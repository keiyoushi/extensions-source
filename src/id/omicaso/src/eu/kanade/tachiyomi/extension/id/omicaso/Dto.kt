package eu.kanade.tachiyomi.extension.id.omicaso

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CatalogDto(
    val items: List<MangaDto>,
    @SerialName("has_more") val hasMore: Boolean,
    val sections: SectionsDto,
)

@Serializable
class SectionsDto(
    val trending: TrendingDto,
)

@Serializable
class TrendingDto(
    val all: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    private val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = cover
    }
}

@Serializable
class GenreDto(
    val name: String,
    val value: String,
)
