package com.luaforge.studio.lxclua.ui.editor.designer

import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.node.assignNodePaths

/**
 * 节点树变更操作
 */
sealed class NodeMutation {
    /**
     * 修改节点属性
     * @param nodePath 目标节点路径
     * @param key 属性键名
     * @param value 新的属性值
     */
    data class UpdateProperty(val nodePath: String, val key: String, val value: Any?) : NodeMutation()

    /**
     * 添加子节点
     * @param parentPath 父节点路径
     * @param child 要添加的子节点
     * @param insertIndex 插入位置，-1表示追加到末尾
     */
    data class AddChild(val parentPath: String, val child: ComposeNode, val insertIndex: Int = -1) : NodeMutation()

    /**
     * 删除节点（不能删除根节点）
     * @param nodePath 要删除的节点路径
     */
    data class RemoveNode(val nodePath: String) : NodeMutation()

    /**
     * 移动节点
     * @param sourcePath 源节点路径
     * @param targetParentPath 目标父节点路径
     * @param insertIndex 目标位置索引
     */
    data class MoveNode(val sourcePath: String, val targetParentPath: String, val insertIndex: Int) : NodeMutation()

    /**
     * 复制节点（在原节点后面插入副本）
     * @param nodePath 要复制的节点路径
     */
    data class Duplicate(val nodePath: String) : NodeMutation()

    /**
     * 调整同级节点顺序
     * @param nodePath 要移动的节点路径
     * @param delta 移动方向：-1上移（索引-1），+1下移（索引+1）
     */
    data class ReorderSibling(val nodePath: String, val delta: Int) : NodeMutation()
}

/**
 * 节点树变更操作器
 *
 * 对不可变的 ComposeNode 树应用变更，返回新的节点树。
 * 每次变更后自动重新分配所有节点路径。
 */
object NodeMutator {

    /**
     * 对根节点应用变更，返回新的节点树（已重新分配路径）
     *
     * @param root 原始根节点
     * @param mutation 要应用的变更操作
     * @return 应用变更后的新根节点（路径为"0"）
     * @throws IllegalArgumentException 当路径无效或操作非法时抛出
     */
    fun applyMutation(root: ComposeNode, mutation: NodeMutation): ComposeNode {
        val newRoot = when (mutation) {
            is NodeMutation.UpdateProperty -> applyUpdateProperty(root, mutation)
            is NodeMutation.AddChild -> applyAddChild(root, mutation)
            is NodeMutation.RemoveNode -> applyRemoveNode(root, mutation)
            is NodeMutation.MoveNode -> applyMoveNode(root, mutation)
            is NodeMutation.Duplicate -> applyDuplicate(root, mutation)
            is NodeMutation.ReorderSibling -> applyReorderSibling(root, mutation)
        }
        return assignNodePaths(newRoot, "0")
    }

    /**
     * 深拷贝节点及其子树
     * props/callbacks 浅拷贝（LuaObject引用保持，因为它们是Lua侧对象），children递归深拷贝
     * 新节点的 nodePath 为 null（后续由 assignNodePaths 统一分配）
     */
    fun deepCopy(node: ComposeNode): ComposeNode {
        return ComposeNode(
            type = node.type,
            props = node.props.toMap(),
            children = node.children.map { deepCopy(it) },
            callbacks = node.callbacks.toMap(),
            childrenFunc = node.childrenFunc,
            nodePath = null
        )
    }

    /**
     * 根据组件元数据创建默认节点
     *
     * 使用meta.defaultProps作为初始属性，如果meta.defaultChildren不为空，
     * 则递归创建子节点（通过ComponentLibrary查找子组件元数据）。
     *
     * @param meta 组件元数据
     * @return 创建的ComposeNode（未分配nodePath）
     */
    fun createNodeFromMeta(meta: ComponentMeta): ComposeNode {
        val children = if (meta.defaultChildren.isNotEmpty()) {
            meta.defaultChildren.mapNotNull { childTypeName ->
                val childMeta = ComponentLibrary.findByTypeName(childTypeName)
                if (childMeta != null) {
                    val childProps = if (childMeta.defaultProps.containsKey("text") && meta.defaultProps.containsKey("text")) {
                        childMeta.defaultProps + ("text" to meta.defaultProps["text"]!!)
                    } else {
                        childMeta.defaultProps
                    }
                    createNodeFromMeta(childMeta.copy(defaultProps = childProps))
                } else null
            }
        } else {
            emptyList()
        }

        return ComposeNode(
            type = meta.typeName,
            props = meta.defaultProps,
            children = children
        )
    }

