package com.nirithy.lxclua.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Rect

/**
 * Created by Administrator on 2018/03/10 0010.
 * 颜色查找器，用于在位图中查找指定颜色坐标
 */
class ColorFinder {
    private var mWidth = 0
    private var mHeight = 0
    var pixels: Array<IntArray> = emptyArray()
        private set
    private var mValues: Array<FloatArray>? = null
    private var mValue = 0f

    constructor(bitmap: String?) {
        init(BitmapFactory.decodeFile(bitmap))
    }

    constructor(bitmap: Bitmap) {
        init(bitmap)
    }

    private fun init(bitmap: Bitmap) {
        mWidth = bitmap.width
        mHeight = bitmap.height
        val pixels = IntArray(mWidth * mHeight)
        bitmap.getPixels(pixels, 0, mWidth, 0, 0, mWidth, mHeight)
        this.pixels = Array(mWidth) { IntArray(mHeight) }
        for (h in 0..<mHeight) {
            for (w in 0..<mWidth) {
                this.pixels[w][h] = pixels[h * mWidth + w]
            }
        }
    }

    /**
     * 在整个图片中查找指定颜色值
     * @param color 颜色值(ARGB格式)
     * @return 匹配的坐标，未找到返回(-1,-1)
     */
    fun find(color: Int): Point {
        for (h in 0..<mHeight) {
            for (w in 0..<mWidth) {
                if (this.pixels[w][h] == color) return Point(w, h)
            }
        }
        return Point(-1, -1)
    }

    /**
     * 在指定区域内查找指定颜色值
     * @param x,y 区域左上角
     * @param x2,y2 区域右下角
     * @param color 颜色值(ARGB格式)
     */
    fun find(x: Int, y: Int, x2: Int, y2: Int, color: Int): Point {
        for (h in y..<y2) {
            for (w in x..<x2) {
                if (this.pixels[w][h] == color) return Point(w, h)
            }
        }
        return Point(-1, -1)
    }

    /**
     * 在整个图片中查找Color对象对应的颜色
     * @param color 自定义Color对象
     */
    fun find(color: Color): Point {
        return find(color.red, color.green, color.blue)
    }

    /**
     * 在整个图片中查找指定RGB颜色
     * @param red/green/blue 各通道值
     */
    fun find(red: Int, green: Int, blue: Int): Point {
        for (h in 0..<mHeight) {
            for (w in 0..<mWidth) {
                val color = this.pixels[w][h]
                val r = color shl 8 ushr 24
                val g = color shl 16 ushr 24
                val b = color shl 24 ushr 24
                if (r == red && g == green && b == blue) return Point(w, h)
            }
        }
        return Point(-1, -1)
    }

    /**
     * 在指定区域内查找Color对象对应的颜色
     * @param p1/p2 区域起止点
     * @param color 自定义Color对象
     */
    fun find(p1: Point, p2: Point, color: Color): Point {
        return find(p1.x, p1.y, p2.x, p2.y, color.red, color.green, color.blue)
    }

    /**
     * 在指定区域内查找指定RGB颜色
     */
    fun find(x: Int, y: Int, x2: Int, y2: Int, red: Int, green: Int, blue: Int): Point {
        for (h in y..<y2) {
            for (w in x..<x2) {
                val color = this.pixels[w][h]
                val r = color shl 8 ushr 24
                val g = color shl 16 ushr 24
                val b = color shl 24 ushr 24
                if (r == red && g == green && b == blue) return Point(w, h)
            }
        }
        return Point(-1, -1)
    }

    /**
     * 在整个图片中查找颜色(带容差)
     * @param color 自定义Color对象
     * @param offset 颜色容差偏移量
     */
    fun find(color: Color, offset: Int): Point {
        return find(color.red, color.green, color.blue, offset)
    }

