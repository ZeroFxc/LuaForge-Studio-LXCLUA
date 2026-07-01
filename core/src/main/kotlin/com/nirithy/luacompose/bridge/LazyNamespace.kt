package com.nirithy.luacompose.bridge

import com.nirithy.luacompose.*
import com.luajava.JavaFunction
import com.luajava.LuaState
import com.luajava.LuaObject
import com.nirithy.luacompose.node.ComposeNode

/**
 * 延迟命名空间 — 核心机制
 *
 * 让 Lua 可以动态访问任意 androidx.compose.* 类，无需预注册。
 *
 * 原理：
 *   1. compose 是一个 Lua 表，设置了 __index 元方法
 *   2. Lua 访问 compose.material3 时，__index 被触发，创建子命名空间
 *   3. Lua 访问 compose.material3.Button 时，尝试 Class.forName 加载类
 *   4. 加载成功后创建 ComposeFunction 包装器，Lua 调用时创建 ComposeNode
 *
 * 类名解析规则：
 *   compose.material3.Button → androidx.compose.material3.ButtonKt
 *   compose.foundation.layout.Column → androidx.compose.foundation.layout.ColumnKt
 *   compose.ui.graphics.Color → androidx.compose.ui.graphics.Color (无 Kt 后缀)
 */
object LazyNamespace {
    private const val TAG = "LazyNamespace"
    private val classCache = mutableMapOf<String, Class<*>?>()

