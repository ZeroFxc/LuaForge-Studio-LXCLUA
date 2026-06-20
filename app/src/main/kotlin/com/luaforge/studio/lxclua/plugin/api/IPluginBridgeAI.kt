package com.luaforge.studio.lxclua.plugin.api

import com.luaforge.studio.lxclua.plugin.api.callbacks.AIChatCallback
import com.luaforge.studio.lxclua.plugin.api.callbacks.HttpCallback

/**
 * AI 功能桥接接口
 *
 * 为 DEX/APK 插件提供 AI 功能访问
 */
interface IPluginBridgeAI {
    /** 发送聊天请求（同步，返回 JSON 字符串） */
    fun chat(messagesJson: String): String

    /** 发送聊天请求（异步，通过回调返回结果） */
    fun chatAsync(messagesJson: String, callback: HttpCallback)

    /** 发送聊天请求（异步，返回完整信息含 reasoningContent） */
    fun chatAsyncV2(messagesJson: String, callback: AIChatCallback)

    /** 检查 AI 是否可用 */
    fun isAiAvailable(): Boolean

    /** 获取 AI 配置 */
    fun getAiConfig(): String
}