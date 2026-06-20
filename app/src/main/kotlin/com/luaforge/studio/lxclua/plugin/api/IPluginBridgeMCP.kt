package com.luaforge.studio.lxclua.plugin.api

/**
 * MCP 功能桥接接口
 *
 * 为 DEX/APK 插件提供 MCP 功能访问
 */
interface IPluginBridgeMCP {
    /** 连接到 MCP 服务器 */
    fun connectMcp(): Boolean

    /** 断开 MCP 连接 */
    fun disconnectMcp()

    /** 是否已连接 */
    fun isMcpConnected(): Boolean

    /** 列出所有可用工具（返回 JSON 字符串） */
    fun listMcpTools(): String

    /** 调用 MCP 工具 */
    fun callMcpTool(name: String, argumentsJson: String): String

    /** 列出所有可用资源（返回 JSON 字符串） */
    fun listMcpResources(): String

    /** 读取 MCP 资源 */
    fun readMcpResource(uri: String): String?

    /** 注册插件 MCP 工具 */
    fun registerMcpTool(name: String, description: String, inputSchemaJson: String): Boolean

    /** 注销插件 MCP 工具 */
    fun unregisterMcpTool(name: String)
}