# Nirithy Compose 动画复刻计划

## 摘要

1. 修复 SearchBar 展开过宽、不自动合上的问题
2. 基于 `docs/compose-animations-main` 的 22 个动画示例，用 nirithy compose Lua 语法 1:1 复刻，缺失的 API 补全，优先实现 Canvas 可实现的动画

---

## 一、SearchBar 修复

### 当前问题
- SearchBar 展开时撑满全宽，覆盖其他 UI 元素
- 搜索后不自动合上

### 修复方案
- 在 `07_components.lua` 中给 SearchBar 外层包裹一个固定宽度的 Box/Column
- 确保 `onSearch` 回调中设置 `searchActive.value = false`（已有，确认生效）
- 如果渲染层问题，检查 `SearchBarRenderer` 的 active 处理

### 涉及文件
- `docs/lua_examples/07_components.lua` — 调整 SearchBar 结构

---

## 二、动画复刻 — 优先级分类

### 第 1 层：现有 API 可以完整实现（10 个，优先）

| # | 动画 | 来源 | 用到的 API | 状态 |
|---|------|------|------------|------|
| 1 | Animate Content Size | Example1 | animateDpAsState, Card, Column, clickable | 需补 animateDpAsState |
| 2 | Animated Visibility | Example2 | AnimatedVisibility, slideInHorizontally, fadeIn, scaleIn | 现有 API 够用 |
| 3 | Color State Morph | Example3 | animateColorAsState, backgroundColor, clip | 现有 API 够用 |
| 4 | FAB Spring Morph | Example4 | animateDpAsState, animateFloatAsState, animateColorAsState, spring, rotate, clip | 需补 animateDpAsState |
| 5 | Animated Counter | Example5 | AnimatedContent, slideInVertically, fadeIn, togetherWith | 现有 API 够用 |
| 6 | Crossfade Switcher | Example6 | Crossfade, Button, OutlinedButton | 现有 API 够用 |
| 7 | Pulsing Heart | Example7 | InfiniteTransition, animateFloat, scale, alpha, infiniteRepeatable | 现有 API 够用 |
| 8 | Custom Loading Spinner | Example8 | InfiniteTransition, Canvas, drawArc, drawArcStroke, rotate(drawScope) | 需补 drawScope.rotate |
| 9 | Spring Drag Box | Example9 | Animatable, pointerInput, detectDragGestures, spring, offset | 现有 API 够用 |
| 10 | Easing Showcase | Example10 | animateFloatAsState, tween, BoxWithConstraints | 现有 API 够用 |

### 第 2 层：需补少量 API（6 个）

| # | 动画 | 来源 | 用到的 API | 缺失 API |
|---|------|------|------------|----------|
| 11 | Play/Pause Morph | Example11 | animateFloatAsState, animateColorAsState, Canvas, drawPath, drawCircle, lerp | lerp |
| 13 | Swipeable Cards | Example13 | Animatable, pointerInput, detectDragGestures, graphicsLayer, spring, offset | 现有 API 够用 |
| 14 | Radial FAB Menu | Example14 | Animatable, animateFloatAsState, spring, graphicsLayer, clipCircle | 现有 API 够用 |
| 15 | 3D Card Flip | Example15 | animateFloatAsState, graphicsLayer(rotationY, cameraDistance) | 现有 API 够用 |
| 16 | Confetti Burst | Example16 | Canvas, withFrameNanos, LaunchedEffect, drawRect, rotate(drawScope), detectTapGestures | drawScope.rotate, detectTapGestures |
| 17 | Wave Field | Example17 | Canvas, withFrameNanos, drawPath, lerp, Brush.verticalGradient | lerp, Brush.verticalGradient |

### 第 3 层：需补较多 API（4 个）

| # | 动画 | 来源 | 用到的 API | 缺失 API |
|---|------|------|------------|----------|
| 18 | Metaball Liquid | Example18 | Canvas, withFrameNanos, Brush.radialGradient, BlendMode.Plus, CompositingStrategy.Offscreen | BlendMode, CompositingStrategy |
| 19 | Mesh Aurora | Example19 | Canvas, withFrameNanos, Brush.radialGradient, BlendMode.Plus, CompositingStrategy.Offscreen | BlendMode, CompositingStrategy |
| 20 | Pendulum Wave | Example20 | Canvas, withFrameNanos, drawLine, drawCircle, lerp | lerp |
| 21 | Rainy | Example21 | Canvas, withFrameNanos, drawLine, Brush.linearGradient, StrokeCap.Round | Brush.linearGradient |

### 跳过（2 个）

| # | 动画 | 原因 |
|---|------|------|
| 12 | Shared Bounds Expansion | SharedTransitionLayout 是 Compose 实验性 API，复杂度极高 |
| 22 | Soap Bubble Drag | 依赖 AGSL RuntimeShader 自定义着色器，无法通用化 |

---

## 三、需补充的 Kotlin API

