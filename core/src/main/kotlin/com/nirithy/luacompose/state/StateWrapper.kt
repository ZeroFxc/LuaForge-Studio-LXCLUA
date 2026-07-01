package com.nirithy.luacompose.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * 响应式状态包装器（带依赖追踪）
 *
 * 核心机制（移植自 kulipai LuaCompose）：
 * - getValue() 时自动注册当前 buildCycle 为依赖
 * - setValue() 时只触发「在当前 buildCycle 中被读取过」的状态变更
 * - 未在当前树中显示的状态变更被跳过，避免无效的全量刷新
 *
 * 与 kulipai ComposeScope 的对应关系：
 *   kulipai: ComposeState.dependentScopes (WeakHashMap<ComposeScope, Boolean>)
 *   nirithy: StateWrapper.lastReadCycle (记录最后一次被读取的 buildCycle)
 *
 * buildCycle 是一个全局递增计数器，每次 refreshNodeTree 时 +1。
 * getValue() 将 lastReadCycle 设为当前 buildCycle。
 * setValue() 比较 lastReadCycle == buildCycle，只有匹配时才调用 onChange。
 *
 * - 普通状态：通过 state() 创建，onChange = scheduleRefresh()（全量刷新）
 * - 可变状态：通过 mutableState() 创建，onChange = recomposeTrigger++（轻量重组）
 * - 派生状态：通过 derivedStateOf() 创建，只读
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
     * 读取值，同时注册依赖追踪
     * 如果当前存在活跃的 buildCycle，记录 lastReadCycle
     */
    open fun getValue(): T {
        // ★ 依赖追踪：记录当前 buildCycle 为最后一次读取周期
        if (currentBuildCycle > 0) {
            lastReadCycle = currentBuildCycle
        }
        return state.value
    }

    /**
     * 设置值（带类型转换 + 依赖过滤）
     * 只有 lastReadCycle == currentBuildCycle 时（即此状态在当前树中被读取过）才触发 onChange。
     * 其他情况表示此状态不在当前 UI 树中，跳过刷新避免无效重建。
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

        // ★ 依赖过滤：只触发在当前 buildCycle 中被读取过的状态
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