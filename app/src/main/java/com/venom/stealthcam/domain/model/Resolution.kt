package com.venom.stealthcam.domain.model

/**
 * Video resolution expressed as pixel dimensions.
 * Kept as a plain Kotlin class with no Android dependencies so it is
 * fully testable in JVM unit tests.
 */
data class Resolution(
    val width: Int,
    val height: Int
) : Comparable<Resolution> {

    val totalPixels: Long get() = width.toLong() * height.toLong()
    val megapixels: Float get() = totalPixels / 1_000_000f
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()

    override fun compareTo(other: Resolution): Int =
        totalPixels.compareTo(other.totalPixels)

    override fun toString(): String = "${width}x${height}"

    companion object {
        val UHD_4K   = Resolution(3840, 2160)
        val FHD_1080P = Resolution(1920, 1080)
        val HD_720P  = Resolution(1280, 720)
        val SD_480P  = Resolution(640, 480)
    }
}
