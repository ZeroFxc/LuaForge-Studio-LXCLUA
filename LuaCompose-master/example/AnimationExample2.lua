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

local AnimatedVisibility = animation.AnimatedVisibility
local fadeIn, fadeOut = animation.fadeIn, animation.fadeOut
local scaleIn, scaleOut = animation.scaleIn, animation.scaleOut
local slideInHorizontally = animation.slideInHorizontally
local slideOutHorizontally = animation.slideOutHorizontally

local state = compose.state

local dp = compose.dp
local MaterialTheme = material3.MaterialTheme
local CardDefaults = material3.CardDefaults
local FontWeight = compose.FontWeight
local Color = compose.Color
local Alignment = compose.Alignment
local Arrangement = compose.Arrangement
local TransformOrigin = compose.ui.graphics.TransformOrigin

function AnimationExample2()

    local ANIM_DURATION_MS = 600 -- Tweak shared duration for slide/fade/scale together
    -- Swap slideInHorizontally direction below with negative/positive { fullWidth -> ... }
    local SLIDE_FROM_RIGHT = true
    local visible = state(true)

    Column {
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()
            Text {
                text = "AnimatedVisibility: slide / fade / scale",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            }

            Button {
                onClick = function()
                    visible.value = not visible.value
                end,
                content = function()
                    Text {
                        text = (visible.value) and "Hide all" or "Show all"
                    }
                end
            }

            Row {
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12),
                content = function()
                    DemoCell {
                        label = "SLIDE",
                        modifier = Modifier.weight(1),
                        content = function()
                            AnimatedVisibility {
                                visible = visible.value,
                                enter = slideInHorizontally {
                                    animationSpec = tween(ANIM_DURATION_MS),
                                    initialOffsetX = function(fullWidth)
                                        return SLIDE_FROM_RIGHT and fullWidth or -fullWidth
                                    end
                                } + fadeIn { animationSpec = tween(ANIM_DURATION_MS) },
                                exit = slideOutHorizontally {
                                    animationSpec = tween(ANIM_DURATION_MS),
                                    targetOffsetX = function(fullWidth)
                                        return SLIDE_FROM_RIGHT and fullWidth or -fullWidth
                                    end
                                } + fadeOut { animationSpec = tween(ANIM_DURATION_MS) },

                                content = function()
                                    AnimChip { text = "SLIDE" }
                                end

                            }
                        end
                    }

                    DemoCell {
                        label = "FADE",
                        modifier = Modifier.weight(1),
                        content = function()
                            AnimatedVisibility {
                                visible = visible.value,
                                enter = fadeIn { animationSpec = tween(ANIM_DURATION_MS) },
                                exit = fadeOut { animationSpec = tween(ANIM_DURATION_MS) },
                                content = function()
                                    AnimChip { text = "FADE" }
                                end
                            }
                        end
                    }

                    DemoCell {
                        label = "SCALE",
                        modifier = Modifier.weight(1),
                        content = function()
                            AnimatedVisibility {
                                visible = visible.value,
                                enter = scaleIn {
                                    animationSpec = tween(ANIM_DURATION_MS),
                                    transformOrigin = TransformOrigin(0.5, 0.5),
                                } + fadeIn { animationSpec = tween(ANIM_DURATION_MS) },
                                exit = scaleOut {
                                    animationSpec = tween(ANIM_DURATION_MS),
                                    transformOrigin = TransformOrigin(0.5, 0.5),
                                } + fadeOut { animationSpec = tween(ANIM_DURATION_MS) },
                                content = function()
                                    AnimChip { text = "FADE" }
                                end
                            }
                        end
                    }


                end
            }


        end
    }


end

-- label,modifier,content
function DemoCell(args)
    local label = args["label"]
    local modifier = args["modifier"]
    local content = args["content"]

    Column {
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = function()
            Text {
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            }
            Box {
                modifier = Modifier
                        .fillMaxWidth()
                        .height(96),
                contentAlignment = Alignment.Center,
                content = content
            }
        end
    }
end

-- text
function AnimChip(args)
    local text = args["text"]
    Card {
        colors = CardDefaults.cardColors {
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        },
        shape = MaterialTheme.shapes.medium,
        content = function()
            Text {
                text = text,
                modifier = Modifier.padding(18, 14),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            }
        end
    }

end

setContent(AnimationExample2)
