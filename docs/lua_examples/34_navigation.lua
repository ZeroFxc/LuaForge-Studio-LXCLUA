-- ============================================================
-- Nirithy LuaCompose 官方教程 34 — 导航与滑动
-- ============================================================
-- 目标：NavigationBar、SwipeToDismiss、SegmentedButton、HorizontalPager、VerticalPager
-- 覆盖：NavigationBar, SwipeToDismissBox, SingleChoiceSegmentedButtonRow,
--       MultiChoiceSegmentedButtonRow, HorizontalPager, VerticalPager
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- 导航状态
local currentTab = compose.state(0)
-- 分段按钮
local singleSelect = compose.state(0)
local multiSelect = compose.state({false, true, false, false})
-- 滑动删除列表
local items = compose.state({{id = 1, text = "滑动我删除 — 左滑"}, {id = 2, text = "滑动我删除 — 右滑"},
                             {id = 3, text = "滑动我删除 — 左滑"}, {id = 4, text = "滑动我删除 — 右滑"}})
-- 翻页
local currentPage = compose.state(0)
local pageColors = {0xFFE3F2FD, 0xFFE8F5E9, 0xFFFFF3E0, 0xFFFCE4EC, 0xFFF3E5F5}

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24)
            .verticalScroll(),

        children = {

            compose.Text({
                text = "导航与滑动",
                fontSize = 22,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(20) }),

            -- ===== 1. NavigationBar 底部导航栏 =====
            compose.Text({ text = "1. NavigationBar 底部导航:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.NavigationBar({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.NavigationBarItem({
                        selected = currentTab.value == 0,
                        icon = "Home",
                        label = "首页",
                        onClick = function() currentTab.value = 0 end,
                    }),
                    compose.NavigationBarItem({
                        selected = currentTab.value == 1,
                        icon = "Search",
                        label = "搜索",
                        onClick = function() currentTab.value = 1 end,
                    }),
                    compose.NavigationBarItem({
                        selected = currentTab.value == 2,
                        icon = "Favorite",
                        label = "收藏",
                        onClick = function() currentTab.value = 2 end,
                    }),
                    compose.NavigationBarItem({
                        selected = currentTab.value == 3,
                        icon = "Settings",
                        label = "设置",
                        onClick = function() currentTab.value = 3 end,
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 2. SegmentedButton 分段按钮 =====
            compose.Text({ text = "2. SegmentedButton 单选分段:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.SingleChoiceSegmentedButtonRow({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.SegmentedButton({
                        selected = singleSelect.value == 0,
                        text = "日",
                        onClick = function() singleSelect.value = 0 end,
                    }),
                    compose.SegmentedButton({
                        selected = singleSelect.value == 1,
                        text = "周",
                        onClick = function() singleSelect.value = 1 end,
                    }),
                    compose.SegmentedButton({
                        selected = singleSelect.value == 2,
                        text = "月",
                        onClick = function() singleSelect.value = 2 end,
                    }),
                    compose.SegmentedButton({
                        selected = singleSelect.value == 3,
                        text = "年",
                        onClick = function() singleSelect.value = 3 end,
                    }),
                },
            }),
            compose.Text({
                textLambda = function()
                    local labels = {"日", "周", "月", "年"}
                    return "当前: " .. labels[singleSelect.value + 1]
                end,
                fontSize = 13,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            compose.Text({ text = "多选分段:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.MultiChoiceSegmentedButtonRow({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.SegmentedButton({
                        checked = multiSelect.value[1],
                        text = "布局",
                        onCheckedChange = function(v)
                            -- ★ state() 必须整体替换 .value 才能触发重渲染
                            local t = multiSelect.value
                            multiSelect.value = {v, t[2], t[3], t[4]}
                        end,
                    }),
                    compose.SegmentedButton({
                        checked = multiSelect.value[2],
                        text = "组件",
                        onCheckedChange = function(v)
                            local t = multiSelect.value
                            multiSelect.value = {t[1], v, t[3], t[4]}
                        end,
                    }),
                    compose.SegmentedButton({
                        checked = multiSelect.value[3],
                        text = "动画",
                        onCheckedChange = function(v)
                            local t = multiSelect.value
                            multiSelect.value = {t[1], t[2], v, t[4]}
                        end,
                    }),
                    compose.SegmentedButton({
                        checked = multiSelect.value[4],
                        text = "Canvas",
                        onCheckedChange = function(v)
                            local t = multiSelect.value
                            multiSelect.value = {t[1], t[2], t[3], v}
                        end,
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 3. SwipeToDismiss 滑动删除 =====
            compose.Text({ text = "3. SwipeToDismissBox 滑动删除:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.LazyColumn({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(200),
                children = (function()
                    local result = {}
                    for _, itemData in ipairs(items.value) do
                        table.insert(result, compose.SwipeToDismissBox({
                            onDismissedToStart = function()
                                -- 左滑删除
                                local newItems = {}
                                for _, it in ipairs(items.value) do
                                    if it.id ~= itemData.id then
                                        table.insert(newItems, it)
                                    end
                                end
                                items.value = newItems
                            end,
                            onDismissedToEnd = function()
                                -- 右滑删除
                                local newItems = {}
                                for _, it in ipairs(items.value) do
                                    if it.id ~= itemData.id then
                                        table.insert(newItems, it)
                                    end
                                end
                                items.value = newItems
                            end,
                            children = {
                                -- 背景（滑动时显示），_slot = "background" 标记
                                compose.Box({
                                    _slot = "background",
                                    modifier = compose.Modifier()
                                        .fillMaxWidth()
                                        .height(56)
                                        .backgroundRounded(0xFFFF5252, 8),
                                    children = {
                                        compose.Text({
                                            text = "删除",
                                            color = 0xFFFFFFFF,
                                            fontSize = 14,
                                            modifier = compose.Modifier()
                                                .align("CenterEnd")
                                                .padding(0, 0, 16, 0),
                                        }),
                                    },
                                }),
                                -- 内容
                                compose.Card({
                                    modifier = compose.Modifier()
                                        .fillMaxWidth()
                                        .padding(0, 2, 0, 2),
                                    children = {
                                        compose.Text({
                                            text = itemData.text,
                                            fontSize = 14,
                                            modifier = compose.Modifier().padding(16),
                                        }),
                                    },
                                }),
                            },
                        }))
                    end
                    if #result == 0 then
                        table.insert(result, compose.Text({
                            text = "所有项已删除",
                            fontSize = 14,
                            color = 0xFF999999,
                            modifier = compose.Modifier().padding(16),
                        }))
                    end
                    return result
                end)(),
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 4. HorizontalPager 翻页 =====
            compose.Text({
                textLambda = function()
                    return "4. HorizontalPager 翻页 — 第" .. (currentPage.value + 1) .. "页"
                end,
                fontSize = 16,
                fontWeight = 600,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.HorizontalPager({
                pageCount = 5,
                beyondViewportPageCount = 1,
                onPageChanged = function(page)
                    currentPage.value = page
                end,
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(120),
                children = function(page)
                    return compose.Box({
                        modifier = compose.Modifier()
                            .fillMaxSize()
                            .backgroundRounded(pageColors[page + 1], 12)
                            .padding(20),
                        contentAlignment = "Center",
                        children = {
                            compose.Text({
                                text = "第 " .. (page + 1) .. " 页",
                                fontSize = 20,
                                fontWeight = 700,
                                color = 0xFF333333,
                            }),
                        },
                    })
                end,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            -- 页面指示器
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                horizontalArrangement = "Center",
                children = (function()
                    local dots = {}
                    for i = 0, 4 do
                        table.insert(dots, compose.Box({
                            modifier = compose.Modifier()
                                .size(currentPage.value == i and 10 or 6, currentPage.value == i and 10 or 6)
                                .padding(2)
                                .backgroundRounded(currentPage.value == i and 0xFF6750A4 or 0xFFCCCCCC, 999),
                        }))
                        if i < 4 then
                            table.insert(dots, compose.Spacer({ modifier = compose.Modifier().width(4) }))
                        end
                    end
                    return dots
                end)(),
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 5. VerticalPager 垂直翻页 =====
            compose.Text({ text = "5. VerticalPager 垂直翻页:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.VerticalPager({
                pageCount = 3,
                beyondViewportPageCount = 1,
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(100),
                children = function(page)
                    local colors = {0xFFE3F2FD, 0xFFE8F5E9, 0xFFFFF3E0}
                    return compose.Box({
                        modifier = compose.Modifier()
                            .fillMaxSize()
                            .backgroundRounded(colors[page + 1], 8)
                            .padding(16),
                        contentAlignment = "Center",
                        children = {
                            compose.Text({
                                text = "垂直翻页 " .. (page + 1),
                                fontSize = 16,
                                fontWeight = 600,
                                color = 0xFF333333,
                            }),
                        },
                    })
                end,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(32) }),
        },
    })
end)