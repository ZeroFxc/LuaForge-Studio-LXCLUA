package com.luaforge.studio.lxclua.mcp

import com.luaforge.studio.lxclua.ai.AIConfigManager
import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/** MCP 工具定义 */
data class MCPTool(
    val name: String,
    val description: String = "",
    val inputSchema: Map<String, Any> = emptyMap()
)

/** MCP 资源定义 */
data class MCPResource(
    val uri: String,
    val name: String,
    val description: String = "",
    val mimeType: String = "text/plain"
)

/** MCP 工具调用请求 */
data class MCPToolCallRequest(
    val toolName: String,
    val arguments: Map<String, Any> = emptyMap()
)

/** MCP 工具调用结果 */
data class MCPToolCallResult(
    val success: Boolean,
    val content: List<MCPContent> = emptyList(),
    val error: String? = null
)

/** MCP 内容块 */
data class MCPContent(
    val type: String = "text",  // "text" / "image" / "resource"
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

/** MCP 服务中的工具状态 */
data class MCPToolState(
    val name: String,
    val enabled: Boolean = true
)

/** MCP 服务信息（工具组） */
data class MCPServiceInfo(
    val name: String,
    val label: String,
    val enabled: Boolean = true,
    val tools: List<MCPTool> = emptyList(),
    /** 工具名 -> 工具状态 */
    val toolStates: MutableMap<String, MCPToolState> = mutableMapOf()
)

/** MCP 服务接口 */
interface IMCPService {
    /** 连接到 MCP 服务器 */
    suspend fun connect(): Boolean

    /** 断开连接 */
    suspend fun disconnect()

    /** 是否已连接 */
    val isConnected: Boolean

    /** 列出所有可用工具 */
    suspend fun listTools(): List<MCPTool>

    /** 调用工具 */
    suspend fun callTool(request: MCPToolCallRequest): MCPToolCallResult

    /** 列出所有可用资源 */
    suspend fun listResources(): List<MCPResource>

    /** 读取资源 */
    suspend fun readResource(uri: String): String?
}

/** MCP 传输类型 */
enum class MCPTransport(val value: String) {
    STREAMABLE_HTTP("streamable_http"),
    SSE("sse");

    companion object {
        fun fromString(s: String?): MCPTransport = when (s?.lowercase()) {
            "sse" -> SSE
            else -> STREAMABLE_HTTP
        }
    }
}

/** 基于官方 MCP Kotlin SDK 的服务实现（使用 SDK 内置 Transport） */
class MCPServiceImpl(
    private val serverUrl: String? = null,
    private val serverTransport: String? = null
) : IMCPService {

    private val ktorClient = HttpClient()

    private var sdkClient: Client? = null
    private var transport: Transport? = null

    private val transportType: MCPTransport
        get() = MCPTransport.fromString(serverTransport)

    override var isConnected: Boolean = false
        private set

    private val config get() = AIConfigManager.currentConfig

    private val baseUrl: String
        get() = serverUrl?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            ?: config.mcpEndpoint.trimEnd('/')

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 开头就捕获 URL，避免 get() 属性在后续被修改
            
            val url = baseUrl
            android.util.Log.i("MCPService", "正在连接 MCP 服务器: $url, 传输类型: ${transportType.value}")

            // 根据传输类型创建 SDK 内置 Transport
            val t = when (transportType) {
                MCPTransport.SSE -> {
                    android.util.Log.i("MCPService", "使用 SDK 内置 SSE 传输: $url")
                    SseClientTransport(ktorClient, url, null)
                }
                MCPTransport.STREAMABLE_HTTP -> {
                    android.util.Log.i("MCPService", "使用 SDK 内置 Streamable HTTP 传输: $url")
                    StreamableHttpClientTransport(ktorClient, url)
                }
            }
            transport = t

            // 创建 Client 并连接
            val client = Client(
                clientInfo = Implementation(name = "LXC-LUA", version = "1.1.3")
            )
            sdkClient = client
            client.connect(t)
            isConnected = true
            android.util.Log.i("MCPService", "MCP 连接成功！协议版本: ${client.serverCapabilities}")

            // 获取工具列表验证
            val toolsResult = client.listTools()
            val tools = toolsResult?.tools ?: emptyList()
            android.util.Log.i("MCPService", "获取到 ${tools.size} 个工具: ${tools.map { it.name }}")
            if (tools.isEmpty()) {
                android.util.Log.w("MCPService", "工具列表为空！可能原因: 1) 服务器未声明 tools 能力 2) 服务器未注册工具")
            }

            return@withContext true
        } catch (e: Exception) {
            android.util.Log.e("MCPService", "MCP 连接失败 (url=$baseUrl): ${e.message}", e)
            isConnected = false
            return@withContext false
        }
    }

    override suspend fun disconnect() {
        android.util.Log.d("MCPService", "断开 MCP 连接")
        try {
            sdkClient?.close()
        } catch (e: Exception) {
            android.util.Log.e("MCPService", "断开连接异常: ${e.message}", e)
        }
        sdkClient = null
        transport = null
        isConnected = false
    }

    override suspend fun listTools(): List<MCPTool> = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext emptyList()
        try {
            val client = sdkClient ?: return@withContext emptyList()
            val result = client.listTools() ?: return@withContext emptyList()
            result.tools.map { sdkTool ->
                MCPTool(
                    name = sdkTool.name,
                    description = sdkTool.description ?: "",
                    inputSchema = sdkToolToSchema(sdkTool)
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("MCPService", "listTools 异常: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun callTool(request: MCPToolCallRequest): MCPToolCallResult = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext MCPToolCallResult(false, error = "MCP 未连接")
        try {
            val client = sdkClient ?: return@withContext MCPToolCallResult(false, error = "MCP 客户端未初始化")

            // 转换参数为 JsonObject
            val jsonArgs = request.arguments.entries.associate {
                it.key to JsonPrimitive(it.value.toString())
            }.let { JsonObject(it) }

            val result = client.callTool(
                CallToolRequest(
                    params = CallToolRequestParams(
                        name = request.toolName,
                        arguments = jsonArgs
                    )
                )
            )
            val contentList = result?.content?.map { contentItem ->
                when (contentItem) {
                    is TextContent -> MCPContent(
                        type = "text",
                        text = contentItem.text,
                        data = null,
                        mimeType = null
                    )
                    is ImageContent -> MCPContent(
                        type = "image",
                        text = null,
                        data = contentItem.data,
                        mimeType = contentItem.mimeType
                    )
                    else -> MCPContent(
                        type = contentItem.type.value,
                        text = null,
                        data = null,
                        mimeType = null
                    )
                }
            } ?: emptyList()
            MCPToolCallResult(true, content = contentList)
        } catch (e: Exception) {
            android.util.Log.e("MCPService", "callTool 异常: ${e.message}", e)
            MCPToolCallResult(false, error = e.message ?: "调用失败")
        }
    }

    override suspend fun listResources(): List<MCPResource> = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext emptyList()
        try {
            val client = sdkClient ?: return@withContext emptyList()
            val result = client.listResources() ?: return@withContext emptyList()
            result.resources.map { res ->
                MCPResource(
                    uri = res.uri.toString(),
                    name = res.name ?: "",
                    description = res.description ?: "",
                    mimeType = res.mimeType ?: "text/plain"
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("MCPService", "listResources 异常: ${e.message}")
            emptyList()
        }
    }

    override suspend fun readResource(uri: String): String? = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext null
        try {
            val client = sdkClient ?: return@withContext null
            val result = client.readResource(
                io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest(
                    io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams(uri)
                )
            )
            val contents = result?.contents ?: return@withContext null
            if (contents.isNotEmpty()) {
                when (val first = contents.first()) {
                    is TextResourceContents -> first.text
                    else -> null
                }
            } else null
        } catch (e: Exception) {
            android.util.Log.w("MCPService", "readResource 异常: ${e.message}")
            null
        }
    }

    /**
     * 将 SDK 的 ToolSchema 转换为 Map
     * SDK 0.13.0 的 Tool.inputSchema 是 ToolSchema 类型，包含 properties(JsonObject)、required 等
     */
    private fun sdkToolToSchema(tool: Tool): Map<String, Any> {
        return try {
            val schema = tool.inputSchema
            val result = mutableMapOf<String, Any>()
            result["type"] = schema.type
            schema.schema?.let { result["\$schema"] = it }
            schema.required?.let { result["required"] = it }
            schema.properties?.let { props ->
                result["properties"] = jsonElementToMap(props)
            }
            result
        } catch (e: Exception) {
            android.util.Log.w("MCPService", "sdkToolToSchema 转换异常: ${e.message}")
            emptyMap()
        }
    }

    /** kotlinx.serialization JsonElement 递归转 Map */
    private fun jsonElementToMap(element: kotlinx.serialization.json.JsonElement): Any {
        return when (element) {
            is kotlinx.serialization.json.JsonObject -> {
                val map = mutableMapOf<String, Any>()
                element.forEach { (key, value) ->
                    map[key] = jsonElementToMap(value)
                }
                map
            }
            is kotlinx.serialization.json.JsonArray -> {
                element.map { jsonElementToMap(it) }
            }
            is kotlinx.serialization.json.JsonPrimitive -> {
                val primitive = element
                when {
                    primitive.isString -> primitive.content
                    primitive.content == "true" -> true
                    primitive.content == "false" -> false
                    primitive.content == "null" -> null
                    else -> primitive.content.toDoubleOrNull() ?: primitive.content
                } ?: primitive.content
            }
            else -> element.toString()
        }
    }
}

/** MCP 管理器单例 — 管理多个 MCP 服务器连接 */
object MCPManager {
    private var _service: IMCPService? = null

    /** 当前 MCP 服务实例（兼容旧 API，使用第一个远程服务器） */
    val service: IMCPService
        get() {
            if (_service == null) {
                _service = MCPServiceImpl()
            }
            return _service!!
        }

    /** 重新初始化服务 */
    fun refresh() {
        _service = MCPServiceImpl()
        // 同时刷新所有多服务器实例
        refreshAllServers()
    }

    // ========== 多服务器管理 ==========

    /** 多服务器实例映射: serverId -> MCP 服务实例 */
    private val serverInstances = ConcurrentHashMap<String, IMCPService>()

    /** 服务器连接状态: serverId -> isConnected */
    private val serverConnectionStates = ConcurrentHashMap<String, Boolean>()

    /** 服务器工具缓存: serverId -> tools */
    private val serverToolsCache = ConcurrentHashMap<String, List<MCPTool>>()

    /** 获取或创建指定服务器的服务实例 */
    private fun getServerService(entry: MCPServerEntry): IMCPService {
        return serverInstances.getOrPut(entry.id) {
            MCPServiceImpl(entry.url, entry.transport)
        }
    }

    /** 连接指定服务器 */
    suspend fun connectServer(entry: MCPServerEntry): Boolean {
        if (entry.source == MCPServerSource.LOCAL_PLUGIN) return true // 本地插件不需要连接
        try {
            val svc = getServerService(entry)
            val result = svc.connect()
            serverConnectionStates[entry.id] = result
            if (result) {
                // 缓存工具列表
                val tools = svc.listTools()
                serverToolsCache[entry.id] = tools
            }
            return result
        } catch (e: Exception) {
            android.util.Log.e("MCPManager", "连接服务器 ${entry.name} 失败", e)
            serverConnectionStates[entry.id] = false
            return false
        }
    }

    /** 断开指定服务器 */
    suspend fun disconnectServer(entry: MCPServerEntry) {
        serverInstances[entry.id]?.disconnect()
        serverConnectionStates[entry.id] = false
        serverToolsCache.remove(entry.id)
    }

    /** 获取所有已启用的 MCP 服务器（从配置 + 本地插件） */
    fun getEnabledServers(): List<MCPServerEntry> {
        val configServers = com.luaforge.studio.lxclua.ai.AIConfigManager.currentConfig.mcpServers
            .filter { it.enabled }
        // 追加本地插件注册的服务器
        val localPluginServers = pluginServerEntries.values.toList()
        return configServers + localPluginServers
    }

    /** 获取所有服务器的工具列表（合并） */
    suspend fun getAllTools(): List<MCPTool> {
        val allTools = mutableListOf<MCPTool>()
        val servers = getEnabledServers()
        for (entry in servers) {
            if (entry.source == MCPServerSource.LOCAL_PLUGIN) {
                // 本地插件工具
                val pluginTools = getPluginToolsForServer(entry.id)
                allTools.addAll(pluginTools)
            } else {
                // 远程服务器工具
                val cached = serverToolsCache[entry.id]
                if (cached != null) {
                    allTools.addAll(cached)
                } else {
                    val svc = getServerService(entry)
                    if (svc.isConnected || connectServer(entry)) {
                        val tools = svc.listTools()
                        serverToolsCache[entry.id] = tools
                        allTools.addAll(tools)
                    }
                }
            }
        }
        return allTools
    }

    /** 调用工具（自动路由到正确的服务器） */
    suspend fun callToolAnywhere(toolName: String, args: Map<String, Any>): MCPToolCallResult {
        // 先检查本地插件工具
        val pluginResult = callPluginTool(toolName, args)
        if (pluginResult != null) {
            return MCPToolCallResult(true, listOf(MCPContent(text = pluginResult)))
        }
        // 再检查远程服务器
        for ((serverId, _) in serverInstances) {
            val cached = serverToolsCache[serverId] ?: continue
            if (cached.any { it.name == toolName }) {
                val svc = serverInstances[serverId] ?: continue
                return svc.callTool(MCPToolCallRequest(toolName, args))
            }
        }
        return MCPToolCallResult(false, error = "未找到工具: $toolName")
    }

    /** 刷新所有服务器（配置变更后调用） */
    fun refreshAllServers() {
        serverInstances.clear()
        serverConnectionStates.clear()
        serverToolsCache.clear()
        pluginServerEntries.clear()
        pluginTools.clear()
        pluginToolHandlers.clear()
    }

    // ========== 动态注册的工具（由插件提供） ==========

    private val pluginTools = ConcurrentHashMap<String, MCPTool>()
    private val pluginToolHandlers = ConcurrentHashMap<String, suspend (Map<String, Any>) -> String>()
    /** 插件注册的服务器条目（按插件 ID 分组） */
    private val pluginServerEntries = ConcurrentHashMap<String, MCPServerEntry>()

    // ========== MCP 服务注册表（工具组管理） ==========

    /** 服务状态：服务名 -> 服务信息 */
    private val serviceRegistry = ConcurrentHashMap<String, MCPServiceInfo>()

    /** 工具到服务的反向映射：工具名 -> 服务名 */
    private val toolToService = ConcurrentHashMap<String, String>()

    /** 注册插件工具 */
    fun registerPluginTool(tool: MCPTool, handler: suspend (Map<String, Any>) -> String) {
        pluginTools[tool.name] = tool
        pluginToolHandlers[tool.name] = handler
    }

    /** 注册插件 MCP 服务器（自动添加到配置列表） */
    fun registerPluginServer(pluginId: String, pluginName: String, tools: List<MCPTool>) {
        val entry = MCPServerEntry(
            id = "plugin_$pluginId",
            name = pluginName,
            source = MCPServerSource.LOCAL_PLUGIN,
            pluginId = pluginId,
            enabled = true
        )
        pluginServerEntries[pluginId] = entry
        tools.forEach { tool ->
            pluginTools[tool.name] = tool
        }
        // 自动添加到全局配置的 MCP 服务器列表
        addPluginServerToConfig(entry)
    }

    /** 注销插件 MCP 服务器（同时从配置列表移除） */
    fun unregisterPluginServer(pluginId: String) {
        val entry = pluginServerEntries.remove(pluginId) ?: return
        // 从配置列表中移除
        removePluginServerFromConfig(entry.id)
        // 清理该插件的工具
        val toolNames = pluginTools.entries
            .filter { it.value.name.startsWith(pluginId) || pluginToolHandlers[it.key] != null }
            .map { it.key }
            .toList()
        toolNames.forEach { pluginTools.remove(it) }
    }

    /** 将插件服务器添加到全局配置 */
    private fun addPluginServerToConfig(entry: MCPServerEntry) {
        val config = AIConfigManager.currentConfig
        val existing = config.mcpServers.find { it.id == entry.id }
        if (existing == null) {
            AIConfigManager.updateInMemory(config.copy(mcpServers = config.mcpServers + entry))
        }
    }

    /** 将插件服务添加到全局配置的 MCP 服务器列表（供 UI 展示） */
    fun addServiceToConfig(serviceName: String, serviceLabel: String) {
        android.util.Log.i("MCPManager", "[addServiceToConfig] 添加服务到配置: $serviceName ($serviceLabel)")
        val entry = MCPServerEntry(
            id = "plugin_service_$serviceName",
            name = serviceLabel,
            source = MCPServerSource.LOCAL_PLUGIN,
            pluginId = null,
            enabled = true
        )
        addPluginServerToConfig(entry)
        android.util.Log.i("MCPManager", "[addServiceToConfig] 完成, 当前 mcpServers 数量: ${AIConfigManager.currentConfig.mcpServers.size}")
    }

    /** 从全局配置中移除插件服务 */
    fun removeServiceFromConfig(serviceName: String) {
        android.util.Log.i("MCPManager", "[removeServiceFromConfig] 从配置移除服务: $serviceName")
        removePluginServerFromConfig("plugin_service_$serviceName")
    }

    /** 从全局配置中移除插件服务器 */
    private fun removePluginServerFromConfig(serverId: String) {
        val config = AIConfigManager.currentConfig
        AIConfigManager.updateInMemory(config.copy(mcpServers = config.mcpServers.filter { it.id != serverId }))
    }

    /** 注销插件工具 */
    fun unregisterPluginTool(name: String) {
        pluginTools.remove(name)
        pluginToolHandlers.remove(name)
    }

    /** 获取所有已注册的插件工具 */
    fun getPluginTools(): List<MCPTool> = pluginTools.values.toList()

    /** 获取指定插件服务器的工具 */
    private fun getPluginToolsForServer(serverId: String): List<MCPTool> {
        return pluginTools.values.toList()
    }

    /** 调用插件工具 */
    suspend fun callPluginTool(name: String, args: Map<String, Any>): String? {
        // 检查工具是否属于某个服务，以及服务/工具是否被禁用
        val serviceName = toolToService[name]
        if (serviceName != null) {
            val serviceInfo = serviceRegistry[serviceName]
            if (serviceInfo != null) {
                if (!serviceInfo.enabled) {
                    return "服务 [$serviceName] 已禁用"
                }
                val toolState = serviceInfo.toolStates[name]
                if (toolState != null && !toolState.enabled) {
                    return "工具 [$name] 在服务 [$serviceName] 中已被禁用"
                }
            }
        }
        return pluginToolHandlers[name]?.invoke(args)
    }

    // ========== MCP 服务管理（工具组） ==========

    /**
     * 注册一个 MCP 服务（工具组）
     * 服务默认启用，所有工具默认启用
     * 如果已持久化过状态，则恢复持久化的状态
     */
    fun registerService(serviceName: String, serviceLabel: String, tools: List<MCPTool>) {
        val serverId = "plugin_service_$serviceName"
        // 检查持久化的服务器启用状态
        val persistedEntry = com.luaforge.studio.lxclua.ai.AIConfigManager.currentConfig.mcpServers
            .find { it.id == serverId }
        val isEnabled = persistedEntry?.enabled ?: true
        // 检查持久化的工具开关状态
        val persistedToolStates = com.luaforge.studio.lxclua.ai.AIConfigManager.currentConfig.mcpToolStates[serviceName]

        val toolStates = mutableMapOf<String, MCPToolState>()
        for (tool in tools) {
            val toolEnabled = persistedToolStates?.get(tool.name) ?: true
            toolStates[tool.name] = MCPToolState(tool.name, enabled = toolEnabled)
            toolToService[tool.name] = serviceName
        }
        serviceRegistry[serviceName] = MCPServiceInfo(
            name = serviceName,
            label = serviceLabel,
            enabled = isEnabled,
            tools = tools,
            toolStates = toolStates
        )
        android.util.Log.i("MCPManager", "[registerService] $serviceName: enabled=$isEnabled, tools=${tools.size}, 持久化工具状态=${persistedToolStates?.size ?: 0}")
    }

    /**
     * 注册单个工具到已有服务（或创建新服务）
     * 用于 registerTool 单独注册工具时确保归属到服务
     */
    fun registerToolToService(serviceName: String, tool: MCPTool) {
        val existing = serviceRegistry[serviceName]
        if (existing != null) {
            existing.toolStates[tool.name] = MCPToolState(tool.name, enabled = true)
            serviceRegistry[serviceName] = existing.copy(
                tools = existing.tools + tool
            )
        } else {
            val toolStates = mutableMapOf(tool.name to MCPToolState(tool.name, enabled = true))
            serviceRegistry[serviceName] = MCPServiceInfo(
                name = serviceName,
                label = serviceName,
                enabled = true,
                tools = listOf(tool),
                toolStates = toolStates
            )
        }
        toolToService[tool.name] = serviceName
    }

    /**
     * 获取所有启用服务中的启用工具（供 AI 使用）
     * 只返回服务启用且工具也启用的工具
     */
    fun getEnabledServiceTools(): List<MCPTool> {
        val result = mutableListOf<MCPTool>()
        // 插件服务
        for ((_, info) in serviceRegistry) {
            if (!info.enabled) continue
            for (tool in info.tools) {
                val state = info.toolStates[tool.name]
                if (state == null || state.enabled) {
                    result.add(tool)
                }
            }
        }
        // 远程 MCP 服务器 — 检查持久化的工具开关状态
        val config = AIConfigManager.currentConfig
        for ((serverId, tools) in serverToolsCache) {
            val entry = config.mcpServers.find { it.id == serverId }
            if (entry == null || !entry.enabled) continue
            val toolStates = config.mcpToolStates[entry.name] ?: emptyMap()
            for (tool in tools) {
                val enabled = toolStates[tool.name] ?: true
                if (enabled) {
                    result.add(tool)
                }
            }
        }
        android.util.Log.d("MCPManager", "[getEnabledServiceTools] 服务数: ${serviceRegistry.size}, 远程: ${serverToolsCache.size}, 启用工具数: ${result.size}")
        return result
    }

    /**
     * 获取服务层级结构（供 UI 展示）
     * 包含插件注册的服务和远程 MCP 服务器的工具
     */
    fun getServiceHierarchy(): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()

        // 插件注册的服务
        for ((_, info) in serviceRegistry) {
            val toolList = info.tools.map { tool ->
                val state = info.toolStates[tool.name]
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "enabled" to (state?.enabled ?: true)
                )
            }
            result.add(mapOf(
                "name" to info.name,
                "label" to info.label,
                "enabled" to info.enabled,
                "source" to "plugin",
                "toolCount" to info.tools.size,
                "tools" to toolList
            ))
        }

        // 远程 MCP 服务器的工具
        for ((serverId, tools) in serverToolsCache) {
            val entry = pluginServerEntries[serverId]
                ?: AIConfigManager.currentConfig.mcpServers.find { it.id == serverId }
            // 读取持久化的工具开关状态
            val persistedToolStates = AIConfigManager.currentConfig.mcpToolStates[entry?.name] ?: emptyMap()
            val toolList = tools.map { tool ->
                val toolEnabled = persistedToolStates[tool.name] ?: true
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "enabled" to toolEnabled
                )
            }
            if (toolList.isNotEmpty()) {
                result.add(mapOf(
                    "name" to (entry?.name ?: serverId),
                    "label" to (entry?.name ?: serverId),
                    "enabled" to (entry?.enabled ?: true),
                    "source" to "remote",
                    "toolCount" to tools.size,
                    "tools" to toolList
                ))
            }
        }

        return result
    }

    /** 启用服务（启用后该服务下所有工具可用） */
    fun enableService(serviceName: String): Boolean {
        val info = serviceRegistry[serviceName] ?: return false
        serviceRegistry[serviceName] = info.copy(enabled = true)
        syncServiceEnabledToConfig(serviceName, true)
        return true
    }

    /** 禁用服务（禁用后该服务下所有工具不可用） */
    fun disableService(serviceName: String): Boolean {
        val info = serviceRegistry[serviceName] ?: return false
        serviceRegistry[serviceName] = info.copy(enabled = false)
        syncServiceEnabledToConfig(serviceName, false)
        return true
    }

    /** 同步服务启用状态到全局配置列表 */
    private fun syncServiceEnabledToConfig(serviceName: String, enabled: Boolean) {
        val config = AIConfigManager.currentConfig
        val serverId = "plugin_service_$serviceName"
        val updated = config.mcpServers.map { entry ->
            if (entry.id == serverId) entry.copy(enabled = enabled) else entry
        }
        AIConfigManager.updateInMemory(config.copy(mcpServers = updated))
    }

    /** 启用服务中的指定工具（插件和远程 MCP 均支持） */
    fun enableServiceTool(serviceName: String, toolName: String): Boolean {
        // 先尝试插件服务
        val info = serviceRegistry[serviceName]
        if (info != null) {
            val toolStates = info.toolStates
            if (toolStates.containsKey(toolName)) {
                toolStates[toolName] = MCPToolState(toolName, enabled = true)
                syncToolStatesToConfig()
                return true
            }
            return false
        }
        // 远程 MCP 服务器 - 直接更新持久化的工具状态
        return setRemoteToolState(serviceName, toolName, true)
    }

    /** 禁用服务中的指定工具（插件和远程 MCP 均支持） */
    fun disableServiceTool(serviceName: String, toolName: String): Boolean {
        // 先尝试插件服务
        val info = serviceRegistry[serviceName]
        if (info != null) {
            val toolStates = info.toolStates
            if (toolStates.containsKey(toolName)) {
                toolStates[toolName] = MCPToolState(toolName, enabled = false)
                syncToolStatesToConfig()
                return true
            }
            return false
        }
        // 远程 MCP 服务器
        return setRemoteToolState(serviceName, toolName, false)
    }

    /** 设置远程 MCP 服务器的工具状态 */
    private fun setRemoteToolState(serverName: String, toolName: String, enabled: Boolean): Boolean {
        // 查找对应的 MCP 服务器 entry
        val entry = AIConfigManager.currentConfig.mcpServers.find { it.name == serverName }
        if (entry == null) {
            android.util.Log.w("MCPManager", "setRemoteToolState: 找不到服务器 $serverName")
            return false
        }
        // 验证工具是否属于该服务器
        val serverTools = serverToolsCache[entry.id]
        if (serverTools?.none { it.name == toolName } != false) {
            android.util.Log.w("MCPManager", "setRemoteToolState: 工具 $toolName 不在服务器 $serverName 中")
            return false
        }
        val config = AIConfigManager.currentConfig
        val currentStates = config.mcpToolStates.toMutableMap()
        val toolStates = currentStates.getOrPut(serverName) { mutableMapOf() }.toMutableMap()
        toolStates[toolName] = enabled
        currentStates[serverName] = toolStates
        AIConfigManager.updateInMemory(config.copy(mcpToolStates = currentStates))
        android.util.Log.i("MCPManager", "setRemoteToolState: $serverName/$toolName -> $enabled")
        return true
    }

    /** 持久化所有服务的工具开关状态到配置 */
    private fun syncToolStatesToConfig() {
        val states = mutableMapOf<String, Map<String, Boolean>>()
        for ((serviceName, info) in serviceRegistry) {
            val toolStates = mutableMapOf<String, Boolean>()
            for ((toolName, state) in info.toolStates) {
                toolStates[toolName] = state.enabled
            }
            if (toolStates.isNotEmpty()) {
                states[serviceName] = toolStates
            }
        }
        val config = com.luaforge.studio.lxclua.ai.AIConfigManager.currentConfig
        com.luaforge.studio.lxclua.ai.AIConfigManager.updateInMemory(config.copy(mcpToolStates = states))
        android.util.Log.d("MCPManager", "[syncToolStatesToConfig] 已持久化 ${states.size} 个服务的工具状态")
    }

    /** 获取服务状态（包含服务启用状态和各工具状态） */
    fun getServiceStatus(serviceName: String): Map<String, Any>? {
        val info = serviceRegistry[serviceName] ?: return null
        val toolStatusList = info.toolStates.map { (name, state) ->
            mapOf("name" to name, "enabled" to state.enabled)
        }
        return mapOf(
            "name" to info.name,
            "label" to info.label,
            "enabled" to info.enabled,
            "toolCount" to info.tools.size,
            "tools" to toolStatusList
        )
    }

    /** 列出所有已注册的 MCP 服务 */
    fun listServices(): List<Map<String, Any>> {
        return serviceRegistry.values.map { info ->
            mapOf(
                "name" to info.name,
                "label" to info.label,
                "enabled" to info.enabled,
                "toolCount" to info.tools.size
            )
        }
    }

    /** 注销 MCP 服务（同时注销该服务下所有工具） */
    fun unregisterService(serviceName: String) {
        val info = serviceRegistry.remove(serviceName) ?: return
        // 清理反向映射
        for (tool in info.tools) {
            toolToService.remove(tool.name)
            pluginTools.remove(tool.name)
            pluginToolHandlers.remove(tool.name)
        }
    }
}