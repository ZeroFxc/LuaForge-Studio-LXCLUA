package com.kulipai.luacompose

import org.junit.Test
import java.lang.reflect.Method

class TestReflect {
    @Test
    fun testTextMethods() {
        val clazz = Class.forName("androidx.compose.material3.TextKt")
        val methods = clazz.declaredMethods.filter { it.name.startsWith("Text") }
        for (m in methods) {
            println("Method: ${m.name}")
            for ((i, p) in m.parameterTypes.withIndex()) {
                println("  $i: ${p.name}")
            }
        }
    }
}
