package io.github.univers629.millet.ui.screen
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import io.github.univers629.millet.ui.util.PopupBackdropHolder
import io.github.univers629.millet.ui.util.MilletTopAppBar
import io.github.univers629.millet.ui.util.MilletSmallTopAppBar
import io.github.univers629.millet.ui.util.BarIconButton

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import io.github.univers629.millet.BuildConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univers629.millet.R
import io.github.univers629.millet.ui.component.DialogBackHandler
import io.github.univers629.millet.ui.component.StatusTagMiuix
import io.github.univers629.millet.ui.component.effect.BgEffectBackground
import io.github.univers629.millet.ui.theme.LocalEnableBlur
import io.github.univers629.millet.ui.util.AboutPhoneUtils
import io.github.univers629.millet.ui.util.milletMiuixBlurEffect
import io.github.univers629.millet.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 关于主界面（对齐 InstallerX-Revived / HyperCeiler / HyperOS 规范）：
 * - 只有主界面具备沉浸式状态栏与动态背景光效，下滑滚动时动效淡出；
 * - 图标区域无背景卡片、无外层光圈，居中呈现动态色彩 Logo、标题与版本号；
 * - 所有卡片为标准 Miuix 透光卡片，卡片内不同行自然排列，无横线分割；
 * - 卡片每行左侧图标无多余高亮配色，与普通行保持一致。
 */
