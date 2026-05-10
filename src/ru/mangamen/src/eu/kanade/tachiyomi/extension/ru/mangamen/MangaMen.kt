package eu.kanade.tachiyomi.extension.ru.mangamen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MangaMen : HttpSource() {

    override val name = "MangaMen"

    override val baseUrl = "https://mangamen.com"

    override val lang = "ru"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Safari/537.36",
        )
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(catalogUrl(page, sort = SORT_POPULAR), headers)

    override fun popularMangaParse(response: Response): MangasPage = catalogParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET(catalogUrl(page, sort = SORT_LATEST), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = catalogParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builder = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga-list")
            .addQueryParameter("page", page.toString())

        // Catalog filter param is `q`; `?query=...` is silently ignored.
        if (query.isNotBlank()) builder.addQueryParameter("q", query.trim())

        filters.filterIsInstance<UriFilter>().forEach { it.addToUri(builder) }

        return GET(builder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = catalogParse(response)

    private fun catalogUrl(page: Int, sort: String): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("manga-list")
        .addQueryParameter("sort", sort)
        .addQueryParameter("dir", "desc")
        .addQueryParameter("page", page.toString())
        .build()
        .toString()

    private fun catalogParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.media-card[href]")
            .map(::cardToSManga)
            .distinctBy { it.url }
        val hasNextPage = mangas.size >= MIN_PAGE_SIZE
        return MangasPage(mangas, hasNextPage)
    }

    private fun cardToSManga(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".media-card__title")?.text()!!
        setUrlWithoutDomain(element.absUrl("href"))
        thumbnail_url = element.absUrl("data-src").takeIf { it.isNotEmpty() }
            ?: BG_IMAGE_REGEX.find(element.attr("style"))?.groupValues?.get(1)
    }

    // ============================== Filters ================================

    override fun getFilterList(): FilterList = mangaMenFilters()

    // =============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
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
    }

    private fun parseStatus(raw: String?): Int = when (raw?.lowercase()) {
        "онгоинг", "продолжается" -> SManga.ONGOING
        "завершён", "завершен", "закончен" -> SManga.COMPLETED
        "приостановлен", "заморожен" -> SManga.ON_HIATUS
        "заброшен", "выпуск прекращён", "выпуск прекращен" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // =============================== Chapters ===============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapter-item").mapNotNull { item ->
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
    }

    private fun String?.parseChapterDate(): Long {
        if (isNullOrBlank()) return 0L
        val trimmed = trim()
        if (ABSOLUTE_DATE_REGEX.matches(trimmed)) {
            return absoluteDateFormat.tryParse(trimmed)
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

    override fun pageListParse(response: Response): List<Page> {
        // Reader page embeds the image list as `window.__pg = [{"p":1,"u":"..."}, ...]`
        // in an inline <script>; DOM <img>s only appear after client-side hydration.
        val html = response.body.string()
        val arrayJson = PAGES_VAR_REGEX.find(html)?.groupValues?.getOrNull(1)
            ?: return emptyList()
        return PAGE_URL_REGEX.findAll(arrayJson)
            .mapIndexed { index, m -> Page(index, imageUrl = m.groupValues[1].replace("\\/", "/")) }
            .toList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val SORT_LATEST = "last_chapter_at"
        private const val SORT_POPULAR = "views"
        private const val MIN_PAGE_SIZE = 30

        private val BG_IMAGE_REGEX = Regex("""url\(['"]?([^'"\)]+)['"]?\)""")
        private val RELATIVE_NUMBER_REGEX = Regex("""^(\d+)""")
        private val ABSOLUTE_DATE_REGEX = Regex("""^\d{2}\.\d{2}\.\d{4}$""")

        private val PAGES_VAR_REGEX = Regex("""window\.__pg\s*=\s*(\[[^\]]+\])""")
        private val PAGE_URL_REGEX = Regex(""""u":"([^"]+)"""")

        private val absoluteDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
    }
}
