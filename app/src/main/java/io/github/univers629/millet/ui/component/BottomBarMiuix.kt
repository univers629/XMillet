package io.github.univers629.millet.ui.component
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import io.github.univers629.millet.ui.theme.LocalEnableProgressiveBlur

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univers629.millet.ui.theme.LocalEnableBlur
import io.github.univers629.millet.ui.theme.LocalEnableFloatingBottomBar
import io.github.univers629.millet.ui.theme.LocalEnableFloatingBottomBarBlur
import io.github.univers629.millet.ui.util.BlurredBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.springAnimateToPage

class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
    private val animatePageChanges: Boolean = true,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return

        navJob?.cancel()

        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                if (animatePageChanges) {
                    pagerState.springAnimateToPage(targetIndex)
                } else {
                    pagerState.scrollToPage(targetIndex)
                }
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    animatePageChanges: Boolean = true,
): MainPagerState {
    return remember(pagerState, coroutineScope, animatePageChanges) {
        MainPagerState(pagerState, coroutineScope, animatePageChanges)
    }
}

enum class BottomBarDestination(
    val label: String,
    val icon: ImageVector,
) {
    Home("主页", MiuixIcons.Home),
    Processes("进程列表", XMilletIcons.ProcessBolt),
    TombStats("墓碑统计", TombStatsIcon),
    Apps("应用策略", XMilletIcons.AppPolicy),
    Settings("设置", MiuixIcons.Settings);

    companion object {
        /** 当前可见的底栏目的地（顺序即 Pager 页序）。页面下标一律经由此列表换算，禁止硬编码。 */
        fun visible(showTombStats: Boolean): List<BottomBarDestination> =
            if (showTombStats) entries.toList() else entries.filter { it != TombStats }
    }
}

/**
 * XMillet 底部导航栏（复刻 KernelSU BottomBarMiuix）：
 * - 悬浮模式：iOS 液态玻璃悬浮底栏 (FloatingBottomBar)，宽度随 Tab 数自适应；
 * - 经典模式：全宽 NavigationBar；
 * - 背景模糊为「渐进式」时，只在经典底栏自身范围内自下而上渐隐模糊，不向上溢出到内容区；
 *   悬浮底栏由液态玻璃自行采样，不再额外叠加底部模糊带。
 */
@Composable
fun BoxScope.BottomBarMiuix(
    mainState: MainPagerState,
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    destinations: List<BottomBarDestination> = BottomBarDestination.entries,
    modifier: Modifier = Modifier,
) {
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val progressive = LocalEnableProgressiveBlur.current && enableBlur && blurBackdrop != null

    val items = destinations.map { destination ->
        NavigationItem(
            label = destination.label,
            icon = destination.icon,
        )
    }
    // 5 个 Tab 时收窄：5×60 + 8 + 2×16 = 340dp，紧凑屏 (360dp) 也有余量且不挤压文字；4 个 Tab 保持原尺寸
    val dense = items.size >= 5
    val itemMinWidth = if (dense) 60.dp else 68.dp
    val sideMargin = if (dense) 16.dp else 28.dp
    val safeSelectedIndex = mainState.selectedPage.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    if (!enableFloatingBottomBar) {
        val surface = colorScheme.surface
        BlurredBar(
            backdrop = blurBackdrop,
            blurActive = enableBlur && !progressive,
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (progressive && blurBackdrop != null) {
                        Modifier.progressiveTextureBlur(
                            backdrop = blurBackdrop,
                            shape = RectangleShape,
                            blurRadius = 60f,
                            gradient = ProgressiveBlur.Bottom,
                            colors = BlurColors(
                                blendColors = listOf(BlendColorEntry(color = surface.copy(alpha = 0.3f))),
                            ),
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            NavigationBar(
                modifier = Modifier.fillMaxWidth(),
                color = if (enableBlur && blurBackdrop != null) Color.Transparent else colorScheme.surface,
            ) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        icon = item.icon,
                        label = item.label,
                        selected = safeSelectedIndex == index,
                        onClick = { mainState.animateToPage(index) }
                    )
                }
            }
        }
    } else {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            WindowInsets.captionBar.asPaddingValues().calculateBottomPadding() +
            24.dp

        FloatingBottomBar(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
                .padding(start = sideMargin, end = sideMargin, bottom = bottomPadding),
            selectedIndex = safeSelectedIndex,
            onSelected = { mainState.animateToPage(it) },
            backdrop = backdrop,
            tabsCount = items.size,
            isBlurEnabled = enableFloatingBottomBarBlur
        ) { activateTab ->
            items.forEachIndexed { index, item ->
                val destination = destinations[index]
                FloatingBottomBarItem(
                    selected = safeSelectedIndex == index,
                    onClick = { activateTab(index) },
                    modifier = Modifier.defaultMinSize(minWidth = itemMinWidth)
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = destination.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
