# LXC-LUA Bug修复与插件系统全面扩展计划

## 一、任务概述

本计划涵盖以下5大模块的工作：
1. **启动Activity报错修复** - `MainActivityPlayStore does not exist` 问题
2. **最近列表卡片大小可调** - 从固定3档改为滑块自由调节
3. **首页布局预览FAB增强** - 眼睛图标长按弹出布局预览浮层
4. **大文件解耦** - 在保留功能前提下拆分超大文件
5. **插件系统全面扩展** - 贯穿整个程序的事件接口、挂钩、生命周期

---

## 二、现状分析

### 2.1 启动报错根因
- `AndroidManifest.xml` 中 `.SplashWelcome` 已直接配置了 `MAIN/LAUNCHER` intent-filter
- `IconManager.AppIcon.DEFAULT` 错误地映射到 `.SplashWelcome`（真实Activity），而非 `.MainActivityDefault`（别名）
- Android Studio 编译后自动运行时，系统通过 `componentName` 指定了错误的Activity别名组合
- 当所有 alias 被禁用再启用选中项时，存在窗口期没有可用的 launcher activity

### 2.2 最近卡片大小
- 当前 `recentCardWidth` 仅有3档：0=120dp, 1=150dp, 2=180dp
- SettingsScreen 中使用 SegmentedButton 选择，不支持自定义宽度
- HomeScreen 中通过 `recentCardWidthDp` 读取但写死映射关系

### 2.3 布局预览FAB
- 当前实现（SettingsScreen.kt:1023-1038）仅在首页布局设置卡片展开时显示一个眼睛图标FAB
- 点击直接 `onBack()` 返回首页，**没有长按预览功能**
- 需要实现：长按弹出对话框/浮层，实时渲染当前设置下的首页布局效果预览

### 2.4 大文件情况
| 文件 | 行数 | 拆分策略 |
|------|------|---------|
| HomeScreen.kt | 3211 | 按功能模块拆分：项目列表渲染、搜索/过滤、分类管理、批量操作、封面/标签管理、备份还原、导入导出 |
| ProjectCard.kt | 1340 | 已部分拆分（ProjectCardComponents.kt），继续拆分SwipeToDismissBoxWrapper、ProjectDropdownMenu、FlatModeContent、LargeCardModeContent |
| SettingsScreen.kt | 1832 | 按设置分组拆分：首页设置组、外观设置组、编辑器设置组、构建设置组、AI设置组、MCP设置组、关于设置组 |
| PluginBridgeImpl.kt | 1832 | 已按子模块拆分到PluginSys/Editor/Project等文件，检查是否还有集中代码未拆分 |

### 2.5 插件系统现状
- 已有基础事件系统（EventManager）支持监听器和拦截器
- 已有UI扩展点：工具栏按钮、首页FAB、分类栏、菜单项
- 事件覆盖不足，缺少以下挂钩点：
  - 编辑器内事件：光标移动、选择变化、编译输出、代码分析完成
  - 文件操作事件：新建文件/文件夹、重命名、删除、导入
  - UI交互事件：搜索、排序变更、分类切换、多选模式进入/退出
  - 悬浮窗事件、通知事件、主题变更事件
  - 插件间通信机制、自定义事件注册机制
  - Compose UI挂载点（编辑器工具栏、状态栏、侧边栏面板注入）
- 插件示例存在问题：API使用不一致、计数错误、废弃API、调试代码遗留

---

## 三、详细实施方案

### Phase 1: 修复启动Activity报错（高优先级）

**文件：** `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/.../utils/IconManager.kt`

**修改内容：**

1. **AndroidManifest.xml 修正：**
   - 将 `.SplashWelcome` 的 `MAIN/LAUNCHER` intent-filter 移除（只保留一个启用的启动入口）
   - `.SplashWelcome` 改为普通Activity（不直接暴露launcher intent-filter）
   - `.MainActivityDefault` 默认设为 `android:enabled="true"`，作为默认启动别名
   - `.MainActivityPlayStore` 保持 `enabled="false"`
   - 这样确保任何时刻只有一个 activity-alias 有 MAIN/LAUNCHER

