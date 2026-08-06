# 构建与调试指南

本文档详细说明如何构建项目、配置开发环境以及调试 Lua VM 和 Android 应用层的问题。

---

## 1. 环境配置

### 1.1 系统要求

| 组件 | 版本 | 说明 |
|------|------|------|
| Android Studio | Hedgehog (2023.1.1)+ | 推荐使用最新稳定版 |
| JDK | 17+ | Android Studio 内嵌 JDK |
| Android SDK | API 24-36 | minSdk=24, targetSdk=35, compileSdk=36 |
| NDK | 29.0.13004108 | 原生代码编译 |
| CMake | 3.22+ | 原生构建工具 |
| Gradle | 9.1.0 | 项目构建系统 |

### 1.2 首次设置步骤

1. **安装 Android Studio**

   下载最新稳定版并安装，确保安装 Android SDK 和 NDK。

2. **导入项目**

   ```
   File → Open → 选择项目根目录
   ```

3. **配置 SDK 路径**

   编辑 `local.properties`:
   ```properties
   sdk.dir=C\:\\Users\\<username>\\AppData\\Local\\Android\\Sdk
   ndk.dir=C\:\\Users\\<username>\\AppData\\Local\\Android\\Sdk\\ndk\\29.0.13004108
   ```

4. **首次 Gradle 同步**

   Gradle 会自动下载所有依赖，首次可能需要较长时间（建议联网）。

---

## 2. 构建项目

### 2.1 构建类型

| 类型 | 命令 | 产物 | 特点 |
|------|------|------|------|
| Debug | `./gradlew :app:assembleDebug` | `app-debug.apk` | 可调试、日志输出、jniDebuggable=true |
| Release | `./gradlew :app:assembleRelease` | `app-release.apk` | 优化、签名、无调试信息 |

### 2.2 常用 Gradle 任务

```bash
# 清理构建产物
./gradlew clean

# 编译 Debug 版本
./gradlew :app:assembleDebug

# 安装到连接的设备
./gradlew :app:installDebug

# 运行测试
./gradlew :app:testDebugUnitTest

# 构建并运行
./gradlew :app:installDebug :app:run

# 仅构建 Lua VM
./gradlew :app:externalNativeBuildDebug

# 查看所有可用任务
./gradlew tasks
```

### 2.3 模块依赖构建

```bash
# 构建所有依赖模块
./gradlew :core:assemble :editor:assemble :signer:assemble :compiler:assemble

# 构建 core-apk (会为 app 模块生成 assets/core.apk)
./gradlew :core-apk:assembleRelease
```

---

## 3. Lua VM 独立构建

### 3.1 编译目标

进入 `app/src/main/jni/lua/` 目录，执行对应平台的 make 命令：

```bash
cd app/src/main/jni/lua/

# Linux (GCC)
make linux

# Windows (MinGW-w64)
make mingw

# Windows 静态链接
make mingw-static

# Android (Termux 环境)
make termux

# macOS
make macosx

# WebAssembly
make wasm

# WASM 最小版本
make wasm-minimal

# WASM LSP 服务器
make wasmlsp

# 清理
make clean
```

### 3.2 编译产物

| 产物 | 说明 |
|------|------|
| `lxclua` / `lxclua.exe` | Lua 解释器 (交互式 REPL) |
| `luac` / `luac.exe` | Lua 字节码编译器 |
| `lbcdump` / `lbcdump.exe` | 字节码反汇编器 |
| `luaccheck` / `luaccheck.exe` | 字节码检查器 |
| `lxclua-lsp` / `lxclua-lsp.exe` | LSP 语言服务器 |
| `liblxclua.a` | Lua 静态库 |
| `lxclua.dll` / `liblxclua.so` | Lua 动态库 |
| `lxclua.js` + `lxclua.wasm` | WebAssembly 版本 |

### 3.3 运行测试

```bash
# 运行完整的测试套件
./lxclua tests/verify_docs_full.lua      # 验证所有文档中的特性
./lxclua tests/test_parser_features.lua   # 语法特性测试
./lxclua tests/test_advanced_parser.lua  # 高级语法测试

# 快速测试
./lxclua -e "print('Hello, LXCLUA!')"

# 查看版本
./lxclua -v
```

