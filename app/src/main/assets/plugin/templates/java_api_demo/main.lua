-- ============================================
-- Java 反射 API 演示插件
-- 演示如何通过 plugin.reflect 模块操作 Java 类与对象
-- ============================================
--
-- ============================================
-- Java 反射 API 调用规则（重要！）
-- ============================================
-- 在 LXC-LUA 插件中，Java 对象是 UserData 类型。
-- 通过 plugin.reflect 获取到的 Java 实例对象，调用其方法
-- 必须使用 . （点号），不能使用 : （冒号）。
--
-- 错误写法（会报错，因为 : 会隐式传入 self 作为第一个参数）：
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
-- 另外，本项目提供 plugin.reflect 封装模块，推荐使用：
--   plugin.reflect.loadClass(className)            -- 加载类
--   plugin.reflect.newInstance(className, args)    -- 创建实例
--   plugin.reflect.callMethod(obj, method, args)   -- 调用实例方法
--   plugin.reflect.callStaticMethod(cls, m, args)  -- 调用静态方法
--   plugin.reflect.getField(obj, field)            -- 读实例字段
--   plugin.reflect.setField(obj, field, value)     -- 写实例字段
--   plugin.reflect.getStaticField(cls, field)      -- 读静态字段
--   plugin.reflect.setStaticField(cls, field, val) -- 写静态字段
-- ============================================

plugin.sys.log("JavaAPIDemo", "插件已加载")

-- ============================================
-- 工具函数：安全调用（捕获 Java 异常）
-- ============================================

--- 安全执行 Java 调用，使用 pcall 捕获异常
-- @param label 日志标签
-- @param fn 要执行的函数
-- @return 执行结果，失败返回 nil
local function safeCall(label, fn)
    local ok, result = pcall(fn)
    if not ok then
        plugin.sys.log("JavaAPIDemo", "[" .. label .. "] 调用异常: " .. tostring(result))
        plugin.sys.toast("[" .. label .. "] 异常: " .. tostring(result))
        return nil
    end
    return result
end

-- ============================================
-- 演示 1：静态方法调用
-- 通过 plugin.reflect.callStaticMethod 调用 Java 静态方法
-- 例如 android.util.Log.i、java.lang.System.currentTimeMillis 等
-- ============================================
local function demoStaticMethod()
    plugin.sys.log("JavaAPIDemo", "=== 演示 1：静态方法调用 ===")

    -- 1.1 调用 java.lang.System.currentTimeMillis() 获取时间戳
    local timestamp = safeCall("currentTimeMillis", function()
        return plugin.reflect.callStaticMethod("java.lang.System", "currentTimeMillis", nil)
    end)
    if timestamp then
        plugin.sys.log("JavaAPIDemo", "当前时间戳(ms): " .. tostring(timestamp))
    end

    -- 1.2 调用 java.lang.String 的静态方法 valueOf
    local strVal = safeCall("String.valueOf", function()
        return plugin.reflect.callStaticMethod("java.lang.String", "valueOf", {12345})
    end)
    if strVal then
        plugin.sys.log("JavaAPIDemo", "String.valueOf(12345) = " .. tostring(strVal))
    end

    -- 1.3 尝试直接加载类并调用静态方法（如果 UserData 支持 . 调用）
    local Log = safeCall("loadClass(Log)", function()
        return plugin.reflect.loadClass("android.util.Log")
    end)
    if Log then
        plugin.sys.log("JavaAPIDemo", "已加载 android.util.Log 类")
        -- 注意：通过 UserData 直接调用静态方法时使用 . 而非 :
        -- 示例：Log.i("JavaAPIDemo", "来自 Java Log.i 的消息")
        -- 如果 UserData 桥接可用，这里可以直接 . 调用；否则使用 callStaticMethod
        pcall(function()
            -- 尝试 UserData 直接 . 调用
            if Log.i then
                Log.i("JavaAPIDemo", "通过 UserData . 调用 Log.i 成功")
                plugin.sys.log("JavaAPIDemo", "UserData . 调用 Log.i 成功")
            end
        end)
    end

    plugin.sys.toast("静态方法演示完成，请查看日志")
end

