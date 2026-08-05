package com.kulipai.luacompose

import androidx.compose.runtime.Composable
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.declaredFunctions

class TestCallBy {
    @Composable
    fun MyText(text: String, color: Int = 0) {
        println(text + color)
    }

    fun test() {
        val func = this::class.declaredFunctions.first { it.name == "MyText" }
        try {
            func.callBy(mapOf())
        } catch(e: Exception) {
            println("CallBy Error: $e")
        }
    }
}
