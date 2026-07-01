package com.nirithy.luacompose.annotations

/**
 * 标记需要 KSP 扫描的 Compose 包，生成 ComponentRegistry 注册代码。
 *
 * 使用方式：在任意类上标注此注解，指定要扫描的 packageName。
 *    @LuaComposeScan("androidx.compose.material3")
 *    class LuaComposeConfig
 *
 * KSP 编译期会扫描指定包内所有 public 的 @Composable 函数，
 * 生成对应的 ComponentRegistry.register() 调用代码。
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class LuaComposeScan(
    /** 要扫描的 Compose 包名，如 "androidx.compose.material3" */
    val packageName: String
)