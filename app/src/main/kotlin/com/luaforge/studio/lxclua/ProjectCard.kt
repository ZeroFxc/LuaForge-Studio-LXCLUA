package com.luaforge.studio.lxclua

import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
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
import com.luaforge.studio.lxclua.ui.components.DefaultProjectIcon
import com.luaforge.studio.lxclua.ui.components.FilePickerDialog
import com.luaforge.studio.lxclua.ui.components.HighlightedText
import com.luaforge.studio.lxclua.ui.components.SelectionMode
import com.luaforge.studio.lxclua.ui.components.SmallIconArea
import com.luaforge.studio.lxclua.ui.components.TagsRow
import com.luaforge.studio.lxclua.ui.components.Toast
import com.luaforge.studio.lxclua.ui.components.isDark
import com.luaforge.studio.lxclua.ui.components.projectcard.SwipeToDismissBoxWrapper
import com.luaforge.studio.lxclua.ui.editor.CodeEditScreen
import com.luaforge.studio.lxclua.ui.project.NewProjectScreen
import com.luaforge.studio.lxclua.ui.settings.DarkMode
import com.luaforge.studio.lxclua.ui.settings.HomeDensity
import com.luaforge.studio.lxclua.ui.settings.ProjectCover
import com.luaforge.studio.lxclua.ui.settings.CoverType
import com.luaforge.studio.lxclua.ui.settings.ProjectTag
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 项目卡片组件 - 支持封面、标签、搜索高亮、滑动手势等功能
 * @param project 项目数据项
 * @param isPinned 是否置顶
 * @param isPendingDeletion 是否待删除（动画中）
 * @param onTogglePinned 置顶/取消置顶回调
 * @param onDeleteClick 删除回调
 * @param onShareClick 分享回调
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 * @param onSwipeLeft 左滑回调（插件事件广播）
 * @param onSwipeRight 右滑回调（插件事件广播）
 * @param onSetCategory 设置分类回调
 * @param isMultiSelectMode 是否多选模式
 * @param isSelectedInMultiSelect 多选模式下是否选中
 * @param badge 插件角标信息
 * @param extraMenuItems 插件额外菜单项（始终在最后）
 * @param isFlatMode 是否扁平列表模式
 * @param modifier 修饰符
 * @param cover 项目封面配置（SOLID_COLOR/IMAGE）
 * @param cornerRadius 卡片圆角大小
 * @param density 卡片密度（COMPACT/COMFORTABLE/LARGE）
 * @param tags 项目标签列表
 * @param showModifiedTime 是否显示修改时间
 * @param showPath 是否显示路径
 * @param highlightText 搜索高亮文本
 * @param onBackupClick 备份项目回调（null则不显示）
 * @param onSaveAsTemplateClick 保存为模板回调（null则不显示）
 * @param onSetCoverClick 设置封面回调（null则不显示）
 * @param onSetTagsClick 管理标签回调（null则不显示）
 * @param onCreateShortcutClick 添加到桌面回调（null则不显示）
 * @param enableSwipeGesture 是否启用滑动手势
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onSetCategory: (() -> Unit)? = null,
    isMultiSelectMode: Boolean = false,
    isSelectedInMultiSelect: Boolean = false,
    badge: PluginManager.BadgeInfo? = null,
    extraMenuItems: List<PluginManager.ProjectCardMenuItem> = emptyList(),
    isFlatMode: Boolean = false,
    modifier: Modifier = Modifier,
    cover: ProjectCover? = null,
    cornerRadius: Dp = 12.dp,
    density: HomeDensity = HomeDensity.COMFORTABLE,
    tags: List<ProjectTag> = emptyList(),
    showModifiedTime: Boolean = true,
    showPath: Boolean = true,
    highlightText: String = "",
    onBackupClick: (() -> Unit)? = null,
    onSaveAsTemplateClick: (() -> Unit)? = null,
    onSetCoverClick: (() -> Unit)? = null,
    onSetTagsClick: (() -> Unit)? = null,
    onCreateShortcutClick: (() -> Unit)? = null,
    enableSwipeGesture: Boolean = true
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var manifestInfo by remember { mutableStateOf<ManifestInfo?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    // 本地调试模式状态（点击角标时立即更新，避免等待文件重新加载）
    var localDebugMode by remember { mutableStateOf(false) }

    val iconFile = remember(project.path) {
        File(project.path, "icon.png")
    }
    val hasIcon by derivedStateOf {
        iconFile.exists() && iconFile.isFile
    }

    // 根据密度计算尺寸
    val (verticalPadding, horizontalPadding) = when (density) {
        HomeDensity.COMPACT -> if (isFlatMode) 8.dp to 12.dp else 10.dp to 12.dp
        HomeDensity.COMFORTABLE -> if (isFlatMode) 10.dp to 12.dp else 16.dp to 16.dp
        HomeDensity.LARGE -> if (isFlatMode) 12.dp to 16.dp else 20.dp to 20.dp
    }
    val titleStyle = when (density) {
        HomeDensity.COMPACT -> MaterialTheme.typography.bodyLarge
        HomeDensity.COMFORTABLE -> if (isFlatMode) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium
        HomeDensity.LARGE -> if (isFlatMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
    }
    val subtitleStyle = when (density) {
        HomeDensity.COMPACT -> MaterialTheme.typography.bodySmall
        HomeDensity.COMFORTABLE -> if (isFlatMode) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
        HomeDensity.LARGE -> MaterialTheme.typography.bodyMedium
    }
    val titleFontWeight = if (density == HomeDensity.LARGE && !isFlatMode) FontWeight.SemiBold else FontWeight.Medium

    // ========== 封面背景计算 ==========
    val isSolidCover = cover?.type == CoverType.SOLID_COLOR
    val isImageCover = cover?.type == CoverType.IMAGE && cover.imagePath.isNotEmpty()
    val coverAlpha = (cover?.alpha ?: 1.0f).coerceIn(0.3f, 1.0f)
    // 纯色封面颜色（应用透明度）
    val solidCoverColor = if (isSolidCover) Color(cover!!.colorValue).copy(alpha = coverAlpha) else Color.Unspecified
    // 判断封面是否为深色，用于文字颜色自适应
    val isCoverDark = when {
        isSolidCover -> Color(cover!!.colorValue).isDark()
        isImageCover -> true // 图片封面叠加深色遮罩，视为深色
        else -> false
    }
    // 内容文字颜色
    val onCoverColor = if (isCoverDark) Color.White else Color.Black
    val onCoverColorVariant = onCoverColor.copy(alpha = 0.7f)
    // 分割线颜色
    val dividerColor = onCoverColor.copy(alpha = 0.15f)

    // 封面形状：大卡片用圆形，扁平模式用圆角矩形（仅用于小图标区域）
    val coverShape = if (isFlatMode) RoundedCornerShape(8.dp) else CircleShape
    val cardShape = RoundedCornerShape(cornerRadius)

    // 小图标尺寸（左侧保留的小文件夹/项目图标）
    val smallIconSize = when (density) {
        HomeDensity.COMPACT -> if (isFlatMode) 28.dp else 32.dp
        HomeDensity.COMFORTABLE -> if (isFlatMode) 32.dp else 40.dp
        HomeDensity.LARGE -> if (isFlatMode) 36.dp else 48.dp
    }
    val smallIconInnerSize = when (density) {
        HomeDensity.COMPACT -> 18.dp
        HomeDensity.COMFORTABLE -> 22.dp
        HomeDensity.LARGE -> 26.dp
    }

    // 卡片背景色（多选模式优先）
    val cardContainerColor = when {
        isMultiSelectMode && isSelectedInMultiSelect -> colorScheme.primaryContainer
        isImageCover -> Color.Black // 图片封面用黑色背景，图片加载前不会闪烁白边
        isSolidCover -> solidCoverColor
        else -> colorScheme.surfaceContainerLow
    }
    // 卡片内容文字颜色（有封面时用自适应颜色，否则用主题默认）
    val contentColor = when {
        isMultiSelectMode && isSelectedInMultiSelect -> colorScheme.onPrimaryContainer
        isSolidCover || isImageCover -> onCoverColor
        else -> Color.Unspecified // 用默认
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
                        // 同步本地调试模式状态
                        localDebugMode = debugMode ?: false
                    } catch (e: Exception) {
                        LogCatcher.e("ProjectCard", "加载项目设置失败", e)
                    }
                }
            }
        }
    }

    // 滑动手势启用条件（多选模式下禁用）
    val swipeEnabled = enableSwipeGesture && !isMultiSelectMode
    // SwipeToDismissBox开关：设为false可回退到pointerInput实现
    val useSwipeToDismiss = swipeEnabled

    // 切换Debug模式回调
    // 使用org.json.JSONObject直接操作，避免Map转换链路中嵌套对象丢失的问题
    val onDebugToggleAction: () -> Unit = {
        val newMode = !localDebugMode
        localDebugMode = newMode
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val settingsFile = File(project.path, "settings.json")
                    if (settingsFile.exists()) {
                        val json = settingsFile.readText()
                        val jsonObj = JSONObject(json)
                        // 获取或创建application节点
                        var appObj = jsonObj.optJSONObject("application")
                        if (appObj == null) {
                            appObj = JSONObject()
                            jsonObj.put("application", appObj)
                        }
                        // 设置debugmode字段
                        appObj.put("debugmode", newMode)
                        // 写回文件，使用4空格缩进格式化
                        settingsFile.writeText(jsonObj.toString(4))
                    } else {
                        // settings.json不存在时创建默认结构，包含application.debugmode
                        val jsonObj = JSONObject()
                        val appObj = JSONObject()
                        appObj.put("label", project.name)
                        appObj.put("debugmode", newMode)
                        jsonObj.put("application", appObj)
                        jsonObj.put("package", "com.example.${project.name.lowercase(Locale.ROOT).replace("\\W".toRegex(), "")}")
                        settingsFile.writeText(jsonObj.toString(4))
                    }
                } catch (e: Exception) {
                    LogCatcher.e("ProjectCard", "切换Debug模式失败", e)
                }
            }
        }
    }

    // 卡片内部内容（不含外层Card）
    val cardInnerContent: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = !isPendingDeletion,
            enter = fadeIn(animationSpec = tween(300)) + expandVertically(
                animationSpec = tween(300)
            ),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(
                animationSpec = tween(300)
            )
        ) {
            // 图片封面背景：Box叠加图片+遮罩+内容，图片/遮罩用matchParentSize()适应内容撑开的卡片大小
            if (isImageCover) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // 背景图片：用matchParentSize()适应父Box（父Box高度由内容层决定），不会撑开卡片
                    val coverFile = File(cover!!.imagePath)
                    val imgOffsetX = cover.offsetX
                    val imgOffsetY = cover.offsetY
                    SubcomposeAsyncImage(
                        model = coverFile,
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = coverAlpha
                                translationX = imgOffsetX
                                translationY = imgOffsetY
                            },
                        contentScale = ContentScale.Crop,
                        error = { /* 图片加载失败时显示纯色背景 */ }
                    )
                    // 半透明遮罩（matchParentSize 适应内容高度）
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.55f)
                                    )
                                )
                            )
                    )
                    // 实际内容（决定卡片高度）
                    Box(modifier = Modifier.fillMaxWidth()) {
                        CardContentLayer(
                            project = project, manifestInfo = manifestInfo, isPinned = isPinned,
                            isMultiSelectMode = isMultiSelectMode, isSelectedInMultiSelect = isSelectedInMultiSelect,
                            badge = badge, tags = tags, hasIcon = hasIcon, iconFile = iconFile,
                            cover = cover, showMenu = showMenu, onMenuToggle = { showMenu = it },
                            onTogglePinned = onTogglePinned, onDeleteClick = onDeleteClick, onShareClick = onShareClick,
                            onSetCategory = onSetCategory, onBackupClick = onBackupClick,
                            onSaveAsTemplateClick = onSaveAsTemplateClick, onSetCoverClick = onSetCoverClick,
                            onSetTagsClick = onSetTagsClick, onCreateShortcutClick = onCreateShortcutClick,
                            extraMenuItems = extraMenuItems, colorScheme = colorScheme,
                            dateFormat = dateFormat, scope = scope,
                            verticalPadding = verticalPadding, horizontalPadding = horizontalPadding,
                            smallIconSize = smallIconSize, smallIconInnerSize = smallIconInnerSize,
                            titleStyle = titleStyle, subtitleStyle = subtitleStyle,
                            titleFontWeight = titleFontWeight, highlightText = highlightText,
                            showPath = showPath, showModifiedTime = showModifiedTime,
                            coverShape = coverShape, isFlatMode = isFlatMode,
                            isDebugMode = localDebugMode, onDebugToggle = onDebugToggleAction,
                            isCoverMode = true, onCoverColor = onCoverColor, onCoverColorVariant = onCoverColorVariant,
                            dividerColor = dividerColor
                        )
                    }
                }
            } else {
                // 纯色封面或无封面：直接渲染内容
                CardContentLayer(
                    project = project, manifestInfo = manifestInfo, isPinned = isPinned,
                    isMultiSelectMode = isMultiSelectMode, isSelectedInMultiSelect = isSelectedInMultiSelect,
                    badge = badge, tags = tags, hasIcon = hasIcon, iconFile = iconFile,
                    cover = cover, showMenu = showMenu, onMenuToggle = { showMenu = it },
                    onTogglePinned = onTogglePinned, onDeleteClick = onDeleteClick, onShareClick = onShareClick,
                    onSetCategory = onSetCategory, onBackupClick = onBackupClick,
                    onSaveAsTemplateClick = onSaveAsTemplateClick, onSetCoverClick = onSetCoverClick,
                    onSetTagsClick = onSetTagsClick, onCreateShortcutClick = onCreateShortcutClick,
                    extraMenuItems = extraMenuItems, colorScheme = colorScheme,
                    dateFormat = dateFormat, scope = scope,
                    verticalPadding = verticalPadding, horizontalPadding = horizontalPadding,
                    smallIconSize = smallIconSize, smallIconInnerSize = smallIconInnerSize,
                    titleStyle = titleStyle, subtitleStyle = subtitleStyle,
                    titleFontWeight = titleFontWeight, highlightText = highlightText,
                    showPath = showPath, showModifiedTime = showModifiedTime,
                    coverShape = coverShape, isFlatMode = isFlatMode,
                    isDebugMode = localDebugMode, onDebugToggle = onDebugToggleAction,
                    isCoverMode = isSolidCover, onCoverColor = onCoverColor, onCoverColorVariant = onCoverColorVariant,
                    dividerColor = dividerColor
                )
            }
        }
    }

    // 构建Card的modifier
    val cardModifier = modifier
        .combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
        .let { mod ->
            // fallback pointerInput（当不使用SwipeToDismissBox时）
            if (swipeEnabled && !useSwipeToDismiss && (onSwipeLeft != null || onSwipeRight != null)) {
                mod.pointerInput(Unit) {
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
            } else mod
        }

    // 构建Card
    val card: @Composable () -> Unit = {
        Card(
            modifier = cardModifier,
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = cardContainerColor
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 1.dp
            ),
            content = { cardInnerContent() }
        )
    }

    // 使用SwipeToDismissBox包装或直接渲染Card
    if (useSwipeToDismiss) {
        SwipeToDismissBoxWrapper(
            onTogglePinned = onTogglePinned,
            onDeleteClick = onDeleteClick,
            onShareClick = onShareClick,
            onSetTagsClick = onSetTagsClick,
            onSwipeLeft = onSwipeLeft,
            onSwipeRight = onSwipeRight,
            colorScheme = colorScheme,
            cardShape = cardShape,
            content = card
        )
    } else {
        card()
    }
}

