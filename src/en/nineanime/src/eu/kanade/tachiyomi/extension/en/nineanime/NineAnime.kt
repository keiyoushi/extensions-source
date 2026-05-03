package eu.kanade.tachiyomi.extension.en.nineanime

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NineAnime : HttpSource() {

    override val name = "NineAnime"

    override val baseUrl = "https://www.nineanime.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .followRedirects(true)
        .build()

    private val imageUrlRegex = Regex("""["'](http[^"']+)["']""")

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/category/index_$page.html?sort=views", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.post").map { element ->
            SManga.create().apply {
                element.select("p.title a").let {
                    title = it.text()
                    setUrlWithoutDomain(it.attr("href"))
                }
                thumbnail_url = element.select("img").attr("abs:src")
            }
        }
        val hasNextPage = document.select("a.next").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/category/index_$page.html?sort=updated", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search/".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("page", "$page.html")
                .build()

            return GET(url, headers)
        }

        var url = "$baseUrl/category/"
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        for (filter in filterList) {
            if (filter is GenreFilter) {
                url += filter.toUriPart() + "_$page.html"
            }
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        with(document.select("div.manga-detailtop")) {
            thumbnail_url = select("img.detail-cover").attr("abs:src")
            author = select("span:contains(Author) + a").joinToString { it.text() }
            artist = select("span:contains(Artist) + a").joinToString { it.text() }
            status = when (select("p:has(span:contains(Status))").firstOrNull()?.ownText()?.trim()) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        with(document.select("div.manga-detailmiddle")) {
            genre = select("p:has(span:contains(Genre)) a").joinToString { it.text() }
            description = select("p.mobile-none").text()
        }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + "${manga.url}?waring=1", headers)

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.detail-chlist li").map { element ->
            SChapter.create().apply {
                element.select("a").let {
                    name = it.select("span").firstOrNull()?.text() ?: it.text()
                    setUrlWithoutDomain(it.attr("href"))
                }
                date_upload = element.select("span.time").text().toDate()
            }
        }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
    }

    private fun String.toDate(): Long {
        if (contains("ago")) {
            val split = split(" ")
            if (split.size < 2) return 0L
            val amount = split[0].toIntOrNull() ?: return 0L
            val cal = Calendar.getInstance()
            return when {
                split[1].contains("minute") -> cal.apply { add(Calendar.MINUTE, -amount) }.timeInMillis
                split[1].contains("hour") -> cal.apply { add(Calendar.HOUR, -amount) }.timeInMillis
                split[1].contains("day") -> cal.apply { add(Calendar.DAY_OF_MONTH, -amount) }.timeInMillis
                else -> 0L
            }
        }
        return dateFormat.tryParse(this)
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val url = baseUrl + chapter.url.trimEnd('/') + "-10-1.html"
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        // Find cid by cleanly parsing it from our appended pageListRequest layout
        val cid = document.location().substringBefore("-10-1").substringBefore("?").trimEnd('/').substringAfterLast('/')
        val iframeUrl = "$baseUrl/chapter/iframe_views/$cid"

        try {
            val iframeHeaders = headersBuilder().add("Referer", document.location()).build()
            client.newCall(GET(iframeUrl, iframeHeaders)).execute().use { iframeResponse ->
                val domain1Doc = iframeResponse.asJsoup()

                // External routing triggers (redirecting domain chain to dynamic blogs)
                val jumpUrl = domain1Doc.selectFirst("a.vision-button")?.attr("abs:href")

                if (jumpUrl != null) {
                    client.newCall(GET(jumpUrl, iframeHeaders)).execute().use { jumpResponse ->
                        val scriptData = jumpResponse.asJsoup()
                            .select("script:containsData(all_imgs_url)").firstOrNull()?.data()

                        if (scriptData != null) {
                            val arrayString = scriptData.substringAfter("all_imgs_url: [").substringBefore("]")
                            val imageUrls = imageUrlRegex.findAll(arrayString).map {
                                it.groupValues[1].replace("\\/", "/")
                            }.toList()

                            if (imageUrls.isNotEmpty()) {
                                return imageUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore exception and fall through to native
        }

        // Native parsing fallback.
        val pages = mutableListOf<Page>()

        // Scrape images from current page natively
        document.select("img.manga_pic").forEach { img ->
            val url = img.attr("abs:src")
            if (url.isNotEmpty()) {
                pages.add(Page(pages.size, imageUrl = url))
            }
        }

        // Iterate over select options if chapter has more than 10 images natively
        val options = document.select("select.sl-page option")
        for (i in 1 until options.size) {
            val pageUrl = options[i].attr("abs:value")
            if (pageUrl.isEmpty()) continue

            client.newCall(GET(pageUrl, headers)).execute().use { pageResponse ->
                val pageDoc = pageResponse.asJsoup()

                pageDoc.select("img.manga_pic").forEach { img ->
                    val url = img.attr("abs:src")
                    if (url.isNotEmpty()) {
                        pages.add(Page(pages.size, imageUrl = url))
                    }
                }
            }
        }

        if (pages.isEmpty()) {
            throw Exception("No images found (Natively or Externally Routed)")
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Note: ignored if using text search!"),
        Filter.Separator("-----------------"),
        GenreFilter(),
    )
}
