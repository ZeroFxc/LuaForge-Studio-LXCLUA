package com.nirithy.luacompose.bridge

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import com.nirithy.luacompose.*
import com.nirithy.lxclua.DebugLogger
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.nirithy.luacompose.animation.AnimationPlugin
import com.nirithy.luacompose.component.AndroidViewComponent
import com.nirithy.luacompose.component.BoxWithConstraintsComponent
import com.nirithy.luacompose.component.ComplementComponents
import com.nirithy.luacompose.component.ContainerComponents
import com.nirithy.luacompose.component.DisplayComponents
import com.nirithy.luacompose.component.IconComponent
import com.nirithy.luacompose.component.InputComponents
import com.nirithy.luacompose.component.LayoutComponents
import com.nirithy.luacompose.effect.EffectPlugin
import com.nirithy.luacompose.modifier.ModifierChain
import com.nirithy.luacompose.navigation.Navigation3Plugin
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.PluginRegistry
import com.nirithy.luacompose.render.CanvasPlugin
import com.nirithy.luacompose.render.ComponentRegistry
import com.nirithy.luacompose.state.StateWrapper
import com.luajava.JavaFunction
import com.luajava.LuaObject
import com.luajava.LuaState

/**
 * Compose-Lua 桥接核心
 * 将 Jetpack Compose 组件库以函数式声明风格注入到 Lua 环境中。
 *
 * 职责：状态管理、注入编排、刷新调度、组件工厂注册。
 * Lua API 注入器（工厂函数）→ ComposeInjectors.kt
 * 节点解析器 → NodeParser.kt
 */
object ComposeBridge {
    private const val TAG = "ComposeBridge"

    // ========== 公开状态 ==========
    val rootState: MutableState<ComposeNode?> = mutableStateOf(null)
    val luaError: MutableState<String?> = mutableStateOf(null)
    /** mutableState 变更时递增，触发 ComposeHost 重组（不重建 Lua 树） */
    val recomposeTrigger: MutableState<Long> = mutableStateOf(0L)

    // ========== 内部状态 ==========

    /** 当前活跃的 Lua 渲染函数引用 */
    internal var activeLuaFunc: LuaObject? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // 状态缓存：每次刷新时按调用顺序返回同一 StateWrapper
    internal val stateCache = mutableListOf<StateWrapper<*>>()
    internal var stateIndex = 0

    // remember 缓存：按调用顺序缓存计算结果
    internal val rememberCache = mutableListOf<Any?>()
    internal var rememberIndex = 0

    // NavBackStack 缓存：按调用顺序返回同一实例
    internal val navBackStackCache = mutableListOf<com.nirithy.luacompose.navigation.NavBackStack>()
    internal var navBackStackIndex = 0

    // ========== SharedTransition 作用域栈 ==========
    /** 当前活跃的 SharedTransitionScope 栈，每层 SharedTransitionLayout 入栈，用于 sharedElement 修饰符获取上下文 */
    private val activeSharedTransitionScopes = mutableListOf<SharedTransitionScope>()
    /** 当前活跃的 AnimatedVisibilityScope 栈，用于 sharedElement 的 animatedVisibilityScope 参数 */
    private val activeAnimatedVisibilityScopes = mutableListOf<AnimatedVisibilityScope>()

    /** 获取当前活跃的 SharedTransitionScope */
    fun getActiveSharedTransitionScope(): SharedTransitionScope? =
        activeSharedTransitionScopes.lastOrNull()

    /** 获取当前活跃的 AnimatedVisibilityScope */
    fun getActiveAnimatedVisibilityScope(): AnimatedVisibilityScope? =
        activeAnimatedVisibilityScopes.lastOrNull()

    /** 入栈 SharedTransitionScope */
    fun pushActiveSharedTransitionScope(scope: SharedTransitionScope) {
        activeSharedTransitionScopes.add(scope)
    }

    /** 出栈 SharedTransitionScope */
    fun popActiveSharedTransitionScope() {
        if (activeSharedTransitionScopes.isNotEmpty()) {
            activeSharedTransitionScopes.removeAt(activeSharedTransitionScopes.lastIndex)
        }
    }

    /** 入栈 AnimatedVisibilityScope */
    fun pushActiveAnimatedVisibilityScope(scope: AnimatedVisibilityScope) {
        activeAnimatedVisibilityScopes.add(scope)
    }

