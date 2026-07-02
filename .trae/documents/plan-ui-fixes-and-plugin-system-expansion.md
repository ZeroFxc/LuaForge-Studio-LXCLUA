# 实施计划：UI修复 + 大文件解耦 + 插件系统全面扩展

## Summary

本计划包含三大部分：
1. **紧急Bug修复**：启动Activity报错、最近列表卡片大小可调、首页布局预览FAB、Debug/Release角标、封面全卡片背景等UI问题
2. **大文件解耦**：将 HomeScreen.kt(2939行)、SettingsScreen.kt(1782行)、ProjectCard.kt(1545行) 中独立功能抽离为单独的Composable文件，保留功能不变
3. **插件系统全面扩展**：补充缺失事件、增加拦截机制、扩展UI挂载点、完善事件生命周期触发、编写完整插件示例
额外任务:
1.项目卡片背景图片要适应卡片大小并且可以拖拽移动位置而不是项目卡片适应图片大小
2.项目切换 release 或者 debug 项目的setting.json里的debug模式并没有进行对应的修改
## Current State Analysis

### 启动报错问题
- AndroidManifest.xml 中 `MainActivityPlayStore` 是 activity-alias，targetActivity 指向 `.SplashWelcome`
- SplashWelcome (app模块) 始终启动 `MainActivity`，不存在 `MainActivityPlayStore` Activity 类
- Android Studio 运行时可能因为默认选中了 alias 而非实际 Activity，导致 "Activity class does not exist" 错误
- 需要在 build.gradle.kts 中配置正确的启动 Activity，或确保运行配置指向正确的 Activity

### 现有插件系统缺口
- 编辑器事件（onFileOpen/Save/Close/TextChanged/EditorInit/EditorClose）已定义但**未实际触发**
- 缺少项目CRUD事件：onProjectCreate/Delete/Rename/Open/Backup/Restore
- 缺少设置变更事件：onSettingsChanged
- 缺少页面导航事件：onPageChanged
- 缺少新建项目事件：onNewProject
- 事件系统是单向通知，**不支持拦截/阻止默认行为**（仅cancelBuild例外）
- 项目卡片普通点击不触发插件事件，仅多选模式触发
- 缺少首页UI扩展点：分类栏、搜索栏、顶部工具栏、FAB区域
- Java 反射调用示例需要明确说明用 `.` 而非 `:`

### 大文件情况
| 文件 | 行数 | 问题 |
|------|------|------|
| HomeScreen.kt | 2939 | 首页全部逻辑+所有对话框集中在一个文件 |
| SettingsScreen.kt | 1782 | 设置页全部逻辑+所有设置项 |
| ProjectCard.kt | 1545 | 卡片组件+滑动包装+封面区域+标签+角标+两种布局模式 |

## Proposed Changes

### 第一部分：紧急Bug修复和UI完善

#### 1. 修复启动Activity报错
**文件**: `app/build.gradle.kts`
- 在 defaultConfig 中添加 `manifestPlaceholders` 或确保启动 Activity 配置正确
- 或者检查是否需要在 AndroidManifest.xml 中将 MAIN/LAUNCHER intent-filter 直接放在 SplashWelcome 上而非 alias 上

**文件**: `app/src/main/AndroidManifest.xml`
- 确保 LAUNCHER activity-alias 配置正确，targetActivity 存在
- 考虑将 MAIN/LAUNCHER 直接放在 SplashWelcome 上，alias 仅用于图标切换

#### 2. 最近列表卡片大小可调
**文件**: `app/src/main/kotlin/.../ui/settings/SettingsManager.kt`
- 在 SettingsData 中添加 `recentCardWidth: Int = 1` (0=小120dp, 1=中150dp, 2=大180dp)

**文件**: `app/src/main/kotlin/.../ui/settings/SettingsScreen.kt`
- 在首页布局设置区域添加"最近项目卡片大小"选择项（FilterChip：小/中/大）

**文件**: `app/src/main/kotlin/.../HomeScreen.kt`
- 根据设置动态计算最近项目mini-card宽度

#### 3. 首页布局预览FAB
**文件**: `app/src/main/kotlin/.../HomeScreen.kt`
- 在首页布局设置对话框展开时，右下角显示浮动FAB（眼睛图标 Icons.Filled.Visibility）
- 长按FAB可预览当前布局配置效果（不保存，松手恢复）
- 点击FAB则立即应用当前配置并关闭对话框

#### 4. 封面作为整个卡片背景
**文件**: `app/src/main/kotlin/.../ProjectCard.kt`
- 纯色封面：Card 的 containerColor 使用封面颜色（已实现，需验证）
- 图片封面：SubcomposeAsyncImage 覆盖整个 Card 区域（fillMaxSize），内容叠放在半透明遮罩上
- 已添加 cardContainerColor 在图片封面时为黑色背景

