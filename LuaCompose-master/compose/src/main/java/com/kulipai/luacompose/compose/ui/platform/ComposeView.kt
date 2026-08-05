package com.kulipai.luacompose.compose.ui.platform

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import com.kulipai.luacompose.compose.runtime.ComposeScope
import com.kulipai.luacompose.compose.ui.graphics.ComposeScopeComponent
import com.kulipai.luacompose.compose.script.ScriptFunction

/**
 * A standard Android View that can render a Lua Compose function.
 * This allows integrating Lua Compose into standard Android XML layouts or View hierarchies.
 */
class ComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var composeScope: ComposeScope? = null

    /**
     * Sets the Lua function to be rendered by this view.
     */
    fun setContent(luaFunction: ScriptFunction) {
        // Reuse scope if the function is the same, or create a new one
        if (composeScope?.contentFunc != luaFunction) {
            composeScope = ComposeScope(luaFunction)
        }
        // Force recomposition
        disposeComposition()
        requestLayout()
    }

    var isDynamicColorEnabled: Boolean = true

    @Composable
    override fun Content() {
        val colorScheme = when {
            isDynamicColorEnabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                val context = androidx.compose.ui.platform.LocalContext.current
                if (androidx.compose.foundation.isSystemInDarkTheme()) {
                    androidx.compose.material3.dynamicDarkColorScheme(context)
                } else {
                    androidx.compose.material3.dynamicLightColorScheme(context)
                }
            }
            androidx.compose.foundation.isSystemInDarkTheme() -> androidx.compose.material3.darkColorScheme()
            else -> androidx.compose.material3.lightColorScheme()
        }

        androidx.compose.material3.MaterialTheme(
            colorScheme = colorScheme
        ) {
            composeScope?.let { scope ->
                ComposeScopeComponent(scope)
            }
        }
    }
}
