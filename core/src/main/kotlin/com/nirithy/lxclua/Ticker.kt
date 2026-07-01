package com.nirithy.lxclua

import android.os.Handler
import android.os.Message

class Ticker : LuaGcable {
    private var mHandler: Handler? = null

    private var mOnTickListener: OnTickListener? = null

    private var mThread: Thread? = null

    private var mPeriod: Long = 1000

    private var mEnabled = true

    var isRun: Boolean = false
        private set

    private var mLast: Long = 0

    private var mOffset: Long = 0

    private var mGc = false


    init {
        init()
    }

    private fun init() {
        mHandler = object : Handler() {
            override fun handleMessage(msg: Message) {
                if (mOnTickListener != null && !mGc) {
                    try {
                        mOnTickListener!!.onTick()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mGc = true
                        isRun = false
                        mOnTickListener = null
                    }
                }
            }
        }
        mThread = object : Thread() {
            override fun run() {
                isRun = true
                while (isRun && !mGc && mHandler != null) {
                    val now = System.currentTimeMillis()
                    if (!mEnabled) mLast = now - mOffset
                    if (now - mLast >= mPeriod) {
                        mLast = now
                        if (mHandler != null) {
                            mHandler!!.sendEmptyMessage(0)
                        }
                    }

                    try {
                        sleep(1)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    var period: Long
        get() = mPeriod
        set(period) {
            mLast = System.currentTimeMillis()
            mPeriod = period
        }


    var interval: Long
        get() = mPeriod
        set(period) {
            mLast = System.currentTimeMillis()
            mPeriod = period
        }

    var enabled: Boolean
        get() = mEnabled
        set(enabled) {
            mEnabled = enabled
            if (!enabled) mOffset = System.currentTimeMillis() - mLast
        }

    fun setOnTickListener(ltr: OnTickListener?) {
        mOnTickListener = ltr
    }

    fun start() {
        mThread!!.start()
    }

    fun stop() {
        isRun = false
    }

    override fun gc() {
        mGc = true
        isRun = false
        mOnTickListener = null
        if (mHandler != null) {
            mHandler!!.removeCallbacksAndMessages(null)
        }
        if (mThread != null) {
            mThread!!.interrupt()
        }
    }

    override val isGc get() = mGc


    interface OnTickListener {
        fun onTick()
    }
}
