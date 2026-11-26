package eu.kanade.tachiyomi.extension.en.comix

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import java.util.Calendar

const val CHAPTER_ID_MEMO = "chapterId"
const val CHAPTER_VOTES_MEMO = "votes"
const val CHAPTER_OFFICIAL_MEMO = "official"
const val CHAPTER_GROUP_ID_MEMO = "groupId"
const val CHAPTER_LIST_DEDUPLICATED_MEMO = "chapterListDeduplicated"
const val CHAPTER_LIST_BLACKLIST_MEMO = "chapterListBlacklist"
const val CHAPTER_DATA_VERSION_MEMO = "chapterDataVersion"

@Serializable
class Term(
    val title: String,
)

@Serializable
class TagSearchResponse(
    val result: List<TagSearchHit>,
)

@Serializable
class TagSearchHit(val id: Int, val label: String)

@Serializable
class Manga(
    private val title: String,
    private val altTitles: List<String> = emptyList(),
    private val synopsis: String? = null,
    private val type: String = "",
    private val poster: Poster? = null,
    private val status: String = "",
    private val contentRating: String = "safe",
    private val authors: List<Term>? = null,
    private val artists: List<Term>? = null,
    private val genres: List<Term>? = null,
    private val tags: List<Term>? = null,
    private val demographics: List<Term>? = null,
    private val formats: List<Term>? = null,
    private val publishers: List<Term>? = null,
    private val ratedAvg: Double = 0.0,
    private val ratedCount: Long = 0L,
    private val followsTotal: Long = 0L,
    private val rank: Int = 0,
    private val year: Int? = null,
    private val originalLanguage: String? = null,
    private val links: Map<String, String?>? = null,
    private val url: String,
) {
    @Serializable
    class Poster(
        private val medium: String? = null,
        private val large: String? = null,
    ) {
        fun from(baseUrl: HttpUrl, quality: String?) = when (quality) {
            "large" -> large ?: medium
            else -> medium ?: large
        }?.let { baseUrl.resolve(it)?.toString() }
    }

    fun toSManga(
        baseUrl: HttpUrl,
        posterQuality: String?,
        altTitlesInDesc: Boolean = false,
        scorePosition: String,
        showExtraInfo: Boolean = true,
        showTags: Boolean = false,
    ) = SManga.create().apply {
        url = this@Manga.url.substringAfter("/title")
        title = this@Manga.title

        author = authors?.joinToString { it.title }
        artist = artists?.joinToString { it.title }
        description = MarkdownDescription.Builder().apply {
            val score = ratedAvg.takeIf { it > 0 }
            if (scorePosition == "top") rating(score)
            markdown(synopsis)
            if (altTitlesInDesc) altTitles(altTitles)
            if (showExtraInfo) {
                field("Year", year?.takeIf { it > 0 }?.toString())
                field("Language", originalLanguage?.uppercase())
                field("Publisher", publishers?.joinToString { it.title }?.ifEmpty { null })
                field("Content rating", contentRating.replaceFirstChar(Char::uppercase))
                field("Rank", rank.takeIf { it > 0 }?.let { "#$it" })
                field("Rated by", ratedCount.takeIf { it > 0 }?.toString())
                field("Followed by", followsTotal.takeIf { it > 0 }?.toString())
                links(getTrackerLinks(), label = "Trackers")
            }
            if (scorePosition == "bottom") rating(score)
        }.build()
        initialized = true
        status = parseStatus()
        thumbnail_url = this@Manga.poster?.from(baseUrl, posterQuality)
        genre = getGenres(showTags)
    }

    fun toBasicSManga(baseUrl: HttpUrl, posterQuality: String?) = SManga.create().apply {
        url = this@Manga.url.substringAfter("/title")
        title = this@Manga.title
        status = parseStatus()
        thumbnail_url = this@Manga.poster?.from(baseUrl, posterQuality)
    }

    private fun parseStatus() = when (status) {
        "releasing" -> SManga.ONGOING
        "on_hiatus" -> SManga.ON_HIATUS
        "finished" -> SManga.COMPLETED
        "discontinued" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun getTrackerLinks(): List<Pair<String, String?>> {
        val linkMap = links ?: return emptyList()
        val siteNames = mapOf(
            "al" to "AniList",
            "mal" to "MyAnimeList",
            "mu" to "MangaUpdates",
            "md" to "MangaDex",
            "mb" to "MangaBaka",
        )
        val known = siteNames.map { (key, name) -> name to linkMap[key] }
        val unknown = linkMap.filterKeys { it !in siteNames }
            .map { (key, url) -> key.uppercase() to url?.takeIf { it.startsWith("http") } }
        return known + unknown
    }

    private fun getGenres(showTags: Boolean) = buildList {
        when (type) {
            "manhwa" -> add("Manhwa")
            "manhua" -> add("Manhua")
            "manga" -> add("Manga")
            else -> add("Other")
        }
        formats?.map { it.title }?.let { addAll(it) }
        genres?.map { it.title }?.let { addAll(it) }
        demographics?.map { it.title }?.let { addAll(it) }
        if (showTags) tags?.map { it.title }?.let { addAll(it) }
        if (contentRating == "erotica" || contentRating == "pornographic") add("NSFW")
    }.distinct().joinToString()
}

@Serializable
class ApiResponse<T>(val result: T)

@Serializable
class Meta(
    val page: Int,
    val lastPage: Int,
)

@Serializable
class MangaItems(
    val items: List<Manga>,
    val meta: Meta,
)

@Serializable
class ChapterItems(
    val items: List<Chapter>,
    val meta: Meta,
)

@Serializable
class ApiRefusal(
    val code: JsonPrimitive? = null,
    val error: String? = null,
)

@Serializable
class EncryptedBody(val e: String)

@Serializable
class Chapter(
    val id: Int,
    private val url: String,
    private val number: Double,
    private val volume: Int = 0,
    private val name: String? = null,
    private val votes: Int = 0,
    private val createdAtFormatted: String = "",
    private val group: Uploader? = null,
    private val creator: Uploader? = null,
    private val isOfficial: Boolean = false,
) {
    @Serializable
    class Uploader(
        val id: Int? = null,
        val name: String? = null,
    )

    fun toSChapter() = SChapter.create().apply {
        url = this@Chapter.url.removePrefix("/")
        memo = buildJsonObject {
            put(CHAPTER_ID_MEMO, id)
            put(CHAPTER_VOTES_MEMO, votes)
            put(CHAPTER_OFFICIAL_MEMO, isOfficial)
            group?.id?.let { put(CHAPTER_GROUP_ID_MEMO, it) }
        }
        name = buildString {
            if (volume > 0) append("Vol. $volume ")
            append("Chapter ")
            append(this@Chapter.number.toString().removeSuffix(".0"))
            this@Chapter.name?.takeUnless { it.isEmpty() }?.let { append(": $it") }
        }
        date_upload = parseRelativeDate(this@Chapter.createdAtFormatted)
        chapter_number = this@Chapter.number.toFloat()
        scanlator = group?.name ?: creator?.name ?: "Official".takeIf { isOfficial }
    }
}

@Serializable
class ChapterPagesResult(val pages: ChapterPages)

@Serializable
class ChapterPages(
    val items: List<PageDto>,
    private val alt: List<String> = emptyList(),
    private val alt2: JsonPrimitive? = null,
    @SerialName("alt_node") private val altNode: JsonPrimitive? = null,
) {
    fun altHosts(host: String, index: Int): List<String> {
        val label = host.substringBefore('.')
        val domain = host.substringAfter('.')
        val domains = listOfNotNull(domain, alt.getOrNull(index % alt.size.coerceAtLeast(1)), alt2?.content).distinct()
        val labels = listOfNotNull(label, altNode?.content).distinct()
        return labels.flatMap { l -> domains.map { "$l.$it" } } - host
    }

    @Serializable
    class PageDto(val url: String)
}

private val DATE_REGEX = Regex("""^(\d+)\s*(s|m|h|d|w|mo|mos|y|yr|yrs|min|mins|sec|secs|hr|hrs|day|days|week|weeks|month|months|year|years)$""")

fun parseRelativeDate(dateStr: String): Long {
    if (dateStr.isEmpty()) return 0L
    val trimmed = dateStr.trim().lowercase().removeSuffix(" ago")
    val match = DATE_REGEX.find(trimmed) ?: return 0L

    val amount = match.groupValues[1].toIntOrNull() ?: return 0L
    val unit = match.groupValues[2]

    val calendar = Calendar.getInstance()
    when (unit) {
        "s", "sec", "secs" -> calendar.add(Calendar.SECOND, -amount)
        "m", "min", "mins" -> calendar.add(Calendar.MINUTE, -amount)
        "h", "hr", "hrs" -> calendar.add(Calendar.HOUR_OF_DAY, -amount)
        "d", "day", "days" -> calendar.add(Calendar.DAY_OF_YEAR, -amount)
        "w", "week", "weeks" -> calendar.add(Calendar.WEEK_OF_YEAR, -amount)
        "mo", "mos", "month", "months" -> calendar.add(Calendar.MONTH, -amount)
        "y", "yr", "yrs", "year", "years" -> calendar.add(Calendar.YEAR, -amount)
    }
    return calendar.timeInMillis
}

@Serializable
class WafVerifyRequest(
    @SerialName("cf_turnstile_token") private val cfTurnstileToken: String,
)

@Serializable
class WafVerifyResponse(val success: Boolean)
