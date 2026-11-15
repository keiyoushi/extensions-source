package eu.kanade.tachiyomi.extension.all.twicomi

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
private val tokyoZone = ZoneId.of("Asia/Tokyo")

@Serializable
class TwicomiResponse<T>(
    val response: T,
)

@Serializable
class MangaListWithCount(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("manga_list") val mangaList: List<MangaListItem>,
)

@Serializable
class MangaListItem(
    private val author: AuthorDto,
    val tweet: TweetDto,
) {
    internal fun toSManga() = SManga.create().apply {
        val tweetAuthor = this@MangaListItem.author
        val timestamp = dateFormat.tryParseDateTime(tweet.tweetCreateTime, tokyoZone)
        val extraData = "$timestamp,${tweet.attachImageUrls.joinToString()}"

        url = "/manga/${tweetAuthor.screenName}/${tweet.tweetId}#$extraData"
        title = tweet.tweetText.split("\n").first()
        author = "${tweetAuthor.name} (@${tweetAuthor.screenName})"
        description = tweet.tweetText
        genre = (tweet.hashTags + tweet.tags).joinToString()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        thumbnail_url = tweet.attachImageUrls.firstOrNull()
        initialized = true
    }
}

@Serializable
class AuthorListWithCount(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("author_list") val authorList: List<AuthorWrapperDto>,
)

@Serializable
class AuthorWrapperDto(
    val author: AuthorDto,
)

@Serializable
class AuthorDto(
    @SerialName("screen_name") val screenName: String,
    val name: String,
    private val description: String? = null,
    @SerialName("profile_image") private val profileImage: String? = null,
) {
    internal fun toSManga() = SManga.create().apply {
        url = "/author/$screenName"
        title = name
        author = screenName
        description = this@AuthorDto.description
        thumbnail_url = profileImage
        initialized = true
    }
}

@Serializable
class TweetDto(
    @SerialName("tweet_id") val tweetId: String,
    @SerialName("tweet_text") val tweetText: String,
    @SerialName("attach_image_urls") val attachImageUrls: List<String>,
    val tags: List<String>,
    @SerialName("hash_tags") val hashTags: List<String>,
    @SerialName("tweet_create_time") val tweetCreateTime: String,
)
