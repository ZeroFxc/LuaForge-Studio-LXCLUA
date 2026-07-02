# Nirithy LuaCompose 官方教程

纯 Lua 驱动的 Jetpack Compose UI 框架。所有 UI 代码用 Lua 编写，实时渲染为原生 Android 界面。

## 快速开始

```lua
local compose = _G.compose

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier().fillMaxSize().padding(24),
        children = {
            compose.Text({
                text = "Hello Nirithy LuaCompose!",
                fontSize = 24,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),
        },
    })
end)
```

## 教程目录

| 编号 | 教程 | 覆盖内容 |
|------|------|----------|
| 01 | [入门](./01_hello.lua) | compose.render, Text, Modifier, Column, Spacer, padding, fillMaxSize, fillMaxWidth, fontSize, color, fontWeight, background |
| 02 | [状态管理](./02_counter.lua) | mutableState, Button, Row, onClick, weight, Spacer |
| 03 | [布局](./03_layouts.lua) | Column, Row, Box, Spacer, verticalArrangement, horizontalArrangement, fillMaxSize, fillMaxWidth, fillMaxHeight, weight, background, size, verticalScroll |
| 04 | [输入组件](./04_inputs.lua) | TextField, OutlinedTextField, Checkbox, Switch, Slider, onValueChange, onCheckedChange, 状态绑定 |
| 05 | [列表与卡片](./05_lists.lua) | LazyColumn, Card, 动态数据, for 循环生成子节点 |
| 06 | [动画](./06_animations.lua) | AnimatedVisibility, animateFloatAsState, Crossfade |
| 07 | [高级组件](./07_components.lua) | SearchBar, LinearProgressIndicator, CircularProgressIndicator, FilterChip, Badge, FloatingActionButton, Icon, TabRow, Tab |
| 08 | [导航与抽屉](./08_drawer.lua) | ModalNavigationDrawer, ModalDrawerSheet, Scaffold, BackHandler |
| 09 | [Canvas 绘图](./09_canvas.lua) | Canvas, onDraw, DrawScope (drawCircle, drawRect, drawLine, drawPath), continuousRedraw |
| 10 | [副作用与协程](./10_effects.lua) | LaunchedEffect, key, DisposableEffect, CoroutineScope, delay, remember |

## 核心语法规则

### 1. State 声明位置

```lua
-- ✅ 正确：在 render 外面声明
local count = compose.mutableState(0)

compose.render(function()
    -- count.value 读取；count.value = x 写入
end)
```

### 2. 子组件必须放在 children 表中

```lua
-- ✅ 正确
compose.Column({
    children = {
        compose.Text({ text = "Hello" }),
        compose.Text({ text = "World" }),
    },
})

-- ❌ 错误：直接放在表里
compose.Column({
    compose.Text({ text = "Hello" }),  -- 不会渲染！
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

-- ✅ 正确
local c = 0xFF00FF00
if flag then c = 0xFFFF0000 end
local x = { color = c }
```

## 状态类型对比

| 类型 | 创建方式 | 触发效果 | 适用场景 |
|------|----------|----------|----------|
| `state` | `compose.state(value)` | 全量重建 Lua 节点树 | 页面结构变化 |
| `mutableState` | `compose.mutableState(value)` | 轻量重组 | 动画、拖拽、计数器 |
| `derivedStateOf` | `compose.derivedStateOf(fn)` | 只读 | 派生计算 |

## 组件全景

### 布局
Column, Row, Box, LazyColumn, LazyRow, Spacer, BoxWithConstraints

### 显示
Text, Icon

### 输入
Button, TextButton, OutlinedButton, IconButton, TextField, OutlinedTextField, Checkbox, Switch, Slider

### 容器
Card, Surface, Scaffold, Divider, VerticalDivider

### Material3
FloatingActionButton, SmallFloatingActionButton, LargeFloatingActionButton, ExtendedFloatingActionButton, AssistChip, FilterChip, InputChip, SuggestionChip, TabRow, Tab, ScrollableTabRow, ModalNavigationDrawer, DismissibleNavigationDrawer, ModalDrawerSheet, PermanentNavigationDrawer, SearchBar, DockedSearchBar, DatePicker, DatePickerDialog, TimePicker, LinearProgressIndicator, CircularProgressIndicator, Badge, BadgedBox

### 动画
AnimatedVisibility, AnimatedContent, Crossfade, InfiniteTransition, SharedTransitionLayout

### 其他
Canvas, LaunchedEffect, key, DisposableEffect, BackHandler, AndroidView, NavHost

## Modifier API

### 尺寸
`fillMaxSize()`, `fillMaxWidth()`, `fillMaxHeight()`, `size(w, h)`, `width(w)`, `height(h)`, `wrapContentSize()`, `wrapContentWidth()`, `wrapContentHeight()`, `widthIn(min, max)`, `heightIn(min, max)`, `sizeIn(minW, maxW, minH, maxH)`, `aspectRatio(r)`

### 内边距
`padding(all)`, `padding(top, bottom, start, end)`, `padding(horizontal, vertical)`

### 布局
`weight(f)`, `offset(x, y)`

### 外观
`background(color)`, `alpha(f)`, `borderRadius(r)`, `circle()`, `border(w, color)`, `shadow(elev, shape)`, `clip()`, `clipCircle()`

### 变换
`rotate(deg)`, `scale(sx, sy)`, `graphicsLayer{}`

### 交互
`clickableLua(callback)`, `verticalScroll()`, `animateContentSize()`, `onSizeChanged(callback)`

### 绘制
`drawBehind(callback)`, `drawWithContent(callback)`

### 动画
`sharedElement(key)`, `sharedBounds(key)`

## DrawScope API

Canvas 的 onDraw 回调接收 `draw` 对象，支持以下方法：

`drawCircle(x, y, radius, color)`, `drawRect(left, top, right, bottom, color)`, `drawLine(x1, y1, x2, y2, color, width)`, `drawRoundRect(left, top, right, bottom, radius, color)`, `drawPath(path, color, width)`, `drawText(text, x, y, color, size)`

坐标和尺寸均为 Double 类型（Lua number）。