package com.nirithy.luacompose.runtime

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import com.nirithy.luacompose.script.BridgeEngine
import com.nirithy.luacompose.script.BridgeValue

/**
 * ComposeScope 包装器，将特殊 Compose 作用域对象包装为 Lua 可访问表
 *
 * 目前仅包装 BoxWithConstraintsScope（暴露布局约束属性）。
 * RowScope/ColumnScope/LazyListScope 等作用域对象直接返回 Java 对象，
 * 由 Kotlin 侧组件渲染器在正确的 Compose 上下文中处理。
 *
 * 参考 LuaCompose-master 的 ScopeWrappers 设计
 */
object ScopeWrappers {

    /**
     * 将作用域对象包装为 Lua 可访问的 BridgeValue
     *
     * @param obj 作用域对象（如 BoxWithConstraintsScope）
     * @param engine 引擎实例
     */
    fun wrap(obj: Any?, engine: BridgeEngine): BridgeValue {
        if (obj == null) return engine.createNil()

        return when (obj) {
            is BoxWithConstraintsScope -> wrapBoxWithConstraintsScope(obj, engine)
            // 其他作用域对象（RowScope、ColumnScope、LazyListScope 等）直接返回 Java 对象
            else -> engine.coerceJavaToScript(obj)
        }
    }

    /**
     * 包装 BoxWithConstraintsScope，暴露 maxWidth/maxHeight/minWidth/minHeight/constraints
     */
    private fun wrapBoxWithConstraintsScope(
        scope: BoxWithConstraintsScope,
        engine: BridgeEngine
    ): BridgeValue {
        val table = engine.createTable()
        val meta = engine.createTable()

        meta.set("__index", engine.createFunction { args ->
            val key = args.getOrNull(1)?.toStringValue() ?: ""
            when (key) {
                "maxWidth" -> engine.createValue(scope.maxWidth.value.toDouble())
                "maxHeight" -> engine.createValue(scope.maxHeight.value.toDouble())
                "minWidth" -> engine.createValue(scope.minWidth.value.toDouble())
                "minHeight" -> engine.createValue(scope.minHeight.value.toDouble())
                "constraints" -> engine.coerceJavaToScript(scope.constraints)
                else -> engine.createNil()
            }
        })

        meta.set("__tostring", engine.createFunction {
            engine.createValue(scope.toString())
        })

        table.setMetatable(meta)
        return table
    }
}