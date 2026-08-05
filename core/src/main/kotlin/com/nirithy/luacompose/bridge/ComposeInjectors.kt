package com.nirithy.luacompose.bridge

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nirithy.luacompose.*
import com.nirithy.luacompose.animation.AnimationSpecs
import com.nirithy.luacompose.animation.EasingTable
import com.nirithy.luacompose.animation.LuaAnimatable
import com.nirithy.luacompose.coroutine.LuaCoroutineScope
import com.nirithy.luacompose.draw.LuaPath
import com.nirithy.luacompose.graphics.LuaBrush
import com.nirithy.luacompose.graphics.LuaColor
import com.nirithy.luacompose.graphics.LuaOffset
import com.nirithy.luacompose.graphics.LuaRect
import com.nirithy.luacompose.graphics.LuaSize
import com.nirithy.luacompose.modifier.ModifierChain
import com.nirithy.luacompose.reflect.LazyObjectWrapper
import com.nirithy.luacompose.state.StateWrapper
import com.luajava.JavaFunction
import com.luajava.LuaObject
import com.luajava.LuaState
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay as coroutineDelay
import kotlinx.coroutines.launch

/**
 * ComposeBridge 的 Lua API 注入器
 * 将所有 register* 工厂函数从 ComposeBridge.kt 中提取出来，保持单一职责。
 * 每个函数都是 ComposeBridgeInstance 的扩展函数，通过 internal 可见性访问 bridge 实例的状态字段。
 */
private const val TAG = "ComposeBridge"

// ========== 核心状态工厂 ==========

/**
 * compose.Modifier() — 创建 ModifierChain 实例
 * compose.Modifier.fillMaxWidth() — 创建并返回已应用 fillMaxWidth 的 ModifierChain
 * 支持两种语法：
 *   1. Modifier() 创建空修饰符链
 *   2. Modifier.fillMaxWidth() 创建并应用修饰符（类似 Kotlin 扩展函数语法）
 */
internal fun ComposeBridgeInstance.registerModifierFactory(L: LuaState) {
    // 创建 Modifier 表
    L.newTable()

    // 创建 __index 元表（包含所有修饰符方法）
    L.newTable()
    val metaIdx = L.getTop()

    // 辅助函数：为每个修饰符方法创建 __index 条目
    fun addModifierMethod(name: String, apply: (ModifierChain) -> Unit) {
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val chain = ModifierChain.create()
                apply(chain)
                L.pushJavaObject(chain)
                return 1
            }
        })
        L.setField(metaIdx, name)
    }

    // 无参数方法
    addModifierMethod("fillMaxSize") { it.fillMaxSize() }
    addModifierMethod("fillMaxWidth") { it.fillMaxWidth() }
    addModifierMethod("fillMaxHeight") { it.fillMaxHeight() }
    addModifierMethod("wrapContentWidth") { it.wrapContentWidth() }
    addModifierMethod("wrapContentHeight") { it.wrapContentHeight() }
    addModifierMethod("wrapContentSize") { it.wrapContentSize() }
    addModifierMethod("animateContentSize") { it.animateContentSize() }
    addModifierMethod("circle") { it.circle() }
    addModifierMethod("clipCircle") { it.clipCircle() }
    addModifierMethod("verticalScroll") { it.verticalScroll() }
    addModifierMethod("clickable") { it.clickable() }

    // 单参数方法（从 Lua 栈读取参数）
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val arg = L.toNumber(2).toFloat()
            chain.padding(arg)
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "padding")

    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val arg = L.toNumber(2).toFloat()
            chain.height(arg)
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "height")

    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val arg = L.toNumber(2).toFloat()
            chain.width(arg)
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "width")

    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val arg = L.toNumber(2).toFloat()
            chain.size(arg)
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "size")

    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val arg = L.toNumber(2).toFloat()
            chain.alpha(arg)
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "alpha")

    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val arg = L.toNumber(2).toFloat()
            chain.borderRadius(arg)
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "borderRadius")

    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val arg = L.toNumber(2).toFloat()
            chain.shadow(arg)
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "shadow")

    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val arg = L.toNumber(2).toFloat()
            chain.rotate(arg)
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "rotate")

    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val arg = L.toNumber(2).toFloat()
            chain.aspectRatio(arg)
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "aspectRatio")

    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val arg = L.toNumber(2).toFloat()
            chain.weight(arg)
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "weight")

    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            val arg = L.toNumber(2).toFloat()
            chain.clip(arg)
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "clip")

    // 支持 Modifier() 语法（__call 元方法）
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val chain = ModifierChain.create()
            L.pushJavaObject(chain)
            return 1
        }
    })
    L.setField(metaIdx, "__call")

    // 设置元表
    L.setMetaTable(-2)

    logV(TAG) { "[Modifier] 注册完成，支持 Modifier() 和 Modifier.xxx() 语法" }
    L.setField(-2, "Modifier")
}

/** compose.state(value) — 创建响应式状态，变更时触发全量刷新 */
internal fun ComposeBridgeInstance.registerStateFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2) {
                logW(TAG) { "[state] 未提供初始值，返回 nil" }
                L.pushNil(); return 1
            }
            val obj = L.toJavaObject(2)
            // 类型归一化：与旧代码保持一致，Number 统一为 Float
            val normalized = when (obj) {
                is Boolean -> obj
                is Number -> obj.toFloat()
                is String -> obj
                else -> obj
            }
            logD(TAG) { "[state] 创建响应式状态, 初始值: $normalized (${normalized?.javaClass?.simpleName})" }
            // ★ 通过 ComposeScope 管理状态生命周期
            val scope = currentScope
            val wrapper = scope.getOrCreateState(normalized) { scheduleRefresh() }
            L.pushJavaObject(wrapper); return 1
        }
    })
    L.setField(-2, "state")
}

/** compose.mutableState(value) — 创建可变状态，变更时触发全量刷新重建节点树 */
internal fun ComposeBridgeInstance.registerMutableState(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2) { L.pushNil(); return 1 }
            val obj = L.toJavaObject(2)
            // 类型归一化：与旧代码保持一致，Number 统一为 Float
            val normalized = when (obj) {
                is Boolean -> obj
                is Number -> obj.toFloat()
                is String -> obj
                else -> obj
            }
            // ★ 通过 ComposeScope 管理状态生命周期
            val scope = currentScope
            val wrapper = scope.getOrCreateState(normalized) { scheduleRefresh() }
            L.pushJavaObject(wrapper); return 1
        }
    })
    L.setField(-2, "mutableState")
}

/** compose.remember(initFn) — 按调用顺序缓存计算结果 */
internal fun ComposeBridgeInstance.registerRememberFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2 || !L.isFunction(2)) {
                logW(TAG) { "[remember] 需要函数参数" }
                L.pushNil(); return 1
            }
            // ★ 通过 ComposeScope 管理 remember 缓存
            val scope = currentScope
            val result = scope.getOrCreateRemember(initFn = {
                L.pushValue(2)
                val ok = L.pcall(0, 1, 0)
                val r = if (ok == 0) L.toJavaObject(-1) else null
                L.pop(if (ok == 0) 1 else 0)
                r
            })
            L.pushJavaObject(result); return 1
        }
    })
    L.setField(-2, "remember")
}

/** compose.derivedStateOf(computeFn) — 派生状态，依赖的 state 变化时自动重新计算 */
internal fun ComposeBridgeInstance.registerDerivedStateFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2 || !L.isFunction(2)) {
                logW(TAG) { "[derivedStateOf] 需要函数参数" }
                L.pushNil(); return 1
            }
            val computeFunc = L.getLuaObject(2)
            // ★ 通过 ComposeScope 管理派生状态生命周期
            val scope = currentScope
            val wrapper = scope.getOrCreateDerivedState {
                try {
                    computeFunc.call()
                } catch (e: Exception) {
                    logW(TAG) { "[derivedStateOf] 计算失败: ${e.message}" }
                    null
                }
            }
            L.pushJavaObject(wrapper); return 1
        }
    })
    L.setField(-2, "derivedStateOf")
}

// ========== 动画规格工厂（spring / tween） ==========

/** compose.spring(dampingRatio, stiffness) 或 compose.spring { dampingRatio = ..., stiffness = ... } — 创建弹簧动画规格 */
internal fun ComposeBridgeInstance.registerSpringFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top >= 2 && L.isTable(2)) {
                // Table 语法: spring { dampingRatio = ..., stiffness = ... }
                L.getField(2, "dampingRatio")
                val dampingRatio = if (L.isNumber(-1)) L.toNumber(-1).toFloat() else 0.55f
                L.pop(1)
                L.getField(2, "stiffness")
                val stiffness = if (L.isNumber(-1)) L.toNumber(-1).toFloat() else 600f
                L.pop(1)
                L.pushJavaObject(androidx.compose.animation.core.spring<Float>(dampingRatio = dampingRatio, stiffness = stiffness))
                return 1
            }
            // 函数调用语法: spring(dampingRatio, stiffness)
            val dampingRatio = if (top >= 2) L.toNumber(2).toFloat() else 0.55f
            val stiffness = if (top >= 3) L.toNumber(3).toFloat() else 600f
            L.newTable()
            L.pushString("spring"); L.setField(-2, "type")
            L.pushNumber(dampingRatio.toDouble()); L.setField(-2, "dampingRatio")
            L.pushNumber(stiffness.toDouble()); L.setField(-2, "stiffness")
            return 1
        }
    })
    L.setField(-2, "spring")
}

/** compose.tween(durationMs, easing) 或 compose.tween { durationMillis = ..., easing = ... } — 创建 tween 动画规格 */
internal fun ComposeBridgeInstance.registerTweenFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top >= 2 && L.isTable(2)) {
                // Table 语法: tween { durationMillis = ..., easing = ... }
                L.getField(2, "durationMillis")
                val durationMs = if (L.isNumber(-1)) L.toNumber(-1).toInt() else 300
                L.pop(1)
                L.getField(2, "easing")
                val easing: androidx.compose.animation.core.Easing = if (L.isString(-1)) {
                    when (L.toString(-1)) {
                        "Linear" -> androidx.compose.animation.core.LinearEasing
                        "FastOutSlowIn" -> androidx.compose.animation.core.FastOutSlowInEasing
                        "FastOutLinearIn" -> androidx.compose.animation.core.FastOutLinearInEasing
                        "LinearOutSlowIn" -> androidx.compose.animation.core.LinearOutSlowInEasing
                        else -> androidx.compose.animation.core.FastOutSlowInEasing
                    }
                } else {
                    try { L.toJavaObject(-1) as? androidx.compose.animation.core.Easing } catch (e: Exception) { null }
                        ?: androidx.compose.animation.core.FastOutSlowInEasing
                }
                L.pop(1)
                L.pushJavaObject(androidx.compose.animation.core.tween<Float>(durationMillis = durationMs, easing = easing))
                return 1
            }
            // 函数调用语法: tween(durationMs, easing)
            val durationMs = if (top >= 2) L.toNumber(2).toInt() else 300
            val easing = if (top >= 3) L.toString(3) else "FastOutSlowIn"
            L.newTable()
            L.pushString("tween"); L.setField(-2, "type")
            L.pushNumber(durationMs.toDouble()); L.setField(-2, "durationMs")
            L.pushString(easing); L.setField(-2, "easing")
            return 1
        }
    })
    L.setField(-2, "tween")
}

