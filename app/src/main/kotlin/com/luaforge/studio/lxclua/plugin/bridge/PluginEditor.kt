package com.luaforge.studio.lxclua.plugin.bridge

import android.os.Handler
import android.os.Looper
import com.luaforge.studio.lxclua.plugin.PluginManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * 编辑器操作 API
 * 
 * 使用方式: plugin.editor.getText()
 */
class PluginEditor {

    /**
     * 获取当前活动编辑器信息（含文件路径、内容、语言、光标位置等）
     * 返回 Map，包含以下键:
     *   - filePath: 文件路径 (string) 或 null
     *   - content: 编辑器内容 (string)
     *   - language: 语言类型 (string) 或 null
     *   - cursorLine: 光标行 (int)
     *   - cursorColumn: 光标列 (int)
     *   - isModified: 是否已修改 (boolean)
     *   - editorActive: 编辑器是否处于活动状态 (boolean)
     *   - fileOpen: 是否有文件打开 (boolean)
     */
    fun getActiveEditorInfo(): Map<String, Any?> {
        val vm = PluginManager.activeViewModel
        if (vm == null) {
            return mapOf(
                "editorActive" to false,
                "fileOpen" to false,
                "filePath" to null,
                "content" to "",
                "language" to null,
                "cursorLine" to 0,
                "cursorColumn" to 0,
                "isModified" to false
            )
        }
        val state = vm.activeFileState
        if (state == null) {
            return mapOf(
                "editorActive" to true,
                "fileOpen" to false,
                "filePath" to null,
                "content" to "",
                "language" to null,
                "cursorLine" to 0,
                "cursorColumn" to 0,
                "isModified" to false
            )
        }
        // 使用 getText() 获取内容（优先 state.content，mutation 后已同步更新）
        val editor = vm.getActiveEditor()
        val content = getText() ?: ""
        val cursor = editor?.cursor
        return mapOf(
            "editorActive" to true,
            "fileOpen" to true,
            "filePath" to state.file.absolutePath,
            "content" to content,
            "language" to getLanguage(),
            "cursorLine" to (cursor?.leftLine ?: 0),
            "cursorColumn" to (cursor?.leftColumn ?: 0),
            "isModified" to state.isModified
        )
    }
    
    /**
     * 获取当前活动文件路径
     */
    fun getActiveFile(): String? {
        return PluginManager.activeViewModel?.activeFileState?.file?.absolutePath
    }
    
    /**
     * 获取当前文件内容（优先从 state.content 获取，避免 UI 异步更新导致的脏读）
     */
    fun getText(): String? {
        val vm = PluginManager.activeViewModel ?: return null
        val state = vm.activeFileState
        // state.content 在每次 mutation 后同步更新，比 editor.text 更可靠
        return state?.content ?: vm.getActiveEditor()?.text?.toString()
    }

    /**
     * 同步更新 state.content（用于 mutation 方法在 UI 更新前同步记录新内容）
     */
    private fun syncStateContent(newContent: String) {
        PluginManager.activeViewModel?.activeFileState?.let { state ->
            state.content = newContent
        }
    }
    
    /**
     * 设置当前文件内容（同步更新 CodeEditor 文本控件和 state）
     */
    fun setText(text: String) {
        PluginManager.activeViewModel?.let { vm ->
            // 更新 state.content
            vm.activeFileState?.onContentChanged(text)
            vm.activeFileState?.let { state ->
                state.content = text
            }
            // 更新 CodeEditor 的实际文本控件（否则 getLineCount/getActiveEditorInfo 读到的还是旧内容）
            val editor = vm.getActiveEditor()
            if (editor != null) {
                val content = editor.text
                val lineCount = content.lineCount
                runOnUiThread {
                    if (lineCount > 0) {
                        val lastCol = content.getColumnCount(lineCount - 1)
                        content.replace(0, 0, lineCount - 1, lastCol, text)
                    } else {
                        content.insert(0, 0, text)
                    }
                }
            }
        }
    }
    
