package com.luaforge.studio.lxclua.ui.editor.designer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import com.nirithy.luacompose.node.ComposeNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设计器视图模型
 *
 * 独立于 EditorViewModel 的设计器状态管理器，与 EditorViewModel 共存。
 * 使用 Compose mutableStateOf 管理 UI 状态，使用协程实现防抖刷新逻辑。
 *
 * 主要职责：
 * - 管理设计器模式切换（OFF/CODE_DESIGN/DESIGN_ONLY）
 * - 管理节点选中状态
 * - 管理分屏比例
 * - 管理预览变换（缩放、平移、设备类型）
 * - 管理预览错误与降级渲染
 * - 管理组件库面板和底部属性面板
 * - 实现代码变更防抖刷新（800ms）
 */
class DesignerViewModel {

    private val _designerMode: MutableState<DesignerMode> = mutableStateOf(DesignerMode.OFF)
    /** 设计器模式 */
    val designerMode: DesignerMode get() = _designerMode.value

    private val _selectedNodePath: MutableState<String?> = mutableStateOf(null)
    /** 选中节点的路径ID */
    val selectedNodePath: String? get() = _selectedNodePath.value

    private val _splitRatio: MutableState<Float> = mutableStateOf(0.4f)
    /** 代码/设计分割比例 */
    val splitRatio: Float get() = _splitRatio.value

    private val _isFullscreenPreview: MutableState<Boolean> = mutableStateOf(false)
    /** 是否全屏预览 */
    val isFullscreenPreview: Boolean get() = _isFullscreenPreview.value

    private val _previewScale: MutableState<Float> = mutableStateOf(1f)
    /** 预览缩放比例 */
    val previewScale: Float get() = _previewScale.value

    private val _previewOffsetX: MutableState<Float> = mutableStateOf(0f)
    /** 预览水平偏移 */
    val previewOffsetX: Float get() = _previewOffsetX.value

    private val _previewOffsetY: MutableState<Float> = mutableStateOf(0f)
    /** 预览垂直偏移 */
    val previewOffsetY: Float get() = _previewOffsetY.value

    private val _previewDevice: MutableState<PreviewDevice> = mutableStateOf(PreviewDevice.PHONE)
    /** 预览设备类型 */
    val previewDevice: PreviewDevice get() = _previewDevice.value

    private val _previewError: MutableState<String?> = mutableStateOf(null)
    /** 预览错误信息 */
    val previewError: String? get() = _previewError.value

    private val _lastSuccessfulCode: MutableState<String?> = mutableStateOf(null)
    /** 最后一次成功渲染的代码（降级用） */
    val lastSuccessfulCode: String? get() = _lastSuccessfulCode.value

    private val _lastSuccessfulRoot: MutableState<ComposeNode?> = mutableStateOf(null)
    /** 最后一次成功渲染的根节点（降级用） */
    val lastSuccessfulRoot: ComposeNode? get() = _lastSuccessfulRoot.value

    private val _rootNode: MutableState<ComposeNode?> = mutableStateOf(null)
    /** 当前预览的根节点（组件树显示用） */
    val rootNode: ComposeNode? get() = _rootNode.value

    private val _showComponentPalette: MutableState<Boolean> = mutableStateOf(false)
    /** 组件库面板是否展开 */
    val showComponentPalette: Boolean get() = _showComponentPalette.value

    private val _showBottomPanel: MutableState<Boolean> = mutableStateOf(false)
    /** 底部面板是否显示 */
    val showBottomPanel: Boolean get() = _showBottomPanel.value

    private val _bottomPanelHeight: MutableState<Int> = mutableStateOf(280)
    /** 底部面板高度(dp) */
    val bottomPanelHeight: Int get() = _bottomPanelHeight.value

    private val _bottomPanelTab: MutableState<BottomPanelTab> = mutableStateOf(BottomPanelTab.PROPERTIES)
    /** 底部面板当前标签页 */
    val bottomPanelTab: BottomPanelTab get() = _bottomPanelTab.value

    private val _isReadOnly: MutableState<Boolean> = mutableStateOf(false)
    /** 是否只读模式 */
    val isReadOnly: Boolean get() = _isReadOnly.value

    private val _draggingComponent: MutableState<ComponentMeta?> = mutableStateOf(null)
    /** 当前正在拖拽的组件，null 表示没有拖拽 */
    val draggingComponent: ComponentMeta? get() = _draggingComponent.value

    private val _dragPosition: MutableState<Offset?> = mutableStateOf(null)
    /** 拖拽指针的全局位置（相对于DesignerHost根布局） */
    val dragPosition: Offset? get() = _dragPosition.value

    private val _draggingTreePath: MutableState<String?> = mutableStateOf(null)
    /** 组件树中正在拖拽的节点路径 */
    val draggingTreePath: String? get() = _draggingTreePath.value

    private val _treeDragPosition: MutableState<Offset?> = mutableStateOf(null)
    /** 组件树拖拽指针位置（相对于根布局） */
    val treeDragPosition: Offset? get() = _treeDragPosition.value