    /**
     * 在整个图片中查找指定RGB颜色(带容差)
     */
    fun find(red: Int, green: Int, blue: Int, offset: Int): Point {
        val r1 = red - offset
        val r2 = red + offset
        val g1 = green - offset
        val g2 = green + offset
        val b1 = blue - offset
        val b2 = blue + offset

        for (h in 0..<mHeight) {
            for (w in 0..<mWidth) {
                val color = this.pixels[w][h]
                val r = color shl 8 ushr 24
                val g = color shl 16 ushr 24
                val b = color shl 24 ushr 24
                if (r >= r1 && r <= r2 && g >= g1 && g <= g2 && b >= b1 && b <= b2) return Point(
                    w,
                    h
                )
            }
        }
        return Point(-1, -1)
    }

    /**
     * 在指定区域内查找颜色(带容差)
     * @param p1/p2 区域起止点
     * @param color 自定义Color对象
     * @param offset 颜色容差偏移量
     */
    fun find(p1: Point, p2: Point, color: Color, offset: Int): Point {
        return find(p1.x, p1.y, p2.x, p2.y, color.red, color.green, color.blue, offset)
    }

    /**
     * 在指定区域内查找指定RGB颜色(带容差)
     */
    fun find(
        x: Int,
        y: Int,
        x2: Int,
        y2: Int,
        red: Int,
        green: Int,
        blue: Int,
        offset: Int
    ): Point {
        val r1 = red - offset
        val r2 = red + offset
        val g1 = green - offset
        val g2 = green + offset
        val b1 = blue - offset
        val b2 = blue + offset

        for (h in y..<y2) {
            for (w in x..<x2) {
                val color = this.pixels[w][h]
                val r = color shl 8 ushr 24
                val g = color shl 16 ushr 24
                val b = color shl 24 ushr 24
                if (r >= r1 && r <= r2 && g >= g1 && g <= g2 && b >= b1 && b <= b2) return Point(
                    w,
                    h
                )
            }
        }
        return Point(-1, -1)
    }

    /**
     * 在指定区域内查找颜色(带容差和多点验证)
     * @param p IntArray数组，每个数组为[x,y,r,g,b,offset]格式的颜色点
     */
    fun find(
        x: Int,
        y: Int,
        x2: Int,
        y2: Int,
        red: Int,
        green: Int,
        blue: Int,
        offset: Int,
        p: Array<IntArray?>
    ): Point {
        val cp = p.mapNotNull { it?.let { arr -> ColorPoint(arr) } }.toTypedArray()
        return find(x, y, x2, y2, red, green, blue, offset, cp)
    }

    /**
     * 在指定区域内查找颜色(带容差和多点验证)
     * @param cp 颜色验证点数组
     */
    fun find(
        x: Int,
        y: Int,
        x2: Int,
        y2: Int,
        red: Int,
        green: Int,
        blue: Int,
        offset: Int,
        cp: Array<ColorPoint>
    ): Point {
        val r1 = red - offset
        val r2 = red + offset
        val g1 = green - offset
        val g2 = green + offset
        val b1 = blue - offset
        val b2 = blue + offset

        for (h in y..<y2) {
            for (w in x..<x2) {
                val color = this.pixels[w][h]
                val r = color shl 8 ushr 24
                val g = color shl 16 ushr 24
                val b = color shl 24 ushr 24
                if (r >= r1 && r <= r2 && g >= g1 && g <= g2 && b >= b1 && b <= b2) {
                    var ok = true
                    for (c in cp) {
                        if (!c.check(this.pixels, x, y)) {
                            ok = false
                            break
                        }
                    }
                    if (ok) return Point(w, h)
                }
            }
        }
        return Point(-1, -1)
    }

    /**
     * 查找文本行(基于HSV明度分析)
     * @param o 行参数
     * @return 匹配的Rect列表
     */
    fun findLine(o: Int): ArrayList<Rect?> {
        return findLine(mWidth / 2, 10, mWidth - 10, mHeight - o * 16, 0.5f, o * 8, o * 4, o)
    }

    fun findLine(n: Float, o: Int): ArrayList<Rect?> {
        return findLine(mWidth / 2, 10, mWidth - 10, mHeight - o * 16, n, o * 8, o * 4, o)
    }

