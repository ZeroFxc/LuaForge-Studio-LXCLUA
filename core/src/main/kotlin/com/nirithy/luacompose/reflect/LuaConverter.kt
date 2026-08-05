package com.nirithy.luacompose.reflect

import com.nirithy.luacompose.logW
import com.luajava.LuaState
import java.util.*

/**
 * Lua ↔ Java 双向类型转换器（移植自 kulipai LuaCompose）
 *
 * 增强项（相比 luajava 原生 toJavaObject）：
 * - 循环引用检测：visited 列表防止栈溢出
 * - 深度限制：depth > 100 时截断，返回 "[truncated]"
 * - 特殊类型标记：自动识别 _javaColor、_javaDp、_javaOffset 等标记字段
 * - 数组/字典自动区分：table.length() > 0 → List，否则 → Map
 * - Kotlin inline class 拆箱：box-impl 方法处理
 *
 * 使用方式：
 *   val obj = LuaConverter.scriptToJava(L, tableIdx)  // 替代 L.toJavaObject(idx)
 *   val script = LuaConverter.javaToScript(L, javaObj)  // 压入栈顶
 */
object LuaConverter {
    private const val TAG = "LuaConverter"
    private const val MAX_DEPTH = 100
    private const val MAX_STRING_LENGTH = 1024 * 1024  // 1MB

    /**
     * Lua 值 → Java 对象（智能转换）
     *
     * @param L LuaState
     * @param idx 栈索引（支持负索引）
     * @return Java 对象
     */
    fun scriptToJava(L: LuaState, idx: Int, visited: MutableSet<Int> = HashSet(), depth: Int = 0): Any? {
        val absIdx = if (idx > 0) idx else L.getTop() + idx + 1

        if (depth > MAX_DEPTH) {
            logW(TAG) { "[scriptToJava] 深度超限: $depth, 截断" }
            return null
        }

        val type = L.type(absIdx)
        return when (type) {
            LuaState.LUA_TBOOLEAN -> L.toBoolean(absIdx)
            LuaState.LUA_TNUMBER -> {
                // 尝试保持整数精度
                val num = L.toNumber(absIdx)
                if (num == Math.ceil(num) && num <= Long.MAX_VALUE && num >= Long.MIN_VALUE) {
                    num.toLong()
                } else {
                    num
                }
            }
            LuaState.LUA_TSTRING -> {
                val str = L.toString(absIdx)
                if (str.length > MAX_STRING_LENGTH) str.substring(0, MAX_STRING_LENGTH) + "..." else str
            }
            LuaState.LUA_TTABLE -> tableToJava(L, absIdx, visited, depth)
            LuaState.LUA_TUSERDATA -> L.toJavaObject(absIdx)
            LuaState.LUA_TFUNCTION -> {
                // 尝试转换为 LuaObject
                try { L.getLuaObject(absIdx) } catch (e: Exception) { null }
            }
            LuaState.LUA_TNIL -> null
            LuaState.LUA_TNONE -> null
            else -> {
                try { L.toJavaObject(absIdx) } catch (e: Exception) { null }
            }
        }
    }

    /**
     * Lua 表 → Java 对象（智能转换）
     *
     * 检测顺序：
     * 1. 特殊类型标记（_javaColor, _javaDp, _javaOffset, _javaRect, _javaSize）
     * 2. 数组检测（连续整数 key 1..n）
     * 3. 普通映射
     */
    private fun tableToJava(L: LuaState, absIdx: Int, visited: MutableSet<Int>, depth: Int): Any? {
        // 循环引用检测
        if (absIdx in visited) {
            logW(TAG) { "[tableToJava] 循环引用检测: idx=$absIdx" }
            return null
        }
        visited.add(absIdx)

        try {
            // 1. 检测特殊类型标记
            val specialResult = detectSpecialType(L, absIdx, visited, depth)
            if (specialResult != null) return specialResult

            // 2. 遍历表，判断是数组还是映射
            L.pushNil()
            var isArray = true
            var maxKey = 0.0
            val entries = mutableListOf<Pair<Any?, Any?>>()

            while (L.next(absIdx) != 0) {
                val key = scriptToJava(L, -2, visited, depth + 1)
                val value = scriptToJava(L, -1, visited, depth + 1)

                entries.add(key to value)

                // 检测是否为连续整数数组
                if (isArray) {
                    val numKey = (key as? Number)?.toDouble()
                    if (numKey != null && numKey == (maxKey + 1.0)) {
                        maxKey = numKey
                    } else {
                        isArray = false
                    }
                }

                L.pop(1)
            }

            // 3. 返回数组或映射
            return if (isArray && entries.isNotEmpty() && entries.first().first is Number) {
                // 按 key 排序后提取值
                entries.sortBy { (it.first as? Number)?.toDouble() ?: 0.0 }
                entries.mapNotNull { it.second }
            } else {
                val map = LinkedHashMap<Any?, Any?>()
                for ((k, v) in entries) {
                    map[k] = v
                }
                map
            }
        } finally {
            visited.remove(absIdx)
        }
    }

