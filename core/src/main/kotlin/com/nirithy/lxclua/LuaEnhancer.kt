package com.nirithy.lxclua

import com.android.cglib.proxy.Enhancer
import com.android.cglib.proxy.EnhancerInterface
import com.android.cglib.proxy.MethodFilter
import com.android.cglib.proxy.MethodInterceptor

/**
 * Created by nirenr on 2018/12/19.
 */
class LuaEnhancer(cls: Class<*>?) {
    private val mEnhancer: Enhancer

    constructor(cls: String) : this(Class.forName(cls))

    init {
        mEnhancer = Enhancer(LuaApplication.instance)
        mEnhancer.setSuperclass(cls)
    }

    fun setInterceptor(obj: EnhancerInterface, interceptor: MethodInterceptor?) {
        obj.setMethodInterceptor_Enhancer(interceptor)
    }

    fun create(): Class<*>? {
        try {
            return mEnhancer.create()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun create(filer: MethodFilter?): Class<*>? {
        try {
            mEnhancer.setMethodFilter(filer)
            return mEnhancer.create()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
