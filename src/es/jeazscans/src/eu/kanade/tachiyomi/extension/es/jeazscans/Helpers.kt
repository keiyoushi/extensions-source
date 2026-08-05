package eu.kanade.tachiyomi.extension.es.jeazscans

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ── Date helpers ──────────────────────────────────────────────────────────────

private const val DATE_PATTERN = "dd MMM, yyyy"
private const val DATE_PATTERN_NO_COMMA = "dd MMM yyyy"
private const val DATE_PATTERN_SHORT = "dd MMM"

private val DATE_LOCALE: Locale = Locale.forLanguageTag("es")

/**
 * Thread-local [SimpleDateFormat] – each thread gets its own instance.
 */
internal val dateFormatRef: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat(DATE_PATTERN, DATE_LOCALE) }

/**
 * Thread-local [SimpleDateFormat] for the no-comma `dd MMM yyyy` format.
 */
internal val noCommaDateFormatRef: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat(DATE_PATTERN_NO_COMMA, DATE_LOCALE) }

/**
 * Thread-local [SimpleDateFormat] for the short `dd MMM` format.
 */
internal val shortDateFormatRef: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat(DATE_PATTERN_SHORT, DATE_LOCALE) }

/**
 * Regex matching an integer inside a relative-date string such as "hace 3 horas".
 */
internal val NUMBER_REGEX: Regex = Regex("""\d+""")

/**
 * Parse a chapter date string that may be absolute ("15 Jul, 2025") or
 * relative ("hace 3 horas", "ayer", "hoy").
 *
 * Returns epoch millis, or 0 when unparseable.
 */
internal fun parseChapterDate(date: String?): Long {
    if (date.isNullOrEmpty()) return 0L
    val lowercaseDate = date.lowercase()
    return when {
        lowercaseDate.contains("hace") -> {
            val number = NUMBER_REGEX.find(lowercaseDate)?.value?.toIntOrNull() ?: return 0L
            val cal = Calendar.getInstance()
            when {
                lowercaseDate.contains("segundo") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
                lowercaseDate.contains("minuto") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
                lowercaseDate.contains("hora") || lowercaseDate.contains("hr") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
                lowercaseDate.contains("día") || lowercaseDate.contains("dia") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
                lowercaseDate.contains("semana") -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
                lowercaseDate.contains("mes") -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
                lowercaseDate.contains("año") -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
                else -> 0L
            }
        }
        lowercaseDate.contains("ayer") -> {
            Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
        }
        lowercaseDate.contains("hoy") -> {
            Calendar.getInstance().timeInMillis
        }
        else -> parseAbsoluteDate(date)
    }
}

/**
 * Parse an absolute date, trying progressively shorter formats:
 * 1. `dd MMM, yyyy` (e.g. "15 Jul, 2025")
 * 2. `dd MMM yyyy` (no comma)
 * 3. `dd MMM` (day + month, assumes the current year, as used by the API)
 * Returns epoch millis, or 0 when no format matches.
 */
private fun parseAbsoluteDate(date: String): Long {
    val full = dateFormatRef.get().parse(date, ParsePosition(0))?.time
    if (full != null) return full

    val noComma = noCommaDateFormatRef.get().parse(date, ParsePosition(0))?.time
    if (noComma != null) return noComma

    val short = shortDateFormatRef.get().parse(date, ParsePosition(0))?.time
    if (short != null) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = short
            set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
        }
        return cal.timeInMillis
    }

    return 0L
}

// ── Locked-chapter payment helpers ────────────────────────────────────────────

private const val PAYMENT_UNTIL_PATTERN = "yyyy-MM-dd HH:mm:ss"

/**
 * Thread-local [SimpleDateFormat] for the chapters API `payment_until` timestamp.
 *
 * The timestamp is interpreted in the device's default timezone as a best-effort
 * snapshot; the site renders a live ticking countdown that the extension cannot
 * reproduce without a chapter-list reload.
 */
internal val paymentUntilFormatRef: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat(PAYMENT_UNTIL_PATTERN, Locale.ROOT) }

/**
 * Parse the chapters API `payment_until` timestamp (e.g. `2026-08-06 05:06:46`)
 * to epoch millis, or `null` when absent or unparseable.
 */
internal fun parsePaymentUntil(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return paymentUntilFormatRef.get().parse(value, ParsePosition(0))?.time
}

/**
 * Format a remaining duration as a minutes-granularity snapshot, e.g. `0d 11h 24m`.
 * Rounds up to the next minute so a not-yet-elapsed deadline never renders as
 * zero minutes.
 */
internal fun formatCountdown(remainingMillis: Long): String {
    val totalMinutes = (remainingMillis.coerceAtLeast(1L) + 59_999L) / 60_000L
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    return "${days}d ${hours}h ${minutes}m"
}

// ── Chapter-number regex ──────────────────────────────────────────────────────

internal val CHAPTER_NUMBER_REGEX: Regex = Regex("capitulo-([0-9.]+)", RegexOption.IGNORE_CASE)

// ── Page / reader helpers ─────────────────────────────────────────────────────

/**
 * Base64-decode a `data-verify` value, reverse the result, and return the image URL.
 * Returns `null` when the payload is not valid Base64 or does not start with "http".
 */
internal fun decodeVerifyToUrl(dataVerify: String): String? {
    val decoded = runCatching {
        java.util.Base64.getDecoder().decode(dataVerify).toString(Charsets.UTF_8)
    }.getOrNull() ?: return null

    val url = decoded.reversed().trim()
    if (!url.startsWith("http")) return null
    return url
}

// ── API fallback helpers ──────────────────────────────────────────────────────

