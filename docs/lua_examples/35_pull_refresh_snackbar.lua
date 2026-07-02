-- ============================================================
-- Nirithy LuaCompose 官方教程 35 — 下拉刷新 & Snackbar & LazyListScope
-- ============================================================
-- 目标：PullToRefreshBox、SnackbarHost、LazyListScopeWrapper (item/items DSL)
-- 覆盖：PullToRefreshBox, SnackbarHost, SnackbarHostState, LazyColumn with scope DSL
-- 用法：复制到项目 main.lua 直接运行
-- ============================================================

local compose = _G.compose

-- 状态
local isRefreshing = compose.state(false)
local dataCount = compose.state(20)
local snackbarState = compose.SnackbarHostState()

-- 模拟刷新（使用 delayMs 非阻塞延迟回调）
local function simulateRefresh()
    isRefreshing.value = true
    -- ★ compose.delayMs(ms, callback) — 非阻塞延迟，可在任意上下文调用
    compose.delayMs(1500, function()
        dataCount.value = dataCount.value + 10
        isRefreshing.value = false
        compose.showSnackbar(snackbarState, "刷新完成，新增10条数据", "撤销", "Short")
    end)
end

compose.render(function()
    return compose.Scaffold({
        snackbarHost = {
            compose.SnackbarHost({
                hostState = snackbarState,
            }),
        },

        children = {
            compose.Column({
                modifier = compose.Modifier()
                    .fillMaxSize()
                    .padding(24),

                children = {

                    compose.Text({
                        text = "下拉刷新 & Snackbar",
                        fontSize = 22,
                        fontWeight = 700,
                        color = 0xFF6750A4,
                    }),
                    compose.Spacer({ modifier = compose.Modifier().height(20) }),

                    -- ===== 1. PullToRefreshBox 下拉刷新 =====
                    compose.Text({ text = "1. PullToRefreshBox 下拉刷新:", fontSize = 16, fontWeight = 600 }),
                    compose.Spacer({ modifier = compose.Modifier().height(8) }),

                    compose.Card({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .height(280),
                        children = {
                            compose.PullToRefreshBox({
                                isRefreshing = isRefreshing.value,
                                onRefresh = function()
                                    simulateRefresh()
                                end,
                                modifier = compose.Modifier().fillMaxSize(),
                                children = {
                                    compose.LazyColumn({
                                        modifier = compose.Modifier().fillMaxSize(),
                                        children = function(scope)
                                            -- ★ LazyListScopeWrapper DSL:
                                            --    Lua 侧直接调用 scope.item() / scope.items()
                                            --    实现真正的懒加载，比一次性返回节点列表高效
                                            scope.item(function()
                                                return compose.Text({
                                                    text = "下拉刷新列表 (共" .. dataCount.value .. "项)",
                                                    fontSize = 14,
                                                    fontWeight = 600,
                                                    modifier = compose.Modifier().padding(8),
                                                })
                                            end)

                                            scope.items(dataCount.value, function(i)
                                                return compose.Card({
                                                    modifier = compose.Modifier()
                                                        .fillMaxWidth()
                                                        .padding(0, 2, 0, 2),
                                                    children = {
                                                        compose.Row({
                                                            modifier = compose.Modifier()
                                                                .fillMaxWidth()
                                                                .padding(12),
                                                            verticalAlignment = "CenterVertically",
                                                            children = {
                                                                compose.Box({
                                                                    modifier = compose.Modifier()
                                                                        .size(36, 36)
                                                                        .backgroundRounded(0xFFE8DEF8, 18),
                                                                    contentAlignment = "Center",
                                                                    children = {
                                                                        compose.Text({
                                                                            text = tostring(i),
                                                                            fontSize = 12,
                                                                            fontWeight = 600,
                                                                            color = 0xFF6750A4,
                                                                        }),
                                                                    },
                                                                }),
                                                                compose.Spacer({ modifier = compose.Modifier().width(12) }),
                                                                compose.Text({
                                                                    text = "列表项 " .. i,
                                                                    fontSize = 14,
                                                                }),
                                                            },
                                                        }),
                                                    },
                                                })
                                            end)
                                        end,
                                    }),
                                },
                            }),
                        },
                    }),

                    compose.Spacer({ modifier = compose.Modifier().height(24) }),

                    -- ===== 2. Snackbar 使用方式 =====
                    compose.Text({ text = "2. Snackbar 使用方式:", fontSize = 16, fontWeight = 600 }),
                    compose.Spacer({ modifier = compose.Modifier().height(8) }),

                    compose.Text({
                        text = [[Snackbar 通过 Scaffold 的 snackbarHost 插槽注入。

1. 创建 SnackbarHostState: compose.SnackbarHostState()
2. 在 Scaffold 中设置 snackbarHost 为 SnackbarHost
3. 调用 compose.showSnackbar(state, "消息", "动作", "时长", onResult)

支持三种时长: "Short", "Long", "Indefinite"
onResult 回调参数: "ActionPerformed"(点击动作) 或 "Dismissed"(关闭)]],
                        fontSize = 13,
                        color = 0xFF666666,
                    }),

                    compose.Spacer({ modifier = compose.Modifier().height(12) }),

                    compose.Row({
                        modifier = compose.Modifier().fillMaxWidth(),
                        children = {
                            compose.Button({
                                text = "显示短提示",
                                onClick = function()
                                    compose.showSnackbar(snackbarState, "这是一条短提示", "关闭", "Short",
                                        function(result)
                                            -- result: "ActionPerformed" 或 "Dismissed"
                                        end
                                    )
                                end,
                                modifier = compose.Modifier().weight(1),
                            }),
                            compose.Spacer({ modifier = compose.Modifier().width(4) }),
                            compose.Button({
                                text = "显示长提示",
                                onClick = function()
                                    compose.showSnackbar(snackbarState, "这是一条长提示，会持续更长时间", "知道了", "Long",
                                        function(result)
                                            -- result: "ActionPerformed" 或 "Dismissed"
                                        end
                                    )
                                end,
                                modifier = compose.Modifier().weight(1),
                            }),
                        },
                    }),

                    compose.Spacer({ modifier = compose.Modifier().height(24) }),

                    -- ===== 3. LazyColumn scope DSL 详解 =====
                    compose.Text({ text = "3. LazyListScope DSL 详解:", fontSize = 16, fontWeight = 600 }),
                    compose.Spacer({ modifier = compose.Modifier().height(8) }),

                    compose.Text({
                        text = [[LazyColumn / LazyRow 的 children 支持两种模式：

1. 静态 children 列表 — 适合少量固定项
2. children = function(scope) DSL — 推荐方式

scope 提供以下方法：
  scope.item(function() ... end)        — 添加单个项
  scope.item(key, function() ... end)   — 带 key 的单个项
  scope.items(count, function(i) ... end) — 批量添加
  scope.items(count, keyFn, function(i) ... end) — 带 key 批量

Lua 索引从 1 开始，与 Kotlin 侧自动转换。]],
                        fontSize = 13,
                        color = 0xFF666666,
                    }),

                    compose.Spacer({ modifier = compose.Modifier().height(12) }),

                    compose.Card({
                        modifier = compose.Modifier()
                            .fillMaxWidth()
                            .height(200),
                        children = {
                            compose.LazyColumn({
                                modifier = compose.Modifier().fillMaxSize(),
                                children = function(scope)
                                    -- 表头
                                    scope.item(function()
                                        return compose.Text({
                                            text = "DSL 示例列表",
                                            fontSize = 14,
                                            fontWeight = 600,
                                            color = 0xFF6750A4,
                                            modifier = compose.Modifier().padding(12, 8, 12, 4),
                                        })
                                    end)

                                    -- 用 key 添加项
                                    scope.item("header", function()
                                        return compose.Divider({})
                                    end)

                                    -- 批量生成 5 项
                                    scope.items(5, function(i)
                                        return compose.Text({
                                            text = "项 " .. i,
                                            fontSize = 14,
                                            modifier = compose.Modifier().padding(12, 6, 12, 6),
                                        })
                                    end)

                                    -- 带 key 的批量生成
                                    scope.items(3,
                                        function(i) return "item_" .. i end,  -- key 函数
                                        function(i)                            -- 内容函数
                                            return compose.Text({
                                                text = "带 key 的项 " .. i,
                                                fontSize = 14,
                                                fontWeight = 500,
                                                modifier = compose.Modifier().padding(12, 6, 12, 6),
                                            })
                                        end
                                    )
                                end,
                            }),
                        },
                    }),

                    compose.Spacer({ modifier = compose.Modifier().height(24) }),

                    -- ===== 4. 手动触发刷新按钮 =====
                    compose.Text({ text = "4. 手动触发刷新:", fontSize = 16, fontWeight = 600 }),
                    compose.Spacer({ modifier = compose.Modifier().height(8) }),

                    compose.Row({
                        modifier = compose.Modifier().fillMaxWidth(),
                        children = {
                            compose.Button({
                                text = "刷新数据",
                                onClick = function()
                                    simulateRefresh()
                                end,
                                modifier = compose.Modifier().weight(1),
                            }),
                            compose.Spacer({ modifier = compose.Modifier().width(4) }),
                            compose.Button({
                                text = "清空列表",
                                onClick = function()
                                    local saved = dataCount.value
                                    dataCount.value = 0
                                    compose.showSnackbar(snackbarState, "列表已清空", "撤销", "Short",
                                        function(result)
                                            if result == "ActionPerformed" then
                                                dataCount.value = saved
                                            end
                                        end
                                    )
                                end,
                                modifier = compose.Modifier().weight(1),
                            }),
                        },
                    }),

                    compose.Spacer({ modifier = compose.Modifier().height(32) }),
                },
            }),
        },
    })
end)