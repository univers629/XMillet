package io.github.univers629.millet.ui.data

import io.github.univers629.millet.core.ShellProcessSnapshot
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import io.github.univers629.millet.core.AppUidUtils
import io.github.univers629.millet.core.CloudConfigReader
import io.github.univers629.millet.core.CloudListCodec
import io.github.univers629.millet.core.Logger
import io.github.univers629.millet.core.PrefsSnapshot
import io.github.univers629.millet.core.RootHelper
import io.github.univers629.millet.core.SETTING_MILLET_NO_RESTRICT_APP
import io.github.univers629.millet.ipc.PowerKeeperBinderClient
import io.github.univers629.millet.ui.model.AppIconSource
import io.github.univers629.millet.ui.screen.InstalledAppUiModel
import io.github.univers629.millet.ui.screen.RunningProcessUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 慢流包元数据（仅随应用安装/卸载/下拉刷新变化，与策略集合解耦）。
 */
data class RawPackageMeta(
    val appName: String,
    val packageName: String,
    val uid: Int,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val iconSource: AppIconSource? = null,
)

/** 已安装应用与双开包扫描数据源（支持纯 JVM 测试替身）。 */
interface InstalledPackageSource {
    suspend fun loadPrimaryPackages(): List<RawPackageMeta>
    suspend fun loadDualPackages(primaryByPackage: Map<String, RawPackageMeta>): Pair<List<RawPackageMeta>, Boolean>
}

/** 运行态、进程快照、云名单与系统省电策略数据源（支持纯 JVM 测试替身）。 */
interface RuntimeEnvironmentSource {
    suspend fun queryRuntimeStatus(): PowerKeeperBinderClient.RuntimeStatusSnapshot
    suspend fun queryRunningProcesses(): List<RunningProcessUiModel>?
    suspend fun loadFallbackCloudLists(): CloudListCodec.CloudLists?
    suspend fun readBatteryUnrestrictedPackages(): Set<String>
    suspend fun thawAllFrozenUids(): Int
}