---

## 4. 调试 Android 应用

### 4.1 调试配置

在 Android Studio 中创建调试配置：

1. 点击 `Run` → `Edit Configurations`
2. 选择 `app` 模块
3. 确保 `Deploy` 设置为 `APK from app bundle`

### 4.2 Logcat 调试

**过滤标签**:

```bash
# 查看所有 LuaForge Studio 日志
adb logcat | grep -E "LXCLUA|lua|LuaForge"

# 过滤 JNI 调用日志
adb logcat -s "JNI" "lua" "LuaExecutor"

# 查看 Lua VM 日志
adb logcat -s "lua"

# 导出日志到文件
adb logcat -d > logcat.txt
```

**常用 logcat 过滤表达式**:

```
# 仅看应用进程的日志
adb logcat --pid=$(adb shell pidof com.luaforge.studio.lxclua)

# 过滤错误级别以上
adb logcat *:E

# 带时间戳
adb logcat -v time
```

### 4.3 Kotlin/Compose 调试

```kotlin
// 在代码中添加日志
import android.util.Log

class EditorViewModel : ViewModel() {
    companion object {
        private const val TAG = "EditorViewModel"
    }
    
    fun executeScript(code: String) {
        Log.d(TAG, "Executing script: ${code.take(100)}...")
        
        try {
            val result = luaExecutor.execute(code)
            Log.d(TAG, "Execution result: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed", e)
        }
    }
}
```

**断点调试**:
- 在 Kotlin 代码中点击行号设置断点
- 点击 `Debug` 按钮启动调试会话
- 使用 `Step Over (F8)`, `Step Into (F7)`, `Step Out (Shift+F8)` 控制执行流程

### 4.4 Profiler 分析

1. 打开 `View` → `Tool Windows` → `Profiler`
2. 选择应用进程
3. 选择分析类型:
   - **CPU**: 查看方法调用栈和耗时
   - **Memory**: 检测内存泄漏
   - **Network**: 网络请求监控
   - **Energy**: 电量消耗分析

---

## 5. 调试 Lua VM (原生层)

### 5.1 C/C++ 断点调试

在 Android Studio 中支持原生代码调试:

1. 编辑 `Run/Debug Configurations`
2. 在 `Debugger` 选项卡选择 `Dual (Java + Native)` 或 `Native Only`
3. 在 C/C++ 源文件中设置断点
4. 启动调试会话

**LLDB 调试命令**:
```
# 设置断点
b lvm.c:1234

# 继续执行
c

# 查看变量
p L->top
p ci->u.l.base

# 调用栈
bt

# 列出源码
list
```

### 5.2 Android 平台 Lua 日志

```c
// 在 Lua VM C 代码中使用 Android 日志
#include <android/log.h>

#define LOG_TAG "LXCLua"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 使用示例
LOGD("Executing function: %s", lua_tostring(L, 1));
LOGE("Error in lvm: code=%d", error_code);
```

### 5.3 写入外部日志文件

为避免 logcat 缓冲区溢出导致日志丢失，可以将关键日志写入外部文件：

```c
#include <stdio.h>

static FILE* g_log_file = NULL;

void init_external_log(const char* path) {
    g_log_file = fopen(path, "w");
    if (!g_log_file) {
        LOGE("Failed to open log file: %s", path);
    }
}

void write_log(const char* fmt, ...) {
    if (g_log_file) {
        va_list args;
        va_start(args, fmt);
        vfprintf(g_log_file, fmt, args);
        va_end(args);
        fflush(g_log_file);
    }
}

// 在 Android 中将日志写入 /sdcard/
init_external_log("/sdcard/a.log");
write_log("Lua execution started: pid=%d\n", getpid());
```

之后通过 `adb pull` 拉取完整日志:
```bash
adb pull /sdcard/a.log .
```

### 5.4 Lua 调试库

