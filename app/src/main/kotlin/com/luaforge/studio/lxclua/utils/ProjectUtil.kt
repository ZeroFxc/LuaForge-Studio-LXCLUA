package com.luaforge.studio.lxclua.utils

import android.content.Context
import android.net.Uri
import com.luaforge.studio.lxclua.ProjectItem
import com.luaforge.studio.lxclua.ui.project.TemplateItem
import com.luaforge.studio.lxclua.ui.settings.SettingsManager
import com.luaforge.studio.lxclua.ui.settings.TemplateType
import com.luaforge.studio.lxclua.ui.settings.ProjectTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.UUID
import java.util.zip.ZipFile

object ProjectUtil {

    /**
     * 缩短文件路径显示，将常见外部存储前缀替换为 /sdcard/
     * 例如 /storage/emulated/0/xxx -> /sdcard/xxx
     *      /storage/self/primary/xxx -> /sdcard/xxx
     * @param path 原始文件路径
     * @return 缩短后的路径
     */
    fun shortenPath(path: String): String {
        if (path.isEmpty()) return path
        // 按优先级依次替换常见前缀
        val prefixes = listOf(
            "/storage/emulated/0/",
            "/storage/self/primary/",
            "/sdcard/"
        )
        for (prefix in prefixes) {
            if (path.startsWith(prefix, ignoreCase = true)) {
                return "/sdcard/" + path.removePrefix(prefix).removePrefix("/")
            }
        }
        // /storage/emulated/legacy 等变体
        if (path.startsWith("/storage/emulated/", ignoreCase = true)) {
            val afterStorage = path.removePrefix("/storage/emulated/")
            val slashIdx = afterStorage.indexOf('/')
            if (slashIdx >= 0) {
                return "/sdcard/" + afterStorage.substring(slashIdx + 1)
            }
        }
        return path
    }

