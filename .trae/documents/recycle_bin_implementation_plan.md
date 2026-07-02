# 回收站功能补全实现计划

## 摘要

用户需求：批量删除需确认对话框、支持撤回操作、回收站机制、7天自动清理、索引文件记录、启动时最快算法扫描清理过期项目。

当前状态：核心管理器 `RecycleBinManager.kt` 和回收站页面 `TrashScreen.kt` 已创建完成，`HomeScreen.kt` 中的删除确认对话框和 Snackbar 撤销功能已实现，`SplashWelcome.kt` 启动清理代码已写好但引用了不存在的设置字段导致编译错误。需要补全 3 处缺口并编译验证。

---

## 当前状态分析

### 已完成的文件/功能

| 文件 | 状态 | 说明 |
|------|------|------|
| `utils/RecycleBinManager.kt` | ✅ 已完成 | moveToTrash/restoreFromTrash/permanentlyDelete/clearExpired/clearAll、JSON索引原子读写、元数据快照保存恢复、O(n)索引扫描清理 |
| `ui/trash/TrashScreen.kt` | ✅ 已完成 | 回收站列表、恢复/永久删除/清空操作、确认对话框、空状态、时间/大小显示 |
| `HomeScreen.kt` | ✅ 已完成 | 单个删除确认、批量删除确认、Snackbar撤销(lastDeletedTrashIds)、TRASH内容分支、moveProjectToTrash函数 |
| `MainActivity.kt` | ✅ 已完成 | MainContentType.TRASH枚举已存在 |
| `SplashWelcome.kt` | ⚠️ 部分完成 | 启动清理逻辑已写，但引用`trashRetentionDays`字段不存在，导致编译错误 |

### 需要补全的缺口

| 缺口 | 文件 | 影响 |
|------|------|------|
| 侧滑栏缺少回收站导航入口 | `AppDrawer.kt` | 用户无法进入回收站页面 |
| SettingsData缺少trashRetentionDays字段 | `ui/settings/SettingsManager.kt` | SplashWelcome引用编译失败，无法配置保留天数 |
| 设置页面缺少回收站设置项和入口 | `ui/settings/SettingsScreen.kt` | 用户无法调整保留天数、无法从设置进入回收站 |

---

## 具体修改方案

### 1. SettingsManager.kt - 添加trashRetentionDays设置字段

**位置**: `e:\Soft\Proje\LXC-LUA\app\src\main\kotlin\com\luaforge\studio\lxclua\ui\settings\SettingsManager.kt`

**修改内容**:

1. **PreferencesKeys**（第143行之后）添加:
   ```kotlin
   val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
   ```

2. **SettingsData数据类**（第1095行之后，即`recentCardWidthDp`字段后面）添加:
   ```kotlin
   val trashRetentionDays: Int = 7,  // 回收站保留天数，默认7天，范围3-30天
   ```

3. **loadSavedSettings函数**（第507行附近，读取`recentCardWidthDpRaw`之后）添加读取:
   ```kotlin
   val trashRetentionDays = (preferences[PreferencesKeys.TRASH_RETENTION_DAYS] ?: 7).coerceIn(3, 30)
   ```

4. **SettingsData构造调用**（第584行之后，即`recentCardWidthDp = recentCardWidthDp`后面）添加:
   ```kotlin
   trashRetentionDays = trashRetentionDays,
   ```

5. **saveSettingsAsync函数**（第693行之后，即保存`recentCardWidthDp`之后）添加写入:
   ```kotlin
   preferences[PreferencesKeys.TRASH_RETENTION_DAYS] = currentSettings.trashRetentionDays
   ```

**为什么这样做**: 使用intPreferencesKey存储天数，默认值7天，加载时coerceIn(3,30)防止非法值。与现有设置项保持一致的读写模式。

---

### 2. AppDrawer.kt - 添加侧滑栏回收站导航入口

**位置**: `e:\Soft\Proje\LXC-LUA\app\src\main\kotlin\com\luaforge\studio\lxclua\AppDrawer.kt`

**修改内容**:

在"项目分组"和"设置分组"之间（第163行`BaseDrawerItem`（PROJECTS）结束之后，第168行设置分组之前），添加回收站入口:

```kotlin
// ============ 回收站 ============
BaseDrawerItem(
    label = "回收站",
    icon = Icons.Filled.Delete,
    iconContentDescription = "回收站",
    selected = currentContentType == MainContentType.TRASH,
    onClick = { onContentTypeChange(MainContentType.TRASH) }
)
```

同时需要在文件顶部import中确认 `Icons.Filled.Delete` 已可用（当前第39行已导入`Icons.Filled.*`，无需额外导入）。

**为什么这样做**: 回收站是项目管理相关功能，放在"项目"分组之后、"设置"之前，用户容易找到。使用Delete图标符合回收站语义。TrashScreen的onBack回调已在HomeScreen中正确处理（切回PROJECTS）。

---

### 3. SettingsScreen.kt - 添加回收站设置区域

**位置**: `e:\Soft\Proje\LXC-LUA\app\src\main\kotlin\com\luaforge\studio\lxclua\ui\settings\SettingsScreen.kt`

**修改内容**:

在设置页面的适当位置（建议在"通用"或"项目管理"相关区域末尾，或在备份设置附近）添加一个设置分组:

