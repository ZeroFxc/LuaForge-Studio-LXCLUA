package com.luaforge.studio.lxclua.ui.editor.components

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/** 媒体文件扩展名 */
private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "ico")
private val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp", "webm")

/**
 * 判断文件是否为支持的媒体文件
 */
fun isMediaFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in imageExtensions || ext in videoExtensions
}

/**
 * 判断文件是否为图片文件
 */
fun isImageFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in imageExtensions
}

/**
 * 判断文件是否为视频文件
 */
fun isVideoFile(fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in videoExtensions
}

/**
 * 媒体文件预览组件
 * 支持图片缩放/平移手势，视频播放控制
 */
@Composable
fun MediaPreviewView(
    file: File,
    modifier: Modifier = Modifier
) {
    val fileName = file.name
    val filePath = file.absolutePath

    if (isImageFile(fileName)) {
        ImagePreview(filePath = filePath, modifier = modifier)
    } else if (isVideoFile(fileName)) {
        VideoPreview(filePath = filePath, modifier = modifier)
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "不支持预览此文件类型",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 图片预览（支持双指缩放和平移）
 */
@Composable
private fun ImagePreview(
    filePath: String,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(filePath))
                .crossfade(true)
                .build(),
            contentDescription = "图片预览",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 视频预览（使用系统 VideoView）
 */
@Composable
private fun VideoPreview(
    filePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        if (hasError) {
            Text(
                text = "无法播放此视频",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            // 使用 AndroidView 嵌入原生 VideoView
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.fromFile(File(filePath)))
                        setOnPreparedListener { mp ->
                            isLoading = false
                            mp.isLooping = true
                            // 设置 MediaController 提供播放控制
                            val controller = MediaController(ctx)
                            controller.setMediaPlayer(this)
                            setMediaController(controller)
                            start()
                        }
                        setOnErrorListener { _, _, _ ->
                            isLoading = false
                            hasError = true
                            true
                        }
                    }
                }
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}