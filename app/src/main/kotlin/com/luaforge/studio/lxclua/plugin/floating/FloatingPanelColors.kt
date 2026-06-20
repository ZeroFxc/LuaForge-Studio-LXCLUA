package com.luaforge.studio.lxclua.plugin.floating

import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import com.luaforge.studio.lxclua.plugin.PluginManager
import com.luaforge.studio.lxclua.ui.settings.DarkMode
import com.luaforge.studio.lxclua.ui.settings.SettingsManager
import com.luaforge.studio.lxclua.ui.theme.ThemeType
import com.luaforge.studio.lxclua.ui.theme.*

/**
 * 悬浮面板主题颜色
 *
 * 完全根据应用设置中的主题类型和夜间模式自动适配颜色，
 * 不硬编码任何默认预设值。
 * 每次调用属性会重新读取当前设置，确保跟随主题切换。
 */
object FloatingPanelColors {

    /** 面板背景色 */
    val panelBackground: Int get() = currentSurface
    /** 面板边框色 */
    val panelBorder: Int get() = currentOutline
    /** 面板标题文字色 */
    val panelTitleText: Int get() = currentOnSurface
    /** 内容容器背景色 */
    val contentBackground: Int get() = currentSurfaceContainer
    /** 内容容器文字色 */
    val contentText: Int get() = currentOnSurface
    /** 关闭按钮背景色 */
    val cancelButtonBg: Int get() = currentOutlineVariant
    /** 关闭按钮文字色 */
    val cancelButtonText: Int get() = currentOnSurface
    /** 发送按钮背景色 */
    val sendButtonBg: Int get() = currentPrimary
    /** 发送按钮文字色 */
    val sendButtonText: Int get() = currentOnPrimary
    /** 停止按钮背景色（红色警告色） */
    val stopButtonBg: Int get() = currentError
    /** 停止按钮文字色 */
    val stopButtonText: Int get() = currentOnError
    /** 调节手柄颜色 */
    val resizeHandle: Int get() = currentOutline
    /** 加载动画颜色 */
    val loadingIndicator: Int get() = currentPrimary
    /** 输入框 hint 文字色（使用主题的 onSurfaceVariant，不再硬编码） */
    val hintText: Int get() = currentOnSurfaceVariant

    // ==================== 内部 ====================

