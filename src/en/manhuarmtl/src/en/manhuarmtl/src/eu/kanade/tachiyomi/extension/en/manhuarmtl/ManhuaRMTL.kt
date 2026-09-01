package eu.kanade.tachiyomi.extension.en.manhuarmtl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

@Source
abstract class ManhuaRMTL :
    Madara(),
    ConfigurableSource {

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaSubString = "manga"

    // Custom client with OCR text-overlay interceptor
    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(::ocrImageInterceptor)
        .build()

    // Thread-safe storage for OCR text boxes, keyed by full image URL
    private val ocrData = ConcurrentHashMap<String, List<OcrTextBox>>()

    // Site excludes adult content by default; override to show everything unless the user opts out
    override val adultContentFilterOptions: Map<String, String> = mapOf(
        "Show all (incl. adult)" to "",
        "Hide adult" to "0",
        "Adult only" to "1",
    )

    // Custom sort values — manhuarmtl.com uses ?sort= (not ?m_orderby=)
    override val orderByFilterOptions: Map<String, String> = mapOf(
        "Relevance" to "relevance",
        "Latest" to "latest",
        "Oldest update" to "latest_asc",
        "Trending" to "trending",
        "Newest" to "new",
        "Oldest" to "new_asc",
        "Title A-Z" to "az",
        "Title Z-A" to "za",
        "Most chapters" to "chapters",
        "Fewest chapters" to "chapters_asc",
        "Top rated" to "rating",
        "Most bookmarked" to "bookmarks",
    )

    private val preferences = getPreferences()

    // ============================== Popular / Latest ==============================

    // Site uses custom MRM card layout instead of standard Madara
    override fun popularMangaSelector() = "li.mrm-r-item"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        url = element.selectFirst("a.mrm-r-item__link")?.attr("href")?.substringAfter(baseUrl) ?: ""
        title = element.selectFirst("a.mrm-r-item__link")?.attr("title") ?: ""
        thumbnail_url = element.selectFirst("span.mrm-r-item__art img")?.attr("abs:src")?.trim()
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // NSFW filter applies to browse/latest ONLY (not search).
    // Always includes the adult param:
    //   hideNsfw=ON  → adult=0 (hide adult)
    //   hideNsfw=OFF → adult=  (empty = show all, including adult)
    private fun browseUrl(sort: String, page: Int): String {
        val pg = if (page > 1) "&pg=$page" else ""
        val adult = if (preferences.hideNsfw()) "&adult=0" else "&adult="
        return "$baseUrl/?post_type=wp-manga&s=&sort=$sort$adult$pg"
    }

    // Site uses ?sort= instead of ?m_orderby=
    override fun popularMangaRequest(page: Int): Request = GET(browseUrl("trending", page), headers)

    override fun latestUpdatesRequest(page: Int): Request = GET(browseUrl("latest", page), headers)

    override fun popularMangaNextPageSelector(): String? = "a.next.page-numbers, a.mrm-pager__btn[rel=next]"

    // ============================== Search ==============================
    // Search ALWAYS shows everything — the NSFW setting does NOT filter search.
    // The user can still find NSFW content via search even when "Hide NSFW" is ON.

    override fun searchRequest(page: Int, query: String, filters: FilterList): Request {
        // Check if user explicitly selected an Adult content filter
        val adultFilter = filters.filterIsInstance<AdultContentFilter>().firstOrNull()
        // Default to "show all" in search — the NSFW setting doesn't affect search
        val adultValue = adultFilter?.toUriPart() ?: ""

        val url = "$baseUrl/".toHttpUrl().newBuilder().apply {
            addQueryParameter("post_type", "wp-manga")
            addQueryParameter("s", query)
            addQueryParameter("adult", adultValue)
            if (page > 1) addQueryParameter("pg", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is AuthorFilter -> if (filter.state.isNotBlank()) addQueryParameter("author", filter.state)
                    is ArtistFilter -> if (filter.state.isNotBlank()) addQueryParameter("artist", filter.state)
                    is YearFilter -> if (filter.state.isNotBlank()) addQueryParameter("release", filter.state)
                    is StatusFilter -> filter.state.forEach { if (it.state) addQueryParameter("status[]", it.id) }
                    is OrderByFilter -> if (filter.toUriPart().isNotBlank()) addQueryParameter("sort", filter.toUriPart())
                    is GenreConditionFilter -> addQueryParameter("op", filter.toUriPart())
                    is GenreList -> filter.state.filter { it.state }.forEach { addQueryParameter("genre[]", it.id) }
                    is ExcludeGenreList -> filter.state.filter { it.state }.forEach { addQueryParameter("exclude_genre[]", it.id) }
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaNextPageSelector(): String? = "a.next.page-numbers, a.mrm-pager__btn[rel=next]"

    // ============================== Genres ==============================

    // Override the genre request — the form lives on the search results page
    override fun genresRequest(): Request = GET("$baseUrl/?post_type=wp-manga&s=", headers)

    // Custom MRM chips layout — NOT the standard Madara checkbox-group
    override fun parseGenres(document: Document): List<Genre> = document.select("div.mrm-fgroup__chips label.mrm-gchip--in")
        .map { label ->
            val name = label.selectFirst("span")?.text() ?: label.text()
            val id = label.selectFirst("input[type=checkbox]")?.`val`() ?: name
            Genre(name, id)
        }

    // ============================== Manga Details ==============================

    // Custom MRM "hero" layout selectors
    override val mangaDetailsSelectorTitle = "h1.mrm-hero__title"
    override val mangaDetailsSelectorThumbnail = "div.mrm-hero__cover img"
    override val mangaDetailsSelectorAuthor = ".post-content_item:contains(Author) .author-content a, .post-content_item:contains(Author) .summary-content a"
    override val mangaDetailsSelectorArtist = ".post-content_item:contains(Artist) .artist-content a, .post-content_item:contains(Artist) .summary-content a"
    override val mangaDetailsSelectorStatus = ".post-content_item:contains(Status) .summary-content"
    override val mangaDetailsSelectorDescription = "div.description-summary div.summary__content, div.summary_content div.post-content_item > h5:contains(Summary) + div, div.mrm-panel div.summary__content"
    override val mangaDetailsSelectorGenre = "div.mrm-genres__list a[rel=tag]"
    override val mangaDetailsSelectorTag = ""
    override val seriesTypeSelector = ".post-content_item:contains(Type) .summary-content"

    // Alt names live in the MRM hero block, not the standard post-content row
    override val altNameSelector = "p.mrm-hero__alt"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        with(document) {
            manga.title = selectFirst(mangaDetailsSelectorTitle)?.ownText() ?: ""
            select(mangaDetailsSelectorAuthor).map { it.text() }.filter { it.notUpdating() }.joinToString().takeIf { it.isNotBlank() }?.let { manga.author = it }
            select(mangaDetailsSelectorArtist).map { it.text() }.filter { it.notUpdating() }.joinToString().takeIf { it.isNotBlank() }?.let { manga.artist = it }

            // Raw synopsis
            val synopsis = selectFirst(mangaDetailsSelectorDescription)?.let {
                if (it.select("p").text().isNotEmpty()) {
                    it.select("p").joinToString(separator = "\n\n") { p -> p.text().replace("<br>", "\n") }
                } else {
                    it.text()
                }
            }

            selectFirst(mangaDetailsSelectorThumbnail)?.let { manga.thumbnail_url = imageFromElement(it) }

            selectFirst(mangaDetailsSelectorStatus)?.let {
                val statusText = it.text().filter { ch -> ch.isLetterOrDigit() || ch.isWhitespace() }.trim()
                manga.status = when {
                    completedStatusList.any { c -> c.equals(statusText, true) } -> SManga.COMPLETED
                    ongoingStatusList.any { c -> c.equals(statusText, true) } -> SManga.ONGOING
                    hiatusStatusList.any { c -> c.equals(statusText, true) } -> SManga.ON_HIATUS
                    canceledStatusList.any { c -> c.equals(statusText, true) } -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }

            // Extract type early — used for both genre chips and info line
            val type = selectFirst(seriesTypeSelector)?.ownText()?.takeIf { it.isNotBlank() && it.notUpdating() }

            // Genres (optionally include type: Manhwa/Manhua/Manga)
            val genreList = select(mangaDetailsSelectorGenre).mapTo(ArrayList()) { it.text() }
            if (preferences.showTypeInGenre() && type != null) {
                genreList.add(type)
            }
            manga.genre = genreList.distinctBy(String::lowercase).joinToString().ifBlank { null }

            // ===== Build comix-style description =====
            val showAltNames = preferences.showAltNames()
            val showExtraInfo = preferences.showExtraInfo()
            val scorePosition = preferences.getScorePosition()

            // Alt names
            val altNames = selectFirst(altNameSelector)?.ownText()?.takeIf { it.isNotBlank() && it.notUpdating() }

            // Rating / votes from MRM facts — site uses a 0-5 scale (NOT 0-10 like comix)
            val ratingText = selectFirst("li.mrm-facts__item--rating strong")?.text()
            val ratingScore = ratingText?.toFloatOrNull()
            val votesText = selectFirst("li.mrm-facts__item--rating .mrm-facts__sub")?.text()
            val votesCount = Regex("""(\d+)""").find(votesText ?: "")?.value?.toIntOrNull() ?: 0
            val hasScore = ratingScore != null && votesCount > 0

            val stars = if (hasScore) {
                val score = ratingScore!!
                // Site uses 0-5 scale: round to nearest int (5.0 → 5 stars, 4.4 → 4, 4.8 → 5)
                val fullStars = score.roundToInt().coerceIn(0, 5)
                "★".repeat(fullStars) + "☆".repeat(5 - fullStars) + " $score"
            } else {
                null
            }

            // Type / chapters / views / release year
            val chaptersText = selectFirst(".post-content_item:contains(Chapters) .summary-content")?.text()
            val chaptersNum = chaptersText?.filter { it.isDigit() }?.toIntOrNull()
            val releaseYear = selectFirst(".post-content_item:contains(Release) .summary-content a")?.text()
                ?: selectFirst(".post-content_item:contains(Release) .summary-content")?.ownText()
            val viewsText = selectFirst("li.mrm-facts__item:has(i.ion-md-eye)")?.text()
            val views = viewsText?.filter { it.isDigit() }

            val infoLine = if (showExtraInfo) {
                buildString {
                    if (type != null) append("**Type:** $type")
                    if (releaseYear != null) {
                        if (isNotEmpty()) append(" · ")
                        append("**Year:** $releaseYear")
                    }
                    if (chaptersNum != null && chaptersNum > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("**Chapters:** $chaptersNum")
                    }
                    if (views != null && views.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("**Views:** $views")
                    }
                    if (manga.status != SManga.UNKNOWN) {
                        if (isNotEmpty()) append(" · ")
                        append("**Status:** ${formatStatus(manga.status)}")
                    }
                    if (hasScore) {
                        if (isNotEmpty()) append(" · ")
                        append("**$votesCount ratings**")
                    }
                }.ifBlank { null }
            } else {
                null
            }

            val desc = buildString {
                if (scorePosition == "top" && stars != null) {
                    append(stars)
                    append("\n")
                    if (infoLine != null) {
                        append(infoLine)
                        append("\n\n")
                    }
                }

                synopsis?.let { append(it) }

                if (showAltNames && altNames != null) {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative names:\n")
                    append("• $altNames")
                }

                if (scorePosition == "end" && stars != null) {
                    if (isNotEmpty()) append("\n\n")
                    append(stars)
                    if (infoLine != null) {
                        append("\n")
                        append(infoLine)
                    }
                }

                if (scorePosition == "none" && infoLine != null) {
                    if (isNotEmpty()) append("\n\n")
                    append(infoLine)
                }
            }.trim()

            manga.description = desc.ifBlank { synopsis }
            manga.initialized = true
        }

        return manga
    }

    private fun formatStatus(status: Int): String = when (status) {
        SManga.ONGOING -> "Ongoing"
        SManga.COMPLETED -> "Completed"
        SManga.CANCELLED -> "Cancelled"
        SManga.ON_HIATUS -> "On hiatus"
        else -> "Unknown"
    }

    // ============================== Chapters ==============================
    // Standard Madara selectors work — li.wp-manga-chapter is present in the detail HTML.
    // All chapters are in the initial page load (no AJAX needed).

    // ============================== Pages + OCR ==============================
    // The site serves RAW images. English MTL text is a JS overlay fetched from
    // fetch-ocr.php. We parse the _0xvault credentials from the reading page,
    // fetch the text data, and burn it onto the images via a network interceptor.

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body?.string() ?: ""
        val readingPageUrl = response.request.url.toString()
        val document = Jsoup.parse(html, readingPageUrl)

        val pages = pageListParse(document)

        val textMode = preferences.chapterTextMode()

        if (textMode != "raw" && html.isNotBlank()) {
            // Clear previous chapter's OCR data
            ocrData.clear()

            try {
                val credentials = parseOcrCredentials(html)
                if (credentials != null) {
                    val ocrPages = fetchOcrData(credentials, readingPageUrl)
                    if (ocrPages != null && ocrPages.isNotEmpty()) {
                        // Build filename → text boxes map (try multiple key formats for robust matching)
                        val ocrByFilename = mutableMapOf<String, List<OcrTextBox>>()
                        for (ocrPage in ocrPages) {
                            val filename = ocrPage.image ?: continue
                            var textBoxes = ocrPage.normalisedTexts()
                            // Arabic mode: translate the already-extracted English MTL text to Arabic.
                            // Coordinates (box) are kept untouched — only .text changes.
                            if (textMode == "ar" && textBoxes.isNotEmpty()) {
                                textBoxes = translateTextBoxes(textBoxes, "ar")
                            }
                            if (textBoxes.isNotEmpty()) {
                                // Store under original name AND URL-decoded name
                                ocrByFilename[filename] = textBoxes
                                ocrByFilename[filename.replace("%20", " ")] = textBoxes
                                ocrByFilename[filename.replace(" ", "_")] = textBoxes
                            }
                        }

                        // Match OCR data to pages by filename (try multiple formats)
                        for (page in pages) {
                            val imageUrl = page.imageUrl ?: continue
                            // Strip leading spaces (site has src=" https://..."), get filename, strip query
                            val filename = imageUrl.trim().substringAfterLast("/").substringBefore("?")
                            // Also try URL-decoded version
                            val decodedFilename = java.net.URLDecoder.decode(filename, "UTF-8")

                            val textBoxes = ocrByFilename[filename]
                                ?: ocrByFilename[decodedFilename]
                                ?: ocrByFilename[decodedFilename.replace(" ", "_")]
                            if (textBoxes != null && textBoxes.isNotEmpty()) {
                                ocrData[imageUrl.trim()] = textBoxes
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall back to raw images silently
            }
        }

        return pages
    }

    /**
     * Parse OCR credentials from the reading page HTML.
     * The credentials are in a JS array: _0xvault = ["base64cid","hex64token",ts,"hex16nonce","url","hex32ref"]
     */
    private fun parseOcrCredentials(html: String): OcrCredentials? {
        // Find the _0xvault array — it contains exactly 6 elements
        // ["base64","hex64",number,"hex16","url","hex32"]
        val vaultRegex = Regex(
            """_0xvault\s*=\s*\[\s*"([A-Za-z0-9+/=]+)"\s*,\s*"([0-9a-f]{64})"\s*,\s*(\d+)\s*,\s*"([0-9a-f]{16})"\s*,\s*"(https?:\\?/\\?/[^"]+fetch-ocr\.php)"\s*,\s*"([0-9a-f]{32})"\s*\]""",
        )
        val match = vaultRegex.find(html) ?: return null

        // Unescape the URL (JS uses \/ for /)
        val gateUrl = match.groupValues[5].replace("\\/", "/")

        return OcrCredentials(
            cid = match.groupValues[1], // base64 — sent as-is, do NOT decode
            token = match.groupValues[2], // 64-hex
            timestamp = match.groupValues[3].toLongOrNull() ?: 0L,
            nonce = match.groupValues[4], // 16-hex
            gateUrl = gateUrl,
            ref = match.groupValues[6], // 32-hex
        )
    }

    /**
     * Fetch OCR text data from fetch-ocr.php.
     * Sends the exact same request as the site's JS:
     * - POST with JSON body {"cid":"<base64>","ref":"<hex>"}
     * - Headers: X-Gate-Token, X-Gate-Nonce, X-Gate-Timestamp, X-Requested-With, Cache-Control
     * - Origin and Referer headers are REQUIRED (site returns 403 without them)
     */
    private fun fetchOcrData(credentials: OcrCredentials, readingPageUrl: String): List<OcrPage>? {
        // Body: cid stays base64, ref is hex — both as-is from _0xvault
        val jsonBody = """{"cid":"${credentials.cid}","ref":"${credentials.ref}"}"""
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        // Use the source's default headers (includes User-Agent) as a base,
        // then set all required OCR headers
        val request = Request.Builder()
            .url(credentials.gateUrl)
            .post(requestBody)
            .headers(headers)
            .header("Content-Type", "application/json")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Cache-Control", "no-cache")
            .header("X-Gate-Token", credentials.token)
            .header("X-Gate-Nonce", credentials.nonce)
            .header("X-Gate-Timestamp", credentials.timestamp.toString())
            .header("Referer", readingPageUrl)
            .header("Origin", baseUrl)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            response.close()

            if (body.isNullOrBlank()) return null

            // Detect Cloudflare challenge page
            if (body.contains("Just a moment") || body.contains("cf-challenge") || body.contains("cf-mitigated")) {
                return null
            }

            // Try parsing as bare array first, then as envelope
            try {
                body.parseAs<List<OcrPage>>()
            } catch (_: Exception) {
                try {
                    body.parseAs<OcrResponse>().pages()
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Translate a page's text boxes using the free, unofficial Google Translate
     * "gtx" endpoint (the same one translate.google.com's web client uses — no API key).
     *
     * All texts for a page are joined with "\n" and sent as ONE request (to avoid
     * one HTTP call per text box), then the response is split back on "\n".
     * If the split doesn't line up 1:1 with the input (Google sometimes merges/
     * splits sentences), each text is retried individually as a safe fallback.
     * Box coordinates are never touched — only .text is replaced.
     */
    private fun translateTextBoxes(boxes: List<OcrTextBox>, targetLang: String): List<OcrTextBox> {
        if (boxes.isEmpty()) return boxes

        val joined = boxes.joinToString("\n") { it.text }
        val batchResult = googleTranslate(joined, targetLang)
        val lines = batchResult?.split("\n")

        if (lines != null && lines.size == boxes.size) {
            return boxes.mapIndexed { i, box -> box.copy(text = lines[i].trim()) }
        }

        // Fallback: translate one by one (slower, but robust if batching misaligns)
        return boxes.map { box ->
            val translated = googleTranslate(box.text, targetLang)
            box.copy(text = translated?.trim()?.ifBlank { box.text } ?: box.text)
        }
    }

    /**
     * Single call to Google's free translate_a/single endpoint.
     * Returns null on any network/parsing failure (caller falls back to the
     * original — usually English — text so a translation hiccup never breaks a page).
     */
    private fun googleTranslate(text: String, targetLang: String): String? {
        if (text.isBlank()) return text

        return try {
            val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", "en")
                .addQueryParameter("tl", targetLang)
                .addQueryParameter("dt", "t")
                .addQueryParameter("q", text)
                .build()

            val request = Request.Builder().url(url).headers(headers).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            response.close()

            if (body.isNullOrBlank()) return null

            // Response shape: [[["<translated>","<original>",null,null,...], ...], null, "en", ...]
            val root = body.parseAs<List<JsonElement>>()
            val segments = (root.getOrNull(0) as? kotlinx.serialization.json.JsonArray) ?: return null
            val sb = StringBuilder()
            for (seg in segments) {
                val arr = seg as? kotlinx.serialization.json.JsonArray ?: continue
                val piece = (arr.getOrNull(0) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
                sb.append(piece)
            }
            sb.toString().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Network interceptor that overlays English MTL text on raw chapter images.
     * Only runs when "English (MTL)" mode is selected in settings.
     */
    private fun ocrImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (preferences.chapterTextMode() == "raw") return response
        if (!response.isSuccessful) return response

        val url = request.url.toString()

        // Only process images from the CDN
        if (!url.contains("cdn.manhuarmmtl.com") && !url.contains("manhuarmmtl.com")) return response

        // Look up OCR text boxes for this image URL (try both raw and trimmed)
        val textBoxes = ocrData[url] ?: ocrData[url.trim()] ?: return response
        if (textBoxes.isEmpty()) return response

        // Read the image bytes
        val imageBytes = response.body?.bytes() ?: return response
        if (imageBytes.isEmpty()) return response

        // Overlay text on the image
        val isRtl = preferences.chapterTextMode() == "ar"
        val modifiedBytes = overlayText(imageBytes, textBoxes, isRtl) ?: return response

        // Build new response with modified image
        val contentType = response.body?.contentType()
        val newBody = modifiedBytes.toResponseBody(contentType)

        return response.newBuilder()
            .body(newBody)
            .build()
    }

    /**
     * Burn English text boxes onto a raw image bitmap.
     * Matches the site's exact rendering:
     * - Font size: min(sqrt(w*h)/sqrt(len), w/len, h/2) * 2, clamped [8, 64]
     * - Text centered horizontally on box center, top-aligned to box top
     * - Vertically centered within full box height
     * - Black text with 4-corner white outline (0 blur)
     *
     * @param isRtl When true (Arabic mode), text is laid out right-to-left using
     *   StaticLayout.Builder with TextDirectionHeuristics.RTL (API 23+). On older
     *   API levels this falls back to the legacy constructor, which still renders
     *   Arabic shaping correctly but paragraph alignment may look slightly off.
     */
    private fun buildStaticLayout(text: CharSequence, paint: TextPaint, maxWidth: Int, isRtl: Boolean): StaticLayout {
        return if (isRtl && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.2f, 0f, false)
        }
    }

    private fun overlayText(imageBytes: ByteArray, textBoxes: List<OcrTextBox>, isRtl: Boolean = false): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        for (textBox in textBoxes) {
            val x = textBox.box.getOrElse(0) { 0f }
            val y = textBox.box.getOrElse(1) { 0f }
            val w = textBox.box.getOrElse(2) { 0f }
            val h = textBox.box.getOrElse(3) { 0f }

            if (w <= 0 || h <= 0) continue

            val text = textBox.text
            if (text.isBlank()) continue

            // ===== Font size calculation (exact match to site's JS, +25% bigger) =====
            // Site's formula:
            // 1. baseFontSize = min(sqrt(w*h)/sqrt(len), w/len, h/2)
            // 2. Clamp baseFontSize to [8, 64]
            // 3. finalFontSize = baseFontSize * 2  (200% user setting)
            // 4. Clamp finalFontSize to [8, 64]
            // 5. Custom: multiply by 1.25 for better readability
            val textLength = text.length
            val boxArea = w * h
            val areaFactor = Math.sqrt(boxArea.toDouble()) / Math.sqrt(textLength.toDouble())
            val widthFactor = w.toDouble() / textLength
            val heightFactor = h.toDouble() / 2.0
            val rawBase = minOf(areaFactor, widthFactor, heightFactor)
            // Step 2: clamp base to [8, 64] BEFORE multiplying
            val clampedBase = rawBase.coerceIn(8.0, 64.0)
            // Step 3: multiply by 2 (200% default user setting)
            val finalSize = clampedBase * 2.0
            // Step 4: clamp final to [8, 64]
            val siteFontSize = finalSize.toFloat().coerceIn(8f, 64f)
            // Step 5: make 35% bigger for better readability
            val fontSize = (siteFontSize * 1.35f).coerceIn(8f, 90f)

            // Outline width: max(0.75, fontSize * 0.08)
            val outlineWidth = maxOf(0.75f, fontSize * 0.08f)

            // maxWidth = w * 1.4 (text can extend beyond box)
            val maxWidth = (w * 1.4f).toInt()

            // Stroke paint (white outline — 4-corner shadow simulation)
            // Using "casual" font family for a more comic/manga look (closest to Anime Ace)
            val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = fontSize
                typeface = Typeface.create("casual", Typeface.BOLD)
                style = Paint.Style.STROKE
                strokeWidth = outlineWidth * 2
            }

            // Fill paint (black text)
            val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = fontSize
                typeface = Typeface.create("casual", Typeface.BOLD)
            }

            // Use StaticLayout for word-wrapping within maxWidth
            // Line spacing multiplier 1.2 to match site's line-height
            val strokeLayout = buildStaticLayout(text, strokePaint, maxWidth, isRtl)
            val fillLayout = buildStaticLayout(text, fillPaint, maxWidth, isRtl)

            // Position: horizontally centered on box center, vertically centered in box height
            // Clamp BOTH horizontal and vertical to image bounds to prevent text cut-off
            val textHeight = strokeLayout.height.toFloat()
            val boxCenterX = x + w / 2f
            val verticalOffset = ((h - textHeight) / 2f).coerceAtLeast(0f)

            // Horizontal: clamp so text stays within image bounds
            val imgWidth = mutableBitmap.width.toFloat()
            val imgHeight = mutableBitmap.height.toFloat()
            val layoutWidth = maxWidth.toFloat()
            val rawTranslateX = boxCenterX - layoutWidth / 2f
            val translateX = rawTranslateX.coerceIn(0f, (imgWidth - layoutWidth).coerceAtLeast(0f))

            // Vertical: clamp so text doesn't go off the bottom of the image
            val rawTranslateY = y + verticalOffset
            val translateY = rawTranslateY.coerceIn(0f, (imgHeight - textHeight).coerceAtLeast(0f))

            canvas.save()
            canvas.translate(translateX, translateY)
            // Draw stroke (outline) first, then fill on top
            strokeLayout.draw(canvas)
            fillLayout.draw(canvas)
            canvas.restore()
        }

        val output = java.io.ByteArrayOutputStream()
        mutableBitmap.compress(Bitmap.CompressFormat.WEBP, 95, output)

        if (bitmap != mutableBitmap) bitmap.recycle()
        mutableBitmap.recycle()

        return output.toByteArray()
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!.trim(), headers.newBuilder().set("Referer", baseUrl).build())

    // ============================== Filters ==============================

    private class ExcludeGenreList(title: String, genres: List<Genre>) : Filter.Group<GenreCheckBox>(title, genres.map { GenreCheckBox(it.name, it.id) })

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        val filters = mutableListOf<Filter<*>>(
            AuthorFilter("Author"),
            ArtistFilter("Artist"),
            YearFilter("Release year"),
            StatusFilter(
                title = "Status",
                status = statusFilterOptions.map { Tag(it.key, it.value) },
            ),
            OrderByFilter(
                title = "Sort by",
                options = orderByFilterOptions.toList(),
                state = 1, // Default: Latest
            ),
            AdultContentFilter(
                title = "Adult content",
                options = adultContentFilterOptions.toList(),
            ),
        )

        if (genresList.isNotEmpty()) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Genres (include)"),
                GenreConditionFilter(
                    title = "Genre match mode",
                    options = genreConditionFilterOptions.toList(),
                ),
                GenreList(
                    title = "Genres",
                    genres = genresList,
                ),
                Filter.Separator(),
                Filter.Header("Genres (exclude)"),
                ExcludeGenreList(
                    title = "Exclude genres",
                    genres = genresList,
                ),
            )
        } else if (fetchGenres) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Press 'Reset' to attempt to load genres"),
            )
        }

        return FilterList(filters)
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        // Chapter text mode (English MTL overlay vs Raw)
        androidx.preference.ListPreference(screen.context).apply {
            key = PREF_CHAPTER_TEXT_MODE
            title = "Chapter text"
            summary = "English/Arabic (MTL overlay) burns translated text onto raw images. Arabic is machine-translated " +
                "from the site's English text via free Google Translate. Raw shows original images only."
            entries = arrayOf("English (MTL overlay)", "Arabic (MTL overlay, translated)", "Raw images only")
            entryValues = arrayOf("en", "ar", "raw")
            setDefaultValue("en")
        }.let(screen::addPreference)

        // Hide NSFW content from browse/latest only (does NOT affect search)
        androidx.preference.SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_NSFW
            title = "Hide NSFW in browse"
            summary = "Hide adult content from Popular and Latest lists. Search is unaffected — you can still find NSFW content via search."
            setDefaultValue(false)
        }.let(screen::addPreference)

        // Show alt names
        androidx.preference.SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_ALT_NAMES
            title = "Show alternative names"
            summary = "Display alternative titles in the description"
            setDefaultValue(true)
        }.let(screen::addPreference)

        // Show extra info
        androidx.preference.SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_EXTRA_INFO
            title = "Show extra info in description"
            summary = "Display type, status, year, chapters, views, rating"
            setDefaultValue(true)
        }.let(screen::addPreference)

        // Show type in genre chips
        androidx.preference.SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_TYPE_IN_GENRE
            title = "Show type in genre chips"
            summary = "Include Manhwa/Manhua/Manga in the genre field"
            setDefaultValue(true)
        }.let(screen::addPreference)

        // Score display position
        androidx.preference.ListPreference(screen.context).apply {
            key = PREF_SCORE_POSITION
            title = "Score display position"
            summary = "Where to display the manga score"
            entries = arrayOf("Don't show", "Top of description", "End of description")
            entryValues = arrayOf("none", "top", "end")
            setDefaultValue("end")
        }.let(screen::addPreference)
    }

    private fun android.content.SharedPreferences.chapterTextMode(): String = getString(PREF_CHAPTER_TEXT_MODE, "en") ?: "en"
    private fun android.content.SharedPreferences.hideNsfw(): Boolean = getBoolean(PREF_HIDE_NSFW, false)
    private fun android.content.SharedPreferences.showAltNames(): Boolean = getBoolean(PREF_SHOW_ALT_NAMES, true)
    private fun android.content.SharedPreferences.showExtraInfo(): Boolean = getBoolean(PREF_SHOW_EXTRA_INFO, true)
    private fun android.content.SharedPreferences.showTypeInGenre(): Boolean = getBoolean(PREF_SHOW_TYPE_IN_GENRE, true)
    private fun android.content.SharedPreferences.getScorePosition(): String = getString(PREF_SCORE_POSITION, "end") ?: "end"

    companion object {
        private const val PREF_CHAPTER_TEXT_MODE = "pref_chapter_text_mode"
        private const val PREF_HIDE_NSFW = "pref_hide_nsfw"
        private const val PREF_SHOW_ALT_NAMES = "pref_show_alt_names"
        private const val PREF_SHOW_EXTRA_INFO = "pref_show_extra_info"
        private const val PREF_SHOW_TYPE_IN_GENRE = "pref_show_type_in_genre"
        private const val PREF_SCORE_POSITION = "pref_score_position"
    }
}
