-- ============================================================
-- 示例插件 - LXC-LUA 插件 API 演示
-- 演示分类化插件桥接 API 的使用方法
-- ============================================================

-- API 模块说明:
-- plugin.sys:     系统操作（日志、Toast、版本等）
-- plugin.editor:  编辑器操作（文本、光标、文件、语言检测等）
-- plugin.project: 项目操作（文件读写、目录等）
-- plugin.ui:      对话框操作（消息、确认、输入、单选、多选、文件列表、图片、文本、复选框、底部面板扩展）
-- plugin.events:  事件监听（注册、触发事件）
-- plugin.config:  配置存储（键值持久化）
-- plugin.reflect: Java 反射（类、方法、字段）
-- plugin.lua:     Lua 脚本执行
-- plugin.menu:    菜单管理（快捷操作、菜单项）
-- plugin.clipboard: 剪贴板操作
-- plugin.http:    网络请求
-- plugin.manager: 插件管理（信息获取、启用禁用）
-- plugin.nav:     导航与侧滑栏（添加菜单项）
-- plugin.about:   关于页面扩展（添加 section、链接、回调、信息项）
-- plugin.mainpage: 主页项目列表操作（多选模式、徽章、自定义菜单、滑动事件）
-- plugin.settings: 设置页面扩展
-- plugin.detail:  插件详情展开区扩展
-- plugin.logger:  日志系统（记录/读取/搜索/跨插件日志访问）
-- plugin.assets:  资源注册表（注册/查询/读取跨插件共享资源）
-- plugin.shortcut: 快捷键绑定（注册/查询/冲突检测）
--
-- 新增主页项目列表事件:
--   onProjectLongPress(projectId, projectName, projectPath)  -- 项目长按
--   onProjectClick(projectId, projectName, projectPath)      -- 项目点击
--   onProjectSwipeLeft(projectId, projectName, projectPath)  -- 项目左滑
--   onProjectSwipeRight(projectId, projectName, projectPath) -- 项目右滑
--
-- plugin.mainpage API:
--   多选: enterMultiSelectMode, exitMultiSelectMode, toggleProjectSelection,
--         selectProject, deselectProject, getSelectedProjectIds, etc.
--   徽章: setProjectBadge(id, text, color), clearProjectBadge(id), clearAllBadges
--   菜单: addProjectCardMenuItem(key, label, callback), removeProjectCardMenuItem(key)
--   数据: getProjectIds(), getProjectName(id), getProjectPath(id), getProjectCount()

-- 1. 系统操作示例 - 检查版本和日志
local version = plugin.sys.getVersion()
local dataDir = plugin.sys.getDataDir()
plugin.sys.log("示例插件", "插件已加载, IDE 版本: " .. tostring(version))
plugin.sys.log("示例插件", "插件数据目录: " .. tostring(dataDir))

-- 2. 配置存储示例
local greetCount = plugin.config.getInt("greet_count", 0)
plugin.config.setInt("greet_count", greetCount + 1)
plugin.sys.log("示例插件", "插件启动次数: " .. (greetCount + 1) .. " 次")

-- 3. 添加编辑器快捷操作按钮
plugin.menu.addQuickAction("greet", "问候", function()
    plugin.sys.toast("你好！这是第 " .. plugin.config.getInt("greet_count", 0) .. " 次启动插件")
end)

-- 4. 插入注释按钮
plugin.menu.addQuickAction("insert_comment", "插入注释", function()
    plugin.editor.insertText("-- TODO: ")
end)

-- 5. 显示选中文本按钮
plugin.menu.addQuickAction("show_selection", "显示选区", function()
    local ok, result = pcall(function()
        return plugin.editor.getSelectedText()
    end)
    
    if ok and result and #result > 0 then
        plugin.sys.toast("选中内容: " .. result)
    else
        local ok2, cursor = pcall(function()
            return plugin.editor.getCursorPosition()
        end)
        
        if ok2 and cursor then
            plugin.sys.toast("光标位置: 行 " .. tostring(cursor[1] + 1) .. ", 列 " .. tostring(cursor[2] + 1))
        else
            plugin.sys.toast("请先打开一个文件")
        end
    end
end)

-- 6. 跳转到指定行
plugin.menu.addQuickAction("goto_line", "跳转到行", function()
    local callback = {
        onInput = function(text)
            local line = tonumber(text)
            if line then
                plugin.editor.gotoLine(line - 1)
                plugin.sys.toast("已跳转到第 " .. line .. " 行")
            else
                plugin.sys.toast("请输入有效的行号")
            end
        end
    }
    plugin.ui.showInputDialog("跳转到行", "请输入行号", "1", callback)
end)

-- 7. 显示确认对话框
plugin.menu.addQuickAction("show_dialog", "显示对话框", function()
    plugin.ui.showConfirm("确认操作", "确定要执行此操作吗？", function()
        plugin.sys.toast("你点击了确定")
    end, function()
        plugin.sys.toast("你点击了取消")
    end)
end)

-- 8. 复制文件路径
plugin.menu.addQuickAction("copy_file_path", "复制路径", function()
    local filePath = plugin.editor.getActiveFile()
    if filePath then
        plugin.clipboard.copy(filePath)
        plugin.sys.toast("已复制: " .. filePath)
    else
        plugin.sys.toast("没有打开的文件")
    end
end)

-- 9. 文件信息
plugin.menu.addQuickAction("file_info", "文件信息", function()
    local filePath = plugin.editor.getActiveFile()
    local projectPath = plugin.project.getPath()
    
    if filePath then
        local info = "当前文件: " .. filePath .. "\n"
        info = info .. "项目路径: " .. tostring(projectPath)
        plugin.ui.showMessage("文件信息", info)
    else
        plugin.sys.toast("没有打开的文件")
    end
end)

-- 10. 检查当前编辑器语言
plugin.menu.addQuickAction("check_language", "检查语言", function()
    local lang = plugin.editor.getLanguage()
    local ext = plugin.editor.getFileExtension()
    local isLua = plugin.editor.isLanguage("lua")
    
    local info = "当前语言: " .. tostring(lang) .. "\n" ..
                 "文件扩展名: " .. tostring(ext) .. "\n" ..
                 "是否 Lua 文件: " .. (isLua and "是" or "否")
    plugin.ui.showMessage("编辑器语言信息", info)
end)

