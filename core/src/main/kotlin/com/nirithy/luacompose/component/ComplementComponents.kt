package com.nirithy.luacompose.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer

/**
 * 补充组件插件：FAB、Chip、Tab、Drawer、SearchBar、DatePicker、ProgressIndicator、Badge、DropdownMenu、BottomSheet
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

        // DropdownMenu / BottomSheet
        "DropdownMenu" to { node -> DropdownMenuLayout(node) },
        "ModalBottomSheet" to { node -> ModalBottomSheetLayout(node) },

        // Snackbar
        "SnackbarHost" to { node -> SnackbarHostLayout(node) },

        // ExposedDropdownMenu
        "ExposedDropdownMenuBox" to { node -> ExposedDropdownMenuBoxLayout(node) },

        // PullToRefresh
        "PullToRefreshBox" to { node -> PullToRefreshBoxLayout(node) },

        // Pager
        "HorizontalPager" to { node -> HorizontalPagerLayout(node) },
        "VerticalPager" to { node -> VerticalPagerLayout(node) },

        // AlertDialog
        "AlertDialog" to { node -> AlertDialogLayout(node) },

        // NavigationBar
        "NavigationBar" to { node -> NavigationBarLayout(node) },

        // SwipeToDismiss
        "SwipeToDismissBox" to { node -> SwipeToDismissBoxLayout(node) },

        // SegmentedButton
        "SingleChoiceSegmentedButtonRow" to { node -> SingleChoiceSegmentedButtonRowLayout(node) },
        "MultiChoiceSegmentedButtonRow" to { node -> MultiChoiceSegmentedButtonRowLayout(node) },

        // Popup
        "Popup" to { node -> PopupLayout(node) },
    )

    // ========== FAB ==========

    @Composable
    private fun FabLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        FloatingActionButton(
            onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() } },
            modifier = ComposeRenderer.resolveModifier(node),
            containerColor = node.props["color"]?.let { resolveColor(it) } ?: MaterialTheme.colorScheme.primaryContainer,
        ) { ComposeRenderer.RenderChildren(node) }
    }

    @Composable
    private fun SmallFabLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        SmallFloatingActionButton(
            onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() } },
            modifier = ComposeRenderer.resolveModifier(node),
        ) { ComposeRenderer.RenderChildren(node) }
    }

    @Composable
    private fun LargeFabLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        LargeFloatingActionButton(
            onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() } },
            modifier = ComposeRenderer.resolveModifier(node),
        ) { ComposeRenderer.RenderChildren(node) }
    }

    @Composable
    private fun ExtendedFabLayout(node: ComposeNode) {
        val onClick = node.callbacks["onClick"]
        val text = node.stringProp("text") ?: ""
        ExtendedFloatingActionButton(
            onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() } },
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
            onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() } },
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
            onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() } },
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
            onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() } },
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
            onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() } },
            modifier = ComposeRenderer.resolveModifier(node),
            label = { Text(label) },
        )
    }

    // ========== Tab ==========

    @Composable
    private fun TabRowLayout(node: ComposeNode) {
        val selectedIndex = node.floatProp("selectedIndex", 0f).toInt()
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
            onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() } },
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
        val gesturesEnabled = node.boolProp("gesturesEnabled", true)
        val openProp = node.boolProp("open", false)
        val onOpen = node.callbacks["onOpen"]
        val onClose = node.callbacks["onClose"]
        val drawerState = rememberDrawerState(if (openProp) DrawerValue.Open else DrawerValue.Closed)

        // 响应外部open属性变化
        LaunchedEffect(openProp) {
            if (openProp && drawerState.isClosed) drawerState.open()
            else if (!openProp && drawerState.isOpen) drawerState.close()
        }
        // 同步抽屉状态变化到Lua回调
        LaunchedEffect(drawerState.currentValue) {
            if (drawerState.currentValue == DrawerValue.Open) synchronized(ComposeBridgeInstance.current.luaLock) { onOpen?.call() }
            else if (drawerState.currentValue == DrawerValue.Closed) synchronized(ComposeBridgeInstance.current.luaLock) { onClose?.call() }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = gesturesEnabled,
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
        var active by remember { mutableStateOf(node.boolProp("active", false)) }
        val placeholder = node.stringProp("placeholder") ?: "搜索..."
        val onQueryChange = node.callbacks["onQueryChange"]
        val onSearch = node.callbacks["onSearch"]
        val onActiveChange = node.callbacks["onActiveChange"]

        // 同步外部props变化到本地状态
        val externalQuery = node.stringProp("query") ?: ""
        LaunchedEffect(externalQuery) { if (query != externalQuery) query = externalQuery }
        val externalActive = node.boolProp("active", false)
        LaunchedEffect(externalActive) { if (active != externalActive) active = externalActive }

        SearchBar(
            query = query,
            onQueryChange = { newQuery ->
                query = newQuery
                synchronized(ComposeBridgeInstance.current.luaLock) { onQueryChange?.call(newQuery) }
            },
            onSearch = { synchronized(ComposeBridgeInstance.current.luaLock) { onSearch?.call(query) } },
            active = active,
            onActiveChange = { newActive ->
                active = newActive
                synchronized(ComposeBridgeInstance.current.luaLock) { onActiveChange?.call(newActive) }
            },
            modifier = ComposeRenderer.resolveModifier(node).heightIn(max = 400.dp),
            placeholder = { Text(placeholder) },
            leadingIcon = node.props["leadingIcon"]?.let { iconName ->
                { Icon(IconComponent.iconMap[iconName.toString()] ?: Icons.Filled.Info, contentDescription = null) }
            },
        ) {
            // ★ 修复 Material3 SearchBar 内部 measure 异常（IllegalArgumentException: height）
            // SearchBar 展开时内容区域可能获得无限高度约束，用 Column + heightIn 限制
            Column(modifier = Modifier.heightIn(max = 280.dp)) {
                ComposeRenderer.RenderChildren(node)
            }
        }
    }

    @Composable
    private fun DockedSearchBarLayout(node: ComposeNode) {
        var query by remember { mutableStateOf(node.stringProp("query") ?: "") }
        var active by remember { mutableStateOf(node.boolProp("active", false)) }
        val placeholder = node.stringProp("placeholder") ?: "搜索..."
        val onQueryChange = node.callbacks["onQueryChange"]
        val onSearch = node.callbacks["onSearch"]
        val onActiveChange = node.callbacks["onActiveChange"]

        // 同步外部props变化到本地状态
        val externalQuery = node.stringProp("query") ?: ""
        LaunchedEffect(externalQuery) { if (query != externalQuery) query = externalQuery }

        DockedSearchBar(
            query = query,
            onQueryChange = { newQuery ->
                query = newQuery
                synchronized(ComposeBridgeInstance.current.luaLock) { onQueryChange?.call(newQuery) }
            },
            onSearch = { synchronized(ComposeBridgeInstance.current.luaLock) { onSearch?.call(query) } },
            active = active,
            onActiveChange = { newActive ->
                active = newActive
                synchronized(ComposeBridgeInstance.current.luaLock) { onActiveChange?.call(newActive) }
            },
            modifier = ComposeRenderer.resolveModifier(node).heightIn(max = 400.dp),
            placeholder = { Text(placeholder) },
        ) {
            Column(modifier = Modifier.heightIn(max = 280.dp)) {
                ComposeRenderer.RenderChildren(node)
            }
        }
    }

    // ========== DatePicker / TimePicker ==========

    @Composable
    private fun DatePickerLayout(node: ComposeNode) {
        val state = rememberDatePickerState()
        val onDateSelected = node.callbacks["onDateSelected"]
        // 日期变化时回调
        LaunchedEffect(state.selectedDateMillis) {
            state.selectedDateMillis?.let { synchronized(ComposeBridgeInstance.current.luaLock) { onDateSelected?.call(it) } }
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
            onDismissRequest = { synchronized(ComposeBridgeInstance.current.luaLock) { onDismiss?.call() } },
            confirmButton = {
                TextButton(onClick = {
                    synchronized(ComposeBridgeInstance.current.luaLock) { onConfirm?.call(state.selectedDateMillis) }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onDismiss?.call() } }) { Text("取消") }
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

    // ========== DropdownMenu ==========

    /**
     * 下拉菜单组件
     * Lua 用法：
     *   compose.DropdownMenu {
     *     expanded = showMenu.value,
     *     onDismissRequest = function() showMenu.value = false end,
     *     children = {
     *       compose.DropdownMenuItem({ text = "选项1", onClick = function() ... end }),
     *       compose.DropdownMenuItem({ text = "选项2", onClick = function() ... end }),
     *     },
     *   }
     * 每个子节点如果 type 为 "DropdownMenuItem"，自动渲染为 DropdownMenuItem
     */
    @Composable
    private fun DropdownMenuLayout(node: ComposeNode) {
        val expanded = node.boolProp("expanded", false)
        val onDismiss = node.callbacks["onDismissRequest"]

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { synchronized(ComposeBridgeInstance.current.luaLock) { onDismiss?.call() } },
            modifier = ComposeRenderer.resolveModifier(node),
        ) {
            // 渲染子节点：DropdownMenuItem 直接渲染为菜单项，其他节点作为内容
            for (child in node.children) {
                if (child.type == "DropdownMenuItem") {
                    DropdownMenuItem(
                        text = { Text(child.stringProp("text") ?: "") },
                        onClick = {
                            val onClick = child.callbacks["onClick"]
                            synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() }
                        },
                        leadingIcon = child.props["leadingIcon"]?.let { iconName ->
                            { Icon(IconComponent.iconMap[iconName.toString()] ?: Icons.Filled.Info, contentDescription = null) }
                        },
                        enabled = child.boolProp("enabled", true),
                    )
                } else {
                    ComposeRenderer.RenderNode(child)
                }
            }
        }
    }

    // ========== ModalBottomSheet ==========

    /**
     * 底部弹出面板
     * Lua 用法：
     *   compose.ModalBottomSheet {
     *     visible = showSheet.value,
     *     onDismissRequest = function() showSheet.value = false end,
     *     children = {
     *       compose.Text({ text = "底部面板内容" }),
     *     },
     *   }
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ModalBottomSheetLayout(node: ComposeNode) {
        val visible = node.boolProp("visible", false)
        val onDismiss = node.callbacks["onDismissRequest"]
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = node.boolProp("skipPartiallyExpanded", false),
        )

        // 用 snapshotFlow 监听 visible 变化，避免 LaunchedEffect 重启时的竞态
        // 同时确保 sheetState 在 show/hide 之间切换时不会闪烁
        LaunchedEffect(Unit) {
            snapshotFlow { visible }
                .collect { v ->
                    if (v) {
                        // 仅在隐藏状态时执行 show，避免重复调用
                        if (!sheetState.isVisible) {
                            try { sheetState.show() } catch (_: Exception) {}
                        }
                    } else {
                        if (sheetState.isVisible) {
                            try { sheetState.hide() } catch (_: Exception) {}
                        }
                    }
                }
        }

        // 拖拽关闭时同步 visible 状态
        LaunchedEffect(sheetState.isVisible) {
            if (!sheetState.isVisible && visible) {
                synchronized(ComposeBridgeInstance.current.luaLock) { onDismiss?.call() }
            }
        }

        if (visible || sheetState.isVisible) {
            ModalBottomSheet(
                onDismissRequest = { synchronized(ComposeBridgeInstance.current.luaLock) { onDismiss?.call() } },
                modifier = ComposeRenderer.resolveModifier(node),
                sheetState = sheetState,
                dragHandle = if (node.boolProp("dragHandle", true)) {
                    { BottomSheetDefaults.DragHandle() }
                } else null,
            ) {
                ComposeRenderer.RenderChildren(node)
            }
        }
    }

    // ========== Snackbar ==========

    /**
     * Snackbar 宿主，挂在 Scaffold 的 snackbarHost 插槽中使用
     * Lua 用法：
     *   compose.SnackbarHost {
     *     hostState = snackbarState,  -- 通过 compose.SnackbarHostState() 创建
     *   }
     * 配合 scaffold 使用：
     *   compose.Scaffold {
     *     snackbarHost = { compose.SnackbarHost({ hostState = snackbarState }) },
     *     ...
     *   }
     */
    @Composable
    private fun SnackbarHostLayout(node: ComposeNode) {
        val hostState = node.props["hostState"] as? SnackbarHostState
            ?: remember { SnackbarHostState() }
        SnackbarHost(
            hostState = hostState,
            modifier = ComposeRenderer.resolveModifier(node),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = node.props["color"]?.let { resolveColor(it) } ?: SnackbarDefaults.color,
                actionColor = node.props["actionColor"]?.let { resolveColor(it) } ?: SnackbarDefaults.actionColor,
            )
        }
    }

    // ========== ExposedDropdownMenu ==========

    /**
     * 下拉选择框
     * Lua 用法：
     *   compose.ExposedDropdownMenuBox {
     *     expanded = expanded.value,
     *     onExpandedChange = function(v) expanded.value = v end,
     *     children = {
     *       compose.TextField({ value = selectedText, readOnly = true, trailingIcon = { ... } }),
     *       compose.ExposedDropdownMenu({
     *         expanded = expanded.value,
     *         onDismissRequest = function() expanded.value = false end,
     *         children = { ... menu items ... },
     *       }),
     *     },
     *   }
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ExposedDropdownMenuBoxLayout(node: ComposeNode) {
        var expanded by remember { mutableStateOf(node.boolProp("expanded", false)) }
        val onExpandedChange = node.callbacks["onExpandedChange"]

        // 同步外部expanded属性
        val externalExpanded = node.boolProp("expanded", false)
        LaunchedEffect(externalExpanded) { if (expanded != externalExpanded) expanded = externalExpanded }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { newExpanded ->
                expanded = newExpanded
                synchronized(ComposeBridgeInstance.current.luaLock) { onExpandedChange?.call(newExpanded) }
            },
            modifier = ComposeRenderer.resolveModifier(node),
        ) {
            ComposeRenderer.RenderChildren(node)
        }
    }

    // ========== PullToRefresh ==========

    /**
     * 下拉刷新容器
     * Lua 用法：
     *   compose.PullToRefreshBox {
     *     isRefreshing = isLoading.value,
     *     onRefresh = function()
     *       -- 执行刷新逻辑
     *       compose.LaunchedEffect({ key = "refresh" }, function()
     *         compose.delay(2000)
     *         isLoading.value = false
     *       end)
     *     end,
     *     children = { compose.LazyColumn { ... } },
     *   }
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PullToRefreshBoxLayout(node: ComposeNode) {
        val isRefreshing = node.boolProp("isRefreshing", false)
        val onRefresh = node.callbacks["onRefresh"]

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { synchronized(ComposeBridgeInstance.current.luaLock) { onRefresh?.call() } },
            modifier = ComposeRenderer.resolveModifier(node),
        ) {
            ComposeRenderer.RenderChildren(node)
        }
    }

    // ========== Pager ==========

    /**
     * 水平翻页组件
     * Lua 用法：
     *   compose.HorizontalPager {
     *     pageCount = 3,
     *     beyondViewportPageCount = 1,
     *     children = function(page)
     *       return compose.Text({ text = "第" .. (page + 1) .. "页" })
     *     end,
     *   }
     */
    @Composable
    private fun HorizontalPagerLayout(node: ComposeNode) {
        val pageCount = node.floatProp("pageCount", 1f).toInt().coerceAtLeast(1)
        val beyondViewportPageCount = node.floatProp("beyondViewportPageCount", 1f).toInt()
        val pagerState = rememberPagerState(pageCount = { pageCount })
        val onPageChanged = node.callbacks["onPageChanged"]
        val childrenFunc = node.childrenFunc
        val children = node.children

        // 页面变化回调
        LaunchedEffect(pagerState.currentPage) {
            synchronized(ComposeBridgeInstance.current.luaLock) { onPageChanged?.call(pagerState.currentPage.toDouble()) }
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = beyondViewportPageCount,
            modifier = ComposeRenderer.resolveModifier(node),
        ) { page ->
            if (childrenFunc != null) {
                // 调用 Lua 函数获取当前页的节点树
                val pageNode = try {
                    synchronized(ComposeBridgeInstance.current.luaLock) {
                        childrenFunc.call(page.toDouble())
                    } as? ComposeNode
                } catch (_: Exception) { null }
                if (pageNode != null) {
                    ComposeRenderer.RenderNode(pageNode)
                }
            } else if (page < children.size) {
                ComposeRenderer.RenderNode(children[page])
            }
        }
    }

    /**
     * 垂直翻页组件
     */
    @Composable
    private fun VerticalPagerLayout(node: ComposeNode) {
        val pageCount = node.floatProp("pageCount", 1f).toInt().coerceAtLeast(1)
        val beyondViewportPageCount = node.floatProp("beyondViewportPageCount", 1f).toInt()
        val pagerState = rememberPagerState(pageCount = { pageCount })
        val onPageChanged = node.callbacks["onPageChanged"]
        val childrenFunc = node.childrenFunc
        val children = node.children

        // 页面变化回调
        LaunchedEffect(pagerState.currentPage) {
            synchronized(ComposeBridgeInstance.current.luaLock) { onPageChanged?.call(pagerState.currentPage.toDouble()) }
        }

        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = beyondViewportPageCount,
            modifier = ComposeRenderer.resolveModifier(node),
        ) { page ->
            if (childrenFunc != null) {
                val pageNode = try {
                    synchronized(ComposeBridgeInstance.current.luaLock) {
                        childrenFunc.call(page.toDouble())
                    } as? ComposeNode
                } catch (_: Exception) { null }
                if (pageNode != null) {
                    ComposeRenderer.RenderNode(pageNode)
                }
            } else if (page < children.size) {
                ComposeRenderer.RenderNode(children[page])
            }
        }
    }

    // ========== AlertDialog ==========

    /**
     * 对话框
     * Lua 用法：
     *   compose.AlertDialog {
     *     visible = showDialog.value,
     *     title = "确认",
     *     text = "是否继续？",
     *     onConfirm = function() ... end,
     *     onDismiss = function() showDialog.value = false end,
     *     confirmText = "确定",
     *     dismissText = "取消",
     *   }
     */
    @Composable
    private fun AlertDialogLayout(node: ComposeNode) {
        val visible = node.boolProp("visible", false)
        val title = node.stringProp("title")
        val text = node.stringProp("text")
        val onConfirm = node.callbacks["onConfirm"]
        val onDismiss = node.callbacks["onDismiss"]
        val confirmText = node.stringProp("confirmText") ?: "确定"
        val dismissText = node.stringProp("dismissText") ?: "取消"

        if (visible) {
            AlertDialog(
                onDismissRequest = { synchronized(ComposeBridgeInstance.current.luaLock) { onDismiss?.call() } },
                title = if (title != null) { { Text(title) } } else null,
                text = if (text != null) { { Text(text) } } else null,
                confirmButton = {
                    TextButton(onClick = {
                        synchronized(ComposeBridgeInstance.current.luaLock) { onConfirm?.call() }
                    }) { Text(confirmText) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        synchronized(ComposeBridgeInstance.current.luaLock) { onDismiss?.call() }
                    }) { Text(dismissText) }
                },
                modifier = ComposeRenderer.resolveModifier(node),
            )
        }
    }

    // ========== NavigationBar ==========

    /**
     * 底部导航栏
     * Lua 用法：
     *   compose.Scaffold {
     *     bottomBar = {
     *       compose.NavigationBar {
     *         children = {
     *           compose.NavigationBarItem({ selected = true, icon = "Home", label = "首页", onClick = function() ... end }),
     *           compose.NavigationBarItem({ selected = false, icon = "Search", label = "搜索", onClick = function() ... end }),
     *         },
     *       }
     *     },
     *   }
     */
    /**
     * 底部导航栏 - NavigationBarItem 需要 RowScope，在此内联渲染子节点
     */
    @Composable
    private fun NavigationBarLayout(node: ComposeNode) {
        NavigationBar(modifier = ComposeRenderer.resolveModifier(node)) {
            // 内联渲染每个子节点，确保 NavigationBarItem 获得 RowScope 上下文
            for (child in node.children) {
                if (child.type == "NavigationBarItem") {
                    renderNavigationBarItem(child)
                } else {
                    ComposeRenderer.RenderNode(child)
                }
            }
        }
    }

    /** 内联渲染 NavigationBarItem，保留 RowScope 上下文 */
    @Composable
    private fun RowScope.renderNavigationBarItem(node: ComposeNode) {
        val selected = node.boolProp("selected", false)
        val onClick = node.callbacks["onClick"]
        val label = node.stringProp("label")
        val iconName = node.stringProp("icon")
        NavigationBarItem(
            selected = selected,
            onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() } },
            icon = {
                if (iconName != null) {
                    Icon(IconComponent.iconMap[iconName] ?: Icons.Filled.Info, contentDescription = label)
                }
            },
            label = if (label != null) { { Text(label) } } else null,
            modifier = ComposeRenderer.resolveModifier(node),
        )
    }

    // ========== SwipeToDismiss ==========

    /**
     * 滑动删除容器
     * Lua 用法：
     *   compose.SwipeToDismissBox {
     *     onDismissedToStart = function() ... end,
     *     onDismissedToEnd = function() ... end,
     *     background = { compose.Text({ text = "删除", color = 0xFFFF0000 }) },
     *     children = {
     *       compose.Card({ children = { compose.Text({ text = "可滑动删除的项" }) } }),
     *     },
     *   }
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SwipeToDismissBoxLayout(node: ComposeNode) {
        val onDismissedToStart = node.callbacks["onDismissedToStart"]
        val onDismissedToEnd = node.callbacks["onDismissedToEnd"]
        val enableDismissFromStartToEnd = node.boolProp("enableDismissFromStartToEnd", true)
        val enableDismissFromEndToStart = node.boolProp("enableDismissFromEndToStart", true)

        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        synchronized(ComposeBridgeInstance.current.luaLock) { onDismissedToEnd?.call() }
                        true
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        synchronized(ComposeBridgeInstance.current.luaLock) { onDismissedToStart?.call() }
                        true
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            },
        )

        SwipeToDismissBox(
            state = dismissState,
            modifier = ComposeRenderer.resolveModifier(node),
            enableDismissFromStartToEnd = enableDismissFromStartToEnd,
            enableDismissFromEndToStart = enableDismissFromEndToStart,
            backgroundContent = {
                // 渲染 background 子节点
                val bgNode = node.children.find { it.props["_slot"] == "background" }
                if (bgNode != null) {
                    ComposeRenderer.RenderNode(bgNode)
                }
            },
        ) {
            // 渲染 content 子节点
            val contentNode = node.children.find { it.props["_slot"] != "background" }
            if (contentNode != null) {
                ComposeRenderer.RenderNode(contentNode)
            } else {
                ComposeRenderer.RenderChildren(node)
            }
        }
    }

    // ========== SegmentedButton ==========

    /**
     * 单选分段按钮容器，内联渲染 SegmentedButton 子节点以保留作用域上下文
     */
    @Composable
    private fun SingleChoiceSegmentedButtonRowLayout(node: ComposeNode) {
        SingleChoiceSegmentedButtonRow(modifier = ComposeRenderer.resolveModifier(node)) {
            for (child in node.children) {
                renderSingleChoiceSegmentedButton(child)
            }
        }
    }

    /**
     * 多选分段按钮容器
     */
    @Composable
    private fun MultiChoiceSegmentedButtonRowLayout(node: ComposeNode) {
        MultiChoiceSegmentedButtonRow(modifier = ComposeRenderer.resolveModifier(node)) {
            for (child in node.children) {
                renderMultiChoiceSegmentedButton(child)
            }
        }
    }

    /** 单选分段按钮项，保留 SingleChoiceSegmentedButtonRowScope 上下文 */
    @Composable
    private fun SingleChoiceSegmentedButtonRowScope.renderSingleChoiceSegmentedButton(node: ComposeNode) {
        val selected = node.boolProp("selected", false)
        val onClick = node.callbacks["onClick"]
        val text = node.stringProp("text") ?: ""
        SegmentedButton(
            selected = selected,
            onClick = { synchronized(ComposeBridgeInstance.current.luaLock) { onClick?.call() } },
            shape = MaterialTheme.shapes.medium,
            modifier = ComposeRenderer.resolveModifier(node),
        ) {
            if (node.children.isNotEmpty()) {
                ComposeRenderer.RenderChildren(node)
            } else {
                Text(text)
            }
        }
    }

    /** 多选分段按钮项，保留 MultiChoiceSegmentedButtonRowScope 上下文 */
    @Composable
    private fun MultiChoiceSegmentedButtonRowScope.renderMultiChoiceSegmentedButton(node: ComposeNode) {
        val checked = node.boolProp("checked", false)
        val onCheckedChange = node.callbacks["onCheckedChange"]
        val text = node.stringProp("text") ?: ""
        SegmentedButton(
            checked = checked,
            onCheckedChange = { newChecked ->
                synchronized(ComposeBridgeInstance.current.luaLock) { onCheckedChange?.call(newChecked) }
            },
            shape = MaterialTheme.shapes.medium,
            modifier = ComposeRenderer.resolveModifier(node),
        ) {
            if (node.children.isNotEmpty()) {
                ComposeRenderer.RenderChildren(node)
            } else {
                Text(text)
            }
        }
    }

    // ========== Popup ==========

    /**
     * 弹出窗口
     * Lua 用法：
     *   compose.Popup {
     *     visible = showPopup.value,
     *     alignment = "TopStart",
     *     offsetX = 0,
     *     offsetY = 100,
     *     onDismissRequest = function() showPopup.value = false end,
     *     children = { compose.Text({ text = "弹窗内容" }) },
     *   }
     */
    @Composable
    private fun PopupLayout(node: ComposeNode) {
        val visible = node.boolProp("visible", false)
        val onDismiss = node.callbacks["onDismissRequest"]
        val offsetX = node.floatProp("offsetX", 0f).toInt()
        val offsetY = node.floatProp("offsetY", 0f).toInt()
        val dismissOnBackPress = node.boolProp("dismissOnBackPress", true)
        val dismissOnClickOutside = node.boolProp("dismissOnClickOutside", true)

        if (visible) {
            Popup(
                alignment = resolvePopupAlignment(node.prop<Any?>("alignment")),
                offset = androidx.compose.ui.unit.IntOffset(offsetX, offsetY),
                onDismissRequest = if (dismissOnBackPress) {
                    { synchronized(ComposeBridgeInstance.current.luaLock) { onDismiss?.call() } }
                } else null,
                properties = PopupProperties(
                    dismissOnBackPress = dismissOnBackPress,
                    dismissOnClickOutside = dismissOnClickOutside,
                ),
            ) {
                ComposeRenderer.RenderChildren(node)
            }
        }
    }

    /** 解析 Popup alignment，支持 Java 对象和字符串回退 */
    private fun resolvePopupAlignment(prop: Any?): androidx.compose.ui.Alignment = when {
        prop is androidx.compose.ui.Alignment -> prop
        prop is String -> when (prop) {
            "TopStart" -> androidx.compose.ui.Alignment.TopStart
            "TopCenter" -> androidx.compose.ui.Alignment.TopCenter
            "TopEnd" -> androidx.compose.ui.Alignment.TopEnd
            "CenterStart" -> androidx.compose.ui.Alignment.CenterStart
            "Center" -> androidx.compose.ui.Alignment.Center
            "CenterEnd" -> androidx.compose.ui.Alignment.CenterEnd
            "BottomStart" -> androidx.compose.ui.Alignment.BottomStart
            "BottomCenter" -> androidx.compose.ui.Alignment.BottomCenter
            "BottomEnd" -> androidx.compose.ui.Alignment.BottomEnd
            else -> androidx.compose.ui.Alignment.TopStart
        }
        else -> androidx.compose.ui.Alignment.TopStart
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