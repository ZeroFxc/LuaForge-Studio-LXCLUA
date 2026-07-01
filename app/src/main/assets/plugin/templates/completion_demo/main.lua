-- ============================================================
-- 代码补全演示插件 — plugin.completion API 全面展示
-- ============================================================

-- —————— 生命周期日志 ——————
plugin.logger.info("补全演示", "插件开始加载")
plugin.logger.debug("补全演示", "插件ID: " .. plugin.getPluginId())

-- —————— 辅助函数 ——————
local function logInfo(msg)
    plugin.logger.info("补全演示", msg)
end

local function logWarn(msg)
    plugin.logger.warn("补全演示", msg)
end

-- ============================================================
-- 第一部分: 关键字补全 — addKeyword / addKeywords
-- ============================================================

-- 1.1 添加单个 Lua 关键字
plugin.completion.addKeyword("async")
plugin.completion.addKeyword("await")
plugin.completion.addKeyword("export")
plugin.completion.addKeyword("yield")
logInfo("已添加 4 个单个关键字: async, await, export, yield")

-- 1.2 批量添加关键字（自定义框架 API）
local frameworkKeywords = {
    "newButton", "newLabel", "newTextView", "newEditText",
    "newImageView", "newListView", "newScrollView",
    "setText", "setTextColor", "setTextSize", "setBackgroundColor",
    "setVisibility", "setOnClick", "setOnLongClick",
    "createLayout", "createDialog", "createToast",
    "startActivity", "startService", "sendBroadcast",
    "newIntent", "newBundle", "putExtra", "getExtra",
    "newSharedPreferences", "getSharedPreferences",
    "newFile", "readFile", "writeFile", "deleteFile",
    "newThread", "newTimer", "newTimerTask",
    "httpGet", "httpPost", "parseJson", "stringifyJson",
    "encrypt", "decrypt", "base64Encode", "base64Decode",
    "md5", "sha1", "sha256",
    "sleep", "delay", "throttle", "debounce",
    "copy", "paste", "showToast", "showDialog",
    "getScreenWidth", "getScreenHeight", "dp2px", "px2dp",
    "uuid", "randomInt", "randomString", "timestamp",
    "versionCode", "versionName", "packageName"
}
local kwCount = plugin.completion.addKeywords(frameworkKeywords)
logInfo("已批量添加 " .. kwCount .. " 个框架关键字")

-- ============================================================
-- 第二部分: 包函数补全 — addPackage
-- ============================================================

-- 2.1 注册 myutils 包
plugin.completion.addPackage("myutils", {
    "isEmpty", "isNotEmpty", "isBlank", "isNotBlank",
    "trim", "split", "join", "replace", "capitalize",
    "startsWith", "endsWith", "contains", "indexOf",
    "substring", "toUpperCase", "toLowerCase",
    "formatDate", "formatNumber", "formatFileSize",
    "parseInt", "parseFloat", "parseBoolean",
    "toJson", "fromJson", "clone", "deepCopy",
    "merge", "pluck", "groupBy", "sortBy", "unique",
    "first", "last", "isEmpty", "isNotEmpty",
    "range", "shuffle", "chunk", "flatten"
})
logInfo("已添加 myutils 包函数: 36 个")

-- 2.2 注册 network 包
plugin.completion.addPackage("network", {
    "get", "post", "put", "delete", "patch", "head",
    "setTimeout", "setHeader", "setBody", "setQuery",
    "setBaseUrl", "addInterceptor", "removeInterceptor",
    "onSuccess", "onError", "onComplete",
    "retry", "cancel", "cancelAll",
    "isConnected", "getType", "getSignalStrength"
})
logInfo("已添加 network 包函数: 21 个")

-- 2.3 注册 storage 包
plugin.completion.addPackage("storage", {
    "get", "set", "remove", "clear", "has", "keys", "values",
    "getInt", "setInt", "getFloat", "setFloat",
    "getBoolean", "setBoolean", "getString", "setString",
    "getObject", "setObject", "getAll", "setAll",
    "size", "isEmpty", "containsKey",
    "backup", "restore", "export", "import"
})
logInfo("已添加 storage 包函数: 27 个")

-- 2.4 注册 ui 包
plugin.completion.addPackage("ui", {
    "toast", "toastLong", "dialog", "confirm", "alert",
    "input", "progress", "loading", "snackbar", "popup",
    "bottomSheet", "actionSheet", "picker", "datePicker",
    "timePicker", "colorPicker", "filePicker",
    "show", "hide", "dismiss", "dismissAll",
    "setTitle", "setMessage", "setPositiveButton", "setNegativeButton",
    "create", "inflate", "findById", "removeView"
})
logInfo("已添加 ui 包函数: 29 个")

