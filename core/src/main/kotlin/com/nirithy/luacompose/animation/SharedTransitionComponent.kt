package com.nirithy.luacompose.animation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.nirithy.luacompose.bridge.ComposeBridge
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.render.ComposeRenderer
import com.nirithy.luacompose.logE
import com.luajava.LuaObject

/**
 * SharedTransition 共享元素过渡组件
 *
 * 提供 SharedTransitionLayout 容器，内部通过 Modifier.sharedElement(key) 标记
 * 需要过渡的元素，在 AnimatedContent 等切换容器中自动执行界限动画。
 *
 * Lua 用法：
 *   compose.SharedTransitionLayout {
 *     content = function(scope)
 *       compose.AnimatedContent {
 *         targetState = selected.value,
 *         children = function(current)
 *           if current == nil then
 *             compose.Box {
 *               modifier = compose.Modifier().size(100, 100).sharedElement("hero"),
 *             }
 *           else
 *             compose.Box {
 *               modifier = compose.Modifier().fillMaxSize().sharedElement("hero"),
 *             }
 *           end
 *         end,
 *       }
 *     end,
 *   }
 */
object SharedTransitionComponents {
    private const val TAG = "SharedTransition"

    @Composable
    fun SharedTransitionLayoutRenderer(node: ComposeNode) {
        val modifier = ComposeRenderer.resolveModifier(node)
        val contentFn = node.callbacks["content"]

        SharedTransitionLayout(modifier = modifier) {
            // this = SharedTransitionScope
            ComposeBridge.pushActiveSharedTransitionScope(this)

            if (contentFn != null) {
                var error: Exception? = null
                var resultNode: ComposeNode? = null
                try {
                    val result = contentFn.call(this)
                    resultNode = result as? ComposeNode
                } catch (e: Exception) {
                    error = e
                }
                if (error != null) {
                    logE(TAG) { "SharedTransitionLayout content 回调失败: ${error.message}" }
                }
                if (resultNode != null) {
                    ComposeRenderer.RenderNode(resultNode)
                }
            } else {
                ComposeRenderer.RenderChildren(node)
            }
            ComposeBridge.popActiveSharedTransitionScope()
        }
    }

    /**
     * 在 Compose Modifier 上应用 sharedElement 修饰符
     * 由 ComposeRenderer.resolveModifier 调用
     *
     * @param baseModifier 基础 modifier
     * @param sharedElementKey 共享元素键
     * @param boundsTransformFn 自定义界限变换函数（Lua 回调，可为 null）
     * @return 应用了 sharedElement 的 modifier
     */
    @Composable
    fun sharedElementModifier(
        baseModifier: Modifier,
        sharedElementKey: String,
        boundsTransformFn: LuaObject? = null
    ): Modifier {
        val scope = ComposeBridge.getActiveSharedTransitionScope() ?: return baseModifier
        val visibilityScope = ComposeBridge.getActiveAnimatedVisibilityScope() ?: return baseModifier

        // 使用 remember 缓存 SharedContentState，key 不变时复用
        val state = remember(sharedElementKey) {
            createSharedContentState(scope, sharedElementKey)
        } ?: return baseModifier

        // 构建 boundsTransform lambda
        val boundsTransform: (Rect, Rect) -> FiniteAnimationSpec<Rect> = if (boundsTransformFn != null) {
            { initialBounds: Rect, targetBounds: Rect ->
                try {
                    (boundsTransformFn.call(initialBounds, targetBounds) as? FiniteAnimationSpec<Rect>)
                        ?: tween<Rect>()
                } catch (e: Exception) {
                    tween<Rect>()
                }
            }
        } else {
            { _, _ -> tween<Rect>() }
        }

        return with(scope) {
            baseModifier.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = visibilityScope,
                boundsTransform = boundsTransform
            )
        }
    }

    /**
     * 通过反射创建 SharedContentState 实例
     * SharedContentState 是 @InternalAnimationApi，需要反射调用
     */
    private fun createSharedContentState(
        scope: SharedTransitionScope,
        key: String
    ): SharedTransitionScope.SharedContentState? {
        return try {
            val clazz = Class.forName("androidx.compose.animation.SharedTransitionScope\$SharedContentState")
            // 构造函数: (outer: SharedTransitionScope, config: SharedContentConfig, key: Any)
            val ctor = clazz.declaredConstructors.firstOrNull { c ->
                c.parameterTypes.size == 3 &&
                    c.parameterTypes[1] == Class.forName("androidx.compose.animation.SharedTransitionScope\$SharedContentConfig")
            } ?: return null
            ctor.isAccessible = true
            // 创建默认 SharedContentConfig
            val configClazz = ctor.parameterTypes[1]
            val config = configClazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            ctor.newInstance(scope, config, key) as? SharedTransitionScope.SharedContentState
        } catch (e: Exception) {
            logE(TAG) { "创建 SharedContentState 失败: ${e.message}" }
            null
        }
    }
}