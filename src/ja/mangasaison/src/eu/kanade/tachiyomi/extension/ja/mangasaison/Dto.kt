package eu.kanade.tachiyomi.extension.ja.mangasaison

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Variables
@Suppress("unused")
@Serializable
class PopularVariables(
    private val genreIds: List<String>,
    private val limit: Int,
)

@Suppress("unused")
@Serializable
class LatestVariables(
    private val layoutId: String,
    private val limit: Int,
    private val offset: Int,
)

@Suppress("unused")
@Serializable
class SearchRequestBody(
    private val query: String,
    private val page: Int,
    private val hitsPerPage: Int,
    private val filters: String,
    private val attributesToRetrieve: List<String>,
    private val attributesToHighlight: List<String>,
    private val restrictSearchableAttributes: List<String>,
)

@Suppress("unused")
@Serializable
class DetailsVariables(
    private val titleId: Int,
    private val limit: Int,
    private val sortType: String,
)

// Responses
@Serializable
class RankingResponse(
    val storeLatestWeeklySalesRankings: List<StoreLatestWeeklySalesRanking>,
)

@Serializable
class StoreLatestWeeklySalesRanking(
    val ranking: List<Ranking>,
)

@Serializable
class Ranking(
    private val titleId: Int,
    private val title: Title,
) {
    fun toSManga() = SManga.create().apply {
        url = titleId.toString()
        title = this@Ranking.title.titleName
        thumbnail_url = this@Ranking.title.compressedTitleThumbnailPath
    }
}

@Serializable
class Title(
    val titleName: String,
    val compressedTitleThumbnailPath: String?,
)

@Serializable
class LatestResponse(
    val newArrivalContents: List<Hit>,
)

@Serializable
class SearchResponse(
    val hits: List<Hit>,
    private val page: Int,
    private val nbPages: Int,
) {
    fun hasNextPage() = page + 1 < nbPages
}

@Serializable
class Hit(
    private val titleId: Int,
    @JsonNames("contentName") private val titleName: String,
    @JsonNames("compressedContentThumbnailPath") private val compressedTitleThumbnailPath: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = titleId.toString()
        title = titleName
        thumbnail_url = compressedTitleThumbnailPath
    }
}

@Serializable
class DetailsResponse(
    val bookTitle: BookTitle,
    val bookContents: List<BookContent>,
)

@Serializable
class BookTitle(
    private val titleName: String,
    private val titleNameKana: String?,
    private val compressedTitleThumbnailPath: String?,
    private val publisherName: String?,
    private val magazineName: String?,
    private val longDescription: String?,
    private val authorNames: List<String>?,
    private val genres: List<Genre>?,
    private val hasLastVolume: Boolean?,
) {
    fun toSManga() = SManga.create().apply {
        title = titleName
        author = authorNames?.joinToString()
        description = buildString {
            longDescription?.let { append(Jsoup.parseBodyFragment(it).text()) }

            titleNameKana?.takeIf { it.isNotEmpty() }?.let {
                append("\n\nAlternative Title: $it")
            }

            publisherName?.takeIf { it.isNotEmpty() }?.let {
                append("\n\nPublisher: $it")
            }

            magazineName?.takeIf { it.isNotEmpty() }?.let {
                append("\n\nMagazine: $it")
            }
        }
        genre = genres?.mapNotNull { it.genre?.name }?.joinToString()
        status = if (hasLastVolume == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = compressedTitleThumbnailPath
    }
}

@Serializable
class Genre(
    val genre: GenreName?,
)

@Serializable
class GenreName(
    val name: String?,
)

@Serializable
class BookContent(
    private val distributionId: String,
    private val contentName: String,
    private val volumeNo: Int?,
    private val salesStartAt: String?,
    private val sampleDistributionId: String?,
    private val limitedReadPeriodBookContent: LimitedReadPeriodBookContent?,
    private val isPurchased: Boolean?,
    private val currentPrice: Int?,
) {
    private val isFree: Boolean
        get() = isPurchased == true || limitedReadPeriodBookContent != null || currentPrice == 0

    val isLocked: Boolean
        get() = !isFree && sampleDistributionId.isNullOrEmpty()

    val isPreview: Boolean
        get() = !isFree && !sampleDistributionId.isNullOrEmpty()

    fun toSChapter() = SChapter.create().apply {
        url = distributionId
        name = when {
            isLocked -> "🔒 "
            isPreview -> "🔒 (Preview) "
            else -> ""
        } + contentName
        date_upload = dateFormat.tryParseDateTime(salesStartAt)
        chapter_number = volumeNo?.toFloat() ?: -1f
        val readableId = limitedReadPeriodBookContent?.distributionId ?: sampleDistributionId.takeIf { isPreview }
        if (readableId != null) {
            memo = buildJsonObject {
                put("distributionId", readableId)
            }
        }
    }
}

@Serializable
class LimitedReadPeriodBookContent(
    val distributionId: String,
)

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.of("Asia/Tokyo"))

@Serializable
class ViewerResponse(
    val contentId: String,
    val contentType: String,
)

@Serializable
class AccessResponse(
    val url: String,
    val token: String,
)

@Serializable
class MdPackage(
    val spine: List<SpineItem>,
)

@Serializable
class SpineItem(
    val href: String,
)

@Serializable
class ImageRequestData(
    val localHeaderOffset: Long,
    val compressedSize: Long,
    val method: Int,
    val key: String,
)