    /**
     * 判断当前是否为暗色模式
     * - DARK: 强制暗色
     * - LIGHT: 强制亮色
     * - FOLLOW_SYSTEM: 读取系统夜间模式状态
     */
    private val isDark: Boolean get() {
        val settings = SettingsManager.currentSettings
        return when (settings.darkMode) {
            DarkMode.DARK -> true
            DarkMode.LIGHT -> false
            DarkMode.FOLLOW_SYSTEM -> {
                val ctx = PluginManager.appContext ?: return false
                val nightMode = ctx.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                nightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private val themeType: ThemeType get() {
        return SettingsManager.currentSettings.themeType
    }

    private val currentSurface: Int get() {
        val dark = isDark
        return when (themeType) {
            ThemeType.GREEN -> if (dark) surfaceDarkGreen.toArgb() else surfaceLightGreen.toArgb()
            ThemeType.BLUE -> if (dark) surfaceDarkBlue.toArgb() else surfaceLightBlue.toArgb()
            ThemeType.PINK -> if (dark) surfaceDarkPink.toArgb() else surfaceLightPink.toArgb()
        }
    }

    private val currentOnSurface: Int get() {
        val dark = isDark
        return when (themeType) {
            ThemeType.GREEN -> if (dark) onSurfaceDarkGreen.toArgb() else onSurfaceLightGreen.toArgb()
            ThemeType.BLUE -> if (dark) onSurfaceDarkBlue.toArgb() else onSurfaceLightBlue.toArgb()
            ThemeType.PINK -> if (dark) onSurfaceDarkPink.toArgb() else onSurfaceLightPink.toArgb()
        }
    }

    private val currentOnSurfaceVariant: Int get() {
        val dark = isDark
        return when (themeType) {
            ThemeType.GREEN -> if (dark) onSurfaceVariantDarkGreen.toArgb() else onSurfaceVariantLightGreen.toArgb()
            ThemeType.BLUE -> if (dark) onSurfaceVariantDarkBlue.toArgb() else onSurfaceVariantLightBlue.toArgb()
            ThemeType.PINK -> if (dark) onSurfaceVariantDarkPink.toArgb() else onSurfaceVariantLightPink.toArgb()
        }
    }

    private val currentSurfaceContainer: Int get() {
        val dark = isDark
        return when (themeType) {
            ThemeType.GREEN -> if (dark) surfaceContainerDarkGreen.toArgb() else surfaceContainerLightGreen.toArgb()
            ThemeType.BLUE -> if (dark) surfaceContainerDarkBlue.toArgb() else surfaceContainerLightBlue.toArgb()
            ThemeType.PINK -> if (dark) surfaceContainerDarkPink.toArgb() else surfaceContainerLightPink.toArgb()
        }
    }

    private val currentOutline: Int get() {
        val dark = isDark
        return when (themeType) {
            ThemeType.GREEN -> if (dark) outlineDarkGreen.toArgb() else outlineLightGreen.toArgb()
            ThemeType.BLUE -> if (dark) outlineDarkBlue.toArgb() else outlineLightBlue.toArgb()
            ThemeType.PINK -> if (dark) outlineDarkPink.toArgb() else outlineLightPink.toArgb()
        }
    }

    private val currentOutlineVariant: Int get() {
        val dark = isDark
        return when (themeType) {
            ThemeType.GREEN -> if (dark) outlineVariantDarkGreen.toArgb() else outlineVariantLightGreen.toArgb()
            ThemeType.BLUE -> if (dark) outlineVariantDarkBlue.toArgb() else outlineVariantLightBlue.toArgb()
            ThemeType.PINK -> if (dark) outlineVariantDarkPink.toArgb() else outlineVariantLightPink.toArgb()
        }
    }

    private val currentPrimary: Int get() {
        val dark = isDark
        return when (themeType) {
            ThemeType.GREEN -> if (dark) primaryDarkGreen.toArgb() else primaryLightGreen.toArgb()
            ThemeType.BLUE -> if (dark) primaryDarkBlue.toArgb() else primaryLightBlue.toArgb()
            ThemeType.PINK -> if (dark) primaryDarkPink.toArgb() else primaryLightPink.toArgb()
        }
    }

    private val currentOnPrimary: Int get() {
        val dark = isDark
        return when (themeType) {
            ThemeType.GREEN -> if (dark) onPrimaryDarkGreen.toArgb() else onPrimaryLightGreen.toArgb()
            ThemeType.BLUE -> if (dark) onPrimaryDarkBlue.toArgb() else onPrimaryLightBlue.toArgb()
            ThemeType.PINK -> if (dark) onPrimaryDarkPink.toArgb() else onPrimaryLightPink.toArgb()
        }
    }

    /** 错误/警告色（用于停止按钮） */
    private val currentError: Int get() {
        val dark = isDark
        return when (themeType) {
            ThemeType.GREEN -> if (dark) errorDarkGreen.toArgb() else errorLightGreen.toArgb()
            ThemeType.BLUE -> if (dark) errorDarkBlue.toArgb() else errorLightBlue.toArgb()
            ThemeType.PINK -> if (dark) errorDarkPink.toArgb() else errorLightPink.toArgb()
        }
    }

    private val currentOnError: Int get() {
        val dark = isDark
        return when (themeType) {
            ThemeType.GREEN -> if (dark) onErrorDarkGreen.toArgb() else onErrorLightGreen.toArgb()
            ThemeType.BLUE -> if (dark) onErrorDarkBlue.toArgb() else onErrorLightBlue.toArgb()
            ThemeType.PINK -> if (dark) onErrorDarkPink.toArgb() else onErrorLightPink.toArgb()
        }
    }
}