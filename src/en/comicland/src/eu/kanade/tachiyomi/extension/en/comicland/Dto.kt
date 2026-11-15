package eu.kanade.tachiyomi.extension.en.comicland

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ApiResponse<T>(
    val data: T? = null,
)

@Serializable
class PageData(
    private val list: List<ComicDto>? = null,
    private val items: List<ComicDto>? = null,
    @SerialName("has_more") private val hasMore: Boolean? = null,
) {
    val comics: List<ComicDto>
        get() = list ?: items ?: emptyList()

    val hasNextPage: Boolean
        get() = hasMore ?: (comics.size == 20)
}

@Serializable
class ComicDto(
    private val slug: String,
    private val title: String,
    @SerialName("cover_url") private val coverUrl: String,
) {
    fun toSManga() = SManga.create().apply {
        title = this@ComicDto.title
        thumbnail_url = coverUrl
        url = slug
    }
}

@Serializable
class ComicDetailDto(
    val slug: String,
    private val title: String,
    @SerialName("cover_url") private val coverUrl: String,
    private val description: String? = null,
    private val authors: List<NameDto>? = null,
    private val artists: List<NameDto>? = null,
    private val genres: List<NameDto>? = null,
    val chapters: List<ChapterDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@ComicDetailDto.title
        thumbnail_url = coverUrl
        url = slug
        description = this@ComicDetailDto.description
        author = authors?.joinToString { it.name }
        artist = artists?.joinToString { it.name }
        genre = genres?.joinToString { it.name }
        initialized = true
    }
}

@Serializable
class NameDto(
    val name: String,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_index") private val chapterIndex: Float,
    private val title: String,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        name = title
        chapter_number = chapterIndex
        url = "/comic/$slug/chapter/${chapterIndex.toString().removeSuffix(".0")}"
    }
}

@Serializable
class PagesData(
    val pages: List<String> = emptyList(),
)
