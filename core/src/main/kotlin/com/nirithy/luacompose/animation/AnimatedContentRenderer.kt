package com.nirithy.luacompose.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import com.nirithy.luacompose.bridge.ComposeBridge
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.render.ComposeRenderer
import com.nirithy.luacompose.logE
import com.nirithy.luacompose.logD

/**
 * AnimatedContent 组件渲染器
 * 当 targetState 变化时，使用指定动画过渡切换内容
 *
 * Lua 用法：
 *   compose.AnimatedContent {
 *     targetState = count.value,
 *     enter = compose.slideInHorizontally(),
 *     exit = compose.slideOutHorizontally(),
 *     children = {
 *       compose.Text { text = "当前值: " .. tostring(count.value) }
 *     },
 *   }
 */
@Composable
fun AnimatedContentRenderer(node: ComposeNode) {
    // 允许 targetState 为 null，因为初始状态可以是 nil（列表/详情切换场景）
    val targetState = node.props["targetState"]

    val durationMs = (node.props["durationMs"] as? Number)?.toInt() ?: 300
    val transitionSpecCallback = node.callbacks["transitionSpec"]

    val transitionSpec: AnimatedContentTransitionScope<Any?>.() -> ContentTransform = {
        if (transitionSpecCallback != null) {
            try {
                val result = transitionSpecCallback.call(initialState, targetState)
                val dir = result as? String ?: "right"
                when (dir) {
                    "up" -> ContentTransform(
                        targetContentEnter = slideInVertically { it } + fadeIn(),
                        initialContentExit = slideOutVertically { -it } + fadeOut(),
                        sizeTransform = SizeTransform(clip = false) { _, _ -> tween(durationMillis = durationMs) }
                    )
                    "down" -> ContentTransform(
                        targetContentEnter = slideInVertically { -it } + fadeIn(),
                        initialContentExit = slideOutVertically { it } + fadeOut(),
                        sizeTransform = SizeTransform(clip = false) { _, _ -> tween(durationMillis = durationMs) }
                    )
                    else -> ContentTransform(
                        targetContentEnter = fadeIn() + slideInHorizontally(),
                        initialContentExit = fadeOut() + slideOutHorizontally(),
                        sizeTransform = SizeTransform(clip = false) { _, _ -> tween(durationMillis = durationMs) }
                    )
                }
            } catch (e: Exception) {
                logE("AnimatedContent") { "transitionSpec 回调失败: ${e.message}" }
                ContentTransform(
                    targetContentEnter = fadeIn() + slideInHorizontally(),
                    initialContentExit = fadeOut() + slideOutHorizontally(),
                    sizeTransform = SizeTransform(clip = false) { _, _ -> tween(durationMillis = durationMs) }
                )
            }
        } else {
            val enter = node.props["enter"] as? EnterTransition ?: fadeIn() + slideInHorizontally()
            val exit = node.props["exit"] as? ExitTransition ?: fadeOut() + slideOutHorizontally()
            ContentTransform(
                targetContentEnter = enter,
                initialContentExit = exit,
                sizeTransform = SizeTransform(clip = false) { _, _ -> tween(durationMillis = durationMs) }
            )
        }
    }

    AnimatedContent(
        targetState = targetState,
        modifier = ComposeRenderer.resolveModifier(node),
        transitionSpec = transitionSpec,
        label = "AnimatedContent"
    ) { target ->
        // this = AnimatedVisibilityScope，推入栈供 sharedElement 使用
        ComposeBridge.pushActiveAnimatedVisibilityScope(this as AnimatedVisibilityScope)
        var error: Exception? = null
        var result: ComposeNode? = null
        val childrenFn = node.childrenFunc
        if (childrenFn != null) {
            result = try {
                childrenFn.call(target) as? ComposeNode
            } catch (e: Exception) {
                error = e
                null
            }
        }
        if (error != null) {
            logE("AnimatedContent") { "children 回调失败: ${error.message}" }
        }
        if (result != null) {
            logD("AnimatedContent") { "渲染动态子节点: target=$target, type=${result.type}" }
            ComposeRenderer.RenderNode(result)
        } else if (childrenFn == null) {
            ComposeRenderer.RenderChildren(node)
        }
        ComposeBridge.popActiveAnimatedVisibilityScope()
    }
}