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
local OutlinedButton = material3.OutlinedButton
local Spacer = compose.Spacer

local Card = material3.Card
local Surface = material3.Surface
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

local state = compose.state

local dp = compose
local MaterialTheme = material3.MaterialTheme
local CardDefaults = material3.CardDefaults
local FontWeight = compose.FontWeight

local Color = compose.ui.graphics.Color
local Alignment = compose.ui.Alignment
local Arrangement = compose.foundation.layout.Arrangement
local TransformOrigin = compose.TransformOrigin

function AnimationExample6()
    local crossfadeDurationMs = 600 -- Try snap (1) or slow (2000) for very different feels.

    local color1 = Color(0xFFFFB74D)
    local color2 = Color(0xFF4FC3F7)
    local color3 = Color(0xFF5C6BC0)

    local tabs = {
        { "\u1F305", "Morning", color1 },
        { "\u1F31E", "Noon", color2 },
        { "\u1F319", "Night", color3 },
    }

    local selected = state(1)

    Column {
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()
            Row {
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8),
                content = function()
                    for index, t in tabs do
                        local emoji, name, _ = table.unpack(t)
                        local isSelected = index == selected.value

                        if (isSelected) then

                            Button {
                                onClick = function()
                                    selected.value = index
                                end,
                                modifier = Modifier.height(44),
                                content = function()

                                    Text {
                                        text = emoji.." "..name,
                                        fontSize = 14
                                    }
                                end
                            }



                        else

                            OutlinedButton {
                                onClick = function()
                                    selected.value = index
                                end,
                                modifier = Modifier.height(44),
                                content = function()

                                    Text {
                                        text = emoji.." "..name,
                                        fontSize = 14
                                    }
                                end
                            }


                        end

                    end
                end

            }

            Crossfade {
                targetState = selected.value,
                animationSpec = tween(crossfadeDurationMs),
                label = "tab-crossfade",
                content = function(current)
                    emoji, name, color = table.unpack(tabs[current])
                    Surface {
                        modifier = Modifier
                                .fillMaxWidth()
                                .height(260),
                        shape = RoundedCornerShape(20),
                        color = color,
                        content = function()
                            Box {
                                modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24),
                                contentAlignment = Alignment.Center,
                                content = function()
                                    Column {
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12),
                                        content = function()
                                            Text {
                                                text = emoji, fontSize = 96
                                            }
                                            Text {
                                                text = name,
                                                fontSize = 36,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            }
                                        end

                                    }
                                end
                            }
                        end
                    }

                end

            }

        end

    }
end

setContent(AnimationExample6)
