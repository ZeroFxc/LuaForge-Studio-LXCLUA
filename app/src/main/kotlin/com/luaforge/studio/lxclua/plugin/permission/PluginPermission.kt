package com.luaforge.studio.lxclua.plugin.permission

/**
 * 权限分类
 */
enum class PermissionCategory(val displayName: String) {
    NETWORK("网络"),
    FILE_SYSTEM("文件系统"),
    UI("界面"),
    SYSTEM("系统"),
    AI("AI"),
    MCP("MCP"),
    PLUGIN("插件管理")
}

/**
 * 插件权限枚举
 *
 * 定义插件可以请求的各种系统能力访问权限。
 * 插件在 manifest.json 中声明所需权限，宿主在加载时检查并授权。
 */
enum class PluginPermission(
    val key: String,
    val description: String,
    val category: PermissionCategory,
    /** 是否默认授予（低风险权限自动授权） */
    val grantedByDefault: Boolean = false
) {
    // ==================== 网络 ====================
    /** HTTP 网络请求 */
    NETWORK_HTTP("network.http", "发起 HTTP 网络请求", PermissionCategory.NETWORK, true),
    /** WebSocket 连接 */
    NETWORK_WEBSOCKET("network.websocket", "建立 WebSocket 连接", PermissionCategory.NETWORK),

    // ==================== 文件系统 ====================
    /** 读取项目文件 */
    FILE_READ("file.read", "读取项目文件内容", PermissionCategory.FILE_SYSTEM, true),
    /** 写入项目文件 */
    FILE_WRITE("file.write", "写入或修改项目文件", PermissionCategory.FILE_SYSTEM, true),
    /** 读取外部存储 */
    FILE_READ_EXTERNAL("file.read_external", "读取外部存储文件", PermissionCategory.FILE_SYSTEM),
    /** 写入外部存储 */
    FILE_WRITE_EXTERNAL("file.write_external", "写入外部存储文件", PermissionCategory.FILE_SYSTEM),

    // ==================== UI ====================
    /** 显示对话框 */
    UI_DIALOG("ui.dialog", "显示对话框和提示", PermissionCategory.UI, true),
    /** 显示通知 */
    UI_NOTIFICATION("ui.notification", "发送系统通知", PermissionCategory.UI),
    /** 自定义 Web UI */
    UI_WEBUI("ui.webui", "提供 Web 界面", PermissionCategory.UI, true),
    /** 编辑器扩展 */
    UI_EDITOR("ui.editor", "扩展编辑器功能", PermissionCategory.UI, true),

    // ==================== 系统 ====================
    /** 剪贴板访问 */
    SYSTEM_CLIPBOARD("system.clipboard", "读写剪贴板", PermissionCategory.SYSTEM, true),
    /** 执行系统命令 */
    SYSTEM_SHELL("system.shell", "执行系统命令", PermissionCategory.SYSTEM),
    /** 反射调用 */
    SYSTEM_REFLECTION("system.reflection", "使用 Java 反射", PermissionCategory.SYSTEM),
    /** 构建项目 */
    SYSTEM_BUILD("system.build", "触发项目构建", PermissionCategory.SYSTEM, true),

    // ==================== AI ====================
    /** AI 聊天 */
    AI_CHAT("ai.chat", "调用 AI 聊天功能", PermissionCategory.AI),
    /** AI 代码分析 */
    AI_CODE_ANALYSIS("ai.code_analysis", "使用 AI 分析代码", PermissionCategory.AI),

    // ==================== MCP ====================
    /** MCP 连接 */
    MCP_CONNECT("mcp.connect", "连接 MCP 服务器", PermissionCategory.MCP),
    /** MCP 工具调用 */
    MCP_TOOL_CALL("mcp.tool_call", "调用 MCP 工具", PermissionCategory.MCP),
    /** MCP 资源读取 */
    MCP_RESOURCE_READ("mcp.resource_read", "读取 MCP 资源", PermissionCategory.MCP),

    // ==================== 插件管理 ====================
    /** 管理其他插件 */
    PLUGIN_MANAGE("plugin.manage", "管理其他插件", PermissionCategory.PLUGIN);

    companion object {
        /** 根据 key 查找权限 */
        fun fromKey(key: String): PluginPermission? =
            entries.find { it.key == key }

        /** 获取默认授予的权限列表 */
        fun getDefaultPermissions(): List<PluginPermission> =
            entries.filter { it.grantedByDefault }
    }
}

/**
 * 插件权限管理器
 *
 * 负责检查插件是否拥有指定权限。
 * 在 PluginBridgeImpl 各操作前调用权限检查。
 */
class PluginPermissionManager(private val pluginId: String) {
    /** 当前插件已授予的权限 */
    private val grantedPermissions = mutableSetOf<PluginPermission>()

    /** 初始化默认权限 */
    init {
        grantedPermissions.addAll(PluginPermission.getDefaultPermissions())
    }

    /** 授予权限 */
    fun grant(permission: PluginPermission) {
        grantedPermissions.add(permission)
    }

    /** 授予多个权限 */
    fun grantAll(permissions: Collection<PluginPermission>) {
        grantedPermissions.addAll(permissions)
    }

    /** 撤销权限 */
    fun revoke(permission: PluginPermission) {
        grantedPermissions.remove(permission)
    }

    /** 检查是否拥有指定权限 */
    fun hasPermission(permission: PluginPermission): Boolean =
        permission in grantedPermissions

    /** 检查是否拥有指定权限（通过 key），如果权限不存在则通过 */
    fun checkPermission(permission: PluginPermission) {
        if (!hasPermission(permission)) {
            throw SecurityException("插件 [$pluginId] 缺少权限: ${permission.key} (${permission.description})")
        }
    }

    /** 获取所有已授予权限 */
    fun getGrantedPermissions(): Set<PluginPermission> = grantedPermissions.toSet()

    /** 从 manifest 权限列表加载 */
    fun loadFromManifest(manifestPermissions: List<String>) {
        manifestPermissions.forEach { key ->
            PluginPermission.fromKey(key)?.let { grant(it) }
        }
    }
}