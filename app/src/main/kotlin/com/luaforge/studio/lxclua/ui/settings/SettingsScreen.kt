package com.luaforge.studio.lxclua.ui.settings

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DataArray
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LabelImportant
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.LocaleListCompat
import com.luaforge.studio.lxclua.R
import com.luaforge.studio.lxclua.ui.components.AppIconGrid
import com.luaforge.studio.lxclua.ui.components.ColorPickerDialog
import com.luaforge.studio.lxclua.ui.components.FilePickerDialog
import com.luaforge.studio.lxclua.ui.components.HomeLayoutPreviewDialog
import com.luaforge.studio.lxclua.ui.components.SelectionMode
import com.luaforge.studio.lxclua.ui.theme.ThemeType
import com.luaforge.studio.lxclua.utils.IconManager
import com.luaforge.studio.lxclua.utils.NonBlockingToastState
import com.luaforge.studio.lxclua.plugin.state.PluginSettingsState
import com.luaforge.studio.lxclua.plugin.state.PluginSettingsItem
import com.luaforge.studio.lxclua.ai.AIConfigManager
import com.luaforge.studio.lxclua.ai.AIConfigData
import com.luaforge.studio.lxclua.ai.AIProvider
import com.luaforge.studio.lxclua.ai.AIProviderConfig
import com.luaforge.studio.lxclua.ai.AIManager
import com.luaforge.studio.lxclua.ai.ChatMessage
import com.luaforge.studio.lxclua.mcp.MCPManager
import com.luaforge.studio.lxclua.mcp.MCPServerEntry
import com.luaforge.studio.lxclua.mcp.MCPServerSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    currentSettings: SettingsData,
    onSettingsChanged: (SettingsData) -> Unit,
    toast: NonBlockingToastState,
    onContentTypeChange: ((com.luaforge.studio.lxclua.MainContentType) -> Unit)? = null
) {
    val settingsManager = SettingsManager

    var appIconExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentSettingsState = settingsManager.currentSettings

    var appearanceExpanded by remember { mutableStateOf(false) }
    var editorConfigExpanded by remember { mutableStateOf(false) }
    var syntaxHighlightExpanded by remember { mutableStateOf(false) }
    var toastSettingsExpanded by remember { mutableStateOf(false) }

    var fontMenuExpanded by remember { mutableStateOf(false) }
    var editorFontMenuExpanded by remember { mutableStateOf(false) }

    var customFontPath by remember { mutableStateOf(currentSettingsState.customFontPath) }

    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerTitle by remember { mutableStateOf("") }
    var colorToEdit by remember { mutableStateOf<Color?>(null) }
    var onColorSelected by remember { mutableStateOf<(Color) -> Unit>({}) }

    // 备份路径选择器状态
    var showBackupDirPicker by remember { mutableStateOf(false) }
    // 默认备份目录路径
    val defaultBackupPath = remember {
        File(Environment.getExternalStorageDirectory(), "LXC-LUA/backups").absolutePath
    }

    // AI 配置状态（从 AIConfigManager 的 StateFlow 收集，实时响应外部变更）
    val aiConfig by AIConfigManager.configFlow.collectAsState()
    var aiExpanded by remember { mutableStateOf(false) }
    var mcpExpanded by remember { mutableStateOf(false) }
    var aiTesting by remember { mutableStateOf(false) }

    // 回收站保留天数对话框状态
    var showTrashDaysDialog by remember { mutableStateOf(false) }

    fun updateAiConfig(newConfig: AIConfigData) {
        scope.launch {
            AIConfigManager.saveConfig(context, newConfig)
            AIManager.refresh()
            MCPManager.refresh()
        }
    }

    // 使用资源ID，避免在remember中直接调用stringResource
    val fontFamilyOptions = remember {
        listOf(
            R.string.settings_font_default to FontFamilyType.DEFAULT,
            R.string.settings_font_sans_serif to FontFamilyType.SANS_SERIF,
            R.string.settings_font_serif to FontFamilyType.SERIF,
            R.string.settings_font_monospace to FontFamilyType.MONOSPACE,
            R.string.settings_font_josefin_sans to FontFamilyType.JOSEFIN_SANS
        )
    }

    val editorFontOptions = remember {
        listOf(
            R.string.settings_font_georgiamono_italic to EditorFontType.GEORGIA_MONO_ITALIC,
            R.string.settings_font_fira_code to EditorFontType.FIRA_CODE,
            R.string.settings_font_custom to EditorFontType.CUSTOM
        )
    }

    fun updateSettingsWithSave(newSettings: SettingsData) {
        settingsManager.updateSettings(newSettings)
        settingsManager.saveSettings(context)
        onSettingsChanged(newSettings)
    }

    fun isValidFontFile(path: String): Boolean {
        if (path.isBlank()) return false
        val file = File(path)
        return file.exists() && file.isFile && file.canRead() &&
                (file.extension.equals("ttf", ignoreCase = true) ||
                        file.extension.equals("otf", ignoreCase = true))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 首页布局设置展开状态（提升到顶层，FAB可浮动在列表上）
        var homeSettingsExpanded by remember { mutableStateOf(false) }
        var showLayoutPreview by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {

            item {
                SettingsCardGroup(
                    title = stringResource(R.string.settings_theme_appearance),
                    icon = Icons.Filled.Palette,
                    initiallyExpanded = appearanceExpanded,
                    onExpandedChange = { appearanceExpanded = it }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_theme_color),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ThemeColorOption(
                                    color = Color(0xFF2E6A44),
                                    name = stringResource(R.string.settings_theme_green),
                                    isSelected = currentSettingsState.themeType == ThemeType.GREEN,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(themeType = ThemeType.GREEN))
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                ThemeColorOption(
                                    color = Color(0xFF36618E),
                                    name = stringResource(R.string.settings_theme_blue),
                                    isSelected = currentSettingsState.themeType == ThemeType.BLUE,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(themeType = ThemeType.BLUE))
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                ThemeColorOption(
                                    color = Color(0xFF8D4A5A),
                                    name = stringResource(R.string.settings_theme_pink),
                                    isSelected = currentSettingsState.themeType == ThemeType.PINK,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(themeType = ThemeType.PINK))
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_theme_mode),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                DarkModeOption(
                                    icon = Icons.Filled.Settings,
                                    name = stringResource(R.string.settings_theme_follow_system),
                                    isSelected = currentSettingsState.darkMode == DarkMode.FOLLOW_SYSTEM,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(darkMode = DarkMode.FOLLOW_SYSTEM))
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                DarkModeOption(
                                    icon = Icons.Filled.LightMode,
                                    name = stringResource(R.string.settings_theme_light),
                                    isSelected = currentSettingsState.darkMode == DarkMode.LIGHT,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(darkMode = DarkMode.LIGHT))
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                DarkModeOption(
                                    icon = Icons.Filled.DarkMode,
                                    name = stringResource(R.string.settings_theme_dark),
                                    isSelected = currentSettingsState.darkMode == DarkMode.DARK,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(darkMode = DarkMode.DARK))
                                    }
                                )
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )

                        SettingsListItem(
                            title = stringResource(R.string.settings_dynamic_color),
                            subtitle = stringResource(R.string.settings_dynamic_color_desc),
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = currentSettingsState.dynamicColor,
                                    onCheckedChange = {
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(dynamicColor = it)
                                        )
                                    }
                                )
                            },
                            onClick = {
                                updateSettingsWithSave(currentSettingsState.copy(dynamicColor = !currentSettingsState.dynamicColor))
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_shape_corner),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ShapeSizeSelector(
                            selectedIndex = currentSettingsState.shapeSizeIndex,
                            onIndexSelected = { newIndex ->
                                updateSettingsWithSave(currentSettingsState.copy(shapeSizeIndex = newIndex))
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_font_size),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FontSizeSelector(
                            currentScale = currentSettingsState.fontSizeScale,
                            onScaleSelected = { newScale ->
                                updateSettingsWithSave(currentSettingsState.copy(fontSizeScale = newScale))
                            }
                        )
                    }
                    
