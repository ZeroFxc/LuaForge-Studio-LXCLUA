package com.luaforge.studio.lxclua.ai

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** AI 提供商枚举 */
enum class AIProvider(val displayName: String, val defaultEndpoint: String, val defaultModel: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1/messages", "claude-sonnet-4-20250514"),
    SILICONFLOW("硅基流动", "https://api.siliconflow.cn/v1/chat/completions", "Qwen/Qwen3.5-4B"),
    CUSTOM("自定义", "", "");

    companion object {
        fun fromName(name: String): AIProvider =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: CUSTOM
    }
}

/** 单个 AI 提供商配置 */
data class AIProviderConfig(
    /** 唯一标识 */
    val id: String = UUID.randomUUID().toString(),
    /** 用户自定义名称 */
    val name: String = "默认",
    /** AI 提供商类型 */
    val provider: AIProvider = AIProvider.OPENAI,
    /** API 密钥 */
    val apiKey: String = "",
    /** 自定义 API 端点 */
    val customEndpoint: String = "",
    /** 模型名称 */
    val model: String = "",
    /** 温度参数 (0.0 ~ 2.0) */
    val temperature: Float = 0.7f,
    /** 最大输出 token 数 */
    val maxTokens: Int = 4096,
    /** 系统提示词 */
    val systemPrompt: String = "",
    /** 是否启用此提供商 */
    val enabled: Boolean = true,
    /** 是否启用 MCP 工具（仅对支持 function calling 的模型开启） */
    val supportsTools: Boolean = false,
    /** 最大工具调用轮次（防止无限循环），默认 10 */
    val maxToolRounds: Int = 10,
    /** 是否由插件注册 */
    val isPluginRegistered: Boolean = false,
    /** 注册此配置的插件 ID */
    val pluginId: String? = null,
    /** 插件注册的额外模型列表 */
    val registeredModels: List<String> = emptyList()
) {
    /** 获取实际使用的 API 端点 */
    val effectiveEndpoint: String
        get() = when (provider) {
            AIProvider.CUSTOM -> customEndpoint
            else -> provider.defaultEndpoint
        }

    /** 获取实际使用的模型名 */
    val effectiveModel: String
        get() = if (model.isBlank()) provider.defaultModel else model

    /** 是否配置完成（有 API Key 且已启用） */
    val isConfigured: Boolean
        get() = enabled && apiKey.isNotBlank()

    /** 所有可用模型（默认模型 + 插件注册的模型） */
    val allModels: List<String>
        get() {
            val models = mutableListOf<String>()
            if (model.isNotBlank()) models.add(model)
            else if (provider.defaultModel.isNotBlank()) models.add(provider.defaultModel)
            models.addAll(registeredModels)
            return models.distinct()
        }

    companion object {
        /** 从 JSONObject 反序列化 */
        fun fromJson(obj: JSONObject): AIProviderConfig {
            val regModels = mutableListOf<String>()
            val arr = obj.optJSONArray("registeredModels")
            if (arr != null) {
                for (i in 0 until arr.length()) regModels.add(arr.optString(i, ""))
            }
            return AIProviderConfig(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "默认"),
                provider = try { AIProvider.fromName(obj.optString("provider", "OPENAI")) } catch (_: Exception) { AIProvider.OPENAI },
                apiKey = obj.optString("apiKey", ""),
                customEndpoint = obj.optString("customEndpoint", ""),
                model = obj.optString("model", ""),
                temperature = obj.optDouble("temperature", 0.7).toFloat(),
                maxTokens = obj.optInt("maxTokens", 4096),
                systemPrompt = obj.optString("systemPrompt", ""),
                enabled = obj.optBoolean("enabled", true),
                supportsTools = obj.optBoolean("supportsTools", false),
                maxToolRounds = obj.optInt("maxToolRounds", 10),
                isPluginRegistered = obj.optBoolean("isPluginRegistered", false),
                pluginId = obj.optString("pluginId", ""),
                registeredModels = regModels
            )
        }

        /** 序列化为 JSONObject */
        fun toJson(config: AIProviderConfig): JSONObject = JSONObject().apply {
            put("id", config.id)
            put("name", config.name)
            put("provider", config.provider.name)
            put("apiKey", config.apiKey)
            put("customEndpoint", config.customEndpoint)
            put("model", config.model)
            put("temperature", config.temperature.toDouble())
            put("maxTokens", config.maxTokens)
            put("systemPrompt", config.systemPrompt)
            put("enabled", config.enabled)
            put("supportsTools", config.supportsTools)
            put("maxToolRounds", config.maxToolRounds)
            put("isPluginRegistered", config.isPluginRegistered)
            config.pluginId?.let { put("pluginId", it) }
            val arr = JSONArray()
            config.registeredModels.forEach { arr.put(it) }
            put("registeredModels", arr)
        }

        /** 从 JSON 字符串反序列化列表 */
        fun fromJsonList(json: String): List<AIProviderConfig> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        /** 序列化列表为 JSON 字符串 */
        fun toJsonList(configs: List<AIProviderConfig>): String {
            val arr = JSONArray()
            configs.forEach { arr.put(toJson(it)) }
            return arr.toString()
        }
    }
}

