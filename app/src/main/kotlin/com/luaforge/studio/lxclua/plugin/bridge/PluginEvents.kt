package com.luaforge.studio.lxclua.plugin.bridge

import com.luaforge.studio.lxclua.plugin.api.callbacks.IPluginEventListener
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.EventInfo

/**
 * 事件监听 API
 *
 * Lua 使用方式（注意: Java 对象/方法调用一律使用 `.` 而非 `:`）：
 * ```lua
 * -- 1. 注册普通监听器（Lua 函数）
 * plugin.events.on("onFileOpen", function(filePath)
 *     print("打开文件: " .. filePath)
 * end)
 *
 * -- 2. 注册一次性监听器
 * plugin.events.once("onAppStart", function()
 *     print("应用启动了，只会触发一次")
 * end)
 *
 * -- 3. 注册拦截器（返回 true 阻止默认行为）
 * plugin.events.intercept("onProjectDelete", function(args)
 *     local projectId = args[1]
 *     if projectId == "important-project" then
 *         print("禁止删除重要项目！")
 *         return true  -- 拦截删除操作
 *     end
 *     return false  -- 放行
 * end)
 *
 * -- 4. 注销监听器（需保存监听器引用）
 * local listener = function() print("event") end
 * plugin.events.on("onFileSave", listener)
 * plugin.events.off("onFileSave", listener)
 *
 * -- 5. 触发自定义事件
 * plugin.events.fire("myCustomEvent", "hello", 123)
 *
 * -- 6. 注册自定义事件（让其他插件可发现）
 * plugin.events.registerEvent("onMyPluginDataUpdate", "我的插件数据更新时触发")
 *
 * -- 7. 插件间消息通信（点对点）
 * plugin.events.sendMessage("target-plugin-id", "refresh", '{"reason":"data_changed"}')
 *
 * -- 8. 插件间消息广播
 * plugin.events.broadcastMessage("globalNotification", '{"level":"info"}')
 *
 * -- 9. 接收其他插件消息
 * plugin.events.onMessage(function(fromId, action, dataJson)
 *     print("收到来自 " .. fromId .. " 的消息: " .. action)
 * end)
 *
 * -- 10. 查询已注册事件列表
 * local events = plugin.events.getRegisteredEvents()
 * for i, ev in ipairs(events) do
 *     print(ev.eventName, ev.description, ev.pluginId)
 * end
 * ```
 */
class PluginEvents(private val pluginId: String) {

    // ============ 事件监听核心 API ============

    /**
     * 注册事件监听器（别名 on 更符合 Lua 习惯）
     * @param eventName 事件名称
     * @param listener 监听器（LuaFunction 或 带 onEvent 方法的 table）
     */
    fun register(eventName: String, listener: Any) {
        EventManager.registerEventListener(pluginId, eventName, listener)
    }

    /**
     * register 的别名，更符合 Lua/JS 事件命名习惯
     */
    fun on(eventName: String, listener: Any) = register(eventName, listener)

    /**
     * 注册一次性事件监听器，回调触发一次后自动注销
     * @param eventName 事件名称
     * @param handler 事件处理回调
     */
    fun once(eventName: String, handler: (Array<out Any?>) -> Unit) {
        val listener = object : IPluginEventListener {
            override fun onEvent(vararg args: Any?) {
                EventManager.unregisterEventListener(eventName, this)
                handler(args)
            }
        }
        EventManager.registerOnceListener(pluginId, eventName, listener)
    }

    /**
     * 注册事件拦截器
     * @param eventName 要拦截的事件名称
     * @param priority 优先级，数字越小优先级越高，默认0
     * @param handler 拦截处理函数，参数为事件参数数组，返回true表示拦截（阻止默认行为），false表示放行
     */
    fun intercept(eventName: String, priority: Int = 0, handler: (Array<out Any?>) -> Boolean) {
        EventManager.registerInterceptor(pluginId, eventName, priority) { _, args ->
            handler(args)
        }
    }

