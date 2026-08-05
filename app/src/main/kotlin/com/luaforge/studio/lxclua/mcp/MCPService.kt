package com.luaforge.studio.lxclua.mcp

import com.luaforge.studio.lxclua.ai.AIConfigManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    private val ktorClient = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000    // 连接超时 30 秒
            requestTimeoutMillis = 60_000    // 请求超时 60 秒
            socketTimeoutMillis = 60_000     // Socket 超时 60 秒
        }
    }

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
        }
    }
}

/** MCP 管理器单例 — 管理多个 MCP 服务器连接 */
object MCPManager {
    private var _service: IMCPService? = null

    /** 应用上下文（用于持久化配置） */
    private var appContext: android.content.Context? = null

    /**
     * 初始化 MCP 管理器（应用启动时调用）
     * @param context 应用上下文
     */
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

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

    // ========== 本地 MCP 服务器（局域网广播，每条目独立） ==========

    /** 本地 HTTP 服务器实例映射: serverId -> MCPLocalServer */
    private val localServers = ConcurrentHashMap<String, MCPLocalServer>()

    /** NSD 广播服务实例映射: serverId -> MCPBroadcastService */
    private val broadcastServices = ConcurrentHashMap<String, MCPBroadcastService>()

    /** 实际使用的端口映射: serverId -> port */
    private val actualPorts = ConcurrentHashMap<String, Int>()

    /** 端口锁定状态映射: serverId -> portLocked */
    private val portLockStates = ConcurrentHashMap<String, Boolean>()

    /** 是否有任何广播服务器在运行 */
    val isAnyBroadcastRunning: Boolean
        get() = localServers.values.any { it.running }

    /**
     * 刷新广播状态：根据所有已启用服务器的 broadcastEnabled 字段
     * 为每个启用了广播的条目启动独立的 HTTP 服务器和 NSD 广播
     * 端口由条目 broadcastPort 指定（0 = 随机端口）
     */
    suspend fun refreshBroadcast() {
        val config = AIConfigManager.currentConfig
        val broadcastEntries = config.mcpServers.filter { it.enabled && it.broadcastEnabled }
        android.util.Log.i("MCPManager", "[refreshBroadcast] 开始: 总服务器=${config.mcpServers.size}, 广播条目=${broadcastEntries.size}, ids=${broadcastEntries.map { it.id }}")
        val broadcastIds = broadcastEntries.map { it.id }.toSet()

        // 停止已取消广播的条目
        val toStop = localServers.keys.filter { it !in broadcastIds }
        for (id in toStop) {
            stopServerForEntry(id)
        }

        // 启动/更新广播条目
        for (entry in broadcastEntries) {
            val existingServer = localServers[entry.id]
            if (existingServer != null && existingServer.running) {
                // 已在运行，检查端口是否变了
                val currentPort = actualPorts[entry.id] ?: 0
                // 端口锁定状态变化或端口值变化时重启
                val portChanged = entry.portLocked && entry.broadcastPort != currentPort
                val lockStatusChanged = entry.portLocked != (portLockStates[entry.id] ?: false)
                if (portChanged || lockStatusChanged) {
                    android.util.Log.d("MCPManager", "[refreshBroadcast] ${entry.name} 端口变更: current=$currentPort, new=${entry.broadcastPort}, locked=${entry.portLocked}，重启")
                    stopServerForEntry(entry.id)
                    startServerForEntry(entry)
                }
            } else {
                startServerForEntry(entry)
            }
        }
        android.util.Log.i("MCPManager", "[refreshBroadcast] 完成: 运行中=${localServers.size}个, 条目: ${localServers.keys}")
    }

    /**
     * 设置端口锁定状态（持久化到磁盘）
     * @param serverId 服务器条目 ID
     * @param locked 是否锁定
     * @return 是否成功
     */
    fun setPortLocked(serverId: String, locked: Boolean): Boolean {
        val config = AIConfigManager.currentConfig
        val entry = config.mcpServers.find { it.id == serverId } ?: return false
        val updated = entry.copy(portLocked = locked)
        val newServers = config.mcpServers.map {
            if (it.id == serverId) updated else it
        }
        val newConfig = config.copy(mcpServers = newServers)
        AIConfigManager.updateInMemory(newConfig)
        // 更新内存中的锁定状态
        portLockStates[serverId] = locked
        // 持久化到磁盘
        persistConfig(newConfig)
        android.util.Log.i("MCPManager", "[setPortLocked] $serverId -> locked=$locked (persisted)")
        return true
    }

