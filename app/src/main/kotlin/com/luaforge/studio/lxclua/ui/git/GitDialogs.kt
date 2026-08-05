package com.luaforge.studio.lxclua.ui.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luaforge.studio.lxclua.R
import com.luaforge.studio.lxclua.git.GitCommitInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==================== 共享小组件 ====================

/** Git 状态字母徽标（M/A/D/?/C 等小图标） */
@Composable
fun GitStateBadge(state: com.luaforge.studio.lxclua.git.GitFileState) {
    val (letter, color) = when (state) {
        com.luaforge.studio.lxclua.git.GitFileState.UNTRACKED -> "?" to Color(0xFF4CAF50)
        com.luaforge.studio.lxclua.git.GitFileState.MODIFIED -> "M" to Color(0xFFFFA726)
        com.luaforge.studio.lxclua.git.GitFileState.ADDED -> "A" to Color(0xFF66BB6A)
        com.luaforge.studio.lxclua.git.GitFileState.CHANGED -> "M" to Color(0xFF66BB6A)
        com.luaforge.studio.lxclua.git.GitFileState.DELETED -> "D" to Color(0xFFEF5350)
        com.luaforge.studio.lxclua.git.GitFileState.MISSING -> "D" to Color(0xFFFFA726)
        com.luaforge.studio.lxclua.git.GitFileState.CONFLICT -> "!" to Color(0xFFEF5350)
    }
    Text(
        text = letter,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .padding(start = 4.dp)
    )
}

@Composable
fun GitSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

fun formatGitTime(timeMillis: Long): String {
    if (timeMillis <= 0) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

// ==================== 初始化 / 克隆 ====================

@Composable
fun GitInitDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
        title = { Text(stringResource(R.string.git_init)) },
        text = { Text(stringResource(R.string.git_init_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.git_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun GitCloneDialog(
    onClone: (url: String, username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null) },
        title = { Text(stringResource(R.string.git_clone)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.git_remote_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.git_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.git_token)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onClone(url, username, password) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.git_clone))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ==================== 分支 ====================

@Composable
fun GitBranchCreateDialog(
    startPoint: String? = null,
    onCreate: (name: String, checkout: Boolean, force: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var checkout by remember { mutableStateOf(true) }
    var force by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
        title = { Text(stringResource(R.string.git_new_branch)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.git_branch_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!startPoint.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.git_start_point, startPoint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checkout, onCheckedChange = { checkout = it })
                    Text(stringResource(R.string.git_checkout_after), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = force, onCheckedChange = { force = it })
                    Text(stringResource(R.string.git_force), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, checkout, force) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.git_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun GitBranchActionsDialog(
    branchName: String,
    isRemote: Boolean,
    onCheckout: () -> Unit,
    onDelete: () -> Unit,
    onRename: (newName: String) -> Unit,
    onNewBranchFrom: () -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(branchName.substringAfterLast('/')) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
        title = { Text(branchName, style = MaterialTheme.typography.titleMedium) },
        text = {
            if (renaming) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.git_rename)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column {
                    TextButton(onClick = onCheckout, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.git_checkout))
                    }
                    TextButton(onClick = onNewBranchFrom, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AddCircle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.git_new_branch))
                    }
                    if (!isRemote) {
                        TextButton(onClick = { renaming = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.git_rename))
                        }
                        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.git_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (renaming) {
                TextButton(onClick = { onRename(newName) }, enabled = newName.isNotBlank()) {
                    Text(stringResource(R.string.git_confirm))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
        dismissButton = {
            if (renaming) {
                TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

// ==================== 标签 ====================

@Composable
fun GitTagCreateDialog(
    defaultRef: String = "HEAD",
    onCreate: (name: String, message: String, ref: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf(defaultRef) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
        title = { Text(stringResource(R.string.git_new_tag)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.git_tag_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text(stringResource(R.string.git_ref)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.git_tag_message)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, message, ref) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.git_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ==================== 重置 ====================

@Composable
fun GitResetDialog(
    defaultRef: String = "HEAD",
    showRefField: Boolean = true,
    onReset: (com.luaforge.studio.lxclua.git.GitManager.ResetMode, ref: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var ref by remember { mutableStateOf(defaultRef) }
    var mode by remember { mutableStateOf(com.luaforge.studio.lxclua.git.GitManager.ResetMode.MIXED) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
        title = { Text(stringResource(R.string.git_reset)) },
        text = {
            Column {
                if (showRefField) {
                    OutlinedTextField(
                        value = ref,
                        onValueChange = { ref = it },
                        label = { Text(stringResource(R.string.git_ref)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                com.luaforge.studio.lxclua.git.GitManager.ResetMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Column {
                            Text(
                                m.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                when (m) {
                                    com.luaforge.studio.lxclua.git.GitManager.ResetMode.SOFT ->
                                        stringResource(R.string.git_reset_soft_desc)
                                    com.luaforge.studio.lxclua.git.GitManager.ResetMode.MIXED ->
                                        stringResource(R.string.git_reset_mixed_desc)
                                    com.luaforge.studio.lxclua.git.GitManager.ResetMode.HARD ->
                                        stringResource(R.string.git_reset_hard_desc)
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onReset(mode, ref) }) { Text(stringResource(R.string.git_reset)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun GitConfirmDialog(
    title: String,
    message: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.git_confirm),
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ==================== 远程 ====================

@Composable
fun GitRemoteAddDialog(
    editingRemote: GitRemoteEditTarget? = null,
    onSave: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(editingRemote?.name ?: "origin") }
    var url by remember { mutableStateOf(editingRemote?.url ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editingRemote != null) R.string.git_remote_edit else R.string.git_remote_add
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.git_remote_name)) },
                    singleLine = true,
                    enabled = editingRemote == null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.git_remote_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, url) }, enabled = name.isNotBlank() && url.isNotBlank()) {
                Text(stringResource(R.string.git_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

data class GitRemoteEditTarget(val name: String, val url: String)

// ==================== 贮藏 ====================

@Composable
fun GitStashCreateDialog(
    onCreate: (message: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_stash_create)) },
        text = {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.git_message)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(message) }) { Text(stringResource(R.string.git_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ==================== 提交详情 ====================

@Composable
fun GitCommitDetailDialog(
    commit: GitCommitInfo,
    onCheckout: () -> Unit,
    onNewBranch: () -> Unit,
    onNewTag: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.History, contentDescription = null) },
        title = { Text(commit.shortId, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(commit.fullMessage, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${commit.authorName} <${commit.authorEmail}>",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    formatGitTime(commit.commitTime),
                    style = MaterialTheme.typography.bodySmall
                )
                SelectionContainer {
                    Text(
                        commit.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = onCheckout) { Text(stringResource(R.string.git_checkout)) }
                    OutlinedButton(onClick = onNewBranch) { Text(stringResource(R.string.git_new_branch)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = onNewTag) { Text(stringResource(R.string.git_new_tag)) }
                    OutlinedButton(onClick = onReset) {
                        Text(stringResource(R.string.git_reset), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

// ==================== Diff 查看 ====================

@Composable
fun GitDiffDialog(
    path: String,
    diffText: String?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(path, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column {
                when {
                    loading -> {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator(Modifier.size(28.dp)) }
                    }
                    diffText.isNullOrBlank() -> Text(
                        stringResource(R.string.git_diff_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    else -> SelectionContainer {
                        Text(
                            text = diffText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .heightIn(max = 420.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

/** 单行文本标签 + 操作图标按钮行（供 Diff/Blame 等操作使用） */
@Composable
fun GitActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
