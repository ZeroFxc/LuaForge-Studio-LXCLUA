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

            // ============ 回收站 ============
            BaseDrawerItem(
                label = "回收站",
                icon = Icons.Filled.Delete,
                iconContentDescription = "回收站",
                selected = currentContentType == MainContentType.TRASH,
                onClick = { onContentTypeChange(MainContentType.TRASH) }
            )

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
        // 触发应用销毁事件
        com.luaforge.studio.lxclua.plugin.state.EventManager.fireEvent(
            com.luaforge.studio.lxclua.plugin.state.PluginEvents.ON_APP_DESTROY
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