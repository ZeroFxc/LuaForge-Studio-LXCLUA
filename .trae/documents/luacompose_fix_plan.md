# LuaCompose 完善与隐藏问题修复计划

## 摘要

LuaCompose 是项目中 Lua 与 Jetpack Compose 互操作的核心桥接框架，位于 `core/src/main/kotlin/com/nirithy/luacompose/`。经全面代码审查，发现 **3个严重阻断性bug**、**5个高优先级功能缺陷**、**7个中优先级问题**和若干低优先级问题，以及1个架构级隐患。本计划按优先级逐一修复，确保框架稳定可用。

---

## 当前状态分析

### 核心架构（正常工作）
- ComposeBridge 单例管理状态缓存和刷新调度 ✓
- ComposeHost 渲染宿主+主题同步 ✓
- ComposeInjectors 注册1400+行API ✓
- ModifierChain 链式Modifier构建 ✓
- NodeParser 解析Lua表→ComposeNode ✓
- 组件注册表（硬编码+KSP生成+动态反射） ✓
- Lua↔Java类型转换（LuaConverter） ✓
- 动画/导航/画布/手势/副作用插件 ✓

### 问题总览

| 优先级 | 问题 | 影响 |
|--------|------|------|
| **P0阻断** | Animatable.scope从未赋值 → animateTo/snapTo完全无效 | 动画API完全不可用 |
| **P0阻断** | SearchBar/DockedSearchBar的onActiveChange为空 → 永远无法展开 | 搜索组件完全失效 |
| **P0阻断** | compose.delay()使用Thread.sleep → 阻塞主线程致ANR | 任何delay调用都卡死UI |
| **P1高** | 输入组件(TextField/Checkbox/Switch/Slider)本地remember状态不响应props更新 | 无法程序化控制输入框/勾选状态 |
| **P1高** | animateColorAsState将ARGB Long强转Float → 颜色精度丢失/插值错误 | 颜色动画显示异常颜色 |
| **P1高** | SpacerLayout未调用resolveModifier → 自定义modifier(background/padding等)全部丢失 | Spacer无法设置外观 |
| **P1高** | LazyColumn/LazyRow的childrenFunc包裹在单个item中 → 懒加载完全失效 | 长列表一次性全部渲染，性能差 |
| **P1高** | LuaActivity.onDestroy未清理ComposeBridge全局状态 | Activity重建后状态残留，可能崩溃 |
| **P2中** | ModalNavigationDrawer drawerState无法从Lua控制 | 抽屉无法程序化开关 |
| **P2中** | SharedTransitionLayout中RenderNode/RenderChildren异常导致作用域栈泄漏 | 异常后sharedElement失效 |
| **P2中** | compose.withFrameNanos()同步调用，未真正等下一帧 | 帧回调API语义错误 |
| **P2中** | clickable每次重组创建新MutableInteractionSource → 涟漪状态重置 | 用户体验细节 |
| **P2中** | DynamicRenderer参数解析极有限 → 很多组件参数无法通过动态反射传递 | 动态组件可用性低 |
| **P2中** | ComposeBridge单例不支持多Activity | 多Activity场景状态互相覆盖 |
| **P2中** | Lua回调（onClick等）未加synchronized(luaState)锁 | 潜在线程安全问题 |
| **P3低** | BackHandlerComponent.boolProp默认值逻辑冗余 | 代码清理 |
| **P3低** | verticalScroll在LazyList中嵌套无检测 | 可能滚动冲突 |
| **P3低** | LuaObject引用未在旧节点树释放 | 高频刷新时内存膨胀 |

---

## 具体修改方案

