package com.luaforge.studio.lxclua.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.luaforge.studio.lxclua.MainActivity
import com.luaforge.studio.lxclua.ProjectItem
import java.io.File

/**
 * 桌面快捷方式工具类
 * 提供动态快捷方式更新和请求Pin快捷方式功能
 */
object ShortcutHelper {

    private const val EXTRA_PROJECT_ID = "extra_project_id"
    private const val EXTRA_ACTION = "extra_action"
    private const val ACTION_OPEN_PROJECT = "open_project"
    private const val MAX_SHORTCUTS = 5

    /**
     * 更新动态快捷方式（长按应用图标显示最近项目）
     */
    fun updateShortcuts(context: Context, recentProjects: List<ProjectItem>) {
        try {
            val shortcuts = recentProjects.take(MAX_SHORTCUTS).mapIndexed { index, project ->
                val icon = loadProjectIcon(context, project)
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(EXTRA_PROJECT_ID, project.id)
                    putExtra(EXTRA_ACTION, ACTION_OPEN_PROJECT)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    setData(Uri.parse("lxclua://project/${project.id}"))
                }
                ShortcutInfoCompat.Builder(context, "recent_${project.id}")
                    .setShortLabel(project.name)
                    .setLongLabel(project.name)
                    .setIcon(icon)
                    .setIntent(intent)
                    .setRank(index)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: Exception) {
            android.util.Log.w("ShortcutHelper", "更新动态快捷方式失败: ${e.message}")
        }
    }

    /**
     * 请求创建桌面快捷方式（Pin Shortcut）
     */
    fun createShortcut(context: Context, project: ProjectItem): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                if (shortcutManager?.isRequestPinShortcutSupported != true) {
                    return false
                }
            }
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                return false
            }

            val icon = loadProjectIcon(context, project)
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_PROJECT_ID, project.id)
                putExtra(EXTRA_ACTION, ACTION_OPEN_PROJECT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val shortcut = ShortcutInfoCompat.Builder(context, "pin_${project.id}")
                .setShortLabel(project.name)
                .setLongLabel(project.name)
                .setIcon(icon)
                .setIntent(intent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            true
        } catch (e: Exception) {
            android.util.Log.w("ShortcutHelper", "创建快捷方式失败: ${e.message}")
            false
        }
    }

    /**
     * 从Intent中获取要打开的项目ID
     */
    fun getProjectIdFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.getStringExtra(EXTRA_ACTION) != ACTION_OPEN_PROJECT) return null
        return intent.getStringExtra(EXTRA_PROJECT_ID)
    }

    /**
     * 加载项目图标，失败则返回默认图标
     * 优先使用项目icon.png，不存在则使用默认图标
     */
    private fun loadProjectIcon(context: Context, project: ProjectItem): IconCompat {
        return try {
            val iconFile = File(project.path, "icon.png")
            if (iconFile.exists() && iconFile.isFile && iconFile.length() > 0) {
                val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bitmap != null) {
                    // 缩放图标到合适尺寸，回收原始bitmap避免内存泄漏
                    val scaled = if (bitmap.width != 128 || bitmap.height != 128) {
                        val resized = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
                        if (resized != bitmap) bitmap.recycle()
                        resized
                    } else {
                        bitmap
                    }
                    IconCompat.createWithBitmap(scaled)
                } else {
                    IconCompat.createWithResource(context, android.R.drawable.sym_def_app_icon)
                }
            } else {
                IconCompat.createWithResource(context, android.R.drawable.sym_def_app_icon)
            }
        } catch (e: Exception) {
            android.util.Log.w("ShortcutHelper", "加载项目图标失败: ${e.message}")
            IconCompat.createWithResource(context, android.R.drawable.sym_def_app_icon)
        }
    }
}
