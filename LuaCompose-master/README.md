# LuaCompose 📱

> **🚨 注意：本项目目前处于 `1.0.0-experimental` 阶段，属于不稳定的实验性、开发中版本。API 接口与功能实现可能会有较大变动。**

`LuaCompose` 是一个将 **Lua 脚本** 与 **Android Jetpack Compose** 结合的动态化 UI 框架。它允许开发者使用 Lua 脚本动态定义、渲染和更新原生的 Compose 组件，旨在探索 Android 端基于 Lua 语言的动态化 UI 布局与热重载。

---

## ✨ 核心特性

- 🔗 **Compose 与 Lua 绑定**：支持 `Text`, `Column`, `Row`, `Box`, `Button` 等核心 Jetpack Compose 组件的动态渲染与控制。
- 🔄 **热重载支持**：可在运行时重新读取并执行外部 Lua 脚本，实现无缝的 UI 界面热更新。
- 📦 **自动映射机制**：通过 KSP 和反射，使得绝大部分的 Compose Modifier 属性、基础数据类和动画配置都能被 Lua 透明调用。
- 🛠 **高度拓展性**：支持 Lua 原生方法覆写，允许开发者像原生代码一样自定义 `Modifier` 样式原型。

---

## 🏗️ 架构原理

本项目采用了一套多层桥接架构来实现真正的 Compose 组件在 Lua 中的无缝调用：

1. **编译期代码生成（KSP）**：
   在编译期扫描标有 `@LuaBridgePackage` 注解的包路径下的 `Composable` 函数，自动生成相应的 Kotlin 代理插件。这使得大多数 Compose 核心方法都能在不需要手动编写 Wrapper 的情况下注册到 Lua 环境中，极大提升了加载和调用性能。
2. **运行时反射缓存机制**：
   当 Lua 需要调用底层 Java 对象的属性或方法时，底层会通过反射自动搜索类及父类的公开方法，并利用 `LruCache` 机制进行缓存，以便兼顾执行效率和开发上的便捷性。
3. **Lua 原型链注入**：
   框架自动将 Compose 中大量的方法和伴生对象映射成 Lua 能够识别的 Table 和 Metatable。对于像 `Modifier` 这样需要支持链式调用的核心类，我们实现了基于 Lua 表原型（Prototype）的 fallback 查找逻辑，让开发者能够在 Lua 端实现 `Modifier.xxx = function(self) return self.padding(10) end` 的扩展体验。

---

## 🛠️ 项目结构

- **`annotations/`**：存放专门用于 KSP 扫描的自定义注解（例如 `@LuaBridgePackage`、`@LuaBridgeLocals`）。
- **`compiler/`**：基于 KSP 构建的符号处理器，负责在编译期自动扫描分析 Compose 源码并生成 Lua 到 Compose 的静态桥接插件代码。
- **`compose/`**：Lua 桥接核心层模块，包含 `ComposeBridge`、`LuaComposeRegistry` 以及底层的 Java-Lua 对象包裹器（Wrapper）与全局工具函数（例如 `dump`）。
- **`app/`**：宿主应用层，包含启动和执行 Lua 脚本的入口（`MainActivity.kt`）。
- **`example/`**：存放大量测试及展示所用的 Lua 实例代码。

---

## 🚀 支持状态与 TODO 列表

### ✅ 已经支持的 Compose 库模块
- [x] `androidx.compose.foundation` (基础布局等)
- [x] `androidx.compose.foundation.layout` (Row, Column, Box, Padding 等布局约束)
- [x] `androidx.compose.material3` (质感设计组件库)
- [x] `androidx.compose.ui` (底层 UI 控制)
- [x] `androidx.compose.animation` (高级核心动画支持，包含 Crossfade, AnimatedVisibility 等)
- [x] `androidx.compose.animation.core` (底层动画规范与插值器等)
- [x] `CompositionLocal` 及上下文相关组件 (支持直接在 Lua 调用 `LocalDensity.current` 等)
- [x] `DrawScope` 与 Canvas 自定义绘制环境集成

### ⏳ 待实现 / 待完善 (TODO)
- [ ] 针对 `androidx.compose.material` (M2) 或更广泛的三方库支持
- [ ] List / LazyColumn 等具有复杂上下文作用域组件的性能调优
- [ ] 更加完善的 Lua 错误堆栈反向映射（将报错精准定位到具体的 Compose Node）
- [ ] 提升 Compose State 与 Lua 响应式变量之间的双向绑定效率

---

## 🎮 如何测试与运行示例

在项目的 **`example/`** 文件夹中，我们内置了大量的 Lua 版 Compose 动画测试脚本 (`AnimationExample1.lua` 到 `AnimationExample22.lua`)。

> **ℹ️ 提示：** 这些示例的灵感和原生 Kotlin 代码来源于知名的 [skydoves/compose-animations](https://github.com/skydoves/compose-animations) 仓库。目前，这些例子已经以 **1:1 的等价语义**通过 Lua 脚本完全重构实现，渲染效果和原生的 Kotlin 代码高度一致！

### 测试步骤

1. 编译并安装 `app-debug.apk` 到你的 Android 设备中。
2. 将 `example/` 中的任意一个 Lua 文件（例如 `AnimationExample1.lua`）推送到手机的沙盒存储路径，并重命名为 `main.lua`。
   路径位置为：
   `/sdcard/Android/data/com.kulipai.luacompose/files/main.lua`
3. 确保文件替换完成后，打开运行中的 App。
4. **点击页面上的 Reload 按钮**。
5. 脚本会被热重载，并立刻在页面中渲染出对应 Lua 代码中描述的 Compose 动画或布局效果。
