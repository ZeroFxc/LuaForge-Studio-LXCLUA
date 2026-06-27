package com.luaforge.studio.lxclua.plugin.bridge

import com.luaforge.studio.lxclua.ai.AIManager
import com.luaforge.studio.lxclua.ai.AIConfigManager
import com.luaforge.studio.lxclua.ai.AIProvider
import com.luaforge.studio.lxclua.ai.AIProviderConfig
import com.luaforge.studio.lxclua.ai.ChatMessage
import com.luaforge.studio.lxclua.ai.ChatRequest
import com.luaforge.studio.lxclua.ai.ChatResponse
import com.luaforge.studio.lxclua.plugin.PluginManager
import com.luajava.LuaFunction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * 插件 AI 桥接类
 *
 * 为 Lua 插件提供 AI 功能访问：
 * - plugin.ai.chat(messages, callback)  -- 聊天请求
 * - plugin.ai.chatAsync(messages, callback)  -- 异步聊天
 * - plugin.ai.chatStream(messages, onChunk, onDone)  -- 流式聊天
 * - plugin.ai.getConfig()  -- 获取 AI 配置
 * - plugin.ai.isAvailable()  -- 检查 AI 是否可用
 * - plugin.ai.cancelAll()  -- 取消所有进行中的请求
 * - plugin.ai.registerProvider(pluginId, name, provider, model, apiKey)  -- 注册提供商
 * - plugin.ai.registerModel(pluginId, providerId, modelName)  -- 注册模型
 * - plugin.ai.unregisterProvider(pluginId)  -- 注销提供商
 * - plugin.ai.getProviders()  -- 获取所有提供商列表
 */
class PluginAI {

    /** 进行中的协程任务，用于取消 */
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private var jobIdCounter = 0L

    private fun nextJobId(): String = "plugin_ai_${++jobIdCounter}"

    /** 取消所有正在进行的聊天请求 */
    fun cancelChat() {
        android.util.Log.i("PluginAI", "[取消] 正在取消 ${activeJobs.size} 个活跃任务")
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        AIManager.cancelCurrentChat()
    }

    /** 安全调用 Lua 回调 */
    private fun safeCall(cb: Any?, vararg args: Any?) {
        try {
            @Suppress("UNCHECKED_CAST")
            (cb as? LuaFunction<Any>)?.call(*args)
        } catch (e: Exception) {
            android.util.Log.e("PluginAI", "Lua 回调异常: ${e.message}", e)
        }
    }

    /** 发送聊天请求（同步，阻塞） */
    fun chat(messages: List<Map<String, String>>, callback: LuaFunction<*>?): String? {
        val msgs = messages.map { ChatMessage(it["role"] ?: "user", it["content"] ?: "") }
        val req = ChatRequest(messages = msgs)
        return try {
            val response = runBlocking { AIManager.chat(req) }
            safeCall(
                callback,
                response.success,
                response.content ?: "",
                response.error ?: "",
                response.model ?: "",
                response.usage?.totalTokens ?: 0,
                response.reasoningContent ?: ""
            )
            response.content
        } catch (e: Exception) {
            safeCall(callback, false, "", e.message ?: "未知错误", "", 0, "")
            null
        }
    }

