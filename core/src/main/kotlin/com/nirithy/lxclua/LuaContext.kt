package com.nirithy.lxclua

import android.content.Context
import com.luajava.LuaState

interface LuaContext {
    val classLoaders: ArrayList<ClassLoader?>?

    fun call(func: String?, vararg args: Any?)

    fun set(name: String?, value: Any?)

    val luaPath: String?

    fun resolveLuaPath(path: String?): String?

    fun resolveLuaPath(dir: String?, name: String?): String?

    val luaDir: String?

    fun resolveLuaDir(dir: String?): String?

    var luaExtDir: String?

    fun resolveLuaExtDir(dir: String?): String?

    fun resolveLuaExtPath(path: String?): String?

    fun resolveLuaExtPath(dir: String?, name: String?): String?

    val luaLpath: String?

    val luaCpath: String?

    val context: Context?

    val luaState: LuaState?

    fun doFile(path: String?, vararg arg: Any?): Any?

    fun sendMsg(msg: String?)

    fun sendError(title: String?, msg: Exception?)

    val width: Int

    val height: Int

    val globalData: MutableMap<*, *>?

    val sharedData: Any?

    fun getSharedData(key: String?): Any?

    fun getSharedData(key: String?, def: Any?): Any?

    fun setSharedData(key: String?, value: Any?): Boolean

    fun regGc(obj: LuaGcable?)
}
