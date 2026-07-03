-- 11_animate_content_size.lua — AnimationExample1: animateDpAsState 卡片展开/折叠
-- 1:1 复刻 Kotlin 原版，包含标题、箭头指示器、5段文本
local compose = _G.compose

local EXPAND_DURATION_MS = 400
local COLLAPSED_HEIGHT = 160
local EXPANDED_HEIGHT = 330

local isExpanded = compose.state(false)

compose.render(function()
    local expanded = isExpanded.value
    local targetH = expanded and EXPANDED_HEIGHT or COLLAPSED_HEIGHT
    local animatedHeight = compose.animateDpAsState({
        targetValue = targetH,
        animationSpec = compose.tween(EXPAND_DURATION_MS, "FastOutSlowIn"),
        useRecompose = true,
    })

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "animateDpAsState on an expandable card",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Card({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(animatedHeight.value)
                    .clickableLua(function()
                        isExpanded.value = not isExpanded.value
                    end),
                color = 0xFFE7E0EC,
                shape = compose.RoundedCornerShape(12),
                children = {
                    compose.Column({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .padding(20),
                        children = {
                            -- 标题行：文字 + 箭头指示器
                            compose.Row({
                                modifier = compose.Modifier().fillMaxWidth(),
                                children = {
                                    compose.Text({
                                        text = expanded and "Tap to collapse" or "Tap to expand",
                                        fontSize = 14,
                                        fontWeight = 600,
                                        color = 0xFF1C1B1F,
                                        modifier = compose.Modifier().weight(1),
                                    }),
                                    compose.Text({
                                        text = expanded and "v" or ">",
                                        fontSize = 16,
                                        fontWeight = 600,
                                        color = 0xFF1C1B1F,
                                    }),
                                },
                            }),
                            compose.Spacer({ modifier = compose.Modifier().height(8) }),
                            compose.Text({
                                text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                                fontSize = 14,
                                color = 0xFF49454F,
                            }),
                            compose.Text({
                                text = "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
                                fontSize = 14,
                                color = 0xFF49454F,
                            }),
                            compose.Text({
                                text = "Ut enim ad minim veniam, quis nostrud exercitation ullamco.",
                                fontSize = 14,
                                color = 0xFF49454F,
                            }),
                            compose.Text({
                                text = "Duis aute irure dolor in reprehenderit in voluptate velit esse.",
                                fontSize = 14,
                                color = 0xFF49454F,
                            }),
                            compose.Text({
                                text = "Excepteur sint occaecat cupidatat non proident, sunt in culpa.",
                                fontSize = 14,
                                color = 0xFF49454F,
                            }),
                        },
                    }),
                },
            }),
        },
    })
end)