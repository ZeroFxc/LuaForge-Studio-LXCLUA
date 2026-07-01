package com.nirithy.lxclua

interface LuaGcable {
    fun gc()

    val isGc: Boolean
}
