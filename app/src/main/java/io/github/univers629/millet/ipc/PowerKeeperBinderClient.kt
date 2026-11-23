package io.github.univers629.millet.ipc

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.Parcel
import io.github.univers629.millet.core.CloudListCodec
import io.github.univers629.millet.core.FreezeStatsCodec
import io.github.univers629.millet.core.FreezeStatsSnapshotData
import io.github.univers629.millet.core.HookInstallRegistry
import io.github.univers629.millet.core.Logger
import io.github.univers629.millet.core.PrefReader
import io.github.univers629.millet.core.RomCapabilities
import io.github.univers629.millet.core.StatsWindow
import io.github.univers629.millet.powerkeeper.PowerKeeperCloudLists

/**
 * powerkeeper 与 App 侧共用的跨进程 Binder 客户端代理及粘性广播握手器。
 *
 * 握手原理：
 * 调用 `context.registerReceiver(dummyReceiver, IntentFilter("XMillet-Binder"))` 时，
 * `system_server` 侧的 S1（`ActivityManagerService#registerReceiverWithFeature`）会拦截该请求，
 * 并将 `SystemBinderService` 塞进返回的粘性 `Intent` extras（key = `"system"`）。
 */
object PowerKeeperBinderClient {

    /**
     * system_server 实时运行状态快照（供 XMillet 主页实时看板展示）。
     */
    data class RuntimeStatusSnapshot(
        val binderAlive: Boolean = false,
        val greezeReady: Boolean = false,
        val aurogonReady: Boolean = false,
        val dozeReady: Boolean = false,
        val powerKeeperConnected: Boolean = false,
        val frozenUids: List<Int> = emptyList(),
        val frameworkApiVersion: Int = 102,
        /** 跨进程汇总的 Hook 覆盖情况；未上报时为 [HookCoverage.EMPTY] */
        val hookCoverage: HookCoverage = HookCoverage.EMPTY,
        /**
         * powerkeeper 用官方接口读到的三条云名单；未上报时为 null，
         * 此时界面应退回模块自读数据库的兜底路径。
         */
        val cloudLists: CloudListCodec.CloudLists? = null,
    ) {
        /** 已安装 Hook 数；模块未上报时为 -1（界面须显示「未知」而不是 0） */
        val installedHooks: Int get() = if (hookCoverage.hasData) hookCoverage.installedCount else -1

        /** Hook 规格总数；模块未上报时为 -1 */
        val totalHooks: Int get() = if (hookCoverage.hasData) hookCoverage.totalCount else -1
    }

    @Volatile
    private var remoteSystemBinder: IBinder? = null

    @Volatile
    private var pkCallbackStub: ClientCallbackStub? = null

    @Volatile
    private var appCallbackStub: ClientCallbackStub? = null

    @Volatile
    private var appProcessContext: Context? = null

    /** powerkeeper 侧握手失败后的重试计数与互斥锁（见 [bindFromPowerKeeper]）。 */
    private val bindRetryLock = Any()

    @Volatile
    private var bindRetryAttempt = 0

    private val retryHandler by lazy { Handler(Looper.getMainLooper()) }

    private const val MAX_BIND_RETRY = 8
    private const val MAX_BIND_RETRY_DELAY_MS = 8_000L

    /** 系统服务 Binder 是否已拿到；未就绪时 [notifyPrefChanged] 会静默丢弃。 */
    val isSystemServiceReady: Boolean get() = remoteSystemBinder != null

    /**
     * Binder 就绪回调。
     *
     * 应用刚启动时 `XposedPrefsBridge` 就会推送配置，那时通常还没绑定系统服务，
     * 通知会静默失败；拿到 Binder 后必须再补一次，Hook 侧才会整表重读。
     */
    @Volatile
    var onSystemBinderReady: (() -> Unit)? = null

    /** 上一次 `queryRuntimeStatus` 的失败摘要，用于抑制重复日志 */
    @Volatile
    private var lastStatusFailure: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /** App 侧收到版本更新提示时的 UI 回调 */
    @Volatile
    var onAppUpdateNoticeReceived: (() -> Unit)? = null

