package com.nirithy.lxclua.screencapture

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.nirithy.lxclua.LuaAccessibilityService

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class ScreenShot(private val mService: Context, private val mCallback: VirtualDisplay.Callback?) {
    private var mScreenCaptureListener: ScreenCaptureListener? = null
    private var mImage: Image? = null
    private var mMediaProjection: MediaProjection? = null
    private var mVirtualDisplay: VirtualDisplay? = null

    private var mImageReader: ImageReader? = null

    private var mScreenWidth = 0
    private var mScreenHeight = 0
    private var mScreenDensity = 0


    init {
        init()
        if (mResultData == null) {
            val intent = Intent(mService, ScreenCaptureActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            mService.startActivity(intent)
        } else {
            startVirtual()
        }
        //createImageReader();
    }

    private fun init() {
        val mWindowManager = mService.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        mWindowManager.getDefaultDisplay().getRealMetrics(metrics)
        mScreenDensity = metrics.densityDpi
        mScreenWidth = metrics.widthPixels
        mScreenHeight = metrics.heightPixels
        createImageReader()
    }


    fun startScreenShot(listener: ScreenCaptureListener?) {
        if (mScreenCaptureListener != null) return
        mScreenCaptureListener = listener
        startScreenShot()
    }

    fun startScreenShot() {
        val handler1 = Handler()
        handler1.postDelayed(object : Runnable {
            override fun run() {
                //start virtual
                startVirtual()
            }
        }, 5)

        handler1.postDelayed(object : Runnable {
            override fun run() {
                //capture the screen
                startCapture()
            }
        }, 100)
    }

    val screenShot: Bitmap?
        get() = this.capture

    private fun createImageReader() {
        mImageReader =
            ImageReader.newInstance(mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 1)
    }

    fun reSize() {
        stopVirtual()
        closeImageReader()
        init()
        startVirtual()
    }

    fun startVirtual() {
        if (mMediaProjection != null) {
            virtualDisplay()
        } else {
            setUpMediaProjection()
            virtualDisplay()
        }
    }

    fun setUpMediaProjection() {
        if (mMediaProjection != null) return
        if (mResultData == null) {
            val intent = Intent(mService, ScreenCaptureActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            mService.startActivity(intent)
        } else {
            val manager = this.mediaProjectionManager ?: return
            val data = mResultData!!
            mMediaProjection = manager.getMediaProjection(Activity.RESULT_OK, data)
        }
    }

    private val mediaProjectionManager: MediaProjectionManager?
        get() = mService.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?

    private fun virtualDisplay() {
        if (mMediaProjection == null) setUpMediaProjection()
        if (mMediaProjection == null) return
        if (mVirtualDisplay != null) return
        try {
            //init();
            mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(
                "screen-mirror",
                mScreenWidth,
                mScreenHeight,
                mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader!!.getSurface(),
                mCallback,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startCapture() {
        if (mImage != null) return
        mImage = mImageReader!!.acquireLatestImage()

        if (mImage == null) {
            if (mScreenCaptureListener != null) {
                mScreenCaptureListener!!.onScreenCaptureDone(null)
                mScreenCaptureListener = null
            }
        } else {
            val mSaveTask = SaveTask()
            mSaveTask.execute(mImage)
            //AsyncTaskCompat.executeParallel(mSaveTask, image);
        }
    }

    private val capture: Bitmap?
        get() {
            if (mImageReader == null) return null
            mImage = mImageReader!!.acquireLatestImage()

            if (mImage == null) {
                return null
            } else {
                val width = mImage!!.getWidth()
                val height = mImage!!.getHeight()
                val planes = mImage!!.getPlanes()
                val buffer = planes[0]!!.getBuffer()
                //每个像素的间距
                val pixelStride = planes[0]!!.getPixelStride()
                //总的间距
                val rowStride = planes[0]!!.getRowStride()
                val rowPadding = rowStride - pixelStride * width
                var bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                mImage!!.close()
                mImage = null
                return bitmap
            }
        }


    fun setScreenCaptureBitmap(bitmap: Bitmap?) {
        mScreenCaptureBitmap = bitmap
    }


    @Suppress("DEPRECATION")
    inner class SaveTask : AsyncTask<Image?, Void?, Bitmap?>() {
        override fun doInBackground(vararg params: Image?): Bitmap? {
            if (params.isEmpty() || params[0] == null) {
                return null
            }

            val image: Image = params[0]!!

            val width = image.getWidth()
            val height = image.getHeight()
            val planes = image.getPlanes()
            val buffer = planes[0]!!.getBuffer()
            //每个像素的间距
            val pixelStride = planes[0]!!.getPixelStride()
            //总的间距
            val rowStride = planes[0]!!.getRowStride()
            val rowPadding = rowStride - pixelStride * width
            var bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            image.close()
            mImage = null
            if (mScreenCaptureListener != null) {
                mScreenCaptureListener!!.onScreenCaptureDone(bitmap)
                mScreenCaptureListener = null
                return null
            }

            return null
        }

        override fun onPostExecute(bitmap: Bitmap?) {
            super.onPostExecute(bitmap)
            //预览图片
            if (bitmap != null) {
                setScreenCaptureBitmap(bitmap)
                Log.e("ryze", "获取图片成功")
                //mService.startActivity(PreviewPictureActivity.newIntent(mService));
            }
        }
    }


    private fun tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection!!.stop()
            mMediaProjection = null
        }
    }

    private fun stopVirtual() {
        if (mVirtualDisplay == null) {
            return
        }
        mVirtualDisplay!!.release()
        mVirtualDisplay = null
    }

    private fun closeImageReader() {
        if (mImageReader != null) mImageReader!!.close()
        mImageReader = null
    }


    fun release() {
        stopVirtual()
        tearDownMediaProjection()
        closeImageReader()
        mScreenShot = null
    }


    companion object {
        private var sService: LuaAccessibilityService? = null
        private var sScreenCaptureListener: ScreenCaptureListener? = null
        private var mResultData: Intent? = null

        fun getResultData(mService: LuaAccessibilityService?) {
            if (mService == null) return
            if (mResultData == null) {
                val intent = Intent(mService, ScreenCaptureActivity::class.java)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                mService.startActivity(intent)
            }
        }


        fun setResultData(mResultData: Intent?) {
            if (mResultData == null) {
                if (sService != null) Toast.makeText(sService, "未获得权限", Toast.LENGTH_SHORT)
                    .show()
                if (sScreenCaptureListener != null) sScreenCaptureListener!!.onScreenCaptureError("未获得权限")
                return
            }

            Companion.mResultData = mResultData
            if (sService != null) {
                sService!!.getHandler().postDelayed(object : Runnable {
                    override fun run() {
                        Companion.getScreenCaptureBitmap(sService, sScreenCaptureListener!!)
                    }
                }, 500)
            }
        }

        fun getScreenCaptureBitmap(
            mService: LuaAccessibilityService?,
            screenCaptureListener: ScreenCaptureListener
        ) {
            if (mService == null) return

            var mImageReader: ImageReader? = null
            var mMediaProjection: MediaProjection? = null
            var mVirtualDisplay: VirtualDisplay? = null
            var mImage: Image?
            sService = mService
            sScreenCaptureListener = screenCaptureListener
            try {
                if (mResultData == null) {
                    val intent = Intent(mService, ScreenCaptureActivity::class.java)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    mService.startActivity(intent)
                } else {
                    val mWindowManager =
                        mService.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
                    val metrics = DisplayMetrics()
                    val mScreenDensity: Int
                    val mScreenWidth: Int
                    val mScreenHeight: Int
                    if (mWindowManager != null) {
                        mWindowManager.getDefaultDisplay().getRealMetrics(metrics)
                        mScreenDensity = metrics.densityDpi
                        mScreenWidth = metrics.widthPixels
                        mScreenHeight = metrics.heightPixels
                    } else {
                        mScreenHeight = mService.height
                        mScreenWidth = mService.width
                        mScreenDensity = mService.density
                    }
                    mImageReader = ImageReader.newInstance(
                        mScreenWidth,
                        mScreenHeight,
                        PixelFormat.RGBA_8888,
                        1
                    )

                    mMediaProjection =
                        (mService.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(
                            Activity.RESULT_OK, mResultData!!
                        )

                    mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(
                        "screen-mirror",
                        mScreenWidth,
                        mScreenHeight,
                        mScreenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        mImageReader.getSurface(),
                        null,
                        null
                    )


                    mImage = mImageReader.acquireLatestImage()
                    for (i in 0..39) {
                        try {
                            Thread.sleep(5)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                        mImage = mImageReader.acquireLatestImage()

                        if (mImage != null) break
                    }

                    if (mImage == null) {
                        screenCaptureListener.onScreenCaptureError("请重试")
                    } else {
                        val width = mImage.getWidth()
                        val height = mImage.getHeight()
                        val planes = mImage.getPlanes()
                        val buffer = planes[0]!!.getBuffer()
                        //每个像素的间距
                        val pixelStride = planes[0]!!.getPixelStride()
                        //总的间距
                        val rowStride = planes[0]!!.getRowStride()
                        val rowPadding = rowStride - pixelStride * width
                        var bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_4444
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        mImage.close()
                        screenCaptureListener.onScreenCaptureDone(bitmap)
                    }
                    sService = null
                    sScreenCaptureListener = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                sScreenCaptureListener!!.onScreenCaptureError("请重试")
                sService = null
                sScreenCaptureListener = null
            } finally {
                if (mVirtualDisplay != null) mVirtualDisplay.release()
                if (mImageReader != null) {
                    mImageReader.close()
                }
                if (mMediaProjection != null) {
                    mMediaProjection.stop()
                }
            }
        }

        var mScreenCaptureBitmap: Bitmap? = null
        var appName: String = ""
        private var mScreenShot: ScreenShot? = null
    }
}