2. **IconManager.kt 修正：**
   - `AppIcon.DEFAULT` 的 `aliasName` 改为 `.MainActivityDefault`
   - 修复切换逻辑：使用 `PackageManager.COMPONENT_ENABLED_STATE_ENABLED` 和 `COMPONENT_ENABLED_STATE_DISABLED` 时，避免同时禁用所有组件
   - `initIconSetting` 改为只在当前图标与保存值不一致时才执行切换，避免每次启动都禁用/启用组件

3. **验证：** 编译debug APK，通过Android Studio直接运行，确认不再报 `Activity class does not exist` 错误

---

### Phase 2: 最近列表卡片大小可调（中优先级）

**文件：**
- `app/src/main/kotlin/.../ui/settings/SettingsManager.kt`
- `app/src/main/kotlin/.../ui/settings/SettingsScreen.kt`
- `app/src/main/kotlin/.../HomeScreen.kt`

**修改内容：**

1. **SettingsManager.kt：**
   - 新增 `RECENT_CARD_WIDTH_DP` 偏好设置（Int类型，范围80-240dp，默认150）
   - 保留旧的 `recentCardWidth` (0/1/2) 用于迁移，加载时自动映射为dp值
   - `SettingsData` 中新增 `recentCardWidthDp: Int = 150`
   - 添加值域校验：`recentCardWidthDp.coerceIn(80, 240)`

2. **SettingsScreen.kt（首页布局设置组内）：**
   - 将原来的 SegmentedButton（紧凑/标准/宽）替换为 Slider 滑块
   - Slider范围：80dp..240dp，步长10dp
   - 旁边显示当前宽度数值（如 "150dp"）
   - 提供预设按钮：紧凑(120dp)、标准(150dp)、宽(180dp) 快速选择
   - 即时应用设置（onValueChange时即更新）

3. **HomeScreen.kt：**
   - 修改 `recentCardWidthDp` 计算逻辑，直接读取 `settings.recentCardWidthDp.dp`
   - 确保最近项目横向列表中的卡片宽度响应设置变化
   - 卡片内的文字大小、图标大小根据宽度做自适应调整

---

### Phase 3: 首页布局预览FAB（中优先级）

**文件：**
- `app/src/main/kotlin/.../ui/settings/SettingsScreen.kt`
- 新建 `app/src/main/kotlin/.../ui/components/HomeLayoutPreviewDialog.kt`

**修改内容：**

1. **新建 HomeLayoutPreviewDialog.kt：**
   - 创建一个 `@Composable fun HomeLayoutPreviewDialog(...)` 弹窗组件
   - 弹窗内容为一个缩小版的首页布局预览，展示：
     - 项目卡片列表（使用3-4个示例项目数据）
     - 根据当前SettingsData渲染卡片（CARD/FLAT模式、密度、圆角、显示/隐藏修改时间、显示/隐藏路径）
     - 如果启用分类则显示分类栏
     - 如果启用最近项目条则显示最近项目条
   - 弹窗占屏幕80%大小，内部使用缩放或固定小尺寸渲染
   - 点击弹窗外区域或关闭按钮关闭

2. **SettingsScreen.kt 修改：**
   - 现有的眼睛FAB保持：点击行为改为弹出预览对话框（而非直接onBack）
   - 新增长按预览：长按FAB时弹出预览对话框（与点击行为一致，但增加视觉反馈）
   - 实际上更合理的交互：点击=弹出预览对话框（可看到效果），对话框中提供"返回首页查看"按钮
   - 添加 `showPreviewDialog` 状态变量控制弹窗显示

3. **预览数据：** 使用硬编码的3个示例ProjectItem，模拟真实项目卡片的渲染效果

---

### Phase 4: 大文件解耦（高优先级，与其他任务穿插进行）

