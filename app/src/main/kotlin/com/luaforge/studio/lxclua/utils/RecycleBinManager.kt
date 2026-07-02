package com.luaforge.studio.lxclua.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.luaforge.studio.lxclua.ui.editor.persistence.EditorStateUtil
import com.luaforge.studio.lxclua.ui.settings.ProjectCover
import com.luaforge.studio.lxclua.ui.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 回收站条目
 * @param trashId 回收站唯一ID = "{originalId}_{deleteTimestamp}"
 * @param originalId 原始项目ID（目录名）
 * @param originalName 原始项目名称
 * @param originalPath 原始完整路径
 * @param originalParentDir 原始父目录路径（用于恢复时判断是否还在原位置）
 * @param trashPath 回收站中路径
 * @param deletedAt 删除时间戳（毫秒）
 * @param sizeBytes 项目大小（字节）
 * @param wasPinned 是否置顶
 * @param wasRecent 是否在最近列表中
 * @param tags 标签集合快照
 * @param coverData 封面JSON序列化字符串
 * @param customOrderIndex 自定义排序位置（-1表示不在排序中）
 * @param categoryId 所属分类ID（null表示无分类）
 */
data class TrashItem(
    val trashId: String,
    val originalId: String,
    val originalName: String,
    val originalPath: String,
    val originalParentDir: String,
    val trashPath: String,
    val deletedAt: Long,
    val sizeBytes: Long,
    val wasPinned: Boolean,
    val wasRecent: Boolean,
    val tags: Set<String>,
    val coverData: String?,
    val customOrderIndex: Int,
    val categoryId: String?
)

/**
 * 回收站管理器
 *
 * 回收站目录: /sdcard/LXC-LUA/trash/
 * 索引文件: /sdcard/LXC-LUA/trash/.trash_index.json
 *
 * 核心设计：
 * - 项目删除时move到trash目录（renameTo，同分区原子操作）
 * - 元数据快照保存在索引JSON中，恢复时原样还原
 * - 启动时O(n)扫描索引清理过期项目（不遍历目录，最快）
 * - 保留天数可配置（默认7天）
 */
object RecycleBinManager {

    private const val TAG = "RecycleBin"
    private const val TRASH_DIR_NAME = "LXC-LUA/trash"
    private const val INDEX_FILE_NAME = ".trash_index.json"

    // 内存缓存
    private val trashItems = mutableListOf<TrashItem>()
    private var initialized = false

    /**
     * 获取回收站根目录
     */
    private fun getTrashDir(): File {
        val baseDir = Environment.getExternalStorageDirectory()
        return File(baseDir, TRASH_DIR_NAME)
    }

    /**
     * 获取索引文件
     */
    private fun getIndexFile(): File {
        return File(getTrashDir(), INDEX_FILE_NAME)
    }

