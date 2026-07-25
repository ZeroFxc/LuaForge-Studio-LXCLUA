package com.luaforge.studio.lxclua.ui.editor.designer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirithy.luacompose.node.ComposeNode

private data class TreeNodeItem(
    val node: ComposeNode,
    val depth: Int,
    val hasChildren: Boolean,
    val isDynamicContent: Boolean
)

@Composable
fun ComponentTreePanel(
    rootNode: ComposeNode?,
    selectedNodePath: String?,
    onNodeSelected: (String?) -> Unit,
    onDeleteNode: (String) -> Unit = {},
    onDuplicateNode: (String) -> Unit = {},
    onMoveUp: (String) -> Unit = {},
    onMoveDown: (String) -> Unit = {},
    onTreeNodeDrop: (sourcePath: String, targetPath: String, insertAsChild: Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val designerVM = LocalDesignerState.current
    val listState = rememberLazyListState()
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    var refreshKey by remember { mutableStateOf(0) }
    val expandedPaths = remember { mutableSetOf<String>() }
    var expandedPathsVersion by remember { mutableStateOf(0) }

    LaunchedEffect(rootNode, refreshKey) {
        if (rootNode != null) {
            val defaultExpanded = mutableSetOf<String>()
            collectInitialExpandedPaths(rootNode, 0, 2, defaultExpanded)
            expandedPaths.clear()
            expandedPaths.addAll(defaultExpanded)
            expandedPathsVersion++
        } else {
            expandedPaths.clear()
            expandedPathsVersion++
        }
    }

    val dragPos = designerVM.treeDragPosition
    val dragPath = designerVM.draggingTreePath
    LaunchedEffect(dragPos, itemBounds.size) {
        if (dragPos == null) {
            designerVM.setTreeDragTarget(null, false)
            return@LaunchedEffect
        }
        var bestPath: String? = null
        var bestAsChild = false
        for ((path, rect) in itemBounds) {
            if (rect.contains(dragPos)) {
                bestPath = path
                val relY = dragPos.y - rect.top
                val item = rootNode?.findNodeByPath(path)
                val hasChildren = item != null && (item.children.isNotEmpty() || item.childrenFunc != null)
                bestAsChild = hasChildren && relY > rect.height * 0.6f
                break
            }
        }
        if (bestPath != null && bestPath != dragPath) {
            designerVM.setTreeDragTarget(bestPath, bestAsChild)
        } else {
            designerVM.setTreeDragTarget(null, false)
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TreeHeader(
                onExpandAll = {
                    if (rootNode != null) {
                        val allPaths = mutableSetOf<String>()
                        collectAllPaths(rootNode, allPaths)
                        expandedPaths.clear()
                        expandedPaths.addAll(allPaths)
                        expandedPathsVersion++
                    }
                },
                onCollapseAll = {
                    expandedPaths.clear()
                    expandedPathsVersion++
                },
                onRefresh = {
                    refreshKey++
                }
            )

            if (rootNode == null) {
                EmptyState()
            } else {
                val items = remember(rootNode, expandedPathsVersion, refreshKey) {
                    flattenTree(rootNode, expandedPaths)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(items, key = { _, item -> item.node.nodePath ?: "${item.node.type}_${item.depth}" }) { _, item ->
                        val nodePath = item.node.nodePath
                        val isDragging = dragPath != null && dragPath == nodePath
                        val isDropTarget = dragPath != null && designerVM.treeDragTargetPath == nodePath
                        val dropAsChild = isDropTarget && designerVM.treeDragInsertAsChild
                        TreeNodeRow(
                            item = item,
                            isSelected = nodePath == selectedNodePath,
                            isExpanded = nodePath?.let { expandedPaths.contains(it) } ?: false,
                            isDragging = isDragging,
                            isDropTarget = isDropTarget,
                            dropAsChild = dropAsChild,
                            onToggleExpand = { path ->
                                if (expandedPaths.contains(path)) {
                                    expandedPaths.remove(path)
                                } else {
                                    expandedPaths.add(path)
                                }
                                expandedPathsVersion++
                            },
                            onSelect = { path ->
                                onNodeSelected(path)
                            },
                            onDeleteNode = onDeleteNode,
                            onDuplicateNode = onDuplicateNode,
                            onMoveUp = onMoveUp,
                            onMoveDown = onMoveDown,
                            onDragStart = { path ->
                                designerVM.startTreeDrag(path)
                            },
                            onDragMoved = { rootPos ->
                                designerVM.updateTreeDragPosition(rootPos)
                            },
                            onDragEnd = {
                                val source = designerVM.draggingTreePath
                                val target = designerVM.treeDragTargetPath
                                val asChild = designerVM.treeDragInsertAsChild
                                if (source != null && target != null && source != target) {
                                    val sourceNode = rootNode.findNodeByPath(source)
                                    val targetIsDescendant = sourceNode?.let { isNodeDescendant(it, target) } ?: false
                                    if (!targetIsDescendant) {
                                        onTreeNodeDrop(source, target, asChild)
                                    }
                                }
                                designerVM.endTreeDrag()
                            },
                            onDragCancel = {
                                designerVM.endTreeDrag()
                            },
                            onBoundsChanged = { p, bounds ->
                                if (bounds != null && p != null) {
                                    itemBounds[p] = bounds
                                } else if (p != null) {
                                    itemBounds.remove(p)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun isNodeDescendant(node: ComposeNode, targetPath: String): Boolean {
    if (node.nodePath == targetPath) return true
    for (child in node.children) {
        if (isNodeDescendant(child, targetPath)) return true
    }
    return false
}

private fun collectAllPaths(node: ComposeNode, paths: MutableSet<String>) {
    val path = node.nodePath ?: return
    if (node.children.isNotEmpty() || node.childrenFunc != null) {
        paths.add(path)
    }
    for (child in node.children) {
        collectAllPaths(child, paths)
    }
}

private fun collectInitialExpandedPaths(node: ComposeNode, currentDepth: Int, maxDepth: Int, paths: MutableSet<String>) {
    val path = node.nodePath ?: return
    if (currentDepth < maxDepth && (node.children.isNotEmpty() || node.childrenFunc != null)) {
        paths.add(path)
        for (child in node.children) {
            collectInitialExpandedPaths(child, currentDepth + 1, maxDepth, paths)
        }
    }
}

private fun flattenTree(root: ComposeNode, expandedPaths: Set<String>): List<TreeNodeItem> {
    val result = mutableListOf<TreeNodeItem>()
    flattenNode(root, 0, expandedPaths, result)
    return result
}

private fun flattenNode(
    node: ComposeNode,
    depth: Int,
    expandedPaths: Set<String>,
    result: MutableList<TreeNodeItem>
) {
    val hasChildren = node.children.isNotEmpty() || node.childrenFunc != null
    val isDynamic = node.childrenFunc != null && node.children.isEmpty()
    result.add(TreeNodeItem(node, depth, hasChildren, isDynamic))

    val path = node.nodePath
    if (path != null && expandedPaths.contains(path)) {
        for (child in node.children) {
            flattenNode(child, depth + 1, expandedPaths, result)
        }
    }
}

@Composable
private fun TreeHeader(
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Widgets,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "组件树",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onExpandAll,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = "展开全部",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onCollapseAll,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.UnfoldLess,
                contentDescription = "折叠全部",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "刷新",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无组件，请在编辑器中编写代码",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TreeNodeRow(
    item: TreeNodeItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    isDragging: Boolean = false,
    isDropTarget: Boolean = false,
    dropAsChild: Boolean = false,
    onToggleExpand: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onDeleteNode: (String) -> Unit,
    onDuplicateNode: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onDragStart: (String) -> Unit,
    onDragMoved: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onBoundsChanged: (String?, Rect?) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val node = item.node
    val path = node.nodePath

    var itemRootOffset by remember { mutableStateOf(Offset.Zero) }
    val itemRootOffsetState = rememberUpdatedState(itemRootOffset)

    val backgroundColor = when {
        isDragging -> Color.Transparent
        isDropTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        else -> Color.Transparent
    }

    val borderStroke = when {
        isDropTarget && dropAsChild -> BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
        isDropTarget -> BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        else -> null
    }

    val leftBorderColor = if (isDropTarget && !dropAsChild) {
        MaterialTheme.colorScheme.primary
    } else null

    val meta = remember(node.type) { ComponentLibrary.findByTypeName(node.type) }
    val displayName = meta?.displayName ?: node.type
    val description = remember(node.type, node.props) { getNodeDescription(node) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .graphicsLayer {
                if (isDragging) alpha = 0.4f
            }
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInRoot()
                itemRootOffset = bounds.topLeft
                onBoundsChanged(path, bounds)
            }
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (leftBorderColor != null) {
                    Modifier.drawBehind {
                        drawRect(
                            color = leftBorderColor,
                            topLeft = Offset.Zero,
                            size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                        )
                    }
                } else Modifier
            )
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onSelect(path) }
            )
            .pointerInput(path) {
                if (path == null || path == "0") return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        onDragStart(path)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val rootPos = itemRootOffsetState.value + change.position
                        onDragMoved(rootPos)
                    },
                    onDragEnd = {
                        onDragEnd()
                    },
                    onDragCancel = {
                        onDragCancel()
                    }
                )
            },
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp),
        border = borderStroke
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp + 24.dp * item.depth, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.hasChildren) {
                IconButton(
                    onClick = {
                        path?.let { onToggleExpand(it) }
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (isExpanded) "折叠" else "展开",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(2.dp))

            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center
            ) {
                if (meta != null) {
                    meta.icon()
                } else {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = displayName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (description != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.isDynamicContent) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "[动态内容]",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 10.sp
                )
            }

            if (dropAsChild && isDropTarget) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.SubdirectoryArrowRight,
                    contentDescription = "放入内部",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isHovered || isSelected) {
                val isRoot = path == "0" || item.depth == 0
                IconButton(
                    onClick = { path?.let { onDuplicateNode(it) } },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isRoot) {
                    IconButton(
                        onClick = { path?.let { onMoveUp(it) } },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "上移",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { path?.let { onMoveDown(it) } },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "下移",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { path?.let { onDeleteNode(it) } },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun getNodeDescription(node: ComposeNode): String? {
    return when (node.type) {
        "Text" -> {
            val text = node.stringProp("text")
            if (!text.isNullOrEmpty()) {
                if (text.length > 12) text.take(12) + "..." else text
            } else null
        }
        else -> null
    }
}
