-- 22_swipeable_cards.lua — AnimationExample13: 滑动卡片堆叠
-- 1:1 复刻 Kotlin 原版，拖拽卡片左右滑出，后方卡片缩放堆叠
local compose = _G.compose

local SWIPE_THRESHOLD_FRACTION = 0.3
local ROTATION_FACTOR = 0.15
local FLING_STIFFNESS = 300
local FLING_DURATION_MS = 300

local CARD_COLORS = { 0xFFEF5350, 0xFF42A5F5, 0xFF66BB6A, 0xFFFFCA28 }

local topIndex = compose.state(0)
local offsetX = compose.Animatable(0)

compose.render(function()
    local idx = topIndex.value
    local allDone = (idx >= #CARD_COLORS)

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Swipeable Cards",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "Swipe the top card left or right",
                fontSize = 12,
                color = 0xFF49454F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(360),
                children = (function()
                    local nodes = {}

                    if allDone then
                        nodes[#nodes + 1] = compose.Button({
                            text = "All done. Tap to reset",
                            onClick = function()
                                topIndex.value = 0
                                offsetX.snapTo(0)
                            end,
                            modifier = compose.Modifier()
                                .fillMaxWidth()
                                .padding(24, 0),
                        })
                    else
                        -- 后方卡片堆叠
                        for peek = 2, 1, -1 do
                            local peekIndex = idx + peek
                            if peekIndex < #CARD_COLORS then
                                local scale = 1.0 - peek * 0.05
                                local yOff = peek * 12
                                nodes[#nodes + 1] = compose.Card({
                                    modifier = compose.Modifier()
                                        .fillMaxWidth()
                                        .height(280)
                                        .offset(0, yOff)
                                        .graphicsLayer(scale, scale, 1, 0),
                                    color = CARD_COLORS[peekIndex + 1],
                                    children = {
                                        compose.Box({
                                            modifier = compose.Modifier()
                                                .fillMaxWidth()
                                                .padding(32),
                                            children = {
                                                compose.Text({
                                                    text = "Card " .. (peekIndex + 1),
                                                    fontSize = 28,
                                                    fontWeight = 700,
                                                    color = 0xFFFFFFFF,
                                                }),
                                            },
                                        }),
                                    },
                                })
                            end
                        end

                        -- 顶部可拖拽卡片
                        local ox = offsetX.getValue()
                        nodes[#nodes + 1] = compose.Card({
                            modifier = compose.Modifier()
                                .fillMaxWidth()
                                .height(280)
                                .offset(ox, 0)
                                .graphicsLayer(1, 1, 1, ox * ROTATION_FACTOR)
                                .pointerInputFull(
                                    function(x, y) end,
                                    function(dx, dy)
                                        offsetX.snapTo(offsetX.getValue() + dx)
                                    end,
                                    function()
                                        local currentOx = offsetX.getValue()
                                        local threshold = 300 * SWIPE_THRESHOLD_FRACTION
                                        if math.abs(currentOx) > threshold then
                                            local dir = currentOx > 0 and 1 or -1
                                            offsetX.animateTo(dir * 600, compose.tween(FLING_DURATION_MS, "FastOutSlowIn"))
                                            topIndex.value = idx + 1
                                            offsetX.snapTo(0)
                                        else
                                            offsetX.animateTo(0, compose.spring(0.55, FLING_STIFFNESS))
                                        end
                                    end
                                ),
                            color = CARD_COLORS[idx + 1],
                            children = {
                                compose.Box({
                                    modifier = compose.Modifier()
                                        .fillMaxWidth()
                                        .padding(32),
                                    children = {
                                        compose.Text({
                                            text = "Card " .. (idx + 1),
                                            fontSize = 32,
                                            fontWeight = 700,
                                            color = 0xFFFFFFFF,
                                        }),
                                    },
                                }),
                            },
                        })
                    end

                    return nodes
                end)(),
            }),
        },
    })
end)