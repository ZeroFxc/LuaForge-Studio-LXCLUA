package com.luaforge.studio.lxclua.plugin.data

/**
 * 插件类型枚举
 */
enum class PluginType {
    /** 核心插件 - 系统必需，无法禁用 */
    CORE,
    /** 普通插件 - 可自由启用/禁用 */
    NORMAL,
    /** 附属插件 - 依赖其他插件才能运行 */
    DEPENDENT,
    /** 扩展插件 - 扩展其他插件的功能 */
    EXTENSION
}

/**
 * 插件依赖项
 */
data class PluginDependency(
    /** 依赖的插件ID */
    val pluginId: String,
    /** 最低版本要求 */
    val minVersion: String = "0.0.0",
    /** 是否必需（true表示没有该依赖无法加载） */
    val required: Boolean = true
)

/**
 * 插件元数据（从 manifest.json 解析）
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val type: String = "lua",
    val main: String,
    val mainClass: String? = null,
    val apiVersion: Int = 1,
    /** 插件分类 */
    val category: String = "other",
    /** 插件类型（core/normal/dependent/extension） */
    val pluginType: String = "normal",
    /** 依赖的插件列表 */
    val dependencies: List<PluginDependency> = emptyList(),
    /** 被哪些插件扩展 */
    val extendedBy: List<String> = emptyList(),
    /** 插件主页/文档链接 */
    val homepage: String? = null,
    /** 插件图标（base64或资源路径） */
    val icon: String? = null,
    /** 插件标签列表，支持格式: "标签名" / "#颜色:标签名" / "图标路径|标签名" / "#颜色:图标路径|标签名" */
    val tags: List<String> = emptyList()
)