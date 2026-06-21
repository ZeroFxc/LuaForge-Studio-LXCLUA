-- ============================================================
-- 悬浮球 AI 代码生成器
--
-- 功能：
--   1. 创建悬浮球（"AI" 图标），可拖拽移动
--   2. 点击悬浮球展开输入面板（WebUI 模板渲染）
--   3. 获取当前活动编辑器内容和语言
--   4. 将提示词 + 编辑器代码发送给 AI 生成代码
--   5. 支持三种输出模式：
--      - 面板流式：AI 内容实时显示在面板中（默认）
--      - 编辑器流式：AI 逐字输入到编辑器光标处
--      - 编辑器一次性：AI 完成后一次性插入到编辑器
--   6. 流式开关：在应用设置中控制是否使用流式输出
--   7. 手柄：可通过 plugin.floating.setResizeHandleEnabled 控制显示/隐藏
--
-- 架构说明：
--   面板内容由 WebUI 模板渲染（插件自定义 web/panel.html），
--   Lua 与 WebUI 通过以下方式双向通信：
--   - WebUI → Lua：JS 调用 LXC.callLua("onFloatingPanelSubmit", json)
--   - Lua → WebUI：plugin.floating.sendToWeb(id, json) 或
--     plugin.floating.appendStreamContent(id, text) 等便捷方法
--   也可以通过 plugin.floating.showPanel() 使用内置默认模板
-- ============================================================

local ballId = nil
local isProcessing = false
local outputMode = "panel"  -- 输出模式: "panel" | "editor_stream" | "editor_once"

-- ============================================================
-- 全局函数：WebUI 面板提交时调用（由 JS 通过 LXC.callLua 触发）
-- ============================================================
function onFloatingPanelSubmit(jsonStr)
    plugin.logger.debug("FloatingAI", "onFloatingPanelSubmit 收到: " .. tostring(jsonStr):sub(1, 200))
    local ok, data = pcall(json.decode, jsonStr)
    if not ok or not data then
        plugin.logger.warn("FloatingAI", "onFloatingPanelSubmit: JSON 解析失败: " .. tostring(jsonStr) .. " 错误: " .. tostring(data))
        return
    end
    -- data 是 Java JSONObject，LuaJava 用 . 语法调用方法（: 会额外传 self）
    local text = ""
    if data.has("text") then
        text = data.getString("text")
    elseif data.has("prompt") then
        text = data.getString("prompt")
    end
    plugin.logger.debug("FloatingAI", "onFloatingPanelSubmit: text=" .. tostring(text):sub(1, 100))
    if text ~= "" then
        -- 临时去掉 pcall 让错误直接暴露，定位具体行号
        plugin.logger.debug("FloatingAI", "即将调用 generateCode, prompt=" .. tostring(text):sub(1, 50))
        generateCode(text)
    else
        plugin.logger.warn("FloatingAI", "onFloatingPanelSubmit: text 为空")
    end
end

-- ============================================================
-- 全局函数：WebUI 面板关闭时调用
-- ============================================================
function onFloatingPanelCancel(jsonStr)
    plugin.logger.debug("FloatingAI", "onFloatingPanelCancel 触发")
    if ballId then
        plugin.floating.hidePanel(ballId)
    end
end

-- ============================================================
-- 全局函数：WebUI 停止按钮点击时调用
-- ============================================================
function onFloatingPanelStop(jsonStr)
    plugin.logger.debug("FloatingAI", "onFloatingPanelStop 触发")
    plugin.ai.cancelAll()
end

-- ============================================================
-- 显示提示
-- ============================================================
local function showTip(msg)
    plugin.sys.toast(msg)
end

