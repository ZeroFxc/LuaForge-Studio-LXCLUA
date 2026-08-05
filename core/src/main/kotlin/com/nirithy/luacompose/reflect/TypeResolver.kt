package com.nirithy.luacompose.reflect

import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Lua → Java 类型智能解析器
 *
 * 处理 KSP 生成的组件渲染器中需要特殊转换的类型：
 * - CardColors：通过 _isCardColors 标记识别
 * - AnnotatedString：自动包装字符串
 * - 数字类型自动适配（Int/Float/Double/Long）
 * - Color → Long、Dp → Float 拆箱
 * - Kotlin inline class unbox-impl 拆箱
 *
 * 参考 LuaCompose-master 的 TypeResolver 设计
 */
object TypeResolver {

    /**
     * 将 Lua 值解析为目标 Java 类型
     *
     * @param value Lua 侧的原始值（Map/Number/String 等）
     * @param type 目标 Java 类型
     * @return 转换后的值，无匹配时返回原始值
     */
    @Composable
    fun resolve(value: Any?, type: Class<*>): Any? {
        if (value == null) return null

        // CardColors 特殊处理
        if (value is Map<*, *>) {
            if (value["_isCardColors"] == true && type.name == "androidx.compose.material3.CardColors") {
                return CardDefaults.cardColors(
                    containerColor = resolveColor(value["containerColor"], Color.Unspecified),
                    contentColor = resolveColor(value["contentColor"], Color.Unspecified)
                )
            }
        }

        // AnnotatedString 自动包装
        if (type.name == "androidx.compose.ui.text.AnnotatedString") {
            return androidx.compose.ui.text.AnnotatedString(value.toString())
        }

        // 数字类型适配
        if (value is Number) {
            return when (type) {
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> value.toInt()
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> value.toFloat()
                Double::class.javaPrimitiveType, Double::class.javaObjectType -> value.toDouble()
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> value.toLong()
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> value.toShort()
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> value.toByte()
                else -> value
            }
        }

        // Color → Long 拆箱
        if (value is Color && (type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType)) {
            return value.value.toLong()
        }

        // Dp → Float 拆箱
        if (value is androidx.compose.ui.unit.Dp && (type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType)) {
            return value.value
        }

        // Kotlin inline class unbox-impl 拆箱
        if (type.isPrimitive) {
            try {
                val unboxMethod = value.javaClass.methods.firstOrNull {
                    it.name == "unbox-impl"
                }
                if (unboxMethod != null && unboxMethod.returnType == type) {
                    return unboxMethod.invoke(value)
                }
            } catch (_: Exception) {
                // 忽略
            }
        }

        return value
    }

    /**
     * 解析颜色值，支持 Color 对象和 Long 色值
     */
    private fun resolveColor(value: Any?, default: Color): Color {
        return when (value) {
            is Color -> value
            is Long -> Color(value.toULong())
            is Number -> Color(value.toLong().toULong())
            else -> default
        }
    }

    /**
     * 参数类型强制转换，处理 Number → Dp 等特殊转换
     *
     * 参考 LuaCompose-master 的 ComposeBridge.coerceArg：
     * 当 Lua 侧传入 Number 但目标参数是 Dp 时，自动装箱为 Dp 对象。
     * 例如：compose.material3.TextField(modifier = ..., value = "hello", singleLine = true)
     * 中的 singleLine 是 Boolean，但 Lua 侧可能传入整数 1。
     */
    fun coerceArg(argVal: Any?, paramClass: Class<*>): Any? {
        if (argVal is Number) {
            return when (paramClass) {
                Float::class.java, Float::class.javaPrimitiveType -> argVal.toFloat()
                Int::class.java, Int::class.javaPrimitiveType -> argVal.toInt()
                Long::class.java, Long::class.javaPrimitiveType -> argVal.toLong()
                Double::class.java, Double::class.javaPrimitiveType -> argVal.toDouble()
                Short::class.java, Short::class.javaPrimitiveType -> argVal.toShort()
                Byte::class.java, Byte::class.javaPrimitiveType -> argVal.toByte()
                androidx.compose.ui.unit.Dp::class.java -> androidx.compose.ui.unit.Dp(argVal.toFloat())
                androidx.compose.ui.unit.TextUnit::class.java -> androidx.compose.ui.unit.TextUnit(argVal.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
                else -> argVal
            }
        }
        if (argVal is Boolean && paramClass == Boolean::class.javaPrimitiveType) {
            return argVal
        }
        return argVal
    }
}