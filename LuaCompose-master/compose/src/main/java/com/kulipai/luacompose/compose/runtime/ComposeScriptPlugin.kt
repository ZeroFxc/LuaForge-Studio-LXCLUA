package com.kulipai.luacompose.compose.runtime

import androidx.compose.runtime.Composable
import com.kulipai.luacompose.compose.script.ScriptTable

interface ComposeScriptPlugin {
    val namespace: String?
    fun getComponents(): Map<String, @Composable (props: Map<String, Any?>, childScope: ComposeScope?) -> Unit>
    fun injectGlobals(scriptTable: ScriptTable)
    @Composable
    fun injectLocals(scope: ComposeScope) {}
}
