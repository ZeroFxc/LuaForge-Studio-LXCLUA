package com.nirithy.luacompose.component

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
    )

    @Composable private fun ButtonLayout(node: ComposeNode) {
        Button(onClick = { invokeCallback(node, "onClick") }, modifier = ComposeRenderer.resolveModifier(node),
            enabled = node.boolProp("enabled", true),
            colors = node.props["color"]?.let { resolveColor(it) }?.let { ButtonDefaults.buttonColors(containerColor = it) } ?: ButtonDefaults.buttonColors(),
        ) { ComposeRenderer.RenderChildren(node) }
    }
    @Composable private fun TextButtonLayout(node: ComposeNode) {
        TextButton(onClick = { invokeCallback(node, "onClick") }, modifier = ComposeRenderer.resolveModifier(node),
            enabled = node.boolProp("enabled", true),
        ) { ComposeRenderer.RenderChildren(node) }
    }
    @Composable private fun OutlinedButtonLayout(node: ComposeNode) {
        OutlinedButton(onClick = { invokeCallback(node, "onClick") }, modifier = ComposeRenderer.resolveModifier(node),
            enabled = node.boolProp("enabled", true),
        ) { ComposeRenderer.RenderChildren(node) }
    }
    @Composable private fun IconButtonLayout(node: ComposeNode) {
        IconButton(onClick = { invokeCallback(node, "onClick") }, modifier = ComposeRenderer.resolveModifier(node),
            enabled = node.boolProp("enabled", true),
        ) { ComposeRenderer.RenderChildren(node) }
    }
    @Composable private fun TextFieldLayout(node: ComposeNode) {
        var text by remember { mutableStateOf(node.stringProp("text") ?: "") }
        val label = node.stringProp("label") ?: ""; val placeholder = node.stringProp("placeholder") ?: ""
        TextField(value = text, onValueChange = { newValue -> text = newValue; invokeCallback(node, "onValueChange", newValue) },
            modifier = ComposeRenderer.resolveModifier(node), enabled = node.boolProp("enabled", true),
            label = { if (label.isNotEmpty()) Text(label) }, placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            singleLine = node.boolProp("singleLine", false), readOnly = node.boolProp("readOnly", false),
        )
    }
    @Composable private fun OutlinedTextFieldLayout(node: ComposeNode) {
        var text by remember { mutableStateOf(node.stringProp("text") ?: "") }
        val label = node.stringProp("label") ?: ""; val placeholder = node.stringProp("placeholder") ?: ""
        OutlinedTextField(value = text, onValueChange = { newValue -> text = newValue; invokeCallback(node, "onValueChange", newValue) },
            modifier = ComposeRenderer.resolveModifier(node), enabled = node.boolProp("enabled", true),
            label = { if (label.isNotEmpty()) Text(label) }, placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            singleLine = node.boolProp("singleLine", false), readOnly = node.boolProp("readOnly", false),
        )
    }
    @Composable private fun CheckboxLayout(node: ComposeNode) {
        var checked by remember { mutableStateOf(node.boolProp("checked", false)) }
        Checkbox(checked = checked, onCheckedChange = { newValue -> checked = newValue; invokeCallback(node, "onCheckedChange", newValue) },
            modifier = ComposeRenderer.resolveModifier(node), enabled = node.boolProp("enabled", true))
    }
    @Composable private fun SwitchLayout(node: ComposeNode) {
        var checked by remember { mutableStateOf(node.boolProp("checked", false)) }
        Switch(checked = checked, onCheckedChange = { newValue -> checked = newValue; invokeCallback(node, "onCheckedChange", newValue) },
            modifier = ComposeRenderer.resolveModifier(node), enabled = node.boolProp("enabled", true))
    }
    @Composable private fun SliderLayout(node: ComposeNode) {
        var value by remember { mutableStateOf(node.floatProp("value", 0f)) }
        val range = (node.props["valueRange"] as? List<*>)?.let {
            ((it.getOrNull(0) as? Number)?.toFloat() ?: 0f)..((it.getOrNull(1) as? Number)?.toFloat() ?: 1f)
        } ?: 0f..1f
        Slider(value = value, onValueChange = { newValue -> value = newValue; invokeCallback(node, "onValueChange", newValue) },
            modifier = ComposeRenderer.resolveModifier(node), enabled = node.boolProp("enabled", true), valueRange = range)
    }
    private fun invokeCallback(node: ComposeNode, key: String, vararg args: Any?) {
        try { node.callback(key)?.call(*args) } catch (e: LuaException) { e.printStackTrace() }
    }
    /** Color(Int) 按 sRGB ARGB 解释，Double.toInt() 会截断溢出 */
    private fun resolveColor(value: Any?): Color? = when (value) {
        is Long -> Color(value.toInt()); is Int -> Color(value)
        is Double -> Color(value.toLong().toInt())
        is Number -> Color(value.toInt()); else -> null
    }
}