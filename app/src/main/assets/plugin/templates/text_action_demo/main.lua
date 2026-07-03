-- ============================================================
-- 文本操作工具栏扩展插件
-- 演示如何向长按浮动工具栏动态注册自定义按钮
-- ============================================================

-- ============================================================
-- 图标常量参考表
-- 内置图标名称（可直接用于 registerTextActionButton 的 icon 参数）：
--   "SELECT_ALL"  全选
--   "COPY"        复制
--   "PASTE"       粘贴
--   "CUT"         剪切
--   "LONG_SELECT" 长选
--   "UNDO"        撤销
--   "REDO"        重做
--   "SEARCH"      搜索
--   "DELETE"      删除
--   "FORMAT"      格式化
--   "CODE"        代码
--   "SAVE"        保存
--   "MORE"        更多
--   "REFRESH"     刷新
--   "SHARE"       分享
--
-- 也可以使用自定义图片路径（如 "/sdcard/my_icon.png"）
-- 图片会自动裁剪到不超过 256px
-- ============================================================

-- 获取所有可用图标常量
local function printAvailableIcons()
    local icons = plugin.editor.getTextActionIcons()
    local names = {}
    for name, _ in pairs(icons) do
        table.insert(names, name)
    end
    table.sort(names)
    plugin.logger.info("TextActionDemo", "可用图标: " .. table.concat(names, ", "))
end

-- 按钮点击处理（通过事件监听）
local function onButtonClick(buttonId, selectedText)
    plugin.logger.info("TextActionDemo", "按钮被点击: " .. buttonId .. ", 选中文本: " .. selectedText)

    if buttonId == "my_undo" then
        plugin.editor.undo()
        plugin.ui.showMessage("撤销", "已撤销上一步操作")
    elseif buttonId == "my_redo" then
        plugin.editor.redo()
        plugin.ui.showMessage("重做", "已重做操作")
    elseif buttonId == "my_search" then
        local text = selectedText or ""
        if text ~= "" then
            plugin.editor.findText(text, false, false)
            plugin.ui.showMessage("搜索", "已搜索: " .. text)
        else
            plugin.ui.showMessage("搜索", "请先选中要搜索的文本")
        end
    elseif buttonId == "my_share" then
        local text = plugin.editor.getText() or "无内容"
        plugin.ui.showMessage("分享", "当前文件内容长度: " .. #text .. " 字符")
    elseif buttonId == "my_custom" then
        plugin.ui.showMessage("自定义按钮", "你点击了自定义按钮！")
    end
end

-- 文本操作窗口显示时的回调
local function onWindowShown(selectedText)
    plugin.logger.info("TextActionDemo", "文本操作窗口显示, 选中: " .. selectedText)
end

-- 插件加载
function onLoad()
    -- 打印可用图标列表
    printAvailableIcons()

    -- 注册自定义按钮到长按浮动工具栏
    -- 参数: (按钮ID, 图标名或路径, 标签)
    plugin.editor.registerTextActionButton("my_undo", "UNDO", "撤销")
    plugin.editor.registerTextActionButton("my_redo", "REDO", "重做")
    plugin.editor.registerTextActionButton("my_search", "SEARCH", "搜索")
    plugin.editor.registerTextActionButton("my_share", "SHARE", "分享")
    plugin.editor.registerTextActionButton("my_custom", "CODE", "自定义")

    -- 监听按钮点击事件
    plugin.events.on("onTextActionWindowButtonClick", function(buttonId, selectedText)
        onButtonClick(buttonId, selectedText)
    end)

    -- 监听窗口显示事件（可选）
    plugin.events.on("onTextActionWindowShown", function(selectedText)
        onWindowShown(selectedText)
    end)

    plugin.logger.info("TextActionDemo", "文本操作工具栏扩展插件已加载")
    plugin.logger.info("TextActionDemo", "已注册 " .. #plugin.editor.getTextActionButtons() .. " 个自定义按钮")
end

-- 插件卸载
-- 注意：按钮会在插件卸载时自动清除，无需手动注销
-- 但如果需要在插件运行期间动态移除按钮，可以使用 unregisterTextActionButton
function onUnload()
    plugin.logger.info("TextActionDemo", "文本操作工具栏扩展插件已卸载（按钮已自动清除）")
end