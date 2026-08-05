package com.nirithy.luacompose.state

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.script.BridgeFunction
import com.nirithy.luacompose.script.BridgeValue
import kotlinx.coroutines.CoroutineScope

/**
 * 组件级状态容器，替代全局 stateCache/rememberCache 列表
 *
 * 每个组件调用拥有独立的 ComposeScope，管理该组件及其子组件的状态生命周期。
 * 核心机制：
 * - 按调用顺序缓存状态，与当前 state()/remember() 的索引方式兼容
 * - 每次 beginCycle 重置索引，endCycle 清理不再被访问的条目
 * - 子 scope 继承父 scope 的 coroutineScope，支持嵌套组件
 *
 * 与参考实现 (kulipai LuaCompose) 的对应：
 *   kulipai: ComposeScope.states/remembers/childScopes (MutableMap)
 *   nirithy: 基于索引的有序列表 + 访问追踪集合
 */
class ComposeScope(
    /** 父 scope，null 表示根 scope */
    val parent: ComposeScope? = null,
    /** 协程作用域，继承自父 scope 或 ComposeBridgeInstance.mainScope */
    var coroutineScope: CoroutineScope? = null,
    /** 屏幕密度，继承自父 scope */
    var density: Float = 2.0f
) {

    // ========== 上下文属性（由 ComposeScopeComponent 注入） ==========

    /** Android 上下文 */
    var context: Context? = null
    /** 设备配置 */
    var configuration: Configuration? = null
    /** Material3 颜色方案 */
    var colorScheme: ColorScheme? = null
    /** Material3 字体排版 */
    var typography: Typography? = null
    /** Material3 形状 */
    var shapes: Shapes? = null

    // ========== 内容函数 ==========

    /** scope 的内容函数（Lua 函数），由 execute() 调用 */
    var contentFunc: BridgeFunction? = null

    // ========== 重组版本号 ==========

    private val _recomposeVersion = mutableStateOf(0)
    val recomposeVersion: State<Int> = _recomposeVersion

    // ========== 状态存储 ==========

    /** 响应式状态列表，按调用顺序存储 */
    private val states = mutableListOf<StateWrapper<*>>()
    /** remember 缓存列表，按调用顺序存储 */
    private val remembers = mutableListOf<Any?>()
    /** 子 scope 列表 */
    private val childScopes = mutableListOf<ComposeScope>()

    // ========== 当前周期索引 ==========

    private var stateIdx = 0
    private var rememberIdx = 0
    private var childScopeIdx = 0

    // ========== 访问追踪 ==========

    /** 当前周期被访问的 state 索引集合 */
    private val accessedStates = mutableSetOf<Int>()
    /** 当前周期被访问的 remember 索引集合 */
    private val accessedRemembers = mutableSetOf<Int>()
    /** 当前周期被访问的 childScope 索引集合 */
    private val accessedChildScopes = mutableSetOf<Int>()

    // ========== LaunchedEffect 追踪（参考 kulipai ComposeScope） ==========

    /** LaunchedEffect 的 key 列表，按索引映射 */
    internal val launchedEffectKeys = mutableMapOf<Int, List<Any?>>()
    /** LaunchedEffect 的协程 Job，按索引映射 */
    internal val launchedEffectJobs = mutableMapOf<Int, kotlinx.coroutines.Job>()
    /** LaunchedEffect 总数计数器 */
    internal var launchedEffectsCount = 0

    /** 取消所有 LaunchedEffect 并清空追踪 */
    fun restartLaunchedEffects() {
        launchedEffectKeys.clear()
        launchedEffectJobs.values.forEach { it.cancel() }
        launchedEffectJobs.clear()
    }

    // ========== remember 的 key 追踪（参考 kulipai ComposeScope） ==========

    /** remember 的 key 列表，用于判断 key 是否变更从而重新初始化 */
    internal val rememberKeys = mutableMapOf<Int, List<Any?>>()

    // ========== Effect 状态追踪（参考 kulipai ComposeScope） ==========

    /** DisposableEffect 等副作用的状态标记，按字符串 key 存储 */
    val effectStates = mutableMapOf<String, Boolean>()

    // ========== 本地变量存储（参考 kulipai ComposeScope） ==========

    /** 本地变量存储，用于组件间传递上下文 */
    val locals = mutableMapOf<String, Any?>()

    // ========== 公开方法 ==========

    /**
     * 获取或创建响应式状态
     * @param initialValue 初始值（仅首次创建时使用）
     * @param onChange 状态变更回调
     * @return 缓存的或新创建的 StateWrapper
     */
    fun getOrCreateState(
        initialValue: Any?,
        onChange: (() -> Unit)? = null
    ): StateWrapper<*> {
        val idx = stateIdx++
        accessedStates.add(idx)
        if (idx < states.size) {
            return states[idx]
        }
        val wrapper = StateWrapper(initialValue, onChange)
        states.add(wrapper)
        return wrapper
    }

    /**
     * 获取或创建 remember 缓存值
     * @param initFn 初始化函数（仅首次调用或 key 变更时执行）
     * @param keys 依赖的 key 列表，key 变更时重新初始化（参考 kulipai ComposableScope）
     * @return 缓存的或新计算的值
     */
    fun getOrCreateRemember(initFn: () -> Any?, keys: List<Any?> = emptyList()): Any? {
        val idx = rememberIdx++
        accessedRemembers.add(idx)
        val oldKeys = rememberKeys[idx]
        // key 变更时重新初始化，参考 kulipai ComposeScope.getOrCreateRemember
        if (idx < remembers.size && oldKeys == keys) {
            return remembers[idx]
        }
        // 首次创建或 key 变更，重新执行初始化
        val value = initFn()
        if (idx < remembers.size) {
            remembers[idx] = value
        } else {
            remembers.add(value)
        }
        rememberKeys[idx] = keys
        return value
    }

    /**
     * 获取或创建派生状态（只读，每次访问时重新计算）
     * 参考 kulipai ComposeScope.getOrCreateDerivedState
     * @param computeFn 计算函数，每次 getValue 时调用
     * @return 缓存的派生状态 StateWrapper
     */
    fun getOrCreateDerivedState(computeFn: () -> Any?): StateWrapper<*> {
        val idx = stateIdx++
        accessedStates.add(idx)
        if (idx < states.size) {
            return states[idx]
        }
        val wrapper = StateWrapper.createDerived(computeFn)
        states.add(wrapper)
        return wrapper
    }

    /**
     * 获取或创建子 scope，继承父 scope 的上下文属性
     * 参考 kulipai ComposeScope.getOrCreateChildScope
     * @return 缓存的或新创建的子 ComposeScope
     */
    fun getOrCreateChildScope(): ComposeScope {
        val idx = childScopeIdx++
        accessedChildScopes.add(idx)
        if (idx < childScopes.size) {
            val existing = childScopes[idx]
            // 更新继承的上下文属性，确保子 scope 拿到最新的父 scope 上下文
            existing.coroutineScope = this.coroutineScope
            existing.context = this.context
            existing.density = this.density
            existing.configuration = this.configuration
            existing.colorScheme = this.colorScheme
            existing.typography = this.typography
            existing.shapes = this.shapes
            return existing
        }
        val child = ComposeScope(
            parent = this,
            coroutineScope = this.coroutineScope,
            density = this.density
        )
        child.context = this.context
        child.configuration = this.configuration
        child.colorScheme = this.colorScheme
        child.typography = this.typography
        child.shapes = this.shapes
        childScopes.add(child)
        return child
    }

    /**
     * 开始新的渲染周期，重置所有索引和访问追踪
     * 同时重置 LaunchedEffect 计数器和 effectStates
     */
    fun beginCycle() {
        stateIdx = 0
        rememberIdx = 0
        childScopeIdx = 0
        launchedEffectsCount = 0
        accessedStates.clear()
        accessedRemembers.clear()
        accessedChildScopes.clear()
        // 递归重置子 scope
        for (child in childScopes) {
            child.beginCycle()
        }
    }

    /**
     * 结束渲染周期，清理不再被访问的条目
     * 同时同步所有 StateWrapper 的 buildCycle
     * 清理不再被访问的 rememberKeys、launchedEffect 状态
     */
    fun endCycle(buildCycle: Long) {
        // 清理不再被访问的 state（从后往前删，避免索引偏移）
        var removed = 0
        for (i in states.indices.reversed()) {
            if (i !in accessedStates) {
                states.removeAt(i)
                removed++
            }
        }
        // 清理不再被访问的 remember 和其 key
        var removedR = 0
        for (i in remembers.indices.reversed()) {
            if (i !in accessedRemembers) {
                remembers.removeAt(i)
                rememberKeys.remove(i)
                removedR++
            }
        }
        // 清理不再被访问的 childScope
        var removedC = 0
        for (i in childScopes.indices.reversed()) {
            if (i !in accessedChildScopes) {
                childScopes.removeAt(i)
                removedC++
            }
        }

        // 同步所有 StateWrapper 的 buildCycle
        for (sw in states) {
            sw.currentBuildCycle = buildCycle
        }

        // 递归结束子 scope 的周期
        for (child in childScopes) {
            child.endCycle(buildCycle)
        }
    }

    /**
     * 获取所有 StateWrapper（用于依赖追踪同步）
     */
    fun collectAllStates(): List<StateWrapper<*>> {
        val result = mutableListOf<StateWrapper<*>>()
        result.addAll(states)
        for (child in childScopes) {
            result.addAll(child.collectAllStates())
        }
        return result
    }

    /**
     * 触发重组，递增 recomposeVersion
     */
    fun invalidate() {
        _recomposeVersion.value++
    }

    /**
     * 执行 scope 的内容函数，返回生成的 ComposeNode 列表
     *
     * @param args 传递给内容函数的参数
     * @return 内容函数生成的 ComposeNode 列表
     */
    fun execute(vararg args: BridgeValue): List<ComposeNode> {
        val func = contentFunc ?: return emptyList()

        beginCycle()
        try {
            // 调用内容函数，返回 ComposeNode（由 Lua 侧组件工厂生成）
            val result = func.call(*args)
            if (result.isUserdata()) {
                val node = result.asUserdata() as? ComposeNode
                return if (node != null) listOf(node) else emptyList()
            }
            return emptyList()
        } finally {
            endCycle(StateWrapper.globalBuildCycle)
        }
    }

    /**
     * 完全重置 scope，清空所有状态
     * 包括 LaunchedEffect、effectStates、locals 等
     */
    fun reset() {
        // 取消所有 LaunchedEffect
        launchedEffectJobs.values.forEach { it.cancel() }
        states.clear()
        remembers.clear()
        childScopes.clear()
        rememberKeys.clear()
        launchedEffectKeys.clear()
        launchedEffectJobs.clear()
        effectStates.clear()
        locals.clear()
        stateIdx = 0
        rememberIdx = 0
        childScopeIdx = 0
        launchedEffectsCount = 0
        accessedStates.clear()
        accessedRemembers.clear()
        accessedChildScopes.clear()
    }
}