package com.luaforge.studio.lxclua.ui.editor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.luaforge.studio.lxclua.R
import com.luaforge.studio.lxclua.build.ApkBuilder
import com.luaforge.studio.lxclua.ui.editor.viewmodel.EditorViewModel
import com.luaforge.studio.lxclua.utils.ConsoleUtil
import com.luaforge.studio.lxclua.utils.JsonUtil
import com.luaforge.studio.lxclua.utils.LogCatcher
import com.luaforge.studio.lxclua.utils.NonBlockingToastState
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import com.luajava.LuaState
import com.luajava.LuaStateFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 编译当前文件
 */
suspend fun compileCurrentFile(
    viewModel: EditorViewModel,
    toast: NonBlockingToastState,
    context: Context,
    isCompilingFile: (Boolean) -> Unit
) {
    val currentFile = viewModel.activeFileState?.file
    if (currentFile == null) {
        toast.showToast(context.getString(R.string.code_editor_no_active_file))
        return
    }

    val fileName = currentFile.name
    val filePath = currentFile.absolutePath

    // 检查文件类型
    val isLuaFile = fileName.endsWith(".lua", ignoreCase = true)
    val isAlyFile = fileName.endsWith(".aly", ignoreCase = true)

    if (!isLuaFile && !isAlyFile) {
        toast.showToast(context.getString(R.string.code_editor_only_lua_aly))
        return
    }

    // 显示编译进度
    isCompilingFile(true)

    // 触发构建开始钩子
    EventManager.fireEvent(PluginEvents.ON_BUILD_START, filePath, "compile")

    // 检查取消标志（插件可能在 onBuildStart 中调用了 cancelBuild）
    if (com.luaforge.studio.lxclua.plugin.bridge.PluginBuild.buildCancelled) {
        com.luaforge.studio.lxclua.plugin.bridge.PluginBuild.buildCancelled = false
        isCompilingFile(false)
        return
    }

    try {
        // 在后台线程执行编译
        val (compiledPath, errorMsg) = withContext(Dispatchers.IO) {
            var luaState: LuaState? = null
            try {
                // 先保存当前文件
                viewModel.saveCurrentFileSilently()

                // 创建 LuaState
                luaState = LuaStateFactory.newLuaState()
                luaState.openLibs()

                // 使用 ConsoleUtil.build 编译文件（自动处理 lua 和 aly 类型）
                val result = ConsoleUtil.build(luaState, filePath)
                val resultTable = result as? Map<*, *>
                val path = resultTable?.get("path") as? String
                val error = resultTable?.get("error") as? String

                Pair(path, error)
            } catch (e: Exception) {
                LogCatcher.e("CodeEditScreen", "编译文件失败: $filePath", e)
                Pair(null, e.message)
            } finally {
                // 清理 LuaState：先 GC 清理栈，再关闭以释放原生内存
                luaState?.let {
                    try {
                        it.gc(LuaState.LUA_GCCOLLECT, 1)
                        it.top = 0 // 清理栈
                        it.close()
                    } catch (e: Exception) {
                        LogCatcher.e("CodeEditScreen", "清理LuaState失败", e)
                    }
                }
            }
        }

        if (compiledPath != null) {
            // 触发编译完成钩子（成功）
            EventManager.fireEvent(PluginEvents.ON_BUILD_FINISH, filePath, compiledPath, true)
            com.luaforge.studio.lxclua.plugin.bridge.PluginBuild.lastGlobalResult = compiledPath
            toast.showToast(context.getString(R.string.code_editor_compile_success, compiledPath))

            // 重新加载编译后的文件
            if (isAlyFile) {
                val newFile = File(filePath.replace(".aly", ".lua"))
                if (newFile.exists()) {
                    // 重新打开编译后的文件
                    withContext(Dispatchers.Main) {
                        viewModel.openFile(newFile, currentFile.parent ?: "")
                    }
                }
            } else {
                // 对于.lua文件，重新加载当前文件以显示编译后的内容
                viewModel.reloadCurrentFile()
            }
        } else {
            // 触发编译完成钩子（失败）
            val displayError = errorMsg ?: context.getString(R.string.unknown_error)
            EventManager.fireEvent(PluginEvents.ON_BUILD_FINISH, filePath, displayError, false)
            com.luaforge.studio.lxclua.plugin.bridge.PluginBuild.lastGlobalResult = displayError
            EventManager.fireEvent(PluginEvents.ON_BUILD_ERROR, filePath, displayError, "compile")
            toast.showToast(context.getString(R.string.code_editor_compile_failed, displayError))
        }
    } catch (e: Exception) {
        LogCatcher.e("CodeEditScreen", "编译文件异常", e)
        val excMsg = context.getString(R.string.code_editor_compile_exception, e.message)
        EventManager.fireEvent(PluginEvents.ON_BUILD_FINISH, filePath, excMsg, false)
        com.luaforge.studio.lxclua.plugin.bridge.PluginBuild.lastGlobalResult = excMsg
        EventManager.fireEvent(PluginEvents.ON_BUILD_ERROR, filePath, excMsg, "compile")
        toast.showToast(excMsg)
    } finally {
        // 隐藏编译进度
        isCompilingFile(false)
    }
}