-- ============================================
-- 演示 2：静态字段访问
-- ============================================
local function demoStaticField()
    plugin.sys.log("JavaAPIDemo", "=== 演示 2：静态字段访问 ===")

    -- 2.1 访问 java.io.File.separator 字段
    local separator = safeCall("File.separator", function()
        return plugin.reflect.getStaticField("java.io.File", "separator")
    end)
    if separator then
        plugin.sys.log("JavaAPIDemo", "文件分隔符: " .. tostring(separator))
    end

    -- 2.2 访问 java.lang.Integer.MAX_VALUE
    local maxInt = safeCall("Integer.MAX_VALUE", function()
        return plugin.reflect.getStaticField("java.lang.Integer", "MAX_VALUE")
    end)
    if maxInt then
        plugin.sys.log("JavaAPIDemo", "Integer.MAX_VALUE = " .. tostring(maxInt))
    end

    -- 2.3 尝试访问 android.os.Build.VERSION.SDK_INT
    local sdkInt = safeCall("Build.VERSION.SDK_INT", function()
        -- SDK_INT 是 android.os.Build$VERSION 的嵌套类字段
        local VersionCls = plugin.reflect.loadClass("android.os.Build$VERSION")
        if VersionCls and VersionCls.SDK_INT then
            return VersionCls.SDK_INT
        end
        return plugin.reflect.getStaticField("android.os.Build$VERSION", "SDK_INT")
    end)
    if sdkInt then
        plugin.sys.log("JavaAPIDemo", "Android SDK_INT = " .. tostring(sdkInt))
    else
        plugin.sys.log("JavaAPIDemo", "无法访问 Build.VERSION.SDK_INT（可能非 Android 环境）")
    end

    local info = "静态字段演示:\n"
    info = info .. "File.separator = " .. tostring(separator) .. "\n"
    info = info .. "Integer.MAX_VALUE = " .. tostring(maxInt)
    plugin.ui.showMessage("静态字段访问", info)
end

-- ============================================
-- 演示 3：创建 Java 实例（new 对象）
-- ============================================
local function demoNewInstance()
    plugin.sys.log("JavaAPIDemo", "=== 演示 3：创建 Java 实例 ===")

    -- 3.1 无参构造：new java.util.Date()
    local date1 = safeCall("new Date()", function()
        return plugin.reflect.newInstance("java.util.Date", nil)
    end)
    if date1 then
        plugin.sys.log("JavaAPIDemo", "无参 Date: " .. tostring(date1))
    end

    -- 3.2 带 long 参数的构造：new Date(timestamp)
    local date2 = safeCall("new Date(long)", function()
        return plugin.reflect.newInstance("java.util.Date", {0})
    end)
    if date2 then
        plugin.sys.log("JavaAPIDemo", "Date(0) = " .. tostring(date2))
    end

    -- 3.3 String 构造：new String("Hello from Lua")
    local jStr = safeCall("new String", function()
        return plugin.reflect.newInstance("java.lang.String", {"Hello from Lua"})
    end)
    if jStr then
        -- 演示：获取到实例后用 . 调用 toString()
        local strValue = "[UserData]"
        pcall(function()
            if jStr.toString then
                strValue = jStr.toString()
            end
        end)
        plugin.sys.log("JavaAPIDemo", "String 实例: " .. strValue)
    end

    -- 3.4 创建 java.util.ArrayList
    local list = safeCall("new ArrayList", function()
        return plugin.reflect.newInstance("java.util.ArrayList", nil)
    end)
    if list then
        plugin.sys.log("JavaAPIDemo", "ArrayList 实例创建成功: " .. tostring(list))
    end

    -- 将创建的 date1 保存到全局以便后续演示
    _G._java_demo_date = date1
    _G._java_demo_list = list

    plugin.sys.toast("Java 实例创建完成，请查看日志")
end