/** AI 全局配置数据类 */
data class AIConfigData(
    /** 是否启用 AI 功能（全局开关） */
    val enabled: Boolean = false,
    /** 提供商配置列表 */
    val providers: List<AIProviderConfig> = listOf(AIProviderConfig()),
    /** 当前活跃的提供商 ID */
    val activeProviderId: String? = null,
    /** 是否启用 MCP 服务 */
    val mcpEnabled: Boolean = false,
    /** MCP 服务端点 */
    val mcpEndpoint: String = "http://localhost:8080/mcp",
    /** MCP 传输类型 */
    val mcpTransport: String = "streamable_http",
    /** MCP 服务器列表（多服务器支持） */
    val mcpServers: List<com.luaforge.studio.lxclua.mcp.MCPServerEntry> = emptyList(),
    /** MCP 工具开关状态持久化：serviceName -> (toolName -> enabled) */
    val mcpToolStates: Map<String, Map<String, Boolean>> = emptyMap()
) {
    // ========== 向后兼容的计算属性（委托给活跃提供商） ==========

    /** 当前活跃的提供商配置 */
    val activeProvider: AIProviderConfig?
        get() = providers.find { it.id == activeProviderId } ?: providers.firstOrNull()

    /** 当前提供商类型 */
    val provider: AIProvider get() = activeProvider?.provider ?: AIProvider.OPENAI

    /** 当前 API 密钥 */
    val apiKey: String get() = activeProvider?.apiKey ?: ""

    /** 当前自定义端点 */
    val customEndpoint: String get() = activeProvider?.customEndpoint ?: ""

    /** 当前模型名称 */
    val model: String get() = activeProvider?.model ?: ""

    /** 当前温度参数 */
    val temperature: Float get() = activeProvider?.temperature ?: 0.7f

    /** 当前最大 Token 数 */
    val maxTokens: Int get() = activeProvider?.maxTokens ?: 4096

    /** 当前系统提示词 */
    val systemPrompt: String get() = activeProvider?.systemPrompt ?: ""

    /** 当前实际使用的 API 端点 */
    val effectiveEndpoint: String get() = activeProvider?.effectiveEndpoint ?: ""

    /** 当前实际使用的模型名 */
    val effectiveModel: String get() = activeProvider?.effectiveModel ?: ""

    /** 最大工具调用轮次 */
    val maxToolRounds: Int get() = activeProvider?.maxToolRounds ?: 10

    /** 是否配置完成（有 API Key 且全局启用） */
    val isConfigured: Boolean
        get() = enabled && (activeProvider?.isConfigured ?: false)

    companion object {
        /**
         * 从 JSON 字符串反序列化为 AIConfigData
         * 用于 SD 卡配置文件的读取
         */
        fun fromJson(json: String): AIConfigData? {
            return try {
                val obj = JSONObject(json)
                val version = obj.optInt("version", 1)
                AIConfigData(
                    enabled = obj.optBoolean("enabled", false),
                    providers = AIProviderConfig.fromJsonList(obj.optString("providers", "")),
                    activeProviderId = obj.optString("activeProviderId", "").takeIf { it.isNotBlank() },
                    mcpEnabled = obj.optBoolean("mcpEnabled", false),
                    mcpEndpoint = obj.optString("mcpEndpoint", "http://localhost:8080/mcp"),
                    mcpTransport = obj.optString("mcpTransport", "streamable_http"),
                    mcpServers = com.luaforge.studio.lxclua.mcp.MCPServerEntry.fromJsonList(obj.optString("mcpServers", "")),
                    mcpToolStates = parseToolStatesJsonStatic(obj.optString("mcpToolStates", ""))
                )
            } catch (e: Exception) {
                android.util.Log.e("AIConfigData", "fromJson 解析失败: ${e.message}")
                null
            }
        }

        /**
         * 序列化为 JSON 字符串
         * 用于 SD 卡配置文件的写入
         */
        fun toJson(config: AIConfigData): String {
            val obj = JSONObject().apply {
                put("version", 1)
                put("enabled", config.enabled)
                put("providers", JSONArray().apply {
                    config.providers.forEach { put(AIProviderConfig.toJson(it)) }
                })
                config.activeProviderId?.let { put("activeProviderId", it) }
                put("mcpEnabled", config.mcpEnabled)
                put("mcpEndpoint", config.mcpEndpoint)
                put("mcpTransport", config.mcpTransport)
                put("mcpServers", com.luaforge.studio.lxclua.mcp.MCPServerEntry.toJsonList(config.mcpServers))
                put("mcpToolStates", serializeToolStatesJsonStatic(config.mcpToolStates))
            }
            return obj.toString(2) // 格式化输出，便于阅读
        }

        /** 静态版本：解析工具状态 JSON */
        private fun parseToolStatesJsonStatic(json: String): Map<String, Map<String, Boolean>> {
            if (json.isBlank()) return emptyMap()
            return try {
                val root = JSONObject(json)
                val result = mutableMapOf<String, Map<String, Boolean>>()
                for (serviceName in root.keys()) {
                    val toolsJson = root.getJSONObject(serviceName)
                    val tools = mutableMapOf<String, Boolean>()
                    for (toolName in toolsJson.keys()) {
                        tools[toolName] = toolsJson.getBoolean(toolName)
                    }
                    result[serviceName] = tools
                }
                result
            } catch (e: Exception) {
                emptyMap()
            }
        }

        /** 静态版本：序列化工具状态为 JSON */
        private fun serializeToolStatesJsonStatic(states: Map<String, Map<String, Boolean>>): String {
            if (states.isEmpty()) return ""
            val root = JSONObject()
            for ((serviceName, tools) in states) {
                val toolsJson = JSONObject()
                for ((toolName, enabled) in tools) {
                    toolsJson.put(toolName, enabled)
                }
                root.put(serviceName, toolsJson)
            }
            return root.toString()
        }
    }
}

