package com.luaforge.studio.lxclua.git

import android.content.Context
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.HistogramDiff
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.ByteArrayOutputStream
import java.io.File

/** 文件在工作区中的 Git 状态（用于文件树小图标标记） */
enum class GitFileState {
    UNTRACKED,   // 未跟踪
    MODIFIED,    // 已修改（未暂存）
    ADDED,       // 已暂存的新文件
    CHANGED,     // 已暂存的修改
    DELETED,     // 已暂存的删除
    MISSING,     // 已从工作区删除但未暂存
    CONFLICT,    // 冲突
}

/** 提交信息 */
data class GitCommitInfo(
    val id: String,
    val shortId: String,
    val message: String,
    val fullMessage: String,
    val authorName: String,
    val authorEmail: String,
    val commitTime: Long,
    val parentIds: List<String> = emptyList(),
)

/** 分支信息 */
data class GitBranchInfo(
    val name: String,
    val isRemote: Boolean,
    val isCurrent: Boolean,
)

/** 远程仓库信息 */
data class GitRemoteInfo(
    val name: String,
    val url: String,
)

/** 状态统计 */
data class GitStatusSummary(
    val untracked: Int,
    val modified: Int,
    val staged: Int,
    val conflicts: Int,
) {
    val totalChanges: Int get() = untracked + modified + staged + conflicts
    val isClean: Boolean get() = totalChanges == 0
}

/** 完整状态结果：相对路径 -> 状态 */
data class GitStatusResult(
    val fileStates: Map<String, GitFileState>,
    val summary: GitStatusSummary,
)

/** 推送结果条目 */
data class GitPushUpdate(
    val ref: String,
    val status: String,
    val message: String?,
)

/** 网络操作进度监视器 */
class GitProgressMonitor(
    private val onUpdate: (title: String, percent: Int) -> Unit
) : ProgressMonitor {
    private var taskName = ""
    private var total = 0
    private var done = 0

    override fun start(totalTasks: Int) {}

    override fun beginTask(title: String, totalWork: Int) {
        taskName = title
        total = totalWork
        done = 0
        onUpdate(title, if (totalWork <= 0) -1 else 0)
    }

    override fun update(completed: Int) {
        done += completed
        val percent = if (total > 0) (done * 100 / total).coerceIn(0, 100) else -1
        onUpdate(taskName, percent)
    }

    override fun endTask() {
        onUpdate(taskName, 100)
    }

    override fun showDuration(isShowingDuration: Boolean) {}

    override fun isCancelled(): Boolean = false
}

/**
 * JGit 功能封装：覆盖 init/clone/status/add/commit/log/branch/tag/stash/
 * remote/fetch/pull/push/reset/clean/diff/blame/config 等全部核心操作。
 *
 * 注意：所有方法均为同步阻塞调用，必须放在 IO 线程执行。
 */
object GitManager {

    // ==================== Android 临时目录修复 ====================

    @Volatile
    private var tempDirReady = false

    /**
     * Android 上 java.io.tmpdir 系统属性默认为 null，
     * JGit 内部创建临时目录时会按字面量创建名为 "null" 的文件夹。
     * 必须在首次使用 JGit 之前调用本方法（幂等，可重复调用）。
     */
    fun ensureTempDir(context: Context) {
        if (tempDirReady) return
        synchronized(this) {
            if (tempDirReady) return
            val current = System.getProperty("java.io.tmpdir")
            val valid = current != null && File(current).let {
                it.exists() && it.isDirectory && it.canWrite()
            }
            if (!valid) {
                val tmp = File(context.cacheDir, "jgit-tmp")
                if (!tmp.exists()) tmp.mkdirs()
                System.setProperty("java.io.tmpdir", tmp.absolutePath)
            }
            tempDirReady = true
        }
    }

    /**
     * 清理目录下已被误创建的空的 "null" 文件夹（历史遗留）。
     */
    fun cleanupNullFolder(dir: File) {
        val nullDir = File(dir, "null")
        if (nullDir.isDirectory && nullDir.list()?.isEmpty() == true) {
            nullDir.delete()
        }
    }

    // ==================== 基础 ====================

    /** 判断目录是否为 Git 仓库（向上查找 .git） */
    fun findRepositoryDir(dir: File): File? {
        var current: File? = dir
        while (current != null) {
            if (File(current, Constants.DOT_GIT).exists()) return current
            current = current.parentFile
        }
        return null
    }

