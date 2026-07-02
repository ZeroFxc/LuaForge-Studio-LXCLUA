package com.nirithy.luacompose.bridge

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * 动画颜色状态包装器
 *
 * Lua 端：
 *   local anim = compose.animateColorAsState(0xFF000000)
 *   anim:setTarget(0xFFFF0000)  -- 设置目标颜色，ComposeHost 自动驱动颜色过渡动画
 *   print(anim.value)    -- 读取当前动画颜色值（平滑过渡的ARGB Long）
 *
 * 内部由 ComposeHost.animateValues() 使用 animateColorAsState 驱动。
 */
class AnimatedColor(
    initialColorArgb: Long
) {
    /** 初始颜色（使用 Long 构造 sRGB Color，不能用 toULong()，否则ColorSpace越界） */
    private val initialColor: Color = Color(initialColorArgb)

    /** 目标颜色，Lua 通过 setTarget() 修改 */
    val targetValue: MutableState<Color> = mutableStateOf(initialColor)

    /** 当前动画颜色值，由 ComposeHost 在每帧动画后更新 */
    val animatedValue: MutableState<Color> = mutableStateOf(initialColor)

    /** 动画规格，Lua 可通过 animateColorAsState({animationSpec = ...}) 自定义 */
    var animationSpec: AnimationSpec<Color> = defaultSpec

    /** Lua 可读 .value，返回 ARGB Long 值（sRGB 格式，与Color(Long)构造一致） */
    fun getValue(): Long {
        val c = animatedValue.value
        // 手动打包 ARGB（Color 在 sRGB 下 red/green/blue/alpha 均为 0f..1f）
        val a = (c.alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        val r = (c.red   * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (c.green * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (c.blue  * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    /** Lua 通过 .value 访问时，luajava 会隐式传入 self 作为第一个参数，需要重载匹配 */
    @Suppress("UNUSED_PARAMETER")
    fun getValue(ignored: Any?): Long {
        return getValue()
    }

    /** Lua 设置目标颜色（ARGB Long，sRGB格式），触发动画 */
    fun setTarget(target: Long) {
        targetValue.value = Color(target)
    }

    companion object {
        /** 默认颜色动画规格（spring 弹性动画） */
        val defaultSpec: AnimationSpec<Color> = spring()
    }
}
