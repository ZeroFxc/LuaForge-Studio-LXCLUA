package com.luaforge.studio.lxclua.files

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import com.luaforge.studio.lxclua.plugin.PluginManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Css
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luaforge.studio.lxclua.R
import com.luaforge.studio.lxclua.git.GitFileState
import com.luaforge.studio.lxclua.ui.git.GitStateBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 文件树节点的数据结构
data class FileNode(
    val file: File,
    val isDirectory: Boolean,
)

/**
 * 判断是否为图像文件
 */
fun isImageFile(fileName: String): Boolean {
    val imageExtensions = listOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg")
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return extension in imageExtensions
}

/**
 * 获取文件图标资源ID，如果是特定类型返回资源ID，否则返回null
 */
fun getFileIconResource(fileName: String): Int? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "lua" -> R.drawable.ic_language_lua
        "aly" -> R.drawable.ic_code_braces
        "json" -> R.drawable.ic_code_json
        else -> null
    }
}

/**
 * 获取文件对应的Material图标
 */
fun getMaterialFileIcon(fileName: String): ImageVector {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "xml" -> Icons.Filled.Code
        "txt" -> Icons.Default.Description
        "html" -> Icons.Filled.Html
        "css" -> Icons.Filled.Css
        "js" -> Icons.Filled.Javascript
        "md" -> Icons.Filled.Description
        "yml", "yaml" -> Icons.Filled.Settings
        "properties" -> Icons.Filled.Settings
        "gradle" -> Icons.Filled.Build
        "gitignore" -> Icons.Filled.Block
        "aly" -> Icons.Filled.Code
        else -> Icons.Default.Description
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTree(
    rootPath: String,
    refreshTrigger: Int, // 刷新触发器（会重置展开状态）
    modifier: Modifier = Modifier,
    watchTrigger: Int = 0, // 文件监听触发器（仅重新加载子项，不打开展开状态）
    gitStates: Map<String, GitFileState> = emptyMap(),
    onGitStage: ((File) -> Unit)? = null,
    onGitUnstage: ((File) -> Unit)? = null,
    onGitDiscard: ((File) -> Unit)? = null,
    onFileClick: (File) -> Unit,
    onFileRenamed: (oldFile: File, newFile: File) -> Unit = { _, _ -> },
    onFileDeleted: (File) -> Unit = { }
) {
    var rootFiles by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current  // 添加LocalDensity

    var containerWidth by remember { mutableIntStateOf(0) }
    val sideMargin = 12.dp

    val minItemWidth = remember(containerWidth, sideMargin, density) {
        if (containerWidth == 0) 0.dp else {
            val containerWidthDp = with(density) { containerWidth.toDp() }
            (containerWidthDp - (sideMargin * 2)).coerceAtLeast(0.dp)
        }
    }

    var itemWidths by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val maxContentWidth = itemWidths.values.maxOrNull() ?: 0
    val viewportWidthPx = if (containerWidth > 0) {
        with(density) { (containerWidth.toDp() - (sideMargin * 2)).toPx() }
    } else 0f
    val isHorizontalScrollEnabled = maxContentWidth > viewportWidthPx && containerWidth > 0
    val horizontalScrollState = rememberScrollState()

    var expandedNodes by remember(
        rootPath,
        refreshTrigger
    ) { mutableStateOf(setOf(File(rootPath).path)) }

    val onSmartToggle: (FileNode) -> Unit = smartToggle@{ node ->
        val path = node.file.path
        if (expandedNodes.contains(path)) {
            expandedNodes -= path
            return@smartToggle
        }
        scope.launch(Dispatchers.IO) {
            val children = node.file.listFiles()
            val childCount = children?.size ?: 0
            if (childCount != 1 || children?.first()?.isFile == true) {
                withContext(Dispatchers.Main) {
                    expandedNodes += path
                }
            } else {
                val pathsToExpend = mutableListOf<String>()
                var currentFile = node.file
                var currentChildren = children
                while (currentChildren?.size == 1 && currentChildren.first().isDirectory) {
                    pathsToExpend.add(currentFile.path)
                    val singleChild = currentChildren.first()
                    currentFile = singleChild
                    currentChildren = currentFile.listFiles()
                }
                pathsToExpend.add(currentFile.path)
                withContext(Dispatchers.Main) {
                    expandedNodes += pathsToExpend
                }
            }
        }
    }

    fun refreshDirectory(directory: File) {
        scope.launch {
            val path = directory.absolutePath
            if (expandedNodes.contains(path)) {
                expandedNodes -= path
                delay(20)
                expandedNodes += path
            }
        }
    }

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedFileNode by remember { mutableStateOf<FileNode?>(null) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isHorizontalScrollEnabled) {
        if (!isHorizontalScrollEnabled) {
            horizontalScrollState.animateScrollTo(0)
        }
    }

    // 添加 refreshTrigger 到 LaunchedEffect 的 key 中
    LaunchedEffect(rootPath, refreshTrigger) {
        val rootFile = File(rootPath)
        rootFiles = if (rootFile.exists()) {
            listOf(FileNode(file = rootFile, isDirectory = rootFile.isDirectory))
        } else {
            emptyList()
        }
    }

    if (rootFiles.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Box(
            modifier = modifier
                .onSizeChanged { containerWidth = it.width }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        state = horizontalScrollState,
                        enabled = isHorizontalScrollEnabled
                    ),
                contentPadding = PaddingValues(
                    horizontal = sideMargin,
                    vertical = 4.dp
                )
            ) {
                items(rootFiles, key = { it.file.path }) { node ->
                    FileNodeItem(
                        node = node,
                        depth = 0,
                        expandedNodes = expandedNodes,
                        minWidth = minItemWidth,
                        gitStates = gitStates,
                        watchTrigger = watchTrigger,
                        onToggle = onSmartToggle,
                        onFileClick = onFileClick,
                        onLongClick = {
                            selectedFileNode = it
                            showBottomSheet = true
                        },
                        onWidthMeasured = { path, width ->
                            if (itemWidths[path] != width) itemWidths = itemWidths + (path to width)
                        },
                        onDisposed = { path ->
                            itemWidths = itemWidths - path
                        }
                    )
                }
            }
        }
    }
    if (showBottomSheet && selectedFileNode != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            FileActionBottomSheet(
                node = selectedFileNode!!,
                gitState = gitStates[selectedFileNode!!.file.path],
                onGitStage = onGitStage,
                onGitUnstage = onGitUnstage,
                onGitDiscard = onGitDiscard,
                onDismiss = {
                    scope.launch { sheetState.hide() }
                        .invokeOnCompletion { if (!sheetState.isVisible) showBottomSheet = false }
                },
                onDeleteRequest = { showDeleteConfirmationDialog = true },
                onCreateFileRequest = { showCreateFileDialog = true },
                onCreateFolderRequest = { showCreateFolderDialog = true },
                onRenameRequest = { showRenameDialog = true }
            )
        }
    }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text(stringResource(R.string.filetree_delete_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.filetree_delete_confirm_message), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.filetree_delete_confirm_name, selectedFileNode?.file?.name ?: ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.filetree_delete_confirm_path, selectedFileNode?.file?.absolutePath ?: ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.filetree_delete_confirm_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmationDialog = false
                        showBottomSheet = false
                        selectedFileNode?.let { node ->
                            scope.launch {
                                val parent = node.file.parentFile ?: File(rootPath)
                                val success =
                                    withContext(Dispatchers.IO) {
                                        if (node.isDirectory) node.file.deleteRecursively() else node.file.delete()
                                    }
                                if (success) {
                                    refreshDirectory(parent)
                                    onFileDeleted(node.file)
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (showCreateFileDialog) {
        var fileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text(stringResource(R.string.filetree_new_file)) },
            text = {
                Column {
                    Text(stringResource(R.string.filetree_new_file_prompt), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text(stringResource(R.string.filetree_new_file_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (fileName.isNotBlank()) {
                            showCreateFileDialog = false
                            showBottomSheet = false
                            selectedFileNode?.let { node ->
                                val parentDir =
                                    if (node.isDirectory) node.file else node.file.parentFile
                                parentDir?.let {
                                    scope.launch {
                                        try {
                                            val newFile = File(it, fileName)
                                            val success =
                                                withContext(Dispatchers.IO) { newFile.createNewFile() }
                                            if (success) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.code_editor_file_created),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                refreshDirectory(it)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.code_editor_file_exists),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.code_editor_file_create_failed, e.message),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = fileName.isNotBlank()
                ) {
                    Text(stringResource(R.string.code_editor_create), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateFileDialog = false
                        fileName = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.filetree_new_folder)) },
            text = {
                Column {
                    Text(stringResource(R.string.filetree_new_folder_prompt), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text(stringResource(R.string.filetree_new_folder_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            showCreateFolderDialog = false
                            showBottomSheet = false
                            selectedFileNode?.let { node ->
                                val parentDir =
                                    if (node.isDirectory) node.file else node.file.parentFile
                                parentDir?.let {
                                    scope.launch {
                                        val newDir = File(it, folderName)
                                        val success =
                                            withContext(Dispatchers.IO) { newDir.mkdirs() }
                                        if (success) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.code_editor_folder_created),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            refreshDirectory(it)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.code_editor_folder_create_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = folderName.isNotBlank()
                ) {
                    Text(stringResource(R.string.code_editor_create), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateFolderDialog = false
                        folderName = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(selectedFileNode?.file?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.filetree_rename)) },
            text = {
                Column {
                    Text(stringResource(R.string.filetree_rename_prompt), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.filetree_rename_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            showRenameDialog = false
                            showBottomSheet = false
                            selectedFileNode?.let { node ->
                                scope.launch {
                                    val parent = node.file.parentFile ?: return@launch
                                    val oldFile = node.file
                                    val newFile = File(parent, newName)
                                    val success =
                                        withContext(Dispatchers.IO) { oldFile.renameTo(newFile) }
                                    if (success) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.filetree_rename_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        refreshDirectory(parent)
                                        onFileRenamed(oldFile, newFile)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.filetree_rename_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text(stringResource(R.string.filetree_rename), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        newName = selectedFileNode?.file?.name ?: ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileNodeItem(
    node: FileNode,
    depth: Int,
    expandedNodes: Set<String>,
    minWidth: Dp,
    gitStates: Map<String, GitFileState>,
    watchTrigger: Int,
    onToggle: (FileNode) -> Unit,
    onFileClick: (File) -> Unit,
    onLongClick: (FileNode) -> Unit,
    onWidthMeasured: (String, Int) -> Unit,
    onDisposed: (String) -> Unit
) {
    val isExpanded = expandedNodes.contains(node.file.path)
    val gitState = gitStates[node.file.path]

    val animationSpec = tween<Float>(durationMillis = 150)
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "arrowAnimation",
        animationSpec = animationSpec
    )

    var children by remember(isExpanded, node, watchTrigger) { mutableStateOf<List<FileNode>>(emptyList()) }

    LaunchedEffect(isExpanded, node, watchTrigger) {
        if (isExpanded && node.isDirectory) {
            withContext(Dispatchers.IO) {
                val loaded = node.file.listFiles()
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?.map { FileNode(file = it, isDirectory = it.isDirectory) }
                    ?: emptyList()
                withContext(Dispatchers.Main) {
                    children = loaded
                }
            }
        }
    }

    DisposableEffect(node.file.path) {
        onDispose { onDisposed(node.file.path) }
    }

    val widthModifier = if (minWidth > 0.dp) {
        Modifier.widthIn(min = minWidth)
    } else {
        Modifier.fillMaxWidth()
    }

    Column(
        modifier = Modifier
            .then(widthModifier)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = {
                    if (node.isDirectory) {
                        onToggle(node)
                    } else {
                        onFileClick(node.file)
                    }
                },
                onLongClick = { onLongClick(node) }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onWidthMeasured(node.file.path, it.width) }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩进参考线
            if (depth > 0) {
                repeat(depth) {
                    Box(modifier = Modifier.width(20.dp)) {
                        Box(
                            modifier = Modifier
                                .padding(start = 9.dp)
                                .width(1.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )
                    }
                }
            }

            // 展开箭头（仅文件夹） - 使用20dp大小
            if (node.isDirectory) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.open_drawer),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }

            // 文件/文件夹图标 - 使用24dp大小
            Spacer(modifier = Modifier.width(2.dp))

            if (node.isDirectory) {
                if (node.file.name == ".git") {
                    Icon(
                        Icons.Filled.AccountTree,
                        contentDescription = stringResource(R.string.cd_project_folder),
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFFE5733A)
                    )
                } else {
                    Icon(
                        if (isExpanded) Icons.Filled.FolderOpen else Icons.Default.Folder,
                        contentDescription = stringResource(R.string.cd_project_folder),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (isImageFile(node.file.name)) {
                // 使用 Coil 加载图像文件
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(node.file)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.cd_project_icon),
                    modifier = Modifier.size(24.dp),
                    placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                    error = painterResource(android.R.drawable.ic_menu_gallery)
                )
            } else {
                // 其他文件：优先使用自定义图标，其次使用 Material 图标
                val customIconRes = getFileIconResource(node.file.name)
                if (customIconRes != null) {
                    Icon(
                        painter = painterResource(id = customIconRes),
                        contentDescription = stringResource(R.string.cd_project_icon),
                        modifier = Modifier.size(24.dp),
                        tint = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                } else {
                    val materialIcon = getMaterialFileIcon(node.file.name)
                    Icon(
                        imageVector = materialIcon,
                        contentDescription = stringResource(R.string.cd_project_icon),
                        modifier = Modifier.size(24.dp),
                        tint = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = node.file.name,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                fontSize = 14.sp,
                color = gitState?.let { state ->
                    when (state) {
                        GitFileState.MODIFIED -> Color(0xFFFFA726)
                        GitFileState.ADDED, GitFileState.CHANGED, GitFileState.UNTRACKED -> Color(0xFF66BB6A)
                        GitFileState.DELETED, GitFileState.MISSING, GitFileState.CONFLICT -> Color(0xFFEF5350)
                    }
                } ?: LocalContentColor.current
            )

            // Git 状态小图标徽标
            if (gitState != null) {
                GitStateBadge(gitState)
            }

            Spacer(modifier = Modifier.width(24.dp))
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(150)),
            exit = shrinkVertically(animationSpec = tween(150))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                children.forEach { child ->
                    FileNodeItem(
                        node = child,
                        depth = depth + 1,
                        expandedNodes = expandedNodes,
                        minWidth = minWidth,
                        gitStates = gitStates,
                        watchTrigger = watchTrigger,
                        onToggle = onToggle,
                        onFileClick = onFileClick,
                        onLongClick = onLongClick,
                        onWidthMeasured = onWidthMeasured,
                        onDisposed = onDisposed
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSheetActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    color: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (color != Color.Unspecified) color else LocalContentColor.current
        Icon(imageVector = icon, contentDescription = text, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, color = tint, fontSize = 16.sp)
    }
}

@Suppress("DEPRECATION")
@Composable
fun FileActionBottomSheet(
    node: FileNode,
    onDismiss: () -> Unit,
    onDeleteRequest: () -> Unit,
    onCreateFileRequest: () -> Unit,
    onCreateFolderRequest: () -> Unit,
    onRenameRequest: () -> Unit,
    gitState: GitFileState? = null,
    onGitStage: ((File) -> Unit)? = null,
    onGitUnstage: ((File) -> Unit)? = null,
    onGitDiscard: ((File) -> Unit)? = null,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val isDirectory = node.isDirectory
    val fileExtension = if (!isDirectory) node.file.name.substringAfterLast('.', "").lowercase() else ""
    
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        BottomSheetActionItem(
            Icons.Default.Description,
            stringResource(R.string.filetree_new_file),
            { onCreateFileRequest(); onDismiss() })
        BottomSheetActionItem(
            Icons.Default.CreateNewFolder,
            stringResource(R.string.filetree_new_folder),
            { onCreateFolderRequest(); onDismiss() })
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )
        BottomSheetActionItem(
            Icons.Default.DriveFileRenameOutline,
            stringResource(R.string.filetree_rename),
            { onRenameRequest(); onDismiss() })
        BottomSheetActionItem(
            Icons.Default.ContentCopy,
            stringResource(R.string.filetree_copy_path), {
                clipboardManager.setText(AnnotatedString(node.file.absolutePath))
                Toast.makeText(context, context.getString(R.string.filetree_path_copied), Toast.LENGTH_SHORT).show()
                onDismiss()
            })

        // Git 操作区（仅当文件处于 Git 变更状态时显示）
        if (gitState != null) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )
            val staged = gitState == GitFileState.ADDED || gitState == GitFileState.CHANGED || gitState == GitFileState.DELETED
            if (staged && onGitUnstage != null) {
                BottomSheetActionItem(
                    Icons.Default.Close,
                    stringResource(R.string.git_unstage),
                    {
                        onGitUnstage(node.file)
                        onDismiss()
                    })
            } else if (onGitStage != null) {
                BottomSheetActionItem(
                    Icons.Default.AddCircle,
                    stringResource(R.string.git_stage),
                    {
                        onGitStage(node.file)
                        onDismiss()
                    })
            }
            if (gitState == GitFileState.MODIFIED && onGitDiscard != null) {
                BottomSheetActionItem(
                    Icons.Default.Refresh,
                    stringResource(R.string.git_discard),
                    {
                        onGitDiscard(node.file)
                        onDismiss()
                    },
                    MaterialTheme.colorScheme.error
                )
            }
        }

        val pluginMenuItems = com.luaforge.studio.lxclua.plugin.state.UIState.fileTreeMenuItems
        if (pluginMenuItems.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )
        }
        
        for (menuItem in pluginMenuItems) {
            when (menuItem) {
                is com.luaforge.studio.lxclua.plugin.state.UIState.FileTreeMenuItem.Item -> {
                    if (matchesFilter(menuItem.filter, isDirectory, fileExtension)) {
                        BottomSheetActionItem(
                            getIconByName(menuItem.iconName),
                            menuItem.label,
                            {
                                menuItem.onClick(node.file.absolutePath, isDirectory)
                                onDismiss()
                            })
                    }
                }
                is com.luaforge.studio.lxclua.plugin.state.UIState.FileTreeMenuItem.Divider -> {
                    if (matchesFilter(menuItem.filter, isDirectory, fileExtension)) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = DividerDefaults.Thickness,
                            color = DividerDefaults.color
                        )
                    }
                }
            }
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )
        BottomSheetActionItem(
            Icons.Default.Delete,
            stringResource(R.string.delete),
            { onDeleteRequest(); onDismiss() },
            MaterialTheme.colorScheme.error
        )
    }
}

/**
 * 检查菜单项过滤器是否匹配当前文件
 */
private fun matchesFilter(filter: String?, isDirectory: Boolean, fileExtension: String): Boolean {
    if (filter == null) return true
    
    return when (filter.lowercase()) {
        "directory" -> isDirectory
        "file" -> !isDirectory
        else -> {
            if (filter.startsWith('.')) {
                fileExtension == filter.substring(1).lowercase()
            } else {
                fileExtension == filter.lowercase()
            }
        }
    }
}

/**
 * 根据图标名称获取 Material 图标
 */
private fun getIconByName(iconName: String?): ImageVector {
    if (iconName == null) {
        return Icons.Default.Description
    }
    
    return when (iconName.lowercase()) {
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
        "sort" -> Icons.Default.Sort
        "refresh" -> Icons.Default.Refresh
        "export" -> Icons.Default.Share
        "import" -> Icons.Default.Share
        "send" -> Icons.Default.Send
        "open" -> Icons.Default.OpenInNew
        "close" -> Icons.Default.Close
        "check" -> Icons.Default.Check
        "alert" -> Icons.Default.Warning
        "error" -> Icons.Default.Error
        "success" -> Icons.Default.CheckCircle
        "play" -> Icons.Default.PlayArrow
        "pause" -> Icons.Default.Pause
        "stop" -> Icons.Default.Stop
        else -> Icons.Default.Description
    }
}