    /**
     * 在指定父节点中插入新组件
     *
     * 根据组件元数据创建新节点，并在父节点的指定索引位置插入。
     * 返回应用变更后的新节点树（已重新分配路径）。
     *
     * @param root 根节点
     * @param parentPath 父节点路径
     * @param index 插入索引，-1 表示追加到末尾
     * @param componentMeta 组件元数据
     * @return 应用变更后的新根节点
     */
    fun insertComponentAt(root: ComposeNode, parentPath: String, index: Int, componentMeta: ComponentMeta): ComposeNode {
        val newNode = createNodeFromMeta(componentMeta)
        return applyMutation(root, NodeMutation.AddChild(parentPath, newNode, index))
    }

    /**
     * 更新节点属性
     */
    private fun applyUpdateProperty(root: ComposeNode, mutation: NodeMutation.UpdateProperty): ComposeNode {
        val segments = parsePath(mutation.nodePath)
        require(segments.isNotEmpty()) { "节点路径不能为空" }
        return updatePropertyRecursive(root, segments, 0, mutation.key, mutation.value)
    }

    /**
     * 递归更新属性
     */
    private fun updatePropertyRecursive(
        node: ComposeNode,
        segments: List<Int>,
        index: Int,
        key: String,
        value: Any?
    ): ComposeNode {
        if (index == segments.size - 1) {
            val newProps = node.props.toMutableMap()
            if (value == null) {
                newProps.remove(key)
            } else {
                newProps[key] = value
            }
            return node.copy(props = newProps)
        }
        val childIndex = segments[index + 1]
        require(childIndex >= 0 && childIndex < node.children.size) {
            "子节点索引越界: $childIndex, 子节点数: ${node.children.size}"
        }
        val newChildren = node.children.toMutableList()
        newChildren[childIndex] = updatePropertyRecursive(newChildren[childIndex], segments, index + 1, key, value)
        return node.copy(children = newChildren)
    }

    /**
     * 添加子节点
     */
    private fun applyAddChild(root: ComposeNode, mutation: NodeMutation.AddChild): ComposeNode {
        if (mutation.parentPath == "0" || mutation.parentPath.isEmpty()) {
            val newChildren = root.children.toMutableList()
            val insertIndex = if (mutation.insertIndex < 0) newChildren.size else mutation.insertIndex.coerceIn(0, newChildren.size)
            newChildren.add(insertIndex, mutation.child)
            return root.copy(children = newChildren)
        }
        val segments = parsePath(mutation.parentPath)
        return addChildRecursive(root, segments, 0, mutation.child, mutation.insertIndex)
    }

    /**
     * 递归添加子节点
     */
    private fun addChildRecursive(
        node: ComposeNode,
        segments: List<Int>,
        index: Int,
        child: ComposeNode,
        insertIndex: Int
    ): ComposeNode {
        if (index == segments.size - 1) {
            val newChildren = node.children.toMutableList()
            val actualInsertIndex = if (insertIndex < 0) newChildren.size else insertIndex.coerceIn(0, newChildren.size)
            newChildren.add(actualInsertIndex, child)
            return node.copy(children = newChildren)
        }
        val childIndex = segments[index + 1]
        require(childIndex >= 0 && childIndex < node.children.size) {
            "子节点索引越界: $childIndex, 子节点数: ${node.children.size}"
        }
        val newChildren = node.children.toMutableList()
        newChildren[childIndex] = addChildRecursive(newChildren[childIndex], segments, index + 1, child, insertIndex)
        return node.copy(children = newChildren)
    }

    /**
     * 删除节点
     */
    private fun applyRemoveNode(root: ComposeNode, mutation: NodeMutation.RemoveNode): ComposeNode {
        val segments = parsePath(mutation.nodePath)
        require(segments.size > 1) { "不能删除根节点" }
        return removeNodeRecursive(root, segments, 0)
    }

    /**
     * 递归删除节点
     */
    private fun removeNodeRecursive(
        node: ComposeNode,
        segments: List<Int>,
        index: Int
    ): ComposeNode {
        val childIndex = segments[index + 1]
        require(childIndex >= 0 && childIndex < node.children.size) {
            "子节点索引越界: $childIndex, 子节点数: ${node.children.size}"
        }
        if (index == segments.size - 2) {
            val newChildren = node.children.toMutableList()
            val removed = newChildren.removeAt(childIndex)
            removed.release()
            return node.copy(children = newChildren)
        }
        val newChildren = node.children.toMutableList()
        newChildren[childIndex] = removeNodeRecursive(newChildren[childIndex], segments, index + 1)
        return node.copy(children = newChildren)
    }

