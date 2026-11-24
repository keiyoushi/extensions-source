package eu.kanade.tachiyomi.extension.all.seinagi

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class Seinagi : FoolSlide() {

    // Series and chapter links, on the source's own subdomain
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.size < 2 || segments[0] !in LINK_TYPES) return null
        val seriesUrl = baseUrl.toHttpUrl().newBuilder().addPathSegments("series/${segments[1]}/").build()
        val document = client.get(seriesUrl, adultHeaders, Fetch.DOCUMENT).asJsoup()
        return mangaDetailsParse(document).apply {
            this.url = seriesUrl.encodedPath
            title = document.selectFirst("h1.title")!!.text()
            initialized = true
        }
    }

    // The Spanish readers label the description in Spanish, and the series page shows the small cover
    override suspend fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        description = description
            ?: document.selectFirst("div.info li:has(> b:matchesOwn(^Descripción$))")?.ownText()?.removePrefix(":")?.trim()?.ifEmpty { null }
        thumbnail_url = thumbnail_url?.replace("/thumb_", "/")
    }

    companion object {
        private val LINK_TYPES = setOf("series", "read")
    }
}
