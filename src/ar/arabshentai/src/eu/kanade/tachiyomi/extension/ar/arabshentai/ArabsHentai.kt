package eu.kanade.tachiyomi.extension.ar.arabshentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ArabsHentai : HttpSource() {
    override val name = "هنتاي العرب"
    override val baseUrl = "https://arabshentai.com"
    override val lang = "ar"
    private val dateFormat = SimpleDateFormat("d MMM، yyy", Locale("ar"))
    override val supportsLatest = true
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/page/$page/?orderby=new-manga", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#archive-content .wp-manga").mapNotNull { it.toPopularManga() }
        val hasNextPage = document.selectFirst(".pagination a.arrow_pag i#nextpagination") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toPopularManga(): SManga? {
        val link = selectFirst(".data h3 a") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = link.text()
            thumbnail_url = selectFirst("a .poster img")?.imgAttr()
        }
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga/page/$page/?orderby=new_chapter", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#archive-content .wp-manga").mapNotNull { it.toPopularManga() }
        val hasNextPage = document.selectFirst(".pagination a.arrow_pag i#nextpagination") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/page/$page/".toHttpUrl().newBuilder()
        url.addQueryParameter("s", query)
        filters.forEach { filter ->
            when (filter) {
                is GenresOpFilter -> url.addQueryParameter("op", filter.toUriPart())
                is GenresFilter -> filter.state.filter { it.state }.forEach { url.addQueryParameter("genre[]", it.uriPart) }
                is StatusFilter -> filter.state.filter { it.state }.forEach { url.addQueryParameter("status[]", it.uriPart) }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".search-page .result-item article:not(:has(.tvshows))").mapNotNull { it.toSearchManga() }
        val hasNextPage = document.selectFirst(".pagination span.current + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toSearchManga(): SManga? {
        val titleElement = selectFirst(".details .title") ?: return null
        val link = titleElement.selectFirst("a") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = titleElement.text()
            thumbnail_url = selectFirst(".image .thumbnail a img")?.imgAttr()
        }
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return document.selectFirst(".content")?.let { content ->
            SManga.create().apply {
                title = content.selectFirst(".sheader .data h1")?.text() ?: ""
                thumbnail_url = content.selectFirst(".sheader .poster img")?.imgAttr()
                val genres = mutableListOf<String>()
                content.selectFirst("#manga-info")?.let { info ->
                    description = "\u061C" + info.select(".wp-content p").text() + "\n" + "أسماء أُخرى: " + info.select("div b:contains(أسماء أُخرى) + span").text()
                    status = info.select("div b:contains(حالة المانجا) + span").text().parseStatus()
                    author = info.select("div b:contains(الكاتب) + span a").text()
                    artist = info.select("div b:contains(الرسام) + span a").text()
                    genres += info.select("div b:contains(نوع العمل) + span a").text()
                }
                genres += content.select(".data .sgeneros a").map { it.text() }
                genre = genres.joinToString()
                initialized = true
            }
        } ?: throw Exception("Failed to parse manga details")
    }

    private fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        contains("مستمر", ignoreCase = true) -> SManga.ONGOING
        contains("مكتمل", ignoreCase = true) -> SManga.COMPLETED
        contains("متوقف", ignoreCase = true) -> SManga.ON_HIATUS
        contains("ملغية", ignoreCase = true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chapter-list a[href*='/manga/'], .oneshot-reader .images .image-item a[href$='manga-paged=1']")
            .mapNotNull { it.toChapter() }
    }

    private fun Element.toChapter(): SChapter = SChapter.create().apply {
        val url = absUrl("href")
        if (url.contains("style=paged")) {
            setUrlWithoutDomain(url.substringBeforeLast("?"))
            name = "ونشوت"
            date_upload = 0L
        } else {
            name = select(".chapternum").text()
            date_upload = select(".chapterdate").text().parseChapterDate()
            setUrlWithoutDomain(url)
        }
    }

    private fun String?.parseChapterDate(): Long = dateFormat.tryParse(this)

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".chapter_image img.wp-manga-chapter-img").mapIndexed { index, item ->
            Page(index = index, imageUrl = item.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String? = when {
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("bv-data-src") -> attr("bv-data-src")
        else -> attr("abs:src")
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

    private fun genresRequest() = GET("$baseUrl/%d8%aa%d8%b5%d9%86%d9%8a%d9%81%d8%a7%d8%aa", headers)
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
