package com.nirithy.lxclua.screencapture

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Created by ryze on 2016-5-26.
 */
object FileUtil {
    //系统保存截图的路径
    val SCREENCAPTURE_PATH: String =
        "ScreenCapture" + File.separator + "Screenshots" + File.separator

    //  public static final String SCREENCAPTURE_PATH = "ZAKER" + File.separator + "Screenshots" + File.separator;
    const val SCREENSHOT_NAME: String = "Screenshot"

    fun getAppPath(context: Context): String {
        if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            return Environment.getExternalStorageDirectory().toString()
        } else {
            return context.getFilesDir().toString()
        }
    }


    fun getScreenShots(context: Context): String {
        val stringBuffer = StringBuffer(getAppPath(context))
        stringBuffer.append(File.separator)

        stringBuffer.append(SCREENCAPTURE_PATH)

        val file = File(stringBuffer.toString())

        if (!file.exists()) {
            file.mkdirs()
        }

        return stringBuffer.toString()
    }

    fun getScreenShotsName(context: Context): String {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.CHINESE)

        val date = simpleDateFormat.format(Date())

        val stringBuffer = getScreenShots(context) + SCREENSHOT_NAME +
                "_" +
                date +
                ".png"

        return stringBuffer
    }
}
