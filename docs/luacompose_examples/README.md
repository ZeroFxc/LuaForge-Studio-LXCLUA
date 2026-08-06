# LuaCompose UI 示例项目

> 纯 Lua 驱动的 Jetpack Compose UI 框架示例集合

本目录包含 35 个 LuaCompose UI 示例，从入门到高级，涵盖了 LuaForge Studio 中声明式 UI 开发的各种使用场景。

## 快速开始

```lua
local compose = _G.compose

-- 最简单的 LuaCompose 应用
compose.render(function()
    return compose.Column({
        modifier = compose.Modifier().fillMaxSize().padding(24),
        children = {
            compose.Text({
                text = "Hello, LuaCompose!",
                fontSize = 24,
                color = 0xFF6750A4,
            }),
        },
    })
end)
```

## 教程目录

### 入门基础 (01-10)

| 编号 | 教程 | 覆盖内容 |
|------|------|----------|
| 01 | [入门](./01_hello.lua) | compose.render, Text, Modifier, Column, Spacer, padding, fillMaxWidth, fontSize, color, fontWeight |
| 02 | [状态管理](./02_counter.lua) | mutableState, Button, Row, onClick, weight |
| 03 | [布局](./03_layouts.lua) | Column, Row, Box, Spacer, verticalArrangement, horizontalArrangement, fillMaxSize, fillMaxWidth, fillMaxHeight, weight, background, size, verticalScroll |
| 04 | [输入组件](./04_inputs.lua) | TextField, OutlinedTextField, Checkbox, Switch, Slider, onValueChange, onCheckedChange |
| 05 | [列表与卡片](./05_lists.lua) | LazyColumn, Card, 动态数据, for 循环 |
| 06 | [动画](./06_animations.lua) | AnimatedVisibility, animateFloatAsState, Crossfade, Animatable |
| 07 | [高级组件](./07_components.lua) | SearchBar, LinearProgressIndicator, CircularProgressIndicator, FilterChip, Badge, FloatingActionButton, Icon, TabRow, Tab |
| 08 | [导航与抽屉](./08_drawer.lua) | ModalNavigationDrawer, ModalDrawerSheet, Scaffold, BackHandler |
| 09 | [Canvas 绘图](./09_canvas.lua) | Canvas, onDraw, DrawScope, continuousRedraw |
| 10 | [副作用与协程](./10_effects.lua) | LaunchedEffect, key, DisposableEffect, CoroutineScope, delay, remember |

### 动画高级 (11-20)

| 编号 | 教程 | 覆盖内容 |
|------|------|----------|
| 11 | [内容尺寸动画](./11_animate_content_size.lua) | animateContentSize, Modifier.animateContentSize() |
| 12 | [可见性动画](./12_animated_visibility.lua) | AnimatedVisibility, ExpandVertically, ShrinkHorizontally, FadeIn, FadeOut |
| 13 | [颜色渐变](./13_color_morph.lua) | animateColorAsState, 颜色过渡动画 |
| 14 | [弹簧动画](./14_fab_spring.lua) | spring, springSpec, DampingRatio, Stiffness |
| 15 | [动画计数器](./15_animated_counter.lua) | animateIntAsState, 数字滚动动画 |
| 16 | [交叉淡入淡出](./16_crossfade.lua) | Crossfade, AnimatedContent |
| 17 | [脉冲效果](./17_pulsing_heart.lua) | infiniteRepeatable, RepeatMode, 呼吸动画 |
| 18 | [加载动画](./18_loading_spinner.lua) | CircularProgressIndicator, 加载状态 |
| 19 | [弹簧拖拽](./19_spring_drag.lua) | SwipeToDismiss, Modifier.offset, 手势拖拽 |
| 20 | [缓动函数展示](./20_easing_showcase.lua) | Easing, FastOutSlowIn, LinearOutSlowIn, CustomEasing |

### 高级组件 (21-30)

