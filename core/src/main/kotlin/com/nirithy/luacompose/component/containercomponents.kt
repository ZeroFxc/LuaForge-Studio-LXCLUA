package com.nirithy.luacompose.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer

/**
 * 容器组件插件：Card、Surface、Scaffold、Spacer、Divider、VerticalDivider
 */
object ContainerComponents : ComposePlugin {
    override val namespace = "container"

    @OptIn(ExperimentalMaterial3Api::class)
    override fun getComponents() = mapOf<String, @Composable (ComposeNode) -> Unit>(
        "Card" to { node -> CardLayout(node) },
        "Surface" to { node -> SurfaceLayout(node) },
        "Scaffold" to { node -> ScaffoldLayout(node) },
        "Spacer" to { node -> SpacerLayout(node) },
        "Divider" to { node -> DividerLayout(node) },
        "VerticalDivider" to { node -> VerticalDividerLayout(node) },
    )

    @Composable private fun CardLayout(node: ComposeNode) {
        val colors = buildCardColors(node)
        Card(modifier = ComposeRenderer.resolveModifier(node),
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = node.floatProp("elevation", 4f).dp),
        ) { ComposeRenderer.RenderChildren(node) }
    }

    /** 从 Lua colors 表构建 CardColors，支持 containerColor/contentColor/disabledContainerColor/disabledContentColor */
    @Composable
    private fun buildCardColors(node: ComposeNode): androidx.compose.material3.CardColors {
        // 优先从 colors 表读取，兼容旧的 color 属性
        val colorsTable = node.props["colors"]
        val containerColor = colorsTable?.let { resolveColorFromTable(it, "containerColor") }
            ?: node.props["color"]?.let { resolveColor(it) }
        val contentColor = colorsTable?.let { resolveColorFromTable(it, "contentColor") }
        val disabledContainerColor = colorsTable?.let { resolveColorFromTable(it, "disabledContainerColor") }
        val disabledContentColor = colorsTable?.let { resolveColorFromTable(it, "disabledContentColor") }

        return CardDefaults.cardColors(
            containerColor = containerColor ?: CardDefaults.cardColors().containerColor,
            contentColor = contentColor ?: CardDefaults.cardColors().contentColor,
            disabledContainerColor = disabledContainerColor ?: CardDefaults.cardColors().disabledContainerColor,
            disabledContentColor = disabledContentColor ?: CardDefaults.cardColors().disabledContentColor,
        )
    }

    /** 从 Lua 子表中提取颜色值 */
    private fun resolveColorFromTable(table: Any?, key: String): Color? {
        if (table !is Map<*, *>) return null
        return resolveColor(table[key])
    }
    @Composable private fun SurfaceLayout(node: ComposeNode) {
        Surface(modifier = ComposeRenderer.resolveModifier(node),
            color = node.props["color"]?.let { resolveColor(it) } ?: Color.Transparent,
        ) { ComposeRenderer.RenderChildren(node) }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun ScaffoldLayout(node: ComposeNode) {
        val title = node.stringProp("title") ?: ""
        val topBarNode = node.children.find { it.type == "TopBar" || it.props["_scaffoldSlot"] == "topBar" }

        // snackbarHost: 从 props["snackbarHost"] 表中提取 SnackbarHost ComposeNode
        val snackbarHostNode = extractScaffoldSlotNode(node.props["snackbarHost"])
        // bottomBar / floatingActionButton: 从 children 中查找
        val bottomBarNode = node.children.find { it.props["_scaffoldSlot"] == "bottomBar" }
        val fabNode = node.children.find { it.props["_scaffoldSlot"] == "floatingActionButton" }
        // 需要从 children 中排除已分配到 slot 的节点，只渲染剩余内容
        val slotChildren = node.children.filter {
            it.props["_scaffoldSlot"] !in listOf("topBar", "bottomBar", "floatingActionButton")
        }

        Scaffold(modifier = ComposeRenderer.resolveModifier(node),
            topBar = {
                if (topBarNode != null) {
                    TopAppBar(title = { Text(topBarNode.stringProp("title") ?: title) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = topBarNode.props["color"]?.let { resolveColor(it) } ?: TopAppBarDefaults.topAppBarColors().containerColor
                        ))
                } else if (title.isNotEmpty()) { TopAppBar(title = { Text(title) }) }
            },
            snackbarHost = {
                if (snackbarHostNode != null) {
                    ComposeRenderer.RenderNode(snackbarHostNode)
                }
            },
            bottomBar = {
                if (bottomBarNode != null) {
                    ComposeRenderer.RenderNode(bottomBarNode)
                }
            },
            floatingActionButton = {
                if (fabNode != null) {
                    ComposeRenderer.RenderNode(fabNode)
                }
            },
        ) { _ ->
            // 渲染剩余子节点（排除已分配到 slot 的）
            ComposeRenderer.RenderChildren(ComposeNode(
                type = node.type, props = node.props, children = slotChildren,
                callbacks = node.callbacks, childrenFunc = node.childrenFunc
            ))
        }
    }

    /** 从 snackbarHost 表 prop 中提取 ComposeNode（如 SnackbarHost） */
    private fun extractScaffoldSlotNode(prop: Any?): ComposeNode? {
        if (prop is ComposeNode) return prop
        if (prop is Map<*, *>) {
            // 遍历 Map 查找 ComposeNode（如 {compose.SnackbarHost({...})}）
            for (value in prop.values) {
                if (value is ComposeNode) return value
            }
        }
        return null
    }
    @Composable private fun SpacerLayout(node: ComposeNode) {
        val w = node.floatProp("width", 0f); val h = node.floatProp("height", 0f)
        Spacer(modifier = ComposeRenderer.resolveModifier(node)
            .then(if (w > 0f) Modifier.width(w.dp) else Modifier)
            .then(if (h > 0f) Modifier.height(h.dp) else Modifier))
    }
    @Composable private fun DividerLayout(node: ComposeNode) {
        HorizontalDivider(modifier = ComposeRenderer.resolveModifier(node), thickness = node.floatProp("thickness", 1f).dp,
            color = node.props["color"]?.let { resolveColor(it) } ?: Color.Gray.copy(alpha = 0.3f))
    }
    @Composable private fun VerticalDividerLayout(node: ComposeNode) {
        VerticalDivider(modifier = ComposeRenderer.resolveModifier(node), thickness = node.floatProp("thickness", 1f).dp,
            color = node.props["color"]?.let { resolveColor(it) } ?: Color.Gray.copy(alpha = 0.3f))
    }
    /** Color(Int) 按 sRGB ARGB 解释，Double.toInt() 会截断溢出 */
    private fun resolveColor(value: Any?): Color? = when (value) {
        is Long -> Color(value.toInt()); is Int -> Color(value)
        is Double -> Color(value.toLong().toInt())
        is Number -> Color(value.toInt()); else -> null
    }
}