package com.luaforge.studio.lxclua.mcp

import com.luaforge.studio.lxclua.ai.AIConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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

/** 基于 HTTP 的 MCP 服务实现（Streamable HTTP Transport） */
class MCPServiceImpl(
    private val serverUrl: String? = null,
    private val serverTransport: String? = null
) : IMCPService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var sessionId: String? = null
    private var serverCapabilities: JSONObject? = null

    override var isConnected: Boolean = false
        private set

    private val config get() = AIConfigManager.currentConfig

    /** 实际使用的端点 */
    private val effectiveEndpoint: String
        get() = serverUrl?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            ?: config.mcpEndpoint.trimEnd('/')

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpoint = effectiveEndpoint
            // MCP 初始化请求
            val initBody = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "initialize")
                put("params", JSONObject().apply {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", JSONObject())
                    put("clientInfo", JSONObject().apply {
                        put("name", "LXC-LUA")
                        put("version", "1.1.3")
                    })
                })
            }
            val response = client.newCall(
                Request.Builder()
                    .url(endpoint)
                    .post(initBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            if (!response.isSuccessful) return@withContext false
            val body = response.body?.string() ?: return@withContext false
            val json = JSONObject(body)
            val result = json.optJSONObject("result")
            if (result != null) {
                serverCapabilities = result.optJSONObject("capabilities")
                sessionId = response.header("Mcp-Session-Id")
                isConnected = true
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("MCPService", "MCP 连接失败", e)
            false
        }
    }

    override suspend fun disconnect() {
        sessionId = null
        serverCapabilities = null
        isConnected = false
    }

    override suspend fun listTools(): List<MCPTool> = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext emptyList()
        try {
            val result = sendRequest("tools/list")
            val tools = result?.optJSONArray("tools") ?: return@withContext emptyList()
            (0 until tools.length()).map { i ->
                val tool = tools.getJSONObject(i)
                val schema = tool.optJSONObject("inputSchema") ?: JSONObject()
                val schemaMap = jsonToMap(schema)
                MCPTool(
                    name = tool.optString("name", ""),
                    description = tool.optString("description", ""),
                    inputSchema = schemaMap
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun callTool(request: MCPToolCallRequest): MCPToolCallResult = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext MCPToolCallResult(false, error = "MCP 未连接")
        try {
            val argsJson = JSONObject()
            request.arguments.forEach { (k, v) -> argsJson.put(k, v) }
            val params = JSONObject().apply {
                put("name", request.toolName)
                put("arguments", argsJson)
            }
            val result = sendRequest("tools/call", params)
            if (result == null) {
                return@withContext MCPToolCallResult(false, error = "工具调用返回为空")
            }
            val contentArray = result.optJSONArray("content")
            val contentList = if (contentArray != null) {
                (0 until contentArray.length()).map { i ->
                    val item = contentArray.getJSONObject(i)
                    MCPContent(
                        type = item.optString("type", "text"),
                        text = item.optString("text", null),
                        data = item.optString("data", null),
                        mimeType = item.optString("mimeType", null)
                    )
                }
            } else {
                // 直接返回文本
                listOf(MCPContent(text = result.optString("text", result.toString())))
            }
            MCPToolCallResult(true, content = contentList)
        } catch (e: Exception) {
            MCPToolCallResult(false, error = e.message ?: "调用失败")
        }
    }

    override suspend fun listResources(): List<MCPResource> = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext emptyList()
        try {
            val result = sendRequest("resources/list")
            val resources = result?.optJSONArray("resources") ?: return@withContext emptyList()
            (0 until resources.length()).map { i ->
                val res = resources.getJSONObject(i)
                MCPResource(
                    uri = res.optString("uri", ""),
                    name = res.optString("name", ""),
                    description = res.optString("description", ""),
                    mimeType = res.optString("mimeType", "text/plain")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun readResource(uri: String): String? = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext null
        try {
            val params = JSONObject().put("uri", uri)
            val result = sendRequest("resources/read", params)
            val contents = result?.optJSONArray("contents")
            if (contents != null && contents.length() > 0) {
                contents.getJSONObject(0).optString("text", null)
                    ?: contents.getJSONObject(0).optString("blob", null)
            } else {
                result?.optString("text")
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 发送 JSON-RPC 请求 */
    private suspend fun sendRequest(method: String, params: JSONObject? = null): JSONObject? {
        val requestId = System.currentTimeMillis().toInt()
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", requestId)
            put("method", method)
            if (params != null) put("params", params)
        }
        val endpoint = effectiveEndpoint
        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        sessionId?.let { reqBuilder.addHeader("Mcp-Session-Id", it) }
        val response = client.newCall(reqBuilder.build()).execute()
        val respBody = response.body?.string() ?: return null
        val json = JSONObject(respBody)
        val error = json.optJSONObject("error")
        if (error != null) {
            android.util.Log.w("MCPService", "MCP 请求错误: $method -> ${error.optString("message")}")
            return null
        }
        return json.optJSONObject("result")
    }

    /** JSONObject 转 Map */
    private fun jsonToMap(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            val value = json.get(key)
            when (value) {
                is JSONObject -> map[key] = jsonToMap(value)
                is JSONArray -> map[key] = (0 until value.length()).map { i ->
                    val item = value.get(i)
                    if (item is JSONObject) jsonToMap(item) else item
                }
                else -> map[key] = value
            }
        }
        return map
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
     */
    fun registerService(serviceName: String, serviceLabel: String, tools: List<MCPTool>) {
        val toolStates = mutableMapOf<String, MCPToolState>()
        for (tool in tools) {
            toolStates[tool.name] = MCPToolState(tool.name, enabled = true)
            toolToService[tool.name] = serviceName
        }
        serviceRegistry[serviceName] = MCPServiceInfo(
            name = serviceName,
            label = serviceLabel,
            enabled = true,
            tools = tools,
            toolStates = toolStates
        )
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
        for ((_, info) in serviceRegistry) {
            if (!info.enabled) continue
            for (tool in info.tools) {
                val state = info.toolStates[tool.name]
                if (state == null || state.enabled) {
                    result.add(tool)
                }
            }
        }
        android.util.Log.d("MCPManager", "[getEnabledServiceTools] 服务数: ${serviceRegistry.size}, 启用工具数: ${result.size}")
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
            val toolList = tools.map { tool ->
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "enabled" to true
                )
            }
            if (toolList.isNotEmpty()) {
                result.add(mapOf(
                    "name" to (entry?.id ?: serverId),
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

    /** 启用服务中的指定工具 */
    fun enableServiceTool(serviceName: String, toolName: String): Boolean {
        val info = serviceRegistry[serviceName] ?: return false
        val toolStates = info.toolStates
        if (toolStates.containsKey(toolName)) {
            toolStates[toolName] = MCPToolState(toolName, enabled = true)
            return true
        }
        return false
    }

    /** 禁用服务中的指定工具 */
    fun disableServiceTool(serviceName: String, toolName: String): Boolean {
        val info = serviceRegistry[serviceName] ?: return false
        val toolStates = info.toolStates
        if (toolStates.containsKey(toolName)) {
            toolStates[toolName] = MCPToolState(toolName, enabled = false)
            return true
        }
        return false
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