// ========== 动画状态工厂 ==========

/** compose.animateFloatAsState(target) — 动画浮点状态，支持表参数 {targetValue=..., animationSpec=...} */
internal fun ComposeBridgeInstance.registerAnimateFloatFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            var target: Float = 0f
            var spec: AnimationSpec<Float>? = null
            var useRecompose = false
            if (top >= 2 && L.isTable(2)) {
                // 表参数: {targetValue=..., animationSpec=..., useRecompose=...}
                L.getField(2, "targetValue")
                target = if (L.isNumber(-1)) L.toNumber(-1).toFloat() else 0f
                L.pop(1)
                L.getField(2, "animationSpec")
                if (L.isTable(-1)) spec = AnimatedFloat.parseSpec(L.getLuaObject(-1))
                L.pop(1)
                L.getField(2, "useRecompose")
                useRecompose = if (L.isBoolean(-1)) L.toBoolean(-1) else false
                L.pop(1)
            } else {
                target = if (top >= 2) L.toNumber(2).toFloat() else 0f
            }
            val idx = animIndex++
            if (idx < animatedFloats.size) {
                // ★ 修复：缓存复用时必须更新 target，否则 Lua 状态变化后动画目标值不更新
                val anim = animatedFloats[idx]
                anim.targetValue.value = target
                anim.spec = spec
                L.pushJavaObject(anim); return 1
            }
            val anim = AnimatedFloat(target, useRecompose = useRecompose, spec = spec)
            animatedFloats.add(anim)
            L.pushJavaObject(anim); return 1
        }
    })
    L.setField(-2, "animateFloatAsState")
}

/** compose.animateFloatAsStateRecompose(target) — 轻量重组模式动画，仅触发 recomposeTrigger */
internal fun ComposeBridgeInstance.registerAnimateFloatRecomposeFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val target = if (top >= 2) L.toNumber(2).toFloat() else 0f
            val idx = animIndex++
            if (idx < animatedFloats.size) {
                val anim = animatedFloats[idx]
                anim.targetValue.value = target
                L.pushJavaObject(anim); return 1
            }
            val anim = AnimatedFloat(target, useRecompose = true)
            animatedFloats.add(anim)
            L.pushJavaObject(anim); return 1
        }
    })
    L.setField(-2, "animateFloatAsStateRecompose")
}

/** compose.animateFloatAsStateTween(target, durationMs, easingName) — tween 动画 */
internal fun ComposeBridgeInstance.registerAnimateFloatTweenFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val target = if (top >= 2) L.toNumber(2).toFloat() else 0f
            val durationMs = if (top >= 3) L.toNumber(3).toInt() else 300
            val easingName = if (top >= 4) L.toString(4) else "FastOutSlowIn"
            val idx = animIndex++
            if (idx < animatedFloats.size) {
                val anim = animatedFloats[idx]
                anim.targetValue.value = target
                anim.spec = AnimatedFloat.parseSpec(
                    mapOf("type" to "tween", "durationMs" to durationMs, "easing" to easingName)
                )
                L.pushJavaObject(anim); return 1
            }
            val anim = AnimatedFloat(target, spec = AnimatedFloat.parseSpec(
                mapOf("type" to "tween", "durationMs" to durationMs, "easing" to easingName)
            ))
            animatedFloats.add(anim)
            L.pushJavaObject(anim); return 1
        }
    })
    L.setField(-2, "animateFloatAsStateTween")
}

/** compose.animateFloatAsStateRecomposeTween(target, durationMs, easingName) — 轻量重组 + tween */
internal fun ComposeBridgeInstance.registerAnimateFloatRecomposeTweenFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val target = if (top >= 2) L.toNumber(2).toFloat() else 0f
            val durationMs = if (top >= 3) L.toNumber(3).toInt() else 300
            val easingName = if (top >= 4) L.toString(4) else "FastOutSlowIn"
            val idx = animIndex++
            if (idx < animatedFloats.size) {
                val anim = animatedFloats[idx]
                anim.targetValue.value = target
                anim.spec = AnimatedFloat.parseSpec(
                    mapOf("type" to "tween", "durationMs" to durationMs, "easing" to easingName)
                )
                L.pushJavaObject(anim); return 1
            }
            val anim = AnimatedFloat(target, useRecompose = true, spec = AnimatedFloat.parseSpec(
                mapOf("type" to "tween", "durationMs" to durationMs, "easing" to easingName)
            ))
            animatedFloats.add(anim)
            L.pushJavaObject(anim); return 1
        }
    })
    L.setField(-2, "animateFloatAsStateRecomposeTween")
}

/** compose.animateColorAsState(targetColor) — 颜色动画状态 */
internal fun ComposeBridgeInstance.registerAnimateColorFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            // 支持两种调用方式：
            // 1. compose.animateColorAsState(0xFF000000) — 直接传颜色值
            // 2. compose.animateColorAsState({targetValue = 0xFF000000, animationSpec = ...}) — 传 table
            val targetColor: Long
            var animSpec: AnimationSpec<Color>? = null
            if (top >= 2 && L.isTable(2)) {
                // table 模式：读取 targetValue 和 animationSpec
                L.getField(2, "targetValue")
                targetColor = if (L.isNumber(-1)) L.toNumber(-1).toLong() else 0xFF000000L
                L.pop(1)
                L.getField(2, "animationSpec")
                val obj = L.toJavaObject(-1)
                if (obj is AnimationSpec<*>) {
                    @Suppress("UNCHECKED_CAST")
                    animSpec = obj as AnimationSpec<Color>
                }
                L.pop(1)
            } else {
                targetColor = if (top >= 2) L.toNumber(2).toLong() else 0xFF000000L
            }
            val idx = animColorIndex++
            if (idx < animatedColors.size) {
                val anim = animatedColors[idx]
                anim.targetValue.value = Color(targetColor)
                if (animSpec != null) {
                    anim.animationSpec = animSpec
                }
                L.pushJavaObject(anim); return 1
            }
            val anim = AnimatedColor(targetColor)
            if (animSpec != null) {
                anim.animationSpec = animSpec
            }
            animatedColors.add(anim)
            L.pushJavaObject(anim); return 1
        }
    })
    L.setField(-2, "animateColorAsState")
}

/** compose.animateDpAsState(targetDp) — Dp 动画状态 */
internal fun ComposeBridgeInstance.registerAnimateDpFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val targetDp = if (top >= 2) L.toNumber(2).toFloat() else 0f
            val idx = animDpIndex++
            if (idx < animatedDps.size) {
                val anim = animatedDps[idx]
                anim.targetValue.value = targetDp.dp
                L.pushJavaObject(anim); return 1
            }
            val anim = AnimatedDp(targetDp)
            animatedDps.add(anim)
            L.pushJavaObject(anim); return 1
        }
    })
    L.setField(-2, "animateDpAsState")
}

// ========== 单位与颜色 ==========

/** compose.dp(value) — dp 单位转换（当前为数字透传） */
internal fun ComposeBridgeInstance.registerDpHelper(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val value = if (top < 2) 0.0 else L.toNumber(2)
            L.pushNumber(value); return 1
        }
    })
    L.setField(-2, "dp")
}

/** compose.color(argb/r,g,b,a) — 创建 ARGB 颜色值 */
internal fun ComposeBridgeInstance.registerColorHelper(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val color = when {
                top >= 4 -> {
                    val r = L.toNumber(2).toInt(); val g = L.toNumber(3).toInt()
                    val b = L.toNumber(4).toInt(); val a = if (top >= 5) L.toNumber(5).toInt() else 255
                    (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                }
                top >= 2 -> {
                    val raw = L.toNumber(2).toLong()
                    if (raw and 0xFF000000L == 0L) raw or 0xFF000000L else raw
                }
                else -> 0xFF000000L
            }
            L.pushNumber(color.toDouble()); return 1
        }
    })
    L.setField(-2, "color")
}

/** compose.now() — 返回当前毫秒时间戳，用于计时等场景 */
internal fun ComposeBridgeInstance.registerTimeHelper(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            L.pushNumber(System.currentTimeMillis().toDouble())
            return 1
        }
    })
    L.setField(-2, "now")
}

/** compose.backgroundColor(argb) — 设置根 Surface 背景色 */
internal fun ComposeBridgeInstance.registerBackgroundColor(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top >= 2) {
                val raw = L.toNumber(2).toLong()
                val argb = if (raw and 0xFF000000L == 0L) raw or 0xFF000000L else raw
                backgroundColor.value = Color(argb.toInt())
            }
            return 0
        }
    })
    L.setField(-2, "backgroundColor")
}

// ========== 主题 ==========

/** compose.Theme — 由 ComposeHost 在 recomposition 时同步颜色值 */
internal fun ComposeBridgeInstance.registerThemeTable(L: LuaState) {
    L.newTable()
    val themeIdx = L.getTop()
    L.newTable()

    val handler = object : JavaFunction(L) {
        override fun execute(): Int {
            val key = L.toString(3)
            when (key) {
                "typography" -> { createThemeSubTable(L, themeTypography); return 1 }
                "shapes" -> { createThemeSubTable(L, themeShapes); return 1 }
            }
            val argb = themeColors.value[key]
            if (argb != null) {
                L.pushNumber(argb.toDouble())
            } else {
                L.pushNumber(0.0)
            }
            return 1
        }
    }
    LazyNamespace.pushLuaIndexWrapper(L, handler)
    L.setField(-2, "__index")
    L.setMetaTable(themeIdx)
    L.setField(-2, "Theme")
}

/** 创建 Theme 子表（typography 或 shapes），带 __index 元表 */
internal fun ComposeBridgeInstance.createThemeSubTable(
    L: LuaState,
    dataSource: MutableState<Map<String, Map<String, Float>>>
) {
    L.newTable()
    val subIdx = L.getTop()
    L.newTable()

    val subHandler = object : JavaFunction(L) {
        override fun execute(): Int {
            val key = L.toString(3)
            val styleData = dataSource.value[key]
            if (styleData != null) {
                L.newTable()
                for (entry in styleData.entries) {
                    L.pushNumber(entry.value.toDouble())
                    L.setField(-2, entry.key)
                }
            } else {
                L.pushNil()
            }
            return 1
        }
    }
    LazyNamespace.pushLuaIndexWrapper(L, subHandler)
    L.setField(-2, "__index")
    L.setMetaTable(subIdx)
}

