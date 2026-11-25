package io.github.univers629.millet.core

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import java.io.InputStream
import java.lang.reflect.Method
import java.util.Collections

/** 用户选择的特权执行方式。 */
enum class PrivilegeMode(val label: String) {
    AUTO("自动"),
    ROOT("仅 Root"),
    SHIZUKU("仅 Shizuku"),
}

/** 当前实际可用的特权通道。 */
enum class PrivilegeBackend { NONE, ROOT, SHIZUKU }

data class PrivilegeState(
    val mode: PrivilegeMode = PrivilegeMode.AUTO,
    val backend: PrivilegeBackend = PrivilegeBackend.NONE,
    val rootGranted: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val shizukuUid: Int = -1,
    val shizukuVersion: Int = -1,
) {
    /** 是否具备 root 级能力（读其它应用私有数据、stop/start 等）。adb 身份的 Shizuku 不算。 */
    val hasRootLevel: Boolean
        get() = backend == PrivilegeBackend.ROOT ||
            (backend == PrivilegeBackend.SHIZUKU && shizukuUid == 0)

    val backendLabel: String
        get() = when (backend) {
            PrivilegeBackend.ROOT -> "Root"
            PrivilegeBackend.SHIZUKU -> if (shizukuUid == 0) "Shizuku（root）" else "Shizuku（adb）"
            PrivilegeBackend.NONE -> "未授权"
        }

    val shizukuSummary: String
        get() = when {
            !shizukuRunning -> "未检测到 Shizuku / Sui 服务，点击打开 Shizuku"
            !shizukuGranted -> "服务运行中（API $shizukuVersion），点击授权 XMillet"
            shizukuUid == 0 -> "已授权 · root 身份（uid 0）"
            shizukuUid == 2000 -> "已授权 · adb 身份（uid 2000），部分功能受限"
            else -> "已授权 · uid $shizukuUid"
        }
}

/**
 * 统一的特权命令执行入口（Root / Shizuku 两种实现）。
 *
 * 模块（LSPosed）未生效时，App 侧的兜底能力全部走这里：省电策略写入、进程快照、
 * 诊断包、LSPosed 管理器入口等。Shizuku 以 adb 身份（uid 2000）运行时没有 root 级能力，
 * 依赖 root 的功能请先判断 [hasRootLevel]。
 *
 * 只在 App 进程使用；Hook 进程（system_server / powerkeeper）不会初始化本对象。
 */
object PrivilegedExecutor {

    const val KEY_PRIVILEGE_MODE = "ui_privilege_mode"
    const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.privileged.api"
    private const val SHIZUKU_REQUEST_CODE = 0x5A1

    private val _state = MutableStateFlow(PrivilegeState())
    val state: StateFlow<PrivilegeState> = _state.asStateFlow()

    @Volatile
    private var mode: PrivilegeMode = PrivilegeMode.AUTO

    @Volatile
    private var installed = false

    fun init(context: Context) {
        if (installed) return
        installed = true
        mode = readMode(context)
        runCatching {
            Shizuku.addBinderReceivedListenerSticky { refreshState() }
            Shizuku.addBinderDeadListener { refreshState() }
            Shizuku.addRequestPermissionResultListener { _, _ -> refreshState() }
        }.onFailure { Logger.warn("Shizuku 监听注册失败: ${it.javaClass.simpleName}") }
        refreshState()
    }

    fun setMode(context: Context, newMode: PrivilegeMode) {
        mode = newMode
        runCatching {
            PrefReader.openUiPrefs(context).edit().putInt(KEY_PRIVILEGE_MODE, newMode.ordinal).apply()
        }
        refreshState()
    }

    private fun readMode(context: Context): PrivilegeMode {
        val index = runCatching {
            PrefReader.openUiPrefs(context).getInt(KEY_PRIVILEGE_MODE, 0)
        }.getOrDefault(0)
        return PrivilegeMode.entries.getOrElse(index) { PrivilegeMode.AUTO }
    }

