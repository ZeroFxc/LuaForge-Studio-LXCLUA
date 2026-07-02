package com.luaforge.studio.lxclua.plugin.state

import com.luaforge.studio.lxclua.plugin.api.callbacks.IPluginEventListener
import com.luajava.LuaFunction
import com.luajava.LuaTable

/**
 * 插件生命周期事件常量
 */
object PluginEvents {
    // ========== 编辑器相关事件 ==========
    const val ON_FILE_OPEN = "onFileOpen"
    const val ON_FILE_SAVE = "onFileSave"
    const val ON_FILE_CLOSE = "onFileClose"
    const val ON_TEXT_CHANGED = "onTextChanged"
    const val ON_EDITOR_INIT = "onEditorInit"
    const val ON_EDITOR_CLOSE = "onEditorClose"
    /** 光标位置变化，参数: (filePath: String, line: Int, column: Int) */
    const val ON_CURSOR_MOVED = "onCursorMoved"
    /** 选择范围变化，参数: (filePath: String, startLine:Int, startCol:Int, endLine:Int, endCol:Int, selectedText:String) */
    const val ON_SELECTION_CHANGED = "onSelectionChanged"
    /** 文件切换（标签页切换），参数: (newFilePath: String?, oldFilePath: String?) */
    const val ON_FILE_SWITCHED = "onFileSwitched"
    /** 编辑器内容滚动，参数: (filePath: String, firstVisibleLine: Int, visibleLineCount: Int) */
    const val ON_EDITOR_SCROLL = "onEditorScroll"

    // ========== 插件生命周期事件 ==========
    const val ON_PLUGIN_LOADED = "onPluginLoaded"
    const val ON_PLUGIN_UNLOADED = "onPluginUnloaded"
    const val ON_PLUGIN_ENABLED = "onPluginEnabled"
    const val ON_PLUGIN_DISABLED = "onPluginDisabled"
    const val ON_ALL_PLUGINS_LOADED = "onAllPluginsLoaded"
    /** 插件安装成功，参数: (pluginId: String) */
    const val ON_PLUGIN_INSTALL = "onPluginInstall"
    /** 插件卸载完成，参数: (pluginId: String) */
    const val ON_PLUGIN_UNINSTALL = "onPluginUninstall"

    // ========== 应用生命周期事件 ==========
    const val ON_APP_START = "onAppStart"
    const val ON_APP_RESUME = "onAppResume"
    const val ON_APP_PAUSE = "onAppPause"
    const val ON_APP_STOP = "onAppStop"
    /** 应用销毁，参数: 无 */
    const val ON_APP_DESTROY = "onAppDestroy"

    // ========== 项目生命周期事件 ==========
    /** 项目创建完成，参数: (projectId: String, projectName: String, projectPath: String) */
    const val ON_PROJECT_CREATE = "onProjectCreate"
    /** 项目删除完成，参数: (projectId: String, projectName: String, projectPath: String) */
    const val ON_PROJECT_DELETE = "onProjectDelete"
    /** 项目重命名完成，参数: (projectId: String, oldName: String, newName: String, projectPath: String) */
    const val ON_PROJECT_RENAME = "onProjectRename"
    /** 项目打开（进入编辑器前），参数: (projectId: String, projectName: String, projectPath: String) */
    const val ON_PROJECT_OPEN = "onProjectOpen"
    /** 项目备份完成，参数: (projectId: String, backupPath: String, success: Boolean) */
    const val ON_PROJECT_BACKUP = "onProjectBackup"
    /** 项目恢复完成，参数: (projectId: String, backupPath: String, success: Boolean) */
    const val ON_PROJECT_RESTORE = "onProjectRestore"
    /** 新建项目完成（从新建项目页面），参数: (projectName: String, projectPath: String, templateId: String) */
    const val ON_NEW_PROJECT = "onNewProject"
    /** 项目标签添加，参数: (projectId: String, tag: String) */
    const val ON_PROJECT_TAG_ADDED = "onProjectTagAdded"
    /** 项目标签移除，参数: (projectId: String, tag: String) */
    const val ON_PROJECT_TAG_REMOVED = "onProjectTagRemoved"
    /** 项目封面变更，参数: (projectId: String, coverType: String, coverValue: String) */
    const val ON_PROJECT_COVER_CHANGED = "onProjectCoverChanged"
    /** 项目置顶状态变化，参数: (projectId: String, pinned: Boolean) */
    const val ON_PROJECT_PIN_CHANGED = "onProjectPinChanged"

