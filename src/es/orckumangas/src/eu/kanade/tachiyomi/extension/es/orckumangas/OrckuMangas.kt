package eu.kanade.tachiyomi.extension.es.orckumangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.select.Elements

class OrckuMangas : HttpSource() {

    override val name = "Orcku Mangas"
    override val baseUrl = "https://orckumangas.com"
    override val lang = "es"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3, 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/ranking.php?page=$page", headers)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/index.php?filter_chapters=1&type=", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div > a.block").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.ownText()
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/buscador.php".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()

            return GET(url, headers)
        }
        val url = "$baseUrl/biblioteca.php".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> url.addQueryParameter("genre", filter.selected)
                is TypeFilter -> url.addQueryParameter("type", filter.selected)
                is StatusFilter -> url.addQueryParameter("status", filter.selected)
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Los filtros son ignorados si se realiza una búsqueda por texto"),
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.card > a").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.ownText()
                setUrlWithoutDomain(element.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("div.flex > a:containsOwn(Siguiente)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return document.selectFirst("main div.card")!!.let { element ->
            SManga.create().apply {
                title = element.selectFirst("h1")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                author = element.selectFirst("div:has(> span:containsOwn(Autor))")?.ownText()
                artist = element.selectFirst("div:has(> span:containsOwn(Artista))")?.ownText()
                status = element.selectFirst("div:has(> span:containsOwn(Estado))")?.ownText().parseStatus()
                genre = element.select("a[href*=genre]").joinToString { it.text() }
                description = element.selectFirst("p")?.text()
            }
        }
    }

    private fun String?.parseStatus(): Int = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$baseUrl${manga.url}".toHttpUrl().newBuilder()
            .setQueryParameter("order", "desc")
            .setQueryParameter("page", "1")
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url
        var document = response.asJsoup()
        val chapterList = mutableListOf<SChapter>()
        var page = url.queryParameter("page")!!.toInt()
        while (true) {
            val chapterElements = document.select("div.card div.grid > a.block")
            chapterList.addAll(parseChapters(chapterElements))

            val hasNextPage = document.selectFirst("div > a[href*=page=${page + 1}]") != null
            if (!hasNextPage) break
            page++

            val newUrl = url.newBuilder().setQueryParameter("page", page.toString()).build()
            document = client.newCall(GET(newUrl, headers)).execute().asJsoup()
        }

        return chapterList
    }

    private fun parseChapters(elements: Elements): List<SChapter> = elements.map {
        SChapter.create().apply {
            name = it.selectFirst("span")!!.ownText()
            setUrlWithoutDomain(it.attr("abs:href"))
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.chapter-images img").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
