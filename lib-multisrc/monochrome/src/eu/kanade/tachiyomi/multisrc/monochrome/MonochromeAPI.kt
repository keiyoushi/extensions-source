package eu.kanade.tachiyomi.multisrc.monochrome

import kotlinx.serialization.Serializable
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

internal const val UUID_QUERY = "uuid:"

private const val ISO_DATE = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"

private val dateFormat = SimpleDateFormat(ISO_DATE, Locale.ROOT)

private val decimalFormat = DecimalFormat("#.##")

@Serializable
data class Results(
    private val offset: Int,
    private val limit: Int,
    private val results: List<Manga>,
    private val total: Int,
) : Iterable<Manga> by results {
    val hasNext: Boolean
        get() = total > results.size + offset * limit
}

@Serializable
data class Manga(
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
data class Chapter(
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
            append("Chapter ").append(decimalFormat.format(number))
            if (name.isNotEmpty()) append(" - ").append(name)
        }

    val timestamp: Long
        get() = dateFormat.parse(uploadTime)?.time ?: 0L

    val parts: String
        get() = "/$id|$version|$length"
}
