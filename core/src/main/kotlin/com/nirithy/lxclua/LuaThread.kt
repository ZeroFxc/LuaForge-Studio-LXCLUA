package com.nirithy.lxclua

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.luajava.JavaFunction
import com.luajava.LuaException
import com.luajava.LuaMetaTable
import com.luajava.LuaObject
import com.luajava.LuaState
import com.luajava.LuaStateFactory
import java.io.IOException
import java.util.regex.Pattern

class LuaThread : Thread, Runnable, LuaMetaTable, LuaGcable {
    private var mGc = false

    override fun gc() {
        // TODO: Implement this method
        quit()
        mGc = true
    }

    override val isGc get() = mGc


    override fun __call(arg: Array<Any?>?): Any? {
        // TODO: Implement this method
        return null
    }

    override fun __index(key: String?): Any {
        // TODO: Implement this method
        return object : LuaMetaTable {
            override fun __call(arg: Array<Any?>): Any? {
                // TODO: Implement this method
                call(key, arg)
                return null
            }

            override fun __index(key: String?): Any? {
                // TODO: Implement this method
                return null
            }

            override fun __newIndex(key: String?, value: Any?) {
                // TODO: Implement this method
            }
        }
    }

    override fun __newIndex(key: String?, value: Any?) {
        // TODO: Implement this method
        set(key, value)
    }

    private var L: LuaState? = null
    private var thandler: Handler? = null
    var isRun: Boolean = false
    private val mLuaContext: LuaContext

    private val mIsLoop: Boolean

    private var mSrc: String? = null

    private var mArg: Array<Any?>? = arrayOfNulls<Any>(0)

    private var mBuffer: ByteArray? = null

    constructor(luaContext: LuaContext, src: String, arg: Array<Any?>) : this(
        luaContext,
        src,
        false,
        arg
    )

    @JvmOverloads
    constructor(
        luaContext: LuaContext,
        src: String,
        isLoop: Boolean = false,
        arg: Array<Any?>? = null
    ) {
        luaContext.regGc(this)
        mLuaContext = luaContext
        mSrc = src
        mIsLoop = isLoop
        if (arg != null) mArg = arg
    }

    constructor(luaContext: LuaContext, func: LuaObject, arg: Array<Any?>) : this(
        luaContext,
        func,
        false,
        arg
    )

    @JvmOverloads
    constructor(
        luaContext: LuaContext,
        func: LuaObject,
        isLoop: Boolean = false,
        arg: Array<Any?>? = null
    ) {
        mLuaContext = luaContext
        if (arg != null) mArg = arg
        mIsLoop = isLoop
        mBuffer = func.dump()
    }

    override fun run() {
        try {
            if (L == null) {
                initLua()

                if (mBuffer != null) newLuaThread(mBuffer, *mArg!!)
                else newLuaThread(mSrc!!, *mArg!!)
            }
        } catch (e: LuaException) {
            mLuaContext.sendError(this.toString(), e)
            return
        }
        if (mIsLoop) {
            Looper.prepare()
            thandler = ThreadHandler()
            isRun = true
            L!!.getGlobal("run")
            if (!L!!.isNil(-1)) {
                L!!.pop(1)
                runFunc("run")
            }

            Looper.loop()
        }
        isRun = false
        L!!.gc(LuaState.LUA_GCCOLLECT, 1)
        System.gc()
    }

    fun call(func: String?) {
        push(3, func)
    }

    fun call(func: String?, args: Array<Any?>) {
        if (args.size == 0) push(3, func)
        else push(1, func, args)
    }

    fun set(key: String?, value: Any?) {
        push(4, key, arrayOf<Any?>(value))
    }

    @Throws(LuaException::class)
    fun get(key: String?): Any? {
        L!!.getGlobal(key)
        return L!!.toJavaObject(-1)
    }

    fun quit() {
        if (isRun) {
            isRun = false
            thandler!!.getLooper().quit()
        }
    }

    fun push(what: Int, s: String?) {
        if (!isRun) {
            mLuaContext.sendMsg("thread is not running")
            return
        }

        val message = Message()
        val bundle = Bundle()
        bundle.putString("data", s)
        message.setData(bundle)
        message.what = what

        thandler!!.sendMessage(message)
    }