### 3.1 animateDpAsState（优先级：高）
- **文件**: `core/src/main/kotlin/com/nirithy/luacompose/animation/AnimatableSupport.kt` 或新建 `AnimateDpAsState.kt`
- **实现**: 创建 `AnimatedDp` 类，包装 `animateDpAsState`，暴露 `getValue()` 返回 Float
- **Lua 用法**: `local dp = compose.animateDpAsState(targetValue.value)`
- **涉及动画**: Example 1, 4

### 3.2 drawScope.rotate（优先级：高）
- **文件**: `core/src/main/kotlin/com/nirithy/luacompose/draw/DrawScopeWrapper.kt`
- **实现**: 添加 `fun rotate(degrees: Double)` 方法，调用 `drawScope.rotate(degrees.toFloat())`
- **Lua 用法**: `draw.rotate(angle)` 在 onDraw 回调中使用
- **涉及动画**: Example 8, 16

### 3.3 lerp 工具函数（优先级：中）
- **文件**: 新建 `core/src/main/kotlin/com/nirithy/luacompose/math/Lerp.kt`
- **实现**: 注册 `compose.lerp(start, end, fraction)` 返回插值结果
- **Lua 用法**: `local x = compose.lerp(x1, x2, t)`
- **涉及动画**: Example 11, 17, 20

### 3.4 BlendMode 支持（优先级：中）
- **文件**: `core/src/main/kotlin/com/nirithy/luacompose/draw/DrawScopeWrapper.kt`
- **实现**: 在 Canvas 渲染时支持 `blendMode` prop，或在 draw 方法中允许指定 blendMode
- **Lua 用法**: Canvas 的 `blendMode = "Plus"` prop
- **涉及动画**: Example 18, 19

### 3.5 CompositingStrategy.Offscreen（优先级：中）
- **文件**: `core/src/main/kotlin/com/nirithy/luacompose/modifier/ModifierChain.kt`
- **实现**: 添加 `compositingStrategy("Offscreen")` modifier
- **Lua 用法**: `.compositingStrategy("Offscreen")` 
- **涉及动画**: Example 18, 19

### 3.6 Brush.verticalGradient / Brush.linearGradient（优先级：中）
- **文件**: `core/src/main/kotlin/com/nirithy/luacompose/bridge/ComposeInjectors.kt`
- **实现**: 类似已有的 `registerBrushRadialGradient`，创建 `Brush.verticalGradient` 和 `Brush.linearGradient`
- **Lua 用法**: `compose.Brush.verticalGradient({ colors = {...}, stops = {...} })`
- **涉及动画**: Example 17, 21

---

## 四、输出文件结构

```
docs/lua_examples/
├── 11_animate_content_size.lua    # Example 1
├── 12_animated_visibility.lua     # Example 2
├── 13_color_morph.lua             # Example 3
├── 14_fab_spring.lua              # Example 4
├── 15_animated_counter.lua        # Example 5
├── 16_crossfade.lua               # Example 6
├── 17_pulsing_heart.lua           # Example 7
├── 18_loading_spinner.lua         # Example 8
├── 19_spring_drag.lua             # Example 9
├── 20_easing_showcase.lua         # Example 10
├── 21_play_pause_morph.lua        # Example 11
├── 22_swipeable_cards.lua         # Example 13
├── 23_radial_fab.lua              # Example 14
├── 24_3d_card_flip.lua            # Example 15
├── 25_confetti.lua                # Example 16
├── 26_wave_field.lua              # Example 17
├── 27_metaball.lua                # Example 18
├── 28_mesh_aurora.lua             # Example 19
├── 29_pendulum.lua                # Example 20
├── 30_rainy.lua                   # Example 21
```

---

## 五、实施顺序

### 阶段 1：Kotlin API 补充
1. `animateDpAsState` — 创建 AnimatedDp 类 + 注册
2. `drawScope.rotate` — DrawScopeWrapper 添加方法
3. `lerp` — 注册 compose.lerp
4. `Brush.verticalGradient` / `Brush.linearGradient` — 注册
5. `BlendMode` — Canvas 渲染支持
6. `CompositingStrategy` — Modifier 添加

### 阶段 2：Lua 示例编写（第 1 层）
按上述编号顺序，每个示例独立测试通过后再写下一个。

### 阶段 3：Lua 示例编写（第 2 层）
依赖阶段 2 的 API 补充完成后开始。

### 阶段 4：Lua 示例编写（第 3 层）
依赖阶段 3 的 API 补充完成后开始。

---

## 六、验证方法

- 每个 Lua 示例复制到 `main.lua` 运行
- 检查 logcat 输出无 `LuaError` 报错
- 视觉验证动画效果与原始 GIF 一致
- Kotlin 端编译通过：`./gradlew.bat :core:compileDebugKotlin`

---

## 七、假设与决策

- `AnimatedContent` 渲染器已存在（AnimationPlugin 中注册），可直接使用
- `InfiniteTransition` 渲染器已存在，支持 `animateFloat` 子节点
- `graphicsLayer` 已支持 rotationY + cameraDistance（3D 翻转）
- `pointerInputFull` 和 `gestures.detectDragGestures` 已可用
- SharedTransitionLayout（Example 12）和 AGSL RuntimeShader（Example 22）跳过
- 所有坐标/尺寸使用 Double 类型传递给 Lua number