-- 11. 检查应用语言环境（国际化）
plugin.menu.addQuickAction("check_locale", "语言环境", function()
    local lang = plugin.sys.getAppLanguage()
    local region = plugin.sys.getAppRegion()
    local locale = plugin.sys.getAppLocale()
    local isChinese = plugin.sys.isChinese()
    local isEnglish = plugin.sys.isEnglish()
    
    local info = "应用语言: " .. lang .. "\n" ..
                 "应用区域: " .. region .. "\n" ..
                 "完整区域: " .. locale .. "\n" ..
                 "中文环境: " .. (isChinese and "是" or "否") .. "\n" ..
                 "英文环境: " .. (isEnglish and "是" or "否")
    plugin.ui.showMessage("国际化信息", info)
end)

-- ============================================
-- Java 反射 API 调用规则（重要！）
-- ============================================
-- 在 LXC-LUA 插件中，Java 对象是 UserData 类型。
-- 通过 plugin.reflect 获取到的 Java 实例，调用其方法时
-- 必须使用 . （点号），不能使用 : （冒号）。
-- : 会隐式传入 self 作为第一个参数，导致参数错位报错。
--
-- 错误写法：
--   local result = obj:someMethod(arg)
--   local str = obj:toString()
--
-- 正确写法：
--   local result = obj.someMethod(arg)
--   local str = obj.toString()
--
-- 访问 Java 字段也用 . ：
--   local value = obj.fieldName
--
-- 本插件使用 plugin.reflect 封装模块调用 Java：
--   plugin.reflect.newInstance / callMethod / callStaticMethod /
--   getField / setField / getStaticField / setStaticField / loadClass
-- 详细用法可参考 java_api_demo 示例插件。
-- ============================================

-- 12. Java 反射测试 - 加载类
plugin.menu.addQuickAction("java_load_class", "加载类", function()
    local clazz = plugin.reflect.loadClass("com.luaforge.studio.lxclua.plugin.ReflectionTestClass")
    if clazz then
        plugin.sys.toast("类加载成功: " .. tostring(clazz))
    else
        plugin.sys.toast("类加载失败")
    end
end)

-- 13. Java 反射测试 - 静态字段
plugin.menu.addQuickAction("java_static_field", "静态字段", function()
    local className = "com.luaforge.studio.lxclua.plugin.ReflectionTestClass"
    
    -- 获取当前静态字段值
    local currentValue = plugin.reflect.getStaticField(className, "staticField")
    plugin.sys.log("示例插件", "静态字段当前值: " .. tostring(currentValue))
    
    -- 切换值
    local newValue
    if tostring(currentValue) == "静态字段初始值" then
        newValue = "从 Lua 修改的值"
    else
        newValue = "静态字段初始值"
    end
    
    -- 设置静态字段
    plugin.reflect.setStaticField(className, "staticField", newValue)
    
    -- 再次获取确认
    local confirmValue = plugin.reflect.getStaticField(className, "staticField")
    
    plugin.ui.showMessage("静态字段测试", 
        "修改前: " .. tostring(currentValue) .. "\n" ..
        "修改后: " .. tostring(confirmValue) .. "\n\n" ..
        "再次点击可切换回来")
end)

-- 14. Java 反射测试 - 静态方法
plugin.menu.addQuickAction("java_static_method", "静态方法", function()
    local className = "com.luaforge.studio.lxclua.plugin.ReflectionTestClass"
    
    -- 调用无参静态方法
    local result1 = plugin.reflect.callStaticMethod(className, "staticMethod", nil)
    plugin.sys.log("示例插件", "staticMethod(): " .. tostring(result1))
    
    -- 调用带参数的静态方法
    local result2 = plugin.reflect.callStaticMethod(className, "staticMethodWithParams", {10, 20})
    plugin.sys.log("示例插件", "staticMethodWithParams(10, 20): " .. tostring(result2))
    
    plugin.ui.showMessage("静态方法测试",
        "staticMethod(): " .. tostring(result1) .. "\n" ..
        "staticMethodWithParams(10, 20): " .. tostring(result2))
end)

-- 15. Java 反射测试 - 创建实例
plugin.menu.addQuickAction("java_new_instance", "创建实例", function()
    local className = "com.luaforge.studio.lxclua.plugin.ReflectionTestClass"
    
    -- 无参构造
    local obj1 = plugin.reflect.newInstance(className, nil)
    plugin.sys.log("示例插件", "无参构造: " .. tostring(obj1))
    
    -- 带 String 参数的构造
    local obj2 = plugin.reflect.newInstance(className, {"初始值来自 Lua"})
    plugin.sys.log("示例插件", "String 构造: " .. tostring(obj2))
    
    -- 带 int, int 参数的构造
    local obj3 = plugin.reflect.newInstance(className, {100, 200})
    plugin.sys.log("示例插件", "int,int 构造: " .. tostring(obj3))
    
    local info = "无参构造: " .. tostring(obj1) .. "\n" ..
                 "String 构造: " .. tostring(obj2) .. "\n" ..
                 "int,int 构造: " .. tostring(obj3)
    plugin.ui.showMessage("创建实例测试", info)
end)

-- 16. Java 反射测试 - 实例字段
-- 注意: 使用全局变量保存实例，以便在多次点击间保持状态
local savedInstance = nil

plugin.menu.addQuickAction("java_instance_field", "实例字段", function()
    local className = "com.luaforge.studio.lxclua.plugin.ReflectionTestClass"
    
    -- 如果实例不存在，创建新实例
    if not savedInstance then
        savedInstance = plugin.reflect.newInstance(className, {"测试实例"})
        plugin.sys.log("示例插件", "已创建新实例")
    end
    
    -- 获取当前实例字段值
    local currentValue = plugin.reflect.getField(savedInstance, "instanceField")
    plugin.sys.log("示例插件", "实例字段当前值: " .. tostring(currentValue))
    
    -- 切换值
    local newValue
    if tostring(currentValue) == "测试实例" then
        newValue = "从 Lua 修改"
    elseif tostring(currentValue) == "从 Lua 修改" then
        newValue = "再次修改"
    else
        newValue = "测试实例"
    end
    
    -- 设置实例字段
    plugin.reflect.setField(savedInstance, "instanceField", newValue)
    
    -- 再次获取确认
    local confirmValue = plugin.reflect.getField(savedInstance, "instanceField")
    
    plugin.ui.showMessage("实例字段测试",
        "当前值: " .. tostring(currentValue) .. "\n" ..
        "修改后: " .. tostring(confirmValue) .. "\n\n" ..
        "再次点击继续切换")
end)