/**
 * 构建项目核心逻辑
 * @param logCallback 日志回调，将构建日志实时推送到 UI
 */
suspend fun buildProject(
    context: Context,
    projectPath: String,
    logCallback: (String) -> Unit = {}
): String {
    // 设置 LogCatcher 监听器，将构建日志推送到 UI
    val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    LogCatcher.setLogListener { level, tag, message ->
        val time = timestamp.format(java.util.Date())
        val levelTag = when (level) {
            "ERROR" -> "[E]"
            "WARN" -> "[W]"
            "INFO" -> "[I]"
            else -> "[D]"
        }
        logCallback("$time $levelTag $tag: $message")
    }

    return try {
        withContext(Dispatchers.IO) {
            LogCatcher.i("CodeEditScreen", "开始构建项目，路径: $projectPath")

        // 触发构建开始钩子
        EventManager.fireEvent(PluginEvents.ON_BUILD_START, projectPath, "project")

        // 检查取消标志（插件可能在 onBuildStart 中调用了 cancelBuild）
        if (com.luaforge.studio.lxclua.plugin.bridge.PluginBuild.buildCancelled) {
            com.luaforge.studio.lxclua.plugin.bridge.PluginBuild.buildCancelled = false
            return@withContext "cancelled:已取消"
        }

        // 在构建前清理内存
        // 注：Android 上显式 System.gc() 会暂停所有线程，已移除

        // 读取 settings.json 文件
        val settingsFile = File(projectPath, "settings.json")
        if (!settingsFile.exists()) {
            return@withContext "error: ${context.getString(R.string.editor_build_settings_not_found)}"
        }

        // 使用JsonUtil解析settings.json
        val settings = try {
            val jsonString = settingsFile.readText()
            JsonUtil.parseObject(jsonString)
        } catch (e: Exception) {
            LogCatcher.e("CodeEditScreen", "解析 settings.json 失败", e)
            return@withContext "error: ${context.getString(R.string.editor_build_parse_settings_failed, e.message)}"
        }

        // 提取构建所需参数
        val packageName = settings["package"] as? String ?: "com.example.myapp"
        val versionName = settings["versionName"] as? String ?: "1.0.0"
        val versionCode = settings["versionCode"] as? String ?: "1"

        // 获取应用名称
        val appName = try {
            val application = settings["application"] as? Map<String, Any?>
            (application?.get("label") as? String) ?: File(projectPath).name
        } catch (e: Exception) {
            File(projectPath).name
        }

        // 获取调试模式
        val isDebug = try {
            val application = settings["application"] as? Map<String, Any?>
            (application?.get("debugmode") as? Boolean) ?: false
        } catch (e: Exception) {
            false
        }

        // 获取权限列表
        val permissions = try {
            val permissionList = settings["user_permission"] as? List<*>
            permissionList?.filterIsInstance<String>()?.toTypedArray() ?: emptyArray<String>()
        } catch (e: Exception) {
            emptyArray<String>()
        }
        
// 获取 Maven 依赖列表
val mavenDependencies = try {
    val implList = settings["implementation"] as? List<*>
    implList?.filterIsInstance<String>() ?: emptyList()
} catch (e: Exception) {
    emptyList<String>()
}

        // 获取图标路径
        val iconFile = File(projectPath, "icon.png")
        val iconPath = if (iconFile.exists()) iconFile.absolutePath else null

        // 获取minSdkVersion和targetSdkVersion
        var minSdkVersion = 21  // 默认值
        var targetSdkVersion = 29 // 默认值

        try {
            val usesSdk = settings["uses_sdk"] as? Map<String, Any?>
            if (usesSdk != null) {
                val minSdkStr = usesSdk["minSdkVersion"] as? String
                val targetSdkStr = usesSdk["targetSdkVersion"] as? String

                if (minSdkStr != null) {
                    minSdkVersion = minSdkStr.toIntOrNull() ?: 21
                }

                if (targetSdkStr != null) {
                    targetSdkVersion = targetSdkStr.toIntOrNull() ?: 29
                }

                // 如果上面没有找到，尝试直接作为数字获取
                if (minSdkVersion == 21) {
                    minSdkVersion = (usesSdk["minSdkVersion"] as? Int) ?: 21
                }

                if (targetSdkVersion == 29) {
                    targetSdkVersion = (usesSdk["targetSdkVersion"] as? Int) ?: 29
                }
            }
        } catch (e: Exception) {
            LogCatcher.e("CodeEditScreen", "解析uses_sdk失败，使用默认值", e)
        }

        // 检查必要的文件
        val mainLuaFile = File(projectPath, "main.lua")
        if (!mainLuaFile.exists()) {
            return@withContext "error: ${context.getString(R.string.code_editor_main_lua_not_found)}"
        }

        // 设置输出路径，使用应用外部存储目录
        val buildDir = File(context.getExternalFilesDir(null), "build")
        if (!buildDir.exists()) {
            buildDir.mkdirs()
        }

        // 使用应用名称作为APK文件名，清理非法字符（仅保留字母数字）
        val cleanAppName = appName.replace(Regex("[^a-zA-Z0-9]"), "_")
        val baseApkName = "${cleanAppName}.apk"
        val apkFile = File(buildDir, baseApkName)

        // 如果文件已存在，添加时间戳
        val finalApkFile = if (apkFile.exists()) {
            val timestamp = System.currentTimeMillis()
            File(buildDir, "${cleanAppName}_${timestamp}.apk")
        } else {
            apkFile
        }

        val externalApkPath = finalApkFile.absolutePath

        LogCatcher.i("CodeEditScreen", "输出APK路径: $externalApkPath")

        // 调用新的 ApkBuilder 进行构建
        return@withContext try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            LogCatcher.i(
                "CodeEditScreen",
                "构建前内存 - 最大: ${maxMemory / 1024 / 1024}MB, 已用: ${usedMemory / 1024 / 1024}MB"
            )

            if ((usedMemory.toFloat() / maxMemory) > 0.8) {
                LogCatcher.w("CodeEditScreen", "内存使用过高，跳过GC（Android 上显式 GC 会暂停所有线程）")
            }

            // 调用新的 ApkBuilder.bin 方法
            val result = ApkBuilder.bin(
                context,                     // context
                context.filesDir.absolutePath, // mRootDir
                projectPath,                 // projectPath
                appName,                     // appName
                packageName,                 // packageName
                versionName,                 // versionName
                versionCode,                 // versionCode
                iconPath,                    // iconPath
                permissions,                 // permissions
                isDebug,                     // isDebug
                externalApkPath,             // outputPath
                minSdkVersion,               // minSdkVersion
                targetSdkVersion,             // targetSdkVersion
                mavenDependencies
            )

            // 构建后再次检查内存
            val finalUsedMemory = runtime.totalMemory() - runtime.freeMemory()
            LogCatcher.i("CodeEditScreen", "构建后内存 - 已用: ${finalUsedMemory / 1024 / 1024}MB")

            // 触发构建完成钩子
            val success = !result.startsWith("error:")
            EventManager.fireEvent(PluginEvents.ON_BUILD_FINISH, projectPath, result, success)
            com.luaforge.studio.lxclua.plugin.bridge.PluginBuild.lastGlobalResult = result
            if (!success) {
                EventManager.fireEvent(PluginEvents.ON_BUILD_ERROR, projectPath, result, "project")
            }
            result
        } catch (e: Throwable) {
            val errorResult: String
            if (e is OutOfMemoryError) {
                LogCatcher.e(
                    "CodeEditScreen",
                    "构建过程中内存溢出",
                    Exception("内存溢出: ${e.message}")
                )
                errorResult = "error: ${context.getString(R.string.editor_build_out_of_memory)}"
            } else {
                val exception = if (e is Exception) e else Exception(e.toString(), null)
                LogCatcher.e("CodeEditScreen", "调用 ApkBuilder 构建失败", exception)
                errorResult = "error: ${context.getString(R.string.code_editor_build_exception, e.message)}"
            }
            EventManager.fireEvent(PluginEvents.ON_BUILD_FINISH, projectPath, errorResult, false)
            com.luaforge.studio.lxclua.plugin.bridge.PluginBuild.lastGlobalResult = errorResult
            EventManager.fireEvent(PluginEvents.ON_BUILD_ERROR, projectPath, errorResult, "project")
            errorResult
        }
    } finally {
        // 清除日志监听器
        LogCatcher.setLogListener(null)
    }
}

