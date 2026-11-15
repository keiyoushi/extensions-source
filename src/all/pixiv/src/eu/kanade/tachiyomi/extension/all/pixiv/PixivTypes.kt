package eu.kanade.tachiyomi.extension.all.pixiv

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal class PixivApiResponse(
    val error: Boolean = false,
    val message: String? = null,
    val body: JsonElement? = null,
)

@Serializable
internal class PixivResults(
    val illusts: List<PixivIllust>? = null,
)

@Serializable
internal class PixivIllust(
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
internal class PixivIllustDetails(
    val illust_details: PixivIllust? = null,
)

@Serializable
internal class PixivIllustsDetails(
    val illust_details: List<PixivIllust>? = null,
)

@Serializable
internal class PixivIllustPage(
    val urls: PixivIllustPageUrls? = null,
)

@Serializable
internal class PixivIllustPageUrls(
    val thumb_mini: String? = null,
    val small: String? = null,
    val regular: String? = null,
    val original: String? = null,
)

@Serializable
internal class PixivAuthorDetails(
    val user_id: String? = null,
    val user_name: String? = null,
)

@Serializable
internal class PixivSeriesDetails(
    val series: PixivSeries?,
)

@Serializable
internal class PixivSeries(
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
internal class PixivSeriesContents(
    val series_contents: List<PixivIllust>? = null,
)

@Serializable
internal class PixivRankings(
    val ranking: List<PixivRankingEntry>? = null,
)

@Serializable
internal class PixivRankingEntry(
    val illustId: String? = null,
    val rank: Int? = null,
)

// Data models for parsing __NEXT_DATA__ from /search/users endpoint
@Serializable
internal class PixivNextData(
    val props: PixivNextDataProps,
)

@Serializable
internal class PixivNextDataProps(
    val pageProps: PixivPageProps,
)

@Serializable
internal class PixivPageProps(
    val userIds: List<Long> = emptyList(),
    val userData: PixivUserData? = null,
)

@Serializable
internal class PixivUserData(
    val users: Map<String, PixivUserInfo> = emptyMap(),
)

@Serializable
internal class PixivUserInfo(
    val userId: String,
    val name: String,
    val image: String? = null,
    val imageBig: String? = null,
    val comment: String? = null,
)
