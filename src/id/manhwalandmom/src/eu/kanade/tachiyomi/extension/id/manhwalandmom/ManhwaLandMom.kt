package eu.kanade.tachiyomi.extension.id.manhwalandmom

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import eu.kanade.tachiyomi.multisrc.manhwaindo.ManhwaIndo as ManhwaIndoTheme

@Source
abstract class ManhwaLandMom : ManhwaIndoTheme() {

    // manhwaland.io's read button links its transit page with a short link to the current domain as the query
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> {
        val shortLink = client.get(PORTAL_URL, Fetch.DOCUMENT).asJsoup()
            .selectFirst("a.btn.main")!!
            .absUrl("href").toHttpUrl()
            .encodedQuery!!.toHttpUrl()
        val current = client.get(shortLink, Fetch.DOCUMENT).use { it.request.url }
        return listOfNotNull(current.takeIf { it.host != shortLink.host }?.let { "${it.scheme}://${it.host}" })
    }

    // entries saved before the site moved series from /manga/ to /series/
    override fun getMangaUrl(manga: SManga) = if (manga.url.startsWith("/manga/")) {
        "$baseUrl/series/${manga.url.removePrefix("/manga/")}"
    } else {
        super.getMangaUrl(manga)
    }

    private companion object {
        const val PORTAL_URL = "https://manhwaland.io/"
    }
}
