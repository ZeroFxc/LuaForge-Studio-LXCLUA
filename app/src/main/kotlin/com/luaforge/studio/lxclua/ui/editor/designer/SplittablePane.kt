package com.luaforge.studio.lxclua.ui.editor.designer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * 可拖拽分割布局容器
 *
 * 支持左右分割或上下分割，提供可拖拽的分割线调整两侧面板比例。
 * 支持双击分割线重置比例，全屏模式平滑过渡。
 *
 * @param modifier 修饰符
 * @param firstPane 第一个面板内容（左侧/上方，代码区）
 * @param secondPane 第二个面板内容（右侧/下方，设计区）
 * @param splitRatio 第一个面板占比，范围 0-1
 * @param onSplitRatioChange 比例变化回调
 * @param isVertical true=左右分割（垂直分割线），false=上下分割（水平分割线）
 * @param isFullscreenFirst 是否全屏显示第一个面板
 * @param isFullscreenSecond 是否全屏显示第二个面板
 */
@Composable
fun SplittablePane(
    modifier: Modifier = Modifier,
    firstPane: @Composable () -> Unit,
    secondPane: @Composable () -> Unit,
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit,
    isVertical: Boolean = true,
    isFullscreenFirst: Boolean = false,
    isFullscreenSecond: Boolean = false,
) {
    // 分割线拖拽状态
    var isDragging by remember { mutableStateOf(false) }

    // 全屏动画目标比例
    val targetRatio = when {
        isFullscreenFirst -> 1f
        isFullscreenSecond -> 0f
        else -> splitRatio.coerceIn(0.15f, 0.85f)
    }

    // 全屏切换时使用动画平滑过渡
    val animatedRatio by animateFloatAsState(
        targetValue = targetRatio,
        label = "splitRatioAnimation"
    )

    // 实际使用的比例：全屏时用动画值，拖拽时实时更新
    val actualRatio = when {
        isFullscreenFirst || isFullscreenSecond -> animatedRatio
        else -> splitRatio.coerceIn(0.15f, 0.85f)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val containerWidth = maxWidth.value
        val containerHeight = maxHeight.value

        if (isVertical) {
            // 左右分割布局
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // 第一个面板（左侧）
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(actualRatio)
                ) {
                    firstPane()
                }

                // 分割线
                VerticalDivider(
                    isDragging = isDragging,
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDrag = { dragAmount ->
                        if (!isFullscreenFirst && !isFullscreenSecond) {
                            val deltaRatio = dragAmount / containerWidth
                            val newRatio = (splitRatio + deltaRatio).coerceIn(0.15f, 0.85f)
                            onSplitRatioChange(newRatio)
                        }
                    },
                    onDoubleClick = {
                        if (!isFullscreenFirst && !isFullscreenSecond) {
                            onSplitRatioChange(0.5f)
                        }
                    }
                )

                // 第二个面板（右侧）
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f - actualRatio)
                ) {
                    secondPane()
                }
            }
        } else {
            // 上下分割布局
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 第一个面板（上方）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(actualRatio)
                ) {
                    firstPane()
                }

                // 分割线
                HorizontalDivider(
                    isDragging = isDragging,
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDrag = { dragAmount ->
                        if (!isFullscreenFirst && !isFullscreenSecond) {
                            val deltaRatio = dragAmount / containerHeight
                            val newRatio = (splitRatio + deltaRatio).coerceIn(0.15f, 0.85f)
                            onSplitRatioChange(newRatio)
                        }
                    },
                    onDoubleClick = {
                        if (!isFullscreenFirst && !isFullscreenSecond) {
                            onSplitRatioChange(0.5f)
                        }
                    }
                )

                // 第二个面板（下方）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - actualRatio)
                ) {
                    secondPane()
                }
            }
        }
    }
}

/**
 * 垂直分割线（左右分割用）
 *
 * @param isDragging 是否正在拖拽
 * @param onDragStart 拖拽开始回调
 * @param onDragEnd 拖拽结束回调
 * @param onDrag 拖拽中回调，参数：水平拖拽增量
 * @param onDoubleClick 双击回调
 */
@Composable
private fun VerticalDivider(
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (dragAmount: Float) -> Unit,
    onDoubleClick: () -> Unit,
) {
    val dividerColor = if (isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val indicatorColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(6.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleClick() }
                )
            },
        color = dividerColor,
        tonalElevation = if (isDragging) 2.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            // 拖拽指示条
            Surface(
                modifier = Modifier
                    .fillMaxHeight(0.4f)
                    .width(2.dp),
                color = indicatorColor.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            ) {}
        }
    }
}

/**
 * 水平分割线（上下分割用）
 *
 * @param isDragging 是否正在拖拽
 * @param onDragStart 拖拽开始回调
 * @param onDragEnd 拖拽结束回调
 * @param onDrag 拖拽中回调，参数：垂直拖拽增量
 * @param onDoubleClick 双击回调
 */
@Composable
private fun HorizontalDivider(
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (dragAmount: Float) -> Unit,
    onDoubleClick: () -> Unit,
) {
    val dividerColor = if (isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val indicatorColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleClick() }
                )
            },
        color = dividerColor,
        tonalElevation = if (isDragging) 2.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            // 拖拽指示条
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(2.dp),
                color = indicatorColor.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small
            ) {}
        }
    }
}

/**
 * 根据屏幕宽度记住分割方向
 *
 * @return true=左右分割（宽度>=600dp），false=上下分割
 */
@Composable
fun rememberSplitOrientation(): Boolean {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        configuration.screenWidthDp >= 600
    }
}
