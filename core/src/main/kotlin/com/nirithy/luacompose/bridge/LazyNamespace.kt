package com.nirithy.luacompose.bridge

import com.nirithy.luacompose.*
import com.luajava.JavaFunction
import com.luajava.LuaState
import com.nirithy.luacompose.node.ComposeNode

/**
 * 延迟命名空间 — 全动态组件解析
 *
 * 让 Lua 可以动态访问任意 androidx.compose.* 类和组件，无需预注册，无需硬编码列表。
 *
 * 解析策略（三层）：
 *   1. Class.forName 直接解析 → 成功则返回 KotlinObject 或 ComposeFunction
 *   2. 跨包搜索（仅根命名空间）→ 遍历已缓存的动态子命名空间
 *   3. 全部失败 → 大写 key 创建 ComposeFunction（空 classPath），由 ComponentRegistry 按 type 匹配渲染器
 *                 小写 key 创建子命名空间
 *
 * 为什么不需要别名和静态子包列表：
 *   - ComponentRegistry 已通过 Plugin 注册了所有组件的渲染器
 *   - ComposeFunction 创建 ComposeNode(type=key)，ComponentRegistry.render() 按 type 直接命中
 *   - 完整路径（compose.material3.Button）走子命名空间 + Class.forName 正常解析
 *   - 缩略写法（compose.Button）走 ComposeFunction("", "Button")，ComponentRegistry 命中
 */
object LazyNamespace {
    private const val TAG = "LazyNamespace"
    private val classCache = mutableMapOf<String, Class<*>?>()

    /**
     * 创建延迟命名空间表
     */
    fun create(L: LuaState, prefix: String) {
        classCache.clear()
        val topBefore = L.getTop()
        logI(TAG) { "[create] prefix=$prefix, 初始栈顶=$topBefore" }

        L.newTable()
        val tableIdx = L.getTop()
        logI(TAG) { "[create] 创建命名空间表, idx=$tableIdx, 栈顶=${L.getTop()}" }

        L.newTable()
        val metaIdx = L.getTop()
        logI(TAG) { "[create] 创建元表, idx=$metaIdx, 栈顶=${L.getTop()}" }

        val indexHandler = NamespaceIndexHandler(L, prefix, /* isRoot */ prefix == "androidx.compose")
        pushLuaIndexWrapper(L, indexHandler)
        logI(TAG) { "[create] pushLuaIndexWrapper 后, 栈顶=${L.getTop()}" }

        L.setField(-2, "__index")
        logI(TAG) { "[create] setField __index 后, 栈顶=${L.getTop()}" }

        L.pushString("__index")
        L.getTable(metaIdx)
        val hasIndex = L.type(-1) != LuaState.LUA_TNIL
        logI(TAG) { "[create] 元表 __index 存在=$hasIndex, type=${L.typeName(L.type(-1))}" }
        L.pop(1)

        val beforeSet = L.getTop()
        val result = L.setMetaTable(tableIdx)
        val afterSet = L.getTop()
        logI(TAG) { "[create] setMetaTable(idx=$tableIdx) 返回=$result, 栈顶 $beforeSet → $afterSet" }

        if (afterSet == beforeSet) {
            logW(TAG) { "[create] setMetaTable 未弹出元表(ret=0)！手动清理" }
            L.pop(1)
        }

        val verifyResult = L.getMetaTable(tableIdx)
        val verifyType = L.type(-1)
        logI(TAG) { "[create] getMetaTable(idx=$tableIdx) 返回=$verifyResult, type=${L.typeName(verifyType)}" }
        L.pop(1)

        logI(TAG) { "[create] 完成, 最终栈顶=${L.getTop()}" }
    }

    /**
     * 尝试解析类名，返回 Compose 可组合函数类（如 ButtonKt）
     */
    private fun resolveClass(fullPath: String): Class<*>? {
        classCache[fullPath]?.let { return it }
        val candidates = listOf(fullPath, "${fullPath}Kt", "${fullPath}Composable")
        for (candidate in candidates) {
            try {
                val clazz = Class.forName(candidate)
                classCache[fullPath] = clazz
                logD(TAG) { "[resolve] $fullPath → $candidate ✓" }
                return clazz
            } catch (_: Exception) {}
        }
        classCache[fullPath] = null
        logV(TAG) { "[resolve] $fullPath → 未找到" }
        return null
    }

