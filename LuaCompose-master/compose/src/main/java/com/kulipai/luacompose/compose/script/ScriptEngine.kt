package com.kulipai.luacompose.compose.script

interface ScriptValue {
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
    
    val stableId: Int
    
    fun asTable(): ScriptTable
    fun asFunction(): ScriptFunction
    fun asUserdata(): Any?
}

interface ScriptTable : ScriptValue {
    fun get(key: String): ScriptValue
    fun get(index: Int): ScriptValue
    fun get(key: ScriptValue): ScriptValue
    fun rawget(key: String): ScriptValue
    
    fun set(key: String, value: ScriptValue)
    fun set(index: Int, value: ScriptValue)
    fun set(key: ScriptValue, value: ScriptValue)

    fun rawset(key: String, value: ScriptValue)
    fun rawset(key: ScriptValue, value: ScriptValue)
    
    fun length(): Int
    fun keys(): List<ScriptValue>
    fun setMetatable(meta: ScriptTable)
    fun getMetatable(): ScriptTable?
}

interface ScriptFunction : ScriptValue {
    fun call(vararg args: ScriptValue): ScriptValue
}

fun interface ScriptCallback {
    fun call(args: Array<ScriptValue>): ScriptValue
}

interface ScriptEngine {
    fun createNil(): ScriptValue
    fun createValue(value: Boolean): ScriptValue
    fun createValue(value: Int): ScriptValue
    fun createValue(value: Double): ScriptValue
    fun createValue(value: String): ScriptValue
    fun createTable(): ScriptTable
    fun createUserdata(value: Any): ScriptValue
    fun createFunction(callback: ScriptCallback): ScriptFunction
    fun coerceJavaToScript(value: Any?): ScriptValue
    fun setAddMetamethod(table: ScriptTable, callback: ScriptCallback)
    fun createTableWithAdd(addCallback: ScriptCallback): ScriptTable
}
