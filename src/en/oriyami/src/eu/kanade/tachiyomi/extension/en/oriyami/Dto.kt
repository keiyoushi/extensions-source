package eu.kanade.tachiyomi.extension.en.oriyami

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import kotlin.time.Instant

@Serializable
class PagedList(
    val results: List<MangaItem>,
    @SerialName("has_next") val hasNext: Boolean,
)

@Serializable
class BrowseList(
    val results: List<MangaItem>,
    val next: String? = null,
)

@Serializable
class MangaItem(
    private val title: String,
    private val slug: String,
    private val type: String,
    @SerialName("cover_url") private val coverUrl: String?,
    private val status: String?,
    private val genres: List<String>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaItem.title
        thumbnail_url = coverUrl
        genres?.let { genre = (listOf(type) + it).distinct().joinToString() }
        status = parseStatus(this@MangaItem.status)
        memo = buildJsonObject { put(MEMO_TYPE, type) }
    }
}

@Serializable
class MangaDetails(
    private val title: String,
    val slug: String,
    val type: String,
    private val mangakas: List<String>,
    @SerialName("alternative_names") private val alternativeNames: List<String>,
    private val synopsis: String?,
    @SerialName("cover_url") private val coverUrl: String?,
    private val genres: List<String>,
    private val status: String?,
    private val demographic: String?,
    @SerialName("release_year") private val releaseYear: Int?,
    @SerialName("has_anime_adaptation") private val hasAnimeAdaptation: Boolean,
    @SerialName("avg_rating") private val avgRating: Double,
    @SerialName("rating_count") private val ratingCount: Int,
    private val sources: List<TrackerLink>,
    val scans: List<Scan>,
    @SerialName("relations_outgoing") val relations: List<MangaItem>,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDetails.title
        author = mangakas.joinToString().ifEmpty { null }
        thumbnail_url = coverUrl
        genre = (listOfNotNull(type, demographic) + genres).distinct().joinToString()
        status = parseStatus(this@MangaDetails.status)
        description = buildString {
            val info = listOfNotNull(
                if (ratingCount > 0) "★ ${String.format(Locale.ROOT, "%.1f", avgRating)} ($ratingCount)" else null,
                releaseYear?.toString(),
                if (hasAnimeAdaptation) "Has anime" else null,
            )
            if (info.isNotEmpty()) append(info.joinToString(" · "), "\n\n")
            synopsis?.let { append(it.trim(), "\n\n") }
            val altNames = alternativeNames.map { name ->
                name.replace(WHITESPACE_REGEX, " ").trim().capitalizeWords()
            }.filter { it.isNotEmpty() }
            if (altNames.isNotEmpty()) {
                append("**Alternative names:**\n")
                altNames.forEach { append("- ", it, "\n") }
                append("\n")
            }
            if (sources.isNotEmpty()) {
                append("**Links:**\n")
                sources.forEach { append("- [", it.label, "](", it.url, ")\n") }
            }
        }.trim().ifEmpty { null }
        memo = buildJsonObject { put(MEMO_TYPE, type) }
    }
}

@Serializable
class TrackerLink(
    @SerialName("site_label") val label: String,
    val url: String,
)

@Serializable
class Scan(
    private val number: String,
    private val kind: String,
    @SerialName("created_at") private val createdAt: String,
    @SerialName("published_at") private val publishedAt: String?,
) {
    fun toSChapter(slug: String, type: String) = SChapter.create().apply {
        url = "$slug/${kind.lowercase()}-$number"
        name = "$kind $number"
        date_upload = Instant.tryParse(publishedAt ?: createdAt)
        memo = buildJsonObject {
            put(MEMO_TYPE, type)
            put(MEMO_SLUG, slug)
            put(MEMO_NUMBER, number)
        }
    }
}

@Serializable
class ChapterPages(val pages: List<PageImage>)

@Serializable
class PageImage(@SerialName("image_url") val imageUrl: String)

@Serializable
class LibraryData(
    val initialMeta: LibraryMeta,
    val initialYears: List<Int>,
)

@Serializable
class LibraryMeta(
    val types: List<String>,
    val statuses: List<String>,
    val demographics: List<String>,
    val genres: List<String>,
)

private val WHITESPACE_REGEX = Regex("""\s+""")

// the site capitalises each word of the alternative names it shows
private fun String.capitalizeWords() = mapIndexed { i, char ->
    if (char.isLowerCase() && (i == 0 || !this[i - 1].isWordPart())) char.titlecaseChar() else char
}.joinToString("")

private fun Char.isWordPart() = isLetterOrDigit() || this in "'’" || category == CharCategory.NON_SPACING_MARK

private fun parseStatus(status: String?) = when (status) {
    "Ongoing" -> SManga.ONGOING
    "Completed" -> SManga.COMPLETED
    "Hiatus" -> SManga.ON_HIATUS
    "Cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

const val MEMO_TYPE = "type"
const val MEMO_SLUG = "slug"
const val MEMO_NUMBER = "number"