    // ========== 文件操作事件 ==========
    /** 文件新建完成，参数: (filePath: String, isDirectory: Boolean) */
    const val ON_FILE_CREATED = "onFileCreated"
    /** 文件重命名完成，参数: (oldPath: String, newPath: String, isDirectory: Boolean) */
    const val ON_FILE_RENAMED = "onFileRenamed"
    /** 文件删除完成，参数: (filePath: String, isDirectory: Boolean) */
    const val ON_FILE_DELETED = "onFileDeleted"
    /** 文件导入完成，参数: (filePath: String, sourceUri: String) */
    const val ON_FILE_IMPORTED = "onFileImported"

    // ========== 主页项目列表事件 ==========
    const val ON_PROJECT_LONG_PRESS = "onProjectLongPress"
    const val ON_PROJECT_CLICK = "onProjectClick"
    const val ON_PROJECT_SWIPE_LEFT = "onProjectSwipeLeft"
    const val ON_PROJECT_SWIPE_RIGHT = "onProjectSwipeRight"
    /** 搜索文本变化，参数: (query: String) */
    const val ON_SEARCH_QUERY_CHANGED = "onSearchQueryChanged"
    /** 排序方式变化，参数: (sortOrder: String) */
    const val ON_SORT_ORDER_CHANGED = "onSortOrderChanged"
    /** 分类切换，参数: (categoryId: String?) */
    const val ON_CATEGORY_CHANGED = "onCategoryChanged"
    /** 多选模式进入，参数: 无 */
    const val ON_MULTI_SELECT_ENTER = "onMultiSelectEnter"
    /** 多选模式退出，参数: 无 */
    const val ON_MULTI_SELECT_EXIT = "onMultiSelectExit"
    /** 多选项目变化，参数: (selectedCount: Int, selectedIdsJson: String) */
    const val ON_MULTI_SELECTION_CHANGED = "onMultiSelectionChanged"
    /** 下拉刷新，参数: 无 */
    const val ON_PULL_TO_REFRESH = "onPullToRefresh"
    /** 分类创建，参数: (categoryId: String, categoryName: String) */
    const val ON_CATEGORY_CREATED = "onCategoryCreated"
    /** 分类删除，参数: (categoryId: String) */
    const val ON_CATEGORY_DELETED = "onCategoryDeleted"

    // ========== 设置变更事件 ==========
    /** 设置保存后触发，参数: (changedFieldsJson: String) 为当前完整设置的 JSON */
    const val ON_SETTINGS_CHANGED = "onSettingsChanged"

    // ========== 页面导航事件 ==========
    /** 页面切换事件，参数: (pageId: String, fromPageId: String) pageId: main/new_project/editor/settings/about/plugins/webui */
    const val ON_PAGE_CHANGED = "onPageChanged"

    // ========== UI 交互事件 ==========
    const val ON_BACK_PRESSED = "onBackPressed"
    /** 主题/深色模式变更，参数: (darkMode: String) darkMode: light/dark/system */
    const val ON_THEME_CHANGED = "onThemeChanged"
    /** 语言变更，参数: (languageCode: String) */
    const val ON_LANGUAGE_CHANGED = "onLanguageChanged"
    /** Toast显示，参数: (message: String, type: String) type: normal/success/warn/error，可拦截 */
    const val ON_TOAST_SHOWN = "onToastShown"

    // ========== 构建事件 ==========
    /** 构建开始，参数: (projectPath: String, buildType: String) buildType 为 "project" 或 "compile" */
    const val ON_BUILD_START = "onBuildStart"
    /** 构建完成，参数: (projectPath: String, result: String, success: Boolean) */
    const val ON_BUILD_FINISH = "onBuildFinish"
    /** 构建出错，参数: (projectPath: String, errorMessage: String, buildType: String) */
    const val ON_BUILD_ERROR = "onBuildError"
    /** 编译/构建输出，参数: (projectPath: String, message: String, level: String) level: info/warn/error */
    const val ON_BUILD_OUTPUT = "onBuildOutput"
    /** 代码分析完成，参数: (filePath: String, diagnosticsJson: String) */
    const val ON_ANALYZE_COMPLETE = "onAnalyzeComplete"

