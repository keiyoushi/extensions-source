package eu.kanade.tachiyomi.extension.en.cutiecomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class CutieComics : HttpSource() {

    override val name = "Cutie Comics"

    override val baseUrl = "https://cutiecomics.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("#dle-content > div.w25").map { element ->
            SManga.create().apply {
                with(element.selectFirst("strong.field-content > a")!!) {
                    title = ownText()
                    setUrlWithoutDomain(attr("href"))
                }
                thumbnail_url = element.selectFirst("a > img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst(".navigation > a > i.fa-angle-right") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
        val id = query.removePrefix(PREFIX_SEARCH)
        client.newCall(GET("$baseUrl/$id"))
            .asObservableSuccess()
            .map(::searchMangaByIdParse)
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response).apply {
            setUrlWithoutDomain(response.request.url.toString())
        }
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        require(query.isNotBlank() && query.length >= 4) { "Invalid search! It should have at least 4 non-blank characters." }
        val body = FormBody.Builder()
            .add("do", "search")
            .add("subaction", "search")
            .add("full_search", "0")
            .add("search_start", "$page")
            .add("result_from", "${(page - 1) * 20 + 1}")
            .add("story", query)
            .build()
        return POST("$baseUrl/index.php?do=search", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE

            title = document.selectFirst("h1#page-title")!!.text()
            thumbnail_url = document.selectFirst("div.galery > img")?.absUrl("src")
            genre = document.select("h3.field-label ~ span").joinToString { it.text() }
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

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.galery > img").mapIndexed { index, item ->
            Page(index, imageUrl = item.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
