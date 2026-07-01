package com.nirithy.luacompose.graphics

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape

/**
 * Lua 可用的 Shape 包装器
 * 支持 RoundedCornerShape 和 CircleShape
 */
class LuaShape private constructor(private val shape: Shape) {
    fun toComposeShape(): Shape = shape

    companion object {
        /** 创建圆角矩形形状 */
        fun rounded(cornerRadius: Double): LuaShape {
            val dp = cornerRadius.toFloat()
            return LuaShape(RoundedCornerShape(dp))
        }

        /** 创建圆形形状 */
        fun circle(): LuaShape = LuaShape(CircleShape)
    }

    override fun toString(): String = "LuaShape(${shape})"
}

/**
 * Lua 可用的 Brush 包装器
 * 支持 radialGradient
 */
class LuaBrush(
    val type: String,
    val centerX: Double = 0.5,
    val centerY: Double = 0.5,
    val radius: Double = 1.0,
    val colors: List<Long> = emptyList()
) {
    fun toComposeBrush(): androidx.compose.ui.graphics.Brush {
        return when (type) {
            "radialGradient" -> {
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = colors.map { androidx.compose.ui.graphics.Color(it.toInt()) },
                    center = androidx.compose.ui.geometry.Offset(centerX.toFloat(), centerY.toFloat()),
                    radius = radius.toFloat()
                )
            }
            else -> androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(androidx.compose.ui.graphics.Color.White)
            )
        }
    }

    override fun toString(): String = "LuaBrush($type)"
}