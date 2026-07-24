package eu.kanade.tachiyomi.extension.vi.lxhentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds
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
        var token = ""
        var imageUrls = emptyList<String>()

        for (attempt in 1..2) {
            try {
                runWebView(timeout = 45.seconds) {
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    userAgent = headers["User-Agent"]!!

                    poll(1.seconds) {
                        evaluateJs(
                            """(function(){
                                var b=document.querySelector('.swal2-confirm');
                                if(b && !b.disabled && b.textContent.includes('tiếp tục')) b.click();
                            })()""",
                        )

                        evaluateJs(checkAndDecodeScript) { value ->
                            val parsed = parseTokenResult(value) ?: return@evaluateJs
                            token = parsed.first
                            imageUrls = parsed.second
                            resolve(Unit)
                        }
                    }

                    loadUrl(chapterUrl)
                }

                if (token.isNotEmpty() && imageUrls.isNotEmpty()) break
            } catch (_: WebViewTimeoutException) {}
        }

        val urls = imageUrls.filter { it.isNotBlank() }
        if (urls.isEmpty()) return emptyList()

        val pageMetadata = encodePageMetadata(chapterUrl, token)
        return urls.mapIndexed { index: Int, imageUrl: String ->
            Page(index, url = pageMetadata, imageUrl = imageUrl)
        }
    }

    private fun parseTokenResult(value: String): Pair<String, List<String>>? {
        val cleaned = value.trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleaned.isEmpty() || cleaned == "null" || cleaned == "[]") return null

        return try {
            val json = cleaned
                .removePrefix("Object {").removeSuffix("}")
                .replace("Object {", "{")
            val tokenMatch = Regex(""""token"\s*:\s*\"([^\"]*)\"""").find(json)
            val urlsMatch = Regex(""""urls"\s*:\s*\[([^\]]*)\]""").find(json)

            val t = tokenMatch?.groupValues?.get(1) ?: return null
            val urlsRaw = urlsMatch?.groupValues?.get(1) ?: return null

            val urls = Regex(""""([^"]*http[^"]*)"""").findAll(urlsRaw)
                .map { it.groupValues[1] }
                .toList()

            if (t.isNotEmpty() && urls.isNotEmpty()) t to urls else null
        } catch (_: Exception) {
            null
        }
    }

    override fun imageRequest(page: Page): Request {
        val (chapterUrl, actionToken) = decodePageMetadata(page.url)
        val imageUrl = page.imageUrl ?: throw Exception("Không tìm thấy URL ảnh")
        return GET(imageUrl, imageHeaders(chapterUrl, actionToken))
    }

    private fun imageHeaders(chapterUrl: String, actionToken: String) = super.headersBuilder()
        .add("Referer", chapterUrl)
        .add("Origin", baseUrl)
        .add("Token", actionToken)
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
        if (separatorIndex <= 0) {
            throw Exception("Không đọc được thông tin token ảnh")
        }

        val chapterUrl = rawMetadata.substring(0, separatorIndex)
        val actionToken = rawMetadata.substring(separatorIndex + 1)
        return chapterUrl to actionToken
    }

    private val backgroundUrlRegex = Regex("""background-image:\s*url\(['"]?([^'")]+)""", RegexOption.IGNORE_CASE)
    private val genreSlugRegex = Regex("""toggleGenre\('([^']+)'\)""")
    private val checkAndDecodeScript = """(function(){
        try {
            var b = document.querySelector('.swal2-confirm');
            if (b && !b.disabled && b.textContent.indexOf('tiếp tục') >= 0) b.click();
            var t = window.actionToken;
            if (!t || typeof t !== 'string' || t.length === 0) return JSON.stringify({token:'',urls:[]});
            var scripts = document.querySelectorAll('script:not([src])');
            var target = null;
            for (var i = 0; i < scripts.length; i++) {
                var txt = scripts[i].textContent;
                if (txt.indexOf('[\"KGZ1') >= 0 || txt.indexOf('=\\[\"KGZ1') >= 0) {
                    target = txt;
                    break;
                }
            }
            if (!target) return JSON.stringify({token:t,urls:[]});
            var b64Match = target.match(/=\\[((?:\"[A-Za-z0-9+/=]{20,}\",?\s*)+)\]/);
            if (!b64Match) return JSON.stringify({token:t,urls:[]});
            var parts = b64Match[1].match(/\"([^\"]+)\"/g);
            if (!parts) return JSON.stringify({token:t,urls:[]});
            var joined = parts.map(function(s){return s.replace(/\"/g,'');}).join('');
            var raw = atob(joined);
            var layer1;
            try { layer1 = decodeURIComponent(escape(raw)); } catch(e2) { layer1 = raw; }
            var key2Match = layer1.match(/var _\\w+='([0-9a-f]{20,})'/);
            if (!key2Match) return JSON.stringify({token:t,urls:[]});
            var key2 = key2Match[1];
            var arrRe = /var _\\w+=\\[((?:-?\\d+,?)*)\\]/g;
            var combined = [];
            var m;
            while ((m = arrRe.exec(layer1)) !== null) {
                var nums = m[1].split(',').filter(function(s){return s.length>0;}).map(Number);
                combined = combined.concat(nums);
            }
            if (combined.length === 0) return JSON.stringify({token:t,urls:[]});
            var decoded = '';
            for (var i = 0; i < combined.length; i++) {
                decoded += String.fromCharCode((combined[i] ^ key2.charCodeAt(i % key2.length)) & 0xFF);
            }
            var key3Match = decoded.match(/var _\\w+=\"([0-9a-f]{20,})\"/);
            if (!key3Match) return JSON.stringify({token:t,urls:[]});
            var key3 = key3Match[1];
            var jsonB64Match = decoded.match(/var _\\w+=\"([A-Za-z0-9+/=]{50,})\"/);
            if (!jsonB64Match) return JSON.stringify({token:t,urls:[]});
            var jsonArr = JSON.parse(atob(jsonB64Match[1]));
            var urls = [];
            for (var j = 0; j < jsonArr.length; j++) {
                var item;
                try { item = decodeURIComponent(escape(atob(jsonArr[j]))); }
                catch(e3) { item = atob(jsonArr[j]); }
                var url = '';
                for (var k = 0; k < item.length; k++) {
                    url += String.fromCharCode((item.charCodeAt(k) ^ key3.charCodeAt(k % key3.length)) & 0xFF);
                }
                if (url.indexOf('http') === 0 && urls.indexOf(url) < 0) urls.push(url);
            }
            return JSON.stringify({token:t,urls:urls});
        } catch(e) { return JSON.stringify({token:'',urls:[]}); }
    })()"""
}
