package com.nirithy.luacompose.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import kotlinx.coroutines.delay
import com.luajava.LuaObject
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.render.ComposeRenderer

/**
 * Compose 副作用支持：LaunchedEffect / key / DisposableEffect
 *
 * Lua 用法：
 *   -- LaunchedEffect: 在 Compose 生命周期中启动协程
 *   compose.LaunchedEffect {
 *     key = count.value,          -- 当 key 变化时重新执行
 *     block = function()
 *       -- 协程体
 *     end,
 *   }
 *
 *   -- key: 按 key 分组复用组合
 *   compose.key {
 *     key = item.id,
 *     children = { ... },
 *   }
 *
 *   -- DisposableEffect: 带清理的副作用
 *   compose.DisposableEffect {
 *     key = someValue,
 *     effect = function()
 *       -- 设置
 *       return function()
 *         -- 清理
 *       end
 *     end,
 *   }
 */

/** LaunchedEffect 渲染器 */
@Composable
fun LaunchedEffectRenderer(node: ComposeNode) {
    val key = node.props["key"]
    // ★ 修复：block 是函数，NodeParser 将其存入 callbacks，而非 props
    val block = node.callbacks["block"]

    LaunchedEffect(key) {
        try {
            block?.call()
        } catch (e: Exception) {
            // 协程中的异常不向上传播
        }
    }

    // 渲染 children（如果有的话，用于显示状态文本等）
    ComposeRenderer.RenderChildren(node)
}

/** key 渲染器：按 key 分组复用 */
@Composable
fun KeyRenderer(node: ComposeNode) {
    val keyVal = node.props["key"] ?: Unit
    key(keyVal) {
        ComposeRenderer.RenderChildren(node)
    }
}

/** DisposableEffect 渲染器 */
@Composable
fun DisposableEffectRenderer(node: ComposeNode) {
    val key = node.props["key"]
    // ★ 修复：effect 是函数，NodeParser 将其存入 callbacks，而非 props
    val effect = node.callbacks["effect"]

    DisposableEffect(key) {
        val onDisposeFn: Any? = if (effect != null) {
            try { effect.call() } catch (_: Exception) { null }
        } else null

        onDispose {
            try {
                val fn = onDisposeFn
                if (fn is LuaObject && fn.isFunction()) {
                    fn.call()
                }
            } catch (_: Exception) { }
        }
    }

    // 渲染 children（如果有的话，用于显示状态文本等）
    ComposeRenderer.RenderChildren(node)
}