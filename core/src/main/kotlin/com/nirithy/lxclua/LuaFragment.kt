package com.nirithy.lxclua

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.luajava.LuaObject
import com.luajava.LuaTable

@SuppressLint("ValidFragment")
class LuaFragment : Fragment {
    private var mLayout: LuaTable<*, *>? = null
    private var mLoadLayout: LuaObject? = null
    private var mView: View? = null

    constructor(layout: LuaTable<*, *>?) {
        mLoadLayout = null
        mLayout = layout
    }

    constructor(view: View?) {
        mLayout = null
        mLoadLayout = null
        mView = view
    }

    constructor() {
        mLayout = null
        mLoadLayout = null
    }


    fun setLayout(layout: LuaTable<*, *>?) {
        mLayout = layout
        mView = null
    }

    fun setLayout(layout: View?) {
        mView = layout
        mLayout = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            if (mView != null) return mView
            if (mLayout != null) return ((mLayout!!.getLuaState().getLuaObject("require")
                .call("loadlayout")) as LuaObject).call(mLayout) as View?
            return TextView(getActivity())
        } catch (e: Exception) {
            throw IllegalArgumentException(e.message)
        }
    }

    companion object {
        fun newInstance(luaTable: LuaTable<*, *>?): LuaFragment {
            return LuaFragment(luaTable)
        }

        fun newInstance(view: View?): LuaFragment {
            return LuaFragment(view)
        }
    }
}