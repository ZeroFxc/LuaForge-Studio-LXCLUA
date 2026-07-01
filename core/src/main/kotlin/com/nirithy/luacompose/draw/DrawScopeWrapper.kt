package com.nirithy.luacompose.draw

import com.nirithy.luacompose.logW
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.luajava.LuaObject

private const val TAG = "DrawScope"

/**
 * DrawScope 包装器，将 Compose 绘制 API 暴露给 Lua
 *
 * 注意：不使用 @JvmOverloads，因为 luajava 对合成方法解析不稳定。
 * 所有可选参数使用 Number? 类型，null 表示默认值。
 *
 * Lua 用法：
 *   compose.Canvas { modifier = ...; onDraw = function(draw)
 *     draw:drawCircle(60, 60, 40, compose.Theme.primary)
 *     draw:drawRect(10, 10, 200, 80, 0xFF0000FF)
 *     draw:drawLine(0, 0, 100, 100, 0xFFFF0000, 2.0)
 *     draw:rotate(45, 100, 100)
 *   end }
 */
class DrawScopeWrapper(private val drawScope: DrawScope) {

    /** 将 Lua 传入的 Number 转为 Compose Color */
    private fun toColor(value: Any?): Color = when (value) {
        is Long -> Color(value.toInt())
        is Double -> Color(value.toLong().toInt())
        is Int -> Color(value)
        is Number -> Color(value.toInt())
        else -> Color.Black
    }

    // ========== 绘制矩形（5参数: left, top, right, bottom, color）==========
    fun drawRect(left: Double, top: Double, right: Double, bottom: Double, color: Any) {
        drawScope.drawRect(
            color = toColor(color),
            topLeft = Offset(left.toFloat(), top.toFloat()),
            size = androidx.compose.ui.geometry.Size((right - left).toFloat(), (bottom - top).toFloat())
        )
    }

    // ========== 绘制描边矩形（6参数: left, top, right, bottom, color, strokeWidth）==========
    fun drawRectStroke(left: Double, top: Double, right: Double, bottom: Double, color: Any, strokeWidth: Double) {
        drawScope.drawRect(
            color = toColor(color),
            topLeft = Offset(left.toFloat(), top.toFloat()),
            size = androidx.compose.ui.geometry.Size((right - left).toFloat(), (bottom - top).toFloat()),
            style = Stroke(width = strokeWidth.toFloat())
        )
    }

    // ========== 绘制圆角矩形（6参数）==========
    fun drawRoundRect(left: Double, top: Double, right: Double, bottom: Double, cornerRadius: Double, color: Any) {
        drawScope.drawRoundRect(
            color = toColor(color),
            topLeft = Offset(left.toFloat(), top.toFloat()),
            size = androidx.compose.ui.geometry.Size((right - left).toFloat(), (bottom - top).toFloat()),
            cornerRadius = CornerRadius(cornerRadius.toFloat()),
            style = Fill
        )
    }

    // ========== 绘制圆角矩形+描边（7参数）==========
    fun drawRoundRectStroke(left: Double, top: Double, right: Double, bottom: Double, cornerRadius: Double, color: Any, strokeWidth: Double) {
        drawScope.drawRoundRect(
            color = toColor(color),
            topLeft = Offset(left.toFloat(), top.toFloat()),
            size = androidx.compose.ui.geometry.Size((right - left).toFloat(), (bottom - top).toFloat()),
            cornerRadius = CornerRadius(cornerRadius.toFloat()),
            style = Stroke(width = strokeWidth.toFloat())
        )
    }

    // ========== 绘制圆形（4参数）==========
    fun drawCircle(centerX: Double, centerY: Double, radius: Double, color: Any) {
        drawScope.drawCircle(
            color = toColor(color),
            radius = radius.toFloat(),
            center = Offset(centerX.toFloat(), centerY.toFloat()),
            style = Fill
        )
    }

    // ========== 绘制描边圆形（5参数）==========
    fun drawCircleStroke(centerX: Double, centerY: Double, radius: Double, color: Any, strokeWidth: Double) {
        drawScope.drawCircle(
            color = toColor(color),
            radius = radius.toFloat(),
            center = Offset(centerX.toFloat(), centerY.toFloat()),
            style = Stroke(width = strokeWidth.toFloat())
        )
    }