**拆分原则：**
- 提取可组合函数到独立文件，不改变原有逻辑和功能
- 保持原有import和类引用关系
- 每个拆出的文件遵循单一职责原则
- 不添加新功能，仅移动代码

**4.1 ProjectCard.kt 拆分（1340行 → ~400行）：**

拆出文件到 `ui/components/projectcard/` 目录：
- `SwipeToDismissWrapper.kt` - 滑动手势包装器（原513-713行）
- `ProjectCardMenu.kt` - 下拉菜单组件（原720-816行）
- `ProjectCardContent.kt` - 内容分发 + FlatMode/LargeCard内容（原823-1379行）

保留在 ProjectCard.kt：
- `ProjectCard()` 主函数（原158-504行）
- 必要的import和类型定义

**4.2 HomeScreen.kt 拆分（3211行 → ~800行）：**

拆出文件到 `ui/home/` 目录：
- `HomeProjectList.kt` - 项目列表渲染逻辑（LazyColumn、分组、排序、搜索过滤）
- `HomeSearchBar.kt` - 搜索栏和搜索逻辑
- `HomeCategoryBar.kt` - 分类管理UI（添加/编辑/删除分类、分类切换）
- `HomeRecentBar.kt` - 最近项目横向条
- `HomeMultiSelect.kt` - 多选模式逻辑和批量操作
- `HomeCoverManager.kt` - 封面设置、标签管理相关弹窗和逻辑
- `HomeBackupRestore.kt` - 备份/还原功能
- `HomeImportExport.kt` - 导入/导出项目、模板保存
- `HomeDialogs.kt` - 各种对话框（新建文件夹、重命名、删除确认等）

保留在 HomeScreen.kt：
- `MainScreen()` 主函数
- 状态管理和事件回调
- 整体布局Scaffold

**4.3 SettingsScreen.kt 拆分（1832行 → ~500行）：**

拆出文件到 `ui/settings/groups/` 目录：
- `HomeSettingsGroup.kt` - 首页布局设置组（原535-926行）
- `AppearanceSettingsGroup.kt` - 外观设置（深色模式、主题颜色、语言、字体大小等）
- `EditorSettingsGroup.kt` - 编辑器设置（字体、字号、自动补全、行号等）
- `BuildSettingsGroup.kt` - 构建相关设置（签名、包名、输出目录等）
- `AiSettingsGroup.kt` - AI设置（已部分拆出到AISettings.kt）
- `McpSettingsGroup.kt` - MCP设置（已部分拆出到MCPSettings.kt）
- `AboutSettingsGroup.kt` - 关于和版本信息

保留在 SettingsScreen.kt：
- `SettingsScreen()` 主函数
- 设置保存逻辑
- 顶栏和整体布局

**4.4 PluginBridgeImpl.kt 检查：**
- 确认已拆出的子模块（PluginSys.kt, PluginEditor.kt等）是否完整
- 将剩余的集中代码按功能分类移到对应子模块文件中
- PluginBridgeImpl.kt 只保留组合各子模块的桥接代码

---

### Phase 5: 插件系统全面扩展（核心任务）

#### 5.1 事件系统增强

**文件：** `plugin/state/EventManager.kt`, `plugin/state/PluginEvents.kt`

**新增事件常量（PluginEvents.kt）：**