    /**
     * 取消事件监听（别名 off）
     */
    fun unregister(eventName: String, listener: Any) {
        EventManager.unregisterEventListener(eventName, listener)
    }

    /**
     * unregister 的别名
     */
    fun off(eventName: String, listener: Any) = unregister(eventName, listener)

    /**
     * 触发/广播事件
     * @param eventName 事件名称
     * @param args 事件参数
     */
    fun fire(eventName: String, vararg args: Any?) {
        EventManager.fireEvent(eventName, *args)
    }

    /**
     * fire 的别名（emit）
     */
    fun emit(eventName: String, vararg args: Any?) = fire(eventName, *args)

    /**
     * 带拦截检查的事件触发（返回true表示被拦截，调用方应阻止默认行为）
     * @param eventName 事件名称
     * @param args 事件参数
     * @return true=被拦截，false=未被拦截
     */
    fun fireWithIntercept(eventName: String, vararg args: Any?): Boolean {
        return EventManager.fireEventWithIntercept(eventName, *args)
    }

    /**
     * 仅检查是否被拦截（不通知监听器，用于操作前确认）
     */
    fun isIntercepted(eventName: String, vararg args: Any?): Boolean {
        return EventManager.checkIntercepted(eventName, *args)
    }

    // ============ 自定义事件注册与发现 ============

    /**
     * 注册自定义事件到系统（供其他插件发现）
     * @param eventName 自定义事件名
     * @param description 事件描述（用于文档）
     */
    fun registerEvent(eventName: String, description: String = "") {
        EventManager.registerCustomEvent(pluginId, eventName, description)
    }

    /**
     * 获取所有已注册事件列表（系统事件+自定义事件）
     * @return 事件信息列表，每项包含 eventName, description, pluginId, isCustom
     */
    fun getRegisteredEvents(): List<EventInfo> {
        return EventManager.getAllRegisteredEvents()
    }

    /**
     * 获取指定事件的监听器数量
     */
    fun getListenerCount(eventName: String): Int {
        return EventManager.getListenerCount(eventName)
    }

    /**
     * 获取指定事件的拦截器数量
     */
    fun getInterceptorCount(eventName: String): Int {
        return EventManager.getInterceptorCount(eventName)
    }

    // ============ 插件间消息通信 ============

    /**
     * 发送点对点消息给指定插件
     * @param toPluginId 目标插件ID
     * @param action 动作标识
     * @param dataJson 数据JSON字符串（默认空对象）
     */
    fun sendMessage(toPluginId: String, action: String, dataJson: String = "{}") {
        EventManager.sendPluginMessage(pluginId, toPluginId, action, dataJson)
    }

    /**
     * 广播消息给所有插件
     * @param action 动作标识
     * @param dataJson 数据JSON字符串
     */
    fun broadcastMessage(action: String, dataJson: String = "{}") {
        EventManager.sendPluginMessage(pluginId, "", action, dataJson)
    }

    /**
     * 注册消息接收器
     * @param handler 消息处理函数 (fromId: String, action: String, dataJson: String) -> Unit
     */
    fun onMessage(handler: (String, String, String) -> Unit) {
        EventManager.registerPluginMessageHandler(pluginId, handler)
    }

    // ============ 预定义事件常量 ============

    // ===== 编辑器相关事件 =====
    val ON_FILE_OPEN = "onFileOpen"
    val ON_FILE_SAVE = "onFileSave"
    val ON_FILE_CLOSE = "onFileClose"
    val ON_TEXT_CHANGED = "onTextChanged"
    val ON_EDITOR_INIT = "onEditorInit"
    val ON_EDITOR_CLOSE = "onEditorClose"
    /** 光标位置变化，参数: (filePath: String, line: Int, column: Int) */
    val ON_CURSOR_MOVED = "onCursorMoved"
    /** 选择范围变化，参数: (filePath, startLine, startCol, endLine, endCol, selectedText) */
    val ON_SELECTION_CHANGED = "onSelectionChanged"
    /** 文件切换（标签页切换），参数: (newFilePath: String?, oldFilePath: String?) */
    val ON_FILE_SWITCHED = "onFileSwitched"
    /** 编辑器内容滚动，参数: (filePath, firstVisibleLine, visibleLineCount) */
    val ON_EDITOR_SCROLL = "onEditorScroll"

