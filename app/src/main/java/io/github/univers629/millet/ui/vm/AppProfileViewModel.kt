package io.github.univers629.millet.ui.vm

import io.github.univers629.millet.core.ShellProcessSnapshot
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.PowerManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.univers629.millet.core.AppUidUtils
import io.github.univers629.millet.core.BatteryPolicyHelper
import io.github.univers629.millet.core.PrefsKeys
import io.github.univers629.millet.core.PrefsSnapshot
import io.github.univers629.millet.core.RootHelper
import io.github.univers629.millet.core.SystemBatteryOptimizationHelper
import io.github.univers629.millet.ipc.PowerKeeperBinderClient
import io.github.univers629.millet.ui.data.PolicyRepository
import io.github.univers629.millet.ui.data.RuntimeAndAppsRepository
import io.github.univers629.millet.ui.model.AppIconSource
import io.github.univers629.millet.ui.screen.InstalledAppUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 单应用策略页的系统交互桥接（进程快照扫描与系统省电策略读写，支持纯 JVM 测试替换）。
 */
interface AppProfileSystemBridge {
    suspend fun scanAppProcesses(uid: Int): List<RootHelper.ProcessRawEntry>?
    suspend fun readSystemBatteryPolicy(packageName: String): Pair<Boolean, String>
    suspend fun setIgnoringBatteryOptimization(packageName: String): Boolean
    suspend fun restoreBatteryOptimization(packageName: String): Boolean
    fun buildFallbackAppModel(packageName: String, uid: Int): InstalledAppUiModel
}

