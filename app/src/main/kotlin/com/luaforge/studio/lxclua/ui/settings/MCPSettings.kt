package com.luaforge.studio.lxclua.ui.settings

import android.content.Context
import android.content.Intent
import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
fun MCPSettingsSection(
    config: AIConfigData,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onConfigChange: (AIConfigData) -> Unit,
    onTestConnection: suspend (MCPServerEntry) -> Boolean
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
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // 广播地址锁定状态：serverId -> isLocked，从配置初始化
    var broadcastLocks by remember {
        mutableStateOf(
            config.mcpServers.associate { it.id to it.portLocked }
        )
    }
    // 当配置变化时更新锁定状态
    LaunchedEffect(config.mcpServers) {
        broadcastLocks = config.mcpServers.associate { it.id to it.portLocked }
    }

    // 获取当前 Context（非 Composable 上下文使用）
    val context = LocalContext.current

    // 通知权限请求（Android 13+）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onConfigChange(config.copy(keepAliveNotification = true))
        }
    }

    // 电池优化白名单请求
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        if (isIgnored) {
            onConfigChange(config.copy(keepAliveInBackground = true))
        }
    }

    // 获取服务层级结构（含工具列表），hierarchyVersion 变化时重新获取
    val serviceHierarchy = remember(hierarchyVersion) { MCPManager.getServiceHierarchy() }

    // 广播状态（每个服务器独立端口，不再共用全局端口）

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
                    } else {
                        // 远程 MCP 服务器：按服务器名称匹配，source 为 remote
                        serviceHierarchy.find { it["name"] == server.name && it["source"] == "remote" }
                    }
                    val tools = (serviceInfo?.get("tools") as? List<*>) ?: emptyList<Any>()

                    Column {
                        SettingsListItem(
                            title = server.name,
                            subtitle = if (isPlugin) {
                                val toolCount = serviceInfo?.get("toolCount") as? Int ?: 0
                                "${stringResource(R.string.settings_mcp_local_plugin)} · $toolCount 个工具"
                            } else {
                                val toolCount = serviceInfo?.get("toolCount") as? Int ?: 0
                                val urlText = server.url.ifBlank { stringResource(R.string.settings_mcp_no_url) }
                                if (toolCount > 0) "$urlText · $toolCount 个工具" else urlText
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 广播开关
                                    IconButton(
                                        onClick = {
                                            val newBroadcast = !server.broadcastEnabled
                                            val updated = config.mcpServers.map {
                                                if (it.id == server.id) it.copy(broadcastEnabled = newBroadcast) else it
                                            }
                                            onConfigChange(config.copy(mcpServers = updated))
                                            scope.launch {
                                                MCPManager.refreshBroadcast()
                                                hierarchyVersion++ // 刷新广播地址显示
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = if (server.broadcastEnabled) "关闭广播" else "开启广播",
                                            tint = if (server.broadcastEnabled)
                                                MaterialTheme.colorScheme.tertiary
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Switch(
                                        checked = server.enabled,
                                        onCheckedChange = { enabled ->
                                            val updated = config.mcpServers.map {
                                                if (it.id == server.id) it.copy(enabled = enabled) else it
                                            }
                                            onConfigChange(config.copy(mcpServers = updated))
                                            // 同步更新服务状态
                                            if (isPlugin) {
                                                val serviceName = server.id.removePrefix("plugin_service_")
                                                if (enabled) {
                                                    MCPManager.enableService(serviceName)
                                                } else {
                                                    MCPManager.disableService(serviceName)
                                                }
                                            } else {
                                                // 远程 MCP 服务器：启用时连接，禁用时断开
                                                scope.launch {
                                                    if (enabled) {
                                                        val success = onTestConnection(server)
                                                        if (success) {
                                                            hierarchyVersion++
                                                        }
                                                    } else {
                                                        MCPManager.disconnectServer(server)
                                                        hierarchyVersion++
                                                    }
                                                }
                                            }
                                            // 启用/禁用后刷新广播
                                            scope.launch {
                                                MCPManager.refreshBroadcast()
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
                                    // 远程 MCP 服务器也显示展开按钮（有工具或广播时）
                                    if (!isPlugin && (tools.isNotEmpty() || server.broadcastEnabled)) {
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
                                } else if (server.url.isNotBlank() || server.broadcastEnabled) {
                                    // 远程 MCP 服务器：有工具或广播时展开，否则测试连接
                                    if (tools.isNotEmpty() || server.broadcastEnabled) {
                                        expandedServiceId = if (isExpanded) null else server.id
                                    } else {
                                        scope.launch {
                                            val success = onTestConnection(server)
                                            if (success) {
                                                // 连接成功，刷新 UI 显示工具列表
                                                hierarchyVersion++
                                            }
                                        }
                                    }
                                }
                            }
                        )

                        // 展开区域：工具列表 + 广播地址
                        AnimatedVisibility(
                            visible = isExpanded && (tools.isNotEmpty() || server.broadcastEnabled),
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            key(hierarchyVersion) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 8.dp, bottom = 4.dp)
                            ) {
                                // 广播地址显示（仅在广播开启时显示）
                                if (server.broadcastEnabled) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        val isLocked = broadcastLocks[server.id] ?: true
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "广播地址",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                                contentDescription = if (isLocked) "锁定" else "解锁",
                                                tint = if (isLocked)
                                                    MaterialTheme.colorScheme.tertiary
                                                else
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable {
                                                        val newLocked = !isLocked
                                                        broadcastLocks = broadcastLocks.toMutableMap().apply {
                                                            put(server.id, newLocked)
                                                        }
                                                        // 调用 API 持久化端口锁定状态
                                                        MCPManager.setPortLocked(server.id, newLocked)
                                                        // 刷新广播以应用新的端口设置
                                                        scope.launch {
                                                            MCPManager.refreshBroadcast()
                                                            hierarchyVersion++
                                                        }
                                                    }
                                            )
                                        }
                                        var addresses by remember(server.id, hierarchyVersion) { mutableStateOf<List<String>>(emptyList()) }
                                        LaunchedEffect(server.id, hierarchyVersion) {
                                            addresses = MCPManager.getBroadcastAddresses(server.id)
                                        }
                                        if (addresses.isEmpty()) {
                                            Text(
                                                "等待服务器启动...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        } else {
                                            addresses.forEach { addr ->
                                                Text(
                                                    addr,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                    modifier = Modifier
                                                        .padding(vertical = 1.dp)
                                                        .clickable {
                                                            clipboardManager.setText(AnnotatedString(addr))
                                                        }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                                // 广播与工具之间的分隔线
                                if (server.broadcastEnabled && tools.isNotEmpty()) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                    )
                                }
                                // 工具列表
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
                            } // key(hierarchyVersion)
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

            // 添加服务器按钮
            SettingsListItem(
                title = stringResource(R.string.settings_mcp_add_server),
                subtitle = stringResource(R.string.settings_mcp_add_server_desc),
                onClick = { showAddDialog = true }
            )
        }
    }

    // 保活设置
    SettingsCardGroup(
        title = "保活设置",
        icon = Icons.Filled.Settings,
        initiallyExpanded = false,
        onExpandedChange = { }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsListItem(
                title = "保活总开关",
                subtitle = "开启后广播服务器将持续运行",
                onClick = {},
                trailingContent = {
                    Switch(
                        checked = config.keepAliveEnabled,
                        onCheckedChange = { enabled ->
                            onConfigChange(config.copy(keepAliveEnabled = enabled))
                        }
                    )
                }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            SettingsListItem(
                title = "后台保活",
                subtitle = "应用切到后台时保持广播服务器运行，需要加入电池优化白名单",
                onClick = {},
                trailingContent = {
                    Switch(
                        checked = config.keepAliveInBackground,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // 请求电池优化白名单
                                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                                if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    batteryOptimizationLauncher.launch(intent)
                                } else {
                                    onConfigChange(config.copy(keepAliveInBackground = true))
                                }
                            } else {
                                onConfigChange(config.copy(keepAliveInBackground = false))
                            }
                        }
                    )
                }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            SettingsListItem(
                title = "通知栏保活",
                subtitle = "通过前台通知防止系统杀死进程，需要通知权限",
                onClick = {},
                trailingContent = {
                    Switch(
                        checked = config.keepAliveNotification,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Android 13+ 需要请求通知权限
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onConfigChange(config.copy(keepAliveNotification = true))
                                }
                            } else {
                                onConfigChange(config.copy(keepAliveNotification = false))
                            }
                        }
                    )
                }
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