#### 5. 路径显示简化
**文件**: `app/src/main/kotlin/.../utils/ProjectUtil.kt`
- shortenPath() 已实现：/storage/emulated/0/xxx → /sdcard/xxx
- 确保 ProjectCard 中路径显示使用该方法

#### 6. Release角标绿色
**文件**: `app/src/main/kotlin/.../ProjectCard.kt`
- debugBgColor: Release用绿色(0xFF4CAF50)，Debug用primary色（已实现，需验证）

#### 7. 无分类崩溃修复
**文件**: `app/src/main/kotlin/.../ui/settings/SettingsManager.kt`
- ProjectCategory.icon 改为 `String? = null`（已实现）
- 验证 copy() 方法不会因null icon NPE

**文件**: `app/src/main/kotlin/.../HomeScreen.kt`
- "无分类"选项点击时，确保不传递null给需要非空参数的copy

#### 8. 分类栏位置实时切换
**文件**: `app/src/main/kotlin/.../HomeScreen.kt`
- 确认分类栏根据 categoryBarPosition 在顶部/底部正确渲染
- 确保使用 currentSettings 的状态收集方式正确（collectAsState）

### 第二部分：大文件解耦

#### 1. ProjectCard.kt 拆分（1545行 → 多个文件）
**新文件**: `app/src/main/kotlin/.../ui/components/ProjectCardComponents.kt`
- 提取: DefaultProjectIcon(), IconCoverArea(), TagsRow(), DebugBadge(), ProjectBadges()
- 提取: LargeCardModeContent(), FlatCardModeContent()
- 保留: ProjectCard() 主函数和 SwipeToDismissBoxWrapper() 在原文件

**新文件**: `app/src/main/kotlin/.../ui/components/ProjectCardMenu.kt`
- 提取: 下拉菜单逻辑（DropdownMenu + 所有DropdownMenuItem）
- 包括: 重命名、复制路径、删除、备份、保存模板、设置封面、管理标签、添加桌面、插件菜单

#### 2. HomeScreen.kt 拆分（2939行 → 多个文件）
**新文件**: `app/src/main/kotlin/.../ui/home/HomeScreenDialogs.kt`
- 提取所有对话框:
  - 新建分类对话框
  - 分类管理对话框
  - 标签管理对话框
  - 封面设置对话框
  - 备份/恢复对话框
  - 删除确认对话框
  - 重命名对话框
  - 标签选择对话框
  - 批量操作对话框
  - 目录配置对话框

**新文件**: `app/src/main/kotlin/.../ui/home/HomeScreenSections.kt`
- 提取独立UI区块:
  - 最近项目栏（RecentProjectsBar）
  - 标签筛选栏（TagFilterBar）
  - 分类栏（CategoryBar）
  - 搜索栏（SearchBar）
  - 项目列表（ProjectList）
  - 多选操作栏（MultiSelectBar）
  - 首页顶部工具栏（HomeTopBar）

**新文件**: `app/src/main/kotlin/.../ui/home/HomeScreenState.kt`
- 提取 remember 状态定义和 derivedStateOf 计算
- 包括: 所有状态变量、筛选逻辑、排序逻辑、批量操作逻辑

**保留在原文件**: HomeScreen() 主函数（组合各组件）

#### 3. SettingsScreen.kt 拆分（1782行 → 多个文件）
**新文件**: `app/src/main/kotlin/.../ui/settings/SettingsSections.kt`
- 提取各设置区域为独立Composable:
  - 外观设置（主题、深色模式、暗色OLED、颜色）
  - 首页布局设置（布局模式、密度、分类栏位置、卡片圆角、路径显示、时间显示）
  - 编辑器设置（字体大小、行号、换行、自动保存等）
  - 构建设置（签名、包名格式等）
  - 备份设置（路径、自动备份）
  - 关于/版本信息

**新文件**: `app/src/main/kotlin/.../ui/settings/SettingsItems.kt`
- 提取通用设置项组件: SettingsListItem, SettingsSlider, SettingsChoiceChips

**保留在原文件**: SettingsScreen() 主函数和分类管理对话框

### 第三部分：插件系统全面扩展

#### 1. 补全未触发的编辑器事件
**文件**: `app/src/main/kotlin/.../ui/editor/CodeEditScreen.kt`
- 在文件打开时 fireEvent(PluginEvents.ON_FILE_OPEN, filePath)
- 在文件保存时 fireEvent(PluginEvents.ON_FILE_SAVE, filePath)
- 在文件关闭时 fireEvent(PluginEvents.ON_FILE_CLOSE, filePath)
- 在文本变化时（debounce 300ms）fireEvent(PluginEvents.ON_TEXT_CHANGED, filePath, content)
- 在编辑器初始化完成时 fireEvent(PluginEvents.ON_EDITOR_INIT, projectPath)
- 在编辑器关闭时 fireEvent(PluginEvents.ON_EDITOR_CLOSE, projectPath)

