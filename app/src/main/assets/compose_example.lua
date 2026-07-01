-- Compose-Lua 示例
-- 使用 compose 全局表声明式构建 Compose UI

local function main()
    -- 创建响应式状态
    local count = compose.state(0)
    local text = compose.state("Hello Lua Compose")

    -- 返回根节点树
    return compose.Column {
        modifier = compose.Modifier
            .fillMaxSize()
            .padding(16),
        verticalArrangement = "Center",
        horizontalAlignment = "CenterHorizontally",
        children = {
            -- 标题
            compose.Text {
                text = "Lua Compose 示例",
                fontSize = 24,
                fontWeight = "Bold",
                modifier = compose.Modifier.paddingBottom(24),
            },

            -- 计数器显示
            compose.Card {
                modifier = compose.Modifier
                    .fillMaxWidth()
                    .padding(16),
                elevation = 4,
                children = {
                    compose.Column {
                        modifier = compose.Modifier.padding(16),
                        horizontalAlignment = "CenterHorizontally",
                        children = {
                            compose.Text {
                                text = "点击次数",
                                fontSize = 14,
                                fontWeight = "Medium",
                            },
                            compose.Text {
                                text = tostring(count.value),
                                fontSize = 48,
                                fontWeight = "Bold",
                                modifier = compose.Modifier.paddingVertical(8),
                            },
                        },
                    },
                },
            },

            -- 按钮行
            compose.Row {
                horizontalArrangement = "Center",
                modifier = compose.Modifier.paddingTop(16),
                children = {
                    compose.Button {
                        onClick = function()
                            count.value = count.value + 1
                        end,
                        modifier = compose.Modifier.padding(4),
                        children = {
                            compose.Text { text = "+1" },
                        },
                    },
                    compose.Button {
                        onClick = function()
                            count.value = 0
                        end,
                        modifier = compose.Modifier.padding(4),
                        color = 0xFFE53935,
                        children = {
                            compose.Text { text = "重置" },
                        },
                    },
                },
            },

            -- 文本输入
            compose.OutlinedTextField {
                text = text.value,
                onValueChange = function(v)
                    text.value = v
                end,
                label = "输入文本",
                modifier = compose.Modifier
                    .fillMaxWidth()
                    .padding(16),
                singleLine = true,
            },

            -- 显示输入的文本
            compose.Text {
                text = "你输入了: " .. text.value,
                fontSize = 16,
                fontWeight = "Medium",
                color = compose.color(0x2196F3),
                modifier = compose.Modifier.paddingTop(8),
            },
        },
    }
end

-- 渲染
compose.render(main)