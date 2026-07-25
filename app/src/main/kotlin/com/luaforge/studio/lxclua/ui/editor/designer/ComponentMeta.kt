package com.luaforge.studio.lxclua.ui.editor.designer

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewArray
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 组件分类
 */
enum class ComponentCategory {
    /** 布局类组件：Column, Row, Box, Spacer, FlowRow, FlowColumn, LazyVerticalGrid, LazyHorizontalGrid */
    LAYOUT,
    /** 显示类组件：Text, Image, Icon */
    DISPLAY,
    /** 输入类组件：Button, TextField, OutlinedTextField, RadioButton, TextButton */
    INPUT,
    /** 容器类组件：Card, Surface, Scaffold, BoxWithConstraints */
    CONTAINER,
    /** 列表类组件：LazyColumn, LazyRow */
    LIST,
    /** 高级控件：Slider, Switch, Checkbox, Badge, BadgedBox, ProgressIndicator, Chip, TabRow, SearchBar */
    ADVANCED,
    /** 导航类组件：NavigationBar, NavigationBarItem, ModalNavigationDrawer, DismissibleNavigationDrawer, ModalDrawerSheet */
    NAVIGATION,
    /** 弹窗类组件：AlertDialog, DropdownMenu, Popup, ModalBottomSheet, ExposedDropdownMenuBox */
    DIALOG,
    /** 反馈类组件：SnackbarHost, PullToRefreshBox, SwipeToDismissBox */
    FEEDBACK,
    /** 动画类组件：AnimatedVisibility, Crossfade */
    ANIMATION,
    /** 绘图类组件：Canvas */
    DRAWING,
    /** 效果类组件：LaunchedEffect, DisposableEffect, key */
    EFFECT,
    /** 其他组件：Divider, FloatingActionButton, BackHandler, HorizontalPager, VerticalPager, SegmentedButton */
    OTHER
}

/**
 * 属性编辑器类型
 *
 * 定义属性面板中每种属性对应的编辑器控件类型
 */
enum class PropertyEditorType {
    /** 文本输入 */
    TEXT,
    /** 数字输入（Int/Float） */
    NUMBER,
    /** 布尔开关 */
    BOOLEAN,
    /** 颜色选择（色板预设） */
    COLOR,
    /** 下拉选择 */
    ENUM,
    /** 字体粗细选择 */
    TEXT_STYLE,
    /** 水平对齐选择 */
    ALIGN_H,
    /** 垂直对齐选择 */
    ALIGN_V,
    /** 水平排列 */
    ARRANGE_H,
    /** 垂直排列 */
    ARRANGE_V,
}

/**
 * 属性描述符
 *
 * 描述组件的一个可编辑属性，包括属性名、显示名、编辑器类型、默认值、枚举选项和分组信息。
 *
 * @property key 属性名，对应 ComposeNode.props 中的键，如 "text"、"fontSize"
 * @property displayName 界面显示名称，如 "文本"、"字号"
 * @property editorType 使用的编辑器控件类型
 * @property defaultValue 属性默认值
 * @property enumOptions ENUM 类型的可选值列表
 * @property category 属性分组名称，用于面板中按组显示，如 "通用"、"内容"、"文字"
 */
data class PropertyDescriptor(
    val key: String,
    val displayName: String,
    val editorType: PropertyEditorType,
    val defaultValue: Any? = null,
    val enumOptions: List<String> = emptyList(),
    val category: String = "通用"
)

/**
 * 预设颜色定义
 *
 * 用于颜色选择器的预设色板，使用 ARGB Long 值表示
 */
object PresetColors {
    /** 颜色名称到 ARGB Long 值的映射 */
    val colorMap: LinkedHashMap<String, Long> = linkedMapOf(
        "黑色" to 0xFF000000L,
        "白色" to 0xFFFFFFFFL,
        "红色" to 0xFFFF0000L,
        "橙色" to 0xFFFF9800L,
        "黄色" to 0xFFFFEB3BL,
        "绿色" to 0xFF4CAF50L,
        "蓝色" to 0xFF2196F3L,
        "紫色" to 0xFF9C27B0L,
        "灰色" to 0xFF9E9E9EL,
        "透明" to 0x00000000L,
    )

    /** 颜色值列表 */
    val colors: List<Long> get() = colorMap.values.toList()

    /** 颜色名称列表 */
    val names: List<String> get() = colorMap.keys.toList()

    /** 根据颜色值查找最接近的预设颜色名称 */
    fun findName(colorValue: Long): String? {
        return colorMap.entries.find { it.value == colorValue }?.key
    }
}

/**
 * 预定义的枚举选项
 */
object PropertyEnumOptions {
    /** 垂直排列选项（Column/LazyColumn 的 verticalArrangement） */
    val VERTICAL_ARRANGEMENT = listOf("Top", "Center", "Bottom", "SpaceAround", "SpaceBetween", "SpaceEvenly")

    /** 水平排列选项（Row/LazyRow 的 horizontalArrangement） */
    val HORIZONTAL_ARRANGEMENT = listOf("Start", "Center", "End", "SpaceAround", "SpaceBetween", "SpaceEvenly")

    /** 水平对齐选项（Column 的 horizontalAlignment） */
    val HORIZONTAL_ALIGNMENT = listOf("Start", "CenterHorizontally", "End")

    /** 垂直对齐选项（Row 的 verticalAlignment） */
    val VERTICAL_ALIGNMENT = listOf("Top", "CenterVertically", "Bottom")

    /** Box 内容对齐选项 */
    val CONTENT_ALIGNMENT = listOf(
        "TopStart", "TopCenter", "TopEnd",
        "CenterStart", "Center", "CenterEnd",
        "BottomStart", "BottomCenter", "BottomEnd"
    )

    /** 字体粗细选项 */
    val FONT_WEIGHT = listOf("Normal", "Bold", "Medium", "Light")

    /** 文本对齐选项 */
    val TEXT_ALIGN = listOf("Start", "Center", "End", "Justify")

    /** 弹出位置选项 */
    val POPUP_ALIGNMENT = listOf("TopStart", "TopCenter", "TopEnd", "CenterStart", "Center", "CenterEnd", "BottomStart", "BottomCenter", "BottomEnd")
}

/**
 * 组件元数据
 *
 * 描述设计器组件库中每个可用组件的信息，包括类型名、显示名、分类、描述、图标、
 * 默认属性和默认子组件，用于组件面板展示和拖拽创建。
 *
 * @property typeName LuaCompose 中的类型名，如 "Column"
 * @property displayName 界面显示名称，如 "列布局"
 * @property category 组件所属分类
 * @property description 组件简短描述
 * @property icon 组件图标 Composable
 * @property defaultProps 拖拽到画布时的默认属性
 * @property defaultChildren 默认子组件类型名列表（如 Button 默认有 Text 子节点）
 * @property canHaveChildren 是否允许包含子组件
 * @property properties 组件可编辑的属性描述符列表
 * @property luaTemplate 拖拽到画布时生成的 Lua 代码模板，null 表示使用默认模板
 */
