package com.luaforge.studio.lxclua.ui.plugin

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luaforge.studio.lxclua.plugin.PluginManager
import com.luaforge.studio.lxclua.R
import com.luaforge.studio.lxclua.plugin.LoadedPlugin
import com.luaforge.studio.lxclua.plugin.data.PluginManifest
import com.luaforge.studio.lxclua.plugin.data.PluginDependency
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import com.luaforge.studio.lxclua.plugin.state.PluginDetailState
import com.luaforge.studio.lxclua.plugin.state.PluginDetailItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun PluginScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val plugins = PluginManager.loadedPlugins

    var showDeleteConfirmDialog by remember { mutableStateOf<LoadedPlugin?>(null) }
    var isInstalling by remember { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf("") }
    // 日志查看模式：非 null 时整个屏幕由 PluginLogScreen 接管
    var logPlugin by remember { mutableStateOf<LoadedPlugin?>(null) }

    // 编辑对话框
    var editPlugin by remember { mutableStateOf<LoadedPlugin?>(null) }
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editAuthor by remember { mutableStateOf("") }
    var editVersion by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("") }

    // 导出 ZIP 的目标文件
    var exportZipPluginId by remember { mutableStateOf<String?>(null) }

    // 导出目录的目标目录
    var exportDirPluginId by remember { mutableStateOf<String?>(null) }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isInstalling = true
            installMessage = context.getString(R.string.plugin_installing_zip)
            scope.launch(Dispatchers.IO) {
                val tempZip = copyUriToCache(context, uri)
                if (tempZip != null && tempZip.exists()) {
                    val result = PluginManager.installPluginFromZip(context, tempZip)
                    withContext(Dispatchers.Main) {
                        isInstalling = false
                        tempZip.delete()
                        if (result.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.plugin_install_success, result.getOrNull()?.name ?: ""), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.plugin_install_failed, result.exceptionOrNull()?.message ?: ""), Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isInstalling = false
                        Toast.makeText(context, context.getString(R.string.plugin_read_zip_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            isInstalling = true
            installMessage = context.getString(R.string.plugin_installing_dir)
            scope.launch(Dispatchers.IO) {
                val result = copyUriDirToCache(context, uri)
                if (result != null) {
                    val installResult = PluginManager.installPluginFromDirectory(context, result)
                    withContext(Dispatchers.Main) {
                        isInstalling = false
                        result.deleteRecursively()
                        if (installResult.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.plugin_import_dir_success, installResult.getOrNull()?.name ?: ""), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.plugin_import_dir_failed, installResult.exceptionOrNull()?.message ?: ""), Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isInstalling = false
                        Toast.makeText(context, context.getString(R.string.plugin_read_dir_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null && exportZipPluginId != null) {
            val pluginId = exportZipPluginId!!
            exportZipPluginId = null
            scope.launch(Dispatchers.IO) {
                val tempFile = File(context.cacheDir, "plugin_export_${UUID.randomUUID()}.zip")
                val success = PluginManager.exportPluginToZip(context, pluginId, tempFile)
                withContext(Dispatchers.Main) {
                    if (success) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            tempFile.inputStream().use { input -> input.copyTo(output) }
                        }
                        Toast.makeText(context, context.getString(R.string.plugin_export_zip_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.plugin_export_zip_failed), Toast.LENGTH_SHORT).show()
                    }
                    tempFile.delete()
                }
            }
        }
    }

    val exportDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null && exportDirPluginId != null) {
            val pluginId = exportDirPluginId!!
            exportDirPluginId = null
            scope.launch(Dispatchers.IO) {
                val pluginDir = PluginManager.getPluginDirectory(context, pluginId)
                val success = if (pluginDir != null) {
                    copyLocalDirToDocumentTree(context, pluginDir, uri)
                } else false
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.plugin_export_dir_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.plugin_export_dir_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 进入日志查看模式：完全覆盖主列表
    val pluginForLog = logPlugin
    if (pluginForLog != null) {
        PluginLogScreen(
            plugin = pluginForLog,
            onBack = { logPlugin = null }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (plugins.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.plugin_no_plugins),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    )
                    TextButton(onClick = { zipPickerLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                        Text(stringResource(R.string.plugin_import_zip_hint))
                    }
                    TextButton(onClick = { dirPickerLauncher.launch(null) }) {
                        Text(stringResource(R.string.plugin_import_dir_hint))
                    }
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            PluginManager.scanPlugins(context)
                        }
                    }) {
                        Text(stringResource(R.string.plugin_refresh_from_dir))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { zipPickerLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.plugin_import_zip_button), maxLines = 1, fontSize = 13.sp, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = { dirPickerLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.plugin_import_dir_button), maxLines = 1, fontSize = 13.sp, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    PluginManager.scanPlugins(context)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.plugin_refresh_cd), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                items(plugins, key = { it.manifest.id }) { plugin ->
                    PluginItemCard(
                        plugin = plugin,
                        onToggle = { enabled ->
                            PluginManager.togglePlugin(context, plugin.manifest.id, enabled)
                        },
                        onDelete = {
                            showDeleteConfirmDialog = plugin
                        },
                        onEdit = {
                            editPlugin = plugin
                            editName = plugin.manifest.name
                            editDescription = plugin.manifest.description
                            editAuthor = plugin.manifest.author
                            editVersion = plugin.manifest.version
                            editCategory = plugin.manifest.category
                        },
                        onExportZip = {
                            exportZipPluginId = plugin.manifest.id
                            exportZipLauncher.launch("${plugin.manifest.name}.zip")
                        },
                        onExportDir = {
                            exportDirPluginId = plugin.manifest.id
                            exportDirLauncher.launch(null)
                        },
                        onLog = {
                            logPlugin = plugin
                        },
                        onCardClick = {
                            // 触发插件卡片点击事件，供插件注册的监听器响应
                            EventManager.fireEvent(PluginEvents.ON_PLUGIN_CARD_CLICK, plugin.manifest.id)
                            // 如果插件包含 WebUI，点击卡片后自动打开
                            val hasWeb = java.io.File(plugin.directory, "web/index.html").exists()
                            if (hasWeb) {
                                com.luaforge.studio.lxclua.plugin.state.NavigationState.openWebUI(
                                    plugin.manifest.id, "index.html"
                                )
                            }
                        }
                    )
                }
            }
        }

        if (!isInstalling) {
            ExtendedFloatingActionButton(
                onClick = { zipPickerLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.plugin_fab_import)) }
            )
        }

        if (isInstalling) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(installMessage.ifBlank { context.getString(R.string.plugin_installing_wait) })
                    }
                }
            }
        }

        showDeleteConfirmDialog?.let { plugin ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = null },
                title = { Text(stringResource(R.string.plugin_dialog_delete_title)) },
                text = { Text(stringResource(R.string.plugin_dialog_delete_message, plugin.manifest.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        PluginManager.deletePlugin(context, plugin.manifest.id)
                        showDeleteConfirmDialog = null
                        Toast.makeText(context, context.getString(R.string.plugin_deleted), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        editPlugin?.let { plugin ->
            EditPluginDialog(
                plugin = plugin,
                name = editName,
                description = editDescription,
                author = editAuthor,
                version = editVersion,
                category = editCategory,
                onNameChange = { editName = it },
                onDescriptionChange = { editDescription = it },
                onAuthorChange = { editAuthor = it },
                onVersionChange = { editVersion = it },
                onCategoryChange = { editCategory = it },
                onDismiss = { editPlugin = null },
                onSave = {
                    val newManifest = plugin.manifest.copy(
                        name = editName,
                        description = editDescription,
                        author = editAuthor,
                        version = editVersion,
                        category = editCategory
                    )
                    val success = PluginManager.updatePluginManifest(context, plugin.manifest.id, newManifest)
                    editPlugin = null
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.plugin_info_updated), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.plugin_update_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
fun PluginDetailItemRow(item: PluginDetailItem) {
    var switchState by remember { mutableStateOf(item.initialValue) }

    when (item.type) {
        PluginDetailState.TYPE_SWITCH -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.subtitle.isNotEmpty()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Switch(
                    checked = switchState,
                    onCheckedChange = { newValue ->
                        switchState = newValue
                        item.onChange?.invoke(newValue)
                    }
                )
            }
        }
        PluginDetailState.TYPE_BUTTON -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.subtitle.isNotEmpty()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                IconButton(onClick = { item.onClick?.invoke() }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = item.title,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        PluginDetailState.TYPE_INFO -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.subtitle.isNotEmpty()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PluginItemCard(
    plugin: LoadedPlugin,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onExportZip: () -> Unit,
    onExportDir: () -> Unit,
    onLog: () -> Unit,
    onCardClick: () -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // 缓存昂贵的计算结果，避免列表滑动时重复计算
    val description = remember(plugin.manifest.description) { plugin.manifest.description ?: "" }
    val dependencies = remember(plugin.manifest.dependencies) { plugin.manifest.dependencies ?: emptyList() }
    val extensions = remember(plugin.manifest.extendedBy) { plugin.manifest.extendedBy ?: emptyList() }
    val hasWebUI = remember { java.io.File(plugin.directory, "web/index.html").exists() }

    // 缓存依赖/扩展插件的名称查找，避免在重组时重复遍历 loadedPlugins
    // 这些名称在插件生命周期内不变，可以安全缓存
    val depNameMap = remember(plugin.manifest.id, dependencies) {
        dependencies.associate { dep ->
            dep.pluginId to (PluginManager.loadedPlugins.find { it.manifest.id == dep.pluginId }?.manifest?.name ?: dep.pluginId)
        }
    }
    val extNameMap = remember(plugin.manifest.id, extensions) {
        extensions.associate { extId ->
            extId to (PluginManager.loadedPlugins.find { it.manifest.id == extId }?.manifest?.name ?: extId)
        }
    }

    // 缓存自定义详情项，避免每次重组都查询
    val detailItems = remember(plugin.manifest.id) {
        PluginDetailState.getItemsForPlugin(plugin.manifest.id)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = { showContextMenu = true }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 卡片头部：名称、版本/作者 + Switch/日志/删除按钮
            PluginCardHeader(
                plugin = plugin,
                onToggle = onToggle,
                onLog = onLog,
                onDelete = onDelete
            )

            // 展开/折叠指示器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = null
                    )
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) stringResource(R.string.plugin_expand_collapse) else stringResource(R.string.plugin_expand_detail),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) stringResource(R.string.plugin_expand_collapse) else stringResource(R.string.plugin_expand_cd),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 展开内容：延迟组合，仅在展开时渲染
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                PluginCardExpandedContent(
                    plugin = plugin,
                    description = description,
                    dependencies = dependencies,
                    extensions = extensions,
                    depNameMap = depNameMap,
                    extNameMap = extNameMap,
                    detailItems = detailItems,
                    hasWebUI = hasWebUI
                )
            }

            // 长按上下文菜单
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plugin_menu_edit)) },
                    onClick = {
                        showContextMenu = false
                        onEdit()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plugin_menu_export_zip)) },
                    onClick = {
                        showContextMenu = false
                        onExportZip()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Archive, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plugin_menu_export_dir)) },
                    onClick = {
                        showContextMenu = false
                        onExportDir()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                    }
                )
                Divider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plugin_cd_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showContextMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }
    }
}

