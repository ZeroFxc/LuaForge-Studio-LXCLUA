package com.nirithy.lxclua

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.os.Environment
import com.luajava.LuaException
import dalvik.system.DexClassLoader
import java.io.File
import java.io.IOException

class LuaDexLoader(private val mContext: LuaContext) {
    val classLoaders: ArrayList<ClassLoader?> = ArrayList<ClassLoader?>()
    val librarys: HashMap<String?, String?> = HashMap<String?, String?>()

    private val luaDir: String?

    var assets: AssetManager? = null
        private set

    private var mResources: LuaResources? = null
    private var mTheme: Resources.Theme? = null
    private val odexDir: String?
    private val privateLibsDir: String

    init {
        luaDir = mContext.resolveLuaDir(null)
        val app: LuaApplication = LuaApplication.instance!!
        odexDir = app.odexDir

        // 初始化私有libs目录
        val ctx = mContext.context!!
        privateLibsDir = File(ctx.getFilesDir(), "private_libs").absolutePath
        val dir = File(privateLibsDir)
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    val theme: Resources.Theme
        get() = mTheme!!

    fun loadApp(pkg: String): LuaDexClassLoader? {
        try {
            var dex: LuaDexClassLoader? = dexCache.get(pkg)
            if (dex == null) {
                val manager = mContext.context!!.packageManager
                val info = manager.getPackageInfo(pkg, 0).applicationInfo
                dex = LuaDexClassLoader(
                    info!!.publicSourceDir,
                    LuaApplication.instance!!.odexDir,
                    info.nativeLibraryDir,
                    mContext.context!!.classLoader
                )
                dexCache.put(pkg, dex)
            }
            if (!classLoaders.contains(dex)) {
                classLoaders.add(dex)
            }
            return dex
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return null
    }

    @Throws(LuaException::class)
    fun loadLibs() {
        val libs = File(mContext.resolveLuaDir(null) + "/libs").listFiles()
        if (libs == null) return
        for (f in libs) {
            if (f.isDirectory()) continue
            if (f.absolutePath.endsWith(".so")) loadLib(f.getName())
            else loadDex(f.absolutePath)
        }
    }

    @Throws(LuaException::class)
    fun loadLib(name: String) {
        var fn: String? = name
        val i = name.indexOf(".")
        if (i > 0) fn = name.substring(0, i)
        if (fn!!.startsWith("lib")) fn = fn.substring(3)
        val libDir = mContext.context!!.getDir(fn, Context.MODE_PRIVATE).absolutePath
        val libPath = libDir + "/lib" + fn + ".so"
        var f = File(libPath)
        if (!f.exists()) {
            f = File(luaDir + "/libs/lib" + fn + ".so")
            if (!f.exists()) throw LuaException("can not find lib " + name)
            LuaUtil.Companion.copyFile(luaDir + "/libs/lib" + fn + ".so", libPath)
        }
        librarys.put(fn, libPath)
    }

    @Throws(LuaException::class)
    fun loadDex(path: String): DexClassLoader {
        var path = path
        var dex: LuaDexClassLoader? = dexCache.get(path)
        if (dex == null) dex = loadApp(path)
        if (dex == null) {
            val name: String? = path
            if (path.get(0) != '/') path = luaDir + "/" + path

            // 检查文件是否存在
            var srcFile = File(path)
            if (!srcFile.exists()) {
                if (File(path + ".dex").exists()) path += ".dex"
                else if (File(path + ".jar").exists()) path += ".jar"
                else throw LuaException(path + " not found")
                srcFile = File(path)
            }

            // Android 14+ 需要特殊处理
            var finalPath = path
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
                // Android 14+：文件必须是只读的
                finalPath = prepareFileForAndroid14(srcFile)
            }

            var id: String? = LuaUtil.Companion.getFileMD5(finalPath)
            if (id != null && id == "0") id = name
            dex = dexCache.get(id)

            if (dex == null) {
                dex = LuaDexClassLoader(
                    finalPath, odexDir,
                    LuaApplication.instance!!.applicationInfo.nativeLibraryDir,
                    mContext.context!!.classLoader
                )
                dexCache.put(id, dex)
            }
        }

        if (!classLoaders.contains(dex)) {
            classLoaders.add(dex)
            val dexPath = dex.dexPath
            if (dexPath?.endsWith(".jar") == true) loadResources(dexPath)
        }
        return dex
    }

    /**
     * Android 14+ 特殊处理：确保文件是只读的
     */
    @Throws(LuaException::class)
    private fun prepareFileForAndroid14(srcFile: File): String {
        try {
            // 检查文件是否在外部存储中
            if (isInExternalStorage(srcFile)) {
                // 复制到私有目录
                return copyToPrivateDirWithReadOnly(srcFile)
            } else {
                // 文件已经在私有目录，确保是只读的
                if (srcFile.exists() && srcFile.canWrite()) {
                    srcFile.setReadOnly()
                }
                return srcFile.absolutePath
            }
        } catch (e: Exception) {
            throw LuaException("Failed to prepare file for Android 14: " + e.message)
        }
    }

    /**
     * 检查文件是否在外部存储（可写的公共目录）中
     */
    private fun isInExternalStorage(file: File): Boolean {
        try {
            if (Environment.isExternalStorageRemovable()) {
                return false
            }

            val filePath = file.absolutePath
            val externalPaths = arrayOf<String>(
                Environment.getExternalStorageDirectory().absolutePath,
                "/sdcard",
                "/storage/emulated",
                "/mnt/sdcard"
            )

            for (externalPath in externalPaths) {
                if (filePath.startsWith(externalPath)) {
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * 将文件复制到应用私有目录并设置为只读（Android 14+ 安全模式）
     */
    @Throws(LuaException::class, IOException::class)
    private fun copyToPrivateDirWithReadOnly(srcFile: File): String {
        // 创建私有目录（如果不存在）
        val dir = File(privateLibsDir)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw LuaException("Failed to create directory: " + privateLibsDir)
            }
        }

        // 创建目标文件路径（保持文件名）
        val fileName = srcFile.getName()
        val destFile = File(dir, fileName)

        // 如果文件已存在，检查是否需要更新
        if (destFile.exists()) {
            // 检查文件大小和MD5是否相同
            val srcSize = srcFile.length()
            val destSize = destFile.length()

            if (srcSize == destSize) {
                // 文件大小相同，检查MD5
                val srcMd5: String? = LuaUtil.Companion.getFileMD5(srcFile.absolutePath)
                val destMd5: String? = LuaUtil.Companion.getFileMD5(destFile.absolutePath)

                if (srcMd5 != null && srcMd5 == destMd5) {
                    // 文件相同，确保文件是只读的
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        destFile.setReadOnly()
                    }
                    return destFile.absolutePath
                }
            }

            // 文件不同或无法获取MD5，删除旧文件
            if (!destFile.delete()) {
                // 如果删除失败，可以尝试重命名旧文件
                val oldFile = File(dir, fileName + ".old." + System.currentTimeMillis())
                destFile.renameTo(oldFile)
            }
        }

        // 根据文档要求，在写入前将文件设置为只读（避免竞态条件）
        // 先创建空文件并设置为只读
        destFile.createNewFile()
        destFile.setReadOnly()

        // 但是，我们需要写入文件内容，所以需要重新打开为可写
        // 创建一个临时文件写入，然后重命名
        val tempFile = File(dir, fileName + ".tmp." + System.currentTimeMillis())

        try {
            // 复制文件到临时文件
            LuaUtil.Companion.copyFile(srcFile.absolutePath, tempFile.absolutePath)

            // 验证复制是否成功
            if (!tempFile.exists()) {
                throw LuaException("Failed to create temporary file")
            }

            val srcSize = srcFile.length()
            val tempSize = tempFile.length()
            if (srcSize != tempSize) {
                throw LuaException("File size mismatch (source: " + srcSize + ", temp: " + tempSize + ")")
            }

            // 删除只读的空文件
            destFile.delete()

            // 将临时文件重命名为目标文件
            if (!tempFile.renameTo(destFile)) {
                throw LuaException("Failed to rename temporary file to destination")
            }

            // 设置目标文件为只读
            destFile.setReadOnly()

            // 验证最终文件
            if (!destFile.exists()) {
                throw LuaException("Destination file does not exist after rename")
            }

            return destFile.absolutePath
        } finally {
            // 清理临时文件
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun loadResources(path: String?) {
        try {
            val assetManager = AssetManager::class.java.newInstance()
            val addAssetPath = assetManager.javaClass.getMethod("addAssetPath", String::class.java)
            val ok = addAssetPath.invoke(assetManager, path) as Int
            if (ok == 0) return
            this.assets = assetManager
            val superRes = mContext.context!!.resources
            mResources = LuaResources(
                this.assets, superRes.getDisplayMetrics(),
                superRes.getConfiguration()
            )
            mResources!!.setSuperResources(superRes)
            mTheme = mResources!!.newTheme()
            mTheme!!.setTo(mContext.context!!.theme)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val resources: Resources
        get() = mResources!!

    /**
     * 清理私有目录中的旧文件
     */
    fun cleanupOldFiles() {
        try {
            val dir = File(privateLibsDir)
            if (!dir.exists() || !dir.isDirectory()) {
                return
            }

            val files = dir.listFiles()
            if (files == null) {
                return
            }

            // 清理超过30天的文件
            val cutoff = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)

            for (file in files) {
                // 不清理正在使用的文件
                if (file.lastModified() < cutoff) {
                    // 检查是否是.tmp文件或者.old文件
                    if (file.getName().contains(".tmp.") || file.getName().contains(".old.")) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private val dexCache = HashMap<String?, LuaDexClassLoader?>()
    }
}