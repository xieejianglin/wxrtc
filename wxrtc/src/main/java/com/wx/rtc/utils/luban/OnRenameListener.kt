package com.wx.rtc.utils.luban

/**
 * Author: zibin
 * Datetime: 2018/5/18
 * <p>
 * 提供修改压缩图片命名接口
 * <p>
 * A functional interface (callback) that used to rename the file after compress.
 */
interface OnRenameListener {
    /**
     * 压缩前调用该方法用于修改压缩后文件名
     *
     *
     * Call before compression begins.
     *
     * @param filePath 传入文件路径/ file path
     * @return 返回重命名后的字符串/ file name
     */
    fun rename(filePath: String): String
}