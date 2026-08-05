package com.nirithy.luacompose.script

/**
 * Lua 表的抽象表示，隔离底层 Lua 引擎实现
 */
interface BridgeTable : BridgeValue {
    fun get(key: String): BridgeValue
    fun get(index: Int): BridgeValue
    fun get(key: BridgeValue): BridgeValue
    fun rawget(key: String): BridgeValue

    fun set(key: String, value: BridgeValue)
    fun set(index: Int, value: BridgeValue)
    fun set(key: BridgeValue, value: BridgeValue)
    fun rawset(key: String, value: BridgeValue)
    fun rawset(key: BridgeValue, value: BridgeValue)

    fun length(): Int
    fun keys(): List<BridgeValue>

    fun getMetatable(): BridgeTable?
    fun setMetatable(meta: BridgeTable)
}