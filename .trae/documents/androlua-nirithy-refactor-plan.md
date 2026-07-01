# com.androlua → com.nirithy.lxclua 重构计划

## 概述

将 `com.androlua` 包下 52 个 Kotlin 文件（Android Studio 自动从 Java 转换）重构为 `com.nirithy.lxclua`，修复所有编译错误，建立 LuaLexer 的 JFlex 构建时自动生成机制。

## 当前状态分析

### 项目结构
- 7 个 Gradle 模块：app、core、core-apk、editor、compiler、annotations、signer
- `com.androlua` 包位于 `core/src/main/java/com/androlua/`，共 50 个 `.kt` 文件 + 子包 `util/`（11 个文件）
- 核心接口：`LuaContext`（12 属性 + 15 方法），主实现：`LuaActivity`

### 编译错误分布（8 个文件，约 80+ 错误）

| 文件 | 错误数 | 根因类别 |
|------|--------|---------|
| CrashHandler.kt | 2 | `is Array<String>` 泛型擦除 |
| GifDecoder.kt | 8 | val 未初始化 + val 重新赋值 |
| Http.kt | 6 | AsyncTask 泛型、isCancelled 方法不存在、展开运算符 null |
| LuaAccessibilityService.kt | 8 | Path?→Path、onConfigurationChanged 签名、LuaTable 泛型、AccessibilityNodeInfo? |
| LuaActivity.kt | ~40+ | 接口方法未实现、override 缺失、内部类构造、Activity 方法访问、smart cast |
| LuaActivityX.kt | ~15 | 继承 LuaActivity 的接口实现缺失 |
| LuaAdapter.kt | 5 | getContext() 未定义、内部类构造 |
| 其他 Adapter 类 | ~3 | 类似 getContext() 问题 |

### 引用分布（详细）

**Java 文件引用 com.androlua（13 个文件，21 处引用）：**

| 文件 | 引用内容 |
|------|---------|
| `com/luajava/LuaJavaAPI.java` | LuaBitmap, LuaEnhancer, LuaGcable |
| `com/luajava/LuaAbstractMethodInterceptor.java` | LuaContext |
| `com/luajava/LuaMethodInterceptor.java` | LuaContext |
| `com/luajava/LuaInvocationHandler.java` | LuaContext |
| `com/luajava/LuaState.java` | LuaContext（3处：成员变量、参数、返回值） |
| `com/myopicmobile/textwarrior/common/Lexer.java` | LuaLexer, LuaTokenTypes |
| `com/myopicmobile/textwarrior/common/AutoComplete.java` | LuaLexer, LuaTokenTypes |
| `com/myopicmobile/textwarrior/common/AutoIndent.java` | LuaLexer, LuaTokenTypes |
| `com/myopicmobile/textwarrior/common/ReadTask.java` | LuaEditor |
| `com/myopicmobile/textwarrior/common/WriteTask.java` | LuaEditor |
| `android/widget/RippleHelper.java` | TimerTaskX, TimerX |
| `com/android/cglib/proxy/Enhancer.java` | LuaUtil |
| `com/nirenr/screencapture/ScreenShot.java` | LuaAccessibilityService |

**Kotlin 文件引用 com.androlua（16 个文件）：**

| 包 | 文件 |
|----|------|
| `com.luaforge.studio.lxclua` | SplashWelcome.kt, CodeEditScreen.kt, AnalyseScreen.kt, RecyclerAdapterUtil.kt, BitmapUtil.kt |
| `com.nirithy.luacompose` | ComposeBridge.kt, ComposeHost.kt |
| `com.androlua`（内部） | LuaAccessibilityService.kt, LuaBitmapDrawable.kt, LuaTimerTask.kt, LuaAsyncTask.kt, LuaActivity.kt, LuaService.kt, LuaTimer.kt, Http.kt, ClickRunnable.kt |

**AndroidManifest（2 个文件，13 处引用）：**

| 文件 | 引用数 | 组件 |
|------|--------|------|
| `app/src/main/AndroidManifest.xml` | 6 | LuaApplication, Main, LuaActivity, LuaActivityX, LuaService, LuaAccessibilityService |
| `core-apk/src/main/AndroidManifest.xml` | 7 | 同上 + SplashWelcome |

**ProGuard：** `app/proguard-rules.pro` 第 8 行：`-keep class com.androlua.** { *; }`

### LuaLexer 现状

- **已生成版本**：`LuaLexer.kt`（包名 `com.androlua`），由 **JFlex 1.6.1** 从 `D:/JFLEX/bin/lua.flex` 生成，手动转为 Kotlin
- **项目根目录 `lua.flex`**：新版规格文件，包名 `com.nirithy.luaeditor.tools.tokenizer`，包含大量新 token（~130+ 个），**尚未通过 JFlex 重新生成**
- **`LuaTokenTypes.kt`**：90 个枚举值，与旧版 LuaLexer 对应，缺失新版 `lua.flex` 中的 ~60 个 token
- 两套词法系统并行：旧版可用但功能有限，新版规格完整但未生成代码

