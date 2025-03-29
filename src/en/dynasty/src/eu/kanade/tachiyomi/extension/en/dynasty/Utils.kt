package eu.kanade.tachiyomi.extension.en.dynasty

import kotlin.math.min

fun String.levenshteinDistance(other: String): Int {
    if (this == other) {
        return 0
    }
    if (this.isEmpty()) {
        return other.length
    }
    if (other.isEmpty()) {
        return this.length
    }

    val lhsLength = this.length + 1
    val rhsLength = other.length + 1

    var cost = Array(lhsLength) { it }
    var newCost = Array(lhsLength) { 0 }

    for (i in 1 until rhsLength) {
        newCost[0] = i

        for (j in 1 until lhsLength) {
            val match = if (this[j - 1] == other[i - 1]) 0 else 1

            val costReplace = cost[j - 1] + match
            val costInsert = cost[j] + 1
            val costDelete = newCost[j - 1] + 1

            newCost[j] = min(min(costInsert, costDelete), costReplace)
        }

        val swap = cost
        cost = newCost
        newCost = swap
    }

    return cost[lhsLength - 1]
}

/**
 * @param threshold 0 = exact match
 */
fun String.almostEquals(other: String, threshold: Float): Boolean {
    if (threshold <= 0f) {
        return equals(other, ignoreCase = true)
    }
    val diff = lowercase().levenshteinDistance(other.lowercase()) / ((length + other.length) / 2f)
    return diff < threshold
}
