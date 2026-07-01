package com.nirithy.luacompose.reflect

import com.nirithy.luacompose.*
import com.luajava.LuaState
import com.nirithy.luacompose.bridge.LazyNamespace
import java.lang.reflect.*

/**
 * Java 对象 → Lua Table 包装器（移植自 kulipai LuaCompose）
 *
 * 核心机制：
 * - wrapObject(L, obj) 将任意 Java 对象包装为 Lua table
 * - __index 元方法：属性 getter（prop）、方法调用（按参数数量匹配）
 * - __newindex 元方法：属性 setter（setXxx）、字段直接赋值
 * - wrapClass(L, className) 包装 Java 类，支持 __call 构造函数调用
 *
 * 与 LazyNamespace 的互补关系：
 *   LazyNamespace: 找到类和可组合函数（compose.material3.Button → ButtonKt）
 *   LazyObjectWrapper: 操作实例（draw.drawRect(...) → 调用方法）
 *
 * 使用场景：
 *   - Canvas onDraw 回调的 DrawScope 对象
 *   - 复杂 Java 对象的方法调用
 *   - 第三方库的 Java 对象暴露给 Lua
 */
object LazyObjectWrapper {
    private const val TAG = "LazyObjWrap"
    private const val PARAM_MATCH_THRESHOLD = 5  // 参数匹配阈值，超过不匹配

    /**
     * 将 Java 对象包装为 Lua table，压入栈顶
     */
    fun wrapObject(L: LuaState, obj: Any?) {
        if (obj == null) { L.pushNil(); return }

        L.newTable()
        val tableIdx = L.getTop()

        // 创建元表，设置 __index 和 __newindex
        L.newTable()
        val metaIdx = L.getTop()

        // __index: 属性 getter + 方法调用
        val indexHandler = ObjectIndexHandler(L, obj)
        LazyNamespace.pushLuaIndexWrapper(L, indexHandler)
        L.setField(-2, "__index")

        // __newindex: 属性 setter + 字段赋值
        val newIndexHandler = ObjectNewIndexHandler(L, obj)
        LazyNamespace.pushLuaIndexWrapper(L, newIndexHandler)
        L.setField(-2, "__newindex")

        L.setMetaTable(tableIdx)
        // 栈: [table]
    }

    /**
     * 包装 Java 类（如通过 Class.forName 加载的类），支持 __call
     */
    fun wrapClass(L: LuaState, className: String) {
        try {
            val clazz = Class.forName(className)
            wrapClass(L, clazz)
        } catch (e: ClassNotFoundException) {
            logW(TAG) { "[wrapClass] 类未找到: $className" }
            L.pushNil()
        }
    }

    fun wrapClass(L: LuaState, clazz: Class<*>) {
        L.newTable()
        val tableIdx = L.getTop()

        L.newTable()
        val metaIdx = L.getTop()

        // __index: 静态方法/字段
        val indexHandler = ClassIndexHandler(L, clazz)
        LazyNamespace.pushLuaIndexWrapper(L, indexHandler)
        L.setField(-2, "__index")

        // __call: 构造函数调用 — 需要 varargs 包装以支持多参数
        val callHandler = ClassCallHandler(L, clazz)
        val tempGlobal = "__lazy_class_call_${callHandler.hashCode().toUInt()}"
        L.pushJavaFunction(callHandler)
        L.setGlobal(tempGlobal)

        val code = """
            local handler = $tempGlobal
            _G["$tempGlobal"] = nil
            return function(cls, ...)
                return handler(cls, ...)
            end
        """.trimIndent()

        if (L.LloadString(code) == 0) {
            L.pcall(0, 1, 0)
            L.setField(-2, "__call")
        } else {
            logE(TAG) { "[wrapClass] __call 包装函数创建失败: ${L.toString(-1)}" }
            L.pop(1)
        }

        L.setMetaTable(tableIdx)
    }

