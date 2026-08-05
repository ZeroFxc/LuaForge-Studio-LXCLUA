package com.kulipai.luacompose

import com.kulipai.luacompose.compose.script.LuajEngine
import com.kulipai.luacompose.compose.LuaComposeLib
import com.kulipai.luacompose.compose.runtime.ComposeScope
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.Globals
import org.luaj.lib.jse.JsePlatform

class LuaComposeLibExportTest {
    @Test
    fun generatedPluginRegistryClassIsLoadable() {
        assertTrue(
            runCatching {
                Class.forName("com.kulipai.luacompose.generated.GeneratedPluginRegistry")
            }.isSuccess
        )
    }

    @Test
    fun generatedFoundationPluginClassIsLoadable() {
        assertTrue(
            runCatching {
                Class.forName("com.kulipai.luacompose.generated.FoundationGeneratedPlugin")
            }.isSuccess
        )
    }

    @Test
    fun foundationLayoutComponentsAreExportedToLua() {
        val globals = Globals()
        ComposeBridge.engine = LuajEngine
        ComposeBridge.luaValueUnwrapper = { value -> value }

        val env = LuajEngine.wrap(globals).asTable()
        LuaComposeLib.inject(env)

        val compose = env.get("compose").asTable()
        val foundation = compose.get("foundation").asTable()
        val layout = foundation.get("layout").asTable()
        val material3 = compose.get("material3").asTable()

        assertTrue(foundation.get("Canvas").isFunction())
        assertTrue(layout.get("Column").isFunction())
        assertTrue(layout.get("Row").isFunction())
        assertTrue(material3.get("Text").isFunction())
    }

    @Test
    fun injectClearsPreviousRootContentState() {
        val globals = Globals()
        ComposeBridge.engine = LuajEngine
        ComposeBridge.luaValueUnwrapper = { value -> value }

        LuaComposeLib.rootContentFunc = LuajEngine.createFunction { LuajEngine.createNil() }
        LuaComposeLib.globalEnv = LuajEngine.createTable()

        val env = LuajEngine.wrap(globals).asTable()
        LuaComposeLib.inject(env)

        assertNull(LuaComposeLib.rootContentFunc)
        assertEquals(env, LuaComposeLib.globalEnv)
    }

    @Test
    fun clearRuntimeStateResetsGlobalScriptReferences() {
        ComposeBridge.engine = LuajEngine
        ComposeBridge.luaValueUnwrapper = { value -> value }

        LuaComposeLib.rootContentFunc = LuajEngine.createFunction { LuajEngine.createNil() }
        LuaComposeLib.globalEnv = LuajEngine.createTable()

        LuaComposeLib.clearRuntimeState()

        assertNull(LuaComposeLib.rootContentFunc)
        assertNull(LuaComposeLib.globalEnv)
    }

    @Test
    fun luaScriptCanReadFoundationLayoutColumn() {
        val globals = JsePlatform.standardGlobals()
        ComposeBridge.engine = LuajEngine
        ComposeBridge.luaValueUnwrapper = { value ->
            if (value is org.luaj.LuaValue) {
                ComposeBridge.scriptToJava(LuajEngine.wrap(value))
            } else {
                value
            }
        }

        val env = LuajEngine.wrap(globals).asTable()
        LuaComposeLib.inject(env)

        val result = globals.load(
            """
            local compose = compose
            local Column = compose.foundation.layout.Column
            return Column ~= nil
            """.trimIndent(),
            "main.lua"
        ).call()

        assertEquals(true, result.toboolean())
    }

    @Test
    fun luaScriptCanReadFoundationCanvas() {
        val globals = JsePlatform.standardGlobals()
        ComposeBridge.engine = LuajEngine
        ComposeBridge.luaValueUnwrapper = { value ->
            if (value is org.luaj.LuaValue) {
                ComposeBridge.scriptToJava(LuajEngine.wrap(value))
            } else {
                value
            }
        }

        val env = LuajEngine.wrap(globals).asTable()
        LuaComposeLib.inject(env)

        val result = globals.load(
            """
            local compose = compose
            local Canvas = compose.foundation.Canvas
            return Canvas ~= nil
            """.trimIndent(),
            "main.lua"
        ).call()

        assertEquals(true, result.toboolean())
    }

    @Test
    fun luaScriptCannotReadTopLevelCanvasAlias() {
        val globals = JsePlatform.standardGlobals()
        ComposeBridge.engine = LuajEngine
        ComposeBridge.luaValueUnwrapper = { value ->
            if (value is org.luaj.LuaValue) {
                ComposeBridge.scriptToJava(LuajEngine.wrap(value))
            } else {
                value
            }
        }

        val env = LuajEngine.wrap(globals).asTable()
        LuaComposeLib.inject(env)

        val result = globals.load(
            """
            local compose = compose
            return compose.Canvas == nil
            """.trimIndent(),
            "main.lua"
        ).call()

        assertEquals(true, result.toboolean())
    }

