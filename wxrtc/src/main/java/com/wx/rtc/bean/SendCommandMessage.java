package com.wx.rtc.bean;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SendCommandMessage {

    @SerializedName("signal")
    private String signal;
    @SerializedName("app_id")
    private String appId;
    @SerializedName("room_id")
    private String roomId;
    @SerializedName("user_id")
    private String userId;
    @SerializedName("room_msg")
    private RoomMsg roomMsg;
    @SerializedName("record_cmd")
    private RecordCmdDTO recordCmd;
    @SerializedName("process_cmd_list")
    private List<ProcessCmdListDTO> processCmdList;
    @SerializedName("call_cmd")
    private CallCmdDTO callCmd;

    public String getSignal() {
        return signal;
    }

    public void setSignal(String signal) {
        this.signal = signal;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public RoomMsg getRoomMsg() {
        return roomMsg;
    }

    public void setRoomMsg(RoomMsg roomMsg) {
        this.roomMsg = roomMsg;
    }

    public RecordCmdDTO getRecordCmd() {
        return recordCmd;
    }

    public void setRecordCmd(RecordCmdDTO recordCmd) {
        this.recordCmd = recordCmd;
    }

    public List<ProcessCmdListDTO> getProcessCmdList() {
        return processCmdList;
    }

    public void setProcessCmdList(List<ProcessCmdListDTO> processCmdList) {
        this.processCmdList = processCmdList;
    }

    public CallCmdDTO getCallCmd() {
        return callCmd;
    }

    public void setCallCmd(CallCmdDTO callCmd) {
        this.callCmd = callCmd;
    }

    public static class RecordCmdDTO {
        @SerializedName("cmd")
        private String cmd;
        @SerializedName("end_file_name")
        private String endFileName;
        @SerializedName("mix_id")
        private String mixId;
        @SerializedName("extra_data")
        private String extraData;
        @SerializedName("need_after_asr")
        private boolean needAfterAsr;
        @SerializedName("hospital_id")
        private String hospitalId;
        @SerializedName("spk_list")
        private List<SpeakerDTO> spkList;

        public String getCmd() {
            return cmd;
        }

        public void setCmd(String cmd) {
            this.cmd = cmd;
        }

        public String getEndFileName() {
            return endFileName;
        }

        public String getMixId() {
            return mixId;
        }

        public void setMixId(String mixId) {
            this.mixId = mixId;
        }

        public void setEndFileName(String endFileName) {
            this.endFileName = endFileName;
        }

        public String getExtraData() {
            return extraData;
        }

        public void setExtraData(String extraData) {
            this.extraData = extraData;
        }

        public boolean isNeedAfterAsr() {
            return needAfterAsr;
        }

        public void setNeedAfterAsr(boolean needAfterAsr) {
            this.needAfterAsr = needAfterAsr;
        }

        public String getHospitalId() {
            return hospitalId;
        }

        public void setHospitalId(String hospitalId) {
            this.hospitalId = hospitalId;
        }

        public List<SpeakerDTO> getSpkList() {
            return spkList;
        }

        public void setSpkList(List<SpeakerDTO> spkList) {
            this.spkList = spkList;
        }
    }



    public static class ProcessCmdListDTO {
        @SerializedName("type")
        private String type;
        @SerializedName("cmd")
        private String cmd;
        @SerializedName("hospital_id")
        private String hospitalId;
        @SerializedName("spk_list")
        private List<SpeakerDTO> spkList;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getCmd() {
            return cmd;
        }

        public void setCmd(String cmd) {
            this.cmd = cmd;
        }

        public String getHospitalId() {
            return hospitalId;
        }

        public void setHospitalId(String hospitalId) {
            this.hospitalId = hospitalId;
        }

        public List<SpeakerDTO> getSpkList() {
            return spkList;
        }

        public void setSpkList(List<SpeakerDTO> spkList) {
            this.spkList = spkList;
        }
    }

    public static class SpeakerDTO {
        @SerializedName("spk_id")
        private Long spkId;
        @SerializedName("spk_name")
        private String spkName;

        public Long getSpkId() {
            return spkId;
        }

        public void setSpkId(Long spkId) {
            this.spkId = spkId;
        }

        public String getSpkName() {
            return spkName;
        }

        public void setSpkName(String spkName) {
            this.spkName = spkName;
        }
    }

    public static class CallCmdDTO{
        @SerializedName("cmd")
        private String cmd;
        @SerializedName("user_id")
        private String userId;
        @SerializedName("room_id")
        private String roomId;

        public String getCmd() {
            return cmd;
        }

        public void setCmd(String cmd) {
            this.cmd = cmd;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getRoomId() {
            return roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }
    }
}
