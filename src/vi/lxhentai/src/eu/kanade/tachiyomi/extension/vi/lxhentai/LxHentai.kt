package eu.kanade.tachiyomi.extension.vi.lxhentai

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class LxHentai : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage(client.get(browseMangaUrl(page, "-views")))

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(client.get(browseMangaUrl(page, "-updated_at")))

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "-updated_at"
        val searchType = filters.firstInstanceOrNull<SearchTypeFilter>()?.toUriPart() ?: "name"
        val statuses = filters.firstInstanceOrNull<StatusFilter>()?.selectedValues().orEmpty()
        val includedGenres = filters.firstInstanceOrNull<GenreFilter>()?.includedValues().orEmpty()
        val excludedGenres = filters.firstInstanceOrNull<GenreFilter>()?.excludedValues().orEmpty()

        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("filter[$searchType]", query)
                }
                if (statuses.isNotEmpty()) {
                    addQueryParameter("filter[status]", statuses.joinToString(","))
                }
                if (includedGenres.isNotEmpty()) {
                    addQueryParameter("filter[accept_genres]", includedGenres.joinToString(","))
                }
                if (excludedGenres.isNotEmpty()) {
                    addQueryParameter("filter[reject_genres]", excludedGenres.joinToString(","))
                }
            }
            .build()
        return parseMangaPage(client.get(url))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen/$slug")
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    private fun browseMangaUrl(page: Int, sortBy: String): HttpUrl = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
        .addQueryParameter("sort", sortBy)
        .addQueryParameter("page", page.toString())
        .addQueryParameter("filter[status]", "ongoing,completed,paused")
        .build()

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val mangaList = document.select("div.manga-vertical")
            .map { element: Element -> mangaFromElement(element) }
        val hasNextPage = document.select("a#pagination[data-page]")
            .asSequence()
            .mapNotNull { element: Element -> element.attr("data-page").toIntOrNull() }
            .any { page: Int -> page > currentPage }

        return MangasPage(mangaList, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        val titleElement = element.selectFirst("a.text-ellipsis[href^=/truyen/]")!!
        val coverElement = element.selectFirst("div.cover")

        return SManga.create().apply {
            title = titleElement.text()
            setUrlWithoutDomain(titleElement.absUrl("href"))
            thumbnail_url = coverElement?.let { it: Element -> getThumbnailUrl(it) }
        }
    }

    private fun getThumbnailUrl(element: Element): String? = element.absUrl("data-bg")
        .ifEmpty { parseBackgroundUrl(element.attr("style")).orEmpty() }
        .ifBlank { null }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("div.flex.flex-row.truncate.mb-4 span.grow.text-lg.ml-1.text-ellipsis.font-semibold")!!.text()
        thumbnail_url = document.selectFirst("div.md\\:col-span-2 div.cover-frame > div.cover")
            ?.let { element: Element -> getThumbnailUrl(element) }
        author = document.infoRow("Tác giả:")
            ?.select("a[href*=/tac-gia/]")
            ?.joinToString { it: Element -> it.text() }
            ?.ifEmpty { null }

        genre = document.infoRow("Thể loại:")
            ?.select("a[href*=/the-loai/]")
            ?.joinToString { it: Element -> it.text() }
            ?.ifEmpty { null }

        val altNames = document.infoRow("Tên khác:")
            ?.select("a, span:not(.font-semibold)")
            ?.joinToString { it.text() }
            ?.takeIf { it.isNotEmpty() }

        val summary = document.select("p:contains(Tóm tắt) ~ p").joinToString("\n") { it.wholeText() }.trim()

        description = buildString {
            if (altNames != null) {
                append("Tên khác: ", altNames, "\n\n")
            }
            append(summary)
        }.trim()

        status = parseStatus(document.infoRow("Tình trạng:")?.text())
    }

    private fun Document.infoRow(label: String): Element? = select("div")
        .firstOrNull { row: Element -> row.selectFirst("> span.font-semibold")?.text() == label }

    private fun parseStatus(rawStatus: String?): Int {
        val status = rawStatus?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "đang tiến hành" in status -> SManga.ONGOING
            "hoàn thành" in status || "completed" in status -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================= Chapters ===============================

    private fun parseChapterList(document: Document): List<SChapter> {
        val chapterElements = document.select("ul.overflow-y-auto a[href^=/truyen/]:has(span.timeago)")
            .ifEmpty { document.select("a[href^=/truyen/]:has(span.timeago)") }

        return chapterElements.mapNotNull { element: Element ->
            val chapterName = element.selectFirst("span.text-ellipsis")?.text()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = chapterName
                date_upload = parseChapterDate(element.selectFirst("span.timeago"))
            }
        }
    }

    private fun parseChapterDate(timeElement: Element?): Long = Instant.parseOrNull(timeElement?.attr("datetime").orEmpty())?.toEpochMilliseconds() ?: 0L

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = "$baseUrl${chapter.url}"
        val chapterHtml = client.get(chapterUrl).asJsoup().html()

        val imageUrls = extractImageUrls(chapterHtml)
        if (imageUrls.isEmpty()) return emptyList()

        val token = getActionToken(chapterUrl, chapterHtml)
        val pageMetadata = encodePageMetadata(chapterUrl, token)

        return imageUrls.mapIndexed { index: Int, imageUrl: String ->
            Page(index, url = pageMetadata, imageUrl = imageUrl)
        }
    }

    private suspend fun getActionToken(chapterUrl: String, chapterHtml: String): String {
        cachedActionToken?.let { return it }

        val csrfToken = csrfTokenRegex.find(chapterHtml)?.groupValues?.get(1).orEmpty()
        val siteKey = recaptchaRegex.find(chapterHtml)?.groupValues?.get(1).orEmpty()

        val tokenHeaders = headersBuilder()
            .set("Accept", "application/json")
            .set("Content-Type", "application/json")
            .set("X-CSRF-TOKEN", csrfToken)
            .set("Referer", chapterUrl)
            .set("Origin", baseUrl)
            .build()

        val getResponse = client.get("$baseUrl/get_token", tokenHeaders, ensureSuccess = false)
        if (getResponse.isSuccessful) {
            val data = getResponse.parseAs<TokenResponse>()
            if (!data.is_bot && !data.action_token.isNullOrEmpty()) {
                return data.action_token.also { cachedActionToken = it }
            }
        } else {
            getResponse.close()
        }

        if (siteKey.isNotEmpty()) {
            val turnstileToken = getTurnstileToken(
                url = chapterUrl,
                siteKey = siteKey,
            )

            val payload = TurnstilePayload(turnstileResponse = turnstileToken).toJsonRequestBody()
            val postResponse = client.post("$baseUrl/get_token", tokenHeaders, payload, ensureSuccess = false)
            if (postResponse.isSuccessful) {
                val data = postResponse.parseAs<TokenResponse>()
                if (!data.is_bot && !data.action_token.isNullOrEmpty()) {
                    return data.action_token.also { cachedActionToken = it }
                }
            } else {
                postResponse.close()
            }
        }

        return ""
    }

    private fun extractImageUrls(html: String): List<String> {
        for (match in scriptTagRegex.findAll(html)) {
            val scriptContent = match.groupValues[1]
            if (!scriptContent.contains("KGZ") || scriptContent.length < 1000) continue

            val bArrayContent = bArrayRegex.find(scriptContent)?.groupValues?.get(2) ?: continue
            val parts = quotedStringRegex.findAll(bArrayContent).map { it.groupValues[1] }.toList()
            if (parts.isEmpty()) continue

            val layer2 = try {
                String(Base64.decode(parts.joinToString(""), Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                continue
            }

            val arrayMap = mutableMapOf<String, IntArray>()
            for (arrMatch in arrayAssignRegex.findAll(layer2)) {
                val name = arrMatch.groupValues[1]
                val nums = arrMatch.groupValues[2].split(',').mapNotNull { it.trim().toIntOrNull() }.toIntArray()
                arrayMap[name] = nums
            }

            val hexKey = keyRegex.find(layer2)?.groupValues?.get(1) ?: continue
            val concatExpr = concatRegex.find(layer2)?.groupValues?.get(1) ?: continue
            val varNames = varNameRegex.findAll(concatExpr).map { it.value }.toList()
            if (varNames.isEmpty()) continue

            var totalLength = 0
            for (v in varNames) {
                totalLength += arrayMap[v]?.size ?: 0
            }
            if (totalLength == 0) continue

            val payload = ByteArray(totalLength)
            var offset = 0
            for (v in varNames) {
                val arr = arrayMap[v] ?: continue
                for (num in arr) {
                    payload[offset++] = num.toByte()
                }
            }

            val keyLen = hexKey.length
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor hexKey[i % keyLen].code).toByte()
            }

            val layer3 = String(payload, Charsets.UTF_8)
            val key3 = key3Regex.find(layer3)?.groupValues?.get(1) ?: continue
            val rawJsonB64 = b64JsonRegex.find(layer3)?.groupValues?.get(1) ?: continue

            val jsonStr = try {
                String(Base64.decode(rawJsonB64, Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                continue
            }

            val rawList = try {
                jsonStr.parseAs<List<String>>()
            } catch (_: Exception) {
                continue
            }

            val urls = mutableListOf<String>()
            val key3Len = key3.length
            for (item in rawList) {
                val b = try {
                    Base64.decode(item, Base64.DEFAULT)
                } catch (_: Exception) {
                    continue
                }
                for (j in b.indices) {
                    b[j] = (b[j].toInt() xor key3[j % key3Len].code).toByte()
                }
                urls.add(String(b, Charsets.UTF_8))
            }

            if (urls.isNotEmpty()) return urls
        }

        return emptyList()
    }

    override fun imageRequest(page: Page): Request {
        val (chapterUrl, actionToken) = decodePageMetadata(page.url)
        val imageUrl = page.imageUrl ?: throw Exception("Không tìm thấy URL ảnh")
        return GET(imageUrl, imageHeaders(chapterUrl, actionToken))
    }

    private fun imageHeaders(chapterUrl: String, actionToken: String) = super.headersBuilder()
        .add("Referer", chapterUrl)
        .add("Origin", baseUrl)
        .apply {
            if (actionToken.isNotEmpty()) {
                add("Token", actionToken)
            }
        }
        .build()

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tim-kiem").asJsoup()
        .select("label")
        .mapNotNull { element ->
            val slug = genreSlugRegex.matchEntire(element.attr("@click"))
                ?.groupValues
                ?.get(1)
                ?: return@mapNotNull null
            val genreName = element.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            GenreOption(genreName, slug)
        }
        .distinctBy { it.slug }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // ============================== Helpers ===============================

    private fun parseBackgroundUrl(styleValue: String?): String? {
        if (styleValue.isNullOrBlank()) return null

        val rawUrl = backgroundUrlRegex.find(styleValue)?.groupValues?.get(1) ?: return null
        return rawUrl.toHttpUrlOrNull()?.toString()
            ?: "$baseUrl${rawUrl.takeIf { it.startsWith("/") } ?: "/$rawUrl"}"
    }

    private fun encodePageMetadata(chapterUrl: String, actionToken: String): String = "$chapterUrl\n$actionToken"

    private fun decodePageMetadata(rawMetadata: String): Pair<String, String> {
        val separatorIndex = rawMetadata.lastIndexOf('\n')
        if (separatorIndex < 0) {
            return rawMetadata to ""
        }

        val chapterUrl = rawMetadata.substring(0, separatorIndex)
        val actionToken = rawMetadata.substring(separatorIndex + 1)
        return chapterUrl to actionToken
    }

    private var cachedActionToken: String? = null

    private val csrfTokenRegex = Regex("""var\s+csrf_token\s*=\s*'([^']+)'""")
    private val recaptchaRegex = Regex("""var\s+recaptcha\s*=\s*'([^']+)'""")
    private val scriptTagRegex = Regex("""<script\b[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
    private val bArrayRegex = Regex("""var\s+(_\w+)\s*=\s*\[([\s\S]*?)\];\s*var\s+_\w+\s*=\s*\1\.join""")
    private val quotedStringRegex = Regex(""""([^"]+)"""")
    private val arrayAssignRegex = Regex("""var\s+(_\w+)\s*=\s*\[((?:\d+(?:\s*,\s*\d+)*)?)\]""")
    private val keyRegex = Regex("""var\s+_\w+\s*=\s*['"]([0-9a-f]{16,64})['"]""")
    private val concatRegex = Regex("""var\s+_\w+\s*=\s*(_\w+(?:\.concat\(_\w+\))+)""")
    private val varNameRegex = Regex("""_\w+""")
    private val key3Regex = Regex("""var\s+_\w+\s*=\s*"([0-9a-f]{16,64})";""")
    private val b64JsonRegex = Regex("""var\s+_\w+\s*=\s*"([A-Za-z0-9+/=]{100,})";""")
    private val backgroundUrlRegex = Regex("""background-image:\s*url\(['"]?([^'")]+)""", RegexOption.IGNORE_CASE)
    private val genreSlugRegex = Regex("""toggleGenre\('([^']+)'\)""")
}
