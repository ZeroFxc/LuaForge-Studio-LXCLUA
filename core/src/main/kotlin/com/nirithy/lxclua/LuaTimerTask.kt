package com.nirithy.lxclua

import com.nirithy.lxclua.util.TimerTaskX
import com.luajava.JavaFunction
import com.luajava.LuaException
import com.luajava.LuaObject
import com.luajava.LuaState
import com.luajava.LuaStateFactory
import java.io.IOException
import java.util.regex.Pattern

class LuaTimerTask : TimerTaskX {
    private var L: LuaState? = null

    private val mLuaContext: LuaContext

    private var mSrc: String? = null

    private var mArg: Array<Any?>? = arrayOfNulls<Any>(0)

    private var mEnabled = true

    private var mBuffer: ByteArray? = null

    @JvmOverloads
    constructor(luaContext: LuaContext, src: String, arg: Array<Any?>? = null) {
        mLuaContext = luaContext
        mSrc = src
        if (arg != null) mArg = arg
    }

    @JvmOverloads
    constructor(luaContext: LuaContext, func: LuaObject, arg: Array<Any?>? = null) {
        mLuaContext = luaContext
        if (arg != null) mArg = arg

        mBuffer = func.dump()
    }

    override fun run() {
        if (!mEnabled) return
        try {
            if (L == null) {
                initLua()

                if (mBuffer != null) newLuaThread(mBuffer, *mArg!!)
                else newLuaThread(mSrc!!, *mArg!!)
            } else {
                L!!.getGlobal("run")
                if (!L!!.isNil(-1)) runFunc("run")
                else {
                    if (mBuffer != null) newLuaThread(mBuffer, *mArg!!)
                    else newLuaThread(mSrc!!, *mArg!!)
                }
            }
        } catch (e: LuaException) {
            mLuaContext.sendError(this.toString(), e)
        }
        L!!.gc(LuaState.LUA_GCCOLLECT, 1)
        System.gc()
    }

    override fun cancel(): Boolean {
        // TODO: Implement this method
        return super.cancel()
    }

    fun setArg(arg: Array<Any?>) {
        mArg = arg
    }

    @Throws(
        ArrayIndexOutOfBoundsException::class,
        LuaException::class,
        IllegalArgumentException::class
    )
    fun setArg(arg: LuaObject) {
        mArg = arg.asArray()
    }

    override var isEnabled: Boolean
        get() = mEnabled
        set(enabled) { mEnabled = enabled }


    @Throws(LuaException::class)
    fun set(key: String?, value: Any?) {
        L!!.pushObjectValue(value)
        L!!.setGlobal(key)
    }

    @Throws(LuaException::class)
    fun get(key: String?): Any? {
        L!!.getGlobal(key)
        return L!!.toJavaObject(-1)
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
        L!!.pushJavaObject(mLuaContext)
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
        }
    }

    @Throws(LuaException::class)
    private fun newLuaThread(buf: ByteArray?, vararg args: Any?) {
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
}