```lua
-- 使用内置 debug 库
debug.sethook(function(event)
    local info = debug.getinfo(2, "nSl")
    print(string.format("[%s] %s:%d", event, info.short_src, info.currentline))
end, "lcr")  -- l=line, c=call, r=return

-- 使用 debugger 模块 (如有)
local dbg = require("debugger")
dbg.listen("0.0.0.0", 9168)  -- 远程调试端口
```

---

## 6. 性能分析

### 6.1 Lua VM 性能基准

```lua
-- benchmark.lua
local start = os.clock()

-- 测试代码
local function fib(n)
    if n < 2 then return n end
    return fib(n-1) + fib(n-2)
end

for i = 1, 10 do
    fib(30)
end

local elapsed = os.clock() - start
print(string.format("耗时: %.4f 秒", elapsed))
```

```bash
./lxclua benchmark.lua
```

### 6.2 JIT 性能对比

```lua
local jit = require("jit")

-- 禁用 JIT
jit.off()
local start = os.clock()
-- 执行代码...
local jit_off_time = os.clock() - start

-- 启用 JIT
jit.on()
start = os.clock()
-- 执行相同代码...
local jit_on_time = os.clock() - start

print(string.format("JIT off: %.4fs", jit_off_time))
print(string.format("JIT on:  %.4fs", jit_on_time))
print(string.format("加速比: %.2fx", jit_off_time / jit_on_time))
```

### 6.3 内存使用检查

```lua
-- 获取当前内存使用 (KB)
local mem = collectgarbage("count")
print(string.format("Lua 内存使用: %.2f MB", mem / 1024))

-- 强制回收
collectgarbage("collect")
local mem_after = collectgarbage("count")
print(string.format("回收后: %.2f MB", mem_after / 1024))
```

---

## 7. 故障排除

### 7.1 构建问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| `UnsatisfiedLinkError` | native 库未正确加载 | 检查 ABI 兼容性；确保 `System.loadLibrary` 调用了正确的库名 |
| NDK 编译失败 | NDK 版本不匹配或缺少工具链 | 检查 `local.properties` 中的 NDK 路径；更新 NDK |
| JNI 找不到方法 | 方法签名不匹配 | 核对 Java/Kotlin 声明和 C 函数签名 |
| Gradle 依赖冲突 | 多个模块依赖同一库不同版本 | 使用 `exclude` 排除冲突依赖 |
| `core.apk` 未找到 | `:core-apk` 模块未构建 | 运行 `:core-apk:assembleRelease` |

### 7.2 运行时问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| Lua VM 启动失败 | 内存不足 | 检查可用内存；减少 Lua 栈大小 |
| 脚本执行超时 | 死循环或无限递归 | 设置执行超时机制 |
| UI 卡顿 | Lua 代码阻塞主线程 | 将长任务移到后台线程 |
| JNI 崩溃 | 野指针或已释放引用 | 检查 JNI 引用管理 |
| Canvas 渲染异常 | 连续重绘逻辑错误 | 检查 `continuousRedraw` 回调函数 |

### 7.3 调试技巧

**检查 Native 库是否正確打包**:

```bash
# 查看 APK 中是否包含 native 库
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep liblua

# 输出示例:
# lib/arm64-v8a/libluajava.so
```

**验证 Lua VM 状态**:

```kotlin
// Kotlin 中检查 Lua VM 状态
class LuaHealthChecker(private val luaExecutor: LuaExecutor) {
    
    fun check(): HealthStatus {
        return try {
            val version = luaExecutor.execute("return _VERSION")
            val hasJit = luaExecutor.execute("return jit ~= null")
            val memUsage = luaExecutor.execute("return collectgarbage('count')")
            
            HealthStatus.healthy(version, hasJit, memUsage)
        } catch (e: Exception) {
            HealthStatus.unhealthy(e)
        }
    }
}
```

**使用 AddressSanitizer 检测内存错误** (开发调试):

```makefile
# 在 Makefile 中添加
CFLAGS += -fsanitize=address -fno-omit-frame-pointer
LDFLAGS += -fsanitize=address
```

---

## 8. 发布打包

