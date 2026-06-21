package com.luaforge.studio.lxclua.ai

import com.luaforge.studio.lxclua.mcp.MCPManager
import com.luaforge.studio.lxclua.mcp.MCPToolCallRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** AI 聊天消息 */
data class ChatMessage(
    val role: String,   // "system" / "user" / "assistant" / "tool"
    val content: String,
    val toolCallId: String? = null,      // 工具调用 ID（tool 角色）
    val toolCalls: List<ToolCall>? = null // AI 的工具调用
)

/** 工具调用 */
data class ToolCall(
    val id: String,
    val functionName: String,
    val functionArgs: String   // JSON 字符串
)

/** 流式工具调用 delta 累积器（用于拼接 SSE 流中的增量 tool_calls） */
private class ToolCallDeltaAccumulator {
    var id: String? = null
    var functionName: String? = null
    val functionArgs = StringBuilder()
}

/** AI 聊天请求 */
data class ChatRequest(
    val messages: List<ChatMessage>,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val stream: Boolean = false,
    // OpenAI 推理力度: none / minimal / low / medium / high / xhigh
    val reasoningEffort: String? = null,
    // SiliconFlow 专用: 启用思考模式
    val enableThinking: Boolean? = null,
    // SiliconFlow: 思维链 token 预算 / Anthropic: budget_tokens
    val thinkingBudget: Int? = null,
    // Anthropic 专用: thinking 类型 ("enabled" / "adaptive")
    val thinkingType: String? = null,
    // Anthropic 专用: adaptive thinking 力度 (low / medium / high / max)
    val thinkingEffort: String? = null,
    /** MCP 工具定义列表（传给 AI 的函数定义） */
    val tools: List<Map<String, Any>>? = null
)

/** AI 聊天响应 */
data class ChatResponse(
    val success: Boolean,
    val content: String? = null,
    val error: String? = null,
    val model: String? = null,
    val usage: TokenUsage? = null,
    /** 思维链推理内容（推理模型的 thinking 过程） */
    val reasoningContent: String? = null,
    /** AI 返回的工具调用 */
    val toolCalls: List<ToolCall>? = null
)

/** Token 用量 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    /** 思维链 token 数（推理模型专用） */
    val reasoningTokens: Int = 0
)

/** AI 服务接口 */
interface IAIService {
    /** 发送聊天请求 */
    suspend fun chat(request: ChatRequest): ChatResponse

    /** 流式聊天 */
    suspend fun chatStream(
        request: ChatRequest,
        onChunk: (String) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onReasoning: ((String) -> Unit)? = null
    )

    /** 检查 AI 服务是否可用 */
    fun isAvailable(): Boolean
}

/** AI 服务实现 */
class AIServiceImpl(private val config: AIConfigData) : IAIService {

