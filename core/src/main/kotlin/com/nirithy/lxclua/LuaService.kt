package com.nirithy.lxclua

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Binder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.util.Log
import android.widget.Toast
import com.nirithy.lxclua.LuaBroadcastReceiver.OnReceiveListener
import com.nirithy.lxclua.Ticker.OnTickListener
import com.luajava.JavaFunction
import com.luajava.LuaException
import com.luajava.LuaObject
import com.luajava.LuaState
import com.luajava.LuaStateFactory
import dalvik.system.DexClassLoader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.Volatile

class LuaService : Service(), LuaContext, OnReceiveListener {
    override var luaDir: String? = null
    override var luaCpath: String? = null
    var binder: LuaBinder? = LuaBinder()
    private var mLuaDexLoader: LuaDexLoader? = null
    private val gclist = ArrayList<LuaGcable?>()
    override var luaLpath: String? = null
    private var handler: MainHandler? = null
    private var luaMdDir: String? = null
    private var L: LuaState? = null
    override var luaPath: String? = null
    private var localDir: String? = null
    private var odexDir: String? = null
    private var libDir: String? = null
    override var luaExtDir: String? = null
    private var mReceiver: BroadcastReceiver? = null
    private val output = StringBuilder()
    private var toast: Toast? = null
    private val toastbuilder = StringBuilder()
    private var lastShow: Long = 0
    private var mResources: LuaResources? = null

    @Volatile
    private var mDestroyed = false

    override val classLoaders: ArrayList<ClassLoader?>?
        get() = mLuaDexLoader!!.classLoaders

    @Throws(LuaException::class)
    fun loadDex(path: String?): DexClassLoader? {
        return mLuaDexLoader!!.loadDex(path!!)
    }

    val librarys: HashMap<String?, String?>?
        get() = mLuaDexLoader!!.librarys

    fun loadResources(path: String?) {
        mLuaDexLoader!!.loadResources(path)
    }

    override fun getAssets(): AssetManager {
        if (mLuaDexLoader != null && mLuaDexLoader!!.assets != null) return mLuaDexLoader!!.assets!!
        return applicationContext.assets
    }

    val luaResources: LuaResources?
        get() {
            var superRes = applicationContext.resources
            if (mLuaDexLoader != null && mLuaDexLoader!!.resources != null) superRes =
                mLuaDexLoader!!.resources!!
            mResources = LuaResources(
                getAssets(), superRes.getDisplayMetrics(),
                superRes.getConfiguration()
            )
            mResources!!.setSuperResources(superRes)
            return mResources
        }

    val superResources: Resources
        get() = applicationContext.resources

    override fun getResources(): Resources {
        if (mLuaDexLoader != null && mLuaDexLoader!!.resources != null) return mLuaDexLoader!!.resources!!
        if (mResources != null) return mResources!!
        return applicationContext.resources
    }

    fun registerReceiver(receiver: LuaBroadcastReceiver?, filter: IntentFilter?): Intent? {
        // TODO: Implement this method
        return super.registerReceiver(receiver, filter)
    }

    fun registerReceiver(ltr: OnReceiveListener?, filter: IntentFilter?): Intent? {
        // TODO: Implement this method
        val receiver = LuaBroadcastReceiver(ltr ?: return null)
        return super.registerReceiver(receiver, filter)
    }

    fun registerReceiver(filter: IntentFilter?): Intent? {
        // TODO: Implement this method
        if (mReceiver != null) unregisterReceiver(mReceiver)
        mReceiver = LuaBroadcastReceiver(this)
        return super.registerReceiver(mReceiver, filter)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        // TODO: Implement this method
        runFunc("onReceive", context, intent)
    }

    override fun regGc(obj: LuaGcable?) {
        // TODO: Implement this method
        gclist.add(obj)
    }

    override fun resolveLuaPath(path: String?): String? {
        if (path == null) return null
        return File(luaDir, path).getAbsolutePath()
    }

    override fun resolveLuaPath(dir: String?, name: String?): String? {
        if (dir == null || name == null) return null
        return File(resolveLuaDir(dir), name).getAbsolutePath()
    }

    override fun resolveLuaExtPath(path: String?): String? {
        if (path == null) return null
        return File(luaExtDir, path).getAbsolutePath()
    }

    override fun resolveLuaExtPath(dir: String?, name: String?): String? {
        if (dir == null || name == null) return null
        return File(resolveLuaExtDir(dir), name).getAbsolutePath()
    }

    override fun resolveLuaDir(name: String?): String? {
        // TODO: Implement this method
        val dir = File(luaDir + "/" + name)
        if (!dir.exists()) if (!dir.mkdirs()) return null
        return dir.getAbsolutePath()
    }

