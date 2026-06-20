-- ============================================================
-- 编辑器 MCP 服务插件
-- 将编辑器操作工具注册为一个 MCP 服务 "editor_service"
-- 可通过 plugin.mcp.enableService/disableService 整体开关
-- 也可对单个工具 enableTool/disableTool 精细控制
-- 工具列表:
--   editor_get_content    - 获取编辑器全部内容
--   editor_set_content    - 设置编辑器全部内容（重写整个文件）
--   editor_clear_all      - 清空编辑器全部内容
--   editor_get_line_count - 获取总行数
--   editor_get_line       - 获取单行内容
--   editor_get_lines      - 获取指定行范围
--   editor_edit_line      - 编辑替换指定行
--   editor_insert_line    - 在指定行前插入
--   editor_delete_line    - 删除指定行
--   editor_delete_lines   - 删除指定行范围
--   editor_replace_lines  - 替换行范围
--   editor_append_text    - 在末尾追加文本
--   editor_goto_line      - 跳转到指定行
--   editor_get_cursor     - 获取光标位置
--   editor_get_info       - 获取编辑器信息
--   editor_search         - 在编辑器中搜索文本
-- ============================================================

-- 工具处理函数

-- 获取指定行范围的上下文（前后各带 radius 行），用于写操作后返回局部变更给 AI
-- 用 getText() 读取 state.content（同步更新），避免 getLines() 读到 UI 线程未处理的旧数据
local function getContextAround(startLine, endLine, radius)
    radius = radius or 2
    local text = plugin.editor.getText()
    if text == nil or text == "" then return "" end
    local from = math.max(0, startLine - radius)
    local to = endLine + radius
    local result = {}
    local lineIdx = 0
    for lineStr in text:gmatch("[^\n]*") do
        if lineIdx >= from and lineIdx <= to then
            table.insert(result, string.format("  %d: %s", lineIdx, lineStr))
        end
        lineIdx = lineIdx + 1
    end
    return table.concat(result, "\n")
end

local function handle_get_content(args)
    local info = plugin.editor.getActiveEditorInfo()
    return tostring(info["content"] or "")
end

local function handle_get_lines(args)
    local startLine = tonumber(args["startLine"] or args["start_line"]) or 0
    local endLine = tonumber(args["endLine"] or args["end_line"]) or 0
    local lines = plugin.editor.getLines(startLine, endLine)
    return lines or "无效的行范围"
end

local function handle_edit_line(args)
    local line = tonumber(args["line"]) or -1
    local text = tostring(args["text"] or "")
    plugin.editor.editLine(line, text)
    return "第 " .. line .. " 行已更新\n修改区域上下文：\n" .. getContextAround(line, line, 3)
end

local function handle_insert_line(args)
    local line = tonumber(args["line"]) or 0
    local text = tostring(args["text"] or "")
    plugin.editor.insertLine(line, text)
    return "已在第 " .. line .. " 行前插入\n修改区域上下文：\n" .. getContextAround(line, line + 1, 3)
end

local function handle_delete_line(args)
    local line = tonumber(args["line"]) or -1
    plugin.editor.deleteLine(line)
    return "第 " .. line .. " 行已删除\n修改区域上下文：\n" .. getContextAround(line, line, 3)
end

local function handle_replace_lines(args)
    local startLine = tonumber(args["startLine"] or args["start_line"]) or 0
    local endLine = tonumber(args["endLine"] or args["end_line"]) or 0
    local text = tostring(args["text"] or "")
    plugin.editor.replaceLines(startLine, endLine, text)
    return "第 " .. startLine .. " 到 " .. endLine .. " 行已替换\n修改区域上下文：\n" .. getContextAround(startLine, endLine, 2)
end

local function handle_goto_line(args)
    local line = tonumber(args["line"]) or 0
    plugin.editor.gotoLine(line)
    return "已跳转到第 " .. line .. " 行"
end

local function handle_get_cursor(args)
    local pos = plugin.editor.getCursorPosition()
    if pos then
        return "光标在第 " .. pos[1] .. " 行，第 " .. pos[2] .. " 列"
    end
    return "无法获取光标位置"
end

local function handle_get_info(args)
    local info = plugin.editor.getActiveEditorInfo()
    local lines = {}
    for k, v in pairs(info) do
        table.insert(lines, k .. ": " .. tostring(v))
    end
    return table.concat(lines, "\n")
end