-- 17. Java 反射测试 - 实例方法
plugin.menu.addQuickAction("java_instance_method", "实例方法", function()
    local className = "com.luaforge.studio.lxclua.plugin.ReflectionTestClass"
    
    -- 创建实例
    local obj = plugin.reflect.newInstance(className, {"Hello Lua"})
    
    -- 调用无参实例方法
    local result1 = plugin.reflect.callMethod(obj, "instanceMethod", nil)
    plugin.sys.log("示例插件", "instanceMethod(): " .. tostring(result1))
    
    -- 调用带参数的实例方法
    local result2 = plugin.reflect.callMethod(obj, "instanceMethodWithParams", {"测试", 3})
    plugin.sys.log("示例插件", "instanceMethodWithParams: " .. tostring(result2))
    
    -- 调用带返回值的方法
    local result3 = plugin.reflect.callMethod(obj, "increment", nil)
    plugin.sys.log("示例插件", "increment(): " .. tostring(result3))
    
    plugin.ui.showMessage("实例方法测试",
        "instanceMethod(): " .. tostring(result1) .. "\n" ..
        "instanceMethodWithParams: " .. tostring(result2) .. "\n" ..
        "increment(): " .. tostring(result3))
end)

-- 18. 执行 Lua 脚本
plugin.menu.addQuickAction("exec_lua", "执行 Lua", function()
    local result = plugin.lua.execute("return 1 + 2 + 3")
    plugin.sys.toast("计算结果: " .. tostring(result))
end)

-- 19. 添加顶部菜单项
plugin.menu.addMenuItem("hello_menu", "插件消息", function()
    plugin.ui.showMessage("示例插件", "这是从插件菜单项触发的消息！")
end)

plugin.menu.addMenuItem("current_file", "显示当前文件", function()
    local file = plugin.editor.getActiveFile()
    if file then
        plugin.sys.toast("当前文件: " .. file)
    else
        plugin.sys.toast("没有打开的文件")
    end
end)

plugin.menu.addMenuDivider("divider1")

plugin.menu.addMenuItem("insert_date", "插入日期", function()
    local date = plugin.reflect.newInstance("java.util.Date")
    local dateStr = plugin.reflect.callMethod(date, "toString", nil)
    plugin.editor.insertText("-- " .. tostring(dateStr))
end)

-- 20. 监听文件保存事件
plugin.events.register("onFileSave", function(filePath)
    plugin.sys.log("示例插件", "文件已保存: " .. tostring(filePath))
end)

-- 21. 监听文件打开事件
plugin.events.register("onFileOpen", function(filePath)
    local fileName = tostring(filePath):match("[^/]+$") or tostring(filePath)
    plugin.sys.toast("已打开文件: " .. fileName)
end)

-- 22. 文件树上下文菜单示例 - 查看文件信息
plugin.menu.addFileTreeItem("ft_file_info", "查看文件信息", nil, function(filePath, isDirectory)
    local fileInfo = plugin.project.getFileInfo(filePath)
    if fileInfo then
        local info = "名称: " .. tostring(fileInfo.name) .. "\n"
        info = info .. "路径: " .. tostring(fileInfo.path) .. "\n"
        info = info .. "类型: " .. (fileInfo.isDirectory and "目录" or "文件") .. "\n"
        if not fileInfo.isDirectory then
            info = info .. "扩展名: ." .. tostring(fileInfo.extension) .. "\n"
            info = info .. "大小: " .. tostring(fileInfo.size) .. " 字节\n"
            info = info .. "修改时间: " .. os.date("%Y-%m-%d %H:%M:%S", fileInfo.lastModified / 1000) .. "\n"
        end
        info = info .. "父目录: " .. tostring(fileInfo.parentPath)
        plugin.ui.showMessage("文件信息", info)
    else
        plugin.sys.toast("文件不存在")
    end
end)

-- 23. 文件树上下文菜单示例 - 运行 Lua
plugin.menu.addFileTreeItem("ft_run_lua", "运行 Lua", ".lua", function(filePath, isDirectory)
    plugin.sys.toast("正在运行: " .. tostring(filePath))
    local result = plugin.lua.executeFile(filePath)
    plugin.sys.log("示例插件", "Lua 执行结果: " .. tostring(result))
end)

-- 24. 侧滑栏扩展示例 - 添加设置菜单项
plugin.nav.addSidebarItem("plugin_settings", "插件设置", "settings", "settings", function()
    plugin.sys.toast("点击了插件设置（侧滑栏菜单项）")
    plugin.ui.showMessage("侧滑栏功能", "这是通过插件动态添加到侧滑栏的菜单项！")
end)

-- 25. 侧滑栏扩展示例 - 添加自定义菜单项
plugin.nav.addSidebarItem("plugin_feature", "插件功能", "custom", "extension", function()
    plugin.sys.toast("点击了插件功能")
    plugin.ui.showMessage("自定义侧滑栏项", "此菜单项添加到了 custom 分组")
end)

-- 26. 侧滑栏扩展示例 - 添加到项目分组（带图标）
plugin.nav.addSidebarItem("plugin_quick_open", "项目快捷打开", "project", "folder", function()
    plugin.sys.toast("插件注册的项目分组菜单项被点击了")
end)

-- 27. 侧滑栏扩展示例 - 添加到关于分组
plugin.nav.addSidebarItem("plugin_about_link", "关于本插件", "about", "info", function()
    plugin.ui.showMessage("关于本插件", "本示例插件演示了所有 5 个侧滑栏分组的注册方式。")
end)

-- 28. 侧滑栏扩展示例 - 添加到插件管理分组
plugin.nav.addSidebarItem("plugin_market", "插件市场", "plugins", "star", function()
    plugin.ui.showMessage("插件市场", "这里可以打开插件市场（演示）")
end)

-- 29. 侧滑栏扩展示例 - 演示动态打开/关闭侧滑栏
plugin.menu.addQuickAction("open_sidebar_demo", "打开侧滑栏", function()
    plugin.nav.openSidebar()
    plugin.sys.log("示例插件", "已通过 API 打开侧滑栏")
end)

-- 30. 侧滑栏扩展示例 - 演示动态移除单个项
plugin.menu.addQuickAction("remove_one_sidebar", "移除「插件功能」", function()
    plugin.nav.removeSidebarItem("plugin_feature")
    plugin.sys.toast("已从侧滑栏移除「插件功能」项，再次启动插件可恢复")
end)

