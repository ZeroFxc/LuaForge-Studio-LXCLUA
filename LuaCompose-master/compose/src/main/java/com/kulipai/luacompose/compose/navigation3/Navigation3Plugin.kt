package com.kulipai.luacompose.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import com.kulipai.luacompose.compose.script.ScriptValue
import com.kulipai.luacompose.compose.script.ScriptTable
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import com.kulipai.luacompose.compose.runtime.ComposeScriptPlugin
import com.kulipai.luacompose.compose.runtime.ComposeScope
import androidx.compose.ui.Modifier

class Navigation3Plugin : ComposeScriptPlugin {
    override val namespace: String = "navigation3"
    
    override fun getComponents(): Map<String, @Composable (props: Map<String, Any?>, childScope: ComposeScope?) -> Unit> {
        return mapOf(
            "NavDisplay" to { props, _ ->
                val backStack = props["backStack"] as? List<Any> ?: emptyList()
                val modifier = props["modifier"] as? Modifier ?: Modifier
                
                // Get Lua provided decorators, filtering out the dummy string tokens
                val luaDecoratorsRaw = props["entryDecorators"] as? List<*> ?: emptyList<Any>()
                @Suppress("UNCHECKED_CAST")
                val actualDecorators = luaDecoratorsRaw.filterIsInstance<NavEntryDecorator<*>>().map { it as NavEntryDecorator<Any> }.toMutableList()
                
                // If they used the dummy tokens, instantiate the real composables here!
                if (luaDecoratorsRaw.contains("SAVEABLE_STATE_HOLDER")) {
                    actualDecorators.add(androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator<Any>())
                }
                // VIEW_MODEL_STORE is omitted due to missing lifecycle-viewmodel-navigation3 dependency
                
                val providerUserdata = props["entryProvider"]
                val provider = providerUserdata as? ((Any) -> NavEntry<Any>)
                
                android.util.Log.d("LUA_NAVDISPLAY", "NavDisplay called. backStack size: ${backStack.size}, providerUserdata type: ${providerUserdata?.javaClass?.name}, provider: $provider")
                
                if (provider != null) {
                    androidx.navigation3.ui.NavDisplay(
                        backStack = backStack,
                        modifier = modifier,
                        entryDecorators = actualDecorators,
                        entryProvider = provider
                    )
                }
            }
        )
    }
    
    override fun injectGlobals(scriptTable: ScriptTable) {
        scriptTable.set("entryProvider", ComposeBridge.engine.createFunction { args ->
            val firstArg = args.getOrNull(0) as? ScriptValue
            val luaTable = firstArg?.asTable() ?: error("entryProvider expects a Lua Table")
            val activeScope = ComposeBridge.getActiveScope() ?: error("entryProvider must be called in Compose context")
            val provider: (Any) -> NavEntry<*> = { keyRaw ->
                val scriptKey = ComposeBridge.javaToScript(keyRaw)
                val routeName = if (scriptKey.isString()) {
                    scriptKey.toStringValue()
                } else if (scriptKey.isTable()) {
                    val r = scriptKey.asTable().get("route")
                    if (r.isString()) r.toStringValue() else error("Key table must have a 'route' string field")
                } else {
                    error("Invalid Lua NavKey: $keyRaw. Expected String or Table with 'route'.")
                }
                
                val luaFunc = luaTable.get(routeName)
                if (!luaFunc.isFunction()) {
                    error("No Composable function found for route: $routeName")
                }
                
                val childScope = activeScope.getOrCreateChildScope(luaFunc.asFunction())
                
                NavEntry(keyRaw) {
                    com.kulipai.luacompose.compose.ui.graphics.ComposeScopeComponent(childScope, null, scriptKey)
                }
            }
            ComposeBridge.engine.createUserdata(provider)
        })

        scriptTable.set("rememberSaveableStateHolderNavEntryDecorator", ComposeBridge.engine.createFunction {
            ComposeBridge.engine.createValue("SAVEABLE_STATE_HOLDER")
        })

        scriptTable.set("rememberViewModelStoreNavEntryDecorator", ComposeBridge.engine.createFunction {
            ComposeBridge.engine.createValue("VIEW_MODEL_STORE")
        })

        scriptTable.set("rememberNavBackStack", ComposeBridge.engine.createFunction { args ->
            val scope = ComposeBridge.getActiveScope() ?: error("rememberNavBackStack must be called in Compose context")
            
            val initialKeysArg = args.getOrNull(0)
            val initialKeys = mutableListOf<Any>()
            
            if (initialKeysArg != null && initialKeysArg.isTable()) {
                val table = initialKeysArg.asTable()
                val len = table.length()
                for (i in 1..len) {
                    val v = table.get(i)
                    if (v.isString()) {
                        initialKeys.add(v.toStringValue())
                    } else {
                        initialKeys.add(v)
                    }
                }
            } else if (initialKeysArg != null && initialKeysArg.isString()) {
                initialKeys.add(initialKeysArg.toStringValue())
            }

            val actualKey = scope.remembersCount++
            val oldKeys = scope.rememberKeys[actualKey]
            
            if (!scope.remembers.containsKey(actualKey) || oldKeys != initialKeys) {
                val list = mutableStateListOf<Any>()
                list.addAll(initialKeys)
                scope.rememberKeys[actualKey] = initialKeys
                scope.remembers[actualKey] = list
            }
            ComposeBridge.javaToScript(scope.remembers[actualKey])
        })
    }
}
