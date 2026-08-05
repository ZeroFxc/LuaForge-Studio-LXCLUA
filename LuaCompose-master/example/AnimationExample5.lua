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

local AnimatedContent = animation.AnimatedContent

local spring = animation.core.spring
local tween = animation.core.tween


local AnimatedVisibility = animation.AnimatedVisibility
local fadeIn, fadeOut = animation.fadeIn, animation.fadeOut
local scaleIn, scaleOut = animation.scaleIn, animation.scaleOut
local slideInHorizontally = animation.slideInHorizontally
local slideOutHorizontally = animation.slideOutHorizontally
local slideInVertically = animation.slideInVertically
local slideOutVertically = animation.slideOutVertically

local state = compose.state

local dp = compose
local MaterialTheme = material3.MaterialTheme
local CardDefaults = material3.CardDefaults
local FontWeight = compose.FontWeight
local Color = compose.ui.graphics.Color
local Alignment = compose.Alignment
local Arrangement = compose.Arrangement
local TransformOrigin = compose.TransformOrigin

function AnimationExample5()
    local slideDurationMs = 650 -- try 120 (snappy) / 800 (luxurious)
    local fadeDurationMs = 450 -- try 0 (no fade) / 600 (long crossfade)
    local slideOffsetDivisor = 1 -- 1 = full height slide, 2 = half, 4 = subtle

    local count = state(0)
    Column {
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()

            Box {
                modifier = Modifier
                        .fillMaxWidth()
                        .height(160),
                contentAlignment = Alignment.Center,

                content = function()
                    AnimatedContent {
                        targetState = count.value,
                        transitionSpec = function(scope)
                            local goingUp = scope.targetState > scope.initialState
                            if (goingUp) then
                                return (slideInVertically {
                                    animationSpec = tween(slideDurationMs),
                                    initialOffsetY = function(it)
                                        return it / slideOffsetDivisor
                                    end,

                                } + fadeIn { animationSpec = tween(fadeDurationMs) }):togetherWith(
                                        (slideOutVertically {
                                            animationSpec = tween(slideDurationMs),
                                            targetOffsetY = function(it)
                                                return -it / slideOffsetDivisor
                                            end,

                                        }
                                                + fadeOut { animationSpec = tween(fadeDurationMs) }
                                        )
                                )

                            else
                                return (slideInVertically {
                                    animationSpec = tween(slideDurationMs),
                                    initialOffsetY = function(it)
                                        return - it / slideOffsetDivisor
                                    end,

                                } + fadeIn { animationSpec = tween(fadeDurationMs) }):togetherWith(
                                        (slideOutVertically {
                                            animationSpec = tween(slideDurationMs),
                                            targetOffsetY = function(it)
                                                return it / slideOffsetDivisor
                                            end,

                                        }
                                                + fadeOut { animationSpec = tween(fadeDurationMs) }
                                        )
                                )
                            end

                        end,

                        content = function(_,value)
                            Text {
                                text = value,
                                fontSize = 96,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            }
                        end

                    }
                end


            }

            Row {
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16, Alignment.CenterHorizontally),
                content = function()
                    Button {
                        onClick = function()
                            count.value = count.value - 1
                        end,
                        content = function()
                            Text {
                                text = "−",
                                fontSize = 24
                            }
                        end
                    }

                    Button {
                        onClick = function()
                            count.value = count.value + 1
                        end,
                        content = function()
                            Text {
                                text = "+",
                                fontSize = 24
                            }
                        end
                    }


                end
            }

        end
    }


end

setContent(AnimationExample5)