-- 31. 侧滑栏扩展示例 - 演示清除本插件所有侧滑栏项
plugin.menu.addQuickAction("clear_sidebar", "清除本插件侧滑栏项", function()
    plugin.nav.clearSidebarItems()
    plugin.sys.toast("本插件注册的所有侧滑栏项已清除")
end)

-- 32. 侧滑栏扩展示例 - 演示重新注册一组完整的 5 分组菜单
plugin.menu.addQuickAction("reregister_sidebar", "重新注册侧滑栏项", function()
    plugin.nav.clearSidebarItems()
    plugin.nav.addSidebarItem("rs_project", "重新注册-项目", "project", "folder", function()
        plugin.sys.toast("重新注册的项目分组")
    end)
    plugin.nav.addSidebarItem("rs_settings", "重新注册-设置", "settings", "settings", function()
        plugin.sys.toast("重新注册的设置分组")
    end)
    plugin.nav.addSidebarItem("rs_about", "重新注册-关于", "about", "info", function()
        plugin.sys.toast("重新注册的关于分组")
    end)
    plugin.nav.addSidebarItem("rs_plugins", "重新注册-插件", "plugins", "extension", function()
        plugin.sys.toast("重新注册的插件分组")
    end)
    plugin.nav.addSidebarItem("rs_custom", "重新注册-自定义", "custom", "star", function()
        plugin.sys.toast("重新注册的自定义分组")
    end)
    plugin.sys.toast("已重新注册 5 个分组的侧滑栏菜单项")
end)

-- 33. 关于页面扩展示例 - 添加一个 section 标题
plugin.about.addSection("plugin_features", "插件功能演示", 0)

-- 34. 关于页面扩展示例 - 在 section 下添加链接项（点击打开 URL）
plugin.about.addLink(
    "homepage",
    "plugin_features",
    "项目主页",
    "在浏览器中打开项目主页",
    "https://github.com/",
    "code",
    0xFF8D4A5A
)

-- 35. 关于页面扩展示例 - 在 section 下添加回调项（点击触发回调）
plugin.about.addCallback(
    "show_count",
    "plugin_features",
    "查看启动次数",
    "通过回调显示本插件启动次数",
    "star",
    0xFF5B8DEF,
    function()
        local count = plugin.config.getInt("greet_count", 0)
        plugin.ui.showMessage("启动次数", "本插件已启动 " .. count .. " 次")
    end
)

-- 36. 关于页面扩展示例 - 添加不可点击的信息条目
plugin.about.addInfo(
    "info_hint",
    "plugin_features",
    "提示",
    "关于页面区域中的信息条目不可点击",
    "info",
    0xFF36618E
)

-- 37. 关于页面扩展示例 - 添加第二个 section
plugin.about.addSection("plugin_links", "外部链接", 10)
plugin.about.addLink(
    "luaforge_site",
    "plugin_links",
    "LuaForge 官网",
    "访问项目官网",
    "https://lxclua.星辰.online",
    "web",
    0xFF2E6A44
)
plugin.about.addLink(
    "kotlin_lang",
    "plugin_links",
    "Kotlin 官网",
    "Kotlin 编程语言官网",
    "https://kotlinlang.org",
    "code",
    0xFF7F52FF
)

-- 38. 关于页面扩展示例 - 动态移除 / 清除演示
plugin.menu.addQuickAction("about_remove_one", "移除一个关于项", function()
    plugin.about.removeItem("show_count")
    plugin.sys.toast("已移除「查看启动次数」项")
end)

plugin.menu.addQuickAction("about_remove_section", "移除 plugin_links", function()
    plugin.about.removeSection("plugin_links")
    plugin.sys.toast("已移除「外部链接」section 及其下所有项")
end)

plugin.menu.addQuickAction("about_clear", "清除本插件关于项", function()
    plugin.about.clearItems()
    plugin.sys.toast("本插件注册的所有关于页面项已清除")
end)

plugin.menu.addQuickAction("about_reregister", "重新注册关于项", function()
    plugin.about.clearItems()
    plugin.about.addSection("plugin_features", "插件功能演示", 0)
    plugin.about.addLink("homepage", "plugin_features", "项目主页", "在浏览器中打开项目主页", "https://github.com/", "code", 0xFF8D4A5A)
    plugin.about.addCallback("show_count", "plugin_features", "查看启动次数", "通过回调显示启动次数", "star", 0xFF5B8DEF, function()
        local count = plugin.config.getInt("greet_count", 0)
        plugin.ui.showMessage("启动次数", "本插件已启动 " .. count .. " 次")
    end)
    plugin.about.addInfo("info_hint", "plugin_features", "提示", "信息条目不可点击", "info", 0xFF36618E)
    plugin.about.addSection("plugin_links", "外部链接", 10)
    plugin.about.addLink("luaforge_site", "plugin_links", "LuaForge 官网", "访问项目官网", "https://lxclua.星辰.online", "web", 0xFF2E6A44)
    plugin.sys.toast("已重新注册关于页面项")
end)

-- 39. 插件管理信息
plugin.menu.addQuickAction("plugin_info", "插件信息", function()
    local info = "插件 ID: com.example.hello_plugin\n" ..
                 "版本: 1.0.0\n" ..
                 "类型: Lua 插件\n" ..
                 "API 版本: " .. plugin.getApiVersion() .. "\n\n" ..
                 "支持的模块:\n" ..
                 "- sys: 系统操作\n" ..
                 "- editor: 编辑器操作\n" ..
                 "- project: 项目操作\n" ..
                 "- ui: 对话框操作\n" ..
                 "- events: 事件监听\n" ..
                 "- config: 配置存储\n" ..
                 "- reflect: Java 反射\n" ..
                 "- lua: Lua 脚本执行\n" ..
                 "- menu: 菜单管理\n" ..
                 "- clipboard: 剪贴板操作\n" ..
                 "- http: 网络请求\n" ..
                 "- manager: 插件管理\n" ..
                 "- nav: 导航与侧滑栏\n" ..
                 "- about: 关于页面扩展\n" ..
                 "- mainpage: 主页项目列表"
    plugin.ui.showMessage("示例插件信息", info)
end)

