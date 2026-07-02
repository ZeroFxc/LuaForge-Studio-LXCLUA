package com.luaforge.studio.lxclua.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.luaforge.studio.lxclua.ui.settings.SettingsManager

/**
 * 应用图标管理器
 * 负责通过activity-alias切换应用启动图标
 */
object IconManager {

    enum class AppIcon(val aliasName: String, val displayName: String) {
        DEFAULT(".MainActivityDefault", "默认图标"),
        PLAY_STORE(".MainActivityPlayStore", "经典图标"),
        ADAPTIVE(".MainActivityDefault", "自适应图标")  // 与DEFAULT使用相同别名（均支持roundIcon）
    }

    // 需要管理的别名组件列表（不包含SplashWelcome本身，只操作alias）
    private val ALIAS_COMPONENTS = listOf(
        AppIcon.DEFAULT.aliasName,
        AppIcon.PLAY_STORE.aliasName
    ).distinct()

    /**
     * 获取当前使用的图标类型
     */
    fun getCurrentIcon(context: Context): AppIcon {
        val packageManager = context.packageManager

        return try {
            // 检查各个别名组件是否启用
            val playStoreComponent = ComponentName(
                context,
                "${context.packageName}${AppIcon.PLAY_STORE.aliasName}"
            )
            val defaultComponent = ComponentName(
                context,
                "${context.packageName}${AppIcon.DEFAULT.aliasName}"
            )
            when {
                packageManager.getComponentEnabledSetting(playStoreComponent) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> AppIcon.PLAY_STORE
                packageManager.getComponentEnabledSetting(defaultComponent) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> AppIcon.DEFAULT
                else -> AppIcon.DEFAULT
            }
        } catch (e: Exception) {
            AppIcon.DEFAULT
        }
    }

    /**
     * 切换应用图标
     * 注意：只操作activity-alias组件，不禁用SplashWelcome本身
     */
    fun switchAppIcon(context: Context, newIcon: AppIcon) {
        val packageManager = context.packageManager

        // 先启用目标组件（确保在禁用其他组件前目标已可用）
        val targetComponent = ComponentName(
            context,
            "${context.packageName}${newIcon.aliasName}"
        )
        packageManager.setComponentEnabledSetting(
            targetComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // 禁用其他别名组件（不包含SplashWelcome和当前选中的）
        ALIAS_COMPONENTS.forEach { alias ->
            if (alias != newIcon.aliasName) {
                val componentName = ComponentName(
                    context,
                    "${context.packageName}$alias"
                )
                try {
                    packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (e: Exception) {
                    // 忽略异常
                }
            }
        }

        // 保存到设置
        val settings = SettingsManager.currentSettings
        if (settings.selectedAppIcon != newIcon) {
            val newSettings = settings.copy(selectedAppIcon = newIcon)
            SettingsManager.updateSettings(newSettings)
            SettingsManager.saveSettings(context)
        }
    }

    /**
     * 初始化图标设置 - 确保只有一个图标别名启用
     * 仅在当前图标与保存设置不一致时才执行切换，避免每次启动都执行PackageManager操作
     */
    fun initIconSetting(context: Context) {
        val savedIcon = SettingsManager.currentSettings.selectedAppIcon
        val currentIcon = getCurrentIcon(context)

        // 只有当当前启用的图标与保存的设置不一致时才执行切换
        if (currentIcon != savedIcon) {
            try {
                switchAppIcon(context, savedIcon)
            } catch (e: Exception) {
                // 如果初始化失败，确保默认图标启用
                val defaultComponent = ComponentName(
                    context,
                    "${context.packageName}${AppIcon.DEFAULT.aliasName}"
                )
                context.packageManager.setComponentEnabledSetting(
                    defaultComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
