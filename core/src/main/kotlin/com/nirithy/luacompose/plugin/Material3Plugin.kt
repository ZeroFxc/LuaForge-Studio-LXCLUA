package com.nirithy.luacompose.plugin

import com.luajava.JavaFunction
import com.luajava.LuaState
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.node.ComposeNode

/**
 * Material3 插件 — 注入 Material3 主题 API 到 compose.material3 命名空间
 *
 * 负责注入：
 * - CardDefaults.cardColors 工厂
 * - MaterialTheme（colorScheme / typography / shapes 懒加载）
 *
 * 颜色方案、字体排版、形状从 ComposeScope 动态获取，支持运行时主题切换。
 * 替代原来在 ComposeBridgeInstance 中硬编码的 themeColors/themeTypography/themeShapes。
 *
 * 参考 LuaCompose-master 的 Material3Plugin 设计
 */
object Material3Plugin : ComposePlugin {
    override val namespace: String = "material3"

    override fun getComponents(): Map<String, @androidx.compose.runtime.Composable (ComposeNode) -> Unit> =
        emptyMap()

    override fun injectGlobals(L: LuaState, composeTableIdx: Int) {
        // 创建 material3 子命名空间表并注入到 compose 根表
        L.newTable()
        val material3Idx = L.getTop()
        injectCardDefaults(L, material3Idx)
        injectMaterialTheme(L, material3Idx)
        // 设为 compose.material3，LazyNamespace 的 __index 会优先命中已缓存的表
        L.setField(composeTableIdx, "material3")
    }

