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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
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

