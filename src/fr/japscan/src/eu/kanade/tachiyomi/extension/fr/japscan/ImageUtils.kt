package eu.kanade.tachiyomi.extension.fr.japscan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.io.encoding.Base64
import kotlin.math.abs

fun decodeBase64ToImage(b64: String): Bitmap {
    val bytes = Base64.decode(b64)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun edgeDifference(top: Bitmap, bottom: Bitmap): Long {
    // Compare the bottom row of `top` with the top row of `bottom`
    // Return the sum of absolute differences across RGB channels for each column
    val topY = top.height - 1
    val bottomY = 0
    val w = minOf(top.width, bottom.width) // only compare overlapping width
    var diff: Long = 0
    for (x in 0 until w) {
        val pxTop = top.getPixel(x, topY)
        val pxBottom = bottom.getPixel(x, bottomY)

        // Extract RGB components from packed int pixel
        val r1 = (pxTop shr 16) and 0xFF
        val g1 = (pxTop shr 8) and 0xFF
        val b1 = pxTop and 0xFF

        val r2 = (pxBottom shr 16) and 0xFF
        val g2 = (pxBottom shr 8) and 0xFF
        val b2 = pxBottom and 0xFF

        // Accumulate L1 color difference
        diff += abs(r1 - r2)
        diff += abs(g1 - g2)
        diff += abs(b1 - b2)
    }
    return diff
}

fun computeCostMatrix(segments: List<Bitmap>): Array<LongArray> {
    // Build an n x n cost matrix where cost[i][j] is the edge difference
    // when segment i is placed above segment j. Diagonals remain "infinite".
    val n = segments.size
    val inf = Long.MAX_VALUE / 4 // large sentinel to avoid overflow on addition
    val cost = Array(n) { LongArray(n) { inf } }
    for (i in 0 until n) {
        for (j in 0 until n) {
            if (i == j) continue
            cost[i][j] = edgeDifference(segments[i], segments[j])
        }
    }
    return cost
}

fun generatePermutations(n: Int): List<List<Int>> {
    // Generate all permutations of indices [0..n-1] using backtracking.
    val results = mutableListOf<List<Int>>()
    val arr = IntArray(n) { it }
    fun swap(i: Int, j: Int) {
        val t = arr[i]
        arr[i] = arr[j]
        arr[j] = t
    }
    fun backtrack(k: Int) {
        if (k == n) {
            results.add(arr.toList())
            return
        }
        for (i in k until n) {
            swap(k, i)
            backtrack(k + 1)
            swap(k, i) // restore
        }
    }
    backtrack(0)
    return results
}

fun findBestVerticalOrder(cost: Array<LongArray>): List<Int> {
    // For exactly 4 segments, evaluate all permutations and pick the one with minimal total adjacent-edge cost (greedy sum of consecutive pairs).
    val n = cost.size
    require(n == 4) { "Exactly four segments were expected." }
    val perms = generatePermutations(n)
    var bestScore = Long.MAX_VALUE
    var bestPerm = perms.first()
    for (p in perms) {
        var score: Long = 0
        for (k in 0 until n - 1) {
            score += cost[p[k]][p[k + 1]]
            if (score >= bestScore) break // early prune
        }
        if (score < bestScore) {
            bestScore = score
            bestPerm = p
        }
    }
    return bestPerm
}

fun orderUuidsByImageVerticality(items: List<Pair<String, String>>): List<String> {
    require(items.size == 4) { "Exactly 4 Base64 segments are required." }
    val bitmaps = items.map { (_, src) -> decodeBase64ToImage(src.split(",")[1].replace("\\", "")) }
    val cost = computeCostMatrix(bitmaps)
    val orderIndex = findBestVerticalOrder(cost)
    return orderIndex.map { items[it].first }
}
