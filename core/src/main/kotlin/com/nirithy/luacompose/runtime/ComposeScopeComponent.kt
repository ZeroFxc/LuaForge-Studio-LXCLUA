package com.nirithy.luacompose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.render.ComposeRenderer
import com.nirithy.luacompose.script.BridgeValue
import com.nirithy.luacompose.state.ComposeScope

/**
 * 在 Compose 上下文中执行 ComposeScope 并渲染其节点
 *
 * 从 Compose 环境中获取 coroutineScope、context、density、configuration、
 * colorScheme、typography、shapes 并注入到 scope 中，然后执行 scope 的 contentFunc
 * 并渲染生成的 ComposeNode 列表。
 *
 * 参考 LuaCompose-master 的 ComposeScopeComponent 设计
 */
@Composable
fun ComposeScopeComponent(
    scope: ComposeScope,
    parentComposeScope: Any? = null,
    vararg args: BridgeValue
) {
    scope.coroutineScope = rememberCoroutineScope()
    scope.context = LocalContext.current
    scope.density = LocalDensity.current.density
    scope.configuration = LocalConfiguration.current

    // 从 MaterialTheme 获取当前主题
    val currentColorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val currentTypography = androidx.compose.material3.MaterialTheme.typography
    val currentShapes = androidx.compose.material3.MaterialTheme.shapes

    scope.colorScheme = currentColorScheme
    scope.typography = currentTypography
    scope.shapes = currentShapes

    val version by scope.recomposeVersion
    val nodes = remember(
        version,
        currentColorScheme,
        currentTypography,
        currentShapes,
        *args
    ) {
        scope.execute(*args)
    }

    for (node in nodes) {
        ComposeNodeRenderer(node, parentComposeScope)
    }
}

/**
 * 渲染单个 ComposeNode，委托给 ComposeRenderer.RenderNode
 *
 * 参考 LuaCompose-master 的 ComposeNodeRenderer 设计
 */
@Composable
fun ComposeNodeRenderer(node: ComposeNode, parentComposeScope: Any? = null) {
    ComposeRenderer.RenderNode(node)
}