package com.nirithy.lxclua

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt


object LuaBitmap {
    var cache: WeakHashMap<String?, WeakReference<Bitmap?>?> =
        WeakHashMap<String?, WeakReference<Bitmap?>?>()

    private const val l = 0
    var cacheTime: Long = (60 * 60 * 1000).toLong()
    private var sHeader: HashMap<String?, String?>? = null

    fun setHeader(header: HashMap<String?, String?>?) {
        sHeader = header
    }

    fun setUserAgent(userAgent: String?) {
        if (sHeader == null) sHeader = HashMap<String?, String?>()
        sHeader!!.put("User-Agent", userAgent)
    }

    fun setReferer(referer: String?) {
        if (sHeader == null) sHeader = HashMap<String?, String?>()
        sHeader!!.put("Referer", referer)
    }

    fun setCookie(cookie: String?) {
        if (sHeader == null) sHeader = HashMap<String?, String?>()
        sHeader!!.put("Cookie", cookie)
    }

    fun checkCache(context: LuaContext?, url: String): Boolean {
        // TODO: Implement this method
        @SuppressLint("DefaultLocale") val path =
            File(imageCacheDir, String.format("%08x", url.hashCode())).absolutePath
        val f = File(path)
        return f.exists() && cacheTime != -1L && System.currentTimeMillis() - f.lastModified() < cacheTime
    }

    @Throws(IOException::class)
    fun getLocalBitmap(url: String?): Bitmap? {
        val fis = FileInputStream(url)
        val bitmap = BitmapFactory.decodeStream(fis)
        fis.close()
        return bitmap
    }

    fun getLocalBitmap(context: LuaContext, url: String): Bitmap? {
        return decodeScale(context.width, File(url))
    }

    @Throws(IOException::class)
    fun getHttpBitmap(url: String?): Bitmap? {
        //Log.d(TAG, url);
        val myFileUrl = URL(url)
        val conn = myFileUrl.openConnection() as HttpURLConnection
        //conn.setConnectTimeout(0);
        conn.setDoInput(true)
        conn.connect()
        val `is` = conn.getInputStream()
        val bitmap = BitmapFactory.decodeStream(`is`)
        `is`.close()
        return bitmap
    }

    private val imageCacheDir: String = File(
        LuaApplication.instance!!.getExternalCacheDir(),
        "images"
    ).absolutePath

    init {
        File(imageCacheDir).mkdirs()
    }

    @Throws(IOException::class)
    fun getHttpBitmap(context: LuaContext, url: String): Bitmap? {
        //Log.d(TAG, url);
        @SuppressLint("DefaultLocale") val path =
            File(imageCacheDir, String.format("%08x", url.hashCode())).absolutePath
        val f = File(path)
        f.getParentFile().mkdirs()
        //context.sendMsg(System.currentTimeMillis() +";"+ f.lastModified() +";"+ mCacheTime +";"+(System.currentTimeMillis() - f.lastModified() < mCacheTime));
        if (f.exists() && cacheTime != -1L && System.currentTimeMillis() - f.lastModified() < cacheTime) {
            return decodeScale(context.width, File(path))
        }
        File(path).delete()
        val myFileUrl = URL(url)
        val conn = myFileUrl.openConnection()
        conn.setConnectTimeout(120000)
        conn.setDoInput(true)
        if (sHeader != null) {
            val entries = sHeader!!.entries
            for (entry in entries) {
                conn.setRequestProperty(entry.key, entry.value)
            }
        }
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
        //Bitmap bitmap = BitmapFactory.decodeStream(is);
        val bitmap = decodeScale(context.width, File(path))
        return bitmap
    }

    @Throws(IOException::class)
    fun getAssetBitmap(context: Context, name: String): Bitmap? {
        val am = context.getAssets()
        val `is` = am.open(name)
        val bitmap = BitmapFactory.decodeStream(`is`)
        `is`.close()
        return bitmap
    }

    @Throws(IOException::class)
    fun getBitmap(context: LuaContext, path: String): Bitmap? {
        val wRef = cache.get(path)
        if (wRef != null) {
            val bt = wRef.get()
            if (bt != null) return bt
        }

        val bitmap: Bitmap?
        if (path.lowercase(Locale.getDefault())
                .startsWith("http://") || path.lowercase(Locale.getDefault()).startsWith("https://")
        ) {
            bitmap = getHttpBitmap(context, path)
        } else if (path.get(0) != '/') {
            bitmap = getLocalBitmap(context, context.resolveLuaDir(null)!! + "/" + path)
        } else {
            bitmap = getLocalBitmap(context, path)
        }

        cache.put(path, WeakReference<Bitmap?>(bitmap))
        return bitmap
    }

