package com.nirithy.luacompose.config

import com.nirithy.luacompose.annotations.LuaComposeScan

/**
 * LuaCompose 编译期配置。
 *
 * 标注 @LuaComposeScan 注解指定需要 KSP 扫描的 Compose 包。
 * KSP 编译期会扫描这些包内所有 public 的 @Composable 函数，
 * 生成 GeneratedComponentRegistry 代码，在运行时注册到 ComponentRegistry。
 *
 * 要添加新包，只需在此类上添加新的 @LuaComposeScan 注解。
 */
@LuaComposeScan("androidx.compose.material3")
@LuaComposeScan("androidx.compose.foundation")
@LuaComposeScan("androidx.compose.foundation.layout")
@LuaComposeScan("androidx.compose.ui")
@LuaComposeScan("androidx.compose.animation")
class LuaComposeConfig