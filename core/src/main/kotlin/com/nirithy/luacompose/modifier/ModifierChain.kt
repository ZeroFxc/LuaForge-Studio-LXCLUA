package com.nirithy.luacompose.modifier

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.luajava.LuaObject
import kotlin.math.roundToInt
import com.nirithy.luacompose.bridge.ComposeBridge
import com.nirithy.luacompose.draw.DrawScopeWrapper
import com.nirithy.luacompose.gesture.GestureConfig

/**
 * 链式 Modifier 构造器
 *
 * 每个方法返回自身，支持链式调用。
 * Lua 用法: compose.Modifier().fillMaxSize().padding(16).background(0xFFRRGGBB)
 *
 * 参考 LuaCompose-master 的 LuaModifier 设计，扩展了以下能力：
 * - weight/align：布局权重和对齐，由父容器消费
 * - rotate/scale：图形变换
 * - widthIn/heightIn/sizeIn：尺寸约束
 * - clickable(Runnable)：点击交互
 */
class ModifierChain {
    private var modifier: Modifier = Modifier

    /** 权重属性，由 Row/Column 渲染时消费 */
    var weightProportion: Float = 0f
    /** 对齐属性，由 Box 渲染时消费 */
    var alignment: Any? = null

    /** 手势配置，由 ComposeRenderer 渲染时消费 */
    var gestureConfig: GestureConfig? = null

    /** 是否可滚动，由 ComposeRenderer 在 @Composable 中消费 */
    var scrollable: Boolean = false

    /** drawBehind 绘制回调，由 resolveModifier 消费 */
    var drawBehindCallback: LuaObject? = null

    /** drawWithContent 绘制回调，由 resolveModifier 消费 */
    var drawWithContentCallback: LuaObject? = null

    /** 共享元素键，用于 sharedElement 过渡动画 */
    var sharedElementKey: String? = null
    /** 共享边界键，用于 sharedBounds 过渡动画 */
    var sharedBoundsKey: String? = null
    /** 共享元素自定义 boundsTransform 回调（Lua 函数） */
    var sharedElementBoundsTransform: LuaObject? = null

    fun build(): Modifier = modifier

    // ========== 尺寸 ==========

    fun fillMaxSize(): ModifierChain { modifier = modifier.fillMaxSize(); return this }
    fun fillMaxWidth(): ModifierChain { modifier = modifier.fillMaxWidth(); return this }
    fun fillMaxHeight(): ModifierChain { modifier = modifier.fillMaxHeight(); return this }
    fun wrapContentWidth(): ModifierChain { modifier = modifier.wrapContentWidth(); return this }
    fun wrapContentHeight(): ModifierChain { modifier = modifier.wrapContentHeight(); return this }
    fun wrapContentSize(): ModifierChain { modifier = modifier.wrapContentSize(); return this }

    /** 正方形尺寸（单参数） */
    fun size(square: Float): ModifierChain { modifier = modifier.size(square.dp); return this }
    /** 宽高分别指定（双参数） */
    fun size(widthDp: Float, heightDp: Float): ModifierChain {
        modifier = modifier.size(widthDp.dp, heightDp.dp); return this
    }
    fun width(w: Float): ModifierChain { modifier = modifier.width(w.dp); return this }
    fun height(h: Float): ModifierChain { modifier = modifier.height(h.dp); return this }
    fun aspectRatio(ratio: Float): ModifierChain { modifier = modifier.aspectRatio(ratio); return this }

    /** 按比例填充最大宽度（Lambda 版本），回调返回 0~1 的比例 */
    fun fillMaxWidthLambda(callback: com.luajava.LuaObject): ModifierChain {
        modifier = modifier.fillMaxWidth(
            try {
                synchronized(ComposeBridge.luaLock) { callback.call() }?.let { (it as? Number)?.toFloat() } ?: 1f
            } catch (_: Exception) { 1f }
        )
        return this
    }

