package com.nirithy.luacompose.node

import com.luajava.LuaObject

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

    inline fun <reified T> prop(key: String): T? = props[key] as? T
    fun stringProp(key: String): String? = props[key] as? String
    fun boolProp(key: String, default: Boolean = false): Boolean = (props[key] as? Boolean) ?: default
    fun floatProp(key: String, default: Float = 0f): Float {
        val v = props[key]
        return when (v) { is Number -> v.toFloat(); else -> default }
    }
    fun callback(key: String): LuaObject? = callbacks[key]
}