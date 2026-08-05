@file:OptIn(ExperimentalMaterial3Api::class)

package com.luaforge.studio.lxclua.ui.editor

import android.app.Activity
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nirithy.lxclua.LuaActivity
import com.luaforge.studio.lxclua.ProjectItem
import com.luaforge.studio.lxclua.R
import com.luaforge.studio.lxclua.files.FileTree
import com.luaforge.studio.lxclua.git.GitFileState
import com.luaforge.studio.lxclua.git.GitManager
import com.luaforge.studio.lxclua.git.GitStatusSummary
import com.luaforge.studio.lxclua.ui.analyse.AnalyseScreen
import com.luaforge.studio.lxclua.ui.attribute.AttributeScreen
import com.luaforge.studio.lxclua.ui.components.ColorPickerDialog
import com.luaforge.studio.lxclua.ui.components.EdgeSwipeDismissibleDrawer
import com.luaforge.studio.lxclua.ui.editor.viewmodel.EditorViewModel
import com.luaforge.studio.lxclua.ui.git.GitCloneDialog
import com.luaforge.studio.lxclua.ui.git.GitInitDialog
import com.luaforge.studio.lxclua.ui.git.GitScreen
import com.luaforge.studio.lxclua.ui.javaapi.JavaApiScreen
import com.luaforge.studio.lxclua.ui.settings.SettingsManager
import com.luaforge.studio.lxclua.utils.LogCatcher
import com.luaforge.studio.lxclua.utils.NonBlockingToastState
import com.luaforge.studio.lxclua.utils.TransitionUtil
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 定义滑动手势方向枚举
enum class SwipeDirection { UP, DOWN }

// 定义覆盖层密封类
sealed class OverlayScreen {
    object NONE : OverlayScreen()
    data class ANALYSE(val codeContent: String, val projectPath: String?) : OverlayScreen()
    data class JAVA_API(val initialClass: String? = null) : OverlayScreen()
    object ATTRIBUTE : OverlayScreen()
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CodeEditScreen(
    project: ProjectItem,
    onBack: () -> Unit,
    toast: NonBlockingToastState
) {
    var isAutoSaving by remember { mutableStateOf(false) }
    var autoSaveCompleted by remember { mutableStateOf(false) }

    val fileTreeDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isMoreMenuExpanded by remember { mutableStateOf(false) }

    val settingsManager = SettingsManager
    val currentSettings = settingsManager.currentSettings

    var previousFontSettings by remember {
        mutableStateOf(
            currentSettings.editorFontType to currentSettings.customFontPath
        )
    }

    val projectPath = project.path

    val viewModelKey = remember(project.path, project.createdDate.time) {
        "${project.path}_${project.createdDate.time}"
    }

    val viewModel: EditorViewModel = viewModel(key = viewModelKey)

    var showInstallDialog by remember { mutableStateOf(false) }
    var apkFilePath by remember { mutableStateOf<String?>(null) }
    var buildResultType by remember { mutableStateOf(BuildResultType.SUCCESS) }
    var buildResultMessage by remember { mutableStateOf<String?>(null) }
    var isBuilding by remember { mutableStateOf(false) }
    val buildLogLines = remember { mutableStateListOf<String>() }
    var showBuildLog by remember { mutableStateOf(false) }
    var isCompilingFile by remember { mutableStateOf(false) }
    var showInitialLoader by remember { mutableStateOf(true) }
    var showEditorContent by remember { mutableStateOf(false) }
    var tabBarRendered by remember { mutableStateOf(false) }
    val lastFileToOpen = remember { mutableStateOf<String?>(null) }

    var currentOverlay by remember { mutableStateOf<OverlayScreen>(OverlayScreen.NONE) }

    val currentFileName = remember(viewModel.activeFileIndex, viewModel.openFiles) {
        if (viewModel.activeFileIndex in viewModel.openFiles.indices) {
            viewModel.openFiles[viewModel.activeFileIndex].file.name
        } else {
            ""
        }
    }

    var previousProjectPath by remember { mutableStateOf<String?>(null) }
    var previousProjectTimestamp by remember { mutableStateOf<Long?>(null) }

    val density = LocalDensity.current
    val panelState = rememberDraggablePanelState(
        minHeight = with(density) { 88.dp.toPx() }
    )

    // 快捷功能相关状态
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileType by remember { mutableStateOf(context.getString(R.string.code_editor_file)) }
    var newFileName by remember { mutableStateOf("") }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(Color.Black) }
    var isBackingUp by remember { mutableStateOf(false) }
    var refreshFileTreeKey by remember { mutableStateOf(0) }

    // 后缀选择菜单状态（独立于输入框）
    var suffixMenuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // ========== 搜索面板状态 ==========
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var ignoreCase by remember { mutableStateOf(true) }
    var isReplaceVisible by remember { mutableStateOf(false) } // 替换栏展开状态，父组件管理避免面板隐藏时重置

    val onSearchTextChange: (String) -> Unit = { text ->
        searchText = text
        viewModel.searchText(text, ignoreCase)
    }
    val onReplaceTextChange: (String) -> Unit = { replaceText = it }
    val onIgnoreCaseChange: (Boolean) -> Unit = { newIgnoreCase ->
        ignoreCase = newIgnoreCase
        if (searchText.isNotEmpty()) {
            viewModel.searchText(searchText, newIgnoreCase)
        }
    }
    val onCloseSearch: () -> Unit = {
        isSearchVisible = false
        viewModel.stopSearch()
        searchText = ""
        replaceText = ""
    }
    val onSearchNext: () -> Unit = { viewModel.searchNext() }
    val onSearchPrev: () -> Unit = { viewModel.searchPrev() }
    val onReplaceCurrent: (String) -> Unit = { text -> viewModel.replaceCurrent(text) }
    val onReplaceAll: (String) -> Unit = { text -> viewModel.replaceAll(text) }
    // =================================

    // ========== 快捷功能栏可见性状态 ==========
    var quickBarVisible by remember { mutableStateOf(true) }

    // ========== Maven下载进度状态 ==========
    var showDownloadProgress by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var currentDownloadFile by remember { mutableStateOf("") }
    var currentDownloadIndex by remember { mutableStateOf(0) }
    var totalDownloadFiles by remember { mutableStateOf(0) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }
    // =================================

