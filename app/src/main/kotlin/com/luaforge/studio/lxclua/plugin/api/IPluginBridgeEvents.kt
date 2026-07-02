package com.luaforge.studio.lxclua.plugin.api

/**
 * 事件系统相关 API
 */
interface IPluginBridgeEvents {
    /**
     * 注册事件监听器
     * 支持的事件：
     * - onFileOpen: 文件打开时触发，参数: (filePath)
     * - onFileSave: 文件保存时触发，参数: (filePath)
     * - onFileClose: 文件关闭时触发，参数: (filePath)
     * - onTextChanged: 文本变化时触发，参数: (filePath, newContent)
     * - onEditorInit: 编辑器初始化时触发，参数: (projectPath)
     * - onEditorClose: 编辑器关闭时触发，参数: (projectPath)
     * - onProjectLongPress: 主页项目长按时触发，参数: (projectId, projectName, projectPath)
     * - onProjectClick: 主页项目点击时触发，参数: (projectId, projectName, projectPath)
     */
    fun registerEventListener(eventName: String, listener: Any)
    
    /**
     * 注册一次性事件监听器，回调触发一次后自动注销
     * @param eventName 事件名称
     * @param handler 事件处理回调，参数: (args: Array<out Any?>)
     */
    fun once(eventName: String, handler: (Array<out Any?>) -> Unit)
    
    /**
     * 注册事件拦截器
     * 拦截器在默认行为执行前被调用，返回true可以阻止默认行为。
     * 多个拦截器按priority顺序执行（数字越小优先级越高），所有拦截器都会执行（类似cancelBuild机制）。
     * @param eventName 要拦截的事件名称
     * @param priority 优先级，数字越小优先级越高，默认0
     * @param handler 拦截处理函数，参数: (args: Array<out Any?>)，返回true表示拦截（阻止默认行为），false表示放行
     */
    fun intercept(eventName: String, priority: Int, handler: (Array<out Any?>) -> Boolean)
    
    /**
     * 取消事件监听
     */
    fun unregisterEventListener(eventName: String, listener: Any)
}