@Composable
fun AboutScreenMiuix(
    onBack: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenRepo: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val isDark = colorScheme.surface.luminance() < 0.5f
    // 关于页流光不依赖“顶栏/底栏毛玻璃”设置：参照 InstallerX/HyperCeiler，
    // 关于页的动态背景与图标透传是页面自身效果，仅受系统能力（RenderEffect）限制。
    val backdrop = rememberBlurBackdrop(true)

    val context = LocalContext.current
    val versionLabel = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName}(${info.longVersionCode})"
        }.getOrDefault("${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
    }
    val deviceModel = remember { AboutPhoneUtils.getDeviceModel() }
    val androidVersion = remember { AboutPhoneUtils.getAndroidVersion() }
    val osVersion = remember { AboutPhoneUtils.getOsVersion() }

    val lazyListState = rememberLazyListState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var logoHeightPx by remember { mutableIntStateOf(0) }
    var logoHeightDp by remember { mutableStateOf(260.dp) }

    // 滚动进度：0f = 停留在顶部，1f = 头部区域完全收起。
    // 分母取占位项（见下方 logoSpacer）的实际高度，与 InstallerX 的 MiuixAboutPage 同一口径。
    val scrollProgressState = remember {
        derivedStateOf {
            if (logoHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }
    val scrollProgress by scrollProgressState
    // 三段错峰窗口：版本号 5%→20%、标题 20%→35%、图标 35%→50% 依次收起。
    // 只在 graphicsLayer 的绘制阶段读取（不要提前求值），否则每滚动一像素都会触发重组。
    fun collapseProgress(start: Float): Float =
        ((scrollProgressState.value - start) / 0.15f).coerceIn(0f, 1f)

    val displayCutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val horizontalSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()

    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xCC2A2A38), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xFF3A3A4A), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
            )
        }
    }

    val cardBlurColors = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0x991E1E22), BlurBlendMode.SrcOver),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xCCF7F7FA), BlurBlendMode.SrcOver),
            )
        }
    }

    Scaffold(
        topBar = {
            MilletSmallTopAppBar(
                modifier = Modifier.milletMiuixBlurEffect(
                    backdrop = backdrop,
                    enabled = scrollProgress == 1f,
                ),
                title = "关于",
                scrollBehavior = topAppBarScrollBehavior,
                color = if (backdrop != null && scrollProgress == 1f) {
                    Color.Transparent
                } else {
                    colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f)
                },
                titleColor = colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    BarIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = colorScheme.onSurface
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val listContentPadding = PaddingValues(
            start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + displayCutoutInsets.calculateLeftPadding(layoutDirection),
            top = innerPadding.calculateTopPadding(),
            end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + displayCutoutInsets.calculateRightPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + 32.dp,
        )

        val logoPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 24.dp,
            start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + displayCutoutInsets.calculateLeftPadding(layoutDirection),
            end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + displayCutoutInsets.calculateRightPadding(layoutDirection),
        )

        BgEffectBackground(
            dynamicBackground = true,
            isOs3Effect = true,
            modifier = Modifier.fillMaxSize(),
            bgModifier = backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier,
            effectBackground = true,
            isFullSize = false,
            isDarkTheme = isDark,
            alpha = { (1f - scrollProgress).coerceIn(0f, 1f) },
        ) {
            // 居中 Logo 与标题区域（无背景卡片、无外层光圈、动态流光上色）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = logoPadding.calculateTopPadding() + 52.dp,
                        start = logoPadding.calculateStartPadding(layoutDirection),
                        end = logoPadding.calculateEndPadding(layoutDirection),
                    )
                    .onSizeChanged { size ->
                        with(density) { logoHeightDp = size.height.toDp() }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(width = 80.dp, height = 90.dp),
                ) {
                    if (backdrop != null) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_xmillet_oobe_logo),
                            modifier = Modifier
                                .size(width = 80.dp, height = 90.dp)
                                .graphicsLayer {
                                    val progress = collapseProgress(0.35f)
                                    alpha = 1f - progress
                                    scaleX = 1f - progress * 0.05f
                                    scaleY = 1f - progress * 0.05f
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .textureBlur(
                                    backdrop = backdrop,
                                    // 用图形自身的 alpha 当遮罩，不再叠一层圆角矩形窗口
                                    shape = RectangleShape,
                                    blurRadius = 80f,
                                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                    colors = BlurColors(blendColors = logoBlend),
                                    contentBlendMode = BlendMode.DstIn,
                                    enabled = true,
                                ),
                            contentDescription = null,
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_xmillet_oobe_logo),
                            modifier = Modifier
                                .size(width = 80.dp, height = 90.dp)
                                .graphicsLayer {
                                    val progress = collapseProgress(0.35f)
                                    alpha = 1f - progress
                                    scaleX = 1f - progress * 0.05f
                                    scaleY = 1f - progress * 0.05f
                                },
                            colorFilter = ColorFilter.tint(if (isDark) Color(0xFFE2E7FF) else colorScheme.onSurface),
                            contentDescription = "XMillet",
                        )
                    }
                }

                Text(
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 4.dp)
                        .graphicsLayer {
                            val progress = collapseProgress(0.20f)
                            alpha = 1f - progress
                            scaleX = 1f - progress * 0.05f
                            scaleY = 1f - progress * 0.05f
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .then(
                            if (backdrop != null) {
                                Modifier.textureBlur(
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(16.dp),
                                    blurRadius = 80f,
                                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                    colors = BlurColors(blendColors = logoBlend),
                                    contentBlendMode = ComposeBlendMode.DstIn,
                                    enabled = true,
                                )
                            } else {
                                Modifier
                            },
                        ),
                    text = "XMillet",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 35.sp,
                    letterSpacing = 0.sp,
                    color = if (backdrop != null) {
                        if (isDark) Color.White else Color(0xFF1A1A2E)
                    } else colorScheme.onSurface,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val progress = collapseProgress(0.05f)
                            alpha = 1f - progress
                            scaleX = 1f - progress * 0.05f
                            scaleY = 1f - progress * 0.05f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        color = colorScheme.onSurfaceVariantSummary,
                        text = versionLabel,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // 内容列表区域（卡片透光、纯净无边框、无行间横线）
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                contentPadding = listContentPadding,
            ) {
                item(key = "logoSpacer") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(
                                logoHeightDp + 52.dp + logoPadding.calculateTopPadding() - listContentPadding.calculateTopPadding() + 126.dp,
                            )
                            .onSizeChanged { size ->
                                logoHeightPx = size.height
                            },
                    )
                }

                // 系统与环境卡片
                item {
                    SmallTitle(text = "系统与环境")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .then(
                                if (backdrop != null) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = 60f,
                                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                        colors = BlurColors(blendColors = cardBlurColors),
                                        enabled = true,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        colors = CardDefaults.defaultColors(
                            color = colorScheme.surfaceContainer.copy(alpha = 0.85f),
                        ),
                    ) {
                        BasicComponent(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.Smartphone,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onSurface,
                                )
                            },
                            title = "设备型号",
                            summary = deviceModel,
                        )
                        BasicComponent(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.Android,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onSurface,
                                )
                            },
                            title = "Android版本",
                            summary = androidVersion,
                        )
                        BasicComponent(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.Memory,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onSurface,
                                )
                            },
                            title = "OS版本",
                            summary = osVersion,
                        )
                    }
                }

                // 核心功能子页入口
                item {
                    SmallTitle(text = "内容与许可")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .then(
                                if (backdrop != null) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = 60f,
                                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                        colors = BlurColors(blendColors = cardBlurColors),
                                        enabled = true,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        colors = CardDefaults.defaultColors(
                            color = colorScheme.surfaceContainer.copy(alpha = 0.85f),
                        ),
                    ) {
                        ArrowPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onSurface,
                                )
                            },
                            title = "墓碑与冻结机制解析",
                            summary = "基础概念、名词解释与核心策略解析",
                            onClick = onOpenKnowledge,
                        )
                        ArrowPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.Code,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onSurface,
                                )
                            },
                            title = "开源地址",
                            summary = "https://github.com/univers629/XMillet",
                            onClick = onOpenRepo,
                        )
                        ArrowPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.Policy,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    tint = colorScheme.onSurface,
                                )
                            },
                            title = "开源代码许可",
                            summary = "查看引用的开源项目与其协议",
                            onClick = onOpenLicenses,
                        )
                    }
                }

                // 页脚版权
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Copyright © 2026 XMillet Project. All rights reserved.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

