package com.luaforge.studio.lxclua.plugin.api

/**
 * 提供给插件调用的 Host API 桥梁
 * 
 * 整合所有子接口，提供统一的 API 访问入口
 */
interface IPluginBridge :
    IPluginBridgeDialogs,
    IPluginBridgeEditor,
    IPluginBridgeProject,
    IPluginBridgeUI,
    IPluginBridgeClipboard,
    IPluginBridgeConfig,
    IPluginBridgeEvents,
    IPluginBridgeThreads,
    IPluginBridgeNetwork,
    IPluginBridgeReflection,
    IPluginBridgeDex,
    IPluginBridgeResources,
    IPluginBridgeLua,
    IPluginBridgeLogger,
    IPluginBridgeResourceRegistry,
    IPluginBridgeShortcut,
    IPluginBridgeCompletion,
    IPluginBridgeSymbolBar,
    IPluginBridgeSyntax,
    IPluginBridgeDecoration,
    IPluginBridgeBuild,
    IPluginBridgeWebUI,
    IPluginBridgeAI,
    IPluginBridgeMCP,
    IPluginBridgeFloating,
    IPluginBridgeSystem {
    
    // ==================== 基础功能 ====================
    
    /**
     * 显示 Toast 提示
     */
    fun toast(message: String)
    
    /**
     * 显示长时 Toast 提示
     */
    fun toastLong(message: String)
    
    /**
     * 输出日志
     */
    fun log(tag: String, message: String)
    
    /**
     * 获取宿主应用版本号
     */
    fun getVersion(): String
    
    /**
     * 获取插件数据存储目录
     */
    fun getDataDir(): String
    
    /**
     * 获取 Android Context
     */
    fun getContext(): android.content.Context
}