/** compose.LocalDensity — 屏幕密度信息 */
internal fun ComposeBridgeInstance.registerLocalDensity(L: LuaState) {
    L.newTable()
    val densityIdx = L.getTop()
    L.newTable()
    val handler = object : JavaFunction(L) {
        override fun execute(): Int {
            val key = L.toString(3)
            when (key) {
                "density" -> L.pushNumber(density.value.toDouble())
                "fontScale" -> L.pushNumber(1.0)
                else -> L.pushNil()
            }
            return 1
        }
    }
    LazyNamespace.pushLuaIndexWrapper(L, handler)
    L.setField(-2, "__index")
    L.setMetaTable(densityIdx)
    L.setField(-2, "LocalDensity")
}

/** compose.MaterialTheme.typography — Material3 主题排版系统，支持 labelSmall 等字体样式访问 */
internal fun ComposeBridgeInstance.registerMaterialThemeTypography(L: LuaState) {
    // MaterialTheme 外部表
    L.newTable()
    val mtIdx = L.getTop()

    // typography 子表
    L.newTable()
    val typoIdx = L.getTop()

    // typography 的 __index 元表，按 key 返回对应的字体样式属性表
    L.newTable()
    val handler = object : JavaFunction(L) {
        override fun execute(): Int {
            val key = L.toString(3)
            // 返回一个包含 fontSize / fontWeight / lineHeight 等属性的表
            L.newTable()
            when (key) {
                "labelSmall" -> {
                    L.pushNumber(11.0); L.setField(-2, "fontSize")
                    L.pushInteger(500L); L.setField(-2, "fontWeight")
                    L.pushNumber(16.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.5); L.setField(-2, "letterSpacing")
                }
                "labelMedium" -> {
                    L.pushNumber(12.0); L.setField(-2, "fontSize")
                    L.pushInteger(500L); L.setField(-2, "fontWeight")
                    L.pushNumber(16.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.5); L.setField(-2, "letterSpacing")
                }
                "labelLarge" -> {
                    L.pushNumber(14.0); L.setField(-2, "fontSize")
                    L.pushInteger(500L); L.setField(-2, "fontWeight")
                    L.pushNumber(20.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.1); L.setField(-2, "letterSpacing")
                }
                "bodySmall" -> {
                    L.pushNumber(12.0); L.setField(-2, "fontSize")
                    L.pushInteger(400L); L.setField(-2, "fontWeight")
                    L.pushNumber(16.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.4); L.setField(-2, "letterSpacing")
                }
                "bodyMedium" -> {
                    L.pushNumber(14.0); L.setField(-2, "fontSize")
                    L.pushInteger(400L); L.setField(-2, "fontWeight")
                    L.pushNumber(20.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.25); L.setField(-2, "letterSpacing")
                }
                "bodyLarge" -> {
                    L.pushNumber(16.0); L.setField(-2, "fontSize")
                    L.pushInteger(400L); L.setField(-2, "fontWeight")
                    L.pushNumber(24.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.5); L.setField(-2, "letterSpacing")
                }
                "titleSmall" -> {
                    L.pushNumber(14.0); L.setField(-2, "fontSize")
                    L.pushInteger(500L); L.setField(-2, "fontWeight")
                    L.pushNumber(20.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.1); L.setField(-2, "letterSpacing")
                }
                "titleMedium" -> {
                    L.pushNumber(16.0); L.setField(-2, "fontSize")
                    L.pushInteger(500L); L.setField(-2, "fontWeight")
                    L.pushNumber(24.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.15); L.setField(-2, "letterSpacing")
                }
                "titleLarge" -> {
                    L.pushNumber(22.0); L.setField(-2, "fontSize")
                    L.pushInteger(400L); L.setField(-2, "fontWeight")
                    L.pushNumber(28.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.0); L.setField(-2, "letterSpacing")
                }
                "headlineSmall" -> {
                    L.pushNumber(24.0); L.setField(-2, "fontSize")
                    L.pushInteger(400L); L.setField(-2, "fontWeight")
                    L.pushNumber(32.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.0); L.setField(-2, "letterSpacing")
                }
                "headlineMedium" -> {
                    L.pushNumber(28.0); L.setField(-2, "fontSize")
                    L.pushInteger(400L); L.setField(-2, "fontWeight")
                    L.pushNumber(36.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.0); L.setField(-2, "letterSpacing")
                }
                "headlineLarge" -> {
                    L.pushNumber(32.0); L.setField(-2, "fontSize")
                    L.pushInteger(400L); L.setField(-2, "fontWeight")
                    L.pushNumber(40.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.0); L.setField(-2, "letterSpacing")
                }
                "displaySmall" -> {
                    L.pushNumber(36.0); L.setField(-2, "fontSize")
                    L.pushInteger(400L); L.setField(-2, "fontWeight")
                    L.pushNumber(44.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.0); L.setField(-2, "letterSpacing")
                }
                "displayMedium" -> {
                    L.pushNumber(45.0); L.setField(-2, "fontSize")
                    L.pushInteger(400L); L.setField(-2, "fontWeight")
                    L.pushNumber(52.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.0); L.setField(-2, "letterSpacing")
                }
                "displayLarge" -> {
                    L.pushNumber(57.0); L.setField(-2, "fontSize")
                    L.pushInteger(400L); L.setField(-2, "fontWeight")
                    L.pushNumber(64.0); L.setField(-2, "lineHeight")
                    L.pushNumber(-0.25); L.setField(-2, "letterSpacing")
                }
                else -> {
                    // 默认返回 bodyMedium 样式
                    L.pushNumber(14.0); L.setField(-2, "fontSize")
                    L.pushInteger(400L); L.setField(-2, "fontWeight")
                    L.pushNumber(20.0); L.setField(-2, "lineHeight")
                    L.pushNumber(0.25); L.setField(-2, "letterSpacing")
                }
            }
            return 1
        }
    }
    LazyNamespace.pushLuaIndexWrapper(L, handler)
    L.setField(-2, "__index")
    L.setMetaTable(typoIdx)

    // typography 表设置到 MaterialTheme 中
    L.setField(-2, "typography")

    // MaterialTheme 的 __index 元表（支持未来扩展其他属性如 colorScheme）
    L.newTable()
    val mtHandler = object : JavaFunction(L) {
        override fun execute(): Int {
            val key = L.toString(3)
            when (key) {
                "typography" -> {
                    // 返回 typography 子表
                    L.getField(2, "typography")
                    return 1
                }
                else -> {
                    L.pushNil()
                    return 1
                }
            }
        }
    }
    LazyNamespace.pushLuaIndexWrapper(L, mtHandler)
    L.setField(-2, "__index")
    L.setMetaTable(mtIdx)

    L.setField(-2, "MaterialTheme")
}

// ========== 枚举表 ==========

/** compose.FontWeight — 字重枚举表 */
internal fun ComposeBridgeInstance.registerFontWeightTable(L: LuaState) {
    L.newTable()
    val weights = mapOf(
        "Thin" to 100, "ExtraLight" to 200, "Light" to 300,
        "Normal" to 400, "Medium" to 500, "SemiBold" to 600,
        "Bold" to 700, "ExtraBold" to 800, "Black" to 900,
        "W100" to 100, "W200" to 200, "W300" to 300,
        "W400" to 400, "W500" to 500, "W600" to 600,
        "W700" to 700, "W800" to 800, "W900" to 900,
    )
    for ((name, value) in weights) {
        L.pushInteger(value.toLong()); L.setField(-2, name)
    }
    L.setField(-2, "FontWeight")
}

/** compose.Arrangement — 布局排列枚举表 */
internal fun ComposeBridgeInstance.registerArrangementTable(L: LuaState) {
    L.newTable()
    val items = listOf("Start", "Center", "End", "Top", "Bottom",
        "SpaceAround", "SpaceBetween", "SpaceEvenly")
    for (item in items) {
        L.pushString(item); L.setField(-2, item)
    }
    L.setField(-2, "Arrangement")
}

/** compose.Alignment — 对齐方式枚举表 */
internal fun ComposeBridgeInstance.registerAlignmentTable(L: LuaState) {
    L.newTable()
    val items = listOf("TopStart", "TopCenter", "TopEnd",
        "CenterStart", "Center", "CenterEnd",
        "BottomStart", "BottomCenter", "BottomEnd",
        "CenterHorizontally", "CenterVertically",
        "Start", "End", "Top", "Bottom")
    for (item in items) {
        L.pushString(item); L.setField(-2, item)
    }
    L.setField(-2, "Alignment")
}

// ========== 动画规格 ==========

/** 注册动画规格工厂：tween, spring, repeatable, infiniteRepeatable */
internal fun ComposeBridgeInstance.registerAnimationSpecs(L: LuaState) {
    // tween(durationMs, delayMs)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val duration = if (top >= 2) L.toNumber(2).toInt() else 300
            val delay = if (top >= 3) L.toNumber(3).toInt() else 0
            L.pushJavaObject(AnimationSpecs.createTween(duration, delay)); return 1
        }
    })
    L.setField(-2, "tween")

    // spring(dampingRatio, stiffness)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val damping = if (top >= 2) L.toNumber(2).toFloat() else 0.5f
            val stiffness = if (top >= 3) L.toNumber(3).toFloat() else 400f
            L.pushJavaObject(AnimationSpecs.createSpring(damping, stiffness)); return 1
        }
    })
    L.setField(-2, "spring")

    // repeatable(iterations, animation, repeatMode)
    L.pushJavaFunction(object : JavaFunction(L) {
        @Suppress("UNCHECKED_CAST")
        override fun execute(): Int {
            val top = L.getTop()
            val iterations = if (top >= 2) L.toNumber(2).toInt() else 1
            val spec = if (top >= 3) L.toJavaObject(3) as? DurationBasedAnimationSpec<Float>
                else tween<Float>(300) as DurationBasedAnimationSpec<Float>
            val mode = if (top >= 4) L.toJavaObject(4) as? RepeatMode else RepeatMode.Restart
            L.pushJavaObject(AnimationSpecs.createRepeatable(
                iterations, spec ?: (tween<Float>(300) as DurationBasedAnimationSpec<Float>),
                mode ?: RepeatMode.Restart
            ))
            return 1
        }
    })
    L.setField(-2, "repeatable")

    // infiniteRepeatable(animation, repeatMode)
    L.pushJavaFunction(object : JavaFunction(L) {
        @Suppress("UNCHECKED_CAST")
        override fun execute(): Int {
            val top = L.getTop()
            val spec = if (top >= 2) L.toJavaObject(2) as? DurationBasedAnimationSpec<Float>
                else tween<Float>(1000) as DurationBasedAnimationSpec<Float>
            val mode = if (top >= 3) L.toJavaObject(3) as? RepeatMode else RepeatMode.Restart
            L.pushJavaObject(AnimationSpecs.createInfiniteRepeatable(
                spec ?: (tween<Float>(1000) as DurationBasedAnimationSpec<Float>),
                mode ?: RepeatMode.Restart
            ))
            return 1
        }
    })
    L.setField(-2, "infiniteRepeatable")
}

