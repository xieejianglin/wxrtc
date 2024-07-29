package com.wx.rtc.bean;

public @interface CallCommand {
    String INVITE = "invite";
    String CANCEL = "cancel";
    String ACCEPT = "accept";
    String REJECT = "reject";
    String LINE_BUSY = "line_busy";
    String HANG_UP = "hang_up";
    String NEW_INVITATION_RECEIVED = "new_invitation_received";
    String INVITATION_CANCELED = "invitation_canceled";
    String INVITATION_ACCEPTED = "invitation_accepted";
    String INVITATION_REJECTED = "invitation_rejected";
    String INVITATION_NO_RESP = "invitation_no_resp";
    String INVITATION_LINE_BUSY = "invitation_line_busy";
    String CALL_END = "call_end";
}
