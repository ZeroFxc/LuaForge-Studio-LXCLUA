package com.luaforge.studio.lxclua.plugin.floating

import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.PathShape
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.luaforge.studio.lxclua.ui.settings.SettingsData
import com.luaforge.studio.lxclua.ui.settings.SettingsManager
import java.io.File

/**
 * 悬浮球输入面板
 *
 * 点击悬浮球后展开的界面，支持三种模式：
 * - DEFAULT：默认提示输入框 + 发送按钮
 * - WEBUI：加载插件 web/ 目录下的 HTML 页面
 * - CUSTOM：用户通过 Lua 传入的任意 View
 *
 * 支持按住右下角拖拽调节面板大小
 */
class FloatingInputPanel(
    context: Context,
    private val ballId: String,
    private val onSubmit: ((String, String) -> Unit)?
) : LinearLayout(context) {

    /** 面板显示模式 */
    enum class PanelMode { DEFAULT, WEBUI, CUSTOM }

    private var currentMode: PanelMode = PanelMode.DEFAULT

    private val titleView: TextView
    private val loadingIndicator: ProgressBar
    private val contentContainer: FrameLayout

    // 默认模式组件
    private val inputView: EditText
    private val sendButton: Button
    private val cancelButton: Button
    private val stopButton: Button
    private val defaultContentLayout: LinearLayout

    // 流式输出组件
    private val streamOutputView: TextView
    private val streamOutputScroll: ScrollView
    private val streamLoadingIndicator: ProgressBar
    private val streamContainer: LinearLayout
    // 思考过程显示
    private val reasoningOutputView: TextView
    private val reasoningScroll: ScrollView

    // WebUI 模式组件
    private var webView: WebView? = null

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

    /** 停止按钮回调（用户点击停止生成） */
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

    /** 设置变化监听器，主题切换时自动更新面板颜色 */
    private val settingsListener: (SettingsData) -> Unit = { _ ->
        post { applyTheme() }
    }

    /** 监听系统配置变化（如夜间模式切换），确保 FOLLOW_SYSTEM 时面板颜色跟随系统 */
    private val configCallback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            post { applyTheme() }
        }
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

        // 标题行（含加载动画，可拖拽移动面板）
        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            // 标题栏可拖拽移动整个面板
            setOnTouchListener { _, event -> this@FloatingInputPanel.handleDragTouch(event) }
        }
        loadingIndicator = ProgressBar(context).apply {
            visibility = GONE
            isIndeterminate = true
            // 小尺寸转圈
            val size = dpToPx(16)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 0, dpToPx(6), 0)
            }
        }
        titleRow.addView(loadingIndicator)
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
            // 裁剪子视图到圆角边界，防止 WebView/EditText 内容溢出覆盖圆角边框
            clipToOutline = true
            clipChildren = true
            // 内容左右留白，避免紧贴边框
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
            dpToPx(180)
        ).apply {
            setMargins(0, dpToPx(4), 0, dpToPx(4))
        })

        // 初始化面板尺寸（与默认 layout 一致）
        panelWidth = dpToPx(260)
        panelHeight = dpToPx(180)

        // 默认内容布局
        defaultContentLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
        }

        // 输入框
        inputView = EditText(context).apply {
            textSize = 13f
            minLines = 2
            maxLines = 4
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            gravity = Gravity.TOP
        }
        defaultContentLayout.addView(inputView, LayoutParams(dpToPx(220), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dpToPx(4), 0, dpToPx(4))
        })

        // 流式输出区域容器（含加载动画，默认隐藏）
        streamContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = GONE
        }
        streamLoadingIndicator = ProgressBar(context).apply {
            visibility = GONE
            isIndeterminate = true
            val size = dpToPx(16)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dpToPx(4), 0, dpToPx(4))
            }
        }
        streamContainer.addView(streamLoadingIndicator)
        // 思考过程显示区（小面板，灰色斜体，默认隐藏）
        reasoningScroll = ScrollView(context).apply {
            visibility = GONE
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))
        }
        reasoningOutputView = TextView(context).apply {
            textSize = 11f
            gravity = Gravity.TOP or Gravity.START
            setTextColor(0xFF888888.toInt())
            setTypeface(null, android.graphics.Typeface.ITALIC)
        }
        reasoningScroll.addView(reasoningOutputView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        streamContainer.addView(reasoningScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(60)
        ).apply {
            setMargins(0, dpToPx(2), 0, dpToPx(4))
        })
        streamOutputScroll = ScrollView(context).apply {
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }
        streamOutputView = TextView(context).apply {
            textSize = 12f
            gravity = Gravity.TOP or Gravity.START
        }
        streamOutputScroll.addView(streamOutputView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        streamContainer.addView(streamOutputScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(100)
        ).apply {
            setMargins(0, dpToPx(4), 0, dpToPx(4))
        })
        defaultContentLayout.addView(streamContainer, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        contentContainer.addView(defaultContentLayout, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 按钮行
        val buttonRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }

        cancelButton = Button(context).apply {
            text = "关闭"
            textSize = 12f
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            setOnClickListener { hide() }
        }
        buttonRow.addView(cancelButton, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, dpToPx(8), 0) })

        sendButton = Button(context).apply {
            text = "发送"
            textSize = 12f
            setPadding(dpToPx(16), dpToPx(4), dpToPx(16), dpToPx(4))
            setOnClickListener {
                val text = inputView.text.toString().trim()
                if (text.isNotEmpty()) {
                    onSubmit?.invoke(ballId, text)
                    inputView.text.clear()
                    // 不再自动关闭面板，让用户可以看到 AI 响应
                }
            }
        }
        buttonRow.addView(sendButton, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, dpToPx(8), 0) })

        // 停止按钮（流式输出时显示，用于手动终止 AI 生成）
        stopButton = Button(context).apply {
            text = "停止"
            textSize = 12f
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            visibility = GONE
            setOnClickListener { onStop?.invoke() }
        }
        buttonRow.addView(stopButton, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        addView(buttonRow, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // 应用主题颜色
        applyTheme()

        // 注册设置变化监听，主题切换时自动更新面板颜色
        SettingsManager.addListener(settingsListener)

        // 注册系统配置变化监听（如夜间模式切换），确保 FOLLOW_SYSTEM 时面板跟随系统
        context.applicationContext.registerComponentCallbacks(configCallback)

        visibility = GONE
    }

    /**
     * 应用当前主题颜色到所有组件
     * 每次面板显示前调用，确保跟随主题切换
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

        // 输入框
        inputView.setTextColor(c.contentText)
        inputView.setHintTextColor(c.hintText)
        inputView.setBackgroundColor(c.contentBackground)

        // 关闭按钮
        cancelButton.background = createRoundedBg(c.cancelButtonBg, dpToPx(6).toFloat())
        cancelButton.setTextColor(c.cancelButtonText)

        // 发送按钮
        sendButton.background = createRoundedBg(c.sendButtonBg, dpToPx(6).toFloat())
        sendButton.setTextColor(c.sendButtonText)

        // 停止按钮
        stopButton.background = createRoundedBg(c.stopButtonBg, dpToPx(6).toFloat())
        stopButton.setTextColor(c.stopButtonText)

        // 流式输出区域
        streamOutputView.setTextColor(c.contentText)
        streamOutputView.setBackgroundColor(c.contentBackground)
        streamOutputScroll.setBackgroundColor(c.contentBackground)

        // 手柄
        handleBgDrawable.setColor(c.resizeHandle)

        // 加载动画
        loadingIndicator.indeterminateTintList = android.content.res.ColorStateList.valueOf(c.loadingIndicator)
        streamLoadingIndicator.indeterminateTintList = android.content.res.ColorStateList.valueOf(c.loadingIndicator)
    }

    /**
     * 销毁面板，清理资源
     * 移除 SettingsManager 监听器，防止内存泄漏
     */
    fun destroy() {
        SettingsManager.removeListener(settingsListener)
        context.applicationContext.unregisterComponentCallbacks(configCallback)
    }

    // ==================== 调节大小 ====================

    /**
     * 处理调节大小手柄的触摸事件
     */
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

    /**
     * 应用面板尺寸，更新 WindowManager.LayoutParams 和内容容器大小
     */
    private fun applyPanelSize(width: Int, height: Int) {
        panelWidth = width
        panelHeight = height

        // 更新内容容器尺寸（宽度保持 MATCH_PARENT，由父布局 padding 控制留白）
        val contentWrapper = contentContainer.parent as? ViewGroup
        (contentWrapper?.layoutParams as? LinearLayout.LayoutParams)?.apply {
            this.height = height
        }
        contentWrapper?.requestLayout()

        // 更新 WindowManager.LayoutParams
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

    /** 显示面板（默认模式） */
    fun show(title: String, hint: String) {
        titleView.text = title
        inputView.hint = hint
        inputView.text.clear()
        switchToMode(PanelMode.DEFAULT)
        visibility = VISIBLE
        // 请求焦点，让输入框可以立即输入
        inputView.post {
            inputView.requestFocus()
        }
    }

    /** 隐藏面板 */
    fun hide() {
        visibility = GONE
        // 隐藏时清理 WebView
        if (currentMode == PanelMode.WEBUI) {
            webView?.stopLoading()
        }
    }

    /** 更新面板标题（用于流式进度等场景，线程安全） */
    fun updateTitle(title: String) {
        post {
            titleView.text = title
        }
    }

    /** 显示加载转圈动画 */
    fun showLoading() {
        post { loadingIndicator.visibility = VISIBLE }
    }

    /** 隐藏加载转圈动画 */
    fun hideLoading() {
        post { loadingIndicator.visibility = GONE }
    }

    /** 显示停止按钮 */
    fun showStopButton() {
        post { stopButton.visibility = VISIBLE }
    }

    /** 隐藏停止按钮 */
    fun hideStopButton() {
        post { stopButton.visibility = GONE }
    }

    // ==================== 流式输出控制 ====================

    /** 显示流式输出区域，隐藏输入框，并显示加载转圈 */
    fun showStreamOutput() {
        post {
            inputView.visibility = GONE
            streamContainer.visibility = VISIBLE
            streamOutputView.text = ""
            streamLoadingIndicator.visibility = VISIBLE
            stopButton.visibility = VISIBLE
        }
    }

    /** 追加流式内容到输出区域，首次收到数据时自动隐藏加载转圈 */
    fun appendStreamContent(text: String) {
        post {
            // 首次收到内容时隐藏加载动画
            if (streamLoadingIndicator.visibility == VISIBLE) {
                streamLoadingIndicator.visibility = GONE
            }
            streamOutputView.append(text)
            // 自动滚动到底部
            streamOutputScroll.post {
                streamOutputScroll.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    /** 清空流式输出区域 */
    fun clearStreamOutput() {
        post {
            streamOutputView.text = ""
        }
    }

    /** 隐藏流式输出区域，恢复输入框 */
    fun hideStreamOutput() {
        post {
            streamContainer.visibility = GONE
            streamLoadingIndicator.visibility = GONE
            reasoningScroll.visibility = GONE
            reasoningOutputView.text = ""
            stopButton.visibility = GONE
            inputView.visibility = VISIBLE
        }
    }

    // ==================== 思考过程显示 ====================

    /** 显示思考过程面板 */
    fun showReasoningOutput() {
        post {
            reasoningScroll.visibility = VISIBLE
            reasoningOutputView.text = ""
        }
    }

    /** 追加思考过程内容 */
    fun appendReasoningContent(text: String) {
        post {
            reasoningOutputView.append(text)
            reasoningScroll.post {
                reasoningScroll.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    /** 隐藏思考过程面板 */
    fun hideReasoningOutput() {
        post {
            reasoningScroll.visibility = GONE
            reasoningOutputView.text = ""
        }
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

    // ==================== WebUI 模式 ====================

    /**
     * 以 WebUI 模式显示面板，加载插件 web/ 目录下的 HTML 页面
     *
     * @param title 面板标题
     * @param webRootDir 插件的 web 资源根目录（插件目录/web/）
     * @param page HTML 页面文件名，如 "index.html"
     * @param jsBridge 可选 JS 桥接对象（如 PluginWebUIBridge.JsApiBridge）
     * @param bridgeName JS 桥接对象在 window 上的名字，如 "LXC"
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
        if (!pageFile.exists()) return false

        titleView.text = title
        switchToMode(PanelMode.WEBUI)

        // 创建 WebView
        if (webView == null) {
            webView = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // 透明背景，避免 WebView 白色底色超出圆角
                setBackgroundColor(Color.TRANSPARENT)
                // 防止内容溢出到面板框架外
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
                    // 禁用缩放，防止用户手势放大导致内容溢出
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)
                }
                webViewClient = object : WebViewClient() {}
                webChromeClient = object : WebChromeClient() {}
            }
        } else {
            // 重用已有 WebView
            (webView!!.parent as? ViewGroup)?.removeView(webView)
        }

        // 注入 JS 桥接
        if (jsBridge != null) {
            webView!!.addJavascriptInterface(jsBridge, bridgeName)
        }

        contentContainer.addView(webView)
        webView!!.loadUrl("file://${pageFile.absolutePath}")
        visibility = VISIBLE

        return true
    }

    // ==================== 自定义视图模式 ====================

    /**
     * 以自定义视图模式显示面板
     *
     * @param title 面板标题
     * @param customView 自定义 View，由 Lua 端创建
     */
    fun showCustom(title: String, customView: View) {
        titleView.text = title
        switchToMode(PanelMode.CUSTOM)

        // 移除旧视图
        (customView.parent as? ViewGroup)?.removeView(customView)
        contentContainer.addView(customView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        visibility = VISIBLE
    }

    // ==================== 焦点控制 ====================

    /** 请求面板焦点（让输入框获得键盘焦点） */
    fun requestFocusOnInput() {
        when (currentMode) {
            PanelMode.DEFAULT -> {
                inputView.post {
                    inputView.requestFocus()
                }
            }
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
            PanelMode.DEFAULT -> inputView.clearFocus()
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
                    // 同步更新悬浮球位置（球在面板上方 60dp 处）
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
        currentMode = mode

        // 清空内容容器
        contentContainer.removeAllViews()

        when (mode) {
            PanelMode.DEFAULT -> {
                // 恢复默认输入布局
                contentContainer.addView(defaultContentLayout, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
                sendButton.visibility = VISIBLE
            }
            PanelMode.WEBUI -> {
                // 隐藏发送按钮，WebUI 模式下由 HTML 自己处理交互
                sendButton.visibility = GONE
                // WebView 由 showWebUI 添加
            }
            PanelMode.CUSTOM -> {
                // 模式切换时可隐藏发送按钮
                sendButton.visibility = GONE
                // 自定义视图由 showCustom 添加
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /** 创建圆角背景 Drawable */
    private fun createRoundedBg(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            setCornerRadius(radius)
        }
    }
}