    /** 出栈 AnimatedVisibilityScope */
    fun popActiveAnimatedVisibilityScope() {
        if (activeAnimatedVisibilityScopes.isNotEmpty()) {
            activeAnimatedVisibilityScopes.removeAt(activeAnimatedVisibilityScopes.lastIndex)
        }
    }

    /**
     * 当前活跃的导航回退栈，由 NavBackStack 注册
     * LuaActivity 的 onBackPressed 检查此栈，自动 pop
     */
    @Volatile
    var activeBackStack: com.nirithy.luacompose.navigation.NavBackStack? = null

    // 背景色
    val backgroundColor: MutableState<Color> = mutableStateOf(Color.Unspecified)

    // 主题色缓存（预设 Material3 浅色主题默认值）
    val themeColors: MutableState<Map<String, Long>> = mutableStateOf(mapOf(
        "primary" to 0xFF6750A4L, "onPrimary" to 0xFFFFFFFFL,
        "primaryContainer" to 0xFFEADDFFL, "onPrimaryContainer" to 0xFF21005DL,
        "secondary" to 0xFF625B71L, "onSecondary" to 0xFFFFFFFFL,
        "secondaryContainer" to 0xFFE8DEF8L, "onSecondaryContainer" to 0xFF1D192BL,
        "tertiary" to 0xFF7D5260L, "onTertiary" to 0xFFFFFFFFL,
        "tertiaryContainer" to 0xFFFFD8E4L, "onTertiaryContainer" to 0xFF31111DL,
        "background" to 0xFFFEF7FFL, "onBackground" to 0xFF1C1B1FL,
        "surface" to 0xFFFEF7FFL, "onSurface" to 0xFF1C1B1FL,
        "surfaceVariant" to 0xFFE7E0ECL, "onSurfaceVariant" to 0xFF49454FL,
        "error" to 0xFFB3261EL, "onError" to 0xFFFFFFFFL,
        "errorContainer" to 0xFFF9DEDCL, "onErrorContainer" to 0xFF410E0BL,
        "outline" to 0xFF79747EL, "outlineVariant" to 0xFFCAC4D0L,
        "inverseSurface" to 0xFF313033L, "inverseOnSurface" to 0xFFF4EFF4L,
        "inversePrimary" to 0xFFD0BCFFL,
    ))

    // 字体排版缓存
    val themeTypography: MutableState<Map<String, Map<String, Float>>> = mutableStateOf(mapOf(
        "displayLarge" to mapOf("fontSize" to 57f, "fontWeight" to 400f, "lineHeight" to 64f, "letterSpacing" to 0f),
        "displayMedium" to mapOf("fontSize" to 45f, "fontWeight" to 400f, "lineHeight" to 52f, "letterSpacing" to 0f),
        "displaySmall" to mapOf("fontSize" to 36f, "fontWeight" to 400f, "lineHeight" to 44f, "letterSpacing" to 0f),
        "headlineLarge" to mapOf("fontSize" to 32f, "fontWeight" to 400f, "lineHeight" to 40f, "letterSpacing" to 0f),
        "headlineMedium" to mapOf("fontSize" to 28f, "fontWeight" to 400f, "lineHeight" to 36f, "letterSpacing" to 0f),
        "headlineSmall" to mapOf("fontSize" to 24f, "fontWeight" to 400f, "lineHeight" to 32f, "letterSpacing" to 0f),
        "titleLarge" to mapOf("fontSize" to 22f, "fontWeight" to 400f, "lineHeight" to 28f, "letterSpacing" to 0f),
        "titleMedium" to mapOf("fontSize" to 16f, "fontWeight" to 500f, "lineHeight" to 24f, "letterSpacing" to 0.15f),
        "titleSmall" to mapOf("fontSize" to 14f, "fontWeight" to 500f, "lineHeight" to 20f, "letterSpacing" to 0.1f),
        "bodyLarge" to mapOf("fontSize" to 16f, "fontWeight" to 400f, "lineHeight" to 24f, "letterSpacing" to 0.5f),
        "bodyMedium" to mapOf("fontSize" to 14f, "fontWeight" to 400f, "lineHeight" to 20f, "letterSpacing" to 0.25f),
        "bodySmall" to mapOf("fontSize" to 12f, "fontWeight" to 400f, "lineHeight" to 16f, "letterSpacing" to 0.4f),
        "labelLarge" to mapOf("fontSize" to 14f, "fontWeight" to 500f, "lineHeight" to 20f, "letterSpacing" to 0.1f),
        "labelMedium" to mapOf("fontSize" to 12f, "fontWeight" to 500f, "lineHeight" to 16f, "letterSpacing" to 0.5f),
        "labelSmall" to mapOf("fontSize" to 11f, "fontWeight" to 500f, "lineHeight" to 16f, "letterSpacing" to 0.5f),
    ))

