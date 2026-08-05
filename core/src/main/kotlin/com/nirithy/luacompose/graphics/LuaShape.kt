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
    val colors: List<Long> = emptyList(),
    val colorStops: List<Float>? = null,  // 颜色停止位置，与 colors 一一对应
    val startX: Double = 0.0,
    val startY: Double = 0.0,
    val endX: Double = 0.0,
    val endY: Double = 1.0,
    val tileMode: String = "Clamp"
) {
    fun toComposeBrush(): androidx.compose.ui.graphics.Brush {
        return when (type) {
            "radialGradient" -> {
                if (colorStops != null && colorStops.isNotEmpty() && colors.size == colorStops.size) {
                    // 带 color stops 的渐变
                    val stops = colors.mapIndexed { i, c ->
                        colorStops[i] to androidx.compose.ui.graphics.Color(c.toInt())
                    }.toTypedArray()
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colorStops = stops,
                        center = androidx.compose.ui.geometry.Offset(centerX.toFloat(), centerY.toFloat()),
                        radius = radius.toFloat()
                    )
                } else {
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = colors.map { androidx.compose.ui.graphics.Color(it.toInt()) },
                        center = androidx.compose.ui.geometry.Offset(centerX.toFloat(), centerY.toFloat()),
                        radius = radius.toFloat()
                    )
                }
            }
            "verticalGradient" -> {
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = colors.map { androidx.compose.ui.graphics.Color(it.toInt()) },
                    startY = startY.toFloat(),
                    endY = endY.toFloat()
                )
            }
            "linearGradient" -> {
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = colors.map { androidx.compose.ui.graphics.Color(it.toInt()) },
                    start = androidx.compose.ui.geometry.Offset(startX.toFloat(), startY.toFloat()),
                    end = androidx.compose.ui.geometry.Offset(endX.toFloat(), endY.toFloat())
                )
            }
            else -> androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(androidx.compose.ui.graphics.Color.White)
            )
        }
    }

    override fun toString(): String = "LuaBrush($type)"
}