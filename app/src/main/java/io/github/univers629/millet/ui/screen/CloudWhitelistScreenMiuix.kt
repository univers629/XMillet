package io.github.univers629.millet.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.univers629.millet.core.CloudListCodec
import io.github.univers629.millet.core.PrefReader
import io.github.univers629.millet.core.PrefsKeys
import io.github.univers629.millet.ui.component.StatusTagMiuix
import io.github.univers629.millet.ui.component.WarningCard
import io.github.univers629.millet.ui.component.WarningLevel
import io.github.univers629.millet.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** PowerKeeper 云端配置库的三条白名单分类。 */
private enum class CloudWhitelistTab(
    val title: String,
    val configKey: String,
    val description: String,
) {
    DOZE(
        title = "云免冻",
        configKey = "dozeWhiteListApps",
        description = "Doze 深度休眠云端白名单：进入深度休眠时由系统豁免的应用。",
    ),
    FROZEN(
        title = "云墓碑白",
        configKey = "FrozenNewWhiteList",
        description = "新版墓碑云端白名单：PowerKeeper / Greezer 官方默认免冻结名单。",
    ),
    LAUNCH(
        title = "云启动放行",
        configKey = "launchRestrict",
        description = "启动限制云端放行名单：受限时段内仍允许后台启动的应用。",
    );

    fun packagesOf(lists: CloudListCodec.CloudLists): Set<String> = when (this) {
        DOZE -> lists.dozeWhiteList
        FROZEN -> lists.frozenNewWhiteList
        LAUNCH -> lists.launchRestrict
    }
}

/**
 * 官方云白名单子页面：展示 PowerKeeper 读取的三条云端名单（dozeWhiteList / frozenNewWhiteList / launchRestrict），
 * 并提供「云免冻」与「云墓碑白」总开关以屏蔽或恢复官方云端白名单（云启动放行保持只读）。
 *
 * 云名单由 PowerKeeper 进程在开机或模块重载后异步上报，晚于首屏到达属于正常现象，
 * 未读取到时展示等待提示并允许手动刷新，不发起任何网络请求。
 */
