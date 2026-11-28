package eu.kanade.tachiyomi.extension.en.mangago

import android.util.Base64
import android.util.LruCache
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.md5
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Mangago :
    KeiSource(),
    ConfigurableSource {
    private val preferences = getPreferences()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::imageDescrambler)
        addCookie("_m_superu" to "1")
        rateLimit(1) { it.host == baseUrl.toHttpUrl().host }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("genre/all/$page/")
            .addQueryParameter("f", "1")
            .addQueryParameter("o", "1")
            .addQueryParameter("sortby", "view")
            .addQueryParameter("e", "")
            .build()
        val response = client.get(url, Fetch.DOCUMENT)
        return parseMangasPage(response)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/list/latest/all/$page/", Fetch.DOCUMENT)
        return parseMangasPage(response)
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".updatesli, .pic_list > li")
            .mapNotNull(::mangaFromElement)
        val hasNextPage = document.selectFirst(".current+li > a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst(".thm-effect") ?: return null
        val title = link.attrOrNull("title") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            this.title = title
            thumbnail_url = link.selectFirst("img")?.imgAttr()
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/r/l_search".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("page", page.toString())
                .build()
        } else {
            "$baseUrl/genre/".toHttpUrl().newBuilder().apply {
                val genres = mutableListOf<String>()
                val excludedGenres = mutableListOf<String>()

                filters.forEach { filter ->
                    when (filter) {
                        is UriFilter -> filter.addToUrl(this)
                        is GenreFilterGroup -> filter.state.forEach { genre ->
                            when (genre.state) {
                                Filter.TriState.STATE_EXCLUDE -> excludedGenres += genre.name
                                Filter.TriState.STATE_INCLUDE -> genres += genre.name
                            }
                        }
                        else -> {}
                    }
                }

                addPathSegment(if (genres.isEmpty()) "all" else genres.joinToString(","))
                addPathSegment(page.toString())
                addQueryParameter("e", excludedGenres.joinToString(","))
            }.build()
        }

        return parseMangasPage(client.get(url, Fetch.DOCUMENT))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (
            url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.") ||
            url.pathSegments.firstOrNull() != "read-manga" ||
            url.pathSegments.getOrNull(1).isNullOrEmpty()
        ) {
            return null
        }

        val mangaUrl = "/read-manga/${url.pathSegments[1]}/"
        val manga = SManga.create().apply { this.url = mangaUrl }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = mangaUrl
            }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        document.selectFirst(".w-title h1")?.text()?.let {
            title = if (removeTitleVersion) it.replace(TITLE_REGEX, "") else it
        }

        document.getElementById("information")?.let { info ->
            thumbnail_url = info.selectFirst("img")?.absUrl("src")
            val summary = info.selectFirst(".manga_summary")
                ?.text()
                ?.takeUnless { it.equals("not found...", ignoreCase = true) }
            val rating = info.selectFirst("#interest_sectl .rating_num")?.text()?.toDoubleOrNull()
            val votes = info.selectFirst("#interest_sectl a[href*=/home/manga/status/]")
                ?.text()
                ?.substringBefore(" ")
                ?.toIntOrNull()
            var released: String? = null
            var altNames = emptyList<String>()

            info.select(".manga_info li, .manga_right tr").forEach { element ->
                val label = element.selectFirst("b, label")?.text().orEmpty()
                when (label.lowercase()) {
                    "alternative:" -> {
                        val raw = element.text().removePrefix(label)
                        altNames = if ('/' in raw || ';' in raw) {
                            raw.split('/', ';')
                        } else {
                            raw.split(',')
                        }.filterNot { it.trim().equals("None", ignoreCase = true) }
                    }
                    "status:" -> status = when (element.selectFirst("span")?.text()?.lowercase()) {
                        "ongoing" -> SManga.ONGOING
                        "completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                    "author(s):", "author:" -> {
                        author = element.select("a").joinToString { it.text() }
                        released = RELEASED_REGEX.find(element.text())?.groupValues?.get(1)
                    }
                    "genre(s):" -> genre = element.select("a").joinToString { it.text() }
                }
            }

            description = MarkdownDescription.Builder()
                .rating(rating, votes = votes)
                .field("Released", released)
                .description(summary)
                .altTitles(altNames)
                .build()
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val blacklist = scanlatorBlacklist
        return document.select(":is(table#raws_table, table#chapter_table) > tbody > tr, table.uk-table > tbody > tr")
            .mapNotNull { element ->
                val link = element.selectFirst("a.chico") ?: return@mapNotNull null
                if (link.attr("href").contains("/raw/") && removeRaws) return@mapNotNull null
                val name = link.textOrNull() ?: return@mapNotNull null
                val date = DATE_FORMAT.tryParseDate(element.selectFirst("td:last-child")?.text(), ZoneOffset.UTC)
                val scanlator = element.selectFirst("td.no a, td.uk-table-shrink a")?.textOrNull()
                if (scanlator != null && scanlator.lowercase() in blacklist) return@mapNotNull null
                val chapterUrl = link.absUrl("href")

                SChapter.create().apply {
                    url = stableChapterId(date, name, scanlator)
                    this.name = name
                    date_upload = date
                    this.scanlator = scanlator ?: "Unknown"
                    memo = buildJsonObject { put("chapterUrl", chapterUrl) }
                }
            }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val sameAuthor = document
            .select(".pic_list .updatesli")
            .mapNotNull(::mangaFromElement)
        val alsoLiked = document.select(".also-like li").mapNotNull { element ->
            val link = element.selectFirst("h4 a[href*=\"/read-manga/\"][title]")
                ?: return@mapNotNull null
            val title = link.attrOrNull("title")
                ?: link.textOrNull()
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }
        return (sameAuthor + alsoLiked).distinctBy { it.url }
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.readerUrl()

    private val pageBatchMutex = Mutex()
    private val pageBatchCache = LruCache<String, MutableMap<Int, String>>(10)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(chapter.readerUrl(), Fetch.DOCUMENT).asJsoup()
        val availableImages = getChapterImageUrls(document)
        val totalPages = document.selectFirst("script:containsData(total_pages)")
            ?.data()
            ?.let { TOTAL_PAGES_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: return emptyList()
        val urlTemplate = document.selectFirst("input#curl")
            ?.attr("value")
            ?.takeIf { it.contains("{page}") }
            ?: return emptyList()
        val readerPrefix = getReaderPrefix(document, urlTemplate)
        val batchSize = availableImages.count(String::isNotEmpty)
            .takeIf { it > 0 }
            ?: return emptyList()

        return (0 until totalPages).map { index ->
            val imageUrl = availableImages.getOrNull(index).orEmpty()
            if (imageUrl.isNotEmpty()) {
                Page(index, imageUrl = imageUrl)
            } else {
                val pageNumber = index + 1
                val batchStart = ((pageNumber - 1) / batchSize) * batchSize + 1
                val batchUrl = readerPrefix.newBuilder()
                    .addEncodedPathSegments(
                        urlTemplate.removePrefix("/").replace("{page}", batchStart.toString()),
                    )
                    .build()
                Page(index, "$batchUrl#$index")
            }
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val pageUrl = page.url.toHttpUrl()
        val index = pageUrl.fragment?.toIntOrNull() ?: error("Missing page index")
        val batchUrl = pageUrl.newBuilder().fragment(null).build().toString()

        return pageBatchMutex.withLock {
            val batch = pageBatchCache[batchUrl] ?: run {
                val document = client.get(batchUrl, Fetch.DOCUMENT).asJsoup()
                getChapterImageUrls(document)
                    .mapIndexedNotNull { imageIndex, imageUrl ->
                        imageUrl.takeIf { it.isNotEmpty() }?.let { imageIndex to it }
                    }
                    .toMap(mutableMapOf())
                    .also { pageBatchCache.put(batchUrl, it) }
            }

            val imageUrl = batch.remove(index)
                ?: error("Unable to find image for page ${index + 1}")
            if (batch.isEmpty()) pageBatchCache.remove(batchUrl)
            imageUrl
        }
    }

    private fun SChapter.readerUrl(): String = memo["chapterUrl"]?.string ?: error("Refresh chapter list")

    override fun getImageRequest(page: Page): Request {
        val request = super.getImageRequest(page)
        // Image hosts with an underscore (iweb_12) fail the TLS handshake
        if (!request.url.host.contains("_")) return request
        return request.newBuilder()
            .url(request.url.newBuilder().scheme("http").build())
            .build()
    }

    private fun imageDescrambler(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment
            ?.takeIf { it.contains("desckey=") }
            ?: return response
        val key = fragment.substringAfter("desckey=").substringBefore("&")
        val cols = fragment.substringAfter("&cols=").toIntOrNull() ?: return response
        val body = response.body.byteStream().use { Descrambler.unscrambleImage(it, key, cols) }

        return response.newBuilder()
            .body(body)
            .build()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/genre/all/", Fetch.DOCUMENT).asJsoup()
        return document.select("#genre_panel .genre_select_div[_id]")
            .map { it.attr("_id") }
            .filter { it.isNotEmpty() }
            .distinct()
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<String>>().orEmpty()
        return FilterList(
            buildList {
                add(Filter.Header("Ignored if using text search"))
                add(SortFilter())
                add(StatusFilterGroup())
                if (genres.isNotEmpty()) add(GenreFilterGroup(genres))
            },
        )
    }

    private suspend fun getChapterImageUrls(document: Document): List<String> {
        val encryptedImages = document.selectFirst("script:containsData(imgsrcs)")
            ?.data()
            ?.let { IMG_SRCS_REGEX.find(it)?.groupValues?.get(1) }
            ?.let { Base64.decode(it, Base64.DEFAULT) }
            ?: return emptyList()

        val chapterJsUrl = document.selectFirst("script[src*=chapter.js]")?.absUrl("src")
            ?: return emptyList()
        val chapterJs = SoJsonV4Deobfuscator.decode(client.get(chapterJsUrl, Fetch.SCRIPT).body.string())
        return Descrambler.decodeImageList(encryptedImages, chapterJs)
    }

    private fun Element.imgAttr() = absUrl(if (hasAttr("data-src")) "data-src" else "src")

    private fun getReaderPrefix(document: Document, urlTemplate: String): HttpUrl {
        val location = document.location().toHttpUrl()
        val templateSegment = urlTemplate.removePrefix("/").substringBefore("/")
        val baseHost = baseUrl.toHttpUrl().host
        val isBaseHost = location.host == baseHost || location.host == baseHost.removePrefix("www.")

        return when {
            isBaseHost &&
                location.pathSegments.size > 3 &&
                location.pathSegments[0] == "read-manga" &&
                location.pathSegments[2] == templateSegment -> {
                "$baseUrl/read-manga/${location.pathSegments[1]}".toHttpUrl()
            }
            !isBaseHost &&
                location.pathSegments.firstOrNull() == templateSegment -> {
                location.newBuilder()
                    .encodedPath("/")
                    .query(null)
                    .fragment(null)
                    .build()
            }
            else -> error("Unexpected chapter URL structure")
        }
    }

    private fun stableChapterId(date: Long, name: String, scanlator: String?): String = "$date:$name:${scanlator.orEmpty()}".md5().toHexString().takeLast(10)

    private val removeRaws get() = preferences.getBoolean(REMOVE_RAW_PREF, true)
    private val removeTitleVersion get() = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)
    private val scanlatorBlacklist: Set<String>
        get() = preferences.getString(PREF_SCANLATOR_BLACKLIST, "")
            ?.split(",", "\n")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet() ?: emptySet()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_RAW_PREF
            title = "Hide RAW chapters"
            setDefaultValue(true)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_TITLE_VERSION_PREF
            title = "Remove version information from entry titles"
            summary = "This removes version tags like '(Official)' or '(Yaoi)' from entry titles " +
                "and helps identify duplicate entries in your library. " +
                "To update existing entries, enable 'update library manga title' in advanced settings of app"
            setDefaultValue(false)
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_SCANLATOR_BLACKLIST
            title = "Scanlator Blacklist"
            summary = "Filter out chapters from specific scanlators. Comma-separated list of group names."
            dialogTitle = "Exclude scanlators"
            setDefaultValue("")
        }.let(screen::addPreference)
    }
}

private const val REMOVE_RAW_PREF = "pref_remove_raw"
private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
private const val PREF_SCANLATOR_BLACKLIST = "pref_scanlator_blacklist"

private val RELEASED_REGEX = Regex("""(\d{4}) released""")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
private val IMG_SRCS_REGEX = Regex("""var imgsrcs\s*=\s*['"]([a-zA-Z0-9+=/]+)['"]""")
private val TOTAL_PAGES_REGEX = Regex("""total_pages\s*=\s*(\d+)""")
private val TITLE_REGEX = Regex(
    """^(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩)\s*)+|(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/\s*Official)\s*)+$""",
    RegexOption.IGNORE_CASE,
)