    // 形状缓存
    val themeShapes: MutableState<Map<String, Map<String, Float>>> = mutableStateOf(mapOf(
        "extraSmall" to mapOf("topStart" to 4f, "topEnd" to 4f, "bottomStart" to 4f, "bottomEnd" to 4f),
        "small" to mapOf("topStart" to 8f, "topEnd" to 8f, "bottomStart" to 8f, "bottomEnd" to 8f),
        "medium" to mapOf("topStart" to 12f, "topEnd" to 12f, "bottomStart" to 12f, "bottomEnd" to 12f),
        "large" to mapOf("topStart" to 16f, "topEnd" to 16f, "bottomStart" to 16f, "bottomEnd" to 16f),
        "extraLarge" to mapOf("topStart" to 28f, "topEnd" to 28f, "bottomStart" to 28f, "bottomEnd" to 28f),
    ))

    // 屏幕密度缓存
    val density: MutableState<Float> = mutableStateOf(2.0f)

    // Android 上下文（由 LuaActivity 注入）
    @JvmField
    var androidContext: Context? = null
    var androidConfiguration: Configuration? = null

    /** 由 LuaActivity 调用，注入 Android 上下文和配置 */
    fun setAndroidContext(context: Context) {
        androidContext = context
        androidConfiguration = context.resources.configuration
        density.value = context.resources.displayMetrics.density
        logD(TAG) { "[setAndroidContext] density=${density.value}, orientation=${androidConfiguration?.orientation}" }
    }

    // 动画状态缓存
    internal val animatedFloats = mutableListOf<AnimatedFloat>()
    internal var animIndex = 0

    // ========== 注入入口 ==========

    fun inject(L: LuaState) {
        logD("ComposeBridge") { "inject 开始, LuaState=${L.hashCode()}" }
        resetState()
        ComponentRegistry.clear()
        PluginRegistry.clear()

        logI(TAG) { "========== 开始注入 Compose API ==========" }

        // 注册插件 → 组件渲染器
        logD(TAG) { "[inject] 注册插件..." }
        PluginRegistry.registerAll(
            LayoutComponents, DisplayComponents, InputComponents,
            ContainerComponents, BoxWithConstraintsComponent, IconComponent,
            AnimationPlugin, CanvasPlugin, EffectPlugin, Navigation3Plugin(),
            AndroidViewComponent, ComplementComponents,
        )
        PluginRegistry.applyToComponentRegistry()
        logD(TAG) { "[inject] 组件渲染器注册完成，共 ${ComponentRegistry.componentCount()} 个" }

        // KSP 编译期生成的组件注册
        registerKspComponents()

        // 创建 compose 命名空间（根命名空间），子命名空间由 __index 懒加载
        LazyNamespace.create(L, "androidx.compose")

        // 注入所有 Lua API（工厂函数提取到 ComposeInjectors.kt）
        logD(TAG) { "[inject] 注入 Lua API..." }
        registerModifierFactory(L)
        registerStateFactory(L)
        registerMutableState(L)
        registerRememberFactory(L)
        registerRememberKeysFactory(L)
        registerDerivedStateFactory(L)
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

        // 副作用 API（LaunchedEffect / DisposableEffect / key）
        registerLaunchedEffectApi(L)
        registerDisposableEffectApi(L)
        registerKeyApi(L)

        // 快速路径组件
        registerFastPathComponents(L)

        // 导航 API
        Navigation3Plugin.injectNavigationApis(L)

        // 验证并设置全局 compose 表
        verifyAndSetGlobal(L)
    }

