package com.wx.rtc.utils

import com.wx.rtc.rtc.PeerConnectionParameters

const val VIDEO_TRACK_ID: String = "ARDAMSv0"
const val AUDIO_TRACK_ID: String = "ARDAMSa0"
const val VIDEO_TRACK_TYPE: String = "video"
const val TAG = "PCRTCClient"
const val VIDEO_CODEC_VP8 = "VP8"
const val VIDEO_CODEC_VP9 = "VP9"
const val VIDEO_CODEC_H264 = "H264"
const val VIDEO_CODEC_H264_BASELINE = "H264 Baseline"
const val VIDEO_CODEC_H264_HIGH = "H264 High"
const val AUDIO_CODEC_OPUS = "opus"
const val AUDIO_CODEC_ISAC = "ISAC"
const val VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate"
const val VIDEO_FLEXFEC_FIELDTRIAL =
    "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/"
const val VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/"
const val DISABLE_WEBRTC_AGC_FIELDTRIAL =
    "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/"
const val AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate"
const val AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
const val AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
const val AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter"
const val AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
const val DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement"
const val HD_VIDEO_WIDTH = 1280
const val HD_VIDEO_HEIGHT = 720
const val BPS_IN_KBPS = 1000
const val RTCEVENTLOG_OUTPUT_DIR_NAME = "rtc_event_log"
fun getSdpVideoCodecName(parameters: PeerConnectionParameters): String {
    return when (parameters.videoCodec) {
        VIDEO_CODEC_VP8 -> VIDEO_CODEC_VP8
        VIDEO_CODEC_VP9 -> VIDEO_CODEC_VP9
        VIDEO_CODEC_H264_HIGH, VIDEO_CODEC_H264_BASELINE -> VIDEO_CODEC_H264
        else -> VIDEO_CODEC_VP8
    }
}

fun getFieldTrials(peerConnectionParameters: PeerConnectionParameters): String {
    var fieldTrials = ""
    if (peerConnectionParameters.videoFlexfecEnabled) {
        fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL
        log(TAG, "Enable FlexFEC field trial.")
    }
    //        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
    if (peerConnectionParameters.disableWebRtcAGCAndHPF) {
        fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL
        log(TAG, "Disable WebRTC AGC field trial.")
    }
    return fieldTrials
}

fun setStartBitrate(
    codec: String, isVideoCodec: Boolean, sdpDescription: String, bitrateKbps: Int
): String {
    val lines =
        sdpDescription.split("\r\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    var rtpmapLineIndex = -1
    var sdpFormatUpdated = false
    var codecRtpMap: String? = null
    // Search for codec rtpmap in format
    // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
    var regex = "^a=rtpmap:(\\d+) $codec(/\\d+)+[\r]?$"
    var codecPattern = Regex(regex)
    for (i in lines.indices) {
        val codecMatcher = codecPattern.find(lines[i])
        if (codecMatcher != null) {
            codecRtpMap = codecMatcher.groupValues[1]
            rtpmapLineIndex = i
            break
        }
    }
    if (codecRtpMap == null) {
        log(TAG, "No rtpmap for $codec codec")
        return sdpDescription
    }
    log(
        TAG,
        "Found " + codec + " rtpmap " + codecRtpMap + " at " + lines[rtpmapLineIndex]
    )
    // Check if a=fmtp string already exist in remote SDP for this codec and
    // update it with new bitrate parameter.
    regex = "^a=fmtp:$codecRtpMap \\w+=\\d+.*[\r]?$"
    codecPattern = Regex(regex)
    for (i in lines.indices) {
        val codecMatcher = codecPattern.find(lines[i])
        if (codecMatcher != null) {
            log(TAG, "Found " + codec + " " + lines[i])
            if (isVideoCodec) {
                lines[i] += "; " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps
            } else {
                lines[i] += "; " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000)
            }
            log(TAG, "Update remote SDP line: " + lines[i])
            sdpFormatUpdated = true
            break
        }
    }
    val newSdpDescription = StringBuilder()
    for (i in lines.indices) {
        newSdpDescription.append(lines[i]).append("\r\n")
        // Append new a=fmtp line if no such line exist for a codec.
        if (!sdpFormatUpdated && i == rtpmapLineIndex) {
            var bitrateSet = if (isVideoCodec) {
                "a=fmtp:" + codecRtpMap + " " + VIDEO_CODEC_PARAM_START_BITRATE + "=" + bitrateKbps
            } else {
                ("a=fmtp:" + codecRtpMap + " " + AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000))
            }
            log(TAG, "Add remote SDP line: $bitrateSet")
            newSdpDescription.append(bitrateSet).append("\r\n")
        }
    }
    return newSdpDescription.toString()
}

/**
 * Returns the line number containing "m=audio|video", or -1 if no such line exists.
 */
fun findMediaDescriptionLine(isAudio: Boolean, sdpLines: Array<String>): Int {
    val mediaDescription = if (isAudio) "m=audio " else "m=video "
    for (i in sdpLines.indices) {
        if (sdpLines[i].startsWith(mediaDescription)) {
            return i
        }
    }
    return -1
}

fun joinString(
    s: Iterable<CharSequence?>, delimiter: String, delimiterAtEnd: Boolean
): String {
    val iter = s.iterator()
    if (!iter.hasNext()) {
        return ""
    }
    val buffer = StringBuilder(iter.next()!!)
    while (iter.hasNext()) {
        buffer.append(delimiter).append(iter.next())
    }
    if (delimiterAtEnd) {
        buffer.append(delimiter)
    }
    return buffer.toString()
}

fun movePayloadTypesToFront(
    preferredPayloadTypes: List<String?>, mLine: String
): String? {
    // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
    val origLineParts =
        listOf(*mLine.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
    if (origLineParts.size <= 3) {
        log(TAG, "Wrong SDP media description format: $mLine")
        return null
    }
    val header: List<String?> = origLineParts.subList(0, 3)
    val unpreferredPayloadTypes: MutableList<String?> =
        ArrayList(origLineParts.subList(3, origLineParts.size))
    unpreferredPayloadTypes.removeAll(preferredPayloadTypes)
    // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
    // types.
    val newLineParts: MutableList<String?> = ArrayList()
    newLineParts.addAll(header)
    newLineParts.addAll(preferredPayloadTypes)
    newLineParts.addAll(unpreferredPayloadTypes)
    return joinString(newLineParts, " ", false /* delimiterAtEnd */)
}

fun preferCodec(sdpDescription: String, codec: String, isAudio: Boolean): String {
    val lines =
        sdpDescription.split("\r\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val mLineIndex = findMediaDescriptionLine(isAudio, lines)
    if (mLineIndex == -1) {
        log(TAG, "No mediaDescription line, so can't prefer $codec")
        return sdpDescription
    }
    // A list with all the payload types with name |codec|. The payload types are integers in the
    // range 96-127, but they are stored as strings here.
    val codecPayloadTypes: MutableList<String?> = ArrayList()
    // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
    val codecPattern = Regex("^a=rtpmap:(\\d+) $codec(/\\d+)+[\r]?$")
    for (line in lines) {
        val codecMatcher = codecPattern.find(line)
        if (codecMatcher != null) {
            codecPayloadTypes.add(codecMatcher.groupValues[1])
        }
    }
    if (codecPayloadTypes.isEmpty()) {
        log(TAG, "No payload types with name $codec")
        return sdpDescription
    }
    val newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex])
        ?: return sdpDescription
    log(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine)
    lines[mLineIndex] = newMLine
    return joinString(listOf(*lines), "\r\n", true /* delimiterAtEnd */)
}