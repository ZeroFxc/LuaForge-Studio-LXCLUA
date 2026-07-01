package com.nirithy.lxclua

import java.io.IOException

object ZipUtil {
    fun zip(sourceFilePath: String?, zipFilePath: String?): Boolean {
        return LuaUtil.Companion.zip(sourceFilePath!!, zipFilePath!!)
    }

    fun unzip(zipPath: String?, destPath: String?): Boolean {
        try {
            LuaUtil.Companion.unZip(zipPath!!, destPath!!)
            return true
        } catch (e: IOException) {
            return false
        }
    }
}
