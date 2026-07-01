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
import android.widget.ArrayListAdapter
import android.widget.ImageView
import android.widget.TextView
import com.luajava.LuaException
import com.luajava.LuaObject
import com.luajava.LuaState
import java.io.IOException
import java.util.Locale

class LuaArrayAdapter @JvmOverloads constructor(
    private val mContext: LuaContext,
    private val mResource: LuaObject?,
    objects: Array<Any?>? = arrayOfNulls<Any?>(0)
) : ArrayListAdapter<Any?>(
    mContext.context!!, 0, objects
) {
    private val mRes: Resources

    private val L: LuaState

    private val loadlayout: LuaObject

    var animation: Animation? = null

    private var mDraw: Drawable? = null

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        // TODO: Implement this method
        return getView(position, convertView, parent)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        // TODO: Implement this method
        var view: View? = null
        var holder: LuaObject? = null
        if (convertView == null) {
            L.newTable()
            holder = L.getLuaObject(-1)
            L.pop(1)
            try {
                view = loadlayout.call(mResource, holder, AbsListView::class.java) as View?
            } catch (e: LuaException) {
                return View(mContext.context)
            }
        } else {
            view = convertView
        }
        setHelper(view, getItem(position)!!)
        if (this.animation != null) view!!.startAnimation(this.animation)
        return view
    }


    private fun setHelper(view: View?, value: Any) {
        if (view is TextView) {
            if (value is CharSequence) view.setText(value)
            else view.setText(value.toString())
        } else if (view is ImageView) {
            try {
                var drawable: Drawable? = null
                if (value is Bitmap) drawable = BitmapDrawable(mRes, value)
                else if (value is String) drawable =
                    AsyncLoader().getBitmap(mContext, value)
                else if (value is Drawable) drawable = value
                else if (value is Number) drawable = mRes.getDrawable(value.toInt())

                view.setImageDrawable(drawable)
                if (drawable is BitmapDrawable) {
                    val bmp = drawable.getBitmap()
                    var w = bmp.width
                    var h = bmp.height
                    if (view.getScaleType() == ImageView.ScaleType.FIT_XY) {
                        h = (mContext.width * (h.toFloat()) / (w.toFloat())).toInt()
                        w = mContext.width
                        view.setLayoutParams(ViewGroup.LayoutParams(w, h))
                    }
                } else if (drawable is LoadingDrawable) {
                    var w = mContext.width
                    val h = w / 4
                    w = mContext.width
                    view.setLayoutParams(ViewGroup.LayoutParams(w, h))
                } else if (drawable is Drawable) {
                    val rect = drawable.getBounds()
                    var w = rect.width()
                    var h = rect.height()

                    if (view.getScaleType() == ImageView.ScaleType.FIT_XY) {
                        h = (mContext.width * (h.toFloat()) / (w.toFloat())).toInt()
                        w = mContext.width
                        view.setLayoutParams(ViewGroup.LayoutParams(w, h))
                    }
                }
            } catch (e: Exception) {
                Log.i("lua", e.message!!)
            }
        }
    }

    private val mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            notifyDataSetChanged()
        }
    }
    private val loaded = HashMap<String?, Boolean?>()

    init {
        mRes = mContext.context!!.resources

        L = mContext.luaState!!
        loadlayout = L.getLuaObject("loadlayout")
        L.newTable()
        loadlayout.call(mResource, L.getLuaObject(-1), AbsListView::class.java)
        L.pop(1)
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
            } catch (e: Exception) {
                e.printStackTrace()
                mContext!!.sendError("AsyncLoader Error", e)
            }
        }
    }
}
