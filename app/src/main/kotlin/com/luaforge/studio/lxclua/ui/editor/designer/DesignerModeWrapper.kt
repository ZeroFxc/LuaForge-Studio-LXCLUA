package com.luaforge.studio.lxclua.ui.editor.designer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luaforge.studio.lxclua.ui.editor.SwipeDirection
import com.luaforge.studio.lxclua.ui.editor.components.CodeEditorView
import com.luaforge.studio.lxclua.ui.editor.components.MediaPreviewView
import com.luaforge.studio.lxclua.ui.editor.components.isMediaFile
import com.luaforge.studio.lxclua.ui.editor.viewmodel.CodeEditorState
import com.luaforge.studio.lxclua.ui.editor.viewmodel.EditorViewModel

/**
 * 设计器模式包装组件
 */
@Composable
fun DesignerModeWrapper(
    state: CodeEditorState,
    viewModel: EditorViewModel,
    isActiveFile: Boolean,
    expansionRatio: Float,
    onSwipe: ((SwipeDirection) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val isDesignerFile = remember(state.file.name) {
        state.file.name.endsWith(".lua", ignoreCase = true) ||
                state.file.name.endsWith(".aly", ignoreCase = true)
    }

    // 媒体文件（图片/视频）直接预览
    if (isMediaFile(state.file.name)) {
        MediaPreviewView(
            file = state.file,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (!isDesignerFile) {
        CodeEditorView(
            modifier = modifier.fillMaxSize(),
            state = state,
            viewModel = viewModel,
            isActiveFile = isActiveFile,
            expansionRatio = expansionRatio,
            onSwipe = onSwipe
        )
        return
    }

    val designerVM = rememberDesignerState()
    var currentCode by remember(state.file.absolutePath) { mutableStateOf(state.content) }

    DisposableEffect(designerVM) {
        onDispose {
            designerVM.destroy()
        }
    }

    LaunchedEffect(viewModel.textChangeVersion) {
        if (!viewModel.isUpdatingFromDesigner) {
            currentCode = state.content
        }
    }

    val getLatestEditorText: () -> String = { currentCode }

    val onCodeChanged: (String) -> Unit = { newCode ->
        currentCode = newCode
        viewModel.replaceEditorContent(state.file, newCode)
    }

    CompositionLocalProvider(LocalDesignerState provides designerVM) {
        Box(modifier = modifier.fillMaxSize()) {
            when (designerVM.designerMode) {
                DesignerMode.OFF -> {
                    CodeEditorView(
                        modifier = Modifier.fillMaxSize(),
                        state = state,
                        viewModel = viewModel,
                        isActiveFile = isActiveFile,
                        expansionRatio = expansionRatio,
                        onSwipe = onSwipe
                    )

                    AnimatedVisibility(
                        visible = false, // 设计器(Beta)入口已禁用
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        SmallFloatingActionButton(
                            onClick = { designerVM.setMode(DesignerMode.DESIGN_ONLY) },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.DesignServices,
                                contentDescription = "打开设计器"
                            )
                        }
                    }
                }

                DesignerMode.CODE_DESIGN -> {
                    SplittablePane(
                        firstPane = {
                            CodeEditorView(
                                modifier = Modifier.fillMaxSize(),
                                state = state,
                                viewModel = viewModel,
                                isActiveFile = isActiveFile,
                                expansionRatio = expansionRatio,
                                onSwipe = onSwipe
                            )
                        },
                        secondPane = {
                            DesignerHost(
                                currentCode = currentCode,
                                onCodeChanged = onCodeChanged,
                                onRequestEditorText = getLatestEditorText,
                                isActive = isActiveFile,
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        splitRatio = designerVM.splitRatio,
                        onSplitRatioChange = { designerVM.setSplitRatio(it) },
                        isVertical = rememberSplitOrientation(),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                DesignerMode.DESIGN_ONLY -> {
                    DesignerHost(
                        currentCode = currentCode,
                        onCodeChanged = onCodeChanged,
                        onRequestEditorText = getLatestEditorText,
                        isActive = isActiveFile,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