-- 40. 测试插件管理功能
plugin.menu.addQuickAction("list_plugins", "插件列表", function()
    local plugins = plugin.manager.getPluginIds()
    if plugins and #plugins > 0 then
        local pluginList = ""
        for i, pluginId in ipairs(plugins) do
            local name = plugin.manager.getPluginName(pluginId) or pluginId
            local version = plugin.manager.getPluginVersion(pluginId) or "未知"
            local enabled = plugin.manager.isPluginEnabled(pluginId) and "✓" or "✗"
            local category = plugin.manager.getPluginCategory(pluginId) or "normal"
            local categoryLabel = plugin.manager.getPluginCategoryLabel(pluginId) or category
            pluginList = pluginList .. i .. ". [" .. enabled .. "] " .. name .. " (v" .. version .. ") - " .. categoryLabel .. "\n"
        end
        plugin.ui.showMessage("已安装插件", pluginList)
    else
        plugin.sys.toast("没有安装的插件")
    end
end)

-- 41. 测试插件分类系统
plugin.menu.addQuickAction("plugin_category", "插件分类", function()
    local categories = {
        {type = "core", label = "核心插件", desc = "系统必需，无法禁用"},
        {type = "normal", label = "普通插件", desc = "可自由启用/禁用"},
        {type = "dependent", label = "附属插件", desc = "依赖其他插件才能运行"},
        {type = "extension", label = "扩展插件", desc = "扩展其他插件的功能"}
    }
    
    local info = "插件分类系统\n\n"
    for i, cat in ipairs(categories) do
        info = info .. cat.label .. ":\n"
        info = info .. "  - 类型标识: " .. cat.type .. "\n"
        info = info .. "  - 说明: " .. cat.desc .. "\n\n"
    end
    
    info = info .. "当前插件分类: " .. (plugin.manager.getPluginCategoryLabel("com.example.hello_plugin") or "未知")
    
    plugin.ui.showMessage("插件分类说明", info)
end)

-- 42. 底部面板扩展示例 - 添加编辑器底部面板
plugin.ui.addBottomPanelItem(
    plugin.getPluginId(),
    "plugin_output",
    "插件输出",
    {
        {type = "section", value = "运行状态"},
        {type = "text", value = "插件已成功加载，所有模块初始化完成"},
        {type = "spacer", height = 8},
        {type = "section", value = "快捷操作"},
        {type = "button", id = "refresh_status", value = "刷新状态"},
        {type = "button", id = "clear_output", value = "清空输出"},
        {type = "spacer", height = 8},
        {type = "section", value = "实时信息"},
        {type = "text", value = "当前 API 版本: " .. plugin.getApiVersion()},
        {type = "text", value = "插件 ID: " .. plugin.getPluginId()}
    },
    function()
        plugin.sys.toast("底部面板按钮被点击")
    end
)

-- 43. 底部面板扩展示例 - 添加第二个面板（数据监控）
plugin.ui.addBottomPanelItem(
    plugin.getPluginId(),
    "plugin_monitor",
    "数据监控",
    {
        {type = "section", value = "编辑器状态"},
        {type = "text", value = "在此可以显示实时的编辑器状态信息"},
        {type = "spacer", height = 4},
        {type = "button", id = "check_editor", value = "检查编辑器状态"},
        {type = "spacer", height = 8},
        {type = "section", value = "项目信息"},
        {type = "text", value = "打开文件后将在此显示详细信息"},
        {type = "button", id = "refresh_project", value = "刷新项目信息"}
    },
    function()
        local file = plugin.editor.getActiveFile()
        if file then
            plugin.sys.toast("当前文件: " .. file)
        else
            plugin.sys.toast("没有打开的文件")
        end
    end
)

-- 44. 移除底部面板的快捷操作
plugin.menu.addQuickAction("remove_bottom_panel", "移除监控面板", function()
    plugin.ui.removeBottomPanelItem("plugin_monitor")
    plugin.sys.toast("已移除「数据监控」底部面板")
end)

plugin.menu.addQuickAction("restore_bottom_panel", "恢复监控面板", function()
    plugin.ui.addBottomPanelItem(
        plugin.getPluginId(),
        "plugin_monitor",
        "数据监控",
        {
            {type = "section", value = "编辑器状态"},
            {type = "text", value = "面板已恢复"},
            {type = "button", id = "check_editor", value = "检查编辑器状态"}
        },
        function()
            plugin.sys.toast("监控面板按钮被点击")
        end
    )
    plugin.sys.toast("已恢复「数据监控」底部面板")
end)

-- 45. 文件列表对话框示例
plugin.menu.addQuickAction("file_list_dialog", "文件列表对话框", function()
    local projectPath = plugin.project.getPath()
    if projectPath then
        plugin.ui.showFileListDialog("项目文件列表", projectPath, ".lua", {
            onInput = function(path)
                plugin.sys.toast("选中文件: " .. path)
            end
        })
    else
        plugin.sys.toast("请先打开一个项目")
    end
end)

-- 46. 图片对话框示例
plugin.menu.addQuickAction("image_dialog", "图片对话框", function()
    local projectPath = plugin.project.getPath()
    if projectPath then
        local imgPath = projectPath .. "/screenshot.png"
        local file = io.open(imgPath, "r")
        if file then
            file:close()
            plugin.ui.showImageDialog("截图预览", imgPath)
        else
            plugin.ui.showMessage("提示", "项目根目录未找到 screenshot.png\n请放置一张截图后重试")
        end
    else
        plugin.sys.toast("请先打开一个项目")
    end
end)

-- 47. 文本展示对话框示例
plugin.menu.addQuickAction("text_dialog", "文本展示对话框", function()
    local info = "=== 插件系统信息 ===\n\n"
    info = info .. "API 版本: " .. plugin.getApiVersion() .. "\n"
    info = info .. "插件 ID: " .. plugin.getPluginId() .. "\n"
    info = info .. "启动次数: " .. plugin.config.getInt("greet_count", 0) .. "\n\n"
    info = info .. "支持的对话框类型:\n"
    info = info .. "  - showMessage: 消息对话框\n"
    info = info .. "  - showConfirm: 确认对话框\n"
    info = info .. "  - showInputDialog: 输入对话框\n"
    info = info .. "  - showSingleChoiceDialog: 单选对话框\n"
    info = info .. "  - showMultiChoiceDialog: 多选对话框\n"
    info = info .. "  - showFileListDialog: 文件列表对话框\n"
    info = info .. "  - showImageDialog: 图片对话框\n"
    info = info .. "  - showTextDialog: 文本展示对话框\n"
    info = info .. "  - showCheckboxDialog: 复选框对话框\n"
    plugin.ui.showTextDialog("插件系统信息", info)
end)

