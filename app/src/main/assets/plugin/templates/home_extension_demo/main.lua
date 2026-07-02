-- ============================================
-- 首页扩展示范插件
-- 演示首页工具栏按钮、FAB、分类栏扩展、徽章等功能
-- ============================================
--
-- plugin.mainpage 扩展 API（如存在则使用，否则降级提示）：
--   addToolbarAction(key, icon, label, callback)  -- 添加顶部工具栏按钮
--   addHomeFab(key, icon, label, callback)        -- 添加浮动操作按钮
--   addCategoryBarItem(key, icon, label, callback) -- 添加分类栏项目
--   setProjectBadge(id, text, color)              -- 设置项目卡片徽章
--   clearProjectBadge(id)                         -- 清除单个徽章
--   clearAllBadges()                              -- 清除所有徽章
--   getProjectIds()                               -- 获取所有项目ID
--   getProjectName(id) / getProjectPath(id)       -- 获取项目信息
--   getProjectCount()                             -- 获取项目总数
--   getSelectedProjectIds()                       -- 获取选中的项目
--   setMultiSelectMode(bool) / enterMultiSelectMode / exitMultiSelectMode
--   navigateToProject(id)                         -- 导航到项目
--   refreshProjects()                             -- 刷新项目列表
--   showToast(msg)                                -- 显示 Toast（可能不存在，用 plugin.sys.toast 代替）
-- ============================================

plugin.sys.log("HomeExtDemo", "首页扩展示范插件已加载")

-- 注册表，保存已注册的 UI 元素 key，便于卸载时清理
local registered = {
    toolbarActions = {},
    fabs = {},
    categoryItems = {},
    badges = {}
}

-- ============================================
-- 安全调用包装
-- ============================================
local function safeApi(name, fn)
    local ok, result = pcall(fn)
    if not ok then
        plugin.sys.log("HomeExtDemo", "[" .. name .. "] 调用失败: " .. tostring(result))
        return nil
    end
    return result
end

-- ============================================
-- 1. 添加顶部工具栏按钮（刷新图标）
-- ============================================
local function addToolbarDemo()
    if not plugin.mainpage.addToolbarAction then
        plugin.sys.log("HomeExtDemo", "addToolbarAction API 不存在，跳过工具栏按钮注册")
        return false
    end

    local key = "home_ext_refresh"
    local ok = pcall(function()
        plugin.mainpage.addToolbarAction(key, "refresh", "刷新", function()
            plugin.sys.log("HomeExtDemo", "工具栏按钮点击: 刷新")
            plugin.sys.toast("刷新项目列表...")
            -- 尝试调用 refreshProjects
            if plugin.mainpage.refreshProjects then
                plugin.mainpage.refreshProjects()
            end
            -- 刷新徽章
            refreshNewBadges()
        end)
    end)

    if ok then
        table.insert(registered.toolbarActions, key)
        plugin.sys.log("HomeExtDemo", "已注册工具栏按钮: " .. key)
        return true
    end
    return false
end

-- ============================================
-- 2. 添加首页 FAB（Add 图标）
-- ============================================
local function addFabDemo()
    if not plugin.mainpage.addHomeFab then
        plugin.sys.log("HomeExtDemo", "addHomeFab API 不存在，跳过 FAB 注册")
        return false
    end

    local key = "home_ext_fab_add"
    local ok = pcall(function()
        plugin.mainpage.addHomeFab(key, "add", "新建项目", function()
            plugin.sys.log("HomeExtDemo", "FAB 点击: 新建项目")
            plugin.sys.toast("点击了新建项目 FAB")
            -- 演示：显示项目数量
            local count = "?"
            if plugin.mainpage.getProjectCount then
                count = tostring(plugin.mainpage.getProjectCount())
            end
            plugin.ui.showMessage("新建项目",
                "当前项目总数: " .. count .. "\n\n这里可以弹出新建项目对话框（演示）")
        end)
    end)

    if ok then
        table.insert(registered.fabs, key)
        plugin.sys.log("HomeExtDemo", "已注册 FAB: " .. key)
        return true
    end
    return false
