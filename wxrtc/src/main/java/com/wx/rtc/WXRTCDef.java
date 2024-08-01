package com.wx.rtc;

/**
 * @author Administrator
 */
public class WXRTCDef {
    public static final int WXRTC_VIDEO_RESOLUTION_120_120 = 1;

    public static final int WXRTC_VIDEO_RESOLUTION_160_160 = 3;

    public static final int WXRTC_VIDEO_RESOLUTION_270_270 = 5;

    public static final int WXRTC_VIDEO_RESOLUTION_480_480 = 7;

    public static final int WXRTC_VIDEO_RESOLUTION_160_120 = 50;

    public static final int WXRTC_VIDEO_RESOLUTION_240_180 = 52;

    public static final int WXRTC_VIDEO_RESOLUTION_280_210 = 54;

    public static final int WXRTC_VIDEO_RESOLUTION_320_240 = 56;

    public static final int WXRTC_VIDEO_RESOLUTION_400_300 = 58;

    public static final int WXRTC_VIDEO_RESOLUTION_480_360 = 60;

    public static final int WXRTC_VIDEO_RESOLUTION_640_480 = 62;

    public static final int WXRTC_VIDEO_RESOLUTION_960_720 = 64;

    public static final int WXRTC_VIDEO_RESOLUTION_160_90 = 100;

    public static final int WXRTC_VIDEO_RESOLUTION_256_144 = 102;

    public static final int WXRTC_VIDEO_RESOLUTION_320_180 = 104;

    public static final int WXRTC_VIDEO_RESOLUTION_480_270 = 106;

    public static final int WXRTC_VIDEO_RESOLUTION_640_360 = 108;

    public static final int WXRTC_VIDEO_RESOLUTION_960_540 = 110;

    public static final int WXRTC_VIDEO_RESOLUTION_1280_720 = 112;

    public static final int WXRTC_VIDEO_RESOLUTION_1920_1080 = 114;

    public static final int WXRTC_VIDEO_RESOLUTION_MODE_LANDSCAPE = 0;

    public static final int WXRTC_VIDEO_RESOLUTION_MODE_PORTRAIT = 1;

    public static final int WXRTC_VIDEO_RENDER_MODE_FILL = 0;

    public static final int WXRTC_VIDEO_RENDER_MODE_FIT = 1;

    public static final int WXRTC_VIDEO_ROTATION_0 = 0;

    public static final int WXRTC_VIDEO_ROTATION_90 = 1;

    public static final int WXRTC_VIDEO_ROTATION_180 = 2;

    public static final int WXRTC_VIDEO_ROTATION_270 = 3;

    public static final int WXRTC_VIDEO_MIRROR_TYPE_AUTO = 0;

    public static final int WXRTC_VIDEO_MIRROR_TYPE_ENABLE = 1;

    public static final int WXRTC_VIDEO_MIRROR_TYPE_DISABLE = 2;

    /**
     * 通话角色
     */
    public enum Role{
        /**
         * 未知类型
         */
        None,
        /**
         * 主叫（邀请方）
         */
        Caller,
        /**
         * 被叫（被邀请方）
         */
        Callee,
    }

    /**
     * 通话角色
     */
    public enum Status{
        /**
         * 未定义
         */
        None,
        /**
         * 通话等待中
         */
        Calling,
        /**
         * 通话已接听（通话中）
         */
        Connected,
    }

    public static class WXRTCVideoEncParam {
        public int videoResolution = WXRTC_VIDEO_RESOLUTION_1280_720;
        public int videoResolutionMode = WXRTC_VIDEO_RESOLUTION_MODE_LANDSCAPE;
        public int videoFps = 25;
        public int videoMinBitrate = 2000;
        public int videoMaxBitrate = 2500;
    }

    public static class WXRTCRenderParams {
        public int rotation = WXRTC_VIDEO_ROTATION_0;

        public int fillMode = WXRTC_VIDEO_RENDER_MODE_FILL;

        public int mirrorType = WXRTC_VIDEO_MIRROR_TYPE_AUTO;
    }

}
