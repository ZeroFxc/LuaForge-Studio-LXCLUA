package com.luaforge.studio.lxclua.ui.editor.designer

import com.nirithy.luacompose.node.ComposeNode

/**
 * 预览设备类型枚举
 *
 * 定义预览窗口的模拟设备尺寸：
 * - PHONE: 手机尺寸
 * - TABLET: 平板尺寸
 * - FILL: 填充可用空间
 */
enum class PreviewDevice {
    /** 手机模式 */
    PHONE,
    /** 平板模式 */
    TABLET,
    /** 填充可用空间 */
    FILL
}

/**
 * 底部面板标签页枚举
 *
 * 定义底部属性面板显示的内容：
 * - PROPERTIES: 属性面板，显示选中组件的属性
 * - TREE: 组件树面板，显示节点层级结构
 */
enum class BottomPanelTab {
    /** 属性面板 */
    PROPERTIES,
    /** 组件树面板 */
    TREE
}

/**
 * 设计器 UI 状态数据类
 *
 * 管理设计器模式下的所有 UI 状态，包括：
 * - 设计器模式切换
 * - 节点选中状态
 * - 分屏比例
 * - 预览控制（缩放、偏移、设备类型）
 * - 错误处理与降级渲染
 * - 面板显示控制
 *
 * @param designerMode 当前设计器模式
 * @param selectedNodePath 选中节点的路径ID（如 "0.2.1" 表示第0个子节点的第2个子节点的第1个子节点）
 * @param splitRatio 代码/设计分割比例，范围 0-1，0.5 表示均分
 * @param isFullscreenPreview 是否全屏预览
 * @param previewScale 预览缩放比例
 * @param previewOffsetX 预览水平偏移量
 * @param previewOffsetY 预览垂直偏移量
 * @param previewDevice 预览设备类型
 * @param previewError 预览错误信息，null 表示无错误
 * @param lastSuccessfulCode 最后一次成功渲染的代码，用于错误时降级显示
 * @param lastSuccessfulRoot 最后一次成功渲染的根节点，用于错误时降级显示
 * @param rootNode 当前预览的根节点（组件树显示用）
 * @param showComponentPalette 组件库面板是否展开
 * @param bottomPanelTab 底部面板当前显示的标签页
 * @param isReadOnly 是否只读模式（代码包含动态结构时禁用编辑）
 */
data class DesignerUiState(
    val designerMode: DesignerMode = DesignerMode.OFF,
    val selectedNodePath: String? = null,
    val splitRatio: Float = 0.4f,
    val isFullscreenPreview: Boolean = false,
    val previewScale: Float = 1f,
    val previewOffsetX: Float = 0f,
    val previewOffsetY: Float = 0f,
    val previewDevice: PreviewDevice = PreviewDevice.PHONE,
    val previewError: String? = null,
    val lastSuccessfulCode: String? = null,
    val lastSuccessfulRoot: ComposeNode? = null,
    val rootNode: ComposeNode? = null,
    val showComponentPalette: Boolean = false,
    val bottomPanelTab: BottomPanelTab = BottomPanelTab.PROPERTIES,
    val showBottomPanel: Boolean = false,
    val bottomPanelHeight: Int = 280,
    val isReadOnly: Boolean = false
)
