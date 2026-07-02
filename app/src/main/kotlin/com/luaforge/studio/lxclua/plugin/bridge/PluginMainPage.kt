package com.luaforge.studio.lxclua.plugin.bridge

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.luaforge.studio.lxclua.plugin.PluginManager
import com.luaforge.studio.lxclua.plugin.api.IPluginBridgeMainPage
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.NavigationState
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import com.luaforge.studio.lxclua.plugin.state.UIState

/**
 * 主页项目列表操作 API
 * 
 * 使用方式:
 *   plugin.mainpage.enterMultiSelectMode()
 *   plugin.mainpage.setProjectBadge("projId", "3个文件", 0xFF4CAF50)
 *   plugin.mainpage.addToolbarAction("sync", "Sync", "Refresh") { ... }
 */
class PluginMainPage(private val pluginId: String) : IPluginBridgeMainPage {
    
    private val handler = Handler(Looper.getMainLooper())
    
    // ============ 多选模式 ============
    
    fun enterMultiSelectMode() {
        handler.post { PluginManager.isMultiSelectMode.value = true }
    }
    
    fun exitMultiSelectMode() {
        handler.post {
            PluginManager.isMultiSelectMode.value = false
            PluginManager.multiSelectedProjectIds.clear()
        }
    }
    
    /**
     * 设置多选模式
     * @param enabled true 进入多选模式，false 退出
     */
    override fun setMultiSelectMode(enabled: Boolean) {
        handler.post {
            PluginManager.isMultiSelectMode.value = enabled
            if (!enabled) {
                PluginManager.multiSelectedProjectIds.clear()
            }
        }
    }
    
    fun toggleProjectSelection(projectId: String) {
        handler.post {
            if (projectId in PluginManager.multiSelectedProjectIds) {
                PluginManager.multiSelectedProjectIds.remove(projectId)
            } else {
                PluginManager.multiSelectedProjectIds.add(projectId)
            }
        }
    }
    
    fun selectProject(projectId: String) {
        handler.post {
            if (projectId !in PluginManager.multiSelectedProjectIds) {
                PluginManager.multiSelectedProjectIds.add(projectId)
            }
        }
    }
    
    fun deselectProject(projectId: String) {
        handler.post { PluginManager.multiSelectedProjectIds.remove(projectId) }
    }
    
    /**
     * 获取当前多选模式下选中的项目ID列表
     * @return 选中的项目ID数组
     */
    override fun getSelectedProjectIds(): Array<String> {
        return PluginManager.multiSelectedProjectIds.toTypedArray()
    }
    
    fun getSelectedCount(): Int {
        return PluginManager.multiSelectedProjectIds.size
    }
    
    fun isInMultiSelectMode(): Boolean {
        return PluginManager.isMultiSelectMode.value
    }
    
    fun clearSelection() {
        handler.post { PluginManager.multiSelectedProjectIds.clear() }
    }
    
    // ============ 项目徽章 ============
    
    /**
     * 给项目卡片设置徽章标签
     * @param projectId 项目ID
     * @param text 徽章文字
     * @param color 徽章颜色 (0xAARRGGBB 格式)
     */
    override fun setProjectBadge(projectId: String, text: String, color: Long) {
        handler.post {
            PluginManager.projectBadges[projectId] = PluginManager.BadgeInfo(text, color, pluginId)
        }
    }

    /**
     * 清除项目徽章
     */
    override fun clearProjectBadge(projectId: String) {
        handler.post {
            // 只清除本插件设置的徽章
            val existing = PluginManager.projectBadges[projectId]
            if (existing != null && existing.pluginId == pluginId) {
                PluginManager.projectBadges.remove(projectId)
            }
        }
    }

    /**
     * 清除所有项目徽章
     */
    fun clearAllBadges() {
        handler.post {
            // 只清除本插件设置的徽章
            val toRemove = PluginManager.projectBadges.filter { it.value.pluginId == pluginId }.keys
            toRemove.forEach { PluginManager.projectBadges.remove(it) }
        }
    }
    
    // ============ 项目卡片上下文菜单扩展 ============
    
    /**
     * 向项目卡片的三点菜单中添加自定义菜单项
     * @param key 菜单项唯一标识
     * @param label 菜单项文字
     * @param onClick 点击回调，参数: (projectId, projectName, projectPath)
     */
    fun addProjectCardMenuItem(key: String, label: String, onClick: ProjectCardMenuCallback) {
        handler.post {
            PluginManager.projectCardMenuItems.add(
                PluginManager.ProjectCardMenuItem(key, label) { pid, pname, ppath ->
                    onClick.onItemClick(pid, pname, ppath)
                }
            )
        }
    }
    
    /**
     * 移除项目卡片菜单项
     */
    fun removeProjectCardMenuItem(key: String) {
        handler.post {
            PluginManager.projectCardMenuItems.removeIf { it.key == key }
        }
    }
    
    /**
     * 清除本插件添加的所有项目卡片菜单项
     */
    fun clearProjectCardMenuItems() {
        handler.post {
            PluginManager.projectCardMenuItems.clear()
        }
    }
    
    // ============ 项目列表数据 ============