### P0-1: 修复Animatable.scope未赋值

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/bridge/ComposeInjectors.kt` (registerAnimatableFactory函数，约第748-757行)

**修改内容**:
在创建LuaAnimatable后，通过ComposeBridge注入 rememberCoroutineScope。但因为Injector在非Compose上下文，需要：
1. 在LuaAnimatable中添加`setScope()`方法
2. 在ComposeHost中渲染时，对Animatable类型的值自动设置scope
3. 或者更简单：在AnimatedFloat/lauAnimatable创建时，使用ComposeBridge.mainScope（在ComposeBridge中创建一个主线程CoroutineScope(SupervisorJob()+Dispatchers.Main)）

**方案**: 在ComposeBridge中添加`val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`，在registerAnimatableFactory中设置`anim.scope = mainScope`。同时在resetState中cancel mainScope的job并重建。

### P0-2: 修复SearchBar/DockedSearchBar的active状态管理

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/component/ComplementComponents.kt`

**修改内容**:
1. SearchBarLayout: 将`active`改为本地`var active by remember { mutableStateOf(node.boolProp("active", false)) }`，同时在onActiveChange中更新本地状态并调用Lua的onActiveChange回调
2. DockedSearchBarLayout: 同样修复active状态管理，onSearch/onActiveChange调用Lua回调而非空lambda
3. query同理，确保props变化能同步（使用LaunchedEffect同步props到本地状态）

### P0-3: 修复compose.delay()阻塞主线程

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/bridge/ComposeInjectors.kt` (registerDelayTool函数)

**修改内容**:
1. 删除Thread.sleep实现
2. 改为通过ComposeBridge.mainScope.launch启动协程，使用kotlinx.coroutines.delay实现非阻塞延迟
3. 但JavaFunction是同步的，无法直接suspend。方案：delay函数检查是否在协程中（通过LuaState的thread/runningCoroutine），否则用mainScope.postDelayed模拟
4. 更好方案：将delay作为Lua协程库的一部分，在CoroutineSupport中提供delay方法；compose.delay()全局函数保留但改为非阻塞：通过mainHandler.postDelayed实现延迟回调（不阻塞当前线程），接受一个callback函数参数。或者直接：compose.delay(ms)不阻塞，立即返回，用Log.e提示用户使用scope.delay(ms)在协程中使用

**方案**: 改为`Handler(Looper.getMainLooper()).postDelayed({ /* 不做任何事，仅不阻塞 */ }, ms)` 是不对的——delay应该是挂起函数。正确做法：在JavaFunction中无法suspend，所以compose.delay()应该输出错误日志提示使用`scope:delay(ms)`，而不是Thread.sleep。将Thread.sleep改为仅log警告并立即返回。

### P1-1: 修复输入组件受控/非受控状态同步

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/component/inputcomponents.kt`

**修改内容**:
对TextField/OutlinedTextField/Checkbox/Switch/Slider，使用"受控组件"模式：
1. 保持本地remember状态用于UI即时响应
2. 添加`LaunchedEffect(node)`或`LaunchedEffect(props值)` 同步外部props到本地状态——当Lua侧修改props时，本地状态同步更新
3. 示例模式：
```kotlin
var text by remember { mutableStateOf(node.stringProp("text") ?: "") }
val externalText = node.stringProp("text") ?: ""
LaunchedEffect(externalText) {
    if (text != externalText) text = externalText
}
```
4. Checkbox/Switch同理同步checked，Slider同步value

### P1-2: 修复animateColorAsState颜色精度问题

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/bridge/ComposeInjectors.kt` (registerAnimateColorFactory)

**修改内容**:
1. AnimatedFloat是Float动画，不能直接用于Color。需要创建LuaAnimatedColor类包装Color状态
2. 在ComposeHost中检测animatedValues中的值类型，如果是Color类型则使用`animateColorAsState`而非`animateFloatAsState`
3. 或者简化方案：将颜色存储为Int/Long，在ComposeHost渲染时判断animatedFloat的初始值是否为颜色范围（0xFF000000L..0xFFFFFFFFL），用animateColorAsState
4. **更实用方案**: 修改AnimatedFloat支持Any类型，在ComposeHost中根据类型选择animate*AsState。或者单独实现LuaAnimatedColor，在injectors中注册compose.animateColorAsState返回LuaAnimatedColor，registerAnimateColorFactory改为创建LuaAnimatedColor(Long color)

### P1-3: 修复SpacerLayout丢失modifier

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/component/containercomponents.kt` (SpacerLayout函数)

