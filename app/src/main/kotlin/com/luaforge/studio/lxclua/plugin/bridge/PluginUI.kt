package com.luaforge.studio.lxclua.plugin.bridge

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import com.luaforge.studio.lxclua.plugin.PluginManager
import com.luaforge.studio.lxclua.plugin.api.callbacks.OnInputCallback
import com.luaforge.studio.lxclua.plugin.api.callbacks.OnMultiSelectCallback
import com.luaforge.studio.lxclua.plugin.api.callbacks.OnSelectCallback
import com.luaforge.studio.lxclua.plugin.state.UIExtensionManager
import com.luaforge.studio.lxclua.plugin.state.UIExtensionPoints

/**
 * UI 操作与扩展 API
 *
 * Lua 使用方式（注意: Java 对象/方法调用一律使用 `.` 而非 `:`）：
 * ```lua
 * -- 1. 对话框
 * plugin.ui.showMessage("提示", "Hello World")
 * plugin.ui.showConfirm("确认", "确定要删除吗？", function()
 *     print("用户点了确定")
 * end, function()
 *     print("用户点了取消")
 * end)
 *
 * -- 2. 添加工具栏按钮（在首页工具栏右侧）
 * local btnId = plugin.ui.addToolbarButton("home_toolbar_end", "refresh", "刷新", function()
 *     print("刷新按钮被点击")
 * end)
 *
 * -- 3. 添加菜单项
 * plugin.ui.addMenuItem("home_more_menu", "export", "导出项目", function()
 *     print("导出菜单被点击")
 * end)
 *
 * -- 4. 扩展点位置常量（参考 plugin.ui.POINTS 表）
 * print(plugin.ui.POINTS.HOME_TOOLBAR_END)
 * ```
 */
class PluginUI(private val pluginId: String) {

    private val handler = Handler(Looper.getMainLooper())

    // ============ UI 扩展点位置常量（Lua 可通过 plugin.ui.POINTS.XXX 访问） ============
    val POINTS = UIExtensionPoints

    /**
     * 注册 Compose UI 扩展点（Java/Dex插件用，Lua 不支持直接传 Composable）
     * @param extensionPoint 扩展点位置（使用 UIExtensionPoints 常量）
     * @param priority 优先级，数字越小越靠前
     * @param content Compose 可组合内容
     */
    fun registerExtension(
        extensionPoint: String,
        priority: Int = 0,
        content: @Composable (Map<String, Any?>) -> Unit
    ) {
        UIExtensionManager.registerExtension(pluginId, extensionPoint, priority, content)
    }

    /**
     * 取消注册本插件所有扩展点
     * @param extensionPoint 指定位置（为null则取消全部）
     */
    fun unregisterExtensions(extensionPoint: String? = null) {
        UIExtensionManager.unregisterExtensions(pluginId, extensionPoint)
    }

    // ============ 工具栏按钮扩展（Lua 友好 API） ============

    /**
     * 在指定工具栏扩展点添加一个图标按钮（Lua 友好 API）
     * 底层会注册一个带 IconButton 的 Compose 扩展
     *
     * @param extensionPoint 扩展点位置，例如 POINTS.HOME_TOOLBAR_END, POINTS.EDITOR_TOOLBAR_END
     * @param id 按钮唯一标识（同插件内同位置不能重复）
     * @param iconName Material 图标名称（暂时使用文字标签代替，后续可扩展图标名解析）
     * @param tooltip 按钮提示文本
     * @param priority 优先级
     * @param onClick 点击回调
     * @return 按钮ID（可用于后续移除）
     */
    fun addToolbarButton(
        extensionPoint: String,
        id: String,
        tooltip: String,
        priority: Int = 0,
        onClick: Runnable
    ): String {
        val key = "${pluginId}:${extensionPoint}:${id}"
        handler.post {
            // 注册到扩展点系统（渲染端根据 ToolbarButtonAction 类型渲染）
            val existing = PluginManager.toolbarActionEntries.indexOfFirst { it.key == key }
            val entry = PluginManager.ToolbarActionEntry(
                key = key,
                pluginId = pluginId,
                extensionPoint = extensionPoint,
                actionId = id,
                tooltip = tooltip,
                priority = priority,
                onClick = onClick
            )
            if (existing >= 0) {
                PluginManager.toolbarActionEntries[existing] = entry
            } else {
                PluginManager.toolbarActionEntries.add(entry)
            }
        }
        return key
    }

    /**
     * 移除指定工具栏按钮
     */
    fun removeToolbarButton(key: String) {
        handler.post {
            PluginManager.toolbarActionEntries.removeAll { it.key == key }
        }
    }

    /**
     * 移除本插件所有工具栏按钮
     */
    fun clearToolbarButtons() {
        handler.post {
            PluginManager.toolbarActionEntries.removeAll { it.pluginId == pluginId }
        }
    }

    // ============ 菜单项扩展（Lua 友好 API） ============

    /**
     * 在指定菜单扩展点添加菜单项
     * @param extensionPoint 扩展点位置
     * @param id 菜单项唯一标识
     * @param title 菜单标题
     * @param priority 优先级
     * @param onClick 点击回调
     * @return 菜单项ID
     */
    fun addMenuItem(
        extensionPoint: String,
        id: String,
        title: String,
        priority: Int = 0,
        onClick: Runnable
    ): String {
        val key = "${pluginId}:${extensionPoint}:${id}"
        handler.post {
            val existing = PluginManager.menuItemEntries.indexOfFirst { it.key == key }
            val entry = PluginManager.MenuItemEntry(
                key = key,
                pluginId = pluginId,
                extensionPoint = extensionPoint,
                itemId = id,
                title = title,
                priority = priority,
                onClick = onClick
            )
            if (existing >= 0) {
                PluginManager.menuItemEntries[existing] = entry
            } else {
                PluginManager.menuItemEntries.add(entry)
            }
        }
        return key
    }

