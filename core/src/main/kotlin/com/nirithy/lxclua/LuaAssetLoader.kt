package com.nirithy.lxclua

import android.content.Context
import com.luajava.JavaFunction
import com.luajava.LuaException
import com.luajava.LuaState
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class LuaAssetLoader(luaContext: LuaContext, L: LuaState?) : JavaFunction(L) {
    private val mContext: Context

    init {
        mContext = luaContext.context!!
    }

    @Throws(LuaException::class)
    override fun execute(): Int {
        var name = L.toString(-1)
        name = name.replace('.', '/') + ".lua"
        try {
            val bytes = readAsset(name)
            val ok = L.LloadBuffer(bytes, name)
            if (ok != 0) L.pushString("\n\t" + L.toString(-1))
            return 1
        } catch (e: IOException) {
            L.pushString("\n\tno file '/assets/" + name + "'")
            return 1
        }
    }

    @Throws(IOException::class)
    fun readAsset(name: String): ByteArray {
        val am = mContext.getAssets()
        val `is` = am.open(name)
        val ret: ByteArray = readAll(`is`)
        `is`.close()
        //am.close();
        return ret
    }

    companion object {
        @Throws(IOException::class)
        private fun readAll(input: InputStream): ByteArray {
            val output = ByteArrayOutputStream(4096)
            val buffer = ByteArray(4096)
            var n = 0
            while (-1 != (input.read(buffer).also { n = it })) {
                output.write(buffer, 0, n)
            }
            val ret = output.toByteArray()
            output.close()
            return ret
        }
    }
}

