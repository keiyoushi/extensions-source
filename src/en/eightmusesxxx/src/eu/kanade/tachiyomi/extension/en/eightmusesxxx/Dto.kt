package eu.kanade.tachiyomi.extension.en.eightmusesxxx

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ComicListDto(val items: List<ComicDto>)

@Serializable
class ComicPageDto(
    val comic: ComicDto,
    val recommendations: List<ComicDto> = emptyList(),
)

@Serializable
class ComicDto(
    private val slug: String,
    private val name: String,
    private val categories: List<String> = emptyList(),
    @SerialName("page_count") private val pageCount: Int? = null,
    val pages: List<PageDto> = emptyList(),
    private val description: String? = null,
    private val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = pages.firstOrNull { it.label == "cover" }?.imageUrl
        genre = categories.joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(this@ComicDto.description)
            .field("Pages", pageCount?.toString())
            .build()
    }

    fun toSChapters() = chapters.reversed().map { it.toSChapter(slug) }
}

@Serializable
class PageDto(
    val label: String,
    @SerialName("image_url") val imageUrl: String,
)

@Serializable
class ChapterDto(
    private val slug: String,
    private val name: String,
) {
    fun toSChapter(comicSlug: String) = SChapter.create().apply {
        url = "$comicSlug/$slug"
        name = this@ChapterDto.name
    }
}
