package com.kulipai.luacompose.compose.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionDefaults
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kulipai.luacompose.compose.runtime.ComposeBridge
import com.kulipai.luacompose.compose.script.ScriptFunction
import com.kulipai.luacompose.compose.script.ScriptTable
import com.kulipai.luacompose.compose.script.ScriptValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// 链式 Modifier 封装
// TODO)) 跨平台: 以lua的方式传入给lua层，而不是反射
class LuaModifier(val modifier: Modifier = Modifier) {
    var alignmentStr: String? = null
    var alignObject: Any? = null
    var weightVal: Float? = null
    var weightFill: Boolean = true

    fun copy(newModifier: Modifier = this.modifier): LuaModifier {
        val next = LuaModifier(newModifier)
        next.alignmentStr = this.alignmentStr
        next.alignObject = this.alignObject
        next.weightVal = this.weightVal
        next.weightFill = this.weightFill
        return next
    }


    fun padding(dpOrTable: Any): LuaModifier {
        val unwrapped = ComposeBridge.unwrapAny(dpOrTable)
        if (unwrapped is PaddingValues) {
            return copy(modifier.padding(unwrapped))
        }
        if (unwrapped is Map<*, *>) {
            val all = unwrapped["all"] ?: unwrapped[1.0] ?: unwrapped[1]
            if (all != null) return padding(all)

            val horizontal = unwrapped["horizontal"] ?: unwrapped[1.0] ?: unwrapped[1]
            val vertical = unwrapped["vertical"] ?: unwrapped[2.0] ?: unwrapped[2]
            if (horizontal != null || vertical != null) return padding(horizontal ?: 0, vertical ?: 0)

            val start = unwrapped["start"] ?: unwrapped[1.0] ?: unwrapped[1]
            val top = unwrapped["top"] ?: unwrapped[2.0] ?: unwrapped[2]
            val end = unwrapped["end"] ?: unwrapped[3.0] ?: unwrapped[3]
            val bottom = unwrapped["bottom"] ?: unwrapped[4.0] ?: unwrapped[4]
            if (start != null || top != null || end != null || bottom != null) {
                return padding(start ?: 0, top ?: 0, end ?: 0, bottom ?: 0)
            }
            return this
        }
        return copy(modifier.padding(resolveDp(unwrapped)))
    }

    fun padding(horizontal: Any, vertical: Any): LuaModifier {
        return copy(modifier.padding(resolveDp(horizontal), resolveDp(vertical)))
    }

    fun padding(start: Any, top: Any, end: Any, bottom: Any): LuaModifier {
        val nextMod = modifier.padding(
            resolveDp(start),
            resolveDp(top),
            resolveDp(end),
            resolveDp(bottom)
        ); return copy(nextMod)
    }

    fun fillMaxSize(): LuaModifier {
        return copy(modifier.fillMaxSize())
    }

    fun fillMaxSize(fractionRaw: Any): LuaModifier {
        val unwrapped = ComposeBridge.unwrapAny(fractionRaw)
        var fraction = 1f
        if (unwrapped is Map<*, *>) {
            val f = unwrapped["fraction"] ?: unwrapped[1.0] ?: unwrapped[1]
            if (f is Number) fraction = f.toFloat()
        } else if (unwrapped is Number) {
            fraction = unwrapped.toFloat()
        }
        return copy(modifier.fillMaxSize(fraction))
        
    }

    fun fillMaxWidth(): LuaModifier {
        return copy(modifier.fillMaxWidth())
    }

    fun fillMaxWidth(fractionRaw: Any): LuaModifier {
        val unwrapped = ComposeBridge.unwrapAny(fractionRaw)
        var fraction = 1f
        if (unwrapped is Map<*, *>) {
            val f = unwrapped["fraction"] ?: unwrapped[1.0] ?: unwrapped[1]
            if (f is Number) fraction = f.toFloat()
        } else if (unwrapped is Number) {
            fraction = unwrapped.toFloat()
        }
        return copy(modifier.fillMaxWidth(fraction))
        
    }

