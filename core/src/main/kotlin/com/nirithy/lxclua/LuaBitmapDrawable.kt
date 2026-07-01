package com.nirithy.lxclua

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import com.nirithy.lxclua.GifDecoder.GifAction
import com.nirithy.lxclua.util.AsyncTaskX
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.min

/**
 * Created by nirenr on 2018/09/05 0005.
 */
class LuaBitmapDrawable(context: LuaContext, path: String) : Drawable(), Runnable, LuaGcable {
    private val mLuaContext: LuaContext
    private var mDuration = 0
    private var mMovieStart: Long = 0
    private var mCurrentAnimationTime = 0
    private var mMovie: Movie? = null
    private val mLoadingDrawable: LoadingDrawable?
    private var mBitmapDrawable: Drawable? = null
    private var mNineBitmapDrawable: NineBitmapDrawable? = null
    private var mColorFilter: ColorFilter? = null
    private var mFillColor = 0
    private var mScaleType: Int = FIT_XY
    private var mGifDecoder: GifDecoder? = null
    private var mGifDecoder2: GifDecoder? = null
    private var mHandler: Handler? = null
    private var mGifFrame: GifDecoder.GifFrame? = null
    private var mDelay = 0
    private var mGc = false

    constructor(context: LuaContext, path: String, def: Drawable?) : this(context, path) {
        mBitmapDrawable = def
    }