    private val client = OkHttpClient.Builder()
        .dns(CustomDns())                         // 绕过 Oppo DNS 代理
        .eventListener(LoggingEventListener())    // 连接诊断日志
        .connectTimeout(30, TimeUnit.SECONDS)     // 连接超时（含 DNS + TCP + TLS）
        .readTimeout(120, TimeUnit.SECONDS)       // 读取超时（流式响应间隔）
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)       // 整个请求总超时
        .build()

    override fun isAvailable(): Boolean = config.isConfigured

    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext ChatResponse(false, error = "AI 服务未启用或未配置 API Key")
        }
        try {
            val (jsonBody, httpBuilder) = buildRequest(request)
            val msgs = jsonBody.optJSONArray("messages")?.length() ?: 0
            val model = jsonBody.optString("model", "?")
            android.util.Log.i("AIService", "[非流式] 开始请求 | 模型: $model | 消息数: $msgs | 端点: ${config.effectiveEndpoint}")
            val httpRequest = httpBuilder
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(httpRequest).execute()
            val body = response.body?.string() ?: ""
            android.util.Log.i("AIService", "[非流式] HTTP ${response.code} | 响应长度: ${body.length}")
            if (response.isSuccessful) {
                val result = parseResponse(body)
                if (result.success) {
                    android.util.Log.i("AIService", "[非流式] 成功 | 模型: ${result.model} | 内容长度: ${result.content?.length ?: 0} | tokens: ${result.usage?.totalTokens ?: "?"}")
                } else {
                    android.util.Log.w("AIService", "[非流式] 解析失败: ${result.error}")
                }
                result
            } else {
                android.util.Log.e("AIService", "[非流式] HTTP ${response.code}: ${body.take(300)}")
                ChatResponse(false, error = "HTTP ${response.code}: ${body.take(500)}")
            }
        } catch (e: Exception) {
            android.util.Log.e("AIService", "[非流式] 异常: ${e.message}", e)
            ChatResponse(false, error = e.message ?: "未知错误")
        }
    }

    override suspend fun chatStream(
        request: ChatRequest,
        onChunk: (String) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onReasoning: ((String) -> Unit)?
    ) = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            android.util.Log.w("AIService", "[流式] 未配置")
            onDone(ChatResponse(false, error = "AI 服务未启用"))
            return@withContext
        }
        // 最多重试 2 次（首次 + 1 次重试）
        var lastError: String? = null
        for (attempt in 1..2) {
            if (attempt > 1) {
                android.util.Log.w("AIService", "[流式] 第 ${attempt} 次重试...")
                kotlinx.coroutines.delay(1000)  // 重试前等待 1 秒
            }
            try {
                val result = executeStreamRequest(request, onChunk, onReasoning)
                if (result.success) {
                    onDone(result)
                    return@withContext
                }
                lastError = result.error
                // 只有超时和网络错误才重试，HTTP 4xx/5xx 不重试
                if (result.error?.contains("timeout", ignoreCase = true) != true &&
                    result.error?.contains("connect", ignoreCase = true) != true &&
                    result.error?.contains("DNS", ignoreCase = true) != true) {
                    break  // 非网络错误，不重试
                }
            } catch (e: CancellationException) {
                android.util.Log.i("AIService", "[流式] 用户取消")
                throw e  // 重新抛出，让协程正常取消
            } catch (e: Exception) {
                lastError = e.message ?: "未知错误"
                android.util.Log.e("AIService", "[流式] 第 ${attempt} 次尝试异常: ${e.message}", e)
                if (e.message?.contains("timeout", ignoreCase = true) != true &&
                    e.message?.contains("connect", ignoreCase = true) != true) {
                    break
                }
            }
        }
        onDone(ChatResponse(false, error = lastError ?: "流式请求失败"))
    }

    /** 执行单次流式请求（提取为独立方法，供重试调用） */
    private suspend fun executeStreamRequest(
        request: ChatRequest,
        onChunk: (String) -> Unit,
        onReasoning: ((String) -> Unit)? = null
    ): ChatResponse {
        val (jsonBody, httpBuilder) = buildRequest(request)
        jsonBody.put("stream", true)
        val model = jsonBody.optString("model", "?")
        val msgs = jsonBody.optJSONArray("messages")?.length() ?: 0
        val endpoint = config.effectiveEndpoint
        val hasTools = jsonBody.has("tools") && jsonBody.optJSONArray("tools")?.length() ?: 0 > 0
        android.util.Log.i("AIService", "[流式] 开始请求 | 模型: $model | 消息数: $msgs | 含tools: $hasTools | 端点: $endpoint")
        // 详细日志：请求体前 2000 字符
        android.util.Log.d("AIService", "[流式] 请求体(截断): ${jsonBody.toString().take(2000)}")
        val httpRequest = httpBuilder
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        // 使用 runInterruptible 包裹阻塞 IO，使协程取消时能中断线程
        return runInterruptible(Dispatchers.IO) {
            val response = client.newCall(httpRequest).execute()
            android.util.Log.i("AIService", "[流式] HTTP ${response.code}")
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                android.util.Log.e("AIService", "[流式] HTTP ${response.code}: ${errorBody.take(500)}")
                return@runInterruptible ChatResponse(false, error = "HTTP ${response.code}: ${errorBody.take(500)}")
            }
            val body = response.body ?: run {
                android.util.Log.w("AIService", "[流式] 响应体为空")
                return@runInterruptible ChatResponse(false, error = "响应体为空")
            }
            val source = body.source()
            val fullContent = StringBuilder()
            // 累积工具调用 delta（按 index 分组）
            val toolAccumulators = mutableMapOf<Int, ToolCallDeltaAccumulator>()
            var lineCount = 0
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                lineCount++
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        android.util.Log.i("AIService", "[流式] 收到 [DONE] | 总行数: $lineCount | 内容长度: ${fullContent.length} | 工具调用: ${toolAccumulators.size}")
                        break
                    }
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = if (delta != null && !delta.isNull("content")) {
                                delta.getString("content")
                            } else ""
                            // 提取推理/思考过程
                            val reasoning = if (delta != null && !delta.isNull("reasoning_content")) {
                                delta.getString("reasoning_content")
                            } else null
                            // 检查是否有 tool_calls
                            val toolCalls = delta?.optJSONArray("tool_calls")
                            val hasToolCalls = toolCalls != null && toolCalls.length() > 0
                            if (content.isNotEmpty()) {
                                fullContent.append(content)
                                onChunk(content)
                            }
                            if (reasoning != null && reasoning.isNotEmpty()) {
                                onReasoning?.invoke(reasoning)
                            }
                            // 累积工具调用 delta
                            if (hasToolCalls) {
                                android.util.Log.i("AIService", "[流式] 收到 tool_calls: ${toolCalls.toString().take(500)}")
                                for (i in 0 until toolCalls.length()) {
                                    val tc = toolCalls.getJSONObject(i)
                                    val idx = tc.optInt("index", -1)
                                    if (idx < 0) continue
                                    val acc = toolAccumulators.getOrPut(idx) { ToolCallDeltaAccumulator() }
                                    // 第一帧：id、type、function.name
                                    if (!tc.isNull("id")) acc.id = tc.getString("id")
                                    val fn = tc.optJSONObject("function")
                                    if (fn != null) {
                                        if (!fn.isNull("name") && fn.getString("name").isNotEmpty()) {
                                            acc.functionName = fn.getString("name")
                                        }
                                        if (!fn.isNull("arguments")) {
                                            acc.functionArgs.append(fn.getString("arguments"))
                                        }
                                    }
                                }
                            }
                            // 非空 delta 但没有 content 时，记录详情
                            if (content.isEmpty() && !hasToolCalls && reasoning == null && delta != null && delta.length() > 0) {
                                android.util.Log.d("AIService", "[流式] delta 无content: ${delta.toString().take(200)}")
                            }
                        }
                    } catch (_: Exception) {
                        // 记录无法解析的原始数据
                        android.util.Log.d("AIService", "[流式] 无法解析: ${data.take(200)}")
                    }
                }
            }
            // 将累积的工具调用 delta 转为 ToolCall 列表
            val toolCalls = toolAccumulators.values.mapNotNull { acc ->
                val name = acc.functionName
                val id = acc.id
                if (name != null && id != null) {
                    ToolCall(id = id, functionName = name, functionArgs = acc.functionArgs.toString())
                } else null
            }
            if (toolCalls.isNotEmpty()) {
                android.util.Log.i("AIService", "[流式] 工具调用已累积: ${toolCalls.map { "${it.functionName}(${it.functionArgs})" }}")
            }
            android.util.Log.i("AIService", "[流式] 完成 | 内容长度: ${fullContent.length} | 总行数: $lineCount")
            ChatResponse(true, content = fullContent.toString(), toolCalls = toolCalls.takeIf { it.isNotEmpty() })
        }
    }

    /** 构建请求 JSON 和 HTTP Builder */
    private fun buildRequest(request: ChatRequest): Pair<JSONObject, Request.Builder> {
        val messages = JSONArray()
        val sysPrompt = config.systemPrompt.ifBlank { null }

        // 如果请求第一条消息已经是 system，将配置中的系统提示词合并进去，避免出现两个 system 消息
        val firstMsg = request.messages.firstOrNull()
        if (firstMsg?.role == "system") {
            val mergedContent = if (sysPrompt != null) "$sysPrompt\n\n${firstMsg.content}" else firstMsg.content
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", mergedContent)
            })
            // 跳过第一条，追加其余消息
            request.messages.drop(1).forEach { msg ->
                val msgJson = JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                    msg.toolCallId?.let { put("tool_call_id", it) }
                    msg.toolCalls?.takeIf { it.isNotEmpty() }?.let { tcs ->
                        val tcArray = JSONArray()
                        tcs.forEach { tc ->
                            tcArray.put(JSONObject().apply {
                                put("id", tc.id)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", tc.functionName)
                                    put("arguments", tc.functionArgs)
                                })
                            })
                        }
                        put("tool_calls", tcArray)
                    }
                }
                messages.put(msgJson)
            }
        } else {
            // 请求中没有 system 消息，若有配置的 system prompt 则作为第一条
            if (sysPrompt != null) {
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", sysPrompt)
                })
            }
            request.messages.forEach { msg ->
                val msgJson = JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                    // 工具调用 ID（tool 角色）
                    msg.toolCallId?.let { put("tool_call_id", it) }
                    // AI 的工具调用（assistant 角色）
                    msg.toolCalls?.takeIf { it.isNotEmpty() }?.let { tcs ->
                        val tcArray = JSONArray()
                        tcs.forEach { tc ->
                            tcArray.put(JSONObject().apply {
                                put("id", tc.id)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", tc.functionName)
                                    put("arguments", tc.functionArgs)
                                })
                            })
                        }
                        put("tool_calls", tcArray)
                    }
                }
                messages.put(msgJson)
            }
        }
        val jsonBody = JSONObject().apply {
            put("model", config.effectiveModel)
            put("messages", messages)
            put("temperature", (request.temperature ?: config.temperature).toDouble())
            put("max_tokens", request.maxTokens ?: config.maxTokens)
            // 各提供商专用推理参数
            when (config.provider) {
                AIProvider.OPENAI -> {
                    // OpenAI: reasoning_effort
                    request.reasoningEffort?.let { put("reasoning_effort", it) }
                }
                AIProvider.ANTHROPIC -> {
                    // Anthropic: thinking 对象
                    val thinkingType = request.thinkingType ?: "enabled"
                    val thinkingObj = JSONObject().apply {
                        put("type", thinkingType)
                        if (thinkingType == "enabled") {
                            put("budget_tokens", request.thinkingBudget ?: 4096)
                        } else {
                            request.thinkingEffort?.let { put("effort", it) }
                        }
                    }
                    put("thinking", thinkingObj)
                }
                AIProvider.SILICONFLOW -> {
                    // SiliconFlow: enable_thinking / thinking_budget / reasoning_effort
                    request.enableThinking?.let { put("enable_thinking", it) }
                    request.thinkingBudget?.let { put("thinking_budget", it) }
                    request.reasoningEffort?.let { put("reasoning_effort", it) }
                }
                AIProvider.CUSTOM -> {
                    // 自定义: 透传所有参数
                    request.reasoningEffort?.let { put("reasoning_effort", it) }
                    request.enableThinking?.let { put("enable_thinking", it) }
                    request.thinkingBudget?.let { put("thinking_budget", it) }
                    request.thinkingType?.let {
                        put("thinking", JSONObject().apply {
                            put("type", it)
                            request.thinkingEffort?.let { eff -> put("effort", eff) }
                        })
                    }
                }
            }
            // 添加 MCP 工具定义
            request.tools?.takeIf { it.isNotEmpty() }?.let { toolsList ->
                val toolsArray = JSONArray()
                toolsList.forEach { tool ->
                    val toolJson = JSONObject()
                    toolJson.put("type", "function")
                    toolJson.put("function", JSONObject().apply {
                        put("name", tool["name"] ?: "")
                        put("description", tool["description"] ?: "")
                        val params = tool["inputSchema"] as? Map<*, *>
                        if (params != null) {
                            put("parameters", mapToJson(params))
                        }
                    })
                    toolsArray.put(toolJson)
                }
                put("tools", toolsArray)
            }
        }
        val httpBuilder = Request.Builder().url(config.effectiveEndpoint)
        when (config.provider) {
            AIProvider.OPENAI, AIProvider.CUSTOM, AIProvider.SILICONFLOW -> {
                httpBuilder.addHeader("Authorization", "Bearer ${config.apiKey}")
            }
            AIProvider.ANTHROPIC -> {
                httpBuilder.addHeader("x-api-key", config.apiKey)
                httpBuilder.addHeader("anthropic-version", "2023-06-01")
            }
        }
        return Pair(jsonBody, httpBuilder)
    }

    /** 解析响应 */
    private fun parseResponse(body: String): ChatResponse {
        return try {
            val json = JSONObject(body)
            // OpenAI 格式
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                val content = if (message != null && !message.isNull("content")) message.getString("content") else ""
                val reasoning = if (message != null && !message.isNull("reasoning_content"))
                    message.getString("reasoning_content").takeIf { it.isNotEmpty() } else null
                // 解析 tool_calls
                val toolCalls = message?.optJSONArray("tool_calls")?.let { tcArray ->
                    (0 until tcArray.length()).map { i ->
                        val tc = tcArray.getJSONObject(i)
                        val func = tc.optJSONObject("function")
                        ToolCall(
                            id = tc.optString("id", ""),
                            functionName = func?.optString("name", "") ?: "",
                            functionArgs = func?.optString("arguments", "{}") ?: "{}"
                        )
                    }
                }
                val usage = json.optJSONObject("usage")?.let {
                    TokenUsage(
                        it.optInt("prompt_tokens", 0),
                        it.optInt("completion_tokens", 0),
                        it.optInt("total_tokens", 0),
                        it.optJSONObject("completion_tokens_details")?.optInt("reasoning_tokens", 0) ?: 0
                    )
                }
                return ChatResponse(true, content = content, model = json.optString("model"), usage = usage, reasoningContent = reasoning, toolCalls = toolCalls)
            }
            // Anthropic 格式
            val contentList = json.optJSONArray("content")
            if (contentList != null && contentList.length() > 0) {
                val text = contentList.getJSONObject(0).optString("text", "")
                val usage = json.optJSONObject("usage")?.let {
                    TokenUsage(
                        it.optInt("input_tokens", 0),
                        it.optInt("output_tokens", 0),
                        it.optInt("input_tokens", 0) + it.optInt("output_tokens", 0)
                    )
                }
                return ChatResponse(true, content = text, model = json.optString("model"), usage = usage)
            }
            ChatResponse(false, error = "无法解析响应: ${body.take(200)}")
        } catch (e: Exception) {
            ChatResponse(false, error = "解析响应失败: ${e.message}")
        }
    }

    /** Map 转 JSONObject（用于工具参数递归转换） */
    private fun mapToJson(map: Map<*, *>): JSONObject {
        val json = JSONObject()
        map.forEach { (k, v) ->
            val key = k.toString()
            when (v) {
                is Map<*, *> -> json.put(key, mapToJson(v))
                is List<*> -> {
                    val arr = JSONArray()
                    v.forEach { item ->
                        when (item) {
                            is Map<*, *> -> arr.put(mapToJson(item))
                            else -> arr.put(item)
                        }
                    }
                    json.put(key, arr)
                }
                else -> json.put(key, v)
            }
        }
        return json
    }
}

