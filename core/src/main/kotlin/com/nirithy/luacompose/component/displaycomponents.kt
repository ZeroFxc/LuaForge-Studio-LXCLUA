package com.nirithy.luacompose.component

import com.nirithy.luacompose.logW
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer

/**
 * 显示组件插件：Text
 */
object DisplayComponents : ComposePlugin {
    override val namespace = "display"

    override fun getComponents() = mapOf<String, @Composable (ComposeNode) -> Unit>(
        "Text" to { node -> TextLayout(node) },
    )

    @Composable
    private fun TextLayout(node: ComposeNode) {
        // 支持 textLambda 回调实现响应式文本（mutableState 驱动）
        val textLambda = node.callbacks["textLambda"]
        val displayText: String = if (textLambda != null) {
            try {
                (textLambda.call() as? String) ?: (node.stringProp("text") ?: "")
            } catch (e: Exception) {
                logW("TextLayout") { "textLambda 调用失败: ${e.message}" }
                node.stringProp("text") ?: ""
            }
        } else {
            node.stringProp("text") ?: ""
        }
        Text(
            text = displayText, modifier = ComposeRenderer.resolveModifier(node),
            color = node.props["color"]?.let { resolveColor(it) } ?: Color.Unspecified,
            fontSize = node.floatProp("fontSize").let { if (it > 0f) it.sp else TextUnit.Unspecified },
            fontWeight = resolveFontWeight(node.props["fontWeight"]),
            fontStyle = resolveFontStyle(node.stringProp("fontStyle")),
            textAlign = resolveTextAlign(node.stringProp("textAlign")),
            maxLines = (node.props["maxLines"] as? Number)?.toInt() ?: Int.MAX_VALUE,
            overflow = resolveTextOverflow(node.stringProp("overflow")),
        )
    }
    /** Color(Int) 按 sRGB ARGB 解释，Double.toInt() 会截断溢出，必须经 toLong().toInt() 保留 bit pattern */
    private fun resolveColor(value: Any?): Color? = when (value) {
        is Long -> Color(value.toInt()); is Int -> Color(value)
        is Double -> Color(value.toLong().toInt())
        is Number -> Color(value.toInt()); else -> null
    }
    private fun resolveFontWeight(value: Any?): FontWeight? = when (value) {
        is FontWeight -> value
        is String -> resolveFontWeightByName(value)
        is Number -> FontWeight(value.toInt())
        else -> null
    }
    private fun resolveFontWeightByName(name: String): FontWeight? = when (name) {
        "Thin" -> FontWeight.Thin; "Light" -> FontWeight.Light; "Normal" -> FontWeight.Normal
        "Medium" -> FontWeight.Medium; "SemiBold" -> FontWeight.SemiBold; "Bold" -> FontWeight.Bold
        "ExtraBold" -> FontWeight.ExtraBold; "Black" -> FontWeight.Black; else -> null
    }
    private fun resolveFontStyle(name: String?): FontStyle? = when (name) { "Italic" -> FontStyle.Italic; else -> null }
    private fun resolveTextAlign(name: String?): TextAlign? = when (name) {
        "Left" -> TextAlign.Left; "Right" -> TextAlign.Right; "Center" -> TextAlign.Center; "Justify" -> TextAlign.Justify; else -> null
    }
    private fun resolveTextOverflow(name: String?): TextOverflow = when (name) { "Clip" -> TextOverflow.Clip; else -> TextOverflow.Ellipsis }
}