# LuaCompose 📱

> **🚨 Notice: This project is currently in the `1.0.0-experimental` stage. It is an unstable, in-development experimental version. APIs and functionality are subject to significant changes.**

`LuaCompose` is a dynamic UI framework that integrates **Lua scripting** with **Android Jetpack Compose**. It allows developers to dynamically define, render, and update native Compose components using Lua scripts, aiming to explore dynamic UI layouts and hot-reloading on Android through the Lua language.

---

## ✨ Core Features

- 🔗 **Compose and Lua Binding**: Supports dynamic rendering and control of core Jetpack Compose components like `Text`, `Column`, `Row`, `Box`, `Button`, etc.
- 🔄 **Hot Reloading Support**: Can read and execute external Lua scripts at runtime, enabling seamless hot updates for UI interfaces.
- 📦 **Auto-Mapping Mechanism**: Through KSP and reflection, most Compose Modifier properties, basic data classes, and animation configurations can be transparently called by Lua.
- 🛠 **High Extensibility**: Supports Lua native method overriding, allowing developers to customize `Modifier` style prototypes just like in native code.

---

## 🏗️ Architecture and Principles

This project utilizes a multi-layer bridging architecture to achieve seamless invocation of real Compose components within Lua:

1. **Compile-time Code Generation (KSP)**:
   Scans `Composable` functions annotated with `@LuaBridgePackage` at compile time and automatically generates corresponding Kotlin proxy plugins. This allows most Compose core methods to be registered into the Lua environment without manual Wrapper writing, greatly improving load and invocation performance.
2. **Runtime Reflection Cache Mechanism**:
   When Lua needs to call the properties or methods of underlying Java objects, the system automatically searches the public methods of the class and its superclasses via reflection, using an `LruCache` mechanism to balance execution efficiency and development convenience.
3. **Lua Prototype Chain Injection**:
   The framework automatically maps a large number of Compose methods and companion objects into Lua-recognizable Tables and Metatables. For core classes like `Modifier` that require chain-call support, we implemented a fallback lookup logic based on Lua table prototypes, allowing developers to implement extension experiences like `Modifier.xxx = function(self) return self.padding(10) end` on the Lua side.

---

## 🛠️ Project Structure

- **`annotations/`**: Contains custom annotations specifically for KSP scanning (e.g., `@LuaBridgePackage`, `@LuaBridgeLocals`).
- **`compiler/`**: A symbol processor built on KSP, responsible for automatically scanning and analyzing Compose source code at compile time to generate static bridge plugin code from Lua to Compose.
- **`compose/`**: The core module of the Lua bridge, including `ComposeBridge`, `LuaComposeRegistry`, underlying Java-Lua object wrappers, and global utility functions (e.g., `dump`).
- **`app/`**: The host application layer, containing the entry point (`MainActivity.kt`) for launching and executing Lua scripts.
- **`example/`**: Contains a large number of Lua example codes used for testing and demonstration.

---

## 🚀 Support Status and TODO List

### ✅ Supported Compose Modules
- [x] `androidx.compose.foundation` (Basic layouts, etc.)
- [x] `androidx.compose.foundation.layout` (Row, Column, Box, Padding constraints, etc.)
- [x] `androidx.compose.material3` (Material Design 3 components)
- [x] `androidx.compose.ui` (Low-level UI controls)
- [x] `androidx.compose.animation` (Advanced core animations, including Crossfade, AnimatedVisibility, etc.)
- [x] `androidx.compose.animation.core` (Underlying animation specs, interpolators, etc.)
- [x] `CompositionLocal` and context-related components (Supports direct calls like `LocalDensity.current` in Lua)
- [x] `DrawScope` and Canvas custom drawing environment integration

### ⏳ To Be Implemented / Improved (TODO)
- [ ] Support for `androidx.compose.material` (M2) or broader third-party libraries
- [ ] Performance tuning for components with complex context scopes like List / LazyColumn
- [ ] More robust Lua error stack reverse mapping (accurately pinpointing errors to specific Compose Nodes)
- [ ] Improve the bidirectional binding efficiency between Compose State and Lua reactive variables

---

## 🎮 How to Test and Run Examples

In the project's **`example/`** folder, we have built-in a large number of Lua-version Compose animation test scripts (`AnimationExample1.lua` to `AnimationExample22.lua`).

> **ℹ️ Tip:** The inspiration and native Kotlin code for these examples come from the well-known [skydoves/compose-animations](https://github.com/skydoves/compose-animations) repository. Currently, these examples have been completely reconstructed through Lua scripts with **1:1 equivalent semantics**, and the rendering results are highly consistent with the native Kotlin code!

### Testing Steps

1. Compile and install `app-debug.apk` onto your Android device.
2. Push any Lua file from `example/` (e.g., `AnimationExample1.lua`) to your phone's sandbox storage path and rename it to `main.lua`.
   The path is:
   `/sdcard/Android/data/com.kulipai.luacompose/files/main.lua`
3. Ensure the file replacement is complete, then open the running App.
4. **Click the Reload button on the page.**
5. The script will be hot-reloaded, instantly rendering the Compose animation or layout effect described in the corresponding Lua code on the page.
