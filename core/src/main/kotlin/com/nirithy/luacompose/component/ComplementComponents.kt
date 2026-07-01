package com.nirithy.luacompose.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer

/**
 * 补充组件插件：FAB、Chip、Tab、Drawer、SearchBar、DatePicker、ProgressIndicator、Badge
 *
 * 这些组件 KSP 已注册，但缺少手动渲染器（快速路径 + 自定义属性解析）。
 * 与 KSP 生成的 BridgeRenderer 互补：快速路径注册后优先使用此渲染器。
 */
@OptIn(ExperimentalMaterial3Api::class)
object ComplementComponents : ComposePlugin {
    override val namespace = "complement"

    override fun getComponents() = mapOf<String, @Composable (ComposeNode) -> Unit>(
        // FAB 系列
        "FloatingActionButton" to { node -> FabLayout(node) },
        "SmallFloatingActionButton" to { node -> SmallFabLayout(node) },
        "LargeFloatingActionButton" to { node -> LargeFabLayout(node) },
        "ExtendedFloatingActionButton" to { node -> ExtendedFabLayout(node) },

        // Chip 系列
        "AssistChip" to { node -> AssistChipLayout(node) },
        "FilterChip" to { node -> FilterChipLayout(node) },
        "InputChip" to { node -> InputChipLayout(node) },
        "SuggestionChip" to { node -> SuggestionChipLayout(node) },

        // Tab 系列
        "TabRow" to { node -> TabRowLayout(node) },
        "Tab" to { node -> TabLayout(node) },
        "ScrollableTabRow" to { node -> ScrollableTabRowLayout(node) },

        // Drawer 系列
        "ModalNavigationDrawer" to { node -> ModalDrawerLayout(node) },
        "DismissibleNavigationDrawer" to { node -> DismissibleDrawerLayout(node) },
        "ModalDrawerSheet" to { node -> DrawerSheetLayout(node) },
        "PermanentNavigationDrawer" to { node -> PermanentDrawerLayout(node) },

        // SearchBar
        "SearchBar" to { node -> SearchBarLayout(node) },
        "DockedSearchBar" to { node -> DockedSearchBarLayout(node) },

        // DatePicker / TimePicker
        "DatePicker" to { node -> DatePickerLayout(node) },
        "DatePickerDialog" to { node -> DatePickerDialogLayout(node) },
        "TimePicker" to { node -> TimePickerLayout(node) },

        // ProgressIndicator
        "LinearProgressIndicator" to { node -> LinearProgressLayout(node) },
        "CircularProgressIndicator" to { node -> CircularProgressLayout(node) },

        // Badge
        "Badge" to { node -> BadgeLayout(node) },
        "BadgedBox" to { node -> BadgedBoxLayout(node) },
    )

    // ========== FAB ==========

