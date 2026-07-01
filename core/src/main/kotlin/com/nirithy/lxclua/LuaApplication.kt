package com.nirithy.lxclua

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.luajava.LuaState
import com.luajava.LuaTable
import java.io.File

class LuaApplication : Application(), LuaContext {
    // TODO: Implement this method
    var localDir: String? = null
        protected set

    // TODO: Implement this method
    var odexDir: String? = null
        protected set

    // TODO: Implement this method
    var libDir: String? = null
        protected set

    // TODO: Implement this method
    var mdDir: String? = null
        protected set
    protected var mLuaCpath: String? = null
    protected var mLuaLpath: String? = null
    protected var mLuaExtDir: String? = null
    private val isUpdata = false
    private var mSharedPreferences: SharedPreferences? = null

    fun getUriForPath(path: String): Uri? {
        return FileProvider.getUriForFile(this, getPackageName(), File(path))
    }

    fun getUriForFile(path: File): Uri? {
        return FileProvider.getUriForFile(this, getPackageName(), path)
    }

    fun getPathFromUri(uri: Uri?): String? {
        var path: String? = null
        if (uri != null) {
            val p = arrayOf<String?>(
                getPackageName()
            )
            when (uri.getScheme()) {
                "content" -> {
                    val cursor = getContentResolver().query(uri, p, null, null, null)

                    if (cursor != null) {
                        val idx = cursor.getColumnIndexOrThrow(getPackageName())
                        if (idx < 0) return null
                        path = cursor.getString(idx)
                        cursor.moveToFirst()
                        cursor.close()
                    }
                }

                "file" -> path = uri.getPath()
            }
        }
        return path
    }


    override val classLoaders: ArrayList<ClassLoader?>?
        get() = null

    override fun regGc(obj: LuaGcable?) {
        // TODO: Implement this method
    }

    override val luaPath: String?
        get() = null

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

    override val width: Int
        get() = getResources().getDisplayMetrics().widthPixels

    override val height: Int
        get() = getResources().getDisplayMetrics().heightPixels

    override fun resolveLuaDir(dir: String?): String? {
        // TODO: Implement this method
        return localDir
    }

    override fun resolveLuaExtDir(dir: String?): String? {
        if (dir == null) return null
        val d = File(luaExtDir, dir)
        if (!d.exists()) if (!d.mkdirs()) return d.getAbsolutePath()
        return d.getAbsolutePath()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashHandler.Companion.instance.init(getApplicationContext())
        mSharedPreferences = getSharedPreferences(this)
        //初始化AndroLua工作目录
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val sdDir = Environment.getExternalStorageDirectory().getAbsolutePath()
            mLuaExtDir = sdDir + "/LXC-LUA"
        } else {
            val fs = File("/storage").listFiles()
            for (f in fs!!) {
                val ls = f.list()
                if (ls == null) continue
                if (ls.size > 5) mLuaExtDir = f.getAbsolutePath() + "/LXC-LUA"
            }
            if (mLuaExtDir == null) mLuaExtDir = getDir("LXC-LUA", MODE_PRIVATE).getAbsolutePath()
        }

        val destDir = File(mLuaExtDir)
        if (!destDir.exists()) destDir.mkdirs()

        //定义文件夹
        localDir = getFilesDir().getAbsolutePath()
        odexDir = getDir("odex", MODE_PRIVATE).getAbsolutePath()
        libDir = getDir("lib", MODE_PRIVATE).getAbsolutePath()
        this.mdDir = getDir("lua", MODE_PRIVATE).getAbsolutePath()
        mLuaCpath = getApplicationInfo().nativeLibraryDir + "/lib?.so" + ";" + libDir + "/lib?.so"
        //luaDir = extDir;
        mLuaLpath =
            this.mdDir + "/?.lua;" + this.mdDir + "/lua/?.lua;" + this.mdDir + "/?/settings.json;"
        //checkInfo();
    }

    override val luaDir: String?
        get() = localDir

    override fun call(func: String?, vararg args: Any?) {
        // TODO: Implement this method
    }

    override fun set(name: String?, `object`: Any?) {
        // TODO: Implement this method
        data.put(name, `object`)
    }

    override val globalData: MutableMap<*, *>?
        get() = data

    override val sharedData: Any?
        get() = mSharedPreferences!!.getAll()

    override fun getSharedData(key: String?): Any? {
        return mSharedPreferences!!.getAll().get(key)
    }

    override fun getSharedData(key: String?, def: Any?): Any? {
        val ret: Any? = mSharedPreferences!!.getAll().get(key)
        if (ret == null) return def
        return ret
    }

    override fun setSharedData(key: String?, value: Any?): Boolean {
        val edit = mSharedPreferences!!.edit()
        if (value == null) edit.remove(key)
        else if (value is String) edit.putString(key, value.toString())
        else if (value is Long) edit.putLong(key, value)
        else if (value is Int) edit.putInt(key, value)
        else if (value is Float) edit.putFloat(key, value)
        else if (value is MutableSet<*>) edit.putStringSet(key, value as MutableSet<String?>)
        else if (value is LuaTable<*, *>) edit.putStringSet(key, value.values as HashSet<String?>)
        else if (value is Boolean) edit.putBoolean(key, value)
        else return false
        edit.apply()
        return true
    }

    fun get(name: String?): Any? {
        // TODO: Implement this method
        return data.get(name)
    }


    override var luaExtDir: String?
        get() = mLuaExtDir
        set(value) {
            if (value == null) return
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
        }

    override val luaLpath: String?
        get() = mLuaLpath

    override val luaCpath: String?
        get() = mLuaCpath

    override val context: Context?
        get() = this

    override val luaState: LuaState?
        get() = null

    override fun doFile(path: String?, vararg arg: Any?): Any? {
        // TODO: Implement this method
        return null
    }

    override fun sendMsg(msg: String?) {
        // TODO: Implement this method
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun sendError(title: String?, msg: Exception?) {
    }


    companion object {
        var instance: LuaApplication? = null
            private set
        private val data = HashMap<String?, Any?>()

        private fun getSharedPreferences(context: Context): SharedPreferences {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val deContext = context.createDeviceProtectedStorageContext()
                if (deContext != null) return PreferenceManager.getDefaultSharedPreferences(
                    deContext
                )
                else return PreferenceManager.getDefaultSharedPreferences(context)
            } else {
                return PreferenceManager.getDefaultSharedPreferences(context)
            }
        }
    }
}