package eu.kanade.tachiyomi.extension.es.neomanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class NeoManga : HttpSource() {

    override val name = "NeoManga"
    override val baseUrl = "https://www.neomanga.online"
    override val lang = "es"
    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val rscHeaders = headersBuilder()
        .add("RSC", "1")
        .build()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series", rscHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.extractNextJs<InitialMangasDto> {
            it is JsonObject && "initialMangas" in it
        }
        val mangas = data?.initialMangas?.map { it.toSManga(baseUrl) } ?: emptyList()

        return MangasPage(mangas, false)
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = client.newCall(popularMangaRequest(page))
        .asObservableSuccess()
        .map { response ->
            val data = response.extractNextJs<InitialMangasDto> {
                it is JsonObject && "initialMangas" in it
            }
            var mangas = data?.initialMangas ?: emptyList()

            if (query.isNotBlank()) {
                mangas = mangas.filter { it.title.contains(query, ignoreCase = true) }
            }

            val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
            if (statusFilter != null && statusFilter.selectedValue != "all") {
                mangas = mangas.filter { it.status == statusFilter.selectedValue }
            }

            val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
            if (genreFilter != null && genreFilter.state != 0) {
                val selectedGenre = genreFilter.selectedValue
                mangas = mangas.filter { it.genres.contains(selectedGenre) }
            }

            MangasPage(mangas.map { it.toSManga(baseUrl) }, false)
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Details ==============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/manga/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")?.text()
                ?: throw Exception("Could not find manga title")
            description = document.selectFirst(".whitespace-pre-line")?.text()
            genre = document.select("span.bg-accent-soft").joinToString(", ") { it.text() }

            // Use the _next/image proxy URL as-is from the HTML
            thumbnail_url = document.selectFirst(".aspect-\\[3\\/4\\] img")?.attr("abs:src")

            val statusText = document.selectFirst("span.bg-success, span.bg-danger, span.bg-secondary")?.text()?.lowercase()
            status = when {
                statusText == null -> SManga.UNKNOWN
                statusText.contains("emisión") -> SManga.ONGOING
                statusText.contains("finalizado") -> SManga.COMPLETED
                statusText.contains("pausado") -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================= Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/manga/${manga.url}", rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<MangaDetailsDataDto> {
            it is JsonObject && "chapters" in it
        }

        val mangaUrl = response.request.url.encodedPath

        return data?.chapters?.map { chapter ->
            SChapter.create().apply {
                val chNumStr = chapter.chapterNumber.toString().removeSuffix(".0")
                url = "$mangaUrl/capitulo/$chNumStr"
                name = chapter.title ?: "Capítulo $chNumStr"
                chapter_number = chapter.chapterNumber
                date_upload = dateFormat.tryParse(chapter.publishedAt)
            }
        }?.sortedByDescending { it.chapter_number } ?: emptyList()
    }

    // =============================== Pages ===============================
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<ChapterPageDataDto> {
            it is JsonObject && "chapter" in it
        }

        val pagesUrls = data?.chapter?.pagesUrls ?: emptyList()
        if (pagesUrls.isEmpty()) throw Exception("No se encontraron páginas")

        val directUrls = mutableListOf<String>()

        for (url in pagesUrls) {
            if (url.startsWith("MANGADEX:")) {
                val id = url.removePrefix("MANGADEX:")
                client.newCall(GET("$baseUrl/api/mangadex-pages/$id", headers)).execute().use { apiResponse ->
                    val apiData = apiResponse.parseAs<MangadexPagesDto>()
                    for (i in apiData.pages.indices) {
                        directUrls.add("$baseUrl/api/manga-page/$id/$i")
                    }
                }
            } else {
                directUrls.add(url)
            }
        }

        return directUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    override fun getFilterList() = FilterList(
        StatusFilter(),
        GenreFilter(),
    )
}
