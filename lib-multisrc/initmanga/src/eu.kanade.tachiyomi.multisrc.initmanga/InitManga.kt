package eu.kanade.tachiyomi.multisrc.initmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.io.IOException
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

    @Serializable
    data class SearchDto(
        val title: String? = null,
        val url: String? = null,
        val thumb: String? = null,
    )

    private val uploadDateFormatter by lazy {
        SimpleDateFormat("d MMMM yyyy HH:mm", Locale("tr"))
    }

    private val fallbackDateFormatter by lazy {
        SimpleDateFormat(dateFormatStr, Locale.getDefault())
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val path = if (page == 1) "" else "page/$page/"
        return GET("$baseUrl/$popularUrlSlug/$path")
    }

    override fun popularMangaSelector() = SELECTOR_CARD

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val linkElement = element.selectFirst("h3 a")
            ?: element.selectFirst("div.uk-overflow-hidden a")
            ?: element.selectFirst("a")

        title = element.select("h3").text().trim().ifEmpty { "Bilinmeyen Seri" }
        setUrlWithoutDomain(linkElement?.attr("href") ?: "")
        thumbnail_url = element.select("img").let { img ->
            img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
        }
    }

    override fun popularMangaNextPageSelector() = SELECTOR_NEXT_PAGE

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/$latestUrlSlug/page/$page/")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/wp-json/initlise/v1/search".toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalStateException("Invalid baseUrl")

        urlBuilder.addQueryParameter("term", query)
        urlBuilder.addQueryParameter("page", page.toString())

        val url = urlBuilder.build()
        return Request.Builder()
            .url(url)
            .headers(headers)
            .get()
            .build()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.isSuccessful) {
            throw IOException("Search request failed: ${response.code}")
        }

        val bodyText = response.body.string()
        if (bodyText.isEmpty()) throw IOException("Empty response body")

        val list: List<SearchDto> = try {
            json.decodeFromString(bodyText)
        } catch (e: Exception) {
            throw IOException("Failed to parse search JSON", e)
        }

        val mangas = list.map { dto ->
            SManga.create().apply {
                val rawTitle = dto.title ?: ""
                title = Jsoup.parse(rawTitle).text().trim()

                val fullUrl = dto.url.orEmpty()

                val urlPath = try {
                    val parsed = fullUrl.toHttpUrlOrNull()
                    parsed?.encodedPath ?: fullUrl
                } catch (_: Exception) {
                    fullUrl
                }
                setUrlWithoutDomain(urlPath)

                thumbnail_url = dto.thumb
            }
        }

        return MangasPage(mangas, false)
    }

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

    override fun chapterListRequest(manga: SManga): Request {
        val base = baseUrl.toHttpUrlOrNull() ?: throw IllegalStateException("Invalid baseUrl")
        val resolved = if (manga.url.startsWith("http")) {
            manga.url.toHttpUrlOrNull() ?: throw IllegalStateException("Invalid manga.url")
        } else {
            base.resolve(manga.url) ?: throw IllegalStateException("Couldn't resolve manga.url")
        }

        return GET(resolved.toString(), headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val visitedUrls = mutableSetOf<String>()

        var document = response.asJsoup()
        var currentUrl = response.request.url.toString()

        val cleanUrl: (String) -> String = { it.substringBefore("#").trimEnd('/') }

        while (true) {
            if (!visitedUrls.add(cleanUrl(currentUrl))) break

            val items = document.select(chapterListSelector())
            if (items.isEmpty()) break

            chapters.addAll(items.map(::chapterFromElement))

            val nextLink = document.selectFirst("ul.uk-pagination a:has(span[uk-pagination-next]), ul.uk-pagination a[aria-label='Next page']")

            if (nextLink?.parents()?.any { it.hasClass("uk-disabled") } == true) break

            val nextUrl = nextLink?.attr("abs:href") ?: break

            if (cleanUrl(nextUrl) == cleanUrl(currentUrl) || cleanUrl(nextUrl) in visitedUrls) break

            currentUrl = nextUrl

            val nextResponse = runCatching {
                client.newCall(GET(currentUrl, headers)).execute()
            }.getOrNull()

            if (nextResponse == null || !nextResponse.isSuccessful) {
                nextResponse?.close()
                break
            }

            document = nextResponse.asJsoup()
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
            val doc = Jsoup.parseBodyFragment(trimmed, baseUrl)
            doc.select("img").mapIndexedNotNull { i, img ->
                val src = img.absUrl("data-src")
                    .ifEmpty { img.absUrl("src") }
                    .ifEmpty { img.absUrl("data-lazy-src") }

                val finalSrc = src.ifBlank {
                    val raw = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
                        .ifEmpty { img.attr("abs:data-lazy-src") }
                    when {
                        raw.startsWith("//") -> "https:$raw"
                        raw.startsWith("/") -> baseUrl.toHttpUrlOrNull()?.resolve(raw)?.toString()
                            ?: (baseUrl.trimEnd('/') + raw)

                        else -> raw
                    }
                }

                if (finalSrc.isBlank()) null else Page(i, imageUrl = finalSrc)
            }
        } else {
            runCatching {
                json.parseToJsonElement(trimmed).jsonArray.mapIndexed { i, el ->
                    val src = el.jsonPrimitive.content
                    val finalSrc = when {
                        src.startsWith("//") -> "https:$src"
                        src.startsWith("/") -> baseUrl.toHttpUrlOrNull()?.resolve(src)?.toString()
                            ?: (baseUrl.trimEnd('/') + src)
                        else -> src
                    }
                    Page(i, imageUrl = finalSrc)
                }
            }.getOrElse { emptyList() }
        }
    }

    private fun fallbackPages(document: Document): List<Page> {
        return document.select("div#chapter-content img[src]").mapIndexed { i, img ->
            val src = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            Page(i, "", src)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    companion object {
        private const val SELECTOR_CARD = "div.uk-panel"
        private const val SELECTOR_NEXT_PAGE = "a:contains(Sonraki), a.next, #next-link a"
    }
}