/**
 * 下拉菜单渲染
 * 菜单顺序：置顶、分享、删除、设置分类、备份项目、保存为模板、设置封面、管理标签、添加到桌面、插件菜单项
 */
@Composable
private fun ProjectDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isPinned: Boolean,
    onTogglePinned: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSetCategory: (() -> Unit)?,
    onBackupClick: (() -> Unit)?,
    onSaveAsTemplateClick: (() -> Unit)?,
    onSetCoverClick: (() -> Unit)?,
    onSetTagsClick: (() -> Unit)?,
    onCreateShortcutClick: (() -> Unit)?,
    extraMenuItems: List<PluginManager.ProjectCardMenuItem>,
    project: ProjectItem,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        // 置顶/取消置顶
        DropdownMenuItem(
            text = { Text(if (isPinned) stringResource(R.string.unpin) else stringResource(R.string.pin)) },
            onClick = { onDismiss(); onTogglePinned() },
            leadingIcon = { Icon(if (isPinned) Icons.Filled.Clear else Icons.Filled.Star, contentDescription = null) }
        )
        // 分享
        DropdownMenuItem(
            text = { Text(stringResource(R.string.share)) },
            onClick = { onDismiss(); onShareClick() },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) }
        )
        // 删除
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete), color = colorScheme.error) },
            onClick = { onDismiss(); onDeleteClick() },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = colorScheme.error) }
        )
        // 设置分类
        if (onSetCategory != null) {
            DropdownMenuItem(
                text = { Text("设置分类") },
                onClick = { onDismiss(); onSetCategory() },
                leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) }
            )
        }
        // 备份项目
        if (onBackupClick != null) {
            DropdownMenuItem(
                text = { Text("备份项目") },
                onClick = { onDismiss(); onBackupClick() },
                leadingIcon = { Icon(Icons.Filled.Backup, contentDescription = null) }
            )
        }
        // 保存为模板
        if (onSaveAsTemplateClick != null) {
            DropdownMenuItem(
                text = { Text("保存为模板") },
                onClick = { onDismiss(); onSaveAsTemplateClick() },
                leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null) }
            )
        }
        // 设置封面
        if (onSetCoverClick != null) {
            DropdownMenuItem(
                text = { Text("设置封面") },
                onClick = { onDismiss(); onSetCoverClick() },
                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) }
            )
        }
        // 管理标签
        if (onSetTagsClick != null) {
            DropdownMenuItem(
                text = { Text("管理标签") },
                onClick = { onDismiss(); onSetTagsClick() },
                leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) }
            )
        }
        // 添加到桌面
        if (onCreateShortcutClick != null) {
            DropdownMenuItem(
                text = { Text("添加到桌面") },
                onClick = { onDismiss(); onCreateShortcutClick() },
                leadingIcon = { Icon(Icons.Filled.Launch, contentDescription = null) }
            )
        }
        // 插件额外菜单项（始终在最后）
        extraMenuItems.forEach { item ->
            DropdownMenuItem(
                text = { Text(item.label) },
                onClick = { onDismiss(); item.onClick(project.id, project.name, project.path) },
                leadingIcon = { Icon(Icons.Filled.Extension, contentDescription = null) }
            )
        }
    }
}

