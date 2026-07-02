package com.nirithy.luacompose.bridge

import android.content.Context
import com.nirithy.luacompose.*
import com.nirithy.lxclua.DebugLogger
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.render.ComposeRenderer
import kotlinx.coroutines.flow.collect

private const val TAG = "ComposeHost"

/**
 * Compose 渲染宿主，观察 ComposeBridge.rootState 并渲染 UI
 * 包裹 MaterialTheme 确保 Material3 组件正常渲染
 */
@Composable
fun ComposeHost() {
    val rootNode: ComposeNode? by ComposeBridge.rootState
    val bgColor: Color by ComposeBridge.backgroundColor
    // 订阅 mutableState 变更触发重组，不重建 Lua 树
    val mutableTrigger = ComposeBridge.recomposeTrigger.value

    // 同步 MaterialTheme 到 ComposeBridge，供 Lua 端 compose.Theme 访问
    syncThemeColors()
    syncThemeTypography()
    syncThemeShapes()
    syncDensity()

    // 驱动动画：每个 AnimatedFloat 使用 animateFloatAsState 平滑过渡
    animateValues()

    val node = rootNode
    val error = ComposeBridge.luaError.value
    logD("ComposeHost") { "重组触发: rootNode=${if (node != null) "type=${node.type}" else "null"}, error=${error}, bgColor=${bgColor.toArgb().toString(16)}" }
    logD(TAG) { "[ComposeHost] recomposition 触发, rootNode=${if (node != null) "type=${node.type}" else "null"}, error=${error}" }
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = bgColor
        ) {
            when {
                node != null -> {
                    logD("ComposeHost") { "渲染节点: type=${node.type}, 子节点=${node.children.size}" }
                    logI(TAG) { "[ComposeHost] 开始渲染根节点: type=${node.type}" }
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 渲染节点树
                        ComposeRenderer.Render(node)
                        // 如果有错误，覆盖在顶部显示红色错误条
                        if (error != null) {
                            ErrorBanner(error)
                        }
                    }
                }
                error != null -> {
                    // 无节点树只有错误时，全屏显示错误
                    logD("ComposeHost") { "错误降级: 显示 ErrorFallback ($error)" }
                    ErrorFallback(error)
                }
                else -> {
                    logD("ComposeHost") { "rootNode=null 且无错误，不渲染任何内容！" }
                    logW(TAG) { "[ComposeHost] rootNode 为 null，无错误信息，不渲染任何内容" }
                }
            }
        }
    }
}

/**
 * 错误降级 UI：当 Lua 脚本出错时显示错误信息，而非空白屏幕。
 * 用户可以看到具体错误，方便排查问题。
 * 错误文本可自由选择复制，右上角提供复制按钮。
 */
@Composable
private fun ErrorFallback(error: String) {
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
            // 标题行 + 复制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚠ Lua 渲染错误",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false)
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(error))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制错误信息",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            // 错误详情：可自由选择复制
            SelectionContainer {
                Text(
                    text = error,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        }
    }
}

/**
 * 错误横幅：当有节点树但存在 Lua 错误时，在顶部显示红色错误条
 * 不阻塞用户查看已渲染的内容，仅提示有错误发生
 * 错误文本可自由选择复制，右侧提供复制按钮
 */
@Composable
private fun ErrorBanner(error: String) {
    val clipboardManager = LocalClipboardManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFCDD2),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionContainer {
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = Color(0xFFB71C1C),
                    maxLines = 3,
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(error))
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制错误信息",
                    tint = Color(0xFFB71C1C).copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** 同步 MaterialTheme 颜色值到 ComposeBridge.themeColors（存储 ARGB Long） */