-- ============================================
-- 演示 4：实例方法调用（使用 . 操作符）
-- ============================================
local function demoInstanceMethod()
    plugin.sys.log("JavaAPIDemo", "=== 演示 4：实例方法调用 ===")

    -- 确保有可用实例
    if not _G._java_demo_date then
        _G._java_demo_date = safeCall("new Date()", function()
            return plugin.reflect.newInstance("java.util.Date", nil)
        end)
    end
    local date = _G._java_demo_date
    if not date then
        plugin.sys.toast("无法创建 Date 实例")
        return
    end

    -- 4.1 使用 plugin.reflect.callMethod 调用实例方法
    local timeMs = safeCall("Date.getTime", function()
        return plugin.reflect.callMethod(date, "getTime", nil)
    end)
    if timeMs then
        plugin.sys.log("JavaAPIDemo", "Date.getTime() = " .. tostring(timeMs))
    end

    -- 4.2 使用 UserData . 直接调用 toString()（演示 . 而非 : 的正确写法）
    local dateStr = nil
    local okDirect = pcall(function()
        if date.toString then
            -- 正确：obj.method()  错误：obj:method()
            dateStr = date.toString()
        end
    end)
    if okDirect and dateStr then
        plugin.sys.log("JavaAPIDemo", "date.toString() = " .. tostring(dateStr))
    else
        -- 回退到 reflect 调用
        dateStr = safeCall("Date.toString (reflect)", function()
            return plugin.reflect.callMethod(date, "toString", nil)
        end)
        plugin.sys.log("JavaAPIDemo", "date.toString (reflect) = " .. tostring(dateStr))
    end

    -- 4.3 调用 ArrayList 的 add / size / get 方法
    local list = _G._java_demo_list
    if list then
        safeCall("ArrayList.add", function()
            -- 尝试 UserData 直接 . 调用
            if list.add then
                list.add("Apple")
                list.add("Banana")
                list.add("Cherry")
            else
                plugin.reflect.callMethod(list, "add", {"Apple"})
                plugin.reflect.callMethod(list, "add", {"Banana"})
                plugin.reflect.callMethod(list, "add", {"Cherry"})
            end
        end)

        local size = safeCall("ArrayList.size", function()
            if list.size then return list.size() end
            return plugin.reflect.callMethod(list, "size", nil)
        end)
        plugin.sys.log("JavaAPIDemo", "ArrayList.size() = " .. tostring(size))

        -- 遍历 ArrayList
        if size and type(size) == "number" then
            for i = 0, size - 1 do
                local item = safeCall("ArrayList.get(" .. i .. ")", function()
                    if list.get then return list.get(i) end
                    return plugin.reflect.callMethod(list, "get", {i})
                end)
                if item then
                    plugin.sys.log("JavaAPIDemo", "  [" .. i .. "] = " .. tostring(item))
                end
            end
        end
    end

    local info = "实例方法调用演示:\n"
    info = info .. "Date.toString = " .. tostring(dateStr) .. "\n"
    info = info .. "Date.getTime = " .. tostring(timeMs) .. "\n"
    info = info .. "ArrayList 已添加 3 个元素"
    plugin.ui.showMessage("实例方法调用", info)
end

-- ============================================
-- 演示 5：实例字段读写
-- ============================================
local function demoInstanceField()
    plugin.sys.log("JavaAPIDemo", "=== 演示 5：实例字段读写 ===")

    -- 使用 java.awt.Point 不可用在 Android，改用 ReflectionTestClass 或 java.util.Date 的字段
    -- Date 没有公开字段，使用 plugin.reflect 测试类（如果存在）
    local testClassName = "com.luaforge.studio.lxclua.plugin.ReflectionTestClass"
    local obj = safeCall("new ReflectionTestClass", function()
        return plugin.reflect.newInstance(testClassName, {"字段测试"})
    end)

    if obj then
        -- 读实例字段
        local before = safeCall("get instanceField", function()
            return plugin.reflect.getField(obj, "instanceField")
        end)
        plugin.sys.log("JavaAPIDemo", "instanceField 修改前: " .. tostring(before))

        -- 写实例字段
        safeCall("set instanceField", function()
            plugin.reflect.setField(obj, "instanceField", "Lua 修改后的值")
        end)

        -- 再次读取
        local after = safeCall("get instanceField", function()
            return plugin.reflect.getField(obj, "instanceField")
        end)
        plugin.sys.log("JavaAPIDemo", "instanceField 修改后: " .. tostring(after))

        local info = "实例字段读写:\n"
        info = info .. "修改前: " .. tostring(before) .. "\n"
        info = info .. "修改后: " .. tostring(after)
        plugin.ui.showMessage("实例字段", info)
    else
        -- 回退方案：演示 StringBuilder（没有公开字段，演示 length() 方法代替）
        plugin.sys.log("JavaAPIDemo", "ReflectionTestClass 不存在，使用 StringBuilder 演示")
        local sb = safeCall("new StringBuilder", function()
            return plugin.reflect.newInstance("java.lang.StringBuilder", nil)
        end)
        if sb then
            safeCall("StringBuilder.append", function()
                if sb.append then
                    sb.append("Hello")
                    sb.append(" ")
                    sb.append("Lua")
                else
                    plugin.reflect.callMethod(sb, "append", {"Hello"})
                    plugin.reflect.callMethod(sb, "append", {" "})
                    plugin.reflect.callMethod(sb, "append", {"Lua"})
                end
            end)
            local len = safeCall("StringBuilder.length", function()
                if sb.length then return sb.length() end
                return plugin.reflect.callMethod(sb, "length", nil)
            end)
            local str = safeCall("StringBuilder.toString", function()
                if sb.toString then return sb.toString() end
                return plugin.reflect.callMethod(sb, "toString", nil)
            end)
            local info = "StringBuilder 演示:\n内容: " .. tostring(str) .. "\n长度: " .. tostring(len)
            plugin.ui.showMessage("实例字段演示(回退)", info)
        end
    end
