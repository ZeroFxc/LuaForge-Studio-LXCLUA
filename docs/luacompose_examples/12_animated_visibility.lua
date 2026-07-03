-- 12_animated_visibility.lua — AnimationExample2: AnimatedVisibility slide/fade/scale
-- 1:1 复刻 Kotlin 原版，三列并排展示三种进出场动画
local compose = _G.compose

local visible = compose.state(true)

compose.render(function()
    local isVisible = visible.value

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "AnimatedVisibility: slide / fade / scale",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Button({
                text = isVisible and "Hide all" or "Show all",
                onClick = function()
                    visible.value = not visible.value
                end,
                modifier = compose.Modifier().fillMaxWidth(),
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    -- SLIDE 列（slide + fade 组合）
                    compose.Column({
                        modifier = compose.Modifier().weight(1),
                        children = {
                            compose.Text({
                                text = "SLIDE",
                                fontSize = 12,
                                fontWeight = 600,
                                color = 0xFF49454F,
                            }),
                            compose.Spacer({ modifier = compose.Modifier().height(8) }),
                            compose.Box({
                                modifier = compose.Modifier()
                                    .fillMaxWidth()
                                    .height(96),
                                children = {
                                    compose.AnimatedVisibility({
                                        visible = isVisible,
                                        enter = compose.fadeInSlide(),
                                        exit = compose.fadeOutSlide(),
                                        children = {
                                            compose.Card({
                                                color = 0xFFEADDFF,
                                                shape = compose.RoundedCornerShape(12),
                                                children = {
                                                    compose.Text({
                                                        text = "SLIDE",
                                                        fontSize = 14,
                                                        fontWeight = 700,
                                                        color = 0xFF21005D,
                                                        modifier = compose.Modifier().padding(18, 14),
                                                    }),
                                                },
                                            }),
                                        },
                                    }),
                                },
                            }),
                        },
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(12) }),
                    -- FADE 列（纯淡入淡出）
                    compose.Column({
                        modifier = compose.Modifier().weight(1),
                        children = {
                            compose.Text({
                                text = "FADE",
                                fontSize = 12,
                                fontWeight = 600,
                                color = 0xFF49454F,
                            }),
                            compose.Spacer({ modifier = compose.Modifier().height(8) }),
                            compose.Box({
                                modifier = compose.Modifier()
                                    .fillMaxWidth()
                                    .height(96),
                                children = {
                                    compose.AnimatedVisibility({
                                        visible = isVisible,
                                        enter = compose.fadeIn(),
                                        exit = compose.fadeOut(),
                                        children = {
                                            compose.Card({
                                                color = 0xFFEADDFF,
                                                shape = compose.RoundedCornerShape(12),
                                                children = {
                                                    compose.Text({
                                                        text = "FADE",
                                                        fontSize = 14,
                                                        fontWeight = 700,
                                                        color = 0xFF21005D,
                                                        modifier = compose.Modifier().padding(18, 14),
                                                    }),
                                                },
                                            }),
                                        },
                                    }),
                                },
                            }),
                        },
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(12) }),
                    -- SCALE 列（scale + fade 组合）
                    compose.Column({
                        modifier = compose.Modifier().weight(1),
                        children = {
                            compose.Text({
                                text = "SCALE",
                                fontSize = 12,
                                fontWeight = 600,
                                color = 0xFF49454F,
                            }),
                            compose.Spacer({ modifier = compose.Modifier().height(8) }),
                            compose.Box({
                                modifier = compose.Modifier()
                                    .fillMaxWidth()
                                    .height(96),
                                children = {
                                    compose.AnimatedVisibility({
                                        visible = isVisible,
                                        enter = compose.fadeInScale(),
                                        exit = compose.fadeOutScale(),
                                        children = {
                                            compose.Card({
                                                color = 0xFFEADDFF,
                                                shape = compose.RoundedCornerShape(12),
                                                children = {
                                                    compose.Text({
                                                        text = "SCALE",
                                                        fontSize = 14,
                                                        fontWeight = 700,
                                                        color = 0xFF21005D,
                                                        modifier = compose.Modifier().padding(18, 14),
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
            }),
        },
    })
end)