    /**
     * 在光标位置插入文本（自动切换到主线程执行）
     */
    fun insertText(text: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            PluginManager.activeViewModel?.insertSymbolToCorrectEditor(text)
        } else {
            Handler(Looper.getMainLooper()).post {
                PluginManager.activeViewModel?.insertSymbolToCorrectEditor(text)
            }
        }
    }
    
    /**
     * 获取选中的文本
     */
    fun getSelectedText(): String? {
        val editor = PluginManager.activeViewModel?.getActiveEditor() ?: return null
        val cursor = editor.cursor
        val leftIndex = cursor.left
        val rightIndex = cursor.right
        return if (leftIndex != rightIndex) {
            editor.text.substring(leftIndex, rightIndex)
        } else {
            ""
        }
    }
    
    /**
     * 获取光标位置 [行, 列]
     */
    fun getCursorPosition(): IntArray? {
        val editor = PluginManager.activeViewModel?.getActiveEditor() ?: return null
        val cursor = editor.cursor
        return intArrayOf(cursor.leftLine, cursor.leftColumn)
    }
    
    /**
     * 跳转到指定行
     */
    fun gotoLine(line: Int) {
        PluginManager.activeViewModel?.getActiveEditor()?.jumpToLine(line.coerceAtLeast(0))
    }
    
    /**
     * 跳转到指定位置
     */
    fun gotoPosition(line: Int, column: Int) {
        PluginManager.activeViewModel?.getActiveEditor()?.setSelectionRegion(line, column, line, column)
    }
    
    /**
     * 打开文件
     */
    fun openFile(filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            PluginManager.activeViewModel?.openFile(file)
        }
    }
    
    /**
     * 保存当前文件
     */
    fun saveFile() {
        kotlinx.coroutines.GlobalScope.launch {
            PluginManager.activeViewModel?.saveCurrentFileSilently()
        }
    }
    
    /**
     * 保存所有文件
     */
    fun saveAll() {
        kotlinx.coroutines.GlobalScope.launch {
            PluginManager.activeViewModel?.saveAllFilesSilently()
        }
    }
    
    /**
     * 撤销操作
     */
    fun undo() {
        PluginManager.activeViewModel?.getActiveEditor()?.undo()
    }
    
    /**
     * 重做操作
     */
    fun redo() {
        PluginManager.activeViewModel?.getActiveEditor()?.redo()
    }
    
    /**
     * 获取所有打开的文件路径
     */
    fun getOpenFiles(): Array<String>? {
        return PluginManager.activeViewModel?.openFiles?.map { it.file.absolutePath }?.toTypedArray()
    }
    
    /**
     * 关闭当前文件
     */
    fun closeFile() {
        val index = PluginManager.activeViewModel?.activeFileIndex ?: -1
        if (index >= 0) {
            PluginManager.activeViewModel?.closeFile(index)
        }
    }
    
    /**
     * 获取当前文件的语言类型（根据扩展名判断）
     * @return 语言标识，如 "lua", "java", "kotlin", "xml", "json", "txt" 等
     */
    fun getLanguage(): String? {
        val filePath = getActiveFile() ?: return null
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "lua" -> "lua"
            "java" -> "java"
            "kt", "kts" -> "kotlin"
            "xml" -> "xml"
            "json" -> "json"
            "txt" -> "txt"
            "md" -> "markdown"
            "js" -> "javascript"
            "ts" -> "typescript"
            "py" -> "python"
            "cpp", "hpp", "cc", "hh" -> "cpp"
            "c", "h" -> "c"
            "cs" -> "csharp"
            "go" -> "go"
            "rs" -> "rust"
            "swift" -> "swift"
            "dart" -> "dart"
            "html" -> "html"
            "css" -> "css"
            "gradle" -> "gradle"
            "properties" -> "properties"
            "yml", "yaml" -> "yaml"
            else -> extension.ifEmpty { "unknown" }
        }
    }
    
    /**
     * 获取当前文件的扩展名（不含点）
     */
    fun getFileExtension(): String? {
        val filePath = getActiveFile() ?: return null
        return filePath.substringAfterLast('.', "").ifEmpty { null }
    }
    
    /**
     * 检查当前文件是否为指定语言
     * @param language 语言标识
     * @return 是否匹配
     */
    fun isLanguage(language: String): Boolean {
        return getLanguage()?.equals(language, ignoreCase = true) ?: false
    }
    
    /**
     * 获取所有支持的语言列表
     */
    fun getSupportedLanguages(): Array<String> {
        return arrayOf(
            "lua", "java", "kotlin", "xml", "json", "txt", "markdown",
            "javascript", "typescript", "python", "cpp", "c", "csharp",
            "go", "rust", "swift", "dart", "html", "css", "gradle",
            "properties", "yaml"
        )
    }

    // ========== 精确行数编辑 ==========

    /** 获取编辑器总行数 */
    fun getLineCount(): Int {
        val editor = PluginManager.activeViewModel?.getActiveEditor() ?: return 0
        return editor.text.lineCount
    }

    /** 获取指定行的内容（行号从 0 开始）
     *  @param line 行号 (0-based)
     *  @return 该行内容，或 null 如果行号无效 */
    fun getLine(line: Int): String? {
        val content = PluginManager.activeViewModel?.getActiveEditor()?.text ?: return null
        if (line < 0 || line >= content.lineCount) return null
        val start = content.getCharIndex(line, 0)
        val end = content.getCharIndex(line, content.getColumnCount(line))
        return content.substring(start, end).trimEnd('\n', '\r')
    }

    /** 替换指定行内容（行号从 0 开始）
     *  @param line 行号 (0-based)
     *  @param text 新内容（不含换行符） */
    fun editLine(line: Int, text: String) {
        val content = PluginManager.activeViewModel?.getActiveEditor()?.text ?: return
        if (line < 0 || line >= content.lineCount) return
        // 同步更新 state.content（在 UI 线程更新前，确保 getText() 读到最新内容）
        val currentContent = getText() ?: return
        val lines = currentContent.split("\n").toMutableList()
        if (line < lines.size) {
            lines[line] = text
            syncStateContent(lines.joinToString("\n"))
        }
        val lineEnd = content.getColumnCount(line)
        runOnUiThread {
            content.replace(line, 0, line, lineEnd, text)
        }
    }

    /** 在指定行前插入一行（行号从 0 开始）
     *  @param line 行号 (0-based)，在这行之前插入
     *  @param text 插入的内容（不含换行符） */
    fun insertLine(line: Int, text: String) {
        val content = PluginManager.activeViewModel?.getActiveEditor()?.text ?: return
        if (line < 0) return
        // 同步更新 state.content
        val currentContent = getText() ?: return
        val lines = currentContent.split("\n").toMutableList()
        if (line >= lines.size) {
            // 用空行填充到目标行，再插入
            while (lines.size <= line) {
                lines.add("")
            }
        }
        lines.add(line, text)
        syncStateContent(lines.joinToString("\n"))
        runOnUiThread {
            if (line >= content.lineCount) {
                // 用空行填充到目标行，再插入
                var lastLine = content.lineCount - 1
                var lastCol = content.getColumnCount(lastLine)
                while (content.lineCount <= line) {
                    content.insert(lastLine, lastCol, "\n")
                    lastLine = content.lineCount - 1
                    lastCol = content.getColumnCount(lastLine)
                }
                content.insert(line, 0, "$text\n")
            } else {
                content.insert(line, 0, "$text\n")
            }
        }
    }

    /** 删除指定行（行号从 0 开始）
     *  @param line 行号 (0-based) */
    fun deleteLine(line: Int) {
        val content = PluginManager.activeViewModel?.getActiveEditor()?.text ?: return
        if (line < 0 || line >= content.lineCount) return
        // 同步更新 state.content
        val currentContent = getText() ?: return
        val lines = currentContent.split("\n").toMutableList()
        if (line < lines.size) {
            lines.removeAt(line)
            syncStateContent(lines.joinToString("\n"))
        }
        runOnUiThread {
            content.delete(line, 0, line + 1, 0)
        }
    }

    /** 替换指定行范围的内容
     *  @param startLine 起始行号 (0-based, 包含)
     *  @param endLine 结束行号 (0-based, 包含)
     *  @param text 替换内容（可含换行符） */
    fun replaceLines(startLine: Int, endLine: Int, text: String) {
        val content = PluginManager.activeViewModel?.getActiveEditor()?.text ?: return
        if (startLine < 0 || endLine >= content.lineCount || startLine > endLine) return
        // 同步更新 state.content
        val currentContent = getText() ?: return
        val lines = currentContent.split("\n").toMutableList()
        if (endLine < lines.size) {
            val newLines = text.split("\n")
            for (i in endLine downTo startLine) {
                lines.removeAt(i)
            }
            lines.addAll(startLine, newLines)
            syncStateContent(lines.joinToString("\n"))
        }
        runOnUiThread {
            val endCol = content.getColumnCount(endLine)
            content.replace(startLine, 0, endLine, endCol, text)
        }
    }

    /** 获取指定行范围的内容
     *  @param startLine 起始行号 (0-based, 包含)
     *  @param endLine 结束行号 (0-based, 包含)
     *  @return 该范围内容，或 null */
    fun getLines(startLine: Int, endLine: Int): String? {
        val content = PluginManager.activeViewModel?.getActiveEditor()?.text ?: return null
        if (startLine < 0 || endLine >= content.lineCount || startLine > endLine) return null
        val start = content.getCharIndex(startLine, 0)
        val end = content.getCharIndex(endLine, content.getColumnCount(endLine))
        return content.substring(start, end)
    }

    /** 在 UI 线程执行操作 */
    private fun runOnUiThread(action: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            action()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(action)
        }
    }
}