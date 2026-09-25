package eu.kanade.tachiyomi.extension.es.inmanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

@Source
abstract class InManga : KeiSource() {

    private val dateFormat = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .toFormatter()

    private val imageCDN = "https://cdn1.intomanga.com"

    private fun mangaRequestBody(page: Int, sortBy: Int, query: String = "") = FormBody.Builder()
        .add("filter[generes][]", "-1")
        .add("filter[queryString]", query)
        .add("filter[skip]", ((page - 1) * 10).toString())
        .add("filter[take]", "10")
        .add("filter[sortby]", sortBy.toString())
        .add("filter[broadcastStatus]", "0")
        .add("filter[onlyFavorites]", "false")
        .add("d", "")
        .build()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.post(
            url = "$baseUrl/manga/getMangasConsultResult",
            body = mangaRequestBody(page, 1),
        ).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.post(
            url = "$baseUrl/manga/getMangasConsultResult",
            body = mangaRequestBody(page, 3),
        ).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val document = client.post(
            url = "$baseUrl/manga/getMangasConsultResult",
            body = mangaRequestBody(page, 1, query),
        ).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.getOrNull(0) != "ver" || segments.getOrNull(1) != "manga") return null

        val mangaUrl = when (segments.size) {
            4 -> url.toString()
            5 -> mangaUrlFromChapter(url) ?: return null
            else -> return null
        }

        return fetchMangaDetails(SManga.create().apply { setUrlWithoutDomain(mangaUrl) }).apply {
            setUrlWithoutDomain(mangaUrl)
            initialized = true
        }
    }

    private suspend fun mangaUrlFromChapter(url: HttpUrl): String? {
        val slug = url.pathSegments.getOrNull(2) ?: return null
        val mangaId = client.get(url).asJsoup()
            .select("script:containsData(var mid =)")
            .firstNotNullOfOrNull { script ->
                script.data()
                    .substringAfter("var mid = '")
                    .substringBefore("'")
            }
            ?: return null
        return "$baseUrl/ver/manga/$slug/$mangaId"
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val elements = document.select("body > a")
        val hasNextPage = elements.size == 10
        return MangasPage(elements.map { mangaFromElement(it) }, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("h4.m0")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SManga.create().apply {
            document.selectFirst("div.col-md-3 div.panel.widget")?.let { info ->
                thumbnail_url = info.selectFirst("img")?.absUrl("src")
                status = info.selectFirst("a.list-group-item:contains(estado) span")?.text().let(::parseStatus)
            }
            document.selectFirst("div.col-md-9")!!.let { info ->
                title = info.selectFirst("h1")!!.text()
                description = info.selectFirst("div.panel-body")?.textOrNull()
            }
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("En emisión") -> SManga.ONGOING
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val data = client.get("$baseUrl/chapter/getall?mangaIdentification=${manga.url.substringAfterLast("/")}").parseAs<InMangaResultDto>()
        if (data.data.isNullOrEmpty()) {
            return emptyList()
        }

        val result = data.data.parseAs<InMangaResultObjectDto<InMangaChapterDto>>()
        if (!result.success) {
            return emptyList()
        }

        return result.result
            .map { chapterFromObject(it) }
            .sortedByDescending { it.chapter_number }
    }

    private fun chapterFromObject(chapter: InMangaChapterDto) = SChapter.create().apply {
        url = "/chapter/chapterIndexControls?identification=${chapter.identification}"
        name = "Chapter ${chapter.friendlyChapterNumber}"
        chapter_number = chapter.number?.toFloat() ?: 0f
        date_upload = dateFormat.tryParseDateTime(chapter.registrationDate)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val chapterId = document.selectFirst("input#ChapterIdentification")!!.attr("value")
        val mangaId = document.selectFirst("input#MangaIdentification")!!.attr("value")

        return document.select("img.ImageContainer").mapIndexed { i, img ->
            Page(i, imageUrl = "$imageCDN/i/m/$mangaId/c/$chapterId/o/${img.attr("id")}.jpg")
        }
    }
}
