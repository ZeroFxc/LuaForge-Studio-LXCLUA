package com.nirithy.lxclua

import android.database.DataSetObservable
import android.database.DataSetObserver
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.viewpager.widget.PagerAdapter

@Keep
class LuaPagerAdapter : PagerAdapter {
    private val mObservable = DataSetObservable()
    var data: MutableList<View>
    var titles: MutableList<String?>? = null

    constructor(list: MutableList<View>) {
        this.data = list
    }

    constructor(list: MutableList<View>, titles: MutableList<String?>?) {
        this.data = list
        this.titles = titles
    }

    override fun getPageTitle(position: Int): CharSequence? {
        if (titles != null && titles!!.size >= 0) {
            return titles!!.get(position)
        } else {
            return "No Title"
        }
    }

    override fun destroyItem(viewGroup: ViewGroup, i: Int, obj: Any) {
        viewGroup.removeView(this.data.get(i))
    }

    override fun getCount(): Int {
        return this.data.size
    }

    override fun instantiateItem(viewGroup: ViewGroup, i: Int): Any {
        viewGroup.addView(this.data.get(i))
        return this.data.get(i)
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun notifyDataSetChanged() {
        mObservable.notifyChanged()
    }

    override fun registerDataSetObserver(observer: DataSetObserver) {
        mObservable.registerObserver(observer)
    }

    override fun unregisterDataSetObserver(observer: DataSetObserver) {
        mObservable.unregisterObserver(observer)
    }

    fun add(view: View?) {
        data.add(view!!)
    }

    fun insert(index: Int, view: View?) {
        data.add(index, view!!)
    }

    fun remove(index: Int): View? {
        return data.removeAt(index)
    }

    fun remove(view: View?): Boolean {
        return data.remove(view)
    }

    fun getItem(index: Int): View? {
        return data.get(index)
    }
}