    private fun resetState() {
        activeLuaFunc = null
        rootState.value = null
        stateCache.clear(); stateIndex = 0
        rememberCache.clear(); rememberIndex = 0
        animatedFloats.clear(); animIndex = 0
        navBackStackCache.clear(); activeBackStack = null
        activeSharedTransitionScopes.clear()
        activeAnimatedVisibilityScopes.clear()
        backgroundColor.value = Color.Unspecified
        themeColors.value = themeColors.value // 重置为默认值
        themeTypography.value = themeTypography.value
        themeShapes.value = themeShapes.value
    }

    private fun registerKspComponents() {
        try {
            Class.forName("com.nirithy.luacompose.generated.GeneratedComponentRegistry")
                .getMethod("registerAll").invoke(null)
            logI(TAG) { "[inject] KSP 生成的组件已注册，共 ${ComponentRegistry.componentCount()} 个" }
        } catch (e: ClassNotFoundException) {
            logW(TAG) { "[inject] KSP 生成的组件不存在（首次编译可能未生成），回退到 DynamicRenderer" }
        } catch (e: Exception) {
            logW(TAG) { "[inject] KSP 生成的组件注册失败: ${e.message}" }
        }
    }

    private fun registerFastPathComponents(L: LuaState) {
        val components = listOf("Column", "Row", "Box", "LazyColumn", "LazyRow", "Text", "Button", "TextButton",
            "OutlinedButton", "IconButton", "TextField", "OutlinedTextField", "Checkbox", "Switch", "Slider",
            "Card", "Surface", "Scaffold", "Spacer", "Divider", "VerticalDivider",
            "AnimatedVisibility", "AnimatedContent", "Crossfade", "Canvas", "BoxWithConstraints",
            "Icon", "InfiniteTransition", "LaunchedEffect", "key", "DisposableEffect",
            "NavDisplay", "AndroidView", "FloatingActionButton", "AssistChip", "FilterChip",
            "InputChip", "SuggestionChip", "TabRow", "Tab", "ScrollableTabRow",
            "ModalNavigationDrawer", "SearchBar", "DatePicker", "DatePickerDialog",
            "TimePicker", "LinearProgressIndicator", "CircularProgressIndicator",
            "Badge", "BadgedBox", "SharedTransitionLayout")
        logD(TAG) { "[inject] 注册 ${components.size} 个快速路径组件" }
        for (c in components) registerComponentFactory(L, c, c)
    }

    private fun verifyAndSetGlobal(L: LuaState) {
        val composeIdx = L.getTop()
        L.getMetaTable(composeIdx)
        if (L.type(-1) == LuaState.LUA_TTABLE) {
            L.pushString("__index"); L.getTable(-2)
            logI(TAG) { "[inject] compose metatable __index 存在=${L.type(-1) != LuaState.LUA_TNIL}" }
            L.pop(2)
        } else {
            logE(TAG) { "[inject] compose 没有 metatable!" }
            L.pop(1)
        }
        L.setGlobal("compose")

        val verifyResult = L.LdoString("""
            local mt = getmetatable(compose)
            if mt == nil then error("FATAL: compose metatable is nil!") end
            if mt.__index == nil then error("FATAL: compose metatable.__index is nil!") end
            local test = compose.material3
            if type(test) ~= "table" then error("FATAL: compose.material3 返回了 " .. type(test) .. " 而不是 table!") end
        """.trimIndent())
        if (verifyResult != 0) {
            val errMsg = try { L.toString(-1) } catch (e: Exception) { "unknown" }
            logE(TAG) { "[inject] Lua验证失败: $errMsg" }
            L.pop(1)
        } else {
            logI(TAG) { "[inject] Lua验证: compose metatable 正常" }
        }
        logI(TAG) { "========== Compose API 注入完成 ==========" }
    }

    // ========== 组件工厂函数 ==========

