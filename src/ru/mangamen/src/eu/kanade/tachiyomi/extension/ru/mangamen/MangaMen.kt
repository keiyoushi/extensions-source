package eu.kanade.tachiyomi.extension.ru.mangamen

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
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Source
abstract class MangaMen : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(2)
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        // https://github.com/keiyoushi/extensions-source/pull/15754
        set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest(SORT_POPULAR, page)

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest(SORT_LATEST, page)

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = makeCatalogRequest(SORT_POPULAR, page, query, filters)

    // ============================== Search Utilities ===============================
    protected open suspend fun makeCatalogRequest(sortBy: String, page: Int, query: String? = null, filters: FilterList? = null): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga-list")
            filters?.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.included?.forEach { addQueryParameter("genres[include][]", it) }
                        filter.excluded?.forEach { addQueryParameter("genres[exclude][]", it) }
                    }
                    is TagsFilter -> {
                        filter.included?.forEach { addQueryParameter("tags[include][]", it) }
                        filter.excluded?.forEach { addQueryParameter("tags[exclude][]", it) }
                    }
                    is TypeFilter -> filter.selected?.forEach { addQueryParameter("types[]", it) }
                    is TranslationStatusFilter -> filter.selected?.forEach { addQueryParameter("status[]", it) }
                    is StatusFilter -> filter.selected?.forEach { addQueryParameter("manga_status[]", it) }
                    is YearRangeFilter -> {
                        filter.minValue?.let { addQueryParameter("year[min]", it) }
                        filter.maxValue?.let { addQueryParameter("year[max]", it) }
                    }
                    is OrderBy -> {
                        addQueryParameter("sort", filter.selected)
                        addQueryParameter("dir", filter.order)
                    }
                    else -> {}
                }
            }
            if (filters == null) {
                addQueryParameter("sort", sortBy)
                addQueryParameter("dir", "desc")
            }
            // Catalog filter param is `q`; `?query=...` is silently ignored.
            if (query?.isNotBlank() == true) addQueryParameter("q", query.trim())
            addQueryParameter("page", page.toString())
        }.build()

        return client.get(url).use { response ->
            val document = response.asJsoup()
            val mangas = document.select("a.media-card[href]").map { element ->
                SManga.create().apply {
                    title = element.selectFirst(".media-card__title")?.text()!!
                    setUrlWithoutDomain(element.absUrl("href"))
                    thumbnail_url = element.absUrl("data-src").takeIf { it.isNotEmpty() }
                        ?: BG_IMAGE_REGEX.find(element.attr("style"))?.groupValues?.get(1)
                }
            }
            val hasNextPage = mangas.size >= MIN_PAGE_SIZE
            MangasPage(mangas, hasNextPage)
        }
    }

    // ============================== Filters ================================
    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val response = client.get("$baseUrl/manga-list").asJsoup()
        val script = response.selectFirst("script:containsData(window.__DATA__)")?.data()
            ?: throw Exception("Информация о фильтрах отсутствует")

        val data = script
            .substringAfter("window.__DATA__ = ")
            .substringBeforeLast(";")
            .trim()
            .parseAs<FiltersWrapperDto>()

        return data.toJson()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters.add(OrderBy())
        data?.parseAs<SearchData>()?.let {
            if (it.genres?.isNotEmpty() == true) filters.add(GenreFilter(it.genres))
            if (it.tags?.isNotEmpty() == true) filters.add(TagsFilter(it.tags))
            filters.add(YearRangeFilter())
            if (it.type?.isNotEmpty() == true) filters.add(TypeFilter(it.type))
            if (it.status?.isNotEmpty() == true) filters.add(TranslationStatusFilter(it.status))
            if (it.mangaStatus?.isNotEmpty() == true) filters.add(StatusFilter(it.mangaStatus))
        }
        return FilterList(filters)
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0].isNotBlank()) {
            val tmpManga = SManga.create().apply {
                this.url = "/${url.pathSegments[0]}"
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // ============================== Manga ================================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = client.get(getMangaUrl(manga)).asJsoup()
        val newManga = mangaDetailsParse(data, manga.url)
        val newChapters = chapterListParse(data)

        return SMangaUpdate(newManga, newChapters)
    }

    // =============================== Details ===============================

    private fun mangaDetailsParse(document: Document, mangeUrl: String): SManga = SManga.create().apply {
        url = mangeUrl
        title = document.selectFirst("h1[itemprop=name]")?.text()
            ?: document.selectFirst("h1")?.text()!!

        // `src` is a placeholder (`/images/preroll.svg`); real URL is on `data-src`.
        thumbnail_url = document.selectFirst(".manga__image img.manga__cover, .manga__image img")
            ?.let { it.attr("abs:data-src").ifBlank { it.attr("abs:src") } }
            ?.takeIf { !it.endsWith("/preroll.svg") && it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        val rows = document.select(".info-list__row").associate { row ->
            val key = row.selectFirst("strong")?.text().orEmpty()
            val value = row.selectFirst("span")?.text().orEmpty()
            key to value
        }

        // "Переводчики" rows render as <a> chips without a <span>, so look them up by label.
        val translators = document.select(".info-list__row")
            .firstOrNull { it.selectFirst("strong")?.text() == "Переводчики" }
            ?.select("a")?.joinToString(", ") { it.text() }
            ?.takeIf { it.isNotEmpty() }

        author = rows["Автор"]?.takeIf(String::isNotEmpty)
        artist = rows["Художник"]?.takeIf(String::isNotEmpty) ?: author

        val genres = document.select("a[href*='genres[include]']").map { it.text() }
        val tags = document.select("a[href*='tags[include]']").map { it.text() }
        val type = rows["Тип"]
        genre = (listOfNotNull(type) + genres + tags)
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString()

        status = parseStatus(rows["Статус тайтла"])

        val synopsis = document.selectFirst(".info-desc__content")?.wholeText()?.trim()
        val altTitle = document.selectFirst("h4[itemprop=alternativeHeadline]")?.text()

        description = buildString {
            if (!synopsis.isNullOrBlank()) append(synopsis)
            if (!altTitle.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("Альтернативные названия: $altTitle")
            }
            val extras = listOfNotNull(
                rows["Издатель"]?.takeIf(String::isNotEmpty)?.let { "Издатель: $it" },
                rows["Статус перевода"]?.takeIf(String::isNotEmpty)?.let { "Статус перевода: $it" },
                rows["Дата релиза"]?.takeIf(String::isNotEmpty)?.let { "Дата релиза: $it" },
                rows["Формат выпуска"]?.takeIf(String::isNotEmpty)?.let { "Формат: $it" },
                rows["Загружено глав"]?.takeIf(String::isNotEmpty)?.let { "Загружено глав: $it" },
                rows["Просмотров"]?.takeIf(String::isNotEmpty)?.let { "Просмотров: $it" },
                rows["Рейтинг"]?.takeIf(String::isNotEmpty)?.let { "Рейтинг: $it" },
                translators?.let { "Переводчики: $it" },
            )
            if (extras.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                extras.joinTo(this, separator = "\n")
            }
        }
    }

    private fun parseStatus(raw: String?): Int = when (raw?.lowercase()) {
        "онгоинг", "продолжается" -> SManga.ONGOING
        "завершён", "завершен", "закончен" -> SManga.COMPLETED
        "приостановлен", "заморожен" -> SManga.ON_HIATUS
        "заброшен", "выпуск прекращён", "выпуск прекращен" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // =============================== Chapters ===============================

    private fun chapterListParse(document: Document): List<SChapter> = document.select(".chapter-item").mapNotNull { item ->
        val link = item.selectFirst(".chapter-item__name a[href]") ?: return@mapNotNull null
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            val volume = item.attr("data-volume").ifBlank { "1" }
            val number = item.attr("data-number")
            name = link.text().ifEmpty { "Том $volume. Глава $number" }
            chapter_number = number.toFloatOrNull() ?: -1f
            date_upload = item.selectFirst(".chapter-item__date")?.text().parseChapterDate()
            scanlator = item.selectFirst(".chapter-item__added span")?.text()
                ?.takeIf(String::isNotEmpty)
        }
    }

    private fun String?.parseChapterDate(): Long {
        if (isNullOrBlank()) return 0L
        val trimmed = trim()
        if (ABSOLUTE_DATE_REGEX.matches(trimmed)) {
            return absoluteDateFormat.tryParseDate(trimmed)
        }
        val lower = trimmed.lowercase()
        if (lower.startsWith("сегодня")) return Calendar.getInstance().timeInMillis
        if (lower.startsWith("вчера")) {
            return Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        }
        val amount = RELATIVE_NUMBER_REGEX.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: return 0L
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

    // =============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter)).asJsoup()
        val script = response.selectFirst("script:containsData(window.__pg)")?.data()
            ?: throw Exception("Информация о изображениях отсутствует")

        val data = script
            .substringAfter("window.__pg = ")
            .substringBeforeLast(";")
            .trim()
            .parseAs<List<Pages>>()

        return data.map {
            Page(it.p, imageUrl = it.u)
        }
    }

    companion object {
        private const val SORT_LATEST = "last_chapter_at"
        private const val SORT_POPULAR = "views"
        private const val MIN_PAGE_SIZE = 30

        private val BG_IMAGE_REGEX = Regex("""url\(['"]?([^'")]+)['"]?\)""")
        private val RELATIVE_NUMBER_REGEX = Regex("""^(\d+)""")
        private val ABSOLUTE_DATE_REGEX = Regex("""^\d{2}\.\d{2}\.\d{4}$""")
        private val absoluteDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru"))
    }
}
