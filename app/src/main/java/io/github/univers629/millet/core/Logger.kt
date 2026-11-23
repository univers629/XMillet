package io.github.univers629.millet.core

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * XMillet 统一日志组件。
 *
 * 规范要求：
 * 1. 统一 Tag：`XMillet`
 * 2. 标准事件格式：`[级别] 事件 | 包名 | uid | 原因`
 * 3. 安装期日志（`开始XMillet Hook`、`xxx成功`、`共屏蔽N处应用无响应`、安装失败错误）不受 `isLog` 限制直接输出。
 * 4. 运行期干预日志受 `isLog`（或 Doze 专属 `dozeLog`）门控。
 * 5. 本地文件落盘统一投递至单线程 Handler（`XMillet-LogIo`），不在 Hook 回调线程内执行文件 IO。
 */
object Logger {

    const val TAG = "XMillet"
    const val LOCAL_LOG_DIR = "/data/system/XMillet"
    const val LOCAL_LOG_PATH = "/data/system/XMillet/log"
    const val LOCAL_LAST_LOG_PATH = "/data/system/XMillet/last.log"

    enum class Level(val code: String, val priority: Int) {
        DEBUG("D", 1),
        INFO("I", 2),
        WARN("W", 3),
        ERROR("E", 4);

        companion object {
            fun fromConfig(name: String?): Level {
                return when (name?.lowercase(Locale.ROOT)) {
                    "debug", "d" -> DEBUG
                    "info", "i", "verbose", "v" -> INFO
                    "warn", "w" -> WARN
                    "error", "e" -> ERROR
                    else -> DEBUG
                }
            }
        }
    }

    /** 单线程异步 IO Handler，避免在 Hook 回调中阻塞宿主线程 */
    private val ioLock = Any()

    @Volatile
    private var ioThread: HandlerThread? = null

    @Volatile
    private var ioHandler: Handler? = null

    @Volatile
    private var hotReloading: Boolean = false

    private fun ensureIoHandler(): Handler? {
        if (hotReloading) return null
        ioHandler?.let { return it }
        synchronized(ioLock) {
            if (hotReloading) return null
            ioHandler?.let { return it }
            val thread = HandlerThread("XMillet-LogIo", Process.THREAD_PRIORITY_BACKGROUND)
            thread.start()
            val handler = Handler(thread.looper)
            ioThread = thread
            ioHandler = handler
            return handler
        }
    }

    /**
     * 热重载卸载前安全退出日志 IO 线程，排空已有任务且不再接收新异步投递。
     */
    fun onHotReloading() {
        val thread: HandlerThread?
        val handler: Handler?
        synchronized(ioLock) {
            hotReloading = true
            handler = ioHandler
            ioHandler = null
            thread = ioThread
            ioThread = null
        }
        // 退出前把缓冲区落盘并关闭文件句柄（quitSafely 会先执行完已入队任务）
        try {
            handler?.post { closeLocalWriter() }
        } catch (_: Throwable) {
        }
        thread?.quitSafely()
    }

    private fun currentSnapshot(): PrefsSnapshot {
        return PrefReader.activePrefs()
    }

    /**
     * 格式化结构化干预事件：`事件 | 包名 | uid | 原因`
     */
    fun formatEvent(
        event: String,
        pkg: String = "-",
        uid: Int = -1,
        reason: String = "-"
    ): String {
        val uidStr = if (uid >= 0) uid.toString() else "-"
        return "$event | $pkg | $uidStr | $reason"
    }

    /**
     * 结构化运行期日志（受 `isLog` 门控）。
     */
    fun logEvent(
        level: Level,
        event: String,
        pkg: String = "-",
        uid: Int = -1,
        reason: String = "-"
    ) {
        val prefs = currentSnapshot()
        if (!prefs.isLog && level != Level.ERROR) return
        emit(level, formatEvent(event, pkg, uid, reason), prefs)
    }

    /**
     * 运行期 Debug 日志（受 `isLog` 门控）。
     */
    fun debug(message: String) {
        val prefs = currentSnapshot()
        if (!prefs.isLog) return
        emit(Level.DEBUG, message, prefs)
    }

