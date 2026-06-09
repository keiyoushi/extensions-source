package eu.kanade.tachiyomi.multisrc.sinmh

class ProgressiveParser(private val text: String) {
    private var startIndex = 0

    fun substringBetween(left: String, right: String): String = with(text) {
        val leftIndex = indexOf(left, startIndex)
        if (leftIndex == -1) return ""
        val actualLeftIndex = leftIndex + left.length
        val rightIndex = indexOf(right, actualLeftIndex)
        if (rightIndex == -1) return ""
        startIndex = rightIndex + right.length
        return substring(actualLeftIndex, rightIndex)
    }
}
