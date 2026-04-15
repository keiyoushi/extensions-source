package eu.kanade.tachiyomi.extension.pt.astratoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

class Astratoons : HttpSource() {

    override val name = "Astratoons"

    override val baseUrl = "https://new.astratoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()
    override val versionId: Int = 2
    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ======================== Popular ==========================

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#comicsSlider a").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")?.text() ?: "Unknown"
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ======================== Latest ==========================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "updated_at")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<ComicsResponseDto>()
        val mangas = dto.data.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, dto.currentPage < dto.lastPage)
    }

    // ======================== Search ==========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("search", query)
        }

        filters.firstInstanceOrNull<SortFilter>()?.let {
            url.addQueryParameter("sortBy", it.toQuery())
        }

        filters.firstInstanceOrNull<StatusFilter>()?.toQuery()
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("status", it) }

        filters.firstInstanceOrNull<TypeFilter>()?.state
            ?.filter { it.state }
            ?.forEach { url.addQueryParameter("types[]", it.value) }

        filters.firstInstanceOrNull<TagFilter>()?.state
            ?.filter { it.state }
            ?.forEach { url.addQueryParameter("tags[]", it.value) }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // ======================== Details =========================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")?.text() ?: "Unknown"
        thumbnail_url = document.selectFirst("img[class*=object-cover]")?.absUrl("src")
        description = document.selectFirst("div.space-y-4 > p")?.text()
            ?: document.selectFirst("div:has(>h1) + div")?.text()
        genre = document.select("h3:contains(Tags) + div a").joinToString { it.text() }
        author = document.selectFirst("span:contains(Autor) > span")?.text()
        artist = document.selectFirst("span:contains(Artista) > span")?.text()

        val statusText = document.selectFirst("h3:contains(Informações) + div span.capitalize")?.text()
        status = when (statusText?.lowercase()) {
            "em andamento", "em dia" -> SManga.ONGOING
            "completo" -> SManga.COMPLETED
            "hiato" -> SManga.ON_HIATUS
            "cancelado", "dropado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // ======================== Chapter =========================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val html = document.html()
        val mangaId = MANGA_ID.find(html)?.groupValues?.get(1)
            ?: throw Exception("Could not find manga id")

        var page = 1
        var hasMore = true
        val chapters = mutableListOf<SChapter>()

        while (hasMore) {
            val url = "$baseUrl/api/comics/$mangaId/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("search", "")
                .addQueryParameter("order", "desc")
                .addQueryParameter("page", page.toString())
                .build()

            val res = client.newCall(GET(url, headers)).execute()
            val dto = res.parseAs<ChapterListDto>()

            val fragment = Jsoup.parseBodyFragment(dto.html, baseUrl)
            chapters += fragment.select("a").map { element ->
                SChapter.create().apply {
                    name = element.selectFirst(".text-lg")?.text() ?: "Chapter"
                    setUrlWithoutDomain(element.absUrl("href"))
                }
            }

            hasMore = dto.hasMore
            page++
        }

        return chapters
    }

    // ======================== Pages ===========================

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#reader-container img[src], #reader-container canvas[data-src]")
            .mapIndexed { index, element ->
                val imageUrl = element.absUrl("src").ifEmpty { element.absUrl("data-src") }
                Page(index, document.location(), imageUrl)
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ======================== Filters =========================

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        TagFilter(),
    )

    companion object {
        val MANGA_ID = """comicId:\s*(\d+)""".toRegex()
    }
}
