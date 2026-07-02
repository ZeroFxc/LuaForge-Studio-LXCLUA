# Nirithy LuaCompose 官方教程 + 语法修复

## 摘要

彻底理解当前端到端流程后，修复 Lua 端组件访问的根本问题，编写 10 个官方教程示例。

---

## 端到端流程分析

```
Lua 脚本执行
  compose.render(function() return compose.Column { ... } end)
    → registerRenderFunction 将 Lua 函数引用存入 activeLuaFunc

refreshAfterLoad() → refreshNodeTree()
  activeLuaFunc.call()  ← 执行 Lua 渲染函数
    Lua 调用 compose.Column { ... } → 触发 __index 或直接表命中
      → 创建 ComposeNode(type="Column", props={...}, children=[...])
    return ComposeNode 树
  rootState.value = result

ComposeHost 观察 rootState 变化
  ComposeRenderer.Render(rootNode)
    ComponentRegistry.render(node)
      → renderers["Column"] 命中 → @Composable 渲染器
      → 递归渲染子节点
```

### 核心问题

`compose.LinearProgressIndicator` 失败的根本原因：

```
__index 触发 → 大写 key "LinearProgressIndicator"
  → resolveClass("androidx.compose.LinearProgressIndicator") → 失败
  → 跨包搜索 → 所有子包都找不到 LinearProgressIndicatorKt
  → 回退: createSubNamespace → 返回普通 Lua table
  → Lua 调用 table({...}) → "attempt to call a table value"
```

**错误的设计假设**：`__index` 认为大写 key 解析失败 = 应该创建子命名空间。实际上大写 key 解析失败更可能是**组件名**，应该创建 ComposeFunction。

### 正确的行为

```
__index 触发 → 大写 key "LinearProgressIndicator"
  → resolveClass 失败 → 跨包搜索失败
  → 创建 ComposeFunction("", "LinearProgressIndicator")
  → Lua 调用 → ComposeNode(type="LinearProgressIndicator")
  → ComponentRegistry.render() → renderers["LinearProgressIndicator"] 命中
  → 渲染成功 ✓
```

---

## 改动 1：修复 LazyNamespace `__index` 回退逻辑

**文件**：`core/src/main/kotlin/com/nirithy/luacompose/bridge/LazyNamespace.kt`

### 当前逻辑（错误）

```kotlin
if (key[0].isUpperCase()) {
    val clazz = resolveClass(fullPath)
    if (clazz != null) return pushClassResult(clazz)
    if (isRoot) searchSubNamespaces(key)
    // 大写 key 解析失败 → 回退创建子命名空间 ← BUG
}
createSubNamespace(key, fullPath)  // 返回普通 table
```

### 修复后

```kotlin
if (key[0].isUpperCase()) {
    val clazz = resolveClass(fullPath)
    if (clazz != null) return pushClassResult(clazz)
    if (isRoot) searchSubNamespaces(key)  // 保留跨包搜索，用于找到正确的 classPath
    // 大写 key 解析失败 → 创建 ComposeFunction（空 classPath）
    // ComponentRegistry 会按 type=key 匹配渲染器
    pushComposeFunction(key, "")
    cacheToTable(key)
    return 1
}
// 小写 key → 创建子命名空间
createSubNamespace(key, fullPath)
```

### 移除的内容

- 删除 `classAliases` map（不再需要）
- 删除 `tryResolveInSubPackage` 方法（不再需要别名尝试）
- 删除 `knownSubPackages` 静态列表（跨包搜索仅在动态子命名空间中进行）
- 简化 `searchSubNamespaces`：只遍历已缓存的动态子命名空间做直接类名匹配

### 为什么不需要跨包搜索

| 场景 | 示例 | 处理方式 |
|------|------|----------|
| 缩略写法 | `compose.LinearProgressIndicator` | ComposeFunction("", "LinearProgressIndicator") → ComponentRegistry 按 type 命中 |
| 完整路径 | `compose.material3.LinearProgressIndicator` | 先进入 material3 子命名空间，__index 解析 LinearProgressIndicatorKt → 成功 |
| 未注册组件 | `compose.SomeUnknown` | ComposeFunction("", "SomeUnknown") → ComponentRegistry 未命中 → DynamicRenderer 未命中 → 日志警告 |

跨包搜索保留的唯一价值是**为 DynamicRenderer 提供正确的 classPath**（如 `compose.Button` 跨包搜索到 `material3.ButtonKt`），但这不是必需的——ComponentRegistry 已经有了所有这些组件的渲染器。

---

## 改动 2：编写官方 Nirithy LuaCompose 教程

**位置**：`e:\Soft\Proje\LXC-LUA\docs\lua_examples\`

### 教程设计原则

1. 每个示例独立可运行（复制到项目 main.lua 直接运行）
2. 使用 `compose.render(function() return ... end)` 入口
3. State 在 render 函数外部声明（持久化）
4. 组件使用 `children = { ... }` 传子节点
5. 使用简写语法 `compose.Button` 而非 `compose.material3.Button`
6. 关键行加注释说明

### 10 个示例

| 文件 | 主题 | 覆盖 API |
|------|------|----------|
| `01_hello.lua` | 入门 | compose.render, Text, Modifier, padding, fillMaxWidth, fontSize, color, fontWeight |
| `02_counter.lua` | 状态 | mutableState, state, Button, Row, Column, Spacer, onClick |
| `03_layouts.lua` | 布局 | Column, Row, Box, Spacer, verticalArrangement, horizontalArrangement, fillMaxSize, weight, background |
| `04_inputs.lua` | 输入 | TextField, OutlinedTextField, Checkbox, Switch, Slider, 受控组件模式 |
| `05_lists.lua` | 列表 | LazyColumn, Card, children 函数引用懒加载, verticalScroll |
| `06_animations.lua` | 动画 | animateFloatAsState, AnimatedVisibility, fadeIn/fadeOut, clickableLua |
| `07_components.lua` | 高级组件 | SearchBar, DatePicker, LinearProgressIndicator, Chip, Badge, FAB, Icon |
| `08_drawer.lua` | 导航 | ModalNavigationDrawer, ModalDrawerSheet, BackHandler |
| `09_canvas.lua` | 绘图 | Canvas, onDraw |
| `10_effects.lua` | 副作用 | LaunchedEffect, rememberCoroutineScope, delay, key |

---

## 假设与决策

| 决策 | 理由 |
|------|------|
| 大写 key 解析失败 → ComposeFunction | ComponentRegistry 已有所有渲染器，只需正确路由 |
| 移除所有别名和静态子包列表 | ComponentRegistry 按 type 匹配，不需要 classPath |
| 跨包搜索仅保留动态子命名空间遍历 | 比静态列表更准确，且只在首次访问时触发 |
| 保留 Plugin 注册机制 | 每个组件需要自定义 @Composable 渲染器，无法完全自动化 |
| 教程使用简写语法 | 用户友好，与 ComponentRegistry 的 type 匹配一致 |

---

## 验证步骤

1. 编译 core 模块：`./gradlew :core:compileDebugKotlin`
2. 逐一复制教程 Lua 文件到项目目录，在设备上运行验证
3. 确认以下之前失败的组件可正常渲染：
   - `compose.LinearProgressIndicator` ✓
   - `compose.CircularProgressIndicator` ✓
   - `compose.ModalDrawerSheet` ✓
   - `compose.DockedSearchBar` ✓
   - `compose.SmallFloatingActionButton` ✓
   - `compose.FilterChip` ✓
   - `compose.DismissibleNavigationDrawer` ✓
   - `compose.PermanentNavigationDrawer` ✓