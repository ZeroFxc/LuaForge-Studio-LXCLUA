-- ============================================
-- 事件系统全面演示插件 v2
-- 演示 on/off/once/intercept/fire/emit、
-- 事件拦截（返回true阻止默认行为）、
-- 插件消息通信(sendMessage/broadcastMessage/onMessage)、
-- 自定义事件注册(registerEvent)、事件发现(getRegisteredEvents)
-- ============================================
--
-- 【重要】Lua 调用 Java 对象方法一律使用 `.`，不要用 `:`
-- 错误: plugin.events:on("onFileOpen", cb)
-- 正确: plugin.events.on("onFileOpen", cb)
--

plugin.sys.log("EventDemo", "=== 事件系统全面演示插件已加载 ===")

-- ============================================
-- 1. 注册自定义事件（让其他插件可发现）
-- ============================================
plugin.events.registerEvent("onEventDemoDataUpdate", "事件演示插件数据更新时触发")
plugin.events.registerEvent("onEventDemoCounterChanged", "事件演示计数器变化时触发")

-- ============================================
-- 2. 状态跟踪
-- ============================================
local state = {
    openCount = 0,
    saveCount = 0,
    backPressedCount = 0,
    deleteBlocked = 0,
    counter = 0
}

-- 保存回调引用（便于卸载时清理）
local callbacks = {}

-- ============================================
-- 3. 普通事件监听（on / register 均可，on 更直观）
-- ============================================
callbacks.onProjectOpen = function(projectId, projectName, projectPath)
    state.openCount = state.openCount + 1
    local shortName = tostring(projectName or projectPath):match("([^/\\]+)$") or tostring(projectName)
    plugin.sys.log("EventDemo", string.format("[onProjectOpen] #%d 项目=%s", state.openCount, shortName))
    plugin.sys.toast("打开: " .. shortName)
end
plugin.events.on("onProjectOpen", callbacks.onProjectOpen)

callbacks.onFileSave = function(filePath)
    state.saveCount = state.saveCount + 1
    local name = tostring(filePath):match("([^/\\]+)$") or tostring(filePath)
    plugin.sys.log("EventDemo", "[onFileSave] " .. name)
end
plugin.events.on("onFileSave", callbacks.onFileSave)

-- ============================================
-- 4. 一次性监听（once）
-- ============================================
plugin.events.once("onNewProject", function(projectName, projectPath, templateId)
    plugin.sys.log("EventDemo", "[once onNewProject] 首次新建项目: " .. tostring(projectName))
    plugin.sys.toast("欢迎新建第一个项目: " .. tostring(projectName))
end)

-- ============================================
-- 5. 事件拦截器（intercept）
-- 返回 true = 阻止默认行为，false/nil = 放行
-- 第二个参数 priority 越小优先级越高
-- ============================================

-- 拦截项目删除：弹出确认框，注意对话框是异步的，这里同步返回false放行（
-- 真正的同步拦截适用于即时判断场景，如根据路径/ID判断是否保护某项目）
plugin.events.intercept("onProjectDelete", 0, function(args)
    local projectId = args[1]
    local projectName = args[2]
    local projectPath = args[3]
    plugin.sys.log("EventDemo", "[拦截器] 尝试删除: " .. tostring(projectName))

    -- 示例：阻止删除名称含"important"的项目
    local name = tostring(projectName or "")
    if string.lower(name):find("important") then
        state.deleteBlocked = state.deleteBlocked + 1
        plugin.sys.toast("已拦截删除保护项目: " .. name)
        return true  -- 拦截！阻止默认删除行为
    end
    return false  -- 放行
end)

-- 拦截Toast显示，修改/过滤特定消息
plugin.events.intercept("onToastShown", 10, function(args)
    local message = args[1]
    local msgType = args[2]
    -- 仅记录，不拦截
    plugin.sys.log("EventDemo", string.format("[Toast] %s: %s", tostring(msgType), tostring(message)))
    return false
end)

-- ============================================
-- 6. 监听返回键
-- ============================================
callbacks.onBackPressed = function()
    state.backPressedCount = state.backPressedCount + 1
    if state.backPressedCount % 5 == 0 then
        plugin.sys.toast("返回键已按 " .. state.backPressedCount .. " 次")
    end
end
plugin.events.on("onBackPressed", callbacks.onBackPressed)

-- ============================================
-- 7. 监听应用生命周期
-- ============================================
plugin.events.on("onAppResume", function()
    plugin.sys.log("EventDemo", "[onAppResume] 应用回到前台")
end)
plugin.events.on("onAppPause", function()
    plugin.sys.log("EventDemo", "[onAppPause] 应用进入后台")
end)

-- ============================================
-- 8. 监听页面切换
-- ============================================
plugin.events.on("onPageChanged", function(pageId, fromPageId)
    plugin.sys.log("EventDemo", string.format("[页面] %s -> %s", tostring(fromPageId), tostring(pageId)))
end)

-- ============================================
-- 9. 监听设置变更
-- ============================================
plugin.events.on("onSettingsChanged", function(settingsJson)
    plugin.sys.log("EventDemo", "[设置变更] " .. tostring(settingsJson):sub(1, 80) .. "...")
end)

-- ============================================
-- 10. 监听构建事件
-- ============================================
plugin.events.on("onBuildStart", function(projectPath, buildType)
    plugin.sys.log("EventDemo", string.format("[构建开始] %s type=%s", tostring(projectPath), tostring(buildType)))
end)
plugin.events.on("onBuildFinish", function(projectPath, result, success)
    plugin.sys.log("EventDemo", string.format("[构建完成] success=%s result=%s", tostring(success), tostring(result):sub(1, 60)))
end)
plugin.events.on("onBuildError", function(projectPath, errorMsg, buildType)
    plugin.sys.log("EventDemo", "[构建错误] " .. tostring(errorMsg):sub(1, 100))
end)