-- ============================================================
-- 第三部分: 变量类型映射 — addVariableType
-- ============================================================

-- 3.1 注册常见变量类型映射（用于 obj.method() 成员补全）
plugin.completion.addVariableType("activity", "com.nirithy.lxclua.LuaActivity")
plugin.completion.addVariableType("context", "android.content.Context")
plugin.completion.addVariableType("intent", "android.content.Intent")
plugin.completion.addVariableType("bundle", "android.os.Bundle")
plugin.completion.addVariableType("view", "android.view.View")
plugin.completion.addVariableType("textView", "android.widget.TextView")
plugin.completion.addVariableType("button", "android.widget.Button")
plugin.completion.addVariableType("editText", "android.widget.EditText")
plugin.completion.addVariableType("imageView", "android.widget.ImageView")
plugin.completion.addVariableType("listView", "android.widget.ListView")
plugin.completion.addVariableType("linearLayout", "android.widget.LinearLayout")
plugin.completion.addVariableType("frameLayout", "android.widget.FrameLayout")
plugin.completion.addVariableType("sharedPrefs", "android.content.SharedPreferences")
plugin.completion.addVariableType("editor", "android.content.SharedPreferences$Editor")
plugin.completion.addVariableType("file", "java.io.File")
plugin.completion.addVariableType("thread", "java.lang.Thread")
plugin.completion.addVariableType("handler", "android.os.Handler")
plugin.completion.addVariableType("bitmap", "android.graphics.Bitmap")
plugin.completion.addVariableType("canvas", "android.graphics.Canvas")
plugin.completion.addVariableType("paint", "android.graphics.Paint")
plugin.completion.addVariableType("color", "android.graphics.Color")
plugin.completion.addVariableType("rect", "android.graphics.Rect")
plugin.completion.addVariableType("uri", "android.net.Uri")
plugin.completion.addVariableType("jsonObject", "org.json.JSONObject")
plugin.completion.addVariableType("jsonArray", "org.json.JSONArray")
logInfo("已添加 25 个变量类型映射")

-- ============================================================
-- 第四部分: 自定义补全提供器 — registerProvider
-- ============================================================

-- 4.1 注册 Lua 语言补全提供器（动态返回补全项）
local luaProviderId = plugin.completion.registerProvider("lua", function(prefix, language, filePath)
    local items = {}

    -- 根据前缀动态返回补全项
    if prefix:match("^on") then
        table.insert(items, {label = "onCreate", desc = "Activity 创建回调", commit = "onCreate(savedInstanceState)", kind = "Function"})
        table.insert(items, {label = "onStart", desc = "Activity 启动回调", commit = "onStart()", kind = "Function"})
        table.insert(items, {label = "onResume", desc = "Activity 恢复回调", commit = "onResume()", kind = "Function"})
        table.insert(items, {label = "onPause", desc = "Activity 暂停回调", commit = "onPause()", kind = "Function"})
        table.insert(items, {label = "onStop", desc = "Activity 停止回调", commit = "onStop()", kind = "Function"})
        table.insert(items, {label = "onDestroy", desc = "Activity 销毁回调", commit = "onDestroy()", kind = "Function"})
        table.insert(items, {label = "onClick", desc = "点击事件回调", commit = "onClick(v)", kind = "Function"})
        table.insert(items, {label = "onLongClick", desc = "长按事件回调", commit = "onLongClick(v)", kind = "Function"})
        table.insert(items, {label = "onTouch", desc = "触摸事件回调", commit = "onTouch(v, event)", kind = "Function"})
        table.insert(items, {label = "onItemClick", desc = "列表项点击回调", commit = "onItemClick(parent, v, pos, id)", kind = "Function"})
    elseif prefix:match("^new") then
        table.insert(items, {label = "newActivity", desc = "创建新的 Activity", commit = "newActivity()", kind = "Function"})
        table.insert(items, {label = "newDialog", desc = "创建新的 Dialog", commit = "newDialog()", kind = "Function"})
        table.insert(items, {label = "newLayout", desc = "创建新的布局", commit = "newLayout()", kind = "Function"})
        table.insert(items, {label = "newInstance", desc = "创建新的实例", commit = "newInstance()", kind = "Function"})
        table.insert(items, {label = "newThread", desc = "创建新的线程", commit = "newThread(runnable)", kind = "Function"})
    elseif prefix:match("^get") then
        table.insert(items, {label = "getIntent", desc = "获取 Intent", commit = "getIntent()", kind = "Function"})
        table.insert(items, {label = "getArguments", desc = "获取参数", commit = "getArguments()", kind = "Function"})
        table.insert(items, {label = "getExtras", desc = "获取额外数据", commit = "getExtras()", kind = "Function"})
        table.insert(items, {label = "getStringExtra", desc = "获取字符串参数", commit = "getStringExtra(key)", kind = "Function"})
        table.insert(items, {label = "getIntExtra", desc = "获取整数参数", commit = "getIntExtra(key, def)", kind = "Function"})
        table.insert(items, {label = "getApplicationContext", desc = "获取应用上下文", commit = "getApplicationContext()", kind = "Function"})
        table.insert(items, {label = "getResources", desc = "获取资源", commit = "getResources()", kind = "Function"})
    elseif prefix:match("^set") then
        table.insert(items, {label = "setResult", desc = "设置返回结果", commit = "setResult(resultCode)", kind = "Function"})
        table.insert(items, {label = "setContentView", desc = "设置内容视图", commit = "setContentView(layout)", kind = "Function"})
        table.insert(items, {label = "setTitle", desc = "设置标题", commit = "setTitle(title)", kind = "Function"})
        table.insert(items, {label = "setEnabled", desc = "设置启用状态", commit = "setEnabled(enabled)", kind = "Function"})
        table.insert(items, {label = "setVisible", desc = "设置可见性", commit = "setVisible(visible)", kind = "Function"})
        table.insert(items, {label = "setSelected", desc = "设置选中状态", commit = "setSelected(selected)", kind = "Function"})
    end

    return items
end)
if luaProviderId then
    logInfo("已注册 Lua 补全提供器: " .. luaProviderId)