    /**
     * 从目录加载项目
     */
    suspend fun loadProjectsFromDirectory(
        projectsPath: String,
        onProjectItemsChanged: (List<ProjectItem>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            if (projectsPath.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onProjectItemsChanged(emptyList())
                }
                return@withContext
            }

            val projectsDir = File(projectsPath)

            if (!projectsDir.exists() || !projectsDir.isDirectory) {
                // 尝试创建目录
                projectsDir.mkdirs()
                withContext(Dispatchers.Main) {
                    onProjectItemsChanged(emptyList())
                }
                return@withContext
            }

            val projectList = mutableListOf<ProjectItem>()

            projectsDir.listFiles()?.forEach { projectDir ->
                if (projectDir.isDirectory) {
                    val projectItem = ProjectItem(
                        id = projectDir.name,
                        name = projectDir.name,
                        path = projectDir.absolutePath,
                        createdDate = Date(projectDir.lastModified()),
                        modifiedDate = Date(projectDir.lastModified())
                    )
                    projectList.add(projectItem)
                }
            }

            // 按修改时间排序，最新的在前面
            projectList.sortByDescending { it.modifiedDate }

            withContext(Dispatchers.Main) {
                onProjectItemsChanged(projectList)
            }
        }
    }

    /**
     * 从多个目录加载项目
     */
    suspend fun loadProjectsFromDirectories(
        paths: List<String>,
        onProjectItemsChanged: (List<ProjectItem>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            if (paths.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onProjectItemsChanged(emptyList())
                }
                return@withContext
            }

            val projectList = mutableListOf<ProjectItem>()
            val seenPaths = mutableSetOf<String>()

            for (projectsPath in paths) {
                if (projectsPath.isBlank()) continue
                val projectsDir = File(projectsPath)

                if (!projectsDir.exists() || !projectsDir.isDirectory) {
                    projectsDir.mkdirs()
                    continue
                }

                projectsDir.listFiles()?.forEach { projectDir ->
                    if (projectDir.isDirectory && seenPaths.add(projectDir.absolutePath)) {
                        val projectItem = ProjectItem(
                            id = projectDir.name,
                            name = projectDir.name,
                            path = projectDir.absolutePath,
                            createdDate = Date(projectDir.lastModified()),
                            modifiedDate = Date(projectDir.lastModified())
                        )
                        projectList.add(projectItem)
                    }
                }
            }

            projectList.sortByDescending { it.modifiedDate }

            withContext(Dispatchers.Main) {
                onProjectItemsChanged(projectList)
            }
        }
    }

    /**
     * 生成默认项目名称
     */
    fun generateDefaultProjectName(projectsDir: File): String {
        val baseName = "My Application"
        var counter = 1

        while (true) {
            val projectName = "$baseName$counter"
            val projectDir = File(projectsDir, projectName)
            if (!projectDir.exists()) {
                return projectName
            }
            counter++
        }
    }

    /**
     * 生成包名（汉译英转小写）
     */
    fun generatePackageName(projectName: String): String {
        // 简单实现：将非字母数字字符替换为点，转为小写
        val cleanedName = projectName
            .replace("[^a-zA-Z0-9]".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), ".")
            .lowercase()

        return if (cleanedName.isNotEmpty()) {
            "mylxcluaapp.$cleanedName"
        } else {
            "mylxcluaapp.myapplication"
        }
    }

    /**
     * 验证包名格式
     */
    fun isValidPackageName(packageName: String): Boolean {
        return packageName.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$".toRegex())
    }

    /**
     * 加载模板列表（包括预设模板和用户自定义模板）
     */
    suspend fun loadTemplates(
        context: Context,
        onLoaded: (List<TemplateItem>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val templates = mutableListOf<TemplateItem>()

                // 列出 assets/templates 目录下的 zip 文件（预设模板）
                val assetManager = context.assets
                val templateFiles = assetManager.list("templates") ?: emptyArray()

                templateFiles.forEach { fileName ->
                    if (fileName.endsWith(".zip")) {
                        val templateName = fileName
                            .removeSuffix(".zip")
                            .replace("_", " ")
                            .replace("-", " ")
                            .split(" ")
                            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

                        // 尝试提取预览图片
                        val previewUri = extractPreviewImage(context, fileName)

                        // 预设模板的描述
                        val description = when {
                            fileName.equals("Default.zip", ignoreCase = true) -> "空白项目模板，包含基础项目结构"
                            fileName.contains("lua", ignoreCase = true) -> "Lua脚本项目模板"
                            else -> "预设模板"
                        }

                        templates.add(
                            TemplateItem(
                                name = templateName,
                                zipFileName = fileName,
                                previewUri = previewUri,
                                description = description
                            )
                        )
                    }
                }

                // 加载用户自定义模板（从设置中获取元数据，并结合文件系统）
                val userTemplateItems = loadUserTemplateItems(context)
                templates.addAll(userTemplateItems)

                // 按名称排序，但把Default.zip放在第一个
                templates.sortWith(
                    compareBy(
                        { it.isUserTemplate }, // 预设模板排在前面
                        { !it.zipFileName.equals("Default.zip", ignoreCase = true) },
                        { it.name }
                    ))

                withContext(Dispatchers.Main) {
                    onLoaded(templates)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onLoaded(emptyList())
                }
            }
        }
    }

    /**
     * 提取模板预览图片
     */
    private fun extractPreviewImage(
        context: Context,
        zipFileName: String
    ): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "template_previews")
            cacheDir.mkdirs()

            val previewFile = File(cacheDir, "$zipFileName.preview.png")

            // 如果缓存文件已存在，直接返回
            if (previewFile.exists()) {
                return Uri.fromFile(previewFile)
            }

            // 从 assets 读取 zip 文件
            val assetManager = context.assets
            assetManager.open("templates/$zipFileName").use { inputStream ->
                // 将 zip 文件复制到临时文件
                val tempZipFile = File(cacheDir, "$zipFileName.temp")
                FileOutputStream(tempZipFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                // 从 zip 文件中提取 Preview.png
                ZipFile(tempZipFile).use { zipFile ->
                    val previewEntry = zipFile.entries().toList().find {
                        it.name.equals("Preview.png", ignoreCase = true)
                    }

                    previewEntry?.let { entry ->
                        zipFile.getInputStream(entry).use { zipInputStream ->
                            FileOutputStream(previewFile).use { fileOutputStream ->
                                zipInputStream.copyTo(fileOutputStream)
                            }
                        }

                        // 清理临时文件
                        tempZipFile.delete()

                        return Uri.fromFile(previewFile)
                    }
                }

                // 清理临时文件
                tempZipFile.delete()
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从缓存加载模板预览（支持预设模板和用户模板）
     */
    fun loadTemplatePreview(
        context: Context,
        template: TemplateItem
    ): Any? {
        val cacheDir = File(context.cacheDir, "template_previews")
        val previewFile = File(cacheDir, "${template.zipFileName}.preview.png")

        return if (previewFile.exists()) {
            Uri.fromFile(previewFile)
        } else {
            null
        }
    }

    /**
     * 解压模板文件（支持预设模板和用户模板）
     */
    fun extractTemplate(
        context: Context,
        template: TemplateItem,
        projectDir: File,
        projectName: String,
        packageName: String,
        debugMode: Boolean
    ) {
        val cacheDir = File(context.cacheDir, "template_extract")
        cacheDir.mkdirs()

        // 获取模板zip文件：用户模板从文件系统读取，预设模板从assets复制到临时文件
        val zipFile: File = if (template.isUserTemplate && template.filePath != null) {
            File(template.filePath)
        } else {
            // 从 assets 读取 zip 文件到临时文件
            val assetManager = context.assets
            val tempZipFile = File(cacheDir, "${template.zipFileName}.temp")
            assetManager.open("templates/${template.zipFileName}").use { inputStream ->
                FileOutputStream(tempZipFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            tempZipFile
        }

        // 解压 zip 文件
        ZipFile(zipFile).use { zip ->
            for (entry in zip.entries()) {
                // 跳过 Preview.png 文件
                if (entry.name.equals("Preview.png", ignoreCase = true)) {
                    continue
                }

                val entryFile = File(projectDir, entry.name)

                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    // 确保父目录存在
                    entryFile.parentFile?.mkdirs()

                    // 写入文件
                    zip.getInputStream(entry).use { zipInputStream ->
                        FileOutputStream(entryFile).use { fileOutputStream ->
                            zipInputStream.copyTo(fileOutputStream)
                        }
                    }

                    // 如果是 settings.json 或 main.lua，需要替换内容
                    when {
                        entry.name.endsWith("settings.json") -> {
                            updateSettingsFile(entryFile, projectName, packageName, debugMode)
                        }

                        entry.name.endsWith("main.lua") -> {
                            updateMainLuaFile(entryFile, projectName)
                        }
                    }
                }
            }
        }

        // 清理临时文件（仅清理assets临时文件）
        if (!template.isUserTemplate || template.filePath == null) {
            zipFile.delete()
        }

        // 清理缓存目录
        cacheDir.deleteRecursively()
    }

    /**
     * 更新 main.lua 文件
     */
    fun updateMainLuaFile(mainLuaFile: File, projectName: String) {
        try {
            val content = mainLuaFile.readText()
            val updatedContent = content.replace("AppName", projectName)
            mainLuaFile.writeText(updatedContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 更新 settings.json 文件
     */
    fun updateSettingsFile(
        settingsFile: File,
        projectName: String,
        packageName: String,
        debugMode: Boolean
    ) {
        try {
            val jsonString = settingsFile.readText()
            val jsonMap = JsonUtil.parseObject(jsonString) as MutableMap<String, Any?>

            // 更新包名
            jsonMap["package"] = packageName

            // 更新应用信息
            val applicationMap =
                (jsonMap["application"] as? MutableMap<String, Any>) ?: mutableMapOf()
            applicationMap["label"] = projectName
            applicationMap["debugmode"] = debugMode
            jsonMap["application"] = applicationMap

            // 写回文件 - 使用格式化输出（缩进4个空格）
            val updatedJson = JSONObject(jsonMap).toString(4)
            settingsFile.writeText(updatedJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 更新 settings.json 文件（带全局Utils）
     */
    fun updateSettingsFile(
        settingsFile: File,
        projectName: String,
        packageName: String,
        debugMode: Boolean,
        globalUtils: List<String>
    ) {
        try {
            val jsonString = settingsFile.readText()
            val jsonMap = JsonUtil.parseObject(jsonString) as MutableMap<String, Any?>

            // 更新包名
            jsonMap["package"] = packageName

            // 更新应用信息
            val applicationMap =
                (jsonMap["application"] as? MutableMap<String, Any>) ?: mutableMapOf()
            applicationMap["label"] = projectName
            applicationMap["debugmode"] = debugMode
            jsonMap["application"] = applicationMap

            // 更新全局Utils
            if (globalUtils.isNotEmpty()) {
                jsonMap["global_utils"] = globalUtils
            } else {
                // 如果没有选择任何全局Utils，则设置为空数组
                jsonMap["global_utils"] = emptyList<String>()
            }

            // 写回文件 - 使用格式化输出（缩进4个空格）
            val updatedJson = JSONObject(jsonMap).toString(4)
            settingsFile.writeText(updatedJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 保存 settings.json 文件（基础版本）
     */
    fun saveSettingsFile(
        projectDir: File,
        projectName: String,
        packageName: String,
        debugMode: Boolean
    ) {
        saveSettingsFile(projectDir, projectName, packageName, debugMode, emptyList())
    }

    /**
     * 保存 settings.json 文件（带全局Utils版本）
     */
    fun saveSettingsFile(
        projectDir: File,
        projectName: String,
        packageName: String,
        debugMode: Boolean,
        globalUtils: List<String>
    ) {
        val settingsFile = File(projectDir, "settings.json")

        // 如果已经存在（从模板复制），则更新它
        if (settingsFile.exists()) {
            updateSettingsFile(settingsFile, projectName, packageName, debugMode, globalUtils)
            return
        }

        // 创建默认的 settings.json - 使用明确的类型转换
        val settings = mutableMapOf<String, Any?>(
            "versionName" to "1.0",
            "versionCode" to "1",
            "uses_sdk" to mapOf(
                "minSdkVersion" to "21",
                "targetSdkVersion" to "29"
            ),
            "package" to packageName,
            "application" to mapOf(
                "label" to projectName,
                "debugmode" to debugMode
            ),
            "user_permission" to listOf(
                "WRITE_EXTERNAL_STORAGE",
                "READ_EXTERNAL_STORAGE",
                "INTERNET"
            ),
            "implementation" to emptyList<String>(),
            "global_utils" to globalUtils  // 使用传入的globalUtils
        )

        try {
            // 使用格式化输出（缩进4个空格）
            val jsonString = JSONObject(settings as Map<*, *>).toString(4)
            settingsFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 创建默认的 main.lua 文件（备用）
     */
    fun createDefaultMainLuaFile(
        projectDir: File,
        projectName: String
    ) {
        val mainLuaFile = File(projectDir, "main.lua")

        // 如果已经存在（从模板复制），则跳过
        if (mainLuaFile.exists()) {
            return
        }

        val content = """
            require "import"
            import "android.app.*"
            import "android.os.*"
            import "android.widget.*"
            import "android.view.*"
            import "androidx.appcompat.widget.LinearLayoutCompat"

            activity
            .setTheme(R.style.Theme_Material3_Blue)
            .setTitle("$projectName")
            .setContentView(loadlayout("layout"))
        """.trimIndent()

        try {
            mainLuaFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 复制图标到项目
     */
    fun copyIconToProject(
        context: Context,
        iconUri: Uri,
        projectDir: File
    ) {
        try {
            val iconFile = File(projectDir, "icon.png")

            context.contentResolver.openInputStream(iconUri)?.use { inputStream ->
                FileOutputStream(iconFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取项目信息
     */
    suspend fun getProjectInfo(projectPath: String): Map<String, Any?> {
        return withContext(Dispatchers.IO) {
            val projectDir = File(projectPath)
            val info = mutableMapOf<String, Any?>()

            if (projectDir.exists() && projectDir.isDirectory) {
                // 基本信息
                info["name"] = projectDir.name
                info["path"] = projectDir.absolutePath
                info["lastModified"] = Date(projectDir.lastModified())

                // 检查settings.json文件
                val settingsFile = File(projectDir, "settings.json")
                if (settingsFile.exists() && settingsFile.isFile) {
                    try {
                        val jsonString = settingsFile.readText()
                        val jsonMap = JsonUtil.parseObject(jsonString)
                        info["settings"] = jsonMap

                        // 获取全局Utils信息
                        val globalUtils = (jsonMap["global_utils"] as? List<*>) ?: emptyList<Any>()
                        info["global_utils"] = globalUtils
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 检查是否有icon.png
                val iconFile = File(projectDir, "icon.png")
                info["hasIcon"] = iconFile.exists() && iconFile.isFile

                // 统计文件数量
                val fileCount = projectDir.walk().filter { it.isFile }.count()
                info["fileCount"] = fileCount
            }

            info
        }
    }

    /**
     * 备份项目到zip文件
     * @param projectDir 项目目录
     * @param backupDir 备份目录
     * @param backupName 备份文件名（不含扩展名）
     * @return 备份文件，失败返回null
     */
    fun backupProjectToZip(projectDir: File, backupDir: File, backupName: String): File? {
        return try {
            if (!backupDir.exists()) backupDir.mkdirs()
            val zipFile = File(backupDir, "$backupName.zip")
            FileUtil.createZip(projectDir, zipFile)
            zipFile
        } catch (e: Exception) {
            android.util.Log.e("ProjectUtil", "备份项目失败", e)
            null
        }
    }

    /**
     * 从zip备份还原项目到目标目录
     * @return 是否成功
     */
    fun restoreProjectFromZip(zipFile: File, targetDir: File): Boolean {
        return try {
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()
            FileUtil.extractZip(zipFile, targetDir)
            true
        } catch (e: Exception) {
            android.util.Log.e("ProjectUtil", "还原项目失败", e)
            false
        }
    }

    /**
     * 从用户模板目录加载模板项（结合SettingsManager元数据）
     */
    private fun loadUserTemplateItems(context: Context): List<TemplateItem> {
        val templateDir = SettingsManager.getTemplatesDirectory()
        if (!templateDir.exists() || !templateDir.isDirectory) return emptyList()

        // 获取设置中保存的模板元数据（id -> ProjectTemplate）
        val metadataMap = SettingsManager.currentSettings.userTemplates.associateBy { it.id }

        val zipFiles = templateDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
            ?: return emptyList()

        // 清理元数据中不存在对应文件的条目（可选，保持一致性）
        val existingFilePaths = zipFiles.map { it.absolutePath }.toSet()
        val staleMetadata = metadataMap.values.filter { it.path !in existingFilePaths }
        if (staleMetadata.isNotEmpty()) {
            // 异步清理失效的元数据
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    val current = SettingsManager.currentSettings
                    val validTemplates = current.userTemplates.filter { it.path in existingFilePaths }
                    SettingsManager.updateSettings(current.copy(userTemplates = validTemplates))
                    SettingsManager.saveSettings(context)
                } catch (_: Exception) {}
            }
        }

        return zipFiles.mapNotNull { zipFile ->
            try {
                // 查找对应的元数据（通过路径匹配）
                val meta = metadataMap.values.find { it.path == zipFile.absolutePath }
                val templateId = meta?.id ?: "user_${zipFile.nameWithoutExtension}"
                val displayName = meta?.name ?: zipFile.nameWithoutExtension
                val description = meta?.description ?: "用户自定义模板"

                // 提取预览图
                val previewUri = extractPreviewFromUserZip(context, zipFile)

                TemplateItem(
                    name = displayName,
                    zipFileName = "user_${zipFile.name}",
                    previewPath = null,
                    previewUri = previewUri,
                    isUserTemplate = true,
                    filePath = zipFile.absolutePath,
                    description = description
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 从用户模板目录加载模板（公开方法，保持向后兼容）
     */
    fun loadUserTemplates(context: Context): List<TemplateItem> {
        return loadUserTemplateItems(context)
    }

    /**
     * 从用户zip模板提取预览图
     */
    private fun extractPreviewFromUserZip(context: Context, zipFile: File): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "template_previews")
            cacheDir.mkdirs()
            val previewFile = File(cacheDir, "user_${zipFile.name}.preview.png")
            if (previewFile.exists()) return Uri.fromFile(previewFile)

            ZipFile(zipFile).use { zip ->
                val entry = zip.entries().toList().find {
                    it.name.equals("Preview.png", ignoreCase = true) ||
                            it.name.equals("icon.png", ignoreCase = true)
                } ?: return null
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(previewFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Uri.fromFile(previewFile)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从Uri导入模板zip文件（用于文件选择器返回的Uri）
     * 将zip文件复制到用户模板目录，并在SettingsManager中注册元数据
     * @return 导入成功返回TemplateItem，失败返回null
     */
    fun importTemplateFromUri(context: Context, uri: Uri, templateName: String? = null): TemplateItem? {
        return try {
            val templateDir = SettingsManager.getTemplatesDirectory()

            // 从Uri获取原始文件名
            val originalName = getFileNameFromUri(context, uri) ?: "template_${System.currentTimeMillis()}.zip"
            val baseName = templateName ?: originalName.substringBeforeLast(".")
            val safeBaseName = baseName.replace(Regex("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]"), "_")

            // 生成目标文件名（避免重名）
            var destFile = File(templateDir, "$safeBaseName.zip")
            var counter = 1
            while (destFile.exists()) {
                destFile = File(templateDir, "${safeBaseName}_$counter.zip")
                counter++
            }

            // 复制文件
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            // 验证zip文件有效性
            try {
                ZipFile(destFile).use { /* 能打开即有效 */ }
            } catch (e: Exception) {
                destFile.delete()
                return null
            }

            // 生成模板ID并注册元数据
            val templateId = UUID.randomUUID().toString()
            val projectTemplate = ProjectTemplate(
                id = templateId,
                name = destFile.nameWithoutExtension,
                description = "用户导入的模板",
                type = TemplateType.USER,
                path = destFile.absolutePath,
                createdAt = System.currentTimeMillis()
            )
            SettingsManager.addUserTemplate(projectTemplate, context)

            // 提取预览图
            val previewUri = extractPreviewFromUserZip(context, destFile)

            TemplateItem(
                name = destFile.nameWithoutExtension,
                zipFileName = "user_${destFile.name}",
                previewUri = previewUri,
                isUserTemplate = true,
                filePath = destFile.absolutePath,
                description = "用户导入的模板"
            )
        } catch (e: Exception) {
            android.util.Log.e("ProjectUtil", "从Uri导入模板失败", e)
            null
        }
    }

    /**
     * 从Uri获取文件名
     */
    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            var fileName: String? = null
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            fileName = cursor.getString(displayNameIndex)
                        }
                    }
                }
            }
            if (fileName == null) {
                fileName = uri.path?.substringAfterLast("/")
            }
            fileName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 导入模板zip到用户模板目录（从File对象，保持向后兼容）
     * @return 是否成功
     */
    fun importTemplateFromZip(context: Context, zipFile: File): Boolean {
        return try {
            val templateDir = SettingsManager.getTemplatesDirectory()
            val destFile = File(templateDir, zipFile.name)
            // 如果同名文件存在，加时间戳
            val finalFile = if (destFile.exists()) {
                val name = zipFile.nameWithoutExtension
                val ext = zipFile.extension
                val ts = System.currentTimeMillis()
                File(templateDir, "${name}_$ts.$ext")
            } else {
                destFile
            }
            zipFile.copyTo(finalFile, overwrite = true)

            // 注册元数据
            val templateId = UUID.randomUUID().toString()
            val projectTemplate = ProjectTemplate(
                id = templateId,
                name = finalFile.nameWithoutExtension,
                description = "用户导入的模板",
                type = TemplateType.USER,
                path = finalFile.absolutePath,
                createdAt = System.currentTimeMillis()
            )
            SettingsManager.addUserTemplate(projectTemplate, context)
            true
        } catch (e: Exception) {
            android.util.Log.e("ProjectUtil", "导入模板失败", e)
            false
        }
    }

    /**
     * 删除用户模板（同时删除文件和设置中的元数据）
     */
    fun deleteUserTemplate(context: Context, template: TemplateItem): Boolean {
        return try {
            if (!template.isUserTemplate) return false

            // 从SettingsManager中查找并移除元数据
            val current = SettingsManager.currentSettings
            val metaToRemove = current.userTemplates.find { it.path == template.filePath }
            if (metaToRemove != null) {
                SettingsManager.removeUserTemplate(metaToRemove.id, context, deleteFile = true)
            } else {
                // 元数据不存在但文件存在，直接删除文件
                if (!template.filePath.isNullOrBlank()) {
                    val file = File(template.filePath)
                    if (file.exists()) file.delete()
                }
            }

            // 清理预览缓存
            try {
                val cacheDir = File(context.cacheDir, "template_previews")
                val previewFile = File(cacheDir, "${template.zipFileName}.preview.png")
                if (previewFile.exists()) previewFile.delete()
            } catch (_: Exception) {}

            true
        } catch (e: Exception) {
            android.util.Log.e("ProjectUtil", "删除用户模板失败", e)
            false
        }
    }

    /**
     * 保存项目为用户模板
     */
    fun saveProjectAsTemplate(projectDir: File, templateName: String, context: Context): File? {
        return try {
            val templateDir = SettingsManager.getTemplatesDirectory()
            val safeName = templateName.replace(Regex("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]"), "_")
            val zipFile = File(templateDir, "$safeName.zip")

            // 避免重名
            var finalFile = zipFile
            var counter = 1
            while (finalFile.exists()) {
                finalFile = File(templateDir, "${safeName}_$counter.zip")
                counter++
            }

            FileUtil.createZip(projectDir, finalFile)

            // 注册元数据
            val templateId = UUID.randomUUID().toString()
            val projectTemplate = ProjectTemplate(
                id = templateId,
                name = finalFile.nameWithoutExtension,
                description = "从项目保存的模板",
                type = TemplateType.USER,
                path = finalFile.absolutePath,
                createdAt = System.currentTimeMillis()
            )
            SettingsManager.addUserTemplate(projectTemplate, context)

            finalFile
        } catch (e: Exception) {
            android.util.Log.e("ProjectUtil", "保存模板失败", e)
            null
        }
    }
}