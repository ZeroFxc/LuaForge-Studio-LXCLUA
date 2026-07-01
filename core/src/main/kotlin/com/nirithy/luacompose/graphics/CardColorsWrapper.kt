package com.nirithy.luacompose.graphics

import androidx.compose.material3.CardColors
import androidx.compose.ui.graphics.Color

/**
 * CardColors 工厂
 * 使 Lua 端可以通过 compose.CardDefaults.cardColors{} 配置 Card 颜色
 *
 * CardColors 是 final class，不能继承，因此使用工厂方法直接创建实例。
 */
object CardColorsFactory {
    /**
     * 创建 CardColors 实例
     * @param containerColor 容器背景色
     * @param contentColor 内容颜色
     * @param disabledContainerColor 禁用时容器背景色
     * @param disabledContentColor 禁用时内容颜色
     */
    fun create(
        containerColor: Color? = null,
        contentColor: Color? = null,
        disabledContainerColor: Color? = null,
        disabledContentColor: Color? = null,
    ): CardColors {
        return CardColors(
            containerColor = containerColor ?: Color.Unspecified,
            contentColor = contentColor ?: Color.Unspecified,
            disabledContainerColor = disabledContainerColor ?: Color.Unspecified,
            disabledContentColor = disabledContentColor ?: Color.Unspecified,
        )
    }
}