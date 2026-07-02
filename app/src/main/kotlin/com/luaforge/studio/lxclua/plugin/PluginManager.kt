package com.luaforge.studio.lxclua.plugin

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.luaforge.studio.lxclua.R
import com.luaforge.studio.lxclua.ProjectItem
import com.luaforge.studio.lxclua.plugin.api.IPlugin
import com.luaforge.studio.lxclua.plugin.bridge.PluginBridge
import com.luaforge.studio.lxclua.plugin.data.PluginManifest
import com.luaforge.studio.lxclua.plugin.loaders.DexPluginLoader
import com.luaforge.studio.lxclua.plugin.loaders.LuaPluginLoader
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import com.luaforge.studio.lxclua.plugin.state.UIState
import com.luaforge.studio.lxclua.ui.editor.QuickAction
import com.luaforge.studio.lxclua.ui.editor.viewmodel.EditorViewModel
import com.luaforge.studio.lxclua.ai.AIConfigManager
import com.luaforge.studio.lxclua.ai.AIManager
import com.luaforge.studio.lxclua.mcp.MCPManager
import com.luajava.LuaState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * 已加载插件包装类
 * 
 * enabled 使用 MutableState 确保 Compose 能直接感知状态变化，
 * 避免列表 item 替换导致的 Switch 动画闪烁
 */
class LoadedPlugin(
    val manifest: PluginManifest,
    val directory: File,
    initialEnabled: Boolean,
    var luaState: LuaState? = null,
    var dexPlugin: IPlugin? = null,
    var pluginBridge: Any? = null,  // PluginBridge 实例引用，用于取消 AI 请求等
    /** 加载错误信息（null 表示无错误） */
    var loadError: String? = null
) {
    /** Compose 可观察的启用状态，Switch 直接绑定此值，避免列表复用时状态闪烁 */
    val enabled = mutableStateOf(initialEnabled)

    /**
     * 创建副本（替代 data class 的 copy）
     * @param manifest 新 manifest，默认沿用当前
     * @param directory 新目录，默认沿用当前
     * @param enabled 新启用状态（Boolean），默认沿用当前值
     * @param luaState 新 LuaState，默认沿用当前
     * @param dexPlugin 新 dexPlugin，默认沿用当前
     * @return 新的 LoadedPlugin 实例
     */
    fun copy(
        manifest: PluginManifest = this.manifest,
        directory: File = this.directory,
        enabled: Boolean = this.enabled.value,
        luaState: LuaState? = this.luaState,
        dexPlugin: IPlugin? = this.dexPlugin,
        pluginBridge: Any? = this.pluginBridge
    ): LoadedPlugin {
        return LoadedPlugin(manifest, directory, enabled, luaState, dexPlugin, pluginBridge)
    }
}

/**
 * 插件管理引擎单例
 * 
 * 负责插件的扫描、加载、卸载和管理
 */
object PluginManager {
    private const val PREFS_NAME = "plugin_settings"
    private const val PREF_ENABLED_PREFIX = "plugin_enabled_"
    
    var appContext: Context? = null
        private set
    
    var currentActivity: android.app.Activity? = null
    
    // 内存中的已扫描插件列表
    val loadedPlugins = mutableStateListOf<LoadedPlugin>()
    
    // 当前活动的编辑器 ViewModel
    var activeViewModel: EditorViewModel? = null
    
    // 当前活动的符号栏面板状态
    var activePanelState: com.luaforge.studio.lxclua.ui.editor.DraggablePanelState? = null
    
    // 当前活动的工程路径
    val currentProjectPath = mutableStateOf<String?>(null)
    
    // 插件自定义的符号栏额外符号
    val customSymbolBarSymbols = mutableStateListOf<String>()
    
    // 动态注册的快捷功能动作列表
    val pluginQuickActions = mutableStateListOf<QuickAction>()
    private val quickActionsMap = mutableMapOf<String, QuickAction>()
    
    // Compose 对话框状态
    sealed class DialogState {
        data class Message(
            val title: String,
            val message: String,
            val onDismiss: () -> Unit
        ) : DialogState()
        
        data class Confirm(
            val title: String,
            val message: String,
            val onConfirm: () -> Unit,
            val onCancel: (() -> Unit)?,
            val onDismiss: () -> Unit
        ) : DialogState()
        
        data class Input(
            val title: String,
            val hint: String,
            val defaultValue: String,
            val onInput: (String) -> Unit,
            val onDismiss: () -> Unit
        ) : DialogState()
        