/** AI 配置持久化管理器 */
object AIConfigManager {
    private val Context.aiDataStore by preferencesDataStore(name = "ai_config")

    private object Keys {
        val ENABLED = booleanPreferencesKey("ai_enabled")
        val PROVIDERS_JSON = stringPreferencesKey("ai_providers_json")
        val ACTIVE_PROVIDER_ID = stringPreferencesKey("ai_active_provider_id")
        val MCP_ENABLED = booleanPreferencesKey("mcp_enabled")
        val MCP_ENDPOINT = stringPreferencesKey("mcp_endpoint")
        val MCP_TRANSPORT = stringPreferencesKey("mcp_transport")
        val MCP_SERVERS = stringPreferencesKey("mcp_servers_json")
        val MCP_TOOL_STATES = stringPreferencesKey("mcp_tool_states_json")

        // 旧版字段（用于迁移）
        val OLD_PROVIDER = stringPreferencesKey("ai_provider")
        val OLD_API_KEY = stringPreferencesKey("ai_api_key")
        val OLD_CUSTOM_ENDPOINT = stringPreferencesKey("ai_custom_endpoint")
        val OLD_MODEL = stringPreferencesKey("ai_model")
        val OLD_TEMPERATURE = floatPreferencesKey("ai_temperature")
        val OLD_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val OLD_SYSTEM_PROMPT = stringPreferencesKey("ai_system_prompt")
    }

    private var _config = AIConfigData()
    private val _configFlow = MutableStateFlow(AIConfigData())

    /** 当前内存中的配置 */
    val currentConfig: AIConfigData get() = _config

