package com.nirithy.luacompose.render

import com.nirithy.luacompose.logD
import com.nirithy.luacompose.logI
import com.nirithy.luacompose.logW
import androidx.compose.runtime.Composable
import com.nirithy.luacompose.modifier.ModifierChain
import com.nirithy.luacompose.node.ComposeNode
import java.lang.reflect.Method

/**
 * 动态反射渲染器
 *
 * 当 ComposeNode 的 type 没有在 ComponentRegistry 中注册时，
 * 通过反射查找并调用对应的 @Composable 函数。
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
            logW(TAG) { "[tryRender] 未找到 @Composable 函数: $classPath.$funcName, 可用方法: ${listMethods(classPath)}" }
            return false
        }

        logD(TAG) { "[tryRender] 反射调用: $classPath.$funcName, props=${node.props.keys}" }
        // Composable 调用不能在 try/catch 中，通过 ComponentRegistry 层的 try/catch 处理异常
        invokeComposable(method, node)
        return true
    }

    /**
     * 查找 @Composable 静态方法
     */
    private fun findComposableMethod(classPath: String, funcName: String): Method? {
        return try {
            val clazz = Class.forName(classPath)
            // 查找与 funcName 同名的静态方法
            clazz.methods
                .filter { it.name == funcName && it.parameterCount > 0 }
                .maxByOrNull { it.parameterCount } // 选参数最多的（可能是最完整的重载）
                ?.also { logI(TAG) { "[findMethod] $classPath.$funcName → ${it.parameterCount} 个参数" } }
        } catch (e: ClassNotFoundException) {
            logW(TAG) { "[findMethod] 类未找到: $classPath" }
            null
        }
    }

    /**
     * 列出类中所有方法名（调试用）
     */
    private fun listMethods(classPath: String): String {
        return try {
            Class.forName(classPath).methods
                .filter { it.parameterCount > 0 }
                .map { it.name }
                .distinct()
                .take(10)
                .joinToString(", ")
        } catch (e: Exception) { "N/A" }
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
        if (params.isNotEmpty() && hasContentParam(params.last())) {
            val contentIdx = params.size - 1
            args[contentIdx] = createContentLambda(node)
        }

        method.invoke(null, *args)
    }

    /**
     * 从 ComposeNode.props 解析参数值
     */
    private fun resolveParam(name: String, type: Class<*>, node: ComposeNode): Any? {
        return when (name) {
            "modifier" -> {
                val m = node.props["modifier"]
                when (m) {
                    is ModifierChain -> m.build()
                    else -> null
                }
            }
            "onClick" -> createOnClickCallback(node)
            "onValueChange" -> createOnValueChangeCallback(node)
            "onCheckedChange" -> createOnCheckedChangeCallback(node)
            "text", "value" -> node.stringProp(name) ?: node.props[name]
            "enabled", "checked" -> node.boolProp(name, true)
            "color", "containerColor" -> node.props[name]
            "content", "children" -> null
            else -> node.props[name]
        }
    }

    /** 创建 onClick 回调 (Function0) */
    private fun createOnClickCallback(node: ComposeNode): (() -> Unit)? {
        val cb = node.callback("onClick") ?: return null
        return { cb.call() }
    }

    /** 创建 onValueChange 回调 (Function1<Any?>) */
    private fun createOnValueChangeCallback(node: ComposeNode): ((Any?) -> Unit)? {
        val cb = node.callback("onValueChange") ?: return null
        return { v: Any? -> cb.call(v) }
    }

    /** 创建 onCheckedChange 回调 (Function1<Boolean>) */
    private fun createOnCheckedChangeCallback(node: ComposeNode): ((Boolean) -> Unit)? {
        val cb = node.callback("onCheckedChange") ?: return null
        return { v: Boolean -> cb.call(v) }
    }

    /**
     * 检查参数是否是 content lambda
     */
    private fun hasContentParam(param: java.lang.reflect.Parameter): Boolean {
        return param.name in listOf("content", "children") ||
               param.type.name.contains("Function") ||
               param.type.name.contains("Composable")
    }

    /**
     * 创建 content lambda（渲染子节点）
     */
    @Composable
    private fun createContentLambda(node: ComposeNode): @Composable () -> Unit {
        return {
            ComposeRenderer.RenderChildren(node)
        }
    }
}