    /**
     * 为单个条目启动 HTTP 服务器和 NSD 广播
     * @param entry 服务器条目，broadcastPort=0 时自动分配随机端口；portLocked=true 时使用 broadcastPort
     */
    private suspend fun startServerForEntry(entry: MCPServerEntry) {
        android.util.Log.i("MCPManager", "[startServer] 开始: entry.id=${entry.id}, name=${entry.name}, portLocked=${entry.portLocked}, broadcastPort=${entry.broadcastPort}")
        val ctx = appContext ?: run {
            android.util.Log.e("MCPManager", "appContext 未初始化，无法启动广播服务器")
            return
        }

        // 端口选择策略：
        // 1. portLocked=true 且 broadcastPort>0：使用锁定的端口
        // 2. 其他情况：使用随机端口（OS 自动分配）
        val port = if (entry.portLocked && entry.broadcastPort > 0) {
            android.util.Log.i("MCPManager", "[startServer] 使用锁定端口: ${entry.broadcastPort}")
            entry.broadcastPort
        } else {
            0
        }

        val server = MCPLocalServer(port)
        // 初始化 WakeLock（必须在 start 前调用）
        server.initWakeLock(ctx)
        // 先存入 map，避免协程在 start() 返回后取消导致端口丢失
        localServers[entry.id] = server
        val started = server.start()
        if (!started) {
            localServers.remove(entry.id)
            android.util.Log.e("MCPManager", "[startServer] ${entry.name} 启动失败: port=$port")
            return
        }

        // 获取实际分配的端口（port=0 时系统自动分配）
        val actualPort = server.actualPort
        actualPorts[entry.id] = actualPort
        portLockStates[entry.id] = entry.portLocked
        android.util.Log.i("MCPManager", "[startServer] 存储端口: entry.id=${entry.id}, actualPort=$actualPort, portLocked=${entry.portLocked}, actualPorts=${actualPorts}")

        // 启动 NSD 广播
        val serviceName = "${MCPBroadcastService.SERVICE_NAME}-${entry.name}"
        val broadcast = MCPBroadcastService(ctx, actualPort)
        broadcast.register(serviceName)
        broadcastServices[entry.id] = broadcast

        // 更新配置中的端口（随机分配时记录实际端口，锁定端口时保持原值）
        val config = AIConfigManager.currentConfig
        val updated = config.mcpServers.map {
            if (it.id == entry.id) {
                // 如果是随机端口，更新实际分配的端口；如果锁定，保持原值
                val newPort = if (entry.portLocked) it.broadcastPort else actualPort
                it.copy(broadcastPort = newPort)
            } else it
        }
        val newConfig = config.copy(mcpServers = updated)
        AIConfigManager.updateInMemory(newConfig)
        // 持久化到磁盘
        persistConfig(newConfig)

        android.util.Log.i("MCPManager", "[startServer] ${entry.name}: http://0.0.0.0:$actualPort/mcp, NSD=$serviceName")
    }

    /** 停止单个条目的服务器和广播 */
    private fun stopServerForEntry(serverId: String) {
        broadcastServices[serverId]?.unregister()
        broadcastServices.remove(serverId)
        localServers[serverId]?.stop()
        localServers.remove(serverId)
        actualPorts.remove(serverId)
        portLockStates.remove(serverId)
        android.util.Log.i("MCPManager", "[stopServer] $serverId 已停止")
    }

    /** 停止所有本地服务器和广播 */
    fun stopAllLocalServers() {
        for (id in localServers.keys.toList()) {
            stopServerForEntry(id)
        }
        android.util.Log.i("MCPManager", "所有本地 MCP 服务器已停止")
    }

    /**
     * 获取指定条目的广播地址列表
     * @return 包含 127.0.0.1 和局域网 IP 的地址列表
     */
    suspend fun getBroadcastAddresses(serverId: String): List<String> {
        val cachedPort = actualPorts[serverId]
        val server = localServers[serverId]
        val port = cachedPort ?: server?.getBoundPort() ?: run {
            android.util.Log.w("MCPManager", "[getBroadcastAddresses] serverId=$serverId, actualPorts 无此条目, localServers 也无此条目, 所有key=${actualPorts.keys}")
            return emptyList()
        }
        android.util.Log.d("MCPManager", "[getBroadcastAddresses] serverId=$serverId, port=$port, fromCache=${cachedPort != null}")
        val addresses = mutableListOf<String>()

        // 127.0.0.1 本地回环地址
        addresses.add("http://127.0.0.1:$port/mcp")

        // 局域网 IP
        val broadcast = broadcastServices[serverId]
        val lanIp = broadcast?.getLocalIpAddress()
        if (lanIp != null) {
            addresses.add("http://$lanIp:$port/mcp")
        }

        return addresses
    }

