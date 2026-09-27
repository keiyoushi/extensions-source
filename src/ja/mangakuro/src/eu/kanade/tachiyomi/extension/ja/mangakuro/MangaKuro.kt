package eu.kanade.tachiyomi.extension.ja.mangakuro

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

@Source
abstract class MangaKuro : KeiSource() {

    private val chapterIdRegex = Regex("""CHAPTER_ID\s*=\s*(\d+);""")
    private val dateRegex = Regex("""(\d+)""")

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse("all-manga", page, "sort", "views")

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse("all-manga", page, "sort", "latest-updated")

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = mangaListParse("search", page, "keyword", query)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val baseHost = baseUrl.toHttpUrl().host
        if (url.host != baseHost && !url.host.endsWith(".$baseHost")) return null
        if (url.pathSegments.firstOrNull() != "manga") return null

        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val mangaUrl = "/manga/$slug"

        return mangaDetailsParse(client.get(baseUrl + mangaUrl).asJsoup(), SManga.create())
            .apply { this.url = mangaUrl }
    }

    private suspend fun mangaListParse(path: String, page: Int, param: String, value: String): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(path)
            .addPathSegment(page.toString())
            .addQueryParameter(param, value)
            .build()

        val document = client.get(url).asJsoup()
        val mangas = document.select("div.story_item").mapNotNull { element ->
            val anchor = element.selectFirst("div.mg_name a") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(anchor.absUrl("href"))
                title = anchor.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a[title='Last Page']") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            manga = mangaDetailsParse(document, manga),
            chapters = document.select("div.chapter_box .item").map(::chapterFromElement),
        )
    }

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = manga.apply {
        title = document.selectFirst("div.detail_name > h1")?.text() ?: title
        author = document.selectFirst("div:has(.lnr-user) + .info_value")
            ?.text()
            ?.takeUnless { it == "未詳" }
        status = parseStatus(document.selectFirst("div:has(.lnr-leaf) + .info_value")?.text())
        description = document.selectFirst(".detail_reviewContent")?.text()
        thumbnail_url = document.selectFirst(".detail_avatar img")?.absUrl("src") ?: thumbnail_url
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("進行中") -> SManga.ONGOING
        status.contains("完了") -> SManga.COMPLETED
        status.contains("保留") -> SManga.ON_HIATUS
        status.contains("キャンセル") -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val anchor = element.selectFirst("a.chapter_num")!!
        setUrlWithoutDomain(anchor.absUrl("href"))
        name = anchor.text().removePrefix("#").trim()

        date_upload = parseDate(element.selectFirst("p.chapter_info:nth-of-type(2)")?.text())
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl).asJsoup()

        val chapterId = document.select("script:containsData(CHAPTER_ID)")
            .firstNotNullOfOrNull { chapterIdRegex.find(it.data())?.groupValues?.get(1) }
            ?: throw Exception("Failed to find chapter id")

        val pageHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .set("Referer", chapterUrl)
            .build()

        val response = client.get("$baseUrl/ajax/image/list/chap/$chapterId", pageHeaders)
            .parseAs<PageListResponseDto>()

        if (!response.status) {
            throw Exception(response.msg ?: "Unknown error")
        }

        return Jsoup.parseBodyFragment(response.html, chapterUrl)
            .select("div.image_story img")
            .mapIndexed { index, element -> Page(index, imageUrl = element.absUrl("src")) }
    }

    // ============================= Utilities ==============================

    private fun parseDate(dateText: String?): Long {
        dateText ?: return 0L

        val now = Calendar.getInstance()

        if ("昨日" in dateText) {
            now.add(Calendar.DAY_OF_MONTH, -1)
            return now.timeInMillis
        }

        val amount = dateRegex.find(dateText)?.groupValues?.get(1)?.toIntOrNull() ?: return 0L

        when {
            "秒" in dateText -> now.add(Calendar.SECOND, -amount)
            "分" in dateText -> now.add(Calendar.MINUTE, -amount)
            "時" in dateText -> now.add(Calendar.HOUR, -amount)
            "日" in dateText -> now.add(Calendar.DAY_OF_MONTH, -amount)
            "週" in dateText -> now.add(Calendar.DAY_OF_MONTH, -amount * 7)
            "月" in dateText -> now.add(Calendar.MONTH, -amount)
            "年" in dateText -> now.add(Calendar.YEAR, -amount)
            else -> return 0L
        }

        return now.timeInMillis
    }
}
