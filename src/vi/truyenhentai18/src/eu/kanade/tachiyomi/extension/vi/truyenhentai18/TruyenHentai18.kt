package eu.kanade.tachiyomi.extension.vi.truyenhentai18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class TruyenHentai18 : ParsedHttpSource() {

    override val name = "Truyện Hentai 18+"

    override val baseUrl = "https://truyenhentai18.app"

    private val apiUrl = "https://api.th18.app"

    private val cdnUrl = "https://vi-api.th18.app"

    override val lang = "vi"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/$lang/truyen-de-xuat" + if (page > 1) "/page/$page" else "", headers)

    override fun popularMangaSelector() = ".container .p-2 .shadow-sm.overflow-hidden"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a[title]")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            url = url.removePrefix("/$lang")
            title = it.attr("title")
        }

        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector() = "div.overflow-x-scroll div button"

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/$lang/truyen-moi" + if (page > 1) "/page/$page" else "", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // ============================== Search ======================================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SLUG_SEARCH)
            fetchMangaDetails(SManga.create().apply { this.url = "/$lang/$slug" })
                .map { MangasPage(listOf(it), false) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/posts".toHttpUrl().newBuilder()
            .addQueryParameter("language", lang)
            .addQueryParameter("order", "latest")
            .addQueryParameter("status", "taxonomyid")
            .addQueryParameter("query", query)
            .addQueryParameter("limit", "9999")
            .addQueryParameter("page", "1")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<SearchDto>().data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, hasNextPage = false)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    // ============================== Details ======================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl/$lang${manga.url}"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        genre = document.select("a[href*=the-loai]").joinToString { it.attr("title") }
        thumbnail_url = document.selectFirst("img.bg-background")?.absUrl("src")
        document.selectFirst("h5")?.text()?.lowercase()?.let {
            status = when {
                it.equals("Đã hoàn thành", ignoreCase = true) -> SManga.COMPLETED
                it.equals("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
        setUrlWithoutDomain(document.location())
        url = url.removePrefix("/$lang")
    }

    // ============================== Chapters ======================================

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/$lang${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val document = client.newCall(super.chapterListRequest(manga))
            .execute().asJsoup()
        val postId = document.findPostId()
        return chapterListRequest(postId)
    }

    private fun chapterListRequest(postId: String): Request {
        val url = "$apiUrl/posts/$postId/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("language", lang)
            .addQueryParameter("limit", "9999")
            .addQueryParameter("page", "1")
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<ChapterWrapper>().toSChapterList()
            .sortedByDescending(SChapter::chapter_number)
    }

    private fun Document.findPostId(): String {
        val script = select("script").map(Element::data)
            .first(CHAPTERS_POST_ID::containsMatchIn)

        return CHAPTERS_POST_ID.find(script)?.groups?.get(1)?.value!!
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    // ============================== Pages ======================================

    override fun pageListRequest(chapter: SChapter) = GET(getChapterUrl(chapter), headers)

    override fun pageListParse(document: Document): List<Page> {
        val postId = document.findPostId()
        val dto = client.newCall(chapterListRequest(postId))
            .execute()
            .parseAs<ChapterWrapper>()

        val pathSegment = document.location()
            .substringAfterLast("/")
            .substringBeforeLast(".")

        val page = dto.data.first { pathSegment.equals(it.slug, ignoreCase = true) }

        return Jsoup.parseBodyFragment(page.content).select("img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    companion object {
        internal const val PREFIX_SLUG_SEARCH = "slug:"
        private val CHAPTERS_POST_ID = """(?:(?:postId|post_id).{3})(\d+)""".toRegex()
    }
}
