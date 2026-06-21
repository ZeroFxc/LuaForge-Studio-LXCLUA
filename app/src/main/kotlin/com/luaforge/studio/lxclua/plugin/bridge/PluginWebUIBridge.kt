package com.luaforge.studio.lxclua.plugin.bridge

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.luaforge.studio.lxclua.plugin.PluginManager
import com.luaforge.studio.lxclua.plugin.api.IPluginBridgeWebUI
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.NavigationState
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import com.luajava.LuaState
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 插件 WebUI 桥接 API
 *
 * 允许插件通过 WebView 加载自定义 HTML/CSS/JS 界面，
 * 并通过 window.LXC JavaScript 接口实现 Web ↔ Lua 双向通信。
 *
 * ## 插件使用方式:
 *
 * ### 1. 目录结构
 * ```
 * my-plugin/
 *   web/
 *     index.html        // 入口页面
 *     style.css          // 样式
 *     app.js             // 逻辑
 * ```
 *
 * ### 2. JS 端 API (window.LXC)
 * ```js
 * // 基础
 * LXC.log("调试信息")
 * LXC.toast("提示消息")
 * LXC.getPluginId()          // → "com.example.myplugin"
 *
 * // 调用 Lua
 * LXC.callLua("handleClick", '{"x": 100, "y": 200}')
 *
 * // 存储
 * LXC.setPluginData("theme", "dark")
 * LXC.getPluginData("theme")  // → "dark"
 *
 * // 导航
 * LXC.navigateBack()
 *
 * // 系统信息
 * LXC.getTimestamp()          // → 1718000000000
 * LXC.getSystemProperty("os.version")  // → "4.19.xxx"
 * LXC.getThemeMode()          // → "dark" | "light"
 * LXC.getDeviceInfo()         // → "{...}"
 *
 * // 系统交互
 * LXC.getClipboard()          // 读取剪贴板文本
 * LXC.sendNotification("标题", "内容")  // 发送通知
 * LXC.execShell("echo hello") // 执行 Shell 命令
 * LXC.copyToClipboard("文本") // 复制到剪贴板
 * LXC.shareText("分享内容")   // 系统分享
 * ```
 *
 * ### 3. Lua 端
 * 插件 main.lua 中定义全局函数，JS 通过 callLua 调用:
 * ```lua
 * function handleClick(jsonStr)
 *     local data = json.decode(jsonStr)
 *     plugin.logger.info(TAG, "点击坐标: " .. data.x .. "," .. data.y)
 * end
 * ```
 */
class PluginWebUIBridge(private val pluginId: String) : IPluginBridgeWebUI {

    companion object {
        private const val TAG = "PluginWebUIBridge"
    }

    /**
     * 持有 WebView 引用以支持宿主 → Web 发送消息
     */
    private var webViewRef: WebView? = null

    /** 导航返回回调 */
    var onBackRequested: (() -> Unit)? = null

    /**
     * 消息队列：在 WebView 就绪前暂存待发送消息
     */
    private val pendingMessages = mutableListOf<String>()

    /**
     * 获取插件的 LuaState（用于 callLua 执行）
     */
    private fun getPluginLuaState(): LuaState? {
        return PluginManager.loadedPlugins
            .find { it.manifest.id == pluginId }
            ?.luaState
    }

    /**
     * 向 WebView 发送消息（宿主 → Web）
     * 调用 window.dispatchEvent(new CustomEvent('lxc-message', {detail: message}))
     *
     * @param message JSON 格式消息字符串
     */
    fun sendToWebView(message: String) {
        val wv = webViewRef
        if (wv != null) {
            // 先清空待发送队列
            if (pendingMessages.isNotEmpty()) {
                for (msg in pendingMessages) {
                    wv.post {
                        wv.evaluateJavascript(
                            """window.dispatchEvent(new CustomEvent('lxc-message', {detail: $msg}));""",
                            null
                        )
                    }
                }
                pendingMessages.clear()
            }
            wv.post {
                wv.evaluateJavascript(
                    """window.dispatchEvent(new CustomEvent('lxc-message', {detail: $message}));""",
                    null
                )
            }
        } else {
            pendingMessages.add(message)
        }
    }

