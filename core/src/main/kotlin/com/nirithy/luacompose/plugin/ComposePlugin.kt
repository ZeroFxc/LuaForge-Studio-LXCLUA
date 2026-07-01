package com.nirithy.luacompose.plugin

import androidx.compose.runtime.Composable
import com.nirithy.luacompose.node.ComposeNode

/**
 * Compose 组件插件接口
 * 每个功能模块（布局、显示、输入、容器、动画等）实现此接口，
 * 通过 [getComponents] 返回该模块提供的组件名 → 渲染器映射。
 *
 * 设计参考原版 kulipai/luacompose 的 ComposeScriptPlugin，
 * 适配 nirithy 的 ComposeNode 渲染模型。
 */
interface ComposePlugin {
    /**
     * 插件命名空间，用于日志和组件分类
     * 如 "layout"、"display"、"animation" 等
     * null 表示无特定命名空间
     */
    val namespace: String?

    /**
     * 返回该插件提供的组件：组件名 → @Composable 渲染器
     * 渲染器接收 ComposeNode，从中读取 props/children/callbacks 进行渲染
     */
    fun getComponents(): Map<String, @Composable (ComposeNode) -> Unit>
}