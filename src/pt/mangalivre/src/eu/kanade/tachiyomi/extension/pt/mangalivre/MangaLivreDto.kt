package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.toJsonString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer

@Serializable
class WrapperDto(
    val mangas: List<MangaDto>,
    val pagination: PaginationDto,
) {
    val hasNextPage get() = pagination.hasNextPage
}

@Serializable
class PaginationDto(
    val hasNextPage: Boolean,
)

@Serializable
class MangaDto(
    val id: String,
    val title: String,
    @SerialName("coverUrl")
    val thumbnailUrl: String? = null,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    val genres: List<String>? = null,
    val description: String? = null,
    val alternativeTitle: String? = null,
    val chapters: List<ChapterDto>? = null,
    val status: String? = null,
) {
    fun toSManga(useAlternativeTitle: Boolean) = SManga.create().apply {
        this.title = if (useAlternativeTitle && !alternativeTitle.isNullOrBlank()) alternativeTitle else this@MangaDto.title
        this.thumbnail_url = this@MangaDto.thumbnailUrl
        this.description = buildString {
            if (!this@MangaDto.description.isNullOrBlank()) {
                append(this@MangaDto.description)
            }

            if (!alternativeTitle.isNullOrBlank()) {
                appendLine("${"\n".repeat(3)} Nome altenativo: $alternativeTitle")
            }
        }
        author = authors?.joinToString()
        artist = artists?.joinToString()
        genre = genres?.joinToString()
        this@MangaDto.status?.let {
            status = when (it.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        this.url = id
    }

    fun toSChapterList(): List<SChapter> = chapters?.map { it.toSChapter(getSlug(), id) } ?: emptyList()

    private fun getSlug(): String {
        val noDiacritics = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(MARKS_REGEX, "")
        return noDiacritics.lowercase()
            .replace(NON_ALPHA_REGEX, "-")
            .trim('-')
    }

    companion object {
        private val MARKS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        private val NON_ALPHA_REGEX = "[^a-z0-9]+".toRegex()
    }
}

@Serializable
class ChapterReferenceDto(
    val mangaId: String,
    val chapterId: String,
)

@Serializable
class ChapterDto(
    val id: String,
    val number: String,
    val timestamp: Long,
) {
    fun toSChapter(slug: String, mangaId: String) = SChapter.create().apply {
        name = "Capítulo $number"
        date_upload = timestamp
        url = "/$slug/$number#${ChapterReferenceDto(mangaId, id).toJsonString()}"
    }
}

@Serializable
class PageDto(
    val pages: List<String>,
) {
    fun toPageList() = pages.mapIndexed { index, imageUrl ->
        Page(index, imageUrl = imageUrl)
    }
}
