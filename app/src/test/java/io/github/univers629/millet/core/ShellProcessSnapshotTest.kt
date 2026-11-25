package io.github.univers629.millet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellProcessSnapshotTest {

    private val sample = listOf(
        "__XM_PS__",
        "  UID   PID    RSS     VSZ S WCHAN            ELAPSED NAME",
        " 1000  1500  90000 1000000 S 0                  57:12 system_server",
        "10313 20680 594892 147032076 S do_freezer_trap 56:24 com.tencent.mm",
        "10313 22673 294164 18694540 S 0           1-02:03:04 com.tencent.mm:push",
        "10131 25644  10000  200000 S 0                  01:05 com.example.a",
        "10200 30000   5000  100000 T 0                  00:10 com.example.stopped",
        "__XM_CG__",
        "/sys/fs/cgroup/apps/uid_10131/pid_25644/cgroup.events",
        "__XM_GZ__",
        "Frozen processes:[20680, 25644]",
    )

    @Test
    fun `parse keeps app processes and classifies freeze evidence`() {
        val snapshot = ShellProcessSnapshot.parse(sample, 42L) { uid ->
            when (uid) {
                10313 -> listOf("com.tencent.mm")
                10131 -> listOf("com.example.a")
                10200 -> listOf("com.example.stopped")
                else -> null
            }
        }!!
        assertEquals(42L, snapshot.capturedAt)
        val byPid = snapshot.entries.associateBy { it.pid }
        assertEquals(setOf(20680, 22673, 25644, 30000), byPid.keys)

        assertEquals(RootHelper.FreezerType.V2, byPid.getValue(20680).freezerType)
        assertEquals(RootHelper.FreezeState.FROZEN, byPid.getValue(20680).freezeState)
        assertEquals("do_freezer_trap", byPid.getValue(20680).wchan)

        val push = byPid.getValue(22673)
        assertEquals(RootHelper.FreezeState.RUNNING, push.freezeState)
        assertEquals("com.tencent.mm", push.packageName)
        assertEquals("", push.wchan)
        assertEquals("1-02:03:04", push.elapsedStr)

        assertEquals(RootHelper.FreezerType.V2, byPid.getValue(25644).freezerType)
        assertEquals(RootHelper.FreezerType.KILL, byPid.getValue(30000).freezerType)
    }

    @Test
    fun `parse without freeze probe output marks running state unknown`() {
        val lines = sample.takeWhile { it != "__XM_CG__" }
        val snapshot = ShellProcessSnapshot.parse(lines, 0L) { null }!!
        assertEquals(RootHelper.FreezeState.UNKNOWN, snapshot.entries.first { it.pid == 22673 }.freezeState)
    }

    @Test
    fun `parse returns null when ps produced nothing`() {
        assertNull(ShellProcessSnapshot.parse(listOf("__XM_PS__", "__XM_CG__"), 0L) { null })
    }

    @Test
    fun `parseEtime handles toybox formats`() {
        assertEquals(65L, ShellProcessSnapshot.parseEtime("01:05"))
        assertEquals(3_384L, ShellProcessSnapshot.parseEtime("56:24"))
        assertEquals(93_784L, ShellProcessSnapshot.parseEtime("1-02:03:04"))
        assertEquals(0L, ShellProcessSnapshot.parseEtime("bad"))
    }
}