-- 48. 复选框对话框示例
plugin.menu.addQuickAction("checkbox_dialog", "复选框对话框", function()
    plugin.ui.showCheckboxDialog("设置选项", "启用自动保存功能", true, {
        onSelect = function(index)
            if index == 1 then
                plugin.sys.toast("已启用自动保存")
            else
                plugin.sys.toast("已禁用自动保存")
            end
        end
    })
end)

-- 49. 多选对话框示例
plugin.menu.addQuickAction("multi_choice_demo", "多选对话框", function()
    local items = {"Lua 脚本", "Java 类", "XML 布局", "JSON 配置", "图片资源"}
    local checked = {true, false, false, false, true}
    plugin.ui.showMultiChoiceDialog("选择文件类型", items, checked, {
        onConfirm = function(result)
            local selected = {}
            for i = 0, #items - 1 do
                if result[i] then
                    table.insert(selected, items[i + 1])
                end
            end
            local msg = "已选择: " .. table.concat(selected, ", ")
            plugin.sys.toast(msg)
        end
    })
end)

-- 50. 单选对话框示例
plugin.menu.addQuickAction("single_choice_demo", "单选对话框", function()
    local items = {"开发模式", "测试模式", "生产模式"}
    plugin.ui.showSingleChoiceDialog("选择运行模式", items, 0, {
        onSelect = function(index)
            local mode = items[index + 1]
            plugin.sys.toast("已切换到: " .. mode)
        end
    })
end)

plugin.sys.log("示例插件", "初始化完成，已注册 26 个快捷操作 + 3 个菜单项 + 5 个侧滑栏项 + 2 个 section / 6 个关于项 + 2 个事件监听器 + 2 个底部面板")

-- ============================================================
-- 新增: 日志系统 API 演示 (plugin.logger)
-- ============================================================

-- 51. 记录不同级别的日志
plugin.menu.addQuickAction("log_levels", "日志级别演示", function()
    plugin.logger.debug("示例插件", "这是一条 DEBUG 日志")
    plugin.logger.info("示例插件", "这是一条 INFO 日志")
    plugin.logger.warn("示例插件", "这是一条 WARN 日志")
    plugin.logger.error("示例插件", "这是一条 ERROR 日志", "模拟异常信息")
    plugin.sys.toast("已记录 4 条不同级别的日志")
end)

-- 52. 查看自己的日志文件列表
plugin.menu.addQuickAction("log_list_files", "查看日志文件", function()
    local files = plugin.logger.listLogFiles()
    local logDir = plugin.logger.getLogDir()
    local info = "日志目录: " .. logDir .. "\n\n"
    if files and #files > 0 then
        info = info .. "日志文件列表:\n"
        for i, name in ipairs(files) do
            local size = plugin.logger.getLogFileSize(name)
            info = info .. i .. ". " .. name .. " (" .. size .. " 字节)\n"
        end
    else
        info = info .. "暂无日志文件"
    end
    plugin.ui.showMessage("日志文件", info)
end)

-- 53. 查看最新日志
plugin.menu.addQuickAction("log_latest", "查看最新日志", function()
    local content = plugin.logger.getLatestLog()
    if content and #content > 0 then
        plugin.ui.showTextDialog("最新日志", content)
    else
        plugin.sys.toast("暂无日志内容")
    end
end)

-- 54. 搜索日志
plugin.menu.addQuickAction("log_search", "搜索日志", function()
    plugin.ui.showInputDialog("搜索日志", "输入关键词", "ERROR", {
        onInput = function(kw)
            local results = plugin.logger.searchLogs(kw, 20)
            if results and #results > 0 then
                local info = "搜索关键词: " .. kw .. "\n" ..
                             "匹配条数: " .. #results .. "\n\n"
                for i, entry in ipairs(results) do
                    info = info .. "[" .. entry.timestamp .. "] [" .. entry.level .. "] " .. entry.message .. "\n"
                end
                plugin.ui.showTextDialog("搜索结果", info)
            else
                plugin.sys.toast("未找到匹配日志")
            end
        end
    })
end)

-- 55. 查看日志统计
plugin.menu.addQuickAction("log_stats", "日志统计", function()
    local lineCount = plugin.logger.getLogLineCount()
    local files = plugin.logger.listLogFiles()
    local totalSize = 0
    for _, name in ipairs(files) do
        totalSize = totalSize + plugin.logger.getLogFileSize(name)
    end
    local info = "日志文件数: " .. #files .. "\n" ..
                 "日志总行数: " .. lineCount .. "\n" ..
                 "日志总大小: " .. totalSize .. " 字节\n" ..
                 "日志目录: " .. plugin.logger.getLogDir()
    plugin.ui.showMessage("日志统计", info)
end)

-- 56. 清空日志
plugin.menu.addQuickAction("log_clear", "清空日志", function()
    plugin.ui.showConfirm("清空日志", "确定要清空所有日志文件吗？", function()
        local ok = plugin.logger.clearLogs()
        plugin.sys.toast(ok and "日志已清空" or "清空日志失败")
    end, nil)
end)

-- 57. 跨插件日志访问 - 查看其他插件日志（演示用）
plugin.menu.addQuickAction("log_other_plugin", "查看其他插件日志", function()
    local plugins = plugin.manager.getPluginIds()
    local items = {}
    local otherPlugins = {}
    for i, pid in ipairs(plugins) do
        if pid ~= plugin.getPluginId() then
            table.insert(items, plugin.manager.getPluginName(pid) or pid)
            table.insert(otherPlugins, pid)
        end
    end
    
    if #items == 0 then
        plugin.sys.toast("没有其他插件")
        return
    end
    
    plugin.ui.showSingleChoiceDialog("选择要查看日志的插件", items, 0, {
        onSelect = function(index)
            local targetId = otherPlugins[index + 1]
            local files = plugin.logger.listPluginLogFiles(targetId)
            local logDir = plugin.logger.getPluginLogDir(targetId)
            
            if files and #files > 0 then
                local content = plugin.logger.readPluginLogFile(targetId, files[1])
                local info = "插件: " .. (plugin.manager.getPluginName(targetId) or targetId) .. "\n" ..
                             "日志文件: " .. files[1] .. "\n" ..
                             "行数: " .. plugin.logger.getPluginLogLineCount(targetId) .. "\n\n" ..
                             content
                plugin.ui.showTextDialog("插件日志", info)
            else
                plugin.sys.toast("该插件暂无日志: " .. logDir)
            end
        end
    })
end)

