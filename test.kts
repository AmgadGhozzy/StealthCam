fun main() {
    val rawSizes = listOf(
        Pair(4032, 3024),
        Pair(3840, 2160),
        Pair(1920, 1080),
        Pair(1280, 720)
    )

    val standard16by9 = rawSizes.filter { it.first * 9 == it.second * 16 && it.first <= 3840 }
    println("16:9 sizes: $standard16by9")

    val fallback = rawSizes.filter { it.first <= 1920 && it.second <= 1080 }
    println("Fallback sizes: $fallback")
}
