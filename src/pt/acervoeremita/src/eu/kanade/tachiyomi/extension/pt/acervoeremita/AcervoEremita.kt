package eu.kanade.tachiyomi.extension.pt.acervoeremita

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class AcervoEremita : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds)

    override suspend fun getPopularManga(page: Int) = parseMangasPage(client.get("$baseUrl/explore?page=$page"))

    override suspend fun getLatestUpdates(page: Int) = parseMangasPage(client.get("$baseUrl/explore?page=$page&sort=release&dir=desc"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/explore".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)
            .build()
        return parseMangasPage(client.get(url))
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val dto = response.extractNextJs<PageableMangas>()!!
        val mangas = dto.works.map { it.toSManga() }
        return MangasPage(mangas, dto.pagination.hasNext)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/work".toHttpUrl().newBuilder()
        .addPathSegment(manga.memo["slug"]!!.string)
        .build().toString()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!setOf(baseUrl.toHttpUrl().host, "work").all { url.pathSegments.contains(it) }) {
            return null
        }
        return parseSManga(client.get(url).asJsoup())
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/work/${chapter.memo["slug"]!!.string}/read?chapter=${chapter.chapter_number}&page=1"

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val manga = parseSManga(document)

        val chapterList: List<SChapter> = when {
            fetchChapters -> fetchChaptersList(manga)
            else -> chapters
        }

        return SMangaUpdate(manga, chapterList)
    }

    private fun parseSManga(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst("p[data-component*=description]")?.text()
        genre = document.select("[data-component*=tags] > span").joinToString { it.text() }
        url = document.extractNextJs<WorkId>()!!.workId.toString()
        memo = buildJsonObject {
            put("slug", document.location().toHttpUrl().pathSegments.last())
        }
    }

    private suspend fun fetchChaptersList(manga: SManga): List<SChapter> {
        val offset = 30.0
        var cursor = 0.0
        val chapters = mutableListOf<SChapter>()
        do {
            val dto = client.get("$baseUrl/api/work/chapters?workId=${manga.url}&cursor=$cursor&limit=30")
                .parseAs<PageableChapters>()
            cursor += offset
            chapters += dto.chapters.map { it.toSChapter(manga) }
        } while (dto.pagination.hasNext)
        return chapters.reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("[data-component*=ReaderScreen] > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Accept-Language", "pt-BR,en-US;q=0.9,en;q=0.8")
            .set("Accept-Encoding", "gzip, deflate")
            .set("Sec-Fetch-Dest", "image")
            .set("Sec-Fetch-Mode", "no-cors")
            .set("Sec-Fetch-Site", "same-origin")
            .set("Referer", page.imageUrl!!)
            .removeAll("Origin")
            .build()
        return super.imageRequest(page).newBuilder()
            .headers(headers)
            .build()
    }
}
