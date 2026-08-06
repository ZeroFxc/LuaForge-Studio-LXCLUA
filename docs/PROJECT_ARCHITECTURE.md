# LuaForge Studio 项目架构文档

## 1. 架构概述

LuaForge Studio (LXCLUA) 采用 **双层混合架构**，结合原生 C/C++ 脚本引擎与 Kotlin/Jetpack Compose 移动应用，实现了一个完整的 Android 平台 Lua 集成开发环境。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          用户界面层 (UI Layer)                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                     Jetpack Compose UI                                │   │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────────────┐ │   │
│  │  │ 主页面     │ │ 代码编辑器 │ │ 可视化设计 │ │ 设置与分析         │ │   │
│  │  │ HomeScreen │ │ CodeEdit   │ │ Designer   │ │ Settings/Analyse   │ │   │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────────┤
│                        业务逻辑层 (Business Logic)                           │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                          Kotlin 模块                                   │   │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────────────┐ │   │
│  │  │ 编辑器逻辑 │ │ 插件系统   │ │ Git 集成   │ │ MCP/AI 客户端      │ │   │
│  │  │ ViewModel  │ │ PluginMgr │ │ GitManager │ │ MCPService         │ │   │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────────┤
│                        JNI 桥接层 (Bridge Layer)                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                      C/C++ JNI 实现                                    │   │
│  │  ┌────────────────────────────────────────────────────────────────┐  │   │
│  │  │              LXCLuaCore (luajava/LXCLuaCore.c)                 │  │   │
│  │  │  - Lua 状态管理                                                 │  │   │
│  │  │  - Java 方法注册到 Lua                                         │  │   │
│  │  │  - 异常与类型转换                                               │  │   │
│  │  └────────────────────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────────┤
│                        Lua VM 核心层 (NCore)                                 │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                       C23 原生代码                                    │   │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────────────┐ │   │
│  │  │ 编译器     │ │ 虚拟机     │ │ 扩展库     │ │ 第三方集成         │ │   │
│  │  │ llex/lparser/lasm │ lvm/lgc/ljit │ crypto/http | wasm3/wasmtime  │ │   │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 模块划分

### 2.1 Gradle 模块结构

```
LXC-LUA (Root Project)
├── app                    # 主应用模块 (com.luaforge.studio.lxclua)
├── core                   # 核心功能模块 (Java/Kotlin API)
├── core-apk               # 动态加载的核心 APK
├── editor                 # 编辑器组件模块
├── compiler               # 编译器模块
├── signer                 # 签名工具模块
└── gradle                 # 版本目录 (libs.versions.toml)
```

### 2.2 模块依赖关系

```
app → core
app → editor
app → signer
core-apk → core
```

---

## 3. Android 应用层详解

### 3.1 UI 层 (Kotlin/Compose)

**主路径**: `app/src/main/kotlin/com/luaforge/studio/lxclua/`

| 包路径 | 功能 | 关键文件 |
|--------|------|----------|
| `ui/editor/` | 代码编辑界面 | `CodeEditScreen.kt`, `EditorViewModel.kt` |
| `ui/editor/designer/` | 可视化设计器 | `DesignerHost.kt`, `PreviewCanvas.kt` |
| `ui/components/` | 可复用 UI 组件 | `Toast.kt`, `FilePicker.kt` |
| `ui/settings/` | 设置界面 | `SettingsScreen.kt`, `AISettings.kt` |
| `ui/git/` | Git 操作界面 | `GitScreen.kt`, `GitDialogs.kt` |
| `ui/plugin/` | 插件管理界面 | `PluginScreen.kt`, `PluginManagementScreen.kt` |

**UI 架构模式**:

```
Compose UI (Screen)
    │
    ├── ViewModel (状态管理)
    │   └── State (不可变状态)
    │
    ├── UseCase (业务逻辑)
    │   └── Repository (数据访问)
    │
    └── JNI Bridge (原生调用)
        └── Lua VM
```

### 3.2 业务逻辑层

**关键组件**:

| 组件 | 路径 | 功能 |
|------|------|------|
| `MainActivity` | `MainActivity.kt` | 应用入口，初始化 Lua VM |
| `PluginManager` | `plugin/PluginManager.kt` | 插件加载与管理 |
| `MCPService` | `mcp/MCPService.kt` | Model Context Protocol 客户端 |
| `GitManager` | `git/GitManager.kt` | Git 版本控制 |
| `SettingsManager` | `ui/settings/SettingsManager.kt` | 应用设置管理 |

**插件系统架构**:

```
┌───────────────────────────────────────────────────────────────┐
│                    PluginManager                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │               IPlugin (接口)                             │  │
│  │  - onCreate()                                           │  │
│  │  - onDestroy()                                          │  │
│  │  - getBridge(): IPluginBridge                           │  │
│  └─────────────────────────────────────────────────────────┘  │
│                            │                                   │
│  ┌─────────────────────────┴─────────────────────────────────┐ │
│  │            IPluginBridge (功能扩展点)                      │ │
│  │  - IPluginBridgeEditor      # 编辑器扩展                   │ │
│  │  - IPluginBridgeLua         # Lua API 扩展                 │ │
│  │  - IPluginBridgeAI          # AI 功能扩展                  │ │
│  │  - IPluginBridgeNetwork     # 网络功能扩展                 │ │
│  │  - IPluginBridgeUI          # UI 界面扩展                  │ │
│  │  - ...                                                     │ │
│  └────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

---

## 4. JNI 桥接层

### 4.1 桥接架构

**源文件**: `app/src/main/jni/luajava/`

| 文件 | 功能 |
|------|------|
| `LXCLuaCore.c` | Lua VM 核心 JNI 接口 |
| `luajava.h` | 头文件定义 |

**主要 JNI 函数**:

| 函数 | 功能 |
|------|------|
| `Java_com_luaforge_studio_lxclua_..._luaOpen` | 初始化 Lua VM |
| `...luaClose` | 关闭 Lua VM |
| `...luaLoadString` | 加载并执行 Lua 代码字符串 |
| `...luaLoadFile` | 加载并执行 Lua 文件 |
| `...luaCallFunction` | 调用 Lua 函数 |
| `...luaRegisterJavaMethod` | 注册 Java 方法到 Lua |
| `...luaSetGlobalVariable` | 设置全局变量 |
| `...luaGetGlobalVariable` | 获取全局变量 |

### 4.2 类型映射

```
Java 类型          │  Lua 类型
───────────────────┼───────────────────
boolean            │  boolean
int/long           │  number (integer)
float/double       │  number (float)
String             │  string
byte[]             │  string (binary)
Object[]           │  table
Map<String, Object>│  table
自定义 Java 对象    │  userdata
```

### 4.3 异常处理

JNI 层实现了 Lua 异常到 Java 异常的转换：

```c
// Lua error → Java LuaException
if (lua_pcall(L, nargs, nresults, 0) != LUA_OK) {
    const char* msg = lua_tostring(L, -1);
    // 抛出 Java 异常
    (*env)->ThrowNew(env, luaExceptionClass, msg);
}
```

---

## 5. Lua VM 核心层 (NCore)

### 5.1 目录结构

**主目录**: `app/src/main/jni/lua/`

```
lua/
├── src/
│   ├── core/           # 核心层：API、对象、操作码、内存管理
│   ├── vm/             # VM 运行时：解释器、JIT、字节码工具
│   │   └── jit/        # JIT 编译器子系统
│   ├── compiler/       # 编译器：词法分析、语法分析、代码生成
│   ├── stdlib/         # 标准库：base、math、string、table 等
│   ├── utils/          # 工具库：crypto、http、thread 等
│   ├── bin/            # 应用程序：解释器、编译器、反汇编器
│   ├── jit/            # SLJIT 后端
│   ├── wasm/           # WASM 运行时
│   ├── lua2wasm/       # Lua→WASM 编译器
│   └── lspsrv/         # LSP 服务器
├── pcre2/              # PCRE2 正则引擎
├── quickjs/            # QuickJS JavaScript 引擎
├── docs/               # 文档
└── Makefile            # 构建系统
```

### 5.2 核心模块交互

```
                    ┌──────────────┐
                    │   编译器输入  │
                    │    (.lua)    │
                    └──────┬───────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                ▼                ▼
   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
   │    llex     │  │ last_parse  │  │   lexer     │
   │  (词法分析) │  │  (AST解析)  │  │  (外部API)  │
   └──────┬──────┘  └──────┬──────┘  └─────────────┘
          │                │
          ▼                ▼
   ┌─────────────┐  ┌─────────────┐
   │   lparser   │  │   lcodegen  │
   │  (语法分析) │  │  (代码生成) │
   └──────┬──────┘  └──────┬──────┘
          │                │
          ▼                │
   ┌─────────────┐         │
   │    lasm     │◄────────┘
   │  (汇编器)   │
   └──────┬──────┘
          │
          ▼
   ┌─────────────┐     ┌─────────────┐
   │    lvm      │◄────│   lundump   │
   │  (VM执行)   │     │ (字节码加载)│
   └──────┬──────┘     └──────┬──────┘
          │                   │
          ▼                   ▼
   ┌─────────────┐     ┌─────────────┐
   │    lgc      │     │    ldump    │
   │  (垃圾回收) │     │ (字节码保存)│
   └─────────────┘     └─────────────┘