end

-- 4.2 注册通用补全提供器（language="*" 表示对所有语言生效）
local generalProviderId = plugin.completion.registerProvider("*", function(prefix, language, filePath)
    if prefix:match("^TODO") or prefix:match("^FIXME") or prefix:match("^NOTE") then
        return {
            {label = "TODO: ", desc = "待办事项注释", commit = "TODO: 需要完成", kind = "Comment"},
            {label = "FIXME: ", desc = "需要修复的注释", commit = "FIXME: 需要修复", kind = "Comment"},
            {label = "NOTE: ", desc = "备注注释", commit = "NOTE: 重要说明", kind = "Comment"},
            {label = "HACK: ", desc = "临时方案注释", commit = "HACK: 临时解决方案", kind = "Comment"},
            {label = "XXX: ", desc = "需要注意的注释", commit = "XXX: 需要关注", kind = "Comment"},
            {label = "REVIEW: ", desc = "需要审查的注释", commit = "REVIEW: 需要审查", kind = "Comment"},
            {label = "OPTIMIZE: ", desc = "优化建议注释", commit = "OPTIMIZE: 可以优化", kind = "Comment"},
            {label = "DEPRECATED: ", desc = "废弃标记注释", commit = "DEPRECATED: 已废弃", kind = "Comment"},
        }
    end
    return {}
end)
if generalProviderId then
    logInfo("已注册通用补全提供器: " .. generalProviderId)
end

-- ============================================================
-- 第五部分: 快捷操作菜单 — 演示 API 管理功能
-- ============================================================

-- 5.1 查看当前补全状态
plugin.menu.addQuickAction("comp_status", "📊 补全状态", function()
    local info = "=== 代码补全演示插件 ===\n\n"
    info = info .. "插件ID: " .. plugin.getPluginId() .. "\n"
    info = info .. "API版本: " .. tostring(plugin.getApiVersion()) .. "\n\n"
    info = info .. "【已注册的补全数据】\n"
    info = info .. "  - 框架关键字: " .. kwCount .. " 个\n"
    info = info .. "  - 包注册: myutils, network, storage, ui\n"
    info = info .. "  - 变量类型映射: 25 个\n"
    info = info .. "  - 自定义补全提供器: 2 个\n"
    info = info .. "    · Lua 提供器: " .. (luaProviderId or "无") .. "\n"
    info = info .. "    · 通用提供器: " .. (generalProviderId or "无") .. "\n\n"
    info = info .. "【使用说明】\n"
    info = info .. "在 Lua 编辑器中输入以下内容测试补全:\n"
    info = info .. "  - 输入 'new' 查看框架关键字补全\n"
    info = info .. "  - 输入 'myutils.' 查看包函数补全\n"
    info = info .. "  - 输入 'activity.' 查看成员补全\n"
    info = info .. "  - 输入 'on' 查看动态补全提供器\n"
    info = info .. "  - 输入 'TODO' 查看注释补全\n"
    plugin.ui.showMessage("补全状态", info)
end)

