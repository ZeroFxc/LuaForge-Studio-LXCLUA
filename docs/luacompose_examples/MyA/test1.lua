-- ============================================================
--  animation_examples_extra.lua
--  LuaCompose-master 动画示例移植（test_new_features 未覆盖的部分）
--  nirithy luacompose 适配版
-- ============================================================

local function main()
    -- ================================================
    --  12a. AnimatedVisibility — 进出场动画演示
    -- ================================================
    local visVisible = compose.state(true)

    -- ================================================
    --  12b. AnimatedContent — 计数器滑动切换
    -- ================================================
    local acCount = compose.state(0)
    local acPrev = compose.state(0)  -- 记录上一次的值，用于判断方向

    -- ================================================
    --  12c. Spring Drag Box — 自由拖拽（绿块/蓝块）+ 边界限制（红块）
    -- ================================================
    local dragX = compose.mutableState(0.0)
    local dragY = compose.mutableState(0.0)
    local drag2X = compose.mutableState(0.0)
    local drag2Y = compose.mutableState(0.0)
    local drag3X = compose.mutableState(0.0)
    local drag3Y = compose.mutableState(0.0)
    -- ★ 红块父容器实际尺寸（像素），由 onSizeChanged 动态获取
    local redContainerW = compose.mutableState(0.0)
    local redContainerH = compose.mutableState(0.0)

    -- ================================================
    --  12d. 摆锤波动画 — Canvas 物理模拟 (18个摆锤)
    -- ================================================
    return compose.Column {
        modifier = compose.Modifier()
            .fillMaxWidth()
            .verticalScroll()
            .padding(16),
        horizontalAlignment = "CenterHorizontally",
        children = {

            -- ================================================
            --  12a. AnimatedVisibility
            -- ================================================
            compose.Text {
                text = "12a. AnimatedVisibility 进出场",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8),
            },
            compose.Text {
                text = "点击按钮切换可见性，带默认动画",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(8),
            },
            compose.Button {
                onClick = function()
                    visVisible.value = not visVisible.value
                end,
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Text {
                        text = visVisible.value and "隐藏" or "显示",
                        fontSize = 14,
                    },
                },
            },
            compose.AnimatedVisibility {
                visible = visVisible.value,
                children = {
                    compose.Box {
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .height(100)
                            .backgroundRounded(compose.Theme.primaryContainer, 12),
                        contentAlignment = "Center",
                        children = {
                            compose.Text {
                                text = "我是进出场动画",
                                fontSize = 18,
                                fontWeight = "Bold",
                                color = compose.Theme.onPrimaryContainer,
                            },
                        },
                    },
                },
            },

            -- ================================================
            --  12b. AnimatedContent 计数器
            -- ================================================
            compose.Text {
                text = "12b. AnimatedContent 计数器",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16),
            },
            compose.Text {
                text = "数字上下滑动切换",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(8),
            },
            compose.Box {
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(160),
                contentAlignment = "Center",
                children = {
                    compose.AnimatedContent {
                        targetState = acCount.value,
                        transitionSpec = function(initialState, targetState)
                            -- 根据数值大小判断方向：增加→向上滑入，减少→向下滑入
                            local prev = acPrev.value
                            local curr = targetState
                            if curr > prev then return "up"
                            elseif curr < prev then return "down"
                            else return "up"
                            end
                        end,
                        children = function(target)
                            -- ★ target 是 AnimatedContent 传入的目标状态值
                            --   旧内容用旧值，新内容用新值，不会两边都显示相同数字
                            return compose.Text {
                                text = tostring(target),
                                fontSize = 96,
                                fontWeight = "Bold",
                                color = compose.Theme.primary,
                            }
                        end,
                    },
                },
            },
            compose.Row {
                modifier = compose.Modifier().fillMaxWidth().paddingHorizontal(40),
                horizontalArrangement = "SpaceEvenly",
                children = {
                    compose.Button {
                        onClick = function()
                            acPrev.value = acCount.value
                            acCount.value = acCount.value - 1
                        end,
                        children = {
                            compose.Text { text = "−", fontSize = 24 },
                        },
                    },
                    compose.Button {
                        onClick = function()
                            acPrev.value = acCount.value
                            acCount.value = acCount.value + 1
                        end,
                        children = {
                            compose.Text { text = "+", fontSize = 24 },
                        },
                    },
                },
            },

            -- ================================================
            --  12c. Spring Drag Box
            -- ================================================
            compose.Text {
                text = "12c. Spring Drag Box",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16),
            },
            compose.Text {
                text = "绿块自由 | 红块限界(窄容器) | 蓝块自由(宽容器)",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(8),
            },
            -- 第一行：绿块（自由）+ 红块（窄容器限界）
            compose.Row {
                modifier = compose.Modifier().fillMaxWidth(),
                horizontalArrangement = "SpaceEvenly",
                children = {
                    -- 绿块：自由拖拽，不受边界限制
                    compose.Box {
                        modifier = compose.Modifier()
                            .weight(1.0)
                            .height(260)
                            .padding(4)
                            .backgroundRounded(compose.Theme.surfaceVariant, 8),
                        contentAlignment = "Center",
                        children = {
                            compose.Text {
                                text = "自由",
                                fontSize = 11,
                                color = compose.Theme.onSurfaceVariant,
                                modifier = compose.Modifier().paddingTop(4),
                            },
                            compose.Box {
                                modifier = compose.Modifier()
                                    .offsetLambda(function()
                                        return {x = dragX.value, y = dragY.value}
                                    end)
                                    .size(80, 80)
                                    .backgroundRounded(0xFF26A69A, 16)
                                    .pointerInputFull(
                                        function() end,
                                        function(dx, dy)
                                            dragX.value = dragX.value + dx
                                            dragY.value = dragY.value + dy
                                        end,
                                        function()
                                            dragX.value = 0.0
                                            dragY.value = 0.0
                                        end
                                    ),
                            },
                        },
                    },
                    -- 红块：窄容器内限界拖拽（weight(1.0)，动态边界）
                    compose.Box {
                        modifier = compose.Modifier()
                            .weight(1.0)
                            .height(260)
                            .padding(4)
                            .backgroundRounded(compose.Theme.surfaceVariant, 8)
                            .onSizeChanged(function(w, h)
                                redContainerW.value = w
                                redContainerH.value = h
                            end),
                        contentAlignment = "Center",
                        children = {
                            compose.Text {
                                text = "窄限界",
                                fontSize = 11,
                                color = compose.Theme.onSurfaceVariant,
                                modifier = compose.Modifier().paddingTop(4),
                            },
                            compose.Box {
                                modifier = compose.Modifier()
                                    .offsetLambda(function()
                                        return {x = drag2X.value, y = drag2Y.value}
                                    end)
                                    .size(80, 80)
                                    .backgroundRounded(0xFFEF5350, 16)
                                    .pointerInputFull(
                                        function() end,
                                        function(dx, dy)
                                            -- ★ 动态计算边界：用父容器实际像素尺寸
                                            local density = compose.LocalDensity.density
                                            local blockPx = 80 * density
                                            local maxDx = math.max(0, (redContainerW.value - blockPx) / 2)
                                            local maxDy = math.max(0, (redContainerH.value - blockPx) / 2)
                                            local nx = drag2X.value + dx
                                            local ny = drag2Y.value + dy
                                            drag2X.value = math.max(-maxDx, math.min(maxDx, nx))
                                            drag2Y.value = math.max(-maxDy, math.min(maxDy, ny))
                                        end,
                                        function()
                                            drag2X.value = 0.0
                                            drag2Y.value = 0.0
                                        end
                                    ),
                            },
                        },
                    },
                },
            },
            -- 第二行：蓝块（宽容器，自由拖拽）
            compose.Box {
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(200)
                    .paddingTop(8)
                    .backgroundRounded(compose.Theme.surfaceVariant, 8),
                contentAlignment = "Center",
                children = {
                    compose.Text {
                        text = "自由",
                        fontSize = 11,
                        color = compose.Theme.onSurfaceVariant,
                        modifier = compose.Modifier().paddingTop(4),
                    },
                    compose.Box {
                        modifier = compose.Modifier()
                            .offsetLambda(function()
                                return {x = drag3X.value, y = drag3Y.value}
                            end)
                            .size(80, 80)
                            .backgroundRounded(0xFF42A5F5, 16)
                            .pointerInputFull(
                                function() end,
                                function(dx, dy)
                                    drag3X.value = drag3X.value + dx
                                    drag3Y.value = drag3Y.value + dy
                                end,
                                function()
                                    drag3X.value = 0.0
                                    drag3Y.value = 0.0
                                end
                            ),
                    },
                },
            },

            -- ================================================
            --  12d. 摆锤波动画
            -- ================================================
            compose.Text {
                text = "12d. 摆锤波动画",
                fontSize = 16,
                fontWeight = "Bold",
                color = compose.Theme.onSurface,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(16),
            },
            compose.Text {
                text = "18个摆锤不同频率，每秒同步一次",
                fontSize = 13,
                color = compose.Theme.onSurfaceVariant,
                modifier = compose.Modifier().fillMaxWidth().paddingBottom(8),
            },
            -- ★ 使用 continuousRedraw 让 Canvas 每帧重绘
            --   Kotlin 传入高精度帧时间 timeSec，替代不可靠的 os.clock()
            compose.Canvas {
                continuousRedraw = true,
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(380),
                onDraw = function(draw, w, h, timeSec)
                    local count = 18
                    local hp = w * 0.06
                    local usable = w - hp * 2
                    local pivotY = h * 0.05
                    local len = h * 0.78
                    local bobR = 11
                    local maxAngle = 32 * math.pi / 180
                    local base = 20
                    local period = 1
                    -- ★ 使用 Kotlin 传入的高精度帧时间（秒），每帧不同 → 摆锤持续摆动
                    local t = timeSec or 0

                    -- 背景
                    draw.drawRect(0, 0, w, h, 0xFF101015)
                    -- 支撑杆
                    draw.drawLine(hp * 0.5, pivotY, w - hp * 0.5, pivotY, 0xFF888892, 2.5)

                    for i = 0, count - 1 do
                        local px = hp + i * (usable / (count - 1))
                        local osc = base + i
                        local omega = 2 * math.pi * osc / period
                        local theta = maxAngle * math.cos(omega * t)
                        local bx = px + len * math.sin(theta)
                        local by = pivotY + len * math.cos(theta)

                        -- 渐变色
                        local frac = i / (count - 1)
                        local r = math.floor(0xFF + (0xE0 - 0xFF) * frac)
                        local g = math.floor(0xD7 + (0x40 - 0xD7) * frac)
                        local b = math.floor(0x40 + (0xFB - 0x40) * frac)
                        local col = 0xFF000000 + r * 65536 + g * 256 + b

                        -- 摆线
                        draw.drawLine(px, pivotY, bx, by, 0xFF555560, 1.5)
                        -- 阴影
                        draw.drawCircle(bx + bobR * 0.18, by + bobR * 0.22, bobR * 1.05, 0x73000000)
                        -- 主体
                        draw.drawCircle(bx, by, bobR, col)
                        -- 高光
                        draw.drawCircle(bx - bobR * 0.32, by - bobR * 0.34, bobR * 0.42, 0xD9FFFFFF)
                        draw.drawCircle(bx - bobR * 0.38, by - bobR * 0.40, bobR * 0.18, 0xD9FFFFFF)
                        -- 枢轴点
                        draw.drawCircle(px, pivotY, bobR * 0.18, col)
                    end
                end,
            },

            -- ================================================
            --  底部间距
            -- ================================================
            compose.Spacer { modifier = compose.Modifier().height(32) },
        },
    }
end

compose.render(main)