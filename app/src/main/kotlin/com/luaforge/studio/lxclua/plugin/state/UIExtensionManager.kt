package com.luaforge.studio.lxclua.plugin.state

import androidx.compose.runtime.Composable

/**
 * UI 扩展点位置常量
 * 定义插件可以注入UI组件的位置
 */
object UIExtensionPoints {
    // ========== 首页/主页 ==========
    /** 首页顶部工具栏右侧（图标按钮区），适合添加工具按钮 */
    const val HOME_TOOLBAR_END = "home_toolbar_end"
    /** 首页底部操作栏左侧（FAB旁边），适合添加快捷操作 */
    const val HOME_BOTTOM_BAR_START = "home_bottom_bar_start"
    /** 首页底部操作栏右侧 */
    const val HOME_BOTTOM_BAR_END = "home_bottom_bar_end"
    /** 首页项目列表顶部（搜索栏下方），适合添加横幅/公告 */
    const val HOME_LIST_HEADER = "home_list_header"
    /** 首页项目列表底部（列表结束后），适合添加统计信息 */
    const val HOME_LIST_FOOTER = "home_list_footer"
    /** 首页项目卡片扩展内容（卡片内部下方），适合添加卡片信息/操作 */
    const val HOME_PROJECT_CARD_CONTENT = "home_project_card_content"
    /** 首页项目卡片右上角操作区，适合添加卡片图标按钮 */
    const val HOME_PROJECT_CARD_ACTIONS = "home_project_card_actions"
    /** 首页浮动操作按钮扩展区 */
    const val HOME_FLOATING_ACTIONS = "home_floating_actions"
    /** 首页空状态内容（无项目时显示），适合添加引导内容 */
    const val HOME_EMPTY_CONTENT = "home_empty_content"
    /** 首页最近项目栏右侧，适合添加最近栏操作 */
    const val HOME_RECENT_BAR_END = "home_recent_bar_end"
    /** 首页下拉菜单/更多菜单扩展，适合添加自定义菜单项 */
    const val HOME_MORE_MENU = "home_more_menu"

    // ========== 编辑器 ==========
    /** 编辑器顶部工具栏右侧（保存/运行按钮旁边） */
    const val EDITOR_TOOLBAR_END = "editor_toolbar_end"
    /** 编辑器顶部工具栏左侧（返回按钮旁边） */
    const val EDITOR_TOOLBAR_START = "editor_toolbar_start"
    /** 编辑器底部符号栏扩展（在现有符号按钮后追加） */
    const val EDITOR_SYMBOL_BAR = "editor_symbol_bar"
    /** 编辑器右侧边栏（行号旁边区域） */
    const val EDITOR_GUTTER = "editor_gutter"
    /** 编辑器底部面板（输出/控制台区域扩展） */
    const val EDITOR_BOTTOM_PANEL = "editor_bottom_panel"
    /** 编辑器Tab栏右键菜单/更多菜单 */
    const val EDITOR_TAB_MENU = "editor_tab_menu"
    /** 编辑器文本选择后弹出的操作菜单（复制/剪切旁边） */
    const val EDITOR_SELECTION_MENU = "editor_selection_menu"
    /** 编辑器文件被修改后的状态栏区域 */
    const val EDITOR_STATUS_BAR = "editor_status_bar"
    /** 编辑器空标签页（无打开文件时的欢迎页内容） */
    const val EDITOR_WELCOME_CONTENT = "editor_welcome_content"

    // ========== 设置页 ==========
    /** 设置页列表顶部（标题下方） */
    const val SETTINGS_HEADER = "settings_header"
    /** 设置页列表底部（关于上方） */
    const val SETTINGS_FOOTER = "settings_footer"
    /** 设置页-外观设置组内部末尾 */
    const val SETTINGS_APPEARANCE_SECTION = "settings_appearance_section"
    /** 设置页-编辑器设置组内部末尾 */
    const val SETTINGS_EDITOR_SECTION = "settings_editor_section"
    /** 设置页-自定义设置项组（插件可以添加整个设置卡片分组） */
    const val SETTINGS_PLUGIN_SECTION = "settings_plugin_section"

    // ========== 项目详情/操作 ==========
    /** 项目长按菜单项扩展 */
    const val PROJECT_CONTEXT_MENU = "project_context_menu"
    /** 项目详情页内容区 */
    const val PROJECT_DETAIL_CONTENT = "project_detail_content"
    /** 项目操作对话框底部按钮区扩展 */
    const val PROJECT_DIALOG_ACTIONS = "project_dialog_actions"

