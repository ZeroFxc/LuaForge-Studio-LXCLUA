package com.nirithy.lxclua

import android.R
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Fragment
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.StrictMode
import android.util.DisplayMetrics
import android.util.Log
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayListAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nirithy.lxclua.LuaBroadcastReceiver.OnReceiveListener
import com.nirithy.lxclua.LuaService.LuaBinder
import com.nirithy.lxclua.Ticker.OnTickListener
import com.nirithy.lxclua.util.FileUtil
import com.luaforge.studio.lxclua.utils.JsonUtil.parseObject
import com.luajava.JavaFunction
import com.luajava.LuaException
import com.luajava.LuaObject
import com.luajava.LuaState
import com.luajava.LuaStateFactory
import com.nirithy.luacompose.bridge.ComposeBridge
import com.nirithy.luacompose.bridge.ComposeBridge.activeBackStack
import com.nirithy.luacompose.bridge.ComposeBridge.luaError
import com.nirithy.luacompose.bridge.ComposeBridge.refreshAfterLoad
import com.nirithy.luacompose.bridge.ComposeBridge.rootState
import com.nirithy.luacompose.bridge.ComposeBridge.setAndroidContext
import com.nirithy.luacompose.bridge.createComposeView
import dalvik.system.DexClassLoader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

open class LuaActivity : AppCompatActivity(), OnReceiveListener, LuaContext {
    private var mLuaDir: String? = null
    private var handler: Handler? = null
    private var status: TextView? = null
    private var mLuaCpath: String? = null
    private var mLuaDexLoader: LuaDexLoader? = null
    private var mWidth = 0
    private var mHeight = 0
    private var list: ListView? = null
    private var adapter: ArrayListAdapter<String?>? = null
    private var L: LuaState? = null
    private var mLuaPath: String? = null
    private val toastbuilder = StringBuilder()
    private var isCreate = false
    private var toast: Toast? = null
    private var layout: LinearLayout? = null
    private var isSetViewed = false
    private var lastShow: Long = 0
    var optionsMenu: Menu? = null
        private set
    private var mOnKeyDown: LuaObject? = null
    private var mOnKeyUp: LuaObject? = null
    private var mOnKeyLongPress: LuaObject? = null
    private var mOnTouchEvent: LuaObject? = null
    private var mOnActivityReenter: LuaObject? = null
    var localDir: String? = null
        private set

    private var odexDir: String? = null

    private var libDir: String? = null

    private var mLuaExtDir: String? = null

    private var mReceiver: LuaBroadcastReceiver? = null

    private var mLuaLpath: String? = null

    private var luaMdDir: String? = null

    private val isUpdata = false

    private var mDebug = true
    private var mResources: LuaResources? = null
    private val gclist = ArrayList<LuaGcable>()
    private var pageName = "main"
    private val mDestroyed = AtomicBoolean(false)
    private var mOnKeyShortcut: LuaObject? = null

    override val classLoaders: ArrayList<ClassLoader?>?
        get() = mLuaDexLoader!!.classLoaders

    val librarys: HashMap<String?, String?>?
        get() = mLuaDexLoader!!.librarys

