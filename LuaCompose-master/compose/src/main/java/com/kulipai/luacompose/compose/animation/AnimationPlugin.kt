package com.kulipai.luacompose.compose.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.IntOffset
import com.kulipai.luacompose.compose.runtime.ComposeAnimatableState
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import com.kulipai.luacompose.compose.runtime.ComposeScope
import com.kulipai.luacompose.compose.runtime.ComposeScriptPlugin
import com.kulipai.luacompose.compose.ui.resolveColor
import com.kulipai.luacompose.compose.ui.resolveDp
import com.kulipai.luacompose.compose.script.ScriptFunction
import com.kulipai.luacompose.compose.script.ScriptTable
import com.kulipai.luacompose.compose.script.ScriptValue







class AnimationPlugin : ComposeScriptPlugin {
    override val namespace: String = "animation"

    override fun getComponents(): Map<String, @Composable (props: Map<String, Any?>, childScope: ComposeScope?) -> Unit> {
        val map = mutableMapOf<String, @Composable (props: Map<String, Any?>, childScope: ComposeScope?) -> Unit>()
        map.putAll(com.kulipai.luacompose.compose.animation.transition.registerTransitionComponents())
        return map
    }

    private fun resolveAnimationArgs(args: Array<ScriptValue>): Pair<ScriptValue, ScriptValue> {
        val arg1 = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
        if (arg1.isTable() && !arg1.asTable().get("targetValue").isNil()) {
            val t = arg1.asTable()
            return Pair(t.get("targetValue"), t.get("animationSpec"))
        }
        val arg2 = args.getOrNull(1) ?: ComposeBridge.engine.createNil()
        return Pair(arg1, arg2)
    }
    
