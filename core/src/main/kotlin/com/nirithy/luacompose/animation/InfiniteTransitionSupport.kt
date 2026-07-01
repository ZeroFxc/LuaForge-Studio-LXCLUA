package com.nirithy.luacompose.animation

import com.nirithy.luacompose.logE
import com.nirithy.luacompose.logW
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.render.ComposeRenderer

private const val TAG = "InfiniteTransition"

/**
 * InfiniteTransition 无限循环动画支持
 *
 * 设计思路：Lua 函数通过 luajava 调用时无法保持 @Composable 上下文，
 * 因此不能直接在 Lua 中调用 transition.animateFloat()（它是 @Composable 函数）。
 *
 * 解决方案：
 * 1. Lua 端通过 props 传入动画参数（initialValue, targetValue, durationMs）
 * 2. Kotlin 端在 @Composable 上下文中创建动画
 * 3. 将 animatedValue 作为参数传给 Lua 的 children 函数
 * 4. Lua 函数返回 ComposeNode 树，Kotlin 端渲染
 *
 * Lua 用法：
 *   compose.InfiniteTransition {
 *     initialValue = 0,
 *     targetValue = 1,
 *     durationMs = 1000,
 *     children = function(animValue)
 *       -- animValue 是 float，每帧更新
 *       return compose.Text { fontSize = 80 * (1 + animValue * 0.3) }
 *     end,
 *   }
 */

/** 动画浮点值包装器，暴露 .value 给 Lua */
class LuaAnimatedFloatValue(private val state: State<Float>) {
    fun getValue(): Float = state.value
}

/** InfiniteTransition 组件渲染器 */
@Composable
fun InfiniteTransitionRenderer(node: ComposeNode) {
    val transition = rememberInfiniteTransition(label = "LuaInfiniteTransition")

    // 从 props 读取动画参数（由 Lua 在表构造时传入）
    val initialValue = (node.props["initialValue"] as? Number)?.toFloat() ?: 0f
    val targetValue = (node.props["targetValue"] as? Number)?.toFloat() ?: 1f
    val durationMs = (node.props["durationMs"] as? Number)?.toInt() ?: 1000
    val repeatMode = when (node.props["repeatMode"] as? String) {
        "Restart" -> RepeatMode.Restart
        else -> RepeatMode.Reverse
    }

    // 在 @Composable 上下文中创建动画（这里不会有 luajava 调用问题）
    val animValue by transition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs),
            repeatMode = repeatMode
        ),
        label = "LuaInfiniteFloat"
    )

    val childrenFn = node.childrenFunc
    if (childrenFn != null) {
        // 将 animatedValue 作为 float 参数传给 Lua 函数
        // try-catch 只包裹 luajava 调用（非 @Composable），Compose 渲染放在外部
        val result: Any? = try {
            childrenFn.call(animValue)
        } catch (e: Exception) {
            logE(TAG) { "children 函数调用失败: ${e.message}" }
            null
        }
        if (result is ComposeNode) {
            ComposeRenderer.RenderNode(result)
        } else if (result != null) {
            logW(TAG) { "children 函数返回了非 ComposeNode 类型: ${result.javaClass.name}" }
        }
    } else {
        ComposeRenderer.RenderChildren(node)
    }
}