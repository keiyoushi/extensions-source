package eu.kanade.tachiyomi.extension.it.hentaiarchive

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class HentaiArchive : ParsedHttpSource() {
    override val name = "HentaiArchive"

    override val baseUrl = "https://www.hentai-archive.com"

    override val lang = "it"

    private val cdnHeaders = super.headersBuilder()
        .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .add("Referer", "$baseUrl/")
        .build()

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            if (url.contains("picsarchive1.b-cdn.net")) {
                return@addInterceptor chain.proceed(request.newBuilder().headers(cdnHeaders).build())
            }
            chain.proceed(request)
        }
        .build()

    override val supportsLatest = false

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/hentai-recenti/page/$page", headers)

    override fun popularMangaSelector() = "div.posts-container article"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        // Extract and set the URL without domain
        setUrlWithoutDomain(element.selectFirst("a.entire-meta-link")!!.absUrl("href"))

        // Extract and set the title
        title = element.selectFirst("span.screen-reader-text")!!.text()

        // Extract and set the thumbnail URL
        thumbnail_url = element.selectFirst("span.post-featured-img img.wp-post-image")?.absUrl("data-nectar-img-src")
    }

    override fun popularMangaNextPageSelector() = "nav#pagination a.next.page-numbers"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/page/$page/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = "article.result"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("h2.title a")!!.absUrl("href"))

        title = element.selectFirst("h2.title a")!!.text()

        thumbnail_url = element.selectFirst("a img.wp-post-image")?.absUrl("src")
    }
    override fun searchMangaNextPageSelector() = "a.next.page-numbers"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        with(document.selectFirst("div.main-content")!!) {
            title = selectFirst("h1")!!.text()
            genre = getInfo("meta-category")
        }
    }

    private fun Element.getInfo(text: String): String? {
        // Extract class names from elements with the class 'meta-category'
        return select(".$text")
            .flatMap { it.children() }
            .flatMap { it.classNames() }
            .joinToString(", ")
            .replace("-", " ")
            .replace("hentai", "")
            .takeUnless(String::isBlank)
    }

    // ============================== Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url
            chapter_number = 1F
            name = "Chapter"
        }

        return Observable.just(listOf(chapter))
    }

    override fun chapterListSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException()
    }

    // =============================== Pages ================================
    private var imageregex = Regex("-(\\d+x\\d+)(?=\\.jpg)")

    private fun Element.imgAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("src") -> this.attr("src")
            else -> ""
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val imageElements = document.select("div.content-inner img")

        return imageElements.mapIndexed { index, element ->

            var imageUrl = element.imgAttr()

            // Remove the part between '-' and '.jpg' if it exists
            imageUrl = imageUrl.replace(imageregex, "")

            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }
}
