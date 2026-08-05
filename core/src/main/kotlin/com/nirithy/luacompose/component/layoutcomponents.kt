package com.nirithy.luacompose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.logE
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer
import com.nirithy.luacompose.render.RenderChildWithAlign
import com.nirithy.luacompose.render.RenderChildWithWeight
import com.luajava.LuaObject

/**
 * 布局组件插件：Column、Row、Box、LazyColumn、LazyRow
 *
 * FlowRow/FlowColumn 已由 KSP 渲染器接管（通过 registerKspShortNames 别名映射）
 */
object LayoutComponents : ComposePlugin {
    override val namespace = "layout"

    override fun getComponents() = mapOf<String, @Composable (ComposeNode) -> Unit>(
        "Column" to { node -> ColumnLayout(node) },
        "Row" to { node -> RowLayout(node) },
        "Box" to { node -> BoxLayout(node) },
        "LazyColumn" to { node -> LazyColumnLayout(node) },
        "LazyRow" to { node -> LazyRowLayout(node) },
        "LazyVerticalGrid" to { node -> LazyVerticalGridLayout(node) },
        "LazyHorizontalGrid" to { node -> LazyHorizontalGridLayout(node) },
    )

    @Composable
    private fun ColumnLayout(node: ComposeNode) {
        Column(
            modifier = ComposeRenderer.resolveModifier(node),
            verticalArrangement = resolveVerticalArrangement(node.prop<Any?>("verticalArrangement")),
            horizontalAlignment = resolveHorizontalAlignment(node.prop<Any?>("horizontalAlignment"))
        ) {
            if (node.childrenFunc != null) {
                ComposeRenderer.RenderChildren(node)
            } else {
                for (child in node.children) {
                    RenderChildWithWeight(child)
                }
            }
        }
    }

    @Composable
    private fun RowLayout(node: ComposeNode) {
        Row(
            modifier = ComposeRenderer.resolveModifier(node),
            horizontalArrangement = resolveHorizontalArrangement(node.prop<Any?>("horizontalArrangement")),
            verticalAlignment = resolveVerticalAlignment(node.prop<Any?>("verticalAlignment"))
        ) {
            if (node.childrenFunc != null) {
                ComposeRenderer.RenderChildren(node)
            } else {
                for (child in node.children) {
                    RenderChildWithWeight(child)
                }
            }
        }
    }

    @Composable
    private fun BoxLayout(node: ComposeNode) {
        Box(
            modifier = ComposeRenderer.resolveModifier(node),
            contentAlignment = resolveContentAlignment(node.prop<Any?>("contentAlignment")) ?: Alignment.TopStart
        ) {
            if (node.childrenFunc != null) {
                ComposeRenderer.RenderChildren(node)
            } else {
                for (child in node.children) {
                    RenderChildWithAlign(child)
                }
            }
        }
    }

    @Composable
    private fun LazyColumnLayout(node: ComposeNode) {
        LazyColumn(
            modifier = ComposeRenderer.resolveModifier(node),
            verticalArrangement = resolveVerticalArrangement(node.prop<Any?>("verticalArrangement")),
            horizontalAlignment = resolveHorizontalAlignment(node.prop<Any?>("horizontalAlignment"))
        ) {
            renderLazyItems(node)
        }
    }

    @Composable
    private fun LazyRowLayout(node: ComposeNode) {
        LazyRow(
            modifier = ComposeRenderer.resolveModifier(node),
            horizontalArrangement = resolveHorizontalArrangement(node.prop<Any?>("horizontalArrangement")),
            verticalAlignment = resolveVerticalAlignment(node.prop<Any?>("verticalAlignment"))
        ) {
            renderLazyItems(node)
        }
    }

    @Composable
    private fun LazyVerticalGridLayout(node: ComposeNode) {
        val columns = resolveGridCells(node)
        LazyVerticalGrid(
            columns = columns,
            modifier = ComposeRenderer.resolveModifier(node),
            verticalArrangement = resolveVerticalArrangement(node.prop<Any?>("verticalArrangement")),
            horizontalArrangement = resolveHorizontalArrangement(node.prop<Any?>("horizontalArrangement")),
        ) {
            renderLazyGridItems(node)
        }
    }

    @Composable
    private fun LazyHorizontalGridLayout(node: ComposeNode) {
        val rows = resolveGridCells(node)
        LazyHorizontalGrid(
            rows = rows,
            modifier = ComposeRenderer.resolveModifier(node),
            verticalArrangement = resolveVerticalArrangement(node.prop<Any?>("verticalArrangement")),
            horizontalArrangement = resolveHorizontalArrangement(node.prop<Any?>("horizontalArrangement")),
        ) {
            renderLazyGridItems(node)
        }
    }

    /** 解析 GridCells：columns 或 rows 属性，支持 "Fixed(n)" 格式 */
    private fun resolveGridCells(node: ComposeNode): GridCells {
        val count = node.floatProp("columns", 0f).toInt()
        if (count > 0) return GridCells.Fixed(count)
        val fixedStr = node.stringProp("columns") ?: node.stringProp("rows")
        if (fixedStr != null) {
            val match = Regex("Fixed\\((\\d+)\\)").find(fixedStr)
            if (match != null) return GridCells.Fixed(match.groupValues[1].toInt())
        }
        return GridCells.Fixed(2) // 默认2列
    }

