package com.kulipai.luacompose.compose

import com.kulipai.luacompose.annotations.LuaBridgeClass
import com.kulipai.luacompose.annotations.LuaBridgeLocals
import com.kulipai.luacompose.annotations.LuaBridgeModifiers
import com.kulipai.luacompose.annotations.LuaBridgePackage

@LuaBridgePackage(packageName = "androidx.compose.foundation", category = "foundation")
@LuaBridgePackage(packageName = "androidx.compose.foundation.layout", category = "foundation.layout")
@LuaBridgePackage(packageName = "androidx.compose.material3", category = "material3")
@LuaBridgePackage(packageName = "androidx.compose.ui", category = "ui")
@LuaBridgePackage(packageName = "androidx.compose.ui.viewinterop", category = "ui.viewinterop")
@LuaBridgeClass(targetClass = androidx.compose.ui.graphics.Color::class, category = "ui.graphics")
@LuaBridgeClass(targetClass = androidx.compose.ui.unit.Dp::class, category = "ui.unit")
@LuaBridgeModifiers(packageName = "androidx.compose.foundation.layout")
@LuaBridgeLocals(packageName = "androidx.compose.ui.platform", category = "ui.platform")
@LuaBridgePackage(packageName = "androidx.navigation3.ui", category = "navigation3")
@LuaBridgePackage(packageName = "androidx.navigation3.runtime", category = "navigation3")
class LuaComposeConfig
