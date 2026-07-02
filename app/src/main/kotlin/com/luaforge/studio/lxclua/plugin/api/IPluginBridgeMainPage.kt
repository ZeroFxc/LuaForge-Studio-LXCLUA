package com.luaforge.studio.lxclua.plugin.api

/**
 * 首页（MainPage）UI 扩展与操作 API
 * 
 * 提供插件在首页注册工具栏按钮、FAB 按钮、分类栏项、项目徽章的能力，
 * 以及项目导航、刷新、Toast、多选模式等操作。
 */
interface IPluginBridgeMainPage {
    
    // ==================== 工具栏扩展 ====================
    
    /**
     * 在首页顶部工具栏末尾添加自定义动作按钮
     * @param id 按钮唯一标识（插件内唯一，自动加 pluginId 前缀）
     * @param iconName Material 图标名称（从 MaterialIconMap 获取）
     * @param tooltip 按钮提示文字
     * @param onClick 点击回调
     */
    fun addToolbarAction(id: String, iconName: String, tooltip: String, onClick: Runnable)
    
    /**
     * 移除已注册的工具栏按钮
     * @param actionId 按钮标识（与 addToolbarAction 中的 id 一致）
     */
    fun removeToolbarAction(actionId: String)
    
    // ==================== FAB 扩展 ====================
    
    /**
     * 在首页 FAB 区域添加自定义小浮动按钮（SmallFloatingActionButton）
     * @param id 按钮唯一标识（插件内唯一）
     * @param iconName Material 图标名称
     * @param tooltip 按钮提示文字
     * @param onClick 点击回调
     */
    fun addHomeFab(id: String, iconName: String, tooltip: String, onClick: Runnable)
    
    /**
     * 移除已注册的首页 FAB 按钮
     * @param fabId 按钮标识
     */
    fun removeHomeFab(fabId: String)
    
    // ==================== 分类栏扩展 ====================
    
    /**
     * 在首页分类栏末尾添加自定义分类项
     * @param id 项唯一标识（插件内唯一）
     * @param iconName Material 图标名称
     * @param name 分类名称
     * @param onClick 点击回调
     */
    fun addCategoryBarItem(id: String, iconName: String, name: String, onClick: Runnable)
    
    /**
     * 移除已注册的分类栏项
     * @param itemId 项标识
     */
    fun removeCategoryBarItem(itemId: String)
    
    // ==================== 项目徽章 ====================
    
    /**
     * 为指定项目设置自定义徽章（显示在项目卡片右上角）
     * @param projectId 项目ID
     * @param text 徽章文字
     * @param color 徽章颜色（ARGB Long值，如0xFF4CAF50）
     */
    fun setProjectBadge(projectId: String, text: String, color: Long)
    
    /**
     * 清除指定项目的自定义徽章
     * @param projectId 项目ID
     */
    fun clearProjectBadge(projectId: String)
    
    // ==================== 工具方法 ====================
    
    /**
     * 请求导航到指定项目（打开编辑器）
     * @param projectId 项目ID
     */
    fun navigateToProject(projectId: String)
    
    /**
     * 通知刷新项目列表
     */
    fun refreshProjects()
    
    /**
     * 显示 Toast 提示
     * @param message 提示内容
     */
    fun showToast(message: String)
    
    /**
     * 获取当前多选模式下选中的项目ID列表
     * @return 选中的项目ID数组
     */
    fun getSelectedProjectIds(): Array<String>
    
    /**
     * 设置多选模式
     * @param enabled true 进入多选模式，false 退出
     */
    fun setMultiSelectMode(enabled: Boolean)
}