    private val _treeDragTargetPath: MutableState<String?> = mutableStateOf(null)
    /** 组件树拖拽悬停的目标节点路径 */
    val treeDragTargetPath: String? get() = _treeDragTargetPath.value

    private val _treeDragInsertAsChild: MutableState<Boolean> = mutableStateOf(false)
    /** 组件树拖拽是否插入为目标节点的子节点 */
    val treeDragInsertAsChild: Boolean get() = _treeDragInsertAsChild.value

    private val _dropTargetNodePath: MutableState<String?> = mutableStateOf(null)
    /** 拖拽落点目标节点路径，null 表示无有效目标 */
    val dropTargetNodePath: String? get() = _dropTargetNodePath.value

    private val _dropInsertIndex: MutableState<Int> = mutableStateOf(-1)
    /** 拖拽落点插入索引，-1 表示追加到末尾 */
    val dropInsertIndex: Int get() = _dropInsertIndex.value

    /** 需要预览的代码流，防抖后更新 */
    private val _currentCodeToPreview = MutableStateFlow("")
    val currentCodeToPreview: StateFlow<String> = _currentCodeToPreview.asStateFlow()

    /** 协程作用域，用于防抖任务 */
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 防抖刷新任务 */
    private var refreshJob: Job? = null

    /** 防抖延迟时间（毫秒） */
    private val REFRESH_DELAY_MS = 800L

    /**
     * 切换设计器开关
     * 在 OFF 和 CODE_DESIGN 之间切换
     */
    fun toggleDesigner() {
        _designerMode.value = if (_designerMode.value == DesignerMode.OFF) {
            DesignerMode.CODE_DESIGN
        } else {
            DesignerMode.OFF
        }
    }

    /**
     * 设置设计器模式
     *
     * @param mode 目标设计器模式
     */
    fun setMode(mode: DesignerMode) {
        _designerMode.value = mode
    }

    /**
     * 选中节点
     *
     * @param path 节点路径ID，如 "0.2.1"；null 表示取消选中
     */
    fun selectNode(path: String?) {
        _selectedNodePath.value = path
    }

    /**
     * 设置当前预览根节点
     * 由 PreviewCanvas 的 onRootNodeReady 回调调用
     * 设置前会释放旧根节点的 LuaObject 引用
     *
     * @param root 解析得到的根节点，null 表示无节点
     */
    fun setRootNode(root: ComposeNode?) {
        _rootNode.value?.release()
        _rootNode.value = root
    }

    /**
     * 设置分屏比例
     *
     * @param ratio 分割比例，范围 0-1；会自动约束到 [0.1f, 0.9f] 防止某一侧完全消失
     */
    fun setSplitRatio(ratio: Float) {
        _splitRatio.value = ratio.coerceIn(0.1f, 0.9f)
    }

    /**
     * 切换全屏预览
     */
    fun toggleFullscreen() {
        _isFullscreenPreview.value = !_isFullscreenPreview.value
    }

    /**
     * 设置预览缩放比例
     *
     * @param scale 缩放比例，会自动约束到 [0.25f, 3f]
     */
    fun setPreviewScale(scale: Float) {
        _previewScale.value = scale.coerceIn(0.25f, 3f)
    }

    /**
     * 重置预览变换
     * 重置缩放为 1f，偏移为 0
     */
    fun resetPreviewTransform() {
        _previewScale.value = 1f
        _previewOffsetX.value = 0f
        _previewOffsetY.value = 0f
    }

    /**
     * 设置预览偏移
     *
     * @param x 水平偏移
     * @param y 垂直偏移
     */
    fun setPreviewOffset(x: Float, y: Float) {
        _previewOffsetX.value = x
        _previewOffsetY.value = y
    }

    /**
     * 设置预览设备类型
     *
     * @param device 目标设备类型
     */
    fun setPreviewDevice(device: PreviewDevice) {
        _previewDevice.value = device
    }

    /**
     * 设置预览错误信息
     *
     * @param error 错误信息，null 表示清除错误
     */
    fun setPreviewError(error: String?) {
        _previewError.value = error
    }

    /**
     * 渲染成功回调
     * 保存成功的代码和根节点，用于错误降级
     *
     * @param code 成功渲染的代码
     * @param root 成功解析的根节点
     */
    fun onSuccessfulRender(code: String, root: ComposeNode) {
        _lastSuccessfulCode.value = code
        _lastSuccessfulRoot.value = root
        _previewError.value = null
    }

    /**
     * 切换组件库面板显示状态
     */
    fun toggleComponentPalette() {
        _showComponentPalette.value = !_showComponentPalette.value
    }

    /**
     * 切换底部面板显示状态
     */
    fun toggleBottomPanel() {
        _showBottomPanel.value = !_showBottomPanel.value
    }

    /**
     * 设置底部面板高度
     *
     * @param dp 高度(dp)，自动约束在 [120, 500] 范围内
     */
    fun setBottomPanelHeight(dp: Int) {
        _bottomPanelHeight.value = dp.coerceIn(120, 500)
    }

