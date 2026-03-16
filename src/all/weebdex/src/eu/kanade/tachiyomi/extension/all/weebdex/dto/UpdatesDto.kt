package eu.kanade.tachiyomi.extension.all.weebdex.dto

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class UpdatesListDto(
    private val data: List<UpdateChapterDto> = emptyList(),
    private val map: UpdatesMapDto? = null,
    val page: Int = 1,
    val limit: Int = 0,
    val total: Int = 0,
) {
    val hasNextPage: Boolean
        get() = page * limit < total

    fun toSMangaList(coverQuality: String): List<SManga> {
        val mangaMap = map?.manga ?: return emptyList()
        val seenIds = linkedSetOf<String>()
        data.forEach { chapter -> chapter.mangaId?.let { seenIds.add(it) } }
        return seenIds.mapNotNull { mangaMap[it]?.toSManga(coverQuality) }
    }
}

@Serializable
class UpdatesMapDto(
    val manga: Map<String, MangaDto> = emptyMap(),
)

@Serializable
class UpdateChapterDto(
    private val relationships: UpdateChapterRelationshipsDto? = null,
) {
    val mangaId: String? get() = relationships?.manga?.id
}

@Serializable
class UpdateChapterRelationshipsDto(
    val manga: MangaIdDto? = null,
)

@Serializable
class MangaIdDto(
    val id: String,
)
