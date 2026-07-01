package com.nirithy.lxclua

import com.nirithy.lxclua.util.TimerX
import com.luajava.LuaObject

class LuaTimer : TimerX, LuaGcable {
    private var mGc = false

    override fun gc() {
        // TODO: Implement this method
        stop()
        mGc = true
    }

    override val isGc get() = mGc

    private val task: LuaTimerTask

    @JvmOverloads
    constructor(main: LuaContext, src: String?, arg: Array<Any?>? = null) : super("LuaTimer") {
        main.regGc(this)
        task = LuaTimerTask(main, src!!, arg)
    }

    @JvmOverloads
    constructor(main: LuaContext, func: LuaObject?, arg: Array<Any?>? = null) : super("LuaTimer") {
        main.regGc(this)
        task = LuaTimerTask(main, func!!, arg)
    }

    fun start(delay: Long, period: Long) {
        schedule(task, delay, period)
    }

    fun start(delay: Long) {
        schedule(task, delay)
    }

    fun stop() {
        task.cancel()
    }

    var isEnabled: Boolean
        get() = task.isEnabled
        set(enabled) {
            task.isEnabled = enabled
        }

    fun getEnabled(): Boolean {
        return task.isEnabled
    }

    var period: Long
        get() = task.period
        set(period) {
            task.period = period
        }
}
