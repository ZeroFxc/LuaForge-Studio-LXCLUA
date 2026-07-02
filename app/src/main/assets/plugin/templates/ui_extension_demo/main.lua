-- ============================================
-- UI 扩展点全面演示插件
-- 演示工具栏按钮、菜单项、事件监听、项目徽章、快捷操作、
-- 底部面板、侧滑栏、设置项、关于页面、悬浮球等所有UI扩展
-- ============================================
--
-- 【重要】Lua 调用 Java 对象方法一律使用 `.`，不要用 `:`
-- 错误: plugin.mainpage:getProjects()
-- 正确: plugin.mainpage.getProjects()
--

plugin.sys.log("UIExtensionDemo", "=== UI 扩展演示插件已加载 ===")

-- ============================================
-- 1. 工具栏按钮扩展
-- 在首页工具栏右侧添加"统计"按钮，点击显示项目统计
-- ============================================
local statsBtnKey = plugin.ui.addToolbarButton(
    plugin.ui.POINTS.HOME_TOOLBAR_END,
    "ui_stats_btn",
    "统计",
    0,
    function()
        local projects = plugin.mainpage.getProjects()
        local total = #projects
        local withTag = 0
        local withVersion = 0
        for _, p in ipairs(projects) do
            -- p.version 和 p.tags 由 getProjects 返回
            if p.version and #p.version > 0 then withVersion = withVersion + 1 end
            if p.tags and #p.tags > 0 then withTag = withTag + 1 end
        end
        plugin.ui.showMessage("项目统计", string.format(
            "项目总数: %d\n带标签项目: %d\n有版本标记: %d",
            total, withTag, withVersion
        ))
    end
)

-- 编辑器工具栏右侧添加"日志"按钮
local logBtnKey = plugin.ui.addToolbarButton(
    plugin.ui.POINTS.EDITOR_TOOLBAR_END,
    "ui_log_btn",
    "日志",
    0,
    function()
        plugin.ui.showInputDialog("查看日志", "输入插件ID", "", function(pluginId)
            if pluginId and #pluginId > 0 then
                local logs = plugin.logger.query(pluginId, 50, nil, nil)
                local text = ""
                for i, entry in ipairs(logs) do
                    text = text .. string.format("[%s] %s\n", entry.level or "I", entry.message or "")
                end
                if #text == 0 then text = "没有日志" end
                plugin.ui.showTextDialog(pluginId .. " 日志", text)
            end
        end)
    end
)

