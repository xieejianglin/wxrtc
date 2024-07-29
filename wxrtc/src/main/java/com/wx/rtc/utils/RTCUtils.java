package com.wx.rtc.utils;

import android.util.Size;

import com.wx.rtc.WXRTCDef;

public class RTCUtils {

    public static Size getVideoResolution(int videoResolution){
        int videoWidth = 0;
        int videoHeight = 0;
        switch (videoResolution) {
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_120_120:
                videoWidth = 120;
                videoHeight = 120;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_160_160:
                videoWidth = 160;
                videoHeight = 160;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_270_270:
                videoWidth = 270;
                videoHeight = 270;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_480_480:
                videoWidth = 480;
                videoHeight = 480;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_160_120:
                videoWidth = 160;
                videoHeight = 120;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_240_180:
                videoWidth = 240;
                videoHeight = 180;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_280_210:
                videoWidth = 280;
                videoHeight = 210;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_320_240:
                videoWidth = 320;
                videoHeight = 240;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_400_300:
                videoWidth = 400;
                videoHeight = 300;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_480_360:
                videoWidth = 480;
                videoHeight = 360;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_640_480:
                videoWidth = 640;
                videoHeight = 480;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_960_720:
                videoWidth = 960;
                videoHeight = 720;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_160_90:
                videoWidth = 160;
                videoHeight = 90;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_256_144:
                videoWidth = 256;
                videoHeight = 144;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_320_180:
                videoWidth = 320;
                videoHeight = 180;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_480_270:
                videoWidth = 480;
                videoHeight = 270;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_640_360:
                videoWidth = 640;
                videoHeight = 360;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_960_540:
                videoWidth = 960;
                videoHeight = 540;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_1280_720:
                videoWidth = 1280;
                videoHeight = 720;
                break;
            case WXRTCDef.WXRTC_VIDEO_RESOLUTION_1920_1080:
                videoWidth = 1920;
                videoHeight = 1080;
                break;
            default:
                break;
        }

        return new Size(videoWidth, videoHeight);
    }
}
