package com.nirithy.luacompose.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luajava.JavaFunction
import com.luajava.LuaObject
import com.luajava.LuaState
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.bridge.LazyNamespace
import com.nirithy.luacompose.modifier.ModifierChain

/**
 * Foundation 插件 — 注入 Compose 基础 API 到 compose 全局表
 *
 * 负责注入：
 * - Modifier 工厂
 * - Arrangement 枚举（布局排列方式）
 * - Alignment 枚举（对齐方式）
 * - FontWeight 枚举（字重）
 * - RoundedCornerShape / CircleShape 形状工厂
 * - dp / sp 单位转换
 * - color 颜色工厂
 * - Brush 画刷工厂
 * - PathOperation / StrokeCap 枚举
 * - Easing 缓动函数表
 * - Spring 常量表
 * - 动画规格工厂（tween / spring / repeatable 等）
 * - 进出场动画工厂（fadeIn / slideIn 等）
 * - 图形首类对象工厂（Color / Offset / Size / Rect）
 * - Path 工厂
 * - gestures 手势命名空间
 */
object FoundationPlugin : ComposePlugin {
    override val namespace: String = "foundation"

    override fun getComponents(): Map<String, @androidx.compose.runtime.Composable (com.nirithy.luacompose.node.ComposeNode) -> Unit> {
        return emptyMap() // Foundation 只注入全局 API，不注册组件渲染器
    }

    override fun injectGlobals(L: LuaState, composeTableIdx: Int) {
        injectModifier(L, composeTableIdx)
        injectArrangement(L, composeTableIdx)
        injectAlignment(L, composeTableIdx)
        injectFontWeight(L, composeTableIdx)
        injectShapeFactories(L, composeTableIdx)
        injectDpSp(L, composeTableIdx)
        injectColor(L, composeTableIdx)
        injectBrush(L, composeTableIdx)
        injectPathOperation(L, composeTableIdx)
        injectEasing(L, composeTableIdx)
        injectSpringConstants(L, composeTableIdx)
        injectAnimationSpecs(L, composeTableIdx)
        injectAnimationTransitions(L, composeTableIdx)
        injectGraphicsFactories(L, composeTableIdx)
        injectPathFactory(L, composeTableIdx)
        injectGestures(L, composeTableIdx)
    }

    // ========== Modifier ==========

