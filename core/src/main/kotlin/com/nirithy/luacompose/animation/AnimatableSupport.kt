package com.nirithy.luacompose.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import com.nirithy.luacompose.bridge.ComposeBridgeInstance
import com.nirithy.luacompose.graphics.LuaOffset
import com.luajava.LuaObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Animatable 包装器，暴露 animateTo/snapTo 给 Lua
 * 比 animateFloatAsState 更灵活，可在回调中手动触发动画
 * 支持 Float 和 Offset 两种类型动画
 *
 * Lua 用法：
 *   -- Float 动画
 *   local anim = compose.Animatable(0.0)
 *   anim:animateTo(100.0, compose.spring(0.55, 600))
 *   anim:snapTo(0.0)
 *   -- Offset 动画
 *   local anim = compose.Animatable(Offset(0, 0), Offset.VectorConverter)
 *   anim:animateTo { targetValue = Offset(100, 200), animationSpec = compose.spring { dampingRatio = 0.75 } }
 */
class LuaAnimatable(initialValue: Any, vectorConverter: Any? = null) {
    /** 是否为 Offset 类型动画 */
    private val isOffsetType = vectorConverter != null

    /** Float 类型 Animatable（vectorConverter 为 null 时使用） */
    private val floatAnimatable: Animatable<Float, AnimationVector1D>? =
        if (!isOffsetType) Animatable((initialValue as Number).toFloat()) else null

    /** Offset 类型 Animatable（vectorConverter 不为 null 时使用） */
    @Suppress("UNCHECKED_CAST")
    private val offsetAnimatable: Animatable<Offset, AnimationVector2D>? =
        if (isOffsetType) {
            val offset = (initialValue as LuaOffset).toComposeOffset()
            Animatable(offset, vectorConverter as TwoWayConverter<Offset, AnimationVector2D>)
        } else null

    private val targetScope: CoroutineScope? get() = ComposeBridgeInstance.current.mainScope

    /** 获取当前值（Float 类型返回 Float，Offset 类型返回 LuaOffset） */
    fun getValue(): Any? = if (isOffsetType) {
        val v = offsetAnimatable!!.value
        LuaOffset(v.x.toDouble(), v.y.toDouble())
    } else {
        floatAnimatable!!.value
    }

    /** Lua 通过 .value 访问时，luajava 会隐式传入 self 作为第一个参数 */
    @Suppress("UNUSED_PARAMETER")
    fun getValue(ignored: Any?): Any? = getValue()

    // ========== snapTo ==========

    /** 立即跳转到目标值（Float 类型） */
    fun snapTo(target: Float) {
        targetScope?.launch { floatAnimatable?.snapTo(target) }
    }

    /** Lua : 语法兼容：snapTo(self, target) — Float */
    @Suppress("UNUSED_PARAMETER")
    fun snapTo(ignored: Any?, target: Float) {
        targetScope?.launch { floatAnimatable?.snapTo(target) }
    }

    /** 立即跳转到目标值（Offset 类型） */
    fun snapTo(target: LuaOffset) {
        targetScope?.launch { offsetAnimatable?.snapTo(target.toComposeOffset()) }
    }

    /** Lua : 语法兼容：snapTo(self, target) — Offset */
    @Suppress("UNUSED_PARAMETER")
    fun snapTo(ignored: Any?, target: LuaOffset) {
        targetScope?.launch { offsetAnimatable?.snapTo(target.toComposeOffset()) }
    }

    // ========== animateTo（Float 类型：函数调用语法） ==========

    /** 动画过渡到目标值（默认 spring） */
    fun animateTo(target: Float) {
        targetScope?.launch { floatAnimatable?.animateTo(target, spring()) }
    }

    /** Lua : 语法兼容：animateTo(self, target) */
    @Suppress("UNUSED_PARAMETER")
    fun animateTo(ignored: Any?, target: Float) {
        targetScope?.launch { floatAnimatable?.animateTo(target, spring()) }
    }

    /** 动画过渡到目标值，带 durationMs */
    fun animateTo(target: Float, durationMs: Int) {
        targetScope?.launch { floatAnimatable?.animateTo(target, tween(durationMs)) }
    }