    /** 重新探测 Root / Shizuku 状态并刷新 [state]；只做几次轻量 binder 调用，不会弹授权框。 */
    fun refreshState(): PrivilegeState {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val uid = if (running) runCatching { Shizuku.getUid() }.getOrDefault(-1) else -1
        val version = if (running) runCatching { Shizuku.getVersion() }.getOrDefault(-1) else -1
        val root = RootHelper.isRootGranted
        val shizukuReady = running && granted
        val backend = when (mode) {
            PrivilegeMode.ROOT -> if (root) PrivilegeBackend.ROOT else PrivilegeBackend.NONE
            PrivilegeMode.SHIZUKU -> if (shizukuReady) PrivilegeBackend.SHIZUKU else PrivilegeBackend.NONE
            PrivilegeMode.AUTO -> when {
                root -> PrivilegeBackend.ROOT
                shizukuReady -> PrivilegeBackend.SHIZUKU
                else -> PrivilegeBackend.NONE
            }
        }
        val next = PrivilegeState(mode, backend, root, running, granted, uid, version)
        _state.value = next
        return next
    }

    fun currentBackend(): PrivilegeBackend = refreshState().backend

    fun isAvailable(): Boolean = currentBackend() != PrivilegeBackend.NONE

    fun hasRootLevel(): Boolean = refreshState().hasRootLevel

    /**
     * 确保有可用的特权通道：已有则直接返回；否则在「自动 / 仅 Root」模式下申请一次 Root。
     * Shizuku 授权需要用户在设置页主动点击，这里不会弹 Shizuku 授权框。
     */
    fun ensureReady(timeoutMs: Long = 90_000L): PrivilegeBackend {
        val now = refreshState()
        if (now.backend != PrivilegeBackend.NONE) return now.backend
        if (mode != PrivilegeMode.SHIZUKU) RootHelper.requestRoot(timeoutMs)
        return refreshState().backend
    }

    /**
     * 用当前通道执行一段 shell 脚本并返回标准输出行；没有可用通道、失败或超时时返回空列表。
     *
     * @param mountMaster 仅对 Root 生效（`su -M`）；Shizuku 进程本来就在全局挂载命名空间里。
     */
    fun exec(command: String, mountMaster: Boolean = false, timeoutMs: Long = 20_000L): List<String> =
        when (currentBackend()) {
            PrivilegeBackend.ROOT -> RootShell.exec(command, mountMaster, timeoutMs)
            PrivilegeBackend.SHIZUKU -> ShizukuShell.exec(command, timeoutMs)
            PrivilegeBackend.NONE -> emptyList()
        }

    /** 向 Shizuku 申请授权；结果通过监听回调刷新 [state]。 */
    fun requestShizukuPermission(): Boolean {
        val s = refreshState()
        if (!s.shizukuRunning) return false
        if (s.shizukuGranted) return true
        return runCatching {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            true
        }.getOrElse {
            Logger.warn("Shizuku 授权申请失败: ${it.javaClass.simpleName}")
            false
        }
    }
}

/**
 * 通过 Shizuku 远程进程执行命令。
 *
 * Shizuku API 13 把 `newProcess` 改成了私有方法（官方推荐 UserService），这里只需要跑几条
 * 系统命令，用反射调用即可；proguard-rules.pro 里保留了该方法。
 */
internal object ShizukuShell {

    private val newProcess: Method? by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
        }.onFailure {
            Logger.warn("Shizuku newProcess 不可用: ${it.javaClass.simpleName}")
        }.getOrNull()
    }

    fun exec(command: String, timeoutMs: Long): List<String> {
        val method = newProcess ?: return emptyList()
        val process = try {
            method.invoke(null, arrayOf("sh", "-c", command), null, null) as? Process
        } catch (t: Throwable) {
            Logger.warn("Shizuku 执行失败（${(t.cause ?: t).javaClass.simpleName}）：${command.take(60)}")
            null
        } ?: return emptyList()

        runCatching { process.outputStream.close() }
        val lines = Collections.synchronizedList(ArrayList<String>())
        val reader = Thread({
            runCatching {
                process.inputStream.bufferedReader().useLines { seq -> seq.forEach { lines.add(it) } }
            }
        }, "XMillet-ShizukuOut").apply {
            isDaemon = true
            start()
        }
        Thread({ runCatching { drain(process.errorStream) } }, "XMillet-ShizukuErr").apply {
            isDaemon = true
            start()
        }

        reader.join(timeoutMs)
        if (reader.isAlive) {
            Logger.warn("Shizuku 命令超时（${timeoutMs}ms）：${command.take(60)}")
            runCatching { process.destroy() }
            return emptyList()
        }
        runCatching { process.waitFor() }
        return synchronized(lines) { ArrayList(lines) }
    }

    private fun drain(input: InputStream) {
        val buffer = ByteArray(4096)
        while (input.read(buffer) >= 0) Unit
    }
}