        data class SingleChoice(
            val title: String,
            val items: Array<String>,
            val selectedIndex: Int,
            val onSelect: (Int) -> Unit,
            val onDismiss: () -> Unit
        ) : DialogState()
        
        data class MultiChoice(
            val title: String,
            val items: Array<String>,
            val checkedItems: BooleanArray,
            val onConfirm: (BooleanArray) -> Unit,
            val onDismiss: () -> Unit
        ) : DialogState()

        data class FileList(
            val title: String,
            val directoryPath: String,
            val filter: String?,
            val onSelect: (String) -> Unit,
            val onDismiss: () -> Unit
        ) : DialogState()

        data class ImageDisplay(
            val title: String,
            val imagePath: String,
            val onDismiss: () -> Unit
        ) : DialogState()

        data class TextDisplay(
            val title: String,
            val text: String,
            val onDismiss: () -> Unit
        ) : DialogState()

        data class Checkbox(
            val title: String,
            val message: String,
            val checked: Boolean,
            val onConfirm: (Boolean) -> Unit,
            val onDismiss: () -> Unit
        ) : DialogState()
    }
    
    val currentDialog = mutableStateOf<DialogState?>(null)
    
    // 主页项目多选模式状态
    val isMultiSelectMode = mutableStateOf(false)
    val multiSelectedProjectIds = mutableStateListOf<String>()
    
    // 项目徽章状态: projectId -> BadgeInfo
    data class BadgeInfo(val text: String, val color: Long, val pluginId: String = "")
    val projectBadges = mutableStateMapOf<String, BadgeInfo>()
    
    // 插件注册的项目卡片菜单项
    data class ProjectCardMenuItem(
        val key: String,
        val label: String,
        val onClick: (String, String, String) -> Unit  // projectId, projectName, projectPath
    )
    val projectCardMenuItems = mutableStateListOf<ProjectCardMenuItem>()

    // 编辑器底部面板扩展
    data class BottomPanelElement(
        val type: String,           // "text", "button", "spacer", "section"
        val id: String? = null,     // 用于按钮点击事件
        val value: String? = null,  // 文本内容或按钮标签
        val height: Float = 0f      // 用于 spacer
    )

    data class BottomPanelItem(
        val pluginId: String,
        val key: String,
        val title: String,
        val elements: List<BottomPanelElement>,
        val onEvent: Runnable?
    )
    val bottomPanelItems = mutableStateListOf<BottomPanelItem>()
    val activeBottomPanelKey = mutableStateOf<String?>(null)

    // ========== 插件UI扩展：工具栏按钮 ==========
    /**
     * 工具栏按钮条目（插件通过 plugin.ui.addToolbarButton 注册）
     */
    data class ToolbarActionEntry(
        val key: String,             // 唯一key = pluginId:extensionPoint:id
        val pluginId: String,
        val extensionPoint: String,  // 扩展点位置（UIExtensionPoints常量）
        val actionId: String,        // 按钮ID（插件内唯一）
        val tooltip: String,         // 提示文本/按钮文字
        val priority: Int,           // 优先级（数字越小越靠前）
        val onClick: Runnable        // 点击回调
    )
    val toolbarActionEntries = mutableStateListOf<ToolbarActionEntry>()

    // ========== 插件UI扩展：菜单项 ==========
    /**
     * 菜单项条目（插件通过 plugin.ui.addMenuItem 注册）
     */
    data class MenuItemEntry(
        val key: String,
        val pluginId: String,
        val extensionPoint: String,
        val itemId: String,
        val title: String,
        val priority: Int,
        val onClick: Runnable
    )
    val menuItemEntries = mutableStateListOf<MenuItemEntry>()

    // 当前主页项目列表数据（供插件读取）
    val currentProjectItems = mutableStateListOf<ProjectItem>()
    
    /**
     * 获取插件存放根目录
     */
    fun getPluginsDir(context: Context): File {
        val extDir = Environment.getExternalStorageDirectory()
        val mainDir = File(extDir, "LXC-LUA/plugins")
        if (!mainDir.exists()) {
            mainDir.mkdirs()
        }
        return if (mainDir.exists() && mainDir.canWrite()) {
            mainDir
        } else {
            File(context.filesDir, "plugins").apply { mkdirs() }
        }
    }
    
    /**
     * 获取指定插件的目录 File 对象
     * @param context 任意 Context
     * @param pluginId 插件 ID
     * @return 插件目录 File；插件未加载或不存在时返回 null
     */
    fun getPluginDirectory(context: Context, pluginId: String): File? {
        val plugin = loadedPlugins.find { it.manifest.id == pluginId } ?: return null
        return plugin.directory
    }
    
