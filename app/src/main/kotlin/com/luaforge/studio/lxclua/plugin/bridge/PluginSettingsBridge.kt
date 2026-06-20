package com.luaforge.studio.lxclua.plugin.bridge

import com.luaforge.studio.lxclua.plugin.state.PluginSettingsItem
import com.luaforge.studio.lxclua.plugin.state.PluginSettingsSection
import com.luaforge.studio.lxclua.plugin.state.PluginSettingsState
import com.luaforge.studio.lxclua.ui.settings.SettingsManager

class PluginSettingsBridge(private val pluginId: String) {

    companion object {
        const val TYPE_SWITCH = PluginSettingsState.TYPE_SWITCH
        const val TYPE_BUTTON = PluginSettingsState.TYPE_BUTTON
    }

    /**
     * 获取应用全局设置的当前值，以 Map 形式返回给 Lua
     * 包含 aiStreamEnabled 等字段
     */
    fun getCurrent(): Map<String, Any?> {
        val s = SettingsManager.currentSettings
        return mapOf(
            "aiStreamEnabled" to s.aiStreamEnabled,
            "themeType" to s.themeType.name,
            "darkMode" to s.darkMode.name,
            "editorWordWrap" to s.editorWordWrap,
            "indentGuideEnabled" to s.indentGuideEnabled,
            "hexColorHighlightEnabled" to s.hexColorHighlightEnabled,
        )
    }

    /** 获取 AI 流式输出开关状态（Lua 可直接调用） */
    fun isStreamEnabled(): Boolean = SettingsManager.currentSettings.aiStreamEnabled

    fun addSection(key: String, title: String, priority: Int = 100) {
        PluginSettingsState.addSection(
            PluginSettingsSection(
                key = "${pluginId}_$key",
                pluginId = pluginId,
                title = title,
                priority = priority
            )
        )
    }

    fun addSwitch(
        key: String,
        sectionKey: String,
        title: String,
        subtitle: String,
        initialValue: Boolean = false,
        onChange: (Boolean) -> Unit
    ) {
        PluginSettingsState.addItem(
            PluginSettingsItem(
                key = "${pluginId}_$key",
                sectionKey = "${pluginId}_$sectionKey",
                pluginId = pluginId,
                title = title,
                subtitle = subtitle,
                type = TYPE_SWITCH,
                initialValue = initialValue,
                onChange = onChange
            )
        )
    }

    fun addButton(
        key: String,
        sectionKey: String,
        title: String,
        subtitle: String,
        onClick: Runnable
    ) {
        PluginSettingsState.addItem(
            PluginSettingsItem(
                key = "${pluginId}_$key",
                sectionKey = "${pluginId}_$sectionKey",
                pluginId = pluginId,
                title = title,
                subtitle = subtitle,
                type = TYPE_BUTTON,
                onClick = {
                    try {
                        onClick.run()
                    } catch (e: Exception) {
                        android.util.Log.e("PluginSettingsBridge", "设置按钮回调执行失败: ${pluginId}_$key", e)
                    }
                }
            )
        )
    }

    fun removeSection(key: String) {
        val fullKey = "${pluginId}_$key"
        PluginSettingsState.removeSection(fullKey)
        PluginSettingsState.pluginItems.removeAll { it.sectionKey == fullKey }
    }

    fun removeItem(key: String) {
        PluginSettingsState.removeItem("${pluginId}_$key")
    }

    fun clearItems() {
        PluginSettingsState.clearPluginItems(pluginId)
    }
}