package com.luaforge.studio.lxclua.plugin.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.luaforge.studio.lxclua.plugin.PluginManager
import com.luaforge.studio.lxclua.plugin.api.IPluginBridge
import com.luaforge.studio.lxclua.plugin.api.callbacks.*
import com.luaforge.studio.lxclua.plugin.data.FileInfo
import com.luaforge.studio.lxclua.plugin.data.RegisteredResource
import com.luaforge.studio.lxclua.plugin.data.ShortcutInfo
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.NavigationState
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import com.luaforge.studio.lxclua.plugin.state.UIState
import com.luaforge.studio.lxclua.ui.editor.QuickAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.Executors

/**
 * 插件桥梁实现类
 * 
 * 实现了 IPluginBridge 接口，为插件提供访问宿主功能的能力
 */
class PluginBridgeImpl(val pluginId: String) : IPluginBridge {
    
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newCachedThreadPool()
    private val httpClient = OkHttpClient()
    
    private val pluginDataDir: File by lazy {
        File(PluginManager.appContext?.filesDir, "plugins_data/$pluginId").apply { mkdirs() }
    }
    
    private val pluginPrefs by lazy {
        PluginManager.appContext?.getSharedPreferences("plugin_prefs_$pluginId", Context.MODE_PRIVATE)!!
    }
    
    private val loggerBridge by lazy {
        PluginLogger(PluginManager.appContext!!, pluginId)
    }
    
    private val resourceRegistry by lazy {
        PluginResourceRegistry(pluginId)
    }
    
    private val shortcutBridge by lazy {
        PluginShortcut(pluginId)
    }
    
    private val completionBridge by lazy {
        PluginCompletion(pluginId)
    }
    
    private val syntaxBridge by lazy {
        PluginSyntax(pluginId)
    }
    
    private val decorationBridge by lazy {
        PluginDecoration(pluginId)
    }

    private val buildBridge by lazy {
        PluginBuild(PluginManager.appContext!!)
    }

    private val webUIBridge by lazy {
        PluginWebUIBridge(pluginId)
    }
    
    // ==================== 基础功能 ====================
    