    // ========== 绘制直线（5参数）==========
    fun drawLine(startX: Double, startY: Double, endX: Double, endY: Double, color: Any) {
        drawScope.drawLine(
            color = toColor(color),
            start = Offset(startX.toFloat(), startY.toFloat()),
            end = Offset(endX.toFloat(), endY.toFloat()),
            strokeWidth = 1f
        )
    }

    // ========== 绘制直线（6参数：含线宽）==========
    fun drawLine(startX: Double, startY: Double, endX: Double, endY: Double, color: Any, strokeWidth: Double) {
        drawScope.drawLine(
            color = toColor(color),
            start = Offset(startX.toFloat(), startY.toFloat()),
            end = Offset(endX.toFloat(), endY.toFloat()),
            strokeWidth = strokeWidth.toFloat()
        )
    }

    // ========== 绘制弧线（7参数）==========
    fun drawArc(left: Double, top: Double, right: Double, bottom: Double, startAngle: Double, sweepAngle: Double, color: Any) {
        drawScope.drawArc(
            color = toColor(color),
            startAngle = startAngle.toFloat(),
            sweepAngle = sweepAngle.toFloat(),
            useCenter = true,
            topLeft = Offset(left.toFloat(), top.toFloat()),
            size = androidx.compose.ui.geometry.Size((right - left).toFloat(), (bottom - top).toFloat()),
            style = Fill
        )
    }

    // ========== 绘制描边弧线（8参数）==========
    fun drawArcStroke(left: Double, top: Double, right: Double, bottom: Double, startAngle: Double, sweepAngle: Double, color: Any, strokeWidth: Double) {
        drawScope.drawArc(
            color = toColor(color),
            startAngle = startAngle.toFloat(),
            sweepAngle = sweepAngle.toFloat(),
            useCenter = false,
            topLeft = Offset(left.toFloat(), top.toFloat()),
            size = androidx.compose.ui.geometry.Size((right - left).toFloat(), (bottom - top).toFloat()),
            style = Stroke(width = strokeWidth.toFloat())
        )
    }

    // ========== 绘制 Path ==========
    fun drawPath(luaPath: Any, color: Any) {
        try {
            val javaPath = when (luaPath) {
                is LuaPath -> luaPath.path
                is LuaObject -> luaPath.call("getJavaPath")?.let { if (it is Path) it else null }
                else -> null
            }
            if (javaPath != null) {
                drawScope.drawPath(path = javaPath, color = toColor(color), style = Fill)
            }
        } catch (e: Exception) {
            logW(TAG) { "[drawPath] 失败: ${e.message}" }
        }
    }

    fun drawPathStroke(luaPath: Any, color: Any, strokeWidth: Double) {
        try {
            val javaPath = when (luaPath) {
                is LuaPath -> luaPath.path
                is LuaObject -> luaPath.call("getJavaPath")?.let { if (it is Path) it else null }
                else -> null
            }
            if (javaPath != null) {
                drawScope.drawPath(path = javaPath, color = toColor(color), style = Stroke(width = strokeWidth.toFloat()))
            }
        } catch (e: Exception) {
            logW(TAG) { "[drawPath] 失败: ${e.message}" }
        }
    }

    // ========== 尺寸信息 ==========
    fun getWidth(): Double = drawScope.size.width.toDouble()
    fun getHeight(): Double = drawScope.size.height.toDouble()
}

/**
 * Lua Path 对象，支持 moveTo / lineTo / close / addOval / addRect
 */
class LuaPath {
    val path = Path()

    fun moveTo(x: Double, y: Double) { path.moveTo(x.toFloat(), y.toFloat()) }
    fun lineTo(x: Double, y: Double) { path.lineTo(x.toFloat(), y.toFloat()) }
    fun close() { path.close() }
    fun reset() { path.reset() }
    fun addOval(left: Double, top: Double, right: Double, bottom: Double) {
        path.addOval(Rect(Offset(left.toFloat(), top.toFloat()), Offset(right.toFloat(), bottom.toFloat())))
    }
    fun addRect(left: Double, top: Double, right: Double, bottom: Double) {
        path.addRect(Rect(Offset(left.toFloat(), top.toFloat()), Offset(right.toFloat(), bottom.toFloat())))
    }
    /** 供 DrawScopeWrapper 内部使用 */
    fun getJavaPath(): Path = path
}