    // ========== 依赖事件 ==========
    /** 依赖缺失，启用插件时必需依赖不满足，参数: (pluginId: String, missingDepsCsv: String) */
    const val ON_DEPENDENCY_MISSING = "onDependencyMissing"

    // ========== 属性变更事件 ==========
    /** 插件属性变更，updatePluginName/Description 等成功后，参数: (pluginId: String, changedFieldsJson: String) */
    const val ON_PLUGIN_PROPERTY_CHANGED = "onPluginPropertyChanged"

    // ========== 安装事件 ==========
    /** 安装版本冲突，参数: (pluginId, existingVersion, newVersion, isUpdate, isDowngrade) */
    const val ON_INSTALL_VERSION_CONFLICT = "onInstallVersionConflict"

    // ========== 插件卡片交互事件 ==========
    /** 插件管理页面中卡片被单击，参数: (pluginId: String) */
    const val ON_PLUGIN_CARD_CLICK = "onPluginCardClick"

    // ========== 插件系统事件 ==========
    /** 自定义事件广播（插件间通信用），参数: (senderPluginId: String, eventName: String, dataJson: String) */
    const val ON_CUSTOM_EVENT = "onCustomEvent"
    /** 插件消息（插件间点对点通信），参数: (fromPluginId: String, toPluginId: String, action: String, dataJson: String) */
    const val ON_PLUGIN_MESSAGE = "onPluginMessage"
}

/**
 * 带插件ID的监听器包装
 */
data class ListenerEntry(
    val pluginId: String,
    val listener: Any
)

/**
 * 一次性监听器包装，首次回调后自动注销
 */
private data class OnceListenerEntry(
    val pluginId: String,
    val eventName: String,
    val listener: Any
)

/**
 * 拦截器条目
 * @property pluginId 注册该拦截器的插件ID
 * @property priority 优先级，数字越小优先级越高
 * @property handler 拦截处理函数，返回true表示拦截事件（阻止默认行为），返回false表示放行
 */
data class InterceptorEntry(
    val pluginId: String,
    val priority: Int,
    val handler: (eventName: String, args: Array<out Any?>) -> Boolean
)

/**
 * 自定义事件注册信息
 */
data class EventInfo(
    val pluginId: String,
    val eventName: String,
    val description: String,
    val isCustom: Boolean = true
)

/**
 * 插件消息处理器
 */
private data class MessageHandlerEntry(
    val pluginId: String,
    val handler: (fromId: String, action: String, data: String) -> Unit
)

/**
 * 事件管理器
 * 负责管理插件的事件订阅、拦截和触发
 */
object EventManager {

    // 存储结构: eventName -> List<ListenerEntry>
    private val eventListeners = mutableMapOf<String, MutableList<ListenerEntry>>()

    // 一次性监听器存储
    private val onceListeners = mutableMapOf<String, MutableList<OnceListenerEntry>>()

    // 拦截器存储: eventName -> MutableList<InterceptorEntry>
    private val interceptors = mutableMapOf<String, MutableList<InterceptorEntry>>()

    // 自定义事件注册表
    private val customEvents = mutableMapOf<String, EventInfo>()

    // 插件消息处理器: pluginId -> handler
    private val messageHandlers = mutableMapOf<String, MessageHandlerEntry>()

