package com.nirithy.luacompose.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Animatable 包装器，暴露 animateTo/snapTo 给 Lua
 * 比 animateFloatAsState 更灵活，可在回调中手动触发动画
 *
 * Lua 用法：
 *   local anim = compose.Animatable(0.0)
 *   anim.animateTo(100.0, compose.tween(500))
 *   anim.snapTo(0.0)
 */
class LuaAnimatable(initialValue: Float) {
    val animatable = Animatable(initialValue)
    var scope: CoroutineScope? = null

    /** 获取当前值 */
    fun getValue(): Float = animatable.value

    /** 立即跳转到目标值 */
    fun snapTo(target: Float) {
        scope?.launch { animatable.snapTo(target) }
    }

    /** 动画过渡到目标值 */
    fun animateTo(target: Float, durationMs: Int = 300) {
        scope?.launch {
            animatable.animateTo(target, tween(durationMs))
        }
    }

    /** 是否正在动画中 */
    fun isRunning(): Boolean = animatable.isRunning
}

/**
 * Easing 缓动函数表
 * 提供常用缓动函数供 Lua 使用
 */
object EasingTable {
    val Linear = LinearEasing
    val FastOutSlowIn = FastOutSlowInEasing
    val FastOutLinearIn = FastOutLinearInEasing
    val LinearOutSlowIn = LinearOutSlowInEasing
    val EaseIn = Easing { fraction -> fraction * fraction }
    val EaseOut = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }
    val EaseInOut = Easing { fraction ->
        if (fraction < 0.5f) 2f * fraction * fraction else 1f - (-2f * fraction + 2f) * (-2f * fraction + 2f) / 2f
    }
    val EaseInCubic = Easing { fraction -> fraction * fraction * fraction }
    val EaseOutCubic = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) * (1f - fraction) }
    val EaseInOutCubic = Easing { fraction ->
        if (fraction < 0.5f) 4f * fraction * fraction * fraction
        else 1f - (-2f * fraction + 2f) * (-2f * fraction + 2f) * (-2f * fraction + 2f) / 2f
    }
}