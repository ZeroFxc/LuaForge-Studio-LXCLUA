package com.nirithy.luacompose.render

import com.nirithy.luacompose.bridge.ClassReflectionCache
import com.nirithy.luacompose.logD
import com.nirithy.luacompose.logI
import com.nirithy.luacompose.logW
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nirithy.luacompose.modifier.ModifierChain
import com.nirithy.luacompose.node.ComposeNode
import java.lang.reflect.Method

/**
 * 动态反射渲染器
 *
 * 当 ComposeNode 的 type 没有在 ComponentRegistry 中注册时，
 * 通过反射查找并调用对应的 @Composable 函数。
 *
 * 使用 ClassReflectionCache 缓存反射结果，避免重复 getMethods() 调用。
 *
 * 解析规则：
 *   _classPath = "androidx.compose.material3.ButtonKt" → 反射调用 ButtonKt.Button()
 *   _classPath = "androidx.compose.foundation.layout.ColumnKt" → 反射调用 ColumnKt.Column()
 */
object DynamicRenderer {
    private const val TAG = "DynamicRenderer"
    private val methodCache = mutableMapOf<String, Method?>()

    /**
     * 尝试动态渲染组件
     * @return true 表示成功渲染，false 表示无法渲染
     */
    @Composable
    fun tryRender(node: ComposeNode): Boolean {
        val classPath = node.stringProp("_classPath") ?: return false
        val funcName = node.type

        val cacheKey = "$classPath#$funcName"
        val method = methodCache.getOrPut(cacheKey) { findComposableMethod(classPath, funcName) }

        if (method == null) {
            logW(TAG) { "[tryRender] 未找到 @Composable 函数: $classPath.$funcName" }
            return false
        }

        logD(TAG) { "[tryRender] 反射调用: $classPath.$funcName, props=${node.props.keys}" }
        invokeComposable(method, node)
        return true
    }

    /**
     * 查找 @Composable 静态方法（使用 ClassReflectionCache 加速）
     */
    private fun findComposableMethod(classPath: String, funcName: String): Method? {
        return try {
            val clazz = Class.forName(classPath)
            val info = ClassReflectionCache.getInfo(clazz)
            info.methods[funcName]
                ?.filter { it.parameterCount > 0 }
                ?.maxByOrNull { it.parameterCount }
                ?.also { logI(TAG) { "[findMethod] $classPath.$funcName → ${it.parameterCount} 个参数" } }
        } catch (e: ClassNotFoundException) {
            logW(TAG) { "[findMethod] 类未找到: $classPath" }
            null
        }
    }

    /**
     * 通过反射调用 @Composable 函数
     */
    @Composable
    private fun invokeComposable(method: Method, node: ComposeNode) {
        val params = method.parameters
        val args = arrayOfNulls<Any?>(params.size)

        for (i in params.indices) {
            val param = params[i]
            val paramName = param.name ?: continue
            args[i] = resolveParam(paramName, param.type, node)
        }

        // 处理 content lambda（最后一个 @Composable 参数）
        if (params.isNotEmpty() && isContentParam(params.last())) {
            val contentIdx = params.size - 1
            args[contentIdx] = createContentLambda(node)
        }

        method.invoke(null, *args)
    }

    /**
     * 从 ComposeNode.props 智能解析参数值
     *
     * 优先级：参数名匹配 → 参数类型推断
     */
    private fun resolveParam(name: String, type: Class<*>, node: ComposeNode): Any? {
        // 1. 参数名精确匹配（兼顾常见别名）
        return when (name) {
            // Modifier 类型
            "modifier" -> {
                val m = node.props["modifier"]
                when (m) {
                    is ModifierChain -> m.build()
                    null -> null
                    else -> null
                }
            }
            // 回调函数类型 — 按名称从 node.callbacks 匹配
            "onClick", "onLongClick" -> {
                val cb = node.callback(name)
                if (cb != null) {
                    val result: () -> Unit = { cb.call() }
                    result
                } else null
            }
            "onValueChange", "onSearch", "onQueryChange", "onActiveChange",
            "onOpen", "onClose", "onDismiss", "onDismissRequest" -> {
                val cb = node.callback(name)
                if (cb != null) {
                    val result: (Any?) -> Unit = { v: Any? -> cb.call(v) }
                    result
                } else null
            }
            "onCheckedChange" -> {
                val cb = node.callback(name)
                if (cb != null) {
                    val result: (Boolean) -> Unit = { v: Boolean -> cb.call(v) }
                    result
                } else null
            }
            // 字符串类型
            "text", "value", "label", "placeholder", "query", "title" ->
                node.stringProp(name) ?: node.props[name]
            // 布尔类型
            "enabled", "checked", "active", "open", "gesturesEnabled" ->
                node.boolProp(name, false)
            // 浮点/数值类型
            "value" -> node.props[name]  // Slider 的 value 是 Float
            // content lambda 类型
            "content", "children" -> null
            // 回退：按类型推断
            else -> resolveByType(name, type, node)
        }
    }

    /**
     * 按参数类型推断值（兜底策略）
     */
    private fun resolveByType(name: String, type: Class<*>, node: ComposeNode): Any? {
        val rawValue = node.props[name] ?: return null

        return when {
            // 回调函数类型
            type.name.contains("Function") -> {
                val cb = node.callback(name)
                if (cb != null) {
                    when {
                        type.name.contains("Function0") -> {
                            val result: () -> Unit = { cb.call() }
                            result
                        }
                        type.name.contains("Function1") -> {
                            val result: (Any?) -> Unit = { v: Any? -> cb.call(v) }
                            result
                        }
                        else -> {
                            val result: (Any?) -> Unit = { v: Any? -> cb.call(v) }
                            result
                        }
                    }
                } else null
            }
            // Modifier 类型
            Modifier::class.java.isAssignableFrom(type) && rawValue is ModifierChain ->
                rawValue.build()
            // 基础类型 — 直接使用原始值
            type == String::class.java -> rawValue.toString()
            type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> rawValue as? Boolean
            type == Int::class.javaPrimitiveType || type == Int::class.java -> (rawValue as? Number)?.toInt()
            type == Float::class.javaPrimitiveType || type == Float::class.java -> (rawValue as? Number)?.toFloat()
            type == Long::class.javaPrimitiveType || type == Long::class.java -> (rawValue as? Number)?.toLong()
            type == Double::class.javaPrimitiveType || type == Double::class.java -> (rawValue as? Number)?.toDouble()
            // 其他 — 直接传原始值
            else -> rawValue
        }
    }

    /**
     * 检查参数是否是 content lambda
     */
    private fun isContentParam(param: java.lang.reflect.Parameter): Boolean {
        return param.name in listOf("content", "children") ||
               param.type.name.contains("Function") ||
               param.type.name.contains("Composable")
    }

    /**
     * 创建 content lambda（渲染子节点）
     */
    @Composable
    private fun createContentLambda(node: ComposeNode): @Composable () -> Unit {
        return { ComposeRenderer.RenderChildren(node) }
    }
}