/**
 * 卡片头部组件：插件名称、版本/作者 + Switch/日志/删除按钮
 * 提取为独立 Composable 减少重组范围
 */
@Composable
private fun PluginCardHeader(
    plugin: LoadedPlugin,
    onToggle: (Boolean) -> Unit,
    onLog: () -> Unit,
    onDelete: () -> Unit
) {
    var showErrorDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plugin.manifest.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // 错误状态图标
                if (plugin.loadError != null) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "插件加载失败",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(18.dp)
                            .clickable { showErrorDialog = true }
                    )
                }
            }
            Text(
                text = stringResource(R.string.plugin_version_author, plugin.manifest.version, plugin.manifest.author),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Switch(
                checked = plugin.enabled.value,
                onCheckedChange = onToggle
            )
            IconButton(onClick = onLog) {
                Icon(
                    imageVector = Icons.Default.Article,
                    contentDescription = stringResource(R.string.plugin_cd_view_log),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.plugin_cd_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // 错误详情弹窗
    if (showErrorDialog && plugin.loadError != null) {
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.plugin_load_error_title, plugin.manifest.name),
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column {
                    Text(
                        text = plugin.loadError ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(plugin.loadError ?: ""))
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.plugin_copy_error))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

/**
 * 卡片展开内容组件：类型标签、描述、依赖、扩展、自定义详情、WebUI 按钮
 * 仅在卡片展开时组合，减少折叠状态下的重组开销
 */
@Composable
private fun PluginCardExpandedContent(
    plugin: LoadedPlugin,
    description: String,
    dependencies: List<PluginDependency>,
    extensions: List<String>,
    depNameMap: Map<String, String>,
    extNameMap: Map<String, String>,
    detailItems: List<PluginDetailItem>,
    hasWebUI: Boolean
) {
    Column {
        // 类型标签行
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 脚本类型标签（LUA/DEX/APK）
            val type = plugin.manifest.type ?: "lua"
            val typeText = type.uppercase()
            val badgeColor = when (type.lowercase()) {
                "lua" -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
            val badgeTextColor = when (type.lowercase()) {
                "lua" -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = typeText,
                    fontSize = 10.sp,
                    color = badgeTextColor,
                    fontWeight = FontWeight.Bold
                )
            }

            // 插件分类标签（核心/普通/依赖/扩展）
            val pluginType = plugin.manifest.pluginType ?: "normal"
            val pluginTypeText = when (pluginType.lowercase()) {
                "core" -> stringResource(R.string.plugin_category_core)
                "normal" -> stringResource(R.string.plugin_category_normal)
                "dependent" -> stringResource(R.string.plugin_category_dependent)
                "extension" -> stringResource(R.string.plugin_category_extension)
                else -> pluginType
            }
            val pluginTypeBadgeColor = when (pluginType.lowercase()) {
                "core" -> MaterialTheme.colorScheme.errorContainer
                "normal" -> MaterialTheme.colorScheme.tertiaryContainer
                "dependent" -> MaterialTheme.colorScheme.secondaryContainer
                "extension" -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val pluginTypeTextColor = when (pluginType.lowercase()) {
                "core" -> MaterialTheme.colorScheme.onErrorContainer
                "normal" -> MaterialTheme.colorScheme.onTertiaryContainer
                "dependent" -> MaterialTheme.colorScheme.onSecondaryContainer
                "extension" -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(pluginTypeBadgeColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = pluginTypeText,
                    fontSize = 10.sp,
                    color = pluginTypeTextColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = stringResource(R.string.plugin_id_label, plugin.manifest.id),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 描述
        if (description.isNotEmpty()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        val hasDependencies = dependencies.isNotEmpty()
        val hasExtensions = extensions.isNotEmpty()

        // 依赖和扩展信息
        if (hasDependencies || hasExtensions) {
            Spacer(modifier = Modifier.height(4.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))

            if (hasDependencies) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.plugin_dependencies_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    dependencies.forEachIndexed { index, dep ->
                        val depName = depNameMap[dep.pluginId] ?: dep.pluginId
                        val isSatisfied = PluginManager.loadedPlugins.any {
                            it.manifest.id == dep.pluginId && it.enabled.value
                        }
                        Text(
                            text = depName + if (index < dependencies.size - 1) ", " else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSatisfied)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (hasExtensions) {
                if (hasDependencies) Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = stringResource(R.string.plugin_extended_by_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    extensions.forEachIndexed { index, extId ->
                        val extName = extNameMap[extId] ?: extId
                        val isLoaded = PluginManager.loadedPlugins.any { it.manifest.id == extId }
                        Text(
                            text = extName + if (index < extensions.size - 1) ", " else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLoaded)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 插件自定义详情扩展（开关/齿轮按钮）
        if (detailItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.plugin_detail_extensions),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            detailItems.forEach { item ->
                key(item.key) {
                    PluginDetailItemRow(item = item)
                }
            }
        }

        // WebUI 按钮（如果插件提供了 web 目录）
        if (hasWebUI) {
            Spacer(modifier = Modifier.height(4.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    com.luaforge.studio.lxclua.plugin.state.NavigationState.openWebUI(
                        plugin.manifest.id, "index.html"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.WebAsset,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.plugin_card_webui_button))
            }
        }
    }
}

@Composable
fun EditPluginDialog(
    plugin: LoadedPlugin,
    name: String,
    description: String,
    author: String,
    version: String,
    category: String,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onVersionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ID: ${plugin.manifest.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.plugin_edit_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(R.string.plugin_edit_description)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = onAuthorChange,
                    label = { Text(stringResource(R.string.plugin_edit_author)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = onVersionChange,
                    label = { Text(stringResource(R.string.plugin_edit_version)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = onCategoryChange,
                    label = { Text(stringResource(R.string.plugin_edit_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.plugin_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun copyUriToCache(context: Context, uri: Uri): File? {
    return try {
        val cacheFile = File(context.cacheDir, "plugin_import_${UUID.randomUUID()}.zip")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
            true
        } ?: false

        if (!copied || cacheFile.length() == 0L) {
            cacheFile.delete()
            android.util.Log.e("PluginScreen", "copyUriToCache: 无法复制文件内容, uri=$uri")
            return null
        }
        cacheFile
    } catch (e: Exception) {
        android.util.Log.e("PluginScreen", "copyUriToCache: 异常", e)
        null
    }
}

private fun copyUriDirToCache(context: Context, uri: Uri): File? {
    return try {
        val cacheDir = File(context.cacheDir, "plugin_dir_import_${UUID.randomUUID()}")
        cacheDir.mkdirs()
        copyDocumentTree(context, uri, cacheDir)
        if (File(cacheDir, "manifest.json").exists()) {
            cacheDir
        } else {
            android.util.Log.e("PluginScreen", "copyUriDirToCache: 复制后未找到 manifest.json")
            cacheDir.deleteRecursively()
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("PluginScreen", "copyUriDirToCache: 异常", e)
        null
    }
}

private fun copyDocumentTree(context: Context, treeUri: Uri, destDir: File) {
    val documentId = DocumentsContract.getTreeDocumentId(treeUri)
    copyDocumentTreeInternal(context, treeUri, documentId, destDir)
}

private fun copyDocumentTreeInternal(context: Context, treeUri: Uri, parentDocId: String, destDir: File) {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

    val cursor = context.contentResolver.query(
        childrenUri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ),
        null, null, null
    )

    if (cursor == null) {
        android.util.Log.e("PluginScreen", "copyDocumentTree: cursor is null, treeUri=$treeUri, parentDocId=$parentDocId")
        throw Exception("无法读取目录内容 (content provider 返回空)")
    }

    cursor.use {
        while (it.moveToNext()) {
            val childDocId = it.getString(0) ?: continue
            val childName = it.getString(1) ?: "unknown"
            val childMimeType = it.getString(2) ?: ""
            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)

            if (childMimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                val subDir = File(destDir, childName)
                subDir.mkdirs()
                copyDocumentTreeInternal(context, treeUri, childDocId, subDir)
            } else {
                val destFile = File(destDir, childName)
                val copied = context.contentResolver.openInputStream(childUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    true
                } ?: false
                if (!copied) {
                    android.util.Log.w("PluginScreen", "copyDocumentTree: 无法打开文件 $childName, docId=$childDocId")
                }
            }
        }
    }
}

private fun copyLocalDirToDocumentTree(context: Context, localDir: File, treeUri: Uri): Boolean {
    return try {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        copyLocalDirToDocumentTreeInternal(context, localDir, treeUri, treeDocId)
        true
    } catch (e: Exception) {
        android.util.Log.e("PluginScreen", "copyLocalDirToDocumentTree: 异常", e)
        false
    }
}

private fun copyLocalDirToDocumentTreeInternal(context: Context, localDir: File, treeUri: Uri, parentDocId: String) {
    val children = localDir.listFiles() ?: return

    for (file in children) {
        if (file.isDirectory && file.name == "logs") {
            continue
        }
        if (file.isDirectory) {
            val dirUri = DocumentsContract.createDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId),
                DocumentsContract.Document.MIME_TYPE_DIR,
                file.name
            ) ?: continue
            val dirDocId = DocumentsContract.getDocumentId(dirUri)
            copyLocalDirToDocumentTreeInternal(context, file, treeUri, dirDocId)
        } else {
            val mimeType = when (file.extension.lowercase()) {
                "json" -> "application/json"
                "lua" -> "text/x-lua"
                "txt" -> "text/plain"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "xml" -> "text/xml"
                "zip" -> "application/zip"
                else -> "application/octet-stream"
            }
            try {
                val fileUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId),
                    mimeType,
                    file.name
                )
                if (fileUri != null) {
                    context.contentResolver.openOutputStream(fileUri)?.use { output ->
                        file.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PluginScreen", "copyLocalDirToDocumentTree: 创建文件失败 ${file.name}", e)
            }
        }
    }
}