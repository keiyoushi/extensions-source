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
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter

@Source
abstract class InManga : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val postHeaders = headers.newBuilder()
        .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    private val imageCDN = "https://cdn1.intomanga.com"

    private fun requestBodyBuilder(page: Int, isPopular: Boolean): RequestBody = "filter%5Bgeneres%5D%5B%5D=-1&filter%5BqueryString%5D=&filter%5Bskip%5D=${(page - 1) * 10}&filter%5Btake%5D=10&filter%5Bsortby%5D=${if (isPopular) "1" else "3"}&filter%5BbroadcastStatus%5D=0&filter%5BonlyFavorites%5D=false&d="
        .toRequestBody(null)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.post(
            url = "$baseUrl/manga/getMangasConsultResult",
            headers = postHeaders,
            body = requestBodyBuilder(page, true),
        ).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.post(
            url = "$baseUrl/manga/getMangasConsultResult",
            headers = postHeaders,
            body = requestBodyBuilder(page, false),
        ).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val skip = (page - 1) * 10
        val body =
            "filter%5Bgeneres%5D%5B%5D=-1&filter%5BqueryString%5D=$query&filter%5Bskip%5D=$skip&filter%5Btake%5D=10&filter%5Bsortby%5D=1&filter%5BbroadcastStatus%5D=0&filter%5BonlyFavorites%5D=false&d="
                .toRequestBody(null)
        val document = client.post("$baseUrl/manga/getMangasConsultResult", postHeaders, body).asJsoup()
        return parseMangasPage(document)
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val elements = document.select("body > a")

        val mangas = elements.map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.select("h4.m0").text()
                thumbnail_url = element.select("img").attr("abs:data-src")
            }
        }

        return MangasPage(mangas, elements.size == 10)
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
            document.select("div.col-md-3 div.panel.widget").let { info ->
                thumbnail_url = info.select("img").attr("abs:src")
                status = parseStatus(info.select("a.list-group-item:contains(estado) span").text())
            }
            document.select("div.col-md-9").let { info ->
                title = info.select("h1").text()
                description = info.select("div.panel-body").text()
            }
        }
    }

    private fun parseStatus(status: String) = when {
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
        date_upload = dateFormat.tryParseDate(chapter.registrationDate)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val chapterId = document.select("input#ChapterIdentification").attr("value")
        val mangaId = document.select("input#MangaIdentification").attr("value")

        return document.select("img.ImageContainer").mapIndexed { i, img ->
            Page(i, imageUrl = "$imageCDN/i/m/$mangaId/c/$chapterId/o/${img.attr("id")}.jpg")
        }
    }
}