-- 5.2 添加更多关键字
plugin.menu.addQuickAction("comp_addkw", "➕ 添加关键字", function()
    plugin.ui.showInputDialog("添加关键字", "输入关键字名称", "", {
        onInput = function(text)
            if text and #text > 0 then
                local ok = plugin.completion.addKeyword(text)
                if ok then
                    plugin.sys.toast("已添加关键字: " .. text)
                    logInfo("手动添加关键字: " .. text)
                else
                    plugin.sys.toast("关键字已存在: " .. text)
                end
            end
        end
    })
end)

-- 5.3 批量添加关键字
plugin.menu.addQuickAction("comp_addkws", "➕ 批量添加关键字", function()
    plugin.ui.showInputDialog("批量添加关键字", "输入关键字列表（逗号分隔）", "debug, trace, profile, benchmark", {
        onInput = function(text)
            if text and #text > 0 then
                local parts = {}
                for part in text:gmatch("[^,]+") do
                    local trimmed = part:match("^%s*(.-)%s*$")
                    if #trimmed > 0 then
                        table.insert(parts, trimmed)
                    end
                end
                if #parts > 0 then
                    local count = plugin.completion.addKeywords(parts)
                    plugin.sys.toast("已添加 " .. count .. " 个关键字")
                    logInfo("批量添加 " .. count .. " 个关键字")
                end
            end
        end
    })
end)