**修改内容**:
将SpacerLayout改为：
```kotlin
@Composable private fun SpacerLayout(node: ComposeNode) {
    val w = node.floatProp("width", 0f); val h = node.floatProp("height", 0f)
    Spacer(modifier = ComposeRenderer.resolveModifier(node)
        .then(if (w > 0f) Modifier.width(w.dp) else Modifier)
        .then(if (h > 0f) Modifier.height(h.dp) else Modifier))
}
```

### P1-4: 修复LazyColumn/LazyRow的childrenFunc懒加载

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/component/layoutcomponents.kt`

**修改内容**:
1. 为LazyListScope注入item/items/itemsIndexed API到Lua中
2. 当childrenFunc不为null时，在LazyListScope直接调用childrenFunc（让Lua自己调用item/items），而不是包裹在item{}中
3. 实现方案：创建LazyListScopeWrapper，暴露`item(key, contentType, content)`、`items(count, key, contentType, itemContent)`等方法，通过ComposeBridge传递给childrenFunc
4. 同时改进LazyListScope的contentReceiver——childrenFunc.call()在LazyListScope的接收者中执行，使Lua侧可以写：
```lua
compose.LazyColumn {
    item { compose.Text({ text = "Header" }) }
    items(#list) { i -> compose.Text({ text = list[i] }) }
}
```

### P1-5: LuaActivity.onDestroy清理ComposeBridge状态

**文件**: `e:\Soft\Proje\LXC-LUA\core\src\main\kotlin\com\nirithy\lxclua\LuaActivity.kt`

**修改内容**:
1. 在onDestroy中，在luaState.close()之前调用`ComposeBridge.resetState()`
2. 确保ComposeBridge.resetState()清理：stateCache/rememberCache/animatedFloats/navBackStackCache/activeSharedTransitionScopes/activeAnimatedVisibilityScopes/rootState
3. 取消mainScope的Job，重建新的mainScope

### P2-1: ModalNavigationDrawer drawerState Lua控制

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/component/ComplementComponents.kt` (ModalDrawerLayout)

**修改内容**:
1. 使用`rememberDrawerState(DrawerValue.Closed)`而非boolean
2. 支持`open`/`gesturesEnabled`props
3. 通过LaunchedEffect响应props中的`open`属性变化调用open()/close()
4. 暴露onOpen/onClose回调给Lua

### P2-2: SharedTransitionLayout异常安全

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/animation/SharedTransitionComponent.kt`

**修改内容**:
使用try/finally确保pop执行：
```kotlin
ComposeBridge.pushActiveSharedTransitionScope(this)
try {
    // 渲染内容
    ComposeRenderer.RenderChildren(node)
} finally {
    ComposeBridge.popActiveSharedTransitionScope()
}
```
同理修复AnimatedVisibilityRenderer中的作用域push/pop。

### P2-3: compose.withFrameNanos()真正等下一帧

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/bridge/ComposeInjectors.kt`

**修改内容**:
使用`withInFrameNanos`是suspend函数，JavaFunction中无法直接调用。方案：通过Choreographer.postFrameCallback实现真正的下一帧回调：
```kotlin
Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
    fn.call(frameTimeNanos)
}
```

### P2-4: clickable的MutableInteractionSource缓存

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/modifier/ModifierChain.kt`

**问题**: ModifierChain.build()不是@Composable，无法直接remember。
**方案**: 使用node级别的缓存：在ComposeNode上添加`interactionSource`字段，build()时首次创建后复用。

### P2-5: Lua回调线程安全

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/render/composerenderer.kt`和ModifierChain.kt

**修改内容**:
在所有LuaObject.call()调用点包裹`synchronized(luaState)`。luaState可从ComposeBridge获取或通过ComposeNode传递。由于ComposeBridge是单例，可以添加`val luaLock = Any()`并在所有Lua调用处sync。

### P2-6: ComposeBridge支持多Activity（改进resetState）

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/bridge/ComposeBridge.kt`

**修改内容**:
1. 改进resetState()为完整重置：不仅清空列表，还要cancel所有协程
2. 添加`attachActivity(activity: Activity, luaState: LuaState)`和`detachActivity()`方法
3. LuaActivity.onCreate/onDestroy中调用attach/detach
4. 不做完全多实例支持（架构大改），但确保detach后状态干净，不影响下一个Activity

### P3-1: BackHandlerComponent boolProp清理

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/component/BackHandlerComponent.kt`

修改为：`node.boolProp("enabled", true)` — 使用boolProp的默认参数而非?:。

### P3-2: LazyList中verticalScroll检测

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/render/composerenderer.kt` (resolveModifier)

在verticalScroll处理中添加检测：如果在LazyList作用域内，不应用verticalScroll并logW警告。但因为作用域检测复杂，简化方案：在文档中说明，不做代码改动。

### P3-3: 旧ComposeNode树LuaObject引用释放

**文件**: `core/src/main/kotlin/com/nirithy/luacompose/bridge/ComposeBridge.kt` (refreshNodeTree)

修改内容：在refreshNodeTree创建新rootNode之前，遍历旧rootNode树，对所有LuaObject引用调用`.gc()`或确保Lua GC可回收。可在ComposeNode上添加`release()`方法，递归释放callbacks和childrenFunc。

---

## 组件拓展建议（可选，在修复核心问题后评估）

根据使用场景，以下组件在现有框架中缺失但常用，可按需添加：
1. **DropdownMenu/DropdownMenuItem** — 弹出菜单
2. **ExposedDropdownMenu** — 下拉选择框
3. **BottomSheetScaffold/ModalBottomSheet** — 底部弹窗
4. **DateRangePicker** — 日期范围选择
5. **SwipeToDismiss** — 滑动删除
6. **PullToRefresh** — 下拉刷新（Material3已内置）
7. **HorizontalPager/VerticalPager** — 翻页（Accompanist）
8. **FlowRow/FlowColumn** — 流式布局

这些拓展项不纳入本次修复计划，核心问题修复后再考虑。

---

## 假设与决策

1. **修复优先级**: P0阻断性bug > P1功能缺陷 > P2体验/安全问题 > P3代码清理。优先确保API可用，不崩溃。
2. **不做架构重构**: ComposeBridge单例模式虽然不支持多Activity，但修复resetState()清理干净即可满足单Activity+多Lua页面场景，不做大改。
3. **delay()方案**: compose.delay()改为非阻塞但不执行实际延迟（输出警告），引导用户使用scope.launch中的delay。这是JavaFunction无法suspend的限制下的最优解。
4. **animateColorAsState方案**: 使用独立LuaAnimatedColor类，在ComposeHost中按类型分派animate*AsState。
5. **LazyColumn childrenFunc方案**: 注入LazyListScopeWrapper到Lua环境，让Lua侧直接调用item()/items() DSL函数，实现真正懒加载。这是较大的API改动，但必须修复以保证长列表性能。
6. **线程安全**: 对所有从Compose回调进入Lua的调用点添加synchronized保护LuaState，防止并发访问导致Lua栈损坏。
7. **测试验证**: 编译通过后通过test_new_features.lua运行核心场景验证，重点测试：动画API、输入组件双向绑定、SearchBar展开、Spacer modifier、LazyColumn长列表、delay不阻塞。

---

## 验证步骤

1. 编译core模块和app模块，确保无编译错误
2. 运行test_new_features.lua验证：
   - Animatable.animateTo()能正常工作
   - TextField输入能实时更新，外部修改text prop能同步
   - Checkbox/Switch/Slider受控正常
   - SearchBar点击能展开显示结果
   - Spacer设置background颜色能显示
   - LazyColumn长列表滚动流畅（懒加载）
   - compose.delay()不阻塞UI
   - 颜色动画平滑无突变
3. Activity重建测试：旋转屏幕/深色模式切换后，Compose UI正常重建无残留状态
4. 异常安全测试：Lua代码中抛出error后，SharedTransition/AnimatedVisibility不影响后续渲染
5. 内存检查：频繁刷新（如动画）后内存不持续增长
