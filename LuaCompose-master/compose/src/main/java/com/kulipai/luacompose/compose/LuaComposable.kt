package com.kulipai.luacompose.compose

import androidx.compose.runtime.Composable

fun interface LuaComposable {
    @Composable
    fun invoke(props: Map<String, Any?>)
}
