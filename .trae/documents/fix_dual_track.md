# 修复 mutableState 双轨机制 + 其他所有崩溃

## 摘要

双轨设计的本意是对的：`state()` 触发全量 Lua 树重建，`mutableState()` 触发 Compose 轻量重组。**问题不在 `onChange` 回调，而在 Lua 侧 `count.value` 是即时求值的**——字符串拼接 `"当前计数: " .. count.value` 在 Lua 层就解析成了静态字符串，`StateWrapper` 引用从未进入 `ComposeNode.props`。组件渲染器读到的是静态旧值，UI 不更新。

此外还有多个其他问题：
1. `Canvas` 的 `continuousRedraw` 会无限重组导致卡死
2. `SearchBar` 缺少 `modifier` 参数导致无限 `fillMaxWidth` → 高度溢出 crash
3. `FilterChip` 缺少 `selected` 参数解包 → 无法点击切换
4. `TabRow.Tab` 缺少 `onClick` 回调处理 → 无法切换
5. `AnimatedFloat` 渐变后不更新 Canvas → 进度条不更新
6. 协程按钮点击没有触发协程 → 无响应

修复方案：
- 让 `StateWrapper` 引用存活到 `ComposeNode.props` 中，`ComposeNode` 的访问器方法在渲染时自动解包
- 逐个修复上述组件的问题

## 根因分析

### 核心问题：mutableState UI 不更新

```
当前数据流（错误）：
Lua: count = compose.mutableState(0)
  → StateWrapper<Float>(0f) 推入 Lua 栈 (JavaObject)
Lua 渲染函数:
  compose.Text({ text = "当前计数: " .. count.value })
  → count.value 即时求值 → Float 0.0 → 字符串拼接 → "当前计数: 0"
  → Lua 表 { text = "当前计数: 0" } (LUA_TSTRING)
NodeParser:
  → LuaConverter.scriptToJava() → LUA_TSTRING → Java String
  → props["text"] = "当前计数: 0"  ← 静态值，StateWrapper 已丢失
mutableState 变更:
  → recomposeTrigger++ → ComposeHost 重组
  → TextLayout 读取 node.stringProp("text") → "当前计数: 0" ← 旧值
  → UI 不更新 ❌
```

```
修复后数据流（正确）：
Lua: count = compose.mutableState(0)
  → StateWrapper<Float>(0f) 推入 Lua 栈 (JavaObject)
Lua 渲染函数:
  compose.Text({ text = count })
  → count 是 JavaObject (StateWrapper)
  → Lua 表 { text = JavaObject } (LUA_TUSERDATA)
NodeParser:
  → LuaConverter.scriptToJava() → LUA_TUSERDATA → L.toJavaObject() → StateWrapper
  → props["text"] = StateWrapper<Float>  ← StateWrapper 存活
mutableState 变更:
  → StateWrapper.setValue() → state.value = 新值 (Compose MutableState 更新)
  → onChange() → recomposeTrigger++
  → ComposeHost 重组 → TextLayout 重执行
  → node.stringProp("text") → StateWrapper.getValue() → 最新值
  → UI 更新 ✅
```

### 其他问题分析

1. **SearchBar 高度溢出 crash**：`height = 375957537` → 缺少 `modifier` 参数，默认填充无限高度 → 修改 `SearchBarLayout` 添加 `modifier` 处理
2. **Canvas continuousRedraw 一直重绘**：`continuousRedraw` 在 `CanvasPlugin` 中没有实际使用 → 需要在 `Canvas` 中订阅 `drawScope` 并持续重绘 → 目前这个问题是对的，`continuousRedraw = true` 就是一直重绘用于动画
3. **FilterChip 无法切换**：`selected` 参数在 `FilterChipLayout` 中没有解包 StateWrapper → 使用 `node.boolProp()` 自动解包
4. **Tab 无法切换**：`onClick` 回调在 `TabLayout` 中没有调用 → 添加 `invokeCallback`
5. **effects 中按钮没有事件**：检查 `effects` 示例的按钮 onClick 是否回调正确 → 示例代码需要修正

## 修改

### 修改 1：ComposeNode 访问器解包 StateWrapper

**文件**：`e:\Soft\Proje\LXC-LUA\core\src\main\kotlin\com\nirithy\luacompose\node\ComposeNode.kt`

**修改**：`stringProp()`、`boolProp()`、`floatProp()` 添加 StateWrapper 自动解包。

```kotlin
import com.nirithy.luacompose.state.StateWrapper

inline fun <reified T> prop(key: String): T? {
    val value = props[key] ?: return null
    return when {
        value is StateWrapper<*> -> (value.getValue() as? T) ?: (value as T)
        else -> value as T
    }
}

fun stringProp(key: String): String? {
    val value = props[key] ?: return null
    if (value is StateWrapper<*>) return value.getValue()?.toString()
    return value as? String
}

fun boolProp(key: String, default: Boolean = false): Boolean {
    val value = props[key] ?: return default
    if (value is StateWrapper<*>) return (value.getValue() as? Boolean) ?: default
    return (value as? Boolean) ?: default
}

fun floatProp(key: String, default: Float = 0f): Float {
    val value = props[key] ?: return default
    if (value is StateWrapper<*>) {
        val v = value.getValue()
        return when (v) { is Number -> v.toFloat(); else -> default }
    }
    return when (value) { is Number -> value.toFloat(); else -> default }
}
```