/**
 * 卡片内容层 - 根据isFlatMode选择扁平/大卡片布局
 * 统一处理封面模式下的文字颜色、小图标、角标颜色等
 */
@Composable
private fun CardContentLayer(
    project: ProjectItem,
    manifestInfo: ManifestInfo?,
    isPinned: Boolean,
    isMultiSelectMode: Boolean,
    isSelectedInMultiSelect: Boolean,
    badge: PluginManager.BadgeInfo?,
    tags: List<ProjectTag>,
    hasIcon: Boolean,
    iconFile: File,
    cover: ProjectCover?,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onTogglePinned: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onSetCategory: (() -> Unit)?,
    onBackupClick: (() -> Unit)?,
    onSaveAsTemplateClick: (() -> Unit)?,
    onSetCoverClick: (() -> Unit)?,
    onSetTagsClick: (() -> Unit)?,
    onCreateShortcutClick: (() -> Unit)?,
    extraMenuItems: List<PluginManager.ProjectCardMenuItem>,
    colorScheme: androidx.compose.material3.ColorScheme,
    dateFormat: SimpleDateFormat,
    scope: CoroutineScope,
    verticalPadding: Dp,
    horizontalPadding: Dp,
    smallIconSize: Dp,
    smallIconInnerSize: Dp,
    titleStyle: androidx.compose.ui.text.TextStyle,
    subtitleStyle: androidx.compose.ui.text.TextStyle,
    titleFontWeight: FontWeight,
    highlightText: String,
    showPath: Boolean,
    showModifiedTime: Boolean,
    coverShape: RoundedCornerShape,
    isFlatMode: Boolean,
    isDebugMode: Boolean,
    onDebugToggle: (() -> Unit)?,
    isCoverMode: Boolean,
    onCoverColor: Color,
    onCoverColorVariant: Color,
    dividerColor: Color
) {
    if (isFlatMode) {
        FlatModeContent(
            project = project, manifestInfo = manifestInfo, isPinned = isPinned,
            badge = badge, tags = tags, hasIcon = hasIcon, iconFile = iconFile,
            showMenu = showMenu, onMenuToggle = onMenuToggle,
            onTogglePinned = onTogglePinned, onDeleteClick = onDeleteClick, onShareClick = onShareClick,
            onSetCategory = onSetCategory, onBackupClick = onBackupClick,
            onSaveAsTemplateClick = onSaveAsTemplateClick, onSetCoverClick = onSetCoverClick,
            onSetTagsClick = onSetTagsClick, onCreateShortcutClick = onCreateShortcutClick,
            extraMenuItems = extraMenuItems, colorScheme = colorScheme,
            dateFormat = dateFormat,
            verticalPadding = verticalPadding, horizontalPadding = horizontalPadding,
            smallIconSize = smallIconSize, smallIconInnerSize = smallIconInnerSize,
            titleStyle = titleStyle, subtitleStyle = subtitleStyle,
            titleFontWeight = titleFontWeight, highlightText = highlightText,
            showPath = showPath, showModifiedTime = showModifiedTime,
            coverShape = coverShape,
            isDebugMode = isDebugMode, onDebugToggle = onDebugToggle,
            isCoverMode = isCoverMode, onCoverColor = onCoverColor, onCoverColorVariant = onCoverColorVariant
        )
    } else {
        LargeCardModeContent(
            project = project, manifestInfo = manifestInfo, isPinned = isPinned,
            isMultiSelectMode = isMultiSelectMode, isSelectedInMultiSelect = isSelectedInMultiSelect,
            badge = badge, tags = tags, hasIcon = hasIcon, iconFile = iconFile,
            showMenu = showMenu, onMenuToggle = onMenuToggle,
            onTogglePinned = onTogglePinned, onDeleteClick = onDeleteClick, onShareClick = onShareClick,
            onSetCategory = onSetCategory, onBackupClick = onBackupClick,
            onSaveAsTemplateClick = onSaveAsTemplateClick, onSetCoverClick = onSetCoverClick,
            onSetTagsClick = onSetTagsClick, onCreateShortcutClick = onCreateShortcutClick,
            extraMenuItems = extraMenuItems, colorScheme = colorScheme,
            dateFormat = dateFormat,
            verticalPadding = verticalPadding, horizontalPadding = horizontalPadding,
            smallIconSize = smallIconSize, smallIconInnerSize = smallIconInnerSize,
            titleStyle = titleStyle, subtitleStyle = subtitleStyle,
            titleFontWeight = titleFontWeight, highlightText = highlightText,
            showPath = showPath, showModifiedTime = showModifiedTime,
            coverShape = coverShape, dividerColor = dividerColor,
            isDebugMode = isDebugMode, onDebugToggle = onDebugToggle,
            isCoverMode = isCoverMode, onCoverColor = onCoverColor, onCoverColorVariant = onCoverColorVariant
        )
    }
}

