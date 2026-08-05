package com.kulipai.luacompose.compose.material3


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import com.kulipai.luacompose.compose.runtime.ComposeScriptPlugin
import com.kulipai.luacompose.compose.script.ScriptTable

class Material3Plugin : ComposeScriptPlugin {
    override val namespace: String? = "material3"

    override fun getComponents(): Map<String, @androidx.compose.runtime.Composable (props: Map<String, Any?>, childScope: com.kulipai.luacompose.compose.runtime.ComposeScope?) -> Unit> =
        emptyMap()

    override fun injectGlobals(scriptTable: ScriptTable) {
        val cardDefaultsTable = ComposeBridge.engine.createTable()
        cardDefaultsTable.set("cardColors", ComposeBridge.engine.createFunction { args ->
            val arg = args[0]
            val table = ComposeBridge.engine.createTable()
            table.set("_isCardColors", ComposeBridge.engine.createValue(true))
            if (arg.isTable()) {
                val luaMap = arg.asTable()
                val containerColor = luaMap.get("containerColor")
                val contentColor = luaMap.get("contentColor")
                if (!containerColor.isNil()) table.set("containerColor", containerColor)
                if (!contentColor.isNil()) table.set("contentColor", contentColor)
            }
            table
        })
        scriptTable.set("CardDefaults", cardDefaultsTable)

        val mtTable = ComposeBridge.engine.createTable()

        // --- ColorScheme ---
        val colorSchemeTable = ComposeBridge.engine.createTable()
        val colorSchemeMeta = ComposeBridge.engine.createTable()
        colorSchemeMeta.set("__index", ComposeBridge.engine.createFunction { args ->
            val key = args[1]
            val scope = ComposeBridge.getActiveScope()
            val cs = scope?.colorScheme ?: androidx.compose.material3.lightColorScheme()
            when (key.toStringValue()) {
                "primary" -> ComposeBridge.javaToScript(cs.primary)
                "onPrimary" -> ComposeBridge.javaToScript(cs.onPrimary)
                "primaryContainer" -> ComposeBridge.javaToScript(cs.primaryContainer)
                "onPrimaryContainer" -> ComposeBridge.javaToScript(cs.onPrimaryContainer)
                "secondary" -> ComposeBridge.javaToScript(cs.secondary)
                "onSecondary" -> ComposeBridge.javaToScript(cs.onSecondary)
                "secondaryContainer" -> ComposeBridge.javaToScript(cs.secondaryContainer)
                "onSecondaryContainer" -> ComposeBridge.javaToScript(cs.onSecondaryContainer)
                "tertiary" -> ComposeBridge.javaToScript(cs.tertiary)
                "onTertiary" -> ComposeBridge.javaToScript(cs.onTertiary)
                "tertiaryContainer" -> ComposeBridge.javaToScript(cs.tertiaryContainer)
                "onTertiaryContainer" -> ComposeBridge.javaToScript(cs.onTertiaryContainer)
                "error" -> ComposeBridge.javaToScript(cs.error)
                "errorContainer" -> ComposeBridge.javaToScript(cs.errorContainer)
                "onError" -> ComposeBridge.javaToScript(cs.onError)
                "onErrorContainer" -> ComposeBridge.javaToScript(cs.onErrorContainer)
                "background" -> ComposeBridge.javaToScript(cs.background)
                "onBackground" -> ComposeBridge.javaToScript(cs.onBackground)
                "surface" -> ComposeBridge.javaToScript(cs.surface)
                "onSurface" -> ComposeBridge.javaToScript(cs.onSurface)
                "surfaceVariant" -> ComposeBridge.javaToScript(cs.surfaceVariant)
                "onSurfaceVariant" -> ComposeBridge.javaToScript(cs.onSurfaceVariant)
                "outline" -> ComposeBridge.javaToScript(cs.outline)
                "inverseOnSurface" -> ComposeBridge.javaToScript(cs.inverseOnSurface)
                "inverseSurface" -> ComposeBridge.javaToScript(cs.inverseSurface)
                "inversePrimary" -> ComposeBridge.javaToScript(cs.inversePrimary)
                "surfaceTint" -> ComposeBridge.javaToScript(cs.surfaceTint)
                "outlineVariant" -> ComposeBridge.javaToScript(cs.outlineVariant)
                "scrim" -> ComposeBridge.javaToScript(cs.scrim)
                else -> ComposeBridge.engine.createNil()
            }
        })
        colorSchemeTable.setMetatable(colorSchemeMeta)
        mtTable.set("colorScheme", colorSchemeTable)
        mtTable.set("colors", colorSchemeTable) // Alias

        // --- Typography ---
        val typographyTable = ComposeBridge.engine.createTable()
        val typographyMeta = ComposeBridge.engine.createTable()
        typographyMeta.set("__index", ComposeBridge.engine.createFunction { args ->
            val key = args[1]
            val scope = ComposeBridge.getActiveScope()
            val typo = scope?.typography ?: androidx.compose.material3.Typography()
            when (key.toStringValue()) {
                "displayLarge" -> ComposeBridge.javaToScript(typo.displayLarge)
                "displayMedium" -> ComposeBridge.javaToScript(typo.displayMedium)
                "displaySmall" -> ComposeBridge.javaToScript(typo.displaySmall)
                "headlineLarge" -> ComposeBridge.javaToScript(typo.headlineLarge)
                "headlineMedium" -> ComposeBridge.javaToScript(typo.headlineMedium)
                "headlineSmall" -> ComposeBridge.javaToScript(typo.headlineSmall)
                "titleLarge" -> ComposeBridge.javaToScript(typo.titleLarge)
                "titleMedium" -> ComposeBridge.javaToScript(typo.titleMedium)
                "titleSmall" -> ComposeBridge.javaToScript(typo.titleSmall)
                "bodyLarge" -> ComposeBridge.javaToScript(typo.bodyLarge)
                "bodyMedium" -> ComposeBridge.javaToScript(typo.bodyMedium)
                "bodySmall" -> ComposeBridge.javaToScript(typo.bodySmall)
                "labelLarge" -> ComposeBridge.javaToScript(typo.labelLarge)
                "labelMedium" -> ComposeBridge.javaToScript(typo.labelMedium)
                "labelSmall" -> ComposeBridge.javaToScript(typo.labelSmall)
                else -> ComposeBridge.engine.createNil()
            }
        })
        typographyTable.setMetatable(typographyMeta)
        mtTable.set("typography", typographyTable)

        // --- Shapes ---
        val shapesTable = ComposeBridge.engine.createTable()
        val shapesMeta = ComposeBridge.engine.createTable()
        shapesMeta.set("__index", ComposeBridge.engine.createFunction { args ->
            val key = args[1]
            val scope = ComposeBridge.getActiveScope()
            val sh = scope?.shapes ?: androidx.compose.material3.Shapes()
            when (key.toStringValue()) {
                "extraSmall" -> ComposeBridge.javaToScript(sh.extraSmall)
                "small" -> ComposeBridge.javaToScript(sh.small)
                "medium" -> ComposeBridge.javaToScript(sh.medium)
                "large" -> ComposeBridge.javaToScript(sh.large)
                "extraLarge" -> ComposeBridge.javaToScript(sh.extraLarge)
                else -> ComposeBridge.engine.createNil()
            }
        })
        shapesTable.setMetatable(shapesMeta)
        mtTable.set("shapes", shapesTable)
        mtTable.set("shape", shapesTable) // Alias

        val materialThemeMeta = ComposeBridge.engine.createTable()
        materialThemeMeta.set("__call", ComposeBridge.engine.createFunction {
            ComposeBridge.engine.createNil()
        })
        mtTable.setMetatable(materialThemeMeta)

        scriptTable.set("MaterialTheme", mtTable)
    }

    internal fun resolveIcon(name: String): ImageVector {
        return when (name.lowercase()) {
            "refresh" -> Icons.Default.Refresh
            "add" -> Icons.Default.Add
            "settings" -> Icons.Default.Settings
            "favorite" -> Icons.Default.Favorite
            "close" -> Icons.Default.Close
            "menu" -> Icons.Default.Menu
            "home" -> Icons.Default.Home
            "search" -> Icons.Default.Search
            "person" -> Icons.Default.Person
            "arrowback", "arrow_back" -> Icons.AutoMirrored.Filled.ArrowBack
            "arrowforward", "arrow_forward" -> Icons.AutoMirrored.Filled.ArrowForward
            "check" -> Icons.Default.Check
            "info" -> Icons.Default.Info
            "star" -> Icons.Default.Star
            "share" -> Icons.Default.Share
            "delete" -> Icons.Default.Delete
            "edit" -> Icons.Default.Edit
            "play" -> Icons.Default.PlayArrow
            else -> Icons.Default.Info
        }
    }
}