---

## 变更计划

### 阶段 1：修复当前 Kotlin 编译错误（不改包名）

先让现有代码能编译通过，再改包名，降低风险。

#### 1.1 CrashHandler.kt — 泛型擦除（2 处）
- 第 124 行、139 行：`is Array<String>` → `is Array<*>`
- 同时将 `(obj as Array<String?>)` 改为 `@Suppress("UNCHECKED_CAST")` 包裹的转换

#### 1.2 GifDecoder.kt — val→var（8 处）
- 第 26-28 行：`gct`, `lct`, `act` 三个 `private var` 需要初始化为 `null`
- 第 75-78 行：`prefix`, `suffix`, `pixelStack`, `pixels` 四个 `private var` 需要初始化为 `null`
- 第 598 行：`val i = 0` → `var i = 0`（在 `decodeImageData()` 中 `i` 会被重新赋值）

#### 1.3 Http.kt — AsyncTask 泛型 + 属性访问（6 处）
- 第 413 行：`post(url, data, file, null, header, callback)` 调用歧义，需要显式指定参数类型
- 第 606 行：`HttpTask` 需要实现 `doInBackground(vararg params: Any?): Any?`
- 第 616 行：`mData` 属性需要初始化
- 第 630 行：`doInBackground` 签名改为 `override fun doInBackground(vararg params: Any?): Any?`
- 第 736、748、795 行：`isCancelled` 是 `AsyncTask` 的属性，但 `AsyncTaskX` 可能没有此属性，需要检查 `AsyncTaskX` 的定义
- 第 797 行：`*foo` 展开运算符前加 `!!` 处理 nullable

#### 1.4 LuaAccessibilityService.kt — 类型不匹配（8 处）
- 第 182 行：`Path?` → `Path`，加 `?: return` 或 `!!`
- 第 275 行：`onConfigurationChanged` 参数类型改为 `Configuration?`
- 第 408、412、425 行：`LuaTable<*, *>` 泛型使用 `@Suppress("UNCHECKED_CAST")`
- 第 525、626 行：`AccessibilityNodeInfo?` → `AccessibilityNodeInfo`，加 `?: return` 或 `!!`
- 第 760-761 行：`AccessibilityNodeInfo?` 的安全调用 `?.`

#### 1.5 LuaActivity.kt — 核心修复（~40+ 处）

这是最复杂的文件，所有错误源于 Android Studio 自动转换时未正确处理 Java→Kotlin 的接口实现差异。

**问题分类：**

A. **接口方法未加 override（~15 个方法）**
   `LuaContext` 接口的方法在 Java 中以 getter/setter 函数形式实现，转换后需要 `override`：
   - `getLuaPath(path: String?): String?` → `override fun getLuaPath(path: String?): String?`
   - `getLuaPath(dir: String?, name: String?): String?` → 同上
   - `getLuaExtPath(path: String?): String?` → 同上
   - `getLuaExtPath(dir: String?, name: String?): String?` → 同上
   - `getLuaLpath(): String?` → 同上
   - `getLuaCpath(): String?` → 同上
   - `getContext(): Context?` → 同上
   - `getLuaState(): LuaState?` → 同上
   - `getLuaExtDir(): String?` → 同上
   - `getLuaExtDir(dir: String?): String?` → 同上
   - `setLuaExtDir(dir: String?)` → 同上
   - `getLuaDir(): String?` → 同上
   - `getWidth(): Int` → 同上
   - `getHeight(): Int` → 同上
   - `getGlobalData(): MutableMap<*, *>?` → 同上
   - `getSharedData(): Any?` → 同上
   - `doFile(path: String?, vararg arg: Any?): Any?` → 同上
   - `sendMsg(msg: String?)` → 同上
   - `sendError(title: String?, msg: Exception?)` → 同上
   - `call(func: String?, vararg args: Any?)` → 同上
   - `onReceive(context: Context?, intent: Intent?)` → 同上

B. **getClassLoaders() 签名不匹配**
   - 接口定义为 `val classLoaders: ArrayList<ClassLoader?>?`（Kotlin property）
   - Java 文件中的引用期望 `getClassLoaders()` 方法
   - 需要确保属性名和 getter 都与接口一致

C. **方法签名不匹配**
   - `onCreate(savedInstanceState: Bundle?)` → `override fun onCreate(savedInstanceState: Bundle?)`
   - `onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray)` 需要改为 `Array<out String>`

D. **内部类构造问题**
   - `MainHandler` 内部类：需要添加 `inner` 修饰符，或改为独立类
   - 当前代码 `MainHandler()` 在外部调用，需要 `inner class` 或改为 `nested class`

