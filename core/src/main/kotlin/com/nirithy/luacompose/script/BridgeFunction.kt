package com.nirithy.luacompose.script

/**
 * Lua 函数的抽象表示，隔离底层 Lua 引擎实现
 */
interface BridgeFunction : BridgeValue {
    fun call(vararg args: BridgeValue): BridgeValue
}