package eu.kanade.tachiyomi.extension.all.d9comics

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class SeriesListDto(val series: List<SeriesDto>)

@Serializable
class SeriesDto(
    val id: String,
    private val titles: Map<String, String?> = emptyMap(),
    private val descriptions: Map<String, String?> = emptyMap(),
    private val cover: String? = null,
    private val authors: List<String> = emptyList(),
    private val tags: List<String> = emptyList(),
    private val status: String? = null,
    val displayOrder: Int = 0,
) {
    fun url(lang: String) = "/series/$id.html?lang=$lang"

    fun toSManga(baseUrl: String, lang: String) = SManga.create().apply {
        url = url(lang)
        title = titles.localized(lang)!!
        thumbnail_url = cover?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
        author = authors.joinToString().ifEmpty { null }
        genre = tags.joinToString().ifEmpty { null }
        status = when (this@SeriesDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(descriptions.localized(lang))
            .build()
    }
}

@Serializable
class ComicListDto(val comics: List<ComicDto>)

@Serializable
class ComicDto(
    val lang: String,
    val title: String,
    private val cover: String? = null,
    val date: String,
    val url: String,
    private val authors: List<String> = emptyList(),
    private val tags: List<String> = emptyList(),
    @SerialName("private") private val exclusive: Boolean = false,
    val hidden: Boolean = false,
    val seriesId: String? = null,
    val issueNumber: Int? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = this@ComicDto.url
        title = this@ComicDto.title
        thumbnail_url = cover?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
        author = authors.joinToString().ifEmpty { null }
        genre = tags.joinToString().ifEmpty { null }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toSChapter() = SChapter.create().apply {
        url = this@ComicDto.url
        // Exclusive comics need an access code or a purchase
        name = if (exclusive) "🔒 $title" else title
        issueNumber?.let { chapter_number = it.toFloat() }
        date_upload = DATE_FORMAT.tryParseDate(date, ZoneOffset.UTC)
    }
}

@Serializable
class ReaderComicDto(val pages: List<String>)

// Like the site: the text in this language, else the Spanish one, else any
private fun Map<String, String?>.localized(lang: String) = listOf(this[lang], this["es"]).plus(values).firstOrNull { !it.isNullOrBlank() }

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