/** 注册进出场动画工厂：fadeIn, fadeOut, slideIn, slideOut, scaleIn, scaleOut 等 */
internal fun ComposeBridgeInstance.registerAnimationTransitions(L: LuaState) {
    // 淡入淡出
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.fadeInEnter()); return 1 }
    })
    L.setField(-2, "fadeIn")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.fadeInEnter()); return 1 }
    })
    L.setField(-2, "fadeInEnter")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.fadeOutExit()); return 1 }
    })
    L.setField(-2, "fadeOut")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.fadeOutExit()); return 1 }
    })
    L.setField(-2, "fadeOutExit")

    // 水平滑入滑出
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.slideInHorizontallyEnter()); return 1 }
    })
    L.setField(-2, "slideInHorizontally")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.slideInHorizontallyEnter()); return 1 }
    })
    L.setField(-2, "slideInHorizontallyEnter")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.slideOutHorizontallyExit()); return 1 }
    })
    L.setField(-2, "slideOutHorizontally")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.slideOutHorizontallyExit()); return 1 }
    })
    L.setField(-2, "slideOutHorizontallyExit")

    // 垂直滑入滑出
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.slideInVerticallyEnter()); return 1 }
    })
    L.setField(-2, "slideInVertically")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.slideOutVerticallyExit()); return 1 }
    })
    L.setField(-2, "slideOutVertically")

    // 缩放
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.scaleInEnter()); return 1 }
    })
    L.setField(-2, "scaleIn")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.scaleInEnter()); return 1 }
    })
    L.setField(-2, "scaleInEnter")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.scaleOutExit()); return 1 }
    })
    L.setField(-2, "scaleOut")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.scaleOutExit()); return 1 }
    })
    L.setField(-2, "scaleOutExit")

    // 展开/收缩
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.expandVerticallyEnter()); return 1 }
    })
    L.setField(-2, "expandVertically")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.shrinkVerticallyExit()); return 1 }
    })
    L.setField(-2, "shrinkVertically")

    // 水平展开/收缩
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.expandHorizontallyEnter()); return 1 }
    })
    L.setField(-2, "expandHorizontally")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.shrinkHorizontallyExit()); return 1 }
    })
    L.setField(-2, "shrinkHorizontally")

    // 组合动画快捷方式
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.fadeInExpandEnter()); return 1 }
    })
    L.setField(-2, "fadeInExpand")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.fadeOutShrinkExit()); return 1 }
    })
    L.setField(-2, "fadeOutShrink")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.fadeInSlideEnter()); return 1 }
    })
    L.setField(-2, "fadeInSlide")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.fadeOutSlideExit()); return 1 }
    })
    L.setField(-2, "fadeOutSlide")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.fadeInScaleEnter()); return 1 }
    })
    L.setField(-2, "fadeInScale")
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int { L.pushJavaObject(AnimationSpecs.fadeOutScaleExit()); return 1 }
    })
    L.setField(-2, "fadeOutScale")
}

/** compose.Spring — Spring 动画常量表 */
internal fun ComposeBridgeInstance.registerSpringConstants(L: LuaState) {
    L.newTable()
    L.pushNumber(Spring.DampingRatioHighBouncy.toDouble()); L.setField(-2, "DampingRatioHighBouncy")
    L.pushNumber(Spring.DampingRatioMediumBouncy.toDouble()); L.setField(-2, "DampingRatioMediumBouncy")
    L.pushNumber(Spring.DampingRatioLowBouncy.toDouble()); L.setField(-2, "DampingRatioLowBouncy")
    L.pushNumber(Spring.DampingRatioNoBouncy.toDouble()); L.setField(-2, "DampingRatioNoBouncy")
    L.pushNumber(Spring.StiffnessHigh.toDouble()); L.setField(-2, "StiffnessHigh")
    L.pushNumber(Spring.StiffnessMedium.toDouble()); L.setField(-2, "StiffnessMedium")
    L.pushNumber(Spring.StiffnessMediumLow.toDouble()); L.setField(-2, "StiffnessMediumLow")
    L.pushNumber(Spring.StiffnessLow.toDouble()); L.setField(-2, "StiffnessLow")
    L.pushNumber(Spring.StiffnessVeryLow.toDouble()); L.setField(-2, "StiffnessVeryLow")
    L.setField(-2, "Spring")
}

// ========== 图形首类对象 ==========

/** 注册图形首类对象工厂：Color, Offset, Size, Rect, IntOffset */
internal fun ComposeBridgeInstance.registerGraphicsFactories(L: LuaState) {
    // compose.Color(argb)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val argb = if (top >= 2) L.toNumber(2).toLong() else 0xFF000000L
            val color = if (argb and 0xFF000000L == 0L) argb or 0xFF000000L else argb
            L.pushJavaObject(LuaColor(color)); return 1
        }
    })
    L.setField(-2, "Color")

    // compose.Offset(x, y) — 先注册工厂函数，再增强为带 __call 的表
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val x = if (top >= 2) L.toNumber(2) else 0.0
            val y = if (top >= 3) L.toNumber(3) else 0.0
            L.pushJavaObject(LuaOffset(x, y)); return 1
        }
    })
    // 增强 Offset: 创建带 __call 元方法的表，同时添加 Zero 和 VectorConverter 静态字段
    val offsetFactoryIdx = L.getTop()  // 保存工厂函数在栈上的位置
    L.newTable()
    val offsetTableIdx = L.getTop()
    // 添加 Zero 静态值
    L.pushJavaObject(LuaOffset(0.0, 0.0)); L.setField(-2, "Zero")
    // 添加 VectorConverter（TwoWayConverter<Offset, AnimationVector2D> 实例）
    L.pushJavaObject(com.nirithy.luacompose.animation.OffsetVectorConverter); L.setField(-2, "VectorConverter")
    // 设置 __call 元方法
    L.newTable()
    L.pushValue(offsetFactoryIdx); L.setField(-2, "__call")
    L.setMetaTable(offsetTableIdx)
    L.pop(1)  // 弹出工厂函数
    L.setField(-2, "Offset")

    // compose.IntOffset(x, y) — 整数偏移量工厂
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val x = if (top >= 2) L.toNumber(2).toInt() else 0
            val y = if (top >= 3) L.toNumber(3).toInt() else 0
            L.pushJavaObject(IntOffset(x, y)); return 1
        }
    })
    // 增强 IntOffset: 创建带 __call 和 Zero 静态字段的表
    val intOffsetFactoryIdx = L.getTop()
    L.newTable()
    val intOffsetTableIdx = L.getTop()
    // 添加 Zero 静态值
    L.pushJavaObject(IntOffset.Zero); L.setField(-2, "Zero")
    // 设置 __call 元方法
    L.newTable()
    L.pushValue(intOffsetFactoryIdx); L.setField(-2, "__call")
    L.setMetaTable(intOffsetTableIdx)
    L.pop(1)  // 弹出工厂函数
    L.setField(-2, "IntOffset")

    // compose.Size(width, height)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val w = if (top >= 2) L.toNumber(2) else 0.0
            val h = if (top >= 3) L.toNumber(3) else 0.0
            L.pushJavaObject(LuaSize(w, h)); return 1
        }
    })
    L.setField(-2, "Size")

    // compose.Rect(left, top, right, bottom)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val l = if (top >= 2) L.toNumber(2) else 0.0
            val t = if (top >= 3) L.toNumber(3) else 0.0
            val r = if (top >= 4) L.toNumber(4) else 0.0
            val b = if (top >= 5) L.toNumber(5) else 0.0
            L.pushJavaObject(LuaRect(l, t, r, b)); return 1
        }
    })
    L.setField(-2, "Rect")
}

// ========== 渲染与反射 ==========

/** compose.render(renderFn) — 存储 Lua 渲染函数引用，不立即调用 */
internal fun ComposeBridgeInstance.registerRenderFunction(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2 || !L.isFunction(2)) {
                logE(TAG) { "[render] 参数不是函数！top=$top, type=${if (top >= 2) L.type(2) else "none"}" }
                throw com.luajava.LuaException("compose.render() 需要一个函数作为参数")
            }
            activeLuaFunc = L.getLuaObject(2)
            return 0
        }
    })
    L.setField(-2, "render")
}

/** compose.wrapObject / compose.wrapClass — 反射辅助函数 */
internal fun ComposeBridgeInstance.registerReflectHelpers(L: LuaState) {
    // wrapObject(javaObj)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top >= 2) {
                try {
                    val obj = L.toJavaObject(2)
                    LazyObjectWrapper.wrapObject(L, obj)
                    return 1
                } catch (e: Exception) {
                    logW(TAG) { "[wrapObject] 转换失败: ${e.message}" }
                }
            }
            L.pushNil()
            return 1
        }
    })
    L.setField(-2, "wrapObject")

    // wrapClass(className)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top >= 2) {
                val className = L.toString(2)
                LazyObjectWrapper.wrapClass(L, className)
                return 1
            }
            L.pushNil()
            return 1
        }
    })
    L.setField(-2, "wrapClass")
}

// ========== 其他工厂 ==========

/** compose.Path() / compose.LuaPath() — 创建 LuaPath 实例 */
internal fun ComposeBridgeInstance.registerPathFactory(L: LuaState) {
    // 注册两个名字：Path（推荐）和 LuaPath（兼容）
    for (name in listOf("Path", "LuaPath")) {
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                L.pushJavaObject(LuaPath()); return 1
            }
        })
        L.setField(-2, name)
    }
}

/** compose.rememberCoroutineScope() — 创建协程作用域 */
internal fun ComposeBridgeInstance.registerCoroutineScopeFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            L.pushJavaObject(LuaCoroutineScope()); return 1
        }
    })
    L.setField(-2, "rememberCoroutineScope")
}

/** compose.Animatable(initialValue, vectorConverter?) — 创建 Animatable 实例，支持 Float 和 Offset 类型 */
internal fun ComposeBridgeInstance.registerAnimatableFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top >= 3) {
                // 两个参数：initialValue 和 vectorConverter（Offset 类型动画）
                val initial = try { L.toJavaObject(2) } catch (e: Exception) { 0f }
                val converter = try { L.toJavaObject(3) } catch (e: Exception) { null }
                L.pushJavaObject(LuaAnimatable(initial ?: 0f, converter)); return 1
            }
            // 单参数：Float 类型动画
            val initial = if (top >= 2) L.toNumber(2).toFloat() else 0f
            L.pushJavaObject(LuaAnimatable(initial)); return 1
        }
    })
    L.setField(-2, "Animatable")
}

