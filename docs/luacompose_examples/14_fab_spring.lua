-- 14_fab_spring.lua — AnimationExample4: FAB 弹簧变形（大小/旋转/圆角/颜色）
-- 1:1 复刻 Kotlin 原版，四属性并行弹簧动画
local compose = _G.compose

local collapsedSize = 74
local expandedSize = 196
local collapsedRotation = 0
local expandedRotation = 135
local expandedCornerDp = 24

local morphed = compose.state(false)

compose.render(function()
    local isMorphed = morphed.value

    local size = compose.animateDpAsState({
        targetValue = isMorphed and expandedSize or collapsedSize,
        animationSpec = compose.spring(0.5, 300),
    })
    local rotation = compose.animateFloatAsState({
        targetValue = isMorphed and expandedRotation or collapsedRotation,
        animationSpec = compose.spring(0.5, 300),
    })
    local cornerDp = compose.animateDpAsState({
        targetValue = isMorphed and expandedCornerDp or (collapsedSize / 2),
        animationSpec = compose.spring(0.5, 300),
    })
    local color = compose.animateColorAsState({
        targetValue = isMorphed and 0xFF625B71 or 0xFF6750A4,
        animationSpec = compose.spring(0.5, 300),
    })

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "FAB Spring Morph",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(240),
                children = {
                    compose.Box({
                        modifier = compose.Modifier()
                            .size(size.value, size.value)
                            .rotate(rotation.value)
                            .clip(compose.RoundedCornerShape(cornerDp.value))
                            .background(color.value, compose.RoundedCornerShape(cornerDp.value))
                            .clickableLua(function()
                                morphed.value = not morphed.value
                            end),
                        children = {
                            compose.Text({
                                text = "+",
                                fontSize = 32,
                                fontWeight = 700,
                                color = 0xFFFFFFFF,
                            }),
                        },
                    }),
                },
            }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Text({
                text = "Tap to morph",
                fontSize = 14,
                fontWeight = 600,
                color = 0xFF1C1B1F,
                modifier = compose.Modifier().fillMaxWidth(),
            }),
        },
    })
end)