    /**
     * 将 Modifier 注入为可调用表
     *
     * - Modifier() → 创建新的 ModifierChain 包装器（Lua 表）
     * - Modifier.fillMaxWidth() → 创建新链并调用 fillMaxWidth()，返回包装器
     * - Modifier.padding({horizontal = 12}) → 创建新链，支持 table 参数
     *
     * 返回的包装器是 Lua 表，内部 _chain 字段持有 ModifierChain，
     * 支持链式调用（包装器的 __index 转发到 ModifierChain 方法）。
     */
    private fun injectModifier(L: LuaState, tableIdx: Int) {
        L.newTable()
        val modifierTableIdx = L.getTop()

        // 元表
        L.newTable()

        // __call: Modifier() → 创建新的 ModifierChain 包装器
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val chain = ModifierChain.create()
                pushModifierWrapper(L, chain)
                return 1
            }
        })
        L.setField(-2, "__call")

        // __index: Modifier.xxx → 返回函数，调用时创建新链并执行对应方法
        L.pushJavaFunction(ModifierIndexHandler(L))
        L.setField(-2, "__index")

        // setMetaTable 在某些 LuaJava 版本中不弹出元表，需手动检查
        val beforeSet = L.getTop()
        val result = L.setMetaTable(modifierTableIdx)
        if (L.getTop() == beforeSet) {
            L.pop(1) // 元表未被弹出，手动清理
        }
        L.setField(tableIdx, "Modifier")
    }

    /**
     * 创建 Lua 表包装 ModifierChain，支持链式调用。
     * 包装器的 _chain 字段持有 ModifierChain，
     * __index 元表转发方法调用到 ModifierChain。
     */
    private fun pushModifierWrapper(L: LuaState, chain: ModifierChain) {
        L.newTable()
        val wrapperIdx = L.getTop()

        // 存储 chain 引用
        L.pushJavaObject(chain)
        L.setField(wrapperIdx, "_chain")

        // 设置元表
        L.newTable()
        val metaIdx = L.getTop()

        // __index: 返回可调用函数，执行链式操作并返回包装器自身
        L.pushJavaFunction(ModifierChainMethodHandler(L))
        L.setField(-2, "__index")

        L.setMetaTable(wrapperIdx)
    }

    /**
     * Modifier 的 __index 处理器
     * 返回一个 JavaFunction，调用时创建新 ModifierChain 并通过反射调用对应方法
     *
     * __index 接收 (table, key)，key 在索引 3，索引 2 是 table 自身
     */
    private class ModifierIndexHandler(L: LuaState) : JavaFunction(L) {
        override fun execute(): Int {
            val key = L.toString(3) // 方法名（索引 3 = key，索引 2 = table）
            L.pushJavaFunction(ModifierMethodCall(L, key))
            return 1
        }
    }

    /**
     * Modifier 方法调用处理器（Modifier.xxx 返回的函数）
     * 创建新的 ModifierChain，通过反射调用对应方法并返回包装器
     */
    private class ModifierMethodCall(L: LuaState, private val methodName: String) : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val top = L.getTop()
            val argCount = top - 1 // 减去函数自身

            try {
                // 第一个参数是 table 时，尝试特殊处理
                if (argCount >= 1 && L.isTable(2)) {
                    if (handleTableArg(chain, L, 2)) {
                        pushModifierWrapper(L, chain)
                        return 1
                    }
                }

                // 通过反射调用方法
                invokeMethod(chain, top, argCount)
            } catch (e: Exception) {
                // 方法调用失败，返回空链
            }

            pushModifierWrapper(L, chain)
            return 1
        }

        private fun invokeMethod(chain: ModifierChain, top: Int, argCount: Int) {
            val methods = ModifierChain::class.java.methods.filter { it.name == methodName }.toTypedArray()
            if (methods.isNotEmpty()) {
                val method = findBestMethod(methods, argCount)
                val args = convertArgs(L, method, top)
                method.invoke(chain, *args)
            }
        }

        /**
         * 处理 table 参数的方法（如 padding({horizontal = 12})）
         * @return true 表示已处理，false 表示需回退到反射
         */
        private fun handleTableArg(chain: ModifierChain, L: LuaState, tableIdx: Int): Boolean {
            when (methodName) {
                "padding" -> {
                    val all = getTableNum(L, tableIdx, "all")
                    val horizontal = getTableNum(L, tableIdx, "horizontal")
                    val vertical = getTableNum(L, tableIdx, "vertical")
                    val start = getTableNum(L, tableIdx, "start")
                    val top = getTableNum(L, tableIdx, "top")
                    val end = getTableNum(L, tableIdx, "end")
                    val bottom = getTableNum(L, tableIdx, "bottom")
                    when {
                        all != null -> chain.padding(all)
                        start != null && top != null && end != null && bottom != null ->
                            chain.padding(start, top, end, bottom)
                        horizontal != null && vertical != null ->
                            chain.padding(horizontal, vertical)
                        horizontal != null -> chain.paddingHorizontal(horizontal)
                        vertical != null -> chain.paddingVertical(vertical)
                        start != null -> chain.paddingStart(start)
                        top != null -> chain.paddingTop(top)
                        end != null -> chain.paddingEnd(end)
                        bottom != null -> chain.paddingBottom(bottom)
                        else -> return false
                    }
                    return true
                }
                // 其他支持 table 参数的方法，按需添加
                else -> return false
            }
        }

        /** 从 table 中读取数值字段 */
        private fun getTableNum(L: LuaState, tableIdx: Int, key: String): Float? {
            L.pushString(key)
            L.getTable(tableIdx)
            val result = if (L.isNumber(-1)) L.toNumber(-1).toFloat() else null
            L.pop(1)
            return result
        }

        /** 按参数个数匹配最佳方法重载 */
        private fun findBestMethod(
            methods: Array<java.lang.reflect.Method>,
            argCount: Int
        ): java.lang.reflect.Method {
            return methods.firstOrNull { it.parameterCount == argCount }
                ?: methods.first()
        }

        /** 将 Lua 栈参数转换为 Java 方法参数 */
        private fun convertArgs(
            L: LuaState,
            method: java.lang.reflect.Method,
            top: Int
        ): Array<Any?> {
            val paramTypes = method.parameterTypes
            return Array(paramTypes.size) { i ->
                val luaIdx = i + 2 // Lua 参数从索引 2 开始
                if (luaIdx <= top) convertArg(L, luaIdx, paramTypes[i]) else null
            }
        }

        /** 将单个 Lua 值转换为目标 Java 类型 */
        private fun convertArg(L: LuaState, idx: Int, targetType: Class<*>): Any? {
            return when {
                targetType == java.lang.Float::class.java || targetType == java.lang.Float.TYPE ->
                    L.toNumber(idx).toFloat()
                targetType == java.lang.Integer::class.java || targetType == java.lang.Integer.TYPE ->
                    L.toNumber(idx).toInt()
                targetType == java.lang.Long::class.java || targetType == java.lang.Long.TYPE ->
                    L.toNumber(idx).toLong()
                targetType == java.lang.Double::class.java || targetType == java.lang.Double.TYPE ->
                    L.toNumber(idx)
                targetType == java.lang.Boolean::class.java || targetType == java.lang.Boolean.TYPE ->
                    L.toBoolean(idx)
                targetType == String::class.java ->
                    L.toString(idx)
                targetType == LuaObject::class.java ->
                    L.getLuaObject(idx)
                targetType == Runnable::class.java -> {
                    val obj = L.getLuaObject(idx)
                    Runnable {
                        synchronized(ComposeBridgeInstance.current.luaLock) { obj.call() }
                    }
                }
                else -> L.toJavaObject(idx)
            }
        }
    }

    /**
     * 包装器的 __index 处理器（链式调用）
     * 从包装器的 _chain 字段获取 ModifierChain，
     * 调用对应方法并返回包装器自身，支持链式调用。
     */
    private class ModifierChainMethodHandler(L: LuaState) : JavaFunction(L) {
        override fun execute(): Int {
            val key = L.toString(3) // 方法名
            // 返回一个可调用函数，执行链式操作
            L.pushJavaFunction(ModifierChainMethodCall(L, key))
            return 1
        }
    }

    /**
     * 包装器的链式方法调用处理器
     * 从包装器获取 ModifierChain，调用方法，返回包装器自身
     */
    private class ModifierChainMethodCall(L: LuaState, private val methodName: String) : JavaFunction(L) {
        override fun execute(): Int {
            // 获取包装器表（self）
            val wrapperIdx = 1
            L.getField(wrapperIdx, "_chain")
            val chain = try {
                L.toJavaObject(-1) as? ModifierChain
            } catch (e: Exception) {
                null
            }
            L.pop(1)

            if (chain != null) {
                val top = L.getTop()
                val argCount = top - 1

                try {
                    if (argCount >= 1 && L.isTable(2)) {
                        if (handleTableArg(chain, L, 2)) {
                            L.pushValue(wrapperIdx)
                            return 1
                        }
                    }
                    invokeMethod(chain, top, argCount)
                } catch (e: Exception) {
                    // 忽略
                }
            }

            // 返回包装器自身，支持链式调用
            L.pushValue(wrapperIdx)
            return 1
        }

        private fun invokeMethod(chain: ModifierChain, top: Int, argCount: Int) {
            val methods = ModifierChain::class.java.methods.filter { it.name == methodName }.toTypedArray()
            if (methods.isNotEmpty()) {
                val method = methods.firstOrNull { it.parameterCount == argCount } ?: methods.first()
                val paramTypes = method.parameterTypes
                val args = Array(paramTypes.size) { i ->
                    val luaIdx = i + 2
                    if (luaIdx <= top) convertArg(L, luaIdx, paramTypes[i]) else null
                }
                method.invoke(chain, *args)
            }
        }

        private fun handleTableArg(chain: ModifierChain, L: LuaState, tableIdx: Int): Boolean {
            // 复用 ModifierMethodCall 的逻辑，但直接操作
            when (methodName) {
                "padding" -> {
                    val all = getTableNum(L, tableIdx, "all")
                    val horizontal = getTableNum(L, tableIdx, "horizontal")
                    val vertical = getTableNum(L, tableIdx, "vertical")
                    val start = getTableNum(L, tableIdx, "start")
                    val top = getTableNum(L, tableIdx, "top")
                    val end = getTableNum(L, tableIdx, "end")
                    val bottom = getTableNum(L, tableIdx, "bottom")
                    when {
                        all != null -> chain.padding(all)
                        start != null && top != null && end != null && bottom != null ->
                            chain.padding(start, top, end, bottom)
                        horizontal != null && vertical != null ->
                            chain.padding(horizontal, vertical)
                        horizontal != null -> chain.paddingHorizontal(horizontal)
                        vertical != null -> chain.paddingVertical(vertical)
                        start != null -> chain.paddingStart(start)
                        top != null -> chain.paddingTop(top)
                        end != null -> chain.paddingEnd(end)
                        bottom != null -> chain.paddingBottom(bottom)
                        else -> return false
                    }
                    return true
                }
                else -> return false
            }
        }

        private fun getTableNum(L: LuaState, tableIdx: Int, key: String): Float? {
            L.pushString(key)
            L.getTable(tableIdx)
            val result = if (L.isNumber(-1)) L.toNumber(-1).toFloat() else null
            L.pop(1)
            return result
        }

        private fun convertArg(L: LuaState, idx: Int, targetType: Class<*>): Any? {
            return when {
                targetType == java.lang.Float::class.java || targetType == java.lang.Float.TYPE ->
                    L.toNumber(idx).toFloat()
                targetType == java.lang.Integer::class.java || targetType == java.lang.Integer.TYPE ->
                    L.toNumber(idx).toInt()
                targetType == java.lang.Long::class.java || targetType == java.lang.Long.TYPE ->
                    L.toNumber(idx).toLong()
                targetType == java.lang.Double::class.java || targetType == java.lang.Double.TYPE ->
                    L.toNumber(idx)
                targetType == java.lang.Boolean::class.java || targetType == java.lang.Boolean.TYPE ->
                    L.toBoolean(idx)
                targetType == String::class.java ->
                    L.toString(idx)
                targetType == LuaObject::class.java ->
                    L.getLuaObject(idx)
                targetType == Runnable::class.java -> {
                    val obj = L.getLuaObject(idx)
                    Runnable {
                        synchronized(ComposeBridgeInstance.current.luaLock) { obj.call() }
                    }
                }
                else -> L.toJavaObject(idx)
            }
        }
    }

    // ========== Arrangement ==========

    private fun injectArrangement(L: LuaState, tableIdx: Int) {
        L.newTable()
        // ★ 注入 Java 枚举对象，替代字符串，支持类型安全的直接匹配
        L.pushJavaObject(Arrangement.Top); L.setField(-2, "Top")
        L.pushJavaObject(Arrangement.Center); L.setField(-2, "Center")
        L.pushJavaObject(Arrangement.Bottom); L.setField(-2, "Bottom")
        L.pushJavaObject(Arrangement.Start); L.setField(-2, "Start")
        L.pushJavaObject(Arrangement.End); L.setField(-2, "End")
        L.pushJavaObject(Arrangement.SpaceAround); L.setField(-2, "SpaceAround")
        L.pushJavaObject(Arrangement.SpaceBetween); L.setField(-2, "SpaceBetween")
        L.pushJavaObject(Arrangement.SpaceEvenly); L.setField(-2, "SpaceEvenly")
        // spacedBy 工厂 — 返回 Java 对象，支持 space 和 alignment 参数
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                if (top < 2) { L.pushJavaObject(Arrangement.spacedBy(0.dp)); return 1 }
                // 第一个参数：space (dp 值)
                val space = L.toNumber(2)
                // 第二个参数（可选）：alignment
                if (top >= 3) {
                    val obj = L.toJavaObject(3)
                    if (obj is Alignment.Horizontal) {
                        L.pushJavaObject(Arrangement.spacedBy(space.dp, obj))
                    } else if (obj is Alignment.Vertical) {
                        L.pushJavaObject(Arrangement.spacedBy(space.dp, obj))
                    } else {
                        L.pushJavaObject(Arrangement.spacedBy(space.dp))
                    }
                } else {
                    L.pushJavaObject(Arrangement.spacedBy(space.dp))
                }
                return 1
            }
        })
        L.setField(-2, "spacedBy")
        // aligned 工厂 — 返回 Java 对象
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                if (top >= 2) {
                    val obj = L.toJavaObject(2)
                    if (obj is Alignment.Horizontal) {
                        L.pushJavaObject(Arrangement.aligned(obj))
                    } else if (obj is Alignment.Vertical) {
                        L.pushJavaObject(Arrangement.aligned(obj))
                    } else {
                        L.pushJavaObject(Arrangement.Center)
                    }
                } else {
                    L.pushJavaObject(Arrangement.Center)
                }
                return 1
            }
        })
        L.setField(-2, "aligned")
        L.setField(tableIdx, "Arrangement")
    }

    // ========== Alignment ==========

    private fun injectAlignment(L: LuaState, tableIdx: Int) {
        L.newTable()
        // ★ 注入 Java 枚举对象，替代字符串
        // 2D Alignment
        L.pushJavaObject(Alignment.TopStart); L.setField(-2, "TopStart")
        L.pushJavaObject(Alignment.TopCenter); L.setField(-2, "TopCenter")
        L.pushJavaObject(Alignment.TopEnd); L.setField(-2, "TopEnd")
        L.pushJavaObject(Alignment.CenterStart); L.setField(-2, "CenterStart")
        L.pushJavaObject(Alignment.Center); L.setField(-2, "Center")
        L.pushJavaObject(Alignment.CenterEnd); L.setField(-2, "CenterEnd")
        L.pushJavaObject(Alignment.BottomStart); L.setField(-2, "BottomStart")
        L.pushJavaObject(Alignment.BottomCenter); L.setField(-2, "BottomCenter")
        L.pushJavaObject(Alignment.BottomEnd); L.setField(-2, "BottomEnd")
        // 1D Alignment.Vertical
        L.pushJavaObject(Alignment.Top); L.setField(-2, "Top")
        L.pushJavaObject(Alignment.CenterVertically); L.setField(-2, "CenterVertically")
        L.pushJavaObject(Alignment.Bottom); L.setField(-2, "Bottom")
        // 1D Alignment.Horizontal
        L.pushJavaObject(Alignment.Start); L.setField(-2, "Start")
        L.pushJavaObject(Alignment.CenterHorizontally); L.setField(-2, "CenterHorizontally")
        L.pushJavaObject(Alignment.End); L.setField(-2, "End")
        L.setField(tableIdx, "Alignment")
    }

    // ========== FontWeight ==========

    private fun injectFontWeight(L: LuaState, tableIdx: Int) {
        L.newTable()
        // ★ 注入 Java FontWeight 对象，替代整数
        L.pushJavaObject(FontWeight.Thin); L.setField(-2, "Thin")
        L.pushJavaObject(FontWeight.ExtraLight); L.setField(-2, "ExtraLight")
        L.pushJavaObject(FontWeight.Light); L.setField(-2, "Light")
        L.pushJavaObject(FontWeight.Normal); L.setField(-2, "Normal")
        L.pushJavaObject(FontWeight.Medium); L.setField(-2, "Medium")
        L.pushJavaObject(FontWeight.SemiBold); L.setField(-2, "SemiBold")
        L.pushJavaObject(FontWeight.Bold); L.setField(-2, "Bold")
        L.pushJavaObject(FontWeight.ExtraBold); L.setField(-2, "ExtraBold")
        L.pushJavaObject(FontWeight.Black); L.setField(-2, "Black")
        L.pushJavaObject(FontWeight.W100); L.setField(-2, "W100")
        L.pushJavaObject(FontWeight.W200); L.setField(-2, "W200")
        L.pushJavaObject(FontWeight.W300); L.setField(-2, "W300")
        L.pushJavaObject(FontWeight.W400); L.setField(-2, "W400")
        L.pushJavaObject(FontWeight.W500); L.setField(-2, "W500")
        L.pushJavaObject(FontWeight.W600); L.setField(-2, "W600")
        L.pushJavaObject(FontWeight.W700); L.setField(-2, "W700")
        L.pushJavaObject(FontWeight.W800); L.setField(-2, "W800")
        L.pushJavaObject(FontWeight.W900); L.setField(-2, "W900")
        L.setField(tableIdx, "FontWeight")
    }

    // ========== Shape 工厂 ==========

    private fun injectShapeFactories(L: LuaState, tableIdx: Int) {
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val all = if (top >= 2) L.toNumber(2) else 0.0
                L.pushJavaObject(com.nirithy.luacompose.graphics.LuaShape.rounded(all)); return 1
            }
        })
        L.setField(tableIdx, "RoundedCornerShape")

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                L.pushJavaObject(com.nirithy.luacompose.graphics.LuaShape.circle()); return 1
            }
        })
        L.setField(tableIdx, "CircleShape")
    }

    // ========== dp / sp ==========

    private fun injectDpSp(L: LuaState, tableIdx: Int) {
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val value = if (top < 2) 0.0 else L.toNumber(2)
                L.pushNumber(value); return 1
            }
        })
        L.setField(tableIdx, "dp")

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val value = if (top < 2) 0.0 else L.toNumber(2)
                L.pushNumber(value); return 1
            }
        })
        L.setField(tableIdx, "sp")
    }

    // ========== color ==========

    private fun injectColor(L: LuaState, tableIdx: Int) {
        // 先注册 Color 工厂函数，再增强为可调用表
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val color = when {
                    top >= 4 -> {
                        val r = L.toNumber(2).toInt(); val g = L.toNumber(3).toInt()
                        val b = L.toNumber(4).toInt(); val a = if (top >= 5) L.toNumber(5).toInt() else 255
                        (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                    }
                    top >= 2 -> {
                        val raw = L.toNumber(2).toLong()
                        if (raw and 0xFF000000L == 0L) raw or 0xFF000000L else raw
                    }
                    else -> 0xFF000000L
                }
                L.pushNumber(color.toDouble()); return 1
            }
        })
        val colorFactoryIdx = L.getTop()
        // 创建可调用表，添加预定义颜色常量
        L.newTable()
        val colorTableIdx = L.getTop()
        val predefined = mapOf(
            "Black" to 0xFF000000L, "White" to 0xFFFFFFFFL,
            "Red" to 0xFFFF0000L, "Green" to 0xFF00FF00L, "Blue" to 0xFF0000FFL,
            "Yellow" to 0xFFFFFF00L, "Cyan" to 0xFF00FFFFL, "Magenta" to 0xFFFF00FFL,
            "Gray" to 0xFF888888L, "DarkGray" to 0xFF444444L, "LightGray" to 0xFFCCCCCCL,
            "Transparent" to 0x00000000L,
        )
        for ((name, argb) in predefined) {
            L.pushNumber(argb.toDouble()); L.setField(-2, name)
        }
        L.newTable()
        L.pushValue(colorFactoryIdx)
        L.setField(-2, "__call")
        // setMetaTable 在某些 LuaJava 版本中不弹出元表，需手动检查
        val beforeSetColor = L.getTop()
        L.setMetaTable(colorTableIdx)
        if (L.getTop() == beforeSetColor) {
            L.pop(1) // 元表未被弹出，手动清理
        }
        L.pop(1) // 弹出工厂函数
        L.setField(tableIdx, "Color")
    }

    // ========== Brush ==========

    private fun injectBrush(L: LuaState, tableIdx: Int) {
        L.newTable()
        val brushIdx = L.getTop()

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                if (top < 2 || !L.isTable(2)) { L.pushNil(); return 1 }
                L.getField(2, "centerX"); val cx = if (L.isNumber(-1)) L.toNumber(-1) else 0.5; L.pop(1)
                L.getField(2, "centerY"); val cy = if (L.isNumber(-1)) L.toNumber(-1) else 0.5; L.pop(1)
                L.getField(2, "radius"); val radius = if (L.isNumber(-1)) L.toNumber(-1) else 1.0; L.pop(1)
                L.getField(2, "colors")
                val colors = mutableListOf<Long>()
                if (L.isTable(-1)) {
                    val len = L.rawLen(-1)
                    for (i in 1..len) { L.pushInteger(i.toLong()); L.getTable(-2); colors.add(L.toNumber(-1).toLong()); L.pop(1) }
                }
                L.pop(1)
                L.pushJavaObject(com.nirithy.luacompose.graphics.LuaBrush(
                    type = "radialGradient", centerX = cx, centerY = cy, radius = radius, colors = colors
                )); return 1
            }
        })
        L.setField(-2, "radialGradient")

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                if (top < 2 || !L.isTable(2)) { L.pushNil(); return 1 }
                L.getField(2, "startY"); val sy = if (L.isNumber(-1)) L.toNumber(-1) else 0.0; L.pop(1)
                L.getField(2, "endY"); val ey = if (L.isNumber(-1)) L.toNumber(-1) else 1.0; L.pop(1)
                L.getField(2, "colors")
                val colors = mutableListOf<Long>()
                if (L.isTable(-1)) {
                    val len = L.rawLen(-1)
                    for (i in 1..len) { L.pushInteger(i.toLong()); L.getTable(-2); colors.add(L.toNumber(-1).toLong()); L.pop(1) }
                }
                L.pop(1)
                L.pushJavaObject(com.nirithy.luacompose.graphics.LuaBrush(
                    type = "verticalGradient", startY = sy, endY = ey, colors = colors
                )); return 1
            }
        })
        L.setField(-2, "verticalGradient")

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                if (top < 2 || !L.isTable(2)) { L.pushNil(); return 1 }
                L.getField(2, "startX"); val sx = if (L.isNumber(-1)) L.toNumber(-1) else 0.0; L.pop(1)
                L.getField(2, "startY"); val sy = if (L.isNumber(-1)) L.toNumber(-1) else 0.0; L.pop(1)
                L.getField(2, "endX"); val ex = if (L.isNumber(-1)) L.toNumber(-1) else 1.0; L.pop(1)
                L.getField(2, "endY"); val ey = if (L.isNumber(-1)) L.toNumber(-1) else 1.0; L.pop(1)
                L.getField(2, "colors")
                val colors = mutableListOf<Long>()
                if (L.isTable(-1)) {
                    val len = L.rawLen(-1)
                    for (i in 1..len) { L.pushInteger(i.toLong()); L.getTable(-2); colors.add(L.toNumber(-1).toLong()); L.pop(1) }
                }
                L.pop(1)
                L.pushJavaObject(com.nirithy.luacompose.graphics.LuaBrush(
                    type = "linearGradient", startX = sx, startY = sy, endX = ex, endY = ey, colors = colors
                )); return 1
            }
        })
        L.setField(-2, "linearGradient")

        L.setField(tableIdx, "Brush")
    }

    // ========== PathOperation / StrokeCap ==========

    private fun injectPathOperation(L: LuaState, tableIdx: Int) {
        L.newTable()
        L.pushString("Difference"); L.setField(-2, "Difference")
        L.pushString("Intersect"); L.setField(-2, "Intersect")
        L.pushString("Union"); L.setField(-2, "Union")
        L.pushString("Xor"); L.setField(-2, "Xor")
        L.pushString("ReverseDifference"); L.setField(-2, "ReverseDifference")
        L.setField(tableIdx, "PathOperation")

        L.newTable()
        L.pushString("Butt"); L.setField(-2, "Butt")
        L.pushString("Round"); L.setField(-2, "Round")
        L.pushString("Square"); L.setField(-2, "Square")
        L.setField(tableIdx, "StrokeCap")
    }

    // ========== Easing ==========

    private fun injectEasing(L: LuaState, tableIdx: Int) {
        L.newTable()
        L.pushJavaObject(com.nirithy.luacompose.animation.EasingTable.Linear); L.setField(-2, "Linear")
        L.pushJavaObject(com.nirithy.luacompose.animation.EasingTable.FastOutSlowIn); L.setField(-2, "FastOutSlowIn")
        L.pushJavaObject(com.nirithy.luacompose.animation.EasingTable.FastOutLinearIn); L.setField(-2, "FastOutLinearIn")
        L.pushJavaObject(com.nirithy.luacompose.animation.EasingTable.LinearOutSlowIn); L.setField(-2, "LinearOutSlowIn")
        L.pushJavaObject(com.nirithy.luacompose.animation.EasingTable.EaseIn); L.setField(-2, "EaseIn")
        L.pushJavaObject(com.nirithy.luacompose.animation.EasingTable.EaseOut); L.setField(-2, "EaseOut")
        L.pushJavaObject(com.nirithy.luacompose.animation.EasingTable.EaseInOut); L.setField(-2, "EaseInOut")
        L.pushJavaObject(com.nirithy.luacompose.animation.EasingTable.EaseInCubic); L.setField(-2, "EaseInCubic")
        L.pushJavaObject(com.nirithy.luacompose.animation.EasingTable.EaseOutCubic); L.setField(-2, "EaseOutCubic")
        L.pushJavaObject(com.nirithy.luacompose.animation.EasingTable.EaseInOutCubic); L.setField(-2, "EaseInOutCubic")
        L.setField(tableIdx, "Easing")
    }

    // ========== Spring 常量 ==========

    private fun injectSpringConstants(L: LuaState, tableIdx: Int) {
        L.newTable()
        L.pushNumber(androidx.compose.animation.core.Spring.DampingRatioHighBouncy.toDouble()); L.setField(-2, "DampingRatioHighBouncy")
        L.pushNumber(androidx.compose.animation.core.Spring.DampingRatioMediumBouncy.toDouble()); L.setField(-2, "DampingRatioMediumBouncy")
        L.pushNumber(androidx.compose.animation.core.Spring.DampingRatioLowBouncy.toDouble()); L.setField(-2, "DampingRatioLowBouncy")
        L.pushNumber(androidx.compose.animation.core.Spring.DampingRatioNoBouncy.toDouble()); L.setField(-2, "DampingRatioNoBouncy")
        L.pushNumber(androidx.compose.animation.core.Spring.StiffnessHigh.toDouble()); L.setField(-2, "StiffnessHigh")
        L.pushNumber(androidx.compose.animation.core.Spring.StiffnessMedium.toDouble()); L.setField(-2, "StiffnessMedium")
        L.pushNumber(androidx.compose.animation.core.Spring.StiffnessMediumLow.toDouble()); L.setField(-2, "StiffnessMediumLow")
        L.pushNumber(androidx.compose.animation.core.Spring.StiffnessLow.toDouble()); L.setField(-2, "StiffnessLow")
        L.pushNumber(androidx.compose.animation.core.Spring.StiffnessVeryLow.toDouble()); L.setField(-2, "StiffnessVeryLow")
        L.setField(tableIdx, "Spring")
    }

    // ========== 动画规格 ==========

    private fun injectAnimationSpecs(L: LuaState, tableIdx: Int) {
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val duration = if (top >= 2) L.toNumber(2).toInt() else 300
                val delay = if (top >= 3) L.toNumber(3).toInt() else 0
                L.pushJavaObject(com.nirithy.luacompose.animation.AnimationSpecs.createTween(duration, delay)); return 1
            }
        })
        L.setField(tableIdx, "tween")

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val damping = if (top >= 2) L.toNumber(2).toFloat() else 0.5f
                val stiffness = if (top >= 3) L.toNumber(3).toFloat() else 400f
                L.pushJavaObject(com.nirithy.luacompose.animation.AnimationSpecs.createSpring(damping, stiffness)); return 1
            }
        })
        L.setField(tableIdx, "spring")

        L.pushJavaFunction(object : JavaFunction(L) {
            @Suppress("UNCHECKED_CAST")
            override fun execute(): Int {
                val top = L.getTop()
                val iterations = if (top >= 2) L.toNumber(2).toInt() else 1
                val spec = if (top >= 3) L.toJavaObject(3) as? androidx.compose.animation.core.DurationBasedAnimationSpec<Float>
                    else androidx.compose.animation.core.tween<Float>(300) as androidx.compose.animation.core.DurationBasedAnimationSpec<Float>
                val mode = if (top >= 4) L.toJavaObject(4) as? androidx.compose.animation.core.RepeatMode else androidx.compose.animation.core.RepeatMode.Restart
                L.pushJavaObject(com.nirithy.luacompose.animation.AnimationSpecs.createRepeatable(
                    iterations, spec ?: (androidx.compose.animation.core.tween<Float>(300) as androidx.compose.animation.core.DurationBasedAnimationSpec<Float>),
                    mode ?: androidx.compose.animation.core.RepeatMode.Restart
                ))
                return 1
            }
        })
        L.setField(tableIdx, "repeatable")

        L.pushJavaFunction(object : JavaFunction(L) {
            @Suppress("UNCHECKED_CAST")
            override fun execute(): Int {
                val top = L.getTop()
                val spec = if (top >= 2) L.toJavaObject(2) as? androidx.compose.animation.core.DurationBasedAnimationSpec<Float>
                    else androidx.compose.animation.core.tween<Float>(1000) as androidx.compose.animation.core.DurationBasedAnimationSpec<Float>
                val mode = if (top >= 3) L.toJavaObject(3) as? androidx.compose.animation.core.RepeatMode else androidx.compose.animation.core.RepeatMode.Restart
                L.pushJavaObject(com.nirithy.luacompose.animation.AnimationSpecs.createInfiniteRepeatable(
                    spec ?: (androidx.compose.animation.core.tween<Float>(1000) as androidx.compose.animation.core.DurationBasedAnimationSpec<Float>),
                    mode ?: androidx.compose.animation.core.RepeatMode.Restart
                ))
                return 1
            }
        })
        L.setField(tableIdx, "infiniteRepeatable")
    }

    // ========== 进出场动画 ==========

    private fun injectAnimationTransitions(L: LuaState, tableIdx: Int) {
        val specs = com.nirithy.luacompose.animation.AnimationSpecs
        // 淡入淡出
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.fadeInEnter()); return 1 }
        })
        L.setField(tableIdx, "fadeIn")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.fadeInEnter()); return 1 }
        })
        L.setField(tableIdx, "fadeInEnter")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.fadeOutExit()); return 1 }
        })
        L.setField(tableIdx, "fadeOut")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.fadeOutExit()); return 1 }
        })
        L.setField(tableIdx, "fadeOutExit")
        // 水平滑入滑出
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.slideInHorizontallyEnter()); return 1 }
        })
        L.setField(tableIdx, "slideInHorizontally")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.slideInHorizontallyEnter()); return 1 }
        })
        L.setField(tableIdx, "slideInHorizontallyEnter")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.slideOutHorizontallyExit()); return 1 }
        })
        L.setField(tableIdx, "slideOutHorizontally")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.slideOutHorizontallyExit()); return 1 }
        })
        L.setField(tableIdx, "slideOutHorizontallyExit")
        // 垂直滑入滑出
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.slideInVerticallyEnter()); return 1 }
        })
        L.setField(tableIdx, "slideInVertically")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.slideOutVerticallyExit()); return 1 }
        })
        L.setField(tableIdx, "slideOutVertically")
        // 缩放
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.scaleInEnter()); return 1 }
        })
        L.setField(tableIdx, "scaleIn")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.scaleInEnter()); return 1 }
        })
        L.setField(tableIdx, "scaleInEnter")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.scaleOutExit()); return 1 }
        })
        L.setField(tableIdx, "scaleOut")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.scaleOutExit()); return 1 }
        })
        L.setField(tableIdx, "scaleOutExit")
        // 展开/收缩
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.expandVerticallyEnter()); return 1 }
        })
        L.setField(tableIdx, "expandVertically")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.shrinkVerticallyExit()); return 1 }
        })
        L.setField(tableIdx, "shrinkVertically")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.expandHorizontallyEnter()); return 1 }
        })
        L.setField(tableIdx, "expandHorizontally")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.shrinkHorizontallyExit()); return 1 }
        })
        L.setField(tableIdx, "shrinkHorizontally")
        // 组合动画
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.fadeInExpandEnter()); return 1 }
        })
        L.setField(tableIdx, "fadeInExpand")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.fadeOutShrinkExit()); return 1 }
        })
        L.setField(tableIdx, "fadeOutShrink")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.fadeInSlideEnter()); return 1 }
        })
        L.setField(tableIdx, "fadeInSlide")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.fadeOutSlideExit()); return 1 }
        })
        L.setField(tableIdx, "fadeOutSlide")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.fadeInScaleEnter()); return 1 }
        })
        L.setField(tableIdx, "fadeInScale")
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int { L.pushJavaObject(specs.fadeOutScaleExit()); return 1 }
        })
        L.setField(tableIdx, "fadeOutScale")
    }

    // ========== 图形首类对象 ==========

    private fun injectGraphicsFactories(L: LuaState, tableIdx: Int) {
        // Color 由 injectColor 处理（增强版可调用表）
        // 这里只注册 Offset, Size, Rect

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val x = if (top >= 2) L.toNumber(2) else 0.0
                val y = if (top >= 3) L.toNumber(3) else 0.0
                L.pushJavaObject(com.nirithy.luacompose.graphics.LuaOffset(x, y)); return 1
            }
        })
        L.setField(tableIdx, "Offset")

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val w = if (top >= 2) L.toNumber(2) else 0.0
                val h = if (top >= 3) L.toNumber(3) else 0.0
                L.pushJavaObject(com.nirithy.luacompose.graphics.LuaSize(w, h)); return 1
            }
        })
        L.setField(tableIdx, "Size")

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val l = if (top >= 2) L.toNumber(2) else 0.0
                val t = if (top >= 3) L.toNumber(3) else 0.0
                val r = if (top >= 4) L.toNumber(4) else 0.0
                val b = if (top >= 5) L.toNumber(5) else 0.0
                L.pushJavaObject(com.nirithy.luacompose.graphics.LuaRect(l, t, r, b)); return 1
            }
        })
        L.setField(tableIdx, "Rect")
    }

    // ========== Path ==========

    private fun injectPathFactory(L: LuaState, tableIdx: Int) {
        for (name in listOf("Path", "LuaPath")) {
            L.pushJavaFunction(object : JavaFunction(L) {
                override fun execute(): Int {
                    L.pushJavaObject(com.nirithy.luacompose.draw.LuaPath()); return 1
                }
            })
            L.setField(tableIdx, name)
        }
    }

    // ========== Gestures ==========

    private fun injectGestures(L: LuaState, tableIdx: Int) {
        L.newTable()
        val gesturesIdx = L.getTop()

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                if (top < 2 || !L.isTable(2)) { L.pushNil(); return 1 }
                L.newTable()
                val resultIdx = L.getTop()
                L.pushNil()
                while (L.next(2) != 0) {
                    val key = L.toString(-2)
                    if (L.isFunction(-1)) {
                        L.pushString(key); L.pushValue(-2); L.setTable(resultIdx)
                    }
                    L.pop(1)
                }
                L.pushString("_type"); L.pushString("tapGestures"); L.setTable(resultIdx)
                return 1
            }
        })
        L.setField(-2, "detectTapGestures")

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                if (top < 2 || !L.isTable(2)) { L.pushNil(); return 1 }
                L.newTable()
                val resultIdx = L.getTop()
                L.pushNil()
                while (L.next(2) != 0) {
                    val key = L.toString(-2)
                    if (L.isFunction(-1)) {
                        L.pushString(key); L.pushValue(-2); L.setTable(resultIdx)
                    }
                    L.pop(1)
                }
                L.pushString("_type"); L.pushString("dragGestures"); L.setTable(resultIdx)
                return 1
            }
        })
        L.setField(-2, "detectDragGestures")

        L.setField(tableIdx, "gestures")
    }
}