-- ============================================================
-- 获取编辑器上下文（公共逻辑）
-- ============================================================
local function getEditorContext()
    local editorInfo = plugin.editor.getActiveEditorInfo()
    local editorActive = editorInfo["editorActive"]
    local fileOpen = editorInfo["fileOpen"]
    local filePath = editorInfo["filePath"] or "未知文件"
    local editorText = editorInfo["content"] or ""
    local language = editorInfo["language"] or "lua"
    local cursorLine = editorInfo["cursorLine"] or 0
    local cursorColumn = editorInfo["cursorColumn"] or 0

    if not editorActive then
        plugin.logger.warn("FloatingAI", "编辑器未激活，请先打开一个文件")
        showTip("请先打开一个文件再使用 AI 生成")
        return nil
    end
    if not fileOpen then
        plugin.logger.warn("FloatingAI", "编辑器已激活但未打开文件")
        showTip("请在编辑器中打开一个文件")
        return nil
    end
    if #editorText == 0 then
        plugin.logger.info("FloatingAI", "文件为空: " .. filePath)
    end

    return {
        filePath = filePath,
        editorText = editorText,
        language = language,
        cursorLine = cursorLine,
        cursorColumn = cursorColumn,
    }
end

-- ============================================================
-- 构建消息列表
-- ============================================================
local function buildMessages(editorText, language, filePath, prompt)
    return {
        {role = "system", content = string.format(
            [[你是 LXC-LUA 编辑器的代码助手，当前文件：%s（%s）。
你可以直接修改编辑器内容，也可以生成代码让用户参考。
- 回复简洁，直接给出结果，不要啰嗦
- 如果改完了代码，简单告知改了什么即可
- 遵循 %s 的语法规范]],
            filePath, language, language
        )},
        {role = "user", content = string.format(
            [[当前编辑器代码内容：
```%s
%s
```

用户提示词：%s

请根据以上内容生成代码。如果用户提到了需要修改现有代码，请提供完整的修改后代码或精确的代码片段。]],
            language, editorText, prompt
        )}
    }
end

-- ============================================================
-- 提取 AI 响应中的代码块
-- ============================================================
local function extractCode(text)
    local code = text:match("```[%w]*\n(.-)```")
    if code and code ~= "" then return code end
    code = text:match("```(.-)```")
    if code and code ~= "" then return code:gsub("^%w*\n", "") end
    return text
end

