package com.nirithy.luacompose.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer

/**
 * BoxWithConstraints 组件插件
 * 暴露 maxWidth/maxHeight/minWidth/minHeight 给子组件
 *
 * Lua 用法：
 *   compose.BoxWithConstraints {
 *     modifier = compose.Modifier().fillMaxWidth().height(100),
 *     children = {
 *       compose.Text {
 *         text = "maxWidth: " .. tostring(constraints.maxWidth)
 *       }
 *     },
 *   }
 *
 * 子组件可通过 node.props["constraints"] 访问 ConstraintInfo 对象。
 */
object BoxWithConstraintsComponent : ComposePlugin {
    override val namespace = "layout"

    override fun getComponents() = mapOf<String, @Composable (ComposeNode) -> Unit>(
        "BoxWithConstraints" to { node -> BoxWithConstraintsLayout(node) },
    )

    /**
     * 约束信息，由 BoxWithConstraintsLayout 在渲染时注入到子节点
     */
    data class ConstraintInfo(
        val maxWidth: Float, val maxHeight: Float,
        val minWidth: Float, val minHeight: Float
    )
}

@Composable
private fun BoxWithConstraintsLayout(node: ComposeNode) {
    BoxWithConstraints(
        modifier = ComposeRenderer.resolveModifier(node)
    ) {
        val constraints = BoxWithConstraintsComponent.ConstraintInfo(
            maxWidth = maxWidth.value,
            maxHeight = maxHeight.value,
            minWidth = minWidth.value,
            minHeight = minHeight.value
        )
        if (node.childrenFunc != null) {
            ComposeRenderer.RenderChildren(node)
        } else {
            for (child in node.children) {
                val constrainedChild = child.copy(
                    props = child.props + mapOf("constraints" to constraints)
                )
                ComposeRenderer.RenderNode(constrainedChild)
            }
        }
    }
}