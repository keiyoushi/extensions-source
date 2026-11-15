package eu.kanade.tachiyomi.extension.en.baetoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class SearchDto(
    val data: List<TitleDto>,
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(
    val page: Int,
    val totalPages: Int,
)

@Serializable
class TitleDto(
    private val id: String,
    private val title: String,
    private val coverNormal: String? = null,
    private val author: String? = null,
    private val tags: List<String> = emptyList(),
    private val status: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@TitleDto.title
        thumbnail_url = coverNormal
        author = this@TitleDto.author?.takeUnless { it in PLACEHOLDER_CREATORS }
        genre = tags.joinToString().ifEmpty { null }
        status = parseStatus(this@TitleDto.status)
    }
}

@Serializable
class DetailDto(
    private val id: String,
    private val title: String,
    private val alternativeNames: List<String> = emptyList(),
    private val coverNormal: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val tags: List<String> = emptyList(),
    private val keywords: List<String> = emptyList(),
    private val type: String? = null,
    private val status: String? = null,
    private val released: String? = null,
    private val officialTranslation: Boolean = false,
    private val animeAdaptation: Boolean = false,
    private val description: String? = null,
    val chapters: List<ChapterDto>,
    val recommendations: List<TitleDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@DetailDto.title
        thumbnail_url = coverNormal
        author = this@DetailDto.author?.takeUnless { it in PLACEHOLDER_CREATORS }
        artist = this@DetailDto.artist?.takeUnless { it in PLACEHOLDER_CREATORS }
        genre = (listOfNotNull(type) + tags + keywords).distinct().joinToString().ifEmpty { null }
        status = parseStatus(this@DetailDto.status)
        description = MarkdownDescription.Builder()
            .info(
                released?.takeIf { YEAR_REGEX.matches(it) },
                "Official translation".takeIf { officialTranslation },
                "Anime adaptation".takeIf { animeAdaptation },
            )
            .description(this@DetailDto.description)
            .altTitles(alternativeNames.filter { it != title })
            .build()
    }
}

@Serializable
class ChapterDto(
    val number: String,
    val title: String,
    val sources: List<UploadDto>,
)

@Serializable
class UploadDto(
    private val id: String,
    private val datetime: String? = null,
    val sourceName: String,
) {
    fun toSChapter(chapter: ChapterDto, label: String = sourceName) = SChapter.create().apply {
        url = id
        name = chapter.title
        chapter.number.toFloatOrNull()?.let { chapter_number = it }
        scanlator = label
        date_upload = Instant.tryParse(datetime)
    }
}

@Serializable
class ReaderDto(
    val seriesId: String,
    val seriesTitle: String,
    val seriesCover: String? = null,
    val pages: List<String>,
) {
    fun toSManga() = SManga.create().apply {
        url = seriesId
        title = seriesTitle
        thumbnail_url = seriesCover
    }
}

@Serializable
class GenresDto(
    val initialGenres: List<String>,
)

private fun parseStatus(status: String?) = when (status?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}

// The site's author and artist for titles with no credited creators
private val PLACEHOLDER_CREATORS = setOf("Korean Creator", "Korean Studio")

// `released` also holds the date the site added a title
private val YEAR_REGEX = Regex("""\d{4}""")
