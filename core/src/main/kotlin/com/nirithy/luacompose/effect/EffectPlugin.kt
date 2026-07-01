package com.nirithy.luacompose.effect

import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin

/**
 * Compose 副作用组件插件
 * 整合 LaunchedEffect、key、DisposableEffect 的注册
 *
 * 原版对应 LuaComposeLib 中的副作用注册
 */
object EffectPlugin : ComposePlugin {
    override val namespace = "effect"

    override fun getComponents() = mapOf<String, @androidx.compose.runtime.Composable (ComposeNode) -> Unit>(
        "LaunchedEffect" to { node -> LaunchedEffectRenderer(node) },
        "key" to { node -> KeyRenderer(node) },
        "DisposableEffect" to { node -> DisposableEffectRenderer(node) },
    )
}