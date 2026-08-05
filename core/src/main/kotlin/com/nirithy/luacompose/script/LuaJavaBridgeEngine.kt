package com.nirithy.luacompose.script

import com.luajava.JavaFunction
import com.luajava.LuaObject
import com.luajava.LuaState
import com.nirithy.luacompose.bridge.ComposeBridgeInstance

// ========== LuaJavaBridgeValue ==========

/**
 * 基于 LuaObject 或原始 Java 值的 BridgeValue 实现
 */
class LuaJavaBridgeValue(private val value: Any?) : BridgeValue {
    private val obj: LuaObject? get() = value as? LuaObject

    override fun isNil(): Boolean = value == null
    override fun isBoolean(): Boolean = value is Boolean
    override fun isNumber(): Boolean = value is Number
    override fun isString(): Boolean = value is String
    override fun isFunction(): Boolean = obj?.isFunction() == true
    override fun isTable(): Boolean = obj?.isTable() == true
    override fun isUserdata(): Boolean = obj?.isUserdata() == true

    override fun toBoolean(): Boolean = (value as? Boolean) ?: (value as? Number)?.let { it.toInt() != 0 } ?: false
    override fun toInt(): Int = (value as? Number)?.toInt() ?: 0
    override fun toLong(): Long = (value as? Number)?.toLong() ?: 0L
    override fun toDouble(): Double = (value as? Number)?.toDouble() ?: 0.0
    override fun toFloat(): Float = (value as? Number)?.toFloat() ?: 0f
    override fun toStringValue(): String = value?.toString() ?: "nil"

    override val stableId: Int get() = System.identityHashCode(value ?: this)

    override fun asTable(): BridgeTable {
        val t = obj ?: throw IllegalStateException("Not a table")
        return LuaJavaBridgeTable(t)
    }

    override fun asFunction(): BridgeFunction {
        val f = obj ?: throw IllegalStateException("Not a function")
        return LuaJavaBridgeFunction(f)
    }

    override fun asUserdata(): Any? = obj?.let {
        try { it.getObject() } catch (e: Exception) { null }
    }

    companion object {
        val NIL = LuaJavaBridgeValue(null)
    }
}

// ========== LuaJavaBridgeTable ==========

class LuaJavaBridgeTable(private val tableObj: LuaObject) : BridgeTable {
    private val valueDelegate = LuaJavaBridgeValue(tableObj)

    // 委托给 LuaJavaBridgeValue
    override fun isNil() = false
    override fun isBoolean() = false
    override fun isNumber() = false
    override fun isString() = false
    override fun isFunction() = false
    override fun isTable() = true
    override fun isUserdata() = false
    override fun toBoolean() = true
    override fun toInt() = 0
    override fun toLong() = 0L
    override fun toDouble() = 0.0
    override fun toFloat() = 0f
    override fun toStringValue() = "table"
    override val stableId get() = valueDelegate.stableId
    override fun asTable() = this
    override fun asFunction(): BridgeFunction = throw IllegalStateException("Not a function")
    override fun asUserdata(): Any? = null

    override fun get(key: String): BridgeValue = wrap(tableObj.getField(key))
    override fun get(index: Int): BridgeValue = wrap(tableObj.getField(index.toString()))
    override fun get(key: BridgeValue): BridgeValue = get(key.toStringValue())
    override fun rawget(key: String): BridgeValue = get(key)

    override fun set(key: String, value: BridgeValue) { tableObj.setField(key, unwrap(value)) }
    override fun set(index: Int, value: BridgeValue) { tableObj.setField(index.toString(), unwrap(value)) }
    override fun set(key: BridgeValue, value: BridgeValue) { set(key.toStringValue(), value) }
    override fun rawset(key: String, value: BridgeValue) { set(key, value) }
    override fun rawset(key: BridgeValue, value: BridgeValue) { set(key.toStringValue(), value) }

    override fun length(): Int {
        return try {
            (tableObj.call("length") as? Number)?.toInt() ?: 0
        } catch (e: Exception) { 0 }
    }

    override fun keys(): List<BridgeValue> = emptyList() // LuaObject 不支持遍历 keys

    override fun getMetatable(): BridgeTable? {
        return try {
            val meta = tableObj.getField("__metatable") // 简化处理
            if (meta != null) LuaJavaBridgeTable(meta as LuaObject) else null
        } catch (e: Exception) { null }
    }

    override fun setMetatable(meta: BridgeTable) {
        // LuaObject 不支持直接设置 metatable，通过 LuaState 实现
    }

    internal fun getTableObj(): LuaObject = tableObj

    companion object {
        fun wrap(obj: Any?): BridgeValue {
            if (obj == null) return LuaJavaBridgeValue.NIL
            if (obj is Boolean) return LuaJavaBridgeValue(obj)
            if (obj is Number) return LuaJavaBridgeValue(obj)
            if (obj is String) return LuaJavaBridgeValue(obj)
            if (obj is LuaObject) {
                return when {
                    obj.isTable() -> LuaJavaBridgeTable(obj)
                    obj.isFunction() -> LuaJavaBridgeFunction(obj)
                    else -> LuaJavaBridgeValue(obj)
                }
            }
            return LuaJavaBridgeValue(obj)
        }

        fun unwrap(value: BridgeValue): Any? {
            if (value is LuaJavaBridgeValue) return (value as LuaJavaBridgeValue).let {
                // 通过反射获取内部 value
                value.asUserdata() ?: when {
                    value.isNil() -> null
                    value.isBoolean() -> value.toBoolean()
                    value.isNumber() -> value.toDouble()
                    value.isString() -> value.toStringValue()
                    else -> null
                }
            }
            return null
        }
    }
}

