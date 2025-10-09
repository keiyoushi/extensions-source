package eu.kanade.tachiyomi.extension.en.rezoscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class RezoScans : Iken(
    "Rezo Scans",
    "en",
    "https://rezoscan.org",
    "https://api.rezoscan.org",
) {
    override val versionId = 2

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/api/posts".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", perPage.toString())
            addQueryParameter("tag", "new")
            addQueryParameter("isNovel", "false")
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/api/posts".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", perPage.toString())
            addQueryParameter("tag", "hot")
            addQueryParameter("isNovel", "false")
        }.build()

        return GET(url, headers)
    }
    override fun popularMangaParse(response: Response) = searchMangaParse(response)
    val perPage = 18
}
