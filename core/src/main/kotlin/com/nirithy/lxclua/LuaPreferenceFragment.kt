package com.nirithy.lxclua

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import com.luajava.LuaException
import com.luajava.LuaJavaAPI
import com.luajava.LuaTable
import com.luajava.LuaTable.LuaEntry

/**
 * Created by nirenr on 2018/08/05 0005.
 */
@SuppressLint("ValidFragment")
@Suppress("UNCHECKED_CAST")
class LuaPreferenceFragment(preferences: LuaTable<*, *>) : PreferenceFragment(),
    Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    private var mPreferences: LuaTable<Int?, LuaTable<*, *>?>
    private var mOnPreferenceChangeListener: Preference.OnPreferenceChangeListener? = null
    private var mOnPreferenceClickListener: Preference.OnPreferenceClickListener? = null

    init {
        mPreferences = preferences as LuaTable<Int?, LuaTable<*, *>?>
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getPreferenceManager().setStorageDeviceProtected()
        }
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getActivity()))
        //addPreferencesFromResource(R.xml.preference_screen);
        init(mPreferences)
    }

    fun setPreference(preferences: LuaTable<Int?, LuaTable<*, *>?>) {
        mPreferences = preferences
    }

    private fun init(preferences: LuaTable<Int?, LuaTable<*, *>?>) {
        val ps = getPreferenceScreen()
        val len = preferences.length()
        val L = preferences.getLuaState()
        for (i in 1..len) {
            val p = preferences.get(i)
            try {
                val cls = p!!.getI(1)
                require(!cls.isNil()) { "Fist value Must be a Class<Preference>, checked import package." }
                @Suppress("UNCHECKED_CAST")
                val pf = cls.call(getActivity()) as Preference
                @Suppress("UNCHECKED_CAST")
                for (et in (p as Map<*, *>).entries) {
                    val key: Any? = et.key
                    if (key is String) {
                        try {
                            LuaJavaAPI.javaSetter(L, pf, key, et.value)
                        } catch (e: LuaException) {
                            e.printStackTrace()
                        }
                    }
                }
                pf.setOnPreferenceChangeListener(this)
                pf.setOnPreferenceClickListener(this)
                ps.addPreference(pf)
            } catch (e: Exception) {
                L.getContext().sendError("LuaPreferenceFragment", e)
            }
        }
    }

    fun setOnPreferenceChangeListener(listener: Preference.OnPreferenceChangeListener?) {
        mOnPreferenceChangeListener = listener
    }

    fun setOnPreferenceClickListener(listener: Preference.OnPreferenceClickListener?) {
        mOnPreferenceClickListener = listener
    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
        if (mOnPreferenceChangeListener != null) return mOnPreferenceChangeListener!!.onPreferenceChange(
            preference,
            newValue
        )
        return true
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        if (mOnPreferenceClickListener != null) return mOnPreferenceClickListener!!.onPreferenceClick(
            preference
        )
        return false
    }
}