/**
 * 扁平列表模式内容
 * 紧凑单列布局：小图标+名称(1行)+标签+副标题+更多按钮
 */
@Composable
private fun FlatModeContent(
    project: ProjectItem,
    manifestInfo: ManifestInfo?,
    isPinned: Boolean,
    badge: PluginManager.BadgeInfo?,
    tags: List<ProjectTag>,
    hasIcon: Boolean,
    iconFile: File,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onTogglePinned: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onSetCategory: (() -> Unit)?,
    onBackupClick: (() -> Unit)?,
    onSaveAsTemplateClick: (() -> Unit)?,
    onSetCoverClick: (() -> Unit)?,
    onSetTagsClick: (() -> Unit)?,
    onCreateShortcutClick: (() -> Unit)?,
    extraMenuItems: List<PluginManager.ProjectCardMenuItem>,
    colorScheme: androidx.compose.material3.ColorScheme,
    dateFormat: SimpleDateFormat,
    verticalPadding: Dp,
    horizontalPadding: Dp,
    smallIconSize: Dp,
    smallIconInnerSize: Dp,
    titleStyle: androidx.compose.ui.text.TextStyle,
    subtitleStyle: androidx.compose.ui.text.TextStyle,
    titleFontWeight: FontWeight,
    highlightText: String,
    showPath: Boolean,
    showModifiedTime: Boolean,
    coverShape: RoundedCornerShape,
    isDebugMode: Boolean = false,
    onDebugToggle: (() -> Unit)? = null,
    isCoverMode: Boolean = false,
    onCoverColor: Color = Color.Unspecified,
    onCoverColorVariant: Color = Color.Unspecified
) {
    // 文字颜色：封面模式使用自适应色，否则用主题色
    val titleColor = if (isCoverMode) onCoverColor else colorScheme.onSurface
    val subtitleColor = if (isCoverMode) onCoverColorVariant else colorScheme.onSurfaceVariant
    val highlightColor = if (isCoverMode) onCoverColor else colorScheme.primary
    val starTint = if (isCoverMode) onCoverColor else colorScheme.primary
    val iconTint = if (isCoverMode) onCoverColor else colorScheme.onSurface

    // Debug/Release 角标颜色
    val debugBgColor = if (isDebugMode) colorScheme.primary.copy(alpha = 0.15f) else Color(0xFF4CAF50).copy(alpha = 0.15f)
    val debugContentColor = if (isDebugMode) colorScheme.primary else Color(0xFF4CAF50)

    // 插件角标在封面模式下也需适配
    val badgeBgFn: (Long) -> Color = { badgeColorLong ->
        if (isCoverMode) onCoverColor.copy(alpha = 0.2f) else Color(badgeColorLong.toInt()).copy(alpha = 0.15f)
    }
    val badgeContentFn: (Long) -> Color = { badgeColorLong ->
        if (isCoverMode) onCoverColor else Color(badgeColorLong.toInt())
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .padding(end = 44.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallIconArea(
                hasIcon = hasIcon, iconFile = iconFile,
                iconSize = smallIconSize, iconInnerSize = smallIconInnerSize,
                shape = coverShape, isCoverMode = isCoverMode,
                onCoverColor = onCoverColor, colorScheme = colorScheme
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 项目名称（高亮），扁平模式1行
                HighlightedText(
                    text = manifestInfo?.label ?: project.name,
                    highlight = highlightText,
                    style = titleStyle.copy(fontWeight = titleFontWeight, color = titleColor),
                    color = highlightColor
                )
                // 标签行
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    TagsRow(tags = tags, isCoverMode = isCoverMode, onCoverColor = onCoverColor)
                }
                // 包名（高亮）
                manifestInfo?.packageName?.let { packageName ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = packageName,
                        style = subtitleStyle.copy(color = subtitleColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 项目路径（缩短显示）
                if (showPath) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ProjectUtil.shortenPath(project.path),
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 修改时间
                if (showModifiedTime) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(project.modifiedDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 置顶星标
            if (isPinned) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = stringResource(R.string.cd_pinned),
                    tint = starTint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            // 更多菜单
            Box {
                IconButton(
                    onClick = { onMenuToggle(true) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.code_editor_more),
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                ProjectDropdownMenu(
                    expanded = showMenu,
                    onDismiss = { onMenuToggle(false) },
                    isPinned = isPinned,
                    onTogglePinned = onTogglePinned,
                    onShareClick = onShareClick,
                    onDeleteClick = onDeleteClick,
                    onSetCategory = onSetCategory,
                    onBackupClick = onBackupClick,
                    onSaveAsTemplateClick = onSaveAsTemplateClick,
                    onSetCoverClick = onSetCoverClick,
                    onSetTagsClick = onSetTagsClick,
                    onCreateShortcutClick = onCreateShortcutClick,
                    extraMenuItems = extraMenuItems,
                    project = project,
                    colorScheme = colorScheme
                )
            }
        }
        // Debug/Release 角标 + 插件角标（绝对定位在右上角）
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = verticalPadding + 2.dp, end = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top
        ) {
            badge?.let { bdg ->
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = badgeBgFn(bdg.color),
                    contentColor = badgeContentFn(bdg.color)
                ) {
                    Text(
                        text = bdg.text,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            // Debug用primary色，Release用绿色
            Surface(
                modifier = Modifier.clickable(enabled = onDebugToggle != null) { onDebugToggle?.invoke() },
                shape = RoundedCornerShape(3.dp),
                color = debugBgColor,
                contentColor = debugContentColor
            ) {
                Text(
                    text = if (isDebugMode) "Debug" else "Release",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 大卡片模式内容
 * 完整卡片布局：小图标+名称(2行)+标签+包名/版本/路径+分割线+修改时间
 */
@Composable
private fun LargeCardModeContent(
    project: ProjectItem,
    manifestInfo: ManifestInfo?,
    isPinned: Boolean,
    isMultiSelectMode: Boolean,
    isSelectedInMultiSelect: Boolean,
    badge: PluginManager.BadgeInfo?,
    tags: List<ProjectTag>,
    hasIcon: Boolean,
    iconFile: File,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onTogglePinned: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onSetCategory: (() -> Unit)?,
    onBackupClick: (() -> Unit)?,
    onSaveAsTemplateClick: (() -> Unit)?,
    onSetCoverClick: (() -> Unit)?,
    onSetTagsClick: (() -> Unit)?,
    onCreateShortcutClick: (() -> Unit)?,
    extraMenuItems: List<PluginManager.ProjectCardMenuItem>,
    colorScheme: androidx.compose.material3.ColorScheme,
    dateFormat: SimpleDateFormat,
    verticalPadding: Dp,
    horizontalPadding: Dp,
    smallIconSize: Dp,
    smallIconInnerSize: Dp,
    titleStyle: androidx.compose.ui.text.TextStyle,
    subtitleStyle: androidx.compose.ui.text.TextStyle,
    titleFontWeight: FontWeight,
    highlightText: String,
    showPath: Boolean,
    showModifiedTime: Boolean,
    coverShape: RoundedCornerShape,
    dividerColor: Color,
    isDebugMode: Boolean = false,
    onDebugToggle: (() -> Unit)? = null,
    isCoverMode: Boolean = false,
    onCoverColor: Color = Color.Unspecified,
    onCoverColorVariant: Color = Color.Unspecified
) {
    // 文字颜色
    val titleColor = if (isCoverMode) onCoverColor else colorScheme.onSurface
    val subtitleColor = if (isCoverMode) onCoverColorVariant else colorScheme.onSurfaceVariant
    val highlightColor = if (isCoverMode) onCoverColor else colorScheme.primary
    val starTint = if (isCoverMode) onCoverColor else colorScheme.primary
    val iconTint = if (isCoverMode) onCoverColor else colorScheme.onSurface
    val dividerColorActual = if (isCoverMode) dividerColor else colorScheme.outline.copy(alpha = 0.1f)

    // Debug/Release 角标颜色：Debug primary，Release 绿色
    val debugBgColor = if (isDebugMode) colorScheme.primary.copy(alpha = 0.15f) else Color(0xFF4CAF50).copy(alpha = 0.15f)
    val debugContentColor = if (isDebugMode) colorScheme.primary else Color(0xFF4CAF50)

    val badgeBgFn: (Long) -> Color = { badgeColorLong ->
        if (isCoverMode) onCoverColor.copy(alpha = 0.2f) else Color(badgeColorLong.toInt()).copy(alpha = 0.15f)
    }
    val badgeContentFn: (Long) -> Color = { badgeColorLong ->
        if (isCoverMode) onCoverColor else Color(badgeColorLong.toInt())
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(verticalPadding)
                .padding(horizontal = horizontalPadding)
                .padding(top = 4.dp, end = 56.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // 多选模式复选框
                    if (isMultiSelectMode) {
                        Checkbox(
                            checked = isSelectedInMultiSelect,
                            onCheckedChange = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // 小图标区域
                    SmallIconArea(
                        hasIcon = hasIcon, iconFile = iconFile,
                        iconSize = smallIconSize, iconInnerSize = smallIconInnerSize,
                        shape = coverShape, isCoverMode = isCoverMode,
                        onCoverColor = onCoverColor, colorScheme = colorScheme
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // 项目名称（高亮），大卡片模式2行
                        Text(
                            text = manifestInfo?.label ?: project.name,
                            style = titleStyle.copy(
                                fontWeight = titleFontWeight,
                                color = titleColor
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 标签行
                        if (tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TagsRow(tags = tags, isCoverMode = isCoverMode, onCoverColor = onCoverColor)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Column {
                            manifestInfo?.let { info ->
                                // 包名
                                info.packageName?.let { packageName ->
                                    Text(
                                        text = packageName,
                                        style = subtitleStyle.copy(color = subtitleColor),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                // 版本号
                                info.versionName?.let { versionName ->
                                    Text(
                                        text = stringResource(R.string.version_label, versionName),
                                        style = subtitleStyle,
                                        color = subtitleColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // 项目路径（缩短显示）
                            if (showPath) {
                                Text(
                                    text = ProjectUtil.shortenPath(project.path),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subtitleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                // 右侧操作区：置顶星标 + 菜单按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = stringResource(R.string.cd_pinned),
                            tint = starTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { onMenuToggle(true) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.code_editor_more),
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        ProjectDropdownMenu(
                            expanded = showMenu,
                            onDismiss = { onMenuToggle(false) },
                            isPinned = isPinned,
                            onTogglePinned = onTogglePinned,
                            onShareClick = onShareClick,
                            onDeleteClick = onDeleteClick,
                            onSetCategory = onSetCategory,
                            onBackupClick = onBackupClick,
                            onSaveAsTemplateClick = onSaveAsTemplateClick,
                            onSetCoverClick = onSetCoverClick,
                            onSetTagsClick = onSetTagsClick,
                            onCreateShortcutClick = onCreateShortcutClick,
                            extraMenuItems = extraMenuItems,
                            project = project,
                            colorScheme = colorScheme
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = dividerColorActual
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showModifiedTime) {
                    Text(
                        text = stringResource(R.string.modified_time, dateFormat.format(project.modifiedDate)),
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Debug/Release 角标（绝对定位在卡片右上角，可点击切换）
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = verticalPadding + 4.dp, end = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top
        ) {
            badge?.let { bdg ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeBgFn(bdg.color),
                    contentColor = badgeContentFn(bdg.color)
                ) {
                    Text(
                        text = bdg.text,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            // Debug用primary色，Release用绿色
            Surface(
                modifier = Modifier.clickable(enabled = onDebugToggle != null) { onDebugToggle?.invoke() },
                shape = RoundedCornerShape(4.dp),
                color = debugBgColor,
                contentColor = debugContentColor
            ) {
                Text(
                    text = if (isDebugMode) "Debug" else "Release",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
