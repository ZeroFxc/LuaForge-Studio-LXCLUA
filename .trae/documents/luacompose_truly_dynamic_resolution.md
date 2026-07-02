# LuaCompose 真正动态解析 + 官方教程

## 摘要

1. **移除别名补丁**：重构 LazyNamespace `__index`，大写 key 解析失败时回退创建 ComposeFunction（空 classPath），由 ComponentRegistry 按 `type` 匹配渲染器
2. **编写官方 Lua 教程**：10 个渐进式示例脚本，覆盖所有可用组件和最佳实践

---

## 现状分析

### 问题根因

`compose.LinearProgressIndicator` 失败的原因链：

```
Lua 访问 compose.LinearProgressIndicator
  → __index 触发
  → 大写 key，尝试 resolveClass("androidx.compose.LinearProgressIndicator")
  → 尝试 LinearProgressIndicatorKt → ClassNotFoundException（实际类是 ProgressIndicatorKt）
  → 跨包搜索 → 所有子包都找不到 LinearProgressIndicatorKt
  → 回退: createSubNamespace → 返回普通 Lua table
  → Lua 调用 table({...}) → "attempt to call a table value"
```

**根本矛盾**：`__index` 把"大写 key 解析失败"等同于"这是一个子命名空间"，但实际上它可能是一个已注册的组件名。ComponentRegistry 已经注册了 `"LinearProgressIndicator"` 渲染器，但它没有被调用到。

### 两类大写 key 的区分

| 类型 | 示例 | 期望行为 |
|------|------|----------|
| 纯命名空间 | `material3`, `foundation`, `ui` | 创建子命名空间表 |
| 组件名 | `Button`, `LinearProgressIndicator`, `ModalDrawerSheet` | 返回可调用函数 |
| Kotlin对象 | `MaterialTheme`, `CardDefaults` | 返回 Java 对象 |

**区分规则**：大写 key 优先尝试解析为 Java 类。解析成功 → 返回类/对象。解析失败 → 不是命名空间，是组件名 → 创建 ComposeFunction（空 classPath），由 ComponentRegistry 渲染。

---

## 改动 1：重构 LazyNamespace `__index` 回退逻辑

**文件**：`core/src/main/kotlin/com/nirithy/luacompose/bridge/LazyNamespace.kt`

### 当前逻辑

```kotlin
if (key[0].isUpperCase()) {
    val clazz = resolveClass(fullPath)  // 尝试 Class.forName
    if (clazz != null) return pushClassResult(clazz)
    if (isRoot) searchSubNamespaces(key)  // 跨包搜索
    // 失败 → 回退创建子命名空间 ← BUG
}
createSubNamespace(key, fullPath)  // 返回普通 table
```

### 改进后

```kotlin
if (key[0].isUpperCase()) {
    val clazz = resolveClass(fullPath)
    if (clazz != null) return pushClassResult(clazz)
    if (isRoot) {
        val found = searchSubNamespaces(key)
        if (found > 0) return found
    }
    // 大写 key 解析失败 → 创建 ComposeFunction（空 classPath）
    // ComponentRegistry 会按 type=key 匹配渲染器
    pushComposeFunction(key, "")
    cacheToTable(key)
    return 1
}
// 小写 key → 创建子命名空间
createSubNamespace(key, fullPath)
```

### 关键改动

1. **大写 key 解析失败** → 不再创建子命名空间，直接创建 ComposeFunction
2. **ComposeFunction 的 classPath 为空字符串** → ComponentRegistry 会走 `renderers[node.type]` 路径
3. **结果缓存到父表** → 下次 O(1) 直接命中
4. **移除全部别名代码**：`classAliases` map、`tryResolveInSubPackage` 方法
5. **简化 `searchSubNamespaces`**：不再需要别名尝试，只做直接类名匹配

### 为什么这样是正确的

