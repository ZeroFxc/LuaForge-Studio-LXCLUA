package com.nirithy.luacompose.component

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin

/**
 * BackHandler 组件 — 拦截系统返回键
 *
 * Lua 用法：
 *   compose.BackHandler {
 *     enabled = true,
 *     onBack = function()
 *       selected.value = nil
 *     end,
 *   }
 */
object BackHandlerComponent : ComposePlugin {
    override val namespace = "display"

    override fun getComponents() = mapOf<String, @Composable (ComposeNode) -> Unit>(
        "BackHandler" to { node -> BackHandlerRenderer(node) },
    )

    @Composable
    private fun BackHandlerRenderer(node: ComposeNode) {
        val enabled = node.boolProp("enabled", true)
        val onBack = node.callback("onBack")

        BackHandler(enabled = enabled) {
            synchronized(ComposeBridgeInstance.current.luaLock) { onBack?.call() }
        }
    }
}