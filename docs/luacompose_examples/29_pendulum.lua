-- 29_pendulum.lua — AnimationExample20: 摆锤波浪
-- 1:1 复刻 Kotlin 原版，多摆锤波浪 + 拖尾渐变
local compose = _G.compose

local PENDULUM_COUNT = 15
local PENDULUM_LENGTH_BASE = 200
local PENDULUM_LENGTH_STEP = 12
local AMPLITUDE_FACTOR = 0.9
local FREQ_BASE = 1.2
local TRAIL_ALPHA = 0.15

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Pendulum Wave",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "A classic pendulum wave pattern.",
                fontSize = 12,
                color = 0xFF49454F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Canvas({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(360)
                    .clip(compose.RoundedCornerShape(16))
                    .background(0xFF0A0A14),
                continuousRedraw = true,
                onDraw = function(draw, w, h, timeSec)
                    local cx = w / 2
                    local cy = h * 0.75
                    local spacing = w / (PENDULUM_COUNT + 1)

                    for i = 1, PENDULUM_COUNT do
                        local x = spacing * i
                        local length = PENDULUM_LENGTH_BASE + (i - 1) * PENDULUM_LENGTH_STEP
                        local freq = FREQ_BASE * math.sqrt(PENDULUM_LENGTH_BASE / length)
                        local angle = math.sin(timeSec * freq) * 0.5 * AMPLITUDE_FACTOR

                        -- 拖尾线
                        for t = 0.1, 0.9, 0.05 do
                            local trailAngle = math.sin((timeSec - t * 0.3) * freq) * 0.5 * AMPLITUDE_FACTOR
                            local trailX = x + math.sin(trailAngle) * length
                            local trailY = cy + math.cos(trailAngle) * length
                            local alpha = math.floor(TRAIL_ALPHA * 255 * (1 - t))
                            local color = (0xFF42A5F5 & 0x00FFFFFF) | (alpha << 24)
                            draw.drawCircle(trailX, trailY, 2, color)
                        end

                        -- 摆锤球
                        local bx = x + math.sin(angle) * length
                        local by = cy + math.cos(angle) * length
                        draw.drawCircle(bx, by, 8, 0xFF42A5F5)

                        -- 悬挂点
                        draw.drawCircle(x, cy - 20, 3, 0xFF666666)

                        -- 摆线
                        draw.drawLine(x, cy - 20, bx, by, 0xFF333333, 1)
                    end
                end,
            }),
        },
    })
end)