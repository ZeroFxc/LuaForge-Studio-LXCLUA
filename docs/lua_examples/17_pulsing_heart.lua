-- 17_pulsing_heart.lua — AnimationExample7: 心跳脉冲 (InfiniteTransition)
-- 1:1 复刻 Kotlin 原版，"♥" 字符 scale + alpha 组合脉冲
local compose = _G.compose

local PULSE_DURATION_MS = 600
local SCALE_MIN = 0.85
local SCALE_MAX = 1.15
local ALPHA_MIN = 0.6
local ALPHA_MAX = 1.0
local HEART_FONT_SIZE_SP = 120

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Pulsing Heart (rememberInfiniteTransition)",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            -- 外层 InfiniteTransition 驱动 scale
            compose.InfiniteTransition({
                initialValue = SCALE_MIN,
                targetValue = SCALE_MAX,
                durationMs = PULSE_DURATION_MS,
                repeatMode = "Reverse",
                children = function(scale)
                    -- 内层 InfiniteTransition 驱动 alpha
                    return compose.InfiniteTransition({
                        initialValue = ALPHA_MIN,
                        targetValue = ALPHA_MAX,
                        durationMs = PULSE_DURATION_MS,
                        repeatMode = "Reverse",
                        children = function(alpha)
                            return compose.Box({
                                modifier = compose.Modifier()
                                    .fillMaxWidth()
                                    .height(280),
                                children = {
                                    compose.Text({
                                        text = "\u{2665}",  -- ♥
                                        fontSize = HEART_FONT_SIZE_SP,
                                        color = 0xFFE91E63,
                                        modifier = compose.Modifier()
                                            .scale(scale)
                                            .alpha(alpha),
                                    }),
                                },
                            })
                        end,
                    })
                end,
            }),
        },
    })
end)