# Android 应用开发指南

## 1. 开发环境搭建

### 1.1 系统要求

| 工具 | 推荐版本 | 最低版本 |
|------|---------|---------|
| Android Studio | Hedgehog (2023.1.1) | Flamingo (2022.2.1) |
| JDK | 17 | 17 |
| Android SDK | API 36 | API 24 (min), 35 (target) |
| NDK | 29.0.13004108 | 27.0 |
| CMake | 3.22+ | 3.18+ |
| Gradle | 9.1.0 | 8.0+ |

### 1.2 新建开发环境步骤

1. 安装 Android Studio 并导入项目
2. 打开 `Tools` → `SDK Manager`，确保安装：
   - Android SDK Platform 36
   - NDK (Side by side) 29.0.13004108
   - CMake 3.22+
3. 等待 Gradle 同步完成
4. 选择设备（模拟器或真机）并运行

---

## 2. 项目结构详解

### 2.1 Kotlin 源码目录

```
app/src/main/kotlin/com/luaforge/studio/lxclua/
├── MainActivity.kt           # 应用入口
├── HomeScreen.kt             # 主页面
├── ProjectCard.kt            # 项目卡片组件
├── AppDrawer.kt              # 导航抽屉
│
├── ui/                       # UI 组件包
│   ├── editor/               # 编辑器
│   │   ├── CodeEditScreen.kt         # 代码编辑界面
│   │   ├── EditorViewModel.kt        # 编辑器状态管理
│   │   ├── EditorTabs.kt             # 标签页管理
│   │   ├── EditorToolbars.kt         # 工具栏
│   │   ├── SearchPanel.kt            # 搜索面板
│   │   ├── EditorDialogs.kt          # 编辑器对话框
│   │   ├── designer/                 # 可视化设计器
│   │   │   ├── DesignerHost.kt       # 设计器宿主
│   │   │   ├── DesignerMode.kt       # 设计模式
│   │   │   ├── PreviewCanvas.kt      # 预览画布
│   │   │   ├── PropertyPanel.kt      # 属性面板
│   │   │   └── ComponentPalette.kt   # 组件面板
│   │   ├── components/               # 自定义组件
│   │   ├── persistence/              # 持久化
│   │   └── bridge/                   # 编辑器桥接
│   │
│   ├── settings/             # 设置界面
│   │   ├── SettingsScreen.kt         # 设置主界面
│   │   ├── SettingsManager.kt        # 设置管理器
│   │   ├── AISettings.kt             # AI 设置
│   │   └── MCPSettings.kt            # MCP 设置
│   │
│   ├── git/                  # Git 集成
│   │   ├── GitScreen.kt              # Git 界面
│   │   └── GitDialogs.kt             # Git 对话框
│   │
│   ├── plugin/               # 插件管理界面
│   │   ├── PluginScreen.kt           # 插件列表
│   │   └── PluginManagementScreen.kt # 插件管理
│   │
│   ├── plugin/               # 插件系统
│   │   ├── plugin/ui/                # 插件 UI
│   │   ├── plugin/data/              # 插件数据
│   │   ├── plugin/mcp/               # MCP 服务
│   │   ├── plugin/floating/          # 悬浮窗
│   │   └── plugin/loaders/           # 插件加载器
│   │
│   ├── analyse/              # 分析界面
│   ├── components/           # 可复用组件
│   ├── javaapi/             # Java API 查看
│   └── about/               # 关于界面
│
├── langs/lua/               # Lua 语言支持
│   ├── LuaLanguage.kt       # 语言定义
│   ├── LuaTextTokenizer.kt  # 文本分词器
│   ├── LuaIncrementalAnalyzeManager.kt # 增量分析
│   ├── completion/          # 自动补全
│   ├── format/              # 代码格式化
│   └── tools/               # Lua 工具
│
├── utils/                   # 工具类
│   ├── FileUtil.kt          # 文件工具
│   ├── ProjectUtil.kt       # 项目工具
│   ├── ShortcutHelper.kt    # 快捷键
│   └── IconManager.kt       # 图标管理
│
├── files/                   # 文件管理
│   └── FileTree.kt          # 文件树
│
├── git/                     # Git 管理
│   └── GitManager.kt        # Git 操作封装
│
├── mcp/                     # MCP (Model Context Protocol)
│   ├── MCPService.kt        # MCP 服务主类
│   ├── MCPServerConfig.kt   # 服务器配置
│   ├── MCPLocalServer.kt    # 本地服务器
│   ├── KeepAliveService.kt  # 保活服务
│   └── MCPBroadcastService.kt # 广播服务
│
├── plugin/                  # 插件系统核心
│   ├── PluginManager.kt     # 插件管理器
│   ├── api/                 # 插件 API 接口
│   │   ├── IPlugin.kt               # 插件主接口
│   │   ├── IPluginBridge.kt         # 插件桥接
│   │   └── IPluginBridge*.kt        # 各功能扩展接口
│   ├── bridge/              # 桥接实现
│   ├── state/               # 插件状态
│   └── loaders/             # 加载器
│       ├── LuaPluginLoader.kt       # Lua 插件加载
│       └── DexPluginLoader.kt       # Dex 插件加载
│
└── ai/                      # AI 集成
    ├── AIService.kt         # AI 服务
    └── AIConfig.kt          # AI 配置
```

