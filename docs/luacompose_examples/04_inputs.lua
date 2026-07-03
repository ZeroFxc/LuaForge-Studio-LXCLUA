-- ============================================================
-- Nirithy LuaCompose 官方教程 04 — 输入组件
-- ============================================================
-- 目标：学会 TextField、Checkbox、Switch、Slider 等输入组件
-- 覆盖：TextField, OutlinedTextField, Checkbox, Switch, Slider
--       onValueChange, onCheckedChange, textLambda 状态绑定
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- ⚠️ State 必须在 render 外面声明
-- 输入组件内部使用 remember + LaunchedEffect 同步外部值，mutableState 即可
-- 但显示文字需要使用 textLambda 回调，否则不会随 mutableState 变化更新
local textValue = compose.mutableState("")
local isChecked = compose.mutableState(false)
local isSwitched = compose.mutableState(true)
local sliderValue = compose.mutableState(50.0)

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

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- ===== TextField =====
            compose.Text({ text = "1. TextField 文本输入:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.TextField({
                text = textValue.value,
                label = "请输入文本",
                placeholder = "在这里输入...",
                onValueChange = function(v)
                    textValue.value = v
                end,
                modifier = compose.Modifier().fillMaxWidth(),
            }),

            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            -- ⚠️ 使用 textLambda 回调读取 mutableState 最新值
            compose.Text({
                textLambda = function()
                    return "你输入了: " .. textValue.value
                end,
                fontSize = 14,
                color = 0xFF666666,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== OutlinedTextField =====
            compose.Text({ text = "2. OutlinedTextField 边框输入:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.OutlinedTextField({
                text = textValue.value,
                label = "带边框的输入框",
                placeholder = "支持多行...",
                onValueChange = function(v)
                    textValue.value = v
                end,
                modifier = compose.Modifier().fillMaxWidth(),
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== Checkbox =====
            compose.Text({ text = "3. Checkbox 复选框:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Row({
                children = {
                    compose.Checkbox({
                        checked = isChecked.value,
                        onCheckedChange = function(v)
                            isChecked.value = v
                        end,
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(8) }),
                    compose.Text({
                        text = "同意用户协议",
                        fontSize = 16,
                        modifier = compose.Modifier().padding(0, 12, 0, 0),
                    }),
                },
            }),
            compose.Text({
                textLambda = function()
                    return "状态: " .. (isChecked.value and "已勾选" or "未勾选")
                end,
                fontSize = 14,
                color = 0xFF666666,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== Switch =====
            compose.Text({ text = "4. Switch 开关:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Row({
                children = {
                    compose.Switch({
                        checked = isSwitched.value,
                        onCheckedChange = function(v)
                            isSwitched.value = v
                        end,
                    }),
                    compose.Spacer({ modifier = compose.Modifier().width(8) }),
                    compose.Text({
                        text = "启用通知",
                        fontSize = 16,
                        modifier = compose.Modifier().padding(0, 12, 0, 0),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== Slider =====
            compose.Text({ text = "5. Slider 滑块:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Slider({
                value = sliderValue.value,
                valueRange = { 0, 100 },
                onValueChange = function(v)
                    sliderValue.value = v
                end,
                modifier = compose.Modifier().fillMaxWidth(),
            }),
            compose.Text({
                textLambda = function()
                    return "当前值: " .. math.floor(sliderValue.value)
                end,
                fontSize = 16,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 综合示例：表单 =====
            compose.Text({ text = "6. 综合表单示例:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),
            compose.Card({
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Column({
                        modifier = compose.Modifier().padding(16),
                        children = {
                            compose.Text({ text = "用户设置", fontSize = 18, fontWeight = 700 }),
                            compose.Spacer({ modifier = compose.Modifier().height(8) }),
                            compose.TextField({
                                text = textValue.value,
                                label = "用户名",
                                onValueChange = function(v) textValue.value = v end,
                                modifier = compose.Modifier().fillMaxWidth(),
                            }),
                            compose.Spacer({ modifier = compose.Modifier().height(8) }),
                            compose.Text({
                                textLambda = function()
                                    return "音量: " .. math.floor(sliderValue.value)
                                end,
                                fontSize = 14,
                            }),
                            compose.Slider({
                                value = sliderValue.value,
                                valueRange = { 0, 100 },
                                onValueChange = function(v) sliderValue.value = v end,
                                modifier = compose.Modifier().fillMaxWidth(),
                            }),
                            compose.Spacer({ modifier = compose.Modifier().height(8) }),
                            compose.Row({
                                children = {
                                    compose.Checkbox({
                                        checked = isChecked.value,
                                        onCheckedChange = function(v) isChecked.value = v end,
                                    }),
                                    compose.Text({
                                        text = "记住我",
                                        modifier = compose.Modifier().padding(0, 12, 0, 0),
                                    }),
                                },
                            }),
                            compose.Row({
                                children = {
                                    compose.Switch({
                                        checked = isSwitched.value,
                                        onCheckedChange = function(v) isSwitched.value = v end,
                                    }),
                                    compose.Text({
                                        text = "深色模式",
                                        modifier = compose.Modifier().padding(0, 12, 0, 0),
                                    }),
                                },
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