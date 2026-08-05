package com.kulipai.luacompose.compose.runtime

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Density
import com.kulipai.luacompose.compose.script.ScriptEngine
import com.kulipai.luacompose.compose.script.ScriptFunction
import com.kulipai.luacompose.compose.script.ScriptTable
import com.kulipai.luacompose.compose.script.ScriptValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Stack
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KFunction
import kotlin.reflect.full.companionObjectInstance

data class ComposeNode(
    val type: String,
    val props: Map<String, Any?>,
    val childScope: ComposeScope? = null
)

object ComposeBridge {
    lateinit var engine: ScriptEngine
    val executeLock = java.util.concurrent.locks.ReentrantLock()

    private val activeScopes = ThreadLocal.withInitial { Stack<ComposeScope>() }
    private val activeNodeLists = ThreadLocal.withInitial { Stack<MutableList<ComposeNode>>() }
    private val activeSharedTransitionScopes = ThreadLocal.withInitial { Stack<androidx.compose.animation.SharedTransitionScope>() }
    private val activeAnimatedVisibilityScopes = ThreadLocal.withInitial { Stack<androidx.compose.animation.AnimatedVisibilityScope>() }
    
    val classPrototypes = mutableMapOf<String, ScriptTable>()
    
    fun registerExtension(className: String, ext: ScriptTable) {
        classPrototypes[className] = ext
    }

    val contextReceiversStack = ThreadLocal.withInitial { ArrayDeque<Any>() }
    
    fun pushContextReceiver(receiver: Any) {
        contextReceiversStack.get().addLast(receiver)
    }

    fun popContextReceiver() {
        contextReceiversStack.get().removeLast()
    }

    inline fun <reified T> findContextReceiver(): T? {
        val stack = contextReceiversStack.get()
        return stack.findLast { it is T } as? T
    }
    
    val converters = mutableMapOf<Class<*>, (Any) -> ScriptValue>()

    fun invokeSafe(method: java.lang.reflect.Method, obj: Any?, args: Array<Any?>): Any? {
        val paramTypes = method.parameterTypes
        for (i in args.indices) {
            val arg = args[i]
            val paramType = paramTypes.getOrNull(i) ?: continue
            if (arg != null && paramType.name != arg.javaClass.name && !paramType.isPrimitive && paramType != java.lang.Object::class.java) {
                if (arg is Int) {
                    try {
                        val boxMethod = paramType.getDeclaredMethod("box-impl", Int::class.javaPrimitiveType)
                        args[i] = boxMethod.invoke(null, arg)
                    } catch (e: Exception) { }
                } else if (arg is Float) {
                    try {
                        val boxMethod = paramType.getDeclaredMethod("box-impl", Float::class.javaPrimitiveType)
                        args[i] = boxMethod.invoke(null, arg)
                    } catch (e: Exception) { }
                } else if (arg is Long) {
                    try {
                        val boxMethod = paramType.getDeclaredMethod("box-impl", Long::class.javaPrimitiveType)
                        args[i] = boxMethod.invoke(null, arg)
                    } catch (e: Exception) { }
                } else if (arg is Double) {
                    try {
                        val boxMethod = paramType.getDeclaredMethod("box-impl", Double::class.javaPrimitiveType)
                        args[i] = boxMethod.invoke(null, arg)
                    } catch (e: Exception) { }
                } else if (arg is Boolean) {
                    try {
                        val boxMethod = paramType.getDeclaredMethod("box-impl", Boolean::class.javaPrimitiveType)
                        args[i] = boxMethod.invoke(null, arg)
                    } catch (e: Exception) { }
                }
            }
        }
        return method.invoke(obj, *args)
    }

    fun getActiveScope(): ComposeScope? {
        val stack = activeScopes.get()!!
        return if (stack.isNotEmpty()) stack.peek() else null
    }

    fun pushActiveScope(scope: ComposeScope) {
        activeScopes.get()!!.push(scope)
    }

    fun popActiveScope() {
        val stack = activeScopes.get()!!
        if (stack.isNotEmpty()) stack.pop()
    }

    fun getActiveNodeList(): MutableList<ComposeNode>? {
        val stack = activeNodeLists.get()!!
        return if (stack.isNotEmpty()) stack.peek() else null
    }

    fun pushActiveNodeList(list: MutableList<ComposeNode>) {
        activeNodeLists.get()!!.push(list)
    }

