package com.nirithy.luacompose.plugin

import com.nirithy.luacompose.logD
import com.nirithy.luacompose.logI
import com.nirithy.luacompose.render.ComponentRegistry

/**
 * 插件注册表 — 统一管理所有 ComposePlugin
 *
 * 替代原来在 ComposeBridge.inject() 中手动逐个调用 XxxComponents.register() 的方式。
 * 插件按注册顺序依次生效，后注册的同名组件会覆盖先注册的。
 *
 * 使用方式：
 *   PluginRegistry.init {
 *       register(LayoutComponents)
 *       register(DisplayComponents)
 *       // ...
 *   }
 *
 *   ComposeBridge.inject() 中调用：
 *   PluginRegistry.plugins.forEach { plugin ->
 *       ComponentRegistry.registerAll(plugin.getComponents())
 *   }
 */
object PluginRegistry {
    private const val TAG = "PluginRegistry"

    /** 已注册的插件列表（按注册顺序） */
    private val _plugins = mutableListOf<ComposePlugin>()

    /** 只读插件列表 */
    val plugins: List<ComposePlugin> get() = _plugins.toList()

    /**
     * 注册单个插件
     * @param plugin 插件实例
     */
    fun register(plugin: ComposePlugin) {
        _plugins.add(plugin)
        val ns = plugin.namespace?.let { "[$it]" } ?: ""
        logD(TAG) { "注册插件 $ns ${plugin::class.simpleName}, 组件: ${plugin.getComponents().keys}" }
    }

    /**
     * 批量注册插件
     * @param pluginList 插件列表
     */
    fun registerAll(vararg pluginList: ComposePlugin) {
        pluginList.forEach { register(it) }
    }

    /**
     * 将所有已注册插件的组件注入 ComponentRegistry
     * 应在 ComposeBridge.inject() 中调用，在 KSP 生成组件之前
     */
    fun applyToComponentRegistry() {
        logI(TAG) { "开始注入 ${_plugins.size} 个插件的组件..." }
        for (plugin in _plugins) {
            val components = plugin.getComponents()
            if (components.isNotEmpty()) {
                ComponentRegistry.registerAll(components)
                logD(TAG) { "  ${plugin.namespace ?: "root"}: ${components.size} 个组件" }
            }
        }
        logI(TAG) { "插件注入完成，ComponentRegistry 共 ${ComponentRegistry.componentCount()} 个组件" }
    }

    /**
     * 清空所有插件（用于测试或重新初始化）
     */
    fun clear() {
        _plugins.clear()
    }
}