    @Composable
    private fun FabLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        FloatingActionButton(
            onClick = { onClick?.call() },
            modifier = ComposeRenderer.resolveModifier(node),
            containerColor = node.props["color"]?.let { resolveColor(it) } ?: MaterialTheme.colorScheme.primaryContainer,
        ) { ComposeRenderer.RenderChildren(node) }
    }

    @Composable
    private fun SmallFabLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        SmallFloatingActionButton(
            onClick = { onClick?.call() },
            modifier = ComposeRenderer.resolveModifier(node),
        ) { ComposeRenderer.RenderChildren(node) }
    }

    @Composable
    private fun LargeFabLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        LargeFloatingActionButton(
            onClick = { onClick?.call() },
            modifier = ComposeRenderer.resolveModifier(node),
        ) { ComposeRenderer.RenderChildren(node) }
    }

    @Composable
    private fun ExtendedFabLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        val text = node.stringProp("text") ?: ""
        ExtendedFloatingActionButton(
            onClick = { onClick?.call() },
            modifier = ComposeRenderer.resolveModifier(node),
            text = { Text(text) },
            icon = { ComposeRenderer.RenderChildren(node) },
        )
    }

    // ========== Chip ==========

    @Composable
    private fun AssistChipLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        val label = node.stringProp("label") ?: ""
        AssistChip(
            onClick = { onClick?.call() },
            modifier = ComposeRenderer.resolveModifier(node),
            label = { Text(label) },
            leadingIcon = if (node.children.isNotEmpty()) { { ComposeRenderer.RenderChildren(node) } } else null,
        )
    }

    @Composable
    private fun FilterChipLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        val label = node.stringProp("label") ?: ""
        val selected = node.boolProp("selected", false)
        FilterChip(
            onClick = { onClick?.call() },
            modifier = ComposeRenderer.resolveModifier(node),
            selected = selected,
            label = { Text(label) },
            leadingIcon = if (selected) {
                { Icon(Icons.Filled.Info, contentDescription = null) }
            } else null,
        )
    }

    @Composable
    private fun InputChipLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        val label = node.stringProp("label") ?: ""
        val selected = node.boolProp("selected", false)
        InputChip(
            onClick = { onClick?.call() },
            modifier = ComposeRenderer.resolveModifier(node),
            selected = selected,
            label = { Text(label) },
        )
    }

    @Composable
    private fun SuggestionChipLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        val label = node.stringProp("label") ?: ""
        SuggestionChip(
            onClick = { onClick?.call() },
            modifier = ComposeRenderer.resolveModifier(node),
            label = { Text(label) },
        )
    }

    // ========== Tab ==========

    @Composable
    private fun TabRowLayout(node: ComposeNode) {
        val selectedIndex = (node.props["selectedIndex"] as? Number)?.toInt() ?: 0
        TabRow(
            selectedTabIndex = selectedIndex,
            modifier = ComposeRenderer.resolveModifier(node),
        ) { ComposeRenderer.RenderChildren(node) }
    }

    @Composable
    private fun TabLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        val text = node.stringProp("text") ?: ""
        val selected = node.boolProp("selected", false)
        Tab(
            selected = selected,
            onClick = { onClick?.call() },
            modifier = ComposeRenderer.resolveModifier(node),
            text = { Text(text) },
        )
    }

    @Composable
    private fun ScrollableTabRowLayout(node: ComposeNode) {
        val selectedIndex = (node.props["selectedIndex"] as? Number)?.toInt() ?: 0
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = ComposeRenderer.resolveModifier(node),
            edgePadding = node.floatProp("edgePadding", 52f).dp,
        ) { ComposeRenderer.RenderChildren(node) }
    }

    // ========== Drawer ==========

    @Composable
    private fun ModalDrawerLayout(node: ComposeNode) {
        val drawerContent = node.children.find { it.type == "DrawerSheet" || it.props["_drawerSlot"] == "drawer" }
        val contentNode = node.children.find { it.type != "DrawerSheet" && it.props["_drawerSlot"] != "drawer" }
        var drawerState by remember { mutableStateOf(false) }
        ModalNavigationDrawer(
            drawerState = rememberDrawerState(if (drawerState) DrawerValue.Open else DrawerValue.Closed),
            drawerContent = {
                if (drawerContent != null) ComposeRenderer.Render(drawerContent)
            },
            modifier = ComposeRenderer.resolveModifier(node),
        ) { if (contentNode != null) ComposeRenderer.Render(contentNode) }
    }

    @Composable
    private fun DismissibleDrawerLayout(node: ComposeNode) {
        val drawerContent = node.children.find { it.type == "DrawerSheet" || it.props["_drawerSlot"] == "drawer" }
        DismissibleNavigationDrawer(
            drawerContent = {
                if (drawerContent != null) ComposeRenderer.Render(drawerContent)
            },
            modifier = ComposeRenderer.resolveModifier(node),
        ) { ComposeRenderer.RenderChildren(node) }
    }

    @Composable
    private fun DrawerSheetLayout(node: ComposeNode) {
        ModalDrawerSheet(modifier = ComposeRenderer.resolveModifier(node)) {
            ComposeRenderer.RenderChildren(node)
        }
    }

    @Composable
    private fun PermanentDrawerLayout(node: ComposeNode) {
        val drawerContent = node.children.find { it.type == "DrawerSheet" || it.props["_drawerSlot"] == "drawer" }
        PermanentNavigationDrawer(
            drawerContent = {
                if (drawerContent != null) ComposeRenderer.Render(drawerContent)
            },
            modifier = ComposeRenderer.resolveModifier(node),
        ) { ComposeRenderer.RenderChildren(node) }
    }

    // ========== SearchBar ==========

    @Composable
    private fun SearchBarLayout(node: ComposeNode) {
        var query by remember { mutableStateOf(node.stringProp("query") ?: "") }
        val placeholder = node.stringProp("placeholder") ?: "搜索..."
        val onQueryChange = node.callbacks["onQueryChange"]
        val onSearch = node.callbacks["onSearch"]

        SearchBar(
            query = query,
            onQueryChange = { newQuery ->
                query = newQuery
                onQueryChange?.call(newQuery)
            },
            onSearch = { onSearch?.call(query) },
            active = node.boolProp("active", false),
            onActiveChange = {},
            modifier = ComposeRenderer.resolveModifier(node),
            placeholder = { Text(placeholder) },
            leadingIcon = node.props["leadingIcon"]?.let { iconName ->
                { Icon(IconComponent.iconMap[iconName.toString()] ?: Icons.Filled.Info, contentDescription = null) }
            },
        ) { ComposeRenderer.RenderChildren(node) }
    }

    @Composable
    private fun DockedSearchBarLayout(node: ComposeNode) {
        var query by remember { mutableStateOf(node.stringProp("query") ?: "") }
        val placeholder = node.stringProp("placeholder") ?: "搜索..."
        val onQueryChange = node.callbacks["onQueryChange"]

        DockedSearchBar(
            query = query,
            onQueryChange = { newQuery ->
                query = newQuery
                onQueryChange?.call(newQuery)
            },
            onSearch = {},
            active = false,
            onActiveChange = {},
            modifier = ComposeRenderer.resolveModifier(node),
            placeholder = { Text(placeholder) },
        ) { ComposeRenderer.RenderChildren(node) }
    }

    // ========== DatePicker / TimePicker ==========

    @Composable
    private fun DatePickerLayout(node: ComposeNode) {
        val state = rememberDatePickerState()
        val onDateSelected = node.callbacks["onDateSelected"]
        // 日期变化时回调
        LaunchedEffect(state.selectedDateMillis) {
            state.selectedDateMillis?.let { onDateSelected?.call(it) }
        }
        DatePicker(
            state = state,
            modifier = ComposeRenderer.resolveModifier(node),
        )
    }

    @Composable
    private fun DatePickerDialogLayout(node: ComposeNode) {
        val onConfirm = node.callbacks["onConfirm"]
        val onDismiss = node.callbacks["onDismiss"]
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { onDismiss?.call() },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm?.call(state.selectedDateMillis)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss?.call() }) { Text("取消") }
            },
        ) { DatePicker(state = state, modifier = ComposeRenderer.resolveModifier(node)) }
    }

    @Composable
    private fun TimePickerLayout(node: ComposeNode) {
        val state = rememberTimePickerState()
        TimePicker(
            state = state,
            modifier = ComposeRenderer.resolveModifier(node),
        )
    }

    // ========== ProgressIndicator ==========

    @Composable
    private fun LinearProgressLayout(node: ComposeNode) {
        val progress = node.floatProp("progress", 0f)
        val modifier = ComposeRenderer.resolveModifier(node)
        val color = node.props["color"]?.let { resolveColor(it) } ?: MaterialTheme.colorScheme.primary
        if (progress > 0f) {
            LinearProgressIndicator(progress = { progress }, modifier = modifier, color = color)
        } else {
            LinearProgressIndicator(modifier = modifier, color = color)
        }
    }

    @Composable
    private fun CircularProgressLayout(node: ComposeNode) {
        val progress = node.floatProp("progress", 0f)
        val modifier = ComposeRenderer.resolveModifier(node)
        val color = node.props["color"]?.let { resolveColor(it) } ?: MaterialTheme.colorScheme.primary
        val strokeWidth = node.floatProp("strokeWidth", 4f).dp
        if (progress > 0f) {
            CircularProgressIndicator(progress = { progress }, modifier = modifier, color = color, strokeWidth = strokeWidth)
        } else {
            CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
        }
    }

    // ========== Badge ==========

    @Composable
    private fun BadgeLayout(node: ComposeNode) {
        val text = node.stringProp("text")
        Badge(
            modifier = ComposeRenderer.resolveModifier(node),
            containerColor = node.props["color"]?.let { resolveColor(it) } ?: MaterialTheme.colorScheme.error,
        ) {
            if (text != null) Text(text)
            else ComposeRenderer.RenderChildren(node)
        }
    }

    @Composable
    private fun BadgedBoxLayout(node: ComposeNode) {
        val badgeNode = node.children.find { it.type == "Badge" }
        BadgedBox(
            modifier = ComposeRenderer.resolveModifier(node),
            badge = {
                if (badgeNode != null) ComposeRenderer.Render(badgeNode)
                else {
                    val count = (node.props["badgeCount"] as? Number)?.toInt()
                    if (count != null && count > 0) {
                        Badge { Text(count.toString()) }
                    }
                }
            },
        ) { ComposeRenderer.RenderChildren(node) }
    }

    // ========== 工具 ==========

    private fun resolveColor(value: Any?): Color = when (value) {
        is Long -> Color(value.toInt())
        is Int -> Color(value)
        is Double -> Color(value.toLong().toInt())
        is Number -> Color(value.toInt())
        else -> Color.Unspecified
    }
}