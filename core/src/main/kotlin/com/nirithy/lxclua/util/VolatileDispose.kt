package com.nirithy.lxclua.util

import kotlin.concurrent.Volatile


class VolatileDispose<T> {
    @Volatile
    private var mValue: T? = null

    fun blockedGet(): T? {
        synchronized(this) {
            if (mValue != null) {
                return mValue
            }
            try {
                (this as Object).wait(1000)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
        return mValue
    }

    fun blockedGetOrThrow(exception: Class<out RuntimeException?>): T? {
        synchronized(this) {
            if (mValue != null) {
                return mValue
            }
            try {
                (this as Object).wait()
            } catch (e: InterruptedException) {
                try {
                    throw exception.newInstance()
                } catch (e1: InstantiationException) {
                    throw RuntimeException(e1)
                } catch (e1: IllegalAccessException) {
                    throw RuntimeException(e1)
                }
            }
        }
        return mValue
    }

    fun setAndNotify(value: T?) {
        synchronized(this) {
            mValue = value
            (this as Object).notify()
        }
    }
}