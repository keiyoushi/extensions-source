package eu.kanade.tachiyomi.extension.en.comix

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar

@Serializable
class Term(
    val title: String,
)

@Serializable
class TagSearchResponse(
    val result: List<TagSearchHit> = emptyList(),
)

@Serializable
class TagSearchHit(val id: Int)

@Serializable
class Manga(
    val hid: String,
    private val title: String,
    private val altTitles: List<String> = emptyList(),
    @SerialName("alt_titles") private val altTitlesOld: List<String> = emptyList(),
    private val synopsis: String? = null,
    private val type: String = "",
    private val poster: Poster? = null,
    private val status: String = "",
    private val contentRating: String = "safe",
    private val authors: List<Term>? = null,
    @SerialName("author") private val authorOld: List<Term>? = null,
    private val artists: List<Term>? = null,
    @SerialName("artist") private val artistOld: List<Term>? = null,
    private val genres: List<Term>? = null,
    @SerialName("genre") private val genreOld: List<Term>? = null,
    private val tags: List<Term>? = null,
    @SerialName("theme") private val themeOld: List<Term>? = null,
    private val demographics: List<Term>? = null,
    @SerialName("demographic") private val demographicOld: List<Term>? = null,
    private val formats: List<Term>? = null,
    private val ratedAvg: Double = 0.0,
    private val ratedCount: Long = 0L,
    private val followsTotal: Long = 0L,
    private val rank: Int = 0,
    private val year: Int? = null,
    private val originalLanguage: String? = null,
    private val url: String? = null,
) {
    @Serializable
    class Poster(
        private val small: String? = null,
        private val medium: String? = null,
        private val large: String? = null,
    ) {
        fun from(quality: String?) = when (quality) {
            "large" -> large ?: medium ?: small ?: ""
            "small" -> small ?: medium ?: large ?: ""
            else -> medium ?: large ?: small ?: ""
        }
    }

    private val fancyScore: String
        get() {
            if (ratedAvg == 0.0) return ""

            val score = ratedAvg.toBigDecimal()
            val stars = score.div(BigDecimal(2))
                .setScale(0, RoundingMode.HALF_UP).toInt()

            val scoreString = if (score.scale() == 0) {
                score.toPlainString()
            } else {
                score.stripTrailingZeros().toPlainString()
            }

            return buildString {
                append("★".repeat(stars))
                if (stars < 5) append("☆".repeat(5 - stars))
                append(" $scoreString")
            }
        }

    fun toSManga(
        posterQuality: String?,
        altTitlesInDesc: Boolean = false,
        scorePosition: String,
        showExtraInfo: Boolean = true,
        showTags: Boolean = false,
    ) = SManga.create().apply {
        url = this@Manga.url?.substringAfter("/title") ?: "/$hid"
        title = this@Manga.title

        val actualAuthors = authors ?: authorOld
        val actualArtists = artists ?: artistOld

        author = actualAuthors?.joinToString { it.title }
        artist = actualArtists?.joinToString { it.title }
        description = buildString {
            if (scorePosition == "top") {
                fancyScore.takeIf { it.isNotEmpty() }?.let {
                    append(it)
                    append("\n\n")
                }
            }

            synopsis?.takeUnless { it.isEmpty() }?.let { append(it) }

            val actualAltTitles = altTitles.ifEmpty { altTitlesOld }
            if (altTitlesInDesc && actualAltTitles.isNotEmpty()) {
                append("\n\n")
                append("Alternative Names:\n")
                append(actualAltTitles.joinToString("\n"))
            }

            if (showExtraInfo) {
                val extras = buildExtraInfo()
                if (extras.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append(extras.joinToString("\n"))
                }
            }

            if (scorePosition == "bottom") {
                fancyScore.takeIf { it.isNotEmpty() }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append(it)
                }
            }
        }
        initialized = true
        status = when (this@Manga.status) {
            "releasing" -> SManga.ONGOING
            "on_hiatus" -> SManga.ON_HIATUS
            "finished" -> SManga.COMPLETED
            "discontinued" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = this@Manga.poster?.from(posterQuality)
        genre = getGenres(showTags)
    }

    fun toBasicSManga(posterQuality: String?) = SManga.create().apply {
        url = this@Manga.url?.substringAfter("/title") ?: "/$hid"
        title = this@Manga.title
        thumbnail_url = this@Manga.poster?.from(posterQuality)
    }

    private fun buildExtraInfo(): List<String> = buildList {
        year?.takeIf { it > 0 }?.let { add("Year: $it") }
        originalLanguage?.takeIf { it.isNotBlank() }?.let { add("Language: ${it.uppercase()}") }
        contentRating.takeIf { it.isNotBlank() }?.let {
            add("Content rating: ${it.replaceFirstChar(Char::uppercase)}")
        }
        rank.takeIf { it > 0 }?.let { add("Rank: #$it") }
        ratedCount.takeIf { it > 0 }?.let { add("Rated by: $it") }
        followsTotal.takeIf { it > 0 }?.let { add("Followed by: $it") }
    }

    // The site has separate `genres`, `tags`, `formats`, and `demographics`
    // groupings but only the curated `genres` (plus the type and demographics)
    // belong in Mihon's "genre" chips by default — the `tags` list is dozens
    // of narrative descriptors and the site doesn't surface them in its own
    // detail layout. Users who want them back can flip the
    // "Show tags in genre chips" preference.
    private fun getGenres(showTags: Boolean) = buildList {
        when (type) {
            "manhwa" -> add("Manhwa")
            "manhua" -> add("Manhua")
            "manga" -> add("Manga")
            else -> add("Other")
        }
        (genres ?: genreOld)?.map { it.title }?.let { addAll(it) }
        (demographics ?: demographicOld)?.map { it.title }?.let { addAll(it) }
        if (showTags) tags?.map { it.title }?.let { addAll(it) }
        if (contentRating == "erotica" || contentRating == "pornographic") add("NSFW")
    }.distinct().joinToString()
}