-- ============================================
-- 2. 菜单项扩展（首页更多菜单）
-- ============================================
plugin.ui.addMenuItem(
    plugin.ui.POINTS.HOME_MORE_MENU,
    "ui_export_list",
    "导出项目列表",
    1,
    function()
        local projects = plugin.mainpage.getProjects()
        local lines = {"LXC-LUA 项目列表导出:"}
        lines[#lines+1] = "导出时间: " .. os.date("%Y-%m-%d %H:%M:%S")
        lines[#lines+1] = "--------------------------------"
        for i, p in ipairs(projects) do
            lines[#lines+1] = string.format("%d. %s  [%s]", i, p.name or "?", p.path or "?")
            if p.tags and #p.tags > 0 then
                lines[#lines+1] = "   标签: " .. table.concat(p.tags, ", ")
            end
        end
        plugin.ui.showTextDialog("项目列表导出", table.concat(lines, "\n"))
    end
)

-- ============================================
-- 3. 事件监听
-- ============================================

-- 项目点击
plugin.events.on("onProjectClick", function(projectId, projectName, projectPath)
    plugin.sys.log("UIExtensionDemo", "[点击] " .. tostring(projectName))
end)

-- 项目长按：给长按的项目加一个徽章（演示徽章API，不持久化）
plugin.events.on("onProjectLongPress", function(projectId, projectName, projectPath)
    plugin.mainpage.setProjectBadge(projectId, "★查看", 0xFFFFB300)
end)

-- 左滑/右滑
plugin.events.on("onProjectSwipeLeft", function(projectId, projectName)
    plugin.sys.log("UIExtensionDemo", "[左滑] " .. tostring(projectName))
end)
plugin.events.on("onProjectSwipeRight", function(projectId, projectName)
    plugin.sys.log("UIExtensionDemo", "[右滑] " .. tostring(projectName))
end)

-- 搜索变化
plugin.events.on("onSearchQueryChanged", function(query)
    plugin.sys.log("UIExtensionDemo", "[搜索] " .. tostring(query))
end)

-- 排序变化
plugin.events.on("onSortOrderChanged", function(sortOrder)
    plugin.sys.log("UIExtensionDemo", "[排序] " .. tostring(sortOrder))
end)

-- 分类切换
plugin.events.on("onCategoryChanged", function(categoryId)
    plugin.sys.log("UIExtensionDemo", "[分类] " .. tostring(categoryId or "全部"))
end)

-- 多选模式
plugin.events.on("onMultiSelectEnter", function()
    plugin.sys.log("UIExtensionDemo", "[多选模式] 进入")
end)
plugin.events.on("onMultiSelectExit", function()
    plugin.sys.log("UIExtensionDemo", "[多选模式] 退出")
end)
plugin.events.on("onMultiSelectionChanged", function(selectedCount, selectedIds)
    plugin.sys.log("UIExtensionDemo", "[多选] 已选 " .. tostring(selectedCount) .. " 项")
end)

-- 页面切换
plugin.events.on("onPageChanged", function(pageId, fromPageId)
    plugin.sys.log("UIExtensionDemo", "[页面] " .. tostring(fromPageId) .. " -> " .. tostring(pageId))
end)

-- 主题变化
plugin.events.on("onThemeChanged", function(darkMode)
    plugin.sys.log("UIExtensionDemo", "[主题] darkMode=" .. tostring(darkMode))
end)

-- 插件消息接收
plugin.events.onMessage(function(fromId, action, dataJson)
    plugin.sys.log("UIExtensionDemo", string.format("[消息] from=%s action=%s", tostring(fromId), tostring(action)))
end)

-- ============================================
-- 4. 自定义事件注册
-- ============================================
plugin.events.registerEvent("onUIExtensionDemoAction", "UI扩展演示插件动作触发")

-- ============================================
-- 5. 快捷操作
-- ============================================
plugin.menu.addQuickAction("ui_demo_toast", "测试Toast", function()
    plugin.sys.toast("UI扩展演示Toast测试！")
end)

plugin.menu.addQuickAction("ui_demo_msg", "广播消息", function()
    plugin.events.broadcastMessage("ui_demo_hello", '{"from":"ui_extension_demo","time":' .. os.time() .. '}')
    plugin.sys.toast("已广播消息")
end)

plugin.menu.addQuickAction("ui_demo_badges", "添加示例徽章", function()
    local projects = plugin.mainpage.getProjects()
    local count = 0
    for i, p in ipairs(projects) do
        if i <= 3 then
            plugin.mainpage.setProjectBadge(p.id, "Demo", 0xFFE91E63)
            count = count + 1
        end
    end
    plugin.sys.toast("已给前 " .. count .. " 个项目添加Demo徽章")
end)

plugin.menu.addQuickAction("ui_demo_clear_badges", "清除本插件徽章", function()
    plugin.mainpage.clearAllBadges()
    plugin.sys.toast("已清除本插件设置的徽章")
end)

-- ============================================
-- 6. 侧滑栏菜单
-- ============================================
plugin.nav.addSidebarItem("ui_demo_nav", "UI扩展演示", "custom", "extension", function()
    plugin.ui.showMessage("UI 扩展演示",
        "本插件演示了所有UI扩展点:\n\n" ..
        "【工具栏按钮】\n" ..
        "- HOME_TOOLBAR_END (首页顶部)\n" ..
        "- EDITOR_TOOLBAR_END (编辑器顶部)\n\n" ..
        "【菜单项】\n" ..
        "- HOME_MORE_MENU (首页更多菜单)\n\n" ..
        "【事件监听】\n" ..
        "- 项目点击/长按/左滑/右滑\n" ..
        "- 搜索/排序/分类变化\n" ..
        "- 多选模式进入/退出/选择变化\n" ..
        "- 页面切换/主题变化\n" ..
        "- 插件消息通信\n\n" ..
        "【其他】\n" ..
        "- 项目徽章\n" ..
        "- 快捷操作\n" ..
        "- 侧滑栏菜单\n" ..
        "- 设置项\n" ..
        "- 关于页面\n\n" ..
        "【重要】所有 Java 方法调用均使用 . 而非 :")
end)

-- ============================================
-- 7. 设置项扩展（在设置页面添加自定义分组）
-- ============================================
plugin.settings.addSection("ui_demo_settings", "UI扩展演示设置", 999)
plugin.settings.addSwitch("ui_demo_switch1", "ui_demo_settings",
    "启用演示功能", "开启后会显示一些演示效果", true, function(checked)
        plugin.sys.log("UIExtensionDemo", "[设置] 演示功能=" .. tostring(checked))
        plugin.sys.toast("演示功能: " .. (checked and "已启用" or "已禁用"))
    end)
plugin.settings.addInput("ui_demo_name", "ui_demo_settings",
    "演示名称", "输入自定义名称", "UI演示", function(value)
        plugin.sys.log("UIExtensionDemo", "[设置] 名称=" .. tostring(value))
    end)
plugin.settings.addButton("ui_demo_reset", "ui_demo_settings",
    "清除演示徽章", "清除本插件添加的所有徽章", function()
        plugin.mainpage.clearAllBadges()
        plugin.sys.toast("徽章已清除")
    end)

-- ============================================
-- 8. 关于页面扩展
-- ============================================
plugin.about.addSection("ui_demo_about", "UI 扩展演示插件", 100)
plugin.about.addInfo("ui_demo_about_desc", "ui_demo_about",
    "功能说明",
    "演示工具栏按钮、菜单项、事件监听、徽章、快捷操作、侧滑栏、设置项等所有UI扩展API",
    "info", 0xFF6A1B9A)
plugin.about.addInfo("ui_demo_about_points", "ui_demo_about",
    "扩展点示例",
    "首页工具栏、编辑器工具栏、首页菜单、项目徽章、快捷操作、侧滑栏、设置页、关于页",
    "extension", 0xFF1565C0)
plugin.about.addCallback("ui_demo_about_toast", "ui_demo_about",
    "测试Toast", "发送一个测试Toast",
    "notification", 0xFF2E7D32, function()
        plugin.sys.toast("来自UI扩展演示的Toast！")
    end)
plugin.about.addCallback("ui_demo_about_broadcast", "ui_demo_about",
    "广播消息", "向所有插件广播hello消息",
    "play", 0xFFEF6C00, function()
        plugin.events.broadcastMessage("hello_from_ui_demo", '{"msg":"hi from ui extension demo"}')
        plugin.sys.toast("已广播消息")
    end)

-- ============================================
-- 9. 悬浮球扩展（添加悬浮球菜单项）
-- ============================================
if plugin.floating and plugin.floating.addMenuItem then
    plugin.floating.addMenuItem("ui_demo_float", "UI演示操作", function()
        plugin.ui.showMessage("悬浮球菜单", "这是由UI扩展演示插件添加的悬浮球菜单项")
    end)
end

-- ============================================
-- 10. 插件卸载时清理
-- ============================================
plugin.events.on("onPluginUnloaded", function(pid)
    if pid == plugin.getPluginId() then
        plugin.ui.removeToolbarButton(statsBtnKey)
        plugin.ui.removeToolbarButton(logBtnKey)
        plugin.ui.clearMenuItems()
        plugin.ui.clearBottomPanelItems()
        plugin.mainpage.clearAllBadges()
        plugin.sys.log("UIExtensionDemo", "插件已卸载，资源已清理")
    end
end)

plugin.sys.log("UIExtensionDemo", "初始化完成")
