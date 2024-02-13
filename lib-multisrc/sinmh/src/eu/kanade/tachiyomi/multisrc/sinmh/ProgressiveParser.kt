package eu.kanade.tachiyomi.multisrc.sinmh

class ProgressiveParser(private val text: String) {
    private var startIndex = 0
    fun substringBetween(left: String, right: String): String = with(text) {
        val leftIndex = indexOf(left, startIndex) + left.length
        val rightIndex = indexOf(right, leftIndex)
        startIndex = rightIndex + right.length
        return substring(leftIndex, rightIndex)
    }
}