    // 系统内置事件列表（用于getAllRegisteredEvents返回）
    private val systemEvents = listOf(
        EventInfo("system", PluginEvents.ON_FILE_OPEN, "文件打开", false),
        EventInfo("system", PluginEvents.ON_FILE_SAVE, "文件保存", false),
        EventInfo("system", PluginEvents.ON_FILE_CLOSE, "文件关闭", false),
        EventInfo("system", PluginEvents.ON_TEXT_CHANGED, "文本变化", false),
        EventInfo("system", PluginEvents.ON_EDITOR_INIT, "编辑器初始化", false),
        EventInfo("system", PluginEvents.ON_EDITOR_CLOSE, "编辑器关闭", false),
        EventInfo("system", PluginEvents.ON_CURSOR_MOVED, "光标移动", false),
        EventInfo("system", PluginEvents.ON_SELECTION_CHANGED, "选择变化", false),
        EventInfo("system", PluginEvents.ON_FILE_SWITCHED, "文件切换", false),
        EventInfo("system", PluginEvents.ON_EDITOR_SCROLL, "编辑器滚动", false),
        EventInfo("system", PluginEvents.ON_PLUGIN_LOADED, "插件加载", false),
        EventInfo("system", PluginEvents.ON_PLUGIN_UNLOADED, "插件卸载", false),
        EventInfo("system", PluginEvents.ON_PLUGIN_ENABLED, "插件启用", false),
        EventInfo("system", PluginEvents.ON_PLUGIN_DISABLED, "插件禁用", false),
        EventInfo("system", PluginEvents.ON_ALL_PLUGINS_LOADED, "所有插件加载完成", false),
        EventInfo("system", PluginEvents.ON_PLUGIN_INSTALL, "插件安装", false),
        EventInfo("system", PluginEvents.ON_PLUGIN_UNINSTALL, "插件卸载完成", false),
        EventInfo("system", PluginEvents.ON_APP_START, "应用启动", false),
        EventInfo("system", PluginEvents.ON_APP_RESUME, "应用恢复", false),
        EventInfo("system", PluginEvents.ON_APP_PAUSE, "应用暂停", false),
        EventInfo("system", PluginEvents.ON_APP_STOP, "应用停止", false),
        EventInfo("system", PluginEvents.ON_APP_DESTROY, "应用销毁", false),
        EventInfo("system", PluginEvents.ON_PROJECT_CREATE, "项目创建", false),
        EventInfo("system", PluginEvents.ON_PROJECT_DELETE, "项目删除", false),
        EventInfo("system", PluginEvents.ON_PROJECT_RENAME, "项目重命名", false),
        EventInfo("system", PluginEvents.ON_PROJECT_OPEN, "项目打开", false),
        EventInfo("system", PluginEvents.ON_PROJECT_BACKUP, "项目备份", false),
        EventInfo("system", PluginEvents.ON_PROJECT_RESTORE, "项目恢复", false),
        EventInfo("system", PluginEvents.ON_NEW_PROJECT, "新建项目", false),
        EventInfo("system", PluginEvents.ON_PROJECT_TAG_ADDED, "标签添加", false),
        EventInfo("system", PluginEvents.ON_PROJECT_TAG_REMOVED, "标签移除", false),
        EventInfo("system", PluginEvents.ON_PROJECT_COVER_CHANGED, "封面变更", false),
        EventInfo("system", PluginEvents.ON_PROJECT_PIN_CHANGED, "置顶状态变化", false),
        EventInfo("system", PluginEvents.ON_FILE_CREATED, "文件新建", false),
        EventInfo("system", PluginEvents.ON_FILE_RENAMED, "文件重命名", false),
        EventInfo("system", PluginEvents.ON_FILE_DELETED, "文件删除", false),
        EventInfo("system", PluginEvents.ON_FILE_IMPORTED, "文件导入", false),
        EventInfo("system", PluginEvents.ON_PROJECT_LONG_PRESS, "项目长按", false),
        EventInfo("system", PluginEvents.ON_PROJECT_CLICK, "项目点击", false),
        EventInfo("system", PluginEvents.ON_PROJECT_SWIPE_LEFT, "项目左滑", false),
        EventInfo("system", PluginEvents.ON_PROJECT_SWIPE_RIGHT, "项目右滑", false),
        EventInfo("system", PluginEvents.ON_SEARCH_QUERY_CHANGED, "搜索文本变化", false),
        EventInfo("system", PluginEvents.ON_SORT_ORDER_CHANGED, "排序方式变化", false),
        EventInfo("system", PluginEvents.ON_CATEGORY_CHANGED, "分类切换", false),
        EventInfo("system", PluginEvents.ON_MULTI_SELECT_ENTER, "多选模式进入", false),
        EventInfo("system", PluginEvents.ON_MULTI_SELECT_EXIT, "多选模式退出", false),
        EventInfo("system", PluginEvents.ON_MULTI_SELECTION_CHANGED, "多选项目变化", false),
        EventInfo("system", PluginEvents.ON_PULL_TO_REFRESH, "下拉刷新", false),
        EventInfo("system", PluginEvents.ON_CATEGORY_CREATED, "分类创建", false),
        EventInfo("system", PluginEvents.ON_CATEGORY_DELETED, "分类删除", false),
        EventInfo("system", PluginEvents.ON_SETTINGS_CHANGED, "设置变更", false),
        EventInfo("system", PluginEvents.ON_PAGE_CHANGED, "页面切换", false),
        EventInfo("system", PluginEvents.ON_BACK_PRESSED, "返回键按下", false),
        EventInfo("system", PluginEvents.ON_THEME_CHANGED, "主题变更", false),
        EventInfo("system", PluginEvents.ON_LANGUAGE_CHANGED, "语言变更", false),
        EventInfo("system", PluginEvents.ON_TOAST_SHOWN, "Toast显示", false),
        EventInfo("system", PluginEvents.ON_BUILD_START, "构建开始", false),
        EventInfo("system", PluginEvents.ON_BUILD_FINISH, "构建完成", false),
        EventInfo("system", PluginEvents.ON_BUILD_ERROR, "构建错误", false),
        EventInfo("system", PluginEvents.ON_BUILD_OUTPUT, "构建输出", false),
        EventInfo("system", PluginEvents.ON_ANALYZE_COMPLETE, "代码分析完成", false),
        EventInfo("system", PluginEvents.ON_DEPENDENCY_MISSING, "依赖缺失", false),
        EventInfo("system", PluginEvents.ON_PLUGIN_PROPERTY_CHANGED, "插件属性变更", false),
        EventInfo("system", PluginEvents.ON_INSTALL_VERSION_CONFLICT, "安装版本冲突", false),
        EventInfo("system", PluginEvents.ON_PLUGIN_CARD_CLICK, "插件卡片点击", false),
        EventInfo("system", PluginEvents.ON_CUSTOM_EVENT, "自定义事件", false),
        EventInfo("system", PluginEvents.ON_PLUGIN_MESSAGE, "插件消息", false)
    ).associateBy { it.eventName }.toMutableMap()

