package io.github.univers629.millet.ui.screen
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import io.github.univers629.millet.ui.util.PopupBackdropHolder
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import io.github.univers629.millet.ui.util.MilletTopAppBar
import io.github.univers629.millet.ui.util.MilletSmallTopAppBar
import io.github.univers629.millet.ui.util.BarIconButton

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import io.github.univers629.millet.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.univers629.millet.core.PrefReader
import io.github.univers629.millet.core.PrefsDefaults
import io.github.univers629.millet.core.PrefsKeys
import io.github.univers629.millet.ui.BackupRestoreHelper
import io.github.univers629.millet.ui.component.MiuixConfirmDialog
import io.github.univers629.millet.ui.component.TombStatsIcon
import io.github.univers629.millet.ui.component.XMilletIcons
import io.github.univers629.millet.ui.theme.LocalEnableBlur
import io.github.univers629.millet.ui.theme.UiThemeState
import io.github.univers629.millet.ui.util.AppUpdateStatus
import io.github.univers629.millet.ui.util.BlurredBar
import io.github.univers629.millet.ui.util.BugreportHelper
import io.github.univers629.millet.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun SettingsScreenMiuix(
    themeState: UiThemeState,
    configVersion: Int = 0,
    bottomInnerPadding: Dp,
    updateStatus: AppUpdateStatus = AppUpdateStatus.Idle,
    availableUpdateVersion: String? = null,
    onCheckForUpdates: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenOobe: () -> Unit = {},
    onOpenAbout: () -> Unit,
    onConfigImportedOrReset: () -> Unit,
) {
    val context = LocalContext.current
    val sp = remember { PrefReader.openUiPrefs(context) }
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    var disableKill by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(sp, PrefsKeys.DISABLE_KILL))
    }
    var disableCharge by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(sp, PrefsKeys.DISABLE_CHARGE))
    }
    var disableScreenOnThaw by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(sp, PrefsKeys.DISABLE_SCREEN_ON_THAW))
    }
    var blockPowerSaveWhitelist by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(sp, PrefsKeys.BLOCK_ADD_POWER_SAVE_WHITELIST_APPS))
    }
    var freezeDualApp by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(sp, PrefsKeys.FREEZE_DUAL_APP))
    }
    var isLog by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(sp, PrefsKeys.IS_LOG))
    }
    var simplifyLog by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(sp, PrefsKeys.SIMPLIFY_LOG))
    }

    var enableDoze by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(sp, PrefsKeys.ENABLE_DOZE))
    }
    var skipStateCheck by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(sp, PrefsKeys.SKIP_STATE_CHECK))
    }
    var disableMotionListener by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(sp, PrefsKeys.DISABLE_MOTION_LISTENER))
    }

    var showRebootNotice by remember { mutableStateOf(false) }

    val aliasDefault = remember {
        ComponentName(context.packageName, "${context.packageName}.ui.MainActivityAlias")
    }
    var showLauncherIcon by remember {
        val stateDefault = context.packageManager.getComponentEnabledSetting(aliasDefault)
        mutableStateOf(stateDefault != PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showSendLogDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                }.orEmpty()
                if (json.isNotBlank()) {
                    BackupRestoreHelper.importFromJson(context, json)
                    onConfigImportedOrReset()
                    Toast.makeText(context, "配置导入并同步成功", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { e ->
                Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showRebootNotice) {
        MiuixConfirmDialog(
            show = true,
            title = "需重启系统生效",
            message = "「墓碑与进程跟踪注入」控制系统服务启动期的核心底层注入，修改后请重启手机或软重启系统框架生效。",
            confirmText = "知道了",
            dismissText = null,
            onConfirm = { showRebootNotice = false },
            onDismiss = { showRebootNotice = false }
        )
    }

    if (showResetConfirmDialog) {
        MiuixConfirmDialog(
            show = true,
            title = "恢复默认配置",
            message = "确定要清空所有自定义应用名单并恢复初始开关状态（禁止杀后台=开启，消息应用=微信）吗？",
            confirmText = "确认重置",
            dismissText = "取消",
            onConfirm = {
                showResetConfirmDialog = false
                BackupRestoreHelper.resetToDefaults(context)
                onConfigImportedOrReset()
                Toast.makeText(context, "已恢复出厂默认配置", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showResetConfirmDialog = false }
        )
    }

    LoadingDialogMiuix(show = isLoading)

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                MilletTopAppBar(
                    color = barColor,
                    title = "设置",
                    scrollBehavior = scrollBehavior
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding,
                overscrollEffect = null
            ) {
                item {
                    SmallTitle(
                        text = "外观与主题",
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        GlassDropdownPreference(backdrop = backdrop, 
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.DarkMode,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "颜色模式",
                            items = listOf("跟随系统", "始终浅色", "始终深色"),
                            selectedIndex = themeState.darkMode,
                            onSelectedIndexChange = { themeState.updateDarkMode(it) }
                        )

                        ArrowPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.ColorLens,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "外观个性化",
                            summary = "悬浮底栏、玻璃材质和动态取色",
                            onClick = onOpenAppearance
                        )

                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = TombStatsIcon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "墓碑统计",
                            summary = "在底栏显示墓碑统计页：冻结、唤醒与拦截次数",
                            checked = themeState.showTombStatsTab,
                            onCheckedChange = { themeState.updateShowTombStatsTab(it) }
                        )

                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Show,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "显示桌面启动图标",
                            summary = "关闭后可从 LSPosed 管理器的模块设置中打开 XMillet",
                            checked = showLauncherIcon,
                            onCheckedChange = { visible ->
                                showLauncherIcon = visible
                                context.packageManager.setComponentEnabledSetting(
                                    aliasDefault,
                                    if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                    PackageManager.DONT_KILL_APP
                                )
                            }
                        )
                    }

                    SmallTitle(
                        text = "Millet 墓碑冻结全局策略"
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.CleaningServices,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "禁止息屏与夜间清理后台",
                            summary = "拦截 PowerCheckerController 与 PowerStateMachine自动杀后台",
                            checked = disableKill,
                            onCheckedChange = {
                                disableKill = it
                                PrefReader.putBooleanAndNotify(sp, PrefsKeys.DISABLE_KILL, it)
                            }
                        )
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.BatteryChargingFull,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "禁止插电时全量解冻",
                            summary = "拦截 MilletBrightHandler what=16 与 Greezer 的插电解冻",
                            checked = disableCharge,
                            onCheckedChange = {
                                disableCharge = it
                                PrefReader.putBooleanAndNotify(sp, PrefsKeys.DISABLE_CHARGE, it)
                            }
                        )
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.LightMode,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "亮屏时保持冻结",
                            summary = "屏幕亮起时，不让系统批量解冻后台应用。此项不控制手机进入深度睡眠",
                            checked = disableScreenOnThaw,
                            onCheckedChange = {
                                disableScreenOnThaw = it
                                PrefReader.putBooleanAndNotify(sp, PrefsKeys.DISABLE_SCREEN_ON_THAW, it)
                            }
                        )
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = XMilletIcons.AppPolicy,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "禁止篡改省电白名单",
                            summary = "拦截电量与性能后台自动增删省电白名单（保留你在系统设置里的手动修改）",
                            checked = blockPowerSaveWhitelist,
                            onCheckedChange = {
                                blockPowerSaveWhitelist = it
                                PrefReader.putBooleanAndNotify(
                                    sp,
                                    PrefsKeys.BLOCK_ADD_POWER_SAVE_WHITELIST_APPS,
                                    it
                                )
                            }
                        )
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "控制双开应用冻结",
                            summary = "接管双开空间（用户 999）的冻结，应用策略单独设置；原生包名级名单仍可能共享",
                            checked = freezeDualApp,
                            onCheckedChange = {
                                freezeDualApp = it
                                PrefReader.putBooleanAndNotify(
                                    sp,
                                    PrefsKeys.FREEZE_DUAL_APP,
                                    it
                                )
                            }
                        )
                    }

                    SmallTitle(text = "Doze 深度睡眠策略")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.Bedtime,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "启用 Doze 省电控制增强",
                            summary = "息屏且未充电时，约 10 秒后尝试进入深度睡眠。亮屏或充电时，退出本模块开启的强制睡眠",
                            checked = enableDoze,
                            onCheckedChange = {
                                enableDoze = it
                                PrefReader.putBooleanAndNotify(sp, PrefsKeys.ENABLE_DOZE, it)
                            }
                        )
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.FastForward,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "跳过 Doze 中间传感检查",
                            summary = "当状态进入IDLE_PENDING(2)时直接跃迁至 IDLE_MAINTENANCE(6)",
                            checked = skipStateCheck,
                            onCheckedChange = {
                                skipStateCheck = it
                                PrefReader.putBooleanAndNotify(sp, PrefsKeys.SKIP_STATE_CHECK, it)
                            }
                        )
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "屏蔽 Doze 移动传感器唤醒",
                            summary = "DeviceIdleController#motionLocked，移动手机不退出深度睡眠",
                            checked = disableMotionListener,
                            onCheckedChange = {
                                disableMotionListener = it
                                PrefReader.putBooleanAndNotify(sp, PrefsKeys.DISABLE_MOTION_LISTENER, it)
                            }
                        )
                    }

                    SmallTitle(text = "日志与电量诊断")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.BugReport,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "开启详细运行日志",
                            summary = "记录运行拦截、电量统计与 Doze 日志并写入本地目录",
                            checked = isLog,
                            onCheckedChange = { enabled ->
                                isLog = enabled
                                PrefReader.editAllAndNotify(sp) {
                                    putBoolean(PrefsKeys.IS_LOG, enabled)
                                    putBoolean(PrefsKeys.LOG_TO_LOCAL, enabled)
                                    putBoolean(PrefsKeys.BATTERY_CHANGE_OBSERVE, enabled)
                                    putBoolean(PrefsKeys.DOZE_LOG, enabled)
                                    putString(PrefsKeys.LOG_LEVEL, PrefsDefaults.LOG_LEVEL)
                                    putString(PrefsKeys.LOG_TYPE, PrefsDefaults.LOG_TYPE)
                                }
                            }
                        )
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Tune,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "启用墓碑日志与进程级跟踪",
                            summary = "跟踪单进程冻结并输出墓碑日志(F)/(T)，需重启生效",
                            checked = simplifyLog,
                            onCheckedChange = {
                                simplifyLog = it
                                PrefReader.putBooleanAndNotify(sp, PrefsKeys.SIMPLIFY_LOG, it)
                                showRebootNotice = true
                            }
                        )
                        ArrowPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "发送日志",
                            summary = "打包诊断日志与运行状态，支持保存或分享",
                            onClick = { showSendLogDialog = true }
                        )
                    }

                    SmallTitle(text = "配置备份与恢复")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        ArrowPreference(
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Backup,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "备份配置",
                            summary = "将全部 ${PrefsKeys.ALL_KEYS.size} 项配置与名单导出或分享为 JSON",
                            onClick = { showBackupDialog = true }
                        )

                        ArrowPreference(
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Import,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "恢复配置",
                            summary = "选择已保存的 JSON 文件恢复策略并实时通知系统生效",
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            }
                        )

                        ArrowPreference(
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Reset,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "恢复默认配置",
                            summary = "清空所有自定义应用集合并恢复初始推荐开关",
                            onClick = { showResetConfirmDialog = true }
                        )
                    }

                    SmallTitle(text = "关于")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        ArrowPreference(
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Tasks,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "权限配置向导",
                            summary = "重新检查所需应用权限",
                            onClick = onOpenOobe
                        )
                        val updateSummary = when (updateStatus) {
                            AppUpdateStatus.Checking -> "正在检查更新…"
                            AppUpdateStatus.Available -> if (!availableUpdateVersion.isNullOrBlank()) {
                                "发现新版本 v$availableUpdateVersion，点击查看"
                            } else {
                                "发现新版本，点击查看"
                            }
                            AppUpdateStatus.UpToDate -> "当前已是最新版本 (${BuildConfig.VERSION_NAME})"
                            AppUpdateStatus.Failed -> "检查失败，点击重试"
                            AppUpdateStatus.Idle -> "检查 GitHub Releases 是否有新版本"
                        }
                        ArrowPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "检查更新",
                            summary = updateSummary,
                            onClick = onCheckForUpdates
                        )
                        ArrowPreference(
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Info,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "关于 XMillet",
                            summary = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                            onClick = onOpenAbout
                        )
                    }

                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }

            SendLogDialog(
                show = showSendLogDialog,
                onDismissRequest = { showSendLogDialog = false },
                onSetLoading = { isLoading = it }
            )
            BackupConfigDialog(
                show = showBackupDialog,
                onDismissRequest = { showBackupDialog = false },
                onSetLoading = { isLoading = it }
            )
        }
    }
}

