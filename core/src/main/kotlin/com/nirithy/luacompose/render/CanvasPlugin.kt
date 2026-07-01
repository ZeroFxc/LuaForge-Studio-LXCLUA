package com.nirithy.luacompose.render

import com.nirithy.luacompose.logW
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nirithy.luacompose.draw.DrawScopeWrapper
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin

/**
 * Canvas 绘图组件插件
 * 提供 DrawScope 给 Lua 进行自定义绘制，支持 continuousRedraw 模式每帧重绘
 *
 * Lua 用法：
 *   compose.Canvas {
 *     modifier = compose.Modifier().size(200),
 *     continuousRedraw = true,
 *     onDraw = function(draw, w, h, timeSec)
 *       -- timeSec: 自首次渲染以来的秒数（高精度），用于动画驱动
 *       draw.drawCircle(100, 100, 50, 0xFF6200EE)
 *     end,
 *   }
 */
object CanvasPlugin : ComposePlugin {
    override val namespace = "draw"

    override fun getComponents() = mapOf<String, @Composable (ComposeNode) -> Unit>(
        "Canvas" to { node -> CanvasRenderer(node) },
    )
}

@Composable
private fun CanvasRenderer(node: ComposeNode) {
    val onDraw = node.callbacks["onDraw"] ?: return
    val continuousRedraw = (node.props["continuousRedraw"] as? Boolean) ?: false

    // continuousRedraw 模式：每帧递增 frame，触发 Canvas 重绘
    val frame = remember { mutableStateOf(0L) }
    val startNanos = remember { mutableStateOf(0L) }
    if (continuousRedraw) {
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { nanos ->
                    if (startNanos.value == 0L) startNanos.value = nanos
                    frame.value = nanos
                }
            }
        }
    }

    Box(
        modifier = ComposeRenderer.resolveModifier(node),
        contentAlignment = Alignment.TopStart
    ) {
        // 在 Composable 作用域中读取 frame，触发每帧重组
        if (continuousRedraw) {
            @Suppress("UNUSED_EXPRESSION")
            frame.value
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            try {
                // ★ 将帧时间（秒）传给 Lua，替代不可靠的 os.clock()
                val timeSec = if (startNanos.value > 0L) {
                    (frame.value - startNanos.value) / 1_000_000_000.0
                } else 0.0
                onDraw.call(DrawScopeWrapper(this), size.width.toDouble(), size.height.toDouble(), timeSec)
            } catch (e: Exception) {
                logW("CanvasPlugin") { "[onDraw] 回调失败: ${e.message}" }
            }
        }
    }
}