    /**
     * 设置 WebView 引用（由 PluginWebUIScreen 在创建 WebView 后调用）
     */
    fun attachWebView(webView: WebView) {
        this.webViewRef = webView
        // 发送待处理消息
        if (pendingMessages.isNotEmpty()) {
            for (msg in pendingMessages) {
                webView.post {
                    webView.evaluateJavascript(
                        """window.dispatchEvent(new CustomEvent('lxc-message', {detail: $msg}));""",
                        null
                    )
                }
            }
            pendingMessages.clear()
        }
    }

    // ==================== IPluginBridgeWebUI 实现 ====================

    /**
     * 打开插件 Web 界面
     */
    override fun open(page: String): Boolean {
        val pageFile = page.ifEmpty { "index.html" }
        if (!webFileExists(pageFile)) {
            android.util.Log.w("PluginWebUI[$pluginId]", "Web 文件不存在: $pageFile")
            return false
        }
        NavigationState.openWebUI(pluginId, pageFile)
        return true
    }

    /**
     * 关闭当前 Web 界面
     */
    override fun close(): Boolean {
        NavigationState.closeWebUI()
        return true
    }

    /**
     * 检查是否正在显示 Web 界面
     */
    override fun isShowing(): Boolean = NavigationState.isWebUIShowing()

    /**
     * 向 WebView 发送消息（宿主 → Web）
     */
    override fun sendToWeb(jsonMessage: String) {
        sendToWebView(jsonMessage)
    }

    /**
     * 向 WebView 执行 JavaScript 代码
     */
    override fun evaluateJs(jsCode: String) {
        webViewRef?.post {
            webViewRef?.evaluateJavascript(jsCode, null)
        }
    }

    /**
     * 检查指定 Web 文件是否存在
     */
    override fun webFileExists(filename: String): Boolean {
        val webDir = getWebRootDir() ?: return false
        return File(webDir, filename).exists()
    }

    /**
     * 列出 web/ 目录下所有文件
     */
    override fun listFiles(): Array<String> = listWebFiles()

    /**
     * 获取 Web 资源根目录路径
     */
    override fun getWebRoot(): String {
        return getWebRootDir()?.absolutePath ?: ""
    }

    /**
     * 获取 WebView 实例引用
     */
    override fun getWebView(): WebView? = webViewRef

