package com.luaforge.studio.lxclua.ui.editor.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nirithy.luacompose.node.ComposeNode
import kotlinx.coroutines.delay

/**
 * 属性编辑面板
 *
 * 显示当前选中组件的可编辑属性，支持按分组展示、多种编辑器类型，
 * 属性修改后通过 300ms 防抖 + 失去焦点提交到外部 onPropertyChanged，
 * 避免每次按键触发代码生成导致预览刷新和光标丢失。
 *
 * @param selectedNode 当前选中的节点，null 表示未选中
 * @param onPropertyChanged 属性变化回调，参数为 (属性key, 新值)，防抖后触发
 * @param modifier 修饰符
 */
@Composable
fun PropertyPanel(
    selectedNode: ComposeNode?,
    onPropertyChanged: (String, Any?) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PropertyPanelHeader(selectedNode)

            HorizontalDivider()

            when {
                selectedNode == null -> {
                    EmptySelectionHint()
                }
                else -> {
                    val meta = ComponentLibrary.findByTypeName(selectedNode.type)
                    if (meta == null) {
                        NoEditablePropertiesHint()
                    } else {
                        val hasProperties = meta.properties.isNotEmpty()
                        val hasTemplate = !meta.luaTemplate.isNullOrBlank()
                        if (hasProperties) {
                            PropertyContent(
                                node = selectedNode,
                                properties = meta.properties,
                                onPropertyChanged = onPropertyChanged,
                                luaTemplate = meta.luaTemplate
                            )
                        } else if (hasTemplate) {
                            LuaTemplateView(meta.luaTemplate!!)
                        } else {
                            NoEditablePropertiesHint()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 属性面板标题栏
 *
 * 左侧显示"属性"标题，右侧显示当前选中节点的类型名称。
 */
@Composable
private fun PropertyPanelHeader(selectedNode: ComposeNode?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "属性",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        if (selectedNode != null) {
            Text(
                text = selectedNode.type,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 未选中组件时的提示
 */
@Composable
private fun EmptySelectionHint() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "未选中组件\n点击画布中的组件进行选择",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 组件未注册可编辑属性时的提示
 */
@Composable
private fun NoEditablePropertiesHint() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "该组件属性不可编辑",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 属性内容区域
 *
 * 按分类分组显示属性，分类之间有标题和分隔线。
 *
 * @param node 当前选中的节点
 * @param properties 属性描述符列表
 * @param onPropertyChanged 属性变化回调（防抖）
 */
@Composable
private fun PropertyContent(
    node: ComposeNode,
    properties: List<PropertyDescriptor>,
    onPropertyChanged: (String, Any?) -> Unit,
    luaTemplate: String? = null
) {
    val groupedProperties = remember(properties) {
        properties.groupBy { it.category }
    }

    val categoryOrder = remember(properties) {
        properties.map { it.category }.distinct()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        categoryOrder.forEachIndexed { categoryIndex, category ->
            val props = groupedProperties[category] ?: return@forEachIndexed

            item {
                PropertyCategoryHeader(
                    title = category,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }

            items(props, key = { it.key }) { prop ->
                PropertyEditorRow(
                    node = node,
                    descriptor = prop,
                    onPropertyChanged = onPropertyChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (categoryIndex < categoryOrder.size - 1) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // 代码模板预览
        if (!luaTemplate.isNullOrBlank()) {
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
            item {
                PropertyCategoryHeader(
                    title = "代码模板",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
            item {
                LuaTemplateView(luaTemplate)
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * 属性分类标题
 */
@Composable
private fun PropertyCategoryHeader(
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
 * 单个属性编辑行
 *
 * 根据属性描述符的编辑器类型分发到对应的编辑器 Composable。
 */
@Composable
private fun PropertyEditorRow(
    node: ComposeNode,
    descriptor: PropertyDescriptor,
    onPropertyChanged: (String, Any?) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentValue = node.props[descriptor.key] ?: descriptor.defaultValue

    Column(modifier = modifier) {
        when (descriptor.editorType) {
            PropertyEditorType.TEXT -> {
                TextPropertyEditor(
                    displayName = descriptor.displayName,
                    value = (currentValue as? String) ?: "",
                    onValueChange = { onPropertyChanged(descriptor.key, it) }
                )
            }
            PropertyEditorType.NUMBER -> {
                NumberPropertyEditor(
                    displayName = descriptor.displayName,
                    value = when (currentValue) {
                        is Number -> currentValue.toString()
                        else -> ""
                    },
                    onValueChange = { onPropertyChanged(descriptor.key, it) }
                )
            }
            PropertyEditorType.BOOLEAN -> {
                BooleanPropertyEditor(
                    displayName = descriptor.displayName,
                    value = currentValue as? Boolean ?: false,
                    onValueChange = { onPropertyChanged(descriptor.key, it) }
                )
            }
            PropertyEditorType.COLOR -> {
                ColorPropertyEditor(
                    displayName = descriptor.displayName,
                    value = when (currentValue) {
                        is Long -> currentValue
                        is Number -> currentValue.toLong()
                        else -> 0xFF000000L
                    },
                    onValueChange = { onPropertyChanged(descriptor.key, it) }
                )
            }
            PropertyEditorType.ENUM,
            PropertyEditorType.TEXT_STYLE,
            PropertyEditorType.ALIGN_H,
            PropertyEditorType.ALIGN_V,
            PropertyEditorType.ARRANGE_H,
            PropertyEditorType.ARRANGE_V -> {
                EnumPropertyEditor(
                    displayName = descriptor.displayName,
                    value = (currentValue as? String) ?: (descriptor.defaultValue as? String) ?: "",
                    options = descriptor.enumOptions,
                    onValueChange = { onPropertyChanged(descriptor.key, it) }
                )
            }
        }
    }
}

/**
 * 文本属性编辑器
 *
 * 使用 OutlinedTextField 单行输入，内部维护本地状态，
 * 300ms 防抖 + 失去焦点时提交变更，避免每次按键触发代码生成。
 */
@Composable
private fun TextPropertyEditor(
    displayName: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var localValue by remember(value) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    var lastCommitValue by remember(value) { mutableStateOf(value) }

    fun commitIfNeeded() {
        if (localValue != lastCommitValue) {
            lastCommitValue = localValue
            onValueChange(localValue)
        }
    }

    LaunchedEffect(localValue) {
        if (localValue != lastCommitValue) {
            delay(300)
            commitIfNeeded()
        }
    }

    OutlinedTextField(
        value = localValue,
        onValueChange = { localValue = it },
        label = { Text(displayName, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (isFocused && !focusState.isFocused) {
                    commitIfNeeded()
                }
                isFocused = focusState.isFocused
            },
        textStyle = MaterialTheme.typography.bodySmall
    )
}

/**
 * 数字属性编辑器
 *
 * 使用 OutlinedTextField，键盘类型为 Number，输入解析为 Float。
 * 内部维护本地文本状态，300ms 防抖 + 失去焦点时提交有效数值。
 */
@Composable
private fun NumberPropertyEditor(
    displayName: String,
    value: String,
    onValueChange: (Float) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    var lastCommitText by remember(value) { mutableStateOf(value) }

    fun commitIfNeeded() {
        if (textValue != lastCommitText) {
            val parsed = textValue.toFloatOrNull()
            if (parsed != null) {
                lastCommitText = textValue
                onValueChange(parsed)
            }
        }
    }

    LaunchedEffect(textValue) {
        if (textValue != lastCommitText) {
            delay(300)
            commitIfNeeded()
        }
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            textValue = newText
        },
        label = { Text(displayName, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (isFocused && !focusState.isFocused) {
                    commitIfNeeded()
                }
                isFocused = focusState.isFocused
            },
        textStyle = MaterialTheme.typography.bodySmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

/**
 * 布尔属性编辑器
 *
 * 左侧显示属性名，右侧显示 Switch 开关，即时触发变更。
 */
@Composable
private fun BooleanPropertyEditor(
    displayName: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = value,
            onCheckedChange = onValueChange
        )
    }
}

/**
 * 颜色属性编辑器
 *
 * 上方显示属性名和当前颜色预览，下方一排预设颜色方块供选择。
 * 点击颜色方块即时触发变更。
 */
@Composable
private fun ColorPropertyEditor(
    displayName: String,
    value: Long,
    onValueChange: (Long) -> Unit
) {
    Text(
        text = displayName,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PresetColors.colors.forEachIndexed { index, colorValue ->
            val isSelected = colorValue == value
            ColorSwatch(
                color = Color(colorValue.toInt()),
                isSelected = isSelected,
                onClick = { onValueChange(colorValue) },
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * 单个颜色方块
 *
 * 可点击的颜色预览方块，选中时有 primary 边框高亮。
 */
@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    )
                }
            )
            .padding(if (isSelected) 2.dp else 1.dp)
            .background(color, RoundedCornerShape(3.dp))
    )
}

/**
 * 枚举属性编辑器（下拉选择）
 *
 * 使用 ExposedDropdownMenuBox 实现下拉选择，选择即时触发变更。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumPropertyEditor(
    displayName: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(displayName, style = MaterialTheme.typography.bodySmall) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            textStyle = MaterialTheme.typography.bodySmall,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Lua 代码模板预览
 *
 * 以只读代码块形式展示组件的 Lua 代码模板，
 * 使用等宽字体和浅色背景，便于用户参考生成的代码格式。
 *
 * @param template Lua 代码模板字符串
 */
@Composable
private fun LuaTemplateView(template: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Text(
            text = template,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
