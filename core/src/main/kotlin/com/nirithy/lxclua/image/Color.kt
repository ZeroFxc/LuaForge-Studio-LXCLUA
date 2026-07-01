package com.nirithy.lxclua.image

class Color {
    var red: Int
    var green: Int
    var blue: Int

    constructor(color: Int) {
        red = color shl 8 ushr 24
        green = color shl 16 ushr 24
        blue = color shl 24 ushr 24
    }

    constructor(r: Int, g: Int, b: Int) {
        red = r
        green = g
        blue = b
    }

    val int: Int
        get() = -0x1000000 or (red shl 16) or (green shl 8) or blue

    override fun toString(): String {
        return "Color(" + red + ", " + green + ", " + blue + ")"
    }
}
