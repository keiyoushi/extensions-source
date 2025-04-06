package eu.kanade.tachiyomi.extension.all.pornpics

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

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

    val httpUrl = "https://fake.com/$urlPart".toHttpUrl()
    if (addPath) {
        httpUrl.pathSegments.forEach { addPathSegment(it) }
    }
    if (addQuery) {
        httpUrl.queryParameterNames.forEach { name ->
            httpUrl.queryParameterValues(name).forEach { value ->
                addQueryParameter(name, value)
            }
        }
    }
    return this
}

fun addUrlPart(part: String) {
    "https://www.baidu.com/01/?ppppp=5&".toHttpUrl().newBuilder()
        .addUrlPart(part)
        .build()
        .let { println(it) }
}
