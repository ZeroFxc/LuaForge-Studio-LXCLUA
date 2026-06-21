package com.luaforge.studio.lxclua.plugin.bridge

import com.luaforge.studio.lxclua.plugin.PluginManager
import com.luaforge.studio.lxclua.plugin.floating.FloatingManager
import com.luajava.LuaFunction
import java.io.File
import java.util.UUID

/**
 * 悬浮窗 API
 *
 * 为 Lua 插件提供悬浮窗创建和管理能力：
 * - plugin.floating.createBall(x, y, label, iconText, onClick, onSubmit)  -- 创建悬浮球
 * - plugin.floating.removeBall(id)                -- 移除悬浮球
 * - plugin.floating.showPanel(id, title, hint)     -- 展开默认输入面板
 * - plugin.floating.showPanelWebUI(id, title, page) -- 展开 WebUI 面板
 * - plugin.floating.showPanelCustom(id, title, view) -- 展开自定义 View 面板
 * - plugin.floating.hidePanel(id)                  -- 收起输入面板
 * - plugin.floating.updateBall(id, label)          -- 更新悬浮球文字
 * - plugin.floating.removeAll()                    -- 移除所有悬浮球
 * - plugin.floating.requestFocus(id)               -- 请求面板焦点
 * - plugin.floating.clearFocus(id)                 -- 清除面板焦点
 * - plugin.floating.sendToWeb(id, jsonMessage)     -- 向 WebUI 面板发送消息
 * - plugin.floating.evaluateJs(id, jsCode)         -- 向 WebUI 面板执行 JS
 */
class PluginFloating(private val pluginId: String = "") {

    companion object {
        private const val TAG = "PluginFloating"
    }

    private val ballCallbacks = mutableMapOf<String, LuaFunction<*>?>()
    private val submitCallbacks = mutableMapOf<String, LuaFunction<*>?>()

    /** 安全调用 Lua 回调，捕获任何异常防止崩溃 */
    private fun safeCall(cb: LuaFunction<*>?, vararg args: Any?) {
        cb?.let {
            try {
                @Suppress("UNCHECKED_CAST")
                (it as LuaFunction<Any>).call(*args)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Lua 回调异常: ${e.message}", e)
            }
        }
    }

    /**
     * 创建悬浮球
     *
     * 点击悬浮球时仅触发 onClick 回调，由 Lua 层决定是否显示面板及使用哪种模式。
     * 面板内容通过 WebUI 模板渲染（默认模板或插件自定义 HTML），
     * Lua 与 WebUI 通过 LXC.callLua / lxc-message 双向通信。
     *
     * @param x 初始 X 坐标（像素）
     * @param y 初始 Y 坐标（像素）
     * @param label 悬浮球标签文字
     * @param iconText 悬浮球图标文字（2个字符，如 "AI"）
     * @param onClick 点击悬浮球回调（参数：ballId）
     * @param onSubmit 提交输入回调（已废弃，改用 onFloatingPanelSubmit 全局函数）
     * @return 悬浮球 ID，失败返回 null
     */
    fun createBall(
        x: Int,
        y: Int,
        label: String,
        iconText: String,
        onClick: LuaFunction<*>?,
        onSubmit: LuaFunction<*>?
    ): String? {
        val id = UUID.randomUUID().toString().take(8)
        android.util.Log.d(TAG, "[$pluginId] createBall id=$id, x=$x, y=$y, label=\"$label\"")
        ballCallbacks[id] = onClick
        submitCallbacks[id] = onSubmit

        val success = FloatingManager.createBall(
            id = id,
            pluginId = pluginId,
            x = x,
            y = y,
            label = label,
            iconText = iconText,
            onBallClick = { ballId ->
                android.util.Log.d(TAG, "[$pluginId] ballClick id=$ballId")
                // 仅触发 Lua 回调，由 Lua 层决定展示内容
                safeCall(ballCallbacks[ballId], ballId)
            },
            onInputSubmit = { ballId, inputText ->
                // 保留兼容：如果 Lua 定义了 onSubmit 回调且不使用 WebUI 模式
                safeCall(submitCallbacks[ballId], ballId, inputText)
            }
        )
        return if (success) id else null
    }

    /** 移除悬浮球 */
    fun removeBall(id: String) {
        android.util.Log.d(TAG, "[$pluginId] removeBall id=$id")
        FloatingManager.removeBall(id)
        ballCallbacks.remove(id)
        submitCallbacks.remove(id)
    }

