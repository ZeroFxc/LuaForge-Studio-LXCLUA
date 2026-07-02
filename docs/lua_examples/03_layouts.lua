-- ============================================================
-- Nirithy LuaCompose 官方教程 03 — 布局
-- ============================================================
-- 目标：学会 Column/Row/Box/Spacer 布局，Modifier 链式调用
-- 覆盖：Column, Row, Box, Spacer, verticalArrangement, horizontalArrangement
--       fillMaxSize, fillMaxWidth, fillMaxHeight, weight, background, size
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24)
            .verticalScroll(),

        -- verticalArrangement: "Top" | "Center" | "Bottom" | "SpaceBetween" | "SpaceEvenly" | "SpaceAround"
        children = {

            compose.Text({
                text = "布局示例",
                fontSize = 22,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- ===== Row 水平排列 =====
            compose.Text({ text = "1. Row 水平排列:", fontSize = 16, fontWeight = 600 }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth().height(60),
                children = {
                    compose.Box({
                        modifier = compose.Modifier()
                            .weight(1)
                            .fillMaxHeight()
                            .background(0xFF2196F3),
                    }),
                    compose.Box({
                        modifier = compose.Modifier()
                            .weight(1)
                            .fillMaxHeight()
                            .background(0xFF4CAF50),
                    }),
                    compose.Box({
                        modifier = compose.Modifier()
                            .weight(1)
                            .fillMaxHeight()
                            .background(0xFFFF9800),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- ===== Column 垂直排列 =====
            compose.Text({ text = "2. Column 垂直排列:", fontSize = 16, fontWeight = 600 }),
            compose.Column({
                modifier = compose.Modifier().fillMaxWidth().height(150),
                children = {
                    compose.Box({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .weight(1)
                            .background(0xFFE91E63),
                    }),
                    compose.Box({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .weight(1)
                            .background(0xFF9C27B0),
                    }),
                    compose.Box({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .weight(1)
                            .background(0xFF3F51B5),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- ===== Box 层叠排列 =====
            compose.Text({ text = "3. Box 层叠排列:", fontSize = 16, fontWeight = 600 }),
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(100)
                    .background(0xFFE0E0E0),
                children = {
                    compose.Text({
                        text = "底层文字",
                        fontSize = 14,
                        modifier = compose.Modifier().padding(8),
                    }),
                    compose.Box({
                        modifier = compose.Modifier()
                            .size(40, 40)
                            .background(0xFFFF5722),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- ===== Spacer 间距 =====
            compose.Text({ text = "4. Spacer 间距:", fontSize = 16, fontWeight = 600 }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Text({ text = "左", fontSize = 14 }),
                    compose.Spacer({ modifier = compose.Modifier().weight(1) }),
                    compose.Text({ text = "右", fontSize = 14 }),
                },
            }),
        },
    })
end)