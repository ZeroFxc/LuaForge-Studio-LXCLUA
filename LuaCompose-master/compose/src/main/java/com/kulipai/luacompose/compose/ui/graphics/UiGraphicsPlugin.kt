package com.kulipai.luacompose.compose.ui.graphics

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import com.kulipai.luacompose.compose.createComposeDrawScope
import com.kulipai.luacompose.compose.runtime.ComposeScope
import com.kulipai.luacompose.compose.runtime.ComposeScriptPlugin
import com.kulipai.luacompose.compose.ui.resolveColor
import com.kulipai.luacompose.compose.ui.resolveModifier
import com.kulipai.luacompose.compose.script.ScriptFunction
import com.kulipai.luacompose.compose.script.ScriptTable
import com.kulipai.luacompose.compose.script.ScriptValue

class UiGraphicsPlugin : ComposeScriptPlugin {
    override val namespace: String = "ui.graphics"

    override fun getComponents(): Map<String, @Composable (props: Map<String, Any?>, childScope: ComposeScope?) -> Unit> {
        val map = mutableMapOf<String, @Composable (props: Map<String, Any?>, childScope: ComposeScope?) -> Unit>()
        

        
        return map
    }

    override fun injectGlobals(scriptTable: ScriptTable) {
        val brushTable = ComposeBridge.engine.createTable()
        brushTable.set("radialGradient", ComposeBridge.engine.createFunction { args ->
            val colorStopsTable = args[0].asTable()
            val centerVal = args[1]
            val radiusVal = args[2]

            val colorStops = mutableListOf<Pair<Float, androidx.compose.ui.graphics.Color>>()
            val keys = colorStopsTable.keys()
            for (i in keys.indices) {
                val key = keys[i]
                val colorScriptVal = colorStopsTable.get(key)
                val color = resolveColor(ComposeBridge.scriptToJava(colorScriptVal))
                val fraction = if (key.isNumber()) key.toFloat() else 0f
                colorStops.add(Pair(fraction, color))
            }
            colorStops.sortBy { it.first }

            val center = if (centerVal != null && !centerVal.isNil()) {
                val tableCenter = centerVal.asTable()
                val offsetObj = tableCenter.get("_javaOffset")
                if (offsetObj != null && !offsetObj.isNil()) {
                    offsetObj.asUserdata() as androidx.compose.ui.geometry.Offset
                } else {
                    androidx.compose.ui.geometry.Offset.Unspecified
                }
            } else {
                androidx.compose.ui.geometry.Offset.Unspecified
            }

            val radius = if (radiusVal != null && !radiusVal.isNil()) radiusVal.toFloat() else Float.POSITIVE_INFINITY

            val brush = androidx.compose.ui.graphics.Brush.radialGradient(
                *colorStops.toTypedArray(),
                center = center,
                radius = radius
            )
            ComposeBridge.javaToScript(brush)
        })
        scriptTable.set("Brush", brushTable)

        // -------------- Path ------------------
        ComposeBridge.converters[androidx.compose.ui.graphics.Path::class.java] = { obj ->
            val path = obj as androidx.compose.ui.graphics.Path
            val instance = ComposeBridge.engine.createTable()
            instance.set("_javaPath", ComposeBridge.engine.createUserdata(path))
            
            instance.set("moveTo", ComposeBridge.engine.createFunction { innerArgs ->
                val startIdx = if (innerArgs.size > 0 && innerArgs[0].isTable() && !innerArgs[0].asTable().get("_javaPath").isNil()) 1 else 0
                path.moveTo(innerArgs[startIdx].toFloat(), innerArgs[startIdx + 1].toFloat())
                instance
            })
            
            instance.set("lineTo", ComposeBridge.engine.createFunction { innerArgs ->
                val startIdx = if (innerArgs.size > 0 && innerArgs[0].isTable() && !innerArgs[0].asTable().get("_javaPath").isNil()) 1 else 0
                path.lineTo(innerArgs[startIdx].toFloat(), innerArgs[startIdx + 1].toFloat())
                instance
            })
            
            instance.set("close", ComposeBridge.engine.createFunction { innerArgs ->
                path.close()
                instance
            })
            
            instance.set("reset", ComposeBridge.engine.createFunction { innerArgs ->
                path.reset()
                instance
            })
            
            instance.set("addOval", ComposeBridge.engine.createFunction { innerArgs ->
                val startIdx = if (innerArgs.size > 0 && innerArgs[0].isTable() && !innerArgs[0].asTable().get("_javaPath").isNil()) 1 else 0
                val argTable = innerArgs[startIdx].asTable()
                var rectArg: com.kulipai.luacompose.compose.script.ScriptValue? = argTable.get("_javaRect")
                if (rectArg == null || rectArg.isNil()) {
                    rectArg = argTable.get("oval")?.let { if (it.isTable()) it.asTable().get("_javaRect") else it }
                }
                if (rectArg != null && !rectArg.isNil()) {
                    path.addOval(rectArg.asUserdata() as androidx.compose.ui.geometry.Rect)
                }
                instance
            })
            
            instance.set("addRect", ComposeBridge.engine.createFunction { innerArgs ->
                val startIdx = if (innerArgs.size > 0 && innerArgs[0].isTable() && !innerArgs[0].asTable().get("_javaPath").isNil()) 1 else 0
                val argTable = innerArgs[startIdx].asTable()
                var rectArg: com.kulipai.luacompose.compose.script.ScriptValue? = argTable.get("_javaRect")
                if (rectArg == null || rectArg.isNil()) {
                    rectArg = argTable.get("rect")?.let { if (it.isTable()) it.asTable().get("_javaRect") else it }
                }
                if (rectArg != null && !rectArg.isNil()) {
                    path.addRect(rectArg.asUserdata() as androidx.compose.ui.geometry.Rect)
                }
                instance
            })
            
            instance.set("op", ComposeBridge.engine.createFunction { innerArgs ->
                val startIdx = if (innerArgs.size >= 4) 1 else 0
                val path1Table = innerArgs[startIdx].asTable()
                val path1 = path1Table.get("_javaPath").asUserdata() as androidx.compose.ui.graphics.Path
                val path2Table = innerArgs[startIdx + 1].asTable()
                val path2 = path2Table.get("_javaPath").asUserdata() as androidx.compose.ui.graphics.Path
                val operation = innerArgs[startIdx + 2].toInt()
                val pathOperation = when (operation) {
                    0 -> androidx.compose.ui.graphics.PathOperation.Difference
                    1 -> androidx.compose.ui.graphics.PathOperation.Intersect
                    2 -> androidx.compose.ui.graphics.PathOperation.Union
                    3 -> androidx.compose.ui.graphics.PathOperation.Xor
                    4 -> androidx.compose.ui.graphics.PathOperation.ReverseDifference
                    else -> androidx.compose.ui.graphics.PathOperation.Difference
                }
                val success = path.op(path1, path2, pathOperation)
                ComposeBridge.engine.createValue(success)
            })
            instance
        }

        val pathCompanionTable = ComposeBridge.engine.createTable()
        val pathTableMeta = ComposeBridge.engine.createTable()
        pathTableMeta.set("__call", ComposeBridge.engine.createFunction { args ->
            val path = androidx.compose.ui.graphics.Path()
            ComposeBridge.javaToScript(path)
        })
        pathCompanionTable.setMetatable(pathTableMeta)
        scriptTable.set("Path", pathCompanionTable)
        
        val pathOpTable = ComposeBridge.engine.createTable()
        pathOpTable.set("Difference", ComposeBridge.engine.createValue(0))
        pathOpTable.set("Intersect", ComposeBridge.engine.createValue(1))
        pathOpTable.set("Union", ComposeBridge.engine.createValue(2))
        pathOpTable.set("Xor", ComposeBridge.engine.createValue(3))
        pathOpTable.set("ReverseDifference", ComposeBridge.engine.createValue(4))
        scriptTable.set("PathOperation", pathOpTable)
        // -------------- Color ------------------
        ComposeBridge.converters[Color::class.java] = { obj ->
            val color = obj as Color
            val instance = ComposeBridge.engine.createTable()
            instance.set("_javaColor", ComposeBridge.engine.createUserdata(color))
            
            val instanceMeta = ComposeBridge.engine.createTable()
            instanceMeta.set("__index", ComposeBridge.engine.createFunction { innerArgs ->
                val key = innerArgs[1].toStringValue()
                try {
                    // Use kotlin-reflect to find the property dynamically on Color
                    val prop = Color::class.members.find { it.name == key } as? kotlin.reflect.KProperty1<Any, *>
                    if (prop != null) {
                        val value = prop.getter.call(color)
                        return@createFunction ComposeBridge.javaToScript(value)
                    }
                    
                    // Fallback to manual functions
                    if (key == "luminance") {
                        return@createFunction ComposeBridge.engine.createFunction { _ ->
                            ComposeBridge.javaToScript(color.luminance())
                        }
                    } else if (key == "copy") {
                        return@createFunction ComposeBridge.engine.createFunction { funcArgs ->
                            val startIdx = if (funcArgs.isNotEmpty() && funcArgs[0].isTable() && !funcArgs[0].asTable().get("_javaColor").isNil()) 1 else 0
                            val params = funcArgs.getOrNull(startIdx)
                            val alpha = if (params != null && params.isTable()) {
                                val alphaVal = params.asTable().get("alpha")
                                if (!alphaVal.isNil()) alphaVal.toFloat() else color.alpha
                            } else if (params != null && !params.isNil()) {
                                params.toFloat()
                            } else {
                                color.alpha
                            }
                            ComposeBridge.javaToScript(color.copy(alpha = alpha))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                ComposeBridge.engine.createNil()
            })
            instance.setMetatable(instanceMeta)
            instance
        }

        val colorTableMeta = ComposeBridge.engine.createTable()
        colorTableMeta.set("__index", ComposeBridge.engine.createFunction { args ->
            val key = args[1].toStringValue()
            try {
                // Use kotlin-reflect to find the property dynamically on Color.Companion
                val prop = Color.Companion::class.members.find { it.name == key } as? kotlin.reflect.KProperty1<Any, *>
                if (prop != null) {
                    val value = prop.getter.call(Color.Companion)
                    return@createFunction ComposeBridge.javaToScript(value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            ComposeBridge.engine.createNil()
        })
        colorTableMeta.set("__call", ComposeBridge.engine.createFunction { args ->
            val p1 = args.getOrNull(1)
            val p2 = args.getOrNull(2)
            val p3 = args.getOrNull(3)
            val p4 = args.getOrNull(4)
            if (p2 != null && !p2.isNil()) {
                val red = p1?.toFloat() ?: 0f
                val green = p2.toFloat()
                val blue = p3?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
                val alpha = p4?.let { if (!it.isNil()) it.toFloat() else 1f } ?: 1f
                ComposeBridge.javaToScript(Color(red, green, blue, alpha))
            } else {
                val colorInt = p1?.let { if (!it.isNil()) it.toDouble().toLong().toInt() else 0 } ?: 0
                ComposeBridge.javaToScript(Color(colorInt))
            }
        })
        val colorTable = ComposeBridge.engine.createTable()
        colorTable.setMetatable(colorTableMeta)
        scriptTable.set("Color", colorTable)

        val runtimeShaderTable = ComposeBridge.engine.createTable()
        val runtimeShaderMeta = ComposeBridge.engine.createTable()
        runtimeShaderMeta.set("__call", ComposeBridge.engine.createFunction { args ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val shaderSrc = args[1].toStringValue()
                val shader = android.graphics.RuntimeShader(shaderSrc)
                val instance = ComposeBridge.engine.createTable()
                instance.set("_javaRuntimeShader", ComposeBridge.engine.createUserdata(shader))
                instance.set("setFloatUniform", ComposeBridge.engine.createFunction { innerArgs ->
                    val startIdx = if (innerArgs.size > 0 && innerArgs[0].isTable() && !innerArgs[0].asTable().get("_javaRuntimeShader").isNil()) 1 else 0
                    val name = innerArgs[startIdx].toStringValue()
                    val count = innerArgs.size - startIdx - 1
                    if (count == 1) {
                        shader.setFloatUniform(name, innerArgs[startIdx + 1].toFloat())
                    } else if (count == 2) {
                        shader.setFloatUniform(name, innerArgs[startIdx + 1].toFloat(), innerArgs[startIdx + 2].toFloat())
                    } else if (count == 3) {
                        shader.setFloatUniform(name, innerArgs[startIdx + 1].toFloat(), innerArgs[startIdx + 2].toFloat(), innerArgs[startIdx + 3].toFloat())
                    } else if (count == 4) {
                        shader.setFloatUniform(name, innerArgs[startIdx + 1].toFloat(), innerArgs[startIdx + 2].toFloat(), innerArgs[startIdx + 3].toFloat(), innerArgs[startIdx + 4].toFloat())
                    }
                    ComposeBridge.engine.createNil()
                })
                instance
            } else {
                ComposeBridge.engine.createNil()
            }
        })
        runtimeShaderTable.setMetatable(runtimeShaderMeta)
        scriptTable.set("RuntimeShader", runtimeShaderTable)

        val renderEffectTable = ComposeBridge.engine.createTable()
        renderEffectTable.set("createRuntimeShaderEffect", ComposeBridge.engine.createFunction { args ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val shaderTable = args[0].asTable()
                val shader = shaderTable.get("_javaRuntimeShader").asUserdata() as android.graphics.RuntimeShader
                val uniformName = args[1].toStringValue()
                val effect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, uniformName).asComposeRenderEffect()
                ComposeBridge.engine.createUserdata(effect)
            } else {
                ComposeBridge.engine.createNil()
            }
        })
        scriptTable.set("RenderEffect", renderEffectTable)
        
                
        // ------------------- TransformOrigin ---------------
                val transformOriginMethods = ComposeBridge.engine.createTable()
                transformOriginMethods.set("copy", ComposeBridge.engine.createFunction { args ->
                    val self = args[0].asUserdata() as androidx.compose.ui.graphics.TransformOrigin
                    val pX = args.getOrNull(1)
                    val pY = args.getOrNull(2)
                    val pivotFractionX = if (pX != null && !pX.isNil()) pX.toFloat() else self.pivotFractionX
                    val pivotFractionY = if (pY != null && !pY.isNil()) pY.toFloat() else self.pivotFractionY
                    
                    val newInstance = ComposeBridge.engine.createTable()
                    newInstance.set("_javaTransform", ComposeBridge.engine.createUserdata(androidx.compose.ui.graphics.TransformOrigin(pivotFractionX, pivotFractionY)))
                    newInstance.setMetatable(args[0].asTable().getMetatable()!!)
                    newInstance
                })
                
                fun wrapTransformOrigin(to: androidx.compose.ui.graphics.TransformOrigin): ScriptValue {
                    val instance = ComposeBridge.engine.createTable()
                    instance.set("_javaTransform", ComposeBridge.engine.createUserdata(to))
                    val newMeta = ComposeBridge.engine.createTable()
                    newMeta.set("__index", ComposeBridge.engine.createFunction { args ->
                        val obj = args[0]
                        val key = args[1]
                        val realObj = obj.asTable().get("_javaTransform").asUserdata() as androidx.compose.ui.graphics.TransformOrigin
                        val k = key.toStringValue()
                        if (k == "pivotFractionX") return@createFunction ComposeBridge.engine.createValue(realObj.pivotFractionX.toDouble())
                        if (k == "pivotFractionY") return@createFunction ComposeBridge.engine.createValue(realObj.pivotFractionY.toDouble())
                        
                        val custom = transformOriginMethods.get(key)
                        if (!custom.isNil()) return@createFunction custom
                        ComposeBridge.engine.createNil()
                    })
                    instance.setMetatable(newMeta)
                    return instance
                }
                
                val transformOriginMeta = ComposeBridge.engine.createTable()
                transformOriginMeta.set("__index", ComposeBridge.engine.createFunction { args ->
                    val key = args[1]
                    if (key.toStringValue() == "Center") {
                        wrapTransformOrigin(androidx.compose.ui.graphics.TransformOrigin.Center)
                    } else {
                        ComposeBridge.engine.createNil()
                    }
                })
                transformOriginMeta.set("__call", ComposeBridge.engine.createFunction { args ->
                    val pX = args.getOrNull(1)
                    val pY = args.getOrNull(2)
                    val pivotFractionX = if (pX != null && !pX.isNil()) pX.toFloat() else 0.5f
                    val pivotFractionY = if (pY != null && !pY.isNil()) pY.toFloat() else 0.5f
                    wrapTransformOrigin(androidx.compose.ui.graphics.TransformOrigin(pivotFractionX, pivotFractionY))
                })
                
                val transformOriginTable = ComposeBridge.engine.createTable()
                transformOriginTable.setMetatable(transformOriginMeta)
                scriptTable.set("TransformOrigin", transformOriginTable)
            
                val strokeCapTable = ComposeBridge.engine.createTable()
                strokeCapTable.set("Butt", ComposeBridge.engine.createUserdata(StrokeCap.Butt))
                strokeCapTable.set("Round", ComposeBridge.engine.createUserdata(StrokeCap.Round))
                strokeCapTable.set("Square", ComposeBridge.engine.createUserdata(StrokeCap.Square))
                scriptTable.set("StrokeCap", strokeCapTable)

                val strokeMeta = ComposeBridge.engine.createTable()
                strokeMeta.set("__call", ComposeBridge.engine.createFunction { args ->
                    val params = args.getOrNull(1)
                    var width = 0.0f
                    var cap = StrokeCap.Butt
                    if (params != null && params.isTable()) {
                        val pTable = params.asTable()
                        val w = pTable.get("width")
                        if (!w.isNil()) width = w.toFloat()
                        val c = pTable.get("cap")
                        if (!c.isNil()) cap = c.asUserdata() as StrokeCap
                    }
                    val table = ComposeBridge.engine.createTable()
                    table.set("_javaStroke", ComposeBridge.engine.createUserdata(Stroke(width = width, cap = cap)))
                    table
                })
                val strokeObj = ComposeBridge.engine.createTable()
                strokeObj.setMetatable(strokeMeta)
                scriptTable.set("Stroke", strokeObj)
                scriptTable.set("StrokeCap", strokeCapTable)
    }
}