    /**
     * 通过注册 `"XMillet-Binder"` 广播从 system_server 取回 Binder 句柄。
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun acquireSystemBinder(context: Context): IBinder? {
        remoteSystemBinder?.let { existing ->
            if (existing.isBinderAlive) return existing
        }

        return try {
            val dummyReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) = Unit
            }
            // 只订阅本模块自己的动作，避免与其它同样 hook AMS 的模块抢同一条握手广播
            val filter = IntentFilter(MilletIpcContract.ACTION_XMILLET_BINDER)
            val stickyIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(dummyReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(dummyReceiver, filter)
            }
            try {
                context.unregisterReceiver(dummyReceiver)
            } catch (_: Throwable) {
            }

            val bundle = stickyIntent?.extras
            if (bundle == null) {
                Logger.alwaysWarn("acquireSystemBinder: stickyIntent extras 为空 (stickyIntent=$stickyIntent)")
                return null
            }
            val binder = bundle.getBinder(MilletIpcContract.BINDER_KEY_SYSTEM)
            if (binder != null && !isOurSystemBinder(binder)) {
                Logger.alwaysWarn(
                    "acquireSystemBinder: 返回的 Binder 描述符不是本模块（疑似其他模块抢答），已丢弃"
                )
                return null
            }
            if (binder != null) {
                remoteSystemBinder = binder
                Logger.alwaysInfo("App 成功连接系统服务 Binder: $binder")
                runCatching { onSystemBinderReady?.invoke() }
                    .onFailure { Logger.error("onSystemBinderReady 回调失败", it) }
            } else {
                Logger.alwaysWarn("acquireSystemBinder: 未在 bundle 中找到 system binder: ${bundle.keySet()}")
            }
            binder
        } catch (t: Throwable) {
            Logger.error("acquireSystemBinder failed", t)
            null
        }
    }

    /**
     * 校验拿到的 Binder 确实是本模块的 `SystemBinderService`。
     *
     * 设备上可能同时装着其它同样 hook AMS 的模块，它们对外派发的是自己那套事务码；
     * 描述符不一致时直接丢弃，避免后续 transact 抛异常被误判为「未激活」。
     */
    private fun isOurSystemBinder(binder: IBinder): Boolean = try {
        binder.interfaceDescriptor == MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE
    } catch (t: Throwable) {
        Logger.alwaysWarn("acquireSystemBinder: 描述符探测失败 ${t.javaClass.simpleName}")
        false
    }

    /**
     * 在 `com.miui.powerkeeper` 启动（P1）时建立双向链路并注册 `"PowerKeeper"` 客户端回调。
     *
     * 握手广播由 system_server 侧的 AMS Hook 代答，所以存在两个竞争窗口：开机时 powerkeeper 可能
     * 比 system_server 装好 Hook 更早被拉起；模块热重载时 powerkeeper 也可能在新一代 Hook 装好之前
     * 就发起握手（实测两者相差不到 20ms）。握手失败过一次并不代表永远失败，这里按指数退避重试，
     * 否则 `pkConnected` 会一直停在 false，「电量与性能」检测项永远过不去。
     */
    fun bindFromPowerKeeper(context: Context): Boolean {
        if (bindFromPowerKeeperOnce(context)) {
            bindRetryAttempt = 0
            return true
        }
        scheduleBindRetry(context.applicationContext)
        return false
    }

