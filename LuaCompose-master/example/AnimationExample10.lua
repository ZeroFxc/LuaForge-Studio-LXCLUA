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
local CircleShape = compose.CircleShape
local BoxWithConstraints = compose.foundation.layout.BoxWithConstraints
local LocalDensity = compose.LocalDensity

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
local FastOutLinearInEasing = animation.core.FastOutLinearInEasing
local LinearOutSlowInEasing = animation.core.LinearOutSlowInEasing
local EaseInOutCubic = animation.core.EaseInOutCubic
local EaseOutBounce = animation.core.EaseOutBounce
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



function AnimationExample10()

    local RACE_DURATION_MS = 2500 -- longer = easier to compare easings
    local TRACK_HEIGHT_DP = 36 -- thicker tracks
    local RUNNER_SIZE_DP = 36 -- runner circle size

    local progress = state(0)

    target = animateFloatAsState {
        targetValue = progress.value,
        animationSpec = tween({ durationMillis = RACE_DURATION_MS, easing = LinearEasing }),
    }

    local tick = target

    local entries = {
        { name = "LinearEasing", easing = LinearEasing, color = Color(0xFFEF5350) },
        { name = "FastOutSlowInEasing", easing = FastOutSlowInEasing, color = Color(0xFFAB47BC) },
        { name = "FastOutLinearInEasing", easing = FastOutLinearInEasing, color = Color(0xFF42A5F5) },
        { name = "LinearOutSlowInEasing", easing = LinearOutSlowInEasing, color = Color(0xFF26A69A) },
        { name = "EaseInOutCubic", easing = EaseInOutCubic, color = Color(0xFFFFA726) },
        { name = "EaseOutBounce", easing = EaseOutBounce, color = Color(0xFF8D6E63) },
    }

    Column {
        modifier = Modifier.fillMaxWidth().padding(16),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()
            Text {
                text = "Easing Showcase",
                style = MaterialTheme.typography.titleMedium
            }
            Text {
                text = "Tap Race! to compare easings side by side.",
                style = MaterialTheme.typography.bodySmall,
            }

            for _, entry in entries do
                RaceTrack {
                    entry = entry,
                    progress = progress,
                    durationMs = RACE_DURATION_MS,
                    trackHeightDp = TRACK_HEIGHT_DP,
                    runnerSizeDp = RUNNER_SIZE_DP,
                }
            end

            Button {
                onClick = function()
                    progress.value = (progress.value == 0) and 1 or 0
                end,
                content = function()
                    Text {
                        text = (progress.value == 0) and "Race!" or "Reset"
                    }
                end
            }


        end
    }


end

--[[
entry: EasingEntry,
progress: Float,
durationMs: Int,
trackHeightDp: Int,
runnerSizeDp: Int,
]]
function RaceTrack(args)
    local entry = args["entry"]
    local progress = args["progress"]
    local durationMs = args["durationMs"]
    local trackHeightDp = args["trackHeightDp"]
    local runnerSizeDp = args["runnerSizeDp"]

    local animated = animateFloatAsState {
        targetValue = progress.value,
        animationSpec = tween({ durationMillis = durationMs, easing = entry.easing }),
    }

    Column {
        verticalArrangement = Arrangement.spacedBy(4),
        content = function()
            Text {
                text = entry.name,
                style = MaterialTheme.typography.labelMedium
            }

            BoxWithConstraints {
                modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeightDp)
                        .background({
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(trackHeightDp / 2), }
                ),
                content = function(scope)
                    --
                    local densityValue = LocalDensity.current.density
                    local trackWidthPx = (scope.maxWidth - runnerSizeDp) * densityValue

                    Box {
                        modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding({ horizontal = 0 })
                                .offset(function()
                            return IntOffset((trackWidthPx * animated.value), 0)
                        end)
                                .size(runnerSizeDp)
                                .background({ color = entry.color, shape = CircleShape }),
                    }

                end
            }




        end
    }


end

setContent(AnimationExample10)
