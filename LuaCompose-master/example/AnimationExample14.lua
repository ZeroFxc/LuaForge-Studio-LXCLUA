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

import "java.lang.Math"

function AnimationExample14()
    local ITEM_COUNT = 4
    local RADIUS_DP = 110
    local STAGGER_MS = 50
    local SPRING_STIFFNESS = 500 -- 100 (lazy) ↔ 1500 (snappy)
    local SPRING_DAMPING = 0.55 -- 0.3 (very bouncy) ↔ 1.0 (no overshoot)
    local ARC_DEG = 180 -- 180 = half circle, 360 = full circle

    local palette = {
        Color(0xFFEF5350),
        Color(0xFF42A5F5),
        Color(0xFF66BB6A),
        Color(0xFFFFCA28),
        Color(0xFFAB47BC),
        Color(0xFF26A69A),
        Color(0xFFFF7043),
        Color(0xFF7E57C2),
    }

    local labels = {
        "★", "♥", "✦", "✿", "✱", "✪", "❖", "☎"
    }

    local isOpen = state(false)
    local progresses = remember(ITEM_COUNT, function()
        local list = {}
        for i = 1, ITEM_COUNT do
            table.insert(list, Animatable(0))
        end
        return list
    end)




    local fabRotation = animateFloatAsState {
        targetValue = (isOpen.value) and 45 or 0,
        animationSpec = spring({ stiffness = SPRING_STIFFNESS, dampingRatio = SPRING_DAMPING }),
        label = "fabRotation",
    }

    LaunchedEffect(isOpen.value, function(coroutineScope)
        for i, p in ipairs(progresses) do
            coroutineScope.launch(function()
                local staggerIdx = (isOpen.value) and (i - 1) or (ITEM_COUNT - i)
                -- ?
                delay(staggerIdx * STAGGER_MS)
                p.animateTo({
                    targetValue = (isOpen.value) and 1 or 0,
                    animationSpec = spring({
                        stiffness = SPRING_STIFFNESS,
                        dampingRatio = SPRING_DAMPING,
                    }),
                })
            end) -- launch

        end -- for
    end) -- LaunchedEffect

    local density = LocalDensity.current.density
    local radiusPx = RADIUS_DP * density

    Column {
        modifier = Modifier.fillMaxWidth().padding(16),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()
            Text({
                text = "Radial FAB Menu",
                style = MaterialTheme.typography.titleMedium,
            })
            Text({
                text = "Tap the center. Satellites scatter with staggered spring physics.",
                style = MaterialTheme.typography.bodySmall,
            })
            Box {
                modifier = Modifier.fillMaxWidth().height(320),
                contentAlignment = Alignment.Center,
                content = function()
                    for i, p in ipairs(progresses) do


                        local angleDeg
                        if ITEM_COUNT == 1 then
                            angleDeg = 90.0
                        else
                            local sweepStart = 180 + (180 - ARC_DEG) / 2
                            angleDeg = sweepStart + ((i - 1) / (ITEM_COUNT - 1)) * ARC_DEG
                        end

                        local rad = Math.toRadians(angleDeg)
                        local targetX = radiusPx * math.cos(rad)
                        local targetY = radiusPx * math.sin(rad)
                        Box {
                            modifier = Modifier
                                    .size(56)
                                    .graphicsLayer(function(graphicsLayerScope)
                                local v = p.value
                                graphicsLayerScope.translationX = targetX * v
                                graphicsLayerScope.translationY = targetY * v
                                local s = 0.4 + 0.6 * v
                                graphicsLayerScope.scaleX = s
                                graphicsLayerScope.scaleY = s
                                graphicsLayerScope.alpha = v


                            end) -- graphicsLayer
                                    .clip(CircleShape)
                                    .background(palette[((i - 1) % #palette) + 1])
                                    .clickable({ enabled = isOpen.value,
                                                 onClick = function()
                                                     isOpen.value = false
                                                 end }),
                            contentAlignment = Alignment.Center,
                            content = function()
                                Text({
                                    text = labels[((i - 1) % #labels) + 1],
                                    color = Color.White,
                                    fontSize = 24,
                                    fontWeight = FontWeight.Bold,
                                })
                            end -- box c

                        } -- Box

                    end -- for
                    Box {
                        modifier = Modifier
                                .size(72)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(function()
                            isOpen.value = not isOpen.value
                        end),
                        contentAlignment = Alignment.Center,
                        content = function()
                            Text({
                                text = "+",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 36,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.graphicsLayer { rotationZ = fabRotation.value },
                            })
                        end
                    } -- Box

                end --Box c
            } -- Box

        end -- Column c

    } -- Column

end -- fun14

setContent(AnimationExample14)