    /**
     * 获取当前主页项目列表（返回项目信息table数组，每个元素包含 id, name, path, tags, modifiedTime, createdTime）
     * Lua侧使用方式：
     *   local projects = plugin.mainpage.getProjects()
     *   for i, p in ipairs(projects) do
     *       print(p.id, p.name, p.path)
     *       for j, tag in ipairs(p.tags) do print("  tag:", tag) end
     *   end
     */
    fun getProjects(): List<Map<String, Any?>> {
        val settings = com.luaforge.studio.lxclua.ui.settings.SettingsManager.currentSettings
        return PluginManager.currentProjectItems.map { item ->
            mapOf(
                "id" to item.id,
                "name" to item.name,
                "path" to item.path,
                "modifiedTime" to item.modifiedDate.time,
                "createdTime" to item.createdDate.time,
                "tags" to (settings.projectTagsMap[item.id]?.toList() ?: emptyList<String>())
            )
        }
    }

    /**
     * 获取当前主页项目列表（返回项目ID数组）
     */
    fun getProjectIds(): Array<String> {
        return PluginManager.currentProjectItems.map { it.id }.toTypedArray()
    }
    
    /**
     * 根据ID获取项目名称
     */
    fun getProjectName(projectId: String): String? {
        return PluginManager.currentProjectItems.find { it.id == projectId }?.name
    }
    
    /**
     * 根据ID获取项目路径
     */
    fun getProjectPath(projectId: String): String? {
        return PluginManager.currentProjectItems.find { it.id == projectId }?.path
    }
    
    /**
     * 获取项目列表总数
     */
    fun getProjectCount(): Int {
        return PluginManager.currentProjectItems.size
    }
    
    // ============ 工具栏扩展 ============
    
    /**
     * 在首页顶部工具栏末尾添加自定义动作按钮
     * @param id 按钮唯一标识（插件内唯一）
     * @param iconName Material图标名称
     * @param tooltip 按钮提示文字
     * @param onClick 点击回调
     */
    override fun addToolbarAction(id: String, iconName: String, tooltip: String, onClick: Runnable) {
        handler.post {
            UIState.addToolbarAction(pluginId, id, iconName, tooltip, onClick)
        }
    }
    
    /**
     * 移除已注册的工具栏按钮
     * @param actionId 按钮标识
     */
    override fun removeToolbarAction(actionId: String) {
        handler.post {
            UIState.removeToolbarAction(pluginId, actionId)
        }
    }
    
    // ============ FAB 扩展 ============
    
    /**
     * 在首页 FAB 区域添加自定义小浮动按钮
     * @param id 按钮唯一标识
     * @param iconName Material图标名称
     * @param tooltip 按钮提示文字
     * @param onClick 点击回调
     */
    override fun addHomeFab(id: String, iconName: String, tooltip: String, onClick: Runnable) {
        handler.post {
            UIState.addHomeFab(pluginId, id, iconName, tooltip, onClick)
        }
    }
    
    /**
     * 移除已注册的首页 FAB 按钮
     * @param fabId 按钮标识
     */
    override fun removeHomeFab(fabId: String) {
        handler.post {
            UIState.removeHomeFab(pluginId, fabId)
        }
    }
    
    // ============ 分类栏扩展 ============
    
    /**
     * 在首页分类栏末尾添加自定义分类项
     * @param id 项唯一标识
     * @param iconName Material图标名称
     * @param name 分类名称
     * @param onClick 点击回调
     */
    override fun addCategoryBarItem(id: String, iconName: String, name: String, onClick: Runnable) {
        handler.post {
            UIState.addCategoryBarItem(pluginId, id, iconName, name, onClick)
        }
    }
    
    /**
     * 移除已注册的分类栏项
     * @param itemId 项标识
     */
    override fun removeCategoryBarItem(itemId: String) {
        handler.post {
            UIState.removeCategoryBarItem(pluginId, itemId)
        }
    }
    
    // ============ 工具方法 ============
    
    /**
     * 通过 NavigationState 请求导航到指定项目（打开编辑器）
     * 注意：此方法发送导航请求，实际项目加载需要 MainActivity 监听 pendingNavTarget 并设置 selectedProject。
     * 如果 ON_PROJECT_OPEN 事件被拦截，则不执行导航。
     * @param projectId 项目ID
     */
    override fun navigateToProject(projectId: String) {
        handler.post {
            // 仅检查拦截，事件通知由实际导航完成后（onNavigateToEditor）触发
            val project = PluginManager.currentProjectItems.find { it.id == projectId }
            val projectName = project?.name ?: ""
            val projectPath = project?.path ?: ""
            val intercepted = EventManager.checkIntercepted(
                PluginEvents.ON_PROJECT_OPEN, projectId, projectName, projectPath
            )
            if (!intercepted) {
                // 通过NavigationState发送导航请求（带projectId），MainApp监听后设置selectedProject
                NavigationState.navigateToProject(projectId)
            }
        }
    }
    
    /**
     * 通知刷新项目列表（触发 ON_REFRESH_PROJECTS 事件，由主页监听）
     */
    override fun refreshProjects() {
        handler.post {
            // 使用专用事件通知刷新
            EventManager.fireEvent("onRefreshProjects")
        }
    }
    
    /**
     * 显示 Toast 提示
     * @param message 提示内容
     */
    override fun showToast(message: String) {
        handler.post {
            val ctx = PluginManager.appContext
            if (ctx != null) {
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * 项目卡片菜单项点击回调接口
 * Lua 侧实现 onItemClick(projectId, projectName, projectPath)
 */
interface ProjectCardMenuCallback {
    fun onItemClick(projectId: String, projectName: String, projectPath: String)
}
