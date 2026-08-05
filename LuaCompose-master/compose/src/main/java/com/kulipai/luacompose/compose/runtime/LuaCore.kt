package com.kulipai.luacompose.compose.runtime

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.luaj.LuaFunction
import org.luaj.LuaTable
import org.luaj.LuaValue
import java.util.Stack

data class LuaNode(
    val type: String,
    val props: Map<String, Any?>,
    val childScope: LuaScope? = null
)

object LuaBridge {
    private val activeScopes = ThreadLocal.withInitial { Stack<LuaScope>() }
    private val activeNodeLists = ThreadLocal.withInitial { Stack<MutableList<LuaNode>>() }

    fun getActiveScope(): LuaScope? {
        val stack = activeScopes.get()!!
        return if (stack.isNotEmpty()) stack.peek() else null
    }

    fun pushActiveScope(scope: LuaScope) {
        activeScopes.get()!!.push(scope)
    }

    fun popActiveScope() {
        val stack = activeScopes.get()!!
        if (stack.isNotEmpty()) stack.pop()
    }

    fun getActiveNodeList(): MutableList<LuaNode>? {
        val stack = activeNodeLists.get()!!
        return if (stack.isNotEmpty()) stack.peek() else null
    }

    fun pushActiveNodeList(list: MutableList<LuaNode>) {
        activeNodeLists.get()!!.push(list)
    }

    fun popActiveNodeList() {
        val stack = activeNodeLists.get()!!
        if (stack.isNotEmpty()) stack.pop()
    }

    fun luaValueToJava(value: LuaValue): Any? {
        return when {
            value.isnil() -> null
            value.isboolean() -> value.toboolean()
            value.islong() -> {
                val l = value.tolong()
                if (l.toInt().toLong() == l) l.toInt() else l
            }
            value.isint() -> value.toint()
            value.isnumber() -> value.todouble()
            value.isstring() -> value.tojstring()
            value.isuserdata() -> value.touserdata()
            value.isfunction() -> value
            value.istable() -> {
                if (value.get("_isState").toboolean()) {
                    value.get("javaState").touserdata()
                } else {
                    val len = value.len().toint()
                    if (len > 0) {
                        val list = mutableListOf<Any?>()
                        for (i in 1..len) {
                            list.add(luaValueToJava(value.get(i)))
                        }
                        list
                    } else {
                        luaTableToMap(value)
                    }
                }
            }
            else -> value
        }
    }

    fun luaTableToMap(table: LuaValue): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        if (!table.istable()) return map
        val luaTable = table.checktable()
        var key = LuaValue.NIL
        while (true) {
            val varargs = luaTable.next(key)
            key = varargs.arg1()
            if (key.isnil()) break
            val value = varargs.arg(2)
            map[key.tojstring()] = luaValueToJava(value)
        }
        return map
    }

    fun javaToLuaValue(value: Any?): LuaValue {
        return when (value) {
            null -> LuaValue.NIL
            is Boolean -> LuaValue.valueOf(value)
            is Int -> LuaValue.valueOf(value)
            is Long -> LuaValue.valueOf(value.toDouble())
            is Double -> LuaValue.valueOf(value)
            is Float -> LuaValue.valueOf(value.toDouble())
            is String -> LuaValue.valueOf(value)
            is LuaValue -> value
            else -> org.luaj.lib.jse.CoerceJavaToLua.coerce(value)
        }
    }
}

class LuaStateTable(val javaState: LuaState) : LuaTable() {
    init {
        set("_isState", valueOf(true))
        set("javaState", org.luaj.lib.jse.CoerceJavaToLua.coerce(javaState))
        val meta = LuaTable()
        meta.set(TOSTRING, object : org.luaj.lib.OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                return valueOf(javaState.get().toString())
            }
        })
        setmetatable(meta)
    }

    override fun get(key: LuaValue): LuaValue {
        if (key.tojstring() == "value") {
            return LuaBridge.javaToLuaValue(javaState.get())
        }
        return super.get(key)
    }

    override fun set(key: LuaValue, value: LuaValue) {
        if (key.tojstring() == "value") {
            javaState.set(LuaBridge.luaValueToJava(value))
        } else {
            super.set(key, value)
        }
    }
}

class LuaScope(var contentFunc: LuaFunction) : LuaTable() {
    private val _recomposeVersion = mutableStateOf(0)
    val recomposeVersion: State<Int> = _recomposeVersion

    var coroutineScope: CoroutineScope? = null
    var context: Context? = null
    var density: Density? = null
    var configuration: Configuration? = null
    
    var colorScheme: ColorScheme? = null
    var typography: Typography? = null
    var shapes: Shapes? = null

    private val states = mutableMapOf<Any, LuaStateTable>()
    private var statesCount = 0

    private val remembers = mutableMapOf<Any, Any?>()
    private var remembersCount = 0

    private val childScopes = mutableMapOf<Any, LuaScope>()
    private var childScopesCount = 0

    private val accessedStates = mutableSetOf<Any>()
    private val accessedRemembers = mutableSetOf<Any>()
    private val accessedChildScopes = mutableSetOf<Any>()