E. **Activity 方法访问**
   - `getAssets()`、`getResources()` 等是 `ContextWrapper` 的方法，在 Kotlin 中为属性 `assets`、`resources`
   - 转换后 `getAssets()` → `assets`，`getResources()` → `resources`

F. **null 传给非空参数**
   - 第 404、405、509、523 行：`null` 传给 `String` 类型参数，改为 `String?`
   - 第 1088、1093、1100 行：`null` 传给 `Array<Any?>`，改为 `Array<Any?>?` 或使用 `emptyArray()`

G. **其他类型问题**
   - 第 529、534 行：`String?` 传给 `String`，加 `?: ""` 或 `!!`
   - 第 586 行：`OnReceiveListener?` 传给 `OnReceiveListener`，加 `!!` 或 `?: return`
   - 第 1064、1118 行：`LuaObject?` 传给 `LuaObject`，加 `?: return`
   - 第 1076 行：`task()` 调用歧义，显式指定参数
   - 第 1162 行：`setPeriod()` 不存在，检查 `LuaTimer` 的 API
   - 第 1301 行：`MutableMap` 初始化类型不匹配，使用 `mutableMapOf()`
   - 第 1316 行：`L` 的 smart cast 失败，缓存到局部变量
   - 第 838-850 行：`getInstance()` 方法不存在，需要检查具体类

#### 1.6 LuaActivityX.kt — 继承修复
- `LuaActivityX` 是 `LuaActivity` 的空子类（仅 6 行）
- 所有错误来自父类 `LuaActivity` 的接口实现缺失
- **修复**：LuaActivity 修复后自动解决

#### 1.7 LuaAdapter.kt — Context 访问 + 内部类（5 处）
- 第 96、206、403 行：`mContext.getContext()` → `mContext.context`（`LuaContext` 接口的 `context` 属性）
- 第 283 行：`AsyncLoader` 内部类构造，添加 `inner` 修饰符
- 第 423 行：`ArrayFilter` 内部类构造，添加 `inner` 修饰符

#### 1.8 其他 Adapter 类
- `LuaArrayAdapter.kt`、`LuaExpandableListAdapter.kt`、`LuaMultiAdapter.kt`：搜索 `getContext()`，改为 `context`
- `LuaView.kt`、`LuaWebView.kt`：搜索 `getContext()` 引用
- `LuaBroadcastReceiver.kt`：搜索 `getLuaState()` 等接口方法引用

### 阶段 2：包名重命名 `com.androlua` → `com.nirithy.lxclua`

#### 2.1 移动文件
- 将 `core/src/main/java/com/androlua/` 下所有 52 个 `.kt` 文件移动到 `core/src/main/kotlin/com/nirithy/lxclua/`
- 子包 `util/` 整体移动到 `core/src/main/kotlin/com/nirithy/lxclua/util/`

#### 2.2 更新包声明
- 所有 52 个文件的 `package com.androlua` → `package com.nirithy.lxclua`
- `util/` 子包：`package com.androlua.util` → `package com.nirithy.lxclua.util`

#### 2.3 更新 import 引用（按文件类型）

**Java 文件（13 个文件，21 处）：**

| 文件 | 替换内容 |
|------|---------|
| `com/luajava/LuaJavaAPI.java` | `import com.androlua.LuaBitmap;` → `com.nirithy.lxclua.LuaBitmap` 等 3 处 |
| `com/luajava/LuaAbstractMethodInterceptor.java` | `import com.androlua.LuaContext;` |
| `com/luajava/LuaMethodInterceptor.java` | `import com.androlua.LuaContext;` |
| `com/luajava/LuaInvocationHandler.java` | `import com.androlua.LuaContext;` |
| `com/luajava/LuaState.java` | `com.androlua.LuaContext` → `com.nirithy.lxclua.LuaContext`（3 处） |
| `com/myopicmobile/textwarrior/common/Lexer.java` | `com.androlua.LuaLexer` + `LuaTokenTypes` |
| `com/myopicmobile/textwarrior/common/AutoComplete.java` | 同上 |
| `com/myopicmobile/textwarrior/common/AutoIndent.java` | 同上 |
| `com/myopicmobile/textwarrior/common/ReadTask.java` | `com.androlua.LuaEditor` |
| `com/myopicmobile/textwarrior/common/WriteTask.java` | `com.androlua.LuaEditor` |
| `android/widget/RippleHelper.java` | `com.androlua.util.TimerTaskX` + `TimerX` |
| `com/android/cglib/proxy/Enhancer.java` | `com.androlua.LuaUtil` |
| `com/nirenr/screencapture/ScreenShot.java` | `com.androlua.LuaAccessibilityService` |

**Kotlin 文件（16 个文件）：**

