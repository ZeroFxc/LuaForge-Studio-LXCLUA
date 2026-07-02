# LXC-LUA 首页功能拓展实现计划

## 概要

本计划实现用户要求的全部首页拓展功能，涵盖：项目标签/收藏、最近打开历史、搜索增强、滑动手势可视化、批量操作、项目备份/还原、模板功能（含导入）、桌面快捷方式、自定义封面、手动拖拽排序、分类图标扩展、卡片密度调节，以及全套开关控制。

**核心约束**：不修改 PluginManager 的公开 API（BadgeInfo、ProjectCardMenuItem、事件名等），插件的徽章、菜单项、滑动事件继续正常工作。

---

## 当前状态分析

- Kotlin 2.3.10 + Compose 1.8.0-alpha08 + Material3 1.4.0-alpha14（支持 AnchoredDraggable 和 SwipeToDismissBox）
- 数据持久化：DataStore + SD卡双写（Gson序列化），SettingsManager 单例，当前 SettingsData 已有30+字段
- 首页 `MainScreen()` 在 HomeScreen.kt 中，使用 LazyColumn 渲染项目列表，支持 CARD/FLAT 两种布局
- ProjectCard 已有两种布局模式、更多菜单、徽章、调试标签，滑动使用裸 `detectHorizontalDragGestures` 无视觉反馈
- 新建项目页 NewProjectScreen 已支持 assets 内置模板，使用两步向导
- 现有 zip 工具 `FileUtil.createZip/extractZip` 可直接复用
- 插件 API 边界清晰：菜单项/徽章通过 PluginManager 注入，swipe 通过 EventManager 广播事件

---

## 实现步骤

### 阶段一：数据层扩展（SettingsManager）

**文件**：`app/src/main/kotlin/com/luaforge/studio/lxclua/ui/settings/SettingsManager.kt`

1. **新增枚举和数据类**（文件顶部，现有枚举之后）：
   - `SortOrder` 枚举追加 `CUSTOM`
   - `HomeDensity { COMPACT, COMFORTABLE, LARGE }`
   - `CategoryBarPosition { TOP, BOTTOM }`
   - `ProjectTag(id: String, name: String, color: Long = 0xFF6750A4)`
   - `CoverType { SOLID_COLOR, IMAGE }`
   - `ProjectCover(type: CoverType, colorValue: Int = 0xFF6750A4.toInt(), imagePath: String = "")`
   - `ProjectCategory` 追加 `icon: String = ""` 字段（默认空，Gson兼容旧数据）

2. **扩展 PreferencesKeys**：
   - `PROJECT_TAGS` (string)、`PROJECT_TAGS_MAP` (string)
   - `RECENT_PROJECTS` (string)
   - `SHOW_PROJECT_MODIFIED_TIME` (bool, 默认true)
   - `SHOW_PROJECT_PATH` (bool, 默认true)
   - `CARD_CORNER_RADIUS` (int, 0/1/2, 默认1)
   - `AUTO_OPEN_LAST_PROJECT` (bool, 默认false)
   - `CATEGORY_BAR_POSITION` (string, 默认TOP)
   - `HOME_DENSITY` (string, 默认COMFORTABLE)
   - `PROJECT_COVER_MAP` (string)
   - `CUSTOM_PROJECT_ORDER` (string)
   - `BACKUP_PATH` (string)
   - `SHOW_TAG_FILTER_BAR` (bool, 默认true)
   - `SHOW_RECENT_PROJECTS_BAR` (bool, 默认true)

3. **扩展 SettingsData**：追加所有新字段并带默认值

4. **load/save 流程**：
   - DataStore 加载分支：读取每个新键，try-catch 兜底
   - DataStore 保存分支：序列化集合类型为 JSON
   - SD卡加载分支：每个新字段 `?: 默认值` 手动兜底
   - SD卡保存无需改动（gson.toJson自动包含新字段）

5. **新增便捷方法**：
   - `pushRecentProject(projectId, context)`：FIFO队列，去重，最多5个
   - `getBackupDirectory(): File`：默认 `/sdcard/LXC-LUA/backups`
   - `getTemplatesDirectory(): File`：`/sdcard/LXC-LUA/templates`
   - `setProjectTags(projectId, tagIds, context)`
   - `setProjectCover(projectId, cover, context)`
   - `updateCustomOrder(orderedIds, context)`

---

### 阶段二：工具类扩展