/**
 * 备份项目核心逻辑
 */
suspend fun backupProject(context: Context, projectPath: String): String = withContext(Dispatchers.IO) {
    LogCatcher.i("CodeEditScreen", "开始备份项目，路径: $projectPath")

    // 创建备份目录
    val backupDir = File(context.getExternalFilesDir(null), "backup")
    if (!backupDir.exists()) {
        val created = backupDir.mkdirs()
        if (!created) {
            return@withContext "error: ${context.getString(R.string.editor_backup_dir_create_failed)}"
        }
    }

    // 获取项目名称
    val projectName = File(projectPath).name
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val backupFileName = "${projectName}_${timestamp}.zip"
    val backupFile = File(backupDir, backupFileName)

    try {
        val projectFile = File(projectPath)

        // 使用 use 确保流在异常时安全关闭
        ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zipOut ->
            projectFile.walk().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(projectFile).path
                    val zipEntry = ZipEntry(relativePath)
                    zipEntry.time = file.lastModified()
                    zipOut.putNextEntry(zipEntry)

                    FileInputStream(file).use { input ->
                        input.copyTo(zipOut, bufferSize = 8192)
                    }
                    zipOut.closeEntry()
                }
            }
        }

        LogCatcher.i("CodeEditScreen", "备份成功，保存到: ${backupFile.absolutePath}")
        return@withContext backupFile.absolutePath

    } catch (e: Exception) {
        LogCatcher.e("CodeEditScreen", "备份项目失败", e)
        return@withContext "error: ${context.getString(R.string.code_editor_backup_failed, e.message)}"
    }
}

