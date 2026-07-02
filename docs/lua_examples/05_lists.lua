-- ============================================================
-- Nirithy LuaCompose 官方教程 05 — 列表与卡片
-- ============================================================
-- 目标：学会 LazyColumn、Card 和动态数据渲染
-- 覆盖：LazyColumn, Card, Surface, clickableLua, 动态生成子节点, for 循环
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- ===== 模拟数据 =====
local items = {
    { title = "学习 LuaCompose", desc = "掌握 Lua 驱动的 Compose UI 框架", color = 0xFF6750A4 },
    { title = "编写插件", desc = "扩展 ComposePlugin 接口添加自定义组件", color = 0xFF2196F3 },
    { title = "状态管理", desc = "使用 state 实现响应式 UI", color = 0xFF4CAF50 },
    { title = "动画效果", desc = "AnimatedVisibility 和 Crossfade 过渡动画", color = 0xFFFF9800 },
    { title = "Canvas 绘图", desc = "使用 DrawScope 自定义绘制图形", color = 0xFFE91E63 },
    { title = "导航系统", desc = "Navigation3 实现多页面导航", color = 0xFF9C27B0 },
    { title = "主题定制", desc = "MaterialTheme 颜色和字体系统", color = 0xFF795548 },
    { title = "性能优化", desc = "LazyColumn 懒加载和状态缓存", color = 0xFF607D8B },
}

-- ⚠️ 选中状态使用 state() 触发全量刷新，卡片颜色才能更新
local selectedIndex = compose.state(-1)

-- ⚠️ 生成子节点列表的函数必须在 render 外面定义
-- 在 render 内部调用，利用闭包捕获 state 引用
local function buildItemList()
    local result = {}
    for i, item in ipairs(items) do
        local isSelected = i == selectedIndex.value
        local bgColor = isSelected and item.color or 0xFFFFFFFF
        local textColor = isSelected and 0xFFFFFFFF or 0xFF333333
        local descColor = isSelected and 0xFFFFFFFF or 0xFF666666

        result[#result + 1] = compose.Card({
            modifier = compose.Modifier()
                .fillMaxWidth()
                .padding(8, 4, 8, 4)
                .clickableLua(function()
                    -- 点击切换选中状态
                    if selectedIndex.value == i then
                        selectedIndex.value = -1
                    else
                        selectedIndex.value = i
                    end
                end),
            color = bgColor,
            children = {
                compose.Row({
                    modifier = compose.Modifier()
                        .fillMaxWidth()
                        .padding(16),
                    children = {
                        -- 左侧序号图标
                        compose.Box({
                            modifier = compose.Modifier()
                                .size(40, 40)
                                .background(item.color)
                                .borderRadius(20),
                            children = {
                                compose.Text({
                                    text = tostring(i),
                                    fontSize = 16,
                                    fontWeight = 700,
                                    color = 0xFFFFFFFF,
                                    modifier = compose.Modifier().padding(0, 10, 0, 0),
                                    textAlign = "Center",
                                }),
                            },
                        }),

                        compose.Spacer({ modifier = compose.Modifier().width(12) }),

                        -- 文字内容
                        compose.Column({
                            modifier = compose.Modifier().weight(1),
                            children = {
                                compose.Text({
                                    text = item.title,
                                    fontSize = 16,
                                    fontWeight = 600,
                                    color = textColor,
                                }),
                                compose.Spacer({ modifier = compose.Modifier().height(4) }),
                                compose.Text({
                                    text = item.desc,
                                    fontSize = 13,
                                    color = descColor,
                                }),
                            },
                        }),

                        -- 右侧箭头
                        compose.Text({
                            text = ">",
                            fontSize = 20,
                            color = textColor,
                            modifier = compose.Modifier().padding(0, 6, 0, 0),
                        }),
                    },
                }),
            },
        })
    end
    return result
end

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24),

        children = {
            compose.Text({
                text = "列表与卡片示例",
                fontSize = 22,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Text({
                text = "点击卡片选中，再次点击取消",
                fontSize = 14,
                color = 0xFF666666,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- LazyColumn 懒加载列表
            compose.LazyColumn({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .weight(1),
                children = buildItemList(),
            }),

            -- 底部统计
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Divider({}),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Text({
                text = "共 " .. #items .. " 项"
                    .. (selectedIndex.value > 0 and ("，已选中第 " .. selectedIndex.value .. " 项") or ""),
                fontSize = 14,
                color = 0xFF999999,
            }),
        },
    })
end)