    /** Lua : 语法兼容：animateTo(self, target, durationMs) */
    @Suppress("UNUSED_PARAMETER")
    fun animateTo(ignored: Any?, target: Float, durationMs: Int) {
        targetScope?.launch { floatAnimatable?.animateTo(target, tween(durationMs)) }
    }

    /** 动画过渡到目标值，带 Lua spec 表 {type="spring"/"tween", ...} */
    fun animateTo(target: Float, spec: LuaObject) {
        val animSpec = parseSpec(spec)
        targetScope?.launch { floatAnimatable?.animateTo(target, animSpec) }
    }

    /** Lua : 语法兼容：animateTo(self, target, spec) */
    @Suppress("UNUSED_PARAMETER")
    fun animateTo(ignored: Any?, target: Float, spec: LuaObject) {
        val animSpec = parseSpec(spec)
        targetScope?.launch { floatAnimatable?.animateTo(target, animSpec) }
    }

    // ========== animateTo Table 语法（支持 Float 和 Offset） ==========

    /**
     * Table 语法：animateTo { targetValue = ..., animationSpec = ... }
     * targetValue 可以是 Number（Float）或 LuaOffset（Offset）
     * animationSpec 可以是 LuaObject（spring/tween 规格表）或 Java AnimationSpec 对象
     */
    fun animateTo(config: LuaObject) {
        if (isOffsetType) {
            animateToOffsetTable(config)
        } else {
            animateToFloatTable(config)
        }
    }

    /** Lua : 语法兼容：animateTo(self, config) */
    @Suppress("UNUSED_PARAMETER")
    fun animateTo(ignored: Any?, config: LuaObject) {
        animateTo(config)
    }

    /** 是否正在动画中 */
    fun isRunning(): Boolean = if (isOffsetType) {
        offsetAnimatable?.isRunning ?: false
    } else {
        floatAnimatable?.isRunning ?: false
    }

    // ========== 内部辅助方法 ==========

    /** 处理 Float 类型的 table 语法 animateTo */
    private fun animateToFloatTable(config: LuaObject) {
        val targetField = try { config.getField("targetValue") } catch (_: Exception) { null }
        val target = try { targetField?.getNumber()?.toFloat() } catch (_: Exception) { null } ?: return
        val animSpec = extractAnimSpecForFloat(config)
        targetScope?.launch { floatAnimatable?.animateTo(target, animSpec) }
    }

    /** 处理 Offset 类型的 table 语法 animateTo */
    private fun animateToOffsetTable(config: LuaObject) {
        val targetField = try { config.getField("targetValue") } catch (_: Exception) { null }
        val target = try { targetField?.getObject() as? LuaOffset } catch (_: Exception) { null } ?: return
        val animSpec = extractAnimSpecForOffset(config)
        targetScope?.launch { offsetAnimatable?.animateTo(target.toComposeOffset(), animSpec) }
    }

    /** 从 config 表中提取 Float 类型的 AnimationSpec */
    @Suppress("UNCHECKED_CAST")
    private fun extractAnimSpecForFloat(config: LuaObject): AnimationSpec<Float> {
        return try {
            val specField = config.getField("animationSpec")
            // 先尝试作为 Java AnimationSpec 对象获取
            val obj = try { specField.getObject() } catch (_: Exception) { null }
            if (obj is AnimationSpec<*>) {
                obj as AnimationSpec<Float>
            } else {
                // 回退到解析 Lua 表
                parseSpec(specField)
            }
        } catch (_: Exception) {
            spring()
        }
    }

    /** 从 config 表中提取 Offset 类型的 AnimationSpec */
    @Suppress("UNCHECKED_CAST")
    private fun extractAnimSpecForOffset(config: LuaObject): AnimationSpec<Offset> {
        return try {
            val specField = config.getField("animationSpec")
            val obj = try { specField.getObject() } catch (_: Exception) { null }
            if (obj is AnimationSpec<*>) {
                obj as AnimationSpec<Offset>
            } else {
                parseSpecForOffset(specField)
            }
        } catch (_: Exception) {
            spring()
        }
    }

