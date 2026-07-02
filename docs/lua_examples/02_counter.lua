-- ============================================================
-- Nirithy LuaCompose 官方教程 02 — 状态管理
-- ============================================================
-- 目标：学会 mutableState 状态管理、Button 点击事件、计数器
-- 覆盖：mutableState, Button, Row, Column, Spacer, onClick, Modifier.weight
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- ⚠️ State 必须在 render 函数外面声明，否则每次重绘都会重置
-- ⚠️ mutableState 触发轻量重组（recomposeTrigger++），ComposeNode 访问器自动解包 StateWrapper
local count = compose.mutableState(0)

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24),

        children = {
            compose.Text({
                text = "计数器示例",
                fontSize = 22,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ⚠️ 格式化文本使用 textLambda 回调，在渲染时读取最新值
            compose.Text({
                textLambda = function()
                    return "当前计数: " .. count.value
                end,
                fontSize = 32,
                fontWeight = 700,
                color = 0xFF333333,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- 按钮行
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Button({
                        text = "+1",
                        onClick = function()
                            count.value = count.value + 1
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),

                    compose.Spacer({ modifier = compose.Modifier().width(8) }),

                    compose.Button({
                        text = "-1",
                        onClick = function()
                            count.value = count.value - 1
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),

                    compose.Spacer({ modifier = compose.Modifier().width(8) }),

                    compose.Button({
                        text = "重置",
                        onClick = function()
                            count.value = 0
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),
                },
            }),
        },
    })
end)