    @Test
    fun luaScriptCanReadMaterialThemeToolTable() {
        val globals = JsePlatform.standardGlobals()
        ComposeBridge.engine = LuajEngine
        ComposeBridge.luaValueUnwrapper = { value ->
            if (value is org.luaj.LuaValue) {
                ComposeBridge.scriptToJava(LuajEngine.wrap(value))
            } else {
                value
            }
        }

        val env = LuajEngine.wrap(globals).asTable()
        LuaComposeLib.inject(env)

        val result = globals.load(
            """
            local compose = compose
            local material3 = compose.material3
            local MaterialTheme = material3.MaterialTheme
            return MaterialTheme ~= nil
                and MaterialTheme.typography ~= nil
                and MaterialTheme.shapes ~= nil
                and MaterialTheme.colorScheme ~= nil
            """.trimIndent(),
            "main.lua"
        ).call()

        assertEquals(true, result.toboolean())
    }

    @Test
    fun luaScriptCanReadSharedTransitionApis() {
        val globals = JsePlatform.standardGlobals()
        ComposeBridge.engine = LuajEngine
        ComposeBridge.luaValueUnwrapper = { value ->
            if (value is org.luaj.LuaValue) {
                ComposeBridge.scriptToJava(LuajEngine.wrap(value))
            } else {
                value
            }
        }

        val env = LuajEngine.wrap(globals).asTable()
        LuaComposeLib.inject(env)

        val result = globals.load(
            """
            local compose = compose
            return compose.animation.SharedTransitionLayout ~= nil
                and compose.animation.AnimatedContent ~= nil
                and compose.animation.rememberSharedContentState ~= nil
            """.trimIndent(),
            "main.lua"
        ).call()

        assertEquals(true, result.toboolean())
    }

    @Test
    fun luaScriptCanUseComposeWithAndColorCopyApis() {
        val globals = JsePlatform.standardGlobals()
        ComposeBridge.engine = LuajEngine
        ComposeBridge.luaValueUnwrapper = { value ->
            if (value is org.luaj.LuaValue) {
                ComposeBridge.scriptToJava(LuajEngine.wrap(value))
            } else {
                value
            }
        }

        val env = LuajEngine.wrap(globals).asTable()
        LuaComposeLib.inject(env)

        val result = globals.load(
            """
            local compose = compose
            local color = compose.ui.graphics.Color.White:copy({ alpha = 0.85 })
            return compose["with"] ~= nil and color ~= nil
            """.trimIndent(),
            "main.lua"
        ).call()

        assertEquals(true, result.toboolean())
    }

    @Test
    fun rootContentFunctionKeepsFoundationLayoutUpvaluesAfterScriptReturns() {
        val globals = JsePlatform.standardGlobals()
        ComposeBridge.engine = LuajEngine
        ComposeBridge.luaValueUnwrapper = { value ->
            if (value is org.luaj.LuaValue) {
                ComposeBridge.scriptToJava(LuajEngine.wrap(value))
            } else {
                value
            }
        }

        val env = LuajEngine.wrap(globals).asTable()
        LuaComposeLib.inject(env)

        globals.load(
            """
            local compose = compose
            local Column = compose.foundation.layout.Column
            compose.setContent(function()
                Column {}
            end)
            """.trimIndent(),
            "main.lua"
        ).call()

        val rootLuaFunction = LuaComposeLib.rootContentFunc
        check(rootLuaFunction != null) { "rootContentFunc should be captured by compose.setContent" }

        LuaComposeLib.clearRuntimeState()

        val nodes = ComposeScope(rootLuaFunction).execute()

        assertEquals(1, nodes.size)
        assertEquals("foundation.layout.Column", nodes.single().type)
    }

    @Test
    fun defaultSampleScriptUsesFoundationLayoutNamespace() {
        val mainActivity = File("src/main/java/com/kulipai/luacompose/MainActivity.kt")
        check(mainActivity.isFile) { "Missing MainActivity source at ${mainActivity.absolutePath}" }

        val source = mainActivity.readText()

        assertTrue(source.contains("local Column = compose.foundation.layout.Column"))
        assertTrue(source.contains("local Row = compose.foundation.layout.Row"))
        assertTrue(source.contains("local Spacer = compose.foundation.layout.Spacer"))
        assertFalse(source.contains("local Column = compose.foundation.Column"))
        assertFalse(source.contains("local Row = compose.foundation.Row"))
        assertFalse(source.contains("local Spacer = compose.foundation.Spacer"))
    }
}
