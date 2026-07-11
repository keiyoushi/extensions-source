package eu.kanade.tachiyomi.extension.en.nuviatoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class PaginatedResponse<T>(
    val data: List<T>,
    private val meta: Meta,
) {
    val hasNextPage get() = meta.currentPage < meta.lastPage
}

@Serializable
class Meta(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class SeriesDto(
    val id: String,
    private val title: String,
    private val slug: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val description: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    private val genres: List<String>? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@SeriesDto.title
        url = slug
        thumbnail_url = coverUrl
        author = this@SeriesDto.author
        artist = this@SeriesDto.artist
        description = this@SeriesDto.description
        genre = genres?.joinToString()
        status = parseStatus(this@SeriesDto.status)
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class ChapterDto(
    val id: String,
    val title: String? = null,
    val number: Float? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        val numberString = number?.toString()?.removeSuffix(".0") ?: ""
        name = title ?: "Chapter $numberString".trim()
        url = "$slug/chapter/$numberString?id=$id"
        chapter_number = number ?: -1f
        date_upload = createdAt?.substringBefore(".")?.plus("Z")?.let {
            dateFormat.tryParse(it)
        } ?: 0L
    }
}

@Serializable
class PageDto(
    @SerialName("image_url") val imageUrl: String,
)
