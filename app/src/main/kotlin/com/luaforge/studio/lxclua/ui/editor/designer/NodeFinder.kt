package com.luaforge.studio.lxclua.ui.editor.designer

import com.nirithy.luacompose.node.ComposeNode

/**
 * 根据节点路径递归查找对应的 ComposeNode
 *
 * 节点路径格式为以 "." 分隔的索引序列，如 "0.2.1" 表示：
 * 根节点的第 0 个子节点的第 2 个子节点的第 1 个子节点。
 *
 * @param path 节点路径字符串，如 "0"、"0.2.1"
 * @return 找到的节点，路径无效或未找到返回 null
 */
fun ComposeNode.findNodeByPath(path: String): ComposeNode? {
    if (path.isBlank()) return null
    val segments = path.split(".")
    return findNodeByPathSegments(segments, 0)
}

/**
 * 根据路径段数组递归查找节点
 *
 * @param segments 路径段数组（每个元素为子节点索引的字符串）
 * @param index 当前处理到的段索引
 * @return 找到的节点，未找到返回 null
 */
private fun ComposeNode.findNodeByPathSegments(segments: List<String>, index: Int): ComposeNode? {
    if (index >= segments.size) return this
    val childIndex = segments[index].toIntOrNull() ?: return null
    if (childIndex < 0 || childIndex >= children.size) return null
    return children[childIndex].findNodeByPathSegments(segments, index + 1)
}
