package com.nirithy.lxclua

import com.nirithy.lxclua.util.AsyncTaskX
import com.luajava.JavaFunction
import com.luajava.LuaException
import com.luajava.LuaObject
import com.luajava.LuaState
import com.luajava.LuaStateFactory

class LuaAsyncTask : AsyncTaskX<Any?, Any?, Any?>, LuaGcable {
    private var loadeds: Array<Any>? = null
    private var mGc = false

    override fun gc() {
        // TODO: Implement this method
        if (status == Status.RUNNING) cancel(true)
        mGc = true
    }

    override val isGc get() = mGc

    private var L: LuaState? = null

    private val mLuaContext: LuaContext

    private var mBuffer: ByteArray? = null

    private var mDelay: Long = 0

    private val mCallback: LuaObject?

    private var mUpdate: LuaObject? = null

    constructor(luaContext: LuaContext, delay: Long, callback: LuaObject?) {
        luaContext.regGc(this)
        mLuaContext = luaContext
        mDelay = delay
        mCallback = callback
    }

    constructor(luaContext: LuaContext, src: String, callback: LuaObject?) {
        luaContext.regGc(this)
        mLuaContext = luaContext
        mBuffer = src.toByteArray()
        mCallback = callback
    }


    constructor(luaContext: LuaContext, func: LuaObject, callback: LuaObject?) {
        luaContext.regGc(this)
        mLuaContext = luaContext
        mBuffer = func.dump()
        mCallback = callback
        val l = func.getLuaState()
        val g = l.getLuaObject("luajava")
        val loaded = g.getField("imported")
        if (!loaded.isNil()) {
            loadeds = loaded.asArray()
        }
    }

    constructor(luaContext: LuaContext, func: LuaObject, update: LuaObject?, callback: LuaObject?) {
        luaContext.regGc(this)
        mLuaContext = luaContext
        mBuffer = func.dump()
        mUpdate = update
        mCallback = callback
    }

    @Throws(
        IllegalArgumentException::class,
        ArrayIndexOutOfBoundsException::class,
        LuaException::class
    )
    fun execute() {
        // TODO: Implement this method
        super.execute()
    }

    fun update(msg: Any?) {
        publishProgress(msg)
    }

    fun update(msg: String?) {
        publishProgress(msg)
    }

    fun update(msg: Int) {
        publishProgress(msg)
    }

    override fun doInBackground(vararg args: Any?): Any? {
        if (mDelay != 0L) {
            try {
                Thread.sleep(mDelay)
            } catch (e: InterruptedException) {
            }
            return args
        }
        L = LuaStateFactory.newLuaState()
        L!!.openLibs()
        L!!.pushJavaObject(mLuaContext)
        if (mLuaContext is LuaActivity) {
            L!!.setGlobal("activity")
        } else if (mLuaContext is LuaService) {
            L!!.setGlobal("service")
        }
        L!!.pushJavaObject(this)
        L!!.setGlobal("this")
        L!!.pushContext(mLuaContext)

        L!!.getGlobal("luajava")
        L!!.pushString(mLuaContext.resolveLuaDir(null))
        L!!.setField(-2, "luadir")
        L!!.pop(1)

        try {
            val print: JavaFunction = LuaPrint(mLuaContext, L)
            print.register("print")

            val update: JavaFunction = object : JavaFunction(L) {
                @Throws(LuaException::class)
                override fun execute(): Int {
                    // TODO: Implement this method
                    update(L.toJavaObject(2))
                    return 0
                }
            }

            update.register("update")

            L!!.getGlobal("package")

            L!!.pushString(mLuaContext.luaLpath)
            L!!.setField(-2, "path")
            L!!.pushString(mLuaContext.luaCpath)
            L!!.setField(-2, "cpath")
            L!!.pop(1)
        } catch (e: LuaException) {
            mLuaContext.sendError("AsyncTask", e)
        }

        if (loadeds != null) {
            val require = L!!.getLuaObject("require")
            try {
                require.call("import")
                val _import = L!!.getLuaObject("import")
                for (s in loadeds) _import.call(s.toString())
            } catch (e: LuaException) {
                //	e.printStackTrace();
            }
        }

        try {
            L!!.setTop(0)
            var ok = L!!.LloadBuffer(mBuffer, "LuaAsyncTask")

            if (ok == 0) {
                L!!.getGlobal("debug")
                L!!.getField(-1, "traceback")
                L!!.remove(-2)
                L!!.insert(-2)
                val l = args.size
                for (i in 0..<l) {
                    L!!.pushObjectValue(args[i])
                }
                ok = L!!.pcall(l, LuaState.LUA_MULTRET, -2 - l)
                if (ok == 0) {
                    val n = L!!.getTop() - 1
                    val ret = arrayOfNulls<Any>(n)
                    for (i in 0..<n) ret[i] = L!!.toJavaObject(i + 2)
                    return ret
                }
            }
            throw LuaException(errorReason(ok) + ": " + L!!.toString(-1))
        } catch (e: Exception) {
            mLuaContext.sendError("doInBackground", e)
        }


        return null
    }

    override fun onPostExecute(result: Any?) {
        // TODO: Implement this method

        if (isCancelled) return
        try {
            if (mCallback != null) mCallback.call(*(result as? Array<Any?> ?: emptyArray<Any?>()))
        } catch (e: LuaException) {
            mLuaContext.sendError("onPostExecute", e)
        }
        if (L != null) L!!.gc(LuaState.LUA_GCCOLLECT, 1)
        //L.close();
    }

    override fun onProgressUpdate(vararg values: Any?) {
        // TODO: Implement this method
        try {
            if (mUpdate != null) mUpdate!!.call(*values)
        } catch (e: LuaException) {
            mLuaContext.sendError("onProgressUpdate", e)
        }
        super.onProgressUpdate(*values)
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

    companion object {
        init {
            AsyncTaskX.Companion.setDefaultExecutor(AsyncTaskX.Companion.THREAD_POOL_EXECUTOR)
        }
    }
}