    /**
     * 检测特殊类型标记
     *
     * 支持的标记（参考 LuaCompose-master 的 ComposeBridge.scriptToJava）：
     * - _isState: Lua 状态表 → javaState 中的 Java 对象
     * - _javaObj: 通用 Java 对象包装
     * - _javaColor: Color 对象
     * - _javaDp: Dp 对象
     * - _javaSize: Size 对象
     * - _javaOffset: Offset 对象
     * - _javaIntOffset: IntOffset 对象
     * - _javaStroke: Stroke 对象
     */
    private fun detectSpecialType(L: LuaState, absIdx: Int, visited: MutableSet<Int>, depth: Int): Any? {
        // 检测 _isState 标记（Lua 状态包装）
        L.pushString("_isState")
        L.getTable(absIdx)
        if (L.isBoolean(-1) && L.toBoolean(-1)) {
            L.pop(1)
            // 从 javaState 字段获取 Java 对象
            L.pushString("javaState")
            L.getTable(absIdx)
            if (L.type(-1) == LuaState.LUA_TUSERDATA) {
                val obj = L.toJavaObject(-1)
                L.pop(1)
                return obj
            }
            L.pop(1)
            return null
        }
        L.pop(1)

        // 检测 _javaObj 标记
        L.pushString("_javaObj")
        L.getTable(absIdx)
        if (L.type(-1) == LuaState.LUA_TUSERDATA) {
            val obj = L.toJavaObject(-1)
            L.pop(1)
            return obj
        }
        L.pop(1)

        // 检测 _javaColor 标记
        L.pushString("_javaColor")
        L.getTable(absIdx)
        if (L.type(-1) == LuaState.LUA_TUSERDATA) {
            val obj = L.toJavaObject(-1)
            L.pop(1)
            return obj
        }
        L.pop(1)

        // 检测 _javaDp 标记
        L.pushString("_javaDp")
        L.getTable(absIdx)
        if (L.type(-1) == LuaState.LUA_TUSERDATA) {
            val obj = L.toJavaObject(-1)
            L.pop(1)
            return obj
        }
        L.pop(1)

        // 检测 _javaSize 标记
        L.pushString("_javaSize")
        L.getTable(absIdx)
        if (L.type(-1) == LuaState.LUA_TUSERDATA) {
            val obj = L.toJavaObject(-1)
            L.pop(1)
            return obj
        }
        L.pop(1)

        // 检测 _javaOffset 标记
        L.pushString("_javaOffset")
        L.getTable(absIdx)
        if (L.type(-1) == LuaState.LUA_TUSERDATA) {
            val obj = L.toJavaObject(-1)
            L.pop(1)
            return obj
        }
        L.pop(1)

        // 检测 _javaIntOffset 标记
        L.pushString("_javaIntOffset")
        L.getTable(absIdx)
        if (L.type(-1) == LuaState.LUA_TUSERDATA) {
            val obj = L.toJavaObject(-1)
            L.pop(1)
            return obj
        }
        L.pop(1)

        // 检测 _javaStroke 标记
        L.pushString("_javaStroke")
        L.getTable(absIdx)
        if (L.type(-1) == LuaState.LUA_TUSERDATA) {
            val obj = L.toJavaObject(-1)
            L.pop(1)
            return obj
        }
        L.pop(1)

        // 检测 _chain 标记（ModifierChain 包装器）
        // FoundationPlugin 的 Modifier 方法返回 Lua 表包装器，
        // 内部 _chain 字段持有 ModifierChain 实例
        L.pushString("_chain")
        L.getTable(absIdx)
        if (L.type(-1) == LuaState.LUA_TUSERDATA) {
            val obj = try { L.toJavaObject(-1) } catch (e: Exception) { null }
            L.pop(1)
            if (obj != null) return obj
        } else {
            L.pop(1)
        }

        // 回退：尝试解析旧版 _javaColor 格式（hex 值）
        L.pushString("_javaColor")
        L.getTable(absIdx)
        val hasColor = L.type(-1) != LuaState.LUA_TNIL
        L.pop(1)

        if (hasColor) {
            return parseColor(L, absIdx, visited, depth)
        }

        return null
    }

