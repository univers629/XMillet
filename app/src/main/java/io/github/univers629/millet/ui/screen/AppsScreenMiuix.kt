package io.github.univers629.millet.ui.screen
import io.github.univers629.millet.ui.util.MilletTopAppBar
import io.github.univers629.millet.ui.util.MilletSmallTopAppBar
import io.github.univers629.millet.ui.util.BarIconButton

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univers629.millet.core.CloudListCodec
import io.github.univers629.millet.core.PolicyTemplate
import io.github.univers629.millet.core.PrefReader
import io.github.univers629.millet.ui.component.AppIconImage
import io.github.univers629.millet.ui.component.DropdownItem
import io.github.univers629.millet.ui.component.ListPopupDefaults
import io.github.univers629.millet.ui.component.StatusTagMiuix
import io.github.univers629.millet.ui.theme.LocalEnableBlur
import io.github.univers629.millet.ui.util.BlurredBar
import io.github.univers629.millet.ui.util.PolicyMembershipSnapshot
import io.github.univers629.millet.ui.util.PolicyTemplateStore
import io.github.univers629.millet.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

data class InstalledAppUiModel(
    val appName: String,
    val packageName: String,
    val uid: Int,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val applicationInfo: ApplicationInfo?,
    val inWhiteSet: Boolean,
    val inMessageSet: Boolean,
    val inForceFreezeSet: Boolean,
    val inBlockThawSet: Boolean,
    val inForbidSyncSet: Boolean,
    val inNotFreezeFgsSet: Boolean,
    val inBatteryWhiteSet: Boolean,
    /** 云端配置库里的三条名单（PowerKeeper cloud_configure.db）。 */
    val inCloudDozeWhite: Boolean = false,
    val inCloudFrozenWhite: Boolean = false,
    val inCloudLaunchAllow: Boolean = false,
) {
    val isDualApp: Boolean
        get() = io.github.univers629.millet.core.AppUidUtils.isDualAppUid(uid)

    val hasAnyConfig: Boolean
        get() = inWhiteSet || inMessageSet || inForceFreezeSet || inBlockThawSet ||
            inForbidSyncSet || inNotFreezeFgsSet || inBatteryWhiteSet
}

/**
 * 策略分类筛选。
 *
 * 按「用户意图」分档，不再把同一件事按实现层拆成多档：
 * - 勾选免冻白名单会自动写入电池白名单，并把系统省电策略设为无限制，
 *   电池白名单本身也只是系统 Doze 白名单的一部分，两者合并为「不受限制」；
 * - 系统侧「电池未优化」不再单独成档或成标签：它在打开应用配置页时被读入
 *   （见 `AppProfileScreenMiuix` 的初始同步），之后以开关状态呈现，
 *   避免一个只读状态和应用自己的配置并列在一起；
 * - 禁止解冻 / 禁止同步 依附于强制冻结，合并进「强力冻结」；
 * - 云下发的三份名单来自 PowerKeeper 的 `cloud_configure.db`，与本地配置无关，
 *   单独一档，且不计入「已配置」。
 */
enum class AppCategoryFilter(val title: String) {
    ALL("全部"),
    CONFIGURED("已配置"),
    CLOUD("云白名单"),
    UNRESTRICTED("不受限制"),
    MESSAGE("消息放行"),
    FGS_KEEP("FGS免冻"),
    FORCE_FREEZE("强力冻结");

    fun matches(app: InstalledAppUiModel): Boolean = when (this) {
        ALL -> true
        CONFIGURED -> app.hasAnyConfig
        CLOUD -> app.inCloudDozeWhite || app.inCloudFrozenWhite || app.inCloudLaunchAllow
        UNRESTRICTED -> app.inWhiteSet || app.inBatteryWhiteSet
        MESSAGE -> app.inMessageSet
        FGS_KEEP -> app.inNotFreezeFgsSet
        FORCE_FREEZE -> app.inForceFreezeSet || app.inBlockThawSet || app.inForbidSyncSet
    }
}