    // ================================================================
    //  __index — 属性 getter / 方法调用（对象）
    // ================================================================
    private class ObjectIndexHandler(
        private val luaState: LuaState,
        private val target: Any
    ) : com.luajava.JavaFunction(luaState) {
        private val cache = ClassReflectionCache(target.javaClass)

        override fun execute(): Int {
            val key = try { luaState.toString(3) } catch (e: Exception) { luaState.pushNil(); return 1 }

            // 1. 尝试属性 getter
            val prop = cache.properties[key]
            if (prop != null) {
                try {
                    luaState.pushJavaObject(prop.invoke(target))
                    return 1
                } catch (e: Exception) {
                    logW(TAG) { "[__index] 属性 $key getter 失败: ${e.message}" }
                }
            }

            // 2. 尝试字段
            val field = cache.fields[key]
            if (field != null) {
                try {
                    luaState.pushJavaObject(field.get(target))
                    return 1
                } catch (e: Exception) {
                    logW(TAG) { "[__index] 字段 $key 读取失败: ${e.message}" }
                }
            }

            // 3. 返回方法列表（供 Lua 端调用），包装为调用器
            val methods = cache.functions[key]
            if (methods != null) {
                luaState.pushJavaFunction(MethodCaller(luaState, target, key, methods))
                return 1
            }

            // 4. 未找到
            luaState.pushNil()
            return 1
        }
    }

    // ================================================================
    //  __newindex — 属性 setter / 字段赋值
    // ================================================================
    private class ObjectNewIndexHandler(
        private val luaState: LuaState,
        private val target: Any
    ) : com.luajava.JavaFunction(luaState) {
        private val cache = ClassReflectionCache(target.javaClass)

        override fun execute(): Int {
            val key = try { luaState.toString(3) } catch (e: Exception) { return 0 }
            val value = try { luaState.toJavaObject(4) } catch (e: Exception) { null }

            // 1. 尝试 setter 方法
            if (cache.setterMethods.containsKey(key)) {
                try {
                    cache.setterMethods[key]!!.invoke(target, value)
                    return 0
                } catch (e: Exception) {
                    logW(TAG) { "[__newindex] setter $key 失败: ${e.message}" }
                }
            }

            // 2. 尝试直接字段赋值
            val field = cache.fields[key]
            if (field != null) {
                try {
                    field.set(target, value)
                } catch (e: Exception) {
                    logW(TAG) { "[__newindex] 字段 $key 赋值失败: ${e.message}" }
                }
            }

            return 0
        }
    }

    // ================================================================
    //  __index — 静态方法/字段（类）
    // ================================================================
    private class ClassIndexHandler(
        private val luaState: LuaState,
        private val clazz: Class<*>
    ) : com.luajava.JavaFunction(luaState) {
        private val cache = ClassReflectionCache(clazz)

        override fun execute(): Int {
            val key = try { luaState.toString(3) } catch (e: Exception) { luaState.pushNil(); return 1 }

            // 静态字段
            val field = cache.fields[key]
            if (field != null) {
                try {
                    luaState.pushJavaObject(field.get(null))
                    return 1
                } catch (e: Exception) {
                    logW(TAG) { "[__index:class] 静态字段 $key 读取失败: ${e.message}" }
                }
            }

            // 静态方法
            val methods = cache.functions[key]
            if (methods != null) {
                luaState.pushJavaFunction(MethodCaller(luaState, null, key, methods, isStatic = true))
                return 1
            }

            luaState.pushNil()
            return 1
        }
    }

    // ================================================================
    //  __call — 构造函数
    // ================================================================
    private class ClassCallHandler(
        private val luaState: LuaState,
        private val clazz: Class<*>
    ) : com.luajava.JavaFunction(luaState) {
        override fun execute(): Int {
            // 栈: [cls, arg1, arg2...] (varargs 包装，cls 是类表，后续是构造参数)
            val top = luaState.getTop()
            val args = mutableListOf<Any?>()
            for (i in 2..top) {
                args.add(try { luaState.toJavaObject(i) } catch (e: Exception) { null })
            }

            try {
                val constructor = ClassReflectionCache(clazz).findConstructor(args)
                luaState.pushJavaObject(constructor.newInstance(*args.toTypedArray()))
                return 1
            } catch (e: Exception) {
                logW(TAG) { "[__call] 构造函数调用失败: ${e.message}" }
                luaState.pushNil()
                return 1
            }
        }
    }

