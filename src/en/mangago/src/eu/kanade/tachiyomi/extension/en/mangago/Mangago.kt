package eu.kanade.tachiyomi.extension.en.mangago

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import android.util.LruCache
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.decodeHex
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.InputStream
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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
        val response = client.get("$baseUrl/genre/all/$page/?f=1&o=1&sortby=view&e=")
        return parseMangasPage(response)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/genre/all/$page/?f=1&o=1&sortby=update_date&e=")
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
        val title = link.attr("title").takeIf { it.isNotBlank() } ?: return null
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

        return parseMangasPage(client.get(url))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (
            url.host != baseUrl.toHttpUrl().host ||
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
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        document.selectFirst(".w-title h1")?.text()?.let {
            title = if (removeTitleVersion) it.replace(TITLE_REGEX, "") else it
        }

        document.getElementById("information")?.let { info ->
            thumbnail_url = info.selectFirst("img")?.attr("abs:src")
            description = info.selectFirst(".manga_summary")
                ?.text()
                ?.takeIf { it.isNotEmpty() && !it.equals("not found...", ignoreCase = true) }

            info.select(".manga_info li, .manga_right tr").forEach { element ->
                val label = element.selectFirst("b, label")?.text().orEmpty()
                when (label.lowercase()) {
                    "alternative:" -> {
                        val raw = element.text().removePrefix(label).trim()
                        val altNames = if ('/' in raw || ';' in raw) {
                            raw.split('/', ';')
                        } else {
                            raw.split(',')
                        }.map { it.trim() }
                            .filter { it.isNotEmpty() && !it.equals("None", ignoreCase = true) }
                        if (altNames.isNotEmpty()) {
                            description = buildString {
                                append(description.orEmpty())
                                if (isNotEmpty()) append("\n\n")
                                append(ALT_NAME_PREFIX)
                                append("\n")
                                altNames.joinTo(this, "\n") { "- $it" }
                            }
                        }
                    }
                    "status:" -> status = when (element.selectFirst("span")?.text()?.lowercase()) {
                        "ongoing" -> SManga.ONGOING
                        "completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                    "author(s):", "author:" -> author = element.select("a").joinToString { it.text() }
                    "genre(s):" -> genre = element.select("a").joinToString { it.text() }
                }
            }
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select(":is(table#raws_table, table#chapter_table) > tbody > tr, table.uk-table > tbody > tr")
        .mapNotNull { element ->
            val link = element.selectFirst("a.chico") ?: return@mapNotNull null
            if (link.attr("href").contains("/raw/") && removeRaws) return@mapNotNull null
            val name = link.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val date = DATE_FORMAT.tryParseDate(element.select("td:last-child").text(), ZoneOffset.UTC)
            val scanlator = element.selectFirst("td.no a, td.uk-table-shrink a")
                ?.text()
                ?.takeIf { it.isNotEmpty() }
            val chapterUrl = link.absUrl("href")

            SChapter.create().apply {
                url = stableChapterId(date, name, scanlator)
                this.name = name
                date_upload = date
                this.scanlator = scanlator ?: "Unknown"
                memo = buildJsonObject { put("chapterUrl", chapterUrl) }
            }
        }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val sameAuthor = document
            .select("div.also_like:has(h4:contains(Other manga by the same author)) + .pic_list .updatesli")
            .mapNotNull(::mangaFromElement)
        val alsoLiked = document.select(".also-like li").mapNotNull { element ->
            val link = element.selectFirst("h4 a[href*=\"/read-manga/\"][title]")
                ?: return@mapNotNull null
            val title = link.attr("title").takeIf { it.isNotBlank() }
                ?: link.text().takeIf { it.isNotEmpty() }
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
        val document = client.get(chapter.readerUrl()).asJsoup()
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
                val document = client.get(batchUrl).asJsoup()
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

    override fun imageRequest(page: Page): Request {
        val url = page.imageUrl!!.toHttpUrl()
        val imageUrl = if (url.host.contains("_")) {
            url.newBuilder().scheme("http").build()
        } else {
            url
        }
        return GET(imageUrl, headers)
    }

    private fun imageDescrambler(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment
            ?.takeIf { it.contains("desckey=") }
            ?: return response
        val key = fragment.substringAfter("desckey=").substringBefore("&")
        val cols = fragment.substringAfter("&cols=").toIntOrNull() ?: return response
        val body = response.body.byteStream().use { unscrambleImage(it, key, cols) }

        return response.newBuilder()
            .body(body)
            .build()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/genre/all/").asJsoup()
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
        val chapterJs = client.get(chapterJsUrl).use {
            SoJsonV4Deobfuscator.decode(it.body.string())
        }
        val key = findHexEncodedVariable(chapterJs, "key").decodeHex()
        val iv = findHexEncodedVariable(chapterJs, "iv").decodeHex()
        val cipher = Cipher.getInstance("AES/CBC/ZEROBYTEPADDING").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(iv),
            )
        }

        var imageList = cipher.doFinal(encryptedImages).toString(Charsets.UTF_8)
        imageList = unscrambleImageList(imageList, chapterJs)
        val cols = COLS_REGEX.find(chapterJs)?.groupValues?.get(1).orEmpty()
        val imageKeyScript = chapterJs
            .substringAfter("var renImg = function(img,width,height,id){")
            .substringBefore("key = key.split(")
            .lineSequence()
            .filter { line -> JS_FILTERS.none(line::contains) }
            .joinToString("\n")
            .replace("img.src", "url")

        return QuickJs.create().use { quickJs ->
            quickJs.execute(REPLACE_POS_BYTECODE)
            quickJs.evaluate("function getDescramblingKey(url) { $imageKeyScript; return key; }")
            imageList.split(",").map { imageUrl ->
                if (imageUrl.contains("cspiclink")) {
                    val descKey = quickJs.evaluate("""getDescramblingKey("$imageUrl");""") as String
                    "$imageUrl#desckey=$descKey&cols=$cols"
                } else {
                    imageUrl
                }
            }
        }
    }

    private fun findHexEncodedVariable(input: String, variable: String): String = HEX_VARIABLE_REGEX.findAll(input)
        .firstOrNull { it.groupValues[1] == variable }
        ?.groupValues
        ?.get(2)
        .orEmpty()

    private fun String.unscramble(keys: List<Int>): String {
        var result = this
        keys.reversed().forEach { key ->
            for (index in result.length - 1 downTo key) {
                if (index % 2 != 0) {
                    val previous = result[index - key]
                    result = result.replaceRange(index - key..index - key, result[index].toString())
                    result = result.replaceRange(index..index, previous.toString())
                }
            }
        }
        return result
    }

    private fun unscrambleImageList(imageList: String, js: String): String {
        var result = imageList
        try {
            val keyLocations = KEY_LOCATION_REGEX.findAll(js)
                .map { it.groupValues[1].toInt() }
                .distinct()
                .toList()
            val keys = keyLocations.map { result[it].toString().toInt() }
            keyLocations.forEachIndexed { index, location ->
                result = result.removeRange(location - index..location - index)
            }
            result = result.unscramble(keys)
        } catch (_: NumberFormatException) {
            // The image list is already unscrambled.
        }
        return result
    }

    private fun unscrambleImage(image: InputStream, key: String, cols: Int): ResponseBody {
        val bitmap = BitmapFactory.decodeStream(image)
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val unitWidth = bitmap.width / cols
        val unitHeight = bitmap.height / cols
        val keyArray = key.split("a")

        for (index in 0 until cols * cols) {
            val keyValue = keyArray[index].ifEmpty { "0" }.toInt()
            val destinationRow = keyValue.floorDiv(cols)
            val sourceRow = index.floorDiv(cols)
            val sourceX = (index - sourceRow * cols) * unitWidth
            val sourceY = sourceRow * unitHeight
            val destinationX = (keyValue - destinationRow * cols) * unitWidth
            val destinationY = destinationRow * unitHeight
            canvas.drawBitmap(
                bitmap,
                Rect(sourceX, sourceY, sourceX + unitWidth, sourceY + unitHeight),
                Rect(destinationX, destinationY, destinationX + unitWidth, destinationY + unitHeight),
                null,
            )
        }

        val buffer = okio.Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        bitmap.recycle()
        result.recycle()
        return buffer.asResponseBody("image/jpeg".toMediaTypeOrNull())
    }

    private fun Element.imgAttr() = when {
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        hasAttr("srcset") -> absUrl("srcset").substringBefore(" ")
        else -> absUrl("src")
    }

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

    private fun stableChapterId(date: Long, name: String, scanlator: String?): String = MessageDigest.getInstance("MD5")
        .digest("$date:$name:${scanlator.orEmpty()}".toByteArray())
        .joinToString("") { "%02x".format(it) }
        .takeLast(10)

    private val removeRaws get() = preferences.getBoolean(REMOVE_RAW_PREF, true)
    private val removeTitleVersion get() = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)

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
    }
}

