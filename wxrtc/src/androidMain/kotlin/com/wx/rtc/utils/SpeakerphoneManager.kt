package com.wx.rtc.utils

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.wx.rtc.PlatformContext

actual fun setSpeakerOn(context: PlatformContext, speakerOn: Boolean) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        audioManager.availableCommunicationDevices.find {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }?.let { audioManager.setCommunicationDevice(it) }
    } else {
        audioManager.isSpeakerphoneOn = speakerOn
    }
}