package com.luaforge.studio.lxclua

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.app.LocaleManager
import android.os.LocaleList
import androidx.core.content.getSystemService
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.SubcomposeAsyncImage
import com.luaforge.studio.lxclua.plugin.PluginManager
import com.luaforge.studio.lxclua.plugin.api.callbacks.IPluginEventListener
import com.luaforge.studio.lxclua.plugin.bridge.PluginNavigation
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.NavigationState
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import com.luaforge.studio.lxclua.plugin.state.SidebarItem
import com.luaforge.studio.lxclua.plugin.state.UIExtensionPoints
import com.luaforge.studio.lxclua.plugin.state.UIState
import com.luaforge.studio.lxclua.utils.getIconByName
import com.luaforge.studio.lxclua.ui.editor.persistence.EditorStateUtil
import com.luaforge.studio.lxclua.ui.about.AboutScreen
import com.luaforge.studio.lxclua.ui.components.FilePickerDialog
import com.luaforge.studio.lxclua.ui.components.SelectionMode
import com.luaforge.studio.lxclua.ui.components.Toast
import com.luaforge.studio.lxclua.ui.editor.CodeEditScreen
import com.luaforge.studio.lxclua.ui.project.NewProjectScreen
import com.luaforge.studio.lxclua.ui.settings.DarkMode
import com.luaforge.studio.lxclua.ui.settings.HomeLayoutMode
import com.luaforge.studio.lxclua.ui.settings.HomeDensity
import com.luaforge.studio.lxclua.ui.settings.CategoryBarPosition
import com.luaforge.studio.lxclua.ui.settings.ProjectCategory
import com.luaforge.studio.lxclua.ui.settings.ProjectTag
import com.luaforge.studio.lxclua.ui.settings.ProjectCover
import com.luaforge.studio.lxclua.ui.settings.CoverType
import com.luaforge.studio.lxclua.ui.settings.SettingsManager
import com.luaforge.studio.lxclua.ui.settings.SettingsScreen
import com.luaforge.studio.lxclua.ui.settings.SortOrder
import com.luaforge.studio.lxclua.ui.settings.ToastPosition
import com.luaforge.studio.lxclua.ui.theme.AppThemeWithObserver
import com.luaforge.studio.lxclua.ui.welcome.TransparentSystemBars
import com.luaforge.studio.lxclua.ui.welcome.WelcomeScreen
import com.luaforge.studio.lxclua.ui.welcome.saveWelcomeCompleted
import com.luaforge.studio.lxclua.ui.welcome.shouldShowWelcomeScreen
import com.luaforge.studio.lxclua.utils.*
import io.github.tarifchakder.ktoast.ToastData
import io.github.tarifchakder.ktoast.ToastHost
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// 搜索时间范围枚举
enum class SearchTimeRange {
    ALL, TODAY, THIS_WEEK, THIS_MONTH
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onNavigateToNewProject: () -> Unit,
    onNavigateToEditor: (ProjectItem) -> Unit,
    projectItems: List<ProjectItem>,
    onProjectItemsChanged: (List<ProjectItem>) -> Unit,
    toast: NonBlockingToastState,
    allProjectPaths: List<String>,
    primaryProjectsPath: String,
    onRefreshProjects: suspend () -> Unit
) {

    val packageInfo = AppInfoUtil.getPackageInfo()
    val appVersionName = packageInfo?.versionName ?: "1.0.0"
    packageInfo?.versionCode ?: 1
    val copyrightYear = BuildConfig.COPYRIGHT_YEAR

    var currentContentType by remember { mutableStateOf(MainContentType.PROJECTS) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsManager = SettingsManager
    val currentSettings = settingsManager.currentSettings
    // Snackbar用于删除撤销
    val snackbarHostState = remember { SnackbarHostState() }
    // 最近一次删除的trashId列表（用于撤销）
    val lastDeletedTrashIds = remember { mutableStateListOf<String>() }

    // ---- 配置项目目录状态 ----

    // 搜索相关状态
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // 排序和置顶状态（从设置中读取）
    var sortOrder by remember { mutableStateOf(currentSettings.sortOrder) }
    var pinnedSet by remember { mutableStateOf(currentSettings.pinnedProjects) }

    // 监听设置变化
    LaunchedEffect(currentSettings.sortOrder, currentSettings.pinnedProjects) {
        sortOrder = currentSettings.sortOrder
        pinnedSet = currentSettings.pinnedProjects
    }

    // 首页布局模式和最近项目
    val homeLayoutMode = currentSettings.homeLayoutMode
    val homeShowRecent = currentSettings.homeShowRecent
    val lastOpenedProjectId = currentSettings.lastOpenedProjectId
    val lastOpenedProject = remember(lastOpenedProjectId, projectItems) {
        if (lastOpenedProjectId.isNotEmpty()) projectItems.find { it.id == lastOpenedProjectId } else null
    }

    // 分类筛选
    var selectedCategoryId by remember { mutableStateOf("") }
    val homeCategories = currentSettings.homeCategories
    val homeCategoryEnabled = currentSettings.homeCategoryEnabled
    // 分类管理对话框
    var showCategoryManager by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<ProjectCategory?>(null) }
    // 项目分类选择对话框
    var categoryPickerProject by remember { mutableStateOf<ProjectItem?>(null) }

    // ---- 标签相关 ----
    val homeProjectTags = currentSettings.homeProjectTags
    val projectTagsMap = currentSettings.projectTagsMap
    var selectedTagIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTagManager by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<ProjectTag?>(null) }
    var tagPickerProject by remember { mutableStateOf<ProjectItem?>(null) }
    // ----------------

    // ---- 首页显示设置 ----
    val homeDensity = currentSettings.homeDensity
    val cardCornerRadius = when (currentSettings.cardCornerRadius) {
        0 -> 8.dp
        2 -> 20.dp
        else -> 12.dp
    }
    val showModifiedTime = currentSettings.showProjectModifiedTime
    val showPath = currentSettings.showProjectPath
    val showTagFilterBar = currentSettings.showTagFilterBar
    val showRecentProjectsBar = currentSettings.showRecentProjectsBar
    val enableSwipeGesture = currentSettings.enableSwipeGesture
    // Bug6修复：分类栏位置直接在使用处读取currentSettings.categoryBarPosition，
    // 确保状态变化时Compose能正确追踪订阅并触发重组
    // 最近项目卡片宽度（优先使用自定义dp值，回退到旧枚举映射）
    val recentCardWidthDp = currentSettings.recentCardWidthDp.coerceIn(80, 240).dp
    // 最近项目列表
    val recentProjectItems = remember(currentSettings.recentProjects, projectItems) {
        currentSettings.recentProjects.mapNotNull { pid -> projectItems.find { it.id == pid } }.take(5)
    }
    // 封面映射
    val projectCoverMap = currentSettings.projectCoverMap
    // ----------------

    // ---- 搜索过滤 ----
    var searchFilterCategory by remember { mutableStateOf<String?>(null) }
    var searchFilterTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchTimeRange by remember { mutableStateOf(SearchTimeRange.ALL) }
    var showSearchFilter by remember { mutableStateOf(false) }
    // ----------------

    // ---- 备份/还原 ----
    var showBackupProject by remember { mutableStateOf<ProjectItem?>(null) }
    var showRestorePicker by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    // ----------------

    // ---- 模板 ----
    var showSaveTemplate by remember { mutableStateOf<ProjectItem?>(null) }
    var templateNameInput by remember { mutableStateOf("") }
    // ----------------

    // ---- 封面设置 ----
    var showCoverEditor by remember { mutableStateOf<ProjectItem?>(null) }
    // ----------------

    // ---- 桌面快捷方式 ----
    var showShortcutConfirm by remember { mutableStateOf<ProjectItem?>(null) }
    // ----------------

    // ---- 批量操作 ----
    var showBatchCategory by remember { mutableStateOf(false) }
    var showBatchTags by remember { mutableStateOf(false) }
    var batchMessage by remember { mutableStateOf<String?>(null) }
    // ----------------

    // 更多菜单状态
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // ---- 导入源码相关状态 ----
    var showFilePicker by remember { mutableStateOf(false) }
    var selectedImportFile by remember { mutableStateOf<File?>(null) }
    var importSettingsData by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictLabel by remember { mutableStateOf("") }
    var conflictPath by remember { mutableStateOf("") }
    var conflictAction by remember { mutableStateOf<ConflictAction?>(null) }
    // --------------------------

    // ---- 配置项目目录状态 ----
    var showConfigDirDialog by remember { mutableStateOf(false) }
    var showDirPicker by remember { mutableStateOf(false) }
    var dirPickerTarget by remember { mutableStateOf(DirPickerTarget.PRIMARY) }
    var dirReplaceIndex by remember { mutableStateOf(-1) }
    // --------------------------

    var showDeleteDialog by remember { mutableStateOf(false) }
    // 批量删除确认对话框
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var deleteProjectId by remember { mutableStateOf("") }
    var deleteProjectName by remember { mutableStateOf("") }
    var deleteProjectPath by remember { mutableStateOf("") }

    // 用于处理从设置/关于页返回时的异步操作
    var shouldReturnToProjects by remember { mutableStateOf(false) }

    // 多选模式状态（从 PluginManager 读取）
    val isMultiSelectMode by remember { PluginManager.isMultiSelectMode }
    val multiSelectedProjectIds = PluginManager.multiSelectedProjectIds
    
    // 项目徽章和菜单扩展状态
    val projectBadges = PluginManager.projectBadges
    val projectCardMenuItems = PluginManager.projectCardMenuItems
    
    // 插件UI扩展状态：工具栏按钮、FAB按钮、分类栏项
    val pluginToolbarActions = UIState.toolbarActions
    val pluginHomeFabs = UIState.homeFabs
    val pluginCategoryBarItems = UIState.categoryBarItems
    
    // 监听插件刷新项目列表请求
    DisposableEffect(Unit) {
        val listener = object : IPluginEventListener {
            override fun onEvent(vararg args: Any?) {
                scope.launch { onRefreshProjects() }
            }
        }
        EventManager.registerEventListener("__home_screen__", "onRefreshProjects", listener)
        onDispose {
            EventManager.unregisterEventListener("onRefreshProjects", listener)
        }
    }
    
    // 同步项目列表到 PluginManager 供插件读取
    LaunchedEffect(projectItems) {
        PluginManager.currentProjectItems.clear()
        PluginManager.currentProjectItems.addAll(projectItems)
    }

    // 监听返回标志，处理从设置/关于页返回时的异步操作
    LaunchedEffect(shouldReturnToProjects) {
        if (shouldReturnToProjects) {
            // 先关闭抽屉（如果打开）
            if (drawerState.isOpen) {
                drawerState.close()
            }
            // 切换回项目页面
            currentContentType = MainContentType.PROJECTS
            shouldReturnToProjects = false
        }
    }

    LaunchedEffect(allProjectPaths) {
        onRefreshProjects()
    }

    LaunchedEffect(Unit) {
        onRefreshProjects()
    }

    // 搜索过滤后的项目列表（关键字+标签+时间范围）
    val filteredProjects by remember(projectItems, searchQuery, selectedTagIds, searchTimeRange, searchFilterTags, searchFilterCategory, projectTagsMap) {
        derivedStateOf {
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60 * 60 * 1000
            val timeThreshold = when (searchTimeRange) {
                SearchTimeRange.TODAY -> now - dayMs
                SearchTimeRange.THIS_WEEK -> now - 7 * dayMs
                SearchTimeRange.THIS_MONTH -> now - 30L * dayMs
                SearchTimeRange.ALL -> 0L
            }
            // 有效标签筛选（选中的标签+搜索过滤标签合并）
            val activeTagIds = selectedTagIds + searchFilterTags
            val activeCategoryId = searchFilterCategory ?: ""

            projectItems.filter { project ->
                // 关键字匹配
                val matchQuery = searchQuery.isBlank() ||
                        project.name.contains(searchQuery, ignoreCase = true) ||
                        project.path.contains(searchQuery, ignoreCase = true)
                // 时间范围匹配
                val matchTime = timeThreshold == 0L || project.modifiedDate.time >= timeThreshold
                // 标签匹配（AND关系：必须包含所有选中标签）
                val projectTagIds = projectTagsMap[project.id] ?: emptySet()
                val matchTags = activeTagIds.isEmpty() || projectTagIds.containsAll(activeTagIds)
                // 搜索分类匹配
                val matchCategory = activeCategoryId.isEmpty() || run {
                    val cat = homeCategories.find { it.id == activeCategoryId }
                    cat == null || project.id in cat.projectIds
                }
                matchQuery && matchTime && matchTags && matchCategory
            }
        }
    }

    // 分组并排序后的项目列表（包含分类筛选）
    val displayedProjects by remember(filteredProjects, sortOrder, pinnedSet, selectedCategoryId, homeCategories, homeCategoryEnabled, currentSettings.customProjectOrder) {
        derivedStateOf {
            // 分类筛选（关闭分类功能时不筛选）
            val categoryFiltered = if (!homeCategoryEnabled || selectedCategoryId.isEmpty()) {
                filteredProjects
            } else {
                val cat = homeCategories.find { it.id == selectedCategoryId }
                if (cat != null) {
                    filteredProjects.filter { it.id in cat.projectIds }
                } else {
                    filteredProjects
                }
            }
            // 分为两组：置顶和未置顶
            val pinned = categoryFiltered.filter { it.id in pinnedSet }
            val unpinned = categoryFiltered.filter { it.id !in pinnedSet }

            // 定义排序比较器
            val comparator: Comparator<ProjectItem> = when (sortOrder) {
                SortOrder.NAME_ASC -> compareBy { it.name.lowercase() }
                SortOrder.NAME_DESC -> compareByDescending { it.name.lowercase() }
                SortOrder.DATE_MODIFIED_NEWEST -> compareByDescending { it.modifiedDate }
                SortOrder.DATE_MODIFIED_OLDEST -> compareBy { it.modifiedDate }
                SortOrder.CUSTOM -> {
                    // 自定义排序：按customProjectOrder顺序，不在列表中的按修改时间降序
                    val orderMap = currentSettings.customProjectOrder
                        .mapIndexed { index, id -> id to index }.toMap()
                    Comparator { a, b ->
                        val aIdx = orderMap[a.id]
                        val bIdx = orderMap[b.id]
                        when {
                            aIdx != null && bIdx != null -> aIdx.compareTo(bIdx)
                            aIdx != null -> -1
                            bIdx != null -> 1
                            else -> b.modifiedDate.compareTo(a.modifiedDate)
                        }
                    }
                }
            }

            pinned.sortedWith(comparator) + unpinned.sortedWith(comparator)
        }
    }

    // 新增状态：待删除的项目ID集合（用于退出动画）
    var pendingDeletionIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val lazyListState = rememberLazyListState()
    var showExtendedFab by remember { mutableStateOf(true) }

    var isRefreshing by remember { mutableStateOf(false) }
    var visibleCount by remember { mutableIntStateOf(0) }


    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(
        lazyListState.firstVisibleItemIndex,
        lazyListState.firstVisibleItemScrollOffset
    ) {
        val isScrolled = lazyListState.firstVisibleItemIndex > 0 ||
                lazyListState.firstVisibleItemScrollOffset > 0
        showExtendedFab = !isScrolled
    }

    LaunchedEffect(refreshTrigger, displayedProjects.size) {
        visibleCount = 0
        if (displayedProjects.isNotEmpty()) {
            delay(100)
            for (i in 1..displayedProjects.size) {
                visibleCount = i
                delay(50)
            }
        }
    }

    val pageOrder =
        listOf(MainContentType.PROJECTS, MainContentType.SETTINGS, MainContentType.ABOUT)

    fun showToast(message: String) {
        toast.showToast(message)
    }

    // 将项目移入回收站，返回trashId（失败返回null）
    suspend fun moveProjectToTrash(projectId: String): String? {
        val project = projectItems.find { it.id == projectId } ?: return null
        // 删除前检查拦截器
        val intercepted = EventManager.checkIntercepted(
            PluginEvents.ON_PROJECT_DELETE,
            project.id, project.name, project.path
        )
        if (intercepted) return null

        return try {
            val trashItem = com.luaforge.studio.lxclua.utils.RecycleBinManager.moveToTrash(project, context)
            if (trashItem != null) {
                // 触发删除事件（兼容旧逻辑）和回收站事件
                EventManager.fireEvent(PluginEvents.ON_PROJECT_DELETE, project.id, project.name, project.path)
                EventManager.fireEvent(
                    "onProjectTrashed",
                    project.id, project.name, trashItem.trashId, project.path
                )
                scope.launch { onRefreshProjects() }
                trashItem.trashId
            } else {
                showToast("移入回收站失败")
                null
            }
        } catch (e: Exception) {
            LogCatcher.e("MainScreen", "移入回收站失败", e)
            showToast("移入回收站失败: ${e.message}")
            null
        }
    }

    // 显示删除撤销Snackbar
    fun showTrashSnackbar(message: String, trashIds: List<String>) {
        lastDeletedTrashIds.clear()
        lastDeletedTrashIds.addAll(trashIds)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "撤销",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                // 撤销：批量恢复
                scope.launch {
                    var restored = 0
                    trashIds.forEach { tid ->
                        val restoredProject = com.luaforge.studio.lxclua.utils.RecycleBinManager.restoreFromTrash(tid, context)
                        if (restoredProject != null) {
                            restored++
                            EventManager.fireEvent(
                                "onProjectRestored",
                                tid, restoredProject.id, restoredProject.name, restoredProject.path
                            )
                        }
                    }
                    onRefreshProjects()
                    if (restored > 0) {
                        showToast("已恢复 $restored 个项目")
                    }
                }
            }
        }
    }

    // 单个项目移入回收站（供pendingDeletionIds动画完成后调用）
    fun performDelete(projectId: String) {
        scope.launch {
            val trashId = moveProjectToTrash(projectId)
            if (trashId != null) {
                val project = projectItems.find { it.id == projectId }
                val name = project?.name ?: projectId
                showTrashSnackbar("已将\"$name\"移入回收站", listOf(trashId))
            }
        }
    }

    // 压缩目录的辅助函数
    suspend fun zipDirectory(sourceDir: File, targetZip: File) {
        withContext(Dispatchers.IO) {
            ZipOutputStream(FileOutputStream(targetZip)).use { zos ->
                sourceDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(sourceDir).path
                        val entry = ZipEntry(relativePath)
                        zos.putNextEntry(entry)
                        file.inputStream().use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    // 分享项目函数
    fun shareProject(project: ProjectItem) {
        scope.launch {
            showToast(context.getString(R.string.preparing_share))
            val zipFile = withContext(Dispatchers.IO) {
                try {
                    // 创建临时缓存目录（内部目录名可保留硬编码）
                    val cacheDir = File(context.cacheDir, "shared_projects")
                    cacheDir.mkdirs()

                    // 生成唯一的 ZIP 文件名
                    val timestamp = System.currentTimeMillis()
                    val zipFileName = "${project.name}_${timestamp}.zip"
                    val zipFile = File(cacheDir, zipFileName)

                    // 压缩项目目录
                    zipDirectory(File(project.path), zipFile)

                    zipFile
                } catch (e: Exception) {
                    LogCatcher.e("MainScreen", "压缩项目失败", e)
                    null
                }
            }

            if (zipFile != null && zipFile.exists()) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_project)))
            } else {
                showToast(context.getString(R.string.share_failed_cannot_compress))
            }
        }
    }

    // 更新排序并保存
    fun updateSortOrder(newOrder: SortOrder) {
        val newSettings = currentSettings.copy(sortOrder = newOrder)
        settingsManager.updateSettings(newSettings)
        settingsManager.saveSettings(context)
        sortMenuExpanded = false
    }

    // 切换项目置顶状态
    fun togglePinned(projectId: String) {
        val newPinnedSet = if (projectId in pinnedSet) {
            pinnedSet - projectId
        } else {
            pinnedSet + projectId
        }
        val newSettings = currentSettings.copy(pinnedProjects = newPinnedSet)
        settingsManager.updateSettings(newSettings)
        settingsManager.saveSettings(context)
    }

    // 当搜索激活时自动请求焦点
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(currentContentType) {
        if (currentContentType != MainContentType.PROJECTS) {
            isSearchActive = false
            searchQuery = ""
        }
    }

    // 监听主内容区子页面切换（设置/关于/插件管理），触发 ON_PAGE_CHANGED 事件
    var lastSubPageId by remember { mutableStateOf("main") }
    LaunchedEffect(currentContentType) {
        val subPageId = when (currentContentType) {
            MainContentType.PROJECTS -> "main"
            MainContentType.SETTINGS -> "settings"
            MainContentType.ABOUT -> "about"
            MainContentType.PLUGINS -> "plugins"
            MainContentType.TRASH -> "trash"
        }
        if (subPageId != lastSubPageId) {
            EventManager.fireEvent(PluginEvents.ON_PAGE_CHANGED, subPageId, lastSubPageId)
            lastSubPageId = subPageId
        }
    }

    // ---- 导入源码辅助函数 ----
    suspend fun handleImportFileSelected(
        file: File,
        toast: NonBlockingToastState
    ) {
        withContext(Dispatchers.IO) {
            try {
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry("settings.json")
                    if (entry == null) {
                        withContext(Dispatchers.Main) {
                            toast.showToast(context.getString(R.string.config_file_not_found))
                        }
                        return@withContext
                    }
                    val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val settings = JsonUtil.parseObject(content)
                    withContext(Dispatchers.Main) {
                        importSettingsData = settings
                        showImportConfirmDialog = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    toast.showToast(context.getString(R.string.parse_failed, e.message))
                }
            }
        }
    }

    suspend fun performImport(
        zipFile: File,
        targetDir: File,
        toast: NonBlockingToastState,
        onComplete: () -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                targetDir.mkdirs()
                FileUtil.extractZip(zipFile, targetDir)
                withContext(Dispatchers.Main) {
                    toast.showToast(context.getString(R.string.import_success))
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    toast.showToast(context.getString(R.string.import_failed, e.message))
                }
            }
        }
    }
    // --------------------------

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentContentType = currentContentType,
                onContentTypeChange = { type ->
                    currentContentType = type
                    scope.launch { drawerState.close() }
                },
                copyrightYear = copyrightYear,
                appVersionName = appVersionName
            )

            // 监听 NavigationState.sidebarOpen 的变化，让宿主 drawer 与插件 API 保持同步
            val sidebarOpenFromPlugin = NavigationState.sidebarOpen.value
            LaunchedEffect(sidebarOpenFromPlugin) {
                if (sidebarOpenFromPlugin && drawerState.isClosed) {
                    drawerState.open()
                    NavigationState.sidebarOpen.value = false
                }
            }
        }
    ) {
        BackHandler(
            enabled = isMultiSelectMode || currentContentType != MainContentType.PROJECTS || drawerState.isOpen,
            onBack = {
                scope.launch {
                    // 先执行拦截检查，参考cancelBuild模式
                    val intercepted = EventManager.fireEventWithIntercept(
                        PluginEvents.ON_BACK_PRESSED
                    )
                    if (intercepted) return@launch
                    
                    if (isMultiSelectMode) {
                        // 返回键退出多选模式
                        PluginManager.isMultiSelectMode.value = false
                        multiSelectedProjectIds.clear()
                        EventManager.fireEvent(PluginEvents.ON_MULTI_SELECT_EXIT)
                    } else if (drawerState.isOpen) {
                        drawerState.close()
                    } else if (currentContentType != MainContentType.PROJECTS) {
                        shouldReturnToProjects = true
                    }
                }
            }
        )

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                LargeTopAppBar(
                    title = {
                        if (isSearchActive && currentContentType == MainContentType.PROJECTS) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { 
        Text(
            text = stringResource(R.string.search_placeholder),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        ) 
    },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                                        }
                                    }
                                }
                            )
                        } else {
                            Text(
                                text = when (currentContentType) {
                                    MainContentType.PROJECTS -> stringResource(R.string.app_name)
                                    MainContentType.SETTINGS -> stringResource(R.string.settings)
                                    MainContentType.ABOUT -> stringResource(R.string.about)
                                    MainContentType.PLUGINS -> stringResource(R.string.drawer_plugin_management)
                                    MainContentType.TRASH -> "回收站"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (currentContentType == MainContentType.PROJECTS) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu))
                            }
                        } else {
                            IconButton(onClick = { shouldReturnToProjects = true }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    },
                    actions = {
                        when (currentContentType) {
                            MainContentType.PROJECTS -> {
                                // 搜索按钮
                                IconButton(onClick = {
                                    isSearchActive = !isSearchActive
                                    if (!isSearchActive) {
                                        searchQuery = ""
                                    }
                                }) {
                                    Icon(
                                        if (isSearchActive) Icons.Filled.Clear else Icons.Filled.Search,
                                        contentDescription = if (isSearchActive) stringResource(R.string.close_search) else stringResource(R.string.search)
                                    )
                                }

                                // 排序按钮
                                Box {
                                    IconButton(onClick = { sortMenuExpanded = true }) {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
                                    }
                                    DropdownMenu(
                                        expanded = sortMenuExpanded,
                                        onDismissRequest = { sortMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_name_asc)) },
                                            onClick = { updateSortOrder(SortOrder.NAME_ASC) },
                                            leadingIcon = if (sortOrder == SortOrder.NAME_ASC) {
                                                { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                                            } else null
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_name_desc)) },
                                            onClick = { updateSortOrder(SortOrder.NAME_DESC) },
                                            leadingIcon = if (sortOrder == SortOrder.NAME_DESC) {
                                                { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                                            } else null
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_date_newest)) },
                                            onClick = { updateSortOrder(SortOrder.DATE_MODIFIED_NEWEST) },
                                            leadingIcon = if (sortOrder == SortOrder.DATE_MODIFIED_NEWEST) {
                                                { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                                            } else null
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_date_oldest)) },
                                            onClick = { updateSortOrder(SortOrder.DATE_MODIFIED_OLDEST) },
                                            leadingIcon = if (sortOrder == SortOrder.DATE_MODIFIED_OLDEST) {
                                                { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                                            } else null
                                        )
                                    }
                                }

                                Box {
                                    IconButton(onClick = { moreMenuExpanded = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                                    }
                                    DropdownMenu(
                                        expanded = moreMenuExpanded,
                                        onDismissRequest = { moreMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.import_source)) },
                                            onClick = {
                                                moreMenuExpanded = false
                                                showFilePicker = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.config_project_dir)) },
                                            onClick = {
                                                moreMenuExpanded = false
                                                showConfigDirDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("导入备份") },
                                            onClick = {
                                                moreMenuExpanded = false
                                                showRestorePicker = true
                                            }
                                        )
                                    }
                                }
                                
                                // 插件注册的工具栏动作按钮
                                pluginToolbarActions.forEach { action ->
                                    val iconVec = getIconByName(action.iconName) ?: Icons.Filled.Extension
                                    IconButton(
                                        onClick = {
                                            try {
                                                action.onClick.run()
                                            } catch (e: Exception) {
                                                LogCatcher.e("MainScreen", "插件工具栏按钮 ${action.id} 回调异常", e)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            iconVec,
                                            contentDescription = action.tooltip
                                        )
                                    }
                                }
                            }

                            MainContentType.SETTINGS, MainContentType.ABOUT, MainContentType.PLUGINS, MainContentType.TRASH -> {
                            }
                        }

                        // 插件注册的 home_toolbar_end 工具栏按钮（按priority排序）
                        PluginManager.toolbarActionEntries
                            .filter { it.extensionPoint == UIExtensionPoints.HOME_TOOLBAR_END }
                            .sortedBy { it.priority }
                            .forEach { action ->
                                TextButton(
                                    onClick = {
                                        try {
                                            action.onClick.run()
                                        } catch (e: Exception) {
                                            LogCatcher.e("HomeScreen", "插件工具栏按钮 ${action.key} 回调异常", e)
                                        }
                                    }
                                ) {
                                    Text(action.tooltip)
                                }
                            }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                when (currentContentType) {
                    MainContentType.PROJECTS -> {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            // 插件注册的 SmallFloatingActionButton
                            pluginHomeFabs.forEach { fab ->
                                val iconVec = getIconByName(fab.iconName) ?: Icons.Filled.Extension
                                SmallFloatingActionButton(
                                    onClick = {
                                        try {
                                            fab.onClick.run()
                                        } catch (e: Exception) {
                                            LogCatcher.e("MainScreen", "插件FAB按钮 ${fab.id} 回调异常", e)
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    Icon(
                                        iconVec,
                                        contentDescription = fab.tooltip,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            // 主FAB：新建项目
                            ExtendedFloatingActionButton(
                                onClick = onNavigateToNewProject,
                                icon = {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.cd_add),
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                text = {
                                    AnimatedVisibility(
                                        visible = showExtendedFab,
                                        enter = TransitionUtil.createFABTransition(),
                                        exit = TransitionUtil.createFABExitTransition()
                                    ) {
                                        Text(stringResource(R.string.create_project))
                                    }
                                },
                                expanded = showExtendedFab
                            )
                        }
                    }

                    MainContentType.SETTINGS, MainContentType.ABOUT, MainContentType.PLUGINS, MainContentType.TRASH -> {
                    }
                }
            },
            // 批量操作底部栏（多选模式时显示）
            bottomBar = {
                if (isMultiSelectMode && currentContentType == MainContentType.PROJECTS) {
                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 批量删除（弹出确认对话框）
                            IconButton(
                                onClick = {
                                    if (multiSelectedProjectIds.isNotEmpty()) {
                                        showBatchDeleteConfirm = true
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "批量删除", tint = MaterialTheme.colorScheme.error)
                            }
                            // 批量备份
                            IconButton(
                                onClick = {
                                    if (multiSelectedProjectIds.isNotEmpty()) {
                                        val count = multiSelectedProjectIds.size
                                        scope.launch(Dispatchers.IO) {
                                            var successCount = 0
                                            val backupDir = settingsManager.getBackupDirectory()
                                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                            multiSelectedProjectIds.forEach { pid ->
                                                val proj = projectItems.find { it.id == pid }
                                                if (proj != null) {
                                                    val result = ProjectUtil.backupProjectToZip(
                                                        File(proj.path), backupDir, "${proj.name}_$timestamp"
                                                    )
                                                    if (result != null) {
                                                        successCount++
                                                        // 触发单个项目备份成功事件
                                                        EventManager.fireEvent(
                                                            PluginEvents.ON_PROJECT_BACKUP,
                                                            proj.id, result.absolutePath, true
                                                        )
                                                    } else {
                                                        // 触发单个项目备份失败事件
                                                        EventManager.fireEvent(
                                                            PluginEvents.ON_PROJECT_BACKUP,
                                                            proj.id, "", false
                                                        )
                                                    }
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                showToast("已备份${successCount}/${count}个项目")
                                            }
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Backup, contentDescription = "批量备份")
                            }
                            // 批量分类
                            IconButton(
                                onClick = {
                                    if (multiSelectedProjectIds.isNotEmpty()) {
                                        showBatchCategory = true
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Label, contentDescription = "批量分类")
                            }
                            // 批量标签
                            IconButton(
                                onClick = {
                                    if (multiSelectedProjectIds.isNotEmpty()) {
                                        showBatchTags = true
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.LabelImportant, contentDescription = "批量标签")
                            }
                            // 全选
                            IconButton(
                                onClick = {
                                    val allIds = projectItems.map { it.id }.toSet()
                                    if (multiSelectedProjectIds.size == allIds.size) {
                                        multiSelectedProjectIds.clear()
                                    } else {
                                        multiSelectedProjectIds.clear()
                                        multiSelectedProjectIds.addAll(allIds)
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.DoneAll, contentDescription = "全选")
                            }
                        }
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .consumeWindowInsets(WindowInsets.ime)
            ) {
                AnimatedContent(
                    targetState = currentContentType,
                    transitionSpec = {
                        val currentIndex = pageOrder.indexOf(initialState)
                        val targetIndex = pageOrder.indexOf(targetState)
                        TransitionUtil.createPageTransition(
                            currentIndex = currentIndex,
                            targetIndex = targetIndex
                        )
                    },
                    label = "content_transition"
                ) { targetContentType ->
                    when (targetContentType) {
                        MainContentType.PROJECTS -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // "继续上次项目" 提示卡片
                                if (homeShowRecent && lastOpenedProject != null) {
                                    var showRecentMenu by remember { mutableStateOf(false) }
                                    Box {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                                .combinedClickable(
                                                    onClick = {
                                                        val proj = lastOpenedProject!!
                                                        // 注：onNavigateToEditor 内部已触发 ON_PROJECT_OPEN 事件（含拦截）
                                                        settingsManager.pushRecentProject(proj.id, context)
                                                        onNavigateToEditor(proj)
                                                    },
                                                    onLongClick = { showRecentMenu = true }
                                                ),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Filled.History,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        stringResource(R.string.continue_last_project),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        lastOpenedProject!!.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                FilledTonalButton(onClick = {
                                                    val proj = lastOpenedProject!!
                                                    // 注：onNavigateToEditor 内部已触发 ON_PROJECT_OPEN 事件（含拦截）
                                                    settingsManager.pushRecentProject(proj.id, context)
                                                    onNavigateToEditor(proj)
                                                }) {
                                                    Text(stringResource(R.string.open_project))
                                                }
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showRecentMenu,
                                            onDismissRequest = { showRecentMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("不再显示") },
                                                onClick = {
                                                    showRecentMenu = false
                                                    settingsManager.updateSettings(
                                                        currentSettings.copy(homeShowRecent = false)
                                                    )
                                                    settingsManager.saveSettings(context)
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.VisibilityOff, contentDescription = null)
                                                }
                                            )
                                        }
                                    }
                                }

                                // 最近项目横向快速访问条
                                if (showRecentProjectsBar && recentProjectItems.isNotEmpty()) {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        item {
                                            Text(
                                                "最近",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(end = 4.dp, top = 20.dp)
                                            )
                                        }
                                        items(recentProjectItems, key = { it.id }) { rp ->
                                            val rCover = projectCoverMap[rp.id]
                                            Card(
                                                modifier = Modifier
                                                    .width(recentCardWidthDp)
                                                    .clickable {
                                                        // 注：onNavigateToEditor 内部已触发 ON_PROJECT_OPEN 事件（含拦截）
                                                        settingsManager.pushRecentProject(rp.id, context)
                                                        onNavigateToEditor(rp)
                                                    },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    val rIconFile = File(rp.path, "icon.png")
                                                    when {
                                                        rCover?.type == CoverType.IMAGE && rCover.imagePath.isNotEmpty() -> {
                                                            SubcomposeAsyncImage(
                                                                model = rCover.imagePath,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(44.dp).clip(CircleShape),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        }
                                                        rIconFile.exists() -> {
                                                            SubcomposeAsyncImage(
                                                                model = rIconFile,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(44.dp).clip(CircleShape),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        }
                                                        rCover?.type == CoverType.SOLID_COLOR -> {
                                                            Box(
                                                                modifier = Modifier.size(44.dp).clip(CircleShape)
                                                                    .background(Color(rCover.colorValue)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Filled.Folder,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(26.dp)
                                                                )
                                                            }
                                                        }
                                                        else -> {
                                                            Box(
                                                                modifier = Modifier.size(44.dp).clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Filled.Folder,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(26.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        rp.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 分类筛选栏 - 顶部位置（直接读取currentSettings确保响应式更新）
                                if (homeCategoryEnabled && currentSettings.categoryBarPosition == CategoryBarPosition.TOP) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (homeCategories.isNotEmpty()) {
                                        item {
                                            FilterChip(
                                                selected = selectedCategoryId.isEmpty(),
                                                onClick = { selectedCategoryId = "" },
                                                label = { Text(stringResource(R.string.category_all)) }
                                            )
                                        }
                                        items(homeCategories) { cat ->
                                            FilterChip(
                                                selected = selectedCategoryId == cat.id,
                                                onClick = {
                                                    selectedCategoryId = if (selectedCategoryId == cat.id) "" else cat.id
                                                },
                                                label = { Text(cat.name) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(cat.color.toInt()).copy(alpha = 0.2f),
                                                    selectedLabelColor = Color(cat.color.toInt())
                                                )
                                            )
                                        }
                                    } else {
                                        // 没有分类时显示提示
                                        item {
                                            AssistChip(
                                                onClick = { showCategoryManager = true },
                                                label = { Text("创建分类") },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Filled.Label,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    item {
                                        IconButton(
                                            onClick = { showCategoryManager = true }
                                        ) {
                                            Icon(
                                                Icons.Filled.Settings,
                                                contentDescription = stringResource(R.string.category_manage),
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    // 插件注册的分类栏项
                                    items(pluginCategoryBarItems, key = { it.id }) { catItem ->
                                        val iconVec = getIconByName(catItem.iconName) ?: Icons.Filled.Extension
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                try {
                                                    catItem.onClick.run()
                                                } catch (e: Exception) {
                                                    LogCatcher.e("MainScreen", "插件分类栏项 ${catItem.id} 回调异常", e)
                                                }
                                            },
                                            label = { Text(catItem.name) },
                                            leadingIcon = {
                                                Icon(
                                                    iconVec,
                                                    contentDescription = catItem.name,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                                }

                                // 标签筛选栏（始终显示管理标签入口，即使没有标签）
                                if (showTagFilterBar) {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (homeProjectTags.isNotEmpty()) {
                                            item {
                                                FilterChip(
                                                    selected = selectedTagIds.isEmpty(),
                                                    onClick = { selectedTagIds = emptySet() },
                                                    label = { Text("全部标签") }
                                                )
                                            }
                                            items(homeProjectTags) { tag ->
                                                FilterChip(
                                                    selected = tag.id in selectedTagIds,
                                                    onClick = {
                                                        selectedTagIds = if (tag.id in selectedTagIds) {
                                                            selectedTagIds - tag.id
                                                        } else {
                                                            selectedTagIds + tag.id
                                                        }
                                                    },
                                                    label = { Text(tag.name) },
                                                    leadingIcon = {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(10.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(tag.color.toInt()))
                                                        )
                                                    },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = Color(tag.color.toInt()).copy(alpha = 0.2f),
                                                        selectedLabelColor = Color(tag.color.toInt())
                                                    )
                                                )
                                            }
                                        } else {
                                            // 没有标签时显示"新建标签"快捷入口
                                            item {
                                                AssistChip(
                                                    onClick = { showTagManager = true },
                                                    label = { Text("新建标签") },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Filled.Add,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                        item {
                                            IconButton(onClick = { showTagManager = true }) {
                                                Icon(
                                                    Icons.Filled.Settings,
                                                    contentDescription = "管理标签",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                if (displayedProjects.isEmpty()) {
                                SideEffect {
                                    showExtendedFab = true
                                }
                                Column(
                                    modifier = Modifier.fillMaxSize().weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.FolderOpen,
                                        contentDescription = stringResource(R.string.cd_project_folder),
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.no_projects),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.create_first_project),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            } else {
                                PullToRefreshBox(
                                    isRefreshing = isRefreshing,
                                    onRefresh = {
                                        scope.launch {
                                            isRefreshing = true
                                            val previousItems = projectItems.toList()
                                            val previousSize = previousItems.size
                                            scope.launch { onRefreshProjects() }
                                            val newItems = projectItems
                                            val sizeChanged = newItems.size != previousSize
                                            val contentChanged = newItems != previousItems
                                            if (sizeChanged || contentChanged) {
                                                refreshTrigger++
                                            }
                                            isRefreshing = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize().weight(1f)
                                ) {
                                    
    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        state = lazyListState,
                                        contentPadding = PaddingValues(
                                            if (homeLayoutMode == HomeLayoutMode.FLAT) 8.dp else 16.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(
                                            if (homeLayoutMode == HomeLayoutMode.FLAT) 4.dp else 12.dp
                                        )
                                    ) {
                                        items(
                                            count = visibleCount.coerceAtMost(displayedProjects.size),
                                            key = { displayedProjects[it].id }
                                        ) { index ->
                                            val project = displayedProjects[index]
                                            ProjectCard(
                                                project = project,
                                                isPinned = project.id in pinnedSet,
                                                isPendingDeletion = project.id in pendingDeletionIds,
                                                onTogglePinned = { togglePinned(project.id) },
                                                onDeleteClick = {
                                                    deleteProjectId = project.id
                                                    deleteProjectName = project.name
                                                    deleteProjectPath = project.path
                                                    showDeleteDialog = true
                                                },
                                                onShareClick = { shareProject(project) },
                                                onClick = {
                                                    if (isMultiSelectMode) {
                                                        // 多选模式下点击卡片切换选中状态
                                                        if (project.id in multiSelectedProjectIds) {
                                                            multiSelectedProjectIds.remove(project.id)
                                                        } else {
                                                            multiSelectedProjectIds.add(project.id)
                                                        }
                                                        // 触发多选变化事件
                                                        EventManager.fireEvent(
                                                            PluginEvents.ON_MULTI_SELECTION_CHANGED,
                                                            multiSelectedProjectIds.size,
                                                            multiSelectedProjectIds.joinToString(",")
                                                        )
                                                        EventManager.fireEvent(
                                                            PluginEvents.ON_PROJECT_CLICK,
                                                            project.id, project.name, project.path
                                                        )
                                                    } else {
                                                        // 注：onNavigateToEditor 内部已触发 ON_PROJECT_OPEN 事件（含拦截）
                                                        settingsManager.pushRecentProject(project.id, context)
                                                        onNavigateToEditor(project)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!isMultiSelectMode) {
                                                        // 非多选模式下长按进入多选模式并选中当前项目
                                                        PluginManager.isMultiSelectMode.value = true
                                                        multiSelectedProjectIds.clear()
                                                        multiSelectedProjectIds.add(project.id)
                                                        EventManager.fireEvent(PluginEvents.ON_MULTI_SELECT_ENTER)
                                                        EventManager.fireEvent(
                                                            PluginEvents.ON_MULTI_SELECTION_CHANGED,
                                                            1,
                                                            project.id
                                                        )
                                                    }
                                                    EventManager.fireEvent(
                                                        PluginEvents.ON_PROJECT_LONG_PRESS,
                                                        project.id, project.name, project.path
                                                    )
                                                },
                                                onSetCategory = if (homeCategoryEnabled) {{
                                                    categoryPickerProject = project
                                                }} else null,
                                                isSelectedInMultiSelect = isMultiSelectMode && project.id in multiSelectedProjectIds,
                                                isMultiSelectMode = isMultiSelectMode,
                                                badge = projectBadges[project.id],
                                                extraMenuItems = projectCardMenuItems.toList(),
                                                onSwipeLeft = {
                                                    EventManager.fireEvent(
                                                        PluginEvents.ON_PROJECT_SWIPE_LEFT,
                                                        project.id, project.name, project.path
                                                    )
                                                },
                                                onSwipeRight = {
                                                    EventManager.fireEvent(
                                                        PluginEvents.ON_PROJECT_SWIPE_RIGHT,
                                                        project.id, project.name, project.path
                                                    )
                                                },
                                                isFlatMode = homeLayoutMode == HomeLayoutMode.FLAT,
                                                cover = projectCoverMap[project.id],
                                                cornerRadius = cardCornerRadius,
                                                density = homeDensity,
                                                tags = remember(project.id, projectTagsMap, homeProjectTags) {
                                                    val ids = projectTagsMap[project.id] ?: emptySet()
                                                    homeProjectTags.filter { it.id in ids }
                                                },
                                                showModifiedTime = showModifiedTime,
                                                showPath = showPath,
                                                highlightText = searchQuery,
                                                enableSwipeGesture = enableSwipeGesture && !isMultiSelectMode,
                                                onBackupClick = { showBackupProject = project },
                                                onSaveAsTemplateClick = {
                                                    showSaveTemplate = project
                                                    templateNameInput = project.name
                                                },
                                                onSetCoverClick = { showCoverEditor = project },
                                                onSetTagsClick = { tagPickerProject = project },
                                                onCreateShortcutClick = { showShortcutConfirm = project },
                                                modifier = Modifier.animateItem()
                                            )
                                        }

                                        // 分类筛选栏 - 底部位置（作为LazyColumn最后一项，列表末尾显示）
                                        if (homeCategoryEnabled && currentSettings.categoryBarPosition == CategoryBarPosition.BOTTOM) {
                                            item(key = "bottom_category_bar") {
                                                LazyRow(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                                        .animateItem(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (homeCategories.isNotEmpty()) {
                                                        item {
                                                            FilterChip(
                                                                selected = selectedCategoryId.isEmpty(),
                                                                onClick = { selectedCategoryId = "" },
                                                                label = { Text(stringResource(R.string.category_all)) }
                                                            )
                                                        }
                                                        items(homeCategories) { cat ->
                                                            FilterChip(
                                                                selected = selectedCategoryId == cat.id,
                                                                onClick = {
                                                                    selectedCategoryId = if (selectedCategoryId == cat.id) "" else cat.id
                                                                },
                                                                label = { Text(cat.name) },
                                                                colors = FilterChipDefaults.filterChipColors(
                                                                    selectedContainerColor = Color(cat.color.toInt()).copy(alpha = 0.2f),
                                                                    selectedLabelColor = Color(cat.color.toInt())
                                                                )
                                                            )
                                                        }
                                                    } else {
                                                        item {
                                                            AssistChip(
                                                                onClick = { showCategoryManager = true },
                                                                label = { Text("创建分类") },
                                                                leadingIcon = {
                                                                    Icon(
                                                                        Icons.Filled.Label,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                    item {
                                                        IconButton(
                                                            onClick = { showCategoryManager = true }
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.Settings,
                                                                contentDescription = stringResource(R.string.category_manage),
                                                                modifier = Modifier.size(18.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    // 插件注册的分类栏项
                                                    items(pluginCategoryBarItems, key = { it.id }) { catItem ->
                                                        val iconVec = getIconByName(catItem.iconName) ?: Icons.Filled.Extension
                                                        FilterChip(
                                                            selected = false,
                                                            onClick = {
                                                                try {
                                                                    catItem.onClick.run()
                                                                } catch (e: Exception) {
                                                                    LogCatcher.e("MainScreen", "插件分类栏项 ${catItem.id} 回调异常", e)
                                                                }
                                                            },
                                                            label = { Text(catItem.name) },
                                                            leadingIcon = {
                                                                Icon(
                                                                    iconVec,
                                                                    contentDescription = catItem.name,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }

                        MainContentType.SETTINGS -> {
                            SettingsScreen(
                                onBack = {
                                    shouldReturnToProjects = true
                                },
                                currentSettings = currentSettings,
                                onSettingsChanged = { newSettings ->
                                    settingsManager.updateSettings(newSettings)
                                },
                                toast = toast,
                                onContentTypeChange = { type ->
                                    currentContentType = type
                                }
                            )
                        }

                        MainContentType.ABOUT -> {
                            AboutScreen(
                                onBack = {
                                    shouldReturnToProjects = true
                                }
                            )
                        }

                        MainContentType.PLUGINS -> {
                            com.luaforge.studio.lxclua.ui.plugin.PluginScreen(
                                onBack = {
                                    shouldReturnToProjects = true
                                }
                            )
                        }

                        MainContentType.TRASH -> {
                            com.luaforge.studio.lxclua.ui.trash.TrashScreen(
                                onBack = {
                                    shouldReturnToProjects = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("移入回收站") },
            text = {
                Column {
                    Text("项目将移入回收站，7天后自动删除。您可以在回收站中恢复。", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.project_name_label, deleteProjectName), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.project_path_label, deleteProjectPath), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        if (deleteProjectId.isNotEmpty()) {
                            pendingDeletionIds = pendingDeletionIds + deleteProjectId
                            scope.launch {
                                delay(300)
                                pendingDeletionIds = pendingDeletionIds - deleteProjectId
                                performDelete(deleteProjectId)
                            }
                        }
                    }
                ) {
                    Text("移入回收站", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 批量删除确认对话框
    if (showBatchDeleteConfirm) {
        val count = multiSelectedProjectIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("批量移入回收站") },
            text = {
                Text(
                    "确定要将 $count 个项目移入回收站吗？项目将保留7天，可在回收站中恢复。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatchDeleteConfirm = false
                        val idsToTrash = multiSelectedProjectIds.toList()
                        // 退出多选模式
                        PluginManager.isMultiSelectMode.value = false
                        multiSelectedProjectIds.clear()
                        // 批量移入回收站
                        scope.launch {
                            val trashIds = mutableListOf<String>()
                            idsToTrash.forEach { pid ->
                                val tid = moveProjectToTrash(pid)
                                if (tid != null) trashIds.add(tid)
                            }
                            if (trashIds.isNotEmpty()) {
                                showTrashSnackbar("已将${trashIds.size}个项目移入回收站", trashIds)
                            }
                        }
                    }
                ) {
                    Text("移入回收站", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ---- 导入源码弹窗 ----
    if (showFilePicker) {
        FilePickerDialog(
            initialPath = Environment.getExternalStorageDirectory().absolutePath,
            selectionMode = SelectionMode.FILE,
            title = stringResource(R.string.import_source),
            allowedExtensions = listOf("zip", "alp"),
            onDismiss = { showFilePicker = false },
            onFileSelected = { filePath ->
                showFilePicker = false
                selectedImportFile = File(filePath)
                scope.launch {
                    handleImportFileSelected(
                        selectedImportFile!!,
                        toast
                    )
                }
            }
        )
    }

    if (showImportConfirmDialog && importSettingsData != null) {
        val unknown = stringResource(R.string.unknown)
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text(stringResource(R.string.import_source_title)) },
            text = {
                val settings = importSettingsData!!
                val label = (settings["application"] as? Map<*, *>)?.get("label") as? String ?: unknown
                val packageName = settings["package"] as? String ?: unknown
                val versionName = settings["versionName"] as? String ?: unknown
                val filePath = selectedImportFile?.absolutePath ?: unknown
                Column {
                    Text(stringResource(R.string.project_name_label, label))
                    Text(stringResource(R.string.import_source_package_name, packageName))
                    Text(stringResource(R.string.version_label, versionName))
                    Text(stringResource(R.string.import_source_file_path, filePath))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirmDialog = false
                    val label =
                        ((importSettingsData!!["application"] as? Map<*, *>)?.get("label") as? String)?.trim()
                    if (label.isNullOrBlank()) {
                        scope.launch { toast.showToast(context.getString(R.string.invalid_project_name)) }
                        return@TextButton
                    }
                    val targetDir = File(primaryProjectsPath, label)
                    if (targetDir.exists()) {
                        conflictLabel = label
                        conflictPath = targetDir.absolutePath
                        showConflictDialog = true
                    } else {
                        scope.launch {
                            performImport(selectedImportFile!!, targetDir, toast) {
                                scope.launch { onRefreshProjects() }
                            }
                        }
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            title = { Text(stringResource(R.string.project_exists_title)) },
            text = {
                Text(stringResource(R.string.project_exists_message, conflictLabel))
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showConflictDialog = false
                        conflictAction = ConflictAction.OVERWRITE
                        val targetDir = File(primaryProjectsPath, conflictLabel)
                        scope.launch {
                            targetDir.deleteRecursively()
                            performImport(selectedImportFile!!, targetDir, toast) {
                                scope.launch { onRefreshProjects() }
                            }
                        }
                    }) { Text(stringResource(R.string.overwrite)) }
                    TextButton(onClick = {
                        showConflictDialog = false
                        conflictAction = ConflictAction.CLONE
                        var cloneDir = File(primaryProjectsPath, "${conflictLabel}_clone")
                        var counter = 1
                        while (cloneDir.exists()) {
                            counter++
                            cloneDir = File(primaryProjectsPath, "${conflictLabel}_clone$counter")
                        }
                        scope.launch {
                            performImport(selectedImportFile!!, cloneDir, toast) {
                                scope.launch { onRefreshProjects() }
                            }
                        }
                    }) { Text(stringResource(R.string.clone)) }
                    TextButton(onClick = { showConflictDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
            dismissButton = {}
        )
    }

    // ---- 配置项目目录文件选择器 ----
    if (showDirPicker) {
        FilePickerDialog(
            initialPath = Environment.getExternalStorageDirectory().absolutePath,
            selectionMode = SelectionMode.DIRECTORY,
            title = stringResource(R.string.select_project_dir),
            onDismiss = { showDirPicker = false },
            onDirectorySelected = { selectedPath ->
                showDirPicker = false
                when (dirPickerTarget) {
                    DirPickerTarget.PRIMARY -> {
                        val updatedSettings = currentSettings.copy(
                            projectStoragePath = selectedPath
                        )
                        SettingsManager.updateSettings(updatedSettings)
                        SettingsManager.saveSettings(context)
                        scope.launch { onRefreshProjects() }
                    }
                    DirPickerTarget.ADDITIONAL -> {
                        val newPaths = currentSettings.additionalProjectPaths.toMutableList()
                        newPaths.add(selectedPath)
                        val updatedSettings = currentSettings.copy(
                            additionalProjectPaths = newPaths
                        )
                        SettingsManager.updateSettings(updatedSettings)
                        SettingsManager.saveSettings(context)
                        scope.launch { onRefreshProjects() }
                    }
                }
            }
        )
    }

    // ---- 配置项目目录弹窗 ----
    if (showConfigDirDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDirDialog = false },
            title = { Text(stringResource(R.string.config_project_dirs)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    var modifiedPaths by remember(currentSettings.additionalProjectPaths) {
                        mutableStateOf(currentSettings.additionalProjectPaths.toMutableList())
                    }

                    // 主目录
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.primary_dir),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                currentSettings.projectStoragePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(onClick = {
                            dirPickerTarget = DirPickerTarget.PRIMARY
                            showDirPicker = true
                        }) {
                            Text(stringResource(R.string.modify))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 附加目录列表
                    Text(
                        stringResource(R.string.additional_dir),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    modifiedPaths.forEachIndexed { index, path ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                path,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = {
                                modifiedPaths.removeAt(index)
                                val updatedSettings = currentSettings.copy(
                                    additionalProjectPaths = modifiedPaths.toList()
                                )
                                SettingsManager.updateSettings(updatedSettings)
                                SettingsManager.saveSettings(context)
                                scope.launch { onRefreshProjects() }
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.remove_dir),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 添加目录按钮
                    OutlinedButton(
                        onClick = {
                            dirPickerTarget = DirPickerTarget.ADDITIONAL
                            showDirPicker = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.add_dir))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConfigDirDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // 分类管理对话框
    if (showCategoryManager) {
        var showCreateDialog by remember { mutableStateOf(false) }
        var showDeleteConfirm by remember { mutableStateOf<ProjectCategory?>(null) }
        var categoryNameInput by remember { mutableStateOf("") }
        var categoryColorInput by remember { mutableStateOf(0xFF6750A4.toLong()) }
        val presetColors = listOf(
            0xFF6750A4.toLong(), // 紫色
            0xFFE91E63.toLong(), // 粉红
            0xFF2196F3.toLong(), // 蓝色
            0xFF4CAF50.toLong(), // 绿色
            0xFFFF9800.toLong(), // 橙色
            0xFFF44336.toLong(), // 红色
            0xFF00BCD4.toLong(), // 青色
            0xFF9C27B0.toLong(), // 深紫
            0xFF795548.toLong(), // 棕色
            0xFF607D8B.toLong(), // 蓝灰
        )

        AlertDialog(
            onDismissRequest = { showCategoryManager = false },
            title = { Text(stringResource(R.string.category_manage)) },
            text = {
                Column {
                    if (homeCategories.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_home_categories_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        homeCategories.forEach { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(cat.color.toInt()))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    cat.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${cat.projectIds.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = {
                                        categoryNameInput = cat.name
                                        categoryColorInput = cat.color
                                        editingCategory = cat
                                        showCreateDialog = true
                                    }
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { showDeleteConfirm = cat }
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            categoryNameInput = ""
                            categoryColorInput = presetColors.first()
                            editingCategory = null
                            showCreateDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.category_new))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryManager = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )

        // 新建/编辑分类对话框
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = {
                    Text(if (editingCategory != null) "编辑分类" else stringResource(R.string.category_new))
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = categoryNameInput,
                            onValueChange = { categoryNameInput = it },
                            label = { Text(stringResource(R.string.category_name)) },
                            placeholder = { Text(stringResource(R.string.category_name_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.category_color),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            presetColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(color.toInt()))
                                        .border(
                                            width = if (categoryColorInput == color) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                        .clickable { categoryColorInput = color }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (categoryNameInput.isNotBlank()) {
                                val newCat = if (editingCategory != null) {
                                    editingCategory!!.copy(name = categoryNameInput, color = categoryColorInput)
                                } else {
                                    ProjectCategory(
                                        id = java.util.UUID.randomUUID().toString(),
                                        name = categoryNameInput,
                                        color = categoryColorInput
                                    )
                                }
                                val updatedCategories = if (editingCategory != null) {
                                    homeCategories.map { if (it.id == editingCategory!!.id) newCat else it }
                                } else {
                                    homeCategories + newCat
                                }
                                settingsManager.updateSettings(
                                    currentSettings.copy(homeCategories = updatedCategories)
                                )
                                settingsManager.saveSettings(context)
                                showCreateDialog = false
                            }
                        }
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        // 删除确认对话框
        showDeleteConfirm?.let { cat ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text("删除分类") },
                text = { Text("确定要删除分类「${cat.name}」吗？项目不会被删除。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val updated = homeCategories.filter { it.id != cat.id }
                            settingsManager.updateSettings(
                                currentSettings.copy(homeCategories = updated)
                            )
                            settingsManager.saveSettings(context)
                            if (selectedCategoryId == cat.id) selectedCategoryId = ""
                            showDeleteConfirm = null
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }

    // 项目分类选择对话框 - 独立于分类管理对话框
    categoryPickerProject?.let { project ->
        // 找到项目当前所属分类
        val currentCatId = homeCategories.find { project.id in it.projectIds }?.id ?: ""
        AlertDialog(
            onDismissRequest = { categoryPickerProject = null },
            title = { Text("设置分类 - ${project.name}") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 无分类选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 将项目从所有分类中移除
                                val updated = homeCategories.map { cat ->
                                    cat.copy(projectIds = cat.projectIds - project.id)
                                }
                                settingsManager.updateSettings(
                                    currentSettings.copy(homeCategories = updated)
                                )
                                settingsManager.saveSettings(context)
                                categoryPickerProject = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentCatId.isEmpty(),
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("无分类")
                    }
                    // 分类列表
                    homeCategories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 将项目添加到该分类，同时从其他分类移除
                                    val updated = homeCategories.map { c ->
                                        when {
                                            c.id == cat.id -> c.copy(projectIds = c.projectIds + project.id)
                                            else -> c.copy(projectIds = c.projectIds - project.id)
                                        }
                                    }
                                    settingsManager.updateSettings(
                                        currentSettings.copy(homeCategories = updated)
                                    )
                                    settingsManager.saveSettings(context)
                                    categoryPickerProject = null
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentCatId == cat.id,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(cat.color.toInt()))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat.name)
                        }
                    }
                    // 如果没有分类，提示先创建
                    if (homeCategories.isEmpty()) {
                        Text(
                            "还没有分类，请先点击分类栏的齿轮图标创建分类",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { categoryPickerProject = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // ==================== 标签管理对话框 ====================
    if (showTagManager) {
        // 标签编辑子对话框状态
        var showTagEditDialog by remember { mutableStateOf(false) }
        var tagNameInput by remember { mutableStateOf("") }
        var tagColorInput by remember { mutableStateOf(0xFF6750A4.toLong()) }
        var showTagDeleteConfirm by remember { mutableStateOf<ProjectTag?>(null) }
        val tagPresetColors = listOf(
            0xFF6750A4.toLong(), 0xFF2196F3.toLong(), 0xFF4CAF50.toLong(),
            0xFFFF9800.toLong(), 0xFFF44336.toLong(), 0xFF9C27B0.toLong(),
            0xFF00BCD4.toLong(), 0xFFFF5722.toLong(), 0xFF795548.toLong(),
            0xFF607D8B.toLong(), 0xFFE91E63.toLong(), 0xFF009688.toLong()
        )

        AlertDialog(
            onDismissRequest = { showTagManager = false },
            title = { Text("标签管理") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (homeProjectTags.isEmpty()) {
                        Text(
                            "还没有标签，点击下方按钮新建标签",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        homeProjectTags.forEach { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 颜色圆点
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(tag.color.toInt()))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    tag.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                // 标签使用数
                                val usageCount = projectTagsMap.values.count { tag.id in it }
                                Text(
                                    "$usageCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // 编辑按钮
                                IconButton(
                                    onClick = {
                                        tagNameInput = tag.name
                                        tagColorInput = tag.color
                                        editingTag = tag
                                        showTagEditDialog = true
                                    }
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                // 删除按钮
                                IconButton(
                                    onClick = { showTagDeleteConfirm = tag }
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 新建标签按钮
                    OutlinedButton(
                        onClick = {
                            tagNameInput = ""
                            tagColorInput = tagPresetColors.first()
                            editingTag = null
                            showTagEditDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建标签")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagManager = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )

        // 标签新建/编辑子对话框
        if (showTagEditDialog) {
            AlertDialog(
                onDismissRequest = { showTagEditDialog = false },
                title = { Text(if (editingTag != null) "编辑标签" else "新建标签") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = tagNameInput,
                            onValueChange = { tagNameInput = it },
                            label = { Text("标签名称") },
                            placeholder = { Text("请输入标签名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "标签颜色",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tagPresetColors.take(6).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(color.toInt()))
                                        .border(
                                            width = if (tagColorInput == color) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                        .clickable { tagColorInput = color }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tagPresetColors.drop(6).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(color.toInt()))
                                        .border(
                                            width = if (tagColorInput == color) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                        .clickable { tagColorInput = color }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (tagNameInput.isNotBlank()) {
                                val newTag = if (editingTag != null) {
                                    editingTag!!.copy(name = tagNameInput, color = tagColorInput)
                                } else {
                                    ProjectTag(
                                        id = java.util.UUID.randomUUID().toString(),
                                        name = tagNameInput,
                                        color = tagColorInput
                                    )
                                }
                                val updatedTags = if (editingTag != null) {
                                    homeProjectTags.map { if (it.id == editingTag!!.id) newTag else it }
                                } else {
                                    homeProjectTags + newTag
                                }
                                settingsManager.updateSettings(
                                    currentSettings.copy(homeProjectTags = updatedTags)
                                )
                                scope.launch(Dispatchers.IO) {
                                    settingsManager.saveSettings(context)
                                }
                                showTagEditDialog = false
                            }
                        }
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTagEditDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        // 标签删除确认对话框
        showTagDeleteConfirm?.let { tag ->
            AlertDialog(
                onDismissRequest = { showTagDeleteConfirm = null },
                title = { Text("删除标签") },
                text = { Text("确定要删除标签「${tag.name}」吗？该标签将从所有项目中移除。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // 从标签列表中移除
                            val updatedTags = homeProjectTags.filter { it.id != tag.id }
                            // 从所有项目的标签映射中清理该tagId
                            val updatedTagsMap = projectTagsMap.mapValues { (_, tags) ->
                                tags - tag.id
                            }.filterValues { it.isNotEmpty() }
                            settingsManager.updateSettings(
                                currentSettings.copy(
                                    homeProjectTags = updatedTags,
                                    projectTagsMap = updatedTagsMap
                                )
                            )
                            scope.launch(Dispatchers.IO) {
                                settingsManager.saveSettings(context)
                            }
                            // 如果当前筛选中包含该标签，移除筛选
                            if (tag.id in selectedTagIds) {
                                selectedTagIds = selectedTagIds - tag.id
                            }
                            showTagDeleteConfirm = null
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTagDeleteConfirm = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }

    // ==================== 项目标签选择对话框 ====================
    tagPickerProject?.let { project ->
        // 当前项目已选标签（副本状态）
        var selectedTags by remember(project.id) {
            mutableStateOf(projectTagsMap[project.id] ?: emptySet())
        }
        AlertDialog(
            onDismissRequest = { tagPickerProject = null },
            title = { Text("设置标签 - ${project.name}") },
            text = {
                if (homeProjectTags.isEmpty()) {
                    Text(
                        "还没有标签，请先在标签管理中创建标签",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(homeProjectTags) { tag ->
                            val isChecked = tag.id in selectedTags
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTags = if (isChecked) selectedTags - tag.id else selectedTags + tag.id
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(tag.color.toInt()))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tag.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsManager.setProjectTags(project.id, selectedTags, context)
                        tagPickerProject = null
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { tagPickerProject = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // ==================== 封面设置对话框 ====================
    showCoverEditor?.let { project ->
        // 当前封面状态
        val existingCover = projectCoverMap[project.id]
        var coverTabIndex by remember { mutableStateOf(0) }
        var selectedColor by remember {
            mutableStateOf(
                if (existingCover != null && existingCover.type == CoverType.SOLID_COLOR)
                    existingCover.colorValue.toLong()
                else 0xFF6750A4.toLong()
            )
        }
        // 封面透明度（0.3~1.0）
        var coverAlpha by remember {
            mutableStateOf(
                (existingCover?.alpha ?: 1.0f).coerceIn(0.3f, 1.0f)
            )
        }
        var showCoverImagePicker by remember { mutableStateOf(false) }
        var selectedCoverImagePath by remember {
            mutableStateOf(
                if (existingCover != null && existingCover.type == CoverType.IMAGE) existingCover.imagePath else ""
            )
        }
        // 图片偏移量状态（用于拖拽调整图片位置）
        var coverOffsetX by remember {
            mutableStateOf(existingCover?.offsetX ?: 0f)
        }
        var coverOffsetY by remember {
            mutableStateOf(existingCover?.offsetY ?: 0f)
        }
        val coverPresetColors = listOf(
            0xFF6750A4.toLong(), 0xFF2196F3.toLong(), 0xFF4CAF50.toLong(),
            0xFFFF9800.toLong(), 0xFFF44336.toLong(), 0xFF9C27B0.toLong(),
            0xFF00BCD4.toLong(), 0xFFFF5722.toLong(), 0xFF795548.toLong(),
            0xFF607D8B.toLong(), 0xFFE91E63.toLong(), 0xFF009688.toLong()
        )
        val coverTabTitles = listOf("纯色", "图片")

        AlertDialog(
            onDismissRequest = { showCoverEditor = null },
            title = { Text("设置封面 - ${project.name}") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Tab切换
                    TabRow(
                        selectedTabIndex = coverTabIndex,
                        containerColor = Color.Transparent
                    ) {
                        coverTabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = coverTabIndex == index,
                                onClick = { coverTabIndex = index },
                                text = { Text(title) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    when (coverTabIndex) {
                        0 -> {
                            // 纯色页：预设色块网格
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.height(180.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(coverPresetColors.size) { idx ->
                                    val color = coverPresetColors[idx]
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(color.toInt()))
                                            .border(
                                                width = if (selectedColor == color) 3.dp else 1.dp,
                                                color = if (selectedColor == color)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedColor = color }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // 透明度滑块
                            Text(
                                text = "透明度: ${(coverAlpha * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = coverAlpha,
                                onValueChange = { coverAlpha = it },
                                valueRange = 0.3f..1.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            // 清除封面按钮
                            TextButton(
                                onClick = {
                                    settingsManager.setProjectCover(project.id, null, context)
                                    showCoverEditor = null
                                }
                            ) {
                                Text("清除封面", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        1 -> {
                            // 图片页
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 图片预览区域（固定16:9宽高比，支持拖拽调整偏移）
                                val previewCover = if (selectedCoverImagePath.isNotEmpty()) {
                                    ProjectCover(
                                        type = CoverType.IMAGE,
                                        imagePath = selectedCoverImagePath,
                                        alpha = coverAlpha,
                                        offsetX = coverOffsetX,
                                        offsetY = coverOffsetY
                                    )
                                } else existingCover
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        // 拖拽手势调整图片偏移
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, drag ->
                                                change.consume()
                                                coverOffsetX += drag.x
                                                coverOffsetY += drag.y
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewCover != null && previewCover.type == CoverType.IMAGE && previewCover.imagePath.isNotEmpty()) {
                                        val imgFile = File(previewCover.imagePath)
                                        if (imgFile.exists()) {
                                            // 容器内裁剪显示图片，应用偏移
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(12.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                SubcomposeAsyncImage(
                                                    model = imgFile,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer {
                                                            alpha = coverAlpha
                                                            translationX = coverOffsetX
                                                            translationY = coverOffsetY
                                                        }
                                                )
                                            }
                                            // 提示文字（半透明）
                                            Text(
                                                "拖拽调整位置",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 8.dp)
                                                    .background(
                                                        Color.Black.copy(alpha = 0.4f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        } else {
                                            Text("图片文件不存在", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        Text("未选择图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // 偏移量显示和重置按钮
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "偏移: X=${coverOffsetX.toInt()}, Y=${coverOffsetY.toInt()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = {
                                            coverOffsetX = 0f
                                            coverOffsetY = 0f
                                        }
                                    ) {
                                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("重置位置")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // 透明度滑块
                                Text(
                                    text = "透明度: ${(coverAlpha * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Slider(
                                    value = coverAlpha,
                                    onValueChange = { coverAlpha = it },
                                    valueRange = 0.3f..1.0f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showCoverImagePicker = true }
                                    ) {
                                        Icon(Icons.Filled.Image, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("选择图片")
                                    }
                                    // 清除封面按钮
                                    TextButton(
                                        onClick = {
                                            settingsManager.setProjectCover(project.id, null, context)
                                            showCoverEditor = null
                                        }
                                    ) {
                                        Text("清除封面", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (coverTabIndex) {
                            0 -> {
                                // 保存纯色封面
                                val cover = ProjectCover(
                                    type = CoverType.SOLID_COLOR,
                                    colorValue = selectedColor.toInt(),
                                    alpha = coverAlpha
                                )
                                settingsManager.setProjectCover(project.id, cover, context)
                                showCoverEditor = null
                            }
                            1 -> {
                                // 图片封面：点击确定时才复制图片到项目目录（选中时仅预览，不立刻复制）
                                if (selectedCoverImagePath.isNotEmpty()) {
                                    val srcFile = File(selectedCoverImagePath)
                                    if (srcFile.exists()) {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val projectDir = File(project.path)
                                                val ext = srcFile.extension.ifEmpty { "jpg" }
                                                val coverFile = File(projectDir, "_cover.$ext")
                                                // 源文件不在项目目录才需要复制（避免重复复制已存在的封面）
                                                if (srcFile.absolutePath != coverFile.absolutePath) {
                                                    FileInputStream(srcFile).use { input ->
                                                        FileOutputStream(coverFile).use { output ->
                                                            input.copyTo(output)
                                                        }
                                                    }
                                                }
                                                val finalCoverPath = coverFile.absolutePath
                                                withContext(Dispatchers.Main) {
                                                    val cover = ProjectCover(
                                                        type = CoverType.IMAGE,
                                                        imagePath = finalCoverPath,
                                                        alpha = coverAlpha,
                                                        offsetX = coverOffsetX,
                                                        offsetY = coverOffsetY
                                                    )
                                                    settingsManager.setProjectCover(project.id, cover, context)
                                                    // 触发封面变更事件
                                                    EventManager.fireEvent(
                                                        PluginEvents.ON_PROJECT_COVER_CHANGED,
                                                        project.id, "image", finalCoverPath
                                                    )
                                                    showCoverEditor = null
                                                }
                                            } catch (e: Exception) {
                                                LogCatcher.e("MainScreen", "保存封面图片失败", e)
                                                withContext(Dispatchers.Main) {
                                                    showToast("保存封面失败: ${e.message}")
                                                }
                                            }
                                        }
                                    } else {
                                        showToast("图片文件不存在")
                                    }
                                } else {
                                    showCoverEditor = null
                                }
                            }
                            else -> {
                                showCoverEditor = null
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCoverEditor = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )

        // 封面图片选择器
        if (showCoverImagePicker) {
            FilePickerDialog(
                initialPath = Environment.getExternalStorageDirectory().absolutePath,
                selectionMode = SelectionMode.FILE,
                title = "选择封面图片",
                allowedExtensions = listOf("jpg", "jpeg", "png", "webp"),
                onDismiss = { showCoverImagePicker = false },
                onFileSelected = { filePath ->
                    showCoverImagePicker = false
                    // 选中图片仅用于预览，不立刻复制/裁剪，点击"确定"时才真正保存
                    val srcFile = File(filePath)
                    if (srcFile.exists()) {
                        // 重置偏移（新图片从居中位置开始）
                        coverOffsetX = 0f
                        coverOffsetY = 0f
                        selectedCoverImagePath = filePath
                    } else {
                        showToast("图片文件不存在")
                    }
                }
            )
        }
    }

    // ==================== 备份确认对话框 ====================
    showBackupProject?.let { project ->
        AlertDialog(
            onDismissRequest = { showBackupProject = null },
            title = { Text("备份项目") },
            text = { Text("确定备份项目「${project.name}」吗？\n备份将保存为ZIP文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackupProject = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                val backupDir = settingsManager.getBackupDirectory()
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val backupName = "${project.name}_$timestamp"
                                val zipFile = ProjectUtil.backupProjectToZip(
                                    File(project.path), backupDir, backupName
                                )
                                withContext(Dispatchers.Main) {
                                    if (zipFile != null) {
                                        showToast("备份完成: ${zipFile.absolutePath}")
                                        // 触发备份成功事件
                                        EventManager.fireEvent(
                                            PluginEvents.ON_PROJECT_BACKUP,
                                            project.id, zipFile.absolutePath, true
                                        )
                                    } else {
                                        showToast("备份失败")
                                        // 触发备份失败事件
                                        EventManager.fireEvent(
                                            PluginEvents.ON_PROJECT_BACKUP,
                                            project.id, "", false
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                LogCatcher.e("MainScreen", "备份失败", e)
                                withContext(Dispatchers.Main) {
                                    showToast("备份失败: ${e.message}")
                                    // 触发备份失败事件
                                    EventManager.fireEvent(
                                        PluginEvents.ON_PROJECT_BACKUP,
                                        project.id, "", false
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupProject = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // ==================== 保存为模板对话框 ====================
    showSaveTemplate?.let { project ->
        var templateName by remember(project.id) { mutableStateOf(templateNameInput.ifBlank { project.name }) }
        AlertDialog(
            onDismissRequest = { showSaveTemplate = null },
            title = { Text("保存为模板") },
            text = {
                Column {
                    Text("将项目「${project.name}」保存为可复用模板", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("模板名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = templateName.trim()
                        if (name.isBlank()) {
                            showToast("请输入模板名称")
                            return@TextButton
                        }
                        templateNameInput = name
                        showSaveTemplate = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                val result = ProjectUtil.saveProjectAsTemplate(File(project.path), name, context)
                                withContext(Dispatchers.Main) {
                                    if (result != null) {
                                        showToast("模板已保存: ${result.name}")
                                    } else {
                                        showToast("保存模板失败")
                                    }
                                }
                            } catch (e: Exception) {
                                LogCatcher.e("MainScreen", "保存模板失败", e)
                                withContext(Dispatchers.Main) {
                                    showToast("保存失败: ${e.message}")
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTemplate = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // ==================== 桌面快捷方式确认对话框 ====================
    showShortcutConfirm?.let { project ->
        AlertDialog(
            onDismissRequest = { showShortcutConfirm = null },
            title = { Text("添加桌面快捷方式") },
            text = { Text("将「${project.name}」添加到桌面？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShortcutConfirm = null
                        val result = ShortcutHelper.createShortcut(context, project)
                        showToast(if (result) "已请求添加快捷方式" else "添加失败，可能不支持或被系统拒绝")
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showShortcutConfirm = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // ==================== 批量操作 - 分类选择对话框 ====================
    if (showBatchCategory) {
        AlertDialog(
            onDismissRequest = { showBatchCategory = false },
            title = { Text("批量设置分类") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 无分类选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 批量移除分类：从所有分类中移除选中项目
                                val updated = homeCategories.map { cat ->
                                    cat.copy(projectIds = cat.projectIds - multiSelectedProjectIds)
                                }
                                settingsManager.updateSettings(
                                    currentSettings.copy(homeCategories = updated)
                                )
                                scope.launch(Dispatchers.IO) {
                                    settingsManager.saveSettings(context)
                                }
                                showBatchCategory = false
                                showToast("已更新${multiSelectedProjectIds.size}个项目的分类")
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("无分类（从所有分类中移除）")
                    }
                    homeCategories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 批量添加到该分类（从其他分类移除）
                                    val updated = homeCategories.map { c ->
                                        when {
                                            c.id == cat.id -> c.copy(projectIds = c.projectIds + multiSelectedProjectIds)
                                            else -> c.copy(projectIds = c.projectIds - multiSelectedProjectIds)
                                        }
                                    }
                                    settingsManager.updateSettings(
                                        currentSettings.copy(homeCategories = updated)
                                    )
                                    scope.launch(Dispatchers.IO) {
                                        settingsManager.saveSettings(context)
                                    }
                                    showBatchCategory = false
                                    showToast("已更新${multiSelectedProjectIds.size}个项目的分类")
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(cat.color.toInt()))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat.name)
                        }
                    }
                    if (homeCategories.isEmpty()) {
                        Text(
                            "还没有分类，请先创建分类",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchCategory = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // ==================== 批量操作 - 标签多选对话框 ====================
    if (showBatchTags) {
        // 批量标签状态：合并所有选中项目的标签作为初始值
        var batchSelectedTags by remember {
            val common = multiSelectedProjectIds
                .map { projectTagsMap[it] ?: emptySet() }
                .fold<Set<String>, Set<String>?>(null) { acc, set ->
                    if (acc == null) set else acc.intersect(set)
                } ?: emptySet()
            mutableStateOf(common)
        }
        AlertDialog(
            onDismissRequest = { showBatchTags = false },
            title = { Text("批量设置标签（${multiSelectedProjectIds.size}个项目）") },
            text = {
                if (homeProjectTags.isEmpty()) {
                    Text(
                        "还没有标签，请先创建标签",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(homeProjectTags) { tag ->
                            val isChecked = tag.id in batchSelectedTags
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        batchSelectedTags = if (isChecked) batchSelectedTags - tag.id else batchSelectedTags + tag.id
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isChecked, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(tag.color.toInt()))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tag.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 为每个选中项目设置标签
                        multiSelectedProjectIds.forEach { pid ->
                            settingsManager.setProjectTags(pid, batchSelectedTags, context)
                        }
                        showBatchTags = false
                        showToast("已更新${multiSelectedProjectIds.size}个项目的标签")
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchTags = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // ==================== 导入备份文件选择器 ====================
    if (showRestorePicker) {
        FilePickerDialog(
            initialPath = settingsManager.getBackupDirectory().absolutePath,
            selectionMode = SelectionMode.FILE,
            title = "选择备份文件",
            allowedExtensions = listOf("zip"),
            onDismiss = { showRestorePicker = false },
            onFileSelected = { filePath ->
                showRestorePicker = false
                val zipFile = File(filePath)
                val restoreName = zipFile.nameWithoutExtension
                    .replace(Regex("_\\d{8}_\\d{6}$"), "") // 去掉时间戳后缀
                // 保存待还原文件信息
                selectedImportFile = zipFile
                importSettingsData = mapOf("restoreName" to restoreName)
                // 弹出还原确认对话框
                showRestoreConfirmDialog = true
            }
        )
    }

    // ==================== 还原备份确认对话框 ====================
    if (showRestoreConfirmDialog && selectedImportFile != null) {
        val restoreName = (importSettingsData?.get("restoreName") as? String) ?: selectedImportFile!!.nameWithoutExtension
        var restoreNameInput by remember { mutableStateOf(restoreName) }
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = { Text("还原备份") },
            text = {
                Column {
                    Text("将「${selectedImportFile!!.name}」还原为新项目：")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreNameInput,
                        onValueChange = { restoreNameInput = it },
                        label = { Text("项目名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = restoreNameInput.trim()
                        if (name.isBlank()) {
                            showToast("请输入项目名称")
                            return@TextButton
                        }
                        showRestoreConfirmDialog = false
                        scope.launch(Dispatchers.IO) {
                            try {
                                var targetDir = File(primaryProjectsPath, name)
                                var counter = 1
                                while (targetDir.exists()) {
                                    counter++
                                    targetDir = File(primaryProjectsPath, "${name}_$counter")
                                }
                                val success = ProjectUtil.restoreProjectFromZip(selectedImportFile!!, targetDir)
                                withContext(Dispatchers.Main) {
                                    if (success) {
                                        showToast("还原成功: ${targetDir.name}")
                                        // 触发项目恢复成功事件（projectId为新项目目录名）
                                        EventManager.fireEvent(
                                            PluginEvents.ON_PROJECT_RESTORE,
                                            targetDir.name, selectedImportFile!!.absolutePath, true
                                        )
                                        scope.launch { onRefreshProjects() }
                                    } else {
                                        showToast("还原失败")
                                        // 触发项目恢复失败事件
                                        EventManager.fireEvent(
                                            PluginEvents.ON_PROJECT_RESTORE,
                                            "", selectedImportFile!!.absolutePath, false
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                LogCatcher.e("MainScreen", "还原备份失败", e)
                                withContext(Dispatchers.Main) {
                                    showToast("还原失败: ${e.message}")
                                    // 触发项目恢复失败事件
                                    EventManager.fireEvent(
                                        PluginEvents.ON_PROJECT_RESTORE,
                                        "", selectedImportFile!!.absolutePath, false
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text("还原")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
