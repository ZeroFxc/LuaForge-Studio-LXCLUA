package com.luaforge.studio.lxclua.ui.editor.bridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import com.luaforge.studio.lxclua.plugin.api.callbacks.IPluginEventListener
import io.github.rosemoe.sora.R
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.File

/**
 * 自定义文本操作浮动窗口，支持插件动态注册按钮和自定义样式
 *
 * 功能：
 * - 插件可动态注册/注销自定义按钮
 * - 支持内置图标常量（ICON_*）和自定义图片路径
 * - 自定义图片自动裁剪到目标尺寸
 * - 圆角半径和图标大小可通过设置调整
 * - 按钮点击时触发 onTextActionWindowButtonClick 事件
 * - 窗口显示时触发 onTextActionWindowShown 事件
 * - 插件卸载时自动清除该插件的所有按钮
 */
class LuaTextActionWindow(editor: CodeEditor) : EditorTextActionWindow(editor) {

    // ==================== 图标常量表 ====================

    companion object {
        /**
         * 内置图标常量（key → drawable 资源 ID）
         * 插件可通过 plugin.editor.getTextActionIcons() 获取这些常量
         */
        val ICON_MAP: Map<String, Int> = mapOf(
            // 基础操作
            "SELECT_ALL" to R.drawable.round_select_all_20,
            "COPY" to R.drawable.round_content_copy_20,
            "PASTE" to R.drawable.round_content_paste_20,
            "CUT" to R.drawable.round_content_cut_20,
            "LONG_SELECT" to R.drawable.editor_text_select_start,
            // 撤销/重做
            "UNDO" to R.drawable.round_undo_20,
            "REDO" to R.drawable.round_redo_20,
            // 搜索/替换
            "SEARCH" to R.drawable.round_search_20,
            // 编辑
            "DELETE" to R.drawable.round_delete_20,
            "FORMAT" to R.drawable.round_format_20,
            "CODE" to R.drawable.round_code_20,
            // 文件/保存
            "SAVE" to R.drawable.round_save_20,
            // 工具
            "MORE" to R.drawable.round_more_vert_20,
            "REFRESH" to R.drawable.round_refresh_20,
            "SHARE" to R.drawable.round_share_20,
        )

        /** 默认图标大小 (dp) */
        const val DEFAULT_ICON_SIZE_DP = 45
        /** 默认圆角半径 (dp) */
        const val DEFAULT_CORNER_RADIUS_DP = 5f
        /** 最大图片尺寸 (px) */
        const val MAX_IMAGE_SIZE_PX = 256

        /** 所有活跃实例（用于插件卸载时自动清理按钮） */
        private val instances = mutableListOf<LuaTextActionWindow>()

        /** 待注册按钮（编辑器创建前插件的注册请求） */
        private val pendingButtons = mutableListOf<ButtonEntry>()

        fun registerInstance(instance: LuaTextActionWindow) {
            instances.add(instance)
            // 编辑器创建后，立即处理待注册队列
            if (pendingButtons.isNotEmpty()) {
                val pending = pendingButtons.toList()
                pendingButtons.clear()
                pending.forEach { entry ->
                    instance.registerButton(entry.id, entry.pluginId, entry.iconResId, entry.iconPath, entry.label)
                }
            }
        }

        fun unregisterInstance(instance: LuaTextActionWindow) {
            instances.remove(instance)
        }

        /**
         * 当编辑器尚未创建时，将按钮注册请求加入待处理队列
         */
        fun scheduleButton(id: String, pluginId: String, iconResId: Int, iconPath: String, label: String) {
            pendingButtons.removeAll { it.id == id }
            pendingButtons.add(ButtonEntry(id, pluginId, iconResId, iconPath, label))
        }

        /**
         * 获取待处理队列中的按钮 ID（用于 getTextActionButtons 查询）
         */
        fun getPendingButtonIds(pluginId: String): List<String> {
            return pendingButtons.filter { it.pluginId == pluginId }.map { it.id }
        }

        /**
         * 注销待处理队列中的按钮
         */
        fun unregisterPendingButton(id: String): Boolean {
            return pendingButtons.removeAll { it.id == id }
        }

        /**
         * 当插件卸载时，自动清除该插件在所有编辑器中的自定义按钮
         */
        fun onPluginUnloaded(pluginId: String) {
            pendingButtons.removeAll { it.pluginId == pluginId }
            instances.forEach { it.unregisterButtonsByPlugin(pluginId) }
        }
    }

