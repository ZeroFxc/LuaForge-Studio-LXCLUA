package com.nirithy.luacompose.component

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import com.nirithy.luacompose.bridge.ComposeBridge
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.render.ComposeRenderer
import com.luajava.LuaObject

/**
 * LazyListScope 包装器，注入到 Lua 环境，让 Lua 侧直接调用 item()/items() DSL。
 *
 * Lua 用法：
 *   compose.LazyColumn {
 *     children = function(scope)
 *       scope:item(function() return compose.Text({ text = "表头" }) end)
 *       scope:items(100, function(i)
 *         return compose.Text({ text = "第" .. i .. "项" })
 *       end)
 *     end,
 *   }
 */
class LazyListScopeWrapper(private val scope: LazyListScope) {

    /**
     * 添加单个 item
     * @param content Lua 函数，返回 ComposeNode
     */
    fun item(content: LuaObject) {
        scope.item {
            val node = try {
                synchronized(ComposeBridge.luaLock) { content.call() } as? ComposeNode
            } catch (_: Exception) { null }
            if (node != null) {
                ComposeRenderer.RenderNode(node)
            }
        }
    }

    /**
     * 添加单个 item（带 key，用于稳定标识）
     * @param key 唯一标识
     * @param content Lua 函数，返回 ComposeNode
     */
    fun item(key: Any?, content: LuaObject) {
        scope.item(key = key) {
            val node = try {
                synchronized(ComposeBridge.luaLock) { content.call() } as? ComposeNode
            } catch (_: Exception) { null }
            if (node != null) {
                ComposeRenderer.RenderNode(node)
            }
        }
    }

    /**
     * 批量添加 items
     * @param count 数量
     * @param itemContent Lua 函数，接收索引 i（从 1 开始），返回 ComposeNode
     */
    fun items(count: Int, itemContent: LuaObject) {
        scope.items(count) { index ->
            val node = try {
                synchronized(ComposeBridge.luaLock) {
                    itemContent.call((index + 1).toDouble()) // Lua 索引从 1 开始
                } as? ComposeNode
            } catch (_: Exception) { null }
            if (node != null) {
                ComposeRenderer.RenderNode(node)
            }
        }
    }

    /**
     * 批量添加 items（带 key 生成函数）
     * @param count 数量
     * @param key Lua 函数，接收索引，返回 key
     * @param itemContent Lua 函数，接收索引，返回 ComposeNode
     */
    fun items(count: Int, key: LuaObject, itemContent: LuaObject) {
        scope.items(
            count = count,
            key = { index: Int ->
                try {
                    synchronized(ComposeBridge.luaLock) { key.call((index + 1).toDouble()) }
                } catch (_: Exception) { index }
            },
            itemContent = { index: Int ->
                val node = try {
                    synchronized(ComposeBridge.luaLock) {
                        itemContent.call((index + 1).toDouble())
                    } as? ComposeNode
                } catch (_: Exception) { null }
                if (node != null) {
                    ComposeRenderer.RenderNode(node)
                }
            }
        )
    }

    /**
     * 从列表批量添加 items
     * @param list 包含 ComposeNode 的列表
     */
    fun itemsFromList(list: List<*>) {
        val nodes = list.filterIsInstance<ComposeNode>()
        scope.items(nodes) { node ->
            ComposeRenderer.RenderNode(node)
        }
    }
}