/** compose.Easing — 缓动函数表 */
internal fun ComposeBridgeInstance.registerEasingTable(L: LuaState) {
    L.newTable()
    L.pushJavaObject(EasingTable.Linear); L.setField(-2, "Linear")
    L.pushJavaObject(EasingTable.FastOutSlowIn); L.setField(-2, "FastOutSlowIn")
    L.pushJavaObject(EasingTable.FastOutLinearIn); L.setField(-2, "FastOutLinearIn")
    L.pushJavaObject(EasingTable.LinearOutSlowIn); L.setField(-2, "LinearOutSlowIn")
    L.pushJavaObject(EasingTable.EaseIn); L.setField(-2, "EaseIn")
    L.pushJavaObject(EasingTable.EaseOut); L.setField(-2, "EaseOut")
    L.pushJavaObject(EasingTable.EaseInOut); L.setField(-2, "EaseInOut")
    L.pushJavaObject(EasingTable.EaseInCubic); L.setField(-2, "EaseInCubic")
    L.pushJavaObject(EasingTable.EaseOutCubic); L.setField(-2, "EaseOutCubic")
    L.pushJavaObject(EasingTable.EaseInOutCubic); L.setField(-2, "EaseInOutCubic")
    L.setField(-2, "Easing")

    // compose.CubicBezierEasing(a, b, c, d) — 三次贝塞尔缓动函数工厂
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val a = if (top >= 2) L.toNumber(2).toFloat() else 0f
            val b = if (top >= 3) L.toNumber(3).toFloat() else 0f
            val c = if (top >= 4) L.toNumber(4).toFloat() else 1f
            val d = if (top >= 5) L.toNumber(5).toFloat() else 1f
            L.pushJavaObject(CubicBezierEasing(a, b, c, d)); return 1
        }
    })
    L.setField(-2, "CubicBezierEasing")
}

// =====================================================================
// 以下为本次新增的高/中优先级 API 注入器
// =====================================================================

// ========== 高优 1: sp 单位 ==========

/** compose.sp(value) — sp 字体大小单位（当前为数字透传，与 dp 语义区分） */
internal fun ComposeBridgeInstance.registerSpHelper(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val value = if (top < 2) 0.0 else L.toNumber(2)
            L.pushNumber(value); return 1
        }
    })
    L.setField(-2, "sp")
}

// ========== 高优 2: LocalContext ==========

/** compose.LocalContext — Android Context 的延迟访问代理 */
internal fun ComposeBridgeInstance.registerLocalContext(L: LuaState) {
    L.newTable()
    val ctxIdx = L.getTop()
    L.newTable()
    val handler = object : JavaFunction(L) {
        override fun execute(): Int {
            val key = L.toString(3)
            val ctx = androidContext
            if (ctx == null) {
                logW(TAG) { "[LocalContext] androidContext 未注入，返回 nil" }
                L.pushNil(); return 1
            }
            when (key) {
                "packageName" -> L.pushString(ctx.packageName ?: "")
                "resources" -> L.pushJavaObject(ctx.resources)
                "assets" -> L.pushJavaObject(ctx.assets)
                "filesDir" -> L.pushString(ctx.filesDir?.absolutePath ?: "")
                "cacheDir" -> L.pushString(ctx.cacheDir?.absolutePath ?: "")
                "raw" -> L.pushJavaObject(ctx)
                else -> {
                    try {
                        L.pushJavaObject(ctx)
                    } catch (e: Exception) {
                        L.pushNil()
                    }
                }
            }
            return 1
        }
    }
    LazyNamespace.pushLuaIndexWrapper(L, handler)
    L.setField(-2, "__index")
    L.setMetaTable(ctxIdx)
    L.setField(-2, "LocalContext")
}

// ========== 高优 3: LocalConfiguration ==========

/** compose.LocalConfiguration — 屏幕方向、暗色模式、语言等配置 */
internal fun ComposeBridgeInstance.registerLocalConfiguration(L: LuaState) {
    L.newTable()
    val cfgIdx = L.getTop()
    L.newTable()
    val handler = object : JavaFunction(L) {
        override fun execute(): Int {
            val key = L.toString(3)
            val cfg = androidConfiguration
            if (cfg == null) {
                L.pushNil(); return 1
            }
            when (key) {
                "orientation" -> L.pushInteger(cfg.orientation.toLong())
                "screenWidthDp" -> L.pushInteger(cfg.screenWidthDp.toLong())
                "screenHeightDp" -> L.pushInteger(cfg.screenHeightDp.toLong())
                "uiMode" -> L.pushInteger(cfg.uiMode.toLong())
                "isNightMode" -> {
                    val isNight = (cfg.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                    L.pushBoolean(isNight)
                }
                "locale" -> {
                    val locale = cfg.locales.get(0)
                    L.pushString("${locale.language}_${locale.country}")
                }
                "fontScale" -> L.pushNumber(cfg.fontScale.toDouble())
                else -> L.pushNil()
            }
            return 1
        }
    }
    LazyNamespace.pushLuaIndexWrapper(L, handler)
    L.setField(-2, "__index")
    L.setMetaTable(cfgIdx)
    L.setField(-2, "LocalConfiguration")
}

// ========== 高优 4: with 上下文接收器 ==========

/**
 * compose.with(receiver, block) — 以 receiver 为上下文执行 block
 * 类似 Kotlin 的 with()，Lua 中 block 函数接收 receiver 作为第一个参数
 *
 * Lua 用法:
 *   compose.with(receiver, function(r)
 *     r:someMethod()  -- receiver 的方法
 *   end)
 */
internal fun ComposeBridgeInstance.registerWithContext(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 3 || !L.isFunction(3)) {
                logW(TAG) { "[with] 需要 (receiver, function) 两个参数" }
                L.pushNil(); return 1
            }
            // 将 receiver (栈位置 2) 复制到栈顶，作为函数的第一个参数
            L.pushValue(2)       // receiver
            val pcallResult = L.pcall(1, 1, 0)  // 调用 function，传入 receiver
            if (pcallResult != 0) {
                logW(TAG) { "[with] block 执行错误: ${L.toString(-1)}" }
                L.pop(1); L.pushNil()
            }
            return 1
        }
    })
    L.setField(-2, "with")
}

// ========== 高优 5: 手势检测 gestures 命名空间 ==========

/**
 * compose.gestures.detectTapGestures({ onTap=..., onDoubleTap=..., onLongPress=... })
 * compose.gestures.detectDragGestures({ onDragStart=..., onDrag=..., onDragEnd=..., onDragCancel=... })
 *
 * 直接设置当前 GestureConfig 的回调（由 applyGestures 在 pointerInput 块中调用 gestureBlock 时使用）
 */
internal fun ComposeBridgeInstance.registerGesturesNamespace(L: LuaState) {
    L.newTable()
    val gesturesIdx = L.getTop()

    // detectTapGestures — 从配置表中提取回调，直接设置到当前 GestureConfig
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2 || !L.isTable(2)) {
                logW(TAG) { "[gestures.detectTapGestures] 需要配置表" }
                L.pushNil(); return 1
            }
            val config = currentGestureConfig
            if (config != null) {
                L.getField(2, "onTap")
                if (L.isFunction(-1)) config.onTap = L.getLuaObject(-1)
                L.pop(1)

                L.getField(2, "onDoubleTap")
                if (L.isFunction(-1)) config.onDoubleTap = L.getLuaObject(-1)
                L.pop(1)

                L.getField(2, "onLongPress")
                if (L.isFunction(-1)) config.onLongPress = L.getLuaObject(-1)
                L.pop(1)
            }
            return 0
        }
    })
    L.setField(-2, "detectTapGestures")

    // detectDragGestures — 从配置表中提取回调，直接设置到当前 GestureConfig
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2 || !L.isTable(2)) {
                logW(TAG) { "[gestures.detectDragGestures] 需要配置表" }
                L.pushNil(); return 1
            }
            val config = currentGestureConfig
            if (config != null) {
                L.getField(2, "onDragStart")
                if (L.isFunction(-1)) config.onDragStart = L.getLuaObject(-1)
                L.pop(1)

                L.getField(2, "onDrag")
                if (L.isFunction(-1)) config.onDrag = L.getLuaObject(-1)
                L.pop(1)

                L.getField(2, "onDragEnd")
                if (L.isFunction(-1)) config.onDragEnd = L.getLuaObject(-1)
                L.pop(1)

                L.getField(2, "onDragCancel")
                if (L.isFunction(-1)) config.onDragCancel = L.getLuaObject(-1)
                L.pop(1)
            }
            return 0
        }
    })
    L.setField(-2, "detectDragGestures")

    L.setField(-2, "gestures")
}

// ========== 高优 6: remember 带 keys ==========

/**
 * compose.remember(key1, key2, ..., function) — 基于 key 的缓存
 * 当任意 key 变化时重新计算，否则返回缓存值
 *
 * Lua 用法:
 *   local result = compose.remember(count, function() return count * 2 end)
 */
internal fun ComposeBridgeInstance.registerRememberKeysFactory(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2) {
                logW(TAG) { "[rememberKeys] 需要至少一个参数" }
                L.pushNil(); return 1
            }

            // 找到最后一个函数参数
            var lastFuncIdx = -1
            for (i in top downTo 2) {
                if (L.isFunction(i)) { lastFuncIdx = i; break }
            }
            if (lastFuncIdx < 0) {
                logW(TAG) { "[rememberKeys] 未找到函数参数" }
                L.pushNil(); return 1
            }

            // 收集所有 key 值（函数之前的参数）
            val keys = mutableListOf<Any?>()
            for (i in 2 until lastFuncIdx) {
                keys.add(try { L.toJavaObject(i) } catch (e: Exception) { L.toString(i) })
            }

            // ★ 通过 ComposeScope 管理带 key 的 remember 缓存
            val scope = currentScope
            val result = scope.getOrCreateRemember(keys = keys, initFn = {
                L.pushValue(lastFuncIdx)
                val ok = L.pcall(0, 1, 0)
                val r = if (ok == 0) L.toJavaObject(-1) else null
                L.pop(if (ok == 0) 1 else 0)
                r
            })
            L.pushJavaObject(result); return 1
        }
    })
    L.setField(-2, "rememberKeys")
}

// ========== 高优 7/8: LaunchedEffect / coroutineScope.launch ==========
// (LaunchedEffect 已作为组件存在，coroutineScope.launch 已在 LuaCoroutineScope 中实现)
// 此处补充 compose.LaunchedEffect 作为可直接调用的 API（非组件形式）

/**
 * compose.LaunchedEffect(key, block) 或 compose.LaunchedEffect({ key=..., block=..., children={...} })
 * 支持两种调用方式：
 *   1. 直接调用: compose.LaunchedEffect(someKey, function() ... end)
 *   2. Table 组件语法: compose.LaunchedEffect({ key = ..., block = function() ... end, children = { ... } })
 */
