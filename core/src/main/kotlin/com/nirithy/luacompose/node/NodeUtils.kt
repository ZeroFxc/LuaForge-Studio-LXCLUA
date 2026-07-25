package com.nirithy.luacompose.node

/**
 * 递归为节点树中的每个节点分配 nodePath
 *
 * 节点路径格式为以 "." 分隔的索引序列，根节点路径为 "0"，
 * 子节点路径为 "父路径.子索引"，如 "0.2.1" 表示根节点的第0个子节点的第2个子节点的第1个子节点。
 *
 * @param node 当前节点
 * @param path 当前节点的路径
 * @return 分配了 nodePath 的新节点（ComposeNode不可变，需要复制）
 */
fun assignNodePaths(node: ComposeNode, path: String): ComposeNode {
    val mappedChildren = node.children.mapIndexed { index, child ->
        assignNodePaths(child, "$path.$index")
    }
    return node.copy(
        children = mappedChildren,
        nodePath = path
    )
}