    /** 尺寸约束：最小/最大宽高 */
    fun widthIn(min: Float, max: Float): ModifierChain {
        modifier = modifier.widthIn(min.dp, max.dp); return this
    }
    fun heightIn(min: Float, max: Float): ModifierChain {
        modifier = modifier.heightIn(min.dp, max.dp); return this
    }
    fun sizeIn(minWidth: Float, minHeight: Float, maxWidth: Float, maxHeight: Float): ModifierChain {
        modifier = modifier.sizeIn(minWidth.dp, minHeight.dp, maxWidth.dp, maxHeight.dp); return this
    }

    // ========== 偏移与变换 ==========

    fun offset(x: Float, y: Float): ModifierChain {
        modifier = modifier.offset(x.dp, y.dp); return this
    }

    /** 像素级偏移（px 单位），用于拖拽等需要像素精度的场景，与 dragAmount 单位一致 */
    fun offsetPx(x: Float, y: Float): ModifierChain {
        modifier = modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
        return this
    }
    /**
     * 动态偏移（Lambda 版本），Lua 回调应返回 {x=..., y=...} 表
     * 读取 recomposeTrigger 确保 Compose 在 mutableState 变更时重新计算偏移，
     * 否则 offset { } lambda 在 layout 阶段不会被重新执行。
     */
    fun offsetLambda(callback: LuaObject): ModifierChain {
        modifier = modifier.offset {
            // ★ 读取 recomposeTrigger 让 Compose 追踪此 lambda 的依赖，
            //   mutableState 变更 → recomposeTrigger++ → 此 lambda 重新执行
            @Suppress("UNUSED_EXPRESSION")
            ComposeBridge.recomposeTrigger.value
            try {
                val result = synchronized(ComposeBridge.luaLock) { callback.call() }
                // Lua 返回的表是 LuaObject，不是 Map，需要用 getField 取值
                val px: Float
                val py: Float
                if (result is LuaObject) {
                    val xNum = try { result.getField("x")?.getNumber()?.toFloat() } catch (_: Exception) { null }
                    val yNum = try { result.getField("y")?.getNumber()?.toFloat() } catch (_: Exception) { null }
                    px = xNum ?: 0f
                    py = yNum ?: 0f
                } else if (result is Map<*, *>) {
                    px = ((result["x"] as? Number)?.toFloat() ?: 0f)
                    py = ((result["y"] as? Number)?.toFloat() ?: 0f)
                } else {
                    px = 0f; py = 0f
                }
                androidx.compose.ui.unit.IntOffset(px.toInt(), py.toInt())
            } catch (e: Exception) {
                androidx.compose.ui.unit.IntOffset.Zero
            }
        }
        return this
    }
    /** 旋转（度数） */
    fun rotate(degrees: Float): ModifierChain {
        modifier = modifier.rotate(degrees); return this
    }
    /**
     * 动态旋转（Lambda 版本），Lua 回调返回角度值
     * 读取 recomposeTrigger 确保在 mutableState 变更时重新计算旋转角度
     */
    fun rotateLambda(callback: LuaObject): ModifierChain {
        modifier = modifier.graphicsLayer {
            @Suppress("UNUSED_EXPRESSION")
            ComposeBridge.recomposeTrigger.value
            try {
                val degrees = (synchronized(ComposeBridge.luaLock) { callback.call() } as? Number)?.toFloat() ?: 0f
                this.rotationZ = degrees
            } catch (_: Exception) {}
        }
        return this
    }
    /** 缩放（scaleX, scaleY） */
    fun scale(scaleX: Float, scaleY: Float): ModifierChain {
        modifier = modifier.scale(scaleX, scaleY); return this
    }

    /** graphicsLayer 高级变换：支持缩放、透明度、旋转、阴影等 */
    fun graphicsLayer(scaleX: Float, scaleY: Float, alpha: Float, rotationZ: Float): ModifierChain {
        modifier = modifier.graphicsLayer {
            this.scaleX = scaleX
            this.scaleY = scaleY
            this.alpha = alpha
            this.rotationZ = rotationZ
        }
        return this
    }

