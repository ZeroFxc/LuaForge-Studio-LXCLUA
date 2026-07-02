package com.nirithy.luacompose.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nirithy.luacompose.node.ComposeNode
import com.nirithy.luacompose.plugin.ComposePlugin
import com.nirithy.luacompose.render.ComposeRenderer

/**
 * Icon 组件插件
 * 支持 Material Icons 常用图标
 *
 * Lua 用法：
 *   compose.Icon {
 *     name = "Home",           -- 图标名称
 *     color = compose.Theme.primary,
 *     size = 24,
 *     modifier = compose.Modifier().size(48),
 *   }
 *
 * 支持的图标名（Material Icons Filled）：
 *   Home, Add, ArrowBack, ArrowForward, Check, Close, Delete, Edit,
 *   Favorite, FavoriteBorder, Info, Menu, MoreVert, Person, Search,
 *   Settings, Share, Star, ThumbUp, Warning, Refresh, PlayArrow, Pause,
 *   Email, Lock, Phone, Send, Build, Face, Place, FilterList, Sort
 */
object IconComponent : ComposePlugin {
    override val namespace = "display"

    override fun getComponents() = mapOf<String, @Composable (ComposeNode) -> Unit>(
        "Icon" to { node -> IconRenderer(node) },
    )

    /** 图标名称映射表 */
    internal val iconMap: Map<String, ImageVector> = mapOf(
        "Home" to Icons.Filled.Home,
        "Add" to Icons.Filled.Add,
        "ArrowBack" to Icons.AutoMirrored.Filled.ArrowBack,
        "ArrowForward" to Icons.AutoMirrored.Filled.ArrowForward,
        "Check" to Icons.Filled.Check,
        "Close" to Icons.Filled.Close,
        "Delete" to Icons.Filled.Delete,
        "Edit" to Icons.Filled.Edit,
        "Favorite" to Icons.Filled.Favorite,
        "FavoriteBorder" to Icons.Filled.FavoriteBorder,
        "Info" to Icons.Filled.Info,
        "Menu" to Icons.Filled.Menu,
        "MoreVert" to Icons.Filled.MoreVert,
        "Person" to Icons.Filled.Person,
        "Search" to Icons.Filled.Search,
        "Settings" to Icons.Filled.Settings,
        "Share" to Icons.Filled.Share,
        "Star" to Icons.Filled.Star,
        "ThumbUp" to Icons.Filled.ThumbUp,
        "Warning" to Icons.Filled.Warning,
        "Refresh" to Icons.Filled.Refresh,
        "PlayArrow" to Icons.Filled.PlayArrow,
        "Pause" to Icons.Filled.Pause,
        "Email" to Icons.Filled.Email,
        "Lock" to Icons.Filled.Lock,
        "Phone" to Icons.Filled.Phone,
        "Send" to Icons.AutoMirrored.Filled.Send,
        "Build" to Icons.Filled.Build,
        "Face" to Icons.Filled.Face,
        "Place" to Icons.Filled.Place,
        "FilterList" to Icons.Filled.FilterList,
        "Sort" to Icons.AutoMirrored.Filled.Sort,
        "ArrowDropDown" to Icons.Filled.ArrowDropDown,
        "KeyboardArrowDown" to Icons.Filled.KeyboardArrowDown,
        "KeyboardArrowUp" to Icons.Filled.KeyboardArrowUp,
    )

    /** 获取 Icon 名称列表（供 Lua 端查询） */
    fun getIconNames(): List<String> = iconMap.keys.toList()
}

@Composable
private fun IconRenderer(node: ComposeNode) {
    val name = node.props["name"] as? String ?: "Info"
    val icon = IconComponent.iconMap[name] ?: Icons.Filled.Info
    val tint = node.props["color"]?.let { colorToColor(it) } ?: Color.Unspecified
    val size = (node.props["size"] as? Number)?.toFloat() ?: 24f

    Icon(
        imageVector = icon,
        contentDescription = node.props["contentDescription"] as? String ?: name,
        modifier = ComposeRenderer.resolveModifier(node).size(size.dp),
        tint = tint
    )
}

/** 颜色转换辅助函数 */
private fun colorToColor(value: Any?): Color = when (value) {
    is Long -> Color(value.toInt())
    is Double -> Color(value.toLong().toInt())
    is Int -> Color(value)
    is Number -> Color(value.toInt())
    else -> Color.Unspecified
}