package com.nirithy.lxclua

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.TypedValue
import kotlin.math.min

/**
 * Created by Administrator on 2018/09/02 0002.
 */
class LoadingDrawable(context: Context) : Drawable() {
    private val dm: DisplayMetrics?
    private var n = 0
    private var m = 0
    private var x = 0
    private var y = 0
    private var sn = 3
    private var sm = 1

    private val p: Paint
    private var mState = 0

    init {
        dm = context.getResources().getDisplayMetrics()
        p = Paint()
        p.setStyle(Paint.Style.STROKE)
        p.setAntiAlias(true)
        p.setStrokeWidth(dp(8f).toFloat())
        p.setColor(-0x77777778)
    }

    private fun dp(n: Float): Int {
        return TypedValue.applyDimension(1, n, dm).toInt()
    }

    fun setState(state: Int) {
        mState = state
    }

    fun loading() {
        reset()
    }

    fun succe() {
        mState = STATE_SUCCESS
    }

    fun fail() {
        mState = STATE_FAIL
    }

    fun reset() {
        mState = STATE_LOADING
        sn = 3
        sm = 1
        n = 0
        m = 0
        x = 0
        y = 0
        invalidateSelf()
    }

    override fun draw(c: Canvas) {
        val b = Rect(getBounds())
        val r = (min(b.right, b.bottom).toFloat()).toInt()
        val dx = b.right - r
        val dy = b.bottom - r
        b.right = r
        b.bottom = r
        c.save()
        c.translate((dx / 2).toFloat(), (dy / 2).toFloat())
        val f = RectF(r * 0.15f, r * 0.15f, r * 0.85f, r * 0.85f)
        if (n >= 360 && mState == STATE_LOADING) {
            sm = 8
            sn = -6
        } else if (n <= 6) {
            sn = 6
            sm = 2
        }
        if (n < 360 || mState == STATE_LOADING) {
            if (mState == STATE_LOADING) {
                n += sn
                m += sm
                m %= 360
            } else {
                n += sn * 2
                m += sm * 2
                m %= 360
            }
        }
        c.drawArc(f, m.toFloat(), n.toFloat(), false, p)

        if (n >= 360) {
            sn = -6
            sm = 8

            if (mState == STATE_SUCCESS) {
                val path = Path()
                path.moveTo(b.right * 0.3f, b.bottom * 0.5f)
                path.lineTo(b.right * 0.45f, b.bottom * 0.7f)
                path.lineTo(b.right * 0.75f, b.bottom * 0.4f)
                c.drawPath(path, p)
            } else if (mState == STATE_FAIL) {
                c.drawLine(
                    (b.right / 2).toFloat(),
                    b.bottom * 0.25f,
                    (b.right / 2).toFloat(),
                    b.bottom * 0.65f,
                    p
                )
                c.drawLine(
                    (b.right / 2).toFloat(),
                    b.bottom * 0.7f,
                    (b.right / 2).toFloat(),
                    b.bottom * 0.75f,
                    p
                )
            }
        }
        c.restore()
        invalidateSelf()
    }


    fun setStrokeWidth(width: Float) {
        p.setStrokeWidth(width)
    }

    fun setColor(p1: Int) {
        p.setColor(p1)
    }

    override fun setAlpha(p1: Int) {
        p.setAlpha(p1)
    }

    override fun setColorFilter(p1: ColorFilter?) {
        p.setColorFilter(p1)
    }

    override fun getOpacity(): Int {
        return PixelFormat.UNKNOWN
    }

    companion object {
        const val STATE_LOADING: Int = 0
        const val STATE_SUCCESS: Int = 1
        val STATE_FAIL: Int = -1
    }
}
