package com.nirithy.lxclua

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.AbsListView
import android.widget.BaseExpandableListAdapter
import android.widget.ImageView
import android.widget.TextView
import com.luajava.LuaException
import com.luajava.LuaFunction
import com.luajava.LuaJavaAPI
import com.luajava.LuaState
import com.luajava.LuaTable
import java.io.IOException
import java.util.Locale

class LuaExpandableListAdapter(
    context: LuaContext,
    groupData: LuaTable<Int?, LuaTable<String?, Any?>?>?,
    childData: LuaTable<Int?, LuaTable<Int?, LuaTable<String?, Any?>?>?>?,
    groupLayout: LuaTable<*, *>?,
    childLayout: LuaTable<*, *>?
) : BaseExpandableListAdapter() {
    private val mDraw: BitmapDrawable
    private val mRes: Resources?
    private val L: LuaState
    private val mContext: LuaContext

    private val mGroupData: LuaTable<Int?, LuaTable<String?, Any?>?>?
    private val mChildData: LuaTable<Int?, LuaTable<Int?, LuaTable<String?, Any?>?>?>?

    private val mAnimCache = HashMap<View?, Animation?>()

    private val mGroupLayout: LuaTable<*, *>?
    private val mChildLayout: LuaTable<*, *>?

    private val loadlayout: LuaFunction<View?>

    private val insert: LuaFunction<*>

    private val remove: LuaFunction<*>

    private var updateing = false

    private var mAnimationUtil: LuaFunction<Animation?>? = null

    private var mNotifyOnChange = false
    private val mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            notifyDataSetChanged()
        }
    }
    private val loaded = HashMap<String?, Boolean?>()

    constructor(
        context: LuaContext,
        groupLayout: LuaTable<*, *>?,
        childLayout: LuaTable<*, *>?
    ) : this(context, null, null, groupLayout, childLayout)


    init {
        var groupData = groupData
        var childData = childData
        mContext = context
        L = context.luaState!!
        mRes = mContext.context!!.resources

        mDraw = BitmapDrawable(mRes, javaClass.getResourceAsStream("/res/drawable/icon.png"))
        mDraw.setColorFilter(-0x77000001, PorterDuff.Mode.SRC_ATOP)

        mGroupLayout = groupLayout
        mChildLayout = childLayout

        if (groupData == null) groupData = LuaTable<Int?, LuaTable<String?, Any?>?>(L)
        if (childData == null) childData =
            LuaTable<Int?, LuaTable<Int?, LuaTable<String?, Any?>?>?>(L)
        mGroupData = groupData
        mChildData = childData

        loadlayout = L.getLuaObject("loadlayout").getFunction() as LuaFunction<View?>
        insert = L.getLuaObject("table").getField("insert").getFunction()
        remove = L.getLuaObject("table").getField("remove").getFunction()

        L.newTable()
        loadlayout.call(mGroupLayout, L.getLuaObject(-1), AbsListView::class.java)
        loadlayout.call(mChildLayout, L.getLuaObject(-1), AbsListView::class.java)
        L.pop(1)
    }

    fun setAnimationUtil(animation: LuaFunction<Animation?>?) {
        mAnimCache.clear()
        mAnimationUtil = animation
    }

    override fun getGroupCount(): Int {
        // TODO: Implement this method
        return mGroupData!!.length()
    }

    override fun getChildrenCount(groupPosition: Int): Int {
        // TODO: Implement this method
        return mChildData!!.get(groupPosition + 1)!!.length()
    }

    override fun getGroup(groupPosition: Int): Any? {
        // TODO: Implement this method
        return mGroupData!!.get(groupPosition + 1)
    }

    override fun getChild(groupPosition: Int, childPosition: Int): Any? {
        // TODO: Implement this method
        return mChildData!!.get(groupPosition + 1)!!.get(childPosition + 1)
    }

    override fun getGroupId(groupPosition: Int): Long {
        // TODO: Implement this method
        return (groupPosition + 1).toLong()
    }

    override fun getChildId(groupPosition: Int, childPosition: Int): Long {
        // TODO: Implement this method
        return (childPosition + 1).toLong()
    }

    override fun hasStableIds(): Boolean {
        // TODO: Implement this method
        return false
    }

    fun getGroupItem(groupPosition: Int): GroupItem {
        // TODO: Implement this method
        return GroupItem(mChildData!!.get(groupPosition + 1)!!)
    }

    val groupData: LuaTable<Int?, LuaTable<String?, Any?>?>
        get() = mGroupData!!

    val childData: LuaTable<Int?, LuaTable<Int?, LuaTable<String?, Any?>?>?>
        get() = mChildData!!

    @Throws(Exception::class)
    fun add(groupItem: LuaTable<String?, Any?>?): GroupItem {
        mGroupData!!.put(mGroupData.length() + 1, groupItem)
        val childItem = LuaTable<Int?, LuaTable<String?, Any?>?>(L)
        mChildData!!.put(mGroupData.length(), childItem)
        if (mNotifyOnChange) notifyDataSetChanged()
        return GroupItem(childItem)
    }

    @Throws(Exception::class)
    fun add(
        groupItem: LuaTable<String?, Any?>?,
        childItem: LuaTable<Int?, LuaTable<String?, Any?>?>
    ): GroupItem {
        mGroupData!!.put(mGroupData.length() + 1, groupItem)
        mChildData!!.put(mGroupData.length(), childItem)
        if (mNotifyOnChange) notifyDataSetChanged()
        return GroupItem(childItem)
    }

    @Throws(Exception::class)
    fun insert(
        position: Int,
        groupItem: LuaTable<String?, Any?>?,
        childItem: LuaTable<Int?, LuaTable<String?, Any?>?>
    ): GroupItem {
        insert.call(mGroupData, position + 1, groupItem)
        insert.call(mChildData, position + 1, childItem)
        if (mNotifyOnChange) notifyDataSetChanged()
        return GroupItem(childItem)
    }

    @Throws(Exception::class)
    fun remove(idx: Int) {
        remove.call(mGroupData, idx + 1)
        if (mNotifyOnChange) notifyDataSetChanged()
    }

    fun clear() {
        mGroupData!!.clear()
        mChildData!!.clear()
        if (mNotifyOnChange) notifyDataSetChanged()
    }

    fun setNotifyOnChange(notifyOnChange: Boolean) {
        mNotifyOnChange = notifyOnChange
        if (mNotifyOnChange) notifyDataSetChanged()
    }

    override fun getGroupView(
        groupPosition: Int,
        isExpanded: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        // TODO: Implement this method
        var view: View? = null
        var holder: LuaTable<String?, View?>? = null
        if (convertView == null) {
            try {
                holder = LuaTable<String?, View?>(L)
                view = loadlayout.call(mGroupLayout, holder, AbsListView::class.java)
                view!!.setTag(holder)
            } catch (e: LuaException) {
                return View(mContext.context)
            }
        } else {
            view = convertView
            holder = view.getTag() as LuaTable<String?, View?>?
        }

        val hm = mGroupData!!.get(groupPosition + 1)

        if (hm == null) {
            Log.i("lua", groupPosition.toString() + " is null")
            return view
        }


        val sets = hm.entries
        for (entry in sets) {
            try {
                val key = entry.key
                val value: Any = entry.value!!
                val obj = holder!!.get(key)
                if (obj != null) {
                    setHelper(obj, value)
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
                    anim = mAnimationUtil!!.call()
                    mAnimCache.put(convertView, anim)
                } catch (e: Exception) {
                    Log.i("lua", e.message!!)
                }
            }
            if (anim != null) {
                view.clearAnimation()
                view.startAnimation(anim)
            }
        }
        return view
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        // TODO: Implement this method
        var view: View? = null
        var holder: LuaTable<String?, View?>? = null
        if (convertView == null) {
            try {
                holder = LuaTable<String?, View?>(L)
                view = loadlayout.call(mChildLayout, holder, AbsListView::class.java)
                view!!.setTag(holder)
            } catch (e: LuaException) {
                return View(mContext.context)
            }
        } else {
            view = convertView
            holder = view.getTag() as LuaTable<String?, View?>?
        }

        val hm = mChildData!!.get(groupPosition + 1)!!.get(childPosition + 1)

        if (hm == null) {
            Log.i("lua", childPosition.toString() + " is null")
            return view
        }

        val sets = hm.entries
        for (entry in sets) {
            try {
                val key = entry.key
                val value: Any = entry.value!!
                val obj = holder!!.get(key)
                if (obj != null) {
                    setHelper(obj, value)
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
                    anim = mAnimationUtil!!.call()
                    mAnimCache.put(convertView, anim)
                } catch (e: Exception) {
                    Log.i("lua", e.message!!)
                }
            }
            if (anim != null) {
                view.clearAnimation()
                view.startAnimation(anim)
            }
        }
        return view
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        // TODO: Implement this method
        return false
    }

    private fun setFields(view: View, fields: LuaTable<String?, Any?>) {
        val sets = fields.entries
        for (entry2 in sets) {
            try {
                val key2: String = entry2.key!!
                val value2: Any = entry2.value!!
                if (key2.equals("src", ignoreCase = true)) setHelper(view, value2)
                else javaSetter(view, key2, value2)
            } catch (e2: Exception) {
                Log.i("lua", e2.message!!)
            }
        }
    }

    private fun setHelper(view: View, value: Any) {
        if (value is LuaTable<*, *>) {
            setFields(view, value as LuaTable<String?, Any?>)
        } else if (view is TextView) {
            if (value is CharSequence) view.setText(value)
            else view.setText(value.toString())
        } else if (view is ImageView) {
            try {
                if (value is Bitmap) view.setImageBitmap(value)
                else if (value is String) view.setImageDrawable(
                    AsyncLoader().getBitmap(mContext, value)
                )
                else if (value is Drawable) view.setImageDrawable(value)
                else if (value is Number) view.setImageResource(value.toInt())
            } catch (e: Exception) {
                Log.i("lua", e.message!!)
            }
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

    inner class GroupItem(val data: LuaTable<Int?, LuaTable<String?, Any?>?>) {
        @Throws(Exception::class)
        fun add(item: LuaTable<String?, Any?>?) {
            data.put(data.length() + 1, item)
            if (mNotifyOnChange) notifyDataSetChanged()
        }

        @Throws(Exception::class)
        fun insert(position: Int, item: LuaTable<String?, Any?>?) {
            insert.call(this.data, position + 1, item)
            if (mNotifyOnChange) notifyDataSetChanged()
        }

        @Throws(Exception::class)
        fun remove(position: Int) {
            remove.call(this.data, position + 1)
            if (mNotifyOnChange) notifyDataSetChanged()
        }

        fun clear() {
            data.clear()
            if (mNotifyOnChange) notifyDataSetChanged()
        }
    }

    private inner class AsyncLoader : Thread() {
        private var mPath: String? = null

        private var mContext: LuaContext? = null

        @Throws(IOException::class)
        fun getBitmap(context: LuaContext, path: String): Drawable? {
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

            return mDraw
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
