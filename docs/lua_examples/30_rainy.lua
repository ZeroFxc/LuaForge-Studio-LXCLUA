-- 30_rainy.lua — AnimationExample21: 斜向雨滴效果
-- 1:1 复刻 Kotlin 原版：斜雨+渐变拖尾+触底飞溅+背景渐变
local compose = _G.compose

local DROP_COUNT = 100
local ANGLE_DEG = 100
local SPEED_MIN_DP_PER_SEC = 180
local SPEED_MAX_DP_PER_SEC = 360
local STREAK_LENGTH_MIN_DP = 14
local STREAK_LENGTH_MAX_DP = 28
local STROKE_WIDTH_DP = 1.1
local RAIN_HEAD_COLOR = 0xFFB3E5FC
local RAIN_TAIL_COLOR = 0x00B3E5FC
local SPLASH_RADIUS_DP = 2.5
local SPLASH_COLOR = 0xB3B3E5FC
local BG_TOP = 0xFF0B1420
local BG_BOTTOM = 0xFF152033

local density = compose.LocalDensity.density
local speedMinPx = SPEED_MIN_DP_PER_SEC * density
local speedMaxPx = SPEED_MAX_DP_PER_SEC * density
local streakMinPx = STREAK_LENGTH_MIN_DP * density
local streakMaxPx = STREAK_LENGTH_MAX_DP * density
local strokeWidthPx = STROKE_WIDTH_DP * density
local splashRadiusPx = SPLASH_RADIUS_DP * density

local angleRad = math.rad(ANGLE_DEG)
local dirX = math.cos(angleRad)
local dirY = math.sin(angleRad)

local drops = {}
local initialized = false
local lastTimeSec = 0
local spawnXMin, spawnXMax, spawnXSpan

local function initDrops(w, h)
    local safeDirY = dirY > 0.05 and dirY or 0.05
    local horizontalDrift = math.abs(dirX) * h / safeDirY
    spawnXMin = -horizontalDrift
    spawnXMax = w + horizontalDrift
    spawnXSpan = spawnXMax - spawnXMin

    for i = 1, DROP_COUNT do
        drops[i] = {
            x = spawnXMin + math.random() * spawnXSpan,
            y = math.random() * h,
            speed = speedMinPx + math.random() * (speedMaxPx - speedMinPx),
            length = streakMinPx + math.random() * (streakMaxPx - streakMinPx),
        }
    end
    initialized = true
end

local function resetDrop(d, h)
    d.x = spawnXMin + math.random() * spawnXSpan
    d.y = -d.length - math.random() * h * 0.3
    d.speed = speedMinPx + math.random() * (speedMaxPx - speedMinPx)
    d.length = streakMinPx + math.random() * (streakMaxPx - streakMinPx)
end

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Diagonal Rain Effect",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.Text({
                text = "Tilted rain with splash particles.",
                fontSize = 12,
                color = 0xFF49454F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(380)
                    .clip(compose.RoundedCornerShape(16))
                    .background(BG_TOP),
                children = {
                    compose.Canvas({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .height(380),
                        continuousRedraw = true,
                        onDraw = function(draw, w, h, timeSec)
                            if not initialized then
                                initDrops(w, h)
                            end

                            -- 计算dt
                            local dt = lastTimeSec > 0 and (timeSec - lastTimeSec) or 0
                            if dt > 0.05 then dt = 0.05 end
                            lastTimeSec = timeSec

                            -- 更新物理
                            if dt > 0 then
                                for _, d in ipairs(drops) do
                                    d.x = d.x + dirX * d.speed * dt
                                    d.y = d.y + dirY * d.speed * dt
                                    if d.y - d.length > h or d.x + d.length < spawnXMin or d.x - d.length > spawnXMax then
                                        resetDrop(d, h)
                                    end
                                end
                            end

                            -- 绘制背景渐变
                            draw.drawRectVerticalGradient(0, 0, w, h, BG_TOP, BG_BOTTOM)

                            -- 绘制雨滴
                            for _, d in ipairs(drops) do
                                local headX = d.x
                                local headY = d.y
                                local tailX = headX - dirX * d.length
                                local tailY = headY - dirY * d.length

                                -- 渐变线：尾透明→头明亮
                                draw.drawLine(
                                    tailX, tailY,
                                    headX, headY,
                                    RAIN_TAIL_COLOR, RAIN_HEAD_COLOR,
                                    strokeWidthPx
                                )

                                -- 触底瞬间飞溅小圆点
                                if headY >= h - 2 and headY <= h + 2 then
                                    draw.drawCircle(headX, h - 1, splashRadiusPx, SPLASH_COLOR)
                                end
                            end
                        end,
                    }),
                },
            }),
        },
    })
end)