-- ============================================================
-- 核心：AI 代码生成（流式，面板显示）
-- ============================================================
local function generateCodeStream(prompt, ctx)
    if isProcessing then
        showTip("正在处理中，请稍候...")
        return
    end

    isProcessing = true
    local accumulated = ""
    local chunkCount = 0

    plugin.logger.info("FloatingAI", string.format(
        "开始生成 (流式→面板) | 文件: %s | 语言: %s | 内容: %d 字符 | 提示词: %s",
        ctx.filePath, ctx.language, #ctx.editorText, prompt))

    -- 面板内显示流式输出区域（消息发送到 WebView）
    if ballId then
        plugin.floating.showPanelLoading(ballId)
        plugin.floating.updatePanelTitle(ballId, "AI 正在生成...")
        plugin.floating.showStreamOutput(ballId)
        plugin.floating.showReasoningOutput(ballId)
    end

    local messages = buildMessages(ctx.editorText, ctx.language, ctx.filePath, prompt)

    plugin.ai.chatStream(
        messages,
        function(chunk)
            if chunk and chunk ~= "" then
                accumulated = accumulated .. chunk
                chunkCount = chunkCount + 1
                if ballId then
                    plugin.floating.appendStreamContent(ballId, chunk)
                    plugin.floating.updatePanelTitle(ballId, string.format("AI 生成中... %d 字符", #accumulated))
                end
            end
        end,
        function(success, content, error)
            isProcessing = false
            if ballId then
                plugin.floating.hidePanelLoading(ballId)
                plugin.floating.hideReasoningOutput(ballId)
                plugin.floating.hideStreamOutput(ballId)
            end
            if success and accumulated ~= "" then
                plugin.logger.info("FloatingAI", string.format("流式生成完成, 共 %d 字符", #accumulated))
                if ballId then
                    plugin.floating.updatePanelTitle(ballId, "AI 代码生成 - 完成")
                end
                local code = extractCode(accumulated)
                if code and code ~= "" then
                    plugin.editor.insertText("\n" .. code .. "\n")
                end
                showTip("AI 代码已生成")
            else
                plugin.logger.warn("FloatingAI", "生成失败: " .. (error or "未知错误"))
                if ballId then
                    plugin.floating.updatePanelTitle(ballId, "AI 代码生成 - 失败")
                end
                showTip("AI 生成失败: " .. (error or "未知错误"))
            end
        end,
        -- 思考过程回调：实时显示 AI 推理内容到思考面板
        function(reasoning)
            if ballId and reasoning and reasoning ~= "" then
                plugin.floating.appendReasoningContent(ballId, reasoning)
            end
        end,
        -- 工具调用回调：在 WebUI 面板中以卡片形式显示工具调用
        function(name, args, result)
            plugin.logger.info("FloatingAI", string.format("工具调用: %s(%s) -> %s", name, tostring(args):sub(1, 100), tostring(result):sub(1, 100)))
            if ballId then
                local toolJson = '{"type":"toolCall","name":"' .. name .. '","args":"' .. tostring(args):gsub('"', '\\"'):sub(1, 200) .. '","result":"' .. tostring(result):gsub('"', '\\"'):gsub('\n', '\\n'):sub(1, 300) .. '"}'
                plugin.floating.sendToWeb(ballId, toolJson)
            end
        end
    )
end

-- ============================================================
-- 核心：AI 代码生成（流式，直接输出到编辑器）
-- ============================================================
local function generateCodeStreamToEditor(prompt, ctx)
    if isProcessing then
        showTip("正在处理中，请稍候...")
        return
    end

    isProcessing = true
    plugin.logger.info("FloatingAI", string.format(
        "开始生成 (流式→编辑器) | 文件: %s | 语言: %s | 提示词: %s",
        ctx.filePath, ctx.language, prompt))

    if ballId then
        plugin.floating.showPanelLoading(ballId)
        plugin.floating.updatePanelTitle(ballId, "AI 正在输入到编辑器...")
    end

    local messages = buildMessages(ctx.editorText, ctx.language, ctx.filePath, prompt)

    plugin.ai.chatStreamToEditor(
        messages,
        function(success, content, error)
            isProcessing = false
            if ballId then
                plugin.floating.hidePanelLoading(ballId)
            end
            if success then
                plugin.logger.info("FloatingAI", "流式→编辑器 完成, 共 " .. #content .. " 字符")
                if ballId then
                    plugin.floating.updatePanelTitle(ballId, "AI 代码生成 - 完成")
                end
                showTip("AI 代码已生成到编辑器")
            else
                plugin.logger.warn("FloatingAI", "流式→编辑器 失败: " .. (error or "未知错误"))
                if ballId then
                    plugin.floating.updatePanelTitle(ballId, "AI 代码生成 - 失败")
                end
                showTip("AI 生成失败: " .. (error or "未知错误"))
            end
        end
    )
end

-- ============================================================
-- 核心：AI 代码生成（一次性，完成后插入编辑器）
-- ============================================================
local function generateCodeToEditor(prompt, ctx)
    if isProcessing then
        showTip("正在处理中，请稍候...")
        return
    end

    isProcessing = true
    plugin.logger.info("FloatingAI", string.format(
        "开始生成 (一次性→编辑器) | 文件: %s | 语言: %s | 提示词: %s",
        ctx.filePath, ctx.language, prompt))

    if ballId then
        plugin.floating.showPanelLoading(ballId)
        plugin.floating.updatePanelTitle(ballId, "AI 正在生成...")
    end

    local messages = buildMessages(ctx.editorText, ctx.language, ctx.filePath, prompt)

    plugin.ai.chatToEditor(
        messages,
        function(success, content, error)
            isProcessing = false
            if ballId then
                plugin.floating.hidePanelLoading(ballId)
            end
            if success then
                plugin.logger.info("FloatingAI", "一次性→编辑器 完成, 共 " .. #content .. " 字符")
                if ballId then
                    plugin.floating.updatePanelTitle(ballId, "AI 代码生成 - 完成")
                end
                showTip("AI 代码已生成到编辑器")
            else
                plugin.logger.warn("FloatingAI", "一次性→编辑器 失败: " .. (error or "未知错误"))
                if ballId then
                    plugin.floating.updatePanelTitle(ballId, "AI 代码生成 - 失败")
                end
                showTip("AI 生成失败: " .. (error or "未知错误"))
            end
        end
    )
end

-- ============================================================
-- 统一入口：根据输出模式调用不同的生成方法
-- ============================================================
function generateCode(prompt)
    plugin.logger.debug("FloatingAI", "generateCode 入口, prompt=" .. tostring(prompt):sub(1, 100))
    if isProcessing then
        plugin.logger.warn("FloatingAI", "generateCode: 正在处理中，忽略")
        if ballId then
            plugin.floating.sendToWeb(ballId, '{"type":"loading","show":false}')
            plugin.floating.sendToWeb(ballId, '{"type":"status","text":"正在处理中，请稍候..."}')
        end
        showTip("正在处理中，请稍候...")
        return
    end

    if not plugin.ai.isAvailable() then
        plugin.logger.warn("FloatingAI", "generateCode: AI 服务不可用")
        if ballId then
            plugin.floating.sendToWeb(ballId, '{"type":"loading","show":false}')
            plugin.floating.sendToWeb(ballId, '{"type":"status","text":"AI 服务未配置"}')
        end
        showTip("AI 服务未配置，请在设置中配置 AI 提供商和 API Key")
        return
    end

    local ctx = getEditorContext()
    if not ctx then
        plugin.logger.warn("FloatingAI", "generateCode: 获取编辑器上下文失败")
        if ballId then
            plugin.floating.sendToWeb(ballId, '{"type":"loading","show":false}')
            plugin.floating.sendToWeb(ballId, '{"type":"status","text":"无法获取编辑器上下文"}')
        end
        return
    end

    local aiConfig = plugin.ai.getConfig()
    local provider = aiConfig["provider"] or "未知"
    local model = aiConfig["model"] or "未知"
    plugin.logger.info("FloatingAI", string.format(
        "开始生成 | 模式: %s | 文件: %s | 语言: %s | 内容: %d 字符 | 提示词: %s | 提供商: %s | 模型: %s",
        outputMode, ctx.filePath, ctx.language, #ctx.editorText, prompt, provider, model))

    if outputMode == "editor_stream" then
        generateCodeStreamToEditor(prompt, ctx)
    elseif outputMode == "editor_once" then
        generateCodeToEditor(prompt, ctx)
    else
        -- 面板流式
        if plugin.settings.isStreamEnabled() then
            generateCodeStream(prompt, ctx)
        else
            -- 非流式时用一次性请求
            local messages = buildMessages(ctx.editorText, ctx.language, ctx.filePath, prompt)
            isProcessing = true
            if ballId then
                plugin.floating.showPanelLoading(ballId)
                plugin.floating.updatePanelTitle(ballId, "AI 正在生成...")
            end
            plugin.ai.chatAsync(messages, function(success, content, error, model, tokens, reasoningContent)
                isProcessing = false
                if ballId then
                    plugin.floating.hidePanelLoading(ballId)
                    plugin.floating.updatePanelTitle(ballId, "AI 代码生成")
                end
                if success and content and content ~= "" then
                    local code = extractCode(content)
                    if code and code ~= "" then
                        plugin.editor.insertText("\n" .. code .. "\n")
                    end
                    plugin.logger.info("FloatingAI", "生成完成, tokens: " .. tostring(tokens))
                    showTip("AI 代码已生成")
                else
                    plugin.logger.warn("FloatingAI", "生成失败: " .. (error or "未知错误"))
                    showTip("AI 生成失败: " .. (error or "未知错误"))
                end
            end)
        end
    end
end

-- ============================================================
-- 创建悬浮球
-- ============================================================
local function createFloatingBall()
    if ballId then
        return
    end

    if not plugin.system.canDrawOverlays() then
        plugin.logger.warn("FloatingAI", "悬浮窗权限未授予，跳过创建")
        showTip("需要悬浮窗权限，请在设置中手动开启后重新启用插件")
        return
    end

    local screenW = plugin.system.getScreenWidth()
    local screenH = plugin.system.getScreenHeight()
    if not screenW or screenW <= 0 then
        screenW = 1080
    end
    if not screenH or screenH <= 0 then
        screenH = 1920
    end

    -- 创建悬浮球，点击时加载自定义 WebUI 面板（web/panel.html）
    ballId = plugin.floating.createBall(
        screenW - 80, screenH // 2,
        "AI代码生成",
        "AI",
        function(id)
            -- 点击悬浮球回调：加载自定义 WebUI 面板
            -- 面板由 web/panel.html 渲染，通过 LXC.callLua 与 Lua 双向通信
            -- 也可改用 plugin.floating.showPanel(id, title, hint) 加载内置默认模板
            local ok = plugin.floating.showPanelWebUI(id, "AI 代码生成", "panel.html")
            if not ok then
                -- 自定义模板加载失败时回退到默认模板
                plugin.logger.warn("FloatingAI", "自定义面板加载失败，回退到默认模板")
                plugin.floating.showPanel(id, "AI 代码生成", "输入提示词，如：添加一个排序函数")
            end
            plugin.floating.requestFocus(id)
        end,
        nil  -- onSubmit 已废弃，改用 onFloatingPanelSubmit 全局函数
    )

    if ballId then
        plugin.logger.info("FloatingAI", "悬浮球已创建, ID: " .. ballId)
        showTip("AI 悬浮球已创建")

        -- 示例：自定义悬浮球样式
        -- plugin.floating.updateBallColor(ballId, "#4CAF50")
        -- plugin.floating.updateBallTextColor(ballId, "#FFFFFF")
        -- plugin.floating.updateBallSize(ballId, 48)
        -- plugin.floating.updateBallText(ballId, "AI")

        -- 示例：控制手柄显示
        -- plugin.floating.setResizeHandleEnabled(ballId, true)

        -- 示例：注册悬浮球移动事件
        -- plugin.floating.setOnBallMoved(ballId, function(x, y)
        --     plugin.logger.info("FloatingAI", "悬浮球移动到: " .. x .. "," .. y)
        -- end)

        -- 示例：注册面板大小调节事件
        -- plugin.floating.setOnPanelResized(ballId, function(w, h)
        --     plugin.logger.info("FloatingAI", "面板大小: " .. w .. "x" .. h)
        -- end)
    else
        plugin.logger.warn("FloatingAI", "悬浮球创建失败")
    end
end

-- ============================================================
-- 清理悬浮球
-- ============================================================
local function destroyFloatingBall()
    if ballId then
        plugin.floating.clearFocus(ballId)
        plugin.floating.removeAll()
        ballId = nil
    end
end

-- ============================================================
-- 事件监听
-- ============================================================

-- 本插件被禁用时清理
plugin.events.register("onPluginDisabled", function(disabledPluginId)
    if disabledPluginId ~= "floating_ai_demo" then return end
    plugin.ai.cancelAll()
    destroyFloatingBall()
end)

-- 应用进入后台时隐藏面板
plugin.events.register("onAppPause", function()
    if ballId then
        plugin.floating.clearFocus(ballId)
        plugin.floating.hidePanel(ballId)
    end
end)

-- 应用恢复前台时
plugin.events.register("onAppResume", function()
    -- 面板需要重新显示后才能请求焦点
end)

-- 应用启动后，此时 Activity 已就绪，可请求权限
plugin.events.register("onAppStart", function()
    if not plugin.system.canDrawOverlays() then
        plugin.system.requestOverlayPermission(function(granted, perm, err)
            if granted then
                createFloatingBall()
            else
                plugin.logger.warn("FloatingAI", "用户拒绝了悬浮窗权限")
            end
        end)
    else
        createFloatingBall()
    end
end)

-- ============================================================
-- 初始化：直接尝试创建（权限已授予时立即生效）
-- ============================================================
createFloatingBall()

plugin.logger.info("FloatingAI", "插件已加载")