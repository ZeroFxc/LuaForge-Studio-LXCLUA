package com.nirithy.luacompose.component

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer
import com.luajava.LuaException

/**
 * 输入组件插件：Button、TextButton、OutlinedButton、IconButton、TextField、OutlinedTextField、Checkbox、Switch、Slider
 */
object InputComponents : ComposePlugin {
    override val namespace = "input"

    override fun getComponents() = mapOf<String, @Composable (ComposeNode) -> Unit>(
        "Button" to { node -> ButtonLayout(node) },
        "TextButton" to { node -> TextButtonLayout(node) },
        "OutlinedButton" to { node -> OutlinedButtonLayout(node) },
        "IconButton" to { node -> IconButtonLayout(node) },
        "TextField" to { node -> TextFieldLayout(node) },
        "OutlinedTextField" to { node -> OutlinedTextFieldLayout(node) },
        "Checkbox" to { node -> CheckboxLayout(node) },
        "Switch" to { node -> SwitchLayout(node) },
        "Slider" to { node -> SliderLayout(node) },
        "RadioButton" to { node -> RadioButtonLayout(node) },
    )

    /**
     * 渲染按钮内容：优先使用 children 子节点，否则回退到 text prop
     * 支持 textLambda 回调以响应 mutableState 变化
     */
    @Composable
    private fun ButtonContent(node: ComposeNode) {
        if (node.children.isNotEmpty()) {
            ComposeRenderer.RenderChildren(node)
        } else {
            // 支持 textLambda 回调（动态文字，响应 mutableState）
            val textLambda = node.callback("textLambda")
            if (textLambda != null) {
                Text(text = textLambda.call()?.toString() ?: "")
            } else {
                val text = node.stringProp("text")
                if (text != null) Text(text)
            }
        }
    }

