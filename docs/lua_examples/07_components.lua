-- ============================================================
-- Nirithy LuaCompose 官方教程 07 — 高级组件
-- ============================================================
-- 目标：学会 SearchBar、ProgressIndicator、Chip、Badge、FAB、Icon、Tab 等
-- 覆盖：SearchBar, LinearProgressIndicator, CircularProgressIndicator
--       FilterChip, Badge, FloatingActionButton, Icon, TabRow, Tab
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- ⚠️ 所有需触发 UI 更新的状态必须使用 state()，mutableState 变更不会触发 recomposition
local searchQuery = compose.state("")
local searchActive = compose.state(false)
-- animateFloatAsState 在 Kotlin 端驱动动画，每帧触发 refreshNodeTree
-- 进度目标值：0=初始, 50=半程, 100=完成
local progressTarget = compose.state(0.0)
local selectedTab = compose.state(0)

-- 芯片选中状态
local chipAll = compose.state(true)
local chipLayout = compose.state(false)
local chipAnim = compose.state(false)
local chipCanvas = compose.state(false)

compose.render(function()
    -- ⚠️ animateFloatAsState 在 Kotlin 端用 spring() 驱动，
    -- snapshotFlow 每帧触发 refreshNodeTree 更新 UI
    -- 当 progressTarget 变化时，动画从当前值平滑过渡到目标值
    local animatedProgress = compose.animateFloatAsState(progressTarget.value)

    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24)
            .verticalScroll(),

        children = {

            compose.Text({
                text = "高级组件示例",
                fontSize = 22,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- ===== 1. SearchBar 搜索栏 =====
            compose.Text({ text = "1. SearchBar 搜索栏:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.SearchBar({
                query = searchQuery.value,
                placeholder = "搜索组件...",
                onQueryChange = function(v)
                    searchQuery.value = v
                end,
                onSearch = function(v)
                    searchQuery.value = "搜索: " .. tostring(v)
                    searchActive.value = false
                end,
                active = searchActive.value,
                onActiveChange = function(v)
                    searchActive.value = v
                end,
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .heightIn(0, 280),
            }),
            compose.Text({
                textLambda = function()
                    return "查询: " .. searchQuery.value
                end,
                fontSize = 13,
                color = 0xFF666666,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 2. ProgressIndicator 进度指示器 =====
            compose.Text({ text = "2. ProgressIndicator 进度:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            -- 线性进度条（确定进度，带动画）
            compose.Text({
                textLambda = function()
                    return "确定进度 (" .. string.format("%.0f", animatedProgress.value) .. "%)"
                end,
                fontSize = 13,
                color = 0xFF666666,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(4) }),
            compose.LinearProgressIndicator({
                -- animateFloatAsState 驱动进度值从 0 平滑过渡到目标
                progress = animatedProgress.value / 100,
                modifier = compose.Modifier().fillMaxWidth(),
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            -- 圆形进度
            compose.Row({
                children = {
                    compose.CircularProgressIndicator({
                        progress = animatedProgress.value / 100,
                        modifier = compose.Modifier().size(32, 32),
                        strokeWidth = 3,
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(8) }),
                    compose.Text({
                        textLambda = function()
                            local p = animatedProgress.value
                            if p >= 100 then
                                return "加载完成!"
                            elseif p >= 50 then
                                return "加载中... " .. string.format("%.0f", p) .. "%"
                            else
                                return "准备加载... " .. string.format("%.0f", p) .. "%"
                            end
                        end,
                        fontSize = 14,
                        color = animatedProgress.value >= 100 and 0xFF4CAF50 or 0xFF666666,
                        modifier = compose.Modifier().padding(0, 6, 0, 0),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Button({
                        text = "开始加载",
                        onClick = function()
                            progressTarget.value = 50
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(4) }),
                    compose.Button({
                        text = "加载完成",
                        onClick = function()
                            progressTarget.value = 100
                        end,
                        modifier = compose.Modifier().weight(1),
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(4) }),
                    compose.Button({
                        text = "重置",
                        onClick = function() progressTarget.value = 0 end,
                        modifier = compose.Modifier().weight(1),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 3. Chip 标签 =====
            compose.Text({ text = "3. Chip 标签:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.FilterChip({
                        label = "布局",
                        selected = chipAll.value,
                        onClick = function()
                            chipAll.value = true
                            chipLayout.value = false
                            chipAnim.value = false
                            chipCanvas.value = false
                        end,
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(4) }),
                    compose.FilterChip({
                        label = "组件",
                        selected = chipLayout.value,
                        onClick = function()
                            chipAll.value = false
                            chipLayout.value = true
                            chipAnim.value = false
                            chipCanvas.value = false
                        end,
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(4) }),
                    compose.FilterChip({
                        label = "动画",
                        selected = chipAnim.value,
                        onClick = function()
                            chipAll.value = false
                            chipLayout.value = false
                            chipAnim.value = true
                            chipCanvas.value = false
                        end,
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(4) }),
                    compose.FilterChip({
                        label = "Canvas",
                        selected = chipCanvas.value,
                        onClick = function()
                            chipAll.value = false
                            chipLayout.value = false
                            chipAnim.value = false
                            chipCanvas.value = true
                        end,
                    }),
                },
            }),

            -- 显示当前选中的芯片
            compose.Text({
                textLambda = function()
                    if chipAll.value then return "选中: 布局"
                    elseif chipLayout.value then return "选中: 组件"
                    elseif chipAnim.value then return "选中: 动画"
                    elseif chipCanvas.value then return "选中: Canvas"
                    else return "未选中"
                    end
                end,
                fontSize = 13,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 4. Badge 徽章 =====
            compose.Text({ text = "4. Badge 徽章:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Row({
                children = {
                    compose.BadgedBox({
                        badgeCount = 5,
                        modifier = compose.Modifier().size(48, 48),
                        children = {
                            compose.Icon({
                                name = "Email",
                                size = 32,
                                color = 0xFF666666,
                            }),
                        },
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(24) }),
                    compose.BadgedBox({
                        badgeCount = 99,
                        modifier = compose.Modifier().size(48, 48),
                        children = {
                            compose.Icon({
                                name = "Notifications",
                                size = 32,
                                color = 0xFF666666,
                            }),
                        },
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(24) }),
                    compose.Badge({
                        text = "新",
                        modifier = compose.Modifier().padding(0, 6, 0, 0),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 5. Icon 图标 =====
            compose.Text({ text = "5. Icon 图标:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Icon({ name = "Home", size = 28, color = 0xFF6750A4 }),
                    compose.Spacer({ modifier = compose.Modifier().width(8) }),
                    compose.Icon({ name = "Search", size = 28, color = 0xFF2196F3 }),
                    compose.Spacer({ modifier = compose.Modifier().width(8) }),
                    compose.Icon({ name = "Favorite", size = 28, color = 0xFFE91E63 }),
                    compose.Spacer({ modifier = compose.Modifier().width(8) }),
                    compose.Icon({ name = "Settings", size = 28, color = 0xFF607D8B }),
                    compose.Spacer({ modifier = compose.Modifier().width(8) }),
                    compose.Icon({ name = "Star", size = 28, color = 0xFFFF9800 }),
                    compose.Spacer({ modifier = compose.Modifier().width(8) }),
                    compose.Icon({ name = "Delete", size = 28, color = 0xFFF44336 }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 6. FloatingActionButton =====
            compose.Text({ text = "6. FAB 浮动按钮:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Row({
                children = {
                    compose.FloatingActionButton({
                        onClick = function() end,
                        modifier = compose.Modifier().size(48, 48),
                        children = {
                            compose.Icon({
                                name = "Add",
                                size = 24,
                                color = 0xFFFFFFFF,
                            }),
                        },
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(12) }),
                    compose.SmallFloatingActionButton({
                        onClick = function() end,
                        children = {
                            compose.Icon({ name = "Edit", size = 18, color = 0xFFFFFFFF }),
                        },
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(12) }),
                    compose.ExtendedFloatingActionButton({
                        text = "创建项目",
                        onClick = function() end,
                        children = {
                            compose.Icon({ name = "Add", size = 20, color = 0xFFFFFFFF }),
                        },
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 7. TabRow 选项卡 =====
            compose.Text({ text = "7. TabRow 选项卡:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.TabRow({
                selectedIndex = selectedTab.value,
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Tab({
                        text = "布局",
                        selected = selectedTab.value == 0,
                        onClick = function() selectedTab.value = 0 end,
                    }),
                    compose.Tab({
                        text = "组件",
                        selected = selectedTab.value == 1,
                        onClick = function() selectedTab.value = 1 end,
                    }),
                    compose.Tab({
                        text = "动画",
                        selected = selectedTab.value == 2,
                        onClick = function() selectedTab.value = 2 end,
                    }),
                },
            }),

            -- Tab 内容区
            compose.Card({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .padding(0, 8, 0, 0),
                children = {
                    compose.Box({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .height(60)
                            .padding(16),
                        children = {
                            compose.Text({
                                textLambda = function()
                                    return "当前选中: Tab " .. (selectedTab.value + 1)
                                end,
                                fontSize = 16,
                                color = 0xFF333333,
                            }),
                        },
                    }),
                },
            }),

            -- 底部留白
            compose.Spacer({ modifier = compose.Modifier().height(32) }),
        },
    })
end)