package com.nirithy.lxclua.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.TargetApi
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewConfiguration
import com.nirithy.lxclua.util.VolatileBox
import com.nirithy.lxclua.util.VolatileDispose

class GlobalActionAutomator @TargetApi(Build.VERSION_CODES.N) constructor(
    private var mService: AccessibilityService?, private val mHandler: Handler?
) {
    private var mScreenMetrics: ScreenMetrics? = null

    fun setService(service: AccessibilityService?) {
        mService = service
    }

    fun setScreenMetrics(screenMetrics: ScreenMetrics?) {
        mScreenMetrics = screenMetrics
    }

    fun back(): Boolean {
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun home(): Boolean {
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun powerDialog(): Boolean {
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun performGlobalAction(globalAction: Int): Boolean {
        if (mService == null) return false
        return mService!!.performGlobalAction(globalAction)
    }

    fun notifications(): Boolean {
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun quickSettings(): Boolean {
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun recents(): Boolean {
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    fun splitScreen(): Boolean {
        return performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
    }

    fun gesture(start: Long, duration: Long, path: Path): Boolean {
        return gestures(GestureDescription.StrokeDescription(path, start, duration))
    }

    fun gesture(start: Long, duration: Long, vararg points: IntArray): Boolean {
        val path = pointsToPath(points)
        return gestures(GestureDescription.StrokeDescription(path, start, duration))
    }

    private fun pointsToPath(points: Array<out IntArray>): Path {
        val path = Path()
        path.moveTo(scaleX(points[0][0]).toFloat(), scaleY(points[0][1]).toFloat())
        for (i in 1..<points.size) {
            val point = points[i]
            path.lineTo(scaleX(point[0]).toFloat(), scaleY(point[1]).toFloat())
        }
        return path
    }

    fun gestureAsync(start: Long, duration: Long, vararg points: IntArray) {
        val path = pointsToPath(points)
        gesturesAsync(GestureDescription.StrokeDescription(path, start, duration))
    }

    fun gestures(vararg strokes: GestureDescription.StrokeDescription): Boolean {
        if (mService == null) return false
        val builder = GestureDescription.Builder()
        for (stroke in strokes) {
            builder.addStroke(stroke)
        }
        if (mHandler == null) {
            return gesturesWithoutHandler(builder.build())
        } else {
            return gesturesWithHandler(builder.build())
        }
    }

    private fun gesturesWithHandler(description: GestureDescription): Boolean {
        val result = VolatileDispose<Boolean?>()
        Log.i("GlobalActionAutomator", "dispatchGesture")
        return mService!!.dispatchGesture(
            description,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i("GlobalActionAutomator", "onCompleted")
                    result.setAndNotify(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.i("GlobalActionAutomator", "onCancelled")
                    result.setAndNotify(false)
                }
            },
            mHandler
        )
        //return result.blockedGet();
    }

    private fun gesturesWithoutHandler(description: GestureDescription): Boolean {
        prepareLooperIfNeeded()
        val result = VolatileBox<Boolean?>(false)
        val handler = Handler(Looper.myLooper()!!)
        mService!!.dispatchGesture(
            description,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    result.set(true)
                    quitLoop()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    result.set(false)
                    quitLoop()
                }
            },
            handler
        )
        Looper.loop()
        return result.get()!!
    }

    fun gesturesAsync(vararg strokes: GestureDescription.StrokeDescription) {
        if (mService == null) return
        val builder = GestureDescription.Builder()
        for (stroke in strokes) {
            builder.addStroke(stroke)
        }
        mService!!.dispatchGesture(builder.build(), null, null)
    }

    private fun quitLoop() {
        val looper = Looper.myLooper()
        if (looper != null) {
            looper.quit()
        }
    }

    private fun prepareLooperIfNeeded() {
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }
    }

    fun click(x: Int, y: Int): Boolean {
        return press(x, y, ViewConfiguration.getTapTimeout())
    }

    fun press(x: Int, y: Int, delay: Int): Boolean {
        return gesture(0, delay.toLong(), intArrayOf(x, y))
    }

    fun longClick(x: Int, y: Int): Boolean {
        return gesture(
            0,
            (ViewConfiguration.getLongPressTimeout() + 200).toLong(),
            intArrayOf(x, y)
        )
    }

    private fun scaleX(x: Int): Int {
        if (mScreenMetrics == null) return x
        return mScreenMetrics!!.scaleX(x)
    }

    private fun scaleY(y: Int): Int {
        if (mScreenMetrics == null) return y
        return mScreenMetrics!!.scaleY(y)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, delay: Int): Boolean {
        return gesture(0, delay.toLong(), intArrayOf(x1, y1), intArrayOf(x2, y2))
    }
}
