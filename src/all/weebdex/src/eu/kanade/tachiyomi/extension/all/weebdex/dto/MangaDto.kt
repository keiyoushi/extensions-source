package eu.kanade.tachiyomi.extension.all.weebdex.dto

import eu.kanade.tachiyomi.extension.all.weebdex.WeebDexHelper
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
class MangaListDto(
    private val data: List<MangaDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 0,
    val total: Int = 0,
) {
    val hasNextPage: Boolean
        get() = page * limit < total
    fun toSMangaList(coverQuality: String): List<SManga> {
        return data.map { it.toSManga(coverQuality) }
    }
}

@Serializable
class MangaDto(
    private val id: String,
    private val title: String,
    private val description: String = "",
    private val status: String? = null,
    val relationships: RelationshipsDto? = null,
) {
    @Contextual
    private val helper = WeebDexHelper()
    fun toSManga(coverQuality: String): SManga {
        return SManga.create().apply {
            title = this@MangaDto.title
            description = this@MangaDto.description
            status = helper.parseStatus(this@MangaDto.status)
            thumbnail_url = helper.buildCoverUrl(id, relationships?.cover, coverQuality)
            url = "/manga/$id"
            relationships?.let { rel ->
                author = rel.authors.joinToString(", ") { it.name }
                artist = rel.artists.joinToString(", ") { it.name }
                genre = rel.tags.joinToString(", ") { it.name }
            }
        }
    }
}

@Serializable
class RelationshipsDto(
    val authors: List<NamedEntity> = emptyList(),
    val artists: List<NamedEntity> = emptyList(),
    val tags: List<NamedEntity> = emptyList(),
    val cover: CoverDto? = null,
)

@Serializable
class NamedEntity(
    val name: String,
)

@Serializable
class CoverDto(
    val id: String,
    val ext: String = ".jpg",
)
