package io.github.univers629.millet.ui.screen
import io.github.univers629.millet.ui.util.GlassOverlayListPopup
import io.github.univers629.millet.ui.util.MilletTopAppBar
import io.github.univers629.millet.ui.util.MilletSmallTopAppBar
import io.github.univers629.millet.ui.util.BarIconButton

import android.os.Build
import android.widget.Toast
import io.github.univers629.millet.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univers629.millet.core.FreezeModeLabels
import io.github.univers629.millet.core.RootHelper
import io.github.univers629.millet.core.SoftRebootHelper
import io.github.univers629.millet.core.VersionGate
import io.github.univers629.millet.core.XposedPrefsBridge
import io.github.univers629.millet.ipc.PowerKeeperBinderClient
import io.github.univers629.millet.ui.component.AppIconImage
import io.github.univers629.millet.ui.component.DropdownItem
import io.github.univers629.millet.ui.component.HookCheckSheetMiuix
import io.github.univers629.millet.ui.component.ListPopupDefaults
import io.github.univers629.millet.ui.component.MiuixConfirmDialog
import io.github.univers629.millet.ui.component.StatusTagMiuix
import io.github.univers629.millet.ui.component.WarningCard
import io.github.univers629.millet.ui.component.WarningLevel
import io.github.univers629.millet.ui.component.XMilletIcons
import io.github.univers629.millet.ui.theme.LocalEnableBlur
import io.github.univers629.millet.ui.theme.isInDarkTheme
import io.github.univers629.millet.ui.util.BlurredBar
import io.github.univers629.millet.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import io.github.univers629.millet.ui.util.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

data class RunningProcessUiModel(
    val appName: String,
    val packageName: String,
    val uid: Int,
    val pids: List<Int>,
    val isFrozen: Boolean,
    val freezerTypes: Set<RootHelper.FreezerType> = emptySet(),
    val processCount: Int = 1,
    val frozenCount: Int = 0,
    val unknownCount: Int = 0,
    val memSizeKb: Long = 0L,
    val formattedMemSize: String = "",
    val isDualApp: Boolean = false,
    val applicationInfo: android.content.pm.ApplicationInfo? = null
)

