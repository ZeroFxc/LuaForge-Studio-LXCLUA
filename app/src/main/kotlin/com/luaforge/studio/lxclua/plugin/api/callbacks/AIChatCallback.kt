package com.luaforge.studio.lxclua.plugin.api.callbacks

/**
 * AI 聊天回调接口（DEX 插件用）
 * 
 * 相比 HttpCallback，多了 model、tokens、reasoningContent 参数
 */
interface AIChatCallback {
    fun onResult(
        success: Boolean,
        content: String?,
        error: String?,
        model: String?,
        tokens: Int,
        reasoningContent: String?
    )
}