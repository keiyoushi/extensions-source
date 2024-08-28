package eu.kanade.tachiyomi.extension.es.hentaimode

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class HentaiMode : ParsedHttpSource() {
    override val name = "HentaiArchive"

    override val baseUrl = "https://www.hentai-archive.com"

    override val lang = "it"

    private val cdnHeaders = super.headersBuilder()
        .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .add("Referer", baseUrl) // Replace with the actual referer if needed
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

    override val supportsLatest = true

    // ============================== Popular ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category/hentai-recenti/page/$page", headers)

    override fun latestUpdatesSelector() = "div.posts-container article"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        // Extract and set the URL without domain
        setUrlWithoutDomain(element.selectFirst("a.entire-meta-link")!!.absUrl("href"))

        // Extract and set the title
        title = element.selectFirst("span.screen-reader-text")!!.text()

        // Extract and set the thumbnail URL
        thumbnail_url = element.selectFirst("span.post-featured-img img.wp-post-image")
            ?.absUrl("data-nectar-img-src")
    }

    override fun latestUpdatesNextPageSelector() = "nav#pagination a.next.page-numbers"

    // =============================== Latest ===============================
    override fun popularMangaRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun popularMangaSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun popularMangaFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun popularMangaNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/?s=$query")
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = null

    // =========================== Manga Details ============================
    private val additionalInfos = listOf("Serie", "Tipo", "Personajes", "Idioma")

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        thumbnail_url = document.selectFirst("div.content-inner img")?.absUrl("src")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        with(document.selectFirst("div.main-content")!!) {
            title = selectFirst("h1")!!.text()
            genre = getInfo("meta-category")
        }
    }

    private fun Element.getInfo(text: String): String? {
        return if (text == "meta-category") {
            // Extract class names from elements with the class 'meta-category'
            select(".$text")
                .flatMap { it.children() }
                .flatMap { it.classNames() }
                .joinToString(", ")
                .takeUnless(String::isBlank)
        } else {
            // Original functionality
            select("div.tag-container:containsOwn($text) a.tag")
                .joinToString { it.text() }
                .takeUnless(String::isBlank)
        }
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
    override fun pageListParse(document: Document): List<Page> {
        // Log the full HTML content of the page

        val imageElements = document.select("div.content-inner img")

        // Log the number of images found
        Log.d("PageListParse", "Found ${imageElements.size} images.")

        return imageElements.mapIndexed { index, element ->
            // Extract the URL from the data-src attribute, fallback to src if not present
            var imageUrl = element.attr("data-src")
            if (imageUrl.isEmpty()) {
                imageUrl = element.attr("src")
            }

            // Ensure the URL is absolute
            if (!imageUrl.startsWith("http")) {
                imageUrl = element.absUrl("data-src")
                if (imageUrl.isEmpty()) {
                    imageUrl = element.absUrl("src")
                }
            }

            // Log the image URL for debugging
            Log.d("PageListParse", "Page $index: $imageUrl")

            Page(index, imageUrl = imageUrl)
        }.sortedBy { it.index }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