    init {
        set("state", object : org.luaj.lib.TwoArgFunction() {
            override fun call(scopeObj: LuaValue, initialValue: LuaValue): LuaValue {
                val actualKey = statesCount++
                accessedStates.add(actualKey)
                if (states[actualKey] == null) {
                    val javaState = LuaState(LuaBridge.luaValueToJava(initialValue), this@LuaScope)
                    states[actualKey] = LuaStateTable(javaState)
                }
                return states[actualKey]!!
            }
        })

        set("remember", object : org.luaj.lib.TwoArgFunction() {
            override fun call(scopeObj: LuaValue, initFunc: LuaValue): LuaValue {
                val actualKey = remembersCount++
                accessedRemembers.add(actualKey)
                if (!remembers.containsKey(actualKey)) {
                    val initialValue = initFunc.checkfunction().call()
                    remembers[actualKey] = initialValue
                }
                return remembers[actualKey] as LuaValue
            }
        })

        set("derivedStateOf", object : org.luaj.lib.TwoArgFunction() {
            override fun call(scopeObj: LuaValue, computeFunc: LuaValue): LuaValue {
                val actualKey = statesCount++ 
                accessedStates.add(actualKey)
                if (states[actualKey] == null) {
                    val javaState = LuaDerivedState(computeFunc.checkfunction(), this@LuaScope)
                    states[actualKey] = LuaStateTable(javaState)
                }
                return states[actualKey]!!
            }
        })
    }

    fun getOrCreateChildScope(func: LuaFunction, key: Any? = null): LuaScope {
        val actualKey = key ?: childScopesCount++
        accessedChildScopes.add(actualKey)
        val scope = childScopes.getOrPut(actualKey) { LuaScope(func) }
        scope.contentFunc = func
        return scope
    }

    fun invalidate() {
        _recomposeVersion.value++
    }

    fun execute(vararg args: LuaValue): List<LuaNode> {
        LuaBridge.pushActiveScope(this)
        val rootNodes = mutableListOf<LuaNode>()
        LuaBridge.pushActiveNodeList(rootNodes)
        
        statesCount = 0
        remembersCount = 0
        childScopesCount = 0
        accessedStates.clear()
        accessedRemembers.clear()
        accessedChildScopes.clear()
        
        try {
            contentFunc.invoke(LuaValue.varargsOf(args))
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = "Lua Error: ${e.message}\n\n${android.util.Log.getStackTraceString(e)}"
            rootNodes.add(
                LuaNode(
                    "LuaError",
                    mapOf("text" to errorMsg, "color" to "#ff0000"),
                    null
                )
            )
        } finally {
            LuaBridge.popActiveNodeList()
            LuaBridge.popActiveScope()
        }
        
        states.keys.retainAll(accessedStates)
        remembers.keys.retainAll(accessedRemembers)
        childScopes.keys.retainAll(accessedChildScopes)

        return rootNodes
    }
}

open class LuaState(initialValue: Any?, val scope: LuaScope) {
    open val composeState: State<Any?> = mutableStateOf(initialValue)
    protected val dependentScopes = mutableSetOf<LuaScope>()

    fun registerDependency(scope: LuaScope) {
        dependentScopes.add(scope)
    }

    open fun get(): Any? {
        val active = LuaBridge.getActiveScope()
        if (active != null) {
            dependentScopes.add(active)
        }
        return composeState.value
    }

    open fun set(newValue: Any?) {
        if (composeState is androidx.compose.runtime.MutableState<Any?>) {
            val ms = composeState as androidx.compose.runtime.MutableState<Any?>
            if (ms.value != newValue) {
                ms.value = newValue
                for (scope in dependentScopes) {
                    scope.invalidate()
                }
            }
        }
    }
}

class LuaDerivedState(val computeFunc: LuaFunction, scope: LuaScope) : LuaState(null, scope) {
    override val composeState = androidx.compose.runtime.derivedStateOf {
        LuaBridge.luaValueToJava(computeFunc.call())
    }

    override fun set(newValue: Any?) {
        // Read-only
    }
}

class LuaAnimatableState<T, V : AnimationVector>(
    initialValue: T,
    val typeConverter: TwoWayConverter<T, V>,
    scope: LuaScope
) : LuaState(initialValue, scope) {
    
    var currentSpec: androidx.compose.animation.core.AnimationSpec<T>? = null
    val animatable = Animatable(initialValue, typeConverter)
    fun animateTo(target: T) {
        if (animatable.targetValue == target) return
        scope.coroutineScope?.launch {
            val block: Animatable<T, V>.() -> Unit = {
                if (composeState is androidx.compose.runtime.MutableState<Any?>) {
                    val ms = composeState as androidx.compose.runtime.MutableState<Any?>
                    ms.value = this.value
                }
                for (dep in dependentScopes) {
                    dep.invalidate()
                }
            }
            if (currentSpec != null) {
                animatable.animateTo(target, animationSpec = currentSpec!!, block = block)
            } else {
                animatable.animateTo(target, block = block)
            }
        }
    }
    
    override fun set(newValue: Any?) {
        @Suppress("UNCHECKED_CAST")
        try {
            animateTo(newValue as T)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