internal class AndroidInstalledPackageSource(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : InstalledPackageSource {

    private val appContext: Context = context.applicationContext ?: context

    override suspend fun loadPrimaryPackages(): List<RawPackageMeta> = withContext(ioDispatcher) {
        val pm = appContext.packageManager
        @Suppress("DEPRECATION")
        val installedList = pm.getInstalledApplications(0)
        val result = ArrayList<RawPackageMeta>(installedList.size)
        val seenKeys = HashSet<String>(installedList.size)
        for (info in installedList) {
            val pkg = info.packageName ?: continue
            if (!seenKeys.add("$pkg:${info.uid}")) continue
            val isSys = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            result.add(buildMeta(pm, pkg, info.uid, info, isSys))
        }
        result
    }

    override suspend fun loadDualPackages(
        primaryByPackage: Map<String, RawPackageMeta>,
    ): Pair<List<RawPackageMeta>, Boolean> = withContext(ioDispatcher) {
        val pm = appContext.packageManager
        val dualEntries = PowerKeeperBinderClient.queryDualApps() ?: return@withContext emptyList<RawPackageMeta>() to false
        val result = ArrayList<RawPackageMeta>(dualEntries.size)
        val seenKeys = HashSet<String>(dualEntries.size)
        for (entry in dualEntries) {
            if (!seenKeys.add("${entry.packageName}:${entry.uid}")) continue
            val primary = primaryByPackage[entry.packageName]
            val info = primary?.iconSource?.applicationInfo ?: runCatching {
                pm.getApplicationInfo(entry.packageName, 0)
            }.getOrNull()
            val isSys = (entry.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (primary?.isSystem == true) ||
                (((info?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0)
            result.add(buildMeta(pm, entry.packageName, entry.uid, info, isSys, primary))
        }
        result to true
    }

    private fun buildMeta(
        pm: PackageManager,
        pkg: String,
        uid: Int,
        info: ApplicationInfo?,
        isSystem: Boolean,
        primaryMeta: RawPackageMeta? = null,
    ): RawPackageMeta {
        val label = primaryMeta?.appName ?: info?.let {
            runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()
        } ?: pkg
        val versionName: String
        val versionCode: Long
        if (primaryMeta != null) {
            versionName = primaryMeta.versionName
            versionCode = primaryMeta.versionCode
        } else {
            val pInfo = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
            versionName = pInfo?.versionName.orEmpty()
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo?.longVersionCode ?: 0L
            } else {
                @Suppress("DEPRECATION")
                (pInfo?.versionCode ?: 0).toLong()
            }
        }
        return RawPackageMeta(
            appName = label,
            packageName = pkg,
            uid = uid,
            versionName = versionName,
            versionCode = versionCode,
            isSystem = isSystem,
            iconSource = AppIconSource.fromOrNull(info),
        )
    }
}

internal class AndroidRuntimeEnvironmentSource(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RuntimeEnvironmentSource {

    private val appContext: Context = context.applicationContext ?: context

    override suspend fun queryRuntimeStatus(): PowerKeeperBinderClient.RuntimeStatusSnapshot =
        withContext(ioDispatcher) {
            if (!PowerKeeperBinderClient.pingAlive()) {
                PowerKeeperBinderClient.bindFromApp(appContext)
            }
            PowerKeeperBinderClient.queryRuntimeStatus()
        }

    override suspend fun queryRunningProcesses(): List<RunningProcessUiModel>? =
        withContext(ioDispatcher) {
            val pm = appContext.packageManager
            // 模块快照优先；模块未生效时退回 Root / Shizuku 的 ps + cgroup 降级快照
            val snapshot = PowerKeeperBinderClient.queryProcessSnapshot()
                ?: ShellProcessSnapshot.capture(appContext)
                ?: return@withContext null
            val byPkgUid = LinkedHashMap<String, MutableList<RootHelper.ProcessRawEntry>>()
            for (raw in snapshot.entries) {
                if (raw.packageName.isBlank()) continue
                byPkgUid.getOrPut("${raw.packageName}:${raw.uid}") { ArrayList() }.add(raw)
            }
            if (byPkgUid.isEmpty()) return@withContext emptyList()
            byPkgUid.values.mapNotNull { rawEntries ->
                val first = rawEntries.firstOrNull() ?: return@mapNotNull null
                val pkg = first.packageName
                val uid = first.uid
                if (pkg == appContext.packageName) return@mapNotNull null
                if (!AppUidUtils.isManagedAppUid(uid, includeDualApp = true)) {
                    return@mapNotNull null
                }
                val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                val label = appInfo?.let {
                    runCatching { pm.getApplicationLabel(it).toString().trim() }.getOrNull()
                }.orEmpty().ifEmpty { pkg }
                val pids = rawEntries.map { it.pid }
                val totalMemKb = rawEntries.sumOf { it.rssKb }
                val fTypes = rawEntries.map { it.freezerType }
                    .filter { it != RootHelper.FreezerType.UNKNOWN }.toSet()
                val kernelFrozenCount = rawEntries.count { it.freezeState == RootHelper.FreezeState.FROZEN }
                val unknownCount = rawEntries.count { it.freezeState == RootHelper.FreezeState.UNKNOWN }
                val isSys = ((appInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0

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
                    isSystem = isSys,
                    isDualApp = AppUidUtils.isDualAppUid(uid),
                    iconSource = AppIconSource.fromOrNull(appInfo),
                )
            }.sortedWith(
                compareByDescending<RunningProcessUiModel> { it.isFrozen }
                    .thenByDescending { it.memSizeKb }
                    .thenBy { it.appName.lowercase() }
            )
        }

    override suspend fun loadFallbackCloudLists(): CloudListCodec.CloudLists? =
        withContext(ioDispatcher) {
            val cloudConfig = CloudConfigReader.load(appContext, force = true)
            if (cloudConfig.isEmpty) {
                null
            } else {
                CloudListCodec.CloudLists(
                    dozeWhiteList = cloudConfig.dozeWhiteList,
                    frozenNewWhiteList = cloudConfig.frozenNewWhiteList,
                    launchRestrict = cloudConfig.launchRestrict,
                )
            }
        }

    override suspend fun readBatteryUnrestrictedPackages(): Set<String> =
        withContext(ioDispatcher) {
            runCatching {
                Settings.System.getString(
                    appContext.contentResolver,
                    SETTING_MILLET_NO_RESTRICT_APP,
                )
            }.getOrNull()
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toHashSet()
                ?: emptySet()
        }

    override suspend fun thawAllFrozenUids(): Int = withContext(ioDispatcher) {
        PowerKeeperBinderClient.thawAllFrozenUids()
    }
}

/**
 * 运行态、进程快照与已安装应用列表的响应式仓库。
 *
 * 将慢速变动的 `PackageManager` 元数据扫描与高频变动的策略集合（[PolicyRepository.prefsFlow]）、
 * 云端名单和系统省电无限制名单解耦：
 * - 并发冷启动/恢复请求通过 [packageScanMutex] 单飞合并，避免多倍重复 IPC；
 * - 修改任意应用策略开关时仅在内存合成 [installedAppsFlow]，0 次 `PackageManager` IPC；
 * - Binder 晚于首屏连上时仅增量补充双开列表，不重扫全机主用户包。
 */
class RuntimeAndAppsRepository(
    policyRepository: PolicyRepository,
    private val packageSource: InstalledPackageSource,
    private val environmentSource: RuntimeEnvironmentSource,
    private val scope: CoroutineScope,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val packageScanMutex = Mutex()
    private val runtimeRefreshMutex = Mutex()

    private var hasLoadedPrimaryPackages = false
    private var dualAppsSyncedFromBinder = false
    private var fallbackCloudAttempted = false

    private val _rawPackagesFlow = MutableStateFlow<List<RawPackageMeta>>(emptyList())
    private val _batteryUnrestrictedFlow = MutableStateFlow<Set<String>>(emptySet())
    private val _resolvedCloudListsFlow = MutableStateFlow<CloudListCodec.CloudLists?>(null)

    private val _runtimeStatusFlow = MutableStateFlow(PowerKeeperBinderClient.RuntimeStatusSnapshot())
    val runtimeStatusFlow: StateFlow<PowerKeeperBinderClient.RuntimeStatusSnapshot> =
        _runtimeStatusFlow.asStateFlow()

    private val _runningProcessesFlow = MutableStateFlow<List<RunningProcessUiModel>>(emptyList())
    val runningProcessesFlow: StateFlow<List<RunningProcessUiModel>> =
        _runningProcessesFlow.asStateFlow()

    private val _processSnapshotUnavailableFlow = MutableStateFlow(false)
    val processSnapshotUnavailableFlow: StateFlow<Boolean> =
        _processSnapshotUnavailableFlow.asStateFlow()

    private val _isAppsRefreshingFlow = MutableStateFlow(false)
    val isAppsRefreshingFlow: StateFlow<Boolean> = _isAppsRefreshingFlow.asStateFlow()

    private val _isProcessesRefreshingFlow = MutableStateFlow(false)
    val isProcessesRefreshingFlow: StateFlow<Boolean> = _isProcessesRefreshingFlow.asStateFlow()

    val effectiveCloudListsFlow: StateFlow<CloudListCodec.CloudLists?> = combine(
        _runtimeStatusFlow,
        _resolvedCloudListsFlow,
    ) { status, fallback ->
        status.cloudLists?.takeUnless { it.isEmpty } ?: fallback
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val installedAppsFlow: StateFlow<List<InstalledAppUiModel>> = combine(
        _rawPackagesFlow,
        policyRepository.prefsFlow,
        effectiveCloudListsFlow,
        _batteryUnrestrictedFlow,
        _runningProcessesFlow,
    ) { rawPackages, prefs, cloudLists, batteryUnrestricted, procs ->
        assembleInstalledApps(
            rawPackages = rawPackages,
            prefs = prefs,
            cloudLists = cloudLists,
            batteryUnrestricted = batteryUnrestricted,
            runningProcesses = procs,
        )
    }
        .flowOn(computeDispatcher)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun refreshInstalledPackages(force: Boolean = false, visible: Boolean = false) {
        if (visible) _isAppsRefreshingFlow.value = true
        try {
            // 始终顺便刷新系统包名级「无限制」名单（单次 Settings.System 字符串读取）
            refreshBatteryUnrestricted()
            packageScanMutex.withLock {
                if (hasLoadedPrimaryPackages && !force) {
                    syncDualAppsLocked()
                    return
                }
                val primary = packageSource.loadPrimaryPackages()
                val primaryByPkg = primary.associateBy { it.packageName }
                val (dual, dualLoaded) = packageSource.loadDualPackages(primaryByPkg)
                val merged = mergeRawPackages(primary, dual)
                _rawPackagesFlow.value = merged
                hasLoadedPrimaryPackages = true
                if (dualLoaded) {
                    dualAppsSyncedFromBinder = true
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Logger.error("加载应用策略失败", t)
        } finally {
            if (visible) _isAppsRefreshingFlow.value = false
        }
    }

    suspend fun refreshBatteryUnrestricted() {
        runCatching { environmentSource.readBatteryUnrestrictedPackages() }
            .onSuccess { _batteryUnrestrictedFlow.value = it }
    }

    suspend fun refreshRuntimeAndProcesses(visible: Boolean = false) {
        if (runtimeRefreshMutex.isLocked && !visible) return
        if (visible) _isProcessesRefreshingFlow.value = true
        try {
            runtimeRefreshMutex.withLock {
                val status = environmentSource.queryRuntimeStatus()
                _runtimeStatusFlow.value = status

                val wireCloud = status.cloudLists?.takeUnless { it.isEmpty }
                if (wireCloud != null) {
                    _resolvedCloudListsFlow.value = wireCloud
                } else if (!status.binderAlive && !fallbackCloudAttempted) {
                    fallbackCloudAttempted = true
                    runCatching { environmentSource.loadFallbackCloudLists() }
                        .getOrNull()
                        ?.let { _resolvedCloudListsFlow.value = it }
                }

                if (status.binderAlive && hasLoadedPrimaryPackages && !dualAppsSyncedFromBinder) {
                    scope.launch {
                        packageScanMutex.withLock { syncDualAppsLocked() }
                    }
                }

                val procs = environmentSource.queryRunningProcesses()
                if (procs != null) {
                    _runningProcessesFlow.value = procs
                }
                _processSnapshotUnavailableFlow.value = procs == null
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            _processSnapshotUnavailableFlow.value = true
            Logger.error("刷新进程快照失败", t)
        } finally {
            if (visible) _isProcessesRefreshingFlow.value = false
        }
    }

    suspend fun thawAllFrozenUids(): Int {
        val count = environmentSource.thawAllFrozenUids()
        refreshRuntimeAndProcesses(visible = false)
        return count
    }

    /**
     * 仅在前台生命周期（`Lifecycle.State.STARTED`）内调用：退后台或息屏立即由调用方协程取消。
     */
    suspend fun runForegroundPollingLoop(intervalMsProvider: () -> Long) {
        while (currentCoroutineContext().isActive) {
            delay(intervalMsProvider())
            refreshRuntimeAndProcesses(visible = false)
        }
    }

    private suspend fun syncDualAppsLocked() {
        if (dualAppsSyncedFromBinder || !hasLoadedPrimaryPackages || !_runtimeStatusFlow.value.binderAlive) return
        val current = _rawPackagesFlow.value
        val primaryOnly = current.filterNot { AppUidUtils.isDualAppUid(it.uid) }
        val primaryByPkg = primaryOnly.associateBy { it.packageName }
        val (dual, dualLoaded) = packageSource.loadDualPackages(primaryByPkg)
        if (dualLoaded) {
            _rawPackagesFlow.value = mergeRawPackages(primaryOnly, dual)
            dualAppsSyncedFromBinder = true
        }
    }

    private fun mergeRawPackages(
        primary: List<RawPackageMeta>,
        dual: List<RawPackageMeta>,
    ): List<RawPackageMeta> {
        if (dual.isEmpty()) return primary
        val seen = HashSet<String>(primary.size + dual.size)
        val merged = ArrayList<RawPackageMeta>(primary.size + dual.size)
        for (item in primary) {
            if (seen.add("${item.packageName}:${item.uid}")) merged.add(item)
        }
        for (item in dual) {
            if (seen.add("${item.packageName}:${item.uid}")) merged.add(item)
        }
        return merged
    }

    private fun assembleInstalledApps(
        rawPackages: List<RawPackageMeta>,
        prefs: PrefsSnapshot,
        cloudLists: CloudListCodec.CloudLists?,
        batteryUnrestricted: Set<String>,
        runningProcesses: List<RunningProcessUiModel>,
    ): List<InstalledAppUiModel> {
        if (rawPackages.isEmpty() && runningProcesses.isEmpty()) return emptyList()
        val seenKeys = HashSet<String>(rawPackages.size + 8)
        val primaryByPkg = HashMap<String, RawPackageMeta>(rawPackages.size)
        val result = ArrayList<InstalledAppUiModel>(rawPackages.size + 8)

        fun toUiModel(meta: RawPackageMeta): InstalledAppUiModel {
            val pkg = meta.packageName
            val uid = meta.uid
            return InstalledAppUiModel(
                appName = meta.appName,
                packageName = pkg,
                uid = uid,
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                isSystem = meta.isSystem,
                iconSource = meta.iconSource,
                inWhiteSet = AppUidUtils.containsPolicyTarget(prefs.whiteAppSet, pkg, uid),
                inMessageSet = AppUidUtils.containsPolicyTarget(prefs.messageAppSet, pkg, uid),
                inForceFreezeSet = AppUidUtils.containsPolicyTarget(prefs.forceFreezeSet, pkg, uid),
                inBlockThawSet = AppUidUtils.containsPolicyTarget(prefs.blockThawSet, pkg, uid),
                inForbidSyncSet = AppUidUtils.containsPolicyTarget(prefs.forbidSyncBinderSet, pkg, uid),
                inNotFreezeFgsSet = AppUidUtils.containsPolicyTarget(prefs.notFreezeWhenHasFgsSet, pkg, uid),
                inBatteryWhiteSet = !AppUidUtils.isDualAppUid(uid) && pkg in batteryUnrestricted,
                inCloudDozeWhite = cloudLists != null && pkg in cloudLists.dozeWhiteList,
                inCloudFrozenWhite = cloudLists != null && pkg in cloudLists.frozenNewWhiteList,
                inCloudLaunchAllow = cloudLists != null && pkg in cloudLists.launchRestrict,
            )
        }

        for (meta in rawPackages) {
            if (!AppUidUtils.isDualAppUid(meta.uid)) {
                primaryByPkg[meta.packageName] = meta
            }
            if (!seenKeys.add("${meta.packageName}:${meta.uid}")) continue
            result.add(toUiModel(meta))
        }

        for (proc in runningProcesses) {
            if (!proc.isDualApp) continue
            if (!seenKeys.add("${proc.packageName}:${proc.uid}")) continue
            val primary = primaryByPkg[proc.packageName]
            val fallbackMeta = RawPackageMeta(
                appName = primary?.appName ?: proc.appName,
                packageName = proc.packageName,
                uid = proc.uid,
                versionName = primary?.versionName.orEmpty(),
                versionCode = primary?.versionCode ?: 0L,
                isSystem = primary?.isSystem ?: proc.isSystem,
                iconSource = primary?.iconSource ?: proc.iconSource,
            )
            result.add(toUiModel(fallbackMeta))
        }

        result.sortWith(
            compareByDescending<InstalledAppUiModel> { it.hasAnyConfig }
                .thenBy { it.appName.lowercase() }
                .thenBy { it.packageName }
                .thenBy { it.isDualApp }
        )
        return result
    }
}