    @Composable private fun ButtonLayout(node: ComposeNode) {
        Button(onClick = { invokeCallback(node, "onClick") }, modifier = ComposeRenderer.resolveModifier(node),
            enabled = node.boolProp("enabled", true),
            colors = node.props["color"]?.let { resolveColor(it) }?.let { ButtonDefaults.buttonColors(containerColor = it) } ?: ButtonDefaults.buttonColors(),
        ) { ButtonContent(node) }
    }
    @Composable private fun TextButtonLayout(node: ComposeNode) {
        TextButton(onClick = { invokeCallback(node, "onClick") }, modifier = ComposeRenderer.resolveModifier(node),
            enabled = node.boolProp("enabled", true),
        ) { ButtonContent(node) }
    }
    @Composable private fun OutlinedButtonLayout(node: ComposeNode) {
        OutlinedButton(onClick = { invokeCallback(node, "onClick") }, modifier = ComposeRenderer.resolveModifier(node),
            enabled = node.boolProp("enabled", true),
        ) { ButtonContent(node) }
    }
    @Composable private fun IconButtonLayout(node: ComposeNode) {
        IconButton(onClick = { invokeCallback(node, "onClick") }, modifier = ComposeRenderer.resolveModifier(node),
            enabled = node.boolProp("enabled", true),
        ) { ComposeRenderer.RenderChildren(node) }
    }
    @Composable private fun TextFieldLayout(node: ComposeNode) {
        var text by remember { mutableStateOf(node.stringProp("text") ?: "") }
        val label = node.stringProp("label") ?: ""; val placeholder = node.stringProp("placeholder") ?: ""
        // 同步外部props变化到本地状态（受控组件模式）
        val externalText = node.stringProp("text") ?: ""
        LaunchedEffect(externalText) { if (text != externalText) text = externalText }
        TextField(value = text, onValueChange = { newValue -> text = newValue; invokeCallback(node, "onValueChange", newValue) },
            modifier = ComposeRenderer.resolveModifier(node), enabled = node.boolProp("enabled", true),
            label = { if (label.isNotEmpty()) Text(label) }, placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            singleLine = node.boolProp("singleLine", false), readOnly = node.boolProp("readOnly", false),
        )
    }
    @Composable private fun OutlinedTextFieldLayout(node: ComposeNode) {
        var text by remember { mutableStateOf(node.stringProp("text") ?: "") }
        val label = node.stringProp("label") ?: ""; val placeholder = node.stringProp("placeholder") ?: ""
        val externalText = node.stringProp("text") ?: ""
        LaunchedEffect(externalText) { if (text != externalText) text = externalText }
        OutlinedTextField(value = text, onValueChange = { newValue -> text = newValue; invokeCallback(node, "onValueChange", newValue) },
            modifier = ComposeRenderer.resolveModifier(node), enabled = node.boolProp("enabled", true),
            label = { if (label.isNotEmpty()) Text(label) }, placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            singleLine = node.boolProp("singleLine", false), readOnly = node.boolProp("readOnly", false),
        )
    }
    @Composable private fun CheckboxLayout(node: ComposeNode) {
        var checked by remember { mutableStateOf(node.boolProp("checked", false)) }
        val externalChecked = node.boolProp("checked", false)
        LaunchedEffect(externalChecked) { if (checked != externalChecked) checked = externalChecked }
        Checkbox(checked = checked, onCheckedChange = { newValue -> checked = newValue; invokeCallback(node, "onCheckedChange", newValue) },
            modifier = ComposeRenderer.resolveModifier(node), enabled = node.boolProp("enabled", true))
    }
    @Composable private fun SwitchLayout(node: ComposeNode) {
        var checked by remember { mutableStateOf(node.boolProp("checked", false)) }
        val externalChecked = node.boolProp("checked", false)
        LaunchedEffect(externalChecked) { if (checked != externalChecked) checked = externalChecked }
        Switch(checked = checked, onCheckedChange = { newValue -> checked = newValue; invokeCallback(node, "onCheckedChange", newValue) },
            modifier = ComposeRenderer.resolveModifier(node), enabled = node.boolProp("enabled", true))
    }
    @Composable private fun SliderLayout(node: ComposeNode) {
        var value by remember { mutableStateOf(node.floatProp("value", 0f)) }
        val externalValue = node.floatProp("value", 0f)
        LaunchedEffect(externalValue) { if (kotlin.math.abs(value - externalValue) > 0.001f) value = externalValue }
        val range = (node.props["valueRange"] as? List<*>)?.let {
            ((it.getOrNull(0) as? Number)?.toFloat() ?: 0f)..((it.getOrNull(1) as? Number)?.toFloat() ?: 1f)
        } ?: 0f..1f
        Slider(value = value, onValueChange = { newValue -> value = newValue; invokeCallback(node, "onValueChange", newValue) },
            modifier = ComposeRenderer.resolveModifier(node), enabled = node.boolProp("enabled", true), valueRange = range)
    }
    @Composable private fun RadioButtonLayout(node: ComposeNode) {
        var selected by remember { mutableStateOf(node.boolProp("selected", false)) }
        val externalSelected = node.boolProp("selected", false)
        LaunchedEffect(externalSelected) { if (selected != externalSelected) selected = externalSelected }
        RadioButton(
            selected = selected,
            onClick = { selected = true; invokeCallback(node, "onClick") },
            modifier = ComposeRenderer.resolveModifier(node),
            enabled = node.boolProp("enabled", true),
        )
    }
    private fun invokeCallback(node: ComposeNode, key: String, vararg args: Any?) {
        synchronized(ComposeBridgeInstance.current.luaLock) {
            try { node.callback(key)?.call(*args) } catch (e: LuaException) { e.printStackTrace() }
        }
    }
    /** Color(Int) 按 sRGB ARGB 解释，Double.toInt() 会截断溢出 */
    private fun resolveColor(value: Any?): Color? = when (value) {
        is Long -> Color(value.toInt()); is Int -> Color(value)
        is Double -> Color(value.toLong().toInt())
        is Number -> Color(value.toInt()); else -> null
    }
}