package eu.kanade.tachiyomi.extension.vi.kamicomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Calendar

class KamiComic : HttpSource() {
    override val name = "KamiComic"
    override val lang = "vi"
    override val baseUrl = "https://kamicomi.com"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/bang-xep-hang-truyen/page/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListPage(response.asJsoup())

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/moi-cap-nhat/page/$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListPage(response.asJsoup())

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/wp-json/initlise/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", query)
                .build()
            return GET(url, headers)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val selected = filter.state.firstOrNull { it.state }
                    if (selected != null) {
                        return GET("$baseUrl/the-loai/${selected.slug}/page/$page/", headers)
                    }
                }
                else -> {}
            }
        }

        return latestUpdatesRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url.toString()

        if (url.contains("/wp-json/initlise/v1/search")) {
            val results = response.parseAs<List<SearchResult>>()

            val mangaList = results
                .filter { it.url != null }
                .map { result ->
                    SManga.create().apply {
                        setUrlWithoutDomain(result.url!!.removePrefix(baseUrl))
                         title = result.title!!.replace(MARK_REGEX, "$1")
                        thumbnail_url = result.thumb
                            ?.replace(THUMB_SIZE_REGEX, "")
                    }
                }

            return MangasPage(mangaList, false)
        }

        return parseMangaListPage(response.asJsoup())
    }

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Parsing ===============================

    private fun parseMangaListPage(document: Document): MangasPage {
        val mangaList = document.select("a.uk-link-heading[href*=/truyen/]").map { link ->
            val panel = link.closest(".uk-panel") ?: link.parent()!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = panel.selectFirst("img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("li#next-link:not(.uk-disabled)") != null

        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url
            .removeSuffix("/")
            .substringAfterLast("/")
        val url = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .addQueryParameter("_embed", "wp:featuredmedia,wp:term")
            .build()
        return GET(url, headers)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaList = response.parseAs<List<WpManga>>()
        val wpManga = mangaList.first()

        return SManga.create().apply {
            title = Jsoup.parse(wpManga.title!!.rendered!!).text()

            description = wpManga.content?.rendered?.let { html ->
                Jsoup.parse(html).text().trim()
            }

            val terms = wpManga.embedded?.terms

            genre = terms?.getOrNull(0)
                ?.filter { it.taxonomy == "genre" }
                ?.mapNotNull { it.name }
                ?.joinToString()
                ?.ifEmpty { null }

            author = terms?.getOrNull(1)
                ?.filter { it.taxonomy == "author_tax" }
                ?.mapNotNull { it.name }
                ?.joinToString()
                ?.ifEmpty { null }

            thumbnail_url = wpManga.embedded?.featuredMedia
                ?.firstOrNull()?.sourceUrl

            status = SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        chapters.addAll(parseChapters(document))

        // Handle multi-page chapter lists
        val paginationLinks = document.select("ul.uk-pagination li a[href*=/chuong/page/]")
        val maxPage = paginationLinks.mapNotNull { link ->
            PAGE_NUMBER_REGEX.find(link.absUrl("href"))
                ?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: 1

        val mangaUrl = response.request.url.toString().removeSuffix("/")
        for (page in 2..maxPage) {
            val pageUrl = "$mangaUrl/chuong/page/$page/"
            val pageResponse = client.newCall(GET(pageUrl, headers)).execute()
            val pageDoc = pageResponse.asJsoup()
            chapters.addAll(parseChapters(pageDoc))
        }

        return chapters
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select(".chapter-list a.uk-link-toggle").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            val rawName = element.selectFirst("h3")?.text()?.trim()
                ?: element.text().trim()
            name = CHAPTER_NAME_REGEX.find(rawName)?.value ?: rawName
            date_upload = element.selectFirst("time")?.text()
                .parseRelativeDate()
        }
    }

    private fun String?.parseRelativeDate(): Long {
        this ?: return 0L

        val calendar = Calendar.getInstance()
        val number = NUMBER_REGEX.find(this)?.value?.toIntOrNull() ?: return 0L

        when {
            contains("giây") -> calendar.add(Calendar.SECOND, -number)
            contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("#chapter-content img").mapIndexed { i, element ->
            val imageUrl = element.attr("data-original-src")
                .ifEmpty { element.attr("src") }
            Page(i, imageUrl = imageUrl)
        }.filterNot { it.imageUrl!!.startsWith("data:") }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val MARK_REGEX = Regex("""<mark>(.*?)</mark>""")
        private val NUMBER_REGEX = Regex("""\d+""")
        private val PAGE_NUMBER_REGEX = Regex("""/page/(\d+)/""")
        private val THUMB_SIZE_REGEX = Regex("""-\d+x\d+""")
        private val CHAPTER_NAME_REGEX = Regex("""Chương \d+.*""")
    }
}
