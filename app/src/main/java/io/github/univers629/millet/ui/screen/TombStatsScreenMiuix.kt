package io.github.univers629.millet.ui.screen
import io.github.univers629.millet.ui.util.GlassOverlayListPopup
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import io.github.univers629.millet.ui.util.PopupBackdropHolder
import io.github.univers629.millet.ui.util.MilletTopAppBar
import io.github.univers629.millet.ui.util.MilletSmallTopAppBar
import io.github.univers629.millet.ui.util.BarIconButton

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.LruCache
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univers629.millet.BuildConfig
import io.github.univers629.millet.core.FreezeStatsSnapshotData
import io.github.univers629.millet.core.InterceptRule
import io.github.univers629.millet.core.StatsEventKind
import io.github.univers629.millet.core.StatsEventOrigin
import io.github.univers629.millet.core.StatsTimeBucket
import io.github.univers629.millet.core.StatsWindow
import io.github.univers629.millet.core.WakeCategory
import io.github.univers629.millet.ipc.PowerKeeperBinderClient
import io.github.univers629.millet.ipc.MilletIpcContract
import io.github.univers629.millet.ui.component.StatsAppIcon
import io.github.univers629.millet.ui.component.DropdownItem
import io.github.univers629.millet.ui.component.ListPopupDefaults
import io.github.univers629.millet.ui.component.MiuixConfirmDialog
import io.github.univers629.millet.ui.component.StatusTagMiuix
import io.github.univers629.millet.ui.component.WarningCard
import io.github.univers629.millet.ui.component.WarningLevel
import io.github.univers629.millet.ui.theme.LocalEnableBlur
import io.github.univers629.millet.ui.theme.isInDarkTheme
import io.github.univers629.millet.ui.util.BlurredBar
import io.github.univers629.millet.ui.util.StatsPackageMember
import io.github.univers629.millet.ui.util.StatsDetailSelection
import io.github.univers629.millet.ui.util.StatsQueryScope
import io.github.univers629.millet.ui.util.StatsRefreshGate
import io.github.univers629.millet.ui.util.resolveStatsDetail
import io.github.univers629.millet.ui.util.sortedStatsMemberPackages
import io.github.univers629.millet.ui.util.statsGroupLabel
import io.github.univers629.millet.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import io.github.univers629.millet.ui.util.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// ============================================================================================
// 1. UI 视图模型（基于 io.github.univers629.millet.core.* 纯 Kotlin 数据契约补充应用名与图标）
// ============================================================================================

@Immutable
data class AppFreezeStats(
    val uid: Int,
    val packageName: String,
    val freezeCount: Int,
    val wakeByCategory: Map<WakeCategory, Int>,
    val interceptByRule: Map<InterceptRule, Int>,
    val frozenMillis: Long,
    /** 该应用在窗口内被观测的时长（用于计算冻结率），后端给不出时为 0 */
    val observedMillis: Long,
    val protectedCount: Int = 0,
    val killBlockedCount: Int = 0,
    /** 原始 reason Top N（排查新理由用） */
    val topRawReasons: List<Pair<String, Int>> = emptyList(),
    // ---- 以下由 App 侧 PackageManager 解析填充 ----
    val label: String = packageName,
    val applicationInfo: ApplicationInfo? = null,
    val isSystem: Boolean = false,
    val isDualApp: Boolean = false,
    val members: List<StatsPackageMember> = emptyList(),
) {
    val backgroundWakeCount: Int get() = wakeByCategory.entries.sumOf { if (it.key.isBackground) it.value else 0 }
    val userOpenCount: Int get() = wakeByCategory[WakeCategory.USER_OPEN] ?: 0
    val interceptCount: Int get() = interceptByRule.values.sum()
    val frozenRatio: Float get() = if (observedMillis > 0) (frozenMillis.toFloat() / observedMillis).coerceIn(0f, 1f) else 0f
}

@Immutable
data class StatsRecentEvent(
    val timeMillis: Long,
    val uid: Int,
    val packageName: String,
    val kind: StatsEventKind,
    val category: WakeCategory? = null,
    val rule: InterceptRule? = null,
    val rawReason: String = "",
    val label: String = packageName,
    val applicationInfo: ApplicationInfo? = null,
    val isSystem: Boolean = false,
    val isDualApp: Boolean = false,
    val members: List<StatsPackageMember> = emptyList(),
    val origin: StatsEventOrigin = StatsEventOrigin.UNKNOWN,
)

@Immutable
data class FreezeStatsSnapshot(
    val window: StatsWindow,
    /** 统计起点（墙钟毫秒） */
    val sinceMillis: Long,
    val generatedAtMillis: Long,
    val freezeCount: Int,
    val wakeByCategory: Map<WakeCategory, Int>,
    val screenOffWakeCount: Int,
    /** 窗口内息屏总时长，用于计算「平均每 X 分钟一次」 */
    val screenOffMillis: Long,
    val interceptByRule: Map<InterceptRule, Int>,
    val protectedCount: Int,
    val killBlockedCount: Int,
    val frozenMillisTotal: Long,
    val buckets: List<StatsTimeBucket>,
    val apps: List<AppFreezeStats>,
    val recent: List<StatsRecentEvent>,
    /** system_server 内统计占用（字节），用于页脚透明度展示 */
    val memoryBytes: Long = 0,
    /** 降级说明：例如「本机缺少 dispatchThawEvent，唤醒次数由 thawUid 推断」 */
    val sourceNote: String? = null,
    val isSample: Boolean = false,
) {
    val backgroundWakeCount: Int get() = wakeByCategory.entries.sumOf { if (it.key.isBackground) it.value else 0 }
    val totalWakeCount: Int get() = wakeByCategory.values.sum()
    val interceptCount: Int get() = interceptByRule.values.sum()
    val isEmpty: Boolean
        get() = freezeCount == 0 &&
            totalWakeCount == 0 &&
            interceptCount == 0 &&
            protectedCount == 0 &&
            killBlockedCount == 0
}

private fun originLabel(origin: StatsEventOrigin): String = when (origin) {
    StatsEventOrigin.UNKNOWN -> "来源未确认"
    StatsEventOrigin.NATIVE -> "系统记录"
    StatsEventOrigin.MODULE -> "模块操作"
    StatsEventOrigin.LAUNCH_MODE -> "系统另一入口"
    StatsEventOrigin.SNAPSHOT -> "状态对照"
}

sealed interface StatsLoadState {
    data object Loading : StatsLoadState
    /** 模块未激活 */
    data object ServiceUnavailable : StatsLoadState
    /** 模块版本不支持统计事务码（后端未就绪 / 旧模块） */
    data object BackendUnsupported : StatsLoadState
    /** 墓碑统计开关已关闭 */
    data object Disabled : StatsLoadState
    data class Ready(val snapshot: FreezeStatsSnapshot) : StatsLoadState
}

// ============================================================================================
// 2. 数据仓库（后端接入边界）
// ============================================================================================

object FreezeStatsRepository {

    private data class ResolvedAppMeta(
        val packageName: String,
        val label: String,
        val applicationInfo: ApplicationInfo?,
        val isSystem: Boolean = false,
        val isDualApp: Boolean = false,
        val members: List<StatsPackageMember> = emptyList(),
    )

    suspend fun load(
        context: Context,
        window: StatsWindow,
        serviceAlive: Boolean,
        statsEnabled: Boolean,
    ): StatsLoadState = withContext(Dispatchers.IO) {
        if (!statsEnabled) return@withContext StatsLoadState.Disabled
        if (!serviceAlive) return@withContext StatsLoadState.ServiceUnavailable
        val raw = PowerKeeperBinderClient.queryFreezeStats(window)
            ?: return@withContext StatsLoadState.BackendUnsupported
        if (!raw.enabled) return@withContext StatsLoadState.Disabled
        StatsLoadState.Ready(resolveFromData(context, raw))
    }

    suspend fun reset(): Boolean = withContext(Dispatchers.IO) {
        PowerKeeperBinderClient.resetFreezeStats()
    }

    suspend fun loadSample(context: Context, window: StatsWindow): StatsLoadState =
        withContext(Dispatchers.IO) {
            StatsLoadState.Ready(resolveApps(context, FreezeStatsSampleData.build(context, window)))
        }

    private val labelCache = LruCache<String, Triple<String, ApplicationInfo?, Boolean>>(512)