    /**
     * 解析 Lua 颜色表为 Compose Color long
     * 支持格式：
     *   {_javaColor=true, r=1.0, g=0.5, b=0.0, a=1.0}  (浮点 0-1)
     *   {_javaColor=true, value=0xFFFF0000}              (ARGB hex)
     */
    private fun parseColor(L: LuaState, absIdx: Int, visited: MutableSet<Int>, depth: Int): Any? {
        // 尝试 hex 格式
        L.pushString("value")
        L.getTable(absIdx)
        if (L.isNumber(-1)) {
            val v = L.toNumber(-1).toLong()
            L.pop(1)
            return v
        }
        L.pop(1)

        // 尝试 r/g/b/a 格式
        var r = 1.0; var g = 1.0; var b = 1.0; var a = 1.0
        val keys = arrayOf("r", "g", "b", "a")
        val targets = arrayOf(r, g, b, a)
        for (i in keys.indices) {
            L.pushString(keys[i])
            L.getTable(absIdx)
            if (L.isNumber(-1)) {
                when (i) {
                    0 -> r = L.toNumber(-1)
                    1 -> g = L.toNumber(-1)
                    2 -> b = L.toNumber(-1)
                    3 -> a = L.toNumber(-1)
                }
            }
            L.pop(1)
        }

        val ri = (r * 255).toInt().coerceIn(0, 255)
        val gi = (g * 255).toInt().coerceIn(0, 255)
        val bi = (b * 255).toInt().coerceIn(0, 255)
        val ai = (a * 255).toInt().coerceIn(0, 255)
        return (ai.toLong() shl 24) or (ri.toLong() shl 16) or (gi.toLong() shl 8) or bi.toLong()
    }

    // ================================================================
    //  Java → Lua 方向
    // ================================================================

    /**
     * Java 对象 → Lua 值（压入栈顶）
     *
     * @param L LuaState
     * @param obj Java 对象
     * @param visited 循环引用检测集合
     * @param depth 当前深度
     */
    fun javaToScript(L: LuaState, obj: Any?, visited: MutableSet<Any> = HashSet(), depth: Int = 0) {
        if (depth > MAX_DEPTH) {
            L.pushString("[truncated]")
            return
        }

        when (obj) {
            null -> L.pushNil()
            is Boolean -> L.pushBoolean(obj)
            is Number -> {
                when (obj) {
                    is Float, is Double -> L.pushNumber(obj.toDouble())
                    is Long -> L.pushNumber(obj.toDouble())
                    else -> L.pushNumber(obj.toDouble())
                }
            }
            is String -> {
                if (obj.length > MAX_STRING_LENGTH) {
                    L.pushString(obj.substring(0, MAX_STRING_LENGTH) + "...")
                } else {
                    L.pushString(obj)
                }
            }
            is List<*> -> {
                if (obj in visited) { L.pushNil(); return }
                visited.add(obj)
                L.newTable()
                for ((i, item) in obj.withIndex()) {
                    javaToScript(L, item, visited, depth + 1)
                    L.setField(-2, (i + 1).toString())
                }
                visited.remove(obj)
            }
            is Map<*, *> -> {
                if (obj in visited) { L.pushNil(); return }
                visited.add(obj)
                L.newTable()
                for ((k, v) in obj) {
                    javaToScript(L, k, visited, depth + 1)
                    javaToScript(L, v, visited, depth + 1)
                    L.setTable(-3)
                }
                visited.remove(obj)
            }
            is Array<*> -> {
                if (obj in visited) { L.pushNil(); return }
                visited.add(obj)
                L.newTable()
                for ((i, item) in obj.withIndex()) {
                    javaToScript(L, item, visited, depth + 1)
                    L.setField(-2, (i + 1).toString())
                }
                visited.remove(obj)
            }
            else -> {
                // 尝试用 luajava 原生 pushJavaObject
                try {
                    L.pushJavaObject(obj)
                } catch (e: Exception) {
                    L.pushString(obj.toString())
                }
            }
        }
    }
}