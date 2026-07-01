package com.nirithy.luacompose.animation

import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin

/**
 * 动画组件插件
 * 整合 AnimatedVisibility、AnimatedContent、Crossfade、InfiniteTransition、SharedTransitionLayout 的注册
 *
 * 原版对应 AnimationPlugin + TransitionComponents
 */
object AnimationPlugin : ComposePlugin {
    override val namespace = "animation"

    override fun getComponents() = mapOf<String, @androidx.compose.runtime.Composable (ComposeNode) -> Unit>(
        "AnimatedVisibility" to { node -> AnimatedVisibilityRenderer(node) },
        "AnimatedContent" to { node -> AnimatedContentRenderer(node) },
        "Crossfade" to { node -> CrossfadeRenderer(node) },
        "InfiniteTransition" to { node -> InfiniteTransitionRenderer(node) },
        "SharedTransitionLayout" to { node -> SharedTransitionComponents.SharedTransitionLayoutRenderer(node) },
    )
}