    /**
     * 注册事件监听器
     * @param pluginId 插件ID，用于卸载时清理
     * @param eventName 事件名称
     * @param listener 监听器对象（支持 IPluginEventListener, LuaFunction, LuaTable）
     */
    fun registerEventListener(pluginId: String, eventName: String, listener: Any) {
        eventListeners.getOrPut(eventName) { mutableListOf() }.add(ListenerEntry(pluginId, listener))
    }

    /**
     * 简化版注册（兼容旧代码）
     */
    fun registerEventListener(eventName: String, listener: Any) {
        eventListeners.getOrPut(eventName) { mutableListOf() }.add(ListenerEntry("", listener))
    }

    /**
     * 注册一次性事件监听器，触发一次后自动注销
     * @param pluginId 插件ID
     * @param eventName 事件名称
     * @param listener 监听器对象
     */
    fun registerOnceListener(pluginId: String, eventName: String, listener: Any) {
        onceListeners.getOrPut(eventName) { mutableListOf() }.add(OnceListenerEntry(pluginId, eventName, listener))
    }

    /**
     * 取消事件监听
     */
    fun unregisterEventListener(eventName: String, listener: Any) {
        eventListeners[eventName]?.removeIf { it.listener === listener }
        onceListeners[eventName]?.removeIf { it.listener === listener }
    }

    /**
     * 注册事件拦截器
     * @param pluginId 插件ID，用于卸载时清理
     * @param eventName 要拦截的事件名称
     * @param priority 优先级，数字越小优先级越高，默认为0
     * @param handler 拦截处理函数，参数为(eventName, args)，返回true表示拦截（阻止默认行为），false表示放行
     */
    fun registerInterceptor(
        pluginId: String,
        eventName: String,
        priority: Int = 0,
        handler: (eventName: String, args: Array<out Any?>) -> Boolean
    ) {
        interceptors.getOrPut(eventName) { mutableListOf() }.add(InterceptorEntry(pluginId, priority, handler))
    }

