package eu.kanade.tachiyomi.extension.vi.moetruyen

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MoeTruyen : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(imgxInterceptor())
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get(baseUrl).asJsoup()
            .select("ol.homepage-ranking-list[data-ranking-period=total] a.homepage-ranking-item__link")
            .map(::popularMangaFromElement)

        return MangasPage(mangas, false)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        val titleElement = element.selectFirst(".homepage-ranking-item__title")!!
        val titleAttr = titleElement.attr("title")
        title = titleAttr.ifEmpty { titleElement.text() }
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        return parseMangaList(client.get(url).asJsoup())
    }

    private fun latestMangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("a[href^=/manga/]")!!
        setUrlWithoutDomain(linkElement.absUrl("href"))
        title = getFullListTitle(element)
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    private fun getFullListTitle(element: Element): String {
        val titleElement = element.selectFirst("h3")!!
        val titleAttr = titleElement.attr("title")
        if (titleAttr.isNotEmpty()) {
            return titleAttr
        }

        val titleText = titleElement.text()
        if (!titleText.endsWith("...")) {
            return titleText
        }

        val imageAlt = element.selectFirst("img")?.attr("alt")
            ?.removePrefix("Bìa ")
            ?.trim()
            ?.ifEmpty { null }

        return imageAlt ?: titleText
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("article.manga-card--list")
            .map(::latestMangaFromElement)

        val hasNextPage = document
            .selectFirst("nav[aria-label='Phân trang truyện'] a[aria-label='Trang sau']:not(.is-disabled)")
            ?.attr("href")
            ?.let { it != "#" }
            ?: false

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()
        val includedGenres = filters.firstInstanceOrNull<GenreFilter>()
            ?.state
            ?.filter { it.state }
            .orEmpty()
        val hasFilter = status != null || includedGenres.isNotEmpty()

        if (query.isBlank() && !hasFilter) {
            return getLatestUpdates(page)
        }

        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("q", query)
                }

                status?.let { addQueryParameter("status", it) }
                includedGenres.forEach { addQueryParameter("include", it.id) }
            }
            .build()

        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply { setUrlWithoutDomain("/manga/$slug") }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    // ============================== Details ===============================

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h1.manga-detail-title")!!.text()
        author = document.select("p.manga-detail-meta-line")
            .firstOrNull { line ->
                line.selectFirst(".manga-detail-meta-label")
                    ?.text()
                    ?.contains("Tác giả")
                    ?: false
            }
            ?.select("a.inline-link")
            ?.joinToString { it.text() }
            ?.ifEmpty { null }
        genre = document.select(".manga-detail-genre-chips a.chip")
            .joinToString { it.text() }
            .ifEmpty { null }
        description = document.selectFirst("[data-description-content]")
            ?.text()
            ?.ifEmpty { null }
            ?: document.selectFirst(".manga-description__text")
                ?.text()
                ?.ifEmpty { null }
        status = parseStatus(document.selectFirst(".manga-status-pill")?.text())
        thumbnail_url = document.selectFirst(".detail-cover img")?.absUrl("src")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = if (fetchChapters) fetchChapterList(document) else chapters,
        )
    }

    private fun parseStatus(status: String?): Int = when (status) {
        "Còn tiếp" -> SManga.ONGOING
        "Hoàn thành" -> SManga.COMPLETED
        "Tạm dừng" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private suspend fun fetchChapterList(firstDocument: Document): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val visitedPages = mutableSetOf<String>()
        var currentPageUrl = firstDocument.location()
        var currentDocument = firstDocument

        while (visitedPages.add(currentPageUrl)) {
            chapters += parseChapterList(currentDocument)

            val nextChapterLinkElement: Element? = currentDocument.selectFirst(
                "nav[aria-label*='Phân trang chương'] a[aria-label='Trang chương sau']:not(.is-disabled)",
            )
            val nextChapterPageUrl: String? = nextChapterLinkElement?.let { link ->
                if (link.attr("href") == "#") {
                    null
                } else {
                    link.absUrl("href").ifEmpty { null }
                }
            }

            if (nextChapterPageUrl == null || visitedPages.contains(nextChapterPageUrl)) {
                break
            }

            currentPageUrl = nextChapterPageUrl
            currentDocument = client.get(currentPageUrl).asJsoup()
        }

        return chapters
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("ul.chapter-list li.chapter a.chapter-link").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst(".chapter-num")!!.text()

            val chapterTime = element.selectFirst(".chapter-time")
            val relativeDate = chapterTime?.text()
            val absoluteDate = chapterTime?.attr("title")
                ?.substringAfter("Cập nhật", missingDelimiterValue = "")
                ?.trim()
                ?.ifEmpty { null }

            date_upload = parseRelativeDate(relativeDate).takeIf { it != 0L }
                ?: parseAbsoluteDate(absoluteDate)
        }
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val number = numberRegex.find(dateStr)?.value?.toIntOrNull() ?: return 0L
        val duration = when {
            dateStr.contains("giây") -> number.seconds
            dateStr.contains("phút") -> number.minutes
            dateStr.contains("giờ") -> number.hours
            dateStr.contains("ngày") -> number.days
            dateStr.contains("tuần") -> (number * 7).days
            dateStr.contains("tháng") -> (number * 30).days
            dateStr.contains("năm") -> (number * 365).days
            else -> return 0L
        }

        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    private fun parseAbsoluteDate(date: String?): Long {
        if (date == null) return 0L
        return runCatching {
            LocalDate.parse(date, dateFormat)
                .atStartOfDay(dateZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        val images = document.select("img.page-media")
            .filterNot { element ->
                element.parents().any { parent -> parent.tagName().equals("noscript", ignoreCase = true) }
            }
        val readerPages = document.selectFirst("[data-reader-lazy-pages]")

        val accessUrl = images.firstOrNull()?.attr("data-imgx-access-url")?.ifBlank { null }
            ?: readerPages?.attr("data-reader-imgx-access-url")?.ifBlank { null }

        if (accessUrl != null) {
            val fullAccessUrl = if (accessUrl.startsWith("http")) accessUrl else "$baseUrl$accessUrl"
            val proofToken = readerPages
                ?.attr("data-reader-imgx-proof-token")
                ?.ifBlank { null }

            return fetchPagesWithGrants(fullAccessUrl, images.size, proofToken)
        }

        return images
            .asSequence()
            .map { element ->
                element.absUrl("data-src").ifEmpty { element.absUrl("src") }
            }
            .filter { imageUrl ->
                imageUrl.isNotBlank() && !imageUrl.startsWith("data:")
            }
            .distinct()
            .toList()
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    private suspend fun fetchPagesWithGrants(accessUrl: String, pageCount: Int, proofToken: String?): List<Page> {
        val pages = mutableListOf<Page>()
        val batchSize = 5

        for (start in 0 until pageCount step batchSize) {
            val end = minOf(start + batchSize, pageCount)
            val indices = (start until end).toList()
            val proof = proofToken?.let { createPageAccessProof(accessUrl, indices, it) }
            val body = PageAccessRequest(pageIndexes = indices, pageAccessProof = proof).toJsonRequestBody()
            val accessHeaders = headers.newBuilder()
                .set("Accept", "application/json")
                .apply {
                    proof?.let {
                        set("X-IMGX-Reader-Proof", it.proof)
                        set("X-IMGX-Reader-Proof-Version", it.version)
                    }
                }
                .build()

            val pageAccess = client.post(accessUrl, accessHeaders, body).parseAs<PageAccessResponse>()

            for (entry in pageAccess.pages) {
                if (entry.downloadUrl.isNotBlank() && entry.grant != null) {
                    imgxGrants[entry.downloadUrl] = entry
                    pages.add(Page(entry.pageIndex, imageUrl = entry.downloadUrl))
                }
            }
        }

        return pages.sortedBy { it.index }
    }

    private fun createPageAccessProof(accessUrl: String, pageIndexes: List<Int>, token: String): PageAccessProof {
        val version = "imgx-page-access-proof-v1"
        val issuedAt = System.currentTimeMillis()
        val nonce = randomHex(16)
        val accessPath = accessUrl.toHttpUrl().encodedPath
        val pageIndexPart = pageIndexes.joinToString(",")
        val proofInput = listOf(
            version,
            token,
            accessPath,
            "",
            pageIndexPart,
            issuedAt.toString(),
            nonce.lowercase(Locale.ROOT),
        ).joinToString("\n")

        val proof = MessageDigest.getInstance("SHA-256")
            .digest(proofInput.toByteArray(Charsets.UTF_8))
            .base64UrlNoPadding()

        return PageAccessProof(
            version = version,
            token = token,
            issuedAt = issuedAt,
            nonce = nonce,
            proof = proof,
        )
    }

    private fun randomHex(size: Int): String {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun ByteArray.base64UrlNoPadding(): String = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun imgxInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val entry = imgxGrants.remove(url)

        if (entry?.grant == null) {
            return@Interceptor chain.proceed(request)
        }

        val response = chain.proceed(request)
        val source = response.body.source()

        if (!source.request(14) ||
            source.buffer[0] != 0x49.toByte() || source.buffer[1] != 0x4D.toByte() ||
            source.buffer[2] != 0x47.toByte() || source.buffer[3] != 0x58.toByte()
        ) {
            return@Interceptor response
        }

        val webp = response.body.use {
            ImageDecryptor.decrypt(source.readByteArray(), entry.grant, entry.storageKey)
        }

        response.newBuilder()
            .body(webp.toResponseBody("image/webp".toMediaType()))
            .build()
    }

    private fun DecryptedImage.toResponseBody(mediaType: MediaType): ResponseBody = object : ResponseBody() {
        override fun contentType(): MediaType = mediaType

        override fun contentLength(): Long = size.toLong()

        override fun source(): BufferedSource = ByteArrayInputStream(data, offset, size).source().buffer()
    }

    private val imgxGrants = Collections.synchronizedMap(
        object : LinkedHashMap<String, PageAccessEntry>(IMGX_GRANT_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PageAccessEntry>?): Boolean = size > IMGX_GRANT_CACHE_SIZE
        },
    )

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/manga").asJsoup()
        .select(".filter-option[data-genre]")
        .mapNotNull { element ->
            val id = element.attr("data-genre").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = element.selectFirst(".filter-name")?.text()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            GenreOption(name, id)
        }
        .distinctBy { it.id }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        val section = document.selectFirst("section[aria-labelledby=manga-related-similar-title]")
            ?: return emptyList()

        return section.select("article.manga-related-card").mapNotNull { card ->
            val link = card.selectFirst("a.manga-related-card__link[href^=/manga/]")
                ?: return@mapNotNull null
            val title = card.selectFirst("h3")?.text()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }.distinctBy { it.url }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val numberRegex = Regex("""\d+""")
    private val secureRandom = SecureRandom()

    private companion object {
        const val IMGX_GRANT_CACHE_SIZE = 500
    }
}