    /**
     * 创建延迟命名空间表
     *
     * @param L LuaState
     * @param prefix 类名前缀，如 "androidx.compose"
     * @return 栈上压入一个表（调用者负责 setField 或 setGlobal）
     */
    fun create(L: LuaState, prefix: String) {
        classCache.clear() // 清理上次活动的类缓存，确保重新解析
        val topBefore = L.getTop()
        logI(TAG) { "[create] prefix=$prefix, 初始栈顶=$topBefore" }

        L.newTable()
        val tableIdx = L.getTop()
        logI(TAG) { "[create] 创建命名空间表, idx=$tableIdx, 栈顶=${L.getTop()}" }

        // 创建 __index 元表
        L.newTable()
        val metaIdx = L.getTop()
        logI(TAG) { "[create] 创建元表, idx=$metaIdx, 栈顶=${L.getTop()}" }

        // 用 Lua 闭包包装 JavaFunction，因为 Lua 的 __index 必须是 function 或 table 类型
        // JavaFunction 是 userdata 类型，直接用作 __index 会导致 "attempt to index a userdata value"
        val indexHandler = NamespaceIndexHandler(L, prefix)
        pushLuaIndexWrapper(L, indexHandler)
        logI(TAG) { "[create] pushLuaIndexWrapper 后, 栈顶=${L.getTop()}" }

        L.setField(-2, "__index")
        logI(TAG) { "[create] setField __index 后, 栈顶=${L.getTop()}" }

        // 验证元表内容
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

        // 验证 metatable 是否真的设置上了
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
        // 尝试顺序：原路径 → 加 Kt 后缀 → 加 Composable 后缀
        val candidates = listOf(
            fullPath,
            "${fullPath}Kt",
            "${fullPath}Composable",
        )
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
     *
     * Lua 的 __index 元方法必须是 function 或 table 类型。
     * JavaFunction 是 userdata 类型，被 Lua 当作 table 索引时会触发
     * "attempt to index a userdata value" 错误。
     * 因此需要包装一层 Lua 闭包：function(t, k) return handler(t, k) end
     *
     * @param L LuaState
     * @param handler Java 端的 __index 处理器
     */
    internal fun pushLuaIndexWrapper(L: LuaState, handler: JavaFunction) {
        // 将 handler 暂存到全局，供 Lua 闭包捕获
        val tempGlobal = "__lazy_ns_handler_${handler.hashCode().toUInt()}"
        L.pushJavaFunction(handler)
        L.setGlobal(tempGlobal)

        // 创建 Lua 闭包：捕获 handler 后清理全局，返回包装函数
        val code = """
            local handler = $tempGlobal
            _G["$tempGlobal"] = nil
            return function(t, k)
                return handler(t, k)
            end
        """.trimIndent()

        if (L.LloadString(code) == 0) {
            L.pcall(0, 1, 0)  // 执行闭包，返回的包装函数留在栈顶
        } else {
            val err = L.toString(-1)
            logE(TAG) { "[pushLuaIndexWrapper] 创建包装函数失败: $err" }
            L.pop(1)
            L.pushNil()
        }
    }

    /**
     * __index 处理器
     * 当 Lua 访问命名空间中不存在的字段时触发
     */
    private class NamespaceIndexHandler(
        private val luaState: LuaState,
        private val prefix: String
    ) : JavaFunction(luaState) {
        override fun execute(): Int {
            val topBefore = luaState.getTop()
            // 栈: [handler, table, key] (Lua 闭包调用 handler(t,k) 时，JavaFunction 的 __call 会把函数自身也压栈)
            val key = try {
                luaState.toString(3)
            } catch (e: Exception) {
                logW(TAG) { "[__index] 无法读取 key: ${e.message}" }
                luaState.pushNil(); return 1
            }

            val fullPath = if (prefix.isEmpty()) key else "$prefix.$key"
            logI(TAG) { "[__index] prefix=$prefix, key=$key, fullPath=$fullPath, 栈顶=$topBefore" }

            // 首字母大写 → 尝试解析为类
            if (key.isNotEmpty() && key[0].isUpperCase()) {
                val clazz = resolveClass(fullPath)
                if (clazz != null) {
                    // 先尝试作为 Kotlin object（INSTANCE 字段），如 CardDefaults、Typography
                    try {
                        val instance = clazz.getDeclaredField("INSTANCE").get(null)
                        luaState.pushJavaObject(instance)
                        logI(TAG) { "[__index] $key → KotlinObject, 栈顶=${luaState.getTop()}" }
                        return 1
                    } catch (_: NoSuchFieldException) {
                        // 不是 Kotlin object，作为 Composable 函数类
                        luaState.pushJavaFunction(ComposeFunction(luaState, fullPath, key))
                        logI(TAG) { "[__index] $key → ComposeFunction, 返回栈顶=${luaState.getTop()}" }
                        return 1
                    }
                }
                logD(TAG) { "[__index] $key 大写但未解析到类, 回退创建子命名空间" }
            }

            // 未解析为类 → 创建子命名空间
            logI(TAG) { "[__index] 创建子命名空间: $fullPath" }
            createSubNamespace(key, fullPath)
            logI(TAG) { "[__index] 返回, 栈顶=${luaState.getTop()}" }
            return 1
        }

        /**
         * 内联创建子命名空间，避免 create() 的栈操作不确定性
         *
         * 入栈: [handler, table, key] (Lua 闭包调用 handler(t,k) 时 handler 自身也在栈中)
         * 出栈: [handler, table, key, subTable] (返回 subTable)
         */
        private fun createSubNamespace(key: String, fullPath: String) {
            val topBefore = luaState.getTop()
            logI(TAG) { "[createSub] $fullPath 开始, 栈顶=$topBefore, 栈内容: table(idx=2)='?', key(idx=3)='$key'" }

            // 创建子命名空间表
            luaState.newTable()
            val subTableIdx = luaState.getTop()
            logI(TAG) { "[createSub] 创建子表, idx=$subTableIdx, 栈顶=${luaState.getTop()}" }

            // 创建元表
            luaState.newTable()
            val metaIdx = luaState.getTop()
            logI(TAG) { "[createSub] 创建元表, idx=$metaIdx, 栈顶=${luaState.getTop()}" }

            // 用 Lua 闭包包装 JavaFunction，避免 userdata 作为 __index 被 Lua 当作 table 索引
            val subHandler = NamespaceIndexHandler(luaState, fullPath)
            pushLuaIndexWrapper(luaState, subHandler)
            logI(TAG) { "[createSub] pushLuaIndexWrapper, 栈顶=${luaState.getTop()}" }

            luaState.setField(-2, "__index")
            logI(TAG) { "[createSub] setField __index, 栈顶=${luaState.getTop()}" }

            val beforeSet = luaState.getTop()
            val result = luaState.setMetaTable(subTableIdx)
            val afterSet = luaState.getTop()
            logI(TAG) { "[createSub] setMetaTable(idx=$subTableIdx) 返回=$result, 栈顶 $beforeSet → $afterSet" }

            if (afterSet == beforeSet) {
                logW(TAG) { "[createSub] setMetaTable 未弹出元表(ret=0)！手动清理" }
                luaState.pop(1)
            }

            // 验证 metatable 是否设置成功
            val verifyResult = luaState.getMetaTable(subTableIdx)
            val verifyType = luaState.type(-1)
            logI(TAG) { "[createSub] getMetaTable(idx=$subTableIdx) 返回=$verifyResult, type=${luaState.typeName(verifyType)}" }
            luaState.pop(1)
            if (verifyResult == 0) {
                logE(TAG) { "[createSub] 元表设置失败！subTableIdx=$subTableIdx 没有 metatable！" }
            }

            // 缓存到父表，避免重复 __index 调用
            // 当前栈: [handler, table, key, subTable]，table 在位置 2
            luaState.pushValue(-1)  // 复制 subTable: [handler, table, key, subTable, subTable_copy]
            luaState.setField(2, key)  // table[key] = subTable_copy
            logI(TAG) { "[createSub] $fullPath 完成, 缓存到父表, 最终栈顶=${luaState.getTop()}" }
        }
    }

    /**
     * Compose 函数包装器
     * 当 Lua 调用 compose.material3.Button { ... } 时，创建 ComposeNode
     */
    class ComposeFunction(
        private val luaState: LuaState,
        private val classPath: String,
        private val simpleName: String
    ) : JavaFunction(luaState) {
        override fun execute(): Int {
            val top = luaState.getTop()
            logD(TAG) { "[ComposeFunction] $simpleName 被调用, top=$top, classPath=$classPath" }

            if (top < 2 || !luaState.isTable(2)) {
                // 无参数 → 创建空节点
                luaState.pushJavaObject(ComposeNode(type = simpleName, props = mapOf("_classPath" to classPath)))
                return 1
            }

            // 解析 Lua 表 → ComposeNode，type 用简单名，_classPath 存完整类路径
            val node = NodeParser.parseNodeTable(luaState, 2, simpleName, classPath)
            // 附加 classPath 到 props 中供动态渲染器使用
            val nodeWithPath = node.copy(props = node.props + ("_classPath" to classPath))
            luaState.pushJavaObject(nodeWithPath)
            return 1
        }
    }
}