package com.kulipai.luacompose

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kulipai.luacompose.compose.ui.LuaModifier
import com.kulipai.luacompose.compose.runtime.ComposeScope
import com.kulipai.luacompose.compose.ui.graphics.ComposeScopeComponent
import com.kulipai.luacompose.ui.theme.LuaComposeTheme
import com.kulipai.luacompose.compose.script.LuajEngine
import com.kulipai.luacompose.compose.LuaComposeLib
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import org.luaj.Globals
import org.luaj.LuaFunction
import org.luaj.LuaValue
import org.luaj.lib.ZeroArgFunction
import org.luaj.lib.jse.CoerceJavaToLua
import org.luaj.lib.jse.JsePlatform
import java.io.File
import android.util.Log
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        setContent {
            LuaComposeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        LuaAppRunner(this@MainActivity)
                    }
                }
            }
        }
    }
}

@Composable
fun LuaAppRunner(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var hotReloadTrigger by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var rootScope by remember { mutableStateOf<ComposeScope?>(null) }
    var globalGlobals by remember { mutableStateOf<Globals?>(null) }
    var isHotReloadEnabled by remember { mutableStateOf(true) }

    val externalDir = remember(context) {
        context.getExternalFilesDir(null)
    }

    // 自动监听 main.lua 文件变化并触发热重载
    DisposableEffect(externalDir) {
        if (externalDir == null) return@DisposableEffect onDispose {}

        val observer = object : android.os.FileObserver(
            externalDir.absolutePath,
            android.os.FileObserver.CLOSE_WRITE
        ) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "main.lua") {
                    Log.d("LUA_HOT_RELOAD", "Detected CLOSE_WRITE on main.lua, auto-reloading (hot=${isHotReloadEnabled})...")
                    coroutineScope.launch {
                        if (isHotReloadEnabled) {
                            hotReloadTrigger++
                        } else {
                            reloadTrigger++
                        }
                    }
                }
            }
        }
        observer.startWatching()
        Log.d("LUA_HOT_RELOAD", "Started FileObserver watching dir: ${externalDir.absolutePath}")

        onDispose {
            observer.stopWatching()
            Log.d("LUA_HOT_RELOAD", "Stopped FileObserver")
        }
    }

    // Full Reload (button)
    LaunchedEffect(reloadTrigger) {
        try {
            errorMessage = null
            val result = loadLuaScope(context, null, null)
            globalGlobals = result.first
            rootScope = result.second
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = e.message ?: e.toString()
            rootScope = null
            globalGlobals = null
        }
    }

    // Hot Reload (file save)
    LaunchedEffect(hotReloadTrigger) {
        if (hotReloadTrigger == 0) return@LaunchedEffect
        try {
            errorMessage = null
            val result = loadLuaScope(context, globalGlobals, rootScope)
            globalGlobals = result.first
            rootScope = result.second
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = e.message ?: e.toString()
            // In case of error during hot reload, keep the old UI visible instead of completely breaking,
            // or show the error message. Here we show error.
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 自定义顶部状态栏（无需使用实验性 TopAppBar 接口）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lua Compose",
                style = MaterialTheme.typography.titleLarge
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Live", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = isHotReloadEnabled,
                    onCheckedChange = { isHotReloadEnabled = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { reloadTrigger++ }
                ) {
                    Text("Reload")
                }
            }
        }

        HorizontalDivider()

        // 渲染主布局
        Box(modifier = Modifier.fillMaxSize()) {
            if (errorMessage != null) {
                Text(
                    text = "加载错误:\n$errorMessage",
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                rootScope?.let {
                    key(rootScope) {
                        ComposeScopeComponent(it)
                    }
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// 动态读取并加载 Lua 执行环境
fun loadLuaScope(context: Context, existingGlobals: Globals?, existingScope: ComposeScope?): Pair<Globals, ComposeScope> {
    try {
        // 1. 初始化 luaj++ 虚拟机环境
        val globals = existingGlobals ?: JsePlatform.standardGlobals().also { g ->
            // 2. 初始化 Kotlin 侧实现的 Compose DSL 库
            ComposeBridge.engine = LuajEngine
            ComposeBridge.luaValueUnwrapper = { value ->
                if (value is org.luaj.LuaValue) {
                    ComposeBridge.scriptToJava(LuajEngine.wrap(value))
                } else value
            }
            val env = LuajEngine.wrap(g).asTable()
            LuaComposeLib.inject(env)
        }

        // 4. 定位外置存储路径 /sdcard/Android/data/<packagename>/files/
        val externalDir = context.getExternalFilesDir(null)
            ?: throw RuntimeException("外置存储不可用")
        if (!externalDir.exists()) {
            externalDir.mkdirs()
        }
        val mainLuaFile = File(externalDir, "main.lua")

        // 如果主测试文件不存在，自动写入一个功能完整的示例
        if (!mainLuaFile.exists()) {
            val sampleCode = """
            -- main.lua
            local compose = compose
            local Column = compose.foundation.layout.Column
            local Row = compose.foundation.layout.Row
            local Text = compose.material3.Text
            local Button = compose.material3.Button
            local Spacer = compose.foundation.layout.Spacer
            local TextField = compose.material3.TextField
            
            -- 直接使用 setContent 加载布局，无需在末尾使用 return
            compose.setContent(function()
              -- 创建响应式状态
              local count = compose.state(0)
              local textInput = compose.state("Hello AndroLua")
            
              Column {
                modifier = Modifier().fillMaxSize().padding(16),
                content = function()
                  
                  Text {
                    text = "欢迎使用 AndroLua Compose！",
                    color = "#6200EE",
                    modifier = Modifier().padding(8)
                  }
            
                  Spacer { modifier = Modifier().height(16) }
            
                  -- 文本框双向绑定示例
                  TextField {
                    value = textInput,
                    onValueChange = function(newVal)
                      textInput.value = newVal
                    end,
                    modifier = Modifier().fillMaxWidth()
                  }
            
                  Spacer { modifier = Modifier().height(8) }
            
                  Text {
                    text = "你输入的文本是: " .. tostring(textInput)
                  }
            
                  Spacer { modifier = Modifier().height(24) }
            
                  -- 计数器示例
                  Row {
                    modifier = Modifier().fillMaxWidth(),
                    content = function()
                      Text {
                        text = "计数器当前值: " .. tostring(count),
                        modifier = Modifier().padding(8)
                      }
            
                      Button {
                        onClick = function()
                          count.value = count.value + 1
                        end,
                        content = function()
                          Text { text = "加 1" }
                        end
                      }
                    end
                  }
                  
                end
              }
            end)
            """.trimIndent()
            mainLuaFile.writeText(sampleCode)
        }

        // 5. 加载并运行主 Lua 脚本
        val scriptContent = mainLuaFile.readText()
        Log.d(
            "LUA_SCRIPT",
            "Loading ${mainLuaFile.absolutePath} bytes=${scriptContent.length} firstLine=${scriptContent.lineSequence().firstOrNull()}"
        )
        
        // Clear root function before load so we get the new one
        LuaComposeLib.rootContentFunc = null
        val userScriptResult = globals.load(scriptContent, "main.lua").call()

        // 6. 优先从 compose.rootContentFunc 读取布局函数，其次从脚本返回值中读取
        val rootLuaFunction = LuaComposeLib.rootContentFunc
            ?: (LuajEngine.wrap(userScriptResult).takeIf { it.isFunction() }?.asFunction())
            ?: throw RuntimeException("请使用 compose.setContent(function) 设置布局，或者在 main.lua 结尾返回布局函数")

        if (existingScope != null) {
            // 热重载逻辑
            existingScope.contentFunc = rootLuaFunction
            
            // 重新启动 LaunchedEffect 任务，以便应用速度等新逻辑
            existingScope.restartLaunchedEffects()
            
            existingScope.invalidate() // Trigger recomposition
            return Pair(globals, existingScope)
        } else {
            // 全新重载逻辑
            LuaComposeLib.clearRuntimeState()
            return Pair(globals, ComposeScope(rootLuaFunction))
        }
    } catch (e: Exception) {
        if (existingGlobals == null) {
            LuaComposeLib.clearRuntimeState()
        }
        throw e
    }
}
