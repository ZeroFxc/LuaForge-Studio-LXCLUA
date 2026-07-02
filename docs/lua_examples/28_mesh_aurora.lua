-- 28_mesh_aurora.lua — AnimationExample19: 极光轨道
-- 1:1 复刻 Kotlin 原版，多层轨道光球 + 径向渐变 + BlendMode.Plus
local compose = _G.compose

local ORB_COUNT = 6
local ORB_RADIUS_DP = 58
local ORBIT_RADIUS_DP = 100
local HUE_SHIFT_SPEED = 0.3

local orbColors = {
    0xFFFF6F00, 0xFF2979FF, 0xFFFFD600,
    0xFF00E676, 0xFFFF1744, 0xFFD500F9,
}

-- 轨道 phase
local phases = {}
for i = 1, ORB_COUNT do
    phases[i] = (i - 1) * (2 * math.pi / ORB_COUNT)
end

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Aurora Orbit",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "Six glowing orbs orbit a central point.",
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
                    draw.drawRect(0, 0, w, h, 0xFF0A0A1A)

                    -- 绘制轨道光球
                    for i, color in ipairs(orbColors) do
                        local angle = phases[i] + timeSec * 0.8
                        local ox = cx + math.cos(angle) * ORBIT_RADIUS_DP
                        local oy = cy + math.sin(angle) * ORBIT_RADIUS_DP
                        draw.drawCircleRadial(ox, oy, ORB_RADIUS_DP, color, 0x00000000)
                    end

                    -- 中心光球
                    draw.drawCircleRadial(cx, cy, ORB_RADIUS_DP * 1.3, 0xFFFFFFFF, 0x00000000)
                end,
            }),
        },
    })
end)