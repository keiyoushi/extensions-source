package eu.kanade.tachiyomi.extension.es.elvosque

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class ElVosque : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(series(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(series().filter { it.title.contains(query.trim(), ignoreCase = true) }, false)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host || segments[0] !in listOf("archive", "comic")) return null
        val key = segments.getOrNull(1)?.ifEmpty { null } ?: return null
        val series = series()
        if (key.toIntOrNull() == null) return series.firstOrNull { it.url == "/archive/$key/" }?.apply { initialized = true }

        // Shared links leave the series out, like /comic/3016/otono-2: look for the page in every series
        return coroutineScope {
            series.map { manga -> async { manga.takeIf { chapterList(manga).any { "/$key/" in it.url } } } }.awaitAll()
        }.firstNotNullOfOrNull { it }?.apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) series().first { it.url == manga.url } else manga }
        val chapterList = async { if (fetchChapters) chapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    // Every page of the comic is a chapter, as each one has its own title and date on the site
    private suspend fun chapterList(manga: SManga): List<SChapter> {
        val links = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup().select("#content_div2 p > a[href*=/comic/]")
        return links.mapIndexed { i, link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.ownText().removePrefix("-").trim()
                date_upload = DateTimeFormatter.ISO_LOCAL_DATE.tryParseDate(link.selectFirst("i")?.text(), ZONE)
                chapter_number = (i + 1).toFloat()
            }
        }.reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".img_holder img")
        .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("src")) }

    // The archive lists the covers of the series first, then their descriptions, paired by number
    private suspend fun series(): List<SManga> {
        val document = client.get("$baseUrl/archive/", Fetch.DOCUMENT).asJsoup()
        return document.select("[id^=portadaserie]").map { cover ->
            val about = document.selectFirst("#contenido_serie${cover.id().removePrefix("portadaserie")}")!!
            SManga.create().apply {
                setUrlWithoutDomain(about.selectFirst("a[href*=/archive/]")!!.absUrl("href"))
                title = cover.selectFirst("p")!!.text()
                thumbnail_url = cover.selectFirst("img")?.absUrl("src")
                author = "Morán"
                artist = "Laurielle"
                description = about.select("> p").fold(MarkdownDescription.Builder()) { builder, paragraph -> builder.description(paragraph) }.build()
            }
        }
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Madrid")
    }
}
