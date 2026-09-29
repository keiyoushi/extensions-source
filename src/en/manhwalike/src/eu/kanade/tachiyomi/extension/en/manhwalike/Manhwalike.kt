package eu.kanade.tachiyomi.extension.en.manhwalike

import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.buildApiHeaders
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toDate
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toFormRequestBody
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toOriginal
import eu.kanade.tachiyomi.extension.en.manhwalike.ManhwalikeHelper.toStatus
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

@Source
abstract class Manhwalike : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    // popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("ul.list-hot div.visual").map { element ->
            SManga.create().apply {
                element.selectFirst("h3.title a")?.text()?.also { title = it }
                element.selectFirst("a")?.absUrl("href")?.also { setUrlWithoutDomain(it) }
                thumbnail_url = element.selectFirst("img")?.toOriginal()
            }
        }
        return MangasPage(mangas, false)
    }

    // latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("ul.slick_item div.visual").map { element ->
            SManga.create().apply {
                element.selectFirst("h3.title a")?.text()?.also { title = it }
                element.selectFirst("a")?.absUrl("href")?.also { setUrlWithoutDomain(it) }
                thumbnail_url = element.selectFirst("img")?.toOriginal()
            }
        }
        return MangasPage(mangas, false)
    }

    // search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = if (query.isNotEmpty()) {
            val requestBody = query.toFormRequestBody()
            val requestHeaders = headers.newBuilder().buildApiHeaders(requestBody)
            client.post("$baseUrl/search/html/1", requestHeaders, requestBody)
        } else {
            val url = baseUrl.toHttpUrl().newBuilder()
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.toUriPart().also { url.addPathSegment(it) }
                    else -> {}
                }
            }
            url.addQueryParameter("page", page.toString())
            client.get(url.build())
        }

        val document = response.asJsoup()
        val mangas = when {
            document.select("ul.normal li").isEmpty() -> document.select("ul li").map { element ->
                searchMangaFromElement(element)
            }
            else -> document.select("ul.normal li").map { element ->
                searchMangaFromElement(element)
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination li:last-child a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("img")?.attr("alt")?.also { title = it }
        element.selectFirst("img")?.toOriginal()?.also { thumbnail_url = it }
        element.selectFirst("a")?.absUrl("href")?.also { setUrlWithoutDomain(it) }
    }

    // details + chapters
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = manga.apply {
            author = document.selectFirst("div.author a")?.text()
            status = document.selectFirst("small:contains(Status) + strong")?.text().toStatus()
            genre = document.select("div.categories a").joinToString { it.text() }
            description = document.selectFirst("div.summary-block p.about")?.text()
            thumbnail_url = document.selectFirst("div.fixed-img img")?.absUrl("src")
        }

        val chapterList = document.select("ul.chapter-list li").map { element ->
            SChapter.create().apply {
                element.selectFirst("a")?.absUrl("href")?.also { setUrlWithoutDomain(it) }
                element.selectFirst("a")?.text()?.also { name = it }
                element.selectFirst(".time")?.text().toDate().also { date_upload = it }
            }
        }

        return SMangaUpdate(updatedManga, chapterList)
    }

    // pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".chapter-content .page-chapter img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    // filters
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter(),
    )
}
