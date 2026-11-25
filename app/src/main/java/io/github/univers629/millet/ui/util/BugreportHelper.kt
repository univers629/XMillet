package io.github.univers629.millet.ui.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.system.Os
import io.github.univers629.millet.BuildConfig
import io.github.univers629.millet.core.Logger
import io.github.univers629.millet.core.PrivilegeBackend
import io.github.univers629.millet.core.PrivilegedExecutor
import io.github.univers629.millet.core.RootHelper
import io.github.univers629.millet.ipc.PowerKeeperBinderClient
import io.github.univers629.millet.ui.BackupRestoreHelper
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPOutputStream

/**
 * 诊断报告与配置备份文件生成工具。
 *
 * 将设备与系统属性、XMillet 运行状态快照、当前配置、模块本地日志 (`/data/system/XMillet/log`)、
 * `logcat`、`dmesg`、进程列表及 `dumpsys` 快照统一打包为 `bugreport-yyyyMMdd-HHmmss.tar.gz`，
 * 置于应用 `cacheDir` 下，供 `FileProvider` 分享或 SAF 导出。
 */
object BugreportHelper {

    private const val TIMESTAMP_PATTERN = "yyyyMMdd-HHmmss"
    private const val BUGREPORT_PREFIX = "bugreport-"
    private const val BUGREPORT_SUFFIX = ".tar.gz"
    private const val BACKUP_PREFIX = "XMillet_Backup_"
    private const val BACKUP_SUFFIX = ".json"
    private const val TMP_DIR_NAME = "bugreport_tmp"
    private const val TAR_BLOCK_SIZE = 512