    fun fillMaxHeight(): LuaModifier {
        return copy(modifier.fillMaxHeight())
    }

    fun fillMaxHeight(fractionRaw: Any): LuaModifier {
        val unwrapped = ComposeBridge.unwrapAny(fractionRaw)
        var fraction = 1f
        if (unwrapped is Map<*, *>) {
            val f = unwrapped["fraction"] ?: unwrapped[1.0] ?: unwrapped[1]
            if (f is Number) fraction = f.toFloat()
        } else if (unwrapped is Number) {
            fraction = unwrapped.toFloat()
        }
        return copy(modifier.fillMaxHeight(fraction))
        
    }

    fun size(size: Any): LuaModifier {
        val unwrapped = ComposeBridge.unwrapAny(size)
        if (unwrapped is Map<*, *>) {
            val width = unwrapped["width"] ?: unwrapped[1.0] ?: unwrapped[1]
            val height = unwrapped["height"] ?: unwrapped[2.0] ?: unwrapped[2]
            if (width != null || height != null) return size(width ?: 0, height ?: 0)
        }
        return copy(modifier.size(resolveDp(unwrapped)))
        
    }

    fun size(width: Any, height: Any): LuaModifier {
        return copy(modifier.size(resolveDp(width), resolveDp(height)))
    }

    fun width(width: Any): LuaModifier {
        return copy(modifier.width(resolveDp(width)))
    }

    fun height(height: Any): LuaModifier {
        return copy(modifier.height(resolveDp(height)))
    }

    fun wrapContentSize(): LuaModifier {
        return copy(modifier.wrapContentSize())
    }

