package eu.kanade.tachiyomi.extension.zh.komiic

import kotlin.math.abs

/**
 * 簡化數字顯示
 */
fun simplifyNumber(num: Int): String {
    return when {
        abs(num) < 1000 -> "$num"
        abs(num) < 10000 -> "${num / 1000}千"
        abs(num) < 100000000 -> "${num / 10000}萬"
        else -> "${num / 100000000}億"
    }
}
