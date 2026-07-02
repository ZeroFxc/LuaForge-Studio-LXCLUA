package com.luaforge.studio.lxclua.utils

import androidx.annotation.Keep
import org.json.JSONObject

/**
 * Lua语法分析器工具类
 * 调用luaparser.so库进行Lua代码语法分析
 */
@Keep
object LuaParserUtil {

    // JNI函数编译在LXCLuaCore中，无需单独loadLibrary
    init {
        // 不再需要 System.loadLibrary("luaparser")
    }

    /**
     * 检查解析器是否可用
     * @return 如果解析器可用返回true，否则返回false
     */
    private external fun isParserAvailable(): Boolean

    /**
     * 释放解析器资源
     */
    private external fun releaseParser()

    /**
     * 分析Lua代码语法
     * @param luaCode Lua代码字符串
     * @return 包含分析结果的JSON字符串
     */
    private external fun parseLuaSyntax(luaCode: String): String

    /**
     * 解析Lua代码语法并返回JSON字符串
     * @param luaCode Lua代码字符串
     * @return JSON字符串格式的解析结果
     */
    @JvmStatic
    fun parse(luaCode: String): String {
        return try {
            if (luaCode.isBlank()) {
                return createSuccessJson("请输入内容")
            }

            if (!isParserAvailable()) {
                return createUnavailableJson("原生库未加载或函数未找到")
            }

            parseLuaSyntax(luaCode)

        } catch (e: UnsatisfiedLinkError) {
            createUnavailableJson("解析失败: Lua代码中的原生模块未加载")
        } catch (e: org.json.JSONException) {
            createErrorJson("JSON解析失败")
        } catch (e: Exception) {
            createErrorJson("解析异常")
        }
    }

    /**
     * 解析Lua代码语法并返回JSON对象（供Java端使用）
     * @param luaCode Lua代码字符串
     * @return JSONObject格式的解析结果
     */
    @JvmStatic
    fun parseToJson(luaCode: String): JSONObject {
        val jsonString = parse(luaCode)
        return try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            createErrorJsonObject("JSON解析失败")
        }
    }

    /**
     * 创建成功JSON字符串
     */
    private fun createSuccessJson(message: String): String {
        return JSONObject().apply {
            put("status", true)
            put("message", message)
        }.toString()
    }

    /**
     * 创建错误JSON字符串
     */
    private fun createErrorJson(errorMessage: String): String {
        return JSONObject().apply {
            put("status", false)
            put("line", 1)
            put("message", errorMessage)
            // 标记解析器是否可用：原生模块未加载时 available=false，避免误报波浪线
            put("available", true)
        }.toString()
    }

    /**
     * 创建解析器不可用的错误 JSON（不显示为语法错误波浪线）
     */
    private fun createUnavailableJson(errorMessage: String): String {
        return JSONObject().apply {
            put("status", false)
            put("line", 1)
            put("message", errorMessage)
            put("available", false)
        }.toString()
    }

    /**
     * 创建错误JSON对象
     */
    private fun createErrorJsonObject(errorMessage: String): JSONObject {
        return JSONObject().apply {
            put("status", false)
            put("line", 1)
            put("message", errorMessage)
            put("available", true)
        }
    }

    /**
     * 清理解析器资源
     */
    @JvmStatic
    fun release() {
        try {
            releaseParser()
        } catch (e: Exception) {
            // 静默处理异常
        }
    }

    /**
     * 检查解析器是否初始化成功
     */
    @JvmStatic
    fun isAvailable(): Boolean {
        return try {
            isParserAvailable()
        } catch (e: Exception) {
            false
        }
    }
}