-- 5.4 添加包函数
plugin.menu.addQuickAction("comp_addpkg", "📦 添加包函数", function()
    plugin.ui.showInputDialog("添加包函数", "输入格式: 包名,函数1,函数2,...", "logger,info,warn,error,debug", {
        onInput = function(text)
            if text and #text > 0 then
                local parts = {}
                for part in text:gmatch("[^,]+") do
                    local trimmed = part:match("^%s*(.-)%s*$")
                    if #trimmed > 0 then
                        table.insert(parts, trimmed)
                    end
                end
                if #parts >= 2 then
                    local pkgName = parts[1]
                    local funcs = {}
                    for i = 2, #parts do
                        table.insert(funcs, parts[i])
                    end
                    plugin.completion.addPackage(pkgName, funcs)
                    plugin.sys.toast("已添加包 " .. pkgName .. " (" .. #funcs .. " 个函数)")
                    logInfo("添加包: " .. pkgName .. " (" .. #funcs .. " 个函数)")
                else
                    plugin.sys.toast("格式错误，请至少输入 包名,函数名")
                end
            end
        end
    })
end)

-- 5.5 添加变量类型映射
plugin.menu.addQuickAction("comp_addvar", "🔗 添加变量类型", function()
    plugin.ui.showInputDialog("添加变量类型映射", "输入格式: 变量名,完整类名", "myObj,com.example.MyClass", {
        onInput = function(text)
            if text and #text > 0 then
                local parts = {}
                for part in text:gmatch("[^,]+") do
                    local trimmed = part:match("^%s*(.-)%s*$")
                    table.insert(parts, trimmed)
                end
                if #parts >= 2 then
                    plugin.completion.addVariableType(parts[1], parts[2])
                    plugin.sys.toast("已添加变量类型: " .. parts[1] .. " → " .. parts[2])
                    logInfo("添加变量类型: " .. parts[1] .. " → " .. parts[2])
                else
                    plugin.sys.toast("格式错误，请输入: 变量名,完整类名")
                end
            end
        end
    })
end)

-- 5.6 注册自定义补全提供器
plugin.menu.addQuickAction("comp_regprov", "🔌 注册补全提供器", function()
    plugin.ui.showInputDialog("注册补全提供器", "输入语言类型（lua, java, *, 等）", "lua", {
        onInput = function(lang)
            local pid = plugin.completion.registerProvider(lang, function(prefix, language, filePath)
                if prefix:match("^hello") then
                    return {
                        {label = "helloWorld", desc = "Hello World 示例", commit = "helloWorld()", kind = "Function"},
                        {label = "helloUser", desc = "Hello User 示例", commit = "helloUser(name)", kind = "Function"},
                    }
                end
                return {}
            end)
            if pid then
                plugin.sys.toast("补全提供器已注册 (" .. lang .. "): " .. pid)
                logInfo("注册补全提供器: " .. pid .. " (" .. lang .. ")")
            end
        end
    })
end)

-- 5.7 注销补全提供器
plugin.menu.addQuickAction("comp_unregprov", "🔌 注销补全提供器", function()
    local items = {}
    if luaProviderId then table.insert(items, "Lua提供器: " .. luaProviderId) end
    if generalProviderId then table.insert(items, "通用提供器: " .. generalProviderId) end

    if #items == 0 then
        plugin.sys.toast("没有可注销的补全提供器")
        return
    end

    plugin.ui.showSingleChoiceDialog("选择要注销的提供器", items, 0, {
        onSelect = function(index)
            local pid = index == 0 and luaProviderId or generalProviderId
            if pid then
                local ok = plugin.completion.unregisterProvider(pid)
                plugin.sys.toast(ok and "已注销: " .. pid or "注销失败")
                if ok then logInfo("注销补全提供器: " .. pid) end
            end
        end
    })
end)

-- 5.8 清除所有补全数据
plugin.menu.addQuickAction("comp_clearall", "🗑 清除所有补全数据", function()
    plugin.ui.showConfirm("清除所有补全数据",
        "确定要清除本插件添加的所有补全数据吗？\n\n包括关键字、包函数、变量类型、补全提供器",
        function()
            local result = plugin.completion.clearAll()
            plugin.sys.toast("已清除: 关键字x" .. result[1] ..
                " 包x" .. result[2] .. " 变量类型x" .. result[3] ..
                " 提供器x" .. result[4])
            logInfo("清除所有补全数据: 关键字=" .. result[1] ..
                " 包=" .. result[2] .. " 变量类型=" .. result[3] ..
                " 提供器=" .. result[4])
        end,
        nil
    )
end)

-- 5.9 重新初始化（重新注册所有补全数据）
plugin.menu.addQuickAction("comp_reinit", "🔄 重新初始化", function()
    plugin.completion.clearAll()
    plugin.completion.addKeywords(frameworkKeywords)
    plugin.completion.addKeyword("async")
    plugin.completion.addKeyword("await")
    plugin.completion.addKeyword("export")
    plugin.completion.addKeyword("yield")
    plugin.completion.addPackage("myutils", {
        "isEmpty", "trim", "split", "join", "replace", "capitalize",
        "formatDate", "formatNumber", "toJson", "fromJson", "clone",
        "merge", "groupBy", "sortBy", "unique", "first", "last"
    })
    plugin.completion.addPackage("network", {
        "get", "post", "put", "delete", "setTimeout", "setHeader",
        "onSuccess", "onError", "retry", "cancel"
    })
    plugin.completion.addPackage("storage", {
        "get", "set", "remove", "clear", "has", "keys", "values",
        "getInt", "setInt", "getBoolean", "setBoolean"
    })
    plugin.completion.addPackage("ui", {
        "toast", "dialog", "confirm", "alert", "input", "progress",
        "show", "hide", "dismiss", "setTitle"
    })
    plugin.completion.addVariableType("activity", "com.nirithy.lxclua.LuaActivity")
    plugin.completion.addVariableType("context", "android.content.Context")
    plugin.completion.addVariableType("intent", "android.content.Intent")
    plugin.completion.addVariableType("view", "android.view.View")
    plugin.completion.addVariableType("button", "android.widget.Button")
    plugin.completion.addVariableType("editText", "android.widget.EditText")
    plugin.completion.addVariableType("textView", "android.widget.TextView")
    plugin.completion.addVariableType("file", "java.io.File")
    plugin.completion.addVariableType("thread", "java.lang.Thread")
    plugin.completion.addVariableType("bitmap", "android.graphics.Bitmap")
    plugin.sys.toast("补全数据已重新初始化")
    logInfo("重新初始化完成")
end)

-- ============================================================
-- 初始化完成
-- ============================================================
plugin.logger.info("补全演示", "插件初始化完成")
plugin.logger.info("补全演示", "已注册 " .. kwCount .. " 个关键字 + 4 个包 + 25 个变量类型 + 2 个补全提供器 + 9 个快捷操作")
plugin.sys.toast("代码补全演示插件已加载 ✓\n在编辑器中输入 'new' / 'myutils.' / 'activity.' / 'on' / 'TODO' 测试补全")