```
compose.LinearProgressIndicator
  → [大写] resolveClass 失败 + 跨包搜索失败
  → 创建 ComposeFunction("", "LinearProgressIndicator")
  → Lua 调用: compose.LinearProgressIndicator { ... }
  → 创建 ComposeNode(type="LinearProgressIndicator", props={})
  → ComponentRegistry.render(node)
  → renderers["LinearProgressIndicator"] → 命中 ComplementComponents 注册的渲染器
  → 渲染成功 ✓
```

对于未注册的组件名：
```
compose.SomeUnknownComponent
  → 创建 ComposeFunction("", "SomeUnknownComponent")
  → Lua 调用 → ComposeNode(type="SomeUnknownComponent")
  → ComponentRegistry: renderers 未命中 → DynamicRenderer 未命中
  → 日志警告: "未找到组件的渲染器: SomeUnknownComponent"
  → 这是正确的错误处理 ✓
```

---

## 改动 2：编写官方 Lua 教程

**位置**：`e:\Soft\Proje\LXC-LUA\docs\lua_examples\`

### 教程结构

| 文件 | 主题 | 内容 |
|------|------|------|
| `01_hello_world.lua` | 入门 | compose.render() 入口、Text 组件、Modifier 基础 |
| `02_state.lua` | 状态管理 | state/mutableState、计数器、双向绑定、rememberCoroutineScope |
| `03_layouts.lua` | 布局 | Column/Row/Box/Spacer、verticalArrangement/horizontalArrangement、fillMaxWidth/height/weight |
| `04_inputs.lua` | 输入组件 | TextField/OutlinedTextField、Checkbox/Switch/Slider、受控组件模式 |
| `05_lists.lua` | 列表 | LazyColumn/LazyRow + Card、children 函数引用懒加载、50 项列表 |
| `06_animations.lua` | 动画 | AnimatedVisibility、animateFloatAsState、animateColorAsState、Crossfade |
| `07_components.lua` | 高级组件 | FAB/Chip/Badge/SearchBar/DatePicker/ProgressIndicator/TabRow |
| `08_navigation.lua` | 导航 | ModalNavigationDrawer + ModalDrawerSheet、BackHandler |
| `09_canvas.lua` | 绘图 | Canvas + onDraw、基础图形绘制 |
| `10_effects.lua` | 副作用 | LaunchedEffect、DisposableEffect、key、delay、协程 |

### 教程代码风格要求

- 每个文件开头注释说明目标、覆盖的 API
- 使用 `compose.render(function() return ... end)` 入口
- State 声明在 render 函数外部（持久化）
- 组件使用 `children = { ... }` 传子节点
- Modifier 使用链式调用
- 避免复杂逻辑，每个示例聚焦一个主题
- 关键行加注释说明

---

## 假设与决策

| 决策 | 理由 |
|------|------|
| 大写 key 解析失败 → ComposeFunction 而非子命名空间 | ComponentRegistry 已有渲染器，只需正确路由 |
| 移除所有别名 | 不再需要，ComponentRegistry 按 type 匹配即可 |
| 保留跨包搜索 | 用于找到正确的 classPath（如 Button → material3.ButtonKt），优化 DynamicRenderer |
| 教程放在 `docs/lua_examples/` | 独立于源码，用户可直接复制到项目目录运行 |
| 教程 10 个文件 | 覆盖所有组件，渐进式学习 |

---

## 验证步骤

1. 编译 core 模块：`./gradlew :core:compileDebugKotlin`
2. 运行测试 Lua 脚本，验证：
   - `compose.LinearProgressIndicator` → 正常渲染（不报 "attempt to call a table value"）
   - `compose.CircularProgressIndicator` → 正常渲染
   - `compose.Button` → 正常渲染
   - `compose.ModalDrawerSheet` → 正常渲染
   - `compose.DockedSearchBar` → 正常渲染
   - `compose.SmallFloatingActionButton` → 正常渲染
   - `compose.FilterChip` → 正常渲染
   - `compose.material3.Button` → 正常渲染（完整路径）
   - `compose.foundation.layout.Column` → 正常渲染
3. 复制教程 Lua 文件到设备，逐个运行验证