    // ========== 布局助手启动器 ==========
    val layoutHelperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val newContent = data?.getStringExtra("layout_result")
            if (newContent != null) {
                viewModel.replaceCurrentFileContent(newContent)
            }
        }
    }

    // ========== 保存滚动状态 ==========
    val quickActionScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val symbolBarScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }

    // 当切换到覆盖层页面时自动隐藏键盘
    LaunchedEffect(currentOverlay) {
        if (currentOverlay !is OverlayScreen.NONE) {
            val imm = context.getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentFocusView = (context as? Activity)?.currentFocus
            if (currentFocusView != null) {
                imm?.hideSoftInputFromWindow(currentFocusView.windowToken, 0)
                currentFocusView.clearFocus()
            } else {
                val decorView = (context as? Activity)?.window?.decorView
                if (decorView != null) {
                    imm?.hideSoftInputFromWindow(decorView.windowToken, 0)
                }
            }
        }
    }

    // 确保 viewModel 已经初始化
    LaunchedEffect(Unit) {
        if (!viewModel.isInitialized) {
            viewModel.initialize(context)
        }
    }

    androidx.compose.runtime.DisposableEffect(projectPath) {
        com.luaforge.studio.lxclua.plugin.PluginManager.activeViewModel = viewModel
        com.luaforge.studio.lxclua.plugin.PluginManager.activePanelState = panelState
        com.luaforge.studio.lxclua.plugin.PluginManager.currentProjectPath.value = projectPath
        // 触发编辑器初始化事件
        com.luaforge.studio.lxclua.plugin.PluginManager.notifyEvent(PluginEvents.ON_EDITOR_INIT, projectPath)
        EventManager.fireEvent(PluginEvents.ON_EDITOR_INIT, projectPath)
        com.luaforge.studio.lxclua.plugin.bridge.PluginShortcut.ensureSubscribed()
        onDispose {
            // 触发编辑器关闭事件和当前文件关闭事件
            com.luaforge.studio.lxclua.plugin.PluginManager.notifyEvent(PluginEvents.ON_EDITOR_CLOSE, projectPath)
            EventManager.fireEvent(PluginEvents.ON_EDITOR_CLOSE, projectPath)
            // 关闭所有打开的文件时触发 ON_FILE_CLOSE
            viewModel.openFiles.forEach { fileState ->
                EventManager.fireEvent(PluginEvents.ON_FILE_CLOSE, fileState.file.absolutePath)
            }
            com.luaforge.studio.lxclua.plugin.PluginManager.activeViewModel = null
            com.luaforge.studio.lxclua.plugin.PluginManager.activePanelState = null
            com.luaforge.studio.lxclua.plugin.PluginManager.currentProjectPath.value = null
        }
    }

    LaunchedEffect(currentSettings.editorFontType, currentSettings.customFontPath) {
        val currentFontSettings = currentSettings.editorFontType to currentSettings.customFontPath
        if (currentFontSettings != previousFontSettings) {
            viewModel.updateEditorFonts()
            previousFontSettings = currentFontSettings
        }
    }

    LaunchedEffect(projectPath, project.createdDate.time) {
        if (previousProjectPath != projectPath || previousProjectTimestamp != project.createdDate.time) {
            showInitialLoader = true
            showEditorContent = false
            tabBarRendered = false
            previousProjectPath = projectPath
            previousProjectTimestamp = project.createdDate.time

            if (!viewModel.isInitialized) {
                viewModel.initialize(context)
            }

            loadProjectFiles(
                viewModel = viewModel,
                projectPath = projectPath,
                projectName = project.name,
                enableTabHistory = currentSettings.enableTabHistory,
                lastFileToOpen = lastFileToOpen
            )

            showEditorContent = true
            showInitialLoader = false
            viewModel.onInitialLoaderShown()
        }
    }

    // 文本变化防抖：监听 textChangeVersion，300ms 内无新变化才触发 ON_TEXT_CHANGED 事件
    val textChangeVersion = viewModel.textChangeVersion
    LaunchedEffect(textChangeVersion) {
        if (textChangeVersion == 0) return@LaunchedEffect
        delay(300)
        val activeFile = viewModel.activeFileState ?: return@LaunchedEffect
        EventManager.fireEvent(
            PluginEvents.ON_TEXT_CHANGED,
            activeFile.file.absolutePath,
            activeFile.content
        )
    }

    // 监听导航到 API 阅览器的请求
    LaunchedEffect(viewModel.navigateToApiClass) {
        val className = viewModel.consumeNavigateToApiClass()
        if (className != null) {
            currentOverlay = OverlayScreen.JAVA_API(className)
        }
    }

    // 优先关闭覆盖层
    val isBackHandling = remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        if (isBackHandling.value) return@BackHandler
        scope.launch {
            when {
                isSearchVisible -> {
                    isSearchVisible = false
                    searchText = ""
                    replaceText = ""
                    viewModel.stopSearch()
                }
                showNewFileDialog -> {
                    showNewFileDialog = false
                    newFileName = ""
                }
                showColorPickerDialog -> {
                    showColorPickerDialog = false
                }
                fileTreeDrawerState.isOpen -> {
                    fileTreeDrawerState.close()
                }
                else -> {
                    isBackHandling.value = true
                    try {
                        viewModel.saveAllFilesSilently()
                        onBack()
                    } finally {
                        isBackHandling.value = false
                    }
                }
            }
        }
    }

    // ========== 构建项目 ==========
    val onBuildProjectAction: () -> Unit = {
        scope.launch {
            viewModel.saveAllFilesSilently()
            isBuilding = true
            buildLogLines.clear()
            showBuildLog = true
            val result = try {
                buildProject(context, projectPath) { logLine ->
                    buildLogLines.add(logLine)
                }
            } catch (e: Exception) {
                LogCatcher.e("CodeEditScreen", "构建协程异常", e)
                val errMsg = "error: ${context.getString(R.string.code_editor_build_exception, e.message)}"
                buildLogLines.add("[E] ${e.message}")
                errMsg
            }
            when {
                result.startsWith("cancelled:") -> {
                    // 构建被取消（插件回调 cancelBuild 或被用户取消）
                    buildResultType = BuildResultType.CANCELLED
                    buildResultMessage = result.substringAfter("cancelled:")
                    showInstallDialog = true
                }
                result.startsWith("error:") -> {
                    buildResultType = BuildResultType.ERROR
                    buildResultMessage = result.substringAfter("error: ")
                    showInstallDialog = true
                }
                else -> {
                    buildResultType = BuildResultType.SUCCESS
                    apkFilePath = result
                    buildResultMessage = result
                    showInstallDialog = true
                }
            }
            isBuilding = false
        }
    }

    // ========== 备份项目 ==========
    val onBackupProject: () -> Unit = {
        scope.launch {
            viewModel.saveAllFilesSilently()
            isBackingUp = true
            val result = try {
                backupProject(context, projectPath)
            } catch (e: Exception) {
                LogCatcher.e("CodeEditScreen", "备份协程异常", e)
                "error: ${context.getString(R.string.code_editor_backup_failed, e.message)}"
            }
            if (result.startsWith("error:")) {
                toast.showToast(context.getString(R.string.code_editor_backup_failed, result.substringAfter("error: ")))
                // 触发项目备份失败事件
                EventManager.fireEvent(
                    PluginEvents.ON_PROJECT_BACKUP,
                    projectPath, "", false
                )
            } else {
                toast.showToast(context.getString(R.string.code_editor_backup_success, result))
                // 触发项目备份成功事件
                EventManager.fireEvent(
                    PluginEvents.ON_PROJECT_BACKUP,
                    projectPath, result, true
                )
            }
            isBackingUp = false
        }
    }

    // ========== 新建文件/文件夹 ==========
    fun onCreateFileOrFolder() {
        if (newFileName.isBlank()) {
            scope.launch {
                toast.showToast(context.getString(R.string.code_editor_enter_file_name))
            }
            return
        }
        scope.launch {
            try {
                val activeFile = viewModel.activeFileState
                val baseDir = if (activeFile?.file?.exists() == true) {
                    activeFile.file.parentFile ?: File(projectPath)
                } else {
                    File(projectPath)
                }
                val targetPath = File(baseDir, newFileName)

                if (newFileType == context.getString(R.string.code_editor_file)) {
                    if (targetPath.exists()) {
                        toast.showToast(context.getString(R.string.code_editor_file_exists))
                        return@launch
                    }
                    targetPath.parentFile?.mkdirs()
                    val success = withContext(Dispatchers.IO) { targetPath.createNewFile() }
                    if (success) {
                        toast.showToast(context.getString(R.string.code_editor_file_created))
                        // 刷新文件树
                        refreshFileTreeKey++
                    } else {
                        toast.showToast(context.getString(R.string.code_editor_file_create_failed))
                    }
                } else { // 文件夹
                    if (targetPath.exists()) {
                        toast.showToast(context.getString(R.string.code_editor_folder_exists))
                        return@launch
                    }
                    val success = withContext(Dispatchers.IO) { targetPath.mkdirs() }
                    if (success) {
                        toast.showToast(context.getString(R.string.code_editor_folder_created))
                        // 刷新文件树
                        refreshFileTreeKey++
                    } else {
                        toast.showToast(context.getString(R.string.code_editor_folder_create_failed))
                    }
                }

                showNewFileDialog = false
                newFileName = ""
            } catch (e: Exception) {
                LogCatcher.e("CodeEditScreen", "创建文件/文件夹失败", e)
                toast.showToast(context.getString(R.string.code_editor_file_create_failed, e.message))
            }
        }
    }

    // ========== 颜色选择器回调 ==========
    val onColorSelected: (Color) -> Unit = { color ->
        val hexColor = colorToHex(color)
        viewModel.insertSymbolToCorrectEditor(hexColor)
        showColorPickerDialog = false
    }

    // ========== 布局助手启动逻辑 ==========
    fun onLaunchLayoutHelper() {
        val currentFile = viewModel.activeFileState?.file
        if (currentFile == null) {
            scope.launch {
                toast.showToast(context.getString(R.string.code_editor_no_active_file))
            }
            return
        }
        if (!currentFile.name.endsWith(".aly", ignoreCase = true)) {
            scope.launch {
                toast.showToast(context.getString(R.string.code_editor_current_file_not_supported))
            }
            return
        }

        val content = viewModel.activeFileState?.content ?: run {
            scope.launch {
                toast.showToast(context.getString(R.string.code_editor_cannot_get_content))
            }
            return
        }

        val layoutHelperPath = "${context.filesDir.absolutePath}/layouthelper/main.lua"
        val layoutHelperFile = File(layoutHelperPath)
        if (!layoutHelperFile.exists()) {
            scope.launch {
                toast.showToast(context.getString(R.string.code_editor_layout_helper_not_installed))
            }
            return
        }

        val intent = Intent(context, LuaActivity::class.java).apply {
            data = Uri.fromFile(layoutHelperFile)
            putExtra("layout_content", content)
            putExtra("luapath", currentFile.absolutePath)
        }

        layoutHelperLauncher.launch(intent)
    }

    // ========== 快捷功能列表（使用资源 ID） ==========
    val quickActions = remember {
        listOf(
            QuickAction(R.string.code_editor_open, "", "打开") {
                viewModel.incrementQuickActionFrequency("打开")
                scope.launch {
                    if (fileTreeDrawerState.isClosed) fileTreeDrawerState.open()
                }
            },
            QuickAction(R.string.save, "", "保存") {
                viewModel.incrementQuickActionFrequency("保存")
                scope.launch { viewModel.saveAllModifiedFiles(toast) }
            },
            QuickAction(R.string.code_editor_new, "", "新建") {
                viewModel.incrementQuickActionFrequency("新建")
                newFileType = context.getString(R.string.code_editor_file)
                newFileName = ""
                showNewFileDialog = true
            },
            QuickAction(R.string.code_editor_format, "", "格式化") {
                viewModel.incrementQuickActionFrequency("格式化")
                viewModel.formatCode()
            },
            QuickAction(R.string.code_editor_layout_helper, "", "布局助手") {
                viewModel.incrementQuickActionFrequency("布局助手")
                onLaunchLayoutHelper()
            },
            QuickAction(R.string.code_editor_project_property, "", "项目属性") {
                scope.launch {
                    viewModel.saveAllFilesSilently()
                    viewModel.incrementQuickActionFrequency("项目属性")
                    currentOverlay = OverlayScreen.ATTRIBUTE
                }
            },
            QuickAction(R.string.code_editor_build, "", "构建项目") {
                viewModel.incrementQuickActionFrequency("构建项目")
                onBuildProjectAction()
            },
            QuickAction(R.string.code_editor_analyse, "", "导入分析") {
                viewModel.incrementQuickActionFrequency("导入分析")
                val codeContent = viewModel.activeFileState?.content ?: ""
                currentOverlay = OverlayScreen.ANALYSE(codeContent, projectPath)
            },
            QuickAction(R.string.code_editor_api_viewer, "", "API阅览器") {
                viewModel.incrementQuickActionFrequency("API阅览器")
                currentOverlay = OverlayScreen.JAVA_API()
            },
            QuickAction(R.string.search, "", "搜索") {
                viewModel.incrementQuickActionFrequency("搜索")
                if (viewModel.openFiles.isNotEmpty()) {
                    isSearchVisible = !isSearchVisible
                    if (!isSearchVisible) {
                        onCloseSearch()
                    }
                } else {
                    scope.launch {
                        toast.showToast(context.getString(R.string.code_editor_no_active_file))
                    }
                }
            },
            QuickAction(R.string.code_editor_backup, "", "备份") {
                viewModel.incrementQuickActionFrequency("备份")
                onBackupProject()
            },
            QuickAction(R.string.code_editor_palette, "", "调色板") {
                viewModel.incrementQuickActionFrequency("调色板")
                showColorPickerDialog = true
            }
        )
    }

    val smartSortingEnabled by remember { derivedStateOf { currentSettings.smartSortingEnabled } }

    val pluginActions = com.luaforge.studio.lxclua.plugin.PluginManager.pluginQuickActions
    val combinedQuickActions = remember(quickActions, pluginActions.toList()) {
        quickActions + pluginActions
    }

    var sortedQuickActions by remember { mutableStateOf(combinedQuickActions) }
    LaunchedEffect(viewModel.isQuickActionFrequencyLoaded, smartSortingEnabled, combinedQuickActions) {
        sortedQuickActions = if (viewModel.isQuickActionFrequencyLoaded && smartSortingEnabled) {
            combinedQuickActions.sortedByDescending { viewModel.quickActionFrequencyMap[it.key] ?: 0 }
        } else {
            combinedQuickActions
        }
    }

    EdgeSwipeDismissibleDrawer(
        drawerState = fileTreeDrawerState,
        gesturesEnabled = true,
        drawerContent = {
            ProjectFileTree(
                projectPath = projectPath,
                viewModel = viewModel,
                drawerState = fileTreeDrawerState,
                refreshTrigger = refreshFileTreeKey
            )
        },
        content = {
            AnimatedContent(
                targetState = currentOverlay,
                transitionSpec = { TransitionUtil.createScreenTransition(targetState !is OverlayScreen.NONE) },
                label = "overlay_transition"
            ) { overlay ->
                when (overlay) {
                    is OverlayScreen.ANALYSE -> AnalyseScreen(
                        codeContent = overlay.codeContent,
                        projectPath = overlay.projectPath,
                        onBack = { currentOverlay = OverlayScreen.NONE },
                        toast = toast
                    )

                    is OverlayScreen.JAVA_API -> JavaApiScreen(
                        initialClass = overlay.initialClass,
                        onBack = { currentOverlay = OverlayScreen.NONE },
                        toast = toast
                    )

                    OverlayScreen.ATTRIBUTE -> AttributeScreen(
    projectPath = projectPath,
    onBack = { currentOverlay = OverlayScreen.NONE },
    onSaveComplete = {
        val settingsFile = File(projectPath, "settings.json")
        if (settingsFile.exists()) {
            scope.launch {
                val existingIndex =
                    viewModel.openFiles.indexOfFirst { it.file.absolutePath == settingsFile.absolutePath }
                if (existingIndex != -1) {
                    viewModel.closeFile(existingIndex)
                    delay(100)
                }
                viewModel.openFile(settingsFile, projectPath)
                val newIndex =
                    viewModel.openFiles.indexOfFirst { it.file.absolutePath == settingsFile.absolutePath }
                if (newIndex != -1) {
                    viewModel.changeActiveFileIndex(newIndex)
                }
            }
        }
    },
    toast = toast
)
                    OverlayScreen.NONE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Scaffold(
                                topBar = {
                                    EditorTopBar(
                                        projectName = project.name,
                                        currentFileName = currentFileName,
                                        drawerState = fileTreeDrawerState,
                                        onDrawerToggle = {
                                            scope.launch {
                                                if (fileTreeDrawerState.isOpen) fileTreeDrawerState.close()
                                                else fileTreeDrawerState.open()
                                            }
                                        },
                                        viewModel = viewModel,
                                        toast = toast,
                                        context = context,
                                        projectPath = projectPath,
                                        isMoreMenuExpanded = isMoreMenuExpanded,
                                        onMoreMenuExpandedChange = { isMoreMenuExpanded = it },
                                        isCompilingFile = isCompilingFile,
                                        onCompileFile = {
                                            scope.launch {
                                                compileCurrentFile(
                                                    viewModel = viewModel,
                                                    toast = toast,
                                                    context = context,
                                                    isCompilingFile = { isCompilingFile = it }
                                                )
                                            }
                                        },
                                        onBuildProject = onBuildProjectAction
                                    )
                                },
                                content = { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                    ) {
                                        EditorContent(
                                            modifier = Modifier.fillMaxSize(),
                                            showInitialLoader = showInitialLoader,
                                            showEditorContent = showEditorContent,
                                            isBuilding = isBuilding,
                                            isAutoSaving = isAutoSaving,
                                            isCompilingFile = isCompilingFile,
                                            viewModel = viewModel,
                                            onTabBarRendered = { tabBarRendered = true },
                                            lastFileToOpen = lastFileToOpen.value,
                                            panelState = panelState,
                                            fileTreeDrawerState = fileTreeDrawerState,
                                            quickActions = sortedQuickActions,
                                            isBackingUp = isBackingUp,
                                            isSearchVisible = isSearchVisible,
                                            searchText = searchText,
                                            onSearchTextChange = onSearchTextChange,
                                            replaceText = replaceText,
                                            onReplaceTextChange = onReplaceTextChange,
                                            ignoreCase = ignoreCase,
                                            onIgnoreCaseChange = onIgnoreCaseChange,
                                            onCloseSearch = onCloseSearch,
                                            onSearchNext = onSearchNext,
                                            onSearchPrev = onSearchPrev,
                                            onReplaceCurrent = onReplaceCurrent,
                                            onReplaceAll = onReplaceAll,
                                            toast = toast,
                                            quickActionScrollState = quickActionScrollState,
                                            symbolBarScrollState = symbolBarScrollState,
                                            quickBarVisible = quickBarVisible,
                                            onSwipe = { direction ->
                                                quickBarVisible = when (direction) {
                                                    SwipeDirection.UP -> false
                                                    SwipeDirection.DOWN -> true
                                                }
                                            },
                                            projectName = project.name,
                                            isReplaceVisible = isReplaceVisible,
                                            onReplaceVisibleChange = { isReplaceVisible = it },
                                            showBuildLog = showBuildLog,
                                            buildLogLines = buildLogLines,
                                            onCloseBuildLog = { showBuildLog = false }
                                        )
                                    }
                                }
                            )

                            BuildResultDialog(
                                showDialog = showInstallDialog,
                                resultType = buildResultType,
                                resultMessage = buildResultMessage,
                                onDismiss = {
                                    showInstallDialog = false
                                    apkFilePath = null
                                    buildResultMessage = null
                                },
                                onInstall = {
                                    apkFilePath?.let { filePath ->
                                        installApk(context, filePath, toast, scope)
                                    }
                                    showInstallDialog = false
                                    apkFilePath = null
                                    buildResultMessage = null
                                }
                            )

                            // Maven下载进度对话框
                            DownloadProgressDialog(
                                showDialog = showDownloadProgress,
                                onDismiss = { 
                                    // 用户可以选择取消构建，这里仅关闭对话框，构建仍在后台进行
                                    // 如果需要取消构建，需要更复杂的协程取消逻辑
                                },
                                progress = downloadProgress,
                                currentFile = currentDownloadFile,
                                currentIndex = currentDownloadIndex,
                                totalFiles = totalDownloadFiles,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            )

                            if (showNewFileDialog) {
                                val activeFile = viewModel.activeFileState
                                val baseDir = if (activeFile?.file?.exists() == true) {
                                    activeFile.file.parentFile ?: File(projectPath)
                                } else {
                                    File(projectPath)
                                }

                                // 计算相对路径显示，包含项目名
                                val projectName = project.name
                                val relativePath = if (baseDir.absolutePath.startsWith(projectPath)) {
                                    val rel = baseDir.absolutePath.substring(projectPath.length)
                                    if (rel.startsWith(File.separator)) rel.substring(1) else rel
                                } else {
                                    baseDir.absolutePath
                                }
                                val displayPath = if (relativePath.isNotEmpty()) {
                                    ".../$projectName/$relativePath"
                                } else {
                                    ".../$projectName"
                                }

                                AlertDialog(
                                    onDismissRequest = { showNewFileDialog = false },
                                    title = { Text(stringResource(R.string.code_editor_new)) },
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.code_editor_select_type), style = MaterialTheme.typography.bodyMedium)
                                            Spacer(modifier = Modifier.height(8.dp))

                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(
                                                    selected = newFileType == context.getString(R.string.code_editor_file),
                                                    onClick = { newFileType = context.getString(R.string.code_editor_file) },
                                                    label = { Text(stringResource(R.string.code_editor_file)) }
                                                )
                                                FilterChip(
                                                    selected = newFileType == context.getString(R.string.code_editor_folder),
                                                    onClick = { newFileType = context.getString(R.string.code_editor_folder) },
                                                    label = { Text(stringResource(R.string.code_editor_folder)) }
                                                )
                                            }

                                            Spacer(Modifier.height(16.dp))
                                            Text(stringResource(R.string.code_editor_enter_name), style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.height(8.dp))

                                            // 输入框 + 右侧 ExposedDropdownMenuBox 包裹的 IconButton
                                            OutlinedTextField(
                                                value = newFileName,
                                                onValueChange = { newFileName = it },
                                                label = { Text(stringResource(R.string.code_editor_enter_name)) },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .focusRequester(focusRequester),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(
                                                    onDone = { onCreateFileOrFolder() }
                                                ),
                                                trailingIcon = {
                                                    if (newFileType == context.getString(R.string.code_editor_file)) {
                                                        // 使用 ExposedDropdownMenuBox 将菜单锚定到 IconButton
                                                        ExposedDropdownMenuBox(
                                                            expanded = suffixMenuExpanded,
                                                            onExpandedChange = { suffixMenuExpanded = it }
                                                        ) {
                                                            IconButton(
                                                                onClick = { suffixMenuExpanded = true }
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.ArrowDropDown,
                                                                    contentDescription = stringResource(R.string.code_editor_choose_suffix)
                                                                )
                                                            }
                                                            ExposedDropdownMenu(
                                                                expanded = suffixMenuExpanded,
                                                                onDismissRequest = { suffixMenuExpanded = false },
                                                                modifier = Modifier.width(140.dp)
                                                            ) {
                                                                val commonExtensions = listOf(
                                                                    ".lua", ".aly", ".json", ".txt", ".md", ".html", ".css", ".js"
                                                                )
                                                                commonExtensions.forEach { ext ->
                                                                    DropdownMenuItem(
                                                                        text = { Text(ext) },
                                                                        onClick = {
                                                                            // 替换后缀逻辑
                                                                            val trimmed = newFileName.trim()
                                                                            val lastDotIndex = trimmed.lastIndexOf('.')
                                                                            newFileName = if (lastDotIndex != -1 && lastDotIndex > 0) {
                                                                                trimmed.take(
                                                                                    lastDotIndex
                                                                                ) + ext
                                                                            } else {
                                                                                trimmed + ext
                                                                            }
                                                                            suffixMenuExpanded = false
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            )

                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.code_editor_create_in, displayPath),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = { onCreateFileOrFolder() },
                                            enabled = newFileName.isNotBlank()
                                        ) { Text(stringResource(R.string.code_editor_create)) }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            onClick = {
                                                showNewFileDialog = false
                                                newFileName = ""
                                            }
                                        ) { Text(stringResource(R.string.cancel)) }
                                    }
                                )
                            }

                            if (showColorPickerDialog) {
                                ColorPickerDialog(
                                    title = stringResource(R.string.code_editor_palette),
                                    initialColor = selectedColor,
                                    onDismiss = { showColorPickerDialog = false },
                                    onColorSelected = onColorSelected
                                )
                            }

                            if (fileTreeDrawerState.isOpen) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Transparent)
                                        .clickable(
                                            indication = null,
                                            interactionSource = null
                                        ) { scope.launch { fileTreeDrawerState.close() } }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ProjectFileTree(
    projectPath: String,
    viewModel: EditorViewModel,
    drawerState: DrawerState,
    refreshTrigger: Int
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ===== Git 状态 =====
    var gitRepoDir by remember(projectPath) { mutableStateOf<File?>(null) }
    var gitBranch by remember { mutableStateOf("") }
    var gitSummary by remember { mutableStateOf<GitStatusSummary?>(null) }
    var gitStates by remember { mutableStateOf<Map<String, GitFileState>>(emptyMap()) }
    var gitRefreshKey by remember { mutableIntStateOf(0) }
    var showGitScreen by remember { mutableStateOf(false) }
    var showGitInit by remember { mutableStateOf(false) }
    var showGitClone by remember { mutableStateOf(false) }
    var gitBusy by remember { mutableStateOf(false) }
    var watchTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(projectPath, refreshTrigger, gitRefreshKey) {
        GitManager.ensureTempDir(context)
        val repo = withContext(Dispatchers.IO) {
            // 清理历史遗留的误创建的空 "null" 文件夹
            GitManager.cleanupNullFolder(File(projectPath))
            GitManager.findRepositoryDir(File(projectPath))
        }
        gitRepoDir = repo
        if (repo != null) {
            try {
                val st = withContext(Dispatchers.IO) { GitManager.status(repo) }
                val br = withContext(Dispatchers.IO) { GitManager.currentBranch(repo) }
                gitStates = st.fileStates.mapKeys { File(repo, it.key).path }
                gitSummary = st.summary
                gitBranch = br
            } catch (_: Exception) {
            }
        } else {
            gitStates = emptyMap()
            gitSummary = null
            gitBranch = ""
        }
    }

    fun gitOp(block: suspend () -> Unit) {
        if (gitBusy) return
        scope.launch {
            gitBusy = true
            try {
                GitManager.ensureTempDir(context)
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context, "Git: ${e.message ?: e.javaClass.simpleName}", android.widget.Toast.LENGTH_SHORT
                ).show()
            } finally {
                gitBusy = false
                gitRefreshKey++
            }
        }
    }

    // ===== 文件系统实时监听：自动刷新文件树与 Git 状态 =====
    DisposableEffect(projectPath) {
        var structJob: Job? = null
        var gitJob: Job? = null

        fun handleEvent(event: Int, path: String?) {
            // 忽略 .git 目录内部变化，避免 Git 操作自身触发噪声
            if (path != null && (path == ".git" || path.startsWith(".git/") || path.startsWith(".git\\"))) return
            scope.launch {
                // 内容变化 → 防抖刷新 Git 状态徽标
                gitJob?.cancel()
                gitJob = launch {
                    delay(500)
                    gitRefreshKey++
                }
                // 结构变化 → 防抖重新加载文件列表（保持展开状态）
                val type = event and 0xFFF
                if (type == FileObserver.CREATE || type == FileObserver.DELETE ||
                    type == FileObserver.MOVED_FROM || type == FileObserver.MOVED_TO
                ) {
                    structJob?.cancel()
                    structJob = launch {
                        delay(250)
                        watchTrigger++
                    }
                }
            }
        }

        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+ 支持递归监听整个目录树
            object : FileObserver(File(projectPath)) {
                override fun onEvent(event: Int, path: String?) = handleEvent(event, path)
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(projectPath) {
                override fun onEvent(event: Int, path: String?) = handleEvent(event, path)
            }
        }
        observer.startWatching()

        onDispose {
            observer.stopWatching()
            structJob?.cancel()
            gitJob?.cancel()
        }
    }

    val repo = gitRepoDir

    ModalDrawerSheet(modifier = Modifier.width(260.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                stringResource(R.string.code_editor_file_tree),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // ===== Git 状态栏 =====
        if (repo != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable {
                        showGitScreen = true
                        scope.launch { drawerState.close() }
                    },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.AccountTree,
                        contentDescription = stringResource(R.string.git_title),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            gitBranch.ifBlank { "HEAD" },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val summary = gitSummary
                        if (summary != null && !summary.isClean) {
                            Text(
                                stringResource(R.string.git_changes_count, summary.totalChanges),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    if (gitBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        val summary = gitSummary
                        if (summary != null && !summary.isClean) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary,
                                        shape = RoundedCornerShape(9.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    summary.totalChanges.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                TextButton(onClick = { showGitInit = true }, enabled = !gitBusy) {
                    Icon(
                        Icons.Filled.AccountTree,
                        null,
                        Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.git_init), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { showGitClone = true }, enabled = !gitBusy) {
                    Icon(
                        Icons.Filled.CloudDownload,
                        null,
                        Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.git_clone), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        FileTree(
            rootPath = projectPath,
            refreshTrigger = refreshTrigger,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            watchTrigger = watchTrigger,
            gitStates = gitStates,
            onGitStage = { file ->
                gitOp {
                    val repoDir = gitRepoDir ?: return@gitOp
                    val rel = GitManager.toRelativePath(repoDir, file.path) ?: return@gitOp
                    if (gitStates[file.path] == GitFileState.MISSING) {
                        GitManager.stageDeleted(repoDir, rel)
                    } else {
                        GitManager.stage(repoDir, rel)
                    }
                }
            },
            onGitUnstage = { file ->
                gitOp {
                    val repoDir = gitRepoDir ?: return@gitOp
                    val rel = GitManager.toRelativePath(repoDir, file.path) ?: return@gitOp
                    GitManager.unstage(repoDir, rel)
                }
            },
            onGitDiscard = { file ->
                gitOp {
                    val repoDir = gitRepoDir ?: return@gitOp
                    val rel = GitManager.toRelativePath(repoDir, file.path) ?: return@gitOp
                    GitManager.discard(repoDir, rel)
                }
            },
            onFileClick = { file ->
                viewModel.openFile(file, projectPath)
                scope.launch { drawerState.close() }
            },
            onFileRenamed = { oldFile, _ -> viewModel.handleFileRenamed(oldFile) },
            onFileDeleted = { file -> viewModel.handleFileDeleted(file) }
        )
    }

    // Git 全屏面板
    if (showGitScreen && repo != null) {
        GitScreen(
            repoDir = repo,
            onDismiss = { showGitScreen = false },
            onRepoChanged = { gitRefreshKey++ }
        )
    }

    // 初始化仓库
    if (showGitInit) {
        GitInitDialog(
            onConfirm = {
                showGitInit = false
                gitOp { GitManager.init(File(projectPath)) }
            },
            onDismiss = { showGitInit = false }
        )
    }

    // 克隆仓库
    if (showGitClone) {
        GitCloneDialog(
            onClone = { url, username, password ->
                showGitClone = false
                gitOp {
                    GitManager.cloneRepo(url, File(projectPath), username, password)
                }
            },
            onDismiss = { showGitClone = false }
        )
    }
}

@Composable
fun EditorContent(
    modifier: Modifier = Modifier,
    showInitialLoader: Boolean,
    showEditorContent: Boolean,
    isBuilding: Boolean,
    isAutoSaving: Boolean,
    isCompilingFile: Boolean,
    viewModel: EditorViewModel,
    onTabBarRendered: () -> Unit,
    lastFileToOpen: String?,
    panelState: DraggablePanelState,
    fileTreeDrawerState: DrawerState,
    quickActions: List<QuickAction>,
    isBackingUp: Boolean,
    isSearchVisible: Boolean,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    replaceText: String,
    onReplaceTextChange: (String) -> Unit,
    ignoreCase: Boolean,
    onIgnoreCaseChange: (Boolean) -> Unit,
    onCloseSearch: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchPrev: () -> Unit,
    onReplaceCurrent: (String) -> Unit,
    onReplaceAll: (String) -> Unit,
    toast: NonBlockingToastState,
    quickActionScrollState: ScrollState,
    symbolBarScrollState: ScrollState,
    quickBarVisible: Boolean,
    onSwipe: (SwipeDirection) -> Unit,
    projectName: String = "",
    isReplaceVisible: Boolean = false,
    onReplaceVisibleChange: (Boolean) -> Unit = {},
    showBuildLog: Boolean = false,
    buildLogLines: List<String> = emptyList(),
    onCloseBuildLog: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val hasOpenFiles = viewModel.openFiles.isNotEmpty()
    val isCompletionLoading by remember { derivedStateOf { viewModel.isCompletionDataLoading } }
    val completionProgress by remember { derivedStateOf { viewModel.completionDataProgress } }
    val buildLogListState = androidx.compose.foundation.lazy.rememberLazyListState()

    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = showInitialLoader,
            exit = fadeOut(tween(350))
        ) {
            ProjectLoadingOverlay(
                projectName = projectName,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        AnimatedVisibility(
            visible = !showInitialLoader && showEditorContent,
            enter = fadeIn(tween(500))
        ) {
            val staggeredStart = remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                staggeredStart.value = 1
            }

            Column(Modifier.fillMaxWidth().weight(1f)) {
                val showProgressBar =
                    isBuilding || isAutoSaving || isCompilingFile || isCompletionLoading || isBackingUp

                AnimatedVisibility(
                    visible = showProgressBar && staggeredStart.value >= 1,
                    enter = fadeIn(tween(250)) + expandVertically(animationSpec = tween(250))
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Butt
                    )
                }

                AnimatedVisibility(
                    visible = hasOpenFiles && quickBarVisible && staggeredStart.value >= 1,
                    enter = fadeIn(tween(300, delayMillis = 100)) + expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(300, delayMillis = 100)
                    ),
                    exit = fadeOut() + shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(200)
                    )
                ) {
                    QuickActionToolbar(
                        actions = quickActions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        scrollState = quickActionScrollState
                    )
                }

                AnimatedVisibility(visible = isSearchVisible) {
                    SearchPanel(
                        searchText = searchText,
                        onSearchTextChange = onSearchTextChange,
                        replaceText = replaceText,
                        onReplaceTextChange = onReplaceTextChange,
                        ignoreCase = ignoreCase,
                        onIgnoreCaseChange = onIgnoreCaseChange,
                        onClose = onCloseSearch,
                        onSearchNext = onSearchNext,
                        onSearchPrev = onSearchPrev,
                        onReplaceCurrent = { text -> onReplaceCurrent(text) },
                        onReplaceAll = { text -> onReplaceAll(text) },
                        isReplaceVisible = isReplaceVisible,
                        onReplaceVisibleChange = onReplaceVisibleChange
                    )
                }

                // 构建日志面板
                AnimatedVisibility(
                    visible = showBuildLog,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
                ) {
                    BuildLogPanel(
                        logLines = buildLogLines,
                        isBuilding = isBuilding,
                        listState = buildLogListState,
                        onClose = onCloseBuildLog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LocalDensity.current
                    val availableHeight =
                        remember(constraints.maxHeight) { constraints.maxHeight.toFloat() }
                    LaunchedEffect(availableHeight) {
                        if (availableHeight > 0) panelState.updateMaxHeight(
                            availableHeight
                        )
                    }

                    Column(Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = staggeredStart.value >= 1,
                            enter = fadeIn(tween(400, delayMillis = 200)) + slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = tween(400, delayMillis = 200)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            FileTabView(
                                viewModel = viewModel,
                                lastFileToOpen = lastFileToOpen,
                                onTabBarRendered = onTabBarRendered,
                                panelState = panelState,
                                onOpenFileTree = {
                                    scope.launch { if (fileTreeDrawerState.isClosed) fileTreeDrawerState.open() }
                                },
                                modifier = Modifier.fillMaxSize(),
                                onSwipe = onSwipe
                            )
                        }

                        AnimatedVisibility(
                            visible = hasOpenFiles && staggeredStart.value >= 1,
                            enter = fadeIn(tween(350, delayMillis = 350)) + slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec = tween(350, delayMillis = 350)
                            )
                        ) {
                            DraggableSymbolPanel(
                                viewModel = viewModel,
                                panelState = panelState,
                                hasOpenFiles = hasOpenFiles,
                                toast = toast,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectLoadingOverlay(
    projectName: String,
    modifier: Modifier = Modifier
) {
    val alpha = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        while (true) {
            alpha.animateTo(1f, animationSpec = tween(1000, easing = LinearEasing))
            alpha.animateTo(0.3f, animationSpec = tween(1000, easing = LinearEasing))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = stringResource(R.string.code_editor_loading_project),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha.value)
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private suspend fun loadProjectFiles(
    viewModel: EditorViewModel,
    projectPath: String,
    projectName: String,
    enableTabHistory: Boolean,
    lastFileToOpen: MutableState<String?>
) {
    if (!viewModel.isInitialized) throw IllegalStateException("ViewModel must be initialized before loading project files")
    viewModel.setCurrentProject(projectPath, projectName)

    val historyFiles = viewModel.getAllHistoryFiles()
    val validHistoryFiles = mutableListOf<File>()
    historyFiles.forEach { file ->
        if (file.exists() && file.isFile) validHistoryFiles.add(file)
        else viewModel.removeFileFromHistory(file.absolutePath)
    }

    if (validHistoryFiles.isNotEmpty()) {
        if (enableTabHistory) {
            val lastOpenedFile = viewModel.getLastOpenedFile()
            var targetIndex = 0
            viewModel.openMultipleFiles(validHistoryFiles, projectPath)
            lastOpenedFile?.let { file ->
                if (file.exists() && file.isFile) {
                    lastFileToOpen.value = file.absolutePath
                    val index =
                        validHistoryFiles.indexOfFirst { it.absolutePath == file.absolutePath }
                    if (index != -1) targetIndex = index
                }
            }
            delay(100)
            viewModel.changeActiveFileIndex(targetIndex)
        } else {
            val lastOpenedFile = viewModel.getLastOpenedFile()
            if (lastOpenedFile != null && lastOpenedFile.exists() && lastOpenedFile.isFile) {
                viewModel.openFileSync(lastOpenedFile, projectPath)
            } else if (validHistoryFiles.isNotEmpty()) {
                viewModel.openFileSync(validHistoryFiles[0], projectPath)
            }
        }
    } else {
        val mainLuaFile = File(projectPath, "main.lua")
        if (mainLuaFile.exists() && mainLuaFile.isFile) {
            viewModel.openFileSync(mainLuaFile, projectPath)
        } else {
            val luaFiles =
                File(projectPath).listFiles { _, name -> name.endsWith(".lua", ignoreCase = true) }
            if (luaFiles != null && luaFiles.isNotEmpty()) {
                luaFiles.sortBy { it.name }
                viewModel.openFileSync(luaFiles[0], projectPath)
            }
        }
    }
    viewModel.cleanupNonExistentFilesSync()
}

fun colorToHex(color: Color, includeAlpha: Boolean = false): String {
    val alpha = (color.alpha * 255).toInt()
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return if (includeAlpha) "#%02X%02X%02X%02X".format(alpha, red, green, blue)
    else "#%02X%02X%02X".format(red, green, blue)
}

@Composable
fun getFileTabIconResource(fileName: String): Int? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "lua" -> R.drawable.ic_language_lua
        "json" -> R.drawable.ic_code_json
        "aly" -> R.drawable.ic_code_braces
        else -> null
    }
}

@Composable
fun FileTabIcon(
    fileName: String,
    modifier: Modifier = Modifier
) {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val currentColor = LocalContentColor.current
    val iconResId = getFileTabIconResource(fileName)
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when {
            iconResId != null -> Icon(
                painter = painterResource(id = iconResId),
                contentDescription = stringResource(R.string.code_editor_file_icon_desc, extension.uppercase()),
                modifier = Modifier.fillMaxSize(),
                tint = currentColor
            )

            else -> {
                val iconVector = when (extension) {
                    "xml" -> Icons.Filled.Code
                    "txt" -> Icons.AutoMirrored.Filled.TextSnippet
                    "html" -> Icons.Filled.Html
                    "css" -> Icons.Filled.Css
                    "js" -> Icons.Filled.Javascript
                    "md" -> Icons.Filled.Description
                    "yml", "yaml" -> Icons.Filled.Settings
                    "properties" -> Icons.Filled.Settings
                    "gradle" -> Icons.Filled.Build
                    "gitignore" -> Icons.Filled.Code
                    "aly" -> Icons.Filled.Code
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile
                }
                Icon(
                    imageVector = iconVector,
                    contentDescription = stringResource(R.string.code_editor_file),
                    modifier = Modifier.fillMaxSize(),
                    tint = currentColor
                )
            }
        }
    }
}

@Composable
fun DownloadProgressDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    progress: Float,
    currentFile: String,
    currentIndex: Int,
    totalFiles: Int,
    downloadedBytes: Long,
    totalBytes: Long
) {
    if (!showDialog) return

    val formatBytes: (Long) -> String = { bytes ->
        if (bytes <= 0) "--" else {
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            if (mb >= 1) "%.2f MB".format(mb)
            else "%.2f KB".format(kb)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloading_dependencies)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 如果是解析阶段（total为0或1且index为0），显示不确定进度条
                val isResolving = totalFiles <= 1 && currentIndex == 0
                
                if (isResolving) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = currentFile.ifEmpty { stringResource(R.string.code_editor_preparing) },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.code_editor_analyzing_deps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 正常下载阶段
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // 文件信息
                    Text(
                        text = stringResource(R.string.download_progress_file, currentFile.takeLast(30)),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // 计数和大小
                    val downloadedSize = formatBytes(downloadedBytes)
                    val totalSize = formatBytes(totalBytes)
                    Text(
                        text = "$currentIndex / $totalFiles · $downloadedSize / $totalSize",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        dismissButton = {} // 不允许直接关闭，只能取消构建
    )
}

/**
 * 构建日志面板 - 显示在编辑器底部区域
 */
@Composable
private fun BuildLogPanel(
    logLines: List<String>,
    isBuilding: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 自动滚动到底部
    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.animateScrollToItem(logLines.size - 1)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isBuilding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_code_json),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(R.string.build_log_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isBuilding) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.build_log_close),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 日志内容
            if (logLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBuilding) "等待构建日志..." else "暂无日志",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    items(logLines) { line ->
                        val textColor = when {
                            line.contains("[E]") -> MaterialTheme.colorScheme.error
                            line.contains("[W]") -> Color(0xFFFFA726)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = textColor,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}