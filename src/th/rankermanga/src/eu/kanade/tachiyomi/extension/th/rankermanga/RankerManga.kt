package eu.kanade.tachiyomi.extension.th.rankermanga

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class RankerManga : ZManga() {

    override val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)

    // The site has no advanced search page: popular is the home page's ranking
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.popular div.flexbox-item > a").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.selectFirst(".flexbox-title")!!.text()
                thumbnail_url = link.selectFirst("img")?.cover()
            }
        }
        return MangasPage(mangas, false)
    }

    // The home page lists every series by its latest chapter
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/${pagePathSegment(page)}", Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.flexbox4-item").map { item ->
            SManga.create().apply {
                val link = item.selectFirst(".flexbox4-side .title a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = item.selectFirst(".flexbox4-thumb img")?.cover()
            }
        }
        return MangasPage(mangas, document.selectFirst(popularMangaNextPageSelector()) != null)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/${pagePathSegment(page)}".toHttpUrl().newBuilder().apply {
            addQueryParameter("s", query.trim())
            filters.firstInstanceOrNull<GenreFilter>()?.slug?.takeIf(String::isNotEmpty)?.let { addQueryParameter("genre", it) }
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(popularMangaSelector()).map { item ->
            SManga.create().apply {
                setUrlWithoutDomain(item.selectFirst("div.flexbox2-content a")!!.absUrl("href"))
                title = item.selectFirst("div.flexbox2-title > span")!!.text()
                thumbnail_url = item.selectFirst("img")?.cover()
            }
        }
        return MangasPage(mangas, document.selectFirst(popularMangaNextPageSelector()) != null)
    }

    // Chapter pages sit at the site's root; their side panel links to the series
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val path = url.pathSegments.filter(String::isNotEmpty)
        val seriesPath = when {
            path.size == 2 && path[0] == "series" -> "/series/${path[1]}/"
            path.size == 1 -> client.get("$baseUrl/${path[0]}/", Fetch.DOCUMENT).asJsoup()
                .selectFirst("#chSidenav .coverz a[href*=/series/]")
                ?.absUrl("href")
                ?.toHttpUrl()
                ?.encodedPath
                ?: return null
            else -> return null
        }
        // The theme's details have neither the URL nor the title
        val document = client.get(baseUrl.toHttpUrl().resolve(seriesPath)!!, Fetch.DOCUMENT).asJsoup()
        return mangaDetailsParse(document).apply {
            this.url = seriesPath
            title = document.selectFirst("div.series-titlex h2")!!.text()
            initialized = true
        }
    }

    override val supportsFilterFetching = false

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // List covers are resized by Jetpack (i0.wp.com/<site path>?resize=), or a placeholder
    private fun Element.cover(): String? {
        val src = absUrl("src")
        if (src.endsWith("/no-image.jpg")) return null
        return src.replace(PHOTON_REGEX, "https://").substringBefore('?')
    }

    companion object {
        private val PHOTON_REGEX = Regex("""^https?://i\d\.wp\.com/""")
    }
}
