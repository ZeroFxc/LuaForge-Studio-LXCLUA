package com.nirithy.luacompose.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier

/**
 * 预览节点边界收集器
 *
 * 记录每个节点在根容器中的布局边界，用于选中高亮和点击检测。
 * 绑定到单个预览会话的生命周期。
 */
class PreviewNodeBoundsCollector {
    /** 节点路径到边界矩形的映射（相对于根容器） */
    private val bounds = mutableStateMapOf<String, Rect>()

    /**
     * 记录节点边界
     *
     * @param nodePath 节点路径ID
     * @param rect 节点边界矩形
     */
    fun updateBounds(nodePath: String, rect: Rect) {
        bounds[nodePath] = rect
    }

    /**
     * 获取节点边界
     *
     * @param nodePath 节点路径ID
     * @return 边界矩形，不存在则返回 null
     */
    fun getBounds(nodePath: String): Rect? = bounds[nodePath]

    /**
     * 获取所有已记录的边界
     */
    fun getAllBounds(): Map<String, Rect> = bounds.toMap()

    /**
     * 清除所有边界记录
     */
    fun clear() {
        bounds.clear()
    }
}

/**
 * 在 Modifier 上添加节点边界记录和点击回调
 *
 * 仅在预览模式下生效，用于收集节点位置并响应点击选中。
 *
 * @param collector 边界收集器
 * @param nodePath 当前节点路径
 * @param onNodeClick 节点点击回调
 * @return 配置后的 Modifier
 */
@Composable
fun Modifier.previewNodeInteraction(
    collector: PreviewNodeBoundsCollector,
    nodePath: String,
    onNodeClick: (String) -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .onGloballyPositioned { coordinates ->
            collector.updateBounds(nodePath, coordinates.boundsInRoot())
        }
        .then(
            clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onNodeClick(nodePath) }
            )
        )
}