    override fun resolveLuaExtDir(name: String?): String? {
        // TODO: Implement this method
        val dir = File(luaExtDir + "/" + name)
        if (!dir.exists()) if (!dir.mkdirs()) return null
        return dir.getAbsolutePath()
    }

    override val context: Context?
        get() = this

    override val luaState: LuaState?
        get() = L

    override fun onBind(p1: Intent?): IBinder? {
        // TODO: Implement this method
        startForeground(1, Notification())
        return LuaBinder()
    }

    override fun onCreate() {
        // TODO: Implement this method
        super.onCreate()
        service = this@LuaService
        //定义文件夹
        val app = getApplication() as LuaApplication
        localDir = app.localDir
        odexDir = app.odexDir
        libDir = app.libDir
        luaMdDir = app.mdDir
        luaCpath = app.luaCpath
        luaDir = localDir
        luaLpath = app.luaLpath
        luaExtDir = app.luaExtDir

        handler = MainHandler()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // TODO: Implement this method
        service = this@LuaService
        if (L == null) {
            startForeground(1, Notification())
            luaPath = intent.getStringExtra("luaPath")
            luaDir = intent.getStringExtra("luaDir")
            luaLpath =
                (luaDir + "/?.lua;" + luaDir + "/lua/?.lua;" + luaDir + "/?/settings.json;") + luaLpath

            val uri = intent.getData()
            try {
                initLua()
                mLuaDexLoader = LuaDexLoader(this)
                mLuaDexLoader!!.loadLibs()

                if (uri != null) doFile(uri.getPath()!!)
                else doFile("service.lua")
            } catch (e: Exception) {
                sendMsg(e.message!!)
            }
        }
        runFunc("onStartCommand", intent, flags, startId)
        runFunc("onStart", *(intent.getSerializableExtra("arg") as kotlin.Array<kotlin.Any?>?)!!)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // TODO: Implement this method
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (mDestroyed) return
        mDestroyed = true
        runFunc("onDestroy")
        if (mReceiver != null) unregisterReceiver(mReceiver)
        super.onDestroy()
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
    fun newThread(func: LuaObject?, arg: Array<Any?>? = null): LuaThread {
        val thread = LuaThread(this, func!!, true, arg)
        return thread
    }

    @JvmOverloads
    @Throws(LuaException::class)
    fun newTimer(func: LuaObject?, arg: Array<Any?>? = null): LuaTimer {
        return LuaTimer(this, func, arg)
    }

    @Throws(LuaException::class)
    fun task(delay: Long, func: LuaObject?): LuaAsyncTask {
        return task(delay, emptyArray<Any?>(), func)
    }

    @Throws(LuaException::class)
    fun task(delay: Long, arg: Array<Any?>, func: LuaObject?): LuaAsyncTask {
        val task = LuaAsyncTask(this, delay, func)
        task.execute(*arg)
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
        val thread = newThread(func, null)
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
        return timer(func, 0, period, null)
    }

    @Throws(LuaException::class)
    fun timer(func: LuaObject?, period: Long, arg: Array<Any?>?): LuaTimer {
        return timer(func, 0, period, arg)
    }

    @JvmOverloads
    @Throws(LuaException::class)
    fun timer(func: LuaObject?, delay: Long, period: Long, arg: Array<Any?>? = null): LuaTimer {
        val timer = LuaTimer(this, func, arg)
        timer.start(delay, period)
        return timer
    }

    @Throws(LuaException::class)
    fun ticker(func: LuaObject, period: Long): Ticker {
        val timer = Ticker()
        timer.setOnTickListener(object : OnTickListener {
            override fun onTick() {
                try {
                    func.call()
                } catch (e: LuaException) {
                    e.printStackTrace()
                    sendError("onTick", e)
                }
            }
        })
        timer.period = period
        timer.start()
        return timer
    }

    override val width: Int
        get() = getResources().getDisplayMetrics().widthPixels

    override val height: Int
        get() = getResources().getDisplayMetrics().heightPixels

    override val globalData: MutableMap<*, *>?
        get() = LuaApplication.instance!!.globalData

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


    //初始化lua使用的Java函数
    @Throws(Exception::class)
    private fun initLua() {
        L = LuaStateFactory.newLuaState()
        L!!.openLibs()
        L!!.pushJavaObject(this)
        L!!.setGlobal("service")
        L!!.getGlobal("service")
        L!!.setGlobal("this")
        L!!.pushContext(this)


        L!!.getGlobal("luajava")
        L!!.pushString(luaExtDir)
        L!!.setField(-2, "luaextdir")
        L!!.pushString(luaDir)
        L!!.setField(-2, "luadir")
        L!!.pushString(luaPath)
        L!!.setField(-2, "luapath")
        L!!.pop(1)

        val assetLoader: JavaFunction = LuaAssetLoader(this, L)

        L!!.getGlobal("package")
        L!!.pushString(luaLpath)
        L!!.setField(-2, "path")
        L!!.pushString(luaCpath)
        L!!.setField(-2, "cpath")
        L!!.pop(1)

        val print: JavaFunction = object : JavaFunction(L) {
            @Throws(LuaException::class)
            override fun execute(): Int {
                if (L.getTop() < 2) {
                    sendMsg("")
                    return 0
                }
                for (i in 2..L.getTop()) {
                    val type = L.type(i)
                    var `val`: String? = null
                    val stype = L.typeName(type)
                    when (stype) {
                        "userdata" -> {
                            val obj = L.toJavaObject(i)
                            if (obj != null) `val` = obj.toString()
                        }

                        "boolean" -> `val` = if (L.toBoolean(i)) "true" else "false"
                        else -> `val` = L.toString(i)
                    }
                    if (`val` == null) `val` = stype
                    output.append("\t")
                    output.append(`val`)
                    output.append("\t")
                }
                sendMsg(output.toString().substring(1, output.length - 1))
                output.setLength(0)
                return 0
            }
        }

        print.register("print")
        val set: JavaFunction = object : JavaFunction(L) {
            @Throws(LuaException::class)
            override fun execute(): Int {
                val thread = L.toJavaObject(2) as LuaThread

                thread.set(L.toString(3), L.toJavaObject(4))
                return 0
            }
        }
        set.register("set")

        val call: JavaFunction = object : JavaFunction(L) {
            @Throws(LuaException::class)
            override fun execute(): Int {
                val thread = L.toJavaObject(2) as LuaThread

                val top = L.getTop()
                if (top > 3) {
                    val args = arrayOfNulls<Any>(top - 3)
                    for (i in 4..top) {
                        args[i - 4] = L.toJavaObject(i)
                    }
                    thread.call(L.toString(3), args)
                } else if (top == 3) {
                    thread.call(L.toString(3))
                }

                return 0
            }
        }
        call.register("call")
    }

    //运行lua脚本
    fun doFile(filePath: String): Any? {
        return doFile(filePath, *arrayOfNulls<Any>(0))
    }

    override fun doFile(path: String?, vararg arg: Any?): Any? {
        var filePath = path ?: return null
        var ok = 0
        try {
            if (filePath.get(0) != '/') filePath = luaDir + "/" + filePath

            L!!.setTop(0)
            ok = L!!.LloadFile(filePath)

            if (ok == 0) {
                L!!.getGlobal("debug")
                L!!.getField(-1, "traceback")
                L!!.remove(-2)
                L!!.insert(-2)
                val l = arg.size
                for (i in 0..<l) {
                    L!!.pushObjectValue(arg[i])
                }
                ok = L!!.pcall(l, 1, -2 - l)
                if (ok == 0) {
                    return L!!.toJavaObject(-1)
                }
            }
            throw LuaException(errorReason(ok) + ": " + L!!.toString(-1))
        } catch (e: LuaException) {
            sendMsg(e.message!!)
        }

        return null
    }

    fun doAsset(name: String, vararg args: Any?): Any? {
        var ok = 0
        try {
            val bytes = readAsset(name)
            L!!.setTop(0)
            ok = L!!.LloadBuffer(bytes, name)

            if (ok == 0) {
                L!!.getGlobal("debug")
                L!!.getField(-1, "traceback")
                L!!.remove(-2)
                L!!.insert(-2)
                var l = 0
                if (args != null) l = args.size
                for (i in 0..<l) {
                    L!!.pushObjectValue(args[i])
                }
                ok = L!!.pcall(l, 0, -2 - l)
                if (ok == 0) {
                    return L!!.toJavaObject(-1)
                }
            }
            throw LuaException(errorReason(ok) + ": " + L!!.toString(-1))
        } catch (e: Exception) {
            sendMsg(e.message!!)
        }

        return null
    }

    //运行lua函数
    fun runFunc(funcName: String?, vararg args: Any?): Any? {
        if (L != null) {
            try {
                L!!.setTop(0)
                L!!.getGlobal(funcName)
                if (L!!.isFunction(-1)) {
                    L!!.getGlobal("debug")
                    L!!.getField(-1, "traceback")
                    L!!.remove(-2)
                    L!!.insert(-2)

                    var l = 0
                    if (args != null) l = args.size
                    for (i in 0..<l) {
                        L!!.pushObjectValue(args[i])
                    }

                    val ok = L!!.pcall(l, 1, -2 - l)
                    if (ok == 0) {
                        return L!!.toJavaObject(-1)
                    }
                    throw LuaException(errorReason(ok) + ": " + L!!.toString(-1))
                }
            } catch (e: LuaException) {
                sendMsg(funcName + " " + e.message)
            }
        }
        return null
    }

    //读取asset文件
    //运行lua代码
    fun doString(funcSrc: String?, vararg args: Any?): Any? {
        try {
            L!!.setTop(0)
            var ok = L!!.LloadString(funcSrc)

            if (ok == 0) {
                L!!.getGlobal("debug")
                L!!.getField(-1, "traceback")
                L!!.remove(-2)
                L!!.insert(-2)

                var l = 0
                if (args != null) l = args.size
                for (i in 0..<l) {
                    L!!.pushObjectValue(args[i])
                }

                ok = L!!.pcall(l, 1, -2 - l)
                if (ok == 0) {
                    return L!!.toJavaObject(-1)
                }
            }
            throw LuaException(errorReason(ok) + ": " + L!!.toString(-1))
        } catch (e: LuaException) {
            sendMsg(e.message!!)
        }
        return null
    }

    //生成错误信息
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
        val am = getAssets()
        val `is` = am.open(name)
        val ret: ByteArray = readAll(`is`)
        `is`.close()
        //am.close();
        return ret
    }

