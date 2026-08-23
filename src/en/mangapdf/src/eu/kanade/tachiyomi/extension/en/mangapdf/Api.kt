package eu.kanade.tachiyomi.extension.en.mangapdf

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal const val DEFAULT_API_URL = "https://api.coffeemanga.shop"

internal fun normalizeBaseUrl(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) return DEFAULT_API_URL

    // Strip all leading http:// or https:// prefixes (case-insensitive).
    // Handles users typing "https://https://api.example.com" etc.
    while (s.lowercase().startsWith("https://") || s.lowercase().startsWith("http://")) {
        s = if (s.lowercase().startsWith("https://")) {
            s.substring(8)
        } else {
            s.substring(7)
        }
    }

    s = s.trimStart('/')
    if (s.isEmpty()) return DEFAULT_API_URL

    return "https://$s"
}

private fun apiBuilder(baseUrl: String): HttpUrl.Builder =
    normalizeBaseUrl(baseUrl)
        .toHttpUrl()
        .newBuilder()
        .addPathSegment("api")
        .addPathSegment("v1")

internal fun popularUrl(
    baseUrl: String,
    page: Int,
): HttpUrl =
    apiBuilder(baseUrl)
        .addPathSegment("mihon")
        .addPathSegment("popular")
        .addQueryParameter("page", page.toString())
        .build()

internal fun latestUrl(
    baseUrl: String,
    page: Int,
): HttpUrl =
    apiBuilder(baseUrl)
        .addPathSegment("mihon")
        .addPathSegment("latest")
        .addQueryParameter("page", page.toString())
        .build()

internal fun searchUrl(
    baseUrl: String,
    query: String,
    page: Int,
): HttpUrl =
    apiBuilder(baseUrl)
        .addPathSegment("mihon")
        .addPathSegment("search")
        .addQueryParameter("q", query)
        .addQueryParameter("page", page.toString())
        .build()

internal fun mangaUrl(
    baseUrl: String,
    mangaId: String,
): HttpUrl =
    apiBuilder(baseUrl)
        .addPathSegment("mihon")
        .addPathSegment("manga")
        .addPathSegment(mangaId)
        .build()

internal fun pagesUrl(
    baseUrl: String,
    chapterId: String,
): HttpUrl =
    apiBuilder(baseUrl)
        .addPathSegment("mihon")
        .addPathSegment("chapter")
        .addPathSegment(chapterId)
        .addPathSegment("pages")
        .build()

internal fun mangaIdFromUrl(
    baseUrl: String,
    url: HttpUrl,
): String? {
    val configuredBase = normalizeBaseUrl(baseUrl).toHttpUrl()

    if (url.scheme != configuredBase.scheme ||
        url.host != configuredBase.host ||
        url.port != configuredBase.port
    ) {
        return null
    }

    val basePathSegments = configuredBase.pathSegments.filter { it.isNotEmpty() }
    val targetPathSegments = url.pathSegments.filter { it.isNotEmpty() }

    if (targetPathSegments.size < basePathSegments.size + 5) {
        return null
    }

    for (i in basePathSegments.indices) {
        if (targetPathSegments[i] != basePathSegments[i]) {
            return null
        }
    }

    val offset = basePathSegments.size
    if (targetPathSegments[offset] != "api" ||
        targetPathSegments[offset + 1] != "v1" ||
        targetPathSegments[offset + 2] != "mihon" ||
        targetPathSegments[offset + 3] != "manga"
    ) {
        return null
    }

    val id = targetPathSegments[offset + 4]
    return id.ifBlank { null }
}
