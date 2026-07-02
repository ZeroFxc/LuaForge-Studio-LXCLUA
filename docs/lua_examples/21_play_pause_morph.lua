-- 21_play_pause_morph.lua — AnimationExample11: 播放/暂停图标形变 (Canvas + LuaPath + lerp)
-- 1:1 复刻 Kotlin 原版，三角形→双竖线平滑过渡，右半部分在 50% 后出现
local compose = _G.compose

local MORPH_DURATION_MS = 500
local PLAY_COLOR = 0xFFDE2263
local PAUSE_COLOR = 0xFF42A5F5
local ICON_BOX_DP = 200

local isPlaying = compose.state(false)

compose.render(function()
    local morphProgress = compose.animateFloatAsState({
        targetValue = isPlaying.value and 1.0 or 0.0,
        useRecompose = true,
        animationSpec = compose.tween(MORPH_DURATION_MS, "FastOutSlowIn"),
    })
    local bgColor = compose.animateColorAsState({
        targetValue = isPlaying.value and PAUSE_COLOR or PLAY_COLOR,
        animationSpec = compose.tween(MORPH_DURATION_MS),
    })

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Play / Pause Morph",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "Tap the circle to morph between play and pause.",
                fontSize = 12,
                color = 0xFF49454F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Box({
                        modifier = compose.Modifier()
                            .size(ICON_BOX_DP, ICON_BOX_DP)
                            .clip(compose.CircleShape)
                            .background(bgColor.value)
                            .clickableLua(function()
                                isPlaying.value = not isPlaying.value
                            end),
                        children = {
                            compose.Canvas({
                                modifier = compose.Modifier()
                                    .size(ICON_BOX_DP / 2, ICON_BOX_DP / 2),
                                onDraw = function(draw, w, h, timeSec)
                                    local t = morphProgress.getValue()
                                    local cx = w / 2
                                    local cy = h / 2
                                    local triHalf = w * 0.32
                                    local barHalfW = w * 0.12
                                    local barGap = w * 0.18

                                    -- 三角形顶点
                                    local triTopX = cx - triHalf * 0.65
                                    local triTopY = cy - triHalf
                                    local triBotX = cx - triHalf * 0.65
                                    local triBotY = cy + triHalf
                                    local triRightX = cx + triHalf
                                    local triRightY = cy

                                    -- 暂停左竖线目标
                                    local leftBarL = cx - barGap - barHalfW
                                    local leftBarR = cx - barGap + barHalfW
                                    local barTop = cy - triHalf
                                    local barBot = cy + triHalf

                                    -- 左半部分：三角形 → 左竖线
                                    local leftPath = compose.LuaPath()
                                    leftPath.moveTo(
                                        compose.lerp(triTopX, leftBarL, t),
                                        compose.lerp(triTopY, barTop, t)
                                    )
                                    leftPath.lineTo(
                                        compose.lerp(triRightX, leftBarR, t),
                                        compose.lerp(triRightY, barTop, t)
                                    )
                                    leftPath:lineTo(
                                        compose.lerp(triRightX, leftBarR, t),
                                        compose.lerp(triRightY, barBot, t)
                                    )
                                    leftPath:lineTo(
                                        compose.lerp(triBotX, leftBarL, t),
                                        compose.lerp(triBotY, barBot, t)
                                    )
                                    leftPath.close()
                                    draw.drawPath(leftPath, 0xFFFFFFFF)

                                    -- 右半部分：三角形右边 → 右竖线
                                    local rightBarL = cx + barGap - barHalfW
                                    local rightBarR = cx + barGap + barHalfW

                                    local rightPath = compose.LuaPath()
                                    rightPath.moveTo(
                                        compose.lerp(triRightX, rightBarL, t),
                                        compose.lerp(triRightY, barTop, t)
                                    )
                                    rightPath.lineTo(
                                        compose.lerp(triRightX, rightBarR, t),
                                        compose.lerp(triRightY, barTop, t)
                                    )
                                    rightPath:lineTo(
                                        compose.lerp(triRightX, rightBarR, t),
                                        compose.lerp(triRightY, barBot, t)
                                    )
                                    rightPath:lineTo(
                                        compose.lerp(triRightX, rightBarL, t),
                                        compose.lerp(triRightY, barBot, t)
                                    )
                                    rightPath.close()
                                    draw.drawPath(rightPath, 0xFFFFFFFF)

                                    -- 锚点：确保 Canvas 重新测量
                                    draw.drawCircle(cx, cy, 0, 0x00000000)
                                end,
                            }),
                        },
                    }),
                },
            }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Text({
                text = isPlaying.value and "State: PLAYING" or "State: PAUSED",
                fontSize = 14,
                color = 0xFF49454F,
            }),
        },
    })
end)