**文件**：`app/src/main/kotlin/com/luaforge/studio/lxclua/utils/ProjectUtil.kt`
- `loadUserTemplates(context)`：扫描用户模板目录
- `importTemplateFromZip(context, zipFile)`：拷贝zip到用户模板目录
- `backupProjectToZip(projectDir, backupDir, backupName)`：封装打包逻辑
- `restoreProjectFromZip(zipFile, targetDir)`：封装解压逻辑
- `extractPreviewImage` 支持回退查找 icon.png

**新建文件**：`app/src/main/kotlin/com/luaforge/studio/lxclua/utils/MaterialIconMap.kt`
- `MATERIAL_ICON_MAP: Map<String, ImageVector>`：约40个常用图标名→ImageVector映射
- `getIconByName(name: String): ImageVector?` 查询函数

**新建文件**：`app/src/main/kotlin/com/luaforge/studio/lxclua/utils/ShortcutHelper.kt`
- `createShortcut(context, project)`：使用 ShortcutManagerCompat.requestPinShortcut
- `updateShortcuts(context, recentProjects)`：推送动态快捷方式（最多5个最近项目）

---

### 阶段三：ProjectCard 组件升级

**文件**：`app/src/main/kotlin/com/luaforge/studio/lxclua/ProjectCard.kt`

1. **新增参数**：
   - `cover: ProjectCover?`、`cornerRadius: Dp = 12.dp`
   - `density: HomeDensity = COMFORTABLE`
   - `tags: List<ProjectTag> = emptyList()`
   - `showModifiedTime: Boolean = true`、`showPath: Boolean = true`
   - `highlightText: String = ""`（搜索高亮）
   - `onBackupClick`、`onSaveAsTemplateClick`、`onSetCoverClick`、`onSetTagsClick`、`onCreateShortcutClick`
   - `enableSwipeGesture: Boolean = true`

2. **密度适配**：
   - COMPACT：vertical 8dp/10dp，horizontal 12dp，icon 36dp/32dp，字体小一号
   - COMFORTABLE：保持现有尺寸
   - LARGE：vertical 20dp，horizontal 20dp，icon 56dp，标题 titleLarge

3. **封面渲染**：
   - SOLID_COLOR：图标区背景使用纯色
   - IMAGE：SubcomposeAsyncImage 加载图片，ContentScale.Crop

4. **标签显示**：项目名下方 FlowRow 显示标签Chip，最多3个+`+N`

5. **搜索高亮**：新增 `HighlightedText` Composable，使用 AnnotatedString 对匹配片段加背景色+粗体

6. **更多菜单追加项**（在删除后、插件菜单前）：
   - 备份项目（Backup图标）
   - 保存为模板（Style/Save图标）
   - 设置封面（Image图标）
   - 管理标签（Label图标）
   - 添加到桌面（Launch图标）
   - 末尾保留 `extraMenuItems`（插件菜单位置不变）

7. **卡片形状**：使用 `RoundedCornerShape(cornerRadius)`

8. **滑动手势可视化**：
   - 使用 Material3 `SwipeToDismissBox` 替换裸 pointerInput
   - 左滑（END方向）：1/3阈值显示置顶（primaryContainer背景+Star图标），2/3阈值显示删除（error背景+Delete图标）
   - 右滑（START方向）：显示分享（primaryContainer背景+Share图标）和标签（secondaryContainer背景+Label图标）
   - 阈值到达后动画复位并执行回调
   - 多选模式、enableSwipeGesture=false时禁用滑动
   - **保持** `onSwipeLeft/onSwipeRight` 参数和调用，继续广播插件事件

---

### 阶段四：HomeScreen (MainScreen) 升级

**文件**：`app/src/main/kotlin/com/luaforge/studio/lxclua/HomeScreen.kt`

1. **新增状态**：
   - 搜索过滤：searchFilterCategory、searchFilterTags、searchTimeRange（ALL/TODAY/THIS_WEEK/THIS_MONTH）
   - 标签筛选：selectedTagIds（空为全部，AND关系）
   - 对话框：批量操作、备份/还原、模板保存/导入、封面设置、标签管理、分类图标选择、快捷方式确认
   - 拖拽排序：draggingIndex、draggingOffset

2. **派生数据修改**：
   - `filteredProjects` 综合过滤：关键字+分类+标签+时间范围
   - `displayedProjects`：CUSTOM排序时按customProjectOrder排列（置顶仍在前，不在列表中的追加末尾）
   - `recentProjectItems`：按recentProjects顺序取前5个存在的项目

