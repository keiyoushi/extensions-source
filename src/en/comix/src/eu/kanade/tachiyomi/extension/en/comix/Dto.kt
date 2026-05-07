package eu.kanade.tachiyomi.extension.en.comix

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
class Term(
    val id: Int? = null,
    @SerialName("term_id")
    private val termId: Int? = null,
    private val type: String = "",
    val title: String,
    private val slug: String = "",
    private val count: Int? = null,
)

@Serializable
class Manga(
    @SerialName("hid")
    @JsonNames("hash_id")
    private val hashId: String,
    private val title: String,
    @JsonNames("alt_titles")
    @SerialName("altTitles")
    private val altTitles: List<String> = emptyList(),
    private val synopsis: String?,
    private val type: String,
    private val poster: Poster,
    private val status: String,
    @SerialName("is_nsfw")
    private val isNsfw: Boolean = false,
    private val contentRating: String? = null,
    private val author: List<Term>?,
    private val artist: List<Term>?,
    private val genre: List<Term>?,
    private val theme: List<Term>?,
    private val demographic: List<Term>?,
    @JsonNames("rated_avg", "ratedAvg")
    private val ratedAvg: Double = 0.0,
) {
    @Serializable
    class Poster(
        private val small: String = "",
        private val medium: String,
        private val large: String,
    ) {
        fun from(quality: String?) = when (quality) {
            "large" -> large
            "small" -> small.ifEmpty { medium }
            else -> medium
        }
    }

    private val isNsfwTagged: Boolean
        get() = isNsfw || when (contentRating) {
            "pornographic", "erotica", "suggestive" -> true
            else -> false
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
    ) = SManga.create().apply {
        url = "/$hashId"
        title = this@Manga.title
        author = this@Manga.author.takeUnless { it.isNullOrEmpty() }?.joinToString { it.title }
        artist = this@Manga.artist.takeUnless { it.isNullOrEmpty() }?.joinToString { it.title }
        description = buildString {
            if (scorePosition == "top") {
                fancyScore.takeIf { it.isNotEmpty() }?.let {
                    append(it)
                    append("\n\n")
                }
            }

            synopsis.takeUnless { it.isNullOrEmpty() }
                ?.let { append(it) }
            altTitles.takeIf { altTitlesInDesc && it.isNotEmpty() }
                ?.let { altName ->
                    append("\n\n")
                    append("Alternative Names:\n")
                    append(altName.joinToString("\n"))
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
        thumbnail_url = this@Manga.poster.from(posterQuality)
        genre = getGenres()
    }

    fun toBasicSManga(posterQuality: String?) = SManga.create().apply {
        url = "/$hashId"
        title = this@Manga.title
        thumbnail_url = this@Manga.poster.from(posterQuality)
    }

    fun getGenres() = buildList {
        when (type) {
            "manhwa" -> add("Manhwa")
            "manhua" -> add("Manhua")
            "manga" -> add("Manga")
            else -> add("Other")
        }
        genre.takeUnless { it.isNullOrEmpty() }?.map { it.title }
            .let { addAll(it ?: emptyList()) }
        theme.takeUnless { it.isNullOrEmpty() }?.map { it.title }
            .let { addAll(it ?: emptyList()) }
        demographic.takeUnless { it.isNullOrEmpty() }?.map { it.title }
            .let { addAll(it ?: emptyList()) }
        if (isNsfwTagged) add("NSFW")
    }.distinct().joinToString()
}

@Serializable
class SingleMangaResponse(
    val result: Manga,
)

@Serializable
class Pagination(
    @SerialName("current_page") val page: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class SearchResultMeta(
    val page: Int,
    @SerialName("lastPage") val lastPage: Int,
)

@Serializable
class SearchResponse(
    val result: Items,
) {
    @Serializable
    class Items(
        val items: List<Manga>,
        val pagination: Pagination? = null,
        val meta: SearchResultMeta? = null,
    ) {
        fun hasNextPage(): Boolean = when {
            meta != null -> meta.page < meta.lastPage
            pagination != null -> pagination.page < pagination.lastPage
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
        val items: List<Chapter>,
        val pagination: Pagination? = null,
        val meta: SearchResultMeta? = null,
    ) {
        fun hasNextPage(): Boolean = when {
            meta != null -> meta.page < meta.lastPage
            pagination != null -> pagination.page < pagination.lastPage
            else -> false
        }
    }
}

@Serializable
class Chapter(
    @SerialName("id")
    private val id: Long,
    val number: Double,
    private val name: String = "",
    val votes: Int = 0,
    @SerialName("createdAtFormatted")
    private val createdAtFormatted: String? = null,
    val isOfficial: Boolean = false,
    private val group: Group? = null,
) {
    @Serializable
    class Group(
        val id: Int = 0,
        val name: String = "",
    )

    val scanlationGroupId: Int get() = group?.id ?: 0

    /** Approximate upload time in epoch ms, parsed from the relative [createdAtFormatted] string. */
    val uploadDateMs: Long get() = parseRelativeTimeToEpochMs(createdAtFormatted)

    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = "title/$mangaId/${this@Chapter.id}"
        name = buildString {
            append("Chapter ")
            append(this@Chapter.number.toString().removeSuffix(".0"))
            this@Chapter.name.takeUnless { it.isEmpty() }?.let { append(": $it") }
        }
        date_upload = uploadDateMs
        chapter_number = this@Chapter.number.toFloat()
        scanlator = group?.name
            ?: if (isOfficial) "Official" else "Unknown"
    }
}

/**
 * Parses strings like "3h", "1d", "1w", "3mos", "2y" into an approximate epoch-ms timestamp
 * relative to "now". Returns 0 when the input is missing or cannot be parsed.
 */
private fun parseRelativeTimeToEpochMs(formatted: String?): Long {
    if (formatted.isNullOrBlank()) return 0L
    val match = Regex("""(\d+(?:\.\d+)?)\s*(mos|y|w|d|h|m|s)""", RegexOption.IGNORE_CASE).find(formatted)
        ?: return 0L
    val amount = match.groupValues[1].toDoubleOrNull() ?: return 0L
    val unitMs = when (match.groupValues[2].lowercase()) {
        "s" -> 1_000L
        "m" -> 60_000L
        "h" -> 3_600_000L
        "d" -> 86_400_000L
        "w" -> 604_800_000L
        "mos" -> 2_592_000_000L
        "y" -> 31_536_000_000L
        else -> return 0L
    }
    val deltaMs = (amount * unitMs).toLong()
    return System.currentTimeMillis() - deltaMs
}

@Serializable
class ChapterResponse(
    val result: Items?,
) {
    @Serializable
    class Items(
        @SerialName("id")
        val chapterId: Long,
        val pages: List<PageImage> = emptyList(),
    )

    @Serializable
    class PageImage(
        val url: String,
    )
}