    private fun initHttp(context: LuaContext, path: String) {
        object : AsyncTaskX<String?, String?, String?>() {
            override fun doInBackground(vararg strings: String?): String {
                try {
                    return getHttpBitmap(context, path)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return ""
            }

            override fun onPostExecute(s: String?) {
                init(s ?: return)
            }
        }.execute()
    }

    private fun init(path: String) {
        try {
            mGifDecoder = GifDecoder(FileInputStream(path), object : GifAction {
                override fun parseOk(parseStatus: Boolean, frameIndex: Int) {
                    if (!parseStatus && frameIndex < 0) {
                        init2(path)
                    } else if (parseStatus && mGifDecoder2 == null && mGifDecoder!!.frameCount > 1) {     //当帧数大于1时，启动动画线程
                        mGifDecoder2 = mGifDecoder
                    }
                }
            })
            mGifDecoder!!.start()
        } catch (e: Exception) {
            e.printStackTrace()
            init2(path)
        }
    }


    private fun init2(path: String) {
        if (path.isEmpty()) {
            Handler().postDelayed(object : Runnable {
                override fun run() {
                    mLoadingDrawable!!.setState(-1)
                }
            }, 1000)
            invalidateSelf()
            return
        }

        //mMovie = Movie.decodeFile(path);
        if (mMovie != null) {
            mDuration = mMovie!!.duration()
            if (mDuration == 0) mDuration = 1000
        } else {
            try {
                mNineBitmapDrawable = NineBitmapDrawable(path)
            } catch (e: Exception) {
                try {
                    mBitmapDrawable = BitmapDrawable(LuaBitmap.getLocalBitmap(mLuaContext, path))
                } catch (e1: Exception) {
                    e1.printStackTrace()
                }
            }
        }
        if (mMovie == null && mBitmapDrawable == null && mNineBitmapDrawable == null) {
            Handler().postDelayed(object : Runnable {
                override fun run() {
                    mLoadingDrawable!!.setState(-1)
                }
            }, 1000)
        }
        invalidateSelf()
    }

    override fun getIntrinsicWidth(): Int {
        if (mMovie != null) {
            return mMovie!!.width()
        } else if (mGifDecoder != null) {
            return mGifDecoder!!.width
        } else if (mBitmapDrawable != null) {
            return mBitmapDrawable!!.getIntrinsicWidth()
        } else if (mNineBitmapDrawable != null) {
            return mNineBitmapDrawable!!.getIntrinsicWidth()
        }
        return super.getIntrinsicWidth()
    }

    override fun getIntrinsicHeight(): Int {
        if (mMovie != null) {
            return mMovie!!.height()
        } else if (mGifDecoder != null) {
            return mGifDecoder!!.height
        } else if (mBitmapDrawable != null) {
            return mBitmapDrawable!!.getIntrinsicHeight()
        } else if (mNineBitmapDrawable != null) {
            return mNineBitmapDrawable!!.getIntrinsicHeight()
        }
        return super.getIntrinsicHeight()
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(mFillColor)
        if (mGifDecoder2 != null) {
            val now = System.currentTimeMillis()
            if (mMovieStart == 0L || mGifFrame == null) {
                mGifFrame = mGifDecoder2!!.next()
                mDelay = mGifFrame!!.delay
                mMovieStart = now
            } else {
                while (now - mMovieStart > mDelay) {
                    mGifFrame = mGifDecoder2!!.next()
                    mDelay = mGifFrame!!.delay
                    mMovieStart += mDelay.toLong()
                }
            }
            if (mGifFrame != null) {
                val bound = getBounds()
                val mBitmapDrawable = BitmapDrawable(mGifFrame!!.image)
                var width = mBitmapDrawable.getIntrinsicWidth()
                var height = mBitmapDrawable.getIntrinsicHeight()
                var mScale = 1f
                if (mScaleType == FIT_XY) {
                    val mScaleX = (bound.right - bound.left).toFloat() / width.toFloat()
                    val mScaleY = (bound.bottom - bound.top).toFloat() / height.toFloat()
                    width = (width * mScaleX).toInt()
                    height = (height * mScaleY).toInt()
                } else if (mScaleType != MATRIX) {
                    mScale = min(
                        (bound.bottom - bound.top).toFloat() / height.toFloat(),
                        (bound.right - bound.left).toFloat() / width.toFloat()
                    )
                    width = (width * mScale).toInt()
                    height = (height * mScale).toInt()
                }
                var left = bound.left
                var top = bound.top
                when (mScaleType) {
                    FIT_CENTER -> {
                        left = ((bound.right - bound.left) - width) / 2
                        top = ((bound.bottom - bound.top) - height) / 2
                    }

                    FIT_END -> top = (bound.bottom - bound.top) - height
                }
                //float mScale = Math.min((float) (bound.bottom - bound.top) / (float) mBitmapDrawable.getIntrinsicHeight(), (float) (bound.right - bound.left) / (float) mBitmapDrawable.getIntrinsicWidth());
                mBitmapDrawable.setBounds(Rect(left, top, left + width, top + height))
                mBitmapDrawable.draw(canvas)

                // canvas.drawBitmap(mGifFrame.image, null, getBounds(), null);
            }
            invalidateSelf()
        } else if (mMovie != null) {
            val now = System.currentTimeMillis()
            if (mMovieStart == 0L) mMovieStart = now
            mCurrentAnimationTime = ((now - mMovieStart) % mDuration).toInt()
            mMovie!!.setTime(mCurrentAnimationTime)
            val bound = getBounds()
            canvas.save()
            var width = mMovie!!.width()
            var height = mMovie!!.height()
            var mScale = 1f
            if (mScaleType == FIT_XY) {
                val mScaleX = (bound.right - bound.left).toFloat() / width.toFloat()
                val mScaleY = (bound.bottom - bound.top).toFloat() / height.toFloat()
                canvas.scale(mScaleX, mScaleY)
                width = (width * mScaleX).toInt()
                height = (height * mScaleY).toInt()
            } else if (mScaleType != MATRIX) {
                mScale = min(
                    (bound.bottom - bound.top).toFloat() / height.toFloat(),
                    (bound.right - bound.left).toFloat() / width.toFloat()
                )
                canvas.scale(mScale, mScale)
                width = (width * mScale).toInt()
                height = (height * mScale).toInt()
            }
            var left = bound.left
            var top = bound.top
            when (mScaleType) {
                FIT_CENTER -> {
                    left = (((bound.right - bound.left) - width) / mScale / 2).toInt()
                    top = (((bound.bottom - bound.top) - height) / mScale / 2).toInt()
                }

                FIT_END -> top = (((bound.bottom - bound.top)) - height / mScale).toInt()
            }

            //
            //canvas.translate(left,top);
            mMovie!!.draw(canvas, left.toFloat(), top.toFloat(), Paint())

            canvas.restore()
            invalidateSelf()
        } else if (mBitmapDrawable != null) {
            val bound = getBounds()
            var width = mBitmapDrawable!!.getIntrinsicWidth()
            var height = mBitmapDrawable!!.getIntrinsicHeight()
            var mScale = 1f
            if (mScaleType == FIT_XY) {
                val mScaleX = (bound.right - bound.left).toFloat() / width.toFloat()
                val mScaleY = (bound.bottom - bound.top).toFloat() / height.toFloat()
                width = (width * mScaleX).toInt()
                height = (height * mScaleY).toInt()
            } else if (mScaleType != MATRIX) {
                mScale = min(
                    (bound.bottom - bound.top).toFloat() / height.toFloat(),
                    (bound.right - bound.left).toFloat() / width.toFloat()
                )
                width = (width * mScale).toInt()
                height = (height * mScale).toInt()
            }
            var left = bound.left
            var top = bound.top
            when (mScaleType) {
                FIT_CENTER -> {
                    left = ((bound.right - bound.left) - width) / 2
                    top = ((bound.bottom - bound.top) - height) / 2
                }

                FIT_END -> top = (bound.bottom - bound.top) - height
            }
            //float mScale = Math.min((float) (bound.bottom - bound.top) / (float) mBitmapDrawable.getIntrinsicHeight(), (float) (bound.right - bound.left) / (float) mBitmapDrawable.getIntrinsicWidth());
            mBitmapDrawable!!.setBounds(Rect(left, top, left + width, top + height))
            mBitmapDrawable!!.draw(canvas)
            //canvas.drawBitmap(mBitmapDrawable.getBitmap(),getBounds(),getBounds(),new Paint());
        } else if (mNineBitmapDrawable != null) {
            mNineBitmapDrawable!!.setBounds(getBounds())
            mNineBitmapDrawable!!.draw(canvas)
        } else if (mLoadingDrawable != null) {
            mLoadingDrawable.setBounds(getBounds())
            mLoadingDrawable.draw(canvas)
            invalidateSelf()
        }
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        if (mGifDecoder2 != null) mGifDecoder2!!.free()
    }

    fun setScaleType(scaleType: Int) {
        if (mScaleType != scaleType) {
            mScaleType = scaleType
            invalidateSelf()
        }
    }

    fun setFillColor(fillColor: Int) {
        if (fillColor == mFillColor) {
            return
        }
        mFillColor = fillColor
    }

    override fun setAlpha(alpha: Int) {
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mColorFilter = colorFilter
    }

    override fun getOpacity(): Int {
        return PixelFormat.UNKNOWN
    }

    init {
        var path = path
        mLuaContext = context
        mLoadingDrawable = LoadingDrawable(context.context!!)
        if (path.lowercase(Locale.getDefault())
                .startsWith("http://") || path.lowercase(Locale.getDefault()).startsWith("https://")
        ) {
            initHttp(context, path)
        } else {
            if (!path.startsWith("/")) {
                val resolved = context.resolveLuaPath(path)
                if (resolved != null) {
                    path = resolved!!
                    init(path)
                }
            } else {
                init(path)
            }
        }
    }

    override fun run() {
        invalidateSelf()
    }

    override fun gc() {
        if (mGifDecoder2 != null) mGifDecoder2!!.free()
        if (mBitmapDrawable != null && mBitmapDrawable is BitmapDrawable) (mBitmapDrawable as BitmapDrawable).getBitmap()
            .recycle()
        if (mNineBitmapDrawable != null) mNineBitmapDrawable!!.gc()
        mGifDecoder2 = null
        mBitmapDrawable = null
        mNineBitmapDrawable = null
        mLoadingDrawable!!.setState(-1)
        mGc = true
    }

    override val isGc get() = mGc

    companion object {
        var cacheTime: Long = (7 * 24 * 60 * 60 * 1000).toLong()

        @Throws(IOException::class)
        fun getHttpBitmap(context: LuaContext, url: String): String {
            //Log.d(TAG, url);
            val path = context.resolveLuaExtDir("cache") + "/" + url.hashCode()
            val f = File(path)
            if (f.exists() && System.currentTimeMillis() - f.lastModified() < cacheTime) {
                return path
            }
            File(path).delete()
            val myFileUrl = URL(url)
            val conn = myFileUrl.openConnection() as HttpURLConnection
            conn.setConnectTimeout(120000)
            conn.setDoInput(true)
            conn.connect()
            val `is` = conn.getInputStream()
            val out = FileOutputStream(path)
            if (!LuaUtil.Companion.copyFile(`is`, out)) {
                out.close()
                `is`.close()
                File(path).delete()
                throw RuntimeException("LoadHttpBitmap Error.")
            }
            out.close()
            `is`.close()
            return path
        }

        val MATRIX: Int = (0)
        val FIT_XY: Int = (1)
        val FIT_START: Int = (2)
        val FIT_CENTER: Int = (3)
        val FIT_END: Int = (4)
        val CENTER: Int = (5)
        val CENTER_CROP: Int = (6)
        val CENTER_INSIDE: Int = (7)
    }
}
