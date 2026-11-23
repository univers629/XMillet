package io.github.univers629.millet.ui.theme

import android.app.Activity
import android.content.SharedPreferences
import android.os.Build
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.OnBackCompletedFallback
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import androidx.navigationevent.toNavigationEvent
import io.github.univers629.millet.core.PrefReader
import io.github.univers629.millet.core.PrefsDefaults
import io.github.univers629.millet.core.PrefsKeys
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

val LocalColorMode = staticCompositionLocalOf { 0 }
val LocalEnableBlur = compositionLocalOf { true }
val LocalEnableFloatingBottomBar = compositionLocalOf { true }
val LocalEnableFloatingBottomBarBlur = compositionLocalOf { true }
val LocalEnableProgressiveBlur = compositionLocalOf { true }

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    return when (LocalColorMode.current) {
        1, 4 -> false
        2, 5, 6 -> true
        else -> isSystemInDarkTheme()
    }
}

/**
 * XMillet 界面主题与视觉特效配置状态（持久化于 SharedPreferences，不污染 20 个核心 Hook 键）。
 */
@Stable
class UiThemeState(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_THEME_DARK_MODE = "ui_theme_dark_mode" // 0: 跟随系统, 1: 浅色, 2: 深色
        private const val KEY_MONET_ENABLED = "ui_monet_enabled"     // 默认 false（不启用莫奈动态取色，沿用品牌蓝）
        private const val KEY_KEY_COLOR = "ui_key_color"             // 0: 系统壁纸取色, 其余为 ARGB
        private const val KEY_PALETTE_STYLE = "ui_palette_style"     // ThemePaletteStyle.name
        private const val KEY_COLOR_SPEC = "ui_color_spec"           // ThemeColorSpec.name
        private const val KEY_ENABLE_BLUR = "ui_enable_blur"         // 顶栏/底栏毛玻璃
        private const val KEY_FLOATING_BAR = "ui_floating_bar"       // 悬浮底栏
        private const val KEY_FLOATING_BAR_BLUR = "ui_floating_bar_blur" // 悬浮底栏液态玻璃折射
        private const val KEY_SHOW_TOMB_STATS = "ui_show_tomb_stats"   // 底栏显示「墓碑统计」，默认开启
        private const val KEY_PREDICTIVE_BACK = "ui_predictive_back"   // 预测性返回（OnBackInvoked 通道），默认关闭
        private const val KEY_SWIPE_BACK = "ui_swipe_back"             // 横移返回手势，默认开启
        private const val KEY_PROGRESSIVE_BLUR = "ui_progressive_blur" // 顶栏/底栏渐进式模糊
    }

    /**
     * 底栏是否显示「墓碑统计」页（与模块配置键 [PrefsKeys.FREEZE_STATS] 保持同步，
     * 切换时同步通知 system_server 启停统计采集）。
     */
    var showTombStatsTab by mutableStateOf(
        if (prefs.contains(PrefsKeys.FREEZE_STATS)) {
            PrefReader.readUiBoolean(prefs, PrefsKeys.FREEZE_STATS)
        } else {
            prefs.getBoolean(KEY_SHOW_TOMB_STATS, PrefsDefaults.FREEZE_STATS)
        }
    )
        private set

    fun updateShowTombStatsTab(enabled: Boolean) {
        showTombStatsTab = enabled
        prefs.edit().putBoolean(KEY_SHOW_TOMB_STATS, enabled).apply()
        PrefReader.putBooleanAndNotify(prefs, PrefsKeys.FREEZE_STATS, enabled)
    }

    fun syncTombStatsFromPrefs() {
        val synced = PrefReader.readUiBoolean(prefs, PrefsKeys.FREEZE_STATS)
        showTombStatsTab = synced
        prefs.edit().putBoolean(KEY_SHOW_TOMB_STATS, synced).apply()
    }

    var darkMode by mutableIntStateOf(prefs.getInt(KEY_THEME_DARK_MODE, 0))
        private set

    var monetEnabled by mutableStateOf(prefs.getBoolean(KEY_MONET_ENABLED, false))
        private set

    var keyColor by mutableIntStateOf(prefs.getInt(KEY_KEY_COLOR, 0))
        private set

    var paletteStyle by mutableStateOf(
        runCatching {
            ThemePaletteStyle.valueOf(
                prefs.getString(KEY_PALETTE_STYLE, ThemePaletteStyle.TonalSpot.name)
                    ?: ThemePaletteStyle.TonalSpot.name
            )
        }.getOrDefault(ThemePaletteStyle.TonalSpot)
    )
        private set

    var colorSpec by mutableStateOf(
        runCatching {
            ThemeColorSpec.valueOf(
                prefs.getString(KEY_COLOR_SPEC, ThemeColorSpec.Spec2021.name)
                    ?: ThemeColorSpec.Spec2021.name
            )
        }.getOrDefault(ThemeColorSpec.Spec2021)
    )
        private set

    var enableBlur by mutableStateOf(prefs.getBoolean(KEY_ENABLE_BLUR, true))
        private set

    var enableFloatingBottomBar by mutableStateOf(prefs.getBoolean(KEY_FLOATING_BAR, true))
        private set

    var enableFloatingBottomBarBlur by mutableStateOf(prefs.getBoolean(KEY_FLOATING_BAR_BLUR, true))
        private set

    /** 是否让返回事件走系统的 OnBackInvoked 通道（关闭后退回传统 onBackPressed 通道）。 */
    var enablePredictiveBack by mutableStateOf(prefs.getBoolean(KEY_PREDICTIVE_BACK, false))
        private set

    /** 是否启用子页的横移返回手势。 */
    var enableSwipeBack by mutableStateOf(prefs.getBoolean(KEY_SWIPE_BACK, true))
        private set

    var enableProgressiveBlur by mutableStateOf(prefs.getBoolean(KEY_PROGRESSIVE_BLUR, true))
        private set

    fun updateEnableProgressiveBlur(enabled: Boolean) {
        enableProgressiveBlur = enabled
        prefs.edit().putBoolean(KEY_PROGRESSIVE_BLUR, enabled).apply()
    }

    fun updateDarkMode(mode: Int) {
        darkMode = mode.coerceIn(0, 2)
        prefs.edit().putInt(KEY_THEME_DARK_MODE, darkMode).apply()
    }

    fun updateMonetEnabled(enabled: Boolean) {
        monetEnabled = enabled
        prefs.edit().putBoolean(KEY_MONET_ENABLED, enabled).apply()
    }

    fun updateKeyColor(colorArgb: Int) {
        keyColor = colorArgb
        prefs.edit().putInt(KEY_KEY_COLOR, colorArgb).apply()
    }

    fun updatePaletteStyle(style: ThemePaletteStyle) {
        paletteStyle = style
        prefs.edit().putString(KEY_PALETTE_STYLE, style.name).apply()
    }

    fun updateColorSpec(spec: ThemeColorSpec) {
        colorSpec = spec
        prefs.edit().putString(KEY_COLOR_SPEC, spec.name).apply()
    }

    fun updateEnableBlur(enabled: Boolean) {
        enableBlur = enabled
        prefs.edit().putBoolean(KEY_ENABLE_BLUR, enabled).apply()
    }

    fun updateEnableFloatingBottomBar(enabled: Boolean) {
        enableFloatingBottomBar = enabled
        prefs.edit().putBoolean(KEY_FLOATING_BAR, enabled).apply()
    }

    fun updateEnableFloatingBottomBarBlur(enabled: Boolean) {
        enableFloatingBottomBarBlur = enabled
        prefs.edit().putBoolean(KEY_FLOATING_BAR_BLUR, enabled).apply()
    }

    fun updateEnablePredictiveBack(enabled: Boolean) {
        enablePredictiveBack = enabled
        prefs.edit().putBoolean(KEY_PREDICTIVE_BACK, enabled).apply()
    }

    fun updateEnableSwipeBack(enabled: Boolean) {
        enableSwipeBack = enabled
        prefs.edit().putBoolean(KEY_SWIPE_BACK, enabled).apply()
    }

    /**
     * 计算传给 Miuix ThemeController 的 ColorSchemeMode 编码（对齐 KernelSU MiuixTheme）。
     */
    val effectiveColorMode: Int
        get() = if (monetEnabled) {
            when (darkMode) {
                1 -> 4 // MonetLight
                2 -> 5 // MonetDark
                else -> 3 // MonetSystem
            }
        } else {
            when (darkMode) {
                1 -> 1 // Light
                2 -> 2 // Dark
                else -> 0 // System
            }
        }
}

