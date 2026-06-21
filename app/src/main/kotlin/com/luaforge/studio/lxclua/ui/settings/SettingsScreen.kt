package com.luaforge.studio.lxclua.ui.settings

import android.content.Context
import android.graphics.Typeface
import android.os.Build
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
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
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
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Swipe
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
import androidx.compose.material3.Slider
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

@Composable
fun ColorPreviewChip(
    color: Color,
    onClick: () -> Unit,
    size: Int = 28
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val checkerSize = 6f
            val rows = (this.size.height / checkerSize).toInt() + 1
            val cols = (this.size.width / checkerSize).toInt() + 1

            for (i in 0 until rows) {
                for (j in 0 until cols) {
                    val checkerColor = if ((i + j) % 2 == 0) Color.LightGray else Color.White
                    drawRect(
                        color = checkerColor,
                        topLeft = Offset(j * checkerSize, i * checkerSize),
                        size = Size(checkerSize, checkerSize)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color)
        )
    }
}

enum class EditorFontType {
    GEORGIA_MONO_ITALIC,
    FIRA_CODE,
    CUSTOM
}

enum class DarkMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK
}

enum class FontFamilyType {
    DEFAULT,
    SANS_SERIF,
    SERIF,
    MONOSPACE,
    JOSEFIN_SANS
}