/**
 * 安装APK函数
 */
fun installApk(
    context: Context,
    filePath: String,
    toast: NonBlockingToastState,
    scope: CoroutineScope
) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            LogCatcher.e("CodeEditScreen", "APK文件不存在: $filePath")
            scope.launch {
                toast.showToast(context.getString(R.string.editor_install_apk_not_exists))
            }
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            // 检查Android版本
            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0及以上使用FileProvider
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                // Android 7.0以下使用传统方式
                Uri.fromFile(file)
            }

            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // 对于Android 7.0及以上，需要明确授予临时权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val resInfoList = context.packageManager
                    .queryIntentActivities(this, PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(
                        packageName,
                        apkUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        }

        context.startActivity(intent)

        LogCatcher.i("CodeEditScreen", "正在安装APK: $filePath")

    } catch (e: ActivityNotFoundException) {
        LogCatcher.e("CodeEditScreen", "未找到安装程序", e)
        scope.launch {
            toast.showToast(context.getString(R.string.code_editor_install_not_found))
        }
    } catch (e: SecurityException) {
        LogCatcher.e("CodeEditScreen", "权限不足，无法安装", e)
        scope.launch {
            toast.showToast(context.getString(R.string.code_editor_install_permission_denied))
        }
    } catch (e: Exception) {
        LogCatcher.e("CodeEditScreen", "安装APK失败", e)
        scope.launch {
            toast.showToast(context.getString(R.string.code_editor_install_failed, e.message))
        }
    }
}

/**
 * 从项目的 settings.json 文件中读取 application.label
 */
fun getAppNameFromSettings(projectPath: String): String? {
    return try {
        val settingsFile = File(projectPath, "settings.json")
        if (settingsFile.exists() && settingsFile.isFile) {
            val jsonString = settingsFile.readText()
            val settings = JsonUtil.parseObject(jsonString)

            val application = settings["application"] as? Map<String, Any?>
            val label = application?.get("label") as? String

            label
        } else {
            null
        }
    } catch (e: Exception) {
        LogCatcher.e("CodeEditScreen", "读取 settings.json 失败", e)
        null
    }
}