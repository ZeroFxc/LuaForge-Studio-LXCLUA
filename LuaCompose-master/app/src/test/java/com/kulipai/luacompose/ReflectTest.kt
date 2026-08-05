package com.kulipai.luacompose

import org.junit.Test

class ReflectTest {
    @Test
    fun testReflection() {
        try {
            val clazz = Class.forName("androidx.compose.material3.TextKt")
            println("Found class! Methods:")
            clazz.declaredMethods.forEach { println(it.name) }
        } catch(e: Exception) {
            println("Error: $e")
        }
    }
}
