-- ============================================================
-- Nirithy LuaCompose 官方教程 06 — 动画
-- ============================================================
-- 目标：学会 AnimatedVisibility、animateFloatAsState、Crossfade 动画
-- 覆盖：AnimatedVisibility, animateFloatAsState, Crossfade, Animatable
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- ⚠️ 所有需触发 UI 更新的状态必须使用 state()，mutableState 变更不会触发 recomposition
-- 结构性变化（可见性切换）使用 state() 触发全量刷新
local isVisible = compose.state(true)
local isCrossfade = compose.state(false)
-- 动画目标值使用 state()，点击按钮后全量刷新节点树，animateFloatAsState 读取新目标值
local animTarget = compose.state(0.0)

compose.render(function()
    -- ⚠️ animateFloatAsState 必须在 render 内部调用（Composable 上下文）
    -- 返回的是 AnimatedFloat 对象，用 .value 读取当前数值
    local animatedValue = compose.animateFloatAsState(animTarget.value)

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24)
            .verticalScroll(),

        children = {

            compose.Text({
                text = "动画示例",
                fontSize = 22,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- ===== 1. AnimatedVisibility 显示/隐藏动画 =====
            compose.Text({ text = "1. AnimatedVisibility 显示/隐藏:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.AnimatedVisibility({
                -- ⚠️ visible 使用 state() 的值，点击按钮后全量刷新节点树
                visible = isVisible.value,
                children = {
                    compose.Card({
                        modifier = compose.Modifier().fillMaxWidth(),
                        color = 0xFFE8DEF8,
                        children = {
                            compose.Text({
                                text = "这是一个可以动画显示/隐藏的卡片！\n点击下方按钮切换可见性。",
                                fontSize = 14,
                                modifier = compose.Modifier().padding(16),
                            }),
                        },
                    }),
                },
            }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Button({
                -- ⚠️ 按钮文字在节点树重建时会更新（因为 isVisible 是 state()）
                text = isVisible.value and "隐藏" or "显示",
                onClick = function()
                    isVisible.value = not isVisible.value
                end,
                modifier = compose.Modifier().fillMaxWidth(),
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 2. animateFloatAsState 数值动画 =====
            compose.Text({ text = "2. animateFloatAsState 数值动画:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Text({
                text = "目标值: " .. math.floor(animTarget.value),
                fontSize = 14,
                color = 0xFF666666,
            }),
            -- ⚠️ 使用 textLambda 读取 animatedValue.value（AnimatedFloat 对象）
            compose.Text({
                textLambda = function()
                    return "动画值: " .. string.format("%.1f", animatedValue.value)
                end,
                fontSize = 20,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            -- 动画进度条（Canvas 绘制，不启用 continuousRedraw 避免持续重绘）
            -- state() 触发全量刷新时 Canvas 会重绘，显示当前动画值
            compose.Canvas({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(8)
                    .background(0xFFE0E0E0)
                    .borderRadius(4),
                onDraw = function(draw, w, h, timeSec)
                    -- 读取动画值并绘制进度
                    local progress = animatedValue.value / 100
                    local barW = w * progress
                    if barW > 0 then
                        draw.drawRect(0, 0, barW, h, 0xFF6750A4)
                    end
                end,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Button({
                        text = "0",
                        onClick = function() animTarget.value = 0 end,
                        modifier = compose.Modifier().weight(1),
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(4) }),
                    compose.Button({
                        text = "50",
                        onClick = function() animTarget.value = 50 end,
                        modifier = compose.Modifier().weight(1),
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(4) }),
                    compose.Button({
                        text = "100",
                        onClick = function() animTarget.value = 100 end,
                        modifier = compose.Modifier().weight(1),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 3. Crossfade 交叉淡入淡出 =====
            compose.Text({ text = "3. Crossfade 交叉淡入淡出:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Crossfade({
                -- ⚠️ targetState 使用 state() 的值，切换时全量刷新
                targetState = isCrossfade.value,
                children = {
                    compose.Card({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .height(80),
                        color = isCrossfade.value and 0xFF4CAF50 or 0xFF2196F3,
                        children = {
                            compose.Box({
                                modifier = compose.Modifier()
                                    .fillMaxSize()
                                    .padding(16),
                                children = {
                                    compose.Text({
                                        text = isCrossfade.value and "状态 B (绿色)" or "状态 A (蓝色)",
                                        fontSize = 18,
                                        fontWeight = 700,
                                        color = 0xFFFFFFFF,
                                    }),
                                },
                            }),
                        },
                    }),
                },
            }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Button({
                text = "切换状态",
                onClick = function()
                    isCrossfade.value = not isCrossfade.value
                end,
                modifier = compose.Modifier().fillMaxWidth(),
            }),

            -- 底部留白
            compose.Spacer({ modifier = compose.Modifier().height(32) }),
        },
    })
end)