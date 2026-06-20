package com.luaforge.studio.lxclua.plugin.bridge

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.luaforge.studio.lxclua.plugin.PluginManager
import com.luajava.LuaFunction
import java.util.concurrent.ConcurrentHashMap

/** 安全调用 Lua 回调，捕获任何异常防止崩溃 */
internal fun safeCallLua(cb: Any?, vararg args: Any?) {
    try {
        @Suppress("UNCHECKED_CAST")
        (cb as? LuaFunction<Any>)?.call(*args)
    } catch (e: Exception) {
        android.util.Log.e("PluginBridge", "Lua 回调异常: ${e.message}", e)
    }
}

/**
 * 系统信息与权限 API
 *
 * 为插件提供：
 * - plugin.system.getScreenWidth()        -- 屏幕宽度（像素）
 * - plugin.system.getScreenHeight()       -- 屏幕高度（像素）
 * - plugin.system.getScreenDensity()      -- 屏幕密度（dpi）
 * - plugin.system.getScreenInfo()         -- 屏幕完整信息（Map）
 * - plugin.system.getDeviceInfo()         -- 设备信息（Map）
 * - plugin.system.getAppInfo()            -- 应用信息（Map）
 * - plugin.system.checkPermission(perm)   -- 检查权限是否已授予
 * - plugin.system.requestPermission(perm, callback) -- 请求运行时权限
 * - plugin.system.requestOverlayPermission(callback) -- 请求悬浮窗权限
 * - plugin.system.openOverlaySettings()   -- 打开悬浮窗权限设置页
 */
class PluginSystem {

    companion object {
        /** 权限请求回调缓存 */
        private val permissionCallbacks = ConcurrentHashMap<Int, LuaFunction<*>>()
        private val overlayCallbacks = ConcurrentHashMap<Int, LuaFunction<*>>()
        private var requestCodeCounter = 1000

        /**
         * 处理权限请求结果（由 Activity 调用）
         */
        @JvmStatic
        fun handlePermissionResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
            val callback = permissionCallbacks.remove(requestCode) ?: return
            if (permissions.isNotEmpty()) {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                @Suppress("UNCHECKED_CAST")
                safeCallLua(callback, granted, permissions[0], "")
            }
        }

