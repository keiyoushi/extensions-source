package eu.kanade.tachiyomi.extension.en.kagane

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class GenreDto(
    val id: String,
    @SerialName("genre_name")
    val genreName: String,
)

@Serializable
class TagDto(
    val id: String,
    @SerialName("tag_name")
    val tagName: String,
)

@Serializable
class SourcesDto(
    val sources: List<SourceDto>,
)

@Serializable
data class SourceDto(
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_type") val sourceType: String, // "Official", "Unofficial", "Mixed"
    val title: String,
)

@Serializable
class SearchDto(
    val content: List<Book> = emptyList(),
    val last: Boolean = true,
    @SerialName("total_elements")
    val totalElements: Int = 0,
    @SerialName("total_pages")
    val totalPages: Int = 0,
) {
    fun hasNextPage() = !last

    @Serializable
    class Book(
        @SerialName("series_id")
        val id: String,
        val title: String,
        @SerialName("source_id")
        val sourceId: String? = null,
        @SerialName("current_books")
        val booksCount: Int,
        @SerialName("start_year")
        val startYear: Int? = null,
        @SerialName("cover_image_id")
        val coverImage: String? = null,
        @SerialName("alternate_titles")
        val alternateTitles: List<String> = emptyList(),
    ) {

        fun toSManga(domain: String, showSource: Boolean, sources: Map<String, String>): SManga = SManga.create().apply {
            title = if (showSource) "${this@Book.title.trim()} [${sources[this@Book.sourceId]}]" else this@Book.title.trim()
            url = id
            thumbnail_url = coverImage?.let { "$domain/api/v2/image/$it" }
        }
    }
}

@Serializable
class AlternateSeries(
    @SerialName("current_books")
    val booksCount: Int,
    @SerialName("start_year")
    val startYear: Int? = null,
)

@Serializable
class DetailsDto(
    val title: String,
    val description: String?,
    @SerialName("publication_status")
    val publicationStatus: String,
    @SerialName("upload_status")
    val uploadStatus: String,
    val format: String?,
    @SerialName("source_id")
    val sourceId: String?,
    @SerialName("series_staff")
    val seriesStaff: List<SeriesStaff> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val tags: List<Tag> = emptyList(),
    @SerialName("series_alternate_titles")
    val seriesAlternateTitles: List<AlternateTitle> = emptyList(),
    @SerialName("series_books")
    val seriesBooks: List<ChapterDto.Book> = emptyList(),
) {
    @Serializable
    class SeriesStaff(
        val name: String,
        val role: String,
    )

    @Serializable
    class Genre(
        @SerialName("genre_name")
        val genreName: String,
    )

    @Serializable
    class Tag(
        @SerialName("tag_name")
        val tagName: String,
    )

    @Serializable
    class AlternateTitle(
        val title: String,
        val label: String?,
    )

    fun toSManga(sourceName: String? = null, baseUrl: String = ""): SManga = SManga.create().apply {
        val desc = StringBuilder()

        // Add main description
        this@DetailsDto.description?.takeIf { it.isNotBlank() }?.let {
            desc.append(it.trim())
            desc.append("\n")
        }

        // Add source name
        if (sourceName != null && this@DetailsDto.sourceId != null) {
            if (desc.isNotEmpty()) desc.append("\n")
            desc.append("Source: [$sourceName]($baseUrl/sources/${this@DetailsDto.sourceId})\n")
        }

        // Add alternate titles at the end
        if (seriesAlternateTitles.isNotEmpty()) {
            if (desc.isNotEmpty()) desc.append("\n")
            desc.append("Associated Name(s):\n")
            seriesAlternateTitles.forEach {
                desc.append("• ${it.title}\n")
            }
        }

        // Extract authors and artists from staff (roles like "Author", "Artist", "Story", "Art")
        val authors = seriesStaff.filter {
            it.role.contains("Author", ignoreCase = true) || it.role.contains("Story", ignoreCase = true)
        }.map { it.name }.distinct()
        val artists = seriesStaff.filter {
            it.role.contains("Artist", ignoreCase = true) || it.role.contains("Art", ignoreCase = true)
        }
            .map { it.name }
            .distinct()
            .joinToString(", ")

        artist = artists
        author = authors.joinToString()
        description = desc.toString().trim()
        genre = genres.joinToString { it.genreName }
        status = this@DetailsDto.publicationStatus.toStatus()
    }

    private fun String.toStatus(): Int = when (this.uppercase()) {
        "ONGOING" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        "HIATUS" -> SManga.ON_HIATUS
        "CANCELLED" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class ChapterDto(
    @SerialName("series_books")
    val seriesBooks: List<Book>,
) {
    @Serializable
    class Book(
        @SerialName("book_id")
        val id: String,
        @SerialName("series_id")
        val seriesId: String? = null,
        val title: String,
        @SerialName("created_at")
        val createdAt: String?,
        @SerialName("page_count")
        val pagesCount: Int,
        @SerialName("sort_no")
        val number: Float,
        @SerialName("chapter_no")
        val chapterNo: String?,
        @SerialName("volume_no")
        val volumeNo: String?,
        val groups: List<Group> = emptyList(),
    ) {
        fun toSChapter(actualSeriesId: String, useSourceChapterNumber: Boolean = false, chapterTitleMode: String = "optional"): SChapter = SChapter.create().apply {
            url = "/series/$actualSeriesId/reader/$id"
            name = buildChapterName(chapterTitleMode)
            date_upload = dateFormat.tryParse(createdAt)
            if (useSourceChapterNumber) {
                chapter_number = number
            }
            scanlator = groups.joinToString(", ") { it.title }
        }

        private fun buildChapterName(mode: String = "optional"): String {
            val trimmedTitle = title.trim()
            return when (mode) {
                "optional" -> {
                    when {
                        trimmedTitle.isEmpty() && !chapterNo.isNullOrBlank() -> "Chapter $chapterNo"
                        else -> trimmedTitle
                    }
                }

                "always" -> {
                    when {
                        chapterNo.isNullOrBlank() -> trimmedTitle
                        trimmedTitle.isEmpty() -> "Chapter $chapterNo"
                        else -> "($chapterNo) $trimmedTitle"
                    }
                }

                else -> {}
            } as String
        }
    }

    @Serializable
    class Group(
        val title: String,
    )
    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
    }
}

@Serializable
class ChallengeDto(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("cache_url")
    val cacheUrl: String,
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    @SerialName("page_number")
    val pageNumber: Int,
    @SerialName("page_uuid")
    val pageUuid: String,
)

@Serializable
class IntegrityDto(
    val token: String,
    val exp: Long,
)
