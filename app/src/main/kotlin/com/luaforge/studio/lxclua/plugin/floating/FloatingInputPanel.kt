package com.luaforge.studio.lxclua.plugin.floating

import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.luaforge.studio.lxclua.ui.settings.SettingsData
import com.luaforge.studio.lxclua.ui.settings.SettingsManager
import java.io.File

/**
 * 悬浮球输入面板
 *
 * 点击悬浮球后展开的界面，支持两种模式：
 * - WEBUI：加载 HTML 页面（默认模板或插件自定义页面）
 * - CUSTOM：用户通过 Lua 传入的任意 View
 *
 * 所有面板内容（输入框、按钮、流式输出、思考过程、加载动画）均由 WebUI 模板渲染，
 * 宿主通过 sendToPanelWebView() 发送 JSON 消息控制面板状态。
 *
 * 支持按住右下角拖拽调节面板大小，支持标题栏拖拽移动面板。
 */
class FloatingInputPanel(
    context: Context,
    private val ballId: String,
    private val onSubmit: ((String, String) -> Unit)?
) : LinearLayout(context) {

    companion object {
        private const val TAG = "FloatingPanel"
    }

    /** 面板显示模式 */
    enum class PanelMode { WEBUI, CUSTOM }

    private var currentMode: PanelMode = PanelMode.WEBUI

    private val titleView: TextView
    private val contentContainer: FrameLayout

    // WebView（WebUI 模式使用，懒加载）
    private var webView: WebView? = null
    /** 已加载的 URL，用于判断重新打开时是否需要 reload */
    private var loadedUrl: String? = null

    // 调节大小手柄
    private val resizeHandle: View
    private var isResizing = false
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var resizeStartWidth = 0
    private var resizeStartHeight = 0

    /** 面板是否已添加到 WindowManager */
    var isAttached: Boolean = false
        get
        set

    /** 面板当前宽度（px），首次显示时使用默认值 */
    internal var panelWidth = 0
    /** 面板当前高度（px），首次显示时使用默认值 */
    internal var panelHeight = 0

    /** 面板大小调节回调 (width: Int, height: Int) */
    var onPanelResized: ((Int, Int) -> Unit)? = null

    /** 停止按钮回调（用户点击停止生成，由 Lua 层通过 onFloatingPanelStop 处理） */
    var onStop: (() -> Unit)? = null

    /** 关联的悬浮球，用于拖拽时同步位置 */
    private var floatingBallView: FloatingBallView? = null

    /** 面板拖拽相关 */
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartPanelX = 0
    private var dragStartPanelY = 0
    private val dragThreshold = 10f

    /** 最小尺寸限制（dp） */
    private val minWidthDp = 200
    private val minHeightDp = 100

    /** 面板背景 drawable，用于动态更新颜色 */
    private val panelBgDrawable: GradientDrawable
    /** 内容容器背景 drawable */
    private val contentBgDrawable: GradientDrawable
    /** 手柄背景 drawable */
    private val handleBgDrawable: GradientDrawable

    /** WebView 就绪前暂存待发送消息 */
    private val pendingMessages = mutableListOf<String>()

    /** 设置变化监听器，主题切换时自动更新面板颜色 */
    private val settingsListener: (SettingsData) -> Unit = { _ ->
        post { applyTheme() }
    }

    /** 监听系统配置变化（如夜间模式切换），确保 FOLLOW_SYSTEM 时面板颜色跟随系统 */
    private val configCallback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            post { applyTheme() }
        }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() {}
    }

    init {
        val cornerRadius = dpToPx(12).toFloat()

        orientation = VERTICAL
        // 圆角背景 + 边框（颜色在 applyTheme 中设置）
        panelBgDrawable = GradientDrawable().apply {
            setCornerRadius(cornerRadius)
        }
        background = panelBgDrawable
        setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        gravity = Gravity.CENTER_HORIZONTAL
        clipToPadding = false

        // 标题行（可拖拽移动面板）
        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            // 标题栏可拖拽移动整个面板
            setOnTouchListener { _, event -> this@FloatingInputPanel.handleDragTouch(event) }
        }
        titleView = TextView(context).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }
        titleRow.addView(titleView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        addView(titleRow, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // 内容容器
        val contentWrapper = FrameLayout(context)
        contentBgDrawable = GradientDrawable().apply {
            setCornerRadius(cornerRadius)
        }
        contentContainer = FrameLayout(context).apply {
            background = contentBgDrawable
            // 裁剪子视图到圆角边界，防止 WebView 内容溢出覆盖圆角边框
            clipToOutline = true
            clipChildren = true
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        }
        contentWrapper.addView(contentContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 调节大小手柄
        handleBgDrawable = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setCornerRadius(dpToPx(4).toFloat())
        }
        resizeHandle = View(context).apply {
            background = handleBgDrawable
            layoutParams = FrameLayout.LayoutParams(dpToPx(20), dpToPx(20)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dpToPx(2), dpToPx(2))
            }
            setOnTouchListener { _, event -> handleResizeTouch(event) }
        }
        contentWrapper.addView(resizeHandle)

        addView(contentWrapper, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(200)
        ).apply {
            setMargins(0, dpToPx(4), 0, dpToPx(4))
        })

        // 初始化面板尺寸
        panelWidth = dpToPx(280)
        panelHeight = dpToPx(200)

        // 应用主题颜色
        applyTheme()

        // 注册设置变化监听
        SettingsManager.addListener(settingsListener)

        // 注册系统配置变化监听
        context.applicationContext.registerComponentCallbacks(configCallback)

        visibility = GONE
    }

    /**
     * 应用当前主题颜色到所有组件
     */
    fun applyTheme() {
        val c = FloatingPanelColors

        // 面板背景
        panelBgDrawable.setColor(c.panelBackground)
        panelBgDrawable.setStroke(dpToPx(1), c.panelBorder)

        // 标题
        titleView.setTextColor(c.panelTitleText)

        // 内容容器
        contentBgDrawable.setColor(c.contentBackground)

        // 手柄
        handleBgDrawable.setColor(c.resizeHandle)
    }

    /**
     * 销毁面板，清理资源
     */
    fun destroy() {
        Log.d(TAG, "[$ballId] destroy WebView=${webView != null}")
        loadedUrl = null
        SettingsManager.removeListener(settingsListener)
        context.applicationContext.unregisterComponentCallbacks(configCallback)
        webView?.stopLoading()
        webView?.destroy()
        webView = null
    }

    // ==================== 调节大小 ====================

    private fun handleResizeTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isResizing = true
                resizeStartX = event.rawX
                resizeStartY = event.rawY
                resizeStartWidth = panelWidth
                resizeStartHeight = panelHeight
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isResizing) return false
                val dx = (event.rawX - resizeStartX).toInt()
                val dy = (event.rawY - resizeStartY).toInt()
                val newWidth = (resizeStartWidth + dx).coerceAtLeast(dpToPx(minWidthDp))
                val newHeight = (resizeStartHeight + dy).coerceAtLeast(dpToPx(minHeightDp))
                applyPanelSize(newWidth, newHeight)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isResizing = false
                onPanelResized?.invoke(panelWidth, panelHeight)
                return true
            }
        }
        return false
    }

    private fun applyPanelSize(width: Int, height: Int) {
        panelWidth = width
        panelHeight = height

        val contentWrapper = contentContainer.parent as? ViewGroup
        (contentWrapper?.layoutParams as? LinearLayout.LayoutParams)?.apply {
            this.height = height
        }
        contentWrapper?.requestLayout()

        if (isAttached) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val params = layoutParams as? WindowManager.LayoutParams
            if (params != null && wm != null) {
                params.width = width
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                wm.updateViewLayout(this, params)
            }
        }
    }

    // ==================== 显示/隐藏 ====================

    /**
     * 以默认 WebUI 模板显示面板
     * 加载内置的 panel_default.html，通过 lxc-message 传递 title 和 hint
     *
     * @param title 面板标题
     * @param hint 输入框提示文字
     * @param jsBridge JS 桥接对象（PluginWebUIBridge.JsApiBridge）
     * @param bridgeName JS 桥接名称（默认 "LXC"）
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun showDefaultWebUI(
        title: String,
        hint: String,
        jsBridge: Any? = null,
        bridgeName: String = "LXC"
    ): Boolean {
        Log.d(TAG, "[$ballId] showDefaultWebUI title=\"$title\", hint=\"$hint\", hasBridge=${jsBridge != null}, bridgeName=$bridgeName")
        titleView.text = title
        switchToMode(PanelMode.WEBUI)
        visibility = VISIBLE

        // 如果 WebView 已存在且加载的是同一页面，直接显示，不重新加载
        val targetUrl = "file:///android_asset/html/panel_default.html"
        if (webView != null && loadedUrl == targetUrl) {
            Log.d(TAG, "[$ballId] 复用已加载的默认模板, 不重新加载")
            contentContainer.addView(webView)
            flushPendingMessages()
            return true
        }

        // 创建或重用 WebView
        if (webView == null) {
            Log.d(TAG, "[$ballId] 创建新的 WebView")
            webView = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.TRANSPARENT)
                overScrollMode = View.OVER_SCROLL_NEVER
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)
                }
            }
        } else {
            Log.d(TAG, "[$ballId] 复用现有 WebView")
            (webView!!.parent as? ViewGroup)?.removeView(webView)
        }

        // 注入 JS 桥接
        if (jsBridge != null) {
            Log.d(TAG, "[$ballId] 注入 JS 桥接: $bridgeName")
            webView!!.removeJavascriptInterface(bridgeName)
            webView!!.addJavascriptInterface(jsBridge, bridgeName)
        } else {
            Log.w(TAG, "[$ballId] 未注入 JS 桥接，LXC.callLua 将不可用")
        }

        // 页面加载完成后发送初始化消息
        webView!!.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "[$ballId] 默认模板加载完成 url=$url, 待发送消息=${pendingMessages.size}")
                val initMsg = """{"type":"init","title":"${escapeJson(title)}","hint":"${escapeJson(hint)}"}"""
                sendToPanelWebView(initMsg)
                flushPendingMessages()
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                Log.e(TAG, "[$ballId] 默认模板加载失败! errorCode=$errorCode, desc=$description, url=$failingUrl")
            }
        }
        webView!!.webChromeClient = object : WebChromeClient() {}

        contentContainer.addView(webView)
        Log.d(TAG, "[$ballId] 开始加载默认模板: $targetUrl")
        loadedUrl = targetUrl
        webView!!.loadUrl(targetUrl)

        return true
    }

    /**
     * 以 WebUI 模式显示面板，加载插件 web/ 目录下的 HTML 页面
     *
     * @param title 面板标题
     * @param webRootDir 插件的 web 资源根目录
     * @param page HTML 页面文件名
     * @param jsBridge JS 桥接对象
     * @param bridgeName JS 桥接名称
     * @return 是否成功加载
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun showWebUI(
        title: String,
        webRootDir: File,
        page: String,
        jsBridge: Any? = null,
        bridgeName: String = "LXC"
    ): Boolean {
        val pageFile = File(webRootDir, page)
        if (!pageFile.exists()) {
            Log.e(TAG, "[$ballId] showWebUI 文件不存在: ${pageFile.absolutePath}")
            return false
        }
        Log.d(TAG, "[$ballId] showWebUI title=\"$title\", page=$page, path=${pageFile.absolutePath}, hasBridge=${jsBridge != null}")

        titleView.text = title
        switchToMode(PanelMode.WEBUI)

        // 如果 WebView 已存在且加载的是同一页面，直接显示，不重新加载
        val targetUrl = "file://${pageFile.absolutePath}"
        if (webView != null && loadedUrl == targetUrl) {
            Log.d(TAG, "[$ballId] 复用已加载的自定义模板, 不重新加载")
            contentContainer.addView(webView)
            flushPendingMessages()
            visibility = VISIBLE
            return true
        }

        if (webView == null) {
            Log.d(TAG, "[$ballId] 创建新的 WebView")
            webView = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.TRANSPARENT)
                overScrollMode = View.OVER_SCROLL_NEVER
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)
                }
            }
        } else {
            Log.d(TAG, "[$ballId] 复用现有 WebView")
            (webView!!.parent as? ViewGroup)?.removeView(webView)
        }

        if (jsBridge != null) {
            Log.d(TAG, "[$ballId] 注入 JS 桥接: $bridgeName")
            webView!!.removeJavascriptInterface(bridgeName)
            webView!!.addJavascriptInterface(jsBridge, bridgeName)
        } else {
            Log.w(TAG, "[$ballId] 未注入 JS 桥接，LXC.callLua 将不可用")
        }

        // 自定义 WebUI 页面加载完成后发送积压消息
        webView!!.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "[$ballId] 自定义模板加载完成 url=$url, 待发送消息=${pendingMessages.size}")
                flushPendingMessages()
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                Log.e(TAG, "[$ballId] 自定义模板加载失败! errorCode=$errorCode, desc=$description, url=$failingUrl")
            }
        }
        webView!!.webChromeClient = object : WebChromeClient() {}

        contentContainer.addView(webView)
        Log.d(TAG, "[$ballId] 开始加载自定义模板: $targetUrl")
        loadedUrl = targetUrl
        webView!!.loadUrl(targetUrl)
        visibility = VISIBLE

        return true
    }

    /** 隐藏面板 */
    fun hide() {
        Log.d(TAG, "[$ballId] hide mode=$currentMode")
        visibility = GONE
        if (currentMode == PanelMode.WEBUI) {
            webView?.stopLoading()
        }
    }

    /** 更新面板标题（用于流式进度等场景，线程安全） */
    fun updateTitle(title: String) {
        post {
            titleView.text = title
            // 同步通知 WebView 更新标题显示
            sendToPanelWebView("""{"type":"title","text":"${escapeJson(title)}"}""")
        }
    }

    // ==================== WebView 消息发送 ====================

    /**
     * 向面板 WebView 发送 JSON 消息
     * 如果 WebView 尚未就绪，消息会暂存到队列中
     *
     * @param jsonMessage JSON 格式消息
     */
    fun sendToPanelWebView(jsonMessage: String) {
        val wv = webView
        if (wv != null) {
            val msgType = extractMsgType(jsonMessage)
            Log.v(TAG, "[$ballId] sendToWeb type=$msgType")
            wv.post {
                wv.evaluateJavascript(
                    """window.dispatchEvent(new CustomEvent('lxc-message', {detail: $jsonMessage}));""",
                    null
                )
            }
        } else {
            Log.v(TAG, "[$ballId] sendToWeb 暂存(WebView未就绪), 队列长度=${pendingMessages.size + 1}")
            pendingMessages.add(jsonMessage)
        }
    }

    /** 发送积压的待处理消息 */
    private fun flushPendingMessages() {
        if (pendingMessages.isEmpty()) return
        val wv = webView ?: return
        val count = pendingMessages.size
        Log.d(TAG, "[$ballId] flushPendingMessages 发送 $count 条积压消息")
        val messages = pendingMessages.toList()
        pendingMessages.clear()
        wv.post {
            for (msg in messages) {
                wv.evaluateJavascript(
                    """window.dispatchEvent(new CustomEvent('lxc-message', {detail: $msg}));""",
                    null
                )
            }
        }
    }

    // ==================== 流式输出控制 ====================

    /** 显示流式输出区域，隐藏输入框 */
    fun showStreamOutput() {
        sendToPanelWebView("""{"type":"stream","action":"show"}""")
    }

    /** 追加流式内容到输出区域 */
    fun appendStreamContent(text: String) {
        sendToPanelWebView("""{"type":"stream","action":"append","text":${toJsonString(text)}}""")
    }

    /** 清空流式输出区域 */
    fun clearStreamOutput() {
        sendToPanelWebView("""{"type":"stream","action":"clear"}""")
    }

    /** 隐藏流式输出区域，恢复输入框 */
    fun hideStreamOutput() {
        sendToPanelWebView("""{"type":"stream","action":"hide"}""")
    }

    // ==================== 思考过程显示 ====================

    /** 显示思考过程面板 */
    fun showReasoningOutput() {
        sendToPanelWebView("""{"type":"reasoning","action":"show"}""")
    }

    /** 追加思考过程内容 */
    fun appendReasoningContent(text: String) {
        sendToPanelWebView("""{"type":"reasoning","action":"append","text":${toJsonString(text)}}""")
    }

    /** 隐藏思考过程面板 */
    fun hideReasoningOutput() {
        sendToPanelWebView("""{"type":"reasoning","action":"hide"}""")
    }

    // ==================== 加载动画 ====================

    /** 显示加载转圈动画 */
    fun showLoading() {
        sendToPanelWebView("""{"type":"loading","show":true}""")
    }

    /** 隐藏加载转圈动画 */
    fun hideLoading() {
        sendToPanelWebView("""{"type":"loading","show":false}""")
    }

    // ==================== 停止按钮 ====================

    /** 显示停止按钮 */
    fun showStopButton() {
        sendToPanelWebView("""{"type":"stop","show":true}""")
    }

    /** 隐藏停止按钮 */
    fun hideStopButton() {
        sendToPanelWebView("""{"type":"stop","show":false}""")
    }

    // ==================== 调节大小手柄 ====================

    /** 设置调节大小手柄是否可见 */
    fun setResizeHandleEnabled(enabled: Boolean) {
        post { resizeHandle.visibility = if (enabled) VISIBLE else GONE }
    }

    /** 设置面板初始宽度（dp 单位），在面板显示前调用 */
    fun setPanelWidthDp(widthDp: Int) {
        panelWidth = dpToPx(widthDp)
    }

    // ==================== 自定义视图模式 ====================

    /**
     * 以自定义视图模式显示面板
     *
     * @param title 面板标题
     * @param customView 自定义 View，由 Lua 端创建
     */
    fun showCustom(title: String, customView: View) {
        Log.d(TAG, "[$ballId] showCustom title=\"$title\", viewType=${customView.javaClass.simpleName}")
        titleView.text = title
        switchToMode(PanelMode.CUSTOM)

        (customView.parent as? ViewGroup)?.removeView(customView)
        contentContainer.addView(customView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        visibility = VISIBLE
    }

    // ==================== 焦点控制 ====================

    /** 请求面板焦点 */
    fun requestFocusOnInput() {
        when (currentMode) {
            PanelMode.WEBUI -> {
                webView?.post {
                    webView?.requestFocus()
                }
            }
            PanelMode.CUSTOM -> {
                // 自定义视图自行处理焦点
            }
        }
    }

    /** 清除面板焦点 */
    fun clearPanelFocus() {
        when (currentMode) {
            PanelMode.WEBUI -> webView?.clearFocus()
            PanelMode.CUSTOM -> contentContainer.getChildAt(0)?.clearFocus()
        }
    }

    /** 获取当前模式 */
    fun getCurrentMode(): PanelMode = currentMode

    /** 获取 WebView 实例（WebUI 模式下可用） */
    fun getWebView(): WebView? = webView

    // ==================== 位置更新 ====================

    /** 更新面板位置 */
    fun updatePosition(x: Int, y: Int) {
        if (isAttached) {
            val ctx = context
            val params = layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.x = x
                params.y = y + dpToPx(60)
                (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .updateViewLayout(this, params)
            }
        }
    }

    /** 关联悬浮球，使面板拖拽时可同步更新悬浮球位置 */
    fun setFloatingBall(ball: FloatingBallView) {
        this.floatingBallView = ball
    }

    /** 处理面板拖拽触摸事件 */
    private fun handleDragTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val params = layoutParams as? WindowManager.LayoutParams ?: return false
                isDragging = false
                dragStartX = event.rawX
                dragStartY = event.rawY
                dragStartPanelX = params.x
                dragStartPanelY = params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragStartX
                val dy = event.rawY - dragStartY
                if (kotlin.math.abs(dx) > dragThreshold || kotlin.math.abs(dy) > dragThreshold) {
                    isDragging = true
                }
                if (isDragging) {
                    val params = layoutParams as? WindowManager.LayoutParams ?: return true
                    val newX = dragStartPanelX + dx.toInt()
                    val newY = dragStartPanelY + dy.toInt()
                    params.x = newX
                    params.y = newY
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .updateViewLayout(this, params)
                    floatingBallView?.moveTo(newX, newY - dpToPx(60))
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                return isDragging
            }
        }
        return false
    }

    // ==================== 内部方法 ====================

    /** 切换面板模式 */
    private fun switchToMode(mode: PanelMode) {
        if (currentMode == mode && mode != PanelMode.WEBUI) return
        Log.d(TAG, "[$ballId] switchToMode $currentMode -> $mode")
        currentMode = mode
        contentContainer.removeAllViews()
        // WebView / CustomView 由对应方法添加
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /** JSON 字符串转义 */
    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /** 将字符串转为 JSON 字符串值（带引号） */
    private fun toJsonString(s: String): String {
        return "\"${escapeJson(s)}\""
    }

    /** 从 JSON 消息中提取 type 字段用于日志 */
    private fun extractMsgType(json: String): String {
        val idx = json.indexOf("\"type\"")
        if (idx < 0) return "?"
        val colon = json.indexOf(":", idx + 6)
        if (colon < 0) return "?"
        val start = json.indexOf("\"", colon + 1)
        if (start < 0) return "?"
        val end = json.indexOf("\"", start + 1)
        return if (end > start) json.substring(start + 1, end) else "?"
    }
}