| 编号 | 教程 | 覆盖内容 |
|------|------|----------|
| 21 | [播放暂停动画](./21_play_pause_morph.lua) | Icon morphing, AnimatedContent, 矢量动画 |
| 22 | [滑动卡片](./22_swipeable_cards.lua) | SwipeToDismissBox, 滑动删除, 阈值检测 |
| 23 | [径向浮动按钮](./23_radial_fab.lua) | AnimatedVisibility, 展开/折叠菜单 |
| 24 | [3D 卡片翻转](./24_3d_card_flip.lua) | graphicsLayer, rotationY, 3D 变换 |
| 25 | [纸屑粒子系统](./25_confetti.lua) | Canvas, 粒子系统, 物理模拟, continuousRedraw |
| 26 | [波场动画](./26_wave_field.lua) | Canvas, 正弦波, 时间驱动动画 |
| 27 | [Metaball 效果](./27_metaball.lua) | Canvas, 圆形融合, 距离场 |
| 28 | [极光彩极光](./28_mesh_aurora.lua) | Canvas, 梯形填充, 多层叠加 |
| 29 | [单摆动画](./29_pendulum.lua) | Canvas, 旋转运动, 钟摆物理 |
| 30 | [雨滴效果](./30_rainy.lua) | Canvas, 雨滴粒子, 重力模拟 |

### 输入与导航 (31-35)

| 编号 | 教程 | 覆盖内容 |
|------|------|----------|
| 31 | [输入组件进阶](./31_input_components.lua) | TextField, 文本样式, 错误提示 |
| 32 | [流式网格布局](./32_flow_grid.lua) | FlowRow, FlowColumn, 自动换行布局 |
| 33 | [对话框弹窗](./33_dialogs_popups.lua) | AlertDialog, Dialog, ModalBottomSheet |
| 34 | [导航](./34_navigation.lua) | NavHost, NavController, 路由跳转 |
| 35 | [下拉刷新与 Snackbar](./35_pull_refresh_snackbar.lua) | pullToRefresh, SnackbarHost, 手势处理 |

---

## 核心语法规则

### 1. State 声明位置

```lua
-- ✅ 正确：在 render 外面声明状态
local count = compose.mutableState(0)

compose.render(function()
    -- count.value 读取；count.value = x 写入
end)
```

**重要**:
- **state**: 结构性变化（可见性切换）使用，触发全量刷新
- **mutableState**: 轻量变化（计数器、动画值），触发轻量重组

### 2. 子组件必须放在 children 表中

```lua
-- ✅ 正确
compose.Column({
    children = {
        compose.Text({ text = "Hello" }),
        compose.Text({ text = "World" }),
    },
})

-- ❌ 错误：直接放在表里不会渲染
compose.Column({
    compose.Text({ text = "Hello" }),  -- 被忽略！
})
```

### 3. Modifier 链式调用

```lua
compose.Modifier()
    .fillMaxSize()
    .padding(16)
    .background(0xFF6750A4)
    .borderRadius(8)
```

### 4. 颜色格式

```lua
color = 0xFF6750A4  -- 不透明紫色 (AARRGGBB)
color = 0x80FF0000  -- 半透明红色
```

### 5. 回调函数

```lua
compose.Button({
    onClick = function()
        count.value = count.value + 1
    end,
})
```

### 6. Lua 表内不支持 if 表达式

```lua
-- ❌ 错误
local x = { color = flag and 0xFFFF0000 or 0xFF00FF00 }
-- and/or 在表构造器中可能不返回预期值

-- ✅ 正确：使用传统 if/else
local c = 0xFF00FF00
if flag then c = 0xFFFF0000 end
local x = { color = c }
```

---

## 状态类型对比

| 类型 | 创建方式 | 触发效果 | 适用场景 |
|------|----------|----------|----------|
| `state` | `compose.state(value)` | 全量重建 Lua 节点树 | 页面结构变化 |
| `mutableState` | `compose.mutableState(value)` | 轻量重组 | 动画、拖拽、计数器 |
| `derivedStateOf` | `compose.derivedStateOf(fn)` | 只读 | 派生计算 |
| `LocalDensity` | `compose.LocalDensity` | 只读 | dp 到 px 换算 |
| `LocalContext` | `compose.LocalContext` | 只读 | Android Context 访问 |

