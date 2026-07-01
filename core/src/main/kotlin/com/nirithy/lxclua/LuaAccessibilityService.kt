package com.nirithy.lxclua

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Path
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nirithy.lxclua.util.ClickRunnable
import com.nirithy.lxclua.util.ClickRunnable.ClickCallback
import com.nirithy.lxclua.util.GlobalActionAutomator
import com.luajava.LuaException
import com.luajava.LuaFunction
import com.luajava.LuaTable
import com.nirithy.lxclua.image.Point
import com.nirithy.lxclua.screencapture.ScreenCaptureListener
import com.nirithy.lxclua.screencapture.ScreenShot
import java.util.Collections
import java.util.Locale

@SuppressLint("NewApi")
class LuaAccessibilityService : AccessibilityService() {
    private var mApplication: LuaApplication? = null
    private var mData: MutableMap<*, *>? = null
    private var appMap: HashMap<String?, ComponentName?>? = HashMap<String?, ComponentName?>()
    private var mOk = false
    private var handler: Handler? = null
    private var mGlobalActionAutomator: GlobalActionAutomator? = null
    private var mScreenShot: ScreenShot? = null
    private var mScreenDensity = 0
    private var mScreenWidth = 0
    private var mScreenHeight = 0

    private fun init() {
        val mWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager?
        val metrics = DisplayMetrics()
        if (mWindowManager == null) {
            return
        }
        mWindowManager.getDefaultDisplay().getRealMetrics(metrics)
        mScreenDensity = metrics.densityDpi
        mScreenWidth = metrics.widthPixels
        mScreenHeight = metrics.heightPixels
    }

    val density: Int
        get() {
            if (mScreenDensity == 0) init()
            return mScreenDensity
        }

    val width: Int
        get() {
            if (mScreenWidth == 0) init()
            return mScreenWidth
        }

    val height: Int
        get() {
            if (mScreenHeight == 0) init()
            return mScreenHeight
        }


    interface AccessibilityServiceCallbacks {
        fun onAccessibilityEvent(service: LuaAccessibilityService?, event: AccessibilityEvent?)

        fun onInterrupt(service: LuaAccessibilityService?)

        fun onServiceConnected(service: LuaAccessibilityService?)

        fun onCreate(service: LuaAccessibilityService?)

        fun onKeyEvent(service: LuaAccessibilityService?, event: KeyEvent?): Boolean

        fun onDestroy(service: LuaAccessibilityService?)

        fun onConfigurationChanged(service: LuaAccessibilityService?, newConfig: Configuration?)
    }

    override fun onCreate() {
        // TODO: Implement this method
        Log.i("lua", "onCreate")
        super.onCreate()
        handler = Handler()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mGlobalActionAutomator = GlobalActionAutomator(this, Handler())
            mGlobalActionAutomator!!.setService(this)
        }
        if (sAccessibilityServiceCallbacks != null) sAccessibilityServiceCallbacks!!.onCreate(this)
        asyncGetAllApp()

