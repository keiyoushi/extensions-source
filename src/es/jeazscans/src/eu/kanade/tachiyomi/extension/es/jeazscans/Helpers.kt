package eu.kanade.tachiyomi.extension.es.jeazscans

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ── Date helpers ──────────────────────────────────────────────────────────────

private const val DATE_PATTERN = "dd MMM, yyyy"

private val DATE_LOCALE: Locale = Locale.forLanguageTag("es")

/**
 * Thread-local [SimpleDateFormat] – each thread gets its own instance.
 */
internal val dateFormatRef: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat(DATE_PATTERN, DATE_LOCALE) }

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
        else -> dateFormatRef.get().run {
            parse(date, ParsePosition(0))?.time ?: 0L
        }
    }
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
