package com.nirithy.lxclua

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.graphics.Movie
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import com.luajava.LuaException
import com.luajava.LuaMetaTable
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

/**
 * Created by Administrator on 2017/04/24 0024.
 */
@SuppressLint("UseSparseArrays")
class LuaResources
/**
 * Create a new Resources object on top of an existing set of assets in an
 * AssetManager.
 *
 * @param assets  Previously created AssetManager.
 * @param metrics Current display metrics to consider when
 * selecting/computing resource values.
 * @param config  Desired device configuration to consider when
 */
    (assets: AssetManager?, metrics: DisplayMetrics?, config: Configuration?) :
    Resources(assets, metrics, config), LuaMetaTable {
    private val mTextMap = HashMap<Int?, String?>()
    private val mDrawableMap = HashMap<Int?, Drawable?>()
    private val mColorMap: HashMap<Int?, Int?> = HashMap<Int?, Int?>()
    private val mTextArrayMap = HashMap<Int?, Array<String?>?>()
    private val mIntArrayMap = HashMap<Int?, IntArray?>()
    private val mTypefaceMap = HashMap<Int?, Typeface?>()
    private val mIntMap: HashMap<Int?, Int?> = HashMap<Int?, Int?>()
    private val mFloatMap = HashMap<Int?, Float?>()
    private val mBooleanMap: HashMap<Int?, Boolean?> = HashMap<Int?, Boolean?>()

    private val mIdMap = HashMap<String?, Int?>()
    private var mSuperResources: Resources? = null

    fun setText(id: Int, text: String?) {
        mTextMap.put(id, text)
    }

    fun setString(id: Int, text: String?) {
        mTextMap.put(id, text)
    }

    fun setTextArray(id: Int, text: Array<String?>?) {
        mTextArrayMap.put(id, text)
    }

    fun setStringArray(id: Int, text: Array<String?>?) {
        mTextArrayMap.put(id, text)
    }

    fun setIntArray(id: Int, arr: IntArray?) {
        mIntArrayMap.put(id, arr)
    }

    fun setBoolean(id: Int, bool: Boolean?) {
        mBooleanMap.put(id, bool)
    }

    fun setDrawable(id: Int, drawable: Drawable?) {
        mDrawableMap.put(id, drawable)
    }

    fun setColor(id: Int, color: Int) {
        mColorMap.put(id, color)
    }

    @Throws(NotFoundException::class)
    fun setFont(id: Int, font: Typeface?) {
        mTypefaceMap.put(id, font)
    }

    @Throws(NotFoundException::class)
    override fun getText(id: Int): CharSequence {
        val text = mTextMap.get(id)
        if (text != null) return text
        return mSuperResources!!.getText(id)
    }

    override fun getText(id: Int, def: CharSequence?): CharSequence? {
        val text = mTextMap.get(id)
        if (text != null) return text
        return mSuperResources!!.getText(id, def)
    }

    @Throws(NotFoundException::class)
    override fun getDrawable(id: Int): Drawable? {
        val drawable = mDrawableMap.get(id)
        if (drawable != null) return drawable
        return mSuperResources!!.getDrawable(id)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Throws(NotFoundException::class)
    override fun getDrawable(id: Int, theme: Theme?): Drawable? {
        val drawable = mDrawableMap.get(id)
        if (drawable != null) return drawable
        return mSuperResources!!.getDrawable(id, theme)
    }

    @Throws(NotFoundException::class)
    override fun getColor(id: Int): Int {
        val color = mColorMap.get(id)
        if (color != null) return color
        return mSuperResources!!.getColor(id)
    }

    @Throws(LuaException::class)
    override fun __call(vararg arg: Any?): Any? {
        return null
    }

    fun put(key: String?, value: Any): Int {
        if (value == null) throw NullPointerException()
        val id: Int = mId++
        if (value is Drawable) {
            setDrawable(id, value)
        } else if (value is String) {
            setText(id, value)
        } else if (value is Array<*>) {
            @Suppress("UNCHECKED_CAST")
            setTextArray(id, value as Array<String?>)
        } else if (value is Number) {
            setColor(id, value.toInt())
        } else if (value is IntArray) {
            setIntArray(id, value)
        } else {
            throw IllegalArgumentException()
        }
        mIdMap.put(key, id)
        return id
    }

    fun get(key: String?): Any? {
        return mIdMap.get(key)
    }

    override fun __index(key: String?): Any? {
        return get(key)
    }

    override fun __newIndex(key: String?, value: Any) {
        put(key, value)
    }

    fun setSuperResources(superRes: Resources) {
        mSuperResources = superRes
    }

    @Throws(NotFoundException::class)
    override fun getBoolean(id: Int): Boolean {
        val bool = mBooleanMap.get(id)
        if (bool != null) return bool
        return mSuperResources!!.getBoolean(id)
    }

    @Throws(NotFoundException::class)
    override fun getTextArray(id: Int): Array<CharSequence?> {
        val text = mTextArrayMap.get(id)
        if (text != null) return text as Array<CharSequence?>
        return mSuperResources!!.getTextArray(id)
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Throws(NotFoundException::class)
    override fun getColorStateList(id: Int, theme: Theme?): ColorStateList {
        return mSuperResources!!.getColorStateList(id, theme)
    }

    override fun getConfiguration(): Configuration? {
        return mSuperResources!!.getConfiguration()
    }

    override fun getDisplayMetrics(): DisplayMetrics? {
        return mSuperResources!!.getDisplayMetrics()
    }


    @Throws(NotFoundException::class)
    override fun getDimension(id: Int): Float {
        return mSuperResources!!.getDimension(id)
    }

    override fun getFraction(id: Int, base: Int, pbase: Int): Float {
        return mSuperResources!!.getFraction(id, base, pbase)
    }

    @Throws(NotFoundException::class)
    override fun getDimensionPixelOffset(id: Int): Int {
        return mSuperResources!!.getDimensionPixelOffset(id)
    }

    @Throws(NotFoundException::class)
    override fun getDimensionPixelSize(id: Int): Int {
        return mSuperResources!!.getDimensionPixelSize(id)
    }

    override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
        return mSuperResources!!.getIdentifier(name, defType, defPackage)
    }

    @Throws(NotFoundException::class)
    override fun getInteger(id: Int): Int {
        val i = mIntMap.get(id)
        if (i != null) return i
        return mSuperResources!!.getInteger(id)
    }

    @Throws(NotFoundException::class)
    override fun getColorStateList(id: Int): ColorStateList {
        return mSuperResources!!.getColorStateList(id)
    }

    @Throws(NotFoundException::class)
    override fun getDrawableForDensity(id: Int, density: Int): Drawable? {
        return mSuperResources!!.getDrawableForDensity(id, density)
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Throws(NotFoundException::class)
    override fun getColor(id: Int, theme: Theme?): Int {
        val color = mColorMap.get(id)
        if (color != null) return color
        return mSuperResources!!.getColor(id, theme)
    }

    @Throws(NotFoundException::class)
    override fun getIntArray(id: Int): IntArray {
        val arr = mIntArrayMap.get(id)
        if (arr != null) return arr
        return mSuperResources!!.getIntArray(id)
    }

    @Throws(NotFoundException::class)
    override fun getMovie(id: Int): Movie? {
        return mSuperResources!!.getMovie(id)
    }

    @Throws(NotFoundException::class)
    override fun getResourceEntryName(resid: Int): String? {
        return mSuperResources!!.getResourceEntryName(resid)
    }

    @Throws(NotFoundException::class)
    override fun getResourceName(resid: Int): String? {
        return mSuperResources!!.getResourceName(resid)
    }

    @Throws(NotFoundException::class)
    override fun getResourcePackageName(resid: Int): String? {
        return mSuperResources!!.getResourcePackageName(resid)
    }

    @Throws(NotFoundException::class)
    override fun getResourceTypeName(resid: Int): String? {
        return mSuperResources!!.getResourceTypeName(resid)
    }

    @Throws(NotFoundException::class)
    override fun getString(id: Int): String {
        return getText(id).toString()
    }

    @Throws(NotFoundException::class)
    override fun getString(id: Int, vararg formatArgs: Any?): String {
        return String.format(getString(id), *formatArgs)
    }

    @Throws(NotFoundException::class)
    override fun getStringArray(id: Int): Array<String?> {
        val arr = mTextArrayMap.get(id)
        if (arr != null) return arr
        return mSuperResources!!.getStringArray(id)
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Throws(NotFoundException::class)
    override fun getFont(id: Int): Typeface {
        val font = mTypefaceMap.get(id)
        if (font != null) return font
        return mSuperResources!!.getFont(id)
    }

    @Throws(NotFoundException::class)
    override fun getValue(id: Int, outValue: TypedValue?, resolveRefs: Boolean) {
        mSuperResources!!.getValue(id, outValue, resolveRefs)
    }

    @Throws(NotFoundException::class)
    override fun getValue(name: String?, outValue: TypedValue?, resolveRefs: Boolean) {
        mSuperResources!!.getValue(name, outValue, resolveRefs)
    }

    @Throws(NotFoundException::class)
    override fun getAnimation(id: Int): XmlResourceParser {
        return mSuperResources!!.getAnimation(id)
    }

    @Throws(NotFoundException::class)
    override fun getValueForDensity(
        id: Int,
        density: Int,
        outValue: TypedValue?,
        resolveRefs: Boolean
    ) {
        mSuperResources!!.getValueForDensity(id, density, outValue, resolveRefs)
    }

    @Throws(NotFoundException::class)
    override fun getLayout(id: Int): XmlResourceParser {
        return mSuperResources!!.getLayout(id)
    }

    @Throws(NotFoundException::class)
    override fun getXml(id: Int): XmlResourceParser {
        return mSuperResources!!.getXml(id)
    }


    @Throws(NotFoundException::class)
    override fun getQuantityText(id: Int, quantity: Int): CharSequence {
        return mSuperResources!!.getQuantityText(id, quantity)
    }

    override fun getDrawableForDensity(id: Int, density: Int, theme: Theme?): Drawable? {
        return mSuperResources!!.getDrawableForDensity(id, density, theme)
    }


    @Throws(NotFoundException::class)
    override fun getQuantityString(id: Int, quantity: Int): String {
        return mSuperResources!!.getQuantityString(id, quantity)
    }

    @Throws(NotFoundException::class)
    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String {
        return mSuperResources!!.getQuantityString(id, quantity, *formatArgs)
    }

    @Throws(NotFoundException::class)
    override fun openRawResourceFd(id: Int): AssetFileDescriptor? {
        return mSuperResources!!.openRawResourceFd(id)
    }

    @Throws(NotFoundException::class)
    override fun openRawResource(id: Int): InputStream {
        return mSuperResources!!.openRawResource(id)
    }

    @Throws(NotFoundException::class)
    override fun openRawResource(id: Int, value: TypedValue?): InputStream {
        return mSuperResources!!.openRawResource(id, value)
    }

    override fun obtainAttributes(set: AttributeSet?, attrs: IntArray?): TypedArray? {
        return mSuperResources!!.obtainAttributes(set, attrs)
    }

    @Throws(NotFoundException::class)
    override fun obtainTypedArray(id: Int): TypedArray {
        return mSuperResources!!.obtainTypedArray(id)
    }

    @Throws(XmlPullParserException::class)
    override fun parseBundleExtra(tagName: String?, attrs: AttributeSet?, outBundle: Bundle?) {
        mSuperResources!!.parseBundleExtra(tagName, attrs, outBundle)
    }

    @Throws(IOException::class, XmlPullParserException::class)
    override fun parseBundleExtras(parser: XmlResourceParser?, outBundle: Bundle?) {
        mSuperResources!!.parseBundleExtras(parser, outBundle)
    }

    companion object {
        private var mId = 0x7f050000
    }
}
