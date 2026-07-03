package com.luaforge.studio.lxclua.plugin.bridge

import com.luaforge.studio.lxclua.utils.LogCatcher
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 重定向 Lua 的 print() 到插件日志系统
 *
 * 在 LuaPluginLoader 中替换全局 print，使 print() 调用自动写入该插件的日志文件
 */
class LuaPrintRedirect(private val pluginId: String, pluginsDir: File) {

    private val logTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logFileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val logDir = File(pluginsDir, "$pluginId/logs").also { it.mkdirs() }

    /**
     * 对应 Lua 的 print(...) 函数
     * 参数会被制表符连接，输出到日志文件和 logcat
     */
    fun print(vararg args: Any?) {
        val message = args.joinToString("\t") { it?.toString() ?: "nil" }
        val now = Date()
        val logFile = File(logDir, "${logFileDateFormat.format(now)}.log")
        val timestamp = logTimeFormat.format(now)
        val line = "$timestamp [INFO] [LUA] $message\n"

        try {
            logFile.appendText(line)
        } catch (_: Exception) { }

        LogCatcher.i("Lua/$pluginId", message)
    }
}