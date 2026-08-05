-- foundation
local compose = compose
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

local animateDpAsState = animation.animateDpAsState
local animateColorAsState = animation.animateColorAsState
local tween = animation.core.tween

local state = compose.state

local dp = compose.dp
local MaterialTheme = material3.MaterialTheme
local CardDefaults = material3.CardDefaults
local FontWeight = compose.ui.text.font.FontWeight
local Color = compose.ui.graphics.Color
local Alignment = compose.ui.Alignment
local Arrangement = compose.foundation.layout.Arrangement


function AnimationExample1()
    local EXPAND_DURATION_MS = 400 -- Tweak duration/easing to feel different unfolds
    local COLLAPSED_HEIGHT = dp(160) -- Tweak freely, the value updates live on save
    local EXPANDED_HEIGHT = dp(330) -- Tweak freely, the value updates live on save

    local isExpanded = state(false)
    local spec = tween {
        durationMillis = EXPAND_DURATION_MS
    }
    local animatedHeight = animateDpAsState {
        targetValue = isExpanded.value and EXPANDED_HEIGHT or COLLAPSED_HEIGHT,
        animationSpec =  spec
    }

    Column {
        modifier = Modifier.fillMaxSize().padding(16),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()


            Text {
                text = "点击下面卡片展开和收缩",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            }
            Card {
                modifier = Modifier
                        .fillMaxWidth()
                        .height(animatedHeight.value)
                        .clickable(function()
                    isExpanded.value = not isExpanded.value
                end),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors {
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                },

                content = function()
                    Column {
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(20),
                        verticalArrangement = Arrangement.spacedBy(8),
                        content = function()

                            Row {
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                content = function()

                                    Text {
                                        text = "Tap to " .. (isExpanded.value and "collapse " or "expand "),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    }

                                    Text {
                                        text = isExpanded.value and "v" or ">",
                                        style = MaterialTheme.typography.titleMedium,
                                    }

                                end
                            }

                            Text {
                                text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                                style = MaterialTheme.typography.bodyMedium,
                            }

                            Text {
                                text = "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
                                style = MaterialTheme.typography.bodyMedium,
                            }

                            Text {
                                text = "Ut enim ad minim veniam, quis nostrud exercitation ullamco.",
                                style = MaterialTheme.typography.bodyMedium,
                            }

                            Text {
                                text = "Duis aute irure dolor in reprehenderit in voluptate velit esse.",
                                style = MaterialTheme.typography.bodyMedium,
                            }

                            Text {
                                text = "Excepteur sint occaecat cupidatat non proident, sunt in culpa.",
                                style = MaterialTheme.typography.bodyMedium,
                            }
                        end
                    }
                end
            }


        end
    }
end

setContent(AnimationExample1)