---

## 3. 核心模块详解

### 3.1 MainActivity

**文件**: `MainActivity.kt`

应用入口，负责初始化核心组件：

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 Lua VM
        LuaForge.init(this)
        
        // 初始化插件系统
        PluginManager.init(this)
        
        // 初始化 MCP 服务
        MCPService.init(this)
        
        // 设置 Compose UI
        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}
```

**主要职责**:
- 全局组件初始化
- 权限管理与请求
- 生命周期事件处理
- 深度链接解析

### 3.2 编辑器模块 (Editor)

#### 3.2.1 架构概览

```
CodeEditScreen (Compose UI)
    │
    ├── EditorViewModel (状态)
    │   ├── EditorState (不可变状态)
    │   │   ├── openFiles: List<FileState>     # 打开的文件
    │   │   ├── activeFile: FileState?         # 当前活动文件
    │   │   ├── cursorPosition: CursorPosition # 光标位置
    │   │   ├── diagnostics: List<Diagnostic>  # 诊断信息
    │   │   └── ... 
    │   │
    │   ├── 业务方法
    │   │   ├── openFile(path: String)
    │   │   ├── saveFile()
    │   │   ├── executeScript()
    │   │   └── ...
    │   │
    │   └── 依赖
    │       ├── ProjectRepository
    │       ├── LuaExecutor (JNI 调用)
    │       └── LuaFormatter
    │
    ├── UI 组件
    │   ├── EditorTabs (标签栏)
    │   ├── CodeEditorView (编辑器视图)
    │   ├── EditorToolbars (工具栏)
    │   └── SearchPanel (搜索面板)
    │
    └── 可视化设计器 (可选)
        ├── DesignerHost
        ├── PreviewCanvas
        └── PropertyPanel
```

#### 3.2.2 Lua 脚本执行流程

```kotlin
// EditorViewModel.kt

fun executeScript(code: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            _state.update { it.copy(isExecuting = true) }
            
            // 通过 JNI 调用 Lua VM
            val result = luaExecutor.execute(code)
            
            _state.update { 
                it.copy(
                    isExecuting = false,
                    executionResult = result,
                    consoleOutput = it.consoleOutput + result
                )
            }
        } catch (e: LuaException) {
            _state.update {
                it.copy(
                    isExecuting = false,
                    diagnostics = it.diagnostics + Diagnostic.error(e.message)
                )
            }
        }
    }
}
```

### 3.3 插件系统

#### 3.3.1 插件类型

| 类型 | 扩展名 | 加载器 | 说明 |
|------|--------|--------|------|
| Lua 插件 | `.lua`, `.luac` | `LuaPluginLoader` | 纯 Lua 脚本，最安全 |
| Dex 插件 | `.dex`, `.apk` | `DexPluginLoader` | 编译后的 Java/Kotlin 代码 |

#### 3.3.2 插件结构

```
插件目录/
├── plugin.json              # 插件清单
├── main.lua                 # 入口脚本
├── components/              # 自定义组件
├── assets/                  # 插件资源
└── docs/                    # 插件文档
```

#### 3.3.3 插件清单 (plugin.json)

```json
{
  "name": "示例插件",
  "id": "com.example.plugin",
  "version": "1.0.0",
  "author": "作者名",
  "description": "插件描述",
  "minAppVersion": "1.3.0",
  "entry": "main.lua",
  "permissions": [
    "file.read",
    "file.write",
    "network.http",
    "lua.execute"
  ],
  "bridges": [
    "editor",
    "ui",
    "lua"
  ],
  "contributes": {
    "commands": [
      {
        "command": "helloWorld",
        "title": "你好世界"
      }
    ],
    "views": [
      {
        "id": "sidebar",
        "title": "侧边栏",
        "location": "sidebar"
      }
    ]
  }
}
```

#### 3.3.4 插件 API

```lua
-- main.lua 插件入口

-- 获取桥接对象
local bridge = plugin:getBridge("editor")

-- 注册命令
bridge:registerCommand("helloWorld", function()
    bridge:showMessage("Hello from plugin!")
end)