```kotlin
// ============ 回收站设置 ============
SettingsSection(title = "回收站") {
    // 打开回收站入口
    SettingsClickableRow(
        icon = Icons.Filled.Delete,
        title = "打开回收站",
        subtitle = "查看和恢复已删除的项目",
        onClick = { onContentTypeChange(MainContentType.TRASH) }
    )
    
    // 保留天数选择
    var showDaysDialog by remember { mutableStateOf(false) }
    val retentionDays = settings.trashRetentionDays
    SettingsClickableRow(
        icon = Icons.Filled.Schedule,
        title = "保留天数",
        subtitle = "删除的项目将保留${retentionDays}天后自动清理",
        onClick = { showDaysDialog = true }
    )
    
    if (showDaysDialog) {
        val daysOptions = listOf(3, 7, 14, 30)
        AlertDialog(
            onDismissRequest = { showDaysDialog = false },
            title = { Text("回收站保留天数") },
            text = {
                Column {
                    daysOptions.forEach { days ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    updateSettings(settings.copy(trashRetentionDays = days))
                                    showDaysDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = retentionDays == days,
                                onClick = {
                                    updateSettings(settings.copy(trashRetentionDays = days))
                                    showDaysDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${days}天")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDaysDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
```

需要确认:
- `SettingsSection`和`SettingsClickableRow`是SettingsScreen中已有的可复用组件
- `onContentTypeChange`回调需要从SettingsScreen的参数传递进来（检查当前签名，如果没有则需要添加lambda参数）
- Icons.Filled.Schedule需要确认是否已导入（如果没有，使用Icons.Filled.AccessTime或其他时间图标）

**为什么这样做**: 让用户可以直接从设置页进入回收站，并调整保留天数。提供3/7/14/30天四个预设选项，简单直观。默认7天。

---

### 4. HomeScreen.kt - 确认TrashScreen的onBack回调

**位置**: `e:\Soft\Proje\LXC-LUA\app\src\main\kotlin\com\luaforge\studio\lxclua\HomeScreen.kt`

**检查内容**: 确认第1754-1760行附近TRASH分支的TrashScreen调用中onBack正确切换回PROJECTS:
```kotlin
MainContentType.TRASH -> TrashScreen(
    onBack = { currentContentType = MainContentType.PROJECTS }
)
```
如果已正确则无需修改。

---

### 5. 插件事件扩展（可选，Phase 5）

在PluginEvents中添加回收站相关事件，供插件监听:
- `ON_PROJECT_TRASHED` - 项目移入回收站
- `ON_PROJECT_RESTORED` - 项目从回收站恢复
- `ON_TRASH_CLEARED` - 回收站清空

注：当前代码中已使用字符串事件名（"onProjectTrashed"、"onProjectRestored"、"onTrashCleared"）通过EventManager.fireEvent触发，可保持现状无需额外修改。若需要在PluginEvents中定义常量可后续补充。

---

## 算法说明（启动清理）

`RecycleBinManager.clearExpired()` 已采用最优算法:
1. **不遍历文件系统目录** - 直接读取内存中的索引列表（O(n)，n=回收站项目数）
2. **只过滤过期条目** - `(now - deletedAt) > expireTime` 毫秒级比较
3. **批量删除后原子写回索引** - 先收集所有过期ID，删除文件，最后一次性saveIndex
4. **启动时在IO线程执行** - 不阻塞主线程和其他初始化
5. **索引文件原子写入** - 先写.tmp临时文件，再rename替换，防止写入中途崩溃导致索引损坏

---

## 假设与决策

1. **保留天数范围**: 3-30天，默认7天。太短容易误删无法恢复，太长占用存储空间。
2. **回收站位置**: `/sdcard/LXC-LUA/trash/`，索引文件`.trash_index.json`（隐藏文件）放在trash目录内。
3. **恢复冲突处理**: 原路径存在同名项目时，自动添加"_恢复_时间戳"后缀，不覆盖现有项目。
4. **移动操作优先使用renameTo**: 同分区renameTo是原子操作，极快且安全；跨分区时fallback到copy+delete。
5. **撤销Snackbar时长**: 使用默认Snackbar时长（约4秒），用户在此时间内可点击"撤销"恢复刚删除的项目。
6. **不添加定时任务**: 用户要求"软件刚打开时扫一遍"，不使用WorkManager等定时调度，仅在启动时清理。这是最快且符合需求的方案。

---

## 验证步骤

1. 编译项目，确认无编译错误（重点检查SettingsManager中trashRetentionDays字段的完整链路）
2. 侧滑栏验证：打开侧滑栏，看到"回收站"入口，点击进入TrashScreen
3. 删除验证：
   - 单个项目删除：弹出确认对话框 → 确认后项目移入回收站 → 底部显示Snackbar"已移入回收站"+撤销按钮
   - 批量删除：多选模式下点击删除 → 弹出批量确认（显示数量）→ 确认后全部移入 → Snackbar显示数量+撤销
   - 撤销操作：点击Snackbar撤销按钮 → 项目立即恢复到原位置
4. 回收站页面验证：
   - 显示已删除项目列表（名称、原路径、删除时间、大小）
   - 点击恢复按钮 → 项目恢复并从列表消失
   - 点击删除按钮 → 弹出永久删除确认 → 确认后永久删除
   - 点击清空按钮 → 弹出清空确认 → 确认后全部删除
5. 设置页面验证：
   - "打开回收站"入口可点击跳转
   - "保留天数"可选择3/7/14/30天
6. 过期清理验证：
   - 手动修改索引文件中某个条目的deletedAt为8天前的时间戳 → 重启应用 → 该项目被自动清理（查看Logcat输出）
