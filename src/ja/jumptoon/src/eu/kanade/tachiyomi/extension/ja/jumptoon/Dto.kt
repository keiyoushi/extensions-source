package eu.kanade.tachiyomi.extension.ja.jumptoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Variables
@Suppress("unused")
@Serializable
class SearchVariables(
    private val q: String,
    private val offset: Int,
    private val first: Int,
)

@Suppress("unused")
@Serializable
class SeriesVariables(
    private val seriesId: String,
    private val hasSession: Boolean,
)

@Suppress("unused")
@Serializable
class ContentVariables(
    private val seriesId: String,
    private val id: String,
)

@Suppress("unused")
@Serializable
class RefreshRequestBody(
    @SerialName("grant_type") private val grantType: String,
    @SerialName("refresh_token") private val refreshToken: String,
)

@Serializable
class SessionCookie(
    val refreshToken: String,
)

@Serializable
class TokenResponse(
    @SerialName("id_token") val idToken: String,
)

@Serializable
class RankingResponse(
    val rankingFeedV2: RankingFeed,
)

@Serializable
class RankingFeed(
    val seriesList: List<RankingSeries>,
)

@Serializable
class RankingSeries(
    val series: Series,
)

@Serializable
class LatestResponse(
    val dailyUpdatedSeriesFeedList: List<DailyUpdatedFeed>,
)

@Serializable
class DailyUpdatedFeed(
    val rankedSeriesList: List<RankingSeries>,
)

@Serializable
class SearchResponse(
    val searchSeries: SearchSeries,
)

@Serializable
class SearchSeries(
    val totalCount: Int,
    val seriesList: List<Series>,
)

@Serializable
class Series(
    private val id: String,
    private val name: String,
    private val seriesThumbnailV2ImageUrl: String?,
    val pairedSeries: Series?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id
        title = name
        thumbnail_url = seriesThumbnailV2ImageUrl
    }
}

@Serializable
class DetailsResponse(
    val series: SeriesDetails,
    val seriesEpisodeList: Connection<Episode>,
    val seriesComicsList: Connection<Volume>,
)

@Serializable
class SeriesDetails(
    private val name: String,
    private val description: String?,
    private val copyright: String?,
    private val seriesThumbnailV2ImageUrl: String?,
    private val seriesStatusType: String?,
    private val isSuspended: Boolean,
    private val genreTypes: List<String>?,
    private val authorList: List<SeriesAuthor>?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = name
        description = buildList {
            this@SeriesDetails.description?.let(::add)
            copyright?.let(::add)
        }.joinToString("\n\n")
        thumbnail_url = seriesThumbnailV2ImageUrl
        author = authorList?.joinToString { it.author.name }
        genre = genreTypes?.joinToString { GENRES[it] ?: it }
        status = when {
            isSuspended -> SManga.ON_HIATUS
            seriesStatusType == "COMPLETED" || seriesStatusType == "ONE_SHOT" -> SManga.COMPLETED
            else -> SManga.ONGOING
        }
    }
}

@Serializable
class SeriesAuthor(
    val author: Author,
)

@Serializable
class Author(
    val name: String,
)

@Serializable
class Connection<T>(
    val edges: List<Edge<T>>,
)

@Serializable
class Edge<T>(
    val node: T,
)

@Serializable
class Episode(
    private val id: String,
    private val seriesId: String,
    private val number: String?,
    private val notation: String,
    private val title: String?,
    private val publishStartDatetime: String?,
    private val offerType: String?,
    private val userSeriesEpisode: UserSeriesEpisode?,
) {
    val isLocked: Boolean
        get() = offerType != "FREE" && userSeriesEpisode?.isUnlocked != true

    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id
        val lock = if (isLocked) "🔒 " else ""
        name = if (title != null) "$lock$notation - $title" else "$lock$notation"
        chapter_number = number?.toFloat() ?: -1f
        date_upload = publishStartDatetime?.toLong() ?: 0L
        memo = buildJsonObject {
            put("seriesId", seriesId)
        }
    }
}

@Serializable
class UserSeriesEpisode(
    private val isPurchased: Boolean,
    private val rentalFinishedAt: String?,
) {
    val isUnlocked: Boolean
        get() = isPurchased || (rentalFinishedAt?.toLong() ?: 0L) > System.currentTimeMillis()
}

@Serializable
class Volume(
    private val id: String,
    private val seriesId: String,
    private val number: String?,
    private val notation: String,
    private val publishStartDatetime: String?,
    private val previewPageCount: String?,
    private val userSeriesComics: UserSeriesComics?,
) {
    val isLocked: Boolean
        get() = userSeriesComics?.isPurchased != true

    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id
        val isPreview = isLocked && (previewPageCount?.toInt() ?: 0) > 0
        val lock = if (isLocked) "🔒 " else ""
        val preview = if (isPreview) "(Preview) " else ""
        name = lock + preview + notation
        chapter_number = number?.toFloat() ?: -1f
        date_upload = publishStartDatetime?.toLong() ?: 0L
        memo = buildJsonObject {
            put("seriesId", seriesId)
            put("type", if (isPreview) "preview" else "comics")
        }
    }
}

@Serializable
class UserSeriesComics(
    val isPurchased: Boolean,
)

@Serializable
class ContentResponse(
    val content: EpisodeContent,
)

@Serializable
class EpisodeContent(
    val seriesId: String,
    val number: String,
    val scrambleAlgorithmType: String,
    val pageList: List<ContentPage>,
)

@Serializable
class ContentPage(
    val width: String,
    val imageUrl: String,
)

private val GENRES = mapOf(
    "BATTLE_ACTION" to "バトル・アクション",
    "BOY" to "少年",
    "BUSINESS" to "お仕事もの",
    "COMEDY" to "ギャグ・コメディ",
    "EVERYDAY" to "日常",
    "FANTASY_SF" to "ファンタジー・SF",
    "FEMALE" to "女性",
    "GIRL" to "少女",
    "GOURMET" to "グルメ・料理",
    "HISTORY" to "歴史・時代物",
    "HORROR" to "ホラー・オカルト",
    "HUMAN_DRAMA" to "ヒューマンドラマ",
    "LOVE" to "恋愛",
    "LOVE_COMEDY" to "ラブコメ",
    "MYSTERY_SUSPENSE" to "ミステリー・サスペンス",
    "SCHOOL" to "青春・学園",
    "SPORTS" to "スポーツ",
    "UNDERGROUND_YANKEE" to "アングラ・ヤンキー",
    "YOUTH" to "青年",
)