    /** 可观察的配置流（UI 用它收集变化） */
    val configFlow: StateFlow<AIConfigData> = _configFlow.asStateFlow()

    /** 更新内存配置并通知所有观察者 */
    private fun setConfig(config: AIConfigData) {
        _config = config
        _configFlow.value = config
        android.util.Log.d("AIConfigManager", "[setConfig] 配置已更新, mcpServers: ${config.mcpServers.size} 个, 服务列表: ${config.mcpServers.map { it.id }}")
    }

    /** 异步加载 AI 配置（优先从 SD 卡加载，其次私有目录） */
    suspend fun loadConfig(context: Context) {
        // 先尝试从 SD 卡加载
        val sdConfig = loadConfigFromSdCard()
        if (sdConfig != null) {
            setConfig(sdConfig)
            // 同步到私有目录，确保 SD 卡不可用时也能工作
            try {
                context.aiDataStore.edit { prefs ->
                    prefs[Keys.ENABLED] = sdConfig.enabled
                    prefs[Keys.PROVIDERS_JSON] = AIProviderConfig.toJsonList(sdConfig.providers)
                    sdConfig.activeProviderId?.let { prefs[Keys.ACTIVE_PROVIDER_ID] = it }
                    prefs[Keys.MCP_ENABLED] = sdConfig.mcpEnabled
                    prefs[Keys.MCP_ENDPOINT] = sdConfig.mcpEndpoint
                    prefs[Keys.MCP_TRANSPORT] = sdConfig.mcpTransport
                    prefs[Keys.MCP_SERVERS] = com.luaforge.studio.lxclua.mcp.MCPServerEntry.toJsonList(sdConfig.mcpServers)
                    prefs[Keys.MCP_TOOL_STATES] = serializeToolStatesJson(sdConfig.mcpToolStates)
                }
            } catch (_: Exception) { }
            android.util.Log.i("AIConfigManager", "[loadConfig] 从 SD 卡加载完成, mcpServers: ${_config.mcpServers.size} 个")
            return
        }

        // SD 卡没有配置，从私有目录加载
        val prefs = context.aiDataStore.data.first()
        val providersJson = prefs[Keys.PROVIDERS_JSON] ?: ""

        val providers: List<AIProviderConfig>
        if (providersJson.isNotBlank()) {
            // 新版格式：从 JSON 加载
            providers = AIProviderConfig.fromJsonList(providersJson)
        } else {
            // 迁移旧版单字段格式
            val oldProvider = prefs[Keys.OLD_PROVIDER]
            if (oldProvider != null) {
                providers = listOf(
                    AIProviderConfig(
                        id = "default",
                        name = "默认",
                        provider = AIProvider.fromName(oldProvider),
                        apiKey = prefs[Keys.OLD_API_KEY] ?: "",
                        customEndpoint = prefs[Keys.OLD_CUSTOM_ENDPOINT] ?: "",
                        model = prefs[Keys.OLD_MODEL] ?: "",
                        temperature = prefs[Keys.OLD_TEMPERATURE] ?: 0.7f,
                        maxTokens = prefs[Keys.OLD_MAX_TOKENS] ?: 4096,
                        systemPrompt = prefs[Keys.OLD_SYSTEM_PROMPT] ?: "",
                        enabled = true
                    )
                )
                // 迁移后立即保存为新格式
                context.aiDataStore.edit { edit ->
                    edit[Keys.PROVIDERS_JSON] = AIProviderConfig.toJsonList(providers)
                    edit[Keys.ACTIVE_PROVIDER_ID] = "default"
                    // 清除旧字段
                    edit.remove(Keys.OLD_PROVIDER)
                    edit.remove(Keys.OLD_API_KEY)
                    edit.remove(Keys.OLD_CUSTOM_ENDPOINT)
                    edit.remove(Keys.OLD_MODEL)
                    edit.remove(Keys.OLD_TEMPERATURE)
                    edit.remove(Keys.OLD_MAX_TOKENS)
                    edit.remove(Keys.OLD_SYSTEM_PROMPT)
                }
            } else {
                providers = listOf(AIProviderConfig())
            }
        }

        val config = AIConfigData(
            enabled = prefs[Keys.ENABLED] ?: false,
            providers = providers,
            activeProviderId = prefs[Keys.ACTIVE_PROVIDER_ID] ?: providers.firstOrNull()?.id,
            mcpEnabled = prefs[Keys.MCP_ENABLED] ?: false,
            mcpEndpoint = prefs[Keys.MCP_ENDPOINT] ?: "http://localhost:8080/mcp",
            mcpTransport = prefs[Keys.MCP_TRANSPORT] ?: "streamable_http",
            mcpServers = com.luaforge.studio.lxclua.mcp.MCPServerEntry.fromJsonList(prefs[Keys.MCP_SERVERS] ?: ""),
            mcpToolStates = parseToolStatesJson(prefs[Keys.MCP_TOOL_STATES] ?: "")
        )
        setConfig(config)
        // 首次从私有目录加载后，同步到 SD 卡（创建备份）
        saveConfigToSdCard(context, config)
        android.util.Log.i("AIConfigManager", "[loadConfig] 从私有目录加载完成, mcpServers: ${_config.mcpServers.size} 个")
    }