private const val REMOVE_RAW_PREF = "pref_remove_raw"
private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
private const val ALT_NAME_PREFIX = "Alternative Names:"

private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
private val KEY_LOCATION_REGEX = Regex("""str\.charAt\(\s*(\d+)\s*\)""")
private val IMG_SRCS_REGEX = Regex("""var imgsrcs\s*=\s*['"]([a-zA-Z0-9+=/]+)['"]""")
private val COLS_REGEX = Regex("""var\s*widthnum\s*=\s*heightnum\s*=\s*(\d+);""")
private val TOTAL_PAGES_REGEX = Regex("""total_pages\s*=\s*(\d+)""")
private val HEX_VARIABLE_REGEX =
    Regex("""var\s+(key|iv)\s*=\s*CryptoJS\.enc\.Hex\.parse\("([0-9a-zA-Z]+)"\)""")
private val JS_FILTERS = listOf(
    "jQuery",
    "document",
    "getContext",
    "toDataURL",
    "getImageData",
    "width",
    "height",
)
private val TITLE_REGEX = Regex(
    """^(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩)\s*)+|(?:\s*(?:\([^()]*\)|\{[^{}]*\}|\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/\s*Official)\s*)+$""",
    RegexOption.IGNORE_CASE,
)
private val REPLACE_POS_BYTECODE by lazy {
    QuickJs.create().use {
        it.compile(
            """
                    function replacePos(strObj, pos, replacetext) {
                        var str = strObj.substr(0, pos) + replacetext + strObj.substring(pos + 1, strObj.length);
                        return str;
                    }
            """.trimIndent(),
            "?",
        )
    }
}
