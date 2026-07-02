-- 15_animated_counter.lua — AnimationExample5: AnimatedContent 计数器
-- 1:1 复刻 Kotlin 原版，根据增减方向使用 slideInVertically/slideOutVertically
local compose = _G.compose

local slideDurationMs = 650
local fadeDurationMs = 450
local slideOffsetDivisor = 1

local count = compose.state(0)
local prevCount = compose.state(0)

compose.render(function()
    local current = count.value
    local previous = prevCount.value

    -- 根据增减方向选择 transitionSpec
    local goingUp = current > previous
    local spec = goingUp and "up" or "down"

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "AnimatedContent Counter",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(160),
                children = {
                    compose.AnimatedContent({
                        targetState = current,
                        transitionSpec = spec,
                        durationMs = slideDurationMs,
                        children = {
                            compose.Text({
                                text = tostring(current),
                                fontSize = 96,
                                fontWeight = 700,
                                color = 0xFF6750A4,
                            }),
                        },
                    }),
                },
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Button({
                        text = "-",
                        onClick = function()
                            prevCount.value = current
                            count.value = current - 1
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(16) }),
                    compose.Button({
                        text = "+",
                        onClick = function()
                            prevCount.value = current
                            count.value = current + 1
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),
                },
            }),
        },
    })
end)