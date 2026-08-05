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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import com.nirithy.luacompose.modifier.ModifierChain
import com.nirithy.luacompose.navigation.Navigation3Plugin
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.GeneratedPluginRegistry
import com.nirithy.luacompose.plugin.PluginRegistry
import com.nirithy.luacompose.render.ComponentRegistry
import com.nirithy.luacompose.script.BridgeEngine
import com.nirithy.luacompose.script.LuaJavaBridgeEngine
import com.nirithy.luacompose.state.ComposeScope
import com.nirithy.luacompose.state.StateWrapper
import com.luajava.JavaFunction
import com.luajava.LuaObject
import com.luajava.LuaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers

/**
 * ComposeBridge 实例基类
 *
 * 持有所有 Compose-Lua 桥接的运行时状态，支持多实例隔离。
 * 默认单例 ComposeBridge 用于 LuaActivity 正常运行，PreviewComposeBridge 用于编辑器预览沙箱。
 * 通过 ThreadLocal 支持在特定执行上下文中切换当前活跃 bridge。
 */
open class ComposeBridgeInstance {
    protected open val TAG: String = "ComposeBridgeInstance"

    // ========== 公开状态 ==========
    val rootState: MutableState<ComposeNode?> = mutableStateOf(null)
    val luaError: MutableState<String?> = mutableStateOf(null)
    /** mutableState 变更时递增，触发 ComposeHost 重组（不重建 Lua 树） */
    val recomposeTrigger: MutableState<Long> = mutableStateOf(0L)

    // ========== 内部状态 ==========

    /** 当前活跃的 Lua 渲染函数引用 */
    internal var activeLuaFunc: LuaObject? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 主线程协程作用域，用于 Animatable/delay 等异步操作 */
    var mainScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        internal set

    /** Lua 线程锁，保护 LuaState 并发访问 */
    val luaLock: Any = Any()

    /** Lua 引擎抽象层，隔离底层引擎实现，支持替换为 LuaJ 等引擎 */
    lateinit var engine: BridgeEngine
        internal set

    /** 根 ComposeScope，管理所有组件的状态生命周期 */
    internal val rootScope: ComposeScope = ComposeScope(coroutineScope = mainScope)
    /** 当前活跃的 ComposeScope，在 refreshNodeTree 期间设为 rootScope */
    @Volatile
    internal var currentScope: ComposeScope = rootScope

    // 状态缓存：保持向后兼容，委托给 rootScope
    internal val stateCache: MutableList<StateWrapper<*>> get() = rootScope.collectAllStates().toMutableList()
    internal var stateIndex = 0

    // remember 缓存：保持向后兼容
    internal val rememberCache = mutableListOf<Any?>()
    internal var rememberIndex = 0

    // NavBackStack 缓存：按调用顺序返回同一实例
    internal val navBackStackCache = mutableListOf<com.nirithy.luacompose.navigation.NavBackStack>()
    internal var navBackStackIndex = 0

    // 定时器任务追踪，用于 resetState 时统一清理
    internal val timerJobs = mutableListOf<kotlinx.coroutines.Job>()

    /** 当前正在处理的手势配置，供 Lua 的 detectDragGestures/detectTapGestures 设置回调 */
    @Volatile
    var currentGestureConfig: com.nirithy.luacompose.gesture.GestureConfig? = null

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

    // ========== 上下文接收器栈（compose.with 模式） ==========

    /** 上下文接收器栈，用于 compose.with(receiver, block) 模式 */
    @PublishedApi
    internal val contextReceiversStack = ArrayDeque<Any>()

    /** 入栈上下文接收器 */
    fun pushContextReceiver(receiver: Any) {
        contextReceiversStack.addLast(receiver)
    }

    /** 出栈上下文接收器 */
    fun popContextReceiver() {
        if (contextReceiversStack.isNotEmpty()) {
            contextReceiversStack.removeLast()
        }
    }

    /** 查找指定类型的上下文接收器（从栈顶向下查找） */
    inline fun <reified T> findContextReceiver(): T? {
        return contextReceiversStack.findLast { it is T } as? T
    }