internal fun ComposeBridgeInstance.registerLaunchedEffectApi(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            var key: Any? = Unit
            var block: LuaObject? = null
            val children = mutableListOf<com.nirithy.luacompose.node.ComposeNode>()

            if (top >= 2 && L.isTable(2)) {
                // Table 语法: compose.LaunchedEffect({ key=..., block=..., children={...} })
                L.getField(2, "key")
                if (!L.isNil(-1)) {
                    key = try { L.toJavaObject(-1) } catch (e: Exception) { L.toString(-1) }
                }
                L.pop(1)

                L.getField(2, "block")
                if (L.isFunction(-1)) block = L.getLuaObject(-1)
                L.pop(1)

                L.getField(2, "children")
                if (L.isTable(-1)) {
                    children.addAll(NodeParser.parseChildrenArray(L, -1))
                }
                L.pop(1)
            } else {
                // 直接语法: compose.LaunchedEffect(key, block)
                if (top >= 2) {
                    key = try { L.toJavaObject(2) } catch (e: Exception) { L.toString(2) }
                }
                if (top >= 3 && L.isFunction(3)) block = L.getLuaObject(3)
                else if (top >= 2 && L.isFunction(2)) block = L.getLuaObject(2)
            }

            if (block == null) {
                logW(TAG) { "[LaunchedEffect] 需要 block 函数" }
                L.pushNil(); return 1
            }

            val node = com.nirithy.luacompose.node.ComposeNode(
                type = "LaunchedEffect",
                props = mapOf("key" to (key ?: Unit), "block" to (block as Any)),
                children = children
            )
            L.pushJavaObject(node); return 1
        }
    })
    L.setField(-2, "LaunchedEffect")
}

// ========== 高优 9/10: DisposableEffect / key ==========
// (已作为组件存在，通过 EffectPlugin 渲染，这里补充直接 API)

/**
 * compose.DisposableEffect(key, effectFn) 或 compose.DisposableEffect({ key=..., effect=..., children={...} })
 * 支持两种调用方式：
 *   1. 直接调用: compose.DisposableEffect(someKey, function() return function() ... end end)
 *   2. Table 组件语法: compose.DisposableEffect({ key = ..., effect = function() ... end, children = { ... } })
 */
internal fun ComposeBridgeInstance.registerDisposableEffectApi(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            var key: Any? = Unit
            var effectFn: LuaObject? = null
            val children = mutableListOf<com.nirithy.luacompose.node.ComposeNode>()

            if (top >= 2 && L.isTable(2)) {
                // Table 语法: compose.DisposableEffect({ key=..., effect=..., children={...} })
                L.getField(2, "key")
                if (!L.isNil(-1)) {
                    key = try { L.toJavaObject(-1) } catch (e: Exception) { L.toString(-1) }
                }
                L.pop(1)

                L.getField(2, "effect")
                if (L.isFunction(-1)) effectFn = L.getLuaObject(-1)
                L.pop(1)

                L.getField(2, "children")
                if (L.isTable(-1)) {
                    children.addAll(NodeParser.parseChildrenArray(L, -1))
                }
                L.pop(1)
            } else {
                // 直接语法: compose.DisposableEffect(key, effectFn)
                if (top >= 2) {
                    key = try { L.toJavaObject(2) } catch (e: Exception) { L.toString(2) }
                }
                if (top >= 3 && L.isFunction(3)) effectFn = L.getLuaObject(3)
                else if (top >= 2 && L.isFunction(2)) effectFn = L.getLuaObject(2)
            }

            if (effectFn == null) {
                logW(TAG) { "[DisposableEffect] 需要 effect 函数" }
                L.pushNil(); return 1
            }

            val node = com.nirithy.luacompose.node.ComposeNode(
                type = "DisposableEffect",
                props = mapOf("key" to (key ?: Unit), "effect" to (effectFn as Any)),
                children = children
            )
            L.pushJavaObject(node); return 1
        }
    })
    L.setField(-2, "DisposableEffect")
}

/**
 * compose.key(key, childrenFunc) 或 compose.key({ key=..., children={...} })
 * 支持两种调用方式：
 *   1. 直接调用: compose.key(someKey, function() return { ... } end)
 *   2. Table 组件语法: compose.key({ key = ..., children = { ... } })
 */
internal fun ComposeBridgeInstance.registerKeyApi(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            var keyVal: Any? = Unit
            var childrenFunc: LuaObject? = null
            val children = mutableListOf<com.nirithy.luacompose.node.ComposeNode>()

            if (top >= 2 && L.isTable(2)) {
                // Table 语法: compose.key({ key=..., children={...} })
                L.getField(2, "key")
                if (!L.isNil(-1)) {
                    keyVal = try { L.toJavaObject(-1) } catch (e: Exception) { L.toString(-1) }
                }
                L.pop(1)

                L.getField(2, "children")
                if (L.isTable(-1)) {
                    children.addAll(NodeParser.parseChildrenArray(L, -1))
                } else if (L.isFunction(-1)) {
                    childrenFunc = L.getLuaObject(-1)
                }
                L.pop(1)
            } else {
                // 直接语法: compose.key(key, childrenFunc)
                if (top >= 2) {
                    keyVal = try { L.toJavaObject(2) } catch (e: Exception) { L.toString(2) }
                }
                if (top >= 3 && L.isFunction(3)) childrenFunc = L.getLuaObject(3)
                else if (top >= 2 && L.isFunction(2)) childrenFunc = L.getLuaObject(2)
            }

            val node = com.nirithy.luacompose.node.ComposeNode(
                type = "key",
                props = mapOf("key" to (keyVal ?: Unit)),
                children = children,
                childrenFunc = childrenFunc
            )
            L.pushJavaObject(node); return 1
        }
    })
    L.setField(-2, "key")
}

// ========== 中优 11: Arrangement.spacedBy / aligned ==========

/**
 * compose.Arrangement 增强：添加 spacedBy 和 aligned 工厂函数
 * spacedBy(space) 返回 "spacedBy_<space>" 字符串，在 ComposeRenderer 中解析
 */
internal fun ComposeBridgeInstance.registerArrangementEnhancements(L: LuaState) {
    // 扩展 Arrangement 表，添加 spacedBy 工厂
    L.getField(-1, "Arrangement")
    if (L.isTable(-1)) {
        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val space = if (top >= 2) L.toNumber(2) else 0.0
                L.pushString("spacedBy_$space"); return 1
            }
        })
        L.setField(-2, "spacedBy")

        L.pushJavaFunction(object : JavaFunction(L) {
            override fun execute(): Int {
                val top = L.getTop()
                val alignment = if (top >= 2) L.toString(2) else "Center"
                L.pushString("aligned_$alignment"); return 1
            }
        })
        L.setField(-2, "aligned")
    }
    L.pop(1)  // 弹出 Arrangement
}

// ========== 中优 12: RoundedCornerShape / CircleShape ==========

/** compose.RoundedCornerShape(dp) / compose.CircleShape */
internal fun ComposeBridgeInstance.registerShapeFactories(L: LuaState) {
    // RoundedCornerShape(topStart, topEnd, bottomStart, bottomEnd) 或 RoundedCornerShape(all)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val all = if (top >= 2) L.toNumber(2) else 0.0
            L.pushJavaObject(com.nirithy.luacompose.graphics.LuaShape.rounded(all)); return 1
        }
    })
    L.setField(-2, "RoundedCornerShape")

    // CircleShape
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            L.pushJavaObject(com.nirithy.luacompose.graphics.LuaShape.circle()); return 1
        }
    })
    L.setField(-2, "CircleShape")
}

// ========== 中优 13: Brush.radialGradient ==========

/** compose.Brush — 画刷工厂表 */
internal fun ComposeBridgeInstance.registerBrushRadialGradient(L: LuaState) {
    L.newTable()
    val brushIdx = L.getTop()

    // radialGradient(colorStops, center, radius) 或 radialGradient({centerX, centerY, radius, colors})
    // 支持两种调用方式：
    //   1. 三参数: Brush.radialGradient({[0.0]=Color(...), [0.3]=Color(...), ...}, Offset(...), radius)
    //   2. 单表参数: Brush.radialGradient({centerX=..., centerY=..., radius=..., colors={...}})
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2) {
                logW(TAG) { "[Brush.radialGradient] 需要参数" }
                L.pushNil(); return 1
            }
            
            // 判断调用方式：如果第二个参数是 table 且没有 'centerX' 字段，则为三参数模式
            val isThreeParamMode = top >= 3 && L.isTable(2) && !hasCenterXField(L, 2)
            
            if (isThreeParamMode && top >= 4) {
                // 三参数模式：radialGradient(colorStopsTable, centerOffset, radius)
                // 从颜色映射表中提取颜色和位置
                val colorStops = mutableListOf<Pair<Float, Long>>()
                L.pushNil()
                while (L.next(2) != 0) {
                    // 键是位置（0.0, 0.3, 0.7, 1.0），值是 LuaColor 对象
                    val stopPosition = L.toNumber(-2).toFloat()
                    val colorObj = try { L.toJavaObject(-1) } catch (e: Exception) { null }
                    val colorLong = when (colorObj) {
                        is LuaColor -> colorObj.toArgb()
                        is Number -> colorObj.toLong()
                        else -> {
                            // 尝试从栈上读取数字
                            try { L.toNumber(-1).toLong() } catch (e: Exception) { 0xFF000000L }
                        }
                    }
                    colorStops.add(Pair(stopPosition, colorLong))
                    L.pop(1)
                }
                // 按位置排序
                colorStops.sortBy { it.first }
                val colors = colorStops.map { it.second }
                val stops = colorStops.map { it.first }
                
                // 第二个参数：中心点 Offset
                val centerObj = try { L.toJavaObject(3) } catch (e: Exception) { null }
                val cx: Double
                val cy: Double
                when (centerObj) {
                    is LuaOffset -> { cx = centerObj.x; cy = centerObj.y }
                    else -> { cx = 0.0; cy = 0.0 }
                }
                
                // 第三个参数：半径
                val radius = if (top >= 4) L.toNumber(4) else 1.0
                
                L.pushJavaObject(LuaBrush(
                    type = "radialGradient",
                    centerX = cx, centerY = cy, radius = radius,
                    colors = colors,
                    colorStops = stops
                )); return 1
            }
            
            // 单表参数模式（向后兼容）
            if (!L.isTable(2)) {
                logW(TAG) { "[Brush.radialGradient] 需要配置表" }
                L.pushNil(); return 1
            }
            L.getField(2, "centerX"); val cx = if (L.isNumber(-1)) L.toNumber(-1) else 0.5; L.pop(1)
            L.getField(2, "centerY"); val cy = if (L.isNumber(-1)) L.toNumber(-1) else 0.5; L.pop(1)
            L.getField(2, "radius"); val radius = if (L.isNumber(-1)) L.toNumber(-1) else 1.0; L.pop(1)
            L.getField(2, "colors")
            val colors = mutableListOf<Long>()
            if (L.isTable(-1)) {
                val len = L.rawLen(-1)
                if (len > 0) {
                    for (i in 1..len) {
                        L.pushInteger(i.toLong()); L.getTable(-2)
                        colors.add(L.toNumber(-1).toLong())
                        L.pop(1)
                    }
                }
            }
            L.pop(1)

            L.pushJavaObject(LuaBrush(
                type = "radialGradient",
                centerX = cx, centerY = cy, radius = radius, colors = colors
            )); return 1
        }
        
        /** 检查 table 是否包含 'centerX' 字段（用于区分单表和三参数模式） */
        private fun hasCenterXField(L: LuaState, tableIdx: Int): Boolean {
            L.getField(tableIdx, "centerX")
            val has = L.isNumber(-1)
            L.pop(1)
            return has
        }
    })
    L.setField(-2, "radialGradient")

    // verticalGradient({ colors, startY, endY })
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2 || !L.isTable(2)) { L.pushNil(); return 1 }
            L.getField(2, "startY"); val sy = if (L.isNumber(-1)) L.toNumber(-1) else 0.0; L.pop(1)
            L.getField(2, "endY"); val ey = if (L.isNumber(-1)) L.toNumber(-1) else 1.0; L.pop(1)
            L.getField(2, "colors")
            val colors = mutableListOf<Long>()
            if (L.isTable(-1)) {
                val len = L.rawLen(-1)
                for (i in 1..len) { L.pushInteger(i.toLong()); L.getTable(-2); colors.add(L.toNumber(-1).toLong()); L.pop(1) }
            }
            L.pop(1)
            L.pushJavaObject(com.nirithy.luacompose.graphics.LuaBrush(
                type = "verticalGradient", startY = sy, endY = ey, colors = colors
            )); return 1
        }
    })
    L.setField(-2, "verticalGradient")

    // linearGradient({ startX, startY, endX, endY, colors })
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2 || !L.isTable(2)) { L.pushNil(); return 1 }
            L.getField(2, "startX"); val sx = if (L.isNumber(-1)) L.toNumber(-1) else 0.0; L.pop(1)
            L.getField(2, "startY"); val sy = if (L.isNumber(-1)) L.toNumber(-1) else 0.0; L.pop(1)
            L.getField(2, "endX"); val ex = if (L.isNumber(-1)) L.toNumber(-1) else 1.0; L.pop(1)
            L.getField(2, "endY"); val ey = if (L.isNumber(-1)) L.toNumber(-1) else 1.0; L.pop(1)
            L.getField(2, "colors")
            val colors = mutableListOf<Long>()
            if (L.isTable(-1)) {
                val len = L.rawLen(-1)
                for (i in 1..len) { L.pushInteger(i.toLong()); L.getTable(-2); colors.add(L.toNumber(-1).toLong()); L.pop(1) }
            }
            L.pop(1)
            L.pushJavaObject(com.nirithy.luacompose.graphics.LuaBrush(
                type = "linearGradient", startX = sx, startY = sy, endX = ex, endY = ey, colors = colors
            )); return 1
        }
    })
    L.setField(-2, "linearGradient")

    L.setField(-2, "Brush")
}

