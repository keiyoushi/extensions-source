package eu.kanade.tachiyomi.extension.en.manhuarush

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJsRsc
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ManhuaRush : HttpSource() {

    override val name = "Manhua Rush"
    override val baseUrl = "https://manhuarush.vercel.app"
    override val lang = "en"
    override val supportsLatest = false

    private val rscHeaders: Headers by lazy {
        headersBuilder().add("RSC", "1").build()
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/collections/all?p=all", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("a.card-link").map { element ->
            val img = element.selectFirst("img")!!
            SManga.create().apply {
                url = element.attr("href").substringBefore("?")
                title = img.attr("alt")
                thumbnail_url = img.attr("abs:src")
            }
        }

        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.body.string().extractNextJsRsc<MangaDetailsDto>()
            ?: throw Exception("Failed to extract manga details")

        return dto.toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.body.string().extractNextJsRsc<MangaDetailsDto>()
            ?: throw Exception("Failed to extract chapters")

        return dto.chapters.map { it.toSChapter(dto.mangadexId) }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.body.string().extractNextJsRsc<ReaderDto>()
            ?: return emptyList()

        return dto.toPages()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
}

// Shared date formatter - kept here since it's used by DTOs
internal val manhuaRushDateFormat: SimpleDateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
