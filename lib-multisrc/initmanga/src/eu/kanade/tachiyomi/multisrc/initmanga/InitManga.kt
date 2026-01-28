package eu.kanade.tachiyomi.multisrc.initmanga

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

abstract class InitManga(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    override val versionId: Int,
    private val mangaUrlDirectory: String = "seri",
    private val dateFormatStr: String = "yyyy-MM-dd'T'HH:mm:ss",
    private val popularUrlSlug: String = mangaUrlDirectory,
    private val latestUrlSlug: String = "son-guncellemeler",
) : ParsedHttpSource() {

    override val supportsLatest = true

    @Serializable
    class SearchDto(
        val title: String? = null,
        val url: String? = null,
        val thumb: String? = null,
    )

    protected class GenreData(
        val name: String,
        val url: String,
    )

    protected class Genre(
        name: String,
        val url: String,
    ) : Filter.CheckBox(name)

    protected class GenreListFilter(
        name: String,
        genres: List<Genre>,
    ) : Filter.Group<Genre>(name, genres)

    protected var genrelist: List<GenreData>? = null

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
        return GET("$baseUrl/$popularUrlSlug/$path", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (genrelist == null) {
            genrelist = parseGenres(response.asJsoup(response.peekBody(Long.MAX_VALUE).string()))
        }
        return super.popularMangaParse(response)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        if (genrelist == null) {
            genrelist = parseGenres(response.asJsoup(response.peekBody(Long.MAX_VALUE).string()))
        }
        return super.latestUpdatesParse(response)
    }

    protected open fun parseGenres(document: Document): List<GenreData>? {
        return document.selectFirst("ul.uk-list.uk-text-small, div#uk-tab-3")?.select("li a, a")?.map { element ->
            GenreData(
                name = element.text(),
                url = element.attr("href"),
            )
        }
    }

    protected open fun getGenreList(): List<Genre> {
        return genrelist?.map { Genre(it.name, it.url) }.orEmpty()
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()

        if (!genrelist.isNullOrEmpty()) {
            filters.add(
                Filter.Header("Not: Birden fazla kategori seçilirse sadece ilki kullanılır"),
            )
            filters.add(
                GenreListFilter("Kategoriler", getGenreList()),
            )
        } else {
            filters.add(
                Filter.Header("Kategoriler yükleniyor..."),
            )
        }

        return FilterList(filters)
    }

    override fun popularMangaSelector() = "div.uk-panel"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val linkElement = element.selectFirst("h3 a, div.uk-overflow-hidden a")
            ?: element.selectFirst("a")

        title = element.select("h3").text().trim().ifEmpty {
            element.select("a").clone()
                .apply {
                    select("span, small").remove()
                }.text().trim()
        }
        setUrlWithoutDomain(linkElement!!.attr("href"))
        thumbnail_url = element.select("img").let { img ->
            img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
        }
    }

    override fun popularMangaNextPageSelector() = "a:contains(Sonraki), a.next, #next-link a, li#next-link"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/$latestUrlSlug/page/$page/", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genreFilter = filters.findInstance<GenreListFilter>()
        val selectedGenres = genreFilter?.state?.filter { it.state }.orEmpty()

        val selectedGenre = selectedGenres.firstOrNull()

        if (selectedGenre != null && query.isEmpty()) {
            val genreUrl = selectedGenre.url.let { url ->
                when {
                    url.startsWith("http") -> url
                    url.startsWith("/") -> "$baseUrl$url"
                    else -> "$baseUrl/$url"
                }
            }

            val finalUrl = if (page > 1) {
                val cleanUrl = genreUrl.trimEnd('/')
                "$cleanUrl/page/$page/"
            } else {
                genreUrl
            }

            return GET(finalUrl, headers)
        }

        val urlBuilder = "$baseUrl/wp-json/initlise/v1/search".toHttpUrl().newBuilder()
        urlBuilder.addQueryParameter("term", query)
        urlBuilder.addQueryParameter("page", page.toString())

        val url = urlBuilder.build()
        return GET(url, headers)
    }

    private inline fun <reified T> Iterable<*>.findInstance(): T? {
        return firstOrNull { it is T } as? T
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val bodyText = response.body.string()
        if (bodyText.isEmpty()) throw IOException("Empty response body")

        if (bodyText.trimStart().startsWith("<") || bodyText.trimStart().startsWith("<!")) {
            if (genrelist == null) {
                genrelist = parseGenres(Jsoup.parse(bodyText, baseUrl))
            }
            val document = Jsoup.parse(bodyText, baseUrl)
            val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }

            val hasNextPage = document.selectFirst("ul.uk-pagination li:not(#prev-link) a:not(:matchesOwn(\\S))[href^=http]") != null

            return MangasPage(mangas, hasNextPage)
        }

        val list: List<SearchDto> = bodyText.parseAs()

        val mangas = list.map { dto ->
            SManga.create().apply {
                val rawTitle = dto.title
                title = Jsoup.parse(rawTitle!!).text().trim()
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
        description = document.select("div#manga-description").clone()
            .apply {
                select("a, span").remove()
            }
            .text()
        genre = document.select("div#genre-tags a").joinToString { it.text() }
        thumbnail_url = document.selectFirst("div.single-thumb img")?.attr("abs:src")
            ?: document.selectFirst("a.story-cover img")?.attr("abs:src")

        val siteTitle = document.selectFirst("h1")?.text()
        val mangaTitle = document.selectFirst("h2.uk-h3")?.text()
        title = if (!mangaTitle.isNullOrBlank()) mangaTitle else siteTitle!!
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()
        val baseUrl = response.request.url
        var page = 2

        do {
            val items = document.select(chapterListSelector())
            if (items.isEmpty()) break

            chapters.addAll(items.map(::chapterFromElement))

            val nextUrl = baseUrl.newBuilder()
                .addPathSegment("bolum")
                .addPathSegment("page")
                .addPathSegment(page.toString())
                .build()

            page++

            val nextResponse = client.newCall(GET(nextUrl, headers)).execute()
            document = nextResponse.asJsoup()
            nextResponse.close()

            val hasNextPage = document.selectFirst("ul.uk-pagination a:not(:matchesOwn(\\S))[href^=http]") != null
        } while (hasNextPage)

        return chapters
    }

    override fun chapterListSelector() = "div.chapter-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))

        val rawName = element.select("h3").text().trim()

        name = rawName.substringAfterLast("–").substringAfterLast("-").trim()

        val dateStr = element.select("span[uk-tooltip]").attr("uk-tooltip")
            .substringAfter("title:")
            .substringBefore(";")
            .trim()

        date_upload = runCatching {
            if (dateStr.isNotEmpty()) {
                uploadDateFormatter.tryParse(dateStr)
            } else {
                val fallbackDate = element.select("time").attr("datetime")
                fallbackDateFormatter.tryParse(fallbackDate)
            }
        }.getOrNull() ?: 0L
    }

    override fun pageListParse(document: Document): List<Page> {
        val encryptedData = document.selectFirst("script[src*=dmFyIElua]")?.attr("src")
            ?.substringAfter("base64,")
            ?.substringBeforeLast("\"")

        // RagnarScans

        if (encryptedData.isNullOrEmpty()) {
            runCatching {
                val content = AesDecrypt.REGEX_ENCRYPTED_DATA.find(document.html())?.groupValues?.get(1)
                if (content != null) {
                    val encryptedObject: JsonObject = content.parseAs()
                    val ciphertext = encryptedObject["ciphertext"]!!.jsonPrimitive.content
                    val ivHex = encryptedObject["iv"]!!.jsonPrimitive.content
                    val saltHex = encryptedObject["salt"]!!.jsonPrimitive.content
                    val decryptedContent = AesDecrypt.decryptLayered(document.html(), ciphertext, ivHex, saltHex)

                    if (!decryptedContent.isNullOrEmpty()) {
                        return parseDecryptedPages(decryptedContent)
                    }
                }
            }.onFailure {
                error(it)
            }

            return fallbackPages(document)
        }

        // Merlin Toons
        val decodedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
        val decodedString = String(decodedBytes, Charsets.UTF_8)

        runCatching {
            val regex = Regex("""InitMangaEncryptedChapter\s*=\s*(\{.*?\})""", RegexOption.DOT_MATCHES_ALL)
            val jsonString = regex.find(decodedString)?.groupValues?.get(1)
                ?: decodedString.substringAfter("InitMangaEncryptedChapter=").substringBeforeLast(";")
            val encryptedObject: JsonObject = jsonString.parseAs()

            val ciphertext = encryptedObject["ciphertext"]!!.jsonPrimitive.content
            val ivHex = encryptedObject["iv"]!!.jsonPrimitive.content
            val saltHex = encryptedObject["salt"]!!.jsonPrimitive.content
            val decryptedContent = AesDecrypt.decryptLayered(document.html(), ciphertext, ivHex, saltHex)

            if (!decryptedContent.isNullOrEmpty()) {
                return parseDecryptedPages(decryptedContent)
            }
        }.onFailure {
            error(it)
        }

        return fallbackPages(document)
    }

    private fun parseDecryptedPages(content: String): List<Page> {
        val trimmed = content.trim()

        return if (trimmed.startsWith("<")) {
            val doc = Jsoup.parseBodyFragment(trimmed, baseUrl)
            doc.select("img").mapIndexedNotNull { i, img ->
                val src = img.absUrl("abs:data-src")
                    .ifEmpty { img.absUrl("abs:src") }
                    .ifEmpty { img.absUrl("abs:data-lazy-src") }

                val finalSrc = src.ifBlank {
                    img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
                        .ifEmpty { img.attr("abs:data-lazy-src") }
                }

                if (finalSrc.isBlank()) null else Page(i, imageUrl = finalSrc)
            }
        } else {
            runCatching {
                trimmed.parseAs<JsonArray>().jsonArray.mapIndexed { i, el ->
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
}