end

-- ============================================
-- 演示 6：数组操作（创建、访问长度、遍历）
-- Lua 中创建 Java 数组可通过 plugin.reflect 或反射 Array.newInstance
-- ============================================
local function demoArray()
    plugin.sys.log("JavaAPIDemo", "=== 演示 6：数组操作 ===")

    -- 6.1 调用 java.util.ArrayList.toArray() 作为数组来源
    local list = _G._java_demo_list
    if not list then
        list = safeCall("new ArrayList for array", function()
            local l = plugin.reflect.newInstance("java.util.ArrayList", nil)
            if l.add then
                l.add("one"); l.add("two"); l.add("three")
            else
                plugin.reflect.callMethod(l, "add", {"one"})
                plugin.reflect.callMethod(l, "add", {"two"})
                plugin.reflect.callMethod(l, "add", {"three"})
            end
            return l
        end)
        _G._java_demo_list = list
    end

    if list then
        local size = safeCall("list.size", function()
            if list.size then return list.size() end
            return plugin.reflect.callMethod(list, "size", nil)
        end)
        plugin.sys.log("JavaAPIDemo", "ArrayList 大小: " .. tostring(size))

        -- 6.2 遍历 ArrayList（Lua 侧 for 循环）
        if size and type(size) == "number" then
            local items = {}
            for i = 0, size - 1 do
                local item = safeCall("list.get(" .. i .. ")", function()
                    if list.get then return list.get(i) end
                    return plugin.reflect.callMethod(list, "get", {i})
                end)
                items[#items + 1] = tostring(item)
            end
            plugin.sys.log("JavaAPIDemo", "遍历结果: " .. table.concat(items, ", "))
            plugin.ui.showMessage("数组/集合操作", "ArrayList 元素:\n  " .. table.concat(items, "\n  "))
        end
    else
        plugin.sys.toast("无法创建 ArrayList 进行数组演示")
    end
end

-- ============================================
-- 演示 7：异常捕获（pcall 包裹 Java 调用）
-- ============================================
local function demoException()
    plugin.sys.log("JavaAPIDemo", "=== 演示 7：异常捕获 ===")

    -- 7.1 尝试调用一个不存在的方法，应该抛出异常
    local badList = safeCall("new ArrayList", function()
        return plugin.reflect.newInstance("java.util.ArrayList", nil)
    end)
    if badList then
        local ok, err = pcall(function()
            -- 故意调用不存在的方法
            if badList.nonExistentMethod then
                badList.nonExistentMethod()
            else
                -- 通过 reflect 调用不存在的方法也会抛异常
                plugin.reflect.callMethod(badList, "nonExistentMethod", nil)
            end
        end)
        if ok then
            plugin.sys.log("JavaAPIDemo", "预期异常未抛出（方法可能不存在但未触发异常）")
        else
            plugin.sys.log("JavaAPIDemo", "成功捕获 Java 异常: " .. tostring(err))
        end
    end

    -- 7.2 尝试加载不存在的类
    local ok2, err2 = pcall(function()
        plugin.reflect.loadClass("com.example.NonExistentClass")
    end)
    if not ok2 then
        plugin.sys.log("JavaAPIDemo", "捕获类加载异常: " .. tostring(err2))
    end

    -- 7.3 正常调用包裹在 pcall 中
    local ok3, result = pcall(function()
        return plugin.reflect.callStaticMethod("java.lang.String", "valueOf", {42})
    end)
    if ok3 then
        plugin.sys.log("JavaAPIDemo", "正常调用成功: " .. tostring(result))
    end

    plugin.sys.toast("异常捕获演示完成，请查看日志")
end

-- ============================================
-- 演示 8：Toast 与 Log 综合演示
-- 调用 android.widget.Toast（如果在 Android 环境）
-- ============================================
local function demoToast()
    plugin.sys.log("JavaAPIDemo", "=== 演示 8：Toast 综合演示 ===")

    -- 优先使用 plugin.sys.toast（更可靠）
    plugin.sys.toast("Java API 演示插件运行中！")

    -- 尝试通过反射获取系统信息
    local info = "=== Java 环境信息 ===\n"

    -- Java 版本
    local javaVer = safeCall("java.version", function()
        return plugin.reflect.callStaticMethod("java.lang.System", "getProperty", {"java.version"})
    end)
    info = info .. "Java 版本: " .. tostring(javaVer) .. "\n"

    -- 可用处理器
    local nCores = nil
    pcall(function()
        local rt = plugin.reflect.callStaticMethod("java.lang.Runtime", "getRuntime", nil)
        if rt and rt.availableProcessors then
            nCores = rt.availableProcessors()
        elseif rt then
            nCores = plugin.reflect.callMethod(rt, "availableProcessors", nil)
        end
    end)
    info = info .. "CPU 核心数: " .. tostring(nCores) .. "\n"

    -- 当前时间毫秒
    local now = safeCall("System.currentTimeMillis", function()
        return plugin.reflect.callStaticMethod("java.lang.System", "currentTimeMillis", nil)
    end)
    info = info .. "当前时间戳: " .. tostring(now) .. "\n"

    -- 内存信息
    pcall(function()
        local rt = plugin.reflect.callStaticMethod("java.lang.Runtime", "getRuntime", nil)
        if rt then
            local totalMem, freeMem
            if rt.totalMemory then
                totalMem = rt.totalMemory()
                freeMem = rt.freeMemory()
            else
                totalMem = plugin.reflect.callMethod(rt, "totalMemory", nil)
                freeMem = plugin.reflect.callMethod(rt, "freeMemory", nil)
            end
            if totalMem then
                info = info .. "JVM 总内存: " .. tostring(math.floor(totalMem / 1024 / 1024)) .. " MB\n"
            end
            if freeMem then
                info = info .. "JVM 空闲内存: " .. tostring(math.floor(freeMem / 1024 / 1024)) .. " MB\n"
            end
        end
    end)

    plugin.ui.showMessage("Java 环境信息", info)
end

-- ============================================
-- 注册菜单项：添加侧滑栏菜单 "Java API 演示"
-- ============================================
plugin.nav.addSidebarItem("java_api_demo_menu", "Java API演示", "custom", "code", function()
    plugin.sys.log("JavaAPIDemo", "点击侧滑栏菜单项：Java API演示")
    plugin.ui.showMessage("Java API 演示",
        "本插件演示以下 Java 反射功能:\n\n" ..
        "1. 静态方法调用\n" ..
        "2. 静态字段访问\n" ..
        "3. 创建 Java 实例\n" ..
        "4. 实例方法调用（使用 . 而非 :）\n" ..
        "5. 实例字段读写\n" ..
        "6. 数组/集合操作\n" ..
        "7. 异常捕获（pcall）\n" ..
        "8. 环境信息\n\n" ..
        "请通过编辑器快捷操作按钮体验各功能")
end)

-- ============================================
-- 注册快捷操作按钮
-- ============================================
plugin.menu.addQuickAction("java_static_method", "静态方法", demoStaticMethod)
plugin.menu.addQuickAction("java_static_field", "静态字段", demoStaticField)
plugin.menu.addQuickAction("java_new_instance", "创建实例", demoNewInstance)
plugin.menu.addQuickAction("java_instance_method", "实例方法", demoInstanceMethod)
plugin.menu.addQuickAction("java_instance_field", "实例字段", demoInstanceField)
plugin.menu.addQuickAction("java_array", "数组操作", demoArray)
plugin.menu.addQuickAction("java_exception", "异常捕获", demoException)
plugin.menu.addQuickAction("java_env_info", "环境信息", demoToast)

-- 一键运行全部演示
plugin.menu.addQuickAction("java_run_all", "运行全部演示", function()
    plugin.sys.toast("开始运行全部 Java API 演示...")
    plugin.sys.log("JavaAPIDemo", "========== 开始运行全部演示 ==========")
    demoStaticMethod()
    demoNewInstance()
    demoInstanceMethod()
    demoStaticField()
    demoInstanceField()
    demoArray()
    demoException()
    demoToast()
    plugin.sys.log("JavaAPIDemo", "========== 全部演示运行完成 ==========")
    plugin.sys.toast("全部演示运行完成！")
end)

-- ============================================
-- 关于页面扩展
-- ============================================
plugin.about.addSection("java_api_demo_about", "Java API 演示插件", 0)
plugin.about.addInfo("java_api_about_desc", "java_api_demo_about",
    "功能说明", "演示 Java 反射 API 用法，强调使用 . 调用方法而非 :",
    "info", 0xFF36618E)
plugin.about.addCallback("java_api_run_demo", "java_api_demo_about",
    "运行演示", "点击运行所有 Java API 演示",
    "play", 0xFF5B8DEF, function()
        plugin.sys.toast("请使用编辑器内快捷按钮运行演示")
    end)

plugin.sys.log("JavaAPIDemo", "初始化完成，已注册 8 个快捷操作 + 1 个侧滑栏项 + 1 个关于 section")
