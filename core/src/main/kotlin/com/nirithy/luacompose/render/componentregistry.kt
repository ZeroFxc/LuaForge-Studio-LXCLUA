package com.nirithy.luacompose.render

import com.nirithy.luacompose.logW
import androidx.compose.runtime.Composable
import com.nirithy.luacompose.node.ComposeNode

/**
 * 组件渲染器注册表
 *
 * 渲染优先级：KSP 编译期生成 > 硬编码注册 > 动态反射 > 静默跳过
 */
object ComponentRegistry {
    private const val TAG = "ComponentRegistry"
    private val renderers = mutableMapOf<String, @Composable (ComposeNode) -> Unit>()

    fun register(type: String, renderer: @Composable (ComposeNode) -> Unit) {
        renderers[type] = renderer
    }

    fun registerAll(entries: Map<String, @Composable (ComposeNode) -> Unit>) {
        renderers.putAll(entries)
    }

    @Composable
    fun render(node: ComposeNode) {
        // ★ 优先通过 _classPath 精确匹配（KSP 生成的渲染器）
        val classPath = node.stringProp("_classPath")
        val renderer = if (classPath != null) {
            renderers[classPath] ?: renderers[node.type]
        } else {
            renderers[node.type]
        }

        if (renderer != null) {
            renderer(node)
        } else if (DynamicRenderer.tryRender(node)) {
            // 动态反射渲染成功
        } else {
            logW(TAG) { "[render] 未找到渲染器: type=${node.type}, classPath=${node.stringProp("_classPath")}" }
        }
    }

    fun hasRenderer(type: String): Boolean = renderers.containsKey(type)
    fun componentCount(): Int = renderers.size
    fun clear() { renderers.clear() }
}