package io.github.univers629.millet.ui.component
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import io.github.univers629.millet.ui.util.PopupBackdropHolder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univers629.millet.BuildConfig
import io.github.univers629.millet.core.RomCapabilities
import io.github.univers629.millet.core.RootHelper
import io.github.univers629.millet.core.VersionGate
import io.github.univers629.millet.core.XposedPrefsBridge
import io.github.univers629.millet.ipc.PowerKeeperBinderClient
import io.github.univers629.millet.ui.theme.isInDarkTheme
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private val DETAIL_LABEL_WIDTH = 62.dp
private val STREAM_CONTENT_INDENT = 26.dp
private const val WORD_JOINER = "\u2060"
private val TABULAR_STYLE = TextStyle(fontFeatureSettings = "tnum")

/**
 * 顶部设备与宿主环境指纹的结构化展示模型，同时支持导出为纯文本行供复制报告使用。
 */
private data class EnvFingerprintInfo(
    val appVersion: String,
    val powerKeeperVersion: String,
    val deviceModel: String,
    val romIncremental: String,
    val romDescription: String,
    val kernelVersion: String,
) {
    fun toPlainLines(): List<String> = listOf(
        "XMillet $appVersion | $deviceModel | $romIncremental",
        "$romDescription | PK $powerKeeperVersion",
        "Kernel: $kernelVersion",
    )
}

/**
 * 检测明细区单条键值对行，用于以固定标签列对齐展示，避免折行错位。
 */
internal data class ConsoleDetailRow(
    val label: String,
    val value: String,
    val isAlert: Boolean = false,
    val isSubRow: Boolean = false,
)

/**
 * 流式检测逐条输出的一条结构化记录。
 */
private data class HookCheckStreamItem(
    val id: String,
    val timestamp: String,
    val title: String,
    val summary: String,
    val level: HookDiagnosticLevel,
    val detailRows: List<ConsoleDetailRow> = emptyList(),
    val suggestion: String? = null,
)

/**
 * 首页「运行环境与服务检测」底部面板：打开时逐条流式输出检测结果，
 * 顶部结构化展示设备/ROM/PowerKeeper/内核指纹，每项以对齐的键值表格和提示卡呈现点位明细，
 * 便于未实机测试的 ROM 仅靠截图或一键复制报告即可定位缺失点位。
 */