    /**
     * 移动节点：先移除再添加
     */
    private fun applyMoveNode(root: ComposeNode, mutation: NodeMutation.MoveNode): ComposeNode {
        val sourceSegments = parsePath(mutation.sourcePath)
        require(sourceSegments.size > 1) { "不能移动根节点" }

        val removedResult = removeNodeAndGetIt(root, sourceSegments, 0)
        val afterRemove = removedResult.first
        val movedNode = removedResult.second

        val targetSegments = parsePath(mutation.targetParentPath)
        return addChildRecursive(afterRemove, targetSegments, 0, movedNode, mutation.insertIndex)
    }

    /**
     * 删除节点并返回被删除的节点和新树
     */
    private fun removeNodeAndGetIt(
        node: ComposeNode,
        segments: List<Int>,
        index: Int
    ): Pair<ComposeNode, ComposeNode> {
        val childIndex = segments[index + 1]
        require(childIndex >= 0 && childIndex < node.children.size) {
            "子节点索引越界: $childIndex, 子节点数: ${node.children.size}"
        }
        if (index == segments.size - 2) {
            val newChildren = node.children.toMutableList()
            val removed = newChildren.removeAt(childIndex)
            return Pair(node.copy(children = newChildren), removed)
        }
        val newChildren = node.children.toMutableList()
        val result = removeNodeAndGetIt(newChildren[childIndex], segments, index + 1)
        newChildren[childIndex] = result.first
        return Pair(node.copy(children = newChildren), result.second)
    }

    /**
     * 复制节点
     */
    private fun applyDuplicate(root: ComposeNode, mutation: NodeMutation.Duplicate): ComposeNode {
        val segments = parsePath(mutation.nodePath)
        require(segments.size > 1) { "不能复制根节点" }
        return duplicateRecursive(root, segments, 0)
    }

    /**
     * 递归复制节点
     */
    private fun duplicateRecursive(
        node: ComposeNode,
        segments: List<Int>,
        index: Int
    ): ComposeNode {
        val childIndex = segments[index + 1]
        require(childIndex >= 0 && childIndex < node.children.size) {
            "子节点索引越界: $childIndex, 子节点数: ${node.children.size}"
        }
        if (index == segments.size - 2) {
            val newChildren = node.children.toMutableList()
            val sourceNode = newChildren[childIndex]
            val copiedNode = deepCopy(sourceNode)
            newChildren.add(childIndex + 1, copiedNode)
            return node.copy(children = newChildren)
        }
        val newChildren = node.children.toMutableList()
        newChildren[childIndex] = duplicateRecursive(newChildren[childIndex], segments, index + 1)
        return node.copy(children = newChildren)
    }

    /**
     * 调整同级节点顺序
     */
    private fun applyReorderSibling(root: ComposeNode, mutation: NodeMutation.ReorderSibling): ComposeNode {
        val segments = parsePath(mutation.nodePath)
        require(segments.size > 1) { "不能移动根节点" }
        return reorderRecursive(root, segments, 0, mutation.delta)
    }

    /**
     * 递归调整节点顺序
     */
    private fun reorderRecursive(
        node: ComposeNode,
        segments: List<Int>,
        index: Int,
        delta: Int
    ): ComposeNode {
        val childIndex = segments[index + 1]
        require(childIndex >= 0 && childIndex < node.children.size) {
            "子节点索引越界: $childIndex, 子节点数: ${node.children.size}"
        }
        if (index == segments.size - 2) {
            val currentIndex = childIndex
            val newIndex = (currentIndex + delta).coerceIn(0, node.children.size - 1)
            if (newIndex == currentIndex) {
                return node
            }
            val newChildren = node.children.toMutableList()
            val movedNode = newChildren.removeAt(currentIndex)
            newChildren.add(newIndex, movedNode)
            return node.copy(children = newChildren)
        }
        val newChildren = node.children.toMutableList()
        newChildren[childIndex] = reorderRecursive(newChildren[childIndex], segments, index + 1, delta)
        return node.copy(children = newChildren)
    }

    /**
     * 解析节点路径为索引段列表
     * 路径格式："0.2.1" -> [0, 2, 1]
     */
    private fun parsePath(path: String): List<Int> {
        require(path.isNotBlank()) { "路径不能为空" }
        return path.split(".").map { it.toIntOrNull() ?: throw IllegalArgumentException("无效的路径段: $it") }
    }
}
