package com.nirithy.luacompose.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.luajava.LuaObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Animatable 包装器，暴露 animateTo/snapTo 给 Lua
 * 比 animateFloatAsState 更灵活，可在回调中手动触发动画
 *
 * Lua 用法：
 *   local anim = compose.Animatable(0.0)
 *   anim:animateTo(100.0, compose.spring(0.55, 600))
 *   anim:snapTo(0.0)
 */
class LuaAnimatable(initialValue: Float) {
    val animatable = Animatable(initialValue)
    private val targetScope: CoroutineScope? get() = ComposeBridgeInstance.current.mainScope

    /** 获取当前值 */
    fun getValue(): Float = animatable.value

    /** Lua 通过 .value 访问时，luajava 会隐式传入 self 作为第一个参数 */
    @Suppress("UNUSED_PARAMETER")
    fun getValue(ignored: Any?): Float = animatable.value

    /** 立即跳转到目标值 */
    fun snapTo(target: Float) {
        targetScope?.launch { animatable.snapTo(target) }
    }

    /** Lua : 语法兼容：snapTo(self, target) */
    @Suppress("UNUSED_PARAMETER")
    fun snapTo(ignored: Any?, target: Float) {
        targetScope?.launch { animatable.snapTo(target) }
    }

    /** 动画过渡到目标值（默认 spring） */
    fun animateTo(target: Float) {
        targetScope?.launch {
            animatable.animateTo(target, spring())
        }
    }

    /** Lua : 语法兼容：animateTo(self, target) */
    @Suppress("UNUSED_PARAMETER")
    fun animateTo(ignored: Any?, target: Float) {
        targetScope?.launch {
            animatable.animateTo(target, spring())
        }
    }

    /** 动画过渡到目标值，带 durationMs */
    fun animateTo(target: Float, durationMs: Int) {
        targetScope?.launch {
            animatable.animateTo(target, tween(durationMs))
        }
    }

    /** Lua : 语法兼容：animateTo(self, target, durationMs) */
    @Suppress("UNUSED_PARAMETER")
    fun animateTo(ignored: Any?, target: Float, durationMs: Int) {
        targetScope?.launch {
            animatable.animateTo(target, tween(durationMs))
        }
    }

    /** 动画过渡到目标值，带 Lua spec 表 {type="spring"/"tween", ...} */
    fun animateTo(target: Float, spec: LuaObject) {
        val animSpec = parseSpec(spec)
        targetScope?.launch {
            animatable.animateTo(target, animSpec)
        }
    }

    /** Lua : 语法兼容：animateTo(self, target, spec) */
    @Suppress("UNUSED_PARAMETER")
    fun animateTo(ignored: Any?, target: Float, spec: LuaObject) {
        val animSpec = parseSpec(spec)
        targetScope?.launch {
            animatable.animateTo(target, animSpec)
        }
    }

    /** 是否正在动画中 */
    fun isRunning(): Boolean = animatable.isRunning

    companion object {
        /** 解析 Lua 表为 AnimationSpec */
        fun parseSpec(luaTable: LuaObject): AnimationSpec<Float> {
            val type = try { luaTable.getField("type")?.getString() } catch (_: Exception) { "spring" }
            when (type) {
                "tween" -> {
                    val durationMs = try { luaTable.getField("durationMs")?.getNumber()?.toInt() } catch (_: Exception) { 300 } ?: 300
                    val easingName = try { luaTable.getField("easing")?.getString() } catch (_: Exception) { "FastOutSlowIn" } ?: "FastOutSlowIn"
                    val easing: Easing = when (easingName) {
                        "Linear" -> LinearEasing
                        "FastOutSlowIn" -> FastOutSlowInEasing
                        "FastOutLinearIn" -> FastOutLinearInEasing
                        "LinearOutSlowIn" -> LinearOutSlowInEasing
                        else -> FastOutSlowInEasing
                    }
                    return tween(durationMillis = durationMs, easing = easing)
                }
                "spring" -> {
                    val dampingRatio = try { luaTable.getField("dampingRatio")?.getNumber()?.toFloat() } catch (_: Exception) { 0.55f } ?: 0.55f
                    val stiffness = try { luaTable.getField("stiffness")?.getNumber()?.toFloat() } catch (_: Exception) { 600f } ?: 600f
                    return spring(dampingRatio = dampingRatio, stiffness = stiffness)
                }
                else -> return spring()
            }
        }
    }
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