/** 自定义 DNS 解析器，绕过 Oppo/OnePlus 的 DNS 代理问题 */
private class CustomDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            // 直接使用系统 DNS 解析，跳过 Oppo 的 oplusDnsOpenProxy
            InetAddress.getAllByName(hostname).toList()
        } catch (e: Exception) {
            android.util.Log.e("AIService", "[DNS] 解析失败: $hostname - ${e.message}")
            Dns.SYSTEM.lookup(hostname)  // 回退到 OkHttp 默认解析
        }
    }
}

/** OkHttp 事件监听器，用于诊断连接问题 */
private class LoggingEventListener : EventListener() {
    private var callStartTime = 0L

    override fun callStart(call: Call) {
        callStartTime = System.currentTimeMillis()
    }

    override fun dnsStart(call: Call, domainName: String) {
        android.util.Log.d("AIService", "[网络] DNS 解析开始: $domainName")
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        val elapsed = System.currentTimeMillis() - callStartTime
        android.util.Log.d("AIService", "[网络] DNS 解析完成: $domainName -> ${inetAddressList.map { it.hostAddress }} | ${elapsed}ms")
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        val elapsed = System.currentTimeMillis() - callStartTime
        android.util.Log.d("AIService", "[网络] TCP 连接开始: ${inetSocketAddress.address.hostAddress}:${inetSocketAddress.port} | ${elapsed}ms")
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        val elapsed = System.currentTimeMillis() - callStartTime
        android.util.Log.d("AIService", "[网络] TCP 连接完成: $protocol | ${elapsed}ms")
    }

