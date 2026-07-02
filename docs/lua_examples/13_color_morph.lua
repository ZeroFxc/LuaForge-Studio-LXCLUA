-- 13_color_morph.lua — AnimationExample3: animateColorAsState 色板颜色过渡
-- 1:1 复刻 Kotlin 原版，Hero 色块 + 色板选择器 + 亮度自适应文字颜色
local compose = _G.compose

local COLOR_TRANSITION_MS = 600
local PALETTE = {
    { color = 0xFF63CCD9, name = "Coral" },
    { color = 0xFFC6FF00, name = "Lime" },
    { color = 0xFF40C4FF, name = "Sky" },
    { color = 0xFF47CD72, name = "Lavender" },
}
local HERO_HEIGHT_DP = 180

local selectedIndex = compose.state(0)

-- 计算亮度，决定文字颜色（与 Kotlin Color.luminance() 对应）
local function luminance(c)
    local r = ((c >> 16) & 0xFF) / 255.0
    local g = ((c >> 8) & 0xFF) / 255.0
    local b = (c & 0xFF) / 255.0
    return 0.299 * r + 0.587 * g + 0.114 * b
end

compose.render(function()
    local idx = selectedIndex.value
    local selected = PALETTE[idx + 1]

    local animatedBg = compose.animateColorAsState({
        targetValue = selected.color,
        animationSpec = compose.tween(COLOR_TRANSITION_MS),
    })
    local contentColor = luminance(animatedBg.value) > 0.5 and 0xFF202020 or 0xFFFFFFFF

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "animateColorAsState swatches",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            -- Hero 色块
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(HERO_HEIGHT_DP)
                    .clip(compose.RoundedCornerShape(24))
                    .background(animatedBg.value),
                children = {
                    compose.Text({
                        text = selected.name,
                        fontSize = 28,
                        fontWeight = 700,
                        color = contentColor,
                    }),
                },
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            -- 色板选择器
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = (function()
                    local items = {}
                    for i, item in ipairs(PALETTE) do
                        local index = i - 1
                        items[#items + 1] = compose.Column({
                            modifier = compose.Modifier()
                                .weight(1)
                                .clickableLua(function()
                                    selectedIndex.value = index
                                end),
                            children = {
                                compose.Box({
                                    modifier = compose.Modifier()
                                        .size(48, 48)
                                        .clip(compose.CircleShape)
                                        .background(item.color),
                                }),
                                compose.Spacer({ modifier = compose.Modifier().height(6) }),
                                compose.Text({
                                    text = item.name,
                                    fontSize = 12,
                                    color = 0xFF1C1B1F,
                                }),
                            },
                        })
                        if i < #PALETTE then
                            items[#items + 1] = compose.Spacer({ modifier = compose.Modifier().width(12) })
                        end
                    end
                    return items
                end)(),
            }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Text({
                text = "Tap a swatch to morph the hero color.",
                fontSize = 12,
                color = 0xFF49454F,
            }),
        },
    })
end)