// ========== 低优 24: CardDefaults.cardColors ==========

/** compose.CardDefaults.cardColors{ containerColor=..., contentColor=... } */
internal fun ComposeBridgeInstance.registerCardDefaults(L: LuaState) {
    L.newTable()
    val cardDefaultsIdx = L.getTop()

    // cardColors 工厂
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            var containerColor: Long? = null
            var contentColor: Long? = null
            var disabledContainerColor: Long? = null
            var disabledContentColor: Long? = null

            if (top >= 2 && L.isTable(2)) {
                L.getField(2, "containerColor")
                if (L.isNumber(-1)) containerColor = L.toNumber(-1).toLong()
                L.pop(1)

                L.getField(2, "contentColor")
                if (L.isNumber(-1)) contentColor = L.toNumber(-1).toLong()
                L.pop(1)

                L.getField(2, "disabledContainerColor")
                if (L.isNumber(-1)) disabledContainerColor = L.toNumber(-1).toLong()
                L.pop(1)

                L.getField(2, "disabledContentColor")
                if (L.isNumber(-1)) disabledContentColor = L.toNumber(-1).toLong()
                L.pop(1)
            }

            L.pushJavaObject(com.nirithy.luacompose.graphics.CardColorsFactory.create(
                containerColor = containerColor?.toInt()?.let { androidx.compose.ui.graphics.Color(it) },
                contentColor = contentColor?.toInt()?.let { androidx.compose.ui.graphics.Color(it) },
                disabledContainerColor = disabledContainerColor?.toInt()?.let { androidx.compose.ui.graphics.Color(it) },
                disabledContentColor = disabledContentColor?.toInt()?.let { androidx.compose.ui.graphics.Color(it) },
            )); return 1
        }
    })
    L.setField(-2, "cardColors")

    L.setField(-2, "CardDefaults")
}

// ========== 低优 23: sharedElement 动画 ==========

/** compose.sharedElement(key) — 共享元素过渡动画，返回特殊标记字符串 */
internal fun ComposeBridgeInstance.registerSharedElement(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val key = if (top >= 2) L.toString(2) else ""
            L.pushString("sharedElement_$key"); return 1
        }
    })
    L.setField(-2, "sharedElement")
}

// ========== 中优 14/15: Path API / Stroke / StrokeCap / PathOperation ==========
// (Path API 基本完善，补充 PathOperation 枚举)

/** compose.PathOperation — 路径布尔运算枚举 */
internal fun ComposeBridgeInstance.registerStrokeTable(L: LuaState) {
    L.newTable()
    L.pushString("Difference"); L.setField(-2, "Difference")
    L.pushString("Intersect"); L.setField(-2, "Intersect")
    L.pushString("Union"); L.setField(-2, "Union")
    L.pushString("Xor"); L.setField(-2, "Xor")
    L.pushString("ReverseDifference"); L.setField(-2, "ReverseDifference")
    L.setField(-2, "PathOperation")

    // StrokeCap
    L.newTable()
    L.pushString("Butt"); L.setField(-2, "Butt")
    L.pushString("Round"); L.setField(-2, "Round")
    L.pushString("Square"); L.setField(-2, "Square")
    L.setField(-2, "StrokeCap")
}

// ========== 中优 16: Color 伴生对象（预定义颜色、luminance、copy） ==========

/** compose.Color 预定义常量和工具方法 */
internal fun ComposeBridgeInstance.registerColorCompanion(L: LuaState) {
    // Color 已在 registerGraphicsFactories 中注册为工厂函数
    // 需要增强：让 Color 表同时支持工厂调用和常量访问
    // 方案：创建 Color 表，设置 __call 元方法为工厂函数，同时添加常量字段
    L.getField(-1, "Color")  // 获取现有的 Color 工厂函数
    if (L.isFunction(-1)) {
        val colorFactoryIdx = L.getTop()  // 保存工厂函数位置

        // 创建 Color 表
        L.newTable()
        val colorTableIdx = L.getTop()

        // 添加预定义颜色常量
        val predefined = mapOf(
            "Black" to 0xFF000000L, "White" to 0xFFFFFFFFL,
            "Red" to 0xFFFF0000L, "Green" to 0xFF00FF00L, "Blue" to 0xFF0000FFL,
            "Yellow" to 0xFFFFFF00L, "Cyan" to 0xFF00FFFFL, "Magenta" to 0xFFFF00FFL,
            "Gray" to 0xFF888888L, "DarkGray" to 0xFF444444L, "LightGray" to 0xFFCCCCCCL,
            "Transparent" to 0x00000000L,
        )
        for ((name, argb) in predefined) {
            L.pushNumber(argb.toDouble()); L.setField(-2, name)
        }

        // 设置 __call 元方法，使 Color 表可调用
        L.newTable()
        L.pushValue(colorFactoryIdx)  // 工厂函数
        L.setField(-2, "__call")
        L.setMetaTable(colorTableIdx)

        L.pop(1)  // 弹出工厂函数
        L.setField(-2, "Color")  // 替换 compose 中的 Color 字段
    } else {
        L.pop(1)  // 不是函数，弹出
    }
}

// ========== 中优 17: dump 调试工具 ==========

/** compose.dump(value) — 打印 Lua 值到 logcat */
internal fun ComposeBridgeInstance.registerDumpTool(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top < 2) {
                Log.i("ComposeDump", "dump() called with no arguments")
                return 0
            }
            val sb = StringBuilder("dump: ")
            for (i in 2..top) {
                sb.append(when {
                    L.isNil(i) -> "nil"
                    L.isBoolean(i) -> if (L.toBoolean(i)) "true" else "false"
                    L.isNumber(i) -> L.toNumber(i).toString()
                    L.isString(i) -> "\"${L.toString(i)}\""
                    L.isTable(i) -> {
                        val len = L.rawLen(i)
                        if (len > 0) "table[${len}]" else "table{}"
                    }
                    L.isFunction(i) -> "function"
                    else -> try { "userdata(${L.toJavaObject(i)?.javaClass?.simpleName})" }
                        catch (e: Exception) { "unknown" }
                })
                if (i < top) sb.append(", ")
            }
            Log.i("ComposeDump", sb.toString())
            return 0
        }
    })
    L.setField(-2, "dump")
}

// ========== delay / withFrameNanos ==========

