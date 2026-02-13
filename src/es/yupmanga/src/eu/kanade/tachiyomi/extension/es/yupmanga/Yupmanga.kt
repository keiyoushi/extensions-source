package eu.kanade.tachiyomi.extension.es.yupmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Yupmanga : HttpSource() {

    override val name = "Yupmanga"

    override val baseUrl = "https://www.yupmanga.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3, 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top", headers)

    override fun popularMangaParse(response: Response) = parseSeriesList(response.asJsoup())

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesParse(response: Response) = parseSeriesList(response.asJsoup())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.length < 3) {
            throw Exception("El término de búsqueda debe tener al menos 3 caracteres.")
        }
        val url = "$baseUrl/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        document.selectFirst("main > div.container > div[class^=bg-red]:has(p)")?.let {
            throw Exception("Límite de solicitudes alcanzado. Intente de nuevo en unos minutos.")
        }
        return parseSeriesList(document)
    }

    private fun parseSeriesList(document: Document): MangasPage {
        val mangas = document.selectFirst("div.grid:has(> div.comic-card)")?.select("div.comic-card")?.map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.text()
                val rawUrl = element.selectFirst("> a[href]")!!.attr("abs:href")
                url = rawUrl.toHttpUrl().queryParameter("id")!!
                thumbnail_url = element.selectFirst("img.object-cover")?.attr("abs:src")
            }
        } ?: emptyList()
        val hasNextPage = document.selectFirst("div.flex > a:contains(Siguiente)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/series.php?id=${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            with(document.selectFirst("main > div.container")!!) {
                title = selectFirst("h1")!!.text()
                description = selectFirst("p#synopsisText")?.text()
                author = selectFirst("i[title=Editorial] + span")?.text()
                status = selectFirst("span:has(i[title=Estado])").parseStatus()
                genre = select("a.genre-tag").joinToString { genre ->
                    genre.text().replaceFirstChar { it.uppercase() }
                }
                thumbnail_url = document.selectFirst("meta[property=og:image]")!!.attr("content")
            }
        }
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "activo" -> SManga.ONGOING
        "finalizado" -> SManga.COMPLETED
        "abandonado" -> SManga.CANCELLED
        "pausado" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun paginatedChapterListRequest(mangaId: String, page: Int): Request {
        val url = "$baseUrl/ajax/load_chapters.php".toHttpUrl().newBuilder()
            .addQueryParameter("series_id", mangaId)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("order", "newest_first")

        return GET(url.build(), headers)
    }

    override fun chapterListRequest(manga: SManga): Request = paginatedChapterListRequest(manga.url, 1)

    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        val mangaId = response.request.url.queryParameter("series_id")!!

        lateinit var chapterListDto: ChapterListDto

        var page = 1
        do {
            chapterListDto = if (page == 1) {
                response.parseAs()
            } else {
                client.newCall(
                    paginatedChapterListRequest(mangaId, page),
                ).execute().parseAs()
            }

            val doc = Jsoup.parseBodyFragment(chapterListDto.html, baseUrl)
            allChapters.addAll(parseChapterList(doc))

            page++
        } while (chapterListDto.hasNextPage())

        return allChapters
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("div.comic-card").map { element ->
        SChapter.create().apply {
            name = element.selectFirst("h3")!!.text()
            setUrlWithoutDomain(element.selectFirst("> a[href]")!!.attr("abs:href"))
        }
    }

    private val totalPagesRegex = """totalPages: (\d*)""".toRegex()
    private val imageTokenRegex = """readerImageToken\s*=\s*"(.*?)"""".toRegex()

    override fun pageListParse(response: Response): List<Page> {
        val chapterId = response.request.url.queryParameter("chapter")!!
        val document = response.asJsoup()
        val script = document.select("script:containsData(totalPages)").joinToString("\n")
        val totalPages = totalPagesRegex.find(script)?.groupValues?.get(1)?.toInt()!!
        val imageToken = imageTokenRegex.find(script)?.groupValues?.get(1)!!
        return (1..totalPages).map { pageNumber ->
            val imageUrl = "$baseUrl/image-proxy-v2.php".toHttpUrl().newBuilder()
                .addQueryParameter("chapter", chapterId)
                .addQueryParameter("page", pageNumber.toString())
                .addQueryParameter("context", "reader")
                .addQueryParameter("token", imageToken)
                .build()

            Page(pageNumber, imageUrl = imageUrl.toString())
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    @Serializable
    internal class ChapterListDto(
        val html: String,
        private val currentPage: Int,
        private val totalPages: Int,
    ) {
        fun hasNextPage() = currentPage < totalPages
    }
}