```kotlin
// ========== 编辑器细粒度事件 ==========
/** 光标位置变化，参数: (filePath: String, line: Int, column: Int) */
const val ON_CURSOR_MOVED = "onCursorMoved"
/** 选择范围变化，参数: (filePath: String, startLine:Int, startCol:Int, endLine:Int, endCol:Int, selectedText:String) */
const val ON_SELECTION_CHANGED = "onSelectionChanged"
/** 编译/构建输出，参数: (projectPath: String, message: String, level: String) level: info/warn/error */
const val ON_BUILD_OUTPUT = "onBuildOutput"
/** 代码分析完成，参数: (filePath: String, diagnosticsJson: String) */
const val ON_ANALYZE_COMPLETE = "onAnalyzeComplete"
/** 文件切换（标签页切换），参数: (newFilePath: String?, oldFilePath: String?) */
const val ON_FILE_SWITCHED = "onFileSwitched"
/** 编辑器内容滚动，参数: (filePath: String, firstVisibleLine: Int, visibleLineCount: Int) */
const val ON_EDITOR_SCROLL = "onEditorScroll"

// ========== 文件操作事件 ==========
/** 文件新建完成，参数: (filePath: String, isDirectory: Boolean) */
const val ON_FILE_CREATED = "onFileCreated"
/** 文件重命名完成，参数: (oldPath: String, newPath: String, isDirectory: Boolean) */
const val ON_FILE_RENAMED = "onFileRenamed"
/** 文件删除完成，参数: (filePath: String, isDirectory: Boolean) */
const val ON_FILE_DELETED = "onFileDeleted"
/** 文件导入完成，参数: (filePath: String, sourceUri: String) */
const val ON_FILE_IMPORTED = "onFileImported"

// ========== 主页UI交互事件 ==========
/** 搜索文本变化，参数: (query: String) */
const val ON_SEARCH_QUERY_CHANGED = "onSearchQueryChanged"
/** 排序方式变化，参数: (sortOrder: String) */
const val ON_SORT_ORDER_CHANGED = "onSortOrderChanged"
/** 分类切换，参数: (categoryId: String?) */
const val ON_CATEGORY_CHANGED = "onCategoryChanged"
/** 多选模式进入，参数: 无 */
const val ON_MULTI_SELECT_ENTER = "onMultiSelectEnter"
/** 多选模式退出，参数: 无 */
const val ON_MULTI_SELECT_EXIT = "onMultiSelectExit"
/** 多选项目变化，参数: (selectedCount: Int, selectedIdsJson: String) */
const val ON_MULTI_SELECTION_CHANGED = "onMultiSelectionChanged"
/** 下拉刷新，参数: 无 */
const val ON_PULL_TO_REFRESH = "onPullToRefresh"

// ========== 主题/UI事件 ==========
/** 主题/深色模式变更，参数: (darkMode: String) darkMode: light/dark/system */
const val ON_THEME_CHANGED = "onThemeChanged"
/** 语言变更，参数: (languageCode: String) */
const val ON_LANGUAGE_CHANGED = "onLanguageChanged"
/** Toast显示，参数: (message: String, type: String) type: normal/success/warn/error */
const val ON_TOAST_SHOWN = "onToastShown"

// ========== 标签/分类/封面事件 ==========
/** 项目标签添加，参数: (projectId: String, tag: String) */
const val ON_PROJECT_TAG_ADDED = "onProjectTagAdded"
/** 项目标签移除，参数: (projectId: String, tag: String) */
const val ON_PROJECT_TAG_REMOVED = "onProjectTagRemoved"
/** 项目封面变更，参数: (projectId: String, coverType: String, coverValue: String) */
const val ON_PROJECT_COVER_CHANGED = "onProjectCoverChanged"
/** 项目置顶状态变化，参数: (projectId: String, pinned: Boolean) */
const val ON_PROJECT_PIN_CHANGED = "onProjectPinChanged"
/** 分类创建，参数: (categoryId: String, categoryName: String) */
const val ON_CATEGORY_CREATED = "onCategoryCreated"
/** 分类删除，参数: (categoryId: String) */
const val ON_CATEGORY_DELETED = "onCategoryDeleted"

// ========== 插件系统事件 ==========
/** 自定义事件广播（插件间通信用），参数: (senderPluginId: String, eventName: String, dataJson: String) */
const val ON_CUSTOM_EVENT = "onCustomEvent"
/** 插件消息（插件间点对点通信），参数: (fromPluginId: String, toPluginId: String, action: String, dataJson: String) */
const val ON_PLUGIN_MESSAGE = "onPluginMessage"
```

