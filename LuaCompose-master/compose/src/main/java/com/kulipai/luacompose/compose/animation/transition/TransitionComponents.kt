package com.kulipai.luacompose.compose.animation.transition


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import com.kulipai.luacompose.compose.animation.parseAnimationSpec
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import com.kulipai.luacompose.compose.runtime.ComposeScope
import com.kulipai.luacompose.compose.script.ScriptFunction
import com.kulipai.luacompose.compose.script.ScriptTable
import com.kulipai.luacompose.compose.script.ScriptValue
import com.kulipai.luacompose.compose.ui.graphics.ComposeScopeComponent
import com.kulipai.luacompose.compose.ui.resolveModifier

internal fun registerTransitionComponents(): Map<String, @Composable (props: Map<String, Any?>, childScope: ComposeScope?) -> Unit> {
    return mapOf(
        "SharedTransitionLayout" to { props, childScope ->
            val modifier = resolveModifier(props["modifier"])

            var actualChildScope = childScope
            val contentObj = props["content"]
            if (actualChildScope == null && contentObj is ScriptFunction) {
                actualChildScope = ComposeScope(contentObj)
            }

            SharedTransitionLayout(modifier = modifier) {
                if (actualChildScope != null) {
                    ComposeScopeComponent(actualChildScope, this, ComposeBridge.javaToScript(this))
                }
            }
        },
        "Crossfade" to { props, childScope ->
            val targetState = props["targetState"]
            val modifier = resolveModifier(props["modifier"])
            val animationSpecObj = props["animationSpec"]

            var actualChildScope = childScope
            val contentObj = props["content"]
            if (actualChildScope == null && contentObj is ScriptFunction) {
                actualChildScope = ComposeScope(contentObj)
            }

            var spec: FiniteAnimationSpec<Float> = tween()
            if (animationSpecObj is FiniteAnimationSpec<*>) {
                @Suppress("UNCHECKED_CAST")
                spec = animationSpecObj as FiniteAnimationSpec<Float>
            } else if (animationSpecObj is ScriptValue && animationSpecObj.isUserdata() && animationSpecObj.asUserdata() is FiniteAnimationSpec<*>) {
                @Suppress("UNCHECKED_CAST")
                spec = animationSpecObj.asUserdata() as FiniteAnimationSpec<Float>
            } else if (animationSpecObj is ScriptTable) {
                val parsed = parseAnimationSpec<Float>(animationSpecObj)
                if (parsed is FiniteAnimationSpec<*>) {
                    @Suppress("UNCHECKED_CAST")
                    spec = parsed as FiniteAnimationSpec<Float>
                }
            } else if (animationSpecObj is Map<*, *>) {
                val table = ComposeBridge.engine.createTable()
                for ((k, v) in animationSpecObj) {
                    if (k is String) {
                        table.set(k, ComposeBridge.javaToScript(v))
                    }
                }
                val parsed = parseAnimationSpec<Float>(table)
                if (parsed is FiniteAnimationSpec<*>) {
                    @Suppress("UNCHECKED_CAST")
                    spec = parsed as FiniteAnimationSpec<Float>
                }
            }

            Crossfade(
                targetState = targetState,
                modifier = modifier,
                animationSpec = spec,
                label = props["label"] as? String ?: "Crossfade"
            ) { stateValue ->
                if (actualChildScope != null) {
                    ComposeScopeComponent(
                        actualChildScope,
                        null,
                        ComposeBridge.javaToScript(stateValue)
                    )
                }
            }
        },
        "AnimatedContent" to { props, childScope ->
            val targetState = props["targetState"]
            val modifier = resolveModifier(props["modifier"])
            val transitionSpecObj = props["transitionSpec"]

            var actualChildScope = childScope
            val contentObj = props["content"]
            if (actualChildScope == null && contentObj is ScriptFunction) {
                actualChildScope = ComposeScope(contentObj)
            }

            val actualTarget = targetState

            AnimatedContent(
                targetState = actualTarget,
                modifier = modifier,
                transitionSpec = {
                    var transform: ContentTransform =
                        fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(
                            animationSpec = tween(90)
                        )
                    if (transitionSpecObj is ScriptFunction) {
                        try {
                            val scopeTable = ComposeBridge.engine.createTable()
                            scopeTable.set(
                                "initialState",
                                ComposeBridge.javaToScript(this.initialState)
                            )
                            scopeTable.set(
                                "targetState",
                                ComposeBridge.javaToScript(this.targetState)
                            )
                            scopeTable.set(
                                "isTransitioningTo",
                                ComposeBridge.engine.createFunction { args ->
                                    val isMatch =
                                        this@AnimatedContent.initialState == ComposeBridge.scriptToJava(
                                            args[0]
                                        ) &&
                                                this@AnimatedContent.targetState == ComposeBridge.scriptToJava(
                                            args[1]
                                        )
                                    ComposeBridge.engine.createValue(isMatch)
                                })
                            val res = transitionSpecObj.call(scopeTable)
                            if (res.isTable() && res.asTable().get("_transform").isUserdata()) {
                                transform =
                                    res.asTable().get("_transform").asUserdata() as ContentTransform
                            } else if (res.isUserdata() && res.asUserdata() is ContentTransform) {
                                transform = res.asUserdata() as ContentTransform
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    transform
                },
                label = props["label"] as? String ?: "AnimatedContent"
            ) { stateValue ->
                if (actualChildScope != null) {
                    val branchKey = listOf("AnimatedContentBranch", stateValue ?: "__nil__")
                    val branchScope = actualChildScope.getOrCreateChildScope(
                        actualChildScope.contentFunc,
                        branchKey
                    )
                    ComposeScopeComponent(
                        branchScope,
                        this,
                        ComposeBridge.javaToScript(this),
                        ComposeBridge.javaToScript(stateValue)
                    )
                }
            }
        },
        "AnimatedVisibility" to { props, childScope ->
            val visible = props["visible"] as? Boolean ?: true
            val modifier = resolveModifier(props["modifier"])
            val enterObj = props["enter"]
            val exitObj = props["exit"]

            var enter = fadeIn() + expandIn()
            if (enterObj is androidx.compose.animation.EnterTransition) {
                android.util.Log.d("LUA_ANIM", "enterObj is EnterTransition")
                enter = enterObj
            } else if (enterObj is ScriptTable && enterObj.get("_transition").isUserdata()) {
                android.util.Log.d("LUA_ANIM", "enterObj is ScriptTable")
                enter = enterObj.get("_transition").asUserdata() as EnterTransition
            } else if (enterObj is Map<*, *> && enterObj["_transition"] is EnterTransition) {
                android.util.Log.d("LUA_ANIM", "enterObj is Map")
                enter = enterObj["_transition"] as EnterTransition
            } else {
                android.util.Log.d("LUA_ANIM", "enterObj is UNKNOWN: ${enterObj?.javaClass}")
            }
            android.util.Log.d("LUA_ANIM", "Final enter transition: $enter")

            var exit = shrinkOut() + fadeOut()
            if (exitObj is androidx.compose.animation.ExitTransition) {
                exit = exitObj
            } else if (exitObj is ScriptTable && exitObj.get("_transition").isUserdata()) {
                exit = exitObj.get("_transition").asUserdata() as ExitTransition
            } else if (exitObj is Map<*, *> && exitObj["_transition"] is ExitTransition) {
                exit = exitObj["_transition"] as ExitTransition
            }

            val label = props["label"] as? String ?: "AnimatedVisibility"

            var actualChildScope = childScope
            val contentObj = props["content"]
            if (actualChildScope == null && contentObj is ScriptFunction) {
                actualChildScope = ComposeScope(contentObj)
            }

            AnimatedVisibility(
                visible = visible,
                modifier = modifier,
                enter = enter,
                exit = exit,
                label = label
            ) {
                if (actualChildScope != null) {
                    ComposeScopeComponent(actualChildScope, this)
                }
            }
        },
    )
}
