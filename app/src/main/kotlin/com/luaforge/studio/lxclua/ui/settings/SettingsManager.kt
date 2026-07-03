package com.luaforge.studio.lxclua.ui.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.getSystemService
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.luaforge.studio.lxclua.plugin.state.EventManager
import com.luaforge.studio.lxclua.plugin.state.PluginEvents
import com.luaforge.studio.lxclua.ui.theme.ThemeType
import com.luaforge.studio.lxclua.utils.IconManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

// DataStore 实例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

// 定义所有存储键
private object PreferencesKeys {
    val THEME_TYPE = stringPreferencesKey("theme_type")
    val DARK_MODE = stringPreferencesKey("dark_mode")
    val FONT_SIZE_SCALE = floatPreferencesKey("font_size_scale")
    val SHAPE_SIZE_INDEX = intPreferencesKey("shape_size_index")
    val FONT_FAMILY_TYPE = stringPreferencesKey("font_family_type")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val EDITOR_FONT_TYPE = stringPreferencesKey("editor_font_type")
    val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
    val ENABLE_TAB_HISTORY = booleanPreferencesKey("enable_tab_history")
    val INDENT_GUIDE_ENABLED = booleanPreferencesKey("indentGuideEnabled")
    val PROJECT_STORAGE_PATH = stringPreferencesKey("project_storage_path")
    val ADDITIONAL_PROJECT_PATHS = stringPreferencesKey("additional_project_paths")

    // 语法高亮颜色
    val CLASS_NAME_COLOR = intPreferencesKey("syntax_class_name_color")
    val LOCAL_VAR_COLOR = intPreferencesKey("syntax_local_var_color")
    val KEYWORD_COLOR = intPreferencesKey("syntax_keyword_color")
    val FUNCTION_NAME_COLOR = intPreferencesKey("syntax_function_color")
    val LITERAL_COLOR = intPreferencesKey("syntax_literal_color")
    val COMMENT_COLOR = intPreferencesKey("syntax_comment_color")
    val SELECTED_LINE_COLOR = intPreferencesKey("selected_line_color")

    val SELECTED_APP_ICON = stringPreferencesKey("selected_app_icon")

    // 补全大小写敏感设置项
    val COMPLETION_CASE_SENSITIVE = booleanPreferencesKey("completion_case_sensitive")

    // 排序方式和置顶项目列表
    val SORT_ORDER = stringPreferencesKey("sort_order")
    val PINNED_PROJECTS = stringPreferencesKey("pinned_projects")

    // 智能排序开关
    val SMART_SORTING_ENABLED = booleanPreferencesKey("smart_sorting_enabled")

    // Toast 位置
    val TOAST_POSITION = stringPreferencesKey("toast_position")
    // Toast 边框开关
    val TOAST_BORDER_ENABLED = booleanPreferencesKey("toast_border_enabled")

    val EDITOR_WORD_WRAP = booleanPreferencesKey("editor_word_wrap")

    // 语言设置（使用 DataStore，不再用 SharedPreferences）
    val LANGUAGE_TAG = stringPreferencesKey("language_tag")

    // 【新增】十六进制颜色高亮开关
    val HEX_COLOR_HIGHLIGHT_ENABLED = booleanPreferencesKey("hex_color_highlight_enabled")

    // 【新增】滑动手势开关
    val ENABLE_SWIPE_GESTURE = booleanPreferencesKey("enable_swipe_gesture")

    // 【新增】诊断提示框开关（点击波浪线弹出建议）
    val DIAGNOSTIC_TOOLTIP_ENABLED = booleanPreferencesKey("diagnostic_tooltip_enabled")

    // 【新增】AI 流式输出开关
    val AI_STREAM_ENABLED = booleanPreferencesKey("ai_stream_enabled")

    // 【新增】首页布局模式
    val HOME_LAYOUT_MODE = stringPreferencesKey("home_layout_mode")
    // 【新增】用户自定义项目分类（JSON 数组）
    val HOME_CATEGORIES = stringPreferencesKey("home_categories")
    // 【新增】显示"继续上次项目"提示
    val HOME_SHOW_RECENT = booleanPreferencesKey("home_show_recent")
    // 【新增】是否启用分类功能
    val HOME_CATEGORY_ENABLED = booleanPreferencesKey("home_category_enabled")
    // 【新增】上次打开的项目 ID
    val LAST_OPENED_PROJECT_ID = stringPreferencesKey("last_opened_project_id")
    // 【新增】项目标签（JSON数组）
    val PROJECT_TAGS = stringPreferencesKey("project_tags")
    // 【新增】项目标签映射（JSON对象 Map<String, Set<String>>）
    val PROJECT_TAGS_MAP = stringPreferencesKey("project_tags_map")
    // 【新增】最近项目列表（JSON数组，最多5个）
    val RECENT_PROJECTS = stringPreferencesKey("recent_projects")
    // 【新增】显示项目修改时间
    val SHOW_PROJECT_MODIFIED_TIME = booleanPreferencesKey("show_project_modified_time")
    // 【新增】显示项目路径
    val SHOW_PROJECT_PATH = booleanPreferencesKey("show_project_path")
    // 【新增】卡片圆角（0小/1中/2大）
    val CARD_CORNER_RADIUS = intPreferencesKey("card_corner_radius")
    // 【新增】自动打开上次项目
    val AUTO_OPEN_LAST_PROJECT = booleanPreferencesKey("auto_open_last_project")
    // 【新增】分类栏位置（TOP/BOTTOM）
    val CATEGORY_BAR_POSITION = stringPreferencesKey("category_bar_position")
    // 【新增】卡片密度（COMPACT/COMFORTABLE/LARGE）
    val HOME_DENSITY = stringPreferencesKey("home_density")
    // 【新增】项目封面映射（JSON对象 Map<String, ProjectCover>）
    val PROJECT_COVER_MAP = stringPreferencesKey("project_cover_map")
    // 【新增】自定义项目排序（JSON数组）
    val CUSTOM_PROJECT_ORDER = stringPreferencesKey("custom_project_order")
    // 【新增】备份路径
    val BACKUP_PATH = stringPreferencesKey("backup_path")
    // 【新增】显示标签筛选栏
    val SHOW_TAG_FILTER_BAR = booleanPreferencesKey("show_tag_filter_bar")
    // 【新增】显示最近项目条
    val SHOW_RECENT_PROJECTS_BAR = booleanPreferencesKey("show_recent_projects_bar")
    // 【新增】用户自定义模板列表（JSON数组）
    val USER_TEMPLATES = stringPreferencesKey("user_templates")
    // 【新增】最近项目卡片宽度（0=紧凑120dp, 1=标准150dp, 2=宽180dp）
    val RECENT_CARD_WIDTH = intPreferencesKey("recent_card_width")
    // 【新增】最近项目卡片自定义宽度(dp)，范围80-240，优先级高于recentCardWidth
    val RECENT_CARD_WIDTH_DP = intPreferencesKey("recent_card_width_dp")
    // 【新增】回收站保留天数（默认7天，范围3-30）
    val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
}

