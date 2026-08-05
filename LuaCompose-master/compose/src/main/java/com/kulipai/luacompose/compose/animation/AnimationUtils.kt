package com.kulipai.luacompose.compose.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutBounce
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import com.kulipai.luacompose.compose.script.ScriptTable

fun createEnterTransition(transition: EnterTransition): ScriptTable {
    val meta = ComposeBridge.engine.createTable()
    meta.set("togetherWith", ComposeBridge.engine.createFunction { args ->
        val self = args[0]
        val exitVal = args[1]
        val enter = self.asTable().get("_transition").asUserdata() as EnterTransition
        var exit: ExitTransition = fadeOut()
        if (exitVal.isTable() && exitVal.asTable().get("_transition").isUserdata()) {
            exit = exitVal.asTable().get("_transition").asUserdata() as ExitTransition
        } else if (exitVal.isUserdata() && exitVal.asUserdata() is ExitTransition) {
            exit = exitVal.asUserdata() as ExitTransition
        }
        createContentTransform(enter.togetherWith(exit))
    })
    meta.set("__index", meta)

    val table = ComposeBridge.engine.createTableWithAdd { args ->
        val arg1 = args[0]
        val arg2 = args[1]
        var t1: EnterTransition? = null
        var t2: EnterTransition? = null
        if (arg1.isTable() && arg1.asTable().get("_transition").isUserdata()) {
            t1 = arg1.asTable().get("_transition").asUserdata() as EnterTransition
        }
        if (arg2.isTable() && arg2.asTable().get("_transition").isUserdata()) {
            t2 = arg2.asTable().get("_transition").asUserdata() as EnterTransition
        }
        if (t1 != null && t2 != null) {
            createEnterTransition(t1 + t2)
        } else {
            arg1
        }
    }
    table.set("_transition", ComposeBridge.engine.createUserdata(transition))
    table.setMetatable(meta)
    return table
}

fun createExitTransition(transition: ExitTransition): ScriptTable {
    val meta = ComposeBridge.engine.createTable()
    meta.set("__index", meta)

    val table = ComposeBridge.engine.createTableWithAdd { args ->
        val arg1 = args[0]
        val arg2 = args[1]
        var t1: ExitTransition? = null
        var t2: ExitTransition? = null
        if (arg1.isTable() && arg1.asTable().get("_transition").isUserdata()) {
            t1 = arg1.asTable().get("_transition").asUserdata() as ExitTransition
        }
        if (arg2.isTable() && arg2.asTable().get("_transition").isUserdata()) {
            t2 = arg2.asTable().get("_transition").asUserdata() as ExitTransition
        }
        if (t1 != null && t2 != null) {
            createExitTransition(t1 + t2)
        } else {
            arg1
        }
    }
    table.set("_transition", ComposeBridge.engine.createUserdata(transition))
    table.setMetatable(meta)
    return table
}

fun createContentTransform(transform: ContentTransform): ScriptTable {
    val table = ComposeBridge.engine.createTable()
    table.set("_transform", ComposeBridge.engine.createUserdata(transform))
    return table
}

fun <T> parseAnimationSpec(table: ScriptTable): AnimationSpec<T> {
    val typeVal = table.get("type")
    val type = if (!typeVal.isNil()) typeVal.toStringValue() else "spring"
    return when (type) {
        "tween" -> {
            val dur = table.get("durationMillis")
            val dur2 = table.get("duration")
            val duration =
                if (!dur.isNil()) dur.toInt() else if (!dur2.isNil()) dur2.toInt() else 300
            val del = table.get("delayMillis")
            val del2 = table.get("delay")
            val delay = if (!del.isNil()) del.toInt() else if (!del2.isNil()) del2.toInt() else 0
            val eas = table.get("easing")
            val easing = if (eas.isTable() && eas.asTable().get("type").toStringValue() == "CubicBezierEasing") {
                val t = eas.asTable()
                CubicBezierEasing(
                    t.get("a").toFloat(),
                    t.get("b").toFloat(),
                    t.get("c").toFloat(),
                    t.get("d").toFloat()
                )
            } else {
                val easingStr = if (!eas.isNil()) eas.toStringValue() else "FastOutSlowInEasing"
                when (easingStr) {
                    "LinearEasing" -> LinearEasing
                    "FastOutLinearInEasing" -> FastOutLinearInEasing
                    "LinearOutSlowInEasing" -> LinearOutSlowInEasing
                    "EaseInOutCubic" -> EaseInOutCubic
                    "EaseOutBounce" -> EaseOutBounce
                    else -> FastOutSlowInEasing
                }
            }
            tween(durationMillis = duration, delayMillis = delay, easing = easing)
        }

        "spring" -> {
            val damp = table.get("dampingRatio")
            val dampingRatio =
                if (!damp.isNil()) damp.toFloat() else Spring.DampingRatioNoBouncy.toFloat()
            val stiff = table.get("stiffness")
            val stiffness =
                if (!stiff.isNil()) stiff.toFloat() else Spring.StiffnessMedium.toFloat()
            spring(dampingRatio = dampingRatio, stiffness = stiffness)
        }

        "infiniteRepeatable" -> {
            val animationSpec = table.get("animation")
            val animation = if (!animationSpec.isNil() && animationSpec.isTable()) {
                parseAnimationSpec<T>(animationSpec.asTable()) as? DurationBasedAnimationSpec<T> ?: tween()
            } else tween()
            val repeatModeProp = table.get("repeatMode")
            val repeatMode = if (!repeatModeProp.isNil()) {
                com.kulipai.luacompose.compose.runtime.ComposeBridge.scriptToJava(repeatModeProp) as? RepeatMode ?: RepeatMode.Restart
            } else RepeatMode.Restart
            infiniteRepeatable(animation = animation, repeatMode = repeatMode)
        }

        else -> spring()
    }
}

