package eu.kanade.tachiyomi.extension.uk.mangainua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
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

class Mangainua : HttpSource() {

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

    private fun ajaxHeaders() = headersBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET(baseUrl)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.owl-carousel div.card--big").map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("h3.card__title a")!!.run {
            setUrlWithoutDomain(attr("href"))
            title = text()
        }
        thumbnail_url = element.selectFirst("img")?.run {
            absUrl("data-src").ifEmpty { absUrl("src") }
        }
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page/")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("main.main article.item").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("a:contains(Наступна)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.length > 2) {
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

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
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

    private fun Document.getInfoElement(text: String): Element? = selectFirst("div.item__full-sideba--header:has(div:containsOwn($text)) span.item__full-sidebar--description")

    // ============================== Chapters ==============================
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
            .add("action", "show")
            .add("news_id", metaElement.attr("data-news_id"))
            .add("news_category", metaElement.attr("data-news_category"))
            .add("this_link", metaElement.attr("data-this_link"))
            .add(userHashQuery, userHash)
            .build()

        val request = POST("$baseUrl/$endpoint", ajaxHeaders(), body)

        val chaptersDoc = client.newCall(request).execute().use { it.asJsoup() }
        return parseChapterElements(chaptersDoc.body().children()).asReversed()
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val userHash = document.parseUserHash()
        val endpoint = "engine/ajax/controller.php?mod=load_chapters_image"
        val userHashQuery = document.parseUserHashQuery(endpoint)
        val newsId = document.selectFirst(Evaluator.Id("comics"))!!.attr("data-news_id")
        val url = "$baseUrl/$endpoint&news_id=$newsId&action=show&$userHashQuery=$userHash"

        val pagesDoc = client.newCall(GET(url, ajaxHeaders())).execute()
            .use { it.asJsoup() }
        return pagesDoc.getElementsByTag("img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("data-src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities ==============================
    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("dd.MM.yyyy", Locale.ENGLISH)
        }

        private fun String.toDate(): Long = runCatching { DATE_FORMATTER.parse(trim())?.time }
            .getOrNull() ?: 0L

        private const val SITE_LOGIN_HASH = "site_login_hash"

        private fun Document.parseUserHash(): String {
            val script = selectFirst("script:containsData($SITE_LOGIN_HASH = )")?.data().orEmpty()
            val hash = script.substringAfter("$SITE_LOGIN_HASH = '").substringBefore("'")
            return hash.ifEmpty { throw Exception("Couldn't find user hash") }
        }

        private val userHashQueryRegex = Regex("""(\w+)\s*:\s*site_login_hash""")

        private fun Document.parseUserHashQuery(endpoint: String): String {
            val script = selectFirst("script:containsData($endpoint)")?.data()
                ?: throw Exception("Couldn't find user hash query script!")

            return userHashQueryRegex.find(script)?.groupValues?.get(1)
                ?: throw Exception("Couldn't find user hash query!")
        }
    }
}
