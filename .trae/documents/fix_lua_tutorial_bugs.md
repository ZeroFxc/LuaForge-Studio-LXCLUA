# 修复 LuaCompose 官方教程示例 Bug 计划

## 摘要

通过分析 `error.txt` 中的日志和 5 个教程文件，发现 8 个 Bug。核心根因是 **两层面**：
1. **Kotlin 侧**：`Button` 组件未处理 `text` prop，导致按钮无文字显示
2. **Lua 教程侧**：`mutableState` 只触发轻量重组（`recomposeTrigger++`），不重建 Lua 节点树，导致所有读取 `node.props` 的组件拿到的是初次解析时的静态值，后续状态变更不会反映到 UI 上

---

## 当前状态分析

### 渲染管线回顾

```
mutableState.value = newValue
  → recomposeTrigger++        (仅递增计数器)
  → ComposeHost 重组           (Compose 原生重组，但不重建 Lua 节点树)
  → 所有组件渲染器重新执行     (但 ComposeNode.props 是旧的静态值)
  → UI 不变！                 ❌

state.value = newValue
  → scheduleRefresh()         (调度全量刷新)
  → refreshNodeTree()         (重新执行 Lua 渲染函数)
  → ComposeNode 树重建        (props 包含最新值)
  → ComposeHost 重组
  → UI 更新                   ✅
```

### 关键发现

- `Text` 组件支持 `textLambda` 回调：在每次 Compose 重组时调用回调获取最新值，可以绕过静态 ComposeNode 的限制
- `AnimatedVisibility` 读取 `node.boolProp("visible")`，是静态值
- `animateFloatAsState` 返回 `AnimatedFloat` JavaObject，不是 Lua number，需要 `.value` 读取
- `05_lists.lua` 的交替渲染失败是 `ComposeBridge` 状态清理不完整导致

---

## 提议的修改

### 修改 1：修复 Button 组件支持 `text` prop（Kotlin 侧）

**文件**：`e:\Soft\Proje\LXC-LUA\core\src\main\kotlin\com\nirithy\luacompose\component\inputcomponents.kt`

**问题**：`ButtonLayout` 渲染 `children` 作为按钮内容，但教程使用 `text = "+1"` prop。按钮无子节点 → 内容区域为空 → 按钮不可见、无法点击。

**修改**：在 `ButtonLayout` 中，当 `children` 为空且存在 `text` prop 时，自动渲染 `Text` 作为按钮内容。

```kotlin
// 修改前
Button(...) { ComposeRenderer.RenderChildren(node) }

// 修改后
Button(...) {
    if (node.children.isNotEmpty()) {
        ComposeRenderer.RenderChildren(node)
    } else {
        val text = node.stringProp("text")
        if (text != null) Text(text)
    }
}
```

**同样修改** `TextButtonLayout`、`OutlinedButtonLayout`。

---

### 修改 2：修复 02_counter.lua 教程

**文件**：`e:\Soft\Proje\LXC-LUA\docs\lua_examples\02_counter.lua`

**问题**：按钮文字不显示（由修改 1 修复），但计数显示文字 `"当前计数: " .. count.value` 也不会更新，因为 `count` 是 `mutableState`，不会重建 Lua 节点树。

**修改**：
- 将 `count` 改为 `state()`（按钮点击触发全量刷新，天然适合计数器场景）
- 或将显示文字改为 `textLambda` 回调

```lua
-- 修改前
local count = compose.mutableState(0)

-- 修改后：计数器场景用 state 更合适
local count = compose.state(0)
```

---

### 修改 3：修复 04_inputs.lua 教程

**文件**：`e:\Soft\Proje\LXC-LUA\docs\lua_examples\04_inputs.lua`

**3a — TextField 输入后显示文字不更新**：`textValue` 是 `mutableState`，`onValueChange` 回调触发 `recomposeTrigger++`，但显示文字 `"你输入了: " .. textValue.value` 是静态 `text` prop，不会更新。

**修改**：使用 `textLambda` 回调实现响应式文字显示。

```lua
-- 修改前
compose.Text({
    text = "你输入了: " .. textValue.value,
    fontSize = 14,
    color = 0xFF666666,
})

-- 修改后
compose.Text({
    textLambda = function()
        return "你输入了: " .. tostring(textValue.value)
    end,
    fontSize = 14,
    color = 0xFF666666,
})
```

