# LuaCompose 动态解析改进计划

## 摘要

移除硬编码的快速路径组件列表（`registerFastPathComponents` 中的 50+ 组件名），统一使用 LazyNamespace 的动态反射机制解析所有组件。同时从 LuaCompose-master 原版中采纳改进点，提升架构的灵活性和可维护性。

---

## 现状分析

### 问题根因

当前架构中 `compose.Column` 能工作是因为 `registerFastPathComponents()` 把组件名直接 setField 到 compose 根表上，Lua 访问时走的是**直接表键查找**，不触发 `__index` 元方法。但 `compose.ModalDrawerSheet` 失败是因为：

1. 它不在快速路径列表中
2. LazyNamespace 的 `__index` 触发后，`resolveClass("androidx.compose.ModalDrawerSheet")` 尝试了 `ModalDrawerSheet`、`ModalDrawerSheetKt`、`ModalDrawerSheetComposable` 三个候选类名
3. 实际类在 `androidx.compose.material3.ModalDrawerSheetKt`，不在 `androidx.compose` 根包下
4. 类加载失败后回退创建子命名空间，返回一个普通 table，不是可调用函数

### 原版解决方案（LuaCompose-master）

原版通过 `createLazyNamespace("androidx.compose")` 构建惰性命名空间树：
- `compose.material3` → 子命名空间 `androidx.compose.material3`
- `compose.material3.ModalDrawerSheet` → 在子命名空间上触发 `__index` → `Class.forName("androidx.compose.material3.ModalDrawerSheetKt")` → 成功

原版**完全没有快速路径列表**，所有组件通过正确的命名空间路径 + KSP 代码生成解析。

### 当前架构为什么不直接去掉快速路径

当前项目有两个调用入口：
- `compose.Column`（简写，走快速路径直接命中）
- `compose.foundation.layout.Column`（完整路径，走 LazyNamespace 反射）

如果直接去掉快速路径，`compose.Column` 会触发 `__index`，尝试 `resolveClass("androidx.compose.Column")` → 失败（类在 `foundation.layout` 子包），回退创建子命名空间 → 返回不可调用 table → 报错。

---

## 改进方案

### 核心思路：跨包搜索 + 去掉快速路径

修改 LazyNamespace 的 `resolveClass` 策略：当在根命名空间（`androidx.compose`）无法找到类时，**递归搜索已缓存的子命名空间**，尝试匹配类名。

### 具体改动

#### 改动 1：LazyNamespace 增加跨包搜索（`LazyNamespace.kt`）

**文件**：`core/src/main/kotlin/com/nirithy/luacompose/bridge/LazyNamespace.kt`

**当前逻辑**（`resolveClass` 第 88-107 行）：
```kotlin
fun resolveClass(fullPath: String): Class<*>? {
    val candidates = listOf(fullPath, "${fullPath}Kt", "${fullPath}Composable")
    for (candidate in candidates) {
        try { return Class.forName(candidate) } catch (_: Exception) {}
    }
    return null
}
```

**改进后**：在 `NamespaceIndexHandler.execute()` 中，当大写首字母 key 的 `resolveClass(fullPath)` 失败时，不立即回退创建子命名空间，而是**尝试搜索已缓存的子命名空间**：

```kotlin
// 伪代码
if (key[0].isUpperCase()) {
    val clazz = resolveClass(fullPath)
    if (clazz != null) {
        // 成功，注册 ComposeFunction
        return createComposeFunction(clazz, key)
    }
    // 跨包搜索：遍历已缓存的子命名空间，尝试 match
    val found = searchSubNamespaces(key)
    if (found != null) {
        return found
    }
}
// 回退：创建子命名空间
return createSubNamespace(fullPath)
```

**跨包搜索策略**（`searchSubNamespaces`）：
1. 遍历当前命名空间表的所有已缓存子命名空间（`material3`、`foundation`、`ui`、`animation` 等）
2. 对每个子命名空间，尝试 `resolveClass("${subNs.fullPath}.${key}")`
3. 首次命中后，将结果**缓存到父表**（下次直接命中，不触发 `__index`）
4. 如果全部未命中，返回 null，进入回退逻辑

**为什么这样设计**：
- 与 LuaCompose-master 的"惰性子命名空间 + 递归类加载"模式一致
- 首次访问 `compose.ModalDrawerSheet` 时自动搜索到 `material3.ModalDrawerSheetKt`，后续直接命中缓存
- 不需要维护任何手动列表

#### 改动 2：移除快速路径列表（`ComposeBridge.kt`）

**文件**：`core/src/main/kotlin/com/nirithy/luacompose/bridge/ComposeBridge.kt`

- 删除 `registerFastPathComponents()` 方法及其调用
- 保留 `registerComponentFactory()` 方法（Plugin 注册时仍需要）

**注意**：插件注册的组件（如 LayoutComponents 注册 "Column"）仍然通过 `ComponentRegistry.renderers` 可用，渲染阶段不受影响。只是 Lua 端访问 `compose.Column` 时从"直接表键命中"变为"LazyNamespace 反射 → 跨包搜索 → 命中"。