/**
 * 子页一：墓碑与冻结机制解析
 * - 无背景光效；
 * - 分点顺序：1、3、5、2、6、4；
 * - 标准 Miuix 卡片（无边框、无渐变背景、无行间横线）；
 * - 释义限制在 250 字以内，不使用“科普”字样。
 */
@Composable
fun AboutKnowledgeScreenMiuix(
    onBack: () -> Unit,
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    // 严格按 1 -> 3 -> 5 -> 2 -> 6 -> 4 排序
    val knowledgeList = listOf(
        KnowledgeArticle(
            title = "1. 什么是“墓碑机制” (Freezer)？",
            tag = "核心概念",
            content = "墓碑机制（Freezer）是一种让应用退到后台时“暂停呼吸”的节能技术。它不像杀后台那样强行关闭进程，而是借助 Linux 内核的 cgroup 挂起进程的所有线程。应用留在运行内存中，但完全不消耗 CPU 和电量；当你再次打开它时，几毫秒内就能瞬间满血恢复，既省电又保活。"
        ),
        KnowledgeArticle(
            title = "3. Linux cgroup v1 与 v2 冻结器差异",
            tag = "技术架构",
            content = "这是 Linux 内核管理进程的两种技术规范。cgroup v1（经典冻结器）各控制器彼此独立，层级复杂容易产生死锁；cgroup v2（统一层级树）是现代 Android（12 及以上）的标准推荐，冻结解冻更平滑、状态同步迅速。XMillet 会根据设备内核支持情况优先启用高效的 V2 架构。"
        ),
        KnowledgeArticle(
            title = "5. Android Doze（低电耗模式）协同机制",
            tag = "系统协同",
            content = "Doze 是 Android 系统级的深度节能机制。当设备长时间息屏且静置时，系统会自动推迟后台任务、限制网络访问并减少定时唤醒。XMillet 提供了便捷的 Millet 与 Doze 协同开关，让设备在息屏待机状态下更深层地沉睡，大幅延长待机续航。"
        ),
        KnowledgeArticle(
            title = "2. 小米 Millet 的核心职责与作用",
            tag = "官方机制",
            content = "Millet 是小米在 MIUI 与 HyperOS 中自研的智能后台墓碑系统。它协同系统的“电量与性能”（PowerKeeper）和 Linux 内核驱动，实时监控应用活跃度。在合适时机下达挂起指令，而在有通知、音频播放或跨进程调用等合法请求时精准唤醒，实现后台超低功耗与前台流畅的兼顾。"
        ),
        KnowledgeArticle(
            title = "6. XMillet 模块起到的增强作用",
            tag = "模块价值",
            content = "官方系统的 Millet 策略通常较为保守，或对第三方应用存在规则黑盒。XMillet 作为增强型 Xposed/LSPosed 模块，接管并补齐了系统服务与内核通信链路，提供细粒度的白名单、强制冻结、防止误解冻等高级策略，把墓碑机制的完全掌控权交还给用户。"
        ),
        KnowledgeArticle(
            title = "4. 为什么被冻结后仍能接收即时消息？",
            tag = "消息保活",
            content = "很多通信软件（如微信、QQ）包含前台界面进程与专门负责长连接的后台推送服务。XMillet 支持将核心通信应用列为“消息应用”，并通过智能 Binder 拦截。仅在有新消息或网络同步时瞬间唤醒应用，数据交换完成后再次安全挂起，做到既不丢消息又杜绝后台偷跑。"
        )
    )

    Scaffold(
        topBar = {
            MilletSmallTopAppBar(
                title = "墓碑与冻结机制解析",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BarIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = colorScheme.onSurface
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            )
        ) {
            items(knowledgeList.size) { index ->
                val item = knowledgeList[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            StatusTagMiuix(
                                label = item.tag,
                                backgroundColor = colorScheme.surfaceVariant,
                                contentColor = colorScheme.onSurfaceVariantSummary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.content,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 子页二：开源地址
 * - 无背景光效；
 * - 整张卡片触发跳转浏览器与网址，无额外按钮；
 * - 标准 Miuix 卡片，无行间分割线与外边框。
 */
@Composable
fun AboutRepoScreenMiuix(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val repoUrl = "https://github.com/univers629/XMillet"
    val donateQrResIds = remember {
        listOf(R.drawable.qr_donate_alipay, R.drawable.qr_donate_wechat)
    }
    var previewDonateResId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            MilletSmallTopAppBar(
                title = "开源地址",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BarIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = colorScheme.onSurface
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            )
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    onClick = {
                        openUrlInBrowser(context, repoUrl)
                    },
                    pressFeedbackType = PressFeedbackType.Sink,
                ) {
                    ArrowPreference(
                        title = "XMillet 源代码仓库",
                        summary = repoUrl,
                        startAction = {
                            Icon(
                                imageVector = Icons.Rounded.Code,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                                tint = colorScheme.onSurface
                            )
                        },
                        onClick = {
                            openUrlInBrowser(context, repoUrl)
                        }
                    )
                }
            }

            item {
                SmallTitle(text = "项目概览")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    BasicComponent(
                        title = "开源协议",
                        summary = "GNU General Public License v3.0 (GPL-3.0)"
                    )
                    BasicComponent(
                        title = "核心技术栈",
                        summary = "Kotlin · Jetpack Compose · Miuix · LibXposed / LSPosed · Linux cgroup"
                    )
                    BasicComponent(
                        title = "开发理念",
                        summary = "完全尊重用户自主控制权，提供透明、稳定、低功耗的系统级墓碑增强方案"
                    )
                }
            }

            item {
                SmallTitle(text = "支持")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Text(
                        text = "如果 XMillet 对你有所帮助，欢迎请作者喝杯咖啡",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (resId in donateQrResIds) {
                            Image(
                                painter = painterResource(resId),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(CardDefaults.CornerRadius))
                                    .clickable { previewDonateResId = resId }
                            )
                        }
                    }
                }
            }
        }
    }

    val activeResId = previewDonateResId
    GlassDialog(backdrop = PopupBackdropHolder.current, 
        show = activeResId != null,
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
        title = "感谢支持",
        onDismissRequest = { previewDonateResId = null },
        content = {
            DialogBackHandler { previewDonateResId = null }
            val dismissState = LocalDismissState.current
            if (activeResId != null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(activeResId),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(260.dp)
                    )
                }
                TextButton(
                    text = "关闭",
                    onClick = {
                        previewDonateResId = null
                        dismissState?.invoke()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    )
}

/**
 * 子页三：开源代码许可
 * - 无背景光效；
 * - 按「界面与基础库 / Xposed 接口与运行时 / 源码适配与设计参考」三组划分，与工程实际依赖和源码来源一致；
 * - 整张卡片作为点击跳转项，直接打开浏览器访问项目主页，无额外按钮；
 * - 标准 Miuix 卡片，无行间分割线与外边框。
 */
@Composable
fun AboutLicensesScreenMiuix(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    val licenseSections = remember {
        listOf(
            OpenSourceSection(
                title = "界面与基础开发库",
                projects = listOf(
                    OpenSourceProject(
                        name = "Miuix",
                        license = "Apache-2.0",
                        author = "yukonga (compose-miuix-ui)",
                        description = "小米 HyperOS 风格的 Compose Multiplatform UI 组件库，提供偏好设置、导航与模糊特效基础",
                        url = "https://github.com/compose-miuix-ui/miuix"
                    ),
                    OpenSourceProject(
                        name = "AndroidX & Jetpack Compose",
                        license = "Apache-2.0",
                        author = "Google LLC",
                        description = "官方声明式 UI 工具包与系统扩展库（含 Activity Compose、Material 3、Material Icons Extended 与 NavigationEvent）",
                        url = "https://github.com/androidx/androidx"
                    ),
                    OpenSourceProject(
                        name = "Kotlin",
                        license = "Apache-2.0",
                        author = "JetBrains",
                        description = "模块主体开发语言及协程异步编程基础设施",
                        url = "https://github.com/JetBrains/kotlin"
                    )
                )
            ),
            OpenSourceSection(
                title = "Xposed 接口与运行时",
                projects = listOf(
                    OpenSourceProject(
                        name = "LibXposed (API & Service)",
                        license = "Apache-2.0",
                        author = "libxposed",
                        description = "现代 Xposed API 102 接口与 RemotePreferences 跨进程配置同步服务",
                        url = "https://github.com/libxposed"
                    ),
                    OpenSourceProject(
                        name = "LSPosed",
                        license = "GPL-3.0",
                        author = "LSPosed Team",
                        description = "基于 ART 运行时的现代 Xposed Hook 框架，为系统服务与电量性能侧提供注入环境",
                        url = "https://github.com/LSPosed/LSPosed"
                    )
                )
            ),
            OpenSourceSection(
                title = "源码适配与设计参考",
                projects = listOf(
                    OpenSourceProject(
                        name = "AndroidLiquidGlass",
                        license = "Apache-2.0",
                        author = "Kyant0",
                        description = "悬浮底栏液态玻璃 AGSL 折射透镜（Lens）、色彩提亮（Vibrancy）与阻尼拖拽动效实现来源",
                        url = "https://github.com/Kyant0/AndroidLiquidGlass"
                    ),
                    OpenSourceProject(
                        name = "HyperCeiler",
                        license = "AGPL-3.0",
                        author = "ReChronoRain & Contributors",
                        description = "OOBE 引导页布局、Oklab 扩散光晕着色器（glow.glsl）、箭头矢量图标及 HyperOS 流光视觉架构参考",
                        url = "https://github.com/ReChronoRain/HyperCeiler"
                    ),
                    OpenSourceProject(
                        name = "InstallerX-Revived",
                        license = "GPL-3.0",
                        author = "wxxsfxyzm",
                        description = "关于页沉浸式头部折叠动效、Miuix 流光蒙版混色（ColorBlend）与版本信息页布局参考",
                        url = "https://github.com/wxxsfxyzm/InstallerX-Revived"
                    ),
                    OpenSourceProject(
                        name = "KernelSU",
                        license = "GPL-3.0",
                        author = "tiann (weishu)",
                        description = "应用图标异步 LRU 缓存（AppIconCache）、子页导航架构与 Miuix 主题调色板设计参考",
                        url = "https://github.com/tiann/KernelSU"
                    ),
                    OpenSourceProject(
                        name = "DexSQL",
                        license = "DexSQL Binary License",
                        author = "LSPosed",
                        description = "对系统 APK 与 DEX 做 SQL 检索与反汇编分析，用于定位系统侧 Hook 点（分析期工具）",
                        url = "https://github.com/LSPosed/dexsql-release"
                    )
                )
            )
        )
    }

    Scaffold(
        topBar = {
            MilletSmallTopAppBar(
                title = "开源代码许可",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BarIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = colorScheme.onSurface
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            )
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    BasicComponent(
                        startAction = {
                            Icon(
                                imageVector = Icons.Rounded.Policy,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                                tint = colorScheme.onSurface,
                            )
                        },
                        title = "致敬开源社区",
                        summary = "XMillet 的构建与界面交互离不开以下开源项目与上游库，点击卡片可跳转对应项目主页"
                    )
                }
            }

            licenseSections.forEach { section ->
                item(key = "section_${section.title}") {
                    SmallTitle(text = section.title)
                }
                items(
                    count = section.projects.size,
                    key = { index -> "${section.title}_${section.projects[index].name}" }
                ) { index ->
                    val project = section.projects[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 10.dp),
                        onClick = {
                            openUrlInBrowser(context, project.url)
                        },
                        pressFeedbackType = PressFeedbackType.Sink,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = project.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.5.sp,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                StatusTagMiuix(
                                    label = project.license,
                                    backgroundColor = colorScheme.surfaceVariant,
                                    contentColor = colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "维护者：${project.author}",
                                fontSize = 12.5.sp,
                                color = colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = project.description,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                color = colorScheme.onSurfaceVariantSummary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = project.url,
                                fontSize = 12.sp,
                                color = colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openUrlInBrowser(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "无法打开浏览器：${it.message}", Toast.LENGTH_SHORT).show()
    }
}

private data class KnowledgeArticle(
    val title: String,
    val tag: String,
    val content: String,
)

private data class OpenSourceSection(
    val title: String,
    val projects: List<OpenSourceProject>,
)

private data class OpenSourceProject(
    val name: String,
    val license: String,
    val author: String,
    val description: String,
    val url: String,
)
