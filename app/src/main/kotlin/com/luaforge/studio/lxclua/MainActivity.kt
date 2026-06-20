@file:OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import com.luaforge.studio.lxclua.plugin.bridge.PluginNavigation
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.NavigationState
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import com.luaforge.studio.lxclua.plugin.state.SidebarItem
import com.luaforge.studio.lxclua.ui.editor.persistence.EditorStateUtil
import com.luaforge.studio.lxclua.ui.about.AboutScreen
import com.luaforge.studio.lxclua.ui.components.FilePickerDialog
import com.luaforge.studio.lxclua.ui.components.SelectionMode
import com.luaforge.studio.lxclua.ui.components.Toast
import com.luaforge.studio.lxclua.ui.editor.CodeEditScreen
import com.luaforge.studio.lxclua.ui.project.NewProjectScreen
import com.luaforge.studio.lxclua.ui.settings.DarkMode
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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// 屏幕枚举
enum class AppScreen {
    MAIN,
    NEW_PROJECT,
    EDITOR
}

// 项目数据类
data class ProjectItem(
    val id: String,
    val name: String,
    val path: String,
    val createdDate: Date = Date(),
    val modifiedDate: Date = Date()
)

// 主内容类型枚举
enum class MainContentType {
    PROJECTS,
    SETTINGS,
    ABOUT,
    PLUGINS
}

enum class ConflictAction {
    OVERWRITE, CLONE,
}

enum class DirPickerTarget {
    PRIMARY, ADDITIONAL
}

/** 替换附加目录时记录索引 */
data class DirReplaceInfo(val index: Int)

