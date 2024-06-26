package eu.kanade.tachiyomi.extension.ar.arabshentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ArabsHentai : ParsedHttpSource() {
    override val name = "هنتاي العرب"

    override val baseUrl = "https://arabshentai.com"

    override val lang = "ar"

    private val dateFormat = SimpleDateFormat("d MMM\u060c yyy", Locale("ar"))

    override val supportsLatest = true

    override val client =
        network.cloudflareClient.newBuilder()
            .rateLimit(2)
            .build()

    override fun headersBuilder() =
        super.headersBuilder()
            .set("Referer", "$baseUrl/")
            .set("Origin", baseUrl)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/page/$page/?orderby=new-manga", headers)

    override fun popularMangaSelector() = "#archive-content .wp-manga"

    override fun popularMangaFromElement(element: Element) =
        SManga.create().apply {
            element.selectFirst(".data h3 a")!!.run {
                setUrlWithoutDomain(absUrl("href"))
                title = text()
            }
            thumbnail_url = element.selectFirst("a .poster img")?.imgAttr()
        }

    override fun popularMangaNextPageSelector() = ".pagination a.arrow_pag i#nextpagination"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga/page/$page/?orderby=new_chapter", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/page/$page/".toHttpUrl().newBuilder()
        url.addQueryParameter("s", query)
        filters.forEach { filter ->
            when (filter) {
                is GenresOpFilter -> url.addQueryParameter("op", filter.toUriPart())
                is GenresFilter ->
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("genre[]", it.uriPart) }
                is StatusFilter ->
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("status[]", it.uriPart) }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = ".search-page .result-item article:not(:has(.tvshows))"

    override fun searchMangaFromElement(element: Element) =
        SManga.create().apply {
            element.selectFirst(".details .title")!!.run {
                setUrlWithoutDomain(selectFirst("a")!!.absUrl("href"))
                title = text()
            }
            thumbnail_url = element.selectFirst(".image .thumbnail a img")?.imgAttr()
        }

    override fun searchMangaNextPageSelector() = ".pagination span.current + a"

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) =
        SManga.create().apply {
            document.selectFirst(".content")!!.run {
                title = selectFirst(".sheader .data h1")!!.text()
                thumbnail_url = selectFirst(".sheader .poster img")?.imgAttr()
                val genres = mutableListOf<String>()
                selectFirst("#manga-info")?.run {
                    description = "\u061C" + select(".wp-content p").text() +
                        "\n" + "أسماء أُخرى: " + select("div b:contains(أسماء أُخرى) + span").text()
                    status = select("div b:contains(حالة المانجا) + span").text().parseStatus()
                    author = select("div b:contains(الكاتب) + span a").text()
                    artist = select("div b:contains(الرسام) + span a").text()
                    genres += select("div b:contains(نوع العمل) + span a").text()
                }
                genres += select(".data .sgeneros a").map { it.text() }
                genre = genres.joinToString()
            }
        }

    private fun String?.parseStatus() =
        when {
            this == null -> SManga.UNKNOWN
            this.contains("مستمر", ignoreCase = true) -> SManga.ONGOING
            this.contains("مكتمل", ignoreCase = true) -> SManga.COMPLETED
            this.contains("متوقف", ignoreCase = true) -> SManga.ON_HIATUS
            this.contains("ملغية", ignoreCase = true) -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "#chapter-list a[href*='/manga/'], .oneshot-reader .images .image-item a[href$='manga-paged=1']"

    override fun chapterFromElement(element: Element) =
        SChapter.create().apply {
            val url = element.attr("href")
            if (url.contains("style=paged")) {
                setUrlWithoutDomain(url.substringBeforeLast("?"))
                name = "ونشوت"
                date_upload = 0L
            } else {
                name = element.select(".chapternum").text()
                date_upload = element.select(".chapterdate").text().parseChapterDate()
                setUrlWithoutDomain(url)
            }
        }

    private fun String?.parseChapterDate(): Long {
        if (this == null) return 0L
        return try {
            dateFormat.parse(this)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        return document.select(".chapter_image img.wp-manga-chapter-img").mapIndexed { index, item ->
            Page(index = index, imageUrl = item.imgAttr())
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String? {
        return when {
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("bv-data-src") -> attr("bv-data-src")
            else -> attr("abs:src")
        }
    }

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }
        return FilterList(
            GenresFilter(),
            GenresOpFilter(),
            StatusFilter(),
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun launchIO(block: () -> Unit) = scope.launch { block() }

    private var fetchGenresAttempts: Int = 0

    private fun fetchGenres() {
        if (fetchGenresAttempts < 3 && genreList.isEmpty()) {
            try {
                genreList = client.newCall(genresRequest()).execute()
                    .asJsoup()
                    .let(::parseGenres)
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }

    private fun genresRequest(): Request {
        return GET("$baseUrl/%d8%aa%d8%b5%d9%86%d9%8a%d9%81%d8%a7%d8%aa", headers)
    }

    private fun parseGenres(document: Document): List<Pair<String, String>> {
        val items = document.select("#archive-content ul.genre-list li.item-genre .genre-data a")
        return buildList(items.size) {
            items.mapTo(this) {
                val value = it.ownText()
                Pair(value, value)
            }
        }
    }
}
