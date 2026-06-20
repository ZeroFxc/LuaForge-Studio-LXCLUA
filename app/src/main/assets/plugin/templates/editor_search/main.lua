-- ============================================================
-- 编辑器搜索增强插件
-- 在编辑器顶部 QuickAction 工具栏添加「搜索」按钮
-- 点击按钮弹出搜索面板，支持搜索关键词和替换
-- 输入格式：第一行搜索词，第二行替换词（可选）
-- 搜索结果以列表形式显示：行号 + 上下文片段
-- ============================================================

local panelAnchorId = nil

-- 搜索状态
local searchState = {
    contentLines = {},   -- 编辑器内容按行缓存
    results = {},        -- 搜索结果 {line, col, matchLen, snippet}
    currentIndex = 0,    -- 当前结果索引
    query = "",          -- 当前搜索词
    replaceText = "",    -- 替换文本
}

-- 截取上下文片段（匹配位置前后各取 contextLen 个字符）
local function getSnippet(line, pos, matchLen, contextLen)
    contextLen = contextLen or 30
    local start = math.max(1, pos - contextLen)
    local finish = math.min(#line, pos + matchLen + contextLen)
    local pre = ""
    local post = ""
    if start > 1 then pre = "..." end
    if finish < #line then post = "..." end
    return pre .. line:sub(start, finish) .. post
end

-- 解析输入：第一行搜索词，第二行替换词
local function parseInput(text)
    if not text or text == "" then return "", "" end
    local query, replace = "", ""
    local lineNum = 0
    for part in text:gmatch("[^\n]*") do
        lineNum = lineNum + 1
        if lineNum == 1 then query = part
        elseif lineNum == 2 then replace = part
        else break end
    end
    return query, replace
end

-- 收集搜索结果
local function collectResults(query)
    searchState.results = {}
    searchState.currentIndex = 0
    if query == "" then return end
    local queryLower = query:lower()
    for lineIdx, line in ipairs(searchState.contentLines) do
        local scanPos = 1
        local lineLower = line:lower()
        while true do
            local pos = lineLower:find(queryLower, scanPos, true)
            if not pos then break end
            local snippet = getSnippet(line, pos, #query, 40)
            table.insert(searchState.results, {
                line = lineIdx,
                col = pos,
                matchLen = #query,
                snippet = snippet
            })
            scanPos = pos + 1
        end
    end
end

-- 跳转到指定结果
local function gotoResult(index)
    local total = #searchState.results
    if total == 0 then return end
    if index < 1 then index = total
    elseif index > total then index = 1 end
    searchState.currentIndex = index
    local r = searchState.results[index]
    plugin.editor.gotoLine(r.line - 1)
    return index
end

-- 替换当前匹配
local function replaceCurrent()
    if #searchState.results == 0 or searchState.currentIndex == 0 then
        return "无匹配可替换"
    end
    if searchState.replaceText == "" then
        return "请先输入替换词（第二行）"
    end
    local r = searchState.results[searchState.currentIndex]
    local line = searchState.contentLines[r.line]
    local newLine = line:sub(1, r.col - 1) .. searchState.replaceText .. line:sub(r.col + r.matchLen)
    plugin.editor.editLine(r.line - 1, newLine)
    searchState.contentLines[r.line] = newLine
    collectResults(searchState.query)
    return "已替换第 " .. r.line .. " 行"
end

-- 替换全部
local function replaceAll()
    if #searchState.results == 0 then
        return "无匹配可替换"
    end
    if searchState.replaceText == "" then
        return "请先输入替换词（第二行）"
    end
    -- 从后往前替换避免行号偏移
    local sorted = {}
    for _, r in ipairs(searchState.results) do table.insert(sorted, r) end
    table.sort(sorted, function(a, b) return a.line > b.line end)
    local seen, count = {}, 0
    for _, r in ipairs(sorted) do
        local key = r.line .. "_" .. r.col
        if not seen[key] then
            seen[key] = true
            local line = searchState.contentLines[r.line]
            local newLine = line:sub(1, r.col - 1) .. searchState.replaceText .. line:sub(r.col + r.matchLen)
            plugin.editor.editLine(r.line - 1, newLine)
            searchState.contentLines[r.line] = newLine
            count = count + 1
        end
    end
    searchState.results = {}
    searchState.currentIndex = 0
    return "已替换 " .. count .. " 处匹配"
end

-- 构建搜索结果列表（带行号和上下文）
local function buildResultList()
    local lines = {}
    local total = #searchState.results
    if total == 0 then return "" end

    -- 限制显示数量，避免输出过长
    local maxShow = 50
    local showCount = math.min(total, maxShow)

    for i = 1, showCount do
        local r = searchState.results[i]
        local marker = ""
        if i == searchState.currentIndex then
            marker = " >>> "
        else
            marker = string.format(" %3d ", i)
        end
        table.insert(lines, marker .. "L" .. string.format("%4d", r.line) .. ": " .. r.snippet)
    end

    if total > maxShow then
        table.insert(lines, "  ... 还有 " .. (total - maxShow) .. " 条结果未显示")
    end

    return table.concat(lines, "\n")
end

-- 显示结果到面板
local function showResult(msg)
    plugin.floating.showStreamOutput(panelAnchorId)
    plugin.floating.clearStreamOutput(panelAnchorId)
    plugin.floating.appendStreamContent(panelAnchorId, msg)
end

-- 刷新搜索结果展示
local function refreshResultDisplay()
    if #searchState.results == 0 then
        if searchState.query ~= "" then
            showResult("未找到 \"" .. searchState.query .. "\"")
        else
            showResult("请输入搜索关键词")
        end
        return
    end

    local header = "找到 " .. #searchState.results .. " 处匹配"
    if searchState.replaceText ~= "" then
        header = header .. "  |  替换: \"" .. searchState.replaceText .. "\""
    end
    header = header .. "  |  ← → 导航  |  替换/全部替换\n"
    header = header .. string.rep("-", 50) .. "\n"

    local list = buildResultList()
    showResult(header .. list)
end

-- 打开搜索面板
local function openSearchPanel()
    local content = plugin.editor.getText() or ""
    if content == "" then
        plugin.ui.showMessage("搜索", "编辑器为空")
        return
    end
    searchState.contentLines = {}
    for line in content:gmatch("[^\n]*") do
        table.insert(searchState.contentLines, line)
    end
    plugin.floating.showPanel(panelAnchorId, "搜索和替换",
        "第一行：搜索关键词\n第二行：替换文本（可选）")
end

-- 面板提交回调
local function onPanelSubmit(ballId, text)
    searchState.query, searchState.replaceText = parseInput(text)
    if searchState.query == "" then
        showResult("请输入搜索关键词")
        return
    end
    collectResults(searchState.query)
    if #searchState.results == 0 then
        showResult("未找到 \"" .. searchState.query .. "\"")
    else
        gotoResult(1)
        refreshResultDisplay()
    end
end

-- 上一个匹配
local function prevMatch()
    if #searchState.results == 0 then
        showResult("无搜索结果")
        return
    end
    gotoResult(searchState.currentIndex - 1)
    refreshResultDisplay()
end

-- 下一个匹配
local function nextMatch()
    if #searchState.results == 0 then
        showResult("无搜索结果")
        return
    end
    gotoResult(searchState.currentIndex + 1)
    refreshResultDisplay()
end

-- 替换当前
local function doReplaceCurrent()
    local msg = replaceCurrent()
    showResult(msg)
    if #searchState.results > 0 then
        refreshResultDisplay()
    end
end

-- 替换全部
local function doReplaceAll()
    local msg = replaceAll()
    showResult(msg)
end

-- 插件加载
function onLoad()
    -- 创建隐藏锚点（只用于承载面板，不显示球）
    panelAnchorId = plugin.floating.createBall(
        0, 0, "搜索锚点", "S",
        function(id) end,    -- 点击回调（不会被触发，球已隐藏）
        onPanelSubmit
    )
    if panelAnchorId then
        plugin.floating.hideBall(panelAnchorId)
        plugin.floating.setResizeHandleEnabled(panelAnchorId, true)
        plugin.floating.setPanelWidth(panelAnchorId, 480)
    end

    -- 在 QuickAction 工具栏添加「搜索」按钮
    plugin.menu.addQuickAction("search", "搜索", function()
        if panelAnchorId then openSearchPanel() end
    end)

    -- 导航按钮：上一个 / 下一个
    plugin.menu.addQuickAction("prev", "<", function() prevMatch() end)
    plugin.menu.addQuickAction("next", ">", function() nextMatch() end)

    -- 替换按钮
    plugin.menu.addQuickAction("replace", "替换", function() doReplaceCurrent() end)
    plugin.menu.addQuickAction("replace_all", "全部替换", function() doReplaceAll() end)

    plugin.logger.info("EditorSearch", "编辑器搜索增强插件已加载（工具栏模式）")
end

-- 插件卸载
function onUnload()
    plugin.menu.removeQuickAction("search")
    plugin.menu.removeQuickAction("prev")
    plugin.menu.removeQuickAction("next")
    plugin.menu.removeQuickAction("replace")
    plugin.menu.removeQuickAction("replace_all")
    if panelAnchorId then
        plugin.floating.removeBall(panelAnchorId)
    end
    plugin.logger.info("EditorSearch", "编辑器搜索增强插件已卸载")
end