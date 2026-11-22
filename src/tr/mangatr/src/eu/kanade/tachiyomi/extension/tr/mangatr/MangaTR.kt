package eu.kanade.tachiyomi.extension.tr.mangatr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Clock

@Source
abstract class MangaTR : KeiSource() {

    override fun Headers.Builder.configureHeaders() = add("Accept-Language", "en-US,en;q=0.5")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::verifyChallengeInterceptor)
        addInterceptor(::brokenImageInterceptor)
        addInterceptor(::unscrambleInterceptor)
        addInterceptor(DDoSGuardInterceptor(network.client))
        rateLimit(10) { it.host == IMAGE_HOST }
        rateLimit(2)
    }

    // The chapter list endpoint mislabels its response as Brotli; request an identity
    // encoding so the shared decompression interceptor doesn't choke on it.
    private val ajaxHeaders get() = headersBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .add("Accept-Encoding", "identity")
        .build()

    private var captchaUrl: String? = null

    @Volatile
    private var seed: Pair<String, Int>? = null

    // ============================== Popular ==============================

    // The site reads sort_type the other way round for views and last_update: ASC is most viewed / newest first
    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, "$baseUrl/manga-list-sayfala.html?sort=views&sort_type=ASC&page=$page&listType=pagination")

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, "$baseUrl/manga-list-sayfala.html?sort=last_update&sort_type=ASC&page=$page&listType=pagination")

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/arama.html".toHttpUrl().newBuilder()
                .addQueryParameter("icerik", query)
                .addQueryParameter("page", page.toString())
                .build()
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            val mangas = document.mangaCards("div.arama-card", ".arama-badge", "a.arama-card__title", "img.arama-card__cover")
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
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.mangaCards("div.media-card", ".media-card__badge", "a.media-card__title", "img.media-card__cover")
        return MangasPage(mangas, document.hasNextPage(page))
    }

    private suspend fun getMangaList(page: Int, url: String) = getMangaList(page, url.toHttpUrl())

    private fun Document.mangaCards(card: String, badge: String, title: String, cover: String): List<SManga> = select(card)
        .filterNot { it.selectFirst(badge)?.text()?.lowercase(Locale.ROOT)?.isExcludedType() == true }
        .mapNotNull { element ->
            // Skips the nameless placeholder series ("manga-.html") the site keeps for misfiled uploads
            val link = element.selectFirst(title)?.takeIf { it.text().isNotBlank() } ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = link.text()
                thumbnail_url = element.selectFirst(cover)?.absUrl("src")
            }
        }

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
        val segment = url.pathSegments.singleOrNull() ?: return null
        // A chapter link (id-1-read-<slug>-chapter-2.html) leads to its series page
        val path = CHAPTER_URL_REGEX.matchEntire(segment)?.let { "manga-${it.groupValues[1]}.html" } ?: segment
        if (!path.startsWith("manga-") || !path.endsWith(".html")) return null

        val document = client.get("$baseUrl/$path", Fetch.DOCUMENT).asJsoup()
        return document.mangaDetails().apply {
            this.url = "/$path"
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup()
        val details = if (fetchDetails) document.mangaDetails() else manga
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
        val results = document.selectFirst("#results[data-manga]")
            ?: throw Exception("Bölüm listesi bulunamadı")
        val slug = results.attr("data-manga")
        val type = results.attr("data-tur")
        val seed = document.signatureSeed()
        val from = Fetch.XHR.from(document.location())

        val chapters = mutableListOf<SChapter>()
        val seen = HashSet<String>()
        var offset = 0
        while (true) {
            val timestamp = Clock.System.now().epochSeconds
            val form = FormBody.Builder()
                .add("manga", slug)
                .add("tur", type)
                .add("offset", offset.toString())
                .add("_t", timestamp.toString())
                .add("_sig", "${slug}_${seed}_$timestamp".sha256().toHexString())
                .build()
            val response = client.post("$baseUrl/cek/fetch_pages_manga.php", ajaxHeaders, from, form)
            // the chapter hrefs are relative, but this endpoint lives under /cek/
            val page = response.asJsoup().also { it.setBaseUri("$baseUrl/") }
            val elements = page.select("article.bento-ep-card")
            if (chapters.isEmpty() && elements.isEmpty() && page.selectFirst(".empty-state:contains(Türkçe basım)") != null) {
                throw Exception("Türkçe basımı olduğu için bu seri sitede yayımlanmıyor")
            }
            offset += elements.size
            val added = elements.map(::chapterFromElement).filter { seen.add(it.url) }
            chapters += added

            // Only the first response says whether more exist; later ones end with an empty page.
            // Stopping on a page with nothing new also guards against the offset being ignored.
            if (added.isEmpty() || page.selectFirst("#has-more-chapters")?.attr("value") == "0") break
        }

        return chapters
    }

    // The page's own script signs each chapter list request with the sum of two CSS custom
    // properties, as read through getComputedStyle; they live in the versioned site stylesheet.
    private suspend fun Document.signatureSeed(): Int {
        val cssUrl = selectFirst("link[rel=stylesheet][href*=mtr.min.css]")!!.absUrl("href")
        seed?.takeIf { it.first == cssUrl }?.let { return it.second }

        val css = client.get(cssUrl, Fetch.NONE).body.string()
        val values = SEED_REGEX.findAll(css).distinctBy { it.groupValues[1] }.toList()
        if (values.size != 2) throw Exception("Bölüm listesi imzası bulunamadı")
        return values.sumOf { it.groupValues[2].toInt() }.also { seed = cssUrl to it }
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val link = element.selectFirst("a.bento-ep-title-link")!!
        setUrlWithoutDomain(link.absUrl("href"))

        val number = link.selectFirst(".bento-ep-chapter-num")?.text()?.removeSuffix(".")
        val subtitle = element.selectFirst(".bento-ep-subtitle")?.textOrNull()
        name = listOfNotNull(number?.let { "Bölüm $it" }, subtitle).joinToString(" - ").ifEmpty { link.text() }
        chapter_number = number?.toFloatOrNull() ?: -1f
        date_upload = parseRelativeDate(element.selectFirst(".bento-ep-meta-time")?.text())
        // Green dropdown entries are the translation groups, purple ones the uploading member
        scanlator = element.select(".chapter-dropdown-content .dropdown-item-yesil")
            .map { it.text() }
            .ifEmpty { listOfNotNull(element.selectFirst(".chapter-dropdown-group-name")?.textOrNull()) }
            .distinct()
            .joinToString()
            .ifEmpty { null }
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(baseUrl + chapter.url, Fetch.DOCUMENT)
        val document = response.asJsoup()

        if (document.selectFirst("div#uyari:contains(üye girişi), body :containsOwn(Giriş Yapmalısınız)") != null) {
            throw IOException(LOGIN_REQUIRED)
        }

        if (document.selectFirst("canvas#sliderCanvas, div.box h2:contains(Güvenlik Doğrulaması), div.cf-turnstile") != null) {
            captchaUrl = response.request.url.toString()
            throw IOException("Lütfen WebView'da Bot Korumasını geçin.")
        }

        val urls = readerPageUrls(document)
        if (urls.isEmpty()) {
            throw IOException("Sayfalar bulunamadı; site okuyucusunu değiştirmiş olabilir.")
        }
        return urls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private suspend fun readerPageUrls(document: Document): List<String> {
        val scripts = document.select("script:not([src])").joinToString("\n") { it.data() }

        // The reader asks /reader/pages/ for each batch of three pages. The images are stored with their left and
        // right parts swapped; each page's tk, mixed with the page index and the chapter's seed, gives where to cut.
        val readerId = SCRAMBLE_ID_REGEX.find(scripts)?.groupValues?.get(1) ?: return emptyList()
        val seed = READER_SEED_REGEX.find(scripts)?.groupValues?.get(1).orEmpty()
        val slots = document.select(".reader-page-wrap, .reader-webtoon-wrap").size
        val from = Fetch.XHR.from(document.location())
        val headers = ajaxHeaders.newBuilder().set("Accept", "application/json").build()
        val pages = mutableListOf<RoutePage>()
        val limit = if (slots > 0) slots else MAX_PAGES
        var start = 0
        while (start < limit) {
            val url = "$baseUrl/reader/pages/".toHttpUrl().newBuilder()
                .addQueryParameter("reader", readerId)
                .addQueryParameter("start", start.toString())
                .build()
            val response = client.get(url, headers, from, ensureSuccess = slots > 0)
            // Without slots to count, the batch after the last page is a 404
            if (response.code == 404 && slots == 0) {
                response.close()
                break
            }
            val batch = response.parseAs<RouteBatch>().pages
            if (batch.isEmpty()) break
            pages += batch
            start += 3
        }
        return pages.distinctBy { it.index }.sortedBy { it.index }.map { page ->
            if (page.tk == null) page.url else "${page.url}#$CUT_FRAGMENT=${cutPoint(page.tk, page.index, seed)}"
        }
    }

    // The reader's 16-bit FNV-1a of "index:seed"; tk xor this is the left part's share of the width, in 1/10000
    private fun cutPoint(tk: Int, index: Int, seed: String): Int {
        var hash = 0x811c9dc5.toInt()
        for (char in "$index:$seed") hash = ((hash xor char.code) * 0x01000193) and 0xFFFF
        val share = tk xor hash
        return if (share in 2000..8000) share else 5000
    }

    @Serializable
    private class RouteBatch(val pages: List<RoutePage>)

    @Serializable
    private class RoutePage(val index: Int, val url: String, val tk: Int? = null)

    // ============================= Utilities =============================

    // Some pages are stored broken on the image CDN (an empty 200 or a 404); an empty one is
    // sometimes fixed by retrying. A page still broken after that gets a notice image, instead
    // of a 0-byte file the reader can't open or a download that can never finish.
    private fun brokenImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != IMAGE_HOST) return chain.proceed(request)

        var attempt = 0
        while (true) {
            // Retries must reach the CDN, not an earlier answer from the HTTP cache
            val attemptRequest = if (attempt == 0) request else request.newBuilder().cacheControl(CacheControl.FORCE_NETWORK).build()
            val response = chain.proceed(attemptRequest)
            val broken = try {
                response.code == 404 || (response.isSuccessful && response.peekBody(1).contentLength() == 0L)
            } catch (e: IOException) {
                response.close()
                throw e
            }
            if (!broken) return response
            response.close()
            if (++attempt == IMAGE_ATTEMPTS) return brokenPageResponse(request)
        }
    }

    // Puts a page's parts back in order: with "#cut=<share>", the stored image is the right part, then the left part,
    // whose width is share/10000 of the page
    private fun unscrambleInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val share = request.url.fragment?.removePrefix("$CUT_FRAGMENT=")?.toIntOrNull()
        val response = chain.proceed(request)
        if (share == null || !response.isSuccessful) return response

        val image = response.body.byteStream().use(BitmapFactory::decodeStream)
        val (width, height) = image.width to image.height
        val left = width * share / 10000
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawBitmap(image, Rect(width - left, 0, width, height), Rect(0, 0, left, height), null)
            drawBitmap(image, Rect(0, 0, width - left, height), Rect(left, 0, width, height), null)
        }
        val buffer = Buffer()
        output.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        return response.newBuilder().body(buffer.asResponseBody("image/jpeg".toMediaType())).build()
    }

    private fun brokenPageResponse(request: Request): Response {
        val bitmap = Bitmap.createBitmap(800, 1130, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textAlign = Paint.Align.CENTER
            textSize = 40f
        }
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText("Bu sayfa kaynak sitede bozuk", 400f, 535f, paint)
            drawText("ve yüklenemiyor.", 400f, 595f, paint)
        }
        val buffer = Buffer()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
        bitmap.recycle()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(buffer.asResponseBody("image/png".toMediaType()))
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
                    response.close()
                    val verifyUrl = request.url.newBuilder().encodedPath("/cek/verify.php").build()
                    val verifyRequest = request.newBuilder()
                        .url(verifyUrl)
                        .post(ChallengeRequestDto(challenge).toJsonRequestBody())
                        .header("Accept", "application/json")
                        .header("Referer", request.url.toString())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .fetch(Fetch.XHR)
                        .build()

                    network.client.newCall(verifyRequest).execute().close()
                    return chain.proceed(request)
                }
            }
        }
        return response
    }

    // Ages are shown compactly ("6y", "3a", "2h"); older markup spelled them out ("3 gün önce")
    private fun parseRelativeDate(dateString: String?): Long {
        val match = dateString?.lowercase(TURKISH)?.let(RELATIVE_DATE_REGEX::find) ?: return 0L
        val number = match.groupValues[1].toLong()
        val unitText = match.groupValues[2]
        val unit = when {
            unitText.startsWith("sn") || unitText.startsWith("san") -> ChronoUnit.SECONDS
            unitText == "d" || unitText.startsWith("dk") || unitText.startsWith("dak") -> ChronoUnit.MINUTES
            unitText == "s" || unitText.startsWith("sa") -> ChronoUnit.HOURS
            unitText.startsWith("g") -> ChronoUnit.DAYS
            unitText.startsWith("h") -> ChronoUnit.WEEKS
            unitText.startsWith("a") -> ChronoUnit.MONTHS
            unitText.startsWith("y") -> ChronoUnit.YEARS
            else -> return 0L
        }
        return ZonedDateTime.now().minus(number, unit).toInstant().toEpochMilli()
    }

    companion object {
        private val TURKISH = Locale.forLanguageTag("tr")
        private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s*([a-zçğıöşü]+)""")
        private val CHALLENGE_REGEX = Regex("""challenge:\s*"([^"]+)"""")
        private val CHAPTER_URL_REGEX = Regex("""id-\d+-read-(.+)-chapter-[^/]+\.html""")
        private val SCRAMBLE_ID_REGEX = Regex("""MTR_SCRAMBLE_ID\s*=\s*["']([a-f0-9]{64})["']""")
        private val READER_SEED_REGEX = Regex("""MTR_READER_SEED\s*=\s*["']([^"']*)["']""")
        private const val CUT_FRAGMENT = "cut"
        private const val LOGIN_REQUIRED = "Bu bölümü okuyabilmek için WebView üzerinden üye girişi yapmanız gerekmektedir."
        private const val MAX_PAGES = 600
        private val SEED_REGEX = Regex("""--mdx-(white|black)\s*:\s*(-?\d+)""")
        private const val IMAGE_HOST = "image.mngtr.site"
        private const val IMAGE_ATTEMPTS = 3
    }
}
