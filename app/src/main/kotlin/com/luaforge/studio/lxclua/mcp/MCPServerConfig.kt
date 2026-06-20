package com.luaforge.studio.lxclua.mcp

import java.util.UUID

/** MCP 服务器来源类型 */
enum class MCPServerSource {
    /** 本地插件注册的 MCP 工具 */
    LOCAL_PLUGIN,
    /** 远程 MCP 服务器（URL 连接） */
    REMOTE_URL
}

/** MCP 服务器配置条目 */
data class MCPServerEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val source: MCPServerSource = MCPServerSource.REMOTE_URL,
    val url: String = "",
    val pluginId: String? = null,
    val transport: String = "streamable_http"
) {
    companion object {
        /** 从 JSON 字符串反序列化 */
        fun fromJsonList(json: String): List<MCPServerEntry> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    MCPServerEntry(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", ""),
                        enabled = obj.optBoolean("enabled", true),
                        source = try { MCPServerSource.valueOf(obj.optString("source", "REMOTE_URL")) } catch (_: Exception) { MCPServerSource.REMOTE_URL },
                        url = obj.optString("url", ""),
                        pluginId = obj.optString("pluginId", null),
                        transport = obj.optString("transport", "streamable_http")
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        /** 序列化为 JSON 字符串 */
        fun toJsonList(servers: List<MCPServerEntry>): String {
            val arr = org.json.JSONArray()
            servers.forEach { entry ->
                arr.put(org.json.JSONObject().apply {
                    put("id", entry.id)
                    put("name", entry.name)
                    put("enabled", entry.enabled)
                    put("source", entry.source.name)
                    put("url", entry.url)
                    entry.pluginId?.let { put("pluginId", it) }
                    put("transport", entry.transport)
                })
            }
            return arr.toString()
        }
    }
}