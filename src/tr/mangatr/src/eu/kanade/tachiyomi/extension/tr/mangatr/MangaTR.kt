package eu.kanade.tachiyomi.extension.tr.mangatr

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import rx.Observable
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.Locale

class MangaTR : HttpSource() {

    override val name = "Manga-TR"
    override val baseUrl = "https://manga-tr.com"
    override val lang = "tr"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept-Language", "en-US,en;q=0.5")

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::verifyChallengeInterceptor)
        .addInterceptor(::coverInterceptor)
        .addInterceptor(DDoSGuardInterceptor(network.cloudflareClient))
        .rateLimit(2)
        .build()

    private var captchaUrl: String? = null

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-list-sayfala.html?sort=views&sort_type=DESC&page=$page&listType=pagination", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga-list-sayfala.html?sort=last_update&sort_type=DESC&page=$page&listType=pagination", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/arama.html".toHttpUrl().newBuilder()
                .addQueryParameter("icerik", query)
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/manga-list-sayfala.html".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("listType", "pagination")

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())
                is SortDirectionFilter -> url.addQueryParameter("sort_type", filter.toUriPart())
                is GenreFilter -> {
                    val value = filter.toUriPart()
                    if (value.isNotEmpty()) url.addQueryParameter("tur", value)
                }
                is StatusFilter -> {
                    val value = filter.toUriPart()
                    if (value.isNotEmpty()) url.addQueryParameter("durum", value)
                }
                is TranslationStatusFilter -> {
                    val value = filter.toUriPart()
                    if (value.isNotEmpty()) url.addQueryParameter("ceviri", value)
                }
                is AgeFilter -> {
                    val value = filter.toUriPart()
                    if (value.isNotEmpty()) url.addQueryParameter("yas", value)
                }
                is ContentTypeFilter -> {
                    val value = filter.toUriPart()
                    if (value.isNotEmpty()) url.addQueryParameter("icerik", value)
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val path = response.request.url.encodedPath

        if (path.contains("/arama.html")) {
            val mangas = document.select("div.arama-manga-list a.arama-manga-item")
                .filterNot {
                    val badges = it.select("span.la-badge").text().lowercase(Locale.ROOT)
                    badges.contains("novel") || badges.contains("anime")
                }
                .mapNotNull {
                    val mangaTitle = it.selectFirst(".arama-manga-name")?.text() ?: it.text()
                    if (mangaTitle.isEmpty()) return@mapNotNull null

                    SManga.create().apply {
                        setUrlWithoutDomain(it.absUrl("href"))
                        title = mangaTitle

                        // Fake URL for interceptor to catch and fetch dynamically
                        // so it doesn't block the UI while parsing search entries
                        val slug = it.attr("manga-slug")
                        if (slug.isNotBlank()) {
                            thumbnail_url = "$baseUrl/fake-cover/$slug"
                        }
                    }
                }
            return MangasPage(mangas, false)
        }

        val mangas = document.select("div.media-card")
            .filterNot {
                val badge = it.selectFirst(".media-card__badge")?.text()?.lowercase(Locale.ROOT).orEmpty()
                badge.contains("novel") || badge.contains("anime")
            }
            .mapNotNull {
                val titleLink = it.selectFirst("a.media-card__title, a.media-card__cover-link") ?: return@mapNotNull null
                val mangaTitle = it.selectFirst("a.media-card__title")?.text() ?: return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(titleLink.absUrl("href"))
                    title = mangaTitle
                    thumbnail_url = it.selectFirst("img.media-card__cover")?.absUrl("src")
                }
            }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = document.select(".pagination-wrap a.pagination-link").any {
            val href = it.absUrl("href")
            val pageNum = href.toHttpUrlOrNull()?.queryParameter("page")?.toIntOrNull()
            pageNum != null && pageNum > currentPage
        }

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = captchaUrl?.also { captchaUrl = null } ?: super.getMangaUrl(manga)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()

        title = document.selectFirst("h1")?.text()?.replace(YEAR_REGEX, "") ?: throw Exception("Manga title not found")
        thumbnail_url = document.selectFirst(".poster-card__image")?.absUrl("src")

        val descBlock = document.selectFirst("#manga-description, .detail-copy")?.text()
        val altNames = document.selectFirst(".detail-hero__sub")?.text()
        description = buildString {
            if (!descBlock.isNullOrEmpty()) append(descBlock)
            if (!altNames.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternatif İsimler: ")
                append(altNames)
            }
        }

        author = document.select(".detail-meta-row:contains(Yazar) .detail-meta-row__value a")
            .joinToString { it.text() }
        artist = document.select(".detail-meta-row:contains(Sanatçı) .detail-meta-row__value a")
            .joinToString { it.text() }
        genre = document.select(".detail-meta-row:contains(Tür) .detail-meta-row__value a")
            .joinToString { it.text() }

        val statusText = document.selectFirst(".detail-meta-row:contains(Yayın durumu) .detail-meta-row__value")?.text()?.lowercase(Locale.ROOT)
        status = when {
            statusText?.contains("devam") == true -> SManga.ONGOING
            statusText?.contains("tamamlan") == true -> SManga.COMPLETED
            statusText?.contains("bırak") == true || statusText?.contains("iptal") == true -> SManga.CANCELLED
            statusText?.contains("askı") == true -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================= Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val chapters = mutableListOf<SChapter>()
        val id = manga.url.substringAfter("manga-").substringBefore(".html")
        var nextPage = 1

        val chapterHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Referer", baseUrl + manga.url)
            .build()

        while (true) {
            val requestUrl = "$baseUrl/cek/fetch_pages_manga.php?manga_cek=$id&page=$nextPage"
            val response = client.newCall(GET(requestUrl, chapterHeaders)).execute()
            val doc = response.asJsoup()

            doc.setBaseUri(baseUrl)

            val elements = doc.select("article.chapter-card")
            if (elements.isEmpty()) break

            chapters.addAll(
                elements.map { element ->
                    SChapter.create().apply {
                        val row = element.selectFirst("a.chapter-card__row") ?: element.selectFirst("a.chapter-card__title")!!
                        setUrlWithoutDomain(row.absUrl("href"))

                        val chapterNumText = row.selectFirst(".chapter-number")?.text()?.removeSuffix(".")
                            ?: row.selectFirst(".chapter-title span")?.text()
                            ?: "Bölüm"

                        val sub = element.selectFirst("p.chapter-card__subtitle")?.text()

                        name = if (!sub.isNullOrEmpty()) {
                            "Bölüm $chapterNumText - $sub"
                        } else {
                            "Bölüm $chapterNumText"
                        }

                        val dateText = element.selectFirst(".chapter-card__meta span")?.text()
                        date_upload = parseRelativeDate(dateText)
                    }
                },
            )

            val hasNext = doc.selectFirst("nav.pagination-wrap a.pagination-link[data-page=${nextPage + 1}]") != null
            if (!hasNext) break
            nextPage++
        }

        chapters
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used.")

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        // Check if chapter requires login
        if (document.selectFirst("div#uyari:contains(üye girişi)") != null) {
            throw IOException("Bu bölümü okuyabilmek için WebView üzerinden üye girişi yapmanız gerekmektedir.")
        }

        // Workaround for unsolved DDOS-Guard/Captcha
        if (document.selectFirst("canvas#sliderCanvas, div.box h2:contains(Güvenlik Doğrulaması), div.cf-turnstile") != null) {
            captchaUrl = response.request.url.toString()
            throw IOException("Lütfen WebView'da Bot Korumasını geçin.")
        }

        val pages = mutableListOf<Page>()
        val chapterPages = document.select("div.chapter-page")

        if (chapterPages.isNotEmpty()) {
            val sortedChapterPages = chapterPages
                .filter { it.hasAttr("data-parts") && it.hasAttr("data-order") }
                .sortedBy { it.attr("data-page-index").toIntOrNull() ?: Int.MAX_VALUE }

            for (page in sortedChapterPages) {
                val partsJson = page.attr("data-parts")
                val orderAttr = page.attr("data-order")

                val urls: List<String> = runCatching {
                    partsJson.parseAs<List<String>>()
                }.getOrElse { emptyList() }

                if (urls.isEmpty()) continue

                val mapping = decodePartOrderMapping(orderAttr)
                if (mapping.isNullOrEmpty()) {
                    pages.add(Page(pages.size, imageUrl = urls.first()))
                    continue
                }

                val sortedUrls = mapping
                    .sortedBy { it.second }
                    .mapNotNull { (partIdx, _) -> urls.getOrNull(partIdx) }

                if (sortedUrls.isEmpty()) {
                    pages.add(Page(pages.size, imageUrl = urls.first()))
                    continue
                }

                for (url in sortedUrls) {
                    pages.add(Page(pages.size, imageUrl = url))
                }
            }

            if (pages.isNotEmpty()) return pages
        }

        val directImages = document.select("img[src*='img_part.php'], img[data-src*='img_part.php']")
        if (directImages.isNotEmpty()) {
            return directImages.mapIndexed { index, img ->
                val src = img.absUrl("src").ifEmpty { img.absUrl("data-src") }
                Page(index, imageUrl = src)
            }
        }

        val html = document.html()
        val seenKeys = mutableSetOf<String>()

        val regexPages = IMG_URL_REGEX.findAll(html)
            .map { it.value.replace("&amp;", "&") }
            .filterNot { it.contains("logo") }
            .filter { url ->
                val keyMatch = KEY_REGEX.find(url)
                val key = keyMatch?.groupValues?.get(1) ?: return@filter false
                if (seenKeys.contains(key)) {
                    false
                } else {
                    seenKeys.add(key)
                    true
                }
            }
            .mapIndexed { idx, url -> Page(idx, imageUrl = url) }
            .toList()

        return regexPages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        SortDirectionFilter(),
        GenreFilter(),
        StatusFilter(),
        TranslationStatusFilter(),
        AgeFilter(),
        ContentTypeFilter(),
    )

    // ============================= Utilities =============================

    private fun verifyChallengeInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 200 && response.header("Content-Type")?.contains("text/html") == true) {
            val responseBody = response.peekBody(2048)
            val bodyString = responseBody.string()

            if (bodyString.contains("challenge: \"") && bodyString.contains("/cek/verify.php")) {
                val challengeMatch = CHALLENGE_REGEX.find(bodyString)
                if (challengeMatch != null) {
                    val challenge = challengeMatch.groupValues[1]
                    val verifyUrl = request.url.newBuilder().encodedPath("/cek/verify.php").build()
                    val verifyRequest = request.newBuilder()
                        .url(verifyUrl)
                        .post(ChallengeRequestDto(challenge).toJsonRequestBody())
                        .header("Accept", "application/json")
                        .header("Referer", request.url.toString())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()

                    network.cloudflareClient.newCall(verifyRequest).execute().close()

                    response.close()
                    return chain.proceed(request)
                }
            }
        }
        return response
    }

    private fun coverInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.pathSegments.firstOrNull() == "fake-cover") {
            val slug = request.url.pathSegments.last()

            val popHeaders = headersBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .add("Referer", "$baseUrl/arama.html")
                .build()

            val popRequest = POST(
                "$baseUrl/app/manga/controllers/cont.pop.php",
                popHeaders,
                FormBody.Builder().add("slug", slug).build(),
            )

            val realCoverUrl = try {
                chain.proceed(popRequest).use { response ->
                    if (!response.isSuccessful) return@use null
                    response.asJsoup().selectFirst("img")?.absUrl("src")
                }
            } catch (_: Exception) {
                null
            }

            if (realCoverUrl.isNullOrEmpty()) {
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Cover not found")
                    .body("".toResponseBody("image/png".toMediaType()))
                    .build()
            }

            val realRequest = GET(realCoverUrl, request.headers)
            return chain.proceed(realRequest)
        }

        return chain.proceed(request)
    }

    /** Decodes `data-order`: Base64 then XOR 0x5A, returning a list of (partIndex → position) pairs. */
    private fun decodePartOrderMapping(encoded: String): List<Pair<Int, Int>>? {
        val raw = try {
            Base64.decode(encoded, Base64.DEFAULT)
        } catch (_: Exception) {
            return null
        }
        val decoded = ByteArray(raw.size) { i -> ((raw[i].toInt() and 0xFF) xor 0x5A).toByte() }
        val jsonStr = String(decoded, StandardCharsets.UTF_8)

        return runCatching {
            jsonStr.parseAs<List<Int>>().mapIndexed { idx, pos -> idx to pos }
        }.getOrNull()
            ?: runCatching {
                jsonStr.parseAs<Map<String, Int>>().mapNotNull { (k, v) ->
                    val partIdx = k.toIntOrNull() ?: return@mapNotNull null
                    partIdx to v
                }
            }.getOrNull()
            ?: runCatching {
                jsonStr.parseAs<List<String>>().mapIndexedNotNull { idx, pos ->
                    idx to (pos.toIntOrNull() ?: return@mapIndexedNotNull null)
                }
            }.getOrNull()
            ?: runCatching {
                jsonStr.parseAs<Map<String, String>>().mapNotNull { (k, v) ->
                    val partIdx = k.toIntOrNull() ?: return@mapNotNull null
                    val pos = v.toIntOrNull() ?: return@mapNotNull null
                    partIdx to pos
                }
            }.getOrNull()
    }

    private fun parseRelativeDate(dateString: String?): Long {
        if (dateString == null) return 0L
        val trimmed = dateString.lowercase(Locale.ROOT)
        val number = NUMBER_REGEX.find(trimmed)?.value?.toIntOrNull() ?: return 0L
        val cal = Calendar.getInstance()
        when {
            trimmed.contains("saniye") -> cal.add(Calendar.SECOND, -number)
            trimmed.contains("dakika") || trimmed.contains("dk") -> cal.add(Calendar.MINUTE, -number)
            trimmed.contains("saat") || trimmed.contains("sa") -> cal.add(Calendar.HOUR, -number)
            trimmed.contains("gün") -> cal.add(Calendar.DAY_OF_YEAR, -number)
            trimmed.contains("hafta") -> cal.add(Calendar.WEEK_OF_YEAR, -number)
            trimmed.contains("ay") -> cal.add(Calendar.MONTH, -number)
            trimmed.contains("yıl") || trimmed.contains("yil") -> cal.add(Calendar.YEAR, -number)
            else -> return 0L
        }
        return cal.timeInMillis
    }

    @Serializable
    private class ChallengeRequestDto(val challenge: String)

    companion object {
        private val YEAR_REGEX = Regex("""\s*\(\d{4}\)$""")
        private val NUMBER_REGEX = Regex("""\d+""")
        private val IMG_URL_REGEX = Regex("""https?://[^"'\s]*img_part\.php[^"'\s]*""")
        private val KEY_REGEX = Regex("""key=([^&]+)""")
        private val CHALLENGE_REGEX = Regex("""challenge:\s*"([^"]+)"""")
    }
}
