package com.nirithy.lxclua

import android.R
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.myopicmobile.textwarrior.android.FreeScrollingTextField
import com.myopicmobile.textwarrior.android.TrackpadNavigationMethod
import com.myopicmobile.textwarrior.android.YoyoNavigationMethod
import com.myopicmobile.textwarrior.common.ColorScheme
import com.myopicmobile.textwarrior.common.ColorSchemeDark
import com.myopicmobile.textwarrior.common.ColorSchemeLight
import com.myopicmobile.textwarrior.common.Document
import com.myopicmobile.textwarrior.common.DocumentProvider
import com.myopicmobile.textwarrior.common.LanguageLua
import com.myopicmobile.textwarrior.common.Lexer
import com.myopicmobile.textwarrior.common.LinearSearchStrategy
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class LuaEditor @SuppressLint("StaticFieldLeak") constructor(private val mContext: Context) :
    FreeScrollingTextField(
        mContext
    ) {
    private val _inputtingDoc: Document? = null

    private var _isWordWrap = false

    var filePath: String? = null
        private set

    private val fontDir: String? = LuaApplication.instance!!.resolveLuaExtDir("fonts")
    private val libDir: String? =
        LuaApplication.instance!!.resolveLuaExtPath("android.jar")

    private var _index = 0
    private var finder: LinearSearchStrategy? = null
    private var idx = 0
    private var mKeyword: String? = null

    init {
        setTypeface(Typeface.MONOSPACE)
        val df = File(fontDir, "default.ttf")
        if (df.exists()) setTypeface(Typeface.createFromFile(df))
        val bf = File(fontDir, "bold.ttf")
        if (bf.exists()) setBoldTypeface(Typeface.createFromFile(bf))
        val tf = File(fontDir, "italic.ttf")
        if (tf.exists()) setItalicTypeface(Typeface.createFromFile(tf))
        val dm = mContext.resources.displayMetrics

        val size = TypedValue.applyDimension(2, BASE_TEXT_SIZE_PIXELS.toFloat(), dm)
        setTextSize(size.toInt())
        setShowLineNumbers(true)
        setHighlightCurrentRow(true)
        setWordWrap(false)
        setAutoIndentWidth(2)
        Lexer.setLanguage(LanguageLua.getInstance())
        if (isAccessibilityEnabled()) setNavigationMethod(TrackpadNavigationMethod(this))
        else setNavigationMethod(YoyoNavigationMethod(this))
        val array = mContext.theme.obtainStyledAttributes(
            intArrayOf(
                R.attr.colorBackground,
                R.attr.textColorPrimary,
                R.attr.textColorHighlight,
            )
        )
        val backgroundColor = array.getColor(0, 0xFF00FF)
        val textColor = array.getColor(1, 0xFF00FF)
        val textColorHighlight = array.getColor(2, 0xFF00FF)
        array.recycle()
        setTextColor(textColor)
        setTextHighlightColor(textColorHighlight)
        /*
        new AsyncTask<String, String, String[]>(){
            @Override
            protected String[] doInBackground(String... strings) {
                String[] cls = LuaUtil.getAllName(context, libDir);
                for (int i = 0; i < cls.length; i++) {
                    String cl = cls[i];
                    int d = cl.lastIndexOf("$");
                    if(d<0)
                      d = cl.lastIndexOf(".");
                    if(d>0)
                        cls[i]=cl.substring(d);
                }
                 return cls;
            }

            @Override
            protected void onPostExecute(String[] cls) {
                addNames(cls);
            }
        }.execute();*/
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // TODO: Implement this method
        super.onLayout(changed, left, top, right, bottom)
        if (_index != 0 && right > 0) {
            moveCaret(_index)
            _index = 0
        }
    }

    fun setDark(isDark: Boolean) {
        if (isDark) setColorScheme(ColorSchemeDark())
        else setColorScheme(ColorSchemeLight())
    }

    fun addNames(names: Array<String?>) {
        val lang = Lexer.getLanguage() as LanguageLua
        val old = lang.getNames()
        val news = arrayOfNulls<String>(old.size + names.size)
        System.arraycopy(old, 0, news, 0, old.size)
        System.arraycopy(names, 0, news, old.size, names.size)
        lang.setNames(news)
        Lexer.setLanguage(lang)
        respan()
        invalidate()
    }

    fun addPackage(pkg: String?, names: Array<String?>?) {
        val lang = Lexer.getLanguage() as LanguageLua
        lang.addBasePackage(pkg, names)
        Lexer.setLanguage(lang)
        respan()
        invalidate()
    }

    fun removePackage(pkg: String?) {
        val lang = Lexer.getLanguage() as LanguageLua
        lang.removeBasePackage(pkg)
        Lexer.setLanguage(lang)
        respan()
        invalidate()
    }

    fun setPanelBackgroundColor(color: Int) {
        // TODO: Implement this method
        _autoCompletePanel.setBackgroundColor(color)
    }

    fun setPanelTextColor(color: Int) {
        // TODO: Implement this method
        _autoCompletePanel.setTextColor(color)
    }

    fun setKeywordColor(color: Int) {
        getColorScheme().setColor(ColorScheme.Colorable.KEYWORD, color)
    }

    fun setUserwordColor(color: Int) {
        getColorScheme().setColor(ColorScheme.Colorable.LITERAL, color)
    }

    fun setBasewordColor(color: Int) {
        getColorScheme().setColor(ColorScheme.Colorable.NAME, color)
    }

    fun setStringColor(color: Int) {
        getColorScheme().setColor(ColorScheme.Colorable.STRING, color)
    }

    fun setCommentColor(color: Int) {
        getColorScheme().setColor(ColorScheme.Colorable.COMMENT, color)
    }

    override fun setBackgroundColor(color: Int) {
        getColorScheme().setColor(ColorScheme.Colorable.BACKGROUND, color)
    }

    fun setTextColor(color: Int) {
        getColorScheme().setColor(ColorScheme.Colorable.FOREGROUND, color)
    }

    fun setTextHighlightColor(color: Int) {
        getColorScheme().setColor(ColorScheme.Colorable.SELECTION_BACKGROUND, color)
    }

    val selectedText: String
        get() =// TODO: Implement this method
            _hDoc.subSequence(getSelectionStart(), getSelectionEnd() - getSelectionStart())
                .toString()

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val filteredMetaState = event.getMetaState() and KeyEvent.META_CTRL_MASK.inv()
        if (KeyEvent.metaStateHasNoModifiers(filteredMetaState)) {
            when (keyCode) {
                KeyEvent.KEYCODE_A -> {
                    selectAll()
                    return true
                }

                KeyEvent.KEYCODE_X -> {
                    cut()
                    return true
                }

                KeyEvent.KEYCODE_C -> {
                    copy()
                    return true
                }

                KeyEvent.KEYCODE_V -> {
                    paste()
                    return true
                }

                KeyEvent.KEYCODE_L -> {
                    format()
                    return true
                }

                KeyEvent.KEYCODE_S -> {
                    search()
                    return true
                }

                KeyEvent.KEYCODE_G -> {
                    gotoLine()
                    return true
                }
            }
        }
        return super.onKeyShortcut(keyCode, event)
    }

    fun gotoLine() {
        // TODO: Implement this method
        startGotoMode()
    }

    fun search() {
        // TODO: Implement this method
        startFindMode()
    }

    fun startGotoMode() {
        // TODO: Implement this method
        startActionMode(object : ActionMode.Callback {
            private var idx = 0

            private var edit: EditText? = null

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                // TODO: Implement this method
                mode.setTitle("转到")
                mode.setSubtitle(null)

                edit = object : EditText(mContext) {
                    public override fun onTextChanged(
                        s: CharSequence,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        if (s.length > 0) {
                            idx = 0
                            _gotoLine()
                        }
                    }
                }

                edit!!.setSingleLine(true)
                edit!!.setInputType(2)
                edit!!.setImeOptions(2)
                edit!!.setOnEditorActionListener(object : TextView.OnEditorActionListener {
                    override fun onEditorAction(p1: TextView?, p2: Int, p3: KeyEvent?): Boolean {
                        // TODO: Implement this method
                        _gotoLine()
                        return true
                    }
                })
                edit!!.setLayoutParams(RadioGroup.LayoutParams(getWidth() / 3, -1))
                menu.add(0, 1, 0, "").setActionView(edit)
                menu.add(0, 2, 0, mContext.getString(R.string.ok))
                edit!!.requestFocus()
                return true
            }

            fun _gotoLine() {
                val s = edit!!.getText().toString()
                if (s.isEmpty()) return

                var l = s.toInt()
                if (l > _hDoc.getRowCount()) {
                    l = _hDoc.getRowCount()
                }
                gotoLine(l)
                // TODO: Implement this method
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                // TODO: Implement this method
                return false
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem): Boolean {
                // TODO: Implement this method
                when (item.getItemId()) {
                    1 -> {}
                    2 -> _gotoLine()
                }
                return false
            }

            override fun onDestroyActionMode(p1: ActionMode?) {
                // TODO: Implement this method
            }
        })
    }

    fun startFindMode() {
        // TODO: Implement this method
        startActionMode(object : ActionMode.Callback {
            private var finder: LinearSearchStrategy? = null

            private var idx = 0

            private var edit: EditText? = null

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                // TODO: Implement this method
                mode.setTitle("搜索")
                mode.setSubtitle(null)

                edit = object : EditText(mContext) {
                    public override fun onTextChanged(
                        s: CharSequence,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        if (s.length > 0) {
                            idx = 0
                            findNext()
                        }
                    }
                }
                edit!!.setSingleLine(true)
                edit!!.setImeOptions(3)
                edit!!.setOnEditorActionListener(object : TextView.OnEditorActionListener {
                    override fun onEditorAction(p1: TextView?, p2: Int, p3: KeyEvent?): Boolean {
                        // TODO: Implement this method
                        findNext()
                        return true
                    }
                })
                edit!!.setLayoutParams(RadioGroup.LayoutParams(getWidth() / 3, -1))
                menu.add(0, 1, 0, "").setActionView(edit)
                menu.add(0, 2, 0, mContext.getString(R.string.search_go))
                edit!!.requestFocus()
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                // TODO: Implement this method
                return false
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem): Boolean {
                // TODO: Implement this method
                when (item.getItemId()) {
                    1 -> {}
                    2 -> findNext()
                }
                return false
            }

            fun findNext() {
                // TODO: Implement this method
                finder = LinearSearchStrategy()
                val kw = edit!!.getText().toString()
                if (kw.isEmpty()) {
                    selectText(false)
                    return
                }
                idx = finder!!.find(this@LuaEditor.text, kw, idx, this@LuaEditor.text.length, false, false)
                if (idx == -1) {
                    selectText(false)
                    Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show()
                    idx = 0
                    return
                }
                setSelection(idx, edit!!.getText().length)
                idx += edit!!.getText().length
                moveCaret(idx)
            }

            override fun onDestroyActionMode(p1: ActionMode?) {
                // TODO: Implement this method
            }
        })
    }

    override fun setWordWrap(enable: Boolean) {
        // TODO: Implement this method
        _isWordWrap = enable
        super.setWordWrap(enable)
    }

    var text: DocumentProvider
        get() = createDocumentProvider()
        set(c) {
            //TextBuffer text=new TextBuffer();
            val doc =
                Document(this)
            doc.setWordWrap(_isWordWrap)
            doc.setText(c)
            setDocumentProvider(DocumentProvider(doc))
            //doc.analyzeWordWrap();
        }

    fun insert(idx: Int, text: String?) {
        selectText(false)
        moveCaret(idx)
        paste(text)
        //_hDoc.insert(idx,text);
    }

    fun setText(c: CharSequence, isRep: Boolean) {
        replaceText(0, getLength() - 1, c.toString())
    }

    fun setSelection(index: Int) {
        selectText(false)
        if (!hasLayout()) moveCaret(index)
        else _index = index
    }

    fun gotoLine(line: Int) {
        var line = line
        if (line > _hDoc.getRowCount()) {
            line = _hDoc.getRowCount()
        }
        val i = this.text.getLineOffset(line - 1)
        setSelection(i)
    }

    fun undo() {
        val doc = createDocumentProvider()
        val newPosition = doc.undo()

        if (newPosition >= 0) {
            setEdited(true)
            respan()
            selectText(false)
            moveCaret(newPosition)
            invalidate()
        }
    }

    fun redo() {
        val doc = createDocumentProvider()
        val newPosition = doc.redo()

        if (newPosition >= 0) {
            setEdited(true)

            respan()
            selectText(false)
            moveCaret(newPosition)
            invalidate()
        }
    }

    @Throws(IOException::class)
    fun open(filename: String?) {
        this.filePath = filename
        val reader = BufferedReader(FileReader(filename))
        val buf = StringBuilder()
        var line: String?
        while ((reader.readLine().also { line = it }) != null) buf.append(line).append("\n")
        if (buf.length > 1) buf.setLength(buf.length - 1)
        val doc = Document(this)
        doc.setWordWrap(_isWordWrap)
        doc.setText(buf.toString())
        setDocumentProvider(DocumentProvider(doc))
        /*
        File inputFile = new File(filename);
        _inputtingDoc = new Document(this);
        _inputtingDoc.setWordWrap(this.isWordWrap());
        ReadTask _taskRead = new ReadTask(this, inputFile);
        _taskRead.start();*/
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun save(filename: String? = this.filePath): Boolean {
        if (filename == null) return true
        val outputFile = File(filename)

        if (outputFile.exists()) {
            if (!outputFile.canWrite()) {
                return false
            }
        }

        val writer = BufferedWriter(FileWriter(filename))
        writer.write(this.text.toString())
        writer.close()
        return true
    }

    fun findNext(keyword: String): Boolean {
        if (keyword != mKeyword) {
            mKeyword = keyword
            idx = 0
        }
        // TODO: Implement this method
        finder = LinearSearchStrategy()
        val kw = mKeyword!!
        if (kw.isEmpty()) {
            selectText(false)
            return false
        }
        idx = finder!!.find(this.text, kw, idx, this.text.length, false, false)
        if (idx == -1) {
            selectText(false)
            Toast.makeText(mContext, "未找到", Toast.LENGTH_SHORT).show()
            idx = 0
            return false
        }
        setSelection(idx, mKeyword!!.length)
        idx += mKeyword!!.length
        moveCaret(idx)
        return true
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        when (action) {
            AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> {
                when (arguments!!.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)) {
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE -> moveCaretDown()
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER -> moveCaretRight()
                }
                return true
            }

            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> {
                when (arguments!!.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)) {
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE -> moveCaretUp()
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER -> moveCaretLeft()
                }
                return true
            }

            AccessibilityNodeInfo.ACTION_SET_SELECTION -> {
                if (arguments == null) return true
                val start =
                    arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                val end =
                    arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 0)
                val sel = arguments.getBoolean(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN,
                    false
                )
                if (sel) setSelectionRange(start, end)
                else setSelection(start, end)
                return true
            }

            AccessibilityNodeInfo.ACTION_SET_TEXT -> {
                selectText(false)
                if (arguments == null) setText("", true)
                else setText(
                    arguments.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE)!!,
                    true
                )
                return true
            }

            AccessibilityNodeInfo.ACTION_PASTE -> {
                paste()
                return true
            }

            AccessibilityNodeInfo.ACTION_COPY -> {
                copy()
                return true
            }

            AccessibilityNodeInfo.ACTION_CUT -> {
                cut()
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }
}
