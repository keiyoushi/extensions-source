package eu.kanade.tachiyomi.extension.ru.zazaza

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Zazaza : ParsedHttpSource() {

    override val name = "Zazaza"
    override val baseUrl = "https://a.zazaza.me"
    override val lang = "ru"
    override val supportsLatest = true

    private val myHeaders = Headers.Builder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Android) TachiyomiSY")
        .build()

    override fun headers(): Headers = myHeaders

    // --- Каталог / популярное ---
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga?page=$page", headers())

    // Поставил универсальные селекторы. Потом заменим на точные с сайта.
    override fun popularMangaSelector() = ".manga-card, .manga-item, .book-item, .grid .item"

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            val a = element.selectFirst("a[href]")!!
            title = element.selectFirst("h3, .title, .name")?.text()?.trim().orEmpty()
            setUrlWithoutDomain(a.attr("href"))
            thumbnail_url = element.selectFirst("img[src]")?.absUrl("src")
        }

    override fun popularMangaNextPageSelector() = ".pagination a[rel=next], .next"

    // --- Последние обновления (можно дублировать популярные для старта) ---
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/updates?page=$page", headers())

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // --- Поиск ---
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/search?q=$query&page=$page", headers())

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // --- Страница тайтла ---
    override fun mangaDetailsParse(document: Document): SManga {
        val info = document.selectFirst(".manga-info, .info, .book-info") ?: document
        return SManga.create().apply {
            title = info.selectFirst("h1, .title")?.text()?.trim().orEmpty()
            author = info.select(".author a, .author").joinToString { it.text() }.ifBlank { null }
            artist = info.select(".artist a, .artist").joinToString { it.text() }.ifBlank { null }
            genre = info.select(".genres a, .tags a").joinToString { it.text() }.ifBlank { null }
            description = info.selectFirst(".description, .summary, #description")?.text()
            thumbnail_url = document.selectFirst(".cover img, .poster img, .book-cover img")
                ?.absUrl("src")
        }
    }

    // --- Список глав ---
    override fun chapterListSelector() = ".chapters li a, .chapter-list li a, .chapter-item a"

    override fun chapterFromElement(element: Element): SChapter =
        SChapter.create().apply {
            name = element.text().trim()
            setUrlWithoutDomain(element.attr("href"))
            // дату можно допилить позже
        }

    // --- Список страниц главы ---
    override fun pageListParse(document: Document): List<Page> {
        // Вариант А: картинки прямо в верстке
        val imgs = document.select(".reader img[src], .page img[src], .pages img[src]")
        if (imgs.isNotEmpty()) {
            return imgs.mapIndexed { i, el -> Page(i, document.location(), el.absUrl("src")) }
        }

        // Вариант Б: картинки лежат в inline-скрипте (часто так и бывает)
        val allScripts = document.select("script").joinToString("\n") { it.data() }
        val urlRegex = Regex("""https?://[^"']+\.(?:jpg|jpeg|png|webp)""", RegexOption.IGNORE_CASE)
        val urls = urlRegex.findAll(allScripts).map { it.value }.distinct().toList()

        return urls.mapIndexed { i, u -> Page(i, document.location(), u) }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used")
    }
}