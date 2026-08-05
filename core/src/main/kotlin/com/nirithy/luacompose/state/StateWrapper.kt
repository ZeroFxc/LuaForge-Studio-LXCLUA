package com.nirithy.luacompose.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * 响应式状态包装器（带依赖追踪 + Scope 依赖失效）
 *
 * 核心机制（移植自 kulipai LuaCompose）：
 * - getValue() 时自动注册当前 buildCycle 为依赖，同时注册活跃 ComposeScope 为依赖方
 * - setValue() 时触发依赖过滤 + Scope 精准失效
 * - 未在当前树中显示的状态变更被跳过，避免无效的全量刷新
 *
 * 与 kulipai ComposeScope 的对应关系：
 *   kulipai: ComposeState.dependentScopes (WeakHashMap<ComposeScope, Boolean>)
 *   nirithy: StateWrapper.lastReadCycle (buildCycle 过滤) + dependentScopes (Scope 精准失效)
 *
 * buildCycle 是一个全局递增计数器，每次 refreshNodeTree 时 +1。
 * getValue() 将 lastReadCycle 设为当前 buildCycle。
 * setValue() 比较 lastReadCycle == buildCycle，只有匹配时才触发 onChange。
 * 同时遍历 dependentScopes 调用 scope.invalidate() 实现精准重组。
 */
open class StateWrapper<T>(
    initialValue: T,
    /** 状态变更回调：state() → scheduleRefresh(), mutableState() → recomposeTrigger++ */
    private val onChange: (() -> Unit)? = null
) {
    private val state: MutableState<T> = mutableStateOf(initialValue)

    /** 当前 ComposeBridge 的构建周期，由 ComposeBridge.setBuildCycle() 设置 */
    @Volatile
    internal var currentBuildCycle: Long = -1L

    /** 最后一次被读取的 buildCycle。只有 lastReadCycle == currentBuildCycle 时 setValue 才触发 onChange */
    @Volatile
    private var lastReadCycle: Long = -1L

    /**
     * 依赖此状态的 ComposeScope 集合（线程安全）
     * 参考 LuaCompose-master 的 ComposeState.dependentScopes
     */
    private val dependentScopes = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<ComposeScope, Boolean>())
    )

    /**
     * 注册依赖方 Scope
     * 当 Scope 读取此状态时调用，建立依赖关系
     */
    fun registerDependency(scope: ComposeScope) {
        dependentScopes.add(scope)
    }

    /**
     * 失效所有依赖方 Scope，触发精准重组
     */
    fun invalidateDependents() {
        synchronized(dependentScopes) {
            for (scope in dependentScopes) {
                scope.invalidate()
            }
        }
    }

    /**
     * 读取值，同时注册依赖追踪
     * 如果当前存在活跃的 buildCycle，记录 lastReadCycle
     * 同时注册当前活跃的 ComposeScope 为依赖方
     */
    open fun getValue(): T {
        // ★ 依赖追踪：记录当前 buildCycle 为最后一次读取周期
        if (currentBuildCycle > 0) {
            lastReadCycle = currentBuildCycle
        }
        // ★ Scope 依赖注册：自动注册当前活跃 Scope
        try {
            val activeScope = com.nirithy.luacompose.bridge.ComposeBridgeInstance.current.currentScope
            registerDependency(activeScope)
        } catch (_: Exception) {
            // 初始化阶段可能尚未初始化 bridge
        }
        return state.value
    }

    /**
     * 设置值（带类型转换 + 依赖过滤 + Scope 精准失效）
     * 只有 lastReadCycle == currentBuildCycle 时（即此状态在当前树中被读取过）才触发 onChange。
     * 同时遍历 dependentScopes 调用 scope.invalidate() 实现精准重组。
     */
    @Suppress("UNCHECKED_CAST")
    open fun setValue(v: Any?) {
        val converted: T = when {
            v is Number && state.value is Float -> v.toFloat() as T
            v is Number && state.value is Int -> v.toInt() as T
            v is Number && state.value is Long -> v.toLong() as T
            v is Number && state.value is Boolean -> (v.toInt() != 0) as T
            else -> v as T
        }
        state.value = converted

        // Scope 精准失效：通知所有依赖此状态的 Scope 重组
        invalidateDependents()

        // 依赖过滤：只触发在当前 buildCycle 中被读取过的状态
        if (lastReadCycle == currentBuildCycle || currentBuildCycle <= 0) {
            onChange?.invoke()
        }
    }

    companion object {
        /** 全局构建周期计数器，由 ComposeBridge 在 refreshNodeTree 前后递增 */
        @Volatile
        var globalBuildCycle: Long = 0L

        /**
         * 同步所有 StateWrapper 的 currentBuildCycle。
         * 在 ComposeBridge.refreshNodeTree() 开始时调用。
         */
        fun syncBuildCycle(stateCache: List<StateWrapper<*>>) {
            globalBuildCycle++
            for (sw in stateCache) {
                sw.currentBuildCycle = globalBuildCycle
            }
        }

        /**
         * 创建派生状态（只读）：每次调用 getValue() 时执行 compute 函数
         */
        fun createDerived(compute: () -> Any?): StateWrapper<Any?> {
            return object : StateWrapper<Any?>(null) {
                override fun getValue(): Any? = compute()
                override fun setValue(v: Any?) { /* 只读 */ }
            }
        }
    }
}