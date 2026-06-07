package eu.kanade.tachiyomi.multisrc.initmanga

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
    dateFormatStr: String = "yyyy-MM-dd'T'HH:mm:ss",
    private val popularUrlSlug: String = mangaUrlDirectory,
    private val latestUrlSlug: String = "son-guncellemeler",
) : HttpSource() {

    override val supportsLatest = true

    protected var genrelist: List<GenreData>? = null

    private val fallbackDateFormatter = SimpleDateFormat(dateFormatStr, Locale.ROOT)

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val path = if (page == 1) "" else "page/$page/"
        return GET("$baseUrl/$popularUrlSlug/$path", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (genrelist == null) {
            genrelist = parseGenres(document)
        }
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector().let { document.selectFirst(it) != null }
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (genrelist == null) {
            genrelist = parseGenres(document)
        }
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector().let { document.selectFirst(it) != null }
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun parseGenres(document: Document): List<GenreData>? = document.selectFirst("ul.uk-list.uk-text-small, div#uk-tab-3")?.select("li a, a")?.map { element ->
        GenreData(
            name = element.text(),
            url = element.absUrl("href"),
        )
    }

    protected open fun getGenreList(): List<Genre> = genrelist?.map { Genre(it.name, it.url) }.orEmpty()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()

        if (!genrelist.isNullOrEmpty()) {
            filters.add(
                GenreListFilter("Kategoriler", getGenreList()),
            )
        } else {
            filters.add(
                Filter.Header("Kategorileri yüklemek için sıfırlaya basın"),
            )
        }

        if (typeFilterOptions.isNotEmpty()) filters.add(TypeFilter("Tür"))
        if (statusFilterOptions.isNotEmpty()) filters.add(StatusFilter("Durum"))
        if (ageRatingFilterOptions.isNotEmpty()) filters.add(AgeRatingFilter("Yaş Sınırı"))
        if (ratingMinFilterOptions.isNotEmpty()) filters.add(RatingMinFilter("Min. Puan"))
        if (ratingMaxFilterOptions.isNotEmpty()) filters.add(RatingMaxFilter("Maks. Puan"))
        if (sortFilterOptions.isNotEmpty()) filters.add(SortFilter("Şuna Göre Sırala"))

        return FilterList(filters)
    }

    open fun popularMangaSelector() = "div.manga-item-grid > div.uk-panel.uk-position-relative, " +
        "div.manga-item-grid > div.uk-panel:not(.manga-item-ranking):not(.user-item-info), " +
        "div.uk-panel.uk-position-relative, " +
        "div.uk-panel:not(.manga-item-ranking):not(.user-item-info)"

    open fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val linkElement = element.selectFirst("h3 a, div.uk-overflow-hidden a")
            ?: element.selectFirst("a")

        title = element.select("h3").text().ifEmpty {
            element.select("a").clone()
                .apply {
                    select("span, small").remove()
                }.text()
        }
        setUrlWithoutDomain(linkElement!!.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.let { img ->
            img.absUrl("data-src").ifEmpty { img.absUrl("src") }
        }
    }

    open fun popularMangaNextPageSelector() = "head link[rel=next], link[rel=next], " +
        "ul.uk-pagination li:not(.uk-disabled) a[aria-label=\"Sonraki sayfa\"], " +
        "ul.uk-pagination li:not(.uk-disabled) a[aria-label=\"Next page\"], " +
        "ul.uk-pagination li:not(.uk-disabled) a:has([uk-pagination-next]), " +
        "ul.uk-pagination li#next-link:not(.uk-disabled) a, " +
        "a:contains(Sonraki sayfa), a:contains(Next page), a.next"

    override fun latestUpdatesRequest(page: Int): Request {
        val path = if (page == 1) "" else "page/$page/"
        return GET("$baseUrl/$latestUrlSlug/$path", headers)
    }

    open fun latestUpdatesSelector() = popularMangaSelector()

    open fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    open fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genreFilter = filters.firstInstanceOrNull<GenreListFilter>()
        val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val ageRatingFilter = filters.firstInstanceOrNull<AgeRatingFilter>()
        val ratingMinFilter = filters.firstInstanceOrNull<RatingMinFilter>()
        val ratingMaxFilter = filters.firstInstanceOrNull<RatingMaxFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()

        val selectedGenres = genreFilter?.state?.filter { it.state }.orEmpty()
        val selectedGenre = selectedGenres.firstOrNull()

        if (selectedGenre != null && query.isEmpty()) {
            val genreUrl = selectedGenre.url

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

        typeFilter?.state?.let { if (it > 0) urlBuilder.addQueryParameter("type", typeValues[it]) }
        statusFilter?.state?.let { if (it > 0) urlBuilder.addQueryParameter("status", statusValues[it]) }
        ageRatingFilter?.state?.let { if (it > 0) urlBuilder.addQueryParameter("age_rating", ageRatingValues[it]) }
        ratingMinFilter?.state?.let { urlBuilder.addQueryParameter("rating_min", ratingMinValues[it]) }
        ratingMaxFilter?.state?.let { urlBuilder.addQueryParameter("rating_max", ratingMaxValues[it]) }
        sortFilter?.state?.let { urlBuilder.addQueryParameter("sort", sortValues[it]) }

        val url = urlBuilder.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val peek = response.peekBody(10).string().trimStart()
        if (peek.isEmpty()) throw IOException("Empty response body")

        if (peek.startsWith("<")) {
            val document = response.asJsoup()
            if (genrelist == null) {
                genrelist = parseGenres(document)
            }
            val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }

            val hasNextPage = document.selectFirst("ul.uk-pagination li:not(#prev-link) a:not(:matchesOwn(\\S))[href^=http]") != null

            return MangasPage(mangas, hasNextPage)
        }

        val list = response.parseAs<List<Dto>>()
        val mangas = list.map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    open fun searchMangaSelector() = popularMangaSelector()

    open fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return mangaDetailsParse(document)
    }

    open fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select("div#manga-description").clone()
            .apply {
                select("a, span").remove()
            }
            .text()

        val altName = document.selectFirst("span#comic-othername")?.text()
        if (!altName.isNullOrBlank()) {
            description += "\n\nAlternatif Başlık: $altName"
        }

        genre = document.select("div.uk-flex.uk-flex-nowrap.uk-flex-left.uk-grid-small.uk-grid span.uk-label-contest").joinToString { it.text().removePrefix("#").trim() }
        if (genre.isNullOrEmpty()) {
            genre = document.select("div#genre-tags a").joinToString { it.text() }
        }

        author = document.select("div.manga-info-details:contains(Yazar) a").text()
        if (author.isNullOrEmpty()) {
            author = document.select("div.manga-info-details:contains(Yazar)").text().substringAfter("Yazar:").substringBefore("Çizer:").trim()
        }

        artist = document.select("div.manga-info-details:contains(Çizer) a").text()
        if (artist.isNullOrEmpty()) {
            artist = document.select("div.manga-info-details:contains(Çizer)").text().substringAfter("Çizer:").substringBefore("Durum:").trim()
        }

        val statusText = (
            document.selectFirst("span#manga-status, div.manga-status-ribbons span.manga-status-ribbon__text")?.text()
                ?: document.select("div.manga-info-details:contains(Durum)").text().substringAfter("Durum:")
            ).lowercase()

        status = when {
            statusText.contains("güncel") || statusText.contains("devam") || statusText.contains("ongoing") -> SManga.ONGOING
            statusText.contains("tamamland") || statusText.contains("bitti") || statusText.contains("completed") || (statusText.contains("final") && !statusText.contains("sezon")) -> SManga.COMPLETED
            statusText.contains("ara ver") || statusText.contains("sezon") || statusText.contains("hiatus") -> SManga.ON_HIATUS
            statusText.contains("bırakıldı") || statusText.contains("iptal") || statusText.contains("dropped") || statusText.contains("cancel") -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        thumbnail_url = document.selectFirst("div.story-cover-wrap img")?.absUrl("src")
            ?: document.selectFirst("div.single-thumb img")?.absUrl("src")
            ?: document.selectFirst("a.story-cover img")?.absUrl("src")

        val siteTitle = document.selectFirst("h1")?.text()
        val mangaTitle = document.selectFirst("h2.uk-h3")?.text()
        title = if (!siteTitle.isNullOrBlank()) siteTitle else mangaTitle!!
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()
        val baseUrlReq = response.request.url
        var page = 2

        do {
            val items = document.select(chapterListSelector())
            if (items.isEmpty()) break

            chapters.addAll(items.map(::chapterFromElement))

            val nextUrl = baseUrlReq.newBuilder()
                .addPathSegment("bolum")
                .addPathSegment("page")
                .addPathSegment(page.toString())
                .build()

            page++

            document = client.newCall(GET(nextUrl, headers)).execute().use { nextResponse ->
                nextResponse.asJsoup()
            }

            val hasNextPage = document.selectFirst("ul.uk-pagination a:not(:matchesOwn(\\S))[href^=http]") != null
        } while (hasNextPage)

        return chapters
    }

    open fun chapterListSelector() = "div.chapter-item"

    open fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))

        val rawName = element.select("h3").text()

        name = rawName.substringAfterLast("–").substringAfterLast("-").trim()
        if (name.isBlank()) {
            name = rawName
        }

        val dateStr = element.select("time").attr("datetime")
        date_upload = fallbackDateFormatter.tryParse(dateStr)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return pageListParse(document)
    }

    open fun pageListParse(document: Document): List<Page> {
        val encryptedData = document.selectFirst("script[src*=dmFyIElua]")?.attr("src")
            ?.substringAfter("base64,")
            ?.substringBeforeLast("\"")

        // RagnarScans

        if (encryptedData.isNullOrEmpty()) {
            runCatching {
                val content = AesDecrypt.REGEX_ENCRYPTED_DATA.find(document.html())?.groupValues?.get(1)
                if (content != null) {
                    val encryptedObject = content.parseAs<JsonObject>()
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
            val encryptedObject = jsonString.parseAs<JsonObject>()

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
                val finalSrc = img.absUrl("data-src")
                    .ifEmpty { img.absUrl("src") }
                    .ifEmpty { img.absUrl("data-lazy-src") }

                if (finalSrc.isBlank()) null else Page(i, imageUrl = finalSrc)
            }
        } else {
            runCatching {
                trimmed.parseAs<JsonArray>().mapIndexed { i, el ->
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

    private fun fallbackPages(document: Document): List<Page> = document.select("div#chapter-content img[src]").mapIndexed { i, img ->
        val src = img.absUrl("src").ifEmpty { img.absUrl("data-src") }
        Page(i, imageUrl = src)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