@Composable
private fun LoadingDialogMiuix(show: Boolean) {
    GlassDialog(backdrop = PopupBackdropHolder.current, 
        show = show,
        onDismissRequest = { },
        content = {
            val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
            NavigationBackHandler(
                state = navEventState,
                isBackEnabled = true,
                onBackCompleted = { },
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    InfiniteProgressIndicator(
                        color = colorScheme.onBackground
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = "正在处理…",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    )
}

@Composable
private fun SendLogDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSetLoading: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    SaveShareActionDialog(
        show = show,
        title = "发送日志",
        saveTitle = "保存日志",
        shareTitle = "发送日志",
        shareChooserTitle = "发送日志",
        mimeType = "application/gzip",
        defaultFileName = { BugreportHelper.buildBugreportFileName() },
        saveSuccessToast = "日志已保存",
        onDismissRequest = onDismissRequest,
        onSetLoading = onSetLoading,
        onSaveToUri = { uri ->
            val bugreport = BugreportHelper.getBugreportFile(context)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                bugreport.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: error("无法写入目标文件")
        },
        onCreateShareFile = { BugreportHelper.getBugreportFile(context) }
    )
}

@Composable
private fun BackupConfigDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSetLoading: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    SaveShareActionDialog(
        show = show,
        title = "备份配置",
        saveTitle = "保存备份",
        shareTitle = "分享备份",
        shareChooserTitle = "分享配置备份",
        mimeType = "application/json",
        defaultFileName = { BugreportHelper.buildBackupFileName() },
        saveSuccessToast = "配置备份已保存",
        onDismissRequest = onDismissRequest,
        onSetLoading = onSetLoading,
        onSaveToUri = { uri ->
            val json = BackupRestoreHelper.exportToJson(context)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("无法写入目标文件")
        },
        onCreateShareFile = { BugreportHelper.createBackupCacheFile(context) }
    )
}

@Composable
private fun SaveShareActionDialog(
    show: Boolean,
    title: String,
    saveTitle: String,
    shareTitle: String,
    shareChooserTitle: String,
    mimeType: String,
    defaultFileName: () -> String,
    saveSuccessToast: String,
    onDismissRequest: () -> Unit,
    onSetLoading: (Boolean) -> Unit,
    onSaveToUri: suspend (Uri) -> Unit,
    onCreateShareFile: suspend () -> java.io.File,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeType)
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            onSetLoading(true)
            try {
                withContext(Dispatchers.IO) {
                    onSaveToUri(uri)
                }
                Toast.makeText(context, saveSuccessToast, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(context, "保存失败：${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                onSetLoading(false)
            }
        }
    }

    GlassDialog(backdrop = PopupBackdropHolder.current, 
        show = show,
        onDismissRequest = onDismissRequest,
        insideMargin = DpSize(0.dp, 0.dp),
        content = {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp),
                text = title,
                fontSize = MiuixTheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = colorScheme.onSurface
            )
            ArrowPreference(
                title = saveTitle,
                startAction = {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = colorScheme.onSurface
                    )
                },
                onClick = {
                    createDocumentLauncher.launch(defaultFileName())
                    onDismissRequest()
                },
                insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            )
            ArrowPreference(
                title = shareTitle,
                startAction = {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = colorScheme.onSurface
                    )
                },
                onClick = {
                    scope.launch {
                        onDismissRequest()
                        onSetLoading(true)
                        try {
                            val file = withContext(Dispatchers.IO) {
                                onCreateShareFile()
                            }
                            val uri: Uri = FileProvider.getUriForFile(
                                context,
                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_STREAM, uri)
                                setDataAndType(uri, mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, shareChooserTitle)
                            )
                        } catch (t: Throwable) {
                            Toast.makeText(context, "分享失败：${t.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            onSetLoading(false)
                        }
                    }
                },
                insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            )
            TextButton(
                text = "取消",
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 24.dp)
                    .padding(horizontal = 24.dp)
            )
        }
    )
}