    /**
     * 移除指定插件的所有拦截器（插件卸载时调用）
     * @param pluginId 插件ID
     */
    fun unregisterPluginInterceptors(pluginId: String) {
        interceptors.values.forEach { entries ->
            entries.removeIf { it.pluginId == pluginId }
        }
    }

    /**
     * 注册自定义事件（插件可注册自己的事件名到系统，方便发现）
     * @param pluginId 插件ID
     * @param eventName 自定义事件名
     * @param description 事件描述（用于插件开发者文档）
     */
    fun registerCustomEvent(pluginId: String, eventName: String, description: String = "") {
        customEvents[eventName] = EventInfo(pluginId, eventName, description, true)
    }

    /**
     * 获取所有已注册事件名（含系统事件和自定义事件）
     * @return 事件信息列表
     */
    fun getAllRegisteredEvents(): List<EventInfo> {
        val all = mutableListOf<EventInfo>()
        all.addAll(systemEvents.values)
        all.addAll(customEvents.values)
        return all
    }

    /**
     * 发送插件间消息
     * @param fromPluginId 发送者插件ID
     * @param toPluginId 目标插件ID（空字符串表示广播给所有插件）
     * @param action 动作标识
     * @param dataJson 数据JSON
     */
    fun sendPluginMessage(fromPluginId: String, toPluginId: String, action: String, dataJson: String = "{}") {
        if (toPluginId.isEmpty()) {
            // 广播给所有插件
            messageHandlers.values.forEach { entry ->
                try {
                    entry.handler(fromPluginId, action, dataJson)
                } catch (e: Exception) {
                    android.util.Log.e("EventManager", "发送插件广播消息到 ${entry.pluginId} 失败", e)
                }
            }
            // 同时触发ON_PLUGIN_MESSAGE事件（用于全局监听）
            fireEvent(PluginEvents.ON_PLUGIN_MESSAGE, fromPluginId, toPluginId, action, dataJson)
        } else {
            // 点对点消息
            val handler = messageHandlers[toPluginId]
            if (handler != null) {
                try {
                    handler.handler(fromPluginId, action, dataJson)
                } catch (e: Exception) {
                    android.util.Log.e("EventManager", "发送插件消息到 $toPluginId 失败", e)
                }
            }
            // 触发ON_PLUGIN_MESSAGE事件
            fireEvent(PluginEvents.ON_PLUGIN_MESSAGE, fromPluginId, toPluginId, action, dataJson)
        }
    }

    /**
     * 注册插件消息接收器
     * @param pluginId 接收消息的插件ID
     * @param handler 消息处理函数 (fromId, action, data) -> Unit
     */
    fun registerPluginMessageHandler(pluginId: String, handler: (fromId: String, action: String, data: String) -> Unit) {
        messageHandlers[pluginId] = MessageHandlerEntry(pluginId, handler)
    }

    /**
     * 注销插件消息接收器
     */
    fun unregisterPluginMessageHandler(pluginId: String) {
        messageHandlers.remove(pluginId)
    }

    /**
     * 触发自定义事件（广播给所有监听者）
     * @param senderPluginId 发送者插件ID
     * @param eventName 自定义事件名
     * @param dataJson 数据JSON
     */
    fun fireCustomEvent(senderPluginId: String, eventName: String, dataJson: String = "{}") {
        fireEvent(PluginEvents.ON_CUSTOM_EVENT, senderPluginId, eventName, dataJson)
        // 也直接触发该事件名（让直接监听该自定义事件名的插件也能收到）
        fireEvent(eventName, senderPluginId, dataJson)
    }

