package com.nirithy.luacompose.gesture

import com.nirithy.luacompose.logD
import com.nirithy.luacompose.logE
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.luajava.LuaObject
import com.nirithy.luacompose.bridge.ComposeBridgeInstance

private const val TAG = "GestureSupport"

/**
 * 手势配置，存储在 ModifierChain 中
 * 由 ComposeRenderer 在渲染时消费
 */
data class GestureConfig(
    var onTap: LuaObject? = null,
    var onDoubleTap: LuaObject? = null,
    var onLongPress: LuaObject? = null,
    var onDragStart: LuaObject? = null,
    var onDrag: LuaObject? = null,
    var onDragEnd: LuaObject? = null,
    var onDragCancel: LuaObject? = null,
    /** pointerInput 稳定 key 列表，由 ModifierChain.pointerInput(key1, key2, callback) 设置 */
    var pointerInputKeys: List<Any>? = null,
    /** 手势配置回调块，由 pointerInput 的 callback 参数设置，在 applyGestures 中首先调用 */
    var gestureBlock: LuaObject? = null,
) {
    val hasAny: Boolean get() = onTap != null || onDoubleTap != null || onLongPress != null
            || onDragStart != null || onDrag != null || onDragEnd != null || onDragCancel != null
            || gestureBlock != null
}

/**
 * 将手势配置应用到 Modifier
 * 在 Composable 上下文中调用
 *
 * 流程：
 * 1. 先调用 gestureBlock（Lua 的 pointerInput callback），在其中通过 detectDragGestures/detectTapGestures 设置回调
 * 2. 然后根据 config 中设置的回调注册 Compose 手势检测器
 */
@Composable
fun Modifier.applyGestures(config: GestureConfig?): Modifier {
    if (config == null || !config.hasAny) return this

    // ★ 使用 remember 创建稳定 key，确保 childrenFunc 重组时 pointerInput 协程不被取消
    val gestureKey = remember { Any() }
    // 如果 ModifierChain 设置了 pointerInputKeys，使用它们；否则使用默认 gestureKey
    val keys = config.pointerInputKeys?.toTypedArray() ?: arrayOf(gestureKey)

    return this.pointerInput(*keys) {
        // Step 1: 调用 gestureBlock 配置手势（Lua 的 detectDragGestures/detectTapGestures 会设置 config 的回调）
        config.gestureBlock?.let { block ->
            try {
                // 设置当前 GestureConfig 引用，供 Lua 的 detectDragGestures/detectTapGestures 使用
                ComposeBridgeInstance.current.currentGestureConfig = config
                block.call()
            } catch (e: Exception) {
                logE(TAG) { "gestureBlock 执行失败: ${e.message}" }
            } finally {
                ComposeBridgeInstance.current.currentGestureConfig = null
            }
        }

        // Step 2: 拖拽手势
        if (config.onDrag != null || config.onDragStart != null || config.onDragEnd != null) {
            logD(TAG) { "detectDragGestures 启动" }
            detectDragGestures(
                onDragStart = { offset ->
                    config.onDragStart?.let { fn ->
                        try {
                            synchronized(ComposeBridgeInstance.current.luaLock) {
                                // 传递 LuaOffset 对象，支持 .x 和 .y 访问
                                fn.call(com.nirithy.luacompose.graphics.LuaOffset(offset.x.toDouble(), offset.y.toDouble()))
                            }
                        }
                        catch (e: Exception) { logE(TAG) { "onDragStart 回调失败: ${e.message}" } }
                    }
                },
                onDragEnd = {
                    config.onDragEnd?.let { fn ->
                        try { synchronized(ComposeBridgeInstance.current.luaLock) { fn.call() } }
                        catch (e: Exception) { logE(TAG) { "onDragEnd 回调失败: ${e.message}" } }
                    }
                },
                onDragCancel = {
                    config.onDragCancel?.let { fn ->
                        try { synchronized(ComposeBridgeInstance.current.luaLock) { fn.call() } }
                        catch (e: Exception) { logE(TAG) { "onDragCancel 回调失败: ${e.message}" } }
                    }
                },
                onDrag = { change, dragAmount ->
                    config.onDrag?.let { fn ->
                        try {
                            change.consume()
                            synchronized(ComposeBridgeInstance.current.luaLock) {
                                // 传递 change 对象（Java）和 dragAmount 作为 LuaOffset 对象
                                // Lua 侧: function(change, dragAmount) change:consume(); dragAmount.y ... end
                                fn.call(change, com.nirithy.luacompose.graphics.LuaOffset(dragAmount.x.toDouble(), dragAmount.y.toDouble()))
                            }
                        } catch (e: Exception) {
                            logE(TAG) { "onDrag 回调失败: ${e.message}" }
                        }
                    }
                }
            )
        }

        // 点击手势
        if (config.onTap != null || config.onDoubleTap != null || config.onLongPress != null) {
            detectTapGestures(
                onTap = config.onTap?.let { fn ->
                    { offset ->
                        try {
                            synchronized(ComposeBridgeInstance.current.luaLock) {
                                fn.call(com.nirithy.luacompose.graphics.LuaOffset(offset.x.toDouble(), offset.y.toDouble()))
                            }
                        }
                        catch (e: Exception) { logE(TAG) { "onTap 回调失败: ${e.message}" } }
                    }
                },
                onDoubleTap = config.onDoubleTap?.let { fn ->
                    { offset ->
                        try {
                            synchronized(ComposeBridgeInstance.current.luaLock) {
                                fn.call(com.nirithy.luacompose.graphics.LuaOffset(offset.x.toDouble(), offset.y.toDouble()))
                            }
                        }
                        catch (e: Exception) { logE(TAG) { "onDoubleTap 回调失败: ${e.message}" } }
                    }
                },
                onLongPress = config.onLongPress?.let { fn ->
                    { offset ->
                        try {
                            synchronized(ComposeBridgeInstance.current.luaLock) {
                                fn.call(com.nirithy.luacompose.graphics.LuaOffset(offset.x.toDouble(), offset.y.toDouble()))
                            }
                        }
                        catch (e: Exception) { logE(TAG) { "onLongPress 回调失败: ${e.message}" } }
                    }
                }
            )
        }
    }
}