    /**
     * 设置底部面板标签页
     *
     * @param tab 目标标签页
     */
    fun setBottomPanelTab(tab: BottomPanelTab) {
        _bottomPanelTab.value = tab
    }

    /**
     * 设置只读模式
     *
     * @param ro 是否只读，true 表示代码编辑器不可编辑
     */
    fun setReadOnly(ro: Boolean) {
        _isReadOnly.value = ro
    }

    /**
     * 开始拖拽组件
     * @param meta 被拖拽的组件元数据
     */
    fun startComponentDrag(meta: ComponentMeta) {
        _draggingComponent.value = meta
    }

    /**
     * 结束拖拽（放下或取消）
     */
    fun endComponentDrag() {
        _draggingComponent.value = null
        _dragPosition.value = null
        clearDropTarget()
    }

    /**
     * 更新拖拽落点目标
     *
     * @param path 目标父节点路径，null 表示无有效目标
     * @param index 插入索引，-1 表示追加到末尾
     */
    fun updateDropTarget(path: String?, index: Int) {
        _dropTargetNodePath.value = path
        _dropInsertIndex.value = index
    }

    /**
     * 清除拖拽落点目标
     */
    fun clearDropTarget() {
        _dropTargetNodePath.value = null
        _dropInsertIndex.value = -1
    }

    /**
     * 更新拖拽指针位置
     * @param offset 指针位置
     */
    fun updateDragPosition(offset: Offset) {
        _dragPosition.value = offset
    }

    /**
     * 开始组件树节点拖拽
     * @param path 被拖拽节点的路径
     */
    fun startTreeDrag(path: String) {
        _draggingTreePath.value = path
        _treeDragTargetPath.value = null
        _treeDragInsertAsChild.value = false
    }

    /**
     * 更新组件树拖拽指针位置
     * @param offset 指针位置（相对于根布局）
     */
    fun updateTreeDragPosition(offset: Offset) {
        _treeDragPosition.value = offset
    }

    /**
     * 设置组件树拖拽目标
     * @param path 目标节点路径，null 表示无有效目标
     * @param asChild 是否作为子节点插入
     */
    fun setTreeDragTarget(path: String?, asChild: Boolean) {
        _treeDragTargetPath.value = path
        _treeDragInsertAsChild.value = asChild
    }

    /**
     * 结束组件树拖拽
     */
    fun endTreeDrag() {
        _draggingTreePath.value = null
        _treeDragPosition.value = null
        _treeDragTargetPath.value = null
        _treeDragInsertAsChild.value = false
    }

    /**
     * 调度预览刷新（防抖）
     *
     * 调用后等待 800ms，若期间没有新的调用则触发刷新。
     * 每次调用都会取消之前的等待任务。
     *
     * @param code 当前代码内容
     */
    fun scheduleRefresh(code: String) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(REFRESH_DELAY_MS)
            _currentCodeToPreview.value = code
        }
    }

    /**
     * 获取当前完整的 UI 状态快照
     *
     * @return DesignerUiState 当前状态
     */
    fun getCurrentState(): DesignerUiState {
        return DesignerUiState(
            designerMode = designerMode,
            selectedNodePath = selectedNodePath,
            splitRatio = splitRatio,
            isFullscreenPreview = isFullscreenPreview,
            previewScale = previewScale,
            previewOffsetX = previewOffsetX,
            previewOffsetY = previewOffsetY,
            previewDevice = previewDevice,
            previewError = previewError,
            lastSuccessfulCode = lastSuccessfulCode,
            lastSuccessfulRoot = lastSuccessfulRoot,
            rootNode = rootNode,
            showComponentPalette = showComponentPalette,
            bottomPanelTab = bottomPanelTab,
            showBottomPanel = showBottomPanel,
            bottomPanelHeight = bottomPanelHeight,
            isReadOnly = isReadOnly
        )
    }

    /**
     * 销毁视图模型，取消所有协程，释放节点资源
     * 在 Composable 离开组合时调用
     */
    fun destroy() {
        refreshJob?.cancel()
        refreshJob = null
        _rootNode.value?.release()
        _rootNode.value = null
        _lastSuccessfulRoot.value?.release()
        _lastSuccessfulRoot.value = null
        _selectedNodePath.value = null
        _previewError.value = null
        _draggingComponent.value = null
        _dragPosition.value = null
        endTreeDrag()
    }
}

/**
 * 设计器状态 CompositionLocal
 * 用于在深层 Composable 组件中访问 DesignerViewModel
 */
val LocalDesignerState = staticCompositionLocalOf<DesignerViewModel> {
    error("LocalDesignerState 未提供，请在 Composable 树中使用 provide(LocalDesignerState) { ... } 或 rememberDesignerState()")
}

/**
 * 记住并创建设计器状态
 *
 * 在 @Composable 函数中调用，创建并记住 DesignerViewModel 实例。
 * 状态会在 Composable 生命周期内保持。
 *
 * @return DesignerViewModel 实例
 */
@Composable
fun rememberDesignerState(): DesignerViewModel {
    return remember { DesignerViewModel() }
}
