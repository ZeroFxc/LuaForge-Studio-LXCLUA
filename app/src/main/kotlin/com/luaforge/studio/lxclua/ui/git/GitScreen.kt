package com.luaforge.studio.lxclua.ui.git

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luaforge.studio.lxclua.R
import com.luaforge.studio.lxclua.git.GitBranchInfo
import com.luaforge.studio.lxclua.git.GitCommitInfo
import com.luaforge.studio.lxclua.git.GitFileState
import com.luaforge.studio.lxclua.git.GitManager
import com.luaforge.studio.lxclua.git.GitProgressMonitor
import com.luaforge.studio.lxclua.git.GitRemoteInfo
import com.luaforge.studio.lxclua.git.GitStatusResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 全屏 Git 面板：覆盖 更改/历史/分支/远程/标签/贮藏/设置 全部功能。
 */
@Composable
fun GitScreen(
    repoDir: File,
    onDismiss: () -> Unit,
    onRepoChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var isBusy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var progressPercent by remember { mutableIntStateOf(-1) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var statusResult by remember { mutableStateOf<GitStatusResult?>(null) }
    var currentBranch by remember { mutableStateOf("") }

    val toast: (String) -> Unit = { msg ->
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    /** 执行 IO 操作并自动刷新 */
    fun runOp(label: String, refreshAfter: Boolean = true, block: suspend () -> Unit) {
        if (isBusy) return
        scope.launch {
            isBusy = true
            busyLabel = label
            progressPercent = -1
            try {
                withContext(Dispatchers.IO) { block() }
                if (refreshAfter) refreshKey++
            } catch (e: Exception) {
                toast("Git: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                isBusy = false
                progressPercent = -1
            }
        }
    }

    fun refreshStatus() {
        scope.launch {
            try {
                val (st, br) = withContext(Dispatchers.IO) {
                    GitManager.status(repoDir) to GitManager.currentBranch(repoDir)
                }
                statusResult = st
                currentBranch = br
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(refreshKey) { refreshStatus() }

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                // 顶栏
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (!isBusy) onDismiss() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.git_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(10.dp))
                    if (currentBranch.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountTree,
                                null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                currentBranch,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { refreshKey++ }, enabled = !isBusy) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = stringResource(R.string.git_refresh)
                        )
                    }
                }

                if (isBusy) {
                    Column(Modifier.fillMaxWidth()) {
                        if (progressPercent in 0..100) {
                            LinearProgressIndicator(
                                progress = { progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            busyLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val tabs = listOf(
                    R.string.git_tab_changes,
                    R.string.git_tab_log,
                    R.string.git_tab_branches,
                    R.string.git_tab_remote,
                    R.string.git_tab_tags,
                    R.string.git_tab_stash,
                    R.string.git_tab_settings,
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, res ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(stringResource(res), fontSize = 13.sp) }
                        )
                    }
                }
                HorizontalDivider()

                Box(Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> GitChangesTab(
                            repoDir = repoDir,
                            status = statusResult,
                            refreshKey = refreshKey,
                            enabled = !isBusy,
                            runOp = { label, block -> runOp(label, block = block) },
                            onRepoChanged = onRepoChanged,
                        )
                        1 -> GitLogTab(
                            repoDir = repoDir,
                            refreshKey = refreshKey,
                            enabled = !isBusy,
                            runOp = { label, block -> runOp(label, block = block) },
                            onRepoChanged = onRepoChanged,
                        )
                        2 -> GitBranchesTab(
                            repoDir = repoDir,
                            refreshKey = refreshKey,
                            enabled = !isBusy,
                            runOp = { label, block -> runOp(label, block = block) },
                            onRepoChanged = onRepoChanged,
                        )
                        3 -> GitRemoteTab(
                            repoDir = repoDir,
                            enabled = !isBusy,
                            onProgress = { _, p -> progressPercent = p },
                            runOp = { label, block -> runOp(label, block = block) },
                            onRepoChanged = onRepoChanged,
                        )
                        4 -> GitTagsTab(
                            repoDir = repoDir,
                            refreshKey = refreshKey,
                            enabled = !isBusy,
                            runOp = { label, block -> runOp(label, block = block) },
                        )
                        5 -> GitStashTab(
                            repoDir = repoDir,
                            refreshKey = refreshKey,
                            enabled = !isBusy,
                            runOp = { label, block -> runOp(label, block = block) },
                            onRepoChanged = onRepoChanged,
                        )
                        6 -> GitSettingsTab(
                            repoDir = repoDir,
                            enabled = !isBusy,
                            runOp = { label, block -> runOp(label, block = block) },
                            onRepoChanged = onRepoChanged,
                        )
                    }
                }
            }
        }
    }
}

