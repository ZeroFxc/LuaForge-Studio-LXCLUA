-- 23_radial_fab.lua — AnimationExample14: 径向 FAB 卫星菜单
-- 1:1 复刻 Kotlin 原版，弧形排列卫星按钮，弹簧交错动画
local compose = _G.compose

local ITEM_COUNT = 4
local RADIUS_DP = 110
local STAGGER_MS = 50
local SPRING_STIFFNESS = 500
local SPRING_DAMPING = 0.55
local ARC_DEG = 180

local palette = { 0xFFEF5350, 0xFF42A5F5, 0xFF66BB6A, 0xFFFFCA28 }
local labels = { "\u{2605}", "\u{2665}", "\u{2726}", "\u{273F}" }  -- ★♥✦✿

local isOpen = compose.state(false)
local progresses = {}
for i = 1, ITEM_COUNT do
    progresses[i] = compose.Animatable(0.0)
end
local fabRotation = compose.Animatable(0.0)

-- 交错动画：展开/关闭时依次启动卫星按钮
local animating = compose.state(false)
local function animateMenu(open)
    if animating.value then return end
    animating.value = true
    if open then
        fabRotation.animateTo(45.0, compose.spring(SPRING_DAMPING, SPRING_STIFFNESS))
        for i = 1, ITEM_COUNT do
            compose.startTimer((i - 1) * STAGGER_MS, function()
                progresses[i].animateTo(1.0, compose.spring(SPRING_DAMPING, SPRING_STIFFNESS))
            end)
        end
        compose.startTimer(ITEM_COUNT * STAGGER_MS + 100, function()
            animating.value = false
        end)
    else
        fabRotation.animateTo(0.0, compose.spring(SPRING_DAMPING, SPRING_STIFFNESS))
        for i = ITEM_COUNT, 1, -1 do
            compose.startTimer((ITEM_COUNT - i) * STAGGER_MS, function()
                progresses[i].animateTo(0.0, compose.spring(SPRING_DAMPING, SPRING_STIFFNESS))
            end)
        end
        compose.startTimer(ITEM_COUNT * STAGGER_MS + 100, function()
            animating.value = false
        end)
    end
end

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Radial FAB Menu",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "Tap the center. Satellites scatter with staggered spring physics.",
                fontSize = 12,
                color = 0xFF49454F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(320),
                children = (function()
                    local nodes = {}

                    -- 卫星按钮
                    for i = 1, ITEM_COUNT do
                        local angleDeg
                        if ITEM_COUNT == 1 then
                            angleDeg = 90
                        else
                            local sweepStart = 180 + (180 - ARC_DEG) / 2
                            angleDeg = sweepStart + (i - 1) / (ITEM_COUNT - 1) * ARC_DEG
                        end
                        local rad = math.rad(angleDeg)
                        local targetX = math.cos(rad) * RADIUS_DP
                        local targetY = math.sin(rad) * RADIUS_DP

                        local v = progresses[i].getValue()
                        local tx = targetX * v
                        local ty = targetY * v
                        local s = 0.4 + 0.6 * v
                        local a = v

                        nodes[#nodes + 1] = compose.Box({
                            modifier = compose.Modifier()
                                .size(56, 56)
                                .offset(tx, ty)
                                .graphicsLayer(s, s, a, 0)
                                .clip(compose.CircleShape)
                                .background(palette[i])
                                .clickableLua(function()
                                    if isOpen.value then
                                        isOpen.value = false
                                        animateMenu(false)
                                    end
                                end),
                            children = {
                                compose.Text({
                                    text = labels[i],
                                    fontSize = 24,
                                    fontWeight = 700,
                                    color = 0xFFFFFFFF,
                                }),
                            },
                        })
                    end

                    -- 中心 FAB
                    nodes[#nodes + 1] = compose.Box({
                        modifier = compose.Modifier()
                            .size(72, 72)
                            .clip(compose.CircleShape)
                            .background(0xFF6750A4)
                            .clickableLua(function()
                                isOpen.value = not isOpen.value
                                animateMenu(isOpen.value)
                            end),
                        children = {
                            compose.Text({
                                text = "+",
                                fontSize = 36,
                                fontWeight = 700,
                                color = 0xFFFFFFFF,
                                modifier = compose.Modifier()
                                    .graphicsLayer(1, 1, 1, fabRotation.getValue()),
                            }),
                        },
                    })

                    return nodes
                end)(),
            }),
        },
    })
end)