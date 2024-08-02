package com.wx.rtc.bean

internal annotation class CallCommand {
    companion object {
        const val INVITE: String = "invite"
        const val CANCEL: String = "cancel"
        const val ACCEPT: String = "accept"
        const val REJECT: String = "reject"
        const val LINE_BUSY: String = "line_busy"
        const val HANG_UP: String = "hang_up"
        const val NEW_INVITATION_RECEIVED: String = "new_invitation_received"
        const val INVITATION_CANCELED: String = "invitation_canceled"
        const val INVITATION_ACCEPTED: String = "invitation_accepted"
        const val INVITATION_REJECTED: String = "invitation_rejected"
        const val INVITATION_NO_RESP: String = "invitation_no_resp"
        const val INVITATION_LINE_BUSY: String = "invitation_line_busy"
        const val CALL_END: String = "call_end"
    }
}