-- ============================================================
-- 新增: 资源注册表 API 演示 (plugin.assets)
-- ============================================================

-- 58. 注册资源演示
plugin.menu.addQuickAction("asset_register", "注册一个资源", function()
    local projectPath = plugin.project.getPath()
    if not projectPath then
        plugin.sys.toast("请先打开一个项目")
        return
    end
    
    local testFile = projectPath .. "/main.lua"
    local file = io.open(testFile, "r")
    if not file then
        plugin.sys.toast("项目根目录未找到 main.lua")
        return
    end
    file:close()
    
    local meta = {
        version = "1.0",
        encoding = "utf-8"
    }
    
    local globalId = plugin.assets.registerAsset(
        "demo_entry",           -- key
        "data",                 -- type
        testFile,               -- filePath
        "演示入口文件",           -- displayName
        "这是演示插件的 main.lua 入口文件", -- description
        true,                   -- isPublic (其他插件可访问)
        meta                    -- metadata
    )
    
    if globalId then
        plugin.sys.toast("资源注册成功: " .. globalId)
    else
        plugin.sys.toast("资源注册失败，文件不存在")
    end
end)

-- 59. 查看自己注册的资源
plugin.menu.addQuickAction("asset_list_my", "查看我的资源", function()
    local assets = plugin.assets.getMyAssets()
    if assets and #assets > 0 then
        local info = "我的注册资源 (" .. #assets .. " 个):\n\n"
        for i, asset in ipairs(assets) do
            info = info .. asset.id .. "\n"
            info = info .. "  类型: " .. asset.type .. "\n"
            info = info .. "  名称: " .. asset.displayName .. "\n"
            info = info .. "  大小: " .. asset.size .. " 字节\n"
            info = info .. "  公开: " .. (asset.isPublic and "是" or "否") .. "\n\n"
        end
        plugin.ui.showTextDialog("我的资源", info)
    else
        plugin.sys.toast("你没有注册任何资源，先点击「注册一个资源」试试")
    end
end)

-- 60. 查看所有公开资源
plugin.menu.addQuickAction("asset_list_all", "查看所有公开资源", function()
    local assets = plugin.assets.getAllPublicAssets()
    local total = plugin.assets.getTotalAssetCount()
    if assets and #assets > 0 then
        local info = "全局公开资源: " .. #assets .. " / " .. total .. " 个\n\n"
        for i, asset in ipairs(assets) do
            info = info .. asset.id .. " [" .. asset.type .. "] " .. asset.displayName .. "\n"
        end
        plugin.ui.showTextDialog("公开资源列表", info)
    else
        plugin.sys.toast("暂无公开资源，总计: " .. total .. " 个")
    end
end)

-- 61. 按类型筛选资源
plugin.menu.addQuickAction("asset_filter_type", "按类型筛选资源", function()
    local types = {"image", "audio", "font", "data", "layout", "icon", "raw"}
    plugin.ui.showSingleChoiceDialog("选择资源类型", types, 0, {
        onSelect = function(index)
            local selType = types[index + 1]
            local assets = plugin.assets.getAssetsByType(selType)
            local count = plugin.assets.getAssetCountByType(selType)
            if assets and #assets > 0 then
                local info = selType .. " 类型资源: " .. #assets .. " 个（共 " .. count .. " 个）\n\n"
                for i, asset in ipairs(assets) do
                    info = info .. i .. ". " .. asset.id .. " - " .. asset.displayName .. "\n"
                end
                plugin.ui.showTextDialog("类型筛选: " .. selType, info)
            else
                plugin.sys.toast("没有 " .. selType .. " 类型的资源（共 " .. count .. " 个）")
            end
        end
    })
end)

-- 62. 读取资源内容
plugin.menu.addQuickAction("asset_read", "读取资源内容", function()
    local assets = plugin.assets.getMyAssets()
    if not assets or #assets == 0 then
        plugin.sys.toast("请先注册一个资源")
        return
    end
    
    local items = {}
    for i, asset in ipairs(assets) do
        table.insert(items, asset.id .. " (" .. asset.type .. ")")
    end

    plugin.ui.showSingleChoiceDialog("选择要读取的资源", items, 0, {
        onSelect = function(index)
            local asset = assets[index + 1]
            local exists = plugin.assets.assetExists(asset.id)
            if not exists then
                plugin.sys.toast("资源文件不存在: " .. asset.filePath)
                return
            end
            
            local text = plugin.assets.readAssetText(asset.id)
            if text then
                local preview = text:sub(1, 500)
                if #text > 500 then preview = preview .. "\n\n... (内容已截断)" end
                plugin.ui.showTextDialog("资源: " .. asset.id, preview)
            else
                plugin.sys.toast("读取资源失败（可能是二进制文件）")
            end
        end
    })
end)

-- 63. 注销资源
plugin.menu.addQuickAction("asset_unregister", "注销资源", function()
    local assets = plugin.assets.getMyAssets()
    if not assets or #assets == 0 then
        plugin.sys.toast("没有可注销的资源")
        return
    end
    
    local items = {}
    for i, asset in ipairs(assets) do
        table.insert(items, asset.id .. " - " .. asset.displayName)
    end

    plugin.ui.showSingleChoiceDialog("选择要注销的资源", items, 0, {
        onSelect = function(index)
            local asset = assets[index + 1]
            local ok = plugin.assets.unregisterAsset(asset.key)
            plugin.sys.toast(ok and "已注销: " .. asset.id or "注销失败")
        end
    })
end)

-- 64. 注销全部资源
plugin.menu.addQuickAction("asset_unregister_all", "注销全部资源", function()
    local count = plugin.assets.getMyAssets()
    local n = count and #count or 0
    if n == 0 then
        plugin.sys.toast("没有可注销的资源")
        return
    end
    plugin.ui.showConfirm("注销全部资源", "确定要注销全部 " .. n .. " 个资源吗？", function()
        local removed = plugin.assets.unregisterAllAssets()
        plugin.sys.toast("已注销 " .. removed .. " 个资源")
    end, nil)
end)

plugin.sys.log("示例插件", "初始化完成，已注册 26 + 14 + 9 个快捷操作 + 3 个菜单项 + 5 个侧滑栏项 + 2 个 section / 6 个关于项 + 2 个事件监听器 + 2 个底部面板 + 3 个快捷键 + 5 个快捷键管理菜单")

