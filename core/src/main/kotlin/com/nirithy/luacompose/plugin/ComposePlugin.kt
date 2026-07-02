package com.nirithy.luacompose.plugin

import androidx.compose.runtime.Composable
import com.luajava.LuaState
import com.nirithy.luacompose.node.ComposeNode

/**
 * Compose 组件插件接口
 *
 * 每个功能模块（布局、显示、输入、容器、动画等）实现此接口。
 *
 * 设计参考原版 kulipai/luacompose 的 ComposeScriptPlugin：
 *   - getComponents(): 组件名 → 渲染器映射
 *   - injectGlobals(): 向 Lua 环境注入全局辅助函数/常量（如 Arrangement、FontWeight）
 *   - injectLocals(): 向 Compose 作用域注入局部值（如 LocalDensity）
 *
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

    /**
     * 向 Lua 环境注入全局辅助函数/常量
     *
     * 示例：
     *   - Arrangement.SpaceBetween → compose.Arrangement.SpaceBetween
     *   - FontWeight.Bold → compose.FontWeight.Bold
     *   - CardDefaults → compose.material3.CardDefaults
     *
     * 默认空实现，子类按需覆盖。
     *
     * @param L LuaState
     * @param composeTable 当前 compose 表在栈中的索引
     */
    fun injectGlobals(L: LuaState, composeTableIdx: Int) {}

    /**
     * 向 Compose 作用域注入局部值
     *
     * 示例：
     *   - LocalDensity → 在 Lua 中可通过 compose.LocalDensity 访问
     *   - LocalConfiguration → 在 Lua 中可通过 compose.LocalConfiguration 访问
     *
     * 默认空实现，子类按需覆盖。
     *
     * @param L LuaState
     * @param composeTableIdx 当前 compose 表在栈中的索引
     */
    fun injectLocals(L: LuaState, composeTableIdx: Int) {}
}