    /**
     * Java-Native-Lua 三方桥接器
     *
     * 通过 @JavascriptInterface 暴露给 WebView 的 JS 上下文，
     * JS 代码通过 window.LXC.* 访问所有方法。
     */
    inner class JsApiBridge {
        // ==================== 日志 ====================

        /**
         * JS 端日志输出到 Android Logcat
         */
        @JavascriptInterface
        fun log(message: String) {
            android.util.Log.d("PluginWebUI[$pluginId]", "[JS] $message")
        }

        // ==================== Toast ====================

        /**
         * 显示短 Toast 提示
         */
        @JavascriptInterface
        fun toast(message: String) {
            val context = PluginManager.appContext ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * 显示长 Toast 提示
         */
        @JavascriptInterface
        fun toastLong(message: String) {
            val context = PluginManager.appContext ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }

        // ==================== Lua 调用 ====================

        /**
         * 从 JS 调用插件的 Lua 全局函数
         *
         * 在插件 main.lua 中定义函数:
         * ```lua
         * function onJsEvent(data)
         *     plugin.logger.info(TAG, "JS 传来: " .. data)
         *     return "Lua 已处理"
         * end
         * ```
         * JS 调用:
         * ```js
         * var result = LXC.callLua("onJsEvent", '{"action": "save"}');
         * console.log(result); // "Lua 已处理"
         * ```
         *
         * @param funcName Lua 全局函数名
         * @param argsJson JSON 格式参数（可选，传空字符串表示无参数）
         * @return Lua 函数返回值（字符串形式），失败返回 "error: ..."
         */
        @JavascriptInterface
        fun callLua(funcName: String, argsJson: String): String {
            android.util.Log.d(TAG, "[$pluginId] callLua func=$funcName, args=${argsJson.take(200)}")
            return try {
                val luaState = getPluginLuaState()
                if (luaState == null) {
                    android.util.Log.e(TAG, "[$pluginId] callLua 失败: 插件 '$pluginId' 的 LuaState 未初始化")
                    return "error: 插件 Lua 状态未初始化"
                }

                // 获取全局函数
                luaState.getGlobal(funcName)
                if (!luaState.isFunction(-1)) {
                    luaState.pop(1)
                    android.util.Log.w(TAG, "[$pluginId] callLua 失败: 全局函数 '$funcName' 不存在")
                    return "error: 函数 '$funcName' 不存在"
                }

                // 压入参数
                val argCount = if (argsJson.isNotEmpty()) {
                    luaState.pushString(argsJson)
                    1
                } else {
                    0
                }

                // 调用函数
                val status = luaState.pcall(argCount, 1, 0)
                if (status != 0) {
                    val errMsg = luaState.toString(-1)
                    luaState.pop(1)
                    android.util.Log.e(TAG, "[$pluginId] callLua pcall 失败: func=$funcName, error=$errMsg")
                    return "error: Lua调用失败 - $errMsg"
                }

                // 获取返回值
                val result = if (luaState.isNil(-1)) {
                    "nil"
                } else if (luaState.isString(-1)) {
                    luaState.toString(-1) ?: ""
                } else if (luaState.isNumber(-1)) {
                    luaState.toNumber(-1).toString()
                } else if (luaState.isBoolean(-1)) {
                    luaState.toBoolean(-1).toString()
                } else {
                    luaState.toString(-1) ?: "unknown"
                }
                luaState.pop(1)
                android.util.Log.d(TAG, "[$pluginId] callLua 成功: func=$funcName, result=${result.take(100)}")
                result
            } catch (e: Exception) {
                android.util.Log.e(TAG, "[$pluginId] callLua 异常: func=$funcName", e)
                "error: ${e.message}"
            }
        }

        /**
         * 从 JS 触发 Lua 事件（通过 EventManager）
         *
         * 插件注册事件:
         * ```lua
         * plugin.events.on("webui:action", function(data)
         *     plugin.logger.info(TAG, "WebUI 动作: " .. data.action)
         * end)
         * ```
         * JS 触发:
         * ```js
         * LXC.fireEvent("webui:action", '{"action": "save"}');
         * ```
         *
         * @param eventName 事件名称
         * @param dataJson JSON 格式事件数据
         */
        @JavascriptInterface
        fun fireEvent(eventName: String, dataJson: String) {
            try {
                EventManager.fireEvent("WEBUI_$eventName", pluginId, dataJson)
            } catch (e: Exception) {
                android.util.Log.e("PluginWebUI[$pluginId]", "fireEvent 异常", e)
            }
        }

        // ==================== 数据存储 ====================

        /**
         * JS 端读取插件持久化数据
         *
         * @param key 键名
         * @return 值，不存在时返回空字符串
         */
        @JavascriptInterface
        fun getPluginData(key: String): String {
            val context = PluginManager.appContext ?: return ""
            val prefs = context.getSharedPreferences(
                "plugin_config_$pluginId",
                Context.MODE_PRIVATE
            )
            return prefs.getString(key, "") ?: ""
        }

        /**
         * JS 端写入插件持久化数据
         *
         * @param key 键名
         * @param value 值
         */
        @JavascriptInterface
        fun setPluginData(key: String, value: String) {
            val context = PluginManager.appContext ?: return
            context.getSharedPreferences("plugin_config_$pluginId", Context.MODE_PRIVATE)
                .edit().putString(key, value).apply()
        }

        /**
         * JS 端删除插件持久化数据
         *
         * @param key 键名
         */
        @JavascriptInterface
        fun removePluginData(key: String) {
            val context = PluginManager.appContext ?: return
            context.getSharedPreferences("plugin_config_$pluginId", Context.MODE_PRIVATE)
                .edit().remove(key).apply()
        }

        /**
         * JS 端清空插件所有持久化数据
         */
        @JavascriptInterface
        fun clearPluginData() {
            val context = PluginManager.appContext ?: return
            context.getSharedPreferences("plugin_config_$pluginId", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }

        // ==================== 导航 ====================

        /**
         * 获取当前插件 ID
         */
        @JavascriptInterface
        fun getPluginId(): String = pluginId

        /**
         * 通知宿主返回上一页
         */
        @JavascriptInterface
        fun navigateBack() {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onBackRequested?.invoke()
            }
        }

        // ==================== 系统 ====================

        /**
         * 获取设备信息 JSON
         */
        @JavascriptInterface
        fun getDeviceInfo(): String {
            return try {
                val context = PluginManager.appContext ?: return "{}"
                val info = mapOf(
                    "model" to android.os.Build.MODEL,
                    "manufacturer" to android.os.Build.MANUFACTURER,
                    "sdkVersion" to android.os.Build.VERSION.SDK_INT,
                    "versionRelease" to android.os.Build.VERSION.RELEASE,
                    "language" to java.util.Locale.getDefault().language,
                    "screenWidth" to context.resources.displayMetrics.widthPixels,
                    "screenHeight" to context.resources.displayMetrics.heightPixels,
                    "density" to context.resources.displayMetrics.density
                )
                val json = StringBuilder("{")
                info.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) json.append(",")
                    json.append("\"$k\":")
                    when (v) {
                        is Number -> json.append(v)
                        else -> json.append("\"$v\"")
                    }
                }
                json.append("}")
                json.toString()
            } catch (e: Exception) {
                "{}"
            }
        }

