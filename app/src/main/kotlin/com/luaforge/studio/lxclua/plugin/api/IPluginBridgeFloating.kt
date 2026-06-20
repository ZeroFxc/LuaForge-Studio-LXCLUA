package com.luaforge.studio.lxclua.plugin.api

/**
 * 悬浮窗功能桥接接口
 *
 * 为 DEX/APK 插件提供悬浮窗创建和管理能力
 */
interface IPluginBridgeFloating {
    /** 创建悬浮球，返回悬浮球 ID */
    fun createFloatingBall(x: Int, y: Int, label: String, iconText: String): String?

    /** 移除悬浮球 */
    fun removeFloatingBall(id: String)

    /** 移除所有悬浮球 */
    fun removeAllFloatingBalls()

    /** 更新悬浮球标签 */
    fun updateFloatingBall(id: String, label: String)

    /** 展开默认输入面板 */
    fun showFloatingPanel(id: String, title: String, hint: String)

    /**
     * 以 WebUI 模式展开面板，加载插件 web/ 目录下的 HTML 页面
     *
     * @param id 悬浮球 ID
     * @param title 面板标题
     * @param page HTML 页面文件名（相对于 web/ 目录）
     * @return 是否成功加载
     */
    fun showFloatingPanelWebUI(id: String, title: String, page: String): Boolean

    /** 收起输入面板 */
    fun hideFloatingPanel(id: String)

    /** 请求面板焦点（让输入框或 WebView 获得焦点） */
    fun requestFloatingPanelFocus(id: String)

    /** 清除面板焦点 */
    fun clearFloatingPanelFocus(id: String)

    /**
     * 向 WebUI 面板发送 JSON 消息（宿主 → Web）
     * Web 端通过监听 lxc-message 自定义事件接收
     */
    fun sendToFloatingPanelWeb(id: String, jsonMessage: String)

    /**
     * 向 WebUI 面板执行 JavaScript 代码
     */
    fun evaluateFloatingPanelJs(id: String, jsCode: String)

    /** 获取悬浮球数量 */
    fun getFloatingBallCount(): Int
}