    private fun bindFromPowerKeeperOnce(context: Context): Boolean {
        val binder = acquireSystemBinder(context)
        if (binder == null) {
            Logger.alwaysWarn("PK绑定系统服务暂未就绪（system_server 侧可能仍在重装），稍后重试")
            return false
        }
        if (!pingAlive(binder)) {
            return false
        }
        Logger.alwaysInfo("电量与性能绑定系统服务成功")

        val appCtx = context.applicationContext
        val callbackStub = pkCallbackStub ?: ClientCallbackStub { message ->
            handlePowerKeeperInboundMessage(appCtx, message)
        }.also { pkCallbackStub = it }
        registerClient(
            binder,
            callbackStub,
            MilletIpcContract.CLIENT_POWER_KEEPER,
            // powerkeeper 进程只看得见 com.miui.powerkeeper.*，能力位、Hook 账本与
            // 官方云名单接口都在这里上报
            hooksReport = HookInstallRegistry.toWireFormat(),
            capsReport = RomCapabilities.buildCapabilityWireFormat(context.classLoader),
            cloudLists = PowerKeeperCloudLists.read(context)?.let(CloudListCodec::encode)
        )
        return true
    }

    private fun scheduleBindRetry(context: Context) {
        val attempt = synchronized(bindRetryLock) { ++bindRetryAttempt }
        if (attempt > MAX_BIND_RETRY) {
            Logger.error("PK绑定系统服务失败已达 $MAX_BIND_RETRY 次，停止重试（下次亮灭屏或进程重启时再试）")
            return
        }
        val delayMs = (500L shl (attempt - 1)).coerceAtMost(MAX_BIND_RETRY_DELAY_MS)
        Logger.alwaysWarn("PK绑定系统服务失败，${delayMs}ms 后重试（第 $attempt 次）")
        runCatching {
            retryHandler.postDelayed({
                runCatching { bindFromPowerKeeper(context) }
                    .onFailure { Logger.error("PK绑定重试异常", it) }
            }, delayMs)
        }.onFailure { Logger.error("PK绑定重试调度失败", it) }
    }

    /**
     * 在模块 App（UI 进程）启动时建立双向链路并注册 `"XMillet"` 客户端回调。
     */
    fun bindFromApp(context: Context): Boolean {
        appProcessContext = context.applicationContext ?: context
        val binder = acquireSystemBinder(context) ?: return false
        if (!pingAlive(binder)) return false

        val callbackStub = appCallbackStub ?: ClientCallbackStub { message ->
            handleAppInboundMessage(message)
        }.also { appCallbackStub = it }
        registerClient(binder, callbackStub, MilletIpcContract.CLIENT_XMILLET_APP)
        return true
    }

    /**
     * 热重载卸载前清理所有待执行的重连或回调任务，防止旧代 Handler 持有类加载器引用。
     */
    fun cancelPendingRetries() {
        synchronized(bindRetryLock) {
            bindRetryAttempt = 0
        }
        retryHandler.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun handlePowerKeeperInboundMessage(context: Context, message: String) {
        if (message == MilletIpcContract.MSG_REBIND_SYSTEM_BINDER) {
            remoteSystemBinder = null
            Logger.postIoTask {
                runCatching { bindFromPowerKeeper(context) }
                    .onFailure { Logger.error("热重载重连 system_server 失败", it) }
            }
            return
        }
        // 收到配置键名 -> 延迟 200ms 重载 powerkeeper 侧对应配置分组
        mainHandler.postDelayed({
            PrefReader.reloadPowerKeeperByKey(message)
        }, 200L)
    }

    private fun handleAppInboundMessage(message: String) {
        if (message == MilletIpcContract.MSG_REBIND_SYSTEM_BINDER) {
            remoteSystemBinder = null
            val ctx = appProcessContext
            if (ctx != null) {
                Thread({
                    runCatching { bindFromApp(ctx) }
                }, "XMillet-AppRebind").start()
            }
            return
        }
        if (message == MilletIpcContract.MSG_IS_UPDATE) {
            mainHandler.post { onAppUpdateNoticeReceived?.invoke() }
            return
        }
    }

    // ==================== 事务调用封装 ====================

    fun pingAlive(binder: IBinder? = remoteSystemBinder): Boolean {
        val target = binder ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            target.transact(MilletIpcContract.TX_PING_ALIVE, data, reply, 0)
            reply.readException()
            reply.readInt() != 0
        } catch (_: Throwable) {
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 注册客户端回调。
     *
     * [hooksReport] / [capsReport] / [cloudLists] 是本进程的 Hook 账本、宿主能力位与
     * 云名单快照；只有 `com.miui.powerkeeper` 上报才有意义（App 进程既没有 Hook、
     * 没有宿主类，也调用不到官方云名单接口）。
     * 这三个字段都是协议尾部追加项，旧版 system_server 会忽略它们。
     */
    private fun registerClient(
        binder: IBinder,
        callback: IBinder,
        clientName: String,
        hooksReport: String? = null,
        capsReport: String? = null,
        cloudLists: String? = null
    ) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            data.writeStrongBinder(callback)
            data.writeString(clientName)
            data.writeString(hooksReport.orEmpty())
            data.writeString(capsReport.orEmpty())
            data.writeString(cloudLists.orEmpty())
            binder.transact(MilletIpcContract.TX_REGISTER_CLIENT, data, null, IBinder.FLAG_ONEWAY)
        } catch (t: Throwable) {
            Logger.error("registerClient($clientName) failed", t)
        } finally {
            data.recycle()
        }
    }

