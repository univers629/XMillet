package io.github.univers629.millet.ui
import androidx.compose.runtime.SideEffect
import io.github.univers629.millet.ui.util.PopupBackdropHolder

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.univers629.millet.BuildConfig
import io.github.univers629.millet.core.AppUidUtils
import io.github.univers629.millet.core.CloudListCodec
import io.github.univers629.millet.core.Logger
import io.github.univers629.millet.core.OobeManager
import io.github.univers629.millet.core.BatteryPolicyHelper
import io.github.univers629.millet.core.PrefReader
import io.github.univers629.millet.core.PrefsKeys
import io.github.univers629.millet.core.RootHelper
import io.github.univers629.millet.core.SETTING_MILLET_NO_RESTRICT_APP
import io.github.univers629.millet.ipc.PowerKeeperBinderClient
import io.github.univers629.millet.ui.component.AppUpdateDialogMiuix
import io.github.univers629.millet.ui.component.BottomBarMiuix
import io.github.univers629.millet.ui.component.BottomBarDestination
import io.github.univers629.millet.ui.screen.TombStatsScreenMiuix
import io.github.univers629.millet.ui.component.MainPagerState
import io.github.univers629.millet.ui.component.MiuixConfirmDialog
import io.github.univers629.millet.ui.component.rememberMainPagerState
import io.github.univers629.millet.ui.screen.AboutKnowledgeScreenMiuix
import io.github.univers629.millet.ui.screen.AboutLicensesScreenMiuix
import io.github.univers629.millet.ui.screen.AboutRepoScreenMiuix
import io.github.univers629.millet.ui.screen.AboutScreenMiuix
import io.github.univers629.millet.ui.screen.AppProfileScreenMiuix
import io.github.univers629.millet.ui.screen.AppsScreenMiuix
import io.github.univers629.millet.ui.screen.CloudWhitelistScreenMiuix
import io.github.univers629.millet.ui.screen.HomeScreenMiuix
import io.github.univers629.millet.ui.screen.InstalledAppUiModel
import io.github.univers629.millet.ui.screen.OobeScreenMiuix
import io.github.univers629.millet.ui.screen.PolicyTemplateDetailScreenMiuix
import io.github.univers629.millet.ui.screen.PolicyTemplatesScreenMiuix
import io.github.univers629.millet.ui.screen.ProcessListScreenMiuix
import io.github.univers629.millet.ui.screen.RunningProcessUiModel
import io.github.univers629.millet.ui.screen.SettingsScreenMiuix
import io.github.univers629.millet.ui.screen.ThemeSettingsScreenMiuix
import io.github.univers629.millet.ui.theme.LocalEnableBlur
import io.github.univers629.millet.ui.theme.LocalEnableFloatingBottomBar
import io.github.univers629.millet.ui.theme.LocalEnableFloatingBottomBarBlur
import io.github.univers629.millet.ui.theme.UiThemeState
import io.github.univers629.millet.ui.theme.XMilletMiuixTheme
import io.github.univers629.millet.ui.util.AppUpdateController
import io.github.univers629.millet.ui.util.AppUpdateDownloadState
import io.github.univers629.millet.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.pager.PagerDefaults.flingBehavior
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec

/**
 * XMillet 2.0 统一现代化主界面（基于 Miuix KMP 0.9.4）：
 * - 核心主页面（基于 HorizontalPager 物理弹簧平滑联动）：
 *   1. 主页（状态总览、核心服务连接检测、已冻结应用管理、环境参数、活跃进程概览）
 *   2. 进程列表（活跃应用与系统进程扫描、冻结状态、内存占用）
 *   3. 墓碑统计（可选显示：冻结、唤醒原因、时段分布、拦截统计与单应用明细）
 *   4. 应用策略（7 大分类胶囊过滤、Miuix 胶囊搜索栏、下拉刷新、应用卡片列表）
 *   5. 设置与个性化（Millet 墓碑冻结策略、Doze 深度睡眠、Miuix 莫奈调色板、悬浮底栏、备份恢复）
 * - 平滑二级子页面（接入 Miuix NavDisplay 连续栈深度与系统预测返回手势，支持全屏侧滑平滑返回）：
 *   - 单应用策略配置页 (AppProfileScreenMiuix)
 *   - 个性化主题页 (ThemeSettingsScreenMiuix)
 *   - 权限向导页 (OobeScreenMiuix)
 *   - 关于与文档页群 (AboutScreenMiuix 等)
 */
class MainActivity : ComponentActivity() {

    private sealed interface Route : NavKey {
        data object Main : Route
        data class AppProfile(val app: InstalledAppUiModel) : Route
        data object PolicyTemplates : Route
        data class PolicyTemplateDetail(val templateId: String) : Route
        data object CloudWhitelist : Route
        data object Appearance : Route
        data object Oobe : Route
        data object About : Route
        data object AboutKnowledge : Route
        data object AboutRepo : Route
        data object AboutLicenses : Route
    }

    private class Navigator(
        val backStack: NavBackStack,
    ) {
        fun push(key: NavKey) {
            if (key !in backStack) {
                backStack.add(key)
            }
        }

        fun pop() {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }

        fun current() = backStack.lastOrNull()
    }