    fun push(what: Int, s: String?, args: Array<Any?>?) {
        if (!isRun) {
            mLuaContext.sendMsg("thread is not running")
            return
        }

        val message = Message()
        val bundle = Bundle()
        bundle.putString("data", s)
        bundle.putSerializable("args", args)
        message.setData(bundle)
        message.what = what

        thandler!!.sendMessage(message)
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


    @Throws(LuaException::class)
    private fun initLua() {
        L = LuaStateFactory.newLuaState()
        L!!.openLibs()
        L!!.pushJavaObject(mLuaContext.context)
        if (mLuaContext is LuaActivity) {
            L!!.setGlobal("activity")
        } else if (mLuaContext is LuaService) {
            L!!.setGlobal("service")
        }
        L!!.pushJavaObject(this)
        L!!.setGlobal("this")
        L!!.pushContext(mLuaContext)

        val print: JavaFunction = LuaPrint(mLuaContext, L)
        print.register("print")

        L!!.getGlobal("package")

        L!!.pushString(mLuaContext.luaLpath)
        L!!.setField(-2, "path")
        L!!.pushString(mLuaContext.luaCpath)
        L!!.setField(-2, "cpath")
        L!!.pop(1)

        val set: JavaFunction = object : JavaFunction(L) {
            @Throws(LuaException::class)
            override fun execute(): Int {
                mLuaContext.set(L.toString(2), L.toJavaObject(3))
                return 0
            }
        }
        set.register("set")

        val call: JavaFunction = object : JavaFunction(L) {
            @Throws(LuaException::class)
            override fun execute(): Int {
                val top = L.getTop()
                if (top > 2) {
                    val args = arrayOfNulls<Any>(top - 2)
                    for (i in 3..top) {
                        args[i - 3] = L.toJavaObject(i)
                    }
                    mLuaContext.call(L.toString(2), *args)
                } else if (top == 2) {
                    mLuaContext.call(L.toString(2))
                }
                return 0
            }
        }
        call.register("call")
    }

    private fun newLuaThread(str: String, vararg args: Any?) {
        try {
            if (Pattern.matches("^\\w+$", str)) {
                doAsset(str + ".lua", *args)
            } else if (Pattern.matches("^[\\w\\.\\_/]+$", str)) {
                L!!.getGlobal("luajava")
                L!!.pushString(mLuaContext.resolveLuaDir(null))
                L!!.setField(-2, "luadir")
                L!!.pushString(str)
                L!!.setField(-2, "luapath")
                L!!.pop(1)

                doFile(str, *args)
            } else {
                doString(str, *args)
            }
        } catch (e: Exception) {
            mLuaContext.sendError(this.toString(), e)
            quit()
        }
    }

    private fun newLuaThread(buf: ByteArray?, vararg args: Any?) {
        try {
            var ok = 0
            L!!.setTop(0)
            ok = L!!.LloadBuffer(buf, "TimerTask")

            if (ok == 0) {
                L!!.getGlobal("debug")
                L!!.getField(-1, "traceback")
                L!!.remove(-2)
                L!!.insert(-2)
                val l = args.size
                for (i in 0..<l) {
                    L!!.pushObjectValue(args[i])
                }
                ok = L!!.pcall(l, 0, -2 - l)
                if (ok == 0) {
                    return
                }
            }
            throw LuaException(errorReason(ok) + ": " + L!!.toString(-1))
        } catch (e: Exception) {
            mLuaContext.sendError(this.toString(), e)
            quit()
        }
    }

    @Throws(LuaException::class)
    private fun doFile(filePath: String?, vararg args: Any?) {
        var ok = 0
        L!!.setTop(0)
        ok = L!!.LloadFile(filePath)

        if (ok == 0) {
            L!!.getGlobal("debug")
            L!!.getField(-1, "traceback")
            L!!.remove(-2)
            L!!.insert(-2)
            val l = args.size
            for (i in 0..<l) {
                L!!.pushObjectValue(args[i])
            }
            ok = L!!.pcall(l, 0, -2 - l)
            if (ok == 0) {
                return
            }
        }
        throw LuaException(errorReason(ok) + ": " + L!!.toString(-1))
    }


    @Throws(LuaException::class, IOException::class)
    fun doAsset(name: String?, vararg args: Any?) {
        var ok = 0
        val bytes: ByteArray = LuaUtil.Companion.readAsset(mLuaContext.context!!, name!!)
        L!!.setTop(0)
        ok = L!!.LloadBuffer(bytes, name!!)

        if (ok == 0) {
            L!!.getGlobal("debug")
            L!!.getField(-1, "traceback")
            L!!.remove(-2)
            L!!.insert(-2)
            val l = args.size
            for (i in 0..<l) {
                L!!.pushObjectValue(args[i])
            }
            ok = L!!.pcall(l, 0, -2 - l)
            if (ok == 0) {
                return
            }
        }
        throw LuaException(errorReason(ok) + ": " + L!!.toString(-1))
    }

    @Throws(LuaException::class)
    private fun doString(src: String?, vararg args: Any?) {
        L!!.setTop(0)
        var ok = L!!.LloadString(src)

        if (ok == 0) {
            L!!.getGlobal("debug")
            L!!.getField(-1, "traceback")
            L!!.remove(-2)
            L!!.insert(-2)
            val l = args.size
            for (i in 0..<l) {
                L!!.pushObjectValue(args[i])
            }
            ok = L!!.pcall(l, 0, -2 - l)
            if (ok == 0) {
                return
            }
        }
        throw LuaException(errorReason(ok) + ": " + L!!.toString(-1))
    }


    private fun runFunc(funcName: String?, vararg args: Any?) {
        try {
            L!!.setTop(0)
            L!!.getGlobal(funcName)
            if (L!!.isFunction(-1)) {
                L!!.getGlobal("debug")
                L!!.getField(-1, "traceback")
                L!!.remove(-2)
                L!!.insert(-2)

                val l = args.size
                for (i in 0..<l) {
                    L!!.pushObjectValue(args[i])
                }

                val ok = L!!.pcall(l, 1, -2 - l)
                if (ok == 0) {
                    return
                }
                throw LuaException(errorReason(ok) + ": " + L!!.toString(-1))
            }
        } catch (e: LuaException) {
            mLuaContext.sendError(this.toString() + " " + funcName, e)
        }
    }

    private fun setField(key: String?, value: Any?) {
        try {
            L!!.pushObjectValue(value)
            L!!.setGlobal(key)
        } catch (e: LuaException) {
            mLuaContext.sendError(this.toString(), e)
        }
    }

    private inner class ThreadHandler : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val data = msg.getData()
            when (msg.what) {
                0 -> newLuaThread(
                    data.getString("data")!!,
                    *(data.getSerializable("args") as kotlin.Array<kotlin.Any?>?)!!
                )

                1 -> runFunc(
                    data.getString("data"),
                    *(data.getSerializable("args") as kotlin.Array<kotlin.Any?>?)!!
                )

                2 -> newLuaThread(data.getString("data")!!)
                3 -> runFunc(data.getString("data"))
                4 -> setField(
                    data.getString("data"),
                    (data.getSerializable("args") as Array<Any?>?)!![0]
                )
            }
        }
    }
}
