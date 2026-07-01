package com.nirithy.lxclua.image


class Point {
    var x: Int
    var y: Int
    var t: Int = 0


    constructor(x: Int, y: Int, t: Int) {
        this.x = x
        this.y = y
        this.t = t
    }

    constructor(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    constructor(src: Point) {
        this.x = src.x
        this.y = src.y
    }

    /**
     * Set the point's x and y coordinates
     */
    fun set(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /**
     * Negate the point's coordinates
     */
    fun negate() {
        x = -x
        y = -y
    }

    /**
     * Offset the point's coordinates by dx, dy
     */
    fun offset(dx: Int, dy: Int) {
        x += dx
        y += dy
    }

    /**
     * Returns true if the point's coordinates equal (x,y)
     */
    fun equals(x: Int, y: Int): Boolean {
        return this.x == x && this.y == y
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false

        val point = o as Point

        if (x != point.x) return false
        return y == point.y
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        return result
    }

    override fun toString(): String {
        return "Point(" + x + ", " + y + ": " + t + ")"
    }
}