@Composable
private fun syncThemeColors() {
    val cs = MaterialTheme.colorScheme
    fun colorToArgb(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL
    ComposeBridge.themeColors.value = mapOf(
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
}

/** 同步 MaterialTheme 字体排版到 ComposeBridge.themeTypography */
@Composable
private fun syncThemeTypography() {
    val typography = MaterialTheme.typography
    fun textStyleToMap(style: TextStyle): Map<String, Float> = mapOf(
        "fontSize" to style.fontSize.value,
        "fontWeight" to (style.fontWeight?.weight ?: 400).toFloat(),
        "lineHeight" to style.lineHeight.value,
        "letterSpacing" to style.letterSpacing.value,
    )
    ComposeBridge.themeTypography.value = mapOf(
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
}

/** 同步 MaterialTheme 形状到 ComposeBridge.themeShapes */
@Composable
private fun syncThemeShapes() {
    val density = LocalDensity.current
    fun shapeToMap(shape: Shape): Map<String, Float> {
        return if (shape is RoundedCornerShape) {
            fun CornerSize.toDpValue(): Float = with(density) { toPx(Size.Zero, density).toDp().value }
            mapOf(
                "topStart" to shape.topStart.toDpValue(),
                "topEnd" to shape.topEnd.toDpValue(),
                "bottomStart" to shape.bottomStart.toDpValue(),
                "bottomEnd" to shape.bottomEnd.toDpValue(),
            )
        } else {
            mapOf("topStart" to 0f, "topEnd" to 0f, "bottomStart" to 0f, "bottomEnd" to 0f)
        }
    }
    val shapes = MaterialTheme.shapes
    ComposeBridge.themeShapes.value = mapOf(
        "extraSmall" to shapeToMap(shapes.extraSmall),
        "small" to shapeToMap(shapes.small),
        "medium" to shapeToMap(shapes.medium),
        "large" to shapeToMap(shapes.large),
        "extraLarge" to shapeToMap(shapes.extraLarge),
    )
}

/** 同步屏幕密度到 ComposeBridge.density */
@Composable
private fun syncDensity() {
    ComposeBridge.density.value = LocalDensity.current.density
}

/**
 * 每帧动画值变化后通过 snapshotFlow 触发更新：
 * - 普通模式：scheduleRefresh() 重建 Lua 节点树（适合大部分动画场景）
 * - useRecompose 模式：recomposeTrigger++ 轻量重组（适合拖拽等需要保持手势的场景）
 */
@Composable
private fun animateValues() {
    // Float 动画
    for (anim in ComposeBridge.animatedFloats) {
        val spec = anim.spec ?: spring()
        val animated by animateFloatAsState(
            targetValue = anim.targetValue.value,
            animationSpec = spec,
            label = "AnimatedFloat"
        )
        if (anim.animatedValue.value != animated) {
            anim.animatedValue.value = animated
        }
    }
    // Dp 动画（AnimatedDp 直接存储 Float 值）
    for (anim in ComposeBridge.animatedDps) {
        val spec = anim.spec ?: spring()
        val animated by animateDpAsState(
            targetValue = anim.targetValue.value,
            animationSpec = spec,
            label = "AnimatedDp"
        )
        if (anim.animatedValue.value != animated.value) {
            anim.animatedValue.value = animated.value
        }
    }
    // Color 动画
    for (anim in ComposeBridge.animatedColors) {
        val animatedColor by animateColorAsState(
            targetValue = anim.targetValue.value,
            animationSpec = anim.animationSpec,
            label = "AnimatedColor"
        )
        if (anim.animatedValue.value != animatedColor) {
            anim.animatedValue.value = animatedColor
        }
    }

    // 观察 animatedValue 变化，根据模式选择更新方式
    LaunchedEffect(Unit) {
        snapshotFlow {
            ComposeBridge.animatedFloats.map { it.animatedValue.value } +
            ComposeBridge.animatedDps.map { it.animatedValue.value } +
            ComposeBridge.animatedColors.map { it.animatedValue.value.value.toLong() }
        }.collect {
            // 检查是否有 useRecompose 模式的动画需要更新
            val hasRecompose = ComposeBridge.animatedFloats.any { it.useRecompose }
            if (hasRecompose || ComposeBridge.animatedColors.isNotEmpty()) {
                ComposeBridge.recomposeTrigger.value++
            }
            // 检查是否有普通模式的动画需要更新
            val hasNormal = (ComposeBridge.animatedFloats.any { !it.useRecompose } ||
                                 ComposeBridge.animatedDps.isNotEmpty())
            if (hasNormal) {
                ComposeBridge.scheduleRefresh()
            }
        }
    }
}

/**
 * 创建 ComposeView 并绑定 ComposeHost，供 Java 侧调用
 *
 * @param context Android Context
 * @return 已配置的 ComposeView，可直接 setContentView
 */
fun createComposeView(context: Context): ComposeView {
    logD("ComposeHost") { "createComposeView 被调用, context=${context.javaClass.simpleName}" }
    logI(TAG) { "[createComposeView] 创建 ComposeView, context=${context.javaClass.simpleName}" }
    return ComposeView(context).apply {
        setContent { ComposeHost() }
    }
}