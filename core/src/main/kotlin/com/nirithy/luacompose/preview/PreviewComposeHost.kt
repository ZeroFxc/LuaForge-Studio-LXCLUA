package com.nirithy.luacompose.preview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.render.ComposeRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 预览 Compose 宿主
 *
 * 在 @Composable 函数中接收 Lua 代码字符串，创建并管理 PreviewSandbox 生命周期，
 * 执行代码并渲染结果节点树。支持预览交互模式下的节点选中。
 *
 * @param luaCode LuaCompose 代码字符串，需要包含 compose.render(function() ... end)
 * @param isPreviewMode 是否启用预览交互模式（节点点击选中、边界记录）
 * @param selectedNodePath 当前选中的节点路径
 * @param onNodeSelected 节点选中回调
 * @param onRootNodeAvailable 根节点可用回调，渲染成功后传递带 nodePath 的节点树，失败时传 null
 * @param onPreviewError 预览错误回调，执行失败时传递错误信息，成功时传 null
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun PreviewComposeHost(
    luaCode: String,
    isPreviewMode: Boolean = false,
    selectedNodePath: String? = null,
    onNodeSelected: ((String?) -> Unit)? = null,
    onRootNodeAvailable: ((ComposeNode?) -> Unit)? = null,
    onPreviewError: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 使用 ApplicationContext 避免 Activity 重建导致沙箱失效
    val appContext = remember { context.applicationContext }
    var sandbox by remember { mutableStateOf<PreviewSandbox?>(null) }
    var rootNode by remember { mutableStateOf<ComposeNode?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val boundsCollector = remember { PreviewNodeBoundsCollector() }

    // 创建或重建沙箱（使用稳定的 ApplicationContext，不随 Activity 变化）
    LaunchedEffect(appContext) {
        val newSandbox = PreviewSandbox(appContext)
        sandbox = newSandbox
    }

    // 代码变化时在后台线程重新执行，避免阻塞主线程和 native 崩溃
    LaunchedEffect(luaCode, sandbox, onRootNodeAvailable, onPreviewError) {
        val sb = sandbox ?: return@LaunchedEffect
        if (luaCode.isBlank()) {
            rootNode = null
            errorMsg = null
            onRootNodeAvailable?.invoke(null)
            onPreviewError?.invoke(null)
            return@LaunchedEffect
        }

        // 在 IO 线程执行 Lua 代码，避免主线程阻塞和超时机制失效
        val success = withContext(Dispatchers.IO) {
            sb.executeCode(luaCode)
        }
        rootNode = sb.getRootNode()
        errorMsg = if (success) null else sb.getError()
        boundsCollector.clear()
        onRootNodeAvailable?.invoke(rootNode)
        onPreviewError?.invoke(errorMsg)
    }

    // 设置预览模式到 bridge
    LaunchedEffect(isPreviewMode, sandbox, onNodeSelected) {
        val bridge = sandbox?.getBridge()
        if (bridge != null) {
            bridge.isPreviewMode = isPreviewMode
            bridge.onNodeClick = if (isPreviewMode && onNodeSelected != null) { path ->
                onNodeSelected(path)
            } else null
            bridge.previewBoundsCollector = if (isPreviewMode) boundsCollector else null
        }
    }

    // 组件销毁时清理沙箱
    DisposableEffect(Unit) {
        onDispose {
            sandbox?.getBridge()?.let { bridge ->
                bridge.isPreviewMode = false
                bridge.onNodeClick = null
                bridge.previewBoundsCollector = null
            }
            sandbox?.destroy()
            sandbox = null
        }
    }

    // 获取当前 bridge 用于渲染
    val bridge = sandbox?.getBridge()

    MaterialTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (bridge != null) {
                // 同步主题和密度到预览 bridge
                PreviewThemeSync(bridge)
                // 驱动动画
                PreviewAnimateValues(bridge)
            }

            val node = rootNode
            val error = errorMsg

            when {
                node != null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 在 bridge 上下文中渲染节点树
                        if (bridge != null) {
                            ComposeBridgeInstance.withBridge(bridge) {
                                ComposeRenderer.Render(node)
                            }
                        } else {
                            ComposeRenderer.Render(node)
                        }

                        // 选中节点高亮叠加层
                        if (isPreviewMode && selectedNodePath != null) {
                            SelectionOverlay(
                                collector = boundsCollector,
                                selectedPath = selectedNodePath,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 错误横幅
                        if (error != null) {
                            PreviewErrorBanner(error)
                        }
                    }
                }
                error != null -> {
                    // 无节点只有错误时，全屏显示错误
                    PreviewErrorFallback(error)
                }
                luaCode.isBlank() -> {
                    PreviewEmptyState("请输入 LuaCompose 代码")
                }
                else -> {
                    PreviewEmptyState("正在加载预览...")
                }
            }
        }
    }
}

/**
 * 选中节点高亮叠加层
 *
 * 根据收集到的节点边界，在选中节点位置绘制蓝色虚线边框。
 */
@Composable
private fun SelectionOverlay(
    collector: PreviewNodeBoundsCollector,
    selectedPath: String,
    modifier: Modifier = Modifier
) {
    val bounds = collector.getBounds(selectedPath)
    val primaryColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    Canvas(modifier = modifier) {
        if (bounds != null && bounds.size.width > 0f && bounds.size.height > 0f) {
            val paddingPx = with(density) { 2.dp.toPx() }
            val cornerRadiusPx = with(density) { 2.dp.toPx() }
            val strokeWidthPx = with(density) { 2.dp.toPx() }
            val dashInterval = with(density) { 8.dp.toPx() }

            val rect = Rect(
                offset = Offset(
                    bounds.left - paddingPx,
                    bounds.top - paddingPx
                ),
                size = Size(
                    bounds.width + paddingPx * 2,
                    bounds.height + paddingPx * 2
                )
            )

            drawRoundRect(
                color = primaryColor,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(
                    width = strokeWidthPx,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(dashInterval, dashInterval), 0f
                    )
                )
            )
        }
    }
}

