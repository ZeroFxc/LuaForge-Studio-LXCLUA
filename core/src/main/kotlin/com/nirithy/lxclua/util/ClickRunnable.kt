package com.nirithy.lxclua.util

import android.util.Log
import com.nirithy.lxclua.LuaAccessibilityService
import com.luajava.LuaTable

/**
 * Created by Administrator on 2017/08/31 0031.
 */
class ClickRunnable(
    private val mService: LuaAccessibilityService,
    private var mButtons: LuaTable<*, *>
) : Runnable {
    private var mIdx = 1
    private var mN = -1
    private var mM = -1
    private var mClickCallback: ClickCallback? = null
    private var mIsCancel = false
    private var mClick: ClickRunnable? = null

    fun cancel() {
        mIsCancel = true
        if (mClick != null) mClick!!.cancel()
    }

    fun canClick(callback: ClickCallback?): Boolean {
        mClickCallback = callback
        return canClick()
    }

    fun canClick(): Boolean {
        if (mButtons.length() == 0) return false
        val size = mButtons.length()
        for (i in 0..<size) {
            if (mIsCancel) {
                if (mClickCallback != null) mClickCallback!!.onDone(false, mButtons, null, -1)
                return false
            }
            val obj = mButtons.get(i + 1)
            if (obj is LuaTable<*, *>) {
                if (obj.length() == 0) continue
                val name = obj.get(1) as String?
                if (name == null) continue
                if (postClick(name)) {
                    mButtons = obj
                    return true
                }
            } else if (obj is String) {
                val node = mService.findAccessibilityNodeInfo(obj)
                if (node != null) {
                    mService.toClick2(node)
                    if (mClickCallback != null) mClickCallback!!.onDone(true, mButtons, obj, i)
                    return true
                }
            }
        }
        if (mClickCallback != null) mClickCallback!!.onDone(false, mButtons, null, -1)
        return false
    }

    private fun postClick(name: String?): Boolean {
        var name = name
        if (name == null) return false
        var time: Long = 1000
        var idx = name.lastIndexOf("$")
        if (idx > 0) {
            try {
                time = name.substring(idx + 1).toLong()
            } catch (e: Exception) {
                time = 1000
            }
            name = name.substring(0, idx)
        }
        idx = name.lastIndexOf(">")
        if (idx > 0) {
            if (mN < 0) {
                try {
                    mN = name.substring(idx + 1).toInt()
                } catch (e: Exception) {
                    mN = -1
                }
            }
            name = name.substring(0, idx)
        }
        idx = name.lastIndexOf("<")
        if (idx > 0) {
            if (mM < 0) {
                try {
                    mM = name.substring(idx + 1).toInt()
                } catch (e: Exception) {
                    mM = -1
                }
            }
            name = name.substring(0, idx)
        }
        mM--
        mN--
        val node = mService.findAccessibilityNodeInfo(name)
        Log.i("lua", "findAccessibilityNodeInfo " + name + "," + mN + "," + mM + "," + node)

        if (node != null) {
            mN = -1
            mService.toClick2(node)
            mService.getHandler().postDelayed(this, time)
            return true
        } else if (mN > 0 || mM > 0) {
            mService.getHandler().postDelayed(this, time)
            return true
        }
        if (mClickCallback != null) mClickCallback!!.onDone(true, mButtons, name, mIdx)
        return false
    }

    override fun run() {
        if (mIsCancel) {
            if (mClickCallback != null) mClickCallback!!.onDone(false, mButtons, null, -1)
            return
        }
        if (mN < 0 && mM < 0) mIdx++
        val obj = mButtons.get(mIdx)
        if (obj == null) {
            if (mClickCallback != null) mClickCallback!!.onDone(
                mIdx == mButtons.length(),
                mButtons,
                null,
                mIdx
            )
            return
        }
        if (obj is LuaTable<*, *>) {
            if (obj.length() == 0) return
            mClick = ClickRunnable(mService, obj)
            mClick!!.canClick(object : ClickCallback {
                override fun onDone(bool: Boolean, bs: LuaTable<*, *>?, name: String?, idx: Int) {
                    mClick = null
                    run()
                }
            })

            /*String name = (String) bs.get(1);
            if (name == null)
                return;
            if (postClick(name)) {
                mIdx = 1;
                mM = -1;
                mN = -1;
                mButtons = bs;
            }*/
        } else if (obj is String) {
            postClick(obj)
        }
    }


    interface ClickCallback {
        fun onDone(bool: Boolean, bs: LuaTable<*, *>?, name: String?, idx: Int)
    }
}
