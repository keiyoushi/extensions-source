package eu.kanade.tachiyomi.extension.all.sankakucomplex

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.escapeMarkdown
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class BookListDto(
    val pools: PageDto<PoolDto>,
    val series: PageDto<SeriesDto>,
)

@Serializable
class PageDto<T>(
    val data: List<T>,
)

@Serializable
class SeriesDto(
    private val id: String,
    private val name: String,
    @SerialName("sample_url") private val sampleUrl: String? = null,
    private val firstPool: PoolDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/series/$id"
        title = name
        thumbnail_url = sampleUrl ?: firstPool?.sampleUrl
    }
}

@Serializable
class SeriesDataDto(
    val seriesV2: SeriesDetailsDto,
)

@Serializable
class SeriesDetailsDto(
    private val name: String,
    private val nameJa: String? = null,
    private val descriptionEn: String? = null,
    private val description: String? = null,
    private val isCompleted: Boolean,
    val poolCount: Int,
    private val coverPool: CoverPoolDto? = null,
    private val tags: List<SeriesTagDto>,
    private val pools: List<SeriesPoolDto>,
) {
    val books: List<SeriesPoolDto>
        get() = pools.filter { it.status != "deleted" && !it.isBlacklisted }

    fun toDetails(manga: SManga, loggedIn: Boolean): SManga {
        val visible = books
        val tags = (tags + visible.flatMap { it.tags }).map { it.type to it.displayName }
        val text = descriptionEn ?: description
        val count = if (visible.size < poolCount && !loggedIn) "${visible.size} of $poolCount books, log in via WebView for all" else "${visible.size} books"

        return manga.apply {
            title = name
            thumbnail_url = thumbnail_url ?: coverPool?.coverPost?.previewUrl
            author = tags.namesOf(TAG_ARTIST)
            artist = author
            genre = tags.genres()
            status = if (isCompleted) SManga.COMPLETED else SManga.ONGOING
            description = MarkdownDescription.Builder()
                .info(count, tags.namesOf(TAG_LANGUAGE))
                .markdown(text?.dtextToMarkdown())
                .altTitles(listOfNotNull(nameJa).filter { it != name })
                .build()
        }
    }

    fun toSChapterList() = books.sortedByDescending { it.sequence }.map { it.toSChapter() }
}

@Serializable
class CoverPoolDto(
    val coverPost: CoverPostDto? = null,
)

@Serializable
class CoverPostDto(
    val previewUrl: String? = null,
)

@Serializable
class SeriesPoolDto(
    private val id: String,
    private val nameEn: String? = null,
    private val name: String? = null,
    val status: String,
    val isBlacklisted: Boolean,
    private val createdAt: String,
    @SerialName("sequenceV2") val sequence: Int,
    val tags: List<SeriesTagDto>,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/books/$id"
        name = (nameEn ?: this@SeriesPoolDto.name)?.replace('_', ' ') ?: "Book $sequence"
        chapter_number = sequence.toFloat()
        date_upload = createdAt.toLong() * 1000
    }
}

@Serializable
class SeriesTagDto(
    @SerialName("tagType") val type: Int,
    private val nameEn: String? = null,
    private val name: String,
) {
    val displayName: String
        get() = nameEn ?: name.replace('_', ' ')
}

