package eu.kanade.tachiyomi.extension.es.traduccionesmoonlight

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import kotlin.math.min

class TraduccionesMoonlight : HttpSource() {

    override val versionId = 3

    override val name = "Traducciones Moonlight"

    override val baseUrl = "https://traduccionesmoonlight.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder() = Headers.Builder()
        .add("Referer", "$baseUrl/")

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    private val seriesPath = "/ver"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/topSerie", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val responseData = response.parseAs<ResponseDto<TopSeriesDto>>()

        val topDaily = responseData.response.topDaily.flatten().map { it.data }
        val topWeekly = responseData.response.topWeekly.flatten().map { it.data }
        val topMonthly = responseData.response.topMonthly.flatten().map { it.data }

        val mangas = (topDaily + topWeekly + topMonthly).distinctBy { it.slug }
            .map { it.toSManga(seriesPath) }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/lastUpdates", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val responseData = response.parseAs<ResponseDto<List<SeriesDto>>>()

        val mangas = responseData.response
            .map { it.toSManga(seriesPath) }

        return MangasPage(mangas, false)
    }

    private var comicsList = listOf<SeriesDto>()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = if (comicsList.isEmpty()) {
        client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map {
                comicsList = it.parseAs<ResponseDto<List<SeriesDto>>>().response
                applyFilters(comicsList, page, query, filters)
            }
    } else {
        Observable.just(applyFilters(comicsList, page, query, filters))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/comics", headers)

    private fun applyFilters(comics: List<SeriesDto>, page: Int, query: String, filterList: FilterList): MangasPage {
        var filteredList = mutableListOf<SeriesDto>()

        if (query.isNotBlank()) {
            if (query.length < 2) throw Exception("La búsqueda debe tener al menos 2 caracteres")
            filteredList.addAll(
                comicsList.filter {
                    it.name.contains(query, ignoreCase = true) || it.alternativeName?.contains(query, ignoreCase = true) == true
                },
            )
        } else {
            filteredList.addAll(comics)
        }

        val statusFilter = filterList.firstInstanceOrNull<StatusFilter>()

        if (statusFilter != null) {
            if (statusFilter.toUriPart() != 0) {
                filteredList = filteredList.filter { it.status == statusFilter.toUriPart() }.toMutableList()
            }
        }

        val sortByFilter = filterList.firstInstanceOrNull<SortByFilter>()

        if (sortByFilter != null) {
            when (sortByFilter.selected) {
                "name" -> filteredList.sortBy { it.name }
                "views" -> filteredList.sortBy { it.trending?.views }
                "updated_at" -> filteredList.sortBy { it.lastChapterDate }
                "created_at" -> filteredList.sortBy { it.createdAt }
            }

            if (sortByFilter.state?.ascending == false) {
                filteredList.reverse()
            }
        }

        val hasNextPage = filteredList.size > page * MANGAS_PER_PAGE

        return MangasPage(
            filteredList.subList((page - 1) * MANGAS_PER_PAGE, min(page * MANGAS_PER_PAGE, filteredList.size))
                .map { it.toSManga(seriesPath) },
            hasNextPage,
        )
    }

    override fun getFilterList() = getFilters()

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga = response.extractNextJs<SeriesDto>()!!.toSMangaDetails()

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val series = response.extractNextJs<SeriesDto>()!!
        return series.chapters.map { it.toSChapter(seriesPath, series.slug) }
    }

    override fun pageListParse(response: Response): List<Page> {
        var doc = response.asJsoup()
        val form = doc.selectFirst("form[method=post]")
        if (form != null) {
            val url = form.attr("action")
            val headers = headersBuilder().set("Referer", doc.location()).build()
            val body = FormBody.Builder()
            form.select("input").forEach {
                body.add(it.attr("name"), it.attr("value"))
            }
            doc = client.newCall(POST(url, headers, body.build())).execute().asJsoup()
        }
        return doc.select("main.contenedor.read img, main > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.imgAttr())
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        const val MANGAS_PER_PAGE = 15
    }
}
