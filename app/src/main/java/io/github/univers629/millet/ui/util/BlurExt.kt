package io.github.univers629.millet.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import io.github.univers629.millet.ui.theme.LocalEnableProgressiveBlur
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.material.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun Modifier.milletMiuixBlurEffect(
    backdrop: LayerBackdrop?,
    enabled: Boolean = true,
    blurRadius: Float = 25f,
    shape: Shape = RectangleShape,
): Modifier {
    if (!enabled || backdrop == null) return this
    val blendColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)
    return this.then(
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(color = blendColor),
                ),
            ),
        ),
    )
}

/**
 * 顶栏/底栏外壳：向内下发背景采样源（[LocalBarBackdrop]）。
 * 渐进式模糊开启时由 GlassTopAppBar / 底部渐变带自行绘制，这里不再叠整块 textureBlur。
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progressive = LocalEnableProgressiveBlur.current && backdrop != null
    CompositionLocalProvider(LocalBarBackdrop provides backdrop) {
        Box(
            modifier = modifier.then(
                if (blurActive && !progressive && backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 25f,
                        colors = BlurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.87f)),
                            ),
                        ),
                    )
                } else {
                    Modifier
                }
            ),
        ) {
            content()
        }
    }
}
