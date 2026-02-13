package eu.kanade.tachiyomi.extension.en.kagane

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class SearchDto(
    val content: List<Book>,
    val last: Boolean,
) {
    fun hasNextPage() = !last

    @Serializable
    class Book(
        @SerialName("series_id")
        val id: String,
        @SerialName("title")
        val name: String,
        @SerialName("source_id")
        val source: String,
        @SerialName("current_books")
        val booksCount: Int,
        @SerialName("start_year")
        val releaseDate: Int?,
        @SerialName("cover_image_id")
        val coverImage: String? = null,
    ) {

        fun toSManga(apiUrl: String, showSource: Boolean): SManga = SManga.create().apply {
            title = if (showSource) "${name.trim()} [$source]" else name
            url = id
            thumbnail_url = coverImage?.let { "$apiUrl/api/v2/image/$it" }
        }
    }
}

@Serializable
class AlternateSeries(
    @SerialName("current_books")
    val booksCount: Int,
    @SerialName("start_year")
    val releaseDate: Int?,
)

@Serializable
class DetailsDto(
    @SerialName("series_id")
    val id: String,
    @SerialName("source_id")
    val source: String,
    @SerialName("series_staff")
    val authors: List<StaffDto>,
    @SerialName("publication_status")
    val status: String,
    @SerialName("description")
    val summary: String?,
    val genres: List<GenreDto>,
    @SerialName("series_alternate_titles")
    val alternateTitles: List<AlternateTitles>,
    @SerialName("series_books")
    val books: List<BookDto> = emptyList(),
    @SerialName("series_links")
    val links: List<LinkDto> = emptyList(),
) {
    @Serializable
    class StaffDto(
        val name: String,
        val role: String,
    )

    @Serializable
    class GenreDto(
        @SerialName("genre_name")
        val name: String,
    )

    @Serializable
    class AlternateTitles(
        val title: String,
    )

    @Serializable
    class LinkDto(
        val label: String,
        val url: String,
    )

    @Serializable
    class BookDto(
        @SerialName("book_id")
        val id: String,
        @SerialName("series_id")
        val seriesId: String? = null,
        val title: String,
        @SerialName("published_on")
        val releaseDate: String?,
        @SerialName("page_count")
        val pagesCount: Int,
        @SerialName("sort_no")
        val number: Float,
    ) {
        fun toSChapter(useSourceChapterNumber: Boolean = false, fallbackSeriesId: String? = null): SChapter = SChapter.create().apply {
            val sId = seriesId ?: fallbackSeriesId ?: ""
            url = "$sId;$id;$pagesCount"
            name = title
            date_upload = dateFormat.tryParse(releaseDate)
            if (useSourceChapterNumber) {
                chapter_number = number
            }
        }
    }

    val sourceName: String
        get() = links.firstOrNull { it.label.lowercase() in officialSources }?.label
            ?: links.firstOrNull()?.label
            ?: source

    fun toSManga(): SManga = SManga.create().apply {
        val desc = StringBuilder()
        if (!summary.isNullOrBlank()) desc.append(summary + "\n\n")

        desc.append("Source: ").append(sourceName + "\n\n")

        if (alternateTitles.isNotEmpty()) {
            desc.append("Associated Name(s):\n\n")
            alternateTitles.forEach { desc.append("• ${it.title}\n") }
        }

        author = authors.filter { it.role == "Author" }.joinToString { it.name }
        description = desc.toString()
        genre = (listOf(sourceName) + genres.map { it.name }).joinToString()
        status = this@DetailsDto.status.toStatus()
    }

    private fun String.toStatus(): Int = when (this.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
    }
}

@Serializable
class ChallengeDto(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("cache_url")
    val cacheUrl: String,
    @SerialName("integrity_token")
    val integrityToken: String? = null,
    val pages: List<PageInfo> = emptyList(),
) {
    @Serializable
    class PageInfo(
        @SerialName("page_number")
        val pageNumber: Int,
        @SerialName("page_uuid")
        val pageUuid: String,
        val format: String,
    )

    fun getPageMapping(): Map<Int, String> = pages.associate { it.pageNumber to it.pageUuid }
}

@Serializable
class IntegrityDto(
    @SerialName("token")
    val token: String,
    @SerialName("exp")
    val exp: Long? = null,
)
