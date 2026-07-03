-- 19_spring_drag.lua — AnimationExample9: 弹簧拖拽方块
-- 1:1 复刻 Kotlin 原版：拖拽瞬时跟手 + 松手弹簧回弹
local compose = _G.compose

local SPRING_STIFFNESS = 600
local SPRING_DAMPING_RATIO = 0.55
local BOX_COLOR = 0xFF26A69A
local BOX_SIZE_DP = 80
local BOX_CORNER_DP = 16

-- 当前偏移（像素单位），直接驱动 UI
local offsetX = compose.state(0.0)
local offsetY = compose.state(0.0)

-- 弹簧物理状态
local springVx = 0
local springVy = 0
local springTimer = nil
local springDamping = 2 * SPRING_DAMPING_RATIO * math.sqrt(SPRING_STIFFNESS)

-- 停止回弹计时器
local function stopSpring()
    if springTimer then
        springTimer.stop()
        springTimer = nil
    end
end

-- 开始弹簧回弹
local function startSpring()
    stopSpring()
    springVx = 0
    springVy = 0
    springTimer = compose.startTimer(16, function()
        local dt = 0.016
        local ox = offsetX.value
        local oy = offsetY.value

        -- 弹簧阻尼物理：fx = -k*x - c*v
        local fx = -SPRING_STIFFNESS * ox - springDamping * springVx
        local fy = -SPRING_STIFFNESS * oy - springDamping * springVy
        springVx = springVx + fx * dt
        springVy = springVy + fy * dt
        ox = ox + springVx * dt
        oy = oy + springVy * dt

        -- 接近静止时停止
        if math.abs(ox) < 0.5 and math.abs(oy) < 0.5 and
           math.abs(springVx) < 5 and math.abs(springVy) < 5 then
            offsetX.value = 0
            offsetY.value = 0
            stopSpring()
            return
        end

        offsetX.value = ox
        offsetY.value = oy
    end)
end

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Spring Drag Box",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "Drag me, then release",
                fontSize = 12,
                color = 0xFF666666,
            }),
            compose.Text({
                textLambda = function()
                    return "offset = (" .. math.floor(offsetX.value) .. ", " .. math.floor(offsetY.value) .. ")"
                end,
                fontSize = 10,
                color = 0xFF999999,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(280),
                contentAlignment = "Center",
                children = {
                    compose.Box({
                        modifier = compose.Modifier()
                            -- offsetLambda 动态读取 state 值（px单位），state变化触发重组
                            .offsetLambda(function()
                                return { x = offsetX.value, y = offsetY.value }
                            end)
                            .size(BOX_SIZE_DP, BOX_SIZE_DP)
                            .clip(compose.RoundedCornerShape(BOX_CORNER_DP))
                            .background(BOX_COLOR)
                            .pointerInputFull(
                                -- onDragStart：停止回弹，准备拖拽
                                function(x, y)
                                    stopSpring()
                                    springVx = 0
                                    springVy = 0
                                end,
                                -- onDrag：累加像素增量，瞬时跟手
                                function(dx, dy)
                                    offsetX.value = offsetX.value + dx
                                    offsetY.value = offsetY.value + dy
                                end,
                                -- onDragEnd：松手，开始弹簧回弹
                                function()
                                    startSpring()
                                end
                            ),
                    }),
                },
            }),
        },
    })
end)