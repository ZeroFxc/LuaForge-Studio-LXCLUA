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
    PLUGINS,
    TRASH
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

    // 记录上一个页面ID，用于 ON_PAGE_CHANGED 事件
    var lastPageId by remember { mutableStateOf("main") }

    // WebUI 状态（在 MainApp 顶层作用域，覆盖所有页面包括编辑器）
    var webUIPluginId by remember { mutableStateOf<String?>(null) }
    var webUIPage by remember { mutableStateOf("index.html") }

    var projectItems by remember { mutableStateOf(emptyList<ProjectItem>()) }
    val toast = rememberNonBlockingToastState()
    val context = LocalContext.current

    // 监听顶层页面导航变化，触发 ON_PAGE_CHANGED 事件
    LaunchedEffect(currentScreen, webUIPluginId) {
        val newPageId = when {
            webUIPluginId != null -> "webui"
            currentScreen == AppScreen.MAIN -> "main"
            currentScreen == AppScreen.NEW_PROJECT -> "new_project"
            currentScreen == AppScreen.EDITOR -> "editor"
            else -> "main"
        }
        if (newPageId != lastPageId) {
            EventManager.fireEvent(PluginEvents.ON_PAGE_CHANGED, newPageId, lastPageId)
            lastPageId = newPageId
        }
    }

    // 返回主页时从磁盘重新加载设置，自愈恢复可能被异常修改的内存状态
    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.MAIN) {
            SettingsManager.reloadSettingsFromDisk(context)
        }
    }

    // 监听插件导航请求
    val navTarget by com.luaforge.studio.lxclua.plugin.state.NavigationState.pendingNavTarget
    val pendingProjectId by com.luaforge.studio.lxclua.plugin.state.NavigationState.pendingOpenProjectId
    LaunchedEffect(navTarget) {
        navTarget?.let { target ->
            when (target) {
                "main" -> currentScreen = AppScreen.MAIN
                "new_project" -> currentScreen = AppScreen.NEW_PROJECT
                "editor" -> {
                    // 如果有指定要打开的项目ID，查找并设置selectedProject
                    val pid = pendingProjectId
                    if (pid != null) {
                        val project = projectItems.find { it.id == pid }
                        if (project != null) {
                            selectedProject = project
                            SettingsManager.saveLastOpenedProject(project.id, context)
                            SettingsManager.pushRecentProject(project.id, context)
                            ShortcutHelper.updateShortcuts(
                                context,
                                SettingsManager.currentSettings.recentProjects.mapNotNull { rid ->
                                    projectItems.find { it.id == rid }
                                }
                            )
                            // 触发项目打开事件
                            EventManager.fireEvent(
                                PluginEvents.ON_PROJECT_OPEN,
                                project.id, project.name, project.path
                            )
                        }
                    }
                    currentScreen = AppScreen.EDITOR
                }
            }
            com.luaforge.studio.lxclua.plugin.state.NavigationState.clearNavTarget()
        }
    }

    // 监听 WebUI 导航请求
    val webUINavPluginId by com.luaforge.studio.lxclua.plugin.state.NavigationState.pendingWebUIPluginId
    LaunchedEffect(webUINavPluginId) {
        webUINavPluginId?.let { id ->
            webUIPluginId = id
            webUIPage = com.luaforge.studio.lxclua.plugin.state.NavigationState.pendingWebUIPage.value
                ?: "index.html"
        }
    }


    val scope = rememberCoroutineScope()

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

    // 处理快捷方式Intent和自动打开上次项目
    LaunchedEffect(Unit) {
        // 先刷新项目列表
        refreshProjects()
        // 检查Intent是否携带项目ID（桌面快捷方式）
        val activity = context as? android.app.Activity
        val intentProjectId = ShortcutHelper.getProjectIdFromIntent(activity?.intent)
        val targetId = if (!intentProjectId.isNullOrEmpty()) {
            intentProjectId
        } else if (settings.autoOpenLastProject && settings.lastOpenedProjectId.isNotEmpty()) {
            settings.lastOpenedProjectId
        } else {
            null
        }
        if (targetId != null) {
            val project = projectItems.find { it.id == targetId }
            if (project != null) {
                // 打开前检查拦截器
                val intercepted = EventManager.checkIntercepted(
                    PluginEvents.ON_PROJECT_OPEN,
                    project.id, project.name, project.path
                )
                if (!intercepted) {
                    selectedProject = project
                    SettingsManager.saveLastOpenedProject(project.id, context)
                    SettingsManager.pushRecentProject(project.id, context)
                    currentScreen = AppScreen.EDITOR
                    // 导航成功后通知监听器
                    EventManager.fireEvent(
                        PluginEvents.ON_PROJECT_OPEN,
                        project.id, project.name, project.path
                    )
                }
            }
        }
    }

    val toastPosition = settings.toastPosition

    val toastTransitionSpec: AnimatedContentTransitionScope<ToastData?>.() -> ContentTransform = {
        TransitionUtil.createToastPositionedScaleTransition(toastPosition)
    }

    BackHandler(enabled = currentScreen != AppScreen.MAIN) {
        // 先执行拦截检查，参考cancelBuild模式
        val intercepted = EventManager.fireEventWithIntercept(
            PluginEvents.ON_BACK_PRESSED
        )
        if (intercepted) return@BackHandler
        
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
                            // 打开前检查拦截器
                            val intercepted = EventManager.checkIntercepted(
                                PluginEvents.ON_PROJECT_OPEN,
                                project.id, project.name, project.path
                            )
                            if (intercepted) return@MainScreen
                            selectedProject = project
                            SettingsManager.saveLastOpenedProject(project.id, context)
                            SettingsManager.pushRecentProject(project.id, context)
                            // 更新桌面快捷方式
                            ShortcutHelper.updateShortcuts(
                                context,
                                SettingsManager.currentSettings.recentProjects.mapNotNull { pid ->
                                    projectItems.find { it.id == pid }
                                }
                            )
                            currentScreen = AppScreen.EDITOR
                            // 导航成功后通知监听器
                            EventManager.fireEvent(
                                PluginEvents.ON_PROJECT_OPEN,
                                project.id, project.name, project.path
                            )
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
