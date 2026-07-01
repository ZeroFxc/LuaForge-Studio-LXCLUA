package com.nirithy.lxclua

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.luaforge.studio.lxclua.utils.LuaRecyclerAdapter
import com.luaforge.studio.lxclua.utils.OkHttpUtil
import com.luaforge.studio.lxclua.utils.RecyclerAdapterUtil.createAdapter
import com.luajava.JavaFunction
import com.luajava.LuaException
import com.luajava.LuaObject
import com.luajava.LuaState
import java.io.File
import java.lang.reflect.Array
import java.lang.reflect.Modifier
import java.util.Arrays

class LuaFunctionRegistrar(
    private val L: LuaState,
    private val context: Context,
    private val luaDir: String?
) {
    /**
     * 根据提供的工具类名称列表选择性注册函数
     */
    fun registerSelectedFunctions(selectedUtils: MutableList<String?>) {
        // 将列表转换为集合以便快速查找

        val selectedSet: MutableSet<String?> = HashSet<String?>(selectedUtils)

        try {
            // 注册选中的工具类
            for (utilName in selectedSet) {
                val className: String? = UTIL_CLASS_MAP.get(utilName)
                if (className != null) {
                    registerUtilClass(className)
                } else {
                    Log.w(TAG, "未找到工具类: " + utilName)
                }
            }

            // 特殊处理：如果包含 OkHttpUtil，需要注册 HTTP 函数
            if (selectedSet.contains("OkHttpUtil")) {
                registerHttpFunctions()
            }

            if (selectedSet.contains("RecyclerAdapterUtil")) {
                registerRecyclerAdapterFunctions()
            }

            // 注册通用函数（无论选择什么工具类都需要的）
            registerCommonFunctions()
        } catch (e: Exception) {
            Log.e(TAG, "选择性注册工具类函数时出错", e)
            e.printStackTrace()
        }
    }

    /**
     * 注册单个工具类
     */
    private fun registerUtilClass(className: String) {
        try {
            val clazz = Class.forName(className)
            val methods = clazz.getMethods()

            for (method in methods) {
                // 只处理公共静态方法
                if (!Modifier.isStatic(method.getModifiers())
                    || !Modifier.isPublic(method.getModifiers())
                ) {
                    continue
                }

                // 跳过Kotlin自动生成的方法
                if (method.getName().contains("$")) {
                    continue
                }

                val methodName = method.getName()

                // 检查是否需要Context参数
                val paramTypes = method.getParameterTypes()
                val needsContext =
                    paramTypes.size > 0 && Context::class.java.isAssignableFrom(paramTypes[0])

                // 检查是否是可变参数方法
                val isVarArgs = method.isVarArgs()

                // 为这个方法创建Lua函数
                L.pushJavaFunction(
                    object : JavaFunction(L) {
                        @Throws(LuaException::class)
                        override fun execute(): Int {
                            try {
                                // 计算实际需要的Lua参数数量
                                val paramCount = paramTypes.size
                                val luaArgCount = L.getTop() - 1

                                // 准备参数数组
                                val args = arrayOfNulls<Any>(paramCount)
                                var luaIndex = 2 // Lua参数从索引2开始

                                // 如果需要Context，自动注入当前context
                                if (needsContext) {
                                    args[0] = context
                                }

                                // 处理固定参数（可变参数前的参数）
                                val fixedParamCount = if (isVarArgs) paramCount - 1 else paramCount
                                for (i in (if (needsContext) 1 else 0)..<fixedParamCount) {
                                    if (luaIndex <= L.getTop()) {
                                        args[i] = L.toJavaObject(luaIndex)
                                        luaIndex++
                                    } else {
                                        // 缺少参数，设为null
                                        args[i] = null
                                    }
                                }

                                // 处理可变参数（如果有）
                                if (isVarArgs) {
                                    // 计算可变参数数量
                                    val varArgCount =
                                        luaArgCount - (fixedParamCount - (if (needsContext) 1 else 0))

                                    if (varArgCount > 0) {
                                        // 创建数组来保存可变参数
                                        val varArgType =
                                            paramTypes[paramCount - 1]!!.getComponentType()
                                        val varArgs =
                                            Array.newInstance(
                                                varArgType,
                                                varArgCount
                                            ) as kotlin.Array<Any?>

                                        // 从Lua栈中获取可变参数
                                        for (i in 0..<varArgCount) {
                                            varArgs[i] = L.toJavaObject(luaIndex)
                                            luaIndex++
                                        }

                                        args[paramCount - 1] = varArgs
                                    } else {
                                        // 如果没有可变参数，传入空数组
                                        val varArgType =
                                            paramTypes[paramCount - 1]!!.getComponentType()
                                        args[paramCount - 1] = Array.newInstance(varArgType, 0)
                                    }
                                }

                                // 调用方法
                                val result = method.invoke(null, *args)

                                // 处理返回值
                                if (method.getReturnType() == Void.TYPE
                                    || method.getReturnType() == Void::class.java
                                ) {
                                    return 0 // 没有返回值
                                } else {
                                    L.pushObjectValue(result)
                                    return 1 // 有返回值
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "调用方法 " + methodName + " 失败: " + e.message)
                                e.printStackTrace()
                                throw LuaException("Failed to call " + methodName + ": " + e.message)
                            }
                        }
                    })

                // 注册到Lua全局变量
                L.setGlobal(methodName)
            }
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 注册HTTP网络请求函数为Lua全局函数
     */
    private fun registerHttpFunctions() {
        try {
            // ========== GET请求 ==========
            // get(url,cookie,charset,header,callback)
            val httpGet: JavaFunction =
                object : JavaFunction(L) {
                    @Throws(LuaException::class)
                    override fun execute(): Int {
                        try {
                            val top = L.getTop()
                            if (top < 2) {
                                throw LuaException("参数不足，至少需要url和callback")
                            }

                            // Lua传递的第一个参数在索引2
                            val url = L.toString(2)

                            // 解析可选参数
                            var cookie: String? = null
                            var charset: String? = null
                            var headers: HashMap<String?, String?>? = null
                            var callback: LuaObject? = null

                            // 遍历参数，判断类型（从索引3开始）
                            for (i in 3..top) {
                                val type = L.type(i)

                                if (type == LuaState.LUA_TFUNCTION) {
                                    // 最后一个参数是callback
                                    callback = L.getLuaObject(i)
                                } else if (type == LuaState.LUA_TSTRING) {
                                    val value = L.toString(i)

                                    // 判断是cookie还是charset
                                    if (cookie == null) {
                                        cookie = value
                                    } else if (charset == null) {
                                        charset = value
                                    }
                                } else if (type == LuaState.LUA_TTABLE) {
                                    // 解析header表
                                    headers = HashMap<String?, String?>()
                                    L.pushNil()
                                    while (L.next(i) != 0) {
                                        try {
                                            val key = L.toString(-2)
                                            val value = L.toString(-1)
                                            headers.put(key, value)
                                        } catch (e: Exception) {
                                            // 忽略转换错误
                                        }
                                        L.pop(1)
                                    }
                                }
                            }

                            if (callback == null) {
                                throw LuaException("缺少callback函数")
                            }

                            val finalCookie = cookie
                            val finalCharset = charset
                            val finalHeaders = headers
                            val finalCallback: LuaObject? = callback

                            val thread =
                                Thread(
                                    object : Runnable {
                                        override fun run() {
                                            try {
                                                @Suppress("UNCHECKED_CAST")
                                                val response =
                                                    OkHttpUtil.get(
                                                        context,  // 使用传入的Context
                                                        url,
                                                        finalCookie,
                                                        finalCharset,
                                                        finalHeaders as? Map<String, String>?
                                                    )

                                                (context as Activity)
                                                    .runOnUiThread(
                                                        object : Runnable {
                                                            override fun run() {
                                                                try {
                                                                    // 调用回调函数，传入四个参数
                                                                    finalCallback!!.call(
                                                                        response.code,
                                                                        response.content,
                                                                        response.cookie,
                                                                        response.headers
                                                                    )
                                                                } catch (e: LuaException) {
                                                                    e.printStackTrace()
                                                                    try {
                                                                        finalCallback!!.call(
                                                                            -1,
                                                                            "Callback error: " + e.message,
                                                                            "",
                                                                            HashMap<String?, String?>()
                                                                        )
                                                                    } catch (e2: LuaException) {
                                                                        e2.printStackTrace()
                                                                    }
                                                                }
                                                            }
                                                        })
                                            } catch (e: Exception) {
                                                (context as Activity)
                                                    .runOnUiThread(
                                                        object : Runnable {
                                                            override fun run() {
                                                                try {
                                                                    finalCallback!!.call(
                                                                        -1,
                                                                        "Error: " + e.message,
                                                                        "",
                                                                        HashMap<String?, String?>()
                                                                    )
                                                                } catch (e2: LuaException) {
                                                                    e2.printStackTrace()
                                                                }
                                                            }
                                                        })
                                            }
                                        }
                                    })
                            thread.start()
                        } catch (e: Exception) {
                            throw LuaException(e)
                        }
                        return 0
                    }
                }
            httpGet.register("get")

            // ========== POST请求 ==========
            val httpPost: JavaFunction =
                object : JavaFunction(L) {
                    @Throws(LuaException::class)
                    override fun execute(): Int {
                        try {
                            val top = L.getTop()
                            if (top < 3) {
                                throw LuaException("参数不足，至少需要url、data和callback")
                            }

                            // Lua传递的参数从索引2开始
                            val url = L.toString(2)

                            // 解析参数
                            val formData = HashMap<String?, String?>()
                            var cookie: String? = null
                            var charset: String? = null
                            var headers: HashMap<String?, String?>? = null
                            var callback: LuaObject? = null

                            // 参数索引（从3开始）
                            var paramIndex = 3

                            // 第一个参数应该是data表
                            if (paramIndex <= top) {
                                val type = L.type(paramIndex)

                                if (type == LuaState.LUA_TTABLE) {
                                    // 解析formData表
                                    L.pushNil()
                                    while (L.next(paramIndex) != 0) {
                                        try {
                                            val key = L.toString(-2)
                                            val value = L.toString(-1)
                                            formData.put(key, value)
                                        } catch (e: Exception) {
                                            // 忽略转换错误
                                        }
                                        L.pop(1)
                                    }
                                    paramIndex++
                                } else if (type == LuaState.LUA_TFUNCTION) {
                                    // 没有data参数，直接是callback
                                    callback = L.getLuaObject(paramIndex)
                                    paramIndex++
                                }
                            }

                            // 继续解析剩余参数（如果有）
                            while (paramIndex <= top) {
                                val type = L.type(paramIndex)

                                if (type == LuaState.LUA_TFUNCTION) {
                                    // callback函数
                                    callback = L.getLuaObject(paramIndex)
                                    paramIndex++
                                } else if (type == LuaState.LUA_TSTRING) {
                                    val value = L.toString(paramIndex)

                                    // 判断是cookie还是charset
                                    if (cookie == null) {
                                        cookie = value
                                    } else if (charset == null) {
                                        charset = value
                                    }
                                    paramIndex++
                                } else if (type == LuaState.LUA_TTABLE) {
                                    // 解析header表
                                    headers = HashMap<String?, String?>()
                                    L.pushNil()
                                    while (L.next(paramIndex) != 0) {
                                        try {
                                            val key = L.toString(-2)
                                            val value = L.toString(-1)
                                            headers.put(key, value)
                                        } catch (e: Exception) {
                                            // 忽略转换错误
                                        }
                                        L.pop(1)
                                    }
                                    paramIndex++
                                } else {
                                    // 未知参数类型，跳过
                                    paramIndex++
                                }
                            }

                            if (callback == null) {
                                throw LuaException("缺少callback函数")
                            }

                            val finalFormData = if (formData.isEmpty()) null else formData
                            val finalCookie = cookie
                            val finalCharset = charset
                            val finalHeaders = headers
                            val finalCallback: LuaObject? = callback

                            val thread =
                                Thread(
                                    object : Runnable {
                                        override fun run() {
                                            try {
                                                @Suppress("UNCHECKED_CAST")
                                                val response =
                                                    OkHttpUtil.post(
                                                        context,
                                                        url,
                                                        finalFormData as? Map<String, String>?,
                                                        finalCookie,
                                                        finalCharset,
                                                        finalHeaders as? Map<String, String>?
                                                    )

                                                (context as Activity)
                                                    .runOnUiThread(
                                                        object : Runnable {
                                                            override fun run() {
                                                                try {
                                                                    // 调用回调函数，传入四个参数
                                                                    finalCallback!!.call(
                                                                        response.code,
                                                                        response.content,
                                                                        response.cookie,
                                                                        response.headers
                                                                    )
                                                                } catch (e: LuaException) {
                                                                    e.printStackTrace()
                                                                    try {
                                                                        finalCallback!!.call(
                                                                            -1,
                                                                            "Callback error: " + e.message,
                                                                            "",
                                                                            HashMap<String?, String?>()
                                                                        )
                                                                    } catch (e2: LuaException) {
                                                                        e2.printStackTrace()
                                                                    }
                                                                }
                                                            }
                                                        })
                                            } catch (e: Exception) {
                                                (context as Activity)
                                                    .runOnUiThread(
                                                        object : Runnable {
                                                            override fun run() {
                                                                try {
                                                                    finalCallback!!.call(
                                                                        -1,
                                                                        "Error: " + e.message,
                                                                        "",
                                                                        HashMap<String?, String?>()
                                                                    )
                                                                } catch (e2: LuaException) {
                                                                    e2.printStackTrace()
                                                                }
                                                            }
                                                        })
                                            }
                                        }
                                    })
                            thread.start()
                        } catch (e: Exception) {
                            throw LuaException(e)
                        }
                        return 0
                    }
                }
            httpPost.register("post")

            // ========== 文件下载 ==========
            val httpDownload: JavaFunction =
                object : JavaFunction(L) {
                    @Throws(LuaException::class)
                    override fun execute(): Int {
                        try {
                            val top = L.getTop()
                            if (top < 3) {
                                throw LuaException("参数不足，至少需要url、path和callback")
                            }

                            // Lua传递的参数在索引2和3
                            val url = L.toString(2)
                            val savePath = L.toString(3)

                            // 获取完整保存路径
                            val fullSavePath = getFullPathForHttp(savePath)

                            // 解析可选参数
                            var cookie: String? = null
                            var headers: HashMap<String?, String?>? = null
                            var callback: LuaObject? = null

                            // 遍历参数，判断类型（从索引4开始）
                            for (i in 4..top) {
                                val type = L.type(i)

                                if (type == LuaState.LUA_TFUNCTION) {
                                    // 最后一个参数是callback
                                    callback = L.getLuaObject(i)
                                } else if (type == LuaState.LUA_TSTRING) {
                                    // 只能是cookie
                                    cookie = L.toString(i)
                                } else if (type == LuaState.LUA_TTABLE) {
                                    // 解析header表
                                    headers = HashMap<String?, String?>()
                                    L.pushNil()
                                    while (L.next(i) != 0) {
                                        try {
                                            val key = L.toString(-2)
                                            val value = L.toString(-1)
                                            headers.put(key, value)
                                        } catch (e: Exception) {
                                            // 忽略转换错误
                                        }
                                        L.pop(1)
                                    }
                                }
                            }

                            if (callback == null) {
                                throw LuaException("缺少callback函数")
                            }

                            val finalCookie = cookie
                            val finalHeaders = headers
                            val finalCallback: LuaObject? = callback

                            val thread =
                                Thread(
                                    object : Runnable {
                                        override fun run() {
                                            try {
                                                @Suppress("UNCHECKED_CAST")
                                                val response =
                                                    OkHttpUtil.download(
                                                        context,
                                                        url,
                                                        fullSavePath,
                                                        finalCookie,
                                                        finalHeaders as? Map<String, String>?
                                                    )

                                                (context as Activity)
                                                    .runOnUiThread(
                                                        object : Runnable {
                                                            override fun run() {
                                                                try {
                                                                    // 调用回调函数，传入四个参数
                                                                    finalCallback!!.call(
                                                                        response.code,
                                                                        response.content,
                                                                        response.cookie,
                                                                        response.headers
                                                                    )
                                                                } catch (e: LuaException) {
                                                                    e.printStackTrace()
                                                                    try {
                                                                        finalCallback!!.call(
                                                                            -1,
                                                                            "Callback error: " + e.message,
                                                                            "",
                                                                            HashMap<String?, String?>()
                                                                        )
                                                                    } catch (e2: LuaException) {
                                                                        e2.printStackTrace()
                                                                    }
                                                                }
                                                            }
                                                        })
                                            } catch (e: Exception) {
                                                (context as Activity)
                                                    .runOnUiThread(
                                                        object : Runnable {
                                                            override fun run() {
                                                                try {
                                                                    finalCallback!!.call(
                                                                        -1,
                                                                        "Error: " + e.message,
                                                                        "",
                                                                        HashMap<String?, String?>()
                                                                    )
                                                                } catch (e2: LuaException) {
                                                                    e2.printStackTrace()
                                                                }
                                                            }
                                                        })
                                            }
                                        }
                                    })
                            thread.start()
                        } catch (e: Exception) {
                            throw LuaException(e)
                        }
                        return 0
                    }
                }
            httpDownload.register("download")

            // ========== 文件上传 ==========
            val httpUpload: JavaFunction =
                object : JavaFunction(L) {
                    @Throws(LuaException::class)
                    override fun execute(): Int {
                        try {
                            val top = L.getTop()
                            if (top < 3) {
                                throw LuaException("参数不足，至少需要url、filePath和callback")
                            }

                            // Lua传递的参数在索引2和3
                            val url = L.toString(2)
                            val filePath = L.toString(3)

                            // 获取完整文件路径
                            val fullFilePath = getFullPathForHttp(filePath)

                            // 解析可选参数
                            var cookie: String? = null
                            var headers: HashMap<String?, String?>? = null
                            var callback: LuaObject? = null

                            // 遍历参数，判断类型（从索引4开始）
                            for (i in 4..top) {
                                val type = L.type(i)

                                if (type == LuaState.LUA_TFUNCTION) {
                                    // 最后一个参数是callback
                                    callback = L.getLuaObject(i)
                                } else if (type == LuaState.LUA_TSTRING) {
                                    // 只能是cookie
                                    cookie = L.toString(i)
                                } else if (type == LuaState.LUA_TTABLE) {
                                    // 解析header表
                                    headers = HashMap<String?, String?>()
                                    L.pushNil()
                                    while (L.next(i) != 0) {
                                        try {
                                            val key = L.toString(-2)
                                            val value = L.toString(-1)
                                            headers.put(key, value)
                                        } catch (e: Exception) {
                                            // 忽略转换错误
                                        }
                                        L.pop(1)
                                    }
                                }
                            }

                            if (callback == null) {
                                throw LuaException("缺少callback函数")
                            }

                            val finalCookie = cookie
                            val finalHeaders = headers
                            val finalCallback: LuaObject? = callback

                            val thread =
                                Thread(
                                    object : Runnable {
                                        override fun run() {
                                            try {
                                                @Suppress("UNCHECKED_CAST")
                                                val response =
                                                    OkHttpUtil.upload(
                                                        context,
                                                        url,
                                                        fullFilePath,
                                                        finalCookie,
                                                        finalHeaders as? Map<String, String>?,
                                                        "file",  // fieldName 参数
                                                        null // extraParams 参数
                                                    )

                                                (context as Activity)
                                                    .runOnUiThread(
                                                        object : Runnable {
                                                            override fun run() {
                                                                try {
                                                                    // 调用回调函数，传入四个参数
                                                                    finalCallback!!.call(
                                                                        response.code,
                                                                        response.content,
                                                                        response.cookie,
                                                                        response.headers
                                                                    )
                                                                } catch (e: LuaException) {
                                                                    e.printStackTrace()
                                                                    try {
                                                                        finalCallback!!.call(
                                                                            -1,
                                                                            "Callback error: " + e.message,
                                                                            "",
                                                                            HashMap<String?, String?>()
                                                                        )
                                                                    } catch (e2: LuaException) {
                                                                        e2.printStackTrace()
                                                                    }
                                                                }
                                                            }
                                                        })
                                            } catch (e: Exception) {
                                                (context as Activity)
                                                    .runOnUiThread(
                                                        object : Runnable {
                                                            override fun run() {
                                                                try {
                                                                    finalCallback!!.call(
                                                                        -1,
                                                                        "Error: " + e.message,
                                                                        "",
                                                                        HashMap<String?, String?>()
                                                                    )
                                                                } catch (e2: LuaException) {
                                                                    e2.printStackTrace()
                                                                }
                                                            }
                                                        })
                                            }
                                        }
                                    })
                            thread.start()
                        } catch (e: Exception) {
                            throw LuaException(e)
                        }
                        return 0
                    }
                }
            httpUpload.register("upload")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "注册HTTP函数失败: " + e.message)
        }
    }


    private fun registerRecyclerAdapterFunctions() {
        try {
            // 注册createAdapter方法

            val createAdapterFunc: JavaFunction = object : JavaFunction(L) {
                @Throws(LuaException::class)
                override fun execute(): Int {
                    try {
                        // 获取参数：data, list_item, method
                        val top = L.getTop()
                        if (top < 3) {
                            throw LuaException("参数不足，需要data, list_item, method三个参数")
                        }

                        // 获取data参数
                        var dataList: MutableList<Any?> = ArrayList<Any?>()

                        // 参数1是data表
                        if (L.isTable(1)) {
                            // 使用LuaTable的asArray()方法将Lua表转换为Java数组
                            val luaData = L.getLuaObject(1)

                            // 检查是否为数组表
                            if (luaData.isTable()) {
                                try {
                                    // 使用asArray()方法将Lua表转换为Object数组
                                    val array = luaData.asArray()
                                    if (array != null) {
                                        // 将数组转换为List
                                        dataList = Arrays.asList<Any?>(*array)
                                    } else {
                                        // 如果asArray()返回null，尝试手动遍历
                                        val len = L.rawLen(1)
                                        for (i in 1..len) {
                                            L.pushInteger(i.toLong())
                                            L.getTable(1)
                                            try {
                                                dataList.add(L.toJavaObject(-1))
                                            } catch (e: Exception) {
                                                // 忽略转换错误
                                            }
                                            L.pop(1)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "转换Lua表为数组失败: " + e.message, e)
                                    // 备用方法：手动遍历
                                    val len = L.rawLen(1)
                                    var i = 1
                                    while (i <= len) {
                                        L.pushInteger(i.toLong())
                                        L.getTable(1)
                                        try {
                                            dataList.add(L.toJavaObject(-1))
                                        } catch (e2: Exception) {
                                            // 忽略转换错误
                                        }
                                        L.pop(1)
                                        i++
                                    }
                                }
                            }
                        } else if (L.isUserdata(1)) {
                            val obj = L.toJavaObject(1)
                            if (obj is MutableList<*>) {
                                dataList = obj as MutableList<Any?>
                            }
                        }

                        // 获取list_item参数
                        val listItem: Any
                        val type = L.type(2)

                        when (type) {
                            LuaState.LUA_TSTRING -> listItem = L.toString(2)
                            LuaState.LUA_TNUMBER -> if (L.isInteger(2)) {
                                listItem = L.toInteger(2).toInt()
                            } else {
                                listItem = L.toNumber(2)
                            }

                            LuaState.LUA_TTABLE -> listItem = L.getLuaObject(2)
                            else -> listItem = L.toJavaObject(2)
                        }

                        // 获取method参数
                        val method = L.getLuaObject(3)

                        // 调用RecyclerAdapterUtil.createAdapter
                        val adapter =
                            createAdapter(
                                context,
                                dataList,
                                listItem,
                                method
                            )

                        L.pushJavaObject(adapter)
                        return 1
                    } catch (e: Exception) {
                        Log.e(TAG, "创建RecyclerAdapter失败: " + e.message, e)
                        throw LuaException("创建RecyclerAdapter失败: " + e.message)
                    }
                }
            }
            createAdapterFunc.register("createRecyclerAdapter")

            // 注册notifyDataSetChanged方法
            val notifyDataSetChangedFunc: JavaFunction = object : JavaFunction(L) {
                @Throws(LuaException::class)
                override fun execute(): Int {
                    try {
                        if (L.getTop() < 1) {
                            throw LuaException("参数不足，需要adapter参数")
                        }

                        val adapterObj = L.getLuaObject(1)
                        if (adapterObj.isUserdata()) {
                            val obj = adapterObj.getObject()
                            if (obj is LuaRecyclerAdapter) {
                                obj.notifyDataSetChanged()
                            } else if (obj is RecyclerView.Adapter<*>) {
                                obj.notifyDataSetChanged()
                            }
                        }
                        return 0
                    } catch (e: Exception) {
                        throw LuaException(e)
                    }
                }
            }
            notifyDataSetChangedFunc.register("notifyDataSetChanged")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 注册一些通用的Lua函数
     */
    private fun registerCommonFunctions() {
        try {
            // 注册获取Lua目录的函数
            val getLuaDirectory: JavaFunction =
                object : JavaFunction(L) {
                    @Throws(LuaException::class)
                    override fun execute(): Int {
                        L.pushString(luaDir)
                        return 1
                    }
                }
            getLuaDirectory.register("getLuaDir")
        } catch (e: Exception) {
        }
    }

    /**
     * 为HTTP函数获取完整文件路径的辅助方法
     */
    private fun getFullPathForHttp(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("/")) {
            return path
        }
        return File(luaDir, path).getAbsolutePath()
    }

    companion object {
        private const val TAG = "LuaFunctionRegistrar"

        // 工具类映射表，用于快速查找
        private val UTIL_CLASS_MAP: MutableMap<String?, String?> =
            object : HashMap<String?, String?>() {
                init {
                    put("BitmapUtil", "com.luaforge.studio.lxclua.utils.BitmapUtil")
                    put("GlideUtil", "com.luaforge.studio.lxclua.utils.GlideUtil")
                    put("OkHttpUtil", "com.luaforge.studio.lxclua.utils.OkHttpUtil")
                    put("UiUtil", "com.luaforge.studio.lxclua.utils.UiUtil")
                    put(
                        "RecyclerAdapterUtil",
                        "com.luaforge.studio.lxclua.utils.RecyclerAdapterUtil"
                    )
                    put("ThemeUtil", "com.luaforge.studio.lxclua.utils.ThemeUtil")
                }
            }
    }
}
