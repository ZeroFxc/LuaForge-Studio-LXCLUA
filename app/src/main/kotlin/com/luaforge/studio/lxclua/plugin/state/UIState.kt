package com.luaforge.studio.lxclua.plugin.state

import androidx.compose.runtime.mutableStateListOf

/**
 * UI 状态管理
 * 负责管理插件注册的菜单项、文件树菜单项、首页工具栏/FAB/分类栏扩展等 UI 元素
 */
object UIState {
    
    // ==================== 插件菜单项 ====================
    
    sealed class PluginMenuItem {
        abstract val key: String
        abstract val pluginId: String
        
        data class Item(
            override val key: String,
            override val pluginId: String,
            val label: String,
            val onClick: Runnable
        ) : PluginMenuItem()
        
        data class Divider(
            override val key: String,
            override val pluginId: String
        ) : PluginMenuItem()
    }
    
    val pluginMenuItems = mutableStateListOf<PluginMenuItem>()
    
    fun addPluginMenuItem(pluginId: String, key: String, label: String, onClick: Runnable) {
        val globalKey = "${pluginId}_$key"
        val existingIndex = pluginMenuItems.indexOfFirst { it.key == globalKey }
        val newItem = PluginMenuItem.Item(globalKey, pluginId, label, onClick)
        
        if (existingIndex >= 0) {
            pluginMenuItems[existingIndex] = newItem
        } else {
            pluginMenuItems.add(newItem)
        }
    }
    
    fun addPluginMenuDivider(pluginId: String, key: String) {
        val globalKey = "${pluginId}_$key"
        val existingIndex = pluginMenuItems.indexOfFirst { it.key == globalKey }
        val newDivider = PluginMenuItem.Divider(globalKey, pluginId)
        
        if (existingIndex >= 0) {
            pluginMenuItems[existingIndex] = newDivider
        } else {
            pluginMenuItems.add(newDivider)
        }
    }
    
    fun removePluginMenuItem(pluginId: String, key: String) {
        val globalKey = "${pluginId}_$key"
        pluginMenuItems.removeAll { it.key == globalKey }
    }
    
    fun removePluginMenuItems(pluginId: String) {
        pluginMenuItems.removeAll { it.pluginId == pluginId }
    }
    
    // ==================== 文件树菜单项 ====================
    
    sealed class FileTreeMenuItem {
        abstract val key: String
        abstract val pluginId: String
        abstract val filter: String?
        
        data class Item(
            override val key: String,
            override val pluginId: String,
            val label: String,
            val iconName: String?,
            override val filter: String?,
            val onClick: (String, Boolean) -> Unit
        ) : FileTreeMenuItem()
        
        data class Divider(
            override val key: String,
            override val pluginId: String,
            override val filter: String?
        ) : FileTreeMenuItem()
    }
    
    val fileTreeMenuItems = mutableStateListOf<FileTreeMenuItem>()
    
    fun addFileTreeMenuItem(pluginId: String, key: String, label: String, iconName: String?, filter: String?, onClick: (String, Boolean) -> Unit) {
        val globalKey = "${pluginId}_$key"
        val existingIndex = fileTreeMenuItems.indexOfFirst { it.key == globalKey }
        val newItem = FileTreeMenuItem.Item(globalKey, pluginId, label, iconName, filter, onClick)
        
        if (existingIndex >= 0) {
            fileTreeMenuItems[existingIndex] = newItem
        } else {
            fileTreeMenuItems.add(newItem)
        }
    }
    
    fun addFileTreeMenuDivider(pluginId: String, key: String, filter: String?) {
        val globalKey = "${pluginId}_$key"
        val existingIndex = fileTreeMenuItems.indexOfFirst { it.key == globalKey }
        val newDivider = FileTreeMenuItem.Divider(globalKey, pluginId, filter)
        
        if (existingIndex >= 0) {
            fileTreeMenuItems[existingIndex] = newDivider
        } else {
            fileTreeMenuItems.add(newDivider)
        }
    }
    
    fun removeFileTreeMenuItem(pluginId: String, key: String) {
        val globalKey = "${pluginId}_$key"
        fileTreeMenuItems.removeAll { it.key == globalKey }
    }
    
    fun removeFileTreeMenuItems(pluginId: String) {
        fileTreeMenuItems.removeAll { it.pluginId == pluginId }
    }
    
    // ==================== 首页顶部工具栏扩展按钮 ====================
    
    /**
     * 首页顶部工具栏动作按钮
     * @property id 按钮唯一标识（全局，自动加pluginId前缀）
     * @property pluginId 所属插件ID
     * @property iconName Material图标名称（从 MaterialIconMap 获取）
     * @property tooltip 按钮提示文字
     * @property onClick 点击回调
     */
    data class ToolbarAction(
        val id: String,
        val pluginId: String,
        val iconName: String,
        val tooltip: String,
        val onClick: Runnable
    )
    