    fun background(colorProp: Any): LuaModifier {
        var nextModifier = modifier
        val unwrapped = ComposeBridge.unwrapAny(colorProp)
        if (unwrapped is Map<*, *>) {
            val color = unwrapped["color"] ?: unwrapped[1.0] ?: unwrapped[1]
            val shape = unwrapped["shape"] ?: unwrapped[2.0] ?: unwrapped[2]
            if (color != null) {
                if (shape != null) return background(color, shape)
                return background(color)
            }
        }
        try {
            nextModifier = nextModifier.background(resolveColor(unwrapped))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return copy(nextModifier)
    }

    fun background(colorProp: Any, shapeProp: Any): LuaModifier {
        var nextModifier = modifier
        val resolvedShape = resolveShape(shapeProp)
        try {
            if (resolvedShape != null) {
                nextModifier = nextModifier.background(resolveColor(colorProp), resolvedShape)
            } else {
                nextModifier = nextModifier.background(resolveColor(colorProp))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return copy(nextModifier)
    }

    fun alpha(alpha: Float): LuaModifier {
        return copy(modifier.alpha(alpha))
    }

    fun aspectRatio(ratio: Float): LuaModifier {
        return copy(modifier.aspectRatio(ratio))
    }

    fun rotate(degrees: Any): LuaModifier {
        var nextModifier = modifier
        val unwrapped = ComposeBridge.unwrapAny(degrees)
        if (unwrapped is Map<*, *>) {
            val deg = unwrapped["degrees"] ?: unwrapped[1.0] ?: unwrapped[1]
            if (deg is Number) {
                nextModifier = nextModifier.rotate(deg.toFloat())
            }
        } else if (unwrapped is Number) {
            nextModifier = nextModifier.rotate(unwrapped.toFloat())
        }
        return copy(nextModifier)
    }

    fun offset(x: Any, y: Any): LuaModifier {
        return copy(modifier.offset(resolveDp(x), resolveDp(y)))
    }

    fun offset(tableRaw: Any): LuaModifier {
        var nextModifier = modifier
        val tableValue = if (tableRaw is ScriptValue) tableRaw else ComposeBridge.engine.coerceJavaToScript(tableRaw)
        if (tableValue.isFunction()) {
            val table = tableValue.asFunction()
            nextModifier = nextModifier.offset {
                val res = table.call()
                val unwrapped = ComposeBridge.unwrapAny(res)
                
                if (unwrapped is androidx.compose.ui.unit.IntOffset) {
                    unwrapped
                } else {
                    androidx.compose.ui.unit.IntOffset.Zero
                }
            }
            return copy(nextModifier)
        }
        val unwrapped = ComposeBridge.unwrapAny(tableRaw)
        if (unwrapped is Map<*, *>) {
            val x = unwrapped["x"] ?: unwrapped[1.0] ?: unwrapped[1]
            val y = unwrapped["y"] ?: unwrapped[2.0] ?: unwrapped[2]
            if (x != null || y != null) return offset(x ?: 0, y ?: 0)
        }
        return offset(unwrapped ?: 0, 0)
    }

    fun clickable(onClickOrTable: Any): LuaModifier {
        var nextModifier = modifier
        val unwrapped = ComposeBridge.unwrapAny(onClickOrTable)
        var onClickFunc: ScriptFunction? = null
        var enabled = true

        when (unwrapped) {
            is ScriptFunction -> {
                onClickFunc = unwrapped
            }
            is Map<*, *> -> {
                onClickFunc = (unwrapped["onClick"] ?: unwrapped[1.0] ?: unwrapped[1]) as? ScriptFunction
                val enabledVal = unwrapped["enabled"] ?: unwrapped["clickable"]
                if (enabledVal is Boolean) {
                    enabled = enabledVal
                }
            }
        }

        if (onClickFunc != null) {
            nextModifier = nextModifier.clickable(enabled = enabled) {
                try {
                    onClickFunc.call()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return copy(nextModifier)
    }

    fun animateContentSize(): LuaModifier {
        return copy(modifier.animateContentSize())
        
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    fun sharedElement(config: Any): LuaModifier {
        var nextModifier = modifier
        val scope = ComposeBridge.getActiveSharedTransitionScope() 
            ?: ComposeBridge.findContextReceiver<androidx.compose.animation.SharedTransitionScope>() 
            ?: return copy()
        val unwrapped = ComposeBridge.unwrapAny(config)
        val map = unwrapped as? Map<*, *> ?: return copy()
        val visibilityScope = (
            ComposeBridge.unwrapAny(map["animatedVisibilityScope"])
                as? androidx.compose.animation.AnimatedVisibilityScope
            ) ?: (
            ComposeBridge.unwrapAny(map["visibilityScope"])
                as? androidx.compose.animation.AnimatedVisibilityScope
            ) ?: ComposeBridge.getActiveAnimatedVisibilityScope()
            ?: ComposeBridge.findContextReceiver<androidx.compose.animation.AnimatedVisibilityScope>()
            ?: return copy()
        val state = ComposeBridge.unwrapAny(map["sharedContentState"]) as? SharedTransitionScope.SharedContentState
            ?: return copy()

        val boundsTransform = when (val raw = map["boundsTransform"]) {
            is Function2<*, *, *> -> {
                @Suppress("UNCHECKED_CAST")
                raw as BoundsTransform
            }
            is ScriptFunction -> {
                BoundsTransform { initialBounds: Rect, targetBounds: Rect ->
                    val result = raw.call(
                        ComposeBridge.javaToScript(initialBounds),
                        ComposeBridge.javaToScript(targetBounds)
                    )
                    ComposeBridge.scriptToJava(result) as? androidx.compose.animation.core.FiniteAnimationSpec<Rect>
                        ?: androidx.compose.animation.core.spring()
                }
            }
            else -> SharedTransitionDefaults.BoundsTransform
        }

        nextModifier = with(scope) {
            modifier.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = visibilityScope,
                boundsTransform = boundsTransform
            )
        }
        return copy(nextModifier)
    }

    fun pointerInput(arg: Any): LuaModifier {
        var nextModifier = modifier
        val blockValue = if (arg is ScriptValue) arg else ComposeBridge.engine.coerceJavaToScript(arg)
        if (blockValue.isFunction()) {
            return internalPointerInput(emptyArray(), arg)
        }
        val unwrapped = ComposeBridge.unwrapAny(arg)
        if (unwrapped is Map<*, *>) {
            val onTap = unwrapped["onTap"] as? ScriptFunction
            val onDoubleTap = unwrapped["onDoubleTap"] as? ScriptFunction
            val onLongPress = unwrapped["onLongPress"] as? ScriptFunction
            val onDrag = unwrapped["onDrag"] as? ScriptFunction

            if (onTap != null || onDoubleTap != null || onLongPress != null) {
                nextModifier = nextModifier.pointerInput("tapGestures") {
                    detectTapGestures(
                        onTap = onTap?.let { fn ->
                            { offset ->
                                fn.call(
                                    ComposeBridge.engine.createValue(
                                        offset.x.toDouble()
                                    ), ComposeBridge.engine.createValue(offset.y.toDouble())
                                )
                            }
                        },
                        onDoubleTap = onDoubleTap?.let { fn ->
                            { offset ->
                                fn.call(
                                    ComposeBridge.engine.createValue(
                                        offset.x.toDouble()
                                    ), ComposeBridge.engine.createValue(offset.y.toDouble())
                                )
                            }
                        },
                        onLongPress = onLongPress?.let { fn ->
                            { offset ->
                                fn.call(
                                    ComposeBridge.engine.createValue(
                                        offset.x.toDouble()
                                    ), ComposeBridge.engine.createValue(offset.y.toDouble())
                                )
                            }
                        }
                    )
                }
            }
            if (onDrag != null) {
                nextModifier = nextModifier.pointerInput("dragGestures") {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val changeTable = ComposeBridge.engine.createTable()
                        val dragAmountTable = ComposeBridge.engine.createTable()
                        dragAmountTable.set("x", ComposeBridge.engine.createValue(dragAmount.x.toDouble()))
                        dragAmountTable.set("y", ComposeBridge.engine.createValue(dragAmount.y.toDouble()))
                        onDrag.call(changeTable, dragAmountTable)
                    }
                }
            }
        }
        return copy(nextModifier)
    }

    fun clip(shape: String, radius: Int): LuaModifier {
        var nextModifier = modifier
        val clipShape = when (shape.lowercase()) {
            "circle", "circleshape" -> CircleShape
            "rounded", "roundedcornershape" -> RoundedCornerShape(radius.dp)
            "rectangle", "rectangleshape" -> androidx.compose.ui.graphics.RectangleShape
            else -> null
        }
        if (clipShape != null) {
            nextModifier = nextModifier.clip(clipShape)
        }
        return copy(nextModifier)
    }



    fun pointerInput(key1: Any, blockValueRaw: Any): LuaModifier {
        return internalPointerInput(arrayOf(ComposeBridge.unwrapAny(key1)), blockValueRaw)
    }

    fun pointerInput(key1: Any, key2: Any, blockValueRaw: Any): LuaModifier {
        return internalPointerInput(arrayOf(ComposeBridge.unwrapAny(key1), ComposeBridge.unwrapAny(key2)), blockValueRaw)
    }

    fun pointerInput(key1: Any, key2: Any, key3: Any, blockValueRaw: Any): LuaModifier {
        return internalPointerInput(arrayOf(ComposeBridge.unwrapAny(key1), ComposeBridge.unwrapAny(key2), ComposeBridge.unwrapAny(key3)), blockValueRaw)
    }

    fun pointerInput(key1: Any, key2: Any, key3: Any, key4: Any, blockValueRaw: Any): LuaModifier {
        return internalPointerInput(arrayOf(ComposeBridge.unwrapAny(key1), ComposeBridge.unwrapAny(key2), ComposeBridge.unwrapAny(key3), ComposeBridge.unwrapAny(key4)), blockValueRaw)
    }

    fun pointerInput(key1: Any, key2: Any, key3: Any, key4: Any, key5: Any, blockValueRaw: Any): LuaModifier {
        return internalPointerInput(arrayOf(ComposeBridge.unwrapAny(key1), ComposeBridge.unwrapAny(key2), ComposeBridge.unwrapAny(key3), ComposeBridge.unwrapAny(key4), ComposeBridge.unwrapAny(key5)), blockValueRaw)
    }

    fun pointerInput(key1: Any, key2: Any, key3: Any, key4: Any, key5: Any, key6: Any, blockValueRaw: Any): LuaModifier {
        return internalPointerInput(arrayOf(ComposeBridge.unwrapAny(key1), ComposeBridge.unwrapAny(key2), ComposeBridge.unwrapAny(key3), ComposeBridge.unwrapAny(key4), ComposeBridge.unwrapAny(key5), ComposeBridge.unwrapAny(key6)), blockValueRaw)
    }

    private fun internalPointerInput(keys: Array<Any?>, blockValueRaw: Any): LuaModifier {
        val blockValue = if (blockValueRaw is ScriptValue) blockValueRaw else ComposeBridge.engine.coerceJavaToScript(blockValueRaw)
        val block = if (blockValue.isFunction()) blockValue.asFunction() else null
        
        if (block != null) {
            val actions = mutableListOf<suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit>()
            ComposeBridge.pushActivePointerInputScopeActions(actions)
            try {
                block.call()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                ComposeBridge.popActivePointerInputScopeActions()
            }

            val lambda: suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit = {
                kotlinx.coroutines.coroutineScope {
                    for (action in actions) {
                        launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { action() }
                    }
                }
            }
            val nextMod = when (keys.size) {
                0 -> modifier.pointerInput(Unit, lambda)
                1 -> modifier.pointerInput(keys[0], lambda)
                2 -> modifier.pointerInput(keys[0], keys[1], lambda)
                else -> modifier.pointerInput(*keys, block = lambda)
            }
            return copy(nextMod)
        }
        return this
    }

    fun clip(shapeProp: Any): LuaModifier {
        var nextModifier = modifier
        val unwrapped = ComposeBridge.unwrapAny(shapeProp)
        if (unwrapped is Map<*, *>) {
            val shape = unwrapped["shape"] ?: unwrapped[1.0] ?: unwrapped[1]
            if (shape != null) return clip(shape as Any)
        }
        val clipShape = resolveShape(unwrapped)
        if (clipShape != null) {
            nextModifier = nextModifier.clip(clipShape)
        }
        return copy(nextModifier)
    }

    fun border(tableOrWidth: Any): LuaModifier {
        var nextModifier = modifier
        val unwrapped = ComposeBridge.unwrapAny(tableOrWidth)
        if (unwrapped is Map<*, *>) {
            val width = unwrapped["width"] ?: unwrapped[1.0] ?: unwrapped[1]
            val color = unwrapped["color"] ?: unwrapped[2.0] ?: unwrapped[2]
            val shape = unwrapped["shape"] ?: unwrapped[3.0] ?: unwrapped[3]
            if (width != null && color != null) {
                val resolvedShape = resolveShape(shape)
                val w = resolveDp(width)
                val c = resolveColor(color)
                if (resolvedShape != null) {
                    nextModifier = nextModifier.border(w, c, resolvedShape)
                } else {
                    nextModifier = nextModifier.border(w, c)
                }
            }
        }
        return copy(nextModifier)
    }

    fun border(width: Any, color: Any): LuaModifier {
        var nextModifier = modifier
        try {
            nextModifier = nextModifier.border(resolveDp(width), resolveColor(color))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return copy(nextModifier)
    }



    fun align(alignStrOrObj: Any): LuaModifier {
        val next = copy()
        val unwrapped = ComposeBridge.unwrapAny(alignStrOrObj)
        if (unwrapped is String) {
            next.alignmentStr = unwrapped
        } else {
            next.alignObject = unwrapped
        }
        return next
    }

    fun weight(weight: Any): LuaModifier {
        val next = copy()
        val unwrapped = ComposeBridge.unwrapAny(weight)
        if (unwrapped is Map<*, *>) {
            val w = unwrapped["weight"] ?: unwrapped[1.0] ?: unwrapped[1]
            val f = unwrapped["fill"] ?: unwrapped[2.0] ?: unwrapped[2]
            if (w != null) {
                next.weightVal = (w as? Number)?.toFloat() ?: 1f
            }
            if (f != null) {
                next.weightFill = f as? Boolean ?: true
            }
        } else if (unwrapped is Number) {
            next.weightVal = unwrapped.toFloat()
        }
        return next
    }

    fun weight(weight: Any, fill: Any): LuaModifier {
        val next = copy()
        val unwrappedWeight = ComposeBridge.unwrapAny(weight)
        if (unwrappedWeight is Number) {
            next.weightVal = unwrappedWeight.toFloat()
        }
        val unwrappedFill = ComposeBridge.unwrapAny(fill)
        if (unwrappedFill is Boolean) {
            next.weightFill = unwrappedFill
        }
        return next
    }





    fun widthIn(): LuaModifier {
        return copy(modifier.widthIn())
        
    }

    fun widthIn(min: Any, max: Any): LuaModifier {
        return copy(modifier.widthIn(resolveDp(min), resolveDp(max)))
        
    }

    fun widthIn(table: Any): LuaModifier {
        var nextModifier = modifier
        val unwrapped = ComposeBridge.unwrapAny(table)
        if (unwrapped is Map<*, *>) {
            val min = unwrapped["min"] ?: unwrapped[1.0] ?: unwrapped[1]
            val max = unwrapped["max"] ?: unwrapped[2.0] ?: unwrapped[2]
            val minDp = if (min != null) resolveDp(min) else Dp.Unspecified
            val maxDp = if (max != null) resolveDp(max) else Dp.Unspecified
            nextModifier = nextModifier.widthIn(min = minDp, max = maxDp)
        } else {
            nextModifier = nextModifier.widthIn(min = resolveDp(unwrapped))
        }
        return copy(nextModifier)
    }


    fun heightIn(): LuaModifier {
        return copy(modifier.heightIn())
        
    }

    fun heightIn(min: Any, max: Any): LuaModifier {
        return copy(modifier.heightIn(resolveDp(min), resolveDp(max)))
        
    }

    fun heightIn(table: Any): LuaModifier {
        var nextModifier = modifier
        val unwrapped = ComposeBridge.unwrapAny(table)
        if (unwrapped is Map<*, *>) {
            val min = unwrapped["min"] ?: unwrapped[1.0] ?: unwrapped[1]
            val max = unwrapped["max"] ?: unwrapped[2.0] ?: unwrapped[2]
            val minDp = if (min != null) resolveDp(min) else Dp.Unspecified
            val maxDp = if (max != null) resolveDp(max) else Dp.Unspecified
            nextModifier = nextModifier.heightIn(min = minDp, max = maxDp)
        } else {
            nextModifier = nextModifier.heightIn(min = resolveDp(unwrapped))
        }
        return copy(nextModifier)
    }


    fun sizeIn(): LuaModifier {
        return copy(modifier.sizeIn())
        
    }

    fun sizeIn(
        minWidth: Any,
        minHeight: Any,
        maxWidth: Any,
        maxHeight: Any
    ): LuaModifier {
        val nextMod = modifier.sizeIn(
            resolveDp(minWidth),
            resolveDp(minHeight),
            resolveDp(maxWidth),
            resolveDp(maxHeight),
        )
        return copy(nextMod)
    }

    fun sizeIn(table: Any): LuaModifier {
        var nextModifier = modifier
        val unwrapped = ComposeBridge.unwrapAny(table)
        if (unwrapped is Map<*, *>) {
            val minWidth = unwrapped["minWidth"] ?: unwrapped["min"] ?: unwrapped[1.0] ?: unwrapped[1]
            val minHeight = unwrapped["minHeight"] ?: unwrapped["min"] ?: unwrapped[2.0] ?: unwrapped[2]
            val maxWidth = unwrapped["maxWidth"] ?: unwrapped["max"] ?: unwrapped[3.0] ?: unwrapped[3]
            val maxHeight = unwrapped["maxHeight"] ?: unwrapped["max"] ?: unwrapped[4.0] ?: unwrapped[4]

            val minWidthDp = if (minWidth != null) resolveDp(minWidth) else Dp.Unspecified
            val minHeightDp = if (minHeight != null) resolveDp(minHeight) else Dp.Unspecified
            val maxWidthDp = if (maxWidth != null) resolveDp(maxWidth) else Dp.Unspecified
            val maxHeightDp = if (maxHeight != null) resolveDp(maxHeight) else Dp.Unspecified

            nextModifier = nextModifier.sizeIn(
                minWidth = minWidthDp,
                minHeight = minHeightDp,
                maxWidth = maxWidthDp,
                maxHeight = maxHeightDp
            )
        } else {
            nextModifier = nextModifier.sizeIn(minWidth = resolveDp(unwrapped), minHeight = resolveDp(unwrapped))
        }
        return copy(nextModifier)
    }


    // compose.ui.draw
    fun scale(scale: Float): LuaModifier {
        return copy(modifier.scale(scale))
    }

    fun scale(scaleX: Float, scaleY: Float): LuaModifier {
        return copy(modifier.scale(scaleX, scaleY))
    }

    fun drawBehind(blockRaw: Any): LuaModifier {
        var nextModifier = modifier
        val blockValue = if (blockRaw is ScriptValue) blockRaw else ComposeBridge.engine.coerceJavaToScript(blockRaw)
        if (blockValue.isFunction()) {
            val block = blockValue.asFunction()
            nextModifier = nextModifier.drawBehind {
                val scopeTable = ComposeBridge.javaToScript(this)
                try {
                    block.call(scopeTable)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return copy(nextModifier)
    }

    fun drawWithContent(blockRaw: Any): LuaModifier {
        var nextModifier = modifier
        val blockValue = if (blockRaw is ScriptValue) blockRaw else ComposeBridge.engine.coerceJavaToScript(blockRaw)
        if (blockValue.isFunction()) {
            val block = blockValue.asFunction()
            nextModifier = nextModifier.drawWithContent {
                val scopeTable = ComposeBridge.javaToScript(this)
                try {
                    block.call(scopeTable)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return copy(nextModifier)
    }

    fun graphicsLayer(blockRaw: Any): LuaModifier {
        var nextModifier = modifier
        val blockValue = if (blockRaw is ScriptValue) blockRaw else ComposeBridge.engine.coerceJavaToScript(blockRaw)
        if (blockValue.isFunction()) {
            val block = blockValue.asFunction()
            nextModifier = nextModifier.graphicsLayer {
                val scopeTable = ComposeBridge.javaToScript(this)
                try {
                    block.call(scopeTable)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            // Also support passing a table to configure properties without a function
            val unwrapped = ComposeBridge.unwrapAny(blockRaw)
            if (unwrapped is Map<*, *>) {
                nextModifier = nextModifier.graphicsLayer {
                    val scopeTable = ComposeBridge.javaToScript(this)
                    for ((k, v) in unwrapped) {
                        try {
                            // Try to set using the newly added __newindex mechanism
                            val key = k.toString()
                            val javaVal = ComposeBridge.scriptToJava(ComposeBridge.engine.coerceJavaToScript(v))
                            
                            val setterName = "set" + key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                            val setter = this.javaClass.methods.find { it.name == setterName && it.parameterCount == 1 }
                            if (setter != null) {
                                val paramType = setter.parameterTypes[0]
                                val convertedArg = if (javaVal is Number && paramType == Float::class.java) {
                                    javaVal.toFloat()
                                } else {
                                    javaVal
                                }
                                setter.invoke(this, convertedArg)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        return copy(nextModifier)
    }





}