-- ============================================================
-- 新增: 快捷键绑定系统 API 演示 (plugin.shortcut)
-- ============================================================

-- 65. 注册一个快捷键: Ctrl+Shift+F → 格式化代码
local fmtRegistered = plugin.shortcut.register(
    "format_code",
    "Ctrl+Shift+F",
    "格式化代码",
    "按下 Ctrl+Shift+F 格式化当前文件",
    function(combo)
        plugin.sys.toast("快捷键触发: " .. combo .. " → 格式化代码")
        plugin.editor.saveActiveFile()
    end,
    true
)
if fmtRegistered then
    plugin.sys.log("示例插件", "快捷键注册成功: " .. fmtRegistered)
else
    plugin.sys.log("示例插件", "快捷键注册失败: Ctrl+Shift+F")
end

-- 66. 注册快捷键: Ctrl+D → 复制当前行
plugin.shortcut.register(
    "duplicate_line",
    "Ctrl+D",
    "复制当前行",
    function(combo)
        local text = plugin.editor.getSelectedText()
        if text and #text > 0 then
            plugin.editor.insertText(text)
            plugin.sys.toast("已复制选中文本")
        else
            plugin.sys.toast("请先选中文本再按 Ctrl+D")
        end
    end,
    true
)

-- 67. 注册快捷键: Alt+G → 跳转到行
plugin.shortcut.register(
    "goto_line_shortcut",
    "Alt+G",
    "跳转到行",
    function(combo)
        plugin.ui.showInputDialog("跳转到行", "请输入行号", "1", {
            onInput = function(text)
                local line = tonumber(text)
                if line then
                    plugin.editor.gotoLine(line - 1)
                    plugin.sys.toast("已跳转到第 " .. line .. " 行")
                end
            end
        })
    end,
    true
)

-- 68. 添加查看快捷键列表的菜单
plugin.menu.addQuickAction("shortcut_list", "查看快捷键列表", function()
    local shortcuts = plugin.shortcut.getMyShortcuts()
    local allCount = plugin.shortcut.getShortcutCount()
    if shortcuts and #shortcuts > 0 then
        local info = "我的快捷键: " .. #shortcuts .. " 个（全局共 " .. allCount .. " 个）\n\n"
        for i, sc in ipairs(shortcuts) do
            info = info .. i .. ". " .. sc.combination .. " → " .. sc.label .. "\n"
            if sc.description ~= "" then
                info = info .. "   描述: " .. sc.description .. "\n"
            end
            info = info .. "   仅编辑器: " .. (sc.editorOnly and "是" or "否") .. "\n\n"
        end
        plugin.ui.showMessage("我的快捷键", info)
    else
        plugin.sys.toast("未注册任何快捷键")
    end
end)

-- 69. 添加查看全部快捷键（含冲突检测）的菜单
plugin.menu.addQuickAction("shortcut_all", "查看全部快捷键", function()
    local all = plugin.shortcut.getAllShortcuts()
    local count = plugin.shortcut.getShortcutCount()
    if all and #all > 0 then
        local info = "全部快捷键 (" .. count .. " 个):\n\n"
        for i, sc in ipairs(all) do
            info = info .. i .. ". [" .. sc.pluginId .. "] " .. sc.combination .. " → " .. sc.label .. "\n"
        end
        plugin.ui.showTextDialog("全部快捷键", info)
    else
        plugin.sys.toast("暂无快捷键")
    end
end)

-- 70. 检测快捷键冲突
plugin.menu.addQuickAction("shortcut_conflict", "检测快捷键冲突", function()
    plugin.ui.showInputDialog("检测快捷键冲突", "输入组合键", "Ctrl+S", {
        onInput = function(text)
            local taken = plugin.shortcut.isCombinationTaken(text)
            if taken then
                plugin.ui.showMessage("冲突检测结果",
                    "组合键 " .. text .. " 已被占用:\n\n" ..
                    "注册者: " .. taken.pluginId .. "\n" ..
                    "用途: " .. taken.label .. "\n" ..
                    "ID: " .. taken.id)
            else
                plugin.sys.toast("组合键 " .. text .. " 未被占用")
            end
        end
    })
end)

-- 71. 注销指定快捷键
plugin.menu.addQuickAction("shortcut_unregister", "注销快捷键", function()
    local shortcuts = plugin.shortcut.getMyShortcuts()
    if not shortcuts or #shortcuts == 0 then
        plugin.sys.toast("没有可注销的快捷键")
        return
    end
    
    local items = {}
    for i, sc in ipairs(shortcuts) do
        table.insert(items, sc.combination .. " - " .. sc.label)
    end

    plugin.ui.showSingleChoiceDialog("选择要注销的快捷键", items, 0, {
        onSelect = function(index)
            local sc = shortcuts[index + 1]
            local ok = plugin.shortcut.unregister(sc.key)
            plugin.sys.toast(ok and "已注销: " .. sc.combination or "注销失败")
        end
    })
end)

-- 72. 注销全部快捷键
plugin.menu.addQuickAction("shortcut_unregister_all", "注销全部快捷键", function()
    local shortcuts = plugin.shortcut.getMyShortcuts()
    local n = shortcuts and #shortcuts or 0
    if n == 0 then
        plugin.sys.toast("没有可注销的快捷键")
        return
    end
    plugin.ui.showConfirm("注销全部快捷键", "确定要注销全部 " .. n .. " 个快捷键吗？", function()
        local removed = plugin.shortcut.unregisterAll()
        plugin.sys.toast("已注销 " .. removed .. " 个快捷键")
    end, nil)
end)

-- 73. 重新注册演示快捷键
plugin.menu.addQuickAction("shortcut_reregister", "重新注册快捷键", function()
    plugin.shortcut.unregisterAll()
    
    local a = plugin.shortcut.register("format_code", "Ctrl+Shift+F", "格式化代码", "格式化当前文件", function(combo)
        plugin.sys.toast("格式化触发!")
    end, true)
    
    local b = plugin.shortcut.register("duplicate_line", "Ctrl+D", "复制当前行", function(combo)
        plugin.sys.toast("复制触发!")
    end, true)
    
    local c = plugin.shortcut.register("goto_line_shortcut", "Alt+G", "跳转到行", function(combo)
        plugin.sys.toast("跳转触发!")
    end, true)
    
    local count = 0
    if a then count = count + 1 end
    if b then count = count + 1 end
    if c then count = count + 1 end
    plugin.sys.toast("已重新注册 " .. count .. " 个快捷键")
end)