    fun popActiveNodeList() {
        val stack = activeNodeLists.get()!!
        if (stack.isNotEmpty()) stack.pop()
    }

    fun getActiveSharedTransitionScope(): androidx.compose.animation.SharedTransitionScope? {
        val stack = activeSharedTransitionScopes.get()!!
        return if (stack.isNotEmpty()) stack.peek() else null
    }

    fun pushActiveSharedTransitionScope(scope: androidx.compose.animation.SharedTransitionScope) {
        activeSharedTransitionScopes.get()!!.push(scope)
    }

    fun popActiveSharedTransitionScope() {
        val stack = activeSharedTransitionScopes.get()!!
        if (stack.isNotEmpty()) stack.pop()
    }

    fun getActiveAnimatedVisibilityScope(): androidx.compose.animation.AnimatedVisibilityScope? {
        val stack = activeAnimatedVisibilityScopes.get()!!
        return if (stack.isNotEmpty()) stack.peek() else null
    }

    fun pushActiveAnimatedVisibilityScope(scope: androidx.compose.animation.AnimatedVisibilityScope) {
        activeAnimatedVisibilityScopes.get()!!.push(scope)
    }

    fun popActiveAnimatedVisibilityScope() {
        val stack = activeAnimatedVisibilityScopes.get()!!
        if (stack.isNotEmpty()) stack.pop()
    }

    private val activeDrawScopes = ThreadLocal.withInitial { java.util.Stack<androidx.compose.ui.graphics.drawscope.DrawScope>() }
    fun getActiveDrawScope(): androidx.compose.ui.graphics.drawscope.DrawScope? = if (activeDrawScopes.get()!!.isNotEmpty()) activeDrawScopes.get()!!.peek() else null
    fun pushActiveDrawScope(scope: androidx.compose.ui.graphics.drawscope.DrawScope) { activeDrawScopes.get()!!.push(scope) }
    fun popActiveDrawScope() {
        val stack = activeDrawScopes.get()!!
        if (stack.isNotEmpty()) stack.pop()
    }

    private val activePointerInputScopeActions = ThreadLocal.withInitial { java.util.Stack<MutableList<suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit>>() }
    fun getActivePointerInputScopeActions(): MutableList<suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit>? = if (activePointerInputScopeActions.get()!!.isNotEmpty()) activePointerInputScopeActions.get()!!.peek() else null
    fun pushActivePointerInputScopeActions(actions: MutableList<suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit>) { activePointerInputScopeActions.get()!!.push(actions) }
    fun popActivePointerInputScopeActions() {
        val stack = activePointerInputScopeActions.get()!!
        if (stack.isNotEmpty()) stack.pop()
    }

    fun scriptToJava(value: ScriptValue?, visited: MutableList<Any> = mutableListOf(), depth: Int = 0): Any? {
        if (depth > 100) {
            android.util.Log.e("LUA_COMPOSE", "Depth limit exceeded in scriptToJava")
            return null
        }
        if (value == null || value.isNil()) return null
        return when {
            value.isBoolean() -> value.toBoolean()
            value.isNumber() -> {
                val d = value.toDouble()
                if (d == d.toLong().toDouble()) d.toInt() else d.toFloat()
            }
            value.isString() -> value.toStringValue()
            value.isFunction() -> value
            value.isUserdata() -> value.asUserdata()
            value.isTable() -> {
                val table = value.asTable()
                val tableUserdata = table.get("_javaObj").asUserdata()
                val visitedKey = tableUserdata ?: table.stableId
                
                if (visited.contains(visitedKey)) {
                    android.util.Log.w("LUA_COMPOSE", "Circular reference detected in Lua table")
                    return null
                }
                visited.add(visitedKey)

                val isState = table.rawget("_isState")
                val isColor = table.rawget("_javaColor")
                val isDp = table.rawget("_javaDp")
                val isSize = table.rawget("_javaSize")
                val isOffset = table.rawget("_javaOffset")
                val isIntOffset = table.rawget("_javaIntOffset")
                val isStroke = table.rawget("_javaStroke")
                val isJavaObj = table.rawget("_javaObj")

                val result = if (isState.isBoolean() && isState.toBoolean()) {
                    table.rawget("javaState").asUserdata()
                } else if (isJavaObj.isUserdata()) {
                    isJavaObj.asUserdata()
                } else if (isColor.isUserdata()) {
                    isColor.asUserdata()
                } else if (isDp.isUserdata()) {
                    isDp.asUserdata()
                } else if (isSize.isUserdata()) {
                    isSize.asUserdata()
                } else if (isOffset.isUserdata()) {
                    isOffset.asUserdata()
                } else if (isIntOffset.isUserdata()) {
                    isIntOffset.asUserdata()
                } else if (isStroke.isUserdata()) {
                    isStroke.asUserdata()
                } else {
                    val len = table.length()
                    if (len > 0) {
                        val list = mutableListOf<Any?>()
                        for (i in 1..len) {
                            list.add(scriptToJava(table.get(i), visited, depth + 1))
                        }
                        list
                    } else {
                        scriptTableToMap(table, visited, depth + 1)
                    }
                }
                visited.remove(visitedKey)
                result
            }
            else -> value
        }
    }