    /**
     * 获取条目的广播端口
     */
    fun getBroadcastPort(serverId: String): Int {
        return actualPorts[serverId] ?: 0
    }

    /**
     * 获取所有启用了广播的服务器条目
     */
    fun getBroadcastEnabledServers(): List<MCPServerEntry> {
        return AIConfigManager.currentConfig.mcpServers.filter { it.enabled && it.broadcastEnabled }
    }

    /**
     * 获取广播条目的工具列表（供本地 HTTP 服务器 tools/list 使用）
     * 只返回启用了广播的服务器中的已启用工具
     */
    fun getBroadcastTools(): List<MCPTool> {
        val broadcastEntries = getBroadcastEnabledServers()
        val result = mutableListOf<MCPTool>()

        for (entry in broadcastEntries) {
            if (entry.source == MCPServerSource.LOCAL_PLUGIN) {
                // 插件服务：从 serviceRegistry 中获取已启用的工具
                val serviceName = entry.id.removePrefix("plugin_service_")
                val info = serviceRegistry[serviceName]
                if (info != null && info.enabled) {
                    for (tool in info.tools) {
                        val state = info.toolStates[tool.name]
                        if (state == null || state.enabled) {
                            result.add(tool)
                        }
                    }
                }
            } else {
                // 远程服务器：从缓存中获取工具
                val tools = serverToolsCache[entry.id]
                if (tools != null) {
                    val toolStates = AIConfigManager.currentConfig.mcpToolStates[entry.name] ?: emptyMap()
                    for (tool in tools) {
                        val enabled = toolStates[tool.name] ?: true
                        if (enabled) {
                            result.add(tool)
                        }
                    }
                }
            }
        }
        android.util.Log.d("MCPManager", "[getBroadcastTools] 广播条目=${broadcastEntries.size}, 工具数=${result.size}")
        return result
    }

