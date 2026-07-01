package com.androlua;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.luaforge.studio.lxclua.core.BuildConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 调试日志工具，写入 /sdcard/Download/lxc_debug_*.log
 * 用法：DebugLogger.log("tag", "msg") 或 DebugLogger.logError("tag", "msg", e)
 *
 * 条件编译规则（等价于 C 的 #ifdef DEBUG）：
 *   - DEBUG 构建：所有方法正常执行，日志写入文件和 logcat
 *   - RELEASE 构建：所有方法立即 return，零开销，R8 会将调用处完全内联消除
 *   调用方无需任何修改。
 */
public class DebugLogger {
    private static final String TAG = "LXC-DEBUG";
    private static PrintWriter writer;
    private static File logFile;
    private static boolean initialized;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    public static synchronized void init(Context context) {
        // Release 构建时直接跳过，等价于 #ifdef DEBUG
        if (!BuildConfig.DEBUG) return;

        if (initialized) return;
        initialized = true;
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) downloadDir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            logFile = new File(downloadDir, "lxc_debug_" + timestamp + ".log");
            writer = new PrintWriter(new FileWriter(logFile, true), true);
            write("=== DebugLogger init ===");
            write("path: " + logFile.getAbsolutePath());
            write("pkg: " + context.getPackageName());
            write("ver: " + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
            Log.i(TAG, "log file: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "init failed, fallback to internal", e);
            try {
                File dir = context.getFilesDir();
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                logFile = new File(dir, "lxc_debug_" + timestamp + ".log");
                writer = new PrintWriter(new FileWriter(logFile, true), true);
                write("=== DebugLogger init (internal) ===");
                write("path: " + logFile.getAbsolutePath());
            } catch (Exception e2) {
                Log.e(TAG, "fallback also failed", e2);
            }
        }
    }

    public static synchronized void log(String tag, String msg) {
        if (!BuildConfig.DEBUG) return;
        Log.i(TAG, "[" + tag + "] " + msg);
        write("[" + tag + "] " + msg);
    }

    public static synchronized void logError(String tag, String msg, Throwable e) {
        if (!BuildConfig.DEBUG) return;
        Log.e(TAG, "[" + tag + "] " + msg, e);
        write("[" + tag + "] ERROR: " + msg);
        if (e != null) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            write(sw.toString());
        }
    }

    public static synchronized String getLogPath() {
        if (!BuildConfig.DEBUG) return "disabled in release";
        return logFile != null ? logFile.getAbsolutePath() : "not initialized";
    }

    private static void write(String msg) {
        if (writer == null) return;
        try {
            String ts = dateFormat.format(new Date());
            writer.println(ts + " " + msg);
            writer.flush();
        } catch (Exception e) {
            Log.e(TAG, "write failed: " + e.getMessage());
        }
    }

    public static synchronized void close() {
        if (!BuildConfig.DEBUG) return;
        try {
            if (writer != null) { writer.flush(); writer.close(); }
        } catch (Exception ignored) {}
        writer = null;
    }
}