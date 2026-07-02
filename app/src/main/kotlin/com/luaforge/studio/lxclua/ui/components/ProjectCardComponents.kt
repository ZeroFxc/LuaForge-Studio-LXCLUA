package com.luaforge.studio.lxclua.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.luaforge.studio.lxclua.R
import com.luaforge.studio.lxclua.ui.settings.ProjectTag
import java.io.File
import java.util.Locale

/**
 * 判断颜色是否为深色（基于亮度公式）
 * @return true表示深色背景，应使用白色文字；false表示浅色背景，应使用黑色文字
 */
fun Color.isDark(): Boolean {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return luminance < 0.5f
}

/**
 * 搜索高亮文本组件
 * 将text中与highlight匹配的片段（不区分大小写）高亮显示
 * @param text 原始文本
 * @param highlight 要高亮的搜索词
 * @param style 基础文本样式
 * @param color 高亮颜色
 */
@Composable
fun HighlightedText(
    text: String,
    highlight: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color
) {
    if (highlight.isEmpty()) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    val annotatedString = remember(text, highlight, color) {
        buildAnnotatedString {
            val lowerText = text.lowercase(Locale.getDefault())
            val lowerHighlight = highlight.lowercase(Locale.getDefault())
            var startIndex = 0
            while (startIndex < text.length) {
                val index = lowerText.indexOf(lowerHighlight, startIndex)
                if (index == -1) {
                    append(text.substring(startIndex))
                    break
                }
                if (index > startIndex) {
                    append(text.substring(startIndex, index))
                }
                withStyle(
                    SpanStyle(
                        background = color.copy(alpha = 0.2f),
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(text.substring(index, index + highlight.length))
                }
                startIndex = index + highlight.length
            }
        }
    }

    Text(
        text = annotatedString,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * 左侧小图标区域渲染（封面模式下显示的小图标）
 * - 有项目icon.png：显示项目图标
 * - 无icon：显示文件夹图标
 * - 颜色根据封面自适应（白/黑）或主题primary色
 * @param hasIcon 是否存在项目自有图标
 * @param iconFile 图标文件
 * @param iconSize 图标容器大小
 * @param iconInnerSize 内部图标大小
 * @param shape 裁剪形状
 * @param isCoverMode 是否封面模式（影响颜色）
 * @param onCoverColor 封面模式下的图标颜色
 * @param colorScheme 颜色方案
 */
@Composable
internal fun SmallIconArea(
    hasIcon: Boolean,
    iconFile: File,
    iconSize: Dp,
    iconInnerSize: Dp,
    shape: RoundedCornerShape,
    isCoverMode: Boolean,
    onCoverColor: Color,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    // 图标颜色
    val iconTint = if (isCoverMode) onCoverColor else colorScheme.primary
    // 背景：封面模式下给一个半透明圆底增强对比度；非封面模式下浅色底
    val bgColor = if (isCoverMode) {
        onCoverColor.copy(alpha = 0.15f)
    } else {
        colorScheme.primary.copy(alpha = 0.1f)
    }

    Box(
        modifier = Modifier
            .size(iconSize)
            .clip(shape)
            .background(bgColor, shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            // 有项目自有图标：显示项目图标
            hasIcon -> {
                SubcomposeAsyncImage(
                    model = iconFile,
                    contentDescription = stringResource(R.string.cd_project_icon),
                    modifier = Modifier.size(iconInnerSize),
                    contentScale = ContentScale.Crop,
                    error = {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(iconInnerSize)
                        )
                    }
                )
            }
            // 无图标：显示默认文件夹图标
            else -> {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(iconInnerSize)
                )
            }
        }
    }
}

/**
 * 标签行渲染
 * 显示项目标签，最多显示前3个，超过显示"+N"
 * @param tags 标签列表
 * @param isCoverMode 是否封面模式（标签背景色用半透明白/黑）
 * @param onCoverColor 封面模式下的文字颜色
 */
@Composable
internal fun TagsRow(
    tags: List<ProjectTag>,
    isCoverMode: Boolean = false,
    onCoverColor: Color = Color.Unspecified
) {
    if (tags.isEmpty()) return

    val displayTags = tags.take(3)
    val remaining = tags.size - 3

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), enabled = false),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        displayTags.forEach { tag ->
            if (isCoverMode) {
                // 封面模式：使用半透明背景+封面文字色
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = onCoverColor.copy(alpha = 0.2f),
                    contentColor = onCoverColor
                ) {
                    Text(
                        text = tag.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(tag.color.toInt()).copy(alpha = 0.15f),
                    contentColor = Color(tag.color.toInt())
                ) {
                    Text(
                        text = tag.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
        if (remaining > 0) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isCoverMode) onCoverColor.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                contentColor = if (isCoverMode) onCoverColor else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = "+$remaining",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 默认项目图标
 * @param size 图标容器大小
 * @param innerSize 内部图标大小
 */
@Composable
fun DefaultProjectIcon(
    size: Dp = 48.dp,
    innerSize: Dp = 24.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                MaterialTheme.shapes.medium
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = stringResource(R.string.cd_project_folder),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(innerSize)
        )
    }
}
