package eu.kanade.tachiyomi.extension.uk.dgmanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

private fun mangaType(type: String?): String = when (type) {
    "manga" -> "Манґа"
    "manhwa" -> "Манхва"
    "manhua" -> "Маньхва"
    "western" -> "Вестерн"
    "Мальописи" -> "Мальопис"
    "novel" -> "Новела"
    else -> "ЧЗХ"
}

@Serializable
class CatalogResponseDto(
    val titles: List<SearchResponseTitlesDto>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
class SearchResponseTitlesDto(
    @SerialName("_id") private val id: String,
    private val title: String,
    private val cover: String,
    private val genres: Array<String> = arrayOf(),
    private val type: String? = null,
) {
    fun toSManga(ignoredGenres: Set<String>): SManga? = SManga.create().apply {
        // Hide manga by genres in Settings
        if (ignoredGenres.isNotEmpty()) {
            if (genres.any { it in ignoredGenres }) return null
        }
        // hide novels
        if (type == "novel") return null

        url = id
        title = this@SearchResponseTitlesDto.title
        thumbnail_url = cover
    }
}

@Serializable
class SMangaDto(
    @SerialName("_id") private val id: String,
    private val title: String,
    private val cover: String,
    private val alternativeTitles: Array<String>? = emptyArray<String>(),
    private val description: String? = null,
    private val type: String? = null,
    @SerialName("translation_status") private val translationStatus: String? = null,
    private val genres: Array<String>? = null,
    private val tags: Array<String>? = null,
    private val authorRef: List<SMangaStaffDto>? = null,
    private val illustratorRef: List<SMangaStaffDto>? = null,
    private val ageRating: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id
        title = this@SMangaDto.title
        thumbnail_url = cover
        description = buildString {
            append(this@SMangaDto.description)
            append("\n\nАльтернативні назви: ${alternativeTitles?.joinToString(",")}")
        }
        author = authorRef?.joinToString { it.name.toString() }?.takeIf { it.isNotBlank() }
        artist = illustratorRef?.joinToString { it.name.toString() }?.takeIf { it.isNotBlank() }
        genre = buildList {
            ageRating?.let { add(it) }
            add(mangaType(type))
            genres?.map { it }?.let { addAll(it) }
            tags?.map { it }?.let { addAll(it) }
        }.joinToString()
        status = when (translationStatus) {
            "Покинуто" -> SManga.CANCELLED
            "Завершено" -> SManga.COMPLETED
            "Перекладається" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class SMangaStaffDto(
    val name: String? = null,
)

@Serializable
class ChapterResponseDto(
    @SerialName("_id") val id: String,
    private val title: String,
    private val chapterNumber: Float,
    private val volumeNumber: Int,
    private val chapterName: String? = null,
    private val createdAt: String? = null,
    private val updatedAt: String? = null,
    private val teams: List<SMangaStaffDto>? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        val vol = volumeNumber.toString().removeSuffix(".0")
        val num = chapterNumber.toString().removeSuffix(".0")
        val time = createdAt ?: updatedAt ?: ""
        name = "Том $vol Розділ $num ${chapterName ?: ""}"
        url = "$id/$num/$title"
        date_upload = Instant.parseOrNull(time)?.toEpochMilliseconds() ?: 0L
        chapter_number = chapterNumber
        scanlator = teams?.map { it.name.toString() }?.joinToString { it }
    }
}

@Serializable
class PagesList(
    val pages: List<String>,
)
