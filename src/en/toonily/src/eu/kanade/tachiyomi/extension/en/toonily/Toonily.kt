package eu.kanade.tachiyomi.extension.en.toonily

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

private const val DOMAIN = "toonily.com"

class Toonily :
    Madara(
        "Toonily",
        "https://$DOMAIN",
        "en",
        SimpleDateFormat("MMM d, yy", Locale.US),
    ) {
    override val client = super.client.newBuilder()
        .addNetworkInterceptor(CookieInterceptor(DOMAIN, "toonily-mature" to "1"))
        .addInterceptor(::hdCoverInterceptor)
        .build()

    override val mangaSubString = "serie"
    override val filterNonMangaItems = false
    override val useNewChapterEndpoint = true
    override val sendViewCount = false
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun searchMangaSelector() = "div.page-item-detail.manga"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = super.searchMangaRequest(
        page,
        query.replace(titleSpecialCharactersRegex, " ").trim(),
        filters,
    )

    override fun genresRequest(): Request = GET("$baseUrl/search/?post_type=wp-manga", headers)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val newManga = SManga.create().apply {
            url = manga.url.replace("/webtoon/", "/$mangaSubString/")
        }
        return super.mangaDetailsRequest(newManga)
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun parseChapterDate(date: String?): Long {
        val formattedDate = if (date?.contains("UP") == true) "today" else date
        return super.parseChapterDate(formattedDate)
    }

    private fun hdCoverInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        return if (
            url.host.startsWith("static") && // covers are hosted on the static cdn, panels on data cdn
            url.pathSegments.lastOrNull()?.contains(sdCoverRegex) == true
        ) {
            try {
                val newUrl = url.newBuilder()
                    .removePathSegment(url.pathSegments.lastIndex)
                    .addPathSegment(
                        sdCoverRegex.replace(
                            url.pathSegments.last(),
                            "$1",
                        ),
                    ).build()
                val newRequest = request.newBuilder()
                    .url(newUrl)
                    .build()

                chain.proceed(newRequest)
                    .also { assert(it.isSuccessful) }
            } catch (_: Throwable) {
                chain.proceed(request)
            }
        } else {
            chain.proceed(request)
        }
    }

    companion object {
        val titleSpecialCharactersRegex = "[^a-z0-9]+".toRegex()
        val sdCoverRegex = Regex("""-[0-9]+x[0-9]+(\.\w+)$""")
    }
}