local function handle_search(args)
    local query = tostring(args["query"] or "")
    local content = tostring(plugin.editor.getText() or "")
    if query == "" then return "搜索内容为空" end
    local results = {}
    local lineNum = 0
    for line in content:gmatch("[^\n]*") do
        lineNum = lineNum + 1
        local pos = line:find(query, 1, true)
        if pos then
            local snippet = line:sub(math.max(1, pos - 20), math.min(#line, pos + #query + 20))
            table.insert(results, "第 " .. lineNum .. " 行: ..." .. snippet .. "...")
        end
    end
    if #results == 0 then
        return "未找到 \"" .. query .. "\""
    end
    if #results > 20 then
        return "找到 " .. #results .. " 处匹配，前 20 项:\n" .. table.concat(results, "\n", 1, 20)
    end
    return "找到 " .. #results .. " 处匹配:\n" .. table.concat(results, "\n")
end

-- ===== 新增工具处理函数 =====

local function handle_set_content(args)
    local text = tostring(args["text"] or "")
    plugin.editor.setText(text)
    if text == "" then
        return "编辑器已清空（0 字符）"
    end
    -- 返回前几行让 AI 确认内容已写入
    return "编辑器内容已更新，共 " .. #text .. " 字符\n文件开头预览：\n" .. getContextAround(0, 2, 0)
end

local function handle_clear_all(args)
    plugin.editor.setText("")
    return "编辑器已清空（0 字符）"
end

local function handle_get_line_count(args)
    local count = plugin.editor.getLineCount()
    return "编辑器共 " .. count .. " 行"
end

local function handle_get_line(args)
    local line = tonumber(args["line"]) or 0
    local content = plugin.editor.getLine(line)
    if content == nil then return "行号 " .. line .. " 无效" end
    return content
end

local function handle_delete_lines(args)
    local startLine = tonumber(args["startLine"] or args["start_line"]) or 0
    local endLine = tonumber(args["endLine"] or args["end_line"]) or 0
    if startLine > endLine then
        return "起始行号不能大于结束行号: " .. startLine .. " > " .. endLine
    end
    local count = plugin.editor.getLineCount()
    if endLine >= count then
        return "结束行号 " .. endLine .. " 超出范围，编辑器共 " .. count .. " 行"
    end
    -- 用 replaceLines 替换为空串实现删除
    plugin.editor.replaceLines(startLine, endLine, "")
    local deleted = endLine - startLine + 1
    return "已删除第 " .. startLine .. " 到 " .. endLine .. " 行，共 " .. deleted .. " 行\n修改区域上下文：\n" .. getContextAround(startLine, startLine, 3)
end

local function handle_append_text(args)
    local text = tostring(args["text"] or "")
    local count = plugin.editor.getLineCount()
    plugin.editor.insertLine(count, text)
    return "已在末尾追加文本，共 " .. #text .. " 字符\n末尾上下文：\n" .. getContextAround(count - 1, count, 2)
end

-- 工具定义列表（不含 handler，handler 在 registerService 时传入）
local toolDefs = {
    {
        name = "editor_get_content",
        description = "获取当前编辑器全部文本内容，返回完整文本。用于了解当前文件的全貌",
        inputSchema = { type = "object", properties = {}, required = {} },
        handler = handle_get_content
    },
    {
        name = "editor_get_lines",
        description = "获取编辑器指定行范围的内容（包含 startLine 和 endLine）。适合只查看文件的一部分",
        inputSchema = {
            type = "object",
            properties = {
                startLine = { type = "integer", description = "起始行号（从 0 开始，包含）" },
                endLine = { type = "integer", description = "结束行号（从 0 开始，包含）" }
            },
            required = { "startLine", "endLine" }
        },
        handler = handle_get_lines
    },
    {
        name = "editor_edit_line",
        description = "编辑替换编辑器指定行的内容，会将该行完整替换为 text。只修改单行",
        inputSchema = {
            type = "object",
            properties = {
                line = { type = "integer", description = "行号（从 0 开始）" },
                text = { type = "string", description = "替换的新内容（不含换行符）" }
            },
            required = { "line", "text" }
        },
        handler = handle_edit_line
    },
    {
        name = "editor_insert_line",
        description = "在编辑器指定行之前插入新行，原指定行及之后的行都向后移动",
        inputSchema = {
            type = "object",
            properties = {
                line = { type = "integer", description = "行号（从 0 开始），在这行之前插入" },
                text = { type = "string", description = "插入的内容（不含换行符）" }
            },
            required = { "line", "text" }
        },
        handler = handle_insert_line
    },
    {
        name = "editor_delete_line",
        description = "删除编辑器指定单行。如需删除多行，请使用 editor_delete_lines",
        inputSchema = {
            type = "object",
            properties = { line = { type = "integer", description = "行号（从 0 开始）" } },
            required = { "line" }
        },
        handler = handle_delete_line
    },
    {
        name = "editor_replace_lines",
        description = "替换编辑器指定行范围的内容（从 startLine 到 endLine，包含两端）。text 可包含换行符以插入多行",
        inputSchema = {
            type = "object",
            properties = {
                startLine = { type = "integer", description = "起始行号（从 0 开始，包含）" },
                endLine = { type = "integer", description = "结束行号（从 0 开始，包含）" },
                text = { type = "string", description = "替换的新内容（可含 \\n 换行符）" }
            },
            required = { "startLine", "endLine", "text" }
        },
        handler = handle_replace_lines
    },
    {
        name = "editor_goto_line",
        description = "跳转到编辑器指定行，光标会移动到该行",
        inputSchema = {
            type = "object",
            properties = { line = { type = "integer", description = "目标行号（从 0 开始）" } },
            required = { "line" }
        },
        handler = handle_goto_line
    },
    {
        name = "editor_get_cursor",
        description = "获取编辑器当前光标位置，返回行号和列号（均从 0 开始）",
        inputSchema = { type = "object", properties = {}, required = {} },
        handler = handle_get_cursor
    },
    {
        name = "editor_get_info",
        description = "获取编辑器状态信息：文件路径、语言类型、是否已修改、光标位置等",
        inputSchema = { type = "object", properties = {}, required = {} },
        handler = handle_get_info
    },
    {
        name = "editor_search",
        description = "在当前编辑器文件中搜索指定文本，返回所有匹配的行号和上下文",
        inputSchema = {
            type = "object",
            properties = { query = { type = "string", description = "搜索关键词" } },
            required = { "query" }
        },
        handler = handle_search
    },
    {
        name = "editor_set_content",
        description = "设置编辑器全部内容，会完全替换当前所有文本。用于重写整个文件，或先清空再写入新内容",
        inputSchema = {
            type = "object",
            properties = { text = { type = "string", description = "新的完整文本内容" } },
            required = { "text" }
        },
        handler = handle_set_content
    },
    {
        name = "editor_clear_all",
        description = "清空编辑器全部内容，相当于删除所有文本",
        inputSchema = { type = "object", properties = {}, required = {} },
        handler = handle_clear_all
    },
    {
        name = "editor_get_line_count",
        description = "获取编辑器当前总行数",
        inputSchema = { type = "object", properties = {}, required = {} },
        handler = handle_get_line_count
    },
    {
        name = "editor_get_line",
        description = "获取编辑器指定单行的内容，比 get_lines 更轻量，适合只查看某一行",
        inputSchema = {
            type = "object",
            properties = { line = { type = "integer", description = "行号（从 0 开始）" } },
            required = { "line" }
        },
        handler = handle_get_line
    },
    {
        name = "editor_delete_lines",
        description = "删除编辑器指定行范围（从 startLine 到 endLine，包含两端）。一次性删除多行，比逐行删除高效",
        inputSchema = {
            type = "object",
            properties = {
                startLine = { type = "integer", description = "起始行号（从 0 开始，包含）" },
                endLine = { type = "integer", description = "结束行号（从 0 开始，包含）" }
            },
            required = { "startLine", "endLine" }
        },
        handler = handle_delete_lines
    },
    {
        name = "editor_append_text",
        description = "在编辑器末尾追加文本。适合在文件最后添加新内容，不用关心具体行号",
        inputSchema = {
            type = "object",
            properties = { text = { type = "string", description = "要追加的文本内容" } },
            required = { "text" }
        },
        handler = handle_append_text
    }
}

-- 注册所有工具为一个 MCP 服务
local function registerService()
    local count = plugin.mcp.registerService("editor_service", "编辑器操作", toolDefs)
    plugin.logger.info("EditorMCP", "已注册 MCP 服务 [editor_service]，包含 " .. count .. " 个工具")

    -- 验证注册结果：直接用 registerService 返回值对比，不遍历 Java List
    local expected = #toolDefs
    if count ~= expected then
        plugin.logger.error("EditorMCP", "注册数量不匹配: 期望 " .. expected .. " 个, 实际 " .. count .. " 个", "")
    else
        plugin.logger.info("EditorMCP", "全部 " .. count .. " 个工具注册成功")
    end

    -- 打印服务状态
    local status = plugin.mcp.getServiceStatus("editor_service")
    if status then
        plugin.logger.info("EditorMCP", "服务状态: enabled=" .. tostring(status["enabled"])
            .. ", toolCount=" .. tostring(status["toolCount"]))
    end
end

-- 插件加载入口
function onLoad()
    registerService()
    plugin.logger.info("EditorMCP", "编辑器 MCP 服务插件已加载")
end

-- 插件卸载入口
function onUnload()
    plugin.mcp.unregisterService("editor_service")
    plugin.logger.info("EditorMCP", "编辑器 MCP 服务插件已卸载")
end