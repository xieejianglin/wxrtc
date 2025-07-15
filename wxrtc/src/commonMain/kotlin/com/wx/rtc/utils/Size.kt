package com.wx.rtc.utils

class Size(val width: Int, val height: Int) {
    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (this === other) {
            return true
        }
        if (other is Size) {
            val other: Size = other as Size
            return width == other.width && height == other.height
        }
        return false
    }

    override fun toString(): String {
        return return "${width}x$height"
    }

    override fun hashCode(): Int {
        // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
        return height xor ((height shl (Int.SIZE_BITS / 2)) or (height ushr (Int.SIZE_BITS / 2)))
    }
}