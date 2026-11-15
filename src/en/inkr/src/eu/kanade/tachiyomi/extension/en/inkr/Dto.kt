package eu.kanade.tachiyomi.extension.en.inkr

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

internal const val CHAPTER_FREE_MEMO = "free"
internal const val CHAPTER_ACCESSIBLE_MEMO = "accessible"
internal const val CHAPTER_TITLE_MEMO = "title"

@Serializable
class FilteredResponse(
    val data: List<String> = emptyList(),
)

@Serializable
class FilteredRequest(
    val limit: Int = 10000,
    val orStyleOrigin: List<String>? = null,
    val releaseStatus: String? = null,
    val andGenres: List<String>? = null,
)

@Serializable
class SearchRequest(
    val query: String,
)

@Serializable
class SearchResponse(
    val data: SearchData = SearchData(),
)

@Serializable
class SearchData(
    val title: List<String> = emptyList(),
)

@Serializable
class ContentJsonRequest(
    val fields: List<String>,
    val oids: List<String>,
    val includes: ChapterPagesIncludes? = null,
)

@Serializable
class ChapterPagesIncludes(
    val chapterPages: ChapterPagesInclude,
)

@Serializable
class ChapterPagesInclude(
    val fields: List<String>,
    val includes: EmptyIncludes,
    val includeKey: String,
)

@Serializable
class EmptyIncludes

@Serializable
class ContentMapResponse(
    val data: Map<String, JsonElement> = emptyMap(),
)

@Serializable
class TitleDto(
    val oid: String,
    val name: String = "",
    val thumbnailImage: String? = null,
    val releaseStatus: String? = null,
    val styleOrigin: String? = null,
    val keyGenreList: List<String> = emptyList(),
    val summary: List<String> = emptyList(),
    val pageReadCount: Long = 0,
    val latestChapterFirstPublishedDate: String? = null,
    val chapterList: List<String> = emptyList(),
    val titleCreators: List<TitleCreatorDto> = emptyList(),
    val isExplicit: Boolean = false,
    val monetizationType: String? = null,
    val isAvailable: Boolean = true,
    val isRemovedFromSale: Boolean = false,
) {
    fun toSManga(
        thumbnailUrl: String?,
        authors: String?,
        genres: String?,
    ) = SManga.create().apply {
        url = oid
        title = name
        thumbnail_url = thumbnailUrl
        author = authors
        artist = authors
        description = summary.joinToString("\n").takeIf { it.isNotEmpty() }
        genre = listOfNotNull(
            styleOrigin?.replace('-', ' ')?.replaceFirstChar { it.uppercase() },
            genres,
        ).filter { it.isNotBlank() }.joinToString()
        status = when (releaseStatus?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class TitleCreatorDto(
    val creator: String,
    val role: String? = null,
)

@Serializable
class NamedDto(
    val oid: String,
    val name: String = "",
    val url: String? = null,
)

@Serializable
class ChapterDto(
    val oid: String,
    val name: String = "",
    val order: Float = 0f,
    val firstPublishedDate: String? = null,
    val publishedDate: String? = null,
    val revenueType: String? = null,
    val coinPrice: Int = 0,
    val isPurchasedByCoin: Boolean = false,
    val isPurchasedBySub: Boolean = false,
) {
    val isFree: Boolean
        get() {
            val type = revenueType?.lowercase()
            return type == "ad" || type == "free"
        }

    fun isAccessible(isSubscriber: Boolean = false): Boolean {
        if (isFree || isPurchasedByCoin || isPurchasedBySub) return true
        if (!isSubscriber) return false
        val type = revenueType?.lowercase()
        return type == "subscription-only" || type == "mixed"
    }

    fun toSChapter(titleOid: String, showPaidMarker: Boolean, isSubscriber: Boolean = false): SChapter {
        val accessible = isAccessible(isSubscriber)
        val chapterName = if (!accessible && showPaidMarker) "🔒 $name" else name
        return SChapter.create().apply {
            url = oid
            name = chapterName
            chapter_number = order
            date_upload = Instant.tryParse(firstPublishedDate ?: publishedDate)
            memo = buildJsonObject {
                put(CHAPTER_FREE_MEMO, isFree)
                put(CHAPTER_ACCESSIBLE_MEMO, accessible)
                put(CHAPTER_TITLE_MEMO, titleOid)
            }
        }
    }
}

@Serializable
class ChapterPagesDto(
    val chapterPages: List<ChapterPageDto> = emptyList(),
)

@Serializable
class ChapterPageDto(
    val page: String? = null,
    val url: String,
)
