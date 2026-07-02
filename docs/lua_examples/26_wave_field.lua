-- 26_wave_field.lua — AnimationExample17: 分层波浪场
-- 1:1 复刻 Kotlin 原版：主波+次谐波叠加，多层视差，Canvas continuousRedraw 每帧驱动
local compose = _G.compose

local LAYER_COUNT = 4
local BASE_FREQUENCY = 0.010
local BASE_AMPLITUDE_DP = 18
local BASE_PHASE_SPEED = 2.2
local FREQ_RAMP = 0.18
local AMPL_FALLOFF = 0.08
local PHASE_RAMP = 0.35
local SECONDARY_RATIO = 2.7
local SECONDARY_AMP = 0.35
local LAYER_COLOR_START = 0xFF7EC9EB
local LAYER_COLOR_END = 0xFF1A237E
local LAYER_ALPHA_FRONT = 0.85
local LAYER_ALPHA_BACK = 0.25
local BG_TOP = 0xFF0A0E27
local BG_BOTTOM = 0xFFBFC2ED
local BASELINE_FRACTION = 0.55
local SAMPLE_STEP_PX = 5

-- 颜色插值（ARGB）
local function lerpColor(c1, c2, t)
    local a1 = (c1 >> 24) & 0xFF
    local r1 = (c1 >> 16) & 0xFF
    local g1 = (c1 >> 8) & 0xFF
    local b1 = c1 & 0xFF
    local a2 = (c2 >> 24) & 0xFF
    local r2 = (c2 >> 16) & 0xFF
    local g2 = (c2 >> 8) & 0xFF
    local b2 = c2 & 0xFF
    local a = math.floor(a1 + (a2 - a1) * t + 0.5)
    local r = math.floor(r1 + (r2 - r1) * t + 0.5)
    local g = math.floor(g1 + (g2 - g1) * t + 0.5)
    local b = math.floor(b1 + (b2 - b1) * t + 0.5)
    return (a << 24) | (r << 16) | (g << 8) | b
end

-- 设置颜色 alpha 通道
local function withAlpha(color, alpha)
    local a = math.floor(alpha * 255 + 0.5)
    if a < 0 then a = 0 elseif a > 255 then a = 255 end
    return (color & 0x00FFFFFF) | (a << 24)
end

-- 波浪 Y 坐标计算（与 Kotlin 原版 waveY 函数一致）
local function waveY(x, baseline, frequency, amplitude, phase)
    local primary = math.sin(x * frequency + phase) * amplitude
    local secondary = math.sin(x * frequency * SECONDARY_RATIO + phase * 1.7) * amplitude * SECONDARY_AMP
    return baseline + primary + secondary
end

compose.render(function()
    local density = compose.LocalDensity.density
    local baseAmplitudePx = BASE_AMPLITUDE_DP * density

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Wave Field",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "Layered compound sine waves.",
                fontSize = 12,
                color = 0xFF49454F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(380)
                    .clip(compose.RoundedCornerShape(16)),
                children = {
                    compose.Canvas({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .height(380),
                        continuousRedraw = true,
                        onDraw = function(draw, w, h, timeSec)
                            local t = timeSec or 0
                            local baseline = h * BASELINE_FRACTION
                            local twoPi = 2 * math.pi
                            local safeStep = SAMPLE_STEP_PX < 1 and 1 or SAMPLE_STEP_PX
                            local sampleCount = math.floor(w / safeStep) + 2

                            -- 背景：垂直渐变
                            draw.drawRectVerticalGradient(0, 0, w, h, BG_TOP, BG_BOTTOM)

                            -- 从后往前画（layerIndex 从大到小）
                            local denom = math.max(LAYER_COUNT - 1, 1)
                            for layerIndex = LAYER_COUNT - 1, 0, -1 do
                                local layerT = layerIndex / denom
                                local frequency = BASE_FREQUENCY * (1 + layerIndex * FREQ_RAMP)
                                local amplitude = baseAmplitudePx * math.max(1 - layerIndex * AMPL_FALLOFF, 0.05)
                                local phaseSpeed = BASE_PHASE_SPEED * (1 + layerIndex * PHASE_RAMP)
                                local phaseOffset = layerIndex * (twoPi / math.max(LAYER_COUNT, 1)) * 0.5
                                local phase = t * phaseSpeed + phaseOffset

                                local baseColor = lerpColor(LAYER_COLOR_START, LAYER_COLOR_END, layerT)
                                local alpha = LAYER_ALPHA_FRONT + (LAYER_ALPHA_BACK - LAYER_ALPHA_FRONT) * layerT
                                local layerColor = withAlpha(baseColor, alpha)

                                local path = compose.Path()
                                local firstY = waveY(0, baseline, frequency, amplitude, phase)
                                path.moveTo(0, firstY)

                                for i = 1, sampleCount - 1 do
                                    local x = math.min(i * safeStep, w)
                                    local y = waveY(x, baseline, frequency, amplitude, phase)
                                    path.lineTo(x, y)
                                    if x >= w then break end
                                end

                                path.lineTo(w, h)
                                path.lineTo(0, h)
                                path.close()

                                draw.drawPath(path, layerColor)
                            end
                        end,
                    }),
                },
            }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Text({
                text = "Waves animate continuously. Tweak literals at top of file.",
                fontSize = 11,
                color = 0xFF49454F,
            }),
        },
    })
end)