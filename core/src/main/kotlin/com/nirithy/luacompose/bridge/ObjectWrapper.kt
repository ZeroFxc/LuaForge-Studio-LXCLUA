package com.nirithy.luacompose.bridge

import com.luajava.JavaFunction
import com.luajava.LuaState
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Java 反射缓存，避免重复调用 getMethods()/getFields()/getConstructors()
 *
 * 从 LuaCompose-master 原版采纳，核心改进：
 *   - 每个类只反射一次，结果缓存
 *   - 方法按参数数量分组，支持重载匹配
 *   - 属性访问（getter/setter）自动识别
 */
object ClassReflectionCache {
    private val cache = mutableMapOf<Class<*>, ReflectionInfo>()

    data class ReflectionInfo(
        val methods: Map<String, List<Method>>,     // 方法名 → 重载列表
        val fields: Map<String, Field>,              // 字段名 → Field
        val constructors: List<Constructor<*>>,      // 所有构造函数
        val properties: Map<String, Method>,         // getter → Method (foo → getFoo())
        val setters: Map<String, Method>,            // setter → Method (foo → setFoo(v))
    )

    fun getInfo(clazz: Class<*>): ReflectionInfo {
        return cache.getOrPut(clazz) {
            val methods = mutableMapOf<String, MutableList<Method>>()
            val fields = mutableMapOf<String, Field>()
            val properties = mutableMapOf<String, Method>()
            val setters = mutableMapOf<String, Method>()

            for (method in clazz.methods) {
                // 忽略 Object 方法
                if (method.declaringClass == Any::class.java) continue
                
                methods.getOrPut(method.name) { mutableListOf() }.add(method)

                // 识别 getter: getXxx() → xxx
                if (method.name.startsWith("get") && method.parameterCount == 0
                    && method.name.length > 3 && method.returnType != Void.TYPE) {
                    val propName = method.name.substring(3).replaceFirstChar { it.lowercase() }
                    properties[propName] = method
                }
                // 识别 setter: setXxx(v) → xxx
                if (method.name.startsWith("set") && method.parameterCount == 1
                    && method.name.length > 3) {
                    val propName = method.name.substring(3).replaceFirstChar { it.lowercase() }
                    setters[propName] = method
                }
            }

            for (field in clazz.fields) {
                fields[field.name] = field
            }

            ReflectionInfo(
                methods = methods,
                fields = fields,
                constructors = clazz.constructors.toList(),
                properties = properties,
                setters = setters,
            )
        }
    }

    /** 清除缓存（Activity 销毁时调用） */
    fun clear() = cache.clear()
}

/**
 * Java 对象/类包装器
 *
 * 从 LuaCompose-master 原版采纳，核心改进：
 *   - __index：属性访问 → 静态字段 → 方法调用（支持重载匹配）
 *   - __call：构造函数调用
 *   - 使用 ClassReflectionCache 避免重复反射
 *
 * 用于 LazyNamespace 解析到 Java 类时，替代简单的 pushJavaObject。
 */
object ObjectWrapper {
    private const val TAG = "ObjectWrapper"

    /**
     * 将 Java 类包装为 Lua 可调用表并压入栈顶
     *
     * 支持：
     *   compose.ui.graphics.Color.Red  → 静态字段访问
     *   compose.ui.graphics.Color(0xFF0000) → 构造函数调用
     *   compose.ui.unit.Dp.value → 属性访问
     */
    fun pushWrappedClass(L: LuaState, clazz: Class<*>) {
        // 先尝试 Kotlin object（INSTANCE 字段）
        try {
            val instance = clazz.getDeclaredField("INSTANCE").get(null)
            pushWrappedObject(L, instance)
            return
        } catch (_: NoSuchFieldException) {}

        // 尝试 Kotlin companion object
        try {
            val companion = clazz.getDeclaredField("Companion").get(null)
            pushWrappedObject(L, companion)
            return
        } catch (_: NoSuchFieldException) {}

        // 普通类：创建类包装器（支持静态字段访问 + 构造函数调用）
        L.newTable()
        val tableIdx = L.getTop()

        L.newTable()
        val metaIdx = L.getTop()

        // __index: 静态字段/方法访问
        L.pushJavaFunction(ClassIndexHandler(L, clazz))
        L.setField(-2, "__index")

        // __call: 构造函数调用
        L.pushJavaFunction(ClassCallHandler(L, clazz))
        L.setField(-2, "__call")

        L.setMetaTable(tableIdx)
        // 栈顶: wrappedTable
    }

    /**
     * 将 Java 实例包装为 Lua 可访问表
     */
    fun pushWrappedObject(L: LuaState, obj: Any) {
        L.newTable()
        val tableIdx = L.getTop()

        // 存储原始 Java 对象引用
        L.pushJavaObject(obj)
        L.setField(tableIdx, "_javaObj")

        L.newTable()
        val metaIdx = L.getTop()

        L.pushJavaFunction(InstanceIndexHandler(L, obj))
        L.setField(-2, "__index")

        L.pushJavaFunction(InstanceNewIndexHandler(L, obj))
        L.setField(-2, "__newindex")

        L.setMetaTable(tableIdx)
    }

