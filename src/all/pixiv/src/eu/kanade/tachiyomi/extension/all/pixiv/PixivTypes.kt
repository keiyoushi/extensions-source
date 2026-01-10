package eu.kanade.tachiyomi.extension.all.pixiv

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class PixivApiResponse(
    val error: Boolean = false,
    val message: String? = null,
    val body: JsonElement? = null,
)

@Serializable
internal data class PixivResults(
    val illusts: List<PixivIllust>? = null,
)

@Serializable
internal data class PixivIllust(
    val author_details: PixivAuthorDetails? = null,
    val comment: String? = null,
    val id: String? = null,
    val is_ad_container: Int? = null,
    val series: PixivSearchResultSeries? = null,
    val tags: List<String>? = null,
    val title: String? = null,
    val type: String? = null,
    val upload_timestamp: Long? = null,
    val url: String? = null,
    val x_restrict: String? = null,
)

@Serializable
internal data class PixivSearchResultSeries(
    val coverImage: String? = null,
    val id: String? = null,
    val title: String? = null,
    val userId: String? = null,
)

@Serializable
internal data class PixivIllustDetails(
    val illust_details: PixivIllust? = null,
)

@Serializable
internal data class PixivIllustsDetails(
    val illust_details: List<PixivIllust>? = null,
)

@Serializable
internal data class PixivIllustPage(
    val urls: PixivIllustPageUrls? = null,
)

@Serializable
internal data class PixivIllustPageUrls(
    val thumb_mini: String? = null,
    val small: String? = null,
    val regular: String? = null,
    val original: String? = null,
)

@Serializable
internal data class PixivAuthorDetails(
    val user_id: String? = null,
    val user_name: String? = null,
)

@Serializable
internal data class PixivSeriesDetails(
    val series: PixivSeries?,
)

@Serializable
internal data class PixivSeries(
    val caption: String? = null,
    val coverImage: JsonPrimitive? = null,
    val id: String? = null,
    val title: String? = null,
    /**
     * CAUTION: sometimes this isn't passed!
     */
    val userId: String? = null,
)

@Serializable
internal data class PixivSeriesContents(
    val series_contents: List<PixivIllust>? = null,
)

@Serializable
internal data class PixivRankings(
    val ranking: List<PixivRankingEntry>? = null,
)

@Serializable
internal data class PixivRankingEntry(
    val illustId: String? = null,
    val rank: Int? = null,
)

// Data models for parsing __NEXT_DATA__ from /search/users endpoint
@Serializable
internal data class PixivNextData(
    val props: PixivNextDataProps,
)

@Serializable
internal data class PixivNextDataProps(
    val pageProps: PixivPageProps,
)

@Serializable
internal data class PixivPageProps(
    val userIds: List<Long> = emptyList(),
    val userData: PixivUserData? = null,
)

@Serializable
internal data class PixivUserData(
    val users: Map<String, PixivUserInfo> = emptyMap(),
)

@Serializable
internal data class PixivUserInfo(
    val id: String,
    val name: String,
)
