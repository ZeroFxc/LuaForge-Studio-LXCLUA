package com.nirithy.luacompose

import android.util.Log
import com.luaforge.studio.lxclua.core.BuildConfig

/**
 * 条件日志工具（等价于 C 的 #ifdef DEBUG）
 *
 * 所有函数均为 inline，且以 BuildConfig.DEBUG（编译期常量）作为守卫。
 * - DEBUG 构建：正常输出到 logcat
 * - RELEASE 构建：R8 将整个调用点完全消除，零字节码开销，零字符串分配
 *
 * 使用方式（替换原有 import android.util.Log）：
 *   import com.nirithy.luacompose.logD
 *   import com.nirithy.luacompose.logI
 *   import com.nirithy.luacompose.logW
 *   import com.nirithy.luacompose.logE
 *   import com.nirithy.luacompose.logV
 *
 *   logD(TAG) { "详细诊断信息: $value" }   // lambda 懒求值，release 时 lambda 也不会创建
 *
 * 注意：使用 lambda 形式确保字符串拼接在 release 时完全不执行。
 */

@Suppress("NOTHING_TO_INLINE")
inline fun logV(tag: String, msg: () -> String) {
    if (BuildConfig.DEBUG) Log.v(tag, msg())
}

@Suppress("NOTHING_TO_INLINE")
inline fun logD(tag: String, msg: () -> String) {
    if (BuildConfig.DEBUG) Log.d(tag, msg())
}

@Suppress("NOTHING_TO_INLINE")
inline fun logI(tag: String, msg: () -> String) {
    if (BuildConfig.DEBUG) Log.i(tag, msg())
}

@Suppress("NOTHING_TO_INLINE")
inline fun logW(tag: String, msg: () -> String) {
    if (BuildConfig.DEBUG) Log.w(tag, msg())
}

@Suppress("NOTHING_TO_INLINE")
inline fun logW(tag: String, msg: () -> String, tr: Throwable) {
    if (BuildConfig.DEBUG) Log.w(tag, msg(), tr)
}

@Suppress("NOTHING_TO_INLINE")
inline fun logE(tag: String, msg: () -> String) {
    if (BuildConfig.DEBUG) Log.e(tag, msg())
}

@Suppress("NOTHING_TO_INLINE")
inline fun logE(tag: String, msg: () -> String, tr: Throwable) {
    if (BuildConfig.DEBUG) Log.e(tag, msg(), tr)
}