    // ================================================================
    //  方法调用器 — 按参数数量匹配
    // ================================================================
    private class MethodCaller(
        private val luaState: LuaState,
        private val target: Any?,
        private val methodName: String,
        private val candidates: List<Method>,
        private val isStatic: Boolean = false
    ) : com.luajava.JavaFunction(luaState) {
        override fun execute(): Int {
            val top = luaState.getTop()
            // 栈: [function, arg1, arg2, ...] (JavaFunction 调用时函数自身在栈位置 1)
            val args = mutableListOf<Any?>()
            for (i in 2..top) {
                args.add(try { luaState.toJavaObject(i) } catch (e: Exception) { null })
            }

            // 按参数数量匹配方法
            val method = findBestMatch(args)
            if (method == null) {
                logW(TAG) { "[methodCall] $methodName: 无匹配方法, 参数数量=${args.size}, 候选=${candidates.size}" }
                luaState.pushNil()
                return 1
            }

            try {
                val result = method.invoke(target, *args.toTypedArray())
                luaState.pushJavaObject(result)
                return 1
            } catch (e: InvocationTargetException) {
                logW(TAG) { "[methodCall] $methodName 调用失败: ${e.targetException?.message}" }
                luaState.pushNil()
                return 1
            } catch (e: Exception) {
                logW(TAG) { "[methodCall] $methodName 调用失败: ${e.message}" }
                luaState.pushNil()
                return 1
            }
        }

        private fun findBestMatch(args: List<Any?>): Method? {
            // 精确参数数量匹配
            val exact = candidates.firstOrNull { it.parameterCount == args.size }
            if (exact != null) return exact

            // 参数数量差小于阈值，选最接近的
            if (args.size <= PARAM_MATCH_THRESHOLD) {
                return candidates.minByOrNull { kotlin.math.abs(it.parameterCount - args.size) }
            }

            return null
        }
    }
}

/**
 * 反射缓存（移植自 kulipai LuaCompose）
 *
 * 缓存 Java 类的反射信息，避免重复反射调用。
 * 每个类只解析一次，后续查询直接从缓存命中。
 */
class ClassReflectionCache(javaClass: Class<*>) {
    /** getter 方法: 属性名 → Method（如 getWidth → width） */
    val properties: Map<String, Method>

    /** setter 方法: 属性名 → Method（如 setWidth → width） */
    val setterMethods: MutableMap<String, Method> = mutableMapOf()

    /** 字段: 字段名 → Field */
    val fields: MutableMap<String, Field> = mutableMapOf()

    /** 方法: 方法名 → 按参数数量排序的 Method 列表 */
    val functions: MutableMap<String, List<Method>> = mutableMapOf()

    /** 构造函数: 按参数数量排序 */
    val constructors: List<Constructor<*>>

    init {
        val props = mutableMapOf<String, Method>()
        val funcs = mutableMapOf<String, MutableList<Method>>()
        val flds = mutableMapOf<String, Field>()

        // 解析方法
        for (method in javaClass.methods) {
            val name = method.name
            // 过滤掉 Object 和 Class 的通用方法
            if (name in setOf("getClass", "hashCode", "equals", "toString", "notify", "notifyAll", "wait")) {
                continue
            }

            // getter 检测: getXxx() 无参数有返回值
            if (name.startsWith("get") && name.length > 3 && method.parameterCount == 0 && method.returnType != Void.TYPE) {
                val propName = name[3].lowercaseChar() + name.substring(4)
                props[propName] = method
            }

            // isXxx() → boolean getter
            if (name.startsWith("is") && name.length > 2 && method.parameterCount == 0 && method.returnType == Boolean::class.javaPrimitiveType) {
                val propName = name[2].lowercaseChar() + name.substring(3)
                props[propName] = method
            }

            // setter 检测: setXxx(value) 单参数
            if (name.startsWith("set") && name.length > 3 && method.parameterCount == 1) {
                val propName = name[3].lowercaseChar() + name.substring(4)
                setterMethods[propName] = method
            }

            // 普通方法
            funcs.getOrPut(name) { mutableListOf() }.add(method)
        }

        // 按参数数量排序
        for ((_, list) in funcs) {
            list.sortBy { it.parameterCount }
        }

        // 解析字段
        for (field in javaClass.fields) {
            flds[field.name] = field
        }
        // 也解析非 public 字段
        for (field in javaClass.declaredFields) {
            if (field.name !in flds) {
                field.isAccessible = true
                flds[field.name] = field
            }
        }

        properties = props
        functions.putAll(funcs.mapValues { it.value.sortedBy { m -> m.parameterCount } })
        fields.putAll(flds)
        constructors = javaClass.constructors.toList()
    }

    /** 查找匹配参数数量的构造函数 */
    fun findConstructor(args: List<Any?>): Constructor<*> {
        val exact = constructors.firstOrNull { it.parameterCount == args.size }
        if (exact != null) return exact
        return constructors.firstOrNull() ?: throw NoSuchMethodException("无构造函数")
    }
}