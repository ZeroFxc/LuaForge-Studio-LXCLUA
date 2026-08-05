local compose = compose
local setContent = compose.setContent
local state = compose.state
local remember = compose.remember
local LocalDensity = compose.LocalDensity

local Column = compose.foundation.layout.Column
local BoxWithConstraints = compose.foundation.layout.BoxWithConstraints
local Canvas = compose.foundation.Canvas
local Arrangement = compose.Arrangement
local Modifier = compose.Modifier

local material3 = compose.material3
local Text = material3.Text
local MaterialTheme = material3.MaterialTheme

local LaunchedEffect = compose.LaunchedEffect
local withFrameNanos = compose.withFrameNanos

local Color = compose.ui.graphics.Color
local Offset = compose.ui.geometry.Offset

local PI = math.pi
local cos = math.cos
local sin = math.sin
local floor = math.floor

local function lerp(startVal, stopVal, fraction)
    return startVal + (stopVal - startVal) * fraction
end

local function lerpColor(c1, c2, fraction)
    local a = floor(lerp(c1.alpha, c2.alpha, fraction) * 255)
    local r = floor(lerp(c1.red, c2.red, fraction) * 255)
    local g = floor(lerp(c1.green, c2.green, fraction) * 255)
    local b = floor(lerp(c1.blue, c2.blue, fraction) * 255)
    local argb = a * 16777216 + r * 65536 + g * 256 + b
    return Color(argb)
end

