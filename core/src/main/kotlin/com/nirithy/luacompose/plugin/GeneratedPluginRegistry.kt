package com.nirithy.luacompose.plugin

import com.nirithy.luacompose.animation.AnimationPlugin
import com.nirithy.luacompose.component.AndroidViewComponent
import com.nirithy.luacompose.component.BackHandlerComponent
import com.nirithy.luacompose.component.BoxWithConstraintsComponent
import com.nirithy.luacompose.component.ComplementComponents
import com.nirithy.luacompose.component.ContainerComponents
import com.nirithy.luacompose.component.DisplayComponents
import com.nirithy.luacompose.component.IconComponent
import com.nirithy.luacompose.component.InputComponents
import com.nirithy.luacompose.component.LayoutComponents
import com.nirithy.luacompose.effect.EffectPlugin
import com.nirithy.luacompose.navigation.Navigation3Plugin
import com.nirithy.luacompose.render.CanvasPlugin

/**
 * 统一插件注册入口
 *
 * 集中管理所有 ComposePlugin 的注册，替代原来在 ComposeBridgeInstance.inject()
 * 中手动逐个调用的方式。新增插件只需在此文件中添加一行即可。
 *
 * 参考 com.kulipai.luacompose.generated.GeneratedPluginRegistry 的设计，
 * 未来可改为 KSP 编译期自动生成。
 */
object GeneratedPluginRegistry {
    /**
     * 获取所有已注册的插件列表
     * 按依赖顺序排列：Foundation API 在前，业务组件在后
     */
    fun getAllPlugins(): List<ComposePlugin> = listOf(
        // 基础 API 插件（注入全局函数和常量）
        FoundationPlugin,

        // Material3 主题插件（注入 CardDefaults、MaterialTheme 到 compose.material3）
        Material3Plugin,

        // 布局组件
        LayoutComponents,

        // 显示组件
        DisplayComponents,

        // 输入组件
        InputComponents,

        // 容器组件
        ContainerComponents,
        BoxWithConstraintsComponent,

        // 图标组件
        IconComponent,

        // 动画插件
        AnimationPlugin,

        // 画布插件
        CanvasPlugin,

        // 效果插件
        EffectPlugin,

        // 导航插件
        Navigation3Plugin(),

        // 平台组件
        AndroidViewComponent,

        // 辅助组件
        ComplementComponents,
        BackHandlerComponent,
    )

    /**
     * 将所有插件注册到 PluginRegistry
     */
    fun registerAll() {
        PluginRegistry.registerAll(*getAllPlugins().toTypedArray())
    }
}