/** 预测性返回只在 Android 14 及以上可用：13 上 ROM 的兼容回调会与自建回调互相触发。 */
internal val supportsPredictiveBack: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

/**
 * 注册常驻的 OnBackInvokedCallback，把系统返回转发进 [dispatcher]。
 *
 * 不能用 navigationevent 的 OnBackInvokedInput：它只在存在启用中的 handler 时注册，
 * 根页面或返回处理器短暂为空时会注销，让位给 framework 的 CompatOnBackInvokedCallback，
 * 后者把返回重新合成成 BACK 键事件，合成事件又被 NativePreImeInputStage 拦回
 * onBackInvoked，形成自循环，返回键事件在输入阶段持续堆积直至 ANR。
 *
 * @return 注册的回调与它投喂的 dispatcher 输入，供调用方在销毁时成对注销。
 */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun registerPredictiveBackInput(
    activity: ComponentActivity,
    dispatcher: NavigationEventDispatcher,
): Pair<OnBackInvokedCallback, DirectNavigationEventInput> {
    val input = DirectNavigationEventInput()
    dispatcher.addInput(input, NavigationEventDispatcher.PRIORITY_DEFAULT)
    val callback = object : OnBackAnimationCallback {
        override fun onBackStarted(backEvent: BackEvent) {
            input.backStarted(backEvent.toNavigationEvent())
        }

        override fun onBackProgressed(backEvent: BackEvent) {
            input.backProgressed(backEvent.toNavigationEvent())
        }

        override fun onBackInvoked() {
            input.backCompleted()
        }

        override fun onBackCancelled() {
            input.backCancelled()
        }
    }
    activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
        OnBackInvokedDispatcher.PRIORITY_DEFAULT,
        callback,
    )
    return callback to input
}

