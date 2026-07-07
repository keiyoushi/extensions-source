package eu.kanade.tachiyomi.extension.ar.brownmanga

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.Request
import okhttp3.Response

@Source
abstract class BrownManga : HttpSource() {
    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url}"

    private fun apiQuery(body: QueryBody): Request {
        val apiHeaders = headersBuilder()
            .set("Accept", "application/json")
            .set("Content-Type", "application/json")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
        return POST("$baseUrl/api/query", apiHeaders, body.toJsonRequestBody())
    }

    override fun popularMangaRequest(page: Int): Request {
        val body = QueryBody(
            table = "manhwa",
            select = "id, title, title_ar, slug, cover_url, status, type, description, description_ar",
            order = Order(column = "views", ascending = false),
            limit = 20,
            offset = (page - 1) * 20,
        )
        return apiQuery(body)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<ManhwaDto>>().map { it.toSManga() }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val body = QueryBody(
            table = "chapters",
            select = "id, chapter_number, title, created_at, is_locked, manhwa_id, manhwa:manhwa_id(id, title, title_ar, slug, cover_url, status, type)",
            order = Order(column = "created_at", ascending = false),
            limit = 20,
            offset = (page - 1) * 20,
        )
        return apiQuery(body)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<ChWithManhwaDto>>()
            .mapNotNull { it.manhwa?.toSManga() }
            .distinctBy { it.url }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = QueryBody(
            table = "manhwa",
            select = "id, title, title_ar, slug, cover_url, status, type, description, description_ar",
            filters = listOf(
                Filter(col = "title_ar", op = "ilike", `val` = "%$query%"),
            ),
            limit = 20,
            offset = (page - 1) * 20,
        )
        return apiQuery(body)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val body = QueryBody(
            table = "manhwa",
            select = "*",
            filters = listOf(Filter(col = "id", op = "eq", `val` = manga.url)),
            limit = 1,
        )
        return apiQuery(body)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<List<ManhwaDto>>().first().toSManga()

    override fun chapterListRequest(manga: SManga): Request {
        val body = QueryBody(
            table = "chapters",
            select = "id, chapter_number, title, is_locked, manhwa_id",
            filters = listOf(
                Filter(col = "manhwa_id", op = "eq", `val` = manga.url),
                Filter(col = "is_locked", op = "eq", `val` = "false"),
            ),
            order = Order(column = "chapter_number", ascending = false),
            limit = 500,
        )
        return apiQuery(body)
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<List<ChapterDto>>().map { ch ->
        SChapter.create().apply {
            url = ch.id
            name = ch.title?.ifEmpty { null }
                ?: "الفصل ${ch.chapterNumber?.toInt() ?: 0}"
            chapter_number = ch.chapterNumber?.toFloat() ?: 0f
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val body = QueryBody(
            table = "chapter_pages",
            select = "*",
            filters = listOf(Filter(col = "chapter_id", op = "eq", `val` = chapter.url)),
            order = Order(column = "page_number", ascending = true),
        )
        return apiQuery(body)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<List<ChapterPageDto>>().mapIndexed { index, page ->
        Page(index, imageUrl = page.imageUrl ?: "")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun ManhwaDto.toSManga() = SManga.create().apply {
        url = id
        title = titleAr?.ifEmpty { null } ?: this@toSManga.title!!
        thumbnail_url = coverUrl
        description = descriptionAr?.ifEmpty { null } ?: this@toSManga.description
        status = when (this@toSManga.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}
