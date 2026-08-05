package com.kulipai.luacompose.compose.runtime

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import com.kulipai.luacompose.compose.script.ScriptValue

object ScopeWrappers {
    fun wrap(obj: Any?): ScriptValue {
        if (obj == null) return ComposeBridge.engine.createNil()
        if (obj is BoxWithConstraintsScope) {
            val table = ComposeBridge.engine.createTable()
            val meta = ComposeBridge.engine.createTable()
            meta.set("__index", ComposeBridge.engine.createFunction { args ->
                val key = args[1].toStringValue()
                when (key) {
                    "maxWidth" -> ComposeBridge.engine.createValue(obj.maxWidth.value.toDouble())
                    "maxHeight" -> ComposeBridge.engine.createValue(obj.maxHeight.value.toDouble())
                    "minWidth" -> ComposeBridge.engine.createValue(obj.minWidth.value.toDouble())
                    "minHeight" -> ComposeBridge.engine.createValue(obj.minHeight.value.toDouble())
                    "constraints" -> ComposeBridge.engine.coerceJavaToScript(obj.constraints)
                    else -> ComposeBridge.engine.createNil()
                }
            })
            // allow toString
            meta.set("__tostring", ComposeBridge.engine.createFunction {
                ComposeBridge.engine.createValue(obj.toString())
            })
            table.setMetatable(meta)
            return table
        }
        return ComposeBridge.javaToScript(obj)
    }
}
