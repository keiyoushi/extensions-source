package eu.kanade.tachiyomi.extension.uk.mangainua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.jsoup.select.Evaluator
import java.text.SimpleDateFormat
import java.util.Locale

class Mangainua : ParsedHttpSource() {

    // Info
    override val name = "MANGA/in/UA"
    override val baseUrl = "https://manga.in.ua"
    override val lang = "uk"
    override val supportsLatest = true

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 5)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET(baseUrl)

    override fun popularMangaSelector() = "div.owl-carousel div.card--big"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("h3.card__title a")!!.run {
            setUrlWithoutDomain(attr("href"))
            title = text()
        }
        thumbnail_url = element.selectFirst("img")?.run {
            absUrl("data-src").ifEmpty { absUrl("src") }
        }
    }

    override fun popularMangaNextPageSelector() = null

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page/")

    override fun latestUpdatesSelector() = "main.main article.item"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = "a:contains(Наступна)"

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.length > 2) {
            POST(
                "$baseUrl/index.php?do=search",
                body = FormBody.Builder()
                    .add("do", "search")
                    .add("subaction", "search")
                    .add("story", query)
                    .add("search_start", page.toString())
                    .build(),
                headers = headers,
            )
        } else {
            throw Exception("Запит має містити щонайменше 3 символи / The query must contain at least 3 characters")
        }
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("span.UAname")!!.text()
        description = document.selectFirst("div.item__full-description")!!.text()
        thumbnail_url = document.selectFirst("div.item__full-sidebar--poster img")?.absUrl("src")
        status = when (document.getInfoElement("Статус перекладу:")?.text()?.trim()) {
            "Триває" -> SManga.ONGOING
            "Покинуто" -> SManga.CANCELLED
            "Закінчений" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        genre = buildList {
            // genres
            addAll(document.getInfoElement("Жанри:")?.select("a")?.eachText().orEmpty())

            // additional
            val type = when (document.getInfoElement("Тип:")?.text()) {
                "ВЕБМАНХВА" -> "Manhwa"
                "МАНХВА" -> "Manhwa"
                "МАНЬХВА" -> "Manhua"
                "ВЕБМАНЬХВА" -> "Manhua"
                else -> "Manga"
            }
            add(type)
        }.joinToString()
    }

    private fun Document.getInfoElement(text: String): Element? =
        selectFirst("div.item__full-sideba--header:has(div:containsOwn($text)) span.item__full-sidebar--description")

    // ============================== Chapters ==============================
    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException()
    }

    private fun parseChapterElements(elements: Elements): List<SChapter> {
        var previousChapterName: String? = null
        var previousChapterNumber: Float = 0F
        return elements.map { element ->
            SChapter.create().apply {
                val urlElement = element.selectFirst("a")!!
                setUrlWithoutDomain(urlElement.attr("href"))
                val chapterName = urlElement.ownText().trim()
                val chapterNumber = element.attr("manga-chappter")
                    .ifEmpty { chapterName.substringAfter("Розділ").substringBefore("-").trim() }
                    .toFloatOrNull() ?: 1F

                if (chapterName.contains("Альтернативний переклад")) {
                    // Alternative translation of the previous chapter
                    name = previousChapterName.orEmpty().substringBefore("-").trim()
                    scanlator = urlElement.text().substringAfter("від:").trim()
                    chapter_number = previousChapterNumber
                } else {
                    // Normal chapter
                    name = chapterName
                    chapter_number = chapterNumber
                    scanlator = element.attr("translate").takeUnless(String::isBlank)

                    previousChapterName = chapterName
                    previousChapterNumber = chapterNumber
                }
                date_upload = element.child(0).ownText().toDate()
            }
        }
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val userHash = document.parseUserHash()
        val endpoint = "engine/ajax/controller.php?mod=load_chapters"
        val userHashQuery = document.parseUserHashQuery(endpoint)
        val metaElement = document.selectFirst(Evaluator.Id("linkstocomics"))!!
        val body = FormBody.Builder()
            .addEncoded("action", "show")
            .addEncoded("news_id", metaElement.attr("data-news_id"))
            .addEncoded("news_category", metaElement.attr("data-news_category"))
            .addEncoded("this_link", metaElement.attr("data-this_link"))
            .addEncoded(userHashQuery, userHash)
            .build()
        val request = POST("$baseUrl/$endpoint", headers, body)

        val chaptersDoc = client.newCall(request).execute().use { it.asJsoup() }
        return parseChapterElements(chaptersDoc.body().children()).asReversed()
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        val userHash = document.parseUserHash()
        val endpoint = "engine/ajax/controller.php?mod=load_chapters_image"
        val userHashQuery = document.parseUserHashQuery(endpoint)
        val newsId = document.selectFirst(Evaluator.Id("comics"))!!.attr("data-news_id")
        val url = "$baseUrl/$endpoint&news_id=$newsId&action=show&$userHashQuery=$userHash"
        val pagesDoc = client.newCall(GET(url, headers)).execute()
            .use { it.asJsoup() }
        return pagesDoc.getElementsByTag("img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("data-src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================= Utilities ==============================
    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("dd.MM.yyyy", Locale.ENGLISH)
        }

        private fun String.toDate(): Long {
            return runCatching { DATE_FORMATTER.parse(trim())?.time }
                .getOrNull() ?: 0L
        }

        private const val SITE_LOGIN_HASH = "site_login_hash"

        private fun Document.parseUserHash(): String {
            val script = selectFirst("script:containsData($SITE_LOGIN_HASH = )")?.data().orEmpty()
            val hash = script.substringAfter("$SITE_LOGIN_HASH = '").substringBefore("'")
            return hash.ifEmpty { throw Exception("Couldn't find user hash") }
        }

        private fun Document.parseUserHashQuery(endpoint: String): String {
            val script = selectFirst("script:containsData($endpoint)")?.data()
            val queries = script?.run {
                substringAfter(endpoint).substringAfter('{').substringBefore('}')
            }
            val query = queries.orEmpty()
                .substringBefore(SITE_LOGIN_HASH, "")
                .substringBeforeLast(':')
                .trimEnd()
                .substringAfterLast(' ')

            return query.ifEmpty { throw Exception("Couldn't find user hash query!") }
        }
    }
}