-- ============================================
-- 11. 插件消息通信（onMessage 接收）
-- ============================================
plugin.events.onMessage(function(fromId, action, dataJson)
    plugin.sys.log("EventDemo", string.format("[收到消息] from=%s action=%s data=%s",
        tostring(fromId), tostring(action), tostring(dataJson)))
    plugin.sys.toast(string.format("[消息] %s: %s", tostring(fromId), tostring(action)))
end)

-- ============================================
-- 12. 工具栏按钮扩展（首页工具栏右侧）
-- ============================================
local btnId = plugin.ui.addToolbarButton(
    plugin.ui.POINTS.HOME_TOOLBAR_END,
    "evt_demo_btn",
    "事件",
    0,
    function()
        state.counter = state.counter + 1
        -- 触发自定义事件（通知其他插件）
        plugin.events.fire("onEventDemoCounterChanged", state.counter)
        plugin.sys.toast("计数器: " .. state.counter)
    end
)

-- ============================================
-- 13. 菜单项扩展（首页更多菜单）
-- ============================================
plugin.ui.addMenuItem(
    plugin.ui.POINTS.HOME_MORE_MENU,
    "evt_demo_status",
    "事件演示状态",
    0,
    function()
        local evts = plugin.events.getRegisteredEvents()
        local evtCount = #evts
        local info = string.format(
            "=== 事件演示状态 ===\n\n" ..
            "打开项目: %d 次\n" ..
            "保存文件: %d 次\n" ..
            "返回键: %d 次\n" ..
            "拦截删除: %d 次\n" ..
            "计数器: %d\n\n" ..
            "系统注册事件总数: %d",
            state.openCount, state.saveCount, state.backPressedCount,
            state.deleteBlocked, state.counter, evtCount
        )
        plugin.ui.showMessage("事件演示", info)
    end
)

plugin.ui.addMenuItem(
    plugin.ui.POINTS.HOME_MORE_MENU,
    "evt_demo_broadcast",
    "广播消息给所有插件",
    1,
    function()
        plugin.events.broadcastMessage("hello", '{"from":"event_demo","msg":"hi all"}')
        plugin.sys.toast("已广播 hello 消息")
    end
)

-- ============================================
-- 14. 底部面板
-- ============================================
plugin.ui.addBottomPanelItem("event_demo_panel", "事件统计", {
    {type = "text", value = "=== 事件统计 ==="},
    {type = "spacer", height = 8},
    {type = "text", value = "打开: " .. state.openCount},
    {type = "text", value = "保存: " .. state.saveCount},
    {type = "spacer", height = 8},
    {type = "button", id = "reset", value = "重置计数器"}
}, function()
    state.counter = 0
    plugin.sys.toast("计数器已重置")
end)

-- ============================================
-- 15. 快捷操作
-- ============================================
plugin.menu.addQuickAction("evt_fire_custom", "触发自定义事件", function()
    plugin.events.fire("onEventDemoDataUpdate", "hello", os.time())
    plugin.sys.toast("已触发 onEventDemoDataUpdate")
end)

plugin.menu.addQuickAction("evt_list_events", "列出所有事件", function()
    local evts = plugin.events.getRegisteredEvents()
    local list = "系统+自定义事件共 " .. #evts .. " 个:\n\n"
    local shown = 0
    for i, ev in ipairs(evts) do
        if shown < 30 then
            local tag = ev.isCustom and "[自]" or "[系]"
            list = list .. tag .. " " .. ev.eventName .. "\n"
            shown = shown + 1
        end
    end
    if #evts > 30 then
        list = list .. "... 还有 " .. (#evts - 30) .. " 个"
    end
    plugin.ui.showTextDialog("已注册事件", list)
end)

plugin.menu.addQuickAction("evt_send_msg", "发送消息给自身", function()
    plugin.events.sendMessage(plugin.getPluginId(), "ping", '{"time":' .. os.time() .. '}')
end)

-- ============================================
-- 16. 侧滑栏菜单项
-- ============================================
plugin.nav.addSidebarItem("event_demo_nav", "事件演示", "custom", "notification", function()
    plugin.ui.showMessage("事件演示",
        "本插件演示了事件系统全部核心功能:\n\n" ..
        "1. on/off 注册/注销事件\n" ..
        "2. once 一次性监听\n" ..
        "3. intercept 事件拦截\n" ..
        "4. fire/emit 触发事件\n" ..
        "5. registerEvent 自定义事件注册\n" ..
        "6. getRegisteredEvents 事件发现\n" ..
        "7. sendMessage 点对点消息\n" ..
        "8. broadcastMessage 广播消息\n" ..
        "9. onMessage 接收消息\n" ..
        "10. addToolbarButton 工具栏按钮\n" ..
        "11. addMenuItem 菜单项扩展\n\n" ..
        "【重要】所有 Java 方法调用均使用 . 而非 :")
end)

-- ============================================
-- 17. 插件卸载时清理
-- ============================================
plugin.events.on("onPluginUnloaded", function(pluginId)
    if pluginId == plugin.getPluginId() then
        plugin.ui.removeToolbarButton(btnId)
        plugin.ui.clearMenuItems()
        plugin.ui.clearBottomPanelItems()
        plugin.sys.log("EventDemo", "插件已卸载，资源已清理")
    end
end)

plugin.sys.log("EventDemo", "初始化完成")