**3b — Checkbox/Switch 状态文字不更新**：同样问题，`mutableState` 变更后显示文字不变。

**修改**：同样使用 `textLambda`。

**3c — Slider 值显示不更新**：`"当前值: " .. math.floor(sliderValue.value)` 是静态 text。

**修改**：同样使用 `textLambda`。

**3d — 综合表单中的文字**：同样需要 `textLambda`。

---

### 修改 4：修复 05_lists.lua 教程

**文件**：`e:\Soft\Proje\LXC-LUA\docs\lua_examples\05_lists.lua`

**4a — 卡片点击无选中效果**：`selectedIndex` 是 `mutableState`，`buildItemList()` 在 `render()` 内部调用生成静态子节点列表。点击后 `mutableState` 变更触发轻量重组，但 `buildItemList()` 不重新执行，卡片颜色不变。

**修改**：将 `selectedIndex` 改为 `state()`（列表选中是结构性变化，需要重建节点树）。同时为卡片添加点击回调。

```lua
-- 修改前
local selectedIndex = compose.mutableState(-1)

-- 修改后
local selectedIndex = compose.state(-1)
```

**4b — 卡片缺少点击回调**：`buildItemList()` 中 Card 没有 `onClick`。

**修改**：为每个 Card 添加 `clickableLua` modifier 或通过其他方式处理点击。

```lua
-- 在 Card 的 modifier 中添加
modifier = compose.Modifier()
    .fillMaxWidth()
    .padding(8, 4, 8, 4)
    .clickableLua(function()
        if selectedIndex.value == i then
            selectedIndex.value = -1
        else
            selectedIndex.value = i
        end
    end),
```

**4c — 交替渲染失败**（第二次运行不渲染）：`ComposeBridge.resetState()` 可能未完全清理上次运行的状态，导致 `refreshNodeTree` 中 `stateIndex` 不匹配或 `ComposeView` 未正确清理。

**修改**：在 `LuaActivity.onDestroy()` 或 `onPause()` 中确保清理 ComposeView，在 `ComposeBridge.resetState()` 中确保清空所有缓存。

---

### 修改 5：修复 06_animations.lua 教程

**文件**：`e:\Soft\Proje\LXC-LUA\docs\lua_examples\06_animations.lua`

**5a — `string.format` 崩溃**：`animateFloatAsState` 返回 `AnimatedFloat` JavaObject，不是 Lua number。`string.format("%.1f", animatedValue)` 收到 JavaObject 导致 `bad argument #2 to 'format' (number expected, got JavaObject)`。

**修改**：使用 `animatedValue.value` 读取数值。

```lua
-- 修改前
local animatedValue = compose.animateFloatAsState(animTarget.value)
-- ...
text = "动画值: " .. string.format("%.1f", animatedValue),

-- 修改后
textLambda = function()
    return "动画值: " .. string.format("%.1f", animatedValue.value)
end,
```

**5b — AnimatedVisibility 不切换**：`isVisible` 是 `mutableState`，`AnimatedVisibility` 组件读取 `visible` prop 是静态值。

**修改**：将 `isVisible` 改为 `state()`（可见性切换是结构性变化）。

```lua
-- 修改前
local isVisible = compose.mutableState(true)

-- 修改后
local isVisible = compose.state(true)
```

**5c — Crossfade 不切换**：`isCrossfade` 是 `mutableState`，同样问题。

**修改**：改为 `state()`。

**5d — 动画进度条不更新**：`animatedValue` 是 `AnimatedFloat`，`fillMaxWidth(animatedValue / 100)` 需要读取 `.value`。

**修改**：需要特殊处理。`Modifier.fillMaxWidth(fraction)` 在 Lua 侧调用时，`fraction` 参数是 `AnimatedFloat` 对象而不是数值。需要检查 `ModifierChain` 是否支持 `AnimatedFloat` 作为参数。

**方案**：将进度条改为 Canvas 绘制，或者使用 `textLambda` 读取动画值后在 `Box` 的 modifier 中使用。实际上，`ModifierChain` 的 `fillMaxWidth` 接收 `Float`，而 `animatedValue` 是 `AnimatedFloat`。Lua 侧 `Modifier.fillMaxWidth(animatedValue.value / 100)` 在初始调用时是正确的（读取当前值），但后续动画值变化时不会更新 Modifier。

**推荐方案**：去掉动画进度条部分的 `.fillMaxWidth(animatedValue / 100)` 用法，改为 Canvas 绘制，或使用 `textLambda` 仅显示数值文字。

