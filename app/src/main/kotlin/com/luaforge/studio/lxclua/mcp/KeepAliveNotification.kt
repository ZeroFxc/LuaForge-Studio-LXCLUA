package com.luaforge.studio.lxclua.mcp

import android.content.Context

/**
 * 保活通知管理
 * 通过前台服务显示通知，防止系统杀死进程，保持广播服务器持续运行
 */
object KeepAliveNotification {
    /**
     * 显示保活通知（启动前台服务）
     */
    fun show(context: Context) {
        KeepAliveService.start(context)
    }

    /**
     * 隐藏保活通知（停止前台服务）
     */
    fun hide(context: Context) {
        KeepAliveService.stop(context)
    }

    /**
     * 检查保活服务是否正在运行
     */
    fun isShowing(): Boolean {
        return KeepAliveService.isRunning()
    }
}
