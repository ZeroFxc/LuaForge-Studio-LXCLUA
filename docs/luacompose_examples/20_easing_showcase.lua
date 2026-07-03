-- 20_easing_showcase.lua — AnimationExample10: 缓动函数赛跑 (animateFloatAsState)
-- 1:1 复刻 Kotlin 原版，6条赛道使用不同 easing 的 tween 比较曲线
local compose = _G.compose

local RACE_DURATION_MS = 2500
local TRACK_HEIGHT_DP = 42
local RUNNER_SIZE_DP = 34

local entries = {
    { name = "LinearEasing",         color = 0xFFEF5350, easing = "Linear" },
    { name = "FastOutSlowInEasing",  color = 0xFFAB47BC, easing = "FastOutSlowIn" },
    { name = "FastOutLinearInEasing",color = 0xFF42A5F5, easing = "FastOutLinearIn" },
    { name = "LinearOutSlowInEasing",color = 0xFF26A69A, easing = "LinearOutSlowIn" },
    { name = "EaseInOutCubic",       color = 0xFFFFA726, easing = "EaseInOutCubic" },
    { name = "EaseOutBounce",        color = 0xFF8D6E63, easing = "EaseOutBounce" },
}

local progress = compose.state(0.0)

compose.render(function()
    local p = progress.value

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Easing Showcase",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "Tap Race! to compare easings side by side.",
                fontSize = 12,
                color = 0xFF49454F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            -- 赛道列表
            compose.BoxWithConstraints({
                modifier = compose.Modifier().fillMaxWidth(),
                children = (function()
                    local tracks = {}
                    for i, entry in ipairs(entries) do
                        local anim = compose.animateFloatAsState({
                            targetValue = p,
                            useRecompose = true,
                            animationSpec = compose.tween(RACE_DURATION_MS, entry.easing),
                        })

                        tracks[#tracks + 1] = compose.Text({
                            text = entry.name,
                            fontSize = 12,
                            fontWeight = 600,
                            color = 0xFF555555,
                        })
                        tracks[#tracks + 1] = compose.Spacer({ modifier = compose.Modifier().height(4) })

                        -- 赛道背景 + 跑者
                        tracks[#tracks + 1] = compose.Box({
                            modifier = compose.Modifier()
                                .fillMaxWidth()
                                .height(TRACK_HEIGHT_DP)
                                .background(0xFFE7E0EC)
                                .clip(compose.RoundedCornerShape(TRACK_HEIGHT_DP / 2)),
                            children = {
                                compose.Box({
                                    modifier = compose.Modifier()
                                        .offset(anim.getValue() * 280, 4)
                                        .size(RUNNER_SIZE_DP, RUNNER_SIZE_DP)
                                        .clip(compose.CircleShape)
                                        .background(entry.color),
                                }),
                            },
                        })
                        tracks[#tracks + 1] = compose.Spacer({ modifier = compose.Modifier().height(6) })
                    end
                    return tracks
                end)(),
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Button({
                text = p == 0 and "Race!" or "Reset",
                onClick = function()
                    progress.value = p == 0 and 1.0 or 0.0
                end,
                modifier = compose.Modifier().fillMaxWidth(),
            }),
        },
    })
end)