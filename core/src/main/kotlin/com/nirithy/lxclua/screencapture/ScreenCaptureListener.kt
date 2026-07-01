package com.nirithy.lxclua.screencapture

import android.graphics.Bitmap

/**
 * Created by Administrator on 2017/08/06 0006.
 */
interface ScreenCaptureListener {
    fun onScreenCaptureDone(bitmap: Bitmap?)

    fun onScreenCaptureError(msg: String?)
}
