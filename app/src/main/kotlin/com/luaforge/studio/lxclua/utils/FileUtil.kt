package com.luaforge.studio.lxclua.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object FileUtil {

    /**
     * 获取项目存储路径
     */
    fun getProjectsPath(context: Context): String {
        val settings = com.luaforge.studio.lxclua.ui.settings.SettingsManager.currentSettings
        return settings.projectStoragePath.ifBlank {
            val externalDir = context.getExternalFilesDir(null)
            externalDir?.let { File(it, "projects").absolutePath } ?: ""
        }
    }

    /**
     * 获取所有项目目录路径（主目录 + 附加目录）
     */
    fun getAllProjectPaths(context: Context): List<String> {
        val settings = com.luaforge.studio.lxclua.ui.settings.SettingsManager.currentSettings
        val paths = mutableListOf<String>()
        val primary = getProjectsPath(context)
        if (primary.isNotBlank()) {
            paths.add(primary)
        }
        paths.addAll(settings.additionalProjectPaths.filter { it.isNotBlank() })
        return paths.distinct()
    }

    /**
     * 获取项目目录
     */
    fun getProjectsDirectory(context: Context): File {
        val settings = com.luaforge.studio.lxclua.ui.settings.SettingsManager.currentSettings
        return if (settings.projectStoragePath.isNotBlank()) {
            File(settings.projectStoragePath)
        } else {
            val externalDir = context.getExternalFilesDir(null)
            File(externalDir ?: context.filesDir, "projects").apply {
                mkdirs()
            }
        }
    }

    /**
     * 检查文件是否存在
     */
    fun fileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }

    /**
     * 创建目录（如果不存在）
     */
    fun createDirectoryIfNotExists(directoryPath: String): Boolean {
        val dir = File(directoryPath)
        return if (!dir.exists()) {
            dir.mkdirs()
        } else {
            true
        }
    }

    /**
     * 删除文件或目录
     */
    fun deleteFileOrDirectory(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } else {
            false
        }
    }

    /**
     * 获取文件大小（字节）
     */
    fun getFileSize(filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) {
            if (file.isDirectory) {
                file.walk().filter { it.isFile }.sumOf { it.length() }
            } else {
                file.length()
            }
        } else {
            0L
        }
    }

    /**
     * 获取文件扩展名
     */
    fun getFileExtension(fileName: String): String {
        return if (fileName.contains(".")) {
            fileName.substringAfterLast(".", "")
        } else {
            ""
        }
    }

    /**
     * 获取不带扩展名的文件名
     */
    fun getFileNameWithoutExtension(fileName: String): String {
        return if (fileName.contains(".")) {
            fileName.substringBeforeLast(".")
        } else {
            fileName
        }
    }

    // ========== ZIP 操作函数 ==========

    /**
     * 读取 ZIP 文件中指定条目（文件）的内容为字符串
     * @param zipFile ZIP 文件
     * @param entryName 条目名称（例如 "settings.json"）
     * @return 文件内容，如果条目不存在或读取失败则返回 null
     */
    fun readFileFromZip(zipFile: File, entryName: String): String? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(entryName) ?: return null
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 解压 ZIP 文件到目标目录
     * @param zipFile ZIP 文件
     * @param destinationDir 目标目录（如果不存在会自动创建）
     * @return true 表示成功，false 表示失败
     */
    fun extractZip(zipFile: File, destinationDir: File): Boolean {
        return try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    // 跳过无效条目（entry.name 为 null 或空会导致创建 null 文件夹）
                    if (entry.name.isNullOrBlank()) {
                        return@forEach
                    }
                    val targetFile = File(destinationDir, entry.name)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 将目录打包为 ZIP 文件
     * @param sourceDir 源目录
     * @param destZipFile 目标 ZIP 文件
     * @param excludeFilter 排除过滤器，返回 true 表示跳过该文件/目录。默认为空（不排除任何文件）。
     *                      对于目录，会跳过该目录及其所有子内容。
     * @return true 表示成功
     */
    fun createZip(sourceDir: File, destZipFile: File, excludeFilter: (File) -> Boolean = { false }): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(destZipFile)).use { zos ->
                sourceDir.walkTopDown().onEnter { dir ->
                    dir == sourceDir || !excludeFilter(dir)
                }.forEach { file ->
                    if (file == sourceDir) return@forEach
                    val relativePath = file.relativeTo(sourceDir).path.replace("\\", "/")
                    if (file.isDirectory) {
                        zos.putNextEntry(ZipEntry("$relativePath/"))
                        zos.closeEntry()
                    } else {
                        if (excludeFilter(file)) return@forEach
                        zos.putNextEntry(ZipEntry(relativePath))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}