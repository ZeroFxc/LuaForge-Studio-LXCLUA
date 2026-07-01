package com.nirithy.lxclua.image

import android.graphics.Point

class ColorPoint {
    var red: Int
    var green: Int
    var blue: Int
    var x: Int
    var y: Int
    var offset: Int

    constructor(p: Point, color: Color, o: Int) : this(
        p.x,
        p.y,
        color.red,
        color.green,
        color.blue,
        o
    )

    constructor(x: Int, y: Int, r: Int, g: Int, b: Int, o: Int) {
        this.x = x
        this.y = y
        this.red = r
        this.green = g
        this.blue = b
        this.offset = o
    }

    constructor(arg: IntArray) {
        x = arg[0]
        y = arg[1]
        red = arg[2]
        green = arg[3]
        blue = arg[4]
        offset = arg[5]
    }

    @JvmOverloads
    fun check(pixels: Array<IntArray>, x: Int = 0, y: Int = 0): Boolean {
        val r1 = red - offset
        val r2 = red + offset
        val g1 = green - offset
        val g2 = green + offset
        val b1 = blue - offset
        val b2 = blue + offset
        val color = pixels[this.y + y][this.x + x]
        val r = color shl 8 ushr 24
        val g = color shl 16 ushr 24
        val b = color shl 24 ushr 24
        return r >= r1 && r <= r2 && g >= g1 && g <= g2 && b >= b1 && b <= b2
    }
}