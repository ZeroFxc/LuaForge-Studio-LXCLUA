package com.nirithy.luacompose.navigation

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.logD
import com.nirithy.luacompose.logE
import com.nirithy.luacompose.logW
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer
import com.luajava.JavaFunction
import com.luajava.LuaObject
import com.luajava.LuaState

private const val TAG = "Navigation3Plugin"

/**
 * Navigation3 导航插件
 * 将 androidx.navigation3 的导航能力注入到 Nirithy Compose 的 Lua 环境中。
 *
 * 注入的 Lua API：
 *   compose.rememberNavBackStack({"Home"})     — 创建导航回退栈（返回 MutableList）
 *   compose.entryProvider({ Home = fn, ... })  — 创建路由 → @Composable 映射
 *   compose.rememberSaveableStateHolderNavEntryDecorator() — 返回令牌字符串
 *
 * 注册的组件：
 *   navigation3.NavDisplay  — 渲染导航显示
 *
 * Lua 使用示例：
 *   local backStack = compose.rememberNavBackStack({"Home"})
 *   local provider = compose.entryProvider({
 *       Home = function() return compose.Surface { compose.Text { text = "Home" } } end,
 *       Detail = function() return compose.Surface { compose.Text { text = "Detail" } } end
 *   })
 *   compose.render(function()
 *       return compose.NavDisplay {
 *           backStack = backStack,
 *           entryProvider = provider,
 *           entryDecorators = { compose.rememberSaveableStateHolderNavEntryDecorator() }
 *       }
 *   end)
 *
 * 导航操作：
 *   backStack.add("Detail")                    -- 导航到 Detail
 *   backStack.removeAt(backStack.size() - 1)   -- 返回上一页
 *
 * 注意：
 *   导航条目的 Lua 函数在 Compose 重组时被调用，请避免在其中使用 compose.state() 或
 *   compose.remember()，这些 API 依赖全局状态缓存，在导航上下文中可能产生意外行为。
 *   如需状态管理，使用 Lua 局部变量或闭包捕获。
 */
class Navigation3Plugin : ComposePlugin {
    override val namespace: String = "navigation3"

