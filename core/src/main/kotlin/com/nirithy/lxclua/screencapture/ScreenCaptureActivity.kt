package com.nirithy.lxclua.screencapture

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class ScreenCaptureActivity : Activity() {
    private var view: TextView? = null
    private val permissions: ArrayList<String?>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = TextView(this)
        view!!.setText("请授予权限")
        setContentView(view)
        requesturePermission()
    }

    fun requesturePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //5.0 之后才允许使用屏幕截图
            Toast.makeText(this, "仅支持安卓5以上系统", Toast.LENGTH_SHORT).show()
            //TalkManAccessibilityService.getInstance().toBack();
            return
        }
        try {
            val mediaProjectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ScreenShot.Companion.setResultData(null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_MEDIA_PROJECTION -> if (resultCode == RESULT_OK && data != null) {
                ScreenShot.Companion.setResultData(data)
                //Toast.makeText(this,"获得权限成功",Toast.LENGTH_SHORT).show();
            }

            else -> ScreenShot.Companion.setResultData(null)
        }
        finish()
    }

    override fun finish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            super.finish()
        }
    }

    companion object {
        const val REQUEST_MEDIA_PROJECTION: Int = 18
    }
}
