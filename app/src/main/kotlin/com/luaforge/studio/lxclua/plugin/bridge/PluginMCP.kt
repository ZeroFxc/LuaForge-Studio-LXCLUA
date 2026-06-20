package com.luaforge.studio.lxclua.plugin.bridge

import android.content.Context
import com.luaforge.studio.lxclua.mcp.MCPManager
import com.luaforge.studio.lxclua.mcp.MCPTool
import com.luaforge.studio.lxclua.mcp.MCPToolCallRequest
import com.luajava.LuaFunction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 插件 MCP 桥接类
 *
 * 为 Lua 插件提供 MCP 功能访问：
 * - plugin.mcp.connect()           -- 连接到 MCP 服务器
 * - plugin.mcp.disconnect()        -- 断开连接
 * - plugin.mcp.isConnected()       -- 是否已连接
 * - plugin.mcp.listTools()         -- 列出可用工具
 * - plugin.mcp.callTool(name, args)  -- 调用工具
 * - plugin.mcp.listResources()     -- 列出可用资源
 * - plugin.mcp.readResource(uri)   -- 读取资源
 * - plugin.mcp.registerTool(...)   -- 注册插件工具到指定服务
 * - plugin.mcp.unregisterTool(name) -- 注销插件工具
 * - plugin.mcp.registerService(...) -- 注册 MCP 服务（工具组）
 * - plugin.mcp.enableService/disableService -- 启停服务
 * - plugin.mcp.enableTool/disableTool -- 启停服务内工具
 */