internal class AndroidAppProfileSystemBridge(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppProfileSystemBridge {

    private val appContext: Context = context.applicationContext ?: context

    override suspend fun scanAppProcesses(uid: Int): List<RootHelper.ProcessRawEntry>? =
        withContext(ioDispatcher) {
            val snapshot = PowerKeeperBinderClient.queryProcessSnapshot()
                ?: ShellProcessSnapshot.capture(appContext)
                ?: return@withContext null
            RootHelper.sortProcessEntries(snapshot.entries.filter { it.uid == uid })
        }

    override suspend fun readSystemBatteryPolicy(packageName: String): Pair<Boolean, String> =
        withContext(ioDispatcher) {
            val noRestrict = BatteryPolicyHelper.isPackageNoRestrict(appContext, packageName)
            val label = when {
                noRestrict -> "无限制（系统包名级记录）"
                appContext.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(packageName) == true -> "已忽略电池优化（AOSP 白名单）"
                else -> "未记录为无限制或忽略电池优化"
            }
            noRestrict to label
        }

    override suspend fun setIgnoringBatteryOptimization(packageName: String): Boolean =
        SystemBatteryOptimizationHelper.setAppIgnoringBatteryOptimization(appContext, packageName)

    override suspend fun restoreBatteryOptimization(packageName: String): Boolean =
        SystemBatteryOptimizationHelper.restoreAppBatteryOptimizationToDefault(appContext, packageName)

    override fun buildFallbackAppModel(packageName: String, uid: Int): InstalledAppUiModel {
        val pm = appContext.packageManager
        val info = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
        val label = info?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
        val pkgInfo = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
        return InstalledAppUiModel(
            appName = label ?: packageName,
            packageName = packageName,
            uid = if (uid > 0) uid else (info?.uid ?: 0),
            versionName = pkgInfo?.versionName.orEmpty(),
            versionCode = pkgInfo?.longVersionCode ?: 0L,
            isSystem = ((info?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0,
            iconSource = AppIconSource.fromOrNull(info),
            inWhiteSet = false,
            inMessageSet = false,
            inForceFreezeSet = false,
            inBlockThawSet = false,
            inForbidSyncSet = false,
            inNotFreezeFgsSet = false,
            inBatteryWhiteSet = false,
        )
    }
}

@Immutable
data class AppProfileUiState(
    val app: InstalledAppUiModel,
    val isDualApp: Boolean,
    val inWhite: Boolean = false,
    val inMessage: Boolean = false,
    val inForceFreeze: Boolean = false,
    val inBlockThaw: Boolean = false,
    val inForbidSync: Boolean = false,
    val inNotFreezeFgs: Boolean = false,
    val inIgnoreNetworkWake: Boolean = false,
    val appProcesses: List<RootHelper.ProcessRawEntry> = emptyList(),
    val isScanningProcesses: Boolean = false,
    val processSnapshotUnavailable: Boolean = false,
    val hasScannedProcesses: Boolean = false,
    val systemUnrestricted: Boolean = false,
    val systemPolicyLabel: String = "读取中…",
)

private data class LocalProfileState(
    val appProcesses: List<RootHelper.ProcessRawEntry> = emptyList(),
    val isScanningProcesses: Boolean = false,
    val processSnapshotUnavailable: Boolean = false,
    val hasScannedProcesses: Boolean = false,
    val systemUnrestricted: Boolean = false,
    val systemPolicyLabel: String = "读取中…",
)

/**
 * 单应用策略详情页 ViewModel（通过 [NavEntryStoreRegistry] 按 `(packageName, uid)` 隔离作用域）。
 *
 * - 策略开关直连 [PolicyRepository.prefsFlow]，互斥/依附联动与系统省电策略失败自动回滚全部可纯 JVM 单测；
 * - 进程快照保留 460ms 转场延迟，命中 [AppProcessSnapshotCache] 时立即展示避免布局跳变。
 */
class AppProfileViewModel(
    private val packageName: String,
    private val uid: Int,
    private val policyRepo: PolicyRepository,
    private val runtimeRepo: RuntimeAndAppsRepository,
    private val bridge: AppProfileSystemBridge,
    private val scanDelayMs: Long = PROCESS_SCAN_DELAY_MS,
) : ViewModel() {

    private val cacheKey = "$packageName:$uid"
    private val isDualApp = AppUidUtils.isDualAppUid(uid)
    private val fallbackApp: InstalledAppUiModel by lazy { bridge.buildFallbackAppModel(packageName, uid) }

    private val localState = MutableStateFlow(
        LocalProfileState(
            appProcesses = AppProcessSnapshotCache.get(cacheKey).orEmpty(),
            hasScannedProcesses = AppProcessSnapshotCache.get(cacheKey) != null,
        )
    )

    private val toastChannel = Channel<String>(Channel.BUFFERED)
    val toastEvents: Flow<String> = toastChannel.receiveAsFlow()

    val uiState: StateFlow<AppProfileUiState> = combine(
        runtimeRepo.installedAppsFlow,
        policyRepo.prefsFlow,
        localState,
    ) { apps, prefs, local ->
        val resolved = apps.find { it.packageName == packageName && it.uid == uid } ?: fallbackApp
        buildUiState(resolved, prefs, local)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = buildUiState(fallbackApp, policyRepo.prefsFlow.value, localState.value),
    )

    init {
        viewModelScope.launch {
            refreshSystemPolicy()
        }
        viewModelScope.launch {
            if (scanDelayMs > 0L) delay(scanDelayMs)
            scanProcesses()
        }
    }

    fun refreshProcesses() {
        viewModelScope.launch {
            scanProcesses()
        }
    }

    fun setWhiteList(checked: Boolean) {
        if (checked) {
            policyRepo.updateAppMemberships(
                packageName,
                uid,
                PrefsKeys.WHITE_APP_SET to true,
                PrefsKeys.MESSAGE_APP_SET to false,
                PrefsKeys.FORCE_FREEZE_SET to false,
                PrefsKeys.BLOCK_THAW_SET to false,
                PrefsKeys.FORBID_SYNC_BINDER_SET to false,
                PrefsKeys.IGNORE_NETWORK_WAKE_SET to false,
                PrefsKeys.NOT_FREEZE_WHEN_HAS_FGS_SET to false,
            )
            if (!isDualApp) {
                viewModelScope.launch {
                    val ok = bridge.setIgnoringBatteryOptimization(packageName)
                    refreshSystemPolicy()
                    runtimeRepo.refreshBatteryUnrestricted()
                    if (!ok) {
                        policyRepo.updateAppMemberships(packageName, uid, PrefsKeys.WHITE_APP_SET to false)
                        toastChannel.trySend("系统省电策略写入失败：需要模块在线或 Root 授权")
                    }
                }
            }
        } else {
            policyRepo.updateAppMemberships(packageName, uid, PrefsKeys.WHITE_APP_SET to false)
            if (!isDualApp) {
                viewModelScope.launch {
                    val ok = bridge.restoreBatteryOptimization(packageName)
                    refreshSystemPolicy()
                    runtimeRepo.refreshBatteryUnrestricted()
                    if (!ok) {
                        policyRepo.updateAppMemberships(packageName, uid, PrefsKeys.WHITE_APP_SET to true)
                        toastChannel.trySend("恢复系统省电策略失败：需要模块在线或 Root 授权")
                    }
                }
            }
        }
    }

    fun setMessageApp(checked: Boolean) {
        if (checked) {
            val prefs = policyRepo.prefsFlow.value
            val changes = mutableListOf(PrefsKeys.MESSAGE_APP_SET to true)
            if (AppUidUtils.containsPolicyTarget(prefs.whiteAppSet, packageName, uid)) {
                changes.add(PrefsKeys.WHITE_APP_SET to false)
            }
            if (AppUidUtils.containsPolicyTarget(prefs.ignoreNetworkWakeSet, packageName, uid)) {
                changes.add(PrefsKeys.IGNORE_NETWORK_WAKE_SET to false)
            }
            policyRepo.updateAppMemberships(packageName, uid, *changes.toTypedArray())
        } else {
            policyRepo.updateAppMemberships(packageName, uid, PrefsKeys.MESSAGE_APP_SET to false)
        }
    }

    fun setNotFreezeWhenHasFgs(checked: Boolean) {
        if (checked) {
            val prefs = policyRepo.prefsFlow.value
            val changes = mutableListOf(PrefsKeys.NOT_FREEZE_WHEN_HAS_FGS_SET to true)
            if (AppUidUtils.containsPolicyTarget(prefs.whiteAppSet, packageName, uid)) {
                changes.add(PrefsKeys.WHITE_APP_SET to false)
            }
            if (AppUidUtils.containsPolicyTarget(prefs.forceFreezeSet, packageName, uid)) {
                changes.add(PrefsKeys.FORCE_FREEZE_SET to false)
                changes.add(PrefsKeys.BLOCK_THAW_SET to false)
                changes.add(PrefsKeys.FORBID_SYNC_BINDER_SET to false)
                changes.add(PrefsKeys.IGNORE_NETWORK_WAKE_SET to false)
            }
            policyRepo.updateAppMemberships(packageName, uid, *changes.toTypedArray())
        } else {
            policyRepo.updateAppMemberships(packageName, uid, PrefsKeys.NOT_FREEZE_WHEN_HAS_FGS_SET to false)
        }
    }

    fun setForceFreeze(checked: Boolean) {
        if (checked) {
            val prefs = policyRepo.prefsFlow.value
            val changes = mutableListOf(PrefsKeys.FORCE_FREEZE_SET to true)
            if (AppUidUtils.containsPolicyTarget(prefs.whiteAppSet, packageName, uid)) {
                changes.add(PrefsKeys.WHITE_APP_SET to false)
            }
            if (AppUidUtils.containsPolicyTarget(prefs.notFreezeWhenHasFgsSet, packageName, uid)) {
                changes.add(PrefsKeys.NOT_FREEZE_WHEN_HAS_FGS_SET to false)
            }
            policyRepo.updateAppMemberships(packageName, uid, *changes.toTypedArray())
        } else {
            policyRepo.updateAppMemberships(
                packageName,
                uid,
                PrefsKeys.FORCE_FREEZE_SET to false,
                PrefsKeys.BLOCK_THAW_SET to false,
                PrefsKeys.FORBID_SYNC_BINDER_SET to false,
                PrefsKeys.IGNORE_NETWORK_WAKE_SET to false,
            )
        }
    }

    fun setBlockThaw(checked: Boolean) {
        policyRepo.updateAppMemberships(packageName, uid, PrefsKeys.BLOCK_THAW_SET to checked)
    }

    fun setForbidSyncBinder(checked: Boolean) {
        policyRepo.updateAppMemberships(packageName, uid, PrefsKeys.FORBID_SYNC_BINDER_SET to checked)
    }

    fun setIgnoreNetworkWake(checked: Boolean) {
        val prefs = policyRepo.prefsFlow.value
        if (checked && AppUidUtils.containsPolicyTarget(prefs.messageAppSet, packageName, uid)) {
            policyRepo.updateAppMemberships(
                packageName,
                uid,
                PrefsKeys.IGNORE_NETWORK_WAKE_SET to true,
                PrefsKeys.MESSAGE_APP_SET to false,
            )
        } else {
            policyRepo.updateAppMemberships(packageName, uid, PrefsKeys.IGNORE_NETWORK_WAKE_SET to checked)
        }
    }

    private suspend fun scanProcesses() {
        if (localState.value.isScanningProcesses) return
        localState.update { it.copy(isScanningProcesses = true) }
        try {
            val list = bridge.scanAppProcesses(uid)
            if (list == null) {
                localState.update {
                    it.copy(
                        processSnapshotUnavailable = true,
                        hasScannedProcesses = true,
                    )
                }
                return
            }
            AppProcessSnapshotCache.put(cacheKey, list)
            localState.update {
                it.copy(
                    appProcesses = list,
                    processSnapshotUnavailable = false,
                    hasScannedProcesses = true,
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            localState.update {
                it.copy(
                    processSnapshotUnavailable = true,
                    hasScannedProcesses = true,
                )
            }
        } finally {
            localState.update { it.copy(isScanningProcesses = false) }
        }
    }

    private suspend fun refreshSystemPolicy() {
        runCatching { bridge.readSystemBatteryPolicy(packageName) }
            .onSuccess { (unrestricted, label) ->
                localState.update {
                    it.copy(
                        systemUnrestricted = unrestricted,
                        systemPolicyLabel = label,
                    )
                }
            }
    }

    private fun buildUiState(
        app: InstalledAppUiModel,
        prefs: PrefsSnapshot,
        local: LocalProfileState,
    ): AppProfileUiState = AppProfileUiState(
        app = app,
        isDualApp = isDualApp,
        inWhite = AppUidUtils.containsPolicyTarget(prefs.whiteAppSet, packageName, uid),
        inMessage = AppUidUtils.containsPolicyTarget(prefs.messageAppSet, packageName, uid),
        inForceFreeze = AppUidUtils.containsPolicyTarget(prefs.forceFreezeSet, packageName, uid),
        inBlockThaw = AppUidUtils.containsPolicyTarget(prefs.blockThawSet, packageName, uid),
        inForbidSync = AppUidUtils.containsPolicyTarget(prefs.forbidSyncBinderSet, packageName, uid),
        inNotFreezeFgs = AppUidUtils.containsPolicyTarget(prefs.notFreezeWhenHasFgsSet, packageName, uid),
        inIgnoreNetworkWake = AppUidUtils.containsPolicyTarget(prefs.ignoreNetworkWakeSet, packageName, uid),
        appProcesses = local.appProcesses,
        isScanningProcesses = local.isScanningProcesses,
        processSnapshotUnavailable = local.processSnapshotUnavailable,
        hasScannedProcesses = local.hasScannedProcesses,
        systemUnrestricted = local.systemUnrestricted,
        systemPolicyLabel = local.systemPolicyLabel,
    )

    companion object {
        /**
         * 进程扫描启动延迟，确保扫描落点稳定在转场动画结束之后。
         */
        const val PROCESS_SCAN_DELAY_MS = 460L
    }
}

private object AppProcessSnapshotCache {
    private const val MAX_ENTRIES = 8

    private val entries =
        object : LinkedHashMap<String, List<RootHelper.ProcessRawEntry>>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, List<RootHelper.ProcessRawEntry>>?,
            ): Boolean = size > MAX_ENTRIES
        }

    @Synchronized
    fun get(cacheKey: String): List<RootHelper.ProcessRawEntry>? = entries[cacheKey]

    @Synchronized
    fun put(cacheKey: String, processes: List<RootHelper.ProcessRawEntry>) {
        entries[cacheKey] = processes
    }
}
