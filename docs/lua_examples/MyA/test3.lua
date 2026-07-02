local function main()
    local searchQuery = compose.state("")
    local fabCount = compose.state(0)
    local selectedDate = compose.state("未选择日期")
    local isSearching = compose.state(false)
    local searchTimerId = 0

    local scope = compose.rememberCoroutineScope()

    return compose.Column {
        modifier = compose.Modifier().fillMaxSize().padding(16).verticalScroll(),
        children = {
            -- ===== 当前时间 =====
            compose.Text {
                text = "当前时间戳: " .. compose.now() .. " ms",
                fontSize = 12,
                color = compose.color(0x666666),
            },

            -- ===== FAB 点击计数 =====
            compose.Row {
                modifier = compose.Modifier().fillMaxWidth().paddingTop(12),
                horizontalArrangement = "SpaceBetween",
                verticalAlignment = "CenterVertically",
                children = {
                    compose.Text {
                        text = "FAB 点击次数: " .. fabCount.value,
                        fontSize = 16,
                        fontWeight = "Bold",
                    },
                    compose.FloatingActionButton {
                        onClick = function()
                            fabCount.value = fabCount.value + 1
                        end,
                        children = { compose.Icon { icon = "Add" } },
                    },
                },
            },

            -- ===== 搜索框 + 3秒超时 =====
            compose.Text {
                text = "搜索（输入后3秒自动停止进度条）",
                fontSize = 14,
                fontWeight = "Bold",
                modifier = compose.Modifier().paddingTop(16),
            },
            compose.SearchBar {
                query = searchQuery.value,
                placeholder = "输入搜索内容...",
                onQueryChange = function(v)
                    searchQuery.value = v
                    if v ~= "" then
                        isSearching.value = true
                        searchTimerId = searchTimerId + 1
                        local myId = searchTimerId
                        -- 使用 . 语法（非 :），launchAfter 自动延迟3秒后执行
                        scope.launchAfter(3000, function()
                            if myId == searchTimerId then
                                isSearching.value = false
                            end
                        end)
                    else
                        isSearching.value = false
                    end
                end,
                onSearch = function(v) end,
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8),
            },

            -- 搜索内容回显
            compose.Text {
                text = searchQuery.value ~= ""
                    and ("搜索内容: " .. searchQuery.value)
                    or "请在上方输入搜索内容",
                fontSize = 14,
                color = compose.color(0x2196F3),
                modifier = compose.Modifier().paddingTop(8),
            },

            -- ===== 闪烁进度条 =====
            compose.AnimatedVisibility {
                visible = isSearching.value,
                enter = compose.fadeIn(),
                exit = compose.fadeOut(),
                children = {
                    compose.Column {
                        children = {
                            compose.Text {
                                text = "正在搜索... (3秒后自动停止)",
                                fontSize = 12,
                                color = compose.color(0xFF9800),
                                modifier = compose.Modifier().paddingTop(4),
                            },
                            compose.LinearProgressIndicator {
                                modifier = compose.Modifier().fillMaxWidth().paddingTop(4),
                            },
                        },
                    },
                },
            },

            -- ===== 日期选择器 =====
            compose.Text {
                text = "选择日期",
                fontSize = 14,
                fontWeight = "Bold",
                modifier = compose.Modifier().paddingTop(16),
            },
            compose.Text {
                text = "选中日期: " .. selectedDate.value,
                fontSize = 14,
                color = compose.color(0x4CAF50),
                modifier = compose.Modifier().paddingTop(4),
            },
            compose.DatePicker {
                onDateSelected = function(millis)
                    selectedDate.value = "时间戳: " .. millis .. " ms"
                end,
                modifier = compose.Modifier().paddingTop(4),
            },

            -- ===== Chip =====
            compose.Text {
                text = "筛选条件",
                fontSize = 14,
                fontWeight = "Bold",
                modifier = compose.Modifier().paddingTop(16),
            },
            compose.Row {
                modifier = compose.Modifier().fillMaxWidth().paddingTop(8),
                horizontalArrangement = "Start",
                children = {
                    compose.FilterChip {
                        label = "全部",
                        selected = true,
                        onClick = function() end,
                        modifier = compose.Modifier().paddingEnd(8),
                    },
                    compose.FilterChip {
                        label = "已选 " .. fabCount.value,
                        selected = fabCount.value > 0,
                        onClick = function() end,
                    },
                },
            },

            -- ===== 徽章 =====
            compose.Text {
                text = "消息通知",
                fontSize = 14,
                fontWeight = "Bold",
                modifier = compose.Modifier().paddingTop(16),
            },
            compose.Badge {
                text = tostring(fabCount.value),
                modifier = compose.Modifier().paddingTop(8),
            },
        },
    }
end
compose.render(main)