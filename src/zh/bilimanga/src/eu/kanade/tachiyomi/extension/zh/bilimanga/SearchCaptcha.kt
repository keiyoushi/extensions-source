package eu.kanade.tachiyomi.extension.zh.bilimanga

import android.graphics.Bitmap

// Digits of the search captcha's bitmap font
private val DIGITS = listOf(
    listOf("...##...", "..####..", ".##..##.", "##....##", "##....##", "##....##", "##....##", ".##..##.", "..####..", "...##..."),
    listOf("..##..", ".###..", "####..", "..##..", "..##..", "..##..", "..##..", "..##..", "..##..", "######"),
    listOf("..####..", ".##..##.", "##....##", "......##", ".....##.", "....##..", "...##...", "..##....", ".##.....", "########"),
    listOf(".#####..", "##...##.", "......##", ".....##.", "...###..", ".....##.", "......##", "......##", "##...##.", ".#####.."),
    listOf(".....##.", "....###.", "...####.", "..##.##.", ".##..##.", "##...##.", "########", ".....##.", ".....##.", ".....##."),
    listOf("#######.", "##......", "##......", "##.###..", "###..##.", "......##", "......##", "##....##", ".##..##.", "..####.."),
    listOf("..####..", ".##..##.", "##....#.", "##......", "##.###..", "###..##.", "##....##", "##....##", ".##..##.", "..####.."),
    listOf("########", "......##", "......##", ".....##.", "....##..", "...##...", "..##....", ".##.....", "##......", "##......"),
    listOf("..####..", ".##..##.", "##....##", ".##..##.", "..####..", ".##..##.", "##....##", "##....##", ".##..##.", "..####.."),
    listOf("..####..", ".##..##.", "##....##", "##....##", ".##..###", "..###.##", "......##", ".#....##", ".##..##.", "..####.."),
)

private class Glyph(val score: Float, val x: Int, val digit: Int)

/** Reads the 4 digits of the search captcha: each digit is one solid colour over a shadow at (+1, +1). */
fun solveSearchCaptcha(bitmap: Bitmap): String {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val background = IntArray(width) { x ->
        (0 until height).groupingBy { y -> pixels[y * width + x] }.eachCount().maxBy { it.value }.key
    }
    val colours = pixels.indices
        .filter { pixels[it] != background[it % width] }
        .groupBy { pixels[it] }
        .values.map(List<Int>::toHashSet)
        .filter { it.size >= 12 }

    val glyphs = colours
        .filterNot { set ->
            colours.any { other ->
                other !== set && set.count { it % width > 0 && it >= width && (it - width - 1) in other } * 2 > set.size
            }
        }
        .map { matchDigit(it, width, height) }

    return glyphs.sortedByDescending { it.score }.take(4).sortedBy { it.x }.joinToString("") { it.digit.toString() }
}

private fun matchDigit(set: Set<Int>, width: Int, height: Int): Glyph {
    fun inSet(x: Int, y: Int) = x in 0 until width && y in 0 until height && (y * width + x) in set
    val minX = set.minOf { it % width }
    val minY = set.minOf { it / width }
    var best = Glyph(Float.NEGATIVE_INFINITY, minX, 0)
    DIGITS.forEachIndexed { digit, rows ->
        val templateWidth = rows[0].length
        val size = rows.sumOf { row -> row.count { it == '#' } }
        for (offsetX in minX - 2..minX + 2) {
            for (offsetY in minY - 2..minY + 2) {
                var hits = 0
                var extra = 0
                for (row in rows.indices) {
                    for (column in 0 until templateWidth) {
                        val inGlyph = inSet(offsetX + column, offsetY + row)
                        if (rows[row][column] == '#') {
                            if (inGlyph) hits++
                        } else if (inGlyph) {
                            extra++
                        }
                    }
                }
                val score = (2 * hits - size - extra).toFloat() / size
                if (score > best.score) best = Glyph(score, offsetX, digit)
            }
        }
    }
    return best
}
