package eu.kanade.tachiyomi.extension.ru.mangashi

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
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Source
abstract class MangaShi : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(2)
    }

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest(page, "popular")

    // ========================= Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest(page, "updated")

    // ========================= Search =========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = makeCatalogRequest(page, "", query, filters)

    // ============================== Search Utilities ===============================
    private suspend fun makeCatalogRequest(page: Int, sortBy: String = "", query: String = "", filters: FilterList? = null): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("catalog")
            addPathSegment("")

            filters?.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.selected)
                    is StatusFilter -> addQueryParameter("status", filter.selected)
                    is TypeFilter -> addQueryParameter("type", filter.selected)
                    is AgeRatingFilter -> addQueryParameter("age_rating", filter.selected)
                    is YearFilter -> addQueryParameter("year", filter.selected)
                    is GenreFilter -> {
                        filter.included?.forEach { addQueryParameter("tag", it) }
                        filter.excluded?.forEach { addQueryParameter("exclude_tag", it) }
                    }
                    else -> {}
                }
            }

            if (filters == null) {
                addQueryParameter("sort", sortBy)
                addQueryParameter("status", "")
                addQueryParameter("type", "")
                addQueryParameter("age_rating", "")
                addQueryParameter("year", "")
            }

            addQueryParameter("chapters_min", "")
            addQueryParameter("chapters_max", "")
            addQueryParameter("q", query)
            addQueryParameter("page", page.toString())
        }.build()

        client.get(url).use {
            return catalogParse(it.asJsoup())
        }
    }

    // ========================= Filters =========================
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val result = client.get("$baseUrl/catalog/").asJsoup()

        return result.selectFirst(".genre-flyout")?.select(".group")?.map {
            it.selectFirst("span")?.text() to it.selectFirst("input")?.attr("value")
        }?.ifEmpty { emptyList() }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()

        filters.add(SortFilter())

        data?.parseAs<List<Pair<String, String>>>().let {
            if (it?.isNotEmpty() == true) filters.add(GenreFilter(it))
        }

        filters.addAll(
            listOf(
                StatusFilter(),
                TypeFilter(),
                AgeRatingFilter(),
                YearFilter(),
            ),
        )

        return FilterList(filters)
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "manga") {
            val tmpManga = SManga.create().apply {
                this.url = "/${url.pathSegments[0]}/${url.pathSegments[1]}/"
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // ========================= Manga =========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val newUrl = manga.url
        val document = client.get("${baseUrl}$newUrl").asJsoup()

        val mangaNew = parseMangaDetails(document, newUrl)

        val chaptersNew = if (fetchChapters) {
            parseChapterList(document)
        } else {
            chapters
        }

        return SMangaUpdate(mangaNew, chaptersNew)
    }

    // ========================= Manga Details =========================
    private fun parseMangaDetails(document: Document, newUrl: String): SManga = SManga.create().apply {
        url = newUrl
        title = document.selectFirst("h1")!!.text()

        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?.let { if (it.startsWith("/")) "$baseUrl$it" else it }

        val authorLinks = document.select("a[href*=\"?author=\"]")
        author = authorLinks.firstOrNull()?.text()?.takeIf(String::isNotEmpty)

        val badges = document.select("span.tracking-widest").map { it.text().trim() }
        val statusText = badges.firstOrNull { txt ->
            val lower = txt.lowercase()
            lower.contains("онгоинг") || lower.contains("выпускается") || lower.contains("заверш") || lower.contains("заморож") || lower.contains("приостановл") || lower.contains("заброш") || lower.contains("хиатус")
        }

        status = parseStatus(statusText)

        val typeText = badges.firstOrNull { txt ->
            val lower = txt.lowercase()
            lower.contains("манга") || lower.contains("манхва") || lower.contains("маньхуа") || lower.contains("комикс")
        }

        val genreLinks = document.select("a[href*=manga-genre]").eachText()
        val tagLinks = document.select("a[href*=\"?tag=\"]").eachText()
        genre = (listOfNotNull(typeText) + genreLinks + tagLinks)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString { it.removePrefix("#").lowercase(Locale.ROOT) }

        description = Jsoup.parseBodyFragment(
            document.selectFirst("div.leading-relaxed[x-ref*=\"descDesktop\"]")
                ?.wholeText()?.trim().toString(),
        ).text()
    }

    private fun parseStatus(raw: String?): Int {
        val txt = raw?.lowercase() ?: return SManga.UNKNOWN
        return when {
            txt.contains("онгоинг") || txt.contains("выпускается") || txt.contains("продолжается") -> SManga.ONGOING
            txt.contains("завершен") || txt.contains("завершён") || txt.contains("завершена") -> SManga.COMPLETED
            txt.contains("заморож") || txt.contains("приостановл") || txt.contains("хиатус") -> SManga.ON_HIATUS
            txt.contains("заброш") -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // ========================= Chapters =========================
    private suspend fun parseChapterList(document: Document): List<SChapter> {
        val chapters = document.select("#chapters-list > a[href*=\"/glava\"]")
            .mapNotNull(::parseChapter)
            .toMutableList()

        var nextUrl = document.selectFirst("#chapters-load-more a")
            ?.attr("hx-get")

        val chapUrl = "$baseUrl${nextUrl?.substringBefore("?")}?"
        nextUrl = nextUrl?.substringAfter("?")

        while (nextUrl != null) {
            client.get("${chapUrl}$nextUrl").use { nextResponse ->
                val fragment = Jsoup.parseBodyFragment(nextResponse.body.string(), baseUrl)
                chapters.addAll(fragment.select("a[href*=\"/glava\"]").mapNotNull(::parseChapter))
                nextUrl = fragment.selectFirst("nav.flex a:last-child:contains(Дальше)")
                    ?.attr("href")?.substringAfter("?")
            }
        }

        return chapters
    }

    private fun parseChapter(link: Element): SChapter? {
        val href = link.attr("href")
        if (!CHAPTER_URL_REGEX.containsMatchIn(href)) return null
        if (link.selectFirst("span.chapter-title") == null) return null
        return SChapter.create().apply {
            setUrlWithoutDomain(
                if (href.startsWith("/")) "$baseUrl$href" else link.absUrl("href"),
            )
            name = link.select("span.chapter-title > span").text().trim()

            val dateText = link.select("span span").eachText()
                .lastOrNull { ABSOLUTE_DATE_REGEX.containsMatchIn(it) || RELATIVE_DATE_REGEX.containsMatchIn(it) }
            date_upload = parseChapterDate(dateText?.trim())
            chapter_number = CHAPTER_NUMBER_REGEX.find(href)
                ?.groupValues?.get(1)
                ?.replace(",", ".")
                ?.toFloatOrNull() ?: -1f
        }
    }

    private fun parseChapterDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        val trimmed = dateStr.trim()
        if (ABSOLUTE_DATE_REGEX.matches(trimmed)) {
            return dateFormat.tryParseDate(trimmed)
        }
        val lower = trimmed.lowercase()
        val amount = RELATIVE_NUMBER_REGEX.find(lower)?.groupValues?.get(1)?.toIntOrNull()
        if (amount != null) {
            val cal = Calendar.getInstance()
            when {
                "сек" in lower -> cal.add(Calendar.SECOND, -amount)
                "мин" in lower -> cal.add(Calendar.MINUTE, -amount)
                "час" in lower -> cal.add(Calendar.HOUR_OF_DAY, -amount)
                "дн" in lower || "день" in lower || "дня" in lower -> cal.add(Calendar.DAY_OF_YEAR, -amount)
                "недел" in lower -> cal.add(Calendar.WEEK_OF_YEAR, -amount)
                "месяц" in lower -> cal.add(Calendar.MONTH, -amount)
                "год" in lower || "лет" in lower -> cal.add(Calendar.YEAR, -amount)
                else -> return 0L
            }
            return cal.timeInMillis
        }
        return 0L
    }

    // ========================= Pages =========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        return document.select("img.reader-image").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    private fun catalogParse(document: Document): MangasPage {
        val grid = document.selectFirst("#manga-grid") ?: return MangasPage(emptyList(), false)
        val mangas = grid.select("> a[href*=\"/manga/\"]")
            .map(::cardToSManga)
            .distinctBy { it.url }
        val hasNextPage = mangas.size >= MIN_PAGE_SIZE
        return MangasPage(mangas, hasNextPage)
    }

    private fun cardToSManga(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("h3, h4, .title")?.text()
            ?: element.text().lines().firstOrNull { it.isNotBlank() }!!
        thumbnail_url = element.selectFirst("img")?.imgAttr()
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    companion object {
        private const val MIN_PAGE_SIZE = 20
        private val CHAPTER_URL_REGEX = Regex("""/glava[-_]""")
        private val CHAPTER_NUMBER_REGEX = Regex("""/glava[-_]([\d,]+)""")
        private val ABSOLUTE_DATE_REGEX = Regex("""\d{2}\.\d{2}\.\d{4}""")
        private val RELATIVE_DATE_REGEX = Regex("""\d+\s*(сек|мин|час|дн|день|дня|недел|месяц|год|лет)""")
        private val RELATIVE_NUMBER_REGEX = Regex("""(\d+)""")
        private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
    }
}