    override fun secureConnectStart(call: Call) {
        val elapsed = System.currentTimeMillis() - callStartTime
        android.util.Log.d("AIService", "[网络] TLS 握手开始 | ${elapsed}ms")
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        val elapsed = System.currentTimeMillis() - callStartTime
        android.util.Log.d("AIService", "[网络] TLS 握手完成 | ${elapsed}ms")
    }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
        val elapsed = System.currentTimeMillis() - callStartTime
        android.util.Log.e("AIService", "[网络] 连接失败: ${inetSocketAddress.address?.hostAddress}:${inetSocketAddress.port} | ${elapsed}ms | ${ioe.message}")
    }

    override fun responseHeadersStart(call: Call) {
        val elapsed = System.currentTimeMillis() - callStartTime
        android.util.Log.d("AIService", "[网络] 等待响应头 | ${elapsed}ms")
    }

    override fun responseHeadersEnd(call: Call, response: okhttp3.Response) {
        val elapsed = System.currentTimeMillis() - callStartTime
        android.util.Log.d("AIService", "[网络] 收到响应头: HTTP ${response.code} | ${elapsed}ms")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        val elapsed = System.currentTimeMillis() - callStartTime
        android.util.Log.e("AIService", "[网络] 请求失败: ${ioe.message} | ${elapsed}ms")
    }
}

