package eu.kanade.tachiyomi.extension.id.wurmz

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaDetailsDto(
    val name: String,
    val alternateName: String? = null,
    val description: String? = null,
    val image: String? = null,
    val author: AuthorDto? = null,
    val genre: List<String>? = null,
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
    }
}

@Serializable
class AuthorDto(val name: String)

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_label") val label: String,
    @SerialName("chapter_sort") val sort: Float,
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

@Serializable
class LdJsonDto(
    val dangerouslySetInnerHTML: InnerHtmlDto,
)

@Serializable
class InnerHtmlDto(
    val __html: String,
)