@Serializable
class PoolDto(
    private val id: String,
    private val name: String?,
    @SerialName("name_ja") private val nameJa: String? = null,
    @SerialName("description_en") private val descriptionEn: String? = null,
    private val description: String? = null,
    @SerialName("created_at") private val createdAt: String,
    @SerialName("post_count") val postCount: Int,
    @SerialName("fav_count") private val favCount: Int? = null,
    private val rating: String? = null,
    @SerialName("sample_url") val sampleUrl: String? = null,
    private val tags: List<TagDto> = emptyList(),
    @SerialName("post_tags") private val postTags: List<TagDto> = emptyList(),
    @SerialName("redirect_to_signup") val redirectToSignup: Boolean = false,
    val posts: List<PostDto> = emptyList(),
) {
    fun toSManga() = name?.let {
        SManga.create().apply {
            url = "/books/$id"
            title = it
            thumbnail_url = sampleUrl
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    fun toDetails(manga: SManga): SManga {
        val tags = (tags + postTags).map { it.type to it.displayName }
        val text = descriptionEn ?: description

        return manga.apply {
            title = name!!
            thumbnail_url = sampleUrl ?: thumbnail_url
            author = tags.namesOf(TAG_ARTIST)
            artist = author
            genre = tags.genres()
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            description = MarkdownDescription.Builder()
                .info(
                    if (posts.size < postCount) "${posts.size} of $postCount pages" else "$postCount pages",
                    CONTENT_RATINGS[rating],
                    tags.namesOf(TAG_LANGUAGE),
                    favCount?.takeIf { it > 0 }?.let { "♥ $it" },
                )
                .markdown(text?.dtextToMarkdown())
                .field("Circle", tags.namesOf(TAG_STUDIO))
                .field("Characters", tags.namesOf(TAG_CHARACTER))
                .altTitles(listOfNotNull(nameJa).filter { it != name })
                .build()
        }
    }

    fun toSChapter() = SChapter.create().apply {
        url = "/books/$id"
        name = "Book"
        date_upload = DATE_FORMAT.tryParseDateTime(createdAt, API_ZONE)
    }
}

@Serializable
class TagDto(
    val type: Int,
    @SerialName("name_en") private val nameEn: String? = null,
    private val tagName: String,
) {
    val displayName: String
        get() = nameEn ?: tagName.replace('_', ' ')
}

@Serializable
class PostDto(
    val sequence: Int? = null,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("redirect_to_signup") val redirectToSignup: Boolean = false,
)

@Serializable
class SeriesVariablesDto(
    val seriesId: String,
)

@Serializable
class RefreshDto(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("access_token_ttl") val accessTokenTtl: Long,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("refresh_token_ttl") val refreshTokenTtl: Long,
)

@Serializable
class JwtDto(
    val exp: Long,
)

private const val TAG_ARTIST = 1
private const val TAG_STUDIO = 2
private const val TAG_COPYRIGHT = 3
private const val TAG_CHARACTER = 4
private const val TAG_GENRE = 5
private const val TAG_MEDIUM = 8
private const val TAG_LANGUAGE = 21

private val CONTENT_RATINGS = mapOf("s" to "G", "q" to "R15+", "e" to "R18+")

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

// The REST API's created_at is 10 hours behind the GraphQL epoch for the same pool, all year round
private val API_ZONE = ZoneOffset.ofHours(-10)

private val DTEXT_LINK = Regex(""""([^"]+)":(https?://\S+)""")
private val DTEXT_WIKI = Regex("""\[\[([^\]]+)]]""")

private fun List<Pair<Int, String>>.namesOf(type: Int) = filter { it.first == type }.map { it.second }.distinct().joinToString().ifEmpty { null }

// Format, genre and parody tags
private fun List<Pair<Int, String>>.genres() = listOf(TAG_MEDIUM, TAG_GENRE, TAG_COPYRIGHT)
    .flatMap { type -> filter { it.first == type }.map { it.second } }
    .distinct()
    .joinToString()
    .ifEmpty { null }

// Sankaku's DText: "text":url links and [[tag]] wiki links
private fun String.dtextToMarkdown(): String {
    val withoutWiki = DTEXT_WIKI.replace(this) { it.groupValues[1] }
    var last = 0
    return buildString {
        DTEXT_LINK.findAll(withoutWiki).forEach { match ->
            append(withoutWiki.substring(last, match.range.first).escapeMarkdown())
            append("[${match.groupValues[1].escapeMarkdown()}](${match.groupValues[2]})")
            last = match.range.last + 1
        }
        append(withoutWiki.substring(last).escapeMarkdown())
    }
}
