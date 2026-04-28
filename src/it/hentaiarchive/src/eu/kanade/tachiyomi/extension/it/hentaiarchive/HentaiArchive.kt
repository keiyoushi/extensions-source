package eu.kanade.tachiyomi.extension.it.hentaiarchive

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

class HentaiArchive : HttpSource() {
    override val name = "HentaiArchive"

    override val baseUrl = "https://www.hentai-archive.com"

    override val lang = "it"

    private val cdnHeaders = super.headersBuilder()
        .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .add("Referer", "$baseUrl/")
        .build()

    override val client = network.cloudflareClient.newBuilder()
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

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.posts-container article").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.entire-meta-link")!!.absUrl("href"))
                title = element.selectFirst("span.screen-reader-text")!!.text()
                thumbnail_url = element.selectFirst("span.post-featured-img img.wp-post-image")?.absUrl("data-nectar-img-src")
            }
        }

        val hasNextPage = document.selectFirst("nav#pagination a.next.page-numbers") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/page/$page/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("article.result").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("h2.title a")!!.absUrl("href"))
                title = element.selectFirst("h2.title a")!!.text()
                thumbnail_url = element.selectFirst("a img.wp-post-image")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("a.next.page-numbers") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            with(document.selectFirst("div.main-content")!!) {
                title = selectFirst("h1")!!.text()
                genre = getInfo("meta-category")
            }
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
            .trim()
            .takeUnless(String::isEmpty)
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

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ================================
    private val imageRegex = Regex("-(\\d+x\\d+)(?=\\.jpg)")

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("src") -> absUrl("src")
        else -> ""
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("div.content-inner img").mapIndexed { index, element ->
            val imageUrl = element.imgAttr().replace(imageRegex, "")
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
