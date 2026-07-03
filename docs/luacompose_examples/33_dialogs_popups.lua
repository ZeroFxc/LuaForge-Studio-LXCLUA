-- ============================================================
-- Nirithy LuaCompose 官方教程 33 — 对话框与弹窗
-- ============================================================
-- 目标：AlertDialog、Popup、DropdownMenu、ExposedDropdownMenuBox、ModalBottomSheet
-- 覆盖：AlertDialog, Popup, DropdownMenu, ExposedDropdownMenuBox, ModalBottomSheet
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- 状态
local showDialog = compose.state(false)
local showPopup = compose.state(false)
local showMenu = compose.state(false)
local showExposed = compose.state(false)
local showSheet = compose.state(false)
local selectedOption = compose.state("请选择")
local menuItems = {"编辑", "分享", "复制", "删除"}

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24)
            .verticalScroll(),

        children = {

            compose.Text({
                text = "对话框与弹窗",
                fontSize = 22,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),
            compose.Spacer({ modifier = compose.Modifier().height(20) }),

            -- ===== 1. AlertDialog 对话框 =====
            compose.Text({ text = "1. AlertDialog 对话框:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Button({
                text = "显示对话框",
                onClick = function() showDialog.value = true end,
                modifier = compose.Modifier().fillMaxWidth(),
            }),
            -- AlertDialog 通过 visible 属性控制显示
            compose.AlertDialog({
                visible = showDialog.value,
                title = "确认操作",
                text = "确定要执行此操作吗？\n此操作不可撤销。",
                confirmText = "确定",
                dismissText = "取消",
                onConfirm = function()
                    showDialog.value = false
                end,
                onDismiss = function()
                    showDialog.value = false
                end,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 2. DropdownMenu 下拉菜单 =====
            compose.Text({ text = "2. DropdownMenu 下拉菜单:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Box({
                contentAlignment = "Center",
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.Button({
                        text = "打开菜单",
                        onClick = function() showMenu.value = true end,
                    }),
                    compose.DropdownMenu({
                        expanded = showMenu.value,
                        onDismissRequest = function()
                            showMenu.value = false
                        end,
                        children = (function()
                            local result = {}
                            for _, item in ipairs(menuItems) do
                                table.insert(result, {
                                    type = "DropdownMenuItem",
                                    text = item,
                                    onClick = function()
                                        selectedOption.value = item
                                        showMenu.value = false
                                    end,
                                })
                            end
                            return result
                        end)(),
                    }),
                },
            }),
            compose.Text({
                textLambda = function()
                    return "选中: " .. selectedOption.value
                end,
                fontSize = 13,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 3. ExposedDropdownMenuBox 下拉选择框 =====
            compose.Text({ text = "3. ExposedDropdownMenuBox 下拉选择:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.ExposedDropdownMenuBox({
                expanded = showExposed.value,
                onExpandedChange = function(v)
                    showExposed.value = v
                end,
                modifier = compose.Modifier().fillMaxWidth(),
                children = {
                    compose.TextField({
                        text = selectedOption.value,
                        readOnly = true,
                        modifier = compose.Modifier()
                            .fillMaxWidth(),
                        trailingIcon = {
                            compose.Icon({
                                name = showExposed.value and "KeyboardArrowUp" or "ArrowDropDown",
                                size = 20,
                                color = 0xFF666666,
                            }),
                        },
                    }),
                    compose.DropdownMenu({
                                        expanded = showExposed.value,
                                        onDismissRequest = function()
                                            showExposed.value = false
                                        end,
                                        children = (function()
                                            local options = {"选项一", "选项二", "选项三", "选项四"}
                                            local result = {}
                                            for _, opt in ipairs(options) do
                                                table.insert(result, {
                                                    type = "DropdownMenuItem",
                                                    text = opt,
                                                    onClick = function()
                                                        selectedOption.value = opt
                                                        showExposed.value = false
                                                    end,
                                                })
                                            end
                                            return result
                                        end)(),
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 4. Popup 弹出窗口 =====
            compose.Text({ text = "4. Popup 弹出窗口:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Button({
                text = "显示 Popup",
                onClick = function() showPopup.value = true end,
                modifier = compose.Modifier().fillMaxWidth(),
            }),

            compose.Popup({
                visible = showPopup.value,
                alignment = "Center",
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                onDismissRequest = function()
                    showPopup.value = false
                end,
                children = {
                    compose.Card({
                        modifier = compose.Modifier()
                            .width(280)
                            .padding(16),
                        children = {
                            compose.Column({
                                modifier = compose.Modifier().padding(16),
                                horizontalAlignment = "CenterHorizontally",
                                children = {
                                    compose.Text({
                                        text = "弹出窗口",
                                        fontSize = 18,
                                        fontWeight = 600,
                                    }),
                                    compose.Spacer({ modifier = compose.Modifier().height(8) }),
                                    compose.Text({
                                        text = "这是通过 Popup 组件显示的弹窗内容",
                                        fontSize = 14,
                                        color = 0xFF666666,
                                    }),
                                    compose.Spacer({ modifier = compose.Modifier().height(12) }),
                                    compose.Button({
                                        text = "关闭",
                                        onClick = function()
                                            showPopup.value = false
                                        end,
                                    }),
                                },
                            }),
                        },
                    }),
                },
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- ===== 5. ModalBottomSheet 底部面板 =====
            compose.Text({ text = "5. ModalBottomSheet 底部面板:", fontSize = 16, fontWeight = 600 }),
            compose.Spacer({ modifier = compose.Modifier().height(8) }),

            compose.Button({
                text = "显示底部面板",
                onClick = function() showSheet.value = true end,
                modifier = compose.Modifier().fillMaxWidth(),
            }),

            compose.ModalBottomSheet({
                visible = showSheet.value,
                onDismissRequest = function()
                    showSheet.value = false
                end,
                dragHandle = true,
                children = {
                    compose.Column({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .padding(24),
                        children = {
                            compose.Text({
                                text = "底部面板",
                                fontSize = 20,
                                fontWeight = 600,
                            }),
                            compose.Spacer({ modifier = compose.Modifier().height(12) }),
                            compose.Text({
                                text = "这是一个 ModalBottomSheet 示例。\n\n支持拖拽关闭、半展开等特性。",
                                fontSize = 14,
                                color = 0xFF666666,
                            }),
                            compose.Spacer({ modifier = compose.Modifier().height(16) }),

                            -- 面板内选项
                            compose.TextButton({
                                text = "选项一",
                                onClick = function() showSheet.value = false end,
                                modifier = compose.Modifier().fillMaxWidth(),
                            }),
                            compose.TextButton({
                                text = "选项二",
                                onClick = function() showSheet.value = false end,
                                modifier = compose.Modifier().fillMaxWidth(),
                            }),
                            compose.TextButton({
                                text = "关闭",
                                onClick = function() showSheet.value = false end,
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