package com.nirithy.luacompose.runtime

import androidx.compose.runtime.Composable
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.script.BridgeFunction
import com.nirithy.luacompose.script.BridgeValue
import com.nirithy.luacompose.state.ComposeScope

/**
 * Lua 函数 → Kotlin Lambda 包装器
 *
 * KSP 生成的组件渲染器需要将 Lua 函数转换为 Kotlin 的 FunctionN 类型，
 * 以便传递给 Compose API。此类提供 Function0~Function6 的包装。
 *
 * 参考 LuaCompose-master 的 FunctionWrappers 设计
 */
object FunctionWrappers {

    /**
     * 将 Lua 函数包装为 Kotlin lambda
     *
     * @param childScope 子 ComposeScope（用于 content lambda）
     * @param scriptFunction Lua 函数引用
     * @param paramTypeName 目标 Kotlin 函数类型（如 "kotlin.jvm.functions.Function2"）
     * @param isComposable 是否为 @Composable lambda
     * @return 包装后的 Kotlin lambda 对象
     */
    fun wrap(
        childScope: ComposeScope?,
        scriptFunction: BridgeFunction?,
        paramTypeName: String,
        isComposable: Boolean
    ): Any {
        val engine = ComposeBridgeInstance.current.engine

        if (isComposable) {
            return wrapComposable(childScope, paramTypeName, engine)
        } else {
            return wrapNonComposable(scriptFunction, paramTypeName, engine)
        }
    }

    /**
     * 包装 @Composable lambda（content lambda）
     */
    private fun wrapComposable(
        childScope: ComposeScope?,
        paramTypeName: String,
        engine: com.nirithy.luacompose.script.BridgeEngine
    ): Any {
        return when (paramTypeName) {
            "kotlin.jvm.functions.Function2" -> {
                // () -> Unit
                val func: @Composable () -> Unit = {
                    childScope?.let { ComposeScopeComponent(it, null) }
                }
                func
            }
            "kotlin.jvm.functions.Function3" -> {
                // (p1) -> Unit
                val func: @Composable (Any?) -> Unit = { p1 ->
                    childScope?.let { ComposeScopeComponent(it, p1, ScopeWrappers.wrap(p1, engine)) }
                }
                func
            }
            "kotlin.jvm.functions.Function4" -> {
                // (p1, p2) -> Unit
                val func: @Composable (Any?, Any?) -> Unit = { p1, p2 ->
                    childScope?.let {
                        ComposeScopeComponent(
                            it, p1,
                            ScopeWrappers.wrap(p1, engine),
                            ScopeWrappers.wrap(p2, engine)
                        )
                    }
                }
                func
            }
            "kotlin.jvm.functions.Function5" -> {
                // (p1, p2, p3) -> Unit
                val func: @Composable (Any?, Any?, Any?) -> Unit = { p1, p2, p3 ->
                    childScope?.let {
                        ComposeScopeComponent(
                            it, p1,
                            ScopeWrappers.wrap(p1, engine),
                            ScopeWrappers.wrap(p2, engine),
                            ScopeWrappers.wrap(p3, engine)
                        )
                    }
                }
                func
            }
            "kotlin.jvm.functions.Function6" -> {
                // (p1, p2, p3, p4) -> Unit
                val func: @Composable (Any?, Any?, Any?, Any?) -> Unit = { p1, p2, p3, p4 ->
                    childScope?.let {
                        ComposeScopeComponent(
                            it, p1,
                            ScopeWrappers.wrap(p1, engine),
                            ScopeWrappers.wrap(p2, engine),
                            ScopeWrappers.wrap(p3, engine),
                            ScopeWrappers.wrap(p4, engine)
                        )
                    }
                }
                func
            }
            else -> {
                val func: @Composable () -> Unit = {
                    childScope?.let { ComposeScopeComponent(it, null) }
                }
                func
            }
        }
    }

    /**
     * 包装非 Composable lambda（如 onClick、onValueChange 等回调）
     */
    private fun wrapNonComposable(
        scriptFunction: BridgeFunction?,
        paramTypeName: String,
        engine: com.nirithy.luacompose.script.BridgeEngine
    ): Any {
        return when (paramTypeName) {
            "kotlin.jvm.functions.Function0" -> {
                val func: () -> Any? = {
                    scriptFunction?.call()?.let { scriptToJavaValue(it) }
                }
                func
            }
            "kotlin.jvm.functions.Function1" -> {
                val func: (Any?) -> Any? = { p1 ->
                    scriptFunction?.call(ScopeWrappers.wrap(p1, engine))?.let { scriptToJavaValue(it) }
                }
                func
            }
            "kotlin.jvm.functions.Function2" -> {
                val func: (Any?, Any?) -> Any? = { p1, p2 ->
                    scriptFunction?.call(
                        ScopeWrappers.wrap(p1, engine),
                        ScopeWrappers.wrap(p2, engine)
                    )?.let { scriptToJavaValue(it) }
                }
                func
            }
            "kotlin.jvm.functions.Function3" -> {
                val func: (Any?, Any?, Any?) -> Any? = { p1, p2, p3 ->
                    scriptFunction?.call(
                        ScopeWrappers.wrap(p1, engine),
                        ScopeWrappers.wrap(p2, engine),
                        ScopeWrappers.wrap(p3, engine)
                    )?.let { scriptToJavaValue(it) }
                }
                func
            }
            "kotlin.jvm.functions.Function4" -> {
                val func: (Any?, Any?, Any?, Any?) -> Any? = { p1, p2, p3, p4 ->
                    scriptFunction?.call(
                        ScopeWrappers.wrap(p1, engine),
                        ScopeWrappers.wrap(p2, engine),
                        ScopeWrappers.wrap(p3, engine),
                        ScopeWrappers.wrap(p4, engine)
                    )?.let { scriptToJavaValue(it) }
                }
                func
            }
            else -> {
                val func: () -> Any? = {
                    scriptFunction?.call()?.let { scriptToJavaValue(it) }
                }
                func
            }
        }
    }

    /**
     * 将 BridgeValue 转换为 Java 对象
     * 简化版：主要处理基本类型和 userdata
     */
    private fun scriptToJavaValue(value: BridgeValue): Any? {
        if (value.isNil()) return null
        if (value.isBoolean()) return value.toBoolean()
        if (value.isNumber()) {
            val d = value.toDouble()
            return if (d == d.toLong().toDouble()) d.toLong() else d
        }
        if (value.isString()) return value.toStringValue()
        if (value.isUserdata()) return value.asUserdata()
        return value
    }
}