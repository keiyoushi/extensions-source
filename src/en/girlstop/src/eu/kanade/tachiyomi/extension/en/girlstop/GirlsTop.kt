package eu.kanade.tachiyomi.extension.en.girlstop

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class GirlsTop : HttpSource() {

    override val name = "GirlsTop"

    override val baseUrl = "https://en.girlstop.info"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)

    // Enforce a desktop User-Agent to prevent redirects to the mobile site (me.girlstop.info)
    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/filter.php?srt=viw"
        } else {
            "$baseUrl/filter.php?srt=viw&page=${page - 1}"
        }
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return extractMangasFromDocument(document)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/index.php"
        } else {
            "$baseUrl/index.php?page=${page - 1}"
        }
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return extractMangasFromDocument(document)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("text", query)
                .build()
            return POST("$baseUrl/models.php", headers, formBody)
        }

        val sortPath = filters.firstInstanceOrNull<Filters>()?.toUriPart() ?: "filter.php?srt=viw"
        val url = if (page == 1) {
            "$baseUrl/$sortPath"
        } else {
            "$baseUrl/$sortPath&page=${page - 1}"
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return extractMangasFromDocument(document)
    }

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val url = response.request.url.toString()

        return SManga.create().apply {
            if (url.contains("models.php")) {
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
    }

    // ============================= Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        if (manga.url.contains("psto.php")) {
            return@fromCallable listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Gallery"
                    chapter_number = 1f
                },
            )
        }

        val chapters = mutableListOf<SChapter>()
        var nextUrl: String? = manga.url

        while (nextUrl != null) {
            val request = GET(baseUrl + nextUrl, headers)
            val response = client.newCall(request).execute()
            val document = response.asJsoup()

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

            nextUrl = document.selectFirst("li.next a")?.attr("href")?.let { "/$it" }
        }

        chapters
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("a.fullimg").mapIndexed { index, a ->
            Page(index, imageUrl = a.absUrl("href"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
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
            else -> dateFormat.tryParse(dateStr)
        }
    }

    companion object {
        private val TITLE_SUFFIX_REGEX = Regex(" - nude galleries.*")
    }
}