    private fun getPrefs(context: Context) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 初始化插件管理器
     * 先加载 AI 配置（同步），再加载插件，确保插件注册的 MCP 服务不会被后续异步 loadConfig 覆盖
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        scanPlugins(context)
        // 先同步加载 AI 配置，避免插件注册 MCP 服务后被 loadConfig 覆盖
        runBlocking(Dispatchers.IO) {
            AIConfigManager.loadConfig(context)
            AIManager.refresh()
        }
        // 初始化 MCP 管理器（用于持久化配置）
        MCPManager.init(context)
        // 配置就绪后再加载插件
        loadEnabledPlugins()
        // 自动连接已启用的远程 MCP 服务器（异步，不阻塞启动）
        MCPManager.autoConnectEnabledServers()
    }
    
    /**
     * 扫描所有的插件目录，解析元数据
     */
    fun scanPlugins(context: Context) {
        val pluginsDir = getPluginsDir(context)
        val dirs = pluginsDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        val manifestList = mutableListOf<LoadedPlugin>()
        val prefs = getPrefs(context)
        
        for (dir in dirs) {
            val manifestFile = File(dir, "manifest.json")
            if (manifestFile.exists()) {
                try {
                    val content = manifestFile.readText()
                    val manifest = Gson().fromJson(content, PluginManifest::class.java)
                    val enabled = prefs.getBoolean(PREF_ENABLED_PREFIX + manifest.id, false)
                    
                    // 保留已加载插件的运行状态
                    val existing = loadedPlugins.find { it.manifest.id == manifest.id }
                    if (existing != null) {
                        manifestList.add(existing.copy(manifest = manifest, directory = dir, enabled = enabled))
                    } else {
                        manifestList.add(LoadedPlugin(manifest, dir, enabled))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PluginManager", "解析插件元数据失败: ${dir.absolutePath}", e)
                }
            }
        }
        
        // 卸载已删除的插件
        val currentIds = manifestList.map { it.manifest.id }.toSet()
        loadedPlugins.forEach {
            if (it.manifest.id !in currentIds) {
                unloadPluginInternal(it)
            }
        }
        
        loadedPlugins.clear()
        loadedPlugins.addAll(manifestList)
    }
    
    /**
     * 加载所有已启用的插件（带依赖检查）
     */
    fun loadEnabledPlugins() {
        // 先加载核心插件
        for (plugin in loadedPlugins) {
            if (plugin.enabled.value && isCorePlugin(plugin) && plugin.luaState == null && plugin.dexPlugin == null) {
                try {
                    loadPluginInternal(plugin)
                } catch (e: Exception) {
                    android.util.Log.e("PluginManager", "加载核心插件失败: ${plugin.manifest.name}", e)
                }
            }
        }
        
        // 然后加载普通插件（按依赖顺序）
        for (plugin in loadedPlugins) {
            if (plugin.enabled.value && !isCorePlugin(plugin) && plugin.luaState == null && plugin.dexPlugin == null) {
                try {
                    loadPluginInternal(plugin)
                } catch (e: Exception) {
                    android.util.Log.e("PluginManager", "加载插件失败: ${plugin.manifest.name}", e)
                }
            }
        }
        
        // 触发所有插件加载完成事件
        EventManager.fireEvent(PluginEvents.ON_ALL_PLUGINS_LOADED)
    }
    
    /**
     * 检查是否为核心插件
     */
    private fun isCorePlugin(plugin: LoadedPlugin): Boolean {
        return plugin.manifest.pluginType.equals("core", ignoreCase = true)
    }
    
    /**
     * 检查插件依赖是否满足
     * @return Pair(是否满足, 不满足的原因)
     */
    fun checkDependencies(plugin: LoadedPlugin): Pair<Boolean, String> {
        val context = appContext
        val manifest = plugin.manifest
        val dependencies = manifest.dependencies ?: emptyList()
        
        for (dep in dependencies) {
            val depPlugin = loadedPlugins.find { it.manifest.id == dep.pluginId }
            
            if (depPlugin == null) {
                if (dep.required) {
                    return Pair(false, context?.getString(R.string.plugin_dependency_missing, dep.pluginId)
                        ?: "缺少必需依赖: ${dep.pluginId}")
                }
                continue
            }
            
            if (!isVersionSatisfied(depPlugin.manifest.version, dep.minVersion)) {
                if (dep.required) {
                    return Pair(false, context?.getString(
                        R.string.plugin_dependency_version_mismatch,
                        dep.pluginId, dep.minVersion, depPlugin.manifest.version
                    ) ?: "依赖 ${dep.pluginId} 版本要求 ${dep.minVersion}，当前版本 ${depPlugin.manifest.version}")
                }
            }
            
            if (!depPlugin.enabled.value) {
                if (dep.required) {
                    return Pair(false, context?.getString(R.string.plugin_dependency_disabled, dep.pluginId)
                        ?: "必需依赖 ${dep.pluginId} 未启用")
                }
            }
        }
        
        return Pair(true, context?.getString(R.string.plugin_dependency_check_passed) ?: "依赖检查通过")
    }
    
    /**
     * 版本号比较
     * @return 当前版本是否满足最低版本要求
     */
    private fun isVersionSatisfied(currentVersion: String, minVersion: String): Boolean {
        val currentParts = currentVersion.split(".")
        val minParts = minVersion.split(".")
        
        for (i in 0 until maxOf(currentParts.size, minParts.size)) {
            val current = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            val min = minParts.getOrNull(i)?.toIntOrNull() ?: 0
            
            if (current > min) return true
            if (current < min) return false
        }
        return true
    }
    
    /**
     * 单个插件加载逻辑（带依赖检查）
     */
    internal fun loadPluginInternal(plugin: LoadedPlugin) {
        val context = appContext ?: return
        val manifest = plugin.manifest
        
        // 检查依赖
        val (depsOk, depsReason) = checkDependencies(plugin)
        if (!depsOk) {
            val err = "依赖检查失败: $depsReason"
            android.util.Log.w("PluginManager", "插件 ${manifest.name} $err，跳过加载")
            plugin.loadError = err
            return
        }
        
        val type = (manifest.type ?: "lua").lowercase(java.util.Locale.getDefault())
        // 加载前清除旧错误
        plugin.loadError = null
        
        try {
            if (type == "lua") {
                LuaPluginLoader.load(plugin, context)
            } else if (type == "dex" || type == "apk") {
                DexPluginLoader.load(plugin, context)
            }
        } catch (e: Exception) {
            val err = "加载异常: ${e.javaClass.simpleName}: ${e.message}"
            android.util.Log.e("PluginManager", "加载插件失败: ${manifest.name}", e)
            plugin.loadError = err
        }
    }
    
    /**
     * 单个插件卸载逻辑
     */
    internal fun unloadPluginInternal(plugin: LoadedPlugin) {
        val pluginId = plugin.manifest.id
        
        try {
            // 取消该插件进行中的 AI 请求
            (plugin.pluginBridge as? PluginBridge)?.ai?.cancelAll()
            
            // 调用 Lua 插件的卸载回调
            val luaState = plugin.luaState
            if (luaState != null) {
                try {
                    luaState.getGlobal("onUnload")
                    if (luaState.isFunction(-1)) {
                        luaState.pcall(0, 0, 0)
                    } else {
                        luaState.pop(1)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PluginManager", "调用 onUnload 异常 [${pluginId}]", e)
                }
            }
            
            // 清理 Lua 状态
            plugin.luaState?.close()
            plugin.luaState = null
            
            // 调用插件的卸载回调
            plugin.dexPlugin?.onUnload()
            plugin.dexPlugin = null
            
            // 清理该插件注册的 UI 元素
            removePluginUiElements(pluginId)
            
            // 清理该插件的悬浮球
            com.luaforge.studio.lxclua.plugin.floating.FloatingManager.removeBallsByPlugin(pluginId)
            
            // 移除该插件的所有事件监听器
            EventManager.removePluginListeners(pluginId)
            
            // 触发插件卸载事件
            EventManager.fireEvent(PluginEvents.ON_PLUGIN_UNLOADED, pluginId)
        } catch (e: Exception) {
            android.util.Log.e("PluginManager", "卸载插件失败: ${plugin.manifest.name}", e)
        }
    }
    
    /**
     * 移除插件注册的所有 UI 元素
     */
    private fun removePluginUiElements(pluginId: String) {
        // 移除快捷操作
        quickActionsMap.keys.removeAll { it.startsWith("${pluginId}_") }
        updateQuickActions()
        
        // 移除菜单项、文件树菜单、工具栏/FAB/分类栏扩展
        UIState.removePluginUI(pluginId)
        
        // 移除侧滑栏菜单项
        com.luaforge.studio.lxclua.plugin.state.NavigationState.clearPluginSidebarItems(pluginId)
        
        // 移除关于页面 section / item
        com.luaforge.studio.lxclua.plugin.state.AboutState.clearPluginItems(pluginId)
        
        // 移除设置页面扩展
        com.luaforge.studio.lxclua.plugin.state.PluginSettingsState.clearPluginItems(pluginId)
        
        // 移除插件详情展开区扩展
        com.luaforge.studio.lxclua.plugin.state.PluginDetailState.clearPluginItems(pluginId)

        // 移除底部面板扩展
        bottomPanelItems.removeAll { it.pluginId == pluginId }
        if (bottomPanelItems.none { it.key == activeBottomPanelKey.value }) {
            activeBottomPanelKey.value = bottomPanelItems.firstOrNull()?.key
        }

        // 移除UI扩展点（Compose 扩展）
        com.luaforge.studio.lxclua.plugin.state.UIExtensionManager.unregisterExtensions(pluginId)

        // 移除工具栏按钮扩展
        toolbarActionEntries.removeAll { it.pluginId == pluginId }

        // 移除菜单项扩展
        menuItemEntries.removeAll { it.pluginId == pluginId }

        // 移除项目卡片菜单项（注意：现有projectCardMenuItems不带pluginId，这里只清理以pluginId_开头的key）
        projectCardMenuItems.removeAll { it.key.startsWith("${pluginId}_") }

        // 移除该插件设置的所有项目徽章
        val badgesToRemove = projectBadges.filter { it.value.pluginId == pluginId }.keys
        badgesToRemove.forEach { projectBadges.remove(it) }

        // 移除注册的资源
        com.luaforge.studio.lxclua.plugin.bridge.PluginResourceRegistry.removeAllPluginAssets(pluginId)
        
        // 移除注册的快捷键
        com.luaforge.studio.lxclua.plugin.bridge.PluginShortcut.removeAllPluginShortcuts(pluginId)
        
        // 移除注册的语法高亮规则
        com.luaforge.studio.lxclua.plugin.bridge.PluginSyntax.removePluginLanguages(pluginId)
        
        // 移除编辑器装饰
        com.luaforge.studio.lxclua.plugin.bridge.PluginDecoration.removePluginDecorations(pluginId)
        // 移除装饰事件回调
        com.luaforge.studio.lxclua.plugin.bridge.PluginDecoration.removePluginCallbacks(pluginId)
        
        // 移除该插件的所有通知
        com.luaforge.studio.lxclua.plugin.state.NotificationState.clearPlugin(pluginId)
    }
    
    /**
     * 更新快捷操作列表
     */
    fun updateQuickActions() {
        pluginQuickActions.clear()
        pluginQuickActions.addAll(quickActionsMap.values)
    }
    
    /**
     * 添加快捷操作
     */
    fun addQuickAction(pluginId: String, key: String, action: QuickAction) {
        val globalKey = "${pluginId}_$key"
        quickActionsMap[globalKey] = action
        updateQuickActions()
    }
    
    /**
     * 移除快捷操作
     */
    fun removeQuickAction(pluginId: String, key: String) {
        val globalKey = "${pluginId}_$key"
        quickActionsMap.remove(globalKey)
        updateQuickActions()
    }
    
    /**
     * 清除插件的所有快捷操作
     */
    fun clearQuickActions(pluginId: String) {
        quickActionsMap.keys.removeAll { it.startsWith("${pluginId}_") }
        updateQuickActions()
    }
    
    /**
     * 启用/禁用插件
     */
    fun setPluginEnabled(context: Context, pluginId: String, enabled: Boolean) {
        val plugin = loadedPlugins.find { it.manifest.id == pluginId }
        if (plugin != null) {
            if (enabled) {
                // 检查缺失的必需依赖并触发事件
                val missing = getMissingDependencies(pluginId)
                if (missing.isNotEmpty()) {
                    EventManager.fireEvent(PluginEvents.ON_DEPENDENCY_MISSING,
                        pluginId, missing.joinToString(","))
                }
                // 级联启用：先启用所有必需依赖
                cascadeEnable(context, plugin.manifest)
            }
            
            plugin.enabled.value = enabled
            getPrefs(context).edit()
                .putBoolean(PREF_ENABLED_PREFIX + pluginId, enabled)
                .apply()
            
            if (enabled) {
                loadPluginInternal(plugin)
                EventManager.fireEvent(PluginEvents.ON_PLUGIN_ENABLED, pluginId)
            } else {
                unloadPluginInternal(plugin)
                EventManager.fireEvent(PluginEvents.ON_PLUGIN_DISABLED, pluginId)
            }
        }
    }

    /**
     * 级联启用插件的所有必需依赖
     * 递归处理：如果依赖的插件也有自己的依赖，同样级联启用
     */
    private fun cascadeEnable(context: Context, manifest: PluginManifest, depth: Int = 0) {
        if (depth > 10) return // 防止循环依赖导致无限递归
        for (dep in manifest.dependencies) {
            if (!dep.required) continue // 只处理必需依赖
            val depPlugin = loadedPlugins.find { it.manifest.id == dep.pluginId }
            if (depPlugin != null && !depPlugin.enabled.value) {
                // 先递归处理依赖的依赖
                cascadeEnable(context, depPlugin.manifest, depth + 1)
                depPlugin.enabled.value = true
                getPrefs(context).edit()
                    .putBoolean(PREF_ENABLED_PREFIX + dep.pluginId, true)
                    .apply()
                try {
                    loadPluginInternal(depPlugin)
                    EventManager.fireEvent(PluginEvents.ON_PLUGIN_ENABLED, dep.pluginId)
                } catch (e: Exception) {
                    android.util.Log.e("PluginManager", "级联启用依赖失败: ${dep.pluginId}", e)
                }
            }
        }
    }
    
    /**
     * 触发事件
     */
    fun fireEvent(eventName: String, vararg args: Any?) {
        EventManager.fireEvent(eventName, *args)
    }
    
    /**
     * 通知事件（与 fireEvent 功能相同，为了兼容旧代码）
     */
    fun notifyEvent(eventName: String, vararg args: Any?) {
        EventManager.fireEvent(eventName, *args)
    }
    
    /**
     * 动态切换插件启用状态
     */
    fun togglePlugin(context: Context, pluginId: String, enabled: Boolean) {
        val index = loadedPlugins.indexOfFirst { it.manifest.id == pluginId }
        if (index != -1) {
            val plugin = loadedPlugins[index]

            getPrefs(context).edit()
                .putBoolean(PREF_ENABLED_PREFIX + pluginId, enabled)
                .apply()

            if (enabled) {
                loadPluginInternal(plugin)
                // 检查加载是否成功：失败则还原启用状态
                if (plugin.loadError != null) {
                    android.util.Log.e("PluginManager", "动态启用插件失败: $pluginId - ${plugin.loadError}")
                    // 还原为未启用
                    plugin.enabled.value = false
                    getPrefs(context).edit()
                        .putBoolean(PREF_ENABLED_PREFIX + pluginId, false)
                        .apply()
                    return
                }
                EventManager.fireEvent(PluginEvents.ON_PLUGIN_ENABLED, pluginId)
            } else {
                unloadPluginInternal(plugin)
                EventManager.fireEvent(PluginEvents.ON_PLUGIN_DISABLED, pluginId)
            }

            // 直接修改 MutableState 值，触发 Compose 细粒度重组，避免全量 item 替换导致的 Switch 闪烁
            plugin.enabled.value = enabled
        }
    }
    
    /**
     * 彻底删除插件
     */
    fun deletePlugin(context: Context, pluginId: String): Boolean {
        val index = loadedPlugins.indexOfFirst { it.manifest.id == pluginId }
        if (index != -1) {
            val plugin = loadedPlugins[index]
            unloadPluginInternal(plugin)
            
            val success = plugin.directory.deleteRecursively()
            
            getPrefs(context).edit()
                .remove(PREF_ENABLED_PREFIX + pluginId)
                .apply()
                
            scanPlugins(context)
            
            // 触发插件卸载完成事件
            EventManager.fireEvent(PluginEvents.ON_PLUGIN_UNINSTALL, pluginId)
            
            return success
        }
        return false
    }
    
    /**
     * 从 Zip 文件解压并安装插件
     */
    fun installPluginFromZip(context: Context, zipFile: File): Result<PluginManifest> {
        try {
            val tempDir = File(context.cacheDir, "temp_plugin_extract_${UUID.randomUUID()}")
            tempDir.mkdirs()
            
            val extractOk = com.luaforge.studio.lxclua.utils.FileUtil.extractZip(zipFile, tempDir)
            if (!extractOk) {
                tempDir.deleteRecursively()
                return Result.failure(Exception(
                    context.getString(R.string.plugin_zip_extract_failed)
                ))
            }
            
            var manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                val children = tempDir.listFiles()
                val nestedDir = children?.find { it.isDirectory && File(it, "manifest.json").exists() }
                if (nestedDir != null) {
                    manifestFile = File(nestedDir, "manifest.json")
                } else {
                    return Result.failure(Exception(
                        context.getString(R.string.plugin_zip_no_manifest)
                    ))
                }
            }
            
            val content = manifestFile.readText()
            val manifest = Gson().fromJson(content, PluginManifest::class.java)
            
            val pluginsDir = getPluginsDir(context)
            val destDir = File(pluginsDir, manifest.id)
            
            if (destDir.exists()) {
                val preCheck = preCheckInstall(manifest)
                EventManager.fireEvent(PluginEvents.ON_INSTALL_VERSION_CONFLICT,
                    manifest.id, preCheck.existingVersion ?: "?", manifest.version,
                    preCheck.isUpdate, preCheck.isDowngrade.toString())
                togglePlugin(context, manifest.id, false)
                getPrefs(context).edit().remove(PREF_ENABLED_PREFIX + manifest.id).apply()
            }
            
            destDir.deleteRecursively()
            val sourceDir = manifestFile.parentFile ?: tempDir
            sourceDir.copyRecursively(destDir, overwrite = true)
            tempDir.deleteRecursively()
            
            scanPlugins(context)
            
            // 触发插件安装成功事件
            EventManager.fireEvent(PluginEvents.ON_PLUGIN_INSTALL, manifest.id)
            
            return Result.success(manifest)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * 从目录导入插件（复制到 plugins 目录）
     */
    fun installPluginFromDirectory(context: Context, sourceDir: File): Result<PluginManifest> {
        try {
            val manifestFile = File(sourceDir, "manifest.json")
            if (!manifestFile.exists()) {
                return Result.failure(Exception(
                    context.getString(R.string.plugin_dir_no_manifest)
                ))
            }
            
            val content = manifestFile.readText()
            val manifest = Gson().fromJson(content, PluginManifest::class.java)
            
            val pluginsDir = getPluginsDir(context)
            val destDir = File(pluginsDir, manifest.id)
            
            if (destDir.exists()) {
                val preCheck = preCheckInstall(manifest)
                EventManager.fireEvent(PluginEvents.ON_INSTALL_VERSION_CONFLICT,
                    manifest.id, preCheck.existingVersion ?: "?", manifest.version,
                    preCheck.isUpdate, preCheck.isDowngrade.toString())
                togglePlugin(context, manifest.id, false)
                getPrefs(context).edit().remove(PREF_ENABLED_PREFIX + manifest.id).apply()
            }
            
            destDir.deleteRecursively()
            sourceDir.copyRecursively(destDir, overwrite = true)
            
            scanPlugins(context)
            
            // 触发插件安装成功事件
            EventManager.fireEvent(PluginEvents.ON_PLUGIN_INSTALL, manifest.id)
            
            return Result.success(manifest)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * 导出插件为 ZIP 文件
     */
    fun exportPluginToZip(context: Context, pluginId: String, destZipFile: File): Boolean {
        val plugin = loadedPlugins.find { it.manifest.id == pluginId } ?: return false
        return com.luaforge.studio.lxclua.utils.FileUtil.createZip(plugin.directory, destZipFile) { file ->
            file.isDirectory && file.name == "logs"
        }
    }
    
    /**
     * 导出插件到指定目录
     */
    fun exportPluginToDirectory(context: Context, pluginId: String, destDir: File): Boolean {
        val plugin = loadedPlugins.find { it.manifest.id == pluginId } ?: return false
        return try {
            val targetDir = File(destDir, plugin.manifest.id)
            targetDir.deleteRecursively()
            plugin.directory.copyRecursively(targetDir, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 更新插件 manifest.json
     */
    fun updatePluginManifest(context: Context, pluginId: String, newManifest: PluginManifest): Boolean {
        val plugin = loadedPlugins.find { it.manifest.id == pluginId } ?: return false
        return try {
            val manifestFile = File(plugin.directory, "manifest.json")
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            manifestFile.writeText(gson.toJson(newManifest))
            scanPlugins(context)
            EventManager.fireEvent(PluginEvents.ON_PLUGIN_PROPERTY_CHANGED,
                pluginId, gson.toJson(mapOf("name" to newManifest.name,
                    "description" to newManifest.description,
                    "author" to newManifest.author,
                    "homepage" to (newManifest.homepage ?: ""),
                    "tags" to newManifest.tags.joinToString(","))))
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 依赖状态检查 ====================

    /**
     * 依赖检查结果
     */
    data class DependencyStatus(
        val pluginId: String,
        val required: Boolean,
        val exists: Boolean,
        val isEnabled: Boolean,
        val installedVersion: String?,
        val minVersion: String,
        val versionMatch: Boolean
    )

    /**
     * 检查插件的所有依赖状态
     * @return 每个依赖的详细状态列表
     */
    fun checkDependencyStatus(pluginId: String): List<DependencyStatus> {
        val plugin = loadedPlugins.find { it.manifest.id == pluginId } ?: return emptyList()
        return plugin.manifest.dependencies.map { dep ->
            val depPlugin = loadedPlugins.find { it.manifest.id == dep.pluginId }
            val installedVer = depPlugin?.manifest?.version
            val exists = depPlugin != null
            val verMatch = if (installedVer != null) {
                compareVersions(installedVer, dep.minVersion) >= 0
            } else false
            DependencyStatus(
                pluginId = dep.pluginId,
                required = dep.required,
                exists = exists,
                isEnabled = depPlugin?.enabled?.value ?: false,
                installedVersion = installedVer,
                minVersion = dep.minVersion,
                versionMatch = verMatch
            )
        }
    }

    /**
     * 获取插件的缺失依赖 ID 列表
     */
    fun getMissingDependencies(pluginId: String): List<String> {
        return checkDependencyStatus(pluginId)
            .filter { it.required && !it.exists }
            .map { it.pluginId }
    }

    /**
     * 简单版本比较：返回 1 表示 v1 > v2，-1 表示 v1 < v2，0 表示相等
     */
    fun compareVersions(v1: String, v2: String): Int {
        try {
            val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        } catch (e: Exception) {
            return v1.compareTo(v2)
        }
    }

    // ==================== 安装版本比对 ====================

    /**
     * 安装版本检查结果
     */
    data class InstallPreCheck(
        val alreadyExists: Boolean,
        val existingVersion: String?,
        val newVersion: String,
        val isSameVersion: Boolean,
        val isUpdate: Boolean,       // 是升级（新 > 旧）
        val isDowngrade: Boolean     // 是降级（新 < 旧）
    )

    /**
     * 安装前版本预检（不执行安装）
     */
    fun preCheckInstall(manifest: PluginManifest): InstallPreCheck {
        val existing = loadedPlugins.find { it.manifest.id == manifest.id }
        if (existing == null) {
            return InstallPreCheck(
                alreadyExists = false, existingVersion = null,
                newVersion = manifest.version, isSameVersion = false,
                isUpdate = false, isDowngrade = false
            )
        }
        val cmp = compareVersions(manifest.version, existing.manifest.version)
        return InstallPreCheck(
            alreadyExists = true,
            existingVersion = existing.manifest.version,
            newVersion = manifest.version,
            isSameVersion = cmp == 0,
            isUpdate = cmp > 0,
            isDowngrade = cmp < 0
        )
    }

    // ==================== 属性动态修改 ====================

    /**
     * 更新插件名称（动态修改，持久化到 manifest.json）
     */
    fun updatePluginName(context: Context, pluginId: String, newName: String): Boolean {
        val manifest = loadedPlugins.find { it.manifest.id == pluginId }?.manifest ?: return false
        val updated = manifest.copy(name = newName)
        return updatePluginManifest(context, pluginId, updated)
    }

    /**
     * 更新插件描述
     */
    fun updatePluginDescription(context: Context, pluginId: String, newDescription: String): Boolean {
        val manifest = loadedPlugins.find { it.manifest.id == pluginId }?.manifest ?: return false
        val updated = manifest.copy(description = newDescription)
        return updatePluginManifest(context, pluginId, updated)
    }

    /**
     * 更新插件作者
     */
    fun updatePluginAuthor(context: Context, pluginId: String, newAuthor: String): Boolean {
        val manifest = loadedPlugins.find { it.manifest.id == pluginId }?.manifest ?: return false
        val updated = manifest.copy(author = newAuthor)
        return updatePluginManifest(context, pluginId, updated)
    }

    /**
     * 更新插件主页
     */
    fun updatePluginHomepage(context: Context, pluginId: String, newHomepage: String?): Boolean {
        val manifest = loadedPlugins.find { it.manifest.id == pluginId }?.manifest ?: return false
        val updated = manifest.copy(homepage = newHomepage)
        return updatePluginManifest(context, pluginId, updated)
    }

    /**
     * 更新插件标签
     */
    fun updatePluginTags(context: Context, pluginId: String, newTags: List<String>): Boolean {
        val manifest = loadedPlugins.find { it.manifest.id == pluginId }?.manifest ?: return false
        val updated = manifest.copy(tags = newTags)
        return updatePluginManifest(context, pluginId, updated)
    }
}
