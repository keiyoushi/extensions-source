package eu.kanade.tachiyomi.extension.id.wurmz

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaDetailsDto(
    private val name: String,
    private val alternateName: String? = null,
    private val description: String? = null,
    private val image: String? = null,
    private val author: AuthorDto? = null,
    private val genre: List<String>? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        description = buildString {
            this@MangaDetailsDto.description?.let { append(it) }
            alternateName?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append("\n\n")
                append("Nama Alternatif: ")
                append(it)
            }
        }
        thumbnail_url = image
        author = this@MangaDetailsDto.author?.name
        genre = this@MangaDetailsDto.genre?.joinToString()
        initialized = true
    }
}

@Serializable
class AuthorDto(val name: String)

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
    val sourceSlug: String? = null,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_label") private val label: String,
    @SerialName("chapter_sort") private val sort: Float,
) {
    fun toSChapter(sourceSlug: String) = SChapter.create().apply {
        url = "/detail/$sourceSlug/chapter/$label"
        name = "Chapter $label"
        chapter_number = sort
    }
}

@Serializable
class PageListDto(
    val images: List<String>,
)
