# 根因修复：mutableState 应触发全量树重建

## 摘要

上一次修复是"打补丁"——把教程中的 `mutableState` 改成 `state()`，或用 `textLambda` 绕过。真正的根因是：**nirithy 的 `mutableState` 只递增 `recomposeTrigger`，不重建 Lua 节点树，导致所有从 `ComposeNode.props` 读取的值都是首次渲染时的静态值**。kulipai 版本只有一种状态类型，每次变更都重建整个 Lua 节点树。

## 当前状态分析

### nirithy 当前的双轨渲染模型（问题根因）

```
state() 的 onChange → scheduleRefresh() → refreshNodeTree() → 重建 Lua 节点树 → UI 更新 ✅
mutableState() 的 onChange → recomposeTrigger++ → ComposeHost 重组 → 不重建树 → UI 不变 ❌
```

关键代码在 `ComposeInjectors.kt` 第 91-95 行：

```kotlin
// state() — 正确
StateWrapper(obj) { scheduleRefresh() }  // 走全量刷新

// mutableState() — 问题根因
StateWrapper(obj) { recomposeTrigger.value++ }  // 只触发 Compose 重组，不重建 Lua 树
```

### kulipai 的单轨模型（正确做法）

```
ComposeState.set() → scope.invalidate() → _recomposeVersion.value++
  → remember(version) { scope.execute() }  ← key 变了，重新执行 Lua 函数
  → 生成全新的 List<ComposeNode> → UI 更新 ✅
```

kulipai 只有一种状态类型，不区分"全量刷新"和"轻量重组"。每次状态变更都重建整个 Lua 节点树。

### 为什么"轻量重组"模型不工作

在 nirithy 的渲染管线中：
1. `refreshNodeTree()` 执行 Lua 渲染函数 → 生成 `ComposeNode` 树 → `rootState.value = result`
2. ComposeHost 重组 → `ComposeRenderer.Render(rootNode)` → 各组件渲染器读取 `ComposeNode.props`

`mutableState` 的 `recomposeTrigger++` 只触发步骤 2 重新执行，但步骤 1 不执行。所以 `ComposeNode.props` 仍然是旧值，UI 不变。

## 提议的修改

### 修改 1：mutableState 的 onChange 改为 scheduleRefresh()

**文件**：`e:\Soft\Proje\LXC-LUA\core\src\main\kotlin\com\nirithy\luacompose\bridge\ComposeInjectors.kt`

**修改**：将 `registerMutableState` 中的 `recomposeTrigger.value++` 改为 `scheduleRefresh()`。

```kotlin
// 修改前 (第 91-95 行)
val wrapper = when (obj) {
    is Boolean -> StateWrapper(obj) { recomposeTrigger.value++ }
    is Number -> StateWrapper(obj.toFloat()) { recomposeTrigger.value++ }
    is String -> StateWrapper(obj) { recomposeTrigger.value++ }
    else -> StateWrapper(obj) { recomposeTrigger.value++ }
}

// 修改后
val wrapper = when (obj) {
    is Boolean -> StateWrapper(obj) { scheduleRefresh() }
    is Number -> StateWrapper(obj.toFloat()) { scheduleRefresh() }
    is String -> StateWrapper(obj) { scheduleRefresh() }
    else -> StateWrapper(obj) { scheduleRefresh() }
}
```

**影响**：`mutableState` 和 `state` 行为一致，都触发全量树重建。`recomposeTrigger` 仅保留给内部动画系统（`AnimatedFloat`）使用。

### 修改 2：还原教程示例，使用 mutableState

**文件**：
- `02_counter.lua`：`state` → `mutableState`（还原为标准 Compose API 名称）
- `04_inputs.lua`：去掉 `textLambda` 工作区，恢复为直接 `text = "..." .. state.value` 写法
- `05_lists.lua`：保持 `state()`（结构性变化需要全量刷新，语义正确）
- `06_animations.lua`：`isVisible/isCrossfade` 保持 `state()`（结构变化），`animTarget` 保持 `mutableState`（现在也能正常工作）

### 修改 3：更新 README.md 中的状态类型说明

**文件**：`e:\Soft\Proje\LXC-LUA\docs\lua_examples\README.md`

**修改**：更新状态类型对比表，说明 `state` 和 `mutableState` 行为一致，推荐使用 `mutableState`（与 Compose API 命名一致）。

## 修改汇总

| 编号 | 文件 | 修改类型 | 描述 |
|------|------|----------|------|
| 1 | `ComposeInjectors.kt` | Kotlin 根因修复 | `mutableState` 的 onChange 改为 `scheduleRefresh()` |
| 2a | `02_counter.lua` | 教程还原 | `state` → `mutableState` |
| 2b | `04_inputs.lua` | 教程还原 | 去掉 `textLambda`，恢复直接 `text` 写法 |
| 2c | `06_animations.lua` | 教程还原 | 保持 `state()` 用于结构变化，`mutableState` 用于动画值 |
| 3 | `README.md` | 文档更新 | 更新状态类型说明 |

## 假设与决策

1. `scheduleRefresh()` 的防重入机制（`refreshPending`）已经存在，频繁调用不会导致重复刷新，因此即使多个 `mutableState` 同时变更也安全
2. `stateCache` 同时被 `state()` 和 `mutableState()` 共享，行为一致，无需分离
3. `recomposeTrigger` 保留给 `AnimatedFloat` 系统使用，不影响本次修改
4. 性能：kulipai 已验证每次重建整个 Lua 树在生产环境中可接受，小型 UI 树的重建成本极低

## 验证步骤

1. 编译项目确保 Kotlin 修改无编译错误
2. 在设备上运行 `02_counter.lua`：按钮点击后计数文字更新
3. 运行 `04_inputs.lua`：输入文字后下方显示实时更新，Checkbox/Switch/Slider 状态文字更新
4. 运行 `05_lists.lua`：点击卡片有选中效果
5. 运行 `06_animations.lua`：AnimatedVisibility 切换正常，数值动画显示正常