package com.nirithy.luacompose.bridge

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 动画 Dp 状态包装器
 *
 * Lua 端：
 *   local dp = compose.animateDpAsState(160)
 *   dp:setTarget(330)  -- 设置目标值
 *   print(dp.value)    -- 读取当前动画值
 *
 * 内部由 ComposeHost.animateValues() 使用 animateDpAsState 驱动。
 */
class AnimatedDp(
    initialValue: Float,
    val spec: AnimationSpec<Dp>? = null
) {
    val targetValue: MutableState<Dp> = mutableStateOf(initialValue.dp)
    val animatedValue: MutableState<Float> = mutableStateOf(initialValue)

    /** Lua 可读 .value */
    fun getValue(): Float = animatedValue.value

    /** Lua 通过 .value 访问时，luajava 会隐式传入 self 作为第一个参数，需要重载匹配 */
    @Suppress("UNUSED_PARAMETER")
    fun getValue(ignored: Any?): Float {
        return getValue()
    }

    /** Lua 设置目标值 */
    fun setTarget(target: Float) {
        targetValue.value = target.dp
    }
}