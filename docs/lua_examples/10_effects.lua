-- ============================================================
-- Nirithy LuaCompose 官方教程 10 — 副作用与协程
-- ============================================================
-- 目标：学会 LaunchedEffect、key、DisposableEffect、startTimer 高精度定时器
-- 覆盖：LaunchedEffect, key, DisposableEffect, startTimer
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- ⚠️ 所有需触发 UI 更新的状态必须使用 state()，mutableState 变更不会触发 recomposition
local counter = compose.state(0)
local effectKey = compose.state(1)
-- 高精度定时器值（Kotlin 协程驱动，每 100ms 递增）
local timerValue = compose.state(0.0)
-- 定时器是否正在运行
local timerRunning = compose.state(false)
local timerObj = nil  -- 定时器对象引用，用于停止

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24)
            .verticalScroll(),

        children = {

            compose.Text({
                text = "副作用与协程示例",
                fontSize = 22,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- ===== 1. LaunchedEffect 启动协程 =====
            compose.Text({ text = "1. LaunchedEffect 协程启动:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Text({
                text = "LaunchedEffect 在 Composable 进入组合时启动协程。\n"
                    .. "当 key 变化时，旧的协程取消，新的协程启动。",
                fontSize = 13,
                color = 0xFF666666,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            -- LaunchedEffect: key 变化时递增计数器
            compose.LaunchedEffect({
                key = effectKey.value,
                block = function()
                    counter.value = counter.value + 1
                end,
            }),

            compose.Text({
                textLambda = function()
                    return "计数器: " .. counter.value
                end,
                fontSize = 24,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),

            compose.Text({
                textLambda = function()
                    return "当前 key: " .. effectKey.value
                end,
                fontSize = 14,
                color = 0xFF999999,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Button({
                        text = "计数 +1",
                        onClick = function()
                            counter.value = counter.value + 1
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(4) }),
                    compose.Button({
                        text = "切换 key",
                        onClick = function()
                            effectKey.value = effectKey.value + 1
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 2. key 组件 =====
            compose.Text({ text = "2. key 组件:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Text({
                text = "key 组件控制子组件的重组范围。\n"
                    .. "当 key 变化时，子组件被销毁并重建。",
                fontSize = 13,
                color = 0xFF666666,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.key({
                key = effectKey.value,
                children = {
                    compose.Card({
                        modifier = compose.Modifier().fillMaxWidth(),
                        color = effectKey.value % 2 == 0 and 0xFFE8DEF8 or 0xFFFFF3E0,
                        children = {
                            compose.Text({
                                text = "key = " .. effectKey.value .. "\n"
                                    .. "这个 Card 在 key 变化时会被重建。",
                                fontSize = 14,
                                modifier = compose.Modifier().padding(16),
                            }),
                        },
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 3. DisposableEffect 清理副作用 =====
            compose.Text({ text = "3. DisposableEffect 清理副作用:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Text({
                text = "DisposableEffect 在组件离开组合时执行清理逻辑。\n"
                    .. "切换 key 时，旧的 DisposableEffect 会调用 onDispose 清理。",
                fontSize = 13,
                color = 0xFF666666,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.DisposableEffect({
                key = effectKey.value,
                effect = function()
                    -- effect 函数执行初始化，返回 onDispose 清理函数
                    return function()
                        counter.value = 0
                    end
                end,
                children = {
                    compose.Text({
                        text = "DisposableEffect 活跃中 (key=" .. effectKey.value .. ")",
                        fontSize = 14,
                        color = 0xFF4CAF50,
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 4. 高精度定时器 (Kotlin 协程驱动) =====
            compose.Text({ text = "4. startTimer 高精度定时器:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Text({
                text = "compose.startTimer 使用 Kotlin 协程的 delay 实现高精度定时。\n"
                    .. "每 100ms 触发一次回调，更新 timerValue。",
                fontSize = 13,
                color = 0xFF666666,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Text({
                textLambda = function()
                    return "定时器: " .. string.format("%.1f", timerValue.value) .. "s"
                end,
                fontSize = 24,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Button({
                        text = timerRunning.value and "暂停" or "开始计时",
                        onClick = function()
                            if timerRunning.value then
                                -- 停止定时器
                                if timerObj then
                                    timerObj.stop()
                                    timerObj = nil
                                end
                                timerRunning.value = false
                            else
                                -- 启动高精度定时器（Kotlin 协程驱动）
                                timerRunning.value = true
                                timerObj = compose.startTimer(100, function()
                                    timerValue.value = timerValue.value + 0.1
                                end)
                            end
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(4) }),
                    compose.Button({
                        text = "重置",
                        onClick = function()
                            if timerObj then
                                timerObj.stop()
                                timerObj = nil
                            end
                            timerRunning.value = false
                            timerValue.value = 0
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 5. remember 记忆化 =====
            compose.Text({ text = "5. remember 记忆化:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Text({
                text = "remember 在重组之间保持值不变。\n"
                    .. "只有 key 变化时才重新计算。",
                fontSize = 13,
                color = 0xFF666666,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Text({
                textLambda = function()
                    return "remember 缓存的 key 是 effectKey (" .. effectKey.value .. ")。\n"
                        .. "切换 key 时，缓存的值会重新计算。"
                end,
                fontSize = 13,
                color = 0xFF999999,
            }),

            -- 底部留白
            compose.Spacer({ modifier = compose.Modifier().height(32) }),
        },
    })
end)