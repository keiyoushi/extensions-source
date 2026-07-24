package eu.kanade.tachiyomi.extension.uk.faust

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlin.time.Instant

// ============================== Utilities ===============================
private fun mangaType(type: String?): String = when (type) {
    "Manga" -> "Манґа"
    "Manhwa" -> "Манхва"
    "Manhua" -> "Маньхва"
    "Oneshot" -> "Ваншот"
    "Webcomic" -> "Вебкомікс"
    "Doujinshi" -> "Доджінші"
    "Extra" -> "Екстра"
    "Comics" -> "Комікс"
    "Malyopys" -> "Мальопис"
    else -> "ЧЗХ"
}

// ============================== Search ===============================
@Serializable
class SearchRequestBody(
    var searchQuery: String? = null,
    var page: Int? = null,
    var pageSize: Int = 30,
    var sortBy: String? = null,
    var mangaType: String? = null,
    var translationStatus: String? = null,
    var publicationStatus: String? = null,
    var ageBracket: String? = null,
    var yearFrom: String? = null,
    var yearTo: String? = null,
    var minChapters: String? = null,
    var maxChapters: String? = null,
    var genreIds: JsonArray? = null,
    var excludeGenreIds: JsonArray? = null,
    var tagIds: JsonArray? = null,
    var excludeTagIds: JsonArray? = null,
)

// ============================== DTO ===============================
@Serializable
class SearchResponseDto(
    val page: Int = 0,
    val totalPages: Int = 0,
    val titles: List<SearchResponseTitlesDto>,
)

@Serializable
class SearchResponseTitlesDto(
    private val name: String,
    private val slug: String,
    private val coverImageUrl: String,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = coverImageUrl
    }
}

@Serializable
class SMangaDto(
    private val name: String,
    private val coverImageUrl: String,
    private val description: String? = null,
    private val slug: String,
    private val artists: List<Person>? = emptyList(),
    private val authors: List<Person>? = emptyList(),
    private val mangaType: String? = null,
    private val tags: List<Tags>? = emptyList(),
    private val genres: List<Tags>? = emptyList(),
    private val translationStatus: String? = null,
    private val averageRating: Float? = 0.0F,
    private val bookmarksCount: Int? = 0,
    private val englishName: String? = "",
    private val votesCount: Int? = 0,
    val volumes: List<VolumesListDto>,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = coverImageUrl
        description = buildString {
            append(this@SMangaDto.description)
            append("\n\nАльтернативні назви: $englishName")
            append("\nРейтинг: ${"%.2f".format(averageRating)}/5 ($votesCount), В закладках: $bookmarksCount")
        }
        artist = artists?.joinToString { "${it.firstName} ${it.lastName}".trim() }?.takeIf { it.isNotBlank() }
        author = authors?.joinToString { "${it.firstName} ${it.lastName}".trim() }?.takeIf { it.isNotBlank() }
        genre = buildList {
            add(mangaType(mangaType))
            genres?.map { it.name }?.let { addAll(it) }
            tags?.map { it.name }?.let { addAll(it) }
        }.joinToString()
        status = when (translationStatus) {
            "Inactive" -> SManga.CANCELLED
            "Translated" -> SManga.COMPLETED
            "Active" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class Person(
    val firstName: String? = null,
    val lastName: String? = null,
)

@Serializable
class Tags(
    val name: String,
)

@Serializable
class VolumesListDto(
    val chapters: List<ChaptersListDto>,
)

@Serializable
class ChaptersListDto(
    private val name: String,
    private val slug: String,
    private val volumeOrder: Float,
    private val number: Float,
    private val updatedDate: String? = null,
    private val createdDate: String? = null,
    private val translationTeams: List<Tags>? = emptyList(),
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val vol = volumeOrder.toString().removeSuffix(".0")
        val chp = number.toString().removeSuffix(".0")
        val time = updatedDate ?: createdDate ?: ""
        name = when {
            this@ChaptersListDto.name.contains("Розділ") -> "Том $vol ${this@ChaptersListDto.name}"
            else -> "Том $vol Розділ $chp ${this@ChaptersListDto.name}"
        }
        url = "$slug/$mangaSlug"
        date_upload = Instant.parseOrNull(time)?.toEpochMilliseconds() ?: 0L
        chapter_number = number
        scanlator = translationTeams?.joinToString { it.name }
    }
}

@Serializable
class ChapterResponseList(
    val pages: List<ResponseImagesList>,
)

@Serializable
class ResponseImagesList(
    val blobName: String,
    val pageNumber: Int,
)

// ============================== Filters DTO ===============================
@Serializable
class GenreListPageDto(
    val items: List<GenreDto>,
)

@Serializable
class GenreDto(
    val id: String,
    val name: String,
)

@Serializable
class FiltersDto(
    val genres: List<Pair<String, String>>,
    val tags: List<Pair<String, String>>,
)
