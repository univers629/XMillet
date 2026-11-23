package io.github.univers629.millet.ui.screen
import io.github.univers629.millet.ui.util.MilletTopAppBar
import io.github.univers629.millet.ui.util.MilletSmallTopAppBar
import io.github.univers629.millet.ui.util.BarIconButton

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material.icons.rounded.SyncDisabled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univers629.millet.core.AppUidUtils
import io.github.univers629.millet.core.BatteryPolicyHelper
import io.github.univers629.millet.core.FreezeModeLabels
import io.github.univers629.millet.core.PrefReader
import io.github.univers629.millet.core.PrefsKeys
import io.github.univers629.millet.core.RootHelper
import io.github.univers629.millet.core.SystemBatteryOptimizationHelper
import io.github.univers629.millet.ipc.PowerKeeperBinderClient
import io.github.univers629.millet.ui.component.AppIconImage
import io.github.univers629.millet.ui.component.DropdownItem
import io.github.univers629.millet.ui.component.ListPopupDefaults
import io.github.univers629.millet.ui.component.StatusTagMiuix
import io.github.univers629.millet.ui.component.WarningCard
import io.github.univers629.millet.ui.component.WarningLevel
import io.github.univers629.millet.ui.component.XMilletIcons
import io.github.univers629.millet.ui.theme.LocalEnableBlur
import io.github.univers629.millet.ui.util.BlurredBar
import io.github.univers629.millet.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 单应用策略详情页，展示进程快照与七个应用级策略集合。 */
@Composable
fun AppProfileScreenMiuix(
    app: InstalledAppUiModel,
    onBack: () -> Unit,
    onPolicyChanged: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { PrefReader.openUiPrefs(context) }
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    val policyTarget = AppUidUtils.encodeQueryTarget(app.packageName, app.uid)
    val isDualApp = AppUidUtils.isDualAppUid(app.uid)
    val processCacheKey = "${app.packageName}:${app.uid}"
    val pageToken = remember(app.packageName, app.uid) { Any() }
    val currentPageToken by rememberUpdatedState(pageToken)
    var isPageActive by remember(pageToken) { mutableStateOf(true) }
    DisposableEffect(pageToken) {
        onDispose { isPageActive = false }
    }
    var appProcesses by remember(app.packageName, app.uid) {
        mutableStateOf(AppProcessSnapshotCache.get(processCacheKey).orEmpty())
    }
    var isScanningProcesses by remember(app.packageName, app.uid) { mutableStateOf(false) }
    var processSnapshotUnavailable by remember(app.packageName, app.uid) { mutableStateOf(false) }
    var hasScannedProcesses by remember(app.packageName, app.uid) {
        mutableStateOf(AppProcessSnapshotCache.get(processCacheKey) != null)
    }

    suspend fun scanProcesses() {
        if (isScanningProcesses || !isPageActive || currentPageToken !== pageToken) return
        val targetUid = app.uid
        isScanningProcesses = true
        try {
            val snapshot = withContext(Dispatchers.IO) {
                PowerKeeperBinderClient.queryProcessSnapshot()
            }
            if (!isPageActive || currentPageToken !== pageToken) return
            if (snapshot == null) {
                processSnapshotUnavailable = true
                hasScannedProcesses = true
                return
            }
            val list = withContext(Dispatchers.IO) {
                RootHelper.sortProcessEntries(snapshot.entries.filter { it.uid == targetUid })
            }
            if (!isPageActive || currentPageToken !== pageToken) return
            appProcesses = list
            AppProcessSnapshotCache.put(processCacheKey, list)
            processSnapshotUnavailable = false
            hasScannedProcesses = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (isPageActive && currentPageToken === pageToken) {
                processSnapshotUnavailable = true
                hasScannedProcesses = true
            }
        } finally {
            isScanningProcesses = false
        }
    }

    val refreshProcesses: () -> Unit = {
        coroutineScope.launch { scanProcesses() }
    }

    LaunchedEffect(app.packageName, app.uid) {
        // 等转场动画放完再扫描：AnimatedContent 的进场动画是 360ms（MainActivity:517），
        // 扫描结果若在动画中途落地，「正在扫描」的小占位会被撑成 N 行进程，
        // 整页重排会落在动画帧上，造成卡顿。
        delay(PROCESS_SCAN_DELAY_MS)
        scanProcesses()
    }

    var inWhite by remember(app.packageName, app.uid) {
        mutableStateOf(AppUidUtils.containsPolicyTarget(PrefReader.readUiStringSet(prefs, PrefsKeys.WHITE_APP_SET), app.packageName, app.uid))
    }
    var inMessage by remember(app.packageName, app.uid) {
        mutableStateOf(AppUidUtils.containsPolicyTarget(PrefReader.readUiStringSet(prefs, PrefsKeys.MESSAGE_APP_SET), app.packageName, app.uid))
    }
    var inForceFreeze by remember(app.packageName, app.uid) {
        mutableStateOf(AppUidUtils.containsPolicyTarget(PrefReader.readUiStringSet(prefs, PrefsKeys.FORCE_FREEZE_SET), app.packageName, app.uid))
    }
    var inBlockThaw by remember(app.packageName, app.uid) {
        mutableStateOf(AppUidUtils.containsPolicyTarget(PrefReader.readUiStringSet(prefs, PrefsKeys.BLOCK_THAW_SET), app.packageName, app.uid))
    }
    var inForbidSync by remember(app.packageName, app.uid) {
        mutableStateOf(AppUidUtils.containsPolicyTarget(PrefReader.readUiStringSet(prefs, PrefsKeys.FORBID_SYNC_BINDER_SET), app.packageName, app.uid))
    }
    var inNotFreezeFgs by remember(app.packageName, app.uid) {
        mutableStateOf(AppUidUtils.containsPolicyTarget(PrefReader.readUiStringSet(prefs, PrefsKeys.NOT_FREEZE_WHEN_HAS_FGS_SET), app.packageName, app.uid))
    }
    var inIgnoreNetworkWake by remember(app.packageName, app.uid) {
        mutableStateOf(AppUidUtils.containsPolicyTarget(PrefReader.readUiStringSet(prefs, PrefsKeys.IGNORE_NETWORK_WAKE_SET), app.packageName, app.uid))
    }
    // 系统省电策略按包名读取，不作为模块名单的成员判定。
    var systemUnrestricted by remember(app.packageName, app.uid) { mutableStateOf(false) }

    // 系统省电策略的只读回显：不提供绑定/初始化按钮，进页面自动读系统真值。
    var systemPolicyLabel by remember(app.packageName, app.uid) { mutableStateOf("读取中…") }

    suspend fun refreshSystemPolicy() {
        val (unrestricted, label) = withContext(Dispatchers.IO) {
            val value = BatteryPolicyHelper.isPackageNoRestrict(context, app.packageName)
            value to readSystemPolicyLabel(context, app.packageName, value)
        }
        if (!isPageActive || currentPageToken !== pageToken) return
        systemUnrestricted = unrestricted
        systemPolicyLabel = label
        // 开关只跟随模块名单；系统侧是否「无限制」由上方提示条单独展示，
        // 避免系统名单没清干净时把开关顶回开启状态，让用户以为点不动。
    }
    LaunchedEffect(app.packageName, app.uid) {
        refreshSystemPolicy()
    }

    fun buildMembershipSet(key: String, add: Boolean): Set<String> {
        val currentSet = PrefReader.readUiStringSet(prefs, key)
        if (add) currentSet.add(policyTarget) else currentSet.remove(policyTarget)
        return currentSet
    }

    fun updateMemberships(vararg changes: Pair<String, Boolean>) {
        if (changes.isEmpty()) return
        val updates = LinkedHashMap<String, Set<String>>(changes.size)
        for ((key, add) in changes) {
            updates[key] = buildMembershipSet(key, add)
        }
        PrefReader.putStringSetsAndNotify(prefs, updates, changes.first().first)
        onPolicyChanged()
    }

    fun updateMembership(key: String, add: Boolean) {
        updateMemberships(key to add)
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                MilletTopAppBar(
                    color = barColor,
                    title = "应用策略配置",
                    navigationIcon = {
                        BarIconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        val showTopPopup = remember { mutableStateOf(false) }
                        BarIconButton(
                            modifier = Modifier.padding(end = 12.dp),
                            onClick = { showTopPopup.value = true },
                            holdDownState = showTopPopup.value
                        ) {
                            Icon(
                                imageVector = MiuixIcons.MoreCircle,
                                tint = colorScheme.onSurface,
                                contentDescription = "更多操作"
                            )
                        }
                        OverlayListPopup(
                            show = showTopPopup.value,
                            popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showTopPopup.value = false },
                            content = {
                                ListPopupColumn {
                                    val items = listOf(
                                        "启动该应用",
                                        "系统应用详情页"
                                    )
                                    items.forEachIndexed { idx, text ->
                                        DropdownItem(
                                            text = text,
                                            optionSize = items.size,
                                            index = idx,
                                            onSelectedIndexChange = {
                                                showTopPopup.value = false
                                                when (idx) {
                                                    0 -> {
                                                        val launchIntent = context.packageManager
                                                            .getLaunchIntentForPackage(app.packageName)
                                                        if (launchIntent != null) {
                                                            runCatching { context.startActivity(launchIntent) }
                                                        } else {
                                                            Toast.makeText(context, "该应用无桌面启动入口", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    1 -> {
                                                        val intent = Intent(
                                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                            Uri.fromParts("package", app.packageName, null)
                                                        )
                                                        runCatching { context.startActivity(intent) }
                                                    }
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
                    .padding(top = 12.dp)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding,
                overscrollEffect = null
            ) {
                item {
                    // 1. 顶部应用信息卡片（复刻 KernelSU AppProfileMiuix）
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        insideMargin = PaddingValues(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(
                                applicationInfo = app.applicationInfo,
                                label = app.appName,
                                modifier = Modifier.size(64.dp)
                            )
                            Column(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 8.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = app.appName,
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight(600),
                                    fontSize = 17.sp,
                                    modifier = Modifier.basicMarquee(),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (app.versionName.isNotEmpty()) {
                                    Text(
                                        text = "${app.versionName} (${app.versionCode})",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariantSummary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.basicMarquee(),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Text(
                                    text = app.packageName,
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.basicMarquee(),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (app.isDualApp) {
                                    StatusTagMiuix(
                                        label = "双开",
                                        backgroundColor = colorScheme.tertiaryContainer,
                                        contentColor = colorScheme.onTertiaryContainer
                                    )
                                }
                                StatusTagMiuix(
                                    label = "UID ${app.uid}",
                                    backgroundColor = colorScheme.primary.copy(alpha = 0.85f),
                                    contentColor = colorScheme.onPrimary
                                )
                                if (app.isSystem) {
                                    StatusTagMiuix(
                                        label = "SYSTEM",
                                        backgroundColor = colorScheme.secondaryContainer,
                                        contentColor = colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // 2. 系统省电策略「无限制」提示卡片
                    if (systemUnrestricted) {
                        WarningCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                            message = "系统包名级省电策略记录为「无限制 (MILLET_NO_RESTRICT_APP)」，该记录不代表当前进程的内核冻结状态。",
                            level = WarningLevel.Notice
                        )
                    }

                    // 3. 应用进程快照
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SmallTitle(
                            text = "进程快照 (${appProcesses.size})",
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = refreshProcesses,
                            enabled = !isScanningProcesses,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = "刷新进程状态",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        insideMargin = PaddingValues(14.dp)
                    ) {
                        if (processSnapshotUnavailable && appProcesses.isNotEmpty()) {
                            Text(
                                text = "进程快照不可用，本次采集失败；保留上次成功快照。",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        if (appProcesses.isEmpty()) {
                            // 空列表占位保持固定高度，减少扫描期间的布局变化。
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = when {
                                        isScanningProcesses -> "正在扫描进程..."
                                        processSnapshotUnavailable -> "进程快照不可用"
                                        !hasScannedProcesses -> "正在准备进程快照..."
                                        else -> "快照中没有该 UID 的进程"
                                    },
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = when {
                                        isScanningProcesses -> "请稍候..."
                                        processSnapshotUnavailable -> "本次采集失败，点击刷新可重试"
                                        !hasScannedProcesses -> "转场结束后立即读取进程表"
                                        else -> "仅表示本次采集未返回目标 UID 的进程，不推断原因"
                                    },
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                            }
                        } else {
                            val totalMemKb = appProcesses.sumOf { it.rssKb }
                            val totalMemFormatted = RootHelper.formatMemSize(totalMemKb)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "共 ${appProcesses.size} 个进程",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                                if (totalMemFormatted.isNotEmpty()) {
                                    Text(
                                        text = "总物理内存：$totalMemFormatted",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.primary
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                appProcesses.forEachIndexed { index, proc ->
                                    if (index > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(0.6.dp)
                                                .background(colorScheme.outline.copy(alpha = 0.2f))
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // 头部：主/子进程标识 + 进程名 + 冻结/等待状态标签
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val isMain = proc.processName == app.packageName
                                            Row(
                                                modifier = Modifier.weight(1f, fill = false),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isMain) "● 主进程 " else "○ 子进程 ",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = if (isMain) colorScheme.primary else colorScheme.onSurfaceVariantSummary
                                                )
                                                Text(
                                                    text = proc.processName,
                                                    fontWeight = FontWeight(600),
                                                    fontSize = 13.5.sp,
                                                    color = colorScheme.onSurface,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.basicMarquee()
                                                )
                                            }

                                            Spacer(Modifier.size(8.dp))

                                            // 冻结状态仅取进程快照，Greeze UID 记账不作为内核事实。
                                            val modeType = proc.freezerType
                                            val modeColor = FreezeModeLabels.colorHex(modeType)
                                            when (proc.freezeState) {
                                                RootHelper.FreezeState.FROZEN -> {
                                                    StatusTagMiuix(
                                                        label = if (modeColor != null) FreezeModeLabels.processLabel(modeType) else "已冻结（方式未知）",
                                                        backgroundColor = if (modeColor != null) Color(modeColor) else colorScheme.primary,
                                                        contentColor = Color.White
                                                    )
                                                }
                                                RootHelper.FreezeState.RUNNING -> {
                                                    StatusTagMiuix(
                                                        label = "未冻结",
                                                        backgroundColor = colorScheme.secondaryContainer,
                                                        contentColor = colorScheme.onSecondaryContainer
                                                    )
                                                }
                                                RootHelper.FreezeState.UNKNOWN -> {
                                                    StatusTagMiuix(
                                                        label = "冻结状态未知",
                                                        backgroundColor = colorScheme.secondaryContainer,
                                                        contentColor = colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }

                                        // 核心指标行 1：实际物理内存与 PID
                                        val rssText = RootHelper.formatMemSize(proc.rssKb).ifEmpty { "0 KB" }
                                        Text(
                                            text = "物理内存: $rssText · PID: ${proc.pid}",
                                            fontSize = 12.sp,
                                            color = colorScheme.onSurfaceVariantSummary
                                        )

                                        // 核心指标行 2：运行时长与内核等待通道
                                        val uptimeText = proc.uptime.ifEmpty { "存活中" }
                                        Text(
                                            text = "运行时长: $uptimeText · 内核通道: ${proc.wchan}",
                                            fontSize = 12.sp,
                                            color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.85f)
                                        )

                                        // 挂载服务展示
                                        if (proc.services.isNotEmpty()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "挂载服务 (${proc.services.size} 个):",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colorScheme.primary
                                                )
                                                proc.services.forEach { svc ->
                                                    Text(
                                                        text = "└─ $svc",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = colorScheme.onSurfaceVariantSummary,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.basicMarquee()
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = "挂载服务: 无活跃服务",
                                                fontSize = 11.sp,
                                                color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.65f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. 冻结豁免与消息策略组
                    SmallTitle(text = "冻结豁免与保活策略")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
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
                            title = "加入 XMillet 免冻结白名单",
                            summary = if (isDualApp) {
                                "此双开实例加入模块免冻结名单，并关闭冲突策略；不修改系统包名级省电策略"
                            } else {
                                "永不冻结此应用（开启后自动关闭消息应用与强制冻结，并同步设为系统省电策略无限制）"
                            },
                            checked = inWhite,
                            onCheckedChange = { checked ->
                                inWhite = checked
                                if (checked) {
                                    inMessage = false
                                    inForceFreeze = false
                                    inBlockThaw = false
                                    inForbidSync = false
                                    inIgnoreNetworkWake = false
                                    inNotFreezeFgs = false
                                    updateMemberships(
                                        PrefsKeys.WHITE_APP_SET to true,
                                        PrefsKeys.MESSAGE_APP_SET to false,
                                        PrefsKeys.FORCE_FREEZE_SET to false,
                                        PrefsKeys.BLOCK_THAW_SET to false,
                                        PrefsKeys.FORBID_SYNC_BINDER_SET to false,
                                        PrefsKeys.IGNORE_NETWORK_WAKE_SET to false,
                                        PrefsKeys.NOT_FREEZE_WHEN_HAS_FGS_SET to false
                                    )

                                    if (!isDualApp) {
                                        // 主用户保留系统省电策略同步动作。
                                        coroutineScope.launch {
                                            val ok = SystemBatteryOptimizationHelper.setAppIgnoringBatteryOptimization(context, app.packageName)
                                            refreshSystemPolicy()
                                            if (!ok) {
                                                inWhite = false
                                                updateMembership(PrefsKeys.WHITE_APP_SET, false)
                                                Toast.makeText(
                                                    context,
                                                    "系统省电策略写入失败：需要模块在线或 Root 授权",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                } else {
                                    updateMembership(PrefsKeys.WHITE_APP_SET, false)
                                    if (!isDualApp) {
                                        coroutineScope.launch {
                                            val ok = SystemBatteryOptimizationHelper.restoreAppBatteryOptimizationToDefault(context, app.packageName)
                                            refreshSystemPolicy()
                                            if (!ok) {
                                                inWhite = true
                                                updateMembership(PrefsKeys.WHITE_APP_SET, true)
                                                Toast.makeText(
                                                    context,
                                                    "恢复系统省电策略失败：需要模块在线或 Root 授权",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "设为消息应用",
                            summary = "允许收到网络推送包后短暂解冻处理消息（与免冻结白名单、忽略网络唤醒互斥）",
                            checked = inMessage,
                            onCheckedChange = { checked ->
                                inMessage = checked
                                if (checked) {
                                    val changes = mutableListOf(PrefsKeys.MESSAGE_APP_SET to true)
                                    if (inWhite) {
                                        inWhite = false
                                        changes.add(PrefsKeys.WHITE_APP_SET to false)
                                    }
                                    if (inIgnoreNetworkWake) {
                                        inIgnoreNetworkWake = false
                                        changes.add(PrefsKeys.IGNORE_NETWORK_WAKE_SET to false)
                                    }
                                    updateMemberships(*changes.toTypedArray())
                                } else {
                                    updateMembership(PrefsKeys.MESSAGE_APP_SET, false)
                                }
                            }
                        )
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.PlayCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "存在前台服务时不冻结",
                            summary = "当 Aurogon 检测到活跃 ForegroundService 时阻止冻结（与免冻结白名单、强制冻结互斥）",
                            checked = inNotFreezeFgs,
                            onCheckedChange = { checked ->
                                inNotFreezeFgs = checked
                                if (checked) {
                                    val changes = mutableListOf(PrefsKeys.NOT_FREEZE_WHEN_HAS_FGS_SET to true)
                                    if (inWhite) {
                                        inWhite = false
                                        changes.add(PrefsKeys.WHITE_APP_SET to false)
                                    }
                                    if (inForceFreeze) {
                                        inForceFreeze = false
                                        inBlockThaw = false
                                        inForbidSync = false
                                        inIgnoreNetworkWake = false
                                        changes.add(PrefsKeys.FORCE_FREEZE_SET to false)
                                        changes.add(PrefsKeys.BLOCK_THAW_SET to false)
                                        changes.add(PrefsKeys.FORBID_SYNC_BINDER_SET to false)
                                        changes.add(PrefsKeys.IGNORE_NETWORK_WAKE_SET to false)
                                    }
                                    updateMemberships(*changes.toTypedArray())
                                } else {
                                    updateMembership(PrefsKeys.NOT_FREEZE_WHEN_HAS_FGS_SET, false)
                                }
                            }
                        )
                    }

                    // 5. 强力冻结与解冻压制组（带平滑折叠展开动画）
                    SmallTitle(text = "强力冻结与唤醒压制")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.AcUnit,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "强制冻结",
                            summary = "向官方链路请求冻结并限制已适配的后台豁免（与免冻结白名单、前台服务不冻结互斥）",
                            checked = inForceFreeze,
                            onCheckedChange = { checked ->
                                inForceFreeze = checked
                                if (checked) {
                                    val changes = mutableListOf(PrefsKeys.FORCE_FREEZE_SET to true)
                                    if (inWhite) {
                                        inWhite = false
                                        changes.add(PrefsKeys.WHITE_APP_SET to false)
                                    }
                                    if (inNotFreezeFgs) {
                                        inNotFreezeFgs = false
                                        changes.add(PrefsKeys.NOT_FREEZE_WHEN_HAS_FGS_SET to false)
                                    }
                                    updateMemberships(*changes.toTypedArray())
                                } else {
                                    inBlockThaw = false
                                    inForbidSync = false
                                    inIgnoreNetworkWake = false
                                    updateMemberships(
                                        PrefsKeys.FORCE_FREEZE_SET to false,
                                        PrefsKeys.BLOCK_THAW_SET to false,
                                        PrefsKeys.FORBID_SYNC_BINDER_SET to false,
                                        PrefsKeys.IGNORE_NETWORK_WAKE_SET to false
                                    )
                                }
                            }
                        )
                        AnimatedVisibility(
                            visible = inForceFreeze,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                SwitchPreference(
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Rounded.Block,
                                            contentDescription = null,
                                            modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    title = "禁止后台解冻",
                                    summary = "仅放行 Activity 启动与切换前台解冻，强力压制其他后台唤醒",
                                    checked = inBlockThaw,
                                    onCheckedChange = { checked ->
                                        inBlockThaw = checked
                                        updateMembership(PrefsKeys.BLOCK_THAW_SET, checked)
                                    }
                                )
                                SwitchPreference(
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Rounded.SyncDisabled,
                                            contentDescription = null,
                                            modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    title = "阻止同步 Binder 唤醒",
                                    summary = "拦截 Sync Binder / Check Binder 与 MilletHandler what=13 唤醒",
                                    checked = inForbidSync,
                                    onCheckedChange = { checked ->
                                        inForbidSync = checked
                                        updateMembership(PrefsKeys.FORBID_SYNC_BINDER_SET, checked)
                                    }
                                )
                                SwitchPreference(
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Rounded.SignalWifiOff,
                                            contentDescription = null,
                                            modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    title = "忽略网络唤醒",
                                    summary = "不再因推送、网络变化唤醒该应用，冻结会一直保持到手动打开（与设为消息应用互斥；代价是消息延迟送达）",
                                    checked = inIgnoreNetworkWake,
                                    onCheckedChange = { checked ->
                                        inIgnoreNetworkWake = checked
                                        if (checked && inMessage) {
                                            inMessage = false
                                            updateMemberships(
                                                PrefsKeys.IGNORE_NETWORK_WAKE_SET to true,
                                                PrefsKeys.MESSAGE_APP_SET to false
                                            )
                                        } else {
                                            updateMembership(PrefsKeys.IGNORE_NETWORK_WAKE_SET, checked)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // 6. 系统省电策略（只读）
                    //    真值来自 Settings.System.MILLET_NO_RESTRICT_APP，进页面自动读取，
                    //    没有「绑定 / 初始化」按钮；要改就去系统设置里改。
                    SmallTitle(text = "系统省电策略")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        ArrowPreference(
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
                            title = "当前策略",
                            summary = if (isDualApp) {
                                "$systemPolicyLabel；系统策略按包名共享，不能为双开独立设置；模块名单可按实例独立设置"
                            } else {
                                systemPolicyLabel
                            },
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        )
                    }

                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding() +
                                24.dp
                        )
                    )
                }
            }
        }
    }
}

/** 把系统省电策略翻译成一句话，供只读行展示。 */
private fun readSystemPolicyLabel(context: Context, packageName: String, noRestrict: Boolean): String = when {
    noRestrict -> "无限制（系统包名级记录）"
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(packageName) == true -> "已忽略电池优化（AOSP 白名单）"
    else -> "未记录为无限制或忽略电池优化"
}

/**
 * 进程扫描的启动延迟。
 *
 * 取 460ms：AnimatedContent 的进场动画为 360ms，加上 60ms 的起步延迟（见 MainActivity:514-517），
 * 留出约 40ms 余量，确保扫描落点稳定在动画结束之后。
 */
private const val PROCESS_SCAN_DELAY_MS = 460L

/** 按包名与 UID 缓存最近一次成功快照，不做持久化。 */
private object AppProcessSnapshotCache {
    private const val MAX_ENTRIES = 8

    private val entries =
        object : LinkedHashMap<String, List<RootHelper.ProcessRawEntry>>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, List<RootHelper.ProcessRawEntry>>?
            ): Boolean = size > MAX_ENTRIES
        }

    @Synchronized
    fun get(cacheKey: String): List<RootHelper.ProcessRawEntry>? = entries[cacheKey]

    @Synchronized
    fun put(cacheKey: String, processes: List<RootHelper.ProcessRawEntry>) {
        entries[cacheKey] = processes
    }
}