    override fun toast(message: String) {
        handler.post {
            PluginManager.appContext?.let {
                android.widget.Toast.makeText(it, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun toastLong(message: String) {
        handler.post {
            PluginManager.appContext?.let {
                android.widget.Toast.makeText(it, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun log(tag: String, message: String) {
        com.luaforge.studio.lxclua.utils.LogCatcher.d(tag, message)
    }
    
    override fun getVersion(): String {
        return PluginManager.appContext?.packageManager?.getPackageInfo(
            PluginManager.appContext!!.packageName, 0
        )?.versionName ?: "1.0.0"
    }
    
    override fun getDataDir(): String {
        return pluginDataDir.absolutePath
    }
    
    override fun getContext(): Context {
        return PluginManager.appContext!!
    }
    
    // ==================== 快捷操作 ====================
    
    override fun addQuickAction(key: String, label: String, onClick: Runnable) {
        val globalKey = "${pluginId}_$key"
        val action = QuickAction(
            labelResId = 0,
            labelString = label,
            key = globalKey,
            onClick = { onClick.run() }
        )
        PluginManager.addQuickAction(pluginId, key, action)
    }
    
    override fun removeQuickAction(key: String) {
        PluginManager.removeQuickAction(pluginId, key)
    }
    
    override fun clearQuickActions() {
        PluginManager.clearQuickActions(pluginId)
    }
    
    // ==================== 菜单操作 ====================
    
    override fun addMenuItem(key: String, label: String, onClick: Runnable) {
        UIState.addPluginMenuItem(pluginId, key, label, onClick)
    }
    
    override fun addMenuDivider(key: String) {
        UIState.addPluginMenuDivider(pluginId, key)
    }
    
    override fun removeMenuItem(key: String) {
        UIState.removePluginMenuItem(pluginId, key)
    }
    
    override fun clearMenuItems() {
        UIState.removePluginMenuItems(pluginId)
    }
    
    // ==================== 文件树操作 ====================
    
    override fun addFileTreeMenuItem(key: String, label: String, filter: String?, onClick: FileTreeItemCallback) {
        addFileTreeMenuItem(key, label, null, filter, onClick)
    }
    
    override fun addFileTreeMenuItem(key: String, label: String, iconName: String?, filter: String?, onClick: FileTreeItemCallback) {
        UIState.addFileTreeMenuItem(pluginId, key, label, iconName, filter, onClick::onItemClick)
    }
    
    override fun addFileTreeMenuDivider(key: String, filter: String?) {
        UIState.addFileTreeMenuDivider(pluginId, key, filter)
    }
    
    override fun removeFileTreeMenuItem(key: String) {
        UIState.removeFileTreeMenuItem(pluginId, key)
    }
    
    override fun clearFileTreeMenuItems() {
        UIState.removeFileTreeMenuItems(pluginId)
    }
    
    override fun getFileInfo(filePath: String): FileInfo? {
        val file = File(filePath)
        if (!file.exists()) return null
        
        val name = file.name
        val extension = name.substringAfterLast('.', "").lowercase()
        val nameWithoutExtension = name.substringBeforeLast('.', name)
        
        return FileInfo(
            path = file.absolutePath,
            name = name,
            extension = extension,
            isDirectory = file.isDirectory,
            size = if (file.isDirectory) 0 else file.length(),
            lastModified = file.lastModified(),
            parentPath = file.parent ?: "",
            nameWithoutExtension = nameWithoutExtension
        )
    }
    
    // ==================== 剪贴板 ====================
    
    override fun copyToClipboard(text: String) {
        val clipboard = PluginManager.appContext?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("text", text)
        clipboard?.setPrimaryClip(clip)
    }
    
    override fun getClipboardText(): String? {
        val clipboard = PluginManager.appContext?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            return clip.getItemAt(0).text?.toString()
        }
        return null
    }
    
    // ==================== 配置存储 ====================
    
    override fun getConfig(key: String, defaultValue: String): String {
        return pluginPrefs.getString(key, defaultValue) ?: defaultValue
    }
    
    override fun setConfig(key: String, value: String) {
        pluginPrefs.edit().putString(key, value).apply()
    }
    
    override fun getConfigInt(key: String, defaultValue: Int): Int {
        return pluginPrefs.getInt(key, defaultValue)
    }
    
    override fun setConfigInt(key: String, value: Int) {
        pluginPrefs.edit().putInt(key, value).apply()
    }
    
    override fun getConfigBoolean(key: String, defaultValue: Boolean): Boolean {
        return pluginPrefs.getBoolean(key, defaultValue)
    }
    
    override fun setConfigBoolean(key: String, value: Boolean) {
        pluginPrefs.edit().putBoolean(key, value).apply()
    }
    
    override fun removeConfig(key: String) {
        pluginPrefs.edit().remove(key).apply()
    }
    
    // ==================== 事件系统 ====================
    
    override fun registerEventListener(eventName: String, listener: Any) {
        EventManager.registerEventListener(pluginId, eventName, listener)
    }
    
    override fun once(eventName: String, handler: (Array<out Any?>) -> Unit) {
        val listener = object : IPluginEventListener {
            override fun onEvent(vararg args: Any?) {
                EventManager.unregisterEventListener(eventName, this)
                handler(args)
            }
        }
        EventManager.registerOnceListener(pluginId, eventName, listener)
    }
    
    override fun intercept(eventName: String, priority: Int, handler: (Array<out Any?>) -> Boolean) {
        EventManager.registerInterceptor(pluginId, eventName, priority) { _, args ->
            handler(args)
        }
    }
    
    override fun unregisterEventListener(eventName: String, listener: Any) {
        EventManager.unregisterEventListener(eventName, listener)
    }
    
    // ==================== 首页 MainPage 扩展 ====================
    
    /**
     * 在首页顶部工具栏末尾添加自定义动作按钮
     * @param id 按钮唯一标识（插件内唯一）
     * @param iconName Material图标名称
     * @param tooltip 按钮提示文字
     * @param onClick 点击回调
     */
    override fun addToolbarAction(id: String, iconName: String, tooltip: String, onClick: Runnable) {
        handler.post {
            UIState.addToolbarAction(pluginId, id, iconName, tooltip, onClick)
        }
    }
    
    /**
     * 移除已注册的工具栏按钮
     */
    override fun removeToolbarAction(actionId: String) {
        handler.post {
            UIState.removeToolbarAction(pluginId, actionId)
        }
    }
    
    /**
     * 在首页 FAB 区域添加自定义小浮动按钮
     */
    override fun addHomeFab(id: String, iconName: String, tooltip: String, onClick: Runnable) {
        handler.post {
            UIState.addHomeFab(pluginId, id, iconName, tooltip, onClick)
        }
    }
    
    /**
     * 移除已注册的首页 FAB 按钮
     */
    override fun removeHomeFab(fabId: String) {
        handler.post {
            UIState.removeHomeFab(pluginId, fabId)
        }
    }
    
    /**
     * 在首页分类栏末尾添加自定义分类项
     */
    override fun addCategoryBarItem(id: String, iconName: String, name: String, onClick: Runnable) {
        handler.post {
            UIState.addCategoryBarItem(pluginId, id, iconName, name, onClick)
        }
    }
    
    /**
     * 移除已注册的分类栏项
     */
    override fun removeCategoryBarItem(itemId: String) {
        handler.post {
            UIState.removeCategoryBarItem(pluginId, itemId)
        }
    }
    
    /**
     * 为指定项目设置自定义徽章
     */
    override fun setProjectBadge(projectId: String, text: String, color: Long) {
        handler.post {
            PluginManager.projectBadges[projectId] = PluginManager.BadgeInfo(text, color)
        }
    }
    
    /**
     * 清除指定项目的自定义徽章
     */
    override fun clearProjectBadge(projectId: String) {
        handler.post { PluginManager.projectBadges.remove(projectId) }
    }
    
    /**
     * 请求导航到指定项目（打开编辑器），会先经过 ON_PROJECT_OPEN 拦截检查
     */
    override fun navigateToProject(projectId: String) {
        handler.post {
            val project = PluginManager.currentProjectItems.find { it.id == projectId }
            val projectName = project?.name ?: ""
            val projectPath = project?.path ?: ""
            // 仅检查拦截，事件通知由实际导航完成后（onNavigateToEditor）触发
            val intercepted = EventManager.checkIntercepted(
                PluginEvents.ON_PROJECT_OPEN,
                projectId, projectName, projectPath
            )
            if (!intercepted) {
                NavigationState.navigateToProject(projectId)
            }
        }
    }
    
    /**
     * 通知刷新项目列表
     */
    override fun refreshProjects() {
        handler.post {
            EventManager.fireEvent("onRefreshProjects")
        }
    }
    
    /**
     * 显示 Toast 提示
     */
    override fun showToast(message: String) {
        toast(message)
    }
    
    /**
     * 获取当前多选模式下选中的项目ID列表
     */
    override fun getSelectedProjectIds(): Array<String> {
        return PluginManager.multiSelectedProjectIds.toTypedArray()
    }
    
    /**
     * 设置多选模式
     */
    override fun setMultiSelectMode(enabled: Boolean) {
        handler.post {
            PluginManager.isMultiSelectMode.value = enabled
            if (!enabled) {
                PluginManager.multiSelectedProjectIds.clear()
            }
        }
    }
    
    // ==================== 线程工具 ====================
    
    override fun runOnUIThread(runnable: Runnable) {
        handler.post(runnable)
    }
    
    override fun runOnBackgroundThread(runnable: Runnable) {
        backgroundExecutor.execute(runnable)
    }
    
    // ==================== 网络请求 ====================
    
    override fun httpGet(url: String, headers: Map<String, String>?, callback: HttpCallback) {
        backgroundExecutor.execute {
            try {
                val requestBuilder = Request.Builder().url(url).get()
                headers?.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
                
                val response = httpClient.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful) {
                    callback.onResult(true, responseBody, null)
                } else {
                    callback.onResult(false, responseBody, "HTTP ${response.code}")
                }
            } catch (e: Exception) {
                callback.onResult(false, null, e.message)
            }
        }
    }
    
    override fun httpPost(url: String, body: String, headers: Map<String, String>?, callback: HttpCallback) {
        backgroundExecutor.execute {
            try {
                val requestBuilder = Request.Builder().url(url)
                    .post(body.toRequestBody())
                headers?.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
                
                val response = httpClient.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful) {
                    callback.onResult(true, responseBody, null)
                } else {
                    callback.onResult(false, responseBody, "HTTP ${response.code}")
                }
            } catch (e: Exception) {
                callback.onResult(false, null, e.message)
            }
        }
    }
    
    // ==================== 编辑器操作（简化实现） ====================
    
    override fun getActiveFile(): String? {
        return PluginManager.activeViewModel?.activeFileState?.file?.absolutePath
    }
    
    override fun getActiveText(): String? {
        return PluginManager.activeViewModel?.activeFileState?.content
    }
    
    override fun setActiveText(text: String) {
        handler.post {
            PluginManager.activeViewModel?.let { vm ->
                vm.activeFileState?.onContentChanged(text)
                vm.activeFileState?.let { state ->
                    state.content = text
                }
            }
        }
    }
    
    override fun insertText(text: String) {
        handler.post {
            PluginManager.activeViewModel?.insertSymbolToCorrectEditor(text)
        }
    }
    
    override fun getSelectedText(): String? {
        val editor = PluginManager.activeViewModel?.getActiveEditor() ?: return null
        val cursor = editor.cursor
        val leftIndex = cursor.left
        val rightIndex = cursor.right
        return if (leftIndex != rightIndex) {
            editor.text.substring(leftIndex, rightIndex)
        } else {
            ""
        }
    }
    
    override fun setSelection(start: Int, end: Int) {
        handler.post {
            val editor = PluginManager.activeViewModel?.getActiveEditor() ?: return@post
            val startPos = editor.text.indexer.getCharPosition(start)
            val endPos = editor.text.indexer.getCharPosition(end)
            editor.setSelectionRegion(
                startPos.line, startPos.column,
                endPos.line, endPos.column
            )
        }
    }
    
    override fun getCursorPosition(): IntArray? {
        val editor = PluginManager.activeViewModel?.getActiveEditor() ?: return null
        val cursor = editor.cursor
        return intArrayOf(cursor.leftLine, cursor.leftColumn)
    }
    
    override fun gotoLine(line: Int) {
        handler.post {
            val editor = PluginManager.activeViewModel?.getActiveEditor() ?: return@post
            editor.jumpToLine(line.coerceAtLeast(0))
        }
    }
    
    override fun gotoPosition(line: Int, column: Int) {
        handler.post {
            val editor = PluginManager.activeViewModel?.getActiveEditor() ?: return@post
            editor.setSelectionRegion(line, column, line, column)
        }
    }
    
    override fun getOpenFiles(): Array<String>? {
        return PluginManager.activeViewModel?.openFiles?.map { it.file.absolutePath }?.toTypedArray()
    }
    
    override fun closeActiveFile() {
        handler.post {
            val index = PluginManager.activeViewModel?.activeFileIndex ?: -1
            if (index >= 0) {
                PluginManager.activeViewModel?.closeFile(index)
            }
        }
    }
    
    override fun openFile(filePath: String) {
        handler.post {
            val file = File(filePath)
            if (file.exists()) {
                PluginManager.activeViewModel?.openFile(file)
            }
        }
    }
    
    override fun saveActiveFile() {
        kotlinx.coroutines.GlobalScope.launch {
            PluginManager.activeViewModel?.saveCurrentFileSilently()
        }
    }
    
    override fun saveAllFiles() {
        kotlinx.coroutines.GlobalScope.launch {
            PluginManager.activeViewModel?.saveAllFilesSilently()
        }
    }
    
    override fun undo() {
        handler.post {
            PluginManager.activeViewModel?.getActiveEditor()?.undo()
        }
    }
    
    override fun redo() {
        handler.post {
            PluginManager.activeViewModel?.getActiveEditor()?.redo()
        }
    }
    
    override fun findText(query: String, caseSensitive: Boolean, regex: Boolean) {
        handler.post {
            val editor = PluginManager.activeViewModel?.getActiveEditor() ?: return@post
            val options = io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions(!caseSensitive, regex)
            editor.searcher.search(query, options)
        }
    }
    
    override fun replaceText(query: String, replacement: String, replaceAll: Boolean) {
        handler.post {
            val editor = PluginManager.activeViewModel?.getActiveEditor() ?: return@post
            if (replaceAll) {
                val content = editor.text.toString()
                val newContent = content.replace(query, replacement)
                editor.setText(newContent)
            } else {
                val cursor = editor.cursor
                val selectedText = editor.text.substring(cursor.left, cursor.right)
                if (selectedText == query) {
                    editor.insertText(replacement, 0)
                }
            }
        }
    }
    
    // ==================== 项目操作 ====================
    
    override fun getProjectPath(): String? {
        return PluginManager.currentProjectPath.value
    }
    
    override fun readFile(relativePath: String): String? {
        return try {
            val projectPath = PluginManager.currentProjectPath.value ?: return null
            File(projectPath, relativePath).readText()
        } catch (e: Exception) {
            null
        }
    }
    
    override fun writeFile(relativePath: String, content: String): Boolean {
        return try {
            val projectPath = PluginManager.currentProjectPath.value ?: return false
            File(projectPath, relativePath).writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun listFiles(relativePath: String): Array<String>? {
        return try {
            val projectPath = PluginManager.currentProjectPath.value ?: return null
            File(projectPath, relativePath).list()
        } catch (e: Exception) {
            null
        }
    }
    
    override fun createFile(relativePath: String, content: String): Boolean {
        return try {
            val projectPath = PluginManager.currentProjectPath.value ?: return false
            File(projectPath, relativePath).apply {
                parentFile?.mkdirs()
                writeText(content)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun deleteFile(relativePath: String): Boolean {
        return try {
            val projectPath = PluginManager.currentProjectPath.value ?: return false
            File(projectPath, relativePath).deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun fileExists(relativePath: String): Boolean {
        return try {
            val projectPath = PluginManager.currentProjectPath.value ?: return false
            File(projectPath, relativePath).exists()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun createDirectory(relativePath: String): Boolean {
        return try {
            val projectPath = PluginManager.currentProjectPath.value ?: return false
            File(projectPath, relativePath).mkdirs()
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== UI 对话框 ====================
    
    override fun showMessage(title: String, message: String) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.Message(
                title = title,
                message = message,
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }
    
    override fun showConfirm(title: String, message: String, onConfirm: Runnable, onCancel: Runnable?) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.Confirm(
                title = title,
                message = message,
                onConfirm = {
                    PluginManager.currentDialog.value = null
                    onConfirm.run()
                },
                onCancel = onCancel?.let {
                    {
                        PluginManager.currentDialog.value = null
                        it.run()
                    }
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }
    
    override fun showInputDialog(title: String, hint: String, defaultValue: String, onInput: OnInputCallback) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.Input(
                title = title,
                hint = hint,
                defaultValue = defaultValue,
                onInput = { text ->
                    PluginManager.currentDialog.value = null
                    onInput.onInput(text)
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }
    
    override fun showSingleChoiceDialog(title: String, items: Array<String>, selectedIndex: Int, onSelect: OnSelectCallback) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.SingleChoice(
                title = title,
                items = items,
                selectedIndex = selectedIndex,
                onSelect = { index ->
                    PluginManager.currentDialog.value = null
                    onSelect.onSelect(index)
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }
    
    override fun showMultiChoiceDialog(title: String, items: Array<String>, checkedItems: BooleanArray, onConfirm: OnMultiSelectCallback) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.MultiChoice(
                title = title,
                items = items,
                checkedItems = checkedItems.copyOf(),
                onConfirm = { result ->
                    PluginManager.currentDialog.value = null
                    onConfirm.onConfirm(result)
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }
    
    override fun showProgressDialog(title: String, message: String): com.luaforge.studio.lxclua.plugin.api.ProgressDialogHandle {
        return object : com.luaforge.studio.lxclua.plugin.api.ProgressDialogHandle {
            override fun setProgress(progress: Int) {}
            override fun setMessage(message: String) {}
            override fun dismiss() {}
            override fun isShowing(): Boolean = false
        }
    }

    override fun showFileListDialog(title: String, directoryPath: String, filter: String?, onSelect: OnInputCallback) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.FileList(
                title = title,
                directoryPath = directoryPath,
                filter = filter,
                onSelect = { path ->
                    PluginManager.currentDialog.value = null
                    onSelect.onInput(path)
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    override fun showImageDialog(title: String, imagePath: String) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.ImageDisplay(
                title = title,
                imagePath = imagePath,
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    override fun showTextDialog(title: String, text: String) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.TextDisplay(
                title = title,
                text = text,
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    override fun showCheckboxDialog(title: String, message: String, checked: Boolean, onConfirm: OnSelectCallback) {
        handler.post {
            PluginManager.currentDialog.value = PluginManager.DialogState.Checkbox(
                title = title,
                message = message,
                checked = checked,
                onConfirm = { result ->
                    PluginManager.currentDialog.value = null
                    onConfirm.onSelect(if (result) 1 else 0)
                },
                onDismiss = { PluginManager.currentDialog.value = null }
            )
        }
    }

    override fun addBottomPanelItem(pluginId: String, key: String, title: String, elements: List<PluginManager.BottomPanelElement>, onEvent: Runnable?) {
        handler.post {
            val item = PluginManager.BottomPanelItem(
                pluginId = pluginId,
                key = key,
                title = title,
                elements = elements,
                onEvent = onEvent
            )
            val existingIndex = PluginManager.bottomPanelItems.indexOfFirst { it.key == key }
            if (existingIndex >= 0) {
                PluginManager.bottomPanelItems[existingIndex] = item
            } else {
                PluginManager.bottomPanelItems.add(item)
            }
            if (PluginManager.activeBottomPanelKey.value == null) {
                PluginManager.activeBottomPanelKey.value = key
            }
        }
    }

    override fun removeBottomPanelItem(key: String) {
        handler.post {
            PluginManager.bottomPanelItems.removeAll { it.key == key }
            if (PluginManager.activeBottomPanelKey.value == key) {
                PluginManager.activeBottomPanelKey.value = PluginManager.bottomPanelItems.firstOrNull()?.key
            }
        }
    }

    override fun clearBottomPanelItems(pluginId: String) {
        handler.post {
            PluginManager.bottomPanelItems.removeAll { it.pluginId == pluginId }
            if (PluginManager.bottomPanelItems.none { it.key == PluginManager.activeBottomPanelKey.value }) {
                PluginManager.activeBottomPanelKey.value = PluginManager.bottomPanelItems.firstOrNull()?.key
            }
        }
    }

    // ==================== Java 反射调用 ====================
    
    override fun loadClass(className: String): Class<*>? {
        return try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            null
        }
    }
    
    override fun newInstance(className: String, args: Array<Any?>?): Any? {
        return try {
            val clazz = Class.forName(className)
            if (args.isNullOrEmpty()) {
                clazz.getDeclaredConstructor().newInstance()
            } else {
                val argTypes = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
                val constructor = clazz.getDeclaredConstructor(*argTypes)
                constructor.isAccessible = true
                constructor.newInstance(*args)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun callStaticMethod(className: String, methodName: String, args: Array<Any?>?): Any? {
        return try {
            val clazz = Class.forName(className)
            val argArray = args ?: emptyArray()
            val methods = clazz.methods.filter { it.name == methodName }
            for (method in methods) {
                val paramTypes = method.parameterTypes
                if (paramTypes.size == argArray.size) {
                    val convertedArgs = convertArgs(paramTypes, argArray)
                    if (convertedArgs != null) {
                        return method.invoke(null, *convertedArgs)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    override fun callMethod(obj: Any, methodName: String, args: Array<Any?>?): Any? {
        return try {
            val argArray = args ?: emptyArray()
            val methods = obj.javaClass.methods.filter { it.name == methodName }
            for (method in methods) {
                val paramTypes = method.parameterTypes
                if (paramTypes.size == argArray.size) {
                    val convertedArgs = convertArgs(paramTypes, argArray)
                    if (convertedArgs != null) {
                        return method.invoke(obj, *convertedArgs)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun convertArgs(paramTypes: Array<Class<*>>, args: Array<Any?>): Array<Any?>? {
        return try {
            Array(args.size) { i ->
                val arg = args[i]
                val paramType = paramTypes[i]
                if (arg == null) null
                else if (paramType.isInstance(arg)) arg
                else convertValue(arg, paramType)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun convertValue(value: Any, targetType: Class<*>): Any? {
        return when (targetType) {
            Int::class.java, Int::class.javaPrimitiveType -> (value as? Number)?.toInt()
            Long::class.java, Long::class.javaPrimitiveType -> (value as? Number)?.toLong()
            Float::class.java, Float::class.javaPrimitiveType -> (value as? Number)?.toFloat()
            Double::class.java, Double::class.javaPrimitiveType -> (value as? Number)?.toDouble()
            Boolean::class.java, Boolean::class.javaPrimitiveType -> value as? Boolean
            else -> value
        }
    }
    
    override fun getStaticField(className: String, fieldName: String): Any? {
        return try {
            val clazz = Class.forName(className)
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(null)
        } catch (e: Exception) {
            null
        }
    }
    
    override fun setStaticField(className: String, fieldName: String, value: Any?) {
        try {
            val clazz = Class.forName(className)
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (e: Exception) {
        }
    }
    
    override fun getField(obj: Any, fieldName: String): Any? {
        return try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(obj)
        } catch (e: Exception) {
            null
        }
    }
    
    override fun setField(obj: Any, fieldName: String, value: Any?) {
        try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(obj, value)
        } catch (e: Exception) {
        }
    }
    
    // ==================== DEX 加载 ====================
    
    override fun loadDex(dexPath: String): Boolean {
        return try {
            val classLoader = dalvik.system.DexClassLoader(
                dexPath,
                PluginManager.appContext?.codeCacheDir?.absolutePath,
                null,
                javaClass.classLoader
            )
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun loadDexFromUrl(url: String, callback: DexLoadCallback) {
        backgroundExecutor.execute {
            try {
                val response = httpClient.newCall(Request.Builder().url(url).get().build()).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        val dexFile = File(PluginManager.appContext?.cacheDir, "temp.dex")
                        dexFile.writeBytes(bytes)
                        loadDex(dexFile.absolutePath)
                        callback.onLoad(true, null)
                    } else {
                        callback.onLoad(false, "下载失败")
                    }
                } else {
                    callback.onLoad(false, "HTTP ${response.code}")
                }
            } catch (e: Exception) {
                callback.onLoad(false, e.message)
            }
        }
    }
    
    override fun getPluginClassLoader(): ClassLoader? {
        return javaClass.classLoader
    }
    
    // ==================== 动态资源 ====================
    
    override fun loadResources(apkPath: String): com.luaforge.studio.lxclua.plugin.data.PluginResources? {
        return try {
            val assetManager = android.content.res.AssetManager::class.java.newInstance()
            val addAssetPath = android.content.res.AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            addAssetPath.invoke(assetManager, apkPath)
            
            val resources = PluginManager.appContext?.resources
            val config = resources?.configuration
            val metrics = resources?.displayMetrics
            
            com.luaforge.studio.lxclua.plugin.data.PluginResources(
                assetManager,
                android.content.res.Resources(assetManager, metrics, config),
                "plugin"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun getResourceString(resources: com.luaforge.studio.lxclua.plugin.data.PluginResources, resName: String): String? {
        return try {
            val resId = resources.resources.getIdentifier(resName, "string", resources.packageName)
            if (resId > 0) resources.resources.getString(resId) else null
        } catch (e: Exception) {
            null
        }
    }
    
    override fun getResourceDrawable(resources: com.luaforge.studio.lxclua.plugin.data.PluginResources, resName: String): android.graphics.drawable.Drawable? {
        return try {
            val resId = resources.resources.getIdentifier(resName, "drawable", resources.packageName)
            if (resId > 0) resources.resources.getDrawable(resId, null) else null
        } catch (e: Exception) {
            null
        }
    }
    
    override fun getResourceColor(resources: com.luaforge.studio.lxclua.plugin.data.PluginResources, resName: String): Int {
        return try {
            val resId = resources.resources.getIdentifier(resName, "color", resources.packageName)
            if (resId > 0) resources.resources.getColor(resId, null) else 0
        } catch (e: Exception) {
            0
        }
    }
    
    override fun getResourceId(resources: com.luaforge.studio.lxclua.plugin.data.PluginResources, resName: String, resType: String): Int {
        return resources.resources.getIdentifier(resName, resType, resources.packageName)
    }
    
    // ==================== Lua 脚本执行 ====================
    
    override fun executeLua(script: String): Any? {
        return try {
            val l = com.luajava.LuaStateFactory.newLuaState()
            l.openLibs()
            val ok = l.LloadString(script)
            if (ok == 0) {
                l.pcall(0, 1, 0)
                val result = l.toJavaObject(-1)
                l.close()
                result
            } else {
                l.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun executeLuaFile(scriptPath: String): Any? {
        return try {
            val l = com.luajava.LuaStateFactory.newLuaState()
            l.openLibs()
            val ok = l.LloadFile(scriptPath)
            if (ok == 0) {
                l.pcall(0, 1, 0)
                val result = l.toJavaObject(-1)
                l.close()
                result
            } else {
                l.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun compileLua(script: String): ByteArray? {
        return try {
            val l = com.luajava.LuaStateFactory.newLuaState()
            l.openLibs()
            val ok = l.LloadString(script)
            if (ok == 0) {
                val bytecode = l.dump(-1)
                l.close()
                bytecode
            } else {
                l.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun executeLuaBytecode(bytecode: ByteArray): Any? {
        return try {
            val l = com.luajava.LuaStateFactory.newLuaState()
            l.openLibs()
            l.LloadBuffer(bytecode, "bytecode")
            l.pcall(0, 1, 0)
            val result = l.toJavaObject(-1)
            l.close()
            result
        } catch (e: Exception) {
            null
        }
    }
    
    // ==================== 日志系统 ====================
    
    override fun debug(tag: String, message: String) {
        loggerBridge.debug(tag, message)
    }
    
    override fun info(tag: String, message: String) {
        loggerBridge.info(tag, message)
    }
    
    override fun warn(tag: String, message: String) {
        loggerBridge.warn(tag, message)
    }
    
    override fun error(tag: String, message: String, error: String?) {
        loggerBridge.error(tag, message, error)
    }
    
    override fun getLogDir(): String {
        return loggerBridge.getLogDir()
    }
    
    override fun listLogFiles(): Array<String> {
        return loggerBridge.listLogFiles()
    }
    
    override fun readLogFile(filename: String): String {
        return loggerBridge.readLogFile(filename)
    }
    
    override fun getLatestLog(): String {
        return loggerBridge.getLatestLog()
    }
    
    override fun getLogFileSize(filename: String): Long {
        return loggerBridge.getLogFileSize(filename)
    }
    
    override fun clearLogs(): Boolean {
        return loggerBridge.clearLogs()
    }
    
    override fun getPluginLogDir(pluginId: String): String {
        return loggerBridge.getPluginLogDir(pluginId)
    }
    
    override fun listPluginLogFiles(pluginId: String): Array<String> {
        return loggerBridge.listPluginLogFiles(pluginId)
    }
    
    override fun readPluginLogFile(pluginId: String, filename: String): String {
        return loggerBridge.readPluginLogFile(pluginId, filename)
    }
    
    override fun getPluginLatestLog(pluginId: String): String {
        return loggerBridge.getPluginLatestLog(pluginId)
    }
    
    override fun searchLogs(keyword: String, maxResults: Int): Array<LogEntry> {
        return loggerBridge.searchLogs(keyword, maxResults)
    }
    
    override fun searchPluginLogs(pluginId: String, keyword: String, maxResults: Int): Array<LogEntry> {
        return loggerBridge.searchPluginLogs(pluginId, keyword, maxResults)
    }
    
    override fun getLogLineCount(): Int {
        return loggerBridge.getLogLineCount()
    }
    
    override fun getPluginLogLineCount(pluginId: String): Int {
        return loggerBridge.getPluginLogLineCount(pluginId)
    }
    
    // ==================== 资源注册表 ====================
    
    override fun registerAsset(
        key: String,
        type: String,
        filePath: String,
        displayName: String,
        description: String,
        isPublic: Boolean,
        metadata: Map<String, String>?
    ): String? {
        return resourceRegistry.registerAsset(key, type, filePath, displayName, description, isPublic, metadata)
    }
    
    override fun unregisterAsset(key: String): Boolean {
        return resourceRegistry.unregisterAsset(key)
    }
    
    override fun unregisterAllAssets(): Int {
        return resourceRegistry.unregisterAllAssets()
    }
    
    override fun getMyAssets(): Array<RegisteredResource> {
        return resourceRegistry.getMyAssets()
    }
    
    override fun getAsset(globalId: String): RegisteredResource? {
        return resourceRegistry.getAsset(globalId)
    }
    
    override fun getAllPublicAssets(): Array<RegisteredResource> {
        return resourceRegistry.getAllPublicAssets()
    }
    
    override fun getAssetsByType(type: String): Array<RegisteredResource> {
        return resourceRegistry.getAssetsByType(type)
    }
    
    override fun getPluginAssets(pluginId: String): Array<RegisteredResource> {
        return resourceRegistry.getPluginAssets(pluginId)
    }
    
    override fun readAssetBytes(globalId: String): ByteArray? {
        return resourceRegistry.readAssetBytes(globalId)
    }
    
    override fun readAssetText(globalId: String): String? {
        return resourceRegistry.readAssetText(globalId)
    }
    
    override fun assetExists(globalId: String): Boolean {
        return resourceRegistry.assetExists(globalId)
    }
    
    override fun getTotalAssetCount(): Int {
        return resourceRegistry.getTotalAssetCount()
    }
    
    override fun getAssetCountByType(type: String): Int {
        return resourceRegistry.getAssetCountByType(type)
    }
    
    // ==================== 快捷键绑定 ====================
    
    override fun register(
        key: String,
        combination: String,
        label: String,
        description: String,
        callback: Any,
        editorOnly: Boolean
    ): String? {
        return shortcutBridge.register(key, combination, label, description, callback, editorOnly)
    }
    
    override fun register(key: String, combination: String, label: String, callback: Any): String? {
        return shortcutBridge.register(key, combination, label, callback)
    }
    
    override fun register(key: String, combination: String, label: String, callback: Any, editorOnly: Boolean): String? {
        return shortcutBridge.register(key, combination, label, callback, editorOnly)
    }
    
    override fun unregister(key: String): Boolean {
        return shortcutBridge.unregister(key)
    }
    
    override fun unregisterAll(): Int {
        return shortcutBridge.unregisterAll()
    }
    
    override fun getMyShortcuts(): Array<ShortcutInfo> {
        return shortcutBridge.getMyShortcuts()
    }
    
    override fun getShortcut(globalId: String): ShortcutInfo? {
        return shortcutBridge.getShortcut(globalId)
    }
    
    override fun getAllShortcuts(): Array<ShortcutInfo> {
        return shortcutBridge.getAllShortcuts()
    }
    
    override fun isCombinationTaken(combination: String): ShortcutInfo? {
        return shortcutBridge.isCombinationTaken(combination)
    }
    
    override fun getShortcutCount(): Int {
        return shortcutBridge.getShortcutCount()
    }
    
    // ==================== 代码补全扩展 ====================
    
    override fun addKeyword(keyword: String): Boolean {
        return completionBridge.addKeyword(keyword)
    }
    
    override fun addKeywords(keywords: Array<String>): Int {
        return completionBridge.addKeywords(keywords)
    }
    
    override fun removeKeyword(keyword: String): Boolean {
        return completionBridge.removeKeyword(keyword)
    }
    
    override fun addPackage(packageName: String, functions: Array<String>): Boolean {
        return completionBridge.addPackage(packageName, functions)
    }
    
    override fun removePackage(packageName: String): Boolean {
        return completionBridge.removePackage(packageName)
    }
    
    override fun addVariableType(variableName: String, className: String): Boolean {
        return completionBridge.addVariableType(variableName, className)
    }
    
    override fun removeVariableType(variableName: String): Boolean {
        return completionBridge.removeVariableType(variableName)
    }
    
    override fun registerProvider(language: String, callback: Any): String? {
        return completionBridge.registerProvider(language, callback)
    }
    
    override fun unregisterProvider(providerId: String): Boolean {
        return completionBridge.unregisterProvider(providerId)
    }
    
    override fun clearMyKeywords(): Int {
        return completionBridge.clearMyKeywords()
    }
    
    override fun clearMyPackages(): Int {
        return completionBridge.clearMyPackages()
    }
    
    override fun clearMyVariableTypes(): Int {
        return completionBridge.clearMyVariableTypes()
    }
    
    override fun clearMyProviders(): Int {
        return completionBridge.clearMyProviders()
    }
    
    override fun clearAll(): Array<Int> {
        return completionBridge.clearAll()
    }
    
    // ==================== 符号栏操作 ====================
    
    override fun getSymbols(): Array<String> {
        val baseSymbols = arrayOf(
            "function()", "(", ")", "[", "]", "{", "}", "\"", "=", ":",
            ".", ",", ";", "_", "+", "-", "*", "/", "\\", "%",
            "#", "^", "$", "?", "&", "|", "<", ">", "~", "'"
        )
        val custom = PluginManager.customSymbolBarSymbols.toList()
        return (baseSymbols.toList() + custom).toTypedArray()
    }
    
    override fun addSymbol(symbol: String): Boolean {
        val baseSet = setOf(
            "function()", "(", ")", "[", "]", "{", "}", "\"", "=", ":",
            ".", ",", ";", "_", "+", "-", "*", "/", "\\", "%",
            "#", "^", "$", "?", "&", "|", "<", ">", "~", "'"
        )
        if (symbol in baseSet) return false
        if (symbol in PluginManager.customSymbolBarSymbols) return false
        handler.post {
            PluginManager.customSymbolBarSymbols.add(symbol)
        }
        return true
    }
    
    override fun removeSymbol(symbol: String): Boolean {
        val baseSet = setOf(
            "function()", "(", ")", "[", "]", "{", "}", "\"", "=", ":",
            ".", ",", ";", "_", "+", "-", "*", "/", "\\", "%",
            "#", "^", "$", "?", "&", "|", "<", ">", "~", "'"
        )
        if (symbol in baseSet) return false
        if (symbol !in PluginManager.customSymbolBarSymbols) return false
        handler.post {
            PluginManager.customSymbolBarSymbols.remove(symbol)
        }
        return true
    }
    
    override fun clearCustomSymbols() {
        handler.post {
            PluginManager.customSymbolBarSymbols.clear()
        }
    }
    
    override fun getSymbolFrequency(symbol: String): Int {
        return PluginManager.activeViewModel?.symbolFrequencyMap?.get(symbol) ?: 0
    }
    
    override fun getAllSymbolFrequencies(): Map<String, Int> {
        return PluginManager.activeViewModel?.symbolFrequencyMap?.toMap() ?: emptyMap()
    }
    
    override fun incrementSymbolFrequency(symbol: String) {
        PluginManager.activeViewModel?.incrementSymbolFrequency(symbol)
    }
    
    override fun insertSymbol(symbol: String) {
        handler.post {
            PluginManager.activeViewModel?.insertSymbolToCorrectEditor(symbol)
        }
    }
    
    override fun getSelectedClassName(): String? {
        return PluginManager.activeViewModel?.selectedClassName
    }
    
    override fun getSelectedClassCandidates(): Array<String>? {
        return PluginManager.activeViewModel?.selectedClassCandidates?.toTypedArray()
    }
    
    override fun setPanelExpanded(expanded: Boolean) {
        handler.post {
            val panelState = PluginManager.activePanelState ?: return@post
            if (expanded) {
                panelState.animateToHeight(panelState.maxHeight * 0.8f)
            } else {
                panelState.animateToHeight(panelState.minHeight)
            }
        }
    }
    
    override fun isPanelExpanded(): Boolean {
        val panelState = PluginManager.activePanelState ?: return false
        return panelState.isAboveThreshold
    }
    
    override fun setPanelHeight(heightPx: Float) {
        handler.post {
            PluginManager.activePanelState?.updateHeight(heightPx)
        }
    }
    
    override fun getPanelHeight(): Float {
        return PluginManager.activePanelState?.height ?: 0f
    }
    
    override fun getPanelMinHeight(): Float {
        return PluginManager.activePanelState?.minHeight ?: 0f
    }
    
    override fun getPanelMaxHeight(): Float {
        return PluginManager.activePanelState?.maxHeight ?: 0f
    }
    
    // ==================== 语法高亮扩展 ====================
    
    override fun registerLanguage(languageId: String, rules: Map<String, Any>): Boolean {
        return syntaxBridge.registerLanguage(languageId, rules)
    }
    
    override fun unregisterLanguage(languageId: String): Boolean {
        return syntaxBridge.unregisterLanguage(languageId)
    }
    
    override fun getLanguageRules(languageId: String): Map<String, Any>? {
        return syntaxBridge.getLanguageRules(languageId)
    }
    
    override fun getAllRegisteredLanguages(): Array<String> {
        return syntaxBridge.getAllRegisteredLanguages()
    }
    
    override fun clearMyLanguages(): Int {
        return syntaxBridge.clearMyLanguages()
    }
    
    override fun isLanguageRegistered(languageId: String): Boolean {
        return syntaxBridge.isLanguageRegistered(languageId)
    }
    
    // ==================== 编辑器装饰 ====================
    
    override fun setLineBackground(line: Int, color: Int): Boolean {
        return decorationBridge.setLineBackground(line, color)
    }
    
    override fun setLineBackground(line: Int, color: Int, category: String): Boolean {
        return decorationBridge.setLineBackground(line, color, category)
    }
    
    override fun setLineBackgrounds(lines: IntArray, color: Int): Int {
        return decorationBridge.setLineBackgrounds(lines, color)
    }
    
    override fun setGutterBackground(line: Int, color: Int): Boolean {
        return decorationBridge.setGutterBackground(line, color)
    }
    
    override fun setGutterBackground(line: Int, color: Int, category: String): Boolean {
        return decorationBridge.setGutterBackground(line, color, category)
    }
    
    override fun setGutterIcon(line: Int, iconType: String): Boolean {
        return decorationBridge.setGutterIcon(line, iconType)
    }
    
    override fun setGutterIcon(line: Int, iconType: String, category: String): Boolean {
        return decorationBridge.setGutterIcon(line, iconType, category)
    }
    
    override fun removeLineDecoration(line: Int): Boolean {
        return decorationBridge.removeLineDecoration(line)
    }
    
    override fun clearMyDecorations(): Int {
        return decorationBridge.clearMyDecorations()
    }
    
    override fun getMyDecorations(): Array<Map<String, Any?>> {
        return decorationBridge.getMyDecorations()
    }
    
    override fun getLineDecorations(line: Int): Array<Map<String, Any?>> {
        return decorationBridge.getLineDecorations(line)
    }
    
    override fun setOnDecorationClick(callback: Runnable) {
        decorationBridge.setOnDecorationClick(callback)
    }
    
    override fun setOnDecorationLongClick(callback: Runnable) {
        decorationBridge.setOnDecorationLongClick(callback)
    }
    
    override fun setOnDecorationDoubleClick(callback: Runnable) {
        decorationBridge.setOnDecorationDoubleClick(callback)
    }
    
    override fun setOnGutterIconClick(callback: Runnable) {
        decorationBridge.setOnGutterIconClick(callback)
    }
    
    override fun gotoNextDecoration(): Int {
        return decorationBridge.gotoNextDecoration()
    }
    
    override fun gotoNextDecoration(category: String): Int {
        return decorationBridge.gotoNextDecoration(category)
    }
    
    override fun gotoPreviousDecoration(): Int {
        return decorationBridge.gotoPreviousDecoration()
    }
    
    override fun gotoPreviousDecoration(category: String): Int {
        return decorationBridge.gotoPreviousDecoration(category)
    }
    
    override fun getDecorationLines(): IntArray {
        return decorationBridge.getDecorationLines()
    }
    
    override fun getDecorationLines(category: String): IntArray {
        return decorationBridge.getDecorationLines(category)
    }

    // ==================== 构建系统 ====================

    override fun buildProject(projectPath: String): String {
        return buildBridge.buildProject(projectPath)
    }

    override fun compileFile(filePath: String): String {
        return buildBridge.compileFile(filePath)
    }

    override fun getLastBuildResult(): String? {
        return buildBridge.getLastBuildResult()
    }

    override fun cancelBuild() {
        buildBridge.cancelBuild()
    }

    // ==================== WebUI ====================

    /**
     * 打开插件 Web 界面
     *
     * 通过 NavigationState 触发 UI 层导航到 PluginWebUIScreen
     */
    override fun open(page: String): Boolean {
        return try {
            val context = PluginManager.appContext ?: return false
            // 确保 web 文件存在
            if (!webUIBridge.webFileExists(page.ifEmpty { "index.html" })) {
                android.util.Log.w("PluginBridgeImpl", "WebUI 文件不存在: $page")
                return false
            }
            // 触发导航
            NavigationState.openWebUI(pluginId, page.ifEmpty { "index.html" })
            true
        } catch (e: Exception) {
            android.util.Log.e("PluginBridgeImpl", "打开 WebUI 失败", e)
            false
        }
    }

    /**
     * 关闭当前 Web 界面
     */
    override fun close(): Boolean {
        return try {
            NavigationState.closeWebUI()
            true
        } catch (e: Exception) {
            android.util.Log.e("PluginBridgeImpl", "关闭 WebUI 失败", e)
            false
        }
    }

    /**
     * 检查是否正在显示 Web 界面
     */
    override fun isShowing(): Boolean {
        return NavigationState.isWebUIShowing()
    }

    /**
     * 向 WebView 发送消息（宿主 → Web）
     */
    override fun sendToWeb(jsonMessage: String) {
        webUIBridge.sendToWebView(jsonMessage)
    }

    /**
     * 向 WebView 执行 JavaScript 代码
     */
    override fun evaluateJs(jsCode: String) {
        val wv = webUIBridge.getWebView()
        webUIBridge.evaluateJs(wv, jsCode)
    }

    /**
     * 检查指定 Web 文件是否存在
     */
    override fun webFileExists(filename: String): Boolean {
        return webUIBridge.webFileExists(filename)
    }

    /**
     * 列出 web/ 目录下所有文件
     */
    override fun listFiles(): Array<String> {
        return webUIBridge.listWebFiles()
    }

    /**
     * 获取 Web 资源根目录路径
     */
    override fun getWebRoot(): String {
        return webUIBridge.getWebRootDir()?.absolutePath ?: ""
    }

    /**
     * 获取 WebView 实例引用
     */
    override fun getWebView(): WebView? {
        return webUIBridge.getWebView()
    }

    // ==================== AI 功能 ====================

    private val aiBridge by lazy { PluginAI() }

    override fun chat(messagesJson: String): String {
        return try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            val messages: List<Map<String, String>> = gson.fromJson(messagesJson, type)
            val result = aiBridge.chat(messages, null)
            result ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun chatAsync(messagesJson: String, callback: HttpCallback) {
        try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            val messages: List<Map<String, String>> = gson.fromJson(messagesJson, type)
            val msgs = messages.map { com.luaforge.studio.lxclua.ai.ChatMessage(it["role"] ?: "user", it["content"] ?: "") }
            val req = com.luaforge.studio.lxclua.ai.ChatRequest(messages = msgs)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val response = com.luaforge.studio.lxclua.ai.AIManager.chat(req)
                    callback.onResult(response.success, response.content, response.error)
                } catch (e: Exception) {
                    callback.onResult(false, null, e.message)
                }
            }
        } catch (e: Exception) {
            callback.onResult(false, null, e.message)
        }
    }

    override fun chatAsyncV2(messagesJson: String, callback: com.luaforge.studio.lxclua.plugin.api.callbacks.AIChatCallback) {
        try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            val messages: List<Map<String, String>> = gson.fromJson(messagesJson, type)
            val msgs = messages.map { com.luaforge.studio.lxclua.ai.ChatMessage(it["role"] ?: "user", it["content"] ?: "") }
            val req = com.luaforge.studio.lxclua.ai.ChatRequest(messages = msgs)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val response = com.luaforge.studio.lxclua.ai.AIManager.chat(req)
                    callback.onResult(
                        response.success,
                        response.content,
                        response.error,
                        response.model,
                        response.usage?.totalTokens ?: 0,
                        response.reasoningContent
                    )
                } catch (e: Exception) {
                    callback.onResult(false, null, e.message, null, 0, null)
                }
            }
        } catch (e: Exception) {
            callback.onResult(false, null, e.message, null, 0, null)
        }
    }

    override fun isAiAvailable(): Boolean = aiBridge.isAvailable()

    override fun getAiConfig(): String {
        return com.google.gson.Gson().toJson(aiBridge.getConfig())
    }

    // ==================== MCP 功能 ====================

    private val mcpBridge by lazy { PluginMCP(pluginId, PluginManager.appContext!!) }

    override fun connectMcp(): Boolean = mcpBridge.connect()

    override fun disconnectMcp() { mcpBridge.disconnect() }

    override fun isMcpConnected(): Boolean = mcpBridge.isConnected()

    override fun listMcpTools(): String {
        return com.google.gson.Gson().toJson(mcpBridge.listTools())
    }

    override fun callMcpTool(name: String, argumentsJson: String): String {
        return try {
            val args = com.google.gson.Gson().fromJson(argumentsJson, Map::class.java) as? Map<String, Any> ?: emptyMap()
            val result = mcpBridge.callTool(name, args)
            com.google.gson.Gson().toJson(result)
        } catch (e: Exception) {
            "{\"success\":false,\"error\":\"${e.message}\"}"
        }
    }

    override fun listMcpResources(): String {
        return com.google.gson.Gson().toJson(mcpBridge.listResources())
    }

    override fun readMcpResource(uri: String): String? = mcpBridge.readResource(uri)

    override fun registerMcpTool(name: String, description: String, inputSchemaJson: String): Boolean {
        return try {
            // DEX 插件注册工具使用简化方式
            com.luaforge.studio.lxclua.mcp.MCPManager.registerPluginTool(
                com.luaforge.studio.lxclua.mcp.MCPTool(name, description, emptyMap())
            ) { _ -> "DEX 插件工具: $name 已调用" }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun unregisterMcpTool(name: String) {
        com.luaforge.studio.lxclua.mcp.MCPManager.unregisterPluginTool(name)
    }

    // ==================== 悬浮窗功能 ====================

    private val floatingBridge by lazy { PluginFloating(pluginId) }

    override fun createFloatingBall(x: Int, y: Int, label: String, iconText: String): String? {
        return floatingBridge.createBall(x, y, label, iconText, null, null)
    }

    override fun removeFloatingBall(id: String) {
        floatingBridge.removeBall(id)
    }

    override fun removeAllFloatingBalls() {
        floatingBridge.removeAll()
    }

    override fun updateFloatingBall(id: String, label: String) {
        floatingBridge.updateBall(id, label)
    }

    override fun showFloatingPanel(id: String, title: String, hint: String) {
        floatingBridge.showPanel(id, title, hint)
    }

    override fun showFloatingPanelWebUI(id: String, title: String, page: String): Boolean {
        return floatingBridge.showPanelWebUI(id, title, page)
    }

    override fun hideFloatingPanel(id: String) {
        floatingBridge.hidePanel(id)
    }

    override fun requestFloatingPanelFocus(id: String) {
        floatingBridge.requestFocus(id)
    }

    override fun clearFloatingPanelFocus(id: String) {
        floatingBridge.clearFocus(id)
    }

    override fun sendToFloatingPanelWeb(id: String, jsonMessage: String) {
        floatingBridge.sendToWeb(id, jsonMessage)
    }

    override fun evaluateFloatingPanelJs(id: String, jsCode: String) {
        floatingBridge.evaluateJs(id, jsCode)
    }

    override fun getFloatingBallCount(): Int = floatingBridge.getBallCount()

    // ==================== 系统信息与权限 ====================

    private val systemBridge by lazy { PluginSystem() }

    override fun getScreenWidth(): Int = systemBridge.getScreenWidth()

    override fun getScreenHeight(): Int = systemBridge.getScreenHeight()

    override fun getScreenDensity(): Float = systemBridge.getScreenDensity()

    override fun getScreenInfoJson(): String {
        return com.google.gson.Gson().toJson(systemBridge.getScreenInfo())
    }

    override fun getDeviceInfoJson(): String {
        return com.google.gson.Gson().toJson(systemBridge.getDeviceInfo())
    }

    override fun getAppInfoJson(): String {
        return com.google.gson.Gson().toJson(systemBridge.getAppInfo())
    }

    override fun checkPermission(permission: String): Boolean = systemBridge.checkPermission(permission)

    override fun canDrawOverlays(): Boolean = systemBridge.canDrawOverlays()

    override fun openOverlaySettings() = systemBridge.openOverlaySettings()

    override fun openAppSettings() = systemBridge.openAppSettings()
}