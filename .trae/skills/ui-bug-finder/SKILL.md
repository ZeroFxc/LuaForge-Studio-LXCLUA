---
name: "ui-bug-finder"
description: "Systematically finds UI bugs in Android Compose apps. Invoke when user reports UI issues, missing elements, settings not applying, or visual problems. Follows a rigorous checklist-based approach."
---

# UI Bug Finder - Android Compose 问题排查技能

## 核心理念
按照用户实际使用路径，从"用户看到什么"出发，而不是从"代码写了什么"出发。每个功能必须验证：开关是否生效、文字是否完整显示、命名是否准确、交互是否实时响应。

## 排查流程

### 第一步：理解用户报告的问题
- 仔细阅读用户描述的现象
- 将问题分类：显示问题 / 交互问题 / 命名问题 / 实时性问题 / 功能缺失
- 记录用户期望的行为 vs 实际行为

### 第二步：源码追踪（必须按顺序执行）

#### 2.1 设置开关类问题
当用户说"开启了XX但没看到"：
1. 找到 SettingsManager 中对应的设置字段和默认值
2. 找到设置页面中该 Switch/FilterChip 的 onClick 回调，确认是否调用了 updateSettingsWithSave
3. 找到使用该设置的 Composable 函数，确认是否正确读取了 `currentSettings.xxx` 或 `collectAsState`
4. **关键点**：如果使用的是 `val x = currentSettings.xxx` 而不是 `by currentSettings.xxx.collectAsState()`，状态变化可能不触发重组
5. 确认显示条件判断是否正确（`if (showXxx)` 而非 `if (!showXxx)`）

#### 2.2 命名/术语问题
当用户说"应该叫XX不是YY"：
1. 搜索所有出现该术语的地方：硬编码中文字符串、stringResource
2. 统一修改，包括：菜单项、对话框标题、Toast提示、开关标签
3. 检查是否有语义错误：如"封面"≠"图标"，封面是整个卡片的背景

#### 2.3 文字显示不全问题
当用户说"文字太长显示不出来"：
1. 检查 Text 组件是否设置了 `maxLines` 和 `overflow = TextOverflow.Ellipsis`
2. 检查父容器是否给了足够空间或使用了 `weight(1f)`
3. 横向滚动列表（LazyRow）中的项目应设置固定宽度或使用 `fillMaxWidth` 结合 padding
4. 项目名称应优先完整显示，其他信息（如路径）可以省略

#### 2.4 实时切换问题
当用户说"切换XX没有实时生效"：
1. 确认状态是 `State<T>` 或通过 `collectAsState()` 获取
2. 确认 LaunchedEffect/remember 的 key 包含了该状态
3. 确认 Composable 参数中传递的是状态值而非捕获的旧值
4. 如果涉及组件位置变化（如分类栏在顶部/底部），检查 if/else 分支是否都正确渲染

#### 2.5 功能入口问题
当用户说"没看到XX管理器/XX按钮"：
1. 搜索该功能的显示触发点
2. 确认菜单项/按钮是否被添加到了正确的位置
3. 确认点击事件是否正确绑定
4. 检查是否有条件判断导致功能被隐藏

#### 2.6 角标/徽章问题
当用户说"点击XX角标没反应"：
1. 找到角标（Badge/Box）组件的 modifier
2. 确认是否添加了 `.clickable()` 或 `combinedClickable()`
3. 确认点击回调中是否有状态更新
4. 默认状态检查：不是"不显示"而是显示默认值（如 Release）

#### 2.7 文件选择器问题
当用户说"文件选择器里没有快捷目录"：
1. 检查是否使用了系统原生文件选择器（ACTION_OPEN_DOCUMENT）
2. 确认是否设置了正确的 mime type
3. 对于特定目录，考虑通过 Storage Access Framework 的 EXTRA_INITIAL_URI 初始位置
4. 添加常用路径快捷入口按钮在选择器对话框中

#### 2.8 封面vs图标问题
当用户说"封面不是图标"：
1. 封面是整个卡片的背景区域（大尺寸，如左侧120x120dp或顶部横幅）
2. 图标是小尺寸的项目标识（如40x40dp）
3. 纯色封面：Card 或 IconCoverArea 的 backgroundColor
4. 图片封面：Card 的背景图或 IconCoverArea 的 fillMaxSize 图片
5. 不要把"设置封面"做成"选择一个小图标"

### 第三步：修复验证清单
修复后自检：
- [ ] 开关打开/关闭，UI 元素立即显示/隐藏
- [ ] 所有文字完整显示，长文本有合理的省略策略
- [ ] 术语命名一致，符合用户预期
- [ ] 点击/长按交互有正确响应
- [ ] 设置变更实时生效，无需重启
- [ ] 功能入口在正确位置可见
- [ ] 封面与图标功能区分正确
- [ ] 对话框中有快捷操作入口

### 第四步：输出格式
修复完成后给出：
1. 问题根因
2. 修改的文件
3. 具体改动说明
4. 验证方法

## 注意事项
- 永远不要只修表面，要找到为什么设置了但没生效（状态订阅问题？条件判断反了？）
- 中文文字与变量拼接时必须使用 `${var}` 格式，避免 `$var中文` 歧义
- Compose 中状态必须是可观察的（State/Flow），直接读字段不会触发重组
- 修改时不要破坏已有功能，最小化改动
