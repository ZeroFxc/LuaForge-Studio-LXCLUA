package com.nirithy.luacompose.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.RepeatableSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import com.nirithy.luacompose.bridge.ComposeBridge
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.render.ComposeRenderer

/**
 * 动画规格工具类
 * 提供 Lua 端可用的动画规格构造函数和进出场动画
 *
 * 参考 LuaCompose-master 的 AnimationPlugin 设计
 */
object AnimationSpecs {

    // ========== 动画规格 ==========

    /** 创建 tween 动画规格 */
    fun createTween(durationMs: Int = 300, delayMs: Int = 0): DurationBasedAnimationSpec<Float> {
        return tween(durationMillis = durationMs, delayMillis = delayMs)
    }

    /** 创建 spring 动画规格 */
    fun createSpring(dampingRatio: Float = Spring.DampingRatioMediumBouncy, stiffness: Float = Spring.StiffnessMedium): FiniteAnimationSpec<Float> {
        return spring(dampingRatio = dampingRatio, stiffness = stiffness)
    }

    /** 创建 repeatable 动画规格 */
    fun createRepeatable(
        iterations: Int,
        animation: DurationBasedAnimationSpec<Float>,
        repeatMode: RepeatMode = RepeatMode.Restart
    ): RepeatableSpec<Float> {
        return androidx.compose.animation.core.repeatable(iterations, animation, repeatMode)
    }

    /** 创建 infiniteRepeatable 动画规格 */
    fun createInfiniteRepeatable(
        animation: DurationBasedAnimationSpec<Float>,
        repeatMode: RepeatMode = RepeatMode.Restart
    ): InfiniteRepeatableSpec<Float> {
        return androidx.compose.animation.core.infiniteRepeatable(animation, repeatMode)
    }

    // ========== 进出场动画 ==========

    /** 入场动画：淡入 */
    fun fadeInEnter(): EnterTransition = fadeIn()

    /** 出场动画：淡出 */
    fun fadeOutExit(): ExitTransition = fadeOut()

    /** 入场动画：展开(垂直) */
    fun expandVerticallyEnter(): EnterTransition = expandVertically()

    /** 出场动画：收缩(垂直) */
    fun shrinkVerticallyExit(): ExitTransition = shrinkVertically()

    /** 入场动画：展开(水平) */
    fun expandHorizontallyEnter(): EnterTransition = expandHorizontally()

    /** 出场动画：收缩(水平) */
    fun shrinkHorizontallyExit(): ExitTransition = shrinkHorizontally()

    /** 入场动画：水平滑入（从屏幕右侧外滑入） */
    fun slideInHorizontallyEnter(): EnterTransition = slideInHorizontally(initialOffsetX = { it })

    /** 出场动画：水平滑出（滑出到屏幕右侧外） */
    fun slideOutHorizontallyExit(): ExitTransition = slideOutHorizontally(targetOffsetX = { it })

    /** 入场动画：垂直滑入（从屏幕底部外滑入） */
    fun slideInVerticallyEnter(): EnterTransition = slideInVertically(initialOffsetY = { it })

    /** 出场动画：垂直滑出（滑出到屏幕底部外） */
    fun slideOutVerticallyExit(): ExitTransition = slideOutVertically(targetOffsetY = { it })

    /** 入场动画：缩放进入 */
    fun scaleInEnter(): EnterTransition = scaleIn()

    /** 出场动画：缩放退出 */
    fun scaleOutExit(): ExitTransition = scaleOut()

    /** 组合：淡入+展开 */
    fun fadeInExpandEnter(): EnterTransition = fadeIn() + expandVertically()

    /** 组合：淡出+收缩 */
    fun fadeOutShrinkExit(): ExitTransition = fadeOut() + shrinkVertically()

    /** 组合：淡入+滑入 */
    fun fadeInSlideEnter(): EnterTransition = fadeIn() + slideInHorizontally()

    /** 组合：淡出+滑出 */
    fun fadeOutSlideExit(): ExitTransition = fadeOut() + slideOutHorizontally()

    /** 组合：淡入+缩放进入 */
    fun fadeInScaleEnter(): EnterTransition = fadeIn() + scaleIn()

    /** 组合：淡出+缩放退出 */
    fun fadeOutScaleExit(): ExitTransition = fadeOut() + scaleOut()
}

/** 支持 enter/exit 动画的可见性切换 */
@Composable
fun AnimatedVisibilityRenderer(node: ComposeNode) {
    val visible = node.boolProp("visible", true)
    val enter = node.props["enter"] as? EnterTransition ?: fadeIn()
    val exit = node.props["exit"] as? ExitTransition ?: fadeOut()

    AnimatedVisibility(
        visible = visible,
        modifier = ComposeRenderer.resolveModifier(node),
        enter = enter,
        exit = exit
    ) {
        // this = AnimatedVisibilityScope，推入栈供 sharedElement 使用
        ComposeBridge.pushActiveAnimatedVisibilityScope(this)
        // RenderChildren是Composable不能被try-catch包裹，pop直接在其后执行
        // 若RenderChildren抛异常，pop可能不执行——但Compose异常会终止重组，影响有限
        ComposeRenderer.RenderChildren(node)
        ComposeBridge.popActiveAnimatedVisibilityScope()
    }
}