#### 改动 3：采纳原版的 wrapObject/wrapClass 机制

**新增文件**：`core/src/main/kotlin/com/nirithy/luacompose/bridge/ObjectWrapper.kt`

从 LuaCompose-master 采纳 `wrapClass` / `wrapObject` 的反射包装机制：
- 为 Java 类创建 Lua 代理表，支持 `__index`（属性访问、方法调用）、`__call`（构造函数）
- 添加反射缓存（`ClassReflectionCache`），避免重复 `getMethods()` / `getFields()`
- 方法调用按参数数量匹配重载

**用途**：替代当前 LazyNamespace 中 `pushJavaObject` 的简单处理，让 Lua 端可以流畅地访问 Java 对象（如 `Color.Red`、`Dp(16f)` 等）。

#### 改动 4：改进 Plugin 接口（对齐原版 `ComposeScriptPlugin`）

**文件**：`core/src/main/kotlin/com/nirithy/luacompose/plugin/ComposePlugin.kt`

当前 `ComposePlugin` 接口只有 `namespace` 和 `getComponents()`。对齐原版增加：
- `injectGlobals(scriptTable)`：注入全局辅助函数（如 `Arrangement.SpaceBetween`、`FontWeight.Bold` 等）
- `injectLocals(scope)`：注入 Compose 局部值（如 `LocalDensity`）

**好处**：各插件自己管理注入，而不是在 ComposeBridge 中集中处理。

#### 改动 5：改进 DynamicRenderer 参数解析

**文件**：`core/src/main/kotlin/com/nirithy/luacompose/render/DynamicRenderer.kt`

当前 `resolveParam` 靠硬编码参数名映射（`modifier` → ModifierChain.build() 等），覆盖不全。改进：
- 使用 Compose 函数的 `@Composable` 参数类型信息做智能映射
- 对 `Function0`/`Function1`/`Function2` 类型的参数，自动从 `node.callbacks` 中按名称匹配
- 对 `Modifier` 类型参数，默认使用 `resolveModifier(node)`
- 对 `String`/`Boolean`/`Float`/`Int` 等基础类型，从 `node.props` 取值

---

## 其他值得采纳的改进点

### 从 LuaCompose-master 原版

1. **KSP 编译器插件完善**：原版的 `LuaComposeProcessor` 更完整，支持 `@LuaBridgePackage` 包级扫描 + `@LuaBridgeClass` 类级扫描 + `@LuaBridgeModifiers` Modifier 扩展扫描 + `@LuaBridgeLocals` Local 属性扫描。当前项目只有 `@LuaComposeScan` 一个注解。

2. **FunctionWrappers**：原版有 `Function0`-`Function6` 的完整包装器，处理 Lua 函数 → Kotlin lambda 的转换。当前项目在多个地方重复实现此逻辑。

3. **ClassReflectionCache**：原版缓存了 `getMethods()`、`getFields()` 的结果，避免每次反射都重新查询。当前项目每次调用都重新反射。

4. **ScriptEngine 抽象层**：原版通过 `ScriptEngine` 接口将 Lua 引擎完全抽象，理论上可以切换到任何脚本引擎。当前项目直接依赖 luajava，耦合度高。

### 从当前代码自身发现

5. **DerivedState 未实现**：`mutableStateOf` 已实现，但 `derivedStateOf` 缺失。原版有完整实现。

6. **remember 带 key 变体**：当前 `remember` 不支持 key 参数（`remember(key) { ... }`），原版支持。

7. **SnapshotStateList/Map**：原版支持 `mutableStateListOf` / `mutableStateMapOf`，当前项目未实现。

---

## 假设与决策

| 决策 | 理由 |
|------|------|
| 跨包搜索仅在根命名空间执行 | 子命名空间路径已精确，不需要跨包搜索 |
| 搜索结果缓存到父表 | 避免每次访问都触发 `__index` 的跨包搜索开销 |
| 保留 DynamicRenderer 作为兜底 | 与"全动态"目标一致，无需 KSP 也能运行未注册组件 |
| 不强制要求 KSP 生成 | 保持与当前项目的兼容性，KSP 作为优化手段而非必需 |
| 快速路径列表完全移除 | 跨包搜索 + 缓存机制已覆盖所有场景 |

---

## 验证步骤

1. 编译 core 模块：`./gradlew :core:compileDebugKotlin`
2. 运行测试 Lua 脚本，验证以下组件可正常访问：
   - `compose.Column`（简写，跨包搜索到 `foundation.layout`）
   - `compose.Button`（简写，跨包搜索到 `material3`）
   - `compose.ModalDrawerSheet`（简写，跨包搜索到 `material3`）
   - `compose.material3.Button`（完整路径，子命名空间解析）
   - `compose.DockedSearchBar`（简写，跨包搜索）
   - `compose.SmallFloatingActionButton`（简写，跨包搜索）
3. 验证 `wrapObject`：`compose.ui.graphics.Color.Red` → 能访问静态字段
4. 验证 `injectGlobals`：`compose.Arrangement.SpaceBetween` → 能正常使用