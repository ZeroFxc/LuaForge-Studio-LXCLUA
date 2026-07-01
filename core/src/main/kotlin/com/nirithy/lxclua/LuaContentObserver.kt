package com.nirithy.lxclua

import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler

/**
 * Created by Administrator on 2017/11/09 0009.
 */
class LuaContentObserver private constructor(handler: Handler?) : ContentObserver(handler),
    LuaGcable {
    private var mOnChangeListener: OnChangeListener? = null
    private var mGc = false

    constructor(
        context: LuaContext,
        uri: String?
    ) : this(Handler(LuaApplication.instance!!.getMainLooper())) {
        val mUri = Uri.parse(uri)
        context.regGc(this)
        LuaApplication.instance!!.getContentResolver().registerContentObserver(
            mUri,
            true,
            this
        )
    }


    constructor(
        context: LuaContext,
        mUri: Uri
    ) : this(Handler(LuaApplication.instance!!.getMainLooper())) {
        context.regGc(this)
        LuaApplication.instance!!.getContentResolver().registerContentObserver(
            mUri,
            true,
            this
        )
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        if (mOnChangeListener != null) {
            val cursor: Cursor? = LuaApplication.instance!!.getContentResolver()
                .query(uri!!, null, null, null, null)
            if (cursor != null) cursor.moveToFirst()
            mOnChangeListener!!.onChange(selfChange, uri, cursor)
            if (cursor != null) cursor.close()
        }
    }

    fun setOnChangeListener(listener: OnChangeListener?) {
        mOnChangeListener = listener
    }

    override fun gc() {
        LuaApplication.instance!!.getContentResolver().unregisterContentObserver(this)
        mGc = true
    }

    override val isGc get() = mGc

    interface OnChangeListener {
        fun onChange(selfChange: Boolean, uri: Uri?, cursor: Cursor?)
    }
}
