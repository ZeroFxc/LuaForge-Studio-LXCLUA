package com.nirithy.lxclua

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.luajava.LuaException
import com.luajava.LuaFunction
import com.luajava.LuaObject

class LuaDrawable(func: LuaFunction<*>) : Drawable(), LuaGcable {
    private val mContext: LuaContext
    private val mDraw: LuaObject

    val paint: Paint
    private var mOnDraw: LuaFunction<*>? = null

    private var mGc = false


    init {
        mDraw = func
        this.paint = Paint()
        mContext = mDraw.getLuaState().getContext()
        if (mContext is LuaActivity) {
            mContext.regGc(this)
        }
    }

    override fun draw(canvas: Canvas) {
        if (mGc) return
        try {
            if (mOnDraw == null) {
                val r = mDraw.call(canvas, this.paint, this)
                if (r != null && r is LuaFunction<*>) mOnDraw = r
            }
            if (mOnDraw != null) {
                mOnDraw!!.call(canvas)
            }
        } catch (e: LuaException) {
            if (!mGc) {
                mContext.sendError("onDraw", e)
            }
        }
    }

    override fun setAlpha(p1: Int) {
        paint.setAlpha(p1)
    }

    override fun setColorFilter(p1: ColorFilter?) {
        paint.setColorFilter(p1)
    }

    override fun getOpacity(): Int {
        return PixelFormat.UNKNOWN
    }

    override fun gc() {
        mGc = true
        mOnDraw = null
    }

    override val isGc get() = mGc
}