    /**
     * 带拦截检查的事件触发
     * 先按优先级顺序执行所有拦截器，然后始终执行普通监听器（含一次性监听器）。
     * @param eventName 事件名称
     * @param args 事件参数
     * @return true表示事件被拦截（调用方应阻止默认行为），false表示未被拦截
     */
    fun fireEventWithIntercept(eventName: String, vararg args: Any?): Boolean {
        // 1. 执行拦截器（按priority升序，数字越小优先级越高）
        var intercepted = false
        val interceptorList = interceptors[eventName]?.sortedBy { it.priority } ?: emptyList()
        for (entry in interceptorList) {
            try {
                if (entry.handler(eventName, args)) {
                    intercepted = true
                    // 注意：不提前返回，所有拦截器都要执行（参考cancelBuild模式，让所有插件都有机会响应）
                }
            } catch (e: Exception) {
                android.util.Log.e("EventManager", "执行事件 $eventName 拦截器 ${entry.pluginId} 发生错误", e)
            }
        }

        // 2. 始终执行普通监听器（保持事件通知语义）
        notifyListeners(eventName, args)

        // 3. 执行一次性监听器，然后移除
        val onceList = onceListeners[eventName]?.toList() ?: emptyList()
        if (onceList.isNotEmpty()) {
            for (entry in onceList) {
                try {
                    invokeListener(entry.listener, args)
                } catch (e: Exception) {
                    android.util.Log.e("EventManager", "通知一次性事件 $eventName 到监听器 ${entry.pluginId} 发生错误", e)
                }
            }
            onceListeners[eventName]?.removeAll(onceList)
        }

        return intercepted
    }

    /**
     * 仅检查事件是否被拦截（不通知普通监听器，用于操作前确认）
     * @param eventName 事件名
     * @param args 事件参数
     * @return true表示拦截（应阻止默认操作），false表示未拦截
     */
    fun checkIntercepted(eventName: String, vararg args: Any?): Boolean {
        var intercepted = false
        val interceptorList = interceptors[eventName]?.sortedBy { it.priority } ?: emptyList()
        for (entry in interceptorList) {
            try {
                if (entry.handler(eventName, args)) {
                    intercepted = true
                }
            } catch (e: Exception) {
                android.util.Log.e("EventManager", "执行事件 $eventName 拦截器 ${entry.pluginId} 发生错误", e)
            }
        }
        return intercepted
    }

    /**
     * 触发事件（纯通知，保持向后兼容）
     * 内部会先执行拦截器再通知监听器，返回值被忽略（即不影响默认行为的场景使用此方法）
     */
    fun fireEvent(eventName: String, vararg args: Any?) {
        fireEventWithIntercept(eventName, *args)
    }

    /**
     * 通知普通监听器（内部方法）
     */
    private fun notifyListeners(eventName: String, args: Array<out Any?>) {
        val entries = eventListeners[eventName]?.toList() ?: return

        for (entry in entries) {
            try {
                invokeListener(entry.listener, args)
            } catch (e: Exception) {
                android.util.Log.e("EventManager", "通知事件 $eventName 到监听器 ${entry.pluginId} 发生错误", e)
            }
        }
    }

    /**
     * 调用单个监听器（支持 IPluginEventListener, LuaFunction, LuaTable）
     */
    private fun invokeListener(listener: Any, args: Array<out Any?>) {
        when (listener) {
            is LuaFunction<*> -> {
                listener.call(*args)
            }
            is LuaTable<*, *> -> {
                val onEvent = listener["onEvent"]
                if (onEvent is LuaFunction<*>) {
                    onEvent.call(*args)
                }
            }
            is IPluginEventListener -> {
                listener.onEvent(*args)
            }
        }
    }

    /**
     * 移除指定插件的所有事件监听器（包括普通监听器、一次性监听器、拦截器和消息处理器）
     */
    fun removePluginListeners(pluginId: String) {
        eventListeners.values.forEach { entries ->
            entries.removeIf { it.pluginId == pluginId }
        }
        onceListeners.values.forEach { entries ->
            entries.removeIf { it.pluginId == pluginId }
        }
        unregisterPluginInterceptors(pluginId)
        unregisterPluginMessageHandler(pluginId)
        // 移除该插件注册的自定义事件
        customEvents.values.removeIf { it.pluginId == pluginId }
    }

    /**
     * 获取指定事件的监听器数量
     */
    fun getListenerCount(eventName: String): Int {
        return (eventListeners[eventName]?.size ?: 0) + (onceListeners[eventName]?.size ?: 0)
    }

    /**
     * 获取指定事件的拦截器数量
     */
    fun getInterceptorCount(eventName: String): Int {
        return interceptors[eventName]?.size ?: 0
    }

    /**
     * 清空所有事件监听器和拦截器
     */
    fun clearAllListeners() {
        eventListeners.clear()
        onceListeners.clear()
        interceptors.clear()
        customEvents.clear()
        messageHandlers.clear()
    }
}
