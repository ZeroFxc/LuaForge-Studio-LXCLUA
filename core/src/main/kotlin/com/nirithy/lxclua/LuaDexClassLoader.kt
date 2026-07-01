package com.nirithy.lxclua

import dalvik.system.DexClassLoader

class LuaDexClassLoader(
    val dexPath: String?,
    optimizedDirectory: String?,
    libraryPath: String?,
    parent: ClassLoader?
) : DexClassLoader(
    dexPath, optimizedDirectory, libraryPath, parent
) {
    private val classCache = HashMap<String?, Class<*>?>()

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String?): Class<*>? {
        // TODO: Implement this method
        var cls = classCache.get(name)
        if (cls == null) {
            cls = super.findClass(name)
            classCache.put(name, cls)
        }
        return cls
    }
}

