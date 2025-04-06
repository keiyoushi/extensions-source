package eu.kanade.tachiyomi.extension.all.pornpics

import okhttp3.HttpUrl

internal const val QUERY_PAGE_SIZE = 19

internal fun HttpUrl.Builder.addQueryParameter(encodedName: String, encodedValue: Int) =
    addQueryParameter(encodedName, encodedValue.toString())

internal fun HttpUrl.Builder.addQueryParameterPage(page: Int) =
    // Add +1 to requested image count per page,
    // Compare actual received count with pageSize to determine next page.
    this.addQueryParameter("limit", QUERY_PAGE_SIZE + 1)
        .addQueryParameter("offset", (page - 1) * QUERY_PAGE_SIZE)

internal fun HttpUrl.Builder.addUrlPart(
    urlPart: String?,
    addPath: Boolean = true,
    addQuery: Boolean = true,
): HttpUrl.Builder {
    if (urlPart == null) {
        return this
    }

    val sections = urlPart.split("?", limit = 2)
    val (pathSection, querySection) = when (sections.size) {
        // 01?s=123 ?=123
        2 -> sections
        // /01
        else -> listOf(sections[0], "")
    }

    // path
    if (addPath) {
        pathSection.split("/")
            .filter { it.isNotBlank() }
            .forEach { addPathSegment(it) }
    }

    // query
    if (addQuery) {
        querySection.split("&").filter { it.isNotBlank() }.forEach { param ->
            param.split("=", limit = 2).let {
                addQueryParameter(it[0], it.getOrNull(1))
            }
        }
    }
    return this
}
