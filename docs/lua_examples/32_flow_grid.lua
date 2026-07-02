-- ============================================================
-- Nirithy LuaCompose 官方教程 32 — 流式布局与网格
-- ============================================================
-- 目标：FlowRow、FlowColumn、LazyVerticalGrid、LazyHorizontalGrid
-- 覆盖：FlowRow, FlowColumn, LazyVerticalGrid, LazyHorizontalGrid
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- 模拟标签数据
local tags = {"Kotlin", "Lua", "Compose", "Android", "Material3", "WebAssembly",
              "Canvas", "动画", "LazyColumn", "网格", "FlowRow", "响应式"}

-- 模拟图片数据
local gridItems = {}
for i = 1, 30 do
    local hue = (i * 37) % 360
    gridItems[i] = {
        index = i,
        color = 0xFF000000 + hue * 65536 + (100 - hue * 0.3) * 256 + 0xC0,
    }
end

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24)
            .verticalScroll(),

        children = {

            compose.Text({
                text = "流式布局与网格",
                fontSize = 22,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(20) }),

            -- ===== 1. FlowRow 流式水平布局 =====
            compose.Text({ text = "1. FlowRow 标签云:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Card({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.FlowRow({
                        modifier = compose.Modifier().padding(12),
                        horizontalArrangement = "SpaceAround",
                        children = (function()
                            local result = {}
                            for _, tag in ipairs(tags) do
                                table.insert(result, compose.SuggestionChip({
                                    label = tag,
                                    onClick = function() end,
                                }))
                            end
                            return result
                        end)(),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 2. FlowColumn 流式垂直布局 =====
            compose.Text({ text = "2. FlowColumn 流式垂直:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Card({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(300),
                children = {
                    compose.FlowColumn({
                        modifier = compose.Modifier()
                            .fillMaxSize()
                            .padding(12),
                        verticalArrangement = "SpaceAround",
                        children = (function()
                            local result = {}
                            for i = 1, 20 do
                                local hue = (i * 47) % 360
                                table.insert(result, compose.Box({
                                    modifier = compose.Modifier()
                                        .size(40 + i * 2, 40 + i * 2)
                                        .backgroundRounded(0xFF000000 + hue * 65536 + 0x80, 8),
                                    children = {
                                        compose.Text({
                                            text = tostring(i),
                                            fontSize = 10,
                                            color = 0xFFFFFFFF,
                                            modifier = compose.Modifier().align("Center"),
                                        }),
                                    },
                                }))
                            end
                            return result
                        end)(),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 3. LazyVerticalGrid 垂直网格 =====
            compose.Text({ text = "3. LazyVerticalGrid 照片墙:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.LazyVerticalGrid({
                columns = 3,  -- 3列
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(300),
                children = (function()
                    local result = {}
                    for i = 1, 30 do
                        table.insert(result, compose.Card({
                            modifier = compose.Modifier()
                                .padding(2)
                                .fillMaxWidth()
                                .aspectRatio(1),
                            children = {
                                compose.Box({
                                    modifier = compose.Modifier()
                                        .fillMaxSize()
                                        .backgroundRounded(gridItems[i].color, 4),
                                    contentAlignment = "Center",
                                    children = {
                                        compose.Text({
                                            text = tostring(i),
                                            fontSize = 14,
                                            fontWeight = 600,
                                            color = 0xFFFFFFFF,
                                        }),
                                    },
                                }),
                            },
                        }))
                    end
                    return result
                end)(),
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 4. LazyHorizontalGrid 水平网格 =====
            compose.Text({ text = "4. LazyHorizontalGrid 横向滚动:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.LazyHorizontalGrid({
                rows = 2,  -- 2行
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(160),
                children = (function()
                    local result = {}
                    for i = 1, 20 do
                        local hue = (i * 27) % 360
                        table.insert(result, compose.Card({
                            modifier = compose.Modifier()
                                .padding(2)
                                .size(80, 70),
                            children = {
                                compose.Box({
                                    modifier = compose.Modifier()
                                        .fillMaxSize()
                                        .backgroundRounded(0xFF000000 + hue * 65536 + 0x90, 4),
                                    contentAlignment = "Center",
                                    children = {
                                        compose.Text({
                                            text = tostring(i),
                                            fontSize = 13,
                                            fontWeight = 600,
                                            color = 0xFFFFFFFF,
                                        }),
                                    },
                                }),
                            },
                        }))
                    end
                    return result
                end)(),
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 5. GridCells 字符串格式 =====
            compose.Text({ text = "5. GridCells Fixed(4) — 4列:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.LazyVerticalGrid({
                columns = "Fixed(4)",  -- 字符串格式
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(200),
                children = (function()
                    local result = {}
                    for i = 1, 16 do
                        table.insert(result, compose.Box({
                            modifier = compose.Modifier()
                                .padding(1)
                                .fillMaxWidth()
                                .height(40)
                                .backgroundRounded(0xFFE8DEF8, 4),
                            contentAlignment = "Center",
                            children = {
                                compose.Text({
                                    text = tostring(i),
                                    fontSize = 12,
                                    color = 0xFF6750A4,
                                }),
                            },
                        }))
                    end
                    return result
                end)(),
            }),

            compose.Spacer({ modifier = compose.Modifier().height(32) }),
        },
    })
end)