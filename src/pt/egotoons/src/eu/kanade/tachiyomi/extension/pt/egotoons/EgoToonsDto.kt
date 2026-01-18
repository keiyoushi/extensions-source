package eu.kanade.tachiyomi.extension.pt.egotoons

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
data class EgoToonsMangaDto(
    val id: Int,
    val title: String,
    val status: String? = null,
    val synopsis: String? = null,
    val cover: String,
    val tags: List<TagDto> = emptyList(),
    val genres: List<TagDto> = emptyList(),
    val author: AuthorDto? = null,
    val works: Int = 0,
) {
    @Serializable
    data class AuthorDto(
        val firstName: String? = null,
        val lastName: String? = null,
    ) {
        val name: String
            get() = listOfNotNull(firstName, lastName).joinToString(" ")
    }

    @Serializable
    data class TagDto(
        val id: Int = 0,
        val name: String,
    )

    fun toSManga(): SManga = SManga.create().apply {
        title = this@EgoToonsMangaDto.title
        thumbnail_url = cover
        url = "obra/$id"
        author = this@EgoToonsMangaDto.author?.name
        description = synopsis?.let { Jsoup.parseBodyFragment(it).text() }
        genre = (tags + genres).joinToString { it.name }
        status = when (this@EgoToonsMangaDto.status) {
            "IN_PROGRESS" -> SManga.ONGOING
            "HIATUS" -> SManga.ON_HIATUS
            "COMPLETED" -> SManga.COMPLETED
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
data class EgoToonsPaginatedDto<T>(
    val items: List<T>,
    val pagination: EgoToonsPaginationDto,
)

@Serializable
data class EgoToonsPaginationDto(
    val offset: Int,
    val limit: Int,
    val total: Int,
    val pages: Int,
    val currentPage: Int,
) {
    val hasNextPage: Boolean
        get() = currentPage < pages
}

@Serializable
data class EgoToonsChapterDto(
    val id: Int,
    val chapter: Float,
    val title: String? = null,
    val status: String,
) {
    fun toSChapter(mangaId: Int): SChapter = SChapter.create().apply {
        val formattedNum = chapter.toString().removeSuffix(".0")
        val chapterName = "Capítulo $formattedNum"

        name = when {
            title.isNullOrBlank() -> chapterName
            title.contains(formattedNum) -> title
            else -> "$chapterName - $title"
        }
        chapter_number = chapter
        url = "obra/$mangaId/capitulo/$formattedNum"
    }
}