    fun isRepository(dir: File): Boolean = findRepositoryDir(dir) != null

    private fun openGit(dir: File): Git {
        val repoDir = findRepositoryDir(dir) ?: throw IllegalStateException("Not a git repository: ${dir.path}")
        val repository: Repository = FileRepositoryBuilder()
            .setGitDir(File(repoDir, Constants.DOT_GIT))
            .readEnvironment()
            .build()
        return Git(repository)
    }

    private fun <T> withGit(dir: File, block: (Git) -> T): T {
        openGit(dir).use { git -> return block(git) }
    }

    private fun friendlyError(e: Exception): String {
        return e.message ?: e.javaClass.simpleName
    }

    /** 执行 Git 操作，异常统一转换为带信息的 RuntimeException */
    private fun <T> runGit(block: () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            throw RuntimeException(friendlyError(e), e)
        }
    }

    fun credentials(username: String?, password: String?): CredentialsProvider? {
        if (username.isNullOrBlank()) return null
        return UsernamePasswordCredentialsProvider(username, password ?: "")
    }

    // ==================== 初始化 / 克隆 ====================

    fun init(dir: File): Unit = runGit {
        Git.init().setDirectory(dir).call().use { }
    }

    fun cloneRepo(
        url: String,
        dir: File,
        username: String? = null,
        password: String? = null,
        monitor: ProgressMonitor? = null,
    ): Unit = runGit {
        val cmd = Git.cloneRepository()
            .setURI(url)
            .setDirectory(dir)
        credentials(username, password)?.let { cmd.setCredentialsProvider(it) }
        monitor?.let { cmd.setProgressMonitor(it) }
        cmd.call().use { }
    }

    // ==================== 状态 ====================

    fun status(dir: File): GitStatusResult = runGit {
        withGit(dir) { git ->
            val st = git.status().call()
            val map = HashMap<String, GitFileState>()
            st.added.forEach { map[it] = GitFileState.ADDED }
            st.changed.forEach { map[it] = GitFileState.CHANGED }
            st.removed.forEach { map[it] = GitFileState.DELETED }
            st.modified.forEach { map[it] = GitFileState.MODIFIED }
            st.missing.forEach { map[it] = GitFileState.MISSING }
            st.untracked.forEach { map[it] = GitFileState.UNTRACKED }
            st.conflicting.forEach { map[it] = GitFileState.CONFLICT }
            val summary = GitStatusSummary(
                untracked = st.untracked.size,
                modified = st.modified.size + st.missing.size,
                staged = st.added.size + st.changed.size + st.removed.size,
                conflicts = st.conflicting.size,
            )
            GitStatusResult(map, summary)
        }
    }

    /** 将相对路径状态映射为绝对路径映射（供文件树使用） */
    fun statusByAbsolutePath(dir: File): Map<String, GitFileState> {
        val repoDir = findRepositoryDir(dir) ?: return emptyMap()
        return status(repoDir).fileStates.mapKeys { File(repoDir, it.key).path }
    }

    // ==================== 暂存 ====================

    fun stage(dir: File, vararg paths: String): Unit = runGit {
        withGit(dir) { git ->
            val cmd = git.add()
            paths.forEach { cmd.addFilepattern(it) }
            cmd.call()
        }
    }

    /** 暂存删除的文件 */
    fun stageDeleted(dir: File, vararg paths: String): Unit = runGit {
        withGit(dir) { git ->
            val cmd = git.add().setUpdate(true)
            paths.forEach { cmd.addFilepattern(it) }
            cmd.call()
        }
    }

    fun stageAll(dir: File): Unit = runGit {
        withGit(dir) { git ->
            git.add().addFilepattern(".").call()
            git.add().setUpdate(true).addFilepattern(".").call()
        }
    }

    fun unstage(dir: File, vararg paths: String): Unit = runGit {
        withGit(dir) { git ->
            val cmd = git.reset()
            paths.forEach { cmd.addPath(it) }
            cmd.call()
        }
    }

    fun unstageAll(dir: File): Unit = runGit {
        withGit(dir) { git -> git.reset().call() }
    }

    /** 放弃工作区更改（恢复文件） */
    fun discard(dir: File, vararg paths: String): Unit = runGit {
        withGit(dir) { git ->
            val cmd = git.checkout()
            paths.forEach { cmd.addPath(it) }
            cmd.call()
        }
    }

    // ==================== 提交 ====================

    fun commit(
        dir: File,
        message: String,
        authorName: String? = null,
        authorEmail: String? = null,
        amend: Boolean = false,
        allowEmpty: Boolean = false,
    ): GitCommitInfo = runGit {
        withGit(dir) { git ->
            val cmd = git.commit()
                .setMessage(message)
                .setAmend(amend)
                .setAllowEmpty(allowEmpty)
            val name = authorName?.takeIf { it.isNotBlank() }
                ?: git.repository.config.getString("user", null, "name") ?: "user"
            val email = authorEmail?.takeIf { it.isNotBlank() }
                ?: git.repository.config.getString("user", null, "email") ?: "user@localhost"
            val ident = PersonIdent(name, email)
            cmd.setAuthor(ident).setCommitter(ident)
            toCommitInfo(cmd.call())
        }
    }

    fun headInfo(dir: File): GitCommitInfo? = runGit {
        withGit(dir) { git ->
            val head: ObjectId = git.repository.resolve(Constants.HEAD) ?: return@withGit null
            RevWalk(git.repository).use { rw ->
                toCommitInfo(rw.parseCommit(head))
            }
        }
    }

    fun currentBranch(dir: File): String = runGit {
        withGit(dir) { git -> git.repository.branch }
    }

    // ==================== 历史 ====================

    fun log(dir: File, path: String? = null, maxCount: Int = 200): List<GitCommitInfo> = runGit {
        withGit(dir) { git ->
            val cmd = git.log().setMaxCount(maxCount)
            if (!path.isNullOrBlank()) cmd.addPath(path)
            try {
                cmd.call().map { toCommitInfo(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // ==================== 分支 ====================

    fun branches(dir: File): List<GitBranchInfo> = runGit {
        withGit(dir) { git ->
            val current = try { git.repository.fullBranch } catch (e: Exception) { null }
            git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call().map { ref ->
                val name = ref.name
                val isRemote = name.startsWith(Constants.R_REMOTES)
                GitBranchInfo(
                    name = name,
                    isRemote = isRemote,
                    isCurrent = name == current,
                )
            }
        }
    }

    fun createBranch(dir: File, name: String, startPoint: String? = null, force: Boolean = false): Unit = runGit {
        withGit(dir) { git ->
            val cmd = git.branchCreate().setName(name).setForce(force)
            if (!startPoint.isNullOrBlank()) cmd.setStartPoint(startPoint)
            cmd.call()
        }
    }

    fun checkout(dir: File, name: String): Unit = runGit {
        withGit(dir) { git -> git.checkout().setName(name).call() }
    }

    fun checkoutNewBranch(dir: File, name: String, startPoint: String? = null): Unit = runGit {
        withGit(dir) { git ->
            val cmd = git.checkout().setCreateBranch(true).setName(name)
            if (!startPoint.isNullOrBlank()) cmd.setStartPoint(startPoint)
            cmd.call()
        }
    }

    fun deleteBranch(dir: File, name: String, force: Boolean = false): Unit = runGit {
        withGit(dir) { git -> git.branchDelete().setBranchNames(name).setForce(force).call() }
    }

    fun renameBranch(dir: File, oldName: String, newName: String): Unit = runGit {
        withGit(dir) { git -> git.branchRename().setOldName(oldName).setNewName(newName).call() }
    }

    // ==================== 标签 ====================

    fun tags(dir: File): List<String> = runGit {
        withGit(dir) { git ->
            git.tagList().call().map { Repository.shortenRefName(it.name) }
        }
    }

    fun createTag(dir: File, name: String, message: String? = null, ref: String? = null): Unit = runGit {
        withGit(dir) { git ->
            val cmd = git.tag().setName(name)
            if (!message.isNullOrBlank()) cmd.setMessage(message)
            if (!ref.isNullOrBlank()) {
                val obj = git.repository.resolve(ref)
                RevWalk(git.repository).use { rw -> cmd.setObjectId(rw.parseAny(obj)) }
            }
            cmd.call()
        }
    }

    fun deleteTag(dir: File, name: String): Unit = runGit {
        withGit(dir) { git -> git.tagDelete().setTags(name).call() }
    }

    // ==================== 贮藏 (stash) ====================

    fun stashList(dir: File): List<Pair<String, String>> = runGit {
        withGit(dir) { git ->
            git.stashList().call().mapIndexed { index, commit ->
                "stash@{$index}" to (commit.shortMessage ?: "")
            }
        }
    }

    fun stashCreate(dir: File, message: String? = null): Unit = runGit {
        withGit(dir) { git ->
            val cmd = git.stashCreate()
            if (!message.isNullOrBlank()) cmd.setWorkingDirectoryMessage(message)
            cmd.call()
        }
    }

    fun stashApply(dir: File, stashRef: String): Unit = runGit {
        withGit(dir) { git -> git.stashApply().setStashRef(stashRef).call() }
    }

    fun stashDrop(dir: File, index: Int): Unit = runGit {
        withGit(dir) { git -> git.stashDrop().setStashRef(index).call() }
    }

    // ==================== 重置 / 清理 ====================

    enum class ResetMode(val type: ResetCommand.ResetType) {
        SOFT(ResetCommand.ResetType.SOFT),
        MIXED(ResetCommand.ResetType.MIXED),
        HARD(ResetCommand.ResetType.HARD),
    }

    fun reset(dir: File, mode: ResetMode, ref: String = Constants.HEAD): Unit = runGit {
        withGit(dir) { git -> git.reset().setMode(mode.type).setRef(ref).call() }
    }

    /** 清理未跟踪文件，返回被清理的相对路径 */
    fun clean(dir: File, dryRun: Boolean = false): Set<String> = runGit {
        withGit(dir) { git ->
            git.clean()
                .setCleanDirectories(true)
                .setDryRun(dryRun)
                .call()
        }
    }

    // ==================== 远程 ====================

    fun remotes(dir: File): List<GitRemoteInfo> = runGit {
        withGit(dir) { git ->
            git.remoteList().call().map { rc: RemoteConfig ->
                GitRemoteInfo(rc.name, rc.urIs.firstOrNull()?.toString() ?: "")
            }
        }
    }

    fun addRemote(dir: File, name: String, url: String): Unit = runGit {
        withGit(dir) { git -> git.remoteAdd().setName(name).setUri(URIish(url)).call() }
    }

    fun removeRemote(dir: File, name: String): Unit = runGit {
        withGit(dir) { git -> git.remoteRemove().setRemoteName(name).call() }
    }

    fun updateRemoteUrl(dir: File, name: String, url: String): Unit = runGit {
        withGit(dir) { git -> git.remoteSetUrl().setRemoteName(name).setRemoteUri(URIish(url)).call() }
    }

    fun fetch(
        dir: File,
        remote: String = "origin",
        username: String? = null,
        password: String? = null,
        monitor: ProgressMonitor? = null,
    ): String = runGit {
        withGit(dir) { git ->
            val cmd = git.fetch().setRemote(remote).setRemoveDeletedRefs(true)
            credentials(username, password)?.let { cmd.setCredentialsProvider(it) }
            monitor?.let { cmd.setProgressMonitor(it) }
            val result = cmd.call()
            if (result.trackingRefUpdates.isEmpty()) "fetch: no new refs"
            else result.trackingRefUpdates.joinToString("\n") {
                "${it.localName}: ${it.oldObjectId.name.take(7)} -> ${it.newObjectId.name.take(7)}"
            }
        }
    }

    fun pull(
        dir: File,
        remote: String = "origin",
        branch: String? = null,
        username: String? = null,
        password: String? = null,
        monitor: ProgressMonitor? = null,
    ): String = runGit {
        withGit(dir) { git ->
            val cmd = git.pull().setRemote(remote)
            if (!branch.isNullOrBlank()) cmd.setRemoteBranchName(branch)
            credentials(username, password)?.let { cmd.setCredentialsProvider(it) }
            monitor?.let { cmd.setProgressMonitor(it) }
            val result = cmd.call()
            val mergeStatus = result.mergeResult?.mergeStatus?.toString() ?: ""
            "pull: ${if (result.isSuccessful) "success" else "failed"} $mergeStatus"
        }
    }

    fun push(
        dir: File,
        remote: String = "origin",
        username: String? = null,
        password: String? = null,
        force: Boolean = false,
        monitor: ProgressMonitor? = null,
    ): List<GitPushUpdate> = runGit {
        withGit(dir) { git ->
            val cmd = git.push().setRemote(remote).setForce(force)
            credentials(username, password)?.let { cmd.setCredentialsProvider(it) }
            monitor?.let { cmd.setProgressMonitor(it) }
            val updates = mutableListOf<GitPushUpdate>()
            for (pushResult: PushResult in cmd.call()) {
                for (u: RemoteRefUpdate in pushResult.remoteUpdates) {
                    updates.add(
                        GitPushUpdate(
                            ref = u.remoteName,
                            status = u.status.name,
                            message = u.message?.takeIf { it.isNotBlank() },
                        )
                    )
                }
            }
            updates
        }
    }

    // ==================== 配置 ====================

    fun getUserConfig(dir: File): Pair<String, String> = runGit {
        withGit(dir) { git ->
            val cfg = git.repository.config
            (cfg.getString("user", null, "name") ?: "") to
                    (cfg.getString("user", null, "email") ?: "")
        }
    }

    fun setUserConfig(dir: File, name: String, email: String): Unit = runGit {
        withGit(dir) { git ->
            val cfg = git.repository.config
            cfg.setString("user", null, "name", name)
            cfg.setString("user", null, "email", email)
            cfg.save()
        }
    }

    // ==================== Diff / Blame ====================

    /** 单个工作区文件相对 HEAD 的 unified diff 文本 */
    fun diffFile(dir: File, relPath: String): String = runGit {
        withGit(dir) { git ->
            val repo = git.repository
            val workFile = File(repo.workTree, relPath)
            val newText = if (workFile.exists()) workFile.readText() else ""
            var oldText = ""
            val head = repo.resolve(Constants.HEAD)
            if (head != null) {
                RevWalk(repo).use { rw ->
                    val commit = rw.parseCommit(head)
                    TreeWalk.forPath(repo, relPath, commit.tree)?.let { tw ->
                        oldText = String(repo.open(tw.getObjectId(0)).bytes)
                    }
                }
            }
            val edits = HistogramDiff().diff(RawTextComparator.DEFAULT, RawText(oldText.toByteArray()), RawText(newText.toByteArray()))
            val out = ByteArrayOutputStream()
            val formatter = DiffFormatter(out)
            formatter.setRepository(repo)
            formatter.format(edits, RawText(oldText.toByteArray()), RawText(newText.toByteArray()))
            formatter.flush()
            out.toString("UTF-8")
        }
    }

    data class BlameLine(
        val line: Int,
        val shortId: String,
        val author: String,
        val time: Long,
        val text: String,
    )

    fun blame(dir: File, relPath: String): List<BlameLine> = runGit {
        withGit(dir) { git ->
            val result = git.blame().setFilePath(relPath).call() ?: return@withGit emptyList()
            val lines = mutableListOf<BlameLine>()
            for (i in 0 until result.resultContents.size()) {
                val commit = result.getSourceCommit(i)
                lines.add(
                    BlameLine(
                        line = i + 1,
                        shortId = commit?.name?.take(7) ?: "",
                        author = result.getSourceAuthor(i)?.name ?: "",
                        time = result.getSourceCommitter(i)?.`when`?.time ?: 0L,
                        text = result.resultContents.getString(i),
                    )
                )
            }
            lines
        }
    }

    // ==================== 工具 ====================

    /** 将绝对路径转为仓库相对路径 */
    fun toRelativePath(dir: File, absolutePath: String): String? {
        val repoDir = findRepositoryDir(dir) ?: return null
        val file = File(absolutePath)
        if (!file.path.startsWith(repoDir.path)) return null
        return file.relativeTo(repoDir).path.replace('\\', '/')
    }

    private fun toCommitInfo(commit: RevCommit): GitCommitInfo = GitCommitInfo(
        id = commit.name,
        shortId = commit.name.take(7),
        message = commit.shortMessage ?: "",
        fullMessage = commit.fullMessage ?: "",
        authorName = commit.authorIdent?.name ?: "",
        authorEmail = commit.authorIdent?.emailAddress ?: "",
        commitTime = commit.commitTime * 1000L,
        parentIds = commit.parents.map { it.name },
    )

    /** 相对路径引用解析为 ObjectId（供外部使用） */
    fun resolveRef(dir: File, ref: String): String? = runGit {
        withGit(dir) { git -> git.repository.resolve(ref)?.name }
    }
}