/** compose.delay(ms) — 非阻塞延迟提示（必须在协程中使用 scope.delay(ms)） */
internal fun ComposeBridgeInstance.registerDelayTool(L: LuaState) {
    val bridge = this
    // delay(ms) — 不能在JavaFunction中挂起，仅输出警告不阻塞
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val ms = if (top >= 2) L.toNumber(2).toLong() else 0L
            Log.e(TAG, "[delay] compose.delay($ms) 是挂起函数，必须在协程中使用 scope:delay($ms)，直接调用不会生效且已废弃")
            // 不再使用Thread.sleep阻塞主线程
            return 0
        }
    })
    L.setField(-2, "delay")

    // withFrameNanos(callback) — 使用Choreographer真正等待下一帧
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            if (top >= 2 && L.isFunction(2)) {
                val fn = L.getLuaObject(2)
                try {
                    android.view.Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
                        synchronized(bridge.luaLock) {
                            try {
                                fn.call(frameTimeNanos.toDouble())
                            } catch (e: Exception) {
                                logW(TAG) { "[withFrameNanos] 回调失败: ${e.message}" }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logW(TAG) { "[withFrameNanos] 投递帧回调失败: ${e.message}" }
                }
            }
            return 0
        }
    })
    L.setField(-2, "withFrameNanos")

    // ========== 高精度定时器 ==========

    /**
     * compose.startTimer(intervalMs, callback)
     * 在 Kotlin coroutineScope 中启动高精度定时器，使用协程 delay 挂起
     * 返回 stop() 函数用于停止定时器
     *
     * Lua 用法：
     *   local timer = compose.startTimer(100, function()
     *       timerValue.value = timerValue.value + 0.1
     *   end)
     *   -- 停止: timer.stop()
     */
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val intervalMs = if (top >= 2) L.toNumber(2).toLong() else 100L
            val callback = if (top >= 3 && L.isFunction(3)) L.getLuaObject(3)
            else if (top >= 2 && L.isFunction(2)) L.getLuaObject(2)
            else null

            if (callback == null) {
                logW(TAG) { "[startTimer] 需要 callback 函数" }
                L.pushNil(); return 1
            }

            val scope = bridge.mainScope
            var running = true
            logI(TAG) { "[startTimer] 启动定时器, intervalMs=$intervalMs, scope=${scope.hashCode()}" }
            val job = scope.launch {
                logI(TAG) { "[startTimer] 协程启动, 开始循环" }
                while (running) {
                    try {
                        callback.call()
                    } catch (e: Exception) {
                        logW(TAG) { "[startTimer] 回调异常: ${e.message}" }
                    }
                    coroutineDelay(intervalMs)
                }
                logI(TAG) { "[startTimer] 协程退出" }
            }
            // 追踪定时器任务，resetState 时统一清理
            bridge.timerJobs.add(job)

            // 返回 stop 函数
            L.newTable()
            L.pushJavaFunction(object : JavaFunction(L) {
                override fun execute(): Int {
                    running = false
                    job.cancel()
                    bridge.timerJobs.remove(job)
                    return 0
                }
            })
            L.setField(-2, "stop")
            return 1
        }
    })
    L.setField(-2, "startTimer")

    /**
     * compose.delayMs(ms, callback)
     * 非阻塞延迟回调，使用 Handler.postDelayed 实现
     * 可在任意上下文中调用（回调、协程、事件处理等）
     *
     * Lua 用法：
     *   compose.delayMs(1500, function()
     *       isRefreshing.value = false
     *   end)
     */
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val ms = if (top >= 2) L.toNumber(2).toLong() else 0L
            val callback = if (top >= 3 && L.isFunction(3)) L.getLuaObject(3)
            else if (top >= 2 && L.isFunction(2)) L.getLuaObject(2)
            else null

            if (callback == null) {
                logW(TAG) { "[delayMs] 需要 callback 函数" }
                return 0
            }

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                synchronized(bridge.luaLock) {
                    try {
                        callback.call()
                    } catch (e: Exception) {
                        logW(TAG) { "[delayMs] 回调异常: ${e.message}" }
                    }
                }
            }, ms)
            return 0
        }
    })
    L.setField(-2, "delayMs")

    // compose.delay(ms) — 在 Lua 协程中延迟（用于 LaunchedEffect 等 Kotlin 协程上下文）
    // 注意：由于 Lua 无法直接调用 Kotlin 挂起函数，此处使用 Thread.sleep 实现
    // 在 LaunchedEffect 的 block 中，Kotlin 协程已提供异步上下文，Thread.sleep 不会阻塞 UI
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val ms = if (top >= 2) L.toNumber(2).toLong() else 0L
            if (ms > 0) {
                try {
                    Thread.sleep(ms)
                } catch (_: InterruptedException) {}
            }
            return 0
        }
    })
    L.setField(-2, "delay")

    // lerp 线性插值
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val start = if (top >= 2) L.toNumber(2) else 0.0
            val end = if (top >= 3) L.toNumber(3) else 1.0
            val fraction = if (top >= 4) L.toNumber(4) else 0.5
            L.pushNumber(start + (end - start) * fraction); return 1
        }
    })
    L.setField(-2, "lerp")
}

/** compose.SnackbarHostState() — 创建 Snackbar 宿主状态 */
internal fun ComposeBridgeInstance.registerSnackbarHostState(L: LuaState) {
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            L.pushJavaObject(androidx.compose.material3.SnackbarHostState())
            return 1
        }
    })
    L.setField(-2, "SnackbarHostState")
}

/**
 * compose.showSnackbar(state, message, actionLabel, duration, onResult)
 * 在协程中调用 SnackbarHostState.showSnackbar()
 * @param 1 state: SnackbarHostState
 * @param 2 message: String
 * @param 3 actionLabel: String (可选)
 * @param 4 duration: String "Short"|"Long"|"Indefinite" (可选，默认 "Short")
 * @param 5 onResult: function(result) (可选) — result 为 "ActionPerformed" 或 "Dismissed"
 */
internal fun ComposeBridgeInstance.registerShowSnackbar(L: LuaState) {
    val bridge = this
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val state = if (top >= 2) L.toJavaObject(2) as? androidx.compose.material3.SnackbarHostState
            else null
            val message = if (top >= 3 && L.isString(3)) L.toString(3) else ""
            val actionLabel = if (top >= 4 && L.isString(4)) L.toString(4) else null
            val durationStr = if (top >= 5 && L.isString(5)) L.toString(5) else "Short"
            val onResult = if (top >= 6 && L.isFunction(6)) L.getLuaObject(6)
            else if (top >= 5 && L.isFunction(5)) L.getLuaObject(5)
            else null

            if (state == null || message.isEmpty()) {
                logW("ComposeInjectors") { "[showSnackbar] state 或 message 无效" }
                return 0
            }

            val duration = when (durationStr) {
                "Long" -> androidx.compose.material3.SnackbarDuration.Long
                "Indefinite" -> androidx.compose.material3.SnackbarDuration.Indefinite
                else -> androidx.compose.material3.SnackbarDuration.Short
            }

            val scope = bridge.mainScope
            scope.launch {
                try {
                    val result = state.showSnackbar(message, actionLabel, false, duration)
                    if (onResult != null) {
                        synchronized(bridge.luaLock) {
                            try {
                                onResult.call(result.name) // "ActionPerformed" 或 "Dismissed"
                            } catch (e: Exception) {
                                logW("ComposeInjectors") { "[showSnackbar] onResult 回调异常: ${e.message}" }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logW("ComposeInjectors") { "[showSnackbar] 异常: ${e.message}" }
                }
            }
            return 0
        }
    })
    L.setField(-2, "showSnackbar")
}

// ========== RuntimeShader / RenderEffect 着色器效果 ==========

/**
 * compose.RuntimeShader(skslSource) — 创建 RuntimeShader 实例
 * compose.RenderEffect.createRuntimeShaderEffect(shader, uniformName) — 创建着色器渲染效果
 * compose.RenderEffect.createBlurEffect(radiusX, radiusY) — 创建模糊效果
 * compose.RenderEffect.createOffsetEffect(offsetX, offsetY) — 创建偏移效果
 * compose.RenderEffect.createChainEffect(outer, inner) — 创建链式效果
 *
 * Lua 用法:
 *   local shader = compose.RuntimeShader([[
 *     uniform float2 size;
 *     half4 main(float2 coord) {
 *       return half4(coord.x/size.x, coord.y/size.y, 0.5, 1.0);
 *     }
 *   ]])
 *   shader:setFloatUniform("size", width, height)
 *   local effect = compose.RenderEffect.createRuntimeShaderEffect(shader, "size")
 *   -- 然后通过 graphicsLayer { renderEffect = effect } 使用
 */
internal fun ComposeBridgeInstance.registerRuntimeShaderApi(L: LuaState) {
    // compose.RuntimeShader(skslSource)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val skslSource = if (top >= 2 && L.isString(2)) L.toString(2) else ""
            if (skslSource.isEmpty()) {
                logW(TAG) { "[RuntimeShader] 需要 SKSL 着色器源码" }
                L.pushNil(); return 1
            }
            try {
                val shader = android.graphics.RuntimeShader(skslSource)
                L.pushJavaObject(shader); return 1
            } catch (e: Exception) {
                logW(TAG) { "[RuntimeShader] 创建失败: ${e.message}" }
                L.pushNil(); return 1
            }
        }
    })
    L.setField(-2, "RuntimeShader")

    // compose.RenderEffect 命名空间
    L.newTable()
    val renderEffectIdx = L.getTop()

    // RenderEffect.createRuntimeShaderEffect(shader, uniformName)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val shader = if (top >= 2) {
                try { L.toJavaObject(2) as? android.graphics.RuntimeShader } catch (e: Exception) { null }
            } else null
            val uniformName = if (top >= 3 && L.isString(3)) L.toString(3) else ""

            if (shader == null) {
                logW(TAG) { "[RenderEffect.createRuntimeShaderEffect] 需要 RuntimeShader 对象" }
                L.pushNil(); return 1
            }

            try {
                val effect = if (uniformName.isNotEmpty()) {
                    android.graphics.RenderEffect.createRuntimeShaderEffect(shader, uniformName)
                } else {
                    android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "")
                }
                L.pushJavaObject(effect); return 1
            } catch (e: Exception) {
                logW(TAG) { "[RenderEffect.createRuntimeShaderEffect] 创建失败: ${e.message}" }
                L.pushNil(); return 1
            }
        }
    })
    L.setField(-2, "createRuntimeShaderEffect")

    // RenderEffect.createBlurEffect(radiusX, radiusY, edgeTreatment)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val radiusX = if (top >= 2) L.toNumber(2).toFloat() else 0f
            val radiusY = if (top >= 3) L.toNumber(3).toFloat() else radiusX
            try {
                val effect = android.graphics.RenderEffect.createBlurEffect(
                    radiusX, radiusY,
                    android.graphics.Shader.TileMode.CLAMP
                )
                L.pushJavaObject(effect); return 1
            } catch (e: Exception) {
                logW(TAG) { "[RenderEffect.createBlurEffect] 创建失败: ${e.message}" }
                L.pushNil(); return 1
            }
        }
    })
    L.setField(-2, "createBlurEffect")

    // RenderEffect.createOffsetEffect(offsetX, offsetY)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val offsetX = if (top >= 2) L.toNumber(2).toFloat() else 0f
            val offsetY = if (top >= 3) L.toNumber(3).toFloat() else 0f
            try {
                val effect = android.graphics.RenderEffect.createOffsetEffect(offsetX, offsetY)
                L.pushJavaObject(effect); return 1
            } catch (e: Exception) {
                logW(TAG) { "[RenderEffect.createOffsetEffect] 创建失败: ${e.message}" }
                L.pushNil(); return 1
            }
        }
    })
    L.setField(-2, "createOffsetEffect")

    // RenderEffect.createChainEffect(outer, inner)
    L.pushJavaFunction(object : JavaFunction(L) {
        override fun execute(): Int {
            val top = L.getTop()
            val outer = if (top >= 2) {
                try { L.toJavaObject(2) as? android.graphics.RenderEffect } catch (e: Exception) { null }
            } else null
            val inner = if (top >= 3) {
                try { L.toJavaObject(3) as? android.graphics.RenderEffect } catch (e: Exception) { null }
            } else null
            if (outer == null || inner == null) {
                logW(TAG) { "[RenderEffect.createChainEffect] 需要两个 RenderEffect 对象" }
                L.pushNil(); return 1
            }
            try {
                val effect = android.graphics.RenderEffect.createChainEffect(outer, inner)
                L.pushJavaObject(effect); return 1
            } catch (e: Exception) {
                logW(TAG) { "[RenderEffect.createChainEffect] 创建失败: ${e.message}" }
                L.pushNil(); return 1
            }
        }
    })
    L.setField(-2, "createChainEffect")

    L.setField(-2, "RenderEffect")
}