data class ComponentMeta(
    val typeName: String,
    val displayName: String,
    val category: ComponentCategory,
    val description: String,
    val icon: @Composable () -> Unit,
    val defaultProps: Map<String, Any?> = emptyMap(),
    val defaultChildren: List<String> = emptyList(),
    val canHaveChildren: Boolean = true,
    val properties: List<PropertyDescriptor> = emptyList(),
    val luaTemplate: String? = null
)

/**
 * 组件库
 *
 * 提供所有设计器可用组件的元数据列表，按分类组织。
 */
object ComponentLibrary {

    /**
     * 创建一个简单的图标组件，使用 Material Icon
     *
     * @param imageVector Material 图标向量
     * @param contentDescription 无障碍描述
     * @return 图标 Composable
     */
    @Composable
    private fun SimpleIcon(
        imageVector: ImageVector,
        contentDescription: String? = null
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    /**
     * 通用尺寸属性（宽度、高度），用于大多数可视组件
     */
    private val sizeProperties: List<PropertyDescriptor> = listOf(
        PropertyDescriptor(
            key = "width",
            displayName = "宽度",
            editorType = PropertyEditorType.NUMBER,
            defaultValue = 0f,
            category = "尺寸"
        ),
        PropertyDescriptor(
            key = "height",
            displayName = "高度",
            editorType = PropertyEditorType.NUMBER,
            defaultValue = 0f,
            category = "尺寸"
        )
    )

    /**
     * 所有可用组件的元数据列表
     */
    val allComponents: List<ComponentMeta> = listOf(
        // ================================================================
        // 布局类组件
        // ================================================================
        ComponentMeta(
            typeName = "Column",
            displayName = "列布局",
            category = ComponentCategory.LAYOUT,
            description = "垂直排列子组件",
            icon = { SimpleIcon(Icons.Default.ViewAgenda, "列布局") },
            defaultProps = mapOf(
                "verticalArrangement" to "Top",
                "horizontalAlignment" to "Start"
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "verticalArrangement",
                    displayName = "垂直排列",
                    editorType = PropertyEditorType.ARRANGE_V,
                    defaultValue = "Top",
                    enumOptions = PropertyEnumOptions.VERTICAL_ARRANGEMENT,
                    category = "排列"
                ),
                PropertyDescriptor(
                    key = "horizontalAlignment",
                    displayName = "水平对齐",
                    editorType = PropertyEditorType.ALIGN_H,
                    defaultValue = "Start",
                    enumOptions = PropertyEnumOptions.HORIZONTAL_ALIGNMENT,
                    category = "对齐"
                )
            ) + sizeProperties,
            luaTemplate = "compose.Column({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        \n    },\n})"
        ),
        ComponentMeta(
            typeName = "Row",
            displayName = "行布局",
            category = ComponentCategory.LAYOUT,
            description = "水平排列子组件",
            icon = { SimpleIcon(Icons.Default.ViewArray, "行布局") },
            defaultProps = mapOf(
                "horizontalArrangement" to "Start",
                "verticalAlignment" to "CenterVertically"
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "horizontalArrangement",
                    displayName = "水平排列",
                    editorType = PropertyEditorType.ARRANGE_H,
                    defaultValue = "Start",
                    enumOptions = PropertyEnumOptions.HORIZONTAL_ARRANGEMENT,
                    category = "排列"
                ),
                PropertyDescriptor(
                    key = "verticalAlignment",
                    displayName = "垂直对齐",
                    editorType = PropertyEditorType.ALIGN_V,
                    defaultValue = "CenterVertically",
                    enumOptions = PropertyEnumOptions.VERTICAL_ALIGNMENT,
                    category = "对齐"
                )
            ) + sizeProperties,
            luaTemplate = "compose.Row({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        \n    },\n})"
        ),
        ComponentMeta(
            typeName = "Box",
            displayName = "堆叠布局",
            category = ComponentCategory.LAYOUT,
            description = "子组件堆叠放置",
            icon = { SimpleIcon(Icons.Default.Dashboard, "堆叠布局") },
            defaultProps = mapOf(
                "contentAlignment" to "TopStart"
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "contentAlignment",
                    displayName = "内容对齐",
                    editorType = PropertyEditorType.ENUM,
                    defaultValue = "TopStart",
                    enumOptions = PropertyEnumOptions.CONTENT_ALIGNMENT,
                    category = "对齐"
                )
            ) + sizeProperties,
            luaTemplate = "compose.Box({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        \n    },\n})"
        ),
        ComponentMeta(
            typeName = "Spacer",
            displayName = "间距",
            category = ComponentCategory.LAYOUT,
            description = "创建空白间距",
            icon = { SimpleIcon(Icons.Default.SpaceBar, "间距") },
            defaultProps = mapOf(
                "height" to 8f
            ),
            canHaveChildren = false,
            properties = sizeProperties,
            luaTemplate = "compose.Spacer({ modifier = compose.Modifier().height(8) })"
        ),
        ComponentMeta(
            typeName = "FlowRow",
            displayName = "流式行",
            category = ComponentCategory.LAYOUT,
            description = "自动换行的水平流式布局",
            icon = { SimpleIcon(Icons.Default.ViewColumn, "流式行") },
            defaultProps = mapOf(
                "horizontalArrangement" to "Start"
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "horizontalArrangement",
                    displayName = "水平排列",
                    editorType = PropertyEditorType.ARRANGE_H,
                    defaultValue = "Start",
                    enumOptions = PropertyEnumOptions.HORIZONTAL_ARRANGEMENT,
                    category = "排列"
                )
            ) + sizeProperties,
            luaTemplate = "compose.FlowRow({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        \n    },\n})"
        ),
        ComponentMeta(
            typeName = "FlowColumn",
            displayName = "流式列",
            category = ComponentCategory.LAYOUT,
            description = "自动换列的垂直流式布局",
            icon = { SimpleIcon(Icons.Default.ViewColumn, "流式列") },
            defaultProps = mapOf(
                "verticalArrangement" to "Top"
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "verticalArrangement",
                    displayName = "垂直排列",
                    editorType = PropertyEditorType.ARRANGE_V,
                    defaultValue = "Top",
                    enumOptions = PropertyEnumOptions.VERTICAL_ARRANGEMENT,
                    category = "排列"
                )
            ) + sizeProperties,
            luaTemplate = "compose.FlowColumn({\n    modifier = compose.Modifier().fillMaxHeight(),\n    children = {\n        \n    },\n})"
        ),
        ComponentMeta(
            typeName = "LazyVerticalGrid",
            displayName = "垂直网格",
            category = ComponentCategory.LAYOUT,
            description = "垂直滚动网格布局",
            icon = { SimpleIcon(Icons.Default.ViewModule, "垂直网格") },
            defaultProps = mapOf(
                "columns" to 3
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "columns",
                    displayName = "列数",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 3,
                    category = "网格"
                )
            ) + sizeProperties,
            luaTemplate = "compose.LazyVerticalGrid({\n    columns = 3,\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        \n    },\n})"
        ),
        ComponentMeta(
            typeName = "LazyHorizontalGrid",
            displayName = "水平网格",
            category = ComponentCategory.LAYOUT,
            description = "水平滚动网格布局",
            icon = { SimpleIcon(Icons.Default.ViewModule, "水平网格") },
            defaultProps = mapOf(
                "rows" to 2
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "rows",
                    displayName = "行数",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 2,
                    category = "网格"
                )
            ) + sizeProperties,
            luaTemplate = "compose.LazyHorizontalGrid({\n    rows = 2,\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        \n    },\n})"
        ),

        // ================================================================
        // 列表类组件
        // ================================================================
        ComponentMeta(
            typeName = "LazyColumn",
            displayName = "列表",
            category = ComponentCategory.LIST,
            description = "垂直滚动列表（懒加载，支持 scope DSL）",
            icon = { SimpleIcon(Icons.Default.ViewList, "列表") },
            defaultProps = mapOf(
                "verticalArrangement" to "Top"
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "verticalArrangement",
                    displayName = "垂直排列",
                    editorType = PropertyEditorType.ARRANGE_V,
                    defaultValue = "Top",
                    enumOptions = PropertyEnumOptions.VERTICAL_ARRANGEMENT,
                    category = "排列"
                )
            ) + sizeProperties,
            luaTemplate = "compose.LazyColumn({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = function(scope)\n        scope.items(5, function(i)\n            return compose.Text({ text = \"项 \" .. i, modifier = compose.Modifier().padding(8) })\n        end)\n    end,\n})"
        ),
        ComponentMeta(
            typeName = "LazyRow",
            displayName = "水平列表",
            category = ComponentCategory.LIST,
            description = "水平滚动列表（懒加载）",
            icon = { SimpleIcon(Icons.Default.ViewDay, "水平列表") },
            defaultProps = mapOf(
                "horizontalArrangement" to "Start"
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "horizontalArrangement",
                    displayName = "水平排列",
                    editorType = PropertyEditorType.ARRANGE_H,
                    defaultValue = "Start",
                    enumOptions = PropertyEnumOptions.HORIZONTAL_ARRANGEMENT,
                    category = "排列"
                )
            ) + sizeProperties,
            luaTemplate = "compose.LazyRow({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        \n    },\n})"
        ),

        // ================================================================
        // 显示类组件
        // ================================================================
        ComponentMeta(
            typeName = "Text",
            displayName = "文本",
            category = ComponentCategory.DISPLAY,
            description = "显示文字内容",
            icon = { SimpleIcon(Icons.Default.TextFields, "文本") },
            defaultProps = mapOf(
                "text" to "文本"
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "text",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "文本",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "fontSize",
                    displayName = "字号",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 0f,
                    category = "文字"
                ),
                PropertyDescriptor(
                    key = "color",
                    displayName = "颜色",
                    editorType = PropertyEditorType.COLOR,
                    defaultValue = 0xFF000000L,
                    category = "文字"
                ),
                PropertyDescriptor(
                    key = "fontWeight",
                    displayName = "粗细",
                    editorType = PropertyEditorType.TEXT_STYLE,
                    defaultValue = "Normal",
                    enumOptions = PropertyEnumOptions.FONT_WEIGHT,
                    category = "文字"
                ),
                PropertyDescriptor(
                    key = "textAlign",
                    displayName = "对齐",
                    editorType = PropertyEditorType.ENUM,
                    defaultValue = "Start",
                    enumOptions = PropertyEnumOptions.TEXT_ALIGN,
                    category = "文字"
                )
            ) + sizeProperties,
            luaTemplate = "compose.Text({\n    text = \"文本\",\n    fontSize = 16,\n    color = 0xFF000000,\n})"
        ),
        ComponentMeta(
            typeName = "Image",
            displayName = "图片",
            category = ComponentCategory.DISPLAY,
            description = "显示图片",
            icon = { SimpleIcon(Icons.Default.Image, "图片") },
            defaultProps = mapOf(
                "painter" to "image.png"
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "painter",
                    displayName = "图片资源",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "image.png",
                    category = "内容"
                )
            ) + sizeProperties,
            luaTemplate = "compose.Image({\n    painter = \"image.png\",\n    modifier = compose.Modifier().size(48, 48),\n})"
        ),
        ComponentMeta(
            typeName = "Icon",
            displayName = "图标",
            category = ComponentCategory.DISPLAY,
            description = "Material 图标",
            icon = { SimpleIcon(Icons.Default.Star, "图标") },
            defaultProps = mapOf(
                "name" to "Home",
                "size" to 24
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "name",
                    displayName = "图标名",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "Home",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "size",
                    displayName = "大小",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 24,
                    category = "尺寸"
                ),
                PropertyDescriptor(
                    key = "color",
                    displayName = "颜色",
                    editorType = PropertyEditorType.COLOR,
                    defaultValue = 0xFF000000L,
                    category = "外观"
                )
            ),
            luaTemplate = "compose.Icon({\n    name = \"Home\",\n    size = 24,\n    color = 0xFF000000,\n})"
        ),

        // ================================================================
        // 输入类组件
        // ================================================================
        ComponentMeta(
            typeName = "Button",
            displayName = "按钮",
            category = ComponentCategory.INPUT,
            description = "可点击的按钮",
            icon = { SimpleIcon(Icons.Default.SmartButton, "按钮") },
            defaultProps = mapOf(
                "text" to "按钮"
            ),
            defaultChildren = listOf("Text"),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "text",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "按钮",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "onClick",
                    displayName = "点击事件名",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "",
                    category = "事件"
                )
            ) + sizeProperties,
            luaTemplate = "compose.Button({\n    text = \"按钮\",\n    onClick = function() end,\n    modifier = compose.Modifier().fillMaxWidth(),\n})"
        ),
        ComponentMeta(
            typeName = "TextField",
            displayName = "输入框",
            category = ComponentCategory.INPUT,
            description = "文本输入框",
            icon = { SimpleIcon(Icons.Default.Edit, "输入框") },
            defaultProps = mapOf(
                "text" to "",
                "label" to "标签"
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "value",
                    displayName = "占位文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "label",
                    displayName = "标签",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "标签",
                    category = "内容"
                )
            ) + sizeProperties,
            luaTemplate = "compose.TextField({\n    text = \"\",\n    label = \"标签\",\n    onValueChange = function(v) end,\n    modifier = compose.Modifier().fillMaxWidth(),\n})"
        ),
        ComponentMeta(
            typeName = "OutlinedTextField",
            displayName = "边框输入框",
            category = ComponentCategory.INPUT,
            description = "带边框的文本输入框",
            icon = { SimpleIcon(Icons.Default.Edit, "边框输入框") },
            defaultProps = mapOf(
                "text" to "",
                "label" to "标签"
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "text",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "label",
                    displayName = "标签",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "标签",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "placeholder",
                    displayName = "占位符",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "",
                    category = "内容"
                )
            ) + sizeProperties,
            luaTemplate = "compose.OutlinedTextField({\n    text = \"\",\n    label = \"标签\",\n    onValueChange = function(v) end,\n    modifier = compose.Modifier().fillMaxWidth(),\n})"
        ),
        ComponentMeta(
            typeName = "RadioButton",
            displayName = "单选按钮",
            category = ComponentCategory.INPUT,
            description = "Material 单选按钮",
            icon = { SimpleIcon(Icons.Default.RadioButtonChecked, "单选按钮") },
            defaultProps = mapOf(
                "selected" to false
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "selected",
                    displayName = "已选中",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.RadioButton({\n    selected = false,\n    onClick = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "TextButton",
            displayName = "文本按钮",
            category = ComponentCategory.INPUT,
            description = "无边框纯文本按钮",
            icon = { SimpleIcon(Icons.Default.SmartButton, "文本按钮") },
            defaultProps = mapOf(
                "text" to "文本按钮"
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "text",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "文本按钮",
                    category = "内容"
                )
            ),
            luaTemplate = "compose.TextButton({\n    text = \"文本按钮\",\n    onClick = function() end,\n    modifier = compose.Modifier().fillMaxWidth(),\n})"
        ),

        // ================================================================
        // 高级控件
        // ================================================================
        ComponentMeta(
            typeName = "Slider",
            displayName = "滑块",
            category = ComponentCategory.ADVANCED,
            description = "滑动选择数值",
            icon = { SimpleIcon(Icons.Default.Tune, "滑块") },
            defaultProps = mapOf(
                "value" to 0.5f
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "value",
                    displayName = "当前值",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 0.5f,
                    category = "数值"
                )
            ) + sizeProperties,
            luaTemplate = "compose.Slider({\n    value = 0.5,\n    valueRange = {0, 1},\n    onValueChange = function(v) end,\n    modifier = compose.Modifier().fillMaxWidth(),\n})"
        ),
        ComponentMeta(
            typeName = "Switch",
            displayName = "开关",
            category = ComponentCategory.ADVANCED,
            description = "开关切换控件",
            icon = { SimpleIcon(Icons.Default.ToggleOn, "开关") },
            defaultProps = mapOf(
                "checked" to false
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "checked",
                    displayName = "已开启",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.Switch({\n    checked = false,\n    onCheckedChange = function(v) end,\n})"
        ),
        ComponentMeta(
            typeName = "Checkbox",
            displayName = "复选框",
            category = ComponentCategory.ADVANCED,
            description = "Material 复选框",
            icon = { SimpleIcon(Icons.Default.CheckBox, "复选框") },
            defaultProps = mapOf(
                "checked" to false
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "checked",
                    displayName = "已勾选",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.Checkbox({\n    checked = false,\n    onCheckedChange = function(v) end,\n})"
        ),
        ComponentMeta(
            typeName = "Badge",
            displayName = "徽章",
            category = ComponentCategory.ADVANCED,
            description = "小圆点或数字徽章",
            icon = { SimpleIcon(Icons.Default.Notifications, "徽章") },
            defaultProps = mapOf(
                "text" to "新"
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "text",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "新",
                    category = "内容"
                )
            ),
            luaTemplate = "compose.Badge({\n    text = \"新\",\n})"
        ),
        ComponentMeta(
            typeName = "BadgedBox",
            displayName = "角标容器",
            category = ComponentCategory.ADVANCED,
            description = "带角标的容器（如通知数量）",
            icon = { SimpleIcon(Icons.Default.Notifications, "角标容器") },
            defaultProps = mapOf(
                "badgeCount" to 5
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "badgeCount",
                    displayName = "角标数",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 5,
                    category = "内容"
                )
            ) + sizeProperties,
            luaTemplate = "compose.BadgedBox({\n    badgeCount = 5,\n    modifier = compose.Modifier().size(48, 48),\n    children = {\n        compose.Icon({ name = \"Email\", size = 32 }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "LinearProgressIndicator",
            displayName = "线性进度条",
            category = ComponentCategory.ADVANCED,
            description = "线性进度指示器",
            icon = { SimpleIcon(Icons.Default.Timer, "线性进度条") },
            defaultProps = mapOf(
                "progress" to 0.5f
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "progress",
                    displayName = "进度",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 0.5f,
                    category = "数值"
                )
            ) + sizeProperties,
            luaTemplate = "compose.LinearProgressIndicator({\n    progress = 0.5,\n    modifier = compose.Modifier().fillMaxWidth(),\n})"
        ),
        ComponentMeta(
            typeName = "CircularProgressIndicator",
            displayName = "圆形进度",
            category = ComponentCategory.ADVANCED,
            description = "圆形进度指示器",
            icon = { SimpleIcon(Icons.Default.Timer, "圆形进度") },
            defaultProps = mapOf(
                "progress" to 0.5f
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "progress",
                    displayName = "进度",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 0.5f,
                    category = "数值"
                ),
                PropertyDescriptor(
                    key = "strokeWidth",
                    displayName = "线宽",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 4f,
                    category = "外观"
                )
            ) + sizeProperties,
            luaTemplate = "compose.CircularProgressIndicator({\n    progress = 0.5,\n    modifier = compose.Modifier().size(32, 32),\n    strokeWidth = 3,\n})"
        ),
        ComponentMeta(
            typeName = "FilterChip",
            displayName = "筛选标签",
            category = ComponentCategory.ADVANCED,
            description = "可选中/取消的筛选标签",
            icon = { SimpleIcon(Icons.Default.Label, "筛选标签") },
            defaultProps = mapOf(
                "label" to "标签",
                "selected" to false
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "label",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "标签",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "selected",
                    displayName = "已选中",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.FilterChip({\n    label = \"标签\",\n    selected = false,\n    onClick = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "SuggestionChip",
            displayName = "建议标签",
            category = ComponentCategory.ADVANCED,
            description = "可点击的建议标签",
            icon = { SimpleIcon(Icons.Default.Label, "建议标签") },
            defaultProps = mapOf(
                "label" to "标签"
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "label",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "标签",
                    category = "内容"
                )
            ),
            luaTemplate = "compose.SuggestionChip({\n    label = \"标签\",\n    onClick = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "AssistChip",
            displayName = "辅助标签",
            category = ComponentCategory.ADVANCED,
            description = "辅助功能标签",
            icon = { SimpleIcon(Icons.Default.Label, "辅助标签") },
            defaultProps = mapOf(
                "label" to "标签"
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "label",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "标签",
                    category = "内容"
                )
            ),
            luaTemplate = "compose.AssistChip({\n    label = \"标签\",\n    onClick = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "InputChip",
            displayName = "输入标签",
            category = ComponentCategory.ADVANCED,
            description = "可关闭的输入标签",
            icon = { SimpleIcon(Icons.Default.Label, "输入标签") },
            defaultProps = mapOf(
                "label" to "标签"
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "label",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "标签",
                    category = "内容"
                )
            ),
            luaTemplate = "compose.InputChip({\n    label = \"标签\",\n    selected = false,\n    onClick = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "TabRow",
            displayName = "选项卡栏",
            category = ComponentCategory.ADVANCED,
            description = "Material 选项卡栏",
            icon = { SimpleIcon(Icons.Default.Tab, "选项卡栏") },
            defaultProps = mapOf(
                "selectedIndex" to 0
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "selectedIndex",
                    displayName = "选中索引",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 0,
                    category = "状态"
                )
            ) + sizeProperties,
            luaTemplate = "compose.TabRow({\n    selectedIndex = 0,\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        compose.Tab({ text = \"标签1\", selected = true, onClick = function() end }),\n        compose.Tab({ text = \"标签2\", selected = false, onClick = function() end }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "Tab",
            displayName = "选项卡",
            category = ComponentCategory.ADVANCED,
            description = "单个选项卡",
            icon = { SimpleIcon(Icons.Default.Tab, "选项卡") },
            defaultProps = mapOf(
                "text" to "标签",
                "selected" to false
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "text",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "标签",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "selected",
                    displayName = "已选中",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.Tab({\n    text = \"标签\",\n    selected = false,\n    onClick = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "ScrollableTabRow",
            displayName = "可滚动选项卡",
            category = ComponentCategory.ADVANCED,
            description = "可水平滚动的选项卡栏",
            icon = { SimpleIcon(Icons.Default.Tab, "可滚动选项卡") },
            defaultProps = mapOf(
                "selectedIndex" to 0
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "selectedIndex",
                    displayName = "选中索引",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 0,
                    category = "状态"
                )
            ) + sizeProperties,
            luaTemplate = "compose.ScrollableTabRow({\n    selectedIndex = 0,\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        compose.Tab({ text = \"标签1\", selected = true, onClick = function() end }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "SearchBar",
            displayName = "搜索栏",
            category = ComponentCategory.ADVANCED,
            description = "Material 搜索栏",
            icon = { SimpleIcon(Icons.Default.Search, "搜索栏") },
            defaultProps = mapOf(
                "query" to "",
                "placeholder" to "搜索..."
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "placeholder",
                    displayName = "占位符",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "搜索...",
                    category = "内容"
                )
            ) + sizeProperties,
            luaTemplate = "compose.SearchBar({\n    query = \"\",\n    placeholder = \"搜索...\",\n    onQueryChange = function(v) end,\n    onSearch = function(v) end,\n    active = false,\n    onActiveChange = function(v) end,\n    modifier = compose.Modifier().fillMaxWidth(),\n})"
        ),

        // ================================================================
        // 导航类组件
        // ================================================================
        ComponentMeta(
            typeName = "NavigationBar",
            displayName = "底部导航栏",
            category = ComponentCategory.NAVIGATION,
            description = "Material 底部导航栏",
            icon = { SimpleIcon(Icons.Default.Navigation, "底部导航栏") },
            canHaveChildren = true,
            properties = sizeProperties,
            luaTemplate = "compose.NavigationBar({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        compose.NavigationBarItem({\n            selected = true,\n            icon = \"Home\",\n            label = \"首页\",\n            onClick = function() end,\n        }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "NavigationBarItem",
            displayName = "导航项",
            category = ComponentCategory.NAVIGATION,
            description = "底部导航栏单项",
            icon = { SimpleIcon(Icons.Default.Navigation, "导航项") },
            defaultProps = mapOf(
                "selected" to false,
                "icon" to "Home",
                "label" to "首页"
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "label",
                    displayName = "标签",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "首页",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "icon",
                    displayName = "图标名",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "Home",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "selected",
                    displayName = "已选中",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.NavigationBarItem({\n    selected = false,\n    icon = \"Home\",\n    label = \"首页\",\n    onClick = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "ModalNavigationDrawer",
            displayName = "模态抽屉",
            category = ComponentCategory.NAVIGATION,
            description = "从侧边滑出的模态抽屉",
            icon = { SimpleIcon(Icons.Default.Menu, "模态抽屉") },
            defaultProps = mapOf(
                "open" to false,
                "gesturesEnabled" to true
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "gesturesEnabled",
                    displayName = "手势开启",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = true,
                    category = "交互"
                )
            ),
            luaTemplate = "compose.ModalNavigationDrawer({\n    open = false,\n    onOpen = function() end,\n    onClose = function() end,\n    gesturesEnabled = true,\n    children = {\n        compose.ModalDrawerSheet({\n            _drawerSlot = \"drawer\",\n            modifier = compose.Modifier().width(280),\n            children = {\n                compose.Text({ text = \"抽屉内容\", modifier = compose.Modifier().padding(24, 48, 0, 0) }),\n            },\n        }),\n        compose.Column({\n            modifier = compose.Modifier().fillMaxSize(),\n            children = {\n                compose.Text({ text = \"主内容\", modifier = compose.Modifier().padding(24) }),\n            },\n        }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "DismissibleNavigationDrawer",
            displayName = "常驻抽屉",
            category = ComponentCategory.NAVIGATION,
            description = "常驻侧边导航抽屉",
            icon = { SimpleIcon(Icons.Default.Menu, "常驻抽屉") },
            canHaveChildren = true,
            luaTemplate = "compose.DismissibleNavigationDrawer({\n    children = {\n        compose.ModalDrawerSheet({\n            _drawerSlot = \"drawer\",\n            modifier = compose.Modifier().width(280),\n            children = {\n                compose.Text({ text = \"抽屉内容\", modifier = compose.Modifier().padding(24, 48, 0, 0) }),\n            },\n        }),\n        compose.Column({\n            modifier = compose.Modifier().fillMaxSize(),\n            children = {\n                compose.Text({ text = \"主内容\", modifier = compose.Modifier().padding(24) }),\n            },\n        }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "ModalDrawerSheet",
            displayName = "抽屉面板",
            category = ComponentCategory.NAVIGATION,
            description = "抽屉内的内容面板",
            icon = { SimpleIcon(Icons.Default.Menu, "抽屉面板") },
            canHaveChildren = true,
            properties = sizeProperties,
            luaTemplate = "compose.ModalDrawerSheet({\n    _drawerSlot = \"drawer\",\n    modifier = compose.Modifier().width(280),\n    children = {\n        \n    },\n})"
        ),

        // ================================================================
        // 弹窗类组件
        // ================================================================
        ComponentMeta(
            typeName = "AlertDialog",
            displayName = "对话框",
            category = ComponentCategory.DIALOG,
            description = "Material 警告对话框",
            icon = { SimpleIcon(Icons.Default.Warning, "对话框") },
            defaultProps = mapOf(
                "visible" to false,
                "title" to "标题",
                "text" to "内容"
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "title",
                    displayName = "标题",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "标题",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "text",
                    displayName = "内容",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "内容",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "confirmText",
                    displayName = "确认文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "确定",
                    category = "按钮"
                ),
                PropertyDescriptor(
                    key = "dismissText",
                    displayName = "取消文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "取消",
                    category = "按钮"
                )
            ),
            luaTemplate = "compose.AlertDialog({\n    visible = false,\n    title = \"标题\",\n    text = \"内容\",\n    confirmText = \"确定\",\n    dismissText = \"取消\",\n    onConfirm = function() end,\n    onDismiss = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "DatePicker",
            displayName = "日期选择器",
            category = ComponentCategory.DIALOG,
            description = "Material 日期选择器",
            icon = { SimpleIcon(Icons.Default.DateRange, "日期选择器") },
            canHaveChildren = false,
            luaTemplate = "compose.DatePicker({\n    state = datePickerState,\n})"
        ),
        ComponentMeta(
            typeName = "DatePickerDialog",
            displayName = "日期对话框",
            category = ComponentCategory.DIALOG,
            description = "Material 日期选择对话框",
            icon = { SimpleIcon(Icons.Default.DateRange, "日期对话框") },
            defaultProps = mapOf(
                "visible" to false
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "visible",
                    displayName = "可见",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.DatePickerDialog({\n    visible = false,\n    onConfirm = function(date) end,\n    onDismiss = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "TimePicker",
            displayName = "时间选择器",
            category = ComponentCategory.DIALOG,
            description = "Material 时间选择器",
            icon = { SimpleIcon(Icons.Default.Schedule, "时间选择器") },
            canHaveChildren = false,
            luaTemplate = "compose.TimePicker({\n    state = timePickerState,\n})"
        ),
        ComponentMeta(
            typeName = "ModalBottomSheet",
            displayName = "底部面板",
            category = ComponentCategory.DIALOG,
            description = "Modal 底部弹出面板",
            icon = { SimpleIcon(Icons.Default.KeyboardArrowDown, "底部面板") },
            defaultProps = mapOf(
                "visible" to false,
                "dragHandle" to true
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "dragHandle",
                    displayName = "拖拽手柄",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = true,
                    category = "交互"
                )
            ),
            luaTemplate = "compose.ModalBottomSheet({\n    visible = false,\n    onDismissRequest = function() end,\n    dragHandle = true,\n    children = {\n        compose.Column({\n            modifier = compose.Modifier().fillMaxWidth().padding(24),\n            children = {\n                compose.Text({ text = \"底部面板\", fontSize = 20, fontWeight = 600 }),\n            },\n        }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "DropdownMenu",
            displayName = "下拉菜单",
            category = ComponentCategory.DIALOG,
            description = "Material 下拉菜单",
            icon = { SimpleIcon(Icons.Default.MoreVert, "下拉菜单") },
            defaultProps = mapOf(
                "expanded" to false
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "expanded",
                    displayName = "已展开",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.DropdownMenu({\n    expanded = false,\n    onDismissRequest = function() end,\n    children = {\n        { type = \"DropdownMenuItem\", text = \"选项1\", onClick = function() end },\n    },\n})"
        ),
        ComponentMeta(
            typeName = "Popup",
            displayName = "弹出窗口",
            category = ComponentCategory.DIALOG,
            description = "通用弹出窗口",
            icon = { SimpleIcon(Icons.Default.Warning, "弹出窗口") },
            defaultProps = mapOf(
                "visible" to false,
                "alignment" to "Center",
                "dismissOnBackPress" to true,
                "dismissOnClickOutside" to true
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "alignment",
                    displayName = "对齐位置",
                    editorType = PropertyEditorType.ENUM,
                    defaultValue = "Center",
                    enumOptions = PropertyEnumOptions.POPUP_ALIGNMENT,
                    category = "位置"
                ),
                PropertyDescriptor(
                    key = "dismissOnBackPress",
                    displayName = "返回关闭",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = true,
                    category = "交互"
                ),
                PropertyDescriptor(
                    key = "dismissOnClickOutside",
                    displayName = "点击外部关闭",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = true,
                    category = "交互"
                )
            ),
            luaTemplate = "compose.Popup({\n    visible = false,\n    alignment = \"Center\",\n    dismissOnBackPress = true,\n    dismissOnClickOutside = true,\n    onDismissRequest = function() end,\n    children = {\n        compose.Card({\n            modifier = compose.Modifier().width(280).padding(16),\n            children = {\n                compose.Text({ text = \"弹窗内容\", modifier = compose.Modifier().padding(16) }),\n            },\n        }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "ExposedDropdownMenuBox",
            displayName = "下拉选择框",
            category = ComponentCategory.DIALOG,
            description = "Material 下拉选择框",
            icon = { SimpleIcon(Icons.Default.KeyboardArrowDown, "下拉选择框") },
            defaultProps = mapOf(
                "expanded" to false
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "expanded",
                    displayName = "已展开",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ) + sizeProperties,
            luaTemplate = "compose.ExposedDropdownMenuBox({\n    expanded = false,\n    onExpandedChange = function(v) end,\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        compose.TextField({\n            text = \"请选择\",\n            readOnly = true,\n            modifier = compose.Modifier().fillMaxWidth(),\n        }),\n        compose.DropdownMenu({\n            expanded = false,\n            onDismissRequest = function() end,\n            children = {\n                { type = \"DropdownMenuItem\", text = \"选项1\", onClick = function() end },\n            },\n        }),\n    },\n})"
        ),

        // ================================================================
        // 反馈类组件
        // ================================================================
        ComponentMeta(
            typeName = "SnackbarHost",
            displayName = "提示条宿主",
            category = ComponentCategory.FEEDBACK,
            description = "Snackbar 的宿主容器",
            icon = { SimpleIcon(Icons.Default.Info, "提示条宿主") },
            canHaveChildren = false,
            luaTemplate = "compose.SnackbarHost({\n    hostState = compose.SnackbarHostState(),\n})"
        ),
        ComponentMeta(
            typeName = "PullToRefreshBox",
            displayName = "下拉刷新",
            category = ComponentCategory.FEEDBACK,
            description = "下拉刷新容器",
            icon = { SimpleIcon(Icons.Default.Refresh, "下拉刷新") },
            defaultProps = mapOf(
                "isRefreshing" to false
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "isRefreshing",
                    displayName = "刷新中",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ) + sizeProperties,
            luaTemplate = "compose.PullToRefreshBox({\n    isRefreshing = false,\n    onRefresh = function() end,\n    modifier = compose.Modifier().fillMaxSize(),\n    children = {\n        \n    },\n})"
        ),
        ComponentMeta(
            typeName = "SwipeToDismissBox",
            displayName = "滑动删除",
            category = ComponentCategory.FEEDBACK,
            description = "滑动删除容器",
            icon = { SimpleIcon(Icons.Default.Swipe, "滑动删除") },
            canHaveChildren = true,
            properties = sizeProperties,
            luaTemplate = "compose.SwipeToDismissBox({\n    onDismissedToStart = function() end,\n    onDismissedToEnd = function() end,\n    children = {\n        compose.Box({\n            _slot = \"background\",\n            modifier = compose.Modifier().fillMaxWidth().height(56).backgroundRounded(0xFFFF5252, 8),\n        }),\n        compose.Card({\n            modifier = compose.Modifier().fillMaxWidth().padding(0, 2, 0, 2),\n            children = {\n                compose.Text({ text = \"滑动删除\", modifier = compose.Modifier().padding(16) }),\n            },\n        }),\n    },\n})"
        ),

        // ================================================================
        // 动画类组件
        // ================================================================
        ComponentMeta(
            typeName = "AnimatedVisibility",
            displayName = "动画可见性",
            category = ComponentCategory.ANIMATION,
            description = "带动画的显示/隐藏容器",
            icon = { SimpleIcon(Icons.Default.PlayArrow, "动画可见性") },
            defaultProps = mapOf(
                "visible" to true
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "visible",
                    displayName = "可见",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = true,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.AnimatedVisibility({\n    visible = true,\n    children = {\n        compose.Text({ text = \"内容\", modifier = compose.Modifier().padding(16) }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "Crossfade",
            displayName = "交叉淡入淡出",
            category = ComponentCategory.ANIMATION,
            description = "交叉淡入淡出过渡动画",
            icon = { SimpleIcon(Icons.Default.PlayArrow, "交叉淡入淡出") },
            defaultProps = mapOf(
                "targetState" to false
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "targetState",
                    displayName = "目标状态",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.Crossfade({\n    targetState = false,\n    children = {\n        compose.Card({\n            modifier = compose.Modifier().fillMaxWidth().height(80),\n            children = {\n                compose.Text({ text = \"内容\", modifier = compose.Modifier().padding(16) }),\n            },\n        }),\n    },\n})"
        ),

        // ================================================================
        // 绘图类组件
        // ================================================================
        ComponentMeta(
            typeName = "Canvas",
            displayName = "画布",
            category = ComponentCategory.DRAWING,
            description = "自定义绘图画布",
            icon = { SimpleIcon(Icons.Default.Palette, "画布") },
            defaultProps = mapOf(
                "continuousRedraw" to false
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "continuousRedraw",
                    displayName = "持续重绘",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "绘图"
                )
            ) + sizeProperties,
            luaTemplate = "compose.Canvas({\n    modifier = compose.Modifier().fillMaxWidth().height(100).background(0xFFF5F5F5).borderRadius(8),\n    onDraw = function(draw, w, h, timeSec)\n        draw.drawCircle(w / 2, h / 2, 30, 0xFF6750A4)\n    end,\n})"
        ),

        // ================================================================
        // 效果类组件
        // ================================================================
        ComponentMeta(
            typeName = "LaunchedEffect",
            displayName = "协程启动",
            category = ComponentCategory.EFFECT,
            description = "Composable 进入组合时启动协程",
            icon = { SimpleIcon(Icons.Default.PlayArrow, "协程启动") },
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "key",
                    displayName = "Key",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 0,
                    category = "参数"
                )
            ),
            luaTemplate = "compose.LaunchedEffect({\n    key = 1,\n    block = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "DisposableEffect",
            displayName = "可清理副作用",
            category = ComponentCategory.EFFECT,
            description = "可清理的副作用（离开组合时执行清理）",
            icon = { SimpleIcon(Icons.Default.Delete, "可清理副作用") },
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "key",
                    displayName = "Key",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 0,
                    category = "参数"
                )
            ),
            luaTemplate = "compose.DisposableEffect({\n    key = 1,\n    effect = function()\n        return function() end\n    end,\n    children = {\n        \n    },\n})"
        ),
        ComponentMeta(
            typeName = "key",
            displayName = "Key 重组",
            category = ComponentCategory.EFFECT,
            description = "控制子组件的重组范围",
            icon = { SimpleIcon(Icons.Default.Settings, "Key 重组") },
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "key",
                    displayName = "Key",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 0,
                    category = "参数"
                )
            ),
            luaTemplate = "compose.key({\n    key = 1,\n    children = {\n        \n    },\n})"
        ),

        // ================================================================
        // 容器类组件
        // ================================================================
        ComponentMeta(
            typeName = "Card",
            displayName = "卡片",
            category = ComponentCategory.CONTAINER,
            description = "Material 卡片容器",
            icon = { SimpleIcon(Icons.Default.ViewCompact, "卡片") },
            defaultProps = emptyMap(),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "elevation",
                    displayName = "阴影",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 4f,
                    category = "外观"
                )
            ) + sizeProperties,
            luaTemplate = "compose.Card({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        compose.Text({ text = \"卡片内容\", modifier = compose.Modifier().padding(16) }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "Surface",
            displayName = "平面",
            category = ComponentCategory.CONTAINER,
            description = "Material 平面容器",
            icon = { SimpleIcon(Icons.Default.Dashboard, "平面") },
            canHaveChildren = true,
            properties = sizeProperties,
            luaTemplate = "compose.Surface({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        \n    },\n})"
        ),
        ComponentMeta(
            typeName = "Scaffold",
            displayName = "页面框架",
            category = ComponentCategory.CONTAINER,
            description = "Material 页面框架（含顶栏/底栏/浮动按钮等）",
            icon = { SimpleIcon(Icons.Default.Dashboard, "页面框架") },
            canHaveChildren = true,
            luaTemplate = "compose.Scaffold({\n    modifier = compose.Modifier().fillMaxSize(),\n    children = {\n        compose.Box({\n            _scaffoldSlot = \"topBar\",\n            title = \"标题\",\n            modifier = compose.Modifier().fillMaxWidth(),\n        }),\n        compose.Column({\n            modifier = compose.Modifier().fillMaxSize().padding(24),\n            children = {\n                compose.Text({ text = \"内容\" }),\n            },\n        }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "BoxWithConstraints",
            displayName = "约束框",
            category = ComponentCategory.CONTAINER,
            description = "可获取约束信息的容器",
            icon = { SimpleIcon(Icons.Default.Dashboard, "约束框") },
            canHaveChildren = true,
            properties = sizeProperties,
            luaTemplate = "compose.BoxWithConstraints({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        \n    },\n})"
        ),

        // ================================================================
        // 其他组件
        // ================================================================
        ComponentMeta(
            typeName = "Divider",
            displayName = "水平分割线",
            category = ComponentCategory.OTHER,
            description = "水平分割线",
            icon = { SimpleIcon(Icons.Default.MoreVert, "水平分割线") },
            canHaveChildren = false,
            luaTemplate = "compose.Divider({})"
        ),
        ComponentMeta(
            typeName = "VerticalDivider",
            displayName = "垂直分割线",
            category = ComponentCategory.OTHER,
            description = "垂直分割线",
            icon = { SimpleIcon(Icons.Default.MoreVert, "垂直分割线") },
            canHaveChildren = false,
            luaTemplate = "compose.VerticalDivider({})"
        ),
        ComponentMeta(
            typeName = "FloatingActionButton",
            displayName = "浮动按钮",
            category = ComponentCategory.OTHER,
            description = "Material 浮动操作按钮",
            icon = { SimpleIcon(Icons.Default.Add, "浮动按钮") },
            canHaveChildren = true,
            luaTemplate = "compose.FloatingActionButton({\n    onClick = function() end,\n    modifier = compose.Modifier().size(48, 48),\n    children = {\n        compose.Icon({ name = \"Add\", size = 24, color = 0xFFFFFFFF }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "SmallFloatingActionButton",
            displayName = "小浮动按钮",
            category = ComponentCategory.OTHER,
            description = "小号浮动操作按钮",
            icon = { SimpleIcon(Icons.Default.Add, "小浮动按钮") },
            canHaveChildren = true,
            luaTemplate = "compose.SmallFloatingActionButton({\n    onClick = function() end,\n    children = {\n        compose.Icon({ name = \"Edit\", size = 18, color = 0xFFFFFFFF }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "ExtendedFloatingActionButton",
            displayName = "扩展浮动按钮",
            category = ComponentCategory.OTHER,
            description = "带文字的扩展浮动按钮",
            icon = { SimpleIcon(Icons.Default.Add, "扩展浮动按钮") },
            defaultProps = mapOf(
                "text" to "创建"
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "text",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "创建",
                    category = "内容"
                )
            ),
            luaTemplate = "compose.ExtendedFloatingActionButton({\n    text = \"创建\",\n    onClick = function() end,\n    children = {\n        compose.Icon({ name = \"Add\", size = 20, color = 0xFFFFFFFF }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "BackHandler",
            displayName = "返回处理",
            category = ComponentCategory.OTHER,
            description = "拦截系统返回键",
            icon = { SimpleIcon(Icons.Default.ArrowBack, "返回处理") },
            defaultProps = mapOf(
                "enabled" to true
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "enabled",
                    displayName = "启用",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = true,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.BackHandler({\n    enabled = true,\n    onBack = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "HorizontalPager",
            displayName = "水平翻页",
            category = ComponentCategory.OTHER,
            description = "水平翻页容器",
            icon = { SimpleIcon(Icons.Default.ViewCarousel, "水平翻页") },
            defaultProps = mapOf(
                "pageCount" to 3,
                "beyondViewportPageCount" to 1
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "pageCount",
                    displayName = "页数",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 3,
                    category = "翻页"
                )
            ) + sizeProperties,
            luaTemplate = "compose.HorizontalPager({\n    pageCount = 3,\n    beyondViewportPageCount = 1,\n    onPageChanged = function(page) end,\n    modifier = compose.Modifier().fillMaxWidth().height(120),\n    children = function(page)\n        return compose.Text({ text = \"第 \" .. (page + 1) .. \" 页\" })\n    end,\n})"
        ),
        ComponentMeta(
            typeName = "VerticalPager",
            displayName = "垂直翻页",
            category = ComponentCategory.OTHER,
            description = "垂直翻页容器",
            icon = { SimpleIcon(Icons.Default.ViewCarousel, "垂直翻页") },
            defaultProps = mapOf(
                "pageCount" to 3,
                "beyondViewportPageCount" to 1
            ),
            canHaveChildren = true,
            properties = listOf(
                PropertyDescriptor(
                    key = "pageCount",
                    displayName = "页数",
                    editorType = PropertyEditorType.NUMBER,
                    defaultValue = 3,
                    category = "翻页"
                )
            ) + sizeProperties,
            luaTemplate = "compose.VerticalPager({\n    pageCount = 3,\n    beyondViewportPageCount = 1,\n    modifier = compose.Modifier().fillMaxWidth().height(100),\n    children = function(page)\n        return compose.Text({ text = \"第 \" .. (page + 1) .. \" 页\" })\n    end,\n})"
        ),
        ComponentMeta(
            typeName = "SingleChoiceSegmentedButtonRow",
            displayName = "单选分段",
            category = ComponentCategory.OTHER,
            description = "单选分段按钮组",
            icon = { SimpleIcon(Icons.Default.ToggleOn, "单选分段") },
            canHaveChildren = true,
            properties = sizeProperties,
            luaTemplate = "compose.SingleChoiceSegmentedButtonRow({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        compose.SegmentedButton({\n            selected = true,\n            text = \"选项1\",\n            onClick = function() end,\n        }),\n        compose.SegmentedButton({\n            selected = false,\n            text = \"选项2\",\n            onClick = function() end,\n        }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "MultiChoiceSegmentedButtonRow",
            displayName = "多选分段",
            category = ComponentCategory.OTHER,
            description = "多选分段按钮组",
            icon = { SimpleIcon(Icons.Default.CheckBox, "多选分段") },
            canHaveChildren = true,
            properties = sizeProperties,
            luaTemplate = "compose.MultiChoiceSegmentedButtonRow({\n    modifier = compose.Modifier().fillMaxWidth(),\n    children = {\n        compose.SegmentedButton({\n            checked = false,\n            text = \"选项1\",\n            onCheckedChange = function(v) end,\n        }),\n        compose.SegmentedButton({\n            checked = false,\n            text = \"选项2\",\n            onCheckedChange = function(v) end,\n        }),\n    },\n})"
        ),
        ComponentMeta(
            typeName = "SegmentedButton",
            displayName = "分段按钮",
            category = ComponentCategory.OTHER,
            description = "分段按钮组中的单个按钮",
            icon = { SimpleIcon(Icons.Default.ToggleOn, "分段按钮") },
            defaultProps = mapOf(
                "text" to "选项",
                "selected" to false
            ),
            canHaveChildren = false,
            properties = listOf(
                PropertyDescriptor(
                    key = "text",
                    displayName = "文本",
                    editorType = PropertyEditorType.TEXT,
                    defaultValue = "选项",
                    category = "内容"
                ),
                PropertyDescriptor(
                    key = "selected",
                    displayName = "已选中",
                    editorType = PropertyEditorType.BOOLEAN,
                    defaultValue = false,
                    category = "状态"
                )
            ),
            luaTemplate = "compose.SegmentedButton({\n    selected = false,\n    text = \"选项\",\n    onClick = function() end,\n})"
        ),
        ComponentMeta(
            typeName = "AndroidView",
            displayName = "原生视图",
            category = ComponentCategory.OTHER,
            description = "嵌入 Android 原生 View",
            icon = { SimpleIcon(Icons.Default.Extension, "原生视图") },
            canHaveChildren = false,
            properties = sizeProperties,
            luaTemplate = "compose.AndroidView({\n    viewName = \"TextView\",\n    modifier = compose.Modifier().fillMaxWidth(),\n})"
        )
    )

    /**
     * 按分类获取组件
     *
     * @param category 组件分类
     * @return 该分类下的所有组件
     */
    fun getByCategory(category: ComponentCategory): List<ComponentMeta> {
        return allComponents.filter { it.category == category }
    }

    /**
     * 根据类型名查找组件元数据
     *
     * @param typeName LuaCompose 类型名
     * @return 对应的组件元数据，未找到返回 null
     */
    fun findByTypeName(typeName: String): ComponentMeta? {
        return allComponents.find { it.typeName == typeName }
    }

    /**
     * 搜索组件
     *
     * @param query 搜索关键词（匹配显示名和类型名）
     * @return 匹配的组件列表
     */
    fun search(query: String): List<ComponentMeta> {
        if (query.isBlank()) return allComponents
        val lowerQuery = query.lowercase()
        return allComponents.filter {
            it.displayName.contains(query) ||
            it.typeName.lowercase().contains(lowerQuery) ||
            it.description.contains(query)
        }
    }
}

/**
 * 分类显示名称映射
 */
val ComponentCategory.displayName: String
    get() = when (this) {
        ComponentCategory.LAYOUT -> "布局"
        ComponentCategory.DISPLAY -> "显示"
        ComponentCategory.INPUT -> "输入"
        ComponentCategory.CONTAINER -> "容器"
        ComponentCategory.LIST -> "列表"
        ComponentCategory.ADVANCED -> "高级控件"
        ComponentCategory.NAVIGATION -> "导航"
        ComponentCategory.DIALOG -> "弹窗"
        ComponentCategory.FEEDBACK -> "反馈"
        ComponentCategory.ANIMATION -> "动画"
        ComponentCategory.DRAWING -> "绘图"
        ComponentCategory.EFFECT -> "效果"
        ComponentCategory.OTHER -> "其他"
    }