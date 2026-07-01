package com.nirithy.lxclua.util

import android.app.Activity
import android.util.DisplayMetrics
import android.view.Display


/**
 * Created by Stardust on 2017/4/26.
 */
class ScreenMetrics {
    private var mDesignWidth = 0
    private var mDesignHeight = 0

    constructor(designWidth: Int, designHeight: Int) {
        mDesignWidth = designWidth
        mDesignHeight = designHeight
    }

    constructor()

    fun setDesignWidth(designWidth: Int) {
        mDesignWidth = designWidth
    }

    fun setDesignHeight(designHeight: Int) {
        mDesignHeight = designHeight
    }

    fun setScreenMetrics(width: Int, height: Int) {
        mDesignWidth = width
        mDesignHeight = height
    }

    /** 实例级缩放X坐标，委托给 companion 方法 */
    fun scaleX(x: Int): Int = ScreenMetrics.scaleX(x, mDesignWidth)

    /** 实例级缩放Y坐标，委托给 companion 方法 */
    fun scaleY(y: Int): Int = ScreenMetrics.scaleY(y, mDesignHeight)

    companion object {
        var deviceScreenHeight: Int = 0
            private set
        var deviceScreenWidth: Int = 0
            private set
        private var initialized = false
        var deviceScreenDensity: Int = 0
            private set
        private var display: Display? = null

        fun initIfNeeded(activity: Activity) {
            if (initialized) return
            val metrics = DisplayMetrics()
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics)
            deviceScreenHeight = metrics.heightPixels
            deviceScreenWidth = metrics.widthPixels
            deviceScreenDensity = metrics.densityDpi
            display = activity.getWindowManager().getDefaultDisplay()
            initialized = true
        }

        @JvmOverloads
        fun scaleX(x: Int, width: Int = 0): Int {
            if (width == 0 || !initialized) return x
            return x * deviceScreenWidth / width
        }

        @JvmOverloads
        fun scaleY(y: Int, height: Int = 0): Int {
            if (height == 0 || !initialized) return y
            return y * deviceScreenHeight / height
        }


        @JvmOverloads
        fun rescaleX(x: Int, width: Int = 0): Int {
            if (width == 0 || !initialized) return x
            return x * width / deviceScreenWidth
        }


        @JvmOverloads
        fun rescaleY(y: Int, height: Int = 0): Int {
            if (height == 0 || !initialized) return y
            return y * height / deviceScreenHeight
        }
    }
}

