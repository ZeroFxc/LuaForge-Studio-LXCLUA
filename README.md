# LuaForge Studio - LXCLUA

<p align="center">
  <strong>基于 LuaForge-Studio 的 Android Lua IDE 与定制运行时</strong>
</p>

<p align="center">
  <a href="#特性">特性</a> ·
  <a href="#项目结构">项目结构</a> ·
  <a href="#快速开始">快速开始</a> ·
  <a href="#文档">文档</a> ·
  <a href="#开发指南">开发指南</a>
</p>

---

## 项目简介

**LuaForge Studio (LXCLUA)** 是一个功能完整的 Android 平台 Lua 集成开发环境，内置深度定制的 Lua 5.5 运行时引擎。该项目在 [LuaForge-Studio](https://github.com/wisyh/LuaForge-Studio) 基础上进行了大量修改和扩展，为移动端脚本开发提供了强大、安全且易用的工具链。

项目包含两个主要层次：

1. **Lua VM 核心层** (`app/src/main/jni/lua/`) - 基于 Lua 5.5 深度定制的 C/C++ 脚本引擎，支持跨平台编译
2. **Android IDE 层** (`app/src/main/kotlin/`) - 使用 Jetpack Compose 构建的现代化移动开发环境

---

## 特性

### Lua VM 核心 (NCore)

| 特性 | 说明 |
|------|------|
| **64 位指令集** | 自定义 XCLUA 指令集，优化操作码派发和更大操作数空间 |
| **安全编译** | 动态操作码映射、SHA-256 完整性校验、时间戳加密 |
| **语法扩展** | 类/接口、Switch/Try-Catch、箭头函数、管道操作符、可选链、空值合并等 |
| **代码混淆** | 11 种混淆技术（CFF、块洗牌、虚假块、字符串加密、VM 保护等） |
| **JIT 编译** | 基于 SLJIT 的跨平台即时编译（支持 x86/x64、ARM32/64、RISC-V 等） |
| **字节码转 C** | 将 Lua 字节码转换为 C 源代码，便于嵌入或外部编译优化 |
| **WASM 运行时** | 集成 wasm3 解释器与 wasmtime JIT 运行时 |
| **LSP 服务器** | 内置语言服务器协议实现，提供 IDE 智能提示 |
| **多线程支持** | 互斥锁、条件变量、读写锁、通道等同步原语 |

### Android IDE 客户端

| 特性 | 说明 |
|------|------|
| **代码编辑器** | 语法高亮、自动补全、代码折叠、错误诊断 |
| **可视化设计器** | LuaCompose 声明式 UI 的可视化编辑与预览 |
| **插件系统** | 支持 Lua/Dex 插件动态加载，扩展 IDE 功能 |
| **MCP 集成** | Model Context Protocol 客户端，支持 AI 辅助编程 |
| **Git 集成** | 内置 JGit 版本控制功能 |
| **项目管理** | 项目模板、文件浏览器、资源管理 |

---

## 项目结构

```
LXC-LUA/
├── app/                          # Android 应用主模块
│   ├── src/main/
│   │   ├── kotlin/              # Kotlin/Compose 源代码
│   │   │   └── com/luaforge/studio/lxclua/
│   │   │       ├── ui/          # Compose UI 组件
│   │   │       ├── plugin/      # 插件系统
│   │   │       ├── mcp/         # MCP 客户端
│   │   │       ├── langs/lua/   # Lua 语言支持
│   │   │       └── ...
│   │   ├── jni/                 # JNI 桥接与原生库
│   │   │   ├── lua/             # Lua VM 核心 (NCore)
│   │   │   │   ├── src/         # 核心源码 (core, vm, compiler, stdlib, utils)
│   │   │   │   └── docs/        # Lua VM 文档
│   │   │   ├── luajava/         # Lua-Java 桥接 (LXCLuaCore.c)
│   │   │   ├── socket/          # LuaSocket 网络库
│   │   │   ├── lfs/             # LuaFileSystem
│   │   │   ├── lsqlite3/        # SQLite 数据库
│   │   │   ├── lua-protobuf/    # Protocol Buffers
│   │   │   ├── luv/             # libuv 异步 I/O
│   │   │   └── ...
│   │   ├── assets/              # 脚本资源和模板
│   │   └── res/                 # Android 资源
│   └── build.gradle.kts         # 应用构建配置
│
├── core/                        # 核心模块 (Java/Kotlin)
├── core-apk/                    # 核心 APK 模块（动态加载）
├── editor/                      # 编辑器模块
├── signer/                      # 签名工具模块
├── compiler/                    # 编译器模块
├── gradle/                      # Gradle 构建配置
├── docs/                        # 项目文档
│   └── luacompose_examples/     # LuaCompose UI 示例 (35个)
├── scripts/                     # 构建和工具脚本
├── jgit-master/                 # JGit 依赖库
└── LuaForge_Studio_Backup/      # 项目备份
```

---

## 快速开始

### 环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Hedgehog (2023.1.1) 或更高 |
| Android SDK | API 24-36 |
| NDK | 29.0.13004108 或更高 |
| JDK | 17 |
| Gradle | 9.1.0 |

### 构建步骤

1. **克隆项目**

   ```bash
   git clone <repository-url>
   cd LXC-LUA
   ```

2. **导入 Android Studio**

   打开 Android Studio → `File` → `Open` → 选择项目根目录

3. **同步依赖**

   Gradle 会自动下载所需依赖，首次同步可能需要较长时间

4. **构建并运行**

   ```bash
   # 调试构建
   ./gradlew :app:assembleDebug

   # 安装到设备
   ./gradlew :app:installDebug
   ```

   或在 Android Studio 中直接点击 `Run` 按钮

### Lua VM 独立编译

如果需要单独编译 Lua VM（命令行工具）：

```bash
cd app/src/main/jni/lua/

# Linux
make linux

# Windows (MinGW)
make mingw

# Android (Termux)
make termux

# WebAssembly
make wasm
```

---

## 文档

### 架构与设计

- [项目架构文档](./docs/PROJECT_ARCHITECTURE.md) - 双层架构设计与模块关系
- [Lua VM 集成文档](./docs/LUA_VM_INTEGRATION.md) - Android 如何与 Lua VM 交互
- [Lua VM 核心文档](./app/src/main/jni/lua/docs/README.md) - NCore 引擎完整文档

### 开发指南

- [Android 开发指南](./docs/ANDROID_DEVELOPMENT.md) - Android 端开发说明
- [构建与调试指南](./docs/BUILD_AND_DEBUG.md) - 编译、调试、性能分析
- [Lua VM 构建指南](./app/src/main/jni/lua/docs/BUILD.md) - 多平台编译说明

### API 参考

- [Lua API 参考](./app/src/main/jni/lua/docs/LUA_API.md) - 所有 require 模块的 API 文档
- [语法参考](./app/src/main/jni/lua/docs/SYNTAX_REFERENCE.md) - 扩展语法完整参考
- [模块说明](./app/src/main/jni/lua/docs/MODULES.md) - 各模块详细描述
- [LuaCompose 示例](./docs/luacompose_examples/README.md) - 35 个 UI 组件示例

### 其他

- [安全策略](./app/src/main/jni/lua/docs/SECURITY.md) - 安全漏洞报告与策略
- [贡献指南](./app/src/main/jni/lua/docs/CONTRIBUTING.md) - 如何参与贡献
- [开发计划](./app/src/main/jni/lua/docs/PLAN.md) - 项目路线图与规划

---

## 架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                       Android 应用层 (Kotlin/Compose)                │
│                                                                     │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  ┌───────────┐  │
│  │  IDE UI     │  │  插件系统     │  │  MCP 客户端 │  │ Git 集成  │  │
│  └─────────────┘  └──────────────┘  └────────────┘  └───────────┘  │
├─────────────────────────────────────────────────────────────────────┤
│                       JNI 桥接层 (C/C++)                            │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                    LXCLuaCore (luajava)                      │    │
│  └─────────────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────────────┤
│                       Lua VM 核心层 (C23)                            │
│                                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐    │
│  │  编译器   │  │  虚拟机   │  │ 标准库   │  │   扩展库         │    │
│  │ llex     │  │ lvm      │  │ base     │  │ crypto, http    │    │
│  │ lparser  │  │ lgc      │  │ math     │  │ thread, fs      │    │
│  │ lcodegen │  │ ljit     │  │ string   │  │ struct, ptr     │    │
│  │ lasm     │  │          │  │ table    │  │ wasm3, wasmtime │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 平台支持

| 平台 | 架构 | 状态 | 备注 |
|------|------|------|------|
| Android | arm64-v8a | ✅ 主平台 | 完整 IDE 功能 |
| Android | armeabi-v7a | ⚠️ 未测试 | VM 支持，IDE 未构建 |
| Linux | x86_64 | ✅ 支持 | 命令行工具 + WASM 运行时 |
| Windows | x86_64 | ✅ 支持 | MinGW 编译 |
| macOS | x86_64/ARM64 | ✅ 支持 | 命令行工具 |
| WebAssembly | wasm32 | ✅ 支持 | Emscripten 编译 |

---

## 开发状态

### 当前版本
- **版本**: 1.3.5 (build 20260806)
- **活跃维护**: 是

### 已知限制

- ARM64 JIT 在某些 Android 设备上存在稳定性问题
- Lua→WASM 编译器仅支持 Lua 子集语法
- WASI 支持尚未完整实现所有系统调用
- 部分混淆选项组合可能导致字节码不兼容

---

## 致谢

本项目基于以下优秀开源项目构建：

| 项目 | 用途 |
|------|------|
| [Lua](https://www.lua.org/) | 核心语言引擎 |
| [LuaForge-Studio](https://github.com/wisyh/LuaForge-Studio) | 原始项目基础 |
| [QuickJS](https://bellard.org/quickjs/) | JavaScript 引擎集成 |
| [SLJIT](https://github.com/zherczeg/sljit) | JIT 编译后端 |
| [wasm3](https://github.com/wasm3/wasm3) | WebAssembly 解释器 |
| [wasmtime](https://wasmtime.dev/) | WebAssembly 运行时 |
| [JGit](https://www.eclipse.org/jgit/) | Java Git 实现 |
| [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) | AI 协议集成 |

---

## 许可证

本项目基于 [MIT License](./LICENSE) 发布。

Lua 原始代码 Copyright (c) 1994-2024 PUC-Rio。
