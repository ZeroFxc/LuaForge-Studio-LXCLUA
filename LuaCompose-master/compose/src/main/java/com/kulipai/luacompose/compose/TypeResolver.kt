package com.kulipai.luacompose.compose

import kotlin.reflect.KType

import androidx.compose.runtime.Composable
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import com.kulipai.luacompose.compose.ui.resolveColor

object TypeResolver {
    @Composable
    fun resolve(value: Any?, type: Class<*>): Any? {
        if (value == null) return null
        
        if (value is Map<*, *>) {
            android.util.Log.e("TypeResolver", "Map check: isCardColors=${value["_isCardColors"]}, type.name=${type.name}")
            if (value["_isCardColors"] == true && type.name == "androidx.compose.material3.CardColors") {
                return CardDefaults.cardColors(
                    containerColor = resolveColor(value["containerColor"], Color.Unspecified),
                    contentColor = resolveColor(value["contentColor"], Color.Unspecified)
                )
            }
        }
        
        if (type == String::class.java) {
            return value.toString()
        }
        
        if (type.name == "androidx.compose.ui.text.AnnotatedString") {
            return androidx.compose.ui.text.AnnotatedString(value.toString())
        }

        if (value is Number) {
            when (type) {
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> return value.toInt()
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> return value.toFloat()
                Double::class.javaPrimitiveType, Double::class.javaObjectType -> return value.toDouble()
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> return value.toLong()
                Short::class.javaPrimitiveType, Short::class.javaObjectType -> return value.toShort()
                Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> return value.toByte()
            }
        }
        if (value is androidx.compose.ui.graphics.Color && (type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType)) {
            return value.value.toLong()
        }
        if (value is androidx.compose.ui.unit.Dp && (type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType)) {
            return value.value
        }
        
        // Generic unbox-impl for other value classes (like TextUnit)
        if (type.isPrimitive) {
            try {
                val unboxMethod = value.javaClass.methods.firstOrNull { it.name == "unbox-impl" }
                if (unboxMethod != null && unboxMethod.returnType == type) {
                    return unboxMethod.invoke(value)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        // Return original value if no conversion is matched
        return value
    }
}
