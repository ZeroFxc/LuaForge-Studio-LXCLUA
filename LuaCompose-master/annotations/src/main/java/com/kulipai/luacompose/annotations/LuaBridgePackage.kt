package com.kulipai.luacompose.annotations

import kotlin.reflect.KClass

@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class LuaBridgePackage(
    val packageName: String,
    val category: String = "ui"
)

@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class LuaBridgeClass(
    val targetClass: KClass<*>,
    val category: String = "ui"
)

@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class LuaBridgeModifiers(
    val packageName: String
)

@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class LuaBridgeLocals(
    val packageName: String,
    val category: String = ""
)
