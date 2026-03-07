package eu.kanade.tachiyomi.extension.en.ezmanga

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class EZmanga :
    Iken(
        "EZmanga",
        "en",
        "https://ezmanga.org",
        "https://vapi.ezmanga.org",
    ) {
    // Migrated from HeanCms to Iken
    override val versionId = 4

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val usePopularMangaApi = true

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/api/query".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", Iken.PER_PAGE.toString())
            addQueryParameter("orderBy", "updatedAt")
        }.build()
        return GET(url, headers)
    }
}