end

-- ============================================
-- 3. 添加分类栏项目（Star 图标）
-- ============================================
local function addCategoryDemo()
    if not plugin.mainpage.addCategoryBarItem then
        plugin.sys.log("HomeExtDemo", "addCategoryBarItem API 不存在，跳过分类栏注册")
        return false
    end

    local key = "home_ext_starred"
    local ok = pcall(function()
        plugin.mainpage.addCategoryBarItem(key, "star", "收藏", function()
            plugin.sys.log("HomeExtDemo", "分类栏点击: 收藏")
            plugin.sys.toast("已切换到收藏分类（演示）")
        end)
    end)

    if ok then
        table.insert(registered.categoryItems, key)
        plugin.sys.log("HomeExtDemo", "已注册分类栏项: " .. key)
        return true
    end
    return false
end

-- ============================================
-- 4. 为前 3 个项目添加"新"徽章
-- ============================================
function refreshNewBadges()
    if not plugin.mainpage.setProjectBadge then
        plugin.sys.log("HomeExtDemo", "setProjectBadge API 不存在，跳过徽章设置")
        return
    end
    if not plugin.mainpage.getProjectIds then
        plugin.sys.log("HomeExtDemo", "getProjectIds API 不存在，无法获取项目列表")
        return
    end

    -- 先清除之前由本插件添加的徽章
    for _, pid in ipairs(registered.badges) do
        pcall(function() plugin.mainpage.clearProjectBadge(pid) end)
    end
    registered.badges = {}

    local projectIds = plugin.mainpage.getProjectIds()
    if not projectIds then
        plugin.sys.log("HomeExtDemo", "getProjectIds 返回 nil")
        return
    end

    local badgeColor = 0xFFE91E63 -- 粉红色
    local limit = math.min(3, #projectIds)
    for i = 1, limit do
        local pid = tostring(projectIds[i])
        local ok = pcall(function()
            plugin.mainpage.setProjectBadge(pid, "新", badgeColor)
        end)
        if ok then
            table.insert(registered.badges, pid)
            local pname = "?"
            if plugin.mainpage.getProjectName then
                pcall(function() pname = plugin.mainpage.getProjectName(pid) end)
            end
            plugin.sys.log("HomeExtDemo", "已为项目添加「新」徽章: " .. tostring(pname))
        end
    end
    plugin.sys.log("HomeExtDemo", "共为 " .. limit .. " 个项目添加「新」徽章")
end

-- ============================================
-- 5. 演示 API：navigateToProject / refreshProjects / showToast
--            getSelectedProjectIds / setMultiSelectMode
-- ============================================

-- 5.1 演示导航到第一个项目
local function demoNavigate()
    if not plugin.mainpage.getProjectIds then
        plugin.sys.toast("getProjectIds API 不存在")
        return
    end
    local ids = plugin.mainpage.getProjectIds()
    if not ids or #ids == 0 then
        plugin.sys.toast("没有可导航的项目")
        return
    end
    local firstId = tostring(ids[1])
    local name = firstId
    if plugin.mainpage.getProjectName then
        pcall(function() name = plugin.mainpage.getProjectName(firstId) end)
    end
    plugin.sys.log("HomeExtDemo", "尝试导航到项目: " .. tostring(name))
    if plugin.mainpage.navigateToProject then
        local ok = pcall(function() plugin.mainpage.navigateToProject(firstId) end)
        if ok then
            plugin.sys.toast("已导航到项目: " .. tostring(name))
        else
            plugin.sys.toast("navigateToProject 调用失败")
        end
    else
        plugin.sys.toast("navigateToProject API 不存在，目标项目: " .. tostring(name))
    end
end

-- 5.2 演示刷新项目列表
local function demoRefresh()
    plugin.sys.log("HomeExtDemo", "刷新项目列表")
    if plugin.mainpage.refreshProjects then
        pcall(function() plugin.mainpage.refreshProjects() end)
        plugin.sys.toast("项目列表已刷新")
    else
        -- 降级：刷新徽章代替
        refreshNewBadges()
        plugin.sys.toast("refreshProjects API 不存在，已刷新徽章")
    end
end

-- 5.3 演示获取选中项目
local function demoGetSelected()
    if not plugin.mainpage.getSelectedProjectIds then
        plugin.sys.toast("getSelectedProjectIds API 不存在")
        return
    end
    local selected = plugin.mainpage.getSelectedProjectIds()
    local count = selected and #selected or 0
    plugin.sys.toast("当前选中 " .. count .. " 个项目")
    plugin.sys.log("HomeExtDemo", "选中项目数: " .. count)
    if count > 0 then
        local names = {}
        for i = 1, count do
            local pid = tostring(selected[i])
            local n = pid
            if plugin.mainpage.getProjectName then
                pcall(function() n = plugin.mainpage.getProjectName(pid) end)
            end
            table.insert(names, tostring(n))
        end
        plugin.ui.showMessage("选中的项目", table.concat(names, "\n"))
    end
end

-- 5.4 演示多选模式切换
local function demoMultiSelect()
    if plugin.mainpage.setMultiSelectMode then
        local inMulti = false
        if plugin.mainpage.isInMultiSelectMode then
            pcall(function() inMulti = plugin.mainpage.isInMultiSelectMode() end)
        end
        local newMode = not inMulti
        pcall(function() plugin.mainpage.setMultiSelectMode(newMode) end)
        plugin.sys.toast(newMode and "已进入多选模式" or "已退出多选模式")
    elseif plugin.mainpage.enterMultiSelectMode and plugin.mainpage.exitMultiSelectMode then
        local inMulti = false
        if plugin.mainpage.isInMultiSelectMode then
            pcall(function() inMulti = plugin.mainpage.isInMultiSelectMode() end)
        end
        if inMulti then
            pcall(function() plugin.mainpage.exitMultiSelectMode() end)
            plugin.sys.toast("已退出多选模式")
        else
            pcall(function() plugin.mainpage.enterMultiSelectMode() end)
            plugin.sys.toast("已进入多选模式")
        end
    else
        plugin.sys.toast("多选模式 API 不存在")
    end
end

-- ============================================
-- 注册快捷操作
-- ============================================
plugin.menu.addQuickAction("he_navigate", "导航到首个项目", demoNavigate)
plugin.menu.addQuickAction("he_refresh", "刷新项目列表", demoRefresh)
plugin.menu.addQuickAction("he_selected", "查看选中项目", demoGetSelected)
plugin.menu.addQuickAction("he_multiselect", "切换多选模式", demoMultiSelect)
plugin.menu.addQuickAction("he_badges", "添加「新」徽章", refreshNewBadges)
plugin.menu.addQuickAction("he_clear_badges", "清除徽章", function()
    if plugin.mainpage.clearAllBadges then
        plugin.mainpage.clearAllBadges()
        registered.badges = {}
        plugin.sys.toast("所有徽章已清除")
    else
        plugin.sys.toast("clearAllBadges API 不存在")
    end
end)

-- ============================================
-- 侧滑栏菜单项
-- ============================================
plugin.nav.addSidebarItem("home_ext_menu", "首页扩展示范", "custom", "home", function()
    plugin.sys.log("HomeExtDemo", "点击侧滑栏：首页扩展示范")

    local toolbarOk = plugin.mainpage.addToolbarAction and true or false
    local fabOk = plugin.mainpage.addHomeFab and true or false
    local catOk = plugin.mainpage.addCategoryBarItem and true or false
    local navOk = plugin.mainpage.navigateToProject and true or false
    local refreshOk = plugin.mainpage.refreshProjects and true or false

    local info = "=== 首页扩展 API 支持情况 ===\n\n"
    info = info .. "工具栏按钮 addToolbarAction: " .. (toolbarOk and "✓" or "✗") .. "\n"
    info = info .. "浮动按钮 addHomeFab: " .. (fabOk and "✓" or "✗") .. "\n"
    info = info .. "分类栏 addCategoryBarItem: " .. (catOk and "✓" or "✗") .. "\n"
    info = info .. "导航 navigateToProject: " .. (navOk and "✓" or "✗") .. "\n"
    info = info .. "刷新 refreshProjects: " .. (refreshOk and "✓" or "✗") .. "\n\n"

    local count = "?"
    if plugin.mainpage.getProjectCount then
        pcall(function() count = tostring(plugin.mainpage.getProjectCount()) end)
    end
    info = info .. "当前项目总数: " .. count .. "\n"
    info = info .. "已添加徽章: " .. #registered.badges .. " 个"

    plugin.ui.showMessage("首页扩展示范", info)
end)

-- ============================================
-- 关于页面扩展
-- ============================================
plugin.about.addSection("home_ext_about", "首页扩展示范", 0)
plugin.about.addInfo("home_ext_desc", "home_ext_about",
    "功能说明", "演示首页工具栏按钮、FAB、分类栏、徽章等扩展能力",
    "home", 0xFF36618E)
plugin.about.addCallback("home_ext_add_badges", "home_ext_about",
    "添加「新」徽章", "为前 3 个项目添加「新」徽章",
    "star", 0xFFE91E63, function()
        refreshNewBadges()
        plugin.sys.toast("已尝试为前 3 个项目添加徽章")
    end)
plugin.about.addCallback("home_ext_clear_badges", "home_ext_about",
    "清除徽章", "清除所有项目徽章",
    "delete", 0xFFF44336, function()
        if plugin.mainpage.clearAllBadges then
            plugin.mainpage.clearAllBadges()
            registered.badges = {}
            plugin.sys.toast("徽章已清除")
        else
            plugin.sys.toast("clearAllBadges API 不存在")
        end
    end)

-- ============================================
-- 事件监听：插件加载完成后注册 UI 元素
-- ============================================
plugin.events.register("onAllPluginsLoaded", function()
    plugin.sys.log("HomeExtDemo", "所有插件已加载，开始注册首页 UI 扩展")
    addToolbarDemo()
    addFabDemo()
    addCategoryDemo()
    -- 延迟刷新徽章，确保项目列表已加载
    pcall(function()
        -- 使用定时器或直接调用，这里直接调用
        refreshNewBadges()
    end)
end)

-- ============================================
-- 插件卸载时清理注册的 UI 元素
-- ============================================
local function onUnload()
    plugin.sys.log("HomeExtDemo", "插件卸载中，清理注册的 UI 元素...")

    -- 清理工具栏按钮
    if plugin.mainpage.removeToolbarAction then
        for _, key in ipairs(registered.toolbarActions) do
            pcall(function() plugin.mainpage.removeToolbarAction(key) end)
        end
    end

    -- 清理 FAB
    if plugin.mainpage.removeHomeFab then
        for _, key in ipairs(registered.fabs) do
            pcall(function() plugin.mainpage.removeHomeFab(key) end)
        end
    end

    -- 清理分类栏
    if plugin.mainpage.removeCategoryBarItem then
        for _, key in ipairs(registered.categoryItems) do
            pcall(function() plugin.mainpage.removeCategoryBarItem(key) end)
        end
    end

    -- 清理徽章（只清理本插件添加的）
    if plugin.mainpage.clearProjectBadge then
        for _, pid in ipairs(registered.badges) do
            pcall(function() plugin.mainpage.clearProjectBadge(pid) end)
        end
    end

    plugin.sys.log("HomeExtDemo", "UI 元素清理完成")
end

plugin.events.register("onPluginUnloaded", onUnload)

-- 立即尝试注册（某些场景下 onAllPluginsLoaded 已触发过）
safeApi("立即注册UI", function()
    addToolbarDemo()
    addFabDemo()
    addCategoryDemo()
    refreshNewBadges()
end)

plugin.sys.log("HomeExtDemo", "初始化完成，已注册 6 个快捷操作 + 1 个侧滑栏项 + 1 个关于 section")
