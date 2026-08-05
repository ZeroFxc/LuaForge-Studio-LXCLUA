package com.nirithy.luacompose.preview

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.luajava.JavaFunction
import com.luajava.LuaState
import com.luajava.LuaStateFactory
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.bridge.LazyNamespace
import com.nirithy.luacompose.bridge.registerAnimationSpecs
import com.nirithy.luacompose.bridge.registerAnimationTransitions
import com.nirithy.luacompose.bridge.registerAnimatableFactory
import com.nirithy.luacompose.bridge.registerAnimateColorFactory
import com.nirithy.luacompose.bridge.registerAnimateDpFactory
import com.nirithy.luacompose.bridge.registerAnimateFloatFactory
import com.nirithy.luacompose.bridge.registerAnimateFloatRecomposeFactory
import com.nirithy.luacompose.bridge.registerAnimateFloatRecomposeTweenFactory
import com.nirithy.luacompose.bridge.registerAnimateFloatTweenFactory
import com.nirithy.luacompose.bridge.registerAlignmentTable
import com.nirithy.luacompose.bridge.registerArrangementEnhancements
import com.nirithy.luacompose.bridge.registerArrangementTable
import com.nirithy.luacompose.bridge.registerBackgroundColor
import com.nirithy.luacompose.bridge.registerBrushRadialGradient
import com.nirithy.luacompose.bridge.registerColorCompanion
import com.nirithy.luacompose.bridge.registerColorHelper
import com.nirithy.luacompose.bridge.registerCoroutineScopeFactory
import com.nirithy.luacompose.bridge.registerDelayTool
import com.nirithy.luacompose.bridge.registerDerivedStateFactory
import com.nirithy.luacompose.bridge.registerDisposableEffectApi
import com.nirithy.luacompose.bridge.registerDpHelper
import com.nirithy.luacompose.bridge.registerDumpTool
import com.nirithy.luacompose.bridge.registerEasingTable
import com.nirithy.luacompose.bridge.registerFontWeightTable
import com.nirithy.luacompose.bridge.registerGesturesNamespace
import com.nirithy.luacompose.bridge.registerGraphicsFactories
import com.nirithy.luacompose.bridge.registerKeyApi
import com.nirithy.luacompose.bridge.registerLaunchedEffectApi
import com.nirithy.luacompose.bridge.registerLocalConfiguration
import com.nirithy.luacompose.bridge.registerLocalContext
import com.nirithy.luacompose.bridge.registerLocalDensity
import com.nirithy.luacompose.bridge.registerModifierFactory
import com.nirithy.luacompose.bridge.registerMutableState
import com.nirithy.luacompose.bridge.registerPathFactory
import com.nirithy.luacompose.bridge.registerReflectHelpers
import com.nirithy.luacompose.bridge.registerRememberFactory
import com.nirithy.luacompose.bridge.registerRememberKeysFactory
import com.nirithy.luacompose.bridge.registerRenderFunction
import com.nirithy.luacompose.bridge.registerShapeFactories
import com.nirithy.luacompose.bridge.registerShowSnackbar
import com.nirithy.luacompose.bridge.registerSnackbarHostState
import com.nirithy.luacompose.bridge.registerSpHelper
import com.nirithy.luacompose.bridge.registerSpringConstants
import com.nirithy.luacompose.bridge.registerSpringFactory
import com.nirithy.luacompose.bridge.registerStateFactory
import com.nirithy.luacompose.bridge.registerStrokeTable
import com.nirithy.luacompose.bridge.registerThemeTable
import com.nirithy.luacompose.bridge.registerTimeHelper
import com.nirithy.luacompose.bridge.registerTweenFactory
import com.nirithy.luacompose.bridge.registerWithContext
import com.nirithy.luacompose.logD
import com.nirithy.luacompose.logE
import com.nirithy.luacompose.logI
import com.nirithy.luacompose.logW
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.node.assignNodePaths

/**
 * 预览沙箱环境
 *
 * 创建独立的 LuaState 执行 LuaCompose 代码，与主 ComposeBridge 状态完全隔离。
 * 用于在编辑器中实时预览 Lua UI，不需要启动新的 Activity。
 *
 * 安全特性：
 * - 禁用危险 API（os.execute、io.open 等文件系统/系统调用）
 * - 提供模拟的 activity 桩对象
 * - 超时保护防止死循环阻塞 UI
 *
 * @param context Android Context，用于资源访问
 */
