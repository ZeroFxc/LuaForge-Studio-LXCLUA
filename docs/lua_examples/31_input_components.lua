-- ============================================================
-- Nirithy LuaCompose 官方教程 31 — 输入组件
-- ============================================================
-- 目标：学会 RadioButton、Checkbox、Switch、Slider 的用法
-- 覆盖：RadioButton, Checkbox, Switch, Slider — 受控组件模式
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- 状态
local radioSelected = compose.state(0) -- 0=无, 1=A, 2=B, 3=C
local checkboxChecked = compose.state(false)
local switchOn = compose.state(false)
local sliderValue = compose.state(0.5)
local triState = compose.state(0) -- 0=未选, 1=半选, 2=全选

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24)
            .verticalScroll(),

        children = {

            compose.Text({
                text = "输入组件示例",
                fontSize = 22,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(20) }),

            -- ===== 1. RadioButton 单选按钮 =====
            compose.Text({ text = "1. RadioButton 单选按钮:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                verticalAlignment = "CenterVertically",
                children = {
                    compose.RadioButton({
                        selected = radioSelected.value == 1,
                        onClick = function() radioSelected.value = 1 end,
                    }),
                    compose.Text({
                        text = "选项 A",
                        fontSize = 15,
                        modifier = compose.Modifier().padding(0, 0, 16, 0),
                    }),

                    compose.RadioButton({
                        selected = radioSelected.value == 2,
                        onClick = function() radioSelected.value = 2 end,
                    }),
                    compose.Text({
                        text = "选项 B",
                        fontSize = 15,
                        modifier = compose.Modifier().padding(0, 0, 16, 0),
                    }),

                    compose.RadioButton({
                        selected = radioSelected.value == 3,
                        onClick = function() radioSelected.value = 3 end,
                    }),
                    compose.Text({
                        text = "选项 C",
                        fontSize = 15,
                    }),
                },
            }),
            compose.Text({
                textLambda = function()
                    local labels = {"无", "A", "B", "C"}
                    return "当前选中: " .. labels[radioSelected.value + 1]
                end,
                fontSize = 13,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 2. Checkbox 复选框 =====
            compose.Text({ text = "2. Checkbox 复选框:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Row({
                verticalAlignment = "CenterVertically",
                children = {
                    compose.Checkbox({
                        checked = checkboxChecked.value,
                        onCheckedChange = function(v)
                            checkboxChecked.value = v
                        end,
                    }),
                    compose.Text({
                        textLambda = function()
                            return checkboxChecked.value and "已勾选" or "未勾选"
                        end,
                        fontSize = 15,
                        modifier = compose.Modifier().padding(4, 0, 0, 0),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 3. Switch 开关 =====
            compose.Text({ text = "3. Switch 开关:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Row({
                verticalAlignment = "CenterVertically",
                children = {
                    compose.Switch({
                        checked = switchOn.value,
                        onCheckedChange = function(v)
                            switchOn.value = v
                        end,
                    }),
                    compose.Text({
                        textLambda = function()
                            return switchOn.value and "开启" or "关闭"
                        end,
                        fontSize = 15,
                        modifier = compose.Modifier().padding(4, 0, 0, 0),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 4. Slider 滑块 =====
            compose.Text({ text = "4. Slider 滑块:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Slider({
                value = sliderValue.value,
                valueRange = {0, 1},
                onValueChange = function(v)
                    sliderValue.value = v
                end,
                modifier = compose.Modifier().fillMaxWidth(),
            }),
            compose.Row({
                modifier = compose.Modifier().fillMaxWidth(),
                horizontalArrangement = "SpaceBetween",
                children = {
                    compose.Text({
                        textLambda = function()
                            return string.format("%.0f%%", sliderValue.value * 100)
                        end,
                        fontSize = 14,
                        color = 0xFF6750A4,
                    }),
                    compose.Text({
                        textLambda = function()
                            return string.format("%.3f", sliderValue.value)
                        end,
                        fontSize = 14,
                        color = 0xFF666666,
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),
            -- 滑块值可视化
            compose.Box({
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .height(8)
                    .backgroundRounded(0xFFE0E0E0, 4),
                children = {
                    compose.Box({
                        modifier = compose.Modifier()
                            .fillMaxWidthLambda(function()
                                return sliderValue.value
                            end)
                            .height(8)
                            .backgroundRounded(0xFF6750A4, 4),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 5. 组合使用：表单示例 =====
            compose.Text({ text = "5. 表单组合示例:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Card({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Column({
                        modifier = compose.Modifier().padding(16),
                        children = {
                            compose.Text({
                                text = "偏好设置",
                                fontSize = 16,
                                fontWeight = 600,
                            }),

                            compose.Spacer({ modifier = compose.Modifier().height(12) }),

                            -- 通知开关
                            compose.Row({
                                modifier = compose.Modifier().fillMaxWidth(),
                                horizontalArrangement = "SpaceBetween",
                                verticalAlignment = "CenterVertically",
                                children = {
                                    compose.Text({
                                        text = "接收通知",
                                        fontSize = 15,
                                    }),
                                    compose.Switch({
                                        checked = switchOn.value,
                                        onCheckedChange = function(v)
                                            switchOn.value = v
                                        end,
                                    }),
                                },
                            }),

                            compose.Spacer({ modifier = compose.Modifier().height(8) }),

                            -- 同意条款
                            compose.Row({
                                verticalAlignment = "CenterVertically",
                                children = {
                                    compose.Checkbox({
                                        checked = checkboxChecked.value,
                                        onCheckedChange = function(v)
                                            checkboxChecked.value = v
                                        end,
                                    }),
                                    compose.Text({
                                        text = "同意用户协议",
                                        fontSize = 15,
                                        modifier = compose.Modifier().padding(4, 0, 0, 0),
                                    }),
                                },
                            }),

                            compose.Spacer({ modifier = compose.Modifier().height(12) }),

                            -- 音量滑块
                            compose.Text({
                                text = "音量",
                                fontSize = 15,
                            }),
                            compose.Slider({
                                value = sliderValue.value,
                                valueRange = {0, 1},
                                onValueChange = function(v)
                                    sliderValue.value = v
                                end,
                                modifier = compose.Modifier().fillMaxWidth(),
                            }),
                        },
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(32) }),
        },
    })
end)