    override fun onCreate(savedInstanceState: Bundle?) {
        DebugLogger.log("LuaActivity", "onCreate 开始, class=" + javaClass.getSimpleName())

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        super.onCreate(null)
        DebugLogger.log("LuaActivity", "super.onCreate 完成")

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val outMetrics = DisplayMetrics()
        wm.getDefaultDisplay().getMetrics(outMetrics)
        mWidth = outMetrics.widthPixels
        mHeight = outMetrics.heightPixels

        layout = LinearLayout(this)
        val scroll = ScrollView(this)
        scroll.setFillViewport(true)
        status = TextView(this)

        status!!.setTextColor(Color.BLACK)
        scroll.addView(
            status,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        status!!.setText("")
        status!!.setTextIsSelectable(true)
        list = ListView(this)
        list!!.setFastScrollEnabled(true)
        adapter =
            object : ArrayListAdapter<String?>(this, R.layout.simple_list_item_1) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    if (convertView == null) view.setTextIsSelectable(true)
                    return view
                }
            }
        list!!.setAdapter(adapter)
        layout!!.addView(
            list,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // 定义文件夹
        val app = getApplication() as LuaApplication
        if (app.javaClass != LuaApplication::class.java) {
            while (true) {
                if (app.javaClass == LuaApplication::class.java) break
            }
        }
        localDir = app.localDir
        odexDir = app.odexDir
        libDir = app.libDir
        luaMdDir = app.mdDir
        mLuaCpath = app.luaCpath
        mLuaDir = localDir
        mLuaLpath = app.luaLpath
        mLuaExtDir = app.luaExtDir

        handler = MainHandler()

        try {
            status!!.setText("")
            adapter!!.clear()
            val intent = getIntent()
            var arg = intent.getSerializableExtra(ARG) as Array<Any?>?
            if (arg == null) arg = arrayOfNulls<Any>(0)

            mLuaPath = luaPath
            DebugLogger.log("LuaActivity", "luaPath=" + mLuaPath)
            pageName = File(mLuaPath).getName()
            val idx = pageName.lastIndexOf(".")
            if (idx > 0) pageName = pageName.substring(0, idx)
            DebugLogger.log("LuaActivity", "pageName=" + pageName + ", luaDir=" + mLuaDir)

            mLuaLpath =
                (mLuaDir + "/?.lua;" + mLuaDir + "/lua/?.lua;" + mLuaDir + "/?/settings.json;") + mLuaLpath
            DebugLogger.log("LuaActivity", "initLua 开始")
            initLua()
            DebugLogger.log("LuaActivity", "initLua 完成")

            mLuaDexLoader = LuaDexLoader(this)
            mLuaDexLoader!!.loadLibs()
            sLuaActivityMap.put(pageName, this)
            DebugLogger.log("LuaActivity", "doFile 开始: " + mLuaPath)
            doFile(mLuaPath!!, arg)
            DebugLogger.log("LuaActivity", "doFile 完成")
            // 触发 compose.render() 的首次渲染（必须在 doFile 返回后调用，避免 Lua VM 重入）
            DebugLogger.log("LuaActivity", "refreshAfterLoad 调用")
            refreshAfterLoad()
            DebugLogger.log(
                "LuaActivity",
                "refreshAfterLoad 完成, rootState=" + rootState.value + ", luaError=" + luaError.value
            )
            isCreate = true
            if (pageName != "main") runFunc("main", *arg)
            runFunc(pageName, *arg)
            runFunc("onCreate", savedInstanceState)
            if (!isSetViewed) {
                // 检查 Lua 是否通过 compose.render() 声明了 Compose UI
                // 或是否有 Lua 错误需要显示（确保错误信息能通过 ComposeView 展示）
                val hasRoot = rootState.value != null
                val hasError = luaError.value != null
                DebugLogger.log(
                    "LuaActivity",
                    "setContentView 决策: hasRoot=" + hasRoot + ", hasError=" + hasError + ", isSetViewed=" + isSetViewed
                )
                if (hasRoot || hasError) {
                    DebugLogger.log("LuaActivity", "创建 ComposeView")
                    setContentView(createComposeView(this))
                    DebugLogger.log("LuaActivity", "ComposeView 设置完成")
                } else {
                    DebugLogger.log("LuaActivity", "fallback 到 LinearLayout（无 Compose UI）")
                    val array =
                        getTheme()
                            .obtainStyledAttributes(
                                intArrayOf(
                                    R.attr.colorBackground,
                                    R.attr.textColorPrimary,
                                    R.attr.textColorHighlightInverse,
                                )
                            )
                    val backgroundColor = array.getColor(0, 0xFF00FF)
                    val textColor = array.getColor(1, 0xFF00FF)
                    array.recycle()
                    status!!.setTextColor(textColor)
                    layout!!.setBackgroundColor(backgroundColor)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        WindowCompat.setDecorFitsSystemWindows(getWindow(), false)

                        val isLightBg = ColorUtils.calculateLuminance(backgroundColor) > 0.5
                        val controller =
                            WindowCompat.getInsetsController(
                                getWindow(),
                                getWindow().getDecorView()
                            )

                        if (controller != null) {
                            controller.setAppearanceLightStatusBars(isLightBg)
                            controller.setAppearanceLightNavigationBars(isLightBg)
                        }

                        ViewCompat.setOnApplyWindowInsetsListener(
                            layout!!,
                            OnApplyWindowInsetsListener { v: View?, insets: WindowInsetsCompat? ->
                                val systemBars = insets!!.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                )
                                v!!.setPadding(
                                    systemBars.left,
                                    systemBars.top,
                                    systemBars.right,
                                    systemBars.bottom
                                )
                                insets
                            })
                    }

                    setContentView(layout)
                }
            }
        } catch (e: Exception) {
            DebugLogger.logError("LuaActivity", "onCreate 异常", e)
            sendMsg(e.message!!)
            setContentView(layout)
            return
        }

        val luaState = L!!
        mOnKeyShortcut = luaState.getLuaObject("onKeyShortcut")
        if (mOnKeyShortcut!!.isNil()) mOnKeyShortcut = null
        mOnKeyDown = luaState.getLuaObject("onKeyDown")
        if (mOnKeyDown!!.isNil()) mOnKeyDown = null
        mOnKeyUp = luaState.getLuaObject("onKeyUp")
        if (mOnKeyUp!!.isNil()) mOnKeyUp = null
        mOnKeyLongPress = luaState.getLuaObject("onKeyLongPress")
        if (mOnKeyLongPress!!.isNil()) mOnKeyLongPress = null
        mOnTouchEvent = luaState.getLuaObject("onTouchEvent")
        if (mOnTouchEvent!!.isNil()) mOnTouchEvent = null
        val onActivityReenter = luaState.getLuaObject("onActivityReenter")
        if (onActivityReenter.isFunction()) {
            mOnActivityReenter = onActivityReenter
        } else {
            mOnActivityReenter = null
        }

        // 注册新的返回手势回调（Android 13+ 推荐）
        onBackPressedDispatcher
            .addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        // ★ 优先检查 ComposeBridge 的导航回退栈
                        val backStack =
                            activeBackStack
                        DebugLogger.log(
                            "LuaActivity",
                            "handleOnBackPressed: backStack=" + (if (backStack != null) backStack.size else "null")
                        )
                        if (backStack != null && backStack.size > 1) {
                            DebugLogger.log(
                                "LuaActivity",
                                "handleOnBackPressed: 移除栈顶, 当前栈大小=" + backStack.size
                            )
                            backStack.removeAt(backStack.size - 1)
                            DebugLogger.log(
                                "LuaActivity",
                                "handleOnBackPressed: 移除后栈大小=" + backStack.size
                            )
                            return
                        }
                        // 然后检查 Lua 的 onBackPressed 回调
                        val ret = runFunc("onBackPressed")
                        if (ret != null && ret.javaClass == Boolean::class.java && ret as Boolean) {
                            // Lua 返回 true，拦截返回事件
                            return
                        }
                        // 否则执行默认返回行为
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                })

        val onAccessibilityEvent = luaState.getLuaObject("onAccessibilityEvent")
        if (onAccessibilityEvent.isFunction()) LuaAccessibilityService.Companion.onAccessibilityEvent =
            onAccessibilityEvent.getFunction()
    }

    fun setFragment(fragment: Fragment?) {
        isSetViewed = true
        setContentView(View(this))
        getFragmentManager().beginTransaction().replace(R.id.content, fragment).commit()
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent?): Boolean {
        if (mOnKeyShortcut != null) {
            try {
                val ret = mOnKeyShortcut!!.call(keyCode, event)
                if (ret != null && ret.javaClass == Boolean::class.java && ret as Boolean) return true
            } catch (e: LuaException) {
                sendError("onKeyShortcut", e)
            }
        }
        return super.onKeyShortcut(keyCode, event)
    }

    override fun regGc(obj: LuaGcable?) {
        gclist.add(obj!!)
    }

    fun test(src: String?, n: Int): Long {
        val luaState = L ?: return 0
        val t = System.currentTimeMillis()
        for (i in 0..<n) {
            luaState.LdoString(src)
        }
        return System.currentTimeMillis() - t
    }

    fun initMain() {
        prjCache.add(this.localDir)
    }

    override val luaPath: String?
        get() {
        val intent = getIntent()
        val uri = intent.getData()
        var path: String? = null
        if (uri == null) return null

        path = uri.getPath()!!
        if (!File(path).exists() && File(resolveLuaPath(path)).exists()) path = resolveLuaPath(path)

        mLuaPath = path
        val f = File(path)

        mLuaDir = File(mLuaPath).getParent()
        if (f.getName() == "main.lua" && File(mLuaDir, "settings.json").exists()) {
            if (!prjCache.contains(mLuaDir)) prjCache.add(mLuaDir)
        } else {
            var parent = mLuaDir
            while (parent != null) {
                if (prjCache.contains(parent)) {
                    mLuaDir = parent
                    break
                } else {
                    if (File(parent, "main.lua").exists() && File(
                            parent,
                            "settings.json"
                        ).exists()
                    ) {
                        mLuaDir = parent
                        if (!prjCache.contains(mLuaDir)) prjCache.add(mLuaDir)
                        break
                    }
                }
                parent = File(parent).getParent()
            }
        }
        return path
    }

    fun getQuery(name: String?): String? {
        val uri = getIntent().getData()
        if (uri == null) return null
        return uri.getQueryParameter(name)
    }

    fun getArg(idx: Int): Any? {
        val arg = getIntent().getSerializableExtra(ARG) as Array<Any?>?
        if (arg == null || arg.size >= idx) return null
        return arg[idx]
    }

    override fun resolveLuaPath(path: String?): String? {
        return File(luaDir, path).getAbsolutePath()
    }

    override fun resolveLuaPath(dir: String?, name: String?): String? {
        return File(resolveLuaDir(dir), name).getAbsolutePath()
    }

    override fun resolveLuaExtPath(path: String?): String? {
        return File(luaExtDir, path).getAbsolutePath()
    }

    override fun resolveLuaExtPath(dir: String?, name: String?): String? {
        return File(resolveLuaExtDir(dir), name).getAbsolutePath()
    }

    override val luaLpath: String?
        get() = mLuaLpath

    override val luaCpath: String?
        get() = mLuaCpath

    override val context: Context?
        get() = this

    override val luaState: LuaState?
        get() = L

    val decorView: View
        get() = getWindow().getDecorView()

    override var luaExtDir: String?
        get() = mLuaExtDir
        set(value) {
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                val sdDir = Environment.getExternalStorageDirectory().getAbsolutePath()
                mLuaExtDir = File(sdDir, value).getAbsolutePath()
            } else {
                val fs = File("/storage").listFiles()
                for (f in fs!!) {
                    val ls = f.list()
                    if (ls == null) continue
                    if (ls.size > 5) mLuaExtDir = File(f, value).getAbsolutePath()
                }
                if (mLuaExtDir == null) mLuaExtDir = getDir(value, MODE_PRIVATE).getAbsolutePath()
            }
            val d = File(mLuaExtDir)
            if (!d.exists()) d.mkdirs()
        }

    override fun resolveLuaExtDir(name: String?): String? {
        val dir = File(luaExtDir, name)
        if (!dir.exists()) if (!dir.mkdirs()) return null
        return dir.getAbsolutePath()
    }

    override val luaDir: String?
        get() = mLuaDir

    fun setLuaDir(dir: String?) {
        mLuaDir = dir
    }

    override fun resolveLuaDir(name: String?): String? {
        val dir = File(mLuaDir + "/" + name)
        if (!dir.exists()) if (!dir.mkdirs()) return null
        return dir.getAbsolutePath()
    }

    @Throws(LuaException::class)
    fun loadApp(path: String?): DexClassLoader? {
        return mLuaDexLoader!!.loadApp(path!!)
    }

    @Throws(LuaException::class)
    fun loadDex(path: String?): DexClassLoader? {
        return mLuaDexLoader!!.loadDex(path!!)
    }

    fun loadResources(path: String?) {
        mLuaDexLoader!!.loadResources(path)
    }

    @get:JvmName("getLuaAssets")
    val assets: AssetManager
        get() {
            val loader = mLuaDexLoader
            if (loader != null && loader.assets != null) return loader.assets!!
            return super.getAssets()
        }

    val luaResources: LuaResources?
        get() {
            var superRes = super.getResources()
            if (mLuaDexLoader != null && mLuaDexLoader!!.resources != null) superRes =
                mLuaDexLoader!!.resources
            mResources =
                LuaResources(assets, superRes.displayMetrics, superRes.configuration)
            mResources!!.setSuperResources(superRes)
            return mResources
        }

    val superResources: Resources
        get() = super.getResources()

    @get:JvmName("getLuaResources")
    val resources: Resources
        get() {
            val loader = mLuaDexLoader
            if (loader != null && loader.resources != null) return loader.resources!!
            if (mResources != null) return mResources!!
            return super.getResources()
        }

    @Throws(LuaException::class)
    fun loadLib(name: String): Any? {
        val i = name.indexOf(".")
        var fn = name
        if (i > 0) fn = name.substring(0, i)
        var f = File(libDir + "/lib" + fn + ".so")
        if (!f.exists()) {
            f = File(mLuaDir + "/lib" + fn + ".so")
            if (!f.exists()) throw LuaException("can not find lib " + name)
            LuaUtil.Companion.copyFile(mLuaDir + "/lib" + fn + ".so", libDir + "/lib" + fn + ".so")
        }
        val luaState = L ?: return null
        val require = luaState.getLuaObject("require")
        return require.call(name)
    }

    fun registerReceiver(receiver: LuaBroadcastReceiver?, filter: IntentFilter?): Intent? {
        return super.registerReceiver(receiver, filter)
    }

    fun registerReceiver(ltr: OnReceiveListener?, filter: IntentFilter?): Intent? {
        val receiver = LuaBroadcastReceiver(ltr!!)
        return super.registerReceiver(receiver, filter)
    }

    fun registerReceiver(filter: IntentFilter?): Intent? {
        if (mReceiver != null) unregisterReceiver(mReceiver)
        mReceiver = LuaBroadcastReceiver(this)
        return super.registerReceiver(mReceiver, filter)
    }

    override fun unregisterReceiver(receiver: BroadcastReceiver?) {
        try {
            super.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.i("lua", "unregisterReceiver: " + receiver)
            e.printStackTrace()
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        runFunc("onReceive", context, intent)
    }

    override fun onContentChanged() {
        super.onContentChanged()
        isSetViewed = true
    }

    override fun onStart() {
        super.onStart()
        runFunc("onStart")
    }

    override fun onResume() {
        super.onResume()
        runFunc("onResume")
    }

    override fun onPause() {
        super.onPause()
        runFunc("onPause")
    }

    override fun onStop() {
        super.onStop()
        runFunc("onStop")
    }

    override fun onDestroy() {
        if (!mDestroyed.compareAndSet(false, true)) return
        if (mReceiver != null) unregisterReceiver(mReceiver)

        for (obj in gclist) {
            obj.gc()
        }
        gclist.clear()
        sLuaActivityMap.remove(pageName)
        runFunc("onDestroy")

        // 清理ComposeBridge全局状态（在luaState关闭前执行，避免回调访问已关闭状态）
        try {
            com.nirithy.luacompose.bridge.ComposeBridge.resetState()
        } catch (_: Exception) {}

        if (mLuaDexLoader != null) {
            try {
                mLuaDexLoader!!.cleanupOldFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (handler != null) {
            handler!!.removeCallbacksAndMessages(null)
            handler = null
        }

        if (layout != null) {
            layout!!.removeAllViews()
            layout = null
        }

        isCreate = false
        toast = null
        list = null
        adapter = null
        status = null

        super.onDestroy()
        val luaState = L
        if (luaState != null) {
            luaState.close()
            L = null
        }
    }

    override fun onRestart() {
        super.onRestart()
        runFunc("onRestart")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        runFunc("onSaveInstanceState", outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        runFunc("onRestoreInstanceState", savedInstanceState)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        runFunc("onUserLeaveHint")
    }

    override fun onNightModeChanged(mode: Int) {
        super.onNightModeChanged(mode)
        runFunc("onNightModeChanged", mode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data != null) {
            val name = data.getStringExtra(NAME)
            if (name != null) {
                val res = data.getSerializableExtra(DATA) as Array<Any?>?
                if (res == null) {
                    runFunc("onResult", name)
                } else {
                    val arg = arrayOfNulls<Any>(res.size + 1)
                    arg[0] = name
                    System.arraycopy(res, 0, arg, 1, res.size)
                    val ret = runFunc("onResult", *arg)
                    if (ret != null && ret.javaClass == Boolean::class.java && ret as Boolean) return
                }
            }
        }
        runFunc("onActivityResult", requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        runFunc("onRequestPermissionsResult", requestCode, permissions, grantResults)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (mOnKeyDown != null) {
            try {
                val ret = mOnKeyDown!!.call(keyCode, event)
                if (ret != null && ret.javaClass == Boolean::class.java && ret as Boolean) return true
            } catch (e: LuaException) {
                sendError("onKeyDown", e)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (mOnKeyUp != null) {
            try {
                val ret = mOnKeyUp!!.call(keyCode, event)
                if (ret != null && ret.javaClass == Boolean::class.java && ret as Boolean) return true
            } catch (e: LuaException) {
                sendError("onKeyUp", e)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (mOnKeyLongPress != null) {
            try {
                val ret = mOnKeyLongPress!!.call(keyCode, event)
                if (ret != null && ret.javaClass == Boolean::class.java && ret as Boolean) return true
            } catch (e: LuaException) {
                sendError("onKeyLongPress", e)
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (mOnTouchEvent != null) {
            try {
                val ret = mOnTouchEvent!!.call(event)
                if (ret != null && ret.javaClass == Boolean::class.java && ret as Boolean) return true
            } catch (e: LuaException) {
                sendError("onTouchEvent", e)
            }
        }
        return super.onTouchEvent(event)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityReenter(resultCode: Int, data: Intent?) {
        super.onActivityReenter(resultCode, data)
        if (mOnActivityReenter != null) {
            try {
                mOnActivityReenter!!.call(resultCode, data)
            } catch (e: LuaException) {
                sendError("onActivityReenter", e)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        optionsMenu = menu
        runFunc("onCreateOptionsMenu", menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var ret: Any? = null
        if (!item.hasSubMenu()) ret = runFunc("onOptionsItemSelected", item)
        if (ret != null && ret.javaClass == Boolean::class.java && ret as Boolean) return true
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View?,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        runFunc("onCreateContextMenu", menu, v, menuInfo)
        super.onCreateContextMenu(menu, v, menuInfo)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        runFunc("onContextItemSelected", item)
        return super.onContextItemSelected(item)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val outMetrics = DisplayMetrics()
        wm.getDefaultDisplay().getMetrics(outMetrics)
        mWidth = outMetrics.widthPixels
        mHeight = outMetrics.heightPixels
        runFunc("onConfigurationChanged", newConfig)
    }

    override val width: Int
        get() = mWidth

    override val height: Int
        get() = mHeight

    override val globalData: MutableMap<*, *>?
        get() = (getApplication() as LuaApplication).globalData

    override val sharedData: Any?
        get() = LuaApplication.instance!!.sharedData

    override fun getSharedData(key: String?): Any? {
        return LuaApplication.instance!!.getSharedData(key)
    }

    override fun getSharedData(key: String?, def: Any?): Any? {
        return LuaApplication.instance!!.getSharedData(key, def)
    }

    override fun setSharedData(key: String?, value: Any?): Boolean {
        return LuaApplication.instance!!.setSharedData(key, value)
    }

    fun bindService(flag: Int): Boolean {
        val conn: ServiceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(comp: ComponentName, binder: IBinder) {
                    runFunc("onServiceConnected", comp, (binder as LuaBinder).service)
                }

                override fun onServiceDisconnected(comp: ComponentName) {
                    runFunc("onServiceDisconnected", comp)
                }
            }
        return bindService(conn, flag)
    }

    fun bindService(conn: ServiceConnection?, flag: Int): Boolean {
        val service = Intent(this, LuaService::class.java)
        service.putExtra("luaDir", mLuaDir)
        service.putExtra("luaPath", mLuaPath)
        return super.bindService(service, conn!!, flag)
    }

    fun stopService(): Boolean {
        return stopService(Intent(this, LuaService::class.java))
    }

    fun startService(): ComponentName? {
        return startService(null, null)
    }

    fun startService(arg: Array<Any?>?): ComponentName? {
        return startService(null, arg)
    }

    fun startService(path: String?): ComponentName? {
        return startService(path, null)
    }

    fun startService(path: String?, arg: Array<Any?>?): ComponentName? {
        val intent = Intent(this, LuaService::class.java)
        intent.putExtra("luaDir", mLuaDir)
        intent.putExtra("luaPath", mLuaPath)
        if (path != null) {
            if (path.get(0) != '/') intent.setData(Uri.parse("file://" + mLuaDir + "/" + path + ".lua"))
            else intent.setData(Uri.parse("file://" + path))
        }

        if (arg != null) intent.putExtra(ARG, arg)

        return super.startService(intent)
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(path: String, newDocument: Boolean) {
        newActivity(1, path, null, newDocument)
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(path: String, arg: Array<Any?>?, newDocument: Boolean) {
        newActivity(1, path, arg, newDocument)
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(req: Int, path: String, newDocument: Boolean) {
        newActivity(req, path, null, newDocument)
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(path: String) {
        newActivity(1, path, arrayOfNulls<Any>(0))
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(path: String, arg: Array<Any?>?) {
        newActivity(1, path, arg)
    }

    @JvmOverloads
    @Throws(FileNotFoundException::class)
    fun newActivity(req: Int, path: String, arg: Array<Any?>? = arrayOfNulls<Any>(0)) {
        newActivity(req, path, arg, false)
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(req: Int, path: String, arg: Array<Any?>?, newDocument: Boolean) {
        var path = path
        var intent = Intent(this, LuaActivity::class.java)
        if (newDocument) intent = Intent(this, LuaActivityX::class.java)

        intent.putExtra(NAME, path)
        if (path.get(0) != '/') path = mLuaDir + "/" + path
        val f = File(path)
        if (f.isDirectory() && File(path + "/main.lua").exists()) path += "/main.lua"
        else if ((f.isDirectory() || !f.exists()) && !path.endsWith(".lua")) path += ".lua"
        if (!File(path).exists()) throw FileNotFoundException(path)

        if (newDocument) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
        }

        intent.setData(Uri.parse("file://" + path))

        if (arg != null) intent.putExtra(ARG, arg)
        if (newDocument) startActivity(intent)
        else startActivityForResult(intent, req)
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(path: String, `in`: Int, out: Int, newDocument: Boolean) {
        newActivity(1, path, `in`, out, null, newDocument)
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(path: String, `in`: Int, out: Int, arg: Array<Any?>?, newDocument: Boolean) {
        newActivity(1, path, `in`, out, arg, newDocument)
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(req: Int, path: String, `in`: Int, out: Int, newDocument: Boolean) {
        newActivity(req, path, `in`, out, null, newDocument)
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(path: String, `in`: Int, out: Int) {
        newActivity(1, path, `in`, out, arrayOfNulls<Any>(0))
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(path: String, `in`: Int, out: Int, arg: Array<Any?>?) {
        newActivity(1, path, `in`, out, arg)
    }

    @JvmOverloads
    @Throws(FileNotFoundException::class)
    fun newActivity(
        req: Int,
        path: String,
        `in`: Int,
        out: Int,
        arg: Array<Any?>? = arrayOfNulls<Any>(0)
    ) {
        newActivity(req, path, `in`, out, arg, false)
    }

    @Throws(FileNotFoundException::class)
    fun newActivity(
        req: Int,
        path: String,
        `in`: Int,
        out: Int,
        arg: Array<Any?>?,
        newDocument: Boolean
    ) {
        var path = path
        var intent = Intent(this, LuaActivity::class.java)
        if (newDocument) intent = Intent(this, LuaActivityX::class.java)
        intent.putExtra(NAME, path)
        if (path.get(0) != '/') path = mLuaDir + "/" + path
        val f = File(path)
        if (f.isDirectory() && File(path + "/main.lua").exists()) path += "/main.lua"
        else if ((f.isDirectory() || !f.exists()) && !path.endsWith(".lua")) path += ".lua"
        if (!File(path).exists()) throw FileNotFoundException(path)
        intent.setData(Uri.parse("file://" + path))

        if (newDocument) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
        }

        if (arg != null) intent.putExtra(ARG, arg)
        if (newDocument) startActivity(intent)
        else startActivityForResult(intent, req)
        overridePendingTransition(`in`, out)
    }

    fun finish(finishTask: Boolean) {
        if (!finishTask) {
            super.finish()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = getIntent()
            if (intent != null && (intent.getFlags() and Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0) finishAndRemoveTask()
            else super.finish()
        } else {
            super.finish()
        }
    }

    @JvmOverloads
    @Throws(LuaException::class)
    fun newTask(
        func: LuaObject,
        update: LuaObject? = null,
        callback: LuaObject? = null
    ): LuaAsyncTask {
        return LuaAsyncTask(this, func, update, callback)
    }

    @JvmOverloads
    @Throws(LuaException::class)
    fun newThread(func: LuaObject?, arg: Array<Any?>? = arrayOfNulls<Any>(0)): LuaThread {
        val thread = LuaThread(this, func!!, true, arg)
        return thread
    }

    @JvmOverloads
    @Throws(LuaException::class)
    fun newTimer(func: LuaObject?, arg: Array<Any?>? = arrayOfNulls<Any>(0)): LuaTimer {
        return LuaTimer(this, func, arg)
    }

    @Throws(LuaException::class)
    fun task(delay: Long, func: LuaObject?): LuaAsyncTask {
        return task(delay, emptyArray<Any?>(), func)
    }

    @Throws(LuaException::class)
    fun task(delay: Long, arg: Array<Any?>, func: LuaObject?): LuaAsyncTask {
        val task = LuaAsyncTask(this, delay, func)
        task.execute(*(arg ?: emptyArray()))
        return task
    }

    @JvmOverloads
    @Throws(LuaException::class)
    fun task(
        func: LuaObject,
        arg: Array<Any?>? = null,
        update: LuaObject? = null,
        callback: LuaObject? = null
    ): LuaAsyncTask {
        val task = LuaAsyncTask(this, func, update, callback)
        task.execute(*(arg ?: emptyArray()))
        return task
    }

    @Throws(LuaException::class)
    fun thread(func: LuaObject?): LuaThread {
        val thread = newThread(func, arrayOfNulls<Any>(0))
        thread.start()
        return thread
    }

    @Throws(LuaException::class)
    fun thread(func: LuaObject?, arg: Array<Any?>?): LuaThread {
        val thread = LuaThread(this, func!!, true, arg)
        thread.start()
        return thread
    }

    @Throws(LuaException::class)
    fun timer(func: LuaObject?, period: Long): LuaTimer {
        return timer(func, 0, period, arrayOfNulls<Any>(0))
    }

    @Throws(LuaException::class)
    fun timer(func: LuaObject?, period: Long, arg: Array<Any?>?): LuaTimer {
        return timer(func, 0, period, arg)
    }

    @JvmOverloads
    @Throws(LuaException::class)
    fun timer(
        func: LuaObject?,
        delay: Long,
        period: Long,
        arg: Array<Any?>? = arrayOfNulls<Any>(0)
    ): LuaTimer {
        val timer = LuaTimer(this, func, arg)
        timer.start(delay, period)
        return timer
    }

    @Throws(LuaException::class)
    fun ticker(func: LuaObject, period: Long): Ticker {
        val timer = Ticker()
        regGc(timer)
        timer.setOnTickListener(
            object : OnTickListener {
                override fun onTick() {
                    try {
                        func.call()
                    } catch (e: LuaException) {
                        e.printStackTrace()
                        sendError("onTick", e)
                    }
                }
            })
        timer.start()
        timer.period = period
        return timer
    }

    @Throws(LuaException::class)
    fun setContentView(layout: String?) {
        setContentView(layout, null)
    }

    @Throws(LuaException::class)
    fun setContentView(layout: String?, env: LuaObject?) {
        val loadlayout = L?.getLuaObject("loadlayout") ?: return
        val view = loadlayout.call(layout, env) as View?
        super.setContentView(view)
    }

    @Throws(LuaException::class)
    fun setContentView(layout: LuaObject) {
        setContentView(layout, null)
    }

    @Throws(LuaException::class)
    fun setContentView(layout: LuaObject, env: LuaObject?) {
        val luaState = L ?: throw LuaException("Lua state is null")
        val loadlayout = luaState.getLuaObject("loadlayout")
        var view: View? = null
        if (layout.isString()) view = loadlayout.call(layout.getString(), env) as View?
        else if (layout.isTable()) view = loadlayout.call(layout, env) as View?
        else throw LuaException("layout may be table or string.")
        super.setContentView(view)
    }

    fun result(data: Array<Any?>?) {
        val res = Intent()
        res.putExtra(NAME, getIntent().getStringExtra(NAME))
        res.putExtra(DATA, data)
        setResult(0, res)
        finish()
    }

    @Throws(Exception::class)
    private fun initLua() {
        val luaState = LuaStateFactory.newLuaState()
        L = luaState
        luaState.openLibs()
        luaState.pushJavaObject(this)
        luaState.setGlobal("activity")
        luaState.getGlobal("activity")
        luaState.setGlobal("this")

        luaState.pushJavaObject(com.luaforge.studio.lxclua.core.R::class.java)
        luaState.setGlobal("R")

        luaState.newTable()
        luaState.pushJavaObject(com.google.android.material.R::class.java)
        luaState.setField(-2, "R")
        luaState.setGlobal("material")

        luaState.newTable()
        luaState.pushJavaObject(androidx.appcompat.R::class.java)
        luaState.setField(-2, "R")
        luaState.setGlobal("androidx")

        luaState.newTable()
        luaState.pushJavaObject(R::class.java)
        luaState.setField(-2, "R")
        luaState.setGlobal("android")

        luaState.pushContext(this)
        luaState.getGlobal("luajava")
        luaState.pushString(mLuaExtDir)
        luaState.setField(-2, "luaextdir")
        luaState.pushString(mLuaDir)
        luaState.setField(-2, "luadir")
        luaState.pushString(mLuaPath)
        luaState.setField(-2, "luapath")
        luaState.pop(1)
        initENV()

        // 注入 Compose API 到 Lua 环境，让 Lua 脚本可以使用 compose.* 系列 API
        DebugLogger.log("LuaActivity", "ComposeBridge.inject 开始")
        setAndroidContext(this)
        ComposeBridge.inject(luaState)
        DebugLogger.log("LuaActivity", "ComposeBridge.inject 完成")

        val print: JavaFunction = LuaPrint(this, luaState)
        print.register("print")

        luaState.getGlobal("package")
        luaState.pushString(mLuaLpath)
        luaState.setField(-2, "path")
        luaState.pushString(mLuaCpath)
        luaState.setField(-2, "cpath")
        luaState.pop(1)

        val set: JavaFunction =
            object : JavaFunction(luaState) {
                @Throws(LuaException::class)
                override fun execute(): Int {
                    val thread = luaState.toJavaObject(2) as LuaThread
                    thread.set(luaState.toString(3), luaState.toJavaObject(4))
                    return 0
                }
            }
        set.register("set")

        val call: JavaFunction =
            object : JavaFunction(luaState) {
                @Throws(LuaException::class)
                override fun execute(): Int {
                    val thread = luaState.toJavaObject(2) as LuaThread
                    val top = luaState.getTop()
                    if (top > 3) {
                        val args = arrayOfNulls<Any>(top - 3)
                        for (i in 4..top) {
                            args[i - 4] = luaState.toJavaObject(i)
                        }
                        thread.call(luaState.toString(3), args)
                    } else if (top == 3) {
                        thread.call(luaState.toString(3))
                    }
                    return 0
                }
            }
        call.register("call")
    }

    fun setDebug(isDebug: Boolean) {
        mDebug = isDebug
    }

    @Throws(LuaException::class)
    private fun initENV() {
        if (!File(mLuaDir + "/settings.json").exists()) return
        val luaState = L ?: return

        try {
            luaState.newTable()
            val env = luaState.getLuaObject(-1)
            luaState.setUpValue(-2, 1)

            val jsonMap: MutableMap<String?, Any?>? =
                parseObject(
                    FileUtil.read(mLuaDir + "/settings.json")
                ) as? MutableMap<String?, Any?>

            val application = jsonMap?.get("application") as MutableMap<String?, Any?>?

            val label = application!!.get("label") as String?
            val debug = application.get("debugmode") as Boolean?

            setTitle(label!!)
            mDebug = debug!!

            val globalUtils = jsonMap?.get("global_utils") as MutableList<String?>?

            if (globalUtils != null && !globalUtils.isEmpty()) {
                val registrar = LuaFunctionRegistrar(luaState, this, mLuaDir)
                registrar.registerSelectedFunctions(globalUtils)
            }
        } catch (e: Exception) {
            sendMsg(e.message!!)
        }
    }

    override fun setTitle(title: CharSequence) {
        super.setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val tDesc = ActivityManager.TaskDescription(title.toString())
            setTaskDescription(tDesc)
        }
    }

    fun doFile(filePath: String?): Any? {
        return doFile(filePath, *arrayOfNulls<Any>(0))
    }

    override fun doFile(path: String?, vararg arg: Any?): Any? {
        var filePath = path ?: return null
        var ok = 0
        val luaState = L ?: return null
        try {
            if (filePath.get(0) != '/') filePath = mLuaDir + "/" + filePath

            luaState.setTop(0)
            DebugLogger.log("LuaActivity", "doFile LloadFile 开始: " + filePath)
            ok = luaState.LloadFile(filePath)
            DebugLogger.log("LuaActivity", "doFile LloadFile 完成: ok=" + ok)

            if (ok == 0) {
                luaState.getGlobal("debug")
                luaState.getField(-1, "traceback")
                luaState.remove(-2)
                luaState.insert(-2)
                val l = arg.size
                for (i in 0..<l) {
                    luaState.pushObjectValue(arg[i])
                }
                DebugLogger.log("LuaActivity", "doFile pcall 开始: nargs=" + l + ", nresults=1")
                ok = luaState.pcall(l, 1, -2 - l)
                DebugLogger.log("LuaActivity", "doFile pcall 完成: ok=" + ok)
                if (ok == 0) {
                    return luaState.toJavaObject(-1)
                }
            }
            val res = Intent()
            res.putExtra(DATA, luaState.toString(-1))
            setResult(ok, res)
            throw LuaException(errorReason(ok) + ": " + luaState.toString(-1))
        } catch (e: LuaException) {
            DebugLogger.logError("LuaActivity", "doFile 失败: " + e.message, e)
            setTitle(errorReason(ok))
            setContentView(layout)
            sendMsg(e.message!!)
            val s = e.message
            val p = "android.permission."
            var i = s!!.indexOf(p)
            if (i > 0) {
                i = i + p.length
                val n = s.indexOf(".", i)
                if (n > i) {
                    var m = s.substring(i, n)
                    luaState.getGlobal("require")
                    luaState.pushString("permission")
                    luaState.pcall(1, 0, 0)
                    luaState.getGlobal("permission_info")
                    luaState.getField(-1, m)
                    if (luaState.isString(-1)) m = m + " (" + luaState.toString(-1) + ")"
                    sendMsg("权限错误: " + m)
                    return null
                }
            }
            if (isUpdata) {
            }
        }

        return null
    }

    fun doAsset(name: String, args: Array<Any?>): Any? {
        var ok = 0
        val luaState = L ?: return null
        try {
            val bytes = readAsset(name)
            luaState.setTop(0)
            ok = luaState.LloadBuffer(bytes, name)

            if (ok == 0) {
                luaState.getGlobal("debug")
                luaState.getField(-1, "traceback")
                luaState.remove(-2)
                luaState.insert(-2)
                val l = args.size
                for (i in 0..<l) {
                    luaState.pushObjectValue(args[i])
                }
                ok = luaState.pcall(l, 0, -2 - l)
                if (ok == 0) {
                    return luaState.toJavaObject(-1)
                }
            }
            throw LuaException(errorReason(ok) + ": " + luaState.toString(-1))
        } catch (e: Exception) {
            setTitle(errorReason(ok))
            setContentView(layout)
            sendMsg(e.message!!)
        }

        return null
    }

    fun runFunc(funcName: String, vararg args: Any?): Any? {
        val luaState = L ?: return null
        synchronized(luaState) {
            try {
                luaState.setTop(0)
                luaState.pushGlobalTable()
                luaState.pushString(funcName)
                luaState.rawGet(-2)
                if (luaState.isFunction(-1)) {
                    luaState.getGlobal("debug")
                    luaState.getField(-1, "traceback")
                    luaState.remove(-2)
                    luaState.insert(-2)

                    val l = args.size
                    for (i in 0..<l) {
                        luaState.pushObjectValue(args[i])
                    }

                    val ok = luaState.pcall(l, 1, -2 - l)
                    if (ok == 0) {
                        return luaState.toJavaObject(-1)
                    }
                    throw LuaException(errorReason(ok) + ": " + luaState.toString(-1))
                }
            } catch (e: LuaException) {
                sendError(funcName, e)
            }
        }
        return null
    }

    fun doString(funcSrc: String?, vararg args: Any?): Any? {
        val luaState = L ?: return null
        try {
            luaState.setTop(0)
            var ok = luaState.LloadString(funcSrc)

            if (ok == 0) {
                luaState.getGlobal("debug")
                luaState.getField(-1, "traceback")
                luaState.remove(-2)
                luaState.insert(-2)

                val l = args.size
                for (i in 0..<l) {
                    luaState.pushObjectValue(args[i])
                }

                ok = luaState.pcall(l, 1, -2 - l)
                if (ok == 0) {
                    return luaState.toJavaObject(-1)
                }
            }
            throw LuaException(errorReason(ok) + ": " + luaState.toString(-1))
        } catch (e: LuaException) {
            sendMsg(e.message!!)
        }
        return null
    }

    private fun errorReason(error: Int): String {
        when (error) {
            6 -> return "error error"
            5 -> return "GC error"
            4 -> return "Out of memory"
            3 -> return "Syntax error"
            2 -> return "Runtime error"
            1 -> return "Yield error"
        }
        return "Unknown error " + error
    }

    @Throws(IOException::class)
    fun readAsset(name: String): ByteArray {
        val am = assets
        val `is` = am.open(name)
        val ret: ByteArray = readAll(`is`)
        `is`.close()
        return ret
    }

    fun showLogs() {
        AlertDialog.Builder(this)
            .setTitle("Logs")
            .setAdapter(adapter, null)
            .setPositiveButton(R.string.ok, null)
            .create()
            .show()
    }

    override fun sendMsg(msg: String?) {
        val message = Message()
        val bundle = Bundle()
        bundle.putString(DATA, msg)
        message.setData(bundle)
        message.what = 0
        handler!!.sendMessage(message)
        Log.i("lua", msg ?: "")
    }

    override fun sendError(title: String?, msg: Exception?) {
        val ret = runFunc("onError", title, msg)
        if (ret != null && ret.javaClass == Boolean::class.java && ret as Boolean) {
        } else sendMsg((title ?: "") + ": " + (msg?.message ?: ""))
    }

    /*
  @SuppressLint("ShowToast")
  public void showToast(String text) {
      long now = System.currentTimeMillis();
      status.append(text + "\n");
      adapter.add(text);
      if (toast == null || now - lastShow > 1000) {
          toastbuilder.setLength(0);
          toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
          toastbuilder.append(text);
          toast.show();
      } else {
          toastbuilder.append("\n");
          toastbuilder.append(text);
          toast.setText(toastbuilder.toString());
          toast.setDuration(Toast.LENGTH_LONG);
      }
      lastShow = now;
  }
  */
    @SuppressLint("ShowToast")
    fun showToast(text: String?) {
        val now = System.currentTimeMillis()
        if (toast == null || now - lastShow > 1000) {
            toastbuilder.setLength(0)
            toastbuilder.append(text)

            val inflater = LayoutInflater.from(this)
            val layout = inflater.inflate(com.luaforge.studio.lxclua.core.R.layout.toast, null)
            val tv = layout.findViewById<TextView>(com.luaforge.studio.lxclua.core.R.id.toast_text)
            tv.setText(toastbuilder.toString())

            toast = Toast(getApplicationContext())
            toast!!.setView(layout)
            toast!!.setDuration(Toast.LENGTH_LONG)
            toast!!.show()
        } else {
            toastbuilder.append("\n").append(text)
            val view = toast!!.getView()
            if (view != null) {
                val tv =
                    view.findViewById<TextView?>(com.luaforge.studio.lxclua.core.R.id.toast_text)
                if (tv != null) {
                    tv.setText(toastbuilder.toString())
                    tv.scrollTo(0, tv.getLineHeight() * tv.getLineCount())
                }
            }
            toast!!.show()
        }
        lastShow = now
    }

    private fun setField(key: String?, value: Any?) {
        val luaState = L ?: return
        synchronized(luaState) {
            try {
                luaState.pushObjectValue(value)
                luaState.setGlobal(key)
            } catch (e: LuaException) {
                sendError("setField", e)
            }
        }
    }

    fun call(func: String?) {
        push(2, func)
    }

    override fun call(func: String?, vararg args: Any?) {
        if (args.isEmpty()) push(2, func)
        else push(3, func, args as Array<Any?>?)
    }

    override fun set(key: String?, value: Any?) {
        push(1, key, arrayOf<Any?>(value))
    }

    @Throws(LuaException::class)
    fun get(key: String?): Any? {
        val luaState = L ?: return null
        synchronized(luaState) {
            luaState.getGlobal(key)
            return luaState.toJavaObject(-1)
        }
    }

    fun push(what: Int, s: String?) {
        val message = Message()
        val bundle = Bundle()
        bundle.putString(DATA, s)
        message.setData(bundle)
        message.what = what

        handler!!.sendMessage(message)
    }

    fun push(what: Int, s: String?, args: Array<Any?>?) {
        val message = Message()
        val bundle = Bundle()
        bundle.putString(DATA, s)
        bundle.putSerializable("args", args)
        message.setData(bundle)
        message.what = what

        handler!!.sendMessage(message)
    }

    inner class MainHandler : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                0 -> {
                    val data = msg.getData().getString(DATA)
                    if (mDebug) showToast(data)
                    status!!.append(data + "\n")
                    adapter!!.add(data)
                }

                1 -> {
                    val data = msg.getData()
                    setField(
                        data.getString(DATA),
                        (data.getSerializable("args") as Array<Any?>?)!![0]
                    )
                }

                2 -> {
                    val src = msg.getData().getString(DATA)
                    runFunc(src!!)
                }

                3 -> {
                    val src = msg.getData().getString(DATA)
                    val args = msg.getData().getSerializable("args")
                    runFunc(src!!, *(args as kotlin.Array<kotlin.Any?>?)!!)
                }
            }
        }
    }

    companion object {
        private const val ARG = "arg"
        private const val DATA = "data"
        private const val NAME = "name"
        private val prjCache = ArrayList<String?>()
        private val sLuaActivityMap = java.util.HashMap<String?, LuaActivity?>()

        @Throws(IOException::class)
        private fun readAll(input: InputStream): ByteArray {
            val output = ByteArrayOutputStream(4096)
            val buffer = ByteArray(4096)
            var n = 0
            while (-1 != (input.read(buffer).also { n = it })) {
                output.write(buffer, 0, n)
            }
            val ret = output.toByteArray()
            output.close()
            return ret
        }

        fun getActivity(name: String?): LuaActivity? {
            return sLuaActivityMap.get(name)
        }
    }
}
