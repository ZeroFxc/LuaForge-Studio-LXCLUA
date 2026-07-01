package com.nirithy.lxclua

import android.os.FileObserver

/**
 * Created by Administrator on 2017/11/08 0008.
 */
class LuaFileObserver : FileObserver {
    private var mOnEventListener: OnEventListener? = null

    constructor(path: String?) : super(path)

    constructor(path: String?, mask: Int) : super(path, mask)

    fun setOnEventListener(listener: OnEventListener?) {
        mOnEventListener = listener
    }

    override fun onEvent(event: Int, path: String?) {
        if (mOnEventListener != null) mOnEventListener!!.onEvent(event, path)
    }

    interface OnEventListener {
        fun onEvent(event: Int, path: String?)
    }
}
