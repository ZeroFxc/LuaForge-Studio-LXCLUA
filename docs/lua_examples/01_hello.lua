-- ============================================================
-- Nirithy LuaCompose 官方教程 01 — 入门
-- ============================================================
-- 目标：学会 compose.render() 入口、Text 组件、Modifier 基础
-- 覆盖：compose.render, compose.Text, compose.Modifier, padding, fillMaxWidth, fontSize, color, fontWeight
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

compose.render(function()
    return compose.Column({
        modifier = compose.Modifier()
            .fillMaxSize()
            .padding(24),

        children = {
            -- 标题
            compose.Text({
                text = "Hello Nirithy LuaCompose!",
                fontSize = 24,
                fontWeight = 700,
                color = 0xFF6750A4,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            -- 正文
            compose.Text({
                text = "这是一个纯 Lua 驱动的 Compose UI 框架。\n"
                    .. "所有 UI 代码用 Lua 编写，实时渲染为原生 Android 界面。",
                fontSize = 16,
                color = 0xFF333333,
            }),

            compose.Spacer({ modifier = compose.Modifier().height(24) }),

            -- 使用 Modifier 装饰文本
            compose.Text({
                text = "这段文字有蓝色背景和圆角",
                fontSize = 14,
                color = 0xFFFFFFFF,
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .background(0xFF2196F3)
                    .padding(12),
            }),

            compose.Spacer({ modifier = compose.Modifier().height(16) }),

            compose.Text({
                text = "这段文字有边框",
                fontSize = 14,
                modifier = compose.Modifier()
                    .fillMaxWidth()
                    .padding(12),
            }),
        },
    })
end)