| 文件 | 替换内容 |
|------|---------|
| `app/.../SplashWelcome.kt` | `import com.androlua.*` → `import com.nirithy.lxclua.*` |
| `app/.../CodeEditScreen.kt` | 同上 |
| `app/.../AnalyseScreen.kt` | 同上 |
| `core/.../RecyclerAdapterUtil.kt` | 同上 |
| `core/.../BitmapUtil.kt` | 同上 |
| `core/.../ComposeBridge.kt` | `import com.androlua.DebugLogger` |
| `core/.../ComposeHost.kt` | `import com.androlua.DebugLogger` |
| 8 个 com.androlua 内部文件 | 包名改为 `com.nirithy.lxclua` 后自动解决 |

#### 2.4 更新 AndroidManifest（2 个文件，13 处）
- `app/src/main/AndroidManifest.xml`：6 处 `com.androlua.*` → `com.nirithy.lxclua.*`
- `core-apk/src/main/AndroidManifest.xml`：7 处 `com.androlua.*` → `com.nirithy.lxclua.*`

#### 2.5 更新 ProGuard 规则
- `app/proguard-rules.pro` 第 8 行：`-keep class com.androlua.**` → `-keep class com.nirithy.lxclua.**`

### 阶段 3：LuaLexer 自动化构建

#### 3.1 分析 lua.flex 并同步 LuaTokenTypes
- 用 Python 脚本解析 `lua.flex`，提取所有 `return XXX` 语句中的 token 名称
- 对比 `LuaTokenTypes.kt` 现有枚举值，找出缺失的 ~60 个 token
- 将缺失的枚举值添加到 `LuaTokenTypes.kt`（保持枚举顺序，新增放末尾）

#### 3.2 修复 lua.flex 包名
- 将 `package com.nirithy.luaeditor.tools.tokenizer;` 改为 `package com.nirithy.lxclua;`
- 同步修正 `%class` 和 `%type` 声明

#### 3.3 编写 Python 分析脚本
- 创建 `scripts/analyze_lexer.py`
- 功能：解析 `lua.flex`，提取所有 token 返回值、规则定义、状态机
- 输出：缺失的 token 列表、规则统计

#### 3.4 配置 Gradle JFlex 生成任务
- 在 `core/build.gradle.kts` 中：
  - 添加 `jflex` 依赖（JFlex 1.9.1，通过 Maven Central）
  - 创建 `generateLuaLexer` JavaExec 任务
  - 输入：`lua.flex`（项目根目录）
  - 输出：`build/generated/source/jflex/com/nirithy/lxclua/LuaLexer.java`
  - 将生成目录加入 `sourceSets.main.java.srcDirs`
  - `tasks.named("compileKotlin")` 依赖 `generateLuaLexer`

#### 3.5 替换 LuaLexer.kt
- 删除手动转换的 `core/src/main/java/com/androlua/LuaLexer.kt`
- 构建时自动生成 `LuaLexer.java`，Kotlin 可直接调用

### 阶段 4：验证

#### 4.1 编译验证
- `./gradlew :core:compileDebugKotlin` 确保 0 错误
- `./gradlew :app:assembleDebug` 确保完整构建

#### 4.2 运行时验证
- 在设备上安装 debug APK
- 验证 Lua 脚本执行正常
- 验证代码编辑器词法高亮正常

---

## 假设与决策

1. **不改 com.luajava 包名**：独立 Lua-Java 桥接库，保持原样
2. **不改 com.myopicmobile.textwarrior 包名**：第三方编辑器库
3. **LuaLexer 生成 Java 而非 Kotlin**：JFlex 只生成 Java，Kotlin 可直接调用
4. **保留 LuaActivity.kt 而非回退 Java**：已投入的 Kotlin 转换保留，只修复编译错误
5. **包名 `com.nirithy.lxclua`**：与 `com.nirithy.luacompose` 对齐
6. **lua.flex 使用新版**：根目录的 `lua.flex` 是较新版本（~130+ token），但需验证其生成的 Java 代码是否与现有 `LuaEditor` 兼容
7. **阶段 1 不改包名**：先在当前包名下修复编译错误，验证通过后再改名，降低风险

## 风险

- **LuaActivity.kt 错误量最大**：~40+ 错误，需逐行对比原 Java 文件修复
- **lua.flex 兼容性**：新版 `lua.flex` 的 token 集合与旧版差异大，生成的 `LuaLexer` 可能不兼容现有 `LuaEditor` 调用方式，需要验证
- **Java 文件引用**：`com.luajava` 和 `com.myopicmobile` 是 Java 文件，修改 import 后需确保编译通过
- **内部类构造**：`LuaAdapter` 的 `AsyncLoader` 和 `ArrayFilter` 内部类改为 `inner` 后，外部引用方式可能变化