package eu.kanade.tachiyomi.extension.ja.dokiraw

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

@Source
abstract class Dokiraw : KeiSource() {

    private val dateRegex = Regex("""(\d+)""")
    private val chapterRegex = Regex("""(\d+(?:\.\d+)?)""")

    // =============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/hot".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        val document = client.get(url).asJsoup()
        val mangaElements = document.select(POPULAR_MANGA_SELECTOR)

        val mangas = mangaElements.map { mangaFromElement(it) }
        val hasNextPage = mangaElements.size == POPULAR_PER_PAGE

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/search/manga".toHttpUrl().newBuilder()
            .addQueryParameter("status", "-1")
            .addQueryParameter("sort", "15")
            .addQueryParameter("page", page.toString())
            .build()

        val document = client.get(url).asJsoup()
        val mangaElements = document.select(POPULAR_MANGA_SELECTOR)

        val mangas = mangaElements.map { mangaFromElement(it) }
        val hasNextPage = mangaElements.size == RESULTS_PER_PAGE

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/manga".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
            }
            filters.firstInstanceOrNull<GenreFilter>()?.let { filter ->
                filter.values[filter.state]
                    .takeIf { it != "All" }
                    ?.let { addQueryParameter("genre", it) }
            }
            addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asJsoup()
        val mangaElements = document.select(POPULAR_MANGA_SELECTOR)

        val mangas = mangaElements.map { mangaFromElement(it) }
        val hasNextPage = mangaElements.size == RESULTS_PER_PAGE

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val baseHost = baseUrl.toHttpUrl().host
        if (url.host != baseHost && !url.host.endsWith(".$baseHost")) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null

        val manga = SManga.create().apply {
            this.url = "/manga/$slug"
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val anchor = element.selectFirst("a[href*=/manga/]")!!
        setUrlWithoutDomain(anchor.absUrl("href"))

        title = element.selectFirst("h3")?.text().orEmpty()

        val thumbnail = element.selectFirst("img")
        thumbnail_url = thumbnail?.absUrl("data-original")
            ?.ifEmpty { thumbnail.absUrl("src") }
    }

    // =========================== Filters ============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Search by Genre"),
        GenreFilter(),
    )

    // ====================== Manga Details & Chapters ======================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document, manga),
            chapters = chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = manga.apply {
        title = document.selectFirst("div[class*=manga-detail_boxInfo] h1")?.text() ?: manga.title
        description = document.select("div.work-break p").lastOrNull()?.text()
        genre = document.select("tr:contains(ジャンル) a[href*=genre]").eachText().joinToString()

        status = when {
            document.selectFirst("div:contains(連載中)") != null -> SManga.ONGOING
            document.selectFirst("div:contains(完結)") != null -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        thumbnail_url = document.selectFirst("img[src*=cover]")?.absUrl("src")
            ?: manga.thumbnail_url

        initialized = true
    }

    private fun chapterListParse(document: Document): List<SChapter> = document.select("a:has(div[class*=manga-detail_chapter])").map { chapterFromElement(it) }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val container = element.selectFirst("div[class*=manga-detail_chapter]")!!
        val nameAndDate = container.select("span")

        name = nameAndDate.first()!!.text()
        chapter_number = chapterRegex.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f

        setUrlWithoutDomain(element.absUrl("href"))

        val dateText = nameAndDate.getOrNull(1)?.text()
        date_upload = parseDate(dateText)
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.page-chapter img").mapIndexed { index, element ->
            val finalUrl = element.absUrl("data-cdn")
                .ifEmpty { element.absUrl("data-original") }
                .ifEmpty { element.absUrl("src") }

            Page(index, imageUrl = finalUrl)
        }
    }

    // ============================= Utilities ==============================

    private fun parseDate(dateStr: String?): Long {
        dateStr ?: return 0L

        val now = Calendar.getInstance()

        if ("昨日" in dateStr) {
            now.add(Calendar.DAY_OF_MONTH, -1)
            return now.timeInMillis
        }

        val amount = dateRegex.find(dateStr)?.groupValues?.get(1)?.toIntOrNull() ?: return 0L

        when {
            "秒" in dateStr -> now.add(Calendar.SECOND, -amount)
            "分" in dateStr -> now.add(Calendar.MINUTE, -amount)
            "時" in dateStr -> now.add(Calendar.HOUR, -amount)
            "日" in dateStr -> now.add(Calendar.DAY_OF_MONTH, -amount)
            "週" in dateStr -> now.add(Calendar.DAY_OF_MONTH, -amount * 7)
            "月" in dateStr -> now.add(Calendar.MONTH, -amount)
            "年" in dateStr -> now.add(Calendar.YEAR, -amount)
            else -> return 0L
        }
        return now.timeInMillis
    }

    companion object {
        private const val POPULAR_MANGA_SELECTOR = "div[class*=manga-item_item]"
        private const val RESULTS_PER_PAGE = 20
        private const val POPULAR_PER_PAGE = 36
    }
}
