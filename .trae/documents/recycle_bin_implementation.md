# 回收站机制 + 删除确认 + 撤回实现计划

## Summary

为 LXC-LUA 项目实现回收站机制，替代当前的永久删除：
1. 批量删除增加二次确认对话框
2. 删除时将项目移入回收站而非永久删除，支持Snackbar即时撤回
3. 回收站项目记录在索引文件中，保留7天
4. 应用启动时快速扫描清理过期回收站项目（O(n)索引扫描，不遍历目录）
5. 回收站入口在侧滑栏，支持恢复/永久删除/清空

## Current State Analysis

### 现有删除流程
- **单个删除**：弹出AlertDialog确认 → 加入`pendingDeletionIds`驱动动画 → 300ms后`performDelete()` → `deleteRecursively()`永久删除磁盘目录 → 清理SettingsManager中的元数据（置顶/最近/标签/封面/排序/分类） → 刷新列表
- **批量删除**：多选模式下点击删除图标，直接遍历ID逐个`performDelete()`，**无确认对话框**
- **无撤回机制**：`pendingDeletionIds`仅用于动画，不是撤销窗口
- **无回收站**：删除不可逆，磁盘数据立即销毁

### 关键发现
- 项目以**目录名作为ID**，目录位于`/sdcard/LXC-LUA/project/`（可配置附加路径）
- 设置使用DataStore持久化，并双写到SD卡`/sdcard/LXC-LUA/config/app_settings.json`
- 备份目录在`/sdcard/LXC-LUA/backups/`，使用ZIP格式
- 启动流程：`SplashWelcome` → 加载设置/资源解压/编辑器状态清理 → `MainActivity.onCreate` → 插件系统初始化 → `ON_APP_START`事件 → 加载项目列表
- 项目列表本身**不持久化**，每次启动通过`ProjectUtil.loadProjectsFromDirectories()`扫描磁盘生成
- 元数据（pinned/recent/tags/cover/order/categories）存在SettingsManager的JSON字段中

## Proposed Changes

### Phase 1: 回收站核心管理器（新建文件）

**新建文件**: `app/src/main/kotlin/com/luaforge/studio/lxclua/utils/RecycleBinManager.kt`

设计为`object`单例，所有方法主线程安全，IO操作在Dispatchers.IO执行。

**数据结构**：
```kotlin
// 回收站条目
data class TrashItem(
    val trashId: String,           // 回收站唯一ID = "{originalId}_{deleteTimestamp}"
    val originalId: String,       // 原始项目ID（目录名）
    val originalName: String,     // 原始项目名称
    val originalPath: String,     // 原始完整路径
    val trashPath: String,        // 回收站中路径
    val deletedAt: Long,          // 删除时间戳（毫秒）
    val sizeBytes: Long,          // 项目大小（字节，用于显示）
    // 元数据快照（用于恢复）
    val wasPinned: Boolean,
    val wasRecent: Boolean,       // 是否在最近列表中
    val tags: Set<String>,
    val coverData: String?,       // 封面JSON序列化
    val customOrderIndex: Int,    // -1表示不在自定义排序中
    val categoryId: String?       // 所属分类ID
)
```

**索引文件**：`/sdcard/LXC-LUA/trash/.trash_index.json`
- JSON数组，所有TrashItem序列化存储
- 每次移入/恢复/清空时原子写入（先写临时文件再rename）
- 避免每次启动遍历回收站目录

**核心方法**：
- `suspend fun moveToTrash(project: ProjectItem, context: Context): TrashItem?`
  - 在IO线程执行：创建trash目录（如不存在）→ 生成trashId → 移动目录到trash（`File.renameTo`优先，跨分区则copy+delete）→ 记录元数据快照 → 更新索引文件
- `suspend fun restoreFromTrash(trashId: String, context: Context): Boolean`
  - IO线程：查索引 → 从trash移回原路径（若原路径存在则重命名为`{name}_restored_{timestamp}`）→ 恢复元数据到SettingsManager → 更新索引
- `suspend fun permanentlyDelete(trashId: String, context: Context): Boolean`
  - IO线程：`deleteRecursively()`删除trash中目录 → 从索引移除
- `suspend fun clearExpired(days: Int = 7, context: Context): List<String>`
  - IO线程：遍历索引（最快算法，不读目录）→ 删除`deletedAt < now - 7天`的项目目录 → 返回已清理的trashId列表
- `suspend fun clearAll(context: Context)`
  - 清空回收站
