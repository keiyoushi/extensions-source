package eu.kanade.tachiyomi.extension.tr.kairatoon

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import kotlin.time.Instant

@Serializable
class WebtoonDto(
    private val slug: String,
    private val title: String,
    @SerialName("title_en") private val titleEn: String? = null,
    @SerialName("title_aliases") private val titleAliases: List<String> = emptyList(),
    @SerialName("alternative_titles") private val alternativeTitles: List<String> = emptyList(),
    private val author: String? = null,
    private val artist: String? = null,
    private val synopsis: String? = null,
    private val genres: List<String> = emptyList(),
    private val tags: List<String> = emptyList(),
    private val status: String? = null,
    private val type: String,
    @SerialName("cover_image_path") private val coverImagePath: String? = null,
    @SerialName("rating_avg") private val ratingAvg: Double? = null,
    @SerialName("rating_count") private val ratingCount: Int = 0,
    @SerialName("is_hidden") private val isHidden: Boolean = false,
) {
    // Novels are text, and the site shows hidden series to its staff only
    val isComic get() = type != "novel" && !isHidden

    fun toSManga(baseUrl: HttpUrl) = SManga.create().apply {
        url = "/webtoon/$slug"
        title = this@WebtoonDto.title
        thumbnail_url = coverImagePath?.let { baseUrl.resolve(it)?.toString() }
        author = this@WebtoonDto.author?.takeIf(String::isNotBlank)
        artist = this@WebtoonDto.artist?.takeIf(String::isNotBlank)
        genre = (listOf(type.replaceFirstChar(Char::uppercase)) + genres + tags).distinct().joinToString()
        status = when (this@WebtoonDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus", "season_end" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(ratingAvg?.takeIf { ratingCount > 0 }, max = 5, votes = ratingCount)
            .description(synopsis)
            .altTitles((listOfNotNull(titleEn) + alternativeTitles + titleAliases).filter { it.isNotBlank() && it != title }.distinct())
            .build()
    }
}

@Serializable
class CountDto(val total: Int)

@Serializable
class ChapterDto(
    private val number: Float,
    private val title: String,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("is_premium_active") private val isPremiumActive: Boolean = false,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        // The site's chapter links: /bolum-15, or /bolum-15.5
        url = "$mangaUrl/bolum-${number.toString().removeSuffix(".0")}"
        name = if (isPremiumActive) "🔒 $title" else title
        chapter_number = number
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ReadDto(private val pages: List<String>) {
    fun toPages(baseUrl: HttpUrl) = pages.mapIndexed { index, path -> Page(index, imageUrl = baseUrl.resolve(path)!!.toString()) }
}

@Serializable
class GenreDto(val name: String)
