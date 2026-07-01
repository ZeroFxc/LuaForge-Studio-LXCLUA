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
    val block = node.props["block"] as? LuaObject

    LaunchedEffect(key) {
        try {
            block?.call()
        } catch (e: Exception) {
            // 协程中的异常不向上传播
        }
    }
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
    val effectFn = node.props["effect"] as? LuaObject

    DisposableEffect(key) {
        var onDispose: LuaObject? = null
        try {
            val result = effectFn?.call()
            if (result is LuaObject) {
                onDispose = result
            }
        } catch (_: Exception) { }

        onDispose {
            try {
                onDispose?.call()
            } catch (_: Exception) { }
        }
    }
}