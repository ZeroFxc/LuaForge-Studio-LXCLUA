package com.luaforge.studio.lxclua.ui.editor.designer

import com.nirithy.luacompose.node.ComposeNode

/**
 * Lua代码生成器
 *
 * 从 ComposeNode 树生成可运行的 LuaCompose 代码。
 * 生成的代码格式遵循 LuaCompose API 规范：
 * - 入口: compose.render(function() return ... end)
 * - 组件: compose.TypeName({ key = value, children = { ... } })
 * - 属性值根据类型格式化，支持字符串、数字、布尔、颜色(0xAARRGGBB)
 * - 回调函数(以on开头)生成占位: function() end
 * - modifier 属性不生成（避免复杂链式调用错误）
 */
object LuaCodeGenerator {

    private const val INDENT = "  "
    private const val NEWLINE = "\n"

    /**
     * 从节点树生成完整可运行的 Lua 代码
     *
     * @param rootNode 根节点（通常是Column/Row/Box等布局组件）
     * @return 完整 Lua 代码字符串
     */
    fun generate(rootNode: ComposeNode): String {
        val sb = StringBuilder()
        sb.append("compose.render(function()").append(NEWLINE)
        sb.append(INDENT).append("return ")
        generateNode(sb, rootNode, 1)
        sb.append(NEWLINE).append("end)")
        return sb.toString()
    }

    /**
     * 递归生成单个节点的Lua代码
     *
     * @param sb StringBuilder 用于拼接代码
     * @param node 当前节点
     * @param depth 当前缩进深度
     */
    private fun generateNode(sb: StringBuilder, node: ComposeNode, depth: Int) {
        val indent = INDENT.repeat(depth)
        val childIndent = INDENT.repeat(depth + 1)

        sb.append("compose.").append(node.type).append("({").append(NEWLINE)

        val propLines = mutableListOf<String>()

        for ((key, value) in node.props) {
            if (key == "modifier") continue
            if (shouldSkipProperty(key, value)) continue

            val formattedValue = formatPropertyValue(key, value, node, depth + 1)
            propLines.add("$childIndent$key = $formattedValue,")
        }

        for (key in node.callbacks.keys) {
            propLines.add("$childIndent$key = function() end,")
        }

        for (i in propLines.indices) {
            sb.append(propLines[i])
            if (i < propLines.size - 1 || node.children.isNotEmpty()) {
                sb.append(NEWLINE)
            }
        }

        if (node.children.isNotEmpty()) {
            if (propLines.isNotEmpty()) {
                sb.append(NEWLINE)
            }
            sb.append(childIndent).append("children = {").append(NEWLINE)
            for ((index, child) in node.children.withIndex()) {
                sb.append(INDENT.repeat(depth + 2))
                generateNode(sb, child, depth + 2)
                if (index < node.children.size - 1) {
                    sb.append(",").append(NEWLINE).append(NEWLINE)
                } else {
                    sb.append(NEWLINE)
                }
            }
            sb.append(childIndent).append("}")
        }

        sb.append(NEWLINE).append(indent).append("})")
    }

    /**
     * 判断是否应该跳过某个属性
     * 跳过modifier，以及值为null的属性
     */
    private fun shouldSkipProperty(key: String, value: Any?): Boolean {
        if (key == "modifier") return true
        if (value == null) return true
        return false
    }

    /**
     * 格式化属性值为Lua代码字符串
     *
     * @param key 属性名
     * @param value 属性值
     * @param node 所属节点（用于检测上下文）
     * @param depth 当前缩进深度
     * @return Lua 格式的值字符串
     */
    private fun formatPropertyValue(key: String, value: Any?, node: ComposeNode, depth: Int): String {
        if (value == null) return "nil"

        if (key.startsWith("on")) {
            return "function() end"
        }

        return when (value) {
            is String -> formatString(value)
            is Boolean -> if (value) "true" else "false"
            is Int -> value.toString()
            is Long -> {
                if (key == "color" || key.endsWith("Color") || isColorValue(value)) {
                    formatColor(value)
                } else {
                    value.toString()
                }
            }
            is Float -> {
                if (value == value.toInt().toFloat() && !value.isInfinite() && !value.isNaN()) {
                    String.format("%.1f", value)
                } else {
                    value.toString()
                }
            }
            is Double -> {
                if (value == value.toInt().toDouble() && !value.isInfinite() && !value.isNaN()) {
                    String.format("%.1f", value)
                } else {
                    value.toString()
                }
            }
            is Number -> value.toString()
            else -> formatString(value.toString())
        }
    }

    /**
     * 格式化字符串值为Lua字符串（双引号包裹，转义特殊字符）
     */
    private fun formatString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    /**
     * 格式化颜色值为0xFFAARRGGBB格式（大写hex）
     */
    private fun formatColor(color: Long): String {
        val a = (color shr 24) and 0xFF
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return "0x%02X%02X%02X%02X".format(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    }

    /**
     * 粗略判断一个Long值是否可能是颜色
     * 颜色通常在0x00000000到0xFFFFFFFF之间，且常见颜色值高位为FF（不透明）
     */
    private fun isColorValue(value: Long): Boolean {
        return value in 0x00000000..0xFFFFFFFFL
    }
}
