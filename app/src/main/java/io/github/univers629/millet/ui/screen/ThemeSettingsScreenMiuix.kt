package io.github.univers629.millet.ui.screen
import androidx.compose.material.icons.rounded.Gradient
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import io.github.univers629.millet.ui.util.MilletTopAppBar
import io.github.univers629.millet.ui.util.MilletSmallTopAppBar
import io.github.univers629.millet.ui.util.BarIconButton

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.univers629.millet.core.PredictiveBackController
import io.github.univers629.millet.ui.theme.LocalEnableBlur
import io.github.univers629.millet.ui.theme.UiThemeState
import io.github.univers629.millet.ui.theme.keyColorOptions
import io.github.univers629.millet.ui.theme.supportsPredictiveBack
import io.github.univers629.millet.ui.util.BlurredBar
import io.github.univers629.millet.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeSettingsScreenMiuix(
    themeState: UiThemeState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val isDark = when (themeState.darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val paletteStyles = ThemePaletteStyle.entries
    val paletteLabels = listOf(
        "TonalSpot (标准柔和)",
        "Spritz (中性淡雅)",
        "Vibrant (鲜艳活力)",
        "Expressive (表现力)",
        "Rainbow (彩虹色盘)",
        "FruitSalad (缤纷果色)",
        "Monochrome (单色极简)",
        "Fidelity (高保真)",
        "Content (内容原色)"
    )

    val colorSpecs = ThemeColorSpec.entries
    val colorSpecLabels = listOf(
        "Material Spec 2021",
        "Material Spec 2025"
    )

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                MilletTopAppBar(
                    color = barColor,
                    title = "外观个性化",
                    scrollBehavior = scrollBehavior,
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
                    }
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
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding,
                overscrollEffect = null
            ) {
                item {
                    SmallTitle(
                        text = "底栏与材质",
                        modifier = Modifier.padding(top = 6.dp)
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
                                    imageVector = Icons.Rounded.SpaceDashboard,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "启用悬浮底栏",
                            summary = "使用胶囊式悬浮底栏（支持拖拽阻尼弹簧动画）",
                            checked = themeState.enableFloatingBottomBar,
                            onCheckedChange = { themeState.updateEnableFloatingBottomBar(it) }
                        )

                        GlassDropdownPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.BlurOn,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "顶栏与底栏背景模糊",
                            summary = "高斯模糊为整块毛玻璃；渐进式模糊为 HyperOS 4 风格边缘渐隐，两者互斥",
                            items = listOf("关闭", "高斯模糊", "渐进式模糊"),
                            selectedIndex = when {
                                !themeState.enableBlur -> 0
                                themeState.enableProgressiveBlur -> 2
                                else -> 1
                            },
                            onSelectedIndexChange = { idx ->
                                themeState.updateEnableBlur(idx != 0)
                                if (idx != 0) themeState.updateEnableProgressiveBlur(idx == 2)
                            },
                            backdrop = backdrop,
                        )

                        AnimatedVisibility(
                            visible = themeState.enableFloatingBottomBar,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            SwitchPreference(
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Rounded.WaterDrop,
                                        contentDescription = null,
                                        modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                title = "启用悬浮底栏液态玻璃效果",
                                summary = "开启边缘透镜折射、色散与重力倾角流光高光",
                                checked = themeState.enableFloatingBottomBarBlur,
                                onCheckedChange = { themeState.updateEnableFloatingBottomBarBlur(it) }
                            )
                        }
                    }

                    SmallTitle(
                        text = "返回手势"
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        // 与 KernelSU 一致：只有 Android 14 及以上才有预测动画，13 上不显示这一项。
                        if (supportsPredictiveBack) {
                            SwitchPreference(
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Rounded.BackHand,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(24.dp),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                title = "启用预测性返回手势的支持",
                                summary = "返回事件交给系统 OnBackInvoked 通道，带预测动画",
                                checked = themeState.enablePredictiveBack,
                                onCheckedChange = { enabled ->
                                    themeState.updateEnablePredictiveBack(enabled)
                                    // 返回通道的位值只在窗口创建时读取，需要重建 Activity 才生效。
                                    (context.applicationContext as? Application)?.let { app ->
                                        PredictiveBackController.applyPredictiveBackEnabled(app, enabled)
                                        (context as? Activity)?.recreate()
                                    }
                                }
                            )
                        }

                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.Swipe,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "横移返回手势",
                            summary = "在子页从屏幕边缘横滑返回上一级",
                            checked = themeState.enableSwipeBack,
                            onCheckedChange = { themeState.updateEnableSwipeBack(it) }
                        )
                    }

                    SmallTitle(
                        text = "莫奈取色与风格"
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
                                    imageVector = Icons.Rounded.ColorLens,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onBackground
                                )
                            },
                            title = "启用莫奈动态取色 (Monet)",
                            summary = "根据系统壁纸或所选主题色生成 Miuix 动态调色板",
                            checked = themeState.monetEnabled,
                            onCheckedChange = { themeState.updateMonetEnabled(it) }
                        )

                        AnimatedVisibility(
                            visible = themeState.monetEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                GlassDropdownPreference(backdrop = backdrop, 
                                    title = "调色板风格 (PaletteStyle)",
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Rounded.Palette,
                                            contentDescription = null,
                                            modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    items = paletteLabels,
                                    selectedIndex = paletteStyles.indexOf(themeState.paletteStyle).coerceAtLeast(0),
                                    onSelectedIndexChange = { idx ->
                                        themeState.updatePaletteStyle(paletteStyles.getOrElse(idx) { ThemePaletteStyle.TonalSpot })
                                    }
                                )

                                GlassDropdownPreference(backdrop = backdrop, 
                                    title = "色彩规范版本 (ColorSpec)",
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Rounded.Style,
                                            contentDescription = null,
                                            modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    items = colorSpecLabels,
                                    selectedIndex = colorSpecs.indexOf(themeState.colorSpec).coerceAtLeast(0),
                                    onSelectedIndexChange = { idx ->
                                        themeState.updateColorSpec(colorSpecs.getOrElse(idx) { ThemeColorSpec.Spec2021 })
                                    }
                                )

                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        val sysColorScheme = if (isDark) {
                                            dynamicDarkColorScheme(context)
                                        } else {
                                            dynamicLightColorScheme(context)
                                        }
                                        ColorButtonMiuix(
                                            color = sysColorScheme.primary,
                                            isSelected = themeState.keyColor == 0,
                                            isDark = isDark,
                                            paletteStyle = themeState.paletteStyle,
                                            colorSpec = themeState.colorSpec,
                                            onClick = { themeState.updateKeyColor(0) }
                                        )
                                    }
                                    keyColorOptions.forEach { named ->
                                        val argb = named.color.toArgb()
                                        ColorButtonMiuix(
                                            color = named.color,
                                            isSelected = themeState.keyColor == argb,
                                            isDark = isDark,
                                            paletteStyle = themeState.paletteStyle,
                                            colorSpec = themeState.colorSpec,
                                            onClick = { themeState.updateKeyColor(argb) }
                                        )
                                    }
                                }
                            }
                        }
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

@Composable
private fun ColorButtonMiuix(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    paletteStyle: ThemePaletteStyle,
    colorSpec: ThemeColorSpec,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) color else colorScheme.outline,
                shape = RoundedCornerShape(18.dp)
            )
            .background(colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton },
        contentAlignment = Alignment.Center
    ) {
        val scale by animateFloatAsState(
            targetValue = if (isSelected) 1.06f else 1.0f,
            label = "swatchScale"
        )
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(44.dp)) {
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height)
                )
                drawArc(
                    color = color.copy(alpha = 0.65f),
                    startAngle = 90f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height)
                )
                drawArc(
                    color = color.copy(alpha = 0.35f),
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height)
                )
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
