package eu.kanade.tachiyomi.extension.all.baobua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class BaoBua() : SimpleParsedHttpSource() {

    override val baseUrl = "https://baobua.net"
    override val lang = "all"
    override val name = "BaoBua"
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun simpleMangaSelector() = ".product-item"

    override fun simpleMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst(".product-title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override fun simpleNextPageSelector(): String = ".pagination-custom .nextPage"

    // region popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/?page=$page", headers)
    // endregion

    // region latest
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    // endregion

    // region Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filter = filters.firstInstance<SourceCategorySelector>()
        return filter.selectedCategory?.let {
            GET(it.buildUrl(baseUrl, page), headers)
        } ?: run {
            baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .build()
                .let { GET(it, headers) }
        }
    }

    // region Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            genre = document.select(".article-tags a").joinToString { it.text() }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val url = getMangaUrl(manga)
        return client.newCall(GET(url, headers))
            .asObservable()
            .map { response ->
                val document = response.asJsoup()
                listOf(
                    SChapter.create().apply {
                        chapter_number = 0F
                        setUrlWithoutDomain(url)
                        date_upload = POST_DATE_FORMAT.tryParse(document.selectFirst(".article-date-comment .date")?.text())
                        name = "Gallery"
                    },
                )
            }
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()
    // endregion

    // region Pages
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val basePageUrl = getChapterUrl(chapter)
        return client.newCall(GET(basePageUrl, headers))
            .asObservable()
            .map { response ->
                val pages: MutableList<String> = mutableListOf()
                var nextPage = ""
                imageListNextPageParse(response.asJsoup()).let {
                    pages.addAll(it.first)
                    nextPage = it.second
                }

                while (nextPage.isNotBlank()) {
                    val doc = client.newCall(GET(nextPage, headers)).execute().asJsoup()
                    imageListNextPageParse(doc).let {
                        pages.addAll(it.first)
                        nextPage = it.second
                    }
                }

                pages.mapIndexed { index, imageUrl ->
                    Page(index, imageUrl = imageUrl)
                }
            }
    }

    override fun pageListParse(document: Document) = throw UnsupportedOperationException()

    private fun imageListNextPageParse(document: Document): Pair<List<String>, String> {
        val pages = document.select(".article-body img")
            .map { it.absUrl("src") }
        val nextPage = document.selectFirst("a.page-numbers:contains(Next)")?.absUrl("href") ?: ""
        return pages to nextPage
    }
    // endregion

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Searching is not supported"),
        Filter.Separator(),
        SourceCategorySelector.create(),
    )

    companion object {

        private val POST_DATE_FORMAT = SimpleDateFormat("EEE MMM dd yyyy", Locale.US)
    }
}