@Composable
fun HomeScreenMiuix(
    runtimeStatus: PowerKeeperBinderClient.RuntimeStatusSnapshot,
    configuredRuleCount: Int,
    runningProcesses: List<RunningProcessUiModel>,
    bottomInnerPadding: Dp,
    onRefresh: () -> Unit,
    onThawAll: () -> Unit = {},
    onNavigateToTab: (io.github.univers629.millet.ui.component.BottomBarDestination) -> Unit,
    onNavigateToProcessList: (filterIndex: Int) -> Unit = {
        onNavigateToTab(io.github.univers629.millet.ui.component.BottomBarDestination.Processes)
    },
    onOpenAbout: () -> Unit = {},
    onOpenAppProfile: (packageName: String, uid: Int) -> Unit = { _, _ -> },
    snapshotUnavailable: Boolean = false,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    var showTopPopup by remember { mutableStateOf(false) }
    var showHookCheckDialog by remember { mutableStateOf(false) }
    var kernelReport by remember { mutableStateOf(PowerKeeperBinderClient.KernelFreezerReport()) }
    var kernelProbeGeneration by remember { mutableStateOf(0) }
    val frameworkState by XposedPrefsBridge.frameworkState.collectAsState()

    // 应用全部已采集进程确认冻结时计入；主应用与双开分别统计，记账不替代快照。
    val frozenApps = remember(runningProcesses) {
        runningProcesses.filter { it.isFrozen }
    }
    val frozenAppCount = remember(frozenApps) {
        frozenApps.map { "${it.packageName}:${it.uid}" }.distinct().size
    }

    // 打开检测弹窗时在 IO 线程探测内核冻结器类型与 XposedService 状态
    LaunchedEffect(showHookCheckDialog, kernelProbeGeneration) {
        if (showHookCheckDialog) {
            kernelReport = withContext(Dispatchers.IO) {
                XposedPrefsBridge.refreshFrameworkState()
                // system_server 才有 /sys/fs/cgroup 与 /proc/<pid>/wchan 的读权限；
                // App 侧自行探测需要 root，会弹一次超级用户提示，因此不做回退。
                PowerKeeperBinderClient.queryKernelFreezerReport()
                    ?: PowerKeeperBinderClient.KernelFreezerReport()
            }
        }
    }


    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                MilletTopAppBar(
                    color = barColor,
                    title = "XMillet",
                    actions = {
                        BarIconButton(
                            modifier = Modifier.padding(end = 4.dp),
                            onClick = { showHookCheckDialog = true }
                        ) {
                            val allPass = runtimeStatus.binderAlive && runtimeStatus.powerKeeperConnected
                            Icon(
                                imageVector = Icons.Rounded.AcUnit,
                                contentDescription = "运行环境与服务检测",
                                tint = if (allPass) colorScheme.primary else Color(0xFFFF9500)
                            )
                        }
                        BarIconButton(
                            onClick = { showTopPopup = true },
                            holdDownState = showTopPopup
                        ) {
                            Icon(
                                imageVector = MiuixIcons.MoreCircle,
                                contentDescription = "更多操作",
                                tint = colorScheme.onBackground
                            )
                        }
                        GlassOverlayListPopup(
                            show = showTopPopup,
                            popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showTopPopup = false },
                            content = {
                                val menuItems = listOf("软重启", "刷新状态")
                                ListPopupColumn {
                                    menuItems.forEachIndexed { idx, text ->
                                        DropdownItem(
                                            text = text,
                                            optionSize = menuItems.size,
                                            index = idx,
                                            onSelectedIndexChange = {
                                                showTopPopup = false
                                                when (idx) {
                                                    0 -> {
                                                        scope.launch {
                                                            val granted = RootHelper.isRootGranted ||
                                                                RootHelper.requestRoot(20_000L)
                                                            val started = granted && SoftRebootHelper.softReboot()
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(
                                                                    context,
                                                                    if (started) "正在软重启，系统服务将重新拉起"
                                                                    else "软重启需要先授予 Root 权限",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                    }
                                                    1 -> onRefresh()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                item {
                    Spacer(Modifier.height(0.dp))
                }

                // 1. 顶部状态卡片（完全对齐 KernelSU HomeMiuix 标准卡片）
                item {
                    StatusCardMiuix(
                        runtimeStatus = runtimeStatus,
                        onClick = onRefresh
                    )
                }

                // 2. 统计看板卡片组
                if (snapshotUnavailable) {
                    item {
                        WarningCard(
                            message = "进程快照不可用，保留上次结果；冻结统计暂不代表当前状态。",
                            level = WarningLevel.Notice,
                            onClick = onRefresh
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DataSummaryCard(
                            title = if (snapshotUnavailable) "上次已冻结" else "当前已冻结",
                            count = frozenAppCount,
                            unit = "应用",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToProcessList(1) }
                        )
                        DataSummaryCard(
                            title = "已配置策略",
                            count = configuredRuleCount,
                            unit = "应用",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToTab(io.github.univers629.millet.ui.component.BottomBarDestination.Apps) }
                        )
                    }
                }

                // 2. 若 PowerKeeper 尚未握手连接，展示提示卡片
                if (runtimeStatus.binderAlive && !runtimeStatus.powerKeeperConnected) {
                    item {
                        WarningCard(
                            message = "「电量与性能 (PowerKeeper)」尚未连接到系统服务。重启电量与性能进程或亮灭屏一次即可自动完成双向握手。",
                            level = WarningLevel.Notice,
                            onClick = onRefresh
                        )
                    }
                } else if (!runtimeStatus.binderAlive) {
                    item {
                        WarningCard(
                            message = "XMillet 系统服务未连接。请确认已在 LSPosed 中勾选启用 XMillet（作用域：系统框架 + 电量与性能）并重启系统。",
                            level = WarningLevel.Error,
                            onClick = onRefresh
                        )
                    }
                }

                // 3. 系统基本信息卡片（复刻 KernelSU InfoCard 样式，带左侧 24.dp 图标）
                item {
                    SystemInfoCard(runtimeStatus = runtimeStatus)
                }

                // 4. 核心服务状态独立卡片（复刻 KernelSU SELinux/Seccomp 独立安全卡片规范，带左侧 24.dp 图标）
                item {
                    CoreServicesStatusCard(runtimeStatus = runtimeStatus)
                }

                // 5. 关于 XMillet 卡片（与设置页底部关于卡片完全对齐）
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
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
                }

                item {
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }

            HookCheckSheetMiuix(
                show = showHookCheckDialog,
                runtimeStatus = runtimeStatus,
                kernelFreezer = kernelReport.mode,
                kernelEvidenceConfirmed = kernelReport.confirmed,
                kernelEvidenceSource = kernelReport.evidence,
                milletBackendState = kernelReport.backend,
                frameworkState = frameworkState,
                onRequestScope = {
                    XposedPrefsBridge.requestMissingScopes { _, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        onRefresh()
                    }
                },
                onHotReloadStale = {
                    XposedPrefsBridge.hotReloadStaleTargets { _, _, summary ->
                        Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
                        onRefresh()
                    }
                },
                onRefresh = {
                    kernelProbeGeneration++
                    scope.launch(Dispatchers.IO) {
                        XposedPrefsBridge.refreshFrameworkState()
                    }
                    onRefresh()
                },
                onDismiss = { showHookCheckDialog = false }
            )
        }
    }
}

@Composable
private fun StatusCardMiuix(
    runtimeStatus: PowerKeeperBinderClient.RuntimeStatusSnapshot,
    onClick: () -> Unit,
) {
    // 激活判据：Binder 已连上，且 system_server 侧冻结闸门已就绪（或 PowerKeeper 客户端已注册）。
    // 主进程与 system_server 的握手是异步的，任一信号成立即视为可用，避免握手时序导致的「未激活」误判。
    val isWorking = runtimeStatus.binderAlive && (runtimeStatus.greezeReady || runtimeStatus.powerKeeperConnected)
    val workingText = if (isWorking) "工作中" else "未激活"
    val workingMode = if (isWorking) "Greeze • PowerKeeper" else "服务未连接"
    // 徽标显示模块真实装上的点位数：旧版写死 "40 Hooks"，而实际规格数随版本增长，
    // 不同 ROM 还会跳过若干点位。未上报（旧模块/未连上）时显示「已就绪」而不是假数字。
    val installedHooks = runtimeStatus.installedHooks
    val hookBadgeLabel = when {
        !isWorking -> "未就绪"
        installedHooks > 0 -> "$installedHooks Hooks"
        else -> "已就绪"
    }
    val isDark = isInDarkTheme()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = when {
                    isWorking -> when {
                        isDynamicColor -> colorScheme.secondaryContainer
                        isDark -> Color(0xFF1A3825)
                        else -> Color(0xFFDFFAE4)
                    }
                    else -> when {
                        isDynamicColor -> colorScheme.surfaceContainer
                        isDark -> Color(0xFF2E1919)
                        else -> Color(0xFFFDE8E8)
                    }
                }
            ),
            onClick = onClick,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(27.dp, 31.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        modifier = Modifier.size(110.dp),
                        imageVector = if (isWorking) {
                            Icons.Rounded.CheckCircleOutline
                        } else {
                            Icons.Rounded.ErrorOutline
                        },
                        tint = when {
                            isWorking -> if (isDynamicColor) colorScheme.primary.copy(alpha = 0.8f) else Color(0xFF36D167)
                            else -> if (isDynamicColor) colorScheme.error.copy(alpha = 0.85f) else Color(0xFFFF5252)
                        },
                        contentDescription = null
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp, 12.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        text = workingMode,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isWorking -> when {
                                isDynamicColor -> colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                isDark -> Color(0xFFB8E8C5)
                                else -> Color(0xFF164A29)
                            }
                            else -> when {
                                isDynamicColor -> colorScheme.onSurfaceVariantSummary
                                isDark -> Color(0xFFFFB4AB)
                                else -> Color(0xFFBA1A1A)
                            }
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp, 14.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Column {
                        Text(
                            text = workingText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                isWorking -> when {
                                    isDynamicColor -> colorScheme.onSecondaryContainer
                                    isDark -> Color.White
                                    else -> Color(0xFF164A29)
                                }
                                else -> when {
                                    isDynamicColor -> colorScheme.onSurface
                                    isDark -> Color.White
                                    else -> Color(0xFFBA1A1A)
                                }
                            }
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isWorking) "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})" else "点击重新检测系统服务连接",
                                modifier = Modifier.weight(1f, fill = false),
                                fontSize = 12.sp,
                                color = when {
                                    isWorking -> when {
                                        isDynamicColor -> colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                        isDark -> Color(0xFFB8E8C5).copy(alpha = 0.85f)
                                        else -> Color(0xFF164A29).copy(alpha = 0.85f)
                                    }
                                    else -> when {
                                        isDynamicColor -> colorScheme.onSurfaceVariantSummary
                                        isDark -> Color(0xFFFFB4AB).copy(alpha = 0.85f)
                                        else -> Color(0xFFBA1A1A).copy(alpha = 0.85f)
                                    }
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            StatusTagMiuix(
                                label = hookBadgeLabel,
                                contentColor = when {
                                    isWorking -> when {
                                        isDynamicColor -> colorScheme.onTertiaryContainer
                                        isDark -> Color(0xFFB8E8C5)
                                        else -> Color(0xFF164A29)
                                    }
                                    else -> when {
                                        isDynamicColor -> colorScheme.onErrorContainer
                                        isDark -> Color(0xFFFFDAD6)
                                        else -> Color(0xFFBA1A1A)
                                    }
                                },
                                backgroundColor = when {
                                    isWorking -> when {
                                        isDynamicColor -> colorScheme.tertiaryContainer
                                        isDark -> Color(0xFF315D3E)
                                        else -> Color(0xFFB8E8C5)
                                    }
                                    else -> when {
                                        isDynamicColor -> colorScheme.errorContainer
                                        isDark -> Color(0xFF601410)
                                        else -> Color(0xFFFFDAD6)
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DataSummaryCard(
    title: String,
    count: Int,
    unit: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariantSummary
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = unit,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            Icon(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp),
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariantActions
            )
        }
    }
}

@Composable
private fun InfoText(
    icon: ImageVector,
    title: String,
    content: String,
    bottomPadding: Dp = 24.dp,
    contentColor: Color = colorScheme.onSurfaceVariantSummary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(24.dp),
            tint = colorScheme.onSurface,
        )
        Column {
            Text(
                text = title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Text(
                text = content,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = contentColor,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SystemInfoCard(
    runtimeStatus: PowerKeeperBinderClient.RuntimeStatusSnapshot
) {
    val kernelVer = remember {
        runCatching { System.getProperty("os.version") ?: "unknown" }.getOrDefault("unknown")
    }
    val deviceDesc = remember {
        val marketName = VersionGate.getSystemProperty("ro.product.marketname").ifEmpty { null }
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val name = marketName ?: if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        "$name ($abi)"
    }
    val xposedStatus = if (runtimeStatus.binderAlive) {
        "libxposed API ${runtimeStatus.frameworkApiVersion}"
    } else {
        "libxposed API 102 (Disconnected)"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            InfoText(
                icon = Icons.Rounded.Tag,
                title = "Xposed API 版本",
                content = xposedStatus,
            )
            InfoText(
                icon = Icons.Rounded.DeveloperBoard,
                title = "内核版本",
                content = kernelVer,
            )
            InfoText(
                icon = Icons.Rounded.Smartphone,
                title = "设备型号",
                content = deviceDesc,
                bottomPadding = 0.dp,
            )
        }
    }
}

@Composable
private fun CoreServicesStatusCard(
    runtimeStatus: PowerKeeperBinderClient.RuntimeStatusSnapshot
) {
    val greezeReady = runtimeStatus.greezeReady
    val aurogonReady = runtimeStatus.aurogonReady
    val pkConnected = runtimeStatus.powerKeeperConnected

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            InfoText(
                icon = Icons.Rounded.AcUnit,
                title = "GreezeManager",
                content = if (greezeReady) "已就绪" else "未就绪",
            )
            InfoText(
                icon = Icons.Rounded.FilterList,
                title = "Aurogon",
                content = if (aurogonReady) "已就绪" else "未就绪",
            )
            InfoText(
                icon = XMilletIcons.ProcessBolt,
                title = "PowerKeeper",
                content = if (pkConnected) "已连接" else "未连接",
                bottomPadding = 0.dp,
            )
        }
    }
}
