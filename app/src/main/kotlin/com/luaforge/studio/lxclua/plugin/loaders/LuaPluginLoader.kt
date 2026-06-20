package com.luaforge.studio.lxclua.plugin.loaders

import android.content.Context
import com.luaforge.studio.lxclua.plugin.LoadedPlugin
import com.luaforge.studio.lxclua.plugin.bridge.PluginBridge
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import com.luajava.LuaState
import com.luajava.LuaStateFactory
import java.io.File

/**
 * Lua 插件加载器
 * 
 * 提供分类化的 API 访问方式：
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
 * - plugin.completion: 代码补全扩展（关键字/包函数/变量类型/自定义提供器）
 * - plugin.syntax: 语法高亮扩展（注册自定义语言的关键词/正则/注释/字符串/代码折叠规则）
 * - plugin.decoration: 编辑器装饰（行背景色、gutter 图标、gutter 背景色）
 */
object LuaPluginLoader {
    
    fun load(plugin: LoadedPlugin, context: Context) {
        // 1. 初始化 LuaState 并加载基础库
        val L = LuaStateFactory.newLuaState()
        L.openLibs()
        
        // 2. 注入全局工具对象
        val bridge = PluginBridge(context, plugin.manifest.id)
        plugin.pluginBridge = bridge  // 保存引用，用于卸载时取消 AI 请求等
        L.pushJavaObject(bridge)
        L.setGlobal("plugin")

        // 注入 json 全局对象（基于 org.json.JSONObject，Lua 通过 obj:optString("key","") 访问字段）
        val jsonHelper = com.luaforge.studio.lxclua.plugin.bridge.LuaJsonHelper()
        L.pushJavaObject(jsonHelper)
        L.setGlobal("json")
        
        // 3. 执行主入口脚本
        val mainFile = File(plugin.directory, plugin.manifest.main)
        var loadSuccess = false
        
        if (mainFile.exists()) {
            val ok = L.LloadFile(mainFile.absolutePath)
            if (ok == 0) {
                L.getGlobal("debug")
                L.getField(-1, "traceback")
                L.remove(-2)
                L.insert(-2)
                val runOk = L.pcall(0, 0, -2)
                if (runOk != 0) {
                    val error = L.toString(-1)
                    android.util.Log.e("PluginManager", "执行 Lua 插件脚本出错: $error")
                } else {
                    loadSuccess = true
                }
            } else {
                val error = L.toString(-1)
                android.util.Log.e("PluginManager", "编译 Lua 插件失败: $error")
            }
        }
        
        plugin.luaState = L
        
        // 4. 调用 onLoad（如果脚本定义了）
        if (loadSuccess) {
            try {
                L.getGlobal("onLoad")
                if (L.isFunction(-1)) {
                    L.getGlobal("debug")
                    L.getField(-1, "traceback")
                    L.remove(-2)
                    L.insert(-2)
                    val onLoadOk = L.pcall(0, 0, -2)
                    if (onLoadOk != 0) {
                        val error = L.toString(-1)
                        android.util.Log.e("PluginManager", "调用 onLoad 失败 [${plugin.manifest.id}]: $error")
                        plugin.loadError = "onLoad 执行失败: $error"
                        loadSuccess = false
                    }
                } else {
                    L.pop(1)  // 弹出非函数值
                }
            } catch (e: Exception) {
                android.util.Log.e("PluginManager", "调用 onLoad 异常 [${plugin.manifest.id}]", e)
                plugin.loadError = "onLoad 异常: ${e.message}"
                loadSuccess = false
            }
        }
        
        // 5. 触发插件加载完成事件
        if (loadSuccess) {
            EventManager.fireEvent(PluginEvents.ON_PLUGIN_LOADED, plugin.manifest.id)
        }
    }
}