    /** 3D 旋转 Y 轴（卡片翻转），cameraDistance 控制透视强度 */
    fun graphicsLayerRotationY(rotationY: Float, cameraDistance: Float): ModifierChain {
        modifier = modifier.graphicsLayer {
            this.rotationY = rotationY
            this.cameraDistance = cameraDistance
        }
        return this
    }

    /**
     * 动态 graphicsLayer（Lambda 版本），Lua 回调返回 {translationX, translationY, scaleX, scaleY, alpha, rotationZ}
     * 读取 recomposeTrigger 确保 Compose 在 mutableState 变更时重新计算变换
     */
    fun graphicsLayerLambda(callback: LuaObject): ModifierChain {
        modifier = modifier.graphicsLayer {
            @Suppress("UNUSED_EXPRESSION")
            ComposeBridge.recomposeTrigger.value
            try {
                val result = synchronized(ComposeBridge.luaLock) { callback.call() }
                (result as? Map<*, *>)?.let {
                    this.translationX = ((it["translationX"] as? Number)?.toFloat() ?: 0f)
                    this.translationY = ((it["translationY"] as? Number)?.toFloat() ?: 0f)
                    this.scaleX = ((it["scaleX"] as? Number)?.toFloat() ?: 1f)
                    this.scaleY = ((it["scaleY"] as? Number)?.toFloat() ?: 1f)
                    this.alpha = ((it["alpha"] as? Number)?.toFloat() ?: 1f)
                    this.rotationZ = ((it["rotationZ"] as? Number)?.toFloat() ?: 0f)
                }
            } catch (_: Exception) {}
        }
        return this
    }

    // ========== 动画 ==========

    /** 内容尺寸变化时自动添加动画过渡 */
    fun animateContentSize(): ModifierChain {
        modifier = modifier.animateContentSize(); return this
    }

    // ========== 内边距 ==========

    /** 四边统一内边距 */
    fun padding(all: Float): ModifierChain {
        modifier = modifier.padding(all.dp); return this
    }
    /** 水平+垂直内边距（padding(horizontal, vertical)） */
    fun padding(horizontal: Float, vertical: Float): ModifierChain {
        modifier = modifier.padding(horizontal = horizontal.dp, vertical = vertical.dp); return this
    }
    /** 四边独立内边距 */
    fun padding(start: Float, top: Float, end: Float, bottom: Float): ModifierChain {
        modifier = modifier.padding(start = start.dp, top = top.dp, end = end.dp, bottom = bottom.dp); return this
    }
    /** 水平+垂直内边距（别名，兼容旧代码） */
    fun paddingHv(horizontal: Float, vertical: Float): ModifierChain {
        modifier = modifier.padding(horizontal = horizontal.dp, vertical = vertical.dp); return this
    }
    /** 四边独立内边距（别名，兼容旧代码） */
    fun paddingLtrb(start: Float, top: Float, end: Float, bottom: Float): ModifierChain {
        return padding(start, top, end, bottom)
    }
    fun paddingHorizontal(horizontal: Float): ModifierChain {
        modifier = modifier.padding(horizontal = horizontal.dp); return this
    }
    fun paddingVertical(vertical: Float): ModifierChain {
        modifier = modifier.padding(vertical = vertical.dp); return this
    }
    fun paddingStart(start: Float): ModifierChain {
        modifier = modifier.padding(start = start.dp); return this
    }
    fun paddingEnd(end: Float): ModifierChain {
        modifier = modifier.padding(end = end.dp); return this
    }
    fun paddingTop(top: Float): ModifierChain {
        modifier = modifier.padding(top = top.dp); return this
    }
    fun paddingBottom(bottom: Float): ModifierChain {
        modifier = modifier.padding(bottom = bottom.dp); return this
    }

    // ========== 外观 ==========

