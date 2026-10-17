package eu.kanade.tachiyomi.extension.tr.mangatr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.JSON_MEDIA_TYPE
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

@Source
abstract class MangaTR : KeiSource() {

    override fun Headers.Builder.configureHeaders() = add("Accept-Language", "en-US,en;q=0.5")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::verifyChallengeInterceptor)
        addInterceptor(::scrambledImageInterceptor)
        addInterceptor(DDoSGuardInterceptor(network.client))
        rateLimit(2)
    }

    // The chapter list endpoint mislabels its response as Brotli; request an identity
    // encoding so the shared decompression interceptor doesn't choke on it.
    private val ajaxHeaders get() = headersBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .add("Accept-Encoding", "identity")
        .build()

    private var captchaUrl: String? = null

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, "$baseUrl/manga-list-sayfala.html?sort=views&sort_type=DESC&page=$page&listType=pagination")

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, "$baseUrl/manga-list-sayfala.html?sort=last_update&sort_type=DESC&page=$page&listType=pagination")

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/arama.html".toHttpUrl().newBuilder()
                .addQueryParameter("icerik", query)
                .addQueryParameter("page", page.toString())
                .build()
            val document = client.get(url).asJsoup()

            val mangas = document.select("div.arama-card")
                .filterNot { it.selectFirst(".arama-badge")?.text()?.lowercase(Locale.ROOT)?.isExcludedType() == true }
                .map {
                    SManga.create().apply {
                        val link = it.selectFirst("a.arama-card__title")!!
                        setUrlWithoutDomain(link.absUrl("href"))
                        title = link.text()
                        thumbnail_url = it.selectFirst("img.arama-card__cover")?.absUrl("src")
                    }
                }
            return MangasPage(mangas, document.hasNextPage(page))
        }

        val url = "$baseUrl/manga-list-sayfala.html".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("listType", "pagination")

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.toUriPart())
                is SortDirectionFilter -> url.addQueryParameter("sort_type", filter.toUriPart())
                is GenreFilter -> filter.toUriPart().takeIf { it.isNotEmpty() }?.let { url.addQueryParameter("tur", it) }
                is StatusFilter -> filter.toUriPart().takeIf { it.isNotEmpty() }?.let { url.addQueryParameter("durum", it) }
                is TranslationStatusFilter -> filter.toUriPart().takeIf { it.isNotEmpty() }?.let { url.addQueryParameter("ceviri", it) }
                is AgeFilter -> filter.toUriPart().takeIf { it.isNotEmpty() }?.let { url.addQueryParameter("yas", it) }
                is ContentTypeFilter -> filter.toUriPart().takeIf { it.isNotEmpty() }?.let { url.addQueryParameter("icerik", it) }
                else -> {}
            }
        }

        return getMangaList(page, url.build())
    }

    private suspend fun getMangaList(page: Int, url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()

        val mangas = document.select("div.media-card")
            .filterNot { it.selectFirst(".media-card__badge")?.text()?.lowercase(Locale.ROOT)?.isExcludedType() == true }
            .map {
                SManga.create().apply {
                    val link = it.selectFirst("a.media-card__title")!!
                    setUrlWithoutDomain(link.absUrl("href"))
                    title = link.text()
                    thumbnail_url = it.selectFirst("img.media-card__cover")?.absUrl("src")
                }
            }

        return MangasPage(mangas, document.hasNextPage(page))
    }

    private suspend fun getMangaList(page: Int, url: String) = getMangaList(page, url.toHttpUrl())

    private fun String.isExcludedType() = contains("novel") || contains("anime")

    private fun Document.hasNextPage(currentPage: Int) = select("a[href*=page=]").any {
        val pageNum = it.absUrl("href").toHttpUrlOrNull()?.queryParameter("page")?.toIntOrNull()
        pageNum != null && pageNum > currentPage
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        SortDirectionFilter(),
        GenreFilter(),
        StatusFilter(),
        TranslationStatusFilter(),
        AgeFilter(),
        ContentTypeFilter(),
    )

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = captchaUrl?.also { captchaUrl = null } ?: super.getMangaUrl(manga)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host != "www.${baseUrl.toHttpUrl().host}") return null
        val path = url.pathSegments.singleOrNull() ?: return null
        if (!path.startsWith("manga-") || !path.endsWith(".html")) return null

        val document = client.get("$baseUrl/$path").asJsoup()
        return document.mangaDetails().apply { this.url = "/$path" }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        val details = document.mangaDetails()
        val chapterList = if (fetchChapters) getChapterList(document) else chapters
        return SMangaUpdate(details, chapterList)
    }

    private fun Document.mangaDetails(): SManga = SManga.create().apply {
        title = selectFirst("h1.bento-hero-title")!!.text()
        thumbnail_url = selectFirst(".poster-card__image")?.absUrl("src")

        val altNames = selectFirst("h1.bento-hero-title + div:not(.bento-hero-meta)")?.textOrNull()
        description = buildString {
            selectFirst("#manga-desc-content")?.wholeText()?.trim()?.let { append(it) }
            if (altNames != null) {
                if (isNotEmpty()) append("\n\n")
                append("Alternatif İsimler: ").append(altNames)
            }
        }

        author = infoRow("Yazar")
        artist = infoRow("Sanatçı")
        genre = select(".bento-hero-genres a").joinToString { it.text() }

        val statusText = infoRow("Durum")?.lowercase(Locale.ROOT)
        status = when {
            statusText == null -> SManga.UNKNOWN
            statusText.contains("devam") -> SManga.ONGOING
            statusText.contains("tamamlan") -> SManga.COMPLETED
            statusText.contains("bırak") || statusText.contains("iptal") -> SManga.CANCELLED
            statusText.contains("askı") -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.infoRow(label: String): String? = selectFirst(".bento-info-row:has(.bento-info-label:contains($label)) .bento-info-value")?.textOrNull()

    // ============================= Chapters ==============================

    private suspend fun getChapterList(document: Document): List<SChapter> {
        // The chapter list endpoint only accepts a signed key embedded in the series page
        val key = CHAPTER_KEY_REGEX.find(document.html())?.groupValues?.get(1)
            ?: throw Exception("Bölüm listesi anahtarı bulunamadı")

        val chapters = mutableListOf<SChapter>()
        var offset: Int? = null

        while (true) {
            val form = FormBody.Builder()
                .add("chapter_list_key", key)
                .apply { offset?.let { add("offset", it.toString()) } }
                .build()
            val response = client.post("$baseUrl/cek/fetch_pages_manga.php", ajaxHeaders, form)
            // the chapter hrefs are relative, but this endpoint lives under /cek/
            val elements = response.asJsoup()
                .also { it.setBaseUri("$baseUrl/") }
                .select("article.bento-ep-card")
            if (elements.isEmpty()) break

            elements.mapTo(chapters, ::chapterFromElement)

            // The site loads 20 entries initially, then 100 per "load more" request
            offset = (offset ?: 0) + if (offset == null) 20 else 100
        }

        return chapters
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val link = element.selectFirst("a.bento-ep-title-link")!!
        setUrlWithoutDomain(link.absUrl("href"))

        val number = link.selectFirst(".bento-ep-chapter-num")?.text()?.removeSuffix(".")
        val subtitle = element.selectFirst(".bento-ep-subtitle")?.textOrNull()
        name = listOfNotNull("Bölüm $number", subtitle).joinToString(" - ")
        chapter_number = number?.toFloatOrNull() ?: -1f
        date_upload = parseRelativeDate(element.selectFirst(".bento-ep-meta-time")?.text())
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(baseUrl + chapter.url)
        val document = response.asJsoup()

        if (document.selectFirst("div#uyari:contains(üye girişi)") != null) {
            throw IOException("Bu bölümü okuyabilmek için WebView üzerinden üye girişi yapmanız gerekmektedir.")
        }

        if (document.selectFirst("canvas#sliderCanvas, div.box h2:contains(Güvenlik Doğrulaması), div.cf-turnstile") != null) {
            captchaUrl = response.request.url.toString()
            throw IOException("Lütfen WebView'da Bot Korumasını geçin.")
        }

        val configScript = document.selectFirst("script[type=application/json][id^=rdm-]")
            ?: throw Exception("Sayfa verisi bulunamadı")
        val pageKey = configScript.attributes().firstNotNullOf { it.value.takeIf(HEX_KEY_REGEX::matches) }
        val gatePath = GATE_PATH_REGEX.find(document.html())?.groupValues?.get(1)
            ?: throw Exception("Sayfa verisi bulunamadı")

        // The gate token is single-use and its response key unlocks the config for this page load only
        val gateKey = client.post("$baseUrl/$gatePath", "{}".toRequestBody(JSON_MEDIA_TYPE))
            .parseAs<ReaderGateDto>().k
        val config = configScript.data()
            .decrypt("gate|$gateKey|reader")
            .decrypt("boot|$pageKey|reader")
            .parseAs<ReaderConfigDto>().data

        return document.select("[${config.parts}]")
            .sortedBy { it.attr(config.pageIndex).toInt() }
            .mapIndexed { index, element ->
                val imageUrl = element.attr(config.parts).decrypt("attr|$pageKey|reader").parseAs<List<String>>().first()
                val order = element.attrOrNull(config.order)?.decrypt("order|$pageKey|reader")?.parseAs<List<Int>>()
                val url = if (order.isNullOrEmpty()) {
                    imageUrl
                } else {
                    imageUrl.toHttpUrl().newBuilder().fragment(order.joinToString(",")).toString()
                }
                Page(index, imageUrl = url)
            }
    }

    private fun String.decrypt(key: String): String {
        val bytes = Base64.decode(replace('-', '+').replace('_', '/'), Base64.DEFAULT)
        for (i in bytes.indices) {
            bytes[i] = (bytes[i].toInt() xor key[i % key.length].code).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }

    // ============================= Utilities =============================

    /**
     * Images are served as N horizontal strips in shuffled order. Each entry of the fragment is
     * `flip * 100 + destination` for the source strip at that index, where flip bit 1 mirrors
     * horizontally and bit 2 mirrors vertically.
     */
    private fun scrambledImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val order = request.url.fragment
            ?.takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() || c == ',' } }
            ?.split(',')
            ?.map(String::toInt)
            ?: return response

        val source = response.use {
            BitmapFactory.decodeStream(it.body.byteStream())
        } ?: throw IOException("Resim çözülemedi")

        val stripHeight = source.height / order.size
        val output = Bitmap.createBitmap(source.width, stripHeight * order.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        order.forEachIndexed { sourceIndex, code ->
            val flip = code / 100
            val destination = code % 100
            val src = Rect(0, sourceIndex * stripHeight, source.width, (sourceIndex + 1) * stripHeight)
            val top = destination * stripHeight
            val dst = Rect(0, top, source.width, top + stripHeight)

            canvas.save()
            canvas.scale(
                if (flip and 1 != 0) -1f else 1f,
                if (flip and 2 != 0) -1f else 1f,
                source.width / 2f,
                top + stripHeight / 2f,
            )
            canvas.drawBitmap(source, src, dst, null)
            canvas.restore()
        }
        source.recycle()

        val buffer = Buffer().apply {
            output.compress(Bitmap.CompressFormat.JPEG, 95, outputStream())
        }
        output.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/jpeg".toMediaType()))
            .build()
    }

    private fun verifyChallengeInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 200 && response.header("Content-Type")?.contains("text/html") == true) {
            val bodyString = response.peekBody(2048).string()

            if (bodyString.contains("/cek/verify.php")) {
                val challenge = CHALLENGE_REGEX.find(bodyString)?.groupValues?.get(1)
                if (challenge != null) {
                    val verifyUrl = request.url.newBuilder().encodedPath("/cek/verify.php").build()
                    val verifyRequest = request.newBuilder()
                        .url(verifyUrl)
                        .post(ChallengeRequestDto(challenge).toJsonRequestBody())
                        .header("Accept", "application/json")
                        .header("Referer", request.url.toString())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()

                    network.client.newCall(verifyRequest).execute().close()

                    response.close()
                    return chain.proceed(request)
                }
            }
        }
        return response
    }

    private fun parseRelativeDate(dateString: String?): Long {
        if (dateString == null) return 0L
        val text = dateString.lowercase(Locale.ROOT)
        val number = NUMBER_REGEX.find(text)?.value?.toLongOrNull() ?: return 0L
        val unit = when {
            text.contains("saniye") -> ChronoUnit.SECONDS
            text.contains("dakika") || text.contains("dk") -> ChronoUnit.MINUTES
            text.contains("saat") -> ChronoUnit.HOURS
            text.contains("gün") -> ChronoUnit.DAYS
            text.contains("hafta") -> ChronoUnit.WEEKS
            text.contains("ay") -> ChronoUnit.MONTHS
            text.contains("yıl") -> ChronoUnit.YEARS
            else -> return 0L
        }
        return ZonedDateTime.now().minus(number, unit).toInstant().toEpochMilli()
    }

    companion object {
        private val NUMBER_REGEX = Regex("""\d+""")
        private val CHALLENGE_REGEX = Regex("""challenge:\s*"([^"]+)"""")
        private val CHAPTER_KEY_REGEX = Regex("""listKey:\s*'([^']+)'""")
        private val GATE_PATH_REGEX = Regex("""_fpx\s*=\s*"([^"]+)"""")
        private val HEX_KEY_REGEX = Regex("""[0-9a-f]{32}""")
    }
}
