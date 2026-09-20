package com.venom.stealthcam.domain.model

/**
 * Controls how the video bitrate is determined.
 *
 * [Auto] — the recording engine calculates an appropriate bitrate based on
 * resolution, frame-rate, and codec efficiency.
 *
 * [Manual] — the user explicitly specifies the bitrate in bits-per-second.
 */
sealed class BitrateMode {
    data object Auto : BitrateMode()
    data class Manual(val bps: Int) : BitrateMode()
}
