-- 27_metaball.lua — AnimationExample18: 融合光球
-- 1:1 复刻 Kotlin 原版，多球径向渐变 + BlendMode.Plus + 轨道运动
local compose = _G.compose

local BALL_COUNT = 5
local BALL_RADIUS_DP = 72
local ORBIT_SPEED_RANGE = { 0.4, 0.9 }
local COLOR_MAIN = 0xFFE91E63
local COLOR_SECONDARY = 0xFF2196F3

-- 球体参数
local balls = {}
for i = 1, BALL_COUNT do
    balls[i] = {
        orbitRadius = 40 + i * 18,
        speed = ORBIT_SPEED_RANGE[1] + (ORBIT_SPEED_RANGE[2] - ORBIT_SPEED_RANGE[1]) * (i / BALL_COUNT),
        phase = math.random() * 2 * math.pi,
        blend = 0.3 + 0.7 * (i / BALL_COUNT),
    }
end

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Orbiting Metaballs",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "Rendered with radial gradients and BlendMode.Plus.",
                fontSize = 12,
                color = 0xFF49454F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Canvas({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(360)
                    .clip(compose.RoundedCornerShape(16))
                    .compositingStrategy("Offscreen"),
                continuousRedraw = true,
                onDraw = function(draw, w, h, timeSec)
                    local cx = w / 2
                    local cy = h / 2

                    -- 暗色背景
                    draw.drawRect(0, 0, w, h, 0xFF0A0A14)

                    -- 绘制球体
                    -- 颜色分量提取
                    local r1 = (COLOR_MAIN >> 16) & 0xFF
                    local g1 = (COLOR_MAIN >> 8) & 0xFF
                    local b1 = COLOR_MAIN & 0xFF
                    local r2 = (COLOR_SECONDARY >> 16) & 0xFF
                    local g2 = (COLOR_SECONDARY >> 8) & 0xFF
                    local b2 = COLOR_SECONDARY & 0xFF

                    for _, ball in ipairs(balls) do
                        local angle = ball.phase + timeSec * ball.speed
                        local bx = cx + math.cos(angle) * ball.orbitRadius
                        local by = cy + math.sin(angle) * ball.orbitRadius
                        local r = math.floor(compose.lerp(r1, r2, ball.blend))
                        local g = math.floor(compose.lerp(g1, g2, ball.blend))
                        local b = math.floor(compose.lerp(b1, b2, ball.blend))
                        local mixedColor = (0xFF << 24) | (r << 16) | (g << 8) | b
                        draw.drawCircleRadial(bx, by, BALL_RADIUS_DP, mixedColor, 0x00000000)
                    end
                end,
            }),
        },
    })
end)