    // ===== 插件生命周期事件 =====
    val ON_PLUGIN_LOADED = "onPluginLoaded"
    val ON_PLUGIN_UNLOADED = "onPluginUnloaded"
    val ON_PLUGIN_ENABLED = "onPluginEnabled"
    val ON_PLUGIN_DISABLED = "onPluginDisabled"
    val ON_ALL_PLUGINS_LOADED = "onAllPluginsLoaded"
    val ON_PLUGIN_INSTALL = "onPluginInstall"
    val ON_PLUGIN_UNINSTALL = "onPluginUninstall"

    // ===== 应用生命周期事件 =====
    val ON_APP_START = "onAppStart"
    val ON_APP_RESUME = "onAppResume"
    val ON_APP_PAUSE = "onAppPause"
    val ON_APP_STOP = "onAppStop"
    val ON_APP_DESTROY = "onAppDestroy"

    // ===== 项目生命周期事件 =====
    /** 项目创建完成，参数: (projectId, projectName, projectPath) */
    val ON_PROJECT_CREATE = "onProjectCreate"
    /** 项目删除完成，参数: (projectId, projectName, projectPath) */
    val ON_PROJECT_DELETE = "onProjectDelete"
    /** 项目重命名完成，参数: (projectId, oldName, newName, projectPath) */
    val ON_PROJECT_RENAME = "onProjectRename"
    /** 项目打开，参数: (projectId, projectName, projectPath) */
    val ON_PROJECT_OPEN = "onProjectOpen"
    /** 项目备份完成，参数: (projectId, backupPath, success) */
    val ON_PROJECT_BACKUP = "onProjectBackup"
    /** 项目恢复完成，参数: (projectId, backupPath, success) */
    val ON_PROJECT_RESTORE = "onProjectRestore"
    /** 新建项目完成，参数: (projectName, projectPath, templateId) */
    val ON_NEW_PROJECT = "onNewProject"
    /** 项目标签添加，参数: (projectId, tag) */
    val ON_PROJECT_TAG_ADDED = "onProjectTagAdded"
    /** 项目标签移除，参数: (projectId, tag) */
    val ON_PROJECT_TAG_REMOVED = "onProjectTagRemoved"
    /** 项目封面变更，参数: (projectId, coverType, coverValue) */
    val ON_PROJECT_COVER_CHANGED = "onProjectCoverChanged"
    /** 项目置顶状态变化，参数: (projectId, pinned) */
    val ON_PROJECT_PIN_CHANGED = "onProjectPinChanged"

    // ===== 文件操作事件 =====
    /** 文件新建完成，参数: (filePath, isDirectory) */
    val ON_FILE_CREATED = "onFileCreated"
    /** 文件重命名完成，参数: (oldPath, newPath, isDirectory) */
    val ON_FILE_RENAMED = "onFileRenamed"
    /** 文件删除完成，参数: (filePath, isDirectory) */
    val ON_FILE_DELETED = "onFileDeleted"
    /** 文件导入完成，参数: (filePath, sourceUri) */
    val ON_FILE_IMPORTED = "onFileImported"

