package com.kulipai.luacompose.compose.ui.unit

import com.kulipai.luacompose.compose.runtime.ComposeScriptPlugin
import com.kulipai.luacompose.compose.runtime.ComposeScope
import com.kulipai.luacompose.compose.script.ScriptTable
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import com.kulipai.luacompose.compose.runtime.ComposeBridge

class UiUnitPlugin : ComposeScriptPlugin {
    override val namespace: String = "ui.unit"
    override fun getComponents(): Map<String, @Composable (props: Map<String, Any?>, childScope: ComposeScope?) -> Unit> = emptyMap()

    override fun injectGlobals(scriptTable: ScriptTable) {
        val offsetMeta = ComposeBridge.engine.createTable()
        offsetMeta.set("__call", ComposeBridge.engine.createFunction { args ->
            val arg1 = args.getOrNull(1)
            var x = 0
            var y = 0
            if (arg1 != null && arg1.isTable()) {
                val t = arg1.asTable()
                val tx = t.get("x")
                val ty = t.get("y")
                if (!tx.isNil()) x = tx.toInt()
                if (!ty.isNil()) y = ty.toInt()
            } else {
                x = args.getOrNull(1)?.let { if (!it.isNil()) it.toInt() else 0 } ?: 0
                y = args.getOrNull(2)?.let { if (!it.isNil()) it.toInt() else 0 } ?: 0
            }
            val table = ComposeBridge.engine.createTable()
            table.set("x", ComposeBridge.engine.createValue(x.toDouble()))
            table.set("y", ComposeBridge.engine.createValue(y.toDouble()))
            table.set("_javaIntOffset", ComposeBridge.engine.createUserdata(IntOffset(x, y)))
            table
        })
        val offsetTable = ComposeBridge.engine.createTable()
        offsetTable.setMetatable(offsetMeta)
        scriptTable.set("IntOffset", offsetTable)
    }
}