/**
 * 同步 MaterialTheme 到预览 bridge
 */
@Composable
private fun PreviewThemeSync(bridge: ComposeBridgeInstance) {
    val cs = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val density = LocalDensity.current

    fun colorToArgb(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

    bridge.themeColors.value = mapOf(
        "primary" to colorToArgb(cs.primary), "onPrimary" to colorToArgb(cs.onPrimary),
        "primaryContainer" to colorToArgb(cs.primaryContainer), "onPrimaryContainer" to colorToArgb(cs.onPrimaryContainer),
        "secondary" to colorToArgb(cs.secondary), "onSecondary" to colorToArgb(cs.onSecondary),
        "secondaryContainer" to colorToArgb(cs.secondaryContainer), "onSecondaryContainer" to colorToArgb(cs.onSecondaryContainer),
        "tertiary" to colorToArgb(cs.tertiary), "onTertiary" to colorToArgb(cs.onTertiary),
        "tertiaryContainer" to colorToArgb(cs.tertiaryContainer), "onTertiaryContainer" to colorToArgb(cs.onTertiaryContainer),
        "background" to colorToArgb(cs.background), "onBackground" to colorToArgb(cs.onBackground),
        "surface" to colorToArgb(cs.surface), "onSurface" to colorToArgb(cs.onSurface),
        "surfaceVariant" to colorToArgb(cs.surfaceVariant), "onSurfaceVariant" to colorToArgb(cs.onSurfaceVariant),
        "error" to colorToArgb(cs.error), "onError" to colorToArgb(cs.onError),
        "errorContainer" to colorToArgb(cs.errorContainer), "onErrorContainer" to colorToArgb(cs.onErrorContainer),
        "outline" to colorToArgb(cs.outline), "outlineVariant" to colorToArgb(cs.outlineVariant),
        "inverseSurface" to colorToArgb(cs.inverseSurface), "inverseOnSurface" to colorToArgb(cs.inverseOnSurface),
        "inversePrimary" to colorToArgb(cs.inversePrimary),
    )

    fun textStyleToMap(style: TextStyle): Map<String, Float> = mapOf(
        "fontSize" to style.fontSize.value,
        "fontWeight" to (style.fontWeight?.weight ?: 400).toFloat(),
        "lineHeight" to style.lineHeight.value,
        "letterSpacing" to style.letterSpacing.value,
    )

    bridge.themeTypography.value = mapOf(
        "displayLarge" to textStyleToMap(typography.displayLarge),
        "displayMedium" to textStyleToMap(typography.displayMedium),
        "displaySmall" to textStyleToMap(typography.displaySmall),
        "headlineLarge" to textStyleToMap(typography.headlineLarge),
        "headlineMedium" to textStyleToMap(typography.headlineMedium),
        "headlineSmall" to textStyleToMap(typography.headlineSmall),
        "titleLarge" to textStyleToMap(typography.titleLarge),
        "titleMedium" to textStyleToMap(typography.titleMedium),
        "titleSmall" to textStyleToMap(typography.titleSmall),
        "bodyLarge" to textStyleToMap(typography.bodyLarge),
        "bodyMedium" to textStyleToMap(typography.bodyMedium),
        "bodySmall" to textStyleToMap(typography.bodySmall),
        "labelLarge" to textStyleToMap(typography.labelLarge),
        "labelMedium" to textStyleToMap(typography.labelMedium),
        "labelSmall" to textStyleToMap(typography.labelSmall),
    )

    bridge.density.value = density.density
}

/**
 * 驱动预览 bridge 的动画值
 */
@Composable
private fun PreviewAnimateValues(bridge: ComposeBridgeInstance) {
    // Float 动画
    for (anim in bridge.animatedFloats) {
        val spec = anim.spec ?: spring()
        val animated by animateFloatAsState(
            targetValue = anim.targetValue.value,
            animationSpec = spec,
            label = "PreviewAnimatedFloat"
        )
        if (anim.animatedValue.value != animated) {
            anim.animatedValue.value = animated
        }
    }
    // Dp 动画
    for (anim in bridge.animatedDps) {
        val spec = anim.spec ?: spring()
        val animated by animateDpAsState(
            targetValue = anim.targetValue.value,
            animationSpec = spec,
            label = "PreviewAnimatedDp"
        )
        if (anim.animatedValue.value != animated.value) {
            anim.animatedValue.value = animated.value
        }
    }
    // Color 动画
    for (anim in bridge.animatedColors) {
        val animatedColor by animateColorAsState(
            targetValue = anim.targetValue.value,
            animationSpec = anim.animationSpec,
            label = "PreviewAnimatedColor"
        )
        if (anim.animatedValue.value != animatedColor) {
            anim.animatedValue.value = animatedColor
        }
    }
}

/**
 * 预览空状态提示
 */
@Composable
private fun PreviewEmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

/**
 * 预览错误降级 UI
 */
@Composable
private fun PreviewErrorFallback(error: String) {
    val clipboardManager = LocalClipboardManager.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "预览错误",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Text(
                text = "",
                fontSize = 12.sp
            )
            SelectionContainer {
                Text(
                    text = error,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(error)) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制错误信息",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 预览错误横幅
 */
@Composable
fun PreviewErrorBanner(error: String) {
    val clipboardManager = LocalClipboardManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "预览警告",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            SelectionContainer {
                Text(
                    text = error,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 3
                )
            }
        }
    }
}