    private fun resolvePackage(
        pm: android.content.pm.PackageManager,
        uid: Int,
        rawPkg: String,
        memberPackages: List<String>? = null,
    ): ResolvedAppMeta {
        val userId = if (uid >= 0) uid / 100_000 else 0
        val appId = if (uid >= 100_000) uid % 100_000 else uid
        val isDualApp = userId == 999
        val packages = sortedStatsMemberPackages(memberPackages)
        if (packages.size > 1) {
            val members = packages.map { pkg ->
                val cacheKey = "$uid:$pkg"
                val cached = labelCache.get(cacheKey) ?: run {
                    val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                    val label = info?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
                        ?.trim()?.ifEmpty { null } ?: pkg
                    Triple(label, info, info != null && (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                        .also { labelCache.put(cacheKey, it) }
                }
                StatsPackageMember(pkg, cached.first, cached.second)
            }
            val label = statsGroupLabel(members)
            return ResolvedAppMeta(
                packageName = "uid:$uid",
                label = if (userId > 0 && !isDualApp) "$label (用户$userId)" else label,
                applicationInfo = null,
                isSystem = members.all { it.applicationInfo?.let { info ->
                    (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } == true },
                isDualApp = isDualApp,
                members = members,
            )
        }
        val trimmedPkg = rawPkg.trim()
        val effectivePkg = if (trimmedPkg.isBlank() || trimmedPkg.startsWith("uid:")) {
            ""
        } else {
            trimmedPkg
        }
        if (effectivePkg.isEmpty()) {
            val fallbackLabel = when {
                appId in 90_000..99_999 -> "沙盒进程 · $uid"
                uid >= 0 -> "UID $uid"
                else -> "未知应用"
            }
            return ResolvedAppMeta(
                packageName = "uid:$uid",
                label = fallbackLabel,
                applicationInfo = null,
                isSystem = false,
                isDualApp = isDualApp,
            )
        }
        labelCache.get(effectivePkg)?.let { (baseLabel, info, isSys) ->
            val finalLabel = if (userId > 0 && !isDualApp) "$baseLabel (用户$userId)" else baseLabel
            return ResolvedAppMeta(effectivePkg, finalLabel, info, isSys, isDualApp)
        }
        val info = runCatching { pm.getApplicationInfo(effectivePkg, 0) }.getOrNull()
        val baseLabel = info?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
            ?.trim()?.ifEmpty { null } ?: effectivePkg
        val isSys = info != null && (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        labelCache.put(effectivePkg, Triple(baseLabel, info, isSys))
        val finalLabel = if (userId > 0 && !isDualApp) "$baseLabel (用户$userId)" else baseLabel
        return ResolvedAppMeta(effectivePkg, finalLabel, info, isSys, isDualApp)
    }

    private fun queryMemberPackages(uids: List<Int>): Map<Int, List<String>> = buildMap {
        for (batch in uids.filter { it >= 0 }.distinct().chunked(MilletIpcContract.MAX_UID_PACKAGES_QUERY)) {
            putAll(PowerKeeperBinderClient.queryUidPackages(batch).orEmpty())
        }
    }

    /** 将系统服务返回的纯 Kotlin 快照映射为 UI 模型并解析包名/应用名/图标 */
    fun resolveFromData(context: Context, raw: FreezeStatsSnapshotData): FreezeStatsSnapshot {
        val pm = context.packageManager
        val packagesByUid = queryMemberPackages(raw.apps.map { it.uid } + raw.recent.map { it.uid })
        val metadata = HashMap<Int, ResolvedAppMeta>()
        fun metaFor(uid: Int, pkg: String): ResolvedAppMeta = metadata.getOrPut(uid) {
            resolvePackage(pm, uid, pkg, packagesByUid[uid])
        }
        return FreezeStatsSnapshot(
            window = raw.window,
            sinceMillis = raw.sinceMillis,
            generatedAtMillis = raw.generatedAtMillis,
            freezeCount = raw.freezeCount,
            wakeByCategory = raw.wakeByCategory,
            screenOffWakeCount = raw.screenOffWakeCount,
            screenOffMillis = raw.screenOffMillis,
            interceptByRule = raw.interceptByRule,
            protectedCount = raw.protectedCount,
            killBlockedCount = raw.killBlockedCount,
            frozenMillisTotal = raw.frozenMillisTotal,
            buckets = raw.buckets,
            apps = raw.apps.map { a ->
                val meta = metaFor(a.uid, a.packageName)
                AppFreezeStats(
                    uid = a.uid,
                    packageName = meta.packageName,
                    freezeCount = a.freezeCount,
                    wakeByCategory = a.wakeByCategory,
                    interceptByRule = a.interceptByRule,
                    frozenMillis = a.frozenMillis,
                    observedMillis = a.observedMillis,
                    protectedCount = a.protectedCount,
                    killBlockedCount = a.killBlockedCount,
                    topRawReasons = a.topRawReasons,
                    label = meta.label,
                    applicationInfo = meta.applicationInfo,
                    isSystem = meta.isSystem,
                    isDualApp = meta.isDualApp,
                    members = meta.members,
                )
            },
            recent = raw.recent.map { e ->
                val meta = metaFor(e.uid, e.packageName)
                StatsRecentEvent(
                    timeMillis = e.timeMillis,
                    uid = e.uid,
                    packageName = meta.packageName,
                    kind = e.kind,
                    category = e.category,
                    rule = e.rule,
                    rawReason = e.rawReason,
                    origin = e.origin,
                    label = meta.label,
                    applicationInfo = meta.applicationInfo,
                    isSystem = meta.isSystem,
                    isDualApp = meta.isDualApp,
                    members = meta.members,
                )
            },
            memoryBytes = raw.memoryBytes,
            sourceNote = raw.sourceNote,
            isSample = false,
        )
    }

    /** 包名 → 应用名 + ApplicationInfo（线程安全 LRU 缓存，仅在 IO 线程调用） */
    fun resolveApps(context: Context, snapshot: FreezeStatsSnapshot): FreezeStatsSnapshot {
        val pm = context.packageManager
        val packagesByUid = if (snapshot.isSample) emptyMap() else {
            queryMemberPackages(snapshot.apps.map { it.uid } + snapshot.recent.map { it.uid })
        }
        val metadata = HashMap<Int, ResolvedAppMeta>()
        fun metaFor(uid: Int, pkg: String): ResolvedAppMeta = metadata.getOrPut(uid) {
            resolvePackage(pm, uid, pkg, packagesByUid[uid])
        }
        return snapshot.copy(
            apps = snapshot.apps.map { a ->
                val meta = metaFor(a.uid, a.packageName)
                a.copy(
                    packageName = meta.packageName,
                    label = meta.label,
                    applicationInfo = meta.applicationInfo,
                    isSystem = meta.isSystem,
                    isDualApp = meta.isDualApp,
                    members = meta.members,
                )
            },
            recent = snapshot.recent.map { e ->
                val meta = metaFor(e.uid, e.packageName)
                e.copy(
                    packageName = meta.packageName,
                    label = meta.label,
                    applicationInfo = meta.applicationInfo,
                    isSystem = meta.isSystem,
                    isDualApp = meta.isDualApp,
                    members = meta.members,
                )
            },
        )
    }
}

/** 仅用于开发期预览界面的示例数据（Debug 包可见，页面上有明显「示例数据」标识） */
private object FreezeStatsSampleData {
    fun build(context: Context, window: StatsWindow): FreezeStatsSnapshot {
        val rnd = Random(window.ordinal * 7919 + 17)
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val pkgs = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }
            .map { it.packageName }
            .sortedBy { it.hashCode() }
            .take(14)
            .ifEmpty { listOf("com.tencent.mm", "com.tencent.mobileqq", "com.taobao.taobao", "com.eg.android.AlipayGphone") }

        val scale = when (window) { StatsWindow.TODAY -> 1; StatsWindow.WEEK -> 6; StatsWindow.BOOT -> 2 }
        val windowMillis = when (window) {
            StatsWindow.TODAY -> 12 * 3_600_000L
            StatsWindow.WEEK -> 7 * 24 * 3_600_000L
            StatsWindow.BOOT -> 30 * 3_600_000L
        }
        val apps = pkgs.mapIndexed { i, pkg ->
            val heat = (pkgs.size - i).toFloat() / pkgs.size
            val cats = WakeCategory.entries.associateWith { c ->
                val base = when (c) {
                    WakeCategory.USER_OPEN -> 12; WakeCategory.NETWORK -> 90; WakeCategory.BINDER -> 40
                    WakeCategory.BROADCAST -> 25; WakeCategory.SCREEN_ON -> 10; WakeCategory.CHARGING -> 3
                    WakeCategory.OTHER -> 8
                    WakeCategory.MODULE_OPERATION -> 2; WakeCategory.LAUNCH_MODE -> 2
                }
                (base * heat * scale * rnd.nextFloat()).toInt()
            }
            val rules = InterceptRule.entries.associateWith { (20 * heat * scale * rnd.nextFloat() * rnd.nextFloat()).toInt() }
                .filterValues { it > 0 }
            AppFreezeStats(
                uid = 10100 + i,
                packageName = pkg,
                freezeCount = (60 * heat * scale * (0.5f + rnd.nextFloat())).toInt(),
                wakeByCategory = cats,
                interceptByRule = rules,
                frozenMillis = (windowMillis * (0.35f + 0.6f * rnd.nextFloat())).toLong(),
                observedMillis = windowMillis,
                protectedCount = if (rnd.nextInt(5) == 0) rnd.nextInt(1, 9) else 0,
                killBlockedCount = rnd.nextInt(0, 4),
                topRawReasons = listOf("PACKET" to rnd.nextInt(10, 90), "Sync Binder4" to rnd.nextInt(5, 40), "broadcast" to rnd.nextInt(1, 20)),
            )
        }
        val wake = WakeCategory.entries.associateWith { c -> apps.sumOf { it.wakeByCategory[c] ?: 0 } }
        val rules = InterceptRule.entries.associateWith { r -> apps.sumOf { it.interceptByRule[r] ?: 0 } }
        val buckets = when (window) {
            StatsWindow.TODAY -> (0 until 24).map { h ->
                val night = h < 7
                StatsTimeBucket(
                    label = "%02d".format(Locale.ROOT, h),
                    screenOnWakes = if (night) rnd.nextInt(0, 3) else rnd.nextInt(5, 40),
                    screenOffWakes = if (night) rnd.nextInt(3, 18) else rnd.nextInt(0, 10),
                )
            }
            StatsWindow.WEEK -> listOf("一", "二", "三", "四", "五", "六", "日").map {
                StatsTimeBucket("周$it", rnd.nextInt(150, 420), rnd.nextInt(40, 160))
            }
            StatsWindow.BOOT -> emptyList()
        }
        val now = System.currentTimeMillis()
        val recent = (0 until 30).map { i ->
            val app = apps[rnd.nextInt(apps.size)]
            val kind = listOf(StatsEventKind.FREEZE, StatsEventKind.THAW, StatsEventKind.INTERCEPT, StatsEventKind.THAW)[rnd.nextInt(4)]
            StatsRecentEvent(
                timeMillis = now - i * rnd.nextLong(20_000, 200_000),
                uid = app.uid,
                packageName = app.packageName,
                kind = kind,
                category = if (kind == StatsEventKind.THAW) WakeCategory.entries[rnd.nextInt(WakeCategory.entries.size)] else null,
                rule = if (kind == StatsEventKind.INTERCEPT) InterceptRule.entries[rnd.nextInt(3)] else null,
                rawReason = if (kind == StatsEventKind.FREEZE) "IMMOBULUS" else "PACKET",
            )
        }
        return FreezeStatsSnapshot(
            window = window,
            sinceMillis = now - windowMillis,
            generatedAtMillis = now,
            freezeCount = apps.sumOf { it.freezeCount },
            wakeByCategory = wake,
            screenOffWakeCount = buckets.sumOf { it.screenOffWakes }.takeIf { it > 0 } ?: (wake.values.sum() / 4),
            screenOffMillis = windowMillis / 2,
            interceptByRule = rules,
            protectedCount = apps.sumOf { it.protectedCount },
            killBlockedCount = apps.sumOf { it.killBlockedCount },
            frozenMillisTotal = apps.sumOf { it.frozenMillis },
            buckets = buckets,
            apps = apps,
            recent = recent,
            memoryBytes = 64 * 1024,
            isSample = true,
        )
    }
}

// ============================================================================================
// 3. 语义配色（自适应浅色 / 深色 / 莫奈动态取色）
// ============================================================================================

@Composable
private fun WakeCategory.displayColor(): Color {
    val dark = isInDarkTheme()
    return when (this) {
        WakeCategory.USER_OPEN -> if (dark) Color(0xFF4D94FF) else Color(0xFF2979FF)
        WakeCategory.NETWORK -> if (dark) Color(0xFFFF9F0A) else Color(0xFFF57C00)
        WakeCategory.BINDER -> if (dark) Color(0xFFBF5AF2) else Color(0xFF9C27B0)
        WakeCategory.BROADCAST -> if (dark) Color(0xFF5AC8FA) else Color(0xFF0288D1)
        WakeCategory.SCREEN_ON -> if (dark) Color(0xFFFFD60A) else Color(0xFFD99B00)
        WakeCategory.CHARGING -> if (dark) Color(0xFF30D158) else Color(0xFF2E9E49)
        WakeCategory.OTHER -> if (dark) Color(0xFF98989D) else Color(0xFF787880)
        WakeCategory.MODULE_OPERATION -> if (dark) Color(0xFF64D2FF) else Color(0xFF007B9E)
        WakeCategory.LAUNCH_MODE -> if (dark) Color(0xFFFFB86C) else Color(0xFFB36B00)
    }
}

@Composable
private fun screenOffAccentColor(): Color {
    // 息屏记录统一使用紫色（与深色模式同色），不再在主色偏紫时切换为琥珀橙：
    // 图标底色是 15% alpha 叠加在卡片上，浅色模式保持紫调、深色模式也不会出现浅色背景。
    return Color(0xFF7D7AFF)
}

@Composable
private fun statsSuccessColor(): Color = if (isInDarkTheme()) Color(0xFF30D158) else Color(0xFF2E9E49)

@Composable
private fun statsProtectColor(): Color = if (isInDarkTheme()) Color(0xFF5AC8FA) else Color(0xFF0288D1)

@Composable
private fun statsWarningTextColor(): Color = if (isInDarkTheme()) Color(0xFFFF9F0A) else Color(0xFFD97706)

// ============================================================================================
// 4. 页面主体
// ============================================================================================

private enum class RankMode(val label: String) {
    WAKE("后台唤醒"),
    FROZEN("冻结时间"),
    INTERCEPT("拦截最多"),
    SYSTEM_RECORDS("系统记录"),
}

/**
 * 墓碑统计页。
 *
 * @param isActive 当前页可见且 App 在前台；仅在为 true 时轮询（HorizontalPager 会预组合相邻页）
 * @param serviceAlive XMillet 系统服务是否连接
 * @param statsEnabled 墓碑统计是否开启
 * @param onEnableStats 在禁用态下点击「立即启用」的回调
 */
@Composable
fun TombStatsScreenMiuix(
    isActive: Boolean,
    serviceAlive: Boolean,
    statsEnabled: Boolean = true,
    bottomInnerPadding: Dp,
    onEnableStats: (() -> Unit)? = null,
    onOpenAppProfile: (packageName: String, uid: Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    var windowIndex by rememberSaveable { mutableIntStateOf(0) }
    val window = StatsWindow.entries[windowIndex]
    var rankModeIndex by rememberSaveable { mutableIntStateOf(0) }
    var useSample by rememberSaveable { mutableStateOf(false) }
    var state by remember { mutableStateOf<StatsLoadState>(StatsLoadState.Loading) }
    var lastGoodSnapshot by remember { mutableStateOf<FreezeStatsSnapshot?>(null) }
    var refreshFailed by remember { mutableStateOf(false) }
    val refreshGate = remember { StatsRefreshGate() }
    var refreshTick by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    var showTopPopup by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showMethodologyDialog by remember { mutableStateOf(false) }
    var detailSelection by remember { mutableStateOf<StatsDetailSelection?>(null) }
    val queryScope = StatsQueryScope(window, useSample)

    suspend fun loadOnce(): StatsLoadState {
        return try {
            if (useSample) {
                FreezeStatsRepository.loadSample(context, window)
            } else {
                FreezeStatsRepository.load(context, window, serviceAlive, statsEnabled)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            StatsLoadState.BackendUnsupported
        }
    }

    fun triggerManualRefresh() {
        if (isRefreshing) return
        isRefreshing = true
        refreshTick++
    }

    DisposableEffect(isActive, window, useSample, serviceAlive, statsEnabled, refreshTick) {
        onDispose { refreshGate.invalidate() }
    }

    // 仅在可见 + 前台时拉取；切换时间窗/开关或手动触发立即重拉；就绪后每 10 秒自动刷新
    LaunchedEffect(isActive, window, useSample, serviceAlive, statsEnabled, refreshTick) {
        if (!isActive) {
            isRefreshing = false
            return@LaunchedEffect
        }
        val ticket = refreshGate.begin(queryScope)
        val cached = lastGoodSnapshot?.takeIf { it.window == window && it.isSample == useSample }
        if (cached == null) {
            state = StatsLoadState.Loading
            refreshFailed = false
        } else {
            state = StatsLoadState.Ready(cached)
        }
        try {
            while (true) {
                var next = try {
                    loadOnce()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    StatsLoadState.BackendUnsupported
                }
                if (!useSample && statsEnabled && next is StatsLoadState.Disabled) {
                    delay(300)
                    next = loadOnce()
                }
                if (!refreshGate.accepts(ticket, queryScope)) return@LaunchedEffect
                if (next is StatsLoadState.Ready) {
                    lastGoodSnapshot = next.snapshot
                    state = next
                    refreshFailed = false
                } else {
                    val previous = lastGoodSnapshot?.takeIf { it.window == window && it.isSample == useSample }
                    if (statsEnabled && next !is StatsLoadState.Disabled && previous != null) {
                        state = StatsLoadState.Ready(previous)
                        refreshFailed = true
                    } else {
                        state = next
                        refreshFailed = false
                    }
                }
                isRefreshing = false
                if (useSample || next !is StatsLoadState.Ready) break
                delay(10_000)
            }
        } finally {
            if (refreshGate.accepts(ticket, queryScope)) isRefreshing = false
        }
    }

    if (showResetDialog) {
        MiuixConfirmDialog(
            show = true,
            title = "重置墓碑统计",
            message = "确定清空「今天」「近 7 天」与「开机以来」的全部统计计数与最近动态吗？此操作无法恢复。",
            confirmText = "确认重置",
            dismissText = "取消",
            onConfirm = {
                showResetDialog = false
                scope.launch {
                    val ok = FreezeStatsRepository.reset()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (ok) "已重置墓碑统计数据" else "重置失败：请确认系统服务连接",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    if (ok) {
                        lastGoodSnapshot = null
                        state = StatsLoadState.Loading
                        detailSelection = null
                        refreshFailed = false
                        refreshGate.invalidate()
                        refreshTick++
                    }
                }
            },
            onDismiss = { showResetDialog = false },
        )
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                MilletTopAppBar(
                    color = barColor,
                    title = "墓碑统计",
                    actions = {
                        BarIconButton(
                            modifier = Modifier.padding(end = 4.dp),
                            onClick = { triggerManualRefresh() },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = "刷新统计",
                                tint = colorScheme.onSurface,
                            )
                        }
                        BarIconButton(
                            modifier = Modifier.padding(end = 12.dp),
                            onClick = { showTopPopup = true },
                            holdDownState = showTopPopup,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.MoreCircle,
                                contentDescription = "更多操作",
                                tint = colorScheme.onSurface,
                            )
                        }
                        GlassOverlayListPopup(
                            show = showTopPopup,
                            popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showTopPopup = false },
                            content = {
                                val canReset = serviceAlive && statsEnabled && !useSample &&
                                    state !is StatsLoadState.BackendUnsupported &&
                                    state !is StatsLoadState.ServiceUnavailable
                                val menuItems = buildList {
                                    if (canReset) add("重置统计数据")
                                    add("统计口径说明")
                                    if (BuildConfig.DEBUG) {
                                        add(if (useSample) "退出示例数据" else "预览示例数据")
                                    }
                                }
                                ListPopupColumn {
                                    menuItems.forEachIndexed { idx, label ->
                                        DropdownItem(
                                            text = label,
                                            optionSize = menuItems.size,
                                            index = idx,
                                            onSelectedIndexChange = {
                                                showTopPopup = false
                                                when (label) {
                                                    "重置统计数据" -> showResetDialog = true
                                                    "统计口径说明" -> showMethodologyDialog = true
                                                    "预览示例数据" -> useSample = true
                                                    "退出示例数据" -> useSample = false
                                                }
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            PullToRefresh(
                isRefreshing = isRefreshing,
                pullToRefreshState = pullToRefreshState,
                onRefresh = { triggerManualRefresh() },
                contentPadding = PaddingValues(top = innerPadding.calculateTopPadding() + 6.dp),
                topAppBarScrollBehavior = scrollBehavior,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 4.dp,
                        bottom = bottomInnerPadding + 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    overscrollEffect = null,
                ) {
                    item(key = "window_tabs") {
                        TabRowWithContour(
                            tabs = StatsWindow.entries.map { it.label },
                            selectedTabIndex = windowIndex,
                            onTabSelected = { windowIndex = it },
                            colors = TabRowDefaults.tabRowColors(
                                backgroundColor = colorScheme.surfaceContainer,
                                contentColor = colorScheme.onSurfaceVariantSummary,
                                selectedBackgroundColor = colorScheme.secondaryContainer,
                                selectedContentColor = if (isDynamicColor) {
                                    colorScheme.onSecondaryContainer
                                } else {
                                    colorScheme.onSurface
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        )
                    }

                    when (val s = state.takeUnless {
                        it is StatsLoadState.Ready &&
                            (it.snapshot.window != window || it.snapshot.isSample != useSample)
                    } ?: StatsLoadState.Loading) {
                        StatsLoadState.Loading -> item(key = "state_loading") {
                            LoadingStateCard()
                        }

                        StatsLoadState.ServiceUnavailable -> item(key = "state_service_unavailable") {
                            StatsStateCard(
                                title = "统计服务未连接",
                                summary = "暂时读不到统计。请检查模块是否正常运行，再点重新检测。",
                                primaryLabel = "重新检测",
                                onPrimaryClick = { triggerManualRefresh() },
                                secondaryLabel = if (BuildConfig.DEBUG) "预览示例数据" else null,
                                onSecondaryClick = { useSample = true },
                            )
                        }

                        StatsLoadState.BackendUnsupported -> item(key = "state_backend_unsupported") {
                            StatsStateCard(
                                title = "暂时读不到统计",
                                summary = "系统没有返回统计数据，可以稍后重试。",
                                primaryLabel = "重新检测",
                                onPrimaryClick = { triggerManualRefresh() },
                                secondaryLabel = if (BuildConfig.DEBUG) "预览示例数据" else null,
                                onSecondaryClick = { useSample = true },
                            )
                        }

                        StatsLoadState.Disabled -> item(key = "state_disabled") {
                            StatsStateCard(
                                title = "墓碑统计采集已关闭",
                                summary = "开启后记录应用冻结、唤醒和拦截情况。统计会定期保存，方便下次继续查看。",
                                primaryLabel = if (onEnableStats != null) "立即启用统计" else null,
                                onPrimaryClick = {
                                    scope.launch {
                                        onEnableStats?.invoke()
                                        delay(350)
                                        refreshTick++
                                    }
                                },
                                secondaryLabel = if (BuildConfig.DEBUG) "预览示例数据" else null,
                                onSecondaryClick = { useSample = true },
                            )
                        }

                        is StatsLoadState.Ready -> {
                            val snap = s.snapshot
                            if (refreshFailed) {
                                item(key = "refresh_failed") {
                                    StatsStateCard(
                                        title = "更新失败，保留上次数据",
                                        summary = "上次更新于 ${formatClock(snap.generatedAtMillis)}，当前内容还没有更新。",
                                        primaryLabel = "重试",
                                        onPrimaryClick = { triggerManualRefresh() },
                                    )
                                }
                            }
                            if (snap.isSample) {
                                item(key = "sample_banner") {
                                    SampleBanner(onExit = { useSample = false })
                                }
                            }
                            if (snap.isEmpty) {
                                item(key = "state_empty") {
                                    StatsStateCard(
                                        title = "暂无统计数据",
                                        summary = "统计从 ${formatDateTime(snap.sinceMillis)} 开始，收到有效记录后会显示在这里。",
                                        primaryLabel = "刷新数据",
                                        onPrimaryClick = { triggerManualRefresh() },
                                    )
                                }
                            } else {
                                statsContent(
                                    snap = snap,
                                    rankModeIndex = rankModeIndex,
                                    onRankModeChange = { rankModeIndex = it },
                                    onTileRankClick = { idx ->
                                        rankModeIndex = idx
                                        val rankItemIndex = 5 +
                                            (if (snap.isSample) 1 else 0) +
                                            (if (refreshFailed) 1 else 0) + 2 +
                                            (if (snap.buckets.isNotEmpty()) 1 else 0)
                                        scope.launch { listState.animateScrollToItem(rankItemIndex) }
                                    },
                                    onOpenDetail = { detailSelection = StatsDetailSelection(it.uid, queryScope) },
                                )
                            }
                        }
                    }
                }
            }

            // 注意：OverlayBottomSheet 与 OverlayDialog 必须留在 Scaffold 内容作用域内才能挂载至 MiuixPopupHost
            AppStatsDetailSheet(
                app = (state as? StatsLoadState.Ready)?.snapshot?.let { snapshot ->
                    resolveStatsDetail(
                        detailSelection, queryScope, StatsQueryScope(snapshot.window, snapshot.isSample),
                        snapshot.apps, AppFreezeStats::uid,
                    )
                },
                updatedAtMillis = (state as? StatsLoadState.Ready)?.snapshot?.generatedAtMillis,
                refreshFailed = refreshFailed,
                onDismiss = { detailSelection = null },
                onOpenAppProfile = { app ->
                    detailSelection = null
                    onOpenAppProfile(app.packageName, app.uid)
                },
            )

            MethodologyDialog(
                show = showMethodologyDialog,
                onDismiss = { showMethodologyDialog = false },
            )
        }
    }
}

/** 统计主体：概览 → 息屏 → 原因 → 时段 → 拦截 → 排行 → 最近动态 → 页脚 */
private fun LazyListScope.statsContent(
    snap: FreezeStatsSnapshot,
    rankModeIndex: Int,
    onRankModeChange: (Int) -> Unit,
    onTileRankClick: (Int) -> Unit,
    onOpenDetail: (AppFreezeStats) -> Unit,
) {
    // 1. 2×2 概览（点按唤醒或拦截指标切换下方排行口径并滚动定位）
    item(key = "summary_grid") {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    title = "冻结次数",
                    value = formatCount(snap.freezeCount),
                    unit = "次",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    title = "后台唤醒",
                    value = formatCount(snap.backgroundWakeCount),
                    unit = "次",
                    modifier = Modifier.weight(1f),
                    onClick = { onTileRankClick(RankMode.WAKE.ordinal) },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    title = "拦截唤醒",
                    value = formatCount(snap.interceptCount),
                    unit = "次",
                    modifier = Modifier.weight(1f),
                    accent = true,
                    onClick = { onTileRankClick(RankMode.INTERCEPT.ordinal) },
                )
                val backgroundWakeAppCount = snap.apps.asSequence()
                    .filter { it.backgroundWakeCount > 0 }
                    .map { it.uid }
                    .distinct()
                    .count()
                StatTile(
                    title = "后台唤醒应用数",
                    value = formatCount(backgroundWakeAppCount),
                    unit = "个",
                    modifier = Modifier.weight(1f),
                    onClick = { onTileRankClick(RankMode.WAKE.ordinal) },
                )
            }
        }
    }

    item(key = "native_records") {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            insideMargin = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("系统记录", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                Text(
                    "冻结 ${formatCount(snap.freezeCount)} 次 · 解冻 ${formatCount(snap.totalWakeCount)} 次",
                    fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }

    // 2. 息屏唤醒高亮条
    item(key = "screen_off_banner") {
        ScreenOffBanner(snap)
    }

    // 3. 唤醒原因
    item(key = "wake_reasons") {
        SmallTitle(text = "系统解冻记录 · 原因")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            insideMargin = PaddingValues(16.dp),
        ) {
            WakeReasonSection(snap.wakeByCategory)
        }
    }

    // 4. 时段分布（开机以来不按分时展示）
    if (snap.buckets.isNotEmpty()) {
        item(key = "time_distribution") {
            SmallTitle(
                text = if (snap.window == StatsWindow.TODAY) "系统解冻记录 · 每小时" else "系统解冻记录 · 每天",
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                TimeDistributionSection(
                    window = snap.window,
                    buckets = snap.buckets,
                )
            }
        }
    }

    // 5. XMillet 干预
    item(key = "interventions") {
        SmallTitle(text = "XMillet 干预")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            insideMargin = PaddingValues(16.dp),
        ) {
            InterventionSection(snap)
        }
    }

    // 6. 应用排行
    item(key = "app_ranking") {
        SmallTitle(text = "应用排行")
        AppRankSection(
            apps = snap.apps,
            modeIndex = rankModeIndex,
            onModeIndexChange = onRankModeChange,
            onOpenDetail = onOpenDetail,
        )
    }

    // 7. 最近动态
    if (snap.recent.isNotEmpty()) {
        item(key = "recent_events") {
            RecentEventsSection(
                events = snap.recent,
                apps = snap.apps,
                onOpenDetail = onOpenDetail,
            )
        }
    }

    // 8. 页脚口径
    item(key = "footer") {
        StatsFooter(snap)
    }
}

// ============================================================================================
// 5. 区块组件
// ============================================================================================

@Composable
private fun StatTile(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.height(IntrinsicSize.Min),
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
        showIndication = onClick != null,
        pressFeedbackType = if (onClick != null) PressFeedbackType.Tilt else PressFeedbackType.None,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = value,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (accent) colorScheme.primary else colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun ScreenOffBanner(snap: FreezeStatsSnapshot) {
    val count = snap.screenOffWakeCount
    val interval = if (count > 0 && snap.screenOffMillis > 0) snap.screenOffMillis / count else 0L
    val accent = screenOffAccentColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "息屏时系统解冻记录 ${formatCount(count)} 次",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = when {
                        count == 0 -> "暂时没有记录，不代表所有应用一直冻结"
                        interval > 0 -> "所有应用合计平均每 ${formatInterval(interval)} 一条记录，不是单个应用的唤醒间隔"
                        else -> "这是系统记录，不能直接换算成消息条数或耗电量"
                    },
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun WakeReasonSection(wake: Map<WakeCategory, Int>) {
    val total = wake.values.sum()
    val safeTotal = total.coerceAtLeast(1)
    val bgCount = wake.entries.sumOf { if (it.key.isBackground) it.value else 0 }
    val userCount = wake[WakeCategory.USER_OPEN] ?: 0
    val ordered = wake.entries.filter { it.value > 0 }.sortedByDescending { it.value }
    val segments = ordered.map { it.key.displayColor() to it.value.toFloat() }

    Column(Modifier.fillMaxWidth()) {
        if (total > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "解冻记录 ${formatCount(total)} 次",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = "后台 ${formatCount(bgCount)} · 前台活动 ${formatCount(userCount)}",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        SegmentedBar(
            segments = segments,
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
        )
        Spacer(Modifier.height(14.dp))
        ordered.forEach { (cat, n) ->
            val pct = n * 100f / safeTotal
            val pctText = if (pct < 1f) "<1%" else "${"%.0f".format(Locale.ROOT, pct)}%"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(cat.displayColor()),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = cat.label,
                    fontSize = 14.sp,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatCount(n),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = pctText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.width(42.dp),
                )
            }
        }
        if (ordered.isEmpty()) {
            Text("暂无系统解冻记录", fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun TimeDistributionSection(
    window: StatsWindow,
    buckets: List<StatsTimeBucket>,
) {
    var selected by remember(window) { mutableIntStateOf(-1) }
    val onColor = colorScheme.primary
    val offColor = screenOffAccentColor()
    val maxTotal = buckets.maxOfOrNull { it.total } ?: 0
    val peakIndex = if (maxTotal > 0) buckets.indices.maxByOrNull { buckets[it].total } ?: -1 else -1
    val showIndex = if (selected in buckets.indices) selected else peakIndex

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                if (showIndex >= 0) {
                    val b = buckets[showIndex]
                    val prefix = if (selected in buckets.indices) "已选 · " else "峰值 · "
                    val suffix = if (buckets.size == 24) " 时" else ""
                    Text(
                        text = "$prefix${b.label}$suffix  共 ${formatCount(b.total)} 次",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "亮屏 ${formatCount(b.screenOnWakes)} · 息屏 ${formatCount(b.screenOffWakes)}",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    Text(
                        text = "本时段暂无系统解冻高峰",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = "点按柱体可查看该时段亮屏与息屏唤醒明细",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (maxTotal > 0) {
                Text(
                    text = "峰 ${formatCount(maxTotal)}",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        StackedBarChart(
            window = window,
            buckets = buckets,
            selectedIndex = selected,
            onSelect = { selected = if (selected == it) -1 else it },
            onColor = onColor,
            offColor = offColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
        Spacer(Modifier.height(6.dp))
        if (buckets.size == 24) {
            // 24 根小时柱：均匀展示关键刻度，避免窄槽内文字裁剪
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("00", "06", "12", "18", "23").forEach { h ->
                    Text(
                        text = h,
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                buckets.forEach { b ->
                    Text(
                        text = b.label,
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(onColor, "亮屏")
            Spacer(Modifier.width(16.dp))
            LegendDot(offColor, "息屏")
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun InterventionSection(snap: FreezeStatsSnapshot) {
    val rules = snap.interceptByRule.entries.filter { it.value > 0 }.sortedByDescending { it.value }
    val maxRule = rules.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (rules.isEmpty()) {
            Text("本时段内没有拦截唤醒", fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
        } else {
            rules.forEach { (rule, n) ->
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Text(rule.label, fontSize = 14.sp, color = colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Text(formatCount(n), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(4.dp))
                    ProgressLine(fraction = n.toFloat() / maxRule, color = colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("保护免冻", snap.protectedCount, Modifier.weight(1f))
            MiniMetric("阻止查杀", snap.killBlockedCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniMetric(
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
    containerColor: Color = colorScheme.surfaceContainerHighest,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
        Text(value?.let(::formatCount) ?: "—", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurface)
    }
}

@Composable
private fun AppRankSection(
    apps: List<AppFreezeStats>,
    modeIndex: Int,
    onModeIndexChange: (Int) -> Unit,
    onOpenDetail: (AppFreezeStats) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var hideSystemApps by rememberSaveable { mutableStateOf(false) }
    val mode = RankMode.entries.getOrElse(modeIndex) { RankMode.WAKE }
    val hasSystemApps = remember(apps) { apps.any { it.isSystem } }

    val baseSorted = remember(apps, mode) {
        when (mode) {
            RankMode.WAKE -> apps.filter { it.backgroundWakeCount > 0 }
                .sortedByDescending { it.backgroundWakeCount }
            RankMode.FROZEN -> apps.filter { it.frozenMillis > 0 }.sortedByDescending { it.frozenMillis }
            RankMode.INTERCEPT -> apps.filter { it.interceptCount > 0 }.sortedByDescending { it.interceptCount }
            RankMode.SYSTEM_RECORDS -> apps.filter { it.freezeCount > 0 || it.wakeByCategory.values.any { count -> count > 0 } }
                .sortedByDescending { it.freezeCount.toLong() + it.wakeByCategory.values.sumOf { count -> count.toLong() } }
        }
    }
    val sorted = remember(baseSorted, hideSystemApps) {
        if (hideSystemApps) baseSorted.filter { !it.isSystem } else baseSorted
    }
    val filtered = remember(sorted, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) sorted
        else sorted.filter {
            it.label.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q) ||
                it.uid.toString().contains(q)
        }
    }
    val maxValue = remember(sorted, mode) {
        sorted.maxOfOrNull { rankValue(it, mode) }?.coerceAtLeast(1f) ?: 1f
    }
    val shown = if (expanded || searchQuery.isNotBlank()) filtered else filtered.take(10)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RankMode.entries.forEachIndexed { idx, m ->
                    val sel = idx == modeIndex
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (sel) colorScheme.primary else colorScheme.surfaceContainerHigh)
                            .clickable { onModeIndexChange(idx) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = m.label,
                            fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (sel) colorScheme.onPrimary else colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                if (hasSystemApps) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(colorScheme.dividerLine),
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (hideSystemApps) colorScheme.secondaryContainer else colorScheme.surfaceContainerHigh,
                            )
                            .clickable { hideSystemApps = !hideSystemApps }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "隐藏系统应用",
                            fontSize = 13.sp,
                            fontWeight = if (hideSystemApps) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (hideSystemApps) {
                                if (isDynamicColor) colorScheme.onSecondaryContainer else colorScheme.onSurface
                            } else {
                                colorScheme.onSurfaceVariantSummary
                            },
                        )
                    }
                }
            }
            if (baseSorted.isNotEmpty()) {
                IconButton(
                    onClick = {
                        if (searchVisible) {
                            searchVisible = false
                            searchQuery = ""
                        } else {
                            searchVisible = true
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (searchVisible) MiuixIcons.Close else MiuixIcons.Basic.Search,
                        contentDescription = if (searchVisible) "收起搜索" else "搜索应用排行",
                        tint = if (searchVisible || searchQuery.isNotBlank()) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = searchVisible || searchQuery.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            MiuixCapsuleSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "搜索应用名、包名或 UID...",
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            if (shown.isEmpty()) {
                if (searchQuery.isNotBlank()) {
                    BasicComponent(
                        title = "未找到匹配的应用",
                        summary = "点击清空搜索词",
                        onClick = { searchQuery = "" },
                    )
                } else {
                    BasicComponent(title = "暂无数据", summary = "本时段内没有符合条件的应用")
                }
            }
            shown.forEachIndexed { i, app ->
                RankRow(
                    rank = i + 1,
                    app = app,
                    mode = mode,
                    fraction = rankValue(app, mode) / maxValue,
                    onClick = { onOpenDetail(app) },
                )
            }
            if (filtered.size > 10 && searchQuery.isBlank()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expanded) "收起" else "查看全部 ${filtered.size} 个应用",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun rankValue(app: AppFreezeStats, mode: RankMode): Float = when (mode) {
    RankMode.WAKE -> app.backgroundWakeCount.toFloat()
    RankMode.FROZEN -> app.frozenMillis.toFloat()
    RankMode.INTERCEPT -> app.interceptCount.toFloat()
    RankMode.SYSTEM_RECORDS -> app.freezeCount.toFloat() + app.wakeByCategory.values.sumOf { it.toDouble() }.toFloat()
}

@Composable
private fun RankRow(
    rank: Int,
    app: AppFreezeStats,
    mode: RankMode,
    fraction: Float,
    onClick: () -> Unit,
) {
    val barFraction = fraction.coerceIn(0.03f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (rank <= 3) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(22.dp),
        )
        StatsAppIcon(
            applicationInfo = app.applicationInfo,
            members = app.members,
            label = app.label,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(38.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = app.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (app.members.size > 1) SharedUidTag()
                    if (app.isDualApp) {
                        StatusTagMiuix(
                            label = "双开",
                            contentColor = colorScheme.primary,
                            backgroundColor = colorScheme.primary.copy(alpha = 0.12f),
                            fontSize = 10.sp,
                            paddingValues = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
                        )
                    } else if (app.isSystem) {
                        StatusTagMiuix(
                            label = "系统",
                            contentColor = colorScheme.onSurfaceVariantSummary,
                            backgroundColor = colorScheme.surfaceContainerHigh,
                            fontSize = 10.sp,
                            paddingValues = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (mode) {
                        RankMode.WAKE -> "${formatCount(app.backgroundWakeCount)} 次"
                        RankMode.FROZEN -> formatDuration(app.frozenMillis)
                        RankMode.INTERCEPT -> "${formatCount(app.interceptCount)} 次"
                        RankMode.SYSTEM_RECORDS -> "冻结 ${formatCount(app.freezeCount)} · 解冻 ${formatCount(app.wakeByCategory.values.sum())}"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(5.dp))
            if (mode == RankMode.WAKE) {
                val segs = app.wakeByCategory.entries
                    .filter { it.key.isBackground && it.value > 0 }
                    .sortedByDescending { it.value }
                    .map { it.key.displayColor() to it.value.toFloat() }
                SegmentedBar(
                    segments = segs,
                    fillFraction = barFraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
            } else {
                ProgressLine(fraction = barFraction, color = colorScheme.primary)
            }
            if (mode == RankMode.FROZEN && app.observedMillis > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "记账冻结占比 ${"%.0f".format(Locale.ROOT, app.frozenRatio * 100)}%",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun SharedUidTag() {
    StatusTagMiuix(
        label = "共用统计",
        contentColor = colorScheme.primary,
        backgroundColor = colorScheme.primary.copy(alpha = 0.12f),
        fontSize = 10.sp,
        paddingValues = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun RecentEventsSection(
    events: List<StatsRecentEvent>,
    apps: List<AppFreezeStats>,
    onOpenDetail: (AppFreezeStats) -> Unit,
) {
    val visibleEvents = remember(events) { events.sortedByDescending { it.timeMillis }.take(15) }
    val appsByUid = remember(apps) { apps.associateBy { it.uid } }
    Column(Modifier.fillMaxWidth()) {
        SmallTitle(text = "最近系统记录 (${visibleEvents.size})")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 6.dp),
            insideMargin = PaddingValues(vertical = 6.dp),
        ) {
            visibleEvents.forEach { e ->
                val matchedApp = appsByUid[e.uid]
                RecentEventRow(
                    e = e,
                    onClick = matchedApp?.let { app -> { onOpenDetail(app) } },
                )
            }
        }
    }
}

@Composable
private fun RecentEventRow(
    e: StatsRecentEvent,
    onClick: (() -> Unit)? = null,
) {
    val (tagText, tagColor) = when (e.kind) {
        StatsEventKind.FREEZE -> "冻结记录" to colorScheme.primary
        StatsEventKind.THAW -> (e.category?.label ?: "解冻记录") to (e.category?.displayColor() ?: colorScheme.onSurfaceVariantSummary)
        StatsEventKind.INTERCEPT -> "已拦截" to statsSuccessColor()
        StatsEventKind.PROTECT -> "保护免冻" to statsProtectColor()
        StatsEventKind.KILL_BLOCKED -> "阻止查杀" to colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatsAppIcon(
            applicationInfo = e.applicationInfo,
            members = e.members,
            label = e.label,
            modifier = Modifier
                .padding(end = 10.dp)
                .size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = e.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (e.members.size > 1) SharedUidTag()
                if (e.isDualApp) {
                    StatusTagMiuix(
                        label = "双开",
                        contentColor = colorScheme.primary,
                        backgroundColor = colorScheme.primary.copy(alpha = 0.12f),
                        fontSize = 10.sp,
                        paddingValues = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            val subText = buildString {
                append(formatClockSeconds(e.timeMillis))
                if (e.rawReason.isNotBlank()) append(" · ").append(e.rawReason)
            }
            Text(
                text = subText,
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        val detail = buildString {
            append(originLabel(e.origin)).append(" · ")
            append(tagText)
            if (e.kind == StatsEventKind.INTERCEPT && e.rule != null) append(" · ").append(e.rule.label)
        }
        StatusTagMiuix(
            label = detail,
            contentColor = tagColor,
            backgroundColor = tagColor.copy(alpha = 0.14f),
        )
    }
}

@Composable
private fun StatsFooter(snap: FreezeStatsSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val mem = if (snap.memoryBytes > 0) " · 占用约 ${(snap.memoryBytes + 1023) / 1024} KB" else ""
        Text(
            text = "统计自 ${formatDateTime(snap.sinceMillis)} · 更新于 ${formatClock(snap.generatedAtMillis)}$mem",
            fontSize = 12.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = "冻结与唤醒次数取自系统冻解入口，按应用汇总；全量原始记录见下方「系统记录」。" +
                "冻结时长是多个应用加在一起的时间，可能超过开机时长。" +
                "共用统计的应用按一个计算，不重复累加成员。" +
                "想看应用现在是否冻结，请看「进程列表」。",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )
        snap.sourceNote?.let {
            Text(text = it, fontSize = 12.sp, color = statsWarningTextColor())
        }
    }
}

@Composable
private fun LoadingStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfiniteProgressIndicator(color = colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "正在读取墓碑统计…",
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "正在向系统读取应用次数和系统记录",
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun StatsStateCard(
    title: String,
    summary: String,
    primaryLabel: String? = null,
    onPrimaryClick: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondaryClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
            )
            if (primaryLabel != null || secondaryLabel != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (secondaryLabel != null) {
                        TextButton(
                            text = secondaryLabel,
                            onClick = onSecondaryClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (primaryLabel != null) {
                        TextButton(
                            text = primaryLabel,
                            onClick = onPrimaryClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleBanner(onExit: () -> Unit) {
    WarningCard(
        message = "当前显示的是示例数据（随机生成，仅用于界面预览）",
        level = WarningLevel.Notice,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        onClick = onExit,
        action = {
            Spacer(Modifier.width(8.dp))
            StatusTagMiuix(
                label = "点按退出",
                backgroundColor = statsWarningTextColor().copy(alpha = 0.16f),
                contentColor = statsWarningTextColor(),
                fontSize = 10.sp,
                paddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            )
        },
    )
}

// ============================================================================================
// 6. 弹窗：统计口径说明 & 应用详情底部弹窗
// ============================================================================================

@Composable
private fun MethodologyDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    GlassDialog(backdrop = PopupBackdropHolder.current, 
        show = show,
        title = "这些数字怎么看",
        onDismissRequest = onDismiss,
    ) {
        val dismissState = LocalDismissState.current
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "• 应用次数：按应用（UID）汇总系统冻结与解冻入口的次数；同一应用短时间内多次冻解会累加，不等于消息条数。",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = "• 系统记录：系统侧冻结与解冻入口的全量次数，含重复与旧版本数据，仅供参考。",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = "• 原因与来源：网络活动不等于新消息，前台活动不等于点击。已知模块操作另列；系统记录标签只说明记录来路，不代表已查明谁触发。",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = "• 冻结时间：这是系统记账时间，多个应用加在一起，可能超过开机时长。想看应用现在是否冻结，请看「进程列表」，不要只看次数。",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(6.dp))
            TextButton(
                text = "知道了",
                onClick = {
                    onDismiss()
                    dismissState?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun AppStatsDetailSheet(
    app: AppFreezeStats?,
    updatedAtMillis: Long?,
    refreshFailed: Boolean,
    onDismiss: () -> Unit,
    onOpenAppProfile: (AppFreezeStats) -> Unit,
) {
    // 关闭动画期间保留最后一次内容，避免内容在退场滑出前突然清空
    var last by remember { mutableStateOf<AppFreezeStats?>(null) }
    if (app != null) last = app
    val a = last ?: return

    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()

    GlassBottomSheet(backdrop = PopupBackdropHolder.current, 
        show = app != null,
        insideMargin = DpSize(16.dp, 0.dp),
        endAction = {
            val dismissState = LocalDismissState.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(48.dp))
                Text(
                    text = a.label,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = {
                        onDismiss()
                        dismissState?.invoke()
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "关闭",
                        tint = colorScheme.onBackground,
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = navBottomPadding + 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatsAppIcon(
                        applicationInfo = a.applicationInfo,
                        members = a.members,
                        label = a.label,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(44.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (a.members.size > 1) "共享 UID · ${a.members.size} 个成员" else {
                                a.packageName.ifBlank { "包名未解析" }
                            },
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "UID ${a.uid}",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                            if (a.members.size > 1) SharedUidTag()
                            if (a.isDualApp) {
                                StatusTagMiuix(
                                    label = "双开",
                                    contentColor = colorScheme.primary,
                                    backgroundColor = colorScheme.primary.copy(alpha = 0.12f),
                                    fontSize = 10.sp,
                                    paddingValues = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
                                )
                            } else if (a.isSystem) {
                                StatusTagMiuix(
                                    label = "系统",
                                    contentColor = colorScheme.onSurfaceVariantSummary,
                                    backgroundColor = colorScheme.surfaceContainerHigh,
                                    fontSize = 10.sp,
                                    paddingValues = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }

                if (a.members.size > 1) {
                    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("这些应用共用统计，不重复计算", fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                            a.members.forEach { member ->
                                Column {
                                    Text(member.label, fontSize = 14.sp, color = colorScheme.onSurface)
                                    Text(member.packageName, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                                }
                            }
                        }
                    }
                }

                updatedAtMillis?.let {
                    Text(
                        text = if (refreshFailed) "更新失败，仍显示 ${formatClock(it)} 的数据" else "更新于 ${formatClock(it)}",
                        fontSize = 12.sp,
                        color = if (refreshFailed) statsWarningTextColor() else colorScheme.onSurfaceVariantSummary,
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniMetric("应用冻结", a.freezeCount, Modifier.weight(1f), containerColor = colorScheme.surfaceContainer)
                    MiniMetric("应用唤醒", a.backgroundWakeCount, Modifier.weight(1f), containerColor = colorScheme.surfaceContainer)
                    MiniMetric("拦截", a.interceptCount, Modifier.weight(1f), containerColor = colorScheme.surfaceContainer)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("系统记录", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                        Text(
                            "冻结 ${formatCount(a.freezeCount)} 次 · 解冻 ${formatCount(a.wakeByCategory.values.sum())} 次",
                            fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary,
                        )
                        Text("解冻次数含前台活动；应用唤醒只统计后台唤醒。", fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("系统记账冻结时长", fontSize = 14.sp, color = colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Text(
                                text = formatDuration(a.frozenMillis),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                            )
                        }
                        if (a.observedMillis > 0) {
                            ProgressLine(fraction = a.frozenRatio, color = colorScheme.primary)
                            Text(
                                text = "记账冻结占比 ${"%.0f".format(Locale.ROOT, a.frozenRatio * 100)}% · 前台活动记录 ${formatCount(a.userOpenCount)} 次",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        } else if (a.userOpenCount > 0) {
                            Text(
                                text = "前台活动记录 ${formatCount(a.userOpenCount)} 次，不代表点击次数",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        if (a.protectedCount > 0 || a.killBlockedCount > 0) {
                            Text(
                                text = "保护免冻 ${formatCount(a.protectedCount)} 次 · 阻止查杀 ${formatCount(a.killBlockedCount)} 次",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("系统解冻记录 · 原因", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                        WakeReasonSection(a.wakeByCategory)
                    }
                }

                val rules = a.interceptByRule.entries.filter { it.value > 0 }.sortedByDescending { it.value }
                if (rules.isNotEmpty() || a.topRawReasons.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            rules.forEach { (r, n) ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text("拦截 · ${r.label}", fontSize = 13.sp, color = colorScheme.onSurface, modifier = Modifier.weight(1f))
                                    Text(formatCount(n), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                                }
                            }
                            if (a.topRawReasons.isNotEmpty()) {
                                Text(
                                    text = "系统记录最多的原因",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(top = if (rules.isNotEmpty()) 6.dp else 0.dp),
                                )
                                a.topRawReasons.forEach { (reason, n) ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatRawThawReason(reason),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = formatCount(n),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val canOpenProfile = a.packageName.isNotBlank() && !a.packageName.startsWith("uid:")
            Button(
                onClick = { onOpenAppProfile(a) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canOpenProfile,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(
                    text = when {
                        a.members.size > 1 -> "多个应用共用统计，请分别设置策略"
                        canOpenProfile -> "前往应用策略"
                        else -> "暂无应用包名 (UID ${a.uid})"
                    },
                    style = MiuixTheme.textStyles.button,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ============================================================================================
// 7. 图表（Canvas 自绘，自适应主题色板）
// ============================================================================================

/** 圆角分段条：底轨铺满全宽，彩色分段按 [fillFraction] 横向堆叠 */
@Composable
private fun SegmentedBar(
    segments: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
    fillFraction: Float = 1f,
) {
    val track = colorScheme.surfaceContainerHighest
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(550)) }
    val clampedFill = fillFraction.coerceIn(0f, 1f)

    Canvas(modifier = modifier.clip(RoundedCornerShape(50))) {
        drawRect(track)
        val total = segments.sumOf { it.second.toDouble() }.toFloat()
        if (total <= 0f || clampedFill <= 0f) return@Canvas
        val gap = 2.dp.toPx()
        var x = 0f
        val activeW = size.width * clampedFill * progress.value
        segments.forEachIndexed { i, (color, value) ->
            val segW = activeW * value / total
            val drawW = if (i < segments.lastIndex) (segW - gap).coerceAtLeast(0f) else segW
            if (drawW > 0f) {
                drawRect(color = color, topLeft = Offset(x, 0f), size = Size(drawW, size.height))
            }
            x += segW
        }
    }
}

/** 细进度条 */
@Composable
private fun ProgressLine(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val track = colorScheme.surfaceContainerHighest
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, cornerRadius = r)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(
                color = color,
                size = Size(w, size.height),
                cornerRadius = r,
            )
        }
    }
}

/** 堆叠柱状图：下段息屏、上段亮屏；点按选中，其他柱变淡；顶部圆角平滑裁剪 */
@Composable
private fun StackedBarChart(
    window: StatsWindow,
    buckets: List<StatsTimeBucket>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onColor: Color,
    offColor: Color,
    modifier: Modifier = Modifier,
) {
    val grid = colorScheme.dividerLine
    val progress = remember(window) { Animatable(0f) }
    LaunchedEffect(window) { progress.animateTo(1f, tween(650)) }
    val maxTotal = (buckets.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)

    Canvas(
        modifier = modifier.pointerInput(buckets.size) {
            detectTapGestures { pos ->
                if (buckets.isEmpty()) return@detectTapGestures
                val idx = (pos.x / (size.width.toFloat() / buckets.size)).toInt().coerceIn(0, buckets.lastIndex)
                onSelect(idx)
            }
        },
    ) {
        for (i in 0..2) {
            val y = size.height * i / 2f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (buckets.isEmpty()) return@Canvas
        val slot = size.width / buckets.size
        val barW = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        val corner = CornerRadius(barW / 3f, barW / 3f)
        val clipPath = Path()

        buckets.forEachIndexed { i, b ->
            val offH = size.height * b.screenOffWakes / maxTotal * progress.value
            val onH = size.height * b.screenOnWakes / maxTotal * progress.value
            val totalH = offH + onH
            if (totalH <= 0f) return@forEachIndexed

            val alpha = if (selectedIndex == -1 || selectedIndex == i) 1f else 0.35f
            val left = slot * i + (slot - barW) / 2f
            val top = size.height - totalH

            clipPath.rewind()
            clipPath.addRoundRect(
                RoundRect(
                    rect = Rect(left, top, left + barW, size.height),
                    topLeft = corner,
                    topRight = corner,
                    bottomLeft = CornerRadius.Zero,
                    bottomRight = CornerRadius.Zero,
                ),
            )
            clipPath(clipPath) {
                if (offH > 0f) {
                    drawRect(
                        color = offColor.copy(alpha = alpha),
                        topLeft = Offset(left, size.height - offH),
                        size = Size(barW, offH),
                    )
                }
                if (onH > 0f) {
                    drawRect(
                        color = onColor.copy(alpha = alpha),
                        topLeft = Offset(left, top),
                        size = Size(barW, onH),
                    )
                }
            }
        }
    }
}

// ============================================================================================
// 8. 格式化
// ============================================================================================

private fun formatRawThawReason(reason: String): String {
    if (reason.isBlank() || '（' in reason || '(' in reason) return reason
    val name = reason.trim().lowercase(Locale.ROOT)
    val label = when (name) {
        "packet" -> "网络数据"
        "check binder" -> "Binder检查"
        "sync binder", "sync binder4" -> "同步Binder"
        "net change" -> "网络变化"
        "network connect" -> "网络连接"
        "signal iso" -> "隔离信号"
        "signal other" -> "其他信号"
        "broadcast" -> "广播"
        "notifi" -> "通知"
        "pendingnotify" -> "待处理通知"
        "activity front", "tofront", "tofrontactivity", "movetofront", "notifymovetofront" -> "活动前台"
        "activity start", "activity start!" -> "活动启动"
        "activity resume" -> "活动恢复"
        "fg activity" -> "前台活动"
        "ui bar" -> "界面栏"
        "launch_mode" -> "启动模式"
        "cmd: thuid" -> "UID解冻命令"
        "mediabutton", "mediabuttoncmd" -> "媒体按键"
        "receiver pkg" -> "广播接收器"
        "alarm" -> "定时器"
        "excute service" -> "服务执行"
        "startservice" -> "服务启动"
        "provider" -> "内容提供器"
        "audiofocus" -> "音频焦点"
        "bluetooth" -> "蓝牙"
        "keycode" -> "按键代码"
        "adj" -> "进程优先级"
        "fromsystem", "from system" -> "系统来源"
        else -> return reason
    }
    return "$reason（$label）"
}

private fun formatCount(n: Int): String = when {
    n >= 100_000 -> "%.1f万".format(Locale.ROOT, n / 10_000f)
    n >= 10_000 -> "%.2f万".format(Locale.ROOT, n / 10_000f)
    else -> "%,d".format(Locale.ROOT, n)
}

private fun formatDurationParts(ms: Long): Pair<String, String> {
    if (ms <= 0L) return "0" to "分钟"
    val h = ms / 3_600_000f
    return when {
        h >= 100f -> "%.0f".format(Locale.ROOT, h) to "小时"
        h >= 1f -> "%.1f".format(Locale.ROOT, h) to "小时"
        ms >= 60_000L -> "%.0f".format(Locale.ROOT, ms / 60_000f) to "分钟"
        else -> "${(ms / 1000L).coerceAtLeast(1)}" to "秒"
    }
}

private fun formatDuration(ms: Long): String {
    val (n, u) = formatDurationParts(ms)
    return "$n $u"
}

private fun formatInterval(ms: Long): String {
    val min = ms / 60_000L
    return when {
        min >= 60 -> "%.1f 小时".format(Locale.ROOT, ms / 3_600_000f)
        min >= 1 -> "$min 分钟"
        else -> "${(ms / 1000L).coerceAtLeast(1)} 秒"
    }
}

private fun formatClock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ms))

private fun formatClockSeconds(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(ms))

private fun formatDateTime(ms: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ms))
