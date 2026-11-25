package io.github.univers629.millet.core

import android.content.Context
import android.os.SystemClock
import io.github.univers629.millet.system.ProcessSnapshotProvider

/**
 * 降级模式的进程快照：模块未生效（拿不到 system_server 快照）时，
 * 用 Root / Shizuku 执行一次 `ps` + cgroup 冻结状态 + `dumpsys greezer`，拼出同样的进程条目。
 *
 * 与模块快照的差异：没有运行中服务列表，内存是 RSS 而不是 PSS；冻结判据为
 * cgroup v2 `cgroup.events frozen 1`、Greeze 的 `Frozen processes` 列表、
 * wchan（`do_freezer_trap` / `__refrigerator`）与信号停止状态 `T`。
 * adb 身份（uid 2000）实测可以读取上述全部数据。
 */
object ShellProcessSnapshot {

    private const val MARK_PS = "__XM_PS__"
    private const val MARK_CG = "__XM_CG__"
    private const val MARK_GZ = "__XM_GZ__"

    private val WHITESPACE = Regex("\\s+")
    private val CGROUP_PID = Regex("/pid_(\\d+)/cgroup\\.events")
    private val GREEZER_LIST = Regex("^Frozen processes:\\s*\\[(.*)]")

    internal val SCRIPT = """
        echo $MARK_PS
        ps -A -w -o UID,PID,RSS,VSZ,S,WCHAN,ETIME,NAME 2>/dev/null
        echo $MARK_CG
        grep -l '^frozen 1' /sys/fs/cgroup/apps/uid_*/pid_*/cgroup.events /sys/fs/cgroup/uid_*/pid_*/cgroup.events 2>/dev/null
        echo $MARK_GZ
        dumpsys greezer 2>/dev/null | grep '^Frozen processes'
    """.trimIndent()

    /** 没有可用特权通道时直接返回 null，不会触发任何授权请求。 */
    fun capture(context: Context): ProcessSnapshotCodec.Snapshot? {
        if (!PrivilegedExecutor.isAvailable()) return null
        val lines = PrivilegedExecutor.exec(SCRIPT, timeoutMs = 10_000L)
        if (lines.isEmpty()) return null
        val pm = context.packageManager
        val cache = HashMap<Int, List<String>?>()
        return parse(lines, SystemClock.uptimeMillis()) { uid ->
            if (cache.containsKey(uid)) cache[uid]
            else runCatching { pm.getPackagesForUid(uid)?.toList() }.getOrNull().also { cache[uid] = it }
        }
    }

    internal fun parse(
        lines: List<String>,
        capturedAt: Long,
        packagesForUid: (Int) -> List<String>?,
    ): ProcessSnapshotCodec.Snapshot? {
        var section = ""
        val psRows = ArrayList<List<String>>()
        val cgroupFrozen = HashSet<Int>()
        val greezerFrozen = HashSet<Int>()
        var greezerSeen = false

        for (raw in lines) {
            val line = raw.trim()
            if (line == MARK_PS || line == MARK_CG || line == MARK_GZ) {
                section = line
                continue
            }
            if (line.isEmpty()) continue
            when (section) {
                MARK_PS -> {
                    val tokens = line.split(WHITESPACE, limit = 8)
                    if (tokens.size == 8 && tokens[0].toIntOrNull() != null) psRows.add(tokens)
                }
                MARK_CG -> CGROUP_PID.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { cgroupFrozen.add(it) }
                MARK_GZ -> GREEZER_LIST.find(line)?.let { match ->
                    greezerSeen = true
                    match.groupValues[1].split(',').mapNotNullTo(greezerFrozen) { it.trim().toIntOrNull() }
                }
            }
        }
        if (psRows.isEmpty()) return null

        // 冻结探测通道是否真的读到了东西；读不到时未冻结进程记为 UNKNOWN 而不是 RUNNING。
        val probeOk = greezerSeen || cgroupFrozen.isNotEmpty()
        val entries = ArrayList<RootHelper.ProcessRawEntry>()
        for (t in psRows) {
            val uid = t[0].toInt()
            if (!AppUidUtils.isManagedAppUid(uid, includeDualApp = true)) continue
            val pid = t[1].toIntOrNull()?.takeIf { it > 0 } ?: continue
            val rssKb = t[2].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val vszKb = t[3].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val state = t[4]
            val wchan = t[5].takeUnless { it == "0" || it == "-" }.orEmpty()
            val elapsedSec = parseEtime(t[6])
            val name = t[7].trim()
            if (name.isEmpty()) continue

            val type = when {
                state == "T" -> RootHelper.FreezerType.KILL
                pid in cgroupFrozen || pid in greezerFrozen || wchan.contains("do_freezer_trap") ->
                    RootHelper.FreezerType.V2
                wchan.contains("refrigerator") -> RootHelper.FreezerType.V1
                else -> RootHelper.FreezerType.UNKNOWN
            }
            val freezeState = when {
                type != RootHelper.FreezerType.UNKNOWN -> RootHelper.FreezeState.FROZEN
                probeOk -> RootHelper.FreezeState.RUNNING
                else -> RootHelper.FreezeState.UNKNOWN
            }
            val packageName = ProcessSnapshotProvider.resolvePackageName(name, packagesForUid(uid), null).orEmpty()
            entries.add(
                RootHelper.ProcessRawEntry(
                    uid = uid,
                    pid = pid,
                    rssKb = rssKb,
                    vszKb = vszKb,
                    wchan = wchan,
                    elapsedStr = formatElapsed(elapsedSec),
                    processName = name,
                    packageName = packageName,
                    status = state,
                    freezerType = type,
                    freezeState = freezeState,
                )
            )
        }
        return ProcessSnapshotCodec.Snapshot(capturedAt, entries)
    }

    /** toybox ETIME：`[[dd-]hh:]mm:ss`。 */
    internal fun parseEtime(text: String): Long {
        val days = if (text.contains('-')) text.substringBefore('-').toLongOrNull() ?: return 0L else 0L
        val clock = text.substringAfter('-')
        var seconds = 0L
        for (part in clock.split(':')) {
            seconds = seconds * 60 + (part.toLongOrNull() ?: return 0L)
        }
        return days * 86_400 + seconds
    }

    private fun formatElapsed(seconds: Long): String {
        if (seconds <= 0L) return ""
        val days = seconds / 86_400
        val hours = (seconds % 86_400) / 3_600
        val minutes = (seconds % 3_600) / 60
        val secs = seconds % 60
        val clock = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        return if (days > 0) "$days-$clock" else clock
    }
}