@Serializable
class SingleMangaResponse(
    val result: Manga,
)

@Serializable
class Meta(
    val page: Int = 1,
    private val lastPage: Int = 1,
    @SerialName("last_page") private val lastPageOld: Int = 1,
    val hasNext: Boolean = false,
) {
    val actualLastPage: Int get() = maxOf(lastPage, lastPageOld)
}

@Serializable
class Pagination(
    val page: Int = 1,
    private val lastPage: Int = 1,
    @SerialName("last_page") private val lastPageOld: Int = 1,
) {
    val actualLastPage: Int get() = maxOf(lastPage, lastPageOld)
}

@Serializable
class SearchResponse(
    val result: Items,
) {
    @Serializable
    class Items(
        val items: List<Manga> = emptyList(),
        private val meta: Meta? = null,
        private val pagination: Pagination? = null,
    ) {
        fun hasNextPage(): Boolean = when {
            meta != null -> meta.page < meta.actualLastPage
            pagination != null -> pagination.page < pagination.actualLastPage
            else -> false
        }
    }
}

@Serializable
class ChapterDetailsResponse(
    val result: Items,
) {
    @Serializable
    class Items(
        val items: List<Chapter> = emptyList(),
        private val meta: Meta? = null,
        private val pagination: Pagination? = null,
    ) {
        fun hasNextPage(): Boolean = when {
            meta != null -> meta.page < meta.actualLastPage
            pagination != null -> pagination.page < pagination.actualLastPage
            else -> false
        }
    }
}

@Serializable
class Chapter(
    val id: Int,
    val number: Double,
    private val name: String = "",
    val votes: Int = 0,
    private val createdAtFormatted: String = "",
    val group: ScanlationGroup? = null,
    val isOfficial: Boolean = false,
) {
    @Serializable
    class ScanlationGroup(
        val id: Int? = null,
        val name: String,
    )

    companion object {
        private val DATE_REGEX = Regex("""^(\d+)\s*(s|m|h|d|w|mo|mos|y|yr|yrs|min|mins|sec|secs|hr|hrs|day|days|week|weeks|month|months|year|years)$""")
    }

    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "title/$mangaSlug/$id-chapter-${number.toString().removeSuffix(".0")}"
        name = buildString {
            append("Chapter ")
            append(this@Chapter.number.toString().removeSuffix(".0"))
            this@Chapter.name.takeUnless { it.isEmpty() }?.let { append(": $it") }
        }
        date_upload = parseRelativeDate(this@Chapter.createdAtFormatted)
        chapter_number = this@Chapter.number.toFloat()
        scanlator = if (this@Chapter.group != null) {
            this@Chapter.group.name
        } else if (this@Chapter.isOfficial) {
            "Official"
        } else {
            "Unknown"
        }
    }

    private fun parseRelativeDate(dateStr: String): Long {
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
}

@Serializable
class ChapterResponse(
    val result: ChapterResult? = null,
) {
    @Serializable
    class ChapterResult(
        val id: Int,
        val pages: Pages = Pages(),
    )

    @Serializable
    class Pages(
        val baseUrl: String = "",
        val items: List<PageDto> = emptyList(),
    )

    @Serializable
    class PageDto(
        val url: String,
    )
}
