package com.luaforge.studio.lxclua.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * 保活前台服务
 * 通过前台服务保持应用在后台运行，防止系统杀死进程
 */
class KeepAliveService : Service() {
    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "lxclua_keep_alive"
        private const val CHANNEL_NAME = "LXC-LUA 保活"
        private const val NOTIFICATION_ID = 1001

        /** 启动保活服务 */
        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止保活服务 */
        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.stopService(intent)
        }

        /** 检查服务是否正在运行 */
        fun isRunning(): Boolean {
            return _isRunning
        }

        private var _isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(TAG, "保活服务已创建")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.i(TAG, "保活服务启动")
        _isRunning = true

        // 请求忽略电池优化（防止 Doze 模式限制）
        requestIgnoreBatteryOptimizations()

        // 启动前台服务，显示持续通知
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 需要指定前台服务类型
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 返回 START_STICKY 确保服务被系统杀死后会自动重启
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning = false
        android.util.Log.i(TAG, "保活服务已停止")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 请求忽略电池优化
     * 如果应用未被加入白名单，则跳转到系统设置页面
     */
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoring = pm?.isIgnoringBatteryOptimizations(packageName) ?: true
            if (!isIgnoring) {
                android.util.Log.w(TAG, "应用未被加入电池优化白名单，尝试跳转设置")
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "跳转电池优化设置失败: ${e.message}")
                    // 尝试打开应用详情页面
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (e2: Exception) {
                        android.util.Log.e(TAG, "跳转应用详情失败: ${e2.message}")
                    }
                }
            } else {
                android.util.Log.i(TAG, "应用已在电池优化白名单中")
            }
        }
    }

    /** 创建通知渠道 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "保持 LXC-LUA 广播服务器在后台运行"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /** 构建保活通知 */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LXC-LUA 运行中")
            .setContentText("广播服务器正在运行，点击返回应用")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(getPendingIntent())
            .build()
    }

    /** 获取点击通知时打开应用的 PendingIntent */
    private fun getPendingIntent(): android.app.PendingIntent? {
        return try {
            val packageManager = packageManager
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            } else null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "创建 PendingIntent 失败: ${e.message}")
            null
        }
    }
}
