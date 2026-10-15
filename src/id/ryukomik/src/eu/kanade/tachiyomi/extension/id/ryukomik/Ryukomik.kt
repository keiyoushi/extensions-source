package eu.kanade.tachiyomi.extension.id.ryukomik

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Source
abstract class Ryukomik : KeiSource() {

    private val apiUrl = "https://api.ryukomik.web.id"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/komiku/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        val response = client.get(url).parseAs<ListResponseDto>()
        val mangas = response.data.mapNotNull { it.toSManga("komiku") }
        val hasNextPage = response.meta?.let { it.currentPage < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = parseCoverCards(document)
        return MangasPage(mangas, false)
    }

    private fun parseCoverCards(document: Document): List<SManga> {
        val seen = mutableSetOf<String>()
        return document.select("a.rk-cover-card[href^='/komik/']").mapNotNull { card ->
            val href = card.attr("abs:href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!seen.add(href)) return@mapNotNull null

            val img = card.selectFirst("img")
            val title = img?.attr("alt")?.takeIf { it.isNotBlank() }
                ?: card.selectFirst("p")?.text()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            SManga.create().apply {
                this.title = title
                thumbnail_url = img?.attr("abs:src")
                setUrlWithoutDomain(href)
            }
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val typeFilter = filters.filterIsInstance<TypeFilter>().firstOrNull()
        val typeValue = typeFilter?.let { TYPE_OPTIONS[it.state].second }.orEmpty()

        val orderFilter = filters.filterIsInstance<OrderByFilter>().firstOrNull()
        val orderValue = orderFilter?.let { ORDER_OPTIONS[it.state].second }.orEmpty()

        val letterFilter = filters.filterIsInstance<LetterFilter>().firstOrNull()
        val letterValue = letterFilter?.let { LETTER_OPTIONS[it.state].second }.orEmpty()

        val statusFilter = filters.filterIsInstance<StatusFilter>().firstOrNull()
        val statusValue = statusFilter?.let { STATUS_OPTIONS[it.state].second }.orEmpty()

        val genreFilter = filters.filterIsInstance<GenreFilter>().firstOrNull()
        val genreValue = genreFilter?.let { GENRE_OPTIONS[it.state].second }.orEmpty()

        if (query.isNotBlank()) {
            val sourcesToSearch = listOf("project", "komiku", "kiryuu", "komikid", "josei")
            val results = coroutineScope {
                sourcesToSearch.map { src ->
                    async { searchSource(src, query) }
                }.awaitAll().flatten().distinctBy { it.url }
            }
            return MangasPage(results, false)
        }

        if (genreValue.isNotBlank()) {
            if (page > 1) {
                return MangasPage(emptyList(), false)
            }
            val document = client.get("$baseUrl/genre/$genreValue".toHttpUrl()).asJsoup()
            val mangas = parseCoverCards(document)
            return MangasPage(mangas, false)
        }

        val urlBuilder = "$apiUrl/komiku/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (typeValue.isNotBlank()) urlBuilder.addQueryParameter("tipe", typeValue)
        if (orderValue.isNotBlank()) urlBuilder.addQueryParameter("orderby", orderValue)
        if (letterValue.isNotBlank()) urlBuilder.addQueryParameter("huruf", letterValue)
        if (statusValue.isNotBlank()) urlBuilder.addQueryParameter("status", statusValue)

        val response = client.get(urlBuilder.build()).parseAs<ListResponseDto>()
        val mangas = response.data.mapNotNull { it.toSManga("komiku") }
        val hasNextPage = response.meta?.let { it.currentPage < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    private suspend fun searchSource(source: String, query: String): List<SManga> {
        val url = if (source == "project") {
            "$baseUrl/api/project/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
        } else {
            "$apiUrl/$source/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
        }

        val response = client.get(url).parseAs<ListResponseDto>()
        return response.data.mapNotNull { it.toSManga(source) }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = runCatching {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.size < 3 || url.pathSegments[0] != "komik") return null
        mangaDetailsParse(client.get(url).asJsoup())
    }.getOrNull()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(doc), chapterListParse(doc))
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(document.location())
        title = document.selectFirst("h1")!!.text().trim()
        thumbnail_url = document.selectFirst("div.rk-shell img[alt=Poster], div.rk-shell div.flex-shrink-0 img")?.attr("abs:src")
        author = document.selectFirst("div.rk-shell span.text-white\\/60.truncate")?.text()?.trim()?.takeUnless { it == "-" }

        genre = buildList {
            document.selectFirst("span.rk-chip")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            document.select("a[href*='/genre/']").forEach {
                val g = it.text().trim()
                if (g.isNotBlank() && !contains(g)) add(g)
            }
        }.joinToString()

        description = document.selectFirst("p.text-sm.leading-relaxed.text-white\\/70")?.text()?.trim()

        val statusText = document.selectFirst("div.rk-shell div.flex.gap-3 span:last-child")?.text()
        status = parseStatus(statusText)
        initialized = true
    }

    private fun chapterListParse(document: Document): List<SChapter> = document.select("a[href*='/chapter/']").map { el ->
        SChapter.create().apply {
            setUrlWithoutDomain(el.attr("abs:href"))
            name = el.selectFirst("span.text-\\[13px\\], span.font-medium")?.text()?.trim()
                ?: el.selectFirst("div > div > span")?.text()?.trim()
                ?: el.text().trim()
            val dateStr = el.selectFirst("span.text-\\[11px\\] > span:first-child")?.text()
            if (!dateStr.isNullOrBlank()) {
                date_upload = parseDate(dateStr)
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val html = client.get(chapterUrl).body.string()

        val imagesMatch = IMAGES_REGEX.find(html)?.groupValues?.get(1) ?: return emptyList()

        val imagesJson = imagesMatch
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

        val images = imagesJson.parseAs<List<String>>()

        return images.mapIndexed { index, imgUrl ->
            val fullUrl = if (imgUrl.startsWith("http://") || imgUrl.startsWith("https://")) {
                imgUrl
            } else {
                "${baseUrl.trimEnd('/')}/${imgUrl.trimStart('/')}"
            }
            Page(index, imageUrl = fullUrl)
        }
    }

    private fun parseDate(dateStr: String): Long {
        val cleanDate = dateStr.trim().lowercase(Locale.ROOT)
        return when {
            cleanDate.contains("detik") -> {
                val num = cleanDate.substringBefore("detik").trim().toIntOrNull() ?: 1
                Calendar.getInstance().apply { add(Calendar.SECOND, -num) }.timeInMillis
            }
            cleanDate.contains("menit") -> {
                val num = cleanDate.substringBefore("menit").trim().toIntOrNull() ?: 1
                Calendar.getInstance().apply { add(Calendar.MINUTE, -num) }.timeInMillis
            }
            cleanDate.contains("jam") -> {
                val num = cleanDate.substringBefore("jam").trim().toIntOrNull() ?: 1
                Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -num) }.timeInMillis
            }
            cleanDate.contains("hari") -> {
                val num = cleanDate.substringBefore("hari").trim().toIntOrNull() ?: 1
                Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -num) }.timeInMillis
            }
            cleanDate.contains("minggu") -> {
                val num = cleanDate.substringBefore("minggu").trim().toIntOrNull() ?: 1
                Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, -num) }.timeInMillis
            }
            cleanDate.contains("bulan") -> {
                val num = cleanDate.substringBefore("bulan").trim().toIntOrNull() ?: 1
                Calendar.getInstance().apply { add(Calendar.MONTH, -num) }.timeInMillis
            }
            cleanDate.contains("tahun") -> {
                val num = cleanDate.substringBefore("tahun").trim().toIntOrNull() ?: 1
                Calendar.getInstance().apply { add(Calendar.YEAR, -num) }.timeInMillis
            }
            cleanDate.contains("/") -> {
                DATE_FORMATTER.tryParseDate(cleanDate)
            }
            else -> 0L
        }
    }

    private fun parseStatus(statusStr: String?): Int = when (statusStr?.trim()?.lowercase(Locale.ROOT)) {
        "ongoing", "berjalan" -> SManga.ONGOING
        "completed", "tamat", "complete" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(TYPE_OPTIONS.map { it.first }.toTypedArray()),
        StatusFilter(STATUS_OPTIONS.map { it.first }.toTypedArray()),
        OrderByFilter(ORDER_OPTIONS.map { it.first }.toTypedArray()),
        LetterFilter(LETTER_OPTIONS.map { it.first }.toTypedArray()),
        Filter.Separator(),
        GenreFilter(GENRE_OPTIONS.map { it.first }.toTypedArray()),
    )

    companion object {
        private val IMAGES_REGEX = Regex("""\\?"images\\?"\s*:\s*(\[[^\]]+\])""")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    }
}