// 排序方式枚举
enum class SortOrder {
    NAME_ASC,           // 名称 A-Z
    NAME_DESC,          // 名称 Z-A
    DATE_MODIFIED_NEWEST, // 修改时间 最新
    DATE_MODIFIED_OLDEST, // 修改时间 最早
    CUSTOM              // 自定义拖拽排序
}

// Toast 位置枚举
enum class ToastPosition {
    TOP, BOTTOM
}

// 首页布局模式
enum class HomeLayoutMode {
    CARD,   // 大卡片展开
    FLAT    // 扁平列表
}

// 卡片密度
enum class HomeDensity {
    COMPACT,    // 紧凑
    COMFORTABLE, // 舒适
    LARGE       // 大
}

// 分类栏位置
enum class CategoryBarPosition {
    TOP, BOTTOM
}

// 封面类型
enum class CoverType {
    SOLID_COLOR, IMAGE
}

// 项目标签数据类
data class ProjectTag(
    val id: String,
    val name: String,
    val color: Long = 0xFF6750A4
)

// 项目封面数据类
data class ProjectCover(
    val type: CoverType = CoverType.SOLID_COLOR,
    val colorValue: Int = 0xFF6750A4.toInt(),
    val imagePath: String = "",
    val alpha: Float = 1.0f,  // 封面透明度 0.3~1.0
    val offsetX: Float = 0f,  // 图片X轴偏移量（拖拽调整位置）
    val offsetY: Float = 0f   // 图片Y轴偏移量（拖拽调整位置）
)

// 项目分类数据类
data class ProjectCategory(
    val id: String = "",
    val name: String = "",
    val projectIds: Set<String> = emptySet(),
    val color: Long = 0xFF6750A4,
    val icon: String? = null  // 分类图标，可空，null表示使用默认图标
)

// 模板类型枚举
enum class TemplateType {
    PRESET, // 预设模板（内置assets）
    USER    // 用户自定义模板
}

