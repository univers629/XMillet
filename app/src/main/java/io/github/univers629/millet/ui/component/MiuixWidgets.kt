package io.github.univers629.millet.ui.component
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import io.github.univers629.millet.ui.util.PopupBackdropHolder
import top.yukonga.miuix.kmp.glass.GlassDialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univers629.millet.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowDialog

enum class WarningLevel {
    Error,
    Notice
}

@Composable
fun StatusTagMiuix(
    label: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = colorScheme.primary.copy(alpha = 0.85f),
    contentColor: Color = colorScheme.onPrimary,
    fontSize: androidx.compose.ui.unit.TextUnit = 8.sp,
    paddingValues: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
    // 非 null 时画描边而不填充实底，用于把「次要/依附性」标签和「强调」标签区分开
    borderColor: Color? = null
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .background(color = backgroundColor, shape = shape)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, shape)
                else Modifier
            )
    ) {
        Text(
            modifier = Modifier.padding(paddingValues),
            text = label,
            color = contentColor,
            fontSize = fontSize,
            fontWeight = FontWeight(750),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun WarningCard(
    message: String,
    modifier: Modifier = Modifier,
    level: WarningLevel = WarningLevel.Error,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val isDark = isInDarkTheme()
    val containerColor = when {
        isDynamicColor -> when (level) {
            WarningLevel.Error -> colorScheme.errorContainer
            WarningLevel.Notice -> colorScheme.tertiaryContainer
        }
        isDark -> when (level) {
            WarningLevel.Error -> Color(0xFF310808)
            WarningLevel.Notice -> Color(0xFF3E2F1B)
        }
        else -> when (level) {
            WarningLevel.Error -> Color(0xFFF8E2E2)
            WarningLevel.Notice -> Color(0xFFFFF0DB)
        }
    }
    val contentColor = when {
        isDynamicColor -> when (level) {
            WarningLevel.Error -> colorScheme.onErrorContainer
            WarningLevel.Notice -> colorScheme.onTertiaryContainer
        }
        else -> when (level) {
            WarningLevel.Error -> Color(0xFFF72727)
            WarningLevel.Notice -> Color(0xFFF5A623)
        }
    }

    Card(
        modifier = modifier,
        onClick = { onClick?.invoke() },
        colors = CardDefaults.defaultColors(
            color = containerColor,
            contentColor = contentColor,
        ),
        showIndication = onClick != null,
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            action?.invoke()
        }
    }
}

@Composable
fun DropdownItem(
    modifier: Modifier = Modifier,
    text: String,
    optionSize: Int,
    index: Int,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    onSelectedIndexChange: (Int) -> Unit
) {
    // OS4 玻璃菜单项：按压即时高亮、松手渐隐，与 GlassPopup 动效一致
    GlassPopupItem(
        text = text,
        modifier = modifier,
        onClick = { onSelectedIndexChange(index) },
    )
}

object ListPopupDefaults {
    val MenuPositionProvider = object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): IntOffset {
            val offsetX = anchorBounds.right - popupContentSize.width - popupMargin.right
            val offsetY = anchorBounds.bottom + popupMargin.top
            return IntOffset(
                x = offsetX.coerceIn(
                    windowBounds.left,
                    (windowBounds.right - popupContentSize.width - popupMargin.right).coerceAtLeast(windowBounds.left),
                ),
                y = offsetY.coerceIn(
                    (windowBounds.top + popupMargin.top).coerceAtMost(windowBounds.bottom - popupContentSize.height - popupMargin.bottom),
                    windowBounds.bottom - popupContentSize.height - popupMargin.bottom,
                ),
            )
        }

        override fun getMargins(): PaddingValues = PaddingValues(start = 20.dp)
    }
}

@Composable
fun MiuixConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String? = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(backdrop = PopupBackdropHolder.current, 
        show = show,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
        title = title,
        onDismissRequest = onDismiss,
        content = {
            DialogBackHandler { onDismiss() }
            val dismissState = LocalDismissState.current
            Text(
                text = message,
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            ) {
                if (dismissText != null) {
                    TextButton(
                        text = dismissText,
                        onClick = {
                            onDismiss()
                            dismissState?.invoke()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(16.dp))
                }
                TextButton(
                    text = confirmText,
                    onClick = {
                        onConfirm()
                        dismissState?.invoke()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    )
}
