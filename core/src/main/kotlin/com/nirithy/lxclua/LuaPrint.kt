package com.nirithy.lxclua

import com.luajava.JavaFunction
import com.luajava.LuaException
import com.luajava.LuaState

class LuaPrint(private val mLuaContext: LuaContext, L: LuaState?) : JavaFunction(L) {
    private val output = StringBuilder()

    @Throws(LuaException::class)
    override fun execute(): Int {
        if (L.getTop() < 2) {
            mLuaContext.sendMsg("")
            return 0
        }
        for (i in 2..L.getTop()) {
            val type = L.type(i)
            var `val`: String? = null
            val stype = L.typeName(type)
            if (stype == "userdata") {
                val obj = L.toJavaObject(i)
                if (obj != null) `val` = obj.toString()
            } else if (stype == "boolean") {
                `val` = if (L.toBoolean(i)) "true" else "false"
            } else {
                `val` = L.LtoString(i)
            }
            if (`val` == null) `val` = stype
            output.append("\t")
            output.append(`val`)
            output.append("\t")
        }
        mLuaContext.sendMsg(output.toString().substring(1, output.length - 1))
        output.setLength(0)
        return 0
    }
}

