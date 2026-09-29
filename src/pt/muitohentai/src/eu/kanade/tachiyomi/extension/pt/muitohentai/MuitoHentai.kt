package eu.kanade.tachiyomi.extension.pt.muitohentai

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MuitoHentai : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)

    // The source does not have a popular list page, so we use the list instead.
    private suspend fun fetchMangaList(page: Int): Document {
        val newHeaders = headers.newBuilder()
            .set("Referer", if (page == 1) baseUrl else "$baseUrl/mangas/${page - 1}")
            .build()

        val pageStr = if (page != 1) page.toString() else ""
        return client.get("$baseUrl/mangas/$pageStr", newHeaders).asJsoup()
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = fetchMangaList(page)
        val mangas = document.select("#archive-content article.tvshows").map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst("#paginacao a:last-child:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.data h3 a")!!.text()
        thumbnail_url = element.selectFirst("div.poster img")!!.attr("abs:src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = fetchMangaList(page)
        val mangas = document.select("ul.lancamento-cap2 > li").map { latestUpdatesFromElement(it) }
        val hasNextPage = document.selectFirst("#paginacao a:last-child:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h2")!!.text()
        thumbnail_url = element.selectFirst("div.capaMangaHentai img")!!.attr("abs:src")
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchUrl = "$baseUrl/buscar-manga/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()

        val document = client.get(searchUrl).asJsoup()
        val mangas = document.select("#archive-content article.tvshows").map { popularMangaFromElement(it) }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = manga.title
            author = document.selectFirst("div:has(strong:contains(Autor))")?.ownText()
            genre = document.select("a.genero_btn").joinToString {
                it.text().replaceFirstChar { ch -> ch.titlecase(LOCALE) }
            }
            description = document.selectFirst("div.backgroundpost:contains(Sinopse)")?.ownText()
            thumbnail_url = document.selectFirst("#capaAnime img")?.attr("abs:src")
        }

        val chapterList = document.select("div.backgroundpost:contains(Capítulos de) h3 > a")
            .map { chapterFromElement(it) }
            .reversed()

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.ownText()
        setUrlWithoutDomain(element.attr("abs:href"))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val newHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl${chapter.url}".substringBeforeLast("/"))
            .build()

        val document = client.get(baseUrl + chapter.url, newHeaders).asJsoup()
        return document.selectFirst("script:containsData(numeroImgAtual)")
            ?.data()
            ?.substringAfter("var arr = ")
            ?.substringBefore(";")
            ?.parseAs<JsonArray>()
            ?.mapIndexed { i, el ->
                Page(i, url = document.location(), imageUrl = el.jsonPrimitive.content)
            }
            .orEmpty()
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Referer", page.url)
        .build()

    companion object {
        private val LOCALE = Locale("pt", "BR")
    }
}
