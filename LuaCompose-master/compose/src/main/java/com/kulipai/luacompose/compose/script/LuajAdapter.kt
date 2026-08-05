package com.kulipai.luacompose.compose.script

import com.kulipai.luacompose.compose.script.*
import org.luaj.LuaValue
import org.luaj.Varargs
import org.luaj.lib.VarArgFunction
import org.luaj.lib.jse.CoerceJavaToLua

internal fun ScriptValue.toLuaj(): LuaValue {
    return when (this) {
        is LuajValue -> this.luajObj
        is LuajTable -> this.luajTable
        is LuajFunction -> this.luajFunc
        else -> throw IllegalArgumentException("Unknown ScriptValue implementation: ${this::class}")
    }
}

class LuajValue(val luajObj: LuaValue) : ScriptValue {
    override fun isNil() = luajObj.isnil()
    override fun isBoolean() = luajObj.isboolean()
    override fun isNumber() = luajObj.isnumber()
    override fun isString() = luajObj.isstring()
    override fun isFunction() = luajObj.isfunction()
    override fun isTable() = luajObj.istable()
    override fun isUserdata() = luajObj.isuserdata()
    
    override fun toBoolean() = luajObj.toboolean()
    override fun toInt() = luajObj.toint()
    override fun toLong() = luajObj.tolong()
    override fun toDouble() = luajObj.todouble()
    override fun toFloat(): Float = luajObj.tofloat()
    override fun toStringValue(): String = luajObj.tojstring()
    
    override val stableId: Int
        get() = System.identityHashCode(luajObj)
        
    override fun asTable(): ScriptTable = LuajTable(luajObj.checktable())
    override fun asFunction(): ScriptFunction = LuajFunction(luajObj.checkfunction())
    override fun asUserdata(): Any? = luajObj.touserdata()
}

class LuajTable(val luajTable: org.luaj.LuaTable) : ScriptTable, ScriptValue by LuajValue(luajTable) {
    override fun get(key: String): ScriptValue = LuajEngine.wrap(luajTable.get(key))
    override fun get(index: Int): ScriptValue = LuajEngine.wrap(luajTable.get(index))
    override fun get(key: ScriptValue): ScriptValue = LuajEngine.wrap(luajTable.get(key.toLuaj()))
    override fun rawget(key: String): ScriptValue = LuajEngine.wrap(luajTable.rawget(key))
    
    override fun set(key: String, value: ScriptValue) { luajTable.set(key, value.toLuaj()) }
    override fun set(index: Int, value: ScriptValue) { luajTable.set(index, value.toLuaj()) }
    override fun set(key: ScriptValue, value: ScriptValue) { luajTable.set(key.toLuaj(), value.toLuaj()) }

    override fun rawset(key: String, value: ScriptValue) { luajTable.rawset(key, value.toLuaj()) }
    override fun rawset(key: ScriptValue, value: ScriptValue) { luajTable.rawset(key.toLuaj(), value.toLuaj()) }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LuajTable) return false
        return luajTable === other.luajTable
    }

    override fun hashCode(): Int {
        return luajTable.hashCode()
    }
    
    override fun length(): Int = luajTable.length()
    override fun keys(): List<ScriptValue> {
        val keys = mutableListOf<ScriptValue>()
        var k: LuaValue = LuaValue.NIL
        while (true) {
            val next = luajTable.next(k)
            k = next.arg1()
            if (k.isnil()) break
            keys.add(LuajEngine.wrap(k))
        }
        return keys
    }
    
    override fun setMetatable(meta: ScriptTable) {
        luajTable.setmetatable((meta as LuajTable).luajTable)
    }
    
    override fun getMetatable(): ScriptTable? {
        val meta = luajTable.getmetatable()
        return if (meta != null && meta.istable()) LuajTable(meta as org.luaj.LuaTable) else null
    }
}

class LuajFunction(val luajFunc: org.luaj.LuaFunction) : ScriptFunction, ScriptValue by LuajValue(luajFunc) {
    override fun call(vararg args: ScriptValue): ScriptValue {
        val luajArgs = Array(args.size) { args[it].toLuaj() }
        val varargs = LuaValue.varargsOf(luajArgs)
        val res = luajFunc.invoke(varargs)
        return LuajEngine.wrap(res.arg1())
    }
}

object LuajEngine : ScriptEngine {
    fun wrap(value: LuaValue): ScriptValue {
        if (value.istable()) return LuajTable(value.checktable())
        if (value.isfunction()) return LuajFunction(value.checkfunction())
        return LuajValue(value)
    }

    override fun createNil(): ScriptValue = LuajValue(LuaValue.NIL)
    override fun createValue(value: Boolean): ScriptValue = LuajValue(LuaValue.valueOf(value))
    override fun createValue(value: Int): ScriptValue = LuajValue(LuaValue.valueOf(value))
    override fun createValue(value: Double): ScriptValue = LuajValue(LuaValue.valueOf(value))
    override fun createValue(value: String): ScriptValue = LuajValue(LuaValue.valueOf(value))
    
    override fun createTable(): ScriptTable = LuajTable(org.luaj.LuaTable())
    override fun createUserdata(value: Any): ScriptValue = LuajValue(org.luaj.LuaUserdata(value))
    
    override fun createFunction(callback: ScriptCallback): ScriptFunction {
        return LuajFunction(object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val scriptArgs = Array(args.narg()) { i -> wrap(args.arg(i + 1)) }
                val res = callback.call(scriptArgs)
                return res.toLuaj()
            }
        })
    }
    
    override fun coerceJavaToScript(value: Any?): ScriptValue {
        val luajValue = org.luaj.lib.jse.CoerceJavaToLua.coerce(value)
        return wrap(luajValue)
    }

    @androidx.annotation.Keep
    class MyAddFunction(val callback: ScriptCallback) : org.luaj.lib.TwoArgFunction() {
        @androidx.annotation.Keep
        override fun call(arg1: LuaValue, arg2: LuaValue): LuaValue {
            println("LUA_ANIM_PRINT: MyAddFunction.call called!")
            val scriptArgs = arrayOf(wrap(arg1), wrap(arg2))
            val res = callback.call(scriptArgs)
            val luajRes = res.toLuaj()
            println("LUA_ANIM_PRINT: MyAddFunction.call returning: $luajRes")
            return luajRes
        }
    }

    override fun setAddMetamethod(table: ScriptTable, callback: ScriptCallback) {
        val luajTable = (table as LuajTable).luajTable
        luajTable.set("__add", MyAddFunction(callback))
    }

    override fun createTableWithAdd(addCallback: ScriptCallback): ScriptTable {
        val luaTable = object : org.luaj.LuaTable() {
            override fun add(rhs: LuaValue): LuaValue {
                val res = addCallback.call(arrayOf<ScriptValue>(this@LuajEngine.wrap(this), this@LuajEngine.wrap(rhs)))
                return res.toLuaj()
            }
        }
        return LuajTable(luaTable)
    }
}