3. **布局顺序**（TOP模式）：
   - 最近项目横向条（LazyRow，RecentProjectMiniCard小卡片）
   - "继续上次项目"卡片
   - 分类筛选栏
   - 标签筛选栏（支持多选+管理按钮）
   - PullToRefreshBox + LazyColumn（项目列表）
   - BOTTOM模式：分类/标签栏放入Scaffold.bottomBar槽位

4. **搜索增强**：
   - TopAppBar搜索区trailing加FilterAlt图标按钮
   - 过滤面板：分类下拉、标签多选Chip、时间范围Chip
   - 搜索关键字传入ProjectCard.highlightText

5. **拖拽排序**（CUSTOM模式）：
   - 使用 `detectDragGesturesAfterLongPress`
   - 拖拽中item做scale+translationY+elevation效果
   - 置顶项目不参与拖拽
   - 结束时调用 `updateCustomOrder`

6. **批量操作**（复用现有多选模式）：
   - Scaffold.topBar替换为批量操作栏（选中数量、全选、取消、反选）
   - Scaffold.bottomBar显示操作按钮行：批量删除、批量导出(zip)、批量移动分类、批量加标签、批量备份
   - IO操作在Dispatchers.IO执行，完成后toast

7. **备份/还原**：
   - 单项目：更多菜单→备份项目→IO打包→toast路径
   - 批量：多选后批量备份
   - 还原：首页更多菜单→导入备份→FilePicker选zip→冲突处理（复用现有OVERWRITE/CLONE逻辑）→解压→刷新列表

8. **模板功能**：
   - 保存为模板：IO线程打包到用户模板目录
   - 导入模板：新建项目页增加"导入模板"按钮，选择zip复制到用户模板目录

9. **封面设置对话框**：
   - 两个Tab：纯色（12种预设+自定义）/图片（FilePicker选图，复制到项目目录_cover.jpg）
   - 清除封面按钮

10. **标签管理**：
    - 全局标签管理对话框：新建/改名/改色/删除（删除时清理projectTagsMap）
    - 项目标签编辑对话框：多选Chip列表
    - 标签筛选栏支持多选（AND关系）

11. **分类图标选择**：
    - 编辑分类对话框增加图标选择区：LazyVerticalGrid显示MATERIAL_ICON_MAP中所有图标按钮
    - 分类FilterChip使用leadingIcon显示选中的图标

12. **卡片密度/圆角/显示开关**：
    - 从currentSettings读取，传递给ProjectCard
    - density同时控制LazyColumn的contentPadding和spacedBy

---

### 阶段五：设置页扩展

**文件**：`app/src/main/kotlin/com/luaforge/studio/lxclua/ui/settings/SettingsScreen.kt`

在"首页布局"分组追加：
- 卡片密度：三个FilterChip（紧凑/舒适/大）
- 卡片圆角：三个FilterChip（小8dp/中12dp/大20dp）
- 显示修改时间 Switch
- 显示项目路径 Switch
- 自动打开上次项目 Switch
- 分类栏位置：两个FilterChip（顶部/底部）
- 显示最近项目条 Switch
- 显示标签筛选栏 Switch

---

### 阶段六：MainActivity 启动与快捷方式

**文件**：`app/src/main/kotlin/com/luaforge/studio/lxclua/MainActivity.kt`

1. **autoOpenLastProject**：LaunchedEffect在项目列表加载完成后，若开关开启且lastOpenedProjectId存在，自动导航到编辑器
2. **onNavigateToEditor 中**：调用 `pushRecentProject` + `ShortcutHelper.updateShortcuts`
3. **Intent处理**：onCreate中检查Intent extras，若带EXTRA_PROJECT_ID则自动打开该项目
4. **现有硬编码中文字符串**：一并抽取到strings.xml

**文件**：`app/src/main/AndroidManifest.xml`
- 添加 `com.android.launcher.permission.INSTALL_SHORTCUT` 权限（兼容旧版）

---

### 阶段七：新建项目页扩展

**文件**：`app/src/main/kotlin/com/luaforge/studio/lxclua/ui/project/NewProjectScreen.kt`

- 模板列表合并内置模板+用户模板
- 模板选择区增加"导入模板"按钮，弹出FilePicker选择zip
- 用户模板显示删除按钮，内置模板不显示

---

### 阶段八：字符串资源

**文件**：`app/src/main/res/values/strings.xml`（中文）、`values-en/strings.xml`（英文）
- 新增所有功能相关字符串（标签、最近、备份、模板、封面、快捷方式、搜索过滤、批量操作、设置项等）
- 将现有硬编码中文（"设置分类"、"创建分类"、"无分类"、"删除分类"、"编辑分类"、"不再显示"等）抽取到strings.xml

---

### 阶段九：清理与兼容

