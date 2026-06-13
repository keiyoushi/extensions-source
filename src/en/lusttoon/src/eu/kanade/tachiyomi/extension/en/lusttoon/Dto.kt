package eu.kanade.tachiyomi.extension.en.lusttoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResponseDto(
    private val data: List<SearchItemDto> = emptyList(),
    private val meta: MetaDto? = null,
) {
    val mangas get() = data.filter { it.slug != null }.map { it.toSManga() }
    val hasNext get() = (meta?.currentPage ?: 1) < (meta?.lastPage ?: 1)
}

@Serializable
class MetaDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class SearchItemDto(
    private val name: String? = null,
    val slug: String? = null,
    private val urlImg: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = name ?: ""
        url = "/comic/$slug"
        thumbnail_url = urlImg?.replace("http://", "https://")
    }
}

@Serializable
class SerieDto(
    private val name: String? = null,
    val slug: String? = null,
    private val urlImg: String? = null,
    private val sinopsis: String? = null,
    private val state: StateDto? = null,
    private val genders: List<GenreDto>? = null,
    val chapters: List<ChapterDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = name ?: ""
        url = "/comic/$slug"
        thumbnail_url = urlImg?.replace("http://", "https://")
        description = sinopsis
        status = when (state?.estado?.lowercase()) {
            "en emision", "ongoing" -> SManga.ONGOING
            "completado", "finalizado", "completed" -> SManga.COMPLETED
            "cancelado", "cancelled" -> SManga.CANCELLED
            "pausado", "hiatus", "paused" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        genre = genders?.joinToString { it.name }?.takeIf { it.isNotBlank() }
    }
}

@Serializable
class StateDto(
    val estado: String,
)

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    val slug: String? = null,
    private val num: Float? = null,
    private val name: String? = null,
    val createdAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/comic/$mangaSlug/$slug"
        val chapName = this@ChapterDto.name
        this.name = if (!chapName.isNullOrBlank() && digitRegex.containsMatchIn(chapName)) {
            chapName
        } else {
            "Chapter ${num?.toString()?.removeSuffix(".0") ?: ""}".trim()
        }
        chapter_number = num ?: -1f
    }

    companion object {
        private val digitRegex = Regex("""\d""")
    }
}