```

---

## 6. 扩展库架构

### 6.1 内置扩展模块

| 类别 | 模块 | 路径 |
|------|------|------|
| 数学 | `bigint` | `src/utils/lbigint.c` |
| 加密 | `crypto`, `rsa`, `ecc` | `src/utils/lcrypto.c`, `lrsa.c`, `lecc.c` |
| 网络 | `http` | `src/utils/libhttp.c` |
| 多线程 | `thread` | `src/stdlib/lthreadlib.c` |
| 文件系统 | `fs` | `src/stdlib/lfs.c` |
| 序列化 | `userdata` | `src/stdlib/ludatalib.c` |
| OOP | `class`, `struct`, `ptr` | `src/stdlib/lclass.c`, `lstruct.c`, `lptrlib.c` |

### 6.2 第三方集成模块

| 模块 | 路径 | 依赖 |
|------|------|------|
| `wasm3` | `src/wasm/lwasm3.c` | `wasm3/` 目录 |
| `wasmtime` | `src/wasm/lwasmtime.c` | `wasmtime/` 预编译库 |
| `quickjs` | `src/bin/lquickjs.c` | `quickjs/` 目录 |
| PCRE2 | `src/stdlib/lstrlib.c` | `pcre2/` 目录 |

---

## 7. 数据流

### 7.1 Lua 脚本执行流程

```
Android UI 按钮点击
    │
    ▼
Kotlin 调用 PluginManager.executeLua(code)
    │
    ▼
通过 JNI 调用 LXCLuaCore.loadString(code)
    │
    ▼
C 层 lua_load() + lua_pcall()
    │
    ├── llex: 词法分析 → Token 流
    ├── lparser: 语法分析 → AST
    ├── lcodegen: 代码生成 → 中间码
    ├── lasm: 汇编 → 字节码
    │
    ▼
lvm: 解释执行字节码
    │
    ▼
执行结果返回 JNI 层
    │
    ▼
Java 层接收结果并更新 UI
```

### 7.2 LuaCompose UI 渲染流程

```
LuaCompose 脚本
    │
    ▼
Lua VM 执行
    │
    ▼
调用注册的 Java 方法 → declareUI(tree)
    │
    ▼
Kotlin/Compose 接收 UI 树描述
    │
    ▼
解析为 Compose Modifier 链
    │
    ▼
Compose 渲染引擎执行
    │
    ▼