    /**
     * 类级别 __index：静态字段 + 静态方法
     */
    private class ClassIndexHandler(
        private val L: LuaState,
        private val clazz: Class<*>
    ) : JavaFunction(L) {
        override fun execute(): Int {
            val key = try { L.toString(2) } catch (_: Exception) { L.pushNil(); return 1 }
            val info = ClassReflectionCache.getInfo(clazz)

            // 1. 静态字段
            val field = info.fields[key]
            if (field != null && Modifier.isStatic(field.modifiers)) {
                try {
                    L.pushJavaObject(field.get(null))
                    return 1
                } catch (_: Exception) {}
            }

            // 2. 属性 getter
            // 对于静态方法，需要检查是否有对应的 getter
            val propGetter = info.properties[key]
            if (propGetter != null && Modifier.isStatic(propGetter.modifiers)) {
                try {
                    val result = propGetter.invoke(null)
                    L.pushJavaObject(result)
                    return 1
                } catch (_: Exception) {}
            }

            // 3. 静态方法
            val methods = info.methods[key]
            if (!methods.isNullOrEmpty()) {
                val staticMethods = methods.filter { Modifier.isStatic(it.modifiers) }
                if (staticMethods.isNotEmpty()) {
                    L.pushJavaFunction(MethodDispatcher(L, null, staticMethods.toList()))
                    return 1
                }
            }

            L.pushNil()
            return 1
        }
    }

    /**
     * 类级别 __call：构造函数调用
     */
    private class ClassCallHandler(
        private val L: LuaState,
        private val clazz: Class<*>
    ) : JavaFunction(L) {
        override fun execute(): Int {
            val info = ClassReflectionCache.getInfo(clazz)
            val constructors = info.constructors

            if (constructors.isEmpty()) {
                L.pushNil()
                return 1
            }

            // 按参数数量匹配构造函数
            val argCount = L.getTop() - 1  // -1 因为位置 1 是闭包自身
            val constructor = constructors.find { it.parameterCount == argCount }
                ?: constructors.maxByOrNull { it.parameterCount }

            if (constructor == null) {
                L.pushNil()
                return 1
            }

            try {
                val args = mutableListOf<Any?>()
                for (i in 1..minOf(argCount, constructor.parameterCount)) {
                    args.add(L.toJavaObject(i + 1))
                }
                // 补齐默认值
                while (args.size < constructor.parameterCount) {
                    args.add(null)
                }
                val instance = constructor.newInstance(*args.toTypedArray())
                pushWrappedObject(L, instance)
                return 1
            } catch (e: Exception) {
                L.pushNil()
                return 1
            }
        }
    }

    /**
     * 实例级别 __index：属性 + 方法
     */
    private class InstanceIndexHandler(
        private val L: LuaState,
        private val obj: Any
    ) : JavaFunction(L) {
        override fun execute(): Int {
            val key = try { L.toString(2) } catch (_: Exception) { L.pushNil(); return 1 }
            val clazz = obj.javaClass
            val info = ClassReflectionCache.getInfo(clazz)

            // 1. _javaObj 原始引用
            if (key == "_javaObj") {
                L.pushJavaObject(obj)
                return 1
            }

            // 2. 属性 getter
            val propGetter = info.properties[key]
            if (propGetter != null) {
                try {
                    val result = propGetter.invoke(obj)
                    L.pushJavaObject(result)
                    return 1
                } catch (_: Exception) {}
            }

            // 3. 字段
            val field = info.fields[key]
            if (field != null) {
                try {
                    L.pushJavaObject(field.get(obj))
                    return 1
                } catch (_: Exception) {}
            }

            // 4. 方法（返回可调用的 dispatcher）
            val methods = info.methods[key]
            if (!methods.isNullOrEmpty()) {
                L.pushJavaFunction(MethodDispatcher(L, obj, methods))
                return 1
            }

            L.pushNil()
            return 1
        }
    }

    /**
     * 实例级别 __newindex：属性 setter + 字段赋值
     */
    private class InstanceNewIndexHandler(
        private val L: LuaState,
        private val obj: Any
    ) : JavaFunction(L) {
        override fun execute(): Int {
            val key = try { L.toString(2) } catch (_: Exception) { return 0 }
            val clazz = obj.javaClass
            val info = ClassReflectionCache.getInfo(clazz)

            // 1. 属性 setter
            val setter = info.setters[key]
            if (setter != null) {
                try {
                    val value = L.toJavaObject(3)
                    setter.invoke(obj, value)
                    return 0
                } catch (_: Exception) {}
            }

            // 2. 字段直接赋值
            val field = info.fields[key]
            if (field != null) {
                try {
                    val value = L.toJavaObject(3)
                    field.set(obj, value)
                    return 0
                } catch (_: Exception) {}
            }

            return 0
        }
    }

    /**
     * 方法分发器：按参数数量匹配重载
     */
    private class MethodDispatcher(
        private val L: LuaState,
        private val target: Any?,  // null 表示静态方法调用
        private val methods: List<Method>
    ) : JavaFunction(L) {
        override fun execute(): Int {
            val argCount = L.getTop() - 1  // -1 因为闭包在位置 1

            // 按参数数量匹配
            val method = methods.find { it.parameterCount == argCount }
                ?: methods.maxByOrNull { it.parameterCount }

            if (method == null) {
                L.pushNil()
                return 1
            }

            try {
                val args = mutableListOf<Any?>()
                for (i in 1..minOf(argCount, method.parameterCount)) {
                    args.add(L.toJavaObject(i + 1))
                }
                while (args.size < method.parameterCount) {
                    args.add(null)
                }
                val result = method.invoke(target, *args.toTypedArray())
                if (result == null || result is Unit) {
                    L.pushNil()
                } else {
                    L.pushJavaObject(result)
                }
                return 1
            } catch (e: Exception) {
                L.pushNil()
                return 1
            }
        }
    }
}