package com.kulipai.luacompose.compose.animation.core

import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import com.kulipai.luacompose.compose.animation.parseAnimationSpec
import com.kulipai.luacompose.compose.runtime.ComposeAnimatableState
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import com.kulipai.luacompose.compose.runtime.ComposeScope
import com.kulipai.luacompose.compose.runtime.ComposeScriptPlugin
import com.kulipai.luacompose.compose.script.ScriptFunction
import com.kulipai.luacompose.compose.script.ScriptTable
import com.kulipai.luacompose.compose.script.ScriptValue
import kotlinx.coroutines.launch

class AnimationCorePlugin : ComposeScriptPlugin {
    override val namespace: String = "animation.core"

    override fun getComponents(): Map<String, @Composable (props: Map<String, Any?>, childScope: ComposeScope?) -> Unit> {
        return emptyMap()
    }

    override fun injectGlobals(scriptTable: ScriptTable) {
        val engine = ComposeBridge.engine

        scriptTable.set("Animatable", engine.createFunction { args ->
            val initialValueRaw = args.getOrNull(0) ?: engine.createNil()
            val initialValueAny = if (!initialValueRaw.isNil()) {
                com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptToJava(initialValueRaw) ?: 0f
            } else 0f
            
            val initialValue = if (initialValueAny is Double) initialValueAny.toFloat()
                else if (initialValueAny is Int) initialValueAny.toFloat()
                else initialValueAny

            val typeConverter = if (initialValue is androidx.compose.ui.geometry.Offset) {
                androidx.compose.ui.geometry.Offset.VectorConverter
            } else {
                kotlin.Float.VectorConverter
            }
            
            val state = com.kulipai.luacompose.compose.runtime.ComposeAnimatableState(
                initialValue, typeConverter as androidx.compose.animation.core.TwoWayConverter<Any, androidx.compose.animation.core.AnimationVector>, com.kulipai.luacompose.compose.runtime.ComposeBridge.getActiveScope()!!
            )
            
            val table = engine.createTable()
            
            val meta = engine.createTable()
            meta.set("__index", engine.createFunction { idxArgs ->
                val key = idxArgs[1].toStringValue()
                if (key == "value") {
                    val activeScope = com.kulipai.luacompose.compose.runtime.ComposeBridge.getActiveScope()
                    if (activeScope != null) {
                        state.registerDependency(activeScope)
                    }
                    return@createFunction com.kulipai.luacompose.compose.runtime.ComposeBridge.javaToScript(state.get())
                }
                if (key == "isRunning") {
                    return@createFunction engine.createValue(state.animatable.isRunning)
                }
                if (key == "snapTo") {
                    return@createFunction engine.createFunction { snapArgs ->
                        val isMethodCall = snapArgs.getOrNull(0) == table
                        val targetArg = if (isMethodCall) snapArgs.getOrNull(1) else snapArgs.getOrNull(0)
                        
                        var target = com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptToJava(targetArg) ?: 0f
                        if (target is Double) target = target.toFloat()
                        if (target is Int) target = target.toFloat()

                        state.scope.coroutineScope?.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                            state.animatable.snapTo(target)
                            val ms = state.composeState as? androidx.compose.runtime.MutableState<Any?>
                            if (ms != null) ms.value = target
                            state.invalidateDependents()
                        }
                        engine.createNil()
                    }
                }
                if (key == "animateTo") {
                    return@createFunction engine.createFunction { animArgs ->
                        val isMethodCall = animArgs.getOrNull(0) == table
                        val targetArg = if (isMethodCall) animArgs.getOrNull(1) else animArgs.getOrNull(0)
                        
                        var target: Any = 0f
                        var spec: AnimationSpec<Any>? = null
                        if (targetArg != null && targetArg.isTable() && targetArg.asTable().get("targetValue") != null && !targetArg.asTable().get("targetValue").isNil()) {
                            val t = targetArg.asTable()
                            val targetVal = com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptToJava(t.get("targetValue")) ?: 0f
                            target = if (targetVal is Double) targetVal.toFloat() else if (targetVal is Int) targetVal.toFloat() else targetVal
                            
                            val s = t.get("animationSpec")
                            if (!s.isNil()) spec = parseAnimationSpec<Any>(s.asTable())
                        } else {
                            val targetVal = com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptToJava(targetArg) ?: 0f
                            target = if (targetVal is Double) targetVal.toFloat() else if (targetVal is Int) targetVal.toFloat() else targetVal
                        }
                        state.scope.coroutineScope?.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                            val block: androidx.compose.animation.core.Animatable<Any, androidx.compose.animation.core.AnimationVector>.() -> Unit = {
                                val ms = state.composeState as? androidx.compose.runtime.MutableState<Any?>
                                if (ms != null) ms.value = this.value
                                state.invalidateDependents()
                            }
                            if (spec != null) {
                                state.animatable.animateTo(target, spec, block = block)
                            } else if (state.currentSpec != null) {
                                state.animatable.animateTo(target, state.currentSpec!!, block = block)
                            } else {
                                state.animatable.animateTo(target, block = block)
                            }
                        }
                        engine.createNil()
                    }
                }
                engine.createNil()
            })
            table.setMetatable(meta)
            table
        })

        scriptTable.set("tween", engine.createFunction { args ->
            var duration = 300
            var delay = 0
            var easingStr = "FastOutSlowInEasing"

            val arg1 = args.getOrNull(0) ?: engine.createNil()
            if (arg1.isTable()) {
                val t = arg1.asTable()
                val dur = t.get("durationMillis")
                val dur2 = t.get("duration")
                duration = if (!dur.isNil()) dur.toInt() else if (!dur2.isNil()) dur2.toInt() else 300
                val del = t.get("delayMillis")
                val del2 = t.get("delay")
                delay = if (!del.isNil()) del.toInt() else if (!del2.isNil()) del2.toInt() else 0
                val eas = t.get("easing")
                easingStr = if (!eas.isNil()) eas.toStringValue() else "FastOutSlowInEasing"
            } else {
                val dur = args.getOrNull(0)
                val del = args.getOrNull(1)
                val eas = args.getOrNull(2)
                duration = if (dur != null && !dur.isNil()) dur.toInt() else 300
                delay = if (del != null && !del.isNil()) del.toInt() else 0
                easingStr = if (eas != null && !eas.isNil()) eas.toStringValue() else "FastOutSlowInEasing"
            }

            val table = engine.createTable()
            table.set("type", engine.createValue("tween"))
            table.set("duration", engine.createValue(duration))
            table.set("delay", engine.createValue(delay))
            table.set("easing", engine.createValue(easingStr))
            table
        })

        scriptTable.set("spring", engine.createFunction { args ->
            var damping = Spring.DampingRatioNoBouncy
            var stiffness = Spring.StiffnessMedium

            val arg1 = args.getOrNull(0) ?: engine.createNil()
            if (arg1.isTable()) {
                val t = arg1.asTable()
                val damp = t.get("dampingRatio")
                val stiff = t.get("stiffness")
                if (!damp.isNil()) damping = damp.toFloat()
                if (!stiff.isNil()) stiffness = stiff.toFloat()
            } else {
                val damp = args.getOrNull(0)
                val stiff = args.getOrNull(1)
                if (damp != null && !damp.isNil()) damping = damp.toFloat()
                if (stiff != null && !stiff.isNil()) stiffness = stiff.toFloat()
            }

            val table = engine.createTable()
            table.set("type", engine.createValue("spring"))
            table.set("dampingRatio", engine.createValue(damping.toDouble()))
            table.set("stiffness", engine.createValue(stiffness.toDouble()))
            table
        })

        scriptTable.set("infiniteRepeatable", engine.createFunction { args ->
            val arg1 = args.getOrNull(0) ?: engine.createNil()
            var animSpec: ScriptValue = engine.createNil()
            var repeatModeStr = "Restart"
            if (arg1.isTable()) {
                val t = arg1.asTable()
                val spec = t.get("animation")
                if (!spec.isNil()) animSpec = spec
                val rm = t.get("repeatMode")
                if (!rm.isNil()) {
                    val jObj = ComposeBridge.scriptToJava(rm)
                    if (jObj is String) repeatModeStr = jObj
                    else if (jObj is RepeatMode) repeatModeStr = jObj.name
                }
            } else {
                val spec = args.getOrNull(0)
                if (spec != null && !spec.isNil()) animSpec = spec
                val rm = args.getOrNull(1)
                if (rm != null && !rm.isNil()) {
                    val jObj = ComposeBridge.scriptToJava(rm)
                    if (jObj is String) repeatModeStr = jObj
                    else if (jObj is RepeatMode) repeatModeStr = jObj.name
                }
            }

            val table = engine.createTable()
            table.set("type", engine.createValue("infiniteRepeatable"))
            table.set("animation", animSpec)
            table.set("repeatMode", ComposeBridge.javaToScript(if (repeatModeStr == "Reverse") RepeatMode.Reverse else RepeatMode.Restart))
            table
        })

        scriptTable.set("rememberInfiniteTransition", engine.createFunction {
            val transitionTable = engine.createTable()
            transitionTable.set("animateFloat", engine.createFunction { args ->
                val arg1 = args.getOrNull(0)
                var initialValue = 0f
                var targetValue = 0f
                var specValue: ScriptValue? = null

                if (arg1 != null && arg1.isTable()) {
                    val t = arg1.asTable()
                    val init = t.get("initialValue")
                    if (!init.isNil()) initialValue = init.toFloat()
                    val target = t.get("targetValue")
                    if (!target.isNil()) targetValue = target.toFloat()
                    val spec = t.get("animationSpec")
                    if (!spec.isNil()) specValue = spec
                } else {
                    val init = args.getOrNull(0)
                    if (init != null && !init.isNil()) initialValue = init.toFloat()
                    val target = args.getOrNull(1)
                    if (target != null && !target.isNil()) targetValue = target.toFloat()
                    val spec = args.getOrNull(2)
                    if (spec != null && !spec.isNil()) specValue = spec
                }

                val activeScope = ComposeBridge.getActiveScope()
                if (activeScope != null) {
                    val initFunc = engine.createFunction {
                        val state = ComposeAnimatableState(initialValue, kotlin.Float.VectorConverter, activeScope)
                        com.kulipai.luacompose.compose.runtime.createComposeStateTable(state)
                    }
                    val animState = activeScope.getOrCreateRemember(initFunc)
                    val javaState = (animState.asTable().get("javaState").asUserdata() as ComposeAnimatableState<Float, AnimationVector1D>)
                    if (specValue != null && specValue.isTable()) {
                        javaState.currentSpec = parseAnimationSpec<Float>(specValue.asTable()) as AnimationSpec<Float>
                    }
                    if (!javaState.animatable.isRunning) {
                        javaState.animateTo(targetValue)
                    }
                    return@createFunction animState
                }
                engine.createNil()
            })
            transitionTable
        })

        scriptTable.set("animateFloat", scriptTable.get("rememberInfiniteTransition").asFunction().call().asTable().get("animateFloat"))

        scriptTable.set("FastOutSlowInEasing", engine.createValue("FastOutSlowInEasing"))
        scriptTable.set("LinearEasing", engine.createValue("LinearEasing"))
        scriptTable.set("FastOutLinearInEasing", engine.createValue("FastOutLinearInEasing"))
        scriptTable.set("LinearOutSlowInEasing", engine.createValue("LinearOutSlowInEasing"))
        scriptTable.set("EaseInOutCubic", engine.createValue("EaseInOutCubic"))
        scriptTable.set("EaseOutBounce", engine.createValue("EaseOutBounce"))

        scriptTable.set("CubicBezierEasing", engine.createFunction { args ->
            val a = args.getOrNull(0)?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
            val b = args.getOrNull(1)?.let { if (!it.isNil()) it.toFloat() else 0f } ?: 0f
            val c = args.getOrNull(2)?.let { if (!it.isNil()) it.toFloat() else 1f } ?: 1f
            val d = args.getOrNull(3)?.let { if (!it.isNil()) it.toFloat() else 1f } ?: 1f
            val table = engine.createTable()
            table.set("type", engine.createValue("CubicBezierEasing"))
            table.set("a", engine.createValue(a.toDouble()))
            table.set("b", engine.createValue(b.toDouble()))
            table.set("c", engine.createValue(c.toDouble()))
            table.set("d", engine.createValue(d.toDouble()))
            table
        })

        val repeatModeTable = engine.createTable()
        repeatModeTable.set("Restart", ComposeBridge.javaToScript(RepeatMode.Restart))
        repeatModeTable.set("Reverse", ComposeBridge.javaToScript(RepeatMode.Reverse))
        scriptTable.set("RepeatMode", repeatModeTable)


        // ------------------ Spring ------------------
        val springTable = ComposeBridge.engine.createTable()
        springTable.set("StiffnessHigh", ComposeBridge.javaToScript(Spring.StiffnessHigh))
        springTable.set("StiffnessMedium", ComposeBridge.javaToScript(Spring.StiffnessMedium))
        springTable.set("StiffnessMediumLow", ComposeBridge.javaToScript(Spring.StiffnessMediumLow))
        springTable.set("StiffnessLow", ComposeBridge.javaToScript(Spring.StiffnessLow))
        springTable.set("StiffnessVeryLow", ComposeBridge.javaToScript(Spring.StiffnessVeryLow))
        springTable.set("DampingRatioHighBouncy", ComposeBridge.javaToScript(Spring.DampingRatioHighBouncy))
        springTable.set("DampingRatioMediumBouncy", ComposeBridge.javaToScript(Spring.DampingRatioMediumBouncy))
        springTable.set("DampingRatioLowBouncy", ComposeBridge.javaToScript(Spring.DampingRatioLowBouncy))
        springTable.set("DampingRatioNoBouncy", ComposeBridge.javaToScript(Spring.DampingRatioNoBouncy))
        springTable.set("DefaultDisplacementThreshold", ComposeBridge.javaToScript(Spring.DefaultDisplacementThreshold))


        scriptTable.set("Spring",springTable)




    }
}