    /** 异步保存 AI 配置（同时保存到私有目录和 SD 卡） */
    suspend fun saveConfig(context: Context, config: AIConfigData) {
        setConfig(config)
        context.aiDataStore.edit { prefs ->
            prefs[Keys.ENABLED] = config.enabled
            prefs[Keys.PROVIDERS_JSON] = AIProviderConfig.toJsonList(config.providers)
            config.activeProviderId?.let { prefs[Keys.ACTIVE_PROVIDER_ID] = it }
            prefs[Keys.MCP_ENABLED] = config.mcpEnabled
            prefs[Keys.MCP_ENDPOINT] = config.mcpEndpoint
            prefs[Keys.MCP_TRANSPORT] = config.mcpTransport
            prefs[Keys.MCP_SERVERS] = com.luaforge.studio.lxclua.mcp.MCPServerEntry.toJsonList(config.mcpServers)
            prefs[Keys.MCP_TOOL_STATES] = serializeToolStatesJson(config.mcpToolStates)
        }
        // 同步保存到 SD 卡
        saveConfigToSdCard(context, config)
    }

    /** 更新配置（内存 + 持久化） */
    suspend fun updateConfig(context: Context, transform: (AIConfigData) -> AIConfigData) {
        saveConfig(context, transform(_config))
    }

    /** 仅更新内存中的配置（不持久化，用于插件注册等场景） */
    fun updateInMemory(config: AIConfigData) {
        setConfig(config)
    }

    // ========== 插件注册接口 ==========

    /** 插件注册提供商配置（自动添加到 providers 列表） */
    fun registerProviderConfig(pluginId: String, pluginName: String, provider: AIProvider, model: String, apiKey: String = "") {
        val id = "plugin_$pluginId"
        val existing = _config.providers.find { it.id == id }
        if (existing != null) {
            // 更新已有配置
            setConfig(_config.copy(
                providers = _config.providers.map {
                    if (it.id == id) it.copy(
                        name = pluginName,
                        provider = provider,
                        model = model,
                        apiKey = apiKey.ifBlank { it.apiKey },
                        enabled = true
                    ) else it
                },
                activeProviderId = _config.activeProviderId ?: id
            ))
        } else {
            val newConfig = AIProviderConfig(
                id = id,
                name = pluginName,
                provider = provider,
                model = model,
                apiKey = apiKey,
                enabled = true,
                isPluginRegistered = true,
                pluginId = pluginId
            )
            setConfig(_config.copy(
                providers = _config.providers + newConfig,
                activeProviderId = _config.activeProviderId ?: id
            ))
        }
    }

    /** 插件注册模型到指定提供商 */
    fun registerModelToProvider(pluginId: String, providerId: String, modelName: String) {
        setConfig(_config.copy(
            providers = _config.providers.map {
                if (it.id == providerId && modelName !in it.registeredModels) {
                    it.copy(registeredModels = it.registeredModels - modelName)
                } else it
            }
        ))
    }

    /** 设置插件注册状态 */
    fun unregisterProviderConfig(pluginId: String) {
        val id = "plugin_$pluginId"
        setConfig(_config.copy(
            providers = _config.providers.filter { it.id != id },
            activeProviderId = if (_config.activeProviderId == id) _config.providers.firstOrNull { it.id != id }?.id else _config.activeProviderId
        ))
    }

