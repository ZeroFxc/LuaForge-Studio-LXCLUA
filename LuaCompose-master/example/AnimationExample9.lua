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
local Canvas = compose.Canvas

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
local spring = animation.core.spring

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
local infiniteRepeatable = animation.core.infiniteRepeatable
local RepeatMode = animation.core.RepeatMode
local rememberInfiniteTransition = animation.core.rememberInfiniteTransition
local LinearEasing = animation.core.LinearEasing
-- ui
local ui = compose.ui
local Color = ui.graphics.Color
local Offset = compose.ui.geometry.Offset
local Size = compose.ui.geometry.Size
local Stroke = compose.ui.graphics.Stroke
local StrokeCap = compose.ui.graphics.StrokeCap

local Modifier = compose.Modifier

local animation_core = compose.animation.core

local Animatable = animation_core.Animatable
local remember = compose.remember
local LaunchedEffect = compose.LaunchedEffect
local rememberCoroutineScope = compose.rememberCoroutineScope

local sp = compose.sp

local IntOffset = compose.ui.unit.IntOffset
local detectDragGestures = compose.gestures.detectDragGestures

function AnimationExample9()
    local SPRING_STIFFNESS = 600
    local SPRING_DAMPING = 0.55
    local BOX_COLOR = Color(0xFF26A69A)
    local BOX_SIZE_DP = dp(80)
    local BOX_CORNER_DP = dp(16)

    local offsetX = remember("offsetX", function()
        return Animatable(0)
    end)
    local offsetY = remember("offsetY", function()
        return Animatable(0)
    end)
    local scope = rememberCoroutineScope()

    LaunchedEffect(SPRING_STIFFNESS, SPRING_DAMPING, function()
        offsetX.snapTo(0)
        offsetY.snapTo(0)
    end)

    Column {
        modifier = Modifier.fillMaxWidth().padding(dp(16)),
        verticalArrangement = Arrangement.spacedBy(dp(16)),
        content = function()
            Text { text = "Spring Drag Box", fontWeight = FontWeight.SemiBold }
            Text { text = "Drag me, then release", fontSize = sp(12), color = Color(0xFF666666) }
            Text { text = "offset = (" .. tostring(offsetX.value) .. ", " .. tostring(offsetY.value) .. ")", fontSize = sp(10), color = Color(0xFF999999) }
            Box {
                modifier = Modifier.fillMaxWidth().heightIn({ min = 280 }),
                contentAlignment = Alignment.Center,
                content = function()
                    Box {
                        modifier = Modifier
                                .offset(function()
                            return IntOffset(math.floor(offsetX.value), math.floor(offsetY.value))
                        end)
                                .size(BOX_SIZE_DP)
                                .clip(RoundedCornerShape(dp(BOX_CORNER_DP)))
                                .background(BOX_COLOR)
                                .pointerInput(SPRING_STIFFNESS, SPRING_DAMPING,
                                function()
                                    detectDragGestures({
                                        onDrag = function(change, dragAmount)
                                            change.consume()
                                            scope.launch(function()
                                                offsetX.snapTo(offsetX.value + dragAmount.x)
                                                offsetY.snapTo(offsetY.value + dragAmount.y)
                                            end)
                                        end,
                                        onDragEnd = function()
                                            scope.launch(function()
                                                scope.launch(function()
                                                    offsetX.animateTo({
                                                        targetValue = 0,
                                                        animationSpec = spring({ stiffness = SPRING_STIFFNESS, dampingRatio = SPRING_DAMPING })
                                                    })
                                                end)
                                                scope.launch(function()
                                                    offsetY.animateTo({
                                                        targetValue = 0,
                                                        animationSpec = spring({ stiffness = SPRING_STIFFNESS, dampingRatio = SPRING_DAMPING })
                                                    })
                                                end)
                                            end)
                                        end
                                    })
                                end),
                    }
                end
            }
        end
    }
end

setContent(AnimationExample9)
