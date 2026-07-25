package com.luaforge.studio.lxclua.ui.editor.designer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.preview.PreviewComposeHost

/**
 * 预览画布组件
 *
 * 包含顶部工具栏（设备选择、缩放控制、全屏）和下方预览内容区。
 * 支持双指缩放、平移、节点选中、设备边框、棋盘格背景。
 *
 * @param luaCode 要预览的 Lua 代码
 * @param selectedNodePath 当前选中的节点路径
 * @param onNodeSelected 节点选中回调（null 表示取消选中）
 * @param onRootNodeReady 根节点就绪回调，渲染成功后传递节点树
 * @param previewScale 预览缩放比例
 * @param onPreviewScaleChange 缩放比例变化回调
 * @param previewOffsetX 预览水平偏移
 * @param previewOffsetY 预览垂直偏移
 * @param onPreviewOffsetChange 偏移变化回调
 * @param previewDevice 预览设备类型
 * @param isReadOnly 是否只读模式
 * @param onBoundsChange 预览区域边界变化回调（用于拖拽放置判断）
 * @param onToggleBottomPanel 切换底部面板回调
 * @param isBottomPanelVisible 底部面板是否可见
 * @param modifier 外部 Modifier
 */
@Composable
fun PreviewCanvas(
    luaCode: String,
    selectedNodePath: String?,
    onNodeSelected: (String?) -> Unit,
    onRootNodeReady: (ComposeNode?) -> Unit,
    previewScale: Float,
    onPreviewScaleChange: (Float) -> Unit,
    previewOffsetX: Float,
    previewOffsetY: Float,
    onPreviewOffsetChange: (Float, Float) -> Unit,
    previewDevice: PreviewDevice,
    isReadOnly: Boolean = false,
    onBoundsChange: ((Rect) -> Unit)? = null,
    onToggleBottomPanel: () -> Unit = {},
    isBottomPanelVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    val designerViewModel = LocalDesignerState.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val density = LocalDensity.current
    var canvasBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    Column(modifier = modifier) {
        PreviewToolbar(
            previewDevice = previewDevice,
            onDeviceChange = { designerViewModel.setPreviewDevice(it) },
            previewScale = previewScale,
            onScaleChange = onPreviewScaleChange,
            isFullscreen = designerViewModel.isFullscreenPreview,
            onFullscreenToggle = { designerViewModel.toggleFullscreen() },
            onResetTransform = { designerViewModel.resetPreviewTransform() },
            designerMode = designerViewModel.designerMode,
            onCloseDesigner = { designerViewModel.setMode(DesignerMode.OFF) },
            onToggleMode = {
                val newMode = if (designerViewModel.designerMode == DesignerMode.CODE_DESIGN) {
                    DesignerMode.DESIGN_ONLY
                } else {
                    DesignerMode.CODE_DESIGN
                }
                designerViewModel.setMode(newMode)
            },
            onTogglePalette = { designerViewModel.toggleComponentPalette() },
            isPaletteVisible = designerViewModel.showComponentPalette,
            onToggleBottomPanel = onToggleBottomPanel,
            isBottomPanelVisible = isBottomPanelVisible,
            onCopyCode = {
                clipboardManager.setText(AnnotatedString(luaCode))
                Toast.makeText(context, "代码已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .checkerboardBackground()
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInRoot()
                    canvasBoundsInRoot = bounds
                    onBoundsChange?.invoke(bounds)
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        onNodeSelected(null)
                    }
                }
        ) {
            PreviewDeviceFrame(
                previewDevice = previewDevice,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = previewScale
                            scaleY = previewScale
                            translationX = previewOffsetX
                            translationY = previewOffsetY
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (previewScale * zoom).coerceIn(0.3f, 3.0f)
                                onPreviewScaleChange(newScale)
                                onPreviewOffsetChange(
                                    previewOffsetX + pan.x,
                                    previewOffsetY + pan.y
                                )
                            }
                        }
                ) {
                    PreviewComposeHost(
                        luaCode = luaCode,
                        isPreviewMode = true,
                        selectedNodePath = selectedNodePath,
                        onNodeSelected = { path -> onNodeSelected(path) },
                        onRootNodeAvailable = { root -> onRootNodeReady(root) },
                        onPreviewError = { error -> designerViewModel.setPreviewError(error) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 拖拽落点目标计算与插入线绘制
            val draggingComp = designerViewModel.draggingComponent
            val dragPos = designerViewModel.dragPosition
            if (draggingComp != null && dragPos != null) {
                val canvasLocalY = dragPos.y - canvasBoundsInRoot.top
                // 计算设备内容区域高度
                val deviceHeightDp = when (previewDevice) {
                    PreviewDevice.PHONE -> 640f
                    PreviewDevice.TABLET -> 800f
                    PreviewDevice.FILL -> with(density) { canvasBoundsInRoot.height / density.density / previewScale }
                }
                val deviceHeightPx = with(density) { deviceHeightDp.dp.toPx() }
                val deviceFrameTopPx = (canvasBoundsInRoot.height - deviceHeightPx * previewScale) / 2f

                // 预览内容中的Y坐标（px，已转换到内容坐标系）
                val previewLocalY = (canvasLocalY - deviceFrameTopPx - previewOffsetY) / previewScale

                val rootNode = designerViewModel.rootNode
                val childCount = rootNode?.children?.size ?: 0
                // 每个子节点的预估高度（内容坐标系px）
                val segmentHeightPx = if (childCount > 0) deviceHeightPx / (childCount + 1) else deviceHeightPx / 2f
                val insertIndex = (previewLocalY / segmentHeightPx).toInt().coerceIn(0, childCount)

                designerViewModel.updateDropTarget("0", insertIndex)

                // 绘制蓝色插入指示线（屏幕坐标系px）
                val lineY = deviceFrameTopPx + insertIndex * segmentHeightPx * previewScale + previewOffsetY
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .offset { IntOffset(0, lineY.roundToInt()) }
                        .background(Color.Blue.copy(alpha = 0.8f))
                        .zIndex(100f)
                )
            } else {
                designerViewModel.clearDropTarget()
            }

            if (isReadOnly) {
                ReadOnlyBadge(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
    }
}

/**
 * 预览工具栏
 */
@Composable
private fun PreviewToolbar(
    previewDevice: PreviewDevice,
    onDeviceChange: (PreviewDevice) -> Unit,
    previewScale: Float,
    onScaleChange: (Float) -> Unit,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    onResetTransform: () -> Unit,
    designerMode: DesignerMode = DesignerMode.CODE_DESIGN,
    onCloseDesigner: (() -> Unit)? = null,
    onToggleMode: (() -> Unit)? = null,
    onTogglePalette: () -> Unit = {},
    isPaletteVisible: Boolean = true,
    onToggleBottomPanel: () -> Unit = {},
    isBottomPanelVisible: Boolean = false,
    onCopyCode: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceSegmentButton(
                    selected = previewDevice == PreviewDevice.PHONE,
                    onClick = { onDeviceChange(PreviewDevice.PHONE) },
                    text = "手机"
                )
                Spacer(modifier = Modifier.width(4.dp))
                DeviceSegmentButton(
                    selected = previewDevice == PreviewDevice.TABLET,
                    onClick = { onDeviceChange(PreviewDevice.TABLET) },
                    text = "平板"
                )
                Spacer(modifier = Modifier.width(4.dp))
                DeviceSegmentButton(
                    selected = previewDevice == PreviewDevice.FILL,
                    onClick = { onDeviceChange(PreviewDevice.FILL) },
                    text = "填充"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onScaleChange((previewScale - 0.1f).coerceIn(0.3f, 3.0f)) }) {
                    Icon(Icons.Default.Remove, contentDescription = "缩小", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "${(previewScale * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { onScaleChange((previewScale + 0.1f).coerceIn(0.3f, 3.0f)) }) {
                    Icon(Icons.Default.Add, contentDescription = "放大", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onResetTransform) {
                    Icon(Icons.Default.RotateLeft, contentDescription = "重置", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePalette) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = if (isPaletteVisible) "隐藏组件库" else "显示组件库",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onToggleBottomPanel) {
                    Icon(
                        imageVector = if (isBottomPanelVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isBottomPanelVisible) "折叠底部面板" else "展开底部面板",
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (onToggleMode != null) {
                    IconButton(onClick = onToggleMode) {
                        Icon(
                            imageVector = if (designerMode == DesignerMode.CODE_DESIGN) Icons.Default.Fullscreen else Icons.Default.Code,
                            contentDescription = if (designerMode == DesignerMode.CODE_DESIGN) "纯设计模式" else "分屏模式",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(onClick = onFullscreenToggle) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onCopyCode) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制代码",
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (onCloseDesigner != null) {
                    IconButton(onClick = onCloseDesigner) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭设计器",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 设备分段选择按钮
 */
@Composable
private fun DeviceSegmentButton(
    selected: Boolean,
    onClick: () -> Unit,
    text: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * 设备外框
 */
@Composable
private fun PreviewDeviceFrame(
    previewDevice: PreviewDevice,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when (previewDevice) {
        PreviewDevice.PHONE -> {
            val widthDp = 360.dp
            val heightDp = 640.dp
            Box(
                modifier = modifier
                    .size(widthDp, heightDp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 4.dp,
                        color = Color.DarkGray,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(MaterialTheme.colorScheme.background)
            ) {
                content()
            }
        }
        PreviewDevice.TABLET -> {
            val widthDp = 600.dp
            val heightDp = 800.dp
            Box(
                modifier = modifier
                    .size(widthDp, heightDp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 4.dp,
                        color = Color.DarkGray,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(MaterialTheme.colorScheme.background)
            ) {
                content()
            }
        }
        PreviewDevice.FILL -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                content()
            }
        }
    }
}

/**
 * 只读标记徽章
 */
@Composable
private fun ReadOnlyBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = "只读",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 棋盘格背景 Modifier
 * 深色模式下使用深灰配色，浅色模式下使用浅灰配色
 */
@Composable
private fun Modifier.checkerboardBackground(): Modifier {
    val isDark = isSystemInDarkTheme()
    val lightColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val darkColor = if (isDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    }
    val gridSize = with(LocalDensity.current) { 16.dp.toPx() }
    return this.drawBehind {
        val cols = (size.width / gridSize).toInt() + 1
        val rows = (size.height / gridSize).toInt() + 1
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val isLight = (row + col) % 2 == 0
                drawRect(
                    color = if (isLight) lightColor else darkColor,
                    topLeft = Offset(col * gridSize, row * gridSize),
                    size = Size(gridSize, gridSize)
                )
            }
        }
    }
}
