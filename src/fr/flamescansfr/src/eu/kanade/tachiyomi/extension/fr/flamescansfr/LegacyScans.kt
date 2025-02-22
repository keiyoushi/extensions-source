package eu.kanade.tachiyomi.extension.fr.flamescansfr

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class LegacyScans : HttpSource() {
    override val name: String = "Legacy Scans"

    override val lang: String = "fr"

    override val baseUrl: String = "https://legacy-scans.com"

    override val supportsLatest: Boolean = true

    override val versionId: Int = 2

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/misc/views/all", headers)

    override fun popularMangaParse(response: Response) =
        mangasPageParse(response.parseAs<List<MangaDto>>(), false)

    override fun latestUpdatesRequest(page: Int): Request {
        val offset = pageOffset(page)
        val url = "$apiUrl/misc/comic/home/updates".toHttpUrl().newBuilder()
            .addQueryParameter("start", "${offset.first}")
            .addQueryParameter("end", "${offset.second}")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage =
        mangasPageParse(response.parseAs<List<MangaDto>>())

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(URL_SEARCH_PREFIX)) {
            val manga = SManga.create().apply {
                url = "/comics/${query.substringAfter(URL_SEARCH_PREFIX)}"
            }
            client.newCall(mangaDetailsRequest(manga)).asObservableSuccess().map { response ->
                val document = response.asJsoup()
                when {
                    isMangaPage(document) -> {
                        MangasPage(listOf(mangaDetailsParse(document)), false)
                    }
                    else -> MangasPage(emptyList(), false)
                }
            }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/misc/home/search".toHttpUrl().newBuilder()
                .addQueryParameter("title", query)
                .build()
            return GET(url, headers)
        }

        val defaultSearchOffSet = 18

        val offset = pageOffset(page, defaultSearchOffSet)

        val url = "$apiUrl/misc/comic/search/query".toHttpUrl().newBuilder()
            .addQueryParameter("start", "${offset.first}")
            .addQueryParameter("end", "${offset.second}")

        filters.forEach { filter ->
            when (filter) {
                is SelectFilter -> {
                    val selected = filter.selectedValue()
                    if (selected.isBlank()) return@forEach
                    url.addQueryParameter(filter.field, selected)
                }
                is GenreList -> {
                    val genres = filter.state
                        .filter(GenreCheckBox::state)
                        .joinToString(",") { it.name }
                    if (genres.isBlank()) return@forEach
                    url.addQueryParameter("genreNames", genres)
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val pathSegments = response.request.url.pathSegments
        return when {
            pathSegments.contains("comic") -> {
                mangasPageParse(response.parseAs<SearchDto>().comics)
            }
            else -> {
                mangasPageParse(response.parseAs<SearchQueryDto>().results, false)
            }
        }
    }

    val mangaDetailsDescriptionSelector = ".serieDescription p"

    override fun mangaDetailsParse(response: Response): SManga =
        mangaDetailsParse(response.asJsoup())

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapterList a").map { element ->
            SChapter.create().apply {
                val spans = element.select("span").map { it.text() }
                name = spans.first()
                date_upload = spans.last().toDate()
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".readerMainContainer img").mapIndexed { index, image ->
            Page(index, document.location(), image.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun getFilterList(): FilterList {
        return FilterList(
            SelectFilter("Status", "status", statusList),
            SelectFilter("Type", "type", typesList),
            GenreList("Genres", genresList),
        )
    }

    private fun mangaDetailsParse(document: Document): SManga {
        return with(document.selectFirst(".serieContainer")!!) {
            SManga.create().apply {
                title = selectFirst("h1")!!.text()
                thumbnail_url = selectFirst("img")?.absUrl("src")
                genre = select(".serieGenre span").joinToString { it.text() }
                description = selectFirst(mangaDetailsDescriptionSelector)?.text()
                author = selectFirst(".serieAdd p:contains(produit) strong")?.text()
                artist = selectFirst(".serieAdd p:contains(Auteur) strong")?.text()
                setUrlWithoutDomain(document.location())
            }
        }
    }

    private fun pageOffset(page: Int, max: Int = 28): Pair<Int, Int> {
        val start = max * (page - 1) + 1
        val end = max * page
        return start to end
    }

    private fun mangasPageParse(dto: List<MangaDto>, hasNextPage: Boolean = true): MangasPage {
        val mangas = dto.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = it.cover?.let { cover -> "$apiUrl/$cover" }
                url = "/comics/${it.slug}"
            }
        }
        return MangasPage(mangas, hasNextPage && mangas.isNotEmpty())
    }

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())

    private fun isMangaPage(document: Document): Boolean =
        document.selectFirst(mangaDetailsDescriptionSelector) != null

    private fun String.toDate(): Long =
        try { dateFormat.parse(trim())!!.time } catch (_: Exception) { 0L }

    companion object {
        const val apiUrl = "https://api.legacy-scans.com"
        const val URL_SEARCH_PREFIX = "slug:"
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)
    }
}
