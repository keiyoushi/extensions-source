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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "https://api.ryukomik.web.id/komiku/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        val response = client.get(url).parseAs<ListResponseDto>()
        val mangas = response.data.mapNotNull { it.toSManga("komiku") }
        val hasNextPage = response.meta?.let { it.currentPage < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page > 1) {
            return MangasPage(emptyList(), false)
        }

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

        val urlBuilder = "https://api.ryukomik.web.id/komiku/list".toHttpUrl().newBuilder()
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
            "https://api.ryukomik.web.id/$source/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
        }

        return runCatching {
            val response = client.get(url).parseAs<ListResponseDto>()
            response.data.mapNotNull { it.toSManga(source) }
        }.getOrDefault(emptyList())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.size < 3 || url.pathSegments[0] != "komik") return null
        return mangaDetailsParse(client.get(url).asJsoup())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        val details = if (fetchDetails) mangaDetailsParse(doc) else manga
        val chapterList = if (fetchChapters) chapterListParse(doc) else chapters
        return SMangaUpdate(details, chapterList)
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

        val imagesMatch = IMAGES_REGEX.find(html)?.groupValues?.get(1)
            ?: throw Exception("Gagal menemukan daftar gambar chapter")

        val imagesJson = imagesMatch
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

        val images = imagesJson.parseAs<List<String>>()
        if (images.isEmpty()) {
            throw Exception("Chapter tidak memiliki gambar")
        }

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

    class TypeFilter(values: Array<String>) : Filter.Select<String>("Tipe Komik", values)
    class StatusFilter(values: Array<String>) : Filter.Select<String>("Status", values)
    class OrderByFilter(values: Array<String>) : Filter.Select<String>("Urutkan Berdasarkan", values)
    class LetterFilter(values: Array<String>) : Filter.Select<String>("Huruf Awalan", values)
    class GenreFilter(values: Array<String>) : Filter.Select<String>("Genre", values)

    companion object {

        private val TYPE_OPTIONS = arrayOf(
            "Semua" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
        )

        private val STATUS_OPTIONS = arrayOf(
            "Semua" to "",
            "Ongoing (Berjalan)" to "ongoing",
            "Completed (Tamat)" to "end",
        )

        private val ORDER_OPTIONS = arrayOf(
            "Default" to "",
            "Chapter Terbaru" to "modified",
            "Komik Terbaru" to "date",
            "Acak" to "rand",
        )

        private val LETTER_OPTIONS = arrayOf(
            "Semua" to "",
            "#" to "%23",
            "A" to "A",
            "B" to "B",
            "C" to "C",
            "D" to "D",
            "E" to "E",
            "F" to "F",
            "G" to "G",
            "H" to "H",
            "I" to "I",
            "J" to "J",
            "K" to "K",
            "L" to "L",
            "M" to "M",
            "N" to "N",
            "O" to "O",
            "P" to "P",
            "Q" to "Q",
            "R" to "R",
            "S" to "S",
            "T" to "T",
            "U" to "U",
            "V" to "V",
            "W" to "W",
            "X" to "X",
            "Y" to "Y",
            "Z" to "Z",
        )

        private val GENRE_OPTIONS = arrayOf(
            "Semua" to "",
            "Action" to "action",
            "Adventure" to "adventure",
            "Boys' Love" to "boys'-love",
            "Comedy" to "comedy",
            "Crime" to "crime",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Fantasy" to "fantasy",
            "Girls' Love" to "girls'-love",
            "Harem" to "harem",
            "Historical" to "historical",
            "Horror" to "horror",
            "Isekai" to "isekai",
            "Josei" to "josei",
            "Magical Girls" to "magical-girls",
            "Martial Arts" to "martial-arts",
            "Mecha" to "mecha",
            "Medical" to "medical",
            "Music" to "music",
            "Mystery" to "mystery",
            "Philosophical" to "philosophical",
            "Psychological" to "psychological",
            "Romance" to "romance",
            "School Life" to "school-life",
            "Sci-Fi" to "sci-fi",
            "Seinen" to "seinen",
            "Shoujo" to "shoujo",
            "Shoujo Ai" to "shoujo-ai",
            "Shounen" to "shounen",
            "Shounen Ai" to "shounen-ai",
            "Slice of Life" to "slice-of-life",
            "Sports" to "sports",
            "Superhero" to "superhero",
            "Supernatural" to "supernatural",
            "Thriller" to "thriller",
            "Tragedy" to "tragedy",
            "Wuxia" to "wuxia",
            "Yuri" to "yuri",
        )

        private val IMAGES_REGEX = Regex("""\\?"images\\?"\s*:\s*(\[[^\]]+\])""")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    }
}

@Serializable
class ListResponseDto(
    val data: List<ItemDto> = emptyList(),
    val meta: MetaDto? = null,
)

@Serializable
class ItemDto(
    val title: String? = null,
    val slug: String? = null,
    @SerialName("detail_link")
    val detailLink: String? = null,
    val link: String? = null,
    val image: String? = null,
    @SerialName("cover_url")
    val coverUrl: String? = null,
    val source: String? = null,
    val type: String? = null,
    val status: String? = null,
) {
    fun toSManga(defaultSource: String): SManga? {
        val s = slug?.takeIf { it.isNotBlank() }
            ?: detailLink?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: link?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: return null
        val mangaSource = source?.takeIf { it.isNotBlank() } ?: defaultSource
        val mangaTitle = title?.takeIf { it.isNotBlank() } ?: return null
        val thumb = image ?: coverUrl

        return SManga.create().apply {
            this.title = mangaTitle
            thumbnail_url = thumb
            url = "/komik/$mangaSource/$s"
        }
    }
}

@Serializable
class MetaDto(
    val currentPage: Int = 1,
    val totalPages: Int = 1,
)
