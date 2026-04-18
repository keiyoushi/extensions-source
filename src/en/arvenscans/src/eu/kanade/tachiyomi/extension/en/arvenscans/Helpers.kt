package eu.kanade.tachiyomi.extension.en.arvenscans

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class DeepLink(
    val mangaSlug: String,
    val chapterSlug: String?,
)

fun parseDeepLink(query: String, baseHost: String): DeepLink? {
    val url = query.toHttpUrlOrNull() ?: return null
    if (url.host != baseHost) return null

    val pathSegments = url.pathSegments
    if (pathSegments.getOrNull(0) != SERIES_PATH_SEGMENT) return null

    val mangaSlug = pathSegments.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?: return null

    val chapterSlug = pathSegments.getOrNull(2)
        ?.takeIf { it.isNotBlank() }

    return DeepLink(mangaSlug, chapterSlug)
}

fun extractMangaSlug(rawUrl: String): String {
    val pathSegments = rawUrl.substringBefore('#').trim('/').split('/').filter { it.isNotBlank() }

    return when {
        pathSegments.size >= 2 && pathSegments[0] == SERIES_PATH_SEGMENT -> pathSegments[1]
        pathSegments.isNotEmpty() -> pathSegments[0]
        else -> throw Exception("Invalid manga url")
    }
}

fun extractChapterSlugs(rawUrl: String): Pair<String, String>? {
    val pathSegments = rawUrl.substringBefore('#').trim('/').split('/').filter { it.isNotBlank() }

    return when {
        pathSegments.size >= 3 && pathSegments[0] == SERIES_PATH_SEGMENT -> pathSegments[1] to pathSegments[2]
        pathSegments.size >= 2 -> pathSegments[pathSegments.size - 2] to pathSegments.last()
        else -> null
    }
}

fun buildSlugSearchTerms(slug: String): List<String> {
    val withSpaces = slug.replace('-', ' ')
    val withoutApostrophe = withSpaces.replace("'", " ")

    return listOf(
        withSpaces,
        withoutApostrophe,
        collapseSpaces(withoutApostrophe),
        slug,
    ).map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun collapseSpaces(text: String): String {
    var value = text
    while (value.contains("  ")) {
        value = value.replace("  ", " ")
    }
    return value
}
