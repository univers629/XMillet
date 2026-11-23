package io.github.univers629.millet.ui.util
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.PopupLayout
import top.yukonga.miuix.kmp.glass.GlassPopup
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import kotlinx.coroutines.delay
import kotlin.math.abs

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.univers629.millet.ui.theme.LocalEnableProgressiveBlur
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 由 [BlurredBar] 下发给顶栏的背景采样源；为 null 时顶栏退回纯色渐变带。 */
val LocalBarBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Miuix Glass 顶栏（HyperOS 4 液态玻璃）。参数与旧 TopAppBar 保持一致，各页面直接平替：
 * - 渐进式模糊开启且有采样源：progressiveBlur = true，背景自顶部向下渐隐模糊；
 * - 否则沿用外层 BlurredBar 的整块毛玻璃（color 透明时）或纯色渐变带。
 */
@Composable
fun MilletTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    titleColor: Color = Color.Unspecified,
    expandable: Boolean = true,
    largeTitle: String = title,
    subtitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
) {
    val backdrop = LocalBarBackdrop.current
    val progressive = LocalEnableProgressiveBlur.current && backdrop != null
    val band: Brush = when {
        progressive -> GlassTopAppBarDefaults.bandBrush(MiuixTheme.colorScheme.surface)
        color == Color.Transparent -> SolidColor(Color.Transparent)
        color.isSpecified -> GlassTopAppBarDefaults.bandBrush(color)
        else -> GlassTopAppBarDefaults.bandBrush(MiuixTheme.colorScheme.surface)
    }
    GlassTopAppBar(
        title = title,
        modifier = modifier,
        expandable = expandable,
        progressiveBlur = progressive,
        backdrop = backdrop,
        largeTitle = largeTitle,
        subtitle = subtitle,
        scrollBehavior = scrollBehavior,
        bandBrush = band,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

/** 不可折叠的小标题版本（替代 SmallTopAppBar）。 */
@Composable
fun MilletSmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    titleColor: Color = Color.Unspecified,
    subtitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
) = MilletTopAppBar(
    title = title,
    modifier = modifier,
    color = color,
    titleColor = titleColor,
    expandable = false,
    subtitle = subtitle,
    navigationIcon = navigationIcon,
    actions = actions,
    scrollBehavior = scrollBehavior,
    defaultWindowInsetsPadding = defaultWindowInsetsPadding,
)

/** 最近一次点击的顶栏按钮边界（窗口坐标，已扣除外边距），供紧随其后的玻璃菜单锚定。 */
internal object BarButtonAnchor {
    var lastClicked: Rect = Rect.Zero
}

/** 顶栏玻璃按钮（替代 IconButton，保留旧参数签名）。点击时记录自身真实边界作为菜单锚点。 */
@Composable
fun BarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    backgroundColor: Color = Color.Unspecified,
    cornerRadius: Dp = 0.dp,
    minHeight: Dp = 0.dp,
    minWidth: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val bounds = remember { arrayOf(Rect.Zero) }
    GlassIconButton(
        onClick = {
            if (enabled) {
                BarButtonAnchor.lastClicked = bounds[0]
                onClick()
            }
        },
        // 接在调用方 padding 之后：得到按钮本体边界（不含 end 边距，也不受按压缩放影响）。
        modifier = modifier.onGloballyPositioned { bounds[0] = it.boundsInWindow() },
        content = content,
    )
}

/**
 * OS4 玻璃菜单：替代 OverlayListPopup，保留原调用签名（定位参数忽略）。
 * 锚点取触发按钮的真实边界，弹出与收纳都以该按钮为原点；每次打开时锁定一次，收起沿用同一锚点。
 * 必须紧跟在触发按钮之后调用：这里的 0 宽占位用于校验点击来源，并在无记录时兜底估算。
 */
@Composable
fun GlassOverlayListPopup(
    show: Boolean,
    popupPositionProvider: Any? = null,
    alignment: Any? = null,
    onDismissRequest: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val slot = remember { arrayOf(Rect.Zero) }
    Box(
        modifier = Modifier
            .width(0.dp)
            .height(40.dp)
            .onGloballyPositioned { slot[0] = it.boundsInWindow() }
    )
    val anchor = remember { arrayOf(Rect.Zero) }
    val wasShown = remember { booleanArrayOf(false) }
    if (show && !wasShown[0]) {
        anchor[0] = resolveMenuAnchor(
            clicked = BarButtonAnchor.lastClicked,
            slot = slot[0],
            fallbackSize = with(density) { 40.dp.toPx() },
            tolerance = with(density) { 64.dp.toPx() },
        )
    }
    wasShown[0] = show
    val mounted = remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            mounted.value = true
        } else if (mounted.value) {
            delay(450)
            mounted.value = false
        }
    }
    if (!mounted.value) return
    PopupLayout(
        visible = mounted,
        enableWindowDim = false,
        enableBackHandler = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
    ) {
        // 首帧以关闭态进入组合、下一帧再打开：animateFloatAsState 首次组合直接取目标值，
        // 若挂载时 show 已为 true，进度一开始就是 1，展开动画会被跳过（表现为闪现）。
        val entered = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered.value = true }
        Box(modifier = Modifier.fillMaxSize()) {
            GlassPopup(
                show = show && entered.value,
                onDismissRequest = { onDismissRequest?.invoke() },
                anchorBounds = anchor[0],
                backdrop = PopupBackdropHolder.current,
            ) {
                content()
            }
        }
    }
}

/** 点击记录与占位相邻（同一按钮）时用真实边界，否则按占位左侧估算一个按钮。 */
private fun resolveMenuAnchor(clicked: Rect, slot: Rect, fallbackSize: Float, tolerance: Float): Rect {
    if (clicked == Rect.Zero) return Rect(slot.left - fallbackSize, slot.top, slot.left, slot.bottom)
    if (slot == Rect.Zero) return clicked
    val adjacent = abs(clicked.right - slot.left) <= tolerance &&
        clicked.bottom > slot.top - tolerance && clicked.top < slot.bottom + tolerance
    return if (adjacent) clicked else Rect(slot.left - fallbackSize, slot.top, slot.left, slot.bottom)
}

/** 玻璃菜单内的列容器（替代 Miuix ListPopupColumn，避免其固有尺寸测量与玻璃面板冲突）。 */
@Composable
fun ListPopupColumn(content: @Composable () -> Unit) {
    Column { content() }
}

/** 带勾选态的玻璃菜单项（替代 DropdownImpl）。 */
@Composable
fun GlassMenuItem(
    text: String,
    isSelected: Boolean = false,
    optionSize: Int = 1,
    index: Int = 0,
    onSelectedIndexChange: (Int) -> Unit,
) {
    GlassPopupItem(text = text, selected = isSelected, onClick = { onSelectedIndexChange(index) })
}