-- 监听事件
bridge:on("fileOpened", function(file)
    print("File opened: " .. file.path)
end)

-- 添加侧边栏视图
local sidebar = plugin:getBridge("ui"):getView("sidebar")
sidebar:addButton("Click Me", function()
    -- 按钮点击处理
end)
```

### 3.4 MCP 集成

#### 3.4.1 MCP 服务架构

```
MCPService (管理多个 MCP 服务器)
    │
    ├── MCPServerConfig (服务器配置)
    │   ├── name: String
    │   ├── command: String (stdio 模式)
    │   ├── url: String (SSE 模式)
    │   └── ...
    │
    ├── 已连接服务器
    │   ├── MCPClient1
    │   │   - tools: List<Tool>
    │   │   - callTool(name, args)
    │   │   - listResources()
    │   │   ...
    │   └── MCPClient2
    │       ...
    │
    └── AI 调用接口
        ├── chatWithAITool(tool_name, params)
        └── ...
```

#### 3.4.2 使用 MCP

```kotlin
// 调用 MCP 工具
val result = MCPService.callTool(
    serverName = "fs",
    toolName = "readFile",
    arguments = mapOf("path" to "/path/to/file.txt")
)
```

### 3.5 Git 集成

#### 3.5.1 GitManager 架构

```kotlin
class GitManager(private val projectDir: File) {
    
    // 初始化仓库
    suspend fun init(): Result<Unit>
    
    // 克隆仓库
    suspend fun clone(url: String, branch: String? = null): Result<Unit>
    
    // 获取状态
    suspend fun status(): Result<GitStatus>
    
    // 暂存文件
    suspend fun add(paths: List<String>): Result<Unit>
    
    // 提交更改
    suspend fun commit(message: String): Result<Unit>
    
    // 分支操作
    suspend fun branches(): Result<List<Branch>>
    suspend fun checkout(branch: String): Result<Unit>
    suspend fun createBranch(branch: String): Result<Unit>
    
    // 远程操作
    suspend fun pull(): Result<Unit>
    suspend fun push(): Result<Unit>
    suspend fun fetch(): Result<Unit>
    
    // 日志
    suspend fun log(count: Int = 20): Result<List<Commit>>
    suspend fun diff(commitHash: String?): Result<String>
}
```

#### 3.5.2 使用示例

```kotlin
class GitViewModel : ViewModel() {
    
    private val gitManager = GitManager(projectDir)
    
    fun loadStatus() {
        viewModelScope.launch {
            val status = gitManager.status().getOrThrow()
            _state.update { it.copy(gitStatus = status) }
        }
    }
    
    fun commitChanges(message: String, files: List<String>) {
        viewModelScope.launch {
            gitManager.add(files)
            gitManager.commit(message)
                .onSuccess { showToast("提交成功") }
                .onFailure { showError(it.message) }
        }
    }
}
```

---

## 4. Compose UI 开发

### 4.1 应用主题

**颜色系统** (Material 3):

```kotlin
// ui/theme/Color.kt

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    error = Color(0xFFB00020),
    // ...
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    // ...
)
```

### 4.2 可复用组件

**主要组件**:

| 组件 | 文件 | 用途 |
|------|------|------|
| `Toast` | `components/Toast.kt` | 消息提示 |
| `FilePicker` | `components/FilePicker.kt` | 文件选择器 |
| `MarkdownView` | `components/MarkdownView.kt` | Markdown 渲染 |
| `ColorPickerDialog` | `components/ColorPickerDialog.kt` | 颜色选择 |

### 4.3 导航结构

```kotlin
// 使用 Jetpack Navigation Compose

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("editor") { CodeEditScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
        composable("git") { GitScreen(navController) }
        composable("plugins") { PluginScreen(navController) }
        composable("about") { AboutScreen(navController) }
    }
}
```

### 4.4 状态管理最佳实践

```kotlin
// 1. 定义不可变状态
data class EditorState(
    val openFiles: List<FileState> = emptyList(),
    val activeFile: FileState? = null,
    val cursorPosition: CursorPosition = CursorPosition(0, 0),
    val isExecuting: Boolean = false,
    val diagnostics: List<Diagnostic> = emptyList(),
    val consoleOutput: String = ""
)

// 2. 使用 StateFlow 管理状态
class EditorViewModel : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
    
    // 3. 一次性事件使用 Channel
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()
    
    fun updateFileContent(path: String, content: String) {
        _state.update { currentState ->
            currentState.copy(
                openFiles = currentState.openFiles.map { file ->
                    if (file.path == path) file.copy(content = content, isModified = true)
                    else file
                }
            )
        }
    }
}

