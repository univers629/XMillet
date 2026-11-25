package io.github.univers629.millet.core

import android.content.Context
import android.provider.Settings
import io.github.univers629.millet.ipc.PowerKeeperBinderClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 系统级省电策略与电池优化（HyperOS / MIUI / AOSP）辅助工具：
 * - 当在 XMillet 中开启“免冻结白名单”时，同步将该应用在系统设置里的省电策略设为「无限制」；
 * - 当在 XMillet 中关闭“免冻结白名单”时，同步将该应用在系统设置里的省电策略恢复为「智能推荐」；
 * - 结合 DeviceIdleController 白名单、appops RUN_ANY_IN_BACKGROUND、
 *   PowerKeeper userTable (bgControl) 以及 MILLET_NO_RESTRICT_APP 全链路协同写入。
 *
 * 优先走模块 IPC：system_server 里的模块直接调用上面几个内部入口，不需要 Root、也不会弹授权提示。
 * 只有模块没启用（Binder 拿不到）时，才退回 Root / Shizuku 脚本兜底（见 [PrivilegedExecutor]）——
 * 那时把多条命令拼成一段脚本一次执行，避免 5～6 次授权请求。
 * Shizuku 为 adb 身份时 PowerKeeper userTable 写不进去（缺 HIDDEN_MODE_PROVIDER），
 * 但 deviceidle、appops 与 MILLET_NO_RESTRICT_APP 都能写，后者才是 Greezer 实际认的名单。
 */
object SystemBatteryOptimizationHelper {

    /**
     * 将指定应用在系统设置里设置为“忽略电池优化 / 省电策略无限制”。
     *
     * @return 是否成功（模块 IPC 与 Root 兜底都失败时为 false）
     */
    suspend fun setAppIgnoringBatteryOptimization(
        context: Context,
        packageName: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext false

        // 1. 模块在系统进程里，直接写，无需 Root
        val viaModule = runCatching {
            PowerKeeperBinderClient.applyBatteryPolicy(packageName, noRestrict = true)
        }.getOrNull()
        if (viaModule == true) {
            Logger.info("省电策略已由模块写入无限制: $packageName")
            return@withContext true
        }

        // 2. 模块缺席：退回 Root / Shizuku 脚本
        PrivilegedExecutor.ensureReady()

        val viaRoot = runCatching {
            PrivilegedExecutor.exec(buildNoRestrictScript(packageName))
            updateMilletNoRestrictSetting(context, packageName, add = true)
            isInNoRestrictSetting(context, packageName)
        }.getOrDefault(false)
        if (!viaRoot) {
            Logger.warn("写入系统省电策略失败（模块未接管且 Root / Shizuku 均不可用）: $packageName")
        }
        viaRoot
    }

    /**
     * 将指定应用在系统设置里恢复为默认的“智能推荐 (miuiAuto)”。
     */
    suspend fun restoreAppBatteryOptimizationToDefault(
        context: Context,
        packageName: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext false

        val viaModule = runCatching {
            PowerKeeperBinderClient.applyBatteryPolicy(packageName, noRestrict = false)
        }.getOrNull()
        if (viaModule == true) {
            Logger.info("省电策略已由模块恢复默认: $packageName")
            return@withContext true
        }

        PrivilegedExecutor.ensureReady()

        val viaRoot = runCatching {
            PrivilegedExecutor.exec(buildRestoreScript(packageName))
            updateMilletNoRestrictSetting(context, packageName, add = false)
            !isInNoRestrictSetting(context, packageName)
        }.getOrDefault(false)
        if (!viaRoot) {
            Logger.warn("恢复系统省电策略失败（模块未接管且 Root / Shizuku 均不可用）: $packageName")
        }
        viaRoot
    }

    /** 读系统「无限制」名单判断应用是否在列；只读，不需要额外权限。 */
    private fun isInNoRestrictSetting(context: Context, packageName: String): Boolean {
        val raw = runCatching {
            Settings.System.getString(context.contentResolver, SETTING_MILLET_NO_RESTRICT_APP)
        }.getOrNull() ?: return false
        return raw.split(',').any { it.trim() == packageName }
    }

    /** Root 兜底：写入「无限制」的四条系统命令，拼成一段脚本一次执行。 */
    private fun buildNoRestrictScript(packageName: String): String = buildString {
        appendLine("dumpsys deviceidle whitelist +$packageName")
        appendLine("cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow")
        appendLine(
            "content update --uri content://com.miui.powerkeeper.configure/userTable" +
                " --bind bgControl:s:noRestrict --where \"pkgName='$packageName'\""
        )
        appendLine(
            "content insert --uri content://com.miui.powerkeeper.configure/userTable" +
                " --bind userId:i:0 --bind pkgName:s:$packageName --bind bgControl:s:noRestrict"
        )
        appendLine("am force-stop com.miui.powerkeeper")
    }

    /** Root 兜底：恢复「智能推荐」的系统命令，拼成一段脚本一次执行。 */
    private fun buildRestoreScript(packageName: String): String = buildString {
        appendLine("dumpsys deviceidle whitelist -$packageName")
        appendLine("cmd appops set $packageName RUN_ANY_IN_BACKGROUND default")
        appendLine(
            "content update --uri content://com.miui.powerkeeper.configure/userTable" +
                " --bind bgControl:s:miuiAuto --where \"pkgName='$packageName'\""
        )
        appendLine("am force-stop com.miui.powerkeeper")
    }

    /**
     * Root 兜底路径维护 `Settings.System.MILLET_NO_RESTRICT_APP`（Greezer 真正认的「无限制」名单）。
     *
     * 当前值可以从 ContentResolver 直接读，不需要 root；只有真正发生变化时才写一次。
     * 模块在线时这一步由 `BatteryPolicyApplier` 在系统进程里完成，不走这里。
     */
    private fun updateMilletNoRestrictSetting(context: Context, packageName: String, add: Boolean) {
        runCatching {
            val cr = context.contentResolver
            val current = Settings.System.getString(cr, SETTING_MILLET_NO_RESTRICT_APP) ?: ""
            val packages = current.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            val changed = if (add) packages.add(packageName) else packages.remove(packageName)
            if (changed) {
                val newValue = packages.joinToString(", ")
                PrivilegedExecutor.exec("settings put system $SETTING_MILLET_NO_RESTRICT_APP \"$newValue\"")
            }
        }
    }
}
