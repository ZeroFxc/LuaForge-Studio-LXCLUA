package com.luaforge.studio.lxclua.plugin.api

/**
 * 系统信息与权限功能桥接接口
 *
 * 为 DEX/APK 插件提供系统信息获取和权限请求能力
 */
interface IPluginBridgeSystem {
    // ==================== 屏幕信息 ====================

    /** 获取屏幕宽度（像素） */
    fun getScreenWidth(): Int

    /** 获取屏幕高度（像素） */
    fun getScreenHeight(): Int

    /** 获取屏幕密度（dpi） */
    fun getScreenDensity(): Float

    /** 获取完整屏幕信息（JSON 字符串） */
    fun getScreenInfoJson(): String

    // ==================== 设备信息 ====================

    /** 获取设备信息（JSON 字符串） */
    fun getDeviceInfoJson(): String

    // ==================== 应用信息 ====================

    /** 获取应用信息（JSON 字符串） */
    fun getAppInfoJson(): String

    // ==================== 权限 ====================

    /** 检查权限是否已授予 */
    fun checkPermission(permission: String): Boolean

    /** 检查悬浮窗权限 */
    fun canDrawOverlays(): Boolean

    /** 打开悬浮窗权限设置 */
    fun openOverlaySettings()

    /** 打开应用设置页面 */
    fun openAppSettings()
}