    /** 解析 Lua 表为 Offset 类型的 AnimationSpec */
    @Suppress("UNCHECKED_CAST")
    private fun parseSpecForOffset(luaTable: LuaObject): AnimationSpec<Offset> {
        val type = try { luaTable.getField("type")?.getString() } catch (_: Exception) { "spring" }
        return when (type) {
            "tween" -> {
                val durationMs = try { luaTable.getField("durationMs")?.getNumber()?.toInt() } catch (_: Exception) { 300 } ?: 300
                val easingName = try { luaTable.getField("easing")?.getString() } catch (_: Exception) { "FastOutSlowIn" } ?: "FastOutSlowIn"
                val easing = parseEasing(easingName)
                tween<Offset>(durationMillis = durationMs, easing = easing)
            }
            "spring" -> {
                val dampingRatio = try { luaTable.getField("dampingRatio")?.getNumber()?.toFloat() } catch (_: Exception) { 0.55f } ?: 0.55f
                val stiffness = try { luaTable.getField("stiffness")?.getNumber()?.toFloat() } catch (_: Exception) { 600f } ?: 600f
                spring<Offset>(dampingRatio = dampingRatio, stiffness = stiffness)
            }
            else -> spring<Offset>()
        }
    }

    companion object {
        /** 解析 Lua 表为 Float 类型的 AnimationSpec */
        fun parseSpec(luaTable: LuaObject): AnimationSpec<Float> {
            val type = try { luaTable.getField("type")?.getString() } catch (_: Exception) { "spring" }
            when (type) {
                "tween" -> {
                    val durationMs = try { luaTable.getField("durationMs")?.getNumber()?.toInt() } catch (_: Exception) { 300 } ?: 300
                    val easingName = try { luaTable.getField("easing")?.getString() } catch (_: Exception) { "FastOutSlowIn" } ?: "FastOutSlowIn"
                    val easing: Easing = when (easingName) {
                        "Linear" -> LinearEasing
                        "FastOutSlowIn" -> FastOutSlowInEasing
                        "FastOutLinearIn" -> FastOutLinearInEasing
                        "LinearOutSlowIn" -> LinearOutSlowInEasing
                        else -> FastOutSlowInEasing
                    }
                    return tween(durationMillis = durationMs, easing = easing)
                }
                "spring" -> {
                    val dampingRatio = try { luaTable.getField("dampingRatio")?.getNumber()?.toFloat() } catch (_: Exception) { 0.55f } ?: 0.55f
                    val stiffness = try { luaTable.getField("stiffness")?.getNumber()?.toFloat() } catch (_: Exception) { 600f } ?: 600f
                    return spring(dampingRatio = dampingRatio, stiffness = stiffness)
                }
                else -> return spring()
            }
        }

        /** 解析 easing 名称字符串为 Easing 对象 */
        fun parseEasing(name: String): Easing = when (name) {
            "Linear" -> LinearEasing
            "FastOutSlowIn" -> FastOutSlowInEasing
            "FastOutLinearIn" -> FastOutLinearInEasing
            "LinearOutSlowIn" -> LinearOutSlowInEasing
            else -> FastOutSlowInEasing
        }
    }
}

/** Offset 类型的 TwoWayConverter，用于 Animatable Offset 动画 */
object OffsetVectorConverter : TwoWayConverter<Offset, AnimationVector2D> {
    override val convertToVector: (Offset) -> AnimationVector2D = { offset ->
        AnimationVector2D(offset.x, offset.y)
    }
    override val convertFromVector: (AnimationVector2D) -> Offset = { vector ->
        Offset(vector.v1, vector.v2)
    }
}

/**
 * Easing 缓动函数表
 * 提供常用缓动函数供 Lua 使用
 */
object EasingTable {
    val Linear = LinearEasing
    val FastOutSlowIn = FastOutSlowInEasing
    val FastOutLinearIn = FastOutLinearInEasing
    val LinearOutSlowIn = LinearOutSlowInEasing
    val EaseIn = Easing { fraction -> fraction * fraction }
    val EaseOut = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }
    val EaseInOut = Easing { fraction ->
        if (fraction < 0.5f) 2f * fraction * fraction else 1f - (-2f * fraction + 2f) * (-2f * fraction + 2f) / 2f
    }
    val EaseInCubic = Easing { fraction -> fraction * fraction * fraction }
    val EaseOutCubic = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) * (1f - fraction) }
    val EaseInOutCubic = Easing { fraction ->
        if (fraction < 0.5f) 4f * fraction * fraction * fraction
        else 1f - (-2f * fraction + 2f) * (-2f * fraction + 2f) * (-2f * fraction + 2f) / 2f
    }
}