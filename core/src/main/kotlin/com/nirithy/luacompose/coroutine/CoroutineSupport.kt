package com.nirithy.luacompose.coroutine

import com.nirithy.luacompose.logW
import com.luajava.LuaObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "CoroutineSupport"

/**
 * Lua 协程作用域，支持 launch、delay 和 launchAfter
 * 由 compose.rememberCoroutineScope() 创建
 *
 * 注意：Lua 中必须使用 . 语法（非 : 语法），因为这是 Java 对象
 *   scope.launch(function() ... end)      -- 正确
 *   scope.launchAfter(3000, function() ... end)  -- 延迟3秒后执行
 */
class LuaCoroutineScope {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    /**
     * 启动协程
     * Lua: scope.launch(function() ... end)
     */
    fun launch(block: LuaObject): Job {
        return scope.launch {
            try {
                block.call()
            } catch (e: Exception) {
                logW(TAG) { "[launch] 协程异常: ${e.message}" }
            }
        }
    }

    /**
     * 延迟指定毫秒后执行 block
     * Lua: scope.launchAfter(3000, function() ... end)
     */
    fun launchAfter(ms: Long, block: LuaObject): Job {
        return scope.launch {
            delay(ms)
            try {
                block.call()
            } catch (e: Exception) {
                logW(TAG) { "[launchAfter] 协程异常: ${e.message}" }
            }
        }
    }

    /** 延迟（毫秒），仅限 Kotlin 协程内部调用，Lua 请使用 launchAfter */
    @Deprecated("Lua 请使用 scope.launchAfter(ms, block) 代替")
    suspend fun delay(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }

    fun cancel() {
        scope.coroutineContext[Job]?.cancel()
    }
}