package com.luaforge.studio.lxclua.plugin.api

/**
 * 文本操作窗口（长按浮动工具栏）相关 API
 *
 * 插件可通过此接口动态注册/注销文本操作窗口的自定义按钮
 */
interface IPluginBridgeTextAction {

    /**
     * 注册文本操作窗口自定义按钮
     *
     * @param id 按钮唯一标识（不可重复）
     * @param icon 图标：内置常量名（如 "SEARCH"、"UNDO"）或自定义图片路径
     * @param label 按钮标签（图标不可用时作为备用显示）
     * @return true=注册成功，false=ID已存在或窗口未初始化
     */
    fun registerTextActionButton(id: String, icon: String, label: String): Boolean

    /**
     * 注销文本操作窗口自定义按钮
     *
     * @param id 按钮唯一标识
     * @return true=注销成功
     */
    fun unregisterTextActionButton(id: String): Boolean

    /**
     * 获取文本操作窗口已注册的自定义按钮 ID 列表
     *
     * @return 按钮 ID 数组
     */
    fun getTextActionButtons(): Array<String>

    /**
     * 获取内置图标常量表
     *
     * 返回格式：{ "ICON_NAME" = true, ... }
     * 插件可直接使用这些常量名作为 registerTextActionButton 的 icon 参数
     *
     * @return 图标名称表
     */
    fun getTextActionIcons(): Map<String, Boolean>
}