    /**
     * 注入 CardDefaults.cardColors 工厂
     * Lua 用法: compose.material3.CardDefaults.cardColors { containerColor = ..., contentColor = ... }
     */
    private fun injectCardDefaults(L: LuaState, tableIdx: Int) {
        L.newTable()
        val cardDefaultsIdx = L.getTop()

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                L.newTable()
                L.pushBoolean(true)
                L.setField(-2, "_isCardColors")

                if (top >= 2 && L.isTable(2)) {
                    // 读取 containerColor
                    L.pushString("containerColor")
                    L.getTable(2)
                    if (!L.isNil(-1)) {
                        L.pushValue(-1)
                        L.setField(-3, "containerColor")
                    }
                    L.pop(1)

                    // 读取 contentColor
                    L.pushString("contentColor")
                    L.getTable(2)
                    if (!L.isNil(-1)) {
                        L.pushValue(-1)
                        L.setField(-3, "contentColor")
                    }
                    L.pop(1)
                }
                return 1
            }
        })
        L.setField(-2, "cardColors")

        L.setField(tableIdx, "CardDefaults")
    }

    /**
     * 注入 MaterialTheme — colorScheme / typography / shapes 懒加载
     *
     * 每次访问时从 ComposeScope 动态获取当前主题值，
     * 支持 MaterialTheme 颜色/字体/形状的运行时切换。
     */
    private fun injectMaterialTheme(L: LuaState, tableIdx: Int) {
        L.newTable()
        val mtIdx = L.getTop()

        // colorScheme 懒加载
        L.newTable()
        val csIdx = L.getTop()
        L.newTable()
        L.pushJavaFunction(ColorSchemeIndexHandler(L))
        L.setField(-2, "__index")
        // setMetaTable 在某些 LuaJava 版本中不弹出元表，需手动检查
        val beforeCs = L.getTop()
        L.setMetaTable(csIdx)
        if (L.getTop() == beforeCs) { L.pop(1) }
        L.setField(mtIdx, "colorScheme")
        // 重新推入 colorScheme 表，供 "colors" 别名使用
        L.getField(mtIdx, "colorScheme")
        L.setField(mtIdx, "colors") // 别名

        // typography 懒加载
        L.newTable()
        val typeIdx = L.getTop()
        L.newTable()
        L.pushJavaFunction(TypographyIndexHandler(L))
        L.setField(-2, "__index")
        // setMetaTable 在某些 LuaJava 版本中不弹出元表，需手动检查
        val beforeType = L.getTop()
        L.setMetaTable(typeIdx)
        if (L.getTop() == beforeType) { L.pop(1) }
        L.setField(mtIdx, "typography")

        // shapes 懒加载
        L.newTable()
        val shapesIdx = L.getTop()
        L.newTable()
        L.pushJavaFunction(ShapesIndexHandler(L))
        L.setField(-2, "__index")
        // setMetaTable 在某些 LuaJava 版本中不弹出元表，需手动检查
        val beforeShapes = L.getTop()
        L.setMetaTable(shapesIdx)
        if (L.getTop() == beforeShapes) { L.pop(1) }
        L.setField(mtIdx, "shapes")
        // 重新推入 shapes 表，供 "shape" 别名使用
        L.getField(mtIdx, "shapes")
        L.setField(mtIdx, "shape") // 别名

        // MaterialTheme 作为可调用对象（空操作）
        L.newTable()
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                L.pushNil()
                return 1
            }
        })
        L.setField(-2, "__call")
        // setMetaTable 在某些 LuaJava 版本中不弹出元表，需手动检查
        val beforeMt = L.getTop()
        L.setMetaTable(mtIdx)
        if (L.getTop() == beforeMt) { L.pop(1) }

        L.setField(tableIdx, "MaterialTheme")
    }

    /**
     * colorScheme 的 __index 处理器
     * 从 ComposeScope.colorScheme 动态获取颜色值
     *
     * __index 接收 (table, key)，key 在索引 3，索引 2 是 table 自身
     */
    private class ColorSchemeIndexHandler(L: LuaState) : JavaFunction(L) {
        override fun execute(): Int {
            val key = try { L.toString(3) } catch (_: Exception) { L.pushNil(); return 1 }
            val scope = ComposeBridgeInstance.current.currentScope
            val cs = scope?.colorScheme ?: androidx.compose.material3.lightColorScheme()

            return when (key) {
                "primary" -> pushColor(L, cs.primary)
                "onPrimary" -> pushColor(L, cs.onPrimary)
                "primaryContainer" -> pushColor(L, cs.primaryContainer)
                "onPrimaryContainer" -> pushColor(L, cs.onPrimaryContainer)
                "secondary" -> pushColor(L, cs.secondary)
                "onSecondary" -> pushColor(L, cs.onSecondary)
                "secondaryContainer" -> pushColor(L, cs.secondaryContainer)
                "onSecondaryContainer" -> pushColor(L, cs.onSecondaryContainer)
                "tertiary" -> pushColor(L, cs.tertiary)
                "onTertiary" -> pushColor(L, cs.onTertiary)
                "tertiaryContainer" -> pushColor(L, cs.tertiaryContainer)
                "onTertiaryContainer" -> pushColor(L, cs.onTertiaryContainer)
                "error" -> pushColor(L, cs.error)
                "onError" -> pushColor(L, cs.onError)
                "errorContainer" -> pushColor(L, cs.errorContainer)
                "onErrorContainer" -> pushColor(L, cs.onErrorContainer)
                "background" -> pushColor(L, cs.background)
                "onBackground" -> pushColor(L, cs.onBackground)
                "surface" -> pushColor(L, cs.surface)
                "onSurface" -> pushColor(L, cs.onSurface)
                "surfaceVariant" -> pushColor(L, cs.surfaceVariant)
                "onSurfaceVariant" -> pushColor(L, cs.onSurfaceVariant)
                "outline" -> pushColor(L, cs.outline)
                "outlineVariant" -> pushColor(L, cs.outlineVariant)
                "inverseSurface" -> pushColor(L, cs.inverseSurface)
                "inverseOnSurface" -> pushColor(L, cs.inverseOnSurface)
                "inversePrimary" -> pushColor(L, cs.inversePrimary)
                "surfaceTint" -> pushColor(L, cs.surfaceTint)
                "scrim" -> pushColor(L, cs.scrim)
                else -> { L.pushNil(); return 1 }
            }
        }

        private fun pushColor(L: LuaState, color: androidx.compose.ui.graphics.Color): Int {
            L.pushJavaObject(color)
            return 1
        }
    }

    /**
     * typography 的 __index 处理器
     * 从 ComposeScope.typography 动态获取字体样式
     *
     * __index 接收 (table, key)，key 在索引 3，索引 2 是 table 自身
     */
    private class TypographyIndexHandler(L: LuaState) : JavaFunction(L) {
        override fun execute(): Int {
            val key = try { L.toString(3) } catch (_: Exception) { L.pushNil(); return 1 }
            val scope = ComposeBridgeInstance.current.currentScope
            val typo = scope?.typography ?: androidx.compose.material3.Typography()

            return when (key) {
                "displayLarge" -> { L.pushJavaObject(typo.displayLarge); 1 }
                "displayMedium" -> { L.pushJavaObject(typo.displayMedium); 1 }
                "displaySmall" -> { L.pushJavaObject(typo.displaySmall); 1 }
                "headlineLarge" -> { L.pushJavaObject(typo.headlineLarge); 1 }
                "headlineMedium" -> { L.pushJavaObject(typo.headlineMedium); 1 }
                "headlineSmall" -> { L.pushJavaObject(typo.headlineSmall); 1 }
                "titleLarge" -> { L.pushJavaObject(typo.titleLarge); 1 }
                "titleMedium" -> { L.pushJavaObject(typo.titleMedium); 1 }
                "titleSmall" -> { L.pushJavaObject(typo.titleSmall); 1 }
                "bodyLarge" -> { L.pushJavaObject(typo.bodyLarge); 1 }
                "bodyMedium" -> { L.pushJavaObject(typo.bodyMedium); 1 }
                "bodySmall" -> { L.pushJavaObject(typo.bodySmall); 1 }
                "labelLarge" -> { L.pushJavaObject(typo.labelLarge); 1 }
                "labelMedium" -> { L.pushJavaObject(typo.labelMedium); 1 }
                "labelSmall" -> { L.pushJavaObject(typo.labelSmall); 1 }
                else -> { L.pushNil(); 1 }
            }
        }
    }

    /**
     * shapes 的 __index 处理器
     * 从 ComposeScope.shapes 动态获取形状
     *
     * __index 接收 (table, key)，key 在索引 3，索引 2 是 table 自身
     */
    private class ShapesIndexHandler(L: LuaState) : JavaFunction(L) {
        override fun execute(): Int {
            val key = try { L.toString(3) } catch (_: Exception) { L.pushNil(); return 1 }
            val scope = ComposeBridgeInstance.current.currentScope
            val sh = scope?.shapes ?: androidx.compose.material3.Shapes()

            return when (key) {
                "extraSmall" -> { L.pushJavaObject(sh.extraSmall); 1 }
                "small" -> { L.pushJavaObject(sh.small); 1 }
                "medium" -> { L.pushJavaObject(sh.medium); 1 }
                "large" -> { L.pushJavaObject(sh.large); 1 }
                "extraLarge" -> { L.pushJavaObject(sh.extraLarge); 1 }
                else -> { L.pushNil(); 1 }
            }
        }
    }
}