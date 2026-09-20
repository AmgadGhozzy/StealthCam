package com.venom.stealthcam.domain.model

/**
 * Supported video codecs.
 * H.265 (HEVC) is preferred when the device supports it — it achieves equivalent
 * quality at roughly 60 % of H.264 bitrate, which matters for long background recordings.
 */
enum class VideoCodec(
    /** MediaFormat MIME type string used directly in MediaRecorder / MediaCodec. */
    val mimeType: String,
    val displayName: String
) {
    H264(mimeType = "video/avc",  displayName = "H.264 (AVC)"),
    H265(mimeType = "video/hevc", displayName = "H.265 (HEVC)")
}