    // ==================== 自定义按钮数据类 ====================

    data class ButtonEntry(
        val id: String,
        val pluginId: String,
        val iconResId: Int = 0,
        val iconPath: String = "",
        val label: String = ""
    )

    // ==================== 状态 ====================

    /** 按钮列表（pluginId → ButtonEntry） */
    private val buttonEntries = mutableListOf<ButtonEntry>()
    /** 自定义按钮视图缓存 */
    private val customButtonViews = mutableListOf<ImageButton>()
    /** 圆角半径 (dp) */
    var cornerRadiusDp: Float = DEFAULT_CORNER_RADIUS_DP
    /** 图标大小 (dp) */
    var iconSizeDp: Int = DEFAULT_ICON_SIZE_DP
    /** 按钮容器 */
    private val innerContainer: LinearLayout?
        get() = (getView().findViewById<View>(R.id.panel_hv) as? android.widget.HorizontalScrollView)
            ?.getChildAt(0) as? LinearLayout
    /** 插件卸载事件监听器引用，用于 dispose 时注销 */
    private val pluginUnloadListener = object : IPluginEventListener {
        override fun onEvent(vararg args: Any?) {
            val pluginId = args.firstOrNull() as? String ?: return
            onPluginUnloaded(pluginId)
        }
    }

    init {
        registerInstance(this)
        // 监听插件卸载事件，自动清理按钮
        EventManager.registerEventListener(
            "__lua_text_action__",
            PluginEvents.ON_PLUGIN_UNLOADED,
            pluginUnloadListener
        )
    }

    /** 释放资源（编辑器销毁时调用） */
    fun dispose() {
        unregisterInstance(this)
        EventManager.unregisterEventListener(PluginEvents.ON_PLUGIN_UNLOADED, pluginUnloadListener)
    }

    // ==================== 公开 API ====================

    /**
     * 注册自定义按钮
     * @param id 按钮唯一标识
     * @param pluginId 插件ID（用于卸载时自动清理）
     * @param icon 图标：内置常量名（如 "SEARCH"）或自定义图片路径
     * @param label 按钮标签（当图标不可用时显示）
     * @return true=注册成功，false=已存在
     */
    fun registerButton(id: String, pluginId: String, icon: String, label: String = ""): Boolean {
        if (buttonEntries.any { it.id == id }) return false

        val iconResId = ICON_MAP[icon] ?: 0
        val isPath = iconResId == 0 && (icon.contains("/") || icon.contains("."))
        val customPath = if (isPath) icon else ""

        buttonEntries.add(
            ButtonEntry(
                id = id,
                pluginId = pluginId,
                iconResId = iconResId,
                iconPath = customPath,
                label = label
            )
        )
        rebuildCustomButtons()
        return true
    }

    /** 内部方法：用预解析参数注册按钮（供 pending 队列回放使用） */
    internal fun registerButton(id: String, pluginId: String, iconResId: Int, iconPath: String, label: String): Boolean {
        if (buttonEntries.any { it.id == id }) return false
        buttonEntries.add(ButtonEntry(id, pluginId, iconResId, iconPath, label))
        rebuildCustomButtons()
        return true
    }

    /**
     * 注销自定义按钮
     */
    fun unregisterButton(id: String): Boolean {
        val removed = buttonEntries.removeAll { it.id == id }
        if (removed) rebuildCustomButtons()
        return removed
    }

    /**
     * 按插件ID清除所有按钮（插件卸载时自动调用）
     */
    fun unregisterButtonsByPlugin(pluginId: String) {
        val removed = buttonEntries.removeAll { it.pluginId == pluginId }
        if (removed) rebuildCustomButtons()
    }

    /**
     * 获取已注册的自定义按钮 ID 列表
     */
    fun getRegisteredButtons(): List<String> = buttonEntries.map { it.id }

    /**
     * 清除所有自定义按钮
     */
    fun clearAllButtons() {
        buttonEntries.clear()
        rebuildCustomButtons()
    }

