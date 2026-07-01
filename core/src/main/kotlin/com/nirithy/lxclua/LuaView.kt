package com.nirithy.lxclua

import android.content.Context
import android.view.View
import com.luajava.LuaException
import com.luajava.LuaObject
import com.luajava.LuaTable

/**
 * Created by Administrator on 2018/08/29 0029.
 */
class LuaView : View {
    private var mTable: LuaTable<*, *>? = null
    private var mOnMeasure: LuaObject? = null

    constructor(context: Context?) : super(context)

    constructor(context: Context?, table: LuaTable<*, *>?) : super(context) {
        mTable = table
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (mTable != null) {
            try {
                mOnMeasure = mTable!!.getField("onMeasure")
                if (mOnMeasure!!.isFunction()) {
                    mOnMeasure!!.call(widthMeasureSpec, heightMeasureSpec, this)
                    return
                }
            } catch (e: LuaException) {
                e.printStackTrace()
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
