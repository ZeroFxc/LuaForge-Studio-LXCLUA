package com.difierline.lua.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 传输层辅助类，用于简化 Java 代码创建传输层实例
 */
object TransportHelper {
    
    /**
     * 创建 SSE 传输层
     * @param url SSE 服务器 URL
     * @return SSE 传输层实例
     */
    @JvmStatic
    fun createSseTransport(url: String): SseClientTransport {
        val client = HttpClient(OkHttp.create()) {
            install(SSE)
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
        return SseClientTransport(client, url, null, {})
    }
    
    /**
     * 创建 Streamable HTTP 传输层
     * @param url Streamable HTTP 服务器 URL
     * @return Streamable HTTP 传输层实例
     */
    @JvmStatic
    fun createStreamableHttpTransport(url: String): StreamableHttpClientTransport {
        val client = HttpClient(OkHttp.create()) {
            install(SSE)
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
        return StreamableHttpClientTransport(client, url, null, {})
    }
}