    // ========== 新建项目页 ==========
    /** 新建项目页顶部 */
    const val NEW_PROJECT_HEADER = "new_project_header"
    /** 新建项目页底部（创建按钮上方） */
    const val NEW_PROJECT_FOOTER = "new_project_footer"
    /** 新建项目页模板列表扩展 */
    const val NEW_PROJECT_TEMPLATES = "new_project_templates"

    // ========== 插件管理页 ==========
    /** 插件管理页顶部工具栏 */
    const val PLUGIN_TOOLBAR = "plugin_toolbar"
    /** 插件卡片内容扩展（描述下方） */
    const val PLUGIN_CARD_CONTENT = "plugin_card_content"
    /** 插件详情页内容区 */
    const val PLUGIN_DETAIL_CONTENT = "plugin_detail_content"

    // ========== 全局/通用 ==========
    /** 应用全局浮动球扩展（点击展开的菜单中追加） */
    const val GLOBAL_FLOATING_MENU = "global_floating_menu"
    /** 应用全局抽屉/侧边栏菜单项 */
    const val GLOBAL_DRAWER_MENU = "global_drawer_menu"
    /** 全局顶部AppBar标题区域（可替换/扩展标题） */
    const val GLOBAL_APPBAR_TITLE = "global_appbar_title"
    /** 全局通知区域扩展 */
    const val GLOBAL_NOTIFICATIONS = "global_notifications"
    /** About页内容扩展 */
    const val ABOUT_CONTENT = "about_content"

    // ========== 文件浏览器 ==========
    /** 文件浏览器工具栏右侧 */
    const val FILE_BROWSER_TOOLBAR = "file_browser_toolbar"
    /** 文件项右键菜单项 */
    const val FILE_CONTEXT_MENU = "file_context_menu"
}

/**
 * UI扩展点注册条目
 * @property pluginId 注册该扩展的插件ID
 * @property extensionPoint 扩展点位置（使用 UIExtensionPoints 常量）
 * @property priority 优先级，数字越小越靠前显示
 * @property content Compose 可组合内容函数，接收 context 参数（通常是当前页面相关数据）
 */
data class UIExtensionEntry(
    val pluginId: String,
    val extensionPoint: String,
    val priority: Int = 0,
    val content: @Composable (Map<String, Any?>) -> Unit
)

/**
 * UI 扩展点管理器
 * 管理插件注册的UI组件注入点
 */
object UIExtensionManager {

    // 存储结构: extensionPoint -> MutableList<UIExtensionEntry>
    private val extensions = mutableMapOf<String, MutableList<UIExtensionEntry>>()

    /**
     * 注册UI扩展点
     * @param pluginId 插件ID
     * @param extensionPoint 扩展点位置（UIExtensionPoints常量）
     * @param priority 优先级，数字越小越靠前
     * @param content Compose内容函数，参数为上下文数据map
     */
    fun registerExtension(
        pluginId: String,
        extensionPoint: String,
        priority: Int = 0,
        content: @Composable (Map<String, Any?>) -> Unit
    ) {
        extensions.getOrPut(extensionPoint) { mutableListOf() }.add(
            UIExtensionEntry(pluginId, extensionPoint, priority, content)
        )
    }

    /**
     * 取消注册指定插件在指定位置的所有扩展
     */
    fun unregisterExtensions(pluginId: String, extensionPoint: String? = null) {
        if (extensionPoint != null) {
            extensions[extensionPoint]?.removeIf { it.pluginId == pluginId }
        } else {
            extensions.values.forEach { list ->
                list.removeIf { it.pluginId == pluginId }
            }
        }
    }

    /**
     * 获取指定扩展点位置的所有已注册UI条目（按优先级排序）
     * @param extensionPoint 扩展点位置
     * @return 按 priority 升序排列的条目列表
     */
    fun getExtensions(extensionPoint: String): List<UIExtensionEntry> {
        return extensions[extensionPoint]?.sortedBy { it.priority } ?: emptyList()
    }

    /**
     * 检查指定扩展点是否有注册内容
     */
    fun hasExtensions(extensionPoint: String): Boolean {
        return extensions[extensionPoint]?.isNotEmpty() == true
    }

    /**
     * 获取指定扩展点的注册插件数量
     */
    fun getExtensionCount(extensionPoint: String): Int {
        return extensions[extensionPoint]?.size ?: 0
    }

    /**
     * 获取所有已注册的扩展点位置名称
     */
    fun getAllExtensionPoints(): List<String> {
        return extensions.keys.toList()
    }

    /**
     * 清空所有注册的扩展（插件系统重置时用）
     */
    fun clearAll() {
        extensions.clear()
    }
}