---

### 修改 6：修复 ComposeBridge 交替渲染失败

**文件**：`e:\Soft\Proje\LXC-LUA\core\src\main\kotlin\com\nirithy\luacompose\bridge\ComposeBridge.kt`

**问题**：`05_lists.lua` 第二次运行不渲染，第三次又正常。周期为 2。

**根因分析**：`resetState()` 中清空了 `stateCache`、`rememberCache` 等，但 `rootState` 可能保留了上次的 ComposeNode 引用。当 `refreshNodeTree` 执行时，`stateIndex` 从 0 开始，但 `stateCache` 中可能还有旧条目（`mutableState` 的 StateWrapper）。

**修改**：在 `resetState()` 中确保：
```kotlin
rootState.value = null  // 清除旧节点树
recomposeTrigger.value = 0L  // 重置触发器
stateCache.clear()
rememberCache.clear()
animatedFloats.clear()
// 确保所有 ThreadLocal 清理
```

同时检查 `LuaActivity` 中每次 `onCreate` 是否正确创建新的 ComposeView。

**文件**：`e:\Soft\Proje\LXC-LUA\core\src\main\kotlin\com\nirithy\lxclua\LuaActivity.kt`

**修改**：在 `onDestroy` 中确保移除 ComposeView 并置空引用。

---

## 修改汇总

| 编号 | 文件 | 修改类型 | 描述 |
|------|------|----------|------|
| 1 | `inputcomponents.kt` | Kotlin 修复 | Button 系列组件支持 `text` prop |
| 2 | `02_counter.lua` | 教程修复 | `mutableState` → `state` |
| 3a | `04_inputs.lua` | 教程修复 | 显示文字使用 `textLambda` |
| 3b | `04_inputs.lua` | 教程修复 | Checkbox/Switch 状态文字使用 `textLambda` |
| 3c | `04_inputs.lua` | 教程修复 | Slider 值文字使用 `textLambda` |
| 3d | `04_inputs.lua` | 教程修复 | 综合表单文字使用 `textLambda` |
| 4a | `05_lists.lua` | 教程修复 | `mutableState` → `state`，添加点击回调 |
| 4b | `05_lists.lua` | 教程修复 | 卡片添加 `clickableLua` modifier |
| 4c | `ComposeBridge.kt` + `LuaActivity.kt` | Kotlin 修复 | 状态清理，修复交替渲染失败 |
| 5a | `06_animations.lua` | 教程修复 | `animatedValue` → `animatedValue.value`，使用 `textLambda` |
| 5b | `06_animations.lua` | 教程修复 | `isVisible` → `state()` |
| 5c | `06_animations.lua` | 教程修复 | `isCrossfade` → `state()` |
| 5d | `06_animations.lua` | 教程修复 | 动画进度条改为 Canvas 绘制或 textLambda |

---

## 假设与决策

1. **`state()` vs `mutableState()` 选择**：结构性变化（可见性、选中状态、列表内容）使用 `state()` 触发全量刷新；连续动画值使用 `mutableState` + `textLambda` 读取。这是最符合 Compose 设计理念的方案。

2. **Button `text` prop 修复在 Kotlin 侧**：教程语法 `Button({ text = "+1", ... })` 是用户期望的自然 API，修复 Kotlin 侧一次解决所有使用场景。

3. **交替渲染失败**：假设是 `rootState` 或 `stateCache` 清理不完整导致。需要在实际代码中验证。

4. **动画进度条**：放弃 `Modifier.fillMaxWidth(animatedValue / 100)` 动态宽度用法，因为在 Lua 侧无法在 Compose 重组时动态更新 Modifier 参数。改为 Canvas 绘制或仅显示数值。

---

## 验证步骤

1. 编译项目确保 Kotlin 修改无编译错误
2. 在设备上按顺序运行 01-05 教程，验证：
   - `02_counter.lua`：按钮有文字，点击能增减计数，显示文字更新
   - `03_layouts.lua`：布局正常渲染
   - `04_inputs.lua`：输入文字后下方显示同步更新，Checkbox/Switch/Slider 状态文字更新
   - `05_lists.lua`：点击卡片有选中效果，连续运行两次都能正常渲染
3. 运行 `06_animations.lua`：不崩溃，AnimatedVisibility 切换正常，Crossfade 切换正常，数值动画显示正常