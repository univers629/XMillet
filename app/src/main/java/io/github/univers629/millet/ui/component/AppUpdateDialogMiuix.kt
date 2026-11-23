package io.github.univers629.millet.ui.component
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import io.github.univers629.millet.ui.util.PopupBackdropHolder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univers629.millet.core.AppReleaseInfo
import io.github.univers629.millet.core.AppUpdateVersionParser
import io.github.univers629.millet.ui.util.AppUpdateController
import io.github.univers629.millet.ui.util.AppUpdateDownloadState
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 新版本更新弹窗（Miuix `WindowDialog` 风格）：
 * 展示版本变化、发布元数据、可滚动更新日志、应用内校验下载进度与浏览器发布页入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUpdateDialogMiuix(
    show: Boolean,
    currentVersionName: String,
    release: AppReleaseInfo,
    downloadState: AppUpdateDownloadState,
    onDismiss: () -> Unit,
    onIgnoreVersion: () -> Unit,
    onOpenReleasePage: () -> Unit,
    onPrimaryAction: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val downloading = downloadState as? AppUpdateDownloadState.Downloading
    val failed = downloadState as? AppUpdateDownloadState.Failed
    val formattedNotes = remember(release.body) {
        AppUpdateVersionParser.formatReleaseBody(release.body)
    }
    val publishedText = remember(release.publishedAtUtc) {
        AppUpdateController.formatPublishedTime(release.publishedAtUtc)
    }
    val sizeText = remember(release.apkAsset?.sizeBytes) {
        AppUpdateVersionParser.formatFileSize(release.apkAsset?.sizeBytes ?: 0L)
    }

    val primaryLabel = when {
        release.apkAsset == null -> "前往发布页"
        downloading != null -> {
            val pct = downloading.fraction?.let { (it * 100f).roundToInt() }
            if (pct != null) "下载中 $pct%" else "下载中…"
        }

        downloadState is AppUpdateDownloadState.Ready -> "立即安装"
        failed != null -> "重试下载"
        else -> "下载并安装"
    }

    GlassDialog(backdrop = PopupBackdropHolder.current, 
        show = show,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
        title = "发现新版本",
        onDismissRequest = onDismiss,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "v$currentVersionName → v${release.versionName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )

                Spacer(Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusTagMiuix(
                        label = release.tagName,
                        backgroundColor = colorScheme.primary.copy(alpha = 0.14f),
                        contentColor = colorScheme.primary,
                        fontSize = 10.sp,
                    )
                    if (sizeText.isNotEmpty()) {
                        StatusTagMiuix(
                            label = sizeText,
                            backgroundColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer,
                            fontSize = 10.sp,
                        )
                    }
                    if (publishedText.isNotEmpty()) {
                        StatusTagMiuix(
                            label = publishedText,
                            backgroundColor = Color.Transparent,
                            contentColor = colorScheme.onSurfaceVariantSummary,
                            borderColor = colorScheme.outline,
                            fontSize = 10.sp,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceContainerHigh)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text = formattedNotes.ifEmpty {
                            "点击下方按钮更新，或前往 GitHub 发布页查看完整变更说明。"
                        },
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }

                if (downloading != null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = downloading.fraction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    val readText = AppUpdateVersionParser.formatFileSize(downloading.bytesRead).ifEmpty { "0 KB" }
                    val totalText = AppUpdateVersionParser.formatFileSize(downloading.totalBytes)
                    val progressDetail = if (totalText.isNotEmpty()) {
                        "已下载 $readText / $totalText"
                    } else {
                        "已下载 $readText"
                    }
                    Text(
                        text = progressDetail,
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                } else if (failed != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = failed.message,
                        fontSize = 12.sp,
                        color = colorScheme.error,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    TextButton(
                        text = if (downloading != null) "取消" else "稍后再说",
                        onClick = {
                            if (downloading != null) onCancelDownload() else onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        text = primaryLabel,
                        enabled = downloading == null,
                        onClick = onPrimaryAction,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }

                if (downloading == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "忽略此版本",
                            fontSize = 12.5.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onIgnoreVersion)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                        Text(
                            text = "前往发布页",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onOpenReleasePage)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        },
    )
}
