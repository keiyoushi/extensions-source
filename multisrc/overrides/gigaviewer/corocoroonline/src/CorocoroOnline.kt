package eu.kanade.tachiyomi.extension.ja.corocoroonline

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element
import rx.Observable

class CorocoroOnline : GigaViewer(
    "Corocoro Online",
    "https://corocoro.jp",
    "ja",
    "https://cdn-img.www.corocoro.jp/public/page",
) {

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher = "小学館"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga", headers)

    override fun popularMangaSelector(): String = "a.p-article-wrap"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3.p-article-title")!!.text()
            .substringAfter(']')
        thumbnail_url = element.selectFirst("> .p-article-image > img")!!.attr("src")
        setUrlWithoutDomain(element.attr("href"))
    }

    // Site doesn't have a manga search and only returns news in search results.
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return fetchPopularManga(page)
            .map { allManga ->
                val filteredManga = allManga.mangas.filter { manga ->
                    manga.title.contains(query, true)
                }

                MangasPage(filteredManga, hasNextPage = false)
            }
    }

    // The chapters only load using the URL with 'www'.
    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(BASE_URL_WWW + manga.url, headers)

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun pageListRequest(chapter: SChapter): Request =
        GET(BASE_URL_WWW + chapter.url, headers)

    // All chapters seems to be free.
    override fun chapterListSelector(): String = "li.episode"

    override val chapterListMode = CHAPTER_LIST_LOCKED

    // The source have no collections, so no need to have filters.
    override fun getFilterList(): FilterList = FilterList()

    companion object {
        private const val BASE_URL_WWW = "https://www.corocoro.jp"
    }
}
