package eu.kanade.tachiyomi.extension.ar.azora

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Azora :
    Iken(
        "Azora",
        "ar",
        "https://azoramoon.com",
        "https://api.azoramoon.com",
    ) {
    override val versionId = 2
    val perPage = 18
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/api/query".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", perPage.toString())
            addQueryParameter("orderBy", "totalViews")
            addQueryParameter("orderDirection", "desc")
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
}