@Composable
fun CloudWhitelistScreenMiuix(
    cloudLists: CloudListCodec.CloudLists?,
    apps: List<InstalledAppUiModel>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenAppProfile: (InstalledAppUiModel) -> Unit,
    configVersion: Int = 0,
    onConfigChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { PrefReader.openUiPrefs(context) }
    var blockCloudDoze by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(prefs, PrefsKeys.BLOCK_CLOUD_DOZE_WHITELIST))
    }
    var blockCloudFrozen by remember(configVersion) {
        mutableStateOf(PrefReader.readUiBoolean(prefs, PrefsKeys.BLOCK_CLOUD_FROZEN_WHITELIST))
    }
    val isDark = isInDarkTheme()
    val blockedBg = if (isDynamicColor) {
        colorScheme.tertiaryContainer
    } else if (isDark) {
        Color(0xFF3E2F1B)
    } else {
        Color(0xFFFFF0DB)
    }
    val blockedFg = if (isDynamicColor) {
        colorScheme.onTertiaryContainer
    } else if (isDark) {
        Color(0xFFF5B041)
    } else {
        Color(0xFFB45309)
    }

    val updateCloudDozeEnabled: (Boolean) -> Unit = { enabled ->
        val nextBlock = !enabled
        blockCloudDoze = nextBlock
        PrefReader.putBooleanAndNotify(prefs, PrefsKeys.BLOCK_CLOUD_DOZE_WHITELIST, nextBlock)
        onConfigChanged()
    }
    val updateCloudFrozenEnabled: (Boolean) -> Unit = { enabled ->
        val nextBlock = !enabled
        blockCloudFrozen = nextBlock
        PrefReader.putBooleanAndNotify(prefs, PrefsKeys.BLOCK_CLOUD_FROZEN_WHITELIST, nextBlock)
        onConfigChanged()
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showUninstalled by rememberSaveable { mutableStateOf(false) }

    // 优先使用完整的云名单（包含未安装包名）；若调用方未传，则回退复用应用列表上的云标记。
    val effectiveLists = remember(cloudLists, apps) {
        if (cloudLists != null && !cloudLists.isEmpty) {
            cloudLists
        } else {
            val fromApps = CloudListCodec.CloudLists(
                dozeWhiteList = apps.asSequence().filter { it.inCloudDozeWhite }.map { it.packageName }.toSet(),
                frozenNewWhiteList = apps.asSequence().filter { it.inCloudFrozenWhite }.map { it.packageName }.toSet(),
                launchRestrict = apps.asSequence().filter { it.inCloudLaunchAllow }.map { it.packageName }.toSet(),
            )
            fromApps.takeUnless { it.isEmpty }
        }
    }

    val currentTab = CloudWhitelistTab.entries.getOrElse(selectedTabIndex) { CloudWhitelistTab.DOZE }
    val appsByPackage = remember(apps) { apps.groupBy { it.packageName } }

    val tabPackages = remember(effectiveLists, currentTab) {
        effectiveLists?.let { currentTab.packagesOf(it) }.orEmpty()
    }
    val allInstalledInTab = remember(tabPackages, appsByPackage) {
        tabPackages.asSequence()
            .flatMap { pkg -> appsByPackage[pkg].orEmpty().asSequence() }
            .sortedWith(
                compareByDescending<InstalledAppUiModel> { it.hasAnyConfig }
                    .thenBy { it.appName.lowercase() }
                    .thenBy { it.packageName }
                    .thenBy { it.isDualApp }
            )
            .toList()
    }
    val allUninstalledInTab = remember(tabPackages, appsByPackage) {
        tabPackages.asSequence()
            .filter { it !in appsByPackage }
            .sorted()
            .toList()
    }

    val installedMatches = remember(allInstalledInTab, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            allInstalledInTab
        } else {
            allInstalledInTab.filter { app ->
                app.appName.lowercase().contains(q) ||
                    app.packageName.lowercase().contains(q) ||
                    app.uid.toString().contains(q)
            }
        }
    }
    val uninstalledMatches = remember(allUninstalledInTab, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            allUninstalledInTab
        } else {
            allUninstalledInTab.filter { pkg -> pkg.lowercase().contains(q) }
        }
    }

    PolicySubPageScaffold(
        title = "官方云白名单",
        onBack = onBack,
        actions = {
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = "刷新云端名单",
                    tint = colorScheme.onSurface
                )
            }
        }
    ) {
        if (effectiveLists == null) {
            item(key = "cloud_unavailable") {
                WarningCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    message = "尚未读取到 PowerKeeper 云端名单。开机或模块热重载后上报可能晚于首屏到达，请稍候刷新；若持续为空，请在 LSPosed 中确认已勾选「电量与性能」。",
                    level = WarningLevel.Notice
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    onClick = onRefresh,
                    showIndication = true,
                    pressFeedbackType = PressFeedbackType.Sink
                ) {
                    BasicComponent(
                        title = if (isRefreshing) "正在读取云端名单…" else "点击重新读取",
                        summary = "数据来自 PowerKeeper 本地云端配置，不发起网络请求"
                    )
                }
                SmallTitle(text = "云名单拦截控制")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    SwitchPreference(
                        title = "启用官方云免冻",
                        summary = if (blockCloudDoze) {
                            "已屏蔽 dozeWhiteListApps 读取与更新，由本地策略接管"
                        } else {
                            "允许系统使用官方云端 Doze 免休眠名单"
                        },
                        checked = !blockCloudDoze,
                        onCheckedChange = updateCloudDozeEnabled
                    )
                    SwitchPreference(
                        title = "启用官方云墓碑白",
                        summary = "允许系统按官方云端名单免冻后台应用；本地「强制冻结」仍优先",
                        checked = !blockCloudFrozen,
                        onCheckedChange = updateCloudFrozenEnabled
                    )
                    BasicComponent(
                        title = "云启动放行（launchRestrict · 只读）",
                        summary = "含输入法、相册、媒体提供器等系统关键组件，不支持整表屏蔽"
                    )
                }
            }
            return@PolicySubPageScaffold
        }

        // 1. 顶部说明与搜索
        item(key = "cloud_header") {
            WarningCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                message = "以下三条名单由 PowerKeeper 从系统云端配置库读取（只读）；点击已安装应用可进入应用配置页调整本地策略。",
                level = WarningLevel.Notice
            )
            MiuixCapsuleSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "搜索应用名称或包名..."
            )
            Spacer(Modifier.height(8.dp))
            TabRowWithContour(
                tabs = CloudWhitelistTab.entries.map { it.title },
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
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
            Spacer(Modifier.height(10.dp))
        }

        // 2. 当前名单摘要、总开关与未安装切换
        item(key = "cloud_tab_meta") {
            val installedPkgCount = tabPackages.size - allUninstalledInTab.size
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = currentTab.configKey,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        when (currentTab) {
                            CloudWhitelistTab.DOZE -> StatusTagMiuix(
                                label = if (blockCloudDoze) "已屏蔽" else "已启用",
                                backgroundColor = if (blockCloudDoze) {
                                    blockedBg
                                } else {
                                    colorScheme.primary.copy(alpha = 0.14f)
                                },
                                contentColor = if (blockCloudDoze) blockedFg else colorScheme.primary,
                                fontSize = 10.sp
                            )

                            CloudWhitelistTab.FROZEN -> StatusTagMiuix(
                                label = if (blockCloudFrozen) "已屏蔽" else "已启用",
                                backgroundColor = if (blockCloudFrozen) {
                                    blockedBg
                                } else {
                                    colorScheme.primary.copy(alpha = 0.14f)
                                },
                                contentColor = if (blockCloudFrozen) blockedFg else colorScheme.primary,
                                fontSize = 10.sp
                            )

                            CloudWhitelistTab.LAUNCH -> StatusTagMiuix(
                                label = "只读",
                                backgroundColor = Color.Transparent,
                                contentColor = colorScheme.onSurfaceVariantSummary,
                                borderColor = colorScheme.outline,
                                fontSize = 10.sp
                            )
                        }
                        StatusTagMiuix(
                            label = "已装 $installedPkgCount / 共 ${tabPackages.size}",
                            backgroundColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = currentTab.description,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = colorScheme.onSurfaceVariantSummary
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                when (currentTab) {
                    CloudWhitelistTab.DOZE -> SwitchPreference(
                        title = "启用官方云免冻",
                        summary = if (blockCloudDoze) {
                            "已屏蔽官方 Doze 云白名单读取与新下发，由本地省电与 Doze 策略接管"
                        } else {
                            "允许 PowerKeeper 按官方云端名单豁免 Doze 深度休眠"
                        },
                        checked = !blockCloudDoze,
                        onCheckedChange = updateCloudDozeEnabled
                    )

                    CloudWhitelistTab.FROZEN -> SwitchPreference(
                        title = "启用官方云墓碑白",
                        summary = "允许系统按官方云端名单免冻后台应用；本地「强制冻结」仍优先",
                        checked = !blockCloudFrozen,
                        onCheckedChange = updateCloudFrozenEnabled
                    )

                    CloudWhitelistTab.LAUNCH -> BasicComponent(
                        title = "该名单仅支持查看",
                        summary = "含输入法、相册、媒体提供器等系统关键组件，整表屏蔽易引发异常；点击下方已安装应用可单独调整策略"
                    )
                }
            }

            if (allUninstalledInTab.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PickerChip(
                        text = "显示未安装包名 (${uninstalledMatches.size})",
                        selected = showUninstalled,
                        onClick = { showUninstalled = !showUninstalled }
                    )
                }
            }
        }

        // 3. 本机已安装应用（可点进单应用配置页）
        item(key = "cloud_installed_title") {
            SmallTitle(text = "本机已安装 (${installedMatches.size})")
        }
        if (installedMatches.isEmpty()) {
            item(key = "cloud_installed_empty") {
                val canRevealUninstalled = !showUninstalled && uninstalledMatches.isNotEmpty()
                val canResetSearch = searchQuery.isNotBlank()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp),
                    onClick = {
                        when {
                            canRevealUninstalled -> showUninstalled = true
                            canResetSearch -> searchQuery = ""
                        }
                    },
                    showIndication = canRevealUninstalled || canResetSearch,
                    pressFeedbackType = if (canRevealUninstalled || canResetSearch) {
                        PressFeedbackType.Sink
                    } else {
                        PressFeedbackType.None
                    }
                ) {
                    BasicComponent(
                        title = when {
                            canRevealUninstalled -> "已安装应用中无匹配项"
                            canResetSearch -> "没有匹配的应用或包名"
                            tabPackages.isEmpty() -> "该云端名单为空"
                            else -> "本机未安装该名单中的任何应用"
                        },
                        summary = when {
                            canRevealUninstalled -> "另有 ${uninstalledMatches.size} 个未安装的云端包名匹配，点击展开查看"
                            canResetSearch -> "点击清空搜索词"
                            tabPackages.isEmpty() -> "PowerKeeper 返回的该项配置当前不包含包名"
                            else -> "可在上方开启「显示未安装包名」查看完整名单"
                        }
                    )
                }
            }
        } else {
            items(installedMatches, key = { "cloud:${currentTab.name}:${it.packageName}:${it.uid}" }) { app ->
                PolicyAppRowCard(
                    app = app,
                    subtitle = if (app.isDualApp) "${app.packageName} · UID ${app.uid}" else app.packageName,
                    onClick = { onOpenAppProfile(app) },
                    trailing = {
                        if (app.hasAnyConfig) {
                            StatusTagMiuix(
                                label = "已配置",
                                backgroundColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary,
                                fontSize = 9.sp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariantActions,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(16.dp)
                        )
                    }
                )
            }
        }

        // 4. 未安装包的原始云端包名（可选展开）
        if (showUninstalled && uninstalledMatches.isNotEmpty()) {
            item(key = "cloud_uninstalled_title") {
                SmallTitle(text = "未安装 (${uninstalledMatches.size})")
            }
            items(uninstalledMatches, key = { "cloud_raw:${currentTab.name}:$it" }) { pkg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pkg,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        StatusTagMiuix(
                            label = "未安装",
                            backgroundColor = Color.Transparent,
                            contentColor = colorScheme.onSurfaceVariantSummary,
                            borderColor = colorScheme.outline,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}