/** AI 管理器单例 */
object AIManager {
    private var _service: IAIService? = null

    /** 当前正在运行的聊天 Job，用于取消 */
    @Volatile
    private var currentChatJob: kotlinx.coroutines.Job? = null

    /** 当前 AI 服务实例 */
    val service: IAIService?
        get() {
            if (_service == null || !_service!!.isAvailable()) {
                _service = AIServiceImpl(AIConfigManager.currentConfig)
            }
            return if (_service!!.isAvailable()) _service else null
        }

    /** 重新初始化服务（配置变更后调用） */
    fun refresh() {
        _service = AIServiceImpl(AIConfigManager.currentConfig)
    }

    /** 取消当前正在运行的聊天 */
    fun cancelCurrentChat() {
        android.util.Log.i("AIManager", "[取消] cancelCurrentChat 被调用, currentChatJob=${currentChatJob}, isActive=${currentChatJob?.isActive}")
        currentChatJob?.cancel()
        currentChatJob = null
    }

    /**
     * 为请求自动附加 MCP 工具定义
     * 仅当：1) 请求未显式指定 tools  2) 当前提供商开启了 supportsTools  时生效
     */
    private fun enrichWithTools(request: ChatRequest): ChatRequest {
        if (request.tools != null) return request
        val active = AIConfigManager.currentConfig.activeProvider
        if (active?.supportsTools != true) {
            android.util.Log.d("AIManager", "[enrichWithTools] 当前提供商未开启 supportsTools，跳过")
            return request
        }
        val enabledTools = MCPManager.getEnabledServiceTools()
        if (enabledTools.isEmpty()) return request
        val tools = enabledTools.map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "inputSchema" to tool.inputSchema
            )
        }
        android.util.Log.i("AIManager", "[enrichWithTools] 自动附加 ${tools.size} 个 MCP 工具: ${tools.map { it["name"] }}")
        return request.copy(tools = tools)
    }

    /** 发送聊天请求（自动包含已启用的 MCP 工具） */
    suspend fun chat(request: ChatRequest): ChatResponse {
        val srv = service ?: return ChatResponse(false, error = "AI 服务未配置")
        return srv.chat(enrichWithTools(request))
    }

    /** 流式聊天（自动包含已启用的 MCP 工具，自动处理多轮工具调用） */
    suspend fun chatStream(
        request: ChatRequest,
        onChunk: (String) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onReasoning: ((String) -> Unit)? = null,
        onToolCall: ((String, String, String) -> Unit)? = null
    ) {
        val srv = service ?: run {
            onDone(ChatResponse(false, error = "AI 服务未配置"))
            return
        }

        // 记录当前 Job 用于取消
        currentChatJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]

        // 检查是否有启用的工具，决定是否启用多轮工具调用模式
        val enabledTools = MCPManager.getEnabledServiceTools()
        val providerSupportsTools = AIConfigManager.currentConfig.activeProvider?.supportsTools == true
        val hasTools = enabledTools.isNotEmpty() && providerSupportsTools

        if (!hasTools || request.tools != null) {
            // 无工具可用 或 调用方已手动指定 tools，直接走单轮流式
            try {
                srv.chatStream(enrichWithTools(request), onChunk, onDone, onReasoning)
            } finally {
                currentChatJob = null
            }
            return
        }

        // 多轮工具调用模式：AI 调用工具 → 执行 → 回传结果 → 继续对话
        val conversation = request.messages.toMutableList()
        val maxRounds = AIConfigManager.currentConfig.maxToolRounds
        val tools = enabledTools.map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "inputSchema" to tool.inputSchema
            )
        }

        try {
            for (round in 1..maxRounds) {
                val req = ChatRequest(
                    messages = conversation.toList(),
                    tools = tools
                )

                val deferred = kotlinx.coroutines.CompletableDeferred<ChatResponse>()
                srv.chatStream(
                    request = req,
                    onChunk = onChunk,
                    onDone = { response -> deferred.complete(response) },
                    onReasoning = onReasoning
                )

                val response = deferred.await()

                if (!response.success) {
                    onDone(response)
                    return
                }

                val toolCalls = response.toolCalls
                if (toolCalls.isNullOrEmpty()) {
                    // 没有工具调用，对话结束
                    onDone(response)
                    return
                }

                android.util.Log.i("AIManager", "[chatStream] 第 $round 轮收到 ${toolCalls.size} 个工具调用: ${toolCalls.map { it.functionName }}")

                // 添加 assistant 消息（含 tool_calls）
                conversation.add(ChatMessage(
                    role = "assistant",
                    content = response.content ?: "",
                    toolCalls = toolCalls
                ))

                // 执行每个工具调用
                for (tc in toolCalls) {
                    val args = try {
                        org.json.JSONObject(tc.functionArgs).let { json ->
                            val map = mutableMapOf<String, Any>()
                            json.keys().forEach { key ->
                                val value = json.get(key)
                                map[key] = when (value) {
                                    is org.json.JSONObject -> value.toString()
                                    is org.json.JSONArray -> value.toString()
                                    else -> value
                                }
                            }
                            map
                        }
                    } catch (e: Exception) {
                        mapOf<String, Any>()
                    }

                    android.util.Log.i("AIManager", "[chatStream] 执行工具: ${tc.functionName}(${tc.functionArgs})")
                    val result = runBlockingOnIO {
                        val toolResult = MCPManager.callToolAnywhere(tc.functionName, args)
                        toolResult.content.firstOrNull()?.text ?: toolResult.error ?: ""
                    }
                    android.util.Log.i("AIManager", "[chatStream] 工具结果: ${tc.functionName} -> ${result.take(200)}")

                    // 通知调用方工具调用信息（用于 WebUI 展示）
                    onToolCall?.invoke(tc.functionName, tc.functionArgs, result)

                    conversation.add(ChatMessage(
                        role = "tool",
                        content = result,
                        toolCallId = tc.id
                    ))
                }

                android.util.Log.i("AIManager", "[chatStream] 第 $round 轮工具调用完成，继续对话")
            }

            onDone(ChatResponse(false, error = "达到最大工具调用轮数 ($maxRounds)"))
        } finally {
            currentChatJob = null
        }
    }

    /**
     * 带工具调用的多轮聊天
     * 自动处理 AI 的工具调用请求：执行工具 → 回传结果 → 继续对话
     * 最多 10 轮工具调用，防止无限循环
     *
     * @param messages 初始消息列表
     * @param autoIncludeTools 是否自动包含所有启用服务中的工具（默认 true）
     * @param onToolCall 工具调用回调 (toolName, args) -> 可选。如果提供，由调用方处理工具执行
     * @return 最终聊天响应（最后一轮的结果）
     */
    suspend fun chatWithTools(
        messages: List<ChatMessage>,
        autoIncludeTools: Boolean = true,
        onToolCall: ((String, Map<String, Any>) -> String)? = null
    ): ChatResponse {
        val srv = service ?: return ChatResponse(false, error = "AI 服务未配置")
        val conversation = messages.toMutableList()
        var lastResponse: ChatResponse? = null
        val maxRounds = AIConfigManager.currentConfig.maxToolRounds

        for (round in 1..maxRounds) {
            val req = ChatRequest(
                messages = conversation.toList(),
                tools = if (autoIncludeTools) {
                    MCPManager.getEnabledServiceTools().map { tool ->
                        mapOf(
                            "name" to tool.name,
                            "description" to tool.description,
                            "inputSchema" to tool.inputSchema
                        )
                    }.takeIf { it.isNotEmpty() }
                } else null
            )

            val response = srv.chat(req)
            lastResponse = response

            if (!response.success) return response

            val toolCalls = response.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                // 没有工具调用，对话结束
                return response
            }

            // 添加 assistant 消息（含 tool_calls）
            conversation.add(ChatMessage(
                role = "assistant",
                content = response.content ?: "",
                toolCalls = toolCalls
            ))

            // 执行每个工具调用
            for (tc in toolCalls) {
                val args = try {
                    org.json.JSONObject(tc.functionArgs).let { json ->
                        val map = mutableMapOf<String, Any>()
                        json.keys().forEach { key ->
                            val value = json.get(key)
                            map[key] = when (value) {
                                is org.json.JSONObject -> value.toString()
                                is org.json.JSONArray -> value.toString()
                                else -> value
                            }
                        }
                        map
                    }
                } catch (e: Exception) {
                    mapOf<String, Any>()
                }

                val result = if (onToolCall != null) {
                    onToolCall(tc.functionName, args)
                } else {
                    // 统一走 callToolAnywhere：先查插件工具，再查远程服务器，都找不到则返回明确错误
                    runBlockingOnIO {
                        val toolResult = MCPManager.callToolAnywhere(tc.functionName, args)
                        toolResult.content.firstOrNull()?.text ?: toolResult.error ?: ""
                    }
                }

                conversation.add(ChatMessage(
                    role = "tool",
                    content = result,
                    toolCallId = tc.id
                ))
            }

            android.util.Log.i("AIManager", "[chatWithTools] 第 $round 轮完成，执行了 ${toolCalls.size} 个工具调用")
        }

        return lastResponse ?: ChatResponse(false, error = "超过最大工具调用轮数")
    }

    /** 在 IO 线程上执行阻塞调用 */
    private suspend fun <T> runBlockingOnIO(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking(Dispatchers.IO) { block() }
    }
}