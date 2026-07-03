-- 16_crossfade.lua — AnimationExample6: Crossfade 标签页切换
-- 1:1 复刻 Kotlin 原版，三个标签(Button/OutlinedButton) + Crossfade 内容
local compose = _G.compose

local crossfadeDurationMs = 6600

local color1 = 0xFFFFB74D
local color2 = 0xFF4FC3F7
local color3 = 0xFF5C6BC0

local tabs = {
    { emoji = "\u{1F305}", name = "Morning", color = color1 },  -- 🌅
    { emoji = "\u{1F31E}", name = "Noon",    color = color2 },  -- 🌞
    { emoji = "\u{1F319}", name = "Night",   color = color3 },  -- 🌙
}
local selected = compose.state(0)

compose.render(function()
    local sel = selected.value
    local tab = tabs[sel + 1]

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Crossfade Tab Switcher",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            -- 标签栏
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = (function()
                    local items = {}
                    for i, t in ipairs(tabs) do
                        local index = i - 1
                        local isSel = (index == sel)
                        if isSel then
                            items[#items + 1] = compose.Button({
                                text = t.emoji .. " " .. t.name,
                                onClick = function()
                                    selected.value = index
                                end,
                                modifier = compose.Modifier()
                                    .weight(1)
                                    .height(44),
                            })
                        else
                            items[#items + 1] = compose.OutlinedButton({
                                text = t.emoji .. " " .. t.name,
                                onClick = function()
                                    selected.value = index
                                end,
                                modifier = compose.Modifier()
                                    .weight(1)
                                    .height(44),
                            })
                        end
                        if i < #tabs then
                            items[#items + 1] = compose.Spacer({ modifier = compose.Modifier().width(8) })
                        end
                    end
                    return items
                end)(),
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            -- Crossfade 内容
            compose.Crossfade({
                targetState = sel,
                durationMs = crossfadeDurationMs,
                children = {
                    compose.Surface({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .height(260),
                        color = tab.color,
                        shape = compose.RoundedCornerShape(20),
                        children = {
                            compose.Box({
                                modifier = compose.Modifier()
                                    .fillMaxWidth()
                                    .padding(24),
                                children = {
                                    compose.Column({
                                        children = {
                                            compose.Text({
                                                text = tab.emoji,
                                                fontSize = 96,
                                            }),
                                            compose.Spacer({ modifier = compose.Modifier().height(12) }),
                                            compose.Text({
                                                text = tab.name,
                                                fontSize = 36,
                                                fontWeight = 700,
                                                color = 0xFFFFFFFF,
                                            }),
                                        },
                                    }),
                                },
                            }),
                        },
                    }),
                },
            }),
        },
    })
end)