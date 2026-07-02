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