object FontManager {
    fun getEditorTypeface(context: Context, settings: SettingsData): Typeface? {
        return try {
            when (settings.editorFontType) {
                EditorFontType.GEORGIA_MONO_ITALIC -> {
                    ResourcesCompat.getFont(context, R.font.georgia_mono_italic)
                }

                EditorFontType.FIRA_CODE -> {
                    ResourcesCompat.getFont(context, R.font.fira_code)
                }

                EditorFontType.CUSTOM -> {
                    if (settings.customFontPath.isNotBlank()) {
                        try {
                            Typeface.createFromFile(settings.customFontPath)
                        } catch (_: Exception) {
                            ResourcesCompat.getFont(context, R.font.georgia_mono_italic)
                        }
                    } else {
                        ResourcesCompat.getFont(context, R.font.georgia_mono_italic)
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    currentSettings: SettingsData,
    onSettingsChanged: (SettingsData) -> Unit,
    toast: NonBlockingToastState
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

    // AI 配置状态（从 AIConfigManager 的 StateFlow 收集，实时响应外部变更）
    val aiConfig by AIConfigManager.configFlow.collectAsState()
    var aiExpanded by remember { mutableStateOf(false) }
    var mcpExpanded by remember { mutableStateOf(false) }
    var aiTesting by remember { mutableStateOf(false) }

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
                        scope.launch {
                            try {
                                val connected = MCPManager.connectServer(server)
                                if (connected) {
                                    toast.showToast(context.getString(R.string.settings_mcp_connection_success_toast, server.name))
                                    MCPManager.disconnectServer(server)
                                } else {
                                    toast.showToast(context.getString(R.string.settings_mcp_connection_failed_toast, server.name))
                                }
                            } catch (e: Exception) {
                                toast.showToast(context.getString(R.string.settings_mcp_connection_error_toast, e.message ?: ""))
                            }
                        }
                    }
                )
            }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsListItem(
    title: String,
    subtitle: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        enabled = enabled,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 1.dp,
            focusedElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (leadingIcon != null) {
                    Box(modifier = Modifier.size(24.dp)) {
                        leadingIcon()
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )

                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            maxLines = 2
                        )
                    }
                }
            }

            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

@Composable
fun ShapeSizeSelector(
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit
) {
    val shapeOptions = listOf(
        stringResource(R.string.settings_shape_small) to 4.dp,
        stringResource(R.string.settings_shape_medium_small) to 8.dp,
        stringResource(R.string.settings_shape_medium) to 12.dp,
        stringResource(R.string.settings_shape_large) to 16.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        shapeOptions.forEachIndexed { index, (label, size) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .wrapContentHeight()
                    .padding(horizontal = 4.dp)
                    .clip(MaterialTheme.shapes.large)
                    .border(
                        width = if (selectedIndex == index) 2.dp else 0.dp,
                        color = if (selectedIndex == index)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.large
                    )
                    .background(
                        if (selectedIndex == index)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )
                    .clickable(
                        onClick = { onIndexSelected(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(size))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (selectedIndex == index) 0.5f else 0.2f
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedIndex == index)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "${size.value.toInt()}dp",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedIndex == index)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun FontSizeSelector(
    currentScale: Float,
    onScaleSelected: (Float) -> Unit
) {
    val fontSizeOptions = listOf(
        stringResource(R.string.settings_font_small) to 0.8f,
        stringResource(R.string.settings_font_medium_small) to 0.9f,
        stringResource(R.string.settings_font_standard) to 1.0f,
        stringResource(R.string.settings_font_large) to 1.1f,
        stringResource(R.string.settings_font_extra_large) to 1.2f
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        fontSizeOptions.forEach { (label, scale) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .wrapContentHeight()
                    .padding(horizontal = 2.dp)
                    .clip(MaterialTheme.shapes.large)
                    .border(
                        width = if (currentScale == scale) 2.dp else 0.dp,
                        color = if (currentScale == scale)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.large
                    )
                    .background(
                        if (currentScale == scale)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )
                    .clickable(
                        onClick = { onScaleSelected(scale) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = when (scale) {
                                    0.8f -> 14.sp
                                    0.9f -> 16.sp
                                    1.0f -> 18.sp
                                    1.1f -> 20.sp
                                    1.2f -> 22.sp
                                    else -> 18.sp
                                }
                            ),
                            fontWeight = FontWeight.Bold,
                            color = if (currentScale == scale)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (currentScale == scale) FontWeight.Bold else FontWeight.Medium,
                        color = if (currentScale == scale)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCardGroup(
    title: String,
    icon: ImageVector,
    initiallyExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 0.dp,
                shape = MaterialTheme.shapes.large,
                clip = true
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = {
                            expanded = !expanded
                            onExpandedChange(expanded)
                        }
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.close) else stringResource(R.string.open_drawer),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ThemeColorOption(
    color: Color,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.large
            )
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(color)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PluginSettingsItemRow(item: PluginSettingsItem) {
    var switchState by remember { mutableStateOf(item.initialValue) }

    when (item.type) {
        PluginSettingsState.TYPE_SWITCH -> {
            SettingsListItem(
                title = item.title,
                subtitle = item.subtitle,
                trailingContent = {
                    Switch(
                        checked = switchState,
                        onCheckedChange = { newValue ->
                            switchState = newValue
                            item.onChange?.invoke(newValue)
                        }
                    )
                },
                onClick = {
                    switchState = !switchState
                    item.onChange?.invoke(switchState)
                }
            )
        }
        PluginSettingsState.TYPE_BUTTON -> {
            SettingsListItem(
                title = item.title,
                subtitle = item.subtitle,
                onClick = {
                    item.onClick?.invoke()
                }
            )
        }
    }
}

@Composable
fun DarkModeOption(
    icon: ImageVector,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.large
            )
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== AI 设置 UI（多提供商版本） ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsSection(
    config: AIConfigData,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    testing: Boolean,
    onConfigChange: (AIConfigData) -> Unit,
    onTestConnection: () -> Unit,
    aiStreamEnabled: Boolean,
    onStreamEnabledChange: (Boolean) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AIProviderConfig?>(null) }
    var providerMenuExpanded by remember { mutableStateOf(false) }

    // 新增对话框状态
    var addName by remember { mutableStateOf("") }
    var addProvider by remember { mutableStateOf(AIProvider.OPENAI) }
    var addApiKey by remember { mutableStateOf("") }
    var addModel by remember { mutableStateOf("") }
    var addProviderMenuExpanded by remember { mutableStateOf(false) }

    SettingsCardGroup(
        title = stringResource(R.string.settings_ai_config),
        icon = Icons.AutoMirrored.Filled.Comment,
        initiallyExpanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 全局启用开关
            SettingsListItem(
                title = stringResource(R.string.settings_ai_enable),
                subtitle = stringResource(R.string.settings_ai_enable_desc),
                trailingContent = {
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { onConfigChange(config.copy(enabled = it)) }
                    )
                },
                onClick = { onConfigChange(config.copy(enabled = !config.enabled)) }
            )

            if (config.enabled) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )

                // AI 流式输出开关
                SettingsListItem(
                    title = stringResource(R.string.settings_ai_stream),
                    subtitle = stringResource(R.string.settings_ai_stream_desc),
                    trailingContent = {
                        Switch(
                            checked = aiStreamEnabled,
                            onCheckedChange = onStreamEnabledChange
                        )
                    },
                    onClick = { onStreamEnabledChange(!aiStreamEnabled) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )

                // 提供商列表标题
                Text(
                    text = stringResource(R.string.settings_ai_provider_list),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (config.providers.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_ai_no_providers),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    config.providers.forEach { providerConfig ->
                        val isActive = providerConfig.id == config.activeProviderId
                        val isPlugin = providerConfig.isPluginRegistered

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingProvider = providerConfig }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 活跃指示器
                                if (isActive) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = stringResource(R.string.settings_ai_active_provider),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = providerConfig.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isPlugin) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.settings_ai_plugin_registered),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .background(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${providerConfig.provider.displayName} | ${providerConfig.effectiveModel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 启用开关
                                Switch(
                                    checked = providerConfig.enabled,
                                    onCheckedChange = { enabled ->
                                        val updated = config.providers.map {
                                            if (it.id == providerConfig.id) it.copy(enabled = enabled) else it
                                        }
                                        onConfigChange(config.copy(providers = updated))
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }

                // 添加提供商按钮
                SettingsListItem(
                    title = stringResource(R.string.settings_ai_add_provider),
                    subtitle = stringResource(R.string.settings_ai_add_provider_desc),
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        addName = ""
                        addProvider = AIProvider.OPENAI
                        addApiKey = ""
                        addModel = ""
                        showAddDialog = true
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )

                // 连接测试
                SettingsListItem(
                    title = stringResource(R.string.settings_ai_connection_test),
                    subtitle = if (testing) stringResource(R.string.settings_ai_connection_testing)
                    else stringResource(R.string.settings_ai_connection_test_desc),
                    onClick = {
                        if (!testing) onTestConnection()
                    },
                    enabled = config.enabled && config.activeProvider?.isConfigured == true && !testing
                )
            }
        }
    }

    // ========== 添加提供商对话框 ==========
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
            },
            title = { Text(stringResource(R.string.settings_ai_add_provider)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it },
                        label = { Text(stringResource(R.string.settings_ai_provider_name)) },
                        placeholder = { Text(stringResource(R.string.settings_ai_provider_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 提供商类型
                    ExposedDropdownMenuBox(
                        expanded = addProviderMenuExpanded,
                        onExpandedChange = { addProviderMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = addProvider.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.settings_ai_provider)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = addProviderMenuExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = addProviderMenuExpanded,
                            onDismissRequest = { addProviderMenuExpanded = false }
                        ) {
                            AIProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            provider.displayName,
                                            fontWeight = if (addProvider == provider) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        addProvider = provider
                                        addProviderMenuExpanded = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = addApiKey,
                        onValueChange = { addApiKey = it },
                        label = { Text(stringResource(R.string.settings_ai_api_key)) },
                        placeholder = { Text(stringResource(R.string.settings_ai_api_key_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = addModel,
                        onValueChange = { addModel = it },
                        label = { Text(stringResource(R.string.settings_ai_model)) },
                        placeholder = { Text(stringResource(R.string.settings_ai_default_model, addProvider.defaultModel)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (addName.isNotBlank()) {
                            val newProvider = AIProviderConfig(
                                name = addName,
                                provider = addProvider,
                                apiKey = addApiKey,
                                model = addModel,
                                enabled = true
                            )
                            onConfigChange(
                                config.copy(
                                    providers = config.providers + newProvider,
                                    activeProviderId = config.activeProviderId ?: newProvider.id
                                )
                            )
                            showAddDialog = false
                        }
                    },
                    enabled = addName.isNotBlank()
                ) {
                    Text(stringResource(R.string.settings_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    // ========== 编辑提供商对话框 ==========
    editingProvider?.let { provider ->
        var editName by remember { mutableStateOf(provider.name) }
        var editProviderType by remember { mutableStateOf(provider.provider) }
        var editApiKey by remember { mutableStateOf(provider.apiKey) }
        var editEndpoint by remember { mutableStateOf(provider.customEndpoint) }
        var editModel by remember { mutableStateOf(provider.model) }
        var editTemperature by remember { mutableStateOf(provider.temperature) }
        var editMaxTokens by remember { mutableStateOf(provider.maxTokens) }
        var editSystemPrompt by remember { mutableStateOf(provider.systemPrompt) }
        var editSupportsTools by remember { mutableStateOf(provider.supportsTools) }
        var editMaxToolRounds by remember { mutableStateOf(provider.maxToolRounds) }
        var editModelMenuExpanded by remember { mutableStateOf(false) }
        var editProviderMenuExpanded by remember { mutableStateOf(false) }
        val isPlugin = provider.isPluginRegistered

        AlertDialog(
            onDismissRequest = { editingProvider = null },
            title = { Text(stringResource(R.string.settings_ai_edit_provider)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 名称
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.settings_ai_provider_name)) },
                        singleLine = true,
                        enabled = !isPlugin,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 提供商类型
                    if (!isPlugin) {
                        ExposedDropdownMenuBox(
                            expanded = editProviderMenuExpanded,
                            onExpandedChange = { editProviderMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = editProviderType.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.settings_ai_provider)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = editProviderMenuExpanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = editProviderMenuExpanded,
                                onDismissRequest = { editProviderMenuExpanded = false }
                            ) {
                                AIProvider.entries.forEach { prov ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                prov.displayName,
                                                fontWeight = if (editProviderType == prov) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            editProviderType = prov
                                            editProviderMenuExpanded = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // API 密钥
                    OutlinedTextField(
                        value = editApiKey,
                        onValueChange = { editApiKey = it },
                        label = { Text(stringResource(R.string.settings_ai_api_key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 自定义端点
                    if (editProviderType == AIProvider.CUSTOM) {
                        OutlinedTextField(
                            value = editEndpoint,
                            onValueChange = { editEndpoint = it },
                            label = { Text(stringResource(R.string.settings_ai_custom_endpoint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 模型选择（下拉 + 自定义）
                    Text(
                        text = stringResource(R.string.settings_ai_select_model),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val allModels = provider.allModels
                    if (allModels.isNotEmpty()) {
                        allModels.forEach { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editModel = m }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (editModel == m) Icons.Filled.CheckCircle else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = if (editModel == m) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = m,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (editModel == m) FontWeight.Bold else FontWeight.Normal
                                )
                                if (m in provider.registeredModels) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.settings_ai_plugin_models),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editModel,
                        onValueChange = { editModel = it },
                        label = { Text(stringResource(R.string.settings_ai_custom_model)) },
                        placeholder = { Text(stringResource(R.string.settings_ai_default_model, editProviderType.defaultModel)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 温度
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_ai_temperature),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format("%.1f", editTemperature),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = editTemperature,
                        onValueChange = { editTemperature = it },
                        valueRange = 0f..2f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 最大 Token
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_ai_max_tokens),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${editMaxTokens}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = editMaxTokens.toFloat(),
                        onValueChange = { editMaxTokens = it.toInt() },
                        valueRange = 256f..32768f,
                        steps = 0,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 系统提示词
                    OutlinedTextField(
                        value = editSystemPrompt,
                        onValueChange = { editSystemPrompt = it },
                        label = { Text(stringResource(R.string.settings_ai_system_prompt)) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // MCP 工具支持（仅对支持 function calling 的模型开启）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editSupportsTools = !editSupportsTools },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MCP 工具支持",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (editSupportsTools) "已开启" else "已关闭",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (editSupportsTools) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = editSupportsTools,
                            onCheckedChange = { editSupportsTools = it }
                        )
                    }
                    if (!editSupportsTools) {
                        Text(
                            text = "开启后，AI 请求将自动附带已启用的 MCP 工具定义。请确保当前模型支持 function calling 后再开启。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }

                    // 最大工具调用轮次
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "最大工具调用轮次",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${editMaxToolRounds}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = editMaxToolRounds.toFloat(),
                        onValueChange = { editMaxToolRounds = it.toInt() },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "控制 AI 最多可调用几轮工具，防止无限循环。建议值：5~15",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            },
            confirmButton = {
                Column {
                    // 设为当前
                    if (provider.id != config.activeProviderId) {
                        TextButton(
                            onClick = {
                                onConfigChange(config.copy(activeProviderId = provider.id))
                                editingProvider = null
                            }
                        ) {
                            Text(stringResource(R.string.settings_ai_set_active))
                        }
                    }
                    Row {
                        // 删除（仅非插件注册的）
                        if (!isPlugin) {
                            TextButton(
                                onClick = {
                                    onConfigChange(
                                        config.copy(
                                            providers = config.providers.filter { it.id != provider.id },
                                            activeProviderId = if (config.activeProviderId == provider.id)
                                                config.providers.firstOrNull { it.id != provider.id }?.id
                                            else config.activeProviderId
                                        )
                                    )
                                    editingProvider = null
                                }
                            ) {
                                Text(
                                    stringResource(R.string.settings_ai_delete_provider),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        // 保存
                        TextButton(
                            onClick = {
                                val updated = config.providers.map {
                                    if (it.id == provider.id) it.copy(
                                        name = editName,
                                        provider = editProviderType,
                                        apiKey = editApiKey,
                                        customEndpoint = editEndpoint,
                                        model = editModel,
                                        temperature = editTemperature,
                                        maxTokens = editMaxTokens,
                                        systemPrompt = editSystemPrompt,
                                        supportsTools = editSupportsTools,
                                        maxToolRounds = editMaxToolRounds
                                    ) else it
                                }
                                onConfigChange(config.copy(providers = updated))
                                editingProvider = null
                            }
                        ) {
                            Text(stringResource(R.string.settings_save))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProvider = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}

// ==================== MCP 设置 UI ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPSettingsSection(
    config: AIConfigData,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onConfigChange: (AIConfigData) -> Unit,
    onTestConnection: (MCPServerEntry) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var addServerName by remember { mutableStateOf("") }
    var addServerUrl by remember { mutableStateOf("") }
    var addServerSource by remember { mutableStateOf(MCPServerSource.REMOTE_URL) }
    var addServerTransport by remember { mutableStateOf("streamable_http") }
    var addSourceMenuExpanded by remember { mutableStateOf(false) }
    var addTransportMenuExpanded by remember { mutableStateOf(false) }
    var expandedServiceId by remember { mutableStateOf<String?>(null) }
    var hierarchyVersion by remember { mutableStateOf(0) }

    // 获取服务层级结构（含工具列表），hierarchyVersion 变化时重新获取
    val serviceHierarchy = remember(hierarchyVersion) { MCPManager.getServiceHierarchy() }

    SettingsCardGroup(
        title = stringResource(R.string.settings_mcp_server_list),
        icon = Icons.Filled.DataArray,
        initiallyExpanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 服务器列表
            if (config.mcpServers.isEmpty()) {
                Text(
                    stringResource(R.string.settings_mcp_no_servers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                config.mcpServers.forEach { server ->
                    val isPlugin = server.source == com.luaforge.studio.lxclua.mcp.MCPServerSource.LOCAL_PLUGIN
                    val isExpanded = expandedServiceId == server.id

                    // 查找该服务在层级结构中的信息（含工具列表）
                    val serviceInfo = if (isPlugin) {
                        val serviceName = server.id.removePrefix("plugin_service_")
                        serviceHierarchy.find { it["name"] == serviceName && it["source"] == "plugin" }
                    } else null
                    val tools = (serviceInfo?.get("tools") as? List<*>) ?: emptyList<Any>()

                    Column {
                        SettingsListItem(
                            title = server.name,
                            subtitle = if (isPlugin) {
                                val toolCount = serviceInfo?.get("toolCount") as? Int ?: 0
                                "${stringResource(R.string.settings_mcp_local_plugin)} · $toolCount 个工具"
                            } else server.url.ifBlank { stringResource(R.string.settings_mcp_no_url) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = server.enabled,
                                        onCheckedChange = { enabled ->
                                            val updated = config.mcpServers.map {
                                                if (it.id == server.id) it.copy(enabled = enabled) else it
                                            }
                                            onConfigChange(config.copy(mcpServers = updated))
                                            // 同步更新 serviceRegistry，确保 getEnabledServiceTools() 生效
                                            if (isPlugin) {
                                                val serviceName = server.id.removePrefix("plugin_service_")
                                                if (enabled) {
                                                    MCPManager.enableService(serviceName)
                                                } else {
                                                    MCPManager.disableService(serviceName)
                                                }
                                            }
                                        }
                                    )
                                    if (isPlugin) {
                                        IconButton(onClick = {
                                            expandedServiceId = if (isExpanded) null else server.id
                                        }) {
                                            Icon(
                                                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = if (isExpanded) "收起" else "展开",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    // 远程 MCP 服务器也显示展开按钮
                                    if (!isPlugin && tools.isNotEmpty()) {
                                        IconButton(onClick = {
                                            expandedServiceId = if (isExpanded) null else server.id
                                        }) {
                                            Icon(
                                                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = if (isExpanded) "收起" else "展开",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    if (!isPlugin) {
                                        IconButton(onClick = {
                                            val updated = config.mcpServers.filter { it.id != server.id }
                                            onConfigChange(config.copy(mcpServers = updated))
                                        }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = stringResource(R.string.settings_mcp_delete),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                if (isPlugin) {
                                    expandedServiceId = if (isExpanded) null else server.id
                                } else if (server.url.isNotBlank()) {
                                    // 远程 MCP 服务器：有工具时展开，否则测试连接
                                    if (tools.isNotEmpty()) {
                                        expandedServiceId = if (isExpanded) null else server.id
                                    } else {
                                        onTestConnection(server)
                                    }
                                }
                            }
                        )

                        // 展开的工具列表
                        AnimatedVisibility(
                            visible = isExpanded && tools.isNotEmpty(),
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 8.dp, bottom = 4.dp)
                            ) {
                                tools.forEach { tool ->
                                    val toolMap = tool as? Map<*, *> ?: return@forEach
                                    val toolName = toolMap["name"] as? String ?: ""
                                    val toolDesc = toolMap["description"] as? String ?: ""
                                    val toolEnabled = toolMap["enabled"] as? Boolean ?: true
                                    val serviceName = serviceInfo?.get("name") as? String ?: ""

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                toolName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (toolDesc.isNotBlank()) {
                                                Text(
                                                    toolDesc,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = toolEnabled,
                                            onCheckedChange = { enabled ->
                                                if (enabled) {
                                                    MCPManager.enableServiceTool(serviceName, toolName)
                                                } else {
                                                    MCPManager.disableServiceTool(serviceName, toolName)
                                                }
                                                // 刷新层级数据
                                                hierarchyVersion++
                                            }
                                        )
                                    }
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                                    )
                                }
                            }
                        }
                    }
                    if (server != config.mcpServers.last()) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )

            // 添加服务器按钮
            SettingsListItem(
                title = stringResource(R.string.settings_mcp_add_server),
                subtitle = stringResource(R.string.settings_mcp_add_server_desc),
                onClick = { showAddDialog = true }
            )
        }
    }

    // 添加服务器对话框
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                addServerName = ""
                addServerUrl = ""
                addServerSource = MCPServerSource.REMOTE_URL
                addServerTransport = "streamable_http"
            },
            title = { Text(stringResource(R.string.settings_mcp_add_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 服务器名称
                    OutlinedTextField(
                        value = addServerName,
                        onValueChange = { addServerName = it },
                        label = { Text(stringResource(R.string.settings_mcp_server_name)) },
                        placeholder = { Text(stringResource(R.string.settings_mcp_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 服务器类型
                    ExposedDropdownMenuBox(
                        expanded = addSourceMenuExpanded,
                        onExpandedChange = { addSourceMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = when (addServerSource) {
                                MCPServerSource.LOCAL_PLUGIN -> stringResource(R.string.settings_mcp_source_local)
                                MCPServerSource.REMOTE_URL -> stringResource(R.string.settings_mcp_source_remote)
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.settings_mcp_source)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = addSourceMenuExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = addSourceMenuExpanded,
                            onDismissRequest = { addSourceMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.settings_mcp_source_remote),
                                        fontWeight = if (addServerSource == MCPServerSource.REMOTE_URL) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    addServerSource = MCPServerSource.REMOTE_URL
                                    addSourceMenuExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.settings_mcp_source_local),
                                        fontWeight = if (addServerSource == MCPServerSource.LOCAL_PLUGIN) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    addServerSource = MCPServerSource.LOCAL_PLUGIN
                                    addSourceMenuExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // URL（仅远程类型显示）
                    if (addServerSource == MCPServerSource.REMOTE_URL) {
                        OutlinedTextField(
                            value = addServerUrl,
                            onValueChange = { addServerUrl = it },
                            label = { Text(stringResource(R.string.settings_mcp_server_url)) },
                            placeholder = { Text("http://localhost:8080/mcp") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 传输类型
                    val transportOptions = listOf("streamable_http" to stringResource(R.string.settings_mcp_transport_streamable_http), "sse" to stringResource(R.string.settings_mcp_transport_sse))
                    ExposedDropdownMenuBox(
                        expanded = addTransportMenuExpanded,
                        onExpandedChange = { addTransportMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = transportOptions.find { it.first == addServerTransport }?.second ?: addServerTransport,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.settings_mcp_transport)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = addTransportMenuExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = addTransportMenuExpanded,
                            onDismissRequest = { addTransportMenuExpanded = false }
                        ) {
                            transportOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            label,
                                            fontWeight = if (addServerTransport == value) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        addServerTransport = value
                                        addTransportMenuExpanded = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val isValid = when (addServerSource) {
                            MCPServerSource.REMOTE_URL -> addServerName.isNotBlank() && addServerUrl.isNotBlank()
                            MCPServerSource.LOCAL_PLUGIN -> addServerName.isNotBlank()
                        }
                        if (isValid) {
                            val newServer = MCPServerEntry(
                                name = addServerName,
                                url = addServerUrl,
                                source = addServerSource,
                                transport = addServerTransport
                            )
                            onConfigChange(config.copy(mcpServers = config.mcpServers + newServer))
                            showAddDialog = false
                            addServerName = ""
                            addServerUrl = ""
                            addServerSource = MCPServerSource.REMOTE_URL
                            addServerTransport = "streamable_http"
                        }
                    },
                    enabled = when (addServerSource) {
                        MCPServerSource.REMOTE_URL -> addServerName.isNotBlank() && addServerUrl.isNotBlank()
                        MCPServerSource.LOCAL_PLUGIN -> addServerName.isNotBlank()
                    }
                ) {
                    Text(stringResource(R.string.settings_add))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    addServerName = ""
                    addServerUrl = ""
                    addServerSource = MCPServerSource.REMOTE_URL
                    addServerTransport = "streamable_http"
                }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}