package com.luaforge.studio.lxclua.plugin.floating

import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.luaforge.studio.lxclua.ai.AIManager
import com.luaforge.studio.lxclua.plugin.PluginManager
import java.io.File

/**
 * 悬浮窗管理器
 *
 * 管理插件创建的悬浮球，负责通过 WindowManager 添加/移除悬浮窗视图。
 */
object FloatingManager {

    private const val TAG = "FloatingManager"

    private var windowManager: WindowManager? = null
    private val floatingBalls = mutableMapOf<String, FloatingBallData>()

    /** 悬浮窗类型：Android 8.0+ 用 TYPE_APPLICATION_OVERLAY，旧版本用 TYPE_PHONE */
    private val overlayType: Int
        @Suppress("DEPRECATION")
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    data class FloatingBallData(
        val id: String,
        val pluginId: String,
        val view: FloatingBallView,
        val panel: FloatingInputPanel,
        val params: WindowManager.LayoutParams
    )

    private fun getWindowManager(): WindowManager? {
        if (windowManager == null) {
            windowManager = PluginManager.appContext?.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
        }
        return windowManager
    }

    /** 创建悬浮球 */
    fun createBall(
        id: String,
        pluginId: String,
        x: Int,
        y: Int,
        label: String,
        iconText: String,
        onBallClick: ((String) -> Unit)?,
        onInputSubmit: ((String, String) -> Unit)?
    ): Boolean {
        val wm = getWindowManager() ?: run {
            Log.e(TAG, "[$id] createBall 失败: WindowManager 不可用")
            return false
        }
        if (floatingBalls.containsKey(id)) {
            Log.w(TAG, "[$id] createBall 失败: 悬浮球已存在")
            return false
        }

        val ctx = PluginManager.appContext ?: run {
            Log.e(TAG, "[$id] createBall 失败: appContext 不可用")
            return false
        }

        Log.d(TAG, "[$id] createBall pluginId=$pluginId, x=$x, y=$y, label=\"$label\", iconText=\"$iconText\"")

        // 创建悬浮球视图
        val ballView = FloatingBallView(ctx, id, label, iconText, onBallClick)

        // 创建输入面板
        val panel = FloatingInputPanel(ctx, id, onInputSubmit)
        // 停止按钮触发 AI 取消
        panel.onStop = { AIManager.cancelCurrentChat() }

        // 悬浮球布局参数
        val ballParams = WindowManager.LayoutParams().apply {
            type = overlayType
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            format = android.graphics.PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        ballView.setLayoutParams(ballParams)
        ballView.setFloatingPanel(panel)
        panel.setFloatingBall(ballView)

        wm.addView(ballView, ballParams)
        floatingBalls[id] = FloatingBallData(id, pluginId, ballView, panel, ballParams)

        Log.d(TAG, "[$id] createBall 成功, 当前悬浮球总数=${floatingBalls.size}")
        return true
    }

    /** 移除悬浮球 */
    fun removeBall(id: String) {
        val data = floatingBalls.remove(id) ?: return
        Log.d(TAG, "[$id] removeBall panelAttached=${data.panel.isAttached}")
        val wm = getWindowManager()
        try {
            if (data.panel.isAttached) {
                wm?.removeView(data.panel)
            }
            wm?.removeView(data.view)
            data.panel.destroy()
            Log.d(TAG, "[$id] removeBall 完成, 剩余悬浮球=${floatingBalls.size}")
        } catch (e: Exception) {
            Log.w(TAG, "[$id] removeBall 异常: ${e.message}")
        }
    }

    /** 移除所有悬浮球 */
    fun removeAllBalls() {
        Log.d(TAG, "removeAllBalls 数量=${floatingBalls.size}")
        floatingBalls.keys.toList().forEach { removeBall(it) }
    }

    /** 移除指定插件的所有悬浮球 */
    fun removeBallsByPlugin(pluginId: String) {
        val ids = floatingBalls.entries.filter { it.value.pluginId == pluginId }.map { it.key }
        Log.d(TAG, "removeBallsByPlugin pluginId=$pluginId, 匹配数量=${ids.size}")
        ids.forEach { removeBall(it) }
    }

    /** 更新面板标题（用于流式进度等场景） */
    fun updatePanelTitle(id: String, title: String) {
        floatingBalls[id]?.panel?.updateTitle(title)
    }

    /** 显示面板加载转圈动画 */
    fun showPanelLoading(id: String) {
        floatingBalls[id]?.panel?.showLoading()
    }

    /** 隐藏面板加载转圈动画 */
    fun hidePanelLoading(id: String) {
        floatingBalls[id]?.panel?.hideLoading()
    }

    /** 显示流式输出中的思考过程面板 */
    fun showReasoningOutput(id: String) {
        floatingBalls[id]?.panel?.showReasoningOutput()
    }

    /** 追加思考过程内容 */
    fun appendReasoningContent(id: String, text: String) {
        floatingBalls[id]?.panel?.appendReasoningContent(text)
    }

    /** 隐藏思考过程面板 */
    fun hideReasoningOutput(id: String) {
        floatingBalls[id]?.panel?.hideReasoningOutput()
    }

    // ==================== 流式输出 ====================

    /** 显示面板流式输出区域 */
    fun showPanelStreamOutput(id: String) {
        floatingBalls[id]?.panel?.showStreamOutput()
    }

    /** 追加流式内容到面板 */
    fun appendPanelStreamContent(id: String, text: String) {
        floatingBalls[id]?.panel?.appendStreamContent(text)
    }

    /** 清空面板流式输出 */
    fun clearPanelStreamOutput(id: String) {
        floatingBalls[id]?.panel?.clearStreamOutput()
    }

    /** 隐藏面板流式输出区域 */
    fun hidePanelStreamOutput(id: String) {
        floatingBalls[id]?.panel?.hideStreamOutput()
    }

    /** 设置面板调节手柄是否可见 */
    fun setPanelResizeHandleEnabled(id: String, enabled: Boolean) {
        floatingBalls[id]?.panel?.setResizeHandleEnabled(enabled)
    }

    /** 设置面板初始宽度（dp 单位） */
    fun setPanelWidth(id: String, widthDp: Int) {
        floatingBalls[id]?.panel?.setPanelWidthDp(widthDp)
    }

    /** 更新悬浮球文字 */
    fun updateBallLabel(id: String, label: String) {
        floatingBalls[id]?.view?.updateLabel(label)
    }

    /** 更新悬浮球背景颜色 */
    fun updateBallColor(id: String, color: String) {
        floatingBalls[id]?.view?.updateBallColor(color)
    }

    /** 更新悬浮球文字颜色 */
    fun updateBallTextColor(id: String, color: String) {
        floatingBalls[id]?.view?.updateBallTextColor(color)
    }

    /** 更新悬浮球大小（dp） */
    fun updateBallSize(id: String, sizeDp: Int) {
        floatingBalls[id]?.view?.updateBallSize(sizeDp)
    }

    /** 更新悬浮球文字内容 */
    fun updateBallText(id: String, text: String) {
        floatingBalls[id]?.view?.updateBallText(text)
    }

    /** 设置悬浮球拖拽移动回调 */
    fun setOnBallMoved(id: String, callback: ((Int, Int) -> Unit)?) {
        floatingBalls[id]?.view?.onBallMoved = callback
    }

    /** 设置面板大小调节回调 */
    fun setOnPanelResized(id: String, callback: ((Int, Int) -> Unit)?) {
        floatingBalls[id]?.panel?.onPanelResized = callback
    }

    /**
     * 以默认 WebUI 模板显示面板
     * 加载内置 panel_default.html，通过 lxc-message 传递 title 和 hint
     *
     * @param id 悬浮球 ID
     * @param title 面板标题
     * @param hint 输入框提示文字
     * @param jsBridge JS 桥接对象（PluginWebUIBridge.JsApiBridge）
     * @param bridgeName JS 桥接名称（默认 "LXC"）
     */
    fun showPanelDefault(
        id: String,
        title: String,
        hint: String,
        jsBridge: Any? = null,
        bridgeName: String = "LXC"
    ): Boolean {
        val data = floatingBalls[id] ?: run {
            Log.w(TAG, "[$id] showPanelDefault 失败: 悬浮球不存在")
            return false
        }
        val wm = getWindowManager() ?: run {
            Log.e(TAG, "[$id] showPanelDefault 失败: WindowManager 不可用")
            return false
        }
        Log.d(TAG, "[$id] showPanelDefault title=\"$title\", hint=\"$hint\", hasBridge=${jsBridge != null}")
        val result = data.panel.showDefaultWebUI(title, hint, jsBridge, bridgeName)
        if (result && !data.panel.isAttached) {
            val panelParams = createPanelParams(data)
            wm.addView(data.panel, panelParams)
            data.panel.isAttached = true
            Log.d(TAG, "[$id] showPanelDefault 面板已添加到 WindowManager")
        }
        return result
    }

    /** 隐藏输入面板 */
    fun hidePanel(id: String) {
        val data = floatingBalls[id] ?: return
        val wm = getWindowManager() ?: return
        Log.d(TAG, "[$id] hidePanel isAttached=${data.panel.isAttached}")
        data.panel.hide()
        if (data.panel.isAttached) {
            try { wm.removeView(data.panel) } catch (_: Exception) {}
            data.panel.isAttached = false
        }
    }

    /** 隐藏悬浮球视图（保留面板功能） */
    fun hideBall(id: String) {
        floatingBalls[id]?.view?.visibility = View.GONE
    }

    /** 显示悬浮球视图 */
    fun showBall(id: String) {
        floatingBalls[id]?.view?.visibility = View.VISIBLE
    }

    /** 获取悬浮球数量 */
    fun getBallCount(): Int = floatingBalls.size

    /** 获取所有悬浮球 ID */
    fun getBallIds(): List<String> = floatingBalls.keys.toList()

    /**
     * 以 WebUI 模式显示面板，加载插件 web/ 目录下的 HTML 页面
     *
     * @param id 悬浮球 ID
     * @param title 面板标题
     * @param webRootDir 插件 web 资源根目录
     * @param page HTML 页面文件名
     * @param jsBridge JS 桥接对象（可选）
     * @param bridgeName JS 桥接名称（默认 "LXC"）
     * @return 是否成功加载
     */
    fun showPanelWebUI(
        id: String,
        title: String,
        webRootDir: File,
        page: String,
        jsBridge: Any? = null,
        bridgeName: String = "LXC"
    ): Boolean {
        val data = floatingBalls[id] ?: run {
            Log.w(TAG, "[$id] showPanelWebUI 失败: 悬浮球不存在")
            return false
        }
        val wm = getWindowManager() ?: run {
            Log.e(TAG, "[$id] showPanelWebUI 失败: WindowManager 不可用")
            return false
        }
        Log.d(TAG, "[$id] showPanelWebUI title=\"$title\", page=$page, hasBridge=${jsBridge != null}")
        val result = data.panel.showWebUI(title, webRootDir, page, jsBridge, bridgeName)
        if (result && !data.panel.isAttached) {
            val panelParams = createPanelParams(data)
            wm.addView(data.panel, panelParams)
            data.panel.isAttached = true
            Log.d(TAG, "[$id] showPanelWebUI 面板已添加到 WindowManager")
        }
        return result
    }

    /**
     * 以自定义视图模式显示面板
     *
     * @param id 悬浮球 ID
     * @param title 面板标题
     * @param customView 自定义 View
     */
    fun showPanelCustom(id: String, title: String, customView: View) {
        val data = floatingBalls[id] ?: run {
            Log.w(TAG, "[$id] showPanelCustom 失败: 悬浮球不存在")
            return
        }
        val wm = getWindowManager() ?: run {
            Log.e(TAG, "[$id] showPanelCustom 失败: WindowManager 不可用")
            return
        }
        Log.d(TAG, "[$id] showPanelCustom title=\"$title\", viewType=${customView.javaClass.simpleName}")
        data.panel.showCustom(title, customView)
        if (!data.panel.isAttached) {
            val panelParams = createPanelParams(data)
            wm.addView(data.panel, panelParams)
            data.panel.isAttached = true
            Log.d(TAG, "[$id] showPanelCustom 面板已添加到 WindowManager")
        }
    }

    /** 请求面板焦点 */
    fun requestPanelFocus(id: String) {
        floatingBalls[id]?.panel?.requestFocusOnInput()
    }

    /** 清除面板焦点 */
    fun clearPanelFocus(id: String) {
        floatingBalls[id]?.panel?.clearPanelFocus()
    }

    /** 向面板 WebView 发送 JSON 消息（支持消息队列，WebView 未就绪时暂存） */
    fun sendToPanelWebView(id: String, jsonMessage: String) {
        floatingBalls[id]?.panel?.sendToPanelWebView(jsonMessage)
    }

    /** 获取面板 WebView 实例（WebUI 模式下可用，用于 evaluateJs 等操作） */
    fun getPanelWebView(id: String): android.webkit.WebView? {
        return floatingBalls[id]?.panel?.getWebView()
    }

    /** 向面板 WebView 执行 JavaScript 代码 */
    fun evaluatePanelJs(id: String, jsCode: String) {
        floatingBalls[id]?.panel?.getWebView()?.post {
            floatingBalls[id]?.panel?.getWebView()?.evaluateJavascript(jsCode, null)
        }
    }

    private fun createPanelParams(data: FloatingBallData): WindowManager.LayoutParams {
        val panel = data.panel
        return WindowManager.LayoutParams().apply {
            type = overlayType
            // 面板需要焦点，让输入框可以获得键盘输入
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            format = android.graphics.PixelFormat.TRANSLUCENT
            // 使用面板存储的尺寸（默认为 WRAP_CONTENT）
            width = panel.panelWidth.takeIf { it > 0 } ?: WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = data.params.x
            y = data.params.y + 80
        }
    }
}