package io.github.univers629.millet.ui.util
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.PopupLayout
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import kotlinx.coroutines.delay

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

/** 顶栏按钮与紧随其后的菜单之间共享的 Miuix 锚点（首次组合时按调用顺序配对）。 */
internal class BarPopupAnchorEntry(val anchor: GlassPopupAnchor) {
    var icon: @Composable () -> Unit by mutableStateOf({})
}

internal object BarPopupAnchorRegistry {
    var last: BarPopupAnchorEntry? = null
}

/** 顶栏玻璃按钮（替代 IconButton，保留旧参数签名）。挂 Miuix [glassPopupAnchor]，菜单展开/收起由库负责。 */
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
    val anchor = rememberGlassPopupAnchor()
    val entry = remember(anchor) { BarPopupAnchorEntry(anchor) }
    entry.icon = content
    BarPopupAnchorRegistry.last = entry
    GlassIconButton(
        onClick = { if (enabled) onClick() },
        modifier = modifier.glassPopupAnchor(anchor, cornerRadius = GlassTopAppBarDefaults.ButtonSize / 2),
    ) {
        Box(modifier = Modifier.glassPopupAnchorContent(anchor)) { content() }
    }
}

/**
 * OS4 玻璃菜单：替代 OverlayListPopup，保留原调用签名（定位参数忽略）。
 * 使用 Miuix [GlassTransformPopup]：从触发按钮变形展开，收起时缩回按钮本身。
 * 必须紧跟在 [BarIconButton] 之后调用，以取得该按钮的锚点。
 */
@Composable
fun GlassOverlayListPopup(
    show: Boolean,
    popupPositionProvider: Any? = null,
    alignment: Any? = null,
    onDismissRequest: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val entry = remember { BarPopupAnchorRegistry.last ?: BarPopupAnchorEntry(GlassPopupAnchor()) }
    val mounted = remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            mounted.value = true
        } else if (mounted.value) {
            // 等库内收起弹簧完全结束再卸载，避免截断。
            delay(1200)
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
        // 库的 updateTransition 首次组合即取目标值；先以关闭态挂载一帧，展开才会走动画。
        val entered = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered.value = true }
        Box(modifier = Modifier.fillMaxSize()) {
            GlassTransformPopup(
                show = show && entered.value,
                onDismissRequest = { onDismissRequest?.invoke() },
                anchor = entry.anchor,
                backdrop = PopupBackdropHolder.current,
                anchorContent = { entry.icon() },
            ) {
                content()
            }
        }
    }
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
