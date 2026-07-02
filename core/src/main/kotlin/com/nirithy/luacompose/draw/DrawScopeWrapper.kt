package com.nirithy.luacompose.draw

import com.nirithy.luacompose.logW
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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

    /** 将 Color 转为 Android ARGB Int */
    private fun Color.toAndroidColor(): Int {
        val a = (this.alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        val r = (this.red * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (this.green * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (this.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
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

    // ========== 绘制直线（7参数：含线宽+cap）==========
    fun drawLine(startX: Double, startY: Double, endX: Double, endY: Double, color: Any, strokeWidth: Double, cap: String) {
        drawScope.drawLine(
            color = toColor(color),
            start = Offset(startX.toFloat(), startY.toFloat()),
            end = Offset(endX.toFloat(), endY.toFloat()),
            strokeWidth = strokeWidth.toFloat(),
            cap = toStrokeCap(cap)
        )
    }

    // ========== 绘制直线（带渐变Brush）==========
    fun drawLine(startX: Double, startY: Double, endX: Double, endY: Double, color1: Any, color2: Any, strokeWidth: Double) {
        drawScope.drawLine(
            brush = Brush.linearGradient(
                colors = listOf(toColor(color1), toColor(color2)),
                start = Offset(startX.toFloat(), startY.toFloat()),
                end = Offset(endX.toFloat(), endY.toFloat())
            ),
            start = Offset(startX.toFloat(), startY.toFloat()),
            end = Offset(endX.toFloat(), endY.toFloat()),
            strokeWidth = strokeWidth.toFloat(),
            cap = toStrokeCap("round")
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

    // ========== 绘制描边弧线（9参数：含cap）==========
    fun drawArcStroke(left: Double, top: Double, right: Double, bottom: Double, startAngle: Double, sweepAngle: Double, color: Any, strokeWidth: Double, cap: String) {
        drawScope.drawArc(
            color = toColor(color),
            startAngle = startAngle.toFloat(),
            sweepAngle = sweepAngle.toFloat(),
            useCenter = false,
            topLeft = Offset(left.toFloat(), top.toFloat()),
            size = androidx.compose.ui.geometry.Size((right - left).toFloat(), (bottom - top).toFloat()),
            style = Stroke(width = strokeWidth.toFloat(), cap = toStrokeCap(cap))
        )
    }

    // ========== 绘制矩形（带垂直渐变Brush）==========
    fun drawRectVerticalGradient(left: Double, top: Double, right: Double, bottom: Double, color1: Any, color2: Any) {
        drawScope.drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(toColor(color1), toColor(color2)),
                startY = top.toFloat(),
                endY = bottom.toFloat()
            ),
            topLeft = Offset(left.toFloat(), top.toFloat()),
            size = androidx.compose.ui.geometry.Size((right - left).toFloat(), (bottom - top).toFloat())
        )
    }
    /** Number 重载 */
    fun drawRectVerticalGradient(left: Number, top: Number, right: Number, bottom: Number, color1: Any, color2: Any) {
        drawRectVerticalGradient(left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble(), color1, color2)
    }

    // ========== 绘制圆形（带径向渐变Brush）==========
    fun drawCircleRadial(centerX: Double, centerY: Double, radius: Double, color1: Any, color2: Any) {
        drawScope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(toColor(color1), toColor(color2)),
                center = Offset(centerX.toFloat(), centerY.toFloat()),
                radius = radius.toFloat()
            ),
            radius = radius.toFloat(),
            center = Offset(centerX.toFloat(), centerY.toFloat())
        )
    }

    /** 解析 StrokeCap 字符串 */
    private fun toStrokeCap(cap: String): StrokeCap = when (cap.lowercase()) {
        "round" -> StrokeCap.Round
        "square" -> StrokeCap.Square
        else -> StrokeCap.Butt
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

    // ========== 状态保存/恢复 ==========

    /** 保存当前画布状态（matrix + clip），用于变换后恢复 */
    fun save() {
        drawScope.drawContext.canvas.save()
    }

    /** 恢复最近一次 save 的画布状态 */
    fun restore() {
        drawScope.drawContext.canvas.restore()
    }

    // ========== 变换 ==========

    /** 画布旋转（围绕原点），degrees 为角度，使用 drawContext.transform 持久旋转 */
    fun rotate(degrees: Double) {
        drawScope.drawContext.transform.rotate(degrees.toFloat())
    }

    /** 画布旋转（围绕原点），接受 Number 类型，兼容 luajava 传递的 Java Float/Int 等 */
    fun rotate(degrees: Number) {
        drawScope.drawContext.transform.rotate(degrees.toFloat())
    }

    /** 画布旋转（围绕指定中心点），degrees 为角度，使用 translate-rotate-translate 实现绕点旋转 */
    fun rotate(degrees: Double, pivotX: Double, pivotY: Double) {
        drawScope.drawContext.transform.translate(pivotX.toFloat(), pivotY.toFloat())
        drawScope.drawContext.transform.rotate(degrees.toFloat())
        drawScope.drawContext.transform.translate(-pivotX.toFloat(), -pivotY.toFloat())
    }

    /** 画布旋转（围绕指定中心点），接受 Number 类型，兼容 luajava 传递的 Java Float/Int 等 */
    fun rotate(degrees: Number, pivotX: Double, pivotY: Double) {
        drawScope.drawContext.transform.translate(pivotX.toFloat(), pivotY.toFloat())
        drawScope.drawContext.transform.rotate(degrees.toFloat())
        drawScope.drawContext.transform.translate(-pivotX.toFloat(), -pivotY.toFloat())
    }

    /** 画布旋转（围绕指定中心点），全 Number 参数版本，兼容 luajava */
    fun rotate(degrees: Number, pivotX: Number, pivotY: Number) {
        drawScope.drawContext.transform.translate(pivotX.toFloat(), pivotY.toFloat())
        drawScope.drawContext.transform.rotate(degrees.toFloat())
        drawScope.drawContext.transform.translate(-pivotX.toFloat(), -pivotY.toFloat())
    }

    // ========== 文本绘制 ==========

    /** 使用 Android nativeCanvas 绘制文本 (x, y, text, color, fontSize) */
    fun drawText(x: Double, y: Double, text: String, color: Any, fontSize: Double) {
        drawCoreText(x, y, text, color, fontSize, "left")
    }

    /** Lua 常用顺序: drawText(text, x, y, color, fontSize) */
    fun drawText(text: String, x: Double, y: Double, color: Any, fontSize: Double) {
        drawCoreText(x, y, text, color, fontSize, "left")
    }

    /** Lua 常用顺序: drawText(text, x, y, color, fontSize, align) */
    fun drawText(text: String, x: Double, y: Double, color: Any, fontSize: Double, align: String) {
        drawCoreText(x, y, text, color, fontSize, align)
    }

    /** 绘制文本 (x, y, text, color, fontSize, align) — align: "left"/"center"/"right" */
    fun drawText(x: Double, y: Double, text: String, color: Any, fontSize: Double, align: String) {
        drawCoreText(x, y, text, color, fontSize, align)
    }

    private fun drawCoreText(x: Double, y: Double, text: String, color: Any, fontSize: Double, align: String) {
        val paint = android.graphics.Paint().apply {
            this.color = toColor(color).toAndroidColor()
            this.textSize = fontSize.toFloat()
            this.isAntiAlias = true
            this.textAlign = when (align) {
                "center" -> android.graphics.Paint.Align.CENTER
                "right" -> android.graphics.Paint.Align.RIGHT
                else -> android.graphics.Paint.Align.LEFT
            }
        }
        drawScope.drawContext.canvas.nativeCanvas.drawText(text, x.toFloat(), y.toFloat(), paint)
    }

    // ========== 椭圆绘制 ==========

    /** 绘制椭圆填充 (left, top, right, bottom, color) */
    fun drawOval(left: Double, top: Double, right: Double, bottom: Double, color: Any) {
        drawScope.drawOval(
            color = toColor(color),
            topLeft = Offset(left.toFloat(), top.toFloat()),
            size = androidx.compose.ui.geometry.Size((right - left).toFloat(), (bottom - top).toFloat())
        )
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
    /** Number 重载，兼容 luajava 传递的 Java Float/Int/Long */
    fun moveTo(x: Number, y: Number) { path.moveTo(x.toFloat(), y.toFloat()) }
    fun lineTo(x: Number, y: Number) { path.lineTo(x.toFloat(), y.toFloat()) }
    fun addOval(left: Number, top: Number, right: Number, bottom: Number) {
        path.addOval(Rect(Offset(left.toFloat(), top.toFloat()), Offset(right.toFloat(), bottom.toFloat())))
    }
    fun addRect(left: Number, top: Number, right: Number, bottom: Number) {
        path.addRect(Rect(Offset(left.toFloat(), top.toFloat()), Offset(right.toFloat(), bottom.toFloat())))
    }
    /** 供 DrawScopeWrapper 内部使用 */
    fun getJavaPath(): Path = path
}