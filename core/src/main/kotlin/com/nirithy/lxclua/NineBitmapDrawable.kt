package com.nirithy.lxclua

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.min

class NineBitmapDrawable : Drawable, LuaGcable {
    private val mPaint = Paint()
    private var mBitmap: Bitmap? = null

    private var mX1 = 0
    private var mY1 = 0
    private var mX2 = 0
    private var mY2 = 0

    private var mRect1: Rect? = null
    private var mRect2: Rect? = null
    private var mRect3: Rect? = null

    private var mRect4: Rect? = null
    private var mRect5: Rect? = null
    private var mRect6: Rect? = null

    private var mRect7: Rect? = null
    private var mRect8: Rect? = null
    private var mRect9: Rect? = null
    private var mGc = false
    private var mH = 0
    private var mW = 0

    constructor(path: String?) : this(LuaBitmap.getLocalBitmap(path)!!)

    constructor(bitmap: Bitmap) {
        val w = bitmap.getWidth()
        val h = bitmap.getHeight()
        val c = Color.BLACK
        var x1 = 0
        var x2 = 0
        for (i in 0..<w) {
            val p = bitmap.getPixel(i, 0)
            if (p == c) {
                x1 = i
                break
            }
            if (p != -1 && p != 0) break
        }
        require(!(x1 == 0 || x1 == w - 1)) { "not found x1" }
        for (i in x1..<w) {
            val p = bitmap.getPixel(i, 0)
            if (p != c) {
                x2 = w - i
                break
            }
        }
        require(!(x2 == 0 || x2 == 1)) { "not found x2" }

        var y1 = 0
        var y2 = 0
        for (i in 0..<h) {
            val p = bitmap.getPixel(0, i)
            if (p == c) {
                y1 = i
                break
            }
            if (p != -1 && p != 0) break
        }
        require(!(y1 == 0 || y1 == h - 1)) { "not found y1" }
        for (i in y1..<h) {
            if (bitmap.getPixel(0, i) != c) {
                y2 = h - i
                break
            }
        }
        require(!(y2 == 0 || y2 == 1)) { "not found y2" }

        init(bitmap, x1, y1, x2, y2)
    }


    constructor(bitmap: Bitmap, x1: Int, y1: Int, x2: Int, y2: Int) {
        init(bitmap, x1, y1, x2, y2)
    }

    private fun init(bitmap: Bitmap, x1: Int, y1: Int, x2: Int, y2: Int) {
        //Log.i("rime", "init: "+x1+";"+y1+";"+x2+";"+y2);
        var x2 = x2
        var y2 = y2
        mBitmap = bitmap
        val w = bitmap.getWidth()
        val h = bitmap.getHeight()


        mRect1 = Rect(1, 1, x1, y1)
        mRect2 = Rect(x1, 1, x2, y1)
        mRect3 = Rect(x2, 1, w - 1, y1)

        mRect4 = Rect(1, y1, x1, y2)
        mRect5 = Rect(x1, y1, x2, y2)
        mRect6 = Rect(x2, y1, w - 1, y2)

        mRect7 = Rect(1, y2, x1, h - 1)
        mRect8 = Rect(x1, y2, x2, h - 1)
        mRect9 = Rect(x2, y2, w - 1, h - 1)
        x2 = w - x2
        y2 = h - y2
        mX1 = x1
        mY1 = y1
        mX2 = x2
        mY2 = y2
        mW = w
        mH = h
    }

    override fun draw(canvas: Canvas) {
        // TODO: Implement this method
        val rect = getBounds()
        val w = rect.right
        val h = rect.bottom
        val s1 = min(w * 1f / mW, h * 1f / mH)
        val x1 = (mX1 * s1).toInt()
        val x2 = (mX2 * s1).toInt()
        val y1 = (mY1 * s1).toInt()
        val y2 = (mY2 * s1).toInt()

        val rect1 = Rect(0, 0, x1, y1)
        val rect2 = Rect(x1, 0, w - x2, y1)
        val rect3 = Rect(w - x2, 0, w, y1)

        val rect4 = Rect(0, y1, x1, h - y2)
        val rect5 = Rect(x1, y1, w - x2, h - y2)
        val rect6 = Rect(w - x2, y1, w, h - y2)

        val rect7 = Rect(0, h - y2, x1, h)
        val rect8 = Rect(x1, h - y2, w - x2, h)
        val rect9 = Rect(w - x2, h - y2, w, h)

        canvas.drawBitmap(mBitmap!!, mRect1, rect1, mPaint)
        canvas.drawBitmap(mBitmap!!, mRect2, rect2, mPaint)
        canvas.drawBitmap(mBitmap!!, mRect3, rect3, mPaint)

        canvas.drawBitmap(mBitmap!!, mRect4, rect4, mPaint)
        canvas.drawBitmap(mBitmap!!, mRect5, rect5, mPaint)
        canvas.drawBitmap(mBitmap!!, mRect6, rect6, mPaint)

        canvas.drawBitmap(mBitmap!!, mRect7, rect7, mPaint)
        canvas.drawBitmap(mBitmap!!, mRect8, rect8, mPaint)
        canvas.drawBitmap(mBitmap!!, mRect9, rect9, mPaint)
    }

    override fun setAlpha(p1: Int) {
        // TODO: Implement this method
        mPaint.setAlpha(p1)
    }

    override fun setColorFilter(p1: ColorFilter?) {
        // TODO: Implement this method
        mPaint.setColorFilter(p1)
    }

    override fun getOpacity(): Int {
        // TODO: Implement this method
        return PixelFormat.UNKNOWN
    }

    override fun gc() {
        try {
            mBitmap!!.recycle()
            mGc = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override val isGc get() = mGc
}
