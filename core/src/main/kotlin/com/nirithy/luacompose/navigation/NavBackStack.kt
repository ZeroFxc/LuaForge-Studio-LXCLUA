package com.nirithy.luacompose.navigation

/**
 * 导航回退栈包装类，实现 MutableList<Any>
 * 修改时触发 onChanged 回调，使 NavDisplay 感知到变化。
 *
 * Lua 端用法：
 *   backStack.add("Detail")           -- 导航到 Detail
 *   backStack.removeAt(backStack.size() - 1)  -- 返回上一页
 *   local count = backStack.size()    -- 获取栈大小
 *
 * 注意：Lua 中必须使用 . 语法而非 : 语法，因为这是 Java 对象。
 */
class NavBackStack(
    initialKeys: List<Any>,
    private val onChanged: () -> Unit
) : MutableList<Any> {

    private val inner: ArrayList<Any> = ArrayList(initialKeys)

    // ===== MutableList 写操作（触发 onChanged） =====

    override fun add(element: Any): Boolean {
        val result = inner.add(element)
        onChanged()
        return result
    }

    override fun add(index: Int, element: Any) {
        inner.add(index, element)
        onChanged()
    }

    override fun addAll(elements: Collection<Any>): Boolean {
        val result = inner.addAll(elements)
        if (result) onChanged()
        return result
    }

    override fun addAll(index: Int, elements: Collection<Any>): Boolean {
        val result = inner.addAll(index, elements)
        if (result) onChanged()
        return result
    }

    override fun removeAt(index: Int): Any {
        if (inner.size <= 1) {
            return if (inner.isNotEmpty()) inner[0] else error("backStack is empty")
        }
        val removed = inner.removeAt(index)
        onChanged()
        return removed
    }

    override fun remove(element: Any): Boolean {
        if (inner.size <= 1) return false
        val result = inner.remove(element)
        if (result) onChanged()
        return result
    }

    override fun removeAll(elements: Collection<Any>): Boolean {
        val result = inner.removeAll(elements.toSet())
        if (result) onChanged()
        return result
    }

    override fun retainAll(elements: Collection<Any>): Boolean {
        val result = inner.retainAll(elements.toSet())
        if (result) onChanged()
        return result
    }

    override fun set(index: Int, element: Any): Any {
        val old = inner.set(index, element)
        onChanged()
        return old
    }

    override fun clear() {
        if (inner.size > 1) {
            val first = inner.firstOrNull()
            inner.clear()
            if (first != null) inner.add(first)
            onChanged()
        }
    }

    // ===== List<Any> 读操作（委托给 inner） =====

    override val size: Int get() = inner.size
    override fun get(index: Int): Any = inner[index]
    override fun isEmpty(): Boolean = inner.isEmpty()
    override fun contains(element: Any): Boolean = inner.contains(element)
    override fun containsAll(elements: Collection<Any>): Boolean = inner.containsAll(elements)
    override fun indexOf(element: Any): Int = inner.indexOf(element)
    override fun lastIndexOf(element: Any): Int = inner.lastIndexOf(element)
    override fun iterator(): MutableIterator<Any> = inner.iterator()
    override fun listIterator(): MutableListIterator<Any> = inner.listIterator()
    override fun listIterator(index: Int): MutableListIterator<Any> = inner.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<Any> = inner.subList(fromIndex, toIndex)
}