    /** 移除所有悬浮球 */
    fun removeAll() {
        android.util.Log.d(TAG, "[$pluginId] removeAll 数量=${ballCallbacks.size}")
        FloatingManager.removeAllBalls()
        ballCallbacks.clear()
        submitCallbacks.clear()
    }

    /** 更新悬浮球标签文字 */
    fun updateBall(id: String, label: String) {
        FloatingManager.updateBallLabel(id, label)
    }

    /**
     * 展开默认输入面板（使用内置 WebUI 模板 panel_default.html）
     *
     * 面板内容由 HTML 模板渲染，Lua 通过以下方式与面板交互：
     * - 面板 → Lua：JS 调用 LXC.callLua("onFloatingPanelSubmit", json)
     * - Lua → 面板：调用 plugin.floating.sendToWeb(id, json)
     *
     * @param id 悬浮球 ID
     * @param title 面板标题
     * @param hint 输入框提示文字
     */
    fun showPanel(id: String, title: String, hint: String) {
        android.util.Log.d(TAG, "[$pluginId] showPanel id=$id, title=\"$title\", hint=\"$hint\"")
        // 创建 WebUI 桥接器用于 JS ↔ Lua 通信
        val webUIBridge = PluginWebUIBridge(pluginId)
        val jsBridge = webUIBridge.JsApiBridge()
        FloatingManager.showPanelDefault(id, title, hint, jsBridge, "LXC")
    }

    /**
     * 以 WebUI 模式展开面板，加载插件 web/ 目录下的 HTML 页面
     *
     * @param id 悬浮球 ID
     * @param title 面板标题
     * @param page HTML 页面文件名（相对于插件 web/ 目录），如 "panel.html"
     * @return 是否成功加载
     */
    fun showPanelWebUI(id: String, title: String, page: String): Boolean {
        android.util.Log.d(TAG, "[$pluginId] showPanelWebUI id=$id, title=\"$title\", page=$page")
        // 查找当前插件的 web 目录
        val plugin = PluginManager.loadedPlugins.find { it.manifest.id == pluginId }
        val webDir = plugin?.let { File(it.directory, "web") }
        if (webDir == null || !webDir.exists()) {
            android.util.Log.w(TAG, "[$pluginId] showPanelWebUI 失败: webDir 不存在")
            return false
        }

        // 创建 WebUI 桥接器用于 JS 通信
        val webUIBridge = PluginWebUIBridge(pluginId)
        val jsBridge = webUIBridge.JsApiBridge()

        return FloatingManager.showPanelWebUI(id, title, webDir, page, jsBridge, "LXC")
    }

    /** 隐藏面板 */
    fun hidePanel(id: String) {
        android.util.Log.d(TAG, "[$pluginId] hidePanel id=$id")
        FloatingManager.hidePanel(id)
    }

    /**
     * 以自定义视图模式显示面板
     *
     * 允许 Lua 传入任意 View 作为面板内容，支持动态构建 UI 组件。
     * 在 Lua 中通过 LuaJava 创建 View（如 LinearLayout、Button、TextView 等），
     * 然后调用此方法将其显示在面板中。
     *
     * @param id 悬浮球 ID
     * @param title 面板标题
     * @param view 自定义 View（Lua 中通过 LuaJava 创建）
     */
    fun showPanelCustom(id: String, title: String, view: Any?) {
        android.util.Log.d(TAG, "[$pluginId] showPanelCustom id=$id, title=\"$title\", viewType=${view?.javaClass?.simpleName}")
        if (view is android.view.View) {
            FloatingManager.showPanelCustom(id, title, view)
        } else {
            android.util.Log.e(TAG, "showPanelCustom: view 不是有效的 View 类型: ${view?.javaClass?.name}")
        }
    }

    /** 隐藏悬浮球视图（保留面板功能，用于工具栏按钮触发场景） */
    fun hideBall(id: String) {
        FloatingManager.hideBall(id)
    }

    /** 显示悬浮球视图 */
    fun showBall(id: String) {
        FloatingManager.showBall(id)
    }

    /** 更新面板标题（用于流式进度等场景） */
    fun updatePanelTitle(id: String, title: String) {
        FloatingManager.updatePanelTitle(id, title)
    }

