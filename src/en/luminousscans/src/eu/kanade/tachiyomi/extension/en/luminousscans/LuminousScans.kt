package eu.kanade.tachiyomi.extension.en.luminousscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class LuminousScans : MangaThemesiaAlt(
    "Luminous Scans",
    "https://luminous-scans.com",
    "en",
    mangaUrlDirectory = "/series",
    randomUrlPrefKey = "pref_permanent_manga_url_2_en",
) {
    init {
        // remove legacy preferences
        preferences.run {
            if (contains("pref_url_map")) {
                edit().remove("pref_url_map").apply()
            }
        }
    }

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = super.searchMangaRequest(page, query, filters)
        if (query.isBlank()) return request

        val url = request.url.newBuilder()
            .addPathSegment("page/$page/")
            .removeAllQueryParameters("page")
            .removeAllQueryParameters("title")
            .addQueryParameter("s", query)
            .build()

        return request.newBuilder()
            .url(url)
            .build()
    }
}
