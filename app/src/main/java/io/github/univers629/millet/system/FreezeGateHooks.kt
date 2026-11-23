package io.github.univers629.millet.system

import android.content.Context
import io.github.univers629.millet.core.ActiveImeTracker
import io.github.univers629.millet.core.AppUidUtils
import io.github.univers629.millet.core.FreezeReasonClassifier
import io.github.univers629.millet.core.HookMode
import io.github.univers629.millet.core.HookSpec
import io.github.univers629.millet.core.InterceptRule
import io.github.univers629.millet.core.Logger
import io.github.univers629.millet.core.NoRestrictSync
import io.github.univers629.millet.core.PrefReader
import io.github.univers629.millet.core.PrefsKeys
import io.github.univers629.millet.core.PrefsSnapshot
import io.github.univers629.millet.core.RomCapabilities
import io.github.univers629.millet.core.ServiceRoleProtection
import io.github.univers629.millet.core.UnifiedHookParam
import io.github.univers629.millet.core.StatsEventOrigin
import io.github.univers629.millet.core.XReflect

/**
 * 冻结与解冻核心三闸（S2 / S3 / S6 / S7 / S8 / S17 / S18 / S20 + UpdateDialog 初始化闸门）。
 *
 * 覆盖主线：
 * 1. 冻结闸门（S7 GreezeManagerService#freezeAction + S8 AurogonImmobulusMode#checkAppStatusForFreeze）
 * 2. 解冻压制（S20 GreezeManagerService#thawUid）
 *
 * 修正缺陷 #1：
 * - `isUidFrozen` 在 `SystemState.greezeService` 上调用
 * - `checkAppFgService` 在 `SystemState.aurogonService` 上调用
 *
 * 修正缺陷 #4：
 * 原模块 17 条解冻理由中，`activity start`、`tofront`、`MediaButton`、`receiver pkg` 4 条在真机
 * `miui-services.jar` 中不存在逐字匹配（真机实际为 `Activity Start`、` Activity Start!`、
 * `toFrontActivity`、`moveToFront`、`notifyMovetoFront` 等），导致被误拦。
 * 本实现同时收录真机精确字符串集与归一化前缀/包含匹配，杜绝误拦前台正常启动。
 */
object FreezeGateHooks {

    private const val CLASS_GREEZE_SERVICE = "com.miui.server.greeze.GreezeManagerService"
    private const val CLASS_AUROGON_MODE = "com.miui.server.greeze.AurogonImmobulusMode"
    private const val CLASS_AUROGON_APP_INFO = "com.miui.server.greeze.AurogonAppInfo"
    private const val CLASS_RUNNING_PROCESS = "com.miui.server.greeze.RunningProcess"
    private const val CLASS_LIST_APPS_MANAGER = "com.miui.server.greeze.power.ListAppsManager"
    private const val CLASS_POLICY_MAKER = "com.miui.server.greeze.power.PolicyMaker"

    /** 统计边界深度超过该时长未更新，视为上次调用未配对退出，归零自愈。 */
    private const val STATS_DEPTH_STALE_NS = 200_000_000L

    private fun isSyncBinderThawReason(reason: String): Boolean =
        FreezeReasonClassifier.isSyncBinderThawReason(reason)

    private fun isAllowedThawReason(reason: String): Boolean =
        FreezeReasonClassifier.isAllowedThawReason(reason)

    private fun isToFrontReason(reason: String): Boolean =
        FreezeReasonClassifier.isToFrontReason(reason)

    private fun isNetworkWakeReason(reason: String): Boolean =
        FreezeReasonClassifier.isNetworkWakeReason(reason)

    private fun isChargeThawReason(reason: String): Boolean =
        FreezeReasonClassifier.isChargeThawReason(reason)

    private fun isScreenOnThawReason(reason: String): Boolean =
        FreezeReasonClassifier.isScreenOnThawReason(reason)

    /**
     * S2a（PA(3)）：GreezeManagerService#<init>(Context) —— 捕获冻结服务实例句柄。
     */
    fun buildS2GreezeInitHook(): HookSpec = HookSpec(
        id = "S2",
        name = "GreezeManagerService#<init>",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = null,
        paramTypes = listOf(Context::class.java),
        paramResolver = { _, clazz ->
            // 澎湃 1.0/2.0：<init>(Context)；澎湃 3.0（Android 16）：<init>() 无参
            clazz.declaredConstructors
                .firstOrNull { ctor -> ctor.parameterTypes.firstOrNull() == Context::class.java }
                ?.parameterTypes?.toList()
                ?: clazz.declaredConstructors.minByOrNull { it.parameterCount }?.parameterTypes?.toList()
        },
        mode = HookMode.AFTER,
        successLog = "获取冻结管理服务成功",
        after = { param ->
            if (SystemState.greezeService == null && param.thisObject != null) {
                SystemState.greezeService = param.thisObject
            }
            val ctx = param.args.firstOrNull() as? Context
            if (ctx != null && SystemState.greezeContext == null) {
                SystemState.greezeContext = ctx
            }
        }
    )

    /**
     * S2b：GreezeManagerService#<init>(Context) —— 生命周期初始化总闸门。
     * 置 `isGreezeReady = true`、记录模块更新时间、注册亮灭屏广播。
     */
    fun buildS2GreezeLifecycleReadyHook(): HookSpec = HookSpec(
        id = "S2_Gate",
        name = "GreezeManagerService#<init>_Lifecycle",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = null,
        paramTypes = listOf(Context::class.java),
        paramResolver = { _, clazz ->
            clazz.declaredConstructors
                .firstOrNull { ctor -> ctor.parameterTypes.firstOrNull() == Context::class.java }
                ?.parameterTypes?.toList()
                ?: clazz.declaredConstructors.minByOrNull { it.parameterCount }?.parameterTypes?.toList()
        },
        mode = HookMode.AFTER,
        after = { param ->
            if (SystemState.greezeService == null && param.thisObject != null) {
                SystemState.greezeService = param.thisObject
            }
            // 开启全模块总闸门
            SystemState.isGreezeReady = true

            val argCtx = param.args.firstOrNull() as? Context
            if (argCtx != null && SystemState.greezeContext == null) {
                SystemState.greezeContext = argCtx
            }
            val ctx = argCtx ?: SystemState.systemContext
            if (SystemState.moduleLastUpdateTime == -1L && ctx != null) {
                try {
                    @Suppress("DEPRECATION")
                    val pkgInfo = ctx.packageManager.getPackageInfo(PrefsKeys.MODULE_PACKAGE, 0)
                    SystemState.moduleLastUpdateTime = pkgInfo.lastUpdateTime
                } catch (_: Throwable) {
                    // 兼容未安装或旧包名场景，不中断后续亮灭屏广播注册
                }
            }

            if (ctx != null) {
                BroadcastAndDozeHooks.registerScreenStatusReceiver(ctx)
                FreezeStatsRecorder.registerScreenReceiver(ctx)
            }

            // Greeze 刚就绪：把「强制冻结」名单真正推下去（澎湃 3.0 没有 MilletPolicy 后唯一的下发通道）
            ForceFreezeBridge.applyAsync(force = true)

            // 同时把白名单同步进系统免冻结名单，开机后 millet 内核侧就不会再冻结这些应用
            val syncCtx = ctx
            Thread(
                {
                    NoRestrictSync.sync(syncCtx, PrefReader.activePrefs().whiteAppSet)
                },
                "XMillet-NoRestrictSync"
            ).start()
        }
    )

