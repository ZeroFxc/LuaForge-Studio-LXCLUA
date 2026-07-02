-- 25_confetti.lua — AnimationExample16: 纸屑爆炸粒子系统
-- 1:1 复刻 Kotlin 原版：物理模拟在onDraw中用timeSec驱动，dp→px换算，save/restore旋转
local compose = _G.compose

local BURST_COUNT = 70
local GRAVITY = 1100
local SPEED_MIN = 700
local SPEED_MAX = 1500
local LAUNCH_ANGLE_DEG = -90
local SPREAD_DEG = 110
local LIFETIME_MS_MIN = 1400
local LIFETIME_MS_MAX = 2600
local AIR_DRAG = 1.4
local WOBBLE_AMP = 90
local WOBBLE_FREQ = 0.006
local ROT_SPEED_MAX = 720
local PARTICLE_W_DP = 7
local PARTICLE_H_DP = 14
local FADE_OUT_FRACTION = 0.3

local PALETTE = {
    0xFFFF5252, 0xFFFFEB3B, 0xFF40C4FF, 0xFF69F0AE,
    0xFFE040FB, 0xFFFF6E40, 0xFFFFFFFF,
}

local density = compose.LocalDensity.density
local particleW = PARTICLE_W_DP * density
local particleH = PARTICLE_H_DP * density

local particles = {}
local lastTimeSec = 0

local function rand(min, max)
    return min + math.random() * (max - min)
end

local function emitParticles(originX, originY)
    local baseRad = math.rad(LAUNCH_ANGLE_DEG)
    local spreadRad = math.rad(SPREAD_DEG / 2)
    for i = 1, BURST_COUNT do
        local angle = baseRad + (math.random() * 2 - 1) * spreadRad
        local speed = rand(SPEED_MIN, SPEED_MAX)
        local sizeJitter = 0.7 + math.random() * 0.6
        particles[#particles + 1] = {
            x = originX,
            y = originY,
            vx = math.cos(angle) * speed,
            vy = math.sin(angle) * speed,
            rotation = math.random() * 360,
            rotSpeed = (math.random() * 2 - 1) * ROT_SPEED_MAX,
            ageMs = 0,
            lifetimeMs = rand(LIFETIME_MS_MIN, LIFETIME_MS_MAX),
            color = PALETTE[math.random(1, #PALETTE)],
            w = particleW * sizeJitter,
            h = particleH * sizeJitter,
            wobblePhase = math.random() * 2 * math.pi,
        }
    end
end

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Confetti Burst",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "Tap anywhere in the area to launch a burst.",
                fontSize = 12,
                color = 0xFF49454F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(12) }),
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(380)
                    .clip(compose.RoundedCornerShape(16))
                    .background(0xFF101015),
                children = {
                    compose.Canvas({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .height(380)
                            .onTap(function(x, y)
                                emitParticles(x, y)
                            end),
                        continuousRedraw = true,
                        onDraw = function(draw, w, h, timeSec)
                            -- 物理更新：用真实dt
                            local dt = lastTimeSec > 0 and (timeSec - lastTimeSec) or 0
                            if dt > 0.05 then dt = 0.05 end  -- 钳制最大dt防止跳帧
                            lastTimeSec = timeSec
                            local dtMs = dt * 1000
                            local dtSec = dt

                            if dtSec > 0 then
                                local damping = math.exp(-AIR_DRAG * dtSec)
                                local i = 1
                                while i <= #particles do
                                    local p = particles[i]
                                    p.ageMs = p.ageMs + dtMs
                                    if p.ageMs >= p.lifetimeMs or p.y > h + 120 then
                                        table.remove(particles, i)
                                    else
                                        p.vy = p.vy + GRAVITY * dtSec
                                        p.vx = p.vx * damping
                                        p.vy = p.vy * damping
                                        local wobble = math.sin(p.ageMs * WOBBLE_FREQ + p.wobblePhase) * WOBBLE_AMP
                                        p.x = p.x + (p.vx + wobble) * dtSec
                                        p.y = p.y + p.vy * dtSec
                                        p.rotation = p.rotation + p.rotSpeed * dtSec
                                        i = i + 1
                                    end
                                end
                            end

                            -- 绘制所有粒子
                            for _, p in ipairs(particles) do
                                local lifeFraction = math.max(0, 1 - p.ageMs / p.lifetimeMs)
                                local alpha
                                if lifeFraction < FADE_OUT_FRACTION then
                                    alpha = lifeFraction / FADE_OUT_FRACTION
                                else
                                    alpha = 1
                                end
                                local a = math.floor(alpha * 255)
                                local color = (p.color & 0x00FFFFFF) | (a << 24)

                                local halfW = p.w / 2
                                local halfH = p.h / 2

                                draw.save()
                                draw.rotate(p.rotation, p.x, p.y)
                                draw.drawRect(
                                    p.x - halfW, p.y - halfH,
                                    p.x + halfW, p.y + halfH,
                                    color
                                )
                                draw.restore()
                            end
                        end,
                    }),
                },
            }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Text({
                        textLambda = function()
                            return "particles: " .. #particles
                        end,
                        fontSize = 12,
                        color = 0xFF999999,
                        modifier = compose.Modifier().weight(1),
                    }),
                    compose.Button({
                        text = "Reset",
                        onClick = function()
                            particles = {}
                        end,
                    }),
                },
            }),
        },
    })
end)