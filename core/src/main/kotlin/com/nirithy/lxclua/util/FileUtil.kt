package com.nirithy.lxclua.util

import android.content.Context
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStream
import java.util.function.Consumer
import java.util.zip.ZipInputStream

object FileUtil {
    fun createDirectory(dirPath: String): Boolean {
        val directory = File(dirPath)
        if (!directory.exists()) {
            return directory.mkdirs()
        } else {
            return false
        }
    }

    @Throws(IOException::class)
    fun write(filePath: String, content: String?) {
        val file = File(filePath)
        if (!file.exists()) {
            file.createNewFile()
        }
        val writer = BufferedWriter(FileWriter(file))
        writer.write(content)
        writer.close()
    }

    @Throws(IOException::class)
    fun read(filePath: String): String {
        val file = File(filePath)
        val reader = BufferedReader(FileReader(file))
        val content = StringBuilder()
        var line = reader.readLine()
        while (line != null) {
            content.append(line)
            line = reader.readLine()
            if (line != null) {
                content.append("\n")
            }
        }
        reader.close()
        return content.toString()
    }

    fun traverseDirectory(folderPath: String, callback: Consumer<String?>) {
        val folder = File(folderPath)
        if (folder.exists() && folder.isDirectory()) {
            val files = folder.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory()) {
                        callback.accept(file.getPath())
                    }
                }
            }
        }
    }

    fun isExist(path: String): Boolean {
        return File(path).exists()
    }

    @Throws(IOException::class)
    fun unAssetsZip(context: Context, zipFilePath: String, targetDirectoryPath: String?) {
        val inputStream = context.getAssets().open(zipFilePath)
        val zipInputStream = ZipInputStream(inputStream)

        try {
            while (true) {
                val entry = zipInputStream.getNextEntry()
                if (entry == null) {
                    break
                }
                val file = File(targetDirectoryPath, entry.getName())

                if (entry.isDirectory()) {
                    file.mkdirs()
                } else {
                    val parent = file.getParentFile()
                    if (parent != null) {
                        parent.mkdirs()
                    }

                    val buffer = ByteArray(1024)

                    val outputStream: OutputStream = FileOutputStream(file)
                    val bufferedOutputStream = BufferedOutputStream(outputStream)

                    var count: Int
                    while ((zipInputStream.read(buffer, 0, 1024).also { count = it }) != -1) {
                        bufferedOutputStream.write(buffer, 0, count)
                    }

                    bufferedOutputStream.flush()
                    bufferedOutputStream.close()
                }
            }
        } finally {
            if (inputStream != null) {
                inputStream.close()
            }
            if (zipInputStream != null) {
                zipInputStream.close()
            }
        }
    }

    @Throws(IOException::class)
    fun replaceFileString(path: String, str1: String, str2: String) {
        var text = read(path)
        text = text.replace(str1, str2)
        write(path, text)
    }

    @Throws(IOException::class)
    fun copyFile(sourceFile: String, destFile: String) {
        val file = File(sourceFile)
        val dest = File(destFile)
        file.renameTo(dest)
    }

    fun deleteFolder(folderPath: String): Boolean {
        val folder = File(folderPath)
        if (folder.isDirectory()) {
            val files = folder.listFiles()
            if (files != null) {
                for (file in files) {
                    deleteFolder(file.getAbsolutePath())
                }
            }
        }
        return folder.delete()
    }
}