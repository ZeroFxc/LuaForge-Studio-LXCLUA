package com.kulipai.luacompose.compose
import android.util.Log
fun logType(value: Any?, type: Class<*>) {
    if (value is Map<*, *>) {
        Log.e("TypeResolver", "Map passed: _isCardColors=${value["_isCardColors"]} type.name=${type.name}")
    }
}