    /** 背景色，使用 Color(Int) 按 sRGB ARGB 解释 */
    fun background(colorInt: Int): ModifierChain {
        modifier = modifier.background(Color(colorInt)); return this
    }
    /** 背景色 + 圆角 */
    fun backgroundRounded(colorInt: Int, radius: Float): ModifierChain {
        modifier = modifier.background(Color(colorInt), RoundedCornerShape(radius.dp)); return this
    }
    fun alpha(value: Float): ModifierChain {
        modifier = modifier.alpha(value); return this
    }
    fun borderRadius(radius: Float): ModifierChain {
        modifier = modifier.clip(RoundedCornerShape(radius.dp)); return this
    }
    fun circle(): ModifierChain {
        modifier = modifier.clip(CircleShape); return this
    }
    /** 边框 */
    fun border(width: Float, colorInt: Int): ModifierChain {
        modifier = modifier.border(width.dp, Color(colorInt)); return this
    }
    /** 边框 + 圆角 */
    fun borderRounded(width: Float, colorInt: Int, radius: Float): ModifierChain {
        modifier = modifier.border(width.dp, Color(colorInt), RoundedCornerShape(radius.dp)); return this
    }
    fun shadow(elevation: Float): ModifierChain {
        modifier = modifier.shadow(elevation.dp); return this
    }
    fun shadowRounded(elevation: Float, radius: Float): ModifierChain {
        modifier = modifier.shadow(elevation.dp, RoundedCornerShape(radius.dp)); return this
    }

    // ========== 布局 ==========

    /** 权重（Row/Column 子项），调用后由布局组件渲染时消费 */
    fun weight(proportion: Float): ModifierChain {
        this.weightProportion = proportion; return this
    }
    /** 对齐（Box 子项），调用后由 Box 渲染时消费 */
    fun align(alignment: String?): ModifierChain {
        if (alignment != null) this.alignment = resolveAlignment(alignment)
        return this
    }

    // ========== 手势交互 ==========

    /** 点击手势 onTap(callback) — callback 接收 (x, y) 坐标 */
    fun onTap(callback: LuaObject): ModifierChain {
        ensureGestureConfig().onTap = callback; return this
    }
    /** 双击手势 */
    fun onDoubleTap(callback: LuaObject): ModifierChain {
        ensureGestureConfig().onDoubleTap = callback; return this
    }
    /** 长按手势 */
    fun onLongPress(callback: LuaObject): ModifierChain {
        ensureGestureConfig().onLongPress = callback; return this
    }
    /** 拖拽手势 onDrag(callback) — callback 接收 (dx, dy) */
    fun onDrag(callback: LuaObject): ModifierChain {
        ensureGestureConfig().onDrag = callback; return this
    }

    private fun ensureGestureConfig(): GestureConfig {
        if (gestureConfig == null) gestureConfig = GestureConfig()
        return gestureConfig!!
    }

    // ========== 滚动 ==========

    /** 标记为可垂直滚动，由 ComposeRenderer 在 @Composable 中消费 */
    fun verticalScroll(): ModifierChain {
        scrollable = true; return this
    }

    // ========== 绘制 ==========

    /** 在元素后面绘制（背景层） */
    fun drawBehind(callback: LuaObject): ModifierChain {
        drawBehindCallback = callback; return this
    }

    /** 在元素内容上绘制（前景层） */
    fun drawWithContent(callback: LuaObject): ModifierChain {
        drawWithContentCallback = callback; return this
    }

    /** 元素尺寸变化时回调，传入 width, height（像素） */
    fun onSizeChanged(callback: LuaObject): ModifierChain {
        modifier = modifier.onSizeChanged { size ->
            try { synchronized(ComposeBridge.luaLock) { callback.call(size.width.toDouble(), size.height.toDouble()) } }
            catch (_: Exception) {}
        }
        return this
    }

    // ========== 裁剪 ==========

