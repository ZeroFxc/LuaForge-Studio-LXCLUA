package com.luaforge.studio.lxclua.mcp

import android.content.Context
import android.os.PowerManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MCP 本地 HTTP 服务器（使用官方 MCP Kotlin SDK）
 *
 * 在设备上启动一个轻量 HTTP 服务器，基于官方 SDK 的 Streamable HTTP 传输，
 * 将插件注册的 MCP 工具暴露给局域网内的其他设备使用。
 */
class MCPLocalServer(
    private val port: Int = 8765
) {
    companion object {
        private const val TAG = "MCPLocalServer"
        const val DEFAULT_PORT = 8765
        const val SERVER_NAME = "LXC-LUA-MCP"
        const val SERVER_VERSION = "1.0.0"
        private const val WAKE_LOCK_TAG = "MCPLocalServer::ToolCall"
    }

    private var engine: EmbeddedServer<*, *>? = null
    private val isRunning = AtomicBoolean(false)
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** 是否正在运行 */
    val running: Boolean get() = isRunning.get()

    /** 实际使用的端口（port=0 时系统自动分配，启动后可用） */
    var actualPort: Int = port
        private set

    /**
     * 从 Ktor 引擎实时获取已绑定的端口
     * 即使协程取消导致 actualPort 未更新，也能拿到真实端口
     */
    suspend fun getBoundPort(): Int {
        if (port > 0) return port
        return try {
            (engine as? EmbeddedServer<*, *>)?.engine?.resolvedConnectors()?.firstOrNull()?.port ?: actualPort
        } catch (e: Exception) {
            actualPort
        }
    }

    /** 初始化 WakeLock（需要在 Context 可用时调用） */
    fun initWakeLock(context: Context) {
        if (wakeLock == null) {
            powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            wakeLock?.setReferenceCounted(false)
            android.util.Log.i(TAG, "WakeLock 已初始化")
        }
    }

    /** 启动服务器 */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isRunning.get()) {
            android.util.Log.w(TAG, "服务器已在运行中")
            return@withContext true
        }
        try {
            val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                // 使用官方 SDK 的 Stateless Streamable HTTP 传输
                mcpStatelessStreamableHttp(
                    path = "/mcp",
                    enableDnsRebindingProtection = false  // 允许局域网任意设备连接
                ) {
                    buildMcpServer()
                }

                routing {
                    // 健康检查端点
                    get("/health") {
                        call.respondText("OK", ContentType.Text.Plain)
                    }
                    // 服务器信息端点
                    get("/") {
                        call.respond(mapOf(
                            "server" to SERVER_NAME,
                            "version" to SERVER_VERSION,
                            "tools" to MCPManager.getBroadcastTools().size
                        ))
                    }
                }
            }
            server.start(wait = false)
            engine = server

            // 等待 Ktor 完成端口绑定（异步启动，最多等 5 秒）
            // 使用 Thread.sleep 而非 delay，避免协程取消导致端口未存储
            val deadline = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < deadline) {
                val connectorPort = server.engine.resolvedConnectors().firstOrNull()?.port ?: 0
                if (connectorPort > 0) {
                    actualPort = connectorPort
                    break
                }
                Thread.sleep(50)
            }

            isRunning.set(true)
            android.util.Log.i(TAG, "MCP 本地服务器已启动: http://0.0.0.0:$actualPort/mcp")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "启动 MCP 本地服务器失败: ${e.message}", e)
            isRunning.set(false)
            false
        }
    }

    /** 停止服务器 */
    fun stop() {
        try {
            engine?.stop(1000, 2000)
            engine = null
            isRunning.set(false)
            // 释放 WakeLock
            releaseWakeLock()
            android.util.Log.i(TAG, "MCP 本地服务器已停止")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "停止服务器异常: ${e.message}", e)
        }
    }

    /**
     * 构建 MCP Server 实例，注册所有广播工具和资源
     * 每个请求都会创建新的 Server 实例，确保工具列表实时更新
     */
    private fun RoutingContext.buildMcpServer(): Server {
        val server = Server(
            serverInfo = Implementation(name = SERVER_NAME, version = SERVER_VERSION),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(),
                    resources = ServerCapabilities.Resources()
                )
            )
        )

        // 注册广播工具
        val tools = MCPManager.getBroadcastTools()
        for (tool in tools) {
            server.addTool(
                name = tool.name,
                description = tool.description,
                inputSchema = mapToToolSchema(tool.inputSchema)
            ) { request ->
                handleToolCall(tool.name, request)
            }
        }
        android.util.Log.d(TAG, "已注册 ${tools.size} 个广播工具")

        // 注册资源
        server.addResource(
            uri = "mcp://lxclua/info",
            name = "LXC-LUA 信息",
            description = "LXC-LUA MCP 服务器信息",
            mimeType = "application/json"
        ) { request ->
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        uri = request.params.uri,
                        mimeType = "application/json",
                        text = """{"server":"$SERVER_NAME","version":"$SERVER_VERSION","tools":${tools.size}}"""
                    )
                )
            )
        }

        return server
    }

    /**
     * 处理工具调用，委托给 MCPManager
     * 使用 WakeLock 确保后台执行时 CPU 不休眠
     */
    private suspend fun handleToolCall(
        toolName: String,
        request: CallToolRequest
    ): CallToolResult {
        val args = request.params.arguments?.let { jsonObjectToMap(it) } ?: emptyMap()
        android.util.Log.i(TAG, "tools/call(广播): $toolName, args=$args")

        // 获取 WakeLock 防止 Doze 模式挂起 CPU
        acquireWakeLock()
        try {
            // 确保在 IO 线程执行，避免协程被挂起
            val result = withContext(Dispatchers.IO) {
                MCPManager.callBroadcastTool(toolName, args)
            }
            return if (result.success) {
                CallToolResult(
                    content = result.content.map { c ->
                        TextContent(text = c.text ?: c.data ?: "")
                    }
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent(text = "错误: ${result.error ?: "未知错误"}")),
                    isError = true
                )
            }
        } finally {
            releaseWakeLock()
        }
    }

    /** 获取 WakeLock */
    private fun acquireWakeLock() {
        try {
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(10 * 60 * 1000L) // 最多持有 10 分钟
                    android.util.Log.d(TAG, "WakeLock 已获取")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "获取 WakeLock 失败: ${e.message}")
        }
    }

    /** 释放 WakeLock */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    android.util.Log.d(TAG, "WakeLock 已释放")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "释放 WakeLock 失败: ${e.message}")
        }
    }

    /**
     * 将工具的 inputSchema Map 转为 SDK 的 ToolSchema
     */
    private fun mapToToolSchema(schema: Map<String, Any>): ToolSchema {
        if (schema.isEmpty()) return ToolSchema()

        val properties = schema["properties"] as? Map<String, Any>
        val required = schema["required"] as? List<*>
        val jsonSchema = schema["\$schema"] as? String

        return ToolSchema(
            schema = jsonSchema,
            properties = properties?.let { mapToJsonObject(it) },
            required = required?.map { it.toString() }
        )
    }

    /**
     * 将 Map<String, Any> 递归转为 JsonObject
     */
    private fun mapToJsonObject(map: Map<String, Any>): JsonObject {
        return buildJsonObject {
            map.forEach { (key, value) ->
                put(key, valueToJson(value))
            }
        }
    }

    /**
     * 递归将任意值转为 JsonElement
     */
    private fun valueToJson(value: Any?): JsonElement {
        return when (value) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                mapToJsonObject(value as Map<String, Any>)
            }
            is List<*> -> buildJsonArray {
                value.forEach { add(valueToJson(it)) }
            }
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            null -> JsonNull
            else -> JsonPrimitive(value.toString())
        }
    }

    /**
     * 将 JsonObject 递归转为 Map<String, Any>
     */
    private fun jsonObjectToMap(json: JsonObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        json.forEach { (key, value) ->
            map[key] = jsonElementToAny(value)
        }
        return map
    }

    private fun jsonElementToAny(element: JsonElement): Any {
        return when (element) {
            is JsonObject -> {
                val map = mutableMapOf<String, Any>()
                element.forEach { (k, v) -> map[k] = jsonElementToAny(v) }
                map
            }
            is JsonArray -> element.map { jsonElementToAny(it) }
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.content == "true" -> true
                    element.content == "false" -> false
                    else -> element.content.toDoubleOrNull() ?: element.content
                }
            }
        }
    }
}