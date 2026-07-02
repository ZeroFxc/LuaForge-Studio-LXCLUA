package com.nirithy.luacompose.node

import com.luajava.LuaObject
import com.nirithy.luacompose.state.StateWrapper

/**
 * Compose UI 节点
 *
 * 注意：不是 data class，因为需要引用相等性来确保 Compose 重组。
 * data class 的 equals 比较所有属性，导致重建后内容相同的新节点被 Compose 跳过。
 */
class ComposeNode(
    val type: String,
    val props: Map<String, Any?> = emptyMap(),
    val children: List<ComposeNode> = emptyList(),
    val callbacks: Map<String, LuaObject> = emptyMap(),
    /** 当 children 为 Lua 函数时存储函数引用，用于 Crossfade/AnimatedContent 等需要动态子节点的场景 */
    val childrenFunc: LuaObject? = null
) {
    /** 手动实现 copy，替代 data class 的 copy() */
    fun copy(
        type: String = this.type,
        props: Map<String, Any?> = this.props,
        children: List<ComposeNode> = this.children,
        callbacks: Map<String, LuaObject> = this.callbacks,
        childrenFunc: LuaObject? = this.childrenFunc
    ): ComposeNode = ComposeNode(type, props, children, callbacks, childrenFunc)

    /**
     * 从 props 中获取值，自动解包 StateWrapper
     * 在 @Composable 上下文中调用 getValue() 会自动订阅 Compose Snapshot 系统
     */
    inline fun <reified T> prop(key: String): T? {
        val value = props[key] ?: return null
        return when {
            value is StateWrapper<*> -> (value.getValue() as? T) ?: (value as T)
            else -> value as? T
        }
    }

    /** 从 props 中获取字符串，自动解包 StateWrapper */
    fun stringProp(key: String): String? {
        val value = props[key] ?: return null
        if (value is StateWrapper<*>) return value.getValue()?.toString()
        return value as? String
    }

    /** 从 props 中获取布尔值，自动解包 StateWrapper */
    fun boolProp(key: String, default: Boolean = false): Boolean {
        val value = props[key] ?: return default
        if (value is StateWrapper<*>) return (value.getValue() as? Boolean) ?: default
        return (value as? Boolean) ?: default
    }

    /** 从 props 中获取浮点数，自动解包 StateWrapper */
    fun floatProp(key: String, default: Float = 0f): Float {
        val value = props[key] ?: return default
        if (value is StateWrapper<*>) {
            val v = value.getValue()
            return when (v) { is Number -> v.toFloat(); else -> default }
        }
        return when (value) { is Number -> value.toFloat(); else -> default }
    }

    fun callback(key: String): LuaObject? = callbacks[key]

    /**
     * 释放节点树中所有 LuaObject 引用，帮助 Lua GC 回收
     * 在旧节点树被替换前调用，避免内存泄漏
     */
    fun release() {
        // 递归释放子节点
        for (child in children) {
            child.release()
        }
    }
}