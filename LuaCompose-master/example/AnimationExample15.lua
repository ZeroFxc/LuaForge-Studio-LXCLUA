local compose = compose
local setContent = compose.setContent
local state = compose.state

local delay = compose.delay

local Color = compose.ui.graphics.Color
local Offset = compose.ui.geometry.Offset
local Rect = compose.ui.geometry.Rect
local StrokeCap = compose.ui.graphics.StrokeCap
local Brush = compose.ui.graphics.Brush
local RuntimeShader = compose.ui.graphics.RuntimeShader
local RenderEffect = compose.ui.graphics.RenderEffect

local Modifier = compose.Modifier
local Arrangement = compose.Arrangement
local Alignment = compose.Alignment
local RoundedCornerShape = compose.RoundedCornerShape
local CircleShape = compose.CircleShape

local FontWeight = compose.FontWeight

local Column = compose.foundation.layout.Column
local Row = compose.foundation.layout.Row
local Box = compose.foundation.layout.Box
local BoxWithConstraints = compose.foundation.layout.BoxWithConstraints
local Canvas = compose.foundation.Canvas
local Spacer = compose.foundation.layout.Spacer

local material3 = compose.material3
local Text = material3.Text
local MaterialTheme = material3.MaterialTheme

local animation = compose.animation
local LaunchedEffect = compose.LaunchedEffect
local remember = compose.remember
local rememberCoroutineScope = compose.rememberCoroutineScope
local LocalDensity = compose.LocalDensity
local withFrameNanos = compose.withFrameNanos

local animateColorAsState = animation.animateColorAsState
local animateFloatAsState = animation.animateFloatAsState
local Animatable = animation.core.Animatable
local FastOutLinearInEasing = animation.core.FastOutLinearInEasing
local tween = animation.core.tween
local spring = animation.core.spring
local CubicBezierEasing = animation.core.CubicBezierEasing
local Spring = animation.core.Spring
local ui = compose.ui
local Path = ui.graphics.Path
local PathOperation = ui.graphics.PathOperation
local TextAlign = ui.text.style.TextAlign
local detectDragGestures = compose.gestures.detectDragGestures
local detectTapGestures = compose.gestures.detectTapGestures
local IntOffset = compose.ui.unit.IntOffset
local math = math
local FastOutSlowInEasing = animation.core.FastOutSlowInEasing
local Card = material3.Card
local CardDefaults = material3.CardDefaults

function AnimationExample15()
    local FLIP_DURATION_MS = 700
    local FLIP_EASING = FastOutSlowInEasing
    local CAMERA_DISTANCE_FACTOR = 12.0

    local FRONT_COLOR = Color(0xFFE91E63)
    local BACK_COLOR = Color(0xFFFF7043)

    Column {
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()
            Text {
                text = "Tap the card to flip",
                modifier = Modifier.padding({ horizontal = 8 })

            }

            local flipped = state(false)
            local rotation = animateFloatAsState {
                targetValue = (flipped.value) and 180 or 0,
                animationSpec = tween({ durationMillis = FLIP_DURATION_MS, easing = FLIP_EASING }),
                label = "flip",
            }

            local density = LocalDensity.current.density

            Box {
                modifier = Modifier.fillMaxWidth().height(320),
                contentAlignment = Alignment.Center,
                content = function()
                    Card {
                        modifier = Modifier
                                .size(240)
                                .graphicsLayer {
                            rotationY = rotation.value,
                            cameraDistance = CAMERA_DISTANCE_FACTOR * density
                        }
                                .clickable(function()
                            flipped.value = not flipped.value
                        end),
                        colors = CardDefaults.cardColors {
                            containerColor = (rotation.value <= 90) and FRONT_COLOR or BACK_COLOR,
                        },

                        content = function()
                            Box {
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                                content = function()
                                    if (rotation.value <= 90) then
                                        Column {
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12),
                                            content = function()
                                                Text({ text = "♥", fontSize = 96, color = Color.White })
                                                Text({ text = "Compose", fontSize = 22, color = Color.White })


                                            end
                                        }

                                    else
                                        Column {
                                            modifier = Modifier.graphicsLayer { rotationY = 180 },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12),
                                            content = function()
                                                Text({ text = "✦", fontSize = 96 })
                                                Text({ text = "Saved!", fontSize = 22, color = Color.White })

                                            end

                                        }
                                    end

                                end -- Box c

                            } -- Box

                        end -- Card c


                    } -- Card

                end -- Box c

            } -- Box

        end -- Column c

    } -- Column

end

setContent(AnimationExample15)