package io.github.univers629.millet.ui.screen
import io.github.univers629.millet.ui.util.MilletTopAppBar
import io.github.univers629.millet.ui.util.MilletSmallTopAppBar
import io.github.univers629.millet.ui.util.BarIconButton

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Widgets
import io.github.univers629.millet.ui.component.XMilletIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.univers629.millet.R
import io.github.univers629.millet.core.AppListPermissionHelper
import io.github.univers629.millet.core.LsposedHelper
import io.github.univers629.millet.ui.component.StatusTagMiuix
import io.github.univers629.millet.ui.component.effect.BgEffectBackground
import io.github.univers629.millet.ui.component.effect.GlowBackground
import io.github.univers629.millet.ui.component.effect.GlowPainter
import io.github.univers629.millet.ui.util.rememberBlurBackdrop
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * OOBE 当前导航步骤：
 * - Startup: 最开场的欢迎入场动画与扩散光效页（HyperCeiler 同款 Glow 扩散光圈与三段式入场动画）
 * - Permissions: 核心权限配置向导页
 */
enum class OobeStep {
    Startup,
    Permissions
}

/**
 * 严格按照 HyperOS 3.0 Provision 官方规范构建的 Scale-Up 圆形展开蒙版（Circular Reveal）：
 * 以底部圆形下一步按钮物理中心为圆心，由初始按钮半径（35dp）平滑扩张至完全覆盖屏幕对角线（maxRadius）。
 */
private class CircleRevealShape(
    private val center: Offset,
    private val radius: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            addOval(
                Rect(
                    left = center.x - radius,
                    top = center.y - radius,
                    right = center.x + radius,
                    bottom = center.y + radius
                )
            )
        }
        return Outline.Generic(path)
    }
}

/**
 * XMillet 首次启动开箱引导（OOBE / Provision）与权限配置向导页面：
 * - 深度学研 HyperCeiler provision 模块现代动画与流光设计；
 * - 开场展示 OKLab 色彩空间计算的 Simplex 噪声与开场环形扩散光效（GlowBackground / glow.glsl）；
 * - 第一页的 Logo 图标、应用大标题 XMillet，以及底部的「下一步」右箭头按钮，统一使用「关于」页同款毛玻璃（textureBlur + logoBlend）效果；
 * - 强制常开毛玻璃渲染（不随设置项中的毛玻璃开关改变）；
 * - Logo 缩放入场动画（0.5 -> 0.95 -> 1.0）+ 标题平移渐显 + 底部 70dp 圆形下一步按钮（带延时入场）；
 * - 下一步按钮点击后播放向外扩张平滑衔接动画过渡至权限配置向导页；
 * - 权限配置页遵循规范：盾牌图标、大标题与副标题均不使用 textureBlur；
 * - 四项核心系统权限统一纳项在同一张 Miuix 透光超椭圆卡片（Card）内；
 * - 权限配置完成后进入首页衔接 HyperCeiler 同款 enter_home 渐隐与弹跳动画（spring 0.65 scale 1.3 -> 1.0）。
 */