/**
 * 应用策略页（策略中枢）：顶部是「策略模板」与「官方云白名单」并排入口卡片，下方保留按分类筛选的应用列表。
 *
 * @param policyVersion 策略集合变化后由宿主递增，确保仅修改「忽略网络唤醒」等不在模型里的字段时也能刷新汇总
 * @param templateVersion 自定义模板增删改后由宿主递增，用于刷新模板数量
 * @param cloudLists PowerKeeper 上报或兜底读到的三条云端名单；未就绪时为 null
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsScreenMiuix(
    apps: List<InstalledAppUiModel>,
    isRefreshing: Boolean,
    showSystemApps: Boolean,
    onToggleShowSystemApps: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAppProfile: (InstalledAppUiModel) -> Unit,
    bottomInnerPadding: Dp,
    policyVersion: Int = 0,
    templateVersion: Int = 0,
    cloudLists: CloudListCodec.CloudLists? = null,
    onOpenTemplates: () -> Unit = {},
    onOpenCloudWhitelist: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { PrefReader.openUiPrefs(context) }
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedCategory = AppCategoryFilter.entries.getOrElse(selectedCategoryIndex) { AppCategoryFilter.ALL }

    val filteredApps = remember(apps, searchQuery, selectedCategory, showSystemApps) {
        val q = searchQuery.trim().lowercase()
        apps.filter { app ->
            val sysMatch = showSystemApps || !app.isSystem || app.hasAnyConfig
            val catMatch = selectedCategory.matches(app)
            val queryMatch = q.isEmpty() ||
                app.appName.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q) ||
                app.uid.toString().contains(q)
            sysMatch && catMatch && queryMatch
        }
    }

    // 模板与云名单概览：随 templateVersion / policyVersion / apps 刷新。
    val templates = remember(templateVersion) { PolicyTemplateStore.loadAll(prefs) }
    val hubSummary = remember(apps, templates, policyVersion) { buildPolicyHubSummary(prefs, apps, templates) }

    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                MilletTopAppBar(
                    color = barColor,
                    title = "应用策略 (${filteredApps.size})",
                    actions = {
                        val showTopPopup = remember { mutableStateOf(false) }
                        BarIconButton(
                            modifier = Modifier.padding(end = 12.dp),
                            onClick = { showTopPopup.value = true },
                            holdDownState = showTopPopup.value
                        ) {
                            Icon(
                                imageVector = MiuixIcons.MoreCircle,
                                tint = colorScheme.onSurface,
                                contentDescription = "更多选项"
                            )
                        }
                        OverlayListPopup(
                            show = showTopPopup.value,
                            popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = {
                                showTopPopup.value = false
                            },
                            content = {
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "显示系统应用",
                                        isSelected = showSystemApps,
                                        optionSize = 1,
                                        onSelectedIndexChange = {
                                            onToggleShowSystemApps()
                                            showTopPopup.value = false
                                        },
                                        index = 0
                                    )
                                }
                            }
                        )
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            PullToRefresh(
                isRefreshing = isRefreshing,
                pullToRefreshState = pullToRefreshState,
                onRefresh = onRefresh,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 6.dp
                ),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = bottomInnerPadding
                    ),
                    overscrollEffect = null
                ) {
                    // 1. Miuix 胶囊搜索栏
                    item {
                        MiuixCapsuleSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholder = "搜索应用名称、包名或 UID..."
                        )
                    }

                    // 策略中枢：左右两张入口卡（策略模板 / 官方云白名单）；搜索时收起，把空间留给结果。
                    if (searchQuery.isBlank()) {
                        item(key = "policy_hub") {
                            PolicyHubCards(
                                templateCount = templates.size,
                                summary = hubSummary,
                                cloudLists = cloudLists,
                                onOpenTemplates = onOpenTemplates,
                                onOpenCloudWhitelist = onOpenCloudWhitelist,
                            )
                        }
                    }

                    // 2. 策略分类横向胶囊筛选条
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppCategoryFilter.entries.forEachIndexed { idx, cat ->
                                val selected = idx == selectedCategoryIndex
                                val bgColor = if (selected) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.surfaceContainerHigh
                                }
                                val txtColor = if (selected) {
                                    colorScheme.onPrimary
                                } else {
                                    colorScheme.onSurfaceVariantSummary
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(bgColor)
                                        .clickable { selectedCategoryIndex = idx }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = cat.title,
                                        color = txtColor,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    if (filteredApps.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                onClick = {
                                    searchQuery = ""
                                    selectedCategoryIndex = 0
                                },
                                showIndication = true
                            ) {
                                BasicComponent(
                                    title = "没有匹配的应用",
                                    summary = "点击重置搜索与分类，或在右上角开启「显示系统应用」"
                                )
                            }
                        }
                    } else {
                        items(filteredApps, key = { "${it.packageName}:${it.uid}" }) { app ->
                            AppItemCardMiuix(
                                app = app,
                                onClick = { onOpenAppProfile(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MiuixCapsuleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = colorScheme.onSurface
        ),
        cursorBrush = SolidColor(colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 4.dp)
            .heightIn(min = 45.dp)
            .background(colorScheme.surfaceContainerHigh, CircleShape),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Search,
                    contentDescription = "Search",
                    modifier = Modifier
                        .size(44.dp)
                        .padding(start = 16.dp, end = 8.dp),
                    tint = colorScheme.onSurfaceContainerHigh,
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            color = colorScheme.onSurfaceVariantSummary
                        )
                    }
                    innerTextField()
                }
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.SearchCleanup,
                        tint = colorScheme.onSurface,
                        contentDescription = "Clear",
                        modifier = Modifier
                            .size(44.dp)
                            .padding(start = 8.dp, end = 16.dp)
                            .clickable(
                                interactionSource = null,
                                indication = null
                            ) {
                                onQueryChange("")
                            },
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppItemCardMiuix(
    app: InstalledAppUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 10.dp),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Sink,
        insideMargin = PaddingValues(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                applicationInfo = app.applicationInfo,
                label = app.appName,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(48.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = app.appName,
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = if (app.isDualApp) "${app.packageName} · UID: ${app.uid}" else app.packageName,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (app.isDualApp) {
                        StatusTagMiuix(
                            label = "双开",
                            backgroundColor = colorScheme.tertiaryContainer,
                            contentColor = colorScheme.onTertiaryContainer
                        )
                    }
                    // 标签分两段：强调类（实心底、饱和色）在前，普通胶囊（浅底色）在后。
                    // 电池白名单/黑名单代表系统级省电状态，排在第一位；SYSTEM 紧随其后，
                    // 再是免冻白名单 / 消息应用 / 强制冻结；依附性的 FGS免冻、禁止解冻、
                    // 禁止同步 与云下发名单放在第二段，避免抢视觉焦点。
                    if (app.inBatteryWhiteSet) {
                        StatusTagMiuix(
                            label = "电池白名单",
                            backgroundColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                            fontSize = 9.sp
                        )
                    }
                    if (app.isSystem) {
                        StatusTagMiuix(
                            label = "SYSTEM",
                            backgroundColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer
                        )
                    }
                    if (app.inWhiteSet) {
                        StatusTagMiuix(
                            label = "免冻白名单",
                            backgroundColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                            fontSize = 9.sp
                        )
                    }
                    if (app.inMessageSet) {
                        StatusTagMiuix(
                            label = "消息应用",
                            backgroundColor = colorScheme.tertiaryContainer,
                            contentColor = colorScheme.onTertiaryContainer,
                            fontSize = 9.sp
                        )
                    }
                    if (app.inForceFreezeSet) {
                        StatusTagMiuix(
                            label = "强制冻结",
                            backgroundColor = colorScheme.error,
                            contentColor = colorScheme.onError,
                            fontSize = 9.sp
                        )
                    }

                    if (app.inNotFreezeFgsSet) {
                        StatusTagMiuix(
                            label = "FGS免冻",
                            backgroundColor = Color.Transparent,
                            contentColor = colorScheme.primary,
                            borderColor = colorScheme.primary
                        )
                    }
                    if (app.inBlockThawSet) {
                        StatusTagMiuix(
                            label = "禁止解冻",
                            backgroundColor = Color.Transparent,
                            contentColor = colorScheme.error,
                            borderColor = colorScheme.error
                        )
                    }
                    if (app.inForbidSyncSet) {
                        StatusTagMiuix(
                            label = "禁止同步",
                            backgroundColor = Color.Transparent,
                            contentColor = colorScheme.error,
                            borderColor = colorScheme.error
                        )
                    }
                    if (app.inCloudDozeWhite) {
                        StatusTagMiuix(
                            label = "云免冻",
                            backgroundColor = Color.Transparent,
                            contentColor = colorScheme.onSurfaceVariantSummary,
                            borderColor = colorScheme.outline
                        )
                    }
                    if (app.inCloudFrozenWhite) {
                        StatusTagMiuix(
                            label = "云墓碑白",
                            backgroundColor = Color.Transparent,
                            contentColor = colorScheme.onSurfaceVariantSummary,
                            borderColor = colorScheme.outline
                        )
                    }
                    if (app.inCloudLaunchAllow) {
                        StatusTagMiuix(
                            label = "云启动放行",
                            backgroundColor = Color.Transparent,
                            contentColor = colorScheme.onSurfaceVariantSummary,
                            borderColor = colorScheme.outline
                        )
                    }
                }
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 策略中枢卡片所需的汇总数据，一次计算后交给 UI，避免在重组中反复读 prefs。 */
internal data class PolicyHubSummary(
    /** 模板 ID → 当前完全匹配该模板的应用数 */
    val usageByTemplate: Map<String, Int>,
    /** 匹配任一模板的应用数（按包名 + UID 去重） */
    val appliedApps: Int,
    /** 本机已安装且命中任一云白名单的独立包数 */
    val cloudInstalledCount: Int,
)

