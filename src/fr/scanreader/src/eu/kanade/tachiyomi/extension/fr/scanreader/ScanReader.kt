package eu.kanade.tachiyomi.extension.fr.scanreader

import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MultipartBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class ScanReader : HttpSource() {

    override val name = "Scan Reader"
    override val baseUrl = "https://scanreader.net"
    override val lang = "fr"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    // ====================== Headers ======================

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ====================== Popular ======================
    // The homepage has a fixed "Tendances de la semaine" section with no pagination.
    // Page 1 returns that section; subsequent pages return an empty list.

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        if (response.request.url != baseUrl.toHttpUrl()) {
            return MangasPage(emptyList(), false)
        }
        val document = response.asJsoup()
        val mangas = document.select("div.popular-section div.manga-card").map { element ->
            mangaFromCard(element)
        }
        // Homepage is a single page with no further pagination
        return MangasPage(mangas, false)
    }

    // ====================== Latest ======================
    // The homepage "Dernières sorties" section: most recently updated titles.

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.recent-section div.manga-latest-card").map { element ->
            mangaFromLatestCard(element)
        }
        return MangasPage(mangas, false)
    }

    // ====================== Search ======================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET(
            "$baseUrl/?s=${query.trim()}&post_type=manga",
            headers,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // Search results may use either card style depending on the theme template
        val cards = document.select("div.manga-card").ifEmpty {
            document.select("div.manga-latest-card")
        }

        val mangas = cards.map { element ->
            val isLatestStyle = element.hasClass("manga-latest-card")
            if (isLatestStyle) mangaFromLatestCard(element) else mangaFromCard(element)
        }

        return MangasPage(mangas, false)
    }

    override fun getFilterList() = FilterList()

    // ====================== Manga Details ======================

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.manga-title")!!.text()

            // The og:image meta tag is the most reliable cover source
            thumbnail_url = document.selectFirst("meta[property='og:image']")
                ?.attr("content")

            // Synopsis is inside a styled div preceded by an <h3> "Synopsis"
            description = document.selectFirst("div.manga-content div[style*='background: #333'] p")
                ?.text()

            // Info grid: each row has a label div and a value div
            document.select("div.manga-info-grid > div").forEach { row ->
                val label = row.selectFirst("div:first-child")?.text() ?: return@forEach
                val valueEl = row.selectFirst("div:last-child") ?: return@forEach
                when {
                    label.contains("Auteur", ignoreCase = true) ->
                        author = valueEl.text().trim()

                    label.contains("Genres", ignoreCase = true) ->
                        genre = valueEl.select("span").joinToString(", ") { it.text().trim() }

                    label.contains("Statut", ignoreCase = true) -> {
                        val statusText = valueEl.text()
                        status = when {
                            statusText.contains("cours", ignoreCase = true) -> SManga.ONGOING
                            statusText.contains("Terminé", ignoreCase = true) -> SManga.COMPLETED
                            statusText.contains("Hiatus", ignoreCase = true) -> SManga.ON_HIATUS
                            else -> SManga.UNKNOWN
                        }
                    }
                }
            }

            initialized = true
        }
    }

    // ====================== Chapter List ======================
    // The chapter list is not in the static manga page HTML. It is loaded via a
    // WordPress admin-ajax.php POST using a per-page nonce and manga ID.
    // We override fetchChapterList to perform this two-step request chain.

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.fromCallable {
            // Step 1 — Load manga page to extract the dynamic nonce and manga ID
            val mangaResponse = client.newCall(mangaDetailsRequest(manga)).execute()
            val document = mangaResponse.asJsoup()

            val container = document.selectFirst("#secure-chapters-container")
                ?: return@fromCallable emptyList<SChapter>()

            val mangaId = container.attr("data-manga-id")
            val nonce = container.attr("data-nonce")

            if (mangaId.isBlank() || nonce.isBlank()) return@fromCallable emptyList<SChapter>()

            // Step 2 — POST to admin-ajax.php with the nonce.
            // Uses multipart/form-data to exactly match the browser's FormData object.
            // X-Requested-With is added because WordPress AJAX handlers commonly check it.
            // Referer is set to the manga page so the server sees a believable origin.
            val ajaxHeaders = headersBuilder()
                .set("Referer", baseUrl + manga.url)
                .add("X-Requested-With", "XMLHttpRequest")
                .build()

            val ajaxBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("action", "load_protected_chapters_html")
                .addFormDataPart("manga_id", mangaId)
                .addFormDataPart("nonce", nonce)
                .build()

            val ajaxResponse = client.newCall(
                POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, ajaxBody),
            ).execute()

            chapterListParse(ajaxResponse)
        }
    }

    // Not called directly (fetchChapterList is fully overridden), but required by HttpSource.
    // It parses the HTML injected by the admin-ajax.php response into the chapter list.
    override fun chapterListParse(response: Response): List<SChapter> {
        val bodyStr = response.body.string()

        // WordPress admin-ajax.php may return raw HTML or a JSON envelope:
        // { "success": true, "data": "<html>" }
        val html = try {
            json.parseToJsonElement(bodyStr)
                .jsonObject["data"]
                ?.jsonPrimitive
                ?.content
                ?: bodyStr
        } catch (e: Exception) {
            bodyStr
        }

        // Return empty list explicitly if WordPress rejected the request
        if (html.trim() == "0" || html.trim() == "-1") return emptyList()

        Log.d("ScanReader", "AJAX response (first 2000 chars): ${html.take(2000)}")

        val document = Jsoup.parse(html)

        // Select every link pointing to a chapter URL. This is resilient to whatever
        // wrapper element the server injects around chapters.
        return document.select("a[href*='/chapitre/']").mapNotNull { link ->
            val href = link.attr("href").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(href)
                // Look for a dedicated name element inside the link first,
                // then fall back to direct text only (ownText excludes child elements,
                // preventing dates/uploader text from leaking into the name)
                name = link.selectFirst(".chapter-number, .chapter-title, [class*='chapter-num']")
                    ?.text()?.trim()
                    ?: link.ownText().trim().ifEmpty {
                        link.children().firstOrNull()?.ownText()?.trim() ?: ""
                    }
                date_upload = parseChapterDate(
                    link.selectFirst("[class*='date'], time, [class*='time']")?.text(),
                )
            }
        }
    }

    // ====================== Page List ======================
    // Images are NOT in <img> tags. The site stores page URLs as an array of
    // Base64-encoded reversed strings inside an inline <script>:
    //
    //   const _0x5f2a = ["<base64>", "<base64>", ...];
    //
    // Decoding: base64Decode(entry).reversed() == real image URL
    //
    // The variable name may change between deploys, so we match any const/let/var
    // array whose values look like Base64 strings.

    override fun pageListRequest(chapter: SChapter): Request =
        GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()

        // Match: const/let/var <name> = ["base64val", "base64val", ...]
        val arrayMatch = Regex(
            """(?:const|let|var)\s+\w+\s*=\s*\[((?:\s*"[A-Za-z0-9+/=]+"(?:\s*,\s*)?)+)\s*]""",
        ).find(body) ?: return emptyList()

        return Regex(""""([A-Za-z0-9+/=]{20,})"""")
            .findAll(arrayMatch.groupValues[1])
            .mapIndexed { index, match ->
                val encoded = match.groupValues[1]
                // Decode: base64 → then reverse the resulting string to get the URL
                val decoded = String(Base64.decode(encoded, Base64.DEFAULT)).reversed()
                Page(index, imageUrl = decoded)
            }
            .toList()
    }

    // imageUrlParse is never called because Page.imageUrl is set directly above
    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    // ====================== Helpers ======================

    /** Regex to extract the cover URL from onclick="addToHistory(id, 'title', 'url')" */
    private val onClickCoverRegex =
        Regex("""addToHistory\(\d+\s*,\s*'[^']*'\s*,\s*'([^']+)'""")

    private fun extractCoverFromOnClick(onclick: String?): String? {
        if (onclick.isNullOrBlank()) return null
        return onClickCoverRegex.find(onclick)?.groupValues?.get(1)
    }

    /**
     * Extracts a usable image URL from a lazy-loaded <img> element.
     * Priority: data-lazy-src > first URL in data-lazy-srcset > src (skipping SVG placeholders)
     */
    private fun extractLazySrc(img: Element): String? {
        img.attr("data-lazy-src").takeIf { it.isNotEmpty() }?.let { return it }

        img.attr("data-lazy-srcset").takeIf { it.isNotEmpty() }?.let { srcset ->
            // srcset format: "url1 300w, url2 768w, ..."
            return srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
        }

        // Fall back to src only if it is not the inline SVG placeholder
        return img.attr("src").takeIf { it.isNotEmpty() && !it.startsWith("data:") }
    }

    /** Parses a manga entry from a popular-section card (div.manga-card). */
    private fun mangaFromCard(element: Element): SManga {
        val link = element.selectFirst("a")!!
        return SManga.create().apply {
            setUrlWithoutDomain(link.attr("href"))
            title = element.selectFirst("h3")!!.text().trim()
            thumbnail_url = extractCoverFromOnClick(link.attr("onclick"))
                ?: element.selectFirst("img")?.let { extractLazySrc(it) }
        }
    }

    /** Parses a manga entry from a recent-section card (div.manga-latest-card). */
    private fun mangaFromLatestCard(element: Element): SManga {
        // The first <a> pointing to /mangas/ is the title link; it also holds the cover onclick
        val link = element.selectFirst("a[href*='/mangas/']")!!
        return SManga.create().apply {
            setUrlWithoutDomain(link.attr("href"))
            title = element.selectFirst("h3")!!.text().trim()
            thumbnail_url = extractCoverFromOnClick(link.attr("onclick"))
                ?: element.selectFirst("img")?.let { extractLazySrc(it) }
        }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)
    }

    private fun parseChapterDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            dateFormat.parse(dateStr.trim())?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