    //显示信息
    override fun sendMsg(msg: String?) {
        val message = Message()
        val bundle = Bundle()
        bundle.putString("data", msg)
        message.setData(bundle)
        message.what = 0
        handler!!.sendMessage(message)
        Log.i("lua", msg ?: "")
    }

    override fun sendError(title: String?, msg: Exception?) {
        runFunc("onError", title, msg)
    }

    @Throws(LuaException::class)
    fun loadLib(name: String): Any? {
        val i = name.indexOf(".")
        var fn = name
        if (i > 0) fn = name.substring(0, i)
        var f = File(libDir + "/lib" + fn + ".so")
        if (!f.exists()) {
            f = File(luaDir + "/lib" + fn + ".so")
            if (!f.exists()) throw LuaException("can not find lib " + name)
            copyFile(luaDir + "/lib" + fn + ".so", libDir + "/lib" + fn + ".so")
        }
        val require = L!!.getLuaObject("require")
        return require.call(name)
    }

    private fun copyFile(oldPath: String, newPath: String?) {
        try {
            var bytesum = 0
            var byteread = 0
            val oldfile = File(oldPath)
            if (oldfile.exists()) { //文件存在时
                val inStream: InputStream = FileInputStream(oldPath) //读入原文件
                val fs = FileOutputStream(newPath)
                val buffer = ByteArray(4096)
                var length: Int
                while ((inStream.read(buffer).also { byteread = it }) != -1) {
                    bytesum += byteread //字节数 文件大小
                    println(bytesum)
                    fs.write(buffer, 0, byteread)
                }
                inStream.close()
            }
        } catch (e: Exception) {
            println("复制文件操作出错")
            e.printStackTrace()
        }
    }

