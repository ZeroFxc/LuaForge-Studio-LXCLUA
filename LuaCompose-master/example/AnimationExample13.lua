local compose = compose
local setContent = compose.setContent
local state = compose.state
local remember = compose.remember
local withScope = compose.with
local LocalDensity = compose.LocalDensity
local rememberCoroutineScope = compose.rememberCoroutineScope

local Column = compose.foundation.layout.Column
local Row = compose.foundation.layout.Row
local Box = compose.foundation.layout.Box
local Spacer = compose.foundation.layout.Spacer
local BoxWithConstraints = compose.foundation.layout.BoxWithConstraints
local RoundedCornerShape = compose.RoundedCornerShape
local Arrangement = compose.Arrangement
local Alignment = compose.Alignment

local material3 = compose.material3
local Text = material3.Text
local Button = material3.Button
local Card = material3.Card
local CardDefaults = material3.CardDefaults
local MaterialTheme = material3.MaterialTheme

local animation = compose.animation
local SharedTransitionLayout = animation.SharedTransitionLayout
local AnimatedContent = animation.AnimatedContent
local rememberSharedContentState = animation.rememberSharedContentState
local fadeIn = animation.fadeIn
local fadeOut = animation.fadeOut
local tween = animation.core.tween
local spring = animation.core.spring
local Animatable = animation.core.Animatable
local FastOutSlowInEasing = animation.core.FastOutSlowInEasing

local Color = compose.ui.graphics.Color
local IntOffset = compose.ui.unit.IntOffset
local detectDragGestures = compose.gestures.detectDragGestures

function AnimationExample13()
    local SWIPE_THRESHOLD_FRACTION = 0.3 --0.1 (easy) ↔ 0.6 (hard)
    local ROTATION_FACTOR = 0.15 --degrees per pixel
    local FLING_STIFFNESS = 300 --spring stiffness when snapping back
    local FLING_DURATION_MS = 300 --off screen exit duration

    local CARD_COLORS = {
        Color(0xFFEF5350), -- try Color(0xFFFF6B9D)
        Color(0xFF42A5F5),
        Color(0xFF66BB6A),
        Color(0xFFFFCA28),
    }

    Column {
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()
            Text {
                text = "Swipe the top card left or right",
                modifier = Modifier.padding({ horizontal = 8 })
            }

            local topIndex = state(0)
            local offsetX = remember(function()
                return Animatable(0)
            end)
            local scope = rememberCoroutineScope()

            BoxWithConstraints {
                modifier = Modifier.fillMaxWidth().height(360),
                contentAlignment = Alignment.Center,
                content = function(boxWithConstraintsScope)
                    local densityValue = LocalDensity.current.density
                    local widthPx = boxWithConstraintsScope.maxWidth * densityValue
                    local threshold = widthPx * SWIPE_THRESHOLD_FRACTION

                    if (topIndex.value >= #CARD_COLORS) then

                        Button {
                            onClick = function()
                                topIndex.value = 0
                                scope.launch(function()
                                    offsetX.snapTo(0)
                                end)
                            end,
                            content = function()
                                Text {
                                    text = "All done. Tap to reset"
                                }
                            end
                        }

                    else
                        for peek = 2, 1, -1 do
                            local peekIndex = topIndex.value + peek
                            if peekIndex < #CARD_COLORS then
                                local scale = 1 - peek * 0.05
                                local yOffset = peek * 12
                                Card {
                                    modifier = Modifier
                                            .fillMaxWidth(0.8)
                                            .height(300)
                                            .offset(function()
                                        return IntOffset(0, yOffset)
                                    end)
                                            .graphicsLayer(function(graphicsLayerScope)
                                        graphicsLayerScope.scaleX = scale
                                        graphicsLayerScope.scaleY = scale
                                    end
                                    ),
                                    colors = CardDefaults.cardColors({ containerColor = CARD_COLORS[peekIndex+1] }),
                                    content = function()
                                        Box {
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                            content = function()
                                                Text {
                                                    text = "Card " .. tostring(peekIndex + 1 +1),
                                                    color = Color.White,
                                                    fontSize = 28
                                                }
                                            end

                                        }
                                    end
                                }

                            end
                        end

                        Card {
                            modifier = Modifier
                                    .fillMaxWidth(0.8)
                                    .height(300)
                                    .offset(
                                    function()
                                        return IntOffset(offsetX.value, 0)
                                    end
                            )
                                    .graphicsLayer(function(graphicsLayerScope)
                                graphicsLayerScope.rotationZ = offsetX.value * ROTATION_FACTOR
                            end)
                                    .pointerInput(
                                    topIndex.value,
                                    SWIPE_THRESHOLD_FRACTION,
                                    FLING_STIFFNESS,
                                    FLING_DURATION_MS,
                                    function()
                                        detectDragGestures {
                                            onDrag = function(change, drag)
                                                change.consume()
                                                scope.launch(function()
                                                    offsetX.snapTo(offsetX.value + drag.x)
                                                end)

                                            end,
                                            onDragEnd = function()
                                                scope.launch(function()
                                                    if (math.abs(offsetX.value) > threshold) then
                                                        local target = (offsetX.value > 0) and widthPx * 1.5 or -widthPx * 1.5
                                                        offsetX.animateTo({
                                                            targetValue = target,
                                                            animationSpec = tween(FLING_DURATION_MS)
                                                        })

                                                        topIndex.value = topIndex.value + 1
                                                        offsetX.snapTo(0)
                                                    else
                                                        offsetX.animateTo(
                                                                0,
                                                                spring({ stiffness = FLING_STIFFNESS })
                                                        )
                                                    end

                                                end)
                                            end


                                        }
                                    end
                            ),
                            colors = CardDefaults.cardColors({ containerColor = CARD_COLORS[topIndex.value+1] }),
                            content = function()
                                Box {
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                    content = function()
                                        Text {
                                            text = "Card " .. tostring(topIndex.value + 1),
                                            color = Color.White,
                                            fontSize = 32
                                        }
                                    end
                                }
                            end
                        }


                    end
                end
            }


        end
    }

end

setContent(AnimationExample13)
