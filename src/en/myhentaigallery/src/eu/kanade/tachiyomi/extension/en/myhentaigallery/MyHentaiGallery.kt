package eu.kanade.tachiyomi.extension.en.myhentaigallery

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class MyHentaiGallery : HttpSource() {

    override val name = "MyHentaiGallery"
    override val baseUrl = "https://myhentaigallery.com"
    override val lang = "en"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // =============================== Popular ================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/views/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseComicListing(response)

    // =============================== Latest =================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/gpage/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseComicListing(response)

    // =============================== Search =================================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith(PREFIX_ID_SEARCH)) {
        val id = query.removePrefix(PREFIX_ID_SEARCH)
        client.newCall(GET("$baseUrl/g/$id", headers))
            .asObservableSuccess()
            .map { response ->
                val details = mangaDetailsParse(response).apply { url = "/g/$id" }
                MangasPage(listOf(details), false)
            }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addPathSegment(page.toString())
                .addQueryParameter("query", query)
                .build()
            return GET(url, headers)
        }

        val categoryFilter = filters.firstInstanceOrNull<GenreFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()

        if (categoryFilter != null && categoryFilter.toUriPart() != "---") {
            val catId = categoryFilter.toUriPart()
            return GET("$baseUrl/g/category/$catId/$page", headers)
        }

        val sortPath = sortFilter?.toUriPart() ?: "gpage"
        return GET("$baseUrl/$sortPath/$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseComicListing(response)

    // ============================== Filters =================================

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortFilter(),
        Filter.Separator(),
        GenreFilter(),
    )

    // =========================== Comic Listing ==============================

    private fun parseComicListing(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.comic-inner").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h2")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")?.encodeSpaces()
            }
        }

        val hasNextPage = document.selectFirst("li.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ==============================

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return document.selectFirst("div.comic-header")!!.let { info ->
            SManga.create().apply {
                title = info.selectFirst("h1")!!.text()
                genre = info.select("div:containsOwn(categories) a").joinToString { it.text() }
                artist = info.select("div:containsOwn(artists) a").joinToString { it.text() }
                thumbnail_url = document.selectFirst(".comic-listing .comic-inner img")?.absUrl("src")?.encodeSpaces()
                status = SManga.COMPLETED
                initialized = true
                description = buildString {
                    info.select("div:containsOwn(groups) a")
                        .takeIf { it.isNotEmpty() }
                        ?.also { if (isNotEmpty()) append("\n\n") }
                        ?.also { appendLine("Groups:") }
                        ?.joinToString("\n") { "- ${it.text()}" }
                        ?.also { append(it) }

                    info.select("div:containsOwn(parodies) a")
                        .takeIf { it.isNotEmpty() }
                        ?.also { if (isNotEmpty()) append("\n\n") }
                        ?.also { appendLine("Parodies:") }
                        ?.joinToString("\n") { "- ${it.text()}" }
                        ?.also { append(it) }
                }
            }
        }
    }

    // =========================== Chapter List ===============================

    override fun chapterListParse(response: Response): List<SChapter> = listOf(
        SChapter.create().apply {
            name = "Chapter"
            url = response.request.url.toString().substringAfter(baseUrl)
        },
    )

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ============================== Page List ===============================

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("div.comic-thumb img[src]").mapIndexed { i, img ->
        Page(i, imageUrl = img.absUrl("src").replace("/thumbnail/", "/original/").encodeSpaces())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Helpers =================================

    private fun String.encodeSpaces(): String = replace(" ", "%20")

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
