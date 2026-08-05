package com.nirithy.luacompose.script

/**
 * Lua 引擎抽象层，隔离底层 Lua 引擎实现
 *
 * 当前实现：LuaJavaBridgeEngine（基于 com.luajava）
 * 可替换为：LuaJBridgeEngine（基于 org.luaj）
 */
interface BridgeEngine {
    /** 回调函数类型，接收 BridgeValue 数组，返回 BridgeValue */
    fun interface BridgeCallback {
        fun call(args: Array<BridgeValue>): BridgeValue
    }

    fun createNil(): BridgeValue
    fun createValue(value: Boolean): BridgeValue
    fun createValue(value: Int): BridgeValue
    fun createValue(value: Double): BridgeValue
    fun createValue(value: String): BridgeValue
    fun createTable(): BridgeTable
    fun createUserdata(value: Any): BridgeValue
    fun createFunction(callback: BridgeCallback): BridgeFunction

    /** 将 Java 对象转换为 Lua 可用的 BridgeValue */
    fun coerceJavaToScript(value: Any?): BridgeValue
}