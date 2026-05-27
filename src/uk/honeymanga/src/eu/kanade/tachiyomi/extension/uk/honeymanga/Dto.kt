package eu.kanade.tachiyomi.extension.uk.honeymanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ============================== Catalog Search ===============================
@Serializable
class CatalogResponseDto(
    val data: List<ResponseData>,
    val cursorNext: JsonObject? = null, // Next page doesn't exist if it's null. Content doesn't matter
) {
    val hasNextPage: Boolean get() = cursorNext?.isEmpty() == false
}

@Serializable
class ResponseData(
    private val id: String,
    private val posterId: String,
    private val title: String,
    private val type: String,
    private val genres: List<String>? = emptyList(),
) {
    fun toSManga(baseUrl: String, imageUrl: String, blockedTypes: Set<String>? = emptySet(), blockedGenres: Set<String>? = emptySet()): SManga? {
        if (blockedTypes != null && blockedTypes.contains(type)) {
            return null
        }
        if (blockedGenres != null && genres != null && blockedGenres.intersect(genres.toSet()).isNotEmpty()) {
            return null
        }

        return SManga.create().apply {
            title = this@ResponseData.title
            thumbnail_url = "$imageUrl/$posterId"
            url = "$baseUrl/book/$id"
        }
    }
}

// ============================== Manga ===============================
@Serializable
class CompleteHoneyMangaDto(
    val id: String,
    val posterId: String,
    val title: String,
    val description: String?,
    val type: String,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    val genresAndTags: List<String>? = null,
    val titleStatus: String? = null,
)

@Serializable
class HoneyMangaChapterPagesDto(
    val id: String,
    val resourceIds: Map<String, String>,
)

@Serializable
class HoneyMangaChapterResponseDto(
    val data: List<HoneyMangaChapterDto>,
)

@Serializable
class HoneyMangaChapterDto(
    val id: String,
    val volume: Int,
    val chapterNum: Int,
    val subChapterNum: Int,
    val mangaId: String,
    val lastUpdated: String,
    val isMonetized: Boolean,
)

// ============================== Request body ===============================
@Serializable
class SearchRequestBody(
    val page: Int,
    val pageSize: Int,
    val sort: SearchSort? = null,
    val filters: List<SearchFilter>? = null,
)

@Serializable
class SearchSort(
    var sortBy: String? = null,
    var sortOrder: String? = null,
)

@Serializable
class SearchFilter(
    val filterBy: String,
    val filterOperator: String,
    val filterValue: List<String>,
)