        /**
         * 震动反馈（需要 VIBRATE 权限）
         * @param durationMs 震动时长（毫秒）
         */
        @JavascriptInterface
        fun vibrate(durationMs: Long) {
            try {
                val context = PluginManager.appContext ?: return
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (vibrator?.hasVibrator() == true) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                        durationMs,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    ))
                }
            } catch (e: SecurityException) {
                android.util.Log.w("PluginWebUI[$pluginId]", "震动权限被拒绝，请检查 AndroidManifest")
            } catch (e: Exception) {
                android.util.Log.e("PluginWebUI[$pluginId]", "震动失败", e)
            }
        }

        /**
         * 将文本复制到剪贴板
         *
         * @param text 要复制的文本
         */
        @JavascriptInterface
        fun copyToClipboard(text: String) {
            val context = PluginManager.appContext ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("plugin_webui", text)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("PluginWebUI[$pluginId]", "复制失败", e)
                }
            }
        }

        // ==================== 系统信息（新增 API） ====================

        /**
         * 获取电池信息 JSON
         * 返回: {"level":75,"status":"discharging","charging":false}
         */
        @JavascriptInterface
        fun getBatteryInfo(): String {
            return try {
                val context = PluginManager.appContext ?: return "{}"
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val intent = context.registerReceiver(null, filter) ?: return "{}"

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 1)
                val pct = if (scale > 0) (level * 100 / scale) else -1
                val statusCode = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)

                val status = when (statusCode) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                    else -> "unknown"
                }

                "{\"level\":$pct,\"status\":\"$status\",\"charging\":${plugged != 0}}"
            } catch (e: Exception) {
                android.util.Log.e("PluginWebUI[$pluginId]", "获取电池信息失败", e)
                "{}"
            }
        }

        /**
         * 获取网络类型
         * 返回: "wifi" / "mobile" / "ethernet" / "none"
         */
        @JavascriptInterface
        fun getNetworkType(): String {
            return try {
                val context = PluginManager.appContext ?: return "none"
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return "none"
                val ni = cm.activeNetworkInfo ?: return "none"
                if (!ni.isConnected) return "none"

                when (ni.type) {
                    ConnectivityManager.TYPE_WIFI -> "wifi"
                    ConnectivityManager.TYPE_MOBILE -> "mobile"
                    ConnectivityManager.TYPE_ETHERNET -> "ethernet"
                    else -> "other"
                }
            } catch (e: Exception) {
                "none"
            }
        }

        /**
         * 获取应用版本名称
         */
        @JavascriptInterface
        fun getAppVersion(): String {
            return try {
                val context = PluginManager.appContext ?: return ""
                val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pkgInfo.versionName ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        /**
         * 获取应用存储路径（外部文件根目录）
         */
        @JavascriptInterface
        fun getStoragePath(): String {
            return try {
                val context = PluginManager.appContext
                context?.getExternalFilesDir(null)?.absolutePath ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        // ==================== 系统交互（新增 API） ====================

        /**
         * 在浏览器中打开 URL
         */
        @JavascriptInterface
        fun openUrl(url: String) {
            try {
                val context = PluginManager.appContext ?: return
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("PluginWebUI[$pluginId]", "打开 URL 失败: $url", e)
            }
        }

        /**
         * 通过系统分享文本
         */
        @JavascriptInterface
        fun shareText(text: String) {
            try {
                val context = PluginManager.appContext ?: return
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                val chooser = Intent.createChooser(intent, "分享到").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                android.util.Log.e("PluginWebUI[$pluginId]", "分享失败", e)
            }
        }

        // ==================== 扩展 API ====================

        /**
         * 获取当前系统时间戳（毫秒）
         * 返回当前时间距 1970-01-01 的毫秒数
         */
        @JavascriptInterface
        fun getTimestamp(): Long {
            return System.currentTimeMillis()
        }

        /**
         * 读取 Java 系统属性
         * 常用键: os.version, java.vm.version, file.separator 等
         *
         * @param key 属性键名
         * @return 属性值，不存在时返回空字符串
         */
        @JavascriptInterface
        fun getSystemProperty(key: String): String {
            return try {
                System.getProperty(key) ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        /**
         * 获取当前系统深色/浅色主题模式
         *
         * @return "dark" 表示深色模式，"light" 表示浅色模式
         */
        @JavascriptInterface
        fun getThemeMode(): String {
            return try {
                val context = PluginManager.appContext ?: return "light"
                val nightModeFlags = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                when (nightModeFlags) {
                    Configuration.UI_MODE_NIGHT_YES -> "dark"
                    Configuration.UI_MODE_NIGHT_NO -> "light"
                    else -> "unknown"
                }
            } catch (e: Exception) {
                "light"
            }
        }

        /**
         * 读取系统剪贴板中的文本内容
         *
         * @return 剪贴板中的文本，无内容时返回空字符串
         */
        @JavascriptInterface
        fun getClipboard(): String {
            return try {
                val context = PluginManager.appContext ?: return ""
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return ""
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).text?.toString() ?: ""
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }

        /**
         * 发送系统通知
         *
         * @param title 通知标题
         * @param text 通知内容
         */
        @JavascriptInterface
        fun sendNotification(title: String, text: String) {
            try {
                val context = PluginManager.appContext ?: return
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "plugin_${pluginId}"

                // Android 8.0+ 需要创建通知渠道
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "插件通知 ($pluginId)",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "来自插件 WebUI 的通知"
                    }
                    manager.createNotificationChannel(channel)
                }

                val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.app.Notification.Builder(context, channelId)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setAutoCancel(true)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    android.app.Notification.Builder(context)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setAutoCancel(true)
                        .build()
                }

                val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                manager.notify(notifId, notification)
            } catch (e: Exception) {
                android.util.Log.e("PluginWebUI[$pluginId]", "发送通知失败", e)
            }
        }

        /**
         * 执行 Shell 命令并同步返回输出
         * 注意：耗时命令会阻塞，超时时间 15 秒
         *
         * @param cmd 要执行的 Shell 命令
         * @return 命令的标准输出，失败时返回 "error: ..."
         */
        @JavascriptInterface
        fun execShell(cmd: String): String {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))

                // 读取 stdout
                val stdout = BufferedReader(InputStreamReader(process.inputStream)).use {
                    it.readText()
                }
                // 读取 stderr
                val stderr = BufferedReader(InputStreamReader(process.errorStream)).use {
                    it.readText()
                }

                val exitCode = try {
                    Thread.sleep(50)
                    process.exitValue()
                } catch (e: IllegalThreadStateException) {
                    // 命令还在执行，等待最多 15 秒
                    val worker = Thread {
                        process.waitFor()
                    }
                    worker.start()
                    worker.join(15000)
                    if (worker.isAlive) {
                        process.destroy()
                        worker.interrupt()
                        return "error: 命令执行超时 (15s) - $cmd"
                    }
                    process.exitValue()
                }

                if (exitCode != 0 && stderr.isNotEmpty()) {
                    "exit=$exitCode\n$stdout\nstderr: $stderr"
                } else {
                    stdout.ifEmpty { stderr }
                }
            } catch (e: Exception) {
                "error: ${e.message}"
            }
        }

        // ==================== 插件数据存储（扩展） ====================

        /**
         * 列出当前插件的所有持久化数据键名（JSON 数组）
         */
        @JavascriptInterface
        fun getAllPluginDataKeys(): String {
            return try {
                val context = PluginManager.appContext ?: return "[]"
                val prefs = context.getSharedPreferences("plugin_config_$pluginId", Context.MODE_PRIVATE)
                val keys = prefs.all.keys
                val sb = StringBuilder("[")
                keys.forEachIndexed { i, k ->
                    if (i > 0) sb.append(",")
                    sb.append("\"$k\"")
                }
                sb.append("]")
                sb.toString()
            } catch (e: Exception) {
                "[]"
            }
        }
    }

    // ==================== Web 资源管理 ====================

    /**
     * 获取插件的 Web 资源根目录
     */
    fun getWebRootDir(): File? {
        val plugin = PluginManager.loadedPlugins.find { it.manifest.id == pluginId } ?: return null
        val webDir = File(plugin.directory, "web")
        return if (webDir.exists()) webDir else null
    }

    /**
     * 列出 Web 资源目录中的所有文件
     */
    fun listWebFiles(): Array<String> {
        val webDir = getWebRootDir() ?: return emptyArray()
        return webDir.listFiles { f -> f.isFile }?.map { it.name }?.toTypedArray() ?: emptyArray()
    }

    /**
     * 创建 WebView 并加载指定页面
     *
     * @param page 页面文件名（相对于 web/ 目录），默认 index.html
     * @param context 上下文
     * @return 配置好的 WebView 实例，失败返回 null
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(context: Context, page: String = "index.html"): WebView? {
        val webDir = getWebRootDir() ?: return null
        val pageFile = File(webDir, page)
        if (!pageFile.exists()) return null

        val webView = WebView(context).apply {
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            addJavascriptInterface(JsApiBridge(), "LXC")

            webViewClient = object : WebViewClient() {}
            webChromeClient = object : WebChromeClient() {}

            loadUrl("file://${pageFile.absolutePath}")
        }

        this.webViewRef = webView
        return webView
    }

    /**
     * 向 WebView 执行 JavaScript 代码（宿主 → Web）
     *
     * ```kotlin
     * bridge.evaluateJs(webView, "document.title = '新标题';")
     * ```
     *
     * @param webView WebView 实例
     * @param jsCode JavaScript 代码
     */
    fun evaluateJs(webView: WebView?, jsCode: String) {
        webView?.post {
            webView.evaluateJavascript(jsCode, null)
        }
    }

    /**
     * 向 WebView 执行 JavaScript 并获取返回值
     *
     * @param webView WebView 实例
     * @param jsCode JavaScript 代码（需返回字符串）
     * @param callback 结果回调
     */
    fun evaluateJsWithResult(webView: WebView?, jsCode: String, callback: (String) -> Unit) {
        webView?.post {
            webView.evaluateJavascript(jsCode) { result ->
                callback(result)
            }
        }
    }
}