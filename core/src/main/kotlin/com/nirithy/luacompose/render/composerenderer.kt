package com.nirithy.luacompose.render

import com.nirithy.luacompose.logD
import com.nirithy.luacompose.logE
import com.nirithy.luacompose.logV
import com.nirithy.luacompose.logW
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import com.nirithy.luacompose.animation.SharedTransitionComponents
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.draw.DrawScopeWrapper
import com.nirithy.luacompose.gesture.applyGestures
import com.nirithy.luacompose.modifier.ModifierChain
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.preview.previewNodeInteraction

private const val TAG = "ComposeRenderer"

object ComposeRenderer {
    @Composable
    fun Render(rootNode: ComposeNode) {
        logD(TAG) { "[Render] 开始渲染节点树, 根节点: type=${rootNode.type}" }
        RenderNode(rootNode)
    }

    @Composable
    fun RenderNode(node: ComposeNode) {
        logV(TAG) { "[RenderNode] 渲染节点: type=${node.type}, 渲染器是否存在=${ComponentRegistry.hasRenderer(node.type)}" }
        ComponentRegistry.render(node)
    }

    @Composable
    fun resolveModifier(node: ComposeNode): Modifier {
        val chain = node.props["modifier"] as? ModifierChain
        var mod = when {
            chain != null -> {
                logV(TAG) { "[resolveModifier] node=${node.type}, 使用 ModifierChain" }
                var baseMod = chain.build()
                // 应用手势
                baseMod = baseMod.applyGestures(chain.gestureConfig)
                // 应用滚动
                if (chain.scrollable) {
                    baseMod = baseMod.verticalScroll(rememberScrollState())
                }
                // 应用 drawBehind
                chain.drawBehindCallback?.let { cb ->
                    baseMod = baseMod.drawBehind { DrawScopeWrapper(this).let { w -> try { cb.call(w) } catch (_: Exception) {} } }
                }
                // 应用 drawWithContent
                chain.drawWithContentCallback?.let { cb ->
                    baseMod = baseMod.drawWithContent {
                        DrawScopeWrapper(this).let { w -> try { cb.call(w) } catch (_: Exception) {} }
                        drawContent()
                    }
                }
                // 应用 sharedElement / sharedBounds
                if (chain.sharedElementKey != null) {
                    baseMod = SharedTransitionComponents.sharedElementModifier(
                        baseMod, chain.sharedElementKey!!, chain.sharedElementBoundsTransform
                    )
                }
                if (chain.sharedBoundsKey != null) {
                    baseMod = SharedTransitionComponents.sharedElementModifier(baseMod, chain.sharedBoundsKey!!)
                }
                baseMod
            }
            else -> {
                val prop = node.props["modifier"]
                when (prop) {
                    is Modifier -> {
                        logV(TAG) { "[resolveModifier] node=${node.type}, 直接使用 Modifier" }
                        prop
                    }
                    else -> {
                        logV(TAG) { "[resolveModifier] node=${node.type}, 无 modifier" }
                        Modifier
                    }
                }
            }
        }

        // 预览模式：添加节点边界记录和点击选中
        val bridge = ComposeBridgeInstance.current
        val nodePath = node.nodePath
        if (bridge.isPreviewMode && nodePath != null) {
            val collector = bridge.previewBoundsCollector
            val onNodeClick = bridge.onNodeClick
            if (collector != null && onNodeClick != null) {
                mod = mod.previewNodeInteraction(collector, nodePath, onNodeClick)
            }
        }

        return mod
    }

    /** 从 ModifierChain 中提取 weight 比例 */
    fun extractWeight(node: ComposeNode): Float =
        (node.props["modifier"] as? ModifierChain)?.weightProportion ?: 0f

    /** 从 ModifierChain 中提取 alignment */
    fun extractAlignment(node: ComposeNode): Any? =
        (node.props["modifier"] as? ModifierChain)?.alignment

    @Composable
    fun RenderChildren(node: ComposeNode) {
        val childrenFunc = node.childrenFunc
        if (childrenFunc != null) {
            // 动态子节点：调用 Lua 函数获取 ComposeNode，每次 recomposition 都会重新执行
            // 用于 Crossfade/AnimatedContent 等需要根据 targetState 动态生成内容的场景
            logV(TAG) { "[RenderChildren] node=${node.type}, 使用 childrenFunc 动态渲染" }
            // try-catch 不能包裹 @Composable 调用，所以只包裹非 Composable 部分
            val result: Any? = try {
                synchronized(ComposeBridgeInstance.current.luaLock) { childrenFunc.call() }
            } catch (e: Exception) {
                logE(TAG) { "[RenderChildren] childrenFunc 调用失败: ${e.message}" }
                null
            }
            if (result is ComposeNode) {
                RenderNode(result)
            } else if (result != null) {
                logW(TAG) { "[RenderChildren] childrenFunc 返回了非 ComposeNode 类型: ${result.javaClass.name}" }
            }
        } else {
            logV(TAG) { "[RenderChildren] node=${node.type}, 子节点数=${node.children.size}" }
            for (child in node.children) RenderNode(child)
        }
    }
}

/**
 * 在 Row/Column 中渲染子节点，自动消费 weight
 * 顶层扩展函数，可在 RowScope/ColumnScope 上下文中直接调用
 */
@Composable
fun RowScope.RenderChildWithWeight(child: ComposeNode) {
    val chain = child.props["modifier"] as? ModifierChain
    if (chain != null && chain.weightProportion > 0f) {
        val weightedMod = chain.build().weight(chain.weightProportion)
        ComposeRenderer.RenderNode(child.copy(props = child.props + ("modifier" to weightedMod)))
    } else {
        ComposeRenderer.RenderNode(child)
    }
}

@Composable
fun ColumnScope.RenderChildWithWeight(child: ComposeNode) {
    val chain = child.props["modifier"] as? ModifierChain
    if (chain != null && chain.weightProportion > 0f) {
        val weightedMod = chain.build().weight(chain.weightProportion)
        ComposeRenderer.RenderNode(child.copy(props = child.props + ("modifier" to weightedMod)))
    } else {
        ComposeRenderer.RenderNode(child)
    }
}

/**
 * 在 Box 中渲染子节点，自动消费 align
 */
@Composable
fun BoxScope.RenderChildWithAlign(child: ComposeNode) {
    val chain = child.props["modifier"] as? ModifierChain
    val align = chain?.alignment
    if (align is Alignment) {
        val alignedMod = chain.build().align(align)
        ComposeRenderer.RenderNode(child.copy(props = child.props + ("modifier" to alignedMod)))
    } else {
        ComposeRenderer.RenderNode(child)
    }
}