    /** 注册单个组件工厂函数到 compose 命名空间 */
    private fun registerComponentFactory(L: LuaState, luaName: String, nodeType: String) {
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                try {
                    val top = L.getTop()
                    if (top < 2 || !L.isTable(2)) {
                        L.pushJavaObject(ComposeNode(type = nodeType)); return 1
                    }
                    val node = NodeParser.parseNodeTable(L, 2, nodeType)
                    logD(TAG) { "[组件:$luaName] 解析完成: props=${node.props.size}个, children=${node.children.size}个" }
                    L.pushJavaObject(node); return 1
                } catch (e: Exception) {
                    logE(TAG) { "[组件:$luaName] 创建失败: ${e.message}" }
                    L.pushJavaObject(ComposeNode(
                        type = "Text",
                        props = mapOf("text" to "[$luaName 错误] ${e.message}", "color" to 0xFFFF0000.toLong())
                    ))
                    return 1
                }
            }
        })
        L.setField(-2, luaName)
    }

    // ========== 刷新机制 ==========

    /** 由 LuaActivity 在 doFile 完成后调用，触发首次渲染（同步，确保 setContentView 前 rootState 已就绪） */
    fun refreshAfterLoad() {
        logI(TAG) { "[refreshAfterLoad] 准备触发首次渲染" }
        if (activeLuaFunc != null) {
            refreshNodeTree()  // 首次渲染同步执行，确保 LuaActivity 能拿到 rootState
        } else {
            logW(TAG) { "[refreshAfterLoad] 没有活跃的渲染函数，跳过" }
        }
    }

    /** 是否已有待处理的刷新任务，用于合并快速连续的 scheduleRefresh 调用 */
    private var refreshPending = false

    /**
     * 调度节点树刷新（防重入）
     * 主线程直接执行，子线程 post 到主线程
     */
    internal fun scheduleRefresh() {
        if (refreshPending) return
        refreshPending = true
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                refreshNodeTree()
            } else {
                mainHandler.post { refreshNodeTree() }
            }
        } finally {
            refreshPending = false
        }
    }

    private fun refreshNodeTree() {
        val func = activeLuaFunc
        if (func == null) {
            logW(TAG) { "[refreshNodeTree] 没有活跃的渲染函数，跳过" }
            return
        }
        // 重置状态索引，确保 state() / remember() 按调用顺序返回缓存的同一对象
        stateIndex = 0; rememberIndex = 0; animIndex = 0; navBackStackIndex = 0
        // 依赖追踪：递增 buildCycle，同步所有 StateWrapper
        StateWrapper.syncBuildCycle(stateCache)
        try {
            val startTime = System.currentTimeMillis()
            val result = func.call()
            val elapsed = System.currentTimeMillis() - startTime
            when (result) {
                is ComposeNode -> {
                    logI(TAG) { "[refreshNodeTree] 渲染成功! 根节点=${result.type}, 子节点=${result.children.size}个, 耗时=${elapsed}ms" }
                    rootState.value = result
                    luaError.value = null
                    dumpNodeTree(result, 0)
                }
                else -> {
                    logW(TAG) { "[refreshNodeTree] 返回了非 ComposeNode 类型: ${result?.javaClass?.name}" }
                    luaError.value = "Lua 渲染函数返回了非 ComposeNode 类型: ${result?.javaClass?.simpleName}"
                    if (rootState.value == null) {
                        rootState.value = ComposeNode(type = "Text", props = mapOf("text" to "", "color" to 0L))
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.logError("ComposeBridge", "refreshNodeTree 异常!", e)
            logE(TAG, { "[refreshNodeTree] 刷新节点树失败!" }, e)
            luaError.value = e.message ?: "未知 Lua 错误"
            if (rootState.value == null) {
                rootState.value = ComposeNode(type = "Text", props = mapOf("text" to "", "color" to 0L))
            }
        }
    }

    /** 递归打印节点树结构（调试用） */
    private fun dumpNodeTree(node: ComposeNode, depth: Int) {
        val indent = "  ".repeat(depth)
        val cbStr = if (node.callbacks.isNotEmpty()) " callbacks=[${node.callbacks.keys.joinToString(",")}]" else ""
        val propsStr = node.props.entries.joinToString(", ") { (k, v) ->
            val vStr = when (v) {
                is ModifierChain -> "ModifierChain"
                is String -> "\"${v.take(20)}${if (v.length > 20) "..." else ""}\""
                else -> v?.toString()?.take(30) ?: "null"
            }
            "$k=$vStr"
        }
        logD(TAG) { "${indent}├─ ${node.type}$cbStr [$propsStr]" }
        for (child in node.children) {
            dumpNodeTree(child, depth + 1)
        }
    }

    fun onStateChanged() {
        scheduleRefresh()
    }
}