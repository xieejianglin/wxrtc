package com.wx.rtc.bean;

/**
 * @author wjf
 */
public @interface SignalCommand {

    /*-------------------发送--------------*/
    String START_PROCESS = "start_process";
    String END_PROCESS = "end_process";
    String SEND_ROOM_MSG = "send_room_msg";
    String PROCESS_CMD = "process_cmd";
    String RECORD_CMD = "record_cmd";
    String ENTER_ROOM = "enter_room";
    String EXIT_ROOM = "exit_room";
    String LOGIN = "login";
    String LOGOUT = "logout";
    String CALL_CMD = "call_cmd";


    /*-------------------接受--------------*/
    String ENTER_ROOM_BACK = "enter_room_back";
    String LOGIN_BACK = "login_back";
    String LOGOUT_BACK = "logout_back";
    String START_RECORD_BACK = "start_record_back";
    String END_RECORD_BACK = "end_record_back";
    String GET_UNPUBLISH = "get_unpublish";
    String EXIT_ROOM_BACK = "exit_room_back";
    String REMOTE_ENTER_ROOM = "remote_enter_room";
    String REMOTE_EXIT_ROOM = "remote_exit_room";
    String ROOM_MSG_REV = "room_msg_rev";
    String CALL_MSG_REV = "call_msg_rev";
}

