package com.nirithy.lxclua

import android.util.Log
import android.webkit.MimeTypeMap
import com.nirithy.lxclua.util.AsyncTaskX
import com.luajava.LuaException
import com.luajava.LuaObject
import com.luajava.LuaString
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object Http {
    /* static {
        setUserAgent("Mozilla/5.0 (Linux; Android 8.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.111 Mobile Safari/537.36 EdgA/41.0.0.1722");
    }*/
    var header: HashMap<String?, String?>? = null

    init {
        try {
            var sslcontext: SSLContext? = null
            sslcontext = SSLContext.getInstance("SSL")
            sslcontext.init(null, arrayOf<TrustManager>(object : X509TrustManager {
                @Throws(CertificateException::class)
                override fun checkClientTrusted(
                    chain: Array<X509Certificate?>?,
                    authType: String?
                ) {
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(
                    chain: Array<X509Certificate?>?,
                    authType: String?
                ) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate?> {
                    return arrayOfNulls<X509Certificate>(0)
                }
            }), SecureRandom())

            val ignoreHostnameVerifier: HostnameVerifier = object : HostnameVerifier {
                override fun verify(s: String?, sslsession: SSLSession?): Boolean {
                    //这块也不用有啥逻辑，确认结果是true就行
                    return true
                }
            }
            HttpsURLConnection.setDefaultHostnameVerifier(ignoreHostnameVerifier)
            HttpsURLConnection.setDefaultSSLSocketFactory(sslcontext.getSocketFactory())
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: KeyManagementException) {
            e.printStackTrace()
        }
    }

    fun setUserAgent(userAgent: String?) {
        if (header == null) header = HashMap<String?, String?>()
        header!!.put("User-Agent", userAgent)
    }

    fun setReferer(referer: String?) {
        if (header == null) header = HashMap<String?, String?>()
        header!!.put("Referer", referer)
    }

    fun setCookie(cookie: String?) {
        if (header == null) header = HashMap<String?, String?>()
        header!!.put("Cookie", cookie)
    }

    fun get(url: String?, callback: LuaObject): HttpTask {
        val task = HttpTask(url, "GET", null, null, null, callback)
        task.execute()
        return task
    }

    fun get(url: String?, header: HashMap<String?, String?>?, callback: LuaObject): HttpTask {
        val task = HttpTask(url, "GET", null, null, header, callback)
        task.execute()
        return task
    }

    fun get(
        url: String?,
        cookie: String,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task =
            if (cookie.matches("[\\w\\-\\.:]+".toRegex()) && Charset.isSupported(cookie)) HttpTask(
                url,
                "GET",
                null,
                cookie,
                header,
                callback
            ) else HttpTask(url, "GET", cookie, null, header, callback)
        task.execute()
        return task
    }

    fun get(url: String?, cookie: String, callback: LuaObject): HttpTask {
        val task =
            if (cookie.matches("[\\w\\-\\.:]+".toRegex()) && Charset.isSupported(cookie)) HttpTask(
                url,
                "GET",
                null,
                cookie,
                null,
                callback
            ) else HttpTask(url, "GET", cookie, null, null, callback)
        task.execute()
        return task
    }

    fun get(url: String?, cookie: String?, charset: String?, callback: LuaObject): HttpTask {
        val task = HttpTask(url, "GET", cookie, charset, null, callback)
        task.execute()
        return task
    }

    fun get(
        url: String?,
        cookie: String?,
        charset: String?,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task = HttpTask(url, "GET", cookie, charset, header, callback)
        task.execute()
        return task
    }

    fun download(url: String?, data: String?, callback: LuaObject): HttpTask {
        val task = HttpTask(url, "GET", null, null, null, callback)
        task.execute(data)
        return task
    }

    fun download(
        url: String?,
        data: String?,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task = HttpTask(url, "GET", null, null, header, callback)
        task.execute(data)
        return task
    }

    fun download(url: String?, data: String?, cookie: String?, callback: LuaObject): HttpTask {
        val task = HttpTask(url, "GET", cookie, null, null, callback)
        task.execute(data)
        return task
    }

    fun download(
        url: String?,
        data: String?,
        cookie: String?,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task = HttpTask(url, "GET", cookie, null, header, callback)
        task.execute(data)
        return task
    }


    fun delete(url: String?, callback: LuaObject): HttpTask {
        val task = HttpTask(url, "DELETE", null, null, null, callback)
        task.execute()
        return task
    }

    fun delete(url: String?, header: HashMap<String?, String?>?, callback: LuaObject): HttpTask {
        val task = HttpTask(url, "DELETE", null, null, header, callback)
        task.execute()
        return task
    }

    fun delete(
        url: String?,
        cookie: String,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task =
            if (cookie.matches("[\\w\\-\\.:]+".toRegex()) && Charset.isSupported(cookie)) HttpTask(
                url,
                "DELETE",
                null,
                cookie,
                header,
                callback
            ) else HttpTask(url, "DELETE", cookie, null, header, callback)
        task.execute()
        return task
    }

    fun delete(url: String?, cookie: String, callback: LuaObject): HttpTask {
        val task =
            if (cookie.matches("[\\w\\-\\.:]+".toRegex()) && Charset.isSupported(cookie)) HttpTask(
                url,
                "DELETE",
                null,
                cookie,
                null,
                callback
            ) else HttpTask(url, "DELETE", cookie, null, null, callback)
        task.execute()
        return task
    }

    fun delete(url: String?, cookie: String?, charset: String?, callback: LuaObject): HttpTask {
        val task = HttpTask(url, "DELETE", cookie, charset, null, callback)
        task.execute()
        return task
    }

    fun delete(
        url: String?,
        cookie: String?,
        charset: String?,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task = HttpTask(url, "DELETE", cookie, charset, header, callback)
        task.execute()
        return task
    }


    fun post(url: String?, data: String?, callback: LuaObject): HttpTask {
        val task = HttpTask(url, "POST", null, null, null, callback)
        task.execute(data)
        return task
    }

    fun post(
        url: String?,
        data: String?,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task = HttpTask(url, "POST", null, null, header, callback)
        task.execute(data)
        return task
    }

    fun post(url: String?, data: String?, cookie: String, callback: LuaObject): HttpTask {
        val task =
            if (cookie.matches("[\\w\\-.:]+".toRegex()) && Charset.isSupported(cookie)) HttpTask(
                url,
                "POST",
                null,
                cookie,
                null,
                callback
            ) else HttpTask(url, "POST", cookie, null, null, callback)
        task.execute(data)
        return task
    }

    fun post(
        url: String?,
        data: String?,
        cookie: String,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task =
            if (cookie.matches("[\\w\\-.:]+".toRegex()) && Charset.isSupported(cookie)) HttpTask(
                url,
                "POST",
                null,
                cookie,
                header,
                callback
            ) else HttpTask(url, "POST", cookie, null, header, callback)
        task.execute(data)
        return task
    }

    fun post(
        url: String?,
        data: String?,
        cookie: String?,
        charset: String?,
        callback: LuaObject
    ): HttpTask {
        val task = HttpTask(url, "POST", cookie, charset, null, callback)
        task.execute(data)
        return task
    }

    fun post(
        url: String?,
        data: String?,
        cookie: String?,
        charset: String?,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task = HttpTask(url, "POST", cookie, charset, header, callback)
        task.execute(data)
        return task
    }

    fun post(url: String?, data: HashMap<String?, String?>, callback: LuaObject): HttpTask {
        return post(url, formatMap(data), callback)
    }

    fun post(
        url: String?,
        data: HashMap<String?, String?>,
        cookie: String,
        callback: LuaObject
    ): HttpTask {
        return post(url, formatMap(data), cookie, callback)
    }

    fun post(
        url: String?,
        data: HashMap<String?, String?>,
        cookie: String,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        return post(url, formatMap(data), cookie, header, callback)
    }

    fun post(
        url: String?,
        data: HashMap<String?, String?>,
        cookie: String?,
        charset: String?,
        callback: LuaObject
    ): HttpTask {
        return post(url, formatMap(data), cookie, charset, callback)
    }

    fun post(
        url: String?,
        data: HashMap<String?, String?>,
        cookie: String?,
        charset: String?,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        return post(url, formatMap(data), cookie, charset, header, callback)
    }

    private fun formatMap(data: HashMap<String?, String?>): String {
        val buf = StringBuilder()
        for (entry in data.entries) {
            buf.append(entry.key).append("=").append(entry.value).append("&")
        }
        if (!data.isEmpty()) buf.deleteCharAt(buf.length - 1)
        return buf.toString()
    }


    private const val boundary = "----q1w2e3r4t5y6u7i8o9p0a1s2d3f4g5h6j7k8l9z0x1c2v3b4n5m6"

    fun post(
        url: String?,
        data: HashMap<String?, String?>,
        file: HashMap<String?, String?>,
        callback: LuaObject
    ): HttpTask {
        return post(url, data, file, null, null, null, callback)
    }

    fun post(
        url: String?,
        data: HashMap<String?, String?>,
        file: HashMap<String?, String?>,
        cookie: String,
        callback: LuaObject
    ): HttpTask {
        return post(url, data, file, cookie, HashMap<String?, String?>(), callback)
    }

    fun post(
        url: String?,
        data: HashMap<String?, String?>,
        file: HashMap<String?, String?>,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        return Http.post(url, data, file, "", header, callback)
    }

    fun post(
        url: String?,
        data: HashMap<String?, String?>,
        file: HashMap<String?, String?>,
        cookie: String,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        return if (cookie.matches("[\\w\\-.:]+".toRegex()) && Charset.isSupported(cookie)) post(
            url,
            data,
            file,
            cookie,
            null,
            header,
            callback
        ) else post(url, data, file, null, cookie, header, callback)
    }

    fun post(
        url: String?,
        data: HashMap<String?, String?>,
        file: HashMap<String?, String?>,
        cookie: String?,
        charset: String?,
        callback: LuaObject
    ): HttpTask {
        return post(url, data, file, cookie, charset, null, callback)
    }

    fun post(
        url: String?,
        data: HashMap<String?, String?>,
        file: HashMap<String?, String?>,
        cookie: String?,
        charset: String?,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        var header = header
        if (header == null) header = HashMap<String?, String?>()
        header.put("Content-Type", "multipart/form-data;boundary=" + boundary)
        val task = HttpTask(url, "POST", cookie, charset, header, callback)
        task.execute(*arrayOf<Any>(formatMultiDate(data, file, charset)))
        return task
    }

    private fun getType(file: String): String {
        val lastDot = file.lastIndexOf(46.toChar())
        if (lastDot >= 0) {
            val extension = file.substring(lastDot + 1)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (mime != null) {
                return mime
            }
        }
        return "application/octet-stream"
    }

    private fun formatMultiDate(
        data: HashMap<String?, String?>,
        file: HashMap<String?, String?>,
        charset: String?
    ): ByteArray {
        var charset = charset
        if (charset == null) charset = "UTF-8"
        val buff = ByteArrayOutputStream()
        for (entry in data.entries) {
            try {
                buff.write(
                    String.format(
                        "--%s\r\nContent-Disposition:form-data;name=\"%s\"\r\n\r\n%s\r\n",
                        boundary,
                        entry.key,
                        entry.value
                    ).toByteArray(
                        charset(charset)
                    )
                )
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        for (entry in file.entries) {
            try {
                buff.write(
                    String.format(
                        "--%s\r\nContent-Disposition:form-data;name=\"%s\";filename=\"%s\"\r\nContent-Type:%s\r\n\r\n",
                        boundary,
                        entry.key,
                        entry.value,
                        Http.getType(entry.value!!)
                    ).toByteArray(
                        charset(charset)
                    )
                )
                buff.write(LuaUtil.Companion.readAll(FileInputStream(entry.value)))
                buff.write("\r\n".toByteArray(charset(charset)))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        try {
            buff.write(String.format("--%s--\r\n", boundary).toByteArray(charset(charset)))
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return buff.toByteArray()
    }


    fun put(url: String?, data: String?, callback: LuaObject): HttpTask {
        val task = HttpTask(url, "PUT", null, null, null, callback)
        task.execute(data)
        return task
    }

    fun put(
        url: String?,
        data: String?,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task = HttpTask(url, "PUT", null, null, header, callback)
        task.execute(data)
        return task
    }

    fun put(url: String?, data: String?, cookie: String, callback: LuaObject): HttpTask {
        val task =
            if (cookie.matches("[\\w\\-\\.:]+".toRegex()) && Charset.isSupported(cookie)) HttpTask(
                url,
                "PUT",
                null,
                cookie,
                null,
                callback
            ) else HttpTask(url, "PUT", cookie, null, null, callback)
        task.execute(data)
        return task
    }

    fun put(
        url: String?,
        data: String?,
        cookie: String,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task =
            if (cookie.matches("[\\w\\-\\.:]+".toRegex()) && Charset.isSupported(cookie)) HttpTask(
                url,
                "PUT",
                null,
                cookie,
                header,
                callback
            ) else HttpTask(url, "PUT", cookie, null, header, callback)
        task.execute(data)
        return task
    }

    fun put(
        url: String?,
        data: String?,
        cookie: String?,
        charset: String?,
        callback: LuaObject
    ): HttpTask {
        val task = HttpTask(url, "PUT", cookie, charset, null, callback)
        task.execute(data)
        return task
    }

    fun put(
        url: String?,
        data: String?,
        cookie: String?,
        charset: String?,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ): HttpTask {
        val task = HttpTask(url, "PUT", cookie, charset, header, callback)
        task.execute(data)
        return task
    }


    class HttpTask(
        private val mUrl: String?,
        private val mMethod: String?,
        private val mCookie: String?,
        private var mCharset: String?,
        header: HashMap<String?, String?>?,
        callback: LuaObject
    ) : AsyncTaskX<Any?, Any?, Any?>() {
        private val mCallback: LuaObject

        private var mData: ByteArray = ByteArray(0)

        private var mOutCharset: String?

        private val mHeader: HashMap<String?, String?>?


        init {
            mOutCharset = mCharset
            mHeader = header
            mCallback = callback
        }


        override fun doInBackground(vararg params: Any?): Any? {
            // TODO: Implement this method
            try {
                val url = URL(mUrl)

                val conn = url.openConnection() as HttpURLConnection
                conn.setConnectTimeout(30000)
                HttpURLConnection.setFollowRedirects(true)
                conn.setDoInput(true)
                conn.setRequestProperty("Accept-Language", "zh-cn,zh;q=0.5")

                if (mCharset == null) mCharset = "UTF-8"
                conn.setRequestProperty("Accept-Charset", mCharset)


                if (header != null) {
                    val entries: MutableSet<MutableMap.MutableEntry<String?, String?>> =
                        header!!.entries
                    for (entry in entries) {
                        conn.setRequestProperty(entry.key, entry.value)
                    }
                }

                if (mHeader != null) {
                    val entries = mHeader.entries
                    for (entry in entries) {
                        conn.setRequestProperty(entry.key, entry.value)
                    }
                }

                if (mCookie != null) conn.setRequestProperty("Cookie", mCookie)

                if (mMethod != null) conn.setRequestMethod(mMethod)

                if ("GET" != mMethod && params.size != 0) {
                    mData = formatData(params)

                    conn.setDoOutput(true)
                    conn.setRequestProperty("Content-length", "" + mData.size)
                }

                conn.connect()

                //download
                if ("GET" == mMethod && params.size != 0) {
                    val f = File(params[0] as String?)
                    if (!f.getParentFile().exists())
                        f.getParentFile().mkdirs()
                    val os = FileOutputStream(f)
                    val `is` = conn.getInputStream()
                    LuaUtil.Companion.copyFile(`is`, os)
                    return arrayOf<Any?>(conn.getResponseCode(), params[0], conn.getHeaderFields())
                }

                //post upload
                if (params.size != 0) {
                    val os = conn.getOutputStream()
                    os.write(mData)
                }

                val code = conn.getResponseCode()
                val hs = conn.getHeaderFields()
                val encoding = conn.getContentEncoding()
                val cs: MutableList<String?>? = hs.get("Set-Cookie")
                val cok = StringBuilder()
                if (cs != null) {
                    for (s in cs) {
                        cok.append(s).append(";")
                    }
                }

                val ct = hs.get("Content-Type")
                if (ct != null) {
                    for (s in ct) {
                        var idx = s.indexOf("charset")
                        if (idx != -1) {
                            idx = s.indexOf("=", idx)
                            if (idx != -1) {
                                var idx2 = s.indexOf(";", idx)
                                if (idx2 == -1) idx2 = s.length
                                mCharset = s.substring(idx + 1, idx2)
                                mOutCharset = mCharset
                                break
                            }
                        }
                    }
                }

                if (mOutCharset == null) {
                    try {
                        val `is` = conn.getInputStream()
                        val reader: ByteArray = LuaUtil.Companion.readAll(`is`)
                        `is`.close()
                        val buf = LuaString(reader)
                        return arrayOf<Any>(code, buf, cok.toString(), hs)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                val buf = StringBuilder()
                try {
                    val `is` = conn.getInputStream()
                    val reader = BufferedReader(InputStreamReader(`is`, mCharset))
                    var line = reader.readLine()
                    if (line != null) buf.append(line)
                    while ((reader.readLine()
                            .also { line = it }) != null && !isCancelled
                    ) buf.append('\n').append(line)
                    `is`.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val `is` = conn.getErrorStream()
                if (`is` != null) {
                    val reader = BufferedReader(InputStreamReader(`is`, mCharset))
                    var line = reader.readLine()
                    if (line != null) buf.append(line)
                    while ((reader.readLine()
                            .also { line = it }) != null && !isCancelled
                    ) buf.append('\n').append(line)
                    `is`.close()
                }
                return arrayOf<Any>(code, String(buf), cok.toString(), hs)
            } catch (e: Exception) {
                e.printStackTrace()
                return arrayOf<Any?>(-1, e.message)
            }
        }

        @Throws(IOException::class)
        private fun formatData(p1: Array<out Any?>): ByteArray {
            // TODO: Implement this method
            var bs: ByteArray? = null
            if (p1.size == 1) {
                val obj = p1[0]
                if (obj is String) bs = obj.toByteArray(charset(mCharset!!))
                else if (obj!!.javaClass.getComponentType() == Byte::class.javaPrimitiveType) bs =
                    obj as ByteArray
                else if (obj is File) bs = LuaUtil.Companion.readAll(FileInputStream(obj))
                else if (obj is MutableMap<*, *>) bs =
                    formatData(obj as MutableMap<String?, String?>)
            }
            return bs!!
        }

        @Throws(UnsupportedEncodingException::class)
        private fun formatData(obj: MutableMap<String?, String?>): ByteArray {
            // TODO: Implement this method
            val buf = StringBuilder()
            val entries = obj.entries
            for (entry in entries) {
                buf.append(entry.key).append("=").append(entry.value).append("&")
            }
            return buf.toString().toByteArray(charset(mCharset!!))
        }


        fun cancel(): Boolean {
            // TODO: Implement this method
            return super.cancel(true)
        }


        override fun onPostExecute(result: Any?) {
            // TODO: Implement this method
            if (isCancelled) return
            try {
                val arr = result as? Array<Any?> ?: return
                mCallback.call(*arr)
            } catch (e: LuaException) {
                try {
                    mCallback.getLuaState().getLuaObject("print").call(e.message)
                } catch (e2: LuaException) {
                }
                Log.i("lua", e.message!!)
            }
        }
    }
}
