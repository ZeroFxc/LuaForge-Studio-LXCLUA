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
    val transport: String = "streamable_http",
    /** 是否将此 MCP 服务的工具广播到局域网 */
    val broadcastEnabled: Boolean = false,
    /** 广播端口（0 = 自动分配随机端口），仅 broadcastEnabled=true 时有效 */
    val broadcastPort: Int = 0,
    /** 是否锁定端口，锁定后端口不再变化，持久化到配置 */
    val portLocked: Boolean = false
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
                        pluginId = obj.optString("pluginId", ""),
                        transport = obj.optString("transport", "streamable_http"),
                        broadcastEnabled = obj.optBoolean("broadcastEnabled", false),
                        broadcastPort = obj.optInt("broadcastPort", 0),
                        portLocked = obj.optBoolean("portLocked", false)
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
                    put("broadcastEnabled", entry.broadcastEnabled)
                    put("broadcastPort", entry.broadcastPort)
                    put("portLocked", entry.portLocked)
                })
            }
            return arr.toString()
        }
    }
}