    /**
     * 重新应用颜色方案
     */
    fun applyCustomColorScheme() {
        applyColorScheme()
        rebuildCustomButtons()
    }

    // ==================== 内部实现 ====================

    override fun applyColorScheme() {
        val view = getView() ?: return  // 父类构造函数中调用时 view 尚未 inflate
        val colorScheme = editor.colorScheme
        val bgColor = colorScheme.getColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND)
        val iconColor = colorScheme.getColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR)
        val dpUnit = editor.dpUnit

        val gd = GradientDrawable().apply {
            cornerRadius = cornerRadiusDp * dpUnit
            setColor(bgColor)
        }
        view.findViewById<View>(R.id.panel_root).background = gd

        val colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)
        applyColorFilterInternal(view, R.id.panel_btn_select_all, colorFilter)
        applyColorFilterInternal(view, R.id.panel_btn_cut, colorFilter)
        applyColorFilterInternal(view, R.id.panel_btn_copy, colorFilter)
        applyColorFilterInternal(view, R.id.panel_btn_paste, colorFilter)
        applyColorFilterInternal(view, R.id.panel_btn_long_select, colorFilter)
        customButtonViews.forEach { btn ->
            btn.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)
        }
    }

    private fun applyColorFilterInternal(view: View, btnId: Int, filter: PorterDuffColorFilter) {
        val btn = view.findViewById<ImageButton>(btnId) ?: return
        btn.colorFilter = filter
    }

    override fun displayWindow() {
        // 确保 view 已 inflate 后再应用颜色方案（修复父类构造函数中 view 未 inflate 的问题）
        applyColorScheme()
        // 触发插件事件
        val selText = getSelectedText()
        EventManager.fireEvent(PluginEvents.ON_TEXT_ACTION_WINDOW_SHOWN, selText)
        super.displayWindow()
    }

    // ==================== 按钮重建 ====================

    private fun rebuildCustomButtons() {
        val container = innerContainer ?: return

        customButtonViews.forEach { container.removeView(it) }
        customButtonViews.clear()

        val dpUnit = editor.dpUnit
        val iconColor = editor.colorScheme.getColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR)

        buttonEntries.forEach { entry ->
            val btn = ImageButton(editor.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (iconSizeDp * dpUnit).toInt(),
                    (iconSizeDp * dpUnit).toInt()
                )
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setPadding(
                    (4 * dpUnit).toInt(),
                    (4 * dpUnit).toInt(),
                    (4 * dpUnit).toInt(),
                    (4 * dpUnit).toInt()
                )
                val typedValue = TypedValue()
                editor.context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, typedValue, true
                )
                setBackgroundResource(typedValue.resourceId)
                setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)

                if (entry.iconResId != 0) {
                    setImageResource(entry.iconResId)
                } else if (entry.iconPath.isNotEmpty()) {
                    loadCustomImage(entry.iconPath)
                } else {
                    contentDescription = entry.label
                }

                setOnClickListener {
                    EventManager.fireEvent(
                        PluginEvents.ON_TEXT_ACTION_WINDOW_BUTTON_CLICK,
                        entry.id,
                        getSelectedText()
                    )
                    dismiss()
                }
            }
            container.addView(btn)
            customButtonViews.add(btn)
        }
    }

    private fun ImageButton.loadCustomImage(path: String) {
        val btn = this
        // 在后台线程解码 Bitmap，避免主线程卡顿
        Thread {
            try {
                val file = File(path)
                if (!file.exists()) return@Thread

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(path, options)

                var sampleSize = 1
                val maxDim = maxOf(options.outWidth, options.outHeight)
                while (maxDim / sampleSize > MAX_IMAGE_SIZE_PX) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val bitmap = BitmapFactory.decodeFile(path, decodeOptions)
                if (bitmap != null) {
                    btn.post {
                        btn.setImageDrawable(BitmapDrawable(btn.resources, bitmap))
                    }
                }
            } catch (_: Exception) {
                // 加载失败静默处理
            }
        }.start()
    }

    private fun getSelectedText(): String {
        val cursor = editor.cursor
        return if (cursor.isSelected) {
            editor.text.substring(cursor.left, cursor.right)
        } else ""
    }
}