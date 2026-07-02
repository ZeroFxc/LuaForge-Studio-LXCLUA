package com.nirithy.luacompose.bridge

import com.nirithy.luacompose.*
import com.nirithy.luacompose.modifier.ModifierChain
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.reflect.LuaConverter
import com.luajava.LuaObject
import com.luajava.LuaState

/**
 * Lua 表 → ComposeNode 解析器
 * 从 ComposeBridge.kt 中提取，保持解析逻辑独立。
 *
 * 供 ComposeBridge.registerComponentFactory 和 LazyNamespace 使用。
 */
object NodeParser {
    private const val TAG = "ComposeBridge"

    /**
     * 从 Lua 表解析 ComposeNode
     * @param nodeType 组件类型名（如 "Text", "Button"）
     * @param fullPath 完整类路径（如 "androidx.compose.material3.ButtonKt"，仅日志用）
     */
    fun parseNodeTable(L: LuaState, tableIdx: Int, nodeType: String, fullPath: String = nodeType): ComposeNode {
        val props = mutableMapOf<String, Any?>()
        val callbacks = mutableMapOf<String, LuaObject>()
        val children = mutableListOf<ComposeNode>()
        var childrenFunc: LuaObject? = null

        L.pushNil()
        while (L.next(tableIdx) != 0) {
            val key: String = try {
                L.toString(-2)
            } catch (e: Exception) {
                logW(TAG) { "[parseNode:$nodeType] 无法读取 key: ${e.message}" }
                L.pop(1); continue
            }
            val valueType = L.type(-1)
            when {
                // children 为表：静态子节点列表
                key == "children" && valueType == LuaState.LUA_TTABLE -> {
                    val parsed = parseChildrenArray(L, -1)
                    logV(TAG) { "[parseNode:$nodeType] children 解析: ${parsed.size} 个子节点" }
                    children.addAll(parsed)
                }
                // children 为函数：动态子节点（Crossfade/AnimatedContent 等场景）
                key == "children" && valueType == LuaState.LUA_TFUNCTION -> {
                    childrenFunc = L.getLuaObject(-1)
                    logV(TAG) { "[parseNode:$nodeType] children 是函数，存储为 childrenFunc（动态子节点）" }
                }
                valueType == LuaState.LUA_TFUNCTION -> {
                    callbacks[key] = L.getLuaObject(-1)
                    logV(TAG) { "[parseNode:$nodeType] 回调: $key = LuaFunction" }
                }
                else -> {
                    try {
                        val value = LuaConverter.scriptToJava(L, -1)
                        props[key] = value
                        val valueStr = when (value) {
                            is ModifierChain -> "ModifierChain(hash=${value.hashCode()})"
                            is String -> "\"${value.take(30)}${if ((value as String).length > 30) "..." else ""}\""
                            else -> value?.toString()?.take(50) ?: "null"
                        }
                        logV(TAG) { "[parseNode:$nodeType] 属性: $key = $valueStr" }
                    } catch (e: Exception) {
                        logW(TAG) { "[parseNode:$nodeType] 无法解析属性 $key: ${e.message}, type=$valueType" }
                    }
                }
            }
            L.pop(1)
        }
        return ComposeNode(type = nodeType, props = props, children = children, callbacks = callbacks, childrenFunc = childrenFunc)
    }

    /**
     * 解析 children 数组（Lua 表），支持数组模式 {child1, child2} 和字典模式
     */
    fun parseChildrenArray(L: LuaState, tableIdx: Int): List<ComposeNode> {
        val children = mutableListOf<ComposeNode>()
        // 转为绝对索引，避免后续 push/pop 改变栈顶导致相对索引失效
        val absTableIdx = if (tableIdx > 0) tableIdx else L.getTop() + tableIdx + 1
        val len = L.rawLen(absTableIdx)
        if (len > 0) {
            logV(TAG) { "[parseChildren] 数组模式, 长度=$len, absIdx=$absTableIdx" }
            for (i in 1..len) {
                L.pushInteger(i.toLong()); L.getTable(absTableIdx)
                parseChildNode(L, -1)?.let { children.add(it) }
                L.pop(1)
            }
        } else {
            logV(TAG) { "[parseChildren] 字典模式遍历, absIdx=$absTableIdx" }
            L.pushNil()
            while (L.next(absTableIdx) != 0) {
                parseChildNode(L, -1)?.let { children.add(it) }
                L.pop(1)
            }
        }
        return children
    }

    /**
     * 解析单个子节点（可能是 ComposeNode 对象、Lua 表或 raw table）
     * raw table 如 {type = "DropdownMenuItem", text = "选项", onClick = fn}
     * 通过 toJavaObject 尝试获取 ComposeNode，失败则作为表解析
     */
    private fun parseChildNode(L: LuaState, idx: Int): ComposeNode? {
        val absIdx = if (idx > 0) idx else L.getTop() + idx + 1
        // 先尝试 as ComposeNode（Kotlin 侧已创建的节点）
        try {
            val obj = L.toJavaObject(absIdx)
            if (obj is ComposeNode) {
                logV(TAG) { "[parseChild] 已解析的子节点: type=${obj.type}" }
                return obj
            }
        } catch (_: Exception) {
            // toJavaObject 失败，继续尝试表解析
        }
        // toJavaObject 返回 null 或非 ComposeNode → 尝试作为表解析
        if (L.isTable(absIdx)) {
            return try {
                L.pushString("type"); L.getTable(absIdx)
                val typeName = if (L.isString(-1)) L.toString(-1) else "Unknown"
                L.pop(1)
                logD(TAG) { "[parseChild] 从表解析子节点: type=$typeName" }
                parseNodeTable(L, absIdx, typeName)
            } catch (e: Exception) {
                logW(TAG) { "[parseChild] 解析子节点失败: ${e.message}" }
                null
            }
        }
        logW(TAG) { "[parseChild] 非表非 ComposeNode，跳过" }
        return null
    }
}