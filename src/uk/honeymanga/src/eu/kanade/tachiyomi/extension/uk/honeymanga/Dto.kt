package eu.kanade.tachiyomi.extension.uk.honeymanga

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

// ============================== Catalog Search ===============================
@Serializable
class CatalogResponseDto(
    val data: List<ResponseData>,
    private val cursorNext: JsonObject? = null, // Next page doesn't exist if it's null. Content doesn't matter
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
    private val adult: String,
) {
    fun toSManga(baseUrl: String, imageUrl: String, blockedTypes: Set<String>? = emptySet(), blockedGenres: Set<String>? = emptySet(), contentShown: String? = null): SManga? {
        if (blockedTypes?.contains(type) == true) return null
        if (blockedGenres?.intersect(genres?.toSet().orEmpty())?.isNotEmpty() == true) return null
        if (contentShown == "IN" && adult != "18+") return null
        if (contentShown == "NOT_IN" && adult == "18+") return null

        // Temporary solution: one of the translation teams left site and renamed all their manga, deleting all images in chapters
        // Link to that team page: https://honey-manga.com.ua/team/e0fc2dda-2547-4822-8c5a-3c6eb34f1da3
        // Hiding all their content by part of the message in title
        if (title.contains("Наша команда покидає Honey Manga")) return null

        return SManga.create().apply {
            title = this@ResponseData.title
            thumbnail_url = "$imageUrl/$posterId"
            url = "$baseUrl/book/$id"
        }
    }
}

// ============================== Manga ===============================
@Serializable
class CompleteMangaDto(
    private val id: String,
    private val posterId: String,
    private val title: String,
    private val description: String?,
    private val type: String,
    private val authors: List<String>? = null,
    private val artists: List<String>? = null,
    private val genresAndTags: List<String>? = null,
    private val titleStatus: String? = null,
    private val adult: String? = null,
) {
    fun toSManga(baseUrl: String, imageUrl: String): SManga = SManga.create().apply {
        title = this@CompleteMangaDto.title
        thumbnail_url = "$imageUrl/$posterId"
        url = "$baseUrl/book/$id"
        description = this@CompleteMangaDto.description
        genre = buildList {
            adult?.takeIf { it == "18+" }?.let { add(it) }
            add(type)
            addAll(genresAndTags.orEmpty())
        }.joinToString()
        artist = artists?.joinToString()
        author = authors?.joinToString()
        status = when (titleStatus.orEmpty()) {
            "Онгоінг" -> SManga.ONGOING
            "Завершено" -> SManga.COMPLETED
            "Покинуто" -> SManga.CANCELLED
            "Призупинено" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

// ============================== Chapter ===============================
@Serializable
class ChapterResponse(
    val data: List<ChapterResponseList>,
)

@Serializable
class ChapterResponseList(
    private val id: String,
    private val volume: Int,
    private val chapterNum: Int,
    private val subChapterNum: Int,
    private val mangaId: String,
    private val lastUpdated: String,
    private val isMonetized: Boolean,
) {
    fun toSChapter(baseUrl: String, hideLocked: Boolean): SChapter? {
        if (hideLocked && isMonetized) return null
        val prefix = if (isMonetized) "\uD83D\uDD12 " else ""
        val suffix = if (subChapterNum == 0) "" else ".$subChapterNum"
        return SChapter.create().apply {
            url = "$baseUrl/read/$id/$mangaId"
            name = "${prefix}Том $volume - Розділ $chapterNum$suffix"
            chapter_number = if (subChapterNum == 0) {
                chapterNum.toFloat()
            } else {
                chapterNum.toFloat() + (subChapterNum.toFloat() / 10f)
            }
            date_upload = Instant.parseOrNull(lastUpdated)?.toEpochMilliseconds() ?: 0L
            memo = buildJsonObject {
                put("locked", isMonetized)
            }
        }
    }
}

// ============================== Pages ===============================
@Serializable
class ChapterPages(
    private val resourceIds: Map<Int, String>,
) {
    fun toPageList(imageUrl: String): List<Page> = resourceIds.map { (index, imageId) ->
        Page(index = index, imageUrl = "$imageUrl/$imageId")
    }
}

// ============================== Search Request ===============================
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

// ============================== Chapter Request ===============================
@Serializable
class ChapterRequestBody(
    val mangaId: String,
    val page: Int,
    val pageSize: Int,
    val sortOrder: String,
)