    // ========== 类型转换器（Java → Script） ==========

    /** Java → Script 自定义转换器，按 Class 匹配 */
    val converters = mutableMapOf<Class<*>, (Any) -> com.nirithy.luacompose.script.BridgeValue>()

    /** Java 值解包器（Lua 侧回调），用于 LuaModifier 中的 unwrapAny */
    var luaValueUnwrapper: ((Any?) -> Any?)? = null

    // ========== 类原型扩展 ==========

    /** 类原型注册表，允许为 Java 类添加 Lua 扩展方法 */
    val classPrototypes = mutableMapOf<String, com.nirithy.luacompose.script.BridgeTable>()

    /** 为指定类注册 Lua 扩展 */
    fun registerExtension(className: String, ext: com.nirithy.luacompose.script.BridgeTable) {
        classPrototypes[className] = ext
    }

    /**
     * 当前活跃的导航回退栈，由 NavBackStack 注册
     * LuaActivity 的 onBackPressed 检查此栈，自动 pop
     */
    @Volatile
    var activeBackStack: com.nirithy.luacompose.navigation.NavBackStack? = null

    // 背景色
    val backgroundColor: MutableState<Color> = mutableStateOf(Color.Unspecified)

    // ========== 预览模式相关 ==========
    /** 是否处于预览交互模式 */
    var isPreviewMode: Boolean = false
    /** 节点点击回调，参数为 nodePath */
    var onNodeClick: ((String) -> Unit)? = null
    /** 预览节点边界收集器 */
    var previewBoundsCollector: com.nirithy.luacompose.preview.PreviewNodeBoundsCollector? = null

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

    // 动画状态缓存
    internal val animatedFloats = mutableListOf<AnimatedFloat>()
    internal var animIndex = 0
    // Dp 动画缓存
    internal val animatedDps = mutableListOf<AnimatedDp>()
    internal var animDpIndex = 0
    // 颜色动画缓存
    internal val animatedColors = mutableListOf<AnimatedColor>()
    internal var animColorIndex = 0

    /** 由 LuaActivity 调用，注入 Android 上下文和配置 */
    fun setAndroidContext(context: Context) {
        androidContext = context
        androidConfiguration = context.resources.configuration
        density.value = context.resources.displayMetrics.density
        logD(TAG) { "[setAndroidContext] density=${density.value}, orientation=${androidConfiguration?.orientation}" }
    }

    // ========== 注入入口 ==========

    open fun inject(L: LuaState) {
        logD("ComposeBridge") { "inject 开始, LuaState=${L.hashCode()}, bridge=${this.hashCode()}" }
        resetState()

        // 初始化引擎抽象层
        engine = LuaJavaBridgeEngine(L)
        logI(TAG) { "[inject] BridgeEngine 已初始化: ${engine::class.simpleName}" }

        // 注册 Java → Script 自定义类型转换器
        registerJavaToScriptConverters()

        logI(TAG) { "========== 开始注入 Compose API ==========" }

        // ★ 通过 GeneratedPluginRegistry 统一注册所有插件
        logD(TAG) { "[inject] 注册插件..." }
        GeneratedPluginRegistry.registerAll()
        PluginRegistry.applyToComponentRegistry()
        logD(TAG) { "[inject] 组件渲染器注册完成，共 ${ComponentRegistry.componentCount()} 个" }

        // KSP 编译期生成的组件注册
        registerKspComponents()

        // 创建 compose 命名空间（根命名空间），子命名空间由 __index 懒加载
        LazyNamespace.create(L, "androidx.compose")
        val composeTableIdx = L.getTop()

        // ★ 通过插件注入全局 API（Modifier、Arrangement、Alignment、动画等）
        logD(TAG) { "[inject] 注入插件全局 API..." }
        PluginRegistry.injectGlobalsAll(L, composeTableIdx)

        // 注入桥接专用 Lua API（状态、效果、导航等）
        logD(TAG) { "[inject] 注入桥接 API..." }
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
        registerColorHelper(L)
        registerTimeHelper(L)
        registerBackgroundColor(L)
        registerThemeTable(L)
        registerLocalDensity(L)
        registerLocalContext(L)
        registerLocalConfiguration(L)
        registerWithContext(L)
        registerCoroutineScopeFactory(L)
        registerAnimatableFactory(L)
        registerRenderFunction(L)
        registerReflectHelpers(L)
        registerDumpTool(L)
        registerDelayTool(L)
        registerSnackbarHostState(L)
        registerShowSnackbar(L)
        registerRuntimeShaderApi(L)

        // 动画状态 API
        registerAnimateColorFactory(L)
        registerAnimateDpFactory(L)

        // 副作用 API（LaunchedEffect / DisposableEffect / key）
        registerLaunchedEffectApi(L)
        registerDisposableEffectApi(L)
        registerKeyApi(L)

        // 导航 API
        Navigation3Plugin.injectNavigationApis(L)

        // 验证并设置全局 compose 表
        verifyAndSetGlobal(L)
    }

