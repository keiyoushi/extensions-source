package eu.kanade.tachiyomi.extension.fr.furyosquad

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class FuryoSquad : HttpSource() {

    override val name = "FuryoSquad"

    override val baseUrl = "https://www.furyosociety.com"

    override val lang = "fr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder()

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/mangas", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div#fs-tous div.fs-card-body").mapNotNull { element ->
            val titleElement = element.selectFirst("span.fs-comic-title a") ?: return@mapNotNull null
            SManga.create().apply {
                val rawUrl = element.selectFirst("div.fs-card-img-container a")?.attr("href") ?: return@mapNotNull null
                url = rawUrl.toHttpUrlOrNull()?.encodedPath ?: rawUrl
                title = titleElement.text()
                thumbnail_url = element.selectFirst("div.fs-card-img-container img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("table.table-striped tr").mapNotNull { element ->
            val titleElement = element.selectFirst("span.fs-comic-title a") ?: return@mapNotNull null
            SManga.create().apply {
                val rawUrl = titleElement.attr("href")
                url = rawUrl.toHttpUrlOrNull()?.encodedPath ?: rawUrl
                title = titleElement.text()
                thumbnail_url = element.selectFirst("img.fs-chap-img")?.attr("abs:src")
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    // ========================= Search =========================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_ID_SEARCH)) {
            val path = query.removePrefix(PREFIX_ID_SEARCH)
            val mangaPath = if (path.startsWith("/")) path else "/$path"
            return fetchMangaByPath(mangaPath)
        }

        if (query.startsWith("http")) {
            val url = query.toHttpUrlOrNull()
            if (url != null && url.host.contains("furyosociety.com")) {
                if (url.pathSegments.contains("series")) {
                    return fetchMangaByPath(url.encodedPath)
                }
                if (url.pathSegments.contains("read")) {
                    val readIndex = url.pathSegments.indexOf("read")
                    if (readIndex != -1 && readIndex + 1 < url.pathSegments.size) {
                        val mangaSlug = url.pathSegments[readIndex + 1]
                        return fetchMangaByPath("/series/$mangaSlug/")
                    }
                }
            }
        }

        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                val mangasPage = popularMangaParse(response)
                val filteredMangas = mangasPage.mangas.filter { it.title.contains(query, ignoreCase = true) }
                MangasPage(filteredMangas, false)
            }
    }

    private fun fetchMangaByPath(path: String): Observable<MangasPage> = client.newCall(GET("$baseUrl$path", headers))
        .asObservableSuccess()
        .map { response ->
            val manga = mangaDetailsParse(response).apply {
                url = path
            }
            MangasPage(listOf(manga), false)
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(1)

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ========================= Details =========================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.fs-comic-title")?.text() ?: ""
            val info = document.selectFirst("div.comic-info") ?: return@apply
            info.select("p.fs-comic-label").forEach { el ->
                when (el.text().lowercase(Locale.ROOT)) {
                    "scénario" -> author = el.nextElementSibling()?.text()
                    "dessins" -> artist = el.nextElementSibling()?.text()
                    "genre" -> genre = el.nextElementSibling()?.text()
                }
            }
            description = info.selectFirst("div.fs-comic-description")?.text()
            thumbnail_url = info.selectFirst("img.comic-cover")?.attr("abs:src")
        }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // ========================= Chapters =========================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.fs-chapter-list div.element").map { element ->
            SChapter.create().apply {
                val titleElement = element.selectFirst("div.title a")!!
                val rawUrl = titleElement.attr("href")
                url = rawUrl.toHttpUrlOrNull()?.encodedPath ?: rawUrl
                name = titleElement.attr("title")
                date_upload = parseChapterDate(element.selectFirst("div.meta_r")?.text() ?: "")
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ========================= Pages =========================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.fs-read img[id]").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()

    // ========================= Utilities =========================

    private fun parseChapterDate(date: String): Long {
        val lcDate = date.lowercase(Locale.ROOT)
        if (lcDate.startsWith("il y a")) {
            return parseRelativeDate(lcDate)
        }

        return when {
            lcDate.startsWith("avant-hier") -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -2)
                    setMidnight()
                }.timeInMillis
            }

            lcDate.startsWith("hier") -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -1)
                    setMidnight()
                }.timeInMillis
            }

            lcDate.startsWith("aujourd'hui") -> {
                Calendar.getInstance().apply {
                    setMidnight()
                }.timeInMillis
            }

            else -> {
                val dateText = DATE_EXTRACT_REGEX.find(date)?.groupValues?.get(1) ?: date
                DATE_FORMAT.tryParse(dateText)
            }
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val match = RELATIVE_DATE_REGEX.find(date) ?: return 0L
        val value = match.groupValues[1].toIntOrNull() ?: return 0L
        val unit = match.groupValues[2]

        return Calendar.getInstance().apply {
            when (unit) {
                "minute", "minutes" -> add(Calendar.MINUTE, -value)
                "heure", "heures" -> add(Calendar.HOUR_OF_DAY, -value)
                "jour", "jours" -> add(Calendar.DATE, -value)
                "semaine", "semaines" -> add(Calendar.DATE, -value * 7)
                "mois" -> add(Calendar.MONTH, -value)
                "an", "ans", "année", "années" -> add(Calendar.YEAR, -value)
            }
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun Calendar.setMidnight() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.FRENCH)
        private val RELATIVE_DATE_REGEX = Regex("""il y a (\d+) (\w+)""")
        private val DATE_EXTRACT_REGEX = Regex("""le (.*)""")
    }
}
