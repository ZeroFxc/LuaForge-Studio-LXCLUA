-- main.lua



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
local infiniteRepeatable =animation.core.infiniteRepeatable
local RepeatMode = animation.core.RepeatMode
local rememberInfiniteTransition = animation.core.rememberInfiniteTransition
-- ui
local ui = compose.ui
local Color = ui.graphics.Color


function AnimationExample7()
    local PULSE_DURATION_MS = 600 -- fast (200) ↔ slow (3000)
    local SCALE_MIN = 0.85
    local SCALE_MAX = 1.15
    local PULSE_EASING = FastOutSlowInEasing -- try LinearEasing or EaseInOutCubic
    local ALPHA_MIN = 0.6
    local ALPHA_MAX = 1.0
    local HEART_FONT_SIZE_SP = 120

    local transition = rememberInfiniteTransition()

    local scale = key("scale", PULSE_DURATION_MS, PULSE_EASING,function()
        return transition.animateFloat {
            initialValue = SCALE_MIN,
            targetValue = SCALE_MAX,
            animationSpec = infiniteRepeatable {
                animation = tween{durationMillis = PULSE_DURATION_MS, easing = PULSE_EASING},
                repeatMode = RepeatMode.Reverse,
            }
        }
    end)

    local alpha = key("alpha", PULSE_DURATION_MS, PULSE_EASING,function()
        return transition.animateFloat {
            initialValue = ALPHA_MIN,
            targetValue = ALPHA_MAX,
            animationSpec = infiniteRepeatable {
                animation = tween{durationMillis = PULSE_DURATION_MS, easing = PULSE_EASING},
                repeatMode = RepeatMode.Reverse,
            }
        }
    end)

    Column {
        modifier = Modifier.fillMaxWidth().padding(16),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()
            Text {
                text = "Pulsing Heart (rememberInfiniteTransition)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            }

            Box {
                modifier = Modifier.fillMaxWidth().heightIn{min = 280 },
                contentAlignment = Alignment.Center,
                content = function()
                    Text {
                        text = "♥",
                        fontSize = HEART_FONT_SIZE_SP,
                        modifier = Modifier
                                .scale(scale.value)
                                .alpha(alpha.value),
                    }
                end

            }
        end
    }


end

setContent(AnimationExample7)
