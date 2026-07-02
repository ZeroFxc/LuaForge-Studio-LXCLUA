-- ============================================================
-- Nirithy LuaCompose 官方教程 09 — Canvas 绘图
-- ============================================================
-- 目标：学会 Canvas 组件、onDraw 回调、DrawScope API
-- 覆盖：Canvas, onDraw, drawCircle, drawRect, drawLine, drawPath
--       continuousRedraw 持续重绘模式
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- ⚠️ 所有需触发 UI 更新的状态必须使用 state()，mutableState 变更不会触发 recomposition
local colorIndex = compose.state(0)
local colors = { 0xFF6750A4, 0xFF2196F3, 0xFF4CAF50, 0xFFFF9800, 0xFFE91E63 }

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24)
            .verticalScroll(),

        children = {

            compose.Text({
                text = "Canvas 绘图示例",
                fontSize = 22,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- ===== 1. 基础图形绘制 =====
            compose.Text({ text = "1. 基础图形绘制:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Canvas({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(180)
                    .background(0xFFF5F5F5)
                    .borderRadius(8),
                onDraw = function(draw, w, h, timeSec)
                    -- 圆形
                    draw.drawCircle(60, 50, 35, 0xFF6750A4)
                    -- 矩形
                    draw.drawRect(110, 15, 180, 85, 0xFF2196F3)
                    -- 圆角矩形
                    draw.drawRoundRect(200, 15, 280, 85, 16, 0xFFFF9800)
                    -- 线条
                    draw.drawLine(20, 110, 140, 160, 0xFF4CAF50, 4)
                    draw.drawLine(140, 110, 20, 160, 0xFFE91E63, 4)
                    -- 文字标签
                    draw.drawText("基础图形", 20, 170, 0xFF333333, 14)
                end,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 2. 持续重绘动画 =====
            compose.Text({ text = "2. 持续重绘动画 (continuousRedraw):", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Canvas({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(200)
                    .background(0xFF1A1A2E)
                    .borderRadius(8),
                continuousRedraw = true,
                onDraw = function(draw, w, h, timeSec)
                    local cx = w / 2
                    local cy = h / 2

                    -- 旋转的点阵
                    for i = 0, 7 do
                        local angle = timeSec * 2 + i * math.pi / 4
                        local x = cx + math.cos(angle) * 60
                        local y = cy + math.sin(angle) * 60
                        local size = 8 + math.sin(timeSec * 3 + i) * 4
                        draw.drawCircle(x, y, size, 0xFF6750A4)
                    end

                    -- 中心脉冲圆
                    local pulseSize = 25 + math.sin(timeSec * 2) * 12
                    draw.drawCircle(cx, cy, pulseSize, 0x80FFFFFF)

                    draw.drawText("连续动画 (timeSec=" .. string.format("%.1f", timeSec) .. ")", 10, 20, 0xFFFFFFFF, 12)
                end,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 3. 交互式绘图 =====
            compose.Text({ text = "3. 颜色切换:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Canvas({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(150)
                    .background(0xFFF5F5F5)
                    .borderRadius(8),
                onDraw = function(draw, w, h, timeSec)
                    local c = colors[colorIndex.value + 1] or 0xFF6750A4

                    -- 渐变色块
                    local blockW = w / 5
                    for i = 0, 4 do
                        local alpha = 0xFF - i * 0x30
                        draw.drawRect(
                            i * blockW, 0,
                            (i + 1) * blockW, h,
                            (c & 0x00FFFFFF) | (alpha << 24)
                        )
                    end

                    draw.drawText("颜色索引: " .. colorIndex.value, 10, 30, 0xFF333333, 14)
                end,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Button({
                        text = "上一颜色",
                        onClick = function()
                            colorIndex.value = (colorIndex.value - 1) % #colors
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(4) }),
                    compose.Button({
                        text = "下一颜色",
                        onClick = function()
                            colorIndex.value = (colorIndex.value + 1) % #colors
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 4. 路径绘制 =====
            compose.Text({ text = "4. 路径绘制:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            -- 贝塞尔曲线
            compose.Text({ text = "三次贝塞尔曲线:", fontSize = 13, color = 0xFF666666 }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Canvas({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(160)
                    .background(0xFFF5F5F5)
                    .borderRadius(8),
                onDraw = function(draw, w, h, timeSec)
                    -- 控制点参考线（虚线效果）
                    draw.drawCircle(20, h / 2, 4, 0xFFE91E63)
                    draw.drawCircle(w / 4, 20, 4, 0xFF666666)
                    draw.drawCircle(w * 3 / 4, h - 20, 4, 0xFF666666)
                    draw.drawCircle(w - 20, h / 2, 4, 0xFFE91E63)

                    -- 三次贝塞尔曲线
                    local path = compose.Path()
                    path.moveTo(20, h / 2)
                    path.cubicTo(w / 4, 20, w * 3 / 4, h - 20, w - 20, h / 2)
                    draw.drawPathStroke(path, 0xFFE91E63, 3)

                    draw.drawText("贝塞尔曲线", 10, 150, 0xFF333333, 12)
                end,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(12) }),

            -- 折线
            compose.Text({ text = "折线 (Polyline):", fontSize = 13, color = 0xFF666666 }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Canvas({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(120)
                    .background(0xFFF5F5F5)
                    .borderRadius(8),
                onDraw = function(draw, w, h, timeSec)
                    local path = compose.Path()
                    path.moveTo(20, 30)
                    path.lineTo(w / 4, 80)
                    path.lineTo(w / 2, 20)
                    path.lineTo(w * 3 / 4, 90)
                    path.lineTo(w - 20, 50)
                    draw.drawPathStroke(path, 0xFF2196F3, 2)

                    draw.drawText("折线", 10, 110, 0xFF333333, 12)
                end,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(12) }),

            -- 填充路径
            compose.Text({ text = "填充路径 (Fill):", fontSize = 13, color = 0xFF666666 }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Canvas({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(120)
                    .background(0xFFF5F5F5)
                    .borderRadius(8),
                onDraw = function(draw, w, h, timeSec)
                    local path = compose.Path()
                    path.moveTo(w / 2, 20)
                    path.lineTo(w - 20, h - 20)
                    path.lineTo(20, h - 20)
                    path.close()
                    draw.drawPath(path, 0x806750A4)

                    draw.drawText("填充三角形", 10, 110, 0xFF333333, 12)
                end,
            }),

            -- 底部留白
            compose.Spacer({ modifier = compose.Modifier().height(32) }),
        },
    })
end)