    /**
     * 将 JavaFunction 处理器包装为 Lua 闭包函数并压入栈顶
     */
    internal fun pushLuaIndexWrapper(L: LuaState, handler: JavaFunction) {
        val tempGlobal = "__lazy_ns_handler_${handler.hashCode().toUInt()}"
        L.pushJavaFunction(handler)
        L.setGlobal(tempGlobal)

        val code = """
            local handler = $tempGlobal
            _G["$tempGlobal"] = nil
            return function(t, k)
                return handler(t, k)
            end
        """.trimIndent()

        if (L.LloadString(code) == 0) {
            L.pcall(0, 1, 0)
        } else {
            val err = L.toString(-1)
            logE(TAG) { "[pushLuaIndexWrapper] 创建包装函数失败: $err" }
            L.pop(1)
            L.pushNil()
        }
    }

    /**
     * 将 ComposeFunction 压入栈顶并缓存到父表
     */
    private fun pushAndCacheComponent(L: LuaState, simpleName: String, classPath: String) {
        L.pushJavaFunction(ComposeFunction(L, classPath, simpleName))
        L.pushValue(-1) // 复制结果
        L.setField(2, simpleName) // 缓存到父表: table[key] = result
    }

    // ==================== __index 处理器 ====================

    private class NamespaceIndexHandler(
        private val L: LuaState,
        private val prefix: String,
        private val isRoot: Boolean
    ) : JavaFunction(L) {
        override fun execute(): Int {
            // 优先检查表中是否已存在该 key（如 Modifier 等通过 setField 注入的值）
            // rawget 不会触发元方法，避免递归
            L.pushValue(3)  // 推入 key 副本
            L.rawGet(2)     // rawget(table, key) → 弹出 key 副本，推入值
            if (L.type(-1) != LuaState.LUA_TNIL) {
                // 表中已存在该 key，直接返回值
                // Lua VM 会自动清理栈上的 self/table/key 参数，只取栈顶的 value 作为返回值
                logI(TAG) { "[__index] rawget 命中 key, type=${L.typeName(L.type(-1))}" }
                return 1
            }
            L.pop(1)  // 弹出 nil

            val key = try {
                L.toString(3)
            } catch (e: Exception) {
                logW(TAG) { "[__index] 无法读取 key: ${e.message}" }
                L.pushNil(); return 1
            }

            val fullPath = if (prefix.isEmpty()) key else "$prefix.$key"
            logV(TAG) { "[__index] prefix=$prefix, key=$key, fullPath=$fullPath" }

            if (key.isNotEmpty() && key[0].isUpperCase()) {
                // 大写 key → 尝试解析为类
                logD(TAG) { "[__index] 大写 key='$key', 尝试解析为类, prefix=$prefix" }
                val clazz = resolveClass(fullPath)
                if (clazz != null) {
                    return pushClassResult(clazz, key, fullPath)
                }

                // 仅在根命名空间跨包搜索（用于缩略写法如 compose.Button）
                if (isRoot) {
                    val found = searchSubNamespaces(key)
                    if (found > 0) return found
                }

                // 大写 key 解析失败 → 创建 ComposeFunction（空 classPath）
                // ComponentRegistry 会按 type=key 匹配渲染器
                logD(TAG) { "[__index] $key → ComposeFunction(空classPath)，由 ComponentRegistry 渲染" }
                pushAndCacheComponent(L, key, "")
                return 1
            }

            // 小写 key → 创建子命名空间
            logV(TAG) { "[__index] 创建子命名空间: $fullPath" }
            createSubNamespace(key, fullPath)
            return 1
        }

        private fun pushClassResult(clazz: Class<*>, key: String, classPath: String): Int {
            try {
                val instance = clazz.getDeclaredField("INSTANCE").get(null)
                L.pushJavaObject(instance)
                logV(TAG) { "[__index] $key → KotlinObject" }
                return 1
            } catch (_: NoSuchFieldException) {
                L.pushJavaFunction(ComposeFunction(L, classPath, key))
                logV(TAG) { "[__index] $key → ComposeFunction" }
                return 1
            }
        }

        /**
         * 跨包搜索：遍历根表中已动态缓存的子命名空间
         */
        private fun searchSubNamespaces(key: String): Int {
            val parentTableIdx = 2
            L.pushNil()
            while (L.next(parentTableIdx) != 0) {
                if (L.isTable(-1)) {
                    val hasMeta = L.getMetaTable(-1)
                    if (hasMeta != 0) {
                        L.pushString("__index")
                        L.getTable(-2)
                        val isNs = L.isFunction(-1) || L.isTable(-1)
                        L.pop(2)
                        if (isNs) {
                            try {
                                val subKey = L.toString(-2)
                                val subFullPath = if (prefix.isEmpty()) subKey else "$prefix.$subKey"
                                val clazz = resolveClass("$subFullPath.$key")
                                if (clazz != null) {
                                    L.pop(2) // 弹出 subTable, subKey
                                    L.pop(1) // 清理 pushNil 留下的 nil
                                    cacheAndPushResult(key, clazz, "$subFullPath.$key")
                                    return 1
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
                L.pop(1)
            }
            L.pop(1) // 清理 pushNil 的 nil
            return 0
        }

        private fun cacheAndPushResult(key: String, clazz: Class<*>, classPath: String) {
            logI(TAG) { "[__index] $key → 跨包搜索命中 $classPath，缓存到父表" }
            try {
                val instance = clazz.getDeclaredField("INSTANCE").get(null)
                L.pushJavaObject(instance)
            } catch (_: NoSuchFieldException) {
                L.pushJavaFunction(ComposeFunction(L, classPath, key))
            }
            L.pushValue(-1)
            L.setField(2, key)
        }

        private fun createSubNamespace(key: String, fullPath: String) {
            val topBefore = L.getTop()
            logV(TAG) { "[createSub] $fullPath 开始, 栈顶=$topBefore" }

            L.newTable()
            val subTableIdx = L.getTop()
            L.newTable()
            val metaIdx = L.getTop()

            val subHandler = NamespaceIndexHandler(L, fullPath, isRoot = false)
            pushLuaIndexWrapper(L, subHandler)
            L.setField(-2, "__index")

            val beforeSet = L.getTop()
            val result = L.setMetaTable(subTableIdx)
            val afterSet = L.getTop()
            if (afterSet == beforeSet) {
                logW(TAG) { "[createSub] setMetaTable 未弹出元表(ret=0)！手动清理" }
                L.pop(1)
            }

            val verifyResult = L.getMetaTable(subTableIdx)
            logV(TAG) { "[createSub] getMetaTable(idx=$subTableIdx) 返回=$verifyResult" }
            L.pop(1)
            if (verifyResult == 0) {
                logE(TAG) { "[createSub] 元表设置失败！" }
            }

            L.pushValue(-1)
            L.setField(2, key)
            logV(TAG) { "[createSub] $fullPath 完成, 缓存到父表, 最终栈顶=${L.getTop()}" }
        }
    }

    // ==================== ComposeFunction ====================

    /**
     * Compose 组件工厂函数
     * 当 Lua 调用 compose.Button { ... } 时，创建 ComposeNode
     */
    class ComposeFunction(
        private val L: LuaState,
        private val classPath: String,
        private val simpleName: String
    ) : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            logV(TAG) { "[ComposeFunction] $simpleName 被调用, classPath=$classPath" }

            if (top < 2 || !L.isTable(2)) {
                L.pushJavaObject(ComposeNode(type = simpleName, props = mapOf("_classPath" to classPath)))
                return 1
            }

            val node = NodeParser.parseNodeTable(L, 2, simpleName, classPath)
            val nodeWithPath = node.copy(props = node.props + ("_classPath" to classPath))
            L.pushJavaObject(nodeWithPath)
            return 1
        }
    }
}