**EventManager.kt 新增API：**
```kotlin
/**
 * 注册自定义事件（插件可注册自己的事件名到系统，方便发现）
 * @param pluginId 插件ID
 * @param eventName 自定义事件名
 * @param description 事件描述（用于插件开发者文档）
 */
fun registerCustomEvent(pluginId: String, eventName: String, description: String = "")

/**
 * 获取所有已注册事件名（含系统事件和自定义事件）
 */
fun getAllRegisteredEvents(): List<EventInfo>

/**
 * 发送插件间消息
 * @param fromPluginId 发送者插件ID
 * @param toPluginId 目标插件ID（空字符串表示广播给所有插件）
 * @param action 动作标识
 * @param dataJson 数据JSON
 */
fun sendPluginMessage(fromPluginId: String, toPluginId: String, action: String, dataJson: String = "{}")

/**
 * 注册插件消息接收器
 */
fun registerPluginMessageHandler(pluginId: String, handler: (fromId:String, action:String, data:String) -> Unit)
```

#### 5.2 新增UI扩展点

**文件：** `plugin/state/UIState.kt`

新增以下扩展点：

```kotlin
// ========== 编辑器工具栏扩展 ==========
data class EditorToolbarAction(
    val id: String,
    val pluginId: String,
    val iconName: String,
    val tooltip: String,
    val showWhen: String, // "always" / "whenEditing" / "whenFileOpen"
    val onClick: Runnable
)
val editorToolbarActions = mutableStateListOf<EditorToolbarAction>()

// ========== 编辑器底部状态栏扩展 ==========
data class EditorStatusBarItem(
    val id: String,
    val pluginId: String,
    val text: String,           // 显示文本（支持动态更新）
    val tooltip: String,
    val onClick: Runnable?
)
val editorStatusBarItems = mutableStateListOf<EditorStatusBarItem>()

// ========== 编辑器侧边面板扩展（可注入Compose内容） ==========
data class EditorSidePanel(
    val id: String,
    val pluginId: String,
    val title: String,
    val iconName: String,
    val contentProvider: Any    // 存储LuaFunction或Composable lambda
)
val editorSidePanels = mutableStateListOf<EditorSidePanel>()

// ========== 项目卡片角标（Badge）扩展 ==========
data class ProjectBadgeInfo(
    val pluginId: String,
    val text: String,
    val backgroundColor: Long,  // ARGB颜色值
    val textColor: Long,
    val onClick: ((projectId: String) -> Unit)?
)
// 每个项目可关联多个badge，通过回调获取
val projectBadgeProviders = mutableMapOf<String, (projectId: String) -> List<ProjectBadgeInfo>>()

// ========== 编辑器Tab上下文菜单扩展 ==========
data class EditorTabMenuItem(
    val id: String,
    val pluginId: String,
    val label: String,
    val iconName: String?,
    val onClick: (filePath: String) -> Unit
)
val editorTabMenuItems = mutableStateListOf<EditorTabMenuItem>()

// ========== 全局悬浮FAB（任何页面都可显示） ==========
data class GlobalFabItem(
    val id: String,
    val pluginId: String,
    val iconName: String,
    val tooltip: String,
    val showOnScreens: Set<String>, // "main","editor","settings","about","plugins",空集合表示所有页面
    val onClick: Runnable
)
val globalFabs = mutableStateListOf<GlobalFabItem>()
```

#### 5.3 插件API桥接层扩展

**文件：** `plugin/bridge/PluginBridgeImpl.kt` 及各子模块文件

在 `plugin.events` 子模块中新增Lua API：

