package com.nirithy.luacompose.bridge

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * 动画浮点状态包装器
 *
 * Lua 端：
 *   local anim = compose.animateFloatAsState(0)
 *   anim:setTarget(100)  -- 设置目标值，ComposeHost 自动驱动动画
 *   print(anim.value)    -- 读取当前动画值（平滑过渡）
 *
 * 内部由 ComposeHost.animateValues() 使用 animateFloatAsState + 指定动画规格驱动。
 * - 普通模式：每帧动画值变化后，snapshotFlow 异步触发 refreshNodeTree 重新解析 Lua 节点树
 * - useRecompose 模式：每帧动画值变化后，仅递增 recomposeTrigger 触发轻量重组
 */
class AnimatedFloat(
    initialValue: Float,
    /** true = 使用 recomposeTrigger 轻量重组（适合拖拽等需要保持手势的场景） */
    val useRecompose: Boolean = false,
    /** 动画规格，null 表示默认 spring() */
    var spec: AnimationSpec<Float>? = null
) {
    /** 目标值，Lua 通过 setTarget() 修改，ComposeHost 观察此值驱动动画 */
    val targetValue: MutableState<Float> = mutableStateOf(initialValue)

    /** 当前动画值，由 ComposeHost 在每帧动画后更新，触发读取它的 UI 重组 */
    val animatedValue: MutableState<Float> = mutableStateOf(initialValue)

    /** Lua 可读 .value */
    fun getValue(): Float = animatedValue.value

    /** Lua 设置目标值，触发动画 */
    fun setTarget(target: Float) {
        targetValue.value = target
    }

    companion object {
        /** 根据 Lua 传入的 spec 表创建 AnimationSpec */
        fun parseSpec(spec: Map<*, *>?): AnimationSpec<Float> {
            if (spec == null) return spring()
            val type = spec["type"] as? String ?: "spring"
            when (type) {
                "tween" -> {
                    val durationMs = (spec["durationMs"] as? Number)?.toInt() ?: 300
                    val easingName = spec["easing"] as? String ?: "FastOutSlowIn"
                    val easing: Easing = when (easingName) {
                        "Linear" -> LinearEasing
                        "FastOutSlowIn" -> FastOutSlowInEasing
                        "FastOutLinearIn" -> FastOutLinearInEasing
                        "LinearOutSlowIn" -> LinearOutSlowInEasing
                        else -> FastOutSlowInEasing
                    }
                    return tween(durationMillis = durationMs, easing = easing)
                }
                else -> return spring()
            }
        }
    }
}