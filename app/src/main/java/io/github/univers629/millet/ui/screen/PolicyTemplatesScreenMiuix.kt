package io.github.univers629.millet.ui.screen
import io.github.univers629.millet.ui.util.GlassOverlayListPopup
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import io.github.univers629.millet.ui.util.PopupBackdropHolder
import io.github.univers629.millet.ui.util.MilletTopAppBar
import io.github.univers629.millet.ui.util.MilletSmallTopAppBar
import io.github.univers629.millet.ui.util.BarIconButton

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univers629.millet.core.PolicyFlag
import io.github.univers629.millet.core.PolicyRules
import io.github.univers629.millet.core.PolicyTemplate
import io.github.univers629.millet.core.PolicyTemplateCodec
import io.github.univers629.millet.core.PrefReader
import io.github.univers629.millet.ui.component.AppIconImage
import io.github.univers629.millet.ui.component.DropdownItem
import io.github.univers629.millet.ui.component.ListPopupDefaults
import io.github.univers629.millet.ui.component.MiuixConfirmDialog
import io.github.univers629.millet.ui.component.StatusTagMiuix
import io.github.univers629.millet.ui.component.WarningCard
import io.github.univers629.millet.ui.component.WarningLevel
import io.github.univers629.millet.ui.theme.LocalEnableBlur
import io.github.univers629.millet.ui.util.BlurredBar
import io.github.univers629.millet.ui.util.PolicyMembershipSnapshot
import io.github.univers629.millet.ui.util.PolicyTemplateApplier
import io.github.univers629.millet.ui.util.PolicyTemplateStore
import io.github.univers629.millet.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import io.github.univers629.millet.ui.util.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// ============================================================================================
// 通用二级页骨架
// ============================================================================================

/**
 * 策略相关二级页的统一骨架：毛玻璃顶栏 + 返回键 + 可滚动列表。
 *
 * 与 `AppProfileScreenMiuix` 的结构保持一致，转场与侧滑返回仍由宿主的 `NavDisplay` 负责。
 */
