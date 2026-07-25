package com.nirithy.luacompose.bridge

/**
 * Compose-Lua 桥接核心（全局默认单例）
 *
 * 将 Jetpack Compose 组件库以函数式声明风格注入到 Lua 环境中。
 * 实际状态和逻辑由父类 ComposeBridgeInstance 持有，支持多实例隔离。
 * 此单例用于 LuaActivity 正常运行，预览沙箱使用独立的 ComposeBridgeInstance 实例。
 *
 * 职责：状态管理、注入编排、刷新调度、组件工厂注册。
 * Lua API 注入器（工厂函数）→ ComposeInjectors.kt
 * 节点解析器 → NodeParser.kt
 */
object ComposeBridge : ComposeBridgeInstance() {
    override val TAG: String = "ComposeBridge"
}
