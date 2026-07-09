package eu.kanade.tachiyomi.extension.en.manhuarush

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.extractNextJsRsc
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class ManhuaRush : HttpSource() {

    override val supportsLatest = false

    private val rscHeaders: Headers by lazy {
        headersBuilder().add("RSC", "1").build()
    }

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("li a.nav-link[href=/ttp-providence]").map { element ->
            SManga.create().apply {
                url = element.attr("href")
                title = document.select("footer .footer-tagline strong").text()
                thumbnail_url = document.select("div[style*=background-image]").attr("style")
                    .substringAfter("url('")
                    .substringBefore("'")
                    .let { "$baseUrl$it" }
            }
        }

        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.select("h1.manga-title").text()
            description = document.select(".manga-desc p").text()
            thumbnail_url = document.select(".cover img").attr("abs:src")
            genre = document.select(".tag-pill").joinToString { it.text() }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.body.string().extractNextJsRsc<ChaptersDto>()
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
