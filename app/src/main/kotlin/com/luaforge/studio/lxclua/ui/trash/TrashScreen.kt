package com.luaforge.studio.lxclua.ui.trash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luaforge.studio.lxclua.plugin.PluginManager
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.utils.RecycleBinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 回收站页面
 * 显示已删除的项目，支持恢复、永久删除、清空回收站
 *
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 回收站项目列表
    var trashItems by remember { mutableStateOf(emptyList<com.luaforge.studio.lxclua.utils.TrashItem>()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<com.luaforge.studio.lxclua.utils.TrashItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 加载回收站列表
    suspend fun refreshTrash() {
        isLoading = true
        withContext(Dispatchers.IO) {
            RecycleBinManager.loadIndex(context)
        }
        trashItems = RecycleBinManager.getTrashItems()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        refreshTrash()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("回收站", fontWeight = FontWeight.Bold)
                        if (trashItems.isNotEmpty()) {
                            Text(
                                "${trashItems.size}个项目 · ${RecycleBinManager.formatSize(RecycleBinManager.getTotalSize())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (trashItems.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "清空回收站",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                trashItems.isEmpty() -> {
                    // 空状态
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "回收站为空",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "删除的项目会在这里保留7天\n之后将自动永久删除",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(trashItems, key = { it.trashId }) { item ->
                            TrashItemCard(
                                item = item,
                                onRestore = {
                                    scope.launch {
                                        val restored = withContext(Dispatchers.IO) {
                                            RecycleBinManager.restoreFromTrash(item.trashId, context)
                                        }
                                        if (restored != null) {
                                            EventManager.fireEvent(
                                                "onProjectRestored",
                                                item.trashId, restored.id, restored.name, restored.path
                                            )
                                            refreshTrash()
                                        }
                                    }
                                },
                                onDelete = {
                                    showDeleteConfirm = item
                                }
                            )
                        }

                        // 底部提示
                        item {
                            Text(
                                "回收站项目将保留7天后自动清理",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // 清空回收站确认
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空回收站") },
            text = { Text("确定要永久删除回收站中的所有${trashItems.size}个项目吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        scope.launch {
                            val count = withContext(Dispatchers.IO) {
                                RecycleBinManager.clearAll(context)
                            }
                            EventManager.fireEvent("onTrashCleared", count)
                            refreshTrash()
                        }
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 永久删除确认
    if (showDeleteConfirm != null) {
        val item = showDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("永久删除") },
            text = { Text("确定要永久删除\"${item.originalName}\"吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val tid = item.trashId
                        showDeleteConfirm = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                RecycleBinManager.permanentlyDelete(tid, context)
                            }
                            refreshTrash()
                        }
                    }
                ) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 回收站项目卡片
 */
@Composable
private fun TrashItemCard(
    item: com.luaforge.studio.lxclua.utils.TrashItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 项目信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    item.originalName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    item.originalPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        RecycleBinManager.formatTimeAgo(item.deletedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        RecycleBinManager.formatSize(item.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 操作按钮
            Row {
                IconButton(onClick = onRestore) {
                    Icon(
                        Icons.Filled.Restore,
                        contentDescription = "恢复",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "永久删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