    /**
     * S3（PA(8)）：AurogonImmobulusMode#<init>(Context, HandlerThread, GreezeManagerService)
     * 独立捕获奥罗贡模式实例句柄（`checkAppFgService` 所在类），并同步消息白名单。
     */
    fun buildS3AurogonInitHook(): HookSpec = HookSpec(
        id = "S3",
        name = "AurogonImmobulusMode#<init>",
        targetClass = CLASS_AUROGON_MODE,
        methodName = null,
        paramTypes = listOf(
            Context::class.java,
            android.os.HandlerThread::class.java,
            CLASS_GREEZE_SERVICE
        ),
        paramResolver = { _, clazz ->
            // 澎湃 1.0/2.0：<init>(Context, HandlerThread, GreezeManagerService)；
            // 澎湃 3.0（Android 16）：<init>() 无参，且 Aurogon 只剩极简构造
            clazz.declaredConstructors
                .firstOrNull { ctor -> ctor.parameterCount == 3 }
                ?.parameterTypes?.toList()
                ?: clazz.declaredConstructors.minByOrNull { it.parameterCount }?.parameterTypes?.toList()
        },
        mode = HookMode.AFTER,
        after = { param ->
            if (SystemState.aurogonService == null && param.thisObject != null) {
                SystemState.aurogonService = param.thisObject
            }
            SystemState.syncMessageAppsToAurogon()
            SystemState.syncDualAppSupportToAurogon(PrefReader.systemPrefs.freezeDualApp)
        }
    )

    private enum class FreezeExemption {
        NONE,
        WHITE_LIST,
        DEFAULT_IME,
        ACCESSIBILITY_SERVICE,
        UNKNOWN_SERVICE_ROLES,
        FOREGROUND_SERVICE
    }

    private fun evaluateFreezeExemption(
        uid: Int,
        pkg: String?,
        prefs: PrefsSnapshot,
        checkFgs: Boolean,
    ): FreezeExemption {
        if (pkg.isNullOrEmpty()) return FreezeExemption.NONE
        if (AppUidUtils.containsPolicyTarget(prefs.whiteAppSet, pkg, uid)) return FreezeExemption.WHITE_LIST
        if (pkg in io.github.univers629.millet.core.BuiltinProtect.FREEZE_EXEMPT_PACKAGES) return FreezeExemption.WHITE_LIST
        when (ActiveImeTracker.resolveServiceRoleProtection(SystemState.systemContext, pkg, uid)) {
            ServiceRoleProtection.DEFAULT_IME -> return FreezeExemption.DEFAULT_IME
            ServiceRoleProtection.ACCESSIBILITY -> return FreezeExemption.ACCESSIBILITY_SERVICE
            ServiceRoleProtection.UNKNOWN -> return FreezeExemption.UNKNOWN_SERVICE_ROLES
            ServiceRoleProtection.NONE -> Unit
        }
        if (checkFgs &&
            !AppUidUtils.containsPolicyTarget(prefs.forceFreezeSet, pkg, uid) &&
            AppUidUtils.containsPolicyTarget(prefs.notFreezeWhenHasFgsSet, pkg, uid) &&
            SystemState.checkAppFgService(uid)
        ) {
            return FreezeExemption.FOREGROUND_SERVICE
        }
        return FreezeExemption.NONE
    }

    /**
     * 亮屏解冻豁免判断：
     * - 核心系统 UID（`< 10000`）与用户「免冻白名单 (`whiteAppSet`)」始终随亮屏放行；
     * - 当前正位于屏幕前台/可见的应用（含 `Activity Start` 记账与实时 `AMS` 前台兜底）放行；
     * - 系统默认输入法、已启用的无障碍服务以及拥有活跃前台服务的 `notFreezeWhenHasFgsSet` 应用放行。
     */
    private fun isScreenOnThawExempt(uid: Int, pkg: String?, prefs: PrefsSnapshot): Boolean {
        if (AppUidUtils.appIdOf(uid) in 0 until 10000) return true
        if (pkg.isNullOrEmpty()) return false
        if (AppUidUtils.containsPolicyTarget(prefs.whiteAppSet, pkg, uid)) return true
        // The ROM foreground fallback identifies packages, not individual users.
        if (SystemState.isPackageCurrentlyForeground(pkg)) return true
        if (ActiveImeTracker.isProtectedSystemServiceApp(SystemState.systemContext, pkg, prefs, uid)) return true
        if (AppUidUtils.containsPolicyTarget(prefs.blockThawSet, pkg, uid) ||
            AppUidUtils.containsPolicyTarget(prefs.ignoreNetworkWakeSet, pkg, uid)
        ) return false
        return uid >= 10000 &&
            !AppUidUtils.containsPolicyTarget(prefs.forceFreezeSet, pkg, uid) &&
            AppUidUtils.containsPolicyTarget(prefs.notFreezeWhenHasFgsSet, pkg, uid) &&
            SystemState.checkAppFgService(uid)
    }

    private fun thawInterceptRule(uid: Int, pkg: String?, reason: String, prefs: PrefsSnapshot): InterceptRule? {
        if (!SystemState.isGreezeReady || !AppUidUtils.isManagedAppUid(uid) || pkg.isNullOrEmpty() || reason.isBlank()) return null
        if (FreezeReasonClassifier.isUserOpenReason(reason) ||
            SystemState.isPackageCurrentlyForeground(pkg) ||
            AppUidUtils.containsPolicyTarget(prefs.whiteAppSet, pkg, uid) ||
            ActiveImeTracker.isProtectedSystemServiceApp(SystemState.systemContext, pkg, prefs, uid)
        ) return null
        if (isScreenOnThawReason(reason) && isScreenOnThawExempt(uid, pkg, prefs)) return null
        if (!SystemState.isUidFrozen(uid)) return null
        return when {
            pkg == "com.tencent.mobileqq" && reason.trim().equals("broadcast", ignoreCase = true) -> InterceptRule.HARD_RULE
            isSyncBinderThawReason(reason) && AppUidUtils.containsPolicyTarget(prefs.forbidSyncBinderSet, pkg, uid) -> InterceptRule.FORBID_SYNC_BINDER
            isNetworkWakeReason(reason) && AppUidUtils.containsPolicyTarget(prefs.ignoreNetworkWakeSet, pkg, uid) -> InterceptRule.IGNORE_NETWORK
            prefs.disableCharge && isChargeThawReason(reason) -> InterceptRule.NO_CHARGE_THAW
            prefs.disableScreenOnThaw && isScreenOnThawReason(reason) -> InterceptRule.NO_SCREEN_ON_THAW
            AppUidUtils.containsPolicyTarget(prefs.blockThawSet, pkg, uid) && !isAllowedThawReason(reason) -> InterceptRule.BLOCK_THAW
            else -> null
        }
    }

    private fun recordThawIntercept(uid: Int, pkg: String?, reason: String, rule: InterceptRule, prefs: PrefsSnapshot) {
        if (prefs.freezeStats) FreezeStatsRecorder.recordIntercept(uid, rule, reason, pkg)
        if (prefs.isLog) Logger.warn("--系统[解冻uid]:${rule.label},拒绝[$reason]解冻[$pkg]uid:$uid")
    }

