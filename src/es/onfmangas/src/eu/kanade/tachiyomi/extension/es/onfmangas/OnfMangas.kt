package eu.kanade.tachiyomi.extension.es.onfmangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OnfMangas : HttpSource() {

    override val name = "ONF MANGAS"
    override val baseUrl = "https://onfmangas.com"
    override val lang = "es"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/populares.php", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".manga-grid .manga-card").mapNotNull { element ->
            SManga.create().apply {
                title = element.selectFirst(".manga-title")?.text()
                    ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                setUrlWithoutDomain(
                    element.selectFirst("a")?.attr("abs:href")
                        ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                )
                thumbnail_url = element.selectFirst(".card-cover img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/mangas.php?tab=general&genero=0&q=&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".manga-grid .manga-card").mapNotNull { element ->
            SManga.create().apply {
                title = element.selectFirst(".manga-title")?.text()
                    ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                setUrlWithoutDomain(
                    element.selectFirst("a")?.attr("abs:href")
                        ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                )
                thumbnail_url = element.selectFirst(".card-cover img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst(".pagination a.page-btn:contains(Siguiente)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/mangas.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())

        val tab = filters.firstInstanceOrNull<TabFilter>()?.selected ?: "general"
        val genero = filters.firstInstanceOrNull<GenreFilter>()?.selected ?: "0"

        url.addQueryParameter("tab", tab)
        url.addQueryParameter("genero", genero)

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun getFilterList() = FilterList(
        TabFilter(),
        GenreFilter(),
    )

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".manga-title")?.text()
                ?.takeIf { it.isNotEmpty() }
                ?: throw Exception("Could not parse manga title")
            author = document.selectFirst("div:contains(Autor:) b")?.text()
            description = document.selectFirst(".manga-description")?.text()
            genre = document.select(".genre-tag").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".manga-poster")?.attr("abs:src")

            val statusText = document.select(".manga-meta span").last()?.text()
            status = when {
                statusText?.contains("EMISIÓN", true) == true -> SManga.ONGOING
                statusText?.contains("FINALIZADO", true) == true -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val hexString = document.selectFirst("script:containsData(const _hex =)")
            ?.data()
            ?.substringAfter("const _hex = \"")
            ?.substringBefore("\";")
            ?: return emptyList()

        val jsonString = decodeHex(hexString)
        val chaptersData = jsonString.parseAs<List<ChapterDto>>()

        val chapters = mutableListOf<SChapter>()

        // Simulating the source's client-side descending sorting
        val sortedChapters = chaptersData.sortedWith(
            compareByDescending<ChapterDto> { it.numberFloat }
                .thenByDescending { it.date },
        )

        for (dto in sortedChapters) {
            val parentChapter = dto.toSChapter().apply {
                date_upload = dateFormat.tryParse(dto.date)
            }
            chapters.add(parentChapter)

            // Inject joint/alternative scanlator versions if they exist
            dto.getOtherVersions()?.forEach { otherVersion ->
                chapters.add(
                    otherVersion.toSChapter(dto).apply {
                        date_upload = parentChapter.date_upload
                    },
                )
            }
        }
        return chapters
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val hexString = document.selectFirst("script:containsData(const _hexP =)")
            ?.data()
            ?.substringAfter("const _hexP = \"")
            ?.substringBefore("\";")
            ?: return emptyList()

        val jsonString = decodeHex(hexString)
        val pagesData = jsonString.parseAs<List<PageDto>>()

        return pagesData.mapIndexed { index, dto -> dto.toPage(index) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    private fun decodeHex(hexString: String): String {
        require(hexString.length % 2 == 0) { "Must have an even length" }
        val bytes = hexString.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        return String(bytes, Charsets.UTF_8)
    }
}