        mApplication = getApplication() as LuaApplication
        mData = mApplication!!.globalData
        if (!mData!!.containsKey("LuaAccessibilityService")) {
            return
        }
        val `as` = mData!!.get("LuaAccessibilityService") as LuaTable<*, *>?
        if (`as` == null) return
        try {
            val func = `as`.get("onCreate") as LuaFunction<*>?
            func!!.call(this)
        } catch (e: LuaException) {
            val func = `as`.get("onError") as LuaFunction<*>?
            if (func == null) {
                Log.i("onCreate", e.message!!)
                return
            }

            try {
                func.call(e)
            } catch (e2: LuaException) {
                Log.i("onCreate", e.message!!)
            }
        }
    }

    fun click(p: Point): Boolean {
        return click(p.x, p.y)
    }

    fun click(x: Int, y: Int): Boolean {
        if (mGlobalActionAutomator != null) {
            return mGlobalActionAutomator!!.click(x, y)
        }
        return false
    }

    fun longClick(p: Point): Boolean {
        return longClick(p.x, p.y)
    }

    fun longClick(x: Int, y: Int): Boolean {
        if (mGlobalActionAutomator != null) {
            return mGlobalActionAutomator!!.longClick(x, y)
        }
        return false
    }

    fun press(p: Point, delay: Int): Boolean {
        return press(p.x, p.y, delay)
    }

    fun press(x: Int, y: Int, delay: Int): Boolean {
        if (mGlobalActionAutomator != null) {
            return mGlobalActionAutomator!!.press(x, y, delay)
        }
        return false
    }

    fun swipe(p1: Point, p2: Point, delay: Int): Boolean {
        return swipe(p1.x, p1.y, p2.x, p2.y, delay)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, delay: Int): Boolean {
        if (mGlobalActionAutomator != null) {
            return mGlobalActionAutomator!!.swipe(x1, y1, x2, y2, delay)
        }
        return false
    }

    fun swipe(path: Path?, delay: Int): Boolean {
        if (mGlobalActionAutomator != null) {
            return mGlobalActionAutomator!!.gesture(0, delay.toLong(), path!!)
        }
        return false
    }

    override fun onServiceConnected() {
        // TODO: Implement this method
        Log.i("lua", "onServiceConnected")
        super.onServiceConnected()
        if (sAccessibilityServiceCallbacks != null) sAccessibilityServiceCallbacks!!.onServiceConnected(
            this
        )

        if (!mData!!.containsKey("LuaAccessibilityService")) {
            return
        }
        val `as` = mData!!.get("LuaAccessibilityService") as LuaTable<*, *>?
        if (`as` == null) {
            return
        }
        try {
            val func = `as`.get("onServiceConnected") as LuaFunction<*>?
            func!!.call(this)
        } catch (e: LuaException) {
            val func = `as`.get("onError") as LuaFunction<*>?
            if (func == null) {
                Log.i("onServiceConnected", e.message!!)
                return
            }

            try {
                func.call(e)
            } catch (e2: LuaException) {
                Log.i("onServiceConnected", e.message!!)
            }
        }
    }

    override fun onAccessibilityEvent(p1: AccessibilityEvent?) {
        // TODO: Implement this method
        //Log.i("lua", p1.toString());

        if (sAccessibilityServiceCallbacks != null) sAccessibilityServiceCallbacks!!.onAccessibilityEvent(
            this,
            p1
        )

        if (onAccessibilityEvent != null) {
            try {
                onAccessibilityEvent!!.call(p1)
            } catch (e: LuaException) {
                Log.i("lua", "onAccessibilityEvent: " + e)
            }
            return
        }
        if (!mData!!.containsKey("LuaAccessibilityService")) {
            return
        }
        val `as` = mData!!.get("LuaAccessibilityService") as LuaTable<*, *>?
        if (`as` == null) {
            return
        }
        try {
            val func = `as`.get("onAccessibilityEvent") as LuaFunction<*>?
            func!!.call(p1)
        } catch (e: LuaException) {
            val func = `as`.get("onError") as LuaFunction<*>?
            if (func == null) {
                Log.i("onAccessibilityEvent", e.message!!)
                return
            }
            try {
                func.call(e)
            } catch (e2: LuaException) {
                Log.i("onAccessibilityEvent", e.message!!)
            }
        }
    }

    override fun onInterrupt() {
        // TODO: Implement this method
        if (sAccessibilityServiceCallbacks != null) sAccessibilityServiceCallbacks!!.onInterrupt(
            this
        )
    }

    override fun onDestroy() {
        instance = null
        if (sAccessibilityServiceCallbacks != null) sAccessibilityServiceCallbacks!!.onDestroy(this)
        stopScreenshot()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (sAccessibilityServiceCallbacks != null) sAccessibilityServiceCallbacks!!.onConfigurationChanged(
            this,
            newConfig
        )
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (sAccessibilityServiceCallbacks != null) if (sAccessibilityServiceCallbacks!!.onKeyEvent(
                this,
                event
            )
        ) return true

        return super.onKeyEvent(event)
    }

    fun startScreenshot() {
        mScreenShot = ScreenShot(this, null)
    }

    fun startScreenshot(callback: VirtualDisplay.Callback?) {
        mScreenShot = ScreenShot(this, callback)
    }

    val screenshot: Bitmap?
        get() {
            if (mScreenShot != null) return mScreenShot!!.screenShot
            return null
        }

    fun getScreenshot(listener: LuaFunction<*>) {
        ScreenShot.getScreenCaptureBitmap(this, object : ScreenCaptureListener {
            override fun onScreenCaptureDone(bitmap: Bitmap?) {
                try {
                    listener.call(bitmap)
                } catch (e: LuaException) {
                    e.printStackTrace()
                }
            }

            override fun onScreenCaptureError(msg: String?) {
                try {
                    listener.call(null, msg)
                } catch (e: LuaException) {
                    e.printStackTrace()
                }
            }
        })
    }

    fun stopScreenshot() {
        if (mScreenShot != null) mScreenShot!!.release()
        mScreenShot = null
    }

    fun scrollForward(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        if (Build.VERSION.SDK_INT < 21) {
            if ((node.getActions() and AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == 0) return false
        } else {
            if (!node.getActionList()
                    .contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            ) return false
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollBackward(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        if (Build.VERSION.SDK_INT < 21) {
            if ((node.getActions() and AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == 0) return false
        } else {
            if (!node.getActionList()
                    .contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            ) return false
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    fun postExecute(
        time: Long,
        menu: String,
        node: AccessibilityNodeInfo,
        callback: LuaFunction<*>
    ) {
        handler!!.postDelayed(object : Runnable {
            override fun run() {
                try {
                    callback.call(execute(menu, node), menu, node)
                } catch (e: LuaException) {
                    e.printStackTrace()
                    sendError("postExecute", e)
                }
            }
        }, time)
    }


    private fun sendError(postClick: String?, e: LuaException?) {
    }

    fun postExecute(time: Long, menu: String, node: AccessibilityNodeInfo) {
        handler!!.postDelayed(object : Runnable {
            override fun run() {
                execute(menu, node)
            }
        }, time)
    }

    fun postClick(time: Long, buttons: LuaTable<*, *>?) {
        handler!!.postDelayed(object : Runnable {
            override fun run() {
                click(buttons)
            }
        }, time)
    }

    fun postClick(time: Long, buttons: LuaTable<*, *>?, callback: LuaFunction<*>) {
        handler!!.postDelayed(object : Runnable {
            override fun run() {
                click(buttons, callback)
            }
        }, time)
    }

    private fun checkParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        return node
    }

    fun click(buttons: LuaTable<*, *>?): Boolean {
        @Suppress("UNCHECKED_CAST")
        return ClickRunnable(this, buttons as LuaTable<*, *>).canClick()
    }

    fun click(buttons: LuaTable<*, *>?, callback: LuaFunction<*>): Boolean {
        @Suppress("UNCHECKED_CAST")
        return ClickRunnable(this, buttons as LuaTable<*, *>).canClick(object : ClickCallback {
            override fun onDone(bool: Boolean, bs: LuaTable<*, *>?, name: String?, idx: Int) {
                try {
                    callback.call(bool, bs, name, idx)
                } catch (e: LuaException) {
                    e.printStackTrace()
                    sendError("click", e)
                }
            }
        })
    }

    fun loopClick(buttons: LuaTable<*, *>?): ClickRunnable {
        @Suppress("UNCHECKED_CAST")
        val click = ClickRunnable(this, buttons as LuaTable<*, *>)
        click.canClick(object : ClickCallback {
            override fun onDone(bool: Boolean, bs: LuaTable<*, *>?, name: String?, idx: Int) {
                loopClick(buttons)
            }
        })
        return click
    }


    fun findClick(buttons: Array<String>): Boolean {
        for (name in buttons) {
            var button = findAccessibilityNodeInfoByText(name, 0)
            if (button != null) {
                button = checkClick(button)
                val ret = button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ret) return true
            }
        }
        return false
    }

    fun click(appName: String?, lable: String?, buttons: IntArray): Boolean {
        if (appName == null || lable == null) return false
        //print("click",getAppName(getFocusView()));
        if (appName != getAppName(this.focusView)) return false
        var root = getRootInActiveWindow()
        if (root == null) return false
        //print("click",root.findAccessibilityNodeInfosByText(lable));
        if (root.findAccessibilityNodeInfosByText(lable).isEmpty()) return false
        for (i in buttons) {
            if (root!!.getChildCount() <= i) return false
            root = root.getChild(i)
            if (root == null) return false
        }
        //print("click",root);
        return toClick(root)
    }

    private fun findNodeInfoByText(
        ret: MutableList<AccessibilityNodeInfo?>,
        node: AccessibilityNodeInfo?,
        keyword: String
    ) {
        if (node == null) return
        val t = arrayOfNulls<CharSequence>(2)
        val names = keyword.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        t[0] = node.getContentDescription()
        t[1] = node.getText()
        for (name in names) {
            var name = name
            val start = !name.startsWith("*")
            val end = !name.endsWith("*")
            if (!start) name = name.substring(1)
            if (!end) name = name.substring(0, name.length - 1)

            for (d in t) {
                if (d == null) continue

                val text = d.toString().trim { it <= ' ' }
                if (start && end) {
                    if (name == text) {
                        ret.add(node)
                        break
                    }
                } else if (start) {
                    if (text.startsWith(name)) {
                        ret.add(node)
                        break
                    }
                } else if (end) {
                    if (text.endsWith(name)) {
                        ret.add(node)
                        break
                    }
                } else {
                    if (text.contains(name)) {
                        ret.add(node)
                        break
                    }
                }
            }
        }
        val c = node.getChildCount()
        for (i in 0..<c) {
            findNodeInfoByText(ret, node.getChild(i), keyword)
        }
    }

    fun findAccessibilityNodeInfoByText(keyword: String): MutableList<AccessibilityNodeInfo?> {
        val root = getRootInActiveWindow()
        val ret: MutableList<AccessibilityNodeInfo?> = java.util.ArrayList<AccessibilityNodeInfo?>()
        if (root == null) return ret
        val names = keyword.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (name in names) {
            var name = name
            if (name.isEmpty()) continue
            val c = name.get(0)
            when (c) {
                '%' -> {
                    execute(name.substring(1), this.focusView!!)
                    return ret
                }
            }

            val idx = name.lastIndexOf('&')
            if (idx > 0) {
                val node = findAccessibilityNodeInfo(name.substring(idx + 1))
                if (node == null) continue
                name = name.substring(0, idx)
            }

            val start = !name.startsWith("*")
            val end = !name.endsWith("*")
            if (!start) name = name.substring(1)
            if (!end) name = name.substring(0, name.length - 1)
            val list = root.findAccessibilityNodeInfosByText(name)
            for (node in list) {
                val text = (node.getText().toString() + "").trim { it <= ' ' }
                val des = (node.getContentDescription().toString() + "").trim { it <= ' ' }
                if (start && end) {
                    if (name == text || name == des) ret.add(node)
                } else if (start) {
                    if (text.startsWith(name) || des.startsWith(name)) ret.add(node)
                } else if (end) {
                    if (text.endsWith(name) || des.endsWith(name)) ret.add(node)
                } else {
                    if (text.contains(name) || des.contains(name)) ret.add(node)
                }
            }
        }
        if (ret.isEmpty()) findNodeInfoByText(ret, root, keyword)
        return ret
    }

    fun findAccessibilityNodeInfoByText(name: String, i: Int): AccessibilityNodeInfo? {
        val ret = findAccessibilityNodeInfoByText(name)
        if (ret.isEmpty()) return null
        val size = ret.size

        if (i + 1 > size || -i > size) return null
        if (i < 0) return ret.get(ret.size + i)
        else return ret.get(i)
    }

    fun findAccessibilityNodeInfoById(name: String?): MutableList<AccessibilityNodeInfo> {
        val root = getRootInActiveWindow()
        if (root == null) return java.util.ArrayList<AccessibilityNodeInfo>()
        return root.findAccessibilityNodeInfosByText(name)
    }

    fun findAccessibilityNodeInfoById(name: String?, i: Int): AccessibilityNodeInfo? {
        val ret = findAccessibilityNodeInfoById(name)
        if (ret.isEmpty()) return null
        val size = ret.size

        if (i + 1 > size || -i > size) return null
        if (i < 0) return ret.get(ret.size + i)
        else return ret.get(i)
    }

    fun findAccessibilityNodeInfoByIndex(name: String): AccessibilityNodeInfo? {
        var root = getRootInActiveWindow()
        if (root == null) return null
        val buttons = name.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (s in buttons) {
            try {
                val i = s.toInt()
                if (root!!.getChildCount() <= i) return null
                root = root.getChild(i)
                if (root == null) return null
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        return root
    }


    fun findAccessibilityNodeInfo(name: String): AccessibilityNodeInfo? {
        var name = name
        var idx = name.lastIndexOf("@")
        if (idx > 0) {
            val app = name.substring(idx + 1)
            if (app != getAppName(this.focusView)) return null
            name = name.substring(0, idx)
        }
        idx = name.lastIndexOf("#")
        var i = -1
        if (idx > 0) {
            try {
                i = name.substring(idx + 1).toInt()
            } catch (e: Exception) {
                i = -1
            }
            name = name.substring(0, idx)
        }
        when (name.get(0)) {
            '%' -> if (execute(
                    name.substring(1),
                    this.focusView!!
                )
            ) return AccessibilityNodeInfo.obtain()
            else return null

            '>' -> if (startApp(name.substring(1))) return AccessibilityNodeInfo.obtain()
            else return null

            '@' -> return findAccessibilityNodeInfoById(name.substring(1), i)
            '$' -> return findAccessibilityNodeInfoByIndex(name.substring(1))
            else -> return findAccessibilityNodeInfoByText(name, i)
        }
    }

    fun execute(menu: String, node: AccessibilityNodeInfo): Boolean {
        var ret = false
        when (menu) {
            "向上翻页" -> {
                val list = findListView(getRootInActiveWindow())
                if (list == null) return false
                ret = scrollBackward(list)
                return ret
            }

            "向下翻页" -> {
                val list2 = findListView(getRootInActiveWindow())
                if (list2 == null) return false
                ret = scrollForward(list2)
                return ret
            }

            "减少进度" -> return scrollBackward(node)
            "增加进度" -> return scrollForward(node)
            "粘贴" -> paste(node)
            "最近任务" -> toRecents()
            "清空" -> if (Build.VERSION.SDK_INT >= 21) {
                return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT)
            } else {
                return false
            }

            "复制" -> copy(getText(node))
            "追加复制" -> appendCopy(getText(node))
            "主屏幕" -> toHome()
            "返回" -> toBack()
            "点击" -> toClick(node)
            "长按" -> toLongClick(node)
            "通知栏", "打开通知栏" -> toNotifications()
            else -> return false
        }

        return true
    }

    fun isListView2(source: AccessibilityNodeInfo?): Boolean {
        if (source == null) return false
        val className = source.getClassName()
        if (className != null) {
            val name = className.toString()
            when (name) {
                "android.widget.AdapterView", "android.widget.ListView", "android.widget.GridView", "android.widget.AbsListView", "android.widget.ExpandableListView", "android.support.v7.widget.RecyclerView", "flyme.support.v7.widget.RecyclerView", "android.widget.ScrollView", "android.widget.HorizontalScrollView", "com.tencent.widget.GridView" -> return true
                else -> if (name.endsWith("ScrollView")) return true
                else if (name.endsWith("GridView")) return true
                else if (name.endsWith("RecyclerView")) return true
                else if (name.endsWith("ListView")) return true
            }
        }
        return false
    }

    fun getText(source: AccessibilityNodeInfo?): String? {
        return getNodeInfoText(source)
    }

    private fun findListView(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (isListView2(node)) return node
        val count = node.getChildCount()
        for (i in 0..<count) {
            val list = findListView(node.getChild(i))
            if (list != null) return list
        }
        return null
    }

    fun insert(mEditView: AccessibilityNodeInfo?, text: CharSequence?): Boolean {
        if (mEditView == null) return false

        if (text == null) return false
        if (mEditView.isEditable()) {
            if (!mEditView.isFocused()) mEditView.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("label", text))
            cm.setText(text)
            return mEditView.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        return false
    }

    fun paste(mEditView: AccessibilityNodeInfo?, text: CharSequence?): Boolean {
        if (mEditView == null) return false

        if (text == null) return false
        if (mEditView.isEditable()) {
            if (!mEditView.isFocused()) mEditView.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("label", text))
            cm.setText(text)
            if (mEditView.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                return true
            }
        }
        return paste(text)
    }

    fun paste(mEditView: AccessibilityNodeInfo?): Boolean {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        return paste(mEditView, cm.getText())
    }

    fun paste(): Boolean {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        return paste(this.focusView, cm.getText())
    }

    fun paste(text: CharSequence?): Boolean {
        var text = text
        if (text == null) return false
        val mEditView = this.editText
        if (mEditView == null) {
            return false
        }
        if (this.focusView?.isEditable() == true && this.focusView?.getText() != null) text =
            this.focusView?.getText().toString() + text
        if (Build.VERSION.SDK_INT >= 21) {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            return mEditView.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } else {
            return false
        }
    }


    @JvmOverloads
    fun copy(t: CharSequence? = getText(this.focusView)): Boolean {
        if (t == null) {
            return false
        }
        val text = t.toString()
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("label", text))
        return true
    }

    @JvmOverloads
    fun appendCopy(t: CharSequence? = getText(this.focusView)): Boolean {
        if (t == null) {
            return false
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val c = cm.getText()
        var text = ""
        if (c != null) text = c.toString()
        if (text.length > 1) text = text + "\n"
        cm.setPrimaryClip(ClipData.newPlainText("label", text + t))
        return true
    }

    fun setText(s: String?): Boolean {
        return setText(this.editText, s)
    }

    fun setText(mEditView: AccessibilityNodeInfo?, s: String?): Boolean {
        if (mEditView == null || !mEditView.isEditable()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= 21) {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                s
            )
            return mEditView.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } else {
            return paste(mEditView, s)
        }
    }

    val editText: AccessibilityNodeInfo?
        get() {
            val editList =
                this.allEditTextList
            if (editList.isEmpty()) {
                return null
            }
            val mHoverView = editList.get(0)
            if (mHoverView != null) mHoverView.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            return mHoverView
        }

    val allEditTextList: ArrayList<AccessibilityNodeInfo?>
        get() {
            val textList =
                java.util.ArrayList<AccessibilityNodeInfo?>()
            val node = getRootInActiveWindow()
            getEditText(node, textList)
            return textList
        }

    fun getEditText(
        node: AccessibilityNodeInfo?,
        textList: java.util.ArrayList<AccessibilityNodeInfo?>
    ) {
        if (node == null) return
        if (node.isEditable()) textList.add(node)
        val count = node.getChildCount()
        if (count > 0) {
            for (i in 0..<count) getEditText(node.getChild(i), textList)
        }
    }

    fun getAllText(minLength: Int): String {
        val list = this.allTextList
        val buf = StringBuilder()
        for (text in list) {
            if (text.length > minLength) buf.append(text).append("\n")
        }
        return buf.toString()
    }


    fun getAllTextList(focus: AccessibilityNodeInfo): java.util.ArrayList<String?> {
        val textList = java.util.ArrayList<String?>()
        val node = getRootInActiveWindow()
        mOk = !focus.isVisibleToUser()
        getText(node, textList, focus)
        return textList
    }

    fun toBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun toHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun toRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun toNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun toClick(nodeInfo: AccessibilityNodeInfo?): Boolean {
        if (nodeInfo != null) {
            try {
                return nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    fun toLongClick(node: AccessibilityNodeInfo?): Boolean {
        if (node != null) {
            try {
                return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private fun getText(
        node: AccessibilityNodeInfo?,
        textList: java.util.ArrayList<String?>,
        focus: AccessibilityNodeInfo?
    ) {
        if (node == null) return
        /*if (!node.isVisibleToUser()&&isInListView(node))
            return;*/
        if (!mOk) mOk = node == focus
        val text: CharSequence? = getNodeInfoText(node)
        if (mOk) {
            if (text != null) textList.add(text.toString())
        }
        val count = node.getChildCount()
        if (count > 0) {
            for (i in 0..<count) {
                val child = node.getChild(i)
                if (child == null) continue
                if (!mOk) mOk = child == focus
                getText(child, textList, focus)
            }
        }
    }

    fun getNodeInfoText(source: AccessibilityNodeInfo?): String? {
        if (source == null) return null
        val ct = source.getContentDescription()
        val text = source.getText()
        var contentDescription: String? = null
        if (ct != null) contentDescription = ct.toString()
        if (contentDescription != null && contentDescription.trim { it <= ' ' }.length > 0 && (!source.isEditable() || text == null)) {
            return contentDescription
        } else if (text != null && text.length > 0) {
            return text.toString()
        }
        return null
    }


    val allTextList: ArrayList<String>
        get() {
            val textList = java.util.ArrayList<String>()
            val node = getRootInActiveWindow()
            getText(node, textList)
            return textList
        }

    private fun getText(node: AccessibilityNodeInfo?, textList: java.util.ArrayList<String>?) {
        if (node == null) return
        /*if (!node.isVisibleToUser()&&isInListView(node))
            return;*/
        val text: CharSequence? = getNodeInfoText(node)
        val count = node.getChildCount()
        if (count > 0) {
            for (i in 0..<count) getText(node.getChild(i), textList)
        }
    }


    private fun asyncGetAllApp() {
        object : AsyncTask<String?, String?, HashMap<String?, ComponentName?>?>() {
            override fun doInBackground(vararg params: String?): HashMap<String?, ComponentName?> {
                val appMap = HashMap<String?, ComponentName?>()
                val manager = getPackageManager()
                var mainIntent = Intent(Intent.ACTION_MAIN, null) //取出Intent 为Action_Main的程序
                mainIntent.addCategory(Intent.CATEGORY_DEFAULT) //分辨出位默认Laucher启动的程序

                var apps = manager.queryIntentActivities(mainIntent, 0) //利用包管理器将起取出来
                Collections.sort<ResolveInfo?>(apps, ResolveInfo.DisplayNameComparator(manager))

                var count = apps.size
                for (i in 0..<count) {
                    //ApplicationInfo application = new ApplicationInfo();
                    val info = apps.get(i)

                    val title = info.loadLabel(manager)
                    val componentName = ComponentName(
                        info.activityInfo.applicationInfo.packageName,
                        info.activityInfo.name
                    )
                    appMap.put(title.toString().lowercase(Locale.getDefault()), componentName)
                }

                mainIntent = Intent(Intent.ACTION_MAIN, null) //取出Intent 为Action_Main的程序
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER) //分辨出位默认Laucher启动的程序

                apps = manager.queryIntentActivities(mainIntent, 0) //利用包管理器将起取出来
                Collections.sort<ResolveInfo?>(apps, ResolveInfo.DisplayNameComparator(manager))

                count = apps.size
                for (i in 0..<count) {
                    //ApplicationInfo application = new ApplicationInfo();
                    val info = apps.get(i)

                    val title = info.loadLabel(manager)
                    val componentName = ComponentName(
                        info.activityInfo.applicationInfo.packageName,
                        info.activityInfo.name
                    )
                    appMap.put(title.toString().lowercase(Locale.getDefault()), componentName)
                }
                return appMap
            }

            override fun onPostExecute(stringComponentNameHashMap: HashMap<String?, ComponentName?>?) {
                super.onPostExecute(stringComponentNameHashMap)
                if (stringComponentNameHashMap != null && !stringComponentNameHashMap.isEmpty()) appMap =
                    stringComponentNameHashMap
            }
        }.execute("")
    }


    private val allApp: Unit
        get() {
            val manager = getPackageManager()
            val mainIntent = Intent(
                Intent.ACTION_MAIN,
                null
            ) //取出Intent 为Action_Main的程序
            mainIntent.addCategory(Intent.CATEGORY_DEFAULT) //分辨出位默认Laucher启动的程序

            val apps =
                manager.queryIntentActivities(mainIntent, 0) //利用包管理器将起取出来
            Collections.sort<ResolveInfo?>(
                apps,
                ResolveInfo.DisplayNameComparator(manager)
            )

            if (apps != null) {
                val count = apps.size
                appMap!!.clear()
                for (i in 0..<count) {
                    //ApplicationInfo application = new ApplicationInfo();
                    val info = apps.get(i)

                    val title = info.loadLabel(manager)
                    val componentName =
                        ComponentName(
                            info.activityInfo.applicationInfo.packageName,
                            info.activityInfo.name
                        )
                    appMap!!.put(
                        title.toString().lowercase(Locale.getDefault()),
                        componentName
                    )
                }
            }
            this.allApp2
        }

    private val allApp2: Unit
        get() {
            val manager = getPackageManager()
            val mainIntent = Intent(
                Intent.ACTION_MAIN,
                null
            ) //取出Intent 为Action_Main的程序
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER) //分辨出位默认Laucher启动的程序

            val apps =
                manager.queryIntentActivities(mainIntent, 0) //利用包管理器将起取出来
            Collections.sort<ResolveInfo?>(
                apps,
                ResolveInfo.DisplayNameComparator(manager)
            )

            if (apps != null) {
                val count = apps.size
                for (i in 0..<count) {
                    //ApplicationInfo application = new ApplicationInfo();
                    val info = apps.get(i)

                    val title = info.loadLabel(manager)
                    val componentName =
                        ComponentName(
                            info.activityInfo.applicationInfo.packageName,
                            info.activityInfo.name
                        )
                    appMap!!.put(
                        title.toString().lowercase(Locale.getDefault()),
                        componentName
                    )
                }
            }
        }

    fun startApp(appName: String): Boolean {
        var appName = appName
        asyncGetAllApp()
        appName = appName.lowercase(Locale.getDefault())
        val className = appMap!!.get(appName)
        if (className == null) return false
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.setComponent(className)
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun deleteApp(appName: String): Boolean {
        var appName = appName
        appMap!!.clear()
        this.allApp2
        appName = appName.lowercase(Locale.getDefault())
        val className = appMap!!.get(appName)
        if (className == null) return false
        val intent =
            Intent(Intent.ACTION_DELETE, Uri.parse("package:" + className.getPackageName()))
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        startActivity(intent)
        return true
    }

    fun installApp(appName: String?): Boolean {
        if (appName == null) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + appName))
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    val focusView: AccessibilityNodeInfo?
        get() = getRootInActiveWindow()

    fun getAppName(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val pkn = node.getPackageName()
        if (pkn == null) return ""
        val pkg = pkn.toString()
        val pm = getPackageManager()
        try {
            val info = pm.getApplicationInfo(pkg, 0)
            return pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return ""
        }
    }

    fun getHandler(): Handler {
        return handler!!
    }

    fun isClickable(source: AccessibilityNodeInfo?): Boolean {
        if (source == null) return false
        if (source.isClickable()) return true
        if (source.isCheckable()) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return source.getActionList()
                .contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        } else {
            return (source.getActions() and AccessibilityNodeInfo.ACTION_CLICK) != 0
        }
    }

    private fun checkClick(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        try {
            var parent = node
            while (parent != null) {
                if (isClickable(parent)) return parent
                parent = parent.getParent()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return node
    }


    fun toClick2(node: AccessibilityNodeInfo?) {
        toClick(checkClick(node))
    }

    companion object {
        private var sAccessibilityServiceCallbacks: AccessibilityServiceCallbacks? = null
        var onAccessibilityEvent: LuaFunction<*>? = null
        var instance: LuaAccessibilityService? = null
            private set

        fun setCallback(callback: AccessibilityServiceCallbacks?) {
            sAccessibilityServiceCallbacks = callback
        }
    }
}