    val toolbarActions = mutableStateListOf<ToolbarAction>()
    
    /**
     * 添加首页顶部工具栏按钮
     * @param pluginId 插件ID
     * @param id 按钮唯一标识（插件内唯一）
     * @param iconName Material图标名称
     * @param tooltip 按钮提示文字
     * @param onClick 点击回调
     */
    fun addToolbarAction(pluginId: String, id: String, iconName: String, tooltip: String, onClick: Runnable) {
        val globalId = "${pluginId}_$id"
        val existingIndex = toolbarActions.indexOfFirst { it.id == globalId }
        val action = ToolbarAction(globalId, pluginId, iconName, tooltip, onClick)
        if (existingIndex >= 0) {
            toolbarActions[existingIndex] = action
        } else {
            toolbarActions.add(action)
        }
    }
    
    /**
     * 移除首页顶部工具栏按钮
     */
    fun removeToolbarAction(pluginId: String, id: String) {
        val globalId = "${pluginId}_$id"
        toolbarActions.removeAll { it.id == globalId }
    }
    
    // ==================== 首页FAB扩展按钮 ====================
    
    /**
     * 首页浮动操作按钮扩展项（SmallFloatingActionButton）
     * @property id 按钮唯一标识
     * @property pluginId 所属插件ID
     * @property iconName Material图标名称
     * @property tooltip 按钮提示文字
     * @property onClick 点击回调
     */
    data class HomeFabItem(
        val id: String,
        val pluginId: String,
        val iconName: String,
        val tooltip: String,
        val onClick: Runnable
    )
    
    val homeFabs = mutableStateListOf<HomeFabItem>()
    
    /**
     * 添加首页浮动操作按钮
     * @param pluginId 插件ID
     * @param id 按钮唯一标识（插件内唯一）
     * @param iconName Material图标名称
     * @param tooltip 按钮提示文字
     * @param onClick 点击回调
     */
    fun addHomeFab(pluginId: String, id: String, iconName: String, tooltip: String, onClick: Runnable) {
        val globalId = "${pluginId}_$id"
        val existingIndex = homeFabs.indexOfFirst { it.id == globalId }
        val item = HomeFabItem(globalId, pluginId, iconName, tooltip, onClick)
        if (existingIndex >= 0) {
            homeFabs[existingIndex] = item
        } else {
            homeFabs.add(item)
        }
    }
    
    /**
     * 移除首页浮动操作按钮
     */
    fun removeHomeFab(pluginId: String, id: String) {
        val globalId = "${pluginId}_$id"
        homeFabs.removeAll { it.id == globalId }
    }
    
    // ==================== 首页分类栏扩展项 ====================
    
    /**
     * 首页分类栏扩展项
     * @property id 项唯一标识
     * @property pluginId 所属插件ID
     * @property name 分类名称
     * @property iconName Material图标名称
     * @property onClick 点击回调
     */
    data class CategoryBarItem(
        val id: String,
        val pluginId: String,
        val name: String,
        val iconName: String,
        val onClick: Runnable
    )
    
    val categoryBarItems = mutableStateListOf<CategoryBarItem>()
    
    /**
     * 添加首页分类栏项
     * @param pluginId 插件ID
     * @param id 项唯一标识（插件内唯一）
     * @param name 分类名称
     * @param iconName Material图标名称
     * @param onClick 点击回调
     */
    fun addCategoryBarItem(pluginId: String, id: String, iconName: String, name: String, onClick: Runnable) {
        val globalId = "${pluginId}_$id"
        val existingIndex = categoryBarItems.indexOfFirst { it.id == globalId }
        val item = CategoryBarItem(globalId, pluginId, name, iconName, onClick)
        if (existingIndex >= 0) {
            categoryBarItems[existingIndex] = item
        } else {
            categoryBarItems.add(item)
        }
    }
    
    /**
     * 移除首页分类栏项
     */
    fun removeCategoryBarItem(pluginId: String, id: String) {
        val globalId = "${pluginId}_$id"
        categoryBarItems.removeAll { it.id == globalId }
    }
    
    // ==================== 插件清理 ====================
    
    /**
     * 移除指定插件注册的所有UI扩展元素（插件卸载时调用）
     * @param pluginId 插件ID
     */
    fun removePluginUI(pluginId: String) {
        removePluginMenuItems(pluginId)
        removeFileTreeMenuItems(pluginId)
        toolbarActions.removeAll { it.pluginId == pluginId }
        homeFabs.removeAll { it.pluginId == pluginId }
        categoryBarItems.removeAll { it.pluginId == pluginId }
    }
}
