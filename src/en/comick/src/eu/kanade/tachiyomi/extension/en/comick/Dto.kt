package eu.kanade.tachiyomi.extension.en.comick

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import kotlin.math.abs

/**
 * Resolves Comick CDN image keys to full URLs.
 *
 * b2key can arrive as:
 * - Full URL (starts with http/https)
 * - Relative path (e.g. "abc123.webp")
 */
object ImageResolver {

    private const val CDN_BASE = "https://meo.comick.pictures/"

    fun resolve(b2key: String?): String {
        if (b2key.isNullOrBlank()) return ""
        return if (b2key.startsWith("http://") || b2key.startsWith("https://")) {
            b2key
        } else {
            CDN_BASE + b2key.trimStart('/')
        }
    }

    fun resolveCover(coverUrl: String?, b2key: String?): String {
        if (!coverUrl.isNullOrBlank()) return coverUrl
        return resolve(b2key)
    }
}

/**
 * Search / Popular endpoint response item.
 * API returns a plain JSON array of these objects.
 */
@Serializable
class SearchMangaDto(
    val title: String? = null,
    val hid: String? = null,
    val slug: String? = null,
    @SerialName("md_titles") val mdTitles: List<AltTitleDto>? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("md_covers") val mdCovers: List<CoverDto>? = null,
    val status: Int? = null,
    val desc: String? = null,
    val country: String? = null,
)

@Serializable
class CoverDto(
    val b2key: String? = null,
    val w: Int? = null,
    val h: Int? = null,
)