@Composable
internal fun PolicySubPageScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                MilletTopAppBar(
                    color = barColor,
                    title = title,
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
                    },
                    actions = actions,
                    scrollBehavior = scrollBehavior
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 12.dp)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding,
                overscrollEffect = null
            ) {
                content()
                item(key = "policy_sub_page_bottom") {
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

/** 应用行：图标 + 名称 + 副标题 + 可选尾随内容，策略页内统一使用。 */
@Composable
internal fun PolicyAppRowCard(
    app: InstalledAppUiModel,
    subtitle: String,
    onClick: (() -> Unit)?,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp)
        .padding(bottom = 10.dp)
    val cardMargin = PaddingValues(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                applicationInfo = app.applicationInfo,
                label = app.appName,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(42.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (app.isDualApp) {
                        Spacer(Modifier.width(6.dp))
                        StatusTagMiuix(
                            label = "双开",
                            backgroundColor = colorScheme.tertiaryContainer,
                            contentColor = colorScheme.onTertiaryContainer
                        )
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
            }
            trailing()
        }
    }
    if (onClick != null) {
        Card(
            modifier = cardModifier,
            onClick = onClick,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Sink,
            insideMargin = cardMargin
        ) {
            rowContent()
        }
    } else {
        Card(
            modifier = cardModifier,
            insideMargin = cardMargin
        ) {
            rowContent()
        }
    }
}

private fun policyStateLabel(flags: Set<PolicyFlag>, templates: List<PolicyTemplate>): String {
    if (flags.isEmpty()) return "未配置"
    val matched = templates.firstOrNull { it.matches(flags) }
    return if (matched != null) "当前：${matched.name}" else "当前：自定义组合（${PolicyFlag.describe(flags)}）"
}

private fun applyResultMessage(
    describeChanged: (Int) -> String,
    result: PolicyTemplateApplier.Result,
): String = buildString {
    append(describeChanged(result.changed))
    if (result.unchanged > 0) append("，${result.unchanged} 个无需变更")
    if (result.skipped > 0) append("，${result.skipped} 个不受模块管理已跳过")
    if (result.pendingBatterySync > 0) append("；正在后台同步 ${result.pendingBatterySync} 个应用的系统省电策略")
}

// ============================================================================================
// 模板总览页
// ============================================================================================

/**
 * 策略模板总览：预设与自定义模板列表，可新建自定义模板。
 *
 * @param templateVersion 自定义模板变化后宿主递增，用于重新读取模板
 * @param onTemplatesChanged 自定义模板增删改后回调，宿主据此递增 [templateVersion]
 */
@Composable
fun PolicyTemplatesScreenMiuix(
    apps: List<InstalledAppUiModel>,
    templateVersion: Int,
    onBack: () -> Unit,
    onOpenTemplate: (String) -> Unit,
    onTemplatesChanged: () -> Unit,
    policyVersion: Int = 0,
) {
    val context = LocalContext.current
    val prefs = remember { PrefReader.openUiPrefs(context) }
    val templates = remember(templateVersion) { PolicyTemplateStore.loadAll(prefs) }
    val summary = remember(apps, templates, policyVersion) { buildPolicyHubSummary(prefs, apps, templates) }
    var showEditor by remember { mutableStateOf(false) }

    PolicySubPageScaffold(
        title = "策略模板",
        onBack = onBack,
        actions = {
            IconButton(
                modifier = Modifier.padding(end = 12.dp),
                onClick = { showEditor = true }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "新建自定义模板",
                    tint = colorScheme.onSurface
                )
            }
        }
    ) {
        item(key = "intro") {
            WarningCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                message = "套用模板会用模板组合覆盖应用现有的 7 项策略，未包含的策略会被关闭；互斥与依附规则与应用配置页一致。",
                level = WarningLevel.Notice
            )
        }

        item(key = "presets") {
            SmallTitle(text = "预设模板")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                templates.filter { it.builtIn }.forEach { template ->
                    TemplateArrowRow(
                        template = template,
                        usage = summary.usageByTemplate[template.id] ?: 0,
                        onClick = { onOpenTemplate(template.id) }
                    )
                }
            }
        }

        item(key = "customs") {
            SmallTitle(text = "自定义模板")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                templates.filterNot { it.builtIn }.forEach { template ->
                    TemplateArrowRow(
                        template = template,
                        usage = summary.usageByTemplate[template.id] ?: 0,
                        onClick = { onOpenTemplate(template.id) }
                    )
                }
                ArrowPreference(
                    startAction = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(24.dp),
                            tint = colorScheme.primary
                        )
                    },
                    title = "新建自定义模板",
                    summary = "命名并勾选需要的策略，之后可批量套用到应用",
                    onClick = { showEditor = true }
                )
            }
        }
    }

    PolicyTemplateEditorSheet(
        show = showEditor,
        initial = null,
        existingTemplates = templates,
        onDismiss = { showEditor = false },
        onSave = { name, flags ->
            showEditor = false
            val saved = PolicyTemplateStore.saveCustom(prefs, null, name, flags)
            if (saved != null) {
                onTemplatesChanged()
                Toast.makeText(context, "已创建模板「${saved.name}」", Toast.LENGTH_SHORT).show()
                onOpenTemplate(saved.id)
            } else {
                Toast.makeText(context, "模板保存失败：名称或策略组合无效", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

@Composable
private fun TemplateArrowRow(
    template: PolicyTemplate,
    usage: Int,
    onClick: () -> Unit,
) {
    ArrowPreference(
        startAction = {
            Icon(
                imageVector = if (template.builtIn) Icons.Rounded.Layers else Icons.Rounded.Tune,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp),
                tint = colorScheme.onBackground
            )
        },
        title = template.name,
        summary = PolicyFlag.describe(template.flags),
        endActions = {
            Text(
                text = if (usage > 0) "$usage 个应用" else "未使用",
                fontSize = 13.sp,
                color = if (usage > 0) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(end = 4.dp)
            )
        },
        onClick = onClick
    )
}

// ============================================================================================
// 模板子页面
// ============================================================================================

/**
 * 模板详情：包含的策略、正在使用的应用，以及批量套用 / 移除。
 *
 * 「在用」= 应用当前七项状态与模板完全一致；状态直接读 prefs 快照，
 * 因为 `InstalledAppUiModel` 不含「忽略网络唤醒」。
 */
@Composable
fun PolicyTemplateDetailScreenMiuix(
    templateId: String,
    apps: List<InstalledAppUiModel>,
    templateVersion: Int,
    onBack: () -> Unit,
    onOpenAppProfile: (InstalledAppUiModel) -> Unit,
    onPolicyChanged: () -> Unit,
    onTemplatesChanged: () -> Unit,
    policyVersion: Int = 0,
) {
    val context = LocalContext.current
    val prefs = remember { PrefReader.openUiPrefs(context) }
    // 写入后立刻重读快照，不必等宿主异步重建应用列表。
    var localRevision by remember { mutableIntStateOf(0) }
    val allTemplates = remember(templateVersion) { PolicyTemplateStore.loadAll(prefs) }
    val template = remember(templateId, allTemplates) { allTemplates.firstOrNull { it.id == templateId } }
    val snapshot = remember(apps, localRevision, policyVersion) { PolicyMembershipSnapshot.read(prefs) }

    if (template == null) {
        PolicySubPageScaffold(title = "策略模板", onBack = onBack) {
            item(key = "missing") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    onClick = onBack,
                    showIndication = true
                ) {
                    BasicComponent(
                        title = "模板不存在",
                        summary = "该自定义模板可能已被删除，点击返回"
                    )
                }
            }
        }
        return
    }

    val users = remember(template, apps, snapshot) {
        apps.filter { PolicyTemplateApplier.isTargetable(it) && template.matches(snapshot.flagsOf(it)) }
            .sortedWith(compareBy<InstalledAppUiModel> { it.appName.lowercase() }.thenBy { it.isDualApp })
    }

    var showPicker by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<List<InstalledAppUiModel>?>(null) }
    var pendingResync by remember { mutableStateOf<Pair<List<InstalledAppUiModel>, Set<PolicyFlag>>?>(null) }

    fun applyTo(
        targets: List<InstalledAppUiModel>,
        flags: Set<PolicyFlag>,
        describeChanged: (Int) -> String,
    ) {
        if (targets.isEmpty()) return
        val result = PolicyTemplateApplier.apply(context, targets, flags, onPolicyChanged)
        localRevision++
        Toast.makeText(context, applyResultMessage(describeChanged, result), Toast.LENGTH_LONG).show()
    }

    PolicySubPageScaffold(
        title = template.name,
        onBack = onBack,
        actions = {
            if (!template.builtIn) {
                val showTopPopup = remember { mutableStateOf(false) }
                IconButton(
                    modifier = Modifier.padding(end = 12.dp),
                    onClick = { showTopPopup.value = true },
                    holdDownState = showTopPopup.value
                ) {
                    Icon(
                        imageVector = MiuixIcons.MoreCircle,
                        tint = colorScheme.onSurface,
                        contentDescription = "模板操作"
                    )
                }
                GlassOverlayListPopup(
                    show = showTopPopup.value,
                    popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                    alignment = PopupPositionProvider.Align.TopEnd,
                    onDismissRequest = { showTopPopup.value = false },
                    content = {
                        ListPopupColumn {
                            val items = listOf("编辑模板", "删除模板")
                            items.forEachIndexed { idx, text ->
                                DropdownItem(
                                    text = text,
                                    optionSize = items.size,
                                    index = idx,
                                    onSelectedIndexChange = {
                                        showTopPopup.value = false
                                        when (idx) {
                                            0 -> showEditor = true
                                            1 -> showDeleteConfirm = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) {
        // 1. 模板头部
        item(key = "header") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                insideMargin = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (template.builtIn) Icons.Rounded.Layers else Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = template.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight(600),
                            color = colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusTagMiuix(
                                label = if (template.builtIn) "预设" else "自定义",
                                backgroundColor = colorScheme.secondaryContainer,
                                contentColor = colorScheme.onSecondaryContainer
                            )
                            StatusTagMiuix(
                                label = "${users.size} 个应用在用",
                                backgroundColor = if (users.isEmpty()) Color.Transparent else colorScheme.primary,
                                contentColor = if (users.isEmpty()) colorScheme.onSurfaceVariantSummary else colorScheme.onPrimary,
                                borderColor = if (users.isEmpty()) colorScheme.outline else null
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = template.description,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colorScheme.onSurfaceVariantSummary
                )
            }
        }

        // 2. 风险提示
        item(key = "warnings") {
            if (PolicyFlag.WHITE in template.flags) {
                WarningCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    message = "套用后主用户应用会同步把系统省电策略设为无限制；移除或改套其他模板时恢复为智能推荐。需要模块在线或 Root 授权，失败会自动回滚。",
                    level = WarningLevel.Notice
                )
            }
            if (PolicyFlag.IGNORE_NETWORK_WAKE in template.flags) {
                WarningCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    message = "该模板包含「忽略网络唤醒」：冻结会保持到手动打开，聊天类应用的消息会明显延迟。",
                    level = WarningLevel.Notice
                )
            }
        }

        // 3. 包含的策略
        item(key = "flags") {
            SmallTitle(text = "包含的策略")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp)
            ) {
                PolicyFlag.entries.forEach { flag ->
                    val on = flag in template.flags
                    BasicComponent(
                        title = flag.label,
                        summary = flag.summary,
                        endActions = {
                            StatusTagMiuix(
                                label = if (on) "开启" else "关闭",
                                backgroundColor = if (on) colorScheme.primary else Color.Transparent,
                                contentColor = if (on) colorScheme.onPrimary else colorScheme.onSurfaceVariantSummary,
                                borderColor = if (on) null else colorScheme.outline,
                                fontSize = 10.sp
                            )
                        }
                    )
                }
            }
            Text(
                text = "套用时，标为「关闭」的策略会从应用上移除。",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        // 4. 批量操作
        item(key = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "全部移除",
                    onClick = { pendingRemoval = users },
                    enabled = users.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showPicker = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(
                        text = "批量套用",
                        color = colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 5. 正在使用的应用
        item(key = "users_title") {
            SmallTitle(text = "正在使用 (${users.size})")
        }
        if (users.isEmpty()) {
            item(key = "users_empty") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    onClick = { showPicker = true },
                    showIndication = true
                ) {
                    BasicComponent(
                        title = "暂无应用使用此模板",
                        summary = "点击选择应用并一键套用"
                    )
                }
            }
        } else {
            items(users, key = { "user:${it.packageName}:${it.uid}" }) { app ->
                PolicyAppRowCard(
                    app = app,
                    subtitle = if (app.isDualApp) "${app.packageName} · UID ${app.uid}" else app.packageName,
                    onClick = { onOpenAppProfile(app) },
                    trailing = {
                        IconButton(
                            onClick = { pendingRemoval = listOf(app) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Close,
                                contentDescription = "移除策略",
                                tint = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        }
    }

    PolicyAppPickerSheet(
        show = showPicker,
        template = template,
        apps = apps,
        snapshot = snapshot,
        templates = allTemplates,
        onDismiss = { showPicker = false },
        onConfirm = { selected ->
            showPicker = false
            applyTo(selected, template.flags) { "已将「${template.name}」套用到 $it 个应用" }
        }
    )

    PolicyTemplateEditorSheet(
        show = showEditor,
        initial = template,
        existingTemplates = allTemplates,
        onDismiss = { showEditor = false },
        onSave = { name, flags ->
            showEditor = false
            val previousUsers = users
            val saved = PolicyTemplateStore.saveCustom(prefs, template.id, name, flags)
            if (saved == null) {
                Toast.makeText(context, "模板保存失败：名称或策略组合无效", Toast.LENGTH_SHORT).show()
            } else {
                onTemplatesChanged()
                if (saved.flags != template.flags && previousUsers.isNotEmpty()) {
                    // 组合变了：已套用旧组合的应用不会自动跟随，交给用户决定是否同步。
                    pendingResync = previousUsers to saved.flags
                } else {
                    Toast.makeText(context, "模板已保存", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val removal = pendingRemoval
    MiuixConfirmDialog(
        show = removal != null,
        title = "移除策略",
        message = buildString {
            append("将关闭 ${removal?.size ?: 0} 个应用的全部 7 项策略，恢复为未配置状态。")
            if (PolicyFlag.WHITE in template.flags) append("主用户应用的系统省电策略会恢复为智能推荐。")
        },
        confirmText = "移除",
        onConfirm = {
            val targets = removal.orEmpty()
            pendingRemoval = null
            applyTo(targets, emptySet()) { "已移除 $it 个应用的全部策略" }
        },
        onDismiss = { pendingRemoval = null }
    )

    val resync = pendingResync
    MiuixConfirmDialog(
        show = resync != null,
        title = "同步到已套用的应用？",
        message = "模板组合已变更。是否把新组合同步到之前使用该模板的 ${resync?.first?.size ?: 0} 个应用？不同步则它们保持原状态。",
        confirmText = "同步",
        dismissText = "暂不",
        onConfirm = {
            val pending = resync
            pendingResync = null
            if (pending != null) applyTo(pending.first, pending.second) { "已将新组合同步到 $it 个应用" }
        },
        onDismiss = { pendingResync = null }
    )

    MiuixConfirmDialog(
        show = showDeleteConfirm,
        title = "删除模板",
        message = "删除「${template.name}」不会改动已套用应用的策略，它们之后会显示为自定义组合。",
        confirmText = "删除",
        onConfirm = {
            showDeleteConfirm = false
            PolicyTemplateStore.deleteCustom(prefs, template.id)
            onTemplatesChanged()
            onBack()
        },
        onDismiss = { showDeleteConfirm = false }
    )
}

// ============================================================================================
// 编辑器 BottomSheet
// ============================================================================================

/**
 * 自定义模板编辑器。
 *
 * 开关联动全部经由 [PolicyRules.toggle]，与应用配置页逐条一致；依附强制冻结的三项
 * 在强制冻结关闭时禁用，和配置页的折叠区行为对应。
 */
@Composable
internal fun PolicyTemplateEditorSheet(
    show: Boolean,
    initial: PolicyTemplate?,
    existingTemplates: List<PolicyTemplate>,
    onDismiss: () -> Unit,
    onSave: (name: String, flags: Set<PolicyFlag>) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var flags by remember { mutableStateOf(initial?.flags.orEmpty()) }
    LaunchedEffect(show, initial?.id) {
        if (show) {
            name = initial?.name.orEmpty()
            flags = initial?.flags.orEmpty()
        }
    }

    val trimmed = name.trim()
    val others = existingTemplates.filter { it.id != initial?.id }
    val duplicateName = others.firstOrNull { it.name == trimmed }
    val sameCombo = others.firstOrNull { flags.isNotEmpty() && it.matches(flags) }
    val blockingError = when {
        trimmed.isEmpty() -> "请输入模板名称"
        duplicateName != null -> "名称与「${duplicateName.name}」重复"
        flags.isEmpty() -> "至少开启一项策略"
        else -> null
    }
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()

    GlassBottomSheet(backdrop = PopupBackdropHolder.current, 
        show = show,
        title = if (initial == null) "新建自定义模板" else "编辑模板",
        insideMargin = DpSize(16.dp, 0.dp),
        onDismissRequest = onDismiss,
    ) {
        val dismissState = LocalDismissState.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = navBottomPadding + 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                TemplateNameField(
                    value = name,
                    onValueChange = { name = it.take(PolicyTemplateCodec.MAX_NAME_LENGTH) }
                )
                SmallTitle(text = "冻结豁免与保活")
                Card(modifier = Modifier.fillMaxWidth()) {
                    listOf(PolicyFlag.WHITE, PolicyFlag.MESSAGE, PolicyFlag.NOT_FREEZE_FGS).forEach { flag ->
                        SwitchPreference(
                            title = flag.label,
                            summary = flag.summary,
                            checked = flag in flags,
                            onCheckedChange = { checked -> flags = PolicyRules.toggle(flags, flag, checked) }
                        )
                    }
                }
                SmallTitle(text = "强力冻结与唤醒压制")
                Card(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        PolicyFlag.FORCE_FREEZE,
                        PolicyFlag.BLOCK_THAW,
                        PolicyFlag.FORBID_SYNC,
                        PolicyFlag.IGNORE_NETWORK_WAKE,
                    ).forEach { flag ->
                        val enabled = !flag.dependsOnForceFreeze || PolicyFlag.FORCE_FREEZE in flags
                        SwitchPreference(
                            title = flag.label,
                            summary = flag.summary,
                            checked = flag in flags,
                            enabled = enabled,
                            onCheckedChange = { checked -> flags = PolicyRules.toggle(flags, flag, checked) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            val hint = when {
                blockingError != null -> blockingError
                sameCombo != null -> "与「${sameCombo.name}」组合相同，套用效果一致"
                initial != null && flags != initial.flags -> "修改组合后，可选择是否同步到已套用的应用"
                else -> "已选：${PolicyFlag.describe(flags)}"
            }
            Text(
                text = hint,
                fontSize = 12.sp,
                color = if (blockingError != null) colorScheme.error else colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        onDismiss()
                        dismissState?.invoke()
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "保存",
                    onClick = {
                        onSave(trimmed, flags)
                        dismissState?.invoke()
                    },
                    enabled = blockingError == null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun TemplateNameField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = colorScheme.onSurface
        ),
        cursorBrush = SolidColor(colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(min = 48.dp)
            .background(colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp)),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "模板名称（最多 ${PolicyTemplateCodec.MAX_NAME_LENGTH} 字）",
                        fontSize = 15.sp,
                        color = colorScheme.onSurfaceVariantSummary
                    )
                }
                innerTextField()
            }
        }
    )
}

// ============================================================================================
// 应用选择 BottomSheet
// ============================================================================================

@Composable
private fun PolicyAppPickerSheet(
    show: Boolean,
    template: PolicyTemplate,
    apps: List<InstalledAppUiModel>,
    snapshot: PolicyMembershipSnapshot,
    templates: List<PolicyTemplate>,
    onDismiss: () -> Unit,
    onConfirm: (List<InstalledAppUiModel>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var includeSystem by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(show) {
        if (show) {
            query = ""
            selected = emptySet()
        }
    }

    // 全部可套用对象（不含已处于该组合的应用）；按搜索词切换时不丢失此前勾选项。
    val allEligible = remember(apps, snapshot, template) {
        apps.asSequence()
            .filter { PolicyTemplateApplier.isTargetable(it) }
            .map { it to snapshot.flagsOf(it) }
            .filter { (_, flags) -> !template.matches(flags) }
            .sortedWith(
                compareByDescending<Pair<InstalledAppUiModel, Set<PolicyFlag>>> { it.second.isNotEmpty() }
                    .thenBy { it.first.appName.lowercase() }
                    .thenBy { it.first.isDualApp }
            )
            .toList()
    }
    // 当前列表可见候选：系统应用默认隐藏，但已有配置的始终可见。
    val candidates = remember(allEligible, query, includeSystem) {
        val q = query.trim().lowercase()
        allEligible.filter { (app, flags) ->
            (includeSystem || !app.isSystem || flags.isNotEmpty()) &&
                (q.isEmpty() ||
                    app.appName.lowercase().contains(q) ||
                    app.packageName.lowercase().contains(q))
        }
    }
    val keyOf: (InstalledAppUiModel) -> String = { "${it.packageName}:${it.uid}" }
    val chosenApps = remember(allEligible, selected) {
        allEligible.map { it.first }.filter { keyOf(it) in selected }
    }
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()

    GlassBottomSheet(backdrop = PopupBackdropHolder.current, 
        show = show,
        title = "套用「${template.name}」",
        insideMargin = DpSize(16.dp, 0.dp),
        onDismissRequest = onDismiss,
    ) {
        val dismissState = LocalDismissState.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = navBottomPadding + 16.dp)
        ) {
            MiuixCapsuleSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = "搜索应用名称或包名..."
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PickerChip(
                    text = "含系统应用",
                    selected = includeSystem,
                    onClick = { includeSystem = !includeSystem }
                )
                Spacer(Modifier.weight(1f))
                PickerChip(
                    text = "全选结果",
                    selected = false,
                    onClick = { selected = selected + candidates.map { keyOf(it.first) } }
                )
                PickerChip(
                    text = "清空",
                    selected = false,
                    onClick = { selected = emptySet() }
                )
            }
            if (candidates.isEmpty()) {
                Text(
                    text = if (query.isBlank()) "没有可套用的应用" else "没有匹配的应用",
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight)
                ) {
                    items(candidates, key = { "pick:${keyOf(it.first)}" }) { (app, flags) ->
                        val key = keyOf(app)
                        val checked = key in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selected = if (checked) selected - key else selected + key }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SelectionIndicator(checked = checked)
                            AppIconImage(
                                applicationInfo = app.applicationInfo,
                                label = app.appName,
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 12.dp)
                                    .size(38.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (app.isDualApp) "${app.appName}（双开）" else app.appName,
                                    fontWeight = FontWeight(550),
                                    color = colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = policyStateLabel(flags, templates),
                                    fontSize = 12.sp,
                                    color = if (flags.isEmpty()) colorScheme.onSurfaceVariantSummary else colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = "所选应用现有的 7 项策略会被替换为「${template.name}」的组合。",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
            Button(
                onClick = {
                    onConfirm(chosenApps)
                    dismissState?.invoke()
                },
                enabled = chosenApps.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(
                    text = if (chosenApps.isNotEmpty()) "套用到 ${chosenApps.size} 个应用" else "请选择应用",
                    color = colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun PickerChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariantSummary
        )
    }
}

/** 自绘勾选指示器：不依赖 Miuix `Checkbox` 的签名（不同版本差异较大）。 */
@Composable
private fun SelectionIndicator(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (checked) colorScheme.primary else Color.Transparent)
            .then(
                if (checked) Modifier else Modifier.border(1.5.dp, colorScheme.outline, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                tint = colorScheme.onPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