**文件**: `app/src/main/kotlin/.../plugin/bridge/PluginEvents.kt`
- 确认所有事件常量已正确定义

#### 2. 新增项目生命周期事件
**文件**: `app/src/main/kotlin/.../plugin/state/PluginEvents.kt` (state层)
**文件**: `app/src/main/kotlin/.../plugin/bridge/PluginEvents.kt` (bridge层)
- 添加事件常量:
  - ON_PROJECT_CREATE(projectId, projectName, projectPath)
  - ON_PROJECT_DELETE(projectId, projectName, projectPath)
  - ON_PROJECT_RENAME(projectId, oldName, newName, projectPath)
  - ON_PROJECT_OPEN(projectId, projectName, projectPath)
  - ON_PROJECT_BACKUP(projectId, backupPath, success)
  - ON_PROJECT_RESTORE(projectId, backupPath, success)
  - ON_NEW_PROJECT(projectName, projectPath, templateId)

**文件**: `app/src/main/kotlin/.../HomeScreen.kt`
- 在项目创建/删除/重命名/备份/恢复处添加 fireEvent 调用
- 在普通项目点击（非多选模式）触发 ON_PROJECT_OPEN 事件

**文件**: `app/src/main/kotlin/.../ui/project/NewProjectScreen.kt`
- 在项目创建成功后触发 ON_NEW_PROJECT 事件

**文件**: `app/src/main/kotlin/.../MainActivity.kt`
- 在打开编辑器导航时触发 ON_PROJECT_OPEN 事件

#### 3. 新增设置变更事件
**文件**: `app/src/main/kotlin/.../plugin/bridge/PluginEvents.kt`
- 添加事件常量: ON_SETTINGS_CHANGED(changedFieldsJson)

**文件**: `app/src/main/kotlin/.../ui/settings/SettingsManager.kt`
- 在 saveSettings/updateSettings 成功后触发 ON_SETTINGS_CHANGED 事件
- 传递变更的字段名列表（或全部设置的JSON）

#### 4. 新增页面导航事件
**文件**: `app/src/main/kotlin/.../plugin/bridge/PluginEvents.kt`
- 添加事件常量: ON_PAGE_CHANGED(pageId, fromPageId)
- pageId: "main", "new_project", "editor", "settings", "about", "plugins", "webui"

**文件**: `app/src/main/kotlin/.../MainActivity.kt`
- 在 Crossfade targetScreen 变化时触发 ON_PAGE_CHANGED 事件

#### 5. 新增事件拦截/接管机制
**文件**: `app/src/main/kotlin/.../plugin/state/EventManager.kt`
- 改造事件系统，支持拦截:
  - 新增 `fireEventWithResult()` 方法，返回 Any? (插件可返回结果)
  - 新增 `registerInterceptor()` 方法，拦截器返回 true 表示阻止默认行为
  - 拦截器按优先级排序执行
  - 保持原有 `fireEvent()` 方法（纯通知，不拦截）

**文件**: `app/src/main/kotlin/.../plugin/api/IPluginBridgeEvents.kt`
- 添加 API 方法:
  - `on(eventName, handler)` → 普通监听（不拦截）
  - `intercept(eventName, priority, handler)` → 拦截器（返回true阻止默认行为）
  - `once(eventName, handler)` → 一次性监听

**文件**: `app/src/main/kotlin/.../plugin/bridge/PluginEvents.kt` (bridge实现)
- 实现 intercept() 和 fireEventWithResult() 桥接到 EventManager

**关键拦截点**（在HomeScreen/MainActivity中实现）:
- 项目点击: 插件可拦截，阻止打开编辑器
- 项目删除: 插件可拦截，阻止删除
- 返回键: 插件可拦截，阻止默认返回
- 文件保存: 插件可拦截
- 构建: 已有cancelBuild，统一到拦截机制

#### 6. 扩展UI挂载点
**文件**: `app/src/main/kotlin/.../plugin/api/IPluginBridgeUI.kt` (或新建IPluginBridgeMainPage)
- 添加首页扩展API:
  - `addHomeToolbarAction(icon, tooltip, onClick)` → 顶部工具栏添加按钮
  - `addHomeFab(icon, tooltip, onClick)` → 添加FAB
  - `addCategoryBarItem(name, icon, onClick)` → 分类栏添加自定义项目
  - `addSearchFilter(name, filterFn)` → 添加搜索过滤器
  - `addProjectCardOverlay(projectId, content)` → 项目卡片叠加内容

**文件**: `app/src/main/kotlin/.../plugin/state/UIState.kt`
- 添加状态容器存储上述扩展点

**文件**: `app/src/main/kotlin/.../HomeScreen.kt`
- 在对应位置渲染插件注册的UI扩展