    /** 发送聊天请求（异步） */
    fun chatAsync(messages: List<Map<String, String>>, callback: LuaFunction<*>) {
        val msgs = messages.map { ChatMessage(it["role"] ?: "user", it["content"] ?: "") }
        val req = ChatRequest(messages = msgs)
        val jobId = nextJobId()
        android.util.Log.i("PluginAI", "[异步] 开始请求 | jobId: $jobId | 消息数: ${msgs.size} | 模型: ${AIConfigManager.currentConfig.effectiveModel}")
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = AIManager.chat(req)
                android.util.Log.i("PluginAI", "[异步] 完成 | jobId: $jobId | success: ${response.success} | 内容长度: ${response.content?.length ?: 0}")
                safeCall(
                    callback,
                    response.success,
                    response.content ?: "",
                    response.error ?: "",
                    response.model ?: "",
                    response.usage?.totalTokens ?: 0,
                    response.reasoningContent ?: ""
                )
            } catch (e: CancellationException) {
                android.util.Log.i("PluginAI", "[异步] 已取消 | jobId: $jobId")
                safeCall(callback, false, "", "已取消", "", 0, "")
            } catch (e: Exception) {
                android.util.Log.e("PluginAI", "[异步] 异常 | jobId: $jobId | ${e.message}", e)
                safeCall(callback, false, "", e.message ?: "未知错误", "", 0, "")
            } finally {
                activeJobs.remove(jobId)
            }
        }
        activeJobs[jobId] = job
    }

    /** 流式聊天 */
    fun chatStream(messages: List<Map<String, String>>, onChunk: LuaFunction<*>, onDone: LuaFunction<*>, onReasoning: LuaFunction<*>?, onToolCall: LuaFunction<*>?, onToolCallDelta: LuaFunction<*>?) {
        val msgs = messages.map { ChatMessage(it["role"] ?: "user", it["content"] ?: "") }
        val req = ChatRequest(messages = msgs)
        val jobId = nextJobId()
        android.util.Log.i("PluginAI", "[流式] 开始请求 | jobId: $jobId | 消息数: ${msgs.size} | 模型: ${AIConfigManager.currentConfig.effectiveModel}")
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch {
            android.util.Log.i("PluginAI", "[流式] 协程已启动 | jobId: $jobId")
            try {
                var chunkCount = 0
                AIManager.chatStream(
                    request = req,
                    onChunk = { chunk ->
                        chunkCount++
                        android.util.Log.d("PluginAI", "[流式] chunk#$chunkCount | jobId: $jobId | 长度: ${chunk.length}")
                        safeCall(onChunk, chunk)
                    },
                    onDone = { response ->
                        android.util.Log.i("PluginAI", "[流式] 完成 | jobId: $jobId | success: ${response.success} | 内容长度: ${response.content?.length ?: 0} | chunks: $chunkCount")
                        safeCall(onDone, response.success, response.content ?: "", response.error ?: "")
                    },
                    onReasoning = if (onReasoning != null) {
                        { reasoning -> safeCall(onReasoning, reasoning) }
                    } else null,
                    onToolCall = if (onToolCall != null) {
                        { index, name, args, result ->
                            android.util.Log.i("PluginAI", "[流式] 工具调用 | jobId: $jobId | #$index $name($args) -> ${result.take(100)}")
                            safeCall(onToolCall, index, name, args, result)
                        }
                    } else null,
                    onToolCallDelta = if (onToolCallDelta != null) {
                        { index, id, name, argsDelta ->
                            android.util.Log.d("PluginAI", "[流式] 工具delta | jobId: $jobId | idx=$index name=$name delta=${argsDelta?.take(50)}")
                            safeCall(onToolCallDelta, index, id ?: "", name ?: "", argsDelta ?: "")
                        }
                    } else null
                )
            } catch (e: CancellationException) {
                android.util.Log.i("PluginAI", "[流式] 已取消 | jobId: $jobId")
                safeCall(onDone, false, "", "已取消")
            } catch (e: Exception) {
                android.util.Log.e("PluginAI", "[流式] 异常 | jobId: $jobId | ${e.message}", e)
                safeCall(onDone, false, "", e.message ?: "流式请求失败")
            } finally {
                activeJobs.remove(jobId)
                android.util.Log.i("PluginAI", "[流式] 协程结束 | jobId: $jobId")
            }
        }
        activeJobs[jobId] = job
        android.util.Log.i("PluginAI", "[流式] 协程已调度 | jobId: $jobId | isActive: ${job.isActive}")
    }

    /** 取消所有进行中的 AI 请求 */
    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    // ========== 流式输出到编辑器 API ==========

    /**
     * 流式聊天，实时输出到编辑器光标位置
     *
     * 每个 chunk 会直接插入到编辑器光标处，产生 AI 逐字输入的视觉效果。
     *
     * @param messages 消息列表
     * @param onDone 完成回调 (success, content, error)
     */
    fun chatStreamToEditor(messages: List<Map<String, String>>, onDone: LuaFunction<*>?) {
        val msgs = messages.map { ChatMessage(it["role"] ?: "user", it["content"] ?: "") }
        val req = ChatRequest(messages = msgs)
        val jobId = nextJobId()
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        android.util.Log.i("PluginAI", "[流式→编辑器] 开始请求 | jobId: $jobId | 消息数: ${msgs.size}")
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                var chunkCount = 0
                AIManager.chatStream(
                    request = req,
                    onChunk = { chunk ->
                        chunkCount++
                        // 在主线程插入到编辑器
                        mainHandler.post {
                            try {
                                PluginManager.activeViewModel?.getActiveEditor()?.let { editor ->
                                    val cursor = editor.cursor
                                    editor.text.insert(cursor.leftLine, cursor.leftColumn, chunk)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PluginAI", "[流式→编辑器] 插入失败: ${e.message}")
                            }
                        }
                    },
                    onDone = { response ->
                        android.util.Log.i("PluginAI", "[流式→编辑器] 完成 | jobId: $jobId | success: ${response.success} | 内容长度: ${response.content?.length ?: 0} | chunks: $chunkCount")
                        safeCall(onDone, response.success, response.content ?: "", response.error ?: "")
                    }
                )
            } catch (e: CancellationException) {
                android.util.Log.i("PluginAI", "[流式→编辑器] 已取消 | jobId: $jobId")
                safeCall(onDone, false, "", "已取消")
            } catch (e: Exception) {
                android.util.Log.e("PluginAI", "[流式→编辑器] 异常 | jobId: $jobId | ${e.message}", e)
                safeCall(onDone, false, "", e.message ?: "流式请求失败")
            } finally {
                activeJobs.remove(jobId)
            }
        }
        activeJobs[jobId] = job
    }

    /**
     * 一次性聊天，完成后将结果插入到编辑器光标位置
     *
     * @param messages 消息列表
     * @param onDone 完成回调 (success, content, error)
     */
    fun chatToEditor(messages: List<Map<String, String>>, onDone: LuaFunction<*>?) {
        val msgs = messages.map { ChatMessage(it["role"] ?: "user", it["content"] ?: "") }
        val req = ChatRequest(messages = msgs)
        val jobId = nextJobId()
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        android.util.Log.i("PluginAI", "[一次性→编辑器] 开始请求 | jobId: $jobId | 消息数: ${msgs.size}")
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = AIManager.chat(req)
                android.util.Log.i("PluginAI", "[一次性→编辑器] 完成 | jobId: $jobId | success: ${response.success}")
                if (response.success && response.content != null) {
                    mainHandler.post {
                        try {
                            PluginManager.activeViewModel?.getActiveEditor()?.let { editor ->
                                val cursor = editor.cursor
                                editor.text.insert(cursor.leftLine, cursor.leftColumn, "\n" + response.content + "\n")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PluginAI", "[一次性→编辑器] 插入失败: ${e.message}")
                        }
                    }
                }
                safeCall(onDone, response.success, response.content ?: "", response.error ?: "")
            } catch (e: CancellationException) {
                android.util.Log.i("PluginAI", "[一次性→编辑器] 已取消 | jobId: $jobId")
                safeCall(onDone, false, "", "已取消")
            } catch (e: Exception) {
                android.util.Log.e("PluginAI", "[一次性→编辑器] 异常 | jobId: $jobId | ${e.message}", e)
                safeCall(onDone, false, "", e.message ?: "未知错误")
            } finally {
                activeJobs.remove(jobId)
            }
        }
        activeJobs[jobId] = job
    }

    /** 检查 AI 是否可用 */
    fun isAvailable(): Boolean {
        val available = AIManager.service != null
        android.util.Log.i("PluginAI", "isAvailable: $available | config: ${AIConfigManager.currentConfig.isConfigured}")
        return available
    }

    /** 获取 AI 配置信息 */
    fun getConfig(): Map<String, Any> {
        val cfg = AIConfigManager.currentConfig
        return mapOf(
            "provider" to cfg.provider.name,
            "model" to cfg.effectiveModel,
            "endpoint" to cfg.effectiveEndpoint,
            "temperature" to cfg.temperature.toDouble(),
            "maxTokens" to cfg.maxTokens,
            "enabled" to cfg.enabled,
            "isConfigured" to cfg.isConfigured
        )
    }

    // ========== 插件注册提供商/模型接口 ==========

    /**
     * 插件注册 AI 提供商配置
     * @param pluginId 插件 ID
     * @param name 提供商名称
     * @param provider 提供商类型（"OPENAI"/"ANTHROPIC"/"SILICONFLOW"/"CUSTOM"）
     * @param model 默认模型
     * @param apiKey API 密钥（可选，为空则使用全局配置）
     */
    fun registerProvider(pluginId: String, name: String, provider: String, model: String, apiKey: String) {
        val prov = try { AIProvider.fromName(provider) } catch (_: Exception) { AIProvider.CUSTOM }
        AIConfigManager.registerProviderConfig(pluginId, name, prov, model, apiKey)
        android.util.Log.i("PluginAI", "插件 [$pluginId] 注册了 AI 提供商: $name ($prov)")
    }

    /**
     * 插件注册模型到指定提供商
     * @param pluginId 插件 ID
     * @param providerId 目标提供商 ID
     * @param modelName 模型名称
     */
    fun registerModel(pluginId: String, providerId: String, modelName: String) {
        AIConfigManager.registerModelToProvider(pluginId, providerId, modelName)
        android.util.Log.i("PluginAI", "插件 [$pluginId] 注册了模型: $modelName -> $providerId")
    }

    /** 插件注销提供商配置 */
    fun unregisterProvider(pluginId: String) {
        AIConfigManager.unregisterProviderConfig(pluginId)
        android.util.Log.i("PluginAI", "插件 [$pluginId] 注销了 AI 提供商")
    }

    /** 插件注销模型 */
    fun unregisterModel(providerId: String, modelName: String) {
        AIConfigManager.unregisterModelFromProvider(providerId, modelName)
    }

    /** 获取所有提供商配置列表 */
    fun getProviders(): List<Map<String, Any?>> {
        return AIConfigManager.currentConfig.providers.map { p ->
            mapOf(
                "id" to p.id,
                "name" to p.name,
                "provider" to p.provider.name,
                "model" to p.effectiveModel,
                "endpoint" to p.effectiveEndpoint,
                "enabled" to p.enabled,
                "isConfigured" to p.isConfigured,
                "isPluginRegistered" to p.isPluginRegistered,
                "pluginId" to (p.pluginId ?: ""),
                "allModels" to p.allModels
            )
        }
    }

    /** 获取当前活跃的提供商 ID */
    fun getActiveProviderId(): String {
        return AIConfigManager.currentConfig.activeProviderId ?: ""
    }
}