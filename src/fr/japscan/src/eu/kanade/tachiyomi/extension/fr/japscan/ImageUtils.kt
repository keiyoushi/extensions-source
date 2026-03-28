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
    val ignoreTopRows = 3 // ignore first 3 rows at the top of each segment (fully noised)
    val rows = 5 // number of rows to compare (bottom of top vs top of bottom)
    val maxOffset = 1 // allow vertical shift -1..+1
    val w = minOf(top.width, bottom.width)

    val topH = top.height
    val bottomH = bottom.height

    // Determine indices for the 5 rows taken from the bottom of `top`,
    // but ensure we did not use any of the first 3 noisy rows.
    // We want the last `rows` rows of `top`, but each index must be >= ignoreTopRows.
    val topRowStart = (topH - rows).coerceAtLeast(ignoreTopRows)
    val topRowIndices = IntArray(rows) { r -> (topRowStart + r).coerceIn(0, topH - 1) }

    // Determine indices for the 5 rows taken from the top of `bottom`,
    // skipping its first `ignoreTopRows` rows.
    val bottomRowStart = ignoreTopRows.coerceAtMost(bottomH - rows)
    val bottomRowIndicesBase = IntArray(rows) { r -> (bottomRowStart + r).coerceIn(0, bottomH - 1) }

    var bestScore = Long.MAX_VALUE

    for (offset in -maxOffset..maxOffset) {
        // per-column list of differences across the rows considered for this offset
        val perColDiffs = Array(w) { mutableListOf<Int>() }

        for (r in 0 until rows) {
            val topY = topRowIndices[r]
            val bottomY = (bottomRowIndicesBase[r] + offset).coerceIn(0, bottomH - 1)
            for (x in 0 until w) {
                val pxTop = top.getPixel(x, topY)
                val pxBottom = bottom.getPixel(x, bottomY)

                // Convert to grayscale (luma) for robustness
                val r1 = (pxTop shr 16) and 0xFF
                val g1 = (pxTop shr 8) and 0xFF
                val b1 = pxTop and 0xFF
                val l1 = (0.2126 * r1 + 0.7152 * g1 + 0.0722 * b1).toInt()

                val r2 = (pxBottom shr 16) and 0xFF
                val g2 = (pxBottom shr 8) and 0xFF
                val b2 = pxBottom and 0xFF
                val l2 = (0.2126 * r2 + 0.7152 * g2 + 0.0722 * b2).toInt()

                perColDiffs[x].add(abs(l1 - l2))
            }
        }

        // For each column take the median of the collected diffs, then sum across columns
        var score: Long = 0
        for (x in 0 until w) {
            val list = perColDiffs[x].sorted()
            val median = if (list.isEmpty()) {
                0
            } else {
                val m = list.size / 2
                if (list.size % 2 == 1) list[m] else (list[m - 1] + list[m]) / 2
            }
            score += median
        }

        if (score < bestScore) bestScore = score
    }

    return bestScore
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