```lua
-- 事件系统扩展API
plugin.events.registerCustomEvent(eventName, description)  -- 注册自定义事件
plugin.events.sendPluginMessage(toPluginId, action, dataJson)  -- 发送插件间消息
plugin.events.onPluginMessage(handler)  -- 监听发给自己的消息
plugin.events.getAllEvents()  -- 获取所有已注册事件列表(table)

-- UI扩展新增API
plugin.ui.addEditorToolbarButton(id, iconName, tooltip, showWhen, onClick)
plugin.ui.removeEditorToolbarButton(id)
plugin.ui.addEditorStatusBarItem(id, text, tooltip, onClick)
plugin.ui.removeEditorStatusBarItem(id)
plugin.ui.addGlobalFab(id, iconName, tooltip, screensTable, onClick)
plugin.ui.removeGlobalFab(id)
plugin.ui.addProjectBadgeProvider(callback)  -- 回调接收projectId，返回badge列表table
plugin.ui.removeProjectBadgeProvider()

-- 项目操作API增强
plugin.project.getAllProjects()  -- 获取所有项目列表table
plugin.project.getProjectInfo(projectId)  -- 获取项目详细信息table
plugin.project.openProject(projectId)  -- 打开指定项目（导航到编辑器）
plugin.project.createProject(name, path, templateId)  -- 创建项目
plugin.project.importProject(uri)  -- 从URI导入项目
plugin.project.exportProject(projectId, destUri)  -- 导出项目

-- 编辑器API增强
plugin.editor.getCurrentFile()  -- 获取当前打开文件路径
plugin.editor.getCursorPosition()  -- 返回 {line, column}
plugin.editor.getSelection()  -- 返回 {startLine, startCol, endLine, endCol, text}
plugin.editor.setCursorPosition(line, column)
plugin.editor.setSelection(startLine, startCol, endLine, endCol)
plugin.editor.scrollToLine(line)
plugin.editor.getOpenFiles()  -- 返回所有打开的文件路径列表table
plugin.editor.closeFile(filePath)
plugin.editor.getLineCount(filePath)
plugin.editor.getText(filePath)
plugin.editor.setText(filePath, text)
plugin.editor.insertText(position, text)
plugin.editor.replaceText(startPos, endPos, text)

-- 应用控制API增强
plugin.sys.getAppVersion()
plugin.sys.getAppVersionCode()
plugin.sys.restartApp()
plugin.sys.vibrate(milliseconds)
plugin.sys.clipboardRead()
plugin.sys.clipboardWrite(text)
plugin.sys.shareText(title, text)
plugin.sys.openUrl(url)
plugin.sys.showToast(message, type)  -- type: "normal","success","warn","error"

-- 数据存储API增强
plugin.config.setPluginData(key, value)  -- 插件私有存储
plugin.config.getPluginData(key, defaultValue)
plugin.config.removePluginData(key)
plugin.config.getAllPluginData()  -- 获取插件所有存储数据

-- 线程API增强
plugin.threads.postOnMain(delayMs, callback)  -- 主线程延迟执行
plugin.threads.io(callback)  -- IO线程执行
plugin.threads.timer(intervalMs, callback)  -- 定时器，返回timerId
plugin.threads.cancelTimer(timerId)
```

#### 5.4 事件触发点注入

在以下位置添加事件触发：

**CodeEditScreen.kt（编辑器）：**
- 光标移动回调 → `ON_CURSOR_MOVED`
- 选择变化回调 → `ON_SELECTION_CHANGED`
- 文件切换（Tab切换） → `ON_FILE_SWITCHED`
- 编辑器滚动 → `ON_EDITOR_SCROLL`
- 初始化完成 → `ON_EDITOR_INIT`（已有，确认触发位置正确）

**构建系统：**
- 构建输出每一行 → `ON_BUILD_OUTPUT`
- 构建开始/结束/错误 → 已有 `ON_BUILD_START/FINISH/ERROR`，确认参数传递正确

