package eu.kanade.tachiyomi.extension.en.mangago

import android.app.Application
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Mangago : ParsedHttpSource(), ConfigurableSource {

    override val name = "Mangago"

    override val baseUrl = "https://www.mangago.me"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2)
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            val fragment = request.url.fragment ?: return@addInterceptor response

            // desckey=...&cols=...
            val key = fragment.substringAfter("desckey=").substringBefore("&")
            val cols = fragment.substringAfter("&cols=").toIntOrNull() ?: return@addInterceptor response

            val image = unscrambleImage(response.body.byteStream(), key, cols)
            val body = image.toResponseBody("image/jpeg".toMediaTypeOrNull())
            return@addInterceptor response.newBuilder()
                .body(body)
                .build()
        }.build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Cookie", cookiesHeader)

    private val cookiesHeader by lazy {
        val cookies = mutableMapOf<String, String>()

        // Needed for correct page ordering
        cookies["_m_superu"] = "1"

        buildCookies(cookies)
    }

    private val genreListingSelector = ".updatesli"

    private val genreListingNextPageSelector = ".current+li > a"

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        val linkElement = element.selectFirst(".thm-effect")!!

        setUrlWithoutDomain(linkElement.attr("href"))
        title = linkElement.attr("title")

        val thumbnailElem = linkElement.selectFirst("img")!!
        thumbnail_url = thumbnailElem.attr("abs:data-src").ifBlank { thumbnailElem.attr("abs:src") }
    }

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/genre/all/$page/?f=1&o=1&sortby=view&e=", headers)

    override fun popularMangaSelector(): String = genreListingSelector

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun popularMangaNextPageSelector() = genreListingNextPageSelector

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/genre/all/$page/?f=1&o=1&sortby=update_date&e=", headers)

    override fun latestUpdatesSelector() = genreListingSelector

    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = genreListingNextPageSelector

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/r/l_search".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("page", page.toString())
                .build().toString()
        } else {
            "$baseUrl/genre/".toHttpUrl().newBuilder().apply {
                val genres = mutableListOf<String>()
                val genresEx = mutableListOf<String>()

                filters.ifEmpty { getFilterList() }.forEach {
                    when (it) {
                        is UriFilter -> it.addToUrl(this)
                        is GenreFilterGroup -> it.state.forEach { genre ->
                            when (genre.state) {
                                Filter.TriState.STATE_EXCLUDE -> genresEx.add(genre.name)
                                Filter.TriState.STATE_INCLUDE -> genres.add(genre.name)
                                else -> {}
                            }
                        }
                        else -> {}
                    }
                }

                if (genres.isEmpty()) {
                    addPathSegment("all")
                } else {
                    addPathSegment(genres.joinToString(","))
                }
                addPathSegment(page.toString())

                addQueryParameter("e", genresEx.joinToString(","))
            }.build().toString()
        }
        return GET(url, headers)
    }

    override fun searchMangaSelector() = "$genreListingSelector, .pic_list > li"

    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaNextPageSelector() = genreListingNextPageSelector

    private var titleRegex: Regex =
        Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|𖤍.+?𖤍|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|\\/Official|\\/ Official", RegexOption.IGNORE_CASE)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".w-title h1")!!.text()
        if (isRemoveTitleVersion()) {
            title = title.replace(titleRegex, "").trim()
        }

        document.getElementById("information")!!.let {
            thumbnail_url = it.selectFirst("img")!!.attr("abs:src")
            description = it.selectFirst(".manga_summary")?.let { summary ->
                summary.selectFirst("font")?.remove()
                summary.text()
            }
            it.select(".manga_info li, .manga_right tr").forEach { el ->
                when (el.selectFirst("b, label")!!.text().lowercase()) {
                    "alternative:" -> description += "\n\n${el.text()}"
                    "status:" -> status = when (el.selectFirst("span")!!.text().lowercase()) {
                        "ongoing" -> SManga.ONGOING
                        "completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                    "author(s):", "author:" -> author = el.select("a").joinToString { it.text() }
                    "genre(s):" -> genre = el.select("a").joinToString { it.text() }
                }
            }
        }
    }

    override fun chapterListSelector() = "table#chapter_table > tbody > tr, table.uk-table > tbody > tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val link = element.select("a.chico")

        val urlOriginal = link.attr("href")
        if (urlOriginal.startsWith("http")) url = urlOriginal else setUrlWithoutDomain(urlOriginal)
        name = link.text().trim()
        date_upload = runCatching {
            dateFormat.parse(element.select("td:last-child").text().trim())?.time
        }.getOrNull() ?: 0L
        scanlator = element.selectFirst("td.no a, td.uk-table-shrink a")?.text()?.trim()
        if (scanlator.isNullOrBlank()) {
            scanlator = "Unknown"
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        if (!document.select("div.controls ul#dropdown-menu-page").isNullOrEmpty()) {
            return pageListParseMobile(document)
        }

        val imgsrcsScript = document.selectFirst("script:containsData(imgsrcs)")?.html()
            ?: throw Exception("Could not find imgsrcs")
        val imgsrcRaw = imgSrcsRegex.find(imgsrcsScript)?.groupValues?.get(1)
            ?: throw Exception("Could not extract imgsrcs")
        val imgsrcs = Base64.decode(imgsrcRaw, Base64.DEFAULT)

        val chapterJsUrl = document.getElementsByTag("script").first {
            it.attr("src").contains("chapter.js", ignoreCase = true)
        }.attr("abs:src")

        val obfuscatedChapterJs =
            client.newCall(GET(chapterJsUrl, headers)).execute().body.string()
        val deobfChapterJs = SoJsonV4Deobfuscator.decode(obfuscatedChapterJs)

        val key = findHexEncodedVariable(deobfChapterJs, "key").decodeHex()
        val iv = findHexEncodedVariable(deobfChapterJs, "iv").decodeHex()
        val cipher = Cipher.getInstance(hashCipher)
        val keyS = SecretKeySpec(key, aes)
        cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(iv))

        var imageList = cipher.doFinal(imgsrcs).toString(Charsets.UTF_8)

        imageList = unescrambleImageList(imageList, deobfChapterJs)

        val cols = colsRegex.find(deobfChapterJs)?.groupValues?.get(1) ?: ""

        return imageList
            .split(",")
            .mapIndexed { idx, it ->
                val url = if (it.contains("cspiclink")) {
                    "$it#desckey=${getDescramblingKey(deobfChapterJs, it)}&cols=$cols"
                } else {
                    it
                }

                Page(idx, imageUrl = url)
            }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    private fun pageListParseMobile(document: Document): List<Page> {
        val pagesCount = document.select("div.controls ul#dropdown-menu-page li").size
        val pageUrl = document.location().removeSuffix("/").substringBeforeLast("-")
        return IntRange(1, pagesCount).map { Page(it, url = "$pageUrl-$it/") }
    }

    private var cachedDeofChapterJS: String? = null
    private var cachedKey: ByteArray? = null
    private var cachedIv: ByteArray? = null
    private var cachedTime: Long = 0
    private val maxCacheTime = 1000 * 60 * 5 // 5 minutes

    override fun imageUrlParse(document: Document): String {
        val imgsrcsScript = document.selectFirst("script:containsData(imgsrcs)")?.html()
            ?: throw Exception("Could not find imgsrcs")
        val imgsrcRaw = imgSrcsRegex.find(imgsrcsScript)?.groupValues?.get(1)
            ?: throw Exception("Could not extract imgsrcs")
        val imgsrcs = Base64.decode(imgsrcRaw, Base64.DEFAULT)
        val chapterJsUrl = document.getElementsByTag("script").first {
            it.attr("src").contains("chapter.js", ignoreCase = true)
        }.attr("abs:src")

        if (cachedDeofChapterJS == null || cachedKey == null || cachedIv == null || System.currentTimeMillis() - cachedTime > maxCacheTime) {
            val obfuscatedChapterJs = client.newCall(GET(chapterJsUrl, headers)).execute().body.string()
            cachedDeofChapterJS = SoJsonV4Deobfuscator.decode(obfuscatedChapterJs)
            cachedKey = findHexEncodedVariable(cachedDeofChapterJS!!, "key").decodeHex()
            cachedIv = findHexEncodedVariable(cachedDeofChapterJS!!, "iv").decodeHex()
            cachedTime = System.currentTimeMillis()
        }

        val cipher = Cipher.getInstance(hashCipher)
        val keyS = SecretKeySpec(cachedKey, aes)
        cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(cachedIv))

        var imageList = cipher.doFinal(imgsrcs).toString(Charsets.UTF_8)

        imageList = unescrambleImageList(imageList, cachedDeofChapterJS!!)

        val cols = colsRegex.find(cachedDeofChapterJS!!)?.groupValues?.get(1) ?: ""

        val pageNumber = document.location().removeSuffix("/").substringAfterLast("-").toInt()

        return imageList.split(",")[pageNumber - 1].let {
            if (it.contains("cspiclink")) {
                "$it#desckey=${getDescramblingKey(cachedDeofChapterJS!!, it)}&cols=$cols"
            } else {
                it
            }
        }
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Ignored if using text search"),
        SortFilter(),
        StatusFilterGroup(),
        GenreFilterGroup(),
    )

    private interface UriFilter {
        fun addToUrl(builder: HttpUrl.Builder)
    }

    private class StatusFilter(name: String, val query: String, state: Boolean) : UriFilter, Filter.CheckBox(name, state) {
        override fun addToUrl(builder: HttpUrl.Builder) {
            builder.addQueryParameter(query, if (state) "1" else "0")
        }
    }

    private class StatusFilterGroup : UriFilter, Filter.Group<StatusFilter>(
        "Status",
        listOf(
            StatusFilter("Completed", "f", true),
            StatusFilter("Ongoing", "o", true),
        ),
    ) {
        override fun addToUrl(builder: HttpUrl.Builder) {
            state.forEach {
                it.addToUrl(builder)
            }
        }
    }

    open class UriPartFilter(
        name: String,
        private val query: String,
        private val vals: Array<Pair<String, String>>,
        private val firstIsUnspecified: Boolean = true,
        state: Int = 0,
    ) : UriFilter, Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
        override fun addToUrl(builder: HttpUrl.Builder) {
            if (state != 0 || !firstIsUnspecified) {
                builder.addQueryParameter(query, vals[state].second)
            }
        }
    }

    private class SortFilter : UriPartFilter(
        "Sort",
        "sortby",
        arrayOf(
            Pair("Random", "random"),
            Pair("Views", "view"),
            Pair("Comment Count", "comment_count"),
            Pair("Creation Date", "create_date"),
            Pair("Update Date", "update_date"),
        ),
        state = 1,
    )

    private class GenreFilter(name: String) : Filter.TriState(name)

    private class GenreFilterGroup : Filter.Group<GenreFilter>(
        "Genres",
        listOf(
            GenreFilter("Yaoi"),
            GenreFilter("Doujinshi"),
            GenreFilter("Shounen Ai"),
            GenreFilter("Shoujo"),
            GenreFilter("Yuri"),
            GenreFilter("Romance"),
            GenreFilter("Fantasy"),
            GenreFilter("Comedy"),
            GenreFilter("Smut"),
            GenreFilter("Adult"),
            GenreFilter("School Life"),
            GenreFilter("Mystery"),
            GenreFilter("One Shot"),
            GenreFilter("Ecchi"),
            GenreFilter("Shounen"),
            GenreFilter("Martial Arts"),
            GenreFilter("Shoujo Ai"),
            GenreFilter("Supernatural"),
            GenreFilter("Drama"),
            GenreFilter("Action"),
            GenreFilter("Adventure"),
            GenreFilter("Harem"),
            GenreFilter("Historical"),
            GenreFilter("Horror"),
            GenreFilter("Josei"),
            GenreFilter("Mature"),
            GenreFilter("Mecha"),
            GenreFilter("Psychological"),
            GenreFilter("Sci-fi"),
            GenreFilter("Seinen"),
            GenreFilter("Slice Of Life"),
            GenreFilter("Sports"),
            GenreFilter("Gender Bender"),
            GenreFilter("Tragedy"),
            GenreFilter("Bara"),
            GenreFilter("Shotacon"),
            GenreFilter("Webtoons"),
        ),
    )

    private fun findHexEncodedVariable(input: String, variable: String): String {
        val regex = Regex("""var $variable\s*=\s*CryptoJS\.enc\.Hex\.parse\("([0-9a-zA-Z]+)"\)""")
        return regex.find(input)?.groupValues?.get(1) ?: ""
    }

    private fun String.unscramble(keys: List<Int>): String {
        var s = this
        keys.reversed().forEach {
            for (i in s.length - 1 downTo it) {
                if (i % 2 != 0) {
                    val temp = s[i - it]
                    s = s.replaceRange(i - it..i - it, s[i].toString())
                    s = s.replaceRange(i..i, temp.toString())
                }
            }
        }
        return s
    }

    private fun unescrambleImageList(imageList: String, js: String): String {
        var imgList = imageList
        try {
            val keyLocations = keyLocationRegex.findAll(js).map {
                it.groupValues[1].toInt()
            }.distinct()

            val unscrambleKey = keyLocations.map {
                imgList[it].toString().toInt()
            }.toList()

            keyLocations.forEachIndexed { idx, it ->
                imgList = imgList.removeRange(it - idx..it - idx)
            }

            imgList = imgList.unscramble(unscrambleKey)
        } catch (e: NumberFormatException) {
            // Only call where it should throw is imageList[it].toString().toInt().
            // This usually means that the list is already unscrambled.
        }
        return imgList
    }

    private fun unscrambleImage(image: InputStream, key: String, cols: Int): ByteArray {
        val bitmap = BitmapFactory.decodeStream(image)

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val unitWidth = bitmap.width / cols
        val unitHeight = bitmap.height / cols

        val keyArray = key.split("a")

        for (idx in 0 until cols * cols) {
            val keyval = keyArray[idx].ifEmpty { "0" }.toInt()

            val heightY = keyval.floorDiv(cols)
            val dy = heightY * unitHeight
            val dx = (keyval - heightY * cols) * unitWidth

            val widthY = idx.floorDiv(cols)
            val sy = widthY * unitHeight
            val sx = (idx - widthY * cols) * unitWidth

            val srcRect = Rect(sx, sy, sx + unitWidth, sy + unitHeight)
            val dstRect = Rect(dx, dy, dx + unitWidth, dy + unitHeight)

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 100, output)

        return output.toByteArray()
    }

    private fun buildCookies(cookies: Map<String, String>) =
        cookies.entries.joinToString(separator = "; ", postfix = ";") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun getDescramblingKey(deobfChapterJs: String, imageUrl: String): String {
        val imgkeys = deobfChapterJs
            .substringAfter("var renImg = function(img,width,height,id){")
            .substringBefore("key = key.split(")
            .split("\n")
            .filter { jsFilters.all { filter -> !it.contains(filter) } }
            .joinToString("\n")
            .replace("img.src", "url")

        val js = """
            function getDescramblingKey(url) { $imgkeys; return key; }
            getDescramblingKey("$imageUrl");
        """.trimIndent()

        return QuickJs.create().use {
            it.execute(replacePosBytecode)

            it.evaluate(js).toString()
        }
    }

    private val jsFilters =
        listOf("jQuery", "document", "getContext", "toDataURL", "getImageData", "width", "height")

    private val hashCipher = "AES/CBC/ZEROBYTEPADDING"

    private val aes = "AES"

    private val keyLocationRegex by lazy {
        Regex("""str\.charAt\(\s*(\d+)\s*\)""")
    }

    private val imgSrcsRegex by lazy {
        Regex("""var imgsrcs\s*=\s*['"]([a-zA-Z0-9+=/]+)['"]""")
    }

    private val colsRegex =
        Regex("""var\s*widthnum\s*=\s*heightnum\s*=\s*(\d+);""")

    private val replacePosBytecode by lazy {
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
    private fun isRemoveTitleVersion() = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_TITLE_VERSION_PREF
            title = "Remove version information from entry titles"
            summary = "This removes version tags like '(Official)' or '(Yaoi)' from entry titles " +
                "and helps identify duplicate entries in your library. " +
                "To update existing entries, remove them from your library (unfavorite) and refresh manually. " +
                "You might also want to clear the database in advanced settings."
            setDefaultValue(false)
        }.let(screen::addPreference)
        addRandomUAPreferenceToScreen(screen)
    }
    companion object {
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
    }
}