// 4. UI 中收集状态
@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsState(initial = null)
    
    // UI 渲染...
}
```

---

## 5. JNI 交互

### 5.1 调用 Lua VM

```kotlin
// LuaExecutor.kt - 封装 JNI 调用

class LuaExecutor {
    
    companion object {
        init {
            System.loadLibrary("luajava")
        }
    }
    
    // 执行 Lua 代码
    external fun execute(code: String): String
    
    // 加载并执行 Lua 文件
    external fun executeFile(path: String): String
    
    // 调用 Lua 函数
    external fun callFunction(name: String, vararg args: Any?): String
    
    // 注册 Java 方法到 Lua
    external fun registerMethod(clazz: Class<*>, methodName: String)
    
    // 获取/设置全局变量
    external fun getGlobal(name: String): Any?
    external fun setGlobal(name: String, value: Any?)
}

// 使用示例
val executor = LuaExecutor()
val result = executor.execute("""
    local greeting = "Hello from Lua!"
    print(greeting)
    return greeting
""")
```

### 5.2 异常处理

```kotlin
try {
    luaExecutor.execute(code)
} catch (e: LuaException) {
    // Lua 运行时错误
    Log.e("LuaExecution", "Lua error: ${e.message}")
    showError("Lua 执行错误", e.message ?: "未知错误")
} catch (e: JNIException) {
    // JNI 调用错误
    Log.e("LuaExecution", "JNI error: ${e.message}")
}
```

---

## 6. 测试

### 6.1 单元测试

**目录**: `app/src/test/`

```kotlin
// EditorViewModelTest.kt

class EditorViewModelTest {
    private lateinit var viewModel: EditorViewModel
    
    @Before
    fun setup() {
        viewModel = EditorViewModel()
    }
    
    @Test
    fun `updateFileContent should update file content`() = runTest {
        // Given
        val path = "test.lua"
        val content = "print('hello')"
        
        // When
        viewModel.updateFileContent(path, content)
        
        // Then
        val state = viewModel.state.value
        val file = state.openFiles.find { it.path == path }
        assertThat(file?.content).isEqualTo(content)
        assertThat(file?.isModified).isTrue()
    }
}
```

### 6.2 UI 测试

**目录**: `app/src/androidTest/`

```kotlin
// EditorScreenTest.kt

@get:Rule
val composeTestRule = createComposeRule()

@Test
fun editorScreen_displaysCodeEditor() {
    composeTestRule.setContent {
        AppTheme {
            CodeEditScreen()
        }
    }
    
    // 验证编辑器显示
    composeTestRule.onNodeWithTag("codeEditor").assertIsDisplayed()
    
    // 模拟输入
    composeTestRule.onNodeWithTag("codeEditor").performTextInput("print('test')")
    
    // 验证内容
    composeTestRule.onNodeWithText("print('test')").assertExists()
}
```

---

## 7. 发布与部署

### 7.1 签名配置

项目使用统一的调试签名密钥 (`difierline.jks`)：

```bash
# 构建 Release 版本
./gradlew :app:assembleRelease

# 输出位置
# app/build/outputs/apk/release/app-release.apk
```

### 7.2 版本管理

**版本号规则**: MAJOR.MINOR.PATCH (例如 1.3.5)
- MAJOR: 重大功能变更
- MINOR: 功能新增
- PATCH: Bug 修复

**版本代码**: YYYYMMDD 格式 (例如 20260806)

### 7.3 发布检查清单

- [ ] 更新 `versionName` 和 `versionCode`
- [ ] 移除调试日志
- [ ] 验证所有功能正常
- [ ] 运行测试套件
- [ ] 签名验证
- [ ] 性能分析无异常

---

## 8. 常见问题

### 8.1 构建问题

| 问题 | 解决方案 |
|------|---------|
| NDK 版本不匹配 | 检查 `local.properties` 中的 NDK 路径 |
| JNI 编译失败 | 检查 C 代码中的语法错误 |
| Gradle 同步失败 | 清理缓存并重新同步 |
| core.apk 未找到 | 确保 `:core-apk:assembleRelease` 任务成功执行 |

### 8.2 运行时问题

| 问题 | 解决方案 |
|------|---------|
| Lua VM 加载失败 | 检查 JNI 库是否正确打包 |
| 脚本执行无输出 | 检查 Lua 代码是否有语法错误 |
| UI 卡顿 | 使用 Profiler 检查主线程阻塞 |

### 8.3 调试技巧

**Logcat 过滤**:

```
# Lua 执行日志
tag:LXCLUA

# JNI 调用日志
tag:JNI

# 编辑器日志
tag:EditorViewModel
```

**断点设置**:
- Kotlin: 在 Android Studio 中直接设置断点
- C/C++: 配置 LLDB 并在 `lua/src/` 目录下的源文件中设置断点
