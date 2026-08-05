package com.nirithy.luacompose.script

/**
 * Lua 值的抽象表示，隔离底层 Lua 引擎实现
 */
interface BridgeValue {
    fun isNil(): Boolean
    fun isBoolean(): Boolean
    fun isNumber(): Boolean
    fun isString(): Boolean
    fun isFunction(): Boolean
    fun isTable(): Boolean
    fun isUserdata(): Boolean

    fun toBoolean(): Boolean
    fun toInt(): Int
    fun toLong(): Long
    fun toDouble(): Double
    fun toFloat(): Float
    fun toStringValue(): String

    /** 稳定标识，用于循环引用检测 */
    val stableId: Int

    fun asTable(): BridgeTable
    fun asFunction(): BridgeFunction
    fun asUserdata(): Any?
}