Surface/Canvas 绘制
```

---

## 8. 安全架构

### 8.1 多层安全防护

```
┌─────────────────────────────────────────────────────────────┐
│                    第 1 层：应用沙箱                         │
│  Android 应用沙箱 + 权限系统                                  │
├─────────────────────────────────────────────────────────────┤
│                    第 2 层：Lua 安全                         │
│  - 字节码签名验证 (\x1bXCF)                                  │
│  - SHA-256 完整性校验                                        │
│  - 时间戳加密防回放                                          │
│  - 动态操作码映射                                            │
├─────────────────────────────────────────────────────────────┤
│                    第 3 层：代码混淆                         │
│  - 控制流扁平化 (CFF)                                        │
│  - 基本块洗牌                                                │
│  - 字符串加密                                                │
│  - VM 保护（自定义指令集）                                    │
├─────────────────────────────────────────────────────────────┤
│                    第 4 层：WASM 沙箱                        │
│  - wasm3 解释器隔离                                          │
│  - wasmtime 安全运行时                                       │
│  - WASI 系统接口限制                                         │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 字节码保护流程

```
源码 (.lua)
    │
    ▼
编译 → 字节码
    │
    ▼
lobfuscate: 应用混淆标志
    │
    ├── OBFUSCATE_CFF: 控制流扁平化
    ├── OBFUSCATE_BLOCK_SHUFFLE: 基本块洗牌
    ├── OBFUSCATE_STR_ENCRYPT: 字符串加密
    ├── OBFUSCATE_VM_PROTECT: VM 保护
    │
    ▼
sha256: 计算完整性签名
    │
    ▼
ldump: 序列化为 .luac 文件
    │
    ▼
加密时间戳 + 动态操作码映射
    │
    ▼
输出受保护的字节码文件
```

---

## 9. 构建系统

### 9.1 Gradle 构建

| 模块 | 类型 | 产物 |
|------|------|------|
| `app` | Android Application | `app-debug.apk` / `app-release.apk` |
| `core` | Android Library | `core.aar` |
| `editor` | Android Library | `editor.aar` |
| `compiler` | Java Library | `compiler.jar` |
| `signer` | Java Library | `signer.jar` |

### 9.2 NDK 构建 (C/C++)

**构建文件**: `app/src/main/jni/Android.mk`

| 目标 | 说明 |
|------|------|
| `libluajava.so` | JNI 桥接库 |
| `liblua.so` (static) | Lua 静态库（内嵌所有扩展） |

### 9.3 Lua VM 独立构建

**Makefile 目标** (`app/src/main/jni/lua/Makefile`):

| 目标 | 平台 | 产物 |
|------|------|------|
| `linux` | Linux x64 | `lxclua`, `luac`, `lbcdump`, `lxclua-lsp` |
| `mingw` | Windows MinGW | `lxclua.exe`, `luac.exe`, `lbcdump.exe` |
| `termux` | Android Termux | `lxclua`, `luac` |
| `wasm` | WebAssembly | `lxclua.js`, `lxclua.wasm` |
| `wasmlsp` | WASM LSP | `lxclua-lsp.js`, `lxclua-lsp.wasm` |

---

## 10. 性能优化策略

### 10.1 VM 优化

| 技术 | 描述 |
|------|------|
| Computed Goto | VM 主循环使用跳转表优化分支预测 |
| 64 位指令 | 单指令携带更多操作数，减少指令数量 |
| 字符串内部化 | 相同字符串共享内存，加速比较 |
| JIT 编译 | 热点代码编译为原生机器码 |

### 10.2 Android 端优化

| 技术 | 描述 |
|------|------|
| APK 拆分 | core.apk 按需动态加载 |
| 增量编译 | NDK/Gradle 增量构建加速 |
| JNI 缓存 | 缓存频繁调用的 Java 方法 ID |
| 协程调度 | Kotlin 协程管理 Lua 执行线程 |

---

## 附录：平台差异表

| 功能 | Android | Linux | Windows | WASM |
|------|---------|-------|---------|------|
| JIT 编译 | ⚠️ ARM64 受限 | ✅ 完整 | ✅ 完整 | ❌ 不可用 |
| wasm3 | ✅ | ✅ | ✅ | — |
| wasmtime | ✅ arm64 | ✅ x64 | ✅ x64 | ❌ |
| QuickJS | ✅ | ✅ | ✅ | ✅ |
| 多线程 | ✅ | ✅ | ✅ | ❌ |
| LSP 服务器 | ❌ | ✅ | ✅ | ✅ (WASM) |
| stdio 交互 | 有限 | ✅ | ✅ | ❌ |
