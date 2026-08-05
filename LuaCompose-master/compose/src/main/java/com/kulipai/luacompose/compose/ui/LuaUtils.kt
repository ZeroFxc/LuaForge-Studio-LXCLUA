package com.kulipai.luacompose.compose.ui

import android.annotation.SuppressLint
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt

@SuppressLint("ModifierFactoryExtensionFunction")
fun resolveModifier(propRaw: Any?): Modifier {
    val prop = com.kulipai.luacompose.compose.runtime.ComposeBridge.unwrapAny(propRaw)
    // Log removed to prevent spam
    return when (prop) {
        is LuaModifier -> prop.modifier
        is Modifier -> prop
        else -> Modifier
    }
}

fun resolveColor(colorPropRaw: Any?, defaultColor: Color = Color.Unspecified): Color {
    val colorProp = com.kulipai.luacompose.compose.runtime.ComposeBridge.unwrapAny(colorPropRaw)
    val result = when (colorProp) {
        null -> defaultColor
        is Color -> colorProp
        is Long -> {
            if (colorProp in 0L..4294967295L) { Color(colorProp) } else { Color(colorProp.toULong()) }
        }
        else -> {
            val colorStr = colorProp.toString()
            try {
                val longVal = colorStr.toLongOrNull()
                if (longVal != null) { resolveColor(longVal, defaultColor) } else { Color(colorStr.toColorInt()) }
            } catch (_: Exception) { defaultColor }
        }
    }

    return result
}

fun resolveDp(valueRaw: Any?): androidx.compose.ui.unit.Dp {
    val value = com.kulipai.luacompose.compose.runtime.ComposeBridge.unwrapAny(valueRaw)
    return when (value) {
        is androidx.compose.ui.unit.Dp -> value
        is Number -> value.toFloat().dp
        is String -> value.toFloatOrNull()?.dp ?: 0.dp
        else -> 0.dp
    }
}

fun resolveSp(valueRaw: Any?): androidx.compose.ui.unit.TextUnit {
    val value = com.kulipai.luacompose.compose.runtime.ComposeBridge.unwrapAny(valueRaw)
    return when (value) {
        is androidx.compose.ui.unit.TextUnit -> value
        is Number -> value.toFloat().sp
        is String -> value.toFloatOrNull()?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified
        else -> androidx.compose.ui.unit.TextUnit.Unspecified
    }
}

fun resolveShape(shapePropRaw: Any?): androidx.compose.ui.graphics.Shape? {
    val shapeProp = com.kulipai.luacompose.compose.runtime.ComposeBridge.unwrapAny(shapePropRaw)
    if (shapeProp is androidx.compose.ui.graphics.Shape) return shapeProp
    if (shapeProp is String) {
        return when (shapeProp.lowercase()) {
            "circle", "circleshape" -> androidx.compose.foundation.shape.CircleShape
            "rounded", "roundedcornershape" -> androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            "rectangle", "rectangleshape" -> androidx.compose.ui.graphics.RectangleShape
            else -> null
        }
    }
    if (shapeProp is com.kulipai.luacompose.compose.script.ScriptValue && shapeProp.isUserdata()) {
        val ud = shapeProp.asUserdata()
        if (ud is androidx.compose.ui.graphics.Shape) return ud
    }
    return null
}