    fun formatTimestamp(now: LocalDateTime = LocalDateTime.now()): String {
        return now.format(DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN, Locale.ROOT))
    }

    fun buildBugreportFileName(timestamp: String = formatTimestamp()): String {
        return "$BUGREPORT_PREFIX$timestamp$BUGREPORT_SUFFIX"
    }

    fun buildBackupFileName(timestamp: String = formatTimestamp()): String {
        return "$BACKUP_PREFIX$timestamp$BACKUP_SUFFIX"
    }

    fun createBackupCacheFile(context: Context): File {
        cleanupCacheFiles(context.cacheDir, BACKUP_PREFIX, BACKUP_SUFFIX)
        val outFile = File(context.cacheDir, buildBackupFileName())
        outFile.writeText(BackupRestoreHelper.exportToJson(context), Charsets.UTF_8)
        return outFile
    }

    fun getBugreportFile(context: Context): File {
        val cacheDir = context.cacheDir
        cleanupCacheFiles(cacheDir, BUGREPORT_PREFIX, BUGREPORT_SUFFIX)

        val bugreportDir = File(cacheDir, TMP_DIR_NAME)
        if (bugreportDir.exists()) {
            bugreportDir.deleteRecursively()
        }
        bugreportDir.mkdirs()

        try {
            val hasRoot = PrivilegedExecutor.ensureReady(timeoutMs = 15_000L) != PrivilegeBackend.NONE

            File(bugreportDir, "basic.txt").writeText(
                buildBasicInfoText(hasRoot),
                Charsets.UTF_8
            )
            File(bugreportDir, "config.json").writeText(
                BackupRestoreHelper.exportToJson(context),
                Charsets.UTF_8
            )

            if (hasRoot) {
                collectRootDiagnostics(bugreportDir)
            } else {
                collectFallbackDiagnostics(bugreportDir)
            }

            val targetFile = File(cacheDir, buildBugreportFileName())
            val files = bugreportDir.listFiles()
                ?.filter { it.isFile && it.canRead() && it.length() > 0L }
                ?.sortedBy { it.name }
                .orEmpty()
            writeTarGz(files, targetFile)
            return targetFile
        } finally {
            bugreportDir.deleteRecursively()
        }
    }

    private fun cleanupCacheFiles(cacheDir: File, prefix: String, suffix: String) {
        cacheDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(suffix) }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun buildBasicInfoText(hasRoot: Boolean): String = buildString {
        val status = runCatching { PowerKeeperBinderClient.queryRuntimeStatus() }.getOrNull()
        val kernelFreezer = runCatching { PowerKeeperBinderClient.queryKernelFreezer() }.getOrNull()
        val uname = runCatching { Os.uname() }.getOrNull()

        appendLine("=== XMillet Diagnostic Report ===")
        appendLine("Timestamp: ${formatTimestamp()}")
        appendLine("AppVersion: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
        appendLine("RootGranted: ${RootHelper.isRootGranted}")
        appendLine("PrivilegeBackend: ${PrivilegedExecutor.state.value.backendLabel} (mode=${PrivilegedExecutor.state.value.mode})")
        appendLine("Shizuku: ${PrivilegedExecutor.state.value.shizukuSummary}")
        appendLine()

        appendLine("=== Device & ROM ===")
        appendLine("Brand: ${Build.BRAND}")
        appendLine("Manufacturer: ${Build.MANUFACTURER}")
        appendLine("Model: ${Build.MODEL}")
        appendLine("Product: ${Build.PRODUCT}")
        appendLine("Device: ${Build.DEVICE}")
        appendLine("SDK: ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
        appendLine("Fingerprint: ${Build.FINGERPRINT}")
        appendLine("HyperOSName: ${getSystemProp("ro.mi.os.version.name")}")
        appendLine("HyperOSCode: ${getSystemProp("ro.mi.os.version.code")}")
        appendLine("MIUIName: ${getSystemProp("ro.miui.ui.version.name")}")
        appendLine("BuildIncremental: ${getSystemProp("ro.build.version.incremental")}")
        if (uname != null) {
            appendLine("KernelRelease: ${uname.release}")
            appendLine("KernelVersion: ${uname.version}")
            appendLine("Machine: ${uname.machine}")
        }
        appendLine()

        appendLine("=== Runtime Status ===")
        if (status == null) {
            appendLine("RuntimeStatus: unavailable")
        } else {
            appendLine("BinderAlive: ${status.binderAlive}")
            appendLine("GreezeReady: ${status.greezeReady}")
            appendLine("AurogonReady: ${status.aurogonReady}")
            appendLine("DozeReady: ${status.dozeReady}")
            appendLine("PowerKeeperConnected: ${status.powerKeeperConnected}")
            appendLine("FrameworkApiVersion: ${status.frameworkApiVersion}")
            appendLine("FrozenUidCount: ${status.frozenUids.size}")
            appendLine("FrozenUids: ${status.frozenUids}")
            appendLine("KernelFreezer: ${kernelFreezer?.label ?: "unknown"}")
            appendLine("HookCoverage: ${status.hookCoverage.ratioOrNull() ?: "unknown"}")
            appendLine("HookUnsupported: ${status.hookCoverage.unsupportedIds}")
            appendLine("HookGated: ${status.hookCoverage.gatedIds}")
            appendLine("HookFailed: ${status.hookCoverage.failedIds}")
            appendLine("Capabilities: ${status.hookCoverage.capabilities}")
        }
    }

    @SuppressLint("PrivateApi")
    private fun getSystemProp(key: String): String {
        return runCatching {
            val spClass = Class.forName("android.os.SystemProperties")
            val getMethod = spClass.getMethod("get", String::class.java, String::class.java)
            (getMethod.invoke(null, key, "") as? String).orEmpty()
        }.getOrDefault("")
    }

    private fun collectRootDiagnostics(dir: File) {
        val dirPath = dir.absolutePath
        val script = """
            cat ${Logger.LOCAL_LOG_PATH} > "$dirPath/xmillet_log.txt" 2>/dev/null || rm -f "$dirPath/xmillet_log.txt"
            cat ${Logger.LOCAL_LAST_LOG_PATH} > "$dirPath/xmillet_last.log" 2>/dev/null || rm -f "$dirPath/xmillet_last.log"
            cat ${Logger.LOCAL_LOG_DIR}/freeze_stats.json > "$dirPath/freeze_stats.json" 2>/dev/null || rm -f "$dirPath/freeze_stats.json"
            logcat -b all -v uid -d > "$dirPath/logcat.txt" 2>&1
            dmesg -r > "$dirPath/dmesg.txt" 2>&1
            toybox ps -T -A -w -o PID,TID,UID,COMM,CMDLINE,CMD,LABEL,STAT,WCHAN > "$dirPath/process.txt" 2>&1
            dumpsys greezemanager > "$dirPath/dumpsys_greezemanager.txt" 2>&1
            dumpsys deviceidle > "$dirPath/dumpsys_deviceidle.txt" 2>&1
            getprop > "$dirPath/props.txt" 2>&1
            chmod 0644 "$dirPath"/* 2>/dev/null || true
        """.trimIndent()
        PrivilegedExecutor.exec(script, timeoutMs = 60_000L)
    }

    private fun collectFallbackDiagnostics(dir: File) {
        runCommandToFile(listOf("logcat", "-d", "-v", "threadtime"), File(dir, "logcat.txt"))
        runCommandToFile(listOf("getprop"), File(dir, "props.txt"))
    }

    private fun runCommandToFile(command: List<String>, outFile: File) {
        runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            process.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            process.waitFor()
        }
    }

    internal fun writeTarGz(files: List<File>, outputFile: File) {
        GZIPOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { gzOut ->
            val buffer = ByteArray(8192)
            for (file in files) {
                val length = file.length()
                val mtimeSec = (file.lastModified() / 1000L).coerceAtLeast(0L)
                val header = createTarHeader(file.name, length, mtimeSec)
                gzOut.write(header)

                var remaining = length
                FileInputStream(file).use { input ->
                    while (remaining > 0L) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read <= 0) break
                        gzOut.write(buffer, 0, read)
                        remaining -= read
                    }
                }
                if (remaining > 0L) {
                    writeZeroPadding(gzOut, remaining.toInt())
                }
                val pad = ((TAR_BLOCK_SIZE - (length % TAR_BLOCK_SIZE).toInt()) % TAR_BLOCK_SIZE)
                if (pad > 0) {
                    writeZeroPadding(gzOut, pad)
                }
            }
            // 标准 tar 结束标志：两个 512 字节全零块
            writeZeroPadding(gzOut, TAR_BLOCK_SIZE * 2)
        }
    }

    private fun createTarHeader(name: String, size: Long, mtimeSec: Long): ByteArray {
        val header = ByteArray(TAR_BLOCK_SIZE)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        nameBytes.copyInto(header, destinationOffset = 0, endIndex = minOf(nameBytes.size, 100))

        writeAscii(header, 100, "0000644\u0000")
        writeAscii(header, 108, "0000000\u0000")
        writeAscii(header, 116, "0000000\u0000")
        writeAscii(header, 124, String.format(Locale.ROOT, "%011o\u0000", size))
        writeAscii(header, 136, String.format(Locale.ROOT, "%011o\u0000", mtimeSec))
        for (i in 148 until 156) {
            header[i] = ' '.code.toByte()
        }
        header[156] = '0'.code.toByte()
        writeAscii(header, 257, "ustar\u0000")
        writeAscii(header, 263, "00")
        writeAscii(header, 265, "root\u0000")
        writeAscii(header, 297, "root\u0000")

        var checksum = 0
        for (b in header) {
            checksum += (b.toInt() and 0xFF)
        }
        writeAscii(header, 148, String.format(Locale.ROOT, "%06o\u0000 ", checksum))
        return header
    }

    private fun writeAscii(target: ByteArray, offset: Int, text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        bytes.copyInto(target, destinationOffset = offset)
    }

    private fun writeZeroPadding(out: OutputStream, count: Int) {
        if (count <= 0) return
        out.write(ByteArray(count))
    }
}