    /** 裁剪为圆角矩形（传入半径 dp） */
    fun clip(radius: Float): ModifierChain {
        modifier = modifier.clip(RoundedCornerShape(radius.dp)); return this
    }
    /** 裁剪为 Shape 形状（传入 RoundedCornerShape/CircleShape/LuaShape 对象） */
    fun clip(shape: Any): ModifierChain {
        val composeShape: androidx.compose.ui.graphics.Shape? = when (shape) {
            is androidx.compose.ui.graphics.Shape -> shape
            is com.nirithy.luacompose.graphics.LuaShape -> shape.toComposeShape()
            else -> null
        }
        if (composeShape != null) {
            modifier = modifier.clip(composeShape)
        }
        return this
    }
    /** 裁剪为圆形 */
    fun clipCircle(): ModifierChain {
        modifier = modifier.clip(CircleShape); return this
    }

    /** 设置合成策略，传 "Offscreen" 启用离屏渲染（用于 BlendMode 混合） */
    fun compositingStrategy(strategy: String): ModifierChain {
        if (strategy == "Offscreen") {
            modifier = modifier.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        }
        return this
    }

    // ========== 手势 ==========

    /** 绑定指针输入手势（detectDragGestures 等），由 applyGestures 在 Composable 中消费 */
    fun pointerInput(onDrag: LuaObject): ModifierChain {
        ensureGestureConfig().onDrag = onDrag; return this
    }
    /** 多参数绑定（Lua: pointerInput(key1, key2, callback)），key1/key2 用于稳定 key，忽略 */
    fun pointerInput(key1: Float, key2: Float, onDrag: LuaObject): ModifierChain {
        ensureGestureConfig().onDrag = onDrag; return this
    }
    /** 完整手势绑定（onDragStart, onDrag, onDragEnd） */
    fun pointerInputFull(onDragStart: LuaObject, onDrag: LuaObject, onDragEnd: LuaObject): ModifierChain {
        val cfg = ensureGestureConfig()
        cfg.onDragStart = onDragStart
        cfg.onDrag = onDrag
        cfg.onDragEnd = onDragEnd
        return this
    }

    // ========== 交互 ==========

    /** 点击（无回调，仅视觉反馈） */
    fun clickable(): ModifierChain {
        modifier = modifier.clickable { }; return this
    }
    /** 点击（带回调 Runnable） */
    fun clickableRunnable(runnable: Runnable): ModifierChain {
        modifier = modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null,
            onClick = { runnable.run() }
        )
        return this
    }
    /** 点击（带 Lua 回调），避免 luajava 接口代理问题 */
    fun clickableLua(callback: LuaObject): ModifierChain {
        modifier = modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null,
            onClick = {
                try { synchronized(ComposeBridge.luaLock) { callback.call() } } catch (_: Exception) {}
            }
        )
        return this
    }

    // ========== 共享元素过渡 ==========

    /** 共享元素过渡 key，由 ComposeRenderer 在 SharedTransitionScope 中消费 */
    fun sharedElement(key: String): ModifierChain {
        sharedElementKey = key; return this
    }

    /** 共享边界过渡 key，由 ComposeRenderer 在 SharedTransitionScope 中消费 */
    fun sharedBounds(key: String): ModifierChain {
        sharedBoundsKey = key; return this
    }

    companion object {
        fun create(): ModifierChain = ModifierChain()

        /** 对齐字符串解析，返回 Alignment 或 Alignment.Horizontal/Vertical */
        fun resolveAlignment(name: String): Any = when (name) {
            "Center" -> Alignment.Center
            "TopStart" -> Alignment.TopStart
            "TopCenter" -> Alignment.TopCenter
            "TopEnd" -> Alignment.TopEnd
            "CenterStart" -> Alignment.CenterStart
            "CenterEnd" -> Alignment.CenterEnd
            "BottomStart" -> Alignment.BottomStart
            "BottomCenter" -> Alignment.BottomCenter
            "BottomEnd" -> Alignment.BottomEnd
            "Start" -> Alignment.Start
            "End" -> Alignment.End
            "Top" -> Alignment.Top
            "Bottom" -> Alignment.Bottom
            "CenterHorizontally" -> Alignment.CenterHorizontally
            "CenterVertically" -> Alignment.CenterVertically
            else -> Alignment.TopStart
        }
    }
}