@Composable
fun MainApp() {
    TransparentSystemBars()

    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }
    var selectedProject by remember { mutableStateOf<ProjectItem?>(null) }

    // 监听插件导航请求
    val navTarget by com.luaforge.studio.lxclua.plugin.state.NavigationState.pendingNavTarget
    LaunchedEffect(navTarget) {
        navTarget?.let { target ->
            when (target) {
                "main" -> currentScreen = AppScreen.MAIN
                "new_project" -> currentScreen = AppScreen.NEW_PROJECT
                "editor" -> currentScreen = AppScreen.EDITOR
            }
            com.luaforge.studio.lxclua.plugin.state.NavigationState.clearNavTarget()
        }
    }

    // WebUI 状态（在 MainApp 顶层作用域，覆盖所有页面包括编辑器）
    var webUIPluginId by remember { mutableStateOf<String?>(null) }
    var webUIPage by remember { mutableStateOf("index.html") }

    // 监听 WebUI 导航请求
    val webUINavPluginId by com.luaforge.studio.lxclua.plugin.state.NavigationState.pendingWebUIPluginId
    LaunchedEffect(webUINavPluginId) {
        webUINavPluginId?.let { id ->
            webUIPluginId = id
            webUIPage = com.luaforge.studio.lxclua.plugin.state.NavigationState.pendingWebUIPage.value
                ?: "index.html"
        }
    }

    var projectItems by remember { mutableStateOf(emptyList<ProjectItem>()) }
    val toast = rememberNonBlockingToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val settings = SettingsManager.currentSettings

    val allProjectPaths by remember(settings.projectStoragePath, settings.additionalProjectPaths) {
        derivedStateOf {
            FileUtil.getAllProjectPaths(context)
        }
    }

    val primaryProjectsPath by remember(settings.projectStoragePath) {
        derivedStateOf {
            FileUtil.getProjectsPath(context).ifBlank {
                context.getExternalFilesDir(null)?.resolve("projects")?.absolutePath ?: ""
            }
        }
    }

    suspend fun refreshProjects() {
        ProjectUtil.loadProjectsFromDirectories(allProjectPaths) { newItems ->
            projectItems = newItems
        }
    }

    val toastPosition = settings.toastPosition

    val toastTransitionSpec: AnimatedContentTransitionScope<ToastData?>.() -> ContentTransform = {
        TransitionUtil.createToastPositionedScaleTransition(toastPosition)
    }

    BackHandler(enabled = currentScreen != AppScreen.MAIN) {
        when (currentScreen) {
            AppScreen.NEW_PROJECT -> {
                currentScreen = AppScreen.MAIN
                scope.launch { refreshProjects() }
            }
            AppScreen.EDITOR -> {
                currentScreen = AppScreen.MAIN
                scope.launch { refreshProjects() }
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = currentScreen,
            animationSpec = tween(
                durationMillis = 450,
                easing = TransitionUtil.decelerateEasing
            )
        ) { targetScreen ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .consumeWindowInsets(WindowInsets.ime)
            ) {
                when (targetScreen) {
                    AppScreen.MAIN -> MainScreen(
                        onNavigateToNewProject = { currentScreen = AppScreen.NEW_PROJECT },
                        onNavigateToEditor = { project ->
                            selectedProject = project
                            currentScreen = AppScreen.EDITOR
                        },
                        projectItems = projectItems,
                        onProjectItemsChanged = { newItems -> projectItems = newItems },
                        toast = toast,
                        allProjectPaths = allProjectPaths,
                        primaryProjectsPath = primaryProjectsPath,
                        onRefreshProjects = { refreshProjects() }
                    )
                    AppScreen.NEW_PROJECT -> {
                        NewProjectScreen(
                            onBack = {
                                currentScreen = AppScreen.MAIN
                                scope.launch { refreshProjects() }
                            },
                            onCreateProject = { newProjectData ->
                                LogCatcher.i("MainApp", "项目创建成功: ${newProjectData.projectName}")
                                scope.launch { refreshProjects() }
                            },
                            toast = toast
                        )
                    }
                    AppScreen.EDITOR -> {
                        selectedProject?.let { project ->
                            Column {
                                CodeEditScreen(
                                    project = project,
                                    onBack = {
                                        currentScreen = AppScreen.MAIN
                                        scope.launch { refreshProjects() }
                                    },
                                    toast = toast
                                )
                            }
                        } ?: run { SideEffect { currentScreen = AppScreen.MAIN } }
                    }
                }
            }
        }

        ToastHost(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 64.dp,
                    bottom = 64.dp,
                    start = 24.dp,
                    end = 24.dp
                ),
            alignment = when (toastPosition) {
                ToastPosition.TOP -> Alignment.TopCenter
                ToastPosition.BOTTOM -> Alignment.BottomCenter
            },
            hostState = toast.originalToastState,
            transitionSpec = toastTransitionSpec,
            toast = { toastData -> Toast(toastData) }
        )
    }

    // WebUI 全屏覆盖层（MainApp 顶层，覆盖所有页面包括编辑器）
    if (webUIPluginId != null) {
        val id = webUIPluginId!!
        com.luaforge.studio.lxclua.plugin.ui.PluginWebUIScreen(
            pluginId = id,
            page = webUIPage,
            onBack = {
                webUIPluginId = null
                webUIPage = "index.html"
                com.luaforge.studio.lxclua.plugin.state.NavigationState.onWebUIClosed()
            }
        )
    }
}

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

    // 搜索过滤后的项目列表
    val filteredProjects by remember(projectItems, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                projectItems
            } else {
                projectItems.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            it.path.contains(searchQuery, ignoreCase = true)
                }
            }
        }
    }

    // 分组并排序后的项目列表
    val displayedProjects by remember(filteredProjects, sortOrder, pinnedSet) {
        derivedStateOf {
            // 分为两组：置顶和未置顶
            val pinned = filteredProjects.filter { it.id in pinnedSet }
            val unpinned = filteredProjects.filter { it.id !in pinnedSet }

            // 定义排序比较器
            val comparator = when (sortOrder) {
                SortOrder.NAME_ASC -> compareBy<ProjectItem> { it.name.lowercase() }
                SortOrder.NAME_DESC -> compareByDescending<ProjectItem> { it.name.lowercase() }
                SortOrder.DATE_MODIFIED_NEWEST -> compareByDescending<ProjectItem> { it.modifiedDate }
                SortOrder.DATE_MODIFIED_OLDEST -> compareBy<ProjectItem> { it.modifiedDate }
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

    // 重命名原 deleteProject 为 performDelete，用于实际删除操作（无动画）
    fun performDelete(projectId: String) {
        scope.launch {
            val project = projectItems.find { it.id == projectId }
            project?.let {
                try {
                    val projectDir = File(it.path)
                    if (projectDir.exists() && projectDir.isDirectory) {
                        projectDir.deleteRecursively()
                        EditorStateUtil.cleanProjectState(context, it.path)

                        // 从置顶集合中移除该项目
                        if (projectId in pinnedSet) {
                            val newPinnedSet = pinnedSet - projectId
                            val newSettings = currentSettings.copy(pinnedProjects = newPinnedSet)
                            SettingsManager.updateSettings(newSettings)
                            // 保存设置（在 IO 线程执行）
                            withContext(Dispatchers.IO) {
                                SettingsManager.saveSettings(context)
                            }
                        }

                        showToast(context.getString(R.string.project_deleted, it.name))
                        scope.launch { onRefreshProjects() }
                    }
                } catch (e: Exception) {
                    LogCatcher.e("MainScreen", "删除项目失败", e)
                    showToast(context.getString(R.string.delete_failed, e.message))
                }
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
                    if (isMultiSelectMode) {
                        EventManager.fireEvent(PluginEvents.ON_BACK_PRESSED)
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
                                    }
                                }
                            }

                            MainContentType.SETTINGS, MainContentType.ABOUT, MainContentType.PLUGINS -> {
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                when (currentContentType) {
                    MainContentType.PROJECTS -> {
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
                            expanded = showExtendedFab,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    MainContentType.SETTINGS, MainContentType.ABOUT, MainContentType.PLUGINS -> {
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
                            if (displayedProjects.isEmpty()) {
                                SideEffect {
                                    showExtendedFab = true
                                }
                                Column(
                                    modifier = Modifier.fillMaxSize(),
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
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        state = lazyListState,
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                                        EventManager.fireEvent(
                                                            PluginEvents.ON_PROJECT_CLICK,
                                                            project.id, project.name, project.path
                                                        )
                                                    } else {
                                                        onNavigateToEditor(project)
                                                    }
                                                },
                                                onLongClick = {
                                                    EventManager.fireEvent(
                                                        PluginEvents.ON_PROJECT_LONG_PRESS,
                                                        project.id, project.name, project.path
                                                    )
                                                },
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
                                                modifier = Modifier.animateItem()
                                            )
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
                                toast = toast
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
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_project_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_project_confirm), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.project_name_label, deleteProjectName), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.project_path_label, deleteProjectPath), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.delete_project_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
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
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
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
}

@SuppressLint("UnrememberedMutableState")
@Composable
fun ProjectCard(
    project: ProjectItem,
    isPinned: Boolean,
    isPendingDeletion: Boolean,
    onTogglePinned: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    isMultiSelectMode: Boolean = false,
    isSelectedInMultiSelect: Boolean = false,
    badge: PluginManager.BadgeInfo? = null,
    extraMenuItems: List<PluginManager.ProjectCardMenuItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    var manifestInfo by remember { mutableStateOf<ManifestInfo?>(null) }
    val iconFile = remember(project.path) {
        File(project.path, "icon.png")
    }
    val hasIcon by derivedStateOf {
        iconFile.exists() && iconFile.isFile
    }

    LaunchedEffect(project.path) {
        withContext(Dispatchers.IO) {
            val projectDir = File(project.path)
            if (projectDir.exists() && projectDir.isDirectory) {
                val settingsFile = File(projectDir, "settings.json")
                if (settingsFile.exists() && settingsFile.isFile) {
                    try {
                        val jsonString = settingsFile.readText()
                        val jsonMap = JsonUtil.parseObject(jsonString)

                        val label =
                            (jsonMap["application"] as? Map<*, *>)?.get("label") as? String
                        val packageName = jsonMap["package"] as? String
                        val versionName = jsonMap["versionName"] as? String
                        val debugMode =
                            (jsonMap["application"] as? Map<*, *>)?.get("debugmode") as? Boolean

                        manifestInfo = ManifestInfo(
                            label = label,
                            packageName = packageName,
                            versionName = versionName,
                            debugMode = debugMode
                        )
                    } catch (e: Exception) {
                        LogCatcher.e("ProjectCard", "加载项目设置失败", e)
                    }
                }
            }
        }
    }

    Card(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .pointerInput(Unit) {
                if (onSwipeLeft != null || onSwipeRight != null) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (totalDrag < -100f) onSwipeLeft?.invoke()
                            else if (totalDrag > 100f) onSwipeRight?.invoke()
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragStart = { totalDrag = 0f }
                    )
                }
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isMultiSelectMode && isSelectedInMultiSelect)
                colorScheme.primaryContainer
            else
                colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 1.dp
        )
    ) {
        AnimatedVisibility(
            visible = !isPendingDeletion,
            enter = fadeIn(animationSpec = tween(300)) + expandVertically(
                animationSpec = tween(300)
            ),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(
                animationSpec = tween(300)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isMultiSelectMode) {
                            Checkbox(
                                checked = isSelectedInMultiSelect,
                                onCheckedChange = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasIcon) {
                                SubcomposeAsyncImage(
                                    model = iconFile,
                                    contentDescription = stringResource(R.string.cd_project_icon),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    loading = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(colorScheme.primary.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = colorScheme.primary
                                            )
                                        }
                                    },
                                    error = {
                                        DefaultProjectIcon()
                                    }
                                )
                            } else {
                                DefaultProjectIcon()
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = manifestInfo?.label ?: project.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Column {
                                manifestInfo?.let { info ->
                                    info.packageName?.let { packageName ->
                                        Text(
                                            text = packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    info.versionName?.let { versionName ->
                                        Text(
                                            text = stringResource(R.string.version_label, versionName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (manifestInfo == null) {
                                Text(
                                    text = project.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    badge?.let { bdg ->
                        val badgeColor = Color(bdg.color.toULong())
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor.copy(alpha = 0.15f),
                            contentColor = badgeColor
                        ) {
                            Text(
                                text = bdg.text,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = stringResource(R.string.cd_pinned),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.code_editor_more),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isPinned) stringResource(R.string.unpin) else stringResource(R.string.pin)) },
                                    onClick = {
                                        showMenu = false
                                        onTogglePinned()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (isPinned) Icons.Filled.Clear else Icons.Filled.Star,
                                            contentDescription = null
                                        )
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share)) },
                                    onClick = {
                                        showMenu = false
                                        onShareClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Share,
                                            contentDescription = null
                                        )
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.delete), color = colorScheme.error)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDeleteClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = colorScheme.error
                                        )
                                    }
                                )

                                extraMenuItems.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.label) },
                                        onClick = {
                                            showMenu = false
                                            item.onClick(project.id, project.name, project.path)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Extension,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = colorScheme.outline.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.modified_time, dateFormat.format(project.modifiedDate)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )

                    if (manifestInfo?.debugMode == true) {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(colorScheme.error.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.debug_mode),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DefaultProjectIcon() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                MaterialTheme.shapes.medium
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = stringResource(R.string.cd_project_folder),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 宿主侧滑栏内容
 *
 * 渲染基础导航项（项目/设置/关于/插件管理）以及插件通过 PluginNavigation 注册的侧滑栏菜单项。
 * 插件项的 group 字段决定它们被插入到哪个分组：
 *  - "project"  -> 项目分组内
 *  - "settings" -> 设置分组内
 *  - "about"    -> 关于分组内
 *  - "plugins"  -> 插件管理分组内
 *  - "custom"   -> 独立的"自定义"分组（位于插件管理之后）
 *  - 其他       -> 自定义分组
 *
 * 插件侧滑栏项通过 NavigationState.pluginSidebarItems 响应式注册，列表变化会触发重组。
 */
@Composable
fun AppDrawerContent(
    currentContentType: MainContentType,
    onContentTypeChange: (MainContentType) -> Unit,
    copyrightYear: String,
    appVersionName: String
) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 280.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.Code,
                    contentDescription = stringResource(R.string.cd_logo),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 读取插件侧滑栏项，使 Compose 跟踪其变化
        val pluginItems = NavigationState.pluginSidebarItems
        val projectPluginItems = pluginItems.filter { it.group == PluginNavigation.TYPE_PROJECT }
        val settingsPluginItems = pluginItems.filter { it.group == PluginNavigation.TYPE_SETTINGS }
        val aboutPluginItems = pluginItems.filter { it.group == PluginNavigation.TYPE_ABOUT }
        val pluginsPluginItems = pluginItems.filter { it.group == PluginNavigation.TYPE_PLUGINS }
        val customPluginItems = pluginItems.filter { it.group == PluginNavigation.TYPE_CUSTOM }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ============ 项目分组 ============
            BaseDrawerItem(
                label = stringResource(R.string.projects),
                icon = Icons.Filled.Folder,
                iconContentDescription = stringResource(R.string.cd_project_folder),
                selected = currentContentType == MainContentType.PROJECTS,
                onClick = { onContentTypeChange(MainContentType.PROJECTS) }
            )
            for (item in projectPluginItems) {
                PluginSidebarDrawerItem(item = item)
            }

            // ============ 设置分组 ============
            BaseDrawerItem(
                label = stringResource(R.string.settings),
                icon = Icons.Filled.Settings,
                iconContentDescription = stringResource(R.string.settings),
                selected = currentContentType == MainContentType.SETTINGS,
                onClick = { onContentTypeChange(MainContentType.SETTINGS) }
            )
            for (item in settingsPluginItems) {
                PluginSidebarDrawerItem(item = item)
            }

            // ============ 关于分组 ============
            BaseDrawerItem(
                label = stringResource(R.string.about),
                icon = Icons.Filled.Info,
                iconContentDescription = stringResource(R.string.about),
                selected = currentContentType == MainContentType.ABOUT,
                onClick = { onContentTypeChange(MainContentType.ABOUT) }
            )
            for (item in aboutPluginItems) {
                PluginSidebarDrawerItem(item = item)
            }

            // ============ 插件管理分组 ============
            BaseDrawerItem(
                label = stringResource(R.string.drawer_plugin_management),
                icon = Icons.Filled.Extension,
                iconContentDescription = stringResource(R.string.drawer_plugin_management),
                selected = currentContentType == MainContentType.PLUGINS,
                onClick = { onContentTypeChange(MainContentType.PLUGINS) }
            )
            for (item in pluginsPluginItems) {
                PluginSidebarDrawerItem(item = item)
            }

            // ============ 插件自定义分组 ============
            if (customPluginItems.isNotEmpty()) {
                if (pluginItems.any { it.group != PluginNavigation.TYPE_CUSTOM }) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                }
                for (item in customPluginItems) {
                    PluginSidebarDrawerItem(item = item)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.copyright, copyrightYear),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.version, appVersionName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * 渲染一个基础的（宿主内置）侧滑栏菜单项
 *
 * @param label 显示文本
 * @param icon 图标
 * @param iconContentDescription 图标的无障碍描述
 * @param selected 是否处于选中态
 * @param onClick 点击回调
 */
@Composable
private fun BaseDrawerItem(
    label: String,
    icon: ImageVector,
    iconContentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Text(label, fontWeight = FontWeight.Medium)
        },
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                tint = if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedContainerColor = Color.Transparent,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/**
 * 渲染一个插件注册的侧滑栏菜单项
 *
 * 根据 item.iconName 查找对应的 Material 图标；
 * 调用 item.onClick 触发插件回调。
 *
 * @param item 由 PluginNavigation.addSidebarItem 注册的侧滑栏项
 */
@Composable
private fun PluginSidebarDrawerItem(item: SidebarItem) {
    val icon = getSidebarIconByName(item.iconName)
    NavigationDrawerItem(
        label = {
            Text(item.label, fontWeight = FontWeight.Medium)
        },
        selected = false,
        onClick = {
            try {
                item.onClick.run()
            } catch (e: Exception) {
                android.util.Log.e("AppDrawerContent", "插件侧滑栏点击回调执行失败: ${item.key}", e)
            }
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = item.label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedContainerColor = Color.Transparent,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/**
 * 将插件侧滑栏 iconName 映射到 Material 图标。
 * 与 FileTree 中的 getIconByName 保持一致的命名风格，便于插件复用。
 *
 * @param iconName 图标名称（不区分大小写），若为 null 或无法识别，返回默认描述符图标
 * @return 对应的 ImageVector
 */
private fun getSidebarIconByName(iconName: String?): ImageVector {
    if (iconName.isNullOrBlank()) {
        return Icons.Default.Extension
    }
    return when (iconName.lowercase(Locale.getDefault())) {
        "folder" -> Icons.Default.Folder
        "file" -> Icons.Default.Description
        "code" -> Icons.Default.Code
        "delete" -> Icons.Default.Delete
        "copy" -> Icons.Default.ContentCopy
        "rename" -> Icons.Default.DriveFileRenameOutline
        "new" -> Icons.Default.CreateNewFolder
        "settings" -> Icons.Default.Settings
        "info" -> Icons.Default.Info
        "edit" -> Icons.Default.Edit
        "share" -> Icons.Default.Share
        "download" -> Icons.Default.Download
        "upload" -> Icons.Default.Upload
        "star" -> Icons.Default.Star
        "favorite" -> Icons.Default.Favorite
        "bookmark" -> Icons.Default.Bookmark
        "home" -> Icons.Default.Home
        "search" -> Icons.Default.Search
        "filter" -> Icons.Default.FilterList
        "sort" -> Icons.AutoMirrored.Filled.Sort
        "refresh" -> Icons.Default.Refresh
        "export" -> Icons.Default.Share
        "import" -> Icons.Default.Download
        "send" -> Icons.Default.Send
        "open" -> Icons.Default.OpenInNew
        "close" -> Icons.Default.Close
        "check" -> Icons.Default.Check
        "alert", "warning" -> Icons.Default.Warning
        "error" -> Icons.Default.Error
        "success" -> Icons.Default.CheckCircle
        "play" -> Icons.Default.PlayArrow
        "pause" -> Icons.Default.Pause
        "stop" -> Icons.Default.Stop
        "plugin", "extension" -> Icons.Default.Extension
        "menu" -> Icons.Default.Menu
        "build" -> Icons.Default.Build
        "javascript", "js" -> Icons.Default.Code
        "html" -> Icons.Default.Code
        "css" -> Icons.Default.Code
        "lua" -> Icons.Default.Code
        "json" -> Icons.Default.Code
        "android" -> Icons.Default.PhoneAndroid
        "web" -> Icons.Default.Public
        "lock" -> Icons.Default.Lock
        "unlock" -> Icons.Default.LockOpen
        "database", "db" -> Icons.Default.Storage
        "mail", "email" -> Icons.Default.Email
        "person", "user" -> Icons.Default.Person
        "group", "team" -> Icons.Default.Group
        "notification" -> Icons.Default.Notifications
        "time", "clock" -> Icons.Default.Schedule
        "calendar" -> Icons.Default.CalendarToday
        "music", "audio" -> Icons.Default.MusicNote
        "image", "photo" -> Icons.Default.Image
        "video" -> Icons.Default.VideoFile
        "book" -> Icons.Default.MenuBook
        "help" -> Icons.Default.Help
        "about" -> Icons.Default.Info
        else -> Icons.Default.Extension
    }
}

data class ManifestInfo(
    val label: String? = null,
    val packageName: String? = null,
    val versionName: String? = null,
    val debugMode: Boolean? = null
)

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 初始化插件系统
    com.luaforge.studio.lxclua.plugin.PluginManager.init(this)
    com.luaforge.studio.lxclua.plugin.PluginManager.currentActivity = this

    // 触发应用启动事件
    com.luaforge.studio.lxclua.plugin.state.EventManager.fireEvent(
        com.luaforge.studio.lxclua.plugin.state.PluginEvents.ON_APP_START
    )

    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)

    val isVersionChanged = intent.getBooleanExtra("isVersionChanged", false)
    val newVersionName = intent.getStringExtra("newVersionName")
    val oldVersionName = intent.getStringExtra("oldVersionName")

    if (isVersionChanged) {
        LogCatcher.i("MainActivity", "检测到版本变更: $oldVersionName -> $newVersionName")
    }

    setContent {
        AppThemeWithObserver {
            SideEffect {
                val window = this@MainActivity.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                val currentSettings = SettingsManager.currentSettings
                val useDarkTheme = when (currentSettings.darkMode) {
                    DarkMode.FOLLOW_SYSTEM -> {
                        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                                Configuration.UI_MODE_NIGHT_YES
                    }
                    DarkMode.LIGHT -> false
                    DarkMode.DARK -> true
                }
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }

            val currentSettings = SettingsManager.currentSettings
            LaunchedEffect(currentSettings.projectStoragePath) {
                SettingsManager.ensureProjectDirectoryExists()
            }

            var shouldShowWelcome by remember { mutableStateOf(shouldShowWelcomeScreen(this@MainActivity)) }

            // 插件对话框宿主
            com.luaforge.studio.lxclua.ui.plugin.PluginDialogHost()

            Crossfade(targetState = shouldShowWelcome, animationSpec = tween(500)) { showWelcome ->
                if (showWelcome) {
                    WelcomeScreen(
                        onComplete = {
                            saveWelcomeCompleted(this@MainActivity)
                            shouldShowWelcome = false
                        },
                        onSkipWelcome = {
                            saveWelcomeCompleted(this@MainActivity)
                            shouldShowWelcome = false
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .consumeWindowInsets(WindowInsets.ime)
                    ) {
                        MainApp()
                        
                        // 插件通知横幅 — 浮在最上层，贯穿全局
                        com.luaforge.studio.lxclua.ui.plugin.PluginNotificationBanner()
                    }
                }
            }
        }
    }
}

    override fun onResume() {
        super.onResume()
        com.luaforge.studio.lxclua.plugin.PluginManager.currentActivity = this
        com.luaforge.studio.lxclua.plugin.state.EventManager.fireEvent(
            com.luaforge.studio.lxclua.plugin.state.PluginEvents.ON_APP_RESUME
        )
    }
    
    override fun onPause() {
        super.onPause()
        com.luaforge.studio.lxclua.plugin.state.EventManager.fireEvent(
            com.luaforge.studio.lxclua.plugin.state.PluginEvents.ON_APP_PAUSE
        )
        com.luaforge.studio.lxclua.plugin.PluginManager.currentActivity = null
    }

    override fun onDestroy() {
        super.onDestroy()
        com.luaforge.studio.lxclua.plugin.state.EventManager.fireEvent(
            com.luaforge.studio.lxclua.plugin.state.PluginEvents.ON_APP_STOP
        )
        com.luaforge.studio.lxclua.plugin.PluginManager.currentActivity = null
        com.luaforge.studio.lxclua.ui.editor.viewmodel.CompletionDataManager.clear()
        System.gc()
    }

    // ==================== 权限请求回调 ====================

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        com.luaforge.studio.lxclua.plugin.bridge.PluginSystem.handlePermissionResult(
            requestCode, permissions, grantResults
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 处理悬浮窗权限请求返回
        if (requestCode >= 1000) {
            com.luaforge.studio.lxclua.plugin.bridge.PluginSystem.handleOverlayResult(requestCode)
        }
    }
}