    /**
     * 初始化（从索引文件加载到内存）
     * 启动时调用一次
     */
    suspend fun loadIndex(context: Context) = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        try {
            val indexFile = getIndexFile()
            if (indexFile.exists()) {
                val type = object : TypeToken<List<TrashItem>>() {}.type
                val reader = FileReader(indexFile)
                val items: List<TrashItem>? = Gson().fromJson(reader, type)
                reader.close()
                if (items != null) {
                    trashItems.clear()
                    trashItems.addAll(items)
                }
            }
            initialized = true
            Log.d(TAG, "加载回收站索引完成，共${trashItems.size}个项目")
        } catch (e: Exception) {
            Log.e(TAG, "加载回收站索引失败", e)
            initialized = true
        }
    }

    /**
     * 原子写入索引文件（先写临时文件再rename）
     */
    private fun saveIndex() {
        try {
            val trashDir = getTrashDir()
            if (!trashDir.exists()) trashDir.mkdirs()
            val indexFile = getIndexFile()
            val tempFile = File(trashDir, ".trash_index.tmp")
            val writer = FileWriter(tempFile)
            Gson().toJson(trashItems, writer)
            writer.flush()
            writer.close()
            // 原子替换
            if (indexFile.exists()) indexFile.delete()
            tempFile.renameTo(indexFile)
        } catch (e: Exception) {
            Log.e(TAG, "保存回收站索引失败", e)
        }
    }

    /**
     * 计算目录大小（递归）
     */
    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirSize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    /**
     * 将项目移入回收站
     * @param project 要删除的项目
     * @param context Context
     * @return TrashItem 回收站条目（失败返回null）
     */
    suspend fun moveToTrash(project: com.luaforge.studio.lxclua.ProjectItem, context: Context): TrashItem? =
        withContext(Dispatchers.IO) {
            try {
                if (!initialized) loadIndex(context)

                val projectDir = File(project.path)
                if (!projectDir.exists() || !projectDir.isDirectory) {
                    Log.e(TAG, "项目目录不存在: ${project.path}")
                    return@withContext null
                }

                val trashDir = getTrashDir()
                if (!trashDir.exists()) trashDir.mkdirs()

                // 生成trashId
                val timestamp = System.currentTimeMillis()
                val trashId = "${project.id}_$timestamp"
                val trashProjectDir = File(trashDir, trashId)

                // 如果目标已存在（理论上不可能，因为时间戳唯一），加后缀
                var finalTrashDir = trashProjectDir
                var suffix = 0
                while (finalTrashDir.exists()) {
                    suffix++
                    finalTrashDir = File(trashDir, "${trashId}_$suffix")
                }

                // 移动目录（renameTo原子操作，同分区必成功）
                val moved = projectDir.renameTo(finalTrashDir)
                if (!moved) {
                    // renameTo失败时（跨分区），使用copy+delete
                    Log.w(TAG, "renameTo失败，使用copy+delete: ${project.path} -> ${finalTrashDir.absolutePath}")
                    try {
                        projectDir.copyRecursively(finalTrashDir, overwrite = true)
                        projectDir.deleteRecursively()
                    } catch (e: Exception) {
                        Log.e(TAG, "copy+delete失败", e)
                        return@withContext null
                    }
                }

                // 获取当前设置，保存元数据快照
                val settings = SettingsManager.currentSettings
                val cover = settings.projectCoverMap[project.id]
                val coverJson = if (cover != null) Gson().toJson(cover) else null
                val categoryId = settings.homeCategories
                    .find { project.id in it.projectIds }?.id
                val orderIndex = settings.customProjectOrder.indexOf(project.id)

                val item = TrashItem(
                    trashId = if (suffix > 0) "${trashId}_$suffix" else trashId,
                    originalId = project.id,
                    originalName = project.name,
                    originalPath = project.path,
                    originalParentDir = projectDir.parentFile?.absolutePath
                        ?: File(Environment.getExternalStorageDirectory(), "LXC-LUA/project").absolutePath,
                    trashPath = finalTrashDir.absolutePath,
                    deletedAt = timestamp,
                    sizeBytes = calculateDirSize(finalTrashDir),
                    wasPinned = settings.pinnedProjects.contains(project.id),
                    wasRecent = settings.recentProjects.contains(project.id),
                    tags = settings.projectTagsMap[project.id] ?: emptySet(),
                    coverData = coverJson,
                    customOrderIndex = orderIndex,
                    categoryId = categoryId
                )

                trashItems.add(item)
                saveIndex()
                Log.d(TAG, "项目已移入回收站: ${project.name} -> ${item.trashId}")

                // 清理编辑器状态
                EditorStateUtil.cleanProjectState(context, project.path)

                // 清理SettingsManager中的元数据（项目从列表消失）
                clearProjectMetadata(project.id, context, item)

                return@withContext item
            } catch (e: Exception) {
                Log.e(TAG, "移入回收站失败: ${project.name}", e)
                return@withContext null
            }
        }

    /**
     * 从回收站恢复项目
     * @param trashId 回收站ID
     * @param context Context
     * @return 恢复后的ProjectItem（失败返回null）
     */
    suspend fun restoreFromTrash(trashId: String, context: Context): com.luaforge.studio.lxclua.ProjectItem? =
        withContext(Dispatchers.IO) {
            try {
                if (!initialized) loadIndex(context)

                val item = trashItems.find { it.trashId == trashId }
                if (item == null) {
                    Log.e(TAG, "回收站条目不存在: $trashId")
                    return@withContext null
                }

                val trashDir = File(item.trashPath)
                if (!trashDir.exists()) {
                    Log.e(TAG, "回收站目录不存在: ${item.trashPath}")
                    // 清理索引中的无效条目
                    trashItems.removeAll { it.trashId == trashId }
                    saveIndex()
                    return@withContext null
                }

                // 确定恢复目标路径
                var targetDir = File(item.originalPath)
                var restoredId = item.originalId
                var restoredName = item.originalName
                if (targetDir.exists()) {
                    // 原路径已存在同名项目，添加时间戳后缀避免冲突
                    val timeSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(item.deletedAt))
                    restoredId = "${item.originalId}_恢复_$timeSuffix"
                    restoredName = "${item.originalName}_恢复_$timeSuffix"
                    targetDir = File(item.originalParentDir, restoredId)
                    // 如果还冲突，加序号
                    var counter = 1
                    while (targetDir.exists()) {
                        restoredId = "${item.originalId}_恢复_${timeSuffix}_$counter"
                        restoredName = "${item.originalName}_恢复_${timeSuffix}_$counter"
                        targetDir = File(item.originalParentDir, restoredId)
                        counter++
                    }
                }

                // 确保父目录存在
                val parentDir = File(item.originalParentDir)
                if (!parentDir.exists()) {
                    // 原父目录不存在，回退到默认项目目录
                    val defaultDir = File(Environment.getExternalStorageDirectory(), "LXC-LUA/project")
                    defaultDir.mkdirs()
                    targetDir = File(defaultDir, restoredId)
                }

                // 移动目录回原位置
                val restored = trashDir.renameTo(targetDir)
                if (!restored) {
                    try {
                        trashDir.copyRecursively(targetDir, overwrite = true)
                        trashDir.deleteRecursively()
                    } catch (e: Exception) {
                        Log.e(TAG, "恢复失败（copy+delete）", e)
                        return@withContext null
                    }
                }

                // 恢复元数据到SettingsManager
                restoreProjectMetadata(item, restoredId, restoredName, targetDir.absolutePath, context)

                // 从回收站索引移除
                trashItems.removeAll { it.trashId == trashId }
                saveIndex()

                Log.d(TAG, "项目已从回收站恢复: ${item.originalName} -> ${targetDir.absolutePath}")

                return@withContext com.luaforge.studio.lxclua.ProjectItem(
                    id = restoredId,
                    name = restoredName,
                    path = targetDir.absolutePath,
                    createdDate = Date(),
                    modifiedDate = Date(targetDir.lastModified())
                )
            } catch (e: Exception) {
                Log.e(TAG, "恢复失败: $trashId", e)
                return@withContext null
            }
        }

    /**
     * 永久删除回收站中的项目
     */
    suspend fun permanentlyDelete(trashId: String, context: Context): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (!initialized) loadIndex(context)

                val item = trashItems.find { it.trashId == trashId } ?: return@withContext false
                val trashDir = File(item.trashPath)
                if (trashDir.exists()) {
                    trashDir.deleteRecursively()
                }
                trashItems.removeAll { it.trashId == trashId }
                saveIndex()
                Log.d(TAG, "已永久删除: $trashId")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "永久删除失败: $trashId", e)
                return@withContext false
            }
        }

    /**
     * 清理过期项目
     * @param days 保留天数
     * @return 已清理的trashId列表
     */
    suspend fun clearExpired(days: Int, context: Context): List<String> = withContext(Dispatchers.IO) {
        try {
            if (!initialized) loadIndex(context)

            val now = System.currentTimeMillis()
            val expireTime = days * 24L * 60L * 60L * 1000L
            val expired = trashItems.filter { (now - it.deletedAt) > expireTime }
            val clearedIds = mutableListOf<String>()

            expired.forEach { item ->
                try {
                    val trashDir = File(item.trashPath)
                    if (trashDir.exists()) {
                        trashDir.deleteRecursively()
                    }
                    clearedIds.add(item.trashId)
                    Log.d(TAG, "清理过期项目: ${item.originalName} (删除于${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.deletedAt))})")
                } catch (e: Exception) {
                    Log.e(TAG, "清理过期项目失败: ${item.trashId}", e)
                }
            }

            if (clearedIds.isNotEmpty()) {
                trashItems.removeAll { it.trashId in clearedIds }
                saveIndex()
            }

            return@withContext clearedIds
        } catch (e: Exception) {
            Log.e(TAG, "清理过期项目失败", e)
            return@withContext emptyList()
        }
    }

    /**
     * 清空回收站
     */
    suspend fun clearAll(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            if (!initialized) loadIndex(context)

            var count = 0
            trashItems.forEach { item ->
                try {
                    val trashDir = File(item.trashPath)
                    if (trashDir.exists()) {
                        trashDir.deleteRecursively()
                    }
                    count++
                } catch (e: Exception) {
                    Log.e(TAG, "清空时删除失败: ${item.trashId}", e)
                }
            }
            trashItems.clear()
            saveIndex()
            Log.d(TAG, "回收站已清空，共删除$count 个项目")
            return@withContext count
        } catch (e: Exception) {
            Log.e(TAG, "清空回收站失败", e)
            return@withContext 0
        }
    }

    /**
     * 获取回收站所有项目
     */
    fun getTrashItems(): List<TrashItem> {
        if (!initialized) return emptyList()
        return trashItems.toList().sortedByDescending { it.deletedAt }
    }

    /**
     * 获取指定回收站项目
     */
    fun getTrashItem(trashId: String): TrashItem? {
        return trashItems.find { it.trashId == trashId }
    }

    /**
     * 获取回收站总大小（字节）
     */
    fun getTotalSize(): Long {
        return trashItems.sumOf { it.sizeBytes }
    }

    /**
     * 获取回收站项目数量
     */
    fun getItemCount(): Int = trashItems.size

    /**
     * 清理项目元数据（移入回收站时从设置中移除）
     */
    private fun clearProjectMetadata(projectId: String, context: Context, item: TrashItem) {
        val settings = SettingsManager.currentSettings
        val newSettings = settings.copy(
            pinnedProjects = settings.pinnedProjects - projectId,
            recentProjects = settings.recentProjects - projectId,
            projectTagsMap = settings.projectTagsMap.toMutableMap().apply { remove(projectId) },
            projectCoverMap = settings.projectCoverMap.toMutableMap().apply { remove(projectId) },
            customProjectOrder = settings.customProjectOrder - projectId,
            homeCategories = settings.homeCategories.map { cat ->
                cat.copy(projectIds = cat.projectIds - projectId)
            }
        )
        SettingsManager.updateSettings(newSettings)
        // 异步保存
        GlobalScope.launch(Dispatchers.IO) {
            SettingsManager.saveSettings(context)
        }
    }

    /**
     * 恢复项目元数据（从回收站恢复时还原到设置中）
     */
    private fun restoreProjectMetadata(
        item: TrashItem,
        restoredId: String,
        restoredName: String,
        restoredPath: String,
        context: Context
    ) {
        val settings = SettingsManager.currentSettings
        val newPinned = if (item.wasPinned) settings.pinnedProjects + restoredId else settings.pinnedProjects
        val newRecent = if (item.wasRecent) {
            (listOf(restoredId) + settings.recentProjects).distinct().take(5)
        } else settings.recentProjects
        val newTagsMap = if (item.tags.isNotEmpty()) {
            settings.projectTagsMap.toMutableMap().apply { put(restoredId, item.tags) }
        } else settings.projectTagsMap
        val newCoverMap = if (!item.coverData.isNullOrEmpty()) {
            try {
                val cover: ProjectCover = Gson().fromJson(item.coverData, ProjectCover::class.java)
                // 如果封面是图片路径，需要更新路径
                val updatedCover = if (cover.type == com.luaforge.studio.lxclua.ui.settings.CoverType.IMAGE) {
                    val oldImgName = File(cover.imagePath).name
                    val newImgFile = File(restoredPath, oldImgName)
                    if (newImgFile.exists()) {
                        cover.copy(imagePath = newImgFile.absolutePath)
                    } else cover
                } else cover
                settings.projectCoverMap.toMutableMap().apply { put(restoredId, updatedCover) }
            } catch (e: Exception) {
                settings.projectCoverMap
            }
        } else settings.projectCoverMap
        val newOrder = if (item.customOrderIndex >= 0) {
            val list = settings.customProjectOrder.toMutableList()
            if (item.customOrderIndex < list.size) list.add(item.customOrderIndex, restoredId)
            else list.add(restoredId)
            list
        } else settings.customProjectOrder
        val newCategories = if (item.categoryId != null) {
            settings.homeCategories.map { cat ->
                if (cat.id == item.categoryId) {
                    cat.copy(projectIds = cat.projectIds + restoredId)
                } else cat
            }
        } else settings.homeCategories

        val newSettings = settings.copy(
            pinnedProjects = newPinned,
            recentProjects = newRecent,
            projectTagsMap = newTagsMap,
            projectCoverMap = newCoverMap,
            customProjectOrder = newOrder,
            homeCategories = newCategories
        )
        SettingsManager.updateSettings(newSettings)
        GlobalScope.launch(Dispatchers.IO) {
            SettingsManager.saveSettings(context)
        }
    }

    /**
     * 格式化文件大小为人类可读字符串
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * 格式化删除时间为"X天前/X小时前/X分钟前"
     */
    fun formatTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}天前"
            hours > 0 -> "${hours}小时前"
            minutes > 0 -> "${minutes}分钟前"
            else -> "刚刚"
        }
    }
}
