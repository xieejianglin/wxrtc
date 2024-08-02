package com.wx.rtc.bean

internal annotation class RecordCommand {
    companion object {
        const val START_RECORD: String = "start_record"
        const val END_RECORD: String = "end_record"
        const val END_AND_START_RECORD: String = "end_and_start_record"
    }
}