    /**
     * 注册 Java → Script 自定义类型转换器
     *
     * 在 coerceJavaToScript 被调用时，会先检查此处注册的转换器，
     * 将 Compose 类型（Dp、Color、Size、Offset、Rect、Path）转换为 Lua 友好的表结构。
     */
    private fun registerJavaToScriptConverters() {
        val bridge = this

        // Dp 转换器
        converters[Dp::class.java] = { value ->
            val dp = value as Dp
            val table = bridge.engine.createTable()
            table.set("value", bridge.engine.createValue(dp.value.toDouble()))
            table.set("_javaDp", bridge.engine.createUserdata(dp))
            table.set("toPx", bridge.engine.createFunction { args ->
                bridge.engine.createValue((dp.value * bridge.density.value).toDouble())
            })
            table
        }

        // Color 转换器
        converters[Color::class.java] = { value ->
            val color = value as Color
            val table = bridge.engine.createTable()
            table.set("_javaColor", bridge.engine.createUserdata(color))
            table.set("luminance", bridge.engine.createFunction { args ->
                val lum = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
                bridge.engine.createValue(lum.toDouble())
            })
            table.set("copy", bridge.engine.createFunction { args ->
                val alpha = if (args.isNotEmpty()) args[0].toDouble() else 1.0
                bridge.engine.createUserdata(color.copy(alpha = alpha.toFloat()))
            })
            table
        }

        // Size 转换器
        converters[Size::class.java] = { value ->
            val size = value as Size
            val table = bridge.engine.createTable()
            table.set("width", bridge.engine.createValue(size.width.toDouble()))
            table.set("height", bridge.engine.createValue(size.height.toDouble()))
            table.set("_javaSize", bridge.engine.createUserdata(size))
            table
        }

        // Offset 转换器
        converters[Offset::class.java] = { value ->
            val offset = value as Offset
            val table = bridge.engine.createTable()
            table.set("x", bridge.engine.createValue(offset.x.toDouble()))
            table.set("y", bridge.engine.createValue(offset.y.toDouble()))
            table.set("_javaOffset", bridge.engine.createUserdata(offset))
            table.set("copy", bridge.engine.createFunction { args ->
                val x = if (args.size >= 1) args[0].toDouble().toFloat() else offset.x
                val y = if (args.size >= 2) args[1].toDouble().toFloat() else offset.y
                bridge.engine.createUserdata(Offset(x, y))
            })
            table
        }

        // Rect 转换器
        converters[Rect::class.java] = { value ->
            val rect = value as Rect
            val table = bridge.engine.createTable()
            table.set("left", bridge.engine.createValue(rect.left.toDouble()))
            table.set("top", bridge.engine.createValue(rect.top.toDouble()))
            table.set("right", bridge.engine.createValue(rect.right.toDouble()))
            table.set("bottom", bridge.engine.createValue(rect.bottom.toDouble()))
            table.set("_javaRect", bridge.engine.createUserdata(rect))
            table
        }

        // Path 转换器
        converters[Path::class.java] = { value ->
            val path = value as Path
            val table = bridge.engine.createTable()
            table.set("_javaPath", bridge.engine.createUserdata(path))
            table.set("moveTo", bridge.engine.createFunction { args ->
                val x = args[0].toDouble().toFloat()
                val y = args[1].toDouble().toFloat()
                path.moveTo(x, y)
                bridge.engine.createNil()
            })
            table.set("lineTo", bridge.engine.createFunction { args ->
                val x = args[0].toDouble().toFloat()
                val y = args[1].toDouble().toFloat()
                path.lineTo(x, y)
                bridge.engine.createNil()
            })
            table.set("close", bridge.engine.createFunction { args ->
                path.close()
                bridge.engine.createNil()
            })
            table.set("reset", bridge.engine.createFunction { args ->
                path.reset()
                bridge.engine.createNil()
            })
            table.set("addOval", bridge.engine.createFunction { args ->
                if (args.size >= 1 && args[0].isTable()) {
                    val rectTable = args[0].asTable()
                    val left = rectTable.get("left").toDouble().toFloat()
                    val top = rectTable.get("top").toDouble().toFloat()
                    val right = rectTable.get("right").toDouble().toFloat()
                    val bottom = rectTable.get("bottom").toDouble().toFloat()
                    path.addOval(Rect(left, top, right, bottom))
                }
                bridge.engine.createNil()
            })
            table.set("addRect", bridge.engine.createFunction { args ->
                if (args.size >= 1 && args[0].isTable()) {
                    val rectTable = args[0].asTable()
                    val left = rectTable.get("left").toDouble().toFloat()
                    val top = rectTable.get("top").toDouble().toFloat()
                    val right = rectTable.get("right").toDouble().toFloat()
                    val bottom = rectTable.get("bottom").toDouble().toFloat()
                    path.addRect(Rect(left, top, right, bottom))
                }
                bridge.engine.createNil()
            })
            table.set("op", bridge.engine.createFunction { args ->
                bridge.engine.createNil()
            })
            table
        }
    }

