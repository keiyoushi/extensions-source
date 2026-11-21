package eu.kanade.tachiyomi.extension.tr.piescans

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Piescans : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", getFilters())

    // The home page's latest updates are paged by this call of its widget
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val body = FormBody.Builder()
            .add("action", "kitmanga_load_chapters_page")
            .add("page", page.toString())
            .add("per_page", "15")
            .add("style", "classic")
            .build()
        val data = client.post("$baseUrl/wp-admin/admin-ajax.php", body = body, fetch = Fetch.XHR).parseAs<LatestDto>().data
        val mangas = Jsoup.parseBodyFragment(data.html, baseUrl).select("div.chapter-box").map { box ->
            SManga.create().apply {
                val link = box.selectFirst(".title a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = box.selectFirst(".poster img")?.fullSize()
            }
        }
        return MangasPage(mangas, page < data.maxPages)
    }

    // Every series is listed by an empty search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            addQueryParameter("s", query.trim())
            filters.filterIsInstance<UriPartFilter>().forEach { filter ->
                val (name, value) = filter.queryParameter
                if (value.isNotEmpty()) addQueryParameter(name, value)
            }
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.series-cover-card a.cover-card-link").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.selectFirst(".cover-card-title")!!.text()
                thumbnail_url = link.selectFirst("img")?.fullSize()
            }
        }
        return MangasPage(mangas, document.selectFirst(".kitmanga-pagenavi .naviright a") != null)
    }

    // Details and chapters are on the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(document, manga.url), parseChapters(document))
    }

    private fun parseDetails(document: Document, path: String) = SManga.create().apply {
        url = path
        title = document.selectFirst("h1.kp-title")!!.ownText()
        thumbnail_url = document.selectFirst(".kp-poster img")?.fullSize()
        val type = document.selectFirst(".kp-badge-type")?.text()?.substringBefore('·')?.trim()
        genre = (listOfNotNull(type) + document.select(".kp-meta-genres a.kp-genre-pill").map { it.text() })
            .distinct()
            .joinToString()
            .ifEmpty { null }
        status = when {
            document.selectFirst(".kp-meta-badges .status.publishing") != null -> SManga.ONGOING
            document.selectFirst(".kp-meta-badges .status.finished") != null -> SManga.COMPLETED
            document.selectFirst(".kp-meta-badges .status.on-hiatus") != null -> SManga.ON_HIATUS
            document.selectFirst(".kp-meta-badges .status.discontinued") != null -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(document.selectFirst(".kp-badge-rating")?.text()?.toDoubleOrNull())
            .description(document.selectFirst("#kpSummaryDesc"))
            .altTitles(listOfNotNull(document.selectFirst(".kp-alt-title")?.text()))
            .build()
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("li.kp-chapter-row a.kp-chapter-item-link").map { link ->
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.selectFirst(".kp-chapter-text")!!.text()
            date_upload = DATE_FORMAT.tryParseDate(link.selectFirst(".chapter-date")?.text(), ZONE)
        }
    }

    // Chapter pages sit at the site's root and link to their series
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val path = url.pathSegments.filter(String::isNotEmpty)
        val seriesPath = when {
            path.size == 2 && path[0] == "manga" -> "/manga/${path[1]}/"
            path.size == 1 -> client.get("$baseUrl/${path[0]}/", Fetch.DOCUMENT).asJsoup()
                .selectFirst("div.chapter-buttons a[href*=/manga/]")
                ?.absUrl("href")
                ?.toHttpUrl()
                ?.encodedPath
                ?: return null
            else -> return null
        }
        return getMangaUpdate(SManga.create().apply { this.url = seriesPath }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("div.chapter-images div.chapter-item img")
        .mapIndexed { i, img -> Page(i, imageUrl = img.encodedAddress() ?: img.absUrl("src")) }

    // One page per chapter has its address encoded in the page, decoded as the site's reader does: base64, then rot13
    private fun Element.encodedAddress(): String? {
        val encoded = attr("data-kisl").ifEmpty { return null }
        return String(Base64.decode(encoded, Base64.DEFAULT)).map { c ->
            when (c) {
                in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                else -> c
            }
        }.joinToString("")
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // WordPress thumbnails end with their size: the original has none
    private fun Element.fullSize() = absUrl("src").replace(SIZE_REGEX, "")

    companion object {
        private val SIZE_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))
        private val ZONE = ZoneId.of("Europe/Istanbul")
    }
}
