package com.nirithy.luacompose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer
import com.nirithy.luacompose.render.RenderChildWithAlign
import com.nirithy.luacompose.render.RenderChildWithWeight

/**
 * 布局组件插件：Column、Row、Box、LazyColumn、LazyRow
 */
object LayoutComponents : ComposePlugin {
    override val namespace = "layout"

    override fun getComponents() = mapOf<String, @Composable (ComposeNode) -> Unit>(
        "Column" to { node -> ColumnLayout(node) },
        "Row" to { node -> RowLayout(node) },
        "Box" to { node -> BoxLayout(node) },
        "LazyColumn" to { node -> LazyColumnLayout(node) },
        "LazyRow" to { node -> LazyRowLayout(node) },
    )

    @Composable
    private fun ColumnLayout(node: ComposeNode) {
        Column(
            modifier = ComposeRenderer.resolveModifier(node),
            verticalArrangement = resolveVerticalArrangement(node.stringProp("verticalArrangement")),
            horizontalAlignment = resolveHorizontalAlignment(node.stringProp("horizontalAlignment"))
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
            horizontalArrangement = resolveHorizontalArrangement(node.stringProp("horizontalArrangement")),
            verticalAlignment = resolveVerticalAlignment(node.stringProp("verticalAlignment"))
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
            contentAlignment = resolveContentAlignment(node.stringProp("contentAlignment")) ?: Alignment.TopStart
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
            verticalArrangement = resolveVerticalArrangement(node.stringProp("verticalArrangement")),
            horizontalAlignment = resolveHorizontalAlignment(node.stringProp("horizontalAlignment"))
        ) {
            if (node.childrenFunc != null) {
                item { ComposeRenderer.RenderChildren(node) }
            } else {
                itemsIndexed(node.children) { _, child -> ComposeRenderer.RenderNode(child) }
            }
        }
    }

    @Composable
    private fun LazyRowLayout(node: ComposeNode) {
        LazyRow(
            modifier = ComposeRenderer.resolveModifier(node),
            horizontalArrangement = resolveHorizontalArrangement(node.stringProp("horizontalArrangement")),
            verticalAlignment = resolveVerticalAlignment(node.stringProp("verticalAlignment"))
        ) {
            if (node.childrenFunc != null) {
                item { ComposeRenderer.RenderChildren(node) }
            } else {
                itemsIndexed(node.children) { _, child -> ComposeRenderer.RenderNode(child) }
            }
        }
    }

    private fun resolveVerticalArrangement(name: String?): Arrangement.Vertical = when (name) {
        "Center" -> Arrangement.Center; "Top" -> Arrangement.Top; "Bottom" -> Arrangement.Bottom
        "SpaceAround" -> Arrangement.SpaceAround; "SpaceBetween" -> Arrangement.SpaceBetween
        "SpaceEvenly" -> Arrangement.SpaceEvenly; else -> Arrangement.Top
    }
    private fun resolveHorizontalArrangement(name: String?): Arrangement.Horizontal = when (name) {
        "Center" -> Arrangement.Center; "Start" -> Arrangement.Start; "End" -> Arrangement.End
        "SpaceAround" -> Arrangement.SpaceAround; "SpaceBetween" -> Arrangement.SpaceBetween
        "SpaceEvenly" -> Arrangement.SpaceEvenly; else -> Arrangement.Start
    }
    private fun resolveHorizontalAlignment(name: String?): Alignment.Horizontal = when (name) {
        "CenterHorizontally" -> Alignment.CenterHorizontally; "Start" -> Alignment.Start; "End" -> Alignment.End; else -> Alignment.Start
    }
    private fun resolveVerticalAlignment(name: String?): Alignment.Vertical = when (name) {
        "CenterVertically" -> Alignment.CenterVertically; "Top" -> Alignment.Top; "Bottom" -> Alignment.Bottom; else -> Alignment.CenterVertically
    }
    private fun resolveContentAlignment(name: String?): Alignment? = when (name) {
        "Center" -> Alignment.Center; "TopStart" -> Alignment.TopStart; "TopCenter" -> Alignment.TopCenter
        "TopEnd" -> Alignment.TopEnd; "CenterStart" -> Alignment.CenterStart; "CenterEnd" -> Alignment.CenterEnd
        "BottomStart" -> Alignment.BottomStart; "BottomCenter" -> Alignment.BottomCenter; "BottomEnd" -> Alignment.BottomEnd; else -> null
    }
}