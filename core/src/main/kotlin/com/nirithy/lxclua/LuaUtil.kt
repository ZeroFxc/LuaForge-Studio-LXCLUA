package com.nirithy.lxclua

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.luajava.LuaFunction
import com.luajava.LuaString
import dalvik.system.DexFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.Adler32
import java.util.zip.CheckedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.sqrt

class LuaUtil {
    fun toBlack(path: String?, n: Float, h: Int, o: Int): BitmapDrawable {
        val image = BitmapFactory.decodeFile(path)
        val width = image.width
        val height = image.height
        val imageRet = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        //int[][] colors = new int[width][height];
        val colors = IntArray(width * height)
        val vs = FloatArray(width * height)
        val hsv = FloatArray(3)
        var v = 0f
        for (y in 0..<height) {
            for (x in 0..<width) {
                val color1 = image.getPixel(x, y)
                Color.colorToHSV(color1, hsv)
                //int v= ((int) (hsv[2]*n))*255/n;
                //float v2=v/10;
                vs[x + width * y] = hsv[2]
                v += hsv[2]
                //imageRet.setPixel(x,y, Color.rgb(v,v,v));
            }
        }
        val vv = v / (width * height) * n
        val color = Array<IntArray?>(width) { IntArray(height) }
        /*for (int i=0;i<width*height;i++){
           if(vs[i]>vv)
               colors[i]=-1;
           else
               colors[i]=0xff000000;
       }*/
        for (y in 0..<height) {
            for (x in 0..<width) {
                val i = x + width * y
                if (vs[i] > vv) {
                    colors[i] = -1
                    color[x]!![y] = 1
                } else {
                    colors[i] = -0x1000000
                    color[x]!![y] = 0
                }
            }
        }
        var ret = 0
        for (x in width / 2..<width - 10) {
            for (y in width / 3..<width) {
                if (check(x, y, color, h, o)) {
                    ret = x
                    Log.i("find_color", ret.toString() + "")
                    break
                }
            }
        }
        return BitmapDrawable(Bitmap.createBitmap(colors, width, height, Bitmap.Config.RGB_565))
    }

    private fun check(x: Int, y: Int, color: Array<IntArray?>, h: Int, o: Int): Boolean {
        for (i in 0..<h) {
            if (!(color[x]!![y + i] == 1 && color[x + o]!![y + i] == 0)) {
                return false
            }
        }
        return true
    }