@Composable
fun OobeScreenMiuix(
    isPreview: Boolean = false,
    onBack: () -> Unit = {},
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val layoutDirection = LocalLayoutDirection.current
    val colorScheme = MiuixTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f
    val startupBackdrop = rememberBlurBackdrop(true)

    var currentStep by rememberSaveable { mutableStateOf(OobeStep.Startup) }
    var isTransitioning by remember { mutableStateOf(false) }
    val transitionProgress = remember { Animatable(0f) }

    // 记录全局根容器与底部下一步按钮的物理坐标与几何尺寸，用于实现 Scale-Up 圆形展开（Circular Reveal）
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var btnCenterInRoot by remember { mutableStateOf(Offset.Zero) }
    var btnRadiusPx by remember { mutableFloatStateOf(0f) }

    // 计算展开至完全覆盖全屏所有四角所需的最大物理半径
    val maxRadius = remember(rootSize, btnCenterInRoot) {
        if (rootSize.width <= 0 || rootSize.height <= 0) 2500f
        else {
            val cx = if (btnCenterInRoot != Offset.Zero) btnCenterInRoot.x else rootSize.width / 2f
            val cy = if (btnCenterInRoot != Offset.Zero) btnCenterInRoot.y else rootSize.height * 0.85f
            val dx = maxOf(cx, rootSize.width - cx)
            val dy = maxOf(cy, rootSize.height - cy)
            hypot(dx.toDouble(), dy.toDouble()).toFloat()
        }
    }

    // 拦截权限页的系统返回键，平滑回退至 Startup 欢迎页（接入共享 NavigationEventDispatcher，不抢占上层浮层）
    val oobeBackState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = oobeBackState,
        isBackEnabled = currentStep == OobeStep.Permissions && !isTransitioning,
        onBackCompleted = { currentStep = OobeStep.Startup },
    )

    var isLspActive by remember { mutableStateOf(false) }
    var canReadApps by remember { mutableStateOf(false) }

    // 检查并更新当前所有权限状态（并发异步刷新）
    suspend fun refreshPermissions() = coroutineScope {
        val lspDeferred = async(Dispatchers.IO) { LsposedHelper.isScopeActive(context) }
        val appsDeferred = async(Dispatchers.IO) { AppListPermissionHelper.canReadInstalledApps(context) }

        isLspActive = lspDeferred.await()
        canReadApps = appsDeferred.await()
    }

    val appListPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        canReadApps = granted || AppListPermissionHelper.canReadInstalledApps(context)
    }

    // 仅在进入权限配置页（Permissions）时才自动触发申请，避免在 Startup 入场动画期间弹窗打断体验
    LaunchedEffect(currentStep) {
        if (currentStep == OobeStep.Permissions) {
            refreshPermissions()
            if (!canReadApps && AppListPermissionHelper.isSupported(context)) {
                appListPermissionLauncher.launch(AppListPermissionHelper.PERMISSION)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    refreshPermissions()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val displayCutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val horizontalSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 触发从 Startup 到 Permissions 的平滑扩散衔接动画（严格对齐 HyperCeiler OS3 的 500ms Scale-Up 标准）
    val os3CubicEase = remember { CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f) }
    val handleNextFromStartup: () -> Unit = {
        if (!isTransitioning) {
            coroutineScope.launch {
                isTransitioning = true
                transitionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 500, easing = os3CubicEase)
                )
                currentStep = OobeStep.Permissions
                isTransitioning = false
                transitionProgress.snapTo(0f)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                rootCoordinates = coords
                rootSize = coords.size
            }
    ) {
        if (currentStep == OobeStep.Startup || isTransitioning) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                OobeStartupContent(
                    isPreview = isPreview,
                    backdrop = startupBackdrop,
                    isTransitioning = isTransitioning,
                    transitionProgress = transitionProgress.value,
                    rootCoordinates = rootCoordinates,
                    onBtnPositioned = { center, radius ->
                        btnCenterInRoot = center
                        btnRadiusPx = radius
                    },
                    onBack = onBack,
                    onNext = handleNextFromStartup
                )
            }
        }

        if (currentStep == OobeStep.Permissions || isTransitioning) {
            val revealModifier = if (isTransitioning) {
                val startRadius = if (btnRadiusPx > 0f) btnRadiusPx else 35.dp.value * 3f
                val currentRadius = startRadius + (maxRadius - startRadius) * transitionProgress.value
                val center = if (btnCenterInRoot != Offset.Zero) {
                    btnCenterInRoot
                } else {
                    Offset(rootSize.width / 2f, rootSize.height * 0.85f)
                }
                Modifier.clip(CircleRevealShape(center, currentRadius))
            } else {
                Modifier
            }

            // 权限页内容轻微上移与渐显（HyperOS 3.0 Provision 官方上浮微动效）
            val permAlpha = if (isTransitioning) {
                (transitionProgress.value * 1.5f).coerceIn(0f, 1f)
            } else 1f
            val permTranslationY = if (isTransitioning) {
                (1f - transitionProgress.value) * 60f
            } else 0f

            val permLightColors = remember { lightColorScheme() }
            MiuixTheme(colors = permLightColors) {
                val colorScheme = permLightColors
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(revealModifier)
                        .graphicsLayer {
                            alpha = permAlpha
                            translationY = permTranslationY
                        }
                ) {
                    Scaffold(
                        topBar = {
                            MilletSmallTopAppBar(
                                title = "",
                                color = Color.Transparent,
                                titleColor = colorScheme.onSurface,
                                navigationIcon = {
                                    BarIconButton(
                                        onClick = {
                                            currentStep = OobeStep.Startup
                                        },
                                        modifier = Modifier.padding(start = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Back,
                                            contentDescription = "返回欢迎页",
                                            tint = colorScheme.onSurface
                                        )
                                    }
                                },
                            )
                        },
                    ) { innerPadding ->
                        BgEffectBackground(
                            dynamicBackground = true,
                            isOs3Effect = true,
                            modifier = Modifier.fillMaxSize(),
                            effectBackground = true,
                            isDarkTheme = false, // 严格保持浅色模式
                            alpha = { 1f },
                        ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 640.dp)
                            ) {
                                val listContentPadding = PaddingValues(
                                    start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + displayCutoutInsets.calculateLeftPadding(layoutDirection) + 12.dp,
                                    top = innerPadding.calculateTopPadding() + 8.dp,
                                    end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + displayCutoutInsets.calculateRightPadding(layoutDirection) + 12.dp,
                                    bottom = navBarPadding + 96.dp,
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scrollEndHaptic()
                                        .overScrollVertical(),
                                    contentPadding = listContentPadding,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // 1. 顶部 Header 区域：纯净安全图标 + 标题与副标题（严格不使用 textureBlur）
                                    item(key = "header") {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 28.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = XMilletIcons.AppPolicy,
                                                contentDescription = null,
                                                modifier = Modifier.size(72.dp),
                                                tint = colorScheme.primary
                                            )

                                            Spacer(Modifier.height(18.dp))

                                            Text(
                                                modifier = Modifier.padding(bottom = 6.dp),
                                                text = "权限设置",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 32.sp,
                                                color = colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )

                                            Text(
                                                text = "你可以稍后设置下列必要权限",
                                                fontSize = 14.sp,
                                                color = colorScheme.onSurfaceVariantSummary,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    // 2. 核心系统权限卡片区域：Miuix 透光 Squircle 卡片
                                    item(key = "permissions_card") {
                                        SmallTitle(
                                            text = "系统与服务权限",
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 16.dp),
                                            colors = CardDefaults.defaultColors(
                                                color = colorScheme.surfaceContainer.copy(alpha = 0.85f),
                                            ),
                                        ) {
                                            // 权限 1：在 Xposed 管理器中激活
                                            BasicComponent(
                                                startAction = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Widgets,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .padding(end = 8.dp)
                                                            .size(24.dp),
                                                        tint = colorScheme.onSurface,
                                                    )
                                                },
                                                title = "在 Xposed 管理器中激活",
                                                summary = "在 LSPosed 中启用 XMillet，并勾选「系统框架」与「电量与性能」",
                                                endActions = {
                                                    if (isLspActive) {
                                                        GrantedCheckIndicator()
                                                    } else {
                                                        StatusTagMiuix(
                                                            label = "点击设置",
                                                            backgroundColor = colorScheme.secondaryContainer,
                                                            contentColor = colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    if (isLspActive) {
                                                        Toast.makeText(context, "LSPosed 模块已激活并握手成功", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        coroutineScope.launch {
                                                            withContext(Dispatchers.IO) {
                                                                LsposedHelper.openModuleList(context)
                                                            }
                                                        }
                                                    }
                                                }
                                            )

                                            // 权限 2：允许读取所有应用列表
                                            BasicComponent(
                                                startAction = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Apps,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .padding(end = 8.dp)
                                                            .size(24.dp),
                                                        tint = colorScheme.onSurface,
                                                    )
                                                },
                                                title = "允许读取所有应用列表",
                                                summary = "用于在冻结策略中精确检索已安装用户应用与系统应用配置",
                                                endActions = {
                                                    if (canReadApps) {
                                                        GrantedCheckIndicator()
                                                    } else {
                                                        StatusTagMiuix(
                                                            label = "点击授权",
                                                            backgroundColor = colorScheme.secondaryContainer,
                                                            contentColor = colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    if (canReadApps) {
                                                        Toast.makeText(context, "已获得读取应用列表权限", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        if (AppListPermissionHelper.isSupported(context)) {
                                                            appListPermissionLauncher.launch(AppListPermissionHelper.PERMISSION)
                                                        } else {
                                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                                data = Uri.fromParts("package", context.packageName, null)
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            runCatching { context.startActivity(intent) }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                // 3. 底部固定的“完成”主操作按钮
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(
                                            start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + displayCutoutInsets.calculateLeftPadding(layoutDirection) + 16.dp,
                                            end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + displayCutoutInsets.calculateRightPadding(layoutDirection) + 16.dp,
                                            bottom = navBarPadding + 16.dp
                                        )
                                ) {
                                    Button(
                                        onClick = onComplete,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        cornerRadius = 16.dp,
                                        colors = ButtonDefaults.buttonColors(
                                            color = colorScheme.primary,
                                            contentColor = colorScheme.onPrimary
                                        )
                                    ) {
                                        Text(
                                            text = if (isLspActive && canReadApps) "完成并进入" else "完成",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * XMillet 首次启动欢迎页（Startup）：
 * - 对齐 HyperCeiler 与 InstallerX-Revived 的流光混合 Token 与色彩体系；
 * - 图标采用 InstallerX-Revived 关于页同款裸露内部图案（ic_xmillet_about_logo），不使用背景底衬与圆角外框；
 * - 消除衔接动画中的矩形边缘边界：Box 与 Image 尺寸对齐（140dp），CompositingStrategy.Offscreen 直接施加于 Image 自身，
 *   使 DstIn 混色限制在矢量图形透明通道内，动画与淡出中不出现矩形方框边界；
 * - 应用标题为纯净裸字（XMillet），严格无底衬背景、无描边、无容器；
 * - 底部 70dp 圆形下一步按钮（CircleShape），流光毛玻璃球体 + 纯白右箭头（ic_oobe_arrow），点击后向外扩张并平滑进入权限配置向导；
 * - 竖向布局严格对齐 HyperCeiler 30 : 40 : 20 黄金比例；
 * - 入场动画：图标缩放入场（0.5 -> 1.0）与渐显，标题下落渐显（translationY +60dp -> 0dp），下一步按钮延时 1200ms 弹出。
 */
@Composable
private fun OobeStartupContent(
    isPreview: Boolean,
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop?,
    isTransitioning: Boolean,
    transitionProgress: Float,
    rootCoordinates: LayoutCoordinates?,
    onBtnPositioned: (center: Offset, radius: Float) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var circleYOffset by remember { mutableFloatStateOf(0.18f) }
    val contentScale = remember { Animatable(0.5f) }
    val contentAlpha = remember { Animatable(0f) }
    val btnScale = remember { Animatable(0.9f) }
    val btnAlpha = remember { Animatable(0f) }
    var isBtnClickable by remember { mutableStateOf(false) }

    // 统一使用下方下一步按钮同款的 OS3 流光暗光玻璃材质（LinearLight + Lab）：
    // 避免 SrcOver 灰死色遮挡，使应用图标、应用标题、副标题和底部按钮拥有一致的流光折射
    val oobeGlassBlend = remember {
        listOf(
            BlendColorEntry(Color(0xFF2E2E2E), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
        )
    }

    LaunchedEffect(Unit) {
        // 1. Logo 与标题完全同步绽放入场动画（严格对齐 HyperCeiler AnimHelper.startPageLogoAnim）：
        // scale: 0.5 -> 0.95 (440ms, sinOut) -> 1.0 (700ms, cubicOut)
        launch {
            contentScale.animateTo(
                0.95f,
                animationSpec = tween(440, easing = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1f))
            )
            contentScale.animateTo(
                1.0f,
                animationSpec = tween(700, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))
            )
        }
        // alpha: 延时 60ms 后渐变到 1.0
        launch {
            delay(60)
            contentAlpha.animateTo(
                1.0f,
                animationSpec = tween(400, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))
            )
        }

        // 2. 底部下一步按钮延时入场动画（严格对齐 HyperCeiler AnimHelper.startPageBtnAnim）：
        // 延时 1340ms，scale: 0.9 -> 1.0 (450ms, cubicOut)，alpha: 0 -> 1.0 (450ms)
        launch {
            delay(1340)
            launch {
                btnScale.animateTo(
                    1.0f,
                    animationSpec = tween(450, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))
                )
            }
            launch {
                btnAlpha.animateTo(
                    1.0f,
                    animationSpec = tween(450, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))
                )
                isBtnClickable = true
            }
        }
    }

    GlowBackground(
        modifier = Modifier.fillMaxSize(),
        bgModifier = backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier,
        needAdmission = true,
        circleYOffset = circleYOffset,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isPreview) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 12.dp, top = 8.dp)
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.30f))

                // 1. Logo 图标（90dp × 90dp）
                val currentAlpha = if (isTransitioning) {
                    (contentAlpha.value * (1f - transitionProgress * 3f)).coerceIn(0f, 1f)
                } else {
                    contentAlpha.value
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(90.dp)
                        .graphicsLayer {
                            scaleX = contentScale.value
                            scaleY = contentScale.value
                            alpha = currentAlpha
                        }
                        .onGloballyPositioned { coordinates ->
                            val windowRoot = coordinates.findRootCoordinates()
                            val bounds = coordinates.boundsInWindow()
                            val screenH = windowRoot.size.height.toFloat()
                            if (screenH > 0f) {
                                val centerY = bounds.top + bounds.height / 2f
                                circleYOffset = (screenH / 2f - centerY) / screenH
                            }
                        }
                ) {
                    if (backdrop != null) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_xmillet_oobe_logo),
                            contentDescription = "XMillet Logo",
                            modifier = Modifier
                                .size(90.dp)
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .textureBlur(
                                    backdrop = backdrop,
                                    shape = RectangleShape,
                                    blurRadius = 60f,
                                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                    colors = BlurColors(blendColors = oobeGlassBlend),
                                    contentBlendMode = BlendMode.DstIn,
                                    enabled = true,
                                )
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_xmillet_oobe_logo),
                            contentDescription = "XMillet Logo",
                            modifier = Modifier.size(90.dp),
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.40f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. 标题（XMillet）：下方按钮同款流光玻璃材质窗口，透出通透流光
                    Text(
                        text = "XMillet",
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-1.2).sp,
                        color = Color.White,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = contentScale.value
                                scaleY = contentScale.value
                                alpha = currentAlpha
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .then(
                                if (backdrop != null) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = 60f,
                                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                        colors = BlurColors(blendColors = oobeGlassBlend),
                                        contentBlendMode = BlendMode.DstIn,
                                        enabled = true,
                                    )
                                } else {
                                    Modifier
                                }
                            )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 副标题：下方按钮同款流光玻璃材质，透出流动光彩
                    Text(
                        text = "小米官方墓碑millet的增强模块",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = contentScale.value
                                scaleY = contentScale.value
                                alpha = currentAlpha
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .then(
                                if (backdrop != null) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(8.dp),
                                        blurRadius = 60f,
                                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                        colors = BlurColors(blendColors = oobeGlassBlend),
                                        contentBlendMode = BlendMode.DstIn,
                                        enabled = true,
                                    )
                                } else {
                                    Modifier
                                }
                            )
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 3. 底部圆形下一步按钮（70dp）
                    Box(
                        modifier = Modifier
                            .padding(bottom = 4.5.dp)
                            .graphicsLayer {
                                scaleX = btnScale.value
                                scaleY = btnScale.value
                                alpha = btnAlpha.value
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .onGloballyPositioned { coordinates ->
                                    rootCoordinates?.let { root ->
                                        if (root.isAttached && coordinates.isAttached) {
                                            val posInRoot = root.localPositionOf(coordinates, Offset.Zero)
                                            val center = Offset(
                                                posInRoot.x + coordinates.size.width / 2f,
                                                posInRoot.y + coordinates.size.height / 2f
                                            )
                                            val radius = coordinates.size.width / 2f
                                            onBtnPositioned(center, radius)
                                        }
                                    }
                                }
                                .clip(CircleShape)
                                .then(
                                    if (backdrop != null) {
                                        Modifier.textureBlur(
                                            backdrop = backdrop,
                                            shape = CircleShape,
                                            blurRadius = 60f,
                                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                            colors = BlurColors(blendColors = oobeGlassBlend),
                                            enabled = true,
                                        )
                                    } else {
                                        Modifier.background(Color(0xFF2E2E3E))
                                    }
                                )
                                .clickable(enabled = isBtnClickable && !isTransitioning) {
                                    onNext()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!isTransitioning) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_oobe_arrow),
                                    contentDescription = "下一步",
                                    tint = Color.White,
                                    modifier = Modifier.size(width = 30.dp, height = 21.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                Spacer(modifier = Modifier.weight(0.20f))
            }
        }
    }
}

/**
 * 权限已就绪图标组件：Miuix 经典小圆底与 Check 对勾
 */
@Composable
private fun GrantedCheckIndicator() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = MiuixIcons.Basic.Check,
            contentDescription = "已授予",
            tint = colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}