internal val PATH_SLUG_CAP_REGEX: Regex =
    Regex("/leer/([^/]+)/capitulo-([0-9.]+)", RegexOption.IGNORE_CASE)

internal val MANGA_SLUG_REGEX: Regex =
    Regex("""MANGA_SLUG\s*=\s*["']([^"']+)["']""")

internal val CAP_INICIAL_REGEX: Regex =
    Regex("""CAP_INICIAL\s*=\s*["']([^"']+)["']""")

/**
 * Extract `(slug, cap)` from a reader [Document].
 *
 * Resolution order:
 * 1. Query parameters `?manga=…&cap=…`
 * 2. Path pattern `/leer/{slug}/capitulo-{cap}`
 * 3. Inline script variables `MANGA_SLUG` / `CAP_INICIAL`
 */
internal fun extractSlugAndCap(document: Document): Pair<String, String>? {
    val locationUrl = document.location().let {
        runCatching { it.toHttpUrlOrNull() }.getOrNull()
    }
    val slugFromQuery = locationUrl?.queryParameter("manga")?.trim().orEmpty()
    val capFromQuery = locationUrl?.queryParameter("cap")?.trim().orEmpty()
    if (slugFromQuery.isNotBlank() && capFromQuery.isNotBlank()) {
        return slugFromQuery to capFromQuery
    }

    val fromPath = PATH_SLUG_CAP_REGEX.find(document.location())?.groupValues
    if (fromPath != null && fromPath.size >= 3) {
        return fromPath[1] to fromPath[2]
    }

    val scriptContent = document.select("script").joinToString("\n") { it.data() + "\n" + it.html() }
    val slugFromScript = MANGA_SLUG_REGEX.find(scriptContent)?.groupValues?.getOrNull(1).orEmpty()
    val capFromScript = CAP_INICIAL_REGEX.find(scriptContent)?.groupValues?.getOrNull(1).orEmpty()

    if (slugFromScript.isNotEmpty() && capFromScript.isNotEmpty()) {
        return slugFromScript to capFromScript
    }

    return null
}

/**
 * Build the `api_lector.php` URL from the current reader [location] and the
 * extracted [slug] / [cap] pair.
 *
 * Returns `null` when [location] is not a valid HTTP URL.
 */
internal fun buildApiUrl(location: String, slug: String, cap: String): String? {
    val current = location.toHttpUrlOrNull() ?: return null

    return runCatching {
        current.newBuilder()
            .encodedPath("/api_lector.php")
            .setQueryParameter("slug", slug)
            .setQueryParameter("cap", cap)
            .build()
            .toString()
    }.getOrNull()
}

// ── Chapter-list API helpers ─────────────────────────────────────────────────

/**
 * Matches the numeric manga id inside a `manga.php?id=123` canonical URL.
 */
internal val MANGA_ID_URL_REGEX: Regex = Regex("""manga\.php\?id=(\d+)""", RegexOption.IGNORE_CASE)

/**
 * Matches the `const MANGA_ID = 123;` variable embedded in the manga page.
 */
internal val MANGA_ID_SCRIPT_REGEX: Regex = Regex("""MANGA_ID\s*=\s*(\d+)""")

/**
 * Matches the manga slug inside a `/manga/{slug}` or `/leer/{slug}/capitulo-{n}` URL.
 */
internal val MANGA_SLUG_URL_REGEX: Regex =
    Regex("""(?:/manga/|/leer/)([^/]+?)(?:/capitulo-[^/]+)?/?$""", RegexOption.IGNORE_CASE)

/**
 * Extract the manga id from a canonical `manga.php?id=…` URL, or `null` when absent.
 */
internal fun extractMangaIdFromUrl(url: String?): Int? = url?.let { MANGA_ID_URL_REGEX.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

/**
 * Extract the manga id from the `MANGA_ID` variable embedded in the page scripts.
 */
internal fun extractMangaIdFromScript(document: Document): Int? {
    val scriptContent = document.select("script").joinToString("\n") { it.data() + "\n" + it.html() }
    return MANGA_ID_SCRIPT_REGEX.find(scriptContent)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

/**
 * Extract the manga slug from the page's canonical `/manga/{slug}` link, falling
 * back to the first `/leer/{slug}/capitulo-…` reader anchor. Returns `null` when
 * no slug can be found.
 */
internal fun extractMangaSlug(document: Document): String? {
    val canonical = document.selectFirst("link[rel=canonical]")?.attr("abs:href").orEmpty()
    val source = canonical.ifEmpty { document.selectFirst("a[href*='/leer/']")?.attr("abs:href").orEmpty() }
    if (source.isBlank()) return null
    return MANGA_SLUG_URL_REGEX.find(source)?.groupValues?.getOrNull(1)
}

/**
 * Upper bound on the number of chapter pages fetched, guarding against a
 * non-terminating pagination response.
 */
internal const val MAX_CHAPTER_PAGES = 100

/**
 * Walk all chapter pages starting at [initialOffset], stopping when a page is
 * empty, the server reports `has_more = false`, `next_offset` is missing, or
 * `next_offset` does not advance past the current offset. [maxPages] prevents
 * unbounded loops.
 */
internal fun walkChapterPages(
    initialOffset: Int = 0,
    maxPages: Int = MAX_CHAPTER_PAGES,
    fetchPage: (offset: Int) -> ChapterPage,
): List<ChapterPage> {
    val pages = mutableListOf<ChapterPage>()
    var offset = initialOffset
    var fetched = 0

    while (fetched < maxPages) {
        val page = fetchPage(offset)
        pages += page
        fetched++

        if (page.isEmpty || !page.hasMore) break
        val next = page.nextOffset ?: break
        if (next <= offset) break

        offset = next
    }

    return pages
}
