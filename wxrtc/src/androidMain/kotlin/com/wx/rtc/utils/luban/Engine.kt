package com.wx.rtc.utils.luban

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Responsible for starting compress and managing active and cached resources.
 */
class Engine @Throws(IOException::class) constructor(
    private val srcImg: InputStreamProvider,
    private var tagImg: File,
    private var focusAlpha: Boolean
) {
    private var srcWidth = 0
    private var srcHeight = 0

    init {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        options.inSampleSize = 1
        BitmapFactory.decodeStream(srcImg.open(), null, options)
        this.srcWidth = options.outWidth
        this.srcHeight = options.outHeight
    }

    private fun computeSize(): Int {
        srcWidth = if (srcWidth % 2 == 1) srcWidth + 1 else srcWidth
        srcHeight = if (srcHeight % 2 == 1) srcHeight + 1 else srcHeight

        val longSide = max(srcWidth, srcHeight)
        val shortSide = min(srcWidth, srcHeight)

        val scale = (shortSide.toFloat() / longSide)
        return if (scale <= 1 && scale > 0.5625) {
            if (longSide < 1664) {
                1
            } else if (longSide < 4990) {
                2
            } else if (longSide in 4991..10239) {
                4
            } else {
                if ((longSide / 1280) == 0) 1 else longSide / 1280
            }
        } else if (scale <= 0.5625 && scale > 0.5) {
            if (longSide / 1280 == 0) 1 else longSide / 1280
        } else {
            ceil(longSide / (1280.0 / scale)).toInt()
        }
    }

    private fun rotatingImage(bitmap: Bitmap?, angle: Int): Bitmap {
        val matrix = Matrix()

        matrix.postRotate(angle.toFloat())

        return Bitmap.createBitmap(bitmap!!, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    @Throws(IOException::class)
    fun compress(): File {
        val options = BitmapFactory.Options()
        options.inSampleSize = computeSize()

        var tagBitmap = BitmapFactory.decodeStream(srcImg.open(), null, options)
        val stream = ByteArrayOutputStream()

        if (Checker.SINGLE.isJPG(srcImg.open())) {
            tagBitmap = rotatingImage(tagBitmap, Checker.SINGLE.getOrientation(srcImg.open()))
        }
        tagBitmap!!.compress(
            if (focusAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
            60,
            stream
        )
        tagBitmap.recycle()

        val fos = FileOutputStream(tagImg)
        fos.write(stream.toByteArray())
        fos.flush()
        fos.close()
        stream.close()

        return tagImg
    }
}