    private data class StatsInvocation(val freeze: Boolean, val outer: Boolean,
        val uids: Set<Int>, val origin: StatsEventOrigin)
    private val statsInvocations = java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<UnifiedHookParam, StatsInvocation>())
    /**
     * 当前线程上已进入的统计边界深度。
     *
     * 用计数器替代 `Thread.currentThread().stackTrace`：后者在持有 Greeze 锁的冻结/解冻热路径上
     * 每次都要捕获整条栈，代价远高于一次计数。深度超过 [STATS_DEPTH_STALE_NS] 未更新时视为
     * 之前某次调用没有配对退出（例如 before 里 blockSafely 或原方法抛异常），归零自愈。
     */
    private class StatsDepthSlot {
        var depth: Int = 0
        var lastEnterNanos: Long = 0L
    }

    private val statsDepth: ThreadLocal<StatsDepthSlot> = object : ThreadLocal<StatsDepthSlot>() {
        override fun initialValue() = StatsDepthSlot()
    }

    // libcore 把 ThreadLocal.get() 标成 @Nullable（initialValue 可能返回 null），这里的槽位恒非空。
    private fun statsDepthSlot(): StatsDepthSlot =
        statsDepth.get() ?: StatsDepthSlot().also { statsDepth.set(it) }

    private fun enterStatsDepth(): Boolean {
        val slot = statsDepthSlot()
        val now = System.nanoTime()
        // depth=当前嵌套层数，lastEnterNanos=最外层进入时刻。层数长时间不收尾说明上次调用没配对退出。
        if (slot.depth != 0 && now - slot.lastEnterNanos > STATS_DEPTH_STALE_NS) slot.depth = 0
        val outer = slot.depth == 0
        if (outer) slot.lastEnterNanos = now
        slot.depth++
        return outer
    }

    private fun leaveStatsDepth() {
        val slot = statsDepthSlot()
        if (slot.depth > 0) slot.depth--
    }

    private fun beginStats(param: UnifiedHookParam, uids: Collection<Int>, reason: String, freeze: Boolean) {
        if (!SystemState.isGreezeReady || !PrefReader.systemPrefs.freezeStats) return
        val targets = uids.filter { AppUidUtils.isManagedAppUid(it) }.toSet()
        val call: StatsInvocation
        synchronized(statsInvocations) {
            if (statsInvocations.containsKey(param)) return
            if (statsInvocations.size >= 256) statsInvocations.keys.firstOrNull()?.let { statsInvocations.remove(it) }
            val outer = enterStatsDepth()
            call = StatsInvocation(freeze, outer, targets, FreezeStatsRecorder.currentOrigin(reason))
            statsInvocations[param] = call
        }
    }

    private fun finishStats(param: UnifiedHookParam, acceptedUids: Collection<Int>, reason: String): Boolean {
        val call = statsInvocations.remove(param) ?: return false
        leaveStatsDepth()
        if (!call.outer) return false
        if (FreezeStatsRecorder.isModuleBatchActive()) return param.throwable == null && !param.isSkipped
        return param.throwable == null && !param.isSkipped
    }

    private fun recordThawAccounting(param: UnifiedHookParam, uids: IntArray, reason: String) {
        val accepted = if (param.throwable == null && !param.isSkipped) resolveEffectiveIds(param.result, uids) else emptyList()
        if (!finishStats(param, accepted, reason) || FreezeStatsRecorder.isDispatchThawEventActive) return
        for (uid in accepted) if (AppUidUtils.isManagedAppUid(uid)) FreezeStatsRecorder.recordThaw(uid, reason)
    }

    /**
     * S6（PA(5)）：GreezeManagerService#freezeUids(int[], long, int, String, boolean)
     * before 拦截受保护 UID（白名单、当前默认输入法、FGS 免冻），after 做冻结记账与日志输出。
     */
    fun buildS6FreezeUidsHook(): HookSpec = HookSpec(
        id = "S6",
        name = "GreezeManagerService#freezeUids",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "freezeUids",
        paramTypes = listOf(
            IntArray::class.java,
            Long::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            String::class.java,
            Boolean::class.javaPrimitiveType!!
        ),
        mode = HookMode.BEFORE_AND_AFTER,
        before = { param ->
            if (!SystemState.isGreezeReady) return@HookSpec
            val uids = param.args.getOrNull(0) as? IntArray ?: return@HookSpec
            if (uids.isEmpty()) return@HookSpec
            val prefs = PrefReader.systemPrefs
            val reason = (param.args.getOrNull(3) as? String) ?: "unknown"
            beginStats(param, uids.toList(), reason, freeze = true)
            var blocked = false
            val kept = ArrayList<Int>(uids.size)
            for (uid in uids) {
                val pkg = if (uid >= 10000) SystemState.getPackageNameForUid(uid) else null
                val exemption = evaluateFreezeExemption(uid, pkg, prefs, checkFgs = true)
                if (exemption != FreezeExemption.NONE) {
                    blocked = true
                    if (prefs.freezeStats && uid >= 10000 &&
                        exemption != FreezeExemption.DEFAULT_IME &&
                        exemption != FreezeExemption.ACCESSIBILITY_SERVICE &&
                        exemption != FreezeExemption.UNKNOWN_SERVICE_ROLES
                    ) {
                        FreezeStatsRecorder.recordProtect(uid, reason, pkg)
                    }
                    if (prefs.isLog) Logger.info("freezeUids 跳过受保护应用[$exemption]: $pkg uid=$uid reason=$reason")
                } else {
                    kept.add(uid)
                }
            }
            if (!blocked) return@HookSpec
            if (kept.isEmpty()) {
                if (param.blockSafely()) finishStats(param, emptyList(), reason)
            } else {
                param.args[0] = kept.toIntArray()
            }
        },
        after = { param ->
            val prefs = PrefReader.systemPrefs
            val needLog = prefs.isLog || prefs.simplifyLog
            val needStats = SystemState.isGreezeReady && prefs.freezeStats
            if (!needLog && !needStats) return@HookSpec

            val argUids = (param.args.getOrNull(0) as? IntArray) ?: IntArray(0)
            val reason = (param.args.getOrNull(3) as? String) ?: "unknown"

            val accepted = if (param.throwable == null && !param.isSkipped) resolveEffectiveIds(param.result, argUids) else emptyList()
            val outerSuccess = finishStats(param, accepted, reason)
            if (needStats) {
                val actuallyFrozenUids = if (outerSuccess) accepted else emptyList()
                if (actuallyFrozenUids.isNotEmpty()) {
                    FreezeStatsRecorder.recordFreezeBatch(actuallyFrozenUids, reason, onlyIfNotFrozen = true)
                }
            }

            if (needLog) {
                val timeout = (param.args.getOrNull(1) as? Long) ?: 0L
                val fromWho = (param.args.getOrNull(2) as? Int) ?: 0
                val flag = (param.args.getOrNull(4) as? Boolean) ?: false

                val effectiveUids = resolveEffectiveIds(param.result, argUids)
                if (effectiveUids.isEmpty()) return@HookSpec

                val labelList = effectiveUids.joinToString(",") { uid ->
                    SystemState.formatAppLabelAndUid(uid)
                }

                if (prefs.isLog) {
                    Logger.warn("++系统[冻结Uids]:[$reason],包列表=[$labelList],time=$timeout,值=$flag,param3=$fromWho")
                    Logger.warn("冻结列表${SystemState.getFrozenUidLabels()}")
                }
                if (prefs.simplifyLog) {
                    Logger.warn("++冻结日志:冻结,因[$reason],[$labelList]")
                }
            }
        }
    )

    private fun resolveEffectiveIds(
        result: Any?,
        argIds: IntArray,
    ): List<Int> {
        return when (result) {
            is Collection<*> -> result.mapNotNull { it as? Int }.filter { it in argIds }
            is IntArray -> result.filter { it in argIds }
            is Boolean -> if (result) argIds.toList() else emptyList()
            else -> emptyList()
        }
    }

    /**
     * S7（PA(4)）：GreezeManagerService#freezeAction
     * - OS1~OS3：(int uid, int fromWho, String reason, boolean isLocked)
     * - OS4：(int uid, int fromWho, String reason, boolean isNeedCompact, boolean checkAudioGps)
     * 【冻结总闸】：
     * 1. `!isGreezeReady` -> 放行
     * 2. 包名命中 `whiteAppSet` -> 阻断冻结（返回 false）
     * 3. `LAUNCH_MODE` 特例（以及通用前台服务保护）：若包名命中 `notFreezeWhenHasFgsSet` 且
     *    `aurogonService.checkAppFgService(uid)` 判断存在前台服务 -> 阻断冻结（返回 false）
     */
    fun buildS7FreezeActionHook(): HookSpec = HookSpec(
        id = "S7",
        name = "GreezeManagerService#freezeAction",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "freezeAction",
        paramTypes = listOf(
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            String::class.java,
            Boolean::class.javaPrimitiveType!!
        ),
        paramResolver = { _, clazz ->
            val candidates = clazz.declaredMethods.filter { m ->
                m.name == "freezeAction" &&
                    (m.parameterCount == 4 ||
                        (m.parameterCount == 5 && m.parameterTypes[4] == Boolean::class.javaPrimitiveType)) &&
                    m.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    m.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    m.parameterTypes[2] == String::class.java &&
                    m.parameterTypes[3] == Boolean::class.javaPrimitiveType
            }
            (candidates.firstOrNull { it.parameterCount == 4 } ?: candidates.firstOrNull())
                ?.parameterTypes
                ?.toList()
        },
        mode = HookMode.BEFORE_AND_AFTER,
        before = { param ->
            if (!SystemState.isGreezeReady) return@HookSpec

            val uid = (param.args.getOrNull(0) as? Int) ?: return@HookSpec
            val reason = (param.args.getOrNull(2) as? String) ?: ""
            val pkg = SystemState.getPackageNameForUid(uid) ?: return@HookSpec
            val prefs = PrefReader.systemPrefs
            if (prefs.freezeStats && uid >= 10000) {
                FreezeStatsRecorder.cachePackageHint(uid, pkg)
            }

            // 1. 白名单、当前默认输入法与 FGS 豁免判定
            when (evaluateFreezeExemption(uid, pkg, prefs, checkFgs = true)) {
                FreezeExemption.WHITE_LIST -> {
                    if (prefs.freezeStats && uid >= 10000) {
                        FreezeStatsRecorder.recordProtect(uid, reason, pkg)
                    }
                    if (prefs.isLog) Logger.info("FAH判断:${pkg}在白名单，因此无法被冻结")
                    Logger.logEvent(Logger.Level.INFO, "FAH判断白名单阻断冻结", pkg, uid, reason)
                    param.blockSafely()
                }
                FreezeExemption.DEFAULT_IME -> {
                    if (prefs.isLog) Logger.info("FAH判断:${pkg}为当前默认输入法，免予冻结")
                    param.blockSafely()
                }
                FreezeExemption.ACCESSIBILITY_SERVICE -> {
                    if (prefs.isLog) Logger.info("FAH判断:${pkg}为已启用无障碍服务，免予冻结")
                    param.blockSafely()
                }
                FreezeExemption.UNKNOWN_SERVICE_ROLES -> param.blockSafely()
                FreezeExemption.FOREGROUND_SERVICE -> {
                    if (prefs.freezeStats && uid >= 10000) {
                        FreezeStatsRecorder.recordProtect(uid, reason, pkg)
                    }
                    if (prefs.isLog) Logger.info("FAH判断:${pkg}具有前台服务,禁止冻结")
                    Logger.logEvent(Logger.Level.INFO, "FAH判断具有前台服务阻断冻结", pkg, uid, reason)
                    param.blockSafely()
                }
                FreezeExemption.NONE -> Unit
            }
            if (!param.isSkipped) beginStats(param, listOf(uid), reason, freeze = true)
        },
        after = { param ->
            if (!SystemState.isGreezeReady) return@HookSpec
            val prefs = PrefReader.systemPrefs
            if (!prefs.freezeStats) return@HookSpec
            val uid = (param.args.getOrNull(0) as? Int) ?: return@HookSpec
            if (uid < 10000) return@HookSpec
            val reason = (param.args.getOrNull(2) as? String) ?: ""
            val accepted = if (param.result == true && param.throwable == null && !param.isSkipped) listOf(uid) else emptyList()
            if (!finishStats(param, accepted, reason) || accepted.isEmpty()) return@HookSpec
            FreezeStatsRecorder.recordFreeze(
                uid = uid,
                reason = reason,
                packageNameHint = FreezeStatsRecorder.getCachedPackageName(uid),
                onlyIfNotFrozen = true,
            )
        }
    )

    /**
     * S8（PA(2)）：AurogonImmobulusMode#checkAppStatusForFreeze(AurogonAppInfo)
     * 【最早一道冻结否决门】：
     * 真机签名：`checkAppStatusForFreeze(Lcom/miui/server/greeze/AurogonAppInfo;)Z`
     * 返回 `false` 表示当前不允许冻结该应用。白名单应用直接在此否决。
     */
    fun buildS8CheckAppStatusForFreezeHook(): HookSpec = HookSpec(
        id = "S8",
        name = "AurogonImmobulusMode#checkAppStatusForFreeze",
        targetClass = CLASS_AUROGON_MODE,
        methodName = "checkAppStatusForFreeze",
        paramTypes = listOf(CLASS_AUROGON_APP_INFO),
        mode = HookMode.BEFORE,
        before = { param ->
            if (!SystemState.isGreezeReady) return@HookSpec
            val appInfoObj = param.args.getOrNull(0) ?: return@HookSpec
            val pkg = try {
                XReflect.getObjectField(appInfoObj, "mPackageName") as? String
            } catch (_: Throwable) {
                null
            } ?: return@HookSpec

            val prefs = PrefReader.systemPrefs
            val uid = resolveAurogonAppUid(appInfoObj)
            // Missing UID fields cannot identify an independent policy target.
            if (!AppUidUtils.isManagedAppUid(uid)) return@HookSpec
            val exemption = evaluateFreezeExemption(uid, pkg, prefs, checkFgs = false)
            if (exemption == FreezeExemption.WHITE_LIST) {
                if (prefs.freezeStats) {
                    FreezeStatsRecorder.recordProtect(uid, "checkAppStatusForFreeze", pkg)
                }
                param.blockSafely()
            } else if (exemption == FreezeExemption.DEFAULT_IME ||
                exemption == FreezeExemption.ACCESSIBILITY_SERVICE ||
                exemption == FreezeExemption.UNKNOWN_SERVICE_ROLES
            ) {
                param.blockSafely()
            }
        }
    )

    private fun resolveAurogonAppUid(appInfoObj: Any): Int {
        // 字段存在性已由 XReflect 正/负缓存，热路径不再触发异常构造。
        return XReflect.getIntFieldOr(appInfoObj, "mUid", "uid")
    }

    /**
     * S17（MSH(0)）：GreezeManagerService#freezeProcess(RunningProcess, long, int, String)
     * 受 `simplifyLog` 装配期门控：记录「谁冻了谁、原因」及该 UID 下冻结进程占比。
     */
    fun buildS17FreezeProcessHook(): HookSpec = HookSpec(
        id = "S17",
        name = "GreezeManagerService#freezeProcess",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "freezeProcess",
        paramTypes = listOf(
            CLASS_RUNNING_PROCESS,
            Long::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            String::class.java
        ),
        mode = HookMode.AFTER,
        gate = { PrefReader.systemPrefs.simplifyLog },
        after = { param ->
            if (!PrefReader.systemPrefs.simplifyLog) return@HookSpec
            val succeeded = (param.result as? Boolean) ?: false
            if (!succeeded) return@HookSpec

            val runningProc = param.args.getOrNull(0) ?: return@HookSpec
            val reason = (param.args.getOrNull(3) as? String) ?: "unknown"

            val uid = XReflect.getIntFieldOr(runningProc, "uid")
            val pid = XReflect.getIntFieldOr(runningProc, "pid")
            val processName = try {
                XReflect.getObjectField(runningProc, "processName") as? String
            } catch (_: Throwable) {
                null
            } ?: ""

            if (uid <= 0 || pid <= 0 || processName.contains("com.google.android.webview")) {
                return@HookSpec
            }

            Logger.postIoTask {
                if (SystemState.isSystemAppUid(uid)) return@postIoTask
                SystemState.recordPidFreezeState(uid, pid, frozen = true, reason = reason)
                val (frozenCount, totalCount, mapSnapshot) = SystemState.getUidFreezeSummary(uid)
                Logger.alwaysInfo(
                    "freezeProcess由于[$reason],冻结进程[$processName],进程冻结情况:$frozenCount/$totalCount,$mapSnapshot"
                )
            }
        }
    )

    /**
     * S18（MSH(1)）：GreezeManagerService#thawProcess(int pid, int fromWho, String reason)
     * 受 `simplifyLog` 装配期门控：从 `/proc/<pid>/status` 异步反查 UID 并更新进程解冻统计。
     */
    fun buildS18ThawProcessHook(): HookSpec = HookSpec(
        id = "S18",
        name = "GreezeManagerService#thawProcess",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "thawProcess",
        paramTypes = listOf(
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            String::class.java
        ),
        mode = HookMode.AFTER,
        gate = { PrefReader.systemPrefs.simplifyLog },
        after = { param ->
            if (!PrefReader.systemPrefs.simplifyLog) return@HookSpec
            val succeeded = (param.result as? Boolean) ?: false
            if (!succeeded) return@HookSpec

            val pid = (param.args.getOrNull(0) as? Int) ?: return@HookSpec
            val reason = (param.args.getOrNull(2) as? String) ?: "unknown"

            // 投递至单线程 Handler 读取 /proc/<pid>/status，不在 Hook 回调线程内执行 IO
            Logger.postIoTask {
                val uid = SystemState.readUidFromProcStatus(pid)
                if (uid <= 0 || SystemState.isSystemAppUid(uid)) return@postIoTask

                val processName = resolveProcessNameByPid(pid) ?: "pid:$pid"
                SystemState.recordPidFreezeState(uid, pid, frozen = false, reason = reason)
                if (!processName.contains("com.google.android.webview") &&
                    reason != "LAUNCH_MODE" &&
                    reason != "IMMOBULUS"
                ) {
                    val (frozenCount, totalCount, mapSnapshot) = SystemState.getUidFreezeSummary(uid)
                    Logger.alwaysInfo(
                        "thawProcess由于[$reason],解冻进程[$processName],进程冻结情况:$frozenCount/$totalCount,$mapSnapshot"
                    )
                }
            }
        }
    )

    /** Single-UID thaw gate; accounting follows a confirmed native return. */
    fun buildS20ThawUidHook(): HookSpec = HookSpec(
        id = "S20",
        name = "GreezeManagerService#thawUid",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "thawUid",
        paramTypes = listOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, String::class.java),
        mode = HookMode.BEFORE_AND_AFTER,
        before = { param ->
            if ((param.method as? java.lang.reflect.Method)?.returnType != Boolean::class.javaPrimitiveType) return@HookSpec
            val uid = param.args.getOrNull(0) as? Int ?: return@HookSpec
            val reason = param.args.getOrNull(2) as? String ?: return@HookSpec
            beginStats(param, listOf(uid), reason, freeze = false)
            val pkg = SystemState.getPackageNameForUid(uid) ?: return@HookSpec
            val prefs = PrefReader.systemPrefs
            if (SystemState.isGreezeReady && isToFrontReason(reason)) SystemState.lastToFrontPackage = pkg
            val rule = thawInterceptRule(uid, pkg, reason, prefs) ?: return@HookSpec
            if (param.blockSafely()) {
                recordThawIntercept(uid, pkg, reason, rule, prefs)
                finishStats(param, emptyList(), reason)
            }
        },
        after = { param ->
            if ((param.method as? java.lang.reflect.Method)?.returnType != Boolean::class.javaPrimitiveType) return@HookSpec
            val uid = param.args.getOrNull(0) as? Int ?: return@HookSpec
            val reason = param.args.getOrNull(2) as? String ?: return@HookSpec
            recordThawAccounting(param, intArrayOf(uid), reason)
        }
    )

    /** Batch thaw uses the same per-UID decision as the synchronous and async gates. */
    fun buildS21ThawUidsProbeHook(): HookSpec = HookSpec(
        id = "S21",
        name = "GreezeManagerService#thawUids",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "thawUids",
        paramTypes = listOf(IntArray::class.java, Int::class.javaPrimitiveType!!, String::class.java),
        mode = HookMode.BEFORE_AND_AFTER,
        before = { param ->
            if ((param.method as? java.lang.reflect.Method)?.returnType != List::class.java) return@HookSpec
            val uids = param.args.getOrNull(0) as? IntArray ?: return@HookSpec
            val reason = param.args.getOrNull(2) as? String ?: return@HookSpec
            val prefs = PrefReader.systemPrefs
            // 目标全都没被冻结时（如 VIBRATOR_APP 反复解冻桌面）直接放行原调用，不做统计与日志
            if (uids.none { SystemState.isUidFrozen(it) }) return@HookSpec
            beginStats(param, uids.toList(), reason, freeze = false)
            val kept = ArrayList<Int>(uids.size)
            val blocked = ArrayList<Pair<Int, InterceptRule>>()
            for (uid in uids) {
                val pkg = SystemState.getPackageNameForUid(uid)
                val rule = thawInterceptRule(uid, pkg, reason, prefs)
                if (rule == null) kept.add(uid) else blocked.add(uid to rule)
            }
            if (prefs.isLog) Logger.info("批量解冻入口[$reason]: uids=[${uids.joinToString(",")}]")
            if (blocked.isEmpty()) return@HookSpec
            if (kept.isEmpty()) {
                if (!param.blockSafely()) return@HookSpec
            } else {
                param.args[0] = kept.toIntArray()
            }
            for ((uid, rule) in blocked) {
                recordThawIntercept(uid, SystemState.getPackageNameForUid(uid), reason, rule, prefs)
            }
            if (param.isSkipped) finishStats(param, emptyList(), reason)
        },
        after = { param ->
            if ((param.method as? java.lang.reflect.Method)?.returnType != List::class.java) return@HookSpec
            val uids = param.args.getOrNull(0) as? IntArray ?: return@HookSpec
            val reason = param.args.getOrNull(2) as? String ?: return@HookSpec
            recordThawAccounting(param, uids, reason)
        }
    )
    /**
     * S22（诊断探点）：GreezeManagerService#dispatchThawEvent(int uid, String reason)
     *
     * 解冻之后的通知分发，uid + reason 都在参数里，用来确认音量键解冻到底走哪条链路。
     */
    fun buildS22DispatchThawProbeHook(): HookSpec = HookSpec(
        id = "S22",
        name = "GreezeManagerService#dispatchThawEvent",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "dispatchThawEvent",
        paramTypes = listOf(Int::class.javaPrimitiveType!!, String::class.java),
        mode = HookMode.BEFORE_AND_AFTER,
        before = { param ->
            val uid = param.args.getOrNull(0) as? Int ?: return@HookSpec
            val reason = param.args.getOrNull(1) as? String ?: "-"
            val prefs = PrefReader.systemPrefs
            if (SystemState.isGreezeReady && prefs.freezeStats && uid >= 10000) {
                beginStats(param, listOf(uid), reason, freeze = false)
            }
            if (prefs.isLog) {
                val pkg = SystemState.getPackageNameForUid(uid) ?: "uid$uid"
                Logger.info("解冻事件[$reason]: $pkg uid=$uid")
            }
        },
        after = { param ->
            val uid = param.args.getOrNull(0) as? Int ?: return@HookSpec
            val reason = param.args.getOrNull(1) as? String ?: "-"
            val success = param.throwable == null && !param.isSkipped
            finishStats(param, if (success) listOf(uid) else emptyList(), reason)
            if (success && SystemState.isGreezeReady && PrefReader.systemPrefs.freezeStats) {
                FreezeStatsRecorder.recordThaw(uid, reason)
            }
        }
    )

    /**
     * S23：【冻结闸门 - 进程级】GreezeManagerService#freezePids(...)
     *
     * 澎湃 3.0 的批量冻结还有一条只按 pid 走的分支
     * （`ActionExecute.freezeNonKeyProcesses → freezePids`），它不经过 `freezeUid` /
     * `freezeProcess`，因此免冻白名单与「前台服务不冻结」在这条路径上原本是失效的：
     * 多进程应用（微信 `:push`、QQ `:MSF` 等）的子进程会被单独冻掉。
     *
     * 这里在冻结之前把属于免冻结应用的 pid 从数组里摘掉；全部摘空时直接否决本次调用。
     */
    internal data class PidIdentity(val uid: Int, val startTicks: Long)

    private val freezePidInvocations = java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<UnifiedHookParam, Map<Int, PidIdentity>>()
    )

    fun buildS23FreezePidsHook(): HookSpec = HookSpec(
        id = "S23",
        name = "GreezeManagerService#freezePids",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "freezePids",
        paramTypes = listOf(
            IntArray::class.java, Long::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!, String::class.java,
        ),
        mode = HookMode.BEFORE_AND_AFTER,
        before = { param ->
            if (!SystemState.isGreezeReady) return@HookSpec
            if ((param.method as? java.lang.reflect.Method)?.returnType != List::class.java) return@HookSpec
            val pidArray = param.args.getOrNull(0) as? IntArray ?: return@HookSpec
            if (pidArray.isEmpty()) return@HookSpec
            val reason = param.args.getOrNull(3) as? String ?: return@HookSpec

            val prefs = PrefReader.systemPrefs

            var blocked = false
            val kept = ArrayList<Int>(pidArray.size)
            var protectedUids: HashSet<Int>? = null
            for (pid in pidArray) {
                val uid = uidForPid(pid)
                val pkg = if (uid > 0) SystemState.getPackageNameForUid(uid) else null
                val exemption = evaluateFreezeExemption(uid, pkg, prefs, checkFgs = true)
                if (exemption != FreezeExemption.NONE) {
                    blocked = true
                    if (prefs.freezeStats && uid >= 10000 &&
                        exemption != FreezeExemption.DEFAULT_IME &&
                        exemption != FreezeExemption.ACCESSIBILITY_SERVICE &&
                        exemption != FreezeExemption.UNKNOWN_SERVICE_ROLES
                    ) {
                        val seen = protectedUids ?: HashSet<Int>().also { protectedUids = it }
                        if (seen.add(uid)) {
                            FreezeStatsRecorder.recordProtect(uid, reason, pkg)
                        }
                    }
                    if (prefs.isLog) Logger.info("freezePids 跳过受保护应用进程[$exemption]: $pkg pid=$pid")
                } else {
                    kept.add(pid)
                }
            }

            if (blocked) {
                if (kept.isEmpty()) {
                    param.blockSafely()
                    return@HookSpec
                }
                param.args[0] = kept.toIntArray()
                Logger.logEvent(Logger.Level.INFO, "freezePids 冻结闸门生效", kept.joinToString(","))
            }
            if (prefs.freezeStats) {
                val identities = kept.mapNotNull { pid ->
                    readPidIdentity(pid)?.let { pid to it }
                }.toMap()
                if (identities.isNotEmpty()) freezePidInvocations[param] = identities
                beginStats(param, identities.values.map { it.uid }, reason, freeze = true)
            }
        },
        after = { param ->
            val identities = freezePidInvocations.remove(param) ?: return@HookSpec
            if (!SystemState.isGreezeReady) return@HookSpec
            val prefs = PrefReader.systemPrefs
            if (!prefs.freezeStats) return@HookSpec
            val argPids = (param.args.getOrNull(0) as? IntArray)?.takeIf { it.isNotEmpty() } ?: return@HookSpec
            val frozenPids = resolveEffectiveIds(param.result, argPids)
            val reason = param.args.getOrNull(3) as? String ?: return@HookSpec
            val uids = if (param.throwable == null && !param.isSkipped)
                acceptedFreezeUids(identities, frozenPids, ::readPidIdentity) else emptyList()
            if (finishStats(param, uids, reason) && uids.isNotEmpty())
                FreezeStatsRecorder.recordFreezeBatch(uids, reason, onlyIfNotFrozen = true)
        }
    )

    internal fun acceptedFreezeUids(
        identities: Map<Int, PidIdentity>,
        acceptedPids: List<Int>,
        currentIdentity: (Int) -> PidIdentity?,
    ): List<Int> = acceptedPids.mapNotNull { pid ->
        val before = identities[pid] ?: return@mapNotNull null
        if (before.uid >= 10000 && currentIdentity(pid) == before) before.uid else null
    }.distinct()

    internal fun parsePidStartTicks(pid: Int, raw: String): Long? {
        if (raw.substringBefore(' ').toIntOrNull() != pid) return null
        val close = raw.lastIndexOf(')')
        if (close < 0 || close + 2 >= raw.length) return null
        val fields = raw.substring(close + 2).trim().split(' ').filter { it.isNotEmpty() }
        return fields.getOrNull(19)?.toLongOrNull()?.takeIf { it > 0L }
    }

    private fun readPidIdentity(pid: Int): PidIdentity? = runCatching {
        if (pid <= 0) return@runCatching null
        val stat = java.io.File("/proc/$pid/stat")
        val startTicks = parsePidStartTicks(pid, stat.readText()) ?: return@runCatching null
        val uid = uidForPid(pid)
        if (uid < 10000 || parsePidStartTicks(pid, stat.readText()) != startTicks) return@runCatching null
        PidIdentity(uid, startTicks)
    }.getOrNull()

    /**
     * pid → uid。`android.os.Process#getUidForPid` 属于隐藏 API，走反射调用；
     * 取不到时返回 -1，由调用方按「未知身份」放行，避免误伤系统进程。
     */
    private fun uidForPid(pid: Int): Int {
        return try {
            val method = getUidForPidMethod ?: return -1
            (method.invoke(null, pid) as? Int) ?: -1
        } catch (_: Throwable) {
            -1
        }
    }

    private val getUidForPidMethod: java.lang.reflect.Method? by lazy {
        runCatching {
            Class.forName("android.os.Process")
                .getDeclaredMethod("getUidForPid", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
        }.getOrNull()
    }

    private fun resolveProcessNameByPid(pid: Int): String? {
        ForegroundState.processNameOf(pid)?.let { return it }
        // 快照未就绪时直接读 procfs，避免为拿进程名去等待 AMS 锁。
        return runCatching {
            java.io.File("/proc/$pid/cmdline").readText().substringBefore('\u0000').takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * S21: PowerManagerService#acquireWakeLockInternal
     * 当应用处于墓碑冻结状态时，拦截其申请 WakeLock，防止被冻结应用锁死 CPU 影响 Deep Sleep。
     */
    fun buildWakeLockHook(): HookSpec = HookSpec(
        id = "S21_WAKELOCK",
        name = "PowerManagerService#acquireWakeLockInternal",
        targetClass = "com.android.server.power.PowerManagerService",
        methodName = "acquireWakeLockInternal",
        paramTypes = listOf(
            android.os.IBinder::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            String::class.java,
            String::class.java,
            android.os.WorkSource::class.java,
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            "android.os.IWakeLockCallback",
        ),
        mode = HookMode.BEFORE,
        before = { param ->
            if ((param.method as? java.lang.reflect.Method)?.returnType != Void.TYPE) return@HookSpec
            val uid = param.args.getOrNull(7) as? Int ?: return@HookSpec
            if (uid > 10000 && SystemState.isUidFrozen(uid)) {
                param.result = null
                if (PrefReader.systemPrefs.isLog) {
                    Logger.debug("阻止已冻结应用申请 WakeLock: UID=$uid")
                }
            }
        }
    )

    /**
     * S24: GreezeManagerService#isAllowBroadcast
     *
     * 官方广播放行入口（澎湃 1.0 起存在）。系统在决定「这条广播能不能送给冻结中的应用」
     * 时会先问它。本模块的 S9/S10 挂在广播队列侧，澎湃 3.0 的推送投递会绕过队列直连，
     * 「消息应用 / 免冻白名单」的推送只在部分投递路径上放行，「禁止后台解冻」对推送
     * 仅部分生效。官方入口复用已有解冻拒绝裁决，保护白名单与系统服务优先；
     * 强冻或唤醒限制不命中模块消息/前台服务放行，其余交回系统判断。
     */
    fun buildS24AllowBroadcastHook(): HookSpec = HookSpec(
        id = "S24",
        name = "GreezeManagerService#isAllowBroadcast",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "isAllowBroadcast",
        paramTypes = listOf(
            Int::class.javaPrimitiveType!!, String::class.java,
            Int::class.javaPrimitiveType!!, String::class.java, String::class.java,
        ),
        mode = HookMode.REPLACE,
        replace = { param ->
            if (!SystemState.isGreezeReady) {
                return@HookSpec param.invokeOriginal()
            }
            val prefs = PrefReader.systemPrefs
            // 已知签名：isAllowBroadcast(int callerUid, String callerPkg, int calleeUid, String calleePkg, String action)
            val calleeUid = param.args.getOrNull(2) as? Int ?: -1
            if (!AppUidUtils.isManagedAppUid(calleeUid)) return@HookSpec param.invokeOriginal()
            val calleePkg = (param.args.getOrNull(3) as? String)?.takeIf { it.isNotEmpty() }
                ?: SystemState.getPackageNameForUid(calleeUid)
                ?: return@HookSpec param.invokeOriginal()
            val rule = thawInterceptRule(calleeUid, calleePkg, "broadcast", prefs)
            if (rule != null) {
                recordThawIntercept(calleeUid, calleePkg, "broadcast", rule, prefs)
                return@HookSpec false
            }
            val suppressSoftExemption = AppUidUtils.containsPolicyTarget(prefs.forceFreezeSet, calleePkg, calleeUid) ||
                AppUidUtils.containsPolicyTarget(prefs.blockThawSet, calleePkg, calleeUid) ||
                AppUidUtils.containsPolicyTarget(prefs.ignoreNetworkWakeSet, calleePkg, calleeUid)
            val exempt = AppUidUtils.containsPolicyTarget(prefs.whiteAppSet, calleePkg, calleeUid) ||
                ActiveImeTracker.isProtectedSystemServiceApp(SystemState.systemContext, calleePkg, prefs, calleeUid) ||
                (!suppressSoftExemption &&
                    (AppUidUtils.containsPolicyTarget(prefs.messageAppSet, calleePkg, calleeUid) ||
                        (AppUidUtils.containsPolicyTarget(prefs.notFreezeWhenHasFgsSet, calleePkg, calleeUid) &&
                            SystemState.checkAppFgService(calleeUid))))
            if (!exempt) {
                return@HookSpec param.invokeOriginal()
            }
            if (prefs.isLog) {
                val action = param.args.getOrNull(4) as? String ?: "-"
                Logger.info("isAllowBroadcast 放行消息应用广播: $calleePkg action=$action")
            }
            true
        }
    )

    /**
     * S25: GreezeManagerService#deferBroadcastForMiui
     *
     * 澎湃自己的「延后投递」入口：返回 true 表示这条广播先攒着，等应用解冻再发。
     * 该入口仅有 action，不能识别收件 UID 或用户空间，保留原生投递判断。
     */
    fun buildS25DeferBroadcastHook(): HookSpec = HookSpec(
        id = "S25",
        name = "GreezeManagerService#deferBroadcastForMiui",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "deferBroadcastForMiui",
        paramTypes = listOf(String::class.java),
        mode = HookMode.REPLACE,
        replace = { param ->
            param.invokeOriginal()
        }
    )

    /**
     * S26: GreezeManagerService#thawUidAsync
     *
     * 已知签名为 `(int uid, int caller, String reason) -> void`。
     * 与同步、批量入口共享 UID 裁决；入队请求不记为成功解冻。
     */
    fun buildS26ThawUidAsyncHook(): HookSpec = HookSpec(
        id = "S26",
        name = "GreezeManagerService#thawUidAsync",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "thawUidAsync",
        paramTypes = listOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, String::class.java),
        mode = HookMode.BEFORE,
        before = { param ->
            if ((param.method as? java.lang.reflect.Method)?.returnType != Void.TYPE) return@HookSpec
            val uid = param.args.getOrNull(0) as? Int ?: return@HookSpec
            val reason = param.args.getOrNull(2) as? String ?: return@HookSpec
            val pkg = SystemState.getPackageNameForUid(uid) ?: return@HookSpec
            val prefs = PrefReader.systemPrefs
            if (SystemState.isGreezeReady && isToFrontReason(reason)) SystemState.lastToFrontPackage = pkg
            val rule = thawInterceptRule(uid, pkg, reason, prefs) ?: return@HookSpec
            if (param.blockSafely()) recordThawIntercept(uid, pkg, reason, rule, prefs)
        }
    )

    /**
     * S27: GreezeManagerService#thawAll
     *
     * 真正的「全量解冻」入口（开机完成、插电、切换省电模式时会被调用）。
     * 仅识别 `(String reason) -> boolean` 全量入口，其他签名保留原生行为。
     * 拦截后单次放行前台、白名单、当前输入法与无障碍服务等安全豁免。
     */
    fun buildS27ThawAllHook(): HookSpec = HookSpec(
        id = "S27",
        name = "GreezeManagerService#thawAll",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "thawAll",
        paramTypes = listOf(String::class.java),
        mode = HookMode.BEFORE,
        before = { param ->
            if ((param.method as? java.lang.reflect.Method)?.returnType != Boolean::class.javaPrimitiveType) return@HookSpec
            if (!SystemState.isGreezeReady || SystemState.greezeService == null) return@HookSpec
            val prefs = PrefReader.systemPrefs
            val reason = param.args.getOrNull(0) as? String ?: return@HookSpec
            val chargeThaw = prefs.disableCharge && isChargeThawReason(reason)
            val screenOnThaw = prefs.disableScreenOnThaw && isScreenOnThawReason(reason)
            if (!chargeThaw && !screenOnThaw) return@HookSpec
            if (!param.blockSafely()) return@HookSpec
            if (prefs.freezeStats) {
                val rule = if (chargeThaw) InterceptRule.NO_CHARGE_THAW else InterceptRule.NO_SCREEN_ON_THAW
                FreezeStatsRecorder.recordSystemIntercept(rule, reason)
            }
            val label = if (chargeThaw) "禁止插电解冻" else "禁止亮屏解冻"
            if (prefs.isLog) Logger.warn("--系统[解冻uid]:$label(thawAll),拒绝[$reason]全量解冻")
            thawProtectedUidsAsync(reason, prefs)
        }
    )

    private fun thawProtectedUidsAsync(reason: String, prefs: PrefsSnapshot) {
        val greeze = SystemState.greezeService ?: return
        Logger.postIoTask {
            val frozenUids = SystemState.getFrozenUidsArray()
            for (uid in frozenUids) {
                val pkg = SystemState.getPackageNameForUid(uid)
                if (thawInterceptRule(uid, pkg, reason, prefs) == null) {
                    runCatching { XReflect.callMethod(greeze, "thawUid", uid, 1000, reason) }
                }
            }
        }
    }

    /**
     * S22: ProcessManager#kill(ProcessConfig)
     * 当 system_server 尝试通过 ProcessManager 杀应用时，若该应用正处于墓碑冻结状态或受保护，拦截杀戮调用。
     */
    fun buildSystemProcessManagerKillHook(): HookSpec = HookSpec(
        id = "S22_KILL",
        name = "ProcessManager#kill",
        targetClass = "miui.process.ProcessManager",
        methodName = "kill",
        paramTypes = emptyList(),
        paramResolver = { _, clazz ->
            val killMethods = clazz.declaredMethods.filter { it.name == "kill" }
            val configMethod = killMethods.firstOrNull {
                it.parameterCount == 1 && it.parameterTypes[0].name == "miui.process.ProcessConfig"
            }
            (configMethod ?: killMethods.firstOrNull())?.parameterTypes?.toList()
        },
        mode = HookMode.BEFORE,
        before = { param ->
            val config = param.args.getOrNull(0) ?: return@HookSpec
            val uid = try {
                XReflect.getIntField(config, "mUid")
            } catch (_: Throwable) {
                try {
                    XReflect.callMethod(config, "getUid") as? Int
                } catch (_: Throwable) { null }
            } ?: -1
            if (uid > 10000 && (SystemState.isUidFrozen(uid) || PrefReader.systemPrefs.disableKill)) {
                if (PrefReader.systemPrefs.freezeStats) {
                    FreezeStatsRecorder.recordKillBlocked(uid, "ProcessManager#kill")
                }
                if (param.blockSafely() && PrefReader.systemPrefs.isLog) {
                    Logger.warn("阻止系统杀戮处于墓碑状态的应用: UID=$uid")
                }
            }
        }
    )

    /**
     * S28（OS2-OS4）：AurogonImmobulusMode#isUidValid(int)
     * 绕过 OS2 机型白名单 `ENABLE_DOUBLE_APP` 与 OS3/OS4 云控总开关 `mDoubleAppCtrlCloud`，
     * 并遵循模块 `freezeDualApp` 开关控制双开（userId == 999）准入。
     */
    fun buildS28DoubleAppUidValidHook(): HookSpec = HookSpec(
        id = "S28",
        name = "AurogonImmobulusMode#isUidValid",
        targetClass = CLASS_AUROGON_MODE,
        methodName = "isUidValid",
        paramTypes = listOf(Int::class.javaPrimitiveType!!),
        mode = HookMode.BEFORE,
        before = { param ->
            val uid = param.args.getOrNull(0) as? Int ?: return@HookSpec
            if (!io.github.univers629.millet.core.AppUidUtils.isDualAppUid(uid)) return@HookSpec
            param.result = PrefReader.systemPrefs.freezeDualApp
        }
    )

    /**
     * S29（OS2-OS4）：GreezeManagerService#checkStateForScrOff(int)
     * 官方至今在此方法保留 `uid <= 19999`，导致双开应用走息屏检查路径被拒。
     */
    fun buildS29ScrOffStateHook(): HookSpec = HookSpec(
        id = "S29",
        name = "GreezeManagerService#checkStateForScrOff",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "checkStateForScrOff",
        paramTypes = listOf(Int::class.javaPrimitiveType!!),
        mode = HookMode.BEFORE,
        before = { param ->
            val uid = param.args.getOrNull(0) as? Int ?: return@HookSpec
            if (!io.github.univers629.millet.core.AppUidUtils.isDualAppUid(uid)) return@HookSpec
            if (!PrefReader.systemPrefs.freezeDualApp) {
                param.result = false
                return@HookSpec
            }
            val self = param.thisObject ?: return@HookSpec
            val scrOff = SystemState.isGreezeScreenOff(self) ?: return@HookSpec
            param.result = scrOff
        }
    )

    /**
     * S30（OS1/OS2）：GreezeManagerService#notifyExcuteServices(int)
     * OS1/OS2 单参重载直接拒掉 `uid > 19999`，导致后台启服务时双开冻结不会被解冻。
     * OS3/OS4 改为 3 参且已自带 `userId == 999` 放行，通过 gate 自动跳过。
     */
    fun buildS30NotifyExecuteServicesHook(classLoader: ClassLoader): HookSpec = HookSpec(
        id = "S30",
        name = "GreezeManagerService#notifyExcuteServices",
        targetClass = CLASS_GREEZE_SERVICE,
        methodName = "notifyExcuteServices",
        paramTypes = listOf(Int::class.javaPrimitiveType!!),
        gate = {
            val clazz = io.github.univers629.millet.core.XReflect.findClassIfExists(CLASS_GREEZE_SERVICE, classLoader)
            clazz?.declaredMethods?.any {
                it.name == "notifyExcuteServices" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
            } == true
        },
        mode = HookMode.BEFORE,
        before = { param ->
            val uid = param.args.getOrNull(0) as? Int ?: return@HookSpec
            if (!io.github.univers629.millet.core.AppUidUtils.isDualAppUid(uid)) return@HookSpec
            if (!PrefReader.systemPrefs.freezeDualApp) return@HookSpec
            val self = param.thisObject ?: return@HookSpec
            SystemState.handleNotifyExecuteServiceForDualUid(self, uid)
            param.result = null
        }
    )

    /**
     * S31（OS1 / MIUI 14）：AurogonImmobulusMode#updateTargetList(int)
     * 仅在目标类没有 `isUidValid` 时安装；启用双开冻结后，将双开 UID 加入
     * Aurogon `mImmobulusTargetList`，并跳过原方法。
     */
    fun buildS31AurogonUpdateTargetListHook(classLoader: ClassLoader): HookSpec = HookSpec(
        id = "S31",
        name = "AurogonImmobulusMode#updateTargetList",
        targetClass = CLASS_AUROGON_MODE,
        methodName = "updateTargetList",
        paramTypes = listOf(Int::class.javaPrimitiveType!!),
        gate = {
            !io.github.univers629.millet.core.RomCapabilities.hasMethod(
                CLASS_AUROGON_MODE,
                "isUidValid",
                classLoader
            )
        },
        mode = HookMode.BEFORE,
        before = { param ->
            val uid = param.args.getOrNull(0) as? Int ?: return@HookSpec
            if (!io.github.univers629.millet.core.AppUidUtils.isDualAppUid(uid)) return@HookSpec
            if (!PrefReader.systemPrefs.freezeDualApp) return@HookSpec
            val self = param.thisObject ?: return@HookSpec
            SystemState.addDualAppToAurogonTargetList(self, uid)
            param.result = null
        }
    )

    /**
     * S33（OS4 / Android 17，PowerKeeper 4.2+）：PolicyMaker#isAllowFreeze(int): int
     * 新版 PowerKeeper 已移除 FrozenAppController（P23 因此 unsupported），冻结资格判定下沉到
     * system_server 的 `com.miui.server.greeze.power.PolicyMaker`，返回 FREEZE_ABLE 才允许冻结。
     * AFTER 阶段把白名单 / 默认输入法 / 无障碍等受保护应用的结果改为 CANNOT_FREEZE。
     * 旧系统不存在该类时由安装器标记为 unsupported，不影响原有路径。
     */
    @Volatile
    private var policyCannotFreeze: Int? = null

    @Volatile
    private var policyFreezeAble: Int? = null

    private fun resolvePolicyConst(clazz: Class<*>, name: String): Int? {
        val field = XReflect.findFieldOrNull(clazz, name) ?: return null
        return try {
            field.getInt(null)
        } catch (_: Throwable) {
            null
        }
    }

    fun buildS33PolicyMakerAllowFreezeHook(): HookSpec = HookSpec(
        id = "S33_POLICY_ALLOW_FREEZE",
        name = "PolicyMaker#isAllowFreeze",
        targetClass = CLASS_POLICY_MAKER,
        methodName = "isAllowFreeze",
        paramTypes = listOf(Int::class.javaPrimitiveType!!),
        mode = HookMode.AFTER,
        after = { param ->
            if (!SystemState.isGreezeReady) return@HookSpec
            if (param.throwable != null) return@HookSpec
            val uid = param.args.getOrNull(0) as? Int ?: return@HookSpec
            if (!AppUidUtils.isManagedAppUid(uid)) return@HookSpec
            val declaring = (param.method as? java.lang.reflect.Method)?.declaringClass ?: return@HookSpec
            val freezeAble = policyFreezeAble
                ?: resolvePolicyConst(declaring, "FREEZE_ABLE")?.also { policyFreezeAble = it }
                ?: return@HookSpec
            if ((param.result as? Int) != freezeAble) return@HookSpec
            val cannotFreeze = policyCannotFreeze
                ?: resolvePolicyConst(declaring, "CANNOT_FREEZE")?.also { policyCannotFreeze = it }
                ?: return@HookSpec
            // 只读缓存；未命中时 UidPackageCache 会后台解析，下次判定即生效（不在平台锁内查 PMS）
            val pkg = SystemState.getPackageNameForUid(uid) ?: return@HookSpec
            val prefs = PrefReader.systemPrefs
            val exemption = evaluateFreezeExemption(uid, pkg, prefs, checkFgs = false)
            if (exemption == FreezeExemption.NONE || exemption == FreezeExemption.FOREGROUND_SERVICE) return@HookSpec
            param.result = cannotFreeze
            if (exemption == FreezeExemption.WHITE_LIST && prefs.freezeStats) {
                FreezeStatsRecorder.recordProtect(uid, "isAllowFreeze", pkg)
            }
            if (prefs.isLog) {
                Logger.warn("系统侧阻止冻结受保护应用 $pkg (UID=$uid, $exemption)")
            }
        }
    )

    /**
     * S32（OS3 / OS4）：ListAppsManager#dealCloudWhiteList(String)
     * 拦截系统侧官方云墓碑白名单注入；开启 `blockCloudFrozenWhitelist` 时跳过原方法，
     * 同时记录最近一次原始串以便用户在 UI 放行后免重启热恢复。
     */
    fun buildS32CloudFrozenWhiteListHook(): HookSpec = HookSpec(
        id = "S32_CLOUD_FROZEN_WL",
        name = "ListAppsManager#dealCloudWhiteList",
        targetClass = CLASS_LIST_APPS_MANAGER,
        methodName = "dealCloudWhiteList",
        paramTypes = listOf(String::class.java),
        mode = HookMode.BEFORE,
        before = { param ->
            val declaringClass = (param.method as? java.lang.reflect.Method)?.declaringClass
            val rawValues = param.args.getOrNull(0) as? String
            SystemState.recordCloudFrozenWhiteListRaw(declaringClass, rawValues)
            val prefs = PrefReader.systemPrefs
            if (!prefs.blockCloudFrozenWhitelist) return@HookSpec
            param.result = null
            if (prefs.isLog) {
                Logger.warn("系统侧拦截官方云墓碑白名单下发")
            }
        }
    )

}
