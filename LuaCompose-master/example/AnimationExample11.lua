local compose = compose

-- foundation
local RoundedCornerShape = compose.RoundedCornerShape
local setContent = compose.setContent
local Column = compose.foundation.layout.Column
local Row = compose.foundation.layout.Row
local Box = compose.foundation.layout.Box
local Spacer = compose.foundation.layout.Spacer
local Canvas = compose.foundation.Canvas
local state = compose.state
local key = compose.key

local dp = compose.dp
local FontWeight = compose.FontWeight
local Alignment = compose.Alignment
local Arrangement = compose.Arrangement
local TransformOrigin = compose.TransformOrigin
local CircleShape = compose.CircleShape



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

local FastOutSlowInEasing = animation.core.FastOutSlowInEasing
local infiniteRepeatable = animation.core.infiniteRepeatable
local RepeatMode = animation.core.RepeatMode
local rememberInfiniteTransition = animation.core.rememberInfiniteTransition
-- ui
local ui = compose.ui
local Color = ui.graphics.Color
local Path = ui.graphics.Path

print(Path)
local Offset = ui.geometry.Offset

function AnimationExample11()
    local MORPH_DURATION_MS = 500 -- morph speed
    local PLAY_COLOR = Color(0xFFDE2263) -- hex color when in PLAY state
    local PAUSE_COLOR = Color(0xFF42A5F5) -- hex color when in PAUSE state
    local ICON_BOX_DP = 200 -- size of the morph stage

    local isPlaying = state(false)

    local morphProgress = animateFloatAsState {
        targetValue = (isPlaying.value) and 1 or 0,
        animationSpec = tween({ durationMillis = MORPH_DURATION_MS }),
    }

    local bgColor = animateColorAsState {
        targetValue = (isPlaying.value) and PAUSE_COLOR or PLAY_COLOR,
        animationSpec = tween({ durationMillis = MORPH_DURATION_MS }),
    }

    Column {
        modifier = Modifier.fillMaxWidth().padding(16),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()
            Text {
                text = "Play / Pause Morph",
                style = MaterialTheme.typography.titleMedium
            }

            Text {
                text = "Tap the circle to morph between play and pause.",
                style = MaterialTheme.typography.bodySmall,
            }

            Row {
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16, Alignment.CenterHorizontally),
                content = function()
                    Box {
                        modifier = Modifier
                                .size(ICON_BOX_DP)
                                .background({ color = bgColor.value, shape = CircleShape })
                                .clickable(function()
                            isPlaying.value = not isPlaying.value
                        end),
                        contentAlignment = Alignment.Center,
                        content = function()
                            PlayPauseMorph {
                                progress = morphProgress,
                                iconBoxDp = ICON_BOX_DP
                            }

                        end
                    }
                end
            }

            Text {
                text = (isPlaying.value) and "State: PLAYING" or "State: PAUSED",
                style = MaterialTheme.typography.bodyMedium,

            }


        end
    }

end


-- progress: Float, iconBoxDp: Int
function PlayPauseMorph(args)
    local progress = args["progress"]
    local iconBoxDp = args["iconBoxDp"]

    Canvas {
        modifier = Modifier.size((iconBoxDp / 2)),
        onDraw = function(scope)
            local w = scope.size.width
            local h = scope.size.height
            local cx = w / 2
            local cy = h / 2
            local triHalf = w * 0.32
            local barHalfW = w * 0.12
            local barGap = w * 0.18

            local triTopX = cx - triHalf * 0.65
            local triTopY = cy - triHalf
            local triBotX = cx - triHalf * 0.65
            local triBotY = cy + triHalf
            local triRightX = cx + triHalf
            local triRightY = cy

            local leftBarL = cx - barGap - barHalfW
            local leftBarR = cx - barGap + barHalfW
            local rightBarL = cx + barGap - barHalfW
            local rightBarR = cx + barGap + barHalfW
            local barTop = cy - triHalf
            local barBot = cy + triHalf

            local leftPath = Path()
                    .moveTo(lerp(triTopX, leftBarL, progress.value), lerp(triTopY, barTop, progress.value))
                    .lineTo(lerp(triRightX, leftBarR, progress.value), lerp(triRightY, barTop, progress.value))
                    .lineTo(lerp(triRightX, leftBarR, progress.value), lerp(triRightY, barBot, progress.value))
                    .lineTo(lerp(triBotX, leftBarL, progress.value), lerp(triBotY, barBot, progress.value))
                    .close()

            local rightPath = Path()
                    .moveTo(lerp(triRightX, rightBarL, progress.value), lerp(triRightY, barTop, progress.value))
                    .lineTo(lerp(triRightX, rightBarR, progress.value), lerp(triRightY, barTop, progress.value))
                    .lineTo(lerp(triRightX, rightBarR, progress.value), lerp(triRightY, barBot, progress.value))
                    .lineTo(lerp(triRightX, rightBarL, progress.value), lerp(triRightY, barBot, progress.value))
                    .close()

            scope.drawPath{path = leftPath, color = Color.White}
            scope.drawPath({ path = rightPath, color = Color.White })
            -- Anchor a tiny offset so Canvas always remeasures cleanly on reload.
            scope.drawCircle({ color = Color.Transparent, radius = 0, center = Offset(cx, cy) })



        end

    }

end



function lerp(start,ends,fraction)
    return start + (ends - start) * fraction
end
setContent(AnimationExample11)