    // ===== 主页项目列表事件 =====
    val ON_PROJECT_LONG_PRESS = "onProjectLongPress"
    val ON_PROJECT_CLICK = "onProjectClick"
    val ON_PROJECT_SWIPE_LEFT = "onProjectSwipeLeft"
    val ON_PROJECT_SWIPE_RIGHT = "onProjectSwipeRight"
    /** 搜索文本变化，参数: (query) */
    val ON_SEARCH_QUERY_CHANGED = "onSearchQueryChanged"
    /** 排序方式变化，参数: (sortOrder) */
    val ON_SORT_ORDER_CHANGED = "onSortOrderChanged"
    /** 分类切换，参数: (categoryId) */
    val ON_CATEGORY_CHANGED = "onCategoryChanged"
    /** 多选模式进入 */
    val ON_MULTI_SELECT_ENTER = "onMultiSelectEnter"
    /** 多选模式退出 */
    val ON_MULTI_SELECT_EXIT = "onMultiSelectExit"
    /** 多选项目变化，参数: (selectedCount, selectedIdsJson) */
    val ON_MULTI_SELECTION_CHANGED = "onMultiSelectionChanged"
    /** 下拉刷新 */
    val ON_PULL_TO_REFRESH = "onPullToRefresh"
    /** 分类创建，参数: (categoryId, categoryName) */
    val ON_CATEGORY_CREATED = "onCategoryCreated"
    /** 分类删除，参数: (categoryId) */
    val ON_CATEGORY_DELETED = "onCategoryDeleted"

    // ===== 设置变更事件 =====
    /** 设置保存后触发，参数: (settingsJson) 为当前完整设置的 JSON */
    val ON_SETTINGS_CHANGED = "onSettingsChanged"

    // ===== 页面导航事件 =====
    /** 页面切换，参数: (pageId, fromPageId) pageId: main/new_project/editor/settings/about/plugins/webui */
    val ON_PAGE_CHANGED = "onPageChanged"

    // ===== UI 交互事件 =====
    val ON_BACK_PRESSED = "onBackPressed"
    /** 主题变更，参数: (darkMode) darkMode: light/dark/system */
    val ON_THEME_CHANGED = "onThemeChanged"
    /** 语言变更，参数: (languageCode) */
    val ON_LANGUAGE_CHANGED = "onLanguageChanged"
    /** Toast显示，参数: (message, type) type: normal/success/warn/error，可拦截 */
    val ON_TOAST_SHOWN = "onToastShown"

    // ===== 构建事件 =====
    /** 构建开始，参数: (projectPath, buildType) buildType: "project"/"compile" */
    val ON_BUILD_START = "onBuildStart"
    /** 构建完成，参数: (projectPath, result, success) */
    val ON_BUILD_FINISH = "onBuildFinish"
    /** 构建出错，参数: (projectPath, errorMessage, buildType) */
    val ON_BUILD_ERROR = "onBuildError"
    /** 构建输出，参数: (projectPath, message, level) level: info/warn/error */
    val ON_BUILD_OUTPUT = "onBuildOutput"
    /** 代码分析完成，参数: (filePath, diagnosticsJson) */
    val ON_ANALYZE_COMPLETE = "onAnalyzeComplete"

    // ===== 依赖与系统事件 =====
    /** 依赖缺失，参数: (pluginId, missingDepsCsv) */
    val ON_DEPENDENCY_MISSING = "onDependencyMissing"
    /** 插件属性变更，参数: (pluginId, changedFieldsJson) */
    val ON_PLUGIN_PROPERTY_CHANGED = "onPluginPropertyChanged"
    /** 安装版本冲突，参数: (pluginId, existingVersion, newVersion, isUpdate, isDowngrade) */
    val ON_INSTALL_VERSION_CONFLICT = "onInstallVersionConflict"
    /** 插件卡片点击，参数: (pluginId) */
    val ON_PLUGIN_CARD_CLICK = "onPluginCardClick"

    // ===== 插件系统事件 =====
    /** 自定义事件广播，参数: (senderPluginId, eventName, dataJson) */
    val ON_CUSTOM_EVENT = "onCustomEvent"
    /** 插件消息，参数: (fromPluginId, toPluginId, action, dataJson) */
    val ON_PLUGIN_MESSAGE = "onPluginMessage"
}
