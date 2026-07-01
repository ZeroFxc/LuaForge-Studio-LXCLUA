package com.nirithy.lxclua.util

import kotlin.concurrent.Volatile


class VolatileBox<T> {
    @Volatile
    private var mValue: T? = null

    constructor()

    constructor(value: T?) {
        set(value)
    }

    fun get(): T? {
        return mValue
    }

    fun set(value: T?) {
        mValue = value
    }

    val isNull: Boolean
        get() = mValue == null

    fun notNull(): Boolean {
        return mValue != null
    }

    fun setAndNotify(value: T?) {
        mValue = value
        synchronized(this) {
            (this as Object).notify()
        }
    }

    fun blockedGet(): T? {
        synchronized(this) {
            try {
                (this as Object).wait()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
        return mValue
    }

    fun blockedGetOrThrow(exception: Class<out RuntimeException?>): T? {
        synchronized(this) {
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
}