    fun scriptTableToMap(table: ScriptTable, visited: MutableList<Any> = mutableListOf(), depth: Int = 0): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = table.keys()
        for (key in keys) {
            val kStr = key.toStringValue()
            if (kStr == "_javaObj") continue
            if (depth > 50) {
                android.util.Log.e("LUA_COMPOSE", "Deep recursion depth=$depth, key=$kStr")
            }
            try {
                map[kStr] = scriptToJava(table.get(key), visited, depth + 1)
            } catch (e: StackOverflowError) {
                android.util.Log.e("LUA_COMPOSE", "StackOverflowError when processing key: $kStr")
                throw e
            }
        }
        return map
    }

    
    var luaValueUnwrapper: ((Any?) -> Any?)? = null

    fun unwrapAny(value: Any?): Any? {
        if (value is ScriptValue) {
            val unwrapped = scriptToJava(value)
            if (unwrapped != null && unwrapped !is ScriptValue) return unwrapped
        }
        return luaValueUnwrapper?.invoke(value) ?: value
    }

    fun javaToScript(value: Any?): ScriptValue {
        if (value == null) return engine.createNil()
        return when (value) {
            is Boolean -> engine.createValue(value)
            is Int -> engine.createValue(value)
            is Long -> engine.createValue(value.toDouble())
            is Double -> engine.createValue(value)
            is Float -> engine.createValue(value.toDouble())
            is String -> engine.createValue(value)
            is ScriptValue -> value
            else -> {
                android.util.Log.i("LUA_JAVA_TO_SCRIPT", "Converting object of type ${value::class.java.name}")
                val clazz = value::class.java
                for ((cls, converter) in converters) {
                    if (cls.isAssignableFrom(clazz)) {
                        return converter(value)
                    }
                }
                
                val name = clazz.name
                if (name.startsWith("java.") || name.startsWith("javax.") ||
                    name.startsWith("android.") || 
                    name.startsWith("org.luaj.") ||
                    (name.startsWith("androidx.") && !name.startsWith("androidx.compose.")) ||
                    value is android.content.Context ||
                    value is android.view.View ||
                    value is java.util.Collection<*> ||
                    value is java.util.Map<*, *>) {
                    return engine.coerceJavaToScript(value)
                }
                
                // Fallback to our generic wrapObject!
                wrapObject(value)
            }
        }
    }

    fun coerceArg(argVal: Any?, paramClass: Class<*>): Any? {
        if (argVal is Number) {
            when (paramClass) {
                Float::class.java, Float::class.javaPrimitiveType -> return argVal.toFloat()
                Int::class.java, Int::class.javaPrimitiveType -> return argVal.toInt()
                Long::class.java, Long::class.javaPrimitiveType -> return argVal.toLong()
                Double::class.java, Double::class.javaPrimitiveType -> return argVal.toDouble()
                androidx.compose.ui.unit.Dp::class.java -> return androidx.compose.ui.unit.Dp(argVal.toFloat())
            }
        }
        return argVal
    }
    private class ClassReflectionCache(javaClass: Class<*>) {
        val properties: Map<String, java.lang.reflect.Method>
        val fields: Map<String, java.lang.reflect.Field>
        val functions: Map<String, List<java.lang.reflect.Method>>
        val constructors: List<java.lang.reflect.Constructor<*>>
        init {
            val propMap = mutableMapOf<String, java.lang.reflect.Method>()
            val fieldMap = mutableMapOf<String, java.lang.reflect.Field>()
            val funcMap = mutableMapOf<String, MutableList<java.lang.reflect.Method>>()
            
            val methodsToCache = mutableListOf<java.lang.reflect.Method>()
            var currentClass: Class<*>? = javaClass
            val visitedInterfaces = mutableSetOf<Class<*>>()
            
            while (currentClass != null) {
                methodsToCache.addAll(currentClass.methods)
                
                val interfacesToProcess = currentClass.interfaces.toMutableList()
                while (interfacesToProcess.isNotEmpty()) {
                    val iface = interfacesToProcess.removeAt(0)
                    if (visitedInterfaces.add(iface)) {
                        methodsToCache.addAll(iface.methods)
                        interfacesToProcess.addAll(iface.interfaces)
                    }
                }
                currentClass = currentClass.superclass
            }
            
            for (method in methodsToCache) {
                try {
                    method.isAccessible = true
                } catch (e: Exception) {}
                
                var name = method.name
                val dashIndex = name.indexOf('-')
                if (dashIndex != -1) {
                    name = name.substring(0, dashIndex)
                }
                
                if (method.parameterTypes.isEmpty() && name.startsWith("get") && name.length > 3) {
                    val propName = name[3].lowercaseChar() + name.substring(4)
                    propMap[propName] = method
                    propMap[name.substring(3)] = method
                } else if (method.parameterTypes.isEmpty() && name.startsWith("is") && name.length > 2) {
                    val propName = name[2].lowercaseChar() + name.substring(3)
                    propMap[propName] = method
                    propMap[name.substring(2)] = method
                }
                
                funcMap.getOrPut(name) { mutableListOf() }.add(method)
            }
            for (field in javaClass.fields) {
                fieldMap[field.name] = field
            }
            properties = propMap
            fields = fieldMap
            functions = funcMap
            constructors = javaClass.constructors.toList()
        }
    }
    
    private val javaClassReflectionCache = java.util.concurrent.ConcurrentHashMap<Class<*>, ClassReflectionCache>()

    private fun getReflectionCache(javaClass: Class<*>): ClassReflectionCache {
        return javaClassReflectionCache.getOrPut(javaClass) {
            val cache = ClassReflectionCache(javaClass)
            android.util.Log.e("LUA_REFLECTION", "Cached reflection for: ${javaClass.name}, total cache size: ${javaClassReflectionCache.size}")
            cache
        }
    }

    fun wrapObject(obj: Any): ScriptValue {
        if (obj is ScriptValue) return obj
        val instance = engine.createTable()
        instance.set("_javaObj", engine.createUserdata(obj))
        
        val instanceMeta = engine.createTable()
        val customProperties = engine.createTable()
        
        instanceMeta.set("__index", engine.createFunction { args ->
            val keyArg = args[1]
            val key = keyArg.toStringValue()
            
            val customVal = customProperties.get(keyArg)
            if (customVal != null && !customVal.isNil()) {
                if (customVal.isFunction()) {
                    return@createFunction engine.createFunction { funcArgs ->
                        val isSelf = funcArgs.getOrNull(0)?.isTable() == true && 
                                     !funcArgs.getOrNull(0)!!.asTable().get("_javaObj").isNil() && 
                                     obj.javaClass.isInstance(funcArgs.getOrNull(0)!!.asTable().get("_javaObj").asUserdata())
                        if (isSelf) {
                            return@createFunction customVal.asFunction().call(*funcArgs)
                        } else {
                            val wrappedSelf = javaToScript(obj)
                            val newArgs = arrayOf(wrappedSelf) + funcArgs
                            return@createFunction customVal.asFunction().call(*newArgs)
                        }
                    }
                }
                return@createFunction customVal
            }
            try {
                val cache = getReflectionCache(obj.javaClass)
                
                val prop = cache.properties[key]
                if (prop != null) {
                    return@createFunction javaToScript(prop.invoke(obj))
                }
                val field = cache.fields[key]
                if (field != null) {
                    return@createFunction javaToScript(field.get(obj))
                }
                
                val funcs = cache.functions[key]
                if (!funcs.isNullOrEmpty()) {
                    return@createFunction engine.createFunction { funcArgs ->
                        try {
                            val isSelf = funcArgs.getOrNull(0)?.isTable() == true && !funcArgs.getOrNull(0)!!.asTable().get("_javaObj").isNil() && funcArgs.getOrNull(0)!!.asTable().get("_javaObj").asUserdata() === obj
                            val expectedLuaArgs = funcArgs.size - (if (isSelf) 1 else 0)
                            val targetFunc = funcs.find { f -> 
                                f.parameterCount == expectedLuaArgs
                            } ?: funcs.first()
                            
                            val javaArgs = mutableListOf<Any?>()
                            var luaArgIndex = if (isSelf) 1 else 0
                            for (paramType in targetFunc.parameterTypes) {
                                val nextArg = funcArgs.getOrNull(luaArgIndex++)
                                javaArgs.add(coerceArg(scriptToJava(nextArg), paramType))
                            }
                            val result = targetFunc.invoke(obj, *javaArgs.toTypedArray())
                            return@createFunction javaToScript(result)
                        } catch (e: Exception) {
                            android.util.Log.e("LUA_REFLECTION", "Error calling $key on obj class ${obj.javaClass}", e)
                            e.printStackTrace()
                        }
                        engine.createNil()
                    }
                } else {
                    android.util.Log.e("LUA_REFLECTION", "Method $key not found on class ${obj.javaClass}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Prototype lookup
            var currentClass: Class<*>? = obj.javaClass
            val visited = mutableSetOf<Class<*>>()
            val queue = ArrayDeque<Class<*>>()
            if (currentClass != null) queue.add(currentClass)
            while (queue.isNotEmpty()) {
                val clazz = queue.removeFirst()
                if (!visited.add(clazz)) continue
                
                val proto = classPrototypes[clazz.name]
                if (proto != null) {
                    val luaVal = proto.get(key)
                    if (luaVal != null && !luaVal.isNil()) {
                        if (luaVal.isFunction()) {
                            return@createFunction engine.createFunction { funcArgs ->
                                val isSelf = funcArgs.getOrNull(0)?.isTable() == true && 
                                             !funcArgs.getOrNull(0)!!.asTable().get("_javaObj").isNil() && 
                                             obj.javaClass.isInstance(funcArgs.getOrNull(0)!!.asTable().get("_javaObj").asUserdata())
                                
                                if (isSelf) {
                                    return@createFunction luaVal.asFunction().call(*funcArgs)
                                } else {
                                    // Inject `self` as the first argument because they used `.` instead of `:`
                                    val wrappedSelf = javaToScript(obj)
                                    val newArgs = arrayOf(wrappedSelf) + funcArgs
                                    return@createFunction luaVal.asFunction().call(*newArgs)
                                }
                            }
                        }
                        return@createFunction luaVal
                    }
                }
                
                // For Kotlin interfaces or Companion objects, we might want to check the companion or the interfaces
                if (clazz.superclass != null) queue.addLast(clazz.superclass)
                for (iface in clazz.interfaces) {
                    queue.addLast(iface)
                }
            }
            
            engine.createNil()
        })
        
        instanceMeta.set("__newindex", engine.createFunction { args ->
            val key = args[1].toStringValue()
            val value = args[2]
            try {
                val cache = getReflectionCache(obj.javaClass)
                
                // Try setter method: setPropName
                val setterName = "set" + key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                android.util.Log.d("LUA_REFLECTION", "Trying to set: " + key + " via " + setterName)
                val setterFuncs = cache.functions[setterName]
                if (!setterFuncs.isNullOrEmpty()) {
                    val targetFunc = setterFuncs.first()
                    val paramType = targetFunc.parameterTypes[0]
                    val javaArg = coerceArg(scriptToJava(value), paramType)
                    try { targetFunc.invoke(obj, javaArg); android.util.Log.d("LUA_REFLECTION", "Successfully set " + setterName); } catch (e: Exception) { android.util.Log.e("LUA_REFLECTION", "Failed to set " + setterName, e); }
                    return@createFunction engine.createNil()
                }
                
                // Try to set field directly
                val field = cache.fields[key]
                if (field != null) {
                    val javaArg = coerceArg(scriptToJava(value), field.type)
                    field.set(obj, javaArg)
                    return@createFunction engine.createNil()
                }
            } catch (e: Exception) {
                android.util.Log.e("LUA_REFLECTION", "Error setting $key on obj class ${obj.javaClass}", e)
            }
            customProperties.set(args[1], value)
            engine.createNil()
        })
        
        instance.setMetatable(instanceMeta)
        return instance
    }

    private val Class<*>.objectInstance: Any?
        get() = try {
            this.getDeclaredField("INSTANCE").get(null)
        } catch (e: Exception) {
            null
        }

    private val Class<*>.companionObjectInstance: Any?
        get() = try {
            this.getDeclaredField("Companion").get(null)
        } catch (e: Exception) {
            null
        }

    fun wrapClass(javaClass: Class<*>): ScriptValue {
        val classTable = engine.createTable()
        val classMeta = engine.createTable()
        
        classMeta.set("__index", engine.createFunction { args ->
            val key = args[1].toStringValue()
            val staticObj = javaClass.objectInstance ?: javaClass.companionObjectInstance
            if (staticObj != null) {
                try {
                    val cache = getReflectionCache(staticObj.javaClass)
                    val prop = cache.properties[key]
                    if (prop != null) {
                        return@createFunction javaToScript(prop.invoke(staticObj))
                    }
                    val field = cache.fields[key]
                    if (field != null) {
                        return@createFunction javaToScript(field.get(staticObj))
                    }
                    val funcs = cache.functions[key]
                    if (!funcs.isNullOrEmpty()) {
                        return@createFunction engine.createFunction { funcArgs ->
                            try {
                                val isSelf = funcArgs.getOrNull(0)?.isTable() == true && !funcArgs.getOrNull(0)!!.asTable().get("_javaObj").isNil() && funcArgs.getOrNull(0)!!.asTable().get("_javaObj").asUserdata() === staticObj
                                val expectedLuaArgs = funcArgs.size - (if (isSelf) 1 else 0)
                                val targetFunc = funcs.find { f -> 
                                    f.parameterCount == expectedLuaArgs
                                } ?: funcs.first()
                                
                                val javaArgs = mutableListOf<Any?>()
                                var luaArgIndex = if (isSelf) 1 else 0
                                for (paramType in targetFunc.parameterTypes) {
                                    val nextArg = funcArgs.getOrNull(luaArgIndex++)
                                    javaArgs.add(coerceArg(scriptToJava(nextArg), paramType))
                                }
                                val result = targetFunc.invoke(staticObj, *javaArgs.toTypedArray())
                                return@createFunction javaToScript(result)
                            } catch (e: Exception) {
                                android.util.Log.e("LUA_REFLECTION", "Error calling $key on class ${staticObj.javaClass}", e)
                            }
                            engine.createNil()
                        }
                    }
                } catch(e: Exception) { e.printStackTrace() }
            }
            
            // Fallback for Enums and static fields directly on the class
            try {
                val cache = getReflectionCache(javaClass)
                val field = cache.fields[key]
                if (field != null && java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                    return@createFunction javaToScript(field.get(null))
                }
            } catch(e: Exception) { e.printStackTrace() }
            
            engine.createNil()
        })
        
        classMeta.set("__call", engine.createFunction { args ->
            try {
                val cache = getReflectionCache(javaClass)
                val constructor = cache.constructors.find { it.parameterCount == args.size - 1 } ?: cache.constructors.firstOrNull()
                if (constructor != null) {
                    val javaArgs = mutableListOf<Any?>()
                    var luaArgIndex = 1 // 0 is the table itself
                    for (paramType in constructor.parameterTypes) {
                        val nextArg = args.getOrNull(luaArgIndex++)
                        javaArgs.add(coerceArg(scriptToJava(nextArg), paramType))
                    }
                    val instance = constructor.newInstance(*javaArgs.toTypedArray())
                    return@createFunction javaToScript(instance)
                } else {
                    val staticObj = javaClass.objectInstance ?: javaClass.companionObjectInstance
                    if (staticObj != null) {
                        return@createFunction javaToScript(staticObj)
                    }
                }
            } catch(e: Exception) { e.printStackTrace() }
            engine.createNil()
        })
        
        classTable.setMetatable(classMeta)
        return classTable
    }

    fun createLazyNamespace(pathPrefix: String): com.kulipai.luacompose.compose.script.ScriptTable {
        val nsTable = engine.createTable()
        val nsMeta = engine.createTable()
        nsMeta.set("__index", engine.createFunction { args ->
            val key = args[1].toStringValue()
            val fullPath = if (pathPrefix.isEmpty()) key else "$pathPrefix.$key"
            
            if (key.isNotEmpty() && key[0].isUpperCase()) {
                try {
                    val className = if (!fullPath.startsWith("androidx.compose.")) "androidx.compose.$fullPath" else fullPath
                    val clazz = Class.forName(className)
                    return@createFunction wrapClass(clazz)
                } catch (e: Exception) {
                    
                }
            }
            
            val childNs = createLazyNamespace(fullPath)
            nsTable.set(key, childNs) 
            return@createFunction childNs
        })
        nsTable.setMetatable(nsMeta)
        return nsTable
    }
}

fun createComposeStateTable(javaState: ComposeState): ScriptTable {
    val table = ComposeBridge.engine.createTable()
    table.set("_isState", ComposeBridge.engine.createValue(true))
    table.set("javaState", ComposeBridge.engine.createUserdata(javaState))
    
    val meta = ComposeBridge.engine.createTable()
    meta.set("__tostring", ComposeBridge.engine.createFunction { 
        ComposeBridge.engine.createValue(javaState.get().toString()) 
    })
    meta.set("__index", ComposeBridge.engine.createFunction { args -> 
        val key = args.getOrNull(1)?.toStringValue()
        if (key == "value") {
            ComposeBridge.javaToScript(javaState.get())
        } else {
            ComposeBridge.engine.createNil()
        }
    })
    meta.set("__newindex", ComposeBridge.engine.createFunction { args -> 
        val key = args.getOrNull(1)?.toStringValue()
        if (key == "value") {
            javaState.set(ComposeBridge.scriptToJava(args.getOrNull(2)))
        }
        ComposeBridge.engine.createNil()
    })
    table.setMetatable(meta)
    return table
}

class ComposeScope(var contentFunc: ScriptFunction) {
    private val _recomposeVersion = mutableStateOf(0)
    val recomposeVersion: State<Int> = _recomposeVersion

    var coroutineScope: CoroutineScope? = null
    var context: Context? = null
    var density: Density? = null
    var configuration: Configuration? = null
    
    var colorScheme: ColorScheme? = null
    var typography: Typography? = null
    var shapes: Shapes? = null

    internal val states = mutableMapOf<Any, ScriptTable>()
    internal var statesCount = 0

    val locals = mutableMapOf<String, Any?>()

    internal val remembers = mutableMapOf<Any, Any?>()
    internal var remembersCount = 0

    internal val childScopes = mutableMapOf<Any, ComposeScope>()
    internal var childScopesCount = 0

    internal val launchedEffectKeys = mutableMapOf<Int, List<Any?>>()
    internal val launchedEffectJobs = mutableMapOf<Int, kotlinx.coroutines.Job>()
    internal var launchedEffectsCount = 0

    fun restartLaunchedEffects() {
        launchedEffectKeys.clear()
        launchedEffectJobs.values.forEach { it.cancel() }
        launchedEffectJobs.clear()
    }

    internal val accessedStates = mutableSetOf<Any>()
    internal val accessedRemembers = mutableSetOf<Any>()
    internal val accessedChildScopes = mutableSetOf<Any>()

    fun getOrCreateState(initialValue: ScriptValue): ScriptTable {
        val actualKey = statesCount++
        accessedStates.add(actualKey)
        if (states[actualKey] == null) {
            val javaState = ComposeState(ComposeBridge.scriptToJava(initialValue), this)
            states[actualKey] = createComposeStateTable(javaState)
        }
        return states[actualKey]!!
    }

    internal val rememberKeys = mutableMapOf<Any, List<Any?>>()

    fun getOrCreateRemember(initFunc: ScriptFunction, keys: List<Any?> = emptyList()): ScriptValue {
        val actualKey = remembersCount++
        accessedRemembers.add(actualKey)
        val oldKeys = rememberKeys[actualKey]
        if (!remembers.containsKey(actualKey) || oldKeys != keys) {
            val initialValue = initFunc.call()
            remembers[actualKey] = initialValue
            rememberKeys[actualKey] = keys
        }
        return remembers[actualKey] as ScriptValue
    }

    fun getOrCreateDerivedState(computeFunc: ScriptFunction): ScriptTable {
        val actualKey = statesCount++ 
        accessedStates.add(actualKey)
        if (states[actualKey] == null) {
            val javaState = ComposeDerivedState(computeFunc, this)
            states[actualKey] = createComposeStateTable(javaState)
        }
        return states[actualKey]!!
    }

    // Storage for effect keys
    val effectStates = mutableMapOf<String, Boolean>()

    fun getOrCreateChildScope(func: ScriptFunction, key: Any? = null): ComposeScope {
        val actualKey = key ?: childScopesCount++
        accessedChildScopes.add(actualKey)
        val scope = childScopes.getOrPut(actualKey) { ComposeScope(func) }
        scope.contentFunc = func
        scope.coroutineScope = this.coroutineScope
        scope.context = this.context
        scope.density = this.density
        scope.configuration = this.configuration
        scope.colorScheme = this.colorScheme
        scope.typography = this.typography
        scope.shapes = this.shapes
        return scope
    }

    fun invalidate() {
        _recomposeVersion.value++
    }

    fun execute(vararg args: ScriptValue): List<ComposeNode> {
        ComposeBridge.executeLock.lock()
        try {
            ComposeBridge.pushActiveScope(this)
            val rootNodes = mutableListOf<ComposeNode>()
            ComposeBridge.pushActiveNodeList(rootNodes)
            
            statesCount = 0
            remembersCount = 0
            childScopesCount = 0
            launchedEffectsCount = 0
            accessedStates.clear()
            accessedRemembers.clear()
            accessedChildScopes.clear()
            
            try {
                contentFunc.call(*args)
            } catch (e: Exception) {
                val stackTraceStr = e.stackTrace.take(50).joinToString("\n") { "\tat $it" } + if (e.stackTrace.size > 50) "\n\t... ${e.stackTrace.size - 50} more" else ""
                var fullStr = "${e.javaClass.name}: ${e.message}\n$stackTraceStr"
                var cause = e.cause
                while (cause != null) {
                    val causeStack = cause.stackTrace.take(50).joinToString("\n") { "\tat $it" } + if (cause.stackTrace.size > 50) "\n\t... ${cause.stackTrace.size - 50} more" else ""
                    fullStr += "\nCaused by: ${cause.javaClass.name}: ${cause.message}\n$causeStack"
                    cause = cause.cause
                }
                android.util.Log.e("LUA_ERROR", fullStr)
                val errorMsg = "Script Error: ${e.message}\n\n$fullStr"
                rootNodes.add(
                    ComposeNode(
                        "LuaError", // Keeping name for compatibility with renderer
                        mapOf("text" to errorMsg, "color" to "#ff0000"),
                        null
                    )
                )
            } finally {
                ComposeBridge.popActiveNodeList()
                ComposeBridge.popActiveScope()
            }
            
            states.keys.retainAll(accessedStates)
            remembers.keys.retainAll(accessedRemembers)
            rememberKeys.keys.retainAll(accessedRemembers)
            childScopes.keys.retainAll(accessedChildScopes)

            return rootNodes
        } finally {
            ComposeBridge.executeLock.unlock()
        }
    }

}

open class ComposeState(initialValue: Any?, val scope: ComposeScope) {
    open val composeState: State<Any?> = mutableStateOf(initialValue)
    protected val dependentScopes = java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(java.util.WeakHashMap<ComposeScope, Boolean>()))

    fun registerDependency(scope: ComposeScope) {
        dependentScopes.add(scope)
    }

    fun invalidateDependents() {
        synchronized(dependentScopes) {
            for (scope in dependentScopes) {
                scope.invalidate()
            }
        }
    }

    open fun get(): Any? {
        val active = ComposeBridge.getActiveScope()
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
                synchronized(dependentScopes) {
                    for (scope in dependentScopes) {
                        scope.invalidate()
                    }
                }
            }
        }
    }
}

class ComposeDerivedState(val computeFunc: ScriptFunction, scope: ComposeScope) : ComposeState(null, scope) {
    override val composeState = androidx.compose.runtime.derivedStateOf {
        ComposeBridge.scriptToJava(computeFunc.call())
    }

    override fun set(newValue: Any?) {
        // Read-only
    }
}

class ComposeAnimatableState<T, V : AnimationVector>(
    initialValue: T,
    val typeConverter: TwoWayConverter<T, V>,
    scope: ComposeScope
) : ComposeState(initialValue, scope) {
    
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
                synchronized(dependentScopes) {
                    for (dep in dependentScopes) {
                        dep.invalidate()
                    }
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