**HomeScreen.kt：**
- 搜索框文本变化 → `ON_SEARCH_QUERY_CHANGED`
- 排序方式切换 → `ON_SORT_ORDER_CHANGED`
- 分类切换 → `ON_CATEGORY_CHANGED`
- 多选模式进入/退出 → `ON_MULTI_SELECT_ENTER/EXIT`
- 多选项目变化 → `ON_MULTI_SELECTION_CHANGED`
- 下拉刷新 → `ON_PULL_TO_REFRESH`
- 标签添加/移除 → `ON_PROJECT_TAG_ADDED/REMOVED`
- 封面变更 → `ON_PROJECT_COVER_CHANGED`
- 置顶状态变化 → `ON_PROJECT_PIN_CHANGED`
- 分类创建/删除 → `ON_CATEGORY_CREATED/DELETED`

**文件树/文件操作：**
- 新建文件/文件夹 → `ON_FILE_CREATED`
- 重命名 → `ON_FILE_RENAMED`
- 删除 → `ON_FILE_DELETED`
- 导入 → `ON_FILE_IMPORTED`

**MainActivity.kt（应用生命周期）：**
- 主题变更 → `ON_THEME_CHANGED`
- 语言变更 → `ON_LANGUAGE_CHANGED`
- Toast显示拦截点 → `ON_TOAST_SHOWN`（可拦截）

#### 5.5 插件示例全面更新与新增

**修复现有示例问题：**
1. `hello_plugin` - 修复快捷操作计数（73个而非26个），修复非ASCII URL问题，补充新增API演示
2. `floating_ai_demo` - 统一使用 `plugin.sys.*` 而非 `plugin.system.*`，移除调试代码注释
3. `editor_search` - 将废弃的 `onSubmit` 改为 `onFloatingPanelSubmit`
4. `build_hook_demo` - 添加用户提示后再注册钩子，不要静默hook
5. `project_tools_plugin` - 修正 `addProjectCardMenuItem` 参数格式与API一致

**新增插件示例：**

1. **`plugin_event_demo`（事件系统完整示例）：**
   - 演示所有系统事件的监听（注册/注销）
   - 演示事件拦截器用法（拦截返回键、拦截项目打开）
   - 演示一次性事件监听
   - 演示自定义事件注册和触发
   - 演示插件间消息收发

2. **`editor_api_demo`（编辑器API完整示例）：**
   - 演示光标位置获取/设置
   - 演示文本选择和操作
   - 演示编辑器工具栏按钮添加
   - 演示状态栏信息显示
   - 演示文件遍历和内容读取
   - 强调Java方法调用使用 `.` 而非 `:`（如 `luajava.bindClass("java.io.File")` 返回的对象方法用 `:` 但Java静态方法和字段用 `.`）

3. **`ui_extension_demo`（UI扩展点完整示例）：**
   - 演示全局FAB注册（在编辑器页面显示）
   - 演示项目卡片Badge添加
   - 演示编辑器Tab菜单扩展
   - 演示首页工具栏/FAB/分类栏扩展（已有home_extension_demo，补充新API）

4. **`plugin_lifecycle_demo`（生命周期演示）：**
   - 演示onLoad/onUnload中正确注册/注销所有监听器和UI元素
   - 演示插件数据持久化（plugin.config）
   - 演示定时器使用和正确清理

**编写插件开发文档（main.lua头部注释规范）：**
每个示例插件的 `main.lua` 头部必须包含：
```lua
--[[
插件名称: xxx
功能描述: xxx
使用API: plugin.events.on(), plugin.ui.addXxx(), ...
注意事项:
  - Java对象方法调用使用冒号(:)，如 file:getName()
  - Java静态方法/字段使用点号(.)，如 JavaFile.separator
  - Lua对象方法调用使用冒号(:)
  - plugin.xxx.* API均使用点号(.)调用
--]]
```

---

## 四、关键决策与假设

1. **启动Activity方案**：采用activity-alias统一管理，默认启用`.MainActivityDefault`作为唯一启动入口，SplashWelcome作为普通Activity不直接暴露launcher intent-filter。这是Android多图标切换的标准做法。

2. **卡片宽度范围**：选择80dp-240dp范围，步长10dp，覆盖从小屏到大屏的合理范围。预设按钮保留3个常用档位。