    internal open fun resetState() {
        activeLuaFunc = null
        rootState.value = null
        luaError.value = null
        recomposeTrigger.value = 0L
        // ★ 通过 ComposeScope 重置所有状态
        rootScope.reset()
        stateIndex = 0
        rememberCache.clear(); rememberIndex = 0
        animatedFloats.clear(); animIndex = 0
        animatedDps.clear(); animDpIndex = 0
        animatedColors.clear(); animColorIndex = 0
        navBackStackCache.clear(); navBackStackIndex = 0
        activeBackStack = null
        activeSharedTransitionScopes.clear()
        activeAnimatedVisibilityScopes.clear()
        backgroundColor.value = Color.Unspecified
        themeColors.value = themeColors.value // 重置为默认值
        themeTypography.value = themeTypography.value
        themeShapes.value = themeShapes.value
        refreshPending = false
        suppressRefresh = false
        // 取消所有定时器任务
        timerJobs.forEach { try { it.cancel() } catch (_: Exception) {} }
        timerJobs.clear()
        // 取消旧协程作用域并重建
        mainScope.cancel()
        mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    internal fun registerKspComponents() {
        try {
            Class.forName("com.nirithy.luacompose.generated.GeneratedComponentRegistry")
                .getMethod("registerAll").invoke(null)
            // ★ 为 KSP 渲染器注册短名称别名，减少硬编码组件依赖
            registerKspShortNames()
            logI(TAG) { "[inject] KSP 生成的组件已注册，共 ${ComponentRegistry.componentCount()} 个" }
        } catch (e: ClassNotFoundException) {
            logW(TAG) { "[inject] KSP 生成的组件不存在（首次编译可能未生成），回退到 DynamicRenderer" }
        } catch (e: Exception) {
            logW(TAG) { "[inject] KSP 生成的组件注册失败: ${e.message}" }
        }
    }

    /**
     * 为 KSP 生成的渲染器注册短名称别名
     *
     * KSP 渲染器以完整类路径注册（如 "androidx.compose.foundation.layout.Column"），
     * 但 Lua 侧通常使用短名称（如 "Column"）。此方法创建短名称 → 完整路径的别名映射，
     * 让 Lua 的 compose.Column 等短名称也能命中 KSP 渲染器，从而减少硬编码组件。
     *
     * 注意：Column/Row/Box 保留硬编码版本（需要 scope 感知的 weight/align 处理），
     * 其他组件如 FlowRow/FlowColumn/Text/Button/Card 等通过 KSP 渲染。
     */
    private fun registerKspShortNames() {
        // 短名称 → KSP 完整类路径映射
        // 仅映射不需要特殊 scope 处理的组件
        val shortNameMap = mapOf(
            // foundation.layout（不需要 scope 处理的布局组件）
            "FlowRow" to "androidx.compose.foundation.layout.FlowRow",
            "FlowColumn" to "androidx.compose.foundation.layout.FlowColumn",
            "Spacer" to "androidx.compose.foundation.layout.Spacer",
            // material3 显示组件
            "Text" to "androidx.compose.material3.Text",
            // material3 按钮
            "Button" to "androidx.compose.material3.Button",
            "ElevatedButton" to "androidx.compose.material3.ElevatedButton",
            "FilledTonalButton" to "androidx.compose.material3.FilledTonalButton",
            "OutlinedButton" to "androidx.compose.material3.OutlinedButton",
            "TextButton" to "androidx.compose.material3.TextButton",
            // material3 容器
            "Card" to "androidx.compose.material3.Card",
            "ElevatedCard" to "androidx.compose.material3.ElevatedCard",
            "OutlinedCard" to "androidx.compose.material3.OutlinedCard",
            "Surface" to "androidx.compose.material3.Surface",
            // material3 输入
            "TextField" to "androidx.compose.material3.TextField",
            "OutlinedTextField" to "androidx.compose.material3.OutlinedTextField",
            "Checkbox" to "androidx.compose.material3.Checkbox",
            "Switch" to "androidx.compose.material3.Switch",
            "Slider" to "androidx.compose.material3.Slider",
            "RadioButton" to "androidx.compose.material3.RadioButton",
            // material3 图标
            "Icon" to "androidx.compose.material3.Icon",
            "IconButton" to "androidx.compose.material3.IconButton",
            // material3 导航
            "Scaffold" to "androidx.compose.material3.Scaffold",
            "TopAppBar" to "androidx.compose.material3.TopAppBar",
            "BottomAppBar" to "androidx.compose.material3.BottomAppBar",
            "NavigationBar" to "androidx.compose.material3.NavigationBar",
            "NavigationBarItem" to "androidx.compose.material3.NavigationBarItem",
            // material3 其他常用组件
            "Divider" to "androidx.compose.material3.Divider",
            "AlertDialog" to "androidx.compose.material3.AlertDialog",
            "CircularProgressIndicator" to "androidx.compose.material3.CircularProgressIndicator",
            "LinearProgressIndicator" to "androidx.compose.material3.LinearProgressIndicator",
            "Badge" to "androidx.compose.material3.Badge",
            "ListItem" to "androidx.compose.material3.ListItem",
            "DropdownMenu" to "androidx.compose.material3.DropdownMenu",
            "DropdownMenuItem" to "androidx.compose.material3.DropdownMenuItem",
            "ModalBottomSheet" to "androidx.compose.material3.ModalBottomSheet",
            "Snackbar" to "androidx.compose.material3.Snackbar",
            "SnackbarHost" to "androidx.compose.material3.SnackbarHost",
            "Tab" to "androidx.compose.material3.Tab",
            "TabRow" to "androidx.compose.material3.TabRow",
            // foundation
            "Image" to "androidx.compose.foundation.Image",
            "Canvas" to "androidx.compose.foundation.Canvas",
        )
        var aliasCount = 0
        for ((shortName, fullPath) in shortNameMap) {
            val renderer = ComponentRegistry.getRenderer(fullPath)
            if (renderer != null) {
                ComponentRegistry.register(shortName, renderer)
                aliasCount++
            }
        }
        logI(TAG) { "[inject] KSP 短名称别名注册完成: $aliasCount/${shortNameMap.size} 个" }
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
            -- ★ 验证 Modifier 是否正确注入
            local mod = compose.Modifier
            local modType = type(mod)
            if modType ~= "table" then error("FATAL: compose.Modifier 返回了 " .. modType .. " 而不是 table!") end
            local modMt = getmetatable(mod)
            if modMt == nil then error("FATAL: compose.Modifier 没有 metatable!") end
            if modMt.__call == nil then error("FATAL: compose.Modifier 没有 __call!") end
            if modMt.__index == nil then error("FATAL: compose.Modifier 没有 __index!") end
            -- 验证 Modifier() 调用返回非 nil
            local chain = mod()
            if chain == nil then error("FATAL: Modifier() 返回 nil!") end
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
     * 抑制刷新标志：在初始代码执行期间设为 true，防止 LuaState 被多线程并发访问导致 SIGABRT
     * 执行期间 Lua 代码调用 state.setValue() 会触发 onChange → scheduleRefresh() → 主线程 refreshNodeTree()
     * → func.call() 访问 LuaState，与 IO 线程的 L.pcall 并发访问同一非线程安全 LuaState，导致内存损坏
     */
    @Volatile
    var suppressRefresh = false

    /**
     * 调度节点树刷新（防重入）
     * 主线程直接执行，子线程 post 到主线程
     */
    internal fun scheduleRefresh() {
        if (suppressRefresh || refreshPending) return
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
        stateIndex = 0; rememberIndex = 0; animIndex = 0; animDpIndex = 0; animColorIndex = 0; navBackStackIndex = 0

        // ★ ComposeScope：开始新的渲染周期
        currentScope = rootScope
        rootScope.beginCycle()

        // 依赖追踪：递增 buildCycle，同步所有 StateWrapper
        StateWrapper.globalBuildCycle++
        val allStates = rootScope.collectAllStates()
        for (sw in allStates) {
            sw.currentBuildCycle = StateWrapper.globalBuildCycle
        }
        try {
            val startTime = System.currentTimeMillis()
            val result = func.call()
            val elapsed = System.currentTimeMillis() - startTime
            when (result) {
                is ComposeNode -> {
                    logI(TAG) { "[refreshNodeTree] 渲染成功! 根节点=${result.type}, 子节点=${result.children.size}个, 耗时=${elapsed}ms" }
                    // 释放旧节点树中的 LuaObject 引用，帮助 Lua GC 回收
                    val oldRoot = rootState.value
                    if (oldRoot != null) {
                        oldRoot.release()
                    }
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
        } finally {
            // ★ ComposeScope：结束渲染周期，清理不再被访问的状态
            rootScope.endCycle(StateWrapper.globalBuildCycle)
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

    companion object {
        /**
         * ThreadLocal 持有当前线程活跃的 bridge 实例。
         * 预览沙箱执行 Lua 代码时会设置此值，使所有通过 current 访问 bridge 的代码使用预览实例。
         * 默认返回全局单例 ComposeBridge，保持向后兼容。
         */
        @PublishedApi
        internal val threadLocalBridge = ThreadLocal<ComposeBridgeInstance>()

        /**
         * 获取当前线程活跃的 bridge 实例。
         * 优先使用 ThreadLocal 中设置的实例（预览沙箱），否则返回全局单例 ComposeBridge。
         */
        val current: ComposeBridgeInstance
            get() = threadLocalBridge.get() ?: ComposeBridge

        /**
         * 在指定 bridge 作为当前实例的上下文中执行代码块。
         * 执行前设置 ThreadLocal，执行后恢复（无论是否异常）。
         *
         * @param bridge 要设为当前的 bridge 实例
         * @param block 要执行的代码块
         * @return 代码块的返回值
         */
        @PublishedApi
        internal inline fun <T> withBridge(bridge: ComposeBridgeInstance, block: () -> T): T {
            val previous = threadLocalBridge.get()
            threadLocalBridge.set(bridge)
            try {
                return block()
            } finally {
                if (previous != null) {
                    threadLocalBridge.set(previous)
                } else {
                    threadLocalBridge.remove()
                }
            }
        }
    }
}
