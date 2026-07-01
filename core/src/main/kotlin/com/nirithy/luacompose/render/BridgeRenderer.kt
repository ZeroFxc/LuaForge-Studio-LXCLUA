package com.nirithy.luacompose.render

import androidx.compose.runtime.Composable
import com.nirithy.luacompose.node.ComposeNode

/**
 * KSP 生成的组件桥接渲染器接口。
 *
 * 每个生成的 object 实现此接口，提供 render() 方法。
 * ComponentRegistry 通过此接口进行类型安全的注册和调用。
 */
interface BridgeRenderer {
    @Composable
    fun render(node: ComposeNode)
}