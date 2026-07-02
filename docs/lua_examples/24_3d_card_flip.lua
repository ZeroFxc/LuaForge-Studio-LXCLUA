-- 24_3d_card_flip.lua — AnimationExample15: 3D 卡片翻转 (Y轴旋转 + 透视)
-- 1:1 复刻 Kotlin 原版，graphicsLayerRotationY 控制翻转，前后内容切换
local compose = _G.compose

local FLIP_DURATION_MS = 700
local CAMERA_DISTANCE_FACTOR = 12
local FRONT_COLOR = 0xFFE91E63
local BACK_COLOR = 0xFFFF7043

local flipped = compose.state(false)

compose.render(function()
    local rotation = compose.animateFloatAsState({
        targetValue = flipped.value and 180.0 or 0.0,
        useRecompose = true,
        animationSpec = compose.tween(FLIP_DURATION_MS, "FastOutSlowIn"),
    })
    local rot = rotation:getValue()
    local isFront = rot <= 90

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "3D Card Flip",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "Tap the card to flip",
                fontSize = 12,
                color = 0xFF49454F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(320),
                children = {
                    compose.Card({
                        modifier = compose.Modifier()
                            .size(240, 240)
                            .graphicsLayerRotationY(rot, CAMERA_DISTANCE_FACTOR)
                            .clickableLua(function()
                                flipped.value = not flipped.value
                            end),
                        color = isFront and FRONT_COLOR or BACK_COLOR,
                        children = {
                            compose.Box({
                                modifier = compose.Modifier()
                                    .fillMaxWidth()
                                    .padding(24),
                                children = (function()
                                    if isFront then
                                        return {
                                            compose.Column({
                                                children = {
                                                    compose.Text({
                                                        text = "\u{2665}",  -- ♥
                                                        fontSize = 96,
                                                        color = 0xFFFFFFFF,
                                                    }),
                                                    compose.Text({
                                                        text = "Compose",
                                                        fontSize = 22,
                                                        fontWeight = 700,
                                                        color = 0xFFFFFFFF,
                                                    }),
                                                },
                                            }),
                                        }
                                    else
                                        return {
                                            compose.Column({
                                                modifier = compose.Modifier()
                                                    .graphicsLayerRotationY(180, CAMERA_DISTANCE_FACTOR),
                                                children = {
                                                    compose.Text({
                                                        text = "\u{2726}",  -- ✦
                                                        fontSize = 96,
                                                        color = 0xFFFFFFFF,
                                                    }),
                                                    compose.Text({
                                                        text = "Saved!",
                                                        fontSize = 22,
                                                        fontWeight = 700,
                                                        color = 0xFFFFFFFF,
                                                    }),
                                                },
                                            }),
                                        }
                                    end
                                end)(),
                            }),
                        },
                    }),
                },
            }),
        },
    })
end)