    //显示toast
    @SuppressLint("ShowToast")
    fun showToast(text: String?) {
        try {
            val now = System.currentTimeMillis()
            if (toast == null || now - lastShow > 1000) {
                toastbuilder.setLength(0)
                toast = Toast.makeText(this, text, Toast.LENGTH_LONG)
                toastbuilder.append(text)
            } else {
                toastbuilder.append("\n")
                toastbuilder.append(text)
                toast!!.setText(toastbuilder.toString())
                toast!!.setDuration(Toast.LENGTH_LONG)
            }
            lastShow = now
            toast!!.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setField(key: String?, value: Any?) {
        try {
            L!!.pushObjectValue(value)
            L!!.setGlobal(key)
        } catch (e: LuaException) {
            sendMsg(e.message!!)
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
        L!!.getGlobal(key)
        return L!!.toJavaObject(-1)
    }

    fun push(what: Int, s: String?) {
        val message = Message()
        val bundle = Bundle()
        bundle.putString("data", s)
        message.setData(bundle)
        message.what = what

        handler!!.sendMessage(message)
    }

    fun push(what: Int, s: String?, args: Array<Any?>?) {
        val message = Message()
        val bundle = Bundle()
        bundle.putString("data", s)
        bundle.putSerializable("args", args)
        message.setData(bundle)
        message.what = what

        handler!!.sendMessage(message)
    }

    inner class LuaBinder : Binder() {
        val service: LuaService
            get() = this@LuaService
    }

    inner class MainHandler : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                0 -> {
                    val data = msg.getData().getString("data")
                    showToast(data)
                }

                1 -> {
                    val data = msg.getData()
                    setField(
                        data.getString("data"),
                        (data.getSerializable("args") as Array<Any?>?)!![0]
                    )
                }

                2 -> {
                    val src = msg.getData().getString("data")
                    runFunc(src)
                }

                3 -> {
                    val src = msg.getData().getString("data")
                    val args = msg.getData().getSerializable("args")
                    runFunc(src, *(args as kotlin.Array<kotlin.Any?>?)!!)
                }
            }
        }
    }

    companion object {
        var service: LuaService? = null
            private set

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
    }
}
