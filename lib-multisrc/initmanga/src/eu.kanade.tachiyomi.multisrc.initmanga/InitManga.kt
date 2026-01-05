package eu.kanade.tachiyomi.multisrc.initmanga

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

abstract class InitManga(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val mangaUrlDirectory: String = "seri",
    private val dateFormatStr: String = "yyyy-MM-dd'T'HH:mm:ss",
    private val popularUrlSlug: String = mangaUrlDirectory,
    private val latestUrlSlug: String = "son-guncellemeler",
) : ParsedHttpSource() {

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val uploadDateFormatter by lazy {
        SimpleDateFormat("d MMMM yyyy HH:mm", Locale("tr"))
    }

    private val fallbackDateFormatter by lazy {
        SimpleDateFormat(dateFormatStr, Locale.getDefault())
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val path = if (page == 1) "" else "page/$page/"
        return GET("$baseUrl/$popularUrlSlug/$path", headers)
    }

    override fun popularMangaSelector() = SELECTOR_CARD

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val linkElement = element.selectFirst("h3 a")
            ?: element.selectFirst("div.uk-overflow-hidden a")
            ?: element.selectFirst("a")

        title = element.select("h3").text().trim().ifEmpty { "Bilinmeyen Seri" }
        setUrlWithoutDomain(linkElement?.attr("href") ?: "")
        thumbnail_url = element.select("img").let { img ->
            img.attr("data-src").ifEmpty { img.attr("src") }
        }
    }

    override fun popularMangaNextPageSelector() = SELECTOR_NEXT_PAGE

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/$latestUrlSlug/page/$page/", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        return if (query.isNotBlank()) {
            fetchSearchFromApi(query)
        } else {
            super.getSearchManga(page, query, filters)
        }
    }

    private fun fetchSearchFromApi(query: String): MangasPage {
        return runCatching {
            client.newCall(GET("$baseUrl/wp-json/initlise/v1/search?term=$query", headers)).execute().use { response ->
                if (!response.isSuccessful) return@use MangasPage(emptyList(), false)

                val bodyText = response.body.string()
                if (bodyText.isEmpty()) return@use MangasPage(emptyList(), false)

                if (bodyText.trim().startsWith("[")) {
                    val searchResults = json.parseToJsonElement(bodyText).jsonArray
                    val mangas = searchResults.map { element ->
                        val jsonObject = element.jsonObject
                        SManga.create().apply {
                            val rawTitle = jsonObject["title"]?.jsonPrimitive?.content ?: ""
                            title = Jsoup.parse(rawTitle).text().trim()
                            setUrlWithoutDomain(jsonObject["url"]?.jsonPrimitive?.content ?: "")
                            thumbnail_url = jsonObject["thumb"]?.jsonPrimitive?.content
                        }
                    }
                    MangasPage(mangas, false)
                } else {
                    MangasPage(emptyList(), false)
                }
            }
        }.getOrElse { MangasPage(emptyList(), false) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/page/$page/?s=$query", headers)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select("div#manga-description p").text()
        genre = document.select("div#genre-tags a").joinToString { it.text() }
        thumbnail_url = document.selectFirst("div.single-thumb img")?.attr("abs:src")
            ?: document.selectFirst("a.story-cover img")?.attr("abs:src")

        val siteTitle = document.selectFirst("h1")?.text()
        val mangaTitle = document.selectFirst("h2.uk-h3")?.text()
        title = if (!mangaTitle.isNullOrBlank()) mangaTitle else siteTitle ?: "Başlık Yok"
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        var url = if (manga.url.startsWith("http")) manga.url else baseUrl + manga.url

        var page = 0
        val maxPages = 50
        val processedUrls = mutableSetOf<String>()

        while (page++ < maxPages) {
            if (!processedUrls.add(url)) break

            val response = runCatching { client.newCall(GET(url, headers)).execute() }.getOrNull()
            if (response == null || !response.isSuccessful) {
                response?.close()
                break
            }

            val doc = response.use { it.asJsoup() }
            val items = doc.select(chapterListSelector())

            if (items.isEmpty()) break

            chapters.addAll(items.map { chapterFromElement(it) })

            val nextLink = doc.select(popularMangaNextPageSelector()).first()
            url = nextLink?.attr("abs:href") ?: break
        }

        return chapters
    }

    override fun chapterListSelector() = "div.chapter-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val select = element.selectFirst("a")
        setUrlWithoutDomain(select?.attr("href") ?: "")

        val rawName = element.select("h3").text().trim()

        name = rawName.substringAfterLast("–").substringAfterLast("-").trim()

        val dateStr = element.select("span[uk-tooltip]").attr("uk-tooltip")
            .substringAfter("title:")
            .substringBefore(";")
            .trim()

        date_upload = runCatching {
            if (dateStr.isNotEmpty()) {
                uploadDateFormatter.parse(dateStr)?.time
            } else {
                val fallbackDate = element.select("time").attr("datetime")
                fallbackDateFormatter.parse(fallbackDate)?.time
            }
        }.getOrNull() ?: 0L
    }

    @SuppressLint("NewApi")
    override fun pageListParse(document: Document): List<Page> {
        val html = document.html()
        val encryptedDataMatch = AesDecrypt.REGEX_ENCRYPTED_DATA.find(html)

        if (encryptedDataMatch != null) {
            runCatching {
                val encryptedObject = json.parseToJsonElement(encryptedDataMatch.groupValues[1]).jsonObject
                val ciphertext = encryptedObject["ciphertext"]!!.jsonPrimitive.content
                val ivHex = encryptedObject["iv"]!!.jsonPrimitive.content
                val saltHex = encryptedObject["salt"]!!.jsonPrimitive.content

                val decryptedContent = AesDecrypt.decryptLayered(html, ciphertext, ivHex, saltHex)

                if (!decryptedContent.isNullOrEmpty()) {
                    return parseDecryptedPages(decryptedContent)
                }
            }
        }

        return fallbackPages(document)
    }

    private fun parseDecryptedPages(content: String): List<Page> {
        val trimmed = content.trim()

        return if (trimmed.startsWith("<")) {
            Jsoup.parseBodyFragment(trimmed).select("img").mapIndexed { i, img ->
                val src = img.attr("data-src")
                    .ifEmpty { img.attr("src") }
                    .ifEmpty { img.attr("data-lazy-src") }
                val finalSrc = if (src.startsWith("/")) baseUrl + src else src
                Page(i, "", finalSrc)
            }
        } else {
            runCatching {
                json.parseToJsonElement(trimmed).jsonArray.mapIndexed { i, el ->
                    val src = el.jsonPrimitive.content
                    val finalSrc = if (src.startsWith("/")) baseUrl + src else src
                    Page(i, "", finalSrc)
                }
            }.getOrElse { emptyList() }
        }
    }

    private fun fallbackPages(document: Document): List<Page> {
        return document.select("div#chapter-content img[src]").mapIndexed { i, img ->
            val src = img.attr("abs:src").ifEmpty { img.attr("data-src") }
            Page(i, "", src)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val SELECTOR_CARD = "div.uk-panel"
        private const val SELECTOR_NEXT_PAGE = "a:contains(Sonraki), a.next, #next-link a"
    }
}