3. **布局预览实现**：使用Dialog+缩小版预览（而非真正导航到首页再返回），这样用户不离开设置页即可看到效果。预览使用模拟数据，不加载真实项目列表以保证性能。

4. **文件拆分策略**：采用"提取函数+保留接口"的方式，不改变State hoisting状态，不改变函数签名，确保拆分后行为完全一致。先拆分ProjectCard（风险最低），再拆分SettingsScreen，最后拆分HomeScreen。

5. **插件API设计**：保持Lua API调用风格统一——所有`plugin.xxx.yyy()`均使用点号`.`调用（Lua模块风格），只有当获取到Java对象实例后才用冒号`:`调用Java实例方法。在示例插件中用注释明确标注这一点。

6. **事件触发语义**：
   - `ON_*_START`/`ON_BEFORE_*`类事件：在操作执行**前**触发，支持拦截
   - `ON_*_COMPLETE`/`ON_*_FINISH`/普通完成事件：在操作成功**后**触发，用于通知
   - 拦截器使用`checkIntercepted()`（仅检查拦截，不通知监听器）
   - 操作完成后使用`fireEvent()`（通知所有监听器）

---

## 五、验证步骤

1. **编译验证**：
   - 执行 `gradlew assembleDebug` 确保无编译错误
   - 执行 `gradlew assembleRelease` 确保release构建也通过

2. **启动验证**：
   - 通过Android Studio直接运行debug APK，确认不再出现 `Activity class does not exist` 错误
   - 切换应用图标（默认/PlayStore/自适应），杀掉应用后重新启动，确认能正常启动
   - 从桌面快捷方式启动，确认正常

3. **最近卡片大小验证**：
   - 进入设置→首页布局，拖动Slider调整最近卡片宽度
   - 返回首页查看最近项目条的卡片宽度变化
   - 测试80dp（最小）和240dp（最大）边界值
   - 点击预设按钮确认能快速设置

4. **布局预览验证**：
   - 进入设置→首页布局，展开卡片组
   - 点击眼睛FAB，弹出预览对话框
   - 在对话框中查看当前布局效果
   - 切换CARD/FLAT模式、密度、圆角等设置，预览实时更新
   - 点击关闭/弹窗外区域关闭对话框
   - 长按FAB验证与点击行为一致

5. **大文件解耦验证**：
   - 确认所有功能正常（项目列表、搜索、分类、标签、封面、备份、导入、多选等）
   - 确认ProjectCard的所有功能正常（滑动手势、下拉菜单、封面显示、角标等）
   - 确认所有设置项正常保存和加载

6. **插件系统验证**：
   - 安装所有示例插件，启用后检查日志无错误
   - 测试hello_plugin的所有API调用
   - 测试event_intercept_demo的拦截功能（拦截项目删除）
   - 测试home_extension_demo的UI扩展
   - 测试新增插件示例（editor_api_demo、plugin_event_demo、ui_extension_demo）
   - 确认插件卸载后所有注册的事件监听器、拦截器、UI元素被正确清理

---

## 六、执行顺序

1. **Phase 1** - 修复启动Activity报错（先解决阻塞性问题）
2. **Phase 4.1** - 拆分ProjectCard.kt（解耦基础组件）
3. **Phase 2** - 最近列表卡片大小可调
4. **Phase 3** - 首页布局预览FAB
5. **Phase 4.3** - 拆分SettingsScreen.kt
6. **Phase 4.2** - 拆分HomeScreen.kt（最大文件，在其他功能完成后拆分）
7. **Phase 5.1-5.3** - 插件事件系统和API桥接层扩展
8. **Phase 5.4** - 事件触发点注入（在拆分后的文件中直接添加）
9. **Phase 5.5** - 插件示例更新和新增
10. **Phase 4.4** - PluginBridgeImpl.kt检查和清理
11. **最终编译验证** - assembleDebug + assembleRelease
