package eu.kanade.tachiyomi.extension.es.noranofansub

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class NoraNoFansub : FoolSlide() {
    // The reader lives under /lector, next to the group's blog
    override val urlModifier = "/lector"

    // The latest page lists chapters, mostly of the same few series: the directory gives each series' last release
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val series = mutableListOf<Pair<SManga, Long>>()
        var directoryPage = 1
        do {
            val document = client.get("$baseUrl$urlModifier/directory/$directoryPage/", Fetch.DOCUMENT).asJsoup()
            document.select(popularMangaSelector()).mapTo(series) { group ->
                val lastRelease = group.selectFirst("div.element div.meta_r")?.text()?.substringAfter(", ")
                popularMangaFromElement(group) to (lastRelease?.let(::parseChapterDate) ?: 0L)
            }
            directoryPage++
        } while (document.selectFirst(popularMangaNextPageSelector()) != null)
        return MangasPage(series.sortedByDescending { it.second }.map { it.first }, false)
    }

    // Series and chapter links, with or without www
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val domain = baseUrl.toHttpUrl().host.removePrefix("www.")
        if (url.host != domain && url.host != "www.$domain") return null
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.size < 3 || segments[0] != "lector" || segments[1] !in LINK_TYPES) return null
        val seriesUrl = baseUrl.toHttpUrl().newBuilder().addPathSegments("lector/series/${segments[2]}/").build()
        val document = client.get(seriesUrl, adultHeaders, Fetch.DOCUMENT).asJsoup()
        return mangaDetailsParse(document).apply {
            this.url = seriesUrl.encodedPath
            title = document.selectFirst("h1.title")!!.text()
            initialized = true
        }
    }

    // The series page shows the small cover
    override suspend fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        thumbnail_url = thumbnail_url?.replace("/thumb_", "/")
    }

    companion object {
        private val LINK_TYPES = setOf("series", "read")
    }
}
