package com.nirithy.lxclua

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.luajava.LuaException
import com.luajava.LuaFunction
import com.luajava.LuaJavaAPI
import com.luajava.LuaObject
import com.luajava.LuaState
import com.luajava.LuaTable
import java.io.IOException
import java.util.Locale

/**
 * Created by Administrator on 2017/02/27 0027.
 */
class LuaMultiAdapter(
    context: LuaContext,
    data: LuaTable<Int?, LuaTable<String?, Any?>?>?,
    layout: LuaTable<Int?, LuaTable<*, *>?>
) : BaseAdapter() {
    private var mDraw: BitmapDrawable? = null
    private val mRes: Resources?
    private val L: LuaState
    private val mContext: LuaContext
    private val mLayout: LuaTable<Int?, LuaTable<*, *>?>
    private val mData: LuaTable<Int?, LuaTable<String?, Any?>?>?
    private var mTheme: LuaTable<String?, Any?>? = null
    private val loadLayout: LuaFunction<View?>
    private val insert: LuaFunction<*>
    private val remove: LuaFunction<*>
    private var mAnimationUtil: LuaTable<Int?, LuaFunction<Animation?>?>? = null
    private val mAnimCache = HashMap<View?, Animation?>()
    private val mStyleCache = HashMap<View?, Boolean?>()

    private var mNotifyOnChange = true
    private var updateing = false
    private val mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            notifyDataSetChanged()
        }
    }
    private val loaded = HashMap<String?, Boolean?>()

    @Suppress("UNCHECKED_CAST")
    constructor(context: LuaContext, layout: LuaTable<*, *>) : this(context, null, layout as LuaTable<Int?, LuaTable<*, *>?>)

    init {
        var data = data
        mContext = context
        mLayout = layout
        mRes = mContext.context!!.resources

        L = context.luaState!!
        if (data == null) data = LuaTable<Int?, LuaTable<String?, Any?>?>(L)
        mData = data
        loadLayout = L.getLuaObject("loadlayout").getFunction() as LuaFunction<View?>
        insert = L.getLuaObject("table").getField("insert").getFunction()
        remove = L.getLuaObject("table").getField("remove").getFunction()
        val len = mLayout.length()
        for (i in 1..len) {
            L.newTable()
            loadLayout.call(mLayout.get(i), L.getLuaObject(-1), AbsListView::class.java)
            L.pop(1)
        }
    }

    override fun getViewTypeCount(): Int {
        return mLayout.length()
    }

    override fun getItemViewType(position: Int): Int {
        val t = (mData!!.get(position + 1)!!.get("__type") as Long).toInt() - 1
        return if (t < 0) 0 else t
    }


    fun setAnimation(animation: LuaTable<Int?, LuaFunction<Animation?>?>?) {
        setAnimationUtil(animation)
    }

    fun setAnimationUtil(animation: LuaTable<Int?, LuaFunction<Animation?>?>?) {
        mAnimCache.clear()
        mAnimationUtil = animation
    }

    override fun getCount(): Int {
        // TODO: Implement this method
        return mData!!.length()
    }

    override fun getItem(position: Int): Any? {
        // TODO: Implement this method
        return mData!!.get(position + 1)
    }

    override fun getItemId(position: Int): Long {
        // TODO: Implement this method
        return (position + 1).toLong()
    }

    val data: LuaTable<Int?, LuaTable<String?, Any?>?>
        get() = mData!!

    @Throws(Exception::class)
    fun add(item: LuaTable<String?, Any?>?) {
        insert.call(mData, item)
        if (mNotifyOnChange) notifyDataSetChanged()
    }

    @Throws(Exception::class)
    fun addAll(items: LuaTable<Int?, LuaTable<String?, Any?>?>) {
        val len = items.length()
        for (i in 1..len) insert.call(mData, items.get(i))
        if (mNotifyOnChange) notifyDataSetChanged()
    }


    @Throws(Exception::class)
    fun insert(position: Int, item: LuaTable<String?, Any?>?) {
        insert.call(mData, position + 1, item)
        if (mNotifyOnChange) notifyDataSetChanged()
    }

    @Throws(Exception::class)
    fun remove(position: Int) {
        remove.call(mData, position + 1)
        if (mNotifyOnChange) notifyDataSetChanged()
    }

    fun clear() {
        mData!!.clear()
        if (mNotifyOnChange) notifyDataSetChanged()
    }

    fun setNotifyOnChange(notifyOnChange: Boolean) {
        mNotifyOnChange = notifyOnChange
        if (mNotifyOnChange) notifyDataSetChanged()
    }

    override fun notifyDataSetChanged() {
        // TODO: Implement this method
        super.notifyDataSetChanged()
        if (!updateing) {
            updateing = true
            Handler().postDelayed(object : Runnable {
                override fun run() {
                    // TODO: Implement this method
                    updateing = false
                }
            }, 500)
        }
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        // TODO: Implement this method
        return getView(position, convertView, parent)
    }

    fun setStyle(theme: LuaTable<String?, Any?>?) {
        mStyleCache.clear()
        mTheme = theme
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        // TODO: Implement this method
        var view: View? = null
        var holder: LuaObject? = null
        var t = (mData!!.get(position + 1)!!.get("__type") as Long).toInt()
        t = if (t < 1) 1 else t
        if (convertView == null) {
            try {
                val layout = mLayout.get(t)
                L.newTable()
                holder = L.getLuaObject(-1)
                L.pop(1)
                view = loadLayout.call(layout, holder, AbsListView::class.java)
                view!!.setTag(holder)
                //mHolderCache.put(view,holder);
            } catch (e: LuaException) {
                return View(mContext.context)
            }
        } else {
            view = convertView
            holder = view.getTag() as LuaObject?
            //holder = mHolderCache.get(view);
        }

        val hm = mData.get(position + 1)

        if (hm == null) {
            Log.i("lua", position.toString() + " is null")
            return view
        }

        val bool = mStyleCache.get(view) == null
        if (bool) mStyleCache.put(view, true)

        val sets = hm.entries
        for (entry in sets) {
            try {
                val key: String = entry.key!!
                if (key == "type") continue
                val value: Any = entry.value!!
                val obj = holder!!.getField(key)
                if (obj.isJavaObject()) {
                    if (mTheme != null && bool) {
                        setHelper((obj.getObject() as android.view.View?)!!, mTheme!!.get(key)!!)
                    }
                    setHelper((obj.getObject() as android.view.View?)!!, value)
                }
            } catch (e: Exception) {
                Log.i("lua", e.message!!)
            }
        }

        if (updateing) {
            return view
        }

        if (mAnimationUtil != null && convertView != null) {
            var anim = mAnimCache.get(convertView)
            if (anim == null) {
                try {
                    anim = mAnimationUtil!!.get(t)!!.call()
                    mAnimCache.put(convertView, anim)
                } catch (e: Exception) {
                    mContext.sendError("setAnimation", e)
                }
            }
            if (anim != null) {
                view.clearAnimation()
                view.startAnimation(anim)
            }
        }
        return view
    }

    @Throws(LuaException::class)
    private fun setFields(view: View, fields: LuaTable<String?, Any?>) {
        val sets = fields.entries
        for (entry2 in sets) {
            val key2: String = entry2.key!!
            val value2: Any = entry2.value!!
            if (key2.equals("src", ignoreCase = true)) setHelper(view, value2)
            else javaSetter(view, key2, value2)
        }
    }

    private fun setHelper(view: View, value: Any) {
        try {
            if (value is LuaTable<*, *>) {
                setFields(view, value as LuaTable<String?, Any?>)
            } else if (view is TextView) {
                if (value is CharSequence) view.setText(value)
                else view.setText(value.toString())
            } else if (view is ImageView) {
                if (value is Bitmap) view.setImageBitmap(value)
                else if (value is String) view.setImageDrawable(
                    AsyncLoader().getBitmap(mContext, value)
                )
                else if (value is Drawable) view.setImageDrawable(value)
                else if (value is Number) view.setImageResource(value.toInt())
            }
        } catch (e: Exception) {
            mContext.sendError("setHelper", e)
        }
    }

    @Throws(LuaException::class)
    private fun javaSetter(obj: Any, methodName: String, value: Any): Int {
        if (methodName.length > 2 && methodName.startsWith("on") && value is LuaFunction<*>) return javaSetListener(
            obj,
            methodName,
            value
        )

        return javaSetMethod(obj, methodName, value)
    }

    @Throws(LuaException::class)
    private fun javaSetListener(obj: Any, methodName: String, value: Any?): Int {
        val name = "setOn" + methodName.substring(2) + "Listener"
        val methods = LuaJavaAPI.getMethod(obj.javaClass, name, false)
        for (m in methods) {
            val tp = m.getParameterTypes()
            if (tp.size == 1 && tp[0]!!.isInterface()) {
                L.newTable()
                L.pushObjectValue(value)
                L.setField(-2, methodName)
                try {
                    val listener = L.getLuaObject(-1).createProxy(tp[0])
                    m.invoke(obj, listener)
                    return 1
                } catch (e: Exception) {
                    throw LuaException(e)
                }
            }
        }
        return 0
    }

    @Throws(LuaException::class)
    private fun javaSetMethod(obj: Any, methodName: String, value: Any): Int {
        var methodName = methodName
        if (Character.isLowerCase(methodName.get(0))) {
            methodName = methodName.get(0).uppercaseChar().toString() + methodName.substring(1)
        }
        val name = "set" + methodName
        val type: Class<*> = value.javaClass
        val buf = StringBuilder()


        val methods = LuaJavaAPI.getMethod(obj.javaClass, name, false)

        for (m in methods) {
            val tp = m.getParameterTypes()
            if (tp.size != 1) continue

            if (tp[0]!!.isPrimitive()) {
                try {
                    if (value is Double || value is Float) {
                        m.invoke(
                            obj,
                            LuaState.convertLuaNumber((value as Number).toDouble(), tp[0])
                        )
                    } else if (value is Long || value is Int) {
                        m.invoke(obj, LuaState.convertLuaNumber((value as Number).toLong(), tp[0]))
                    } else if (value is Boolean) {
                        m.invoke(obj, value)
                    } else {
                        continue
                    }
                    return 1
                } catch (e: Exception) {
                    buf.append(e.message)
                    buf.append("\n")
                    continue
                }
            }

            if (!tp[0]!!.isAssignableFrom(type)) continue

            try {
                m.invoke(obj, value)
                return 1
            } catch (e: Exception) {
                buf.append(e.message)
                buf.append("\n")
                continue
            }
        }
        if (buf.length > 0) throw LuaException("Invalid setter " + methodName + ". Invalid Parameters.\n" + buf + type)
        else throw LuaException("Invalid setter " + methodName + " is not a method.\n")
    }

    private inner class AsyncLoader : Thread() {
        private var mPath: String? = null

        private var mContext: LuaContext? = null

        @Throws(IOException::class)
        fun getBitmap(context: LuaContext, path: String): Drawable {
            // TODO: Implement this method
            mContext = context
            mPath = path
            if (!path.lowercase(Locale.getDefault())
                    .startsWith("http://") && !path.lowercase(Locale.getDefault())
                    .startsWith("https://")
            ) return BitmapDrawable(mRes, LuaBitmap.getBitmap(context, path))
            if (LuaBitmap.checkCache(context, path)) return BitmapDrawable(
                mRes,
                LuaBitmap.getBitmap(context, path)
            )
            if (!loaded.containsKey(mPath)) {
                start()
                loaded.put(mPath, true)
            }

            return LoadingDrawable(mContext!!.context!!)
        }

        override fun run() {
            // TODO: Implement this method
            try {
                LuaBitmap.getBitmap(mContext!!, mPath!!)
                mHandler.sendEmptyMessage(0)
            } catch (e: IOException) {
                mContext!!.sendError("AsyncLoader Error", e)
            }
        }
    }
}
