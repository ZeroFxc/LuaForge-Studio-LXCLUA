-- main.lua

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
local FontWeight = compose.FontWeight
local Color = compose.ui.graphics.Color
local Alignment = compose.Alignment
local Arrangement = compose.Arrangement



function AnimationExample3()

    local COLOR_TRANSITION_MS = 600


    local PALETTE = {
        { name = "Coral", color = Color(0xFF63CCD9) },
        { name = "Lime", color = Color(0xFFC6FF00) },
        { name = "Sky", color = Color(0xFF40C4FF) },
        { name = "Lavender", color = Color(0xFF47CD72) },
    }

    local HERO_HEIGHT_DP = 180

    local selectedIndex = state(1)

    local selectedName, selected = PALETTE[selectedIndex.value].name, PALETTE[selectedIndex.value].color



    --local animatedBg = animateColorAsState(
    --        selected,
    --        tween(COLOR_TRANSITION_MS)
    --)

    local animatedBg = animateColorAsState {
        targetValue = selected,
        animationSpec = tween { durationMillis = COLOR_TRANSITION_MS },
    }

    --local animatedContent = animateColorAsState(
    --        (selected:luminance() > 0.5) and Color.White or Color(0xFF202020),
    --        tween(COLOR_TRANSITION_MS)
    --)
    local animatedContent = animateColorAsState {
        targetValue = (selected:luminance() > 0.5) and Color(0xFF202020) or Color.White,
        animationSpec = tween { durationMillis = COLOR_TRANSITION_MS },
    }

    Column {
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()
            Text {
                text = "animateColorAsState swatches",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            }

            Box {
                modifier = Modifier
                        .fillMaxWidth()
                        .height(HERO_HEIGHT_DP)
                        .clip("rounded", 24)
                        .background(animatedBg.value),
                contentAlignment = Alignment.Center,
                content = function()
                    Text {
                        text = selectedName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = animatedContent.value,
                    }

                end
            }

            Row {
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12),
                content = function()
                    for i, t in PALETTE do
                        local index = i

                        Column {
                            modifier = Modifier
                                    .weight(1)
                                    .clickable(function()
                                selectedIndex.value = index
                            end),

                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6),
                            content = function()
                                Box {
                                    modifier = Modifier
                                            .size(48)
                                            .clip("circle")
                                            .background(t.color),
                                }
                                Text {
                                    text = t.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                }
                            end
                        }
                    end

                end
            }

            Text {
                text = "Tap a swatch to morph the hero color.",
                modifier = Modifier.padding(0, 4, 0, 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,

            }


        end

    }


end

setContent(AnimationExample3)