    fun decodeScale(IMAGE_MAX_SIZE: Int, fis: File): Bitmap? {
        var b: Bitmap? = null

        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeFile(fis.absolutePath, o)
        var scale = 1
        if (o.outHeight > IMAGE_MAX_SIZE * 4 || o.outWidth > IMAGE_MAX_SIZE) {
            scale = 2.0.pow(
                Math.round(
                    ln(
                        IMAGE_MAX_SIZE / max(
                            o.outHeight,
                            o.outWidth
                        ).toDouble()
                    ) / ln(0.5)
                ).toInt().toDouble()
            ).toInt()
        }
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = scale

        b = BitmapFactory.decodeFile(fis.absolutePath, o2)

        return b
    }

    fun getImageFromPath(filePath: String?): Bitmap? {
        var bitmap: Bitmap? = null
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeFile(filePath, opts)

        //缩放图片，避免内存不足
        opts.inSampleSize = computeSampleSize(opts, -1, 250 * 250)
        opts.inJustDecodeBounds = false

        try {
            bitmap = BitmapFactory.decodeFile(filePath, opts)
        } catch (e: Exception) {
            // TODO: handle exception
        }
        return bitmap
    }

    //缩放图片算法
    private fun computeSampleSize(
        options: BitmapFactory.Options,
        minSideLength: Int,
        maxNumOfPixels: Int
    ): Int {
        val initialSize = computeInitialSampleSize(options, minSideLength, maxNumOfPixels)
        var roundedSize: Int
        if (initialSize <= 8) {
            roundedSize = 1
            while (roundedSize < initialSize) {
                roundedSize = roundedSize shl 1
            }
        } else {
            roundedSize = (initialSize + 7) / 8 * 8
        }
        return roundedSize
    }

    private fun computeInitialSampleSize(
        options: BitmapFactory.Options,
        minSideLength: Int,
        maxNumOfPixels: Int
    ): Int {
        val w = options.outWidth.toDouble()
        val h = options.outHeight.toDouble()
        val lowerBound = if (maxNumOfPixels == -1) 1 else ceil(sqrt(w * h / maxNumOfPixels)).toInt()
        val upperBound = if (minSideLength == -1) 128 else min(
            floor(w / minSideLength),
            floor(h / minSideLength)
        ).toInt()
        if (upperBound < lowerBound) {
            // return the larger one when there is no overlapping zone.
            return lowerBound
        }
        if ((maxNumOfPixels == -1) && (minSideLength == -1)) {
            return 1
        } else if (minSideLength == -1) {
            return lowerBound
        } else {
            return upperBound
        }
    }

    fun getBitmapFromFile(file: File?, width: Int, height: Int): Bitmap? {
        var opts: BitmapFactory.Options? = null
        if (null != file && file.exists()) {
            if (width > 0 && height > 0) {
                opts = BitmapFactory.Options()
                // 只是返回的是图片的宽和高，并不是返回一个Bitmap对象
                opts.inJustDecodeBounds = true
                // 信息没有保存在bitmap里面，而是保存在options里面
                BitmapFactory.decodeFile(file.getPath(), opts)
                // 计算图片缩放比例
                val minSideLength = min(width, height)
                // 缩略图大小为原始图片大小的几分之一。根据业务需求来做。
                opts.inSampleSize = computeSampleSize(
                    opts, minSideLength,
                    width * height
                )
                // 重新读入图片，注意此时已经把options.inJustDecodeBounds设回false
                opts.inJustDecodeBounds = false
                // 设置是否深拷贝，与inPurgeable结合使用
                opts.inInputShareable = true
                // 设置为True时，表示系统内存不足时可以被回 收，设置为False时，表示不能被回收。
                opts.inPurgeable = true
            }
            try {
                return BitmapFactory.decodeFile(file.getPath(), opts)
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
            }
        }
        return null
    }

    @JvmStatic
    fun removeBitmap(obj: Bitmap) {
        val sets = cache.entries
        for (set in sets) {
            if (obj == set.value!!.get()) {
                cache.remove(set.key)
                return
            }
        }
    }
}
