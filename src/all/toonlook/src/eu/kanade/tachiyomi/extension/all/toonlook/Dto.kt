package eu.kanade.tachiyomi.extension.all.toonlook

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
internal class GenreDto(
    val name: String,
    val slug: String,
    val count: Int,
)

@Serializable
internal class ChapterData(
    val base: String,
    val lang: String,
    val data: String,
    val imageScramble: ImageScramble? = null,
)

@Serializable
internal class ImageScramble(
    val enabled: Boolean = false,
    val stripHeight: Int = 500,
    val salt: String,
)

@Serializable
internal class PageItem(
    val src: String,
)

@Serializable
internal class RelatedMangaDto(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        this.url = this@RelatedMangaDto.url
        this.title = this@RelatedMangaDto.title
        thumbnail_url = this@RelatedMangaDto.thumbnailUrl
    }
}

internal fun SManga.toRelatedMangaDto() = RelatedMangaDto(url, title, thumbnail_url)