    /**
     * 通知 system_server 与 powerkeeper 某个配置键已发生更新（事务码 4）。
     */
    fun notifyPrefChanged(prefKey: String): Boolean {
        val binder = remoteSystemBinder ?: return false
        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            data.writeString(prefKey)
            binder.transact(MilletIpcContract.TX_NOTIFY_PREF_CHANGED, data, null, IBinder.FLAG_ONEWAY)
        } catch (_: Throwable) {
            false
        } finally {
            data.recycle()
        }
    }

    /**
     * 上报 `MILLET_NO_RESTRICT_APP` 字符串到 system_server（事务码 5）。
     */
    fun reportNoRestrictApps(noRestrictApps: String?) {
        val binder = remoteSystemBinder ?: return
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            data.writeString(noRestrictApps ?: "")
            binder.transact(MilletIpcContract.TX_SET_NO_RESTRICT_APPS, data, null, IBinder.FLAG_ONEWAY)
        } catch (t: Throwable) {
            Logger.error("reportNoRestrictApps failed", t)
        } finally {
            data.recycle()
        }
    }

    /**
     * 查询某应用是否在省电策略「无限制」名单中（事务码 6）。
     */
    fun isNoRestrictApp(packageName: String): Boolean {
        val binder = remoteSystemBinder ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            data.writeString(packageName)
            binder.transact(MilletIpcContract.TX_IS_NO_RESTRICT_APP, data, reply, 0)
            reply.readException()
            reply.readInt() != 0
        } catch (t: Throwable) {
            Logger.error("AppConfigPage的 isNoRestrictApp()发生错误", t)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 让 system_server 侧的模块直接应用省电策略（事务码 15）。
     *
     * @return true 已应用；false 应用失败；null 表示模块未连接（调用方应回退 Root 方案）
     */
    fun applyBatteryPolicy(packageName: String, noRestrict: Boolean): Boolean? {
        val binder = remoteSystemBinder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            data.writeString(packageName)
            data.writeInt(if (noRestrict) 1 else 0)
            binder.transact(MilletIpcContract.TX_APPLY_BATTERY_POLICY, data, reply, 0)
            reply.readException()
            reply.readInt() != 0
        } catch (t: Throwable) {
            Logger.error("applyBatteryPolicy failed", t)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 读取 system_server 侧全局闸门 isGreezeReady 状态（事务码 9）。
     */
    fun isGreezeReady(): Boolean {
        val binder = remoteSystemBinder ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            binder.transact(MilletIpcContract.TX_IS_GREEZE_READY, data, reply, 0)
            reply.readException()
            reply.readInt() != 0
        } catch (_: Throwable) {
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 读取 system_server 采集的应用进程快照（事务码 14）。
     *
     * system_server 有权限直接读 `/proc/<pid>/…`，所以这条路径不需要 App 起 root shell。
     * @return 快照；未连接或解析失败返回 null，合法空快照与失败分别处理。
     */
    fun queryProcessSnapshot(): io.github.univers629.millet.core.ProcessSnapshotCodec.Snapshot? {
        val binder = remoteSystemBinder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            if (!binder.transact(MilletIpcContract.TX_GET_PROCESS_SNAPSHOT, data, reply, 0)) return null
            reply.readException()
            val payload = reply.readString()
            io.github.univers629.millet.core.ProcessSnapshotCodec.decodeSnapshot(payload)
        } catch (t: Throwable) {
            Logger.error("queryProcessSnapshot failed", t)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 内核冻结器类型（cgroup v2 / v1 / 无）。
     *
     * `/sys/fs/cgroup` 与 `/proc/<pid>/wchan` 在 App 侧读不到，原来只能 root 采样；现在由模块在
     * system_server 里判定后经 Binder 回传。返回 null 表示模块未连接（调用方可退回 root 探测）。
     */
    data class KernelFreezerReport(
        val mode: io.github.univers629.millet.core.RootHelper.KernelFreezer =
            io.github.univers629.millet.core.RootHelper.KernelFreezer.UNKNOWN,
        val evidence: String = "UNCONFIRMED",
        val backend: String = "UNCONFIRMED"
    ) {
        val confirmed: Boolean
            get() = when (mode) {
                io.github.univers629.millet.core.RootHelper.KernelFreezer.CGROUP_V2 ->
                    evidence == "CGROUP_V2_FROZEN" || evidence == "WCHAN_V2"
                io.github.univers629.millet.core.RootHelper.KernelFreezer.CGROUP_V1 ->
                    evidence == "CGROUP_V1_FROZEN" || evidence == "WCHAN_V1"
                else -> false
            }
    }

    fun queryKernelFreezer(): io.github.univers629.millet.core.RootHelper.KernelFreezer? =
        queryKernelFreezerReport()?.mode

    fun queryKernelFreezerReport(): KernelFreezerReport? {
        val binder = remoteSystemBinder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            if (!binder.transact(MilletIpcContract.TX_GET_KERNEL_FREEZER, data, reply, 0)) return null
            reply.readException()
            val mode = when (reply.readString()?.trim()?.uppercase()) {
                "V2" -> io.github.univers629.millet.core.RootHelper.KernelFreezer.CGROUP_V2
                "V1" -> io.github.univers629.millet.core.RootHelper.KernelFreezer.CGROUP_V1
                "NONE" -> io.github.univers629.millet.core.RootHelper.KernelFreezer.NONE
                else -> io.github.univers629.millet.core.RootHelper.KernelFreezer.UNKNOWN
            }
            val evidence = if (reply.dataAvail() > 0) reply.readString().orEmpty() else "UNCONFIRMED"
            val backend = if (reply.dataAvail() > 0) reply.readString().orEmpty() else "UNCONFIRMED"
            KernelFreezerReport(mode, evidence, backend)
        } catch (t: Throwable) {
            Logger.error("queryKernelFreezer failed", t)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 查询 system_server 侧的墓碑统计快照（事务码 17）。
     *
     * @return 统计快照；系统服务未连接或旧版本模块不支持该事务码时返回 null
     */
    fun queryFreezeStats(window: StatsWindow): FreezeStatsSnapshotData? {
        val binder = remoteSystemBinder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            data.writeString(window.name)
            val transacted = binder.transact(MilletIpcContract.TX_GET_FREEZE_STATS, data, reply, 0)
            if (!transacted || reply.dataAvail() < 4) {
                return null
            }
            reply.readException()
            if (reply.dataAvail() < 4) {
                return null
            }
            val payload = reply.readString()
            FreezeStatsCodec.decode(payload)
        } catch (t: Throwable) {
            Logger.error("queryFreezeStats(${window.name}) failed", t)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 重置 system_server 侧的墓碑统计计数与历史桶（事务码 18）。
     *
     * @return true 已重置；false 服务未连接、旧版本不支持或调用失败
     */
    fun resetFreezeStats(): Boolean {
        val binder = remoteSystemBinder ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            val transacted = binder.transact(MilletIpcContract.TX_RESET_FREEZE_STATS, data, reply, 0)
            if (!transacted || reply.dataAvail() < 4) {
                return false
            }
            reply.readException()
            if (reply.dataAvail() < 4) {
                return false
            }
            reply.readInt() != 0
        } catch (t: Throwable) {
            Logger.error("resetFreezeStats failed", t)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 按需批量查询完整 UID 的成员包名（事务码 20），由调用方在 IO 刷新中执行。
     *
     * @return 成功查询的 UID 映射；缺失键表示该 UID 未知，空列表表示成功但无成员。
     * 未连接、旧端不支持、整批失败或去重后超过 512 个 UID 时返回 null。
     */
    fun queryUidPackages(uids: Collection<Int>): Map<Int, List<String>>? {
        val requestedUids = LinkedHashSet<Int>()
        for (uid in uids) {
            requestedUids.add(uid)
            if (requestedUids.size > MilletIpcContract.MAX_UID_PACKAGES_QUERY) return null
        }
        if (requestedUids.isEmpty()) return emptyMap()
        val binder = remoteSystemBinder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            data.writeIntArray(requestedUids.toIntArray())
            if (!binder.transact(MilletIpcContract.TX_QUERY_UID_PACKAGES, data, reply, 0) || reply.dataAvail() < 4) {
                return null
            }
            reply.readException()
            if (reply.dataAvail() < 4) return null
            val count = reply.readInt()
            if (count !in 0..requestedUids.size) return null
            val packages = LinkedHashMap<Int, List<String>>(count)
            repeat(count) {
                if (reply.dataAvail() < 8) return null
                val uid = reply.readInt()
                if (uid !in requestedUids || packages.containsKey(uid)) return null
                val members = reply.createStringArrayList() ?: return null
                if (members.any { it.isNullOrBlank() }) return null
                packages[uid] = members.distinct()
            }
            packages
        } catch (t: Throwable) {
            Logger.error("queryUidPackages failed", t)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    data class DualAppEntry(
        val packageName: String,
        val uid: Int,
        val flags: Int,
    )

    /**
     * 查询双开空间（user 999）已安装的受管应用列表（事务码 19）。
     *
     * @return null 表示系统服务未连接或旧版本模块不支持该事务码
     */
    fun queryDualApps(): List<DualAppEntry>? {
        val binder = remoteSystemBinder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            val transacted = binder.transact(MilletIpcContract.TX_GET_DUAL_APPS, data, reply, 0)
            if (!transacted) return null
            reply.readException()
            val payload = reply.readString() ?: return null
            if (payload.isEmpty()) return emptyList()
            payload.lineSequence()
                .mapNotNull { line ->
                    val parts = line.split('|')
                    if (parts.size < 3) return@mapNotNull null
                    val pkg = parts[0].trim()
                    val uid = parts[1].toIntOrNull() ?: return@mapNotNull null
                    val flags = parts[2].toIntOrNull() ?: 0
                    if (pkg.isEmpty() || !io.github.univers629.millet.core.AppUidUtils.isDualAppUid(uid)) {
                        null
                    } else {
                        DualAppEntry(packageName = pkg, uid = uid, flags = flags)
                    }
                }
                .toList()
        } catch (t: Throwable) {
            Logger.error("queryDualApps failed", t)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 读取 system_server 实时运行状态与当前冻结 UID 列表（事务码 10）。
     */
    fun queryRuntimeStatus(): RuntimeStatusSnapshot {
        val binder = remoteSystemBinder
        if (binder == null || !pingAlive(binder)) {
            return RuntimeStatusSnapshot(binderAlive = false)
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            binder.transact(MilletIpcContract.TX_GET_RUNTIME_STATUS, data, reply, 0)
            reply.readException()
            val greezeReady = reply.readInt() != 0
            val aurogonReady = reply.readInt() != 0
            val dozeReady = reply.readInt() != 0
            val pkConnected = reply.readInt() != 0
            val frozenUids = reply.createIntArray()?.toList() ?: emptyList()
            val frameworkApiVersion = if (reply.dataAvail() >= 4) reply.readInt() else 102
            // 尾部追加的 Hook 覆盖信息：旧模块到此为止，必须逐段用 dataAvail 探测，
            // 缺字段时保持 HookCoverage.EMPTY（界面显示「未知」而不是 0）。
            val coverageRaw = if (reply.dataAvail() >= 4) {
                try {
                    reply.readString()
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
            val hookCoverage = HookCoverage.decode(coverageRaw)
            // 再往后是 powerkeeper 用官方接口读到的云名单；旧模块没有这一段。
            val cloudRaw = if (reply.dataAvail() >= 4) {
                try {
                    reply.readString()
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
            val cloudLists = CloudListCodec.decode(cloudRaw)
            Logger.alwaysInfo(
                "运行状态: binderAlive=true, greezeReady=$greezeReady, aurogonReady=$aurogonReady, " +
                    "dozeReady=$dozeReady, pkConnected=$pkConnected, frozen=${frozenUids.size}, " +
                    "frameworkApiVersion=$frameworkApiVersion, hooks=${hookCoverage.ratioOrNull() ?: "未知"}, " +
                    "cloud=${cloudLists?.let { "${it.dozeWhiteList.size}/${it.frozenNewWhiteList.size}/${it.launchRestrict.size}" } ?: "未知"}"
            )
            lastStatusFailure = null
            RuntimeStatusSnapshot(
                binderAlive = true,
                greezeReady = greezeReady,
                aurogonReady = aurogonReady,
                dozeReady = dozeReady,
                powerKeeperConnected = pkConnected,
                frozenUids = frozenUids,
                frameworkApiVersion = frameworkApiVersion,
                hookCoverage = hookCoverage,
                cloudLists = cloudLists,
            )
        } catch (t: Throwable) {
            // 同一条失败只记一次，避免 3 秒轮询刷屏
            val failure = "${t.javaClass.simpleName}: ${t.message}"
            if (failure != lastStatusFailure) {
                lastStatusFailure = failure
                Logger.alwaysWarn("queryRuntimeStatus 事务失败，回退到兜底状态: $failure")
            }
            RuntimeStatusSnapshot(
                binderAlive = true,
                greezeReady = isGreezeReady()
            )
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 一键解冻当前所有已冻结的 UID（事务码 11）。
     */
    fun thawAllFrozenUids(): Int {
        val binder = remoteSystemBinder ?: return 0
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MilletIpcContract.DESCRIPTOR_SYSTEM_SERVICE)
            binder.transact(MilletIpcContract.TX_THAW_ALL_FROZEN, data, reply, 0)
            reply.readException()
            reply.readInt()
        } catch (_: Throwable) {
            0
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 客户端侧回调 Stub（实现 `io.github.univers629.millet.ISystemCallback`）。
     */
    private class ClientCallbackStub(
        private val onMessage: (String) -> Unit
    ) : Binder() {

        init {
            attachInterface(null, MilletIpcContract.DESCRIPTOR_CLIENT_CALLBACK)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(MilletIpcContract.DESCRIPTOR_CLIENT_CALLBACK)
                return true
            }
            if (code == MilletIpcContract.TX_ON_CLIENT_MESSAGE) {
                return try {
                    data.enforceInterface(MilletIpcContract.DESCRIPTOR_CLIENT_CALLBACK)
                    val msg = data.readString()
                    if (!msg.isNullOrEmpty()) {
                        onMessage(msg)
                    }
                    true
                } catch (t: Throwable) {
                    Logger.error("ClientCallbackStub onTransact error", t)
                    false
                }
            }
            return super.onTransact(code, data, reply, flags)
        }
    }
}