---

## 组件 API 参考

### 布局组件

| 组件 | 说明 |
|------|------|
| `Column` | 垂直排列子元素 |
| `Row` | 水平排列子元素 |
| `Box` | 层叠布局 |
| `LazyColumn` | 垂直滚动列表（高性能） |
| `LazyRow` | 水平滚动列表（高性能） |
| `Spacer` | 占位空白（Spacer({modifier = Modifier().height(16)})） |
| `BoxWithConstraints` | 受父级约束影响的 Box |

### 显示组件

| 组件 | 说明 |
|------|------|
| `Text` | 文本展示（text 或 textLambda） |
| `Icon` | 图标展示 |

### 输入组件

| 组件 | 说明 |
|------|------|
| `Button` | 按钮（text + onClick） |
| `TextButton` | 文字按钮 |
| `OutlinedButton` | 轮廓按钮 |
| `IconButton` | 图标按钮 |
| `TextField` | 单行输入框（value + onValueChange） |
| `OutlinedTextField` | 轮廓输入框 |
| `Checkbox` | 复选框（checked + onCheckedChange） |
| `Switch` | 开关（checked + onCheckedChange） |
| `Slider` | 滑块（value + onValueChange） |

### 容器组件

| 组件 | 说明 |
|------|------|
| `Card` | 卡片视图 |
| `Surface` | 基础容器 |
| `Scaffold` | 应用骨架（TopBar + BottomBar + content） |
| `Divider` | 水平分割线 |
| `VerticalDivider` | 垂直分割线 |

### Material 3 组件

| 组件 | 说明 |
|------|------|
| `FloatingActionButton` | 浮动操作按钮 |
| `ExtendedFloatingActionButton` | 扩展浮动按钮 |
| `AssistChip` | 辅助标签 |
| `FilterChip` | 过滤标签 |
| `TabRow` / `Tab` | 标签栏 |
| `ModalNavigationDrawer` | 模态导航抽屉 |
| `ModalDrawerSheet` | 抽屉内容 |
| `SearchBar` / `DockedSearchBar` | 搜索栏 |
| `LinearProgressIndicator` | 线性进度条 |
| `CircularProgressIndicator` | 圆形进度条 |
| `Badge` / `BadgedBox` | 徽章 |
| `DatePicker` / `DatePickerDialog` | 日期选择器 |
| `TimePicker` | 时间选择器 |

### 动画组件

| 组件 | 说明 |
|------|------|
| `AnimatedVisibility` | 显示/隐藏带动画 |
| `AnimatedContent` | 内容切换动画 |
| `Crossfade` | 交叉淡入淡出 |
| `InfiniteTransition` | 无限循环动画 |
| `SharedTransitionLayout` | 共享元素动画 |

### Canvas 组件

```lua
compose.Canvas({
    modifier = Modifier().fillMaxWidth().height(200),
    continuousRedraw = true,  -- 持续重绘（动画）或仅绘制一次
    onDraw = function(draw, w, h, timeSec)
        -- 绘制逻辑
    end,
    onTap = function(x, y)
        -- 点击事件
    end,
})
```

### 副作用组件

| 组件 | 说明 |
|------|------|
| `LaunchedEffect(key, fn)` | 协程副作用，key 变化时重新执行 |
| `DisposableEffect(key, fn)` | 带清理的副作用 |
| `BackHandler` | 处理返回键 |

---

## Modifier API 参考

### 尺寸

| 方法 | 说明 |
|------|------|
| `fillMaxSize()` | 填满父级 |
| `fillMaxWidth()` | 填满父级宽度 |
| `fillMaxHeight()` | 填满父级高度 |
| `size(w, h)` | 指定大小 |
| `width(w)` / `height(h)` | 指定宽高 |
| `wrapContentSize()` | 包裹内容 |
| `widthIn(min, max)` | 宽度范围 |
| `heightIn(min, max)` | 高度范围 |
| `sizeIn(minW, maxW, minH, maxH)` | 尺寸范围 |
| `aspectRatio(r)` | 宽高比 |

### 边距与偏移

