package eu.kanade.tachiyomi.extension.all.pornpics

import okhttp3.HttpUrl

internal const val QUERY_PAGE_SIZE = 20

internal fun HttpUrl.Builder.addQueryParameter(encodedName: String, encodedValue: Int) = addQueryParameter(encodedName, encodedValue.toString())

internal fun HttpUrl.Builder.addQueryParameterPage(page: Int) = addQueryParameter("limit", QUERY_PAGE_SIZE)
    .addQueryParameter("offset", (page - 1) * QUERY_PAGE_SIZE)