function AnimationExample20()
    local PENDULUM_COUNT = 18
    local SYNC_PERIOD_SEC = 30.0
    local BASE_OSCILLATIONS = 20
    local MAX_ANGLE_DEG = 32.0
    local PIVOT_TOP_FRACTION = 0.05
    local PENDULUM_LENGTH_FRACTION = 0.78
    local BOB_RADIUS_DP = 11.0
    local STRING_WIDTH_DP = 1.5
    local STRING_COLOR = Color(0xFF555560)
    local SUPPORT_BAR_COLOR = Color(0xFF888892)
    local COLOR_FIRST = Color(0xFFFFD740)
    local COLOR_LAST = Color(0xFFE040FB)
    local BG_COLOR = Color(0xFF101015)
    local TRAIL_LENGTH = 18
    local TRAIL_ALPHA_HEAD = 0.55

    local time = state(0.0)
    local tick = state(0)

    local trails = remember(function()
        local t = {}
        for i = 1, PENDULUM_COUNT do
            t[i] = {}
        end
        return t
    end)

    LaunchedEffect("Unit", function(scope)
        local lastFrame = 0
        while true do
            withFrameNanos(function(frameTime)
                if lastFrame == 0 then lastFrame = frameTime end
                local deltaSec = (frameTime - lastFrame) / 1000000000.0
                lastFrame = frameTime
                time.value = time.value + deltaSec
                tick.value = tick.value + 1
            end)
        end
    end)

    Column {
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12),
        content = function()
            BoxWithConstraints {
                modifier = Modifier.fillMaxWidth().height(380),
                content = function(boxScope)
                    local densityValue = LocalDensity.current.density
                    local canvasWidthPx = boxScope.maxWidth * densityValue
                    local canvasHeightPx = boxScope.maxHeight * densityValue
                    
                    local bobRadiusPx = BOB_RADIUS_DP * densityValue
                    local stringWidthPx = STRING_WIDTH_DP * densityValue
                    local supportBarWidthPx = 2.5 * densityValue
                    local maxAngleRad = MAX_ANGLE_DEG * PI / 180.0
                    
                    Canvas {
                        modifier = Modifier.fillMaxWidth().height(380),
                        onDraw = function(drawScope)
                            local touch = tick.value
                            
                            drawScope.drawRect({ 
                                color = BG_COLOR, 
                                width = drawScope.size.width,
                                height = drawScope.size.height 
                            })
                            
                            local pivotY = canvasHeightPx * PIVOT_TOP_FRACTION
                            local length = canvasHeightPx * PENDULUM_LENGTH_FRACTION
                            
                            local horizontalPadding = canvasWidthPx * 0.06
                            local usableWidth = canvasWidthPx - horizontalPadding * 2.0
                            local spacing = 0
                            if PENDULUM_COUNT > 1 then
                                spacing = usableWidth / (PENDULUM_COUNT - 1)
                            end
                            
                            drawScope.drawLine({
                                color = SUPPORT_BAR_COLOR,
                                start = Offset(horizontalPadding * 0.5, pivotY),
                                ["end"] = Offset(canvasWidthPx - horizontalPadding * 0.5, pivotY),
                                strokeWidth = supportBarWidthPx
                            })
                            
                            for i = 0, PENDULUM_COUNT - 1 do
                                local pivotX = horizontalPadding + i * spacing
                                local oscillations = BASE_OSCILLATIONS + i
                                local omega = 2.0 * PI * oscillations / SYNC_PERIOD_SEC
                                local theta = maxAngleRad * cos(omega * time.value)
                                
                                local bobX = pivotX + length * sin(theta)
                                local bobY = pivotY + length * cos(theta)
                                local bob = Offset(bobX, bobY)
                                
                                local gradientT = 0
                                if PENDULUM_COUNT > 1 then
                                    gradientT = i / (PENDULUM_COUNT - 1)
                                end
                                local pendulumColor = lerpColor(COLOR_FIRST, COLOR_LAST, gradientT)
                                
                                local trail = trails[i + 1]
                                if TRAIL_LENGTH > 0 then
                                    table.insert(trail, bob)
                                    while #trail > TRAIL_LENGTH do
                                        table.remove(trail, 1)
                                    end
                                    
                                    local trailSize = #trail
                                    for idx = 1, trailSize do
                                        local pos = trail[idx]
                                        local ageT = (idx - 1) / trailSize
                                        local alpha = TRAIL_ALPHA_HEAD * ageT * ageT
                                        local trailRadius = bobRadiusPx * (0.35 + 0.55 * ageT)
                                        drawScope.drawCircle({
                                            color = pendulumColor.copy({ alpha = alpha }),
                                            radius = trailRadius,
                                            center = pos
                                        })
                                    end
                                end
                                
                                drawScope.drawLine({
                                    color = STRING_COLOR,
                                    start = Offset(pivotX, pivotY),
                                    ["end"] = bob,
                                    strokeWidth = stringWidthPx
                                })
                                
                                drawScope.drawCircle({
                                    color = Color(0xFF000000).copy({ alpha = 0.45 }),
                                    radius = bobRadiusPx * 1.05,
                                    center = Offset(bob.x + bobRadiusPx * 0.18, bob.y + bobRadiusPx * 0.22)
                                })
                                
                                drawScope.drawCircle({
                                    color = pendulumColor,
                                    radius = bobRadiusPx,
                                    center = bob
                                })
                                
                                local highlightColor = lerpColor(pendulumColor.copy({ alpha = 0.85 }), Color(0xFFFFFFFF), 0.55)
                                drawScope.drawCircle({
                                    color = highlightColor,
                                    radius = bobRadiusPx * 0.42,
                                    center = Offset(bob.x - bobRadiusPx * 0.32, bob.y - bobRadiusPx * 0.34)
                                })
                                
                                drawScope.drawCircle({
                                    color = Color(0xFFFFFFFF).copy({ alpha = 0.85 }),
                                    radius = bobRadiusPx * 0.18,
                                    center = Offset(bob.x - bobRadiusPx * 0.38, bob.y - bobRadiusPx * 0.40)
                                })
                                
                                drawScope.drawCircle({
                                    color = pendulumColor,
                                    radius = bobRadiusPx * 0.18,
                                    center = Offset(pivotX, pivotY)
                                })
                            end
                        end
                    }
                end
            }
            
            Text {
                text = "Pendulums sync, diverge, and resync every " .. floor(SYNC_PERIOD_SEC) .. "s. Try halving SYNC_PERIOD_SEC.",
                style = MaterialTheme.typography.labelSmall
            }
        end
    }
end

setContent(AnimationExample20)