HorizontalDivider(
    modifier = Modifier.padding(vertical = 4.dp),
    thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
)

Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    Text(
        text = stringResource(R.string.settings_app_language),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
        selected = currentSettingsState.languageTag == "zh",
        onClick = {
            SettingsManager.setAppLanguage(context, "zh")
        },
        label = { Text(stringResource(R.string.language_chinese)) }
    )
    
    FilterChip(
        selected = currentSettingsState.languageTag == "en",
        onClick = {
            SettingsManager.setAppLanguage(context, "en")
        },
        label = { Text(stringResource(R.string.language_english)) }
    )
    }
}

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_font_family),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ExposedDropdownMenuBox(
                            expanded = fontMenuExpanded,
                            onExpandedChange = { fontMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when (currentSettingsState.fontFamilyType) {
                                    FontFamilyType.DEFAULT -> stringResource(R.string.settings_font_default)
                                    FontFamilyType.SANS_SERIF -> stringResource(R.string.settings_font_sans_serif)
                                    FontFamilyType.SERIF -> stringResource(R.string.settings_font_serif)
                                    FontFamilyType.MONOSPACE -> stringResource(R.string.settings_font_monospace)
                                    FontFamilyType.JOSEFIN_SANS -> stringResource(R.string.settings_font_josefin_sans)
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.settings_select_font_family)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontMenuExpanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = fontMenuExpanded,
                                onDismissRequest = { fontMenuExpanded = false }
                            ) {
                                fontFamilyOptions.forEach { (nameRes, type) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(nameRes),
                                                fontWeight = if (currentSettingsState.fontFamilyType == type)
                                                    FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            updateSettingsWithSave(
                                                currentSettingsState.copy(fontFamilyType = type)
                                            )
                                            fontMenuExpanded = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ========== 首页设置 ==========
            item {
                    SettingsCardGroup(
                        title = stringResource(R.string.settings_home_layout),
                        icon = Icons.Filled.Dashboard,
                        initiallyExpanded = homeSettingsExpanded,
                        onExpandedChange = { homeSettingsExpanded = it }
                    ) {
                    // 首页布局模式
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Dashboard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = stringResource(R.string.settings_home_layout),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = currentSettingsState.homeLayoutMode == HomeLayoutMode.CARD,
                                onClick = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(homeLayoutMode = HomeLayoutMode.CARD)
                                    )
                                },
                                label = { Text(stringResource(R.string.settings_home_layout_card)) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = currentSettingsState.homeLayoutMode == HomeLayoutMode.FLAT,
                                onClick = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(homeLayoutMode = HomeLayoutMode.FLAT)
                                    )
                                },
                                label = { Text(stringResource(R.string.settings_home_layout_flat)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 显示"继续上次项目"开关
                    SettingsListItem(
                        title = stringResource(R.string.settings_home_show_recent),
                        subtitle = stringResource(R.string.settings_home_show_recent_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.homeShowRecent,
                                onCheckedChange = {
                                    updateSettingsWithSave(currentSettingsState.copy(homeShowRecent = it))
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(
                                currentSettingsState.copy(homeShowRecent = !currentSettingsState.homeShowRecent)
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 分类功能开关
                    SettingsListItem(
                        title = "项目分类",
                        subtitle = "启用分类筛选栏，对项目进行分类管理",
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Label,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.homeCategoryEnabled,
                                onCheckedChange = {
                                    updateSettingsWithSave(currentSettingsState.copy(homeCategoryEnabled = it))
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(
                                currentSettingsState.copy(homeCategoryEnabled = !currentSettingsState.homeCategoryEnabled)
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 标签筛选栏开关
                    SettingsListItem(
                        title = "标签筛选栏",
                        subtitle = "显示标签筛选栏，支持多标签组合筛选项目",
                        leadingIcon = {
                            Icon(
                                Icons.Filled.LabelImportant,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.showTagFilterBar,
                                onCheckedChange = {
                                    updateSettingsWithSave(currentSettingsState.copy(showTagFilterBar = it))
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(
                                currentSettingsState.copy(showTagFilterBar = !currentSettingsState.showTagFilterBar)
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 最近项目条开关
                    SettingsListItem(
                        title = "最近项目条",
                        subtitle = "首页顶部显示最近打开项目的快速访问条",
                        leadingIcon = {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.showRecentProjectsBar,
                                onCheckedChange = {
                                    updateSettingsWithSave(currentSettingsState.copy(showRecentProjectsBar = it))
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(
                                currentSettingsState.copy(showRecentProjectsBar = !currentSettingsState.showRecentProjectsBar)
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 自动打开上次项目
                    SettingsListItem(
                        title = "启动时打开上次项目",
                        subtitle = "应用启动后自动进入上次打开的项目",
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Launch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.autoOpenLastProject,
                                onCheckedChange = {
                                    updateSettingsWithSave(currentSettingsState.copy(autoOpenLastProject = it))
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(
                                currentSettingsState.copy(autoOpenLastProject = !currentSettingsState.autoOpenLastProject)
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 显示修改时间
                    SettingsListItem(
                        title = "显示修改时间",
                        subtitle = "项目卡片上显示最后修改时间",
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.showProjectModifiedTime,
                                onCheckedChange = {
                                    updateSettingsWithSave(currentSettingsState.copy(showProjectModifiedTime = it))
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(
                                currentSettingsState.copy(showProjectModifiedTime = !currentSettingsState.showProjectModifiedTime)
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 显示项目路径
                    SettingsListItem(
                        title = "显示项目路径",
                        subtitle = "项目卡片上显示项目存储路径",
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.showProjectPath,
                                onCheckedChange = {
                                    updateSettingsWithSave(currentSettingsState.copy(showProjectPath = it))
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(
                                currentSettingsState.copy(showProjectPath = !currentSettingsState.showProjectPath)
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 卡片密度
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "卡片密度",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val densities = listOf(
                                com.luaforge.studio.lxclua.ui.settings.HomeDensity.COMPACT to "紧凑",
                                com.luaforge.studio.lxclua.ui.settings.HomeDensity.COMFORTABLE to "舒适",
                                com.luaforge.studio.lxclua.ui.settings.HomeDensity.LARGE to "大"
                            )
                            densities.forEach { (d, label) ->
                                FilterChip(
                                    selected = currentSettingsState.homeDensity == d,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(homeDensity = d))
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 卡片圆角
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "卡片圆角",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0 to "小", 1 to "中", 2 to "大").forEach { (r, label) ->
                                FilterChip(
                                    selected = currentSettingsState.cardCornerRadius == r,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(cardCornerRadius = r))
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 分类栏位置
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "分类栏位置",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val positions = listOf(
                                com.luaforge.studio.lxclua.ui.settings.CategoryBarPosition.TOP to "顶部",
                                com.luaforge.studio.lxclua.ui.settings.CategoryBarPosition.BOTTOM to "底部"
                            )
                            positions.forEach { (p, label) ->
                                FilterChip(
                                    selected = currentSettingsState.categoryBarPosition == p,
                                    onClick = {
                                        updateSettingsWithSave(currentSettingsState.copy(categoryBarPosition = p))
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 最近项目卡片大小
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "最近项目卡片大小",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${currentSettingsState.recentCardWidthDp}dp",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Slider滑块自由调节宽度，80-240dp，步长10dp
                        Slider(
                            value = currentSettingsState.recentCardWidthDp.toFloat(),
                            onValueChange = { newValue ->
                                val dpValue = newValue.toInt().coerceIn(80, 240)
                                // 同步更新旧枚举值
                                val enumValue = when {
                                    dpValue <= 130 -> 0
                                    dpValue >= 170 -> 2
                                    else -> 1
                                }
                                updateSettingsWithSave(
                                    currentSettingsState.copy(
                                        recentCardWidthDp = dpValue,
                                        recentCardWidth = enumValue
                                    )
                                )
                            },
                            valueRange = 80f..240f,
                            steps = 15, // (240-80)/10 - 1 = 15 steps
                            modifier = Modifier.fillMaxWidth()
                        )
                        // 预设按钮快速选择
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val presets = listOf(120 to "紧凑", 150 to "标准", 180 to "宽")
                            presets.forEach { (dp, label) ->
                                FilterChip(
                                    selected = currentSettingsState.recentCardWidthDp == dp,
                                    onClick = {
                                        val enumValue = when(dp) {
                                            120 -> 0; 180 -> 2; else -> 1
                                        }
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(
                                                recentCardWidthDp = dp,
                                                recentCardWidth = enumValue
                                            )
                                        )
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 备份路径设置
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "备份路径",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "项目备份文件的保存目录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 显示当前备份路径
                            Text(
                                text = if (currentSettingsState.backupPath.isBlank())
                                    "默认: $defaultBackupPath"
                                else
                                    currentSettingsState.backupPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            // 选择目录按钮
                            TextButton(onClick = { showBackupDirPicker = true }) {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("选择")
                            }
                            // 恢复默认按钮
                            if (currentSettingsState.backupPath.isNotBlank()) {
                                TextButton(onClick = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(backupPath = "")
                                    )
                                }) {
                                    Text("默认", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    }
            }

            item {
                SettingsCardGroup(
                    title = stringResource(R.string.settings_app_icon),
                    icon = Icons.Filled.Apps,
                    initiallyExpanded = appIconExpanded,
                    onExpandedChange = { appIconExpanded = it }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_select_app_icon),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val currentIcon = remember {
                            IconManager.getCurrentIcon(context)
                        }

                        AppIconGrid(
                            selectedIcon = currentIcon,
                            onIconSelected = { newIcon: IconManager.AppIcon ->
                                scope.launch {
                                    try {
                                        IconManager.switchAppIcon(context, newIcon)

                                        val newSettings = currentSettings.copy(
                                            selectedAppIcon = newIcon
                                        )
                                        SettingsManager.updateSettings(newSettings)
                                        SettingsManager.saveSettings(context)
                                        onSettingsChanged(newSettings)

                                        toast.showToast(context.getString(R.string.settings_icon_restart_to_effect))
                                    } catch (e: Exception) {
                                        toast.showToast(context.getString(R.string.settings_icon_switch_failed, e.message ?: ""))
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                SettingsCardGroup(
                    title = stringResource(R.string.settings_editor_config),
                    icon = Icons.Filled.Edit,
                    initiallyExpanded = editorConfigExpanded,
                    onExpandedChange = { editorConfigExpanded = it }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_editor_font),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ExposedDropdownMenuBox(
                            expanded = editorFontMenuExpanded,
                            onExpandedChange = { editorFontMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when (currentSettingsState.editorFontType) {
                                    EditorFontType.GEORGIA_MONO_ITALIC -> stringResource(R.string.settings_font_georgiamono_italic)
                                    EditorFontType.FIRA_CODE -> stringResource(R.string.settings_font_fira_code)
                                    EditorFontType.CUSTOM -> stringResource(R.string.settings_font_custom)
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.settings_select_editor_font)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = editorFontMenuExpanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = editorFontMenuExpanded,
                                onDismissRequest = { editorFontMenuExpanded = false }
                            ) {
                                editorFontOptions.forEach { (nameRes, type) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(nameRes),
                                                fontWeight = if (currentSettingsState.editorFontType == type)
                                                    FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            val newSettings =
                                                currentSettingsState.copy(editorFontType = type)
                                            updateSettingsWithSave(newSettings)
                                            customFontPath = newSettings.customFontPath
                                            editorFontMenuExpanded = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = currentSettingsState.editorFontType == EditorFontType.CUSTOM,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Spacer(modifier = Modifier.height(8.dp))

                                var fontPathText by remember { mutableStateOf(currentSettingsState.customFontPath) }
                                var isFontPathValid by remember {
                                    mutableStateOf(
                                        isValidFontFile(fontPathText)
                                    )
                                }

                                OutlinedTextField(
                                    value = fontPathText,
                                    onValueChange = { newPath ->
                                        fontPathText = newPath
                                        isFontPathValid = isValidFontFile(newPath)

                                        if (isFontPathValid) {
                                            val newSettings =
                                                currentSettingsState.copy(customFontPath = newPath)
                                            updateSettingsWithSave(newSettings)
                                        }
                                    },
                                    label = { Text(stringResource(R.string.settings_font_file_path)) },
                                    placeholder = { Text(stringResource(R.string.settings_font_file_placeholder)) },
                                    trailingIcon = {
                                        if (fontPathText.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    fontPathText = ""
                                                    isFontPathValid = false
                                                    val newSettings =
                                                        currentSettingsState.copy(customFontPath = "")
                                                    updateSettingsWithSave(newSettings)
                                                }
                                            ) {
                                                Icon(Icons.Filled.Clear, stringResource(R.string.clear))
                                            }
                                        }
                                    },
                                    isError = fontPathText.isNotBlank() && !isFontPathValid,
                                    supportingText = {
                                        if (fontPathText.isNotBlank() && !isFontPathValid) {
                                            Text(stringResource(R.string.settings_font_file_invalid))
                                        } else if (isFontPathValid) {
                                            Text(stringResource(R.string.settings_font_file_valid))
                                        } else {
                                            Text(stringResource(R.string.settings_font_file_hint))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    SettingsListItem(
                        title = stringResource(R.string.settings_tab_history),
                        subtitle = stringResource(R.string.settings_tab_history_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.enableTabHistory,
                                onCheckedChange = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(enableTabHistory = it)
                                    )
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(currentSettingsState.copy(enableTabHistory = !currentSettingsState.enableTabHistory))
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    SettingsListItem(
                        title = stringResource(R.string.settings_indent_guide),
                        subtitle = stringResource(R.string.settings_indent_guide_desc),
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.FormatIndentIncrease,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.indentGuideEnabled,
                                onCheckedChange = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(indentGuideEnabled = it)
                                    )
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(currentSettingsState.copy(indentGuideEnabled = !currentSettingsState.indentGuideEnabled))
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    SettingsListItem(
                        title = stringResource(R.string.settings_completion_case_sensitive),
                        subtitle = stringResource(R.string.settings_completion_case_sensitive_desc),
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.MergeType,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.completionCaseSensitive,
                                onCheckedChange = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(completionCaseSensitive = it)
                                    )
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(currentSettingsState.copy(completionCaseSensitive = !currentSettingsState.completionCaseSensitive))
                        }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    SettingsListItem(
                        title = stringResource(R.string.settings_hex_color_highlight),
                        subtitle = stringResource(R.string.settings_hex_color_highlight_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.ColorLens,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.hexColorHighlightEnabled,
                                onCheckedChange = {
                                    updateSettingsWithSave(currentSettingsState.copy(hexColorHighlightEnabled = it))
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(currentSettingsState.copy(hexColorHighlightEnabled = !currentSettingsState.hexColorHighlightEnabled))
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    SettingsListItem(
                        title = stringResource(R.string.settings_smart_sorting),
                        subtitle = stringResource(R.string.settings_smart_sorting_desc),
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = currentSettingsState.smartSortingEnabled,
                                onCheckedChange = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(smartSortingEnabled = it)
                                    )
                                }
                            )
                        },
                        onClick = {
                            updateSettingsWithSave(currentSettingsState.copy(smartSortingEnabled = !currentSettingsState.smartSortingEnabled))
                        }
                    )
                    
                    HorizontalDivider(
    modifier = Modifier.padding(vertical = 4.dp),
    thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
)

SettingsListItem(
    title = stringResource(R.string.settings_swipe_gesture),
    subtitle = stringResource(R.string.settings_swipe_gesture_desc),
    leadingIcon = {
        Icon(
            Icons.Filled.Swipe,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    },
    trailingContent = {
        Switch(
            checked = currentSettingsState.enableSwipeGesture,
            onCheckedChange = {
                updateSettingsWithSave(
                    currentSettingsState.copy(enableSwipeGesture = it)
                )
            }
        )
    },
    onClick = {
        updateSettingsWithSave(currentSettingsState.copy(enableSwipeGesture = !currentSettingsState.enableSwipeGesture))
    }
)

// 诊断提示框开关
SettingsListItem(
    title = stringResource(R.string.settings_diagnostic_tooltip),
    subtitle = stringResource(R.string.settings_diagnostic_tooltip_desc),
    leadingIcon = {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    },
    trailingContent = {
        Switch(
            checked = currentSettingsState.diagnosticTooltipEnabled,
            onCheckedChange = {
                updateSettingsWithSave(
                    currentSettingsState.copy(diagnosticTooltipEnabled = it)
                )
            }
        )
    },
    onClick = {
        updateSettingsWithSave(currentSettingsState.copy(diagnosticTooltipEnabled = !currentSettingsState.diagnosticTooltipEnabled))
    }
)

                }
            }

            item {
                SettingsCardGroup(
                    title = stringResource(R.string.settings_notification_toast),
                    icon = Icons.Filled.Info,
                    initiallyExpanded = toastSettingsExpanded,
                    onExpandedChange = { toastSettingsExpanded = it }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_toast_position),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = currentSettingsState.toastPosition == ToastPosition.TOP,
                                onClick = {
                                    updateSettingsWithSave(currentSettingsState.copy(toastPosition = ToastPosition.TOP))
                                },
                                label = { Text(stringResource(R.string.settings_toast_top)) }
                            )

                            FilterChip(
                                selected = currentSettingsState.toastPosition == ToastPosition.BOTTOM,
                                onClick = {
                                    updateSettingsWithSave(currentSettingsState.copy(toastPosition = ToastPosition.BOTTOM))
                                },
                                label = { Text(stringResource(R.string.settings_toast_bottom)) }
                            )
                        }

                        SettingsListItem(
                            title = stringResource(R.string.settings_toast_border),
                            subtitle = stringResource(R.string.settings_toast_border_desc),
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = currentSettingsState.toastBorderEnabled,
                                    onCheckedChange = {
                                        updateSettingsWithSave(currentSettingsState.copy(toastBorderEnabled = it))
                                    }
                                )
                            },
                            onClick = {
                                updateSettingsWithSave(currentSettingsState.copy(toastBorderEnabled = !currentSettingsState.toastBorderEnabled))
                            }
                        )
                    }
                }
            }

            item {
                SettingsCardGroup(
                    title = stringResource(R.string.settings_syntax_highlight),
                    icon = Icons.Filled.ColorLens,
                    initiallyExpanded = syntaxHighlightExpanded,
                    onExpandedChange = { syntaxHighlightExpanded = it }
                ) {
                    // 类名高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_class_name_color),
                        subtitle = stringResource(R.string.settings_class_name_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.classNameColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_class_name_color)
                                    colorToEdit = currentSettingsState.classNameColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(classNameColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_class_name_color)
                            colorToEdit = currentSettingsState.classNameColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(classNameColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 局部变量高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_local_variable_color),
                        subtitle = stringResource(R.string.settings_local_variable_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DataArray,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.localVariableColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_local_variable_color)
                                    colorToEdit = currentSettingsState.localVariableColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(localVariableColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_local_variable_color)
                            colorToEdit = currentSettingsState.localVariableColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(localVariableColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 关键词高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_keyword_color),
                        subtitle = stringResource(R.string.settings_keyword_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.keywordColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_keyword_color)
                                    colorToEdit = currentSettingsState.keywordColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(keywordColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_keyword_color)
                            colorToEdit = currentSettingsState.keywordColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(keywordColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 函数名高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_function_name_color),
                        subtitle = stringResource(R.string.settings_function_name_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Functions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.functionNameColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_function_name_color)
                                    colorToEdit = currentSettingsState.functionNameColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(functionNameColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_function_name_color)
                            colorToEdit = currentSettingsState.functionNameColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(functionNameColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 字符串文字高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_literal_color),
                        subtitle = stringResource(R.string.settings_literal_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.FormatQuote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.literalColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_literal_color)
                                    colorToEdit = currentSettingsState.literalColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(literalColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_literal_color)
                            colorToEdit = currentSettingsState.literalColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(literalColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 注释高亮
                    SettingsListItem(
                        title = stringResource(R.string.settings_comment_color),
                        subtitle = stringResource(R.string.settings_comment_color_desc),
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Comment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.commentColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_comment_color)
                                    colorToEdit = currentSettingsState.commentColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(commentColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_comment_color)
                            colorToEdit = currentSettingsState.commentColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(commentColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )

                    // 选中行背景
                    SettingsListItem(
                        title = stringResource(R.string.settings_selected_line_background),
                        subtitle = stringResource(R.string.settings_selected_line_background_desc),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Highlight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            ColorPreviewChip(
                                color = currentSettingsState.selectedLineColor,
                                onClick = {
                                    colorPickerTitle = context.getString(R.string.settings_selected_line_background)
                                    colorToEdit = currentSettingsState.selectedLineColor
                                    onColorSelected = { newColor ->
                                        updateSettingsWithSave(
                                            currentSettingsState.copy(selectedLineColor = newColor)
                                        )
                                    }
                                    showColorPicker = true
                                },
                                size = 28
                            )
                        },
                        onClick = {
                            colorPickerTitle = context.getString(R.string.settings_selected_line_background)
                            colorToEdit = currentSettingsState.selectedLineColor
                            onColorSelected = { newColor ->
                                updateSettingsWithSave(
                                    currentSettingsState.copy(selectedLineColor = newColor)
                                )
                            }
                            showColorPicker = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                updateSettingsWithSave(
                                    currentSettingsState.copy(
                                        classNameColor = Color(0xFF6E81D9),
                                        localVariableColor = Color(0xFFAAAA88),
                                        keywordColor = Color(0xFFFF565E),
                                        functionNameColor = Color(0xFF2196F3),
                                        literalColor = Color(0xFF008080),
                                        commentColor = Color(0xFFA7A8A8),
                                        selectedLineColor = Color(0x1A000000)
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                Icons.Filled.RestartAlt,
                                contentDescription = stringResource(R.string.settings_reset),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_reset_default_colors))
                        }
                    }
                }
            }

            // 插件扩展设置
            val pluginSections = PluginSettingsState.getSortedSections()
                .filter { section -> PluginSettingsState.getItemsBySection(section.key).isNotEmpty() }

            if (pluginSections.isNotEmpty()) {
                items(pluginSections, key = { section -> section.key }) { section ->
                    val sectionItems = PluginSettingsState.getItemsBySection(section.key)
                    var pluginExpanded by remember { mutableStateOf(false) }

                    SettingsCardGroup(
                        title = section.title,
                        icon = Icons.Filled.Settings,
                        initiallyExpanded = pluginExpanded,
                        onExpandedChange = { expanded -> pluginExpanded = expanded }
                    ) {
                        sectionItems.forEach { item ->
                                PluginSettingsItemRow(item = item)
                            }
                    }
                }
            }

            // AI 配置
            item {
                AISettingsSection(
                    config = aiConfig,
                    expanded = aiExpanded,
                    onExpandedChange = { aiExpanded = it },
                    testing = aiTesting,
                    onConfigChange = { updateAiConfig(it) },
                    onTestConnection = {
                        aiTesting = true
                        scope.launch {
                            try {
                                val result = withTimeout(15_000L) {
                                    AIManager.chat(
                                        com.luaforge.studio.lxclua.ai.ChatRequest(
                                            messages = listOf(ChatMessage("user", "Hello, reply with just 'OK'.")),
                                            maxTokens = 10
                                        )
                                    )
                                }
                                aiTesting = false
                                if (result.success) {
                                    val model = result.model ?: "?"
                                    val content = result.content?.take(50)?.replace("\n", " ") ?: ""
                                    toast.showToast(context.getString(R.string.settings_ai_connection_success_toast, model, content))
                                } else {
                                    toast.showToast(context.getString(R.string.settings_ai_connection_failed, result.error ?: ""))
                                }
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                aiTesting = false
                                toast.showToast(context.getString(R.string.settings_ai_connection_timeout))
                            } catch (e: Exception) {
                                aiTesting = false
                                toast.showToast(context.getString(R.string.settings_ai_connection_failed, e.message ?: ""))
                            }
                        }
                    },
                    aiStreamEnabled = currentSettingsState.aiStreamEnabled,
                    onStreamEnabledChange = {
                        updateSettingsWithSave(currentSettingsState.copy(aiStreamEnabled = it))
                    }
                )
            }

            // MCP 配置
            item {
                MCPSettingsSection(
                    config = aiConfig,
                    expanded = mcpExpanded,
                    onExpandedChange = { mcpExpanded = it },
                    onConfigChange = { updateAiConfig(it) },
                    onTestConnection = { server ->
                        try {
                            val connected = MCPManager.connectServer(server)
                            if (connected) {
                                toast.showToast(context.getString(R.string.settings_mcp_connection_success_toast, server.name))
                            } else {
                                toast.showToast(context.getString(R.string.settings_mcp_connection_failed_toast, server.name))
                            }
                            connected
                        } catch (e: Exception) {
                            toast.showToast(context.getString(R.string.settings_mcp_connection_error_toast, e.message ?: ""))
                            false
                        }
                    }
                )
            }

            // 回收站设置
            item {
                SettingsCardGroup(
                    title = "回收站",
                    icon = Icons.Filled.Delete,
                    initiallyExpanded = false,
                    onExpandedChange = {}
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 打开回收站入口
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = onContentTypeChange != null) {
                                    onContentTypeChange?.invoke(
                                        com.luaforge.studio.lxclua.MainContentType.TRASH
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "打开回收站",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "查看和恢复已删除的项目",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider()

                        // 保留天数
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTrashDaysDialog = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "保留天数",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "删除的项目将保留${currentSettingsState.trashRetentionDays}天后自动清理",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${currentSettingsState.trashRetentionDays}天",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 首页布局设置预览FAB（浮动在LazyColumn之上，始终在右下角可见，首页布局卡片展开时显示）
        if (homeSettingsExpanded) {
            SmallFloatingActionButton(
                onClick = {
                    showLayoutPreview = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.Visibility, contentDescription = "预览效果")
            }
        }

        // 首页布局预览对话框（实时读取currentSettingsState，响应设置变化）
        if (showLayoutPreview) {
            HomeLayoutPreviewDialog(
                settings = currentSettingsState,
                onDismiss = { showLayoutPreview = false },
                onGoToHome = {
                    showLayoutPreview = false
                    onBack()
                }
            )
        }
    }

    // 颜色选择器对话框
    if (showColorPicker && colorToEdit != null) {
        ColorPickerDialog(
            title = colorPickerTitle,
            initialColor = colorToEdit!!,
            onColorSelected = { newColor ->
                onColorSelected(newColor)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    // 备份路径目录选择器
    if (showBackupDirPicker) {
        FilePickerDialog(
            initialPath = if (currentSettingsState.backupPath.isNotBlank())
                currentSettingsState.backupPath else defaultBackupPath,
            selectionMode = SelectionMode.DIRECTORY,
            title = "选择备份目录",
            onDismiss = { showBackupDirPicker = false },
            onDirectorySelected = { dirPath ->
                showBackupDirPicker = false
                updateSettingsWithSave(
                    currentSettingsState.copy(backupPath = dirPath)
                )
            }
        )
    }

    // 回收站保留天数选择对话框
    if (showTrashDaysDialog) {
        val daysOptions = listOf(3, 7, 14, 30)
        AlertDialog(
            onDismissRequest = { showTrashDaysDialog = false },
            title = { Text("回收站保留天数") },
            text = {
                Column {
                    daysOptions.forEach { days ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(trashRetentionDays = days)
                                    )
                                    showTrashDaysDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentSettingsState.trashRetentionDays == days,
                                onClick = {
                                    updateSettingsWithSave(
                                        currentSettingsState.copy(trashRetentionDays = days)
                                    )
                                    showTrashDaysDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${days}天")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrashDaysDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

