package com.kulipai.luacompose.compose.ui.graphics

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.kulipai.luacompose.compose.script.ScriptValue
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import com.kulipai.luacompose.compose.runtime.ComposeScope
import com.kulipai.luacompose.compose.runtime.ComposeNode
import com.kulipai.luacompose.compose.ui.resolveModifier
import com.kulipai.luacompose.compose.ui.LuaModifier
import com.kulipai.luacompose.compose.LuaComposeRegistry

@Composable
fun ComposeScopeComponent(scope: ComposeScope, parentComposeScope: Any? = null, vararg args: ScriptValue) {
    scope.coroutineScope = rememberCoroutineScope()
    scope.context = LocalContext.current
    scope.density = LocalDensity.current
    scope.configuration = LocalConfiguration.current

    val currentColorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val currentTypography = androidx.compose.material3.MaterialTheme.typography
    val currentShapes = androidx.compose.material3.MaterialTheme.shapes

    scope.colorScheme = currentColorScheme
    scope.typography = currentTypography
    scope.shapes = currentShapes
    
    for (plugin in LuaComposeRegistry.plugins) {
        plugin.injectLocals(scope)
    }

    val version by scope.recomposeVersion
    val nodes = remember(
        version,
        currentColorScheme,
        currentTypography,
        currentShapes,
        *args
    ) {
        if (parentComposeScope is androidx.compose.animation.SharedTransitionScope) {
            ComposeBridge.pushActiveSharedTransitionScope(parentComposeScope)
        }
        if (parentComposeScope is androidx.compose.animation.AnimatedVisibilityScope) {
            ComposeBridge.pushActiveAnimatedVisibilityScope(parentComposeScope)
        }
        try {
            scope.execute(*args)
        } finally {
            if (parentComposeScope is androidx.compose.animation.AnimatedVisibilityScope) {
                ComposeBridge.popActiveAnimatedVisibilityScope()
            }
            if (parentComposeScope is androidx.compose.animation.SharedTransitionScope) {
                ComposeBridge.popActiveSharedTransitionScope()
            }
        }
    }

    for (node in nodes) {
        ComposeNodeRenderer(node, parentComposeScope)
    }
}

@Composable
fun ComposeNodeRenderer(node: ComposeNode, parentComposeScope: Any? = null) {
    val childModifier = resolveModifier(node.props["modifier"])
    val alignmentStr = (node.props["modifier"] as? LuaModifier)?.alignmentStr
    val alignObject = (node.props["modifier"] as? LuaModifier)?.alignObject
    val weightVal = (node.props["modifier"] as? LuaModifier)?.weightVal
    val weightFill = (node.props["modifier"] as? LuaModifier)?.weightFill ?: true

    var finalModifier = if (alignObject != null && parentComposeScope != null) {
        when (parentComposeScope) {
            is androidx.compose.foundation.layout.BoxScope -> {
                if (alignObject is androidx.compose.ui.Alignment) {
                    with(parentComposeScope) { childModifier.align(alignObject) }
                } else childModifier
            }
            is androidx.compose.foundation.layout.ColumnScope -> {
                if (alignObject is androidx.compose.ui.Alignment.Horizontal) {
                    with(parentComposeScope) { childModifier.align(alignObject) }
                } else childModifier
            }
            is androidx.compose.foundation.layout.RowScope -> {
                if (alignObject is androidx.compose.ui.Alignment.Vertical) {
                    with(parentComposeScope) { childModifier.align(alignObject) }
                } else childModifier
            }
            else -> childModifier
        }
    } else if (alignmentStr != null && parentComposeScope != null) {
        when (parentComposeScope) {
            is androidx.compose.foundation.layout.BoxScope -> {
                val alignment = LuaComposeRegistry.resolveBoxAlignment(alignmentStr)
                with(parentComposeScope) { childModifier.align(alignment) }
            }

            is androidx.compose.foundation.layout.ColumnScope -> {
                val alignment = LuaComposeRegistry.resolveColumnAlignment(alignmentStr)
                with(parentComposeScope) { childModifier.align(alignment) }
            }

            is androidx.compose.foundation.layout.RowScope -> {
                val alignment = LuaComposeRegistry.resolveRowAlignment(alignmentStr)
                with(parentComposeScope) { childModifier.align(alignment) }
            }

            else -> childModifier
        }
    } else {
        childModifier
    }

    if (weightVal != null && parentComposeScope != null) {
        finalModifier = when (parentComposeScope) {
            is androidx.compose.foundation.layout.ColumnScope -> with(parentComposeScope) {
                finalModifier.weight(
                    weight = weightVal,
                    fill = weightFill
                )
            }

            is androidx.compose.foundation.layout.RowScope -> with(parentComposeScope) {
                finalModifier.weight(
                    weight = weightVal,
                    fill = weightFill
                )
            }

            else -> finalModifier
        }
    }

    val finalProps = node.props.toMutableMap()
    finalProps["modifier"] = finalModifier

    if (node.type == "LuaError") {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            androidx.compose.material3.Text(
                text = node.props["text"]?.toString() ?: "Unknown Script Error",
                color = Color.Red,
                modifier = Modifier.padding(16.dp),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val renderer = LuaComposeRegistry.components[node.type]
    if (renderer != null) {
        renderer(finalProps, node.childScope)
    } else {
        androidx.compose.material3.Text(
            text = "组件 [${node.type}] 未在注册表注册",
            color = Color.Red,
            modifier = Modifier.padding(8.dp)
        )
    }
}
