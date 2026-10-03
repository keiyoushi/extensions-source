package eu.kanade.tachiyomi.multisrc.monochrome

import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.math.RoundingMode
import kotlin.time.Instant

@Serializable
class Results(
    private val offset: Int,
    private val limit: Int,
    private val results: List<Manga>,
    private val total: Int,
) : Iterable<Manga> by results {
    val hasNext: Boolean
        get() = total > results.size + offset * limit
}

@Serializable
class Manga(
    val title: String,
    val description: String,
    val author: String,
    val artist: String,
    val status: String,
    val id: String,
    private val version: Int,
) {
    val cover: String
        get() = "/media/$id/cover.jpg?version=$version"
}

@Serializable
class Chapter(
    private val name: String,
    private val volume: Int?,
    val number: Float,
    val scanGroup: String,
    private val id: String,
    private val version: Int,
    private val length: Int,
    private val uploadTime: String,
) {
    val title: String
        get() = buildString {
            if (volume != null) append("Vol ").append(volume).append(" ")
            append("Chapter ").append(number.formatChapterNumber())
            if (name.isNotEmpty()) append(" - ").append(name)
        }

    val timestamp: Long
        get() = Instant.tryParse(uploadTime)

    val parts: String
        get() = "/$id|$version|$length"
}

// at most two decimals without trailing zeros, e.g. 12.0 -> "12", 12.5 -> "12.5"
private fun Float.formatChapterNumber(): String = toBigDecimal()
    .setScale(2, RoundingMode.HALF_EVEN)
    .stripTrailingZeros()
    .toPlainString()