class PluginMCP(
    private val pluginId: String,
    private val context: Context
) {
    /** 插件日志记录器（用于将错误写入 app 日志系统） */
    private val logger: PluginLogger by lazy {
        PluginLogger(context, pluginId)
    }

    /** 记录工具执行错误到日志系统 */
    private fun logToolError(toolName: String, serviceName: String?, error: Exception) {
        val msg = if (serviceName != null) {
            "MCP 服务 [$serviceName] 工具 [$toolName] 执行异常"
        } else {
            "MCP 工具 [$toolName] 执行异常"
        }
        android.util.Log.e("PluginMCP", msg, error)
        logger.error("PluginMCP", msg, error.message)
    }

    /** 连接到 MCP 服务器 */
    fun connect(): Boolean {
        return runBlocking { MCPManager.service.connect() }
    }

    /** 异步连接 */
    fun connectAsync(callback: LuaFunction<*>) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = MCPManager.service.connect()
            safeCallLua(callback, result)
        }
    }

    /** 断开连接 */
    fun disconnect() {
        runBlocking { MCPManager.service.disconnect() }
    }

    /** 是否已连接 */
    fun isConnected(): Boolean = MCPManager.service.isConnected

    /** 列出所有可用工具 */
    fun listTools(): List<Map<String, Any>> {
        return runBlocking {
            MCPManager.service.listTools().map { tool ->
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "inputSchema" to tool.inputSchema
                )
            }
        }
    }

    /** 异步列出工具 */
    fun listToolsAsync(callback: LuaFunction<*>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tools = MCPManager.service.listTools()
                val result = tools.map { tool ->
                    mapOf(
                        "name" to tool.name,
                        "description" to tool.description,
                        "inputSchema" to tool.inputSchema
                    )
                }
                safeCallLua(callback, true, result)
            } catch (e: Exception) {
                safeCallLua(callback, false, emptyList<Map<String, Any>>(), e.message ?: "")
            }
        }
    }

    /** 调用工具 */
    fun callTool(name: String, args: Map<String, Any>): Map<String, Any> {
        return runBlocking {
            val result = MCPManager.service.callTool(MCPToolCallRequest(name, args))
            mapOf(
                "success" to result.success,
                "content" to result.content.map { c ->
                    mapOf(
                        "type" to c.type,
                        "text" to (c.text ?: ""),
                        "data" to (c.data ?: ""),
                        "mimeType" to (c.mimeType ?: "")
                    )
                },
                "error" to (result.error ?: "")
            )
        }
    }

    /** 异步调用工具 */
    fun callToolAsync(name: String, args: Map<String, Any>, callback: LuaFunction<*>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = MCPManager.service.callTool(MCPToolCallRequest(name, args))
                safeCallLua(
                    callback,
                    result.success,
                    result.content.map { c ->
                        mapOf(
                            "type" to c.type,
                            "text" to (c.text ?: ""),
                            "data" to (c.data ?: ""),
                            "mimeType" to (c.mimeType ?: "")
                        )
                    },
                    result.error ?: ""
                )
            } catch (e: Exception) {
                safeCallLua(callback, false, emptyList<Map<String, Any>>(), e.message ?: "")
            }
        }
    }

    /** 列出所有可用资源 */
    fun listResources(): List<Map<String, String>> {
        return runBlocking {
            MCPManager.service.listResources().map { res ->
                mapOf(
                    "uri" to res.uri,
                    "name" to res.name,
                    "description" to res.description,
                    "mimeType" to res.mimeType
                )
            }
        }
    }

    /** 读取资源 */
    fun readResource(uri: String): String? {
        return runBlocking { MCPManager.service.readResource(uri) }
    }

    /**
     * 注册插件工具到指定服务（供其他 MCP 客户端调用）
     * 工具必须属于某个服务，未指定则归入默认 "default" 服务
     * @param name 工具名称
     * @param description 工具描述
     * @param inputSchema 输入参数 schema
     * @param handler Lua 处理函数
     * @param serviceName 所属服务名（可选，默认 "default"）
     */
    @JvmOverloads
    fun registerTool(
        name: String,
        description: String,
        inputSchema: Map<String, Any>,
        handler: LuaFunction<*>,
        serviceName: String = "default"
    ) {
        @Suppress("UNCHECKED_CAST")
        val h = handler as LuaFunction<Any>
        val tool = MCPTool(name, description, inputSchema)
        MCPManager.registerPluginTool(tool) { args ->
            try {
                val result = h.call(args)
                result?.toString() ?: ""
            } catch (e: Exception) {
                logToolError(name, serviceName, e)
                "插件工具执行错误: ${e.message}"
            }
        }
        MCPManager.registerToolToService(serviceName, tool)
    }

    /** 注销插件工具 */
    fun unregisterTool(name: String) {
        MCPManager.unregisterPluginTool(name)
    }

    /** 获取已注册的插件工具 */
    fun getPluginTools(): List<Map<String, Any>> {
        return MCPManager.getPluginTools().map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "inputSchema" to tool.inputSchema
            )
        }
    }

    // ========== MCP 服务管理 ==========

    /**
     * 注册一个 MCP 服务（工具组）
     * @param serviceName 服务唯一标识
     * @param serviceLabel 服务显示名称
     * @param tools 工具列表，每个工具包含 name, description, inputSchema, handler
     * @return 实际注册的工具数量
     */
    fun registerService(
        serviceName: String,
        serviceLabel: String,
        tools: List<Map<String, Any>>
    ): Int {
        val toolDefs = mutableListOf<MCPTool>()
        for (tool in tools) {
            val name = tool["name"] as? String ?: continue
            val desc = tool["description"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val schema = (tool["inputSchema"] as? Map<String, Any>) ?: emptyMap()
            val handler = tool["handler"] as? LuaFunction<*> ?: continue
            @Suppress("UNCHECKED_CAST")
            val h = handler as LuaFunction<Any>

            val toolDef = MCPTool(name, desc, schema)
            toolDefs.add(toolDef)
            MCPManager.registerPluginTool(toolDef) { args ->
                try {
                    val result = h.call(args)
                    result?.toString() ?: ""
                } catch (e: Exception) {
                    logToolError(name, serviceName, e)
                    "插件工具执行错误: ${e.message}"
                }
            }
        }
        MCPManager.registerService(serviceName, serviceLabel, toolDefs)
        // 添加到全局配置列表，使 UI 的 MCP 服务列表可见
        android.util.Log.i("PluginMCP", "[registerService] 插件 [$pluginId] 注册服务: $serviceName ($serviceLabel), 工具数: ${toolDefs.size}")
        MCPManager.addServiceToConfig(serviceName, serviceLabel)
        return toolDefs.size
    }

    /** 启用指定 MCP 服务（启用后该服务下所有工具可用） */
    fun enableService(serviceName: String): Boolean {
        return MCPManager.enableService(serviceName)
    }

    /** 禁用指定 MCP 服务（禁用后该服务下所有工具不可用） */
    fun disableService(serviceName: String): Boolean {
        return MCPManager.disableService(serviceName)
    }

    /** 启用服务中的指定工具 */
    fun enableTool(serviceName: String, toolName: String): Boolean {
        return MCPManager.enableServiceTool(serviceName, toolName)
    }

    /** 禁用服务中的指定工具 */
    fun disableTool(serviceName: String, toolName: String): Boolean {
        return MCPManager.disableServiceTool(serviceName, toolName)
    }

    /** 获取服务状态（包含服务启用状态和各工具状态） */
    fun getServiceStatus(serviceName: String): Map<String, Any>? {
        return MCPManager.getServiceStatus(serviceName)
    }

    /** 列出所有已注册的 MCP 服务 */
    fun listServices(): List<Map<String, Any>> {
        return MCPManager.listServices()
    }

    /** 注销 MCP 服务（同时注销该服务下所有工具） */
    fun unregisterService(serviceName: String) {
        MCPManager.unregisterService(serviceName)
        MCPManager.removeServiceFromConfig(serviceName)
    }

    /**
     * 获取所有启用服务中的启用工具列表（供 AI 使用）
     * 只返回服务启用且工具也启用的工具
     */
    fun getEnabledServiceTools(): List<Map<String, Any>> {
        return MCPManager.getEnabledServiceTools().map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "inputSchema" to tool.inputSchema
            )
        }
    }

    /**
     * 获取服务层级结构（供 UI 展示）
     * 返回 [{name, label, enabled, source, toolCount, tools: [{name, description, enabled}]}]
     */
    fun getServiceHierarchy(): List<Map<String, Any>> {
        return MCPManager.getServiceHierarchy()
    }
}