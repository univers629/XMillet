package io.github.univers629.millet.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.github.univers629.millet.ipc.PowerKeeperBinderClient

/**
 * LSPosed 管理器界面（寄生管理器）入口与作用域激活状态检测。
 *
 * 激活状态说明：模块被勾选进作用域后，LSPosed 会把 `模块包名 + 作用域包名` 写进
 * `/data/adb/lspd/config/modules_config.db`（及其 WAL 文件）的 scope 表。两个字段在 SQLite 记录里
 * 相邻存放，因此有 Root 时可以直接按「包名紧邻」的特征判断，无需在设备上装 sqlite3。
 */
object LsposedHelper {

    private const val SECRET_CODE = "android_secret_code://5776733"

    private const val ACTION_SECRET_CODE_TELEPHONY = "android.telephony.action.SECRET_CODE"

    private const val ACTION_SECRET_CODE_LEGACY = "android.provider.Telephony.SECRET_CODE"


    private val secretCodeAction: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ACTION_SECRET_CODE_TELEPHONY
        } else {
            ACTION_SECRET_CODE_LEGACY
        }

    /**
     * 模块是否已真正生效。
     *
     * 判据改为运行期事实，而不是去读 LSPosed 的配置库（那条路要 Root）：
     * `binderAlive` 表示模块已在 system_server 挂上 Hook 并注册了 Binder 服务，
     * `powerKeeperConnected` 表示 powerkeeper 进程也被注入并连上了同一个服务。
     * 两者同时成立，等价于「本模块 + system + powerkeeper 都在作用域里且已生效」。
     */
    fun isScopeActive(context: Context): Boolean = runCatching {
        val status = PowerKeeperBinderClient.queryRuntimeStatus()
        status.binderAlive && status.powerKeeperConnected
    }.getOrDefault(false)

    /**
     * 通过 `XposedService.getScope()` 判断框架作用域里是否已同时勾选系统框架与电量与性能。
     */
    fun hasExpectedScope(frameworkState: XposedPrefsBridge.XposedFrameworkState): Boolean {
        return frameworkState.isServiceConnected &&
            frameworkState.hasSystemScope &&
            frameworkState.hasPowerKeeperScope
    }

    /**
     * 拉起 LSPosed 寄生管理器的模块列表页。
     *
     * @return true 表示已发出触发请求；框架未安装时系统不会给出可判定结果，因此不做存在性断言
     */
    fun openModuleList(context: Context): Boolean {
        if (PrivilegedExecutor.isAvailable()) {
            // 与模块 action.sh 完全一致：以 shell 身份向 android 包投递密钥码广播（Root / Shizuku 均可）
            val command = "am broadcast -a $secretCodeAction -d $SECRET_CODE android"
            val output = runCatching { PrivilegedExecutor.exec(command) }.getOrDefault(emptyList())
            if (output.any { it.contains("Broadcast completed", ignoreCase = true) }) return true
        }

        val intent = Intent(secretCodeAction).apply {
            data = Uri.parse(SECRET_CODE)
            // 与 action.sh 里的 `am broadcast ... android` 一致：投递给 android 包内的接收器
            setPackage("android")
        }

        return runCatching {
            context.sendBroadcast(intent)
            true
        }.getOrDefault(false)
    }
}
