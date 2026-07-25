package com.luaforge.studio.lxclua.ui.editor.designer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * 设计器主面板（不包含代码编辑器）
 *
 * 负责管理设计器的整体布局：左侧组件库面板、上部预览画布、底部属性/组件树面板。
 * 通过 DesignerViewModel 管理状态，处理代码与节点树的双向同步。
 * 依赖外部通过 CompositionLocalProvider 提供 LocalDesignerState。
 *
 * @param currentCode 当前编辑器代码
 * @param onCodeChanged 代码变更回调（设计器修改了代码后同步回编辑器）
 * @param onRequestEditorText 主动获取编辑器最新文本的方法
 * @param isActive 当前tab是否激活，非激活时不执行预览代码以节省资源
 * @param modifier 修饰符
 */
@Composable
fun DesignerHost(
    currentCode: String,
    onCodeChanged: (String) -> Unit,
    onRequestEditorText: () -> String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val designerVM = LocalDesignerState.current
    val codeToPreview by designerVM.currentCodeToPreview.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var previewCanvasBounds by remember { mutableStateOf<Rect?>(null) }
    var hostOffsetInRoot by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    LaunchedEffect(currentCode, isActive) {
        if (isActive) {
            designerVM.scheduleRefresh(currentCode)
        }
    }

    LaunchedEffect(designerVM.selectedNodePath) {
        if (designerVM.selectedNodePath != null && designerVM.selectedNodePath != "0") {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(designerVM.draggingComponent) {
        if (designerVM.draggingComponent == null) {
            previewCanvasBounds = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { hostOffsetInRoot = it.boundsInRoot().topLeft }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyUp && 
                        (keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace)) {
                        val selectedPath = designerVM.selectedNodePath
                        if (!selectedPath.isNullOrBlank() && selectedPath != "0") {
                            handleDeleteNode(designerVM, selectedPath, onCodeChanged)
                            true
                        } else {
                            false
                        }
                    } else if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Escape) {
                        if (designerVM.draggingComponent != null) {
                            designerVM.endComponentDrag()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
        ) {
            ComponentPalette(
                isExpanded = designerVM.showComponentPalette,
                onToggleExpand = { designerVM.toggleComponentPalette() },
                onComponentClick = { meta -> handleComponentClick(designerVM, meta, onCodeChanged) },
                onComponentDragStart = { meta -> designerVM.startComponentDrag(meta) },
                onDragEvent = { meta, posInRoot, isEnd, isCancel ->
                    if (isCancel) {
                        designerVM.endComponentDrag()
                    } else if (isEnd) {
                        if (posInRoot != null) {
                            val inPreview = previewCanvasBounds?.contains(posInRoot) == true
                            if (inPreview) {
                                val targetPath = designerVM.dropTargetNodePath
                                val insertIndex = designerVM.dropInsertIndex
                                if (targetPath != null && insertIndex >= 0) {
                                    handleComponentDropAt(designerVM, meta, targetPath, insertIndex, onCodeChanged)
                                } else {
                                    handleComponentDrop(designerVM, meta, onCodeChanged)
                                }
                            }
                        }
                        designerVM.endComponentDrag()
                    } else {
                        if (posInRoot != null) {
                            designerVM.updateDragPosition(posInRoot)
                        }
                    }
                },
                modifier = Modifier.fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    PreviewCanvas(
                        luaCode = if (isActive) codeToPreview else "",
                        selectedNodePath = designerVM.selectedNodePath,
                        onNodeSelected = { path -> designerVM.selectNode(path) },
                        onRootNodeReady = { root -> designerVM.setRootNode(root) },
                        previewScale = designerVM.previewScale,
                        onPreviewScaleChange = { scale -> designerVM.setPreviewScale(scale) },
                        previewOffsetX = designerVM.previewOffsetX,
                        previewOffsetY = designerVM.previewOffsetY,
                        onPreviewOffsetChange = { x, y -> designerVM.setPreviewOffset(x, y) },
                        previewDevice = designerVM.previewDevice,
                        isReadOnly = designerVM.isReadOnly,
                        onBoundsChange = { bounds -> previewCanvasBounds = bounds },
                        onToggleBottomPanel = { designerVM.toggleBottomPanel() },
                        isBottomPanelVisible = designerVM.showBottomPanel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                BottomInspectorPanel(
                    isExpanded = designerVM.showBottomPanel,
                    panelHeight = designerVM.bottomPanelHeight,
                    onHeightChange = { designerVM.setBottomPanelHeight(it) },
                    onToggleExpand = { designerVM.toggleBottomPanel() },
                    selectedTab = designerVM.bottomPanelTab,
                    onTabChange = { designerVM.setBottomPanelTab(it) },
                    rootNode = designerVM.rootNode,
                    selectedNodePath = designerVM.selectedNodePath,
                    onNodeSelected = { path -> designerVM.selectNode(path) },
                    onDeleteNode = { path -> handleDeleteNode(designerVM, path, onCodeChanged) },
                    onDuplicateNode = { path -> handleDuplicateNode(designerVM, path, onCodeChanged) },
                    onMoveUp = { path -> handleMoveNodeUp(designerVM, path, onCodeChanged) },
                    onMoveDown = { path -> handleMoveNodeDown(designerVM, path, onCodeChanged) },
                    onTreeNodeDrop = { source, target, asChild ->
                        handleTreeNodeDrop(designerVM, source, target, asChild, onCodeChanged)
                    },
                    onPropertyChanged = { key, value ->
                        handlePropertyChanged(designerVM, key, value, onCodeChanged)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        val draggingComp = designerVM.draggingComponent
        val dragPos = designerVM.dragPosition
        if (draggingComp != null && dragPos != null) {
            val isInPreview = previewCanvasBounds?.contains(dragPos) == true
            Box(
                modifier = Modifier
                    .offset {
                        val halfSizePx = with(density) { 36.dp.roundToPx() }
                        IntOffset(
                            (dragPos.x - hostOffsetInRoot.x).roundToInt() - halfSizePx,
                            (dragPos.y - hostOffsetInRoot.y).roundToInt() - halfSizePx
                        )
                    }
                    .zIndex(1000f)
                    .size(72.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        2.dp,
                        if (isInPreview) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        draggingComp.icon()
                    }
                    Text(
                        text = draggingComp.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 底部属性/组件树面板
 *
 * 可拖拽调整高度、可折叠的底部面板，包含属性和组件树两个标签页。
 *
 * @param isExpanded 面板是否展开
 * @param panelHeight 面板高度(dp)
 * @param onHeightChange 高度变化回调
 * @param onToggleExpand 切换展开/折叠回调
 * @param selectedTab 当前选中的标签页
 * @param onTabChange 标签页切换回调
 * @param rootNode 根节点
 * @param selectedNodePath 选中节点路径
 * @param onNodeSelected 节点选中回调
 * @param onDeleteNode 删除节点回调
 * @param onDuplicateNode 复制节点回调
 * @param onMoveUp 节点上移回调
 * @param onMoveDown 节点下移回调
 * @param onTreeNodeDrop 组件树拖放回调
 * @param onPropertyChanged 属性变更回调
 * @param modifier 修饰符
 */
@Composable
fun BottomInspectorPanel(
    isExpanded: Boolean,
    panelHeight: Int,
    onHeightChange: (Int) -> Unit,
    onToggleExpand: () -> Unit,
    selectedTab: BottomPanelTab,
    onTabChange: (BottomPanelTab) -> Unit,
    rootNode: com.nirithy.luacompose.node.ComposeNode?,
    selectedNodePath: String?,
    onNodeSelected: (String?) -> Unit,
    onDeleteNode: (String) -> Unit,
    onDuplicateNode: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onTreeNodeDrop: (String, String, Boolean) -> Unit,
    onPropertyChanged: (String, Any?) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // 拖拽手柄条（始终可见，折叠时显示一条细线作为展开入口）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(isExpanded) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaDp = with(density) { dragAmount.toDp().value.toInt() }
                            if (isExpanded) {
                                val newHeight = (panelHeight - deltaDp).coerceIn(120, 500)
                                onHeightChange(newHeight)
                                if (newHeight <= 120) {
                                    onToggleExpand()
                                }
                            } else {
                                if (deltaDp < 0) {
                                    val newHeight = (280 - deltaDp).coerceIn(120, 500)
                                    onHeightChange(newHeight)
                                    onToggleExpand()
                                }
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(3.dp)
                    .background(
                        color = if (isDragging) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.outline.copy(alpha = if (isExpanded) 0.5f else 0.3f),
                        shape = RoundedCornerShape(1.5.dp)
                    )
                    .align(Alignment.Center)
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TabRow(
                    selectedTabIndex = if (selectedTab == BottomPanelTab.PROPERTIES) 0 else 1,
                    modifier = Modifier.weight(1f),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    Tab(
                        selected = selectedTab == BottomPanelTab.PROPERTIES,
                        onClick = {
                            onTabChange(BottomPanelTab.PROPERTIES)
                            if (!isExpanded) onToggleExpand()
                        },
                        text = { Text("属性", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == BottomPanelTab.TREE,
                        onClick = {
                            onTabChange(BottomPanelTab.TREE)
                            if (!isExpanded) onToggleExpand()
                        },
                        text = { Text("组件树", fontSize = 13.sp) }
                    )
                }

                IconButton(onClick = onToggleExpand, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isExpanded) "折叠面板" else "展开面板",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            modifier = Modifier.fillMaxWidth().height(panelHeight.dp)
        ) {
            when (selectedTab) {
                BottomPanelTab.PROPERTIES -> {
                    val selectedNode = remember(rootNode, selectedNodePath) {
                        rootNode?.let { root ->
                            val path = selectedNodePath ?: ""
                            if (path.isBlank()) root
                            else root.findNodeByPath(path)
                        }
                    }
                    PropertyPanel(
                        selectedNode = selectedNode,
                        onPropertyChanged = onPropertyChanged,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                BottomPanelTab.TREE -> {
                    ComponentTreePanel(
                        rootNode = rootNode,
                        selectedNodePath = selectedNodePath,
                        onNodeSelected = onNodeSelected,
                        onDeleteNode = onDeleteNode,
                        onDuplicateNode = onDuplicateNode,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onTreeNodeDrop = onTreeNodeDrop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * 处理组件拖拽放置逻辑（与点击添加逻辑相同）
 */
private fun handleComponentDrop(
    designerVM: DesignerViewModel,
    meta: ComponentMeta,
    onCodeChanged: (String) -> Unit
) {
    handleComponentClick(designerVM, meta, onCodeChanged)
}

/**
 * 处理组件拖拽放置到指定位置的逻辑
 *
 * 根据拖拽落点信息，将新组件插入到指定父节点的指定索引位置。
 * 与 handleComponentDrop 不同，此方法使用精确的插入位置而非默认行为。
 *
 * @param designerVM 设计器视图模型
 * @param meta 组件元数据
 * @param targetPath 目标父节点路径
 * @param insertIndex 插入索引
 * @param onCodeChanged 代码变更回调
 */
private fun handleComponentDropAt(
    designerVM: DesignerViewModel,
    meta: ComponentMeta,
    targetPath: String,
    insertIndex: Int,
    onCodeChanged: (String) -> Unit
) {
    val root = designerVM.rootNode ?: return

    val newRoot = NodeMutator.insertComponentAt(root, targetPath, insertIndex, meta)
    val newCode = LuaCodeGenerator.generate(newRoot)

    designerVM.setRootNode(newRoot)
    onCodeChanged(newCode)

    val newNodePath = if (targetPath == "0") {
        "0.$insertIndex"
    } else {
        "$targetPath.$insertIndex"
    }
    designerVM.selectNode(newNodePath)
}

/**
 * 处理组件点击添加逻辑
 */
private fun handleComponentClick(
    designerVM: DesignerViewModel,
    meta: ComponentMeta,
    onCodeChanged: (String) -> Unit
) {
    val root = designerVM.rootNode ?: return

    val selectedPath = designerVM.selectedNodePath
    val parentPath: String
    val parentNode: com.nirithy.luacompose.node.ComposeNode

    if (selectedPath.isNullOrBlank()) {
        parentPath = "0"
        parentNode = root
    } else {
        val selectedNode = root.findNodeByPath(selectedPath)
        if (selectedNode != null) {
            val selectedMeta = ComponentLibrary.findByTypeName(selectedNode.type)
            if (selectedMeta?.canHaveChildren == true) {
                parentPath = selectedPath
                parentNode = selectedNode
            } else {
                parentPath = "0"
                parentNode = root
            }
        } else {
            parentPath = "0"
            parentNode = root
        }
    }

    val newNode = NodeMutator.createNodeFromMeta(meta)
    val parentChildrenBefore = parentNode.children.size

    val mutation = NodeMutation.AddChild(
        parentPath = parentPath,
        child = newNode,
        insertIndex = -1
    )
    val newRoot = NodeMutator.applyMutation(root, mutation)
    val newCode = LuaCodeGenerator.generate(newRoot)

    designerVM.setRootNode(newRoot)
    onCodeChanged(newCode)

    val newNodePath = if (parentPath == "0") {
        "0.$parentChildrenBefore"
    } else {
        "$parentPath.$parentChildrenBefore"
    }
    designerVM.selectNode(newNodePath)
}

/**
 * 处理属性变更逻辑
 */
private fun handlePropertyChanged(
    designerVM: DesignerViewModel,
    key: String,
    value: Any?,
    onCodeChanged: (String) -> Unit
) {
    val root = designerVM.rootNode ?: return
    val nodePath = designerVM.selectedNodePath ?: return

    val mutation = NodeMutation.UpdateProperty(
        nodePath = nodePath,
        key = key,
        value = value
    )
    val newRoot = NodeMutator.applyMutation(root, mutation)
    val newCode = LuaCodeGenerator.generate(newRoot)

    designerVM.setRootNode(newRoot)
    onCodeChanged(newCode)
}

/**
 * 处理删除选中节点
 */
private fun handleDeleteNode(
    designerVM: DesignerViewModel,
    nodePath: String,
    onCodeChanged: (String) -> Unit
) {
    if (nodePath == "0") return
    val root = designerVM.rootNode ?: return
    val newRoot = NodeMutator.applyMutation(root, NodeMutation.RemoveNode(nodePath))
    val newCode = LuaCodeGenerator.generate(newRoot)
    designerVM.setRootNode(newRoot)
    designerVM.selectNode(null)
    onCodeChanged(newCode)
}

/**
 * 处理复制选中节点
 */
private fun handleDuplicateNode(
    designerVM: DesignerViewModel,
    nodePath: String,
    onCodeChanged: (String) -> Unit
) {
    if (nodePath == "0") return
    val root = designerVM.rootNode ?: return
    val newRoot = NodeMutator.applyMutation(root, NodeMutation.Duplicate(nodePath))
    val newCode = LuaCodeGenerator.generate(newRoot)
    designerVM.setRootNode(newRoot)
    val segments = nodePath.split(".")
    val lastIdx = segments.last().toInt()
    val newNodePath = segments.dropLast(1).plus((lastIdx + 1).toString()).joinToString(".")
    designerVM.selectNode(newNodePath)
    onCodeChanged(newCode)
}

/**
 * 处理节点上移
 */
private fun handleMoveNodeUp(
    designerVM: DesignerViewModel,
    nodePath: String,
    onCodeChanged: (String) -> Unit
) {
    if (nodePath == "0") return
    val root = designerVM.rootNode ?: return
    val segments = nodePath.split(".")
    val currentIdx = segments.last().toInt()
    if (currentIdx <= 0) return
    val newRoot = NodeMutator.applyMutation(root, NodeMutation.ReorderSibling(nodePath, -1))
    val newCode = LuaCodeGenerator.generate(newRoot)
    designerVM.setRootNode(newRoot)
    val newNodePath = segments.dropLast(1).plus((currentIdx - 1).toString()).joinToString(".")
    designerVM.selectNode(newNodePath)
    onCodeChanged(newCode)
}

/**
 * 处理节点下移
 */
private fun handleMoveNodeDown(
    designerVM: DesignerViewModel,
    nodePath: String,
    onCodeChanged: (String) -> Unit
) {
    if (nodePath == "0") return
    val root = designerVM.rootNode ?: return
    val segments = nodePath.split(".")
    val currentIdx = segments.last().toInt()
    val parentPath = segments.dropLast(1).joinToString(".")
    val parentNode = if (parentPath == "0") root else root.findNodeByPath(parentPath) ?: return
    if (currentIdx >= parentNode.children.size - 1) return
    val newRoot = NodeMutator.applyMutation(root, NodeMutation.ReorderSibling(nodePath, +1))
    val newCode = LuaCodeGenerator.generate(newRoot)
    designerVM.setRootNode(newRoot)
    val newNodePath = segments.dropLast(1).plus((currentIdx + 1).toString()).joinToString(".")
    designerVM.selectNode(newNodePath)
    onCodeChanged(newCode)
}

/**
 * 处理组件树节点拖放移动
 */
private fun handleTreeNodeDrop(
    designerVM: DesignerViewModel,
    sourcePath: String,
    targetPath: String,
    insertAsChild: Boolean,
    onCodeChanged: (String) -> Unit
) {
    val root = designerVM.rootNode ?: return
    if (sourcePath == "0" || sourcePath == targetPath) return

    val targetNode = root.findNodeByPath(targetPath) ?: return
    val sourceNode = root.findNodeByPath(sourcePath) ?: return

    val sourceSegments = sourcePath.split(".").map { it.toInt() }
    val targetSegments = targetPath.split(".").map { it.toInt() }
    val sourceParentPath = sourceSegments.dropLast(1).joinToString(".")
    val sourceIdx = sourceSegments.last()

    val mutation = if (insertAsChild) {
        val targetMeta = ComponentLibrary.findByTypeName(targetNode.type)
        if (targetMeta?.canHaveChildren != true) return
        NodeMutation.MoveNode(sourcePath, targetPath, -1)
    } else {
        val targetParentPath = targetSegments.dropLast(1).joinToString(".")
        val targetIdx = targetSegments.last()
        val insertIdx = if (targetParentPath == sourceParentPath && targetIdx > sourceIdx) {
            targetIdx
        } else {
            targetIdx
        }
        NodeMutation.MoveNode(sourcePath, targetParentPath, insertIdx)
    }

    val newRoot = NodeMutator.applyMutation(root, mutation)
    val newPath = findNodePathByReference(newRoot, sourceNode)
    val newCode = LuaCodeGenerator.generate(newRoot)

    designerVM.setRootNode(newRoot)
    designerVM.selectNode(newPath)
    onCodeChanged(newCode)
}

/**
 * 在节点树中按引用查找节点路径
 */
private fun findNodePathByReference(root: com.nirithy.luacompose.node.ComposeNode, target: com.nirithy.luacompose.node.ComposeNode): String? {
    if (root === target) return root.nodePath
    for (child in root.children) {
        findNodePathByReference(child, target)?.let { return it }
    }
    return null
}
