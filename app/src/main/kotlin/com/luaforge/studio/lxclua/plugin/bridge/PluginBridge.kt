package com.luaforge.studio.lxclua.plugin.bridge

import android.content.Context
import com.luaforge.studio.lxclua.plugin.LoadedPlugin
import com.luajava.LuaState
import com.luajava.LuaStateFactory
import java.io.File

/**
 * 插件 API 主入口类
 * 
 * 提供分类化的 API 访问方式:
 * - plugin.sys: 系统操作
 * - plugin.editor: 编辑器操作
 * - plugin.project: 项目操作
 * - plugin.ui: 对话框操作
 * - plugin.events: 事件监听
 * - plugin.config: 配置存储
 * - plugin.reflect: Java 反射
 * - plugin.lua: Lua 脚本执行
 * - plugin.menu: 菜单管理
 * - plugin.clipboard: 剪贴板操作
 * - plugin.http: 网络请求
 * - plugin.manager: 插件管理
 * - plugin.nav: 导航与侧滑栏
 * - plugin.about: 关于页面扩展（添加 section、链接、回调、信息项）
 * - plugin.logger: 日志系统（记录/读取/搜索自己及他插件的日志）
 * - plugin.assets: 资源注册表（注册/查询/读取跨插件共享资源）
 * - plugin.shortcut: 快捷键绑定（注册/查询/冲突检测）
 * - plugin.completion: 代码补全扩展（关键字/包函数/变量类型/自定义提供器）
 * - plugin.notification: 通知系统（IDE 内通知横幅、系统通知推送、通知分组和优先级）
 * - plugin.syntax: 语法高亮扩展（注册自定义语言的关键词/正则/注释/字符串/代码折叠规则）
 * - plugin.decoration: 编辑器装饰（行背景色、gutter 区域背景色、gutter 图标）
 * - plugin.symbolBar: 符号栏操作（添加/移除符号、频率统计、面板展开收起、面板高度控制）
 */
class PluginBridge(private val context: Context, private val pluginId: String) {
    
    // ============ 子模块入口 ============
    
    /** 系统操作 API */
    val sys = PluginSys(context, pluginId)
    
    /** 编辑器操作 API */
    val editor = PluginEditor(pluginId)
    
    /** 项目操作 API */
    val project = PluginProject()
    
    /** UI 对话框与UI扩展 API */
    val ui = PluginUI(pluginId)
    
    /** 事件监听 API */
    val events = PluginEvents(pluginId)
    
    /** 配置存储 API */
    val config = PluginConfig(context, pluginId)
    
    /** Java 反射 API */
    val reflect = PluginReflect()
    
    /** Lua 脚本执行 API */
    val lua = PluginLua()
    
    /** 菜单管理 API */
    val menu = PluginMenu(pluginId)
    
    /** 剪贴板操作 API */
    val clipboard = PluginClipboard(context)
    
    /** 网络请求 API */
    val http = PluginHttp()
    
    /** 插件管理 API */
    val manager = PluginManagerBridge()
    
    /** 导航与侧滑栏 API */
    val nav = PluginNavigation(pluginId)
    
    /** 关于页面扩展 API */
    val about = PluginAboutBridge(pluginId)
    
    /** 主页项目列表操作 API */
    val mainpage = PluginMainPage(pluginId)
    
    /** 设置页面扩展 API */
    val settings = PluginSettingsBridge(pluginId)
    
    /** 插件详情展开区扩展 API */
    val detail = PluginDetailBridge(pluginId)
    
    /** 日志系统 API */
    val logger = PluginLogger(context, pluginId)
    
    /** 资源注册表 API */
    val assets = PluginResourceRegistry(pluginId)
    
    /** 快捷键绑定 API */
    val shortcut = PluginShortcut(pluginId)
    
    /** 代码补全扩展 API */
    val completion = PluginCompletion(pluginId)
    
    /** 通知系统 API */
    val notification = PluginNotification(context, pluginId)
    
    /** 符号栏 API */
    val symbolBar = PluginSymbolBar()
    
    /** 语法高亮扩展 API */
    val syntax = PluginSyntax(pluginId)
    
    /** 编辑器装饰 API */
    val decoration = PluginDecoration(pluginId)

    /** 构建系统 API */
    val build = PluginBuild(context)
    
    /** 线程工具 API */
    val threads = PluginThreads()
    
    /** WebUI API */
    val webui = PluginWebUIBridge(pluginId)

    /** AI 功能 API */
    val ai = PluginAI()

    /** MCP 功能 API */
    val mcp = PluginMCP(pluginId, context)

    /** 悬浮窗功能 API */
    val floating = PluginFloating(pluginId)

    /** 系统信息与权限 API */
    val system = PluginSystem()
    
    // ============ 版本信息 ============
    
    /**
     * 获取 API 版本号
     */
    fun getApiVersion(): Int {
        return 1
    }
    
    /**
     * 获取插件自身 ID
     */
    fun getPluginId(): String {
        return pluginId
    }
}