#### 7. 完善PluginMainPage API
**文件**: `app/src/main/kotlin/.../plugin/bridge/PluginMainPage.kt`
- 添加方法:
  - `navigateToProject(projectId)` → 程序化打开项目
  - `refreshProjects()` → 刷新项目列表
  - `showToast(message)` → 显示Toast
  - `getSelectedProjectIds()` → 获取多选选中项
  - `setMultiSelectMode(enabled)` → 设置多选模式
  - `openProjectContextMenu(projectId)` → 触发项目卡片菜单

#### 8. Java 调用方式文档和示例
**文件**: `app/src/main/assets/plugin/templates/hello_plugin/main.lua`
- 在示例中明确添加 Java 反射调用注释和示例:
  ```lua
  -- Java 反射调用注意：使用 . 调用方法，不要用 :
  -- 错误: local result = obj:someMethod(arg)
  -- 正确: local result = obj.someMethod(arg)
  ```
- 添加静态方法调用、实例创建、字段访问的完整示例

**新文件**: `app/src/main/assets/plugin/templates/java_api_demo/`
- 创建专门的 Java 反射 API 示例插件
- 包含: 静态字段访问、静态方法调用、实例创建、实例字段读写、实例方法调用、数组操作、异常处理
- manifest.json + main.lua

**新文件**: `app/src/main/assets/plugin/templates/event_intercept_demo/`
- 创建事件拦截示例插件
- 演示: 监听项目点击事件、拦截项目删除、拦截返回键、自定义事件触发

**新文件**: `app/src/main/assets/plugin/templates/home_extension_demo/`
- 创建首页扩展示例插件
- 演示: 添加工具栏按钮、添加FAB、添加项目卡片角标、添加搜索过滤器

#### 9. 事件生命周期完善
**文件**: `app/src/main/kotlin/.../MainActivity.kt`
- 确保 onAppStart/onAppResume/onAppPause/onAppStop 在正确的生命周期回调中触发
- 添加 ON_APP_DESTROY 事件

**文件**: `app/src/main/kotlin/.../plugin/PluginManager.kt`
- 在插件加载/卸载/启用/禁用时确保事件正确触发
- 添加 ON_PLUGIN_INSTALL/ON_PLUGIN_UNINSTALL 事件

## Assumptions & Decisions

1. **启动报错修复策略**: 优先检查 Android Studio 运行配置，如果是 IDE 问题则不修改代码；如果是 Manifest 配置问题，则修复 alias 配置。预计是 Android Studio 运行配置选择了 alias 名称作为 Launch Activity，需要引导用户选择正确的 Activity 或在 build.gradle 中配置。

2. **解耦策略**: 不改变任何功能行为，仅将大函数和大文件拆分为更小的、职责单一的 Composable 和工具函数。所有 public API（函数签名）保持不变。

3. **事件拦截机制**: 采用优先级拦截模式（类似OkHttp Interceptor），拦截器返回 `true` 表示消费事件（阻止后续拦截器和默认行为），返回 `false` 表示继续传递。默认行为在所有拦截器之后执行。

4. **插件向后兼容**: 所有新增API为additive变更，不破坏现有插件。新的事件为新增常量，不影响现有事件监听。新的拦截API为新方法，不影响现有 on() 调用。

5. **Java 调用约定**: LuaJava 中调用 Java 方法统一使用 `.` 而非 `:`，因为桥接对象是 UserData 而非普通 Lua table。在文档和示例中明确标注。

6. **封面实现**: 纯色封面作为Card背景色，图片封面作为Card内容的Box背景层，文字叠放在遮罩之上。

## Verification Steps

1. **编译验证**: 执行 `./gradlew :app:compileDebugKotlin` 确保0错误
2. **启动验证**: Android Studio 中运行app，确认不出现 "Activity class does not exist" 错误
3. **UI功能验证**:
   - 开启"显示项目路径"后卡片显示路径，路径使用/sdcard/简写
   - 封面设置后整个卡片背景变化（纯色/图片+透明度）
   - Debug/Release角标始终显示，Release为绿色，点击切换
   - 最近项目卡片大小可调整，名称完整显示
   - 分类栏位置切换实时生效
   - 设置无分类不崩溃
   - 首页布局展开时有眼睛FAB，长按预览
4. **解耦验证**: 所有功能与解耦前一致，无回归
5. **插件系统验证**:
   - 安装hello_plugin，所有示例API正常工作
   - 安装event_intercept_demo，拦截功能正常
   - 安装home_extension_demo，首页扩展点正常渲染
   - onProjectOpen/create/delete等新事件正确触发
   - 编辑器事件（打开/保存/关闭/文本变化）正确触发
   - 设置变更事件正确触发
   - Java反射示例使用 `.` 调用正常工作