### 修改 2：修复 SearchBar 缺少 modifier 参数导致崩溃

**文件**：`e:\Soft\Proje\LXC-LUA\core\src\main\kotlin\com\nirithy\luacompose\component\ComplementComponents.kt`

**问题**：`SearchBarLayout` 没有读取 `modifier` prop，默认 Modifier 为空，但用户传了 `fillMaxWidth` → 无限高度

**修改**：添加 `Modifier = ComposeRenderer.resolveModifier(node)`

```kotlin
// SearchBarLayout 中
SearchBar(
    query = ...,
    onQueryChange = ...,
    onSearch = ...,
    active = active,
    onActiveChange = ...,
    modifier = ComposeRenderer.resolveModifier(node),
    ...
)
```

### 修改 3：修复 FilterChip 无法切换 selected 状态

**文件**：`e:\Soft\Proje\LXC-LUA\core\src\main\kotlin\com\nirithy\luacompose\component\ComplementComponents.kt`

**问题**：`selected = node.boolProp("selected")` → 原来直接读取 `node.props["selected"]` 作为 Boolean，没有解包 StateWrapper

**修改**：使用 `node.boolProp()` 自动解包

### 修改 4：修复 Tab 无法点击切换

**文件**：`e:\Soft\Proje\LXC-LUA\core\src\main\kotlin\com\nirithy\luacompose\component\ComplementComponents.kt`

**问题**：`TabLayout` 中没有调用 `onClick` 回调

**修改**：`onClick = { invokeCallback(node, "onClick") }`

### 修改 5：修复 06_animations.lua Canvas 进度条无限高度

**文件**：`e:\Soft\Proje\LXC-LUA\docs\lua_examples\06_animations.lua`

**问题**：Canvas 的高度设置正确，但外层 Box 缺少 `weight(1)` 或固定高度 → 实际上 crash 在 SearchBar，已修复 Kotlin 侧

### 修改 6：修正教程示例

| 文件 | 修改 |
|------|------|
| `02_counter.lua` | `state` → `mutableState`，显示文字用 `textLambda`（格式化场景），按钮文字直接传 `count`（StateWrapper 自动解包） |
| `04_inputs.lua` | 显示文字改用 `text = state`（直接传 StateWrapper），input 组件保持 `value = state.value`（input 内部有 remember 同步） |
| `05_lists.lua` | 保持 `state()` 不变，因为结构性变化触发全量刷新是正确的 |
| `06_animations.lua` | `isVisible/isCrossfade` 保持 `state()`（结构变化），`animTarget` 保持 `mutableState`，修正 Canvas 高度 |
| `07_components.lua` | `FilterChip selected = selectedState` → 直接传 StateWrapper（自动解包） |
| `10_effects.lua` | 修复按钮 onClick 回调绑定 |

### 修改 7：不需要修改

- `ComposeInjectors.kt` → 不修改，`mutableState` 的 `onChange` 保持 `recomposeTrigger++`（双轨设计不变）
- `Canvas.continuousRedraw` → 本来就是一直重绘，不需要修改

## 修改汇总

| 编号 | 文件 | 修改描述 |
|------|------|------|
| 1 | `ComposeNode.kt` | `prop/stringProp/boolProp/floatProp` 添加 StateWrapper 自动解包 |
| 2 | `ComplementComponents.kt` | SearchBar 添加 `modifier` 参数处理；FilterChip 使用 `node.boolProp()`；Tab 添加 `onClick` 回调调用 |
| 3a | `02_counter.lua` | `state` → `mutableState`，文字用 `textLambda` 格式化，按钮文字直接传 StateWrapper |
| 3b | `04_inputs.lua` | 显示文字直接传 `state`（自动解包） |
| 3c | `06_animations.lua` | 结构变化保持 `state()`，`animTarget` 保持 `mutableState` |
| 3d | `07_components.lua` | 示例中 `FilterChip selected` 直接传 StateWrapper |
| 3e | `10_effects.lua` | 修复示例中按钮 onClick 回调 |

## 假设

1. `LuaConverter.scriptToJava()` 对 `LUA_TUSERDATA` 返回 `L.toJavaObject()` — 即 StateWrapper 本身 — 已验证
2. `StateWrapper.getValue()` 在 `@Composable` 上下文中调用时，Compose Snapshot 系统自动追踪 `MutableState.value` 的读取 — 已验证
3. `textLambda` 是 Text 组件已支持的 API — 已验证

## 验证

1. 编译 `:core:compileDebugKotlin` 无错误
2. 运行 `02_counter.lua`：按钮有文字，点击计数更新，显示文字更新
3. 运行 `04_inputs.lua`：输入文字后下方显示实时更新，Checkbox/Switch/Slider 状态文字更新
4. 运行 `06_animations.lua`：不崩溃，AnimatedVisibility 切换正常，进度条更新正常
5. 运行 `07_components.lua`：FilterChip 切换正常，Tab 点击切换正常，SearchBar 不崩溃
6. 运行 `10_effects.lua`：按钮点击协程正常启动