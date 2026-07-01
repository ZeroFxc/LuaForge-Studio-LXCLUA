package com.nirithy.lxclua

import android.R
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.webkit.ClientCertRequest
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import android.webkit.DownloadListener
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.luajava.LuaException
import com.luajava.LuaFunction
import java.io.File
import java.util.Arrays

class LuaWebView @SuppressLint(
    "AddJavascriptInterface",
    "SetJavaScriptEnabled"
) constructor(context: LuaActivity) : WebView(context), LuaGcable {
    private var mDownloadBroadcastReceiver: DownloadBroadcastReceiver? = null
    private val mDownload = HashMap<Long?, Array<String?>?>()
    private var mOnDownloadCompleteListener: OnDownloadCompleteListener? = null
    private val mContext: LuaActivity
    private var mProgressbar: ProgressBar
    private val dm: DisplayMetrics?
    private var open_dlg: Dialog? = null
    private var open_list: ListView? = null
    private var mUploadMessage: ValueCallback<Uri?>? = null
    private var mDir = "/"
    private var mAdsFilter: LuaFunction<Boolean?>? = null
    private var mGc = false
    var source: String? = null
        private set

    internal inner class InJavaScriptLocalObj {
        @JavascriptInterface
        fun get(html: String?) {
            Log.i("luaj", "get: " + html)
            this@LuaWebView.source = html
        }
    }

    fun setCookie(url: String?, cookie: String) {
        CookieSyncManager.createInstance(mContext)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        val cs: Array<String?> =
            cookie.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (c in cs) {
            cookieManager.setCookie(url, c)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.flush()
        }
        CookieSyncManager.getInstance().sync()
    }

    fun getCookie(url: String?): String? {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        return cookieManager.getCookie(url)
    }

    var cookie: String?
        get() {
            val cookieManager =
                CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            return cookieManager.getCookie(getUrl())
        }
        set(cookie) {
            val url = getUrl()
            CookieSyncManager.createInstance(mContext)
            val cookieManager =
                CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            val cs: Array<String?> =
                cookie!!.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (c in cs) {
                cookieManager.setCookie(url, c)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.flush()
            }
            CookieSyncManager.getInstance().sync()
        }

    override fun gc() {
        destroy()
        mGc = true
    }

    override val isGc get() = mGc

    fun setProgressBarEnabled(visibility: Boolean) {
        if (visibility) mProgressbar.setVisibility(VISIBLE)
        else mProgressbar.setVisibility(GONE)
    }

    fun setProgressBar(pb: ProgressBar) {
        mProgressbar = pb
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        val lp = mProgressbar.getLayoutParams() as LayoutParams
        lp.x = l
        lp.y = t
        mProgressbar.setLayoutParams(lp)
        super.onScrollChanged(l, t, oldl, oldt)
    }

    override fun setDownloadListener(listener: DownloadListener?) {
        super.setDownloadListener(listener)
    }

    fun setOnDownloadStartListener(listener: OnDownloadStartListener) {
        setDownloadListener(object : DownloadListener {
            override fun onDownloadStart(
                p1: String?,
                p2: String?,
                p3: String?,
                p4: String?,
                p5: Long
            ) {
                listener.onDownloadStart(p1, p2, p3, p4, p5)
            }
        })
    }

    fun setOnDownloadCompleteListener(listener: OnDownloadCompleteListener?) {
        mOnDownloadCompleteListener = listener
    }

    override fun destroy() {
        // TODO: Implement this method
        if (mDownloadBroadcastReceiver != null) {
            mContext.unregisterReceiver(mDownloadBroadcastReceiver)
        }
        super.destroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && canGoBack()) {
            goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun setOnKeyListener(l: OnKeyListener?) {
        // TODO: Implement this method
        super.setOnKeyListener(l)
    }

    @SuppressLint("AddJavascriptInterface")
    fun addJSInterface(`object`: JsInterface, name: String) {
        // TODO: Implement this method
        super.addJavascriptInterface(JsObject(`object`), name)
    }

    @SuppressLint("AddJavascriptInterface")
    fun addJsInterface(`object`: JsInterface, name: String) {
        // TODO: Implement this method
        super.addJavascriptInterface(JsObject(`object`), name)
    }

    fun setWebViewClient(client: LuaWebViewClient) {
        // TODO: Implement this method
        super.setWebViewClient(SimpleLuaWebViewClient(client))
    }

    fun openFile(dir: String) {
        if (open_dlg == null) {
            open_dlg = Dialog(getContext())
            open_list = ListView(getContext())
            open_list!!.setFastScrollEnabled(true)
            open_list!!.setFastScrollAlwaysVisible(true)
            open_dlg!!.setContentView(open_list!!)

            open_list!!.setOnItemClickListener(object : AdapterView.OnItemClickListener {
                override fun onItemClick(p1: AdapterView<*>?, p2: View, p3: Int, p4: Long) {
                    val t = (p2 as TextView).getText().toString()
                    if (t == "../") {
                        mDir = File(mDir).getParent() + "/"
                        openFile(mDir)
                        return
                    }
                    val fn = mDir + t
                    val f = File(fn)
                    if (f.isDirectory()) {
                        mDir = fn
                        openFile(mDir)
                        return
                    }
                    mUploadMessage!!.onReceiveValue(Uri.parse(fn))
                }
            })
        }

        val d = File(dir)
        val ns = ArrayList<String?>()
        ns.add("../")


        val fs = d.list()
        if (fs != null) {
            Arrays.sort(fs)
            for (k in fs) {
                if (File(mDir + k).isDirectory()) ns.add(k + "/")
            }

            for (k in fs) {
                if (File(mDir + k).isFile()) ns.add(k)
            }
        }

        val adapter = ArrayAdapter<String?>(getContext(), R.layout.simple_list_item_1, ns)
        open_list!!.setAdapter(adapter)
        open_dlg!!.setTitle(mDir)
        open_dlg!!.show()
    }


    interface OnDownloadCompleteListener {
        fun onDownloadComplete(fileName: String?, mimetype: String?)
    }


    interface OnDownloadStartListener {
        fun onDownloadStart(
            url: String?,
            userAgent: String?,
            contentDisposition: String?,
            mimetype: String?,
            contentLength: Long
        )
    }

    interface JsInterface {
        @JavascriptInterface
        fun execute(arg: String?): String?
    }

    interface LuaWebViewClient {
        fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean

        fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?)

        fun onPageFinished(view: WebView?, url: String?)

        fun onLoadResource(view: WebView?, url: String?)

        fun shouldInterceptRequest(
            view: WebView?,
            url: String?
        ): WebResourceResponse?

        @Deprecated("")
        fun onTooManyRedirects(
            view: WebView?, cancelMsg: Message?,
            continueMsg: Message?
        )

        fun onReceivedError(
            view: WebView?, errorCode: Int,
            description: String?, failingUrl: String?
        )


        fun onFormResubmission(
            view: WebView?, dontResend: Message?,
            resend: Message?
        )


        fun doUpdateVisitedHistory(
            view: WebView?, url: String?,
            isReload: Boolean
        )


        fun onReceivedSslError(
            view: WebView?, handler: SslErrorHandler?,
            error: SslError?
        )


        fun onProceededAfterSslError(view: WebView?, error: SslError?)


        fun onReceivedClientCertRequest(
            view: WebView?,
            handler: ClientCertRequest?, host_and_port: String?
        )


        fun onReceivedHttpAuthRequest(
            view: WebView?,
            handler: HttpAuthHandler?, host: String?, realm: String?
        )


        fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean


        fun onUnhandledKeyEvent(view: WebView?, event: KeyEvent?)


        fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float)


        fun onReceivedLoginRequest(
            view: WebView?, realm: String?,
            account: String?, args: String?
        )

        companion object {
            /**
             * Generic error
             */
            val ERROR_UNKNOWN: Int = -1

            /**
             * Server or proxy hostname lookup failed
             */
            val ERROR_HOST_LOOKUP: Int = -2

            /**
             * Unsupported authentication scheme (not basic or digest)
             */
            val ERROR_UNSUPPORTED_AUTH_SCHEME: Int = -3

            /**
             * User authentication failed on server
             */
            val ERROR_AUTHENTICATION: Int = -4

            /**
             * User authentication failed on proxy
             */
            val ERROR_PROXY_AUTHENTICATION: Int = -5

            /**
             * Failed to connect to the server
             */
            val ERROR_CONNECT: Int = -6


            // These ints must match up to the hidden values in EventHandler.
            /**
             * Failed to read or write to the server
             */
            val ERROR_IO: Int = -7

            /**
             * Connection timed out
             */
            val ERROR_TIMEOUT: Int = -8

            /**
             * Too many redirects
             */
            val ERROR_REDIRECT_LOOP: Int = -9

            /**
             * Unsupported URI scheme
             */
            val ERROR_UNSUPPORTED_SCHEME: Int = -10

            /**
             * Failed to perform SSL handshake
             */
            val ERROR_FAILED_SSL_HANDSHAKE: Int = -11

            /**
             * Malformed URL
             */
            val ERROR_BAD_URL: Int = -12

            /**
             * Generic file error
             */
            val ERROR_FILE: Int = -13

            /**
             * File not found
             */
            val ERROR_FILE_NOT_FOUND: Int = -14

            /**
             * Too many requests during this load
             */
            val ERROR_TOO_MANY_REQUESTS: Int = -15
        }
    }

    private inner class DownloadBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(p1: Context?, p2: Intent) {
            // TODO: Implement this method
            //id=p2.getLongExtra("flg", 0);
            //int id=p2.getFlags();
            val id = p2.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
            val bundle = p2.getExtras()
            //bundle.g
            if (mDownload.containsKey(id)) {
                if (mOnDownloadCompleteListener != null) {
                    val data = mDownload.get(id)
                    mOnDownloadCompleteListener!!.onDownloadComplete(data!![0], data[1])
                } else {
                }
            }
        }
    }

    private inner class Download : DownloadListener {
        var file_input_field: EditText? = null
        private var mUrl: String? = null
        private var mUserAgent: String? = null
        private var mContentDisposition: String? = null
        private var mMimetype: String? = null
        private var mContentLength: Long = 0
        private var mFilename: String? = null

        @SuppressLint("DefaultLocale")
        override fun onDownloadStart(
            url: String?,
            userAgent: String?,
            contentDisposition: String?,
            mimetype: String?,
            contentLength: Long
        ) {
            // TODO: Implement this method
            mUrl = url
            mUserAgent = userAgent
            mContentDisposition = contentDisposition
            mMimetype = mimetype
            mContentLength = contentLength
            val uri = Uri.parse(mUrl)
            mFilename = uri.getLastPathSegment()
            if (contentDisposition != null) {
                val p = "filename=\""
                var i = contentDisposition.indexOf(p)
                if (i != -1) {
                    i += p.length
                    val n = contentDisposition.indexOf('"', i)
                    if (n > i) mFilename = contentDisposition.substring(i, n)
                }
            }
            file_input_field = EditText(mContext)
            //file_input_field.setTextColor(0xff000000);
            file_input_field!!.setText(mFilename)
            var size = contentLength.toString() + "B"
            if (contentLength > 1024 * 1024) size =
                String.format("%.2f MB", contentLength.toDouble() / (1024 * 1024))
            else if (contentLength > 1024) size =
                String.format("%.2f KB", contentLength.toDouble() / (1024))

            AlertDialog.Builder(mContext)
                .setTitle(DOWNLOAD)
                .setMessage("Type: " + mimetype + "\nSize: " + size)
                .setView(file_input_field)
                .setPositiveButton(DOWNLOAD, object : DialogInterface.OnClickListener {
                    override fun onClick(p1: DialogInterface?, p2: Int) {
                        // TODO: Implement this method
                        mFilename = file_input_field!!.getText().toString()
                        download(false)
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton("Only Wifi", object : DialogInterface.OnClickListener {
                    override fun onClick(p1: DialogInterface?, p2: Int) {
                        // TODO: Implement this method
                        mFilename = file_input_field!!.getText().toString()
                        download(true)
                    }
                })
                .create()
                .show()
        }

        fun download(isWifi: Boolean): Long {
            if (mDownloadBroadcastReceiver == null) {
                val filter = IntentFilter()
                filter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                mDownloadBroadcastReceiver = DownloadBroadcastReceiver()
                mContext.registerReceiver(mDownloadBroadcastReceiver, filter)
            }

            val downloadManager =
                mContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val uri = Uri.parse(mUrl)
            uri.getLastPathSegment()
            val request = DownloadManager.Request(uri)
            val dir = mContext.resolveLuaExtDir(DOWNLOAD)
            request.setDestinationInExternalPublicDir(
                File(mContext.luaExtDir).getName() + "/" + DOWNLOAD,
                mFilename
            )

            request.setTitle(mFilename)

            request.setDescription(mUrl)

            //request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (isWifi) request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)

            //request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
            val f = File(dir, mFilename)
            if (f.exists()) f.delete()

            request.setMimeType(mMimetype)
            //Environment.getExternalStoragePublicDirectory(dirType)
            val downloadId = downloadManager.enqueue(request)
            mDownload.put(
                downloadId,
                arrayOf<String?>(File(dir, mFilename).absolutePath, mMimetype)
            )
            return downloadId
        }
    }

    internal inner class JsObject(private val mJs: JsInterface) {
        @JavascriptInterface
        fun execute(arg: String?): String? {
            return mJs.execute(arg)
        }
    }

    private inner class LuaJavaScriptInterface(private val mMain: LuaActivity) {
        @JavascriptInterface
        fun callLuaFunction(name: String?): Any? {
            return mMain.runFunc(name!!)
        }

        @JavascriptInterface
        fun callLuaFunction(name: String?, arg: String?): Any? {
            return mMain.runFunc(name!!, arg)
        }

        @JavascriptInterface
        fun doLuaString(name: String?): Any? {
            return mMain.doString(name)
        }
    }

    fun setAdsFilter(filter: LuaFunction<Boolean?>?) {
        mAdsFilter = filter
    }


    private inner class SimpleLuaWebViewClient(private val mLuaWebViewClient: LuaWebViewClient) :
        WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return mLuaWebViewClient.shouldOverrideUrlLoading(view, url)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            mLuaWebViewClient.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            mLuaWebViewClient.onPageFinished(view, url)
        }

        override fun onLoadResource(view: WebView?, url: String?) {
            mLuaWebViewClient.onLoadResource(view, url)
        }

        @Suppress("deprecation")
        override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
            if (mAdsFilter != null) {
                try {
                    if (mAdsFilter!!.call(url) == true) return WebResourceResponse(null, null, null)
                } catch (e: LuaException) {
                    e.printStackTrace()
                }
            }
            return mLuaWebViewClient.shouldInterceptRequest(view, url)
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            return super.shouldInterceptRequest(view, request)
        }

        @Suppress("deprecation")
        @Deprecated("")
        override fun onTooManyRedirects(
            view: WebView?, cancelMsg: Message,
            continueMsg: Message?
        ) {
            cancelMsg.sendToTarget()
        }

        override fun onReceivedError(
            view: WebView?, errorCode: Int,
            description: String?, failingUrl: String?
        ) {
            mLuaWebViewClient.onReceivedError(view, errorCode, description, failingUrl)
        }

        override fun onFormResubmission(
            view: WebView?, dontResend: Message,
            resend: Message?
        ) {
            dontResend.sendToTarget()
        }

        override fun doUpdateVisitedHistory(
            view: WebView?, url: String?,
            isReload: Boolean
        ) {
            mLuaWebViewClient.doUpdateVisitedHistory(view, url, isReload)
        }

        override fun onReceivedSslError(
            view: WebView?, handler: SslErrorHandler?,
            error: SslError?
        ) {
            mLuaWebViewClient.onReceivedSslError(view, handler, error)
        }

        fun onProceededAfterSslError(view: WebView?, error: SslError?) {
            mLuaWebViewClient.onProceededAfterSslError(view, error)
        }

        fun onReceivedClientCertRequest(
            view: WebView?,
            handler: ClientCertRequest?, host_and_port: String?
        ) {
            mLuaWebViewClient.onReceivedClientCertRequest(view, handler, host_and_port)
        }

        override fun onReceivedHttpAuthRequest(
            view: WebView?,
            handler: HttpAuthHandler?, host: String?, realm: String?
        ) {
            mLuaWebViewClient.onReceivedHttpAuthRequest(view, handler, host, realm)
        }

        override fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean {
            return mLuaWebViewClient.shouldOverrideKeyEvent(view, event)
        }

        override fun onUnhandledKeyEvent(view: WebView?, event: KeyEvent?) {
            mLuaWebViewClient.onUnhandledKeyEvent(view, event)
        }

        override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
            mLuaWebViewClient.onScaleChanged(view, oldScale, newScale)
        }

        override fun onReceivedLoginRequest(
            view: WebView?, realm: String?,
            account: String?, args: String?
        ) {
            mLuaWebViewClient.onReceivedLoginRequest(view, realm, account, args)
        }
    }

    internal inner class LuaWebChromeClient : WebChromeClient() {
        var prompt_input_field: EditText = EditText(mContext)

        override fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult
        ): Boolean {
            AlertDialog.Builder(mContext)
                .setTitle(url)
                .setMessage(message)
                .setPositiveButton(
                    R.string.ok,
                    object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            result.confirm()
                        }
                    })
                .setCancelable(false)
                .create()
                .show()
            return true
        }

        override fun onJsConfirm(
            view: WebView?, url: String?,
            message: String?, result: JsResult
        ): Boolean {
            val b = AlertDialog.Builder(mContext)
            b.setTitle(url)
            b.setMessage(message)
            b.setPositiveButton(
                R.string.ok,
                object : DialogInterface.OnClickListener {
                    override fun onClick(
                        dialog: DialogInterface?,
                        which: Int
                    ) {
                        result.confirm()
                    }
                })
            b.setNegativeButton(
                R.string.cancel,
                object : DialogInterface.OnClickListener {
                    override fun onClick(
                        dialog: DialogInterface?,
                        which: Int
                    ) {
                        result.cancel()
                    }
                })
            b.setCancelable(false)
            b.create()
            b.show()
            return true
        }

        override fun onJsPrompt(
            view: WebView?, url: String?, message: String?,
            defaultValue: String?, result: JsPromptResult
        ): Boolean {
            prompt_input_field.setText(defaultValue)
            val b = AlertDialog.Builder(mContext)
            b.setTitle(url)
            b.setMessage(message)
            b.setView(prompt_input_field)
            b.setPositiveButton(
                R.string.ok,
                object : DialogInterface.OnClickListener {
                    override fun onClick(
                        dialog: DialogInterface?,
                        which: Int
                    ) {
                        val value = prompt_input_field
                            .getText().toString()
                        result.confirm(value)
                    }
                })
            b.setNegativeButton(
                R.string.cancel,
                object : DialogInterface.OnClickListener {
                    override fun onClick(
                        dialog: DialogInterface?,
                        which: Int
                    ) {
                        result.cancel()
                    }
                })
            b.setOnCancelListener(object : DialogInterface.OnCancelListener {
                override fun onCancel(dialog: DialogInterface?) {
                    result.cancel()
                }
            })
            b.show()
            return true
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            //mContext.setProgressBarVisibility(true);
            //mContext.setProgress(newProgress * 100);
            //mContext.setSecondaryProgress(newProgress * 100);
            if (newProgress == 100) {
                mProgressbar.setVisibility(GONE)
            } else {
                mProgressbar.setVisibility(VISIBLE)
                mProgressbar.setProgress(newProgress)
            }
            super.onProgressChanged(view, newProgress)
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            //mContext.setTitle(title);
            super.onReceivedTitle(view, title)
            if (mOnReceivedTitleListener != null) mOnReceivedTitleListener!!.onReceivedTitle(title)
        }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            // TODO: Implement this method
            //mContext.setIcon(new BitmapDrawable(icon));
            super.onReceivedIcon(view, icon)
            if (mOnReceivedIconListener != null) mOnReceivedIconListener!!.onReceivedIcon(icon)
        }

        override fun getDefaultVideoPoster(): Bitmap? {
            return BitmapFactory.decodeResource(
                mContext.getResources(),
                com.luaforge.studio.lxclua.core.R.drawable.icon
            )
        }

        // For Android 3.0+
        // For Android < 3.0
        @JvmOverloads
        fun openFileChooser(uploadMsg: ValueCallback<Uri?>?, acceptType: String? = "") {
            if (mUploadMessage != null) return
            mUploadMessage = uploadMsg
            openFile(mDir)
        }

        // For Android  > 4.1.1
        fun openFileChooser(
            uploadMsg: ValueCallback<Uri?>?,
            acceptType: String?,
            capture: String?
        ) {
            openFileChooser(uploadMsg, acceptType)
        }
    }

    private var mOnReceivedTitleListener: OnReceivedTitleListener? = null
    private var mOnReceivedIconListener: OnReceivedIconListener? = null

    init {
        context.regGc(this)
        mContext = context
        getSettings().setJavaScriptEnabled(true)
        getSettings().setJavaScriptCanOpenWindowsAutomatically(true)
        getSettings().setDisplayZoomControls(true)
        getSettings().setSupportZoom(true)
        getSettings().setDomStorageEnabled(true)
        /*setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });*/
        if (Build.VERSION.SDK_INT >= 21) getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW)
        //getSettings().setUseWideViewPort(true);
        //getSettings().setLoadWithOverviewMode(true);
        //getSettings().setDefaultZoom(WebSettings.ZoomDensity.FAR);
        addJavascriptInterface(LuaJavaScriptInterface(context), "androlua")
        //requestFocus();
        addJavascriptInterface(InJavaScriptLocalObj(), "java_obj")
        setWebViewClient(object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (mAdsFilter != null) {
                    try {
                        val ret = mAdsFilter!!.call(url)
                        if (ret != null && ret) return true
                    } catch (e: LuaException) {
                        e.printStackTrace()
                    }
                }

                if (url.startsWith("http") || url.startsWith("file")) {
                    view.loadUrl(url)
                    return true
                } else {
                    try {
                        mContext.startActivityForResult(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                            0
                        )
                    } catch (e: Exception) {
                        mContext.sendError("LuaWebView", e)
                    }
                    return true
                }
            }

            @Suppress("deprecation")
            override fun shouldInterceptRequest(
                view: WebView?,
                url: String?
            ): WebResourceResponse? {
                if (mAdsFilter != null) {
                    try {
                        val ret = mAdsFilter!!.call(url)
                        if (ret != null && ret) return WebResourceResponse(null, null, null)
                    } catch (e: LuaException) {
                        e.printStackTrace()
                    }
                }
                return null
            }

            override fun onReceivedSslError(
                view: WebView?, handler: SslErrorHandler,
                error: SslError
            ) {
                val b = AlertDialog.Builder(mContext)
                b.setTitle("SslError")
                b.setMessage(error.toString())
                b.setPositiveButton(
                    R.string.ok,
                    object : DialogInterface.OnClickListener {
                        override fun onClick(
                            dialog: DialogInterface?,
                            which: Int
                        ) {
                            handler.proceed()
                        }
                    })
                b.setNegativeButton(
                    R.string.cancel,
                    object : DialogInterface.OnClickListener {
                        override fun onClick(
                            dialog: DialogInterface?,
                            which: Int
                        ) {
                            handler.cancel()
                        }
                    })
                b.setCancelable(false)
                b.create()
                b.show()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.loadUrl(
                    "javascript:window.java_obj.get('<html>'+"
                            + "document.getElementsByTagName('html')[0].innerHTML+'</html>');"
                )
            }
        }
        )

        dm = context.getResources().getDisplayMetrics()
        val top = TypedValue.applyDimension(1, 2f, dm).toInt()

        mProgressbar = ProgressBar(context, null, R.attr.progressBarStyleHorizontal)
        mProgressbar.setLayoutParams(LayoutParams(LayoutParams.FILL_PARENT, top, 0, 0))
        addView(mProgressbar)

        setWebChromeClient(LuaWebChromeClient())
        setDownloadListener(Download())
    }

    fun setOnReceivedTitleListener(listener: OnReceivedTitleListener?) {
        mOnReceivedTitleListener = listener
    }

    fun setOnReceivedIconListener(listener: OnReceivedIconListener?) {
        mOnReceivedIconListener = listener
    }

    interface OnReceivedTitleListener {
        fun onReceivedTitle(string: String?)
    }

    interface OnReceivedIconListener {
        fun onReceivedIcon(bitmap: Bitmap?)
    }

    companion object {
        private const val DOWNLOAD = "Download"
    }
}