- `fun getTrashItems(): List<TrashItem>`
  - 返回内存中缓存的回收站列表（启动时加载）
- `fun getTrashItem(trashId: String): TrashItem?`
- `suspend fun loadIndex(context: Context)`
  - 从索引文件加载到内存缓存（启动时调用一次）

**回收站目录**：`/sdcard/LXC-LUA/trash/`
- 每个项目目录命名为`{originalId}_{timestamp}`
- `.trash_index.json`为隐藏索引文件

### Phase 2: 修改performDelete使用回收站

**修改文件**: `app/src/main/kotlin/com/luaforge/studio/lxclua/HomeScreen.kt`

**performDelete函数改造**：
- 将`deleteRecursively()`替换为`RecycleBinManager.moveToTrash()`
- 不立即从SettingsManager中清除元数据（因为RecycleBinManager会保存元数据快照，从SettingsManager中移除是必要的，让项目从列表消失）
- 删除后显示带"撤销"按钮的Snackbar（用Scaffold的SnackbarHost实现，或用现有toast扩展）
- 撤销操作：从回收站恢复项目，刷新列表

**Snackbar实现**：
- 在HomeScreen的Scaffold中添加`SnackbarHostState`
- 删除成功后`scope.launch { snackbarHostState.showSnackbar(...) }`显示"已移入回收站" + "撤销"action
- 撤销时调用`RecycleBinManager.restoreFromTrash()` + 刷新列表

**批量删除确认对话框**：
- 在多选底部栏删除按钮点击时，弹出AlertDialog："确定要将N个项目移入回收站吗？"
- 确认后才执行批量移入回收站
- 批量删除的撤销：记录所有trashId，撤销时批量恢复
- 批量删除后同样显示Snackbar（"已将N个项目移入回收站"+"撤销"）

**单个删除对话框文本修改**：
- 将"删除项目"改为"移入回收站"
- 文案改为"项目将移入回收站，7天后自动删除"

### Phase 3: 启动时过期清理

**修改文件**: `app/src/main/kotlin/com/luaforge/studio/lxclua/SplashWelcome.kt` 或 `app/src/main/kotlin/com/luaforge/studio/lxclua/MainActivity.kt`

在现有启动协程中（SplashWelcome的`handleSplashLogic`已有并行IO协程），添加：
```kotlin
// 清理过期回收站项目（7天）
launch(Dispatchers.IO) {
    try {
        RecycleBinManager.loadIndex(this@SplashWelcome)
        val cleared = RecycleBinManager.clearExpired(7, this@SplashWelcome)
        if (cleared.isNotEmpty()) {
            LogCatcher.i("RecycleBin", "启动时清理了${cleared.size}个过期回收站项目")
        }
    } catch (e: Exception) {
        LogCatcher.e("RecycleBin", "启动清理回收站失败", e)
    }
}
```

算法说明（最快）：
- 直接读JSON索引文件（内存操作）→ 遍历计算`now - deletedAt > 7天`→ 对每个过期项删除对应目录 → 更新索引
- 时间复杂度O(n)，n为回收站条目数（通常很少）
- 不遍历文件系统目录

### Phase 4: 回收站页面UI

**新建文件**: `app/src/main/kotlin/com/luaforge/studio/lxclua/ui/trash/TrashScreen.kt`

Compose页面，内容：
- TopAppBar标题"回收站"，返回按钮，"清空"按钮（带确认）
- 空状态："回收站为空，删除的项目会在这里保留7天"
- LazyColumn列表：每个TrashItem卡片显示
  - 项目名称
  - 删除时间（"X天前"格式）
  - 原始路径
  - 项目大小（格式化显示）
  - "恢复"按钮（IconButton）
  - "永久删除"按钮（IconButton，带二次确认）
- 底部统计："共N个项目，占用XXX空间，7天后自动清理"

**入口添加**：在侧滑栏（NavigationState/PluginNav相关）添加"回收站"菜单项，在"插件管理"下方，使用Delete/DeleteSweep图标，点击导航到TrashScreen

**导航扩展**：MainApp的currentScreen枚举添加`TRASH`，或使用现有的导航机制。参考设置/关于/插件管理页面的导航方式。

### Phase 5: 插件事件与API扩展

**事件扩展**（在`PluginEvents.kt`中新增）：
- `ON_PROJECT_TRASHED` 参数: (projectId, projectName, trashId, originalPath)
- `ON_PROJECT_RESTORED` 参数: (trashId, projectId, projectName, restoredPath)
- `ON_TRASH_CLEARED` 参数: (count)

