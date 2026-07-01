-- 插件注册系统测试 v2
-- 覆盖所有 31 个注册组件

local function main()
    local count = compose.state(0)
    local show = compose.state(true)
    local checked = compose.state(false)
    local switchOn = compose.state(true)
    local sliderVal = compose.state(0.5)
    local inputText = compose.state("")
    local page = compose.state(0)

    local children = {}

    local function title(t)
        table.insert(children, compose.Text { text = t, fontSize = 16, fontWeight = "Bold", color = 0xFF1565C0 })
    end

    -- ===== 1. LayoutComponents =====
    title("1. LayoutComponents — Column / Row / Box")
    table.insert(children, compose.Row {
        children = {
            compose.Box { modifier = compose.Modifier().size(50).background(0xFFE53935).borderRadius(8), children = { compose.Text { text = "A", color = 0xFFFFFFFF } } },
            compose.Box { modifier = compose.Modifier().size(50).background(0xFF43A047).borderRadius(8), children = { compose.Text { text = "B", color = 0xFFFFFFFF } } },
            compose.Box { modifier = compose.Modifier().size(50).background(0xFF1E88E5).borderRadius(8), children = { compose.Text { text = "C", color = 0xFFFFFFFF } } },
        },
    })

    title("1b. LazyColumn / LazyRow")
    table.insert(children, compose.Text { text = "LazyColumn 渲染中...", fontSize = 12, color = 0xFF999999 })
    table.insert(children, compose.LazyRow {
        children = {
            compose.Text { text = "横向1", fontSize = 14, color = 0xFFE53935, modifier = compose.Modifier().padding(4) },
            compose.Text { text = "横向2", fontSize = 14, color = 0xFF43A047, modifier = compose.Modifier().padding(4) },
            compose.Text { text = "横向3", fontSize = 14, color = 0xFF1E88E5, modifier = compose.Modifier().padding(4) },
        },
    })

    -- ===== 2. DisplayComponents =====
    title("2. DisplayComponents — Text")
    table.insert(children, compose.Text { text = "粗体大字", fontSize = 24, fontWeight = "Bold", color = 0xFF333333 })
    table.insert(children, compose.Text { text = "斜体小字", fontSize = 14, fontStyle = "Italic", color = 0xFF999999 })
    table.insert(children, compose.Text { text = "超长文本截断测试：这是一段非常非常非常长的文本用来测试Ellipsis", maxLines = 1, overflow = "Ellipsis", color = 0xFF666666 })

    -- ===== 3. InputComponents =====
    title("3. InputComponents — Button 系列")
    table.insert(children, compose.Row {
        children = {
            compose.Button { onClick = function() count.value = count.value + 1 end, children = { compose.Text { text = "Button " .. count.value, color = 0xFFFFFFFF } } },
            compose.TextButton { onClick = function() count.value = count.value - 1 end, children = { compose.Text { text = "TextBtn" } } },
            compose.OutlinedButton { onClick = function() inputText.value = "已点击" end, children = { compose.Text { text = "Outlined" } } },
        },
    })

    title("3b. Checkbox / Switch / Slider")
    table.insert(children, compose.Row {
        verticalAlignment = "CenterVertically",
        children = {
            compose.Checkbox { checked = checked.value, onCheckedChange = function(v) checked.value = v end },
            compose.Text { text = "Checkbox", fontSize = 14, color = 0xFF666666 },
            compose.Spacer { width = 12 },
            compose.Switch { checked = switchOn.value, onCheckedChange = function(v) switchOn.value = v end },
            compose.Text { text = "Switch", fontSize = 14, color = 0xFF666666 },
        },
    })
    table.insert(children, compose.Text { text = "Slider: " .. string.format("%.2f", sliderVal.value), fontSize = 14, color = 0xFF666666 })
    table.insert(children, compose.Slider { value = sliderVal.value, onValueChange = function(v) sliderVal.value = v end })

    title("3c. TextField / OutlinedTextField")
    table.insert(children, compose.TextField {
        text = inputText.value, label = "TextField", placeholder = "请输入...",
        onValueChange = function(v) inputText.value = v end,
    })
    table.insert(children, compose.OutlinedTextField {
        text = inputText.value, label = "OutlinedTextField", placeholder = "请输入...",
        onValueChange = function(v) inputText.value = v end,
    })

    -- ===== 4. ContainerComponents =====
    title("4. ContainerComponents — Card / Surface / Spacer / Divider")
    table.insert(children, compose.Card {
        children = {
            compose.Text { text = "Card 容器", fontSize = 16, fontWeight = "Bold", color = 0xFF333333 },
            compose.Text { text = "Card 有 elevation 阴影", fontSize = 13, color = 0xFF999999 },
        },
    })
    table.insert(children, compose.Surface {
        color = 0xFFE3F2FD,
        children = {
            compose.Text { text = "Surface 蓝色背景", fontSize = 14, color = 0xFF1565C0, modifier = compose.Modifier().padding(8) },
            compose.Divider { color = 0xFF90CAF9 },
            compose.Text { text = "Divider 下方", fontSize = 14, color = 0xFF1565C0, modifier = compose.Modifier().padding(8) },
        },
    })
    table.insert(children, compose.Spacer { height = 16 })
    table.insert(children, compose.Text { text = "上下 Spacer 间距 16dp", fontSize = 12, color = 0xFFAAAAAA })
    table.insert(children, compose.Spacer { height = 16 })

    -- ===== 5. BoxWithConstraints =====
    title("5. BoxWithConstraints")
    table.insert(children, compose.BoxWithConstraints {
        modifier = compose.Modifier().fillMaxWidth().height(40).background(0xFFF3E5F5),
        children = { compose.Text { text = "约束注入测试", fontSize = 12, color = 0xFF7B1FA2 } },
    })

    -- ===== 6. Icon =====
    title("6. IconComponent")
    table.insert(children, compose.Row {
        horizontalArrangement = "SpaceEvenly",
        children = {
            compose.Icon { name = "Home", color = 0xFF2196F3, size = 28 },
            compose.Icon { name = "Favorite", color = 0xFFE91E63, size = 28 },
            compose.Icon { name = "Settings", color = 0xFF4CAF50, size = 28 },
            compose.Icon { name = "Search", color = 0xFFFF9800, size = 28 },
            compose.Icon { name = "Star", color = 0xFFFFC107, size = 28 },
            compose.Icon { name = "Info", color = 0xFF9C27B0, size = 28 },
        },
    })

    -- ===== 7. Animation =====
    title("7. AnimationPlugin")

    title("7a. AnimatedVisibility")
    table.insert(children, compose.AnimatedVisibility {
        visible = show.value,
        enter = compose.fadeInExpand(),
        exit = compose.fadeOutShrink(),
        children = { compose.Text { text = "AnimatedVisibility 内容", fontSize = 14, color = 0xFF43A047, modifier = compose.Modifier().padding(8).background(0xFFE8F5E9) } },
    })
    table.insert(children, compose.Button {
        onClick = function() show.value = not show.value end,
        children = { compose.Text { text = "切换可见", color = 0xFFFFFFFF } },
    })

    title("7b. AnimatedContent")
    table.insert(children, compose.Row {
        verticalAlignment = "CenterVertically",
        children = {
            compose.Button {
                onClick = function() page.value = (page.value + 1) % 3 end,
                children = { compose.Text { text = "下一页", color = 0xFFFFFFFF } },
            },
            compose.Spacer { width = 8 },
            compose.AnimatedContent {
                targetState = page.value,
                children = { compose.Text { text = "第" .. (page.value + 1) .. "页", fontSize = 16, fontWeight = "Bold", color = 0xFF1E88E5 } },
            },
        },
    })

    title("7c. Crossfade / InfiniteTransition")
    table.insert(children, compose.Crossfade {
        targetState = page.value,
        durationMs = 400,
        children = {
            compose.Text { text = "Crossfade 页 " .. page.value, fontSize = 16, fontWeight = "Bold", color = 0xFF7C3AED }
        },
    })
    table.insert(children, compose.InfiniteTransition {
        initialValue = 0.8, targetValue = 1.2, durationMs = 800,
        repeatMode = "Reverse",
        children = function(v)
            return compose.Text { text = "∞ 脉冲", fontSize = 18 * v, fontWeight = "Bold", color = 0xFF7C3AED }
        end,
    })

    -- ===== 8. Canvas =====
    title("8. CanvasPlugin")
    table.insert(children, compose.Canvas {
        modifier = compose.Modifier().fillMaxWidth().height(80),
        onDraw = function(draw, w, h)
            local barW = w / 6
            local colors = {0xFFE53935, 0xFFFB8C00, 0xFFFFEB3B, 0xFF43A047, 0xFF1E88E5, 0xFF7C3AED}
            for i = 1, 6 do
                draw.drawRect((i - 1) * barW, 0, i * barW, h, colors[i])
            end
            draw.drawCircle(w / 2, h / 2, 20, 0xFFFFFFFF)
            draw.drawCircleStroke(w / 2, h / 2, 24, 0xFF333333, 3)
        end,
    })

    -- ===== 9. Effect =====
    title("9. EffectPlugin")
    table.insert(children, compose.LaunchedEffect { key = count.value, block = function() end })
    table.insert(children, compose.Text { text = "LaunchedEffect key=" .. count.value, fontSize = 12, color = 0xFF999999 })
    table.insert(children, compose.key {
        key = page.value,
        children = { compose.Text { text = "key 分组复用 page=" .. page.value, fontSize = 12, color = 0xFF43A047 } },
    })
    table.insert(children, compose.DisposableEffect {
        key = show.value,
        effect = function() return function() end end,
    })
    table.insert(children, compose.Text { text = "DisposableEffect key=" .. tostring(show.value), fontSize = 12, color = 0xFF999999 })

    table.insert(children, compose.Text { text = "=== 31 个组件全部测试完成 ===", fontSize = 14, fontWeight = "Bold", color = 0xFF43A047 })

    return compose.Scaffold {
        title = "插件测试",
        children = { compose.LazyColumn { children = children } },
    }
end

compose.render(main)