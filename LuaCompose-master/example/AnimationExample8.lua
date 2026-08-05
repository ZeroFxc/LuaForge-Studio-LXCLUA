
local compose = compose
-- foundation
local RoundedCornerShape = compose.RoundedCornerShape
local setContent = compose.setContent
local Column = compose.foundation.layout.Column
local Row = compose.foundation.layout.Row
local Box = compose.foundation.layout.Box
local Spacer = compose.foundation.layout.Spacer
local state = compose.state
local key = compose.key

local dp = compose.dp
local FontWeight = compose.FontWeight
local Alignment = compose.Alignment
local Arrangement = compose.Arrangement
local TransformOrigin = compose.TransformOrigin
local Canvas = compose.foundation.Canvas

-- material3
local material3 = compose.material3

local Text = material3.Text
local Button = material3.Button
local OutlinedButton = material3.OutlinedButton

local Card = material3.Card
local Surface = material3.Surface
local MaterialTheme = material3.MaterialTheme
local CardDefaults = material3.CardDefaults

-- animation
local animation = compose.animation

local AnimatedContent = animation.AnimatedContent
local Crossfade = animation.Crossfade
local Spring = animation.Spring
local spring = animation.spring

local animateDpAsState = animation.animateDpAsState
local animateColorAsState = animation.animateColorAsState
local animateFloatAsState = animation.animateFloatAsState
local tween = animation.core.tween

local AnimatedVisibility = animation.AnimatedVisibility
local fadeIn, fadeOut = animation.fadeIn, animation.fadeOut
local scaleIn, scaleOut = animation.scaleIn, animation.scaleOut
local slideInHorizontally = animation.slideInHorizontally
local slideOutHorizontally = animation.slideOutHorizontally
local slideInVertically = animation.slideInVertically
local slideOutVertically = animation.slideOutVertically

local FastOutSlowInEasing = animation.core.FastOutSlowInEasing
local infiniteRepeatable = animation.core.infiniteRepeatable
local RepeatMode = animation.core.RepeatMode
local rememberInfiniteTransition = animation.core.rememberInfiniteTransition
local LinearEasing = animation.core.LinearEasing
-- ui
local ui = compose.ui
local Color = ui.graphics.Color
local Offset = compose.ui.geometry.Offset
local Size = compose.ui.geometry.Size
local Stroke = compose.ui.graphics.Stroke
local StrokeCap = compose.ui.graphics.StrokeCap

function AnimationExample8()



    local ROTATION_DURATION_MS = 1400 -- spin speed
    local SWEEP_DURATION_MS = 1200
    local SPINNER_COLOR = Color(0xFF1A94D2) -- try any color
    local SPINNER_STROKE_DP = dp(15)
    local SPINNER_SIZE_DP = dp(163)
    local SWEEP_MIN = 10
    local SWEEP_MAX = 290

    local transition = rememberInfiniteTransition()

    rotation = key(ROTATION_DURATION_MS, function()
        return transition.animateFloat {
            initialValue = 0,
            targetValue = 360,
            animationSpec = infiniteRepeatable {
                animation = tween {
                    durationMillis = ROTATION_DURATION_MS,
                    easing = LinearEasing
                },
                repeatMode = RepeatMode.Restart,
            }
        }
    end)

    sweep = key(SWEEP_DURATION_MS, function()
        return transition.animateFloat {
            initialValue = SWEEP_MIN,
            targetValue = SWEEP_MAX,
            animationSpec = infiniteRepeatable {
                animation = tween {
                    durationMillis = SWEEP_DURATION_MS,
                    easing = LinearEasing
                },
                repeatMode = RepeatMode.Reverse,
            }
        }
    end)

    Column {
        modifier = Modifier.fillMaxWidth().padding(16),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()
            Text {
                text = "Custom Loading Spinner",
                fontWeight = FontWeight.SemiBold,
            }

            Box {
                modifier = Modifier.fillMaxWidth().heightIn({ min = 280 }),
                contentAlignment = Alignment.Center,
                content = function()
                    Canvas {
                        modifier = Modifier.size(SPINNER_SIZE_DP),
                        onDraw = function(drawScope)
                            local density = drawScope.density
                            local strokePx = SPINNER_STROKE_DP.value * density -- Approx toPx Warn实验性的，因为原本是value,但是lua的不能扩展方法，这里给state扩展了
                            local inset = strokePx / 2
                            local arcSize = Size(drawScope.size.width - strokePx, drawScope.size.height - strokePx)
                            drawScope.rotate({
                                degrees = rotation.value,
                                block = function()
                                    return drawScope.drawArc({
                                        color = SPINNER_COLOR,
                                        startAngle = 0,
                                        sweepAngle = sweep.value,
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = arcSize,
                                        style = Stroke({ width = strokePx, cap = StrokeCap.Round }),
                                    })
                                end
                            })
                        end
                    }
                end
            }
        end
    }
end

setContent(AnimationExample8)