fun SearchMangaDto.toSManga(prefs: DetailsPrefs = DetailsPrefs()): SManga {
    val dto = this
    return SManga.create().apply {
        title = resolveTitle(dto.title, dto.mdTitles, null, prefs)
            ?.takeUnless { isNumericIdTitle(it) }
            ?: "Unknown"
        url = when {
            !dto.hid.isNullOrBlank() -> "/comic/${dto.hid}"
            !dto.slug.isNullOrBlank() -> "/comic/${dto.slug}"
            else -> ""
        }
        thumbnail_url = ImageResolver.resolveCover(
            dto.coverUrl,
            dto.mdCovers?.firstOrNull()?.b2key,
        )
        status = when (dto.status) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            3 -> SManga.CANCELLED
            4 -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

/** Parse chapter number string to Float; null / empty -> -1f (oneshot/extra). */
fun parseChapterNumber(chap: String?): Float {
    if (chap.isNullOrBlank()) return -1f
    return chap.toFloatOrNull() ?: -1f
}

// ---------------------------------------------------------------------------
// Comic Details (GET /comic/{slug|hid})
// ---------------------------------------------------------------------------

@Serializable
class ComicDetailsResponse(
    val comic: ComicDetailsDto? = null,
    val authors: List<PersonDto>? = null,
    val artists: List<PersonDto>? = null,
    val demographic: String? = null,
)

@Serializable
class ComicDetailsDto(
    val title: String? = null,
    val hid: String? = null,
    val slug: String? = null,
    val desc: String? = null,
    val status: Int? = null,
    val country: String? = null,
    val year: Int? = null,
    val demographic: Int? = null,
    @SerialName("bayesian_rating") val bayesianRating: String? = null,
    @SerialName("rating_count") val ratingCount: Int? = null,
    val links: JsonObject? = null,
    @SerialName("md_titles") val mdTitles: List<AltTitleDto>? = null,
    @SerialName("md_covers") val mdCovers: List<CoverDto>? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("md_comic_md_genres") val genres: List<GenreWrapperDto>? = null,
    @SerialName("mu_comic_categories") val muComicCategories: List<MuCategoryEntryDto?>? = null,
    @SerialName("mu_comics") val muComics: MuComicsDto? = null,
)

@Serializable
class MuComicsDto(
    @SerialName("mu_comic_categories") val categories: List<MuCategoryEntryDto?>? = null,
)

@Serializable
class MuCategoryEntryDto(
    @SerialName("mu_categories") val category: MuCategoryTitleDto? = null,
)

@Serializable
class MuCategoryTitleDto(
    val title: String? = null,
)

@Serializable
class AltTitleDto(
    val title: String? = null,
    val lang: String? = null,
)

@Serializable
class PersonDto(
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
class GenreWrapperDto(
    @SerialName("md_genres") val mdGenres: GenreDto? = null,
)

@Serializable
class GenreDto(
    val name: String? = null,
    val slug: String? = null,
    val group: String? = null,
)

data class DetailsPrefs(
    val translatedTitle: Boolean = true,
    val showAltTitles: Boolean = true,
    val tagMode: String = "full",
    val showScore: Boolean = true,
)

fun ComicDetailsResponse.toSManga(original: SManga, prefs: DetailsPrefs = DetailsPrefs()): SManga {
    val c = comic ?: return original
    return original.apply {
        title = resolveTitle(c.title, c.mdTitles, c.links, prefs)
            ?.takeUnless { isNumericIdTitle(it) }
            ?: title.takeUnless { isNumericIdTitle(title) }
            ?: "Unknown"

        url = when {
            !c.hid.isNullOrBlank() -> "/comic/${c.hid}"
            !c.slug.isNullOrBlank() -> "/comic/${c.slug}"
            else -> url
        }

        thumbnail_url = ImageResolver.resolveCover(
            c.coverUrl,
            c.mdCovers?.firstOrNull()?.b2key,
        ).ifBlank { thumbnail_url }

        val primary = title
        val altTitles = c.mdTitles
            ?.mapNotNull { t ->
                t.title?.takeIf { name ->
                    name.isNotBlank() && !name.equals(primary, ignoreCase = true)
                }
            }
            ?.distinct()
            .orEmpty()

        val descParts = buildList {
            if (prefs.showScore) {
                val rating = c.bayesianRating?.toDoubleOrNull()
                if (rating != null) {
                    add(formatScore(rating, c.ratingCount))
                }
            }
            c.year?.let { add("Published: $it") }
            c.desc?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (prefs.showAltTitles && altTitles.isNotEmpty()) {
                add("Alternative titles:\n" + altTitles.joinToString("\n") { "• $it" })
            }
        }
        description = descParts.joinToString("\n\n").ifBlank { description }

        author = authors?.mapNotNull { it.name }?.joinToString(", ").orEmpty().ifBlank { author }
        artist = artists?.mapNotNull { it.name }?.joinToString(", ").orEmpty().ifBlank { artist }

        val originLabel = when (c.country?.lowercase()) {
            "jp" -> "Manga"
            "kr" -> "Manhwa"
            "cn" -> "Manhua"
            else -> null
        }
        // md_genres groups: Genre / Theme / Format
        val mdTags = c.genres.orEmpty().mapNotNull { wrapper ->
            val g = wrapper.mdGenres ?: return@mapNotNull null
            val name = g.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            name to g.group.orEmpty()
        }
        // Site "Tags" chips: MangaUpdates categories — handle both shapes:
        // - nested: mu_comics.mu_comic_categories[].mu_categories.title
        // - flat:   comic.mu_comic_categories[].mu_categories.title
        val muTagsNested = c.muComics?.categories.orEmpty().mapNotNull {
            it?.category?.title?.takeIf { t -> t.isNotBlank() }
        }
        val muTagsFlat = c.muComicCategories.orEmpty().mapNotNull {
            it?.category?.title?.takeIf { t -> t.isNotBlank() }
        }
        val muTags = (muTagsNested + muTagsFlat).distinct()
        val selected = when (prefs.tagMode) {
            "basic" -> mdTags.filter { (_, group) ->
                group.isEmpty() || group.equals("Genre", ignoreCase = true)
            }.map { it.first }
            else -> mdTags.map { it.first } + muTags
        }
        val demogLabel = demographic?.takeIf { it.isNotBlank() }
            ?: c.demographic?.let { demographicLabel(it) }
        genre = buildList {
            originLabel?.let { add(it) }
            demogLabel?.let { add(it) }
            addAll(selected)
        }.distinct().joinToString(", ")

        status = when (c.status) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            3 -> SManga.CANCELLED
            4 -> SManga.ON_HIATUS
            else -> status
        }
    }
}

private fun formatScore(rating: Double, count: Int?): String {
    // Comick scores are out of 10; map to 5 stars
    val filled = (rating / 2.0).toInt().coerceIn(0, 5)
    val stars = "★".repeat(filled) + "☆".repeat(5 - filled)
    val votes = if (count != null && count > 0) " (${"%,d".format(Locale.US, count)} ratings)" else ""
    return "$stars $rating$votes"
}

private fun demographicLabel(id: Int): String? = when (id) {
    1 -> "Shounen"
    2 -> "Shoujo"
    3 -> "Seinen"
    4 -> "Josei"
    5 -> "None"
    else -> null
}

/**
 * Title selection for the details page.
 *
 * When "Translated title" is on, ordered resolution:
 * 1. Resmi İngilizce link (engtl) → türetilen başlık alternatif İngilizce başlıklarla eşleştir → eşleşen alternatif
 * 2. links / referrers içindeki diğer http URL'ler → aynı eşleştirme
 * 3. Alternatif başlıklar — boşluk hariç 12 karakter kuralı (tam 12 → >12 en küçüğü → <12 en büyüğü)
 * 4. API comic.title
 *
 * When "Translated title" is off, return API title directly.
 */
private fun resolveTitle(
    apiTitle: String?,
    mdTitles: List<AltTitleDto>?,
    links: JsonObject?,
    prefs: DetailsPrefs,
): String? {
    if (!prefs.translatedTitle) return apiTitle
    return pickTitle(apiTitle, mdTitles, links)
}

/** Comick occasionally stores numeric/hash-like IDs instead of real titles. */
private fun isNumericIdTitle(title: String): Boolean = title.length >= 5 &&
    title.all { it.isDigit() || it in "abcdefABCDEF-" }

private fun isHttpUrl(value: String): Boolean = value.startsWith("http://", ignoreCase = true) ||
    value.startsWith("https://", ignoreCase = true)

/**
 * Ordered title resolution (user spec):
 * 1. İlk resmi İngilizce linkteki başlığı türet, alternatif İngilizce başlıklarla eşleştir ve eşleşen alternatifi döndür.
 * 2. Resmi İngilizce link yoksa; sitedeki links / referrers içindeki tüm http URL'leri tara, aynı eşleştirmeyi dene.
 * 3. Hiçbiri eşleşmezse alternatif başlıklar arasından boşluk hariç uzunluğa göre seç:
 *    tam 12 karakter → yoksa >12 içinde en küçüğü (12'ye en yakın) → yoksa <12 içinde en büyüğü.
 * 4. Alternatif yoksa API başlığı.
 */
private fun pickTitle(
    apiTitle: String?,
    mdTitles: List<AltTitleDto>?,
    links: JsonObject?,
): String? {
    val enTitles = mdTitles
        ?.mapNotNull { t ->
            t.title?.takeIf { name ->
                name.isNotBlank() && t.lang.equals("en", ignoreCase = true)
            }
        }
        ?.filterNot { isNumericIdTitle(it) }
        .orEmpty()

    fun findMatchingAlt(derivedTitle: String): String? {
        val normDerived = normalizeTitleForMatch(derivedTitle)
        // 1) exact normalized equality
        enTitles.firstOrNull { normalizeTitleForMatch(it) == normDerived }?.let { return it }
        // 2) tolerant close check on normalized forms
        return enTitles.firstOrNull { titlesAreClose(it, derivedTitle) }
    }

    // 1. Resmi İngilizce link(ler) — engtl string + urls.engtl[]
    val officialUrls = extractOfficialEnglishUrls(links)
    for (url in officialUrls) {
        val derived = titleFromOfficialUrl(url) ?: continue
        findMatchingAlt(derived)?.let { return it }
    }

    // 2. links / referrers içindeki diğer http URL'ler
    if (links != null) {
        val otherUrls = extractOtherUrls(links, officialUrls.toSet())
        for (url in otherUrls) {
            val derived = titleFromOfficialUrl(url) ?: continue
            findMatchingAlt(derived)?.let { return it }
        }
    }

    // 3. Alternatif başlıklar — boşluk hariç uzunluk kuralı
    if (enTitles.isNotEmpty()) {
        fun nonSpaceLen(s: String) = s.count { !it.isWhitespace() }
        val exact12 = enTitles.filter { nonSpaceLen(it) == 12 }
        if (exact12.isNotEmpty()) {
            return exact12.minWithOrNull(compareBy({ it.length }, { it }))
        }
        val over12 = enTitles.filter { nonSpaceLen(it) > 12 }
        if (over12.isNotEmpty()) {
            return over12.minWithOrNull(compareBy({ nonSpaceLen(it) }, { it.length }))
        }
        val under12 = enTitles.filter { nonSpaceLen(it) < 12 }
        if (under12.isNotEmpty()) {
            return under12.maxWithOrNull(compareBy({ nonSpaceLen(it) }, { it.length }))
        }
        return enTitles.first()
    }

    // 4. API title
    return apiTitle
}

private fun extractOfficialEnglishUrls(links: JsonObject?): List<String> {
    if (links == null) return emptyList()
    val result = mutableListOf<String>()
    (links["engtl"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { isHttpUrl(it) }?.let { result.add(it) }
    val urlsObj = links["urls"] as? JsonObject
    val engtlArray = urlsObj?.get("engtl") as? JsonArray
    engtlArray?.forEach { el ->
        val url = (el as? JsonObject)?.get("url") as? JsonPrimitive
        url?.contentOrNull?.trim()?.takeIf { isHttpUrl(it) }?.let { result.add(it) }
    }
    return result.distinct()
}

private fun extractOtherUrls(links: JsonObject?, exclude: Set<String>): List<String> {
    if (links == null) return emptyList()
    val all = mutableListOf<String>()
    fun collect(el: JsonElement) {
        when (el) {
            is JsonPrimitive -> el.contentOrNull?.trim()?.takeIf { isHttpUrl(it) }?.let { all.add(it) }
            is JsonObject -> el.values.forEach { collect(it) }
            is JsonArray -> el.forEach { collect(it) }
        }
    }
    collect(links)
    return all.filterNot { it in exclude }.distinct()
}

private fun normalizeTitleForMatch(title: String): String {
    return title.lowercase(Locale.ROOT)
        .replace("'", "")
        .replace("’", "")
        .replace("`", "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

/**
 * Check if two titles refer to the same work (normalized).
 * Matches when first words identical and normalized lengths differ by ≤20%, or exact equality.
 */
private fun titlesAreClose(title1: String, title2: String): Boolean {
    val n1 = normalizeTitleForMatch(title1)
    val n2 = normalizeTitleForMatch(title2)
    if (n1.isEmpty() || n2.isEmpty()) return false
    if (n1 == n2) return true
    val words1 = n1.split(Regex("\\s+"))
    val words2 = n2.split(Regex("\\s+"))
    if (words1.firstOrNull() != words2.firstOrNull()) return false
    val maxLen = maxOf(n1.length, n2.length)
    return abs(n1.length - n2.length) <= maxLen * 0.2
}

/** e.g. .../comic/Trading-Snacks-for-Gold-in-the-Apocalypse/69cb... -> "Trading Snacks For Gold In The Apocalypse" */
private fun titleFromOfficialUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val httpUrl = url.toHttpUrlOrNull() ?: return null
    val segments = httpUrl.pathSegments.filter { it.isNotBlank() }

    val markers = setOf("comic", "comics", "title", "series", "book")
    fun looksLikeTitle(seg: String): Boolean = seg.length > 3 &&
        !seg.all { ch -> ch.isDigit() || ch in "abcdef-" } &&
        seg.lowercase() !in OFFICIAL_URL_GENRE_SEGMENTS

    val afterComic = segments
        .dropWhile { it.lowercase() !in markers }
        .drop(1)
        .firstOrNull { looksLikeTitle(it) }

    // Webtoons URLs end with /list and put the title right before it
    // (e.g. /en/fantasy/tower-of-god/list), so "fantasy" must not win.
    val webtoonsTitle = segments
        .zipWithNext()
        .firstOrNull { (current, next) ->
            next.equals("list", ignoreCase = true) && looksLikeTitle(current)
        }
        ?.first

    val raw = afterComic
        ?: webtoonsTitle
        ?: segments
            .filter { seg ->
                seg.length > 5 && seg.contains('-') &&
                    !seg.all { ch -> ch.isDigit() || ch in "abcdef" } &&
                    seg.lowercase() !in OFFICIAL_URL_GENRE_SEGMENTS
            }
            .minByOrNull { it.length }
        ?: return null
    return raw
        // Webnovel appends a numeric series id to the slug:
        // .../comic/world's-best-martial-artist_17190724406942301
        .replace(Regex("""_\d+$"""), "")
        .replace('-', ' ')
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}

/** Genre/category slugs that must not be treated as a title when parsing official links. */
private val OFFICIAL_URL_GENRE_SEGMENTS = setOf(
    "action", "adventure", "comedy", "drama", "fantasy", "horror", "mystery",
    "psychological", "romance", "sci-fi", "slice-of-life", "sports", "supernatural",
    "thriller", "tragedy", "isekai", "martial-arts", "mecha", "historical", "school-life",
    "crime", "demons", "ecchi", "gore", "harem", "magic", "music", "smut", "yaoi", "yuri",
    "genre", "genres", "popular", "top-rated",
)

// ---------------------------------------------------------------------------
// Chapter List
// ---------------------------------------------------------------------------

@Serializable
class ChaptersResponse(
    val chapters: List<ChapterDto>? = null,
    val total: Int? = null,
    val limit: Int? = null,
)

@Serializable
class ChapterDto(
    val hid: String? = null,
    val chap: String? = null,
    val title: String? = null,
    val vol: String? = null,
    val lang: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("group_name") val groupName: List<String>? = null,
)

fun ChapterDto.toSChapter(): SChapter {
    val chapNum = parseChapterNumber(chap)
    val nameBuilder = StringBuilder()
    when {
        chapNum == -1f && !title.isNullOrBlank() -> nameBuilder.append(title)
        chapNum == -1f -> nameBuilder.append("Oneshot")
        else -> {
            nameBuilder.append("Chapter ")
            nameBuilder.append(chap!!.trim())
            if (!title.isNullOrBlank()) {
                nameBuilder.append(": ").append(title)
            }
        }
    }

    return SChapter.create().apply {
        url = "/chapter/${hid.orEmpty()}"
        name = nameBuilder.toString()
        chapter_number = chapNum
        scanlator = groupName?.filter { it.isNotBlank() }?.joinToString(", ").orEmpty()
        date_upload = parseDate(createdAt ?: updatedAt)
    }
}

private val REGEX_DATE_DECIMAL = Regex("""\.\d+""")
private val REGEX_DATE_UTC_OFFSET = Regex("""\+00:00$""")

fun parseDate(dateStr: String?): Long {
    if (dateStr.isNullOrBlank()) return 0L
    return try {
        java.time.Instant.parse(
            dateStr.replace(REGEX_DATE_DECIMAL, "").let {
                if (it.endsWith("Z")) it else "${it}Z"
            }.replace(REGEX_DATE_UTC_OFFSET, "Z"),
        ).toEpochMilli()
    } catch (_: Exception) {
        try {
            val cleaned = dateStr.take(19).replace(' ', 'T')
            java.time.LocalDateTime.parse(cleaned)
                .atZone(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}

// ---------------------------------------------------------------------------
// Page Loader
// ---------------------------------------------------------------------------

@Serializable
class ChapterPagesResponse(
    val chapter: ChapterPagesDto? = null,
)

@Serializable
class ChapterPagesDto(
    val hid: String? = null,
    val images: List<PageImageDto>? = null,
    @SerialName("md_images") val mdImages: List<PageImageDto>? = null,
)

@Serializable
class PageImageDto(
    val b2key: String? = null,
    val w: Int? = null,
    val h: Int? = null,
    val url: String? = null,
)
