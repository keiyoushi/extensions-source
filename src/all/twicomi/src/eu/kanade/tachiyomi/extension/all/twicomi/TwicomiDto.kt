package eu.kanade.tachiyomi.extension.all.twicomi

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("Asia/Tokyo")
}

@Serializable
class TwicomiResponse<T>(
    @SerialName("status_code") val statusCode: Int,
    val response: T,
)

@Serializable
class MangaListWithCount(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("manga_list") val mangaList: List<MangaListItem>,
)

@Serializable
class MangaListItem(
    val author: AuthorDto,
    val tweet: TweetDto,
) {
    internal fun toSManga() = SManga.create().apply {
        val tweetAuthor = this@MangaListItem.author
        val timestamp = runCatching {
            dateFormat.parse(tweet.tweetCreateTime)!!.time
        }.getOrDefault(0L)
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
class AuthorEditedDto(
    val description: String? = null,
    @SerialName("profile_image_large") val profileImageLarge: String,
)

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
    val id: Int,
    @SerialName("screen_name") val screenName: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val description: String? = null,
    @SerialName("profile_image") val profileImage: String? = null,
    @SerialName("manga_tweet_count") val mangaTweetCount: Int,
    @SerialName("is_hide") val isHide: Boolean,
    val flg: Int,
    val edited: AuthorEditedDto,
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
class TweetEditedDto(
    @SerialName("tweet_text") val tweetText: String,
)

@Serializable
class TweetDto(
    val id: Int,
    @SerialName("tweet_id") val tweetId: String,
    @SerialName("tweet_text") val tweetText: String,
    @SerialName("attach_image_urls") val attachImageUrls: List<String>,
    @SerialName("system_tags") val systemTags: List<String>,
    val tags: List<String>,
    @SerialName("hash_tags") val hashTags: List<String>,
    @SerialName("good_count") val goodCount: Int,
    @SerialName("retweet_count") val retweetCount: Int,
    @SerialName("retweet_per_hour") val retweetPerHour: Float,
    val index: Int,
    @SerialName("is_ignore") val isIgnore: Boolean,
    @SerialName("is_possibly_sensitive") val isPossiblySensitive: Boolean,
    val flg: Int,
    @SerialName("tweet_create_time") val tweetCreateTime: String,
    val edited: TweetEditedDto,
)
