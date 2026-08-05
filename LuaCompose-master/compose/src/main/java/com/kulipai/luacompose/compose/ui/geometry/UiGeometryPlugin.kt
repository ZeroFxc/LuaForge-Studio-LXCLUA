package com.kulipai.luacompose.compose.ui.geometry

import com.kulipai.luacompose.compose.runtime.ComposeScriptPlugin
import com.kulipai.luacompose.compose.runtime.ComposeScope
import com.kulipai.luacompose.compose.script.ScriptTable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.VectorConverter
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import com.kulipai.luacompose.compose.script.ScriptValue
import androidx.compose.runtime.Composable

class UiGeometryPlugin : ComposeScriptPlugin {
    override val namespace: String = "ui.geometry"
    override fun getComponents(): Map<String, @Composable (props: Map<String, Any?>, childScope: ComposeScope?) -> Unit> = emptyMap()

    override fun injectGlobals(scriptTable: ScriptTable) {
        val sizeMeta = ComposeBridge.engine.createTable()
        sizeMeta.set("__call", ComposeBridge.engine.createFunction { args ->
            val arg1 = args.getOrNull(1)
            var w = 0f
            var h = 0f
            if (arg1 != null && arg1.isTable()) {
                val t = arg1.asTable()
                val tw = t.get("width")
                val th = t.get("height")
                if (!tw.isNil()) w = tw.toFloat()
                if (!th.isNil()) h = th.toFloat()
            } else {
                w = args.getOrNull(1)?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
                h = args.getOrNull(2)?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
            }
            val table = ComposeBridge.engine.createTable()
            table.set("width", ComposeBridge.engine.createValue(w.toDouble()))
            table.set("height", ComposeBridge.engine.createValue(h.toDouble()))
            table.set("_javaSize", ComposeBridge.engine.createUserdata(Size(w, h)))
            table
        })
        val sizeTable = ComposeBridge.engine.createTable()
        sizeTable.setMetatable(sizeMeta)
        sizeTable.set("Zero", ComposeBridge.engine.createTable().apply {
            set("width", ComposeBridge.engine.createValue(0.0))
            set("height", ComposeBridge.engine.createValue(0.0))
            set("_javaSize", ComposeBridge.engine.createUserdata(Size.Zero))
        })
        scriptTable.set("Size", sizeTable)

        ComposeBridge.converters[Size::class.java] = { obj ->
            val size = obj as Size
            val table = ComposeBridge.engine.createTable()
            table.set("width", ComposeBridge.engine.createValue(size.width.toDouble()))
            table.set("height", ComposeBridge.engine.createValue(size.height.toDouble()))
            table.set("_javaSize", ComposeBridge.engine.createUserdata(size))
            table
        }

        val offsetMeta = ComposeBridge.engine.createTable()
        offsetMeta.set("__call", ComposeBridge.engine.createFunction { args ->
            val arg1 = args.getOrNull(1)
            var x = 0f
            var y = 0f
            if (arg1 != null && arg1.isTable()) {
                val t = arg1.asTable()
                val tx = t.get("x")
                val ty = t.get("y")
                if (!tx.isNil()) x = tx.toFloat()
                if (!ty.isNil()) y = ty.toFloat()
            } else {
                x = args.getOrNull(1)?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
                y = args.getOrNull(2)?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
            }
            val table = ComposeBridge.engine.createTable()
            table.set("x", ComposeBridge.engine.createValue(x.toDouble()))
            table.set("y", ComposeBridge.engine.createValue(y.toDouble()))
            table.set("_javaOffset", ComposeBridge.engine.createUserdata(Offset(x, y)))
            
            table.set("copy", ComposeBridge.engine.createFunction { copyArgs ->
                val map = copyArgs.getOrNull(0)?.asTable()
                val newX = map?.get("x")?.let { if (!it.isNil()) it.toFloat() else x } ?: x
                val newY = map?.get("y")?.let { if (!it.isNil()) it.toFloat() else y } ?: y
                
                // Call Offset(...) again using offsetTable as if it was a function call
                // offsetMeta __call takes offsetTable as first arg, then x and y
                val offsetFunc = offsetMeta.get("__call").asFunction()
                offsetFunc.call(ComposeBridge.engine.createNil(), ComposeBridge.engine.createValue(newX.toDouble()), ComposeBridge.engine.createValue(newY.toDouble()))
            })
            table
        })
        val offsetTable = ComposeBridge.engine.createTable()
        offsetTable.setMetatable(offsetMeta)
        
        offsetTable.set("Zero", ComposeBridge.engine.createTable().apply {
            set("x", ComposeBridge.engine.createValue(0.0))
            set("y", ComposeBridge.engine.createValue(0.0))
            set("_javaOffset", ComposeBridge.engine.createUserdata(Offset.Zero))
            set("copy", ComposeBridge.engine.createFunction { copyArgs ->
                val map = copyArgs.getOrNull(0)?.asTable()
                val newX = map?.get("x")?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
                val newY = map?.get("y")?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
                val offsetFunc = offsetMeta.get("__call").asFunction()
                offsetFunc.call(ComposeBridge.engine.createNil(), ComposeBridge.engine.createValue(newX.toDouble()), ComposeBridge.engine.createValue(newY.toDouble()))
            })
        })
        offsetTable.set("VectorConverter", ComposeBridge.engine.createUserdata(androidx.compose.ui.geometry.Offset.VectorConverter))
        
        scriptTable.set("Offset", offsetTable)

        ComposeBridge.converters[Offset::class.java] = { obj ->
            val offset = obj as Offset
            val table = ComposeBridge.engine.createTable()
            table.set("x", ComposeBridge.engine.createValue(offset.x.toDouble()))
            table.set("y", ComposeBridge.engine.createValue(offset.y.toDouble()))
            table.set("_javaOffset", ComposeBridge.engine.createUserdata(offset))
            table.set("copy", ComposeBridge.engine.createFunction { copyArgs ->
                val map = copyArgs.getOrNull(0)?.asTable()
                val newX = map?.get("x")?.let { if (!it.isNil()) it.toFloat() else offset.x } ?: offset.x
                val newY = map?.get("y")?.let { if (!it.isNil()) it.toFloat() else offset.y } ?: offset.y
                val offsetFunc = offsetMeta.get("__call").asFunction()
                offsetFunc.call(ComposeBridge.engine.createNil(), ComposeBridge.engine.createValue(newX.toDouble()), ComposeBridge.engine.createValue(newY.toDouble()))
            })
            table
        }

        val rectMeta = ComposeBridge.engine.createTable()
        rectMeta.set("__call", ComposeBridge.engine.createFunction { args ->
            val arg1 = args.getOrNull(1)
            var left = 0f
            var top = 0f
            var right = 0f
            var bottom = 0f
            
            if (arg1 != null && arg1.isTable()) {
                val t = arg1.asTable()
                left = t.get("left")?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
                top = t.get("top")?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
                right = t.get("right")?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
                bottom = t.get("bottom")?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
            } else {
                left = args.getOrNull(1)?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
                top = args.getOrNull(2)?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
                right = args.getOrNull(3)?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
                bottom = args.getOrNull(4)?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
            }
            
            val table = ComposeBridge.engine.createTable()
            table.set("left", ComposeBridge.engine.createValue(left.toDouble()))
            table.set("top", ComposeBridge.engine.createValue(top.toDouble()))
            table.set("right", ComposeBridge.engine.createValue(right.toDouble()))
            table.set("bottom", ComposeBridge.engine.createValue(bottom.toDouble()))
            table.set("_javaRect", ComposeBridge.engine.createUserdata(androidx.compose.ui.geometry.Rect(left, top, right, bottom)))
            table
        })
        val rectTable = ComposeBridge.engine.createTable()
        rectTable.setMetatable(rectMeta)
        scriptTable.set("Rect", rectTable)

        ComposeBridge.converters[androidx.compose.ui.geometry.Rect::class.java] = { obj ->
            val rect = obj as androidx.compose.ui.geometry.Rect
            val table = ComposeBridge.engine.createTable()
            table.set("left", ComposeBridge.engine.createValue(rect.left.toDouble()))
            table.set("top", ComposeBridge.engine.createValue(rect.top.toDouble()))
            table.set("right", ComposeBridge.engine.createValue(rect.right.toDouble()))
            table.set("bottom", ComposeBridge.engine.createValue(rect.bottom.toDouble()))
            table.set("_javaRect", ComposeBridge.engine.createUserdata(rect))
            table
        }
    }
}