1. **删除项目时清理关联数据**（performDelete中）：从recentProjects、projectTagsMap、projectCoverMap、customProjectOrder、所有ProjectCategory.projectIds中移除该项目id
2. **颜色规范**：所有Color构造使用 `Color(intValue)`，禁止 `Color(Long.toULong())`
3. **所有新布尔开关默认true**（除autoOpenLastProject默认false）
4. **性能**：封面图片使用Coil加载；标签/封面Map读取放在remember块中；IO操作均在Dispatchers.IO

---

## 验证步骤

1. 编译通过（assembleDebug无错误）
2. 数据持久化：重启应用后标签、封面、排序、最近项目、设置全部保留
3. SD卡双写：app_settings.json包含所有新字段；删除后能从DataStore恢复
4. 插件兼容：现有插件demo的徽章、菜单项、滑动事件正常工作
5. 批量操作：批量删除/导出/移动/加标签/备份功能可用
6. 拖拽排序：CUSTOM模式下长按拖拽排序，重启后顺序保留
7. 滑动手势：开关关闭时不响应；开启时左右滑显示动作背景和图标，触发动作正确；多选模式下禁用
8. 搜索高亮：匹配片段高亮显示；分类/标签/时间过滤有效
9. 备份还原：备份生成zip，导入备份可还原，冲突处理正常
10. 模板：保存为模板后在新建页可见；导入zip模板可用；内置模板正常
11. 桌面快捷方式：长按图标显示最近项目；"添加到桌面"创建成功；点击直达项目
12. 封面：纯色/图片封面正确显示，清除后恢复默认
13. 分类图标：编辑时可选图标，筛选栏显示图标
14. 密度三档切换后padding/图标/字体/spacedBy对应变化
15. 分类栏BOTTOM模式：固定底部，不被FAB遮挡
16. 自动打开：冷启动自动进入上次项目
17. 删除项目后所有相关Map/List已清理，无脏数据

---

## 修改文件清单

| 文件 | 类型 | 主要改动 |
|---|---|---|
| `ui/settings/SettingsManager.kt` | 修改 | 新增枚举/数据类，扩展SettingsData+Keys，新增便捷方法，完善load/save双写 |
| `HomeScreen.kt` | 修改 | 新增状态与对话框，重构布局顺序，搜索增强，拖拽排序，批量操作，备份/模板/封面/标签/快捷方式业务逻辑，密度/开关参数下发 |
| `ProjectCard.kt` | 修改 | 封面/标签/密度/圆角/高亮支持，更多菜单追加项，SwipeToDismissBox替换裸手势 |
| `MainActivity.kt` | 修改 | autoOpenLastProject启动逻辑，pushRecentProject/updateShortcuts调用，Intent快捷方式处理 |
| `utils/ProjectUtil.kt` | 修改 | 用户模板加载/导入、备份/还原方法，extractPreviewImage支持icon.png回退 |
| `utils/MaterialIconMap.kt` | 新建 | Material图标名→ImageVector映射 |
| `utils/ShortcutHelper.kt` | 新建 | 桌面快捷方式创建和动态更新 |
| `ui/settings/SettingsScreen.kt` | 修改 | 首页布局分组新增密度/圆角/显示开关/分类栏位置等设置项 |
| `ui/project/NewProjectScreen.kt` | 修改 | 合并用户模板，添加导入模板按钮，用户模板删除 |
| `AndroidManifest.xml` | 修改 | 添加快捷方式权限 |
| `res/values/strings.xml` | 修改 | 新增所有中文文案，抽取硬编码字符串 |
| `res/values-en/strings.xml` | 修改 | 新增所有英文文案 |

---

## 假设与决策

- 项目标签为多对多关系（一个项目可有多个标签），分类保持一对多（一个项目只属于一个分类）
- 最近项目最多5个（FIFO队列，去重）
- 备份文件名格式：`项目名_yyyyMMdd_HHmmss.zip`
- 用户模板目录 `/sdcard/LXC-LUA/templates`，备份目录 `/sdcard/LXC-LUA/backups`
- 封面图片复制到项目目录下 `_cover.jpg`，避免源文件删除失效
- 多选模式下批量操作栏由HomeScreen直接读取PluginManager.multiSelectedProjectIds，不替代插件多选API
- 标签筛选默认AND关系（项目必须包含所有选中标签）
- SwipeToDismissBox使用Material3官方组件，如遇API版本问题降级使用foundation.AnchoredDraggable手写
- 所有新功能都有开关可关闭，默认开启（自动打开上次项目默认关闭）
