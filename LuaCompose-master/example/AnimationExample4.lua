-- main.lua



local compose = compose

local RoundedCornerShape = compose.RoundedCornerShape

local material3 = compose.material3
local setContent = compose.setContent
local Column = compose.foundation.layout.Column
local Row = compose.foundation.layout.Row
local Box = compose.foundation.layout.Box
local Text = material3.Text
local Button = material3.Button
local Spacer = compose.foundation.layout.Spacer

local Card = material3.Card

-- animation
local animation = compose.animation

local Spring = animation.core.Spring
local spring = animation.core.spring

local animateDpAsState = animation.animateDpAsState
local animateColorAsState = animation.animateColorAsState
local animateFloatAsState = animation.animateFloatAsState
local tween = animation.tween

local AnimatedVisibility = animation.AnimatedVisibility
local fadeIn, fadeOut = animation.fadeIn, animation.fadeOut
local scaleIn, scaleOut = animation.scaleIn, animation.scaleOut
local slideInHorizontally = animation.slideInHorizontally
local slideOutHorizontally = animation.slideOutHorizontally

local state = compose.state

local dp = compose
local MaterialTheme = material3.MaterialTheme
local CardDefaults = material3.CardDefaults
local FontWeight = compose.FontWeight
local Color = compose.ui.graphics.Color
local Alignment = compose.Alignment
local Arrangement = compose.Arrangement
local TransformOrigin = compose.TransformOrigin

function AnimationExample4()
    local springStiffness = Spring.StiffnessMediumLow -- try Spring.StiffnessLow / StiffnessHigh
    local springDamping = Spring.DampingRatioMediumBouncy -- try NoBouncy / HighBouncy

    local collapsedSize = 74 -- idle FAB size
    local expandedSize = 196 -- morphed FAB size
    local collapsedRotation = 0 -- idle rotation in degrees
    local expandedRotation = 135 -- morphed rotation (try 45 / 180 / 360)
    local expandedCornerDp = 24 -- morphed corner radius

    local morphed = state(false)

    local size = animateDpAsState {
        targetValue = (morphed.value) and expandedSize or collapsedSize,
        animationSpec = spring {
            dampingRatio = springDamping,
            stiffness = springStfness
        },
        label = "size",
    }

    local rotation = animateFloatAsState {
        targetValue = (morphed.value) and expandedRotation or collapsedRotation,
        animationSpec = spring {
            dampingRatio = springDamping,
            stiffness = springStiffness
        },
        label = "rotation",
    }
    local cornerDp = animateDpAsState {
        targetValue = (morphed.value) and expandedCornerDp or collapsedSize / 2,
        animationSpec = spring {
            dampingRatio = springDamping,
            stiffness = springStiffness
        },
        label = "corner",
    }
    local color = animateColorAsState {
        targetValue = (morphed.value) and MaterialTheme.colorScheme.secondary or MaterialTheme.colorScheme.primary,
        animationSpec = spring {
            dampingRatio = springDamping,
            stfness = springStiffness
        },
        label = "color",
    }

    Column {
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()


            Box {
                modifier = Modifier
                        .fillMaxWidth()
                        .height(240),
                contentAlignment = Alignment.Center,
                content = function()


                    Box {
                        modifier = Modifier
                                .size(size.value)
                                .rotate(rotation.value)
                                .clip(RoundedCornerShape(cornerDp.value))
                                .background { color = color.value, shape = RoundedCornerShape(cornerDp.value) }
                                .clickable(function()
                            morphed.value = not morphed.value
                        end),
                        contentAlignment = Alignment.Center,
                        content = function()
                            Text {
                                text = "+",
                                color = Color.White,
                                fontSize = 32,
                                fontWeight = FontWeight.Bold,
                            }
                        end
                    }


                end

            }

            Text {
                text = "Tap to morph",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
            }


        end

    }


end

setContent(AnimationExample4)