    /**
     * 移除菜单项
     */
    fun removeMenuItem(key: String) {
        handler.post {
            PluginManager.menuItemEntries.removeAll { it.key == key }
        }
    }

    /**
     * 清空本插件所有菜单项
     */
    fun clearMenuItems() {
        handler.post {
            PluginManager.menuItemEntries.removeAll { it.pluginId == pluginId }
        }
    }

    // ============ 对话框 API ============

    /**
     * 显示消息对话框
     */
    fun showMessage(title: String, message: String) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.Message(
                title = title,
                message = message,
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    /**
     * 显示确认对话框
     */
    fun showConfirm(title: String, message: String, onConfirm: Runnable, onCancel: Runnable?) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.Confirm(
                title = title,
                message = message,
                onConfirm = {
                    PluginManager.currentDialog.value = null
                    onConfirm.run()
                },
                onCancel = onCancel?.let {
                    {
                        PluginManager.currentDialog.value = null
                        it.run()
                    }
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    /**
     * 显示输入对话框
     */
    fun showInputDialog(title: String, hint: String, defaultValue: String, onInput: OnInputCallback) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.Input(
                title = title,
                hint = hint,
                defaultValue = defaultValue,
                onInput = { text ->
                    PluginManager.currentDialog.value = null
                    onInput.onInput(text)
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    /**
     * 显示单选对话框
     */
    fun showSingleChoiceDialog(title: String, items: Array<String>, selectedIndex: Int, onSelect: OnSelectCallback) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.SingleChoice(
                title = title,
                items = items,
                selectedIndex = selectedIndex,
                onSelect = { index ->
                    PluginManager.currentDialog.value = null
                    onSelect.onSelect(index)
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    /**
     * 显示多选对话框
     */
    fun showMultiChoiceDialog(title: String, items: Array<String>, checkedItems: BooleanArray, onConfirm: OnMultiSelectCallback) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.MultiChoice(
                title = title,
                items = items,
                checkedItems = checkedItems.copyOf(),
                onConfirm = { result ->
                    PluginManager.currentDialog.value = null
                    onConfirm.onConfirm(result)
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    /**
     * 显示文件列表对话框
     */
    fun showFileListDialog(title: String, directoryPath: String, filter: String?, onSelect: OnInputCallback) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.FileList(
                title = title,
                directoryPath = directoryPath,
                filter = filter,
                onSelect = { path ->
                    PluginManager.currentDialog.value = null
                    onSelect.onInput(path)
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    /**
     * 显示图片对话框
     */
    fun showImageDialog(title: String, imagePath: String) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.ImageDisplay(
                title = title,
                imagePath = imagePath,
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    /**
     * 显示文本展示对话框
     */
    fun showTextDialog(title: String, text: String) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.TextDisplay(
                title = title,
                text = text,
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    /**
     * 显示复选框对话框
     */
    fun showCheckboxDialog(title: String, message: String, checked: Boolean, onConfirm: OnSelectCallback) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.Checkbox(
                title = title,
                message = message,
                checked = checked,
                onConfirm = { result ->
                    PluginManager.currentDialog.value = null
                    onConfirm.onSelect(if (result) 1 else 0)
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    // ============ 底部面板扩展 ============

    /**
     * 添加编辑器底部面板项
     */
    fun addBottomPanelItem(key: String, title: String, elements: List<Map<String, Any?>>, onEvent: Runnable?) {
        val converted = elements.map { raw ->
            PluginManager.BottomPanelElement(
                type = raw["type"] as? String ?: "text",
                id = raw["id"] as? String,
                value = raw["value"] as? String,
                height = (raw["height"] as? Number)?.toFloat() ?: 0f
            )
        }
        handler.post {
            val item = PluginManager.BottomPanelItem(
                pluginId = pluginId,
                key = key,
                title = title,
                elements = converted,
                onEvent = onEvent
            )
            val existingIndex = PluginManager.bottomPanelItems.indexOfFirst { it.key == key }
            if (existingIndex >= 0) {
                PluginManager.bottomPanelItems[existingIndex] = item
            } else {
                PluginManager.bottomPanelItems.add(item)
            }
            if (PluginManager.activeBottomPanelKey.value == null) {
                PluginManager.activeBottomPanelKey.value = key
            }
        }
    }

    /**
     * 移除底部面板项
     */
    fun removeBottomPanelItem(key: String) {
        handler.post {
            PluginManager.bottomPanelItems.removeAll { it.key == key }
            if (PluginManager.activeBottomPanelKey.value == key) {
                PluginManager.activeBottomPanelKey.value = PluginManager.bottomPanelItems.firstOrNull()?.key
            }
        }
    }

    /**
     * 清空插件的底部面板项
     */
    fun clearBottomPanelItems() {
        handler.post {
            PluginManager.bottomPanelItems.removeAll { it.pluginId == pluginId }
            if (PluginManager.bottomPanelItems.none { it.key == PluginManager.activeBottomPanelKey.value }) {
                PluginManager.activeBottomPanelKey.value = PluginManager.bottomPanelItems.firstOrNull()?.key
            }
        }
    }
}