**PluginMainPage新增API**（给插件访问）：
- `getTrashItems(): List<Map<String, Any?>>` 返回回收站列表
- `restoreFromTrash(trashId: String): Boolean`
- `permanentlyDelete(trashId: String): Boolean`
- `emptyTrash()`

**UI扩展点新增**（`UIExtensionPoints.kt`）：
- `TRASH_TOOLBAR_END` 回收站工具栏右侧
- `TRASH_LIST_ITEM_ACTIONS` 回收站项目卡片操作区

### Phase 6: 设置项

**SettingsScreen.kt** 添加：
- 在"项目存储路径"附近增加"回收站"设置项
  - 回收站保留天数：默认7天，可调整3-30天Slider
  - "打开回收站"快捷按钮（跳转到TrashScreen）
  - "清空回收站"按钮（带确认）

**SettingsManager.kt** 新增：
- `TRASH_RETENTION_DAYS` 偏好，默认7天
- `SettingsData.trashRetentionDays: Int = 7`

## Assumptions & Decisions

1. **移动而非压缩**：回收站直接move目录，不做ZIP压缩。优点是恢复极快（rename），缺点是占用空间。考虑到用户可能随时恢复，且7天自动清理，空间影响可控。
2. **renameTo优先**：同一文件系统分区内使用`File.renameTo()`（原子操作，极快），跨分区才copy+delete。因为trash目录和项目目录都在`/sdcard/LXC-LUA/`下，同一分区，renameTo必定成功。
3. **索引文件而非目录扫描**：`.trash_index.json`记录所有回收站条目，启动清理时O(n)内存操作，不遍历文件系统，最快。
4. **批量撤销**：批量删除记录所有trashId，Snackbar期间可一键撤销全部；Snackbar超时后（如4秒）不可撤销（但可在回收站页面恢复）。
5. **元数据快照恢复**：删除时保存pinned/tags/cover/recent/order/category快照到TrashItem，恢复时原样还原。若恢复时原分类已删除，则不恢复分类关联。
6. **路径冲突处理**：恢复时若原路径已存在同名项目（用户在删除后又新建了同名项目），恢复的项目重命名为`{name}_恢复_{时间戳}`。
7. **不自动备份ZIP**：因为回收站本身保留了完整项目目录，不需要额外ZIP备份。
8. **附加路径项目**：回收站统一放在`/sdcard/LXC-LUA/trash/`，无论项目来自主目录还是附加路径。恢复时若原路径父目录不存在，回退到主项目目录。
9. **启动清理位置**：放在SplashWelcome的IO协程中，不阻塞UI，用户无感知。
10. **Snackbar使用Compose原生**：使用`Scaffold`的`snackbarHost`，不引入额外依赖。

## Verification Steps

1. **单个删除**：删除项目 → 项目从列表消失 → 底部出现"已移入回收站"+"撤销" → 点击撤销 → 项目重新出现，元数据（置顶/标签/封面/分类）完整保留
2. **批量删除**：多选3个项目 → 点击删除 → 弹出确认对话框显示"确定要将3个项目移入回收站吗？" → 确认 → 项目消失 → Snackbar"已将3个项目移入回收站"+"撤销" → 撤销后全部恢复
3. **撤销超时**：删除后不点击撤销 → 等待Snackbar消失 → 项目不在列表中 → 打开回收站 → 项目在回收站列表
4. **回收站页面**：侧滑栏进入回收站 → 显示已删除项目（名称/删除时间/大小）→ 点击"恢复"→ 项目回到列表 → 点击"永久删除"→ 项目消失并永久删除
5. **7天自动清理**：修改系统时间或手动修改索引中`deletedAt`为8天前 → 冷启动App → 回收站中过期项目被自动清理
6. **启动速度**：回收站有50个项目时启动无明显延迟（索引JSON < 100KB，清理操作在IO线程）
7. **路径冲突**：删除项目 → 在原位置新建同名项目 → 从回收站恢复 → 恢复的项目命名为`{name}_恢复_{时间戳}`
8. **附加路径项目**：删除附加路径下的项目 → 进入回收站恢复 → 恢复到原路径
9. **清空回收站**：回收站页面点击"清空" → 确认 → 所有项目永久删除，索引清空
10. **插件事件**：安装测试插件监听`ON_PROJECT_TRASHED`/`ON_PROJECT_RESTORED`事件 → 删除/恢复时事件正确触发