    @Composable
    private fun rememberNavigator(startRoute: Route): Navigator {
        val backStack = remember { navBackStackOf(startRoute) }
        return remember(backStack) { Navigator(backStack) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        val prefs = PrefReader.openUiPrefs(this)
        migrateRemovedThemedIconAlias(prefs)
        val themeState = UiThemeState(prefs)

        // 尝试建立跨进程双向 Binder 连接
        PowerKeeperBinderClient.bindFromApp(this)

        setContent {
            XMilletMiuixTheme(themeState = themeState) {
                MainActivityContent(themeState = themeState)
            }
        }
    }

    /**
     * 旧版本提供过一个「桌面图标取色」开关，原理只是把启动器别名从 MainActivityAlias 切到
     * MainActivityAliasThemed。该别名已随取色开关一并删除，若用户此前开着它，升级后默认别名
     * 仍是被禁用的，桌面图标会凭空消失。这里做一次迁移：把图标补回默认别名，并清掉旧键。
     */
    private fun migrateRemovedThemedIconAlias(prefs: SharedPreferences) {
        val alias = ComponentName(this, "$packageName.ui.MainActivityAlias")
        val legacyAlias = ComponentName(this, "$packageName.ui.MainActivityAliasThemed")
        val aliasState = packageManager.getComponentEnabledSetting(alias)
        val legacyState = packageManager.getComponentEnabledSetting(legacyAlias)
        val legacyToggle = prefs.getBoolean(KEY_THEMED_ICON_LEGACY, false)
        val legacyAliasEnabled = legacyState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        // 旧设备可能停在「默认别名被停用、主题别名被启用」的状态：升级后主题别名已不再是功能，
        // 若不再处理，桌面将没有图标。这里把状态迁回默认别名，并停用遗留别名。
        if ((legacyToggle || legacyAliasEnabled) &&
            aliasState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        ) {
            packageManager.setComponentEnabledSetting(
                alias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        if (legacyAliasEnabled) {
            packageManager.setComponentEnabledSetting(
                legacyAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        if (prefs.getBoolean(KEY_THEMED_ICON_MIGRATED, false) && !legacyToggle) return
        prefs.edit()
            .remove(KEY_THEMED_ICON_LEGACY)
            .putBoolean(KEY_THEMED_ICON_MIGRATED, true)
            .apply()
    }

    @Composable
    private fun MainActivityContent(themeState: UiThemeState) {
        val coroutineScope = rememberCoroutineScope()
        var processFilterIndex by rememberSaveable { mutableIntStateOf(0) }
        val navigator = rememberNavigator(Route.Main)

        // Pager 状态：页序由可见底栏目的地决定（墓碑统计可在设置中隐藏），下标一律经由 destinations 换算
        val destinations = remember(themeState.showTombStatsTab) {
            BottomBarDestination.visible(themeState.showTombStatsTab)
        }
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { destinations.size })
        val mainPagerState =
            rememberMainPagerState(pagerState = pagerState, animatePageChanges = true)
        val navigateTo: (BottomBarDestination) -> Unit = { dest ->
            val idx = destinations.indexOf(dest)
            if (idx >= 0) mainPagerState.animateToPage(idx)
        }

        // 墓碑统计 Tab 被隐藏/恢复时，保持停留在当前目的地（例如设置页不跳页）；若当前目的地已不可见则回到主页
        var activeDestination by remember { mutableStateOf(BottomBarDestination.Home) }
        val settledPage = mainPagerState.pagerState.settledPage
        val currentPage = mainPagerState.pagerState.currentPage
        LaunchedEffect(settledPage) {
            destinations.getOrNull(settledPage)?.let { activeDestination = it }
        }
        LaunchedEffect(destinations) {
            val target = destinations.indexOf(activeDestination).let { if (it >= 0) it else 0 }
            if (pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
            mainPagerState.syncPage()
        }
        LaunchedEffect(currentPage) {
            mainPagerState.syncPage()
        }

        // 主界面 Tab 返回首页拦截（经共享 NavigationEventDispatcher 仲裁，让位于已打开的 Miuix 浮层）
        val mainBackEventState = rememberNavigationEventState(NavigationEventInfo.None)
        NavigationBackHandler(
            state = mainBackEventState,
            isBackEnabled = navigator.backStack.size == 1 && mainPagerState.selectedPage != 0,
            onBackCompleted = { mainPagerState.animateToPage(0) },
        )

        var runtimeStatus by remember {
            mutableStateOf(PowerKeeperBinderClient.RuntimeStatusSnapshot())
        }
        var runningProcesses by remember {
            mutableStateOf<List<RunningProcessUiModel>>(emptyList())
        }
        var installedApps by remember {
            mutableStateOf<List<InstalledAppUiModel>>(emptyList())
        }
        var isAppsRefreshing by remember { mutableStateOf(false) }
        // 云名单是否已经真正读到：模块侧上报的云名单可能晚于首屏到达，
        // 首屏读云库会读到空，需要在模块上报后补读一次。
        var cloudConfigApplied by remember { mutableStateOf(false) }
        var resolvedCloudLists by remember { mutableStateOf<CloudListCodec.CloudLists?>(null) }
        var dualAppsSyncedFromBinder by remember { mutableStateOf(false) }
        // 是否已经成功问过一次系统服务；用于区分「还没问」和「问了但没有模块」
        var systemServiceProbed by remember { mutableStateOf(false) }
        var showSystemApps by rememberSaveable { mutableStateOf(false) }
        var configVersion by remember { mutableIntStateOf(0) }
        var policyTemplateVersion by remember { mutableIntStateOf(0) }

        // 开箱引导（OOBE / Provision）完成状态检查（首次安装/启动时强制进入权限向导）
        var isProvisioned by rememberSaveable {
            mutableStateOf(OobeManager.isProvisioned(this@MainActivity))
        }

        var updateNoticeDialog by remember { mutableStateOf(false) }

        // 注册来自 system_server / PowerKeeper 的异步消息回调
        DisposableEffect(Unit) {
            PowerKeeperBinderClient.onAppUpdateNoticeReceived = {
                updateNoticeDialog = true
            }
            onDispose {
                PowerKeeperBinderClient.onAppUpdateNoticeReceived = null
            }
        }

        var isProcessesRefreshing by remember { mutableStateOf(false) }
        // 首次采集完成前不误报「快照不可用」，仅在真实查询失败或返回空快照对象时才展示提示横幅。
        var processSnapshotUnavailable by remember { mutableStateOf(false) }
        // 同一页面只允许一个采集任务；失败保留旧结果，不将连接故障解释成空快照。
        val refreshStatusAndRunningProcs: () -> Unit = {
            if (!isProcessesRefreshing) {
                isProcessesRefreshing = true
                coroutineScope.launch {
                    try {
                        val status = withContext(Dispatchers.IO) {
                            if (!PowerKeeperBinderClient.pingAlive()) {
                                PowerKeeperBinderClient.bindFromApp(this@MainActivity)
                            }
                            PowerKeeperBinderClient.queryRuntimeStatus()
                        }
                        runtimeStatus = status
                        systemServiceProbed = true

                        val procs = withContext(Dispatchers.IO) {
                            val pm = packageManager
                            val byPkgUid =
                                LinkedHashMap<String, MutableList<RootHelper.ProcessRawEntry>>()

                            // 进程快照只取 system_server 侧：它本来就有权限读 /proc，App 不需要 root。
                            // 采集失败保留旧结果，不在后台拉起 su 触发授权弹窗。
                            val snapshot = PowerKeeperBinderClient.queryProcessSnapshot()
                                ?: return@withContext null
                            for (raw in snapshot.entries) {
                                // 未确认所属包的进程保留在快照中，不构造应用分组。
                                if (raw.packageName.isBlank()) continue
                                byPkgUid.getOrPut("${raw.packageName}:${raw.uid}") { ArrayList() }
                                    .add(raw)
                            }

                            if (byPkgUid.isNotEmpty()) {
                                byPkgUid.values.mapNotNull { rawEntries ->
                                    val first = rawEntries.firstOrNull() ?: return@mapNotNull null
                                    val pkg = first.packageName
                                    val uid = first.uid
                                    if (pkg == packageName) return@mapNotNull null
                                    if (!AppUidUtils.isManagedAppUid(uid, includeDualApp = true)) {
                                        return@mapNotNull null
                                    }
                                    val appInfo =
                                        runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()

                                    val label = appInfo?.let {
                                        runCatching {
                                            pm.getApplicationLabel(it).toString().trim()
                                        }.getOrNull()
                                    }.orEmpty().ifEmpty { pkg }
                                    val pids = rawEntries.map { it.pid }
                                    val totalMemKb = rawEntries.sumOf { it.rssKb }
                                    val fTypes = rawEntries.map { it.freezerType }
                                        .filter { it != RootHelper.FreezerType.UNKNOWN }.toSet()
                                    val kernelFrozenCount =
                                        rawEntries.count { it.freezeState == RootHelper.FreezeState.FROZEN }
                                    val unknownCount =
                                        rawEntries.count { it.freezeState == RootHelper.FreezeState.UNKNOWN }

                                    RunningProcessUiModel(
                                        appName = label,
                                        packageName = pkg,
                                        uid = uid,
                                        pids = pids,
                                        isFrozen = kernelFrozenCount == rawEntries.size,
                                        freezerTypes = fTypes,
                                        processCount = rawEntries.size,
                                        frozenCount = kernelFrozenCount,
                                        unknownCount = unknownCount,
                                        memSizeKb = totalMemKb,
                                        formattedMemSize = RootHelper.formatMemSize(totalMemKb),
                                        isDualApp = AppUidUtils.isDualAppUid(uid),
                                        applicationInfo = appInfo
                                    )
                                }.sortedWith(
                                    compareByDescending<RunningProcessUiModel> { it.isFrozen }
                                        .thenByDescending { it.memSizeKb }
                                        .thenBy { it.appName.lowercase() }
                                )
                            } else {
                                emptyList()
                            }
                        }
                        if (procs != null) runningProcesses = procs
                        processSnapshotUnavailable = procs == null
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        processSnapshotUnavailable = true
                        Logger.error("刷新进程快照失败", error)
                    } finally {
                        isProcessesRefreshing = false
                    }
                }
            }
        }

        // 加载已安装应用策略
        var appLoadGeneration by remember { mutableStateOf(0L) }
        val loadInstalledApps = {
            appLoadGeneration += 1L
            val generation = appLoadGeneration
            isAppsRefreshing = true
            coroutineScope.launch {
                try {
                    val (apps, loadedCloudLists, dualLoaded) = withContext(Dispatchers.IO) {
                        val sp = PrefReader.openUiPrefs(this@MainActivity)
                        val whiteSet = PrefReader.readUiStringSet(sp, PrefsKeys.WHITE_APP_SET)
                        val msgSet = PrefReader.readUiStringSet(sp, PrefsKeys.MESSAGE_APP_SET)
                        val forceSet = PrefReader.readUiStringSet(sp, PrefsKeys.FORCE_FREEZE_SET)
                        val blockThawSet = PrefReader.readUiStringSet(sp, PrefsKeys.BLOCK_THAW_SET)
                        val forbidSyncSet =
                            PrefReader.readUiStringSet(sp, PrefsKeys.FORBID_SYNC_BINDER_SET)
                        val fgsSet =
                            PrefReader.readUiStringSet(sp, PrefsKeys.NOT_FREEZE_WHEN_HAS_FGS_SET)

                        val pm = packageManager
                        // 优先用 powerkeeper 通过官方 GlobalFeatureConfigureHelper 读到的云名单：
                        // 那是系统自己给这三条名单准备的入口，不依赖 Root、也不依赖数据库路径。
                        // 只有模块侧还没上报（首次开机、HotReload 期间）才退回 App 自读数据库的兜底。
                        val wireCloud = runtimeStatus.cloudLists
                        val cloudConfig = if (wireCloud != null && !wireCloud.isEmpty) {
                            io.github.univers629.millet.core.CloudConfigReader.CloudConfig(
                                dozeWhiteList = wireCloud.dozeWhiteList,
                                frozenNewWhiteList = wireCloud.frozenNewWhiteList,
                                launchRestrict = wireCloud.launchRestrict,
                            )
                        } else if (systemServiceProbed && !runtimeStatus.binderAlive) {
                            // 模块不存在（没人通过官方接口上报）时才自读数据库；这条路要 root。
                            io.github.univers629.millet.core.CloudConfigReader.load(
                                this@MainActivity,
                                force = true
                            )
                        } else {
                            // 系统服务在、但 PowerKeeper 还没上报：先用空名单，等上报后自动重建列表。
                            // 不再为了一份名单去起 root shell。
                            io.github.univers629.millet.core.CloudConfigReader.CloudConfig()
                        }

                        @Suppress("DEPRECATION")
                        val installedList = pm.getInstalledApplications(0)

                        // 「电池白名单」不落盘、也不写 prefs：每次加载都以系统实际状态为准，
                        // 这样用户在系统设置里改成「无限制」再回来立刻能看到标签，关掉也不会残留。
                        // 数据源是 PowerKeeper 生成的 Settings.System.MILLET_NO_RESTRICT_APP ——
                        // Greezer 真正认的「无限制」名单就是它；AOSP deviceidle 白名单与它不同步，
                        // 用 PowerManager.isIgnoringBatteryOptimizations() 会得到假阴性。
                        // 该值只含用户主动设置过的应用，天然不含系统预置包。
                        val systemUnrestricted = runCatching {
                            android.provider.Settings.System.getString(
                                contentResolver,
                                SETTING_MILLET_NO_RESTRICT_APP
                            )
                        }.getOrNull()
                            ?.split(',')
                            ?.map { it.trim() }
                            ?.filter { it.isNotEmpty() }
                            ?.toHashSet()
                            ?: HashSet()

                        val result = ArrayList<InstalledAppUiModel>(installedList.size)
                        val primaryInfoByPkg = HashMap<String, ApplicationInfo>(installedList.size)
                        val seenKeys = HashSet<String>(installedList.size)

                        fun buildAppModel(
                            pkg: String,
                            uid: Int,
                            info: ApplicationInfo?,
                            isSys: Boolean,
                        ): InstalledAppUiModel {
                            val label = info?.let {
                                runCatching {
                                    pm.getApplicationLabel(it).toString()
                                }.getOrNull()
                            } ?: pkg
                            val pInfo = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
                            val verCode =
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                    pInfo?.longVersionCode ?: 0L
                                } else {
                                    @Suppress("DEPRECATION")
                                    (pInfo?.versionCode ?: 0).toLong()
                                }
                            return InstalledAppUiModel(
                                appName = label,
                                packageName = pkg,
                                uid = uid,
                                versionName = pInfo?.versionName.orEmpty(),
                                versionCode = verCode,
                                isSystem = isSys,
                                applicationInfo = info,
                                inWhiteSet = AppUidUtils.containsPolicyTarget(whiteSet, pkg, uid),
                                inMessageSet = AppUidUtils.containsPolicyTarget(msgSet, pkg, uid),
                                inForceFreezeSet = AppUidUtils.containsPolicyTarget(
                                    forceSet,
                                    pkg,
                                    uid
                                ),
                                inBlockThawSet = AppUidUtils.containsPolicyTarget(
                                    blockThawSet,
                                    pkg,
                                    uid
                                ),
                                inForbidSyncSet = AppUidUtils.containsPolicyTarget(
                                    forbidSyncSet,
                                    pkg,
                                    uid
                                ),
                                inNotFreezeFgsSet = AppUidUtils.containsPolicyTarget(
                                    fgsSet,
                                    pkg,
                                    uid
                                ),
                                inBatteryWhiteSet = !AppUidUtils.isDualAppUid(uid) && pkg in systemUnrestricted,
                                inCloudDozeWhite = pkg in cloudConfig.dozeWhiteList,
                                inCloudFrozenWhite = pkg in cloudConfig.frozenNewWhiteList,
                                inCloudLaunchAllow = pkg in cloudConfig.launchRestrict
                            )
                        }

                        for (info in installedList) {
                            val pkg = info.packageName ?: continue
                            primaryInfoByPkg[pkg] = info
                            if (!seenKeys.add("$pkg:${info.uid}")) continue
                            val isSys = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            result.add(buildAppModel(pkg, info.uid, info, isSys))
                        }

                        // 单独列出双开空间 (user 999) 应用，与主应用分开展示与查看进程
                        val dualEntries = PowerKeeperBinderClient.queryDualApps()
                        val dualLoaded = dualEntries != null
                        if (dualEntries != null) {
                            for (entry in dualEntries) {
                                if (!seenKeys.add("${entry.packageName}:${entry.uid}")) continue
                                val info = primaryInfoByPkg[entry.packageName]
                                    ?: runCatching {
                                        pm.getApplicationInfo(
                                            entry.packageName,
                                            0
                                        )
                                    }.getOrNull()
                                val isSys = (entry.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                        ((info?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0
                                result.add(buildAppModel(entry.packageName, entry.uid, info, isSys))
                            }
                        }
                        // 兜底补充运行中进程已观察到的双开 UID（例如模块刚热加载前未连上新事务）
                        for (proc in runningProcesses) {
                            if (!proc.isDualApp) continue
                            if (!seenKeys.add("${proc.packageName}:${proc.uid}")) continue
                            val info = primaryInfoByPkg[proc.packageName] ?: proc.applicationInfo
                            val isSys = ((info?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0
                            result.add(buildAppModel(proc.packageName, proc.uid, info, isSys))
                        }

                        result.sortWith(
                            compareByDescending<InstalledAppUiModel> { it.hasAnyConfig }
                                .thenBy { it.appName.lowercase() }
                                .thenBy { it.packageName }
                                .thenBy { it.isDualApp }
                        )
                        val loadedCloudLists = if (cloudConfig.isEmpty) {
                            null
                        } else {
                            CloudListCodec.CloudLists(
                                dozeWhiteList = cloudConfig.dozeWhiteList,
                                frozenNewWhiteList = cloudConfig.frozenNewWhiteList,
                                launchRestrict = cloudConfig.launchRestrict,
                            )
                        }
                        Triple(result, loadedCloudLists, dualLoaded)
                    }
                    if (generation == appLoadGeneration) {
                        installedApps = apps
                        cloudConfigApplied = loadedCloudLists != null
                        if (loadedCloudLists != null) resolvedCloudLists = loadedCloudLists
                        if (dualLoaded) dualAppsSyncedFromBinder = true
                    }
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    Logger.error("加载应用策略失败", error)
                } finally {
                    if (generation == appLoadGeneration) isAppsRefreshing = false
                }
            }
        }

        LaunchedEffect(isProvisioned, runtimeStatus.binderAlive) {
            if (isProvisioned && runtimeStatus.binderAlive && !dualAppsSyncedFromBinder) {
                loadInstalledApps()
            }
        }

        // 云名单由 PowerKeeper 通过官方接口上报，开机后可能比首屏列表晚到；
        // 到齐后重建一次列表让三个云标签出现。这里用 snapshotFlow 事件等待，
        // 避免固定间隔轮询唤醒；同时不依赖 root 授权（云名单已改走跨进程接口）。
        LaunchedEffect(isProvisioned) {
            if (!isProvisioned) return@LaunchedEffect
            if (runtimeStatus.cloudLists == null) {
                withTimeoutOrNull(20_000L) {
                    snapshotFlow { runtimeStatus.cloudLists }.first { it != null }
                }
            }
            if (runtimeStatus.cloudLists != null && !cloudConfigApplied) {
                loadInstalledApps()
            }
        }

        // 按包名与 UID 打开应用策略页：优先用已加载的列表精确匹配 UID，否则按传入 UID 临时构造
        val openProfileByTarget: (String, Int) -> Unit = { pkg, targetUid ->
            val target = installedApps.find {
                it.packageName == pkg && (targetUid <= 0 || it.uid == targetUid)
            }
            if (target != null) {
                navigator.push(Route.AppProfile(target))
            } else {
                val pm = packageManager
                val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
                val sp = PrefReader.openUiPrefs(this@MainActivity)
                val resolvedUid = if (targetUid > 0) targetUid else (appInfo?.uid ?: 0)
                navigator.push(
                    Route.AppProfile(
                        InstalledAppUiModel(
                            appName = label,
                            packageName = pkg,
                            uid = resolvedUid,
                            versionName = "",
                            versionCode = 0,
                            isSystem = ((appInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0,
                            applicationInfo = appInfo,
                            inWhiteSet = AppUidUtils.containsPolicyTarget(
                                PrefReader.readUiStringSet(
                                    sp,
                                    PrefsKeys.WHITE_APP_SET
                                ), pkg, resolvedUid
                            ),
                            inMessageSet = AppUidUtils.containsPolicyTarget(
                                PrefReader.readUiStringSet(
                                    sp,
                                    PrefsKeys.MESSAGE_APP_SET
                                ), pkg, resolvedUid
                            ),
                            inForceFreezeSet = AppUidUtils.containsPolicyTarget(
                                PrefReader.readUiStringSet(
                                    sp,
                                    PrefsKeys.FORCE_FREEZE_SET
                                ), pkg, resolvedUid
                            ),
                            inBlockThawSet = AppUidUtils.containsPolicyTarget(
                                PrefReader.readUiStringSet(
                                    sp,
                                    PrefsKeys.BLOCK_THAW_SET
                                ), pkg, resolvedUid
                            ),
                            inForbidSyncSet = AppUidUtils.containsPolicyTarget(
                                PrefReader.readUiStringSet(
                                    sp,
                                    PrefsKeys.FORBID_SYNC_BINDER_SET
                                ), pkg, resolvedUid
                            ),
                            inNotFreezeFgsSet = AppUidUtils.containsPolicyTarget(
                                PrefReader.readUiStringSet(
                                    sp,
                                    PrefsKeys.NOT_FREEZE_WHEN_HAS_FGS_SET
                                ), pkg, resolvedUid
                            ),
                            inBatteryWhiteSet = !AppUidUtils.isDualAppUid(resolvedUid) && BatteryPolicyHelper.isPackageNoRestrict(
                                this@MainActivity,
                                pkg
                            ),
                        )
                    )
                )
            }
        }

        // 回到前台时重新读一遍系统状态：用户可能刚在系统设置里改过「省电策略」，
        // 而电池白名单标签、进程冻结状态都以系统实际状态为准，不重读就会一直显示旧值。
        val lifecycleOwner = LocalLifecycleOwner.current
        // 前台可见性：状态轮询只在页面前台跑，退到后台就停，省电也省 Binder 往返。
        var isForeground by remember { mutableStateOf(true) }
        DisposableEffect(lifecycleOwner, isProvisioned) {
            if (!isProvisioned) return@DisposableEffect onDispose { }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        isForeground = true
                        refreshStatusAndRunningProcs()
                        loadInstalledApps()
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        isForeground = false
                    }

                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // 仅在已完成开箱向导（进入主界面）后才执行繁重的进程扫描与全系统应用加载，避免在 OOBE 进场动画期间争抢 CPU/IO
        val uiPrefs = remember { PrefReader.openUiPrefs(this@MainActivity) }
        val updateUiState by AppUpdateController.uiState.collectAsState()
        LaunchedEffect(isProvisioned) {
            if (isProvisioned) {
                refreshStatusAndRunningProcs()
                loadInstalledApps()
                AppUpdateController.checkOnAppLaunch(
                    prefs = uiPrefs,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    scope = coroutineScope,
                )
            }
        }

        // 周期刷新运行状态与进程快照（仅在主界面存活且在前台时运行）。
        // 进程快照必须跟着一起刷：冻结判定只采信内核证据，只刷 status 会让
        // 「已冻结 / 运行中」的徽章停在几秒前的状态，与详情页的实时结果对不上。
        // 沿用前台 5 秒请求间隔，快照由 system_server 采集，不触发 App su。
        LaunchedEffect(isProvisioned, isForeground) {
            if (!isProvisioned || !isForeground) return@LaunchedEffect
            while (true) {
                delay(if (activeDestination == BottomBarDestination.Processes) 5_000L else 15_000L)
                if (isForeground) refreshStatusAndRunningProcs()
            }
        }

        // 底部留白计算（根据是否启用悬浮底栏自适应）
        val enableBlur = LocalEnableBlur.current
        val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
        val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
        val surfaceColor = MiuixTheme.colorScheme.surface

        val bottomNavPadding =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding() +
                    if (enableFloatingBottomBar) 96.dp else 72.dp

        val blurBackdrop = rememberBlurBackdrop(enableBlur)
        val backdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
        val popupBackdrop = rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
        SideEffect { PopupBackdropHolder.current = if (enableBlur) popupBackdrop else null }

        // 根级 Miuix Scaffold 承载最外层 MiuixPopupHost，确保所有 Tab 的 Dialog / BottomSheet / ListPopup
        // 及其暗色遮罩统一渲染在 BottomBarMiuix 之上，且共享 insets 不重复叠加
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            // 模块更新提示弹窗
            if (updateNoticeDialog) {
                MiuixConfirmDialog(
                    show = true,
                    title = "模块已更新",
                    message = "检测到 XMillet 模块已更新，请重启系统框架以加载最新 Hook 逻辑。",
                    confirmText = "知道了",
                    dismissText = null,
                    onConfirm = { updateNoticeDialog = false },
                    onDismiss = { updateNoticeDialog = false }
                )
            }

            // 远端版本更新弹窗（每进程仅在打开应用时静默检查一次；避免与模块重启提示弹窗重叠）
            val availableRelease = updateUiState.availableRelease
            if (!updateNoticeDialog && updateUiState.dialogVisible && availableRelease != null) {
                AppUpdateDialogMiuix(
                    show = true,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    release = availableRelease,
                    downloadState = updateUiState.downloadState,
                    onDismiss = { AppUpdateController.dismissDialog() },
                    onIgnoreVersion = { AppUpdateController.ignoreCurrentRelease(uiPrefs) },
                    onOpenReleasePage = {
                        AppUpdateController.openReleasePage(this@MainActivity, availableRelease.htmlUrl)
                    },
                    onPrimaryAction = {
                        when (updateUiState.downloadState) {
                            is AppUpdateDownloadState.Ready ->
                                AppUpdateController.installReadyApk(this@MainActivity)
                            else ->
                                AppUpdateController.startDownload(this@MainActivity, coroutineScope)
                        }
                    },
                    onCancelDownload = { AppUpdateController.cancelDownload() },
                )
            }

            // 全局界面流转控制：严格对齐 HyperCeiler OS3 官方规范（enter_home_anim + provision_out_anim）
            Box(modifier = Modifier.fillMaxSize().then(if (enableBlur) Modifier.layerBackdrop(popupBackdrop) else Modifier)) {
                AnimatedContent(
                    targetState = isProvisioned,
                    transitionSpec = {
                        val os3SineInOut = CubicBezierEasing(0.37f, 0.0f, 0.63f, 1.0f)
                        (scaleIn(
                            initialScale = 1.3f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 130.5f)
                        ) + fadeIn(
                            animationSpec = tween(
                                durationMillis = 230,
                                delayMillis = 60,
                                easing = os3SineInOut
                            )
                        )).togetherWith(
                            fadeOut(
                                animationSpec = tween(durationMillis = 360, easing = os3SineInOut)
                            )
                        )
                    },
                    label = "ProvisionStateTransition"
                ) { provisioned ->
                    if (!provisioned) {
                        OobeScreenMiuix(
                            isPreview = false,
                            onComplete = {
                                OobeManager.setProvisioned(this@MainActivity, true)
                                isProvisioned = true
                            }
                        )
                    } else {
                        val swipeDismiss =
                            if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                                NavSwipeDirection.RightToLeft
                            } else {
                                NavSwipeDirection.LeftToRight
                            }

                        NavDisplay(
                            backStack = navigator.backStack,
                            effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
                            onBack = { navigator.pop() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 根路由：禁止 swipeDismiss 销毁主界面
                            entry<Route.Main>(swipeDismiss = NavSwipeDirection.None) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    HorizontalPager(
                                        state = mainPagerState.pagerState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (blurBackdrop != null) Modifier.layerBackdrop(
                                                    blurBackdrop
                                                ) else Modifier
                                            )
                                            .then(
                                                if (enableFloatingBottomBar && enableFloatingBottomBarBlur) Modifier.layerBackdrop(
                                                    backdrop
                                                ) else Modifier
                                            ),
                                        beyondViewportPageCount = (destinations.size - 1).coerceAtLeast(
                                            0
                                        ),
                                        overscrollEffect = null,
                                        flingBehavior = flingBehavior(
                                            state = mainPagerState.pagerState,
                                            snapAnimationSpec = PagerNavigationSpringSpec,
                                        ),
                                    ) { page ->
                                        val isCurrentPage = page == settledPage
                                        when (destinations.getOrNull(page)) {
                                            BottomBarDestination.Home -> HomeScreenMiuix(
                                                runtimeStatus = runtimeStatus,
                                                configuredRuleCount = installedApps.asSequence()
                                                    .filter { it.hasAnyConfig }
                                                    .map { "${it.packageName}:${it.uid}" }
                                                    .distinct()
                                                    .count(),
                                                runningProcesses = runningProcesses,
                                                bottomInnerPadding = bottomNavPadding,
                                                onRefresh = {
                                                    refreshStatusAndRunningProcs()
                                                    loadInstalledApps()
                                                },
                                                onThawAll = {
                                                    coroutineScope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            PowerKeeperBinderClient.thawAllFrozenUids()
                                                        }
                                                        refreshStatusAndRunningProcs()
                                                    }
                                                },
                                                onNavigateToTab = navigateTo,
                                                onNavigateToProcessList = { filterIndex ->
                                                    processFilterIndex = filterIndex
                                                    navigateTo(BottomBarDestination.Processes)
                                                    refreshStatusAndRunningProcs()
                                                },
                                                onOpenAbout = { navigator.push(Route.About) },
                                                onOpenAppProfile = { pkg, uid ->
                                                    openProfileByTarget(
                                                        pkg,
                                                        uid
                                                    )
                                                },
                                                snapshotUnavailable = processSnapshotUnavailable
                                            )

                                            BottomBarDestination.Processes -> ProcessListScreenMiuix(
                                                runningProcesses = runningProcesses,
                                                isRefreshing = isProcessesRefreshing,
                                                snapshotUnavailable = processSnapshotUnavailable,
                                                bottomInnerPadding = bottomNavPadding,
                                                filterIndex = processFilterIndex,
                                                onFilterIndexChange = { processFilterIndex = it },
                                                onRefresh = {
                                                    refreshStatusAndRunningProcs()
                                                },
                                                onThawAll = {
                                                    coroutineScope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            PowerKeeperBinderClient.thawAllFrozenUids()
                                                        }
                                                        refreshStatusAndRunningProcs()
                                                    }
                                                },
                                                onOpenAppProfile = { pkg, uid ->
                                                    openProfileByTarget(
                                                        pkg,
                                                        uid
                                                    )
                                                },
                                            )

                                            BottomBarDestination.TombStats -> TombStatsScreenMiuix(
                                                isActive = isCurrentPage && isForeground,
                                                serviceAlive = runtimeStatus.binderAlive,
                                                statsEnabled = themeState.showTombStatsTab,
                                                bottomInnerPadding = bottomNavPadding,
                                                onEnableStats = {
                                                    themeState.updateShowTombStatsTab(
                                                        true
                                                    )
                                                },
                                                onOpenAppProfile = { pkg, uid ->
                                                    openProfileByTarget(
                                                        pkg,
                                                        uid
                                                    )
                                                }
                                            )

                                            BottomBarDestination.Apps -> AppsScreenMiuix(
                                                apps = installedApps,
                                                isRefreshing = isAppsRefreshing,
                                                showSystemApps = showSystemApps,
                                                onToggleShowSystemApps = {
                                                    showSystemApps = !showSystemApps
                                                },
                                                onRefresh = { loadInstalledApps() },
                                                onOpenAppProfile = { app ->
                                                    navigator.push(Route.AppProfile(app))
                                                },
                                                bottomInnerPadding = bottomNavPadding,
                                                policyVersion = configVersion,
                                                templateVersion = policyTemplateVersion,
                                                cloudLists = runtimeStatus.cloudLists?.takeUnless { it.isEmpty }
                                                    ?: resolvedCloudLists,
                                                onOpenTemplates = {
                                                    navigator.push(Route.PolicyTemplates)
                                                },
                                                onOpenCloudWhitelist = {
                                                    navigator.push(Route.CloudWhitelist)
                                                }
                                            )

                                            BottomBarDestination.Settings -> SettingsScreenMiuix(
                                                themeState = themeState,
                                                configVersion = configVersion,
                                                bottomInnerPadding = bottomNavPadding,
                                                updateStatus = updateUiState.status,
                                                availableUpdateVersion = updateUiState.availableRelease?.versionName,
                                                onCheckForUpdates = {
                                                    AppUpdateController.checkManually(
                                                        context = this@MainActivity,
                                                        currentVersionCode = BuildConfig.VERSION_CODE,
                                                        currentVersionName = BuildConfig.VERSION_NAME,
                                                        scope = coroutineScope,
                                                    )
                                                },
                                                onOpenAppearance = { navigator.push(Route.Appearance) },
                                                onOpenOobe = { navigator.push(Route.Oobe) },
                                                onOpenAbout = { navigator.push(Route.About) },
                                                onConfigImportedOrReset = {
                                                    themeState.syncTombStatsFromPrefs()
                                                    configVersion++
                                                    loadInstalledApps()
                                                    refreshStatusAndRunningProcs()
                                                }
                                            )

                                            null -> Unit
                                        }
                                    }

                                    // 底部 Miuix 导航栏（与 MainPagerState 物理弹簧联动）
                                    BottomBarMiuix(
                                        mainState = mainPagerState,
                                        blurBackdrop = blurBackdrop,
                                        backdrop = backdrop,
                                        destinations = destinations,
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    )
                                }
                            }

                            // 二级子页面交由 NavDisplay 统一处理系统预测返回与侧滑退栈，保证页面内浮层可优先拦截返回键
                            entry<Route.Appearance>(swipeDismiss = swipeDismiss) {
                                ThemeSettingsScreenMiuix(
                                    themeState = themeState,
                                    onBack = { navigator.pop() }
                                )
                            }

                            entry<Route.AppProfile>(swipeDismiss = swipeDismiss) { key ->
                                AppProfileScreenMiuix(
                                    app = key.app,
                                    onBack = { navigator.pop() },
                                    onPolicyChanged = {
                                        configVersion++
                                        loadInstalledApps()
                                    },
                                )
                            }

                            entry<Route.PolicyTemplates>(swipeDismiss = swipeDismiss) {
                                PolicyTemplatesScreenMiuix(
                                    apps = installedApps,
                                    templateVersion = policyTemplateVersion,
                                    onBack = { navigator.pop() },
                                    onOpenTemplate = { templateId ->
                                        navigator.push(Route.PolicyTemplateDetail(templateId))
                                    },
                                    onTemplatesChanged = {
                                        policyTemplateVersion++
                                    },
                                    policyVersion = configVersion,
                                )
                            }

                            entry<Route.PolicyTemplateDetail>(swipeDismiss = swipeDismiss) { key ->
                                PolicyTemplateDetailScreenMiuix(
                                    templateId = key.templateId,
                                    apps = installedApps,
                                    templateVersion = policyTemplateVersion,
                                    onBack = { navigator.pop() },
                                    onOpenAppProfile = { app ->
                                        navigator.push(Route.AppProfile(app))
                                    },
                                    onPolicyChanged = {
                                        configVersion++
                                        loadInstalledApps()
                                    },
                                    onTemplatesChanged = {
                                        policyTemplateVersion++
                                    },
                                    policyVersion = configVersion,
                                )
                            }

                            entry<Route.CloudWhitelist>(swipeDismiss = swipeDismiss) {
                                CloudWhitelistScreenMiuix(
                                    cloudLists = runtimeStatus.cloudLists?.takeUnless { it.isEmpty }
                                        ?: resolvedCloudLists,
                                    apps = installedApps,
                                    isRefreshing = isAppsRefreshing || isProcessesRefreshing,
                                    onRefresh = {
                                        refreshStatusAndRunningProcs()
                                        loadInstalledApps()
                                    },
                                    onBack = { navigator.pop() },
                                    onOpenAppProfile = { app ->
                                        navigator.push(Route.AppProfile(app))
                                    },
                                    configVersion = configVersion,
                                    onConfigChanged = {
                                        configVersion++
                                    },
                                )
                            }

                            entry<Route.Oobe>(swipeDismiss = swipeDismiss) {
                                OobeScreenMiuix(
                                    isPreview = true,
                                    onBack = { navigator.pop() },
                                    onComplete = {
                                        mainPagerState.animateToPage(0)
                                        navigator.pop()
                                    }
                                )
                            }

                            entry<Route.About>(swipeDismiss = swipeDismiss) {
                                AboutScreenMiuix(
                                    onBack = { navigator.pop() },
                                    onOpenKnowledge = { navigator.push(Route.AboutKnowledge) },
                                    onOpenRepo = { navigator.push(Route.AboutRepo) },
                                    onOpenLicenses = { navigator.push(Route.AboutLicenses) }
                                )
                            }

                            entry<Route.AboutKnowledge>(swipeDismiss = swipeDismiss) {
                                AboutKnowledgeScreenMiuix(
                                    onBack = { navigator.pop() }
                                )
                            }

                            entry<Route.AboutRepo>(swipeDismiss = swipeDismiss) {
                                AboutRepoScreenMiuix(
                                    onBack = { navigator.pop() }
                                )
                            }

                            entry<Route.AboutLicenses>(swipeDismiss = swipeDismiss) {
                                AboutLicensesScreenMiuix(
                                    onBack = { navigator.pop() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 旧版「桌面图标取色」开关的持久化键，仅用于一次性迁移。 */
private const val KEY_THEMED_ICON_LEGACY = "ui_themed_icon_enabled"
private const val KEY_THEMED_ICON_MIGRATED = "ui_themed_icon_migration_done"