    /**
     * 通过像素对比来计算偏差值
     *
     * @param path1 原图位置
     * @param path2 滑块图位置
     * @return 偏差值
     */
    fun getDifferenceValue(path1: String, path2: String): Int {
        var result = 0
        val file = File(path1)
        val file1 = File(path2)
        try {
            val image = BitmapFactory.decodeFile(path1)
            val image1 = BitmapFactory.decodeFile(path2)

            val width = image.width
            val height = image.height
            val colors = Array<IntArray?>(width) { IntArray(height) }
            for (x in 1..<width) {
                for (y in 1..<height) {
                    val color1 = image.getPixel(x, y)
                    val color2 = image1.getPixel(x, y)
                    if (color1 == color2) {
                        colors[x - 1]!![y - 1] = 0
                    } else {
                        colors[x - 1]!![y - 1] = 1
                    }
                }
            }
            var min = 999
            var max = -1
            for (x in colors.indices) {
                for (y in colors[x]!!.indices) {
                    if (colors[x]!![y] == 1) {
                        colors[x]!![y] = checkPixel(x, y, colors)
                        if (colors[x]!![y] == 1) {
                            if (x > max) {
                                max = x
                            } else if (x < min) {
                                min = x
                            }
                        }
                    }
                }
            }
            result = (max + min) / 2
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun checkPixel(x: Int, y: Int, colors: Array<IntArray?>): Int {
        val result = colors[x]!![y]
        var num = 0
        if ((y + 30) < colors[x]!!.size) {
            for (i in 1..30) {
                val color = colors[x]!![y + i]
                if (color == 0) {
                    num += 1
                }
            }
            if (num > 15) {
                return 0
            }
        }
        return result
    }

    companion object {
        /**
         * 截屏
         *
         * @param activity
         * @return
         */
        fun captureScreen(activity: Activity): Bitmap {
// 获取屏幕大小：
            val metrics = DisplayMetrics()
            val WM = activity
                .getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = WM.getDefaultDisplay()
            display.getMetrics(metrics)
            val height = metrics.heightPixels // 屏幕高
            val width = metrics.widthPixels // 屏幕的宽
            // 获取显示方式
            val pixelformat = display.getPixelFormat()
            val localPixelFormat1 = PixelFormat()
            PixelFormat.getPixelFormatInfo(pixelformat, localPixelFormat1)
            val deepth = localPixelFormat1.bytesPerPixel // 位深
            val piex = ByteArray(height * width * deepth)
            try {
                Runtime.getRuntime().exec(
                    arrayOf<String>(
                        "/system/bin/su", "-c",
                        "chmod 777 /dev/graphics/fb0"
                    )
                )
            } catch (e: IOException) {
                e.printStackTrace()
            }
            try {
// 获取fb0数据输入流
                val stream: InputStream = FileInputStream(
                    File(
                        "/dev/graphics/fb0"
                    )
                )
                val dStream = DataInputStream(stream)
                dStream.readFully(piex)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // 保存图片
            val colors = IntArray(height * width)
            for (m in colors.indices) {
                val r = (piex[m * 4].toInt() and 0xFF)
                val g = (piex[m * 4 + 1].toInt() and 0xFF)
                val b = (piex[m * 4 + 2].toInt() and 0xFF)
                val a = (piex[m * 4 + 3].toInt() and 0xFF)
                colors[m] = (a shl 24) + (r shl 16) + (g shl 8) + b
            }
            // piex生成Bitmap
            val bitmap = Bitmap.createBitmap(
                colors, width, height,
                Bitmap.Config.ARGB_8888
            )
            return bitmap
        }

        //读取asset文件
        @Throws(IOException::class)
        fun readAsset(context: Context, name: String): ByteArray {
            val am = context.getAssets()
            val `is` = am.open(name)
            val ret: ByteArray = readAll(`is`)
            `is`.close()
            //am.close();
            return ret
        }

        @Throws(IOException::class)
        fun readAll(input: InputStream): ByteArray {
            val output = ByteArrayOutputStream(8192)
            val buffer = ByteArray(8192)
            var n = 0
            while (-1 != (input.read(buffer).also { n = it })) {
                output.write(buffer, 0, n)
            }
            val ret = output.toByteArray()
            output.close()
            return ret
        }

        //复制asset文件到sd卡
        @Throws(IOException::class)
        fun assetsToSD(context: Context, InFileName: String, OutFileName: String?) {
            val myInput: InputStream?
            val myOutput: OutputStream = FileOutputStream(OutFileName)
            myInput = context.getAssets().open(InFileName)
            val buffer = ByteArray(8192)
            var length = myInput.read(buffer)
            while (length > 0) {
                myOutput.write(buffer, 0, length)
                length = myInput.read(buffer)
            }

            myOutput.flush()
            myInput.close()
            myOutput.close()
        }

        fun copyFile(from: String?, to: String?) {
            try {
                copyFile(FileInputStream(from), FileOutputStream(to))
            } catch (e: IOException) {
                Log.i("lua", e.message!!)
            }
        }

        fun copyFile(`in`: InputStream, out: OutputStream): Boolean {
            try {
                var byteread = 0
                val buffer = ByteArray(8192)
                while ((`in`.read(buffer).also { byteread = it }) != -1) {
                    out.write(buffer, 0, byteread)
                }
                //in.close();
                //out.close();
            } catch (e: Exception) {
                Log.i("lua", e.message!!)
                return false
            }
            return true
        }

        fun copyDir(from: String, to: String): Boolean {
            return copyDir(File(from), File(to))
        }

        fun copyDir(from: File, to: File): Boolean {
            var ret = true
            val p = to.getParentFile()
            if (!p!!.exists()) p.mkdirs()
            if (from.isDirectory()) {
                val fs = from.listFiles()
                if (fs != null && fs.size != 0) {
                    for (f in fs) ret = copyDir(f, File(to, f.getName()))
                } else {
                    if (!to.exists()) ret = to.mkdirs()
                }
            } else {
                try {
                    if (!to.exists()) to.createNewFile()
                    ret = copyFile(FileInputStream(from), FileOutputStream(to))
                } catch (e: IOException) {
                    Log.i("lua", e.message!!)
                    ret = false
                }
            }
            return ret
        }

        @JvmStatic
        fun rmDir(dir: File): Boolean {
            if (dir.isDirectory()) {
                val fs = dir.listFiles()
                for (f in fs!!) rmDir(f)
            }
            return dir.delete()
        }

        fun rmDir(dir: File, ext: String) {
            if (dir.isDirectory()) {
                val fs = dir.listFiles()
                for (f in fs!!) rmDir(f, ext)
                dir.delete()
            }
            if (dir.getName().endsWith(ext)) dir.delete()
        }

        @Throws(IOException::class)
        fun readZip(zippath: String?, filepath: String?): ByteArray {
            val zip = ZipFile(zippath)
            val entey = zip.getEntry(filepath)
            val `is` = zip.getInputStream(entey)
            return readAll(`is`)
        }

        // 计算文件的 MD5 值
        fun getFileMD5(file: String): String? {
            return getFileMD5(File(file))
        }

        fun getFileMD5(file: File?): String? {
            try {
                return getFileMD5(FileInputStream(file))
            } catch (e: FileNotFoundException) {
                return null
            }
        }

        fun getFileMD5(`in`: InputStream): String? {
            val buffer = ByteArray(8192)
            var len: Int
            try {
                val digest = MessageDigest.getInstance("MD5")
                while ((`in`.read(buffer).also { len = it }) != -1) {
                    digest.update(buffer, 0, len)
                }
                val bigInt = BigInteger(1, digest.digest())
                return bigInt.toString(16)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            } finally {
                try {
                    `in`.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 计算文件的 SHA-1 值
        fun getFileSha1(file: String): String? {
            return getFileMD5(File(file))
        }

        fun getFileSha1(file: File?): String? {
            try {
                return getFileSha1(FileInputStream(file))
            } catch (e: FileNotFoundException) {
                return null
            }
        }

        fun getFileSha1(`in`: InputStream): String? {
            val buffer = ByteArray(8192)
            var len: Int
            try {
                val digest = MessageDigest.getInstance("SHA-1")
                while ((`in`.read(buffer).also { len = it }) != -1) {
                    digest.update(buffer, 0, len)
                }
                val bigInt = BigInteger(1, digest.digest())
                return bigInt.toString(16)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            } finally {
                try {
                    `in`.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun getMessageDigest(`in`: String, algorithm: String): String? {
            val buffer = `in`.toByteArray()
            val len = buffer.size
            try {
                val digest = MessageDigest.getInstance(algorithm)
                digest.update(buffer, 0, len)
                val bigInt = BigInteger(1, digest.digest())
                return bigInt.toString(16)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        fun getMD5(`in`: String): String? {
            val buffer = `in`.toByteArray()
            val len = buffer.size
            try {
                val digest = MessageDigest.getInstance("MD5")
                digest.update(buffer, 0, len)
                val bigInt = BigInteger(1, digest.digest())
                return bigInt.toString(16)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        fun getSha1(`in`: String): String? {
            val buffer = `in`.toByteArray()
            val len = buffer.size
            try {
                val digest = MessageDigest.getInstance("SHA-1")
                digest.update(buffer, 0, len)
                val bigInt = BigInteger(1, digest.digest())
                return bigInt.toString(16)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        fun getAllName(context: Context, path: String?): Array<String?> {
            val ret = ArrayList<String?>()
            try {
                val dex = DexFile(context.getPackageCodePath())
                val cls = dex.entries()
                while (cls.hasMoreElements()) {
                    ret.add(cls.nextElement())
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            try {
                val zip = ZipFile(path)
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    ret.add(
                        entries.nextElement().getName().replace("/".toRegex(), ".")
                            .replace(".class", "")
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val arr = arrayOfNulls<String>(ret.size)
            ret.toArray<String?>(arr)
            return arr
        }


        @Throws(IOException::class)
        fun unZip(SourceDir: String, bool: Boolean) {
            if (!bool) {
                unZip(SourceDir)
                return
            }
            var name = File(SourceDir).getName()
            var i = name.lastIndexOf(".")
            if (i > 0) {
                name = name.substring(0, i)
            }
            i = name.indexOf("_")
            if (i > 0) {
                name = name.substring(0, i)
            }
            i = name.indexOf("(")
            if (i > 0) {
                name = name.substring(0, i)
            }
            unZip(SourceDir, File(SourceDir).getParent() + File.separator + name, "")
        }

        @JvmOverloads
        @Throws(IOException::class)
        fun unZip(
            SourceDir: String?,
            extDir: String? = File(SourceDir).getParent(),
            fileExt: String = ""
        ) {
            val zip = ZipFile(SourceDir)
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry: ZipEntry = entries.nextElement()
                val name = entry.getName()
                // 跳过无效条目（name 为 null 会导致创建 null 文件夹）
                if (name == null) continue
                if (!name.startsWith(fileExt)) continue
                val path = name
                if (entry.isDirectory()) {
                    val f = File(extDir + File.separator + path)
                    if (!f.exists()) f.mkdirs()
                } else {
                    val fname = extDir + File.separator + path
                    val temp = File(fname).getParentFile()
                    if (!temp!!.exists()) {
                        if (!temp.mkdirs()) {
                            throw RuntimeException("create file " + temp.getName() + " fail")
                        }
                    }

                    val out = FileOutputStream(extDir + File.separator + path)
                    val `in` = zip.getInputStream(entry)
                    val buf = ByteArray(8192)
                    var count = 0
                    while ((`in`.read(buf).also { count = it }) != -1) {
                        out.write(buf, 0, count)
                    }
                    out.close()
                    `in`.close()
                }
            }
            zip.close()
        }

        private val BUFFER = ByteArray(8192)

        @JvmOverloads
        fun zip(
            sourceFilePath: String,
            zipFilePath: String? = File(sourceFilePath).getParent()
        ): Boolean {
            val f = File(sourceFilePath)
            return zip(sourceFilePath, zipFilePath, f.getName() + ".zip")
        }

        fun zip(sourceFilePath: String, zipFilePath: String?, zipFileName: String): Boolean {
            var result = false
            val source = File(sourceFilePath)
            val zipFile = File(zipFilePath, zipFileName)
            if (!zipFile.getParentFile().exists()) {
                if (!zipFile.getParentFile().mkdirs()) {
                    return result
                }
            }
            if (zipFile.exists()) {
                try {
                    zipFile.createNewFile()
                } catch (e: IOException) {
                    return result
                }
            }

            var dest: FileOutputStream? = null
            var out: ZipOutputStream? = null
            try {
                dest = FileOutputStream(zipFile)
                val checksum = CheckedOutputStream(dest, Adler32())
                out = ZipOutputStream(BufferedOutputStream(checksum))
                //out.setMethod(ZipOutputStream.DEFLATED);
                compress(source, out, "")
                checksum.getChecksum().getValue()
                result = true
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } finally {
                if (out != null) {
                    try {
                        out.closeEntry()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    try {
                        out.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            return result
        }

        private fun compress(file: File, out: ZipOutputStream, mainFileName: String?) {
            if (file.isFile()) {
                var fi: FileInputStream? = null
                var origin: BufferedInputStream? = null
                try {
                    fi = FileInputStream(file)
                    origin = BufferedInputStream(fi, BUFFER.size)
                    //int index=file.getAbsolutePath().indexOf(mainFileName);
                    val entryName = mainFileName + file.getName()
                    println(entryName)
                    val entry = ZipEntry(entryName)
                    out.putNextEntry(entry)
                    //			byte[] data = new byte[BUFFER];
                    var count: Int
                    while ((origin.read(BUFFER, 0, BUFFER.size).also { count = it }) != -1) {
                        out.write(BUFFER, 0, count)
                    }
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    if (origin != null) {
                        try {
                            origin.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            } else if (file.isDirectory()) {
                val fs = file.listFiles()
                if (fs != null) {
                    for (f in fs) {
                        if (f.isFile()) compress(f, out, mainFileName)
                        else compress(f, out, mainFileName + f.getName() + "/")
                    }
                }
            }
        }

        val mFileTypes: HashMap<String?, String?> = HashMap<String?, String?>()

        init {
            // images
            mFileTypes.put("FFD8FF", "jpg")
            mFileTypes.put("89504E47", "png")
            mFileTypes.put("47494638", "gif")
            mFileTypes.put("49492A00", "tif")
            mFileTypes.put("424D", "bmp")
            //other
            mFileTypes.put("41433130", "dwg") // CAD
            mFileTypes.put("38425053", "psd")
            mFileTypes.put("7B5C727466", "rtf") // 日记本
            mFileTypes.put("3C3F786D6C", "xml")
            mFileTypes.put("68746D6C3E", "html")
            mFileTypes.put("44656C69766572792D646174653A", "eml") // 邮件
            mFileTypes.put("D0CF11E0", "doc")
            mFileTypes.put("5374616E64617264204A", "mdb")
            mFileTypes.put("252150532D41646F6265", "ps")
            mFileTypes.put("255044462D312E", "pdf")
            mFileTypes.put("504B0304", "docx")
            mFileTypes.put("52617221", "rar")
            mFileTypes.put("57415645", "wav")
            mFileTypes.put("41564920", "avi")
            mFileTypes.put("2E524D46", "rm")
            mFileTypes.put("000001BA", "mpg")
            mFileTypes.put("000001B3", "mpg")
            mFileTypes.put("6D6F6F76", "mov")
            mFileTypes.put("3026B2758E66CF11", "asf")
            mFileTypes.put("4D546864", "mid")
            mFileTypes.put("1F8B08", "gz")
        }

        fun getFileType(path: String?): String? {
            try {
                return mFileTypes.get(getFileHeader(FileInputStream(path)))
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
            return "unknown"
        }


        fun getFileType(file: File?): String? {
            try {
                return mFileTypes.get(getFileHeader(FileInputStream(file)))
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
            return "unknown"
        }

        /**
         * 获取文件类型
         * ps:流会关闭
         *
         * @param inputStream
         * @return
         */
        fun getFileType(inputStream: InputStream?): String? {
            return mFileTypes.get(getFileHeader(inputStream))
        }

        fun getFileHeader(inputStream: InputStream?): String? {
            var value: String? = null
            try {
                val b = ByteArray(4)
                /*int read() 从此输入流中读取一个数据字节。
             *int read(byte[] b) 从此输入流中将最多 b.length 个字节的数据读入一个 byte 数组中。
             * int read(byte[] b, int off, int len) 从此输入流中将最多 len 个字节的数据读入一个 byte 数组中。
             */
                inputStream!!.read(b, 0, b.size)
                value = bytesToHexString(b)
            } catch (e: Exception) {
            } finally {
                if (null != inputStream) {
                    try {
                        inputStream.close()
                    } catch (e: IOException) {
                    }
                }
            }
            return value
        }

        /**
         * 将要读取文件头信息的文件的byte数组转换成string类型表示
         *
         * @param src 要读取文件头信息的文件的byte数组
         * @return 文件头信息
         */
        private fun bytesToHexString(src: ByteArray?): String? {
            val builder = StringBuilder()
            if (src == null || src.size <= 0) {
                return null
            }
            var hv: String?
            for (i in src.indices) {
                // 以十六进制（基数 16）无符号整数形式返回一个整数参数的字符串表示形式，并转换为大写
                hv = Integer.toHexString(src[i].toInt() and 0xFF).uppercase(Locale.getDefault())
                if (hv.length < 2) {
                    builder.append(0)
                }
                builder.append(hv)
            }
            return builder.toString()
        }

        fun dump(func: LuaFunction<*>): Any {
            try {
                val bytes = func.dump()
                val w = sqrt(bytes.size.toDouble()).toInt()
                val h = w + 1
                val ints = IntArray(w * h)
                val len = bytes.size / 4
                for (i in 0..<len) {
                    val l = i * 4
                    ints[i] = Color.argb(
                        bytes[l].toInt(),
                        bytes[l + 1].toInt(),
                        bytes[l + 2].toInt(),
                        bytes[l + 3].toInt()
                    )
                }
                val bmp = Bitmap.createBitmap(ints, w, h, Bitmap.Config.ARGB_8888)
                val out = FileOutputStream("/sdcard/a.png")
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.close()
                return bmp
            } catch (e: Exception) {
                e.printStackTrace()
                return e
            }
        }

        /*public static void createImage(int width, int height, int ints[][], String name) throws IOException {
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphic = bi.createGraphics();
        graphic.setColor(new Color(0x003D1CFF));
        graphic.fillRect(0, 0, width, height);
        for (int x = 0; x < ints.length; x++) {
            for (int y = 0; y < ints[x].length; y++) {
                if (ints[x][y] == 1) {
                    bi.setRGB(x, y, 0xFF7F2E);
                }
            }
        }
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("png");
        ImageWriter writer = it.next();
        File f = new File("c://" + name + ".png");
        ImageOutputStream ios = ImageIO.createImageOutputStream(f);
        writer.setOutput(ios);
        writer.write(bi);
    }*/
        private fun compare(str: String, target: String): Int {
            val d: Array<IntArray?>? // 矩阵
            val n = str.length
            val m = target.length
            var i: Int // 遍历str的
            var j: Int // 遍历target的
            var ch1: Char // str的
            var ch2: Char // target的
            var temp: Int // 记录相同字符,在某个矩阵位置值的增量,不是0就是1
            if (n == 0) {
                return m
            }
            if (m == 0) {
                return n
            }
            d = Array<IntArray?>(n + 1) { IntArray(m + 1) }
            // 初始化第一列
            i = 0
            while (i <= n) {
                d[i]!![0] = i
                i++
            }
            // 初始化第一行
            j = 0
            while (j <= m) {
                d[0]!![j] = j
                j++
            }
            i = 1
            while (i <= n) {
                // 遍历str
                ch1 = str.get(i - 1)
                // 去匹配target
                j = 1
                while (j <= m) {
                    ch2 = target.get(j - 1)
                    if (ch1 == ch2 || ch1.code == ch2.code + 32 || ch1.code + 32 == ch2.code) {
                        temp = 0
                    } else {
                        temp = 1
                    }
                    // 左边+1,上边+1, 左上角+temp取最小
                    d[i]!![j] = min(d[i - 1]!![j] + 1, d[i]!![j - 1] + 1, d[i - 1]!![j - 1] + temp)
                    j++
                }
                i++
            }
            return d[n]!![m]
        }


        /**
         * 获取最小的值
         */
        private fun min(one: Int, two: Int, three: Int): Int {
            var one = one
            return if (((if (one < two) one else two).also { one = it }) < three) one else three
        }

        /**
         * 获取两字符串的相似度
         */
        fun getSimilarityRatio(str: String, target: String): Float {
            val max = max(str.length, target.length)
            return 1 - compare(str, target).toFloat() / max
        }

        @Throws(IOException::class)
        fun readZipFile(zippath: String?, filepath: String?): LuaString {
            val zip = ZipFile(zippath)
            val entey = zip.getEntry(filepath)
            val `is` = zip.getInputStream(entey)
            return LuaString(readAll(`is`))
        }

        @Throws(IOException::class)
        fun readApkFile(filepath: String?): LuaString {
            val zip: ZipFile = ZipFile(LuaApplication.instance!!.getPackageCodePath())
            val entey = zip.getEntry(filepath)
            val `is` = zip.getInputStream(entey)
            return LuaString(readAll(`is`))
        }
    }
}