    /**
     * 运行期 Info 日志（受 `isLog` 门控）。
     */
    fun info(message: String) {
        val prefs = currentSnapshot()
        if (!prefs.isLog) return
        emit(Level.INFO, message, prefs)
    }

    /**
     * 运行期 Warn 日志（受 `isLog` 门控）。
     */
    fun warn(message: String) {
        val prefs = currentSnapshot()
        if (!prefs.isLog) return
        emit(Level.WARN, message, prefs)
    }

    /**
     * Doze 专属日志（受 `dozeLog` 或 `isLog` 门控）。
     */
    fun doze(level: Level, message: String) {
        val prefs = currentSnapshot()
        if (!prefs.dozeLog && !prefs.isLog) return
        emit(level, message, prefs)
    }

    /**
     * 电量观测专属日志（受 `batteryChangeObserve` 或 `isLog` 门控）。
     */
    fun battery(level: Level, message: String) {
        val prefs = currentSnapshot()
        if (!prefs.batteryChangeObserve && !prefs.isLog) return
        emit(level, message, prefs)
    }

    /**
     * system_server 启动时将上一次的 `/data/system/XMillet/log` 轮转为 `last.log`。
     */
    fun initSystemLogRotation() {
        postIoTask {
            closeLocalWriter()
            localWrittenBytes = 0L
            try {
                val dir = File(LOCAL_LOG_DIR)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val lastFile = File(LOCAL_LAST_LOG_PATH)
                val logFile = File(LOCAL_LOG_PATH)
                if (lastFile.exists()) {
                    lastFile.delete()
                }
                if (logFile.exists()) {
                    logFile.renameTo(lastFile)
                }
                if (!logFile.exists()) {
                    logFile.createNewFile()
                }
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * 装配期/无条件 Info 日志（不受 `isLog` 限制，用于安装成功回执等，始终尝试落盘）。
     */
    fun alwaysInfo(message: String) {
        emit(Level.INFO, message, currentSnapshot(), forceLocal = true)
    }

    /**
     * 装配期/无条件 Warn 日志（用于 `开始XMillet Hook`、`共屏蔽N处应用无响应`、每个成功项等，始终尝试落盘）。
     */
    fun alwaysWarn(message: String) {
        emit(Level.WARN, message, currentSnapshot(), forceLocal = true)
    }

    /**
     * 错误日志（无条件输出，附带异常摘要，始终尝试落盘）。
     */
    fun error(message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) {
            "$message: ${throwable.javaClass.simpleName}(${throwable.message})"
        } else {
            message
        }
        emit(Level.ERROR, fullMsg, currentSnapshot(), forceLocal = true)
    }

    /**
     * 后台单线程异步任务投递入口（供 `/proc` 读取、文件落盘等非阻塞操作复用）。
     */
    fun postIoTask(task: () -> Unit) {
        val handler = ensureIoHandler() ?: return
        try {
            handler.post {
                try {
                    task()
                } catch (t: Throwable) {
                    Log.e(TAG, "[E] async task error: ${t.message}")
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun emit(
        level: Level,
        body: String,
        prefs: PrefsSnapshot,
        forceLocal: Boolean = false
    ) {
        val minLevel = Level.fromConfig(prefs.logLevel)
        if (level.priority < minLevel.priority && level != Level.ERROR) {
            return
        }

        val formattedLine = "$TAG [${level.code}] $body"

        // 1. 输出到 Modern LibXposed；框架未注入时退回 Android Logcat
        var loggedToXposed = false
        if (prefs.logType != "file") {
            try {
                if (ModernBridge.isModernActive) {
                    val priority = when (level) {
                        Level.DEBUG -> Log.DEBUG
                        Level.INFO -> Log.INFO
                        Level.WARN -> Log.WARN
                        Level.ERROR -> Log.ERROR
                    }
                    ModernBridge.xposedModule?.log(priority, TAG, "[${level.code}] $body")
                    loggedToXposed = true
                }
            } catch (_: Throwable) {
            }
        }

        if (!loggedToXposed) {
            when (level) {
                Level.DEBUG -> Log.d(TAG, "[${level.code}] $body")
                Level.INFO -> Log.i(TAG, "[${level.code}] $body")
                Level.WARN -> Log.w(TAG, "[${level.code}] $body")
                Level.ERROR -> Log.e(TAG, "[${level.code}] $body")
            }
        }

        // 2. 如需写入本地文件，统一投递到单线程 IO Handler。
        //    仅由 logToLocal / logType=file / forceLocal 决定，isLog 只控制 LSPosed 输出，避免同一条日志双写。
        //    时间戳在事件发生时（Hook 线程）采集，避免 IO 队列积压导致时间漂移。
        if (prefs.logToLocal || prefs.logType == "file" || forceLocal) {
            val eventTimeMs = System.currentTimeMillis()
            postIoTask {
                appendLineToLocalFile(eventTimeMs, level, body)
            }
        }
    }

    // ===== 以下状态仅在 XMillet-LogIo 单线程内访问，无需加锁 =====
    private const val MAX_LOCAL_LOG_BYTES = 2L * 1024 * 1024
    private const val FLUSH_DELAY_MS = 1500L
    private var localWriter: java.io.BufferedWriter? = null
    private var localWrittenBytes = 0L
    private var flushScheduled = false
    /** 本地文件不可写（如 SELinux 拒绝）时彻底关闭本地落盘，只保留 LSPosed 输出。 */
    private var localWriteDisabled = false
    private val localTimeFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA) }
    private val reusableDate = Date()

    private val flushRunnable = Runnable {
        flushScheduled = false
        try {
            localWriter?.flush()
        } catch (_: Throwable) {
            closeLocalWriter()
        }
    }

    private fun openLocalWriter(): java.io.BufferedWriter? {
        localWriter?.let { return it }
        return try {
            val dir = File(LOCAL_LOG_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(LOCAL_LOG_PATH)
            localWrittenBytes = if (file.exists()) file.length() else 0L
            java.io.BufferedWriter(FileWriter(file, true), 8 * 1024).also { localWriter = it }
        } catch (_: Throwable) {
            degradeLocalWrite()
            null
        }
    }

    /** 本地落盘不可用时降级一次并关闭写入，避免后续每行都触发文件系统调用。 */
    private fun degradeLocalWrite() {
        closeLocalWriter()
        if (localWriteDisabled) return
        localWriteDisabled = true
        Logger.alwaysWarn("本地日志写入失败（可能被 SELinux 拒绝），已降级为仅 LSPosed 输出")
    }
    private fun closeLocalWriter() {
        try {
            localWriter?.flush()
            localWriter?.close()
        } catch (_: Throwable) {
        }
        localWriter = null
    }

    /** 超过上限时轮转为 last.log，防止长时间开机后日志无限增长。 */
    private fun rotateLocalLogIfNeeded() {
        if (localWrittenBytes < MAX_LOCAL_LOG_BYTES) return
        closeLocalWriter()
        try {
            val lastFile = File(LOCAL_LAST_LOG_PATH)
            if (lastFile.exists()) lastFile.delete()
            File(LOCAL_LOG_PATH).renameTo(lastFile)
        } catch (_: Throwable) {
        }
        localWrittenBytes = 0L
    }

    private fun appendLineToLocalFile(eventTimeMs: Long, level: Level, body: String) {
        if (localWriteDisabled) return
        try {
            rotateLocalLogIfNeeded()
            val writer = openLocalWriter() ?: run {
                degradeLocalWrite()
                return
            }
            reusableDate.time = eventTimeMs
            val line = "${localTimeFormat.format(reusableDate)} [${level.code}] $body\n"
            writer.write(line)
            // 近似字节数（中文按 3 字节估算），仅用于触发轮转
            localWrittenBytes += line.length * 3L
            if (level == Level.ERROR) {
                writer.flush()
            } else if (!flushScheduled) {
                flushScheduled = true
                ioHandler?.postDelayed(flushRunnable, FLUSH_DELAY_MS)
            }
        } catch (_: Throwable) {
            // 本地文件写入失败（含 SELinux 拒绝）时彻底降级，避免每行重复重试
            degradeLocalWrite()
        }
    }
}