    /**
     * 调用广播工具（仅从广播条目中查找）
     * 供本地 HTTP 服务器 tools/call 使用
     * 使用 withContext 确保在 IO 线程执行，避免协程被挂起
     */
    suspend fun callBroadcastTool(toolName: String, args: Map<String, Any>): MCPToolCallResult {
        return withContext(Dispatchers.IO) {
            // 先检查插件工具
            val pluginResult = callPluginTool(toolName, args)
            if (pluginResult != null) {
                return@withContext MCPToolCallResult(true, listOf(MCPContent(text = pluginResult)))
            }
            // 再检查远程服务器（仅广播启用的）
            val broadcastEntries = getBroadcastEnabledServers().filter { it.source == MCPServerSource.REMOTE_URL }
            for (entry in broadcastEntries) {
                val cached = serverToolsCache[entry.id] ?: continue
                if (cached.any { it.name == toolName }) {
                    val svc = serverInstances[entry.id] ?: continue
                    return@withContext svc.callTool(MCPToolCallRequest(toolName, args))
                }
            }
            return@withContext MCPToolCallResult(false, error = "未找到广播工具: $toolName")
        }
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

    /**
     * 自动连接所有已启用的远程 MCP 服务器（应用启动时调用）
     * 异步执行，不阻塞主线程，连接成功后工具会缓存到 serverToolsCache
     */
    fun autoConnectEnabledServers() {
        CoroutineScope(Dispatchers.IO).launch {
            val servers = AIConfigManager.currentConfig.mcpServers
                .filter { it.enabled && it.source == MCPServerSource.REMOTE_URL && it.url.isNotBlank() }
            if (servers.isEmpty()) {
                android.util.Log.d("MCPManager", "[autoConnect] 没有已启用的远程 MCP 服务器")
                return@launch
            }
            android.util.Log.i("MCPManager", "[autoConnect] 开始自动连接 ${servers.size} 个远程 MCP 服务器")
            for (entry in servers) {
                try {
                    val result = connectServer(entry)
                    android.util.Log.i("MCPManager", "[autoConnect] ${entry.name}: ${if (result) "连接成功" else "连接失败"}")
                } catch (e: Exception) {
                    android.util.Log.e("MCPManager", "[autoConnect] ${entry.name} 连接异常: ${e.message}")
                }
            }
        }
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

    /** 将插件服务器添加到全局配置（持久化） */
    private fun addPluginServerToConfig(entry: MCPServerEntry) {
        val config = AIConfigManager.currentConfig
        val existing = config.mcpServers.find { it.id == entry.id }
        if (existing == null) {
            val newConfig = config.copy(mcpServers = config.mcpServers + entry)
            AIConfigManager.updateInMemory(newConfig)
            // 持久化到磁盘
            persistConfig(newConfig)
            android.util.Log.i("MCPManager", "[addPluginServerToConfig] 添加并持久化: ${entry.id}")
        }
    }

    /** 将插件服务添加到全局配置的 MCP 服务器列表（供 UI 展示，持久化） */
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

    /** 从全局配置中移除插件服务器（持久化） */
    private fun removePluginServerFromConfig(serverId: String) {
        val config = AIConfigManager.currentConfig
        val newConfig = config.copy(mcpServers = config.mcpServers.filter { it.id != serverId })
        AIConfigManager.updateInMemory(newConfig)
        persistConfig(newConfig)
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

    /**
     * 调用插件工具
     * 使用 withContext 确保在 IO 线程执行，避免协程被挂起
     */
    suspend fun callPluginTool(name: String, args: Map<String, Any>): String? {
        return withContext(Dispatchers.IO) {
            // 检查工具是否属于某个服务，以及服务/工具是否被禁用
            val serviceName = toolToService[name]
            if (serviceName != null) {
                val serviceInfo = serviceRegistry[serviceName]
                if (serviceInfo != null) {
                    if (!serviceInfo.enabled) {
                        return@withContext "服务 [$serviceName] 已禁用"
                    }
                    val toolState = serviceInfo.toolStates[name]
                    if (toolState != null && !toolState.enabled) {
                        return@withContext "工具 [$name] 在服务 [$serviceName] 中已被禁用"
                    }
                }
            }
            return@withContext pluginToolHandlers[name]?.invoke(args)
        }
    }

    // ========== MCP 服务管理（工具组） ==========

    /**
     * 注册一个 MCP 服务（工具组）
     * 服务默认启用，所有工具默认启用
     * 如果已持久化过状态，则恢复持久化的状态（包括 portLocked 和 broadcastPort）
     */
    fun registerService(serviceName: String, serviceLabel: String, tools: List<MCPTool>) {
        val serverId = "plugin_service_$serviceName"
        // 检查持久化的服务器配置
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
        // 恢复端口锁定状态和广播端口
        val portLocked = persistedEntry?.portLocked ?: false
        val broadcastPort = persistedEntry?.broadcastPort ?: 0
        // 同步到内存中的配置（确保 UI 和启动逻辑能看到）
        if (persistedEntry != null) {
            val config = AIConfigManager.currentConfig
            val updated = config.mcpServers.map {
                if (it.id == serverId) it.copy(portLocked = portLocked, broadcastPort = broadcastPort) else it
            }
            AIConfigManager.updateInMemory(config.copy(mcpServers = updated))
        }
        android.util.Log.i("MCPManager", "[registerService] $serviceName: enabled=$isEnabled, portLocked=$portLocked, broadcastPort=$broadcastPort, tools=${tools.size}")
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

    /** 同步服务启用状态到全局配置列表（持久化） */
    private fun syncServiceEnabledToConfig(serviceName: String, enabled: Boolean) {
        val config = AIConfigManager.currentConfig
        val serverId = "plugin_service_$serviceName"
        val updated = config.mcpServers.map { entry ->
            if (entry.id == serverId) entry.copy(enabled = enabled) else entry
        }
        val newConfig = config.copy(mcpServers = updated)
        AIConfigManager.updateInMemory(newConfig)
        persistConfig(newConfig)
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
        val newConfig = config.copy(mcpToolStates = currentStates)
        AIConfigManager.updateInMemory(newConfig)
        // 异步持久化到磁盘
        persistConfig(newConfig)
        android.util.Log.i("MCPManager", "setRemoteToolState: $serverName/$toolName -> $enabled")
        return true
    }

    /**
     * 异步持久化配置到磁盘（IO线程，不阻塞调用方）
     */
    private fun persistConfig(config: com.luaforge.studio.lxclua.ai.AIConfigData) {
        val ctx = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AIConfigManager.saveConfig(ctx, config)
                android.util.Log.d("MCPManager", "[persistConfig] 配置已持久化")
            } catch (e: Exception) {
                android.util.Log.e("MCPManager", "[persistConfig] 持久化失败: ${e.message}", e)
            }
        }
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
        val config = AIConfigManager.currentConfig
        val newConfig = config.copy(mcpToolStates = states)
        AIConfigManager.updateInMemory(newConfig)
        // 异步持久化到磁盘
        persistConfig(newConfig)
        android.util.Log.d("MCPManager", "[syncToolStatesToConfig] 已更新 ${states.size} 个服务的工具状态")
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