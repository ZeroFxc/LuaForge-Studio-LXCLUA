package com.nirithy.lxclua.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.logging.Logger
import java.util.zip.Adler32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtil {
    private val logger: Logger = Logger.getLogger(ZipUtil::class.java.getName())

    //	private static final int BUFFER = 1024 * 10;
    private val BUFFER = ByteArray(4096)

    /**
     * 将指定目录压缩到和该目录同名的zip文件，自定义压缩路径
     *
     * @param sourceFilePath 目标文件路径
     * @param zipFilePath    指定zip文件路径
     * @return
     */
    fun zip(sourceFilePath: String, zipFilePath: String?): Boolean {
        var result = false
        val source = File(sourceFilePath)
        if (!source.exists()) {
            logger.info(sourceFilePath + " doesn't exist.")
            return result
        }
        if (!source.isDirectory()) {
            logger.info(sourceFilePath + " is not a directory.")
            return result
        }
        val zipFile = File(zipFilePath + "/" + source.getName() + ".zip")
        if (zipFile.exists()) {
            logger.info(zipFile.getName() + " is already exist.")
            return result
        } else {
            if (!zipFile.getParentFile().exists()) {
                if (!zipFile.getParentFile().mkdirs()) {
                    logger.info("cann't create file " + zipFile.getName())
                    return result
                }
            }
        }
        logger.info("creating zip file...")
        var dest: FileOutputStream? = null
        var out: ZipOutputStream? = null
        try {
            dest = FileOutputStream(zipFile)
            val checksum = CheckedOutputStream(dest, Adler32())
            out = ZipOutputStream(BufferedOutputStream(checksum))
            out.setMethod(ZipOutputStream.DEFLATED)
            compress(source, out, source.getName())
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
        if (result) {
            logger.info("done.")
        } else {
            logger.info("fail.")
        }
        return result
    }

    private fun compress(file: File, out: ZipOutputStream, mainFileName: String) {
        if (file.isFile()) {
            var fi: FileInputStream? = null
            var origin: BufferedInputStream? = null
            try {
                fi = FileInputStream(file)
                origin = BufferedInputStream(fi, BUFFER.size)
                val index = file.getAbsolutePath().indexOf(mainFileName)
                val entryName = file.getAbsolutePath().substring(index)
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
                    compress(f, out, mainFileName)
                }
            }
        }
    }

    fun unzip(zipPath: String, destPath: String): Boolean {
        return unzip(File(zipPath), destPath)
    }

    /**
     * 将zip文件解压到指定的目录，该zip文件必须是使用该类的zip方法压缩的文件
     *
     * @param zipFile
     * @param destPath
     * @return
     */
    fun unzip(zipFile: File, destPath: String): Boolean {
        var result = false
        if (!zipFile.exists()) {
            logger.info(zipFile.getName() + " doesn't exist.")
            return result
        }
        val target = File(destPath)
        if (!target.exists()) {
            if (!target.mkdirs()) {
                logger.info("cann't create file " + target.getName())
                return result
            }
        }
        /*String mainFileName=zipFile.getName().replace(".zip", "");
        File targetFile=new File(destPath + "/" + mainFileName);
        if (targetFile.exists())
		{
            logger.info(targetFile.getName() + " already exist.");
            return result;
        }*/
        var zis: ZipInputStream? = null
        logger.info("start unzip file ...")
        try {
            val fis = FileInputStream(zipFile)
            val checksum = CheckedInputStream(fis, Adler32())
            zis = ZipInputStream(BufferedInputStream(checksum))
            var entry: ZipEntry?
            while ((zis.getNextEntry().also { entry = it }) != null) {
                var count: Int
                //                byte data[] = new byte[BUFFER];
                val entryName = entry!!.getName()
                // 跳过无效条目（name 为 null 会导致创建 null 文件夹）
                if (entryName == null) continue

                val newEntryName = destPath + "/" + entryName
                println(newEntryName)
                val temp = File(newEntryName).getParentFile()
                if (!temp!!.exists()) {
                    if (!temp.mkdirs()) {
                        throw RuntimeException("create file " + temp.getName() + " fail")
                    }
                }
                val fos = FileOutputStream(newEntryName)
                val dest = BufferedOutputStream(fos, BUFFER.size)
                while ((zis.read(BUFFER, 0, BUFFER.size).also { count = it }) != -1) {
                    dest.write(BUFFER, 0, count)
                }
                dest.flush()
                dest.close()
            }
            result = true
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (zis != null) {
                try {
                    zis.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        if (result) {
            logger.info("done.")
        } else {
            logger.info("fail.")
        }
        return result
    }


    // 4MB buffer
    /**
     * copy input to output stream - available in several StreamUtils or Streams classes
     */
    @Throws(IOException::class)
    fun copy(input: InputStream, output: OutputStream) {
        var bytesRead: Int
        while ((input.read(BUFFER).also { bytesRead = it }) != -1) {
            output.write(BUFFER, 0, bytesRead)
        }
    }

    @Throws(Exception::class)
    fun append(zipFilePath: String?, appendFilePath: String) {
        val war = ZipFile(zipFilePath)
        val append = ZipOutputStream(FileOutputStream(zipFilePath))

        val entries = war.entries()
        while (entries.hasMoreElements()) {
            val e: ZipEntry = entries.nextElement()
            println("copy: " + e.getName())
            append.putNextEntry(e)
            if (!e.isDirectory()) {
                copy(war.getInputStream(e), append)
            }
            append.closeEntry()
        }
        // now append some extra content
        val e = ZipEntry(appendFilePath)
        println("append: " + e.getName())
        append.putNextEntry(e)
        copy(FileInputStream(File(appendFilePath)), append)
        append.closeEntry()
        // close
        war.close()
        append.close()
    }
}
