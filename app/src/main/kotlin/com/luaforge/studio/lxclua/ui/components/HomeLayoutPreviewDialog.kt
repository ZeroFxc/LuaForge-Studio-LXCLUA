package com.luaforge.studio.lxclua.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luaforge.studio.lxclua.ProjectItem
import com.luaforge.studio.lxclua.ui.settings.HomeDensity
import com.luaforge.studio.lxclua.ui.settings.HomeLayoutMode
import com.luaforge.studio.lxclua.ui.settings.SettingsData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页布局预览对话框
 * 使用模拟数据展示当前设置下的首页布局效果，不离开设置页即可预览
 *
 * @param settings 当前设置数据
 * @param onDismiss 关闭对话框回调
 * @param onGoToHome 点击"返回首页查看"回调
 */
@Composable
fun HomeLayoutPreviewDialog(
    settings: SettingsData,
    onDismiss: () -> Unit,
    onGoToHome: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "首页布局预览",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = onGoToHome) {
                                Text("返回首页")
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "关闭")
                            }
                        }
                    }
                }

                // 当前配置提示
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = buildString {
                            append("模式: ")
                            append(if (settings.homeLayoutMode == HomeLayoutMode.CARD) "大卡片" else "扁平列表")
                            append(" | 密度: ")
                            append(when(settings.homeDensity) {
                                HomeDensity.COMPACT -> "紧凑"
                                HomeDensity.COMFORTABLE -> "舒适"
                                HomeDensity.LARGE -> "宽松"
                            })
                            append(" | 圆角: ")
                            append(when(settings.cardCornerRadius) {
                                0 -> "小"
                                1 -> "中"
                                else -> "大"
                            })
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // 预览内容区（使用缩放效果模拟缩小的首页）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(8.dp)
                ) {
                    HomePreviewContent(settings = settings)
                }
            }
        }
    }
}

/**
 * 首页预览内容 - 模拟渲染项目列表
 */
@Composable
private fun HomePreviewContent(settings: SettingsData) {
    // 生成模拟项目数据
    val previewProjects = remember {
        listOf(
            ProjectItem(
                id = "preview1",
                name = "我的第一个项目",
                path = "/sdcard/LXC-LUA/project/demo1",
                createdDate = Date(System.currentTimeMillis() - 86400000 * 5),
                modifiedDate = Date(System.currentTimeMillis() - 3600000 * 2)
            ),
            ProjectItem(
                id = "preview2",
                name = "游戏Demo",
                path = "/sdcard/LXC-LUA/project/game_demo",
                createdDate = Date(System.currentTimeMillis() - 86400000 * 10),
                modifiedDate = Date(System.currentTimeMillis() - 86400000)
            ),
            ProjectItem(
                id = "preview3",
                name = "工具集",
                path = "/sdcard/LXC-LUA/project/utils",
                createdDate = Date(System.currentTimeMillis() - 86400000 * 20),
                modifiedDate = Date(System.currentTimeMillis() - 86400000 * 3)
            ),
            ProjectItem(
                id = "preview4",
                name = "测试项目",
                path = "/sdcard/LXC-LUA/project/test_app",
                createdDate = Date(System.currentTimeMillis() - 86400000 * 2),
                modifiedDate = Date(System.currentTimeMillis() - 600000)
            )
        )
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val isFlatMode = settings.homeLayoutMode == HomeLayoutMode.FLAT

    // 根据密度计算尺寸
    val density = settings.homeDensity
    val (cardVerticalPadding, cardHorizontalPadding) = when (density) {
        HomeDensity.COMPACT -> if (isFlatMode) 6.dp to 10.dp else 8.dp to 10.dp
        HomeDensity.COMFORTABLE -> if (isFlatMode) 8.dp to 12.dp else 12.dp to 14.dp
        HomeDensity.LARGE -> if (isFlatMode) 10.dp to 14.dp else 16.dp to 18.dp
    }
    val cornerRadius = when(settings.cardCornerRadius) {
        0 -> 6.dp
        1 -> 10.dp
        else -> 16.dp
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (isFlatMode) 4.dp else 8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        // 最近项目条（如果启用）
        if (settings.showRecentProjectsBar) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(cornerRadius),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "最近项目 (预览)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "横向滚动",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        // 分类栏（如果启用）
        if (settings.homeCategoryEnabled && settings.homeCategories.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "全部",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    settings.homeCategories.take(3).forEach { category ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        // 项目卡片列表
        items(previewProjects) { project ->
            PreviewProjectCard(
                project = project,
                isFlatMode = isFlatMode,
                cornerRadius = cornerRadius,
                verticalPadding = cardVerticalPadding,
                horizontalPadding = cardHorizontalPadding,
                dateFormat = dateFormat,
                showModifiedTime = settings.showProjectModifiedTime,
                showPath = settings.showProjectPath,
                isPinned = project.id == "preview1"
            )
        }
    }
}

/**
 * 预览用的简化项目卡片
 */
@Composable
private fun PreviewProjectCard(
    project: ProjectItem,
    isFlatMode: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp,
    verticalPadding: androidx.compose.ui.unit.Dp,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    dateFormat: SimpleDateFormat,
    showModifiedTime: Boolean,
    showPath: Boolean,
    isPinned: Boolean
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = cardColor,
        tonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isFlatMode) {
                // 扁平模式
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 小文件夹图标
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(if (verticalPadding > 8.dp) 32.dp else 26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (verticalPadding > 8.dp) 18.dp else 14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = project.name,
                            style = if (verticalPadding > 8.dp) MaterialTheme.typography.bodyMedium
                            else MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (showPath) {
                            Text(
                                text = "/sdcard/LXC-LUA/project/${project.name.lowercase().replace(" ", "_")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Release角标
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Release",
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // 大卡片模式
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(verticalPadding)
                        .padding(horizontal = horizontalPadding)
                        .padding(top = 2.dp, end = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // 文件夹图标
                        Surface(
                            shape = RoundedCornerShape(if (cornerRadius > 10.dp) 12.dp else 8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(if (verticalPadding > 10.dp) 40.dp else 32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(if (verticalPadding > 10.dp) 22.dp else 18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.name,
                                style = if (verticalPadding > 10.dp) MaterialTheme.typography.titleSmall
                                else MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            if (showPath) {
                                Text(
                                    text = "/sdcard/LXC-LUA/project/${project.name.lowercase().replace(" ", "_")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (showModifiedTime || !isPinned) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (showModifiedTime) {
                                Text(
                                    text = "修改于 ${dateFormat.format(project.modifiedDate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                // 右上角Release角标
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = verticalPadding + 2.dp, end = horizontalPadding),
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Release",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
                // 置顶星标
                if (isPinned) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = verticalPadding + 2.dp, end = horizontalPadding + 48.dp)
                            .size(14.dp)
                    )
                }
            }
        }
    }
}