/**
 * XMillet 全局 Miuix 主题包装器（复刻 KernelSU MiuixTheme 实现，支持莫奈壁纸取色、15 色种子、PaletteStyle 与 ColorSpec）。
 */
@Composable
fun XMilletMiuixTheme(
    themeState: UiThemeState,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val colorMode = themeState.effectiveColorMode
    val isDark = isInDarkTheme(colorMode, systemDark)

    val dynamicSeedColor: Color? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (themeState.keyColor == 0) {
            val dynamicScheme = if (isDark) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            dynamicScheme.primary
        } else {
            Color(themeState.keyColor)
        }
    } else if (themeState.keyColor != 0) {
        Color(themeState.keyColor)
    } else {
        Color(0xFF2196F3)
    }

    val controller = remember(
        colorMode,
        dynamicSeedColor,
        isDark,
        themeState.paletteStyle,
        themeState.colorSpec
    ) {
        when (colorMode) {
            1 -> ThemeController(ColorSchemeMode.Light)
            2 -> ThemeController(ColorSchemeMode.Dark)
            3 -> ThemeController(
                ColorSchemeMode.MonetSystem,
                keyColor = dynamicSeedColor,
                isDark = isDark,
                paletteStyle = themeState.paletteStyle,
                colorSpec = themeState.colorSpec,
            )
            4 -> ThemeController(
                ColorSchemeMode.MonetLight,
                keyColor = dynamicSeedColor,
                isDark = false,
                paletteStyle = themeState.paletteStyle,
                colorSpec = themeState.colorSpec,
            )
            5 -> ThemeController(
                ColorSchemeMode.MonetDark,
                keyColor = dynamicSeedColor,
                isDark = true,
                paletteStyle = themeState.paletteStyle,
                colorSpec = themeState.colorSpec,
            )
            else -> ThemeController(ColorSchemeMode.System)
        }
    }

    MiuixTheme(controller = controller) {
        LaunchedEffect(isDark) {
            val activity = context as? ComponentActivity ?: return@LaunchedEffect
            val statusBarStyle = if (isDark) {
                SystemBarStyle.dark(Color.Transparent.toArgb())
            } else {
                SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
            }
            val navigationBarStyle = if (isDark) {
                SystemBarStyle.dark(Color.Transparent.toArgb())
            } else {
                SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
            }
            activity.enableEdgeToEdge(
                statusBarStyle = statusBarStyle,
                navigationBarStyle = navigationBarStyle,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (activity as? Activity)?.window?.isNavigationBarContrastEnforced = false
            }
        }

        val view = LocalView.current
        val activity = context as? ComponentActivity
        // 只在首次组合时决定返回事件分发器的归属，并在 activity/view 不变期间保持稳定。
        // 旧实现每次重组都重新读取 LocalNavigationEventDispatcherOwner：首次组合后自建分发器会被
        // 挂到 decorView 上，下一次重组（例如切换外观开关）就会把它误判为「宿主已提供」，
        // 进而移除返回输入并清空 ViewTree 归属，导致返回手势直接退到桌面；
        // 再切一次开关又会新建分发器恢复功能，表现为返回首页随开关状态时好时坏。
        val (navOwner, ownsDispatcher) = remember(activity, view) {
            val existing = (activity as? NavigationEventDispatcherOwner)
                ?: view.findViewTreeNavigationEventDispatcherOwner()
            if (existing != null) {
                existing to false
            } else {
                // 没有任何处理器消费返回时，交回 Activity 的默认行为（结束当前页面）。
                val dispatcher = NavigationEventDispatcher(
                    onBackCompletedFallback = OnBackCompletedFallback { activity?.finish() }
                )
                val owner: NavigationEventDispatcherOwner = object : NavigationEventDispatcherOwner {
                    override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
                }
                owner to true
            }
        }
        if (ownsDispatcher) {
            DisposableEffect(navOwner, activity, view, themeState.enablePredictiveBack) {
                val targetView = activity?.window?.decorView ?: view
                if (targetView.findViewTreeNavigationEventDispatcherOwner() == null) {
                    targetView.setViewTreeNavigationEventDispatcherOwner(navOwner)
                }
                val dispatcher = navOwner.navigationEventDispatcher
                // Android 14 及以上且开关开启时才走 OnBackInvoked 通道：注册一个常驻回调
                // （而不是交给 navigationevent 的 OnBackInvokedInput，后者只在存在启用中的
                // handler 时才注册，空档期会让位给 framework 的 CompatOnBackInvokedCallback，
                // 该回调把返回重新合成成 BACK 键事件，又被 NativePreImeInputStage 拦回
                // onBackInvoked，形成自循环）。13 上没有预测动画，且 ROM 的兼容回调会与自建
                // 回调互相触发，因此始终走下面的 onBackPressed 通道。
                val predictiveInput =
                    if (activity != null && supportsPredictiveBack && themeState.enablePredictiveBack) {
                        registerPredictiveBackInput(activity, dispatcher)
                    } else {
                        null
                    }
                // 其余情况 framework 不会把返回键交给 OnBackInvoked 通道，需要自己把
                // onBackPressed 通道的事件转发成导航事件，浮层与 NavDisplay 才能继续仲裁。
                val legacyCallback = if (activity != null && predictiveInput == null) {
                    val bridge = DirectNavigationEventInput()
                    dispatcher.addInput(bridge, NavigationEventDispatcher.PRIORITY_DEFAULT)
                    val callback = object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() {
                            bridge.backCompleted()
                        }
                    }
                    activity.onBackPressedDispatcher.addCallback(activity, callback)
                    (bridge to callback)
                } else {
                    null
                }
                onDispose {
                    predictiveInput?.let { (callback, bridge) ->
                        runCatching { activity?.onBackInvokedDispatcher?.unregisterOnBackInvokedCallback(callback) }
                        runCatching { dispatcher.removeInput(bridge) }
                    }
                    legacyCallback?.let { (bridge, callback) ->
                        callback.remove()
                        runCatching { dispatcher.removeInput(bridge) }
                    }
                    if (targetView.findViewTreeNavigationEventDispatcherOwner() === navOwner) {
                        targetView.setViewTreeNavigationEventDispatcherOwner(null)
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides navOwner,
            LocalColorMode provides colorMode,
            LocalEnableBlur provides themeState.enableBlur,
            LocalEnableFloatingBottomBar provides themeState.enableFloatingBottomBar,
            LocalEnableFloatingBottomBarBlur provides themeState.enableFloatingBottomBarBlur,
            LocalEnableProgressiveBlur provides themeState.enableProgressiveBlur,
        ) {
            content()
        }
    }
}
