package com.wx.rtc.utils.luban

import java.io.IOException
import java.io.InputStream

/**
 * 通过此接口获取输入流，以兼容文件、FileProvider方式获取到的图片
 * <p>
 * Get the input stream through this interface, and obtain the picture using compatible files and FileProvider
 */
interface InputStreamProvider {

    @Throws(IOException::class)
    fun open(): InputStream?

    fun getPath(): String
}