| 方法 | 说明 |
|------|------|
| `padding(all)` | 统一内边距 |
| `padding(top, bottom, start, end)` | 分别设置内边距 |
| `padding(horizontal, vertical)` | 水平和垂直内边距 |
| `weight(f)` | 权重（LinearLayout 风格） |
| `offset(x, y)` | 位置偏移 |

### 外观

| 方法 | 说明 |
|------|------|
| `background(color)` | 背景色 |
| `alpha(f)` | 透明度 |
| `borderRadius(r)` | 圆角 |
| `circle()` | 圆形 |
| `border(w, color)` | 边框 |
| `shadow(elev, shape)` | 阴影 |
| `clip()` | 裁剪到边界 |
| `clipCircle()` | 裁剪为圆形 |

### 变换

| 方法 | 说明 |
|------|------|
| `rotate(deg)` | 旋转 |
| `scale(sx, sy)` | 缩放 |
| `graphicsLayer{}` | 高级图形层变换 |

### 交互

| 方法 | 说明 |
|------|------|
| `clickableLua(callback)` | 可点击 |
| `verticalScroll()` | 垂直滚动 |
| `animateContentSize()` | 尺寸动画 |
| `onSizeChanged(callback)` | 尺寸变化回调 |

### 绘制

| 方法 | 说明 |
|------|------|
| `drawBehind(callback)` | 在内容之后绘制 |
| `drawWithContent(callback)` | 自定义绘制内容 |

---

## DrawScope API

Canvas 的 `onDraw` 回调接收 `draw` 对象和尺寸参数：

```lua
onDraw = function(draw, width, height, timeSec)
    -- draw: DrawScope 对象
    -- width, height: Canvas 尺寸
    -- timeSec: 连续重绘时的时间（秒）
end
```

### 绘制方法

| 方法 | 说明 |
|------|------|
| `draw.drawCircle(x, y, radius, color)` | 绘制圆形 |
| `draw.drawRect(left, top, right, bottom, color)` | 绘制矩形 |
| `draw.drawRoundRect(left, top, right, bottom, radius, color)` | 绘制圆角矩形 |
| `draw.drawLine(x1, y1, x2, y2, color, width)` | 绘制直线 |
| `draw.drawPath(path, color, width)` | 绘制路径 |
| `draw.drawText(text, x, y, color, size)` | 绘制文字 |
| `draw.drawImage(image, x, y)` | 绘制图片 |

### 坐标系统

- 坐标原点 (0, 0) 在 Canvas 左上角
- X 轴向右为正，Y 轴向下为正
- 单位：px（使用 LocalDensity.density 从 dp 转换）

---

## 执行示例

### 方式一：通过 LuaForge Studio IDE

1. 打开 LuaForge Studio
2. 新建项目或打开现有项目
3. 将示例代码复制到 `main.lua`
4. 点击运行按钮

### 方式二：通过命令行

```bash
# 在 Termux 或 Linux 环境
./lxclua examples/01_hello.lua
```

### 方式三：嵌入 Android 应用

```kotlin
// Kotlin
val luaCode = File("01_hello.lua").readText()
luaExecutor.execute(luaCode)
```

---

## 常见问题

### 1. 状态更新不触发 UI 刷新

**原因**: 在 render 函数内部创建状态
**解决**: 将状态声明移到 render 外部

### 2. 回调函数中 UI 未更新

**原因**: 回调在主线程外执行
**解决**: 确保状态更新在主线程执行

### 3. Canvas 连续重绘卡顿

**原因**: 复杂绘制逻辑在每一帧执行
**解决**: 优化绘制逻辑，减少不必要的计算，使用 `timeSec` 驱动而非随机数

### 4. children 未渲染

**原因**: 子组件未放在 `children` 表中
**解决**: 使用 `children = { ... }` 包装所有子元素

---

## 相关资源

- [Lua VM 文档](../../app/src/main/jni/lua/docs/README.md)
- [Lua API 参考](../../app/src/main/jni/lua/docs/LUA_API.md)
- [Lua 语法参考](../../app/src/main/jni/lua/docs/SYNTAX_REFERENCE.md)
