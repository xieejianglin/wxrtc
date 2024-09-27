package com.wx.rtc.bean

/**
 * @author wjf
 */
internal annotation class SignalCommand {
    companion object {
        /*-------------------发送--------------*/
        const val START_PROCESS: String = "start_process"
        const val END_PROCESS: String = "end_process"
        const val SEND_ROOM_MSG: String = "send_room_msg"
        const val PROCESS_CMD: String = "process_cmd"
        const val RECORD_CMD: String = "record_cmd"
        const val ENTER_ROOM: String = "enter_room"
        const val EXIT_ROOM: String = "exit_room"
        const val LOGIN: String = "login"
        const val LOGOUT: String = "logout"
        const val CALL_CMD: String = "call_cmd"
        const val VIDEO_AVAILABLE: String = "video_available"
        const val AUDIO_AVAILABLE: String = "audio_available"


        /*-------------------接受--------------*/
        const val ENTER_ROOM_BACK: String = "enter_room_back"
        const val LOGIN_BACK: String = "login_back"
        const val LOGOUT_BACK: String = "logout_back"
        const val START_RECORD_BACK: String = "start_record_back"
        const val END_RECORD_BACK: String = "end_record_back"
        const val GET_UNPUBLISH: String = "get_unpublish"
        const val EXIT_ROOM_BACK: String = "exit_room_back"
        const val REMOTE_ENTER_ROOM: String = "remote_enter_room"
        const val REMOTE_EXIT_ROOM: String = "remote_exit_room"
        const val ROOM_MSG_REV: String = "room_msg_rev"
        const val CALL_MSG_REV: String = "call_msg_rev"
    }
}