// ==================== 更改 ====================

@Composable
private fun GitChangesTab(
    repoDir: File,
    status: GitStatusResult?,
    refreshKey: Int,
    enabled: Boolean,
    runOp: (String, suspend () -> Unit) -> Unit,
    onRepoChanged: () -> Unit,
) {
    val context = LocalContext.current
    var commitMessage by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }
    var authorEmail by remember { mutableStateOf("") }
    var amend by remember { mutableStateOf(false) }
    var diffPath by remember { mutableStateOf<String?>(null) }
    var diffText by remember { mutableStateOf<String?>(null) }
    var diffLoading by remember { mutableStateOf(false) }
    var discardPath by remember { mutableStateOf<String?>(null) }
    var deleteAllUntracked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        try {
            val (name, email) = withContext(Dispatchers.IO) { GitManager.getUserConfig(repoDir) }
            if (authorName.isBlank()) authorName = name
            if (authorEmail.isBlank()) authorEmail = email
        } catch (_: Exception) {
        }
    }

    fun openDiff(relPath: String) {
        diffPath = relPath
        diffText = null
        diffLoading = true
        scope.launch {
            try {
                diffText = withContext(Dispatchers.IO) { GitManager.diffFile(repoDir, relPath) }
            } catch (e: Exception) {
                diffText = e.message
            } finally {
                diffLoading = false
            }
        }
    }

    val fileStates = status?.fileStates.orEmpty()
    val staged = fileStates.filter {
        it.value == GitFileState.ADDED || it.value == GitFileState.CHANGED || it.value == GitFileState.DELETED
    }
    val unstaged = fileStates.filter {
        it.value == GitFileState.MODIFIED || it.value == GitFileState.MISSING || it.value == GitFileState.CONFLICT
    }
    val untracked = fileStates.filter { it.value == GitFileState.UNTRACKED }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 提交卡片
        item {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text(stringResource(R.string.git_message)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = authorName,
                        onValueChange = { authorName = it },
                        label = { Text(stringResource(R.string.git_author_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = authorEmail,
                        onValueChange = { authorEmail = it },
                        label = { Text(stringResource(R.string.git_author_email)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = amend, onCheckedChange = { amend = it })
                    Text(stringResource(R.string.git_amend), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            val msg = commitMessage
                            runOp(context.getString(R.string.git_committing)) {
                                GitManager.commit(repoDir, msg, authorName, authorEmail, amend)
                                withContext(Dispatchers.Main) {
                                    commitMessage = ""
                                    amend = false
                                }
                            }
                            onRepoChanged()
                        },
                        enabled = enabled && commitMessage.isNotBlank() && staged.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.git_commit) + " (${staged.size})")
                    }
                }
            }
        }

        // 已暂存
        if (staged.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GitSectionTitle(stringResource(R.string.git_staged, staged.size), Modifier.weight(1f).padding(0.dp))
                    IconButton(onClick = {
                        runOp(context.getString(R.string.git_unstaging)) { GitManager.unstageAll(repoDir) }
                        onRepoChanged()
                    }) {
                        Icon(Icons.Default.RemoveCircle, stringResource(R.string.git_unstage_all), Modifier.size(18.dp))
                    }
                }
            }
            items(staged.entries.toList(), key = { it.key }) { (path, state) ->
                GitChangeRow(
                    path = path,
                    state = state,
                    onStage = null,
                    onUnstage = {
                        runOp(context.getString(R.string.git_unstaging)) { GitManager.unstage(repoDir, path) }
                        onRepoChanged()
                    },
                    onDiscard = null,
                    onDiff = { openDiff(path) },
                )
            }
        }

        // 未暂存更改
        if (unstaged.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GitSectionTitle(stringResource(R.string.git_unstaged, unstaged.size), Modifier.weight(1f).padding(0.dp))
                    IconButton(onClick = {
                        runOp(context.getString(R.string.git_staging)) {
                            GitManager.stageAll(repoDir)
                        }
                        onRepoChanged()
                    }) {
                        Icon(Icons.Default.AddCircle, stringResource(R.string.git_stage_all), Modifier.size(18.dp))
                    }
                }
            }
            items(unstaged.entries.toList(), key = { it.key }) { (path, state) ->
                GitChangeRow(
                    path = path,
                    state = state,
                    onStage = {
                        runOp(context.getString(R.string.git_staging)) {
                            if (state == GitFileState.MISSING) GitManager.stageDeleted(repoDir, path)
                            else GitManager.stage(repoDir, path)
                        }
                        onRepoChanged()
                    },
                    onUnstage = null,
                    onDiscard = if (state == GitFileState.MODIFIED) {
                        { discardPath = path }
                    } else null,
                    onDiff = { openDiff(path) },
                )
            }
        }

        // 未跟踪
        if (untracked.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GitSectionTitle(
                        stringResource(R.string.git_untracked, untracked.size),
                        Modifier.weight(1f).padding(0.dp)
                    )
                    IconButton(onClick = {
                        val paths = untracked.keys.toTypedArray()
                        runOp(context.getString(R.string.git_staging)) {
                            GitManager.stage(repoDir, *paths)
                        }
                        onRepoChanged()
                    }) {
                        Icon(
                            Icons.Default.AddCircle,
                            stringResource(R.string.git_untracked_stage_all),
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { deleteAllUntracked = true }) {
                        Icon(
                            Icons.Default.Delete,
                            stringResource(R.string.git_untracked_delete_all),
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            items(untracked.entries.toList(), key = { it.key }) { (path, state) ->
                GitChangeRow(
                    path = path,
                    state = state,
                    onStage = {
                        runOp(context.getString(R.string.git_staging)) { GitManager.stage(repoDir, path) }
                        onRepoChanged()
                    },
                    onUnstage = null,
                    onDiscard = null,
                    onDiff = null,
                )
            }
        }

        if (fileStates.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.git_no_changes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    diffPath?.let { path ->
        GitDiffDialog(path = path, diffText = diffText, loading = diffLoading) { diffPath = null }
    }
    discardPath?.let { path ->
        GitConfirmDialog(
            title = stringResource(R.string.git_discard),
            message = stringResource(R.string.git_discard_confirm, path),
            destructive = true,
            onConfirm = {
                discardPath = null
                runOp(context.getString(R.string.git_discarding)) { GitManager.discard(repoDir, path) }
                onRepoChanged()
            },
            onDismiss = { discardPath = null }
        )
    }
    if (deleteAllUntracked) {
        GitConfirmDialog(
            title = stringResource(R.string.git_untracked_delete_all),
            message = stringResource(R.string.git_untracked_delete_all_confirm, untracked.size),
            destructive = true,
            onConfirm = {
                deleteAllUntracked = false
                val paths = untracked.keys.toList()
                runOp(context.getString(R.string.git_cleaning)) {
                    paths.forEach { p -> File(repoDir, p).deleteRecursively() }
                }
                onRepoChanged()
            },
            onDismiss = { deleteAllUntracked = false }
        )
    }
}

@Composable
private fun GitChangeRow(
    path: String,
    state: GitFileState,
    onStage: (() -> Unit)?,
    onUnstage: (() -> Unit)?,
    onDiscard: (() -> Unit)?,
    onDiff: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onDiff?.invoke() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GitStateBadge(state)
        Spacer(Modifier.width(6.dp))
        Text(
            path,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onDiff != null) {
            GitActionIcon(Icons.Default.Visibility, stringResource(R.string.git_diff)) { onDiff() }
        }
        if (onStage != null) {
            GitActionIcon(
                Icons.Default.AddCircle,
                stringResource(R.string.git_stage),
                MaterialTheme.colorScheme.primary
            ) { onStage() }
        }
        if (onUnstage != null) {
            GitActionIcon(Icons.Default.RemoveCircle, stringResource(R.string.git_unstage)) { onUnstage() }
        }
        if (onDiscard != null) {
            GitActionIcon(
                Icons.Default.Restore,
                stringResource(R.string.git_discard),
                MaterialTheme.colorScheme.error
            ) { onDiscard() }
        }
    }
}

// ==================== 历史 ====================

/** 图形分支线调色板（红/绿/黄/蓝/紫/橙/青/粉） */
private val GRAPH_COLORS = listOf(
    Color(0xFFEF5350), // 红
    Color(0xFF66BB6A), // 绿
    Color(0xFFFFCA28), // 黄
    Color(0xFF42A5F5), // 蓝
    Color(0xFFAB47BC), // 紫
    Color(0xFFFF7043), // 橙
    Color(0xFF26C6DA), // 青
    Color(0xFFEC407A), // 粉
)

/** 单行提交图的绘制数据 */
private data class GraphRow(
    val lane: Int,
    val color: Color,
    val incoming: Boolean,
    val hasParents: Boolean,
    val passingLanes: List<Pair<Int, Color>>,
    val mergeEdges: List<Pair<Int, Color>>,
)

/**
 * 简化版 git log --graph 泳道算法：
 * 每个泳道跟踪一个待到达的提交 id，第一父继承当前泳道，合并父分配新泳道。
 */
private fun buildGraph(commits: List<GitCommitInfo>): List<GraphRow> {
    val active = ArrayList<String?>()
    val laneColorIdx = ArrayList<Int>()
    var colorCounter = 0
    val rows = ArrayList<GraphRow>(commits.size)

    fun colorOf(lane: Int): Color =
        GRAPH_COLORS[laneColorIdx.getOrElse(lane) { 0 } % GRAPH_COLORS.size]

    fun findEmptyLane(): Int {
        val idx = active.indexOf(null)
        if (idx >= 0) return idx
        active.add(null)
        laneColorIdx.add(colorCounter++)
        return active.size - 1
    }

    for (commit in commits) {
        var lane = active.indexOf(commit.id)
        val incoming = lane >= 0
        if (!incoming) lane = findEmptyLane()
        val commitColor = colorOf(lane)
        active[lane] = null

        val passing = active.mapIndexedNotNull { i, v ->
            if (v != null) i to colorOf(i) else null
        }

        val parents = commit.parentIds
        val mergeEdges = ArrayList<Pair<Int, Color>>()
        if (parents.isNotEmpty()) {
            active[lane] = parents[0]
            for (p in parents.drop(1)) {
                val pl = findEmptyLane()
                active[pl] = p
                mergeEdges.add(pl to colorOf(pl))
            }
        }
        rows.add(
            GraphRow(
                lane = lane,
                color = commitColor,
                incoming = incoming,
                hasParents = parents.isNotEmpty(),
                passingLanes = passing,
                mergeEdges = mergeEdges,
            )
        )
    }
    return rows
}

@Composable
private fun GitLogTab(
    repoDir: File,
    refreshKey: Int,
    enabled: Boolean,
    runOp: (String, suspend () -> Unit) -> Unit,
    onRepoChanged: () -> Unit,
) {
    val context = LocalContext.current
    var commits by remember { mutableStateOf<List<GitCommitInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<GitCommitInfo?>(null) }
    var branchCreateFor by remember { mutableStateOf<String?>(null) }
    var tagCreateFor by remember { mutableStateOf<String?>(null) }
    var resetFor by remember { mutableStateOf<String?>(null) }
    val graphRows by remember(commits) { mutableStateOf(buildGraph(commits)) }
    val graphLanes = remember(graphRows) {
        (graphRows.maxOfOrNull { it.lane + 1 } ?: 1).coerceIn(1, 8)
    }

    LaunchedEffect(refreshKey) {
        loading = true
        commits = try {
            withContext(Dispatchers.IO) { GitManager.log(repoDir, maxCount = 500) }
        } catch (e: Exception) {
            emptyList()
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (commits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.git_no_commits),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(commits, key = { _, c -> c.id }) { index, commit ->
                    val graph = graphRows.getOrNull(index)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { detail = commit }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 图形分支线
                        val laneWidth = 16.dp
                        val dotColor = graph?.color ?: MaterialTheme.colorScheme.primary
                        Canvas(
                            modifier = Modifier
                                .width(laneWidth * graphLanes)
                                .height(44.dp)
                        ) {
                            val lw = laneWidth.toPx()
                            val cy = size.height / 2f
                            val strokeWidth = 2.5.dp.toPx()
                            fun laneX(l: Int) = l * lw + lw / 2f
                            // 穿越本行的其它泳道竖线
                            graph?.passingLanes?.forEach { (l, c) ->
                                drawLine(c, Offset(laneX(l), 0f), Offset(laneX(l), size.height), strokeWidth)
                            }
                            val lane = graph?.lane ?: 0
                            // 本泳道：上方进线 / 下方出线
                            if (graph?.incoming != false) {
                                drawLine(dotColor, Offset(laneX(lane), 0f), Offset(laneX(lane), cy), strokeWidth)
                            }
                            if (graph?.hasParents != false) {
                                drawLine(dotColor, Offset(laneX(lane), cy), Offset(laneX(lane), size.height), strokeWidth)
                            }
                            // 合并分叉线
                            graph?.mergeEdges?.forEach { (l, c) ->
                                drawLine(c, Offset(laneX(lane), cy), Offset(laneX(l), size.height), strokeWidth)
                            }
                            // 提交圆点
                            drawCircle(dotColor, 5.dp.toPx(), Offset(laneX(lane), cy))
                            drawCircle(Color.White, 2.dp.toPx(), Offset(laneX(lane), cy))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                commit.message,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${commit.authorName} · ${formatGitTime(commit.commitTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            commit.shortId,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = dotColor
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
                }
            }
        }
    }

    detail?.let { commit ->
        GitCommitDetailDialog(
            commit = commit,
            onCheckout = {
                detail = null
                runOp(context.getString(R.string.git_checking_out)) {
                    GitManager.checkout(repoDir, commit.id)
                }
                onRepoChanged()
            },
            onNewBranch = {
                branchCreateFor = commit.id
                detail = null
            },
            onNewTag = {
                tagCreateFor = commit.id
                detail = null
            },
            onReset = {
                resetFor = commit.id
                detail = null
            },
            onDismiss = { detail = null }
        )
    }
    branchCreateFor?.let { ref ->
        GitBranchCreateDialog(
            startPoint = ref.take(7),
            onCreate = { name, checkout, force ->
                branchCreateFor = null
                runOp(context.getString(R.string.git_branch_op)) {
                    if (checkout) GitManager.checkoutNewBranch(repoDir, name, ref)
                    else GitManager.createBranch(repoDir, name, ref, force)
                }
                onRepoChanged()
            },
            onDismiss = { branchCreateFor = null }
        )
    }
    tagCreateFor?.let { ref ->
        GitTagCreateDialog(
            defaultRef = ref.take(7),
            onCreate = { name, message, tagRef ->
                tagCreateFor = null
                runOp(context.getString(R.string.git_tag_op)) {
                    GitManager.createTag(repoDir, name, message, tagRef)
                }
            },
            onDismiss = { tagCreateFor = null }
        )
    }
    resetFor?.let { ref ->
        GitResetDialog(
            defaultRef = ref.take(7),
            onReset = { mode, resetRef ->
                resetFor = null
                runOp(context.getString(R.string.git_resetting)) {
                    GitManager.reset(repoDir, mode, resetRef)
                }
                onRepoChanged()
            },
            onDismiss = { resetFor = null }
        )
    }
}

// ==================== 分支 ====================

@Composable
private fun GitBranchesTab(
    repoDir: File,
    refreshKey: Int,
    enabled: Boolean,
    runOp: (String, suspend () -> Unit) -> Unit,
    onRepoChanged: () -> Unit,
) {
    val context = LocalContext.current
    var branches by remember { mutableStateOf<List<GitBranchInfo>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<GitBranchInfo?>(null) }
    var createFrom by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf<GitBranchInfo?>(null) }

    LaunchedEffect(refreshKey) {
        branches = try {
            withContext(Dispatchers.IO) { GitManager.branches(repoDir) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    val local = branches.filter { !it.isRemote }
    val remote = branches.filter { it.isRemote }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GitSectionTitle(stringResource(R.string.git_branch_local, local.size), Modifier.weight(1f).padding(0.dp))
                IconButton(onClick = {
                    createFrom = null
                    showCreate = true
                }) {
                    Icon(Icons.Default.Add, stringResource(R.string.git_new_branch), Modifier.size(20.dp))
                }
            }
        }
        items(local, key = { it.name }) { branch ->
            GitBranchRow(branch) { actionsFor = branch }
        }
        item { GitSectionTitle(stringResource(R.string.git_branch_remote, remote.size)) }
        items(remote, key = { it.name }) { branch ->
            GitBranchRow(branch) { actionsFor = branch }
        }
    }

    if (showCreate) {
        GitBranchCreateDialog(
            startPoint = createFrom,
            onCreate = { name, checkout, force ->
                showCreate = false
                runOp(context.getString(R.string.git_branch_op)) {
                    if (checkout) GitManager.checkoutNewBranch(repoDir, name, createFrom)
                    else GitManager.createBranch(repoDir, name, createFrom, force)
                }
                onRepoChanged()
            },
            onDismiss = { showCreate = false }
        )
    }
    actionsFor?.let { branch ->
        GitBranchActionsDialog(
            branchName = branch.name,
            isRemote = branch.isRemote,
            onCheckout = {
                actionsFor = null
                runOp(context.getString(R.string.git_checking_out)) { GitManager.checkout(repoDir, branch.name) }
                onRepoChanged()
            },
            onDelete = {
                deleteConfirm = branch
                actionsFor = null
            },
            onRename = { newName ->
                actionsFor = null
                val oldName = branch.name
                runOp(context.getString(R.string.git_branch_op)) {
                    GitManager.renameBranch(repoDir, oldName, newName)
                }
            },
            onNewBranchFrom = {
                createFrom = branch.name
                actionsFor = null
                showCreate = true
            },
            onDismiss = { actionsFor = null }
        )
    }
    deleteConfirm?.let { branch ->
        GitConfirmDialog(
            title = stringResource(R.string.git_delete_branch),
            message = stringResource(R.string.git_delete_branch_confirm, branch.name),
            destructive = true,
            onConfirm = {
                deleteConfirm = null
                runOp(context.getString(R.string.git_branch_op)) {
                    GitManager.deleteBranch(repoDir, branch.name, force = true)
                }
            },
            onDismiss = { deleteConfirm = null }
        )
    }
}

@Composable
private fun GitBranchRow(branch: GitBranchInfo, onMore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onMore)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.AccountTree,
            null,
            Modifier.size(18.dp),
            tint = if (branch.isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(
            branch.name.removePrefix("refs/heads/").removePrefix("refs/remotes/"),
            style = MaterialTheme.typography.bodyMedium,
            color = if (branch.isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (branch.isCurrent) {
            Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.Default.MoreVert,
            stringResource(R.string.git_more),
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== 远程 ====================

@Composable
private fun GitRemoteTab(
    repoDir: File,
    enabled: Boolean,
    onProgress: (String, Int) -> Unit,
    runOp: (String, suspend () -> Unit) -> Unit,
    onRepoChanged: () -> Unit,
) {
    val context = LocalContext.current
    var remotes by remember { mutableStateOf<List<GitRemoteInfo>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var force by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GitRemoteEditTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<GitRemoteInfo?>(null) }
    var resultLog by remember { mutableStateOf("") }

    LaunchedEffect(refreshKey) {
        remotes = try {
            withContext(Dispatchers.IO) { GitManager.remotes(repoDir) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    val monitor = remember {
        GitProgressMonitor { title, percent -> onProgress(title, percent) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.git_username)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(stringResource(R.string.git_token)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GitSectionTitle(stringResource(R.string.git_remotes, remotes.size), Modifier.weight(1f).padding(0.dp))
                IconButton(onClick = {
                    editing = null
                    showAdd = true
                }) {
                    Icon(Icons.Default.Add, stringResource(R.string.git_remote_add), Modifier.size(20.dp))
                }
            }
        }
        items(remotes, key = { it.name }) { remote ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(remote.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        remote.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                GitActionIcon(Icons.Default.Edit, stringResource(R.string.git_remote_edit)) {
                    editing = GitRemoteEditTarget(remote.name, remote.url)
                    showAdd = true
                }
                GitActionIcon(
                    Icons.Default.Delete,
                    stringResource(R.string.git_remote_delete),
                    MaterialTheme.colorScheme.error
                ) { deleteTarget = remote }
            }
        }

        // 操作区
        item {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val remote = remotes.firstOrNull()?.name ?: "origin"
                            resultLog = ""
                            runOp(context.getString(R.string.git_fetching)) {
                                resultLog = GitManager.fetch(
                                    repoDir, remote, username, token, monitor
                                )
                            }
                        },
                        enabled = enabled && remotes.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CloudDownload, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.git_fetch))
                    }
                    OutlinedButton(
                        onClick = {
                            val remote = remotes.firstOrNull()?.name ?: "origin"
                            resultLog = ""
                            runOp(context.getString(R.string.git_pulling)) {
                                resultLog = GitManager.pull(
                                    repoDir, remote, null, username, token, monitor
                                )
                            }
                            onRepoChanged()
                        },
                        enabled = enabled && remotes.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CloudSync, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.git_pull))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = force, onCheckedChange = { force = it })
                    Text(stringResource(R.string.git_push_force), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            val remote = remotes.firstOrNull()?.name ?: "origin"
                            resultLog = ""
                            runOp(context.getString(R.string.git_pushing)) {
                                val updates = GitManager.push(
                                    repoDir, remote, username, token, force, monitor
                                )
                                resultLog = if (updates.isEmpty()) "push: no updates"
                                else updates.joinToString("\n") { "${it.ref} ${it.status}${it.message?.let { m -> " ($m)" } ?: ""}" }
                            }
                        },
                        enabled = enabled && remotes.isNotEmpty()
                    ) {
                        Icon(Icons.Default.CloudUpload, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.git_push))
                    }
                }
                if (resultLog.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        resultLog,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAdd) {
        GitRemoteAddDialog(
            editingRemote = editing,
            onSave = { name, url ->
                showAdd = false
                val wasEditing = editing
                runOp(context.getString(R.string.git_remote_op)) {
                    if (wasEditing != null) GitManager.updateRemoteUrl(repoDir, wasEditing.name, url)
                    else GitManager.addRemote(repoDir, name, url)
                    withContext(Dispatchers.Main) { refreshKey++ }
                }
            },
            onDismiss = { showAdd = false }
        )
    }
    deleteTarget?.let { remote ->
        GitConfirmDialog(
            title = stringResource(R.string.git_remote_delete),
            message = stringResource(R.string.git_remote_delete_confirm, remote.name),
            destructive = true,
            onConfirm = {
                deleteTarget = null
                runOp(context.getString(R.string.git_remote_op)) {
                    GitManager.removeRemote(repoDir, remote.name)
                    withContext(Dispatchers.Main) { refreshKey++ }
                }
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

// ==================== 标签 ====================

@Composable
private fun GitTagsTab(
    repoDir: File,
    refreshKey: Int,
    enabled: Boolean,
    runOp: (String, suspend () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        tags = try {
            withContext(Dispatchers.IO) { GitManager.tags(repoDir) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GitSectionTitle(stringResource(R.string.git_tags, tags.size), Modifier.weight(1f).padding(0.dp))
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.git_new_tag), Modifier.size(20.dp))
                }
            }
        }
        if (tags.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.git_no_tags),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
        items(tags, key = { it }) { tag ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Label,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(10.dp))
                Text(tag, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                GitActionIcon(
                    Icons.Default.Delete,
                    stringResource(R.string.git_delete),
                    MaterialTheme.colorScheme.error
                ) { deleteTarget = tag }
            }
        }
    }

    if (showCreate) {
        GitTagCreateDialog(
            onCreate = { name, message, ref ->
                showCreate = false
                runOp(context.getString(R.string.git_tag_op)) {
                    GitManager.createTag(repoDir, name, message, ref)
                }
            },
            onDismiss = { showCreate = false }
        )
    }
    deleteTarget?.let { tag ->
        GitConfirmDialog(
            title = stringResource(R.string.git_delete_tag),
            message = stringResource(R.string.git_delete_tag_confirm, tag),
            destructive = true,
            onConfirm = {
                deleteTarget = null
                runOp(context.getString(R.string.git_tag_op)) { GitManager.deleteTag(repoDir, tag) }
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

// ==================== 贮藏 ====================

@Composable
private fun GitStashTab(
    repoDir: File,
    refreshKey: Int,
    enabled: Boolean,
    runOp: (String, suspend () -> Unit) -> Unit,
    onRepoChanged: () -> Unit,
) {
    val context = LocalContext.current
    var stashes by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showCreate by remember { mutableStateOf(false) }
    var dropIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(refreshKey) {
        stashes = try {
            withContext(Dispatchers.IO) { GitManager.stashList(repoDir) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GitSectionTitle(stringResource(R.string.git_stashes, stashes.size), Modifier.weight(1f).padding(0.dp))
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.git_stash_create), Modifier.size(20.dp))
                }
            }
        }
        if (stashes.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.git_no_stash),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
        itemsIndexed(stashes) { index, stash ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Inbox,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stash.first, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stash.second,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                GitActionIcon(
                    Icons.Default.Restore,
                    stringResource(R.string.git_stash_apply),
                    MaterialTheme.colorScheme.primary
                ) {
                    runOp(context.getString(R.string.git_stash_op)) {
                        GitManager.stashApply(repoDir, stash.first)
                    }
                    onRepoChanged()
                }
                GitActionIcon(
                    Icons.Default.Delete,
                    stringResource(R.string.git_stash_drop),
                    MaterialTheme.colorScheme.error
                ) { dropIndex = index }
            }
        }
    }

    if (showCreate) {
        GitStashCreateDialog(onCreate = { message ->
            showCreate = false
            runOp(context.getString(R.string.git_stash_op)) {
                GitManager.stashCreate(repoDir, message)
            }
            onRepoChanged()
        }, onDismiss = { showCreate = false })
    }
    if (dropIndex >= 0) {
        GitConfirmDialog(
            title = stringResource(R.string.git_stash_drop),
            message = stringResource(R.string.git_stash_drop_confirm, dropIndex),
            destructive = true,
            onConfirm = {
                val idx = dropIndex
                dropIndex = -1
                runOp(context.getString(R.string.git_stash_op)) { GitManager.stashDrop(repoDir, idx) }
            },
            onDismiss = { dropIndex = -1 }
        )
    }
}

// ==================== 设置 ====================

@Composable
private fun GitSettingsTab(
    repoDir: File,
    enabled: Boolean,
    runOp: (String, suspend () -> Unit) -> Unit,
    onRepoChanged: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showClean by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val (n, e) = withContext(Dispatchers.IO) { GitManager.getUserConfig(repoDir) }
            name = n
            email = e
        } finally {
            loaded = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { GitSectionTitle(stringResource(R.string.git_config)) }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.git_author_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.git_author_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        runOp(context.getString(R.string.git_saving)) {
                            GitManager.setUserConfig(repoDir, name, email)
                        }
                    },
                    enabled = enabled && loaded
                ) {
                    Text(stringResource(R.string.git_save))
                }
            }
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            GitSectionTitle(stringResource(R.string.git_danger_zone))
        }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                OutlinedButton(
                    onClick = { showReset = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.git_reset), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClean = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.git_clean), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showReset) {
        GitResetDialog(
            showRefField = true,
            onReset = { mode, ref ->
                showReset = false
                runOp(context.getString(R.string.git_resetting)) {
                    GitManager.reset(repoDir, mode, ref)
                }
                onRepoChanged()
            },
            onDismiss = { showReset = false }
        )
    }
    if (showClean) {
        GitConfirmDialog(
            title = stringResource(R.string.git_clean),
            message = stringResource(R.string.git_clean_confirm),
            destructive = true,
            onConfirm = {
                showClean = false
                runOp(context.getString(R.string.git_cleaning)) {
                    val removed = GitManager.clean(repoDir)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.git_clean_result, removed.size),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                onRepoChanged()
            },
            onDismiss = { showClean = false }
        )
    }
}
