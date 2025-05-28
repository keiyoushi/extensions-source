package eu.kanade.tachiyomi.extension.en.arvenscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class VortexScans : Iken(
    "Vortex Scans",
    "en",
    "https://vortexscans.org",
    "https://api.vortexscans.org",
) {
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesRequest(page: Int) = latestMangaRequest(page)
    private fun latestMangaRequest(page: Int): Request {
        val url = "$apiUrl/api/posts".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", latestPerPage.toString())
            addQueryParameter("tag", "latestUpdate")
            addQueryParameter("isNovel", "false")
        }.build()

        return GET(url, headers)
    }
}

private const val latestPerPage = 18