    override fun getComponents(): Map<String, @Composable (ComposeNode) -> Unit> {
        return mapOf(
            "NavDisplay" to { node ->
                @Suppress("UNCHECKED_CAST")
                // 直接使用 NavBackStack 实例（MutableList），Navigation3 回退时
                // 修改会触发 onChanged → scheduleRefresh → main() 重新执行
                val backStack = (node.props["backStack"] as? MutableList<Any>) ?: mutableListOf()
                val modifier = ComposeRenderer.resolveModifier(node)

                // 拦截系统返回键，直接操作 NavBackStack
                // Navigation3 内部也有 BackHandler，但优先级低于此回调
                val context = LocalContext.current
                val activity = context as? ComponentActivity
                DisposableEffect(activity) {
                    val callback = object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() {
                            if (backStack.size > 1) {
                                logD(TAG) { "[NavDisplay] 系统返回键: 移除栈顶, 当前大小=${backStack.size}" }
                                backStack.removeAt(backStack.size - 1)
                            } else {
                                // 栈底，恢复 Activity 级别的返回处理
                                isEnabled = false
                                activity?.onBackPressedDispatcher?.onBackPressed()
                                isEnabled = true
                            }
                        }
                    }
                    activity?.onBackPressedDispatcher?.addCallback(callback)
                    onDispose { callback.remove() }
                }

                // 处理 entryDecorators：过滤真正的 NavEntryDecorator 实例，替换令牌字符串
                val luaDecoratorsRaw = (node.props["entryDecorators"] as? List<*>) ?: emptyList<Any>()
                @Suppress("UNCHECKED_CAST")
                val actualDecorators = luaDecoratorsRaw
                    .filterIsInstance<NavEntryDecorator<*>>()
                    .map { it as NavEntryDecorator<Any> }
                    .toMutableList()

                if (luaDecoratorsRaw.contains(SAVEABLE_STATE_HOLDER_TOKEN)) {
                    actualDecorators.add(
                        androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator<Any>()
                    )
                }

                @Suppress("UNCHECKED_CAST")
                val provider = node.props["entryProvider"] as? ((Any) -> NavEntry<Any>)

                logD(TAG) { "[NavDisplay] backStack.size=${backStack.size}, provider=${provider != null}" }

                // ★ 空栈防护：Navigation3 要求 backStack 至少 1 项
                if (backStack.isEmpty()) {
                    logW(TAG) { "[NavDisplay] 跳过渲染：backStack 为空" }
                } else {
                    if (provider != null) {
                        androidx.navigation3.ui.NavDisplay(
                            backStack = backStack,
                            modifier = modifier,
                            entryDecorators = actualDecorators,
                            entryProvider = provider
                        )
                    } else {
                        logW(TAG) { "[NavDisplay] entryProvider 为 null，跳过渲染" }
                    }
                }
            }
        )
    }

    companion object {
        /** 用于标记 entryDecorators 中的 saveable state holder 令牌 */
        const val SAVEABLE_STATE_HOLDER_TOKEN = "SAVEABLE_STATE_HOLDER"

        /**
         * 注入导航相关 API 到 Lua 全局环境
         * 在 ComposeBridgeInstance.current.inject() 中调用
         */
        fun injectNavigationApis(L: LuaState) {
            logD(TAG) { "[inject] 开始注入导航 API" }
            registerEntryProvider(L)
            registerNavBackStack(L)
            registerSaveableStateHolder(L)
            logD(TAG) { "[inject] 导航 API 注入完成" }
        }

        /**
         * 注册 compose.entryProvider(routes) — 创建路由 → 页面内容的映射
         *
         * Lua 用法：
         *   local provider = compose.entryProvider({
         *       Home = function() return compose.Surface { ... } end,
         *       Detail = function() return compose.Surface { ... } end
         *   })
         *
         * 返回的 provider 是一个 Java lambda (Any) -> NavEntry<*>，
         * NavDisplay 在需要渲染某个路由时调用它，传入 route key 字符串。
         * 每个 NavEntry 内部调用 Lua 函数获取 ComposeNode 并通过 ComposeRenderer 渲染。
         */
        private fun registerEntryProvider(L: LuaState) {
            L.pushJavaFunction(object : JavaFunction(L) {
                override fun execute(): Int {
                    val top = L.getTop()
                    if (top < 2 || !L.isTable(2)) {
                        logW(TAG) { "[entryProvider] 参数不是 Lua 表" }
                        L.pushNil(); return 1
                    }

                    // 遍历 Lua 表，收集 routeName → LuaObject 映射
                    val routeMap = mutableMapOf<String, LuaObject>()
                    L.pushNil()
                    while (L.next(2) != 0) {
                        val routeName = L.toString(-2)
                        if (L.isFunction(-1)) {
                            routeMap[routeName] = L.getLuaObject(-1)
                            logD(TAG) { "[entryProvider] 注册路由: $routeName" }
                        }
                        L.pop(1)
                    }

                    if (routeMap.isEmpty()) {
                        logW(TAG) { "[entryProvider] 路由表为空" }
                        L.pushNil(); return 1
                    }

                    // 创建 entryProvider lambda: (Any) -> NavEntry<*>
                    val provider: (Any) -> NavEntry<*> = { keyRaw ->
                        val routeName = keyRaw.toString()
                        val luaFunc = routeMap[routeName]
                            ?: error("未找到路由 '$routeName' 对应的 Composable 函数")

                        NavEntry(keyRaw) {
                            // 调用 Lua 函数构建 ComposeNode 树
                            val result = try {
                                luaFunc.call()
                            } catch (e: Exception) {
                                logE(TAG) { "[entryProvider] 路由 '$routeName' 渲染失败: ${e.message}" }
                                ComposeNode(
                                    type = "Text",
                                    props = mapOf(
                                        "text" to "[导航] $routeName - ${e.message}",
                                        "color" to 0xFFFF0000.toLong()
                                    )
                                )
                            }
                            if (result is ComposeNode) {
                                ComposeRenderer.Render(result)
                            } else {
                                logW(TAG) { "[entryProvider] 路由 '$routeName' 返回了非 ComposeNode: ${result?.javaClass?.name}" }
                            }
                        }
                    }

                    L.pushJavaObject(provider)
                    return 1
                }
            })
            L.setField(-2, "entryProvider")
        }

        /**
         * 注册 compose.rememberNavBackStack(initialKeys) — 创建导航回退栈
         *
         * Lua 用法：
         *   local backStack = compose.rememberNavBackStack({"Home"})
         *   backStack.add("Detail")                    -- 导航到 Detail
         *   backStack.removeAt(backStack.size() - 1)   -- 返回上一页
         *
         * 返回 NavBackStack 实例，修改时自动触发 Compose 重组。
         * 注意：Lua 中必须使用 . 语法而非 : 语法，因为这是 Java 对象。
         */
        private fun registerNavBackStack(L: LuaState) {
            L.pushJavaFunction(object : JavaFunction(L) {
                override fun execute(): Int {
                    val top = L.getTop()

                    // 始终返回同一个缓存实例，避免每次 main() 重新执行时
                    // 创建新实例导致 NavDisplay 内部状态重置
                    if (ComposeBridgeInstance.current.navBackStackCache.isNotEmpty()) {
                        val cached = ComposeBridgeInstance.current.navBackStackCache[0]
                        logD(TAG) { "[rememberNavBackStack] 返回缓存实例, size=${cached.size}" }
                        L.pushJavaObject(cached)
                        return 1
                    }

                    val initialKeys = mutableListOf<Any>()

                    if (top >= 2) {
                        when {
                            L.isTable(2) -> {
                                val len = L.rawLen(2)
                                if (len > 0) {
                                    for (i in 1..len) {
                                        L.pushInteger(i.toLong()); L.getTable(2)
                                        try {
                                            initialKeys.add(L.toJavaObject(-1) ?: L.toString(-1))
                                        } catch (_: Exception) {
                                            initialKeys.add(L.toString(-1))
                                        }
                                        L.pop(1)
                                    }
                                }
                            }
                            L.isString(2) -> {
                                initialKeys.add(L.toString(2))
                            }
                        }
                    }

                    val backStack = NavBackStack(initialKeys) {
                        ComposeBridgeInstance.current.scheduleRefresh()
                    }
                    ComposeBridgeInstance.current.navBackStackCache.add(backStack)
                    // 注册为活跃回退栈，供 LuaActivity.onBackPressed 使用
                    ComposeBridgeInstance.current.activeBackStack = backStack
                    logD(TAG) { "[rememberNavBackStack] 创建回退栈, 初始键: $initialKeys" }
                    L.pushJavaObject(backStack)
                    return 1
                }
            })
            L.setField(-2, "rememberNavBackStack")
        }

        /**
         * 注册 compose.rememberSaveableStateHolderNavEntryDecorator()
         * 返回令牌字符串 "SAVEABLE_STATE_HOLDER"，在 NavDisplay 渲染时替换为真正的
         * rememberSaveableStateHolderNavEntryDecorator<Any>()。
         *
         * 这是一种延迟实例化模式 — 因为 rememberSaveableStateHolderNavEntryDecorator
         * 需要在 @Composable 上下文中调用，而 Lua 端无法直接创建 Composable 函数。
         */
        private fun registerSaveableStateHolder(L: LuaState) {
            L.pushJavaFunction(object : JavaFunction(L) {
                override fun execute(): Int {
                    L.pushString(SAVEABLE_STATE_HOLDER_TOKEN)
                    return 1
                }
            })
            L.setField(-2, "rememberSaveableStateHolderNavEntryDecorator")
        }
    }
}