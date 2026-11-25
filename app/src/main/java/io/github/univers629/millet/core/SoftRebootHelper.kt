package io.github.univers629.millet.core

import android.util.Base64

/**
 * 软重启：只重启 Android 用户态服务（zygote / system_server 随之重启），内核保持运行。
 *
 * 模块首次安装后，LSPosed 还没把代码注入 com.miui.powerkeeper，需要系统服务重新拉起一次才会生效。
 * 完整重启太重，执行一次 `stop` → `start` 就能达到同样效果，代价是十几秒的黑屏与桌面重载。
 */
object SoftRebootHelper {

    private const val SCRIPT_PATH = "/data/local/tmp/xmillet_soft_reboot.sh"

    private val SCRIPT: String = buildString {
        appendLine("#!/system/bin/sh")
        appendLine("# 先等一会儿，让调用方收起菜单并提示用户")
        appendLine("sleep 2")
        appendLine("# 停掉全部 Android 服务（zygote、system_server 与各系统应用）")
        appendLine("stop")
        appendLine("sleep 2")
        appendLine("# 重新拉起，LSPosed 会在启动过程中重新注入模块")
        appendLine("start")
    }

    /**
     * 把脚本写到 /data/local/tmp，再脱离本进程执行，避免 `stop` 时 su 子进程被一起清理。
     *
     * @return false 表示当前没有 Root 权限，调用方应提示用户先授权。
     */
    fun softReboot(): Boolean {
        // stop / start 需要 root 级身份：Root，或以 root 运行的 Shizuku；adb 身份做不到
        if (!PrivilegedExecutor.hasRootLevel()) return false

        val encoded = Base64.encodeToString(SCRIPT.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val command = buildString {
            append("echo ").append(encoded).append(" | base64 -d > ").append(SCRIPT_PATH)
            append(" && chmod 700 ").append(SCRIPT_PATH)
            append(" && setsid sh -c 'nohup sh ").append(SCRIPT_PATH).append(" >/dev/null 2>&1 &'")
        }

        return runCatching {
            PrivilegedExecutor.exec(command)
            true
        }.getOrDefault(false)
    }
}