    /** 显示面板加载转圈动画 */
    fun showPanelLoading(id: String) = FloatingManager.showPanelLoading(id)

    /** 隐藏面板加载转圈动画 */
    fun hidePanelLoading(id: String) = FloatingManager.hidePanelLoading(id)

    /** 显示流式输出中的思考过程面板 */
    fun showReasoningOutput(id: String) = FloatingManager.showReasoningOutput(id)

    /** 追加思考过程内容 */
    fun appendReasoningContent(id: String, text: String) = FloatingManager.appendReasoningContent(id, text)

    /** 隐藏思考过程面板 */
    fun hideReasoningOutput(id: String) = FloatingManager.hideReasoningOutput(id)

    // ==================== 流式输出 ====================

    /** 显示面板流式输出区域，隐藏输入框 */
    fun showStreamOutput(id: String) = FloatingManager.showPanelStreamOutput(id)

    /** 追加流式内容到面板输出区域 */
    fun appendStreamContent(id: String, text: String) = FloatingManager.appendPanelStreamContent(id, text)

    /** 清空面板流式输出区域 */
    fun clearStreamOutput(id: String) = FloatingManager.clearPanelStreamOutput(id)

    /** 隐藏面板流式输出区域，恢复输入框 */
    fun hideStreamOutput(id: String) = FloatingManager.hidePanelStreamOutput(id)

    /** 设置面板调节大小手柄是否可见（Lua 可调用） */
    fun setResizeHandleEnabled(id: String, enabled: Boolean) = FloatingManager.setPanelResizeHandleEnabled(id, enabled)

    /** 设置面板初始宽度（Lua 可调用，dp 单位） */
    fun setPanelWidth(id: String, widthDp: Int) = FloatingManager.setPanelWidth(id, widthDp)

    /** 更新悬浮球背景颜色（Lua 可调用，格式 #RRGGBB 或 #AARRGGBB） */
    fun updateBallColor(id: String, color: String) = FloatingManager.updateBallColor(id, color)

    /** 更新悬浮球文字颜色（Lua 可调用） */
    fun updateBallTextColor(id: String, color: String) = FloatingManager.updateBallTextColor(id, color)

    /** 更新悬浮球大小（Lua 可调用，dp 单位） */
    fun updateBallSize(id: String, sizeDp: Int) = FloatingManager.updateBallSize(id, sizeDp)

    /** 更新悬浮球文字内容（Lua 可调用，不截断） */
    fun updateBallText(id: String, text: String) = FloatingManager.updateBallText(id, text)

    /** 注册悬浮球拖拽移动事件
     *  Lua 回调参数: (x: Int, y: Int) */
    fun setOnBallMoved(id: String, callback: Any?) {
        FloatingManager.setOnBallMoved(id) { x, y ->
            safeCall(callback as? LuaFunction<*>, x, y)
        }
    }

    /** 注册面板大小调节事件
     *  Lua 回调参数: (width: Int, height: Int) */
    fun setOnPanelResized(id: String, callback: Any?) {
        FloatingManager.setOnPanelResized(id) { w, h ->
            safeCall(callback as? LuaFunction<*>, w, h)
        }
    }

    /** 请求面板焦点 */
    fun requestFocus(id: String) {
        FloatingManager.requestPanelFocus(id)
    }

    /** 清除面板焦点 */
    fun clearFocus(id: String) {
        FloatingManager.clearPanelFocus(id)
    }

    /**
     * 向 WebUI 面板发送消息（宿主 → Web）
     * Web 端通过监听 lxc-message 自定义事件接收
     * 支持消息队列，WebView 未就绪时自动暂存
     *
     * @param id 悬浮球 ID
     * @param jsonMessage JSON 格式消息
     */
    fun sendToWeb(id: String, jsonMessage: String) {
        FloatingManager.sendToPanelWebView(id, jsonMessage)
    }

    /**
     * 向 WebUI 面板执行 JavaScript 代码
     *
     * @param id 悬浮球 ID
     * @param jsCode JavaScript 代码
     */
    fun evaluateJs(id: String, jsCode: String) {
        FloatingManager.evaluatePanelJs(id, jsCode)
    }

    /** 获取悬浮球数量 */
    fun getBallCount(): Int = FloatingManager.getBallCount()

    /** 获取所有悬浮球 ID */
    fun getBallIds(): List<String> = FloatingManager.getBallIds()
}