    private inline fun <reified T> getFiniteSpec(specTable: ScriptValue?, defaultSpec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> {
        if (specTable == null || specTable.isNil()) return defaultSpec
        if (specTable.isTable()) {
            return parseAnimationSpec<T>(specTable.asTable()) as? FiniteAnimationSpec<T> ?: defaultSpec
        }
        return defaultSpec
    }

    override fun injectGlobals(scriptTable: ScriptTable) {
        scriptTable.set("rememberSharedContentState", ComposeBridge.engine.createFunction { args ->
            val scope = ComposeBridge.getActiveSharedTransitionScope()
                ?: ComposeBridge.findContextReceiver<androidx.compose.animation.SharedTransitionScope>()
                ?: throw RuntimeException("rememberSharedContentState() 必须在 SharedTransitionLayout/SharedTransitionScope 中调用")
            val activeScope = ComposeBridge.getActiveScope()
                ?: throw RuntimeException("rememberSharedContentState() 必须在 Compose 上下文中调用")

            val keyArg = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
            val configArg = args.getOrNull(1) ?: ComposeBridge.engine.createNil()
            val key = if (keyArg.isTable()) {
                val table = keyArg.asTable()
                val namedKey = table.get("key")
                if (!namedKey.isNil()) ComposeBridge.scriptToJava(namedKey) else ComposeBridge.scriptToJava(table.get(1))
            } else {
                ComposeBridge.scriptToJava(keyArg)
            } ?: "nil"

            val config =
                if (!configArg.isNil() && configArg.isUserdata() && configArg.asUserdata() is SharedTransitionScope.SharedContentConfig) {
                    configArg.asUserdata() as SharedTransitionScope.SharedContentConfig
                } else {
                    SharedTransitionDefaults.SharedContentConfig
                }

            val rememberFunc = ComposeBridge.engine.createFunction {
                val clazz = Class.forName("androidx.compose.animation.SharedTransitionScope\$SharedContentState")
                val ctor = clazz.declaredConstructors.firstOrNull { constructor ->
                    val params = constructor.parameterTypes
                    params.size == 2 && params[0] == Any::class.java && params[1].isAssignableFrom(config::class.java)
                } ?: clazz.declaredConstructors.first()
                ctor.isAccessible = true
                ComposeBridge.javaToScript(ctor.newInstance(key, config))
            }
            activeScope.getOrCreateRemember(
                rememberFunc,
                listOf("shared-content-state", scope.hashCode(), key, config)
            )
        })

        // Transitions

        // Transitions
        scriptTable.set("fadeIn", ComposeBridge.engine.createFunction { args ->
            var specValue: ScriptValue? = null
            var initialAlpha = 0f
            val arg1 = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
            if (arg1.isTable() && !arg1.asTable().get("animationSpec").isNil()) {
                val t = arg1.asTable()
                specValue = t.get("animationSpec")
                val ia = t.get("initialAlpha")
                if (!ia.isNil()) initialAlpha = ia.toFloat()
            } else if (arg1.isTable() && !arg1.asTable().get("type").isNil()) {
                specValue = arg1
                val ia = args.getOrNull(1)
                if (ia != null && !ia.isNil()) initialAlpha = ia.toFloat()
            } else {
                if (!arg1.isNil()) initialAlpha = arg1.toFloat()
            }
            val spec = getFiniteSpec(specValue, spring<Float>(stiffness = Spring.StiffnessMediumLow))
            val res = createEnterTransition(fadeIn(animationSpec = spec, initialAlpha = initialAlpha))
            android.util.Log.d("LUA_ANIM", "fadeIn returning: $res")
            res
        })
        
        scriptTable.set("fadeOut", ComposeBridge.engine.createFunction { args ->
            var specValue: ScriptValue? = null
            var targetAlpha = 0f
            val arg1 = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
            if (arg1.isTable() && !arg1.asTable().get("animationSpec").isNil()) {
                val t = arg1.asTable()
                specValue = t.get("animationSpec")
                val ta = t.get("targetAlpha")
                if (!ta.isNil()) targetAlpha = ta.toFloat()
            } else if (arg1.isTable() && !arg1.asTable().get("type").isNil()) {
                specValue = arg1
                val ta = args.getOrNull(1)
                if (ta != null && !ta.isNil()) targetAlpha = ta.toFloat()
            } else {
                if (!arg1.isNil()) targetAlpha = arg1.toFloat()
            }
            val spec = getFiniteSpec(specValue, spring<Float>(stiffness = Spring.StiffnessMediumLow))
            createExitTransition(fadeOut(animationSpec = spec, targetAlpha = targetAlpha))
        })
        
        scriptTable.set("expandIn", ComposeBridge.engine.createFunction { args ->
            var specValue: ScriptValue? = null
            val arg1 = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
            if (arg1.isTable() && !arg1.asTable().get("animationSpec").isNil()) {
                specValue = arg1.asTable().get("animationSpec")
            } else if (arg1.isTable() && !arg1.asTable().get("type").isNil()) {
                specValue = arg1
            }
            val spec = getFiniteSpec(specValue, spring<androidx.compose.ui.unit.IntSize>(stiffness = Spring.StiffnessMediumLow))
            createEnterTransition(expandIn(animationSpec = spec))
        })
        
        scriptTable.set("shrinkOut", ComposeBridge.engine.createFunction { args ->
            var specValue: ScriptValue? = null
            val arg1 = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
            if (arg1.isTable() && !arg1.asTable().get("animationSpec").isNil()) {
                specValue = arg1.asTable().get("animationSpec")
            } else if (arg1.isTable() && !arg1.asTable().get("type").isNil()) {
                specValue = arg1
            }
            val spec = getFiniteSpec(specValue, spring<androidx.compose.ui.unit.IntSize>(stiffness = Spring.StiffnessMediumLow))
            createExitTransition(shrinkOut(animationSpec = spec))
        })
        
        scriptTable.set("slideInHorizontally", ComposeBridge.engine.createFunction { args ->
            var specValue: ScriptValue? = null
            var initialOffsetX: ScriptFunction? = null
            val arg1 = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
            if (arg1.isTable() && !arg1.asTable().get("animationSpec").isNil()) {
                specValue = arg1.asTable().get("animationSpec")
                val fn = arg1.asTable().get("initialOffsetX")
                if (fn.isFunction()) initialOffsetX = fn.asFunction()
            } else if (arg1.isTable() && !arg1.asTable().get("type").isNil()) {
                specValue = arg1
                val fn = args.getOrNull(1)
                if (fn != null && fn.isFunction()) initialOffsetX = fn.asFunction()
            } else {
                if (arg1.isFunction()) initialOffsetX = arg1.asFunction()
            }
            val spec = getFiniteSpec(specValue, spring<IntOffset>(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold))
            val offsetFunc: (Int) -> Int = { fullWidth ->
                if (initialOffsetX != null) {
                    initialOffsetX!!.call(ComposeBridge.engine.createValue(fullWidth)).toInt()
                } else {
                    -fullWidth / 2
                }
            }
            val res = createEnterTransition(slideInHorizontally(animationSpec = spec, initialOffsetX = offsetFunc))
            android.util.Log.d("LUA_ANIM", "slideInHorizontally returning: $res")
            res
        })
        
        scriptTable.set("slideOutHorizontally", ComposeBridge.engine.createFunction { args ->
            var specValue: ScriptValue? = null
            var targetOffsetX: ScriptFunction? = null
            val arg1 = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
            if (arg1.isTable() && !arg1.asTable().get("animationSpec").isNil()) {
                specValue = arg1.asTable().get("animationSpec")
                val fn = arg1.asTable().get("targetOffsetX")
                if (fn.isFunction()) targetOffsetX = fn.asFunction()
            } else if (arg1.isTable() && !arg1.asTable().get("type").isNil()) {
                specValue = arg1
                val fn = args.getOrNull(1)
                if (fn != null && fn.isFunction()) targetOffsetX = fn.asFunction()
            } else {
                if (arg1.isFunction()) targetOffsetX = arg1.asFunction()
            }
            val spec = getFiniteSpec(specValue, spring<IntOffset>(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold))
            val offsetFunc: (Int) -> Int = { fullWidth ->
                if (targetOffsetX != null) {
                    targetOffsetX!!.call(ComposeBridge.engine.createValue(fullWidth)).toInt()
                } else {
                    -fullWidth / 2
                }
            }
            createExitTransition(slideOutHorizontally(animationSpec = spec, targetOffsetX = offsetFunc))
        })
        
        scriptTable.set("slideInVertically", ComposeBridge.engine.createFunction { args ->
            var specValue: ScriptValue? = null
            var initialOffsetY: ScriptFunction? = null
            val arg1 = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
            if (arg1.isTable() && !arg1.asTable().get("animationSpec").isNil()) {
                specValue = arg1.asTable().get("animationSpec")
                val fn = arg1.asTable().get("initialOffsetY")
                if (fn.isFunction()) initialOffsetY = fn.asFunction()
            } else if (arg1.isTable() && !arg1.asTable().get("type").isNil()) {
                specValue = arg1
                val fn = args.getOrNull(1)
                if (fn != null && fn.isFunction()) initialOffsetY = fn.asFunction()
            } else {
                if (arg1.isFunction()) initialOffsetY = arg1.asFunction()
            }
            val spec = getFiniteSpec(specValue, spring<IntOffset>(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold))
            val offsetFunc: (Int) -> Int = { fullHeight ->
                if (initialOffsetY != null) {
                    initialOffsetY!!.call(ComposeBridge.engine.createValue(fullHeight)).toInt()
                } else {
                    -fullHeight / 2
                }
            }
            createEnterTransition(slideInVertically(animationSpec = spec, initialOffsetY = offsetFunc))
        })
        
        scriptTable.set("slideOutVertically", ComposeBridge.engine.createFunction { args ->
            var specValue: ScriptValue? = null
            var targetOffsetY: ScriptFunction? = null
            val arg1 = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
            if (arg1.isTable() && !arg1.asTable().get("animationSpec").isNil()) {
                specValue = arg1.asTable().get("animationSpec")
                val fn = arg1.asTable().get("targetOffsetY")
                if (fn.isFunction()) targetOffsetY = fn.asFunction()
            } else if (arg1.isTable() && !arg1.asTable().get("type").isNil()) {
                specValue = arg1
                val fn = args.getOrNull(1)
                if (fn != null && fn.isFunction()) targetOffsetY = fn.asFunction()
            } else {
                if (arg1.isFunction()) targetOffsetY = arg1.asFunction()
            }
            val spec = getFiniteSpec(specValue, spring<IntOffset>(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold))
            val offsetFunc: (Int) -> Int = { fullHeight ->
                if (targetOffsetY != null) {
                    targetOffsetY!!.call(ComposeBridge.engine.createValue(fullHeight)).toInt()
                } else {
                    -fullHeight / 2
                }
            }
            createExitTransition(slideOutVertically(animationSpec = spec, targetOffsetY = offsetFunc))
        })

        scriptTable.set("scaleIn", ComposeBridge.engine.createFunction { args ->
            var specValue: ScriptValue? = null
            var initialScale = 0f
            var transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            val arg1 = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
            if (arg1.isTable() && !arg1.asTable().get("animationSpec").isNil()) {
                specValue = arg1.asTable().get("animationSpec")
                val ia = arg1.asTable().get("initialScale")
                if (!ia.isNil()) initialScale = ia.toFloat()
                val toVal = arg1.asTable().get("transformOrigin")
                if (toVal.isUserdata() && toVal.asUserdata() is androidx.compose.ui.graphics.TransformOrigin) {
                    transformOrigin = toVal.asUserdata() as androidx.compose.ui.graphics.TransformOrigin
                }
            } else if (arg1.isTable() && !arg1.asTable().get("type").isNil()) {
                specValue = arg1
                val ia = args.getOrNull(1)
                if (ia != null && !ia.isNil()) initialScale = ia.toFloat()
                val toVal = args.getOrNull(2)
                if (toVal != null && toVal.isTable() && toVal.asTable().get("_javaTransform").isUserdata()) {
                    transformOrigin = toVal.asTable().get("_javaTransform").asUserdata() as androidx.compose.ui.graphics.TransformOrigin
                }
            } else {
                if (!arg1.isNil()) initialScale = arg1.toFloat()
                val toVal = args.getOrNull(1)
                if (toVal != null && toVal.isTable() && toVal.asTable().get("_javaTransform").isUserdata()) {
                    transformOrigin = toVal.asTable().get("_javaTransform").asUserdata() as androidx.compose.ui.graphics.TransformOrigin
                }
            }
            val spec = getFiniteSpec(specValue, spring<Float>(stiffness = Spring.StiffnessMediumLow))
            createEnterTransition(scaleIn(animationSpec = spec, initialScale = initialScale, transformOrigin = transformOrigin))
        })
        
        scriptTable.set("scaleOut", ComposeBridge.engine.createFunction { args ->
            var specValue: ScriptValue? = null
            var targetScale = 0f
            var transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            val arg1 = args.getOrNull(0) ?: ComposeBridge.engine.createNil()
            if (arg1.isTable() && !arg1.asTable().get("animationSpec").isNil()) {
                specValue = arg1.asTable().get("animationSpec")
                val ta = arg1.asTable().get("targetScale")
                if (!ta.isNil()) targetScale = ta.toFloat()
                val toVal = arg1.asTable().get("transformOrigin")
                if (toVal.isTable() && toVal.asTable().get("_javaTransform").isUserdata()) {
                    transformOrigin = toVal.asTable().get("_javaTransform").asUserdata() as androidx.compose.ui.graphics.TransformOrigin
                }
            } else if (arg1.isTable() && !arg1.asTable().get("type").isNil()) {
                specValue = arg1
                val ta = args.getOrNull(1)
                if (ta != null && !ta.isNil()) targetScale = ta.toFloat()
                val toVal = args.getOrNull(2)
                if (toVal != null && toVal.isTable() && toVal.asTable().get("_javaTransform").isUserdata()) {
                    transformOrigin = toVal.asTable().get("_javaTransform").asUserdata() as androidx.compose.ui.graphics.TransformOrigin
                }
            } else {
                if (!arg1.isNil()) targetScale = arg1.toFloat()
                val toVal = args.getOrNull(1)
                if (toVal != null && toVal.isTable() && toVal.asTable().get("_javaTransform").isUserdata()) {
                    transformOrigin = toVal.asTable().get("_javaTransform").asUserdata() as androidx.compose.ui.graphics.TransformOrigin
                }
            }
            val spec = getFiniteSpec(specValue, spring<Float>(stiffness = Spring.StiffnessMediumLow))
            createExitTransition(scaleOut(animationSpec = spec, targetScale = targetScale, transformOrigin = transformOrigin))
        })

        // AsState animators
        scriptTable.set("animateFloatAsState", ComposeBridge.engine.createFunction { args ->
            val (targetValue, spec) = resolveAnimationArgs(args)
            val target = targetValue.toFloat()
            val activeScope = ComposeBridge.getActiveScope()
            if (activeScope != null) {
                val initFunc = ComposeBridge.engine.createFunction {
                    val state = ComposeAnimatableState(target, kotlin.Float.VectorConverter, activeScope)
                    com.kulipai.luacompose.compose.runtime.createComposeStateTable(state)
                }
                val animState = activeScope.getOrCreateRemember(initFunc)
                val javaState = (animState.asTable().get("javaState").asUserdata() as ComposeAnimatableState<Float, AnimationVector1D>)
                if (spec.isTable()) {
                    javaState.currentSpec = parseAnimationSpec<Float>(spec.asTable()) as AnimationSpec<Float>
                }
                javaState.animateTo(target)
                return@createFunction animState
            }
            ComposeBridge.engine.createNil()
        })

        scriptTable.set("animateIntAsState", ComposeBridge.engine.createFunction { args ->
            val (targetValue, spec) = resolveAnimationArgs(args)
            val target = targetValue.toInt()
            val activeScope = ComposeBridge.getActiveScope()
            if (activeScope != null) {
                val initFunc = ComposeBridge.engine.createFunction {
                    val state = ComposeAnimatableState(target, kotlin.Int.VectorConverter, activeScope)
                    com.kulipai.luacompose.compose.runtime.createComposeStateTable(state)
                }
                val animState = activeScope.getOrCreateRemember(initFunc)
                val javaState = (animState.asTable().get("javaState").asUserdata() as ComposeAnimatableState<Int, AnimationVector1D>)
                if (spec.isTable()) {
                    javaState.currentSpec = parseAnimationSpec<Int>(spec.asTable()) as AnimationSpec<Int>
                }
                javaState.animateTo(target)
                return@createFunction animState
            }
            ComposeBridge.engine.createNil()
        })

        scriptTable.set("animateDpAsState", ComposeBridge.engine.createFunction { args ->
            val (targetValue, spec) = resolveAnimationArgs(args)
            val target = resolveDp(ComposeBridge.scriptToJava(targetValue))
            val activeScope = ComposeBridge.getActiveScope()
            if (activeScope != null) {
                val initFunc = ComposeBridge.engine.createFunction {
                    val state = ComposeAnimatableState(target, androidx.compose.ui.unit.Dp.VectorConverter, activeScope)
                    com.kulipai.luacompose.compose.runtime.createComposeStateTable(state)
                }
                val animState = activeScope.getOrCreateRemember(initFunc)
                val javaState = (animState.asTable().get("javaState").asUserdata() as ComposeAnimatableState<androidx.compose.ui.unit.Dp, AnimationVector1D>)
                if (spec.isTable()) {
                    javaState.currentSpec = parseAnimationSpec<androidx.compose.ui.unit.Dp>(spec.asTable()) as AnimationSpec<androidx.compose.ui.unit.Dp>
                }
                javaState.animateTo(target)
                return@createFunction animState
            }
            ComposeBridge.engine.createNil()
        })

        
        scriptTable.set("togetherWith", ComposeBridge.engine.createFunction { args ->
            val arg1 = args[0]
            val arg2 = args[1]
            var enter: androidx.compose.animation.EnterTransition = androidx.compose.animation.fadeIn()
            var exit: androidx.compose.animation.ExitTransition = androidx.compose.animation.fadeOut()
            
            if (arg1.isTable() && arg1.asTable().get("_transition").isUserdata()) {
                enter = arg1.asTable().get("_transition").asUserdata() as EnterTransition
            } else if (arg1.isUserdata() && arg1.asUserdata() is EnterTransition) {
                enter = arg1.asUserdata() as EnterTransition
            }
            
            if (arg2.isTable() && arg2.asTable().get("_transition").isUserdata()) {
                exit = arg2.asTable().get("_transition").asUserdata() as ExitTransition
            } else if (arg2.isUserdata() && arg2.asUserdata() is ExitTransition) {
                exit = arg2.asUserdata() as ExitTransition
            }
            
            createContentTransform(enter.togetherWith(exit))
        })

        scriptTable.set("animateColorAsState", ComposeBridge.engine.createFunction { args ->
            val (targetValue, spec) = resolveAnimationArgs(args)
            val target = resolveColor(ComposeBridge.scriptToJava(targetValue))
            val activeScope = ComposeBridge.getActiveScope()
            if (activeScope != null) {
                val initFunc = ComposeBridge.engine.createFunction {
                    val state = ComposeAnimatableState(target, androidx.compose.ui.graphics.Color.VectorConverter(target.colorSpace), activeScope)
                    com.kulipai.luacompose.compose.runtime.createComposeStateTable(state)
                }
                val animState = activeScope.getOrCreateRemember(initFunc)
                val javaState = (animState.asTable().get("javaState").asUserdata() as ComposeAnimatableState<androidx.compose.ui.graphics.Color, AnimationVector4D>)
                if (spec.isTable()) {
                    javaState.currentSpec = parseAnimationSpec<androidx.compose.ui.graphics.Color>(spec.asTable()) as AnimationSpec<androidx.compose.ui.graphics.Color>
                }
                javaState.animateTo(target)
                return@createFunction animState
            }
            ComposeBridge.engine.createNil()
        })


    }
}
