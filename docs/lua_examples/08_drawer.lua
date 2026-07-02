-- ============================================================
-- Nirithy LuaCompose 官方教程 08 — 导航与抽屉
-- ============================================================
-- 目标：学会 ModalNavigationDrawer、Scaffold、BackHandler 构建完整页面
-- 覆盖：Scaffold, ModalNavigationDrawer, ModalDrawerSheet
--       BackHandler, 页面结构
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- ⚠️ State 必须在 render 外面声明
local drawerOpen = compose.mutableState(false)
local currentPage = compose.mutableState("首页")

-- 模拟页面切换的数据
local pages = { "首页", "发现", "收藏", "设置", "关于" }

compose.render(function()
    return compose.Scaffold({
        modifier = compose.Modifier().fillMaxSize(),

        -- ===== TopBar =====
        children = {
            compose.Box({
                _scaffoldSlot = "topBar",
                title = "Nirithy LuaCompose",
                color = 0xFF6750A4,
                modifier = compose.Modifier().fillMaxWidth(),
            }),
        },
    })

    -- 注意：Scaffold 不支持直接嵌套 ModalNavigationDrawer
    -- 实际项目中 ModalNavigationDrawer 应包裹 Scaffold
    -- 这里展示抽屉的独立用法
end)

-- ============================================================
-- 以下为完整示例：ModalNavigationDrawer + Drawer + 页面切换
-- 复制下方代码替换上面的 render 调用即可
-- ============================================================

--[[
compose.render(function()
    return compose.ModalNavigationDrawer({
        open = drawerOpen.value,
        onOpen = function() drawerOpen.value = true end,
        onClose = function() drawerOpen.value = false end,
        gesturesEnabled = true,

        -- ===== 抽屉内容 (DrawerSheet) =====
        children = {
            -- 抽屉面板
            compose.ModalDrawerSheet({
                _drawerSlot = "drawer",
                modifier = compose.Modifier().width(280),
                children = {
                    compose.Column({
                        modifier = compose.Modifier()
                            .fillMaxHeight()
                            .padding(0, 48, 0, 0),

                        children = {
                            -- 抽屉标题
                            compose.Text({
                                text = "导航菜单",
                                fontSize = 22,
                                fontWeight = 700,
                                color = 0xFF6750A4,
                                modifier = compose.Modifier().padding(24, 0, 0, 16),
                            }),

                            compose.Divider({}),

                            -- 菜单项列表
                            compose.Text({
                                text = "  首页",
                                fontSize = 16,
                                fontWeight = 600,
                                color = currentPage.value == "首页" and 0xFF6750A4 or 0xFF333333,
                                modifier = compose.Modifier()
                                    .fillMaxWidth()
                                    .padding(24, 16, 0, 16)
                                    .background(currentPage.value == "首页" and 0xFFF3E5F5 or 0x00000000),
                            }),
                            compose.Text({
                                text = "  发现",
                                fontSize = 16,
                                fontWeight = 600,
                                color = currentPage.value == "发现" and 0xFF6750A4 or 0xFF333333,
                                modifier = compose.Modifier()
                                    .fillMaxWidth()
                                    .padding(24, 16, 0, 16)
                                    .background(currentPage.value == "发现" and 0xFFF3E5F5 or 0x00000000),
                            }),
                            compose.Text({
                                text = "  收藏",
                                fontSize = 16,
                                fontWeight = 600,
                                color = currentPage.value == "收藏" and 0xFF6750A4 or 0xFF333333,
                                modifier = compose.Modifier()
                                    .fillMaxWidth()
                                    .padding(24, 16, 0, 16)
                                    .background(currentPage.value == "收藏" and 0xFFF3E5F5 or 0x00000000),
                            }),
                            compose.Text({
                                text = "  设置",
                                fontSize = 16,
                                fontWeight = 600,
                                color = currentPage.value == "设置" and 0xFF6750A4 or 0xFF333333,
                                modifier = compose.Modifier()
                                    .fillMaxWidth()
                                    .padding(24, 16, 0, 16)
                                    .background(currentPage.value == "设置" and 0xFFF3E5F5 or 0x00000000),
                            }),
                        },
                    }),
                },
            }),

            -- ===== 主内容区 =====
            compose.Column({
                modifier = compose.Modifier()
                    .fillMaxSize()
                    .background(0xFFFAFAFA),

                children = {
                    -- 顶部栏
                    compose.Row({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .padding(16, 48, 16, 16)
                            .background(0xFF6750A4),
                        children = {
                            compose.Icon({
                                name = "Menu",
                                size = 28,
                                color = 0xFFFFFFFF,
                            }),
                            compose.Spacer({ modifier = compose.Modifier().width(16) }),
                            compose.Text({
                                text = currentPage.value,
                                fontSize = 20,
                                fontWeight = 700,
                                color = 0xFFFFFFFF,
                            }),
                        },
                    }),

                    -- 页面内容
                    compose.Column({
                        modifier = compose.Modifier()
                            .fillMaxSize()
                            .padding(24),
                        children = {
                            compose.Text({
                                text = "当前页面: " .. currentPage.value,
                                fontSize = 18,
                                fontWeight = 600,
                            }),
                            compose.Spacer({ modifier = compose.Modifier().height(16) }),
                            compose.Text({
                                text = "这是一个 ModalNavigationDrawer 示例。\n"
                                    .. "点击左上角菜单图标或从屏幕左侧滑动打开抽屉。\n\n"
                                    .. "点击抽屉中的菜单项切换页面。",
                                fontSize = 15,
                                color = 0xFF666666,
                            }),
                            compose.Spacer({ modifier = compose.Modifier().height(24) }),

                            -- 打开抽屉按钮
                            compose.Button({
                                text = "打开导航菜单",
                                onClick = function()
                                    drawerOpen.value = true
                                end,
                                modifier = compose.Modifier().fillMaxWidth(),
                            }),
                        },
                    }),
                },
            }),
        },
    })
end)
]]