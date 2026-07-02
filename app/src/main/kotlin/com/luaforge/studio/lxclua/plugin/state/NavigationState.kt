package com.luaforge.studio.lxclua.plugin.state

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 侧滑栏菜单项数据类
 */
data class SidebarItem(
    val pluginId: String,
    val key: String,
    val label: String,
    val group: String,
    val iconName: String?,
    val onClick: Runnable
)

/**
 * 导航状态管理
 * 负责管理侧滑栏菜单项和页面导航
 */
object NavigationState {
    
    // 导航栈，记录页面跳转历史，用于 goBack()
    private val navigationStack = mutableListOf<String>()
    private const val MAX_STACK_SIZE = 20
    
    // 插件注册的侧滑栏菜单项（Compose 响应式列表，UI 直接读取以触发重组）
    val pluginSidebarItems = mutableStateListOf<SidebarItem>()
    
    // 侧滑栏状态回调
    private val sidebarStateListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    
    // 当前侧滑栏状态（Compose 响应式，供 UI 直接观察）
    val sidebarOpen = mutableStateOf(false)
    
    // 插件请求的导航目标（String，由 MainActivity 的 LaunchedEffect 观察并执行实际导航）
    val pendingNavTarget = mutableStateOf<String?>(null)
    
    // 插件请求打开的项目ID（配合 navigateTo("editor") 使用）
    val pendingOpenProjectId = mutableStateOf<String?>(null)
    
    // 插件 WebUI 导航：请求打开的插件 ID
    val pendingWebUIPluginId = mutableStateOf<String?>(null)
    
    // 插件 WebUI 导航：请求打开的页面文件名
    val pendingWebUIPage = mutableStateOf("index.html")
    
    // 当前是否正在显示 WebUI
    private var webUIShowing = false
    
    // 当前导航页面
    private var currentPage = "editor"
    
    /**
     * 添加侧滑栏菜单项
     */
    fun addSidebarItem(pluginId: String, key: String, label: String, group: String, iconName: String?, onClick: Runnable) {
        val fullKey = "$pluginId:$key"
        
        // 移除已存在的同名项
        removeSidebarItem(pluginId, key)
        
        pluginSidebarItems.add(
            SidebarItem(
                pluginId = pluginId,
                key = fullKey,
                label = label,
                group = group,
                iconName = iconName,
                onClick = onClick
            )
        )
    }
    
    /**
     * 移除侧滑栏菜单项
     */
    fun removeSidebarItem(pluginId: String, key: String) {
        val fullKey = "$pluginId:$key"
        pluginSidebarItems.removeAll { it.pluginId == pluginId && it.key == fullKey }
    }
    
    /**
     * 清除指定插件的所有侧滑栏菜单项
     */
    fun clearPluginSidebarItems(pluginId: String) {
        pluginSidebarItems.removeAll { it.pluginId == pluginId }
    }
    
    /**
     * 获取所有插件注册的侧滑栏菜单项
     */
    fun getPluginSidebarItems(): List<SidebarItem> {
        return pluginSidebarItems.toList()
    }
    
    /**
     * 按分组获取侧滑栏菜单项
     */
    fun getSidebarItemsByGroup(group: String): List<SidebarItem> {
        return pluginSidebarItems.filter { it.group == group }
    }
    
    /**
     * 打开侧滑栏
     */
    fun openSidebar() {
        sidebarOpen.value = true
        notifySidebarStateChanged(true)
    }
    
    /**
     * 关闭侧滑栏
     */
    fun closeSidebar() {
        sidebarOpen.value = false
        notifySidebarStateChanged(false)
    }
    
    /**
     * 切换侧滑栏状态
     */
    fun toggleSidebar() {
        if (sidebarOpen.value) {
            closeSidebar()
        } else {
            openSidebar()
        }
    }
    
    /**
     * 检查侧滑栏是否打开
     */
    fun isSidebarOpen(): Boolean {
        return sidebarOpen.value
    }
    
    /**
     * 注册侧滑栏状态监听器
     */
    fun addSidebarStateListener(listener: (Boolean) -> Unit) {
        sidebarStateListeners.add(listener)
    }
    
    /**
     * 取消注册侧滑栏状态监听器
     */
    fun removeSidebarStateListener(listener: (Boolean) -> Unit) {
        sidebarStateListeners.remove(listener)
    }
    
    /**
     * 通知侧滑栏状态变化
     */
    private fun notifySidebarStateChanged(isOpen: Boolean) {
        sidebarStateListeners.forEach { it.invoke(isOpen) }
    }
    
    /**
     * 导航到指定页面
     * @param pageId 页面标识，支持: "main"、"new_project"、"editor"、"settings"、"about"、"plugins"
     */
    fun navigateTo(pageId: String) {
        // 推入导航栈（避免连续重复推入同一页面）
        if (navigationStack.isEmpty() || navigationStack.last() != pageId) {
            navigationStack.add(pageId)
            if (navigationStack.size > MAX_STACK_SIZE) {
                navigationStack.removeAt(0)
            }
        }
        pendingNavTarget.value = pageId
        currentPage = pageId
    }

    /**
     * 获取当前页面标识
     */
    fun getCurrentPage(): String {
        return currentPage
    }

    /**
     * 清除导航目标（由 MainActivity 在完成导航后调用）
     */
    fun clearNavTarget() {
        pendingNavTarget.value = null
        pendingOpenProjectId.value = null
    }
    
    /**
     * 导航到指定项目（打开编辑器）
     * @param projectId 要打开的项目ID
     */
    fun navigateToProject(projectId: String) {
        pendingOpenProjectId.value = projectId
        navigateTo("editor")
    }
    
    /**
     * 返回上一页（基于导航栈）
     * 弹出当前页面，导航到上一个页面；若栈为空则返回主页
     */
    fun goBack() {
        // 弹出当前页（栈顶）
        if (navigationStack.isNotEmpty()) {
            navigationStack.removeAt(navigationStack.lastIndex)
        }
        // 取新的栈顶作为目标页，栈空则回主页
        val target = if (navigationStack.isNotEmpty()) navigationStack.last() else "main"
        // 防止回退到同一个页面形成闭环（如栈中只有 editor）
        if (target == currentPage) {
            pendingNavTarget.value = "main"
            currentPage = "main"
        } else {
            pendingNavTarget.value = target
            currentPage = target
        }
    }
    
    /**
     * 清理指定插件的所有资源
     */
    fun cleanupPluginResources(pluginId: String) {
        clearPluginSidebarItems(pluginId)
    }
    
    // ==================== WebUI 导航 ====================
    
    /**
     * 打开指定插件的 WebUI 页面
     *
     * @param pluginId 插件 ID
     * @param page 页面文件名（相对于 web/ 目录），默认 index.html
     */
    fun openWebUI(pluginId: String, page: String = "index.html") {
        pendingWebUIPluginId.value = pluginId
        pendingWebUIPage.value = page
        webUIShowing = true
    }
    
    /**
     * 关闭当前 WebUI 页面
     */
    fun closeWebUI() {
        pendingWebUIPluginId.value = null
        webUIShowing = false
    }
    
    /**
     * 检查 WebUI 是否正在显示
     */
    fun isWebUIShowing(): Boolean = webUIShowing
    
    /**
     * 标记 WebUI 已关闭（由 UI 层在关闭后调用）
     */
    fun onWebUIClosed() {
        webUIShowing = false
        pendingWebUIPluginId.value = null
    }
}