// 项目模板数据类
data class ProjectTemplate(
    val id: String,
    val name: String,
    val description: String = "",
    val type: TemplateType = TemplateType.USER,
    val path: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

object SettingsManager {

    /** 单线程调度器，确保写入操作严格按顺序执行，避免并发竞态 */
    private val saveDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val saveScope = CoroutineScope(SupervisorJob() + saveDispatcher)
    /** 待处理的保存任务，用于防抖合并多次快速保存 */
    private var pendingSaveJob: Job? = null

    /** 自定义 Gson 实例，支持 Compose Color 的序列化 */
    private val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(Color::class.java, object : TypeAdapter<Color>() {
                override fun write(out: JsonWriter, value: Color) {
                    out.value(value.toArgb())
                }
                override fun read(`in`: JsonReader): Color {
                    return Color(`in`.nextInt())
                }
            })
            .setPrettyPrinting()
            .create()
    }

    @Volatile
    var settingsLoaded = false
        private set

    // 当前设置状态
    var currentSettings by mutableStateOf(SettingsData())

    // 设置变化监听器列表
    private val listeners = mutableListOf<(SettingsData) -> Unit>()

    /**
     * 获取固定项目存储路径（外部存储根目录）
     */
    private fun getFixedProjectStoragePath(): String {
        val baseDir = Environment.getExternalStorageDirectory()
        return File(baseDir, "LXC-LUA/project").absolutePath
    }

    // 注册设置变化监听器
    fun addListener(listener: (SettingsData) -> Unit) {
        listeners.add(listener)
    }

    // 移除设置变化监听器
    fun removeListener(listener: (SettingsData) -> Unit) {
        listeners.remove(listener)
    }

    // 更新设置并通知所有监听器
    fun updateSettings(newSettings: SettingsData) {
        currentSettings = newSettings
        notifyListeners()
    }

    /**
     * 从磁盘重新加载设置（用于返回主页时自愈恢复）
     * 如果 settingsLoaded 为 false（进程被杀后恢复），则从 DataStore 完整加载设置
     */
    suspend fun reloadSettingsFromDisk(context: Context) {
        if (!settingsLoaded) {
            // 进程被杀后恢复场景：settingsLoaded 为 false，需要完整加载
            loadSavedSettings(context)
            android.util.Log.d("SettingsManager", "进程恢复：设置已从 DataStore 重新加载")
            return
        }
        // 优先从 SD 卡加载（最新备份），回退到 DataStore
        val sdSettings = loadSettingsFromSdCard(context)
        if (sdSettings != null) {
            updateSettings(sdSettings)
            android.util.Log.d("SettingsManager", "设置已从 SD 卡重新加载")
            return
        }
        // SD 卡无数据，从 DataStore 重新加载
        loadSavedSettings(context)
        android.util.Log.d("SettingsManager", "设置已从 DataStore 重新加载")
    }

    // 通知所有监听器
    private fun notifyListeners() {
        listeners.forEach { listener ->
            listener(currentSettings)
        }
    }

    // 从 DataStore 异步加载设置（优先从 SD 卡加载，其次私有目录）
    suspend fun loadSavedSettings(context: Context) {
        // 先尝试从 SD 卡加载
        val sdSettings = loadSettingsFromSdCard(context)
        if (sdSettings != null) {
            updateSettings(sdSettings)
            // 同步到私有目录
            try {
                context.dataStore.edit { preferences ->
                    preferences[PreferencesKeys.THEME_TYPE] = sdSettings.themeType.name
                    preferences[PreferencesKeys.DARK_MODE] = sdSettings.darkMode.name
                    preferences[PreferencesKeys.FONT_SIZE_SCALE] = sdSettings.fontSizeScale
                    preferences[PreferencesKeys.SHAPE_SIZE_INDEX] = sdSettings.shapeSizeIndex
                    preferences[PreferencesKeys.FONT_FAMILY_TYPE] = sdSettings.fontFamilyType.name
                    preferences[PreferencesKeys.DYNAMIC_COLOR] = sdSettings.dynamicColor
                    preferences[PreferencesKeys.EDITOR_FONT_TYPE] = sdSettings.editorFontType.name
                    preferences[PreferencesKeys.CUSTOM_FONT_PATH] = sdSettings.customFontPath
                    preferences[PreferencesKeys.ENABLE_TAB_HISTORY] = sdSettings.enableTabHistory
                    preferences[PreferencesKeys.INDENT_GUIDE_ENABLED] = sdSettings.indentGuideEnabled
                    preferences[PreferencesKeys.PROJECT_STORAGE_PATH] = sdSettings.projectStoragePath
                    preferences[PreferencesKeys.ADDITIONAL_PROJECT_PATHS] = Gson().toJson(sdSettings.additionalProjectPaths)
                    preferences[PreferencesKeys.CLASS_NAME_COLOR] = sdSettings.classNameColor.toArgb()
                    preferences[PreferencesKeys.LOCAL_VAR_COLOR] = sdSettings.localVariableColor.toArgb()
                    preferences[PreferencesKeys.KEYWORD_COLOR] = sdSettings.keywordColor.toArgb()
                    preferences[PreferencesKeys.FUNCTION_NAME_COLOR] = sdSettings.functionNameColor.toArgb()
                    preferences[PreferencesKeys.LITERAL_COLOR] = sdSettings.literalColor.toArgb()
                    preferences[PreferencesKeys.COMMENT_COLOR] = sdSettings.commentColor.toArgb()
                    preferences[PreferencesKeys.SELECTED_LINE_COLOR] = sdSettings.selectedLineColor.toArgb()
                    preferences[PreferencesKeys.COMPLETION_CASE_SENSITIVE] = sdSettings.completionCaseSensitive
                    preferences[PreferencesKeys.SELECTED_APP_ICON] = sdSettings.selectedAppIcon.name
                    preferences[PreferencesKeys.SORT_ORDER] = sdSettings.sortOrder.name
                    preferences[PreferencesKeys.PINNED_PROJECTS] = Gson().toJson(sdSettings.pinnedProjects)
                    preferences[PreferencesKeys.SMART_SORTING_ENABLED] = sdSettings.smartSortingEnabled
                    preferences[PreferencesKeys.TOAST_POSITION] = sdSettings.toastPosition.name
                    preferences[PreferencesKeys.TOAST_BORDER_ENABLED] = sdSettings.toastBorderEnabled
                    preferences[PreferencesKeys.EDITOR_WORD_WRAP] = sdSettings.editorWordWrap
                    preferences[PreferencesKeys.LANGUAGE_TAG] = sdSettings.languageTag
                    preferences[PreferencesKeys.HEX_COLOR_HIGHLIGHT_ENABLED] = sdSettings.hexColorHighlightEnabled
                    preferences[PreferencesKeys.ENABLE_SWIPE_GESTURE] = sdSettings.enableSwipeGesture
                    preferences[PreferencesKeys.DIAGNOSTIC_TOOLTIP_ENABLED] = sdSettings.diagnosticTooltipEnabled
                    preferences[PreferencesKeys.AI_STREAM_ENABLED] = sdSettings.aiStreamEnabled
                }
            } catch (_: Exception) { }
            settingsLoaded = true
            android.util.Log.d("SettingsManager", "[SD卡] 设置已加载")
            return
        }

        // SD 卡没有配置，从私有目录加载
        val preferences = context.dataStore.data.first()

        val themeType = ThemeType.valueOf(
            preferences[PreferencesKeys.THEME_TYPE] ?: "BLUE"
        )
        val darkMode = DarkMode.valueOf(
            preferences[PreferencesKeys.DARK_MODE] ?: "FOLLOW_SYSTEM"
        )
        val fontSizeScale = preferences[PreferencesKeys.FONT_SIZE_SCALE] ?: 1.0f
        val shapeSizeIndex = preferences[PreferencesKeys.SHAPE_SIZE_INDEX] ?: 2
        val fontFamilyType = FontFamilyType.valueOf(
            preferences[PreferencesKeys.FONT_FAMILY_TYPE] ?: "DEFAULT"
        )
        val dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: false
        val editorFontType = EditorFontType.valueOf(
            preferences[PreferencesKeys.EDITOR_FONT_TYPE] ?: "GEORGIA_MONO_ITALIC"
        )
        val customFontPath = preferences[PreferencesKeys.CUSTOM_FONT_PATH] ?: ""
        val enableTabHistory = preferences[PreferencesKeys.ENABLE_TAB_HISTORY] ?: false
        val indentGuideEnabled = preferences[PreferencesKeys.INDENT_GUIDE_ENABLED] ?: true

        val fixedPath = getFixedProjectStoragePath()

        val classNameColor = preferences[PreferencesKeys.CLASS_NAME_COLOR] ?: 0xFF6E81D9.toInt()
        val localVariableColor = preferences[PreferencesKeys.LOCAL_VAR_COLOR] ?: 0xFFAAAA88.toInt()
        val keywordColor = preferences[PreferencesKeys.KEYWORD_COLOR] ?: 0xFFFF565E.toInt()
        val functionNameColor =
            preferences[PreferencesKeys.FUNCTION_NAME_COLOR] ?: 0xFF2196F3.toInt()
        val literalColor = preferences[PreferencesKeys.LITERAL_COLOR] ?: 0xFF008080.toInt()
        val commentColor = preferences[PreferencesKeys.COMMENT_COLOR] ?: 0xFFA7A8A8.toInt()
        val selectedLineColor =
            preferences[PreferencesKeys.SELECTED_LINE_COLOR] ?: 0x33000000

        // 加载补全大小写敏感设置项
        val completionCaseSensitive =
            preferences[PreferencesKeys.COMPLETION_CASE_SENSITIVE] ?: false

        val selectedAppIconName = preferences[PreferencesKeys.SELECTED_APP_ICON] ?: "DEFAULT"
        val selectedAppIcon = try {
            IconManager.AppIcon.valueOf(selectedAppIconName)
        } catch (_: Exception) {
            IconManager.AppIcon.DEFAULT
        }

        // 加载排序方式
        val sortOrderName = preferences[PreferencesKeys.SORT_ORDER] ?: "NAME_ASC"
        val sortOrder = try {
            SortOrder.valueOf(sortOrderName)
        } catch (_: Exception) {
            SortOrder.NAME_ASC
        }

        // 加载置顶项目列表（存储为 JSON 字符串）
        val pinnedProjectsJson = preferences[PreferencesKeys.PINNED_PROJECTS] ?: "[]"
        val pinnedProjects: Set<String> = try {
            val type = object : TypeToken<Set<String>>() {}.type
            Gson().fromJson(pinnedProjectsJson, type)
        } catch (_: Exception) {
            emptySet()
        }

        // 加载智能排序开关
        val smartSortingEnabled = preferences[PreferencesKeys.SMART_SORTING_ENABLED] ?: false

        // 加载 Toast 位置
        val toastPositionName = preferences[PreferencesKeys.TOAST_POSITION] ?: "BOTTOM"
        val toastPosition = try {
            ToastPosition.valueOf(toastPositionName)
        } catch (_: Exception) {
            ToastPosition.BOTTOM
        }

        // 加载 Toast 边框开关
        val toastBorderEnabled = preferences[PreferencesKeys.TOAST_BORDER_ENABLED] ?: false

        val editorWordWrap = preferences[PreferencesKeys.EDITOR_WORD_WRAP] ?: false

        // 从 DataStore 加载语言设置
        val languageTag = preferences[PreferencesKeys.LANGUAGE_TAG] ?: "zh"

        // 【新增】加载十六进制颜色高亮开关
        val hexColorHighlightEnabled = preferences[PreferencesKeys.HEX_COLOR_HIGHLIGHT_ENABLED] ?: false

        // 【新增】加载滑动手势开关
        val enableSwipeGesture = preferences[PreferencesKeys.ENABLE_SWIPE_GESTURE] ?: false

        // 【新增】加载诊断提示框开关
        val diagnosticTooltipEnabled = preferences[PreferencesKeys.DIAGNOSTIC_TOOLTIP_ENABLED] ?: true

        // 【新增】加载 AI 流式输出开关
        val aiStreamEnabled = preferences[PreferencesKeys.AI_STREAM_ENABLED] ?: false

        // 【新增】加载首页设置
        val homeLayoutModeName = preferences[PreferencesKeys.HOME_LAYOUT_MODE] ?: "CARD"
        val homeLayoutMode = try { HomeLayoutMode.valueOf(homeLayoutModeName) } catch (_: Exception) { HomeLayoutMode.CARD }
        val homeCategoriesJson = preferences[PreferencesKeys.HOME_CATEGORIES] ?: "[]"
        val homeCategories: List<ProjectCategory> = try {
            val type = object : TypeToken<List<ProjectCategory>>() {}.type
            val raw: List<ProjectCategory> = Gson().fromJson(homeCategoriesJson, type) ?: emptyList()
            // 迁移：确保icon字段安全（旧数据可能无icon字段，Gson默认null，String?可接受）
            raw.map { cat ->
                // copy不传icon时默认保留原值，显式传递cat.icon确保无异常
                cat.copy(icon = cat.icon)
            }
        } catch (_: Exception) { emptyList() }
        val homeShowRecent = preferences[PreferencesKeys.HOME_SHOW_RECENT] ?: true
        val homeCategoryEnabled = preferences[PreferencesKeys.HOME_CATEGORY_ENABLED] ?: true
        val lastOpenedProjectId = preferences[PreferencesKeys.LAST_OPENED_PROJECT_ID] ?: ""

        // 加载项目标签
        val projectTagsJson = preferences[PreferencesKeys.PROJECT_TAGS] ?: "[]"
        val homeProjectTags: List<ProjectTag> = try {
            val type = object : TypeToken<List<ProjectTag>>() {}.type
            Gson().fromJson(projectTagsJson, type)
        } catch (_: Exception) { emptyList() }
        val projectTagsMapJson = preferences[PreferencesKeys.PROJECT_TAGS_MAP] ?: "{}"
        val projectTagsMap: Map<String, Set<String>> = try {
            val type = object : TypeToken<Map<String, Set<String>>>() {}.type
            Gson().fromJson(projectTagsMapJson, type)
        } catch (_: Exception) { emptyMap() }

        // 加载最近项目
        val recentProjectsJson = preferences[PreferencesKeys.RECENT_PROJECTS] ?: "[]"
        val recentProjects: List<String> = try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(recentProjectsJson, type)
        } catch (_: Exception) { emptyList() }

        // 加载首页显示开关
        val showProjectModifiedTime = preferences[PreferencesKeys.SHOW_PROJECT_MODIFIED_TIME] ?: true
        val showProjectPath = preferences[PreferencesKeys.SHOW_PROJECT_PATH] ?: true
        val cardCornerRadius = preferences[PreferencesKeys.CARD_CORNER_RADIUS] ?: 1
        val autoOpenLastProject = preferences[PreferencesKeys.AUTO_OPEN_LAST_PROJECT] ?: false
        val showTagFilterBar = preferences[PreferencesKeys.SHOW_TAG_FILTER_BAR] ?: true
        val showRecentProjectsBar = preferences[PreferencesKeys.SHOW_RECENT_PROJECTS_BAR] ?: true

        // 加载分类栏位置
        val categoryBarPositionName = preferences[PreferencesKeys.CATEGORY_BAR_POSITION] ?: "TOP"
        val categoryBarPosition = try { CategoryBarPosition.valueOf(categoryBarPositionName) } catch (_: Exception) { CategoryBarPosition.TOP }

        // 加载卡片密度
        val homeDensityName = preferences[PreferencesKeys.HOME_DENSITY] ?: "COMFORTABLE"
        val homeDensity = try { HomeDensity.valueOf(homeDensityName) } catch (_: Exception) { HomeDensity.COMFORTABLE }

        // 加载项目封面映射
        val projectCoverMapJson = preferences[PreferencesKeys.PROJECT_COVER_MAP] ?: "{}"
        val projectCoverMap: Map<String, ProjectCover> = try {
            val type = object : TypeToken<Map<String, ProjectCover>>() {}.type
            val raw: Map<String, ProjectCover> = Gson().fromJson(projectCoverMapJson, type) ?: emptyMap()
            // 迁移旧数据：alpha为0（Gson默认值）时修正为1.0f
            raw.mapValues { (_, cover) ->
                var migrated = cover
                if (migrated.alpha <= 0f) migrated = migrated.copy(alpha = 1.0f)
                migrated
            }
        } catch (_: Exception) { emptyMap() }

        // 加载自定义排序
        val customProjectOrderJson = preferences[PreferencesKeys.CUSTOM_PROJECT_ORDER] ?: "[]"
        val customProjectOrder: List<String> = try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(customProjectOrderJson, type)
        } catch (_: Exception) { emptyList() }

        // 加载备份路径
        val backupPath = preferences[PreferencesKeys.BACKUP_PATH] ?: ""

        // 加载用户自定义模板列表
        val userTemplatesJson = preferences[PreferencesKeys.USER_TEMPLATES] ?: "[]"
        val userTemplates: List<ProjectTemplate> = try {
            val type = object : TypeToken<List<ProjectTemplate>>() {}.type
            Gson().fromJson(userTemplatesJson, type)
        } catch (_: Exception) { emptyList() }

        // 加载最近项目卡片宽度（默认1=标准）
        val recentCardWidth = preferences[PreferencesKeys.RECENT_CARD_WIDTH] ?: 1
        // 加载最近项目卡片自定义宽度(dp)，0表示未设置（使用旧枚举映射）
        val recentCardWidthDpRaw = preferences[PreferencesKeys.RECENT_CARD_WIDTH_DP] ?: 0
        // 迁移：如果未设置自定义宽度，从旧枚举映射
        val recentCardWidthDp = if (recentCardWidthDpRaw in 80..240) {
            recentCardWidthDpRaw
        } else {
            when (recentCardWidth) {
                0 -> 120
                2 -> 180
                else -> 150
            }
        }

        // 回收站保留天数
        val trashRetentionDays = (preferences[PreferencesKeys.TRASH_RETENTION_DAYS] ?: 7).coerceIn(3, 30)

        val additionalProjectPathsJson = preferences[PreferencesKeys.ADDITIONAL_PROJECT_PATHS] ?: "[]"
        val additionalProjectPaths: List<String> = try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(additionalProjectPathsJson, type)
        } catch (_: Exception) {
            emptyList()
        }

        val storedProjectPath = preferences[PreferencesKeys.PROJECT_STORAGE_PATH] ?: ""
        val projectStoragePath = storedProjectPath.ifBlank { fixedPath }

        updateSettings(
            SettingsData(
                themeType = themeType,
                darkMode = darkMode,
                projectStoragePath = projectStoragePath,
                additionalProjectPaths = additionalProjectPaths,
                fontSizeScale = fontSizeScale,
                shapeSizeIndex = shapeSizeIndex,
                fontFamilyType = fontFamilyType,
                dynamicColor = dynamicColor,
                editorFontType = editorFontType,
                customFontPath = customFontPath,
                enableTabHistory = enableTabHistory,
                classNameColor = Color(classNameColor),
                localVariableColor = Color(localVariableColor),
                keywordColor = Color(keywordColor),
                functionNameColor = Color(functionNameColor),
                literalColor = Color(literalColor),
                commentColor = Color(commentColor),
                selectedLineColor = Color(selectedLineColor),
                indentGuideEnabled = indentGuideEnabled,
                selectedAppIcon = selectedAppIcon,
                completionCaseSensitive = completionCaseSensitive,
                sortOrder = sortOrder,
                pinnedProjects = pinnedProjects,
                smartSortingEnabled = smartSortingEnabled,
                toastPosition = toastPosition,
                toastBorderEnabled = toastBorderEnabled,
                editorWordWrap = editorWordWrap,
                languageTag = languageTag,
                hexColorHighlightEnabled = hexColorHighlightEnabled,
                enableSwipeGesture = enableSwipeGesture,  // 【新增】
                diagnosticTooltipEnabled = diagnosticTooltipEnabled,  // 【新增】
                aiStreamEnabled = aiStreamEnabled,         // 【新增】
                homeLayoutMode = homeLayoutMode,          // 【新增】
                homeCategories = homeCategories,          // 【新增】
                homeCategoryEnabled = homeCategoryEnabled, // 【新增】
                homeProjectTags = homeProjectTags,        // 【新增】
                projectTagsMap = projectTagsMap,          // 【新增】
                recentProjects = recentProjects,          // 【新增】
                showProjectModifiedTime = showProjectModifiedTime, // 【新增】
                showProjectPath = showProjectPath,        // 【新增】
                cardCornerRadius = cardCornerRadius,      // 【新增】
                autoOpenLastProject = autoOpenLastProject, // 【新增】
                categoryBarPosition = categoryBarPosition, // 【新增】
                homeDensity = homeDensity,                // 【新增】
                projectCoverMap = projectCoverMap,        // 【新增】
                customProjectOrder = customProjectOrder,  // 【新增】
                backupPath = backupPath,                  // 【新增】
                showTagFilterBar = showTagFilterBar,      // 【新增】
                showRecentProjectsBar = showRecentProjectsBar, // 【新增】
                homeShowRecent = homeShowRecent,          // 【新增】
                lastOpenedProjectId = lastOpenedProjectId, // 【新增】
                userTemplates = userTemplates,            // 【新增】用户自定义模板
                recentCardWidth = recentCardWidth,        // 【新增】最近项目卡片宽度
                recentCardWidthDp = recentCardWidthDp,    // 【新增】最近项目卡片自定义宽度(dp)
                trashRetentionDays = trashRetentionDays,  // 【新增】回收站保留天数
            )
        )
        settingsLoaded = true
        // 加载完成后同步到 SD 卡（创建备份）
        saveSettingsToSdCard(context)
    }

    // 异步保存设置到 DataStore（同时保存到 SD 卡）
    suspend fun saveSettingsAsync(context: Context) {
        if (!settingsLoaded) {
            return
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_TYPE] = currentSettings.themeType.name
            preferences[PreferencesKeys.DARK_MODE] = currentSettings.darkMode.name
            preferences[PreferencesKeys.FONT_SIZE_SCALE] = currentSettings.fontSizeScale
            preferences[PreferencesKeys.SHAPE_SIZE_INDEX] = currentSettings.shapeSizeIndex
            preferences[PreferencesKeys.FONT_FAMILY_TYPE] = currentSettings.fontFamilyType.name
            preferences[PreferencesKeys.DYNAMIC_COLOR] = currentSettings.dynamicColor
            preferences[PreferencesKeys.EDITOR_FONT_TYPE] = currentSettings.editorFontType.name
            preferences[PreferencesKeys.CUSTOM_FONT_PATH] = currentSettings.customFontPath
            preferences[PreferencesKeys.ENABLE_TAB_HISTORY] = currentSettings.enableTabHistory
            preferences[PreferencesKeys.INDENT_GUIDE_ENABLED] = currentSettings.indentGuideEnabled
            preferences[PreferencesKeys.PROJECT_STORAGE_PATH] = currentSettings.projectStoragePath

            val additionalPathsJson = Gson().toJson(currentSettings.additionalProjectPaths)
            preferences[PreferencesKeys.ADDITIONAL_PROJECT_PATHS] = additionalPathsJson

            preferences[PreferencesKeys.CLASS_NAME_COLOR] = currentSettings.classNameColor.toArgb()
            preferences[PreferencesKeys.LOCAL_VAR_COLOR] =
                currentSettings.localVariableColor.toArgb()
            preferences[PreferencesKeys.KEYWORD_COLOR] = currentSettings.keywordColor.toArgb()
            preferences[PreferencesKeys.FUNCTION_NAME_COLOR] =
                currentSettings.functionNameColor.toArgb()
            preferences[PreferencesKeys.LITERAL_COLOR] = currentSettings.literalColor.toArgb()
            preferences[PreferencesKeys.COMMENT_COLOR] = currentSettings.commentColor.toArgb()
            preferences[PreferencesKeys.SELECTED_LINE_COLOR] =
                currentSettings.selectedLineColor.toArgb()

            preferences[PreferencesKeys.COMPLETION_CASE_SENSITIVE] =
                currentSettings.completionCaseSensitive

            preferences[PreferencesKeys.SELECTED_APP_ICON] = currentSettings.selectedAppIcon.name

            preferences[PreferencesKeys.SORT_ORDER] = currentSettings.sortOrder.name

            val pinnedJson = Gson().toJson(currentSettings.pinnedProjects)
            preferences[PreferencesKeys.PINNED_PROJECTS] = pinnedJson

            preferences[PreferencesKeys.SMART_SORTING_ENABLED] = currentSettings.smartSortingEnabled

            preferences[PreferencesKeys.TOAST_POSITION] = currentSettings.toastPosition.name

            preferences[PreferencesKeys.TOAST_BORDER_ENABLED] = currentSettings.toastBorderEnabled

            preferences[PreferencesKeys.EDITOR_WORD_WRAP] = currentSettings.editorWordWrap

            // 保存语言设置到 DataStore
            preferences[PreferencesKeys.LANGUAGE_TAG] = currentSettings.languageTag

            // 【新增】保存十六进制颜色高亮开关
            preferences[PreferencesKeys.HEX_COLOR_HIGHLIGHT_ENABLED] = currentSettings.hexColorHighlightEnabled

            // 【新增】保存滑动手势开关
            preferences[PreferencesKeys.ENABLE_SWIPE_GESTURE] = currentSettings.enableSwipeGesture

            // 【新增】保存诊断提示框开关
            preferences[PreferencesKeys.DIAGNOSTIC_TOOLTIP_ENABLED] = currentSettings.diagnosticTooltipEnabled

            // 【新增】保存 AI 流式输出开关
            preferences[PreferencesKeys.AI_STREAM_ENABLED] = currentSettings.aiStreamEnabled

            // 【新增】保存首页设置
            preferences[PreferencesKeys.HOME_LAYOUT_MODE] = currentSettings.homeLayoutMode.name
            val homeCategoriesJson = Gson().toJson(currentSettings.homeCategories)
            preferences[PreferencesKeys.HOME_CATEGORIES] = homeCategoriesJson
            preferences[PreferencesKeys.HOME_SHOW_RECENT] = currentSettings.homeShowRecent
            preferences[PreferencesKeys.HOME_CATEGORY_ENABLED] = currentSettings.homeCategoryEnabled
            preferences[PreferencesKeys.CATEGORY_BAR_POSITION] = currentSettings.categoryBarPosition.name
            preferences[PreferencesKeys.HOME_DENSITY] = currentSettings.homeDensity.name
            preferences[PreferencesKeys.LAST_OPENED_PROJECT_ID] = currentSettings.lastOpenedProjectId

            // 保存标签相关
            preferences[PreferencesKeys.PROJECT_TAGS] = Gson().toJson(currentSettings.homeProjectTags)
            preferences[PreferencesKeys.PROJECT_TAGS_MAP] = Gson().toJson(currentSettings.projectTagsMap)

            // 保存最近项目
            preferences[PreferencesKeys.RECENT_PROJECTS] = Gson().toJson(currentSettings.recentProjects)

            // 保存首页显示开关
            preferences[PreferencesKeys.SHOW_PROJECT_MODIFIED_TIME] = currentSettings.showProjectModifiedTime
            preferences[PreferencesKeys.SHOW_PROJECT_PATH] = currentSettings.showProjectPath
            preferences[PreferencesKeys.CARD_CORNER_RADIUS] = currentSettings.cardCornerRadius
            preferences[PreferencesKeys.AUTO_OPEN_LAST_PROJECT] = currentSettings.autoOpenLastProject
            preferences[PreferencesKeys.SHOW_TAG_FILTER_BAR] = currentSettings.showTagFilterBar
            preferences[PreferencesKeys.SHOW_RECENT_PROJECTS_BAR] = currentSettings.showRecentProjectsBar

            // 保存分类栏位置和密度
            preferences[PreferencesKeys.CATEGORY_BAR_POSITION] = currentSettings.categoryBarPosition.name
            preferences[PreferencesKeys.HOME_DENSITY] = currentSettings.homeDensity.name

            // 保存封面和自定义排序
            preferences[PreferencesKeys.PROJECT_COVER_MAP] = Gson().toJson(currentSettings.projectCoverMap)
            preferences[PreferencesKeys.CUSTOM_PROJECT_ORDER] = Gson().toJson(currentSettings.customProjectOrder)

            // 保存备份路径
            preferences[PreferencesKeys.BACKUP_PATH] = currentSettings.backupPath

            // 保存用户自定义模板列表
            preferences[PreferencesKeys.USER_TEMPLATES] = Gson().toJson(currentSettings.userTemplates)

            // 保存最近项目卡片宽度
            preferences[PreferencesKeys.RECENT_CARD_WIDTH] = currentSettings.recentCardWidth
            preferences[PreferencesKeys.RECENT_CARD_WIDTH_DP] = currentSettings.recentCardWidthDp
            // 保存回收站保留天数
            preferences[PreferencesKeys.TRASH_RETENTION_DAYS] = currentSettings.trashRetentionDays
        }
        // 同步保存到 SD 卡
        saveSettingsToSdCard(context)
        notifyListeners()
        // 触发设置变更事件，传递当前完整设置的 JSON
        try {
            EventManager.fireEvent(PluginEvents.ON_SETTINGS_CHANGED, Gson().toJson(currentSettings))
        } catch (_: Exception) {}
    }

    /**
     * 保存设置（防抖：合并短时间内的多次调用，只执行最后一次写入）
     * 使用单线程调度器确保写入严格按顺序执行，避免并发竞态导致数据丢失
     */
    fun saveSettings(context: Context) {
        if (!settingsLoaded) {
            return
        }
        // 取消之前的待处理保存任务，合并为一次写入
        pendingSaveJob?.cancel()
        pendingSaveJob = saveScope.launch {
            // 延迟200ms，合并快速连续的多次调用
            delay(200)
            saveSettingsAsync(context)
        }
    }

    /**
     * 立即同步保存设置（跳过防抖），用于关键场景如退出前保存
     */
    fun saveSettingsImmediate(context: Context) {
        if (!settingsLoaded) {
            return
        }
        pendingSaveJob?.cancel()
        pendingSaveJob = saveScope.launch {
            saveSettingsAsync(context)
        }
    }

    /**
     * 确保项目目录存在
     */
    fun ensureProjectDirectoryExists(): Boolean {
        val projectDir = File(currentSettings.projectStoragePath)
        return try {
            if (!projectDir.exists()) {
                val created = projectDir.mkdirs()
                if (!created) {
                    try {
                        Runtime.getRuntime().exec(arrayOf("mkdir", "-p", projectDir.absolutePath))
                        Thread.sleep(200)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            projectDir.exists() && projectDir.canWrite()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 设置应用语言（兼容 Android 13+ 和旧版本）
     * 会自动重启 Activity 使语言生效
     */
    fun setAppLanguage(context: Context, languageTag: String) {
        // 更新内存中的设置
        val newSettings = currentSettings.copy(languageTag = languageTag)
        updateSettings(newSettings)

        // 异步保存到 DataStore
        if (settingsLoaded) {
            saveScope.launch {
                context.dataStore.edit { preferences ->
                    preferences[PreferencesKeys.LANGUAGE_TAG] = languageTag
                }
            }
        }

        // 设置系统语言
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService<LocaleManager>()
            localeManager?.applicationLocales = LocaleList.forLanguageTags(languageTag)
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }

    }

    /**
     * 同步加载语言设置（用于启动时）
     * 从 DataStore 读取，如果失败返回默认值
     */
    fun loadLanguageSync(context: Context): String {
        return try {
            // 尝试从 DataStore 同步读取（使用 runBlocking 或直接访问）
            // 但由于 DataStore 是异步的，这里用 currentSettings 作为回退
            currentSettings.languageTag
        } catch (_: Exception) {
            "zh"
        }
    }

    // ========== SD 卡配置同步 ==========

    /**
     * 获取 SD 卡配置目录
     */
    private fun getSdCardConfigDir(): File? {
        return try {
            val sdRoot = Environment.getExternalStorageDirectory()
            val configDir = File(sdRoot, "LXC-LUA/config")
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            if (configDir.exists() && configDir.canWrite()) configDir else null
        } catch (e: Exception) {
            android.util.Log.w("SettingsManager", "获取 SD 卡配置目录失败: ${e.message}")
            null
        }
    }

    /** 应用设置的 SD 卡文件路径 */
    private fun getSdCardSettingsFile(): File? {
        return getSdCardConfigDir()?.let { File(it, "app_settings.json") }
    }

    /**
     * 保存上次打开的项目 ID
     * 在打开项目时调用，用于首页"继续上次项目"功能
     */
    fun saveLastOpenedProject(projectId: String, context: Context) {
        val current = currentSettings
        val newSettings = current.copy(
            lastOpenedProjectId = projectId,
            homeLayoutMode = current.homeLayoutMode ?: HomeLayoutMode.CARD,
            homeCategories = current.homeCategories ?: emptyList(),
        )
        updateSettings(newSettings)
        saveSettings(context)
    }

    /**
     * 添加项目到最近打开列表（FIFO，去重，最多5个）
     */
    fun pushRecentProject(projectId: String, context: Context) {
        val current = currentSettings
        val list = current.recentProjects.toMutableList()
        list.remove(projectId) // 去重
        list.add(0, projectId) // 添加到头部
        val trimmed = if (list.size > 5) list.take(5) else list
        val newSettings = current.copy(recentProjects = trimmed)
        updateSettings(newSettings)
        saveSettings(context)
    }

    /**
     * 获取备份目录
     */
    fun getBackupDirectory(): File {
        val path = currentSettings.backupPath
        val dir = if (path.isNotBlank()) {
            File(path)
        } else {
            File(Environment.getExternalStorageDirectory(), "LXC-LUA/backups")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 获取用户模板目录
     */
    fun getTemplatesDirectory(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "LXC-LUA/templates")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 添加用户模板到设置
     * @param template 要添加的模板
     * @param context 上下文用于保存设置
     */
    fun addUserTemplate(template: ProjectTemplate, context: Context) {
        val current = currentSettings
        val list = current.userTemplates.toMutableList()
        // 移除同ID模板（去重）
        list.removeAll { it.id == template.id }
        list.add(template)
        val newSettings = current.copy(userTemplates = list)
        updateSettings(newSettings)
        saveSettings(context)
    }

    /**
     * 删除用户模板
     * @param templateId 模板ID
     * @param context 上下文用于保存设置
     * @param deleteFile 是否同时删除模板zip文件
     */
    fun removeUserTemplate(templateId: String, context: Context, deleteFile: Boolean = true) {
        val current = currentSettings
        val list = current.userTemplates.toMutableList()
        val removed = list.find { it.id == templateId }
        list.removeAll { it.id == templateId }
        val newSettings = current.copy(userTemplates = list)
        updateSettings(newSettings)
        saveSettings(context)
        // 同时删除文件
        if (deleteFile && removed != null && removed.path.isNotBlank()) {
            try {
                val file = File(removed.path)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }
    }

    /**
     * 根据ID获取用户模板
     */
    fun getUserTemplate(templateId: String): ProjectTemplate? {
        return currentSettings.userTemplates.find { it.id == templateId }
    }

    /**
     * 设置项目标签
     */
    fun setProjectTags(projectId: String, tagIds: Set<String>, context: Context) {
        val current = currentSettings
        val newMap = current.projectTagsMap.toMutableMap()
        newMap[projectId] = tagIds
        val newSettings = current.copy(projectTagsMap = newMap)
        updateSettings(newSettings)
        saveSettings(context)
    }

    /**
     * 设置项目封面
     */
    fun setProjectCover(projectId: String, cover: ProjectCover?, context: Context) {
        val current = currentSettings
        val newMap = current.projectCoverMap.toMutableMap()
        if (cover != null) {
            newMap[projectId] = cover
        } else {
            newMap.remove(projectId)
        }
        val newSettings = current.copy(projectCoverMap = newMap)
        updateSettings(newSettings)
        saveSettings(context)
    }

    /**
     * 更新自定义项目排序
     */
    fun updateCustomOrder(orderedIds: List<String>, context: Context) {
        val current = currentSettings
        val newSettings = current.copy(customProjectOrder = orderedIds)
        updateSettings(newSettings)
        saveSettings(context)
    }

    /**
     * 保存设置到 SD 卡（JSON 格式）
     * 失败时静默忽略，不影响主流程
     */
    private fun saveSettingsToSdCard(context: Context) {
        try {
            val file = getSdCardSettingsFile() ?: return
            val json = gson.toJson(currentSettings)
            file.writeText(json, Charsets.UTF_8)
            android.util.Log.d("SettingsManager", "[SD卡] 设置已保存: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.w("SettingsManager", "[SD卡] 保存设置失败: ${e.message}")
        }
    }

    /**
     * 从 SD 卡加载设置
     * @return 设置数据，失败返回 null
     */
    private fun loadSettingsFromSdCard(context: Context): SettingsData? {
        return try {
            val file = getSdCardSettingsFile() ?: return null
            if (!file.exists() || !file.canRead()) return null
            val json = file.readText(Charsets.UTF_8)
            val settings = gson.fromJson(json, SettingsData::class.java)
            // Gson 绕过构造函数，新字段可能为 null，需要兜底
            // 不能用 copy()（会触发 Kotlin 非空检查），故直接构造
            val safe = SettingsData(
                themeType = settings.themeType ?: ThemeType.BLUE,
                darkMode = settings.darkMode ?: DarkMode.FOLLOW_SYSTEM,
                projectStoragePath = settings.projectStoragePath ?: "/storage/emulated/0/LXC-LUA/project/",
                additionalProjectPaths = settings.additionalProjectPaths ?: emptyList(),
                fontSizeScale = settings.fontSizeScale,
                shapeSizeIndex = settings.shapeSizeIndex,
                fontFamilyType = settings.fontFamilyType ?: FontFamilyType.DEFAULT,
                dynamicColor = settings.dynamicColor,
                editorFontType = settings.editorFontType ?: EditorFontType.GEORGIA_MONO_ITALIC,
                customFontPath = settings.customFontPath ?: "",
                enableTabHistory = settings.enableTabHistory,
                classNameColor = settings.classNameColor,
                localVariableColor = settings.localVariableColor,
                keywordColor = settings.keywordColor,
                functionNameColor = settings.functionNameColor,
                literalColor = settings.literalColor,
                commentColor = settings.commentColor,
                selectedLineColor = settings.selectedLineColor,
                indentGuideEnabled = settings.indentGuideEnabled,
                selectedAppIcon = settings.selectedAppIcon ?: IconManager.AppIcon.DEFAULT,
                completionCaseSensitive = settings.completionCaseSensitive,
                sortOrder = settings.sortOrder ?: SortOrder.NAME_ASC,
                pinnedProjects = settings.pinnedProjects ?: emptySet(),
                smartSortingEnabled = settings.smartSortingEnabled,
                toastPosition = settings.toastPosition ?: ToastPosition.BOTTOM,
                toastBorderEnabled = settings.toastBorderEnabled,
                editorWordWrap = settings.editorWordWrap,
                languageTag = settings.languageTag ?: "zh",
                hexColorHighlightEnabled = settings.hexColorHighlightEnabled,
                enableSwipeGesture = settings.enableSwipeGesture,
                diagnosticTooltipEnabled = settings.diagnosticTooltipEnabled,
                aiStreamEnabled = settings.aiStreamEnabled,
                homeLayoutMode = settings.homeLayoutMode ?: HomeLayoutMode.CARD,
                // 迁移分类数据：确保icon字段不为非空null，修复旧数据Gson反序列化问题
                homeCategories = (settings.homeCategories ?: emptyList()).map { cat ->
                    cat.copy(icon = cat.icon)  // icon已改为可空，copy时直接传递
                },
                homeCategoryEnabled = settings.homeCategoryEnabled,
                homeProjectTags = settings.homeProjectTags ?: emptyList(),
                projectTagsMap = settings.projectTagsMap ?: emptyMap(),
                recentProjects = settings.recentProjects ?: emptyList(),
                showProjectModifiedTime = settings.showProjectModifiedTime,
                showProjectPath = settings.showProjectPath,
                cardCornerRadius = settings.cardCornerRadius,
                autoOpenLastProject = settings.autoOpenLastProject,
                categoryBarPosition = settings.categoryBarPosition ?: CategoryBarPosition.TOP,
                homeDensity = settings.homeDensity ?: HomeDensity.COMFORTABLE,
                // 迁移封面数据：alpha为0时修正为1.0f，旧数据无offsetX/offsetY时默认为0
                projectCoverMap = (settings.projectCoverMap ?: emptyMap()).mapValues { (_, cover) ->
                    var migrated = cover
                    if (migrated.alpha <= 0f) migrated = migrated.copy(alpha = 1.0f)
                    // Gson反序列化新字段缺失时会设为0f（Float默认值），0f是合理默认值，无需额外处理
                    migrated
                },
                customProjectOrder = settings.customProjectOrder ?: emptyList(),
                backupPath = settings.backupPath ?: "",
                showTagFilterBar = settings.showTagFilterBar,
                showRecentProjectsBar = settings.showRecentProjectsBar,
                homeShowRecent = settings.homeShowRecent,
                lastOpenedProjectId = settings.lastOpenedProjectId ?: "",
                userTemplates = settings.userTemplates ?: emptyList(),
                recentCardWidth = settings.recentCardWidth.takeIf { it in 0..2 } ?: 1,
                recentCardWidthDp = (settings.recentCardWidthDp as? Int)?.takeIf { it in 80..240} ?: when(settings.recentCardWidth.takeIf { it in 0..2 } ?: 1) {
                    0 -> 120; 2 -> 180; else -> 150
                },
            )
            android.util.Log.d("SettingsManager", "[SD卡] 设置已加载: ${file.absolutePath}")
            safe
        } catch (e: Exception) {
            android.util.Log.w("SettingsManager", "[SD卡] 加载设置失败: ${e.message}")
            null
        }
    }
}

data class SettingsData(
    val themeType: ThemeType = ThemeType.BLUE,
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    val projectStoragePath: String = "/storage/emulated/0/LXC-LUA/project/",
    val additionalProjectPaths: List<String> = emptyList(),
    val fontSizeScale: Float = 1.0f,
    val shapeSizeIndex: Int = 2,
    val fontFamilyType: FontFamilyType = FontFamilyType.DEFAULT,
    val dynamicColor: Boolean = false,
    val editorFontType: EditorFontType = EditorFontType.GEORGIA_MONO_ITALIC,
    val customFontPath: String = "",
    val enableTabHistory: Boolean = false,
    val classNameColor: Color = Color(0xFF6E81D9),
    val localVariableColor: Color = Color(0xFFAAAA88),
    val keywordColor: Color = Color(0xFFFF565E),
    val functionNameColor: Color = Color(0xFF2196F3),
    val literalColor: Color = Color(0xFF008080),
    val commentColor: Color = Color(0xFFA7A8A8),
    val selectedLineColor: Color = Color(0x1A000000),
    val indentGuideEnabled: Boolean = true,
    val selectedAppIcon: IconManager.AppIcon = IconManager.AppIcon.DEFAULT,
    val completionCaseSensitive: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val pinnedProjects: Set<String> = emptySet(),
    val smartSortingEnabled: Boolean = false,
    val toastPosition: ToastPosition = ToastPosition.BOTTOM,
    val toastBorderEnabled: Boolean = false,
    val editorWordWrap: Boolean = false,
    val languageTag: String = "zh",
    val hexColorHighlightEnabled: Boolean = false,  // 【新增】十六进制颜色高亮开关
    val enableSwipeGesture: Boolean = false,         // 【新增】滑动手势开关
    val diagnosticTooltipEnabled: Boolean = true,    // 【新增】诊断提示框开关（点击波浪线弹出建议）
    val aiStreamEnabled: Boolean = false,             // 【新增】AI 流式输出开关
    val homeLayoutMode: HomeLayoutMode = HomeLayoutMode.CARD,  // 【新增】首页布局模式
    val homeCategories: List<ProjectCategory> = emptyList(),   // 【新增】项目分类
    val homeCategoryEnabled: Boolean = true,          // 【新增】是否启用分类功能
    val homeProjectTags: List<ProjectTag> = emptyList(), // 【新增】项目标签
    val projectTagsMap: Map<String, Set<String>> = emptyMap(), // 【新增】项目标签映射
    val recentProjects: List<String> = emptyList(), // 【新增】最近项目列表
    val showProjectModifiedTime: Boolean = true, // 【新增】显示修改时间
    val showProjectPath: Boolean = true, // 【新增】显示项目路径
    val cardCornerRadius: Int = 1, // 【新增】卡片圆角 0小/1中/2大
    val autoOpenLastProject: Boolean = false, // 【新增】自动打开上次项目
    val categoryBarPosition: CategoryBarPosition = CategoryBarPosition.TOP, // 【新增】分类栏位置
    val homeDensity: HomeDensity = HomeDensity.COMFORTABLE, // 【新增】卡片密度
    val projectCoverMap: Map<String, ProjectCover> = emptyMap(), // 【新增】项目封面映射
    val customProjectOrder: List<String> = emptyList(), // 【新增】自定义排序
    val backupPath: String = "", // 【新增】备份路径
    val showTagFilterBar: Boolean = true, // 【新增】显示标签筛选栏
    val showRecentProjectsBar: Boolean = true, // 【新增】显示最近项目条
    val homeShowRecent: Boolean = true,               // 【新增】显示"继续上次项目"
    val lastOpenedProjectId: String = "",             // 【新增】上次打开的项目 ID
    val userTemplates: List<ProjectTemplate> = emptyList(), // 【新增】用户自定义模板列表
    val recentCardWidth: Int = 1,                     // 【新增】最近项目卡片宽度 0=紧凑120dp,1=标准150dp,2=宽180dp
    val recentCardWidthDp: Int = 150,                 // 【新增】最近项目卡片自定义宽度(dp)，范围80-240
    val trashRetentionDays: Int = 7,                  // 【新增】回收站保留天数，默认7天，范围3-30
)