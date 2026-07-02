package com.luaforge.studio.lxclua.ui.components.projectcard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * SwipeToDismissBox滑动包装器
 * 实现左滑显示置顶/删除、右滑显示分享/标签的滑动手势
 * 阈值到达时动画复位并调用对应回调
 *
 * @param onTogglePinned 置顶/取消置顶回调
 * @param onDeleteClick 删除回调
 * @param onShareClick 分享回调
 * @param onSetTagsClick 标签管理回调（null则右滑只有分享）
 * @param onSwipeLeft 左滑事件广播（插件事件用）
 * @param onSwipeRight 右滑事件广播（插件事件用）
 * @param colorScheme 颜色方案
 * @param cardShape 卡片形状
 * @param content 被包装的卡片内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissBoxWrapper(
    onTogglePinned: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onSetTagsClick: (() -> Unit)?,
    onSwipeLeft: (() -> Unit)?,
    onSwipeRight: (() -> Unit)?,
    colorScheme: ColorScheme,
    cardShape: RoundedCornerShape,
    content: @Composable () -> Unit
) {
    // 记录滑动过程中各方向达到的最大进度，用于区分触发哪个动作
    var maxEndToStartProgress by remember { mutableFloatStateOf(0f) }
    var maxStartToEndProgress by remember { mutableFloatStateOf(0f) }
    var actionTriggered by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    // 左滑：根据最大进度决定触发置顶(<2/3)还是删除(>=2/3)
                    if (!actionTriggered) {
                        actionTriggered = true
                        if (maxEndToStartProgress >= 0.66f) {
                            onDeleteClick()
                        } else {
                            onTogglePinned()
                        }
                        onSwipeLeft?.invoke()
                    }
                    false // 返回false使卡片复位
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    // 右滑：根据最大进度决定触发分享(<2/3)还是标签(>=2/3)
                    if (!actionTriggered) {
                        actionTriggered = true
                        if (maxStartToEndProgress >= 0.66f && onSetTagsClick != null) {
                            onSetTagsClick()
                        } else {
                            onShareClick()
                        }
                        onSwipeRight?.invoke()
                    }
                    false // 返回false使卡片复位
                }
                SwipeToDismissBoxValue.Settled -> {
                    actionTriggered = false
                    true
                }
            }
        }
    )

    // 跟踪滑动过程中的最大进度
    val currentDirection = dismissState.dismissDirection
    val currentProgress = dismissState.progress
    LaunchedEffect(currentDirection, currentProgress) {
        when (currentDirection) {
            SwipeToDismissBoxValue.EndToStart -> {
                maxEndToStartProgress = maxOf(maxEndToStartProgress, currentProgress)
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                maxStartToEndProgress = maxOf(maxStartToEndProgress, currentProgress)
            }
            SwipeToDismissBoxValue.Settled -> {
                // 复位时清空
                maxEndToStartProgress = 0f
                maxStartToEndProgress = 0f
            }
            null -> { /* dismissDirection为null表示无方向，不处理 */ }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val progress = dismissState.progress

            when (direction) {
                SwipeToDismissBoxValue.EndToStart -> {
                    // 左滑背景：1/3阈值置顶，2/3阈值删除
                    val isDeleteZone = progress >= 0.66f
                    val bgColor = if (isDeleteZone) colorScheme.error else colorScheme.primaryContainer
                    val iconColor = if (isDeleteZone) colorScheme.onError else colorScheme.onPrimaryContainer
                    val icon = if (isDeleteZone) Icons.Filled.Delete else Icons.Filled.Star
                    val label = if (isDeleteZone) "删除" else "置顶"

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(cardShape)
                            .background(bgColor)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = label,
                                color = iconColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    // 右滑背景：分享(近)和标签(远)
                    val isTagZone = progress >= 0.66f
                    val showTag = onSetTagsClick != null

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(cardShape)
                            .background(if (isTagZone && showTag) colorScheme.secondaryContainer else colorScheme.primaryContainer)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (showTag) {
                            // 显示两个动作区域
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // 分享区域
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "分享",
                                        tint = if (!isTagZone) colorScheme.onPrimaryContainer else colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "分享",
                                        color = if (!isTagZone) colorScheme.onPrimaryContainer else colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (!isTagZone) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                // 标签区域
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Label,
                                        contentDescription = "标签",
                                        tint = if (isTagZone) colorScheme.onSecondaryContainer else colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "标签",
                                        color = if (isTagZone) colorScheme.onSecondaryContainer else colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isTagZone) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        } else {
                            // 仅分享
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "分享",
                                    tint = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "分享",
                                    color = colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                SwipeToDismissBoxValue.Settled, null -> {}
            }
        }
    ) {
        content()
    }
}