        /**
         * 处理悬浮窗权限请求结果（由 Activity 调用）
         */
        @JvmStatic
        fun handleOverlayResult(requestCode: Int) {
            val callback = overlayCallbacks.remove(requestCode) ?: return
            val ctx = PluginManager.appContext
            val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ctx != null) {
                Settings.canDrawOverlays(ctx)
            } else {
                true
            }
            @Suppress("UNCHECKED_CAST")
            safeCallLua(callback, granted, "android.permission.SYSTEM_ALERT_WINDOW", "")
        }

        internal fun nextRequestCode(): Int = requestCodeCounter++
    }

    // ==================== 屏幕信息 ====================

    /** 获取屏幕宽度（像素） */
    fun getScreenWidth(): Int {
        return getMetrics()?.widthPixels ?: 1080
    }

    /** 获取屏幕高度（像素） */
    fun getScreenHeight(): Int {
        return getMetrics()?.heightPixels ?: 1920
    }

    /** 获取屏幕密度（dpi） */
    fun getScreenDensity(): Float {
        return getMetrics()?.density ?: 3.0f
    }

    /** 获取屏幕密度 DPI */
    fun getScreenDensityDpi(): Int {
        return getMetrics()?.densityDpi ?: 480
    }

    /** 获取完整屏幕信息 */
    fun getScreenInfo(): Map<String, Any> {
        val metrics = getMetrics()
        if (metrics == null) return emptyMap()
        return mapOf(
            "width" to metrics.widthPixels,
            "height" to metrics.heightPixels,
            "density" to metrics.density.toDouble(),
            "densityDpi" to metrics.densityDpi,
            "scaledDensity" to metrics.scaledDensity.toDouble(),
            "xdpi" to metrics.xdpi.toDouble(),
            "ydpi" to metrics.ydpi.toDouble(),
            "statusBarHeight" to getStatusBarHeight(),
            "navigationBarHeight" to getNavigationBarHeight()
        )
    }

    /** 获取状态栏高度 */
    fun getStatusBarHeight(): Int {
        val ctx = PluginManager.appContext ?: return 0
        val resourceId = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) ctx.resources.getDimensionPixelSize(resourceId) else 0
    }

    /** 获取导航栏高度 */
    fun getNavigationBarHeight(): Int {
        val ctx = PluginManager.appContext ?: return 0
        val resourceId = ctx.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) ctx.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun getMetrics(): DisplayMetrics? {
        val ctx = PluginManager.appContext ?: return null
        val metrics = DisplayMetrics()
        val wm = ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
        wm?.defaultDisplay?.getRealMetrics(metrics)
        return metrics
    }

    // ==================== 设备信息 ====================

    /** 获取设备信息 */
    fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "model" to (Build.MODEL ?: "未知"),
            "manufacturer" to (Build.MANUFACTURER ?: "未知"),
            "brand" to (Build.BRAND ?: "未知"),
            "device" to (Build.DEVICE ?: "未知"),
            "product" to (Build.PRODUCT ?: "未知"),
            "androidVersion" to Build.VERSION.RELEASE,
            "sdkVersion" to Build.VERSION.SDK_INT.toString(),
            "board" to (Build.BOARD ?: "未知"),
            "hardware" to (Build.HARDWARE ?: "未知")
        )
    }

    // ==================== 应用信息 ====================

    /** 获取应用信息 */
    fun getAppInfo(): Map<String, String> {
        val ctx = PluginManager.appContext ?: return emptyMap()
        return try {
            val pkgInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            mapOf(
                "packageName" to ctx.packageName,
                "versionName" to (pkgInfo.versionName ?: "未知"),
                "versionCode" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    pkgInfo.longVersionCode.toString() else pkgInfo.versionCode.toString()),
                "appName" to (ctx.applicationInfo.loadLabel(ctx.packageManager).toString())
            )
        } catch (e: Exception) {
            mapOf("packageName" to ctx.packageName)
        }
    }

    // ==================== 权限请求 ====================

    /**
     * 检查 Android 运行时权限是否已授予
     *
     * @param permission 权限字符串，如 "android.permission.SYSTEM_ALERT_WINDOW"
     * @return true 已授予，false 未授予
     */
    fun checkPermission(permission: String): Boolean {
        val ctx = PluginManager.appContext ?: return false
        // 悬浮窗权限特殊处理
        if (permission == "android.permission.SYSTEM_ALERT_WINDOW") {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(ctx)
            } else {
                true
            }
        }
        return ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 请求 Android 运行时权限
     *
     * @param permission 权限字符串，如 "android.permission.CAMERA"
     * @param callback 结果回调：function(granted, permission)
     * @return 请求码，可用于追踪
     */
    fun requestPermission(permission: String, callback: LuaFunction<*>): Int {
        val activity = PluginManager.currentActivity
        if (activity == null) {
            @Suppress("UNCHECKED_CAST")
            safeCallLua(callback, false, permission, "Activity 不可用")
            return -1
        }

        // 悬浮窗权限特殊处理
        if (permission == "android.permission.SYSTEM_ALERT_WINDOW") {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestOverlayPermission(activity, callback)
            } else {
                @Suppress("UNCHECKED_CAST")
                safeCallLua(callback, true, permission, "")
                -1
            }
        }

        // 已授予则直接回调
        if (checkPermission(permission)) {
            @Suppress("UNCHECKED_CAST")
            safeCallLua(callback, true, permission, "")
            return -1
        }

        val requestCode = requestCodeCounter++
        permissionCallbacks[requestCode] = callback

        ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
        return requestCode
    }

    /**
     * 请求悬浮窗权限（SYSTEM_ALERT_WINDOW）
     */
    fun requestOverlayPermission(callback: LuaFunction<*>): Int {
        val activity = PluginManager.currentActivity
        if (activity == null) {
            @Suppress("UNCHECKED_CAST")
            (callback as LuaFunction<Any>).call(false, "android.permission.SYSTEM_ALERT_WINDOW", "Activity 不可用")
            return -1
        }
        return requestOverlayPermission(activity, callback)
    }

    private fun requestOverlayPermission(activity: Activity, callback: LuaFunction<*>): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(activity)) {
                @Suppress("UNCHECKED_CAST")
                (callback as LuaFunction<Any>).call(true, "android.permission.SYSTEM_ALERT_WINDOW", "")
                return -1
            }
            val requestCode = requestCodeCounter++
            overlayCallbacks[requestCode] = callback
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, requestCode)
            return requestCode
        }
        return -1
    }

    /**
     * 打开悬浮窗权限设置页面
     */
    fun openOverlaySettings() {
        val activity = PluginManager.currentActivity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    /**
     * 打开应用详情设置页面
     */
    fun openAppSettings() {
        val activity = PluginManager.currentActivity ?: return
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        activity.startActivity(intent)
    }

    /**
     * 检查悬浮窗权限（便捷方法）
     */
    fun canDrawOverlays(): Boolean {
        return checkPermission("android.permission.SYSTEM_ALERT_WINDOW")
    }
}