internal fun buildPolicyHubSummary(
    prefs: SharedPreferences,
    apps: List<InstalledAppUiModel>,
    templates: List<PolicyTemplate>,
): PolicyHubSummary {
    val snapshot = PolicyMembershipSnapshot.read(prefs)
    val flagsByApp = apps.mapNotNull { app ->
        snapshot.flagsOf(app).takeIf { it.isNotEmpty() }?.let { app to it }
    }
    val usage = templates.associate { template ->
        template.id to flagsByApp.count { (_, flags) -> template.matches(flags) }
    }
    val applied = flagsByApp.count { (_, flags) -> templates.any { it.matches(flags) } }
    // 云名单按包名下发，双开实例与主应用算同一个。
    val cloudInstalled = apps.asSequence()
        .filter { it.inCloudDozeWhite || it.inCloudFrozenWhite || it.inCloudLaunchAllow }
        .map { it.packageName }
        .distinct()
        .count()
    return PolicyHubSummary(
        usageByTemplate = usage,
        appliedApps = applied,
        cloudInstalledCount = cloudInstalled,
    )
}

@Composable
private fun PolicyHubCards(
    templateCount: Int,
    summary: PolicyHubSummary,
    cloudLists: CloudListCodec.CloudLists?,
    onOpenTemplates: () -> Unit,
    onOpenCloudWhitelist: () -> Unit,
) {
    val templateSubtitle = if (summary.appliedApps > 0) {
        "$templateCount 套模板 · ${summary.appliedApps} 个在用"
    } else {
        "$templateCount 套模板"
    }
    val cloudSubtitle = when {
        summary.cloudInstalledCount > 0 -> "3 份名单 · 本机 ${summary.cloudInstalledCount} 个"
        cloudLists != null && !cloudLists.isEmpty -> "已读取 · 本机无匹配"
        else -> "尚未读取到云名单"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .padding(horizontal = 12.dp)
            .padding(top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PolicyEntryCard(
            icon = Icons.Rounded.Layers,
            title = "策略模板",
            subtitle = templateSubtitle,
            onClick = onOpenTemplates,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        PolicyEntryCard(
            icon = Icons.Rounded.Cloud,
            title = "官方云白名单",
            subtitle = cloudSubtitle,
            onClick = onOpenCloudWhitelist,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun PolicyEntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(14.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }
}
