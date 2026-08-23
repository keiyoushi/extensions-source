package eu.kanade.tachiyomi.extension.en.mangapdf

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal const val API_BASE_URL = "https://api.coffeemanga.shop"

private fun apiBuilder(): HttpUrl.Builder = API_BASE_URL
    .toHttpUrl()
    .newBuilder()
    .addPathSegment("api")
    .addPathSegment("v1")
    .addPathSegment("mihon")

internal fun popularUrl(page: Int): HttpUrl = apiBuilder()
    .addPathSegment("popular")
    .addQueryParameter("page", page.toString())
    .build()

internal fun latestUrl(page: Int): HttpUrl = apiBuilder()
    .addPathSegment("latest")
    .addQueryParameter("page", page.toString())
    .build()

internal fun searchUrl(query: String, page: Int): HttpUrl = apiBuilder()
    .addPathSegment("search")
    .addQueryParameter("q", query)
    .addQueryParameter("page", page.toString())
    .build()

internal fun mangaUrl(mangaId: String): HttpUrl = apiBuilder()
    .addPathSegment("manga")
    .addPathSegment(mangaId)
    .build()

internal fun pagesUrl(chapterId: String): HttpUrl = apiBuilder()
    .addPathSegment("chapter")
    .addPathSegment(chapterId)
    .addPathSegment("pages")
    .build()
