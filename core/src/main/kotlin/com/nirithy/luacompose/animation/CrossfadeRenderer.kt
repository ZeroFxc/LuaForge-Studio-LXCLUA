package com.nirithy.luacompose.animation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.render.ComposeRenderer

/**
 * Crossfade 组件渲染器
 * 当 targetState 变化时，使用淡入淡出动画切换内容。
 * 比 AnimatedContent 更轻量，不需要指定 enter/exit 动画。
 *
 * Lua 用法：
 *   compose.Crossfade {
 *     targetState = currentPage,
 *     durationMs = 400,
 *     children = {
 *       compose.Text { text = "页面 " .. tostring(currentPage) }
 *     },
 *   }
 */
@Composable
fun CrossfadeRenderer(node: ComposeNode) {
    val targetState = node.props["targetState"] ?: return
    val durationMs = (node.props["durationMs"] as? Number)?.toInt() ?: 300

    Crossfade(
        targetState = targetState,
        modifier = ComposeRenderer.resolveModifier(node),
        animationSpec = tween(durationMillis = durationMs),
        label = "Crossfade"
    ) {
        ComposeRenderer.RenderChildren(node)
    }
}