class PreviewSandbox(context: Context) {
    private val TAG = "PreviewSandbox"

    /** 使用 ApplicationContext 避免 Activity 销毁导致 Context 失效 */
    private val appContext: Context = context.applicationContext

    /** 沙箱独立的 LuaState */
    @Volatile
    private var luaState: LuaState? = null

    /** 沙箱独立的 ComposeBridgeInstance */
    @Volatile
    private var bridge: PreviewComposeBridge? = null

    /** 最后一次解析得到的根节点 */
    @Volatile
    private var rootNode: ComposeNode? = null

    /** 最后一次错误信息 */
    @Volatile
    private var errorMessage: String? = null

    /** 沙箱是否已销毁（volatile 保证多线程可见性） */
    @Volatile
    private var destroyed = false

    /** 是否正在执行代码（防止并发执行和销毁时执行） */
    @Volatile
    private var executing = false

    /** 执行超时时间（毫秒） */
    private val EXECUTION_TIMEOUT_MS = 3000L

    /** 执行锁，防止并发执行和执行期间被销毁 */
    private val executionLock = Any()

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        /** 标记组件是否已全局注册（只需注册一次） */
        @Volatile
        private var componentsRegistered = false
    }

    init {
        initSandbox()
    }

    /**
     * 初始化沙箱环境：创建 LuaState、注入 compose API、禁用危险函数
     */
    private fun initSandbox() {
        logD(TAG) { "初始化预览沙箱..." }

        val L = LuaStateFactory.newLuaState()
        luaState = L
        L.openLibs()

        // 创建沙箱专用的 bridge 实例
        val previewBridge = PreviewComposeBridge()
        bridge = previewBridge
        previewBridge.setAndroidContext(appContext)

        // 全局注册组件（只需注册一次，PluginRegistry/ComponentRegistry 是全局的）
        ensureComponentsRegistered()

        // 注入模拟的 activity 对象（桩实现）
        injectStubActivity(L)

        // 在 bridge 上下文中注入 compose API
        ComposeBridgeInstance.withBridge(previewBridge) {
            injectComposeApis(L, previewBridge)
            disableDangerousApis(L)
        }

        logD(TAG) { "预览沙箱初始化完成" }
    }

    /**
     * 确保组件已全局注册（幂等）
     */
    @Synchronized
    private fun ensureComponentsRegistered() {
        if (componentsRegistered) return
        componentsRegistered = true

        logI(TAG) { "全局注册组件插件..." }
        com.nirithy.luacompose.plugin.PluginRegistry.registerAll(
            com.nirithy.luacompose.component.LayoutComponents,
            com.nirithy.luacompose.component.DisplayComponents,
            com.nirithy.luacompose.component.InputComponents,
            com.nirithy.luacompose.component.ContainerComponents,
            com.nirithy.luacompose.component.BoxWithConstraintsComponent,
            com.nirithy.luacompose.component.IconComponent,
            com.nirithy.luacompose.animation.AnimationPlugin,
            com.nirithy.luacompose.render.CanvasPlugin,
            com.nirithy.luacompose.effect.EffectPlugin,
            com.nirithy.luacompose.navigation.Navigation3Plugin(),
            com.nirithy.luacompose.component.AndroidViewComponent,
            com.nirithy.luacompose.component.ComplementComponents,
            com.nirithy.luacompose.component.BackHandlerComponent,
        )
        com.nirithy.luacompose.plugin.PluginRegistry.applyToComponentRegistry()
        logI(TAG) { "组件注册完成，共 ${com.nirithy.luacompose.render.ComponentRegistry.componentCount()} 个" }
    }

    /**
     * 注入模拟的 activity 桩对象，避免 Lua 代码因找不到 activity 全局变量而崩溃
     */
    private fun injectStubActivity(L: LuaState) {
        L.newTable()

        // 提供简单的桩方法
        addStubFunc(L, "getColor") { L.pushNumber(0.0); 1 }
        addStubFunc(L, "getLuaDir") { L.pushString(""); 1 }
        addStubFunc(L, "getLuaPath") { L.pushString(""); 1 }
        addStubFunc(L, "runOnUiThread") { 0 }
        addStubFunc(L, "showToast") { 0 }

        L.setGlobal("activity")

        // 同时设置 this 全局变量指向桩
        L.getGlobal("activity")
        L.setGlobal("this")
    }

    /**
     * 向栈顶 table 添加一个桩函数
     */
    private fun addStubFunc(L: LuaState, name: String, handler: (LuaState) -> Int) {
        object : JavaFunction(L) {
            override fun execute(): Int = handler(L)
        }.let {
            L.pushJavaFunction(it)
            L.setField(-2, name)
        }
    }

    /**
     * 注入 Compose API 到 Lua 环境
     */
    private fun injectComposeApis(L: LuaState, bridgeInstance: ComposeBridgeInstance) {
        logD(TAG) { "注入 compose API 到沙箱..." }

        bridgeInstance.resetState()

        // 注册 KSP 生成的组件
        bridgeInstance.registerKspComponents()

        // 创建 compose 命名空间
        LazyNamespace.create(L, "androidx.compose")

        // 注入所有 API 工厂函数
        with(bridgeInstance) {
            registerModifierFactory(L)
            registerStateFactory(L)
            registerMutableState(L)
            registerRememberFactory(L)
            registerRememberKeysFactory(L)
            registerDerivedStateFactory(L)
            registerSpringFactory(L)
            registerTweenFactory(L)
            registerAnimateFloatFactory(L)
            registerAnimateFloatRecomposeFactory(L)
            registerAnimateFloatTweenFactory(L)
            registerAnimateFloatRecomposeTweenFactory(L)
            registerDpHelper(L)
            registerSpHelper(L)
            registerColorHelper(L)
            registerTimeHelper(L)
            registerGraphicsFactories(L)
            registerBackgroundColor(L)
            registerThemeTable(L)
            registerLocalDensity(L)
            registerLocalContext(L)
            registerLocalConfiguration(L)
            registerWithContext(L)
            registerGesturesNamespace(L)
            registerPathFactory(L)
            registerCoroutineScopeFactory(L)
            registerAnimatableFactory(L)
            registerEasingTable(L)
            registerRenderFunction(L)
            registerReflectHelpers(L)
            registerDumpTool(L)
            registerDelayTool(L)
            registerShapeFactories(L)
            registerBrushRadialGradient(L)
            registerStrokeTable(L)
            registerColorCompanion(L)
            registerSnackbarHostState(L)
            registerShowSnackbar(L)

            // 枚举表
            registerFontWeightTable(L)
            registerArrangementTable(L)
            registerAlignmentTable(L)
            registerArrangementEnhancements(L)

            // 动画 API
            registerAnimationSpecs(L)
            registerAnimationTransitions(L)
            registerSpringConstants(L)
            registerAnimateColorFactory(L)
            registerAnimateDpFactory(L)

            // 副作用 API
            registerLaunchedEffectApi(L)
            registerDisposableEffectApi(L)
            registerKeyApi(L)

            // 导航 API
            com.nirithy.luacompose.navigation.Navigation3Plugin.injectNavigationApis(L)
        }

        // 设置全局 compose 表（栈顶此时是 compose 命名空间表）
        L.setGlobal("compose")

        logD(TAG) { "compose API 注入完成" }
    }

    /**
     * 禁用沙箱中的危险 API
     */
    private fun disableDangerousApis(L: LuaState) {
        logD(TAG) { "禁用危险 API..." }

        // 禁用 os 表中的危险函数
        L.getGlobal("os")
        if (L.type(-1) == LuaState.LUA_TTABLE) {
            val disabledOsFuncs = listOf("execute", "remove", "rename", "exit", "setlocale")
            for (funcName in disabledOsFuncs) {
                addDisabledFunc(L, funcName, "os")
            }
            addStubFunc(L, "getenv") { L.pushString(""); 1 }
        }
        L.pop(1)

        // 禁用 io 表
        L.getGlobal("io")
        if (L.type(-1) == LuaState.LUA_TTABLE) {
            val disabledIoFuncs = listOf("open", "popen", "lines", "tmpfile")
            for (funcName in disabledIoFuncs) {
                addDisabledFunc(L, funcName, "io")
            }
            addStubFunc(L, "write") { 0 }
        }
        L.pop(1)

        // 禁用 loadfile/dofile
        for (funcName in listOf("loadfile", "dofile")) {
            object : JavaFunction(L) {
                override fun execute(): Int {
                    L.pushNil()
                    L.pushString("沙箱中禁止调用 $funcName")
                    return 2
                }
            }.let {
                L.pushJavaFunction(it)
                L.setGlobal(funcName)
            }
        }

        // 禁用 require
        object : JavaFunction(L) {
            override fun execute(): Int {
                val modName = if (L.top >= 2 && L.isString(2)) L.toString(2) else "unknown"
                L.pushNil()
                L.pushString("沙箱中禁止 require(\"$modName\")")
                return 2
            }
        }.let {
            L.pushJavaFunction(it)
            L.setGlobal("require")
        }

        // 覆盖 debug 库
        object : JavaFunction(L) {
            override fun execute(): Int { return 0 }
        }.let {
            L.pushJavaFunction(it)
            L.setGlobal("debug")
        }

        logD(TAG) { "危险 API 已禁用" }
    }

    /**
     * 向栈顶 table 添加一个被禁用的函数，调用时返回错误
     */
    private fun addDisabledFunc(L: LuaState, funcName: String, tableName: String) {
        object : JavaFunction(L) {
            override fun execute(): Int {
                L.pushNil()
                L.pushString("沙箱中禁止调用 $tableName.$funcName")
                return 2
            }
        }.let {
            L.pushJavaFunction(it)
            L.setField(-2, funcName)
        }
    }

    /**
     * 执行 Lua 代码字符串
     *
     * 线程安全：同一时间只有一个 executeCode 在执行，且执行期间不会被 destroy 中断。
     * 应该在后台线程调用（如 Dispatchers.IO），避免阻塞主线程。
     *
     * @param code 要执行的 Lua 代码
     * @return true 执行成功（有渲染函数并成功解析节点树），false 执行失败
     */
    fun executeCode(code: String): Boolean {
        synchronized(executionLock) {
            if (destroyed) {
                errorMessage = "沙箱已销毁"
                logE(TAG) { errorMessage!! }
                return false
            }

            val L = luaState
            val previewBridge = bridge
            if (L == null || previewBridge == null) {
                errorMessage = "沙箱未初始化"
                return false
            }

            executing = true
            previewBridge.suppressRefresh = true  // 防止执行期间 LuaState 被多线程并发访问
            logD(TAG) { "开始执行 Lua 代码, 长度=${code.length}" }

            // 重置状态
            rootNode = null
            errorMessage = null

            // 使用 CountDownLatch 实现真正的超时保护（在后台线程有效）
            val latch = java.util.concurrent.CountDownLatch(1)
            var timedOut = false
            val timeoutThread = Thread({
                try {
                    val reached = latch.await(EXECUTION_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (!reached) {
                        timedOut = true
                        logE(TAG) { "代码执行超时（${EXECUTION_TIMEOUT_MS}ms）" }
                    }
                } catch (_: InterruptedException) {}
            }, "sandbox-timeout")
            timeoutThread.isDaemon = true
            timeoutThread.start()

            try {
                val result = ComposeBridgeInstance.withBridge(previewBridge) {
                    synchronized(previewBridge.luaLock) {
                        if (destroyed) {
                            errorMessage = "执行期间沙箱被销毁"
                            return@withBridge false
                        }

                        previewBridge.resetState()

                        // 加载并执行用户代码
                        val loadResult = L.LloadString(code)
                        if (loadResult != 0) {
                            val err = try { L.toString(-1) } catch (e: Exception) { "语法错误" }
                            errorMessage = "语法错误: $err"
                            L.pop(1)
                            logE(TAG) { "代码加载失败: $errorMessage" }
                            return@withBridge false
                        }

                        val callResult = L.pcall(0, 0, 0)
                        if (callResult != 0) {
                            val err = try { L.toString(-1) } catch (e: Exception) { "运行时错误" }
                            errorMessage = "运行时错误: $err"
                            L.pop(1)
                            logE(TAG) { "代码执行失败: $errorMessage" }
                            return@withBridge false
                        }

                        if (timedOut || destroyed) {
                            errorMessage = if (timedOut) "代码执行超时（${EXECUTION_TIMEOUT_MS}ms）" else "执行期间沙箱被销毁"
                            return@withBridge false
                        }

                        // 检查是否注册了渲染函数
                        val renderFunc = previewBridge.activeLuaFunc
                        if (renderFunc == null) {
                            errorMessage = "代码中未找到 compose.render() 调用"
                            logW(TAG) { errorMessage!! }
                            return@withBridge false
                        }

                        // 重置状态索引
                        previewBridge.stateIndex = 0
                        previewBridge.rememberIndex = 0
                        previewBridge.animIndex = 0
                        previewBridge.animDpIndex = 0
                        previewBridge.animColorIndex = 0
                        previewBridge.navBackStackIndex = 0

                        // ★ ComposeScope：开始新的渲染周期
                        previewBridge.currentScope = previewBridge.rootScope
                        previewBridge.rootScope.beginCycle()
                        com.nirithy.luacompose.state.StateWrapper.globalBuildCycle++
                        val allStates = previewBridge.rootScope.collectAllStates()
                        for (sw in allStates) {
                            sw.currentBuildCycle = com.nirithy.luacompose.state.StateWrapper.globalBuildCycle
                        }

                        // 调用渲染函数获取节点树
                        val renderResult = try {
                            renderFunc.call()
                        } catch (e: Exception) {
                            errorMessage = "渲染函数执行异常: ${e.message}"
                            logE(TAG, { errorMessage!! }, e)
                            return@withBridge false
                        }

                        if (timedOut || destroyed) {
                            errorMessage = if (timedOut) "渲染超时" else "执行期间沙箱被销毁"
                            return@withBridge false
                        }

                        when (renderResult) {
                            is ComposeNode -> {
                                val nodeWithPaths = assignNodePaths(renderResult, "0")
                                rootNode = nodeWithPaths
                                previewBridge.rootState.value = nodeWithPaths
                                logD(TAG) { "代码执行成功! 根节点=${nodeWithPaths.type}, 子节点=${nodeWithPaths.children.size}个" }
                                true
                            }
                            else -> {
                                errorMessage = "render 函数未返回 ComposeNode，返回了 ${renderResult?.javaClass?.simpleName ?: "nil"}"
                                logW(TAG) { errorMessage!! }
                                false
                            }
                        }.also {
                            // ★ ComposeScope：结束渲染周期，清理不再被访问的状态
                            previewBridge.rootScope.endCycle(com.nirithy.luacompose.state.StateWrapper.globalBuildCycle)
                        }
                    }
                }
                return result
            } catch (e: Throwable) {
                errorMessage = "执行异常: ${e.message}"
                logE(TAG, { errorMessage!! }, e)
                return false
            } finally {
                latch.countDown()
                timeoutThread.interrupt()
                previewBridge.suppressRefresh = false  // 恢复刷新，允许后续交互触发刷新
                executing = false
            }
        }
    }

    /**
     * 获取解析后的根节点
     * @return ComposeNode 根节点，未执行或执行失败返回 null
     */
    fun getRootNode(): ComposeNode? = rootNode

    /**
     * 获取错误信息
     * @return 错误信息字符串，无错误返回 null
     */
    fun getError(): String? = errorMessage

    /**
     * 获取沙箱使用的 bridge 实例
     */
    fun getBridge(): ComposeBridgeInstance? = bridge

    /**
     * 销毁沙箱，释放 LuaState 资源
     */
    fun destroy() {
        synchronized(executionLock) {
            if (destroyed) return
            logD(TAG) { "销毁预览沙箱" }
            destroyed = true

            // 等待正在执行的代码完成（最多等待 2 秒）
            if (executing) {
                logD(TAG) { "等待代码执行完成..." }
                var waitCount = 0
                while (executing && waitCount < 20) {
                    try {
                        (executionLock as Object).wait(100)
                    } catch (_: InterruptedException) {
                        break
                    }
                    waitCount++
                }
                if (executing) {
                    logE(TAG) { "等待执行超时，强制销毁" }
                }
            }

            try {
                bridge?.resetState()
                bridge = null
            } catch (e: Exception) {
                logE(TAG, { "resetState 异常: ${e.message}" }, e)
            }

            try {
                rootNode?.release()
                rootNode = null
            } catch (e: Exception) {
                logE(TAG, { "release rootNode 异常: ${e.message}" }, e)
            }

            try {
                luaState?.close()
                luaState = null
            } catch (e: Exception) {
                logE(TAG, { "close LuaState 异常: ${e.message}" }, e)
            }

            errorMessage = null
        }
    }

    protected fun finalize() {
        if (!destroyed) {
            destroy()
        }
    }
}

/**
 * 预览专用的 ComposeBridge 实现
 */
internal class PreviewComposeBridge : ComposeBridgeInstance() {
    override val TAG: String = "PreviewComposeBridge"
}
