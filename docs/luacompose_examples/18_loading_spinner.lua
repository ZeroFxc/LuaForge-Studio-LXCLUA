-- 18_loading_spinner.lua — AnimationExample8: 自定义旋转加载器
-- 1:1 复刻 Kotlin 原版：InfiniteTransition 旋转+扫角 + Canvas 绘制 Material 风格加载圈
local compose = _G.compose

local ROTATION_DURATION_MS = 1400
local SWEEP_DURATION_MS = 1200
local SPINNER_COLOR = 0xFF1A94D2
local SPINNER_STROKE_DP = 15
local SPINNER_SIZE_DP = 163
local SWEEP_MIN = 10
local SWEEP_MAX = 290

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxWidth()
            .padding(16),
        children = {
            compose.Text({
                text = "Custom Loading Spinner",
                fontSize = 16,
                fontWeight = 600,
                color = 0xFF1C1B1F,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            -- 外层：旋转 0→360，Restart 持续旋转，LinearEasing 匀速
            compose.InfiniteTransition({
                initialValue = 0.0,
                targetValue = 360.0,
                durationMs = ROTATION_DURATION_MS,
                repeatMode = "Restart",
                easing = "Linear",
                children = function(rotation)
                    -- 内层：扫角 10→290，Reverse 来回扫描，LinearEasing 匀速
                    return compose.InfiniteTransition({
                        initialValue = SWEEP_MIN,
                        targetValue = SWEEP_MAX,
                        durationMs = SWEEP_DURATION_MS,
                        repeatMode = "Reverse",
                        easing = "Linear",
                        children = function(sweep)
                            return compose.Box({
                                modifier = compose.Modifier()
                                    .fillMaxWidth()
                                    .height(280),
                                contentAlignment = "Center",
                                children = {
                                    compose.Canvas({
                                        modifier = compose.Modifier()
                                            .size(SPINNER_SIZE_DP, SPINNER_SIZE_DP),
                                        onDraw = function(draw, w, h, timeSec)
                                            local density = w / SPINNER_SIZE_DP
                                            local strokePx = SPINNER_STROKE_DP * density
                                            local inset = strokePx / 2
                                            local arcSize = w - strokePx

                                            -- save/restore 保护画布状态，rotate 绕中心旋转
                                            draw.save()
                                            draw.rotate(rotation, w / 2, h / 2)
                                            draw.drawArcStroke(
                                                inset, inset,
                                                inset + arcSize, inset + arcSize,
                                                0, sweep,
                                                SPINNER_COLOR,
                                                strokePx,
                                                "round"
                                            )
                                            draw.restore()
                                        end,
                                    }),
                                },
                            })
                        end,
                    })
                end,
            }),
        },
    })
end)