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
) {
    val hasAny: Boolean get() = onTap != null || onDoubleTap != null || onLongPress != null
            || onDragStart != null || onDrag != null || onDragEnd != null || onDragCancel != null
}

/**
 * 将手势配置应用到 Modifier
 * 在 Composable 上下文中调用
 */
@Composable
fun Modifier.applyGestures(config: GestureConfig?): Modifier {
    if (config == null || !config.hasAny) return this

    // ★ 使用 remember 创建稳定 key，确保 childrenFunc 重组时 pointerInput 协程不被取消
    val gestureKey = remember { Any() }

    return this.pointerInput(gestureKey) {
        // 拖拽手势
        if (config.onDrag != null || config.onDragStart != null || config.onDragEnd != null) {
            logD(TAG) { "detectDragGestures 启动" }
            detectDragGestures(
                onDragStart = { offset ->
                    config.onDragStart?.let { fn ->
                        try { fn.call(offset.x.toDouble(), offset.y.toDouble()) }
                        catch (e: Exception) { logE(TAG) { "onDragStart 回调失败: ${e.message}" } }
                    }
                },
                onDragEnd = {
                    config.onDragEnd?.let { fn ->
                        try { fn.call() }
                        catch (e: Exception) { logE(TAG) { "onDragEnd 回调失败: ${e.message}" } }
                    }
                },
                onDragCancel = {
                    config.onDragCancel?.let { fn ->
                        try { fn.call() }
                        catch (e: Exception) { logE(TAG) { "onDragCancel 回调失败: ${e.message}" } }
                    }
                },
                onDrag = { change, dragAmount ->
                    config.onDrag?.let { fn ->
                        try {
                            change.consume()
                            fn.call(dragAmount.x.toDouble(), dragAmount.y.toDouble())
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
                        try { fn.call(offset.x.toDouble(), offset.y.toDouble()) }
                        catch (e: Exception) { logE(TAG) { "onTap 回调失败: ${e.message}" } }
                    }
                },
                onDoubleTap = config.onDoubleTap?.let { fn ->
                    { offset ->
                        try { fn.call(offset.x.toDouble(), offset.y.toDouble()) }
                        catch (e: Exception) { logE(TAG) { "onDoubleTap 回调失败: ${e.message}" } }
                    }
                },
                onLongPress = config.onLongPress?.let { fn ->
                    { offset ->
                        try { fn.call(offset.x.toDouble(), offset.y.toDouble()) }
                        catch (e: Exception) { logE(TAG) { "onLongPress 回调失败: ${e.message}" } }
                    }
                }
            )
        }
    }
}