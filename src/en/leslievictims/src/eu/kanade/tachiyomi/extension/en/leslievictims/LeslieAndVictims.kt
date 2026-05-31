package eu.kanade.tachiyomi.extension.en.leslievictims

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class LeslieAndVictims : HttpSource() {

    override val name = "Leslie&Victims"

    override val baseUrl = "https://leslie-victims.pages.dev"

    override val lang = "en"

    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/library", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<LibraryEntry>>().map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/library#$query", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.fragment ?: ""
        val mangas = response.parseAs<List<LibraryEntry>>()
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request = libraryRequestForManga(manga)

    override fun mangaDetailsParse(response: Response): SManga = findEntry(response).toSManga(baseUrl)

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = libraryRequestForManga(manga)

    override fun chapterListParse(response: Response): List<SChapter> = findEntry(response).getChapters(baseUrl)

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val url = (baseUrl + chapter.url).toHttpUrl()
        val seriesId = url.queryParameter("series")
            ?: throw Exception("Missing series ID in chapter URL")
        val chId = url.queryParameter("ch")
            ?: throw Exception("Missing chapter ID in chapter URL")
        return GET("$baseUrl/api/library#$seriesId|$chId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val fragment = response.request.url.fragment
            ?: throw Exception("Missing fragment in page list request")
        val (seriesId, chId) = fragment.split("|")

        val entry = response.parseAs<List<LibraryEntry>>()
            .find { it.getId() == seriesId }
            ?: throw Exception("Series not found: $seriesId")

        val chapterRoot = entry.getChapterRoot(chId)

        if (chapterRoot != null) {
            val rootUrl = chapterRoot.url
            return when (chapterRoot.mode) {
                "list" ->
                    chapterRoot.data.jsonArray
                        .map { it.jsonPrimitive.content }
                        .mapIndexed { i, file -> Page(i, imageUrl = "$rootUrl/$file") }
                "count" -> {
                    val count = chapterRoot.data.jsonPrimitive.content.toInt()
                    (1..count).map { i ->
                        Page(i - 1, imageUrl = "$rootUrl/${i.toString().padStart(2, '0')}.webp")
                    }
                }
                else -> emptyList()
            }
        }

        val baseImgUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("content")
            .addPathSegment(seriesId)
            .addPathSegment(chId)
            .build()

        val pages = mutableListOf<Page>()
        var pageNum = 1
        while (pageNum <= 150) {
            val imgUrl = baseImgUrl.newBuilder()
                .addPathSegment("${pageNum.toString().padStart(2, '0')}.webp")
                .build()
                .toString()

            val checkReq = Request.Builder()
                .url(imgUrl)
                .head()
                .headers(headers)
                .build()

            val (isSuccess, contentType) = client.newCall(checkReq).execute().use { res ->
                res.isSuccessful to (res.header("Content-Type") ?: "")
            }

            if (isSuccess && contentType.startsWith("image")) {
                pages.add(Page(pageNum - 1, imageUrl = imgUrl))
                pageNum++
            } else {
                break
            }
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Utilities =============================

    private fun libraryRequestForManga(manga: SManga): Request {
        val seriesId = (baseUrl + manga.url).toHttpUrl().queryParameter("series")
            ?: throw Exception("Invalid manga URL")
        return GET("$baseUrl/api/library#$seriesId", headers)
    }

    private fun findEntry(response: Response): LibraryEntry {
        val seriesId = response.request.url.fragment
            ?: throw Exception("Missing series ID in request fragment")
        return response.parseAs<List<LibraryEntry>>()
            .find { it.getId() == seriesId }
            ?: throw Exception("Series not found: $seriesId")
    }
}