    /** 插件注销模型 */
    fun unregisterModelFromProvider(providerId: String, modelName: String) {
        setConfig(_config.copy(
            providers = _config.providers.map {
                if (it.id == providerId) {
                    it.copy(registeredModels = it.registeredModels - modelName)
                } else it
            }
        ))
    }

    // ========== MCP 工具状态持久化 ==========

    /** 解析工具状态 JSON: {"serviceName": {"toolName": true, ...}} */
    private fun parseToolStatesJson(json: String): Map<String, Map<String, Boolean>> {
        if (json.isBlank()) return emptyMap()
        return try {
            val root = JSONObject(json)
            val result = mutableMapOf<String, Map<String, Boolean>>()
            for (serviceName in root.keys()) {
                val toolsJson = root.getJSONObject(serviceName)
                val tools = mutableMapOf<String, Boolean>()
                for (toolName in toolsJson.keys()) {
                    tools[toolName] = toolsJson.getBoolean(toolName)
                }
                result[serviceName] = tools
            }
            result
        } catch (e: Exception) {
            android.util.Log.w("AIConfigManager", "解析 MCP 工具状态失败: ${e.message}")
            emptyMap()
        }
    }

    /** 序列化工具状态为 JSON */
    private fun serializeToolStatesJson(states: Map<String, Map<String, Boolean>>): String {
        if (states.isEmpty()) return ""
        val root = JSONObject()
        for ((serviceName, tools) in states) {
            val toolsJson = JSONObject()
            for ((toolName, enabled) in tools) {
                toolsJson.put(toolName, enabled)
            }
            root.put(serviceName, toolsJson)
        }
        return root.toString()
    }

    // ========== SD 卡配置同步 ==========

    /** SD 卡配置目录：/sdcard/LXC-LUA/config/ */
    private fun getSdCardConfigDir(): File? {
        return try {
            val sdRoot = Environment.getExternalStorageDirectory()
            val configDir = File(sdRoot, "LXC-LUA/config")
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            if (configDir.exists() && configDir.canWrite()) configDir else null
        } catch (e: Exception) {
            android.util.Log.w("AIConfigManager", "获取 SD 卡配置目录失败: ${e.message}")
            null
        }
    }

    /** AI 配置的 SD 卡文件路径 */
    private fun getSdCardAiConfigFile(): File? {
        return getSdCardConfigDir()?.let { File(it, "ai_config.json") }
    }

    /**
     * 保存配置到 SD 卡（JSON 格式，便于手动编辑和备份）
     * 失败时静默忽略，不影响主流程
     */
    private fun saveConfigToSdCard(context: Context, config: AIConfigData) {
        try {
            val file = getSdCardAiConfigFile() ?: return
            val json = AIConfigData.toJson(config)
            file.writeText(json, Charsets.UTF_8)
            android.util.Log.d("AIConfigManager", "[SD卡] 配置已保存: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.w("AIConfigManager", "[SD卡] 保存配置失败: ${e.message}")
        }
    }

    /**
     * 从 SD 卡加载配置
     * 仅当 SD 卡配置存在且需要恢复时使用
     * @return 配置数据，失败返回 null
     */
    private fun loadConfigFromSdCard(): AIConfigData? {
        return try {
            val file = getSdCardAiConfigFile() ?: return null
            if (!file.exists() || !file.canRead()) return null
            val json = file.readText(Charsets.UTF_8)
            val config = AIConfigData.fromJson(json)
            android.util.Log.d("AIConfigManager", "[SD卡] 配置已加载: ${file.absolutePath}")
            config
        } catch (e: Exception) {
            android.util.Log.w("AIConfigManager", "[SD卡] 加载配置失败: ${e.message}")
            null
        }
    }

    /**
     * 合并 SD 卡配置到主配置
     * 策略：SD 卡配置存在时，以 SD 卡为准（SD 卡作为主存储）
     * 如果私有目录也有配置，取最新的（通过简单的字段数量判断，或直接以 SD 卡为准）
     */
    private fun mergeSdCardConfig(sdConfig: AIConfigData?): AIConfigData? {
        return sdConfig
    }
}