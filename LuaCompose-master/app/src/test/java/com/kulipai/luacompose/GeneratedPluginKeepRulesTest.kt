package com.kulipai.luacompose

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedPluginKeepRulesTest {
    @Test
    fun composeKeepRulesRetainGeneratedPlugins() {
        val rulesFile = File("../compose/src/main/keepRules/rules.keep")

        assertTrue("compose keep rules file should exist", rulesFile.isFile)

        val rules = rulesFile.readText()
        assertTrue(
            "generated plugin package should be kept for reflective loading",
            rules.contains("-keep class com.kulipai.luacompose.generated.**")
        )
    }
}