@Composable
fun HookCheckSheetMiuix(
    show: Boolean,
    runtimeStatus: PowerKeeperBinderClient.RuntimeStatusSnapshot,
    kernelFreezer: RootHelper.KernelFreezer = RootHelper.KernelFreezer.UNKNOWN,
    frameworkState: XposedPrefsBridge.XposedFrameworkState = XposedPrefsBridge.XposedFrameworkState(),
    onRequestScope: (() -> Unit)? = null,
    onHotReloadStale: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    kernelEvidenceConfirmed: Boolean = false,
    kernelEvidenceSource: String = "UNCONFIRMED",
    milletBackendState: String = "UNCONFIRMED",
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val bodyMaxHeight = (screenHeight * 0.66f).coerceIn(280.dp, 580.dp)

    val envFingerprint = remember(context) { collectEnvFingerprintInfo(context) }
    val envFingerprintLines = remember(envFingerprint) { envFingerprint.toPlainLines() }

    val diagnostics = remember(
        runtimeStatus,
        kernelFreezer,
        frameworkState,
        kernelEvidenceConfirmed,
        kernelEvidenceSource,
        milletBackendState,
    ) {
        generateHookDiagnostics(
            runtimeStatus = runtimeStatus,
            kernelFreezer = kernelFreezer,
            frameworkState = frameworkState,
            kernelEvidenceConfirmed = kernelEvidenceConfirmed,
            kernelEvidenceSource = kernelEvidenceSource,
            milletBackendState = milletBackendState,
        )
    }

    // 只有辅助信息可从“必要项”里排除；官方策略链路与冻结证据始终参与总览。
    val requiredDiagnostics = diagnostics.filterNot { it.optional }
    val totalCount = requiredDiagnostics.size
    val passedCount = requiredDiagnostics.count { it.level == HookDiagnosticLevel.PASSED }
    val overallLevel = when {
        requiredDiagnostics.all { it.level == HookDiagnosticLevel.PASSED } -> HookDiagnosticLevel.PASSED
        requiredDiagnostics.any { it.level == HookDiagnosticLevel.FAILED } -> HookDiagnosticLevel.FAILED
        else -> HookDiagnosticLevel.WARNING
    }

    var runToken by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    val revealTimestamps = remember { mutableStateListOf<String>() }
    val clock = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }

    LaunchedEffect(show, runToken) {
        if (!show) {
            running = false
            return@LaunchedEffect
        }
        revealed = 0
        revealTimestamps.clear()
        running = true
        delay(140)
        val total = diagnostics.size
        for (index in 0 until total) {
            revealTimestamps += LocalTime.now().format(clock)
            revealed = index + 1
            if (index != total - 1) delay(75)
        }
        running = false
    }

    val streamItems = remember(diagnostics, revealed, revealTimestamps.size) {
        diagnostics.take(revealed.coerceAtMost(diagnostics.size)).mapIndexed { index, item ->
            val stamp = revealTimestamps.getOrElse(index) { LocalTime.now().format(clock) }
            HookCheckStreamItem(
                id = item.id,
                timestamp = stamp,
                title = item.title,
                summary = item.summaryText,
                level = item.level,
                detailRows = parseConsoleDetailRows(item.consoleLines),
                suggestion = item.suggestion.takeIf { item.level != HookDiagnosticLevel.PASSED },
            )
        }
    }

    val outerScroll = rememberScrollState()
    LaunchedEffect(streamItems.size, running) {
        if (running && streamItems.isNotEmpty()) {
            outerScroll.animateScrollTo(outerScroll.maxValue)
        }
    }

    // OverlayBottomSheet 的关闭动画由 DismissState 驱动，这里提前取出来供各按钮复用。
    val dismissState = LocalDismissState.current

    GlassBottomSheet(backdrop = PopupBackdropHolder.current, 
        show = show,
        title = "运行环境与服务检测",
        insideMargin = DpSize(16.dp, 8.dp),
        startAction = {
            IconButton(
                onClick = {
                    val report = buildHookCheckReportText(
                        envLines = envFingerprintLines,
                        diagnostics = diagnostics,
                        rawCoverageWire = runtimeStatus.hookCoverage.encode(),
                    )
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("XMillet 诊断报告", report))
                    Toast.makeText(context, "已复制完整诊断报告", Toast.LENGTH_SHORT).show()
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.Copy,
                    contentDescription = "复制检测结果",
                    tint = colorScheme.onBackground,
                )
            }
        },
        endAction = {
            IconButton(
                onClick = {
                    onDismiss()
                    dismissState?.invoke()
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = "关闭",
                    tint = colorScheme.onBackground,
                )
            }
        },
        onDismissRequest = {
            onDismiss()
            dismissState?.invoke()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = bodyMaxHeight)
                    .verticalScroll(outerScroll),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HookCheckStreamHeader(
                    running = running,
                    level = overallLevel,
                    checkedCount = revealed,
                    streamTotal = diagnostics.size,
                    passedCount = passedCount,
                    totalCount = totalCount,
                )

                EnvFingerprintCard(info = envFingerprint)

                HookCheckStreamList(
                    runToken = runToken,
                    items = streamItems,
                    running = running,
                )

                val showRequestScopeAction = onRequestScope != null &&
                    frameworkState.isServiceConnected &&
                    frameworkState.missingScopes.isNotEmpty()
                val showHotReloadAction = onHotReloadStale != null &&
                    frameworkState.isServiceConnected &&
                    frameworkState.staleTargets.isNotEmpty()
                if (showRequestScopeAction || showHotReloadAction) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showRequestScopeAction) {
                            TextButton(
                                text = "授权作用域",
                                onClick = onRequestScope,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (showHotReloadAction) {
                            TextButton(
                                text = "热重载 (${frameworkState.staleTargets.size})",
                                onClick = onHotReloadStale,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (running) {
                    TextButton(
                        text = "正在检测…",
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    TextButton(
                        text = "重新检测",
                        onClick = {
                            runToken++
                            onRefresh()
                        },
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(Modifier.width(12.dp))

                    TextButton(
                        text = "完成",
                        onClick = {
                            onDismiss()
                            dismissState?.invoke()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

/**
 * 流式检测的顶部状态卡片：检测中展示平滑进度条与已检计数，结束后切换为结论卡片。
 */
@Composable
private fun HookCheckStreamHeader(
    running: Boolean,
    level: HookDiagnosticLevel,
    checkedCount: Int,
    streamTotal: Int,
    passedCount: Int,
    totalCount: Int,
) {
    if (!running) {
        OverallStatusHeader(level = level, passedCount = passedCount, totalCount = totalCount)
        return
    }
    val targetProgress = if (streamTotal > 0) checkedCount.toFloat() / streamTotal.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "hook_check_progress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "正在检测系统环境",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
            Text(
                text = "$checkedCount / $streamTotal",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary,
                style = TABULAR_STYLE,
            )
        }
        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier.fillMaxWidth(),
            height = 4.dp,
        )
        Text(
            text = "正在按序核对框架作用域、宿主能力与 Hook 覆盖账本…",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}

/**
 * 设备与宿主环境指纹卡片：固定标签列对齐展示，便于单屏截图快速核对机型与系统版本。
 */
@Composable
private fun EnvFingerprintCard(info: EnvFingerprintInfo) {
    val rows = remember(info) {
        listOf(
            ConsoleDetailRow("应用", "XMillet ${info.appVersion} · PK ${info.powerKeeperVersion}"),
            ConsoleDetailRow("设备", "${info.deviceModel} · ${info.romIncremental}"),
            ConsoleDetailRow("系统", info.romDescription),
            ConsoleDetailRow("内核", info.kernelVersion),
        )
    }
    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rows.forEach { row ->
                DetailKeyValueRow(
                    row = row,
                    alertColor = colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 流式检测结果列表：逐项以结构化行、等宽对齐键值明细块与独立建议卡展示，
 * 全程避免使用空格缩进，确保长文本折行后仍严格左对齐。
 */
@Composable
private fun HookCheckStreamList(
    runToken: Int,
    items: List<HookCheckStreamItem>,
    running: Boolean,
) {
    val dark = isInDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        SelectionContainer {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (items.isEmpty()) {
                    Text(
                        text = if (running) "正在等待首项检测结果…" else "点击「重新检测」开始",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = colorScheme.dividerLine.copy(alpha = 0.55f),
                            )
                        }
                        key(runToken, item.id) {
                            HookCheckStreamItemBlock(item = item, dark = dark)
                        }
                    }
                    if (running) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                            color = colorScheme.dividerLine.copy(alpha = 0.4f),
                        )
                        Text(
                            text = "正在继续核对下一项…",
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = STREAM_CONTENT_INDENT),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HookCheckStreamItemBlock(
    item: HookCheckStreamItem,
    dark: Boolean,
) {
    val density = LocalDensity.current
    val initialOffsetPx = with(density) { 6.dp.toPx() }
    val revealProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        revealProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        )
    }

    val iconTint = iconColorForLevel(item.level, dark)
    val summaryTint = textColorForLevel(item.level, dark)
    val detailBoxBg = colorScheme.surface.copy(alpha = if (dark) 0.52f else 0.72f)
    val detailBoxBorder = colorScheme.dividerLine.copy(alpha = if (dark) 0.4f else 0.55f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = revealProgress.value
                translationY = (1f - revealProgress.value) * initialOffsetPx
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 标题行：左侧固定状态图标，中间标题，右侧等宽数字时间戳
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = iconForLevel(item.level),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.title,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.timestamp,
                fontSize = 10.5.sp,
                lineHeight = 19.sp,
                color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
                style = TABULAR_STYLE,
            )
        }

        // 结论摘要：与标题左缘严格对齐
        Text(
            text = item.summary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            color = summaryTint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = STREAM_CONTENT_INDENT),
        )

        // 技术明细子块：独立圆角容器 + 固定标签列对齐
        if (item.detailRows.isNotEmpty()) {
            val detailShape = RoundedCornerShape(10.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = STREAM_CONTENT_INDENT, top = 2.dp)
                    .clip(detailShape)
                    .background(detailBoxBg)
                    .border(0.5.dp, detailBoxBorder, detailShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.5.dp),
            ) {
                item.detailRows.forEach { row ->
                    DetailKeyValueRow(
                        row = row,
                        alertColor = summaryTint,
                    )
                }
            }
        }

        // 排查/操作建议卡：独立柔和底色 + 悬挂缩进对齐
        val hint = item.suggestion
        if (hint != null) {
            val hintShape = RoundedCornerShape(10.dp)
            val hintBg = suggestionBgForLevel(item.level, dark)
            val hintBorder = iconTint.copy(alpha = if (dark) 0.28f else 0.22f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = STREAM_CONTENT_INDENT, top = 2.dp)
                    .clip(hintShape)
                    .background(hintBg)
                    .border(0.5.dp, hintBorder, hintShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "建议",
                    fontSize = 10.5.sp,
                    lineHeight = 16.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = summaryTint,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = hint,
                    fontSize = 11.5.sp,
                    lineHeight = 16.5.sp,
                    color = summaryTint,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DetailKeyValueRow(
    row: ConsoleDetailRow,
    alertColor: Color,
) {
    val labelText = if (row.isSubRow) "· ${row.label}" else row.label
    val labelColor = when {
        row.isAlert -> alertColor
        row.isSubRow -> colorScheme.onSurfaceVariantSummary.copy(alpha = 0.8f)
        else -> colorScheme.onSurfaceVariantSummary
    }
    val valueColor = if (row.isAlert) {
        alertColor
    } else {
        colorScheme.onSurface.copy(alpha = 0.86f)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(DETAIL_LABEL_WIDTH)
                .padding(start = if (row.isSubRow) 2.dp else 0.dp),
        ) {
            Text(
                text = labelText,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                fontWeight = if (row.isAlert) FontWeight.SemiBold else FontWeight.Medium,
                color = labelColor,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = row.value,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = if (row.isAlert) FontWeight.Medium else FontWeight.Normal,
            color = valueColor,
            style = TABULAR_STYLE,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 将 `HookDiagnosticEntry.consoleLines` 解析为结构化的对齐键值行，
 * 保留全部技术点位、能力位与缺口信息，同时优化逗号与键值分隔符的断行可读性。
 */
internal fun parseConsoleDetailRows(details: List<String>): List<ConsoleDetailRow> {
    val rows = ArrayList<ConsoleDetailRow>()
    for (rawLine in details) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue

        // 1. 两侧 Hook 安装点覆盖：系统侧 / 电量侧
        if (line.startsWith("系统侧:") || line.startsWith("电量侧:")) {
            val segments = splitPipeSegments(line)
            val head = segments.first()
            val colonIdx = head.indexOf(':')
            val sideLabel = head.substring(0, colonIdx).trim()
            val sideValue = head.substring(colonIdx + 1).trim()
            rows += ConsoleDetailRow(
                label = sideLabel,
                value = sideValue,
                isAlert = sideValue == "未上报",
            )
            for (seg in segments.drop(1)) {
                val eqIdx = seg.indexOf('=')
                if (eqIdx > 0) {
                    val key = seg.substring(0, eqIdx).trim()
                    val value = formatCommaList(seg.substring(eqIdx + 1))
                    rows += ConsoleDetailRow(
                        label = key,
                        value = value,
                        isAlert = key == "失败",
                        isSubRow = true,
                    )
                } else {
                    rows += ConsoleDetailRow(label = "说明", value = seg, isSubRow = true)
                }
            }
            continue
        }

        // 2. 缺口告警行（可能附带失败点位与宿主缺失点位）
        if (line.startsWith("缺口:")) {
            val segments = splitPipeSegments(line)
            val head = segments.first()
            rows += ConsoleDetailRow(
                label = "缺口",
                value = head.substringAfter(':').trim(),
                isAlert = true,
            )
            for (seg in segments.drop(1)) {
                val eqIdx = seg.indexOf('=')
                if (eqIdx > 0) {
                    val key = seg.substring(0, eqIdx).trim()
                    val rawVal = seg.substring(eqIdx + 1)
                    val formatted = if (key == "失败") {
                        formatMultiLineList(rawVal)
                    } else {
                        formatCommaList(rawVal)
                    }
                    rows += ConsoleDetailRow(
                        label = key,
                        value = formatted,
                        isAlert = true,
                        isSubRow = true,
                    )
                } else {
                    rows += ConsoleDetailRow(label = "明细", value = seg, isAlert = true, isSubRow = true)
                }
            }
            continue
        }

        // 3. 安装失败目标清单（每个目标单独占一行便于截图定位）
        if (line.startsWith("失败目标:")) {
            rows += ConsoleDetailRow(
                label = "失败目标",
                value = formatMultiLineList(line.substringAfter(':')),
                isAlert = true,
            )
            continue
        }

        // 4. LibXposed 目标进程快照（每进程占一行）
        if (line.startsWith("目标进程:")) {
            rows += ConsoleDetailRow(
                label = "进程",
                value = formatMultiLineList(line.substringAfter(':')),
            )
            continue
        }

        // 5. 常规管道分隔技术明细
        val segments = splitPipeSegments(line)
        if (segments.size >= 4 && segments.all { '=' in it && ':' !in it }) {
            rows += ConsoleDetailRow(
                label = "状态",
                value = segments.joinToString(" · ") { protectEqualsPair(it) },
            )
            continue
        }

        segments.forEachIndexed { idx, seg ->
            val colonIdx = seg.indexOf(':')
            val eqIdx = seg.indexOf('=')
            when {
                colonIdx > 0 && (eqIdx < 0 || colonIdx < eqIdx) -> {
                    val key = seg.substring(0, colonIdx).trim()
                    val rawVal = seg.substring(colonIdx + 1).trim()
                    val value = if (',' !in rawVal && '=' in rawVal && ' ' in rawVal) {
                        formatEqualsTokenGroup(rawVal)
                    } else {
                        rawVal
                    }
                    rows += ConsoleDetailRow(label = key, value = value)
                }
                eqIdx > 0 -> {
                    val key = seg.substring(0, eqIdx).trim()
                    val rawVal = seg.substring(eqIdx + 1).trim()
                    val value = when (key) {
                        "作用域" -> formatCommaList(rawVal)
                        "云名单" -> rawVal.replace("/", " / ")
                        else -> rawVal
                    }
                    rows += ConsoleDetailRow(label = key, value = value)
                }
                else -> {
                    val label = if (idx == 0 && seg.contains("API ")) "框架" else "信息"
                    rows += ConsoleDetailRow(label = label, value = seg)
                }
            }
        }
    }
    return rows
}

private fun splitPipeSegments(line: String): List<String> =
    line.split(" | ").map { it.trim() }.filter { it.isNotEmpty() }

private fun formatCommaList(raw: String): String =
    raw.trim()
        .removePrefix("[")
        .removeSuffix("]")
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ")

private fun formatMultiLineList(raw: String): String =
    raw.trim()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

private fun protectEqualsPair(token: String): String =
    token.trim().replace("=", "$WORD_JOINER=$WORD_JOINER")

private fun formatEqualsTokenGroup(raw: String): String =
    raw.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString("  ") { protectEqualsPair(it) }

private fun collectEnvFingerprintInfo(context: Context): EnvFingerprintInfo {
    val pkVersion = runCatching {
        val info = context.packageManager.getPackageInfo(RomCapabilities.POWERKEEPER_PACKAGE, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("unknown")
    val kernel = runCatching { System.getProperty("os.version") }.getOrNull().orEmpty().ifBlank { "unknown" }
    val model = Build.MODEL.orEmpty().ifBlank { "unknown" }
    val incremental = Build.VERSION.INCREMENTAL.orEmpty().ifBlank { "unknown" }
    return EnvFingerprintInfo(
        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        powerKeeperVersion = pkVersion,
        deviceModel = model,
        romIncremental = incremental,
        romDescription = VersionGate.describeRom(),
        kernelVersion = kernel,
    )
}

private fun iconForLevel(level: HookDiagnosticLevel) = when (level) {
    HookDiagnosticLevel.PASSED -> Icons.Rounded.CheckCircle
    HookDiagnosticLevel.WARNING -> Icons.Rounded.Warning
    HookDiagnosticLevel.FAILED -> Icons.Rounded.Cancel
}

private fun iconColorForLevel(level: HookDiagnosticLevel, dark: Boolean): Color = when (level) {
    HookDiagnosticLevel.PASSED -> if (dark) Color(0xFF34C759) else Color(0xFF24A148)
    HookDiagnosticLevel.WARNING -> if (dark) Color(0xFFFFA928) else Color(0xFFD97706)
    HookDiagnosticLevel.FAILED -> if (dark) Color(0xFFFF4D4F) else Color(0xFFDC2626)
}

private fun textColorForLevel(level: HookDiagnosticLevel, dark: Boolean): Color = when (level) {
    HookDiagnosticLevel.PASSED -> if (dark) Color(0xFF5AD87A) else Color(0xFF1B7A3B)
    HookDiagnosticLevel.WARNING -> if (dark) Color(0xFFFFB74D) else Color(0xFFB45309)
    HookDiagnosticLevel.FAILED -> if (dark) Color(0xFFFF6B6B) else Color(0xFFC62828)
}

private fun suggestionBgForLevel(level: HookDiagnosticLevel, dark: Boolean): Color = when (level) {
    HookDiagnosticLevel.PASSED -> if (dark) Color(0xFF1B3523) else Color(0xFFE8F7EC)
    HookDiagnosticLevel.WARNING -> if (dark) Color(0xFF3A2C19) else Color(0xFFFFF4E0)
    HookDiagnosticLevel.FAILED -> if (dark) Color(0xFF391919) else Color(0xFFFDECEC)
}

private fun glyphOf(level: HookDiagnosticLevel): String = when (level) {
    HookDiagnosticLevel.PASSED -> "✓"
    HookDiagnosticLevel.WARNING -> "!"
    HookDiagnosticLevel.FAILED -> "×"
}

/** 构造可供用户一键复制/反馈的完整文本诊断报告（含环境指纹、逐项明细与原始账本串）。 */
internal fun buildHookCheckReportText(
    envLines: List<String>,
    diagnostics: List<HookDiagnosticEntry>,
    rawCoverageWire: String = "",
    timestamp: LocalDateTime = LocalDateTime.now(),
): String = buildString {
    appendLine("XMillet 运行环境与服务检测")
    appendLine(timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
    if (envLines.isNotEmpty()) {
        envLines.forEach { appendLine(it) }
    }
    if (rawCoverageWire.isNotBlank()) {
        appendLine("Wire: $rawCoverageWire")
    }
    diagnostics.forEach { item ->
        appendLine()
        appendLine("${glyphOf(item.level)} ${item.title} · ${item.summaryText}")
        appendLine("目标：${item.targetClassOrMethod}")
        item.consoleLines.forEach { appendLine("  · $it") }
        appendLine(item.detailText)
        item.suggestion?.let { appendLine("建议：$it") }
    }
}