// ========== LuaJavaBridgeFunction ==========

class LuaJavaBridgeFunction(private val funcObj: LuaObject) : BridgeFunction {
    private val valueDelegate = LuaJavaBridgeValue(funcObj)

    override fun isNil() = false
    override fun isBoolean() = false
    override fun isNumber() = false
    override fun isString() = false
    override fun isFunction() = true
    override fun isTable() = false
    override fun isUserdata() = false
    override fun toBoolean() = true
    override fun toInt() = 0
    override fun toLong() = 0L
    override fun toDouble() = 0.0
    override fun toFloat() = 0f
    override fun toStringValue() = "function"
    override val stableId get() = valueDelegate.stableId
    override fun asTable(): BridgeTable = throw IllegalStateException("Not a table")
    override fun asFunction() = this
    override fun asUserdata(): Any? = null

    override fun call(vararg args: BridgeValue): BridgeValue {
        val javaArgs = args.map { LuaJavaBridgeTable.unwrap(it) }.toTypedArray()
        val result = try {
            funcObj.call(*javaArgs)
        } catch (e: Exception) {
            throw RuntimeException("Lua function call failed: ${e.message}", e)
        }
        return LuaJavaBridgeTable.wrap(result)
    }
}

// ========== LuaJavaBridgeEngine ==========

class LuaJavaBridgeEngine(private val L: LuaState) : BridgeEngine {

    override fun createNil(): BridgeValue = LuaJavaBridgeValue.NIL
    override fun createValue(value: Boolean): BridgeValue = LuaJavaBridgeValue(value)
    override fun createValue(value: Int): BridgeValue = LuaJavaBridgeValue(value)
    override fun createValue(value: Double): BridgeValue = LuaJavaBridgeValue(value)
    override fun createValue(value: String): BridgeValue = LuaJavaBridgeValue(value)

    override fun createTable(): BridgeTable {
        L.newTable()
        val obj = L.getLuaObject(-1)
        L.pop(1)
        return LuaJavaBridgeTable(obj)
    }

    override fun createUserdata(value: Any): BridgeValue {
        L.pushJavaObject(value)
        val obj = L.getLuaObject(-1)
        L.pop(1)
        return LuaJavaBridgeValue(obj)
    }

    override fun createFunction(callback: BridgeEngine.BridgeCallback): BridgeFunction {
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val args = Array<BridgeValue>(top - 1) { i ->
                    val idx = i + 2
                    when {
                        L.isNil(idx) -> LuaJavaBridgeValue.NIL
                        L.isBoolean(idx) -> LuaJavaBridgeValue(L.toBoolean(idx))
                        L.isNumber(idx) -> LuaJavaBridgeValue(L.toNumber(idx))
                        L.isString(idx) -> LuaJavaBridgeValue(L.toString(idx))
                        else -> {
                            val obj = try { L.getLuaObject(idx) } catch (e: Exception) { null }
                            LuaJavaBridgeValue(obj ?: L.toString(idx))
                        }
                    }
                }
                val result = callback.call(args)
                return pushResult(result)
            }
        })
        val obj = L.getLuaObject(-1)
        L.pop(1)
        return LuaJavaBridgeFunction(obj)
    }

    /** 将 BridgeValue 推入 Lua 栈并返回栈上值的数量 */
    private fun pushResult(value: BridgeValue): Int {
        when {
            value.isNil() -> L.pushNil()
            value.isBoolean() -> L.pushBoolean(value.toBoolean())
            value.isNumber() -> {
                val d = value.toDouble()
                if (d == d.toLong().toDouble() && d.toLong() in Int.MIN_VALUE..Int.MAX_VALUE) {
                    L.pushInteger(d.toLong())
                } else {
                    L.pushNumber(d)
                }
            }
            value.isString() -> L.pushString(value.toStringValue())
            value.isTable() -> {
                val table = value as LuaJavaBridgeTable
                L.pushJavaObject(table.getTableObj())
            }
            value.isFunction() -> {
                val func = value as LuaJavaBridgeFunction
                // 通过反射获取 funcObj
                try {
                    val field = func.javaClass.getDeclaredField("funcObj")
                    field.isAccessible = true
                    val obj = field.get(func) as LuaObject
                    L.pushJavaObject(obj)
                } catch (e: Exception) {
                    L.pushNil()
                }
            }
            else -> L.pushNil()
        }
        return 1
    }

    override fun coerceJavaToScript(value: Any?): BridgeValue {
        if (value == null) return LuaJavaBridgeValue.NIL
        // 先检查自定义转换器
        try {
            val bridge = ComposeBridgeInstance.current
            val converter = bridge.converters[value.javaClass]
            if (converter != null) {
                return converter(value)
            }
        } catch (e: Exception) {
            // 如果获取不到当前 bridge，回退到默认转换
        }
        L.pushJavaObject(value)
        val obj = L.getLuaObject(-1)
        L.pop(1)
        return LuaJavaBridgeTable.wrap(obj)
    }
}