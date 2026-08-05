package com.nirithy.luacompose.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.logE
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer

private const val TAG = "BoxWithConstraints"

/**
 * BoxWithConstraints 组件插件
 * 暴露 maxWidth/maxHeight/minWidth/minHeight 给子组件
 *
 * Lua 用法（静态 children）：
 *   compose.BoxWithConstraints {
 *     modifier = compose.Modifier().fillMaxWidth().height(100),
 *     children = {
 *       compose.Text {
 *         text = "maxWidth: " .. tostring(constraints.maxWidth)
 *       }
 *     },
 *   }
 *
 * Lua 用法（动态 content 回调）：
 *   compose.BoxWithConstraints {
 *     content = function(boxWithConstraintsScope)
 *       local widthPx = boxWithConstraintsScope.maxWidth * LocalDensity.current.density
 *       local heightPx = boxWithConstraintsScope.maxHeight * LocalDensity.current.density
 *       return compose.Text { text = "w=" .. tostring(widthPx) .. " h=" .. tostring(heightPx) }
 *     end
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

/**
 * BoxWithConstraints 的 content 回调参数，传递约束信息给 Lua 函数
 * Lua 中通过 . 语法访问属性：scope.maxWidth, scope.maxHeight, scope.minWidth, scope.minHeight
 */
class BoxWithConstraintsScopeWrapper(
    val maxWidth: Float,
    val maxHeight: Float,
    val minWidth: Float,
    val minHeight: Float
)

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
            // content 回调模式：将约束信息包装为 scope 对象传给 Lua 函数
            val scopeWrapper = BoxWithConstraintsScopeWrapper(
                maxWidth = maxWidth.value,
                maxHeight = maxHeight.value,
                minWidth = minWidth.value,
                minHeight = minHeight.value
            )
            val result: Any? = try {
                synchronized(ComposeBridgeInstance.current.luaLock) {
                    node.childrenFunc!!.call(scopeWrapper)
                }
            } catch (e: Exception) {
                logE(TAG) { "[BoxWithConstraintsLayout] childrenFunc 调用失败: ${e.message}" }
                null
            }
            if (result is ComposeNode) {
                ComposeRenderer.RenderNode(result)
            }
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