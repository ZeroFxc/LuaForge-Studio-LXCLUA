package com.luaforge.studio.lxclua.ui.editor.designer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 组件库面板
 *
 * 可展开/折叠的组件选择面板，支持搜索过滤、分类展示、点击添加和拖拽起始。
 * 折叠时显示为 48dp 窄条，展开时宽度为 220dp。
 *
 * @param isExpanded 面板是否展开
 * @param onToggleExpand 切换展开/折叠状态回调
 * @param onComponentClick 组件点击回调
 * @param onComponentDragStart 组件拖拽开始回调，参数为被拖拽的组件元数据
 * @param onDragEvent 拖拽事件回调（位置更新、结束、取消）
 * @param modifier 修饰符
 */
@Composable
fun ComponentPalette(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onComponentClick: (ComponentMeta) -> Unit,
    onComponentDragStart: (ComponentMeta) -> Unit,
    onDragEvent: (meta: ComponentMeta, positionInRoot: Offset?, isEnd: Boolean, isCancel: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val panelWidth by animateDpAsState(
        targetValue = if (isExpanded) 220.dp else 48.dp,
        label = "panelWidthAnimation"
    )

    Surface(
        modifier = modifier
            .width(panelWidth)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PaletteHeader(
                isExpanded = isExpanded,
                onToggleExpand = onToggleExpand
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandHorizontally(expandFrom = Alignment.Start),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    ComponentList(
                        searchQuery = searchQuery,
                        onComponentClick = onComponentClick,
                        onComponentDragStart = onComponentDragStart,
                        onDragEvent = onDragEvent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 面板标题栏
 *
 * @param isExpanded 是否展开
 * @param onToggleExpand 切换展开/折叠回调
 */
@Composable
private fun PaletteHeader(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isExpanded) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "组件",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = "组件",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onToggleExpand,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) "折叠" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 搜索框
 *
 * @param query 当前搜索关键词
 * @param onQueryChange 关键词变化回调
 * @param modifier 修饰符
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.height(48.dp),
        placeholder = {
            Text(
                text = "搜索组件",
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall
    )
}

/**
 * 组件列表
 *
 * 按分类分组显示组件，支持搜索过滤。
 *
 * @param searchQuery 搜索关键词
 * @param onComponentClick 组件点击回调
 * @param onComponentDragStart 组件拖拽开始回调
 * @param onDragEvent 拖拽事件回调
 * @param modifier 修饰符
 */
@Composable
private fun ComponentList(
    searchQuery: String,
    onComponentClick: (ComponentMeta) -> Unit,
    onComponentDragStart: (ComponentMeta) -> Unit,
    onDragEvent: (meta: ComponentMeta, positionInRoot: Offset?, isEnd: Boolean, isCancel: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredComponents = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            ComponentLibrary.allComponents
        } else {
            ComponentLibrary.search(searchQuery)
        }
    }

    val groupedComponents = remember(filteredComponents) {
        filteredComponents.groupBy { it.category }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        enumValues<ComponentCategory>().forEach { category ->
            val components = groupedComponents[category] ?: return@forEach
            if (components.isEmpty()) return@forEach

            item {
                CategoryHeader(
                    title = category.displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp, start = 8.dp, end = 8.dp)
                )
            }

            items(components, key = { it.typeName }) { component ->
                ComponentItem(
                    component = component,
                    onClick = { onComponentClick(component) },
                    onDragStart = { onComponentDragStart(component) },
                    onDragEvent = { pos, isEnd, isCancel -> onDragEvent(component, pos, isEnd, isCancel) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 分类标题
 *
 * @param title 分类名称
 * @param modifier 修饰符
 */
@Composable
private fun CategoryHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * 单个组件项
 *
 * 显示组件图标和名称，支持点击、悬停高亮和长按拖拽检测。
 * 高度固定为 48dp。
 *
 * @param component 组件元数据
 * @param onClick 点击回调
 * @param onDragStart 拖拽开始回调
 * @param onDragEvent 拖拽事件回调（位置在根布局中、是否结束、是否取消）
 * @param modifier 修饰符
 */
@Composable
private fun ComponentItem(
    component: ComponentMeta,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragEvent: (positionInRoot: Offset?, isEnd: Boolean, isCancel: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var itemOffsetInRoot by remember { mutableStateOf(Offset.Zero) }
    var lastDragPosition by remember { mutableStateOf<Offset?>(null) }

    val itemOffsetInRootState = rememberUpdatedState(itemOffsetInRoot)

    val backgroundColor = when {
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier
            .height(48.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .hoverable(interactionSource)
            .onGloballyPositioned { coords ->
                itemOffsetInRoot = coords.boundsInRoot().topLeft
            }
            .pointerInput(component.typeName) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { _ ->
                        lastDragPosition = null
                        onDragStart()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val localPos = change.position
                        val currentOffset = itemOffsetInRootState.value
                        val rootPos = Offset(
                            localPos.x + currentOffset.x,
                            localPos.y + currentOffset.y
                        )
                        lastDragPosition = rootPos
                        onDragEvent(rootPos, false, false)
                    },
                    onDragEnd = {
                        onDragEvent(lastDragPosition, true, false)
                        lastDragPosition = null
                    },
                    onDragCancel = {
                        onDragEvent(null, false, true)
                        lastDragPosition = null
                    }
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                component.icon()
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = component.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