    /**
     * 渲染LazyGrid的子项，与 renderLazyItems 逻辑一致
     */
    private fun androidx.compose.foundation.lazy.grid.LazyGridScope.renderLazyGridItems(node: ComposeNode) {
        val childrenFunc = node.childrenFunc
        if (childrenFunc != null) {
            synchronized(ComposeBridgeInstance.current.luaLock) {
                try {
                    val result = childrenFunc.call()
                    when (result) {
                        is ComposeNode -> {
                            item { ComposeRenderer.RenderNode(result) }
                        }
                        is List<*> -> {
                            gridItems(result.filterIsInstance<ComposeNode>()) { child ->
                                ComposeRenderer.RenderNode(child)
                            }
                        }
                        else -> {
                            item { ComposeRenderer.RenderChildren(node) }
                        }
                    }
                } catch (e: Exception) {
                    logE("LayoutComponents") { "[LazyGrid] childrenFunc调用失败: ${e.message}" }
                }
            }
        } else {
            gridItemsIndexed(node.children) { _, child -> ComposeRenderer.RenderNode(child) }
        }
    }

    /**
     * 渲染LazyList的子项：
     * - 静态children列表：使用itemsIndexed逐个懒加载
     * - childrenFunc动态函数：传递 LazyListScopeWrapper 让 Lua 侧直接调用 item()/items()
     *   - 返回 nil：Lua 侧通过 scope:item()/scope:items() 直接添加（推荐）
     *   - 返回 ComposeNode：fallback 为单个 item
     *   - 返回 List：fallback 为 items 批量
     */
    private fun androidx.compose.foundation.lazy.LazyListScope.renderLazyItems(node: ComposeNode) {
        val childrenFunc = node.childrenFunc
        if (childrenFunc != null) {
            val wrapper = LazyListScopeWrapper(this)
            synchronized(ComposeBridgeInstance.current.luaLock) {
                try {
                    val result = childrenFunc.call(wrapper)
                    // 如果 Lua 侧通过 scope:item()/scope:items() 添加了子项，result 为 nil
                    // 否则 fallback 到旧逻辑
                    when (result) {
                        is ComposeNode -> {
                            item { ComposeRenderer.RenderNode(result) }
                        }
                        is List<*> -> {
                            items(result.filterIsInstance<ComposeNode>()) { child ->
                                ComposeRenderer.RenderNode(child)
                            }
                        }
                        else -> {
                            // 返回 nil 或其他非节点类型：Lua 侧已通过 wrapper 添加
                        }
                    }
                } catch (e: Exception) {
                    logE("LayoutComponents") { "[LazyList] childrenFunc调用失败: ${e.message}" }
                }
            }
        } else {
            itemsIndexed(node.children) { _, child -> ComposeRenderer.RenderNode(child) }
        }
    }

    private fun resolveVerticalArrangement(prop: Any?): Arrangement.Vertical = when {
        prop is Arrangement.Vertical -> prop
        prop is String -> when (prop) {
            "Center" -> Arrangement.Center; "Top" -> Arrangement.Top; "Bottom" -> Arrangement.Bottom
            "SpaceAround" -> Arrangement.SpaceAround; "SpaceBetween" -> Arrangement.SpaceBetween
            "SpaceEvenly" -> Arrangement.SpaceEvenly; else -> Arrangement.Top
        }
        else -> Arrangement.Top
    }
    private fun resolveHorizontalArrangement(prop: Any?): Arrangement.Horizontal = when {
        prop is Arrangement.Horizontal -> prop
        prop is String -> when (prop) {
            "Center" -> Arrangement.Center; "Start" -> Arrangement.Start; "End" -> Arrangement.End
            "SpaceAround" -> Arrangement.SpaceAround; "SpaceBetween" -> Arrangement.SpaceBetween
            "SpaceEvenly" -> Arrangement.SpaceEvenly; else -> Arrangement.Start
        }
        else -> Arrangement.Start
    }
    private fun resolveHorizontalAlignment(prop: Any?): Alignment.Horizontal = when {
        prop is Alignment.Horizontal -> prop
        prop is String -> when (prop) {
            "CenterHorizontally" -> Alignment.CenterHorizontally; "Start" -> Alignment.Start; "End" -> Alignment.End; else -> Alignment.Start
        }
        else -> Alignment.Start
    }
    private fun resolveVerticalAlignment(prop: Any?): Alignment.Vertical = when {
        prop is Alignment.Vertical -> prop
        prop is String -> when (prop) {
            "CenterVertically" -> Alignment.CenterVertically; "Top" -> Alignment.Top; "Bottom" -> Alignment.Bottom; else -> Alignment.CenterVertically
        }
        else -> Alignment.CenterVertically
    }
    private fun resolveContentAlignment(prop: Any?): Alignment? = when {
        prop is Alignment -> prop
        prop is String -> when (prop) {
            "Center" -> Alignment.Center; "TopStart" -> Alignment.TopStart; "TopCenter" -> Alignment.TopCenter
            "TopEnd" -> Alignment.TopEnd; "CenterStart" -> Alignment.CenterStart; "CenterEnd" -> Alignment.CenterEnd
            "BottomStart" -> Alignment.BottomStart; "BottomCenter" -> Alignment.BottomCenter; "BottomEnd" -> Alignment.BottomEnd; else -> null
        }
        else -> null
    }
}