### 8.1 签名配置

项目使用统一的调试签名密钥 (`diferline.jks`)，生产环境需要替换为正式密钥。

```gradle
// app/build.gradle.kts

signingConfigs {
    create("release") {
        keyAlias = "your_key_alias"
        keyPassword = "your_key_password"
        storeFile = file("your_keystore.jks")
        storePassword = "your_store_password"
    }
}
```

### 8.2 版本管理

**版本号规则**:
- `versionName`: 语义化版本 `MAJOR.MINOR.PATCH` (如 `1.3.5`)
- `versionCode`: 整数版本号，每次发布递增 (如 `20260806`)

**自动版本代码生成**:

```kotlin
fun getVersionCode(): Int {
    val date = LocalDate.now()
    return date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
}
```

### 8.3 发布检查清单

构建 Release 版本前，确保:

- [ ] 更新 `versionName` 和 `versionCode` (在 `app/build.gradle.kts`)
- [ ] 移除或禁用所有调试日志
- [ ] 验证所有功能正常运行
- [ ] 运行完整的测试套件
- [ ] 确认 ProGuard/R8 配置正确
- [ ] 使用正确的签名密钥
- [ ] 进行性能分析，无内存泄漏
- [ ] 在不同设备上验证兼容性

### 8.4 多渠道构建

```gradle
// 创建多渠道
flavorDimensions += "version"
productFlavors {
    create("free") {
        dimension = "version"
        applicationIdSuffix = ".free"
    }
    create("pro") {
        dimension = "version"
        applicationIdSuffix = ".pro"
    }
}

// 构建所有渠道
./gradlew assembleFreeRelease
./gradlew assembleProRelease
```

---

## 9. CI/CD 配置

### 9.1 GitHub Actions

```yaml
# .github/workflows/build.yml
name: Build

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Setup Android SDK
      uses: android-actions/setup-android@v3
      
    - name: Install NDK
      run: sdkmanager --install "ndk;29.0.13004108"
      
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
      
    - name: Build Debug
      run: ./gradlew assembleDebug
      
    - name: Run tests
      run: ./gradlew testDebugUnitTest
      
    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/*.apk
```

---

## 10. 常见工具脚本

### 10.1 项目自带脚本

**目录**: `scripts/`

| 脚本 | 功能 |
|------|------|
| `check_strings.py` | 检查硬编码字符串 |
| `find_hardcoded.py` | 查找代码中的硬编码值 |

### 10.2 常用命令速查

```bash
# 构建并安装到设备
./gradlew :app:installDebug

# 单元测试
./gradlew :app:testDebugUnitTest

# UI 测试
./gradlew :app:connectedDebugAndroidTest

# 清理
./gradlew clean

# 查看所有任务
./gradlew tasks --all

# 依赖树
./gradlew :app:dependencies

# 检查依赖更新
./gradlew dependencyUpdates

# 导出测试覆盖率
./gradlew :app:createDebugCoverageReport

# 查找大文件
./gradlew :app:analyzeDebugDependencies
```

---

## 附录：目录结构速查

```
项目根目录/
├── app/                           # 主应用模块
│   ├── build.gradle.kts           # 构建配置
│   ├── proguard-rules.pro         # 混淆规则
│   └── src/main/
│       ├── kotlin/                # Kotlin 源码
│       ├── jni/                   # JNI 和原生代码
│       ├── assets/                # 资产文件
│       └── res/                   # Android 资源符
├── core/                          # 核心模块
├── core-apk/                      # 核心 APK 模块
├── editor/                        # 编辑器模块
├── signer/                        # 签名工具模块
├── compiler/                      # 编译器模块
├── docs/                          # 文档目录
├── scripts/                       # 工具脚本
├── gradle/                        # Gradle 配置
│   └── libs.versions.toml         # 版本目录
├── build.gradle.kts               # 根构建脚本
├── settings.gradle.kts            # 项目设置
├── local.properties               # 本地 SDK 路径
├── gradlew                        # Gradle 包装脚本
└── gradlew.bat                    # Windows Gradle 脚本
```
