-- ============================================================
-- Lua Compose 教学式全功能 Demo v9
-- 按功能分区，每个区域都有可见的视觉标记（圆角背景、边框等）
-- 适合快速上手学习 Lua Compose 的各个功能模块
-- ============================================================

-- 颜色 lerp 工具函数（不需要 bit 库，纯数学运算）
local function lerpColor(c1, c2, t)
    local r1 = math.floor(c1 / 65536) % 256
    local g1 = math.floor(c1 / 256) % 256
    local b1 = c1 % 256
    local r2 = math.floor(c2 / 65536) % 256
    local g2 = math.floor(c2 / 256) % 256
    local b2 = c2 % 256
    local r = math.floor(r1 + (r2 - r1) * t + 0.5)
    local g = math.floor(g1 + (g2 - g1) * t + 0.5)
    local b = math.floor(b1 + (b2 - b1) * t + 0.5)
    return 0xFF000000 + r * 65536 + g * 256 + b
end

local function main()
    -- ========== 状态变量 ==========
    local count = compose.state(0)               -- 计数器
    local name = compose.state("")               -- 文本输入
    local showCard = compose.state(true)         -- 控制卡片可见性
    local currentPage = compose.state(0)         -- Crossfade 页面切换
    local scope = compose.rememberCoroutineScope() -- 协程作用域

    -- 预创建 Color 对象（必须在函数体内，不能在表构造器里声明 local）
    local colorBlue = compose.Color(0xFF2196F3)
    local colorBlueAlpha = colorBlue.copy(0.5)

    -- 拖拽偏移量
    local dragX = compose.mutableState(0.0)
    local dragY = compose.mutableState(0.0)

    -- ========== 动画集锦状态 ==========
    -- 11a: 卡片展开/收缩 — animateFloatAsState 驱动高度插值（0→1）
    local expandAnim = compose.animateFloatAsState(0.0)
    -- 11b: 调色板 — animateFloatAsState 驱动颜色渐进，只插值端点颜色
    local selectedIndex = compose.state(1)       -- 当前选中索引（整数）
    local prevColorIdx = compose.state(1)       -- 动画起始颜色索引
    local colorAnim = compose.animateFloatAsState(1.0)  -- 颜色动画浮点值
    local colorNames = {"珊瑚", "青柠", "天蓝", "薰衣草"}
    local paletteColors = {0xFFEF5350, 0xFFC6FF00, 0xFF40C4FF, 0xFFAB47BC}
    -- 11c: FAB 变形 — animateFloatAsState 驱动所有属性插值（0→1）
    local fabExpanded = compose.animateFloatAsState(0.0)
    local fabColorStart = compose.Color(0xFFE91E63)
    local fabColorEnd = compose.Color(0xFF42A5F5)
    -- 11f: 卡片翻转
    local flipRotation = compose.animateFloatAsState(0.0)
    local density = compose.LocalDensity.density

    -- 11g: Canvas Path 形变 (Play/Pause)
    local isPlaying = compose.state(false)
    local morphProgress = compose.animateFloatAsState(0.0)

    -- 11h: 径向 FAB 菜单
    local radialOpen = compose.state(false)
    local sat1 = compose.animateFloatAsState(0.0)
    local sat2 = compose.animateFloatAsState(0.0)
    local sat3 = compose.animateFloatAsState(0.0)
    local sat4 = compose.animateFloatAsState(0.0)

    -- 11i: 卡片滑动删除
    -- ★ 拖拽用 mutableState 即时响应，松手超过阈值直接切换卡片
    --   注意：旧卡和新卡是同一个 Compose 节点，所以飞出动画会导致新卡继承动画值
    --   因此采用"即时切换"方案：松手超过阈值→立即切换+归零，无残留动画
    local swipeTopIdx = compose.state(0)
    local swipeDragX = compose.mutableState(0.0)
    local swipeColors = {0xFFEF5350, 0xFF42A5F5, 0xFF66BB6A, 0xFFFFCA28}

    -- 11j: 径向 FAB 菜单（状态必须在 return 之前声明）
    local fabOpen = compose.state(false)
    local fabRadius = 120  -- 增大半径，避免按钮重叠
    local fabCount = 4
    local fabArc = 180
    local fabLabels = {"★", "♥", "✦", "✿"}
    local fabColors = {0xFFEF5350, 0xFF42A5F5, 0xFF66BB6A, 0xFFFFCA28}
    local fabToast = compose.state("")  -- 卫星按钮点击提示
    local fabAnims = {}
    for i = 1, fabCount do
        fabAnims[i] = compose.animateFloatAsState(0.0)
    end
    local fabRotation = compose.animateFloatAsState(0.0)
    local fabSatellites = {}
    for i = 1, fabCount do
        local sweepStart = 180 + (180 - fabArc) / 2
        local angleDeg = sweepStart + ((i - 1) / (fabCount - 1)) * fabArc
        local rad = angleDeg * math.pi / 180
        local tx = fabRadius * math.cos(rad)
        local ty = fabRadius * math.sin(rad)
        local anim = fabAnims[i]
        -- ★ 使用 offsetLambda 而非 graphicsLayerLambda，确保点击区域跟随按钮位置
        table.insert(fabSatellites, compose.Box {
            modifier = compose.Modifier()
                .size(52, 52)
                .offsetLambda(function()
                    local v = anim.value
                    return {x = tx * v, y = ty * v}
                end)
                .graphicsLayerLambda(function()
                    local v = anim.value
                    return {
                        scaleX = 0.4 + 0.6 * v, scaleY = 0.4 + 0.6 * v,
                        alpha = v,
                    }
                end)
                .clipCircle()
                .backgroundRounded(fabColors[i], 26)
                .clickableLua(function()
                    fabToast.value = "点击了 " .. fabLabels[i]
                end),
            contentAlignment = "Center",
            children = {
                compose.Text {
                    text = fabLabels[i], fontSize = 22, fontWeight = "Bold",
                    color = 0xFFFFFFFF,
                },
            },
        })
    end

    return compose.Column {
        modifier = compose.Modifier()
            .fillMaxWidth()
            .verticalScroll()
            .padding(16),
        horizontalAlignment = "CenterHorizontally",
        children = {

            -- ================================================
            --  标题栏
            -- ================================================
            compose.Text {
                text = "Lua Compose 教学 Demo v8",
                fontSize = 22,
                fontWeight = "Bold",
                color = compose.Theme.onPrimary,
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .backgroundRounded(compose.Theme.primary, 14)
                    .padding(20),
            },

            -- ================================================
            --  1. 基础组件 + Icon
            -- ================================================
            compose.Text {
                text = "1. 基础组件 + Icon 图标",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            -- Icon 演示
            compose.Row {
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .backgroundRounded(compose.Theme.surfaceVariant, 10)
                    .padding(12),
                horizontalArrangement = "SpaceEvenly",
                children = {
                    compose.Icon { name = "Home", color = compose.Theme.primary, size = 28 },
                    compose.Icon { name = "Favorite", color = compose.Theme.error, size = 28 },
                    compose.Icon { name = "Star", color = compose.Theme.tertiary, size = 28 },
                    compose.Icon { name = "Search", color = compose.Theme.secondary, size = 28 },
                    compose.Icon { name = "Settings", color = compose.Theme.onSurfaceVariant, size = 28 },
                },
            },

            -- 按钮 + 计数器
            compose.Row {
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8),
                horizontalArrangement = "Center",
                children = {
                    compose.Button {
                        onClick = function() count.value = count.value - 1 end,
                        modifier = compose.Modifier().weight(1).padding(4),
                        children = { compose.Text { text = "-1" } },
                    },
                    compose.Text {
                        text = "count = " .. tostring(count.value),
                        fontSize = 20,
                        fontWeight = "Bold",
                        color = compose.Theme.onSurface,
                        modifier = compose.Modifier()
                            .weight(2).padding(8)
                            .backgroundRounded(compose.Theme.surfaceVariant, 8)
                            .paddingHv(16, 10),
                    },
                    compose.Button {
                        onClick = function() count.value = count.value + 1 end,
                        modifier = compose.Modifier().weight(1).padding(4),
                        children = { compose.Text { text = "+1" } },
                    },
                },
            },

            -- 文本输入
            compose.OutlinedTextField {
                text = name.value,
                onValueChange = function(v) name.value = v end,
                label = "输入你的名字",
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8),
                singleLine = true,
            },
            compose.Text {
                text = "你好, " .. name.value .. "!",
                fontSize = 16,
                fontWeight = "Medium",
                color = compose.color(0x1565C0),
                modifier = compose.Modifier()
                    .fillMaxWidth().paddingTop(4)
                    .backgroundRounded(compose.Theme.primaryContainer, 8)
                    .padding(12),
            },

            -- ================================================
            --  2. 图形首类对象：Color / Offset / Size / Rect
            -- ================================================
            compose.Text {
                text = "2. 图形首类对象",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            compose.Text {
                text = "compose.Color(0xFFRRGGBB) 创建颜色对象，支持 copy(alpha)\n"
                    .. "compose.Offset(x, y) / compose.Size(w, h) / compose.Rect(l, t, r, b)",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(8),
            },

            -- Color 对象演示
            compose.Row {
                modifier = compose.Modifier().fillMaxWidth(),
                horizontalArrangement = "SpaceEvenly",
                children = {
                    compose.Text {
                        text = "原始",
                        fontSize = 12,
                        fontWeight = "Bold",
                        color = compose.Theme.onPrimary,
                        modifier = compose.Modifier()
                            .backgroundRounded(colorBlue.toArgb(), 6)
                            .paddingHv(12, 8),
                    },
                    compose.Text {
                        text = "半透明",
                        fontSize = 12,
                        fontWeight = "Bold",
                        color = compose.Theme.onSurface,
                        modifier = compose.Modifier()
                            .backgroundRounded(colorBlueAlpha.toArgb(), 6)
                            .paddingHv(12, 8),
                    },
                },
            },

            -- ================================================
            --  2.5 字体排版与形状 (Typography & Shapes)
            -- ================================================
            compose.Text {
                text = "2.5 字体排版与形状",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },
            compose.Column {
                modifier = compose.Modifier().fillMaxWidth().padding(12),
                children = {
                    compose.Text { text = "📝 字体排版 (Typography)", fontSize = 16, fontWeight = "Bold", modifier = compose.Modifier().paddingBottom(8) },
                    compose.Text { text = "bodyLarge  —  fontSize=" .. compose.Theme.typography.bodyLarge.fontSize .. "sp", fontSize = 14 },
                    compose.Text { text = "bodyMedium —  fontSize=" .. compose.Theme.typography.bodyMedium.fontSize .. "sp", fontSize = 14 },
                    compose.Text { text = "titleLarge —  fontSize=" .. compose.Theme.typography.titleLarge.fontSize .. "sp", fontSize = 14 },
                    compose.Text { text = "labelSmall —  fontSize=" .. compose.Theme.typography.labelSmall.fontSize .. "sp", fontSize = 14 },
                    compose.Text { text = "📐 形状 (Shapes) — medium.topStart=" .. compose.Theme.shapes.medium.topStart .. "dp", fontSize = 14, modifier = compose.Modifier().paddingTop(8) },
                },
            },
            -- ================================================
            --  3. 布局与排列
            -- ================================================
            compose.Text {
                text = "3. 布局与排列",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            compose.Row {
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .backgroundRounded(compose.Theme.secondaryContainer, 10)
                    .padding(8),
                horizontalArrangement = "SpaceEvenly",
                children = {
                    compose.Text {
                        text = "A", fontSize = 14, fontWeight = "Bold",
                        modifier = compose.Modifier()
                            .backgroundRounded(compose.Theme.secondary, 6).paddingHv(12, 6),
                        color = compose.Theme.onSecondary,
                    },
                    compose.Text {
                        text = "B", fontSize = 14, fontWeight = "Bold",
                        modifier = compose.Modifier()
                            .backgroundRounded(compose.Theme.tertiary, 6).paddingHv(12, 6),
                        color = compose.Theme.onTertiary,
                    },
                    compose.Text {
                        text = "C", fontSize = 14, fontWeight = "Bold",
                        modifier = compose.Modifier()
                            .backgroundRounded(compose.Theme.error, 6).paddingHv(12, 6),
                        color = compose.Theme.onError,
                    },
                },
            },

            -- ================================================
            --  4. 状态管理
            -- ================================================
            compose.Text {
                text = "4. 状态管理",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },
            compose.Row {
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .backgroundRounded(compose.Theme.tertiaryContainer, 10)
                    .padding(12),
                horizontalArrangement = "SpaceEvenly",
                children = {
                    compose.Text {
                        text = "count = " .. tostring(count.value),
                        fontSize = 14, fontWeight = "Bold",
                        color = compose.Theme.onTertiaryContainer,
                    },
                    compose.Text {
                        text = "x² = " .. tostring(count.value * count.value),
                        fontSize = 14, fontWeight = "Bold",
                        color = compose.Theme.onTertiaryContainer,
                    },
                    compose.Text {
                        text = "x2 = " .. tostring(count.value * 2),
                        fontSize = 14, fontWeight = "Bold",
                        color = compose.Theme.onTertiaryContainer,
                    },
                },
            },

            -- ================================================
            --  5. Modifier 外观 + graphicsLayer
            -- ================================================
            compose.Text {
                text = "5. Modifier 外观",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            compose.Row {
                modifier = compose.Modifier().fillMaxWidth(),
                horizontalArrangement = "SpaceEvenly",
                children = {
                    compose.Text {
                        text = "圆角+阴影", fontSize = 12, color = compose.Theme.onSurface,
                        modifier = compose.Modifier()
                            .backgroundRounded(compose.Theme.surfaceVariant, 8)
                            .shadowRounded(4, 8).padding(10),
                    },
                    compose.Text {
                        text = "边框", fontSize = 12, color = compose.Theme.primary,
                        modifier = compose.Modifier()
                            .border(1.5, compose.Theme.primary)
                            .borderRadius(8).padding(10),
                    },
                    compose.Text {
                        text = "旋转", fontSize = 12, color = compose.Theme.onSecondaryContainer,
                        modifier = compose.Modifier()
                            .rotate(15)
                            .backgroundRounded(compose.Theme.secondaryContainer, 6).padding(10),
                    },
                    compose.Text {
                        text = "半透明", fontSize = 12, color = compose.Theme.onErrorContainer,
                        modifier = compose.Modifier()
                            .alpha(0.6)
                            .backgroundRounded(compose.Theme.errorContainer, 6).padding(10),
                    },
                },
            },

            -- graphicsLayer
            compose.Text {
                text = "graphicsLayer 变换（缩放1.2x + 旋转-5°）",
                fontSize = 13, color = compose.Theme.onSurface,
                modifier = compose.Modifier()
                    .paddingTop(8)
                    .graphicsLayer(1.2, 1.2, 1.0, -5)
                    .backgroundRounded(compose.Theme.primaryContainer, 6)
                    .paddingHv(12, 6),
            },

            -- ================================================
            --  6. BoxWithConstraints + constraints 对象
            -- ================================================
            compose.Text {
                text = "6. BoxWithConstraints 自适应布局",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            compose.Text {
                text = "子组件通过 node.props.constraints 访问约束信息。\n"
                    .. "下方区域有圆角背景和边框，方便观察布局范围。",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(8),
            },

            compose.BoxWithConstraints {
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(80)
                    .backgroundRounded(compose.Theme.primaryContainer, 12)
                    .border(2, compose.Theme.primary)
                    .borderRadius(12)
                    .padding(12),
                children = {
                    compose.Text {
                        text = "响应式布局区域\n自适应父容器宽度",
                        fontSize = 14,
                        fontWeight = "Bold",
                        color = compose.Theme.onPrimaryContainer,
                    },
                },
            },

            -- ================================================
            --  7. 动画：AnimatedVisibility / AnimatedContent / Crossfade / animateContentSize
            -- ================================================
            compose.Text {
                text = "7. 动画",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            -- AnimatedVisibility
            compose.Text {
                text = "AnimatedVisibility — 带进出场动画的显示/隐藏",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(4),
            },
            compose.Button {
                onClick = function() showCard.value = not showCard.value end,
                modifier = compose.Modifier().fillMaxWidth(),
                children = { compose.Text {
                    text = showCard.value and "隐藏卡片" or "显示卡片",
                    fontSize = 14,
                } },
            },
            compose.AnimatedVisibility {
                visible = showCard.value,
                enter = compose.fadeIn(),
                exit = compose.fadeOut(),
                children = {
                    compose.Card {
                        modifier = compose.Modifier().fillMaxWidth().paddingTop(8),
                        elevation = 4,
                        children = {
                            compose.Text {
                                text = "这是一张带淡入淡出动画的卡片",
                                fontSize = 14,
                                color = compose.Theme.onSurface,
                                modifier = compose.Modifier().padding(16),
                            },
                        },
                    },
                },
            },

            -- AnimatedContent
            compose.Text {
                text = "AnimatedContent — 内容滑动切换",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            compose.AnimatedContent {
                targetState = count.value,
                enter = compose.slideInHorizontally(),
                exit = compose.slideOutHorizontally(),
                durationMs = 300,
                children = {
                    compose.Text {
                        text = "当前值: " .. tostring(count.value),
                        fontSize = 28, fontWeight = "Bold",
                        color = compose.Theme.primary,
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .backgroundRounded(compose.Theme.primaryContainer, 10)
                            .padding(16),
                    },
                },
            },

            -- Crossfade
            compose.Text {
                text = "Crossfade — 淡入淡出切换",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            compose.Row {
                modifier = compose.Modifier().fillMaxWidth(),
                horizontalArrangement = "SpaceEvenly",
                children = {
                    compose.Button {
                        onClick = function() currentPage.value = 0 end,
                        modifier = compose.Modifier().weight(1).padding(2),
                        children = { compose.Text { text = "A", fontSize = 13 } },
                    },
                    compose.Button {
                        onClick = function() currentPage.value = 1 end,
                        modifier = compose.Modifier().weight(1).padding(2),
                        children = { compose.Text { text = "B", fontSize = 13 } },
                    },
                    compose.Button {
                        onClick = function() currentPage.value = 2 end,
                        modifier = compose.Modifier().weight(1).padding(2),
                        children = { compose.Text { text = "C", fontSize = 13 } },
                    },
                },
            },
            compose.Crossfade {
                targetState = currentPage.value,
                durationMs = 400,
                -- children = function() 表示动态子节点，每次 targetState 变化时重新调用
                -- 函数内部可以声明 local 变量，实现动态内容生成
                children = function()
                    local pageNames = {"页面 A — 红色", "页面 B — 绿色", "页面 C — 蓝色"}
                    local pageColors = {compose.Theme.errorContainer, compose.Theme.tertiaryContainer, compose.Theme.primaryContainer}
                    return compose.Text {
                        text = pageNames[currentPage.value + 1] or "未知",
                        fontSize = 18,
                        fontWeight = "Bold",
                        color = compose.Theme.onSurface,
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .backgroundRounded(pageColors[currentPage.value + 1] or compose.Theme.surfaceVariant, 10)
                            .padding(16),
                    }
                end,
            },

            -- animateContentSize
            compose.Text {
                text = "animateContentSize — 尺寸变化动画",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            compose.Text {
                text = count.value > 5
                    and "这是一段很长的文本，当 count 大于 5 时显示。"
                    or "短文本。",
                fontSize = 14,
                color = compose.Theme.onSurface,
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .backgroundRounded(compose.Theme.secondaryContainer, 8)
                    .animateContentSize()
                    .padding(12),
            },

            -- ================================================
            --  8. InfiniteTransition 无限循环动画
            -- ================================================
            compose.Text {
                text = "8. InfiniteTransition 无限循环动画",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            compose.InfiniteTransition {
                initialValue = 0.0,
                targetValue = 1.0,
                durationMs = 1000,
                children = function(animValue)
                    return compose.Row {
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .height(40)
                            .backgroundRounded(compose.Theme.surfaceVariant, 8),
                        children = {
                            compose.Text {
                                text = "循环进度: " .. string.format("%.2f", animValue),
                                fontSize = 14,
                                fontWeight = "Bold",
                                color = compose.Theme.primary,
                                modifier = compose.Modifier()
                                    .padding(12)
                                    .alpha(animValue),
                            },
                        },
                    }
                end,
            },

            -- ================================================
            --  9. LaunchedEffect + key
            -- ================================================
            compose.Text {
                text = "9. LaunchedEffect + key",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            compose.Text {
                text = "LaunchedEffect 在 Compose 生命周期中启动协程。\n"
                    .. "key 按 key 值分组复用组件。",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(8),
            },

            -- LaunchedEffect 演示：key 变化时执行
            compose.LaunchedEffect {
                key = count.value,
                block = function()
                    -- 协程中的操作（这里仅演示，实际可用于延迟操作等）
                end,
            },

            -- key 演示：按 key 分组复用
            compose.key {
                key = "demo_" .. tostring(count.value % 3),
                children = {
                    compose.Text {
                        text = "key = demo_" .. tostring(count.value % 3) .. " (count=" .. tostring(count.value) .. ")",
                        fontSize = 14,
                        fontWeight = "Bold",
                        color = compose.Theme.onSurface,
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .backgroundRounded(compose.Theme.surfaceVariant, 8)
                            .padding(12),
                    },
                },
            },

            -- ================================================
            --  10. Canvas 自定义绘图
            -- ================================================
            compose.Text {
                text = "10. Canvas 自定义绘图",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            compose.Canvas {
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(120)
                    .backgroundRounded(compose.Theme.surfaceVariant, 8),
                onDraw = function(draw)
                    draw.drawCircle(60, 60, 40, compose.Theme.primaryContainer)
                    draw.drawCircle(60, 60, 20, compose.Theme.primary)
                    draw.drawRect(120, 20, 170, 60, compose.Theme.secondaryContainer)
                    draw.drawRect(130, 30, 160, 50, compose.Theme.secondary)
                    draw.drawLine(200, 20, 260, 100, compose.Theme.tertiary, 3)
                    draw.drawLine(200, 100, 260, 20, compose.Theme.tertiary, 3)
                    draw.drawRoundRectStroke(280, 30, 350, 90, 12, compose.Theme.primary, 2)
                end,
            },

            -- ================================================
            --  11. 动态组件（Lazy Namespace）
            -- ================================================
            compose.Text {
                text = "11. 动态组件（Lazy Namespace）",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            compose.Text {
                text = "compose.material3.Button 等无需预注册，\n"
                    .. "自动通过反射加载对应的 Compose 组件。",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(8),
            },

            compose.material3.Button {
                onClick = function() count.value = count.value + 1 end,
                modifier = compose.Modifier().fillMaxWidth(),
                children = { compose.Text {
                    text = "动态 material3.Button (count=" .. tostring(count.value) .. ")",
                } },
            },

            -- ================================================
            --  12. 手势 + 协程
            -- ================================================
            compose.Text {
                text = "12. 手势与协程",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            compose.Row {
                modifier = compose.Modifier().fillMaxWidth(),
                horizontalArrangement = "SpaceEvenly",
                children = {
                    compose.Text {
                        text = "点击 +1",
                        fontSize = 14, fontWeight = "Bold",
                        color = compose.Theme.onPrimary,
                        modifier = compose.Modifier()
                            .backgroundRounded(compose.Theme.primary, 8)
                            .paddingHv(16, 10)
                            .onTap(function() count.value = count.value + 1 end),
                    },
                    compose.Text {
                        text = "长按清零",
                        fontSize = 14, fontWeight = "Bold",
                        color = compose.Theme.onSecondary,
                        modifier = compose.Modifier()
                            .backgroundRounded(compose.Theme.secondary, 8)
                            .paddingHv(16, 10)
                            .onLongPress(function() count.value = 0 end),
                    },
                    compose.Text {
                        text = "协程+1",
                        fontSize = 14, fontWeight = "Bold",
                        color = compose.Theme.onTertiary,
                        modifier = compose.Modifier()
                            .backgroundRounded(compose.Theme.tertiary, 8)
                            .paddingHv(16, 10)
                            .onTap(function()
                                scope.launch(function() count.value = count.value + 1 end)
                            end),
                    },
                },
            },

            -- ================================================
            --  9. Canvas 自定义绘制
            -- ================================================
            compose.Text {
                text = "9. Canvas 自定义绘制",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },
            compose.Text {
                text = "drawCircle / drawRect / drawLine / drawArc / rotate",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(8),
            },
            compose.Canvas {
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(140)
                    .backgroundRounded(compose.Theme.surfaceVariant, 8),
                onDraw = function(draw)
                    -- 背景圆
                    draw.drawCircle(60, 70, 40, compose.Theme.primaryContainer)
                    -- 描边圆
                    draw.drawCircleStroke(60, 70, 40, compose.Theme.primary, 2.0)
                    -- 矩形
                    draw.drawRect(120, 30, 200, 110, compose.Theme.tertiaryContainer)
                    -- 描边矩形
                    draw.drawRectStroke(120, 30, 200, 110, compose.Theme.tertiary, 1.5)
                    -- 直线
                    draw.drawLine(220, 20, 300, 120, compose.Theme.error, 2.0)
                    -- 弧线
                    draw.drawArcStroke(270, 30, 340, 110, 90, 270, compose.Theme.secondary, 2.0)
                end,
            },

            -- ================================================
            --  10. 拖拽手势 + 动态偏移
            -- ================================================
            compose.Text {
                text = "10. 拖拽手势 + 动态偏移",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },
            compose.Text {
                text = "pointerInputFull + offsetLambda，拖拽绿块移动",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(8),
            },

            compose.Box {
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(160)
                    .backgroundRounded(compose.Theme.surfaceVariant, 8),
                contentAlignment = "Center",
                children = {
                    compose.Text {
                        text = "拖我！",
                        textLambda = function()
                            return "拖我！(" .. string.format("%.0f", dragX.value) .. ", " .. string.format("%.0f", dragY.value) .. ")"
                        end,
                        fontSize = 14, fontWeight = "Bold",
                        color = compose.Theme.onPrimary,
                        modifier = compose.Modifier()
                            -- ★ offsetLambda 必须在 backgroundRounded 之前，
                            --   确保整个视觉元素（圆角背景+文字）一起位移
                            .offsetLambda(function()
                                return {x = dragX.value, y = dragY.value}
                            end)
                            .backgroundRounded(compose.Theme.primary, 8)
                            .padding(12)
                            .pointerInputFull(
                                -- onDragStart
                                function()
                                    -- 可以在这里记录起始位置
                                end,
                                -- onDrag: 接收 (dx, dy) 增量
                                function(dx, dy)
                                    dragX.value = dragX.value + dx
                                    dragY.value = dragY.value + dy
                                end,
                                -- onDragEnd
                                function()
                                    -- 松手后弹簧回弹到原点
                                    dragX.value = 0.0
                                    dragY.value = 0.0
                                end
                            ),
                    },
                },
            },

            -- ================================================
            --  11. 动画示例集锦（来自 LuaCompose-master）
            -- ================================================
            compose.Text {
                text = "11. 动画示例集锦",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16).paddingBottom(4),
            },

            -- 11a: animateDpAsState — 卡片展开/收缩
            compose.Text {
                text = "11a. animateDpAsState — 点击卡片展开/收缩",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(4),
            },
            compose.Card {
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(80 + expandAnim.value * 100)
                    .onTap(function()
                        expandAnim.setTarget(expandAnim.value > 0.5 and 0.0 or 1.0)
                    end),
                elevation = 4,
                children = {
                    compose.Column {
                        modifier = compose.Modifier().fillMaxWidth().padding(16),
                        children = {
                            compose.Row {
                                modifier = compose.Modifier().fillMaxWidth(),
                                children = {
                                    compose.Text {
                                        text = expandAnim.value > 0.5 and "点击收缩" or "点击展开",
                                        fontSize = 14, fontWeight = "Bold",
                                        color = compose.Theme.onSurface,
                                        modifier = compose.Modifier().weight(1),
                                    },
                                    compose.Text {
                                        text = expandAnim.value > 0.5 and "▲" or "▼",
                                        fontSize = 16, fontWeight = "Bold",
                                        color = compose.Theme.primary,
                                    },
                                },
                            },
                            compose.Text {
                                text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore.",
                                fontSize = 13,
                                color = compose.Theme.onSurfaceVariant,
                                modifier = compose.Modifier().paddingTop(8),
                            },
                        },
                    },
                },
            },

            -- 11b: animateColorAsState — 调色板颜色切换
            compose.Text {
                text = "11b. animateColorAsState — 调色板颜色渐进切换",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            -- 大色块：lerpColor 只插值端点颜色，不经过中间色
            -- colorAnim 从 prevColorIdx 动画到 selectedIndex，计算归一化进度
            compose.Box {
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(100)
                    .backgroundRounded(
                        (function()
                            local from = prevColorIdx.value
                            local to = selectedIndex.value
                            if from == to then return paletteColors[to] end
                            -- 归一化进度：从 from 到 to 的线性插值
                            local progress = (colorAnim.value - from) / (to - from)
                            progress = math.max(0, math.min(1, progress))
                            return lerpColor(paletteColors[from], paletteColors[to], progress)
                        end)(),
                        16
                    ),
                contentAlignment = "Center",
                children = {
                    compose.Text {
                        text = colorNames[selectedIndex.value] or "?",
                        fontSize = 28, fontWeight = "Bold",
                        color = compose.Theme.onPrimary,
                    },
                },
            },
            compose.Row {
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8),
                horizontalArrangement = "SpaceEvenly",
                children = {
                    compose.Box {
                        modifier = compose.Modifier()
                            .size(48, 48)
                            .clip(24)
                            .backgroundRounded(paletteColors[1], 24)
                            .onTap(function()
                                prevColorIdx.value = selectedIndex.value
                                selectedIndex.value = 1
                                colorAnim.setTarget(1.0)
                            end),
                    },
                    compose.Box {
                        modifier = compose.Modifier()
                            .size(48, 48)
                            .clip(24)
                            .backgroundRounded(paletteColors[2], 24)
                            .onTap(function()
                                prevColorIdx.value = selectedIndex.value
                                selectedIndex.value = 2
                                colorAnim.setTarget(2.0)
                            end),
                    },
                    compose.Box {
                        modifier = compose.Modifier()
                            .size(48, 48)
                            .clip(24)
                            .backgroundRounded(paletteColors[3], 24)
                            .onTap(function()
                                prevColorIdx.value = selectedIndex.value
                                selectedIndex.value = 3
                                colorAnim.setTarget(3.0)
                            end),
                    },
                    compose.Box {
                        modifier = compose.Modifier()
                            .size(48, 48)
                            .clip(24)
                            .backgroundRounded(paletteColors[4], 24)
                            .onTap(function()
                                prevColorIdx.value = selectedIndex.value
                                selectedIndex.value = 4
                                colorAnim.setTarget(4.0)
                            end),
                    },
                },
            },

            -- 11c: spring 弹簧动画 — FAB 变形
            compose.Text {
                text = "11c. spring 弹簧动画 — FAB 变形",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            compose.Box {
                modifier = compose.Modifier().fillMaxWidth().height(120),
                contentAlignment = "Center",
                children = {
                    compose.Text {
                        text = fabExpanded.value > 0.5 and "✕" or "+",
                        fontSize = 28, fontWeight = "Bold",
                        color = compose.Theme.onPrimary,
                        modifier = compose.Modifier()
                            .size(56 + fabExpanded.value * 34, 56 + fabExpanded.value * 34)
                            .clip(28 - fabExpanded.value * 4)
                            .backgroundRounded(
                                lerpColor(fabColorStart.toArgb(), fabColorEnd.toArgb(), fabExpanded.value),
                                28 - fabExpanded.value * 4
                            )
                            .rotate(fabExpanded.value * 45)
                            .onTap(function()
                                fabExpanded.setTarget(fabExpanded.value > 0.5 and 0.0 or 1.0)
                            end),
                    },
                },
            },

            -- 11d: InfiniteTransition — 心跳脉冲
            compose.Text {
                text = "11d. InfiniteTransition — 心跳脉冲",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            compose.InfiniteTransition {
                initialValue = 0.0,
                targetValue = 1.0,
                durationMs = 600,
                children = function(animValue)
                    -- animValue 在 0→1 之间循环，用于驱动 scale 和 alpha
                    local scale = 0.85 + animValue * 0.3
                    local alpha = 0.6 + animValue * 0.4
                    return compose.Box {
                        modifier = compose.Modifier().fillMaxWidth().height(140),
                        contentAlignment = "Center",
                        children = {
                            compose.Text {
                                text = "♥",
                                fontSize = 80,
                                color = compose.Theme.error,
                                modifier = compose.Modifier()
                                    .scale(scale, scale)
                                    .alpha(alpha),
                            },
                        },
                    }
                end,
            },

            -- 11e: Canvas 旋转加载器
            compose.Text {
                text = "11e. Canvas — 旋转加载 Spinner",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            -- 嵌套两个 InfiniteTransition：外层驱动旋转角度，内层驱动弧线扫角
            compose.InfiniteTransition {
                initialValue = 0.0,
                targetValue = 360.0,
                durationMs = 1400,
                children = function(rotation)
                    return compose.InfiniteTransition {
                        initialValue = 10.0,
                        targetValue = 290.0,
                        durationMs = 1200,
                        children = function(sweep)
                            return compose.Box {
                                modifier = compose.Modifier().fillMaxWidth().height(140),
                                contentAlignment = "Center",
                                children = {
                                    compose.Canvas {
                                        modifier = compose.Modifier()
                                            .size(120, 120)
                                            .rotate(rotation),
                                        onDraw = function(draw, w, h)
                                            local cx = w / 2
                                            local cy = h / 2
                                            local r = math.min(w, h) / 2 - 8
                                            local left = cx - r
                                            local top = cy - r
                                            -- 背景弧线（淡色）
                                            draw.drawArcStroke(left, top, left + r * 2, top + r * 2, 0, 360, compose.Theme.surfaceVariant, 8)
                                            -- 前景弧线（主色）
                                            draw.drawArcStroke(left, top, left + r * 2, top + r * 2, 0, sweep, compose.Theme.primary, 8)
                                        end,
                                    },
                                },
                            }
                        end,
                    }
                end,
            },

            -- 11f: 卡片 3D 翻转
            compose.Text {
                text = "11f. graphicsLayerRotationY — 3D 卡片翻转",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            compose.Box {
                modifier = compose.Modifier().fillMaxWidth().height(200),
                contentAlignment = "Center",
                children = {
                    compose.Card {
                        modifier = compose.Modifier()
                            .size(160, 200)
                            .graphicsLayerRotationY(flipRotation.value, 12 * density)
                            .onTap(function()
                                flipRotation.setTarget(flipRotation.value > 90 and 0.0 or 180.0)
                            end),
                        elevation = 6,
                        children = function()
                            if flipRotation.value <= 90 then
                                return compose.Column {
                                    horizontalAlignment = "CenterHorizontally",
                                    modifier = compose.Modifier().fillMaxSize(),
                                    children = {
                                        compose.Text { text = "♥", fontSize = 64, color = compose.Theme.onPrimary },
                                        compose.Text { text = "正面", fontSize = 18, fontWeight = "Bold", color = compose.Theme.onPrimary },
                                    },
                                }
                            else
                                return compose.Column {
                                    modifier = compose.Modifier().fillMaxSize().graphicsLayerRotationY(180, 0),
                                    horizontalAlignment = "CenterHorizontally",
                                    children = {
                                        compose.Text { text = "✦", fontSize = 64 },
                                        compose.Text { text = "反面!", fontSize = 18, fontWeight = "Bold", color = compose.Theme.onSurface },
                                    },
                                }
                            end
                        end,
                    },
                },
            },

            -- ================================================
            --  11g. Canvas Path 形变 — Play/Pause 图标
            -- ================================================
            compose.Text {
                text = "11g. Canvas Path 形变 — Play/Pause 图标",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            compose.Box {
                modifier = compose.Modifier().fillMaxWidth().height(160),
                contentAlignment = "Center",
                children = {
                    compose.Canvas {
                        modifier = compose.Modifier()
                            .size(100, 100)
                            .backgroundRounded(compose.Theme.primary, 50)
                            .onTap(function()
                                isPlaying.value = not isPlaying.value
                                morphProgress.setTarget(isPlaying.value and 1.0 or 0.0)
                            end),
                        onDraw = function(draw, w, h)
                            local t = morphProgress.value
                            local cx = w / 2
                            local cy = h / 2
                            local s = w * 0.28

                            -- 三角形→暂停竖条 的 lerp 插值
                            local function lerp(a, b, f)
                                return a + (b - a) * f
                            end

                            -- 左半边：三角形左边 → 左竖条
                            local leftPath = compose.Path()
                            leftPath.moveTo(lerp(cx - s * 0.55, cx - s * 0.5, t), lerp(cy - s, cy - s * 0.7, t))
                            leftPath.lineTo(lerp(cx + s, cx - s * 0.2, t), lerp(cy, cy - s * 0.7, t))
                            leftPath.lineTo(lerp(cx + s, cx - s * 0.2, t), lerp(cy, cy + s * 0.7, t))
                            leftPath.lineTo(lerp(cx - s * 0.55, cx - s * 0.5, t), lerp(cy + s, cy + s * 0.7, t))
                            leftPath.close()

                            -- 右半边：三角形右边 → 右竖条
                            local rightPath = compose.Path()
                            rightPath.moveTo(lerp(cx + s, cx + s * 0.2, t), lerp(cy, cy - s * 0.7, t))
                            rightPath.lineTo(lerp(cx + s, cx + s * 0.5, t), lerp(cy, cy - s * 0.7, t))
                            rightPath.lineTo(lerp(cx + s, cx + s * 0.5, t), lerp(cy, cy + s * 0.7, t))
                            rightPath.lineTo(lerp(cx + s, cx + s * 0.2, t), lerp(cy, cy + s * 0.7, t))
                            rightPath.close()

                            draw.drawPath(leftPath, compose.Theme.onPrimary)
                            draw.drawPath(rightPath, compose.Theme.onPrimary)
                        end,
                    },
                },
            },
            compose.Text {
                text = isPlaying.value and "状态: 播放中 ▶" or "状态: 暂停 ⏸",
                fontSize = 14,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(4),
            },

            -- ================================================
            --  11h. 径向 FAB 菜单
            -- ================================================
            compose.Text {
                text = "11h. 径向 FAB 菜单 — 卫星按钮散射",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            compose.Box {
                modifier = compose.Modifier().fillMaxWidth().height(260),
                contentAlignment = "Center",
                children = {
                    -- 卫星按钮 1 (左上)
                    compose.Text {
                        text = "★",
                        fontSize = 22,
                        fontWeight = "Bold",
                        color = compose.Theme.onPrimary,
                        modifier = compose.Modifier()
                            .size(48, 48)
                            .clipCircle()
                            .backgroundRounded(0xFFEF5350, 24)
                            .graphicsLayer(sat1.value, sat1.value, sat1.value, 0)
                            .offsetLambda(function()
                                local a = sat1.value
                                return {x = -100 * a, y = -100 * a}
                            end),
                    },
                    -- 卫星按钮 2 (右上)
                    compose.Text {
                        text = "♥",
                        fontSize = 22,
                        fontWeight = "Bold",
                        color = compose.Theme.onPrimary,
                        modifier = compose.Modifier()
                            .size(48, 48)
                            .clipCircle()
                            .backgroundRounded(0xFF42A5F5, 24)
                            .graphicsLayer(sat2.value, sat2.value, sat2.value, 0)
                            .offsetLambda(function()
                                local a = sat2.value
                                return {x = 100 * a, y = -100 * a}
                            end),
                    },
                    -- 卫星按钮 3 (左下)
                    compose.Text {
                        text = "✦",
                        fontSize = 22,
                        fontWeight = "Bold",
                        color = compose.Theme.onPrimary,
                        modifier = compose.Modifier()
                            .size(48, 48)
                            .clipCircle()
                            .backgroundRounded(0xFF66BB6A, 24)
                            .graphicsLayer(sat3.value, sat3.value, sat3.value, 0)
                            .offsetLambda(function()
                                local a = sat3.value
                                return {x = -100 * a, y = 100 * a}
                            end),
                    },
                    -- 卫星按钮 4 (右下)
                    compose.Text {
                        text = "✿",
                        fontSize = 22,
                        fontWeight = "Bold",
                        color = compose.Theme.onPrimary,
                        modifier = compose.Modifier()
                            .size(48, 48)
                            .clipCircle()
                            .backgroundRounded(0xFFFFCA28, 24)
                            .graphicsLayer(sat4.value, sat4.value, sat4.value, 0)
                            .offsetLambda(function()
                                local a = sat4.value
                                return {x = 100 * a, y = 100 * a}
                            end),
                    },
                    -- 中心 FAB
                    compose.Text {
                        text = "+",
                        fontSize = 32,
                        fontWeight = "Bold",
                        color = compose.Theme.onPrimary,
                        modifier = compose.Modifier()
                            .size(64, 64)
                            .clipCircle()
                            .backgroundRounded(compose.Theme.primary, 32)
                            .rotate(sat1.value * 45)
                            .onTap(function()
                                radialOpen.value = not radialOpen.value
                                local target = radialOpen.value and 1.0 or 0.0
                                sat1.setTarget(target)
                                sat2.setTarget(target)
                                sat3.setTarget(target)
                                sat4.setTarget(target)
                            end),
                    },
                },
            },

            -- ================================================
            --  11i. 卡片滑动删除
            -- ================================================
            compose.Text {
                text = "11i. 卡片滑动删除 — 左右滑动移除",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            compose.Box {
                modifier = compose.Modifier().fillMaxWidth().height(200),
                contentAlignment = "Center",
                children = {
                    -- ★ 用 AnimatedContent 包裹整个卡片栈，切换时平滑过渡
                    compose.AnimatedContent {
                        targetState = swipeTopIdx.value,
                        transitionSpec = function(initialState, targetState)
                            local dir = targetState > initialState and "up" or "down"
                            return dir
                        end,
                        children = function(target)
                            return compose.Box {
                                modifier = compose.Modifier().fillMaxWidth().height(200),
                                contentAlignment = "Center",
                                children = {
                                    -- 底层卡片 2（最深）
                                    compose.Card {
                                        color = swipeColors[target + 3] or 0xFFBDBDBD,
                                        modifier = compose.Modifier()
                                            .fillMaxWidth()
                                            .paddingHorizontal(20)
                                            .height(160)
                                            .offsetLambda(function() return {x = 0, y = 20} end)
                                            .graphicsLayer(0.88, 0.88, 1.0, 0),
                                        elevation = 0,
                                        children = {
                                            compose.Box {
                                                modifier = compose.Modifier().fillMaxWidth().height(160),
                                                contentAlignment = "Center",
                                                children = {
                                                    compose.Text {
                                                        text = "卡片 " .. tostring(target + 2),
                                                        fontSize = 24, fontWeight = "Bold",
                                                        color = 0xFFFFFFFF,
                                                    },
                                                },
                                            },
                                        },
                                    },
                                    -- 底层卡片 1
                                    compose.Card {
                                        color = swipeColors[target + 2] or 0xFFBDBDBD,
                                        modifier = compose.Modifier()
                                            .fillMaxWidth()
                                            .paddingHorizontal(20)
                                            .height(160)
                                            .offsetLambda(function() return {x = 0, y = 10} end)
                                            .graphicsLayer(0.94, 0.94, 1.0, 0),
                                        elevation = 1,
                                        children = {
                                            compose.Box {
                                                modifier = compose.Modifier().fillMaxWidth().height(160),
                                                contentAlignment = "Center",
                                                children = {
                                                    compose.Text {
                                                        text = "卡片 " .. tostring(target + 1),
                                                        fontSize = 24, fontWeight = "Bold",
                                                        color = 0xFFFFFFFF,
                                                    },
                                                },
                                            },
                                        },
                                    },
                                    -- 顶层卡片（可滑动）
                                    compose.Card {
                                        color = swipeColors[target + 1] or swipeColors[1],
                                        modifier = compose.Modifier()
                                            .fillMaxWidth()
                                            .paddingHorizontal(20)
                                            .height(160)
                                            .offsetLambda(function()
                                                return {x = swipeDragX.value, y = 0}
                                            end)
                                            .rotateLambda(function()
                                                return swipeDragX.value * 0.05
                                            end)
                                            .pointerInputFull(
                                                function() end,
                                                function(dx, dy)
                                                    swipeDragX.value = swipeDragX.value + dx
                                                end,
                                                function()
                                                    if math.abs(swipeDragX.value) > 150 then
                                                        swipeTopIdx.value = swipeTopIdx.value + 1
                                                    end
                                                    swipeDragX.value = 0.0
                                                end
                                            ),
                                        elevation = 4,
                                        children = {
                                            compose.Box {
                                                modifier = compose.Modifier().fillMaxWidth().height(160),
                                                contentAlignment = "Center",
                                                children = {
                                                    compose.Text {
                                                        text = "卡片 " .. tostring(target + 1) .. " ← 滑动 →",
                                                        fontSize = 20,
                                                        fontWeight = "Bold",
                                                        color = 0xFFFFFFFF,
                                                    },
                                                },
                                            },
                                        },
                                    },
                                },
                            }
                        end,
                    },
                },
            },

            -- 11j: 径向 FAB 菜单
            compose.Text {
                text = "11j. 径向 FAB 菜单",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8).paddingBottom(4),
            },
            compose.Box {
                modifier = compose.Modifier().fillMaxWidth().height(280),
                contentAlignment = "Center",
                children = {
                    -- 卫星按钮点击提示
                    compose.Text {
                        text = fabToast.value,
                        fontSize = 12,
                        color = compose.Theme.primary,
                        modifier = compose.Modifier().paddingTop(4),
                    },
                    -- 中心按钮
                    compose.Box {
                        modifier = compose.Modifier().fillMaxSize(),
                        contentAlignment = "Center",
                        children = {
                            compose.Box {
                                modifier = compose.Modifier()
                                    .size(64, 64)
                                    .clipCircle()
                                    .backgroundRounded(compose.Theme.primary, 32)
                                    .clickableLua(function()
                                        local open = not fabOpen.value
                                        fabOpen.value = open
                                        fabToast.value = ""
                                        local target = open and 1.0 or 0.0
                                        for j = 1, fabCount do
                                            fabAnims[j].setTarget(target)
                                        end
                                        fabRotation.setTarget(open and 45.0 or 0.0)
                                    end),
                                contentAlignment = "Center",
                                children = {
                                    compose.Text {
                                        text = "+", fontSize = 32, fontWeight = "Bold",
                                        color = compose.Theme.onPrimary,
                                        modifier = compose.Modifier().rotate(fabRotation.value),
                                    },
                                },
                            },
                        },
                    },
                    -- 卫星按钮（叠加在中心上方）
                    compose.Box {
                        modifier = compose.Modifier().fillMaxSize(),
                        contentAlignment = "Center",
                        children = fabSatellites,
                    },
                },
            },

            -- ================================================
            --  底部间距
            -- ================================================
            compose.Spacer { modifier = compose.Modifier().height(24) },
        },
    }
end

compose.render(main)