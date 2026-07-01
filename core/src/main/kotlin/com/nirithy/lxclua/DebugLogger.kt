package com.nirithy.lxclua

import android.content.Context
import android.os.Environment
import android.util.Log
import com.luaforge.studio.lxclua.core.BuildConfig
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试日志工具，写入 /sdcard/Download/lxc_debug_*.log
 * 用法：DebugLogger.log("tag", "msg") 或 DebugLogger.logError("tag", "msg", e)
 *
 * 条件编译规则（等价于 C 的 #ifdef DEBUG）：
 * - DEBUG 构建：所有方法正常执行，日志写入文件和 logcat
 * - RELEASE 构建：所有方法立即 return，零开销，R8 会将调用处完全内联消除
 * 调用方无需任何修改。
 */
object DebugLogger {
    private const val TAG = "LXC-DEBUG"
    private var writer: PrintWriter? = null
    private var logFile: File? = null
    private var initialized = false
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun init(context: Context) {
        // Release 构建时直接跳过，等价于 #ifdef DEBUG
        if (!BuildConfig.DEBUG) return

        if (initialized) return
        initialized = true
        try {
            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            logFile = File(downloadDir, "lxc_debug_" + timestamp + ".log")
            writer = PrintWriter(FileWriter(logFile, true), true)
            write("=== DebugLogger init ===")
            write("path: " + logFile!!.getAbsolutePath())
            write("pkg: " + context.getPackageName())
            write(
                "ver: " + context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName
            )
            Log.i(TAG, "log file: " + logFile!!.getAbsolutePath())
        } catch (e: Exception) {
            Log.e(TAG, "init failed, fallback to internal", e)
            try {
                val dir = context.getFilesDir()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                logFile = File(dir, "lxc_debug_" + timestamp + ".log")
                writer = PrintWriter(FileWriter(logFile, true), true)
                write("=== DebugLogger init (internal) ===")
                write("path: " + logFile!!.getAbsolutePath())
            } catch (e2: Exception) {
                Log.e(TAG, "fallback also failed", e2)
            }
        }
    }

    @Synchronized
    fun log(tag: String?, msg: String?) {
        if (!BuildConfig.DEBUG) return
        Log.i(TAG, "[" + tag + "] " + msg)
        write("[" + tag + "] " + msg)
    }

    @Synchronized
    fun logError(tag: String?, msg: String?, e: Throwable?) {
        if (!BuildConfig.DEBUG) return
        Log.e(TAG, "[" + tag + "] " + msg, e)
        write("[" + tag + "] ERROR: " + msg)
        if (e != null) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            write(sw.toString())
        }
    }

    @get:Synchronized
    val logPath: String
        get() {
            if (!BuildConfig.DEBUG) return "disabled in release"
            return if (logFile != null) logFile!!.getAbsolutePath() else "not initialized"
        }

    private fun write(msg: String?) {
        if (writer == null) return
        try {
            val ts = dateFormat.format(Date())
            writer!!.println(ts + " " + msg)
            writer!!.flush()
        } catch (e: Exception) {
            Log.e(TAG, "write failed: " + e.message)
        }
    }

    @Synchronized
    fun close() {
        if (!BuildConfig.DEBUG) return
        try {
            if (writer != null) {
                writer!!.flush()
                writer!!.close()
            }
        } catch (ignored: Exception) {
        }
        writer = null
    }
}