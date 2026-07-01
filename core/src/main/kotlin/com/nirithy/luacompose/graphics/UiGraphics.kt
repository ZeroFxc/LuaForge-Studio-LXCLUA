package com.nirithy.luacompose.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

/**
 * UI 图形首类对象 — Color / Offset / Size / Rect
 * 替代直接传 Long/Double 值的方式，提供更符合 Compose 习惯的 API
 *
 * Lua 用法：
 *   local c = compose.Color(0xFFRRGGBB)
 *   local c2 = c.copy(alpha = 0.5)
 *   local pos = compose.Offset(100, 200)
 *   local sz = compose.Size(300, 400)
 *   local rect = compose.Rect(0, 0, 200, 100)
 */

// ========== Color 对象 ==========

/** Lua 可用的 Color 对象，支持 copy() 修改 alpha */
class LuaColor(private val argb: Long) {
    fun toArgb(): Long = argb
    fun toInt(): Int = argb.toInt()
    fun toComposeColor(): Color = Color(argb.toInt())

    /** 获取/设置 alpha 分量 (0-255) */
    fun getAlpha(): Int = ((argb shr 24) and 0xFF).toInt()
    fun copy(alpha: Double): LuaColor {
        val a = (alpha * 255).toInt().coerceIn(0, 255).toLong()
        return LuaColor((argb and 0x00FFFFFF) or (a shl 24))
    }

    /** 获取 R/G/B 分量 */
    fun getRed(): Int = ((argb shr 16) and 0xFF).toInt()
    fun getGreen(): Int = ((argb shr 8) and 0xFF).toInt()
    fun getBlue(): Int = (argb and 0xFF).toInt()

    override fun toString(): String = "Color(#${java.lang.Long.toHexString(argb)})"
}

// ========== Offset 对象 ==========

/** Lua 可用的 Offset 对象 */
class LuaOffset(val x: Double, val y: Double) {
    fun toComposeOffset(): Offset = Offset(x.toFloat(), y.toFloat())
    override fun toString(): String = "Offset($x, $y)"
}

// ========== Size 对象 ==========

/** Lua 可用的 Size 对象 */
class LuaSize(val width: Double, val height: Double) {
    fun toComposeSize(): Size = Size(width.toFloat(), height.toFloat())
    override fun toString(): String = "Size($width, $height)"
}

// ========== Rect 对象 ==========

/** Lua 可用的 Rect 对象 */
class LuaRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun toComposeRect(): Rect = Rect(Offset(left.toFloat(), top.toFloat()), Offset(right.toFloat(), bottom.toFloat()))
    fun getWidth(): Double = right - left
    fun getHeight(): Double = bottom - top
    override fun toString(): String = "Rect($left, $top, $right, $bottom)"
}

// ========== 颜色工具 ==========

/** 颜色转换工具：Double/Long → Color(Int) */
object ColorUtil {
    fun toColor(value: Any?): Color = when (value) {
        is LuaColor -> value.toComposeColor()
        is Long -> Color(value.toInt())
        is Double -> Color(value.toLong().toInt())
        is Int -> Color(value)
        is Number -> Color(value.toInt())
        else -> Color.Black
    }
}