    fun findLine(n: Float, h: Int, o: Int): ArrayList<Rect?> {
        if (mHeight < mWidth) return findLine(
            mWidth / 2,
            0,
            mWidth - 10,
            mHeight - h,
            n,
            h,
            o * 4,
            o
        )
        else return findLine(mWidth / 2, mWidth / 3, mWidth - 10, mWidth, n, h, o * 4, o)
    }

    fun findLine(n: Float, h: Int, w: Int, o: Int): ArrayList<Rect?> {
        if (mHeight < mWidth) return findLine(mWidth / 2, 0, mWidth - 10, mHeight - h, n, h, w, o)
        else return findLine(mWidth / 2, mWidth / 3, mWidth - 10, mWidth, n, h, w, o)
    }

    /**
     * 基于HSV明度查找文本行
     * @param x1/y1/x2/y2 搜索区域
     * @param n 明度阈值系数
     * @param h 行高
     * @param w 字宽
     * @param o 偏移量
     */
    fun findLine(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        n: Float,
        h: Int,
        w: Int,
        o: Int
    ): ArrayList<Rect?> {
        if (mValues == null) {
            mValues = Array(mWidth) { FloatArray(mHeight) }
            val hsv = FloatArray(3)
            var v = 0f
            for (y in 0..<mHeight) {
                for (x in 0..<mWidth) {
                    val color1 = this.pixels[x][y]
                    AndroidColor.colorToHSV(color1, hsv)
                    mValues!![x][y] = hsv[2]
                    v += hsv[2]
                }
            }
            mValue = v / (mWidth * mHeight)
        }

        val colors = Array(mWidth) { IntArray(mHeight) }
        val vv = mValue * n
        for (y in 0..<mHeight) {
            for (x in 0..<mWidth) {
                if (mValues!![x][y] > vv) {
                    colors[x][y] = 1
                } else {
                    colors[x][y] = 0
                }
            }
        }
        val ret = ArrayList<Rect?>()
        var x = x1
        while (x < x2) {
            for (y in y1..<y2) {
                val l = check(x, y, colors, h, w, o)
                if (l > -1) {
                    x += o
                    ret.add(Rect(x, y, x, x + l))
                    break
                }
            }
            x++
        }
        return ret
    }

    /**
     * 检查指定位置的行模式(竖线检测)
     */
    private fun check(x: Int, y: Int, color: Array<IntArray>, h: Int, w: Int, o: Int): Int {
        for (i in 0..<mHeight - y - h) {
            if (!(color[x][y + i] == 1 && color[x + o][y + i] == 0 && color[x + o + w][y + i] == 0)) {
                if (i > h) return i
                else return -1
            }
        }
        return mHeight - y - h
    }

    private fun check2(x: Int, y: Int, color: Array<IntArray>, h: Int, w: Int, o: Int): Int {
        for (i in 0..<mHeight - y - h) {
            if (!(color[x][y + i] == 0 && color[x + o][y + i] == 1 && color[x + o + w][y + i] == 1)) {
                if (i > h) return i
                else return -1
            }
        }
        return mHeight - y - h
    }

    private fun check3(x: Int, y: Int, color: Array<IntArray>, h: Int, w: Int, o: Int): Int {
        for (i in 0..<mWidth - x - h) {
            if (!(color[x + i][y] == 1 && color[x + i][y + o] == 0 && color[x + i][y + o + w] == 0)) {
                if (i > h) return i
                else return -1
            }
        }
        return mHeight - y - h
    }

    private fun check4(x: Int, y: Int, color: Array<IntArray>, h: Int, w: Int, o: Int): Int {
        for (i in 0..<mWidth - x - h) {
            if (!(color[x + i][y] == 0 && color[x + i][y + o] == 1 && color[x + i][y + o + w] == 1)) {
                if (i > h) return i
                else return -1
            }
        }
        return mHeight - y - h
    }
}
