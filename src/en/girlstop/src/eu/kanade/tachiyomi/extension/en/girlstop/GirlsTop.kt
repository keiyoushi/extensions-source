package eu.kanade.tachiyomi.extension.en.girlstop

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class GirlsTop : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    // Enforce a desktop User-Agent to prevent redirects to the mobile site (me.girlstop.info)
    override fun Headers.Builder.configureHeaders() = set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36")

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page == 1) {
            "$baseUrl/filter.php?srt=viw"
        } else {
            "$baseUrl/filter.php?srt=viw&page=${page - 1}"
        }
        return extractMangasFromDocument(client.get(url).asJsoup())
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) {
            "$baseUrl/index.php"
        } else {
            "$baseUrl/index.php?page=${page - 1}"
        }
        return extractMangasFromDocument(client.get(url).asJsoup())
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("text", query)
                .build()
            return extractMangasFromDocument(client.post("$baseUrl/models.php", formBody).asJsoup())
        }

        val sortPath = filters.firstInstanceOrNull<Filters>()?.toUriPart() ?: "filter.php?srt=viw"
        val url = if (page == 1) {
            "$baseUrl/$sortPath"
        } else {
            "$baseUrl/$sortPath&page=${page - 1}"
        }
        return extractMangasFromDocument(client.get(url).asJsoup())
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (manga.url.contains("psto.php")) {
            val details = if (fetchDetails) mangaDetailsParse(client.get(getMangaUrl(manga))) else manga
            val gallery = SChapter.create().apply {
                url = manga.url
                name = "Gallery"
                chapter_number = 1f
            }
            return SMangaUpdate(details, listOf(gallery))
        }

        val response = client.get(getMangaUrl(manga))
        val isModel = response.request.url.toString().contains("models.php")
        val document = response.asJsoup()
        val details = mangaDetailsParse(document, isModel)
        val chapterList = if (fetchChapters) fetchChapterList(document) else chapters

        return SMangaUpdate(details, chapterList)
    }

    private fun mangaDetailsParse(response: Response): SManga {
        val isModel = response.request.url.toString().contains("models.php")
        return mangaDetailsParse(response.asJsoup(), isModel)
    }

    private fun mangaDetailsParse(document: Document, isModel: Boolean): SManga = SManga.create().apply {
        if (isModel) {
            title = document.selectFirst("h1.index")?.text()?.replace(TITLE_SUFFIX_REGEX, "")?.trim()!!
            description = document.selectFirst("#modeldesc")?.text()
            thumbnail_url = document.selectFirst(".model-cover img")?.absUrl("src")
            status = SManga.ONGOING
        } else {
            title = document.selectFirst("h1")?.text()!!
            author = document.selectFirst(".ps-desc a[href*='user.php']")?.text()
            genre = document.select(".ps-tags a").joinToString(", ") { it.text() }
            description = buildString {
                document.select(".ps-desc").not(".ps-tags").forEach {
                    append(it.text()).append("\n")
                }
            }.trim()
            thumbnail_url = document.selectFirst(".tiles-wrap img")?.absUrl("src")
            status = SManga.COMPLETED
        }
    }

    // ============================= Chapters ==============================

    private suspend fun fetchChapterList(firstPage: Document): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = firstPage

        while (true) {
            chapters += document.select(".thumbs .thumb").map { element ->
                SChapter.create().apply {
                    val a = element.selectFirst(".post_title a")!!
                    setUrlWithoutDomain(a.absUrl("href"))
                    name = a.text()

                    val dateRow = element.select("tr").firstOrNull {
                        it.selectFirst("td")?.text()?.contains("Approved") == true
                    }
                    val dateStr = dateRow?.select("td")?.last()?.text()
                    date_upload = parseDate(dateStr)
                }
            }

            val nextUrl = document.selectFirst("li.next a")?.attr("href") ?: break
            document = client.get("$baseUrl/$nextUrl").asJsoup()
        }

        return chapters
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("a.fullimg").mapIndexed { index, a ->
            Page(index, imageUrl = a.absUrl("href"))
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Search query ignores filters"),
        Filter.Separator(),
        Filters(),
    )

    // ============================= Utilities =============================

    private fun extractMangasFromDocument(document: Document): MangasPage {
        val mangas = document.select(".thumbs .thumb").map { element ->
            SManga.create().apply {
                val a = element.selectFirst(".post_title a")!!
                title = a.text()
                setUrlWithoutDomain(a.absUrl("href"))
                thumbnail_url = element.selectFirst("picture img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("li.next a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        val lower = dateStr.lowercase()
        return when {
            lower.contains("today") || lower.contains("just now") || lower.contains("recently") -> System.currentTimeMillis()
            lower.contains("yesterday") -> System.currentTimeMillis() - 86400000L
            else -> dateFormat.tryParseDate(dateStr)
        }
    }

    companion object {
        private val TITLE_SUFFIX_REGEX = Regex(" - nude galleries.*")
    }
}
